package com.example.atv.di

import com.example.atv.data.epg.CtcEpgProvider
import com.example.atv.data.epg.DefaultDeviceDefaultsProvider
import com.example.atv.data.epg.DeviceDefaultsProvider
import com.example.atv.domain.repository.EpgProvider
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

/**
 * Hilt module binding the CTC EPG provider stack.
 *
 * Spec 005 wires the import flow: `CtcEpgProvider` reads user-entered credentials from
 * the encrypted [com.example.atv.domain.repository.IptvCredentialsStore] at fetch time,
 * and `isConfigured` flips true only after a successful import.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class EpgModule {

    @Binds
    @Singleton
    abstract fun bindEpgProvider(impl: CtcEpgProvider): EpgProvider

    @Binds
    @Singleton
    abstract fun bindDeviceDefaultsProvider(
        impl: DefaultDeviceDefaultsProvider
    ): DeviceDefaultsProvider
}

@Module
@InstallIn(SingletonComponent::class)
object EpgNetworkModule {

    /**
     * OkHttp client for the EPG provider stack.
     *
     * Timeouts:
     *   - `connectTimeout` = 10s: budget for the TCP/TLS handshake to the auth or EPG
     *     load-balancer endpoint. A healthy server connects in well under a second; 10s
     *     catches dead/unroutable hosts without making the user wait for the OS-default
     *     ~minute. Tighter (e.g. 5s) would risk false negatives on slow mobile networks
     *     during initial DNS + handshake.
     *   - `readTimeout` = 15s: budget for any single inter-byte gap on an established
     *     connection. The EPG endpoint typically responds in <2s, but Chinese mobile
     *     networks can briefly stall mid-response. 15s tolerates that without making
     *     a genuinely hung request linger for an unbounded time.
     *
     * Note that timeouts here are per-OkHttp-call. `CtcEpgProvider` adds its own
     * "one silent retry on IOException" on top, so the effective worst-case wait for
     * a user-visible failure is roughly `(connectTimeout + readTimeout) * 2 + retry_delay`
     * — under a minute even in the worst case.
     */
    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()
}
