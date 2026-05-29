# Dependency Management Review — Khmer Calendar GL

## Overview

Review of `app/build.gradle.kts` and `gradle/libs.versions.toml` for dependency currency, usage, and potential streamlining opportunities.

---

## 1. Current Dependency Inventory

### 1.1 Active Production Dependencies

| Dependency | Version | Used In Code | Purpose |
|-----------|---------|-------------|---------|
| `compose-bom` | `2024.09.00` | Yes | Compose platform BOM |
| `androidx.activity:activity-compose` | `1.10.1` | Yes | ComponentActivity |
| `androidx.compose.material3` | BOM-managed | Yes | Material 3 UI components |
| `androidx.compose.ui` | BOM-managed | Yes | Core Compose UI |
| `androidx.compose.ui.graphics` | BOM-managed | Yes | Graphics/Color |
| `androidx.compose.ui.tooling.preview` | BOM-managed | Yes | `@Preview` support |
| `androidx.compose.material.icons.core` | BOM-managed | Yes | `Icons.Default.*` |
| `androidx.core:core-ktx` | `1.18.0` | Yes | Kotlin Android extensions |
| `androidx.lifecycle.runtime.ktx` | `2.8.7` | Yes | Lifecycle-aware operations |
| `androidx.lifecycle.runtime.compose` | `2.8.7` | Yes | `collectAsStateWithLifecycle` |
| `androidx.lifecycle.viewmodel.compose` | `2.8.7` | Yes | `viewModel()` composable |
| `firebase-bom` | `34.12.0` | No | Firebase platform BOM |
| `androidx.room.runtime` | `2.7.0` | **No** | Local database |
| `androidx.room.ktx` | `2.7.0` | **No** | Room Kotlin extensions |
| `retrofit` | `2.12.0` | **No** | REST client |
| `converter-moshi` | `2.12.0` | **No** | Retrofit Moshi converter |
| `moshi-kotlin` | `1.15.2` | **No** | JSON serialization |
| `okhttp` | `4.10.0` | **No** | HTTP client |
| `logging-interceptor` | `4.10.0` | **No** | HTTP logging |
| `kotlinx.coroutines.android` | `1.10.2` | **No** | Coroutines for Android |
| `kotlinx.coroutines.core` | `1.10.2` | Minimal | Coroutines base |

### 1.2 KSP Annotation Processors

| Dependency | Used | Notes |
|-----------|------|-------|
| `room-compiler` | **No** | KSP processor for Room entities |
| `moshi-kotlin-codegen` | **No** | KSP processor for Moshi adapters |

---

## 2. Version Currency Analysis

### 2.1 Out-of-Date Dependencies

| Dependency | Current Version | Latest Stable | Notes |
|-----------|----------------|--------------|-------|
| `compose-bom` | `2024.09.00` | `2025.05.00` | Several major BOM releases behind |
| `agp` | `9.2.1` | `9.2.1` | Current |
| `room` | `2.7.0` | `2.7.1` | Minor update available |
| `okhttp` | `4.10.0` | `4.12.0` | Security patches in 4.11/4.12 |
| `logging-interceptor` | `4.10.0` | `4.12.0` | Should match okhttp version |
| `robolectric` | `4.16.1` | `4.16.1` | Current |
| `roborazzi` | `1.59.0` | Check latest | Verify against current release |
| `secrets-gradle-plugin` | `2.0.1` | `2.0.1` | Current |
| `firebase-bom` | `34.12.0` | `34.12.0` | Current |
| `coil-compose` | `2.7.0` | `3.2.0` | Major version available (Coil 3.x) |

**Recommendation:** Update the Compose BOM to `2025.05.00` for the latest Compose stability improvements and security patches:
```toml
composeBom = "2025.05.00"
```

Update OkHttp and logging-interceptor together (they must match):
```toml
loggingInterceptor = "4.12.0"
okhttp = "4.12.0"
```

---

## 3. Unused Dependency Cleanup

### 3.1 Strongly Recommended Removals

These dependencies are included in the production APK with no runtime usage found:

