# Test Contracts

## Unit Test Specifications

This document defines the expected test cases and their contracts for the 002-ci-tests-security feature.

---

## 1. M3U8ParserTest

**SUT**: `com.example.atv.data.parser.M3U8Parser`

### Test Cases

| ID | Test Name | Input | Expected Output |
|----|-----------|-------|-----------------|
| P-01 | `should parse valid extended m3u8` | Valid #EXTM3U content | List of Channel objects |
| P-02 | `should parse channel attributes` | Entry with tvg-id, tvg-name, tvg-logo | Channel with populated fields |
| P-03 | `should handle missing attributes gracefully` | Entry with only URL | Channel with URL, nulls for optional |
| P-04 | `should return empty list for empty content` | Empty string | emptyList() |
| P-05 | `should throw on invalid m3u8 format` | Content without #EXTM3U | ParseException |
| P-06 | `should parse multiple groups` | Entries with different group-title | Channels grouped correctly |
| P-07 | `should handle special characters in names` | Unicode, quotes in names | Correctly parsed strings |

### Contract

```kotlin
interface M3U8ParserContract {
    /**
     * @param content Raw M3U8 playlist content
     * @return List of parsed channels
     * @throws ParseException if content is malformed
     */
    fun parse(content: String): List<Channel>
}
```

---

## 2. ChannelRepositoryTest

**SUT**: `com.example.atv.data.repository.ChannelRepository`

### Test Cases

| ID | Test Name | Setup | Action | Assertion |
|----|-----------|-------|--------|-----------|
| R-01 | `should return channels from cache when available` | Cache populated | getChannels() | Returns cached, no network call |
| R-02 | `should fetch from network when cache empty` | Cache empty | getChannels() | Network called, cache updated |
| R-03 | `should save channel to database` | - | saveChannel(ch) | DAO insert called |
| R-04 | `should emit loading state during fetch` | - | getChannels() | Flow emits Loading first |
| R-05 | `should emit error on network failure` | Network mock fails | getChannels() | Flow emits Error state |
| R-06 | `should refresh cache on forceRefresh` | Cache populated | getChannels(force=true) | Network called |

### Contract

```kotlin
interface ChannelRepositoryContract {
    fun getChannels(forceRefresh: Boolean = false): Flow<Resource<List<Channel>>>
    suspend fun getChannel(id: Long): Channel?
    suspend fun saveChannel(channel: Channel)
    suspend fun deleteChannel(id: Long)
}
```

---

## 3. PlaybackViewModelTest

**SUT**: `com.example.atv.ui.playback.PlaybackViewModel`

### Test Cases

| ID | Test Name | Setup | Action | State Assertion |
|----|-----------|-------|--------|-----------------|
| V-01 | `should be idle initially` | - | - | UiState.Idle |
| V-02 | `should emit loading when channel selected` | - | selectChannel(1) | UiState.Loading |
| V-03 | `should emit playing after load success` | Mock success | selectChannel(1) | UiState.Playing |
| V-04 | `should emit error on load failure` | Mock failure | selectChannel(1) | UiState.Error |
| V-05 | `should pause playback` | Playing | pause() | UiState.Paused |
| V-06 | `should resume playback` | Paused | play() | UiState.Playing |
| V-07 | `should handle channel switch` | Playing ch1 | selectChannel(2) | Loading → Playing ch2 |

### Contract

```kotlin
interface PlaybackViewModelContract {
    val uiState: StateFlow<PlaybackUiState>
    val currentChannel: StateFlow<Channel?>
    
    fun selectChannel(id: Long)
    fun play()
    fun pause()
    fun stop()
}
```

---

## 4. UrlValidatorTest (New)

**SUT**: `com.example.atv.util.UrlValidator`

### Test Cases

| ID | Test Name | Input | Expected |
|----|-----------|-------|----------|
| U-01 | `should accept http scheme` | `http://example.com/stream` | true |
| U-02 | `should accept https scheme` | `https://example.com/stream` | true |
| U-03 | `should accept rtsp scheme` | `rtsp://192.168.1.1:554/live` | true |
| U-04 | `should reject file scheme` | `file:///etc/passwd` | false |
| U-05 | `should reject javascript scheme` | `javascript:alert(1)` | false |
| U-06 | `should reject ftp scheme` | `ftp://server/file` | false |
| U-07 | `should reject malformed url` | `not a url` | false |
| U-08 | `should reject empty string` | `` | false |
| U-09 | `should handle url with port` | `http://host:8080/path` | true |
| U-10 | `should handle url with query params` | `https://host/path?key=val` | true |

### Contract

```kotlin
object UrlValidator {
    private val ALLOWED_SCHEMES = setOf("http", "https", "rtsp")
    
    /**
     * Validates URL scheme against allowlist.
     * @param url URL string to validate
     * @return true if scheme is http, https, or rtsp
     */
    fun isValidScheme(url: String): Boolean
}
```

---

## Test Doubles

### MockK Setup Patterns

```kotlin
// Repository mock
@MockK
lateinit var repository: ChannelRepository

// Network mock
@MockK
lateinit var api: PlaylistApi

// Database mock (prefer in-memory Room)
@get:Rule
val instantTaskExecutorRule = InstantTaskExecutorRule()
```

### Common Test Fixtures

```kotlin
object TestFixtures {
    val SAMPLE_CHANNEL = Channel(
        id = 1,
        name = "Test Channel",
        url = "https://example.com/stream.m3u8",
        logo = "https://example.com/logo.png",
        group = "Test Group"
    )
    
    val VALID_M3U8 = """
        #EXTM3U
        #EXTINF:-1 tvg-id="test" tvg-name="Test" group-title="Group",Test Channel
        https://example.com/stream.m3u8
    """.trimIndent()
}
```

---

## Coverage Requirements

| Package | Min Coverage | Priority |
|---------|-------------|----------|
| `data.parser` | 80% | High |
| `data.repository` | 70% | High |
| `domain.usecase` | 70% | Medium |
| `ui.*.viewmodel` | 80% | Medium |
| `util` | 80% | High |

**Exclusions** (0% expected):
- Generated Hilt code
- Generated Room code
- Compose UI code
- BuildConfig
