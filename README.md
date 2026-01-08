# ATV - Android TV IPTV Player

[![CI](https://github.com/ultracold273/atv/actions/workflows/ci.yml/badge.svg)](https://github.com/ultracold273/atv/actions/workflows/ci.yml)

ATV is a simple, modern IPTV player designed specifically for Android TV. It allows you to watch live TV streams from M3U8 playlists with a user interface optimized for remote control navigation.

## 📺 Features

- **Simple Navigation**: Designed for D-pad remotes (Up/Down to switch channels, Left for list, OK for number pad).
- **M3U8 Support**: Load your own M3U8 playlist files from local storage.
- **Channel List**: Browse all your channels in a side overlay while watching.
- **Direct Access**: Jump to specific channels using an on-screen number pad.
- **Channel Management**: Manually add, edit, or delete channels directly on your TV.
- **Auto-Play**: Automatically resumes your last watched channel on launch.
- **Demo Mode**: Includes a built-in demo playlist to test functionality immediately.

## 🎮 Controls

| Button | Action |
|--------|--------|
| **UP / DOWN** | Switch to previous/next channel |
| **LEFT** | Open Channel List |
| **OK / CENTER** | Open Number Pad for direct channel entry |
| **BACK** | Close overlays / Exit app |
| **MENU** (or Long Press BACK) | Open Settings Menu |

## 🚀 Getting Started

### Installation

1. Download the latest APK from the [Releases](https://github.com/ultracold273/atv/releases) page.
2. Install the APK on your Android TV device (you may need to enable "Install from unknown sources").

### Setup

1. **First Launch**: You will be prompted to load a playlist.
2. **Load Playlist**: 
   - Click **"Browse Files"** to select an `.m3u8` file from your device's storage.
   - Or click **"Load Demo Playlist"** to try the app with sample channels.
3. **Enjoy**: The first channel will start playing automatically.

## 🛠️ Development

### Prerequisites

- Android Studio Ladybug (2024.2.1) or later
- JDK 17+ (Embedded in Android Studio)
- Android SDK API 35 (Target), API 29 (Min)

### Build & Run

```bash
# Clone the repository
git clone https://github.com/ultracold273/atv.git
cd atv

# Build Debug APK
./studio-gradlew assembleDebug

# Install on connected device/emulator
./studio-gradlew installDebug
```

### Testing on Emulator

The app includes a `studio-gradlew` wrapper script that configures the Java environment automatically.

To test local streams (e.g., udpxy) on the emulator:
1. Ensure your local server allows cleartext traffic (configured in `network_security_config.xml`).
2. Use `adb reverse tcp:PORT tcp:PORT` to forward ports if needed.
3. Use `10.0.2.2` to access the host machine's localhost.

### Running Tests

```bash
# Run unit tests
./studio-gradlew test

# Run unit tests with coverage report
./studio-gradlew koverHtmlReport
# View report at: app/build/reports/kover/html/index.html

# Run E2E tests on emulator (requires Android TV emulator running)
./studio-gradlew connectedAndroidTest

# Run static analysis
./studio-gradlew detekt lint

# Run security scan (OWASP dependency check)
./studio-gradlew dependencyCheckAnalyze
# View report at: app/build/reports/dependency-check-report.html
```

### CI/CD

The project uses GitHub Actions for continuous integration:

- **Build & Test**: Runs on every push and PR
  - Compiles the project
  - Runs unit tests with JUnit 5
  - Generates coverage reports with Kover
- **Static Analysis**: Runs Detekt and Android Lint
- **Security Scan**: Weekly OWASP dependency check (also on main branch pushes)

All CI artifacts (test results, coverage reports, lint reports) are uploaded and available in GitHub Actions.

## 🏗️ Tech Stack

- **Language**: Kotlin
- **UI**: Jetpack Compose for TV (Material 3)
- **Player**: Media3 ExoPlayer
- **Architecture**: MVVM + Clean Architecture
- **DI**: Hilt
- **Async**: Coroutines + Flow
- **Storage**: Room (Database) + DataStore (Preferences)

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.
