package com.svyaz.app.media

import android.media.MediaPlayer
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

/**
 * Проигрыватель голосовых сообщений. Он один на всё приложение,
 * поэтому одновременно звучит только одно сообщение.
 */
class VoicePlayer(private val scope: CoroutineScope) {

    data class State(
        /** Какое вложение сейчас выбрано (играет или на паузе). */
        val mediaId: String? = null,
        val isPlaying: Boolean = false,
        val positionMs: Int = 0,
        val durationMs: Int = 0,
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private var player: MediaPlayer? = null
    private var progressJob: Job? = null

    /** Нажатие на кнопку у сообщения: запустить, поставить на паузу или продолжить. */
    fun toggle(mediaId: String, file: File) {
        val current = player
        if (current != null && _state.value.mediaId == mediaId) {
            if (current.isPlaying) {
                current.pause()
                _state.update { it.copy(isPlaying = false) }
            } else {
                current.start()
                _state.update { it.copy(isPlaying = true) }
                trackProgress()
            }
            return
        }

        stop()
        val newPlayer = MediaPlayer()
        try {
            newPlayer.setDataSource(file.absolutePath)
            newPlayer.prepare()
        } catch (e: Exception) {
            Log.w(TAG, "Не удалось воспроизвести запись", e)
            newPlayer.release()
            return
        }
        newPlayer.setOnCompletionListener { stop() }
        newPlayer.start()
        player = newPlayer
        _state.value = State(mediaId, isPlaying = true, positionMs = 0, durationMs = newPlayer.duration)
        trackProgress()
    }

    fun stop() {
        progressJob?.cancel()
        progressJob = null
        player?.release()
        player = null
        _state.value = State()
    }

    /** Несколько раз в секунду обновляет положение полоски воспроизведения. */
    private fun trackProgress() {
        progressJob?.cancel()
        progressJob = scope.launch {
            while (isActive) {
                val current = player ?: break
                if (!current.isPlaying) break
                _state.update { it.copy(positionMs = current.currentPosition) }
                delay(100)
            }
        }
    }

    private companion object {
        const val TAG = "VoicePlayer"
    }
}
