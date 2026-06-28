package com.example.data

import com.example.calendar.KhmerCalendarHelper
import com.example.calendar.KhmerDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.LocalDate

data class CalendarApiMonth(
    val year: Int,
    val month: Int,
    val days: List<CalendarApiDay>
)

data class CalendarApiMonthOverlays(
    val year: Int,
    val month: Int,
    val notes: List<CalendarApiNote>,
    val events: List<CalendarApiEvent>,
    val holidayEvents: List<CalendarApiHolidayEvent>,
    val workShifts: List<CalendarApiWorkShift>
)

data class CalendarApiDay(
    val date: LocalDate,
    val calendar: KhmerDate,
    val notes: List<CalendarApiNote>,
    val events: List<CalendarApiEvent>,
    val holidayEvents: List<CalendarApiHolidayEvent>,
    val workShift: CalendarApiWorkShift?
)

data class CalendarApiNote(
    val id: String,
    val date: LocalDate,
    val text: String
)

data class CalendarApiEvent(
    val id: String,
    val title: String,
    val description: String?,
    val startsAt: String?,
    val endsAt: String?,
    val allDay: Boolean,
    val location: String?,
    val color: String?
) {
    val timeLabel: String?
        get() = startsAt?.takeIf { it.length >= 16 }?.substring(11, 16)

    val date: LocalDate?
        get() = startsAt?.let { runCatching { LocalDate.parse(it.take(10)) }.getOrNull() }
}

data class CalendarApiHolidayEvent(
    val id: String,
    val nameKm: String,
    val nameEn: String,
    val date: LocalDate,
    val occurrenceDate: LocalDate?,
    val type: String,
    val description: String?,
    val notes: String?
)

data class CalendarApiWorkShift(
    val date: LocalDate,
    val dayOffset: Int?,
    val shiftTemplate: CalendarApiShiftTemplate?,
    val startsAt: String?,
    val endsAt: String?,
    val blocked: Boolean
)

data class CalendarApiShiftTemplate(
    val id: String,
    val code: String,
    val name: String,
    val startTime: String,
    val endTime: String,
    val isOvernight: Boolean
) {
    fun toShiftDef(): AppStore.ShiftDef {
        val (startHour, startMin) = parseTime(startTime)
        val (endHour, endMin) = parseTime(endTime)
        return AppStore.ShiftDef(
            id = code.ifBlank { id },
            name = name,
            startHour = startHour,
            startMin = startMin,
            endHour = endHour,
            endMin = endMin
        )
    }

    private fun parseTime(value: String): Pair<Int, Int> {
        val clean = value.take(5)
        val parts = clean.split(":")
        return (parts.getOrNull(0)?.toIntOrNull() ?: 0) to (parts.getOrNull(1)?.toIntOrNull() ?: 0)
    }
}

/**
 * Client for https://api-calender-sigma.vercel.app.
 *
 * Every request verifies that the response is JSON before parsing so screens
 * can fall back to the offline calendar gracefully if deployment protection or
 * an HTML error page ever appears.
 */
object CalendarApiRepository {
    const val BASE_URL = "https://api-calender-sigma.vercel.app/api/v1"
    private const val TIMEOUT_MS = 20_000

    private val monthCache = mutableMapOf<String, CalendarApiMonth>()
    private val overlayCache = mutableMapOf<String, CalendarApiMonthOverlays>()

    suspend fun fetchMonth(
        year: Int,
        month: Int,
        forceRefresh: Boolean = false
    ): Result<CalendarApiMonth> = withContext(Dispatchers.IO) {
        runCatching {
            val key = "$year-$month"
            if (!forceRefresh) monthCache[key]?.let { return@runCatching it }

            val body = getJson("/calendar/month?year=$year&month=$month")
            parseMonth(body).also { monthCache[key] = it }
        }
    }

