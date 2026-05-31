package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
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
import androidx.lifecycle.lifecycleScope
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.input.pointer.pointerInput

// Beautiful Khmer Heritage Color Palette
val NightBlack = Color(0xFF0D0A0F)      // #0D0A0F
val DeepAmethyst = Color(0xFF140F1A)    // #120E16 in Jetpack
val PlumSurface = Color(0xFF1D1726)     // #1A1520 in Jetpack
val PlumCard = Color(0xFF261E30)        // #221C2A in Jetpack
val DeepBorder = Color(0xFF322640)      // #2E2538 in Jetpack
val DeepMuted = Color(0xFF453556)       // #3D3349 in Jetpack
val SandText = Color(0xFFF5EDD8)        // #F5EDD8 (Cream)
val GoldSubText = Color(0xFFC7B38E)     // #9B8E7A
val DimColor = Color(0xFFA090B8)        // improved contrast on dark backgrounds
val TraditionalGold = Color(0xFFC8973A) // #C8973A
val LightGold = Color(0xFFE8B84B)       // #E8B84B
val CrimsonHoliday = Color(0xFFC0392B)  // #C0392B
val LotusPink = Color(0xFFE8768A)       // #E8768A
val JadeGreen = Color(0xFF4DAF7C)       // #4DAF7C
val MoonWheat = Color(0xFFF2E8C6)       // #F2E8C6
val SkyBlue = Color(0xFF7BA7BC)         // #7BA7BC

// Hoisted gradient brushes – allocated once, not on every recomposition
val GoldBorderGradient = Brush.linearGradient(listOf(TraditionalGold, Color(0xFFFFF0C0), TraditionalGold))
val GoldLotusBrush     = Brush.linearGradient(listOf(TraditionalGold, LotusPink))
val AccentBarBrush     = Brush.horizontalGradient(listOf(CrimsonHoliday, TraditionalGold, LotusPink))

// ── Themeable color scheme (positional destructuring maps to legacy names) ──────
// val (NightBlack, DeepAmethyst, PlumSurface, PlumCard, DeepBorder, DeepMuted,
//      SandText, GoldSubText, DimColor) = LocalAppColors.current
data class AppColors(
    val bg:      Color,  // NightBlack
    val deepBg:  Color,  // DeepAmethyst
    val surface: Color,  // PlumSurface
    val card:    Color,  // PlumCard
    val border:  Color,  // DeepBorder
    val muted:   Color,  // DeepMuted
    val text:    Color,  // SandText
    val subText: Color,  // GoldSubText
    val dim:     Color,  // DimColor
)

val DarkAppColors = AppColors(
    bg = NightBlack, deepBg = DeepAmethyst, surface = PlumSurface, card = PlumCard,
    border = DeepBorder, muted = DeepMuted, text = SandText, subText = GoldSubText, dim = DimColor
)

val LightAppColors = AppColors(
    bg      = Color(0xFFFAF5EE),
    deepBg  = Color(0xFFF0E8DC),
    surface = Color(0xFFFFFFFF),
    card    = Color(0xFFF5EFE6),
    border  = Color(0xFFE2D5C3),
    muted   = Color(0xFFCFC0A8),
    text    = Color(0xFF2C1F0E),
    subText = Color(0xFF7A5F3A),
    dim     = Color(0xFF9A8068),
)

val LocalAppColors = compositionLocalOf { DarkAppColors }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Warm up the milestone table off the main thread to avoid first-frame jank
        lifecycleScope.launch(Dispatchers.Default) {
            KhmerCalendarHelper.warmUp()
        }
        setContent {
            MyApplicationTheme {
                KhmerCalendarApp()
            }
        }
    }
}

enum class AppScreen {
    SPLASH, ONBOARDING, LOGIN, REGISTER, FORGOT, OTP, MAIN_APP
}

enum class AppTab {
    HOME, CALENDAR, AUSPICIOUS, HOLIDAYS, CONVERT, PROFILE
}

@Composable
fun KhmerCalendarApp() {
    var screenState by remember { mutableStateOf(AppScreen.SPLASH) }
    var currentTab by remember { mutableStateOf(AppTab.CALENDAR) }

    // App-wide language, persisted across launches. Defaults to Khmer.
    val context = LocalContext.current
    val langPrefs = remember { context.getSharedPreferences("khmer_calendar_prefs", android.content.Context.MODE_PRIVATE) }
    var appLanguage by remember {
        mutableStateOf(
            if (langPrefs.getString("app_lang", "km") == "en") AppLanguage.EN else AppLanguage.KM
        )
    }
    var isDarkMode by remember { mutableStateOf(langPrefs.getBoolean("dark_mode", true)) }

    // Today's real Gregorian date, used to open the calendar focused on the current day
    val today = remember { java.util.Calendar.getInstance() }

    // State for interactive dates — initialised to the current day
    var calendarYear by remember { mutableStateOf(today.get(java.util.Calendar.YEAR)) }
    var calendarMonth by remember { mutableStateOf(today.get(java.util.Calendar.MONTH) + 1) }
    var selectedDayIndex by remember { mutableStateOf(today.get(java.util.Calendar.DAY_OF_MONTH)) }

    // Conversion calculator state
    var convertYear by remember { mutableStateOf("2026") }
    var convertMonth by remember { mutableStateOf("5") }
    var convertDay by remember { mutableStateOf("25") }
    var convertedKhDate by remember { mutableStateOf<KhmerDate?>(null) }

    // Auspicious filter state
    var selectedAuspiciousFilter by remember { mutableStateOf("ទាំងអស់") }

    // Holiday filter state
    var selectedHolidayFilter by remember { mutableStateOf("ទាំងអស់") }

    // Splash Timer — go straight to the main app (login flow skipped)
    LaunchedEffect(screenState) {
        if (screenState == AppScreen.SPLASH) {
            delay(1800)
            screenState = AppScreen.MAIN_APP
        }
    }

    // Initialize Conversion date on first load
    LaunchedEffect(Unit) {
        convertedKhDate = KhmerCalendarHelper.getKhmerDate(2026, 5, 25)
    }

    // Outer edge-to-edge container
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
                        },
                        selectedAuspiciousFilter = selectedAuspiciousFilter,
                        onAuspiciousFilterChange = { selectedAuspiciousFilter = it },
                        selectedHolidayFilter = selectedHolidayFilter,
                        onHolidayFilterChange = { selectedHolidayFilter = it },
                        onLogOut = {
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
        }
    }
    }
}

/* ─────────────────────────────────────────────────────────────
   AUTHENTICATION SCREENS (Splash, Onboarding, Login, etc.)
───────────────────────────────────────────────────────────── */

@Composable
fun SplashScreenContent() {
    val (NightBlack, DeepAmethyst, PlumSurface, PlumCard, DeepBorder, DeepMuted, SandText, GoldSubText, DimColor) = LocalAppColors.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(Color(0xFF2A1F3A), NightBlack),
                    radius = 900f
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        // Aesthetic corner ornaments
        Text("❁", modifier = Modifier.align(Alignment.TopStart).padding(24.dp), color = TraditionalGold.copy(alpha = 0.3f), fontSize = 18.sp)
        Text("❁", modifier = Modifier.align(Alignment.TopEnd).padding(24.dp), color = TraditionalGold.copy(alpha = 0.3f), fontSize = 18.sp)
        Text("❁", modifier = Modifier.align(Alignment.BottomStart).padding(24.dp), color = TraditionalGold.copy(alpha = 0.3f), fontSize = 18.sp)
        Text("❁", modifier = Modifier.align(Alignment.BottomEnd).padding(24.dp), color = TraditionalGold.copy(alpha = 0.3f), fontSize = 18.sp)

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(110.dp)
                    .border(1.dp, TraditionalGold.copy(alpha = 0.3f), CircleShape)
                    .padding(8.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(88.dp)
                        .background(
                            Brush.linearGradient(listOf(TraditionalGold.copy(alpha = 0.2f), Color.Transparent)),
                            CircleShape
                        )
                        .border(1.dp, TraditionalGold.copy(alpha = 0.6f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text("🌙", fontSize = 42.sp)
                }
            }
            Spacer(modifier = Modifier.height(18.dp))
            Text(
                text = tr("ប្រតិទិនខ្មែរ", "Khmer Calendar"),
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = MoonWheat,
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "KHMER LUNAR CALENDAR",
                fontSize = 11.sp,
                color = TraditionalGold,
                letterSpacing = 2.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(32.dp))
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                color = TraditionalGold,
                strokeWidth = 2.dp
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = tr("កំពុងផ្ទុក…", "Loading…"),
                fontSize = 10.sp,
                color = DimColor
            )
        }
    }
}

