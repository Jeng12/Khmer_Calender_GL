# Khmer Calendar — Development & Improvement Roadmap

**Companion to:** `PROJECT_REVIEW.md`
**Branch:** `claude/project-review-report-OwS8H` · **Last updated:** 2026-06-05

This roadmap turns the review findings into an actionable plan. It covers two
goals the project owner asked for:

1. **Complete the incomplete** — features that are designed/wired but not
   finished.
2. **Add useful new functions** — capabilities that meaningfully extend a Khmer
   calendar app.

It reflects three confirmed product decisions:

- **Auth is dropped.** The app stays open (no login). Profile name/email become
  editable and stored locally; the unwired login/register/OTP/forgot screens are
  retired.
- **Persistence migrates to Room** (already staged in the build) for notes,
  reminders, and the offline holiday cache.
- This document is a plan; each phase is reviewed before implementation.

---

## 0. Verified inventory of incomplete work

| Area | Current state | Gap |
|------|---------------|-----|
| Authentication | Full login/register/OTP/forgot UI exists (`ui/auth/AuthScreens.kt`, 726 lines) | No backend; **bypassed** at splash (`AppNavigation.kt:110–116`); OTP shows literal `842____`; profile name/email hardcoded (`Sophanit`) |
| Reminders | Create + schedule + boot-reschedule works (`alarm/*`) | **No list/edit/delete UI**; `requestCode = y*10000+m*100+d` → **one reminder per day** (collisions overwrite) |
| Notes | One plain string per day, key `YEAR_MONTH_DAY` (`CalendarTab.kt`) | No all-notes view, search, delete, title, or export |
| Sila notifications | Toggle saves `sila_notify` (`ProfileTab.kt`) | **No scheduling logic** — the toggle does nothing |
| Holidays | Remote fetch into a `@Volatile` in-memory cache (`HolidayRepository.kt`) | **No disk persistence** (lost on restart); no "last synced"; no upcoming-holiday alerts |
| Conversion | Gregorian→Khmer only (`getKhmerDate`) | **No Khmer→Gregorian reverse** |
| Engine | `Milestone.length` (29/30 days) is computed | **Never read anywhere** |
| Build | Room / DataStore / Retrofit / Moshi / OkHttp / Coil / CameraX / navigation-compose | All **commented out**, staged but unused |

---

## Guiding principles

- **Reuse, don't rebuild.** Lean on the existing engine and utilities:
  - `calendar/KhmerCalendarHelper.kt` — `getKhmerDate`, `getGregorianMonthDays`,
    `getSerialDay`, `toKhmerNumeral`
  - `core/Localization.kt` — `AppLanguage`, month-name tables, Khmer numerals
  - `alarm/` — `scheduleAlarm`, `AlarmReceiver`, `BootReceiver`
  - `widget/WidgetPrefs.refresh`, `data/HolidayRepository`, `data/GeminiRepository`
- **Keep the architecture.** Single-activity Compose with state hoisting;
  introduce ViewModels only where Room/async data flow justifies it.
- **Each phase ships independently** and leaves the app in a working state.

---

## Phase 0 — Foundation & hygiene  *(effort: S)*

Low-risk cleanups from `PROJECT_REVIEW.md §5/§7` that de-risk everything after.

- **Fix the package namespace.** Rename `com.example` →
  `com.aistudio.khmercalendar` to match `applicationId`. Touches
  `app/build.gradle.kts` (`namespace`), all package declarations, and relative
  names in `AndroidManifest.xml`.
- **Validate calendar input.** Guard `getKhmerDate` / `getSerialDay` against
  impossible dates (e.g. Feb 30) instead of silently computing a wrong result.
- **Single holiday source of truth.** Keep *fixed religious* days computed in the
  engine; take *civil/national* days from `HolidayRepository`. Remove the overlap.
- **Make CI protective.** Add `testDebugUnitTest` + lint to
  `.github/workflows/build.yml` (today it only assembles the APK, so the strong
  unit suite never runs in CI).
- **Survive rotation/process death.** Switch the top-level screen/tab/selection
  state in `AppNavigation.kt` from `remember` to `rememberSaveable`.
- **Housekeeping.** Bump `versionCode`/`versionName`; prune commented-out deps
  that are not on this roadmap.

---

## Phase 1 — Persistence foundation (Room)  *(effort: M)*

Everything in Phase 2 depends on real storage.

- Enable Room + KSP in `app/build.gradle.kts` and `gradle/libs.versions.toml`.
- New package `data/db/` with entities + DAOs:
  - `NoteEntity(dateKey, title?, body, updatedAt)`
  - `ReminderEntity(id, triggerMs, title, message, repeatRule, enabled)`
  - `HolidayEntity(date, nameKh, nameEn, type, description?, notes?, isFixed, lastSyncedAt)`
