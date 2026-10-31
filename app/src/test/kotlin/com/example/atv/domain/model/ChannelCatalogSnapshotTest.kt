package com.example.atv.domain.model

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class ChannelCatalogSnapshotTest {
    private val channel = Channel(17, "CCTV-1", "https://example.invalid/live", "央视", "logo", "code")

    @Test
    fun snapshotIsOrderedAndIndependentOfInputOrder() {
        val channels = mutableListOf(channel.copy(number = 21), channel)
        val snapshot = ChannelCatalogSnapshot.from(channels)
        assertEquals(listOf(17, 21), snapshot.channels.map { it.number })
        assertEquals(snapshot.version, ChannelCatalogSnapshot.from(channels.reversed()).version)
        channels.clear()
        assertEquals(2, snapshot.channels.size)
        assertFalse(snapshot.toString().contains(channel.streamUrl))
    }

    @Test
    fun everyChannelFieldParticipatesInVersioning() {
        val original = ChannelCatalogSnapshot.from(listOf(channel)).version
        val changes = listOf(
            channel.copy(number = 18), channel.copy(name = "renamed"), channel.copy(streamUrl = "changed"),
            channel.copy(groupTitle = "changed"), channel.copy(logoUrl = null), channel.copy(channelCode = "changed"),
        )
        changes.forEach { assertNotEquals(original, ChannelCatalogSnapshot.from(listOf(it)).version) }
        assertNotEquals(original, ChannelCatalogSnapshot.from(emptyList()).version)
    }

    @Test
    fun nullAndEmptyMetadataAndFieldBoundariesAreDistinct() {
        fun version(value: Channel) = ChannelCatalogSnapshot.from(listOf(value)).version
        assertNotEquals(version(channel.copy(groupTitle = null)), version(channel.copy(groupTitle = "")))
        assertNotEquals(version(channel.copy(name = "a|b", groupTitle = "c")),
            version(channel.copy(name = "a", groupTitle = "b|c")))
        val empty = ChannelCatalogSnapshot.from(emptyList())
        assertEquals(empty.version, ChannelCatalogSnapshot.from(emptyList()).version)
        assertTrue(empty.version.length in 1..128)
    }

    @Test
    fun generatedCredentialsAreStrongAndNeverIncludedInDebugStrings() {
        val token = RemoteControlSettings.newToken()
        assertEquals(32, java.util.Base64.getUrlDecoder().decode(token).size)
        assertNotEquals(token, RemoteControlSettings.newToken())
        assertFalse(RemoteControlSettings(true, token).toString().contains(token))
    }
}
