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
    val color: String?,
    val reminderMinutesBefore: Int?
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
    private var authSession: AuthStore.Session? = null

    fun setAuthSession(session: AuthStore.Session?) {
        if (authSession?.userId != session?.userId) {
            monthCache.clear()
            overlayCache.clear()
        }
        authSession = session
    }

    suspend fun fetchMonth(
        year: Int,
        month: Int,
        forceRefresh: Boolean = false
    ): Result<CalendarApiMonth> = withContext(Dispatchers.IO) {
        runCatching {
            val session = requireAuthSession()
            val key = cacheKey(session, year, month)
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
            val session = requireAuthSession()
            val key = cacheKey(session, year, month)
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

    suspend fun fetchPublicHolidays(year: Int): Result<List<CalendarApiHolidayEvent>> = withContext(Dispatchers.IO) {
        runCatching {
            parseHolidayList(getJson("/public-holidays?year=$year"))
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

    suspend fun createHolidayEvent(
        date: LocalDate,
        nameKm: String,
        nameEn: String,
        type: String = "custom",
        isRecurringYearly: Boolean = true,
        description: String? = null,
        notes: String? = null
    ): Result<CalendarApiHolidayEvent> = withContext(Dispatchers.IO) {
        runCatching {
            val body = sendJson(
                "/holiday-events",
                "POST",
                holidayEventPayload(date, nameKm, nameEn, type, isRecurringYearly, description, notes)
            )
            val event = parseHolidayEvent(JSONObject(body).getJSONObject("data"))
                ?: throw IOException("Calendar API returned an invalid holiday event")
            invalidateHolidayEventCaches(event.occurrenceDate ?: event.date)
            event
        }
    }

    suspend fun updateHolidayEvent(
        id: String,
        date: LocalDate,
        nameKm: String,
        nameEn: String,
        type: String = "custom",
        isRecurringYearly: Boolean = true,
        description: String? = null,
        notes: String? = null
    ): Result<CalendarApiHolidayEvent> = withContext(Dispatchers.IO) {
        runCatching {
            val body = sendJson(
                "/holiday-events/${id.urlEncoded()}",
                "PATCH",
                holidayEventPayload(date, nameKm, nameEn, type, isRecurringYearly, description, notes)
            )
            val event = parseHolidayEvent(JSONObject(body).getJSONObject("data"))
                ?: throw IOException("Calendar API returned an invalid holiday event")
            invalidateHolidayEventCaches(event.occurrenceDate ?: event.date)
            event
        }
    }

    suspend fun deleteHolidayEvent(id: String, date: LocalDate? = null): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            sendJson("/holiday-events/${id.urlEncoded()}", "DELETE")
            invalidateHolidayEventCaches(date)
            Unit
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
            val body = sendJson("/notes/${id.urlEncoded()}", "PATCH", payload)
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

    suspend fun createEvent(
        date: LocalDate,
        title: String,
        startsAt: String,
        endsAt: String? = null,
        allDay: Boolean = false,
        description: String? = null,
        location: String? = null,
        color: String? = null,
        reminderMinutesBefore: Int? = null
    ): Result<CalendarApiEvent> = withContext(Dispatchers.IO) {
        runCatching {
            val body = sendJson("/events", "POST", eventPayload(
                title = title,
                startsAt = startsAt,
                endsAt = endsAt,
                allDay = allDay,
                description = description,
                location = location,
                color = color,
                reminderMinutesBefore = reminderMinutesBefore
            ))
            val event = parseEvent(JSONObject(body).getJSONObject("data"))
                ?: throw IOException("Calendar API returned an invalid event")
            invalidateMonth(date)
            event
        }
    }

    suspend fun updateEvent(
        id: String,
        date: LocalDate,
        title: String,
        startsAt: String,
        endsAt: String? = null,
        allDay: Boolean = false,
        description: String? = null,
        location: String? = null,
        color: String? = null,
        reminderMinutesBefore: Int? = null
    ): Result<CalendarApiEvent> = withContext(Dispatchers.IO) {
        runCatching {
            val body = sendJson("/events/${id.urlEncoded()}", "PATCH", eventPayload(
                title = title,
                startsAt = startsAt,
                endsAt = endsAt,
                allDay = allDay,
                description = description,
                location = location,
                color = color,
                reminderMinutesBefore = reminderMinutesBefore
            ))
            val event = parseEvent(JSONObject(body).getJSONObject("data"))
                ?: throw IOException("Calendar API returned an invalid event")
            invalidateMonth(date)
            event
        }
    }

    suspend fun deleteEvent(id: String, date: LocalDate? = null): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            sendJson("/events/${id.urlEncoded()}", "DELETE")
            date?.let(::invalidateMonth)
            Unit
        }
    }

    suspend fun updateWorkScheduleSettings(cycle: AppStore.ShiftCycle): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val payload = JSONObject()
                .put("system_type", cycle.systemType)
                .put("remind", cycle.remind)
                .put("reminder_minutes_before", cycle.reminderMinutesBefore)
                .put("shift_templates", JSONArray().apply {
                    cycle.shifts.forEachIndexed { index, shift ->
                        put(JSONObject()
                            .put("code", shift.id)
                            .put("name", shift.name)
                            .put("start_time", "%02d:%02d".format(shift.startHour, shift.startMin))
                            .put("end_time", "%02d:%02d".format(shift.endHour, shift.endMin))
                            .put("sort_order", index)
                        )
                    }
                })
            sendJson("/work-schedule/settings", "PUT", payload)
            overlayCache.clear()
            Unit
        }
    }

    suspend fun updateWorkScheduleCycle(cycleStartDate: LocalDate, assignments: List<String?>): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val padded = assignments.take(AppStore.CYCLE_SLOTS) +
                List((AppStore.CYCLE_SLOTS - assignments.size).coerceAtLeast(0)) { null }
            val payload = JSONObject().put("assignments", JSONArray().apply {
                padded.forEach { assignment ->
                    if (assignment.isNullOrBlank()) put(JSONObject.NULL) else put(assignment)
                }
            })
            sendJson("/work-schedule/cycles/${cycleStartDate.toString().urlEncoded()}", "PUT", payload)
            invalidateMonth(cycleStartDate)
            invalidateMonth(cycleStartDate.plusMonths(1))
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

    /**
     * Public suspend version — called by SyncRepository.pullWorkScheduleFromRemote
     * to fetch a wide date-range of work shifts for the initial full sync.
     */
    suspend fun fetchWorkShiftsPublic(from: LocalDate, to: LocalDate): List<CalendarApiWorkShift> =
        withContext(Dispatchers.IO) {
            parseDataList(
                getJson("/work-schedule/days?from=${from.toString().urlEncoded()}&to=${to.toString().urlEncoded()}"),
                ::parseWorkShift
            )
        }

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
            applyAuthHeaders()
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
            applyAuthHeaders()
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

    private fun HttpURLConnection.applyAuthHeaders() {
        val session = requireAuthSession()
        setRequestProperty("Authorization", "Bearer ${session.accessToken}")
        setRequestProperty("X-Calendar-User-Id", session.userId)
        setRequestProperty("X-Calendar-User-Email", session.email)
    }

    private fun requireAuthSession(): AuthStore.Session {
        val session = authSession
        if (session == null || session.userId.isBlank() || session.accessToken.isBlank()) {
            throw IOException("Calendar API authentication is required")
        }
        return session
    }

    private fun cacheKey(session: AuthStore.Session, year: Int, month: Int): String =
        "${session.userId}:$year-$month"

    private fun invalidateMonth(date: LocalDate) {
        val monthSuffix = ":${date.year}-${date.monthValue}"
        monthCache.keys.removeAll { it.endsWith(monthSuffix) }
        overlayCache.keys.removeAll { it.endsWith(monthSuffix) }
    }

    private fun invalidateHolidayEventCaches(date: LocalDate?) {
        date?.let(::invalidateMonth)
        overlayCache.clear()
    }

    private fun eventPayload(
        title: String,
        startsAt: String,
        endsAt: String?,
        allDay: Boolean,
        description: String?,
        location: String?,
        color: String?,
        reminderMinutesBefore: Int?
    ): JSONObject = JSONObject()
        .put("title", title.trim())
        .put("starts_at", startsAt)
        .put("all_day", allDay)
        .apply {
            endsAt?.takeIf { it.isNotBlank() }?.let { put("ends_at", it) }
            description?.takeIf { it.isNotBlank() }?.let { put("description", it) }
            location?.takeIf { it.isNotBlank() }?.let { put("location", it) }
            color?.takeIf { it.isNotBlank() }?.let { put("color", it) }
            reminderMinutesBefore?.let { put("reminder_minutes_before", it) }
        }

    private fun holidayEventPayload(
        date: LocalDate,
        nameKm: String,
        nameEn: String,
        type: String,
        isRecurringYearly: Boolean,
        description: String?,
        notes: String?
    ): JSONObject {
        val km = nameKm.trim().ifBlank { nameEn.trim() }
        val en = nameEn.trim().ifBlank { km }
        if (km.isBlank()) throw IOException("Holiday event name is required")
        return JSONObject()
            .put("name_km", km)
            .put("name_en", en)
            .put("date", date.toString())
            .put("type", type.ifBlank { "custom" })
            .put("source", "manual")
            .put("is_fixed", false)
            .put("is_recurring_yearly", isRecurringYearly)
            .apply {
                description?.takeIf { it.isNotBlank() }?.let { put("description", it) }
                notes?.takeIf { it.isNotBlank() }?.let { put("notes", it) }
            }
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

    private fun parseHolidayList(body: String): List<CalendarApiHolidayEvent> {
        val trimmed = body.trimStart()
        val arr = if (trimmed.startsWith("[")) {
            JSONArray(body)
        } else {
            val obj = JSONObject(body)
            obj.optJSONArray("data")
                ?: obj.optJSONArray("holidays")
                ?: obj.optJSONObject("data")?.optJSONArray("holidays")
                ?: JSONArray()
        }
        return arr.mapObjects(::parseHolidayEvent)
    }

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
            holiday = o.cleanString("holiday")
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
            color = o.cleanString("color"),
            reminderMinutesBefore = if (o.has("reminder_minutes_before") && !o.isNull("reminder_minutes_before")) {
                o.optInt("reminder_minutes_before")
            } else {
                null
            }
        )
    }

    private fun parseHolidayEvent(o: JSONObject): CalendarApiHolidayEvent? {
        val date = parseDate(
            o.cleanString("date")
                ?: o.cleanString("holiday_date")
                ?: o.cleanString("occurrence_date")
        ) ?: return null
        val nameKm = o.cleanString("name_km")
            ?: o.cleanString("nameKh")
            ?: o.cleanString("name_kh")
            ?: o.cleanString("title_km")
            ?: o.cleanString("titleKh")
            ?: o.cleanString("name")
            ?: ""
        val nameEn = o.cleanString("name_en")
            ?: o.cleanString("nameEn")
            ?: o.cleanString("title_en")
            ?: o.cleanString("titleEn")
            ?: o.cleanString("name")
            ?: ""
        if (nameKm.isBlank() && nameEn.isBlank()) return null

        return CalendarApiHolidayEvent(
            id = o.idString(),
            nameKm = nameKm.ifBlank { nameEn },
            nameEn = nameEn.ifBlank { nameKm },
            date = date,
            occurrenceDate = parseDate(o.cleanString("occurrence_date")),
            type = o.cleanString("type")
                ?: o.cleanString("category")
                ?: o.cleanString("holiday_type")
                ?: "national",
            description = o.cleanString("description") ?: o.cleanString("desc"),
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
