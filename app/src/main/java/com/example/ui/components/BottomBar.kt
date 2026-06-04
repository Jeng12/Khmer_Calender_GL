package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.navigation.AppTab
import com.example.ui.theme.*

@Composable
fun CustomBottomBar(
    currentTab: AppTab,
    onTabSelect: (AppTab) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(NightBlack)
            .border(BorderStroke(1.dp, DeepBorder))
            .padding(vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Home Tab
            BottomBarItem(
                emoji = "🏠",
                label = "ទំព័រដើម",
                subLabel = "Home",
                isSelected = currentTab == AppTab.HOME,
                onClick = { onTabSelect(AppTab.HOME) }
            )
            // Calendar Tab
            BottomBarItem(
                emoji = "📅",
                label = "ប្រតិទិន",
                subLabel = "Calendar",
                isSelected = currentTab == AppTab.CALENDAR,
                onClick = { onTabSelect(AppTab.CALENDAR) }
            )
            // Auspicious Tab
            BottomBarItem(
                emoji = "🌿",
                label = "មង្គល",
                subLabel = "Auspicious",
                isSelected = currentTab == AppTab.AUSPICIOUS,
                onClick = { onTabSelect(AppTab.AUSPICIOUS) }
            )
            // Holidays Tab
            BottomBarItem(
                emoji = "🎉",
                label = "ថ្ងៃបុណ្យ",
                subLabel = "Holidays",
                isSelected = currentTab == AppTab.HOLIDAYS,
                onClick = { onTabSelect(AppTab.HOLIDAYS) }
            )
            // Convert Tab
            BottomBarItem(
                emoji = "🔄",
                label = "បំលែង",
                subLabel = "Convert",
                isSelected = currentTab == AppTab.CONVERT,
                onClick = { onTabSelect(AppTab.CONVERT) }
            )
            // Profile Tab
            BottomBarItem(
                emoji = "👤",
                label = "ប្រវត្តិរូប",
                subLabel = "Profile",
                isSelected = currentTab == AppTab.PROFILE,
                onClick = { onTabSelect(AppTab.PROFILE) }
            )
        }
    }
}

@Composable
fun BottomBarItem(
    emoji: String,
    label: String,
    subLabel: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .clickable { onClick() }
            .padding(horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(text = emoji, fontSize = 18.sp, modifier = Modifier.alpha(if (isSelected) 1f else 0.5f))
        Text(
            text = label,
            fontSize = 9.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color = if (isSelected) TraditionalGold else GoldSubText.copy(0.6f)
        )
        Text(
            text = subLabel,
            fontSize = 7.sp,
            color = if (isSelected) TraditionalGold.copy(0.7f) else DimColor
        )
        if (isSelected) {
            Box(
                modifier = Modifier
                    .size(12.dp, 2.dp)
                    .clip(CircleShape)
                    .background(TraditionalGold)
            )
        }
    }
}
