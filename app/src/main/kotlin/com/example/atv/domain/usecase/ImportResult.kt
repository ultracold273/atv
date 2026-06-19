package com.example.atv.domain.usecase

/**
 * Outcome of [ImportCtcChannelsUseCase]. Communicates the exact reason for
 * failure so the UI can render granular status messages.
 */
sealed class ImportResult {
    /** Login + fetch + save succeeded; N channels persisted. */
    data class Success(val importedCount: Int) : ImportResult()

    /** Login step failed; nothing was changed. */
    data class LoginFailure(val reason: String) : ImportResult()

    /** Login succeeded but channel fetch / parse failed; nothing was changed. */
    data class FetchFailure(val reason: String) : ImportResult()

    /** Both steps succeeded but the operator returned zero channels; nothing was changed. */
    object NoChannelsReturned : ImportResult()
}
