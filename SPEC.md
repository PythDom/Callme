# Callme — Functional & Technical Specification (v0.2)

> Status: **decisions agreed** (see §8). Nothing is coded yet. **[DECISION n]** markers refer to §8.

## 1. Purpose

Callme is an Android app, distributed as a sideloaded APK, that **simulates an incoming phone call** on the user's own phone:

- at a **time chosen in advance**,
- appearing to come from a **contact chosen from the device's Favorites (starred contacts)**,
- looking **indistinguishable from a real incoming call**, including the contact's photo when one exists.

Typical use: an excuse to leave a meeting/date, a reminder that's hard to ignore, testing.

No real call is placed, no network or SIM is used, no data leaves the phone.

## 2. Scope

### In scope (v1)
- Pick a caller from starred contacts.
- Pick a date/time for the call (one-shot).
- Schedule, list, edit, cancel pending fake calls.
- At the scheduled time: ring + vibrate + full-screen incoming-call screen, even when the phone is locked, asleep, or the app is closed, and after a reboot.
- Answer / decline the call; after answering, show an in-call screen with a running timer and a hang-up button.
- APK build (signed release) installable on the user's phone.

### Out of scope (v1)
- Placing or receiving real calls, SMS, VoIP.
- Distribution on the Play Store.
- Faking calls on another person's device.
- Cloud sync / accounts.

## 3. User flows

