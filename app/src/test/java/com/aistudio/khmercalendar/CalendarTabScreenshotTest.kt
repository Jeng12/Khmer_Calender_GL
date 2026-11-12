package com.aistudio.khmercalendar

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.background
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.aistudio.khmercalendar.core.AppLanguage
import com.aistudio.khmercalendar.core.LocalAppLanguage
import com.aistudio.khmercalendar.data.AppStore
import com.aistudio.khmercalendar.ui.tabs.CalendarTabContent
import com.aistudio.khmercalendar.ui.theme.DarkAppColors
import com.aistudio.khmercalendar.ui.theme.LightAppColors
import com.aistudio.khmercalendar.ui.theme.LocalAppColors
import com.aistudio.khmercalendar.ui.theme.MyApplicationTheme
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [36])
class CalendarTabScreenshotTest {

  @get:Rule val composeTestRule = createComposeRule()

  private val context: Context get() = ApplicationProvider.getApplicationContext()

  @Before
  fun seedLocalData() {
    // Keep the test offline: only local notes/holidays feed the screen.
    AppStore.setCloudSyncEnabled(context, false)
    AppStore.addNote(context, 2026, 5, 15, "Family dinner")
    AppStore.addNote(context, 2026, 5, 20, "Pay electricity bill")
    AppStore.addCustomHoliday(context, 5, 22, "ខួបកំណើត", "Birthday")
  }

  private fun setCalendar(language: AppLanguage, dark: Boolean, onMonthChange: (Int, Int) -> Unit = { _, _ -> }) {
    composeTestRule.setContent {
      var selectedDay by remember { mutableIntStateOf(15) }
      val colors = if (dark) DarkAppColors else LightAppColors
      MyApplicationTheme(darkTheme = dark) {
        CompositionLocalProvider(LocalAppLanguage provides language, LocalAppColors provides colors) {
          Box(Modifier.fillMaxSize().background(colors.bg)) {
            CalendarTabContent(
              year = 2026,
              month = 5,
              selectedDay = selectedDay,
              onMonthChange = onMonthChange,
              onDayChange = { selectedDay = it }
            )
          }
        }
      }
    }
  }

  @Test
  fun calendar_dark_khmer() {
    setCalendar(AppLanguage.KM, dark = true)
    composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/calendar_dark_km.png")
  }

  @Test
  fun calendar_light_english() {
    setCalendar(AppLanguage.EN, dark = false)
    composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/calendar_light_en.png")
  }

  @Test
  fun tapping_a_day_selects_it_and_agenda_lists_local_notes() {
    setCalendar(AppLanguage.EN, dark = true)
    composeTestRule.onNodeWithContentDescription("Wednesday 20 May", substring = true).performClick()
    composeTestRule.onNodeWithContentDescription("Wednesday 20 May", substring = true).assertIsSelected()
    // The agenda is a lazy list, so only assert on a row that is on screen.
    composeTestRule.onNodeWithText("Family dinner").assertExists()
  }

  @Test
  fun next_month_button_reports_the_following_month() {
    var requested: Pair<Int, Int>? = null
    setCalendar(AppLanguage.EN, dark = true) { y, m -> requested = y to m }
    composeTestRule.onNodeWithContentDescription("Next month").performClick()
    assertEquals(2026 to 6, requested)
  }
}
