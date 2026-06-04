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
