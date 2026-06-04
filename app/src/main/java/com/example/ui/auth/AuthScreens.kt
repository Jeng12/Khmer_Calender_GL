package com.example.ui.auth

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*

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
                .background(Brush.horizontalGradient(listOf(CrimsonHoliday, TraditionalGold, LotusPink)))
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
                onValueChange = { email = it },
                textStyle = LocalTextStyle.current.copy(color = SandText, fontSize = 13.sp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("email_input"),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedContainerColor = PlumSurface,
                    focusedContainerColor = PlumSurface,
                    unfocusedBorderColor = DeepBorder,
                    focusedBorderColor = TraditionalGold
                )
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text("ពាក្យសម្ងាត់ / PASSWORD", fontSize = 9.sp, color = DimColor, letterSpacing = 1.sp)
            Spacer(modifier = Modifier.height(6.dp))
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                textStyle = LocalTextStyle.current.copy(color = SandText, fontSize = 13.sp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("password_input"),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedContainerColor = PlumSurface,
                    focusedContainerColor = PlumSurface,
                    unfocusedBorderColor = DeepBorder,
                    focusedBorderColor = TraditionalGold
                )
            )

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
                onClick = onSignIn,
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
    var fn by remember { mutableStateOf("ចន្ទ") }
    var ln by remember { mutableStateOf("ដារ៉ា") }
    var email by remember { mutableStateOf("chanda@example.com") }
    var password by remember { mutableStateOf("") }

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
                    onValueChange = { ln = it },
                    textStyle = TextStyle(color = SandText, fontSize = 12.sp),
                    colors = OutlinedTextFieldDefaults.colors(unfocusedContainerColor = PlumSurface, unfocusedBorderColor = DeepBorder)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text("នាមខ្លួន (First Name)", fontSize = 9.sp, color = DimColor)
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedTextField(
                    value = fn,
                    onValueChange = { fn = it },
                    textStyle = TextStyle(color = SandText, fontSize = 12.sp),
                    colors = OutlinedTextFieldDefaults.colors(unfocusedContainerColor = PlumSurface, unfocusedBorderColor = DeepBorder)
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))
        Text("អ៊ីមែល (Email)", fontSize = 9.sp, color = DimColor)
        Spacer(modifier = Modifier.height(4.dp))
        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            textStyle = TextStyle(color = SandText, fontSize = 12.sp),
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(unfocusedContainerColor = PlumSurface, unfocusedBorderColor = DeepBorder)
        )

        Spacer(modifier = Modifier.height(14.dp))
        Text("ពាក្យសម្ងាត់ (Password)", fontSize = 9.sp, color = DimColor)
        Spacer(modifier = Modifier.height(4.dp))
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            textStyle = TextStyle(color = SandText, fontSize = 12.sp),
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(unfocusedContainerColor = PlumSurface, unfocusedBorderColor = DeepBorder)
        )

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
            onClick = onRegister,
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
