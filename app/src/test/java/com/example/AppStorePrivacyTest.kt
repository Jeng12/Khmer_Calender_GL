package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.*
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AppStorePrivacyTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        AuthStore.clearForTests(context)
        AppStore.clearLocalUserData(context)
        AppStore.setCloudSyncEnabled(context, true)
    }

    @After
    fun tearDown() {
        AppStore.clearLocalUserData(context)
        AuthStore.clearForTests(context)
        AppStore.setCloudSyncEnabled(context, true)
    }

    @Test
    fun cloudSyncTogglePersists() {
        assertTrue(AppStore.isCloudSyncEnabled(context))

        AppStore.setCloudSyncEnabled(context, false)

        assertFalse(AppStore.isCloudSyncEnabled(context))
    }

    @Test
    fun clearLocalUserDataRemovesCalendarProfileAndAiDataButKeepsSyncChoice() {
        AppStore.setCloudSyncEnabled(context, false)
        AppStore.addNote(context, 2026, 6, 28, "Private note")
        AppStore.addCustomHoliday(context, 6, 28, "Holiday", "Holiday")
        AppStore.saveShiftCycle(
            context,
            AppStore.ShiftCycle(
                systemType = 2,
                shifts = AppStore.presetShifts(2),
                dayAssignments = List(AppStore.CYCLE_SLOTS) { index -> if (index == 0) "day" else null },
                remind = true,
                reminderMinutesBefore = 30
            )
        )
        AppStore.addAiContentReport(
            c = context,
            dateLabel = "June 28, 2026",
            content = "AI output",
            reason = "Wrong"
        )

        AppStore.clearLocalUserData(context)

        assertTrue(AppStore.getNotes(context, 2026, 6, 28).isEmpty())
        assertTrue(AppStore.getCustomHolidays(context).isEmpty())
        assertNull(AppStore.getShiftCycle(context))
        assertTrue(AppStore.getAiContentReports(context).isEmpty())
        assertFalse(AppStore.isCloudSyncEnabled(context))
    }

    @Test
    fun customHolidayRemoteEventIdPersistsAndDeleteReturnsHoliday() {
        val holiday = AppStore.addCustomHoliday(context, 6, 28, "Holiday", "Holiday")!!

        AppStore.setCustomHolidayRemoteEventId(context, holiday.id, "remote-123")

        val stored = AppStore.getCustomHolidays(context).single()
        assertEquals("remote-123", stored.remoteHolidayEventId)
        val deleted = AppStore.deleteCustomHoliday(context, stored.id)
        assertEquals("remote-123", deleted?.remoteHolidayEventId)
        assertTrue(AppStore.getCustomHolidays(context).isEmpty())
    }

    @Test
    fun authSessionPersistsAndSwitchingAccountsClearsLocalCalendarData() {
        val first = AuthStore.register(context, "First", "User", "first@example.com", "secret1")
        assertTrue(first is AuthStore.AuthResult.Success)
        AppStore.addNote(context, 2026, 6, 28, "First user note")

        val second = AuthStore.register(context, "Second", "User", "second@example.com", "secret2")
        assertTrue(second is AuthStore.AuthResult.Success)

        val firstSession = (first as AuthStore.AuthResult.Success).session
        val secondSession = (second as AuthStore.AuthResult.Success).session
        assertNotEquals(firstSession.userId, secondSession.userId)
        assertEquals(secondSession.email, AuthStore.currentSession(context)?.email)
        assertTrue(AppStore.getNotes(context, 2026, 6, 28).isEmpty())
    }

    @Test
    fun aiContentReportsAreStoredNewestLast() {
        AppStore.addAiContentReport(context, "Day 1", "Text 1", "Reason 1")
        AppStore.addAiContentReport(context, "Day 2", "Text 2", "Reason 2")

        val reports = AppStore.getAiContentReports(context)

        assertEquals(2, reports.size)
        assertEquals("Day 1", reports[0].dateLabel)
        assertEquals("Reason 2", reports[1].reason)
    }

    @Test
    fun remoteOverlaysMaterializeLocallyAndDeduplicateByRemoteId() {
        val date = LocalDate.of(2026, 6, 28)
        val overlays = CalendarApiMonthOverlays(
            year = 2026,
            month = 6,
            notes = listOf(CalendarApiNote("note-1", date, "Remote note")),
            events = listOf(
                CalendarApiEvent(
                    id = "event-1",
                    title = "Remote reminder",
                    description = "Remote details",
                    startsAt = "2026-06-28T08:30:00+07:00",
                    endsAt = null,
                    allDay = false,
                    location = null,
                    color = null,
                    reminderMinutesBefore = 0
                )
            ),
            holidayEvents = listOf(
                CalendarApiHolidayEvent(
                    id = "holiday-1",
                    nameKm = "Holiday KM",
                    nameEn = "Holiday EN",
                    date = date,
                    occurrenceDate = null,
                    type = "custom",
                    description = null,
                    notes = null
                )
            ),
            workShifts = emptyList()
        )

        SyncRepository.materializeMonthOverlays(context, overlays)
        SyncRepository.materializeMonthOverlays(
            context,
            overlays.copy(notes = listOf(CalendarApiNote("note-1", date, "Remote note updated")))
        )

        val notes = AppStore.getNotes(context, 2026, 6, 28)
        assertEquals(1, notes.size)
        assertEquals("note-1", notes.single().remoteId)
        assertEquals("Remote note updated", notes.single().text)
        assertEquals("event-1", AppStore.getReminders(context).single().remoteEventId)
        assertEquals("holiday-1", AppStore.getCustomHolidays(context).single().remoteHolidayEventId)
    }

    @Test
    fun pendingDeletePreventsRemoteMaterializationFromRecreatingLocalRecord() {
        val date = LocalDate.of(2026, 6, 28)

        SyncRepository.enqueueNoteDelete(context, "remote-note-note-2", "note-2", date)
        SyncRepository.materializeMonthOverlays(
            context,
            CalendarApiMonthOverlays(
                year = 2026,
                month = 6,
                notes = listOf(CalendarApiNote("note-2", date, "Should stay deleted locally")),
                events = emptyList(),
                holidayEvents = emptyList(),
                workShifts = emptyList()
            )
        )

        assertTrue(AppStore.getNotes(context, 2026, 6, 28).isEmpty())
        assertEquals(1, SyncRepository.pendingCount(context))
    }

    @Test
    fun clearLocalUserDataClearsPendingSyncQueue() {
        SyncRepository.enqueueNoteUpsert(
            context,
            localId = "local-note",
            date = LocalDate.of(2026, 6, 28),
            text = "Pending note",
            remoteId = null
        )
        assertEquals(1, SyncRepository.pendingCount(context))

        AppStore.clearLocalUserData(context)

        assertEquals(0, SyncRepository.pendingCount(context))
    }
}
