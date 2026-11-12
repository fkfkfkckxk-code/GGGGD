package com.svyaz.app.call

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.SystemClock
import android.util.Log
import androidx.core.content.ContextCompat
import com.svyaz.app.data.CallDoc
import com.svyaz.app.data.CallRepository
import com.svyaz.app.data.SdpData
import com.svyaz.app.util.UiMessages
import com.svyaz.app.util.toUserMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.webrtc.AudioSource
import org.webrtc.AudioTrack
import org.webrtc.DataChannel
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import org.webrtc.audio.JavaAudioDeviceModule
import kotlin.concurrent.thread

/** Этапы звонка. */
enum class CallPhase {
    /** Звонка нет. */
    IDLE,

    /** Я звоню и жду ответа. */
    OUTGOING,

    /** Мне звонят. */
    INCOMING,

    /** Ответили, телефоны устанавливают соединение. */
    CONNECTING,

    /** Идёт разговор. */
    ACTIVE,

    /** Звонок закончился — пару секунд показываем причину. */
    ENDED,
}

/** Всё, что нужно экрану звонка. */
data class CallUiState(
    val phase: CallPhase = CallPhase.IDLE,
    val peerUid: String? = null,
    val isMuted: Boolean = false,
    val isSpeakerOn: Boolean = false,
    /** Момент начала разговора по часам SystemClock.elapsedRealtime(). */
    val connectedAt: Long? = null,
    val endMessage: String? = null,
)

/**
 * Управляет голосовым звонком.
 *
 * Как устроен звонок:
 *  1. Звонящий создаёт «предложение» (offer) — описание своего звука — и кладёт его в базу.
 *  2. У собеседника срабатывает подписка на входящие вызовы, он видит экран звонка.
 *  3. Приняв вызов, он кладёт в базу «ответ» (answer).
 *  4. Оба телефона обмениваются своими сетевыми адресами (ICE-кандидатами).
 *  5. Дальше звук идёт напрямую между телефонами, минуя базу данных.
 *
 * Все методы и поля используются только из главного потока.
 */
