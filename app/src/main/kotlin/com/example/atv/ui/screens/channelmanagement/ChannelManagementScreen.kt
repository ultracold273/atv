package com.example.atv.ui.screens.channelmanagement

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import com.example.atv.R
import com.example.atv.domain.model.Channel
import com.example.atv.ui.components.AtvButton
import com.example.atv.ui.components.AtvSettingsRow
import com.example.atv.ui.theme.AtvColors
import com.example.atv.ui.theme.AtvTypography
import com.example.atv.ui.util.handleDPadKeyEvents

private const val CHANNEL_NUMBER_WIDTH = 3
private const val URL_PREVIEW_MAX_LENGTH = 50

@Composable
fun ChannelManagementScreen(
    onBack: () -> Boolean,
    viewModel: ChannelManagementViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    ChannelManagementRoute(
        uiState = uiState,
        onBack = onBack,
        onAddChannel = viewModel::showAddDialog,
        onEditChannel = viewModel::showEditDialog,
        onSaveNewChannel = viewModel::addChannel,
        onSaveChannel = viewModel::updateChannel,
        onRequestDelete = viewModel::showDeleteConfirm,
        onDeleteChannel = viewModel::deleteChannel,
        onDismissDialog = viewModel::dismissDialog
    )
}

@Composable
private fun ChannelManagementRoute(
    uiState: ChannelManagementUiState,
    onBack: () -> Boolean,
    onAddChannel: () -> Unit,
    onEditChannel: (Channel) -> Unit,
    onSaveNewChannel: (String, String, Int) -> Unit,
    onSaveChannel: (Channel) -> Unit,
    onRequestDelete: (Channel) -> Unit,
    onDeleteChannel: (Channel) -> Unit,
    onDismissDialog: () -> Unit
) {
    ChannelManagementDialogs(
        uiState = uiState,
        onSaveNewChannel = onSaveNewChannel,
        onSaveChannel = onSaveChannel,
        onRequestDelete = onRequestDelete,
        onDeleteChannel = onDeleteChannel,
        onDismissDialog = onDismissDialog
    )

    ChannelManagementContent(
        uiState = uiState,
        onBack = onBack,
        onAddChannel = onAddChannel,
        onEditChannel = onEditChannel
    )
}

@Composable
private fun ChannelManagementDialogs(
    uiState: ChannelManagementUiState,
    onSaveNewChannel: (String, String, Int) -> Unit,
    onSaveChannel: (Channel) -> Unit,
    onRequestDelete: (Channel) -> Unit,
    onDeleteChannel: (Channel) -> Unit,
    onDismissDialog: () -> Unit
) {
    if (uiState.showAddDialog) {
        ChannelFormDialog(
            title = stringResource(R.string.add_channel),
            onSave = onSaveNewChannel,
            onDismiss = onDismissDialog
        )
    }

    uiState.editingChannel?.let { channel ->
        ChannelFormDialog(
            title = stringResource(R.string.edit_channel),
            initialChannel = channel,
            onSave = { name, url, number ->
                onSaveChannel(channel.copy(name = name, streamUrl = url, number = number))
            },
            onDelete = { onRequestDelete(channel) },
            onDismiss = onDismissDialog
        )
    }

    uiState.deletingChannel?.let { channel ->
        DeleteChannelDialog(
            channel = channel,
            onConfirm = { onDeleteChannel(channel) },
            onDismiss = onDismissDialog
        )
    }
}

@Composable
private fun ChannelManagementContent(
    uiState: ChannelManagementUiState,
    onBack: () -> Boolean,
    onAddChannel: () -> Unit,
    onEditChannel: (Channel) -> Unit
) {
    val addButtonFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        addButtonFocusRequester.requestFocus()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AtvColors.Background)
            .handleDPadKeyEvents(onBack = onBack)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp)
        ) {
            ChannelManagementHeader(
                onAddChannel = onAddChannel,
                addButtonFocusRequester = addButtonFocusRequester
            )
            Spacer(modifier = Modifier.height(24.dp))
            ChannelList(
                channels = uiState.channels,
                isLoading = uiState.isLoading,
                onChannelClick = onEditChannel
            )
        }
    }
}

@Composable
private fun ChannelManagementHeader(
    onAddChannel: () -> Unit,
    addButtonFocusRequester: FocusRequester
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(R.string.channel_management),
            style = AtvTypography.headlineLarge,
            color = AtvColors.OnSurface
        )
        AtvButton(
            label = stringResource(R.string.add_channel),
            leadingText = "+",
            onClick = onAddChannel,
            modifier = Modifier.focusRequester(addButtonFocusRequester),
            color = AtvColors.Secondary,
            height = 48.dp
        )
    }
}

@Composable
private fun ChannelList(
    channels: List<Channel>,
    isLoading: Boolean,
    onChannelClick: (Channel) -> Unit
) {
    when {
        isLoading -> ChannelListMessage(stringResource(R.string.loading))
        channels.isEmpty() -> ChannelListMessage(stringResource(R.string.no_channels_message))
        else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(channels, key = { it.number }) { channel ->
                ChannelRow(channel = channel, onClick = { onChannelClick(channel) })
            }
        }
    }
}

@Composable
private fun ChannelListMessage(message: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = message,
            style = AtvTypography.titleMedium,
            color = AtvColors.OnSurfaceVariant
        )
    }
}

@Composable
private fun ChannelRow(
    channel: Channel,
    onClick: () -> Unit
) {
    AtvSettingsRow(
        title = channel.name,
        subtitle = channel.streamUrl.truncatedUrlPreview(),
        onClick = onClick,
        leadingContent = {
            Text(
                text = channel.number.toString().padStart(CHANNEL_NUMBER_WIDTH, ' '),
                style = AtvTypography.titleMedium,
                color = AtvColors.Primary
            )
        }
    )
}

private fun String.truncatedUrlPreview(): String {
    return take(URL_PREVIEW_MAX_LENGTH) + if (length > URL_PREVIEW_MAX_LENGTH) "..." else ""
}
