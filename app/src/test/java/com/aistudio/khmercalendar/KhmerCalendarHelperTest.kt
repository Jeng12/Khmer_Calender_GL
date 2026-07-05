package com.aistudio.khmercalendar

import com.aistudio.khmercalendar.calendar.KhmerCalendarHelper
import com.aistudio.khmercalendar.calendar.KhmerDate
import org.junit.Assert.*
import org.junit.Test

class KhmerCalendarHelperTest {

    // ─── Khmer Numeral Conversion ──────────────────────────────────────────────

    @Test
    fun toKhmerNumeral_convertsDigitsCorrectly() {
        assertEquals("១", KhmerCalendarHelper.toKhmerNumeral(1))
        assertEquals("១៥", KhmerCalendarHelper.toKhmerNumeral(15))
        assertEquals("២០២៦", KhmerCalendarHelper.toKhmerNumeral(2026))
        assertEquals("០", KhmerCalendarHelper.toKhmerNumeral(0))
    }

    @Test
    fun toKhmerNumeral_allTenDigits() {
        assertEquals("០", KhmerCalendarHelper.toKhmerNumeral(0))
        assertEquals("១", KhmerCalendarHelper.toKhmerNumeral(1))
        assertEquals("២", KhmerCalendarHelper.toKhmerNumeral(2))
        assertEquals("៣", KhmerCalendarHelper.toKhmerNumeral(3))
        assertEquals("៤", KhmerCalendarHelper.toKhmerNumeral(4))
        assertEquals("៥", KhmerCalendarHelper.toKhmerNumeral(5))
        assertEquals("៦", KhmerCalendarHelper.toKhmerNumeral(6))
        assertEquals("៧", KhmerCalendarHelper.toKhmerNumeral(7))
        assertEquals("៨", KhmerCalendarHelper.toKhmerNumeral(8))
        assertEquals("៩", KhmerCalendarHelper.toKhmerNumeral(9))
    }

    @Test
    fun toKhmerNumeral_largeNumber() {
        val result = KhmerCalendarHelper.toKhmerNumeral(1234567890)
        assertEquals("១២៣៤៥៦៧៨៩០", result)
        assertEquals(10, result.length)
    }

    // ─── Serial Day Arithmetic ──────────────────────────────────────────────────

    @Test
    fun getSerialDay_isConsistent() {
        val sd1 = KhmerCalendarHelper.getSerialDay(2026, 5, 15)
        val sd2 = KhmerCalendarHelper.getSerialDay(2026, 5, 16)
        assertEquals("Consecutive days differ by 1", 1, sd2 - sd1)
    }

    @Test
    fun getSerialDay_monthBoundary() {
        val mayLast = KhmerCalendarHelper.getSerialDay(2026, 5, 31)
        val junFirst = KhmerCalendarHelper.getSerialDay(2026, 6, 1)
        assertEquals(1, junFirst - mayLast)
    }

    @Test
    fun getSerialDay_yearBoundary() {
        val dec31 = KhmerCalendarHelper.getSerialDay(2025, 12, 31)
        val jan01 = KhmerCalendarHelper.getSerialDay(2026, 1, 1)
        assertEquals(1, jan01 - dec31)
    }

    @Test
    fun getSerialDay_leapYearFebBoundary() {
        // 2024 is a leap year; Feb has 29 days
        val feb29 = KhmerCalendarHelper.getSerialDay(2024, 2, 29)
        val mar01 = KhmerCalendarHelper.getSerialDay(2024, 3, 1)
        assertEquals(1, mar01 - feb29)
    }

    @Test
    fun getSerialDay_nonLeapYearFebBoundary() {
        // 2025 is not a leap year; Feb has 28 days
        val feb28 = KhmerCalendarHelper.getSerialDay(2025, 2, 28)
        val mar01 = KhmerCalendarHelper.getSerialDay(2025, 3, 1)
        assertEquals(1, mar01 - feb28)
    }

    @Test
    fun getSerialDay_century_nonLeap() {
        // 1900 is divisible by 100 but not 400 → not a leap year
        val feb28 = KhmerCalendarHelper.getSerialDay(1900, 2, 28)
        val mar01 = KhmerCalendarHelper.getSerialDay(1900, 3, 1)
        assertEquals(1, mar01 - feb28)
    }

