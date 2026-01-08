package com.example.atv.data.parser

import com.example.atv.TestFixtures
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("M3U8Parser")
class M3U8ParserTest {
    
    private lateinit var parser: M3U8Parser
    
    @BeforeEach
    fun setup() {
        parser = M3U8Parser()
    }
    
    @Nested
    @DisplayName("P-01: Parse valid extended M3U8")
    inner class ValidParsing {
        
        @Test
        fun `should parse simple valid M3U8`() {
            // Given
            val content = TestFixtures.VALID_M3U8_SIMPLE
            
            // When
            val result = parser.parse(content)
            
            // Then
            assertTrue(result is ParseResult.Success)
            val success = result as ParseResult.Success
            assertEquals(1, success.channels.size)
            assertEquals("Test Channel", success.channels[0].name)
        }
        
        @Test
        fun `should parse M3U8 with multiple channels`() {
            // Given
            val content = TestFixtures.VALID_M3U8_MULTIPLE
            
            // When
            val result = parser.parse(content)
            
            // Then
            assertTrue(result is ParseResult.Success)
            val success = result as ParseResult.Success
            assertEquals(3, success.channels.size)
            assertEquals("News Channel 1", success.channels[0].name)
            assertEquals("Sports Channel", success.channels[1].name)
            assertEquals("News Channel 2", success.channels[2].name)
        }
        
        @Test
        fun `should assign sequential channel numbers`() {
            // Given
            val content = TestFixtures.VALID_M3U8_MULTIPLE
            
            // When
            val result = parser.parse(content) as ParseResult.Success
            
            // Then
            assertEquals(1, result.channels[0].number)
            assertEquals(2, result.channels[1].number)
            assertEquals(3, result.channels[2].number)
        }
    }
    
    @Nested
    @DisplayName("P-02: Parse channel attributes")
    inner class AttributeParsing {
        
        @Test
        fun `should extract group-title attribute`() {
            // Given
            val content = TestFixtures.VALID_M3U8_SIMPLE
            
            // When
            val result = parser.parse(content) as ParseResult.Success
            
            // Then
            assertEquals("Test Group", result.channels[0].groupTitle)
        }
        
        @Test
        fun `should extract tvg-logo attribute`() {
            // Given
            val content = TestFixtures.VALID_M3U8_SIMPLE
            
            // When
            val result = parser.parse(content) as ParseResult.Success
            
            // Then
            assertEquals("https://example.com/logo.png", result.channels[0].logoUrl)
        }
        
        @Test
        fun `should extract stream URL correctly`() {
            // Given
            val content = TestFixtures.VALID_M3U8_SIMPLE
            
            // When
            val result = parser.parse(content) as ParseResult.Success
            
            // Then
            assertEquals("https://example.com/stream.m3u8", result.channels[0].streamUrl)
        }
    }
    
    @Nested
    @DisplayName("P-03: Handle missing attributes gracefully")
    inner class MissingAttributes {
        
        @Test
        fun `should handle channel without logo`() {
            // Given
            val content = """
                #EXTM3U
                #EXTINF:-1 group-title="Group",No Logo Channel
                https://example.com/stream.m3u8
            """.trimIndent()
            
            // When
            val result = parser.parse(content) as ParseResult.Success
            
            // Then
            assertNull(result.channels[0].logoUrl)
            assertEquals("No Logo Channel", result.channels[0].name)
        }
        
        @Test
        fun `should handle channel without group`() {
            // Given
            val content = """
                #EXTM3U
                #EXTINF:-1,No Group Channel
                https://example.com/stream.m3u8
            """.trimIndent()
            
            // When
            val result = parser.parse(content) as ParseResult.Success
            
            // Then
            assertNull(result.channels[0].groupTitle)
            assertEquals("No Group Channel", result.channels[0].name)
        }
        
        @Test
        fun `should handle URL without EXTINF`() {
            // Given - URL directly after header
            val content = """
                #EXTM3U
                https://example.com/direct.m3u8
            """.trimIndent()
            
            // When
            val result = parser.parse(content) as ParseResult.Success
            
            // Then
            assertEquals(1, result.channels.size)
            assertEquals("https://example.com/direct.m3u8", result.channels[0].streamUrl)
        }
    }
    
