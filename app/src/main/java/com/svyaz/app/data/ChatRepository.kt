package com.svyaz.app.data

import android.util.Log
import com.google.firebase.firestore.Blob
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.WriteBatch
import com.svyaz.app.util.UiMessages
import com.svyaz.app.util.toUserMessage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.Date

/**
 * Переписка.
 *
 * В базе данных:
 *  - chats/{chatId}                       — «шапка» чата для списка чатов;
 *  - chats/{chatId}/messages/{messageId}  — сами сообщения;
 *  - chats/{chatId}/media/{mediaId}       — фото и голосовые.
 *
 * Сообщения приходят мгновенно: приложение «подписано» на чат, и сервер сам
 * присылает каждое новое сообщение. Без интернета сообщение ждёт на телефоне
 * и уходит, когда связь появится.
 */
class ChatRepository(private val media: MediaRepository) {

    private val db = FirebaseFirestore.getInstance()
    private val chats = db.collection(Collections.CHATS)

    private fun messages(chatId: String) =
        chats.document(chatId).collection(Collections.MESSAGES)

    /** Список моих чатов, свежие сверху. */
    fun observeChats(myUid: String): Flow<List<Chat>> =
        chats.whereArrayContains("members", myUid).liveSnapshots().map { snapshot ->
            snapshot.documents
                .mapNotNull { it.toChat() }
                .sortedByDescending { it.updatedAt ?: Date() }
        }

    /** Последние [limit] сообщений чата, новые в начале списка. */
    fun observeMessages(chatId: String, myUid: String, limit: Long): Flow<List<Message>> =
        messages(chatId)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(limit)
            // INCLUDE — чтобы узнать момент, когда сообщение дошло до сервера («часики» → «галочка»)
            .liveSnapshots(MetadataChanges.INCLUDE)
            .map { snapshot -> snapshot.documents.map { it.toMessage(myUid) } }

    fun sendText(chatId: String, myUid: String, peerUid: String, text: String) {
        val batch = db.batch()
        batch.set(messages(chatId).document(), messageData(myUid, "text", text = text))
        batch.updateChat(chatId, myUid, peerUid, preview = text.take(200))
        batch.commitInBackground()
    }

    fun sendImage(
        chatId: String,
        myUid: String,
        peerUid: String,
        jpeg: ByteArray,
        width: Int,
        height: Int,
    ) {
        val mediaId = newMediaId(chatId)
        media.saveToCache(mediaId, jpeg)

        val batch = db.batch()
        batch.setMedia(chatId, mediaId, myUid, "image", jpeg)
        batch.set(
            messages(chatId).document(),
            messageData(myUid, "image", mediaId = mediaId, width = width, height = height),
        )
        batch.updateChat(chatId, myUid, peerUid, preview = "📷 Фото")
        batch.commitInBackground()
    }

    fun sendVoice(chatId: String, myUid: String, peerUid: String, audio: ByteArray, durationMs: Long) {
        val mediaId = newMediaId(chatId)
        media.saveToCache(mediaId, audio)

        val batch = db.batch()
        batch.setMedia(chatId, mediaId, myUid, "voice", audio)
        batch.set(
            messages(chatId).document(),
            messageData(myUid, "voice", mediaId = mediaId, durationMs = durationMs),
        )
        batch.updateChat(chatId, myUid, peerUid, preview = "🎤 Голосовое сообщение")
        batch.commitInBackground()
    }

    /**
     * Ставит «доставлено» сообщениям собеседника, которые этот телефон получил.
     * Вызывается, когда в списке чатов появляется новое сообщение.
     */
    suspend fun markDelivered(chatId: String, myUid: String) {
        val sent = messages(chatId).whereEqualTo("status", STATUS_SENT).get().await()
        sent.documents
            .filter { it.getString("senderId") != myUid }
            .forEach { it.reference.update("status", STATUS_DELIVERED) }
    }

    /** Ставит «прочитано» сообщениям собеседника и обнуляет счётчик непрочитанных. */
    fun markRead(chatId: String, myUid: String, loaded: List<Message>) {
        val unread = loaded.filter { it.senderId != myUid && it.status != MessageStatus.READ }
        if (unread.isEmpty()) return
        unread.forEach { messages(chatId).document(it.id).update("status", STATUS_READ) }
        chats.document(chatId).update("unread.$myUid", 0)
    }

