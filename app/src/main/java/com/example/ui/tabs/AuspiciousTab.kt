package com.example.ui.tabs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*

// 3. AUSPICIOUS DAYS TAB CONTAINER
@Composable
fun AuspiciousTabContent(
    selectedFilter: String,
    onFilterChange: (String) -> Unit
) {
    val filters = listOf("ទាំងអស់", "ពិធីមង្គលការ", "ឡើងផ្ទះថ្មី", "បើកអាជីវកម្ម", "ធ្វើដំណើរ")

    // Custom simulated auspicious calendar days in May 2026
    val auspiciousDaysList = listOf(
        Pair("ថ្ងៃពុធ ០៣ ឧសភា", Pair("៣ កើត ពិសាខ", "ពិធីមង្គលការ (Wedding)")),
        Pair("ថ្ងៃសៅរ៍ ០៧ ឧសភា", Pair("៧ កើត ពិសាខ", "ធ្វើដំណើរស្វែងរកលាភ (Travel)")),
        Pair("ថ្ងៃអង្គារ ១២ ឧសភា", Pair("១២ កើត ពិសាខ", "បើកអាជីវកម្ម (Business)")),
        Pair("ថ្ងៃពុធ ១៩ ឧសភា", Pair("៤ រោច ពិសាខ", "ឡើងផ្ទះថ្មី (Housewarming)")),
        Pair("ថ្ងៃអង្គារ ២៦ ឧសភា", Pair("១១ រោច ពិសាខ", "ពិធីមង្គលការ (Wedding)"))
    )

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
                    Text("Auspicious Days in Cambodia · ២០២៦", fontSize = 9.sp, color = JadeGreen)
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

        // Auspicious Day Items
        val filteredList = if (selectedFilter == "ទាំងអស់") auspiciousDaysList else {
            auspiciousDaysList.filter { it.second.second.contains(selectedFilter.replace(" ថ្មី", "")) }
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
            items(filteredList) { dayInfo ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(PlumCard, RoundedCornerShape(12.dp))
                        .border(1.dp, JadeGreen.copy(0.25f), RoundedCornerShape(12.dp))
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(dayInfo.first, fontSize = 12.sp, color = SandText, fontWeight = FontWeight.Bold)
                        Text(dayInfo.second.first, fontSize = 10.sp, color = TraditionalGold)
                    }
                    Box(
                        modifier = Modifier
                            .background(JadeGreen.copy(0.12f), RoundedCornerShape(8.dp))
                            .border(1.dp, JadeGreen.copy(0.4f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(dayInfo.second.second, fontSize = 9.sp, color = JadeGreen, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