    suspend fun fetchMonthOverlays(
        year: Int,
        month: Int,
        forceRefresh: Boolean = false
    ): Result<CalendarApiMonthOverlays> = withContext(Dispatchers.IO) {
        runCatching {
            val key = "$year-$month"
            if (!forceRefresh) overlayCache[key]?.let { return@runCatching it }

            val from = LocalDate.of(year, month, 1)
            val to = from.withDayOfMonth(from.lengthOfMonth())
            val overlays = CalendarApiMonthOverlays(
                year = year,
                month = month,
                notes = fetchNotes().filter { it.date in from..to },
                events = fetchEvents(from, to),
                holidayEvents = fetchHolidayEvents(from, to).getOrThrow(),
                workShifts = fetchWorkShifts(from, to)
            )
            overlayCache[key] = overlays
            overlays
        }
    }

    suspend fun convertDate(
        year: Int,
        month: Int,
        day: Int
    ): Result<KhmerDate> = withContext(Dispatchers.IO) {
        runCatching {
            val date = "%04d-%02d-%02d".format(year, month, day)
            val body = getJson("/calendar/convert?date=${date.urlEncoded()}")
            val data = JSONObject(body).getJSONObject("data")
            parseKhmerDate(data)
        }
    }

    suspend fun fetchDay(date: LocalDate): Result<CalendarApiDay> = withContext(Dispatchers.IO) {
        runCatching {
            val body = getJson("/calendar/day?date=${date.toString().urlEncoded()}")
            parseDayPayload(JSONObject(body).getJSONObject("data"))
        }
    }

    suspend fun fetchHolidayEvents(
        from: LocalDate,
        to: LocalDate,
        forceRefresh: Boolean = false
    ): Result<List<CalendarApiHolidayEvent>> = withContext(Dispatchers.IO) {
        runCatching {
            parseDataList(
                getJson("/holiday-events?from=${from.toString().urlEncoded()}&to=${to.toString().urlEncoded()}"),
                ::parseHolidayEvent
            )
        }
    }

    suspend fun createNote(date: LocalDate, text: String): Result<CalendarApiNote> = withContext(Dispatchers.IO) {
        runCatching {
            val payload = JSONObject()
                .put("date", date.toString())
                .put("text", text.trim())
            val body = sendJson("/notes", "POST", payload)
            val note = parseNote(JSONObject(body).getJSONObject("data"))
                ?: throw IOException("Calendar API returned an invalid note")
            invalidateMonth(date)
            note
        }
    }

    suspend fun updateNote(id: String, date: LocalDate, text: String): Result<CalendarApiNote> = withContext(Dispatchers.IO) {
        runCatching {
            val payload = JSONObject()
                .put("date", date.toString())
                .put("text", text.trim())
            val body = sendJson("/notes/${id.urlEncoded()}", "PUT", payload)
            val note = parseNote(JSONObject(body).getJSONObject("data"))
                ?: throw IOException("Calendar API returned an invalid note")
            invalidateMonth(date)
            note
        }
    }

