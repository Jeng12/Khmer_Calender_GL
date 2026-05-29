# API Key Security Recommendations — Khmer Calendar GL

## Executive Summary

The project uses the **Secrets Gradle Plugin** to inject `GEMINI_API_KEY` from a `.env` file into `BuildConfig`. While this keeps the key out of source control, the compiled key is still embedded in the APK and can be extracted by reverse engineering. This report documents the current approach and provides layered security recommendations.

---

## 1. Current Implementation Analysis

### 1.1 .env.example

```
GEMINI_API_KEY=MY_GEMINI_API_KEY
```

The placeholder `MY_GEMINI_API_KEY` is the default value used when no `.env` file exists. The actual key should be placed in `.env` (gitignored).

### 1.2 Secrets Gradle Plugin Configuration

**File:** `app/build.gradle.kts`
```kotlin
secrets {
    propertiesFileName = ".env"
    defaultPropertiesFileName = ".env.example"
}
```

**How it works:**
1. At build time, the plugin reads key-value pairs from `.env`
2. Values are injected into `BuildConfig` as compile-time string constants
3. The runtime APK contains the literal key value in its compiled code

### 1.3 Risk Assessment

| Risk | Severity | Notes |
|------|----------|-------|
| Key extracted from APK by decompiling `classes.dex` | High | Any `BuildConfig` string constant is readable with `jadx` or `apktool` |
| `.env` committed to git accidentally | Critical | Even if later deleted from history, the key is compromised |
| Key logged by `HttpLoggingInterceptor` | Medium | The `logging-interceptor` dependency is included; ensure `Authorization` headers are redacted |
| Key visible in CI/CD logs | Medium | If the build prints `BuildConfig.GEMINI_API_KEY` during compilation |

---

## 2. Recommendations (Layered Defense)

### 2.1 Immediate — Gitignore Protection (Required)

Verify `.gitignore` contains:
```
.env
local.properties
*.jks
debug.keystore
my-upload-key.jks
```

Run `git status` to confirm `.env` is not tracked. If it was previously committed:
```bash
git rm --cached .env
git commit -m "Remove .env from tracking"
```

---

### 2.2 Short-Term — Firebase App Check (Strongly Recommended)

Enable **Firebase App Check** with the Play Integrity API. This ensures only legitimate, unmodified versions of your app can use your Firebase project's resources:

```kotlin
// Application.kt onCreate()
Firebase.appCheck.installAppCheckProviderFactory(
    PlayIntegrityAppCheckProviderFactory.getInstance()
)
```

```kotlin
// build.gradle.kts — uncomment firebase-ai
implementation(libs.firebase.ai)
```

App Check does not prevent key extraction, but it ensures extracted keys cannot be used from unauthorized clients (non-Play-signed APKs, scripts, etc.).

---

### 2.3 Short-Term — API Key Restrictions via Google Cloud Console

In [Google AI Studio / Google Cloud Console](https://console.cloud.google.com/apis/credentials):

1. Open your Gemini API key
2. Under **Application restrictions**: select **Android apps**
3. Add your app's package name (`com.aistudio.khmercalendar.fksajr`) and SHA-1 certificate fingerprint
4. Under **API restrictions**: restrict to **Generative Language API** only

This prevents the key from being used in any app other than yours (even if extracted).

---

### 2.4 Medium-Term — Move API Calls to a Backend Proxy (Best Practice)

For production apps, the gold standard is to **never embed API keys in the client**:

```
Android App → Your Backend API → Gemini API
```

**Implementation:**
1. Create a lightweight backend (Cloud Run, Firebase Functions, or any server)
2. Store the Gemini API key only on the server
3. The Android app authenticates to your backend using Firebase Auth user tokens
4. Your backend validates the token, then forwards the request to Gemini

```kotlin
// Backend handles the Gemini key — app only sends a user token
val response = retrofit.create(YourBackendApi::class.java)
    .getAuspiciousExplanation(
        authToken = firebaseUser.getIdToken(false).await().token,
        request = AuspiciousRequest(lunarMonth, lunarDay)
    )
```

---

### 2.5 Medium-Term — Obfuscation via ProGuard/R8 (Defense in Depth)

The release build currently has `isMinifyEnabled = false`. Enable R8 minification for release builds:

```kotlin
// app/build.gradle.kts
release {
    isMinifyEnabled = true
    isShrinkResources = true
    proguardFiles(
        getDefaultProguardFile("proguard-android-optimize.txt"),
        "proguard-rules.pro"
    )
}
```

While R8 does not encrypt string literals, it makes reverse engineering significantly harder by obfuscating class and method names.

---

### 2.6 CI/CD — Secrets Management

For GitHub Actions (or other CI):

**Do NOT** add `GEMINI_API_KEY` as a plain environment variable in workflow YAML files. Instead:

1. Store the key in **GitHub Secrets** (Settings → Secrets and variables → Actions)
2. Reference it in the workflow:
```yaml
env:
  GEMINI_API_KEY: ${{ secrets.GEMINI_API_KEY }}
```
3. The Secrets Gradle Plugin will pick it up via the `GEMINI_API_KEY` environment variable (it falls back to environment variables if `.env` is absent)

---

### 2.7 Debug Keystore Security

**File:** `debug.keystore.base64`

The base64-encoded debug keystore is committed to the repository. This is acceptable for debug builds (which cannot be published to the Play Store), but ensure:

- The **release keystore** (`my-upload-key.jks`) is **never** committed to git
- Release signing credentials (`KEYSTORE_PATH`, `STORE_PASSWORD`, `KEY_PASSWORD`) are only set via environment variables in CI
- The `KEYSTORE_PATH` default path in `build.gradle.kts` (`${rootDir}/my-upload-key.jks`) should be documented clearly

---

## 3. Security Checklist

| Item | Status | Priority |
|------|--------|----------|
| `.env` is gitignored | Verify | Critical |
| API key restricted to Android app in Google Console | Not done | High |
| Firebase App Check enabled | Not done | High |
| `HttpLoggingInterceptor` disabled in release builds | Verify | Medium |
| R8 minification enabled for release | Not done | Medium |
| Release keystore not in repository | Verify | Critical |
| Gemini API key moved to backend proxy | Not done | Low (future) |

---

## 4. HttpLoggingInterceptor Warning

The `logging-interceptor` dependency is included in the production build (not test-only). If OkHttp/Retrofit is ever used to make Gemini API requests with an `Authorization` header, request logs could expose the key.

**Recommendation:** Restrict `logging-interceptor` to debug builds only:

```kotlin
// app/build.gradle.kts
debugImplementation(libs.logging.interceptor)
// Remove: implementation(libs.logging.interceptor)
```

Or configure the interceptor to only log in debug:
```kotlin
if (BuildConfig.DEBUG) {
    client.addInterceptor(HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    })
}
```

---

*Generated from analysis of `.env.example`, `app/build.gradle.kts`, `gradle/libs.versions.toml`*
