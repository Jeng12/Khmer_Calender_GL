# Khmer Calendar — Full Project Review

**Repository:** `jeng12/khmer_calender_gl`
**Reviewed:** 2026-06-05 · branch `claude/project-review-report-OwS8H`
**Scope:** Architecture, feature behaviour, the lunisolar calendar engine, code
quality, security, testing/CI, and prioritized recommendations.

---

## 1. Executive Summary

Khmer Calendar is a single-activity **Jetpack Compose** Android app that renders
the traditional Cambodian lunisolar calendar — moon phases, auspicious days,
Buddhist & national holidays, Gregorian↔Khmer date conversion — plus reminders/
alarms, per-day notes, two home-screen widgets, full Khmer/English localization
(including Khmer numerals), and a Gemini-powered "why is this day auspicious"
explainer.

It is roughly **6,900 lines of Kotlin across 26 main source files**, on a modern
toolchain (AGP 9.2.0, Kotlin 2.2.10, compileSdk 36, Compose BOM 2025.05).

**Overall health: solid B+.** The standout is the calendar engine: a genuine
astronomical implementation (Meeus new-moon algorithm + dynamic intercalation)
rather than a hardcoded lookup table, backed by a 49-case unit-test suite. The
weaker areas are app-level architecture hygiene: a placeholder package namespace,
an unwired authentication flow shipped as dead UI, holiday data defined in two
competing places, SharedPreferences used for everything (no Room/DataStore
despite both being staged in the build), and all UI state held in non-saveable
`remember` blocks.

| Top strengths | Top risks |
|---|---|
| Real astronomical calendar engine, well-tested | Holiday logic duplicated (engine **and** remote API) → drift |
| Clean networking with zero extra deps (`HttpURLConnection`) | `namespace = "com.example"` placeholder; mismatched applicationId |
| Robust alarm scheduling + boot persistence | 726-line auth flow is unreachable, unwired dead code |
| Thoughtful KM/EN localization + Khmer numerals | No input validation in the public calendar API |
| Working CI building a debug APK on every push/PR | UI state not `rememberSaveable` → lost on rotation/process death |

---

## 2. Architecture & Tech Stack

**Pattern.** Single `Activity` (`MainActivity`) hosting one Compose tree. The
entire app is a state machine inside `KhmerCalendarApp()`
(`ui/navigation/AppNavigation.kt`): an `AppScreen` enum drives a top-level
`Crossfade` (splash → onboarding/login → main), and an `AppTab` enum drives the
six in-app tabs. **There is no ViewModel layer** despite
`lifecycle-viewmodel-compose` being on the classpath — all screen state lives in
`remember { mutableStateOf(...) }` hoisted at the top composable and threaded
down via callbacks. It is plain state-hoisting, not MVVM/MVI.

**Toolchain (from `app/build.gradle.kts`, `gradle/libs.versions.toml`):**

| Item | Value |
|---|---|
| Android Gradle Plugin | 9.2.0 |
| Kotlin | 2.2.10 (AGP 9 built-in Kotlin; standalone `kotlin.android` deliberately *not* applied) |
| compileSdk / targetSdk / minSdk | 36 / 36 / 24 |
| Java | 11 |
| Compose BOM | 2025.05.00 (Material 3) |
| Firebase BOM | 34.12.0 → `firebase-ai` (Gemini) |
| Glance App Widget | 1.1.1 |
| Coroutines | 1.10.2 |
| Test | JUnit 4.13.2, Robolectric 4.16.1, Roborazzi 1.62.0 |
| applicationId | `com.aistudio.khmercalendar.fksajr` |
| namespace | `com.example`  ⚠️ placeholder |

**Package map (`app/src/main/java/com/example/`):**

```
MainActivity.kt            entry point; warms up calendar engine off-thread
alarm/                     scheduleAlarm + AlarmReceiver + BootReceiver
calendar/                  KhmerCalendarHelper.kt — the calendar engine
core/                      Localization.kt — KM/EN strings, Khmer numerals
data/                      HolidayRepository (remote) + GeminiRepository (AI)
ui/
  navigation/AppNavigation.kt   screen+tab state machine (the app shell)
  tabs/                    Home, Calendar, Auspicious, Holidays, Convert, Profile
  auth/AuthScreens.kt      splash/onboarding/login/register/OTP/forgot (unwired)
  components/              CustomBottomBar, CustomDatePicker
  theme/                   Color, KhmerPalette, Theme, Type
widget/                    KhmerCalendarWidget, KhmerAgendaWidget, WidgetPrefs, WidgetTheme
```

