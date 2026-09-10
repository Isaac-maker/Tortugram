package com.example.tortugram

import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import dev.g000sha256.tdl.dto.File
import kotlinx.coroutines.delay

/**
 * Video Player"
 * isaac-maker 2026
 */

/* ─────────────── Estado compartido del reproductor ─────────────── */

@Stable
class VideoPlayerState {
    var isPlaying by mutableStateOf(false)
        internal set
    var currentPosition by mutableStateOf(0L)
        internal set
    var duration by mutableStateOf(0L)
        internal set
    var isBuffering by mutableStateOf(true)
        internal set

    internal var exoPlayer: ExoPlayer? = null

    fun playPause() {
        exoPlayer?.let { it.playWhenReady = !it.playWhenReady }
    }

    fun seekTo(position: Long) {
        exoPlayer?.seekTo(position)
        currentPosition = position
    }

    fun rewind() {
        exoPlayer?.let {
            val target = (it.currentPosition - 10_000).coerceAtLeast(0)
            it.seekTo(target)
        }
    }

    fun forward() {
        exoPlayer?.let {
            val dur = it.duration.coerceAtLeast(0L)
            val target = (it.currentPosition + 10_000).coerceAtMost(dur)
            it.seekTo(target)
        }
    }
}

@Composable
fun rememberVideoPlayerState(): VideoPlayerState = remember { VideoPlayerState() }

/* ─────────────── Reproductor puro (solo video) ─────────────── */

@OptIn(UnstableApi::class)
@Composable
fun VideoPlayer(
    file: File,
    state: VideoPlayerState,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val streamUrl = remember(file.id) {
        StreamingServer.urlFor(file.id, file.size)
    }

    val exoPlayer = remember(file.id) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(
                MediaItem.Builder()
                    .setUri(streamUrl)
                    .setMimeType(MimeTypes.VIDEO_MP4)
                    .build()
            )
            prepare()
            playWhenReady = true
        }
    }

    DisposableEffect(exoPlayer) {
        state.exoPlayer = exoPlayer
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                state.isBuffering = playbackState == Player.STATE_BUFFERING
                if (playbackState == Player.STATE_READY) {
                    state.duration = exoPlayer.duration.coerceAtLeast(0L)
                }
            }

            override fun onIsPlayingChanged(playing: Boolean) {
                state.isPlaying = playing
            }

            override fun onPlayerError(error: PlaybackException) {
                android.util.Log.e("VideoPlayer", "Error de reproducción", error)
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.release()
            state.exoPlayer = null
        }
    }

    LaunchedEffect(exoPlayer) {
        while (true) {
            state.currentPosition = exoPlayer.currentPosition.coerceAtLeast(0L)
            delay(500)
        }
    }

    Box(
        modifier = modifier.background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        AndroidView(
            factory = { ctx ->
                val playerView = android.view.LayoutInflater.from(ctx)
                    .inflate(com.example.tortugram.R.layout.player_view, null) as PlayerView
                playerView.apply {
                    player = exoPlayer
                    useController = false
                }
            },
            modifier = Modifier.fillMaxSize()
        )
    }
}

/* ─────────────── Helper ─────────────── */

internal fun formatTime(millis: Long): String {
    val totalSeconds = millis / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}