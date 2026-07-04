package com.example.ui.navigation

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.Manifest
import android.app.AlarmManager
import android.app.PendingIntent
import android.app.TimePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.lifecycle.lifecycleScope
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import com.example.calendar.*
import com.example.core.*
import com.example.alarm.*
import com.example.data.*
import com.example.ui.theme.*
import com.example.ui.components.*
import com.example.ui.navigation.*
import com.example.ui.auth.*
import com.example.ui.tabs.*

enum class AppScreen {
    SPLASH, LOGIN, REGISTER, FORGOT, MAIN_APP
}

enum class AppTab {
    HOME, CALENDAR, HOLIDAYS, CONVERT, SCHEDULE, PROFILE
}

@Composable
fun KhmerCalendarApp() {
    var screenState by remember { mutableStateOf(AppScreen.SPLASH) }
    var currentTab by remember { mutableStateOf(AppTab.CALENDAR) }
    var showCloudSyncDisclosure by remember { mutableStateOf(false) }
    var authInProgress by remember { mutableStateOf(false) }

    // App-wide language, persisted across launches. Defaults to Khmer.
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val langPrefs = remember { context.getSharedPreferences("khmer_calendar_prefs", android.content.Context.MODE_PRIVATE) }
    var authSession by remember { mutableStateOf(AuthStore.currentSession(context)) }
    var appLanguage by remember {
        mutableStateOf(
            if (langPrefs.getString("app_lang", "km") == "en") AppLanguage.EN else AppLanguage.KM
        )
    }
    var isDarkMode by remember { mutableStateOf(langPrefs.getBoolean("dark_mode", true)) }

    // Today's real Gregorian date, used to open the calendar focused on the current day
    val today = remember { java.util.Calendar.getInstance() }

    // State for interactive dates — initialised to the current day
    var calendarYear by remember { mutableIntStateOf(today.get(java.util.Calendar.YEAR)) }
    var calendarMonth by remember { mutableIntStateOf(today.get(java.util.Calendar.MONTH) + 1) }
    var selectedDayIndex by remember { mutableIntStateOf(today.get(java.util.Calendar.DAY_OF_MONTH)) }

    // Conversion calculator state
    var convertYear by remember { mutableStateOf("2026") }
    var convertMonth by remember { mutableStateOf("5") }
    var convertDay by remember { mutableStateOf("25") }
    var convertedKhDate by remember { mutableStateOf<KhmerDate?>(null) }

    // Holiday filter state
    var selectedHolidayFilter by remember { mutableStateOf("ទាំងអស់") }

    LaunchedEffect(authSession) {
        AuthStore.setInMemorySession(authSession)
        CalendarApiRepository.setAuthSession(authSession)
    }

    fun enterMainApp(session: AuthStore.Session) {
        authSession = session
        val memberSince = session.createdAt?.take(4)?.toIntOrNull() ?: 2024
        langPrefs.edit()
            .putString("user_name", session.displayName)
            .putInt("member_since", memberSince)
            .putBoolean("logged_out", false)
            .apply()
        screenState = AppScreen.MAIN_APP
        currentTab = AppTab.CALENDAR
    }

    // Splash Timer — require a saved local account/session before opening data screens.
    LaunchedEffect(screenState) {
        if (screenState == AppScreen.SPLASH) {
            delay(1800)
            screenState = if (authSession != null) AppScreen.MAIN_APP else AppScreen.LOGIN
        }
    }

    LaunchedEffect(screenState) {
        if (screenState == AppScreen.MAIN_APP &&
            !langPrefs.getBoolean("cloud_sync_disclosure_seen", false)
        ) {
            showCloudSyncDisclosure = true
        }
    }

    // Initialize Conversion date on first load
    LaunchedEffect(Unit) {
        convertedKhDate = KhmerCalendarHelper.getKhmerDate(2026, 5, 25)
        CalendarApiRepository.convertDate(2026, 5, 25)
            .onSuccess { convertedKhDate = it }
    }

    // Refresh home-screen widgets every time the app starts, so notes/events stay current.
    LaunchedEffect(Unit) {
        com.example.widget.WidgetPrefs.refresh(context)
    }

    // One-time migration of the old single repeating template into per-month
    // schedules, then keep the work-schedule reminder window topped up.
    LaunchedEffect(Unit) {
        com.example.data.AppStore.migrateLegacyTemplate(context)
        com.example.alarm.WorkScheduleScheduler.sync(context)
    }

    LaunchedEffect(screenState, authSession) {
        if (screenState == AppScreen.MAIN_APP &&
            authSession != null &&
            langPrefs.getBoolean("cloud_sync_disclosure_seen", false)
        ) {
            // Push any local-only changes first, then pull the full work schedule
            // from the DB so the ScheduleTab always shows up-to-date data.
            SyncRepository.syncPending(context)
            SyncRepository.pullWorkScheduleFromRemote(context)
        }
    }

    // Outer edge-to-edge container
    MyApplicationTheme(darkTheme = isDarkMode) {
        CompositionLocalProvider(
            LocalAppLanguage provides appLanguage,
            LocalAppColors provides if (isDarkMode) DarkAppColors else LightAppColors
        ) {
            val C = LocalAppColors.current
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = C.bg
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .navigationBarsPadding()
                ) {
                    Crossfade(
                        targetState = screenState,
                        animationSpec = tween(durationMillis = 320, easing = FastOutSlowInEasing),
                        label = "ScreenTransition"
                    ) { currentScreen ->
                when (currentScreen) {
                    AppScreen.SPLASH -> SplashScreenContent()
                    AppScreen.LOGIN -> LoginScreenContent(
                        onSignIn = {},
                        onSignUp = { screenState = AppScreen.REGISTER },
                        onForgot = { screenState = AppScreen.FORGOT },
                        isSubmitting = authInProgress,
                        onSubmit = { email, password ->
                            if (!authInProgress) {
                                authInProgress = true
                                scope.launch {
                                    when (val result = AuthStore.signIn(context, email, password)) {
                                        is AuthStore.AuthResult.Success -> enterMainApp(result.session)
                                        is AuthStore.AuthResult.Error -> Toast.makeText(context, result.message, Toast.LENGTH_SHORT).show()
                                    }
                                    authInProgress = false
                                }
                            }
                        }
                    )
                    AppScreen.REGISTER -> RegisterScreenContent(
                        onBack = { screenState = AppScreen.LOGIN },
                        onRegister = {},
                        isSubmitting = authInProgress,
                        onSubmit = { firstName, lastName, email, password ->
                            if (!authInProgress) {
                                authInProgress = true
                                scope.launch {
                                    when (val result = AuthStore.register(context, firstName, lastName, email, password)) {
                                        is AuthStore.AuthResult.Success -> enterMainApp(result.session)
                                        is AuthStore.AuthResult.Error -> Toast.makeText(context, result.message, Toast.LENGTH_SHORT).show()
                                    }
                                    authInProgress = false
                                }
                            }
                        }
                    )
                    AppScreen.FORGOT -> ForgotScreenContent(
                        onBack = { screenState = AppScreen.LOGIN },
                        onSend = {
                            Toast.makeText(context, "Password reset is local-only in this build. Create a new account if needed.", Toast.LENGTH_LONG).show()
                            screenState = AppScreen.LOGIN
                        }
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
                        onGoToToday = {
                            val cal = java.util.Calendar.getInstance()
                            calendarYear = cal.get(java.util.Calendar.YEAR)
                            calendarMonth = cal.get(java.util.Calendar.MONTH) + 1
                            selectedDayIndex = cal.get(java.util.Calendar.DAY_OF_MONTH)
                        },
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
                            scope.launch {
                                CalendarApiRepository.convertDate(yearVal, mVal, dVal)
                                    .onSuccess { convertedKhDate = it }
                            }
                        },
                        selectedHolidayFilter = selectedHolidayFilter,
                        onHolidayFilterChange = { selectedHolidayFilter = it },
                        onLogOut = {
                            AuthStore.signOut(context)
                            authSession = null
                            CalendarApiRepository.setAuthSession(null)
                            screenState = AppScreen.LOGIN
                            currentTab = AppTab.HOME
                        },
                        isDarkMode = isDarkMode,
                        onDarkModeToggle = { enabled -> isDarkMode = enabled; langPrefs.edit().putBoolean("dark_mode", enabled).apply() },
                        appLanguage = appLanguage,
                        onLanguageChange = { lang ->
                            appLanguage = lang
                            langPrefs.edit()
                                .putString("app_lang", if (lang == AppLanguage.EN) "en" else "km")
                                .apply()
                        }
                    )
                }
            }
            if (showCloudSyncDisclosure) {
                AlertDialog(
                    onDismissRequest = {},
                    title = { Text("Database sync", color = C.text) },
                    text = {
                        Text(
                            "This app can sync notes, reminders, work schedules, and custom holidays with api-calender-sigma.vercel.app. You can turn sync off now or later in Profile.",
                            color = C.subText,
                            fontSize = 12.sp,
                            lineHeight = 17.sp
                        )
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                AppStore.setCloudSyncEnabled(context, true)
                                langPrefs.edit().putBoolean("cloud_sync_disclosure_seen", true).apply()
                                showCloudSyncDisclosure = false
                                scope.launch { SyncRepository.syncPending(context) }
                            }
                        ) {
                            Text("Keep sync on", color = TraditionalGold, fontWeight = FontWeight.Bold)
                        }
                    },
                    dismissButton = {
                        TextButton(
                            onClick = {
                                AppStore.setCloudSyncEnabled(context, false)
                                langPrefs.edit().putBoolean("cloud_sync_disclosure_seen", true).apply()
                                showCloudSyncDisclosure = false
                            }
                        ) {
                            Text("Turn off", color = C.subText)
                        }
                    },
                    containerColor = C.surface,
                    shape = RoundedCornerShape(16.dp)
                )
            }
        }
    }
    }
    }
}


