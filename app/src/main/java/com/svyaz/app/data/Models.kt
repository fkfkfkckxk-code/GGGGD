package com.svyaz.app.data

import java.util.Date

/*
 * Модели — «формы» данных, с которыми работает приложение.
 * В базе Firestore они лежат в коллекциях (папках) с такими же по смыслу полями.
 */

/** Названия коллекций в базе данных. */
object Collections {
    const val USERS = "users"
    const val NICKNAMES = "nicknames"
    const val PHONES = "phones"
    const val EMAILS = "emails"
    const val FRIENDSHIPS = "friendships"
    const val CHATS = "chats"
    const val MESSAGES = "messages"
    const val MEDIA = "media"
    const val CALLS = "calls"
    const val CANDIDATES = "candidates"
}

/**
 * Общий идентификатор пары пользователей: два uid по алфавиту через «_».
 * Он одинаковый, кто бы из двоих его ни вычислял, поэтому служит именем
 * и для записи о дружбе, и для чата этих двух людей.
 */
fun pairId(uidA: String, uidB: String): String =
    if (uidA < uidB) "${uidA}_$uidB" else "${uidB}_$uidA"

/** Профиль пользователя. */
class UserProfile(
    val uid: String,
    val name: String,
    /** Уникальный ник без символа «@», например ivan_petrov. */
    val nickname: String,
    /** Маленькое фото профиля в формате JPEG или null, если фото нет. */
    val avatar: ByteArray?,
)

/** Запись о дружбе или о заявке в друзья между двумя людьми. */
data class Friendship(
    val id: String,
    val members: List<String>,
    /** Кто отправил заявку. */
    val requestedBy: String,
    /** "pending" — заявка ждёт ответа, "accepted" — вы друзья. */
    val status: String,
) {
    val isAccepted: Boolean get() = status == STATUS_ACCEPTED

    fun otherUid(myUid: String): String = members.firstOrNull { it != myUid } ?: myUid

    companion object {
        const val STATUS_PENDING = "pending"
        const val STATUS_ACCEPTED = "accepted"
    }
}

/** Чат в списке чатов: с кем и какое сообщение было последним. */
data class Chat(
    val id: String,
    val members: List<String>,
    val lastText: String,
    val lastSenderId: String,
    val updatedAt: Date?,
    /** Сколько непрочитанных сообщений у каждого участника: uid → количество. */
    val unread: Map<String, Long>,
) {
    fun otherUid(myUid: String): String = members.firstOrNull { it != myUid } ?: myUid

    fun unreadFor(uid: String): Long = unread[uid] ?: 0L
}

enum class MessageType { TEXT, IMAGE, VOICE }

/** Статус сообщения: отправляется → отправлено → доставлено → прочитано. */
enum class MessageStatus { SENDING, SENT, DELIVERED, READ }

data class Message(
    val id: String,
    val senderId: String,
    val type: MessageType,
    val text: String,
    /** Идентификатор вложения (фото или голосового), если оно есть. */
    val mediaId: String?,
    /** Длительность голосового сообщения в миллисекундах. */
    val durationMs: Long,
    /** Размеры фото — чтобы заранее отвести под него место на экране. */
    val width: Int,
    val height: Int,
    val createdAt: Date,
    val status: MessageStatus,
)

/** Описание звукового соединения, которым обмениваются телефоны при звонке. */
data class SdpData(val type: String, val sdp: String)

/** Запись о звонке в базе данных. */
data class CallDoc(
    val id: String,
    val callerId: String,
    val calleeId: String,
    /** "ringing" — идёт вызов, "accepted" — ответили, "ended" — завершён. */
    val status: String,
    /** Почему завершён: hangup, declined, busy, missed, failed. */
    val endReason: String?,
    val offer: SdpData?,
    val answer: SdpData?,
    val createdAt: Date?,
) {
    companion object {
        const val STATUS_RINGING = "ringing"
        const val STATUS_ACCEPTED = "accepted"
        const val STATUS_ENDED = "ended"

        const val REASON_HANGUP = "hangup"
        const val REASON_DECLINED = "declined"
        const val REASON_BUSY = "busy"
        const val REASON_MISSED = "missed"
        const val REASON_FAILED = "failed"
    }
}
