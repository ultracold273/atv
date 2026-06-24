package com.example.atv.data.epg

import com.example.atv.domain.usecase.ImportResult
import com.example.atv.domain.usecase.UnifiedImportChannelsUseCase
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

    @Test
    fun `start does nothing when active source cannot bootstrap`() = runTest {
        val useCase: UnifiedImportChannelsUseCase = mockk {
            coEvery { canBootstrap() } returns false
        }
        val bootstrap = IptvSessionBootstrapper(useCase, this)

        bootstrap.start()
        advanceUntilIdle()

        coVerify(exactly = 0) { useCase() }
        assertEquals(null, bootstrap.lastResult.value)
    }

    @Test
    fun `start runs use case and publishes Success result`() = runTest {
        val useCase: UnifiedImportChannelsUseCase = mockk()
        coEvery { useCase.canBootstrap() } returns true
        coEvery { useCase() } returns ImportResult.Success(7)
        val bootstrap = IptvSessionBootstrapper(useCase, this)

        bootstrap.start()
        advanceUntilIdle()

        coVerify(exactly = 1) { useCase() }
        val r = bootstrap.lastResult.value
        assertTrue(r is ImportResult.Success)
        assertEquals(7, (r as ImportResult.Success).importedCount)
    }

    @Test
    fun `start publishes failure result on LoginFailure`() = runTest {
        val useCase: UnifiedImportChannelsUseCase = mockk()
        coEvery { useCase.canBootstrap() } returns true
        coEvery { useCase() } returns ImportResult.LoginFailure("bad")
        val bootstrap = IptvSessionBootstrapper(useCase, this)

        bootstrap.start()
        advanceUntilIdle()

        val r = bootstrap.lastResult.value
        assertTrue(r is ImportResult.LoginFailure)
        assertEquals("bad", (r as ImportResult.LoginFailure).reason)
    }

    @Test
    fun `calling start twice does not double-fire the use case`() = runTest {
        val useCase: UnifiedImportChannelsUseCase = mockk()
        coEvery { useCase.canBootstrap() } returns true
        coEvery { useCase() } returns ImportResult.Success(1)
        val bootstrap = IptvSessionBootstrapper(useCase, this)

        bootstrap.start()
        advanceUntilIdle()
        bootstrap.start()
        advanceUntilIdle()

        coVerify(exactly = 1) { useCase() }
    }
}

