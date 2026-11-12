package com.svyaz.app.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.svyaz.app.appContainer
import com.svyaz.app.data.Message
import com.svyaz.app.data.MessageStatus
import com.svyaz.app.data.MessageType
import com.svyaz.app.ui.theme.ReadTickDark
import com.svyaz.app.ui.theme.ReadTickLight
import com.svyaz.app.util.UiMessages
import com.svyaz.app.util.formatDuration
import com.svyaz.app.util.formatTime
import kotlinx.coroutines.launch
import java.io.File

/**
 * «Облачко» одного сообщения: мои — справа, собеседника — слева.
 * Внутри — текст, фото или голосовое, а под ними время и статус.
 */
@Composable
fun MessageBubble(
    message: Message,
    isMine: Boolean,
    loadMedia: suspend (String) -> File?,
    onOpenImage: (File) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = if (isMine) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Surface(
            color = if (isMine) colors.primaryContainer else colors.surfaceContainerHigh,
            contentColor = if (isMine) colors.onPrimaryContainer else colors.onSurface,
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (isMine) 16.dp else 4.dp,
                bottomEnd = if (isMine) 4.dp else 16.dp,
            ),
            modifier = Modifier.widthIn(max = 300.dp),
        ) {
            Column(Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                when (message.type) {
                    MessageType.TEXT -> Text(message.text, style = MaterialTheme.typography.bodyLarge)
                    MessageType.IMAGE -> ImageContent(message, loadMedia, onOpenImage)
                    MessageType.VOICE -> VoiceContent(message, loadMedia)
                }
                Row(
                    modifier = Modifier
                        .align(Alignment.End)
                        .padding(top = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = formatTime(message.createdAt),
                        style = MaterialTheme.typography.labelSmall,
                        color = LocalContentColor.current.copy(alpha = 0.7f),
                    )
                    if (isMine) {
                        Spacer(Modifier.width(4.dp))
                        StatusIcon(message.status)
                    }
                }
            }
        }
    }
}

/**
 * Значок статуса моего сообщения:
 * часы — отправляется, одна галочка — отправлено,
 * две серые — доставлено, две синие — прочитано.
 */
@Composable
private fun StatusIcon(status: MessageStatus) {
    val (icon, description) = when (status) {
        MessageStatus.SENDING -> Icons.Outlined.Schedule to "Отправляется"
        MessageStatus.SENT -> Icons.Filled.Done to "Отправлено"
        MessageStatus.DELIVERED -> Icons.Filled.DoneAll to "Доставлено"
        MessageStatus.READ -> Icons.Filled.DoneAll to "Прочитано"
    }
    val tint = when {
        status != MessageStatus.READ -> LocalContentColor.current.copy(alpha = 0.55f)
        isSystemInDarkTheme() -> ReadTickDark
        else -> ReadTickLight
    }
    Icon(icon, contentDescription = description, tint = tint, modifier = Modifier.size(16.dp))
}

/** Состояние загрузки вложения. */
private sealed interface MediaLoad {
    data object Loading : MediaLoad
    data class Ready(val file: File) : MediaLoad
    data object Failed : MediaLoad
}

@Composable
private fun ImageContent(
    message: Message,
    loadMedia: suspend (String) -> File?,
    onOpenImage: (File) -> Unit,
) {
    val mediaId = message.mediaId ?: return
    val load by produceState<MediaLoad>(initialValue = MediaLoad.Loading, mediaId) {
        value = loadMedia(mediaId)?.let { MediaLoad.Ready(it) } ?: MediaLoad.Failed
    }
    // Место под фото отводим сразу по его пропорциям, чтобы список не «прыгал» при загрузке
    val ratio = if (message.width > 0 && message.height > 0) {
        (message.width.toFloat() / message.height).coerceIn(0.5f, 2f)
    } else {
        1f
    }
    Box(
        modifier = Modifier
            .width(240.dp)
            .aspectRatio(ratio)
            .clip(RoundedCornerShape(12.dp))
            .background(Color.Black.copy(alpha = 0.08f)),
        contentAlignment = Alignment.Center,
    ) {
        when (val current = load) {
            MediaLoad.Loading -> CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 2.dp)

            MediaLoad.Failed -> Icon(Icons.Filled.BrokenImage, contentDescription = "Фото не загрузилось")

            is MediaLoad.Ready -> AsyncImage(
                model = current.file,
                contentDescription = "Фото",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .clickable { onOpenImage(current.file) },
            )
        }
    }
}

@Composable
private fun VoiceContent(message: Message, loadMedia: suspend (String) -> File?) {
    val mediaId = message.mediaId ?: return
    val player = appContainer.voicePlayer
    val playerState by player.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var isLoading by remember { mutableStateOf(false) }

    // Проигрыватель общий — проверяем, что он занят именно этим сообщением
    val isCurrent = playerState.mediaId == mediaId
    val progress = if (isCurrent && playerState.durationMs > 0) {
        playerState.positionMs.toFloat() / playerState.durationMs
    } else {
        0f
    }

    Row(
        modifier = Modifier.width(220.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FilledIconButton(
            onClick = {
                if (isLoading) return@FilledIconButton
                scope.launch {
                    isLoading = true
                    val file = loadMedia(mediaId)
                    isLoading = false
                    if (file != null) {
                        player.toggle(mediaId, file)
                    } else {
                        UiMessages.show("Не удалось загрузить запись")
                    }
                }
            },
        ) {
            when {
                isLoading -> CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = LocalContentColor.current,
                )

                isCurrent && playerState.isPlaying ->
                    Icon(Icons.Filled.Pause, contentDescription = "Пауза")

                else -> Icon(Icons.Filled.PlayArrow, contentDescription = "Слушать")
            }
        }
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            LinearProgressIndicator(
                progress = { progress.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = formatDuration(
                    if (isCurrent) playerState.positionMs.toLong() else message.durationMs,
                ),
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}