### 3.1 Scheduling a call
1. User opens Callme → home screen lists upcoming fake calls (max 3; empty state + "Schedule a call" button).
2. "Schedule a call" → **Favorites picker**: list of starred contacts (photo/initials, name, number(s)).
   - If the contact has several numbers, user picks one (it's displayed on the call screen).
3. **Time picker**: date + time. Shortcuts: "in 1 min", "in 5 min", "in 15 min", "in 30 min".
4. Optional settings for this call: ringtone (default = system ringtone), vibration on/off, ring duration before "missed" (default 30 s).
5. Save → call appears in the home list with a countdown; user can edit or delete it.

### 3.2 The fake call
1. At the scheduled time the phone wakes, the screen turns on and the incoming-call screen appears over the lock screen.
2. It plays the ringtone and vibrates, rings only in Normal mode; in Vibrate, Silent or Do Not Disturb it vibrates only **[DECISION 3]**.
3. Displayed: contact photo (full-bleed or circle as the stock dialer does), name, number/label ("Mobile"), "Incoming call", answer & decline controls (swipe or buttons matching the stock look).
4. Outcomes:
   - **Answer** → in-call screen: photo, name, timer 00:00 counting up, mute/speaker/keypad buttons (cosmetic), red hang-up button. A looping "mumbling" voice clip plays in the earpiece **[DECISION 4]**.
   - **Decline** → screen closes, ringing stops.
   - **No action** for N seconds → stops ringing, posts a "Missed call from X" notification and calls back once, 2 min later **[DECISION 5]**.
   - Volume/power button press → silences the ringer (like a real call).

### 3.3 First launch / permissions
Guided onboarding that requests, with explanations, only what's needed (see §5.4). The app must clearly report if a permission is missing that would prevent the call from firing (e.g. exact alarms, full-screen intent).

## 4. "Looks like a real call" — two possible approaches **[DECISION 1]**

| | **A. Replica UI (own screen)** | **B. Native dialer UI (Telecom API)** |
|---|---|---|
| How | App shows its own full-screen activity that mimics the stock incoming-call screen. | App registers a `PhoneAccount` + `ConnectionService`; Android's own dialer (Google Phone, Samsung, …) shows the incoming call. |
| Realism | Very close, but it's a copy: it won't exactly match every OEM/Android version. We'd target one look (e.g. Google Phone / Pixel, or Samsung One UI). | It **is** the real call screen of that phone. Photo comes from the dialer's own contact lookup. |
| Drawbacks | Must design and maintain the look. | User must enable the "Callme" calling account in system settings (hidden on some OEMs); the dialer may show a small "via Callme" label; the call may appear in the system call log; behaviour varies per OEM. |
| Reliability | High, fully under our control. | Medium; depends on dialer/OEM. |

**Chosen: A (replica), Samsung One UI look.** B is not planned.

## 5. Technical design (proposal)

### 5.1 Stack
- Kotlin, Jetpack Compose (Material 3), single-module Gradle project.
- `minSdk` 34 (Android 14), `targetSdk` 35 (Android 15) **[DECISION 6]**.
- Persistence: Room (table `scheduled_call`: id, contactLookupKey, displayName, number, numberLabel, photoUri, triggerAtMillis, ringtoneUri, vibrate, status).
- No internet permission.

### 5.2 Contacts
- `READ_CONTACTS` permission.
- Query `ContactsContract.Contacts` with `STARRED = 1`; read `DISPLAY_NAME`, `PHOTO_URI` (high-res) / `PHOTO_THUMBNAIL_URI`, `LOOKUP_KEY`; numbers from `CommonDataKinds.Phone`.
- At ring time, re-resolve the contact by `LOOKUP_KEY` so a changed photo/name is up to date; fall back to the stored snapshot if the contact was deleted.
- No photo → initials on a coloured background (as stock dialers do).

### 5.3 Scheduling
- `AlarmManager.setAlarmClock()` (most reliable; survives Doze; exact). Requires `SCHEDULE_EXACT_ALARM` (user-granted on Android 14+) — or `USE_EXACT_ALARM` which is acceptable since we're not on the Play Store.
- `BOOT_COMPLETED` / `MY_PACKAGE_REPLACED` / time-zone-change receivers re-register pending alarms.
- Alarm receiver → starts a foreground service (type `phoneCall`/`shortService` per API level) that plays the ringtone and posts a **full-screen-intent notification** (category `CALL`) launching the incoming-call activity.
- Incoming-call activity: `setShowWhenLocked(true)`, `setTurnScreenOn(true)`, excluded from recents.
- Note: some OEMs (Xiaomi, Huawei, Samsung "deep sleep") kill background alarms; onboarding will link to battery-optimisation exemption.

### 5.4 Permissions
| Permission | Why |
|---|---|
| `READ_CONTACTS` | List favorites, photos |
| `POST_NOTIFICATIONS` (13+) | Full-screen call notification, missed-call notification |
| `USE_FULL_SCREEN_INTENT` (14+: user must allow in settings for sideloaded apps) | Show call screen over lock screen |
| `SCHEDULE_EXACT_ALARM` / `USE_EXACT_ALARM` | Ring at the exact time |
| `RECEIVE_BOOT_COMPLETED` | Survive reboot |
| `FOREGROUND_SERVICE` (+ type permission) | Ring reliably |
| `VIBRATE`, `WAKE_LOCK` | Vibrate, wake the device |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` (optional) | Reliability on aggressive OEMs |

### 5.5 Build & delivery
- `./gradlew assembleRelease` → signed APK.
- GitHub Actions workflow builds the APK on each push and attaches it as an artifact (and to a GitHub Release on tags), so the user can download it straight to the phone.
- Signing key stored as GitHub secret (never committed).

## 6. Non-functional requirements
- Call fires within ±5 s of scheduled time when the device is idle/locked.
- Ringing starts < 2 s after the alarm.
- Works offline; no analytics; no data leaves the device.
- App UI language: English **[DECISION 7]**.

## 7. Acceptance criteria (v1)
1. A starred contact with a photo, scheduled 1 min ahead, rings with the photo shown, phone locked and screen off, app swiped away.
2. Same after a reboot between scheduling and ring time.
3. Contact without photo shows initials.
4. Answer → timer runs; hang-up closes. Decline closes immediately.
5. Ringer mode is respected per decision 3.
6. Cancelling a scheduled call guarantees it never fires.
7. APK installs on the target phone via sideload.

## 8. Decisions (agreed 2026-11-08)

1. **UI approach: A — replica screen, Samsung One UI look** (Samsung Phone app incoming-call and in-call screens).
2. **Recurrence: one-shot only.** At most **3 pending calls** at once; the "Schedule" button is disabled (with an explanation) when 3 are pending.
3. **Ringer mode:** Normal → ringtone (+ vibration if the system "vibrate while ringing" setting is on). Vibrate, Silent or Do Not Disturb → **vibrate only**, never sound.
4. **After answering:** play a looping **"mumbling" voice clip** (unintelligible speech-like audio, bundled with the app, royalty-free/generated) through the **earpiece** (switchable to speaker with the cosmetic speaker button).
5. **Missed call:** if unanswered after the ring duration, post a Samsung-style **"Missed call" notification**, then **call back once**, 2 minutes later by default. If that second call is also missed → a second missed-call notification, no more retries. Nothing is written to the real call log. The retry doesn't count toward the 3-pending limit.
6. **Target device: Samsung, Android 14+** → `minSdk 34`, `targetSdk 35`. Onboarding includes Samsung-specific steps (exclude from "Sleeping apps" / set battery to "Unrestricted").
7. **Language: English only.**
8. **No quick trigger** (no widget / quick-settings tile).
