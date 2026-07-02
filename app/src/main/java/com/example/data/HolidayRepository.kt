package com.example.data

import android.content.Context
import com.example.calendar.KhmerCalendarHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

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
            type.equals("buddhist", ignoreCase = true)
}

/**
 * Fetches Cambodian public holidays from the Khmer Calendar API.
 *
 * Results are cached in memory for the process lifetime; callers get a
 * [Result] so network/parse failures can fall back to the bundled list
 * gracefully.
 */
object HolidayRepository {

    @Volatile
    private var cache: Map<String, List<Holiday>> = emptyMap()

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
            val cached = cache[cacheKey]
            if (!forceRefresh && cached != null) return@runCatching cached

            val builtIn = builtInHolidays(targetYear)

            val publicHolidays = CalendarApiRepository.fetchPublicHolidays(targetYear)
                .getOrNull()
                ?.map { event -> event.toHoliday(isFixed = true) }

            var userHolidayEvents = emptyList<Holiday>()
            var loadedCached = false
            if (publicHolidays == null) {
                userHolidayEvents = loadFromPersistentCache(context, targetYear)
                loadedCached = userHolidayEvents.isNotEmpty()
            }

            if (includeDatabaseEvents && !loadedCached) {
                val apiResult = CalendarApiRepository
                    .fetchHolidayEvents(
                        from = LocalDate.of(targetYear, 1, 1),
                        to = LocalDate.of(targetYear, 12, 31),
                        forceRefresh = forceRefresh
                    )

                if (apiResult.isSuccess) {
                    userHolidayEvents = apiResult.getOrThrow().map { event -> event.toHoliday(isFixed = false) }
                } else if (publicHolidays == null) {
                    userHolidayEvents = loadFromPersistentCache(context, targetYear)
                }
            }

            val parsed = ((publicHolidays ?: builtIn) + userHolidayEvents)
                .distinctBy { "${it.date}:${it.nameKh}:${it.nameEn}" }
                .sortedBy { it.date }

            if (parsed.isNotEmpty()) {
                saveToPersistentCache(context, targetYear, parsed)
            }
            cache = cache + (cacheKey to parsed)
            parsed
        }
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
