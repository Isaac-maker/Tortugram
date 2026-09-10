package com.example.tortugram

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.example.tortugram.ui.theme.TortugramTheme
import dev.g000sha256.tdl.dto.MessageVideo

/**
 * MainActivity"
 * isaac-maker 2026
 */
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // --- Login: SIN CAMBIOS, tal cual funcionaba ---
        TelegramManager.initClient(applicationContext)

        // --- Nuevo: gestor de almacenamiento (no toca login/QR) ---
        StorageManager.init(applicationContext)

        // --- Nuevo: arranca el servidor local que permite el streaming de video ---
        StreamingServer.start()

        setContent {
            TortugramTheme {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                ) {
                    AppNavigation()
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        StreamingServer.stop()
    }
}

@Composable
fun AppNavigation() {
    val isLoggedIn by TelegramManager.isLoggedIn.collectAsState()
    val isPasswordRequired by TelegramManager.isPasswordRequired.collectAsState()
    val qrLink by TelegramManager.qrCodeLink.collectAsState()

    var selectedChatId by remember { mutableStateOf<Long?>(null) }
    var selectedVideo by remember { mutableStateOf<Pair<MessageVideo, String>?>(null) }
    // Lista completa de videos del chat actual + índice del que se está
    // viendo, para poder armar los botones de "anterior"/"siguiente" en
    // VideoPlayerScreen sin que esa pantalla tenga que hablar con TDLib.
    var currentVideoList by remember { mutableStateOf<List<MessageVideo>>(emptyList()) }
    var currentVideoIndex by remember { mutableStateOf(0) }
    var showStorage by remember { mutableStateOf(false) }

    // Mismo criterio de nombre que ya usa ChatScreen: nombre de archivo,
    // si no hay, el caption, y si no hay nada, "Video".
    fun titleFor(video: MessageVideo): String =
        video.video.fileName
            .ifEmpty { video.caption.text }
            .ifEmpty { "Video" }

    when {
        // --- Login: SIN CAMBIOS ---
        !isLoggedIn && isPasswordRequired -> PasswordScreen()
        !isLoggedIn -> LoginScreen(qrLink = qrLink)

        // --- Nuevo: pantalla de almacenamiento ---
        showStorage -> StorageScreen(
            onBack = { showStorage = false }
        )

        // --- Nuevo: Reproductor a pantalla completa (streaming) ---
        selectedVideo != null -> {
            val (videoContent, label) = selectedVideo!!
            VideoPlayerScreen(
                file = videoContent.video.video,
                title = label,
                onBack = { selectedVideo = null },
                onPrevious = if (currentVideoIndex > 0) {
                    {
                        currentVideoIndex -= 1
                        val prev = currentVideoList[currentVideoIndex]
                        selectedVideo = prev to titleFor(prev)
                    }
                } else null,
                onNext = if (currentVideoIndex < currentVideoList.lastIndex) {
                    {
                        currentVideoIndex += 1
                        val next = currentVideoList[currentVideoIndex]
                        selectedVideo = next to titleFor(next)
                    }
                } else null
            )
        }

        // --- Cuadrícula de videos del canal/grupo ---
        selectedChatId != null -> {
            Column {
                Button(
                    onClick = { selectedChatId = null },
                    modifier = Modifier.padding(8.dp)
                ) {
                    Text(stringResource(R.string.btn_back_home))
                }
                ChatScreen(
                    chatId = selectedChatId!!,
                    onVideoClick = { videos, index, title ->
                        currentVideoList = videos
                        currentVideoIndex = index
                        selectedVideo = videos[index] to title
                    },
                    onBack = { selectedChatId = null }
                )
            }
        }

        // --- Cuadrícula de canales/grupos ---
        else -> HomeScreen(
            onChatClick = { id -> selectedChatId = id },
            onOpenStorage = { showStorage = true }
        )
    }
}
