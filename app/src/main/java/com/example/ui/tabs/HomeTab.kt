package com.example.ui.tabs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.calendar.KhmerCalendarHelper
import com.example.ui.navigation.AppTab
import com.example.ui.theme.*

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
