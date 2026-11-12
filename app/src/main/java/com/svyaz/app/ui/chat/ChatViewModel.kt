package com.svyaz.app.ui.chat

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.svyaz.app.SvyazApp
import com.svyaz.app.appContainer
import com.svyaz.app.data.MediaRepository
import com.svyaz.app.data.Message
import com.svyaz.app.data.pairId
import com.svyaz.app.media.ImageTools
import com.svyaz.app.media.VoiceRecorder
import com.svyaz.app.util.UiMessages
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Идёт ли запись голосового сообщения и сколько она уже длится. */
data class RecordingState(val isRecording: Boolean = false, val elapsedMs: Long = 0)

/** Логика экрана переписки с одним человеком. */
class ChatViewModel(private val myUid: String, private val peerUid: String) : ViewModel() {

    private val chats = appContainer.chats
    private val media = appContainer.media
    private val appContext = SvyazApp.instance
    private val recorder = VoiceRecorder(appContext)

    private val chatId = pairId(myUid, peerUid)

    /** Сколько последних сообщений загружено. Растёт, когда пользователь листает вверх. */
    private val limit = MutableStateFlow(PAGE_SIZE)

    /** Сообщения, новые в начале списка. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val messages: StateFlow<List<Message>> = limit
        .flatMapLatest { chats.observeMessages(chatId, myUid, it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Писать и звонить можно только друзьям. */
    val isFriend: StateFlow<Boolean> = appContainer.friends.observeFriendships(myUid)
        .map { list -> list.any { it.id == chatId && it.isAccepted } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    private val _recording = MutableStateFlow(RecordingState())
    val recording: StateFlow<RecordingState> = _recording.asStateFlow()
    private var recordingTimer: Job? = null

    /** Подгрузить более старые сообщения. */
    fun loadMore() {
        if (messages.value.size >= limit.value) limit.value += PAGE_SIZE
    }

    fun sendText(text: String) {
        val trimmed = text.trim().take(MAX_TEXT_LENGTH)
        if (trimmed.isEmpty()) return
        chats.sendText(chatId, myUid, peerUid, trimmed)
    }

    fun sendPhoto(uri: Uri) {
        viewModelScope.launch {
            // Сжатие фото — тяжёлая работа, выполняем её не в главном потоке
            val sent = withContext(Dispatchers.IO) {
                val photo = ImageTools.prepareChatPhoto(appContext, uri) ?: return@withContext false
                chats.sendImage(chatId, myUid, peerUid, photo.jpeg, photo.width, photo.height)
                true
            }
            if (!sent) UiMessages.show("Не удалось отправить это фото")
        }
    }

    fun startRecording() {
        if (recorder.isRecording) return
        appContainer.voicePlayer.stop()
        if (!recorder.start()) {
            UiMessages.show("Не удалось включить микрофон")
            return
        }
        _recording.value = RecordingState(isRecording = true)
        recordingTimer = viewModelScope.launch {
            while (isActive) {
                delay(200)
                val elapsed = recorder.elapsedMs
                _recording.value = RecordingState(isRecording = true, elapsedMs = elapsed)
                if (elapsed >= VoiceRecorder.MAX_DURATION_MS) {
                    finishRecording(send = true) // достигли предела длины — отправляем
                }
            }
        }
    }

    /** Закончить запись: [send] = true — отправить, false — выбросить. */
    fun finishRecording(send: Boolean) {
        if (!recorder.isRecording) return
        recordingTimer?.cancel()
        val recording = recorder.stop()
        _recording.value = RecordingState()

        if (!send) {
            recording?.file?.delete()
            return
        }
        if (recording == null) {
            UiMessages.show("Запись слишком короткая")
            return
        }
        viewModelScope.launch {
            val sent = withContext(Dispatchers.IO) {
                val bytes = recording.file.readBytes()
                recording.file.delete()
                if (bytes.size > MediaRepository.MAX_BYTES) return@withContext false
                chats.sendVoice(chatId, myUid, peerUid, bytes, recording.durationMs)
                true
            }
            if (!sent) UiMessages.show("Запись получилась слишком большой")
        }
    }

    /** Отметить сообщения собеседника прочитанными. */
    fun markRead(loaded: List<Message>) {
        chats.markRead(chatId, myUid, loaded)
    }

    /** Файл вложения (фото или голосового) или null, если загрузить не удалось. */
    suspend fun mediaFile(mediaId: String): File? =
        runCatching { media.getFile(chatId, mediaId) }.getOrNull()

    override fun onCleared() {
        recorder.cancel()
    }

    private companion object {
        const val PAGE_SIZE = 50L
        const val MAX_TEXT_LENGTH = 4000
    }
}
