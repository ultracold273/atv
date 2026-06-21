package com.example.atv.domain.model

/**
 * User settings persisted in DataStore.
 */
data class UserPreferences(
    val lastChannelNumber: Int = 1,
    val playlistFilePath: String? = null,
    val autoPlayOnLaunch: Boolean = true,
    val epgEnabled: Boolean = false,
    val udpxyProxy: String? = null
) {
    init {
        require(lastChannelNumber >= 1) { "Last channel number must be >= 1" }
    }

    val hasPlaylist: Boolean get() = playlistFilePath != null

    companion object {
        /** Default udpxy proxy used until the user changes it (spec 005). */
        const val DEFAULT_UDPXY_PROXY = "openwrt:4022"
    }
}
