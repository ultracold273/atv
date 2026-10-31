package com.example.atv.domain.model

enum class ChannelControlFailure {
    CATALOG_CHANGED,
    CHANNEL_NOT_FOUND,
    PLAYBACK_UNAVAILABLE,
    CONTROL_BUSY,
    UNAUTHORIZED,
}

sealed interface ChannelSwitchResult {
    data class Accepted(val number: Int, val catalogVersion: String) : ChannelSwitchResult
    data class Rejected(val failure: ChannelControlFailure) : ChannelSwitchResult
}
