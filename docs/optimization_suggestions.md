# Optimization Suggestions — Khmer Calendar GL

This document provides actionable code-level optimization recommendations based on the bottlenecks identified in `performance_bottleneck_report.md`.

---

## 1. Algorithm Optimizations (KhmerCalendarHelper.kt)

### 1.1 Binary Search for Milestone Lookup

**Current code** (`KhmerCalendarHelper.kt:192`):
```kotlin
val ms = milestones.lastOrNull { it.serialDay <= sDay }
    ?: Milestone(getSerialDay(2026, 5, 11), "ពិសាខ", 30, 2570, ZODIAC_NAMES[6])
```

**Optimized code:**
```kotlin
private val fallbackMilestone = Milestone(
    getSerialDay(2026, 5, 11), "ពិសាខ", 30, 2570, ZODIAC_NAMES[6]
)

private fun findMilestone(sDay: Int): Milestone {
    var lo = 0
    var hi = milestones.size - 1
    while (lo < hi) {
        val mid = (lo + hi + 1) / 2
        if (milestones[mid].serialDay <= sDay) lo = mid else hi = mid - 1
    }
    return if (lo >= 0 && milestones[lo].serialDay <= sDay) milestones[lo]
           else fallbackMilestone
}
```

**Change in `getKhmerDate`:**
```kotlin
val ms = findMilestone(sDay)
```

**Benefit:** Reduces per-lookup cost from O(n≈230) to O(log n≈8) comparisons.

---

### 1.2 Background Warm-Up for Milestone Initialization

Add a `warmUp()` function to force lazy initialization off the main thread:

```kotlin
// KhmerCalendarHelper.kt — add to companion object
fun warmUp() {
    milestones  // accessing the lazy property initializes it
}
```

Call it from `Application.onCreate()` or early in `MainActivity.onCreate()`:

```kotlin
// MainActivity.kt or Application.kt
override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    lifecycleScope.launch(Dispatchers.Default) {
        KhmerCalendarHelper.warmUp()
    }
    // ... rest of onCreate
}
```

**Benefit:** Moves the ~1-50ms initialization off the main thread, eliminating any startup jank.

---

## 2. Compose Recomposition Optimizations (MainActivity.kt)

### 2.1 Memoize Monthly Calendar Data

In `CalendarTabContent()`, wrap the `getGregorianMonthDays()` call with `remember`:

```kotlin
@Composable
fun CalendarTabContent(year: Int, month: Int, ...) {
    val monthDays = remember(year, month) {
        KhmerCalendarHelper.getGregorianMonthDays(year, month)
    }
    // use monthDays for rendering
}
```

**Benefit:** Calendar data is recomputed only when year or month changes, not on every recomposition.

---

### 2.2 Hoist Gradient Brushes Out of Composables

**Before:**
```kotlin
@Composable
fun SomeCard() {
    Box(
        modifier = Modifier.border(
            1.dp,
            Brush.linearGradient(listOf(TraditionalGold, Color(0xFFFFF0C0), TraditionalGold))
        )
    ) { ... }
}
```

**After:**
```kotlin
private val goldBorderGradient = Brush.linearGradient(
    listOf(TraditionalGold, Color(0xFFFFF0C0), TraditionalGold)
)

@Composable
fun SomeCard() {
    Box(
        modifier = Modifier.border(1.dp, goldBorderGradient)
    ) { ... }
}
```

Alternatively, if the brush depends on composable state:
```kotlin
val goldGradient = remember { 
    Brush.linearGradient(listOf(TraditionalGold, Color(0xFFFFF0C0), TraditionalGold))
}
```

**Benefit:** Avoids object allocation on every recomposition frame.

---

### 2.3 Add Stable Keys to LazyColumn Items

**Before:**
```kotlin
LazyColumn {
    items(auspiciousDays) { day ->
        AuspiciousDayCard(day)
    }
}
```

**After:**
```kotlin
LazyColumn {
    items(
        items = auspiciousDays,
        key = { day -> "${day.year}-${day.month}-${day.day}" }
    ) { day ->
        AuspiciousDayCard(day)
    }
}
```

