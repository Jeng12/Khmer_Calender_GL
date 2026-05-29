# Gemini API Usage Report — Khmer Calendar GL

## Executive Summary

A complete analysis of all Kotlin files in the `app` module reveals that **the Gemini API is not currently integrated into the application runtime code**. The project declares the infrastructure for Gemini API use (environment variable injection, Firebase AI SDK dependency) but no actual API calls are present in the codebase. This report documents the current state and identifies where the Gemini API could be integrated.

---

## 1. Current Gemini API Infrastructure

### 1.1 API Key Configuration

**File:** `.env.example`

```
GEMINI_API_KEY=MY_GEMINI_API_KEY
```

The project uses the **Secrets Gradle Plugin** (`com.google.android.libraries.mapsplatform.secrets-gradle-plugin`) to inject the API key from a `.env` file into `BuildConfig` at compile time.

**Configuration in `app/build.gradle.kts`:**
```kotlin
secrets {
    propertiesFileName = ".env"
    defaultPropertiesFileName = ".env.example"
}
```

This means `BuildConfig.GEMINI_API_KEY` is available at runtime for any Kotlin code that imports `BuildConfig`.

### 1.2 Firebase AI SDK Dependency

**File:** `app/build.gradle.kts`

```kotlin
// implementation(libs.firebase.ai)
```

The `firebase-ai` library (which wraps the Gemini API through Firebase) is declared in `gradle/libs.versions.toml` but is **commented out** and not included in the build. The Firebase BOM is included (`platform(libs.firebase.bom)`), but no Firebase services are initialized.

### 1.3 Networking Infrastructure

The following networking dependencies are declared and included (though not used for Gemini):
- **Retrofit 2.12.0** + **Moshi 1.15.2**: REST client with JSON parsing
- **OkHttp 4.10.0** + **Logging Interceptor**: HTTP client with request logging
- **Kotlin Coroutines 1.10.2**: Async support

---

## 2. API Call Inventory

**Result: Zero API calls found.**

A search of all Kotlin files in the `app` module for Gemini-related patterns (`gemini`, `GenerativeModel`, `firebase.ai`, `BuildConfig.GEMINI_API_KEY`, `com.google.ai`, `GenerateContentRequest`) returned no matches.

The application is currently entirely offline:
- Calendar calculations are performed locally by `KhmerCalendarHelper.kt`
- Authentication screens are demo-only (no backend)
- No HTTP requests are made at runtime

---

## 3. Identified Opportunities for Gemini API Integration

The following features could be meaningfully enhanced with Gemini API:

### 3.1 Auspicious Day Explanations

**Screen:** Auspicious Days tab  
**Current:** Shows category badge ("Wedding", "Housewarming") with no explanation  
**Enhancement:** Use Gemini to generate culturally rich explanations of why a specific day is auspicious, referencing lunar day position, zodiac year, and traditional Khmer ceremony guidelines

**Suggested Prompt Template:**
```
You are a Khmer cultural calendar expert. Today is [lunarDayName] of [lunarMonthName] 
in Buddhist Era [BE], year of the [zodiac]. Explain in Khmer and English why this is 
auspicious for [category], referencing traditional Khmer beliefs.
```

### 3.2 Buddhist Holiday Context

**Screen:** Holidays tab  
**Current:** Displays holiday name and date only  
**Enhancement:** Gemini can provide a paragraph about the religious or historical significance of each holiday when tapped

### 3.3 Lunar Calendar Chatbot

**Screen:** New dedicated screen or floating button  
**Enhancement:** A conversational AI assistant that answers questions like:
- "ថ្ងៃល្អសម្រាប់ពិធីមង្គលការ ខែក្រោយ?" (Good days for a wedding next month?)
- "តើ ១៥ កើត ពិសាខ ២៥៧០ ត្រូវនឹងថ្ងៃខែណា?" (What Gregorian date is 15th waxing Visakha 2570?)

### 3.4 Personalized Notifications

**Current:** Static Buddhist Sila notification toggle in Profile  
**Enhancement:** Gemini generates personalized reminders based on the user's calendar events and upcoming auspicious days

### 3.5 Image-Based Date Lookup

**Enhancement:** Allow users to photograph a traditional Khmer almanac page; use Gemini's multimodal capabilities to extract and convert the dates shown

---

## 4. Recommended Implementation Architecture

If Gemini API integration is added, the recommended architecture is:

```
User Action
    │
    ▼
ViewModel (coroutine scope)
    │  launches
    ▼
GeminiRepository
    │  calls
    ▼
GenerativeModel (firebase-ai SDK or direct REST)
    │  returns Flow<GenerateContentResponse>
    ▼
ViewModel State Update
    │
    ▼
Compose UI (collectAsState)
```

### Sample Integration Code

```kotlin
// GeminiRepository.kt
class GeminiRepository {
    private val model = Firebase.ai(backend = GenerativeBackend.googleAI())
        .generativeModel(
            modelName = "gemini-2.0-flash",
            generationConfig = generationConfig { temperature = 0.7f }
        )

    suspend fun explainAuspiciousDay(khmerDate: KhmerDate): String {
        val prompt = buildAuspiciousPrompt(khmerDate)
        val response = model.generateContent(prompt)
        return response.text ?: "No explanation available"
    }
}
```

---

## 5. Potential Optimization Areas

If the Gemini API is enabled in future:

| Area | Recommendation |
|------|---------------|
| **Response caching** | Cache Gemini responses in Room database keyed by (lunarMonth, lunarDay, category) to avoid duplicate API calls for the same day |
| **Prompt engineering** | Include `systemInstruction` with Khmer cultural context to improve response quality |
| **Streaming responses** | Use `generateContentStream()` for long explanations to progressively render text |
| **Rate limiting** | Implement exponential backoff on `429 Resource Exhausted` errors |
| **Offline fallback** | Provide hardcoded fallback explanations when network is unavailable |
| **Cost control** | Use `gemini-2.0-flash` (cheaper) for simple explanations; `gemini-2.0-pro` only for complex queries |

---

## 6. Security Notes

- `BuildConfig.GEMINI_API_KEY` is compiled into the APK and can be extracted by decompilation. See `api_key_security_recommendations.md` for mitigation strategies.
- The `firebase-ai` SDK routes requests through Firebase App Check, which provides some level of API key protection.

---

*Generated from analysis of all Kotlin files in `app/src/main/java/com/example/`, `app/build.gradle.kts`, `.env.example`*
