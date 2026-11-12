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
