package com.example.data

import com.example.calendar.KhmerCalendarHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
    val isBuddhist: Boolean get() = type.equals("religious", ignoreCase = true)
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
    private var cache: Map<Int, List<Holiday>> = emptyMap()

    /**
     * @param year  Optional year filter applied client-side after fetching.
     * @param forceRefresh  Bypass the in-memory cache and re-fetch from network.
     */
    suspend fun fetchHolidays(
        year: Int? = null,
        forceRefresh: Boolean = false
    ): Result<List<Holiday>> = withContext(Dispatchers.IO) {
        runCatching {
            val targetYear = year ?: LocalDate.now().year
            val cached = cache[targetYear]
            if (!forceRefresh && cached != null) return@runCatching cached

            val builtIn = (1..12).flatMap { month ->
                KhmerCalendarHelper.getGregorianMonthDays(targetYear, month).mapNotNull { day ->
                    val name = day.holiday ?: return@mapNotNull null
                    Holiday(
                        nameKh = name,
                        nameEn = name,
                        date = LocalDate.of(day.year, day.month, day.day),
                        type = typeFromBuiltInHoliday(name),
                        description = null,
                        notes = null,
                        isFixed = false
                    )
                }
            }

            val apiEvents = CalendarApiRepository
                .fetchHolidayEvents(
                    from = LocalDate.of(targetYear, 1, 1),
                    to = LocalDate.of(targetYear, 12, 31),
                    forceRefresh = forceRefresh
                )
                .getOrDefault(emptyList())
                .map { event ->
                    Holiday(
                        nameKh = event.nameKm,
                        nameEn = event.nameEn,
                        date = event.occurrenceDate ?: event.date,
                        type = event.type,
                        description = event.description,
                        notes = event.notes,
                        isFixed = false
                    )
                }

            val parsed = (builtIn + apiEvents)
                .distinctBy { "${it.date}:${it.nameKh}:${it.nameEn}" }
                .sortedBy { it.date }

            cache = cache + (targetYear to parsed)
            parsed
        }
    }

    private fun typeFromBuiltInHoliday(name: String): String =
        if (
            name.contains("Bochea", ignoreCase = true) ||
            name.contains("Pchum", ignoreCase = true) ||
            name.contains("Water Festival", ignoreCase = true)
        ) {
            "religious"
        } else {
            "national"
        }
}