    @Test
    fun getSerialDay_400year_isLeap() {
        // 2000 is divisible by 400 → leap year
        val feb29 = KhmerCalendarHelper.getSerialDay(2000, 2, 29)
        val mar01 = KhmerCalendarHelper.getSerialDay(2000, 3, 1)
        assertEquals(1, mar01 - feb29)
    }

    // ─── Day of Week ─────────────────────────────────────────────────────────

    @Test
    fun getKhmerDate_dayOfWeek_knownDate() {
        val kd = KhmerCalendarHelper.getKhmerDate(2026, 5, 25)
        assertEquals("Monday", kd.dayOfWeekEn)
        assertEquals("ចន្ទ", kd.dayOfWeek)
    }

    @Test
    fun getKhmerDate_dayOfWeek_sunday() {
        // January 1, 2023 was a Sunday
        val kd = KhmerCalendarHelper.getKhmerDate(2023, 1, 1)
        assertEquals("Sunday", kd.dayOfWeekEn)
        assertEquals("អាទិត្យ", kd.dayOfWeek)
    }

    @Test
    fun getKhmerDate_dayOfWeek_saturday() {
        // January 7, 2023 was a Saturday
        val kd = KhmerCalendarHelper.getKhmerDate(2023, 1, 7)
        assertEquals("Saturday", kd.dayOfWeekEn)
        assertEquals("សៅរ៍", kd.dayOfWeek)
    }

    @Test
    fun getKhmerDate_shortDayOfWeek_hasValue() {
        val kd = KhmerCalendarHelper.getKhmerDate(2026, 5, 25)
        assertTrue("Short day name should not be empty", kd.dayOfWeekShort.isNotEmpty())
        assertEquals("ច", kd.dayOfWeekShort)  // Monday short form
    }

    @Test
    fun getKhmerDate_dayOfWeekCycleIsCorrect() {
        // 7 consecutive days should cover all 7 weekdays
        val weekdays = (0..6).map { offset ->
            KhmerCalendarHelper.getKhmerDate(2026, 5, 25 + offset).dayOfWeekEn
        }
        assertTrue(weekdays.contains("Monday"))
        assertTrue(weekdays.contains("Tuesday"))
        assertTrue(weekdays.contains("Wednesday"))
        assertTrue(weekdays.contains("Thursday"))
        assertTrue(weekdays.contains("Friday"))
        assertTrue(weekdays.contains("Saturday"))
        assertTrue(weekdays.contains("Sunday"))
    }

    // ─── Lunar Day Values ────────────────────────────────────────────────────

    @Test
    fun getKhmerDate_returns_validLunarDay() {
        val kd = KhmerCalendarHelper.getKhmerDate(2026, 5, 15)
        assertTrue("Lunar day should be 1-15", kd.lunarDayVal in 1..15)
        assertTrue("Lunar month should not be empty", kd.lunarMonthName.isNotEmpty())
    }

    @Test
    fun getKhmerDate_lunarDayVal_neverExceedsFifteen() {
        // All days in a year should have lunar day values 1-15
        for (month in 1..12) {
            val days = KhmerCalendarHelper.getGregorianMonthDays(2026, month)
            for (kd in days) {
                assertTrue("Day ${kd.day}/${kd.month} has lunarDayVal=${kd.lunarDayVal} out of range",
                    kd.lunarDayVal in 1..15)
            }
        }
    }

    @Test
    fun getKhmerDate_lunarDayName_containsKhmerText() {
        val kd = KhmerCalendarHelper.getKhmerDate(2026, 5, 15)
        val name = kd.lunarDayName
        assertTrue("Lunar day name should contain Khmer text", name.isNotEmpty())
        assertTrue("Should contain 'កើត' or 'រោច'",
            name.contains("កើត") || name.contains("រោច"))
    }

    @Test
    fun getKhmerDate_waxingWaningTransition() {
        // Find a new moon day and verify waxing starts
        // The day after new moon should be waxing
        val days = KhmerCalendarHelper.getGregorianMonthDays(2026, 5)
        val waxingDays = days.filter { it.isWaxing }
        val waningDays = days.filter { !it.isWaxing }
        assertTrue("May 2026 should have waxing days", waxingDays.isNotEmpty())
        assertTrue("May 2026 should have waning days", waningDays.isNotEmpty())
    }

