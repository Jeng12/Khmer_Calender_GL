package com.aistudio.khmercalendar

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.aistudio.khmercalendar.core.AppLanguage
import com.aistudio.khmercalendar.core.LocalAppLanguage
import com.aistudio.khmercalendar.data.AppStore
import com.aistudio.khmercalendar.data.ScheduleImageParser.Reading
import com.aistudio.khmercalendar.ui.tabs.ImportReviewDialog
import com.aistudio.khmercalendar.ui.tabs.ImportTarget
import com.aistudio.khmercalendar.ui.tabs.buildImportPreview
import com.aistudio.khmercalendar.ui.theme.MyApplicationTheme
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureScreenRoboImage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.Calendar

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [36])
class ScheduleImportTest {

    @get:Rule val composeTestRule = createComposeRule()

    private val shifts = AppStore.presetShifts(2)

    /** Cycle 26 Oct → 25 Nov 2026 (31 days), with one shift already set on the 26th. */
    private fun target(): ImportTarget {
        val start = Calendar.getInstance().apply { clear(); set(2026, Calendar.OCTOBER, 26) }
        val current = AppStore.emptyDayAssignments().toMutableList().also { it[0] = "night" }
        return ImportTarget("2026-10", start.timeInMillis, 31, current)
    }

    @Test
    fun readings_landOnTheRightDaysOfTheCycle() {
        val preview = buildImportPreview(
            target(),
            mapOf(27 to Reading.Shift("day"), 31 to Reading.Off, 1 to Reading.Shift("night"), 25 to Reading.Shift("day"))
        )
        assertEquals("night", preview.assignments[0])   // 26 Oct: not in the picture, kept
        assertEquals("day", preview.assignments[1])     // 27 Oct
        assertNull(preview.assignments[5])              // 31 Oct: off
        assertEquals("night", preview.assignments[6])   // 1 Nov
        assertEquals("day", preview.assignments[30])    // 25 Nov
        assertEquals(setOf(1, 5, 6, 30), preview.detected)
    }

    @Test
    fun leaveAndHoliday_becomeMarkedDaysOff_notShifts() {
        val preview = buildImportPreview(
            target(),
            mapOf(
                27 to Reading.Marked(AppStore.MARK_ANNUAL_LEAVE),
                28 to Reading.Marked(AppStore.MARK_HOLIDAY),
                29 to Reading.Shift("day")
            )
        )
        assertNull(preview.assignments[1])
        assertEquals(AppStore.MARK_ANNUAL_LEAVE, preview.marks[1])
        assertNull(preview.assignments[2])
        assertEquals(AppStore.MARK_HOLIDAY, preview.marks[2])
        assertEquals("day", preview.assignments[3])
        assertNull(preview.marks[3])
    }

    @OptIn(ExperimentalRoborazziApi::class)
    @Test
    fun reviewDialog_letsADayBeCorrectedBeforeApplying() {
        val preview = buildImportPreview(
            target(),
            (27..31).associateWith { Reading.Shift("day") } + (1..10).associateWith { Reading.Shift("night") } +
                mapOf(11 to Reading.Off, 12 to Reading.Off)
        )
        var applied: List<String?>? = null
        composeTestRule.setContent {
            MyApplicationTheme {
                CompositionLocalProvider(LocalAppLanguage provides AppLanguage.EN) {
                    ImportReviewDialog(preview, shifts, AppLanguage.EN, onDismiss = {}, onApply = { assignments, _ -> applied = assignments })
                }
            }
        }
        captureScreenRoboImage("src/test/screenshots/schedule_import_review.png")

        // 13 Nov was not in the picture (off); one tap makes it the first shift.
        composeTestRule.onNodeWithText("13").performClick()
        composeTestRule.onNodeWithText("Use this schedule").performClick()
        assertEquals("day", applied!![18])
        assertEquals("night", applied!![6])
    }
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [36])
class RosterSetupDialogTest {

