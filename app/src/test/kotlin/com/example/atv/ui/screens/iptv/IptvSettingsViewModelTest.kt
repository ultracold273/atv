package com.example.atv.ui.screens.iptv

import android.content.Context
import com.example.atv.data.epg.DefaultDeviceDefaultsProvider
import com.example.atv.data.epg.DeviceDefaultsProvider
import com.example.atv.domain.model.ChannelSourceMode
import com.example.atv.domain.model.IptvCredentials
import com.example.atv.domain.model.ProxySettings
import com.example.atv.domain.model.UserPreferences
import com.example.atv.domain.repository.ChannelSourceSettingsStore
import com.example.atv.domain.repository.IptvCredentialsStore
import com.example.atv.domain.repository.PreferencesRepository
import com.example.atv.domain.usecase.ImportResult
import com.example.atv.domain.usecase.LoadPlaylistUseCase
import com.example.atv.domain.usecase.UnifiedImportChannelsUseCase
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.just
import io.mockk.runs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.Random

@OptIn(ExperimentalCoroutinesApi::class)
class IptvSettingsViewModelTest {

    @MockK private lateinit var context: Context
    @MockK private lateinit var store: IptvCredentialsStore
    @MockK private lateinit var sourceStore: ChannelSourceSettingsStore
    @MockK private lateinit var preferencesRepository: PreferencesRepository
    @MockK private lateinit var useCase: UnifiedImportChannelsUseCase
    @MockK private lateinit var loadPlaylistUseCase: LoadPlaylistUseCase

    private val storedFlow = MutableStateFlow<IptvCredentials?>(null)
    private val testDispatcher = StandardTestDispatcher()

    private fun defaults(): DeviceDefaultsProvider = DefaultDeviceDefaultsProvider(
        random = Random(42),
        lanIpSource = { "10.0.0.5" },
    )

    @BeforeEach
    fun setUp() {
        MockKAnnotations.init(this, relaxUnitFun = true)
        Dispatchers.setMain(testDispatcher)
        every { store.observe() } returns storedFlow
        coEvery { store.save(any()) } just runs
        coEvery { store.clear() } just runs
        coEvery { sourceStore.readMode() } returns ChannelSourceMode.DIRECT_CTC
        coEvery { sourceStore.saveMode(any()) } just runs
        coEvery { sourceStore.readProxySettings() } returns null
        coEvery { sourceStore.saveProxySettings(any()) } just runs
        coEvery { sourceStore.clearProxySettings() } just runs
        every { preferencesRepository.getUserPreferences() } returns flowOf(UserPreferences())
        coEvery { preferencesRepository.setUdpxyProxy(any()) } just runs
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun newVm(): IptvSettingsViewModel = IptvSettingsViewModel(
        context = context,
        credentialsStore = store,
        sourceSettingsStore = sourceStore,
        preferencesRepository = preferencesRepository,
        deviceDefaults = defaults(),
        importChannelsUseCase = useCase,
        loadPlaylistUseCase = loadPlaylistUseCase,
    )

    @Test
    fun `applies defaults when store is empty`() = runTest {
        val vm = newVm()
        advanceUntilIdle()

        val s = vm.uiState.value
        assertEquals("", s.userId)
        assertEquals("", s.password)
        assertEquals(32, s.stbId.length)
        assertEquals("10.0.0.5", s.ip)
        assertTrue(s.mac.startsWith("00:00:5E:00:53:"))
        assertTrue(s.authServerUrl.startsWith("http://"))
    }

    @Test
    fun `hydrates from direct credentials store when credentials exist`() = runTest {
        val creds = IptvCredentials(
            userId = "1234567890123",
            password = "000000",
            stbId = "a".repeat(32),
            ip = "10.20.30.40",
            mac = "00:00:5E:00:53:AA",
            authServerUrl = "http://x.com",
        )
        storedFlow.value = creds
        val vm = newVm()
        advanceUntilIdle()

        assertEquals(creds, vm.uiState.value.asCredentials)
    }

    @Test
    fun `selectMode persists active mode`() = runTest {
        val vm = newVm()
        advanceUntilIdle()

        vm.selectMode(ChannelSourceMode.HOME_PROXY)
        advanceUntilIdle()

        assertEquals(ChannelSourceMode.HOME_PROXY, vm.uiState.value.sourceMode)
        coVerify { sourceStore.saveMode(ChannelSourceMode.HOME_PROXY) }
    }

    @Test
    fun `direct ctc import saves credentials and runs unified use case`() = runTest {
        coEvery { useCase() } returns ImportResult.Success(42)
        val vm = newVm()
        advanceUntilIdle()
        vm.setUserId("1234567890123")
        vm.setPassword("000000")

        vm.testAndImport()
        advanceUntilIdle()

        val status = vm.uiState.value.importStatus
        assertTrue(status is ImportStatus.Success)
        assertEquals(42, (status as ImportStatus.Success).importedCount)
        coVerifyOrder {
            store.save(any())
            sourceStore.saveMode(ChannelSourceMode.DIRECT_CTC)
            useCase()
        }
    }

    @Test
    fun `home proxy import saves proxy settings and runs unified use case`() = runTest {
        coEvery { useCase() } returns ImportResult.Success(2)
        val vm = newVm()
        advanceUntilIdle()
        vm.selectMode(ChannelSourceMode.HOME_PROXY)
        vm.setProxyBaseUrl("http://openwrt:8080")
        vm.setProxyAccessToken("token")

        vm.testAndImport()
        advanceUntilIdle()

        assertTrue(vm.uiState.value.importStatus is ImportStatus.Success)
        coVerify { sourceStore.saveProxySettings(ProxySettings("http://openwrt:8080", "token")) }
    }

    @Test
    fun `concurrent testAndImport taps coalesce to one use-case invocation`() = runTest {
        coEvery { useCase() } returns ImportResult.Success(1)
        val vm = newVm()
        advanceUntilIdle()
        vm.setUserId("u"); vm.setPassword("p")

        vm.testAndImport()
        vm.testAndImport()
        vm.testAndImport()
        advanceUntilIdle()

        coVerify(exactly = 1) { useCase() }
    }

    @Test
    fun `clearCredentials clears direct and proxy secrets`() = runTest {
        val vm = newVm()
        advanceUntilIdle()

        vm.requestClearCredentials()
        assertTrue(vm.uiState.value.showClearConfirmation)
        vm.confirmClearCredentials()
        advanceUntilIdle()

        coVerify { store.clear() }
        coVerify { sourceStore.clearProxySettings() }
        assertFalse(vm.uiState.value.showClearConfirmation)
    }
}

