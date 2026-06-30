package com.example.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Calendar

object SyncRepository {
    private const val SYNC_FILE = "khmer_calendar_sync"
    private const val OPS_KEY = "pending_ops"
    private const val INITIAL_SYNC_QUEUED_KEY = "initial_sync_queued"
    private const val LAST_SUCCESS_MS_KEY = "last_success_ms"

    private const val TYPE_NOTE = "note"
    private const val TYPE_REMINDER = "reminder"
    private const val TYPE_HOLIDAY = "holiday"
    private const val TYPE_SCHEDULE = "schedule"

    private const val ACTION_UPSERT = "upsert"
    private const val ACTION_DELETE = "delete"
    private const val ACTION_SETTINGS = "settings"
    private const val ACTION_CYCLE = "cycle"

    private data class PendingOp(
        val id: String,
        val key: String,
        val type: String,
        val action: String,
        val localId: String?,
        val remoteId: String?,
        val date: LocalDate?,
        val payload: JSONObject,
        val createdAt: Long
    )

    private data class PendingRemoteIds(
        val noteUpserts: Set<String>,
        val noteDeletes: Set<String>,
        val reminderUpserts: Set<String>,
        val reminderDeletes: Set<String>,
        val holidayUpserts: Set<String>,
        val holidayDeletes: Set<String>
    )

    fun enqueueNoteUpsert(
        context: Context,
        localId: String,
        date: LocalDate,
        text: String,
        remoteId: String?
    ) {
        val cleanText = text.trim()
        if (cleanText.isEmpty()) {
            enqueueNoteDelete(context, localId, remoteId, date)
            return
        }
        enqueue(
            context,
            PendingOp(
                id = newId(),
                key = entityKey(TYPE_NOTE, remoteId, localId),
                type = TYPE_NOTE,
                action = ACTION_UPSERT,
                localId = localId,
                remoteId = remoteId?.takeIf { it.isNotBlank() },
                date = date,
                payload = JSONObject().put("text", cleanText),
                createdAt = System.currentTimeMillis()
            )
        )
    }

    fun enqueueNoteDelete(context: Context, localId: String, remoteId: String?, date: LocalDate) {
        if (remoteId.isNullOrBlank()) {
            removePendingKey(context, entityKey(TYPE_NOTE, null, localId))
            return
        }
        enqueue(
            context,
            PendingOp(
                id = newId(),
                key = entityKey(TYPE_NOTE, remoteId, localId),
                type = TYPE_NOTE,
                action = ACTION_DELETE,
                localId = localId,
                remoteId = remoteId,
                date = date,
                payload = JSONObject(),
                createdAt = System.currentTimeMillis()
            )
        )
    }

    fun enqueueReminderUpsert(context: Context, reminder: AppStore.Reminder) {
        if (reminder.kind != "reminder") return
        val date = eventDate(reminder.triggerMs)
        enqueue(
            context,
            PendingOp(
                id = newId(),
                key = entityKey(TYPE_REMINDER, reminder.remoteEventId, reminder.requestCode.toString()),
                type = TYPE_REMINDER,
                action = ACTION_UPSERT,
                localId = reminder.requestCode.toString(),
                remoteId = reminder.remoteEventId?.takeIf { it.isNotBlank() },
                date = date,
                payload = JSONObject()
                    .put("trigger_ms", reminder.triggerMs)
                    .put("title", reminder.title)
                    .put("message", reminder.message),
                createdAt = System.currentTimeMillis()
            )
        )
    }

    fun enqueueReminderDelete(context: Context, remoteEventId: String?, triggerMs: Long) {
        if (remoteEventId.isNullOrBlank()) return
        enqueue(
            context,
            PendingOp(
                id = newId(),
                key = entityKey(TYPE_REMINDER, remoteEventId, null),
                type = TYPE_REMINDER,
                action = ACTION_DELETE,
                localId = null,
                remoteId = remoteEventId,
                date = eventDate(triggerMs),
                payload = JSONObject().put("trigger_ms", triggerMs),
                createdAt = System.currentTimeMillis()
            )
        )
    }

