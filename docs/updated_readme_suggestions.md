# README.md Enhancement Suggestions

This document proposes additions and improvements to the existing `README.md` to make it more comprehensive for new contributors and users.

---

## 1. Proposed Additions

### 1.1 Badges Section (after the title)

Add status badges for quick project health visibility:

```markdown
[![Build Status](https://github.com/Jeng12/Khmer_Calender_GL/actions/workflows/android.yml/badge.svg)](https://github.com/Jeng12/Khmer_Calender_GL/actions)
[![License: All Rights Reserved](https://img.shields.io/badge/license-ARR-red.svg)](LICENSE)
[![API Level](https://img.shields.io/badge/API-24%2B-brightgreen.svg)](https://developer.android.com/about/versions/nougat)
[![Kotlin](https://img.shields.io/badge/kotlin-2.2.10-blueviolet.svg)](https://kotlinlang.org/)
```

---

### 1.2 Expand the Features Section

Add descriptions of non-obvious features:

```markdown
## Features

- **Today Card** — Live lunar date, moon phase emoji, Buddhist Era (ព.ស.), and zodiac year
- **Monthly Calendar** — 7×6 grid with Khmer day abbreviations, lunar day overlays, 
  moon-phase indicators, and color-coded holiday/auspicious-day markers
- **Auspicious Days** — Filterable list of lucky days for weddings, housewarmings, 
  business openings, and travel, derived from traditional Khmer lunar calculations
- **Holidays** — 16 national and Buddhist public holidays with Khmer names and categories
- **Date Converter** — Convert any Gregorian date (2019–2036) to its full Khmer lunar 
  equivalent: lunar day, lunar month, Buddhist Era, zodiac animal, moon phase
- **Dark heritage theme** — Traditional gold, crimson, and lotus-pink palette
- **Calendar coverage** — 2019–2036, including the rare leap Asadha month in 2027 (BE 2571)
- **Bilingual UI** — Khmer script with English labels throughout
- **Authentication screens** — Demo login, register, and OTP verification flows
```

---

### 1.3 Expand Project Structure

Add explanation of what each file does:

```markdown
## Project Structure

```
Khmer_Calender_GL/
├── app/
│   └── src/
│       ├── main/
│       │   ├── java/com/example/
│       │   │   ├── KhmerCalendarHelper.kt   # Lunar calendar engine (Meeus algorithm,
│       │   │   │                            #   new moon calculation, leap year detection)
│       │   │   ├── MainActivity.kt          # All Compose UI screens & navigation state
│       │   │   └── ui/theme/
│       │   │       ├── Color.kt             # Material 3 color palette
│       │   │       ├── Theme.kt             # Dark/Light MaterialTheme setup
│       │   │       └── Type.kt              # Typography configuration
│       │   ├── res/                         # App resources (icons, strings, themes)
│       │   └── AndroidManifest.xml
│       ├── test/                            # JVM unit tests + Roborazzi screenshot tests
│       └── androidTest/                     # Instrumented tests (requires device/emulator)
├── docs/                                    # Technical documentation
│   ├── khmer_calendar_algorithm_documentation.md
│   ├── ui_ux_review_report.md
│   ├── gemini_api_usage_report.md
│   └── ...
├── gradle/
│   └── libs.versions.toml                   # Centralised dependency version catalog
├── .env.example                             # API key template (copy to .env)
├── debug.keystore.base64                    # Base64-encoded debug signing key
├── build.gradle.kts                         # Root build configuration
└── settings.gradle.kts                      # Project module settings
```
```

---

### 1.4 Add a Screenshots Section

After the Features section, add placeholder for screenshots:

```markdown
## Screenshots

| Home | Calendar | Auspicious Days |
|------|----------|----------------|
| ![Home Screen](docs/screenshots/home.png) | ![Calendar](docs/screenshots/calendar.png) | ![Auspicious](docs/screenshots/auspicious.png) |

| Date Converter | Holidays | Profile |
|---------------|----------|---------|
| ![Convert](docs/screenshots/convert.png) | ![Holidays](docs/screenshots/holidays.png) | ![Profile](docs/screenshots/profile.png) |

> Screenshots generated with `./gradlew recordRoborazziDebug`
```

---

### 1.5 Expand Troubleshooting Section

Add a common issues section before Contributing:

