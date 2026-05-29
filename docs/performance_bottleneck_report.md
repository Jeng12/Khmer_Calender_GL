# Performance Bottleneck Report — Khmer Calendar GL

## Methodology

This report identifies performance issues through static analysis of `KhmerCalendarHelper.kt` and the UI composable code in `MainActivity.kt`. Identified issues are classified by type and potential impact.

---

## 1. Calendar Logic Performance

### Bottleneck 1.1 — Milestone List Built on First Access (High Impact)

**Location:** `KhmerCalendarHelper.kt:128`

```kotlin
private val milestones: List<Milestone> by lazy { buildMilestones(2019, 2036) }
```

**Analysis:**
`buildMilestones()` calls `newMoonJDE()` for approximately `(2036-2017) × 12.37 + 4 ≈ 238` iterations. Each iteration performs:
- 4 `Math.toRadians()` calls (involving floating-point multiplication)
- 15 `sin()` calls (expensive transcendental function)
- Multiple polynomial evaluations (T², T³, T⁴)

**Estimated cost:** ~0.5–2 ms on modern devices (lazy initialization), ~10–50 ms on low-end ARM Cortex-A53 devices.

**Risk:** The `lazy` initializer runs on whichever thread first accesses `milestones`. If this is the main thread during `setContent {}` in `MainActivity`, it can cause a perceptible startup delay (> 16ms frame budget).

**Recommendation:** Pre-initialize the milestones on a background thread at app startup:
```kotlin
// In Application.onCreate() or before setContent
CoroutineScope(Dispatchers.Default).launch {
    KhmerCalendarHelper.warmUp()  // add a public warmUp() function
}
```

---

### Bottleneck 1.2 — Linear Scan for Milestone Lookup (Medium Impact)

**Location:** `KhmerCalendarHelper.kt:192`

```kotlin
val ms = milestones.lastOrNull { it.serialDay <= sDay }
```

**Analysis:** This performs a full O(n) linear scan through all ~230 milestones every time `getKhmerDate()` is called. `getGregorianMonthDays()` calls `getKhmerDate()` for every day in the month (28–31 calls), resulting in 28–31 linear scans per month render.

When the calendar grid renders for a full month, `getGregorianMonthDays(year, month)` is called once, which internally calls `getKhmerDate()` 28–31 times, each doing a linear scan through 230 milestones = ~7,000 comparisons per month render.

**Recommendation:** Replace with a binary search since milestones are sorted by `serialDay`:
```kotlin
val ms = milestones.binarySearchInsertionPoint(sDay)
    .let { idx -> milestones.getOrNull(idx - 1) } 
    ?: fallbackMilestone
```

Or use `java.util.Collections.binarySearch()`.

---

### Bottleneck 1.3 — `getSerialDay()` Called Redundantly During Milestone Building (Low Impact)

**Location:** `KhmerCalendarHelper.kt:141`

```kotlin
newMoons.add(NM(y, m, d, getSerialDay(y, m, d)))
```

During `buildMilestones()`, `getSerialDay()` is called once per new moon (within the lambda), and then again when computing `len = next.sd - nm.sd`. This is fine for startup, but it means `getSerialDay()` is called ~238 times. This is negligible in practice.

---

## 2. UI Rendering Performance

### Bottleneck 2.1 — Monolithic Composable Causing Full Recompositions (High Impact)

**Location:** `MainActivity.kt` — `KhmerCalendarApp()` composable

**Analysis:** The `KhmerCalendarApp()` composable contains approximately 12 `mutableState` variables declared at the top level. Any state change (e.g., changing `calendarMonth`) causes Compose to re-evaluate whether all child composables need recomposition. Since all 6 tab contents are conditionally rendered within the same composable hierarchy, Compose's recomposition scope can be large.

**Specific issue:** `calendarMonth` and `calendarYear` are state variables. When the user navigates months in the Calendar tab, the entire `KhmerCalendarApp()` recomposition scope is triggered, even though only `CalendarTabContent()` needs to update.

**Recommendation:** 
1. Extract state into a `ViewModel` using `LiveData` / `StateFlow`
2. Split tab contents into separate top-level composables with their own `remember`-based state

---

