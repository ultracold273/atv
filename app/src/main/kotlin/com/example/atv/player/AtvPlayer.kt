package com.example.atv.player

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DecoderReuseEvaluation
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
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
    
    @OptIn(UnstableApi::class)
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
        
        /**
         * Log track information when tracks change.
         * This helps diagnose audio codec issues on different devices.
         */
        override fun onTracksChanged(tracks: Tracks) {
            val channel = currentChannel
            Timber.d("========== TRACK INFO for: ${channel?.name} ==========")
            
            for (group in tracks.groups) {
                val trackType = when (group.type) {
                    C.TRACK_TYPE_VIDEO -> "VIDEO"
                    C.TRACK_TYPE_AUDIO -> "AUDIO"
                    C.TRACK_TYPE_TEXT -> "TEXT"
                    else -> "OTHER(${group.type})"
                }
                
                Timber.d("Track Group: $trackType (${group.length} tracks)")
                
                for (i in 0 until group.length) {
                    val format = group.getTrackFormat(i)
                    val isSelected = group.isTrackSelected(i)
                    val isSupported = group.isTrackSupported(i)
                    
                    val info = buildString {
                        append("  [$i] ")
                        append(if (isSelected) "▶ SELECTED" else "  ")
                        append(if (isSupported) " ✓" else " ✗ UNSUPPORTED")
                        append(" | codec=${format.codecs ?: format.sampleMimeType}")
                        
                        if (group.type == C.TRACK_TYPE_AUDIO) {
                            append(" | channels=${format.channelCount}")
                            append(" | sampleRate=${format.sampleRate}")
                            append(" | bitrate=${format.bitrate}")
                            format.language?.let { append(" | lang=$it") }
                        }
                        
                        if (group.type == C.TRACK_TYPE_VIDEO) {
                            append(" | ${format.width}x${format.height}")
                            append(" | fps=${format.frameRate}")
                        }
                    }
                    Timber.d(info)
                    
                    // Extra warning for unsupported codec
                    if (!isSupported) {
                        Timber.w("⚠️ UNSUPPORTED $trackType CODEC: ${format.codecs ?: format.sampleMimeType}")
                        Timber.w("⚠️ This device may not have decoder for this audio format")
                    }
                }
            }
            Timber.d("========== END TRACK INFO ==========")
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
     * Analytics listener to log actual decoder names being used.
     * This shows which codec implementation (hardware vs software/FFmpeg) is decoding the stream.
     */
    @OptIn(UnstableApi::class)
    private val analyticsListener = @UnstableApi
    object : AnalyticsListener {
        override fun onAudioDecoderInitialized(
            eventTime: AnalyticsListener.EventTime,
            decoderName: String,
            initializedTimestampMs: Long,
            initializationDurationMs: Long
        ) {
            Timber.i("🔊 AUDIO DECODER: $decoderName (init took ${initializationDurationMs}ms)")
        }
        
        override fun onVideoDecoderInitialized(
            eventTime: AnalyticsListener.EventTime,
            decoderName: String,
            initializedTimestampMs: Long,
            initializationDurationMs: Long
        ) {
            Timber.i("🎬 VIDEO DECODER: $decoderName (init took ${initializationDurationMs}ms)")
        }
        
        override fun onAudioInputFormatChanged(
            eventTime: AnalyticsListener.EventTime,
            format: androidx.media3.common.Format,
            decoderReuseEvaluation: DecoderReuseEvaluation?
        ) {
            Timber.i("🔊 AUDIO FORMAT: codec=${format.codecs ?: format.sampleMimeType}, " +
                    "channels=${format.channelCount}, sampleRate=${format.sampleRate}")
        }
        
        override fun onVideoInputFormatChanged(
            eventTime: AnalyticsListener.EventTime,
            format: androidx.media3.common.Format,
            decoderReuseEvaluation: DecoderReuseEvaluation?
        ) {
            Timber.i("🎬 VIDEO FORMAT: codec=${format.codecs ?: format.sampleMimeType}, " +
                    "${format.width}x${format.height}, fps=${format.frameRate}")
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
        
        // Configure audio attributes for TV media playback.
        // This ensures proper audio routing on Android TV devices and
        // automatically handles audio focus (pausing when other apps need audio).
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
            .build()

        val renderersFactory = DefaultRenderersFactory(context)
            .setExtensionRendererMode(
                DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON
            )
        val mediaSourceFactory = DefaultMediaSourceFactory(context)
        
        exoPlayer = ExoPlayer.Builder(context)
            .setAudioAttributes(audioAttributes, /* handleAudioFocus = */ true)
            .setHandleAudioBecomingNoisy(true)
            .setRenderersFactory(renderersFactory)
            .setMediaSourceFactory(mediaSourceFactory)
            .build()
            .apply {
                addListener(playerListener)
                addAnalyticsListener(analyticsListener)
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
