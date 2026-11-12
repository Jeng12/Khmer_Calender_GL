package com.aistudio.khmercalendar.calendar

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
    val holiday: String?         // E.g. "វិសាខបូជា"
)

object KhmerCalendarHelper {
    private const val MIN_SUPPORTED_YEAR = 1900
    private const val MAX_SUPPORTED_YEAR = 2200

    /** Gregorian years the lunar tables cover; callers must not navigate outside it. */
    val SUPPORTED_YEARS: IntRange = MIN_SUPPORTED_YEAR..MAX_SUPPORTED_YEAR

    /** First day (as a serial day) of one Khmer lunar month, and its length. */
    private data class Milestone(
        val serialDay: Int,
        val khmerMonthName: String,
        val length: Int
    )

    private val KH_DAYS = listOf("អាទិត្យ", "ចន្ទ", "អង្គារ", "ពុធ", "ព្រហស្បតិ៍", "សុក្រ", "សៅរ៍")
    private val KH_DAYS_SHORT = listOf("អា", "ច", "អ", "ព", "ព្រ", "សុ", "ស")
    private val EN_DAYS = listOf("Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday")

    // 12 Khmer zodiac names, index 0 = Rat. Index = (Gregorian animal year − 4) mod 12.
    private val ZODIAC_NAMES = listOf(
        "ឆ្នាំជូត",    // 0: Rat
        "ឆ្នាំឆ្លូវ",  // 1: Ox
        "ឆ្នាំខាល",   // 2: Tiger
        "ឆ្នាំថោះ",   // 3: Rabbit
        "ឆ្នាំរោង",   // 4: Dragon  (BE 2568, 2580…)
        "ឆ្នាំម្សាញ់", // 5: Snake   (BE 2569, 2581…)
        "ឆ្នាំមមី",    // 6: Horse   (BE 2570, 2582…)
        "ឆ្នាំមមែ",    // 7: Goat    (BE 2571, 2583…)
        "ឆ្នាំវក",     // 8: Monkey  (BE 2572, 2584…)
        "ឆ្នាំរកា",   // 9: Rooster (BE 2573, 2585…)
        "ឆ្នាំច",      // 10: Dog    (BE 2574, 2586…)
        "ឆ្នាំកុរ"     // 11: Pig    (BE 2575, 2587…)
    )

    // ─── Traditional Khmer lunisolar calendar (Chhankitek) ─────────────────────
    //
    // The Khmer calendar is arithmetical, not astronomical: months alternate
    // 29/30 days, and each year is one of three kinds, decided from the Buddhist
    // Era year by the Bodithey/Avoman rules:
    //   • normal     – 354 days
    //   • leap day   – 355 days (ជេស្ឋ has 30 days instead of 29)
    //   • leap month – 384 days (អាសាឍ is doubled: បឋមាសាឍ + ទុតិយាសាឍ)
    // Month starts are generated forward from a fixed epoch: 1 January 1900 was
    // ១ កើត ខែបុស្ស.

    private const val FIRST_ASADH = "បឋមាសាឍ"
    private const val SECOND_ASADH = "ទុតិយាសាឍ"

    private fun aharkun(be: Int): Int = Math.floorDiv(be * 292207 + 499, 800) + 4
    private fun kromthupul(be: Int): Int = 800 - Math.floorMod(be * 292207 + 499, 800)
    private fun avoman(be: Int): Int = Math.floorMod(aharkun(be) * 11 + 25, 692)
    private fun bodithey(be: Int): Int {
        val ahk = aharkun(be)
        return Math.floorMod(Math.floorDiv(ahk * 11 + 25, 692) + ahk + 29, 30)
    }

    /** 0 = regular, 1 = leap month, 2 = leap day, 3 = both (before deferral). */
    private fun boditheyLeap(be: Int): Int {
        val a = avoman(be)
        val b = bodithey(be)
        val solarLeap = kromthupul(be) <= 207
        val avomanLeap = if (solarLeap) a <= 126 else a <= 137 && avoman(be + 1) != 0
        var monthLeap = b >= 25 || b <= 5
        // 25 followed by 5: only the year with 5 takes the leap month.
        if (b == 25 && bodithey(be + 1) == 5) monthLeap = false
        // 24 followed by 6: the year with 24 takes the leap month.
        if (b == 24 && bodithey(be + 1) == 6) monthLeap = true
        return when {
            monthLeap && avomanLeap -> 3
            monthLeap -> 1
            avomanLeap -> 2
            else -> 0
        }
    }

    /**
     * Kind of Khmer year: 0 = normal, 1 = leap month, 2 = leap day. A year
     * cannot have both, so a leap day that coincides with a leap month is
     * carried over to the following year.
     */
    private fun yearKind(be: Int): Int = when (val leap = boditheyLeap(be)) {
        3 -> 1
        1, 2 -> leap
        else -> if (boditheyLeap(be - 1) == 3) 2 else 0
    }

    // ─── Month-start table ─────────────────────────────────────────────────────

