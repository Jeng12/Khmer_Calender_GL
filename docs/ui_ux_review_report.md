# UI/UX Review Report — Khmer Calendar GL

## Executive Summary

The application's dark heritage palette is visually distinctive and culturally appropriate. However, the theming system has a critical **split between the official Material 3 theme** (`ui/theme/`) and **inline custom colors** defined in `MainActivity.kt`. This creates maintainability issues and inconsistencies. The following report details each finding and prioritizes actionable improvements.

---

## 1. Theming System

### Finding 1.1 — Unused Theme Files (Critical)

**Files:** `Color.kt`, `Theme.kt`, `Type.kt`

**Issue:** The `ui/theme/` package defines a standard Material 3 color scheme (`Purple80`, `PurpleGrey80`, `Pink80`, etc.) and `MyApplicationTheme`. However, `MainActivity.kt` defines its own color constants inline and applies hard-coded `Color(0xFF...)` values throughout all composables. The Material theme colors are never referenced by any UI component.

**Impact:** `MaterialTheme.colorScheme.*` tokens return default purple values in all composables, meaning dynamic theming and accessibility features (e.g., high-contrast mode) do not function.

**Recommendation:** Move all custom colors from `MainActivity.kt` into `Color.kt` and wire them into the `DarkColorScheme`/`LightColorScheme` color schemes in `Theme.kt`.

```kotlin
// Color.kt — proposed additions
val NightBlack      = Color(0xFF0D0D0D)
val TraditionalGold = Color(0xFFD4AF37)
val LotusPink       = Color(0xFFE8A0BF)
val JadeGreen       = Color(0xFF00A86B)
val CrimsonRed      = Color(0xFFDC143C)
val SkyBlue         = Color(0xFF87CEEB)
val DeepNavy        = Color(0xFF0A1628)

// Theme.kt — use custom colors in scheme
private val DarkColorScheme = darkColorScheme(
    primary        = TraditionalGold,
    onPrimary      = NightBlack,
    secondary      = LotusPink,
    tertiary       = JadeGreen,
    background     = NightBlack,
    surface        = DeepNavy,
    error          = CrimsonRed,
)
```

---

### Finding 1.2 — Typography Not Configured (High)

**File:** `Type.kt`

**Issue:** Only `bodyLarge` is defined. All other typography roles (`titleLarge`, `titleMedium`, `labelSmall`, `headlineMedium`, etc.) fall back to Material 3 defaults, meaning font sizes and weights are not controlled by the design system.

**Impact:** Inconsistent text sizes across screens; different composables manually set `fontSize` rather than using semantic text roles.

**Recommendation:** Define a complete Khmer-appropriate typography scale:

```kotlin
val Typography = Typography(
    headlineLarge  = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.Bold, ...),
    headlineMedium = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.SemiBold, ...),
    titleLarge     = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.SemiBold, ...),
    titleMedium    = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Medium, ...),
    bodyLarge      = TextStyle(fontSize = 16.sp, lineHeight = 24.sp, ...),
    bodyMedium     = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, ...),
    labelMedium    = TextStyle(fontSize = 12.sp, letterSpacing = 0.5.sp, ...),
)
```

Consider loading the **Noto Sans Khmer** font family for authentic Khmer script rendering.

---

## 2. Color Usage

### Finding 2.1 — Color Constants Defined as Duplicates (Medium)

**File:** `MainActivity.kt`

**Issue:** Multiple similar dark background colors are defined close to each other with no clear semantic distinction:

| Name | Value | Usage |
|------|-------|-------|
| `NightBlack` | `0xFF0D0D0D` | Primary background |
| `DeepNavy` | `0xFF0A1628` | Card backgrounds |
| `CardBackground` | `0xFF1A1A2E` | Some cards |
| `SurfaceDark` | `0xFF16213E` | Other cards |

These four colors serve the same semantic role ("surface / card background") but have different hex values. This makes the design inconsistent across screens.

**Recommendation:** Consolidate to two semantically distinct colors: `background` (darkest) and `surface` (slightly lighter). Map them to `MaterialTheme.colorScheme.background` and `.surface`.

---

### Finding 2.2 — Hardcoded Alpha Values (Low)

Several composables use `Color(0xFF...).copy(alpha = 0.3f)` inline. This prevents consistent application of transparency rules.

**Recommendation:** Define semi-transparent variants in `Color.kt`:
```kotlin
val GoldDivider = TraditionalGold.copy(alpha = 0.3f)
val WhiteSubtle = Color.White.copy(alpha = 0.7f)
```

---

## 3. Spacing and Layout

### Finding 3.1 — Inconsistent Padding Values (Medium)

Padding values throughout `MainActivity.kt` are specified as literal dp values (`8.dp`, `12.dp`, `16.dp`, `20.dp`, `24.dp`) without a consistent spacing scale. Some equivalent UI elements use different padding values.