@Composable
fun OnboardingScreenContent(onContinue: () -> Unit) {
    val (NightBlack, DeepAmethyst, PlumSurface, PlumCard, DeepBorder, DeepMuted, SandText, GoldSubText, DimColor) = LocalAppColors.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(NightBlack)
    ) {
        // Golden accent line
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .background(AccentBarBrush)
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(130.dp)
                    .background(
                        Brush.radialGradient(listOf(TraditionalGold.copy(alpha = 0.25f), CrimsonHoliday.copy(alpha = 0.1f))),
                        RoundedCornerShape(28.dp)
                    )
                    .border(1.dp, TraditionalGold.copy(alpha = 0.4f), RoundedCornerShape(28.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text("🌕", fontSize = 56.sp)
            }
            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = tr("ប្រតិទិនចន្ទគតិ", "Lunar Calendar"),
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = MoonWheat
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "KHMER LUNAR CALENDAR",
                fontSize = 12.sp,
                color = TraditionalGold,
                letterSpacing = 1.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Track traditional Khmer lunar dates, auspicious days for blessings, Buddhist ceremonies, and public holidays instantly.",
                textAlign = TextAlign.Center,
                fontSize = 12.sp,
                color = GoldSubText,
                lineHeight = 18.sp,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Dots indicator
            Row(
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.padding(bottom = 24.dp)
            ) {
                Box(modifier = Modifier.size(24.dp, 6.dp).clip(CircleShape).background(TraditionalGold))
                Spacer(modifier = Modifier.width(6.dp))
                Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(DeepMuted))
                Spacer(modifier = Modifier.width(6.dp))
                Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(DeepMuted))
            }

            Button(
                onClick = onContinue,
                colors = ButtonDefaults.buttonColors(containerColor = TraditionalGold),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("onboarding_continue_button"),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(tr("បន្តទៅមុខទៀត ->", "Continue ->"), color = NightBlack, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = tr("រំលង (Skip)", "Skip"),
                color = DimColor,
                fontSize = 11.sp,
                modifier = Modifier.clickable { onContinue() }
            )
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
fun LoginScreenContent(
    onSignIn: () -> Unit,
    onSignUp: () -> Unit,
    onForgot: () -> Unit
) {
    val (NightBlack, DeepAmethyst, PlumSurface, PlumCard, DeepBorder, DeepMuted, SandText, GoldSubText, DimColor) = LocalAppColors.current
    val lang = LocalAppLanguage.current
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var emailError by remember { mutableStateOf<String?>(null) }
    var passwordError by remember { mutableStateOf<String?>(null) }

    fun validateAndSignIn() {
        val emailRegex = Regex("^[^@]+@[^@]+\\.[^@]+")
        emailError = when {
            email.isBlank() -> tr(lang, "សូមបញ្ចូលអ៊ីមែល (Email required)", "Email required")
            !emailRegex.matches(email) -> tr(lang, "អ៊ីមែលមិនត្រឹមត្រូវ (Invalid email)", "Invalid email")
            else -> null
        }
        passwordError = when {
            password.isBlank() -> tr(lang, "សូមបញ្ចូលពាក្យសម្ងាត់ (Password required)", "Password required")
            password.length < 6 -> tr(lang, "ពាក្យសម្ងាត់ត្រូវការ ៦ តួ+ (Min 6 characters)", "Min 6 characters")
            else -> null
        }
        if (emailError == null && passwordError == null) onSignIn()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(NightBlack)
            .verticalScroll(rememberScrollState())
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .background(Brush.horizontalGradient(listOf(CrimsonHoliday, TraditionalGold)))
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
        ) {
            Spacer(modifier = Modifier.height(20.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(bottom = 24.dp)
            ) {
                Text("🌙", fontSize = 32.sp)
                Column {
                    Text(tr("ចូលគណនី (Sign In)", "Sign In"), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MoonWheat)
                    Text(tr("ចូលទៅកាន់ប្រតិទិនរបស់អ្នក", "Sign in to your traditional calendar"), fontSize = 11.sp, color = DimColor)
                }
            }

            // Input Fields
            Text(tr("អ៊ីមែល / EMAIL", "EMAIL"), fontSize = 9.sp, color = DimColor, letterSpacing = 1.sp)
            Spacer(modifier = Modifier.height(6.dp))
            OutlinedTextField(
                value = email,
                onValueChange = { email = it; emailError = null },
                textStyle = LocalTextStyle.current.copy(color = SandText, fontSize = 13.sp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("email_input"),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedContainerColor = PlumSurface,
                    focusedContainerColor = PlumSurface,
                    unfocusedBorderColor = if (emailError != null) CrimsonHoliday else DeepBorder,
                    focusedBorderColor = if (emailError != null) CrimsonHoliday else TraditionalGold
                )
            )
            if (emailError != null) {
                Text(emailError!!, fontSize = 9.sp, color = CrimsonHoliday, modifier = Modifier.padding(top = 2.dp))
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(tr("ពាក្យសម្ងាត់ / PASSWORD", "PASSWORD"), fontSize = 9.sp, color = DimColor, letterSpacing = 1.sp)
            Spacer(modifier = Modifier.height(6.dp))
            OutlinedTextField(
                value = password,
                onValueChange = { password = it; passwordError = null },
                textStyle = LocalTextStyle.current.copy(color = SandText, fontSize = 13.sp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("password_input"),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedContainerColor = PlumSurface,
                    focusedContainerColor = PlumSurface,
                    unfocusedBorderColor = if (passwordError != null) CrimsonHoliday else DeepBorder,
                    focusedBorderColor = if (passwordError != null) CrimsonHoliday else TraditionalGold
                )
            )
            if (passwordError != null) {
                Text(passwordError!!, fontSize = 9.sp, color = CrimsonHoliday, modifier = Modifier.padding(top = 2.dp))
            }

            Spacer(modifier = Modifier.height(12.dp))
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.CenterEnd
            ) {
                Text(
                    text = tr("ភ្លេចពាក្យសម្ងាត់?", "Forgot password?"),
                    color = TraditionalGold,
                    fontSize = 11.sp,
                    modifier = Modifier.clickable { onForgot() }
                )
            }

            Spacer(modifier = Modifier.height(28.dp))

            Button(
                onClick = { validateAndSignIn() },
                colors = ButtonDefaults.buttonColors(containerColor = TraditionalGold),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("login_button"),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(tr("ចូលគណនី (Sign In)", "Sign In"), color = NightBlack, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }

            Spacer(modifier = Modifier.height(16.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(modifier = Modifier.weight(1f).height(1.dp).background(DeepBorder))
                Text(tr("ឬ បន្តជាមួយ", "Or continue with"), color = DimColor, fontSize = 10.sp)
                Box(modifier = Modifier.weight(1f).height(1.dp).background(DeepBorder))
            }

            Spacer(modifier = Modifier.height(16.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                listOf("Google", "Apple", "Facebook").forEach { provider ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .background(PlumSurface, RoundedCornerShape(10.dp))
                            .border(1.dp, DeepBorder, RoundedCornerShape(10.dp))
                            .clickable { onSignIn() }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = provider,
                            color = SandText,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(30.dp))
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(tr("មិនទាន់មានគណនី?", "No account yet?"), color = GoldSubText, fontSize = 11.sp)
                    Text(
                        text = tr("ចុះឈ្មោះនៅទីនេះ", "Sign up here"),
                        color = TraditionalGold,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        modifier = Modifier.clickable { onSignUp() }
                    )
                }
            }
        }
    }
}

@Composable
fun RegisterScreenContent(
    onBack: () -> Unit,
    onRegister: () -> Unit
) {
    val (NightBlack, DeepAmethyst, PlumSurface, PlumCard, DeepBorder, DeepMuted, SandText, GoldSubText, DimColor) = LocalAppColors.current
    val lang = LocalAppLanguage.current
    var fn by remember { mutableStateOf("") }
    var ln by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var emailError by remember { mutableStateOf<String?>(null) }
    var passwordError by remember { mutableStateOf<String?>(null) }
    var nameError by remember { mutableStateOf<String?>(null) }

    fun validateAndRegister() {
        val emailRegex = Regex("^[^@]+@[^@]+\\.[^@]+")
        nameError = if (fn.isBlank() || ln.isBlank()) tr(lang, "សូមបញ្ចូលឈ្មោះ (Name required)", "Name required") else null
        emailError = when {
            email.isBlank() -> tr(lang, "សូមបញ្ចូលអ៊ីមែល (Email required)", "Email required")
            !emailRegex.matches(email) -> tr(lang, "អ៊ីមែលមិនត្រឹមត្រូវ (Invalid email)", "Invalid email")
            else -> null
        }
        passwordError = when {
            password.isBlank() -> tr(lang, "សូមបញ្ចូលពាក្យសម្ងាត់ (Password required)", "Password required")
            password.length < 6 -> tr(lang, "ពាក្យសម្ងាត់ត្រូវការ ៦ តួ+ (Min 6 characters)", "Min 6 characters")
            else -> null
        }
        if (nameError == null && emailError == null && passwordError == null) onRegister()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(NightBlack)
            .padding(24.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.clickable { onBack() }
        ) {
            Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = TraditionalGold, modifier = Modifier.size(16.dp))
            Text(tr("ត្រឡប់ក្រោយ (Back)", "Back"), color = TraditionalGold, fontSize = 11.sp)
        }

        Spacer(modifier = Modifier.height(20.dp))
        Text(tr("ចុះឈ្មោះថ្មី", "Create Account"), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MoonWheat)
        Text(tr("បង្កើតប្រវត្តិរូបប្រតិទិនរបស់អ្នក", "Create your traditional calendar profile"), fontSize = 11.sp, color = DimColor)

        Spacer(modifier = Modifier.height(20.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(modifier = Modifier.weight(1f)) {
                Text(tr("នាមត្រកូល (Last Name)", "Last Name"), fontSize = 9.sp, color = DimColor)
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedTextField(
                    value = ln,
                    onValueChange = { ln = it; nameError = null },
                    textStyle = TextStyle(color = SandText, fontSize = 12.sp),
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedContainerColor = PlumSurface,
                        unfocusedBorderColor = if (nameError != null) CrimsonHoliday else DeepBorder
                    )
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(tr("នាមខ្លួន (First Name)", "First Name"), fontSize = 9.sp, color = DimColor)
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedTextField(
                    value = fn,
                    onValueChange = { fn = it; nameError = null },
                    textStyle = TextStyle(color = SandText, fontSize = 12.sp),
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedContainerColor = PlumSurface,
                        unfocusedBorderColor = if (nameError != null) CrimsonHoliday else DeepBorder
                    )
                )
            }
        }
        if (nameError != null) {
            Text(nameError!!, fontSize = 9.sp, color = CrimsonHoliday, modifier = Modifier.padding(top = 2.dp))
        }

        Spacer(modifier = Modifier.height(14.dp))
        Text(tr("អ៊ីមែល (Email)", "Email"), fontSize = 9.sp, color = DimColor)
        Spacer(modifier = Modifier.height(4.dp))
        OutlinedTextField(
            value = email,
            onValueChange = { email = it; emailError = null },
            textStyle = TextStyle(color = SandText, fontSize = 12.sp),
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedContainerColor = PlumSurface,
                unfocusedBorderColor = if (emailError != null) CrimsonHoliday else DeepBorder
            )
        )
        if (emailError != null) {
            Text(emailError!!, fontSize = 9.sp, color = CrimsonHoliday, modifier = Modifier.padding(top = 2.dp))
        }

        Spacer(modifier = Modifier.height(14.dp))
        Text(tr("ពាក្យសម្ងាត់ (Password)", "Password"), fontSize = 9.sp, color = DimColor)
        Spacer(modifier = Modifier.height(4.dp))
        OutlinedTextField(
            value = password,
            onValueChange = { password = it; passwordError = null },
            textStyle = TextStyle(color = SandText, fontSize = 12.sp),
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedContainerColor = PlumSurface,
                unfocusedBorderColor = if (passwordError != null) CrimsonHoliday else DeepBorder
            )
        )
        if (passwordError != null) {
            Text(passwordError!!, fontSize = 9.sp, color = CrimsonHoliday, modifier = Modifier.padding(top = 2.dp))
        }

        Spacer(modifier = Modifier.height(20.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .size(14.dp)
                    .border(1.dp, TraditionalGold, RoundedCornerShape(3.dp))
                    .background(TraditionalGold.copy(0.2f))
            )
            Text(
                text = tr("ខ្ញុំយល់ព្រមតាម លក្ខខណ្ឌ និង គោលការណ៍ របស់កម្មវិធី។", "I agree to the app's Terms & Policies."),
                color = GoldSubText,
                fontSize = 10.sp,
                lineHeight = 15.sp
            )
        }

        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = { validateAndRegister() },
            colors = ButtonDefaults.buttonColors(containerColor = TraditionalGold),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(tr("ចុះឈ្មោះភ្លាមៗ", "Register Now"), color = NightBlack, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun ForgotScreenContent(onBack: () -> Unit, onSend: () -> Unit) {
    val (NightBlack, DeepAmethyst, PlumSurface, PlumCard, DeepBorder, DeepMuted, SandText, GoldSubText, DimColor) = LocalAppColors.current
    var email by remember { mutableStateOf("chanda@example.com") }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(NightBlack)
            .padding(24.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.clickable { onBack() }
        ) {
            Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = TraditionalGold, modifier = Modifier.size(16.dp))
            Text(tr("ត្រឡប់ក្រោយ", "Back"), color = TraditionalGold, fontSize = 11.sp)
        }

        Spacer(modifier = Modifier.height(30.dp))
        Card(
            colors = CardDefaults.cardColors(containerColor = PlumSurface),
            border = BorderStroke(1.dp, TraditionalGold.copy(0.3f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("🔑", fontSize = 36.sp)
                Spacer(modifier = Modifier.height(10.dp))
                Text(tr("ភ្លេចពាក្យសម្ងាត់?", "Forgot password?"), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MoonWheat)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = tr("បញ្ចូលអ៊ីមែលរបស់អ្នកដើម្បីទទួលបានតំណភ្ជាប់ប្តូរលេខសម្ងាត់ថ្មី។", "Enter your email to receive a password reset link."),
                    fontSize = 10.sp,
                    color = GoldSubText,
                    textAlign = TextAlign.Center,
                    lineHeight = 16.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
        Text(tr("អ៊ីមែលរបស់អ្នក", "Your Email"), fontSize = 9.sp, color = DimColor)
        Spacer(modifier = Modifier.height(4.dp))
        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            textStyle = TextStyle(color = SandText, fontSize = 12.sp),
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(unfocusedContainerColor = PlumSurface, unfocusedBorderColor = DeepBorder)
        )

        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = onSend,
            colors = ButtonDefaults.buttonColors(containerColor = TraditionalGold),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(tr("ផ្ញើតំណភ្ជាប់", "Send Link"), color = NightBlack, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun OTPScreenContent(onBack: () -> Unit, onVerify: () -> Unit) {
    val (NightBlack, DeepAmethyst, PlumSurface, PlumCard, DeepBorder, DeepMuted, SandText, GoldSubText, DimColor) = LocalAppColors.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(NightBlack)
            .padding(24.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.clickable { onBack() }
        ) {
            Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = TraditionalGold, modifier = Modifier.size(16.dp))
            Text(tr("ត្រឡប់ក្រោយ", "Back"), color = TraditionalGold, fontSize = 11.sp)
        }

        Spacer(modifier = Modifier.height(30.dp))
        Text(tr("បញ្ជាក់លេខកូដសម្ងាត់ OTP", "Verify OTP Code"), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MoonWheat)
        Text(tr("យើងបានផ្ញើលេខកូដសម្ងាត់ប្រាំមួយខ្ទង់ទៅសារទូរសព្ទរបស់អ្នក។", "We sent a 6-digit code to your phone."), fontSize = 11.sp, color = GoldSubText)

        Spacer(modifier = Modifier.height(28.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            val otpList = listOf("8", "4", "2", "_", "_", "_")
            otpList.forEach { char ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .background(if (char == "_") PlumSurface else TraditionalGold.copy(0.15f), RoundedCornerShape(10.dp))
                        .border(1.dp, if (char == "_") DeepBorder else TraditionalGold, RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = char,
                        color = if (char == "_") DimColor else TraditionalGold,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(28.dp))
        Button(
            onClick = onVerify,
            colors = ButtonDefaults.buttonColors(containerColor = TraditionalGold),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(tr("ផ្ទៀងផ្ទាត់ និងចូល", "Verify & Sign In"), color = NightBlack, fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(18.dp))
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Text(
                text = tr("មិនទទួលបានលេខកូដ? ផ្ញើម្តងទៀត (42s)", "Didn't get the code? Resend (42s)"),
                color = TraditionalGold,
                fontSize = 11.sp
            )
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
    selectedAuspiciousFilter: String,
    onAuspiciousFilterChange: (String) -> Unit,
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
            Crossfade(targetState = currentTab, label = "TabTransition") { tab ->
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
                    AppTab.AUSPICIOUS -> AuspiciousTabContent(
                        calendarYear = calendarYear,
                        calendarMonth = calendarMonth,
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
                        onLogOut = onLogOut,
                        isDarkMode = isDarkMode,
                        onDarkModeToggle = onDarkModeToggle,
                        appLanguage = appLanguage,
                        onLanguageChange = onLanguageChange
                    )
                }
            }
        }
    }
}

@Composable
fun CustomBottomBar(
    currentTab: AppTab,
    onTabSelect: (AppTab) -> Unit
) {
    val (NightBlack, DeepAmethyst, PlumSurface, PlumCard, DeepBorder, DeepMuted, SandText, GoldSubText, DimColor) = LocalAppColors.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(NightBlack)
            .border(BorderStroke(1.dp, DeepBorder))
            .padding(vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Home Tab
            BottomBarItem(
                emoji = "🏠",
                label = tr("ទំព័រដើម", "Home"),
                subLabel = "Home",
                isSelected = currentTab == AppTab.HOME,
                onClick = { onTabSelect(AppTab.HOME) }
            )
            // Calendar Tab
            BottomBarItem(
                emoji = "📅",
                label = tr("ប្រតិទិន", "Calendar"),
                subLabel = "Calendar",
                isSelected = currentTab == AppTab.CALENDAR,
                onClick = { onTabSelect(AppTab.CALENDAR) }
            )
            // Holidays Tab
            BottomBarItem(
                emoji = "🎉",
                label = tr("ថ្ងៃបុណ្យ", "Holidays"),
                subLabel = "Holidays",
                isSelected = currentTab == AppTab.HOLIDAYS,
                onClick = { onTabSelect(AppTab.HOLIDAYS) }
            )
            // Convert Tab
            BottomBarItem(
                emoji = "🔄",
                label = tr("បំលែង", "Convert"),
                subLabel = "Convert",
                isSelected = currentTab == AppTab.CONVERT,
                onClick = { onTabSelect(AppTab.CONVERT) }
            )
            // Profile Tab
            BottomBarItem(
                emoji = "👤",
                label = tr("ប្រវត្តិរូប", "Profile"),
                subLabel = "Profile",
                isSelected = currentTab == AppTab.PROFILE,
                onClick = { onTabSelect(AppTab.PROFILE) }
            )
        }
    }
}

@Composable
fun BottomBarItem(
    emoji: String,
    label: String,
    subLabel: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val (NightBlack, DeepAmethyst, PlumSurface, PlumCard, DeepBorder, DeepMuted, SandText, GoldSubText, DimColor) = LocalAppColors.current
    Column(
        modifier = Modifier
            .clickable(
                indication = ripple(color = TraditionalGold.copy(alpha = 0.2f)),
                interactionSource = remember { MutableInteractionSource() },
                onClickLabel = subLabel,
            ) { onClick() }
            .padding(horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(text = emoji, fontSize = 20.sp, modifier = Modifier.alpha(if (isSelected) 1f else 0.5f))
        Text(
            text = label,
            fontSize = 13.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            color = if (isSelected) TraditionalGold else GoldSubText.copy(0.6f)
        )
        if (isSelected) {
            Box(
                modifier = Modifier
                    .size(12.dp, 2.dp)
                    .clip(CircleShape)
                    .background(TraditionalGold)
            )
        }
    }
}

/* ─────────────────────────────────────────────────────────────
   TAB CONTENTS
───────────────────────────────────────────────────────────── */

// 1. HOME TAB CONTAINER
@Composable
fun HomeTabContent(onTabSelect: (AppTab) -> Unit) {
    val (NightBlack, DeepAmethyst, PlumSurface, PlumCard, DeepBorder, DeepMuted, SandText, GoldSubText, DimColor) = LocalAppColors.current
    val lang = LocalAppLanguage.current
    val calendar = remember { java.util.Calendar.getInstance() }
    val currentYear = calendar.get(java.util.Calendar.YEAR)
    val currentMonth = calendar.get(java.util.Calendar.MONTH) + 1
    val currentDay = calendar.get(java.util.Calendar.DAY_OF_MONTH)
    val currentKhmerInfo = remember(currentYear, currentMonth, currentDay) {
        KhmerCalendarHelper.getKhmerDate(currentYear, currentMonth, currentDay)
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(NightBlack)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // App header with Khmer lunar elements
        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .background(
                            Brush.linearGradient(listOf(TraditionalGold, CrimsonHoliday)),
                            RoundedCornerShape(12.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text("🌙", fontSize = 24.sp)
                }
                Column {
                    Text(tr("ប្រតិទិនចន្ទគតិខ្មែរ", "Khmer Lunar Calendar"), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MoonWheat)
                    Text("KHMER LUNAR CALENDAR · OFFICIAL v2", fontSize = 9.sp, color = TraditionalGold, letterSpacing = 1.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        // Today Hero card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = PlumSurface),
                border = BorderStroke(1.dp, TraditionalGold.copy(alpha = 0.35f)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    // Floating giant background moon info
                    Text(
                        text = currentKhmerInfo.moonEmoji,
                        fontSize = 80.sp,
                        modifier = Modifier.align(Alignment.TopEnd).alpha(0.08f)
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column {
                                Text(tr("TODAY · ថ្ងៃនេះ", "TODAY"), fontSize = 9.sp, color = GoldSubText, letterSpacing = 1.sp)
                                Text(
                                    text = if (lang == AppLanguage.EN)
                                        "${currentKhmerInfo.dayOfWeekEn}, ${currentKhmerInfo.day} ${gregMonth(lang, currentMonth - 1)} $currentYear"
                                    else
                                        "ថ្ងៃ${currentKhmerInfo.dayOfWeek} ទី${num(lang, currentKhmerInfo.day)} ${gregMonth(lang, currentMonth - 1)} ${num(lang, currentYear)}",
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MoonWheat
                                )
                            }
                            Text(text = currentKhmerInfo.moonEmoji, fontSize = 32.sp)
                        }

                        // Divider line
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(TraditionalGold.copy(0.25f))
                        )

                        Text(
                            text = "${lunarDayLabel(lang, currentKhmerInfo)} ${lunarMonth(lang, currentKhmerInfo.lunarMonthName)} ${zodiac(lang, currentKhmerInfo.zodiac)}",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = TraditionalGold
                        )

                        // Tags row
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(
                                modifier = Modifier
                                    .background(TraditionalGold.copy(0.18f), RoundedCornerShape(20.dp))
                                    .border(1.dp, TraditionalGold.copy(0.4f), RoundedCornerShape(20.dp))
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text(tr("ព.ស. ${num(lang, currentKhmerInfo.BE)}", "BE ${currentKhmerInfo.BE}"), fontSize = 9.sp, color = TraditionalGold, fontWeight = FontWeight.SemiBold)
                            }
                            Box(
                                modifier = Modifier
                                    .background(LotusPink.copy(0.12f), RoundedCornerShape(20.dp))
                                    .border(1.dp, LotusPink.copy(0.3f), RoundedCornerShape(20.dp))
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = if (lang == AppLanguage.EN)
                                        "${lunarMonth(lang, currentKhmerInfo.lunarMonthName)} ${lunarDayLabel(lang, currentKhmerInfo)}"
                                    else
                                        "ខែ${currentKhmerInfo.lunarMonthName} ${lunarDayLabel(lang, currentKhmerInfo)}",
                                    fontSize = 9.sp, color = LotusPink, fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }
        }

        // Quick action grids
        item {
            Text(tr("សេវាកម្មរហ័ស (QUICK SERVICES)", "QUICK SERVICES"), fontSize = 10.sp, color = DimColor, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                QuickGridCard(
                    modifier = Modifier.weight(1f),
                    emoji = "📅",
                    title = tr("ប្រតិទិន", "Calendar"),
                    subtitle = "Calendar",
                    accentColor = TraditionalGold,
                    onClick = { onTabSelect(AppTab.CALENDAR) }
                )
                QuickGridCard(
                    modifier = Modifier.weight(1f),
                    emoji = "🌿",
                    title = tr("ថ្ងៃមង្គល", "Auspicious"),
                    subtitle = "Auspicious",
                    accentColor = JadeGreen,
                    onClick = { onTabSelect(AppTab.AUSPICIOUS) }
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                QuickGridCard(
                    modifier = Modifier.weight(1f),
                    emoji = "🎊",
                    title = tr("ថ្ងៃបុណ្យ", "Holidays"),
                    subtitle = "Holidays",
                    accentColor = LotusPink,
                    onClick = { onTabSelect(AppTab.HOLIDAYS) }
                )
                QuickGridCard(
                    modifier = Modifier.weight(1f),
                    emoji = "🔄",
                    title = tr("បំលែង", "Convert"),
                    subtitle = "Convert",
                    accentColor = SkyBlue,
                    onClick = { onTabSelect(AppTab.CONVERT) }
                )
            }
        }

        // Upcoming national events in Cambodia
        item {
            Text(tr("ព្រឹត្តិការណ៍ខាងមុខ (UPCOMING EVENTS)", "UPCOMING EVENTS"), fontSize = 10.sp, color = DimColor, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            Spacer(modifier = Modifier.height(10.dp))

            val events = if (lang == AppLanguage.EN) listOf(
                Pair("Visak Bochea", "3 days left · 🌕 15 Waxing"),
                Pair("Pchum Ben Festival", "98 days left · 🌑 15 Waning"),
                Pair("Royal Ploughing Ceremony", "14 days left · 4 Waning")
            ) else listOf(
                Pair("ថ្ងៃបុណ្យវិសាខបូជា (Visak Bochea)", "៣ ថ្ងៃទៀត · 🌕 ១៥ កើត"),
                Pair("បុណ្យភ្ជុំបិណ្ឌ (Pchum Ben Festival)", "៩៨ ថ្ងៃទៀត · 🌑 ១៥ រោច"),
                Pair("ព្រះរាជពិធីច្រត់ព្រះនង្គ័ល (Royal Ploughing)", "១៤ ថ្ងៃទៀត · ៤ រោច")
            )

            events.forEach { event ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                        .background(PlumCard, RoundedCornerShape(12.dp))
                        .border(1.dp, DeepBorder, RoundedCornerShape(12.dp))
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(TraditionalGold.copy(0.12f), RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("🔔", fontSize = 16.sp)
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(event.first, fontSize = 12.sp, color = SandText, fontWeight = FontWeight.Bold)
                        Text(event.second, fontSize = 9.sp, color = TraditionalGold)
                    }
                    Box(
                        modifier = Modifier
                            .background(TraditionalGold.copy(0.12f), RoundedCornerShape(12.dp))
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(tr("រំលឹក", "Remind"), fontSize = 9.sp, color = TraditionalGold, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun QuickGridCard(
    modifier: Modifier = Modifier,
    emoji: String,
    title: String,
    subtitle: String,
    accentColor: Color,
    onClick: () -> Unit
) {
    val (NightBlack, DeepAmethyst, PlumSurface, PlumCard, DeepBorder, DeepMuted, SandText, GoldSubText, DimColor) = LocalAppColors.current
    Box(
        modifier = modifier
            .background(PlumCard, RoundedCornerShape(12.dp))
            .border(BorderStroke(1.dp, accentColor.copy(alpha = 0.25f)), RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(emoji, fontSize = 24.sp)
            Column {
                Text(title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = SandText)
                Text(subtitle, fontSize = 9.sp, color = DimColor)
            }
        }
    }
}

// 2. CALENDAR TAB CONTAINER
@Composable
fun CalendarTabContent(
    year: Int,
    month: Int,
    selectedDay: Int,
    onMonthChange: (Int, Int) -> Unit,
    onDayChange: (Int) -> Unit,
    onGoToToday: () -> Unit = {}
) {
    val (NightBlack, DeepAmethyst, PlumSurface, PlumCard, DeepBorder, DeepMuted, SandText, GoldSubText, DimColor) = LocalAppColors.current
    val lang = LocalAppLanguage.current
    var swipeOffset by remember { mutableStateOf(0f) }
    // Focus on the current day each time the Calendar tab is opened
    LaunchedEffect(Unit) { onGoToToday() }

    // The actual current Gregorian date, used to highlight "today" in the grid
    val todayCal = remember { java.util.Calendar.getInstance() }
    val todayYear = todayCal.get(java.util.Calendar.YEAR)
    val todayMonth = todayCal.get(java.util.Calendar.MONTH) + 1
    val todayDay = todayCal.get(java.util.Calendar.DAY_OF_MONTH)
    // Memoize: recompute only when year/month changes
    val daysList = remember(year, month) { KhmerCalendarHelper.getGregorianMonthDays(year, month) }
    // Starting day of week for index 1 (Sunday=0). Offset +2 matches getKhmerDate's
    // weekday math so the grid columns line up with the real Gregorian weekdays.
    val startDayOfWeekSerial = KhmerCalendarHelper.getSerialDay(year, month, 1)
    val startOffset = ((startDayOfWeekSerial + 2) % 7 + 7) % 7 // index representing start day of week (Sunday=0, etc.)

    val selectedKhmerDate = daysList.getOrNull(selectedDay - 1) ?: daysList.firstOrNull() ?: KhmerCalendarHelper.getKhmerDate(year, month, selectedDay)

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(NightBlack)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Switcher Banner
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(PlumSurface, RoundedCornerShape(12.dp))
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(onClick = {
                    var prevMonth = month - 1
                    var prevYear = year
                    if (prevMonth < 1) {
                        prevMonth = 12
                        prevYear -= 1
                    }
                    onMonthChange(prevYear, prevMonth)
                }) {
                    Text("‹", fontSize = 24.sp, color = TraditionalGold, fontWeight = FontWeight.Bold)
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = if (lang == AppLanguage.EN)
                            "${selectedKhmerDate.dayOfWeekEn}, ${gregMonth(lang, month - 1)} $selectedDay, $year"
                        else
                            "ថ្ងៃទី ${num(lang, selectedDay)} ខែ${gregMonth(lang, month - 1)} ឆ្នាំ${num(lang, year)}",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = MoonWheat
                    )
                    Text(
                        text = tr(
                            "ព.ស. ${num(lang, selectedKhmerDate.BE)} · ${zodiac(lang, selectedKhmerDate.zodiac)}",
                            "BE ${selectedKhmerDate.BE} · ${zodiac(lang, selectedKhmerDate.zodiac)}"
                        ),
                        fontSize = 11.sp,
                        color = TraditionalGold,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    val isTodaySelected = year == todayYear && month == todayMonth && selectedDay == todayDay
                    Box(
                        modifier = Modifier
                            .background(TraditionalGold.copy(0.15f), RoundedCornerShape(20.dp))
                            .border(1.dp, TraditionalGold.copy(0.4f), RoundedCornerShape(20.dp))
                            .clickable { onGoToToday() }
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = if (lang == AppLanguage.EN)
                                if (isTodaySelected) "Today · ${selectedKhmerDate.dayOfWeekEn}"
                                else selectedKhmerDate.dayOfWeekEn
                            else
                                if (isTodaySelected) "ថ្ងៃនេះ ${selectedKhmerDate.dayOfWeek}"
                                else "ថ្ងៃ${selectedKhmerDate.dayOfWeek}",
                            fontSize = 11.sp, color = TraditionalGold, fontWeight = FontWeight.Bold
                        )
                    }
                }

                IconButton(onClick = {
                    var nextMonth = month + 1
                    var nextYear = year
                    if (nextMonth > 12) {
                        nextMonth = 1
                        nextYear += 1
                    }
                    onMonthChange(nextYear, nextMonth)
                }) {
                    Text("›", fontSize = 24.sp, color = TraditionalGold, fontWeight = FontWeight.Bold)
                }
            }
        }

        // Days labels
        item {
            Row(modifier = Modifier.fillMaxWidth()) {
                val weekLabels = weekdayLabels(lang)
                weekLabels.forEachIndexed { idx, label ->
                    Text(
                        text = label,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (idx == 0 || idx == 6) CrimsonHoliday else GoldSubText
                    )
                }
            }
        }

        // Days Grid Calendar
        item {
            // We use simple nested layouts instead of complex lazy grid inside lazy column to prevent crashes
            val totalCells = startOffset + daysList.size
            val rowsCount = (totalCells + 6) / 7

            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.pointerInput(year, month) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            if (swipeOffset > 80f) {
                                var prevMonth = month - 1; var prevYear = year
                                if (prevMonth < 1) { prevMonth = 12; prevYear -= 1 }
                                onMonthChange(prevYear, prevMonth)
                            } else if (swipeOffset < -80f) {
                                var nextMonth = month + 1; var nextYear = year
                                if (nextMonth > 12) { nextMonth = 1; nextYear += 1 }
                                onMonthChange(nextYear, nextMonth)
                            }
                            swipeOffset = 0f
                        },
                        onHorizontalDrag = { _, dragAmount -> swipeOffset += dragAmount }
                    )
                }
            ) {
                for (row in 0 until rowsCount) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        for (col in 0..6) {
                            val cellIdx = row * 7 + col
                            val dayNumber = cellIdx - startOffset + 1

                            if (cellIdx < startOffset || dayNumber > daysList.size) {
                                Box(modifier = Modifier.weight(1f))
                            } else {
                                val dateInfo = daysList[dayNumber - 1]
                                val isSelected = dayNumber == selectedDay
                                val isToday = year == todayYear && month == todayMonth && dayNumber == todayDay
                                val isHoliday = dateInfo.holiday != null
                                val isWeekend = col == 0 || col == 6

                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .aspectRatio(0.8f)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(
                                            when {
                                                isSelected -> TraditionalGold.copy(0.2f)
                                                isToday -> LotusPink.copy(0.12f)
                                                else -> Color.Transparent
                                            }
                                        )
                                        .border(
                                            1.5.dp,
                                            when {
                                                isSelected -> TraditionalGold
                                                isToday -> LotusPink.copy(0.7f)
                                                else -> Color.Transparent
                                            },
                                            RoundedCornerShape(10.dp)
                                        )
                                        .clickable { onDayChange(dayNumber) }
                                        .padding(4.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        // Render small indicator for major moon phase
                                        val hasMoonIndicator =
                                            (dateInfo.isWaxing && dateInfo.lunarDayVal in listOf(1, 8, 15)) ||
                                            (!dateInfo.isWaxing && dateInfo.lunarDayVal == 8)
                                        if (hasMoonIndicator) {
                                            Text(dateInfo.moonEmoji, fontSize = 10.sp, lineHeight = 12.sp)
                                        }

                                        Text(
                                            text = dayNumber.toString(),
                                            fontSize = 20.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                                            color = when {
                                                isSelected -> TraditionalGold
                                                isHoliday -> LotusPink
                                                isWeekend -> CrimsonHoliday
                                                else -> SandText
                                            }
                                        )
                                        Text(
                                            text = lunarDayLabel(lang, dateInfo),
                                            fontSize = 8.sp,
                                            lineHeight = 9.sp,
                                            maxLines = 1,
                                            softWrap = false,
                                            color = if (isSelected) TraditionalGold.copy(0.8f) else DimColor
                                        )
                                        // Small dot if auspicious or has custom holiday
                                        if (dateInfo.isAuspicious) {
                                            Box(modifier = Modifier.size(4.dp).clip(CircleShape).background(JadeGreen))
                                        } else if (isHoliday) {
                                            Box(modifier = Modifier.size(4.dp).clip(CircleShape).background(LotusPink))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Selected date detail box
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = PlumCard),
                border = BorderStroke(1.dp, TraditionalGold.copy(0.3f)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = tr("ព័ត៌មានលម្អិតថ្ងៃទី ${num(lang, selectedDay)}", "Details for Day $selectedDay"),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = TraditionalGold
                        )
                        Text(selectedKhmerDate.moonEmoji, fontSize = 18.sp)
                    }

                    Text(
                        text = "${tr("ថ្ងៃគ្រីស្ដ", "Gregorian Date")}: ${selectedKhmerDate.dayOfWeekEn}, ${selectedKhmerDate.day} ${gregMonth(AppLanguage.EN, selectedKhmerDate.month - 1)} ${selectedKhmerDate.year}",
                        fontSize = 10.sp,
                        color = GoldSubText
                    )

                    Text(
                        text = "${tr("ថ្ងៃចន្ទគតិ", "Lunar Date")}: ${lunarDayLabel(lang, selectedKhmerDate)} ${lunarMonth(lang, selectedKhmerDate.lunarMonthName)}",
                        fontSize = 14.sp,
                        color = SandText,
                        fontWeight = FontWeight.Bold
                    )

                    // Holiday / Auspicious Tag alerts
                    if (selectedKhmerDate.holiday != null) {
                        Box(
                            modifier = Modifier
                                .background(LotusPink.copy(0.12f), RoundedCornerShape(8.dp))
                                .border(1.dp, LotusPink, RoundedCornerShape(8.dp))
                                .padding(8.dp)
                        ) {
                            Text(
                                text = "🎉 ${tr("ថ្ងៃបុណ្យជាតិ", "Public Holiday")}: ${localizeDual(lang, selectedKhmerDate.holiday!!)}",
                                fontSize = 10.sp,
                                color = LotusPink,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    if (selectedKhmerDate.isAuspicious) {
                        Box(
                            modifier = Modifier
                                .background(JadeGreen.copy(0.12f), RoundedCornerShape(8.dp))
                                .border(1.dp, JadeGreen, RoundedCornerShape(8.dp))
                                .padding(8.dp)
                        ) {
                            Text(
                                text = tr(
                                    "🌿 ថ្ងៃមង្គល: ល្អសម្រាប់ ${selectedKhmerDate.auspiciousType ?: "ការងារទូទៅ"}",
                                    "🌿 Auspicious: good for ${localizeDual(lang, selectedKhmerDate.auspiciousType ?: "General work")}"
                                ),
                                fontSize = 10.sp,
                                color = JadeGreen,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    // Legend of color representations
                    Row(
                        modifier = Modifier.padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        listOf(
                            Pair(JadeGreen, tr("ថ្ងៃមង្គល", "Auspicious")),
                            Pair(LotusPink, tr("ថ្ងៃបុណ្យ", "Holiday")),
                            Pair(TraditionalGold, tr("ថ្ងៃសកម្ម", "Selected")),
                            Pair(CrimsonHoliday, tr("ថ្ងៃឈប់", "Weekend"))
                        ).forEach { legend ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(legend.first))
                                Text(legend.second, fontSize = 8.sp, color = DimColor)
                            }
                        }
                    }
                }
            }
        }
    }
}

// 3. AUSPICIOUS DAYS TAB CONTAINER
@Composable
fun AuspiciousTabContent(
    calendarYear: Int,
    calendarMonth: Int,
    selectedFilter: String,
    onFilterChange: (String) -> Unit
) {
    val (NightBlack, DeepAmethyst, PlumSurface, PlumCard, DeepBorder, DeepMuted, SandText, GoldSubText, DimColor) = LocalAppColors.current
    val lang = LocalAppLanguage.current
    // Stable Khmer filter keys (used for state + matching); display labels localized.
    val filters = listOf("ទាំងអស់", "ពិធីមង្គលការ", "ឡើងផ្ទះថ្មី", "បើកអាជីវកម្ម", "ធ្វើដំណើរ")
    val filterLabel: (String) -> String = { key ->
        when (key) {
            "ទាំងអស់" -> tr(lang, "ទាំងអស់", "All")
            "ពិធីមង្គលការ" -> tr(lang, "ពិធីមង្គលការ", "Wedding")
            "ឡើងផ្ទះថ្មី" -> tr(lang, "ឡើងផ្ទះថ្មី", "Housewarming")
            "បើកអាជីវកម្ម" -> tr(lang, "បើកអាជីវកម្ម", "Business")
            "ធ្វើដំណើរ" -> tr(lang, "ធ្វើដំណើរ", "Travel")
            else -> key
        }
    }

    // Pair of display labels + the raw KhmerDate (needed for Gemini)
    data class AuspiciousItem(val gregLabel: String, val lunarLabel: String, val typeLabel: String, val khmerDate: KhmerDate)

    val auspiciousDaysList = remember(calendarYear, calendarMonth, lang) {
        KhmerCalendarHelper.getGregorianMonthDays(calendarYear, calendarMonth)
            .filter { it.isAuspicious }
            .map { d ->
                val dayName = if (lang == AppLanguage.EN) d.dayOfWeekEn else "ថ្ងៃ${d.dayOfWeek}"
                AuspiciousItem(
                    gregLabel  = "$dayName ${num(lang, d.day)} ${gregMonth(lang, d.month - 1)}",
                    lunarLabel = "${lunarDayLabel(lang, d)} ${lunarMonth(lang, d.lunarMonthName)}",
                    typeLabel  = localizeDual(lang, d.auspiciousType ?: tr(lang, "ថ្ងៃល្អ", "Good day")),
                    khmerDate  = d
                )
            }
    }

    val filteredList = remember(auspiciousDaysList, selectedFilter) {
        if (selectedFilter == "ទាំងអស់") auspiciousDaysList
        else auspiciousDaysList.filter {
            it.khmerDate.auspiciousType?.contains(selectedFilter.replace(" ថ្មី", "")) == true
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(NightBlack)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(tr("ថ្ងៃមង្គល (Auspicious Days)", "Auspicious Days"), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MoonWheat)
                    Text("Auspicious Days · ${num(lang, calendarYear)}", fontSize = 9.sp, color = JadeGreen)
                }
                Text("🌿", fontSize = 24.sp)
            }
        }

        // Horizontal filter chips
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                filters.forEach { tag ->
                    val isActive = tag == selectedFilter
                    Box(
                        modifier = Modifier
                            .background(if (isActive) JadeGreen else PlumSurface, RoundedCornerShape(20.dp))
                            .border(1.dp, if (isActive) JadeGreen else DeepBorder, RoundedCornerShape(20.dp))
                            .clickable { onFilterChange(tag) }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = filterLabel(tag),
                            fontSize = 10.sp,
                            color = if (isActive) NightBlack else SandText,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        if (filteredList.isEmpty()) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 40.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("📭", fontSize = 40.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(tr("គ្មានថ្ងៃមង្គលសម្រាប់ជម្រើសនេះទេ", "No auspicious days for this filter"), fontSize = 11.sp, color = DimColor)
                }
            }
        } else {
            items(filteredList, key = { it.gregLabel }) { item ->
                AuspiciousDayCard(item.gregLabel, item.lunarLabel, item.typeLabel, item.khmerDate)
            }
        }
    }
}

@Composable
private fun AuspiciousDayCard(
    gregLabel: String,
    lunarLabel: String,
    typeLabel: String,
    khmerDate: KhmerDate
) {
    val (NightBlack, DeepAmethyst, PlumSurface, PlumCard, DeepBorder, DeepMuted, SandText, GoldSubText, DimColor) = LocalAppColors.current
    val lang = LocalAppLanguage.current
    var explanation by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(PlumCard, RoundedCornerShape(12.dp))
            .border(1.dp, JadeGreen.copy(0.25f), RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(gregLabel, fontSize = 12.sp, color = SandText, fontWeight = FontWeight.Bold)
                Text(lunarLabel, fontSize = 10.sp, color = TraditionalGold)
            }
            Box(
                modifier = Modifier
                    .background(JadeGreen.copy(0.12f), RoundedCornerShape(8.dp))
                    .border(1.dp, JadeGreen.copy(0.4f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(typeLabel, fontSize = 9.sp, color = JadeGreen, fontWeight = FontWeight.Bold)
            }
        }

        // AI Explanation section
        if (explanation != null) {
            Text(
                text = explanation!!,
                fontSize = 10.sp,
                color = MoonWheat,
                lineHeight = 15.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(PlumSurface, RoundedCornerShape(8.dp))
                    .padding(8.dp)
            )
        }

        TextButton(
            onClick = {
                if (explanation == null && !isLoading) {
                    isLoading = true
                    scope.launch {
                        val result = GeminiRepository.explainAuspiciousDay(khmerDate, lang)
                        explanation = result.getOrElse { tr(lang, "មិនអាចភ្ជាប់ AI បានទេ", "Could not connect to AI") + " (${it.message})" }
                        isLoading = false
                    }
                }
            },
            enabled = !isLoading && explanation == null,
            modifier = Modifier.align(Alignment.End),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
        ) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(12.dp), color = JadeGreen, strokeWidth = 1.5.dp)
                Spacer(Modifier.width(4.dp))
                Text(tr("AI កំពុងព្យញ្ចដ…", "AI is thinking…"), fontSize = 9.sp, color = JadeGreen)
            } else if (explanation == null) {
                Text(tr("✨ AI ពន្យល់", "✨ AI Explain"), fontSize = 9.sp, color = JadeGreen)
            }
        }
    }
}

// 4. HOLIDAYS TAB CONTAINER
@Composable
fun HolidaysTabContent(
    selectedFilter: String,
    onFilterChange: (String) -> Unit
) {
    val (NightBlack, DeepAmethyst, PlumSurface, PlumCard, DeepBorder, DeepMuted, SandText, GoldSubText, DimColor) = LocalAppColors.current
    val lang = LocalAppLanguage.current
    val NATIONAL = "ជាតិ (National)"
    val BUDDHIST = "ព្រះពុទ្ធ (Buddhist)"
    // Stable Khmer filter keys; display labels localized.
    val filters = listOf("ទាំងអស់", NATIONAL, BUDDHIST)
    val typeLabel: (String) -> String = { key ->
        when (key) {
            "ទាំងអស់" -> tr(lang, "ទាំងអស់", "All")
            NATIONAL -> tr(lang, "ជាតិ (National)", "National")
            BUDDHIST -> tr(lang, "ព្រះពុទ្ធ (Buddhist)", "Buddhist")
            else -> key
        }
    }

    // Holiday(dateKm, dateEn, nameKm, nameEn, typeKey)
    data class Holiday(val dateKm: String, val dateEn: String, val nameKm: String, val nameEn: String, val type: String)
    val holidaysList = listOf(
        Holiday("០១ មករា", "01 Jan", "ទិវាឆ្នាំថ្មីអន្តរជាតិ · New Year's Day", "New Year's Day", NATIONAL),
        Holiday("០៧ មករា", "07 Jan", "ទិវាជ័យជម្នះលើរបបប្រល័យពូជសាសន៍", "Victory over Genocide Day", NATIONAL),
        Holiday("០៨ មីនា", "08 Mar", "ទិវាអន្តរជាតិរបស់ស្ត្រី · International Women's Day", "International Women's Day", NATIONAL),
        Holiday("១៤-១៦ មេសា", "14-16 Apr", "ចូលឆ្នាំថ្មីប្រពៃណីជាតិ · Khmer New Year", "Khmer New Year", NATIONAL),
        Holiday("០១ ឧសភា", "01 May", "ទិវាពលកម្មអន្តរជាតិ · International Labour Day", "International Labour Day", NATIONAL),
        Holiday("ទី១៥ ពិសាខ (ច)", "15th Visakha (waxing)", "បុណ្យវិសាខបូជា · Visak Bochea Day", "Visak Bochea Day", BUDDHIST),
        Holiday("០១ មិថុនា", "01 Jun", "ទិវាកុមារអន្តរជាតិ · International Children's Day", "International Children's Day", NATIONAL),
        Holiday("១៨ មិថុនា", "18 Jun", "ព្រះរាជពិធីបុណ្យចម្រើនព្រះជន្ម សម្ដេចម៉ែ", "Queen Mother's Birthday", NATIONAL),
        Holiday("ទី១-១៥ ភទ្របទ (ច)", "1-15 Phutrobot (waning)", "បុណ្យភ្ជុំបិណ្ឌ · Pchum Ben Festival", "Pchum Ben Festival", BUDDHIST),
        Holiday("២៤ កញ្ញា", "24 Sep", "ទិវារដ្ឋធម្មនុញ្ញ · Constitution Day", "Constitution Day", NATIONAL),
        Holiday("១៥ តុលា", "15 Oct", "ទិវាគោរពព្រះវិញ្ញាណក្ខន្ធ ព្រះបរមរតនកោដ្ឋ", "Commemoration Day of the King Father", NATIONAL),
        Holiday("ទី១៥ កត្តិក (ក)", "15th Kakdek (waxing)", "ព្រះរាជពិធីបុណ្យអុំទូក · Water Festival", "Water Festival", BUDDHIST),
        Holiday("ទី១៥ មាឃ (ក)", "15th Meak (waxing)", "បុណ្យមាឃបូជា · Meak Bochea Day", "Meak Bochea Day", BUDDHIST),
        Holiday("២៩ តុលា", "29 Oct", "ព្រះរាជពិធីគ្រងព្រះបរមរាជសម្បត្តិ ព្រះមហាក្សត្រ", "King's Coronation Day", NATIONAL),
        Holiday("០៩ វិច្ឆិកា", "09 Nov", "ទិវាបុណ្យឯករាជ្យជាតិ · Independence Day", "Independence Day", NATIONAL),
        Holiday("១០ ធ្នូ", "10 Dec", "ទិវាសិទ្ធិមនុស្ស · Human Rights Day", "Human Rights Day", NATIONAL)
    )

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(NightBlack)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Column {
                Text(tr("ថ្ងៃបុណ្យ (Cambodian Holidays)", "Cambodian Holidays"), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MoonWheat)
                Text("National & Buddhist Public Holidays in Cambodia", fontSize = 9.sp, color = LotusPink)
            }
        }

        // Chips
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                filters.forEach { tag ->
                    val isActive = tag == selectedFilter
                    Box(
                        modifier = Modifier
                            .background(if (isActive) LotusPink else PlumSurface, RoundedCornerShape(20.dp))
                            .border(1.dp, if (isActive) LotusPink else DeepBorder, RoundedCornerShape(20.dp))
                            .clickable { onFilterChange(tag) }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = typeLabel(tag),
                            fontSize = 10.sp,
                            color = if (isActive) NightBlack else SandText,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // Filter and render holidays
        val filteredHolidays = if (selectedFilter == "ទាំងអស់") holidaysList
            else holidaysList.filter { it.type == selectedFilter }

        items(filteredHolidays) { holiday ->
            val isBuddhist = holiday.type == BUDDHIST
            val accentColor = if (isBuddhist) TraditionalGold else LotusPink
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .background(PlumSurface, RoundedCornerShape(12.dp))
                    .border(1.dp, accentColor.copy(0.3f), RoundedCornerShape(12.dp))
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(accentColor.copy(0.12f), RoundedCornerShape(12.dp))
                        .border(1.dp, accentColor.copy(0.3f), RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(if (isBuddhist) "🪷" else "🏮", fontSize = 18.sp)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(if (lang == AppLanguage.EN) holiday.nameEn else holiday.nameKm, fontSize = 12.sp, color = SandText, fontWeight = FontWeight.Bold)
                    Text(typeLabel(holiday.type), fontSize = 9.sp, color = accentColor.copy(0.7f))
                    Text(if (lang == AppLanguage.EN) holiday.dateEn else holiday.dateKm, fontSize = 10.sp, color = TraditionalGold, fontWeight = FontWeight.Bold)
                }
                Box(
                    modifier = Modifier
                        .background(accentColor.copy(0.12f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(tr("ឈប់", "Off"), fontSize = 9.sp, color = accentColor, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// 5. DATE CONVERTER TAB CONTAINER
@Composable
fun DateConvertContent(
    year: String,
    month: String,
    day: String,
    convertedDate: KhmerDate?,
    onConvert: (String, String, String) -> Unit
) {
    val (NightBlack, DeepAmethyst, PlumSurface, PlumCard, DeepBorder, DeepMuted, SandText, GoldSubText, DimColor) = LocalAppColors.current
    val lang = LocalAppLanguage.current
    var inYear by remember { mutableStateOf(year) }
    var inMonth by remember { mutableStateOf(month) }
    var inDay by remember { mutableStateOf(day) }
    var inputError by remember { mutableStateOf<String?>(null) }

    fun validate(): Boolean {
        val y = inYear.toIntOrNull()
        val m = inMonth.toIntOrNull()
        val d = inDay.toIntOrNull()
        return when {
            y == null || m == null || d == null -> {
                inputError = tr(lang, "សូមបញ្ចូលតម្លៃជាលេខ (numbers only)", "Please enter numbers only")
                false
            }
            y < 2019 || y > 2036 -> {
                inputError = tr(lang, "ឆ្នាំ ២០១៩–២០៣៦ ប៉ុណ្ណោះ (Year 2019–2036 only)", "Year 2019–2036 only")
                false
            }
            m < 1 || m > 12 -> {
                inputError = tr(lang, "ខែត្រូវស្ថិតក្នុងចន្លោះ ១–១២ (Month 1–12)", "Month must be 1–12")
                false
            }
            d < 1 || d > 31 -> {
                inputError = tr(lang, "ថ្ងៃត្រូវស្ថិតក្នុងចន្លោះ ១–៣១ (Day 1–31)", "Day must be 1–31")
                false
            }
            else -> {
                inputError = null
                true
            }
        }
    }

    LaunchedEffect(year, month, day) {
        inYear = year
        inMonth = month
        inDay = day
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(NightBlack)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Header banner
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.horizontalGradient(listOf(SkyBlue.copy(0.18f), TraditionalGold.copy(0.1f))),
                        RoundedCornerShape(14.dp)
                    )
                    .border(1.dp, SkyBlue.copy(0.35f), RoundedCornerShape(14.dp))
                    .padding(14.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("🔄", fontSize = 28.sp)
                    Column {
                        Text(tr("បំលែងថ្ងៃខែ", "Date Converter"), fontSize = 17.sp, fontWeight = FontWeight.Bold, color = MoonWheat)
                        Text(tr("ពីគ្រីស្ដសករាជ → ចន្ទគតិខ្មែរ", "Gregorian → Khmer Lunar"), fontSize = 10.sp, color = SkyBlue)
                    }
                }
            }
        }

        // Input card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = PlumSurface),
                border = BorderStroke(1.dp, if (inputError != null) CrimsonHoliday.copy(0.6f) else DeepBorder),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Label row + Today button
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            tr("ថ្ងៃខែគ្រីស្ដសករាជ", "Gregorian Date"),
                            fontSize = 10.sp,
                            color = SkyBlue,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )
                        Box(
                            modifier = Modifier
                                .background(SkyBlue.copy(0.15f), RoundedCornerShape(20.dp))
                                .border(1.dp, SkyBlue.copy(0.4f), RoundedCornerShape(20.dp))
                                .clickable {
                                    val now = java.util.Calendar.getInstance()
                                    inDay = now.get(java.util.Calendar.DAY_OF_MONTH).toString()
                                    inMonth = (now.get(java.util.Calendar.MONTH) + 1).toString()
                                    inYear = now.get(java.util.Calendar.YEAR).toString()
                                    inputError = null
                                }
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(tr("📅 ថ្ងៃនេះ", "📅 Today"), fontSize = 10.sp, color = SkyBlue, fontWeight = FontWeight.Bold)
                        }
                    }

                    // Day / Month / Year fields
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(tr("ថ្ងៃ (Day)", "Day"), fontSize = 9.sp, color = DimColor)
                            Spacer(modifier = Modifier.height(4.dp))
                            OutlinedTextField(
                                value = inDay,
                                onValueChange = { v -> if (v.all { it.isDigit() } && v.length <= 2) inDay = v },
                                textStyle = TextStyle(
                                    color = SandText,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center
                                ),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                placeholder = {
                                    Text("DD", color = DimColor, fontSize = 14.sp,
                                        modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                                },
                                shape = RoundedCornerShape(10.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    unfocusedContainerColor = PlumCard,
                                    focusedContainerColor = PlumCard,
                                    unfocusedBorderColor = DeepBorder,
                                    focusedBorderColor = SkyBlue
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(tr("ខែ (Month)", "Month"), fontSize = 9.sp, color = DimColor)
                            Spacer(modifier = Modifier.height(4.dp))
                            OutlinedTextField(
                                value = inMonth,
                                onValueChange = { v -> if (v.all { it.isDigit() } && v.length <= 2) inMonth = v },
                                textStyle = TextStyle(
                                    color = SandText,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center
                                ),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                placeholder = {
                                    Text("MM", color = DimColor, fontSize = 14.sp,
                                        modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                                },
                                shape = RoundedCornerShape(10.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    unfocusedContainerColor = PlumCard,
                                    focusedContainerColor = PlumCard,
                                    unfocusedBorderColor = DeepBorder,
                                    focusedBorderColor = SkyBlue
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        Column(modifier = Modifier.weight(1.6f)) {
                            Text(tr("ឆ្នាំ (Year)", "Year"), fontSize = 9.sp, color = DimColor)
                            Spacer(modifier = Modifier.height(4.dp))
                            OutlinedTextField(
                                value = inYear,
                                onValueChange = { v -> if (v.all { it.isDigit() } && v.length <= 4) inYear = v },
                                textStyle = TextStyle(
                                    color = SandText,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center
                                ),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                placeholder = {
                                    Text("YYYY", color = DimColor, fontSize = 14.sp,
                                        modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                                },
                                shape = RoundedCornerShape(10.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    unfocusedContainerColor = PlumCard,
                                    focusedContainerColor = PlumCard,
                                    unfocusedBorderColor = DeepBorder,
                                    focusedBorderColor = SkyBlue
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    // Error banner
                    if (inputError != null) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(CrimsonHoliday.copy(0.1f), RoundedCornerShape(8.dp))
                                .border(1.dp, CrimsonHoliday.copy(0.4f), RoundedCornerShape(8.dp))
                                .padding(horizontal = 10.dp, vertical = 8.dp)
                        ) {
                            Text("⚠", fontSize = 13.sp, color = CrimsonHoliday)
                            Text(inputError ?: "", fontSize = 10.sp, color = CrimsonHoliday)
                        }
                    }

                    // Year range hint
                    Text(
                        tr("ឆ្នាំដែលគ្រប: ២០២០ – ២០៣៥", "Supported years: 2020 – 2035"),
                        fontSize = 9.sp,
                        color = DimColor,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.End
                    )

                    // Convert button
                    Button(
                        onClick = { if (validate()) onConvert(inYear, inMonth, inDay) },
                        colors = ButtonDefaults.buttonColors(containerColor = SkyBlue),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("convert_date_button"),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("⇅", fontSize = 18.sp, color = NightBlack)
                            Text(tr("បំលែងជាចន្ទគតិ", "Convert to Lunar"), color = NightBlack, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                    }
                }
            }
        }

        // Result card
        if (convertedDate != null) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = PlumCard),
                    border = BorderStroke(1.dp, TraditionalGold.copy(0.5f)),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        // Result header row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                tr("លទ្ធផលថ្ងៃចន្ទគតិ", "Lunar Date Result"),
                                fontSize = 10.sp,
                                color = TraditionalGold,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp
                            )
                            Text(convertedDate.moonEmoji, fontSize = 24.sp)
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(TraditionalGold.copy(0.25f)))
                        Spacer(modifier = Modifier.height(12.dp))

                        // Gregorian date echo
                        val mIdx = (convertedDate.month - 1).coerceIn(0, 11)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(SkyBlue))
                            Text(
                                text = if (lang == AppLanguage.EN)
                                    "${convertedDate.dayOfWeekEn}, ${convertedDate.day} ${gregMonth(lang, mIdx)} ${convertedDate.year}"
                                else
                                    "ថ្ងៃ${convertedDate.dayOfWeek} ទី${num(lang, convertedDate.day)} ${gregMonth(lang, mIdx)} ${num(lang, convertedDate.year)}",
                                fontSize = 12.sp,
                                color = GoldSubText
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Main lunar result — large
                        Text(
                            text = if (lang == AppLanguage.EN)
                                "${lunarDayLabel(lang, convertedDate)} ${lunarMonth(lang, convertedDate.lunarMonthName)}"
                            else
                                "${lunarDayLabel(lang, convertedDate)} ខែ${convertedDate.lunarMonthName}",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = MoonWheat
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // Tags
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Box(
                                modifier = Modifier
                                    .background(TraditionalGold.copy(0.18f), RoundedCornerShape(20.dp))
                                    .border(1.dp, TraditionalGold.copy(0.4f), RoundedCornerShape(20.dp))
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text(tr("ព.ស. ${num(lang, convertedDate.BE)}", "BE ${convertedDate.BE}"), fontSize = 10.sp, color = TraditionalGold, fontWeight = FontWeight.Bold)
                            }
                            Box(
                                modifier = Modifier
                                    .background(LotusPink.copy(0.12f), RoundedCornerShape(20.dp))
                                    .border(1.dp, LotusPink.copy(0.3f), RoundedCornerShape(20.dp))
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text(zodiac(lang, convertedDate.zodiac), fontSize = 10.sp, color = LotusPink, fontWeight = FontWeight.Bold)
                            }
                            Box(
                                modifier = Modifier
                                    .background(SkyBlue.copy(0.12f), RoundedCornerShape(20.dp))
                                    .border(1.dp, SkyBlue.copy(0.3f), RoundedCornerShape(20.dp))
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    "${convertedDate.moonEmoji} " + if (lang == AppLanguage.EN) {
                                        if (convertedDate.isWaxing) "Waxing" else "Waning"
                                    } else {
                                        if (convertedDate.isWaxing) "កើត" else "រោច"
                                    },
                                    fontSize = 10.sp,
                                    color = SkyBlue,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        // Holiday alert
                        if (convertedDate.holiday != null) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(LotusPink.copy(0.1f), RoundedCornerShape(10.dp))
                                    .border(1.dp, LotusPink.copy(0.45f), RoundedCornerShape(10.dp))
                                    .padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text("🎉", fontSize = 16.sp)
                                Column {
                                    Text(tr("ថ្ងៃបុណ្យ", "Holiday"), fontSize = 9.sp, color = LotusPink.copy(0.7f))
                                    Text(localizeDual(lang, convertedDate.holiday!!), fontSize = 12.sp, color = LotusPink, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        // Auspicious alert
                        if (convertedDate.isAuspicious) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(JadeGreen.copy(0.1f), RoundedCornerShape(10.dp))
                                    .border(1.dp, JadeGreen.copy(0.45f), RoundedCornerShape(10.dp))
                                    .padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text("🌿", fontSize = 16.sp)
                                Column {
                                    Text(tr("ថ្ងៃមង្គល", "Auspicious"), fontSize = 9.sp, color = JadeGreen.copy(0.7f))
                                    Text(
                                        localizeDual(lang, convertedDate.auspiciousType ?: tr(lang, "ថ្ងៃល្អ", "Good day")),
                                        fontSize = 12.sp,
                                        color = JadeGreen,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// 6. PROFILE, SETTINGS & DEMO CONTROLLER
@Composable
fun ProfileSettingsContent(
    onLogOut: () -> Unit,
    isDarkMode: Boolean = true,
    onDarkModeToggle: (Boolean) -> Unit = {},
    appLanguage: AppLanguage = AppLanguage.KM,
    onLanguageChange: (AppLanguage) -> Unit = {}
) {
    val lang = LocalAppLanguage.current
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("khmer_calendar_prefs", android.content.Context.MODE_PRIVATE) }
    var silaNotifyEnabled by remember { mutableStateOf(prefs.getBoolean("sila_notify", true)) }
    val (NightBlack, _, PlumSurface, PlumCard, DeepBorder, _, SandText, GoldSubText, DimColor) = LocalAppColors.current

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(NightBlack)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Hero Avatar row
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(PlumSurface, RoundedCornerShape(14.dp))
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .background(
                            GoldLotusBrush,
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text("S", fontSize = 24.sp, color = NightBlack, fontWeight = FontWeight.Bold)
                }
                Column {
                    Text("Sophanit", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = MoonWheat)
                    Text("jengah6@gmail.com · ${tr("សមាជិកតាំងពីឆ្នាំ ២០២៤", "Member since 2024")}", fontSize = 10.sp, color = GoldSubText)
                }
            }
        }

        // Stats boxes section
        item {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                listOf(
                    Pair(num(lang, 128), tr("ថ្ងៃបានមើល", "Days viewed")),
                    Pair(num(lang, 34), tr("ការបំលែង", "Conversions")),
                    Pair(num(lang, 12), tr("រក្សាទុក", "Saved"))
                ).forEach { stat ->
                    Card(
                        modifier = Modifier.weight(1f),
                        colors = CardDefaults.cardColors(containerColor = PlumCard),
                        border = BorderStroke(1.dp, DeepBorder)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(stat.first, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TraditionalGold)
                            Text(stat.second, fontSize = 8.sp, color = GoldSubText)
                        }
                    }
                }
            }
        }

        // Notification Settings Panel list item
        item {
            Text(tr("ភាសា និង ការជូនដំណឹង (LANGUAGES & ALERTS)", "LANGUAGE & ALERTS"), fontSize = 10.sp, color = DimColor, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            Spacer(modifier = Modifier.height(6.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(PlumSurface, RoundedCornerShape(12.dp))
                    .border(1.dp, DeepBorder, RoundedCornerShape(12.dp))
            ) {
                // Functional language selector — switches the whole app live.
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(tr("ភាសា (Language)", "Language"), fontSize = 11.sp, color = SandText)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(
                            AppLanguage.KM to "ខ្មែរ",
                            AppLanguage.EN to "English"
                        ).forEach { (langOption, label) ->
                            val isActive = appLanguage == langOption
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(if (isActive) TraditionalGold else PlumCard)
                                    .border(
                                        1.dp,
                                        if (isActive) TraditionalGold else DeepBorder,
                                        RoundedCornerShape(20.dp)
                                    )
                                    .clickable { onLanguageChange(langOption) }
                                    .padding(horizontal = 12.dp, vertical = 5.dp)
                            ) {
                                Text(
                                    label,
                                    fontSize = 10.sp,
                                    color = if (isActive) NightBlack else SandText,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
                Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(DeepBorder))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(tr("ការផ្តល់ដំណឹងថ្ងៃសីល (Buddhist Sila Notification)", "Buddhist Sila Notification"), fontSize = 11.sp, color = SandText, modifier = Modifier.weight(1f))
                    Switch(
                        checked = silaNotifyEnabled,
                        onCheckedChange = { enabled ->
                            silaNotifyEnabled = enabled
                            prefs.edit().putBoolean("sila_notify", enabled).apply()
                        },
                        colors = SwitchDefaults.colors(checkedThumbColor = TraditionalGold, checkedTrackColor = TraditionalGold.copy(0.4f))
                    )
                }
            }
        }

        // Dark/Light mode toggle
        item {
            Text(tr("ការបង្ហាញ (DISPLAY)", "DISPLAY"), fontSize = 10.sp, color = DimColor, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(PlumSurface, RoundedCornerShape(12.dp))
                    .border(1.dp, DeepBorder, RoundedCornerShape(12.dp))
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(if (isDarkMode) "🌙" else "☀️", fontSize = 16.sp)
                    Text(tr("របៀបតាមយប់/ថ្ងៃ (Dark Mode)", "Dark / Light Mode"), fontSize = 11.sp, color = SandText)
                }
                Switch(
                    checked = isDarkMode,
                    onCheckedChange = { onDarkModeToggle(it) },
                    colors = SwitchDefaults.colors(checkedThumbColor = TraditionalGold, checkedTrackColor = TraditionalGold.copy(0.4f))
                )
            }
        }

        // Logout
        item {
            Spacer(modifier = Modifier.height(10.dp))
            Button(
                onClick = onLogOut,
                colors = ButtonDefaults.buttonColors(containerColor = CrimsonHoliday),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("logout_button"),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(tr("ចាកចេញពីគណនី (Log Out)", "Log Out"), color = SandText, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
        }
    }
}
