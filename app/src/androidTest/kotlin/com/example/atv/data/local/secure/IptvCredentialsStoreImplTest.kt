package com.example.atv.data.local.secure

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.atv.domain.model.IptvCredentials
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class IptvCredentialsStoreImplTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var store: IptvCredentialsStoreImpl

    @Before
    fun setUp() {
        // Use a unique prefs name so concurrent test runs don't collide.
        store = IptvCredentialsStoreImpl(context, prefsName = "iptv_creds_test")
        runBlocking { store.clear() }
    }

    @After
    fun tearDown() {
        runBlocking { store.clear() }
    }

    @Test
    fun read_returnsNullWhenEmpty() = runBlocking {
        assertNull(store.read())
    }

    @Test
    fun saveAndRead_roundTripsAllFields() = runBlocking {
        val creds = IptvCredentials(
            userId = "1234567890123",
            password = "000000",
            stbId = "0".repeat(32),
            ip = "192.0.2.1",
            mac = "00:00:5E:00:53:01",
            authServerUrl = "http://example.com:8298",
        )
        store.save(creds)
        assertEquals(creds, store.read())
    }

    @Test
    fun clear_wipesAllFields() = runBlocking {
        store.save(
            IptvCredentials(
                userId = "u", password = "p", stbId = "0".repeat(32),
                ip = "i", mac = "m", authServerUrl = "http://x.com",
            )
        )
        store.clear()
        assertNull(store.read())
    }

    @Test
    fun observe_emitsNullThenStoredValueAfterSave() = runBlocking {
        assertNull(store.observe().first())
        val creds = IptvCredentials(
            userId = "u2", password = "p2", stbId = "0".repeat(32),
            ip = "1.2.3.4", mac = "00:00:5E:00:53:02", authServerUrl = "https://x.com",
        )
        store.save(creds)
        assertEquals(creds, store.observe().first())
    }
}
