package com.example.tortugram

import android.view.KeyEvent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.g000sha256.tdl.dto.File
import kotlinx.coroutines.delay

/**
 * Pantalla "Dentro del video"
 * isaac-maker 2026
 */

@Composable
fun VideoPlayerScreen(
    file: File,
    title: String,
    onBack: () -> Unit
) {
    val state = rememberVideoPlayerState()
    var controlsVisible by remember { mutableStateOf(true) }

    // Foco para interceptar teclas a nivel de pantalla
    val screenFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        screenFocusRequester.requestFocus()
    }

    // Auto-ocultar controles
    LaunchedEffect(controlsVisible, state.isPlaying) {
        if (controlsVisible && state.isPlaying) {
            delay(3000)
            controlsVisible = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(screenFocusRequester)
            .focusable()
            // Captura los botones físicos multimedia del mando de Fire o TV
            .onPreviewKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown) {
                    when (keyEvent.nativeKeyEvent.keyCode) {
                        // Captura el botón físico de Atrás en el mando
                        KeyEvent.KEYCODE_BACK -> {
                            onBack()
                            true
                        }
                        KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
                        KeyEvent.KEYCODE_MEDIA_PLAY,
                        KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                            state.playPause()
                            controlsVisible = true
                            true
                        }
                        KeyEvent.KEYCODE_MEDIA_FAST_FORWARD,
                        KeyEvent.KEYCODE_MEDIA_STEP_FORWARD -> {
                            state.forward()
                            controlsVisible = true
                            true
                        }
                        KeyEvent.KEYCODE_MEDIA_REWIND,
                        KeyEvent.KEYCODE_MEDIA_STEP_BACKWARD -> {
                            state.rewind()
                            controlsVisible = true
                            true
                        }
                        KeyEvent.KEYCODE_DPAD_CENTER,
                        KeyEvent.KEYCODE_ENTER -> {
                            if (!controlsVisible) {
                                controlsVisible = true
                                true
                            } else false
                        }
                        else -> false
                    }
                } else false
            }
    ) {
        /* Capa de video */
        VideoPlayer(
            file = file,
            state = state,
            modifier = Modifier.fillMaxSize()
        )

        /* Capa de Overlays */
        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(modifier = Modifier.fillMaxSize()) {

                /* Barra superior */
                Row(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                            .focusable() // Permite seleccionarlo con la rueda (D-Pad)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Cerrar",
                            tint = Color.White
                        )
                    }

                    if (title.isNotBlank()) {
                        Text(
                            text = title,
                            color = Color.White,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .padding(horizontal = 16.dp)
                                .weight(1f)
                        )
                    }
                }

                /* Barra inferior */
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    Color.Black.copy(alpha = 0.75f)
                                )
                            )
                        )
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Slider(
                        value = if (state.duration > 0) {
                            (state.currentPosition.toFloat() / state.duration.toFloat())
                                .coerceIn(0f, 1f)
                        } else 0f,
                        onValueChange = { fraction ->
                            if (state.duration > 0) {
                                state.seekTo((fraction * state.duration).toLong())
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusable() // Permite navegar la barra de progreso con el D-Pad
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { state.rewind() },
                            modifier = Modifier.focusable()
                        ) {
                            Icon(
                                imageVector = Icons.Default.FastRewind,
                                contentDescription = "Retroceder 10s",
                                tint = Color.White
                            )
                        }
                        IconButton(
                            onClick = { state.playPause() },
                            modifier = Modifier.focusable()
                        ) {
                            Icon(
                                imageVector = if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (state.isPlaying) "Pausar" else "Reproducir",
                                tint = Color.White,
                                modifier = Modifier.size(40.dp)
                            )
                        }
                        IconButton(
                            onClick = { state.forward() },
                            modifier = Modifier.focusable()
                        ) {
                            Icon(
                                imageVector = Icons.Default.FastForward,
                                contentDescription = "Avanzar 10s",
                                tint = Color.White
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = formatTime(state.currentPosition),
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall
                        )
                        Text(
                            text = formatTime(state.duration),
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }
        }

        if (state.isBuffering) {
            CircularProgressIndicator(
                color = Color.White,
                modifier = Modifier.align(Alignment.Center)
            )
        }
    }
}