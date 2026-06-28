# QA and Release Readiness Report

Audit date: 2026-06-28

## Scope

Reviewed native Android/Kotlin Compose app architecture, API/database integration, local persistence, permissions, Google Play readiness, build health, and high-risk user-data flows.

## Architecture Review

- UI: Compose single-activity app with tab-level screens for Home, Calendar, Auspicious Days, Holidays, Date Convert, Work Schedule, and Profile.
- Persistence: `AppStore` centralizes local SharedPreferences JSON for notes, reminders/events, holidays, schedules, settings, and AI reports.
- Remote API: `CalendarApiRepository` uses HTTPS `HttpURLConnection`, validates JSON responses, and falls back gracefully when API calls fail.
- Scheduling: `AlarmManager` one-shot reminders with exact-alarm fallback behavior and boot re-arm through `BootReceiver`.
- Widgets: Glance widgets refresh from local data and are refreshed after key data changes.
- AI: Firebase AI/Gemini generates optional auspicious-day explanations on tap.

## Implemented Improvements

- Permission minimization: removed `USE_EXACT_ALARM`.
- User-data disclosure: added first-run sync disclosure and Profile privacy panel.
- Data controls: added sync toggle and local-data deletion.
- AI safety: added in-app AI response reporting and local report persistence.
- Auth cleanup: removed normal navigation into mock login/register flows.
- Backup privacy: excluded local user data from backup/device transfer rules.
- Quality coverage: added `AppStorePrivacyTest` for the new data controls.
- Warning cleanup: switched deprecated Compose icons to AutoMirrored variants.

## Feature Priority

| Priority | Feature/fix | Status |
| --- | --- | --- |
| P0 | Event/reminder and work-schedule API sync | Implemented before this pass and retained behind the sync setting |
| P0 | Play user-data disclosure and local controls | Implemented |
| P0 | Remove overbroad exact alarm permission | Implemented |
| P0 | Remove fake auth from normal user flow | Implemented |
| P1 | AI generated-content reporting | Implemented locally |
| P1 | Backup exclusion for personal local data | Implemented |
| P1 | Hosted privacy policy and Play Console declarations | Owner action required |
| P1 | Authenticated per-user backend data scoping | Backend action recommended before real production use |
| P2 | Full instrumented UI automation on physical/emulated devices | Manual/device action pending if no emulator is available |

## Manual QA Checklist

- Fresh launch: splash opens the calendar and shows database-sync disclosure once.
- Profile: database-sync switch persists; privacy text is visible; local delete confirmation clears local notes/reminders/schedules/profile image.
- Calendar day dialog: add/edit/delete notes; add/delete reminders; verify local UI updates and widgets refresh.
- Work Schedule: configure shifts, assign days, save, delete all schedules.
- Auspicious Days: request AI explanation; report generated response.
- Offline/network failure: app should keep local saves and show "API database sync failed" toast when remote writes fail.
- Permissions: notification permission is requested only when scheduling reminders; exact alarm fallback should avoid crashes if special access is denied.

## Automated Verification Run

- `:app:compileDebugKotlin` passed.
- `:app:testDebugUnitTest` passed.
- `:app:lintDebug` passed with 0 errors. Remaining warnings are non-blocking cleanup items: dependency/update suggestions, KTX style suggestions, widget XML attributes ignored below API 31, unused legacy resources, and launcher-icon density warnings.
- `:app:assembleDebug` passed.
- `:app:installDebug` passed on emulator `emulator-5554`.
- Fresh-launch smoke test passed: app launched to foreground, first-run database-sync disclosure appeared, accepting it rendered the main calendar, and recent logcat had no app crash markers.
- Live API database checks passed against `https://api-calender-sigma.vercel.app/api/v1`:
  - event create/read/update/delete all returned true
  - work schedule save/read/clear all returned true

## Remaining Verification To Run Before Store Upload

- Release packaging: `:app:assembleRelease` reached release compile, R8 minify, resource shrink, and lint vital, then failed because `my-upload-key.jks` is not present locally. Configure `KEYSTORE_PATH`, `STORE_PASSWORD`, and `KEY_PASSWORD` or provide the upload key before Play upload.
- Device scenarios still worth running manually on a physical Android 13+ device:
  - notification permission allow/deny flows
  - exact alarm access denied path
  - boot receiver re-arm after reboot
  - widgets after data changes
  - first-run disclosure layout on smaller screens
- Re-run direct API database checks after any backend/API changes.

## Production Readiness Verdict

The app-side code is substantially stronger after this pass and currently builds/tests. It is suitable for internal testing. Play Store submission still requires hosted privacy policy, Data Safety declarations, target-audience/content-rating declarations, release signing setup, and a product decision on the public unauthenticated API. For real users, authenticated per-user API data scoping and server-side deletion are strongly recommended before production launch.
