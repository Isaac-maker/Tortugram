package com.example.tortugram

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import dev.g000sha256.tdl.dto.MessageVideo
import java.io.File


/**
 * Pantalla "Dentro del Home"
 * isaac-maker 2026
 */

@Composable
fun ChatScreen(
    chatId: Long,
    onVideoClick: (MessageVideo, String) -> Unit
) {

    val messages by
    TelegramManager.messages.collectAsState()

    val gridState =
        rememberLazyGridState()

    LaunchedEffect(chatId) {

        TelegramManager.loadMessages(chatId)
    }

    val videoMessages =
        remember(messages) {

            messages.mapNotNull { message ->

                val video =
                    message.content as? MessageVideo

                if (video != null) {
                    message.id to video
                } else {
                    null
                }
            }
        }

    LaunchedEffect(
        gridState,
        videoMessages.size,
        chatId
    ) {

        snapshotFlow {

            val layoutInfo =
                gridState.layoutInfo

            val totalItems =
                layoutInfo.totalItemsCount

            val lastVisible =
                layoutInfo.visibleItemsInfo
                    .lastOrNull()
                    ?.index
                    ?: 0

            totalItems > 0 &&
                    lastVisible >= totalItems - 6

        }.collect { nearEnd ->

            if (nearEnd) {

                TelegramManager.loadMoreMessages(
                    chatId
                )
            }
        }
    }

    if (videoMessages.isEmpty()) {

        Box(
            modifier =
                Modifier.fillMaxSize(),
            contentAlignment =
                Alignment.Center
        ) {

            CircularProgressIndicator()
        }

        return
    }

    LazyVerticalGrid(

        columns =
            GridCells.Fixed(5),

        state =
            gridState,

        contentPadding =
            PaddingValues(16.dp),

        horizontalArrangement =
            Arrangement.spacedBy(12.dp),

        verticalArrangement =
            Arrangement.spacedBy(16.dp),

        modifier =
            Modifier.fillMaxSize()
    ) {

        items(

            items =
                videoMessages,

            key = {
                it.first
            }

        ) { item ->

            VideoThumbnailCard(

                videoContent =
                    item.second,

                onClick = {

                    val title =
                        item.second
                            .caption
                            .text
                            .ifEmpty {
                                "Video"
                            }

                    onVideoClick(
                        item.second,
                        title
                    )
                }
            )
        }
    }
}




@Composable
private fun VideoThumbnailCard(
    videoContent: MessageVideo,
    onClick: () -> Unit
) {

    val thumbnail =
        videoContent.video.thumbnail?.file

    var localPath by remember(
        thumbnail?.id
    ) {

        mutableStateOf(
            thumbnail?.local?.path ?: ""
        )
    }

    // Controla si este card tiene el foco (navegación con el D-pad del control).
    var isFocused by remember { mutableStateOf(false) }

    LaunchedEffect(
        thumbnail?.id
    ) {

        if (
            localPath.isEmpty() &&
            thumbnail != null
        ) {

            TelegramManager.downloadFile(
                thumbnail.id
            ) { path ->

                localPath = path
            }
        }
    }

    Column {

        Box(

            modifier =
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(
                        RoundedCornerShape(12.dp)
                    )
                    .background(Color.Black)
                    .onFocusChanged { isFocused = it.isFocused }
                    .clickable {
                        onClick()
                    }
                    .border(
                        width = if (isFocused) 6.dp else 0.dp,
                        color = Color(0xFFCCED12),
                        shape = RoundedCornerShape(12.dp)
                    ),

            contentAlignment =
                Alignment.Center
        ) {

            if (
                localPath.isNotEmpty() &&
                File(localPath).exists()
            ) {

                AsyncImage(

                    model =
                        File(localPath),

                    contentDescription =
                        "Miniatura del video",

                    modifier =
                        Modifier.fillMaxSize(),

                    contentScale =
                        ContentScale.Crop
                )
            }

            Icon(

                imageVector =
                    Icons.Default.PlayArrow,

                contentDescription =
                    "Reproducir",

                tint =
                    Color.White,

                modifier =
                    Modifier
                        .size(36.dp)
                        .clip(
                            RoundedCornerShape(50)
                        )
                        .background(
                            Color.Black.copy(
                                alpha = 0.5f
                            )
                        )
                        .padding(4.dp)
            )
        }

        if (
            videoContent.caption.text.isNotEmpty()
        ) {

            Spacer(
                modifier =
                    Modifier.height(4.dp)
            )

            Text(

                text =
                    videoContent.caption.text,

                style =
                    MaterialTheme
                        .typography
                        .bodySmall,

                maxLines = 1,

                overflow =
                    TextOverflow.Ellipsis
            )
        }
    }


}
