package com.example.atv.util

import android.net.Uri
import timber.log.Timber
import androidx.core.net.toUri

/**
 * URL validation utility for security-safe URL handling.
 * 
 * Only allows http, https, and rtsp schemes to prevent
 * security vulnerabilities from file://, javascript://, etc.
 */
object UrlValidator {
    
    /**
     * Allowed URL schemes for stream playback.
     * - http/https: Standard web URLs
     * - rtsp: Real-Time Streaming Protocol for live streams
     */
    private val ALLOWED_SCHEMES = setOf("http", "https", "rtsp")
    
    /**
     * Blocked schemes that pose security risks.
     */
    private val BLOCKED_SCHEMES = setOf(
        "file",       // Local file access
        "javascript", // Script injection
        "ftp",        // File transfer
        "data",       // Data URLs can contain scripts
        "content"     // Android content provider
    )
    
    /**
     * Validates if a URL has an allowed scheme.
     * 
     * @param url URL string to validate
     * @return true if scheme is http, https, or rtsp; false otherwise
     */
    fun isValidScheme(url: String): Boolean {
        if (url.isBlank()) {
            Timber.d("URL validation failed: empty or blank URL")
            return false
        }
        
        return try {
            val uri = url.toUri()
            when (val scheme = uri.scheme?.lowercase()) {
                null -> {
                    Timber.d("URL validation failed: no scheme found in '$url'")
                    false
                }
                in BLOCKED_SCHEMES -> {
                    Timber.w("URL validation failed: blocked scheme '$scheme' in '$url'")
                    false
                }
                in ALLOWED_SCHEMES -> {
                    // Additional validation: ensure host is present for http/https
                    if (scheme in setOf("http", "https") && uri.host.isNullOrBlank()) {
                        Timber.d("URL validation failed: no host in '$url'")
                        false
                    } else {
                        true
                    }
                }
                else -> {
                    Timber.d("URL validation failed: unknown scheme '$scheme' in '$url'")
                    false
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "URL validation failed: malformed URL '$url'")
            false
        }
    }
    
    /**
     * Validates a URL and returns a Result with the validated URI or an error.
     * 
     * @param url URL string to validate
     * @return Result containing the Uri if valid, or an error otherwise
     */
    fun validate(url: String): Result<Uri> {
        if (url.isBlank()) {
            return Result.failure(IllegalArgumentException("URL cannot be empty"))
        }
        
        return try {
            val uri = url.toUri()
            val scheme = uri.scheme?.lowercase()
            
            when {
                scheme == null -> {
                    Result.failure(IllegalArgumentException("URL has no scheme: $url"))
                }
                scheme in BLOCKED_SCHEMES -> {
                    Result.failure(SecurityException("Blocked URL scheme: $scheme"))
                }
                scheme !in ALLOWED_SCHEMES -> {
                    Result.failure(IllegalArgumentException("Unsupported URL scheme: $scheme"))
                }
                scheme in setOf("http", "https") && uri.host.isNullOrBlank() -> {
                    Result.failure(IllegalArgumentException("URL has no host: $url"))
                }
                else -> Result.success(uri)
            }
        } catch (e: Exception) {
            Result.failure(IllegalArgumentException("Malformed URL: $url", e))
        }
    }
    
    /**
     * Checks if a URL uses HTTPS (secure connection).
     * 
     * @param url URL string to check
     * @return true if URL uses https scheme
     */
    fun isSecure(url: String): Boolean {
        return try {
            url.toUri().scheme?.lowercase() == "https"
        } catch (e: Exception) {
            false
        }
    }
}