**Recommendation:** Define a spacing scale as a Kotlin object:

```kotlin
object KhmerSpacing {
    val xs  = 4.dp
    val sm  = 8.dp
    val md  = 16.dp
    val lg  = 24.dp
    val xl  = 32.dp
    val xxl = 48.dp
}
```

---

### Finding 3.2 — Non-Scrollable Content Overflows (High)

On the **Holidays** tab, the list uses `LazyColumn` correctly. However, the **Profile** tab uses a `Column` without `.verticalScroll()`, which can cause content to be cut off on small screens (e.g., Pixel 3a with screen height < 600dp).

**Recommendation:** Wrap the profile tab's `Column` in:
```kotlin
Column(modifier = Modifier.verticalScroll(rememberScrollState())) { ... }
```

---

## 4. Accessibility

### Finding 4.1 — Missing Content Descriptions (High)

Icon-only clickable elements (back button, tab icons, action buttons) lack `contentDescription` values, making the app inaccessible to screen readers (TalkBack).

**Recommendation:** Add descriptive content descriptions to all interactive icons:
```kotlin
IconButton(onClick = { ... }) {
    Icon(
        imageVector = Icons.Default.ArrowBack,
        contentDescription = "ត្រឡប់ក្រោយ (Go back)"
    )
}
```

---

### Finding 4.2 — Color Contrast Ratios (Medium)

Several text/background combinations may not meet WCAG AA (4.5:1) contrast requirements:

| Text Color | Background | Estimated Ratio | WCAG AA |
|-----------|-----------|----------------|---------|
| `TraditionalGold` (`#D4AF37`) on `NightBlack` (`#0D0D0D`) | ~9.3:1 | ✓ Pass |
| `LotusPink` (`#E8A0BF`) on `DeepNavy` (`#0A1628`) | ~6.8:1 | ✓ Pass |
| Gray text (`#808080`) on `CardBackground` (`#1A1A2E`) | ~3.2:1 | ✗ Fail |

**Recommendation:** Audit all gray/muted text on dark backgrounds using a contrast checker. Replace `Color.Gray` with at least `Color(0xFFB0B0B0)` to meet AA standards.

---

## 5. Navigation and Interaction

### Finding 5.1 — Bottom Bar Lacks Visual Feedback (Medium)

The `CustomBottomBar` shows an animated underline for the active tab but provides no touch ripple on tap. This can make the app feel unresponsive on lower-end devices.

**Recommendation:** Add a ripple indication to the tab click area:
```kotlin
Modifier.clickable(
    indication = rememberRipple(color = TraditionalGold.copy(alpha = 0.2f)),
    interactionSource = remember { MutableInteractionSource() }
) { onTabSelected(tab) }
```

---

### Finding 5.2 — No Loading States (Medium)

The date converter (`DateConvertContent`) provides no visual feedback while the calculation runs. Although the calculation is fast, showing a brief loading indicator is good UX practice.

---

### Finding 5.3 — Manual Navigation vs. Compose Navigation (Low)

The app uses enum-based `mutableState` navigation instead of Jetpack Navigation Compose. While functional, this approach:
- Makes deep linking impossible
- Prevents back-stack management
- Complicates testing

**Recommendation:** Migrate to `NavHost`/`NavController` for idiomatic Compose navigation.

---

## 6. Component-Specific Issues

### Finding 6.1 — Calendar Grid Day Cells Too Small (Medium)

The 7×6 calendar grid divides screen width into 7 columns. On phones with screen width < 360dp, the Khmer day abbreviations ("ព្រ", "ក") may be clipped.

**Recommendation:** Use `BoxWithConstraints` to adaptively reduce font size or add horizontal scrolling for narrow screens.

---

### Finding 6.2 — Hardcoded Mock Data (Medium)

Several sections contain hardcoded data that should be dynamic:
- "Upcoming Events" in Home tab (3 hardcoded entries)
- Profile statistics (128 days viewed, 34 conversions)
- Login/Register do not persist state

**Recommendation:** Integrate Room database (already declared as a dependency) to persist user data.

---

## Summary of Recommendations (Priority Order)

| Priority | Finding | Effort |
|----------|---------|--------|
| Critical | 1.1 — Connect theme to MaterialTheme | Medium |
| High | 1.2 — Complete typography scale | Low |
| High | 4.1 — Add content descriptions | Low |
| High | 3.2 — Fix profile tab overflow | Low |
| Medium | 2.1 — Consolidate background colors | Medium |
| Medium | 3.1 — Introduce spacing scale | Medium |
| Medium | 4.2 — Fix color contrast | Low |
| Medium | 5.1 — Add ripple to bottom bar | Low |
| Medium | 6.1 — Adaptive calendar grid | High |
| Low | 5.3 — Migrate to Nav Compose | High |

---

*Generated from analysis of `MainActivity.kt`, `Color.kt`, `Theme.kt`, `Type.kt`*
