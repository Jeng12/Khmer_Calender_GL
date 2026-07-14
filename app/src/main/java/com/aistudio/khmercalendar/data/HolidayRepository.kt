package com.aistudio.khmercalendar.data

import android.content.Context
import com.aistudio.khmercalendar.calendar.KhmerCalendarHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.util.LinkedHashMap

/**
 * A single Cambodian public holiday as returned by the calendar API.
 *
 * Built-in holidays come from the computed calendar month endpoint. Database
 * holiday overlays come from each day payload's `holiday_events` array.
 */
data class Holiday(
    val nameKh: String,
    val nameEn: String,
    val date: LocalDate,
    val type: String,
    val description: String?,
    val notes: String?,
    val isFixed: Boolean
) {
    /** Religious holidays (Visak Bochea, Meak Bochea, Pchum Ben…) map to the Buddhist bucket. */
    val isBuddhist: Boolean get() =
        type.equals("religious", ignoreCase = true) ||
            type.equals("buddhist", ignoreCase = true) ||
            type.contains("buddh", ignoreCase = true) ||
            type.contains("relig", ignoreCase = true) ||
            nameEn.contains("Bochea", ignoreCase = true) ||
            nameEn.contains("Pchum", ignoreCase = true) ||
            nameEn.contains("Visak", ignoreCase = true) ||
            nameEn.contains("Meak", ignoreCase = true)
}

/**
 * Fetches Cambodian public holidays from the Khmer Calendar API.
 *
 * Results are cached in memory for the process lifetime; callers get a
 * [Result] so network/parse failures can fall back to the bundled list
 * gracefully.
 */
object HolidayRepository {
    private const val CACHE_MAX_AGE_MS = 6L * 60L * 60L * 1000L
    private const val FALLBACK_RETRY_AGE_MS = 60L * 1000L

    private data class MemoryEntry(val holidays: List<Holiday>, val expiresAtMs: Long)

