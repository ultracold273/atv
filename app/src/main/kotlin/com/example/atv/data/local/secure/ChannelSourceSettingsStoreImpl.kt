package com.example.atv.data.local.secure

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.example.atv.domain.model.ChannelSourceMode
import com.example.atv.domain.model.ProxySettings
import com.example.atv.domain.repository.ChannelSourceSettingsStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChannelSourceSettingsStoreImpl @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : ChannelSourceSettingsStore {

    private val prefs: SharedPreferences by lazy { buildEncryptedPrefs() }

    override fun observeMode(): Flow<ChannelSourceMode> = callbackFlow {
        trySend(readModeBlocking())
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == KEY_MODE) trySend(readModeBlocking())
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }.flowOn(Dispatchers.IO)

    override suspend fun readMode(): ChannelSourceMode = withContext(Dispatchers.IO) {
        readModeBlocking()
    }

    override suspend fun saveMode(mode: ChannelSourceMode): Unit = withContext(Dispatchers.IO) {
        prefs.edit().putString(KEY_MODE, mode.name).apply()
    }

    override fun observeProxySettings(): Flow<ProxySettings?> = callbackFlow {
        trySend(readProxyBlocking())
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
            trySend(readProxyBlocking())
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }.flowOn(Dispatchers.IO)

    override suspend fun readProxySettings(): ProxySettings? = withContext(Dispatchers.IO) {
        readProxyBlocking()
    }

    override suspend fun saveProxySettings(settings: ProxySettings): Unit = withContext(Dispatchers.IO) {
        prefs.edit()
            .putString(KEY_PROXY_BASE_URL, settings.proxyBaseUrl)
            .putString(KEY_PROXY_ACCESS_TOKEN, settings.accessToken)
            .apply()
    }

    override suspend fun clearProxySettings(): Unit = withContext(Dispatchers.IO) {
        prefs.edit()
            .remove(KEY_PROXY_BASE_URL)
            .remove(KEY_PROXY_ACCESS_TOKEN)
            .apply()
    }

    private fun readModeBlocking(): ChannelSourceMode {
        val raw = prefs.getString(KEY_MODE, null) ?: return ChannelSourceMode.DIRECT_CTC
        return runCatching { ChannelSourceMode.valueOf(raw) }.getOrDefault(ChannelSourceMode.DIRECT_CTC)
    }

    private fun readProxyBlocking(): ProxySettings? {
        val baseUrl = prefs.getString(KEY_PROXY_BASE_URL, null) ?: return null
        return ProxySettings(
            proxyBaseUrl = baseUrl,
            accessToken = prefs.getString(KEY_PROXY_ACCESS_TOKEN, "").orEmpty(),
        )
    }

    private fun buildEncryptedPrefs(): SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedSharedPreferences.create(
            context,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    private companion object {
        const val PREFS_NAME = "channel_source_settings"
        const val KEY_MODE = "mode"
        const val KEY_PROXY_BASE_URL = "proxy_base_url"
        const val KEY_PROXY_ACCESS_TOKEN = "proxy_access_token"
    }
}
