package com.example.atv.testing.fakes

import android.content.Context
import androidx.media3.exoplayer.ExoPlayer
import com.example.atv.domain.model.Channel
import com.example.atv.player.AtvPlayerController
import com.example.atv.player.PlayerState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

@Suppress("UNUSED_PARAMETER")
class FakeAtvPlayer(context: Context) : AtvPlayerController {
    private val state = MutableStateFlow<PlayerState>(PlayerState.Idle)

    override val playerState: StateFlow<PlayerState> = state
    override val player: ExoPlayer? = null

    override fun initialize() = Unit

    override fun playChannel(channel: Channel, playableStreamUrl: String) {
        state.value = PlayerState.Playing(channel)
    }

    override fun retry() = Unit
    override fun pause() = Unit
    override fun resume() = Unit
    override fun stop() {
        state.value = PlayerState.Idle
    }
    override fun release() = Unit
}
