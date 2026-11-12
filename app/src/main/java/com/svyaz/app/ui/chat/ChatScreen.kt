package com.svyaz.app.ui.chat

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Call
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.svyaz.app.appContainer
import com.svyaz.app.data.Message
import com.svyaz.app.ui.common.Avatar
import com.svyaz.app.ui.common.rememberUser
import com.svyaz.app.util.UiMessages
import com.svyaz.app.util.formatDayHeader
import com.svyaz.app.util.isSameDay
import java.io.File
import java.util.Date

/** Экран переписки с одним человеком. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(myUid: String, peerUid: String, onBack: () -> Unit) {
    val viewModel: ChatViewModel = viewModel(key = "chat-$myUid-$peerUid") {
        ChatViewModel(myUid, peerUid)
    }
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val isFriend by viewModel.isFriend.collectAsStateWithLifecycle()
    val recording by viewModel.recording.collectAsStateWithLifecycle()
    val peer = rememberUser(peerUid)
    val listState = rememberLazyListState()

    /** Фото, открытое на весь экран. */
    var openedImage by remember { mutableStateOf<File?>(null) }

    // Пока чат открыт и виден — отмечаем сообщения собеседника прочитанными
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(messages, lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            viewModel.markRead(messages)
        }
    }

    // Пришло или отправлено новое сообщение — прокручиваем к нему,
    // если пользователь не ушёл далеко вверх читать старое
    val newest = messages.firstOrNull()
    LaunchedEffect(newest?.id) {
        if (newest != null && (newest.senderId == myUid || listState.firstVisibleItemIndex <= 2)) {
            listState.animateScrollToItem(0)
        }
    }

    // Долистали до самых старых загруженных сообщений — подгружаем ещё
    val reachedOldest by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            info.totalItemsCount > 0 && lastVisible >= info.totalItemsCount - 3
        }
    }
    LaunchedEffect(reachedOldest, messages.size) {
        if (reachedOldest) viewModel.loadMore()
    }

    // Уходим из чата — останавливаем голосовое сообщение
    DisposableEffect(Unit) {
        onDispose { appContainer.voicePlayer.stop() }
    }

    val photoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) viewModel.sendPhoto(uri)
    }
    val microphonePermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            viewModel.startRecording()
        } else {
            UiMessages.show("Без доступа к микрофону записать голосовое не получится")
        }
    }
    val callPermissions = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        if (result[Manifest.permission.RECORD_AUDIO] == true) {
            appContainer.callManager.startCall(peerUid)
        } else {
            UiMessages.show("Для звонка нужен доступ к микрофону")
        }
    }
    val startCall = {
        // Если разрешения уже выданы, окно с вопросом не появится и звонок начнётся сразу
        val permissions = mutableListOf(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions += Manifest.permission.POST_NOTIFICATIONS // уведомление «Идёт звонок»
        }
        callPermissions.launch(permissions.toTypedArray())
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Avatar(peer, size = 38.dp)
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(
                                text = peer?.name ?: "…",
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = peer?.nickname?.let { "@$it" } ?: "",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = startCall, enabled = isFriend) {
                        Icon(Icons.Filled.Call, contentDescription = "Позвонить")
                    }
                },
            )
        },
        bottomBar = {
            if (isFriend) {
                MessageInput(
                    recording = recording,
                    onSendText = viewModel::sendText,
                    onPickPhoto = {
                        photoPicker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                        )
                    },
                    onStartRecording = { microphonePermission.launch(Manifest.permission.RECORD_AUDIO) },
                    onFinishRecording = viewModel::finishRecording,
                )
            } else {
                NotFriendsBar()
            }
        },
    ) { padding ->
        MessageList(
            messages = messages,
            myUid = myUid,
            listState = listState,
            loadMedia = viewModel::mediaFile,
            onOpenImage = { openedImage = it },
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        )
    }

    openedImage?.let { file ->
        FullScreenImage(file = file, onClose = { openedImage = null })
    }
}

@Composable
private fun MessageList(
    messages: List<Message>,
    myUid: String,
    listState: LazyListState,
    loadMedia: suspend (String) -> File?,
    onOpenImage: (File) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (messages.isEmpty()) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Text(
                text = "Сообщений пока нет.\nНапишите первым 👋",
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }
    LazyColumn(
        modifier = modifier,
        state = listState,
        // Список «перевёрнут»: новые сообщения внизу, и экран открывается на них
        reverseLayout = true,
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        itemsIndexed(messages, key = { _, message -> message.id }) { index, message ->
            // Следующее в списке — более старое сообщение
            val older = messages.getOrNull(index + 1)
            val startsNewDay = older == null || !isSameDay(older.createdAt, message.createdAt)
            Column(Modifier.fillMaxWidth()) {
                if (startsNewDay) DayHeader(message.createdAt)
                MessageBubble(
                    message = message,
                    isMine = message.senderId == myUid,
                    loadMedia = loadMedia,
                    onOpenImage = onOpenImage,
                )
            }
        }
    }
}

/** Плашка с датой между сообщениями разных дней. */
@Composable
private fun DayHeader(date: Date) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = RoundedCornerShape(12.dp),
        ) {
            Text(
                text = formatDayHeader(date),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            )
        }
    }
}

/** Показывается вместо поля ввода, если вы с этим человеком не друзья. */
@Composable
private fun NotFriendsBar() {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Вы не в друзьях с этим человеком — писать и звонить нельзя",
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .navigationBarsPadding()
                .padding(16.dp),
        )
    }
}

/** Фото на весь экран. Закрывается нажатием. */
@Composable
private fun FullScreenImage(file: File, onClose: () -> Unit) {
    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .clickable(onClick = onClose),
            contentAlignment = Alignment.Center,
        ) {
            AsyncImage(
                model = file,
                contentDescription = "Фото",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
