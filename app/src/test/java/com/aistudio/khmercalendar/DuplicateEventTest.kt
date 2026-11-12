package com.aistudio.khmercalendar

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.aistudio.khmercalendar.data.AppStore
import com.aistudio.khmercalendar.widget.AgendaRepository
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Calendar

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DuplicateEventTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val trigger: Long = Calendar.getInstance().apply {
        add(Calendar.DAY_OF_YEAR, 1); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private fun reminder(code: Int, title: String, remoteId: String? = null) =
        AppStore.Reminder(code, trigger, title, "", null, false, "reminder", null, remoteId)

    @Before
    fun clear() = AppStore.clearLocalUserData(context)

    @Test
    fun syncedEvent_adoptsMatchingLocalReminder_insteadOfAddingCopy() {
        AppStore.upsertReminder(context, reminder(100_001, "Dentist"))
        AppStore.upsertSyncedReminder(context, "remote-1", trigger, "Dentist", "")

        val stored = AppStore.getReminders(context)
        assertEquals(1, stored.size)
        assertEquals(100_001, stored.single().requestCode)
        assertEquals("remote-1", stored.single().remoteEventId)
    }

    @Test
    fun widgetAgenda_showsAStoredDuplicateOnce() {
        // Same event already stored twice: once linked twice, once as a server duplicate.
        AppStore.saveReminders(
            context,
            listOf(
                reminder(1, "Dentist", "remote-1"),
                reminder(2, "Dentist", "remote-1"),
                reminder(3, "Dentist", "remote-2"),
                reminder(4, "Meeting", "remote-3")
            )
        )
        val events = AgendaRepository.load(context).filter { it.isEvent }
        assertEquals(listOf("Dentist", "Meeting"), events.map { it.text }.sorted())
    }

    @Test
    fun syncedNote_linksMatchingLocalNote_insteadOfAddingCopy() {
        AppStore.addNote(context, 2026, 11, 20, "Buy rice")
        AppStore.upsertSyncedNote(context, 2026, 11, 20, "note-9", "Buy rice")

        val notes = AppStore.getNotes(context, 2026, 11, 20)
        assertEquals(1, notes.size)
        assertEquals("note-9", notes.single().remoteId)
    }
}