    fun enqueueCustomHolidayUpsert(
        context: Context,
        holiday: AppStore.CustomHoliday,
        date: LocalDate
    ) {
        enqueue(
            context,
            PendingOp(
                id = newId(),
                key = entityKey(TYPE_HOLIDAY, holiday.remoteHolidayEventId, holiday.id),
                type = TYPE_HOLIDAY,
                action = ACTION_UPSERT,
                localId = holiday.id,
                remoteId = holiday.remoteHolidayEventId?.takeIf { it.isNotBlank() },
                date = date,
                payload = JSONObject()
                    .put("name_km", holiday.nameKm)
                    .put("name_en", holiday.nameEn),
                createdAt = System.currentTimeMillis()
            )
        )
    }

    fun enqueueCustomHolidayDelete(
        context: Context,
        holiday: AppStore.CustomHoliday,
        date: LocalDate
    ) {
        val remoteId = holiday.remoteHolidayEventId
        if (remoteId.isNullOrBlank()) {
            removePendingKey(context, entityKey(TYPE_HOLIDAY, null, holiday.id))
            return
        }
        enqueue(
            context,
            PendingOp(
                id = newId(),
                key = entityKey(TYPE_HOLIDAY, remoteId, holiday.id),
                type = TYPE_HOLIDAY,
                action = ACTION_DELETE,
                localId = holiday.id,
                remoteId = remoteId,
                date = date,
                payload = JSONObject(),
                createdAt = System.currentTimeMillis()
            )
        )
    }

    fun enqueueWorkSchedule(
        context: Context,
        cycle: AppStore.ShiftCycle,
        schedules: Map<String, List<String?>>
    ) {
        enqueue(
            context,
            PendingOp(
                id = newId(),
                key = "$TYPE_SCHEDULE:$ACTION_SETTINGS",
                type = TYPE_SCHEDULE,
                action = ACTION_SETTINGS,
                localId = null,
                remoteId = null,
                date = null,
                payload = JSONObject().put("cycle", cycleJson(cycle)),
                createdAt = System.currentTimeMillis()
            )
        )
        schedules.forEach { (key, assignments) ->
            val cycleStart = cycleStartDateFromKey(key) ?: return@forEach
            enqueueScheduleCycle(context, cycleStart, assignments)
        }
    }

    fun enqueueClearWorkSchedules(context: Context, keysToClear: Set<String>) {
        keysToClear.forEach { key ->
            val cycleStart = cycleStartDateFromKey(key) ?: return@forEach
            enqueueScheduleCycle(context, cycleStart, AppStore.emptyDayAssignments())
        }
    }

    suspend fun refreshMonth(
        context: Context,
        year: Int,
        month: Int,
        forceRefresh: Boolean = false
    ): Result<CalendarApiMonthOverlays> {
        if (!AppStore.isCloudSyncEnabled(context)) {
            return Result.failure(IOException("Cloud sync is disabled"))
        }
        syncPending(context)
        return CalendarApiRepository.fetchMonthOverlays(year, month, forceRefresh)
            .onSuccess { overlays ->
                materializeMonthOverlays(context, overlays)
                prefs(context).edit()
                    .putLong("last_refresh_${year}_$month", System.currentTimeMillis())
                    .apply()
            }
    }

