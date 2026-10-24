# ShayLock

[![Android](https://img.shields.io/badge/Platform-Android-3DDC84?logo=android&logoColor=white)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0.21-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-Material%203-4285F4?logo=jetpackcompose&logoColor=white)](https://developer.android.com/jetpack/compose)
[![Min SDK](https://img.shields.io/badge/Min%20SDK-26%20(Android%208.0)-orange)](https://developer.android.com/about/versions/oreo/android-8.0)
[![Target SDK](https://img.shields.io/badge/Target%20SDK-35-blue)](https://developer.android.com/about/versions/15)
[![License](https://img.shields.io/badge/License-GPL%20v3-blue.svg)](LICENSE)

A strict, dark-themed Instagram screen-time blocker for Android. ShayLock limits daily usage of Instagram (`com.instagram.android`) and Instagram Lite (`com.instagram.lite`), locks the app behind a full-screen overlay when your allowance runs out, and protects critical actions with a PIN.

## Features

- **Daily allowance control** - Set a daily limit (10s for testing, 15 min, 30 min, or 1h)
- **Accurate usage tracking** - Only counts time while Instagram is actively in the foreground
- **Full-screen lock overlay** - Blocks Instagram with a modal overlay that swallows Back/Home gestures
- **PIN protection** - Hardcoded PIN (`2310`) gates unlocking, pausing, changing limits, and turning off the blocker
- **Limited extensions** - 3 PIN-based extensions per day (each 5-minute unlock or 15-minute pause consumes 1)
- **Automatic midnight reset** - Usage resets at midnight; your allowance stays configured
- **Service resilience** - Foreground overlay service, accessibility watchdog, and WorkManager health checks
- **Polished dark UI** - Clean Material 3 design with custom logo

## How It Works

1. **Grant permissions** - Enable the Accessibility Service and "Display over other apps" in system settings
2. **Set your allowance** - Choose your daily limit and start blocking
3. **Usage tracking** - The accessibility service monitors window changes and only charges time when Instagram is in focus
4. **Lock on exhaustion** - When allowance runs out, a full-screen lock screen appears over Instagram
5. **Unlock with PIN** - Enter PIN (`2310`) to get a 5-minute session, or pause for 15 minutes (each counts toward 3 daily extensions)

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
- **Background Work**: WorkManager
- **Min SDK**: 26 (Android 8.0)

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
- Only blocks Instagram and Instagram Lite
- Gesture navigation may bypass some key swallowing on some devices
- Usage counter is wall-clock based
- Clearing app data or uninstalling removes all restrictions

## Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

## License

This project is licensed under the GPL v3 License - see the [LICENSE](LICENSE) file for details.
