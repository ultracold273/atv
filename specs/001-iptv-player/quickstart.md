# Quickstart Guide: ATV - Android TV IPTV Player

**Generated**: 2025-12-26  
**Plan**: [plan.md](plan.md)  
**Spec**: [spec.md](spec.md)

---

## Prerequisites

### Development Environment

| Requirement | Version | Notes |
|-------------|---------|-------|
| Android Studio | Ladybug (2024.2.1) or later | With Android TV emulator support |
| JDK | 17 | Embedded in Android Studio |
| Kotlin | 2.1.0 | Stable LTS, configured in project |
| Android SDK | API 35 | Target SDK |
| Android SDK | API 29 | Minimum SDK |

### Recommended Hardware

- **For Testing**: Android TV device (Shield, Chromecast with Google TV) OR Android TV emulator
- **Remote Testing**: ADB debugging with physical remote, or use on-screen D-pad in emulator

---

## Quick Setup

### 1. Clone Repository

```bash
git clone <repository-url> atv
cd atv
```

### 2. Open in Android Studio

1. Open Android Studio
2. Select "Open" → Navigate to project root
3. Wait for Gradle sync to complete

### 3. Configure Android TV Emulator

1. Open Device Manager (Tools → Device Manager)
2. Create Virtual Device
3. Select "TV" category → "Android TV (1080p)"
4. Select system image: API 34 (or 29+)
5. Finish setup

### 4. Build & Run

```bash
# Command line
./gradlew assembleDebug
./gradlew installDebug

# Or use Android Studio
# Click Run ▶️ with TV emulator selected
```

---

## Project Structure

```
atv/
├── app/
│   ├── build.gradle.kts
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml
│       │   └── kotlin/com/example/atv/
│       │       ├── AtvApplication.kt
│       │       ├── MainActivity.kt
│       │       ├── di/                    # Hilt modules
│       │       ├── domain/
│       │       │   ├── model/             # Channel, Playlist, etc.
│       │       │   └── repository/        # Repository interfaces
│       │       ├── data/
│       │       │   ├── local/             # Room DB, DataStore
│       │       │   ├── parser/            # M3U8 parser
│       │       │   └── repository/        # Repository implementations
│       │       ├── player/                # ExoPlayer wrapper
│       │       └── ui/
│       │           ├── navigation/        # Nav graph
│       │           ├── screens/           # Playback, Settings
│       │           ├── components/        # Reusable composables
│       │           └── theme/             # TV theme
│       ├── test/                          # Unit tests
│       └── androidTest/                   # UI tests
├── gradle/
│   └── libs.versions.toml                 # Version catalog
├── build.gradle.kts
├── settings.gradle.kts
└── specs/                                 # Specification docs
```

---

## Build Commands

### Gradle Tasks

```bash
# Build debug APK
./gradlew assembleDebug

# Build release APK (requires signing config)
./gradlew assembleRelease

# Run all unit tests
./gradlew test

# Run connected (instrumentation) tests
./gradlew connectedAndroidTest

# Run lint
./gradlew lint

# Clean build
./gradlew clean

# Generate dependency updates report
./gradlew dependencyUpdates
```

### Useful ADB Commands

```bash
# List connected devices
adb devices

# Install APK
adb install -r app/build/outputs/apk/debug/app-debug.apk

# Launch app
adb shell am start -n com.example.atv/.MainActivity

# Push test M3U8 file to device
adb push test_playlist.m3u8 /sdcard/Download/

# View logs
adb logcat -s ATV:* Timber:*

# Simulate D-pad input
adb shell input keyevent KEYCODE_DPAD_UP
adb shell input keyevent KEYCODE_DPAD_DOWN
adb shell input keyevent KEYCODE_DPAD_LEFT
adb shell input keyevent KEYCODE_DPAD_RIGHT
adb shell input keyevent KEYCODE_DPAD_CENTER
adb shell input keyevent KEYCODE_BACK
adb shell input keyevent KEYCODE_MENU
```

---

## Testing with M3U8 Playlist

### Sample Test Playlist

Create `test_playlist.m3u8`:

```m3u8
#EXTM3U
#EXTINF:-1 tvg-id="test1" group-title="Test",Test Channel 1
https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8
#EXTINF:-1 tvg-id="test2" group-title="Test",Test Channel 2
https://demo.unified-streaming.com/k8s/features/stable/video/tears-of-steel/tears-of-steel.ism/.m3u8
#EXTINF:-1 tvg-id="test3" group-title="Test",Test Channel 3
https://devstreaming-cdn.apple.com/videos/streaming/examples/img_bipbop_adv_example_fmp4/master.m3u8
```

### Load Playlist

1. Push to device: `adb push test_playlist.m3u8 /sdcard/Download/`
2. Launch app
3. Navigate to Settings (long-press BACK or press MENU)
4. Select "Load Playlist"
5. Navigate to Download folder and select file

---

## Development Workflow

### 1. Create Feature Branch

```bash
git checkout -b feature/description
```

### 2. Implement Changes

Follow the project structure:
- Domain logic in `domain/`
- Data layer in `data/`
- UI in `ui/screens/` and `ui/components/`
- Player logic in `player/`

