# ThinkTech Smart Home

![Platform](https://img.shields.io/badge/Platform-Android%20(API%2023%2B)-3DDC84?logo=android&logoColor=white)
![Language](https://img.shields.io/badge/Kotlin-1.9.24-7F52FF?logo=kotlin&logoColor=white)
![SDK](https://img.shields.io/badge/Tuya%20SDK-7.8.0-FF6A00)
![Gradle](https://img.shields.io/badge/Gradle-8.7-02303A?logo=gradle&logoColor=white)

A native Android smart home application built with Kotlin and the **Tuya Smart Life App SDK**. Designed with a clean, responsive interface for managing IoT devices, live IPC security cameras, smart lighting, automated schedules, and voice commands.

## Key Features

- **Device Control & Pairing** — Real-time DP status synchronization, multi-channel switch control, and Wi-Fi / BLE device onboarding.
- **Smart Lighting Studio** — Custom radial dial supporting White CCT (270° arc), 360° RGB color control, brightness, and saturation adjustments.
- **IPC Security Camera** — Live P2P streaming with PTZ pan/tilt, two-way talk, IR night vision, motion detection, auto-tracking, privacy mode, snapshot, and recording.
- **Automation & Schedules** — One-tap scene execution (*Tap-to-Run*) and recurring device schedules via a 24-hour radial clock picker.
- **Voice AI Assistant** — Speech recognition and TTS control for switches, lighting, camera actions, rooms, schedules, and energy checks.
- **Room & Language Management** — Synchronized home/room filtering and instant in-app localization (`id`, `en`, `ms`, `zh`, `ja`).

## Tech Stack

| Layer | Technology |
| :--- | :--- |
| Language | Kotlin 1.9.24 (JDK 17) |
| UI & Graphics | Android ViewBinding, Material 3, Custom Canvas Views, Lottie |
| IoT & Camera SDK | Tuya Smart Life SDK `7.8.0`, Tuya IPC SDK `7.8.2` |
| Concurrency | Kotlin Coroutines & AndroidX Lifecycle |
| Build System | Android Gradle Plugin `8.5.2`, Gradle `8.7` (`minSdk 23`, `targetSdk 34`) |

## Project Structure

```text
app/src/main/java/com/thinktech/smarthome/
├── ThinkTechApp.kt           # SDK, P2P & locale initialization
├── data/                     # Auth, device, pairing, schedule & language repositories
└── ui/
    ├── splash/               # Launch screen & session bootstrap
    ├── login/                # Authentication & account recovery
    ├── home/                 # Dashboard, rooms, Smart scenes & Voice AI
    ├── device/               # Switch, Lighting Studio & IPC Camera controls
    ├── pairing/              # Wi-Fi / BLE device discovery & onboarding
    ├── schedule/             # Radial clock & recurring schedule editor
    └── settings/             # In-app language selection
```

## Getting Started

### Prerequisites

- Android Studio (JDK 17) & Android SDK 34
- A Tuya Developer account with Android App SDK credentials

### Setup

1. Clone the repository:
   ```bash
   git clone https://github.com/WagYu31/ThinkTech.git
   cd ThinkTech
   ```

2. Create `local.properties` from the example template and set your local SDK path and Tuya credentials:
   ```bash
   cp local.properties.example local.properties
   ```
   ```properties
   sdk.dir=/path/to/your/Android/sdk
   TUYA_APP_KEY=your_app_key
   TUYA_APP_SECRET=your_app_secret
   ```

3. Place your Tuya App Security SDK (`security-algorithm-*.aar`) into `app/libs/`.

### Build & Run

```bash
./gradlew assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

### Testing

```bash
./gradlew testDebugUnitTest
```