    private fun newMediaId(chatId: String): String =
        chats.document(chatId).collection(Collections.MEDIA).document().id

    private fun WriteBatch.setMedia(
        chatId: String,
        mediaId: String,
        myUid: String,
        kind: String,
        bytes: ByteArray,
    ) {
        set(
            chats.document(chatId).collection(Collections.MEDIA).document(mediaId),
            mapOf(
                "ownerId" to myUid,
                "kind" to kind,
                "bytes" to Blob.fromBytes(bytes),
                "createdAt" to FieldValue.serverTimestamp(),
            ),
        )
    }

    private fun messageData(
        myUid: String,
        type: String,
        text: String = "",
        mediaId: String? = null,
        durationMs: Long = 0,
        width: Int = 0,
        height: Int = 0,
    ): Map<String, Any?> = mapOf(
        "senderId" to myUid,
        "type" to type,
        "text" to text,
        "mediaId" to mediaId,
        "durationMs" to durationMs,
        "width" to width,
        "height" to height,
        // Время ставит сервер, чтобы порядок сообщений не зависел от часов на телефоне
        "createdAt" to FieldValue.serverTimestamp(),
        "status" to STATUS_SENT,
    )

    /** Обновляет «шапку» чата: последнее сообщение и счётчик непрочитанных у собеседника. */
    private fun WriteBatch.updateChat(chatId: String, myUid: String, peerUid: String, preview: String) {
        set(
            chats.document(chatId),
            mapOf(
                "members" to listOf(myUid, peerUid).sorted(),
                "lastText" to preview,
                "lastSenderId" to myUid,
                "updatedAt" to FieldValue.serverTimestamp(),
                "unread" to mapOf(peerUid to FieldValue.increment(1)),
            ),
            SetOptions.merge(),
        )
    }

    /** Отправляет, не дожидаясь ответа сервера: без интернета запись подождёт в очереди. */
    private fun WriteBatch.commitInBackground() {
        commit().addOnFailureListener { error ->
            Log.w(TAG, "Сообщение не отправлено", error)
            UiMessages.show("Сообщение не отправлено. " + error.toUserMessage())
        }
    }

    private fun DocumentSnapshot.toChat(): Chat? {
        @Suppress("UNCHECKED_CAST")
        val members = get("members") as? List<String> ?: return null
        @Suppress("UNCHECKED_CAST")
        val unread = (get("unread") as? Map<String, Any?>).orEmpty()
            .mapValues { (it.value as? Number)?.toLong() ?: 0L }
        return Chat(
            id = id,
            members = members,
            lastText = getString("lastText").orEmpty(),
            lastSenderId = getString("lastSenderId").orEmpty(),
            updatedAt = getTimestamp("updatedAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                ?.toDate(),
            unread = unread,
        )
    }

    private fun DocumentSnapshot.toMessage(myUid: String): Message {
        val senderId = getString("senderId").orEmpty()
        val status = when {
            // Моё сообщение ещё не подтверждено сервером
            senderId == myUid && metadata.hasPendingWrites() -> MessageStatus.SENDING
            getString("status") == STATUS_READ -> MessageStatus.READ
            getString("status") == STATUS_DELIVERED -> MessageStatus.DELIVERED
            else -> MessageStatus.SENT
        }
        return Message(
            id = id,
            senderId = senderId,
            type = when (getString("type")) {
                "image" -> MessageType.IMAGE
                "voice" -> MessageType.VOICE
                else -> MessageType.TEXT
            },
            text = getString("text").orEmpty(),
            mediaId = getString("mediaId"),
            durationMs = getLong("durationMs") ?: 0L,
            width = (getLong("width") ?: 0L).toInt(),
            height = (getLong("height") ?: 0L).toInt(),
            // Пока сервер не проставил время, берём время телефона
            createdAt = getTimestamp("createdAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                ?.toDate() ?: Date(),
            status = status,
        )
    }

    private companion object {
        const val TAG = "ChatRepository"
        const val STATUS_SENT = "sent"
        const val STATUS_DELIVERED = "delivered"
        const val STATUS_READ = "read"
    }
}
