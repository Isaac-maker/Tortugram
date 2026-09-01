package com.example.tortugram

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import dev.g000sha256.tdl.dto.MessageVideo
import java.io.File as JavaFile

/**
 * Pantalla "Dentro del canal o grupo"
 * isaac-maker 2026
 */
@Composable
fun ChatScreen(
    chatId: Long,
    onVideoClick: (MessageVideo, String) -> Unit
) {
    val messages by TelegramManager.messages.collectAsState()

    LaunchedEffect(chatId) {
        TelegramManager.loadMessages(chatId)
    }

    val videoMessages = remember(messages) {
        messages.mapNotNull { msg -> (msg.content as? MessageVideo)?.let { msg.id to it } }
    }

    if (videoMessages.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(3), // Cambia a 4 si prefieres más columnas
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items(videoMessages, key = { it.first }) { (_, videoContent) ->
            VideoThumbnailCard(
                videoContent = videoContent,
                onClick = {
                    val label = videoContent.caption.text.ifEmpty { "Video" }
                    onVideoClick(videoContent, label)
                }
            )
        }
    }
}

@Composable
private fun VideoThumbnailCard(videoContent: MessageVideo, onClick: () -> Unit) {
    // La miniatura es un archivo diminuto: se descarga completa (no aplica la
    // restricción de streaming, que es solo para el video en sí).
    val thumbFile = videoContent.video.thumbnail?.file
    var localPath by remember(thumbFile?.id) { mutableStateOf(thumbFile?.local?.path ?: "") }

    LaunchedEffect(thumbFile?.id) {
        if (localPath.isEmpty() && thumbFile != null) {
            TelegramManager.downloadFile(thumbFile.id) { path -> localPath = path }
        }
    }

    Column {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.Black)
                .clickable { onClick() },
            contentAlignment = Alignment.Center
        ) {
            if (localPath.isNotEmpty() && JavaFile(localPath).exists()) {
                AsyncImage(
                    model = JavaFile(localPath),
                    contentDescription = "Miniatura de video",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }

            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = "Reproducir",
                tint = Color.White,
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color.Black.copy(alpha = 0.5f))
                    .padding(4.dp)
            )
        }

        if (videoContent.caption.text.isNotEmpty()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = videoContent.caption.text,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
