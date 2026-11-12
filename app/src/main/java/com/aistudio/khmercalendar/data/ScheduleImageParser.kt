package com.aistudio.khmercalendar.data

import kotlin.math.abs
import kotlin.math.max

/**
 * Turns the words recognised in a photo of a work roster into "day of month →
 * shift" pairs. Pure logic with no Android dependency: the text recogniser
 * supplies the words and where they sit in the picture; this decides which
 * shift code belongs to which date.
 *
 * Two layouts are understood:
 *  - a **list**, each date followed by its shift on the same line
 *    (`26 Mon D`, `27 N`, `28 OFF`, or joined as `26D`);
 *  - a **table**, a row of dates with the shift codes in the row beneath.
 *
 * Whichever reading pairs more days wins. Only Latin letters and digits can be
 * read, and a roster listing several people must be cropped to one person's row
 * first, because nothing in the picture says which row is the user's.
 */
object ScheduleImageParser {

    /** One recognised word and its bounding box in image pixels. */
    data class Token(val text: String, val left: Float, val top: Float, val right: Float, val bottom: Float) {
        val centerX: Float get() = (left + right) / 2f
        val centerY: Float get() = (top + bottom) / 2f
        val height: Float get() = bottom - top
        val width: Float get() = right - left
    }

    /** What a day was read as: a shift id, or a day off. */
    sealed interface Reading {
        data class Shift(val shiftId: String) : Reading
        data object Off : Reading
    }

    private val OFF_WORDS = setOf("OFF", "O", "X", "R", "RD", "REST", "DO", "LEAVE", "HOLIDAY", "FREE")

    // Common roster codes by position of the shift in the user's shift list.
    private val TWO_SHIFT_WORDS = listOf(
        setOf("D", "DS", "DAY", "AM", "M", "MORNING"),
        setOf("N", "NS", "NIGHT", "PM")
    )
    private val THREE_SHIFT_WORDS = listOf(
        setOf("A", "M", "MORNING", "D", "DAY", "S1", "1ST"),
        setOf("B", "E", "EVENING", "AFTERNOON", "SWING", "S2", "2ND"),
        setOf("C", "N", "NIGHT", "S3", "3RD")
    )

    private val DAY_ONLY = Regex("""^(\d{1,2})$""")
    private val DATE = Regex("""^(\d{1,2})[/.\-](\d{1,2})(?:[/.\-]\d{2,4})?$""")
    private val DAY_WITH_CODE = Regex("""^(\d{1,2})[:\-=]?([A-Za-z]{1,10})$""")

    /** The reading for a word, or null when the word is not a shift code. */
    fun classify(word: String, shifts: List<AppStore.ShiftDef>): Reading? {
        val w = word.trim().trim('.', ',', ';', ':', '(', ')', '[', ']', '|').uppercase()
        if (w.isEmpty()) return null
        if (w in OFF_WORDS) return Reading.Off
        // The user's own shift names win over the generic codes.
        shifts.firstOrNull { it.name.isNotBlank() && it.name.trim().uppercase() == w }
            ?.let { return Reading.Shift(it.id) }
        val table = if (shifts.size >= 3) THREE_SHIFT_WORDS else TWO_SHIFT_WORDS
        table.forEachIndexed { index, words ->
            if (w in words) return shifts.getOrNull(index)?.let { Reading.Shift(it.id) }
        }
        return null
    }

    private class DayMark(val day: Int, val token: Token)
    private class CodeMark(val reading: Reading, val token: Token)

    /** Day of month (1..31) → what the roster says for it. Days not found are absent. */
    fun parse(tokens: List<Token>, shifts: List<AppStore.ShiftDef>): Map<Int, Reading> {
        if (shifts.isEmpty()) return emptyMap()
        val joined = LinkedHashMap<Int, Reading>()   // "26D": no pairing needed
        val days = ArrayList<DayMark>()
        val codes = ArrayList<CodeMark>()

        for (token in tokens) {
            val text = token.text.trim()
            if (text.isEmpty()) continue
            DAY_ONLY.matchEntire(text)?.let { m ->
                m.groupValues[1].toInt().takeIf { it in 1..31 }?.let { days += DayMark(it, token) }
                return@let
            } ?: DATE.matchEntire(text)?.let { m ->
                m.groupValues[1].toInt().takeIf { it in 1..31 }?.let { days += DayMark(it, token) }
                return@let
            } ?: DAY_WITH_CODE.matchEntire(text)?.let { m ->
                val day = m.groupValues[1].toInt()
                val reading = classify(m.groupValues[2], shifts)
                if (day in 1..31 && reading != null) joined.putIfAbsent(day, reading)
                return@let
            } ?: classify(text, shifts)?.let { codes += CodeMark(it, token) }
        }

        val byRow = pair(days, codes) { day, code ->
            // Code on the same text line, to the right of its date.
            val h = max(day.height, code.height)
            if (abs(code.centerY - day.centerY) <= 0.6f * h && code.centerX > day.centerX) {
                (code.centerX - day.centerX) / h
            } else null
        }
        val byColumn = pair(days, codes) { day, code ->
            // Code underneath its date, in the same column.
            val h = max(day.height, code.height)
            val w = max(max(day.width, code.width), h)
            if (code.centerY > day.centerY + 0.6f * h && abs(code.centerX - day.centerX) <= 0.7f * w) {
                (code.centerY - day.centerY) / h
            } else null
        }
        val paired = if (byColumn.size > byRow.size) byColumn else byRow
        return paired + joined
    }

    /**
     * Each code is given to its nearest date under [cost] (null = not related),
     * and each date keeps only its nearest code, so a date is read once.
     */
    private fun pair(
        days: List<DayMark>,
        codes: List<CodeMark>,
        cost: (day: Token, code: Token) -> Float?
    ): Map<Int, Reading> {
        val best = HashMap<DayMark, Pair<Float, Reading>>()
        for (code in codes) {
            var nearest: DayMark? = null
            var nearestCost = Float.MAX_VALUE
            for (day in days) {
                val c = cost(day.token, code.token) ?: continue
                if (c < nearestCost) { nearestCost = c; nearest = day }
            }
            val day = nearest ?: continue
            val current = best[day]
            if (current == null || nearestCost < current.first) best[day] = nearestCost to code.reading
        }
        // A day number printed twice (e.g. two months in one picture): keep the first by position.
        val result = LinkedHashMap<Int, Reading>()
        best.entries
            .sortedWith(compareBy({ it.key.token.top }, { it.key.token.left }))
            .forEach { (day, value) -> result.putIfAbsent(day.day, value.second) }
        return result
    }
}
