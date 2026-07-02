package com.example

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.example.calendar.KhmerCalendarHelper
import com.example.ui.auth.ForgotScreenContent
import com.example.ui.auth.LoginScreenContent
import com.example.ui.auth.OTPScreenContent
import com.example.ui.auth.OnboardingScreenContent
import com.example.ui.auth.RegisterScreenContent
import com.example.ui.auth.SplashScreenContent
import com.example.ui.navigation.AppTab
import com.example.ui.navigation.MainAppLayout
import com.example.ui.theme.MyApplicationTheme
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [36])
class GreetingScreenshotTest {

  @get:Rule val composeTestRule = createComposeRule()

  @Test
  fun test_splash_screen() {
    composeTestRule.setContent {
      MyApplicationTheme {
        SplashScreenContent()
      }
    }
    composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/splash_screen.png")
  }

  @Test
  fun test_onboarding_screen() {
    composeTestRule.setContent {
      MyApplicationTheme {
        OnboardingScreenContent(onContinue = {})
      }
    }
    composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/onboarding_screen.png")
  }

  @Test
  fun test_login_screen() {
    composeTestRule.setContent {
      MyApplicationTheme {
        LoginScreenContent(onSignIn = {}, onSignUp = {}, onForgot = {})
      }
    }
    composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/login_screen.png")
  }

  @Test
  fun test_register_screen() {
    composeTestRule.setContent {
      MyApplicationTheme {
        RegisterScreenContent(onBack = {}, onRegister = {})
      }
    }
    composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/register_screen.png")
  }

  @Test
  fun test_forgot_screen() {
    composeTestRule.setContent {
      MyApplicationTheme {
        ForgotScreenContent(onBack = {}, onSend = {})
      }
    }
    composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/forgot_screen.png")
  }

  @Test
  fun test_otp_screen() {
    composeTestRule.setContent {
      MyApplicationTheme {
        OTPScreenContent(onBack = {}, onVerify = {})
      }
    }
    composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/otp_screen.png")
  }

  @Test
  fun test_main_app_layout_home() {
    composeTestRule.setContent {
      MyApplicationTheme {
        MainAppLayout(
          currentTab = AppTab.HOME,
          onTabChange = {},
          calendarYear = 2026,
          calendarMonth = 5,
          selectedDayIndex = 15,
          onCalendarMonthChange = { _, _ -> },
          onDaySelect = {},
          convertYear = "2026",
          convertMonth = "5",
          convertDay = "25",
          convertedKhDate = KhmerCalendarHelper.getKhmerDate(2026, 5, 25),
          onConvertClick = { _, _, _ -> },
          selectedHolidayFilter = "ទាំងអស់",
          onHolidayFilterChange = {},
          onLogOut = {}
        )
      }
    }
    composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/main_home.png")
  }

  @Test
  fun test_main_app_layout_calendar() {
    composeTestRule.setContent {
      MyApplicationTheme {
        MainAppLayout(
          currentTab = AppTab.CALENDAR,
          onTabChange = {},
          calendarYear = 2026,
          calendarMonth = 5,
          selectedDayIndex = 15,
          onCalendarMonthChange = { _, _ -> },
          onDaySelect = {},
          convertYear = "2026",
          convertMonth = "5",
          convertDay = "25",
          convertedKhDate = KhmerCalendarHelper.getKhmerDate(2026, 5, 25),
          onConvertClick = { _, _, _ -> },
          selectedHolidayFilter = "ទាំងអស់",
          onHolidayFilterChange = {},
          onLogOut = {}
        )
      }
    }
    composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/main_calendar.png")
  }

  @Test
  fun test_main_app_layout_holidays() {
    composeTestRule.setContent {
      MyApplicationTheme {
        MainAppLayout(
          currentTab = AppTab.HOLIDAYS,
          onTabChange = {},
          calendarYear = 2026,
          calendarMonth = 5,
          selectedDayIndex = 15,
          onCalendarMonthChange = { _, _ -> },
          onDaySelect = {},
          convertYear = "2026",
          convertMonth = "5",
          convertDay = "25",
          convertedKhDate = KhmerCalendarHelper.getKhmerDate(2026, 5, 25),
          onConvertClick = { _, _, _ -> },
          selectedHolidayFilter = "ទាំងអស់",
          onHolidayFilterChange = {},
          onLogOut = {}
        )
      }
    }
    composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/main_holidays.png")
  }

  @Test
  fun test_main_app_layout_convert() {
    composeTestRule.setContent {
      MyApplicationTheme {
        MainAppLayout(
          currentTab = AppTab.CONVERT,
          onTabChange = {},
          calendarYear = 2026,
          calendarMonth = 5,
          selectedDayIndex = 15,
          onCalendarMonthChange = { _, _ -> },
          onDaySelect = {},
          convertYear = "2026",
          convertMonth = "5",
          convertDay = "25",
          convertedKhDate = KhmerCalendarHelper.getKhmerDate(2026, 5, 25),
          onConvertClick = { _, _, _ -> },
          selectedHolidayFilter = "ទាំងអស់",
          onHolidayFilterChange = {},
          onLogOut = {}
        )
      }
    }
    composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/main_convert.png")
  }

  @Test
  fun test_main_app_layout_profile() {
    composeTestRule.setContent {
      MyApplicationTheme {
        MainAppLayout(
          currentTab = AppTab.PROFILE,
          onTabChange = {},
          calendarYear = 2026,
          calendarMonth = 5,
          selectedDayIndex = 15,
          onCalendarMonthChange = { _, _ -> },
          onDaySelect = {},
          convertYear = "2026",
          convertMonth = "5",
          convertDay = "25",
          convertedKhDate = KhmerCalendarHelper.getKhmerDate(2026, 5, 25),
          onConvertClick = { _, _, _ -> },
          selectedHolidayFilter = "ទាំងអស់",
          onHolidayFilterChange = {},
          onLogOut = {}
        )
      }
    }
    composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/main_profile.png")
  }
}
