# Google Play Compliance Checklist

Audit date: 2026-06-28

## Official References

- Google Play target API requirements: https://support.google.com/googleplay/android-developer/answer/11926878?hl=en
- Google Play User Data policy: https://support.google.com/googleplay/android-developer/answer/10144311?hl=en
- Google Play Data safety form guidance: https://support.google.com/googleplay/android-developer/answer/10787469?hl=en
- Google Play permissions and sensitive APIs: https://support.google.com/googleplay/android-developer/answer/16558241?hl=en
- Google Play app review / App content declarations: https://support.google.com/googleplay/android-developer/answer/9859455?hl=en
- Google Play AI-generated content policy: https://support.google.com/googleplay/android-developer/answer/13985936?hl=en
- Google Play Families policy: https://support.google.com/googleplay/android-developer/answer/9893335?hl=en
- Google Play ads policy: https://support.google.com/googleplay/android-developer/answer/9857753?hl=en
- Google Play malware policy: https://support.google.com/googleplay/android-developer/answer/9888380?hl=en
- Google Play device and network abuse policy: https://support.google.com/googleplay/android-developer/answer/16559646?hl=en
- Android exact alarm guidance: https://developer.android.com/develop/background-work/services/alarms

## App Facts Reviewed

- Package: `com.aistudio.khmercalendar.fksajr`
- Target SDK: 36
- Main data stores: SharedPreferences JSON files and `profile_picture.jpg`
- Remote API: `https://api-calender-sigma.vercel.app/api/v1`
- AI provider: Firebase AI / Gemini for auspicious-day explanations
- Permissions after this review: `INTERNET`, `ACCESS_NETWORK_STATE`, `POST_NOTIFICATIONS`, `SCHEDULE_EXACT_ALARM`, `RECEIVE_BOOT_COMPLETED`, `VIBRATE`

## App-Side Changes Made

- Removed `USE_EXACT_ALARM`; kept `SCHEDULE_EXACT_ALARM` because user-created reminder timing is core calendar functionality and the app already falls back to inexact alarms if exact access is unavailable.
- Added first-run database-sync disclosure with choices to keep sync enabled or turn it off.
- Added Profile > Privacy & Data controls: database sync toggle, data-use explanation, permission explanation, and local data deletion.
- Added local deletion for notes, reminders, custom holidays, work schedules, AI reports, profile image, and related local preferences. Reminder alarms are canceled before local data is cleared.
- Added in-app AI response reporting for generated auspicious-day explanations.
- Removed normal navigation into fake login/register flows. The app now opens directly because there is no production account backend.
- Excluded SharedPreferences and `profile_picture.jpg` from Android backup/device transfer rules.
- Added Robolectric coverage for cloud-sync preference, local data deletion, and AI report persistence.

## Policy Checklist

| Policy area | Applies? | Current status | Verification and required action |
| --- | --- | --- | --- |
| Target API level | Yes | Pass | `targetSdk = 36`, above Google Play's API 35+ new-app/update requirement for 2025+. |
| User Data policy | Yes | App-side mitigated; Play/owner action required | In-app disclosure and local deletion are implemented. Data is sent over HTTPS. A hosted privacy policy must accurately disclose notes, reminders/events, work schedules, custom holidays, profile image locality, Firebase AI prompts, and API sync. |
| Data Safety section | Yes | Play Console action required | Complete the Data Safety form. Declare user-entered app activity/content that can be synced to the API; AI provider handling; encryption in transit; deletion/control options; no sale of data. |
| Privacy Policy | Yes | Play Console action required | Add an active privacy policy URL in Play Console and an equivalent in-app/store listing disclosure. The app currently has in-app disclosure but not a hosted URL in this repo. |
| Permissions and sensitive APIs | Yes | Pass with declaration review | Permission set is scoped to network sync/AI, notifications, reminder alarms, boot re-arm, and vibration. `USE_EXACT_ALARM` was removed. `SCHEDULE_EXACT_ALARM` should be justified as user-facing calendar reminders if Play Console requests a declaration. |
| Notifications | Yes | Pass | `POST_NOTIFICATIONS` is requested only when creating reminders or shift reminders on Android 13+. |
| Exact alarms | Yes | Pass with Play review risk | The app calls `canScheduleExactAlarms()` and falls back to `setWindow()` if exact access is unavailable. Keep store listing focused on calendar/reminder functionality. |
| Background location / foreground service | No | Not applicable | No location permission and no foreground service declaration found. |
| AI-generated content | Yes | Pass app-side; operational action required | In-app reporting was added for AI responses. Production moderation should periodically review stored reports and use them to improve filtering/moderation. |
| Ads policy | No | Not applicable | No ads SDK or ad UI found. Declare "No ads" unless ads are added later. |
| Families / target audience | Potential | Declare carefully | App is a general calendar tool. If children are included in target audience, Families requirements apply. Recommended Play Console declaration: not primarily directed to children unless product owner intentionally changes positioning. |
| Health / medical | No | Not applicable | No health or medical advice/features found. |
| Financial services | No | Not applicable | No financial products, payments, crypto, or lending features found. |
| Malware / deceptive behavior | Yes | Pass | No hidden downloads, SMS/call billing, phishing, rooting, or surveillance behavior found. Network usage is tied to calendar API/AI functionality and disclosed in-app. |
| Device and network abuse | Yes | Pass | Network calls are direct HTTPS API/AI requests, not background abuse. API writes only happen through user calendar actions and respect the sync toggle. |
| Spam / minimum functionality | Yes | Pass | App provides real calendar, notes, reminders, holidays, conversion, widgets, and work schedule features. |
| Account deletion / sign-in details | No after fix | Pass | Fake login/register is no longer reachable in normal app flow. No restricted sign-in details are needed for review. |
| App signing / release security | Yes | Needs release setup | Release signing reads keystore credentials from env vars. Before Play submission, configure Play App Signing/upload key securely and do not commit real keystores or passwords. |
| Backup / restore data handling | Yes | Pass | SharedPreferences and local profile image are excluded from backup/device transfer rules to reduce unintended personal-data movement. |

## Remaining Production Actions

1. Publish a privacy policy URL and add it in Play Console and app listing.
2. Complete Data Safety, target audience, ads, content rating, and any permissions declarations in Play Console.
3. Decide whether the public calendar API is acceptable for real users. For production-grade privacy, the API should add authentication, per-user scoping, server-side deletion, and abuse/rate limiting.
4. Review AI reports operationally. The app now stores reports locally; a production backend endpoint would make developer moderation stronger.
