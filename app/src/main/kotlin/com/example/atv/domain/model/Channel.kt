package com.example.atv.domain.model

/**
 * Represents a single IPTV channel from the playlist.
 * 
 * @param number Unique channel number, 1-indexed, assigned during parsing
 * @param name Display name from EXTINF
 * @param streamUrl HLS/RTSP stream URL
 * @param groupTitle Category/group from group-title attribute (optional)
 * @param logoUrl Channel logo URL from tvg-logo attribute (optional)
 */
data class Channel(
    val number: Int,
    val name: String,
    val streamUrl: String,
    val groupTitle: String? = null,
    val logoUrl: String? = null
) {
    init {
        require(number >= 1) { "Channel number must be >= 1" }
        require(name.isNotBlank()) { "Channel name must not be blank" }
        require(streamUrl.isNotBlank()) { "Stream URL must not be blank" }
    }
}
