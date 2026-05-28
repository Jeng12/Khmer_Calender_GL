# ប្រតិទិនចន្ទគតិខ្មែរ — Khmer Lunar Calendar

A beautiful traditional Khmer Lunar Calendar Android app featuring real-time moon phases, auspicious dates, Buddhist holidays, and Gregorian ↔ Khmer date conversion.

---

## Features

- **Today Card** — Live lunar date, moon phase emoji, Buddhist Era (ព.ស.), and zodiac year
- **Monthly Calendar** — Grid view with lunar day overlays, moon-phase indicators, holiday and auspicious-day markers
- **Auspicious Days** — Filterable list of lucky days for weddings, housewarmings, business openings, and travel
- **Holidays** — National and Buddhist public holidays in Cambodia
- **Date Converter** — Convert any Gregorian date to its Khmer lunar equivalent
- **Dark heritage theme** — Traditional gold, crimson, and lotus-pink palette

---

## Requirements

| Tool | Minimum version |
|------|----------------|
| Android Studio | Ladybug (2024.2) or newer |
| Android Gradle Plugin | 9.1.1 |
| Kotlin | 2.2.10 |
| JDK | 11 |
| Android device / emulator | API 24 (Android 7.0) or higher |

---

## Project Setup

### 1. Clone the repository

```bash
git clone https://github.com/Jeng12/Khmer_Calender_GL.git
cd Khmer_Calender_GL
```

### 2. Open in Android Studio

1. Launch **Android Studio**.
2. Choose **File → Open** and select the `Khmer_Calender_GL` folder.
3. Wait for Gradle sync to complete. Android Studio will download all dependencies automatically.

### 3. Configure the API key

The app uses the Gemini API. Create a `.env` file in the **root** of the project (same level as `build.gradle.kts`):

```bash
cp .env.example .env
```

Then open `.env` and replace the placeholder with your real key:

```
GEMINI_API_KEY=YOUR_ACTUAL_GEMINI_API_KEY_HERE
```

> **Getting a key:** Visit [Google AI Studio](https://aistudio.google.com/app/apikey) and create a free API key.

### 4. Set up the debug signing config

The project includes a pre-configured debug keystore. If Android Studio flags a signing error, open `app/build.gradle.kts` and verify the `debug` block references `debugConfig`:

```kotlin
debug {
    signingConfig = signingConfigs.getByName("debugConfig")
}
```

The `debug.keystore.base64` file in the project root is decoded automatically by the build scripts — no manual step is required.

### 5. Run the app

- Select a connected device or start an emulator (API 24+).
- Click the **Run ▶** button in Android Studio, or run from the terminal:

```bash
./gradlew installDebug
```

---

## Project Structure

```
Khmer_Calender_GL/
├── app/
│   └── src/
│       ├── main/
│       │   ├── java/com/example/
│       │   │   ├── KhmerCalendarHelper.kt   # Lunar calendar logic & date conversion
│       │   │   └── MainActivity.kt          # All Compose UI screens & navigation
│       │   ├── res/                         # App resources (icons, themes, strings)
│       │   └── AndroidManifest.xml
│       ├── test/                            # Unit & screenshot tests (Roborazzi)
│       └── androidTest/                     # Instrumented tests
├── gradle/
│   └── libs.versions.toml                   # Centralised dependency versions
├── .env.example                             # API key template
├── build.gradle.kts                         # Root build config
└── settings.gradle.kts
```

---

## Running Tests

**Unit tests** (runs on the JVM, no device needed):

```bash
./gradlew test
```

**Screenshot tests** with Roborazzi:

```bash
./gradlew recordRoborazziDebug   # record golden screenshots
./gradlew verifyRoborazziDebug   # compare against goldens
```

**Instrumented tests** (requires a connected device or emulator):

```bash
./gradlew connectedAndroidTest
```

---

## Key Dependencies

| Library | Purpose |
|---------|---------|
| Jetpack Compose + Material 3 | UI framework |
| AndroidX Room | Local database |
| Retrofit + Moshi | Network & JSON parsing |
| Kotlin Coroutines | Async operations |
| Firebase BOM | Firebase services |
| Roborazzi | Screenshot testing |
| Secrets Gradle Plugin | Secure API key injection from `.env` |

---

## Lunar Calendar Coverage

The built-in milestone table covers **2025 – 2027** (including the leap Asadha month in 2027). Dates outside this range fall back gracefully but may show approximate lunar data. Extend `KhmerCalendarHelper.kt` → `milestones` to add future years.

---

## Contributing

1. Fork the repo and create a feature branch: `git checkout -b feature/your-feature`
2. Commit your changes with a clear message.
3. Open a Pull Request against `main`.

---

## License

This project is for educational and personal use. See `LICENSE` for details if present, otherwise all rights reserved by the author.
