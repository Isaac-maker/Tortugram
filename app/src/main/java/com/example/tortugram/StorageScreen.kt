package com.example.tortugram

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text

/**
 * Pantalla de almacenamiento.
 *
 * Muestra cuánto espacio usan los archivos descargados (fotos, videos,
 * miniaturas) frente a la base de datos de TDLib, deja elegir un límite
 * automático y ofrece un botón para limpiar ya. Todo pasa por
 * StorageManager -> TelegramManager.optimizeStorage(), así que nunca se
 * toca el login/QR ni la sesión.
 *
 * isaac-maker 2026
 */
@Composable
fun StorageScreen(
    onBack: () -> Unit = {}
) {

    val filesSize by StorageManager.filesSize.collectAsState()
    val databaseSize by StorageManager.databaseSize.collectAsState()
    val fileCount by StorageManager.fileCount.collectAsState()
    val limitBytes by StorageManager.limitBytes.collectAsState()
    val autoCleanEnabled by StorageManager.autoCleanEnabled.collectAsState()
    val isBusy by StorageManager.isBusy.collectAsState()
    val notice by StorageManager.notice.collectAsState()

    var lastFreedMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        StorageManager.refreshStats()
    }

    val totalSize = filesSize + databaseSize

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp)
    ) {

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {

            Text(
                text = "Almacenamiento",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground
            )

            Button(onClick = onBack) {
                Text("← Volver")
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // --- Resumen de espacio ---
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(20.dp)
        ) {

            Text(
                text = "Espacio utilizado",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Text(
                text = StorageManager.formatBytes(totalSize),
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(12.dp))

            StorageRow(
                label = "Archivos de Telegram ($fileCount)",
                value = StorageManager.formatBytes(filesSize)
            )

            StorageRow(
                label = "Base de datos (sesión, mensajes)",
                value = StorageManager.formatBytes(databaseSize)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // --- Límite / limpieza automática ---
        Text(
            text = "Límite de almacenamiento",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(8.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {

            StorageManager.LIMIT_OPTIONS_BYTES.forEach { option ->

                LimitOptionChip(
                    label = StorageManager.formatBytes(option),
                    selected = option == limitBytes,
                    onClick = { StorageManager.setLimitBytes(option) }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {

            Text(
                text = if (autoCleanEnabled) {
                    "Limpieza automática: activada"
                } else {
                    "Limpieza automática: desactivada (solo avisa)"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground
            )

            Button(
                onClick = {
                    StorageManager.setAutoCleanEnabled(!autoCleanEnabled)
                }
            ) {
                Text(if (autoCleanEnabled) "Desactivar" else "Activar")
            }
        }

        Text(
            text = "La sesión nunca se cierra al limpiar: solo se " +
                    "borran archivos que Telegram puede volver a " +
                    "descargar cuando los necesites.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(24.dp))

        // --- Botón de limpieza manual ---
        Button(
            onClick = {
                StorageManager.cleanNow { freed ->
                    lastFreedMessage =
                        "Se liberaron ${StorageManager.formatBytes(freed)}"
                }
            }
        ) {
            Text(if (isBusy) "Limpiando..." else "🗑 Limpiar archivos")
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Aviso más reciente: primero el de la limpieza automática
        // (StorageManager.notice), y si no hay, el de la limpieza manual.
        val message = notice ?: lastFreedMessage

        if (message != null) {

            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun StorageRow(label: String, value: String) {

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {

        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun LimitOptionChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {

    var isFocused by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(
                if (selected) {
                    Color(0xFFCCED12).copy(alpha = 0.25f)
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                }
            )
            .onFocusChanged { isFocused = it.isFocused }
            .clickable { onClick() }
            .border(
                width = if (isFocused) 4.dp else 0.dp,
                color = Color(0xFFCCED12),
                shape = RoundedCornerShape(10.dp)
            )
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {

        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
