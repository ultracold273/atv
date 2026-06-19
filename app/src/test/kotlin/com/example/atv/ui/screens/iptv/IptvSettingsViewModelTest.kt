package com.example.atv.ui.screens.iptv

import com.example.atv.data.epg.DefaultDeviceDefaultsProvider
import com.example.atv.data.epg.DeviceDefaultsProvider
import com.example.atv.domain.model.IptvCredentials
import com.example.atv.domain.repository.IptvCredentialsStore
import com.example.atv.domain.usecase.ImportCtcChannelsUseCase
import com.example.atv.domain.usecase.ImportResult
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

    @MockK
    private lateinit var store: IptvCredentialsStore

    @MockK
    private lateinit var useCase: ImportCtcChannelsUseCase

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
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun newVm(): IptvSettingsViewModel =
        IptvSettingsViewModel(store, defaults(), useCase)

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
    fun `hydrates from store when credentials exist`() = runTest {
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
    fun `setUserId updates state`() = runTest {
        val vm = newVm()
        advanceUntilIdle()

        vm.setUserId("9999999999999")
        assertEquals("9999999999999", vm.uiState.value.userId)
    }

    @Test
    fun `isFormValid false until userId and password filled`() = runTest {
        val vm = newVm()
        advanceUntilIdle()

        assertFalse(vm.uiState.value.isFormValid)
        vm.setUserId("1234567890123")
        assertFalse(vm.uiState.value.isFormValid)
        vm.setPassword("000000")
        assertTrue(vm.uiState.value.isFormValid)
    }

    @Test
    fun `testAndImport saves credentials, runs use case, surfaces Success`() = runTest {
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
            useCase()
        }
    }

    @Test
    fun `testAndImport surfaces LoginFailed`() = runTest {
        coEvery { useCase() } returns ImportResult.LoginFailure("bad password")
        val vm = newVm()
        advanceUntilIdle()
        vm.setUserId("u").apply { vm.setPassword("p") }

        vm.testAndImport()
        advanceUntilIdle()

        val status = vm.uiState.value.importStatus
        assertTrue(status is ImportStatus.LoginFailed)
        assertEquals("bad password", (status as ImportStatus.LoginFailed).reason)
    }

    @Test
    fun `testAndImport surfaces NoChannelsReturned`() = runTest {
        coEvery { useCase() } returns ImportResult.NoChannelsReturned
        val vm = newVm()
        advanceUntilIdle()
        vm.setUserId("u"); vm.setPassword("p")

        vm.testAndImport()
        advanceUntilIdle()

        assertTrue(vm.uiState.value.importStatus is ImportStatus.NoChannelsReturned)
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
    fun `clearCredentials shows dialog, confirm wipes store and resets state to defaults`() = runTest {
        storedFlow.value = IptvCredentials(
            userId = "u", password = "p", stbId = "0".repeat(32),
            ip = "i", mac = "m", authServerUrl = "http://x.com",
        )
        val vm = newVm()
        advanceUntilIdle()
        assertEquals("u", vm.uiState.value.userId)

        vm.requestClearCredentials()
        assertTrue(vm.uiState.value.showClearConfirmation)

        vm.confirmClearCredentials()
        advanceUntilIdle()
        coVerify { store.clear() }
        assertFalse(vm.uiState.value.showClearConfirmation)
        // State resets to defaults (UserID/Password empty, defaults reapplied).
        assertEquals("", vm.uiState.value.userId)
        assertEquals("", vm.uiState.value.password)
        assertEquals(32, vm.uiState.value.stbId.length)
    }

    @Test
    fun `dismissClearDialog hides the dialog without clearing`() = runTest {
        val vm = newVm()
        advanceUntilIdle()
        vm.requestClearCredentials()
        vm.dismissClearDialog()

        assertFalse(vm.uiState.value.showClearConfirmation)
        coVerify(exactly = 0) { store.clear() }
    }
}
