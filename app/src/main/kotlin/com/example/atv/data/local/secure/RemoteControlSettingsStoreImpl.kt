package com.example.atv.data.local.secure

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.example.atv.domain.model.RemoteControlSettings
import com.example.atv.domain.repository.RemoteControlSettingsStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RemoteControlSettingsStoreImpl @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : RemoteControlSettingsStore {
    private val preferences by lazy {
        val key = MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
        EncryptedSharedPreferences.create(
            context, "remote_control_settings", key,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    override suspend fun read(): RemoteControlSettings = withContext(Dispatchers.IO) {
        val storedToken = preferences.getString("token", null)
        val settings = RemoteControlSettings(
            enabled = storedToken != null && preferences.getBoolean("enabled", false),
            token = storedToken ?: RemoteControlSettings.newToken(),
        )
        if (storedToken == null) save(settings)
        settings
    }

    override suspend fun save(settings: RemoteControlSettings): Unit = withContext(Dispatchers.IO) {
        check(preferences.edit().putString("token", settings.token).putBoolean("enabled", settings.enabled).commit()) {
            "Unable to persist control settings"
        }
    }
}
