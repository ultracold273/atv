package com.example.atv.ui.screens.iptv.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.example.atv.R
import com.example.atv.domain.model.ChannelSourceMode
import com.example.atv.ui.theme.AtvColors
import com.example.atv.ui.theme.AtvTypography

@Composable
fun SourceModeSelector(
    selected: ChannelSourceMode,
    active: ChannelSourceMode,
    onSelected: (ChannelSourceMode) -> Unit,
) {
    val m3u8FocusRequester = remember { FocusRequester() }
    val directFocusRequester = remember { FocusRequester() }
    val proxyFocusRequester = remember { FocusRequester() }

    LaunchedEffect(active) {
        when (active) {
            ChannelSourceMode.M3U8 -> m3u8FocusRequester
            ChannelSourceMode.DIRECT_CTC -> directFocusRequester
            ChannelSourceMode.HOME_PROXY -> proxyFocusRequester
        }.requestFocus()
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SourceModeButton(
            label = stringResource(R.string.channel_source_m3u8),
            selected = selected == ChannelSourceMode.M3U8,
            active = active == ChannelSourceMode.M3U8,
            focusRequester = m3u8FocusRequester,
            onClick = { onSelected(ChannelSourceMode.M3U8) },
            modifier = Modifier.weight(1f),
        )
        SourceModeButton(
            label = stringResource(R.string.channel_source_direct_ctc),
            selected = selected == ChannelSourceMode.DIRECT_CTC,
            active = active == ChannelSourceMode.DIRECT_CTC,
            focusRequester = directFocusRequester,
            onClick = { onSelected(ChannelSourceMode.DIRECT_CTC) },
            modifier = Modifier.weight(1f),
        )
        SourceModeButton(
            label = stringResource(R.string.channel_source_home_proxy),
            selected = selected == ChannelSourceMode.HOME_PROXY,
            active = active == ChannelSourceMode.HOME_PROXY,
            focusRequester = proxyFocusRequester,
            onClick = { onSelected(ChannelSourceMode.HOME_PROXY) },
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun SourceModeButton(
    label: String,
    selected: Boolean,
    active: Boolean,
    focusRequester: FocusRequester,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val baseColor = when {
        active -> AtvColors.Secondary
        selected -> AtvColors.Primary
        else -> AtvColors.Surface
    }
    val textColor = when {
        active -> AtvColors.Secondary
        selected -> AtvColors.Primary
        else -> AtvColors.OnSurface
    }
    Surface(
        onClick = onClick,
        modifier = modifier
            .height(52.dp)
            .focusRequester(focusRequester)
            .onFocusChanged { state -> if (state.isFocused) onClick() },
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(8.dp)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = when {
                active -> AtvColors.Secondary.copy(alpha = 0.22f)
                selected -> AtvColors.Primary.copy(alpha = 0.18f)
                else -> AtvColors.Surface
            },
            focusedContainerColor = baseColor.copy(alpha = if (active) 0.32f else 0.26f),
        ),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(
                border = BorderStroke(width = 2.dp, color = if (active) AtvColors.Secondary else AtvColors.Primary),
                shape = RoundedCornerShape(8.dp),
            ),
        ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = label,
                style = AtvTypography.labelLarge,
                color = textColor,
                textAlign = TextAlign.Center,
            )
        }
    }
}
