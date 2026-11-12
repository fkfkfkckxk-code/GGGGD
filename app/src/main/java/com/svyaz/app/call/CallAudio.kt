package com.svyaz.app.call

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.Ringtone
import android.media.RingtoneManager
import android.media.ToneGenerator
import android.os.Build
import android.os.PowerManager
import android.util.Log

/**
 * Звук во время звонка: мелодия входящего вызова, гудки, динамик у уха или
 * громкая связь, а также отключение экрана, когда телефон поднесён к уху.
 */
class CallAudio(private val context: Context) {

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager

    private var ringtone: Ringtone? = null
    private var ringback: ToneGenerator? = null
    private var focusRequest: AudioFocusRequest? = null
    private var proximityLock: PowerManager.WakeLock? = null
    private var inCallMode = false

    /** Мелодия входящего вызова (та же, что выбрана в телефоне для звонков). */
    fun startRingtone() {
        stopRingtone()
        runCatching {
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            ringtone = RingtoneManager.getRingtone(context, uri)?.apply {
                audioAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) isLooping = true
                play()
            }
        }.onFailure { Log.w(TAG, "Не удалось включить мелодию вызова", it) }
    }

    fun stopRingtone() {
        runCatching { ringtone?.stop() }
        ringtone = null
    }

    /** Длинные гудки, которые слышит звонящий, пока ему не ответили. */
    fun startRingback() {
        stopRingback()
        runCatching {
            ringback = ToneGenerator(AudioManager.STREAM_VOICE_CALL, 70).also {
                it.startTone(ToneGenerator.TONE_SUP_RINGTONE)
            }
        }.onFailure { Log.w(TAG, "Не удалось включить гудки", it) }
    }

    fun stopRingback() {
        runCatching {
            ringback?.stopTone()
            ringback?.release()
        }
        ringback = null
    }

    /** Переводит телефон в режим разговора: звук идёт в динамик у уха. */
    fun startCallMode() {
        if (inCallMode) return
        inCallMode = true

        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
            )
            .build()
        focusRequest = request
        audioManager.requestAudioFocus(request) // просим другие приложения приглушить музыку
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        setSpeaker(false)
    }

    /** Включает или выключает громкую связь. */
    fun setSpeaker(on: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val speaker = audioManager.availableCommunicationDevices
                .firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
            if (on && speaker != null) {
                audioManager.setCommunicationDevice(speaker)
            } else {
                // Обычный режим: наушники, если подключены, иначе динамик у уха
                audioManager.clearCommunicationDevice()
            }
        } else {
            @Suppress("DEPRECATION")
            audioManager.isSpeakerphoneOn = on
        }
        // С громкой связью телефон держат перед собой — экран гасить не нужно
        if (on) releaseProximityLock() else acquireProximityLock()
    }

    /** Возвращает звук телефона в обычное состояние. */
    fun stopCallMode() {
        stopRingtone()
        stopRingback()
        releaseProximityLock()
        if (!inCallMode) return
        inCallMode = false

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            audioManager.clearCommunicationDevice()
        } else {
            @Suppress("DEPRECATION")
            audioManager.isSpeakerphoneOn = false
        }
        audioManager.mode = AudioManager.MODE_NORMAL
        focusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        focusRequest = null
    }

    /** Гасит экран, когда телефон у уха, чтобы щека не нажимала кнопки. */
    private fun acquireProximityLock() {
        if (!inCallMode || proximityLock?.isHeld == true) return
        val level = PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK
        if (!powerManager.isWakeLockLevelSupported(level)) return
        runCatching {
            proximityLock = powerManager.newWakeLock(level, "svyaz:call").apply {
                acquire(MAX_CALL_MS)
            }
        }
    }

    private fun releaseProximityLock() {
        runCatching { proximityLock?.takeIf { it.isHeld }?.release() }
        proximityLock = null
    }

    private companion object {
        const val TAG = "CallAudio"
        const val MAX_CALL_MS = 4 * 60 * 60 * 1000L
    }
}
