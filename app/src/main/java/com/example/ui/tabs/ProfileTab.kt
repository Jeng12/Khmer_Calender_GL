package com.example.ui.tabs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*

// 6. PROFILE, SETTINGS & DEMO CONTROLLER
@Composable
fun ProfileSettingsContent(
    onLogOut: () -> Unit
) {
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
                            Brush.linearGradient(listOf(TraditionalGold, LotusPink)),
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
                        checked = true,
                        onCheckedChange = {},
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
