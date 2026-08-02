package com.example.atv.ui.screens.channelmanagement

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import com.example.atv.R
import com.example.atv.domain.model.Channel
import com.example.atv.ui.components.AtvConfirmationDialog
import com.example.atv.ui.components.AtvDialog
import com.example.atv.ui.components.AtvDialogButton
import com.example.atv.ui.theme.AtvColors
import com.example.atv.ui.theme.AtvTypography

private const val MAX_CHANNEL_NUMBER_LENGTH = 4

@Composable
internal fun ChannelFormDialog(
    title: String,
    onSave: (name: String, url: String, number: Int) -> Unit,
    onDismiss: () -> Unit,
    initialChannel: Channel? = null,
    onDelete: (() -> Unit)? = null
) {
    var name by remember(initialChannel) { mutableStateOf(initialChannel?.name.orEmpty()) }
    var url by remember(initialChannel) { mutableStateOf(initialChannel?.streamUrl.orEmpty()) }
    var number by remember(initialChannel) {
        mutableStateOf(initialChannel?.number?.toString().orEmpty())
    }
    val nameFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        nameFocusRequester.requestFocus()
    }

    AtvDialog(onDismissRequest = onDismiss) {
        Text(
            text = title,
            style = AtvTypography.headlineMedium,
            color = AtvColors.OnSurface
        )
        Spacer(modifier = Modifier.height(24.dp))
        ChannelFormField(
            label = stringResource(R.string.channel_name),
            value = name,
            onValueChange = { name = it },
            modifier = Modifier.focusRequester(nameFocusRequester)
        )
        Spacer(modifier = Modifier.height(16.dp))
        ChannelFormField(
            label = stringResource(R.string.stream_url),
            value = url,
            onValueChange = { url = it }
        )
        Spacer(modifier = Modifier.height(16.dp))
        ChannelFormField(
            label = stringResource(R.string.channel_number),
            value = number,
            onValueChange = { newValue ->
                if (newValue.all(Char::isDigit) && newValue.length <= MAX_CHANNEL_NUMBER_LENGTH) {
                    number = newValue
                }
            },
            keyboardType = KeyboardType.Number
        )
        Spacer(modifier = Modifier.height(24.dp))
        ChannelFormActions(
            onDelete = onDelete,
            onDismiss = onDismiss,
            onSave = {
                val channelNumber = number.toIntOrNull() ?: 0
                if (name.isNotBlank() && url.isNotBlank() && channelNumber > 0) {
                    onSave(name, url, channelNumber)
                }
            }
        )
    }
}

@Composable
private fun ChannelFormField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Text
) {
    Text(
        text = label,
        style = AtvTypography.labelMedium,
        color = AtvColors.OnSurfaceVariant
    )
    Spacer(modifier = Modifier.height(8.dp))
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .fillMaxWidth()
            .background(AtvColors.Background, RoundedCornerShape(8.dp))
            .height(48.dp),
        textStyle = AtvTypography.bodyLarge.copy(color = AtvColors.OnSurface),
        cursorBrush = SolidColor(AtvColors.Primary),
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        singleLine = true,
        decorationBox = { innerTextField ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                Spacer(modifier = Modifier.width(12.dp))
                innerTextField()
            }
        }
    )
}

@Composable
private fun ChannelFormActions(
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit,
    onSave: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        if (onDelete != null) {
            AtvDialogButton(
                label = stringResource(R.string.delete),
                onClick = onDelete,
                isDestructive = true
            )
        } else {
            Spacer(modifier = Modifier.width(1.dp))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            AtvDialogButton(
                label = stringResource(R.string.cancel),
                onClick = onDismiss
            )
            AtvDialogButton(
                label = stringResource(R.string.save),
                onClick = onSave
            )
        }
    }
}

@Composable
internal fun DeleteChannelDialog(
    channel: Channel,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AtvConfirmationDialog(
        title = stringResource(R.string.confirm_delete_title),
        message = stringResource(R.string.confirm_delete_message, channel.name),
        confirmLabel = stringResource(R.string.delete),
        dismissLabel = stringResource(R.string.cancel),
        onConfirm = onConfirm,
        onDismiss = onDismiss
    )
}