    @get:Rule val composeTestRule = createComposeRule()

    private val shifts = AppStore.presetShifts(2)
    private val days = (9..15).toList()
    private fun row(label: String, vararg codes: String) =
        com.aistudio.khmercalendar.data.ScheduleImageParser.RosterRow(label, days.zip(codes.toList()).toMap())

    private val roster = com.aistudio.khmercalendar.data.ScheduleImageParser.Roster(
        days,
        listOf(
            row("1 EMP101 STAFF ONE Team Lead", "H", "8", "8", "8", "AL", "8", "N"),
            row("4 EMP104 STAFF FOUR Lab-Operator", "12", "12D", "12D", "N", "N", "12", "12"),
            row("5 EMP105 STAFF FIVE Lab-Operator", "H", "12", "12", "12D", "12D", "N", "N")
        )
    )

    @OptIn(ExperimentalRoborazziApi::class)
    @Test
    fun rememberedRowIsPreselected_andCodesBecomeShifts() {
        val start = Calendar.getInstance().apply { clear(); set(2026, Calendar.OCTOBER, 26) }
        val target = ImportTarget("2026-10", start.timeInMillis, 31, AppStore.emptyDayAssignments())
        var picked: Pair<String, Map<String, String>>? = null
        composeTestRule.setContent {
            MyApplicationTheme {
                CompositionLocalProvider(LocalAppLanguage provides AppLanguage.EN) {
                    com.aistudio.khmercalendar.ui.tabs.RosterSetupDialog(
                        setup = com.aistudio.khmercalendar.ui.tabs.RosterSetup(target, roster),
                        shifts = shifts,
                        lang = AppLanguage.EN,
                        savedRowKey = "EMP104",
                        savedMeanings = emptyMap(),
                        onDismiss = {},
                        onContinue = { row, meanings -> picked = row.key to meanings }
                    )
                }
            }
        }
        captureScreenRoboImage("src/test/screenshots/schedule_import_roster.png")
        composeTestRule.onNodeWithText("Continue").performClick()

        assertEquals("EMP104", picked!!.first)
        assertEquals(mapOf("12" to "day", "12D" to "night", "N" to ""), picked!!.second)
    }
}


@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [36])
class RosterOfficeRowTest {

    @get:Rule val composeTestRule = createComposeRule()

    @Test
    fun officeRow_mapsEightToTheEightHourShift_andLeaveAndHolidayToMarks() {
        val days = (9..15).toList()
        val row = com.aistudio.khmercalendar.data.ScheduleImageParser.RosterRow(
            "1 EMP101 STAFF ONE Team Lead",
            days.zip(listOf("H", "8", "8", "8", "AL", "8", "N")).toMap()
        )
        val start = Calendar.getInstance().apply { clear(); set(2026, Calendar.OCTOBER, 26) }
        val target = ImportTarget("2026-10", start.timeInMillis, 31, AppStore.emptyDayAssignments())
        var meanings: Map<String, String>? = null
        composeTestRule.setContent {
            MyApplicationTheme {
                CompositionLocalProvider(LocalAppLanguage provides AppLanguage.EN) {
                    com.aistudio.khmercalendar.ui.tabs.RosterSetupDialog(
                        setup = com.aistudio.khmercalendar.ui.tabs.RosterSetup(
                            target, com.aistudio.khmercalendar.data.ScheduleImageParser.Roster(days, listOf(row))
                        ),
                        shifts = AppStore.presetShifts(2),
                        lang = AppLanguage.EN,
                        savedRowKey = null,
                        savedMeanings = emptyMap(),
                        onDismiss = {},
                        onContinue = { _, m -> meanings = m }
                    )
                }
            }
        }
        composeTestRule.onNodeWithText("Continue").performClick()
        assertEquals(mapOf("8" to "day8", "AL" to "@AL", "H" to "@H", "N" to ""), meanings)
    }
}