Largest files (refactor candidates): `AuthScreens.kt` (726), `CalendarTab.kt`
(628), `CustomDatePicker.kt` (534), `ConvertTab.kt` (532),
`KhmerCalendarWidget.kt` (516).

**Manifest** (`app/src/main/AndroidManifest.xml`): permissions for `INTERNET`,
`ACCESS_NETWORK_STATE`, `POST_NOTIFICATIONS`, `SCHEDULE_EXACT_ALARM`,
`USE_EXACT_ALARM`, `RECEIVE_BOOT_COMPLETED`, `VIBRATE`. One activity; receivers
for `AlarmReceiver` (not exported), `BootReceiver`, and the two widget providers
(exported, as widget providers must be). No services, no content providers.

---

## 3. Feature-by-Feature Review

**Home tab** — "today" card with the live lunar date, moon phase, zodiac, BE
year; shortcuts into other tabs.

**Calendar tab** (`ui/tabs/CalendarTab.kt`) — monthly grid with moon emoji and
holiday markers, day selection, swipe-to-change-month gestures, "go to today",
and a **per-day notes editor**. Notes persist in SharedPreferences file
`khmer_calendar_notes` keyed `"YEAR_MONTH_DAY"`; saving triggers a widget
refresh.

**Convert tab** (`ConvertTab.kt`) — Gregorian→Khmer converter driven by a custom
date picker; calls `KhmerCalendarHelper.getKhmerDate`.

**Auspicious tab** — filterable list of "lucky" days for the visible month, with
a category (wedding/housewarming/business/travel) and an optional Gemini
explanation per day.

**Holidays tab** — Cambodian public holidays fetched from a remote API
(`HolidayRepository`), filterable National vs Buddhist.

**Profile tab** — settings: dark-mode toggle, KM/EN language, widget
language/theme overrides, log-out. All persisted to `khmer_calendar_prefs`.

**Authentication flow** (`ui/auth/AuthScreens.kt`, 726 lines) — splash,
onboarding, login, register, OTP, forgot-password screens. **These are visual
mock-ups with no backend** (no Firebase Auth dependency exists) and are
**currently unreachable**: the splash `LaunchedEffect` jumps straight to
`MAIN_APP` after 1.8 s (`AppNavigation.kt:110–116`). Note that `onLogOut` routes
to `AppScreen.LOGIN`, which would strand the user on the dead login screen.

**Reminders / alarms** (`alarm/`) — `scheduleAlarm` uses `AlarmManager` with
`setExactAndAllowWhileIdle`, gated by `canScheduleExactAlarms()` on API 31+ with
a `setWindow` fallback; `POST_NOTIFICATIONS` is requested at runtime in the
Compose layer. `AlarmReceiver` posts a high-importance, full-screen, alarm-sound
notification. Alarms are persisted as JSON in `khmer_calendar_alarms`, and
`BootReceiver` re-schedules future alarms (pruning past ones) on
`BOOT_COMPLETED` / `LOCKED_BOOT_COMPLETED`. This is a genuinely robust
implementation. One limitation: `requestCode = year*10000 + month*100 + day`
allows **only one reminder per calendar day** — a second reminder overwrites the
first.

**Widgets** (`widget/`) — two Glance widgets: a calendar widget and an agenda
widget that lists the month's notes (now scrollable per the latest commit).
`WidgetPrefs` shares the app's SharedPreferences as a single source of truth and
exposes `refresh()`, called on app start and after note edits.

**Localization** (`core/Localization.kt`) — `AppLanguage` (KM/EN) provided via a
`CompositionLocal`, month-name tables, and a Khmer-numeral converter
(`toKhmerNumeral`). Switching is instant and persisted.

**Gemini explainer** (`data/GeminiRepository.kt`) — `Firebase.ai(googleAI())`
with `gemini-2.0-flash` (temp 0.7, max 300 tokens). `explainAuspiciousDay`
builds a culturally-aware bilingual prompt and returns a `Result<String>` so the
UI degrades gracefully on missing key / no network.

**Persistence summary.** Everything is **SharedPreferences**:
`khmer_calendar_prefs` (language, dark mode, widget settings),
`khmer_calendar_alarms` (alarm JSON), `khmer_calendar_notes` (date→text). Room
and DataStore are present in the version catalog but **commented out** in
`build.gradle.kts` — staged, not used.

