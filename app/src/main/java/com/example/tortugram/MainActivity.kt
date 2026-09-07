package com.example.tortugram

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
class MainActivity : ComponentActivity() {
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
    var showStorage by remember { mutableStateOf(false) }

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
                onBack = { selectedVideo = null }
            )
        }

        // --- Cuadrícula de videos del canal/grupo ---
        selectedChatId != null -> {
            Column {
                Button(
                    onClick = { selectedChatId = null },
                    modifier = Modifier.padding(8.dp)
                ) {
                    Text("← Volver a Home")
                }
                ChatScreen(
                    chatId = selectedChatId!!,
                    onVideoClick = { videoContent, label ->
                        selectedVideo = videoContent to label
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
