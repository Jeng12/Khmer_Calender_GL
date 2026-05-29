package com.example

import kotlin.math.floor
import kotlin.math.sin

data class KhmerDate(
    val day: Int,
    val month: Int,
    val year: Int,
    val dayOfWeek: String,       // "អាទិត្យ", "ចន្ទ", "អង្គារ", "ពុធ", "ព្រហស្បតិ៍", "សុក្រ", "សៅរ៍"
    val dayOfWeekEn: String,     // "Sunday", "Monday", etc.
    val dayOfWeekShort: String,  // "អា", "ច", "អ", "ព", "ព្រ", "សុ", "ស"
    val lunarDayVal: Int,        // 1 - 15
    val isWaxing: Boolean,       // true = កើត, false = រោច
    val lunarDayName: String,    // E.g. "១៥ កើត" or "៦ រោច"
    val lunarMonthName: String,  // E.g. "ពិសាខ"
    val zodiac: String,          // "ឆ្នាំдето"
    val BE: Int,                 // 2570
    val moonEmoji: String,       // "🌕"
    val holiday: String?,        // E.g. "វិសាខបូជា"
    val isAuspicious: Boolean,
    val auspiciousType: String?  // "ពិធីមង្គលការ", "ឡើងផ្ទះថ្មី", "បើកអាជីវកម្ម"
)

object KhmerCalendarHelper {

    private data class Milestone(
        val serialDay: Int,
        val khmerMonthName: String,
        val length: Int,
        val be: Int,
        val zodiac: String
    )

    private val KH_DAYS = listOf("អាទិត្យ", "ចន្ទ", "អង្គារ", "ពុធ", "ព្រហស្បតិ៍", "សុក្រ", "សៅរ៍")
    private val KH_DAYS_SHORT = listOf("អា", "ច", "អ", "ព", "ព្រ", "សុ", "ស")
    private val EN_DAYS = listOf("Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday")
    private val KHMER_NUMERAL_MAP = mapOf(
        '0' to '០', '1' to '១', '2' to '២', '3' to '៣', '4' to '៤',
        '5' to '៥', '6' to '៦', '7' to '៧', '8' to '៨', '9' to '៩'
    )

    // 12 Khmer zodiac names, index 0 = Rat. Formula: (BE % 12 + 4) % 12 gives index.
    private val ZODIAC_NAMES = listOf(
        "ឆ្នាំជូត",    // 0: Rat
        "ឆ្នាំឆ្លូវ",  // 1: Ox
        "ឆ្នាំខាល",   // 2: Tiger
        "ឆ្នាំថោះ",   // 3: Rabbit
        "ឆ្នាំរោង",   // 4: Dragon  (BE 2568, 2580…)
        "ឆ្នាំម្សាញ់", // 5: Snake   (BE 2569, 2581…)
        "ឆ្នាំдето",   // 6: Horse   (BE 2570, 2582…)
        "ឆ្នាំមមី",    // 7: Goat    (BE 2571, 2583…)
        "ឆ្នាំវក",     // 8: Monkey  (BE 2572, 2584…)
        "ឆ្នាំរកា",   // 9: Rooster (BE 2573, 2585…)
        "ឆ្នាំច",      // 10: Dog    (BE 2574, 2586…)
        "ឆ្នាំកុរ"     // 11: Pig    (BE 2575, 2587…)
    )

    // Normal-year and leap-year month name sequences
    private val MONTH_NAMES_NORMAL = listOf(
        "ចេត្រ", "ពិសាខ", "ជេស្ឋ", "អាសាឍ",
        "ស្រាពណ៍", "ភទ្របទ", "អស្សុជ", "កត្តិក",
        "មិគសិរ", "បុស្ស", "មាឃ", "ផល្គុន"
    )
    private val MONTH_NAMES_LEAP = listOf(
        "ចេត្រ", "ពិសាខ", "ជេស្ឋ", "អាសាឍ ១", "អាសាឍ ២",
        "ស្រាពណ៍", "ភទ្របទ", "អស្សុជ", "កត្តិក",
        "មិគសិរ", "បុស្ស", "មាឃ", "ផល្គុន"
    )

    // ─── Astronomical new moon calculator (Meeus, Astronomical Algorithms ch.49) ───

