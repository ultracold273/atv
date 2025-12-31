package com.example.atv.player

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.example.atv.domain.model.Channel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * ExoPlayer wrapper that provides a clean API for channel playback.
 */
@Singleton
class AtvPlayer @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    private var exoPlayer: ExoPlayer? = null
    private var currentChannel: Channel? = null
    
    private val _playerState = MutableStateFlow<PlayerState>(PlayerState.Idle)
    val playerState: StateFlow<PlayerState> = _playerState.asStateFlow()
    
    /**
     * Get the underlying ExoPlayer instance for use with PlayerView.
     */
    val player: ExoPlayer?
        get() = exoPlayer
    
    private val playerListener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            val channel = currentChannel ?: return
            
            when (playbackState) {
                Player.STATE_IDLE -> {
                    Timber.d("Player state: IDLE")
                }
                Player.STATE_BUFFERING -> {
                    Timber.d("Player state: BUFFERING")
                    _playerState.value = PlayerState.Buffering(channel)
                }
                Player.STATE_READY -> {
                    Timber.d("Player state: READY")
                    if (exoPlayer?.isPlaying == true) {
                        _playerState.value = PlayerState.Playing(channel)
                    }
                }
                Player.STATE_ENDED -> {
                    Timber.d("Player state: ENDED")
                    // For live streams, this shouldn't happen normally
                    // Could retry or show error
                }
            }
        }
        
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            val channel = currentChannel ?: return
            if (isPlaying) {
                Timber.d("Playback started for: ${channel.name}")
                _playerState.value = PlayerState.Playing(channel)
            }
        }
        
        override fun onPlayerError(error: PlaybackException) {
            val channel = currentChannel
            Timber.e(error, "Player error for channel: ${channel?.name}")
            _playerState.value = PlayerState.Error(
                channel = channel,
                message = error.message ?: "Playback error",
                exception = error
            )
        }
    }
    
    /**
     * Initialize the player. Must be called before playing.
     */
    @OptIn(UnstableApi::class)
    fun initialize() {
        if (exoPlayer != null) {
            Timber.d("Player already initialized")
            return
        }
        
        Timber.d("Initializing ExoPlayer")
        exoPlayer = ExoPlayer.Builder(context)
            .setHandleAudioBecomingNoisy(true)
            .build()
            .apply {
                addListener(playerListener)
                playWhenReady = true
            }
    }
    
    /**
     * Play a channel.
     */
    fun playChannel(channel: Channel) {
        Timber.d("Playing channel: ${channel.number} - ${channel.name}")
        currentChannel = channel
        _playerState.value = PlayerState.Loading(channel)
        
        val player = exoPlayer
        if (player == null) {
            Timber.w("Player not initialized, initializing now")
            initialize()
        }
        
        exoPlayer?.apply {
            stop()
            clearMediaItems()
            
            val mediaItem = MediaItem.fromUri(channel.streamUrl)
            setMediaItem(mediaItem)
            prepare()
            play()
        }
    }
    
    /**
     * Retry playing the current channel.
     */
    fun retry() {
        currentChannel?.let { playChannel(it) }
    }
    
    /**
     * Pause playback.
     */
    fun pause() {
        exoPlayer?.pause()
    }
    
    /**
     * Resume playback.
     */
    fun resume() {
        exoPlayer?.play()
    }
    
    /**
     * Stop playback and clear media.
     */
    fun stop() {
        Timber.d("Stopping playback")
        exoPlayer?.stop()
        exoPlayer?.clearMediaItems()
        currentChannel = null
        _playerState.value = PlayerState.Idle
    }
    
    /**
     * Release the player. Call when done with the player.
     */
    fun release() {
        Timber.d("Releasing player")
        exoPlayer?.removeListener(playerListener)
        exoPlayer?.release()
        exoPlayer = null
        currentChannel = null
        _playerState.value = PlayerState.Idle
    }
}