    // ─── Buddhist Era and Zodiac ─────────────────────────────────────────────

    @Test
    fun getKhmerDate_beYear_is_gregorianPlus544() {
        val kd = KhmerCalendarHelper.getKhmerDate(2026, 5, 1)
        assertTrue("BE year should be around 2569-2570", kd.BE in 2569..2570)
    }

    @Test
    fun getKhmerDate_beYear_2025() {
        val kd = KhmerCalendarHelper.getKhmerDate(2025, 6, 1)
        assertTrue("BE year should be around 2568-2569", kd.BE in 2568..2569)
    }

    @Test
    fun getKhmerDate_zodiacName_doesNotContainCyrillic() {
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
    fun getKhmerDate_zodiacName_startsWithKhmerPrefix() {
        val kd = KhmerCalendarHelper.getKhmerDate(2026, 5, 1)
        assertTrue("Zodiac should start with 'ឆ្នាំ'", kd.zodiac.startsWith("ឆ្នាំ"))
    }

    @Test
    fun getKhmerDate_zodiacName_isOneOf12Animals() {
        val validZodiacs = setOf(
            "ឆ្នាំជូត", "ឆ្នាំឆ្លូវ", "ឆ្នាំខាល", "ឆ្នាំថោះ",
            "ឆ្នាំរោង", "ឆ្នាំម្សាញ់", "ឆ្នាំមមី", "ឆ្នាំមមែ",
            "ឆ្នាំវក", "ឆ្នាំរកា", "ឆ្នាំច", "ឆ្នាំកុរ"
        )
        for (year in 2019..2035) {
            val kd = KhmerCalendarHelper.getKhmerDate(year, 6, 1)
            assertTrue("Year $year zodiac '${kd.zodiac}' should be one of the 12 animals",
                kd.zodiac in validZodiacs)
        }
    }

    @Test
    fun getKhmerDate_zodiacCycle_repeatsEvery12Years() {
        // Same point in calendar 12 years apart should have same zodiac
        val kd2024 = KhmerCalendarHelper.getKhmerDate(2024, 8, 1)
        val kd2036 = KhmerCalendarHelper.getKhmerDate(2036, 8, 1)
        assertEquals("Zodiac should repeat every 12 years", kd2024.zodiac, kd2036.zodiac)
    }

    // ─── Month Structure ─────────────────────────────────────────────────────

    @Test
    fun getGregorianMonthDays_returnsCorrectCount() {
        assertEquals(31, KhmerCalendarHelper.getGregorianMonthDays(2026, 5).size)
        assertEquals(28, KhmerCalendarHelper.getGregorianMonthDays(2025, 2).size)
        assertEquals(29, KhmerCalendarHelper.getGregorianMonthDays(2024, 2).size) // leap year
        assertEquals(30, KhmerCalendarHelper.getGregorianMonthDays(2026, 4).size)
    }

    @Test
    fun getGregorianMonthDays_correctForAllMonths2026() {
        val expected = mapOf(
            1 to 31, 2 to 28, 3 to 31, 4 to 30, 5 to 31, 6 to 30,
            7 to 31, 8 to 31, 9 to 30, 10 to 31, 11 to 30, 12 to 31
        )
        for ((month, days) in expected) {
            assertEquals("Month $month 2026 should have $days days",
                days, KhmerCalendarHelper.getGregorianMonthDays(2026, month).size)
        }
    }

    @Test
    fun getGregorianMonthDays_daysAreSequential() {
        val days = KhmerCalendarHelper.getGregorianMonthDays(2026, 5)
        for (i in 0 until days.size - 1) {
            assertEquals("Day sequence should be consecutive",
                days[i].day + 1, days[i + 1].day)
        }
    }

    @Test
    fun getGregorianMonthDays_lunarMonthNameNeverEmpty() {
        val days = KhmerCalendarHelper.getGregorianMonthDays(2026, 5)
        for (kd in days) {
            assertTrue("Lunar month name should not be empty for day ${kd.day}",
                kd.lunarMonthName.isNotEmpty())
        }
    }

    // ─── Holiday Detection ───────────────────────────────────────────────────

    @Test
    fun getKhmerDate_builtinHoliday_khmerNewYear() {
        val kd = KhmerCalendarHelper.getKhmerDate(2026, 4, 14)
        assertNotNull("Khmer New Year should be a holiday", kd.holiday)
        assertTrue(kd.holiday!!.contains("ចូលឆ្នាំ"))
    }

    @Test
    fun getKhmerDate_khmerNewYear_allThreeDays() {
        for (day in 14..16) {
            val kd = KhmerCalendarHelper.getKhmerDate(2026, 4, day)
            assertNotNull("Apr $day should be Khmer New Year holiday", kd.holiday)
            assertTrue(kd.holiday!!.contains("ចូលឆ្នាំ"))
        }
    }

    @Test
    fun getKhmerDate_independenceDay_nov9() {
        val kd = KhmerCalendarHelper.getKhmerDate(2026, 11, 9)
        assertNotNull("Nov 9 should be Independence Day", kd.holiday)
        assertTrue(kd.holiday!!.contains("ឯករាជ្យ"))
    }

    @Test
    fun getKhmerDate_victoryDay_jan7() {
        val kd = KhmerCalendarHelper.getKhmerDate(2026, 1, 7)
        assertNotNull("Jan 7 should be Victory Day", kd.holiday)
        assertTrue(kd.holiday!!.contains("ជ័យជម្នះ"))
    }

    @Test
    fun getKhmerDate_laborDay_may1() {
        val kd = KhmerCalendarHelper.getKhmerDate(2026, 5, 1)
        assertNotNull("May 1 should be Labor Day", kd.holiday)
        assertTrue(kd.holiday!!.contains("ពលកម្ម"))
    }

    @Test
    fun getKhmerDate_constitutionDay_sep24() {
        val kd = KhmerCalendarHelper.getKhmerDate(2026, 9, 24)
        assertNotNull("Sep 24 should be Constitution Day", kd.holiday)
        assertTrue(kd.holiday!!.contains("រដ្ឋធម្មនុញ្ញ"))
    }

    @Test
    fun getKhmerDate_nonHolidayIsNull() {
        val kd = KhmerCalendarHelper.getKhmerDate(2026, 5, 3)
        assertNull("May 3 should not be a holiday", kd.holiday)
    }

    @Test
    fun getKhmerDate_dayBeforeKhmerNewYearIsNull() {
        // Apr 13 is NOT a public holiday (Apr 14–16 are)
        val kd = KhmerCalendarHelper.getKhmerDate(2026, 4, 13)
        assertNull("Apr 13 should not be Khmer New Year holiday", kd.holiday)
    }

    @Test
    fun getKhmerDate_visakBochea_isOnFullMoonOfVisakha() {
        // Visak Bochea = 15th waxing day of Visakha lunar month
        // Find it by scanning months around April-May
        var found = false
        for (month in 4..6) {
            val days = KhmerCalendarHelper.getGregorianMonthDays(2026, month)
            for (kd in days) {
                if (kd.holiday?.contains("វិសាខបូជា") == true) {
                    assertEquals("Visak Bochea should be on waxing day 15", 15, kd.lunarDayVal)
                    assertTrue("Visak Bochea should be waxing", kd.isWaxing)
                    assertEquals("Moon should be full on Visak Bochea", "🌕", kd.moonEmoji)
                    found = true
                }
            }
        }
        assertTrue("Visak Bochea should be found in April-June 2026", found)
    }

    // ─── Moon Phase ──────────────────────────────────────────────────────────

    @Test
    fun getKhmerDate_moonEmoji_isValidUnicode() {
        val validEmojis = setOf("🌑", "🌒", "🌓", "🌔", "🌕", "🌖", "🌗", "🌘")
        val days = KhmerCalendarHelper.getGregorianMonthDays(2026, 5)
        for (kd in days) {
            assertTrue("Moon emoji '${kd.moonEmoji}' should be a valid phase emoji",
                kd.moonEmoji in validEmojis)
        }
    }

    @Test
    fun getKhmerDate_fullMoonOnWaxingDay15() {
        // Any day with waxing=true and lunarDayVal=15 should show full moon
        val days = KhmerCalendarHelper.getGregorianMonthDays(2026, 5)
        val fullMoonDays = days.filter { it.isWaxing && it.lunarDayVal == 15 }
        for (kd in fullMoonDays) {
            assertEquals("Full moon emoji expected on waxing day 15", "🌕", kd.moonEmoji)
        }
    }


    // ─── Edge Cases ──────────────────────────────────────────────────────────

    @Test
    fun getKhmerDate_firstDayOfCoveredRange_2019() {
        // Earliest year in milestone range — should not throw
        val kd = KhmerCalendarHelper.getKhmerDate(2019, 1, 1)
        assertNotNull(kd)
        assertTrue(kd.lunarMonthName.isNotEmpty())
    }

    @Test
    fun getKhmerDate_lastDayOfCoveredRange_2036() {
        // Latest year in milestone range — should not throw
        val kd = KhmerCalendarHelper.getKhmerDate(2036, 12, 31)
        assertNotNull(kd)
        assertTrue(kd.lunarMonthName.isNotEmpty())
    }

    @Test
    fun getKhmerDate_leapYear2024_feb29() {
        // Feb 29 exists in 2024 (leap year)
        val kd = KhmerCalendarHelper.getKhmerDate(2024, 2, 29)
        assertNotNull(kd)
        assertEquals(2024, kd.year)
        assertEquals(2, kd.month)
        assertEquals(29, kd.day)
    }

    @Test
    fun getKhmerDate_rejectsInvalidGregorianDate() {
        assertThrows(IllegalArgumentException::class.java) {
            KhmerCalendarHelper.getKhmerDate(2026, 2, 30)
        }
    }

    @Test
    fun getGregorianMonthDays_rejectsOutOfRangeYear() {
        assertThrows(IllegalArgumentException::class.java) {
            KhmerCalendarHelper.getGregorianMonthDays(1899, 12)
        }
    }

    @Test
    fun getKhmerDate_gregorianFieldsPreserved() {
        // Verify the input Gregorian date is echoed back correctly
        val kd = KhmerCalendarHelper.getKhmerDate(2026, 7, 15)
        assertEquals(2026, kd.year)
        assertEquals(7, kd.month)
        assertEquals(15, kd.day)
    }

    @Test
    fun getKhmerDate_leapLunarYear2027_hasExtendedMonths() {
        // 2027 is a Khmer lunar leap year with 13 months (extra Asadha)
        // Scan to find "អាសាឍ ១" and "អាសាឍ ២" lunar month names
        val monthNames = mutableSetOf<String>()
        for (month in 1..12) {
            KhmerCalendarHelper.getGregorianMonthDays(2027, month).forEach {
                monthNames.add(it.lunarMonthName)
            }
        }
        assertTrue("Leap year 2027 should contain first Asadha month",
            monthNames.contains("អាសាឍ ១"))
        assertTrue("Leap year 2027 should contain second Asadha month",
            monthNames.contains("អាសាឍ ២"))
    }

    @Test
    fun getKhmerDate_normalYear_hasExactly12LunarMonths() {
        // 2026 is a normal (non-leap) Khmer year — should have exactly 12 distinct lunar months
        val monthNames = mutableSetOf<String>()
        for (month in 4..12) {
            KhmerCalendarHelper.getGregorianMonthDays(2026, month).forEach {
                monthNames.add(it.lunarMonthName)
            }
        }
        // Normal year should NOT contain split Asadha months
        assertFalse("Normal year 2026 should not have 'អាសាឍ ១'", monthNames.contains("អាសាឍ ១"))
        assertFalse("Normal year 2026 should not have 'អាសាឍ ២'", monthNames.contains("អាសាឍ ២"))
    }

    @Test
    fun getKhmerDate_khmerNewYearBeYear_increments() {
        // BE year should increment at Khmer New Year (around April 13-16)
        val beforeNewYear = KhmerCalendarHelper.getKhmerDate(2026, 4, 1)
        val afterNewYear  = KhmerCalendarHelper.getKhmerDate(2026, 5, 1)
        assertTrue("BE year should be ≥ previous BE after new year",
            afterNewYear.BE >= beforeNewYear.BE)
    }
}
