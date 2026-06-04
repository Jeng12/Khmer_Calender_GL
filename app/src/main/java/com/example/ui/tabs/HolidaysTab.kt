package com.example.ui.tabs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*

// 4. HOLIDAYS TAB CONTAINER
@Composable
fun HolidaysTabContent(
    selectedFilter: String,
    onFilterChange: (String) -> Unit
) {
    val filters = listOf("ទាំងអស់", "ជាតិ (National)", "ព្រះពុទ្ធ (Buddhist)")

    val holidaysList = listOf(
        Triple("១៤-១៦ មេសា", "ចូលឆ្នាំថ្មីប្រពៃណីជាតិ", "Khmer New Year Day"),
        Triple("១៥ ឧសភា", "បុណ្យវិសាខបូជា", "Visak Bochea Day"),
        Triple("១-១៥ កញ្ញា", "បុណ្យភ្ជុំបិណ្ឌ", "Pchum Ben Festival"),
        Triple("១៣-១៥ តុលា", "ព្រះរាជពិធីបុណ្យអុំទូក", "Water Festival Holiday"),
        Triple("០៩ វិច្ឆិកា", "ទិវាបុណ្យឯករាជ្យជាតិ", "Independence Day")
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

        // Items mapping
        items(holidaysList) { holiday ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .background(PlumSurface, RoundedCornerShape(12.dp))
                    .border(1.dp, DeepBorder, RoundedCornerShape(12.dp))
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(LotusPink.copy(0.12f), RoundedCornerShape(12.dp))
                        .border(1.dp, LotusPink.copy(0.3f), RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("🏮", fontSize = 18.sp)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(holiday.second, fontSize = 12.sp, color = SandText, fontWeight = FontWeight.Bold)
                    Text(holiday.third, fontSize = 9.sp, color = DimColor)
                    Text(holiday.first, fontSize = 10.sp, color = TraditionalGold, fontWeight = FontWeight.Bold)
                }
                Box(
                    modifier = Modifier
                        .background(LotusPink.copy(0.12f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text("ឈប់", fontSize = 9.sp, color = LotusPink, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
