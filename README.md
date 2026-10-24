# ShayLock — strict Instagram screen-time blocker

An Android prototype that limits how much Instagram (`com.instagram.android`) may be used per
day. The user picks a **daily allowance** (10 s … 5 h); the app measures how much of that
allowance is actually consumed and, once it is used up, a full-screen lock screen blocks
Instagram until midnight.

There is no PIN and no local bypass. Unlocking Instagram, pausing the block, changing the
allowance, clearing today's usage and turning the blocker off all require a **one-time code from
an accountability partner**. The code is requested through WhatsApp or SMS, expires after five
minutes, works exactly once, and is held in memory only.

Built with Kotlin, Jetpack Compose (Material 3), WorkManager and a service-based architecture. The
UI is deliberately dark-only, with a progressive-disclosure setup flow and a focused active
dashboard.

---

## 1. Requirements

| Item | Version |
|---|---|
| Android Studio | Ladybug (2024.2) or newer |
| JDK | 17 (bundled with Android Studio) |
| Gradle / AGP | 8.11.1 / 8.10.0 (via the wrapper) |
| compileSdk / targetSdk | 35 |
| minSdk | 26 (Android 8.0) |
| Device | Physical Android device, Android 8.0+ |

`minSdk 26` is required because the overlay uses `TYPE_APPLICATION_OVERLAY`, which only exists
from API 26 onwards.

## 2. Build and install

### Android Studio
1. `File > Open` and select the `ShayLock` folder (it is the Gradle root project).
2. Let the Gradle sync finish.
3. Connect the device over USB with **USB debugging** enabled.
4. Press **Run** (Shift+F10) and pick the device from the device list.

### Command line
```powershell
cd C:\NEXUS\ShayLock
.\gradlew :app:assembleDebug
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

The debug APK is written to `app\build\outputs\apk\debug\app-debug.apk`.

## 3. Grant the two system permissions

The app cannot request these at runtime; they must be granted in system settings. The setup
screen has a button for each one that jumps straight to the right settings page.

1. **Accessibility service** — open ShayLock, tap *Open settings* on the
   "Accessibility service" row, find **ShayLock Blocker** in the *Installed services* list and
   switch it **on**, then confirm the warning dialog.
2. **Display over other apps** — tap *Open settings* on the "Display over other apps" row and
   enable the switch for ShayLock.
3. **Notifications** (optional, Android 13+) — tap *Allow*. The blocker works without it; the
   foreground-service notification and the protection health warning are simply hidden.

The setup screen is progressive: it only shows the required permissions that are still missing,
and the optional notification row is only offered during onboarding. Once both required
permissions are active, the permission section collapses and the allowance picker appears. If the
accessibility row shows an amber *Connecting…* state, wait a moment — the app re-checks
automatically until the service is connected. Notifications can still be enabled later from the
footer link.

## 4. Set up an accountability partner

On the setup screen, tap **Accountability partner** and enter the partner's phone number with a
country code (for example `+1 555 123 4567`).

* The **first** partner can be saved directly.
* Changing an **existing** partner requires a code approved by the current partner.
* The number is stored locally in `SharedPreferences`; ShayLock itself has no backend and sends no
  data.

The partner must be reachable in WhatsApp or through the device's SMS app. When an approval is
needed, ShayLock opens that app with a pre-filled message containing the one-time code. The
partner replies with the code, and the user types it into ShayLock.

## 5. Test it

1. Pick a daily allowance (10 s / 15 min / 30 min / 1 h / 2 h / 3 h / 5 h) and press
   **Start blocking**. The very first setup needs **no partner approval** — the 10 s option is
   there to verify the whole flow quickly.
   → The allowance picker gives way to the active dashboard and the status card shows
   *"Protected / X left today"*.
2. Change the allowance again → the approval dialog opens → tap **Request partner code** →
   WhatsApp or SMS opens with a message for the partner → enter the code the partner sends back.
3. Open Instagram and leave it in the foreground for a while.
   → The status card shows a progress bar and *"X used / Y per day"*. The counter only advances
   while Instagram is really on screen.
4. Keep going until the allowance is used up.
   → A full-screen dark lock screen appears over Instagram showing
   **"Instagram is blocked"** with a numeric keypad and four code dots.
5. Press Back / Home / Recents → the overlay swallows them; Instagram cannot be interacted with
   behind the lock screen.
6. Tap **Request partner code** → the lock screen steps aside, WhatsApp or SMS opens, and the
   partner sends the code back. Return to Instagram to enter it.
7. Enter a wrong code → *"That code is not valid."* and the entry is wiped. The block stays up
   until the code expires.
8. Enter the correct code → the overlay closes, Instagram becomes usable for 5 minutes and the
   status card shows a *"PAUSED"* countdown.
9. Wait 5 minutes → the block is re-armed automatically; opening Instagram brings the lock
   screen back.
10. From the active dashboard, **Pause for 15 minutes** (partner code required) grants a longer
    break. The dashboard shows the countdown and **Resume blocking** ends it immediately. Usage
    keeps counting during the pause, so the block returns if the allowance is still exhausted.
11. **Reset today's usage** clears the counter (partner code required), **Turn the blocker off**
    disables the limiter entirely (partner code required).

**No Instagram installed?** Arm a rule, then use the *Preview the lock screen* button on the
active dashboard — it raises exactly the same overlay over ShayLock itself.

## 6. How it works

```
com.example.shaylock
├── ShayLockApp.kt                      Application: initialises state + schedules health work
├── MainActivity.kt                     Compose setup/dashboard + partner approval flow
├── core/
│   ├── AccountabilityController.kt     One-time codes, partner storage, WhatsApp/SMS deep links
│   ├── BlockPolicy.kt                  Blocked packages, allowances, session and pause durations
│   ├── DailyLimit.kt                   The daily allowance + today's usage (wall-clock based)
│   ├── BlockController.kt              Single source of truth + SharedPreferences persistence
│   ├── PermissionUtils.kt              Accessibility / overlay / install-state checks
│   └── TimeFormat.kt                   Duration, allowance and countdown formatting
├── service/
│   ├── BlockerAccessibilityService.kt  The watchdog (TYPE_WINDOW_STATE_CHANGED + key filter)
│   ├── OverlayService.kt               Foreground service that owns the lock window
│   ├── OverlayWindowHost.kt            The actual TYPE_APPLICATION_OVERLAY dialog window
│   ├── OverlayLifecycleOwner.kt        Lifecycle owner for the Compose view in the dialog
│   ├── OverlayController.kt            Facade used by the watchdog and the UI
│   └── ServiceHealthWorker.kt          WorkManager check that the watchdog is still enabled
└── ui/
    ├── screens/SetupScreen.kt          Progressive permissions + partner + dashboard
    ├── screens/OverlayScreen.kt        The lock screen with the partner-code keypad
    ├── screens/ApprovalDialog.kt       One-time-code dialog for protected settings
    ├── components/CodeKeypad.kt        Shared numeric keypad + code dots
    └── theme/                          Dark-only ink palette, type scale and shapes
