package com.example.atv.domain.model

/**
 * User settings persisted in DataStore.
 * 
 * @param lastChannelNumber Last watched channel number (for resume)
 * @param playlistFilePath Path to the loaded playlist file
 * @param autoPlayOnLaunch Whether to auto-play on app launch
 */
data class UserPreferences(
    val lastChannelNumber: Int = 1,
    val playlistFilePath: String? = null,
    val autoPlayOnLaunch: Boolean = true
) {
    init {
        require(lastChannelNumber >= 1) { "Last channel number must be >= 1" }
    }
    
    val hasPlaylist: Boolean get() = playlistFilePath != null
}
