package com.svyaz.app.ui.chat

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.outlined.EmojiEmotions
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.emoji2.emojipicker.EmojiPickerView
import com.svyaz.app.ui.theme.CallRed
import com.svyaz.app.util.formatDuration

/**
 * Нижняя панель чата: поле ввода с кнопками эмодзи, фото, микрофона и отправки.
 * Во время записи голосового вместо поля показывается панель записи.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MessageInput(
    recording: RecordingState,
    onSendText: (String) -> Unit,
    onPickPhoto: () -> Unit,
    onStartRecording: () -> Unit,
    onFinishRecording: (send: Boolean) -> Unit,
) {
    // TextFieldValue хранит не только текст, но и положение курсора — оно нужно для вставки эмодзи
    var field by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(""))
    }
    var emojiOpen by rememberSaveable { mutableStateOf(false) }
    val keyboard = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }

    // Открылась клавиатура — панель эмодзи убираем, чтобы они не показывались вместе
    val keyboardVisible = WindowInsets.isImeVisible
    LaunchedEffect(keyboardVisible) {
        if (keyboardVisible) emojiOpen = false
    }
    BackHandler(enabled = emojiOpen) { emojiOpen = false }

    val insertEmoji: (String) -> Unit = { emoji ->
        val start = field.selection.min
        val end = field.selection.max
        field = TextFieldValue(
            text = field.text.replaceRange(start, end, emoji),
            selection = TextRange(start + emoji.length),
        )
    }

    Surface(color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
        Column(
            // Отступ под клавиатуру и системную панель навигации
            modifier = Modifier.windowInsetsPadding(
                WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal),
            ),
        ) {
            if (recording.isRecording) {
                RecordingBar(
                    elapsedMs = recording.elapsedMs,
                    onCancel = { onFinishRecording(false) },
                    onSend = { onFinishRecording(true) },
                )
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    IconButton(
                        onClick = {
                            if (emojiOpen) {
                                emojiOpen = false
                                focusRequester.requestFocus()
                                keyboard?.show()
                            } else {
                                keyboard?.hide()
                                emojiOpen = true
                            }
                        },
                    ) {
                        if (emojiOpen) {
                            Icon(Icons.Filled.Keyboard, contentDescription = "Клавиатура")
                        } else {
                            Icon(Icons.Outlined.EmojiEmotions, contentDescription = "Эмодзи")
                        }
                    }

                    TextField(
                        value = field,
                        onValueChange = { field = it },
                        placeholder = { Text("Сообщение") },
                        maxLines = 5,
                        shape = RoundedCornerShape(24.dp),
                        colors = TextFieldDefaults.colors(
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            disabledIndicatorColor = Color.Transparent,
                        ),
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Sentences,
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(focusRequester),
                    )

                    if (field.text.isBlank()) {
                        IconButton(onClick = onPickPhoto) {
                            Icon(Icons.Filled.Image, contentDescription = "Отправить фото")
                        }
                        IconButton(onClick = onStartRecording) {
                            Icon(Icons.Filled.Mic, contentDescription = "Записать голосовое сообщение")
                        }
                    } else {
                        Spacer(Modifier.width(4.dp))
                        FilledIconButton(
                            onClick = {
                                onSendText(field.text)
                                field = TextFieldValue("")
                            },
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Отправить")
                        }
                    }
                }

                if (emojiOpen) {
                    EmojiPanel(onEmoji = insertEmoji)
                }
            }
        }
    }
}

/** Панель во время записи голосового: таймер, «удалить» и «отправить». */
@Composable
private fun RecordingBar(elapsedMs: Long, onCancel: () -> Unit, onSend: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onCancel) {
            Icon(Icons.Filled.Delete, contentDescription = "Удалить запись")
        }
        Box(
            modifier = Modifier
                .size(10.dp)
                .background(CallRed, CircleShape),
        )
        Spacer(Modifier.width(8.dp))
        Text(formatDuration(elapsedMs), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.width(12.dp))
        Text(
            text = "Идёт запись…",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        FilledIconButton(onClick = onSend) {
            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Отправить запись")
        }
    }
}

/** Готовая панель выбора эмодзи из библиотеки Android. */
@Composable
private fun EmojiPanel(onEmoji: (String) -> Unit) {
    val currentOnEmoji by rememberUpdatedState(onEmoji)
    AndroidView(
        factory = { context ->
            EmojiPickerView(context).apply {
                setOnEmojiPickedListener { item -> currentOnEmoji(item.emoji) }
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .height(300.dp),
    )
}