```markdown
## Troubleshooting

### Gradle sync fails with "Unsupported class file major version"
Ensure your JDK version is 11 or higher. In Android Studio:
`File → Project Structure → SDK Location → JDK Location`

### "GEMINI_API_KEY" not found during build
Create a `.env` file in the project root:
```bash
cp .env.example .env
# Then edit .env and replace MY_GEMINI_API_KEY with your actual key
```

### Screenshots tests fail with "Golden image not found"
Run the record task first to generate baseline images:
```bash
./gradlew recordRoborazziDebug
```

### App crashes on startup with "IllegalStateException: No date found for..."
The calendar covers years 2019–2036. Dates outside this range will show a fallback 
lunar date. This is expected behavior.

### Debug build signing error
The `debug.keystore.base64` file is decoded automatically. If you see a signing error:
1. Delete `debug.keystore` from the project root
2. Re-sync with Gradle — it will be regenerated from `debug.keystore.base64`

### Build fails with "Could not resolve com.google.firebase:firebase-bom"
Firebase BOM is included but Firebase services are not used. If you don't need Firebase,
you can remove `implementation(platform(libs.firebase.bom))` from `app/build.gradle.kts`.
```

---

### 1.6 Add Architecture Overview

```markdown
## Architecture

The app follows a **single-activity Compose architecture**:

```
MainActivity (ComponentActivity)
└── KhmerCalendarApp() — root composable
    ├── Authentication flow (Splash → Onboarding → Login/Register)
    └── Main app (Bottom navigation with 6 tabs)
        ├── Home tab
        ├── Calendar tab
        ├── Auspicious Days tab
        ├── Holidays tab
        ├── Date Converter tab
        └── Profile tab

KhmerCalendarHelper — stateless calendar computation engine
```

**Navigation:** Screen state is managed with `mutableState<AppScreen>` enum variables.
No Compose Navigation library is used.

**State management:** All UI state is held in `remember {}` blocks within composables.
No ViewModel layer currently (identified as a future improvement area).
```

---

### 1.7 Add Calendar Algorithm Section

```markdown
## Calendar Algorithm

The Khmer lunar calendar is implemented using the **Meeus new moon algorithm** 
(*Astronomical Algorithms*, Chapter 49) which provides sub-minute accuracy for new moon 
times. Key concepts:

- **Chaitra month detection:** The Khmer year begins with the first new moon between 
  March 15 and April 14 (corresponding to the traditional Khmer New Year around April 13–15)
- **Leap years:** Every 2–3 years, a 13th lunar month (second Asadha) is inserted to 
  keep the lunar calendar synchronized with the solar year. The next leap year is **2027**
- **Buddhist Era (BE):** `BE = Gregorian Year + 544`
- **Coverage:** 2019–2036 (extend by expanding the year range in `buildMilestones()`)

See [docs/khmer_calendar_algorithm_documentation.md](docs/khmer_calendar_algorithm_documentation.md) 
for full mathematical documentation.
```

---

### 1.8 Improve Contributing Guidelines

Expand the existing Contributing section:

```markdown
## Contributing

### Before You Start
- Check existing [issues](https://github.com/Jeng12/Khmer_Calender_GL/issues) to avoid 
  duplicate work
- For significant changes, open an issue first to discuss the approach

### Development Workflow
1. Fork the repository
2. Create a feature branch: `git checkout -b feature/your-feature-name`
3. Make your changes with clear, focused commits
4. Run the test suite: `./gradlew test`
5. For UI changes, record updated screenshots: `./gradlew recordRoborazziDebug`
6. Open a Pull Request against `main` with:
   - A description of what changed and why
   - Screenshots for UI changes
   - Reference to any related issues

### Code Style
- Follow [Kotlin coding conventions](https://kotlinlang.org/docs/coding-conventions.html)
- Use Khmer Unicode strings for Khmer text (not transliteration)
- Keep composables focused and under ~100 lines; extract to separate functions when larger

### Testing
- Add unit tests for any changes to `KhmerCalendarHelper.kt`
- Update screenshot tests for UI changes
```

---

## 2. Corrections to Existing Content

### 2.1 Incorrect AGP Version in Requirements Table

**Current:**
```markdown
| Android Gradle Plugin | 9.1.1 |
```

**Correct** (from `gradle/libs.versions.toml`):
```markdown
| Android Gradle Plugin | 9.2.1 |
```

### 2.2 Inaccurate Lunar Calendar Coverage

**Current:**
```markdown
The built-in milestone table covers **2025 – 2027**...
```

**Correct** (from `KhmerCalendarHelper.kt:128`):
```markdown
The built-in milestone table covers **2019 – 2036**...
```

### 2.3 Missing minSdk Mention

The README says "API 24 or higher" but does not mention this in the requirements table. Add:

```markdown
| Minimum Android SDK | API 24 (Android 7.0 Nougat) |
```

---

## 3. Full Proposed Structure

```markdown
# ប្រតិទិនចន្ទគតិខ្មែរ — Khmer Lunar Calendar

[Badges]

[Short description]

---

## Screenshots

---

## Features

---

## Architecture

---

## Calendar Algorithm

---

## Requirements

---

## Project Setup

---

## Project Structure

---

## Running Tests

---

## Key Dependencies

---

## Troubleshooting

---

## Contributing

---

## License
```

---

*Suggestions based on review of the existing `README.md` at `/home/user/Khmer_Calender_GL/README.md`*
