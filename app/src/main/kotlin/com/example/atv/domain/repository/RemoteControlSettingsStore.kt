package com.example.atv.domain.repository

import com.example.atv.domain.model.RemoteControlSettings

interface RemoteControlSettingsStore {
    suspend fun read(): RemoteControlSettings
    suspend fun save(settings: RemoteControlSettings)
}
