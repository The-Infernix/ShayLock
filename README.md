# ShayLock

[![Android](https://img.shields.io/badge/Platform-Android-3DDC84?logo=android&logoColor=white)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0.21-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-Material%203-4285F4?logo=jetpackcompose&logoColor=white)](https://developer.android.com/jetpack/compose)
[![Min SDK](https://img.shields.io/badge/Min%20SDK-26%20(Android%208.0)-orange)](https://developer.android.com/about/versions/oreo/android-8.0)
[![Target SDK](https://img.shields.io/badge/Target%20SDK-35-blue)](https://developer.android.com/about/versions/15)
[![License](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)
[![Website](https://img.shields.io/badge/Website-shaylock.vercel.app-black)](https://shaylock.vercel.app/)

A strict, dark-themed Instagram screen-time blocker for Android. ShayLock limits daily usage of Instagram (`com.instagram.android`) and Instagram Lite (`com.instagram.lite`), permanently blocks `instagram.com` in Chrome, locks the app behind a full-screen overlay when your allowance runs out, and protects critical actions with a PIN.

**[Download from the official website](https://shaylock.vercel.app/)** | **[Latest APK](https://github.com/The-Infernix/ShayLock/releases/latest/download/ShayLock.apk)**

## Features

- **Fixed daily allowances** - 10s (testing), 5 min, 15 min, 30 min, 45 min, or 1 hour
- **One limit change per day** - The configured allowance can only be changed once per calendar day
- **Accurate usage tracking** - Only counts time while Instagram is actively in the foreground
- **Full-screen lock overlay** - Blocks Instagram with a modal overlay that intercepts Back/Home/Recents
- **PIN protection** - The PIN gates unlocking, pausing, changing limits, and turning off the blocker
- **Limited PIN unlocks** - 3 PIN extensions per day; each 5-minute unlock or 15-minute pause consumes one
- **Permanent Chrome web block** - `instagram.com` is blocked in Chrome instantly, with no PIN bypass and no daily-quota exemption
- **System status & recovery** - Live permission badges and a Fix Permissions shortcut
- **Automatic midnight reset** - Usage, PIN unlocks, and the limit-change cooldown reset at midnight
- **Service resilience** - Foreground overlay service, accessibility watchdog, and WorkManager health checks
- **Polished dark UI** - Custom Material 3 theme built entirely in Jetpack Compose

## How It Works

1. **Grant permissions** - Enable the Accessibility Service and "Display over other apps" in system settings
2. **Set your allowance** - Choose your daily limit and start blocking
3. **Usage tracking** - The accessibility service monitors window changes and only charges time when Instagram is in focus
4. **Lock on exhaustion** - When the allowance runs out, a full-screen lock screen appears over Instagram
5. **Unlock with PIN** - Enter PIN (`2310`) to get a 5-minute session, or pause for 15 minutes (each counts toward 3 daily extensions)
6. **Chrome stays blocked** - Opening `instagram.com` in Chrome shows the lock screen immediately; PIN sessions and daily quota do not apply

## Permissions Required

| Permission | Purpose | Required |
|------------|---------|----------|
| **Accessibility Service** | Monitors foreground app and tracks Instagram usage | Yes |
| **Display over other apps (SYSTEM_ALERT_WINDOW)** | Shows the lock overlay above Instagram | Yes |
| **Notifications** | Health warnings if protection is disabled (Android 13+) | Optional |

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
- **State Management**: StateFlow + SharedPreferences
- **Navigation**: Navigation Compose
- **Background Work**: WorkManager
- **Min SDK**: 26 (Android 8.0)
- **Version**: 1.0.0

## Project Structure

```
com.example.shaylock
├── core/           # Business logic (BlockPolicy, BlockController, DailyLimit)
├── service/        # Accessibility service, overlay service, health worker
├── ui/             # Compose screens, components, theme
└── MainActivity.kt # Entry point
```

## Limitations

- PIN is hardcoded (`2310`) in the APK
- Chrome detection depends on the URL bar being exposed to the Accessibility Service
- Only blocks Instagram, Instagram Lite, and `instagram.com` in Chrome
- Gesture navigation may bypass some key swallowing on some devices
- Usage counter is wall-clock based
- Clearing app data or uninstalling removes all restrictions

## Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.
