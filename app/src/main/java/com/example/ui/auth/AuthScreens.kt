package com.example.ui.auth

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
                color = SandText,
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
                color = SandText
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
                Text(tr("បន្តទៅមុខទៀត ->", "Continue ->"), color = OnAccent, fontWeight = FontWeight.Bold, fontSize = 14.sp)
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
                    Text(tr("ចូលគណនី (Sign In)", "Sign In"), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = SandText)
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
                Text(tr("ចូលគណនី (Sign In)", "Sign In"), color = OnAccent, fontWeight = FontWeight.Bold, fontSize = 14.sp)
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
        Text(tr("ចុះឈ្មោះថ្មី", "Create Account"), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = SandText)
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
            Text(tr("ចុះឈ្មោះភ្លាមៗ", "Register Now"), color = OnAccent, fontWeight = FontWeight.Bold)
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
                Text(tr("ភ្លេចពាក្យសម្ងាត់?", "Forgot password?"), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = SandText)
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
            Text(tr("ផ្ញើតំណភ្ជាប់", "Send Link"), color = OnAccent, fontWeight = FontWeight.Bold)
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
        Text(tr("បញ្ជាក់លេខកូដសម្ងាត់ OTP", "Verify OTP Code"), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = SandText)
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
            Text(tr("ផ្ទៀងផ្ទាត់ និងចូល", "Verify & Sign In"), color = OnAccent, fontWeight = FontWeight.Bold)
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