---

## 4. Calendar Engine Deep-Dive (`calendar/KhmerCalendarHelper.kt`)

This is the heart of the project and the strongest code in it. It computes the
Khmer date *astronomically* rather than from a static table.

1. **New-moon times** — `newMoonJDE(k)` implements Jean Meeus, *Astronomical
   Algorithms* ch. 49: base term `2451550.09766 + 29.530588861·k` plus the full
   set of periodic corrections (E, M, M′, F, Ω). `k=0` ≈ Jan 6.6 2000 UT.
2. **JDE → Gregorian** — `jdeToGregorian` shifts to Cambodia time (UTC+7, no DST)
   and applies the standard Julian/Gregorian day-number inversion.
3. **Serial day** — `getSerialDay` is a proleptic continuous day counter (Jan/Feb
   folded into the prior year; `(153·(m−3)+2)/5` cumulative-month formula). The
   `(m−3)` detail is explicitly documented as a fix for a Feb→March weekday bug.
4. **Intercalation** — `buildMilestones(1900, 2200)` finds every "Chaitra" new
   moon (new moon in the **Mar 15–Apr 14** window ≈ Khmer New Year). Between two
   consecutive Chaitra new moons there are 12 months (normal) or 13 (leap — the
   split Asadha / អាសាឍ ១ & ២). Each lunar *month* becomes a `Milestone`
   (start serial day, name, true length, BE, zodiac).
5. **Lookup** — `findMilestone` binary-searches the milestone list (O(log n));
   month results are memoized in a 12-entry LRU (`LinkedHashMap`).
6. **BE & zodiac** — `BE = Gregorian(Chaitra) + 544`;
   `zodiac = ZODIAC_NAMES[((BE % 12) + 4 + 12) % 12]` (BE 2570 → Horse, correct
   for 2026–27).

**Correctness verdict — the engine is sound.** An earlier concern that
`offset % 30` "assumes 30-day months" is **overstated**: because milestones are
per-*month*, `offset` is always the number of days since the current lunar
month's new moon and is naturally bounded by that month's real length (29 or 30,
since the next milestone takes over). The waxing/waning split
(`displayLunarDay = isWaxing ? offset+1 : offset−14`) therefore yields the
correct 1–15 កើត / 1–14-or-15 រោច behaviour, and 29-day months correctly omit
the 15ث រោច. The `length` field on `Milestone` is computed but currently unused —
harmless, though it could replace the `% 30` to make intent explicit.

**Real limitations of the engine:**
- **No input validation** — `getKhmerDate(2026, 2, 30)` computes a wrong date
  silently rather than rejecting it.
- **Chaitra window is a fixed approximation** (Mar 15–Apr 14). The true Khmer
  New Year is set by the Hora almanac; edge years could in principle disagree by
  a day. No test pins this against an authoritative source.
- **Holiday detection is hardcoded here** (13 Gregorian/lunar rules) *and also*
  fetched remotely by `HolidayRepository` — two sources of truth (see §5). Khmer
  New Year is hardcoded `Apr 14..16`, but the civil holiday dates vary (13–16).
- **Auspicious rules are unsourced** — `isAuspicious = offset%30 ∈
  {2,6,10,11,18,25}` and the category via `displayLunarDay % 4` are arbitrary and
  not tied to documented Khmer astrology.
- **Out-of-range dates fail silently** — outside 1900–2200 the engine falls back
  to a single hardcoded milestone instead of erroring.

---

## 5. Findings (severity-ranked)

