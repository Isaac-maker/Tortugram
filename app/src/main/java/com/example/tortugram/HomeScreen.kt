package com.example.tortugram

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Tab
import androidx.tv.material3.TabRow
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import dev.g000sha256.tdl.dto.Chat
import java.io.File

/**
 * Pantalla "Dentro del Home"
 * isaac-maker 2026
 */

@Composable
fun HomeScreen(
    onChatClick: (Long) -> Unit = {},
    onOpenStorage: () -> Unit = {}
) {
    val chats by TelegramManager.chats.collectAsState()
    val folders by TelegramManager.folders.collectAsState()
    val selectedFolderId by TelegramManager.selectedFolderId.collectAsState()

    // HomeScreen es la pantalla raíz: aquí el botón "volver" del control
    // remoto ya no tiene a dónde más regresar, así que en lugar de dejar
    // que el sistema cierre la app de una, mostramos una confirmación.
    var showExitDialog by remember { mutableStateOf(false) }
    val activity = LocalContext.current as? Activity

    BackHandler {
        showExitDialog = true
    }

    if (showExitDialog) {
        AlertDialog(
            onDismissRequest = { showExitDialog = false },
            containerColor = MaterialTheme.colorScheme.surface,
            title = {
                Text(
                    text = "Salir de Tortugram",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground
                )
            },
            text = {
                Text(
                    text = "¿Seguro que quieres salir de la aplicación?",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
            },
            confirmButton = {
                Button(onClick = { activity?.finish() }) {
                    Text("Sí")
                }
            },
            dismissButton = {
                Button(onClick = { showExitDialog = false }) {
                    Text("No")
                }
            }
        )
    }

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

            Row(verticalAlignment = Alignment.CenterVertically) {

                if (chats.isNotEmpty()) {
                    Text(
                        text = "${chats.size} chats",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Spacer(modifier = Modifier.width(16.dp))
                }

                Button(onClick = onOpenStorage) {
                    Text("🗄 Almacenamiento")
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Barra de pestañas para las carpetas (Chat Folders)
        if (folders.isNotEmpty()) {
            TabRow(
                selectedTabIndex = folders.indexOfFirst { it.id == selectedFolderId }.coerceAtLeast(0),
                modifier = Modifier.fillMaxWidth()
            ) {
                folders.forEach { folder ->
                    Tab(
                        selected = folder.id == selectedFolderId,
                        onFocus = { TelegramManager.selectFolder(folder.id) },
                        onClick = { TelegramManager.selectFolder(folder.id) }
                    ) {
                        Text(
                            text = folder.title,
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                        )
                    }
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

    // Controla si este card tiene el foco (navegación con el D-pad del control).
    var isFocused by remember { mutableStateOf(false) }

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
            .onFocusChanged { isFocused = it.isFocused }
            .clickable { onClick() },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .border(
                    width = if (isFocused) 8.dp else 0.dp,
                    color = MaterialTheme.colorScheme.primary,
                    shape = RoundedCornerShape(16.dp)
                ),
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