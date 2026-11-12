package com.aistudio.khmercalendar

import com.aistudio.khmercalendar.data.AppStore
import com.aistudio.khmercalendar.data.ScheduleImageParser
import com.aistudio.khmercalendar.data.ScheduleImageParser.Reading
import com.aistudio.khmercalendar.data.ScheduleImageParser.Token
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScheduleImageParserTest {

    private val two = AppStore.presetShifts(2)     // day, night
    private val three = AppStore.presetShifts(3)   // s1, s2, s3

    /** A word 20px tall placed on a grid: 60px columns, 40px rows. */
    private fun word(text: String, col: Int, row: Int): Token {
        val left = col * 60f
        val top = row * 40f
        return Token(text, left, top, left + 12f * text.length, top + 20f)
    }

    @Test
    fun list_dateThenCodeOnTheSameLine() {
        val tokens = listOf(
            word("26", 0, 0), word("Mon", 1, 0), word("D", 2, 0),
            word("27", 0, 1), word("Tue", 1, 1), word("N", 2, 1),
            word("28", 0, 2), word("Wed", 1, 2), word("OFF", 2, 2)
        )
        val result = ScheduleImageParser.parse(tokens, two)
        assertEquals(Reading.Shift("day"), result[26])
        assertEquals(Reading.Shift("night"), result[27])
        assertEquals(Reading.Off, result[28])
        assertEquals(3, result.size)
    }

    @Test
    fun table_codesInTheRowUnderTheDates() {
        val dates = listOf(26, 27, 28, 29, 30, 1, 2)
        val codes = listOf("D", "D", "N", "N", "OFF", "D", "N")
        val tokens = dates.mapIndexed { i, d -> word(d.toString(), i, 0) } +
            codes.mapIndexed { i, c -> word(c, i, 1) }
        val result = ScheduleImageParser.parse(tokens, two)
        assertEquals(7, result.size)
        assertEquals(Reading.Shift("day"), result[26])
        assertEquals(Reading.Shift("night"), result[28])
        assertEquals(Reading.Off, result[30])
        assertEquals(Reading.Shift("night"), result[2])
    }

    @Test
    fun table_twoWeeksStacked_eachDateTakesTheRowDirectlyBelow() {
        val tokens = listOf(
            word("26", 0, 0), word("27", 1, 0),
            word("D", 0, 1), word("N", 1, 1),
            word("3", 0, 2), word("4", 1, 2),
            word("N", 0, 3), word("OFF", 1, 3)
        )
        val result = ScheduleImageParser.parse(tokens, two)
        assertEquals(Reading.Shift("day"), result[26])
        assertEquals(Reading.Shift("night"), result[27])
        assertEquals(Reading.Shift("night"), result[3])
        assertEquals(Reading.Off, result[4])
    }

    @Test
    fun joinedAndDatedTokens() {
        val tokens = listOf(
            word("26D", 0, 0), word("27-N", 0, 1),
            word("28/11", 0, 2), word("Night", 2, 2),
            word("29/11/2026", 0, 3), word("off", 3, 3)
        )
        val result = ScheduleImageParser.parse(tokens, two)
        assertEquals(Reading.Shift("day"), result[26])
        assertEquals(Reading.Shift("night"), result[27])
        assertEquals(Reading.Shift("night"), result[28])
        assertEquals(Reading.Off, result[29])
    }

    @Test
    fun threeShiftCodes_andCustomShiftNames() {
        assertEquals(Reading.Shift("s1"), ScheduleImageParser.classify("A", three))
        assertEquals(Reading.Shift("s2"), ScheduleImageParser.classify("evening", three))
        assertEquals(Reading.Shift("s3"), ScheduleImageParser.classify("N", three))
        val custom = listOf(two[0].copy(name = "Early"), two[1].copy(name = "Late"))
        assertEquals(Reading.Shift("night"), ScheduleImageParser.classify("LATE", custom))
        assertNull(ScheduleImageParser.classify("Monday", two))
    }

    @Test
    fun ignoresTimesYearsAndUnrelatedWords() {
        val tokens = listOf(
            word("Roster", 0, 0), word("2026", 1, 0), word("07:30", 2, 0),
            word("26", 0, 1), word("D", 1, 1)
        )
        val result = ScheduleImageParser.parse(tokens, two)
        assertEquals(mapOf(26 to Reading.Shift("day")), result)
    }

    @Test
    fun noCodes_givesNothing() {
        assertTrue(ScheduleImageParser.parse(listOf(word("26", 0, 0), word("27", 1, 0)), two).isEmpty())
    }
}

/**
 * The staff-roster layout, using word positions laid out like a real
 * 884×428 staff-roster screenshot (names and staff ids are made up). Cells are
 * left- or right-aligned inside their columns, as in the original.
 */
class RosterParserTest {

    private val two = AppStore.presetShifts(2)
    private val columns = listOf(591f, 636f, 681f, 726f, 771f, 816f, 861f)

