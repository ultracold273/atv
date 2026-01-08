package com.example.atv.util

import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import android.net.Uri

/**
 * Unit tests for UrlValidator.
 * 
 * Tests cover security validation of URL schemes as per FR-019, FR-020.
 */
@DisplayName("UrlValidator")
class UrlValidatorTest {
    
    @BeforeEach
    fun setup() {
        // Mock Uri.parse for unit tests (requires Robolectric or mocking)
        mockkStatic(Uri::class)
    }
    
    @AfterEach
    fun tearDown() {
        unmockkStatic(Uri::class)
    }
    
    private fun mockUri(url: String, scheme: String?, host: String? = "example.com") {
        val mockUri = io.mockk.mockk<Uri>()
        every { mockUri.scheme } returns scheme
        every { mockUri.host } returns host
        every { Uri.parse(url) } returns mockUri
    }
    
    @Nested
    @DisplayName("U-01: HTTP scheme")
    inner class HttpScheme {
        
        @Test
        fun `should accept http scheme`() {
            // Given
            val url = "http://example.com/stream.m3u8"
            mockUri(url, "http")
            
            // When
            val result = UrlValidator.isValidScheme(url)
            
            // Then
            assertTrue(result)
        }
    }
    
    @Nested
    @DisplayName("U-02: HTTPS scheme")
    inner class HttpsScheme {
        
        @Test
        fun `should accept https scheme`() {
            // Given
            val url = "https://example.com/stream.m3u8"
            mockUri(url, "https")
            
            // When
            val result = UrlValidator.isValidScheme(url)
            
            // Then
            assertTrue(result)
        }
    }
    
    @Nested
    @DisplayName("U-03: RTSP scheme")
    inner class RtspScheme {
        
        @Test
        fun `should accept rtsp scheme`() {
            // Given
            val url = "rtsp://192.168.1.1:554/live"
            mockUri(url, "rtsp")
            
            // When
            val result = UrlValidator.isValidScheme(url)
            
            // Then
            assertTrue(result)
        }
    }
    
    @Nested
    @DisplayName("U-04: File scheme")
    inner class FileScheme {
        
        @Test
        fun `should reject file scheme`() {
            // Given
            val url = "file:///etc/passwd"
            mockUri(url, "file")
            
            // When
            val result = UrlValidator.isValidScheme(url)
            
            // Then
            assertFalse(result)
        }
    }
    
    @Nested
    @DisplayName("U-05: JavaScript scheme")
    inner class JavaScriptScheme {
        
        @Test
        fun `should reject javascript scheme`() {
            // Given
            val url = "javascript:alert(1)"
            mockUri(url, "javascript")
            
            // When
            val result = UrlValidator.isValidScheme(url)
            
            // Then
            assertFalse(result)
        }
    }
    
    @Nested
    @DisplayName("U-06: FTP scheme")
    inner class FtpScheme {
        
        @Test
        fun `should reject ftp scheme`() {
            // Given
            val url = "ftp://server/file"
            mockUri(url, "ftp")
            
            // When
            val result = UrlValidator.isValidScheme(url)
            
            // Then
            assertFalse(result)
        }
    }
    
    @Nested
    @DisplayName("U-07: Malformed URL")
    inner class MalformedUrl {
        
        @Test
        fun `should reject malformed url`() {
            // Given
            val url = "not a url"
            mockUri(url, null)
            
            // When
            val result = UrlValidator.isValidScheme(url)
            
            // Then
            assertFalse(result)
        }
    }
    
    @Nested
    @DisplayName("U-08: Empty string")
    inner class EmptyString {
        
        @Test
        fun `should reject empty string`() {
            // Given
            val url = ""
            
            // When
            val result = UrlValidator.isValidScheme(url)
            
            // Then
            assertFalse(result)
        }
        
        @Test
        fun `should reject blank string`() {
            // Given
            val url = "   "
            
            // When
            val result = UrlValidator.isValidScheme(url)
            
            // Then
            assertFalse(result)
        }
    }
    
    @Nested
    @DisplayName("U-09: URL with port")
    inner class UrlWithPort {
        
        @Test
        fun `should handle url with port`() {
            // Given
            val url = "http://host:8080/path"
            mockUri(url, "http")
            
            // When
            val result = UrlValidator.isValidScheme(url)
            
            // Then
            assertTrue(result)
        }
    }
    
    @Nested
    @DisplayName("U-10: URL with query params")
    inner class UrlWithQueryParams {
        
        @Test
        fun `should handle url with query params`() {
            // Given
            val url = "https://host/path?key=val&other=123"
            mockUri(url, "https")
            
            // When
            val result = UrlValidator.isValidScheme(url)
            
            // Then
            assertTrue(result)
        }
    }
    
    @Nested
    @DisplayName("Additional security tests")
    inner class AdditionalSecurity {
        
        @Test
        fun `should reject data scheme`() {
            // Given
            val url = "data:text/html,<script>alert(1)</script>"
            mockUri(url, "data")
            
            // When
            val result = UrlValidator.isValidScheme(url)
            
            // Then
            assertFalse(result)
        }
        
        @Test
        fun `should reject content scheme`() {
            // Given
            val url = "content://com.example.provider/data"
            mockUri(url, "content")
            
            // When
            val result = UrlValidator.isValidScheme(url)
            
            // Then
            assertFalse(result)
        }
        
        @Test
        fun `should handle uppercase scheme`() {
            // Given
            val url = "HTTPS://example.com/stream"
            mockUri(url, "HTTPS")
            
            // When
            val result = UrlValidator.isValidScheme(url)
            
            // Then
            assertTrue(result)
        }
    }
    
    @Nested
    @DisplayName("Validate method")
    inner class ValidateMethod {
        
        @Test
        fun `should return success for valid URL`() {
            // Given
            val url = "https://example.com/stream"
            mockUri(url, "https")
            
            // When
            val result = UrlValidator.validate(url)
            
            // Then
            assertTrue(result.isSuccess)
        }
        
        @Test
        fun `should return failure for blocked scheme`() {
            // Given
            val url = "javascript:alert(1)"
            mockUri(url, "javascript")
            
            // When
            val result = UrlValidator.validate(url)
            
            // Then
            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull() is SecurityException)
        }
        
        @Test
        fun `should return failure for empty URL`() {
            // Given
            val url = ""
            
            // When
            val result = UrlValidator.validate(url)
            
            // Then
            assertTrue(result.isFailure)
        }
    }
    
    @Nested
    @DisplayName("isSecure method")
    inner class IsSecureMethod {
        
        @Test
        fun `should return true for https`() {
            // Given
            val url = "https://example.com/stream"
            mockUri(url, "https")
            
            // When
            val result = UrlValidator.isSecure(url)
            
            // Then
            assertTrue(result)
        }
        
        @Test
        fun `should return false for http`() {
            // Given
            val url = "http://example.com/stream"
            mockUri(url, "http")
            
            // When
            val result = UrlValidator.isSecure(url)
            
            // Then
            assertFalse(result)
        }
    }
}
