# ThinkTech Smart Home (Tuya IoT SDK)

Aplikasi Android Native (Kotlin) untuk ekosistem Smart Home berbasis **Tuya Smart Life App SDK** (`v7.8.0`).

---

## 🔑 Konfigurasi Kredensial Tuya (Lokal)

* **Platform**: Android Native (Kotlin)
* **Package Name**: `com.thinktech.iot.smarthome`
* **Region / Location**: Outside Mainland China (Global)

> Demi keamanan, `AppKey`, `AppSecret`, dan file Security SDK (`security-algorithm-*.aar` / `t_s.bmp`) **tidak ikut dipublikasikan** ke repositori ini.

1. Salin `local.properties.example` menjadi `local.properties` di root proyek, lalu isi kredensial dari Tuya Developer Console:
   ```properties
   sdk.dir=/path/to/your/Android/sdk
   TUYA_APP_KEY=YOUR_TUYA_APP_KEY
   TUYA_APP_SECRET=YOUR_TUYA_APP_SECRET
   ```
2. Di tab **Get Key** pada Tuya Developer Console:
   * Klik **`+ Add SHA256 Hash Value`** pada bagian Android Certificate dan masukkan SHA-256 keystore Anda.
   * Unduh **App Security SDK** (`security-algorithm-*.aar`) lalu letakkan di `app/libs/` (atau `t_s.bmp` di `app/src/main/assets/t_s.bmp`).

---

## 🏗️ Struktur Arsitektur Aplikasi

```text
thinktech/
├── app/
│   ├── src/main/
│   │   ├── AndroidManifest.xml          # Izin lengkap & Metadata AppKey/Secret
│   │   ├── assets/                      # Tempat meletakkan file t_s.bmp
│   │   ├── java/com/thinktech/smarthome/
│   │   │   ├── ThinkTechApp.kt          # Inisialisasi ThingHomeSdk Tuya
│   │   │   ├── data/
│   │   │   │   ├── model/SmartDeviceItem.kt
│   │   │   │   ├── TuyaAuthRepository.kt    # Login email/phone & sesi user
│   │   │   │   ├── TuyaDeviceRepository.kt  # Query Home, device list & DP toggle
│   │   │   │   └── TuyaPairingRepository.kt # EZ Mode Wi-Fi 2.4 GHz pairing
│   │   │   └── ui/
│   │   │       ├── login/LoginActivity.kt   # Layar masuk akun Tuya
│   │   │       ├── home/
│   │   │       │   ├── MainActivity.kt      # Dashboard smart home & device list
│   │   │       │   └── DeviceAdapter.kt     # Adapter list dengan instant power switch
│   │   │       ├── device/DeviceControlActivity.kt # Kontrol detail & listener real-time
│   │   │       └── pairing/PairDeviceActivity.kt   # Wizard penambahan perangkat baru
│   │   └── res/
│   │       ├── layout/                  # activity_login, activity_main, dll.
│   │       ├── values/                  # colors, strings, themes (Material3)
│   │       └── drawable/                # Modern gradient & vector icons
│   ├── build.gradle                     # Konfigurasi dependensi Tuya v7.8.0
│   └── proguard-rules.pro               # Aturan obfuscation resmi Tuya SDK
├── build.gradle                         # Root Gradle konfigurasi AGP & Kotlin
├── settings.gradle                      # Tuya Maven Repositories
└── local.properties                     # Path Android SDK lokal
```

---

## 🚀 Menjalankan Aplikasi

Buka folder project ini di **Android Studio** atau gunakan terminal:
```bash
./gradlew assembleDebug
```
Lalu install ke perangkat/emulator Android Anda:
```bash
adb install app/build/outputs/apk/debug/app-debug.apk
```