class CallManager(
    private val appContext: Context,
    private val signaling: CallRepository,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow(CallUiState())
    val state: StateFlow<CallUiState> = _state.asStateFlow()

    private val audio = CallAudio(appContext)
    private val factory: PeerConnectionFactory by lazy { createFactory() }

    private var myUid: String? = null
    private var appVisible = false
    private var incomingJob: Job? = null

    // --- Текущий звонок ---
    private var callId: String? = null
    private var isCaller = false
    private var incomingOffer: SdpData? = null
    private var peerConnection: PeerConnection? = null
    private var audioSource: AudioSource? = null
    private var localTrack: AudioTrack? = null
    private val callJobs = mutableListOf<Job>()
    private var disconnectJob: Job? = null
    private var endedJob: Job? = null

    /** Запись о звонке уже есть в базе — можно отправлять свои сетевые адреса. */
    private var callDocCreated = false
    private val localCandidatesQueue = mutableListOf<IceCandidate>()

    /** Описание звука собеседника применено — можно принимать его сетевые адреса. */
    private var remoteDescriptionRequested = false
    private var remoteDescriptionReady = false
    private val remoteCandidatesQueue = mutableListOf<IceCandidate>()

    /** Сообщает, кто вошёл в приложение (null — никто). */
    fun setUser(uid: String?) {
        if (myUid == uid) return
        if (callId != null) hangUp()
        myUid = uid
        incomingJob?.cancel()
        incomingJob = null
        updateIncomingListener()
    }

    /** Входящие вызовы принимаем, пока приложение открыто на экране. */
    fun setAppVisible(visible: Boolean) {
        appVisible = visible
        updateIncomingListener()
    }

    private fun updateIncomingListener() {
        val uid = myUid
        if (uid == null || !appVisible) {
            incomingJob?.cancel()
            incomingJob = null
            return
        }
        if (incomingJob?.isActive == true) return
        incomingJob = scope.launch {
            signaling.observeIncoming(uid).collect { onIncoming(it) }
        }
    }

    // ---------------------------------------------------------------------
    // Исходящий звонок
    // ---------------------------------------------------------------------

    /** Позвонить пользователю [peerUid]. */
    fun startCall(peerUid: String) {
        val me = myUid ?: return
        if (callId != null) return
        if (!hasMicPermission()) {
            UiMessages.show("Для звонка нужен доступ к микрофону")
            return
        }

        val id = signaling.newCallId()
        resetCallFields()
        callId = id
        isCaller = true
        _state.value = CallUiState(phase = CallPhase.OUTGOING, peerUid = peerUid)

        val pc = startMedia(id, me)
        if (pc == null) {
            cleanup("Не удалось начать звонок")
            return
        }
        audio.startRingback()

        pc.createOffer(object : SdpAdapter("createOffer", id) {
            override fun onCreateSuccess(description: SessionDescription?) {
                description ?: return
                onMain(id) {
                    pc.setLocalDescription(object : SdpAdapter("setLocalDescription", id) {
                        override fun onSetSuccess() {
                            onMain(id) { publishOffer(id, me, peerUid, description) }
                        }
                    }, description)
                }
            }
        }, MediaConstraints())

        // Если никто не ответил — прекращаем вызов
        callJobs += scope.launch {
            delay(CallConfig.RING_TIMEOUT_MS)
            if (callId == id && _state.value.phase == CallPhase.OUTGOING) {
                endCall(CallDoc.REASON_MISSED, "Нет ответа")
            }
        }
    }

    private fun publishOffer(id: String, me: String, peerUid: String, offer: SessionDescription) {
        callJobs += scope.launch {
            try {
                signaling.createCall(id, me, peerUid, offer)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (callId == id) cleanup(e.toUserMessage())
                return@launch
            }
            if (callId != id) return@launch

            callDocCreated = true
            localCandidatesQueue.forEach { signaling.addCandidate(id, me, it) }
            localCandidatesQueue.clear()
            watchCall(id, peerUid)
        }
    }

    private fun onAnswer(id: String, answer: SdpData) {
        val pc = peerConnection ?: return
        remoteDescriptionRequested = true
        audio.stopRingback()
        _state.update { it.copy(phase = CallPhase.CONNECTING) }
        pc.setRemoteDescription(object : SdpAdapter("setRemoteDescription", id) {
            override fun onSetSuccess() {
                onMain(id) { onRemoteDescriptionReady() }
            }
        }, SessionDescription(SessionDescription.Type.ANSWER, answer.sdp))
    }

    // ---------------------------------------------------------------------
    // Входящий звонок
    // ---------------------------------------------------------------------

    private fun onIncoming(calls: List<CallDoc>) {
        val now = System.currentTimeMillis()
        for (call in calls) {
            if (call.id == callId) continue
            val createdAt = call.createdAt?.time ?: now
            if (now - createdAt > CallConfig.STALE_CALL_MS) {
                // Звонивший давно закрыл приложение, а запись осталась
                signaling.end(call.id, CallDoc.REASON_MISSED)
                continue
            }
            val offer = call.offer ?: continue
            if (callId != null) {
                signaling.end(call.id, CallDoc.REASON_BUSY) // я уже разговариваю
                continue
            }
            ring(call.id, call.callerId, offer)
        }
    }

    private fun ring(id: String, callerUid: String, offer: SdpData) {
        resetCallFields()
        callId = id
        isCaller = false
        incomingOffer = offer
        callDocCreated = true
        _state.value = CallUiState(phase = CallPhase.INCOMING, peerUid = callerUid)
        audio.startRingtone()
        watchCall(id, callerUid)

        callJobs += scope.launch {
            delay(CallConfig.RING_TIMEOUT_MS + 15_000)
            if (callId == id && _state.value.phase == CallPhase.INCOMING) {
                cleanup("Пропущенный звонок")
            }
        }
    }

    /** Принять входящий вызов. */
    fun accept() {
        val id = callId ?: return
        val me = myUid ?: return
        val offer = incomingOffer ?: return
        if (_state.value.phase != CallPhase.INCOMING) return
        if (!hasMicPermission()) {
            UiMessages.show("Чтобы ответить, разрешите доступ к микрофону")
            return
        }

        audio.stopRingtone()
        _state.update { it.copy(phase = CallPhase.CONNECTING) }
        val pc = startMedia(id, me)
        if (pc == null) {
            endCall(CallDoc.REASON_FAILED, "Не удалось ответить на звонок")
            return
        }

        remoteDescriptionRequested = true
        pc.setRemoteDescription(object : SdpAdapter("setRemoteDescription", id) {
            override fun onSetSuccess() {
                onMain(id) {
                    onRemoteDescriptionReady()
                    createAnswer(id, pc)
                }
            }
        }, SessionDescription(SessionDescription.Type.OFFER, offer.sdp))
    }

    private fun createAnswer(id: String, pc: PeerConnection) {
        pc.createAnswer(object : SdpAdapter("createAnswer", id) {
            override fun onCreateSuccess(description: SessionDescription?) {
                description ?: return
                onMain(id) {
                    pc.setLocalDescription(object : SdpAdapter("setLocalDescription", id) {
                        override fun onSetSuccess() {
                            onMain(id) { publishAnswer(id, description) }
                        }
                    }, description)
                }
            }
        }, MediaConstraints())
    }

    private fun publishAnswer(id: String, answer: SessionDescription) {
        callJobs += scope.launch {
            try {
                signaling.answer(id, answer)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (callId == id) endCall(CallDoc.REASON_FAILED, e.toUserMessage())
            }
        }
    }

    /** Отклонить входящий вызов. */
    fun decline() {
        if (_state.value.phase == CallPhase.INCOMING) endCall(CallDoc.REASON_DECLINED, null)
    }

    // ---------------------------------------------------------------------
    // Общее для обеих сторон
    // ---------------------------------------------------------------------

    /** Положить трубку. */
    fun hangUp() {
        endCall(CallDoc.REASON_HANGUP, null)
    }

    fun toggleMute() {
        val muted = !_state.value.isMuted
        localTrack?.setEnabled(!muted)
        _state.update { it.copy(isMuted = muted) }
    }

    fun toggleSpeaker() {
        val speakerOn = !_state.value.isSpeakerOn
        audio.setSpeaker(speakerOn)
        _state.update { it.copy(isSpeakerOn = speakerOn) }
    }

    /** Следит за записью о звонке и за сетевыми адресами собеседника. */
    private fun watchCall(id: String, peerUid: String) {
        callJobs += scope.launch {
            signaling.observeCall(id).collect { call ->
                if (callId != id || call == null) return@collect
                val answer = call.answer
                when {
                    call.status == CallDoc.STATUS_ENDED -> onRemoteEnded(call.endReason)
                    isCaller && answer != null && !remoteDescriptionRequested -> onAnswer(id, answer)
                }
            }
        }
        callJobs += scope.launch {
            signaling.observeCandidates(id, peerUid).collect { candidate ->
                if (callId != id) return@collect
                if (remoteDescriptionReady) {
                    peerConnection?.addIceCandidate(candidate)
                } else {
                    remoteCandidatesQueue += candidate
                }
            }
        }
    }

    private fun onRemoteDescriptionReady() {
        remoteDescriptionReady = true
        remoteCandidatesQueue.forEach { peerConnection?.addIceCandidate(it) }
        remoteCandidatesQueue.clear()
    }

    /** Включает микрофон и создаёт соединение WebRTC. */
    private fun startMedia(id: String, me: String): PeerConnection? {
        CallService.start(appContext)
        audio.startCallMode()
        return try {
            val config = PeerConnection.RTCConfiguration(CallConfig.iceServers).apply {
                sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
                continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
            }
            val pc = factory.createPeerConnection(config, PeerObserver(id, me)) ?: return null
            val source = factory.createAudioSource(MediaConstraints())
            val track = factory.createAudioTrack("audio", source)
            pc.addTrack(track, listOf("stream"))
            peerConnection = pc
            audioSource = source
            localTrack = track
            pc
        } catch (e: Exception) {
            Log.w(TAG, "Не удалось создать соединение", e)
            null
        }
    }

    private fun onConnectionState(id: String, newState: PeerConnection.PeerConnectionState?) {
        when (newState) {
            PeerConnection.PeerConnectionState.CONNECTED -> {
                disconnectJob?.cancel()
                audio.stopRingback()
                if (_state.value.phase != CallPhase.ACTIVE) {
                    _state.update {
                        it.copy(phase = CallPhase.ACTIVE, connectedAt = SystemClock.elapsedRealtime())
                    }
                }
            }

            PeerConnection.PeerConnectionState.DISCONNECTED -> {
                // Связь пропала — даём ей время восстановиться
                disconnectJob?.cancel()
                disconnectJob = scope.launch {
                    delay(CallConfig.RECONNECT_TIMEOUT_MS)
                    if (callId == id) endCall(CallDoc.REASON_FAILED, "Связь потеряна")
                }
            }

            PeerConnection.PeerConnectionState.FAILED ->
                endCall(CallDoc.REASON_FAILED, "Не удалось установить соединение")

            else -> Unit
        }
    }

    /** Собеседник завершил звонок. */
    private fun onRemoteEnded(reason: String?) {
        val message = when {
            _state.value.phase == CallPhase.INCOMING -> "Пропущенный звонок"
            reason == CallDoc.REASON_DECLINED -> "Собеседник отклонил звонок"
            reason == CallDoc.REASON_BUSY -> "Собеседник сейчас разговаривает"
            reason == CallDoc.REASON_MISSED -> "Нет ответа"
            reason == CallDoc.REASON_FAILED -> "Связь потеряна"
            else -> "Звонок завершён"
        }
        cleanup(message)
    }

    /** Завершает звонок с моей стороны и сообщает об этом собеседнику. */
    private fun endCall(reason: String, message: String?) {
        val id = callId ?: return
        signaling.end(id, reason)
        cleanup(message)
    }

    /** Освобождает всё, что было занято звонком. [message] показывается на экране пару секунд. */
    private fun cleanup(message: String?) {
        val peerUid = _state.value.peerUid
        callJobs.forEach { it.cancel() }
        callJobs.clear()
        disconnectJob?.cancel()
        disconnectJob = null
        audio.stopCallMode()

        val pc = peerConnection
        val source = audioSource
        val track = localTrack
        callId = null
        resetCallFields()

        if (pc != null) {
            // Освобождение WebRTC может занять время — делаем это не в главном потоке
            thread(name = "call-cleanup") {
                runCatching { pc.dispose() }
                runCatching { track?.dispose() }
                runCatching { source?.dispose() }
            }
        }
        CallService.stop(appContext)

        endedJob?.cancel()
        if (message == null) {
            _state.value = CallUiState()
        } else {
            _state.value = CallUiState(phase = CallPhase.ENDED, peerUid = peerUid, endMessage = message)
            endedJob = scope.launch {
                delay(2_000)
                if (_state.value.phase == CallPhase.ENDED) _state.value = CallUiState()
            }
        }
    }

    private fun resetCallFields() {
        isCaller = false
        incomingOffer = null
        peerConnection = null
        audioSource = null
        localTrack = null
        callDocCreated = false
        localCandidatesQueue.clear()
        remoteDescriptionRequested = false
        remoteDescriptionReady = false
        remoteCandidatesQueue.clear()
    }

    private fun hasMicPermission(): Boolean =
        ContextCompat.checkSelfPermission(appContext, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    private fun createFactory(): PeerConnectionFactory {
        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(appContext)
                .createInitializationOptions(),
        )
        val audioModule = JavaAudioDeviceModule.builder(appContext).createAudioDeviceModule()
        return PeerConnectionFactory.builder()
            .setAudioDeviceModule(audioModule)
            .createPeerConnectionFactory()
            .also { audioModule.release() }
    }

    /**
     * WebRTC сообщает о событиях из своих потоков. Переносим обработку в главный поток
     * и пропускаем события, если звонок [id] уже закончился.
     */
    private fun onMain(id: String, block: () -> Unit) {
        scope.launch { if (callId == id) block() }
    }

    /** Заготовка: реагируем только на нужные события, любая ошибка завершает звонок. */
    private open inner class SdpAdapter(
        private val step: String,
        private val id: String,
    ) : SdpObserver {
        override fun onCreateSuccess(description: SessionDescription?) {}
        override fun onSetSuccess() {}
        override fun onCreateFailure(error: String?) = fail(error)
        override fun onSetFailure(error: String?) = fail(error)

        private fun fail(error: String?) {
            Log.w(TAG, "$step: $error")
            onMain(id) { endCall(CallDoc.REASON_FAILED, "Не удалось установить соединение") }
        }
    }

    /** События соединения: новые сетевые адреса и смена состояния связи. */
    private inner class PeerObserver(
        private val id: String,
        private val me: String,
    ) : PeerConnection.Observer {

        override fun onIceCandidate(candidate: IceCandidate?) {
            candidate ?: return
            onMain(id) {
                if (callDocCreated) {
                    signaling.addCandidate(id, me, candidate)
                } else {
                    localCandidatesQueue += candidate
                }
            }
        }

        override fun onConnectionChange(newState: PeerConnection.PeerConnectionState?) {
            onMain(id) { onConnectionState(id, newState) }
        }

        // Остальные события приложению не нужны
        override fun onSignalingChange(state: PeerConnection.SignalingState?) {}
        override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) {}
        override fun onIceConnectionReceivingChange(receiving: Boolean) {}
        override fun onIceGatheringChange(state: PeerConnection.IceGatheringState?) {}
        override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) {}
        override fun onAddStream(stream: MediaStream?) {}
        override fun onRemoveStream(stream: MediaStream?) {}
        override fun onDataChannel(channel: DataChannel?) {}
        override fun onRenegotiationNeeded() {}
    }

    private companion object {
        const val TAG = "CallManager"
    }
}
