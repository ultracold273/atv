# Research: Multi-lingual Support (i18n) for ATV

**Date**: 2026-01-02  
**Topic**: Internationalization (i18n) implementation for English and Chinese  

## Current State Analysis

### Existing Infrastructure
1. **strings.xml already exists** at `res/values/strings.xml` with ~28 string resources
2. **Jetpack Compose UI** - uses `Text()` composables throughout
3. **Some strings use resources**, but many UI components have **hardcoded strings**

### Hardcoded Strings Found (require migration)
| File | Hardcoded String | Type |
|------|-----------------|------|
| `PlaybackScreen.kt` | "Loading..." | UI Label |
| `PlaybackScreen.kt` | "Unknown error" | Error Message |
| `SettingsMenu.kt` | "Quick Menu", "Load Playlist", "Manage Channels", etc. | UI Labels |
| `ErrorOverlay.kt` | "Playback Error", "Retry", "Next Channel" | UI Labels/Buttons |
| `ChannelListOverlay.kt` | "Channels" | UI Title |
| `ChannelManagementScreen.kt` | "Manage Channels", "Loading...", "No channels..." | UI Labels |
| `SetupScreen.kt` | "ATV", "Android TV IPTV Player", "Loading playlist..." | UI Labels |
| `NumberPadOverlay.kt` | "Go", "Clear" | Button Labels |
| `MainActivity.kt` | "Press back again to exit" | Toast/Snackbar |
| `PlaybackViewModel.kt` | "Channel X does not exist" | Error Message |

## Recommended Approach

### Android Standard Resource-based Localization

**Why this approach?**
1. **Native Android pattern** - built into the platform, no external libraries needed
2. **Automatic locale switching** - Android system handles language selection
3. **Compose integration** - `stringResource()` API works seamlessly
4. **TV remote friendly** - no impact on navigation or focus management
5. **Minimal code changes** - just replace hardcoded strings with resource references
6. **Efficient** - resources compiled into APK, no runtime parsing

### Implementation Strategy

#### 1. Directory Structure
```
res/
├── values/
│   └── strings.xml          # Default (English)
└── values-zh/
    └── strings.xml          # Chinese (Simplified)
```

> Note: Use `values-zh` for Simplified Chinese (covers both zh-CN and zh-Hans)
> For Traditional Chinese, use `values-zh-rTW` or `values-zh-rHK`

#### 2. Compose API Usage
```kotlin
// Instead of:
Text(text = "Loading...")

// Use:
Text(text = stringResource(R.string.loading))

// For formatted strings:
Text(text = stringResource(R.string.channel_info, channelNumber, channelName))
```

#### 3. Non-Composable Contexts
For ViewModel/Activity contexts where Compose is not available:
```kotlin
// In ViewModel - use Context
context.getString(R.string.channel_not_found, number)

// Or use string resource IDs and resolve in UI layer
data class UiState(
    val errorMessageResId: Int? = null
)
```

### Migration Steps

1. **Audit all hardcoded strings** in Kotlin files
2. **Add missing strings** to `res/values/strings.xml`
3. **Create Chinese translations** in `res/values-zh/strings.xml`
4. **Replace hardcoded strings** with `stringResource()` calls
5. **Handle ViewModels** - pass resource IDs or use Context
6. **Test** in both English and Chinese locales

### String Categories to Localize

| Category | Examples | Priority |
|----------|----------|----------|
| UI Labels | "Loading...", "Channels", "Settings" | P1 |
| Button Text | "Retry", "Cancel", "Save", "Go" | P1 |
| Error Messages | "Stream failed to load", "No channels found" | P1 |
| Titles | "Quick Menu", "Manage Channels" | P1 |
| Instructions | "Press back again to exit" | P2 |
| App Info | "Android TV IPTV Player" | P2 |

### Special Considerations for Chinese

1. **Text length** - Chinese text is typically shorter than English
2. **Font support** - System fonts on Android TV support Chinese
3. **Number formatting** - Channel numbers remain unchanged
4. **Date/Time** - Not heavily used in this app
5. **RTL** - Not applicable (Chinese is LTR)

## Alternatives Considered

### 1. Third-party i18n Libraries (e.g., Lokalise, Crowdin)
- **Pros**: Advanced features, OTA updates
- **Cons**: Overkill for 2 languages, adds dependencies, cost
- **Decision**: Not needed for MVP

### 2. Compose Multiplatform Resources
- **Pros**: Cross-platform consistency
- **Cons**: More complex setup, not standard Android pattern
- **Decision**: Stick with Android resources

### 3. Hardcoded strings with conditional logic
- **Pros**: Simple to implement
- **Cons**: Not scalable, poor maintainability
- **Decision**: Anti-pattern, rejected

## Effort Estimate

| Task | Effort |
|------|--------|
| Audit and add missing English strings | 1 hour |
| Create Chinese translations | 2 hours |
| Migrate hardcoded strings in UI | 3-4 hours |
| Handle ViewModel string resources | 1 hour |
| Testing both locales | 1 hour |
| **Total** | **~8-9 hours** |

## References

- [Android Localization Guide](https://developer.android.com/guide/topics/resources/localization)
- [Compose Resources](https://developer.android.com/develop/ui/compose/resources)
- [Per-app Language Preferences](https://developer.android.com/guide/topics/resources/app-languages)
