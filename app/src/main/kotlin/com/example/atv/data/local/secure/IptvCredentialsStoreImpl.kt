package com.example.atv.data.local.secure

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.example.atv.domain.model.IptvCredentials
import com.example.atv.domain.repository.IptvCredentialsStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * EncryptedSharedPreferences-backed store. Each value is encrypted with AES-256-GCM
 * under a key bound to the Android Keystore (per AndroidX Security defaults).
 *
 * Threading: all reads/writes are dispatched to `Dispatchers.IO` because
 * `SharedPreferences.edit()` and the underlying encryption are synchronous blocking calls.
 */
@Singleton
class IptvCredentialsStoreImpl @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val prefsName: String = DEFAULT_PREFS_NAME,
) : IptvCredentialsStore {

    private val prefs: SharedPreferences by lazy { buildEncryptedPrefs() }

    override fun observe(): Flow<IptvCredentials?> = callbackFlow {
        // Emit current state immediately, then on every change.
        trySend(readBlocking())
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
            trySend(readBlocking())
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }.flowOn(Dispatchers.IO)

    override suspend fun read(): IptvCredentials? = withContext(Dispatchers.IO) {
        readBlocking()
    }

    override suspend fun save(creds: IptvCredentials): Unit = withContext(Dispatchers.IO) {
        prefs.edit()
            .putString(KEY_USER_ID, creds.userId)
            .putString(KEY_PASSWORD, creds.password)
            .putString(KEY_STB_ID, creds.stbId)
            .putString(KEY_IP, creds.ip)
            .putString(KEY_MAC, creds.mac)
            .putString(KEY_AUTH_URL, creds.authServerUrl)
            .apply()
    }

    override suspend fun clear(): Unit = withContext(Dispatchers.IO) {
        prefs.edit().clear().apply()
    }

    private fun readBlocking(): IptvCredentials? {
        val userId = prefs.getString(KEY_USER_ID, null) ?: return null
        return IptvCredentials(
            userId = userId,
            password = prefs.getString(KEY_PASSWORD, "").orEmpty(),
            stbId = prefs.getString(KEY_STB_ID, "").orEmpty(),
            ip = prefs.getString(KEY_IP, "").orEmpty(),
            mac = prefs.getString(KEY_MAC, "").orEmpty(),
            authServerUrl = prefs.getString(KEY_AUTH_URL, "").orEmpty(),
        )
    }

    private fun buildEncryptedPrefs(): SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedSharedPreferences.create(
            context,
            prefsName,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    private companion object {
        const val DEFAULT_PREFS_NAME = "iptv_credentials"
        const val KEY_USER_ID = "user_id"
        const val KEY_PASSWORD = "password"
        const val KEY_STB_ID = "stb_id"
        const val KEY_IP = "ip"
        const val KEY_MAC = "mac"
        const val KEY_AUTH_URL = "auth_server_url"
    }
}