    // k=0 → JDE 2451550.09766 ≈ Jan 6.6, 2000 UT (new moon reference)
    private fun newMoonJDE(k: Double): Double {
        val T = k / 1236.85
        val T2 = T * T
        val T3 = T2 * T
        val T4 = T3 * T

        var jde = 2451550.09766 + 29.530588861 * k +
                  0.00015437 * T2 - 0.000000150 * T3 + 0.00000000073 * T4

        val E = 1.0 - 0.002516 * T - 0.0000074 * T2
        val M  = Math.toRadians(2.5534 + 29.10535670 * k - 0.0000014 * T2 - 0.00000011 * T3)
        val Mp = Math.toRadians(201.5643 + 385.81693528 * k + 0.0107582 * T2 + 0.00001238 * T3 - 0.000000058 * T4)
        val F  = Math.toRadians(160.7108 + 390.67050284 * k - 0.0016118 * T2 - 0.00000227 * T3 + 0.000000011 * T4)
        val Om = Math.toRadians(124.7746 - 1.56375588 * k + 0.0020672 * T2 + 0.00000215 * T3)

        jde += -0.40720 * sin(Mp) +
                0.17241 * E * sin(M) +
                0.01608 * sin(2.0 * Mp) +
                0.01039 * sin(2.0 * F) +
                0.00739 * E * sin(Mp - M) -
                0.00514 * E * sin(Mp + M) +
                0.00208 * E * E * sin(2.0 * M) -
                0.00111 * sin(Mp - 2.0 * F) -
                0.00057 * sin(Mp + 2.0 * F) +
                0.00056 * E * sin(2.0 * Mp + M) -
                0.00042 * sin(3.0 * Mp) +
                0.00042 * E * sin(M + 2.0 * F) +
                0.00038 * E * sin(M - 2.0 * F) -
                0.00024 * E * sin(2.0 * Mp - M) -
                0.00017 * sin(Om)
        return jde
    }

    // Convert JDE (UT) → Gregorian calendar date in Cambodia time (UTC+7)
    private fun jdeToGregorian(jde: Double): Triple<Int, Int, Int> {
        val jdLocal = jde + 7.0 / 24.0   // shift to Cambodia time
        val Z = floor(jdLocal + 0.5).toLong()
        val A = if (Z < 2299161L) Z else {
            val alpha = floor((Z - 1867216.25) / 36524.25).toLong()
            Z + 1L + alpha - alpha / 4L
        }
        val B = A + 1524L
        val C = floor((B - 122.1) / 365.25).toLong()
        val D = floor(365.25 * C).toLong()
        val E = floor((B - D) / 30.6001).toLong()

        val day   = (B - D - floor(30.6001 * E).toLong()).toInt()
        val month = if (E < 14L) (E - 1L).toInt() else (E - 13L).toInt()
        val year  = if (month > 2) (C - 4716L).toInt() else (C - 4715L).toInt()
        return Triple(year, month, day)
    }

    // ─── Dynamic milestone builder ─────────────────────────────────────────────

    private val milestones: List<Milestone> by lazy { buildMilestones(2019, 2036) }

    private fun buildMilestones(firstGregorianYear: Int, lastGregorianYear: Int): List<Milestone> {
        // Collect all new moons whose Cambodia date falls in (firstGregorianYear-1)..(lastGregorianYear+1)
        val kStart = kotlin.math.round((firstGregorianYear - 2001) * 12.37).toInt() - 2
        val kEnd   = kotlin.math.round((lastGregorianYear  - 1999) * 12.37).toInt() + 2

        data class NM(val year: Int, val month: Int, val day: Int, val sd: Int)

        val newMoons = mutableListOf<NM>()
        for (k in kStart..kEnd) {
            val (y, m, d) = jdeToGregorian(newMoonJDE(k.toDouble()))
            if (y in (firstGregorianYear - 1)..(lastGregorianYear + 1)) {
                newMoons.add(NM(y, m, d, getSerialDay(y, m, d)))
            }
        }
        newMoons.sortBy { it.sd }

        // Identify Chaitra new moons: month that contains Khmer New Year ≈ Apr 14
        // Rule: new moon falls between Mar 15 and Apr 14 (inclusive)
        val chaitraIndices = newMoons.indices.filter { i ->
            val nm = newMoons[i]
            (nm.month == 3 && nm.day >= 15) || (nm.month == 4 && nm.day <= 14)
        }

        val result = mutableListOf<Milestone>()

        for (ci in 0 until chaitraIndices.size - 1) {
            val startIdx = chaitraIndices[ci]
            val endIdx   = chaitraIndices[ci + 1]  // exclusive (= next Chaitra)
            val monthsInYear = endIdx - startIdx   // 12 normal, 13 leap
            val isLeap = monthsInYear == 13
            val names  = if (isLeap) MONTH_NAMES_LEAP else MONTH_NAMES_NORMAL

            // BE year: Gregorian year of Chaitra + 544
            val be = newMoons[startIdx].year + 544
            val zodiac = ZODIAC_NAMES[((be % 12) + 4 + 12) % 12]

            for (pos in 0 until monthsInYear) {
                val nm   = newMoons[startIdx + pos]
                val next = newMoons[startIdx + pos + 1]
                val len  = next.sd - nm.sd   // actual days in this lunar month
                val name = names.getOrElse(pos) { "ចេត្រ" }
                result.add(Milestone(nm.sd, name, len, be, zodiac))
            }
        }

        result.sortBy { it.serialDay }
        return result
    }

    // ─── Public API ────────────────────────────────────────────────────────────

    fun getSerialDay(year: Int, month: Int, day: Int): Int {
        var y = year
        var m = month
        if (m <= 2) { y -= 1; m += 12 }
        return (365 * y) + (y / 4) - (y / 100) + (y / 400) + ((153 * m + 2) / 5) + day
    }

