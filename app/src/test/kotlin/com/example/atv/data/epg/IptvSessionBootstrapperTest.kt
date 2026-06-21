package com.example.atv.data.epg

import com.example.atv.domain.model.IptvCredentials
import com.example.atv.domain.repository.IptvCredentialsStore
import com.example.atv.domain.usecase.ImportCtcChannelsUseCase
import com.example.atv.domain.usecase.ImportResult
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class IptvSessionBootstrapperTest {

    private val creds = IptvCredentials(
        userId = "u", password = "p", stbId = "0".repeat(32),
        ip = "i", mac = "m", authServerUrl = "http://x.com",
    )

    @Test
    fun `start does nothing when no credentials stored`() = runTest {
        val store: IptvCredentialsStore = mockk { coEvery { read() } returns null }
        val useCase: ImportCtcChannelsUseCase = mockk()
        val bootstrap = IptvSessionBootstrapper(store, useCase, this)

        bootstrap.start()
        advanceUntilIdle()

        coVerify(exactly = 0) { useCase() }
        assertEquals(null, bootstrap.lastResult.value)
    }

    @Test
    fun `start does nothing when credentials are incomplete`() = runTest {
        val store: IptvCredentialsStore = mockk {
            coEvery { read() } returns creds.copy(password = "")
        }
        val useCase: ImportCtcChannelsUseCase = mockk()
        val bootstrap = IptvSessionBootstrapper(store, useCase, this)

        bootstrap.start()
        advanceUntilIdle()

        coVerify(exactly = 0) { useCase() }
    }

    @Test
    fun `start runs use case and publishes Success result`() = runTest {
        val store: IptvCredentialsStore = mockk { coEvery { read() } returns creds }
        val useCase: ImportCtcChannelsUseCase = mockk()
        coEvery { useCase() } returns ImportResult.Success(7)
        val bootstrap = IptvSessionBootstrapper(store, useCase, this)

        bootstrap.start()
        advanceUntilIdle()

        coVerify(exactly = 1) { useCase() }
        val r = bootstrap.lastResult.value
        assertTrue(r is ImportResult.Success)
        assertEquals(7, (r as ImportResult.Success).importedCount)
    }

    @Test
    fun `start publishes failure result on LoginFailure`() = runTest {
        val store: IptvCredentialsStore = mockk { coEvery { read() } returns creds }
        val useCase: ImportCtcChannelsUseCase = mockk()
        coEvery { useCase() } returns ImportResult.LoginFailure("bad")
        val bootstrap = IptvSessionBootstrapper(store, useCase, this)

        bootstrap.start()
        advanceUntilIdle()

        val r = bootstrap.lastResult.value
        assertTrue(r is ImportResult.LoginFailure)
        assertEquals("bad", (r as ImportResult.LoginFailure).reason)
    }

    @Test
    fun `calling start twice does not double-fire the use case`() = runTest {
        val store: IptvCredentialsStore = mockk { coEvery { read() } returns creds }
        val useCase: ImportCtcChannelsUseCase = mockk()
        coEvery { useCase() } returns ImportResult.Success(1)
        val bootstrap = IptvSessionBootstrapper(store, useCase, this)

        bootstrap.start()
        advanceUntilIdle()
        bootstrap.start()
        advanceUntilIdle()

        coVerify(exactly = 1) { useCase() }
    }
}
