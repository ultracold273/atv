# Data Model: ATV - Android TV IPTV Player

**Generated**: 2025-12-26  
**Plan**: [plan.md](plan.md)  
**Spec**: [spec.md](spec.md)  
**Research**: [research.md](research.md)

---

## Table of Contents

1. [Entity Relationship Diagram](#entity-relationship-diagram)
2. [Domain Entities](#domain-entities)
3. [Database Schema](#database-schema)
4. [State Models](#state-models)
5. [M3U8 Format Specification](#m3u8-format-specification)

---

## Entity Relationship Diagram

```
┌─────────────────────────────────────────────────────────────────┐
│                        ATV Data Model                           │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  ┌──────────────┐         ┌──────────────────┐                 │
│  │   Playlist   │────────>│     Channel      │                 │
│  │              │   1:N   │                  │                 │
│  │ - filePath   │         │ - number (PK)    │                 │
│  │ - loadedAt   │         │ - name           │                 │
│  │ - channelCnt │         │ - streamUrl      │                 │
│  └──────────────┘         │ - groupTitle?    │                 │
│         │                 │ - logoUrl?       │                 │
│         │                 └──────────────────┘                 │
│         │                         │                            │
│         │                         │ current                    │
│         v                         v                            │
│  ┌──────────────────────────────────────────────────┐          │
│  │              UserPreferences                      │          │
│  │                                                   │          │
│  │ - lastChannelNumber: Int                         │          │
│  │ - playlistFilePath: String?                      │          │
│  │ - autoPlayOnLaunch: Boolean                      │          │
│  └──────────────────────────────────────────────────┘          │
│                                                                 │
│  ┌──────────────────────────────────────────────────┐          │
│  │              PlaybackState (Runtime)              │          │
│  │                                                   │          │
│  │ - status: Playing | Paused | Buffering | Error   │          │
│  │ - currentChannel: Channel?                        │          │
│  │ - errorMessage: String?                          │          │
│  └──────────────────────────────────────────────────┘          │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

---

## Domain Entities

### Channel

The core entity representing an IPTV channel from the playlist.

```kotlin
// domain/model/Channel.kt
data class Channel(
    val number: Int,           // Unique, 1-indexed, assigned during parsing
    val name: String,          // Display name from EXTINF
    val streamUrl: String,     // HLS/RTSP stream URL
    val groupTitle: String?,   // Category/group from group-title attribute
    val logoUrl: String?       // Channel logo URL from tvg-logo attribute
)
```

**Constraints:**
- `number` must be >= 1
- `name` must not be blank
- `streamUrl` must be valid HTTP(S) URL
- `groupTitle` optional, used for future grouping features
- `logoUrl` optional, not displayed in MVP

**Cross-Reference:**
- FR-002: Extract channel name, URL from M3U8
- FR-006, FR-008: Navigate by channel number

---

### Playlist

Represents a loaded M3U8 playlist (metadata only, channels stored separately).

```kotlin
// domain/model/Playlist.kt
data class Playlist(
    val filePath: String,       // Source file path for reload
    val loadedAt: Instant,      // When playlist was last loaded
    val channelCount: Int       // Total channels in playlist
)
```

**Constraints:**
- `filePath` must be valid file URI
- `channelCount` >= 0

**Cross-Reference:**
- FR-001: Load playlist from file
- FR-003: Validate playlist structure

---

### UserPreferences

User settings persisted in DataStore.

```kotlin
// domain/model/UserPreferences.kt
data class UserPreferences(
    val lastChannelNumber: Int = 1,
    val playlistFilePath: String? = null,
    val autoPlayOnLaunch: Boolean = true
)
```

**Constraints:**
- `lastChannelNumber` must be >= 1
- `playlistFilePath` null until first playlist loaded

**Cross-Reference:**
- FR-016: Remember last channel on exit
- FR-017: Resume last channel on launch

---

### PlaybackState

Runtime state for the media player (not persisted).

```kotlin
// domain/model/PlaybackState.kt
data class PlaybackState(
    val status: PlaybackStatus = PlaybackStatus.Idle,
    val currentChannel: Channel? = null,
    val errorMessage: String? = null,
    val bufferingProgress: Int = 0  // 0-100 for buffering indicator
)

enum class PlaybackStatus {
    Idle,       // No channel selected
    Loading,    // Preparing media
    Buffering,  // Stream buffering
    Playing,    // Active playback
    Error       // Playback failed
}
```

**State Transitions:**
```
Idle ──[select channel]──> Loading
Loading ──[prepared]──> Buffering | Playing
Loading ──[error]──> Error
Buffering ──[buffered]──> Playing
Playing ──[buffer empty]──> Buffering
Playing ──[change channel]──> Loading
Playing ──[error]──> Error
Error ──[retry/change channel]──> Loading
```

**Cross-Reference:**
- FR-004: Auto-play first channel
- FR-012: Show loading indicator
- FR-013: Show error message

---

## Database Schema

### Room Entities

```kotlin
// data/local/db/ChannelEntity.kt
@Entity(tableName = "channels")
data class ChannelEntity(
    @PrimaryKey 
    val number: Int,
    
    @ColumnInfo(name = "name")
    val name: String,
    
    @ColumnInfo(name = "stream_url")
    val streamUrl: String,
    
    @ColumnInfo(name = "group_title")
    val groupTitle: String?,
    
    @ColumnInfo(name = "logo_url")
    val logoUrl: String?,
    
    @ColumnInfo(name = "is_manually_added")
    val isManuallyAdded: Boolean = false
)
```

### SQL Schema

```sql
CREATE TABLE channels (
    number INTEGER PRIMARY KEY NOT NULL,
    name TEXT NOT NULL,
    stream_url TEXT NOT NULL,
    group_title TEXT,
    logo_url TEXT,
    is_manually_added INTEGER NOT NULL DEFAULT 0
);

CREATE INDEX index_channels_group_title ON channels(group_title);
```

### Entity Mapping

```kotlin
// data/local/db/ChannelMapper.kt
fun ChannelEntity.toDomain(): Channel = Channel(
    number = number,
    name = name,
    streamUrl = streamUrl,
    groupTitle = groupTitle,
    logoUrl = logoUrl
)

fun Channel.toEntity(): ChannelEntity = ChannelEntity(
    number = number,
    name = name,
    streamUrl = streamUrl,
    groupTitle = groupTitle,
    logoUrl = logoUrl
)
```

---

## State Models

### UI State

```kotlin
// ui/screens/playback/PlaybackUiState.kt
data class PlaybackUiState(
    val isLoading: Boolean = true,
    val channels: List<Channel> = emptyList(),
    val currentChannelIndex: Int = 0,
    val playbackState: PlaybackState = PlaybackState(),
    
    // Overlay visibility
    val showChannelInfo: Boolean = false,
    val showChannelList: Boolean = false,
    val showNumberPad: Boolean = false,
    val numberPadInput: String = "",
    
    // Error states
    val playlistError: String? = null
) {
    val currentChannel: Channel? 
        get() = channels.getOrNull(currentChannelIndex)
    
    val hasChannels: Boolean 
        get() = channels.isNotEmpty()
}
```

### Navigation Events

```kotlin
// ui/navigation/NavigationEvent.kt
sealed class NavigationEvent {
    data object NavigateToSettings : NavigationEvent()
    data object NavigateToFilePicker : NavigationEvent()
    data object NavigateBack : NavigationEvent()
}
```

---

## M3U8 Format Specification

### Extended M3U Format

ATV supports the Extended M3U format commonly used for IPTV playlists.

#### Header
```
#EXTM3U
```
Required as the first line to identify valid M3U8 files.

#### Channel Entry Format
```
#EXTINF:<duration> [attributes],<channel-name>
<stream-url>
```

#### Supported Attributes

| Attribute | Required | Description | Example |
|-----------|----------|-------------|---------|
| `tvg-id` | No | Channel identifier | `tvg-id="bbc1"` |
| `tvg-name` | No | Alternative name | `tvg-name="BBC One"` |
| `tvg-logo` | No | Logo URL | `tvg-logo="http://..."` |
| `group-title` | No | Category | `group-title="News"` |

#### Example Playlist

```m3u8
#EXTM3U
#EXTINF:-1 tvg-id="cnn" tvg-name="CNN" tvg-logo="https://example.com/cnn.png" group-title="News",CNN International
https://cnn-stream.example.com/live/playlist.m3u8
#EXTINF:-1 tvg-id="espn" group-title="Sports",ESPN HD
https://espn-stream.example.com/live/playlist.m3u8
#EXTINF:-1,Local Channel
http://192.168.1.100:8080/stream.m3u8
```

#### Parsing Rules

1. **Header Validation**: File must start with `#EXTM3U`
2. **EXTINF Processing**: Parse duration, attributes, and trailing name
3. **URL Extraction**: Next non-comment line after EXTINF is stream URL
4. **Channel Numbering**: Assign sequential numbers starting from 1
5. **Attribute Extraction**: Use regex for quoted attribute values
6. **Error Handling**: Skip malformed entries, continue parsing

#### Parse Result

```kotlin
sealed class ParseResult {
    data class Success(
        val channels: List<Channel>,
        val skippedLines: Int = 0
    ) : ParseResult()
    
    data class Error(
        val message: String,
        val lineNumber: Int? = null
    ) : ParseResult()
}
```

#### Validation Requirements (FR-003)

- File must start with `#EXTM3U` header
- At least one valid channel entry required
- Each channel must have:
  - Non-empty name (from EXTINF)
  - Valid HTTP/HTTPS stream URL
- Invalid entries logged and skipped (not fatal)
