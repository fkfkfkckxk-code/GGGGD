package com.svyaz.app.media

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.os.SystemClock
import android.util.Log
import java.io.File

/** Готовая запись голоса. */
class Recording(val file: File, val durationMs: Long)

/**
 * Запись голосовых сообщений с микрофона.
 * Формат — AAC с небольшим битрейтом: голос звучит хорошо, а три минуты записи
 * помещаются в лимит размера вложения.
 */
class VoiceRecorder(private val context: Context) {

    private var recorder: MediaRecorder? = null
    private var file: File? = null
    private var startedAt = 0L

    val isRecording: Boolean
        get() = recorder != null

    /** Сколько миллисекунд уже идёт запись. */
    val elapsedMs: Long
        get() = if (isRecording) SystemClock.elapsedRealtime() - startedAt else 0L

    /** Начинает запись. Возвращает false, если микрофон недоступен. */
    fun start(): Boolean {
        if (isRecording) return true
        val output = File(context.cacheDir, "voice_${System.currentTimeMillis()}.m4a")
        val newRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }
        return try {
            newRecorder.setAudioSource(MediaRecorder.AudioSource.MIC)
            newRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            newRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            newRecorder.setAudioChannels(1)
            newRecorder.setAudioSamplingRate(16_000)
            newRecorder.setAudioEncodingBitRate(32_000)
            newRecorder.setOutputFile(output.absolutePath)
            newRecorder.prepare()
            newRecorder.start()
            recorder = newRecorder
            file = output
            startedAt = SystemClock.elapsedRealtime()
            true
        } catch (e: Exception) {
            Log.w(TAG, "Не удалось начать запись", e)
            newRecorder.release()
            output.delete()
            false
        }
    }

    /** Заканчивает запись. Возвращает null, если запись не получилась или короче секунды. */
    fun stop(): Recording? {
        val current = recorder ?: return null
        val output = file
        val duration = elapsedMs
        recorder = null
        file = null

        val stoppedOk = try {
            current.stop()
            true
        } catch (e: RuntimeException) {
            // Так бывает, если остановить запись сразу после начала
            false
        } finally {
            current.release()
        }

        if (output == null) return null
        if (!stoppedOk || duration < MIN_DURATION_MS || output.length() == 0L) {
            output.delete()
            return null
        }
        return Recording(output, duration)
    }

    /** Отменяет запись и удаляет файл. */
    fun cancel() {
        stop()?.file?.delete()
    }

    companion object {
        private const val TAG = "VoiceRecorder"
        private const val MIN_DURATION_MS = 1_000L

        /** Самая длинная запись — 3 минуты (около 0,7 МБ). */
        const val MAX_DURATION_MS = 180_000L
    }
}
