package com.example.atv.ui.screens.channelmanagement

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.example.atv.R
import com.example.atv.domain.model.Channel
import com.example.atv.ui.theme.AtvColors
import com.example.atv.ui.theme.AtvTypography
import com.example.atv.ui.util.handleDPadKeyEvents

/**
 * Screen for managing channels (add, edit, delete).
 */
@Composable
fun ChannelManagementScreen(
    onBack: () -> Boolean,
    viewModel: ChannelManagementViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val focusRequester = remember { FocusRequester() }
    
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }
    
    // Show Add Dialog
    if (uiState.showAddDialog) {
        ChannelDialog(
            title = stringResource(R.string.add_channel),
            onSave = { name, url, number ->
                viewModel.addChannel(name, url, number)
            },
            onDismiss = { viewModel.dismissDialog() }
        )
    }
    
    // Show Edit Dialog
    uiState.editingChannel?.let { channel ->
        ChannelDialog(
            title = stringResource(R.string.edit_channel),
            initialName = channel.name,
            initialUrl = channel.streamUrl,
            initialNumber = channel.number.toString(),
            onSave = { name, url, number ->
                viewModel.updateChannel(channel.copy(name = name, streamUrl = url, number = number))
            },
            onDelete = { viewModel.showDeleteConfirm(channel) },
            onDismiss = { viewModel.dismissDialog() }
        )
    }
    
    // Show Delete Confirmation
    uiState.deletingChannel?.let { channel ->
        DeleteConfirmDialog(
            channel = channel,
            onConfirm = { viewModel.deleteChannel(channel) },
            onDismiss = { viewModel.dismissDialog() }
        )
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AtvColors.Background)
            .handleDPadKeyEvents(
                onBack = { onBack() }
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp)
        ) {
            // Header
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
                
                // Add button
                Surface(
                    onClick = { viewModel.showAddDialog() },
                    modifier = Modifier
                        .focusRequester(focusRequester)
                        .height(48.dp),
                    shape = ClickableSurfaceDefaults.shape(
                        shape = RoundedCornerShape(8.dp)
                    ),
                    colors = ClickableSurfaceDefaults.colors(
                        containerColor = AtvColors.Secondary.copy(alpha = 0.2f),
                        focusedContainerColor = AtvColors.Secondary.copy(alpha = 0.4f)
                    ),
                    border = ClickableSurfaceDefaults.border(
                        focusedBorder = androidx.tv.material3.Border(
                            border = androidx.compose.foundation.BorderStroke(
                                width = 2.dp,
                                color = AtvColors.Secondary
                            ),
                            shape = RoundedCornerShape(8.dp)
                        )
                    )
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .padding(horizontal = 24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "+ " + stringResource(R.string.add_channel),
                            style = AtvTypography.titleMedium,
                            color = AtvColors.Secondary
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Channel list
            if (uiState.isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.loading),
                        style = AtvTypography.titleMedium,
                        color = AtvColors.OnSurfaceVariant
                    )
                }
            } else if (uiState.channels.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.no_channels_message),
                        style = AtvTypography.titleMedium,
                        color = AtvColors.OnSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(uiState.channels) { channel ->
                        ChannelItem(
                            channel = channel,
                            onClick = { viewModel.showEditDialog(channel) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ChannelItem(
    channel: Channel,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = ClickableSurfaceDefaults.shape(
            shape = RoundedCornerShape(12.dp)
        ),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = AtvColors.Surface,
            focusedContainerColor = AtvColors.Primary.copy(alpha = 0.2f)
        ),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = androidx.tv.material3.Border(
                border = androidx.compose.foundation.BorderStroke(
                    width = 2.dp,
                    color = AtvColors.Primary
                ),
                shape = RoundedCornerShape(12.dp)
            )
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = channel.number.toString().padStart(3, ' '),
                style = AtvTypography.titleMedium,
                color = AtvColors.Primary
            )
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = channel.name,
                    style = AtvTypography.bodyLarge,
                    color = AtvColors.OnSurface
                )
                Text(
                    text = channel.streamUrl.take(50) + if (channel.streamUrl.length > 50) "..." else "",
                    style = AtvTypography.bodyMedium,
                    color = AtvColors.OnSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ActionChip(
    label: String,
    color: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        modifier = modifier.height(36.dp),
        shape = ClickableSurfaceDefaults.shape(
            shape = RoundedCornerShape(6.dp)
        ),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = color.copy(alpha = 0.1f),
            focusedContainerColor = color.copy(alpha = 0.3f)
        ),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = androidx.tv.material3.Border(
                border = androidx.compose.foundation.BorderStroke(
                    width = 1.dp,
                    color = color
                ),
                shape = RoundedCornerShape(6.dp)
            )
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .padding(horizontal = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                style = AtvTypography.labelMedium,
                color = color
            )
        }
    }
}

