package com.example.data

import java.util.Calendar

/**
 * Pure date logic for the rotating-shift work schedule.
 *
 * The cycle is anchored on the **25th**: the cycle containing a date runs from
 * the 25th of one month up to (but not including) the 25th of the next. It is
 * split into four weeks — weeks 1–3 are 7 days each and week 4 absorbs the
 * remainder, running "until the 25th". The user's weekly shift pattern repeats
 * every cycle.
 *
 * A night shift that ends the next morning (e.g. 19:30 → 07:30) leaves no rest
 * before a same-morning day shift (07:30 → 19:30); such back-to-back days are
 * flagged [WorkDay.blocked] so they can be skipped/blocked.
 */
object WorkCycleEngine {

    private const val DAY_MS = 86_400_000L

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

    /** Midnight of the 25th that begins the cycle containing the given date. */
    fun cycleStart(year: Int, month: Int, day: Int): Calendar {
        return if (day >= 25) midnight(year, month, 25)
        else {
            val c = midnight(year, month, 25)
            c.add(Calendar.MONTH, -1)
            c
        }
    }

    /** 0-based week index (0..3) of the given date within its cycle; week 4 absorbs the remainder. */
    fun weekIndex(year: Int, month: Int, day: Int): Int {
        val start = cycleStart(year, month, day).timeInMillis
        val today = midnight(year, month, day).timeInMillis
        val days = ((today - start) / DAY_MS).toInt()
        return (days / 7).coerceIn(0, 3)
    }

    fun shiftForDate(cycle: AppStore.ShiftCycle, year: Int, month: Int, day: Int): AppStore.ShiftDef? {
        val idx = weekIndex(year, month, day)
        return cycle.shiftById(cycle.weekAssignments.getOrNull(idx))
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

    /** Human-readable date range "25 May – 1 Jun" for week [weekIndex] of the cycle containing today. */
    fun weekRange(referenceYear: Int, referenceMonth: Int, referenceDay: Int, weekIndex: Int): Pair<Calendar, Calendar> {
        val start = cycleStart(referenceYear, referenceMonth, referenceDay)
        val weekStart = (start.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, weekIndex * 7) }
        val weekEnd: Calendar = if (weekIndex >= 3) {
            // Week 4 runs until the day before the next 25th.
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
