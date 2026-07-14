package com.aistudio.khmercalendar.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar

/**
 * Central lightweight persistence layer built on SharedPreferences + JSON.
 *
 * The project stages Room in the build but keeps it disabled; until that is
 * wired up this object is the single source of truth for the features that
 * need real storage:
 *
 *  - **Notes** — multiple notes per day (with delete). Stored in the existing
 *    `khmer_calendar_notes` file. The structured list lives under a
 *    `Y_M_D__notes` key; a legacy `Y_M_D` → joined-text mirror is kept in sync
 *    so the home-screen widget keeps working unchanged.
 *  - **Reminders / events** — multiple per day (with delete). Stored in
 *    `khmer_calendar_alarms` → `alarms` (a JSON array) with a unique request
 *    code per reminder so they never collide.
 *  - **Custom holidays** — user-added holidays, stored in
 *    `khmer_calendar_custom_holidays`.
 *  - **Work schedule** — recurring weekly shifts with optional reminders.
 *  - **Alarm settings** — custom ringtone, insistent ("ring until dismissed")
 *    and default reminder lead time.
 */
object AppStore {

    // ── Pref file names ──────────────────────────────────────────────────────
    private const val NOTES_FILE = "khmer_calendar_notes"
    private const val ALARMS_FILE = "khmer_calendar_alarms"
    private const val HOLIDAYS_FILE = "khmer_calendar_custom_holidays"
    private const val HOLIDAYS_CACHE_FILE = "khmer_calendar_holidays_cache"
    private const val SCHEDULE_FILE = "khmer_calendar_schedule"
    const val SETTINGS_FILE = "khmer_calendar_prefs"

    private fun notesPrefs(c: Context) = c.getSharedPreferences(NOTES_FILE, Context.MODE_PRIVATE)
    private fun alarmsPrefs(c: Context) = c.getSharedPreferences(ALARMS_FILE, Context.MODE_PRIVATE)
    private fun holidaysPrefs(c: Context) = c.getSharedPreferences(HOLIDAYS_FILE, Context.MODE_PRIVATE)
    private fun holidayCachePrefs(c: Context) = c.getSharedPreferences(HOLIDAYS_CACHE_FILE, Context.MODE_PRIVATE)
    private fun schedulePrefs(c: Context) = c.getSharedPreferences(SCHEDULE_FILE, Context.MODE_PRIVATE)
    private fun settings(c: Context) = c.getSharedPreferences(SETTINGS_FILE, Context.MODE_PRIVATE)

    fun dateKey(year: Int, month: Int, day: Int) = "${year}_${month}_$day"