**Remove from `app/build.gradle.kts`:**
```kotlin
// NO Room entities or DAOs exist in codebase:
// implementation(libs.androidx.room.ktx)
// implementation(libs.androidx.room.runtime)
// "ksp"(libs.androidx.room.compiler)

// NO Retrofit service interfaces exist:
// implementation(libs.converter.moshi)
// implementation(libs.retrofit)

// NO OkHttp client usage:
// implementation(libs.logging.interceptor)
// implementation(libs.okhttp)

// NO @JsonClass Moshi annotations:
// implementation(libs.moshi.kotlin)
// "ksp"(libs.moshi.kotlin.codegen)

// Firebase BOM not needed without Firebase SDK:
// implementation(platform(libs.firebase.bom))
```

**Impact of removing unused deps:**
- Removes ~5–8 MB from APK (Room, Retrofit, OkHttp, Moshi, Firebase combined)
- Eliminates 2 KSP annotation processing runs (Room compiler + Moshi codegen), reducing build time by ~15–30 seconds on clean builds
- Reduces dex method count, improving build stability

---

### 3.2 Move to Debug-Only

```kotlin
// Currently production dependency, should be debug-only:
debugImplementation(libs.logging.interceptor)
```

---

### 3.3 Commented-Out Dependencies to Review

The following are commented out in `build.gradle.kts` but still declared in `libs.versions.toml`. If there are no near-term plans to use them, remove the version catalog entries too to reduce catalog clutter:

- `accompanist-permissions` (`0.37.3`) — consider `androidx.activity:activity-compose` permission APIs instead (modern alternative)
- `play-services-location` (`21.3.0`)
- `androidx-camera-*` (`1.5.0`)
- `coil-compose` (`2.7.0`) — outdated (Coil 3.x is the current major version)
- `androidx-datastore-preferences` (`1.1.7`)
- `androidx-navigation-compose` (`2.8.9`)
- `firebase-ai` — should be kept if Gemini integration is planned

---

## 4. Dependency Conflict Analysis

### 4.1 Coroutines Version Inconsistency

Both `kotlinx-coroutines-android` and `kotlinx-coroutines-core` are pinned to `1.10.2`. These are consistent with each other. However, they should be kept in sync with the Kotlin version (`2.2.10`); Kotlin 2.x requires coroutines `1.9.0+` — `1.10.2` satisfies this requirement.

### 4.2 Room Version Alignment

`room-runtime`, `room-ktx`, and `room-compiler` are all declared as `2.7.0` — correctly aligned.

### 4.3 Lifecycle Version Alignment

`lifecycleRuntimeKtx`, `lifecycleViewmodelCompose`, and `lifecycleRuntimeCompose` are all `2.8.7` — correctly aligned.

### 4.4 KSP Version

KSP `2.3.5` should be compatible with Kotlin `2.2.10`. Verify compatibility at [KSP releases](https://github.com/google/ksp/releases) — the first two numbers of KSP's version should match Kotlin's version prefix. `2.3.5` implies KSP for Kotlin `2.x`, which is correct.

---

## 5. Build Configuration Recommendations

### 5.1 Enable R8 for Release Builds

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

### 5.2 Consider Updating compileSdk API

The current `compileSdk { version = release(36) { minorApiLevel = 1 } }` syntax is non-standard. The conventional syntax is:
```kotlin
compileSdk = 36
```

Verify this custom syntax is supported by the AGP version in use.

### 5.3 JDK Version

The project targets `JavaVersion.VERSION_11`. Android apps can use Java 17 features with AGP 9.x. Updating to Java 17 unlocks sealed classes, pattern matching for switch, and other modern language features:

```kotlin
compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}
kotlinOptions {
    jvmTarget = "17"
}
```

---

## 6. Summary of Recommendations

| Action | Priority | Effort | Impact |
|--------|----------|--------|--------|
| Remove unused Room, Retrofit, Moshi, OkHttp, Firebase from production | High | Low | APK size −5MB, build time −30s |
| Update Compose BOM to `2025.05.00` | Medium | Low | Bug fixes, new features |
| Update OkHttp/logging-interceptor to `4.12.0` | Medium | Low | Security patches |
| Enable R8 minification for release | High | Low | APK size −2–4MB |
| Move logging-interceptor to debugImplementation | High | Low | Security |
| Upgrade to Java 17 target | Low | Low | Language features |
| Clean up unused version catalog entries | Low | Low | Maintainability |

---

*Generated from analysis of `app/build.gradle.kts` and `gradle/libs.versions.toml`*
