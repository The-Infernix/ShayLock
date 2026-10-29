# ShayLock

[![Android](https://img.shields.io/badge/Platform-Android-3DDC84?logo=android&logoColor=white)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0.21-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-Material%203-4285F4?logo=jetpackcompose&logoColor=white)](https://developer.android.com/jetpack/compose)
[![Min SDK](https://img.shields.io/badge/Min%20SDK-26%20(Android%208.0)-orange)](https://developer.android.com/about/versions/oreo/android-8.0)
[![Target SDK](https://img.shields.io/badge/Target%20SDK-35-blue)](https://developer.android.com/about/versions/15)
[![License](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)
[![Website](https://img.shields.io/badge/Website-shaylock.vercel.app-black)](https://shaylock.vercel.app/)

A strict, dark-themed screen-time blocker for Android. Pick any installed app, give each one an independent daily allowance, and ShayLock covers it with a full-screen lock when that allowance runs out. A user-defined PIN protects overrides and every setting change.

**[Download from the official website](https://shaylock.vercel.app/)** | **[Latest APK](https://github.com/The-Infernix/ShayLock/releases/latest/download/ShayLock.apk)**

## Features

- **Multi-app protection** - Choose any installed, launchable app from the built-in picker
- **Independent timers** - Every protected app has its own allowance, usage counter and override budget
- **Fixed daily allowances** - 10s (testing), 5 min, 15 min, 30 min, 45 min, or 1 hour per app
- **One limit change per app per day** - The first configuration is free; later changes need the PIN and a daily cooldown
- **Accurate usage tracking** - Only counts time while that app is actually in the foreground; lock-screen time is never charged
- **Full-screen lock overlay** - A modal overlay intercepts Back/Home/Recents while an app is blocked
- **User-defined PIN** - Stored as a salted PBKDF2-HMAC-SHA256 hash; the legacy `2310` PIN is only seeded for upgrades
- **Limited PIN unlocks** - 3 unlocks per app per day; each unlocks that one app for 5 minutes
- **Chrome web block** - While an Instagram package is protected, `instagram.com` in Chrome is blocked and shares Instagram's override budget
- **System status & recovery** - Live permission badges, a Fix Permissions shortcut and a lock-screen preview
- **Automatic midnight reset** - Usage, PIN unlocks and the limit-change cooldown reset at midnight
- **Service resilience** - Foreground overlay service, accessibility watchdog, and WorkManager health checks
- **Polished dark UI** - Custom Material 3 theme built entirely in Jetpack Compose

## How It Works

1. **Grant permissions** - Enable the Accessibility Service and "Display over other apps" in system settings
2. **Pick apps** - Search the installed-app picker and add as many apps as you want to protect
3. **Set allowances** - Each app starts at 30 minutes; adjust any app before starting protection for free
4. **Start protection** - Arm the blocker; usage is tracked per app whenever it is in the foreground
5. **Lock on exhaustion** - When an app's allowance runs out, the full-screen lock covers only that app
6. **Unlock with PIN** - Your PIN opens one blocked app for 5 minutes, up to 3 times per app per day
7. **Change settings with PIN** - Changing an allowance, removing an app or turning protection off needs the PIN

## Permissions Required

| Permission | Purpose | Required |
|------------|---------|----------|
| **Accessibility Service** | Monitors the foreground app and tracks per-app usage | Yes |
| **Display over other apps (SYSTEM_ALERT_WINDOW)** | Shows the lock overlay above a blocked app | Yes |
| **Notifications** | Ongoing status and health warnings (Android 13+) | Optional |

## Build & Install

### Prerequisites
- Android Studio Ladybug (2024.2) or newer
- JDK 17
- Android SDK with API 35

### Build from Android Studio
1. Open the project in Android Studio
2. Sync Gradle
3. Connect a physical device with USB debugging enabled
4. Run the app

### Build from Command Line
```bash
cd ShayLock
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Tech Stack

- **Language**: Kotlin
- **UI**: Jetpack Compose + Material 3
- **Architecture**: Service-based (AccessibilityService + Overlay Service)
- **State Management**: StateFlow + Room + SharedPreferences
- **Database**: Room (KSP code generation)
- **Navigation**: Navigation Compose
- **Background Work**: WorkManager
- **Min SDK**: 26 (Android 8.0)
- **Version**: 2.0.0

## Project Structure

```
com.example.shaylock
├── core/           # BlockPolicy, BlockController, TrackedApp, DailyLimit, PinHasher
├── data/           # Room database, DAO, entities, PinStore
├── service/        # Accessibility service, overlay service, health worker
├── util/           # Installed-app catalog, app labels
├── ui/             # Compose screens, components, theme
└── MainActivity.kt # Entry point
```

## Limitations

- Chrome detection depends on the URL bar being exposed to the Accessibility Service
- The Chrome web rule is Instagram-specific; other sites are not filtered
- Gesture navigation may bypass some key swallowing on some devices
- Usage counter is wall-clock based and shifts if the system clock is changed
- Clearing app data or uninstalling removes all restrictions

## Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.
