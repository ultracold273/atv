package com.example.atv.player

import androidx.media3.exoplayer.ExoPlayer
import com.example.atv.domain.model.Channel
import kotlinx.coroutines.flow.StateFlow

interface AtvPlayerController {
    val playerState: StateFlow<PlayerState>
    val player: ExoPlayer?

    fun initialize()
    fun playChannel(channel: Channel, playableStreamUrl: String = channel.streamUrl)
    fun retry()
    fun pause()
    fun resume()
    fun stop()
    fun release()
}
