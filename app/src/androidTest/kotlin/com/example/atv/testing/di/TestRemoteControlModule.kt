package com.example.atv.testing.di

import com.example.atv.data.local.secure.AndroidControlTlsIdentity
import com.example.atv.data.remotecontrol.ControlListenerFactory
import com.example.atv.data.remotecontrol.ControlNetworkMonitor
import com.example.atv.data.remotecontrol.ControlTlsIdentityProvider
import com.example.atv.data.remotecontrol.RemoteControlApi
import com.example.atv.data.remotecontrol.RemoteControlServer
import com.example.atv.di.RemoteControlModule
import com.example.atv.domain.model.RemoteControlSettings
import com.example.atv.domain.repository.RemoteControlSettingsStore
import dagger.Module
import dagger.Provides
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import kotlinx.coroutines.flow.emptyFlow
import java.net.InetSocketAddress
import javax.inject.Singleton

@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [RemoteControlModule::class])
object TestRemoteControlModule {
    @Provides
    @Singleton
    fun settings(): RemoteControlSettingsStore = object : RemoteControlSettingsStore {
        private var value = RemoteControlSettings(false, RemoteControlSettings.newToken())
        override suspend fun read() = value
        override suspend fun save(settings: RemoteControlSettings) { value = settings }
    }

    @Provides
    fun identity(impl: AndroidControlTlsIdentity): ControlTlsIdentityProvider = impl

    @Provides
    fun network(): ControlNetworkMonitor = object : ControlNetworkMonitor {
        override fun changes() = emptyFlow<Unit>()
        override fun endpoints() = listOf("https://127.0.0.1:8765")
    }

    @Provides
    fun listenerFactory(api: RemoteControlApi): ControlListenerFactory = ControlListenerFactory {
        RemoteControlServer(api, it.context, InetSocketAddress("127.0.0.1", 8765))
    }
}
