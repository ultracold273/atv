package com.example.atv.ui.screens.channelmanagement

import android.net.Uri
import com.example.atv.domain.model.Channel
import com.example.atv.domain.repository.ChannelRepository
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.runs
import io.mockk.unmockkStatic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
@DisplayName("ChannelManagementViewModel")
class ChannelManagementViewModelTest {

    @MockK
    private lateinit var channelRepository: ChannelRepository

    private val testDispatcher = StandardTestDispatcher()

    private val channel = Channel(
        number = 7,
        name = "News",
        streamUrl = "https://example.com/news.m3u8"
    )

    @BeforeEach
    fun setUp() {
        MockKAnnotations.init(this)
        Dispatchers.setMain(testDispatcher)
        mockkStatic(Uri::class)
        every { channelRepository.getAllChannels() } returns flowOf(listOf(channel))
    }

    @AfterEach
    fun tearDown() {
        unmockkStatic(Uri::class)
        Dispatchers.resetMain()
    }

    private fun mockUri(url: String, scheme: String, host: String? = "example.com") {
        val uri = mockk<Uri>()
        every { uri.scheme } returns scheme
        every { uri.host } returns host
        every { Uri.parse(url) } returns uri
    }

    @Test
    fun `loads channels and exposes dialog state`() = runTest {
        val viewModel = ChannelManagementViewModel(channelRepository)
        advanceUntilIdle()

        assertEquals(listOf(channel), viewModel.uiState.value.channels)
        assertFalse(viewModel.uiState.value.isLoading)

        viewModel.showAddDialog()
        assertTrue(viewModel.uiState.value.showAddDialog)

        viewModel.dismissDialog()
        assertFalse(viewModel.uiState.value.showAddDialog)

        viewModel.showEditDialog(channel)
        assertSame(channel, viewModel.uiState.value.editingChannel)

        viewModel.showDeleteConfirm(channel)
        assertSame(channel, viewModel.uiState.value.deletingChannel)

        viewModel.dismissDialog()
        assertNull(viewModel.uiState.value.editingChannel)
        assertNull(viewModel.uiState.value.deletingChannel)
    }

    @Test
    fun `adds a trimmed valid channel and dismisses dialog`() = runTest {
        mockUri("https://example.com/sports.m3u8", "https")
        coEvery { channelRepository.addChannel(any()) } just runs
        val viewModel = ChannelManagementViewModel(channelRepository)
        advanceUntilIdle()
        viewModel.showAddDialog()

        viewModel.addChannel("  Sports  ", "  https://example.com/sports.m3u8  ", 8)
        advanceUntilIdle()

        coVerify(exactly = 1) {
            channelRepository.addChannel(
                Channel(8, "Sports", "https://example.com/sports.m3u8")
            )
        }
        assertFalse(viewModel.uiState.value.showAddDialog)
    }

    @Test
    fun `rejects an invalid channel URL`() = runTest {
        mockUri("file:///tmp/news.m3u8", "file", host = null)
        val viewModel = ChannelManagementViewModel(channelRepository)
        advanceUntilIdle()

        viewModel.addChannel("News", "file:///tmp/news.m3u8", 9)

        coVerify(exactly = 0) { channelRepository.addChannel(any()) }
        assertEquals(
            "Invalid URL. Only http, https, and rtsp are allowed.",
            viewModel.uiState.value.errorMessage
        )
    }

    @Test
    fun `updates and deletes channels through repository`() = runTest {
        mockUri(channel.streamUrl, "https")
        coEvery { channelRepository.updateChannel(channel) } just runs
        coEvery { channelRepository.deleteChannel(channel) } just runs
        val viewModel = ChannelManagementViewModel(channelRepository)
        advanceUntilIdle()

        viewModel.showEditDialog(channel)
        viewModel.updateChannel(channel)
        advanceUntilIdle()
        coVerify(exactly = 1) { channelRepository.updateChannel(channel) }
        assertNull(viewModel.uiState.value.editingChannel)

        viewModel.showDeleteConfirm(channel)
        viewModel.deleteChannel(channel)
        advanceUntilIdle()
        coVerify(exactly = 1) { channelRepository.deleteChannel(channel) }
        assertNull(viewModel.uiState.value.deletingChannel)
    }
}
