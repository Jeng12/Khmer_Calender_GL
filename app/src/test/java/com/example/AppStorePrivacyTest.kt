package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppStore
import com.example.data.AuthStore
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
}