```

* **Daily allowance** — `DailyLimit` holds the fixed allowance, the time consumed today and the
  day the usage belongs to. `rolledToDay()` resets only the usage counter at local midnight; the
  allowance itself never drifts.
* **Watchdog** — `BlockerAccessibilityService` listens for `TYPE_WINDOW_STATE_CHANGED`. It reports
  every foreground change to `BlockController`, which charges the allowance only while a blocked
  app is really on screen (the counter pauses when the user leaves the app and when the lock
  screen covers it). It also swallows `HOME`, `BACK` and `APP_SWITCH` key events while the
  overlay is visible (`flagRequestFilterKeyEvents` + `canRequestFilterKeyEvents` in
  `res/xml/accessibility_service_config.xml`).
* **Overlay** — `OverlayWindowHost` creates a `Dialog` with `TYPE_APPLICATION_OVERLAY`,
  `setCancelable(false)`, `setCanceledOnTouchOutside(false)` and a key listener that eats Back.
  A `Dialog` window is modal, so touches never reach Instagram behind it.
* **State** — `BlockController` holds the allowance, today's usage and the unlock session,
  persists all of them to `SharedPreferences` (so they survive process death) and exposes them as
  `StateFlow`s that the UI collects. A 1-second ticker keeps the counters accurate, rolls the day
  over at midnight and re-arms the block the moment a session expires.
* **Accountability** — `AccountabilityController` creates a four-digit code with a five-minute
  lifetime, keeps it in memory only, and opens `https://wa.me/<partner>?text=…` (WhatsApp) or an
  `smsto:` intent (SMS) with a pre-filled message. `verifyAndConsume()` checks the purpose and
  expiry and invalidates the code on success.
* **Approval gate** — `BlockController.applyDailyLimit()`, `setLimiterEnabled()`,
  `resetTodayUsage()`, `pauseFor()` and `unlockWithCode()` all consume a code for their specific
  purpose and refuse to act without it. The single exception is the first setup: while no
  allowance has ever been configured, the limit can be armed without approval so the user can get
  started.
* **Partner changes** — the first partner is saved directly; replacing an existing partner
  consumes a `CHANGE_PARTNER` code, so the user cannot redirect approvals to a number they
  control after setup.
* **Resilience** — the overlay service returns `START_STICKY`, reinitialises the persisted
  controllers after a process restart and asks the watchdog to re-evaluate the current window.
  `ServiceHealthWorker` runs every 15 minutes through WorkManager; if a rule is enabled but the
  accessibility service is off in system settings, it posts a high-priority notification that
  opens the accessibility settings screen. The warning is cleared once protection is healthy
  again.

## 7. Prototype limitations

* **The code is not a cryptographic secret.** The WhatsApp/SMS message is composed locally and
  pre-filled with the code, so a determined device owner can read it before sending. This is an
  accountability boundary, not a security boundary. A real product needs a backend that sends
  the code to the partner only after they approve.
* The partner phone number is stored locally and is not verified. It must include a country code.
* Only one accessibility service can hold the key-filtering capability at a time. If another
  key-filtering service is enabled, HOME/BACK may leak through; the overlay still re-arms
  whenever Instagram comes back to the foreground.
* On gesture-navigation devices the HOME key is usually not delivered to accessibility
  services at all, so the HOME gesture is not swallowed — the overlay window itself stays on
  top of the launcher.
* The usage counter uses wall-clock time, so changing the device clock shifts it. The allowance
  itself is unaffected.
* Usage is only counted while the accessibility watchdog is running. The WorkManager health check
  warns about a disabled service, but Android does not allow the app to re-enable it silently.
* The allowance is charged from the moment the limiter is switched on, so enabling it late in
  the day still gives the full allowance from that point on.
* WorkManager's minimum periodic interval is 15 minutes. A disabled accessibility service may
  therefore go unnoticed for up to ~15 minutes; Instagram itself remains blocked only when the
  watchdog is actually running.
* Only Instagram (`com.instagram.android`) and Instagram Lite (`com.instagram.lite`) are
  blocked. Add more package ids to `BlockPolicy.BLOCKED_PACKAGES` to extend it.
