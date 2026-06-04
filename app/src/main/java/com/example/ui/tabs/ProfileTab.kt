package com.example.ui.tabs

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