**Benefit:** Compose can reuse existing item compositions when the list changes (e.g., when a filter chip is toggled), instead of re-composing the entire list.

---

### 2.4 Extract State to ViewModel

Move calendar state out of the monolithic `KhmerCalendarApp()` composable:

```kotlin
class CalendarViewModel : ViewModel() {
    private val _year = MutableStateFlow(LocalDate.now().year)
    private val _month = MutableStateFlow(LocalDate.now().monthValue)
    
    val year: StateFlow<Int> = _year.asStateFlow()
    val month: StateFlow<Int> = _month.asStateFlow()

    val monthDays: StateFlow<List<KhmerDate>> = combine(_year, _month) { y, m ->
        withContext(Dispatchers.Default) {
            KhmerCalendarHelper.getGregorianMonthDays(y, m)
        }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    fun prevMonth() { /* update _year, _month */ }
    fun nextMonth() { /* update _year, _month */ }
}
```

**Benefit:** Reduces recomposition scope; ViewModel survives configuration changes.

---

## 3. Build Optimizations (app/build.gradle.kts)

### 3.1 Enable R8 Minification for Release

```kotlin
release {
    isMinifyEnabled = true
    isShrinkResources = true
    proguardFiles(
        getDefaultProguardFile("proguard-android-optimize.txt"),
        "proguard-rules.pro"
    )
}
```

**Benefit:** Reduces APK size by removing unused code from all dependencies. Estimated size reduction: 2–4 MB.

---

### 3.2 Move Logging Interceptor to Debug-Only

```kotlin
// Remove: implementation(libs.logging.interceptor)
// Add:
debugImplementation(libs.logging.interceptor)
```

**Benefit:** Removes network logging code from release builds; reduces APK size by ~200 KB.

---

### 3.3 Remove or Lazily Declare Unused Dependencies

Dependencies currently included but with no runtime usage:
```kotlin
// Comment out until actually used:
// implementation(libs.androidx.room.ktx)
// implementation(libs.androidx.room.runtime)
// implementation(libs.converter.moshi)
// implementation(libs.moshi.kotlin)
// implementation(libs.okhttp)
// implementation(libs.retrofit)
// implementation(libs.kotlinx.coroutines.android)  // only needed if coroutines are used
```

**Note:** Remove the corresponding `ksp` processors too if Room is removed:
```kotlin
// "ksp"(libs.androidx.room.compiler)
// "ksp"(libs.moshi.kotlin.codegen)
```

**Benefit:** Significantly reduces build time (KSP annotation processing is skipped) and APK method count.

---

## 4. Caching Strategy for Calendar Data

If the app is extended to cover more years or offer live data, implement a caching layer:

```kotlin
// Simple LRU cache for monthly data
private val monthCache = LruCache<Pair<Int,Int>, List<KhmerDate>>(12)

fun getGregorianMonthDays(year: Int, month: Int): List<KhmerDate> {
    val key = year to month
    return monthCache[key] ?: run {
        val computed = (1..daysInMonth(year, month)).map { getKhmerDate(year, month, it) }
        monthCache.put(key, computed)
        computed
    }
}
```

**Benefit:** Eliminates redundant calculation when user navigates back and forth between months.

---

## 5. Summary — Optimization Impact

| Optimization | Effort | APK Size | Startup | Runtime | Recompose |
|-------------|--------|----------|---------|---------|-----------|
| Binary search milestone lookup | Low | — | — | ✓ | — |
| Background warm-up | Low | — | ✓ | — | — |
| Memoize monthly data | Low | — | — | — | ✓ |
| Hoist gradient brushes | Low | — | — | — | ✓ |
| LazyColumn stable keys | Low | — | — | — | ✓ |
| ViewModel extraction | High | — | — | — | ✓ |
| Enable R8 minification | Low | ✓ | — | — | — |
| Debug-only logging | Low | ✓ | — | — | — |
| Remove unused deps | Medium | ✓ | ✓ | — | — |
| Month data LRU cache | Medium | — | — | ✓ | — |

---

*Based on bottlenecks identified in `performance_bottleneck_report.md`*
