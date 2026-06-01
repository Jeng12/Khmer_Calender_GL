package com.example.ui.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// ─────────────────────────────────────────────────────────────────────────────
//  Task 3.1 — Consistent spacing scale.
//  A single source of truth for padding / gap values, replacing ad-hoc
//  hardcoded dp literals. Based on a 4dp base grid.
//
//  Usage:
//    Modifier.padding(KhmerSpacing.md)
//    Arrangement.spacedBy(KhmerSpacing.sm)
// ─────────────────────────────────────────────────────────────────────────────
object KhmerSpacing {
    val xs:  Dp = 4.dp   // tight inner gaps, dots, hairlines
    val sm:  Dp = 8.dp   // gaps between chips / small rows
    val md:  Dp = 12.dp  // default content spacing
    val lg:  Dp = 16.dp  // screen / card padding
    val xl:  Dp = 24.dp  // section separation
    val xxl: Dp = 32.dp  // hero / large block spacing
}
