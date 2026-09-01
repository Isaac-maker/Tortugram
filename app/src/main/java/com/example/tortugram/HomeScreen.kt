package com.example.tortugram

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import dev.g000sha256.tdl.dto.Chat
import java.io.File

/**
 * Pantalla "Dentro del Home"
 * isaac-maker 2026
 */

@Composable
fun HomeScreen(
    onChatClick: (Long) -> Unit = {}
) {
    val chats by TelegramManager.chats.collectAsState()
    val folders by TelegramManager.folders.collectAsState()
    val selectedFolderId by TelegramManager.selectedFolderId.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Tortugram, by isaac-maker",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground
            )

            if (chats.isNotEmpty()) {
                Text(
                    text = "${chats.size} chats",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Barra de pestañas para las carpetas (Chat Folders)
        if (folders.isNotEmpty()) {
            ScrollableTabRow(
                selectedTabIndex = folders.indexOfFirst { it.id == selectedFolderId }.coerceAtLeast(0),
                edgePadding = 0.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                folders.forEach { folder ->
                    Tab(
                        selected = folder.id == selectedFolderId,
                        onClick = { TelegramManager.selectFolder(folder.id) },
                        text = {
                            Text(
                                text = folder.title,
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        if (chats.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Cargando carpeta...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(chats, key = { it.id }) { chat ->
                    ChatCard(chat = chat, onClick = { onChatClick(chat.id) })
                }
            }
        }
    }
}

@Composable
private fun ChatCard(chat: Chat, onClick: () -> Unit) {
    val photoFile = chat.photo?.small
    var localPath by remember(photoFile?.id, photoFile?.local?.path) {
        mutableStateOf(photoFile?.local?.path ?: "")
    }

    LaunchedEffect(photoFile?.id) {
        if (localPath.isEmpty() && photoFile != null) {
            TelegramManager.downloadFile(photoFile.id) { path ->
                localPath = path
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            if (localPath.isNotEmpty() && File(localPath).exists()) {
                AsyncImage(
                    model = File(localPath),
                    contentDescription = chat.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Text(
                    text = chat.title.take(1).uppercase(),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = chat.title,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}