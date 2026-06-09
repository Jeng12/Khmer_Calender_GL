package com.example.data

import java.util.Calendar

/**
 * Pure date logic for the rotating-shift work schedule.
 *
 * The cycle is anchored on the **26th**: the cycle containing a date runs from
 * the 26th of one month up to and including the 25th of the next month. It is
 * split into four weeks — weeks 1–3 are 7 days each and week 4 absorbs the
 * remainder, running "until the 25th". The user's weekly shift pattern repeats
 * every cycle.
 *
 * A week may hold several shifts (e.g. the first week working both day and
 * night); the week's days are split between them in order.
 *
 * A night shift that ends the next morning (e.g. 19:30 → 07:30) leaves no rest
 * before a same-morning day shift (07:30 → 19:30); such back-to-back days are
 * flagged [WorkDay.blocked] so they can be skipped/blocked.
 */
object WorkCycleEngine {

    private const val DAY_MS = 86_400_000L
    private const val ANCHOR_DAY = 26

    data class WorkDay(
        val year: Int,
        val month: Int,   // 1..12
        val day: Int,
        val shift: AppStore.ShiftDef,
        val startMs: Long,
        val endMs: Long,
        val blocked: Boolean
    )

    private fun midnight(year: Int, month: Int, day: Int): Calendar =
        Calendar.getInstance().apply {
            clear()
            set(year, month - 1, day, 0, 0, 0)
        }

    /** Midnight of the 26th that begins the cycle containing the given date. */
    fun cycleStart(year: Int, month: Int, day: Int): Calendar {
        return if (day >= ANCHOR_DAY) midnight(year, month, ANCHOR_DAY)
        else midnight(year, month, ANCHOR_DAY).apply { add(Calendar.MONTH, -1) }
    }

    /** Total days in the cycle containing the given date (26th → 26th next month). */
    private fun cycleLengthDays(year: Int, month: Int, day: Int): Int {
        val start = cycleStart(year, month, day)
        val next = (start.clone() as Calendar).apply { add(Calendar.MONTH, 1) }
        return ((next.timeInMillis - start.timeInMillis) / DAY_MS).toInt()
    }

    private fun daysSinceStart(year: Int, month: Int, day: Int): Int {
        val start = cycleStart(year, month, day).timeInMillis
        val today = midnight(year, month, day).timeInMillis
        return ((today - start) / DAY_MS).toInt()
    }

    /** 0-based week index (0..3) of the given date; week 4 absorbs the remainder. */
    fun weekIndex(year: Int, month: Int, day: Int): Int =
        (daysSinceStart(year, month, day) / 7).coerceIn(0, 3)

    /** Number of days in the given week of the cycle containing the date. */
    fun daysInWeek(year: Int, month: Int, day: Int, weekIdx: Int): Int =
        if (weekIdx < 3) 7 else (cycleLengthDays(year, month, day) - 21).coerceAtLeast(1)

    /**
     * The shift worked on a given date. For a multi-shift week the days are
     * split into contiguous blocks, one per assigned shift, in order.
     */
    fun shiftForDate(cycle: AppStore.ShiftCycle, year: Int, month: Int, day: Int): AppStore.ShiftDef? {
        val wi = weekIndex(year, month, day)
        val ids = cycle.shiftIdsForWeek(wi)
        if (ids.isEmpty()) return null
        if (ids.size == 1) return cycle.shiftById(ids[0])

        val diw = daysInWeek(year, month, day, wi)
        val dayInWeek = (daysSinceStart(year, month, day) - wi * 7).coerceAtLeast(0)
        val blockSize = (diw + ids.size - 1) / ids.size       // ceil
        val idx = (dayInWeek / blockSize).coerceIn(0, ids.size - 1)
        return cycle.shiftById(ids[idx])
    }

    private fun shiftStartMs(shift: AppStore.ShiftDef, year: Int, month: Int, day: Int): Long =
        midnight(year, month, day).apply {
            set(Calendar.HOUR_OF_DAY, shift.startHour)
            set(Calendar.MINUTE, shift.startMin)
        }.timeInMillis

    private fun shiftEndMs(shift: AppStore.ShiftDef, year: Int, month: Int, day: Int): Long =
        midnight(year, month, day).apply {
            set(Calendar.HOUR_OF_DAY, shift.endHour)
            set(Calendar.MINUTE, shift.endMin)
            if (shift.isOvernight) add(Calendar.DAY_OF_YEAR, 1)
        }.timeInMillis

    /**
     * Materialise every worked day from [fromCal] to [toCal] (inclusive),
     * applying the no-rest rule. Iterating a couple of days before the real
     * window of interest gives correct [WorkDay.blocked] context at the edge.
     */
    fun buildWorkDays(cycle: AppStore.ShiftCycle, fromCal: Calendar, toCal: Calendar): List<WorkDay> {
        if (!cycle.isConfigured) return emptyList()
        val result = ArrayList<WorkDay>()
        val cursor = (fromCal.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        val end = toCal.timeInMillis
        var prevEndMs = 0L

        while (cursor.timeInMillis <= end) {
            val y = cursor.get(Calendar.YEAR)
            val m = cursor.get(Calendar.MONTH) + 1
            val d = cursor.get(Calendar.DAY_OF_MONTH)
            val shift = shiftForDate(cycle, y, m, d)
            if (shift != null) {
                val startMs = shiftStartMs(shift, y, m, d)
                val endMs = shiftEndMs(shift, y, m, d)
                // No rest after a previous shift that ends at/after this one starts.
                val blocked = prevEndMs != 0L && startMs <= prevEndMs
                result += WorkDay(y, m, d, shift, startMs, endMs, blocked)
                if (!blocked) prevEndMs = endMs
            }
            cursor.add(Calendar.DAY_OF_YEAR, 1)
        }
        return result
    }

    /** Date range (start..end inclusive) for week [weekIdx] of the cycle containing the reference date. */
    fun weekRange(referenceYear: Int, referenceMonth: Int, referenceDay: Int, weekIdx: Int): Pair<Calendar, Calendar> {
        val start = cycleStart(referenceYear, referenceMonth, referenceDay)
        val weekStart = (start.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, weekIdx * 7) }
        val weekEnd: Calendar = if (weekIdx >= 3) {
            // Week 4 runs until the 25th (day before the next 26th).
            (start.clone() as Calendar).apply {
                add(Calendar.MONTH, 1)
                add(Calendar.DAY_OF_YEAR, -1)
            }
        } else {
            (weekStart.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, 6) }
        }
        return weekStart to weekEnd
    }
}