    @Nested
    @DisplayName("P-04: Empty content handling")
    inner class EmptyContent {
        
        @Test
        fun `should return error for empty string`() {
            // Given
            val content = TestFixtures.INVALID_M3U8_EMPTY
            
            // When
            val result = parser.parse(content)
            
            // Then
            assertTrue(result is ParseResult.Error)
            assertEquals("Empty file", (result as ParseResult.Error).message)
        }
        
        @Test
        fun `should return error for whitespace-only content`() {
            // Given
            val content = "   \n\n   \t\t   "
            
            // When
            val result = parser.parse(content)
            
            // Then
            assertTrue(result is ParseResult.Error)
        }
    }
    
    @Nested
    @DisplayName("P-05: Invalid M3U8 format handling")
    inner class InvalidFormat {
        
        @Test
        fun `should return error when missing EXTM3U header`() {
            // Given
            val content = TestFixtures.INVALID_M3U8_NO_HEADER
            
            // When
            val result = parser.parse(content)
            
            // Then
            assertTrue(result is ParseResult.Error)
            val error = result as ParseResult.Error
            assertTrue(error.message.contains("header"))
        }
        
        @Test
        fun `should return error when no valid channels found`() {
            // Given
            val content = TestFixtures.INVALID_M3U8_NO_CHANNELS
            
            // When
            val result = parser.parse(content)
            
            // Then
            assertTrue(result is ParseResult.Error)
            val error = result as ParseResult.Error
            assertTrue(error.message.contains("No valid channels"))
        }
        
        @Test
        fun `should skip entries with invalid URLs`() {
            // Given
            val content = TestFixtures.MIXED_M3U8
            
            // When
            val result = parser.parse(content) as ParseResult.Success
            
            // Then
            assertEquals(2, result.channels.size) // Only http/https URLs
            assertTrue(result.skippedLines == 0 || result.channels.all { 
                it.streamUrl.startsWith("http://") || it.streamUrl.startsWith("https://") 
            })
        }
    }
    
    @Nested
    @DisplayName("P-06: Multiple groups parsing")
    inner class MultipleGroups {
        
        @Test
        fun `should preserve group-title for each channel`() {
            // Given
            val content = TestFixtures.VALID_M3U8_MULTIPLE
            
            // When
            val result = parser.parse(content) as ParseResult.Success
            
            // Then
            assertEquals("News", result.channels[0].groupTitle)
            assertEquals("Sports", result.channels[1].groupTitle)
            assertEquals("News", result.channels[2].groupTitle)
        }
        
        @Test
        fun `should handle channels from different groups`() {
            // Given
            val content = TestFixtures.VALID_M3U8_MULTIPLE
            
            // When
            val result = parser.parse(content) as ParseResult.Success
            val groups = result.channels.mapNotNull { it.groupTitle }.distinct()
            
            // Then
            assertEquals(2, groups.size)
            assertTrue(groups.contains("News"))
            assertTrue(groups.contains("Sports"))
        }
    }
    
    @Nested
    @DisplayName("P-07: Special characters handling")
    inner class SpecialCharacters {
        
        @Test
        fun `should handle Unicode characters in channel names`() {
            // Given
            val content = TestFixtures.VALID_M3U8_UNICODE
            
            // When
            val result = parser.parse(content) as ParseResult.Success
            
            // Then
            assertEquals(2, result.channels.size)
            assertEquals("中文频道", result.channels[0].name)
            assertEquals("Chaîne Française", result.channels[1].name)
        }
        
        @Test
        fun `should handle Unicode in group titles`() {
            // Given
            val content = TestFixtures.VALID_M3U8_UNICODE
            
            // When
            val result = parser.parse(content) as ParseResult.Success
            
            // Then
            assertEquals("国际", result.channels[0].groupTitle)
            assertEquals("Émissions", result.channels[1].groupTitle)
        }
        
        @Test
        fun `should handle RTSP URLs`() {
            // Given
            val content = TestFixtures.VALID_M3U8_RTSP
            
            // When
            val result = parser.parse(content) as ParseResult.Success
            
            // Then
            assertEquals(1, result.channels.size)
            assertEquals("rtsp://192.168.1.100:554/live", result.channels[0].streamUrl)
        }
        
        @Test
        fun `should skip comment lines`() {
            // Given
            val content = TestFixtures.VALID_M3U8_WITH_COMMENTS
            
            // When
            val result = parser.parse(content) as ParseResult.Success
            
            // Then
            assertEquals(1, result.channels.size)
            assertEquals("Simple Channel", result.channels[0].name)
        }
    }
}
