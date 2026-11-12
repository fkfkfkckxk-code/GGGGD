package com.svyaz.app.data

import android.util.Log
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import org.webrtc.IceCandidate
import org.webrtc.SessionDescription

/**
 * «Сигнализация» звонков — обмен служебными данными, чтобы два телефона нашли друг друга.
 * Сам звук идёт напрямую между телефонами (WebRTC), а через базу передаётся только:
 *  - calls/{callId}                  — кто кому звонит, состояние звонка, описания соединения;
 *  - calls/{callId}/candidates/{id}  — сетевые адреса, по которым телефоны пробуют соединиться.
 */
class CallRepository {

    private val calls = FirebaseFirestore.getInstance().collection(Collections.CALLS)

    fun newCallId(): String = calls.document().id

    /** Создаёт звонок: у собеседника появится входящий вызов. */
    suspend fun createCall(callId: String, callerId: String, calleeId: String, offer: SessionDescription) {
        calls.document(callId).set(
            mapOf(
                "callerId" to callerId,
                "calleeId" to calleeId,
                "status" to CallDoc.STATUS_RINGING,
                "offer" to mapOf("type" to "offer", "sdp" to offer.description),
                "createdAt" to FieldValue.serverTimestamp(),
            ),
        ).await()
    }

    /** Отвечает на звонок. */
    suspend fun answer(callId: String, answer: SessionDescription) {
        calls.document(callId).update(
            mapOf(
                "status" to CallDoc.STATUS_ACCEPTED,
                "answer" to mapOf("type" to "answer", "sdp" to answer.description),
            ),
        ).await()
    }

    /** Завершает звонок и сообщает собеседнику причину. */
    fun end(callId: String, reason: String) {
        calls.document(callId)
            .update(mapOf("status" to CallDoc.STATUS_ENDED, "endReason" to reason))
            .addOnFailureListener { Log.w(TAG, "Не удалось завершить звонок $callId", it) }
    }

    fun observeCall(callId: String): Flow<CallDoc?> =
        calls.document(callId).liveSnapshots().map { it.toCallDoc() }

    /** Звонки, которые прямо сейчас «звонят» мне. */
    fun observeIncoming(myUid: String): Flow<List<CallDoc>> =
        calls.whereEqualTo("calleeId", myUid)
            .whereEqualTo("status", CallDoc.STATUS_RINGING)
            .liveSnapshots()
            .map { snapshot -> snapshot.documents.mapNotNull { it.toCallDoc() } }

    fun addCandidate(callId: String, fromUid: String, candidate: IceCandidate) {
        calls.document(callId).collection(Collections.CANDIDATES).add(
            mapOf(
                "from" to fromUid,
                "sdpMid" to candidate.sdpMid,
                "sdpMLineIndex" to candidate.sdpMLineIndex,
                "candidate" to candidate.sdp,
            ),
        ).addOnFailureListener { Log.w(TAG, "Не удалось отправить сетевой адрес", it) }
    }

    /** Сетевые адреса собеседника — по одному, по мере появления. */
    fun observeCandidates(callId: String, fromUid: String): Flow<IceCandidate> = callbackFlow {
        val registration = calls.document(callId).collection(Collections.CANDIDATES)
            .whereEqualTo("from", fromUid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w(TAG, "Ошибка чтения сетевых адресов", error)
                    close()
                    return@addSnapshotListener
                }
                snapshot?.documentChanges
                    ?.filter { it.type == DocumentChange.Type.ADDED }
                    ?.forEach { change ->
                        val doc = change.document
                        val sdp = doc.getString("candidate") ?: return@forEach
                        trySend(
                            IceCandidate(
                                doc.getString("sdpMid"),
                                (doc.getLong("sdpMLineIndex") ?: 0L).toInt(),
                                sdp,
                            ),
                        )
                    }
            }
        awaitClose { registration.remove() }
    }

    private fun DocumentSnapshot.toCallDoc(): CallDoc? {
        if (!exists()) return null
        return CallDoc(
            id = id,
            callerId = getString("callerId").orEmpty(),
            calleeId = getString("calleeId").orEmpty(),
            status = getString("status").orEmpty(),
            endReason = getString("endReason"),
            offer = sdpField("offer"),
            answer = sdpField("answer"),
            createdAt = getTimestamp("createdAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                ?.toDate(),
        )
    }

    private fun DocumentSnapshot.sdpField(name: String): SdpData? {
        val map = get(name) as? Map<*, *> ?: return null
        val sdp = map["sdp"] as? String ?: return null
        return SdpData(type = map["type"] as? String ?: name, sdp = sdp)
    }

    private companion object {
        const val TAG = "CallRepository"
    }
}