/**
 * Dialog for adding or editing a channel.
 */
@Composable
private fun ChannelDialog(
    title: String,
    initialName: String = "",
    initialUrl: String = "",
    initialNumber: String = "",
    onSave: (name: String, url: String, number: Int) -> Unit,
    onDelete: (() -> Unit)? = null,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    var url by remember { mutableStateOf(initialUrl) }
    var number by remember { mutableStateOf(initialNumber) }
    val focusRequester = remember { FocusRequester() }
    
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }
    
    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .background(AtvColors.Surface, RoundedCornerShape(16.dp))
                .padding(24.dp)
                .width(400.dp)
        ) {
            Column {
                Text(
                    text = title,
                    style = AtvTypography.headlineMedium,
                    color = AtvColors.OnSurface
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // Channel Name
                Text(
                    text = stringResource(R.string.channel_name),
                    style = AtvTypography.labelMedium,
                    color = AtvColors.OnSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                BasicTextField(
                    value = name,
                    onValueChange = { name = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(AtvColors.Background, RoundedCornerShape(8.dp))
                        .padding(12.dp)
                        .focusRequester(focusRequester),
                    textStyle = AtvTypography.bodyLarge.copy(color = AtvColors.OnSurface),
                    cursorBrush = SolidColor(AtvColors.Primary),
                    singleLine = true
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Stream URL
                Text(
                    text = stringResource(R.string.stream_url),
                    style = AtvTypography.labelMedium,
                    color = AtvColors.OnSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                BasicTextField(
                    value = url,
                    onValueChange = { url = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(AtvColors.Background, RoundedCornerShape(8.dp))
                        .padding(12.dp),
                    textStyle = AtvTypography.bodyLarge.copy(color = AtvColors.OnSurface),
                    cursorBrush = SolidColor(AtvColors.Primary),
                    singleLine = true
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Channel Number
                Text(
                    text = stringResource(R.string.channel_number),
                    style = AtvTypography.labelMedium,
                    color = AtvColors.OnSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                BasicTextField(
                    value = number,
                    onValueChange = { newValue ->
                        if (newValue.all { it.isDigit() } && newValue.length <= 4) {
                            number = newValue
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(AtvColors.Background, RoundedCornerShape(8.dp))
                        .padding(12.dp),
                    textStyle = AtvTypography.bodyLarge.copy(color = AtvColors.OnSurface),
                    cursorBrush = SolidColor(AtvColors.Primary),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // Action buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Delete button (only shown in edit mode)
                    if (onDelete != null) {
                        ActionChip(
                            label = stringResource(R.string.delete),
                            color = AtvColors.Error,
                            onClick = onDelete
                        )
                    } else {
                        Spacer(modifier = Modifier.width(1.dp))
                    }
                    
                    // Cancel and Save buttons
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        ActionChip(
                            label = stringResource(R.string.cancel),
                            color = AtvColors.OnSurfaceVariant,
                            onClick = onDismiss
                        )
                        ActionChip(
                            label = stringResource(R.string.save),
                            color = AtvColors.Primary,
                            onClick = {
                                val channelNumber = number.toIntOrNull() ?: 0
                                if (name.isNotBlank() && url.isNotBlank() && channelNumber > 0) {
                                    onSave(name, url, channelNumber)
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

/**
 * Confirmation dialog for deleting a channel.
 */
@Composable
private fun DeleteConfirmDialog(
    channel: Channel,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .background(AtvColors.Surface, RoundedCornerShape(16.dp))
                .padding(24.dp)
                .width(350.dp)
        ) {
            Column {
                Text(
                    text = stringResource(R.string.confirm_delete_title),
                    style = AtvTypography.headlineMedium,
                    color = AtvColors.OnSurface
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = stringResource(R.string.confirm_delete_message, channel.name),
                    style = AtvTypography.bodyLarge,
                    color = AtvColors.OnSurfaceVariant
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End)
                ) {
                    ActionChip(
                        label = stringResource(R.string.cancel),
                        color = AtvColors.OnSurfaceVariant,
                        onClick = onDismiss
                    )
                    ActionChip(
                        label = stringResource(R.string.delete),
                        color = AtvColors.Error,
                        onClick = onConfirm
                    )
                }
            }
        }
    }
}
