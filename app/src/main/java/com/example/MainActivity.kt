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

/* ─────────────────────────────────────────────────────────────
   AUTHENTICATION SCREENS (Splash, Onboarding, Login, etc.)
───────────────────────────────────────────────────────────── */

@Composable
fun SplashScreenContent() {
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
                text = "ប្រតិទិនខ្មែរ",
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
                text = "កំពុងផ្ទុក…",
                fontSize = 10.sp,
                color = DimColor
            )
        }
    }
}

@Composable
fun OnboardingScreenContent(onContinue: () -> Unit) {
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
                text = "ប្រតិទិនចន្ទគតិ",
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
                Text("បន្តទៅមុខទៀត ->", color = NightBlack, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = "រំលង (Skip)",
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
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var emailError by remember { mutableStateOf<String?>(null) }
    var passwordError by remember { mutableStateOf<String?>(null) }

    fun validateAndSignIn() {
        val emailRegex = Regex("^[^@]+@[^@]+\\.[^@]+")
        emailError = when {
            email.isBlank() -> "សូមបញ្ចូលអ៊ីមែល (Email required)"
            !emailRegex.matches(email) -> "អ៊ីមែលមិនត្រឹមត្រូវ (Invalid email)"
            else -> null
        }
        passwordError = when {
            password.isBlank() -> "សូមបញ្ចូលពាក្យសម្ងាត់ (Password required)"
            password.length < 6 -> "ពាក្យសម្ងាត់ត្រូវការ ៦ តួ+ (Min 6 characters)"
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
                    Text("ចូលគណនី (Sign In)", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MoonWheat)
                    Text("Sign in to your traditional calendar", fontSize = 11.sp, color = DimColor)
                }
            }

            // Input Fields
            Text("អ៊ីមែល / EMAIL", fontSize = 9.sp, color = DimColor, letterSpacing = 1.sp)
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

            Text("ពាក្យសម្ងាត់ / PASSWORD", fontSize = 9.sp, color = DimColor, letterSpacing = 1.sp)
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
                    text = "ភ្លេចពាក្យសម្ងាត់?",
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
                Text("ចូលគណនី (Sign In)", color = NightBlack, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }

            Spacer(modifier = Modifier.height(16.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(modifier = Modifier.weight(1f).height(1.dp).background(DeepBorder))
                Text("ឬ បន្តជាមួយ", color = DimColor, fontSize = 10.sp)
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
                    Text("មិនទាន់មានគណនី?", color = GoldSubText, fontSize = 11.sp)
                    Text(
                        text = "ចុះឈ្មោះនៅទីនេះ",
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
    var fn by remember { mutableStateOf("") }
    var ln by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var emailError by remember { mutableStateOf<String?>(null) }
    var passwordError by remember { mutableStateOf<String?>(null) }
    var nameError by remember { mutableStateOf<String?>(null) }

    fun validateAndRegister() {
        val emailRegex = Regex("^[^@]+@[^@]+\\.[^@]+")
        nameError = if (fn.isBlank() || ln.isBlank()) "សូមបញ្ចូលឈ្មោះ (Name required)" else null
        emailError = when {
            email.isBlank() -> "សូមបញ្ចូលអ៊ីមែល (Email required)"
            !emailRegex.matches(email) -> "អ៊ីមែលមិនត្រឹមត្រូវ (Invalid email)"
            else -> null
        }
        passwordError = when {
            password.isBlank() -> "សូមបញ្ចូលពាក្យសម្ងាត់ (Password required)"
            password.length < 6 -> "ពាក្យសម្ងាត់ត្រូវការ ៦ តួ+ (Min 6 characters)"
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
            Text("ត្រឡប់ក្រោយ (Back)", color = TraditionalGold, fontSize = 11.sp)
        }

        Spacer(modifier = Modifier.height(20.dp))
        Text("ចុះឈ្មោះថ្មី", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MoonWheat)
        Text("Create your traditional calendar profile", fontSize = 11.sp, color = DimColor)

        Spacer(modifier = Modifier.height(20.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(modifier = Modifier.weight(1f)) {
                Text("នាមត្រកូល (Last Name)", fontSize = 9.sp, color = DimColor)
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
                Text("នាមខ្លួន (First Name)", fontSize = 9.sp, color = DimColor)
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
        Text("អ៊ីមែល (Email)", fontSize = 9.sp, color = DimColor)
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
        Text("ពាក្យសម្ងាត់ (Password)", fontSize = 9.sp, color = DimColor)
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
                text = "ខ្ញុំយល់ព្រមតាម លក្ខខណ្ឌ និង គោលការណ៍ របស់កម្មវិធី។",
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
            Text("ចុះឈ្មោះភ្លាមៗ", color = NightBlack, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun ForgotScreenContent(onBack: () -> Unit, onSend: () -> Unit) {
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
            Text("ត្រឡប់ក្រោយ", color = TraditionalGold, fontSize = 11.sp)
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
                Text("ភ្លេចពាក្យសម្ងាត់?", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MoonWheat)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "បញ្ចូលអ៊ីមែលរបស់អ្នកដើម្បីទទួលបានតំណភ្ជាប់ប្តូរលេខសម្ងាត់ថ្មី។",
                    fontSize = 10.sp,
                    color = GoldSubText,
                    textAlign = TextAlign.Center,
                    lineHeight = 16.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
        Text("អ៊ីមែលរបស់អ្នក", fontSize = 9.sp, color = DimColor)
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
            Text("ផ្ញើតំណភ្ជាប់", color = NightBlack, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun OTPScreenContent(onBack: () -> Unit, onVerify: () -> Unit) {
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
            Text("ត្រឡប់ក្រោយ", color = TraditionalGold, fontSize = 11.sp)
        }

        Spacer(modifier = Modifier.height(30.dp))
        Text("បញ្ជាក់លេខកូដសម្ងាត់ OTP", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MoonWheat)
        Text("យើងបានផ្ញើលេខកូដសម្ងាត់ប្រាំមួយខ្ទង់ទៅសារទូរសព្ទរបស់អ្នក។", fontSize = 11.sp, color = GoldSubText)

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
            Text("ផ្ទៀងផ្ទាត់ និងចូល", color = NightBlack, fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(18.dp))
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Text(
                text = "មិនទទួលបានលេខកូដ? ផ្ញើម្តងទៀត (42s)",
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
                        onLogOut = onLogOut
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
                label = "ទំព័រដើម",
                subLabel = "Home",
                isSelected = currentTab == AppTab.HOME,
                onClick = { onTabSelect(AppTab.HOME) }
            )
            // Calendar Tab
            BottomBarItem(
                emoji = "📅",
                label = "ប្រតិទិន",
                subLabel = "Calendar",
                isSelected = currentTab == AppTab.CALENDAR,
                onClick = { onTabSelect(AppTab.CALENDAR) }
            )
            // Auspicious Tab
            BottomBarItem(
                emoji = "🌿",
                label = "មង្គល",
                subLabel = "Auspicious",
                isSelected = currentTab == AppTab.AUSPICIOUS,
                onClick = { onTabSelect(AppTab.AUSPICIOUS) }
            )
            // Holidays Tab
            BottomBarItem(
                emoji = "🎉",
                label = "ថ្ងៃបុណ្យ",
                subLabel = "Holidays",
                isSelected = currentTab == AppTab.HOLIDAYS,
                onClick = { onTabSelect(AppTab.HOLIDAYS) }
            )
            // Convert Tab
            BottomBarItem(
                emoji = "🔄",
                label = "បំលែង",
                subLabel = "Convert",
                isSelected = currentTab == AppTab.CONVERT,
                onClick = { onTabSelect(AppTab.CONVERT) }
            )
            // Profile Tab
            BottomBarItem(
                emoji = "👤",
                label = "ប្រវត្តិរូប",
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
        Text(text = emoji, fontSize = 18.sp, modifier = Modifier.alpha(if (isSelected) 1f else 0.5f))
        Text(
            text = label,
            fontSize = 9.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color = if (isSelected) TraditionalGold else GoldSubText.copy(0.6f)
        )
        Text(
            text = subLabel,
            fontSize = 7.sp,
            color = if (isSelected) TraditionalGold.copy(0.7f) else DimColor
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
    val calendar = remember { java.util.Calendar.getInstance() }
    val currentYear = calendar.get(java.util.Calendar.YEAR)
    val currentMonth = calendar.get(java.util.Calendar.MONTH) + 1
    val currentDay = calendar.get(java.util.Calendar.DAY_OF_MONTH)
    val currentKhmerInfo = remember(currentYear, currentMonth, currentDay) {
        KhmerCalendarHelper.getKhmerDate(currentYear, currentMonth, currentDay)
    }
    val khmerGregorianMonths = listOf(
        "មករា", "កុម្ភៈ", "មីនា", "មេសា", "ឧសភា", "មិថុនា",
        "កក្កដា", "សីហា", "កញ្ញា", "តុលា", "វិច្ឆិកា", "ធ្នូ"
    )

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
                    Text("ប្រតិទិនចន្ទគតិខ្មែរ", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MoonWheat)
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
                                Text("TODAY · ថ្ងៃនេះ", fontSize = 9.sp, color = GoldSubText, letterSpacing = 1.sp)
                                Text(
                                    text = "ថ្ងៃ${currentKhmerInfo.dayOfWeek} ទី${currentKhmerInfo.day} ${khmerGregorianMonths[currentMonth - 1]} ${KhmerCalendarHelper.toKhmerNumeral(currentYear)}",
                                    fontSize = 15.sp,
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
                            text = "${currentKhmerInfo.lunarDayName} ${currentKhmerInfo.lunarMonthName} ${currentKhmerInfo.zodiac}",
                            fontSize = 13.sp,
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
                                Text("ព.ស. ${currentKhmerInfo.BE}", fontSize = 9.sp, color = TraditionalGold, fontWeight = FontWeight.SemiBold)
                            }
                            Box(
                                modifier = Modifier
                                    .background(LotusPink.copy(0.12f), RoundedCornerShape(20.dp))
                                    .border(1.dp, LotusPink.copy(0.3f), RoundedCornerShape(20.dp))
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text("ខែ${currentKhmerInfo.lunarMonthName} ${currentKhmerInfo.lunarDayName}", fontSize = 9.sp, color = LotusPink, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }
        }

        // Quick action grids
        item {
            Text("សេវាកម្មរហ័ស (QUICK SERVICES)", fontSize = 10.sp, color = DimColor, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                QuickGridCard(
                    modifier = Modifier.weight(1f),
                    emoji = "📅",
                    title = "ប្រតិទិន",
                    subtitle = "Calendar",
                    accentColor = TraditionalGold,
                    onClick = { onTabSelect(AppTab.CALENDAR) }
                )
                QuickGridCard(
                    modifier = Modifier.weight(1f),
                    emoji = "🌿",
                    title = "ថ្ងៃមង្គល",
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
                    title = "ថ្ងៃបុណ្យ",
                    subtitle = "Holidays",
                    accentColor = LotusPink,
                    onClick = { onTabSelect(AppTab.HOLIDAYS) }
                )
                QuickGridCard(
                    modifier = Modifier.weight(1f),
                    emoji = "🔄",
                    title = "បំលែង",
                    subtitle = "Convert",
                    accentColor = SkyBlue,
                    onClick = { onTabSelect(AppTab.CONVERT) }
                )
            }
        }

        // Upcoming national events in Cambodia
        item {
            Text("ព្រឹត្តិការណ៍ខាងមុខ (UPCOMING EVENTS)", fontSize = 10.sp, color = DimColor, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            Spacer(modifier = Modifier.height(10.dp))

            val events = listOf(
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
                        Text("រំលឹក", fontSize = 9.sp, color = TraditionalGold, fontWeight = FontWeight.Bold)
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
                Text(title, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = SandText)
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
    onDayChange: (Int) -> Unit
) {
    // Memoize: recompute only when year/month changes
    val daysList = remember(year, month) { KhmerCalendarHelper.getGregorianMonthDays(year, month) }
    // Starting day of week for index 1
    val startDayOfWeekSerial = KhmerCalendarHelper.getSerialDay(year, month, 1)
    val startOffset = ((startDayOfWeekSerial + 4) % 7 + 7) % 7 // index representing start day of week (Sunday=0, etc.)

    val khmerMonthNames = listOf(
        "មករា", "កុម្ភៈ", "មីនា", "មេសា", "ឧសភា", "មិថុនា",
        "កក្កដា", "សីហា", "កញ្ញា", "តុលា", "វិច្ឆិកា", "ធ្នូ"
    )

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
                        text = "ខែ${khmerMonthNames[month - 1]} $year",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = MoonWheat
                    )
                    Text(
                        text = "ព.ស. ${selectedKhmerDate.BE} · ${selectedKhmerDate.zodiac}",
                        fontSize = 9.sp,
                        color = TraditionalGold,
                        fontWeight = FontWeight.SemiBold
                    )
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
                val weekLabels = listOf("អា", "ច", "អ", "ព", "ព្រ", "សុ", "ស")
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

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
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
                                val isHoliday = dateInfo.holiday != null
                                val isWeekend = col == 0 || col == 6

                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .aspectRatio(0.8f)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(if (isSelected) TraditionalGold.copy(0.2f) else Color.Transparent)
                                        .border(
                                            1.5.dp,
                                            if (isSelected) TraditionalGold else Color.Transparent,
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
                                            text = KhmerCalendarHelper.toKhmerNumeral(dateInfo.lunarDayVal),
                                            fontSize = 11.sp,
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
                            text = "ព័ត៌មានលម្អិតថ្ងៃទី $selectedDay",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = TraditionalGold
                        )
                        Text(selectedKhmerDate.moonEmoji, fontSize = 18.sp)
                    }

                    Text(
                        text = "Gregorian Date: ${selectedKhmerDate.dayOfWeekEn}, ${selectedKhmerDate.day} ${khmerMonthNames[selectedKhmerDate.month - 1]} ${selectedKhmerDate.year}",
                        fontSize = 10.sp,
                        color = GoldSubText
                    )

                    Text(
                        text = "ថ្ងៃចន្ទគតិ: ${selectedKhmerDate.lunarDayName} ${selectedKhmerDate.lunarMonthName}",
                        fontSize = 12.sp,
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
                                text = "🎉 ថ្ងៃបុណ្យជាតិ: ${selectedKhmerDate.holiday}",
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
                                text = "🌿 ថ្ងៃមង្គល: ល្អសម្រាប់ ${selectedKhmerDate.auspiciousType ?: "ការងារទូទៅ"}",
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
                            Pair(JadeGreen, "ថ្ងៃមង្គល"),
                            Pair(LotusPink, "ថ្ងៃបុណ្យ"),
                            Pair(TraditionalGold, "ថ្ងៃសកម្ម"),
                            Pair(CrimsonHoliday, "ថ្ងៃឈប់")
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
    val filters = listOf("ទាំងអស់", "ពិធីមង្គលការ", "ឡើងផ្ទះថ្មី", "បើកអាជីវកម្ម", "ធ្វើដំណើរ")

    val khmerMonthNames = listOf(
        "មករា", "កុម្ភៈ", "មីនា", "មេសា", "ឧសភា", "មិថុនា",
        "កក្កដា", "សីហា", "កញ្ញា", "តុលា", "វិច្ឆិកា", "ធ្នូ"
    )

    // Pair of display labels + the raw KhmerDate (needed for Gemini)
    data class AuspiciousItem(val gregLabel: String, val lunarLabel: String, val typeLabel: String, val khmerDate: KhmerDate)

    val auspiciousDaysList = remember(calendarYear, calendarMonth) {
        KhmerCalendarHelper.getGregorianMonthDays(calendarYear, calendarMonth)
            .filter { it.isAuspicious }
            .map { d ->
                val monthName = khmerMonthNames[d.month - 1]
                AuspiciousItem(
                    gregLabel  = "ថ្ងៃ${d.dayOfWeek} ${KhmerCalendarHelper.toKhmerNumeral(d.day)} $monthName",
                    lunarLabel = "${d.lunarDayName} ${d.lunarMonthName}",
                    typeLabel  = d.auspiciousType ?: "ថ្ងៃល្អ",
                    khmerDate  = d
                )
            }
    }

    val filteredList = remember(auspiciousDaysList, selectedFilter) {
        if (selectedFilter == "ទាំងអស់") auspiciousDaysList
        else auspiciousDaysList.filter { it.typeLabel.contains(selectedFilter.replace(" ថ្មី", "")) }
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
                    Text("ថ្ងៃមង្គល (Auspicious Days)", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MoonWheat)
                    Text("Auspicious Days · ${KhmerCalendarHelper.toKhmerNumeral(calendarYear)}", fontSize = 9.sp, color = JadeGreen)
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
                            text = tag,
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
                    Text("គ្មានថ្ងៃមង្គលសម្រាប់ជម្រើសនេះទេ", fontSize = 11.sp, color = DimColor)
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
                        val result = GeminiRepository.explainAuspiciousDay(khmerDate)
                        explanation = result.getOrElse { "មិនអាចភ្ជាប់ AI បានទេ (${it.message})" }
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
                Text("AI កំពុងព្យញ្ចដ…", fontSize = 9.sp, color = JadeGreen)
            } else if (explanation == null) {
                Text("✨ AI ពន្យល់", fontSize = 9.sp, color = JadeGreen)
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
    val filters = listOf("ទាំងអស់", "ជាតិ (National)", "ព្រះពុទ្ធ (Buddhist)")

    // Each holiday: Triple(date string, Khmer name, type tag)
    // Type tag must match filter values: "ជាតិ (National)" or "ព្រះពុទ្ធ (Buddhist)"
    val holidaysList = listOf(
        Triple("០១ មករា", "ទិវាឆ្នាំថ្មីអន្តរជាតិ · New Year's Day", "ជាតិ (National)"),
        Triple("០៧ មករា", "ទិវាជ័យជម្នះលើរបបប្រល័យពូជសាសន៍", "ជាតិ (National)"),
        Triple("០៨ មីនា", "ទិវាអន្តរជាតិរបស់ស្ត្រី · International Women's Day", "ជាតិ (National)"),
        Triple("១៤-១៦ មេសា", "ចូលឆ្នាំថ្មីប្រពៃណីជាតិ · Khmer New Year", "ជាតិ (National)"),
        Triple("០១ ឧសភា", "ទិវាពលកម្មអន្តរជាតិ · International Labour Day", "ជាតិ (National)"),
        Triple("ទី១៥ ពិសាខ (ច)", "បុណ្យវិសាខបូជា · Visak Bochea Day", "ព្រះពុទ្ធ (Buddhist)"),
        Triple("០១ មិថុនា", "ទិវាកុមារអន្តរជាតិ · International Children's Day", "ជាតិ (National)"),
        Triple("១៨ មិថុនា", "ព្រះរាជពិធីបុណ្យចម្រើនព្រះជន្ម សម្ដេចម៉ែ", "ជាតិ (National)"),
        Triple("ទី១-១៥ ភទ្របទ (ច)", "បុណ្យភ្ជុំបិណ្ឌ · Pchum Ben Festival", "ព្រះពុទ្ធ (Buddhist)"),
        Triple("២៤ កញ្ញា", "ទិវារដ្ឋធម្មនុញ្ញ · Constitution Day", "ជាតិ (National)"),
        Triple("១៥ តុលា", "ទិវាគោរពព្រះវិញ្ញាណក្ខន្ធ ព្រះបរមរតនកោដ្ឋ", "ជាតិ (National)"),
        Triple("ទី១៥ កត្តិក (ក)", "ព្រះរាជពិធីបុណ្យអុំទូក · Water Festival", "ព្រះពុទ្ធ (Buddhist)"),
        Triple("ទី១៥ មាឃ (ក)", "បុណ្យមាឃបូជា · Meak Bochea Day", "ព្រះពុទ្ធ (Buddhist)"),
        Triple("២៩ តុលា", "ព្រះរាជពិធីគ្រងព្រះបរមរាជសម្បត្តិ ព្រះមហាក្សត្រ", "ជាតិ (National)"),
        Triple("០៩ វិច្ឆិកា", "ទិវាបុណ្យឯករាជ្យជាតិ · Independence Day", "ជាតិ (National)"),
        Triple("១០ ធ្នូ", "ទិវាសិទ្ធិមនុស្ស · Human Rights Day", "ជាតិ (National)")
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
                Text("ថ្ងៃបុណ្យ (Cambodian Holidays)", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MoonWheat)
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
                            text = tag,
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
            else holidaysList.filter { it.third == selectedFilter }

        items(filteredHolidays) { holiday ->
            val isBuddhist = holiday.third == "ព្រះពុទ្ធ (Buddhist)"
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
                    Text(holiday.second, fontSize = 12.sp, color = SandText, fontWeight = FontWeight.Bold)
                    Text(holiday.third, fontSize = 9.sp, color = accentColor.copy(0.7f))
                    Text(holiday.first, fontSize = 10.sp, color = TraditionalGold, fontWeight = FontWeight.Bold)
                }
                Box(
                    modifier = Modifier
                        .background(accentColor.copy(0.12f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text("ឈប់", fontSize = 9.sp, color = accentColor, fontWeight = FontWeight.Bold)
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
    var inYear by remember { mutableStateOf(year) }
    var inMonth by remember { mutableStateOf(month) }
    var inDay by remember { mutableStateOf(day) }
    var inputError by remember { mutableStateOf<String?>(null) }

    val khMonths = listOf(
        "មករា", "កុម្ភៈ", "មីនា", "មេសា", "ឧសភា", "មិថុនា",
        "កក្កដា", "សីហា", "កញ្ញា", "តុលា", "វិច្ឆិកា", "ធ្នូ"
    )

    fun validate(): Boolean {
        val y = inYear.toIntOrNull()
        val m = inMonth.toIntOrNull()
        val d = inDay.toIntOrNull()
        return when {
            y == null || m == null || d == null -> {
                inputError = "សូមបញ្ចូលតម្លៃជាលេខ (numbers only)"
                false
            }
            y < 2019 || y > 2036 -> {
                inputError = "ឆ្នាំ ២០១៩–២០៣៦ ប៉ុណ្ណោះ (Year 2019–2036 only)"
                false
            }
            m < 1 || m > 12 -> {
                inputError = "ខែត្រូវស្ថិតក្នុងចន្លោះ ១–១២ (Month 1–12)"
                false
            }
            d < 1 || d > 31 -> {
                inputError = "ថ្ងៃត្រូវស្ថិតក្នុងចន្លោះ ១–៣១ (Day 1–31)"
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
                        Text("បំលែងថ្ងៃខែ", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = MoonWheat)
                        Text("ពីគ្រីស្ដសករាជ → ចន្ទគតិខ្មែរ", fontSize = 10.sp, color = SkyBlue)
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
                            "ថ្ងៃខែគ្រីស្ដសករាជ",
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
                            Text("📅 ថ្ងៃនេះ", fontSize = 10.sp, color = SkyBlue, fontWeight = FontWeight.Bold)
                        }
                    }

                    // Day / Month / Year fields
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("ថ្ងៃ (Day)", fontSize = 9.sp, color = DimColor)
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
                            Text("ខែ (Month)", fontSize = 9.sp, color = DimColor)
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
                            Text("ឆ្នាំ (Year)", fontSize = 9.sp, color = DimColor)
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
                        "ឆ្នាំដែលគ្រប: ២០២០ – ២០៣៥",
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
                            Text("បំលែងជាចន្ទគតិ", color = NightBlack, fontWeight = FontWeight.Bold, fontSize = 14.sp)
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
                                "លទ្ធផលថ្ងៃចន្ទគតិ",
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
                                "ថ្ងៃ${convertedDate.dayOfWeek} ទី${convertedDate.day} ${khMonths[mIdx]} ${convertedDate.year}",
                                fontSize = 12.sp,
                                color = GoldSubText
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Main lunar result — large
                        Text(
                            text = "${convertedDate.lunarDayName} ខែ${convertedDate.lunarMonthName}",
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
                                Text("ព.ស. ${convertedDate.BE}", fontSize = 10.sp, color = TraditionalGold, fontWeight = FontWeight.Bold)
                            }
                            Box(
                                modifier = Modifier
                                    .background(LotusPink.copy(0.12f), RoundedCornerShape(20.dp))
                                    .border(1.dp, LotusPink.copy(0.3f), RoundedCornerShape(20.dp))
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text(convertedDate.zodiac, fontSize = 10.sp, color = LotusPink, fontWeight = FontWeight.Bold)
                            }
                            Box(
                                modifier = Modifier
                                    .background(SkyBlue.copy(0.12f), RoundedCornerShape(20.dp))
                                    .border(1.dp, SkyBlue.copy(0.3f), RoundedCornerShape(20.dp))
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    "${convertedDate.moonEmoji} ${if (convertedDate.isWaxing) "កើត" else "រោច"}",
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
                                    Text("ថ្ងៃបុណ្យ", fontSize = 9.sp, color = LotusPink.copy(0.7f))
                                    Text(convertedDate.holiday!!, fontSize = 12.sp, color = LotusPink, fontWeight = FontWeight.Bold)
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
                                    Text("ថ្ងៃមង្គល", fontSize = 9.sp, color = JadeGreen.copy(0.7f))
                                    Text(
                                        convertedDate.auspiciousType ?: "ថ្ងៃល្អ",
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
    onLogOut: () -> Unit
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("khmer_calendar_prefs", android.content.Context.MODE_PRIVATE) }
    var silaNotifyEnabled by remember { mutableStateOf(prefs.getBoolean("sila_notify", true)) }

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
                    Text("ស", fontSize = 24.sp, color = NightBlack, fontWeight = FontWeight.Bold)
                }
                Column {
                    Text("សុខ ចន្ទដារ៉ា", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = MoonWheat)
                    Text("jengah6@gmail.com · Member since 2024", fontSize = 10.sp, color = GoldSubText)
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
                    Pair("១២៨", "ថ្ងៃបានមើល"),
                    Pair("៣៤", "ការបំលែង"),
                    Pair("១២", "រក្សាទុក")
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
            Text("ភាសា និង ការជូនដំណឹង (LANGUAGES & ALERTS)", fontSize = 10.sp, color = DimColor, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            Spacer(modifier = Modifier.height(6.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(PlumSurface, RoundedCornerShape(12.dp))
                    .border(1.dp, DeepBorder, RoundedCornerShape(12.dp))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("ភាសាខ្មែរ (Khmer Language)", fontSize = 11.sp, color = SandText)
                    Text("សកម្ម", fontSize = 10.sp, color = JadeGreen, fontWeight = FontWeight.Bold)
                }
                Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(DeepBorder))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("ការផ្តល់ដំណឹងថ្ងៃសីល (Buddhist Sila Notification)", fontSize = 11.sp, color = SandText)
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
                Text("ចាកចេញពីគណនី (Log Out)", color = SandText, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
        }
    }
}
