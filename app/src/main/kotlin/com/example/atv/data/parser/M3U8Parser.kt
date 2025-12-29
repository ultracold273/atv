package com.example.atv.data.parser

import com.example.atv.domain.model.Channel
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Result of parsing an M3U8 file.
 */
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

/**
 * Parser for M3U8/M3U playlist files.
 * 
 * Supports Extended M3U format with EXTINF entries containing:
 * - tvg-id: Channel identifier
 * - tvg-name: Alternative name
 * - tvg-logo: Logo URL
 * - group-title: Category/group
 */
@Singleton
class M3U8Parser @Inject constructor() {
    
    companion object {
        private const val M3U_HEADER = "#EXTM3U"
        private const val EXTINF_PREFIX = "#EXTINF:"
        
        // Regex patterns for attribute extraction
        private val GROUP_TITLE_REGEX = """group-title="([^"]*)"""".toRegex()
        private val TVG_LOGO_REGEX = """tvg-logo="([^"]*)"""".toRegex()
        private val TVG_NAME_REGEX = """tvg-name="([^"]*)"""".toRegex()
    }
    
    /**
     * Parse M3U8 content into a list of channels.
     */
    fun parse(content: String): ParseResult {
        val lines = content.lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        
        if (lines.isEmpty()) {
            return ParseResult.Error("Empty file")
        }
        
        // Validate header
        if (!lines.first().startsWith(M3U_HEADER)) {
            return ParseResult.Error("Invalid M3U8 file: missing #EXTM3U header")
        }
        
        val channels = mutableListOf<Channel>()
        var currentExtInf: String? = null
        var skippedLines = 0
        
        for ((index, line) in lines.withIndex()) {
            try {
                when {
                    line.startsWith(M3U_HEADER) -> {
                        // Skip header
                    }
                    line.startsWith(EXTINF_PREFIX) -> {
                        currentExtInf = line
                    }
                    line.startsWith("#") -> {
                        // Skip other comments/directives
                    }
                    isValidUrl(line) -> {
                        if (currentExtInf != null) {
                            val channel = parseChannel(currentExtInf, line, channels.size + 1)
                            if (channel != null) {
                                channels.add(channel)
                            } else {
                                skippedLines++
                                Timber.w("Failed to parse channel at line $index")
                            }
                        } else {
                            // URL without EXTINF, create minimal channel
                            val name = extractFileName(line)
                            channels.add(
                                Channel(
                                    number = channels.size + 1,
                                    name = name,
                                    streamUrl = line
                                )
                            )
                        }
                        currentExtInf = null
                    }
                }
            } catch (e: Exception) {
                Timber.w(e, "Error parsing line $index: $line")
                skippedLines++
                currentExtInf = null
            }
        }
        
        return if (channels.isEmpty()) {
            ParseResult.Error("No valid channels found in playlist")
        } else {
            Timber.d("Parsed ${channels.size} channels, skipped $skippedLines lines")
            ParseResult.Success(channels, skippedLines)
        }
    }
    
    private fun parseChannel(extinf: String, url: String, number: Int): Channel? {
        // Extract name - everything after the last comma
        val name = extinf.substringAfterLast(",").trim()
        if (name.isBlank()) {
            return null
        }
        
        // Extract optional attributes
        val groupTitle = GROUP_TITLE_REGEX.find(extinf)?.groupValues?.getOrNull(1)
        val logoUrl = TVG_LOGO_REGEX.find(extinf)?.groupValues?.getOrNull(1)
        
        return Channel(
            number = number,
            name = name,
            streamUrl = url,
            groupTitle = groupTitle?.takeIf { it.isNotBlank() },
            logoUrl = logoUrl?.takeIf { it.isNotBlank() }
        )
    }
    
    private fun isValidUrl(line: String): Boolean {
        return line.startsWith("http://") || 
               line.startsWith("https://") || 
               line.startsWith("rtsp://") ||
               line.startsWith("rtmp://")
    }
    
    private fun extractFileName(url: String): String {
        return url.substringAfterLast("/")
            .substringBefore("?")
            .substringBefore(".")
            .ifBlank { "Channel" }
    }
}
