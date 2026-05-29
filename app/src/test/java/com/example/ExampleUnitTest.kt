package com.example

import org.junit.Assert.*
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun toKhmerNumeral_convertsDigitsCorrectly() {
        assertEquals("១", KhmerCalendarHelper.toKhmerNumeral(1))
        assertEquals("១៥", KhmerCalendarHelper.toKhmerNumeral(15))
        assertEquals("២០២៦", KhmerCalendarHelper.toKhmerNumeral(2026))
        assertEquals("០", KhmerCalendarHelper.toKhmerNumeral(0))
    }

    @Test
    fun getSerialDay_isConsistent() {
        val sd1 = KhmerCalendarHelper.getSerialDay(2026, 5, 15)
        val sd2 = KhmerCalendarHelper.getSerialDay(2026, 5, 16)
        assertEquals("Consecutive days differ by 1", 1, sd2 - sd1)
    }

    @Test
    fun getSerialDay_monthBoundary() {
        // May has 31 days, so Jun 1 should be 1 day after May 31
        val mayLast = KhmerCalendarHelper.getSerialDay(2026, 5, 31)
        val junFirst = KhmerCalendarHelper.getSerialDay(2026, 6, 1)
        assertEquals(1, junFirst - mayLast)
    }

    @Test
    fun getKhmerDate_builtinHoliday_khmerNewYear() {
        val kd = KhmerCalendarHelper.getKhmerDate(2026, 4, 14)
        assertNotNull("Khmer New Year should be a holiday", kd.holiday)
        assertTrue(kd.holiday!!.contains("ចូលឆ្នាំ"))
    }

    @Test
    fun getKhmerDate_dayOfWeek_knownDate() {
        // May 25 2026 is a Monday
        val kd = KhmerCalendarHelper.getKhmerDate(2026, 5, 25)
        assertEquals("Monday", kd.dayOfWeekEn)
        assertEquals("ចន្ទ", kd.dayOfWeek)
    }

    @Test
    fun getKhmerDate_returns_validLunarDay() {
        val kd = KhmerCalendarHelper.getKhmerDate(2026, 5, 15)
        assertTrue("Lunar day should be 1-15", kd.lunarDayVal in 1..15)
        assertTrue("Lunar month should not be empty", kd.lunarMonthName.isNotEmpty())
    }

    @Test
    fun getKhmerDate_zodiacName_doesNotContainCyrillic() {
        // Ensure zodiac names are all Khmer/ASCII — no Cyrillic characters
        for (month in 1..12) {
            val kd = KhmerCalendarHelper.getKhmerDate(2026, month, 1)
            val zodiac = kd.zodiac
            for (ch in zodiac) {
                val isCyrillic = ch.code in 0x0400..0x04FF
                assertFalse("Zodiac '$zodiac' contains Cyrillic character '$ch'", isCyrillic)
            }
        }
    }

    @Test
    fun getKhmerDate_beYear_is_gregorianPlus544() {
        // Khmer New Year is in April; BE = Gregorian year + 544
        val kd = KhmerCalendarHelper.getKhmerDate(2026, 5, 1)
        assertTrue("BE year should be around 2569-2570", kd.BE in 2569..2570)
    }

    @Test
    fun getGregorianMonthDays_returnsCorrectCount() {
        assertEquals(31, KhmerCalendarHelper.getGregorianMonthDays(2026, 5).size)
        assertEquals(28, KhmerCalendarHelper.getGregorianMonthDays(2025, 2).size)
        assertEquals(29, KhmerCalendarHelper.getGregorianMonthDays(2024, 2).size) // leap year
        assertEquals(30, KhmerCalendarHelper.getGregorianMonthDays(2026, 4).size)
    }

    @Test
    fun getGregorianMonthDays_containsAuspiciousDays() {
        val days = KhmerCalendarHelper.getGregorianMonthDays(2026, 5)
        val auspicious = days.filter { it.isAuspicious }
        assertTrue("May 2026 should have at least one auspicious day", auspicious.isNotEmpty())
    }

    @Test
    fun getKhmerDate_nonHolidayIsNull() {
        // May 3 2026 is a regular day
        val kd = KhmerCalendarHelper.getKhmerDate(2026, 5, 3)
        assertNull("May 3 should not be a holiday", kd.holiday)
    }
}