    fun getKhmerDate(year: Int, month: Int, day: Int): KhmerDate {
        val sDay   = getSerialDay(year, month, day)
        val dowIdx = ((sDay + 4) % 7 + 7) % 7

        val ms = milestones.lastOrNull { it.serialDay <= sDay }
            ?: Milestone(getSerialDay(2026, 5, 11), "ពិសាខ", 30, 2570, ZODIAC_NAMES[6])

        val offset    = sDay - ms.serialDay
        val isWaxing  = (offset % 30) < 15

        val displayLunarDay = if (isWaxing) (offset % 30) + 1 else (offset % 30) - 14

        val lunarDayNameStr = "${toKhmerNumeral(displayLunarDay)} ${if (isWaxing) "កើត" else "រោច"}"

        val moonEmoji = when {
            offset % 30 == 0              -> "🌑"
            offset % 30 in 1..6           -> "🌒"
            offset % 30 == 7              -> "🌓"
            offset % 30 in 8..13          -> "🌔"
            offset % 30 == 14             -> "🌕"
            offset % 30 in 15..21         -> "🌖"
            offset % 30 == 22             -> "🌗"
            else                          -> "🌘"
        }

        // Holiday detection
        var holiday: String? = null
        if (month == 4 && day in 14..16) {
            holiday = "ចូលឆ្នាំថ្មីប្រពៃណីជាតិ"
        } else if (ms.khmerMonthName == "មាឃ" && isWaxing && displayLunarDay == 15) {
            holiday = "បុណ្យមាឃបូជា (Meak Bochea)"
        } else if (ms.khmerMonthName == "ពិសាខ" && isWaxing && displayLunarDay == 15) {
            holiday = "បុណ្យវិសាខបូជា (Visak Bochea)"
        } else if ((ms.khmerMonthName == "ភទ្របទ") && !isWaxing && displayLunarDay == 15) {
            holiday = "បុណ្យភ្ជុំបិណ្ឌ (Pchum Ben)"
        } else if (ms.khmerMonthName == "កត្តិក" && isWaxing && displayLunarDay == 15) {
            holiday = "បុណ្យអុំទូក (Water Festival)"
        } else if (month == 11 && day == 9) {
            holiday = "ទិវាបុណ្យឯករាជ្យជាតិ"
        } else if (month == 1 && day == 7) {
            holiday = "ទិវាជ័យជម្នះលើរបបប្រល័យពូជសាសន៍"
        } else if (month == 5 && day == 1) {
            holiday = "ទិវាពលកម្មអន្តរជាតិ"
        } else if (month == 6 && day == 18) {
            holiday = "ព្រះរាជពិធីបុណ្យចម្រើនព្រះជន្ម សម្តេចម៉ែ"
        } else if (month == 9 && day == 24) {
            holiday = "ទិវារដ្ឋធម្មនុញ្ញ"
        } else if (month == 10 && day == 15) {
            holiday = "ទិវាគោរពព្រះវិញ្ញាណក្ខន្ធ ព្រះបរមរតនកោដ្ឋ"
        } else if (month == 10 && day == 29) {
            holiday = "ព្រះរាជពិធីគ្រងព្រះបរមរាជសម្បត្តិ ព្រះមហាក្សត្រ"
        }

        val isAuspicious = (offset % 30) in listOf(2, 6, 10, 11, 18, 25)
        val auspiciousType = if (isAuspicious) {
            when (displayLunarDay % 4) {
                0    -> "ពិធីមង្គលការ (Wedding)"
                1    -> "ឡើងផ្ទះថ្មី (Housewarming)"
                2    -> "បើកអាជីវកម្ម (Business)"
                else -> "ធ្វើដំណើរស្វែងរកលាភ (Travel)"
            }
        } else null

        return KhmerDate(
            day = day, month = month, year = year,
            dayOfWeek = KH_DAYS[dowIdx],
            dayOfWeekEn = EN_DAYS[dowIdx],
            dayOfWeekShort = KH_DAYS_SHORT[dowIdx],
            lunarDayVal = displayLunarDay,
            isWaxing = isWaxing,
            lunarDayName = lunarDayNameStr,
            lunarMonthName = ms.khmerMonthName,
            zodiac = ms.zodiac,
            BE = ms.be,
            moonEmoji = moonEmoji,
            holiday = holiday,
            isAuspicious = isAuspicious,
            auspiciousType = auspiciousType
        )
    }

    fun toKhmerNumeral(n: Int): String =
        n.toString().map { KHMER_NUMERAL_MAP[it] ?: it }.joinToString("")

    fun getGregorianMonthDays(year: Int, month: Int): List<KhmerDate> {
        val daysInMonth = when (month) {
            1, 3, 5, 7, 8, 10, 12 -> 31
            4, 6, 9, 11            -> 30
            2 -> if ((year % 4 == 0 && year % 100 != 0) || year % 400 == 0) 29 else 28
            else -> 30
        }
        return (1..daysInMonth).map { getKhmerDate(year, month, it) }
    }
}