    private val cache = object : LinkedHashMap<String, MemoryEntry>(8, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, MemoryEntry>?): Boolean =
            size > 8
    }

    /**
     * @param year  Optional year filter applied client-side after fetching.
     * @param forceRefresh  Bypass the in-memory cache and re-fetch from network.
     */
    suspend fun fetchHolidays(
        context: Context,
        year: Int? = null,
        forceRefresh: Boolean = false,
        includeDatabaseEvents: Boolean = true
    ): Result<List<Holiday>> = withContext(Dispatchers.IO) {
        runCatching {
            val targetYear = year ?: LocalDate.now().year
            val cacheKey = "$targetYear:$includeDatabaseEvents"
            val now = System.currentTimeMillis()
            if (!forceRefresh) getMemoryCache(cacheKey, now)?.let { return@runCatching it }

            val persistent = loadFromPersistentCache(context, targetYear)
            val persistentSavedAt = AppStore.getCachedHolidaysSavedAt(context, targetYear)
            val persistentIsFresh = persistent.isNotEmpty() &&
                persistentSavedAt > 0L && now - persistentSavedAt <= CACHE_MAX_AGE_MS
            if (!forceRefresh && includeDatabaseEvents && persistentIsFresh) {
                putMemoryCache(cacheKey, persistent, persistentSavedAt + CACHE_MAX_AGE_MS)
                return@runCatching persistent
            }

            val builtIn = builtInHolidays(targetYear)
            val cachedFixed = persistent.filter(Holiday::isFixed)
            val cachedUserEvents = persistent.filterNot(Holiday::isFixed)

            val publicResult = CalendarApiRepository.fetchPublicHolidays(targetYear)
            val publicHolidays = publicResult.getOrNull()?.map { it.toHoliday(isFixed = true) }
            val databaseResult = if (includeDatabaseEvents) {
                CalendarApiRepository.fetchHolidayEvents(
                    from = LocalDate.of(targetYear, 1, 1),
                    to = LocalDate.of(targetYear, 12, 31),
                    forceRefresh = forceRefresh
                )
            } else {
                Result.success(emptyList())
            }
            val userHolidayEvents = if (includeDatabaseEvents) {
                databaseResult.getOrNull()?.map { it.toHoliday(isFixed = false) } ?: cachedUserEvents
            } else {
                emptyList()
            }

            val fixedHolidays = publicHolidays ?: cachedFixed.takeIf { it.isNotEmpty() } ?: builtIn
            val parsed = (fixedHolidays + userHolidayEvents)
                .filter { it.date.year == targetYear }
                .distinctBy { "${it.date}:${it.nameKh}:${it.nameEn}" }
                .sortedBy { it.date }

            val fullyRefreshed = publicResult.isSuccess && databaseResult.isSuccess
            // The disk cache represents the complete server-backed view. Never
            // replace it with a partial response when one endpoint is offline.
            if (includeDatabaseEvents && fullyRefreshed && parsed.isNotEmpty()) {
                saveToPersistentCache(context, targetYear, parsed)
            }
            val maxAge = if (fullyRefreshed) CACHE_MAX_AGE_MS else FALLBACK_RETRY_AGE_MS
            putMemoryCache(cacheKey, parsed, now + maxAge)
            parsed
        }
    }

    @Synchronized
    private fun getMemoryCache(key: String, nowMs: Long): List<Holiday>? {
        val entry = cache[key] ?: return null
        if (nowMs >= entry.expiresAtMs) {
            cache.remove(key)
            return null
        }
        return entry.holidays
    }

    @Synchronized
    private fun putMemoryCache(key: String, holidays: List<Holiday>, expiresAtMs: Long) {
        cache[key] = MemoryEntry(holidays, expiresAtMs)
    }

    private fun builtInHolidays(year: Int): List<Holiday> =
        (1..12).flatMap { month ->
            KhmerCalendarHelper.getGregorianMonthDays(year, month).mapNotNull { day ->
                val name = day.holiday ?: return@mapNotNull null
                Holiday(
                    nameKh = name,
                    nameEn = name,
                    date = LocalDate.of(day.year, day.month, day.day),
                    type = typeFromBuiltInHoliday(name),
                    description = null,
                    notes = null,
                    isFixed = true
                )
            }
        }

    private fun CalendarApiHolidayEvent.toHoliday(isFixed: Boolean): Holiday =
        Holiday(
            nameKh = nameKm,
            nameEn = nameEn,
            date = occurrenceDate ?: date,
            type = type.ifBlank { typeFromBuiltInHoliday("$nameKm $nameEn") },
            description = description,
            notes = notes,
            isFixed = isFixed
        )

    private fun saveToPersistentCache(context: Context, year: Int, holidays: List<Holiday>) {
        val arr = JSONArray()
        holidays.forEach { h ->
            arr.put(JSONObject().apply {
                put("nameKh", h.nameKh); put("nameEn", h.nameEn)
                put("date", h.date.toString()); put("type", h.type)
                h.description?.let { put("description", it) }
                h.notes?.let { put("notes", it) }
                put("isFixed", h.isFixed)
            })
        }
        AppStore.saveCachedHolidays(context, year, arr.toString())
    }

    private fun loadFromPersistentCache(context: Context, year: Int): List<Holiday> {
        val raw = AppStore.getCachedHolidays(context, year) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                Holiday(
                    nameKh = o.getString("nameKh"),
                    nameEn = o.getString("nameEn"),
                    date = LocalDate.parse(o.getString("date")),
                    type = o.getString("type"),
                    description = o.optString("description").takeIf { it.isNotEmpty() },
                    notes = o.optString("notes").takeIf { it.isNotEmpty() },
                    isFixed = o.optBoolean("isFixed", false)
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun typeFromBuiltInHoliday(name: String): String =
        if (
            name.contains("Buddhist", ignoreCase = true) ||
            name.contains("religious", ignoreCase = true) ||
            name.contains("Bochea", ignoreCase = true) ||
            name.contains("Pchum", ignoreCase = true) ||
            name.contains("Water Festival", ignoreCase = true) ||
            name.contains("បូជា") ||
            name.contains("ភ្ជុំបិណ្ឌ") ||
            name.contains("អុំទូក")
        ) {
            "religious"
        } else {
            "national"
        }
}