/* ─────────────────────────────────────────────────────────────
   MAIN APPLICATION CONTAINER & NAVIGATION
───────────────────────────────────────────────────────────── */

@Composable
fun MainAppLayout(
    currentTab: AppTab,
    onTabChange: (AppTab) -> Unit,
    calendarYear: Int,
    calendarMonth: Int,
    selectedDayIndex: Int,
    onCalendarMonthChange: (Int, Int) -> Unit,
    onDaySelect: (Int) -> Unit,
    onGoToToday: () -> Unit = {},
    convertYear: String,
    convertMonth: String,
    convertDay: String,
    convertedKhDate: KhmerDate?,
    onConvertClick: (String, String, String) -> Unit,
    selectedHolidayFilter: String,
    onHolidayFilterChange: (String) -> Unit,
    onLogOut: () -> Unit,
    isDarkMode: Boolean = true,
    onDarkModeToggle: (Boolean) -> Unit = {},
    appLanguage: AppLanguage = AppLanguage.KM,
    onLanguageChange: (AppLanguage) -> Unit = {}
) {
    val C = LocalAppColors.current
    Scaffold(
        bottomBar = {
            CustomBottomBar(currentTab = currentTab, onTabSelect = onTabChange)
        },
        containerColor = C.bg
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            Crossfade(
                targetState = currentTab,
                animationSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing),
                label = "TabTransition"
            ) { tab ->
                when (tab) {
                    AppTab.HOME -> HomeTabContent(onTabSelect = onTabChange)
                    AppTab.CALENDAR -> CalendarTabContent(
                        year = calendarYear,
                        month = calendarMonth,
                        selectedDay = selectedDayIndex,
                        onMonthChange = onCalendarMonthChange,
                        onDayChange = onDaySelect,
                        onGoToToday = onGoToToday
                    )
                    AppTab.HOLIDAYS -> HolidaysTabContent(
                        displayedYear = calendarYear,
                        selectedFilter = selectedHolidayFilter,
                        onFilterChange = onHolidayFilterChange
                    )
                    AppTab.CONVERT -> SalaryCalculatorTabContent()
                    AppTab.SCHEDULE -> ScheduleTabContent()
                    AppTab.PROFILE -> ProfileSettingsContent(
                        onLogOut = onLogOut,
                        isDarkMode = isDarkMode,
                        onDarkModeToggle = onDarkModeToggle,
                        appLanguage = appLanguage,
                        onLanguageChange = onLanguageChange,
                        onOpenSchedule = { onTabChange(AppTab.SCHEDULE) }
                    )
                }
            }
        }
    }
}
