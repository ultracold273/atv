package com.example.atv.domain.model

/**
 * Represents a single IPTV channel from the playlist.
 *
 * @param number Unique channel number, 1-indexed
 * @param name Display name from EXTINF or operator-provided ChannelName
 * @param streamUrl HLS/RTSP stream URL
 * @param groupTitle Category/group (optional)
 * @param logoUrl Channel logo URL (optional)
 * @param channelCode Opaque per-provider EPG channel identifier. Null for M3U8-loaded
 *   channels; populated by spec 005's CTC import for operator-provided channels.
 */
data class Channel(
    val number: Int,
    val name: String,
    val streamUrl: String,
    val groupTitle: String? = null,
    val logoUrl: String? = null,
    val channelCode: String? = null
) {
    init {
        require(number >= 1) { "Channel number must be >= 1" }
        require(name.isNotBlank()) { "Channel name must not be blank" }
        require(streamUrl.isNotBlank()) { "Stream URL must not be blank" }
    }
}
