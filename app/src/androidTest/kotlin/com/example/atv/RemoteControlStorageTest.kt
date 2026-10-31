package com.example.atv

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.atv.data.local.secure.AndroidControlTlsIdentity
import com.example.atv.data.local.secure.RemoteControlSettingsStoreImpl
import com.example.atv.domain.model.RemoteControlSettings
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.security.KeyStore

@RunWith(AndroidJUnit4::class)
class RemoteControlStorageTest {
    @Test
    fun identitySurvivesReloadAndPrivateKeyIsNotExportable() = runBlocking {
        val first = AndroidControlTlsIdentity().load()
        val second = AndroidControlTlsIdentity().load()
        assertEquals(first.fingerprint, second.fingerprint)
        val keys = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val privateKey = keys.getKey("atv_remote_control_ec_tls_v1", null)
        assertEquals("EC", privateKey.algorithm)
        assertNull(privateKey.encoded)
    }

    @Test
    fun encryptedSettingsSurviveReloadWithoutPlaintextCredentials() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        try {
            val store = RemoteControlSettingsStoreImpl(context)
            val initial = store.read()
            assertFalse(initial.enabled)
            val enabled = initial.copy(enabled = true, token = RemoteControlSettings.newToken())
            store.save(enabled)
            assertEquals(enabled, RemoteControlSettingsStoreImpl(context).read())
            val raw = context.getSharedPreferences("remote_control_settings", Context.MODE_PRIVATE)
            assertTrue(raw.all.values.none { it.toString().contains(enabled.token) })
        } finally {
            context.deleteSharedPreferences("remote_control_settings")
        }
    }
}
