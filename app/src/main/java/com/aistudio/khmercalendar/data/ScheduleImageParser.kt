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
 * read.
 *
 * A third layout, the **staff roster**, is handled by [parseRoster]: one header
 * row of consecutive dates and one row per person underneath. Its codes are
 * often bare numbers (`8`, `12`, `12D`), so they are returned as written and
 * the user says which row is theirs and what each code means.
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

    // ─── Staff roster (one row per person under a row of dates) ──────────────

    /** One person's row: the text left of the date columns and the code under each date. */
    data class RosterRow(val label: String, val codes: Map<Int, String>) {
        /** Stable identity for remembering the user's row: the staff id if one is printed. */
        val key: String
            get() = STAFF_ID.find(label)?.value ?: label.uppercase()
    }

    data class Roster(val days: List<Int>, val rows: List<RosterRow>)

    private val STAFF_ID = Regex("""[A-Z]{2,}\d{2,}""")
    private val CODE = Regex("""^[A-Z0-9]{1,4}$""")
    private val HOURS_DAY = Regex("""^\d{1,2}D$""")
    private val HOURS_NIGHT = Regex("""^\d{1,2}N$""")

    /**
     * Reads a staff roster, or returns null when the picture has no row of at
     * least three consecutive dates with people's rows beneath it.
     */
    fun parseRoster(tokens: List<Token>): Roster? {
        val words = tokens.filter { it.text.any(Char::isLetterOrDigit) && it.height > 0f }
        if (words.isEmpty()) return null
        val lines = groupIntoLines(words)

        // Header: the line holding the longest run of consecutive day numbers.
        var header: List<DayMark> = emptyList()
        var headerY = 0f
        for (line in lines) {
            val run = longestDateRun(line)
            if (run.size > header.size) {
                header = run
                headerY = run.map { it.token.centerY }.average().toFloat()
            }
        }
        if (header.size < 3) return null

        val centers = header.map { it.token.centerX }
        val spacing = centers.zipWithNext { a, b -> b - a }.sorted().let { it[it.size / 2] }
        if (spacing <= 0f) return null
        val half = spacing / 2f
        val gridLeft = centers.first() - half
        val gridRight = centers.last() + half

        val rows = ArrayList<RosterRow>()
        for (line in lines) {
            if (line.first().centerY <= headerY + header.first().token.height * 0.6f) continue
            val label = StringBuilder()
            val cells = HashMap<Int, StringBuilder>()
            for (word in line) {   // already left-to-right
                val x = word.centerX
                if (x < gridLeft) {
                    val text = word.text.trim().trim('|', '[', ']')
                    if (text.isNotEmpty()) { if (label.isNotEmpty()) label.append(' '); label.append(text) }
                } else if (x <= gridRight) {
                    val column = centers.indices.minByOrNull { abs(centers[it] - x) } ?: continue
                    cells.getOrPut(column) { StringBuilder() }
                        .append(word.text.uppercase().filter(Char::isLetterOrDigit))
                }
            }
            val codes = LinkedHashMap<Int, String>()
            for (column in centers.indices) {
                val code = cells[column]?.toString() ?: continue
                if (CODE.matches(code)) codes[header[column].day] = code
            }
            // A row with codes but nobody's name is the "hours per day" line, not a person.
            if (codes.isNotEmpty() && label.any(Char::isLetter)) rows += RosterRow(label.toString(), codes)
        }
        return if (rows.isEmpty()) null else Roster(header.map { it.day }, rows)
    }

    /** Words grouped into text lines (top to bottom), each sorted left to right. */
    private fun groupIntoLines(words: List<Token>): List<List<Token>> {
        val heights = words.map { it.height }.sorted()
        // A full text height of slack: in a table the name is often vertically
        // centred in its row while the codes sit at the bottom of their cells.
        val tolerance = heights[heights.size / 2] * 1.0f
        val lines = ArrayList<MutableList<Token>>()
        for (word in words.sortedBy { it.centerY }) {
            val line = lines.lastOrNull()
            if (line != null && abs(word.centerY - line.map { it.centerY }.average().toFloat()) <= tolerance) {
                line += word
            } else {
                lines += mutableListOf(word)
            }
        }
        return lines.map { line -> line.sortedBy { it.centerX } }
    }

    /** Longest run of day numbers that each follow the one before (month ends may wrap to 1). */
    private fun longestDateRun(line: List<Token>): List<DayMark> {
        val numbers = line.mapNotNull { token ->
            DAY_ONLY.matchEntire(token.text.trim())?.groupValues?.get(1)?.toInt()
                ?.takeIf { it in 1..31 }?.let { DayMark(it, token) }
        }
        var best: List<DayMark> = emptyList()
        var current = ArrayList<DayMark>()
        for (mark in numbers) {
            val prev = current.lastOrNull()
            val follows = prev != null && (mark.day == prev.day + 1 || (prev.day >= 28 && mark.day == 1))
            if (!follows) current = ArrayList()
            current += mark
            if (current.size > best.size) best = current
        }
        return best
    }

    /**
     * A first guess at what a roster code means, for the user to confirm.
     * [allCodes] are the codes in the same row: when hours are written as
     * numbers (`8`, `12`, `12D`), a lone `N` marks a day not worked rather than
     * a night shift, and a bare long shift next to its `D` twin is the night one.
     */
    fun guessMeaning(code: String, allCodes: Set<String>, shifts: List<AppStore.ShiftDef>): Reading {
        if (shifts.isEmpty()) return Reading.Off
        val dayShift = shifts.firstOrNull { !it.isOvernight } ?: shifts.first()
        val nightShift = shifts.firstOrNull { it.isOvernight } ?: shifts.last()
        val usesHours = allCodes.any { it.firstOrNull()?.isDigit() == true }
        val hours = code.toIntOrNull()
        return when {
            code == "H" || code == "0" -> Reading.Off
            HOURS_DAY.matches(code) -> Reading.Shift(dayShift.id)
            HOURS_NIGHT.matches(code) -> Reading.Shift(nightShift.id)
            hours != null ->
                if (hours >= 10 && allCodes.any { HOURS_DAY.matches(it) }) Reading.Shift(nightShift.id)
                else Reading.Shift(dayShift.id)
            code == "N" && usesHours -> Reading.Off
            else -> classify(code, shifts) ?: Reading.Off
        }
    }
}