    suspend fun syncPending(context: Context): Result<Int> {
        if (!AppStore.isCloudSyncEnabled(context)) return Result.success(0)
        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            ensureInitialLocalSyncQueued(context)
            val ops = readOps(context).sortedBy { it.createdAt }
            if (ops.isEmpty()) return@withContext Result.success(0)

            val remaining = mutableListOf<PendingOp>()
            var synced = 0
            var firstFailure: Throwable? = null

            ops.forEach { op ->
                val result = processOp(context, op)
                if (result.isSuccess) {
                    synced++
                } else {
                    remaining += op
                    if (firstFailure == null) firstFailure = result.exceptionOrNull()
                }
            }

            saveOps(context, remaining)
            if (firstFailure == null) {
                prefs(context).edit().putLong(LAST_SUCCESS_MS_KEY, System.currentTimeMillis()).apply()
                Result.success(synced)
            } else {
                Result.failure(firstFailure ?: IOException("Some pending changes could not sync"))
            }
        }
    }

    fun materializeMonthOverlays(context: Context, overlays: CalendarApiMonthOverlays) {
        val pending = pendingRemoteIds(context)

        overlays.notes.forEach { note ->
            if (note.id in pending.noteDeletes || note.id in pending.noteUpserts) return@forEach
            AppStore.upsertSyncedNote(
                context,
                note.date.year,
                note.date.monthValue,
                note.date.dayOfMonth,
                note.id,
                note.text
            )
        }

        overlays.events.forEach { event ->
            if (event.id in pending.reminderDeletes || event.id in pending.reminderUpserts) return@forEach
            val triggerMs = parseEventMillis(event.startsAt) ?: return@forEach
            AppStore.upsertSyncedReminder(
                context,
                remoteEventId = event.id,
                triggerMs = triggerMs,
                title = event.title,
                message = event.description.orEmpty()
            )
        }

        overlays.holidayEvents.forEach { holiday ->
            if (holiday.id in pending.holidayDeletes || holiday.id in pending.holidayUpserts) return@forEach
            val date = holiday.occurrenceDate ?: holiday.date
            AppStore.upsertSyncedCustomHoliday(
                context,
                month = date.monthValue,
                day = date.dayOfMonth,
                nameKm = holiday.nameKm,
                nameEn = holiday.nameEn,
                remoteHolidayEventId = holiday.id
            )
        }

        if (readOps(context).none { it.type == TYPE_SCHEDULE }) {
            materializeWorkShifts(context, overlays.workShifts)
        }
    }

    fun pendingCount(context: Context): Int = readOps(context).size

    private fun enqueueScheduleCycle(context: Context, cycleStart: LocalDate, assignments: List<String?>) {
        enqueue(
            context,
            PendingOp(
                id = newId(),
                key = "$TYPE_SCHEDULE:$ACTION_CYCLE:$cycleStart",
                type = TYPE_SCHEDULE,
                action = ACTION_CYCLE,
                localId = null,
                remoteId = null,
                date = cycleStart,
                payload = JSONObject().put("assignments", assignmentsJson(assignments)),
                createdAt = System.currentTimeMillis()
            )
        )
    }

    private suspend fun processOp(context: Context, op: PendingOp): Result<Unit> =
        when (op.type to op.action) {
            TYPE_NOTE to ACTION_UPSERT -> syncNoteUpsert(context, op)
            TYPE_NOTE to ACTION_DELETE -> syncNoteDelete(context, op)
            TYPE_REMINDER to ACTION_UPSERT -> syncReminderUpsert(context, op)
            TYPE_REMINDER to ACTION_DELETE -> syncReminderDelete(context, op)
            TYPE_HOLIDAY to ACTION_UPSERT -> syncHolidayUpsert(context, op)
            TYPE_HOLIDAY to ACTION_DELETE -> syncHolidayDelete(context, op)
            TYPE_SCHEDULE to ACTION_SETTINGS -> syncScheduleSettings(op)
            TYPE_SCHEDULE to ACTION_CYCLE -> syncScheduleCycle(op)
            else -> Result.success(Unit)
        }

    private suspend fun syncNoteUpsert(context: Context, op: PendingOp): Result<Unit> {
        val date = op.date ?: return Result.success(Unit)
        val text = op.payload.optString("text").trim()
        if (text.isEmpty()) return Result.success(Unit)
        val remoteId = op.remoteId?.takeIf { it.isNotBlank() }
        val result = if (remoteId == null) {
            CalendarApiRepository.createNote(date, text)
        } else {
            val update = CalendarApiRepository.updateNote(remoteId, date, text)
            if (update.isSuccess || update.exceptionOrNull()?.isNotFound() != true) update
            else CalendarApiRepository.createNote(date, text)
        }
        return result.fold(
            onSuccess = { note ->
                op.localId?.let { AppStore.setNoteRemoteId(context, date.year, date.monthValue, date.dayOfMonth, it, note.id) }
                AppStore.upsertSyncedNote(context, date.year, date.monthValue, date.dayOfMonth, note.id, note.text)
                Result.success(Unit)
            },
            onFailure = { Result.failure(it) }
        )
    }

    private suspend fun syncNoteDelete(context: Context, op: PendingOp): Result<Unit> {
        val remoteId = op.remoteId ?: return Result.success(Unit)
        val result = CalendarApiRepository.deleteNote(remoteId, op.date)
        return if (result.isSuccess || result.exceptionOrNull()?.isNotFound() == true) {
            op.date?.let { AppStore.deleteNoteByRemoteId(context, it.year, it.monthValue, it.dayOfMonth, remoteId) }
            Result.success(Unit)
        } else {
            Result.failure(result.exceptionOrNull() ?: IOException("Note delete failed"))
        }
    }

    private suspend fun syncReminderUpsert(context: Context, op: PendingOp): Result<Unit> {
        val triggerMs = op.payload.optLong("trigger_ms", 0L)
        if (triggerMs <= 0L) return Result.success(Unit)
        val date = eventDate(triggerMs)
        val title = op.payload.optString("title").trim().ifBlank { "Reminder" }
        val message = op.payload.optString("message").trim()
        val remoteId = op.remoteId?.takeIf { it.isNotBlank() }
        val result = if (remoteId == null) {
            CalendarApiRepository.createEvent(
                date = date,
                title = title,
                startsAt = eventStartsAt(triggerMs),
                description = message,
                color = "#EAB308",
                reminderMinutesBefore = 0
            )
        } else {
            val update = CalendarApiRepository.updateEvent(
                id = remoteId,
                date = date,
                title = title,
                startsAt = eventStartsAt(triggerMs),
                description = message,
                color = "#EAB308",
                reminderMinutesBefore = 0
            )
            if (update.isSuccess || update.exceptionOrNull()?.isNotFound() != true) update
            else CalendarApiRepository.createEvent(
                date = date,
                title = title,
                startsAt = eventStartsAt(triggerMs),
                description = message,
                color = "#EAB308",
                reminderMinutesBefore = 0
            )
        }
        return result.fold(
            onSuccess = { event ->
                op.localId?.toIntOrNull()?.let { AppStore.setReminderRemoteEventId(context, it, event.id) }
                AppStore.upsertSyncedReminder(context, event.id, triggerMs, event.title, event.description.orEmpty())
                Result.success(Unit)
            },
            onFailure = { Result.failure(it) }
        )
    }

    private suspend fun syncReminderDelete(context: Context, op: PendingOp): Result<Unit> {
        val remoteId = op.remoteId ?: return Result.success(Unit)
        val result = CalendarApiRepository.deleteEvent(remoteId, op.date)
        return if (result.isSuccess || result.exceptionOrNull()?.isNotFound() == true) {
            AppStore.deleteReminderByRemoteEventId(context, remoteId)
            Result.success(Unit)
        } else {
            Result.failure(result.exceptionOrNull() ?: IOException("Reminder delete failed"))
        }
    }

    private suspend fun syncHolidayUpsert(context: Context, op: PendingOp): Result<Unit> {
        val date = op.date ?: return Result.success(Unit)
        val nameKm = op.payload.optString("name_km").trim()
        val nameEn = op.payload.optString("name_en").trim().ifBlank { nameKm }
        if (nameKm.isEmpty() && nameEn.isEmpty()) return Result.success(Unit)
        val remoteId = op.remoteId?.takeIf { it.isNotBlank() }
        val result = if (remoteId == null) {
            CalendarApiRepository.createHolidayEvent(date, nameKm, nameEn)
        } else {
            val update = CalendarApiRepository.updateHolidayEvent(remoteId, date, nameKm, nameEn)
            if (update.isSuccess || update.exceptionOrNull()?.isNotFound() != true) update
            else CalendarApiRepository.createHolidayEvent(date, nameKm, nameEn)
        }
        return result.fold(
            onSuccess = { event ->
                op.localId?.let { AppStore.setCustomHolidayRemoteEventId(context, it, event.id) }
                val eventDate = event.occurrenceDate ?: event.date
                AppStore.upsertSyncedCustomHoliday(
                    context,
                    eventDate.monthValue,
                    eventDate.dayOfMonth,
                    event.nameKm,
                    event.nameEn,
                    event.id
                )
                Result.success(Unit)
            },
            onFailure = { Result.failure(it) }
        )
    }

    private suspend fun syncHolidayDelete(context: Context, op: PendingOp): Result<Unit> {
        val remoteId = op.remoteId ?: return Result.success(Unit)
        val result = CalendarApiRepository.deleteHolidayEvent(remoteId, op.date)
        return if (result.isSuccess || result.exceptionOrNull()?.isNotFound() == true) {
            AppStore.deleteCustomHolidayByRemoteEventId(context, remoteId)
            Result.success(Unit)
        } else {
            Result.failure(result.exceptionOrNull() ?: IOException("Holiday delete failed"))
        }
    }

    private suspend fun syncScheduleSettings(op: PendingOp): Result<Unit> {
        val cycle = op.payload.optJSONObject("cycle")?.let(::parseCycle)
            ?: return Result.success(Unit)
        return CalendarApiRepository.updateWorkScheduleSettings(cycle)
    }

    private suspend fun syncScheduleCycle(op: PendingOp): Result<Unit> {
        val date = op.date ?: return Result.success(Unit)
        val assignments = parseAssignments(op.payload.optJSONArray("assignments"))
        return CalendarApiRepository.updateWorkScheduleCycle(date, assignments)
    }

    private fun ensureInitialLocalSyncQueued(context: Context) {
        val p = prefs(context)
        if (p.getBoolean(INITIAL_SYNC_QUEUED_KEY, false)) return

        AppStore.getAllNotes(context)
            .filter { it.note.remoteId.isNullOrBlank() }
            .forEach { dated ->
                enqueueNoteUpsert(
                    context,
                    dated.note.id,
                    LocalDate.of(dated.year, dated.month, dated.day),
                    dated.note.text,
                    null
                )
            }

        AppStore.getReminders(context)
            .filter { it.kind == "reminder" && it.remoteEventId.isNullOrBlank() }
            .forEach { enqueueReminderUpsert(context, it) }

        val currentYear = LocalDate.now().year
        AppStore.getCustomHolidays(context)
            .filter { it.remoteHolidayEventId.isNullOrBlank() }
            .forEach { holiday ->
                val date = runCatching { LocalDate.of(currentYear, holiday.month, holiday.day) }.getOrNull()
                    ?: return@forEach
                enqueueCustomHolidayUpsert(context, holiday, date)
            }

        val cycle = AppStore.getShiftCycle(context)
        val schedules = AppStore.getCycleSnapshots(context)
        if (cycle != null && cycle.isConfigured && schedules.isNotEmpty()) {
            enqueueWorkSchedule(context, cycle, schedules)
        }

        p.edit().putBoolean(INITIAL_SYNC_QUEUED_KEY, true).apply()
    }

    private fun materializeWorkShifts(context: Context, workShifts: List<CalendarApiWorkShift>) {
        if (workShifts.isEmpty()) return
        val remoteShiftDefs = workShifts.mapNotNull { it.shiftTemplate?.toShiftDef() }
            .distinctBy { it.id }
        if (remoteShiftDefs.isEmpty()) return

        val existing = AppStore.getShiftCycle(context)
        val mergedShifts = ((existing?.shifts).orEmpty() + remoteShiftDefs)
            .distinctBy { it.id }
        val base = existing?.copy(shifts = mergedShifts) ?: AppStore.ShiftCycle(
            systemType = if (mergedShifts.size >= 3) 3 else 2,
            shifts = mergedShifts,
            dayAssignments = AppStore.emptyDayAssignments(),
            remind = false,
            reminderMinutesBefore = 30
        )
        AppStore.saveShiftCycle(context, base)

        val schedules = AppStore.getCycleSnapshots(context).toMutableMap()
        workShifts.forEach { workShift ->
            val key = AppStore.cycleKey(workShift.date.year, workShift.date.monthValue, workShift.date.dayOfMonth)
            val offset = workShift.dayOffset ?: cycleOffset(workShift.date)
            if (offset !in 0 until AppStore.CYCLE_SLOTS) return@forEach
            val assignment = if (workShift.blocked) null else workShift.shiftTemplate?.toShiftDef()?.id
            val days = (schedules[key] ?: AppStore.emptyDayAssignments()).toMutableList()
            days[offset] = assignment
            schedules[key] = days
        }
        AppStore.saveMonthlySchedules(context, schedules)
    }

    private fun cycleOffset(date: LocalDate): Int {
        val start = WorkCycleEngine.cycleStart(date.year, date.monthValue, date.dayOfMonth)
        val startDate = LocalDate.of(
            start.get(Calendar.YEAR),
            start.get(Calendar.MONTH) + 1,
            start.get(Calendar.DAY_OF_MONTH)
        )
        return ChronoUnit.DAYS.between(startDate, date).toInt()
    }

    private fun enqueue(context: Context, op: PendingOp) {
        val ops = readOps(context)
            .filterNot { it.key == op.key }
            .toMutableList()
        if (op.action == ACTION_DELETE) {
            ops.removeAll { it.key == op.key && it.action == ACTION_UPSERT }
        }
        ops += op
        saveOps(context, ops)
    }

    private fun removePendingKey(context: Context, key: String) {
        saveOps(context, readOps(context).filterNot { it.key == key })
    }

    private fun pendingRemoteIds(context: Context): PendingRemoteIds {
        val ops = readOps(context)
        fun ids(type: String, action: String): Set<String> =
            ops.filter { it.type == type && it.action == action }
                .mapNotNull { it.remoteId?.takeIf(String::isNotBlank) }
                .toSet()
        return PendingRemoteIds(
            noteUpserts = ids(TYPE_NOTE, ACTION_UPSERT),
            noteDeletes = ids(TYPE_NOTE, ACTION_DELETE),
            reminderUpserts = ids(TYPE_REMINDER, ACTION_UPSERT),
            reminderDeletes = ids(TYPE_REMINDER, ACTION_DELETE),
            holidayUpserts = ids(TYPE_HOLIDAY, ACTION_UPSERT),
            holidayDeletes = ids(TYPE_HOLIDAY, ACTION_DELETE)
        )
    }

    private fun readOps(context: Context): List<PendingOp> = runCatching {
        val arr = JSONArray(prefs(context).getString(OPS_KEY, "[]") ?: "[]")
        (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            PendingOp(
                id = o.optString("id").ifBlank { newId() },
                key = o.optString("key"),
                type = o.optString("type"),
                action = o.optString("action"),
                localId = o.optString("local_id").takeIf { it.isNotBlank() },
                remoteId = o.optString("remote_id").takeIf { it.isNotBlank() },
                date = parseDate(o.optString("date").takeIf { it.isNotBlank() }),
                payload = o.optJSONObject("payload") ?: JSONObject(),
                createdAt = o.optLong("created_at", 0L)
            ).takeIf { it.key.isNotBlank() && it.type.isNotBlank() && it.action.isNotBlank() }
        }
    }.getOrDefault(emptyList())

    private fun saveOps(context: Context, ops: List<PendingOp>) {
        val arr = JSONArray()
        ops.distinctBy { it.key }.forEach { op ->
            arr.put(JSONObject()
                .put("id", op.id)
                .put("key", op.key)
                .put("type", op.type)
                .put("action", op.action)
                .put("created_at", op.createdAt)
                .put("payload", op.payload)
                .apply {
                    op.localId?.let { put("local_id", it) }
                    op.remoteId?.let { put("remote_id", it) }
                    op.date?.let { put("date", it.toString()) }
                }
            )
        }
        prefs(context).edit().putString(OPS_KEY, arr.toString()).apply()
    }

    private fun entityKey(type: String, remoteId: String?, localId: String?): String {
        val id = remoteId?.takeIf { it.isNotBlank() } ?: localId.orEmpty()
        return "$type:$id"
    }

    private fun cycleJson(cycle: AppStore.ShiftCycle): JSONObject = JSONObject()
        .put("systemType", cycle.systemType)
        .put("remind", cycle.remind)
        .put("reminderMinutesBefore", cycle.reminderMinutesBefore)
        .put("shifts", JSONArray().apply {
            cycle.shifts.forEach { shift ->
                put(JSONObject()
                    .put("id", shift.id)
                    .put("name", shift.name)
                    .put("startHour", shift.startHour)
                    .put("startMin", shift.startMin)
                    .put("endHour", shift.endHour)
                    .put("endMin", shift.endMin)
                )
            }
        })
        .put("dayAssignments", assignmentsJson(cycle.dayAssignments))

    private fun parseCycle(o: JSONObject): AppStore.ShiftCycle {
        val shiftsArr = o.optJSONArray("shifts") ?: JSONArray()
        val shifts = (0 until shiftsArr.length()).mapNotNull { i ->
            val s = shiftsArr.optJSONObject(i) ?: return@mapNotNull null
            AppStore.ShiftDef(
                id = s.optString("id"),
                name = s.optString("name"),
                startHour = s.optInt("startHour"),
                startMin = s.optInt("startMin"),
                endHour = s.optInt("endHour"),
                endMin = s.optInt("endMin")
            )
        }
        return AppStore.ShiftCycle(
            systemType = o.optInt("systemType", if (shifts.size >= 3) 3 else 2),
            shifts = shifts,
            dayAssignments = parseAssignments(o.optJSONArray("dayAssignments")),
            remind = o.optBoolean("remind", true),
            reminderMinutesBefore = o.optInt("reminderMinutesBefore", 30)
        )
    }

    private fun assignmentsJson(assignments: List<String?>): JSONArray = JSONArray().apply {
        val padded = assignments.take(AppStore.CYCLE_SLOTS) +
            List((AppStore.CYCLE_SLOTS - assignments.size).coerceAtLeast(0)) { null }
        padded.forEach { put(it ?: "") }
    }

    private fun parseAssignments(arr: JSONArray?): List<String?> =
        (0 until AppStore.CYCLE_SLOTS).map { i ->
            if (arr != null && i < arr.length()) arr.optString(i).ifBlank { null } else null
        }

    private fun cycleStartDateFromKey(key: String): LocalDate? {
        val parts = key.split("-")
        val year = parts.getOrNull(0)?.toIntOrNull() ?: return null
        val month = parts.getOrNull(1)?.toIntOrNull() ?: return null
        return runCatching { LocalDate.of(year, month, 26) }.getOrNull()
    }

    private fun eventDate(triggerMs: Long): LocalDate =
        Instant.ofEpochMilli(triggerMs).atZone(ZoneId.systemDefault()).toLocalDate()

    private fun eventStartsAt(triggerMs: Long): String =
        Instant.ofEpochMilli(triggerMs)
            .atZone(ZoneId.systemDefault())
            .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)

    private fun parseEventMillis(value: String?): Long? {
        val raw = value?.trim()?.takeIf { it.isNotBlank() } ?: return null
        return runCatching { Instant.parse(raw).toEpochMilli() }.getOrNull()
            ?: runCatching { OffsetDateTime.parse(raw).toInstant().toEpochMilli() }.getOrNull()
            ?: runCatching {
                LocalDateTime.parse(raw.take(19))
                    .atZone(ZoneId.systemDefault())
                    .toInstant()
                    .toEpochMilli()
            }.getOrNull()
    }

    private fun parseDate(value: String?): LocalDate? =
        value?.let { runCatching { LocalDate.parse(it.take(10)) }.getOrNull() }

    private fun Throwable.isNotFound(): Boolean =
        message?.contains("HTTP 404", ignoreCase = true) == true

    private fun prefs(context: Context) =
        context.getSharedPreferences(SYNC_FILE, Context.MODE_PRIVATE)

    private fun newId(): String = "${System.currentTimeMillis()}_${(Math.random() * 100000).toInt()}"
}
