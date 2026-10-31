package com.example.atv.data.remotecontrol

enum class ControlListenerStatus { DISABLED, STARTING, RUNNING, PAUSED, ERROR }

data class RemoteControlState(
    val enabled: Boolean = false,
    val status: ControlListenerStatus = ControlListenerStatus.DISABLED,
    val endpoints: List<String> = emptyList(),
    val token: String = "",
    val fingerprint: String = "",
) {
    override fun toString(): String = "RemoteControlState(enabled=$enabled, status=$status, credentials redacted)"
}
