package com.example.atv.domain.repository

import com.example.atv.domain.model.IptvCredentials
import kotlinx.coroutines.flow.Flow

/**
 * Encrypted storage for CTC IPTV credentials. Implementation MUST use
 * EncryptedSharedPreferences or equivalent — plain DataStore is not acceptable
 * (spec 005 FR-004).
 */
interface IptvCredentialsStore {
    /** Emits the current credentials, or null when nothing is stored. */
    fun observe(): Flow<IptvCredentials?>

    /** Read-once snapshot, returns null when nothing stored. */
    suspend fun read(): IptvCredentials?

    /** Persist all six fields encrypted. */
    suspend fun save(creds: IptvCredentials)

    /** Wipe all stored values. */
    suspend fun clear()
}
