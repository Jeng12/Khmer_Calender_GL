package com.example.data

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
    private const val SCHEDULE_FILE = "khmer_calendar_schedule"
    const val SETTINGS_FILE = "khmer_calendar_prefs"

    private fun notesPrefs(c: Context) = c.getSharedPreferences(NOTES_FILE, Context.MODE_PRIVATE)
    private fun alarmsPrefs(c: Context) = c.getSharedPreferences(ALARMS_FILE, Context.MODE_PRIVATE)
    private fun holidaysPrefs(c: Context) = c.getSharedPreferences(HOLIDAYS_FILE, Context.MODE_PRIVATE)
    private fun schedulePrefs(c: Context) = c.getSharedPreferences(SCHEDULE_FILE, Context.MODE_PRIVATE)
    private fun settings(c: Context) = c.getSharedPreferences(SETTINGS_FILE, Context.MODE_PRIVATE)

    fun dateKey(year: Int, month: Int, day: Int) = "${year}_${month}_$day"

    // ─────────────────────────────────────────────────────────────────────────
    // NOTES — multiple per day
    // ─────────────────────────────────────────────────────────────────────────
    data class Note(val id: String, val text: String, val ts: Long)

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
            else Note(o.optString("id").ifBlank { newId() }, text, o.optLong("ts", 0L))
        }
    } catch (_: Exception) {
        emptyList()
    }

    fun addNote(c: Context, year: Int, month: Int, day: Int, text: String): Boolean {
        val clean = text.trim()
        if (clean.isEmpty()) return false
        val updated = getNotes(c, year, month, day) + Note(newId(), clean, System.currentTimeMillis())
        writeNotes(c, year, month, day, updated)
        return true
    }

    fun updateNote(c: Context, year: Int, month: Int, day: Int, id: String, text: String) {
        val clean = text.trim()
        val updated = getNotes(c, year, month, day).map {
            if (it.id == id) it.copy(text = clean) else it
        }.filter { it.text.isNotEmpty() }
        writeNotes(c, year, month, day, updated)
    }

    fun deleteNote(c: Context, year: Int, month: Int, day: Int, id: String) {
        val updated = getNotes(c, year, month, day).filterNot { it.id == id }
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
        val shiftId: String?
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
                shiftId = o.optString("shiftId").ifBlank { null }
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

    /** A fresh, monotonically increasing request code so reminders never collide. */
    fun nextRequestCode(c: Context): Int {
        val s = settings(c)
        val next = s.getInt("next_request_code", 100_000) + 1
        s.edit().putInt("next_request_code", next).apply()
        return next
    }

    // ─────────────────────────────────────────────────────────────────────────
    // CUSTOM HOLIDAYS — user added, recurring yearly on a Gregorian month/day
    // ─────────────────────────────────────────────────────────────────────────
    data class CustomHoliday(
        val id: String,
        val month: Int,    // 1..12
        val day: Int,      // 1..31
        val nameKm: String,
        val nameEn: String
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
                nameEn = o.optString("nameEn").ifBlank { o.optString("nameKm") }
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
            })
        }
        holidaysPrefs(c).edit().putString("holidays", arr.toString()).apply()
    }

    fun addCustomHoliday(c: Context, month: Int, day: Int, nameKm: String, nameEn: String) {
        val km = nameKm.trim().ifBlank { nameEn.trim() }
        val en = nameEn.trim().ifBlank { km }
        if (km.isEmpty()) return
        saveCustomHolidays(c, getCustomHolidays(c) + CustomHoliday(newId(), month, day, km, en))
    }

    fun deleteCustomHoliday(c: Context, id: String) {
        saveCustomHolidays(c, getCustomHolidays(c).filterNot { it.id == id })
    }

    fun customHolidaysForMonth(c: Context, month: Int): List<CustomHoliday> =
        getCustomHolidays(c).filter { it.month == month }

    // ─────────────────────────────────────────────────────────────────────────
    // WORK SCHEDULE — recurring weekly shifts
    // ─────────────────────────────────────────────────────────────────────────
    data class WorkShift(
        val id: String,
        val label: String,
        val daysOfWeek: List<Int>,   // Calendar.SUNDAY(1)..SATURDAY(7)
        val startHour: Int,
        val startMin: Int,
        val endHour: Int,
        val endMin: Int,
        val remind: Boolean,
        val reminderMinutesBefore: Int
    )

    fun getShifts(c: Context): List<WorkShift> = try {
        val arr = JSONArray(schedulePrefs(c).getString("shifts", "[]") ?: "[]")
        (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val daysArr = o.optJSONArray("days") ?: JSONArray()
            val days = (0 until daysArr.length()).map { daysArr.optInt(it) }
            WorkShift(
                id = o.optString("id").ifBlank { newId() },
                label = o.optString("label"),
                daysOfWeek = days,
                startHour = o.optInt("startHour"),
                startMin = o.optInt("startMin"),
                endHour = o.optInt("endHour"),
                endMin = o.optInt("endMin"),
                remind = o.optBoolean("remind", true),
                reminderMinutesBefore = o.optInt("reminderMinutesBefore", 30)
            )
        }
    } catch (_: Exception) {
        emptyList()
    }

    private fun saveShifts(c: Context, list: List<WorkShift>) {
        val arr = JSONArray()
        list.forEach { s ->
            arr.put(JSONObject().apply {
                put("id", s.id)
                put("label", s.label)
                put("days", JSONArray().apply { s.daysOfWeek.forEach { put(it) } })
                put("startHour", s.startHour); put("startMin", s.startMin)
                put("endHour", s.endHour); put("endMin", s.endMin)
                put("remind", s.remind)
                put("reminderMinutesBefore", s.reminderMinutesBefore)
            })
        }
        schedulePrefs(c).edit().putString("shifts", arr.toString()).apply()
    }

    fun upsertShift(c: Context, shift: WorkShift) {
        val list = getShifts(c)
        val updated = if (list.any { it.id == shift.id })
            list.map { if (it.id == shift.id) shift else it }
        else list + shift
        saveShifts(c, updated)
    }

    fun deleteShift(c: Context, id: String) {
        saveShifts(c, getShifts(c).filterNot { it.id == id })
    }

    /** Shifts that occur on the given Calendar day-of-week (1=Sun .. 7=Sat). */
    fun shiftsForDayOfWeek(c: Context, dayOfWeek: Int): List<WorkShift> =
        getShifts(c).filter { dayOfWeek in it.daysOfWeek }

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
    private fun newId(): String = "${System.currentTimeMillis()}_${(Math.random() * 100000).toInt()}"
}