    // Pre-built for 1900–2200; covers all practical dates without restriction
    private val milestones: List<Milestone> by lazy { buildMilestones(MIN_SUPPORTED_YEAR, MAX_SUPPORTED_YEAR) }

    // Serial day of Visak Bochea (១៥ កើត ខែពិសាខ) per Gregorian year; the
    // Buddhist Era advances on the following day.
    private val visakBocheaByYear = HashMap<Int, Int>()

    // Binary search: O(log n) instead of O(n) linear scan
    private fun findMilestone(sDay: Int): Milestone {
        val ms = milestones
        var lo = 0
        var hi = ms.size - 1
        while (lo < hi) {
            val mid = (lo + hi + 1) / 2
            if (ms[mid].serialDay <= sDay) lo = mid else hi = mid - 1
        }
        return ms[lo]
    }

    // LRU cache for monthly data — pure JVM LinkedHashMap, no Android dependency.
    // An access-ordered LinkedHashMap mutates on every read, and the app UI and the
    // home-screen widgets hit it from different threads, so all access is locked.
    private val monthCache: MutableMap<Long, List<KhmerDate>> =
        object : LinkedHashMap<Long, List<KhmerDate>>(16, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, List<KhmerDate>>) = size > 36
        }

    private fun monthCacheKey(year: Int, month: Int): Long = year.toLong() * 100 + month

    private fun buildMilestones(firstGregorianYear: Int, lastGregorianYear: Int): List<Milestone> {
        val result = ArrayList<Milestone>((lastGregorianYear - firstGregorianYear + 2) * 13)
        var start = getSerialDay(firstGregorianYear, 1, 1)   // 1 Jan 1900 = ១ កើត ខែបុស្ស
        fun add(name: String, length: Int) {
            result.add(Milestone(start, name, length))
            start += length
        }
        // One extra year so the last days of the final Gregorian year are covered.
        for (year in firstGregorianYear..lastGregorianYear + 1) {
            // ជេស្ឋ/អាសាឍ of this Gregorian year fall in BE year + 544.
            val kind = yearKind(year + 544)
            add("បុស្ស", 30)
            add("មាឃ", 29)
            add("ផល្គុន", 30)
            add("ចេត្រ", 29)
            visakBocheaByYear[year] = start + 14
            add("ពិសាខ", 30)
            add("ជេស្ឋ", if (kind == 2) 30 else 29)
            if (kind == 1) {
                add(FIRST_ASADH, 30)
                add(SECOND_ASADH, 30)
            } else {
                add("អាសាឍ", 30)
            }
            add("ស្រាពណ៍", 29)
            add("ភទ្របទ", 30)
            add("អស្សុជ", 29)
            add("កត្តិក", 30)
            add("មិគសិរ", 29)
        }
        return result
    }

    // ─── Public API ────────────────────────────────────────────────────────────

    fun getSerialDay(year: Int, month: Int, day: Int): Int {
        require(month in 1..12) { "Gregorian month must be in 1..12: $month" }
        require(day in 1..daysInGregorianMonth(year, month)) {
            "Invalid Gregorian day for $year-$month: $day"
        }
        var y = year
        var m = month
        if (m <= 2) { y -= 1; m += 12 }
        // March-based month index must start at 0 for the (153*idx+2)/5 cumulative-days
        // formula to stay continuous. After the Jan/Feb shift, m ranges 3..14, so use (m - 3).
        // Using `m` directly added a non-constant rounding offset that broke day counting
        // across the Feb→March boundary, throwing the weekday (and lunar) math off.
        return (365 * y) + (y / 4) - (y / 100) + (y / 400) + ((153 * (m - 3) + 2) / 5) + day
    }

    fun getKhmerDate(year: Int, month: Int, day: Int): KhmerDate {
        require(year in MIN_SUPPORTED_YEAR..MAX_SUPPORTED_YEAR) {
            "Supported Gregorian years are $MIN_SUPPORTED_YEAR..$MAX_SUPPORTED_YEAR: $year"
        }
        val sDay   = getSerialDay(year, month, day)
        // Sunday = 0. Offset +2 aligns the continuous serial-day count with the real
        // Gregorian weekday (verified against known reference dates).
        val dowIdx = ((sDay + 2) % 7 + 7) % 7

        val ms = findMilestone(sDay)

        // Day within the lunar month: 0..14 = ១–១៥ កើត, 15..29 = ១–១៥ រោច
        // (a 29-day month ends on ១៤ រោច).
        val offset    = sDay - ms.serialDay
        val isWaxing  = offset < 15

        val displayLunarDay = if (isWaxing) offset + 1 else offset - 14

        // The Buddhist Era advances the day after Visak Bochea; the animal year
        // advances at Khmer New Year in mid-April.
        val be = if (sDay > (visakBocheaByYear[year] ?: Int.MAX_VALUE)) year + 544 else year + 543
        val animalYear = if (month > 4 || (month == 4 && day >= khmerNewYearDay(year))) year else year - 1
        val zodiac = ZODIAC_NAMES[Math.floorMod(animalYear - 4, 12)]

        val lunarDayNameStr = "${toKhmerNumeral(displayLunarDay)} ${if (isWaxing) "កើត" else "រោច"}"

        val moonEmoji = when {
            offset == 0              -> "🌑"
            offset in 1..6           -> "🌒"
            offset == 7              -> "🌓"
            offset in 8..13          -> "🌔"
            offset == 14             -> "🌕"
            offset in 15..21         -> "🌖"
            offset == 22             -> "🌗"
            else                          -> "🌘"
        }

        // Holiday detection. A lunar festival can land on a fixed-date holiday
        // (Visak Bochea fell on Labour Day in 2026), so both are reported.
        val monthName = ms.khmerMonthName
        val lunarHoliday = when {
            monthName == "មាឃ" && isWaxing && displayLunarDay == 15 -> "បុណ្យមាឃបូជា (Meak Bochea)"
            monthName == "ពិសាខ" && isWaxing && displayLunarDay == 15 -> "បុណ្យវិសាខបូជា (Visak Bochea)"
            monthName == "ភទ្របទ" && !isWaxing && displayLunarDay == 15 -> "បុណ្យភ្ជុំបិណ្ឌ (Pchum Ben)"
            monthName == "កត្តិក" && isWaxing && displayLunarDay == 15 -> "បុណ្យអុំទូក (Water Festival)"
            else -> null
        }
        val fixedHoliday = when {
            month == 4 && day in khmerNewYearDay(year)..16 -> "ចូលឆ្នាំថ្មីប្រពៃណីជាតិ"
            month == 11 && day == 9 -> "ទិវាបុណ្យឯករាជ្យជាតិ"
            month == 1 && day == 7 -> "ទិវាជ័យជម្នះលើរបបប្រល័យពូជសាសន៍"
            month == 5 && day == 1 -> "ទិវាពលកម្មអន្តរជាតិ"
            month == 6 && day == 18 -> "ព្រះរាជពិធីបុណ្យចម្រើនព្រះជន្ម សម្តេចម៉ែ"
            month == 9 && day == 24 -> "ទិវារដ្ឋធម្មនុញ្ញ"
            month == 10 && day == 15 -> "ទិវាគោរពព្រះវិញ្ញាណក្ខន្ធ ព្រះបរមរតនកោដ្ឋ"
            month == 10 && day == 29 -> "ព្រះរាជពិធីគ្រងព្រះបរមរាជសម្បត្តិ ព្រះមហាក្សត្រ"
            else -> null
        }
        val holiday = when {
            lunarHoliday != null && fixedHoliday != null -> "$lunarHoliday · $fixedHoliday"
            else -> lunarHoliday ?: fixedHoliday
        }

        return KhmerDate(
            day = day, month = month, year = year,
            dayOfWeek = KH_DAYS[dowIdx],
            dayOfWeekEn = EN_DAYS[dowIdx],
            dayOfWeekShort = KH_DAYS_SHORT[dowIdx],
            lunarDayVal = displayLunarDay,
            isWaxing = isWaxing,
            lunarDayName = lunarDayNameStr,
            lunarMonthName = ms.khmerMonthName,
            zodiac = zodiac,
            BE = be,
            moonEmoji = moonEmoji,
            holiday = holiday
        )
    }

    fun toKhmerNumeral(n: Int): String {
        val digits = n.toString()
        val out = CharArray(digits.length)
        for (i in digits.indices) {
            val c = digits[i]
            out[i] = if (c in '0'..'9') '០' + (c - '0') else c
        }
        return String(out)
    }

    fun getGregorianMonthDays(year: Int, month: Int): List<KhmerDate> {
        require(year in MIN_SUPPORTED_YEAR..MAX_SUPPORTED_YEAR) {
            "Supported Gregorian years are $MIN_SUPPORTED_YEAR..$MAX_SUPPORTED_YEAR: $year"
        }
        require(month in 1..12) { "Gregorian month must be in 1..12: $month" }
        val key = monthCacheKey(year, month)
        synchronized(monthCache) { monthCache[key] }?.let { return it }
        val daysInMonth = daysInGregorianMonth(year, month)
        val result = (1..daysInMonth).map { getKhmerDate(year, month, it) }
        synchronized(monthCache) { monthCache[key] = result }
        return result
    }

    private fun daysInGregorianMonth(year: Int, month: Int): Int = when (month) {
        1, 3, 5, 7, 8, 10, 12 -> 31
        4, 6, 9, 11 -> 30
        2 -> if ((year % 4 == 0 && year % 100 != 0) || year % 400 == 0) 29 else 28
        else -> throw IllegalArgumentException("Gregorian month must be in 1..12: $month")
    }

    /**
     * First day of Khmer New Year (មហាសង្ក្រាន្ត). It falls on 14 April, or on
     * 13 April in Gregorian leap years, for the decades around the present.
     */
    private fun khmerNewYearDay(year: Int): Int =
        if ((year % 4 == 0 && year % 100 != 0) || year % 400 == 0) 13 else 14

    // Force lazy milestone init off the main thread
    fun warmUp() {
        milestones
    }
}