| # | Sev | Area | Finding | Suggested fix |
|---|-----|------|---------|---------------|
| 1 | Med | Build | `namespace = "com.example"` placeholder; differs from `applicationId com.aistudio.khmercalendar.fksajr`. Generic package risks collisions and looks unprofessional. | Rename package/namespace to `com.aistudio.khmercalendar`. |
| 2 | Med | Data | Holidays defined twice: hardcoded in `KhmerCalendarHelper` **and** remote `HolidayRepository`. They will drift. | Pick one source of truth (prefer data-driven), or clearly layer "fixed religious" vs "remote civil". |
| 3 | Med | Engine | No input validation in `getKhmerDate` / `getSerialDay`. | Validate ranges; throw or return a sentinel for invalid input. |
| 4 | Med | UX/Arch | Auth flow (726 lines) is unreachable dead code; `onLogOut` routes to the dead login screen. | Either wire real auth (Firebase Auth) or remove the flow and fix logout. |
| 5 | Med | State | All UI state is `remember { mutableStateOf }`, not `rememberSaveable`. Calendar position, conversion inputs, filters are lost on rotation / process death. | Use `rememberSaveable` (or hoist into a ViewModel + SavedStateHandle). |
| 6 | Sec | Secrets | `debug.keystore.base64` is committed (decoded at build time). It's the standard android-debug key (low risk), but the pattern is worth flagging; the release keystore is correctly env-injected. Gemini key relies on Firebase/AI-Studio injection — confirm it is never bundled in the APK. | Document key flow; keep release secrets in CI only; consider Firebase App Check. |
| 7 | Med | Reminders | `requestCode = year*10000+month*100+day` → only one alarm per day; a second reminder silently overwrites the first. | Add a per-reminder discriminator (time/uuid) to the request code. |
| 8 | Low | i18n | Only 3 string resources (`app_name`, two widget descriptions); most UI text is inlined in code via the `Localization` tables. Works, but bypasses Android resource i18n and accessibility tooling. | Long-term: migrate user-facing strings to resources or keep the central table but document the choice. |
| 9 | Low | Culture | Auspicious set `{2,6,10,11,18,25}` and `%4` category mapping are arbitrary/unsourced (engine §4). | Source from a Khmer almanac or label as heuristic in the UI. |
| 10 | Low | Build hygiene | `versionCode=1` / `versionName="1.0"` never bumped across many feature commits; large blocks of commented-out deps (Room, Retrofit, Moshi, CameraX, Coil). | Bump versions per release; prune or document staged deps. |
| 11 | Low | Tests | Unit tests exercise ~2019–2036 only; the 1900–2200 range and the silent out-of-range fallback are untested. | Add boundary + known-almanac-date assertions. |

---

## 6. Testing & CI

- **`app/src/test/java/com/example/KhmerCalendarHelperTest.kt`** (~500 lines, ~49
  cases): numerals, serial days, day-of-week, lunar day/phase, zodiac, month
  names, holidays, auspicious days, leap-year (2027 split Asadha), edge cases.
  This is the project's quality anchor.
- **Screenshot testing** via Roborazzi (`GreetingScreenshotTest`) + Robolectric
  (`isIncludeAndroidResources = true`), plus the boilerplate `ExampleUnitTest` /
  `ExampleRobolectricTest`.
- **CI** (`.github/workflows/build.yml`): on push to `main`/`claude/**` and PRs to
  `main`, sets up JDK 21 + Android SDK 36, decodes the debug keystore, writes
  `.env` from a `GEMINI_API_KEY` secret, builds the debug APK, and uploads the
  APK + build log as artifacts. No test/lint gate in CI — only assembly.

**Gap:** CI builds but does not run `testDebugUnitTest` or lint. Adding the test
task would make the strong unit suite actually protective.

---

## 7. Prioritized Recommendations

**Short-term (low effort, high value)**
1. Fix the namespace/applicationId mismatch (#1).
2. Add input validation to the calendar API (#3).
3. Decide a single holiday source of truth (#2).
4. Run unit tests + lint in CI (§6 gap).

**Medium-term**
5. Resolve the auth flow — wire it or remove it, and fix logout routing (#4).
6. Switch UI state to `rememberSaveable` or introduce ViewModels (#5).
7. Give reminders unique request codes (#7).
8. Bump version metadata and prune commented-out dependencies (#10).

**Long-term**
9. Move holidays/auspicious rules to a data-driven, almanac-sourced model and
   widen test coverage to the full date range (#2, #9, #11).
10. Migrate persistence from SharedPreferences to Room/DataStore (already staged
    in the catalog) once notes/reminders grow structured.
11. Harden the Gemini key path (App Check / API restrictions) per the existing
    `docs/api_key_security_recommendations.md`.

---

## 8. Relationship to existing `docs/`

The repo already contains eight focused notes under `docs/` (algorithm,
api-key-security, gemini-usage, dependency-review, performance, optimization,
ui-ux, readme-suggestions). This report consolidates and **verifies** them
against the current code — notably correcting the "30-day month" concern (the
engine is correct, §4) and surfacing app-level issues those notes don't cover
(placeholder namespace, dead auth flow, non-saveable state, holiday
duplication).