    private fun at(text: String, centerX: Float, centerY: Float): Token {
        val w = 6.5f * text.length
        return Token(text, centerX - w / 2, centerY - 5.5f, centerX + w / 2, centerY + 5.5f)
    }

    private fun realRoster(): List<Token> {
        val t = ArrayList<Token>()
        listOf("H", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun").forEachIndexed { i, s -> t += at(s, columns[i], 22f) }
        listOf("09", "10", "11", "12", "13", "14", "15").forEachIndexed { i, s -> t += at(s, columns[i], 57f) }
        listOf("0", "8", "8", "8", "8", "8", "0").forEachIndexed { i, s -> t += at(s, columns[i], 131f) }
        t += listOf(at("N°", 24f, 92f), at("Staff", 80f, 92f), at("ID", 108f, 92f),
            at("Full", 222f, 92f), at("name", 254f, 92f), at("Position", 452f, 92f))
        // Row 1 — office hours: H, 8 ×5, N
        t += listOf(at("1", 24f, 204f), at("EMP101", 95f, 204f), at("STAFF", 160f, 204f), at("ONE", 205f, 204f),
            at("Team", 350f, 207f), at("Lead", 392f, 207f),
            at("H", 575f, 213f), at("8", 651f, 213f), at("8", 696f, 213f), at("8", 741f, 213f),
            at("8", 786f, 213f), at("8", 831f, 213f), at("N", 845f, 213f))
        // Row 4 — shift worker: 12, 12D, 12D, N, N, 12, 12
        t += listOf(at("4", 24f, 325f), at("EMP104", 95f, 325f), at("STAFF", 165f, 325f), at("FOUR", 214f, 325f),
            at("Lab-Operator", 407f, 325f),
            at("12", 603f, 334f), at("12D", 631f, 334f), at("12D", 676f, 334f), at("N", 710f, 334f),
            at("N", 755f, 334f), at("12", 827f, 334f), at("12", 872f, 334f))
        // Row 5 — H, 12, 12, 12D, 12D, N, N
        t += listOf(at("5", 24f, 365f), at("EMP105", 95f, 365f), at("STAFF", 158f, 365f), at("FIVE", 200f, 365f),
            at("Lab-Operator", 407f, 365f),
            at("H", 575f, 374f), at("12", 651f, 374f), at("12", 692f, 374f), at("12D", 721f, 374f),
            at("12D", 766f, 374f), at("N", 800f, 374f), at("N", 845f, 374f))
        return t
    }

    @Test
    fun readsDatesAndOneRowPerPerson_skippingTheHoursLine() {
        val roster = ScheduleImageParser.parseRoster(realRoster())!!
        assertEquals(listOf(9, 10, 11, 12, 13, 14, 15), roster.days)
        assertEquals(listOf("EMP101", "EMP104", "EMP105"), roster.rows.map { it.key })
        assertTrue(roster.rows[1].label.contains("STAFF FOUR"))
    }

    @Test
    fun putsEachCodeUnderItsDate_whateverItsAlignmentInTheCell() {
        val rows = ScheduleImageParser.parseRoster(realRoster())!!.rows
        assertEquals(
            mapOf(9 to "H", 10 to "8", 11 to "8", 12 to "8", 13 to "8", 14 to "8", 15 to "N"),
            rows[0].codes
        )
        assertEquals(
            mapOf(9 to "12", 10 to "12D", 11 to "12D", 12 to "N", 13 to "N", 14 to "12", 15 to "12"),
            rows[1].codes
        )
        assertEquals(
            mapOf(9 to "H", 10 to "12", 11 to "12", 12 to "12D", 13 to "12D", 14 to "N", 15 to "N"),
            rows[2].codes
        )
    }

    @Test
    fun guessesHourCodes() {
        val codes = setOf("H", "12", "12D", "N")
        assertEquals(Reading.Off, ScheduleImageParser.guessMeaning("H", codes, two))
        assertEquals(Reading.Shift("day"), ScheduleImageParser.guessMeaning("12D", codes, two))
        assertEquals(Reading.Shift("night"), ScheduleImageParser.guessMeaning("12", codes, two))
        assertEquals(Reading.Off, ScheduleImageParser.guessMeaning("N", codes, two))
        // Office row: 8 is an ordinary day; without hour codes N still means night.
        assertEquals(Reading.Shift("day"), ScheduleImageParser.guessMeaning("8", setOf("H", "8", "N"), two))
        assertEquals(Reading.Shift("night"), ScheduleImageParser.guessMeaning("N", setOf("D", "N", "OFF"), two))
    }

    @Test
    fun notARoster_returnsNull() {
        val list = listOf(at("26", 10f, 10f), at("D", 60f, 10f), at("27", 10f, 50f), at("N", 60f, 50f))
        assertNull(ScheduleImageParser.parseRoster(list))
    }
}