    suspend fun deleteNote(id: String, date: LocalDate? = null): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            sendJson("/notes/${id.urlEncoded()}", "DELETE")
            date?.let(::invalidateMonth)
            Unit
        }
    }

    private fun fetchNotes(): List<CalendarApiNote> =
        parseDataList(getJson("/notes"), ::parseNote)

    private fun fetchEvents(from: LocalDate, to: LocalDate): List<CalendarApiEvent> =
        parseDataList(
            getJson("/events?from=${from.toString().urlEncoded()}&to=${to.toString().urlEncoded()}"),
            ::parseEvent
        )

    private fun fetchWorkShifts(from: LocalDate, to: LocalDate): List<CalendarApiWorkShift> =
        parseDataList(
            getJson("/work-schedule/days?from=${from.toString().urlEncoded()}&to=${to.toString().urlEncoded()}"),
            ::parseWorkShift
        )

    private fun getJson(pathAndQuery: String): String {
        val conn = (URL(BASE_URL + pathAndQuery).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            instanceFollowRedirects = true
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "KhmerCalendarAndroid/1.0")
        }

        try {
            val code = conn.responseCode
            val body = (if (code in 200..299) conn.inputStream else conn.errorStream)
                ?.bufferedReader()
                ?.use { it.readText() }
                .orEmpty()
            if (code !in 200..299) throw IOException("HTTP $code from calendar API")

            val trimmed = body.trimStart()
            if (!trimmed.startsWith("{") && !trimmed.startsWith("[")) {
                val contentType = conn.contentType.orEmpty()
                throw IOException("Calendar API returned non-JSON response: $contentType")
            }
            return body
        } finally {
            conn.disconnect()
        }
    }

    private fun sendJson(pathAndQuery: String, method: String, payload: JSONObject? = null): String {
        val conn = (URL(BASE_URL + pathAndQuery).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            instanceFollowRedirects = true
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "KhmerCalendarAndroid/1.0")
            if (payload != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            }
        }

        try {
            if (payload != null) {
                conn.outputStream.bufferedWriter(Charsets.UTF_8).use { it.write(payload.toString()) }
            }

            val code = conn.responseCode
            val body = (if (code in 200..299) conn.inputStream else conn.errorStream)
                ?.bufferedReader()
                ?.use { it.readText() }
                .orEmpty()
            if (code !in 200..299) {
                throw IOException("HTTP $code from calendar API: ${body.take(200)}")
            }

            if (body.isBlank()) return body
            val trimmed = body.trimStart()
            if (!trimmed.startsWith("{") && !trimmed.startsWith("[")) {
                val contentType = conn.contentType.orEmpty()
                throw IOException("Calendar API returned non-JSON response: $contentType")
            }
            return body
        } finally {
            conn.disconnect()
        }
    }

    private fun invalidateMonth(date: LocalDate) {
        val key = "${date.year}-${date.monthValue}"
        monthCache.remove(key)
        overlayCache.remove(key)
    }

    private fun parseMonth(body: String): CalendarApiMonth {
        val data = JSONObject(body).getJSONObject("data")
        val year = data.optInt("year")
        val month = data.optInt("month")
        val daysArr = data.optJSONArray("days") ?: JSONArray()
        val days = (0 until daysArr.length()).mapNotNull { i ->
            daysArr.optJSONObject(i)?.let { parseDayPayload(it) }
        }
        return CalendarApiMonth(year, month, days)
    }

    private fun <T> parseDataList(body: String, mapper: (JSONObject) -> T?): List<T> =
        JSONObject(body).optJSONArray("data").mapObjects(mapper)

    private fun parseDayPayload(payload: JSONObject): CalendarApiDay {
        val calendarObj = payload.optJSONObject("calendar") ?: payload
        val khmerDate = parseKhmerDate(calendarObj)
        val localDate = parseDate(calendarObj.cleanString("date")) ?: LocalDate.of(
            khmerDate.year,
            khmerDate.month,
            khmerDate.day
        )

        return CalendarApiDay(
            date = localDate,
            calendar = khmerDate,
            notes = payload.optJSONArray("notes").mapObjects(::parseNote),
            events = payload.optJSONArray("events").mapObjects(::parseEvent),
            holidayEvents = payload.optJSONArray("holiday_events").mapObjects(::parseHolidayEvent),
            workShift = payload.optJSONObject("work_shift")?.let(::parseWorkShift)
        )
    }

    private fun parseKhmerDate(o: JSONObject): KhmerDate {
        val year = o.optInt("year")
        val month = o.optInt("month")
        val day = o.optInt("day")
        val fallback = if (year in 1900..2200 && month in 1..12 && day in 1..31) {
            runCatching { KhmerCalendarHelper.getKhmerDate(year, month, day) }.getOrNull()
        } else null

        return KhmerDate(
            day = day.takeIf { it > 0 } ?: fallback?.day ?: 1,
            month = month.takeIf { it > 0 } ?: fallback?.month ?: 1,
            year = year.takeIf { it > 0 } ?: fallback?.year ?: 1900,
            dayOfWeek = o.cleanString("day_of_week") ?: fallback?.dayOfWeek.orEmpty(),
            dayOfWeekEn = o.cleanString("day_of_week_en") ?: fallback?.dayOfWeekEn.orEmpty(),
            dayOfWeekShort = o.cleanString("day_of_week_short") ?: fallback?.dayOfWeekShort.orEmpty(),
            lunarDayVal = o.optInt("lunar_day", fallback?.lunarDayVal ?: 1),
            isWaxing = o.optBoolean("is_waxing", fallback?.isWaxing ?: true),
            lunarDayName = o.cleanString("lunar_day_name") ?: fallback?.lunarDayName.orEmpty(),
            lunarMonthName = o.cleanString("lunar_month_name") ?: fallback?.lunarMonthName.orEmpty(),
            zodiac = o.cleanString("zodiac") ?: fallback?.zodiac.orEmpty(),
            BE = o.optInt("buddhist_era", fallback?.BE ?: 0),
            moonEmoji = o.cleanString("moon_phase") ?: fallback?.moonEmoji.orEmpty(),
            holiday = o.cleanString("holiday"),
            isAuspicious = o.optBoolean("is_auspicious", fallback?.isAuspicious ?: false),
            auspiciousType = o.cleanString("auspicious_type")
        )
    }

    private fun parseNote(o: JSONObject): CalendarApiNote? {
        val date = parseDate(o.cleanString("date")) ?: return null
        val text = o.cleanString("text") ?: return null
        return CalendarApiNote(
            id = o.idString(),
            date = date,
            text = text
        )
    }

    private fun parseEvent(o: JSONObject): CalendarApiEvent? {
        val title = o.cleanString("title") ?: return null
        return CalendarApiEvent(
            id = o.idString(),
            title = title,
            description = o.cleanString("description"),
            startsAt = o.cleanString("starts_at"),
            endsAt = o.cleanString("ends_at"),
            allDay = o.optBoolean("all_day", false),
            location = o.cleanString("location"),
            color = o.cleanString("color")
        )
    }

    private fun parseHolidayEvent(o: JSONObject): CalendarApiHolidayEvent? {
        val date = parseDate(o.cleanString("date")) ?: return null
        val nameKm = o.cleanString("name_km").orEmpty()
        val nameEn = o.cleanString("name_en").orEmpty()
        if (nameKm.isBlank() && nameEn.isBlank()) return null

        return CalendarApiHolidayEvent(
            id = o.idString(),
            nameKm = nameKm.ifBlank { nameEn },
            nameEn = nameEn.ifBlank { nameKm },
            date = date,
            occurrenceDate = parseDate(o.cleanString("occurrence_date")),
            type = o.cleanString("type") ?: "custom",
            description = o.cleanString("description"),
            notes = o.cleanString("notes")
        )
    }

    private fun parseWorkShift(o: JSONObject): CalendarApiWorkShift? {
        val date = parseDate(o.cleanString("date")) ?: return null
        return CalendarApiWorkShift(
            date = date,
            dayOffset = if (o.isNull("day_offset")) null else o.optInt("day_offset"),
            shiftTemplate = o.optJSONObject("shift_template")?.let(::parseShiftTemplate),
            startsAt = o.cleanString("starts_at"),
            endsAt = o.cleanString("ends_at"),
            blocked = o.optBoolean("blocked", false)
        )
    }

    private fun parseShiftTemplate(o: JSONObject): CalendarApiShiftTemplate = CalendarApiShiftTemplate(
        id = o.idString(),
        code = o.cleanString("code").orEmpty(),
        name = o.cleanString("name").orEmpty(),
        startTime = o.cleanString("start_time") ?: "00:00",
        endTime = o.cleanString("end_time") ?: "00:00",
        isOvernight = o.optBoolean("is_overnight", false)
    )

    private fun parseDate(value: String?): LocalDate? =
        value?.let { runCatching { LocalDate.parse(it.take(10)) }.getOrNull() }

    private fun <T> JSONArray?.mapObjects(mapper: (JSONObject) -> T?): List<T> {
        if (this == null) return emptyList()
        return (0 until length()).mapNotNull { i -> optJSONObject(i)?.let(mapper) }
    }

    private fun JSONObject.cleanString(name: String): String? {
        if (!has(name) || isNull(name)) return null
        return optString(name).trim().takeIf { it.isNotBlank() && it != "null" }
    }

    private fun JSONObject.idString(): String = when (val raw = opt("id")) {
        null, JSONObject.NULL -> ""
        is Number -> raw.toLong().toString()
        else -> raw.toString()
    }

    private fun String.urlEncoded(): String = URLEncoder.encode(this, "UTF-8")
}
