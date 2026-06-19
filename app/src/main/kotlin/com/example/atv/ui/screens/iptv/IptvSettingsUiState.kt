package com.example.atv.ui.screens.iptv

import com.example.atv.domain.model.IptvCredentials

/**
 * State for the IPTV setup screen. Holds the six editable field values plus
 * import-flow status and confirmation-dialog visibility.
 */
data class IptvSettingsUiState(
    val userId: String = "",
    val password: String = "",
    val stbId: String = "",
    val ip: String = "",
    val mac: String = "",
    val authServerUrl: String = "",
    val importStatus: ImportStatus = ImportStatus.Idle,
    val showClearConfirmation: Boolean = false,
) {
    /** The current form values as an [IptvCredentials] record. */
    val asCredentials: IptvCredentials
        get() = IptvCredentials(userId, password, stbId, ip, mac, authServerUrl)

    /** Form is valid and the "Test & import" button should be enabled. */
    val isFormValid: Boolean
        get() = asCredentials.isComplete && !importStatus.isInProgress
}

/**
 * Real-time status of the "Test & import" flow. The UI maps each variant to a
 * localized string. `Idle` is the initial and post-completion state.
 */
sealed class ImportStatus {
    object Idle : ImportStatus()
    object LoggingIn : ImportStatus()
    object FetchingChannels : ImportStatus()
    data class Success(val importedCount: Int) : ImportStatus()
    data class LoginFailed(val reason: String) : ImportStatus()
    data class FetchFailed(val reason: String) : ImportStatus()
    object NoChannelsReturned : ImportStatus()

    val isInProgress: Boolean
        get() = this is LoggingIn || this is FetchingChannels

    val isTerminal: Boolean
        get() = this is Success || this is LoginFailed ||
            this is FetchFailed || this is NoChannelsReturned
}
