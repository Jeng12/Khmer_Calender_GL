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
