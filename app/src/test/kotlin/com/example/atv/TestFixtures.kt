package com.example.atv

import com.example.atv.domain.model.Channel

/**
 * Shared test fixtures for unit tests.
 * Contains sample data, M3U8 content, and helper functions.
 */
object TestFixtures {
    
    // Sample Channels
    val SAMPLE_CHANNEL = Channel(
        number = 1,
        name = "Test Channel",
        streamUrl = "https://example.com/stream.m3u8",
        groupTitle = "Test Group",
        logoUrl = "https://example.com/logo.png"
    )
    
    val SAMPLE_CHANNEL_2 = Channel(
        number = 2,
        name = "Another Channel",
        streamUrl = "https://example.com/stream2.m3u8",
        groupTitle = "Test Group",
        logoUrl = null
    )
    
    val SAMPLE_CHANNEL_MINIMAL = Channel(
        number = 1,
        name = "Minimal",
        streamUrl = "http://example.com/minimal.m3u8"
    )
    
    // Sample M3U8 Content
    val VALID_M3U8_SIMPLE = """
        #EXTM3U
        #EXTINF:-1 tvg-name="Test Channel" tvg-logo="https://example.com/logo.png" group-title="Test Group",Test Channel
        https://example.com/stream.m3u8
    """.trimIndent()
    
    val VALID_M3U8_MULTIPLE = """
        #EXTM3U
        #EXTINF:-1 tvg-name="Channel 1" group-title="News",News Channel 1
        https://example.com/news1.m3u8
        #EXTINF:-1 tvg-name="Channel 2" group-title="Sports",Sports Channel
        https://example.com/sports.m3u8
        #EXTINF:-1 tvg-name="Channel 3" group-title="News",News Channel 2
        https://example.com/news2.m3u8
    """.trimIndent()
    
    val VALID_M3U8_WITH_COMMENTS = """
        #EXTM3U
        # This is a comment
        #EXTINF:-1,Simple Channel
        https://example.com/simple.m3u8
    """.trimIndent()
    
    val VALID_M3U8_RTSP = """
        #EXTM3U
        #EXTINF:-1,RTSP Stream
        rtsp://192.168.1.100:554/live
    """.trimIndent()
    
    val VALID_M3U8_UNICODE = """
        #EXTM3U
        #EXTINF:-1 group-title="国际",中文频道
        https://example.com/chinese.m3u8
        #EXTINF:-1 group-title="Émissions",Chaîne Française
        https://example.com/french.m3u8
    """.trimIndent()
    
    val INVALID_M3U8_NO_HEADER = """
        #EXTINF:-1,Test Channel
        https://example.com/stream.m3u8
    """.trimIndent()
    
    val INVALID_M3U8_EMPTY = ""
    
    val INVALID_M3U8_NO_CHANNELS = """
        #EXTM3U
        # Only comments here
    """.trimIndent()
    
    val INVALID_M3U8_BAD_URL = """
        #EXTM3U
        #EXTINF:-1,Bad Channel
        not-a-valid-url
    """.trimIndent()
    
    // M3U8 with mixed valid/invalid entries
    val MIXED_M3U8 = """
        #EXTM3U
        #EXTINF:-1,Valid Channel
        https://example.com/valid.m3u8
        #EXTINF:-1,Invalid Entry
        ftp://invalid.scheme
        #EXTINF:-1,Another Valid
        http://example.com/another.m3u8
    """.trimIndent()
    
    // URL fixtures for validation tests
    object Urls {
        const val VALID_HTTP = "http://example.com/stream.m3u8"
        const val VALID_HTTPS = "https://example.com/stream.m3u8"
        const val VALID_RTSP = "rtsp://192.168.1.1:554/live"
        const val VALID_RTSP_WITH_PATH = "rtsp://server.com/path/to/stream"
        const val VALID_WITH_PORT = "http://example.com:8080/stream"
        const val VALID_WITH_QUERY = "https://example.com/stream?token=abc123"
        
        const val INVALID_FILE = "file:///etc/passwd"
        const val INVALID_JAVASCRIPT = "javascript:alert(1)"
        const val INVALID_FTP = "ftp://server/file"
        const val INVALID_DATA = "data:text/html,<script>alert(1)</script>"
        const val INVALID_MALFORMED = "not a url at all"
        const val INVALID_EMPTY = ""
    }
}