- Repository wrappers over the DAOs (mirroring the existing repository style).
- **One-time migration** importing current SharedPreferences data
  (`khmer_calendar_notes`, `khmer_calendar_alarms`) into Room on first launch.
- Keep SharedPreferences only for lightweight settings (language, theme, widget
  prefs via `WidgetPrefs`).

---

## Phase 2 — Complete the incomplete features  *(effort: L)*

1. **Reminder management screen.** List / edit / delete reminders; allow
   **multiple reminders per day** by keying `requestCode` on a unique reminder
   id (fixes the collision in `alarm/Alarm.kt`). Optional repeat
   (daily / weekly / lunar). Reuse `scheduleAlarm`; extend `BootReceiver` to
   reschedule from Room.
2. **All-notes view.** A searchable, deletable list with jump-to-date; upgrade
   notes to title + body + timestamp. Reuse the `CalendarTab` editor; add an
   entry point from Home.
3. **Sila-day notifications — finish the dead toggle.** When `sila_notify` is on,
   schedule notifications on Buddhist sabbath days (8 & 15 កើت, 8 & 14/15 រោច).
   This is exactly where the unused **`Milestone.length`** becomes useful — pick
   the correct final waning Sila for 29- vs 30-day months. Reuse
   `AlarmReceiver` / WorkManager.
4. **Local profile (auth retirement).** Make name/email editable and persisted;
   remove the unwired `LOGIN/REGISTER/OTP/FORGOT` screens (or hide behind a build
   flag) and simplify `onLogOut`. Optionally surface the already-built
   **Onboarding** screen on first launch (currently unreachable).
5. **Offline holidays.** Persist `HolidayRepository` results to Room with a
   "last synced" timestamp and a manual refresh; serve from cache when offline.

---

## Phase 3 — New useful functions  *(effort: L)*

- **Khmer → Gregorian reverse conversion.** Add
  `getGregorianDate(be, lunarMonthName, lunarDay, isWaxing)` to
  `KhmerCalendarHelper`: locate the matching milestone, add the day offset, and
  invert `getSerialDay` (new `serialToGregorian` helper). Add a direction toggle
  to `ConvertTab`.
- **Upcoming holiday / auspicious-day notifications.** A WorkManager job that
  alerts N days ahead (user-configurable). Builds on Phase 1 Room data.
- **Share & export.** Share a styled "today" date card (image/text); export notes
  and reminders to JSON/CSV.
- **Birthday → zodiac & age calculator.** Enter a birth date → animal year, age,
  and the next birthday's Khmer date. Pure reuse of the engine.
- **Sila-day markers on the calendar grid** plus a quick search / jump-to-date.
- **Widget enhancements.** Tap a day to open it in-app; additional widget sizes.

---

## Phase 4 — Quality, accessibility & i18n  *(effort: M)*

- **String resources.** Extract user-facing text to resources for true Android
  i18n / RTL / TalkBack (today only 3 string resources exist; the rest is
  inlined).
- **Accessibility.** Add `contentDescription`s; verify dynamic font scaling.
- **Tests.** Cover reverse conversion, Sila detection, boundary years (the engine
  fallback path), and Room DAOs; add screenshot tests for the new screens.

---

## Suggested sequencing & effort

```
Phase 0 (S) ──► Phase 1 (M) ──► Phase 2 (L) ──► Phase 3 (L) ──► Phase 4 (M)
```

**Recommended first milestone:** Phase 0 + the reminder/notes management slices
of Phase 2 — highest user-visible value for the least risk, on top of the Room
foundation.

---

## Key files this roadmap touches

| Concern | Files |
|---|---|
| Calendar engine | `app/src/main/java/com/example/calendar/KhmerCalendarHelper.kt` |
| App shell / navigation | `app/src/main/java/com/example/ui/navigation/AppNavigation.kt` |
| Auth UI (to retire) | `app/src/main/java/com/example/ui/auth/AuthScreens.kt` |
| Reminders | `app/src/main/java/com/example/alarm/{Alarm,AlarmReceiver,BootReceiver}.kt` |
| Notes UI | `app/src/main/java/com/example/ui/tabs/CalendarTab.kt` |
| Profile / settings | `app/src/main/java/com/example/ui/tabs/ProfileTab.kt` |
| Data layer | `app/src/main/java/com/example/data/{HolidayRepository,GeminiRepository}.kt` |
| Build / deps | `app/build.gradle.kts`, `gradle/libs.versions.toml` |
| CI | `.github/workflows/build.yml` |
