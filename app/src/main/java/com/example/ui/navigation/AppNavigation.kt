package com.example.ui.navigation

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.example.calendar.KhmerCalendarHelper
import com.example.calendar.KhmerDate
import com.example.ui.auth.ForgotScreenContent
import com.example.ui.auth.LoginScreenContent
import com.example.ui.auth.OTPScreenContent
import com.example.ui.auth.OnboardingScreenContent
import com.example.ui.auth.RegisterScreenContent
import com.example.ui.auth.SplashScreenContent
import com.example.ui.components.CustomBottomBar
import com.example.ui.tabs.AuspiciousTabContent
import com.example.ui.tabs.CalendarTabContent
import com.example.ui.tabs.DateConvertContent
import com.example.ui.tabs.HolidaysTabContent
import com.example.ui.tabs.HomeTabContent
import com.example.ui.tabs.ProfileSettingsContent
import com.example.ui.theme.NightBlack
import kotlinx.coroutines.delay

enum class AppScreen {
    SPLASH, ONBOARDING, LOGIN, REGISTER, FORGOT, OTP, MAIN_APP
}

enum class AppTab {
    HOME, CALENDAR, AUSPICIOUS, HOLIDAYS, CONVERT, PROFILE
}

@Composable
fun KhmerCalendarApp() {
    var screenState by remember { mutableStateOf(AppScreen.SPLASH) }
    var currentTab by remember { mutableStateOf(AppTab.HOME) }

    // State for interactive dates
    var calendarYear by remember { mutableStateOf(2026) }
    var calendarMonth by remember { mutableStateOf(5) } // May 2026 as standard reference
    var selectedDayIndex by remember { mutableStateOf(15) } // Default May 15 2026

    // Conversion calculator state
    var convertYear by remember { mutableStateOf("2026") }
    var convertMonth by remember { mutableStateOf("5") }
    var convertDay by remember { mutableStateOf("25") }
    var convertedKhDate by remember { mutableStateOf<KhmerDate?>(null) }

    // Auspicious filter state
    var selectedAuspiciousFilter by remember { mutableStateOf("ទាំងអស់") }

    // Holiday filter state
    var selectedHolidayFilter by remember { mutableStateOf("ទាំងអស់") }

    // Splash Timer
    LaunchedEffect(screenState) {
        if (screenState == AppScreen.SPLASH) {
            delay(1800)
            screenState = AppScreen.ONBOARDING
        }
    }

    // Initialize Conversion date on first load
    LaunchedEffect(Unit) {
        convertedKhDate = KhmerCalendarHelper.getKhmerDate(2026, 5, 25)
    }

    // Outer edge-to-edge container
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = NightBlack
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            Crossfade(targetState = screenState, label = "ScreenTransition") { currentScreen ->
                when (currentScreen) {
                    AppScreen.SPLASH -> SplashScreenContent()
                    AppScreen.ONBOARDING -> OnboardingScreenContent(
                        onContinue = { screenState = AppScreen.LOGIN }
                    )
                    AppScreen.LOGIN -> LoginScreenContent(
                        onSignIn = { screenState = AppScreen.MAIN_APP },
                        onSignUp = { screenState = AppScreen.REGISTER },
                        onForgot = { screenState = AppScreen.FORGOT }
                    )
                    AppScreen.REGISTER -> RegisterScreenContent(
                        onBack = { screenState = AppScreen.LOGIN },
                        onRegister = { screenState = AppScreen.OTP }
                    )
                    AppScreen.FORGOT -> ForgotScreenContent(
                        onBack = { screenState = AppScreen.LOGIN },
                        onSend = { screenState = AppScreen.LOGIN }
                    )
                    AppScreen.OTP -> OTPScreenContent(
                        onBack = { screenState = AppScreen.REGISTER },
                        onVerify = { screenState = AppScreen.MAIN_APP }
                    )
                    AppScreen.MAIN_APP -> MainAppLayout(
                        currentTab = currentTab,
                        onTabChange = { currentTab = it },
                        calendarYear = calendarYear,
                        calendarMonth = calendarMonth,
                        selectedDayIndex = selectedDayIndex,
                        onCalendarMonthChange = { year, month ->
                            calendarYear = year
                            calendarMonth = month
                            selectedDayIndex = 1 // reset to 1st of month
                        },
                        onDaySelect = { selectedDayIndex = it },
                        convertYear = convertYear,
                        convertMonth = convertMonth,
                        convertDay = convertDay,
                        convertedKhDate = convertedKhDate,
                        onConvertClick = { y, m, d ->
                            convertYear = y
                            convertMonth = m
                            convertDay = d
                            val yearVal = y.toIntOrNull() ?: 2026
                            val mVal = m.toIntOrNull() ?: 5
                            val dVal = d.toIntOrNull() ?: 25
                            convertedKhDate = KhmerCalendarHelper.getKhmerDate(yearVal, mVal, dVal)
                        },
                        selectedAuspiciousFilter = selectedAuspiciousFilter,
                        onAuspiciousFilterChange = { selectedAuspiciousFilter = it },
                        selectedHolidayFilter = selectedHolidayFilter,
                        onHolidayFilterChange = { selectedHolidayFilter = it },
                        onLogOut = {
                            screenState = AppScreen.LOGIN
                            currentTab = AppTab.HOME
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun MainAppLayout(
    currentTab: AppTab,
    onTabChange: (AppTab) -> Unit,
    calendarYear: Int,
    calendarMonth: Int,
    selectedDayIndex: Int,
    onCalendarMonthChange: (Int, Int) -> Unit,
    onDaySelect: (Int) -> Unit,
    convertYear: String,
    convertMonth: String,
    convertDay: String,
    convertedKhDate: KhmerDate?,
    onConvertClick: (String, String, String) -> Unit,
    selectedAuspiciousFilter: String,
    onAuspiciousFilterChange: (String) -> Unit,
    selectedHolidayFilter: String,
    onHolidayFilterChange: (String) -> Unit,
    onLogOut: () -> Unit
) {
    Scaffold(
        bottomBar = {
            CustomBottomBar(currentTab = currentTab, onTabSelect = onTabChange)
        },
        containerColor = NightBlack
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            Crossfade(targetState = currentTab, label = "TabTransition") { tab ->
                when (tab) {
                    AppTab.HOME -> HomeTabContent(onTabSelect = onTabChange)
                    AppTab.CALENDAR -> CalendarTabContent(
                        year = calendarYear,
                        month = calendarMonth,
                        selectedDay = selectedDayIndex,
                        onMonthChange = onCalendarMonthChange,
                        onDayChange = onDaySelect
                    )
                    AppTab.AUSPICIOUS -> AuspiciousTabContent(
                        selectedFilter = selectedAuspiciousFilter,
                        onFilterChange = onAuspiciousFilterChange
                    )
                    AppTab.HOLIDAYS -> HolidaysTabContent(
                        selectedFilter = selectedHolidayFilter,
                        onFilterChange = onHolidayFilterChange
                    )
                    AppTab.CONVERT -> DateConvertContent(
                        year = convertYear,
                        month = convertMonth,
                        day = convertDay,
                        convertedDate = convertedKhDate,
                        onConvert = onConvertClick
                    )
                    AppTab.PROFILE -> ProfileSettingsContent(
                        onLogOut = onLogOut
                    )
                }
            }
        }
    }
}