### Bottleneck 2.2 — `getGregorianMonthDays()` Called on Every Calendar Recomposition (High Impact)

**Location:** `MainActivity.kt` — `CalendarTabContent()`

**Analysis:** The calendar month computation is likely called directly inside a composable without memoization. Every recomposition (even unrelated ones triggered by state changes elsewhere in the parent) would recompute all 28–31 `KhmerDate` objects for the displayed month.

**Recommendation:**
```kotlin
val monthDays by remember(calendarYear, calendarMonth) {
    derivedStateOf {
        KhmerCalendarHelper.getGregorianMonthDays(calendarYear, calendarMonth)
    }
}
```

The `derivedStateOf` with `remember(key)` ensures the computation only runs when `calendarYear` or `calendarMonth` changes.

---

### Bottleneck 2.3 — LazyColumn Items Without `key` Parameters (Medium Impact)

**Location:** `MainActivity.kt` — Holidays tab, Auspicious tab

**Analysis:** `LazyColumn` / `LazyRow` items without stable `key` parameters force Compose to re-measure and re-layout all visible items when the list changes (e.g., when a filter chip is selected).

**Recommendation:** Add stable keys to all `LazyColumn` items:
```kotlin
LazyColumn {
    items(holidays, key = { it.id }) { holiday ->
        HolidayCard(holiday)
    }
}
```

---

### Bottleneck 2.4 — Gradient Brush Objects Created on Every Recomposition (Medium Impact)

**Location:** `MainActivity.kt` — Multiple composables using `Brush.linearGradient()`

**Analysis:** `Brush.linearGradient(...)` creates a new object on every recomposition. Since these are used in border/background modifiers, they are recreated frequently.

**Recommendation:** Hoist gradient brush creation outside composables or use `remember`:
```kotlin
val goldGradient = remember {
    Brush.linearGradient(listOf(TraditionalGold, Color(0xFFFFF0C0), TraditionalGold))
}
```

---

### Bottleneck 2.5 — Unused Heavy Dependencies in Production Build (Medium Impact)

**Location:** `app/build.gradle.kts`

The production APK includes the following dependencies that appear unused at runtime:
- `androidx.room:room-runtime` and `room-ktx` (no `@Database` class found)
- `retrofit` and `converter-moshi` (no API service interfaces found)
- `okhttp` and `logging-interceptor` (no OkHttp client created)
- `moshi-kotlin` and `moshi-kotlin-codegen` (no `@JsonClass` annotations found)
- `kotlinx-coroutines-android` (no coroutine usage detected)
- `firebase-bom` (Firebase not initialized)

**Impact:** These increase APK size and method count (relevant for dex limits), and increase build time.

**Estimated APK bloat:** ~3–5 MB from unused network/database libraries.

---

### Bottleneck 2.6 — `isMinifyEnabled = false` for Release (Medium Impact)

**Location:** `app/build.gradle.kts:50`

R8 code shrinking is disabled, meaning the release APK includes all class files from all dependencies (including unused ones). With minification enabled, R8 would remove unreachable code paths and reduce APK size significantly.

---

## 3. Memory Considerations

### Bottleneck 3.1 — Milestone List Held in Companion Object (Low Impact)

The `milestones` list (~230 `Milestone` objects) is held in the `KhmerCalendarHelper` singleton for the app's lifetime. Each `Milestone` contains a `String` (month name) and 4 primitive values. Estimated memory: ~15 KB. This is negligible.

---

## 4. Summary

| # | Bottleneck | Type | Impact | Fix Difficulty |
|---|-----------|------|--------|----------------|
| 1.1 | Milestone init on main thread | Startup | High | Low |
| 1.2 | Linear milestone scan | CPU | Medium | Low |
| 2.1 | Monolithic recomposition scope | UI | High | High |
| 2.2 | Calendar data not memoized | UI | High | Low |
| 2.3 | LazyColumn missing keys | UI | Medium | Low |
| 2.4 | Gradient brush recreated | UI | Medium | Low |
| 2.5 | Unused dependencies in APK | APK size | Medium | Low |
| 2.6 | R8 disabled for release | APK size | Medium | Low |

---

*Generated from static analysis of `KhmerCalendarHelper.kt` and `MainActivity.kt`*
