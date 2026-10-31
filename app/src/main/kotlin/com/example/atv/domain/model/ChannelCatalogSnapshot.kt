package com.example.atv.domain.model

import java.nio.ByteBuffer
import java.security.MessageDigest
import java.util.Base64

/** An immutable selection snapshot. Only its public metadata is sent to controllers. */
class ChannelCatalogSnapshot private constructor(
    val version: String,
    val channels: List<Channel>,
) {
    override fun toString(): String = "ChannelCatalogSnapshot(version=$version, count=" + channels.size + ")"

    companion object {
        fun from(channels: List<Channel>): ChannelCatalogSnapshot {
            val ordered = channels.sortedBy { it.number }
            val digest = MessageDigest.getInstance("SHA-256")
            ordered.forEach { channel ->
                digest.addField(channel.number.toString())
                digest.addField(channel.name)
                digest.addField(channel.streamUrl)
                digest.addField(channel.groupTitle)
                digest.addField(channel.logoUrl)
                digest.addField(channel.channelCode)
            }
            val version = "v1-" + Base64.getUrlEncoder().withoutPadding().encodeToString(digest.digest())
            return ChannelCatalogSnapshot(version, ordered)
        }

        private fun MessageDigest.addField(value: String?) {
            val bytes = value?.toByteArray(Charsets.UTF_8)
            // Length framing distinguishes null, empty strings, and embedded separators.
            update(ByteBuffer.allocate(Int.SIZE_BYTES).putInt(bytes?.size ?: -1).array())
            bytes?.let { update(it) }
        }
    }
}
