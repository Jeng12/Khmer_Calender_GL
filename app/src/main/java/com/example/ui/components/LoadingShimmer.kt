package com.example.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// ─── Palette (matches the app's dark plum theme) ──────────────────────────────
private val ShimmerBase    = Color(0xFF2A1F3D)
private val ShimmerHighlight = Color(0xFF3D2E57)
private val ShimmerBright  = Color(0xFF4A3866)

/**
 * A single animated shimmer box. The gradient sweeps left-to-right on a
 * 1200 ms infinite loop, giving a premium "loading" feel that matches the
 * app's dark plum glassmorphism design.
 */
@Composable
fun ShimmerBox(
    modifier: Modifier = Modifier,
    height: Dp = 16.dp,
    cornerRadius: Dp = 8.dp
) {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val shimmerX by transition.animateFloat(
        initialValue = -1000f,
        targetValue  =  2000f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmerX"
    )

    val brush = Brush.linearGradient(
        colors = listOf(ShimmerBase, ShimmerBright, ShimmerBase),
        start  = Offset(shimmerX, 0f),
        end    = Offset(shimmerX + 600f, 0f)
    )

    Box(
        modifier = modifier
            .height(height)
            .clip(RoundedCornerShape(cornerRadius))
            .background(brush)
    )
}

/** A shimmer circle (for avatar / icon placeholders). */
@Composable
fun ShimmerCircle(size: Dp = 40.dp) {
    val transition = rememberInfiniteTransition(label = "shimmerCircle")
    val shimmerX by transition.animateFloat(
        initialValue = -1000f,
        targetValue  =  2000f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmerCircleX"
    )
    val brush = Brush.linearGradient(
        colors = listOf(ShimmerBase, ShimmerBright, ShimmerBase),
        start  = Offset(shimmerX, 0f),
        end    = Offset(shimmerX + 600f, 0f)
    )
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(brush)
    )
}

/**
 * Skeleton that mimics an [AgendaItemRow] card — icon circle + two text lines.
 * Used in CalendarTab while the month overlays are loading.
 */
@Composable
fun AgendaRowShimmer() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(ShimmerBase)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ShimmerCircle(size = 40.dp)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                ShimmerBox(modifier = Modifier.fillMaxWidth(0.65f), height = 13.dp)
                ShimmerBox(modifier = Modifier.fillMaxWidth(0.45f), height = 10.dp)
            }
        }
    }
}

/**
 * Full CalendarTab agenda shimmer — header line + 4 skeleton agenda rows.
 * Shown while the remote month overlays are in flight.
 */
@Composable
fun CalendarAgendaShimmer() {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ShimmerBox(modifier = Modifier.fillMaxWidth(0.4f), height = 11.dp, cornerRadius = 4.dp)
        repeat(4) {
            AgendaRowShimmer()
        }
    }
}

/**
 * HomeTab upcoming-holidays shimmer — three skeleton event rows.
 * Shown while the remote holiday list is loading.
 */
@Composable
fun HomeHolidayShimmer() {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        repeat(3) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ShimmerCircle(size = 36.dp)
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    ShimmerBox(modifier = Modifier.fillMaxWidth(0.7f), height = 12.dp)
                    ShimmerBox(modifier = Modifier.fillMaxWidth(0.4f), height = 10.dp)
                }
            }
        }
    }
}

/**
 * ScheduleTab shimmer — cycle-header bar + a 26-slot day-assignment grid.
 * Shown while the remote work schedule is being pulled.
 */
@Composable
fun ScheduleShimmer() {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Month navigation bar skeleton
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(ShimmerBase)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                ShimmerBox(modifier = Modifier.size(28.dp), cornerRadius = 14.dp)
                ShimmerBox(modifier = Modifier.fillMaxWidth(0.4f), height = 14.dp)
                ShimmerBox(modifier = Modifier.size(28.dp), cornerRadius = 14.dp)
            }
        }

        // Shift-type badge row
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            repeat(2) {
                ShimmerBox(
                    modifier = Modifier.weight(1f),
                    height = 44.dp,
                    cornerRadius = 10.dp
                )
            }
        }

        // Day grid (26 cells arranged 7 per row ≈ 4 rows)
        val rows = 4
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            repeat(rows) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    repeat(7) {
                        ShimmerBox(
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(0.85f),
                            height = 52.dp,
                            cornerRadius = 10.dp
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ShimmerCard(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(ShimmerBase)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ShimmerBox(modifier = Modifier.fillMaxWidth(0.72f), height = 14.dp)
        ShimmerBox(modifier = Modifier.fillMaxWidth(), height = 11.dp)
        ShimmerBox(modifier = Modifier.fillMaxWidth(0.48f), height = 11.dp)
    }
}

@Composable
fun CalendarShimmer() = CalendarAgendaShimmer()

@Composable
fun HomeShimmer() = HomeHolidayShimmer()
