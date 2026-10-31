package com.example.atv.di

import com.example.atv.data.local.secure.AndroidControlTlsIdentity
import com.example.atv.data.local.secure.RemoteControlSettingsStoreImpl
import com.example.atv.data.remotecontrol.AndroidControlNetworkMonitor
import com.example.atv.data.remotecontrol.ControlListenerFactory
import com.example.atv.data.remotecontrol.ControlNetworkMonitor
import com.example.atv.data.remotecontrol.ControlTlsIdentityProvider
import com.example.atv.data.remotecontrol.RemoteControlApi
import com.example.atv.data.remotecontrol.RemoteControlServer
import com.example.atv.domain.repository.RemoteControlSettingsStore
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class RemoteControlModule {
    @Binds
    abstract fun settings(impl: RemoteControlSettingsStoreImpl): RemoteControlSettingsStore

    @Binds
    abstract fun identity(impl: AndroidControlTlsIdentity): ControlTlsIdentityProvider

    @Binds
    abstract fun network(impl: AndroidControlNetworkMonitor): ControlNetworkMonitor

    companion object {
        @Provides
        fun listenerFactory(api: RemoteControlApi): ControlListenerFactory =
            ControlListenerFactory { identity -> RemoteControlServer(api, identity.context) }
    }
}