### 3. Run Tests

```bash
# Unit tests
./gradlew testDebugUnitTest

# Lint
./gradlew lintDebug
```

### 4. Submit PR

```bash
git add .
git commit -m "feat(scope): description"
git push origin feature/description
```

---

## Key Configuration Files

### gradle/libs.versions.toml

```toml
[versions]
# Core - validated 2025-12-26
kotlin = "2.1.0"                    # Latest stable LTS (2.3.0 is newest but 2.1.x is safer)
agp = "8.7.3"                       # Latest stable AGP
composeBom = "2024.12.01"           # Latest Compose BOM
tvFoundation = "1.0.0-alpha12"      # Latest TV foundation
tvMaterial = "1.0.1"                # First stable TV material!
media3 = "1.5.1"                    # Stable with bug fixes (1.9.0 is latest)
hilt = "2.54"                       # Stable (2.57.2 requires Gradle 9.0+)
room = "2.8.4"                      # Latest stable (requires minSdk 23, we have 29)
datastore = "1.1.1"                 # Stable with reliability fixes

[libraries]
# Compose for TV
tv-foundation = { module = "androidx.tv:tv-foundation", version.ref = "tvFoundation" }
tv-material = { module = "androidx.tv:tv-material", version.ref = "tvMaterial" }

# Media3
media3-exoplayer = { module = "androidx.media3:media3-exoplayer", version.ref = "media3" }
media3-exoplayer-hls = { module = "androidx.media3:media3-exoplayer-hls", version.ref = "media3" }
media3-ui = { module = "androidx.media3:media3-ui", version.ref = "media3" }

# DI
hilt-android = { module = "com.google.dagger:hilt-android", version.ref = "hilt" }
hilt-compiler = { module = "com.google.dagger:hilt-android-compiler", version.ref = "hilt" }

# Storage
room-runtime = { module = "androidx.room:room-runtime", version.ref = "room" }
room-ktx = { module = "androidx.room:room-ktx", version.ref = "room" }
room-compiler = { module = "androidx.room:room-compiler", version.ref = "room" }
datastore-preferences = { module = "androidx.datastore:datastore-preferences", version.ref = "datastore" }

# Testing - validated 2025-12-26
junit5 = { module = "org.junit.jupiter:junit-jupiter", version = "5.11.3" }
mockk = { module = "io.mockk:mockk", version = "1.13.14" }
turbine = { module = "app.cash.turbine:turbine", version = "1.2.0" }

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
kotlin-android = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }
compose-compiler = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
hilt = { id = "com.google.dagger.hilt.android", version.ref = "hilt" }
ksp = { id = "com.google.devtools.ksp", version = "2.1.0-1.0.29" }
```

> **Version Notes** (validated 2025-12-26):
> - **Kotlin 2.1.0**: Stable since Nov 2024. Kotlin 2.3.0 released Dec 16, 2025 (10 days ago) — too new for production. 2.1.x has proven ecosystem compatibility.
> - **Media3 1.5.1**: Stable release from mid-2024. Media3 1.9.0 released Dec 17, 2025 (9 days ago) — too new. Previous stable 1.8.0 (Jul 2025) also acceptable.
> - **Hilt 2.54**: Stable with Gradle 8.x. Hilt 2.57.2 requires Gradle 9.0+ (released Jul 2025), which would cascade to AGP 8.13+ upgrade — too risky for MVP.
> - **Room 2.8.4**: Latest stable. Requires minSdk 23; our minSdk 29 is compatible.
> - **TV Material 1.0.1**: First stable release! No longer alpha.
> - No known security vulnerabilities in any dependencies.

### AndroidManifest.xml (Key Entries)

```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    
    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE" />
    
    <uses-feature
        android:name="android.software.leanback"
        android:required="true" />
    
    <uses-feature
        android:name="android.hardware.touchscreen"
        android:required="false" />
    
    <application
        android:name=".AtvApplication"
        android:banner="@drawable/banner"
        android:icon="@mipmap/ic_launcher"
        android:label="@string/app_name"
        android:theme="@style/Theme.ATV">
        
        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:screenOrientation="landscape">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LEANBACK_LAUNCHER" />
            </intent-filter>
        </activity>
        
    </application>
</manifest>
```

---

## Troubleshooting

### Emulator Issues

**Problem**: TV emulator shows "Google Play services out of date"
**Solution**: Use "Android TV" image without Google Play, or update Play services

**Problem**: D-pad navigation not working
**Solution**: Ensure emulator is focused, use keyboard arrows or on-screen controls

### Build Issues

**Problem**: Kotlin/Compose version mismatch
**Solution**: Ensure Kotlin and Compose Compiler versions are compatible in `libs.versions.toml`

**Problem**: Hilt not generating code
**Solution**: Verify KSP plugin is applied and Hilt compiler is in kapt/ksp dependencies

### Runtime Issues

**Problem**: ExoPlayer not playing streams
**Solution**: Check internet permission, verify stream URL is accessible, check logcat for errors

**Problem**: File picker not showing files
**Solution**: Ensure READ_EXTERNAL_STORAGE permission granted, check file is in accessible location
