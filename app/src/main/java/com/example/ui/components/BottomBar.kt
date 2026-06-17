package com.example.ui.components

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
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

@Composable
fun CustomBottomBar(
    currentTab: AppTab,
    onTabSelect: (AppTab) -> Unit
) {
    val (NightBlack, DeepAmethyst, PlumSurface, PlumCard, DeepBorder, DeepMuted, SandText, GoldSubText, DimColor) = LocalAppColors.current
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
                label = tr("ទំព័រដើម", "Home"),
                subLabel = "Home",
                isSelected = currentTab == AppTab.HOME,
                onClick = { onTabSelect(AppTab.HOME) }
            )
            // Calendar Tab
            BottomBarItem(
                emoji = "📅",
                label = tr("ប្រតិទិន", "Calendar"),
                subLabel = "Calendar",
                isSelected = currentTab == AppTab.CALENDAR,
                onClick = { onTabSelect(AppTab.CALENDAR) }
            )
            // Holidays Tab
            BottomBarItem(
                emoji = "🎉",
                label = tr("ថ្ងៃបុណ្យ", "Holidays"),
                subLabel = "Holidays",
                isSelected = currentTab == AppTab.HOLIDAYS,
                onClick = { onTabSelect(AppTab.HOLIDAYS) }
            )
            // Convert Tab
            BottomBarItem(
                emoji = "🔄",
                label = tr("បំលែង", "Convert"),
                subLabel = "Convert",
                isSelected = currentTab == AppTab.CONVERT,
                onClick = { onTabSelect(AppTab.CONVERT) }
            )
            // Work Schedule Tab
            BottomBarItem(
                emoji = "🏭",
                label = tr("កាលវិភាគ", "Schedule"),
                subLabel = "Work Schedule",
                isSelected = currentTab == AppTab.SCHEDULE,
                onClick = { onTabSelect(AppTab.SCHEDULE) }
            )
            // Profile Tab
            BottomBarItem(
                emoji = "👤",
                label = tr("ប្រវត្តិរូប", "Profile"),
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
    val (NightBlack, DeepAmethyst, PlumSurface, PlumCard, DeepBorder, DeepMuted, SandText, GoldSubText, DimColor) = LocalAppColors.current
    val itemAlpha by animateFloatAsState(
        targetValue = if (isSelected) 1f else 0.65f,
        animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
        label = "BottomBarItemAlpha"
    )
    val indicatorWidth by animateDpAsState(
        targetValue = if (isSelected) 14.dp else 0.dp,
        animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
        label = "BottomBarIndicatorWidth"
    )
    val labelColor by animateColorAsState(
        targetValue = if (isSelected) TraditionalGold else GoldSubText,
        animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
        label = "BottomBarLabelColor"
    )

    Column(
        modifier = Modifier
            .clickable(
                indication = ripple(color = TraditionalGold.copy(alpha = 0.2f)),
                interactionSource = remember { MutableInteractionSource() },
                onClickLabel = subLabel,
            ) { onClick() }
            .padding(horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(text = emoji, fontSize = 18.sp, modifier = Modifier.alpha(itemAlpha))
        Text(
            text = label,
            fontSize = 10.sp,
            maxLines = 1,
            softWrap = false,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            color = labelColor
        )
        Box(
            modifier = Modifier
                .width(indicatorWidth)
                .height(2.dp)
                .background(TraditionalGold, CircleShape)
        )
    }
}
