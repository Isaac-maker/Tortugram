package com.example.tortugram

import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.g000sha256.tdl.dto.File
import kotlinx.coroutines.delay

@Composable
fun VideoPlayerScreen(
    file: File,
    title: String,
    onBack: () -> Unit,
    onPrevious: (() -> Unit)? = null,
    onNext: (() -> Unit)? = null
) {
    val state = rememberVideoPlayerState()
    var controlsVisible by remember { mutableStateOf(true) }
    var userActivityTrigger by remember { mutableStateOf(0L) }

    val playButtonFocusRequester = remember { FocusRequester() }
    val blueThemeColor = Color(0xFF2196F3)

    // 1. Manejo del botón Volver de Android/Fire TV
    BackHandler {
        if (controlsVisible) {
            controlsVisible = false
        } else {
            onBack()
        }
    }

    // Solicitamos foco al botón central cada vez que se muestran los controles
    LaunchedEffect(controlsVisible) {
        if (controlsVisible) {
            playButtonFocusRequester.requestFocus()
        }
    }

    // Auto-ocultar controles tras 3.5 segundos de inactividad
    LaunchedEffect(controlsVisible, state.isPlaying, userActivityTrigger) {
        if (controlsVisible && state.isPlaying) {
            delay(3500)
            controlsVisible = false
        }
    }

    fun showControls() {
        controlsVisible = true
        userActivityTrigger = System.currentTimeMillis()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .onKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown) {
                    when (keyEvent.nativeKeyEvent.keyCode) {
                        KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
                        KeyEvent.KEYCODE_MEDIA_PLAY,
                        KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                            state.playPause()
                            showControls()
                            true
                        }
                        KeyEvent.KEYCODE_MEDIA_FAST_FORWARD,
                        KeyEvent.KEYCODE_MEDIA_STEP_FORWARD -> {
                            state.forward()
                            showControls()
                            true
                        }
                        KeyEvent.KEYCODE_MEDIA_REWIND,
                        KeyEvent.KEYCODE_MEDIA_STEP_BACKWARD -> {
                            state.rewind()
                            showControls()
                            true
                        }
                        KeyEvent.KEYCODE_DPAD_CENTER,
                        KeyEvent.KEYCODE_ENTER,
                        KeyEvent.KEYCODE_DPAD_UP,
                        KeyEvent.KEYCODE_DPAD_DOWN,
                        KeyEvent.KEYCODE_DPAD_LEFT,
                        KeyEvent.KEYCODE_DPAD_RIGHT -> {
                            if (!controlsVisible) {
                                showControls()
                                true
                            } else {
                                showControls()
                                false
                            }
                        }
                        else -> false
                    }
                } else false
            }
    ) {
        /* Capa de video */
        key(file.id) {
            VideoPlayer(
                file = file,
                state = state,
                modifier = Modifier.fillMaxSize()
            )
        }

        // 2. Capa clickeable transparente cuando los controles están ocultos
        if (!controlsVisible) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        showControls()
                    }
            )
        }

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
                    TvIconButton(
                        onClick = onBack,
                        icon = Icons.Default.Close,
                        contentDescription = "Cerrar",
                        activeColor = blueThemeColor
                    )

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
                            showControls()
                        },
                        colors = SliderDefaults.colors(
                            thumbColor = blueThemeColor,
                            activeTrackColor = blueThemeColor,
                            inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Anterior
                        TvIconButton(
                            onClick = {
                                showControls()
                                onPrevious?.invoke()
                            },
                            icon = Icons.Default.SkipPrevious,
                            contentDescription = "Video anterior",
                            enabled = onPrevious != null,
                            activeColor = blueThemeColor
                        )

                        // Retroceder 10s
                        TvIconButton(
                            onClick = {
                                showControls()
                                state.rewind()
                            },
                            icon = Icons.Default.FastRewind,
                            contentDescription = "Retroceder 10s",
                            activeColor = blueThemeColor
                        )

                        // Play/Pausa
                        TvIconButton(
                            onClick = {
                                showControls()
                                state.playPause()
                            },
                            icon = if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (state.isPlaying) "Pausar" else "Reproducir",
                            modifier = Modifier.focusRequester(playButtonFocusRequester),
                            activeColor = blueThemeColor,
                            iconSize = 36.dp
                        )

                        // Replay
                        TvIconButton(
                            onClick = {
                                showControls()
                                state.seekTo(0)
                            },
                            icon = Icons.Default.Replay,
                            contentDescription = "Reiniciar video",
                            activeColor = blueThemeColor
                        )

                        // Avanzar 10s
                        TvIconButton(
                            onClick = {
                                showControls()
                                state.forward()
                            },
                            icon = Icons.Default.FastForward,
                            contentDescription = "Avanzar 10s",
                            activeColor = blueThemeColor
                        )

                        // Siguiente
                        TvIconButton(
                            onClick = {
                                showControls()
                                onNext?.invoke()
                            },
                            icon = Icons.Default.SkipNext,
                            contentDescription = "Siguiente video",
                            enabled = onNext != null,
                            activeColor = blueThemeColor
                        )
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
                color = blueThemeColor,
                modifier = Modifier.align(Alignment.Center)
            )
        }
    }
}

@Composable
fun TvIconButton(
    onClick: () -> Unit,
    icon: ImageVector,
    contentDescription: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    activeColor: Color = Color(0xFF2196F3),
    iconSize: Dp = 24.dp
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    Box(
        modifier = modifier
            .padding(4.dp)
            .size(48.dp)
            .background(
                color = if (isFocused) activeColor.copy(alpha = 0.2f) else Color.Transparent,
                shape = CircleShape
            )
            .border(
                width = if (isFocused) 2.dp else 0.dp,
                color = if (isFocused) activeColor else Color.Transparent,
                shape = CircleShape
            )
            .clickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (!enabled) Color.Gray else if (isFocused) activeColor else Color.White,
            modifier = Modifier.size(iconSize)
        )
    }
}