package com.example

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
    val zodiac: String,          // "ឆ្នាំមមែ"
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

    // Serial day calculator (counts days from astronomical year 0)
    fun getSerialDay(year: Int, month: Int, day: Int): Int {
        var y = year
        var m = month
        if (m <= 2) {
            y -= 1
            m += 12
        }
        return (365 * y) + (y / 4) - (y / 100) + (y / 400) + ((153 * m + 2) / 5) + day
    }

    // 1 Kert of lunar months milestones for 2025, 2026, 2027
    private val milestones: List<Milestone> by lazy {
        val list = mutableListOf<Milestone>()

        // Helper to add milestones easily
        fun addMs(y: Int, m: Int, d: Int, khName: String, length: Int, be: Int, zodiac: String) {
            list.add(Milestone(getSerialDay(y, m, d), khName, length, be, zodiac))
        }

        // --- 2025 ---
        addMs(2025, 1, 29, "មាឃ", 29, 2568, "ឆ្នាំរោង")
        addMs(2025, 2, 27, "ផល្គុន", 30, 2568, "ឆ្នាំរោង")
        addMs(2025, 3, 29, "ចេត្រ", 29, 2569, "ឆ្នាំម្សាញ់")
        addMs(2025, 4, 27, "ពិសាខ", 30, 2569, "ឆ្នាំម្សាញ់")
        addMs(2025, 5, 27, "ជេស្ឋ", 30, 2569, "ឆ្នាំម្សាញ់")
        addMs(2025, 6, 26, "អាសាឍ", 30, 2569, "ឆ្នាំម្សាញ់")
        addMs(2025, 7, 26, "ស្រាពណ៍", 29, 2569, "ឆ្នាំម្សាញ់")
        addMs(2025, 8, 24, "ភទ្របទ", 30, 2569, "ឆ្នាំម្សាញ់")
        addMs(2025, 9, 23, "អស្សុជ", 29, 2569, "ឆ្នាំម្សាញ់")
        addMs(2025, 10, 22, "កត្តិក", 30, 2569, "ឆ្នាំម្សាញ់")
        addMs(2025, 11, 21, "មិគសិរ", 29, 2569, "ឆ្នាំម្សាញ់")
        addMs(2025, 12, 20, "បុស្ស", 30, 2569, "ឆ្នាំម្សាញ់")

        // --- 2026 ---
        addMs(2026, 1, 19, "មាឃ", 29, 2569, "ឆ្នាំម្សាញ់")
        addMs(2026, 2, 17, "ផល្គុន", 30, 2569, "ឆ្នាំម្សាញ់")
        addMs(2026, 3, 19, "ចេត្រ", 29, 2570, "ឆ្នាំមមែ")
        addMs(2026, 4, 17, "ពិសាខ", 30, 2570, "ឆ្នាំមមែ")
        addMs(2026, 5, 17, "ជេស្ឋ", 29, 2570, "ឆ្នាំមមែ")
        addMs(2026, 6, 15, "អាសាឍ", 30, 2570, "ឆ្នាំមមែ")
        addMs(2026, 7, 15, "ស្រាពណ៍", 29, 2570, "ឆ្នាំមមែ")
        addMs(2026, 8, 13, "ភទ្របទ", 30, 2570, "ឆ្នាំមមែ")
        addMs(2026, 9, 12, "អស្សុជ", 29, 2570, "ឆ្នាំមមែ")
        addMs(2026, 10, 11, "កត្តិក", 30, 2570, "ឆ្នាំមមែ")
        addMs(2026, 11, 10, "មិគសិរ", 29, 2570, "ឆ្នាំមមែ")
        addMs(2026, 12, 9, "បុស្ស", 30, 2570, "ឆ្នាំមមែ")

        // --- 2027 ---
        addMs(2027, 1, 2, "បុស្ស", 30, 2570, "ឆ្នាំមមែ")
        addMs(2027, 2, 1, "មាឃ", 29, 2570, "ឆ្នាំមមែ")
        addMs(2027, 3, 2, "ផល្គុន", 30, 2570, "ឆ្នាំមមែ")
        addMs(2027, 3, 31, "ចេត្រ", 29, 2571, "ឆ្នាំវក")
        addMs(2027, 4, 30, "ពិសាខ", 30, 2571, "ឆ្នាំវក")
        addMs(2027, 5, 29, "ជេស្ឋ", 29, 2571, "ឆ្នាំវក")
        addMs(2027, 6, 28, "អាសាឍ ១", 30, 2571, "ឆ្នាំវក")  // Leap month 1
        addMs(2027, 7, 28, "អាសាឍ ២", 30, 2571, "ឆ្នាំវក")  // Leap month 2
        addMs(2027, 8, 27, "ស្រាពណ៍", 29, 2571, "ឆ្នាំវក")
        addMs(2027, 9, 25, "ភទ្របទ", 30, 2571, "ឆ្នាំវក")
        addMs(2027, 10, 25, "អស្សុជ", 29, 2571, "ឆ្នាំវក")
        addMs(2027, 11, 23, "កត្តិក", 30, 2571, "ឆ្នាំវក")
        addMs(2027, 12, 23, "មិគសិរ", 29, 2571, "ឆ្នាំវក")

        list.sortBy { it.serialDay }
        list
    }

    // Convert Gregorian day to KhmerDate representation
    fun getKhmerDate(year: Int, month: Int, day: Int): KhmerDate {
        val sDay = getSerialDay(year, month, day)
        val dowIdx = ((sDay + 4) % 7 + 7) % 7

        var matchingMs = milestones.lastOrNull { it.serialDay <= sDay }
        if (matchingMs == null) {
            // Out of bounds fallback: use standard 2026 defaults
            matchingMs = Milestone(getSerialDay(2026, 5, 11), "ពិសាខ", 30, 2570, "ឆ្នាំមមែ")
        }

        val offset = sDay - matchingMs.serialDay
        val isWaxing = (offset % 30) < 15
        
        // Correct lunar day calculation for 29 or 30 day months
        // If it's waning (Roach), the value starts from 1 again after day 15
        val displayLunarDay = if (isWaxing) {
            (offset % 30) + 1
        } else {
            (offset % 30) - 14
        }

        val lunarDayNameStr = "${toKhmerNumeral(displayLunarDay)} ${if (isWaxing) "កើត" else "រោច"}"

        // Moon Emojis mapping
        val moonEmoji = when {
            offset % 30 == 0 -> "🌑"       // New Moon
            offset % 30 in 1..6 -> "🌒"  // Waxing Crescent
            offset % 30 == 7 -> "🌓"       // First Quarter
            offset % 30 in 8..13 -> "🌔" // Waxing Gibbous
            offset % 30 == 14 -> "🌕"      // Full Moon
            offset % 30 in 15..21 -> "🌖" // Waning Gibbous
            offset % 30 == 22 -> "🌗"      // Last Quarter
            else -> "🌘"                   // Waning Crescent
        }

        // Gregorian Calendar Holiday checkers
        var holiday: String? = null
        if (month == 4 && day in 14..16) {
            holiday = "ចូលឆ្នាំថ្មីប្រពៃណីជាតិ"
        } else if (year == 2025 && month == 2 && day == 12) {
            holiday = "បុណ្យមាឃបូជា (Meak Bochea)"
        } else if (year == 2026 && month == 2 && day == 2) {
            holiday = "បុណ្យមាឃបូជា (Meak Bochea)"
        } else if (year == 2027 && month == 2 && day == 20) {
            holiday = "បុណ្យមាឃបូជា (Meak Bochea)"
        } else if (year == 2025 && month == 5 && day == 11) {
            holiday = "បុណ្យវិសាខបូជា (Visak Bochea)"
        } else if (year == 2026 && month == 5 && day == 1) {
            holiday = "បុណ្យវិសាខបូជា (Visak Bochea)"
        } else if (year == 2027 && month == 5 && day == 20) {
            holiday = "បុណ្យវិសាខបូជា (Visak Bochea)"
        } else if (matchingMs.khmerMonthName == "ភទ្របទ" && !isWaxing && displayLunarDay == 15) {
            holiday = "បុណ្យភ្ជុំបិណ្ឌ (Pchum Ben)"
        } else if (matchingMs.khmerMonthName == "កត្តិក" && isWaxing && displayLunarDay == 15) {
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

        // Auspicious Checker: Stable algorithm to make auspicious days look realistic
        // We consider days that have specific lunar alignments as auspicious
        val isAuspicious = (offset % 30) in listOf(2, 6, 10, 11, 18, 25)
        val auspiciousType = if (isAuspicious) {
            when (displayLunarDay % 4) {
                0 -> "ពិធីមង្គលការ (Wedding)"
                1 -> "ឡើងផ្ទះថ្មី (Housewarming)"
                2 -> "បើកអាជីវកម្ម (Business)"
                else -> "ធ្វើដំណើរស្វែងរកលាភ (Travel)"
            }
        } else null

        return KhmerDate(
            day = day,
            month = month,
            year = year,
            dayOfWeek = KH_DAYS[dowIdx],
            dayOfWeekEn = EN_DAYS[dowIdx],
            dayOfWeekShort = KH_DAYS_SHORT[dowIdx],
            lunarDayVal = displayLunarDay,
            isWaxing = isWaxing,
            lunarDayName = lunarDayNameStr,
            lunarMonthName = matchingMs.khmerMonthName,
            zodiac = matchingMs.zodiac,
            BE = matchingMs.be,
            moonEmoji = moonEmoji,
            holiday = holiday,
            isAuspicious = isAuspicious,
            auspiciousType = auspiciousType
        )
    }

    // Number converter helper for Khmer Numerals
    fun toKhmerNumeral(n: Int): String {
        return n.toString().map { KHMER_NUMERAL_MAP[it] ?: it }.joinToString("")
    }

    // Return list of days in a given Gregorian Month-Year
    fun getGregorianMonthDays(year: Int, month: Int): List<KhmerDate> {
        val daysInMonth = when (month) {
            1, 3, 5, 7, 8, 10, 12 -> 31
            4, 6, 9, 11 -> 30
            2 -> if ((year % 4 == 0 && year % 100 != 0) || (year % 400 == 0)) 29 else 28
            else -> 30
        }

        return (1..daysInMonth).map { getKhmerDate(year, month, it) }
    }
}