    private fun parseDateKey(key: String): Triple<Int, Int, Int>? {
        val parts = key.split("_")
        if (parts.size != 3) return null
        val year = parts[0].toIntOrNull() ?: return null
        val month = parts[1].toIntOrNull() ?: return null
        val day = parts[2].toIntOrNull() ?: return null
        return Triple(year, month, day)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // NOTES — multiple per day
    // ─────────────────────────────────────────────────────────────────────────
    data class Note(val id: String, val text: String, val ts: Long, val remoteId: String? = null)
    data class DatedNote(val year: Int, val month: Int, val day: Int, val note: Note)

    private fun notesListKey(year: Int, month: Int, day: Int) = "${dateKey(year, month, day)}__notes"

    /** All notes for a given day, newest last. Migrates a legacy single-string note transparently. */
    fun getNotes(c: Context, year: Int, month: Int, day: Int): List<Note> {
        val p = notesPrefs(c)
        val raw = p.getString(notesListKey(year, month, day), null)
        if (raw != null) {
            return parseNotes(raw)
        }
        // Legacy fallback: a single plain-string note under "Y_M_D".
        val legacy = p.getString(dateKey(year, month, day), null)?.trim().orEmpty()
        if (legacy.isEmpty()) return emptyList()
        val migrated = listOf(Note(newId(), legacy, System.currentTimeMillis()))
        writeNotes(c, year, month, day, migrated)
        return migrated
    }

    private fun parseNotes(raw: String): List<Note> = try {
        val arr = JSONArray(raw)
        (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val text = o.optString("text").trim()
            if (text.isEmpty()) null
            else Note(
                id = o.optString("id").ifBlank { newId() },
                text = text,
                ts = o.optLong("ts", 0L),
                remoteId = o.optString("remote_id").trim().takeIf { it.isNotBlank() }
            )
        }
    } catch (_: Exception) {
        emptyList()
    }

    fun addNote(c: Context, year: Int, month: Int, day: Int, text: String): Note? {
        val clean = text.trim()
        if (clean.isEmpty()) return null
        val note = Note(newId(), clean, System.currentTimeMillis())
        val updated = getNotes(c, year, month, day) + note
        writeNotes(c, year, month, day, updated)
        return note
    }

    fun updateNote(c: Context, year: Int, month: Int, day: Int, id: String, text: String) {
        val clean = text.trim()
        val updated = getNotes(c, year, month, day).map {
            if (it.id == id) it.copy(text = clean) else it
        }.filter { it.text.isNotEmpty() }
        writeNotes(c, year, month, day, updated)
    }

    fun setNoteRemoteId(c: Context, year: Int, month: Int, day: Int, id: String, remoteId: String) {
        val cleanRemoteId = remoteId.trim()
        if (cleanRemoteId.isEmpty()) return
        val updated = getNotes(c, year, month, day).map {
            if (it.id == id) it.copy(remoteId = cleanRemoteId) else it
        }
        writeNotes(c, year, month, day, updated)
    }

    fun upsertSyncedNote(c: Context, year: Int, month: Int, day: Int, remoteId: String, text: String): Note? {
        val cleanRemoteId = remoteId.trim()
        val cleanText = text.trim()
        if (cleanRemoteId.isEmpty() || cleanText.isEmpty()) return null
        val remoteLocalId = "remote-note-$cleanRemoteId"
        var saved: Note? = null
        var matched = false
        val updated = getNotes(c, year, month, day).map { note ->
            if (note.remoteId == cleanRemoteId || note.id == remoteLocalId) {
                matched = true
                note.copy(
                    id = note.id.ifBlank { remoteLocalId },
                    text = cleanText,
                    ts = note.ts.takeIf { it > 0L } ?: System.currentTimeMillis(),
                    remoteId = cleanRemoteId
                ).also { saved = it }
            } else {
                note
            }
        }.let { notes ->
            if (matched) notes
            else notes + Note(remoteLocalId, cleanText, System.currentTimeMillis(), cleanRemoteId).also { saved = it }
        }
        writeNotes(c, year, month, day, updated)
        return saved
    }

    fun deleteNote(c: Context, year: Int, month: Int, day: Int, id: String) {
        val updated = getNotes(c, year, month, day).filterNot { it.id == id }
        writeNotes(c, year, month, day, updated)
    }

    fun deleteNoteByRemoteId(c: Context, year: Int, month: Int, day: Int, remoteId: String) {
        val cleanRemoteId = remoteId.trim()
        if (cleanRemoteId.isEmpty()) return
        val updated = getNotes(c, year, month, day).filterNot { it.remoteId == cleanRemoteId }
        writeNotes(c, year, month, day, updated)
    }

    private fun writeNotes(c: Context, year: Int, month: Int, day: Int, notes: List<Note>) {
        val p = notesPrefs(c)
        val e = p.edit()
        if (notes.isEmpty()) {
            e.remove(notesListKey(year, month, day))
            e.remove(dateKey(year, month, day)) // clear legacy mirror too
        } else {
            val arr = JSONArray()
            notes.forEach { n ->
                arr.put(JSONObject().apply {
                    put("id", n.id); put("text", n.text); put("ts", n.ts)
                    n.remoteId?.let { put("remote_id", it) }
                })
            }
            e.putString(notesListKey(year, month, day), arr.toString())
            // Keep a joined-text mirror so the widget + legacy readers still work.
            e.putString(dateKey(year, month, day), notes.joinToString("\n") { it.text })
        }
        e.apply()
    }

    /** Days (1..31) of the given month that have at least one note. */
    fun daysWithNotes(c: Context, year: Int, month: Int): Set<Int> =
        (1..31).filter { getNotes(c, year, month, it).isNotEmpty() }.toSet()

    fun getAllNotes(c: Context): List<DatedNote> {
        val p = notesPrefs(c)
        val structuredDateKeys = p.all.keys
            .filter { it.endsWith("__notes") }
            .map { it.removeSuffix("__notes") }
            .toSet()
        val dateKeys = p.all.keys.mapNotNull { key ->
            when {
                key.endsWith("__notes") -> key.removeSuffix("__notes")
                key in structuredDateKeys -> null
                key.contains("__") -> null
                else -> key
            }?.let(::parseDateKey)
        }.distinct()

        return dateKeys.flatMap { (year, month, day) ->
            getNotes(c, year, month, day).map { note -> DatedNote(year, month, day, note) }
        }.sortedWith(compareBy({ it.year }, { it.month }, { it.day }, { it.note.ts }))
    }

    // ─────────────────────────────────────────────────────────────────────────
    // REMINDERS / EVENTS — multiple per day, unique request code
    // ─────────────────────────────────────────────────────────────────────────
    data class Reminder(
        val requestCode: Int,
        val triggerMs: Long,
        val title: String,
        val message: String,
        val ringtoneUri: String?,
        val insistent: Boolean,
        val kind: String,        // "reminder" | "shift"
        val shiftId: String?,
        val remoteEventId: String? = null
    )

    fun getReminders(c: Context): List<Reminder> = try {
        val arr = JSONArray(alarmsPrefs(c).getString("alarms", "[]") ?: "[]")
        (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            Reminder(
                requestCode = o.optInt("requestCode"),
                triggerMs = o.optLong("triggerMs", 0L),
                title = o.optString("title"),
                message = o.optString("message"),
                ringtoneUri = o.optString("ringtoneUri").ifBlank { null },
                insistent = o.optBoolean("insistent", false),
                kind = o.optString("kind").ifBlank { "reminder" },
                shiftId = o.optString("shiftId").ifBlank { null },
                remoteEventId = o.optString("remoteEventId").ifBlank { null }
            )
        }
    } catch (_: Exception) {
        emptyList()
    }

    fun saveReminders(c: Context, reminders: List<Reminder>) {
        val arr = JSONArray()
        reminders.forEach { r ->
            arr.put(JSONObject().apply {
                put("requestCode", r.requestCode)
                put("triggerMs", r.triggerMs)
                put("title", r.title)
                put("message", r.message)
                r.ringtoneUri?.let { put("ringtoneUri", it) }
                put("insistent", r.insistent)
                put("kind", r.kind)
                r.shiftId?.let { put("shiftId", it) }
                r.remoteEventId?.let { put("remoteEventId", it) }
            })
        }
        alarmsPrefs(c).edit().putString("alarms", arr.toString()).apply()
    }

    fun upsertReminder(c: Context, r: Reminder) {
        val updated = getReminders(c).filterNot { it.requestCode == r.requestCode } + r
        saveReminders(c, updated)
    }

    fun removeReminder(c: Context, requestCode: Int) {
        saveReminders(c, getReminders(c).filterNot { it.requestCode == requestCode })
    }

    fun setReminderRemoteEventId(c: Context, requestCode: Int, remoteEventId: String) {
        val cleanRemoteId = remoteEventId.trim()
        if (cleanRemoteId.isEmpty()) return
        val updated = getReminders(c).map {
            if (it.requestCode == requestCode) it.copy(remoteEventId = cleanRemoteId) else it
        }
        saveReminders(c, updated)
    }

    fun upsertSyncedReminder(
        c: Context,
        remoteEventId: String,
        triggerMs: Long,
        title: String,
        message: String
    ): Reminder? {
        val cleanRemoteId = remoteEventId.trim()
        if (cleanRemoteId.isEmpty() || triggerMs <= 0L) return null
        val existing = getReminders(c).firstOrNull { it.remoteEventId == cleanRemoteId }
        val reminder = Reminder(
            requestCode = existing?.requestCode ?: stableRemoteRequestCode(cleanRemoteId),
            triggerMs = triggerMs,
            title = title.trim().ifBlank { "Reminder" },
            message = message.trim(),
            ringtoneUri = existing?.ringtoneUri,
            insistent = existing?.insistent ?: false,
            kind = "reminder",
            shiftId = null,
            remoteEventId = cleanRemoteId
        )
        upsertReminder(c, reminder)
        return reminder
    }

    fun deleteReminderByRemoteEventId(c: Context, remoteEventId: String) {
        val cleanRemoteId = remoteEventId.trim()
        if (cleanRemoteId.isEmpty()) return
        saveReminders(c, getReminders(c).filterNot { it.remoteEventId == cleanRemoteId })
    }

    /** A fresh, monotonically increasing request code so reminders never collide. */
    fun nextRequestCode(c: Context): Int {
        val s = settings(c)
        val next = s.getInt("next_request_code", 100_000) + 1
        s.edit().putInt("next_request_code", next).apply()
        return next
    }

    private fun stableRemoteRequestCode(remoteId: String): Int =
        500_000 + (remoteId.hashCode() and 0x3fffffff)

    // ─────────────────────────────────────────────────────────────────────────
    // CUSTOM HOLIDAYS — user added, recurring yearly on a Gregorian month/day
    // ─────────────────────────────────────────────────────────────────────────
    data class CustomHoliday(
        val id: String,
        val month: Int,    // 1..12
        val day: Int,      // 1..31
        val nameKm: String,
        val nameEn: String,
        val remoteHolidayEventId: String? = null
    )

    fun getCustomHolidays(c: Context): List<CustomHoliday> = try {
        val arr = JSONArray(holidaysPrefs(c).getString("holidays", "[]") ?: "[]")
        (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            CustomHoliday(
                id = o.optString("id").ifBlank { newId() },
                month = o.optInt("month"),
                day = o.optInt("day"),
                nameKm = o.optString("nameKm"),
                nameEn = o.optString("nameEn").ifBlank { o.optString("nameKm") },
                remoteHolidayEventId = o.optString("remote_event_id").trim().ifBlank {
                    o.optString("remoteHolidayEventId").trim()
                }.ifBlank {
                    o.optString("remoteId").trim()
                }.takeIf { it.isNotBlank() }
            )
        }
    } catch (_: Exception) {
        emptyList()
    }

    private fun saveCustomHolidays(c: Context, list: List<CustomHoliday>) {
        val arr = JSONArray()
        list.forEach { h ->
            arr.put(JSONObject().apply {
                put("id", h.id); put("month", h.month); put("day", h.day)
                put("nameKm", h.nameKm); put("nameEn", h.nameEn)
                h.remoteHolidayEventId?.let { put("remote_event_id", it) }
            })
        }
        holidaysPrefs(c).edit().putString("holidays", arr.toString()).apply()
    }

    fun addCustomHoliday(c: Context, month: Int, day: Int, nameKm: String, nameEn: String): CustomHoliday? {
        val km = nameKm.trim().ifBlank { nameEn.trim() }
        val en = nameEn.trim().ifBlank { km }
        if (km.isEmpty()) return null
        val holiday = CustomHoliday(newId(), month, day, km, en)
        saveCustomHolidays(c, getCustomHolidays(c) + holiday)
        return holiday
    }

    fun setCustomHolidayRemoteEventId(c: Context, id: String, remoteHolidayEventId: String) {
        val cleanRemoteId = remoteHolidayEventId.trim()
        if (cleanRemoteId.isEmpty()) return
        val updated = getCustomHolidays(c).map {
            if (it.id == id) it.copy(remoteHolidayEventId = cleanRemoteId) else it
        }
        saveCustomHolidays(c, updated)
    }

    fun upsertSyncedCustomHoliday(
        c: Context,
        month: Int,
        day: Int,
        nameKm: String,
        nameEn: String,
        remoteHolidayEventId: String
    ): CustomHoliday? {
        val cleanRemoteId = remoteHolidayEventId.trim()
        val km = nameKm.trim().ifBlank { nameEn.trim() }
        val en = nameEn.trim().ifBlank { km }
        if (cleanRemoteId.isEmpty() || km.isEmpty() || month !in 1..12 || day !in 1..31) return null
        val existing = getCustomHolidays(c).firstOrNull { it.remoteHolidayEventId == cleanRemoteId }
        val holiday = existing?.copy(
            month = month,
            day = day,
            nameKm = km,
            nameEn = en,
            remoteHolidayEventId = cleanRemoteId
        ) ?: CustomHoliday(
            id = "remote-holiday-$cleanRemoteId",
            month = month,
            day = day,
            nameKm = km,
            nameEn = en,
            remoteHolidayEventId = cleanRemoteId
        )
        val updated = if (existing == null) {
            getCustomHolidays(c) + holiday
        } else {
            getCustomHolidays(c).map { if (it.id == existing.id) holiday else it }
        }
        saveCustomHolidays(c, updated)
        return holiday
    }

    fun deleteCustomHolidayByRemoteEventId(c: Context, remoteHolidayEventId: String) {
        val cleanRemoteId = remoteHolidayEventId.trim()
        if (cleanRemoteId.isEmpty()) return
        saveCustomHolidays(c, getCustomHolidays(c).filterNot { it.remoteHolidayEventId == cleanRemoteId })
    }

    fun deleteCustomHoliday(c: Context, id: String): CustomHoliday? {
        val holidays = getCustomHolidays(c)
        val deleted = holidays.firstOrNull { it.id == id }
        if (deleted != null) {
            saveCustomHolidays(c, holidays.filterNot { it.id == id })
        }
        return deleted
    }

    fun customHolidaysForMonth(c: Context, month: Int): List<CustomHoliday> =
        getCustomHolidays(c).filter { it.month == month }

    // ─────────────────────────────────────────────────────────────────────────
    // PUBLIC HOLIDAYS CACHE — fetched from API, cached for offline access
    // ─────────────────────────────────────────────────────────────────────────

    fun getCachedHolidays(c: Context, year: Int): String? =
        holidayCachePrefs(c).getString("holidays_$year", null)

    fun getCachedHolidaysSavedAt(c: Context, year: Int): Long =
        holidayCachePrefs(c).getLong("holidays_${year}_saved_at", 0L)

    fun saveCachedHolidays(c: Context, year: Int, json: String) {
        holidayCachePrefs(c).edit()
            .putString("holidays_$year", json)
            .putLong("holidays_${year}_saved_at", System.currentTimeMillis())
            .apply()
    }

    // ─────────────────────────────────────────────────────────────────────────
    // WORK SCHEDULE — rotating shift system on a 26th→25th monthly cycle
    // ─────────────────────────────────────────────────────────────────────────

    /** A single shift template (e.g. "Day" 07:30–19:30). Crosses midnight when [isOvernight]. */
    data class ShiftDef(
        val id: String,
        val name: String,
        val startHour: Int,
        val startMin: Int,
        val endHour: Int,
        val endMin: Int
    ) {
        val startMinutes: Int get() = startHour * 60 + startMin
        val endMinutes: Int get() = endHour * 60 + endMin
        /** A shift whose end is at/earlier than its start runs into the next day. */
        val isOvernight: Boolean get() = endMinutes <= startMinutes
    }

    /**
     * The user's rotating-shift configuration. [systemType] is 2 or 3; [shifts]
     * holds the (editable) shift templates; [dayAssignments] has [CYCLE_SLOTS]
     * entries — the shift id worked on each day of the cycle (indexed by the
     * day's offset from the cycle start), or null for a day off. Every working
     * day is fully customisable. The same day pattern repeats every cycle.
     */
    data class ShiftCycle(
        val systemType: Int,
        val shifts: List<ShiftDef>,
        val dayAssignments: List<String?>,
        val remind: Boolean,
        val reminderMinutesBefore: Int
    ) {
        fun shiftById(id: String?): ShiftDef? = id?.let { shifts.firstOrNull { s -> s.id == it } }
        fun shiftIdForDay(offset: Int): String? = dayAssignments.getOrNull(offset)
        val isConfigured: Boolean get() = shifts.isNotEmpty()
    }

    /** Max number of day slots in a cycle (longest 26th→25th span is 31 days). */
    const val CYCLE_SLOTS = 31

    data class SalaryCalculatorSettings(
        val basicSalary: String = "",
        val hourlyRate: String = "",
        val dailyRate: String = "",
        val overtimeRate: String = "1.5",
        val nightShiftRate: String = "1.3",
        val holidayDayRate: String = "2.0",
        val holidayNightRate: String = "2.6",
        val benefits: String = "",
        val bonuses: String = "",
        val allowances: String = "",
        val taxVatPercent: String = "",
        val otherDeductions: String = ""
    )

    fun emptyDayAssignments(): List<String?> = List(CYCLE_SLOTS) { null }

    /** Built-in presets matching the standard 2- and 3-shift systems. */
    fun presetShifts(systemType: Int): List<ShiftDef> = if (systemType == 3) listOf(
        ShiftDef("s1", "Shift 1", 7, 30, 15, 30),
        ShiftDef("s2", "Shift 2", 15, 30, 23, 30),
        ShiftDef("s3", "Shift 3", 23, 30, 7, 30)
    ) else listOf(
        ShiftDef("day", "Day", 7, 30, 19, 30),
        ShiftDef("night", "Night", 19, 30, 7, 30)
    )

    fun getShiftCycle(c: Context): ShiftCycle? {
        val raw = schedulePrefs(c).getString("cycle", null) ?: return null
        return try {
            val o = JSONObject(raw)
            val shiftsArr = o.optJSONArray("shifts") ?: JSONArray()
            val shifts = (0 until shiftsArr.length()).mapNotNull { i ->
                val s = shiftsArr.optJSONObject(i) ?: return@mapNotNull null
                ShiftDef(
                    id = s.optString("id").ifBlank { newId() },
                    name = s.optString("name"),
                    startHour = s.optInt("startHour"), startMin = s.optInt("startMin"),
                    endHour = s.optInt("endHour"), endMin = s.optInt("endMin")
                )
            }
            val dayAssignments = parseDayAssignments(o)
            ShiftCycle(
                systemType = o.optInt("systemType", 2),
                shifts = shifts,
                dayAssignments = dayAssignments,
                remind = o.optBoolean("remind", true),
                reminderMinutesBefore = o.optInt("reminderMinutesBefore", 30)
            )
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Read the per-day assignment list, migrating older formats where needed:
     *  - "dayAssignments": array of 31 shift ids ("" = off)   ← current
     *  - "weekAssignments": array-of-arrays or array-of-strings (per week)
     *    is expanded across each week's 7 days as a sensible starting point.
     */
    private fun parseDayAssignments(o: JSONObject): List<String?> {
        o.optJSONArray("dayAssignments")?.let { daArr ->
            return (0 until CYCLE_SLOTS).map { i ->
                if (i < daArr.length()) daArr.optString(i).ifBlank { null } else null
            }
        }
        // Legacy weekly format → expand to days.
        val waArr = o.optJSONArray("weekAssignments")
        val weekShift: (Int) -> String? = wa@{ wi ->
            val el = waArr?.opt(wi) ?: return@wa null
            when (el) {
                is JSONArray -> (0 until el.length()).map { el.optString(it) }.firstOrNull { it.isNotBlank() }
                is String -> el.ifBlank { null }
                else -> null
            }
        }
        return (0 until CYCLE_SLOTS).map { d -> weekShift((d / 7).coerceIn(0, 3)) }
    }

    fun saveShiftCycle(c: Context, cycle: ShiftCycle) {
        val o = JSONObject().apply {
            put("systemType", cycle.systemType)
            put("shifts", JSONArray().apply {
                cycle.shifts.forEach { s ->
                    put(JSONObject().apply {
                        put("id", s.id); put("name", s.name)
                        put("startHour", s.startHour); put("startMin", s.startMin)
                        put("endHour", s.endHour); put("endMin", s.endMin)
                    })
                }
            })
            put("dayAssignments", JSONArray().apply {
                cycle.dayAssignments.forEach { put(it ?: "") }
            })
            put("remind", cycle.remind)
            put("reminderMinutesBefore", cycle.reminderMinutesBefore)
        }
        schedulePrefs(c).edit().putString("cycle", o.toString()).apply()
    }

    fun clearShiftCycle(c: Context) {
        schedulePrefs(c).edit().remove("cycle").apply()
    }

    // ── Per-cycle history snapshots ──────────────────────────────────────────
    // The day pattern repeats, but once a month passes we freeze the worked
    // schedule so past months can be reviewed even if the template later
    // changes. Snapshots are keyed by the cycle's start (e.g. "2026-6").

    /** Stable key for the cycle that contains the given date (its 26th-anchored start). */
    fun cycleKey(year: Int, month: Int, day: Int): String {
        val s = WorkCycleEngine.cycleStart(year, month, day)
        return "${s.get(Calendar.YEAR)}-${s.get(Calendar.MONTH) + 1}"
    }

    fun getCycleSnapshots(c: Context): Map<String, List<String?>> = try {
        val o = JSONObject(schedulePrefs(c).getString("snapshots", "{}") ?: "{}")
        buildMap {
            o.keys().forEach { k ->
                val arr = o.optJSONArray(k) ?: return@forEach
                put(k, (0 until arr.length()).map { arr.optString(it).ifBlank { null } })
            }
        }
    } catch (_: Exception) {
        emptyMap()
    }

    private fun saveCycleSnapshots(c: Context, map: Map<String, List<String?>>) {
        val o = JSONObject()
        map.forEach { (k, list) ->
            o.put(k, JSONArray().apply { list.forEach { put(it ?: "") } })
        }
        schedulePrefs(c).edit().putString("snapshots", o.toString()).apply()
    }

    /** Public saver for the whole per-month schedule map. */
    fun saveMonthlySchedules(c: Context, map: Map<String, List<String?>>) = saveCycleSnapshots(c, map)

    fun getSalaryCalculatorSettings(c: Context, monthKey: String? = null): SalaryCalculatorSettings {
        val p = schedulePrefs(c)
        val raw = monthKey
            ?.let { p.getString("salary_calculator_$it", null) }
            ?: p.getString("salary_calculator", null)
            ?: return SalaryCalculatorSettings()
        return try {
            val o = JSONObject(raw)
            SalaryCalculatorSettings(
                basicSalary = o.optString("basicSalary"),
                hourlyRate = o.optString("hourlyRate"),
                dailyRate = o.optString("dailyRate"),
                overtimeRate = o.optString("overtimeRate").ifBlank { o.optString("overtime").ifBlank { "1.5" } },
                nightShiftRate = o.optString("nightShiftRate").ifBlank { "1.3" },
                holidayDayRate = o.optString("holidayDayRate").ifBlank { "2.0" },
                holidayNightRate = o.optString("holidayNightRate").ifBlank { "2.6" },
                benefits = o.optString("benefits"),
                bonuses = o.optString("bonuses"),
                allowances = o.optString("allowances"),
                taxVatPercent = o.optString("taxVatPercent").ifBlank { o.optString("taxDeductions") },
                otherDeductions = o.optString("otherDeductions")
            )
        } catch (_: Exception) {
            SalaryCalculatorSettings()
        }
    }

    fun saveSalaryCalculatorSettings(c: Context, settings: SalaryCalculatorSettings, monthKey: String? = null) {
        val o = JSONObject().apply {
            put("basicSalary", settings.basicSalary)
            put("hourlyRate", settings.hourlyRate)
            put("dailyRate", settings.dailyRate)
            put("overtimeRate", settings.overtimeRate)
            put("nightShiftRate", settings.nightShiftRate)
            put("holidayDayRate", settings.holidayDayRate)
            put("holidayNightRate", settings.holidayNightRate)
            put("benefits", settings.benefits)
            put("bonuses", settings.bonuses)
            put("allowances", settings.allowances)
            put("taxVatPercent", settings.taxVatPercent)
            put("otherDeductions", settings.otherDeductions)
        }
        val json = o.toString()
        val editor = schedulePrefs(c).edit()
        monthKey?.let { editor.putString("salary_calculator_$it", json) }
        // Always mirror to the month-less key: a month with no saved settings
        // falls back to it, so new months inherit the latest rates entered.
        editor.putString("salary_calculator", json)
        editor.apply()
    }

    /** Remove every monthly schedule (used when the user deletes the schedule). */
    fun clearAllSchedules(c: Context) {
        schedulePrefs(c).edit().remove("snapshots").apply()
    }

    /**
     * One-time migration from the old single repeating template to per-month
     * schedules: seed the month current at migration time with the saved template
     * so an existing user keeps their schedule. Runs at most once; brand-new users
     * simply start with no months scheduled.
     */
    fun migrateLegacyTemplate(c: Context) {
        val sp = schedulePrefs(c)
        if (sp.getBoolean("monthly_migrated", false)) return
        val cfg = getShiftCycle(c)
        if (cfg != null && cfg.isConfigured && cfg.dayAssignments.any { it != null }) {
            val now = Calendar.getInstance()
            val key = cycleKey(now.get(Calendar.YEAR), now.get(Calendar.MONTH) + 1, now.get(Calendar.DAY_OF_MONTH))
            val map = getCycleSnapshots(c).toMutableMap()
            if (!map.containsKey(key)) {
                map[key] = cfg.dayAssignments
                saveCycleSnapshots(c, map)
            }
        }
        sp.edit().putBoolean("monthly_migrated", true).apply()
    }

    /**
     * The cycle in effect for a given date under the per-month model: shared
     * config ([base]) combined with that month's own day assignments. A month
     * with no saved schedule has no work (empty assignments), so it renders
     * normally on the calendar.
     */
    fun cycleForDate(base: ShiftCycle, schedules: Map<String, List<String?>>, year: Int, month: Int, day: Int): ShiftCycle =
        base.copy(dayAssignments = schedules[cycleKey(year, month, day)] ?: emptyDayAssignments())

    // ─────────────────────────────────────────────────────────────────────────
    // ALARM SETTINGS — custom ringtone + behaviour
    // ─────────────────────────────────────────────────────────────────────────
    fun getRingtoneUri(c: Context): String? = settings(c).getString("reminder_ringtone_uri", null)
    fun setRingtoneUri(c: Context, uri: String?) {
        settings(c).edit().apply {
            if (uri == null) remove("reminder_ringtone_uri") else putString("reminder_ringtone_uri", uri)
        }.apply()
    }

    fun getRingtoneTitle(c: Context): String? = settings(c).getString("reminder_ringtone_title", null)
    fun setRingtoneTitle(c: Context, title: String?) {
        settings(c).edit().apply {
            if (title == null) remove("reminder_ringtone_title") else putString("reminder_ringtone_title", title)
        }.apply()
    }

    fun isInsistent(c: Context): Boolean = settings(c).getBoolean("reminder_insistent", false)
    fun setInsistent(c: Context, value: Boolean) {
        settings(c).edit().putBoolean("reminder_insistent", value).apply()
    }

    /** Default time-of-day a new reminder is pre-filled with (minutes from midnight). */
    fun getDefaultReminderMinutes(c: Context): Int = settings(c).getInt("reminder_default_minutes", 8 * 60)
    fun setDefaultReminderMinutes(c: Context, minutes: Int) {
        settings(c).edit().putInt("reminder_default_minutes", minutes).apply()
    }

    // ── util ─────────────────────────────────────────────────────────────────
    fun isCloudSyncEnabled(c: Context): Boolean = settings(c).getBoolean("cloud_sync_enabled", true)

    fun setCloudSyncEnabled(c: Context, enabled: Boolean) {
        settings(c).edit().putBoolean("cloud_sync_enabled", enabled).apply()
        if (enabled) {
            c.getSharedPreferences("khmer_calendar_sync", Context.MODE_PRIVATE)
                .edit()
                .remove("initial_sync_queued")
                .apply()
        }
    }

    fun clearLocalUserData(c: Context) {
        notesPrefs(c).edit().clear().apply()
        alarmsPrefs(c).edit().clear().apply()
        holidaysPrefs(c).edit().clear().apply()
        holidayCachePrefs(c).edit().clear().apply()
        schedulePrefs(c).edit().clear().apply()
        c.getSharedPreferences("khmer_calendar_sync", Context.MODE_PRIVATE).edit().clear().apply()
        settings(c).edit()
            .remove("user_name")
            .remove("profile_image_uri")
            .remove("reminder_ringtone_uri")
            .remove("reminder_ringtone_title")
            .remove("reminder_insistent")
            .remove("reminder_default_minutes")
            .remove("next_request_code")
            .remove("logged_out")
            .apply()
    }

    private fun newId(): String = "${System.currentTimeMillis()}_${(Math.random() * 100000).toInt()}"
}
