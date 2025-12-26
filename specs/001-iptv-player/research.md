# Research: ATV - Android TV IPTV Player

**Generated**: 2025-12-26  
**Plan**: [plan.md](plan.md)  
**Spec**: [spec.md](spec.md)

This document captures technology research and best practices for implementing the ATV IPTV player.

---

## Table of Contents

1. [Media3 ExoPlayer](#media3-exoplayer)
2. [Compose for TV](#compose-for-tv)
3. [Focus Management](#focus-management)
4. [M3U8 Parsing](#m3u8-parsing)
5. [Room Database](#room-database)
6. [DataStore](#datastore)
7. [Hilt Setup](#hilt-setup)
8. [Android TV Remote Handling](#android-tv-remote-handling)

---

## Media3 ExoPlayer

### Decision
Use `androidx.media3:media3-exoplayer` with HLS extension for streaming playback.

### Rationale
- Official Google library, actively maintained
- Built-in support for HLS, DASH, RTSP (FR-002)
- Handles adaptive bitrate streaming automatically
- Integrates with Compose via `AndroidView`

### Alternatives Considered
- **VLC Android** - More codec support but larger APK, less Android-native
- **Custom implementation** - Unnecessary complexity for standard streaming

### Implementation Pattern

```kotlin
// player/AtvPlayer.kt
@Singleton
class AtvPlayer @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var exoPlayer: ExoPlayer? = null
    
    private val _playerState = MutableStateFlow(PlayerState.Idle)
    val playerState: StateFlow<PlayerState> = _playerState.asStateFlow()
    
    fun initialize() {
        exoPlayer = ExoPlayer.Builder(context)
            .setHandleAudioBecomingNoisy(true)
            .build()
            .apply {
                addListener(playerListener)
            }
    }
    
    fun playChannel(streamUrl: String) {
        val mediaItem = MediaItem.fromUri(streamUrl)
        exoPlayer?.apply {
            setMediaItem(mediaItem)
            prepare()
            play()
        }
    }
    
    fun release() {
        exoPlayer?.release()
        exoPlayer = null
    }
}
```

### Dependencies
```toml
# gradle/libs.versions.toml
[versions]
media3 = "1.5.1"  # Stable (1.9.0 released Dec 17, 2025 - only 9 days old; 1.8.0 Jul 2025 also acceptable)

[libraries]
media3-exoplayer = { module = "androidx.media3:media3-exoplayer", version.ref = "media3" }
media3-exoplayer-hls = { module = "androidx.media3:media3-exoplayer-hls", version.ref = "media3" }
media3-ui = { module = "androidx.media3:media3-ui", version.ref = "media3" }
```

### Key Considerations
- Call `release()` in `onStop()` to free resources
- Handle audio focus properly for TV
- Use `setHandleAudioBecomingNoisy(true)` to pause when audio route changes

---

## Compose for TV

### Decision
Use `androidx.tv:tv-foundation` and `androidx.tv:tv-material` for TV-optimized Compose UI.

### Rationale
- Official Google TV components with D-pad navigation built-in
- Focus handling integrated into composables
- Material Design for TV out of the box
- Modern declarative UI matches Kotlin-first approach

### Alternatives Considered
- **Leanback** - Older XML-based, less flexible
- **Plain Compose** - Missing TV-specific focus handling

### Implementation Pattern

```kotlin
// ui/screens/playback/PlaybackScreen.kt
@Composable
fun PlaybackScreen(
    viewModel: PlaybackViewModel = hiltViewModel(),
    onNavigateToSettings: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .handleDPadKeyEvents(
                onUp = { viewModel.previousChannel() },
                onDown = { viewModel.nextChannel() },
                onLeft = { viewModel.showChannelList() },
                onCenter = { viewModel.showNumberPad() }
            )
    ) {
        // Video surface
        PlayerSurface(player = viewModel.player)
        
        // Overlays
        AnimatedVisibility(visible = uiState.showChannelInfo) {
            ChannelInfoOverlay(channel = uiState.currentChannel)
        }
        
        AnimatedVisibility(visible = uiState.showChannelList) {
            ChannelListOverlay(
                channels = uiState.channels,
                currentIndex = uiState.currentChannelIndex,
                onChannelSelected = { viewModel.selectChannel(it) }
            )
        }
    }
}
```

### Dependencies
```toml
[versions]
tvFoundation = "1.0.0-alpha12"  # TV Foundation still in alpha
tvMaterial = "1.0.1"             # First STABLE release! (Dec 2024)
composeCompiler = "2.1.0"        # Now part of Kotlin plugin

[libraries]
tv-foundation = { module = "androidx.tv:tv-foundation", version.ref = "tvFoundation" }
tv-material = { module = "androidx.tv:tv-material", version.ref = "tvMaterial" }
```

### Key Considerations
- Use `TvLazyColumn` / `TvLazyRow` for lists (not regular `LazyColumn`)
- Wrap clickable items in `Surface` from tv-material for focus states
- Test on actual TV device or emulator - focus behavior differs from touch

---

## Focus Management

### Decision
Use Compose for TV's built-in focus system with `FocusRequester` for programmatic control.

### Rationale
- TV interfaces are entirely focus-driven (no touch)
- Compose for TV handles most cases automatically
- `FocusRequester` needed for overlay show/hide transitions

### Implementation Pattern

```kotlin
// ui/components/ChannelListOverlay.kt
@Composable
fun ChannelListOverlay(
    channels: List<Channel>,
    currentIndex: Int,
    onChannelSelected: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val focusRequester = remember { FocusRequester() }
    val listState = rememberTvLazyListState(initialFirstVisibleItemIndex = currentIndex)
    
    LaunchedEffect(Unit) {
        // Request focus when overlay appears
        focusRequester.requestFocus()
    }
    
    Surface(
        modifier = Modifier
            .width(300.dp)
            .fillMaxHeight()
            .focusRequester(focusRequester)
            .onKeyEvent { event ->
                if (event.key == Key.Back || event.key == Key.DirectionLeft) {
                    onDismiss()
                    true
                } else false
            }
    ) {
        TvLazyColumn(state = listState) {
            itemsIndexed(channels) { index, channel ->
                ChannelListItem(
                    channel = channel,
                    isCurrentlyPlaying = index == currentIndex,
                    onSelected = { onChannelSelected(index) }
                )
            }
        }
    }
}
```

### Key Considerations
- Always have one focusable element when overlay appears
- Use `focusRestorer()` to remember focus position when returning
- Handle BACK key to dismiss overlays (don't navigate away)

---

## M3U8 Parsing

### Decision
Implement custom M3U8 parser for playlist files (not streaming manifests).

### Rationale
- M3U8 playlist format is simple text-based
- Need to extract: channel name, URL, group-title, logo
- ExoPlayer's HLS parser is for streaming manifests, not playlist files

### M3U8 Format Reference

```m3u8
#EXTM3U
#EXTINF:-1 tvg-id="channel1" tvg-name="Channel One" tvg-logo="http://logo.png" group-title="News",Channel One HD
http://stream.example.com/channel1/playlist.m3u8
#EXTINF:-1 tvg-id="channel2" group-title="Sports",Sports Channel
http://stream.example.com/channel2/playlist.m3u8
```

### Implementation Pattern

```kotlin
// data/parser/M3U8Parser.kt
class M3U8Parser @Inject constructor() {
    
    fun parse(content: String): ParseResult {
        val lines = content.lines()
        if (lines.firstOrNull()?.trim() != "#EXTM3U") {
            return ParseResult.Error("Invalid M3U8 file: missing #EXTM3U header")
        }
        
        val channels = mutableListOf<Channel>()
        var currentExtInf: String? = null
        
        for (line in lines) {
            when {
                line.startsWith("#EXTINF:") -> {
                    currentExtInf = line
                }
                line.startsWith("http://") || line.startsWith("https://") -> {
                    currentExtInf?.let { extinf ->
                        channels.add(parseChannel(extinf, line, channels.size + 1))
                    }
                    currentExtInf = null
                }
            }
        }
        
        return if (channels.isEmpty()) {
            ParseResult.Error("No valid channels found")
        } else {
            ParseResult.Success(channels)
        }
    }
    
    private fun parseChannel(extinf: String, url: String, number: Int): Channel {
        // Extract attributes using regex
        val name = extinf.substringAfterLast(",").trim()
        val groupTitle = Regex("""group-title="([^"]*)"")""").find(extinf)?.groupValues?.get(1)
        val logoUrl = Regex("""tvg-logo="([^"]*)"")""").find(extinf)?.groupValues?.get(1)
        
        return Channel(
            number = number,
            name = name,
            streamUrl = url,
            groupTitle = groupTitle,
            logoUrl = logoUrl
        )
    }
}

sealed class ParseResult {
    data class Success(val channels: List<Channel>) : ParseResult()
    data class Error(val message: String) : ParseResult()
}
```

### Test Cases Required
- Valid M3U8 with multiple channels
- Missing #EXTM3U header
- Empty file
- Channels with/without group-title
- Channels with/without logo
- Mixed valid/invalid entries

---

## Room Database

### Decision
Use Room for persistent storage of channels and playlist metadata.

### Rationale
- Official Jetpack library, Kotlin-first with KSP
- Compile-time query verification
- Flow support for reactive updates
- Handles schema migrations

### Implementation Pattern

```kotlin
// data/local/db/ChannelEntity.kt
@Entity(tableName = "channels")
data class ChannelEntity(
    @PrimaryKey val number: Int,
    val name: String,
    val streamUrl: String,
    val groupTitle: String?,
    val logoUrl: String?,
    val isManuallyAdded: Boolean = false
)

// data/local/db/ChannelDao.kt
@Dao
interface ChannelDao {
    @Query("SELECT * FROM channels ORDER BY number ASC")
    fun getAllChannels(): Flow<List<ChannelEntity>>
    
    @Query("SELECT * FROM channels WHERE number = :number")
    suspend fun getChannel(number: Int): ChannelEntity?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(channels: List<ChannelEntity>)
    
    @Query("DELETE FROM channels WHERE isManuallyAdded = 0")
    suspend fun deletePlaylistChannels()
    
    @Delete
    suspend fun delete(channel: ChannelEntity)
}

// data/local/db/AtvDatabase.kt
@Database(entities = [ChannelEntity::class], version = 1)
abstract class AtvDatabase : RoomDatabase() {
    abstract fun channelDao(): ChannelDao
}
```

### Dependencies
```toml
[versions]
room = "2.8.4"  # Latest stable (requires minSdk 23, we have minSdk 29 ✓)

[libraries]
room-runtime = { module = "androidx.room:room-runtime", version.ref = "room" }
room-ktx = { module = "androidx.room:room-ktx", version.ref = "room" }
room-compiler = { module = "androidx.room:room-compiler", version.ref = "room" }
```

---

## DataStore

### Decision
Use DataStore Preferences for user settings (last channel, playlist source path).

### Rationale
- Replacement for SharedPreferences
- Coroutines + Flow support
- Type-safe with Kotlin
- Handles IO on background thread automatically

### Implementation Pattern

```kotlin
// data/local/datastore/UserPreferencesDataStore.kt
class UserPreferencesDataStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val Context.dataStore by preferencesDataStore(name = "user_preferences")
    
    private object Keys {
        val LAST_CHANNEL_NUMBER = intPreferencesKey("last_channel_number")
        val PLAYLIST_FILE_PATH = stringPreferencesKey("playlist_file_path")
        val AUTO_PLAY_ON_LAUNCH = booleanPreferencesKey("auto_play_on_launch")
    }
    
    val lastChannelNumber: Flow<Int> = context.dataStore.data
        .map { preferences -> preferences[Keys.LAST_CHANNEL_NUMBER] ?: 1 }
    
    val playlistFilePath: Flow<String?> = context.dataStore.data
        .map { preferences -> preferences[Keys.PLAYLIST_FILE_PATH] }
    
    suspend fun setLastChannelNumber(number: Int) {
        context.dataStore.edit { preferences ->
            preferences[Keys.LAST_CHANNEL_NUMBER] = number
        }
    }
    
    suspend fun setPlaylistFilePath(path: String) {
        context.dataStore.edit { preferences ->
            preferences[Keys.PLAYLIST_FILE_PATH] = path
        }
    }
}
```

### Dependencies
```toml
[versions]
datastore = "1.1.1"

[libraries]
datastore-preferences = { module = "androidx.datastore:datastore-preferences", version.ref = "datastore" }
```

---

## Hilt Setup

### Decision
Use Hilt for dependency injection.

### Rationale
- Official Android DI solution
- Simplifies Android-specific scopes (ViewModel, Activity)
- Compile-time verification
- Integrates with Compose navigation

### Implementation Pattern

```kotlin
// AtvApplication.kt
@HiltAndroidApp
class AtvApplication : Application()

// di/DatabaseModule.kt
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AtvDatabase {
        return Room.databaseBuilder(
            context,
            AtvDatabase::class.java,
            "atv_database"
        ).build()
    }
    
    @Provides
    fun provideChannelDao(database: AtvDatabase): ChannelDao {
        return database.channelDao()
    }
}

// di/PlayerModule.kt
@Module
@InstallIn(SingletonComponent::class)
object PlayerModule {
    
    @Provides
    @Singleton
    fun provideAtvPlayer(@ApplicationContext context: Context): AtvPlayer {
        return AtvPlayer(context)
    }
}
```

### Dependencies
```toml
[versions]
hilt = "2.54"  # Stable with Gradle 8.x (2.57.2 requires Gradle 9.0+ - too new)

[libraries]
hilt-android = { module = "com.google.dagger:hilt-android", version.ref = "hilt" }
hilt-compiler = { module = "com.google.dagger:hilt-android-compiler", version.ref = "hilt" }
hilt-navigation-compose = { module = "androidx.hilt:hilt-navigation-compose", version = "1.2.0" }
```

---

## Android TV Remote Handling

### Decision
Handle D-pad events at the composable level using `Modifier.onKeyEvent()`.

### Rationale
- Compose for TV provides key event modifiers
- Allows different screens to handle keys differently
- No need for complex KeyEvent propagation

### Implementation Pattern

```kotlin
// ui/util/KeyEventExtensions.kt
fun Modifier.handleDPadKeyEvents(
    onUp: (() -> Unit)? = null,
    onDown: (() -> Unit)? = null,
    onLeft: (() -> Unit)? = null,
    onRight: (() -> Unit)? = null,
    onCenter: (() -> Unit)? = null,
    onBack: (() -> Unit)? = null
): Modifier = this.onKeyEvent { event ->
    if (event.type == KeyEventType.KeyDown) {
        when (event.key) {
            Key.DirectionUp -> { onUp?.invoke(); onUp != null }
            Key.DirectionDown -> { onDown?.invoke(); onDown != null }
            Key.DirectionLeft -> { onLeft?.invoke(); onLeft != null }
            Key.DirectionRight -> { onRight?.invoke(); onRight != null }
            Key.DirectionCenter, Key.Enter -> { onCenter?.invoke(); onCenter != null }
            Key.Back -> { onBack?.invoke(); onBack != null }
            else -> false
        }
    } else false
}

// Remote button mapping for ATV MVP:
// UP    → Previous channel
// DOWN  → Next channel
// LEFT  → Show channel list
// OK    → Show number pad
// BACK  → Dismiss overlay / Long-press for settings
// MENU  → Show settings
```

### Long-Press Detection

```kotlin
// For long-press BACK → Settings
var backPressTime by remember { mutableLongStateOf(0L) }

Modifier.onKeyEvent { event ->
    when {
        event.key == Key.Back && event.type == KeyEventType.KeyDown -> {
            backPressTime = System.currentTimeMillis()
            false // Don't consume yet
        }
        event.key == Key.Back && event.type == KeyEventType.KeyUp -> {
            val pressDuration = System.currentTimeMillis() - backPressTime
            if (pressDuration > 1000) {
                onLongPressBack()
                true
            } else {
                onBack()
                true
            }
        }
        else -> false
    }
}
```

### Key Constants Reference
| Remote Button | Compose Key | KeyCode |
|--------------|-------------|---------|
| D-pad Up | `Key.DirectionUp` | KEYCODE_DPAD_UP |
| D-pad Down | `Key.DirectionDown` | KEYCODE_DPAD_DOWN |
| D-pad Left | `Key.DirectionLeft` | KEYCODE_DPAD_LEFT |
| D-pad Right | `Key.DirectionRight` | KEYCODE_DPAD_RIGHT |
| Select/OK | `Key.DirectionCenter` | KEYCODE_DPAD_CENTER |
| Back | `Key.Back` | KEYCODE_BACK |
| Menu | `Key.Menu` | KEYCODE_MENU |
