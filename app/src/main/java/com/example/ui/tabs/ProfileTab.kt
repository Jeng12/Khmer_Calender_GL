package com.example.ui.tabs

import android.Manifest
import android.app.AlarmManager
import android.app.PendingIntent
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
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
import androidx.lifecycle.lifecycleScope
import coil.compose.AsyncImage
import com.example.alarm.*
import com.example.calendar.*
import com.example.core.*
import com.example.data.*
import com.example.ui.auth.*
import com.example.ui.components.*
import com.example.ui.navigation.*
import com.example.ui.tabs.*
import com.example.ui.theme.*
import com.example.ui.theme.MyApplicationTheme
import com.example.widget.WidgetPrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

// 6. PROFILE, SETTINGS & DEMO CONTROLLER
@Composable
fun ProfileSettingsContent(
    onLogOut: () -> Unit,
    isDarkMode: Boolean = true,
    onDarkModeToggle: (Boolean) -> Unit = {},
    appLanguage: AppLanguage = AppLanguage.KM,
    onLanguageChange: (AppLanguage) -> Unit = {},
    onOpenSchedule: () -> Unit = {}
) {
    val lang = LocalAppLanguage.current
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("khmer_calendar_prefs", android.content.Context.MODE_PRIVATE) }
    var silaNotifyEnabled by remember { mutableStateOf(prefs.getBoolean("sila_notify", true)) }

    // Reminder / alarm settings (custom ringtone, ring behaviour, default time)
    val defaultRingtoneLabel = tr("លំនាំដើម", "Default")
    var ringtoneTitle by remember { mutableStateOf(AppStore.getRingtoneTitle(context) ?: defaultRingtoneLabel) }
    var insistent by remember { mutableStateOf(AppStore.isInsistent(context)) }
    var defaultReminderMinutes by remember { mutableIntStateOf(AppStore.getDefaultReminderMinutes(context)) }

    val ringtoneLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            @Suppress("DEPRECATION")
            val uri: Uri? = result.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            AppStore.setRingtoneUri(context, uri?.toString())
            val title = uri?.let { runCatching { RingtoneManager.getRingtone(context, it)?.getTitle(context) }.getOrNull() }
            AppStore.setRingtoneTitle(context, title)
            ringtoneTitle = title ?: defaultRingtoneLabel
        }
    }
    fun openRingtonePicker() {
        val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
            putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM or RingtoneManager.TYPE_NOTIFICATION)
            putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Select reminder sound")
            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
            putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, AppStore.getRingtoneUri(context)?.let { Uri.parse(it) })
        }
        ringtoneLauncher.launch(intent)
    }
    var userName by remember { mutableStateOf(prefs.getString("user_name", "Sophanit") ?: "Sophanit") }
    var showEditNameDialog by remember { mutableStateOf(false) }
    var profileImageUri by remember { mutableStateOf(prefs.getString("profile_image_uri", null)?.let { Uri.parse(it) }) }

    val (NightBlack, _, PlumSurface, PlumCard, DeepBorder, _, SandText, GoldSubText, DimColor) = LocalAppColors.current

    // Helper to copy selected gallery URI to internal storage for persistence
    fun saveImageToInternalStorage(uri: Uri): Uri? {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri)
            val file = File(context.filesDir, "profile_picture.jpg")
            val outputStream = FileOutputStream(file)
            inputStream?.use { input ->
                outputStream.use { output ->
                    input.copyTo(output)
                }
            }
            Uri.fromFile(file)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri ->
            uri?.let {
                val savedUri = saveImageToInternalStorage(it)
                if (savedUri != null) {
                    profileImageUri = savedUri
                    prefs.edit().putString("profile_image_uri", savedUri.toString()).apply()
                }
            }
        }
    )

    // Widget settings + a scope to push live updates to placed widgets.
    val scope = rememberCoroutineScope()
    var widgetLang by remember { mutableStateOf(WidgetPrefs.langSetting(context)) }
    var widgetTheme by remember { mutableStateOf(WidgetPrefs.themeSetting(context)) }

    // Edit Name Dialog
    if (showEditNameDialog) {
        var tempName by remember { mutableStateOf(userName) }
        AlertDialog(
            onDismissRequest = { showEditNameDialog = false },
            title = { Text(tr("ប្តូរឈ្មោះ", "Change Name"), color = SandText) },
            text = {
                TextField(
                    value = tempName,
                    onValueChange = { tempName = it },
                    label = { Text(tr("ឈ្មោះ", "Name")) },
                    singleLine = true,
                    colors = TextFieldDefaults.colors(
                        focusedTextColor = SandText,
                        unfocusedTextColor = SandText,
                        focusedContainerColor = PlumCard,
                        unfocusedContainerColor = PlumCard,
                        cursorColor = TraditionalGold,
                        focusedIndicatorColor = TraditionalGold,
                        unfocusedIndicatorColor = DeepBorder
                    )
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (tempName.isNotBlank()) {
                            userName = tempName
                            prefs.edit().putString("user_name", tempName).apply()
                        }
                        showEditNameDialog = false
                    }
                ) {
                    Text(tr("យល់ព្រម", "OK"), color = TraditionalGold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditNameDialog = false }) {
                    Text(tr("បោះបង់", "Cancel"), color = GoldSubText)
                }
            },
            containerColor = PlumSurface,
            shape = RoundedCornerShape(16.dp)
        )
    }

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
                        .clip(CircleShape)
                        .background(GoldLotusBrush)
                        .clickable {
                            photoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (profileImageUri != null) {
                        AsyncImage(
                            model = profileImageUri,
                            contentDescription = "Profile Picture",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Text(
                            userName.take(1).uppercase(),
                            fontSize = 24.sp,
                            color = NightBlack,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    // Overlay edit icon
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.BottomEnd
                    ) {
                        Text("📷", fontSize = 10.sp, modifier = Modifier.padding(4.dp))
                    }
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { showEditNameDialog = true }
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(userName, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = MoonWheat)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("✏️", fontSize = 12.sp)
                    }
                    Text("jengah6@gmail.com · ${tr("សមាជិកតាំងពីឆ្នាំ ២០២៤", "Member since 2024")}", fontSize = 10.sp, color = GoldSubText)
                }
            }
        }

        // Edit Name Dialog
        // [MOVED DOWN] Notification Settings Panel list item
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
                                    .clickable {
                                        onLanguageChange(langOption)
                                        scope.launch { WidgetPrefs.refresh(context) }
                                    }
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

        // Work schedule entry
        item {
            Text(tr("កាលវិភាគ (SCHEDULE)", "SCHEDULE"), fontSize = 10.sp, color = DimColor, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(PlumSurface, RoundedCornerShape(12.dp))
                    .border(1.dp, DeepBorder, RoundedCornerShape(12.dp))
                    .clickable { onOpenSchedule() }
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("💼", fontSize = 16.sp)
                    Text(tr("កាលវិភាគការងារ (Work Schedule)", "Work Schedule"), fontSize = 11.sp, color = SandText)
                }
                Text("›", fontSize = 20.sp, color = TraditionalGold, fontWeight = FontWeight.Bold)
            }
        }

        // Reminder / alarm settings
        item {
            Text(tr("ការរំលឹក និងសំឡេងរោទ៍ (REMINDERS & ALARM)", "REMINDERS & ALARM"), fontSize = 10.sp, color = DimColor, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            Spacer(modifier = Modifier.height(6.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(PlumSurface, RoundedCornerShape(12.dp))
                    .border(1.dp, DeepBorder, RoundedCornerShape(12.dp))
            ) {
                // Ringtone picker
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { openRingtonePicker() }.padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("🎵", fontSize = 16.sp)
                        Text(tr("សំឡេងរោទ៍ (Ringtone)", "Alarm ringtone"), fontSize = 11.sp, color = SandText)
                    }
                    Text(ringtoneTitle, fontSize = 10.sp, color = TraditionalGold, fontWeight = FontWeight.Bold, maxLines = 1)
                }
                Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(DeepBorder))
                // Default reminder time
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            android.app.TimePickerDialog(
                                context,
                                { _, h, m ->
                                    defaultReminderMinutes = h * 60 + m
                                    AppStore.setDefaultReminderMinutes(context, defaultReminderMinutes)
                                },
                                defaultReminderMinutes / 60, defaultReminderMinutes % 60, true
                            ).show()
                        }
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("⏰", fontSize = 16.sp)
                        Text(tr("ម៉ោងរំលឹកលំនាំដើម (Default time)", "Default reminder time"), fontSize = 11.sp, color = SandText)
                    }
                    Text("%02d:%02d".format(defaultReminderMinutes / 60, defaultReminderMinutes % 60), fontSize = 11.sp, color = TraditionalGold, fontWeight = FontWeight.Bold)
                }
                Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(DeepBorder))
                // Ring until dismissed (insistent)
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Text("🔁", fontSize = 16.sp)
                        Text(tr("រោទ៍រហូតដល់បិទ (Ring until dismissed)", "Ring until dismissed"), fontSize = 11.sp, color = SandText)
                    }
                    Switch(
                        checked = insistent,
                        onCheckedChange = { insistent = it; AppStore.setInsistent(context, it) },
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
                    onCheckedChange = {
                        onDarkModeToggle(it)
                        scope.launch { WidgetPrefs.refresh(context) }
                    },
                    colors = SwitchDefaults.colors(checkedThumbColor = TraditionalGold, checkedTrackColor = TraditionalGold.copy(0.4f))
                )
            }
        }

        // Widget settings
        item {
            Text(tr("ផ្ទាំង Widget (WIDGET)", "WIDGET"), fontSize = 10.sp, color = DimColor, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            Spacer(modifier = Modifier.height(6.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(PlumSurface, RoundedCornerShape(12.dp))
                    .border(1.dp, DeepBorder, RoundedCornerShape(12.dp))
            ) {
                // Widget language
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(tr("ភាសា Widget (Language)", "Widget Language"), fontSize = 11.sp, color = SandText, modifier = Modifier.weight(1f))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        SettingChip(tr("តាមកម្មវិធី", "Follow"), widgetLang == WidgetPrefs.FOLLOW) {
                            widgetLang = WidgetPrefs.FOLLOW; WidgetPrefs.setLang(context, widgetLang); scope.launch { WidgetPrefs.refresh(context) }
                        }
                        SettingChip("ខ្មែរ", widgetLang == "km") {
                            widgetLang = "km"; WidgetPrefs.setLang(context, "km"); scope.launch { WidgetPrefs.refresh(context) }
                        }
                        SettingChip("EN", widgetLang == "en") {
                            widgetLang = "en"; WidgetPrefs.setLang(context, "en"); scope.launch { WidgetPrefs.refresh(context) }
                        }
                    }
                }
                Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(DeepBorder))
                // Widget theme
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(tr("ស្បែក Widget (Theme)", "Widget Theme"), fontSize = 11.sp, color = SandText, modifier = Modifier.weight(1f))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        SettingChip(tr("តាមកម្មវិធី", "Follow"), widgetTheme == WidgetPrefs.FOLLOW) {
                            widgetTheme = WidgetPrefs.FOLLOW; WidgetPrefs.setTheme(context, widgetTheme); scope.launch { WidgetPrefs.refresh(context) }
                        }
                        SettingChip("🌙", widgetTheme == "dark") {
                            widgetTheme = "dark"; WidgetPrefs.setTheme(context, "dark"); scope.launch { WidgetPrefs.refresh(context) }
                        }
                        SettingChip("☀️", widgetTheme == "light") {
                            widgetTheme = "light"; WidgetPrefs.setTheme(context, "light"); scope.launch { WidgetPrefs.refresh(context) }
                        }
                    }
                }
                Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(DeepBorder))
                // Refresh now
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(tr("ផ្ទុក Widget ឡើងវិញ", "Refresh widget"), fontSize = 11.sp, color = SandText, modifier = Modifier.weight(1f))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(SkyBlue.copy(0.15f))
                            .border(1.dp, SkyBlue.copy(0.4f), RoundedCornerShape(20.dp))
                            .clickable {
                                scope.launch {
                                    WidgetPrefs.refresh(context)
                                    Toast.makeText(
                                        context,
                                        tr(lang, "បានផ្ទុក Widget ឡើងវិញ", "Widget refreshed"),
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(tr("↻ ផ្ទុកឡើងវិញ", "↻ Refresh"), fontSize = 10.sp, color = SkyBlue, fontWeight = FontWeight.Bold)
                    }
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
                Text(tr("ចាកចេញពីគណនី (Log Out)", "Log Out"), color = SandText, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
        }
    }
}

/** A small pill toggle used by the Widget settings rows. */
@Composable
private fun SettingChip(label: String, active: Boolean, onClick: () -> Unit) {
    val (NightBlack, _, _, PlumCard, DeepBorder, _, SandText, _, _) = LocalAppColors.current
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (active) TraditionalGold else PlumCard)
            .border(1.dp, if (active) TraditionalGold else DeepBorder, RoundedCornerShape(20.dp))
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 5.dp)
    ) {
        Text(
            label,
            fontSize = 10.sp,
            color = if (active) NightBlack else SandText,
            fontWeight = FontWeight.Bold
        )
    }
}
