package com.example.atv.domain.model

import java.time.Instant

/**
 * Represents a loaded M3U8 playlist (metadata only, channels stored separately).
 * 
 * @param filePath Source file path for reload
 * @param loadedAt When playlist was last loaded
 * @param channelCount Total channels in playlist
 */
data class Playlist(
    val filePath: String,
    val loadedAt: Instant,
    val channelCount: Int
) {
    init {
        require(filePath.isNotBlank()) { "File path must not be blank" }
        require(channelCount >= 0) { "Channel count must be >= 0" }
    }
}
