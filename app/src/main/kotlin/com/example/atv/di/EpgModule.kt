package com.example.atv.di

import com.example.atv.data.epg.CtcEpgProvider
import com.example.atv.data.epg.DeviceProfile
import com.example.atv.domain.repository.EpgProvider
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Named
import javax.inject.Singleton

/**
 * Hilt module binding the CTC EPG provider stack.
 *
 * In spec 004, the `EpgProvider` is wired but its `isConfigured` flag is permanently
 * false, so no request is ever issued. The sentinel `DeviceProfile` and `authServer`
 * exist purely to make the Hilt graph compile; spec 005 will replace them with
 * user-entered credentials before flipping `isConfigured` to true.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class EpgModule {

    @Binds
    @Singleton
    abstract fun bindEpgProvider(impl: CtcEpgProvider): EpgProvider
}

@Module
@InstallIn(SingletonComponent::class)
object EpgNetworkModule {

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    /**
     * Sentinel device profile for 004. Spec 004 has no login UI; this empty profile
     * satisfies Hilt so the graph compiles. It is never actually dispatched to the
     * CTC endpoint because `CtcEpgProvider.isConfigured` stays false until 005's
     * login flow populates real credentials.
     *
     * TODO(005): replace with a Flow<DeviceProfile?> sourced from a user-entered
     * Settings field; gate `isConfigured` on that Flow being non-null.
     */
    @Provides
    @Singleton
    fun provideDeviceProfile(): DeviceProfile = DeviceProfile(
        userId = "",
        password = "",
        stbId = "",
        ip = "",
        mac = "",
    )

    /**
     * The China Telecom EPG operator endpoint. Hardcoded in 004 ONLY because the
     * sentinel [DeviceProfile] above means no request is ever sent (`isConfigured`
     * stays false for the entire 004 lifetime).
     *
     * SECURITY / PRIVACY GUARDRAIL — 005 MUST address this before flipping
     * `isConfigured` to true:
     *   - This URL points at a real third-party telecom operator. Shipping a public
     *     APK that auto-contacts it the moment a user enables EPG would leak the
     *     user's existence to that operator without consent.
     *   - 005's authentication UI must move this string behind a user-entered
     *     setting (DataStore `iptv_auth_server`, exposed in Settings) before any
     *     code path can actually open a connection to it.
     *
     * NOTE: This URL is NOT contacted in 004 (`isConfigured == false`).
     * TODO(005): move behind a user-entered preference before enabling login.
     */
    @Provides
    @Singleton
    @Named("authServer")
    fun provideAuthServer(): String = "http://itv.jsinfo.net:8298"
}
