package com.svyaz.app.data

import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.Blob
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.MetadataChanges
import com.svyaz.app.util.NicknameTakenException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.tasks.await
import java.util.concurrent.ConcurrentHashMap

/** Состояние моего профиля: ещё загружается, не создан или готов. */
sealed interface ProfileState {
    data object Loading : ProfileState
    data object Missing : ProfileState
    data class Exists(val profile: UserProfile) : ProfileState
}

/**
 * Профили пользователей и поиск людей.
 *
 * В базе данных:
 *  - users/{uid}          — профиль: имя, ник, фото;
 *  - nicknames/{ник}      — кому принадлежит ник (так обеспечивается уникальность);
 *  - phones/{телефон}     — кому принадлежит номер (для поиска по телефону);
 *  - emails/{почта}       — кому принадлежит почта Google (для поиска по аккаунту Google).
 */
class UserRepository(private val scope: CoroutineScope) {

    private val db = FirebaseFirestore.getInstance()
    private val users = db.collection(Collections.USERS)
    private val nicknames = db.collection(Collections.NICKNAMES)
    private val phones = db.collection(Collections.PHONES)
    private val emails = db.collection(Collections.EMAILS)

    /** Уже открытые подписки на профили, чтобы не подписываться на одного человека дважды. */
    private val cache = ConcurrentHashMap<String, StateFlow<UserProfile?>>()

    /** Профиль пользователя, который сам обновляется, когда человек меняет имя или фото. */
    fun observeUser(uid: String): StateFlow<UserProfile?> = cache.getOrPut(uid) {
        users.document(uid).liveSnapshots()
            .map { it.toUserProfile() }
            .stateIn(scope, SharingStarted.WhileSubscribed(5_000), null)
    }

    /** Забывает открытые подписки (вызывается при выходе из аккаунта). */
    fun clearCache() = cache.clear()

    /** Следит, создан ли профиль у вошедшего пользователя. */
    fun observeProfileState(uid: String): Flow<ProfileState> =
        // INCLUDE нужен, чтобы узнать, когда ответ пришёл уже с сервера, а не из памяти телефона
        users.document(uid).liveSnapshots(MetadataChanges.INCLUDE).map { snapshot ->
            val profile = snapshot.toUserProfile()
            when {
                profile != null -> ProfileState.Exists(profile)
                // Телефон ещё не связался с сервером — рано решать, что профиля нет
                snapshot.metadata.isFromCache -> ProfileState.Loading
                else -> ProfileState.Missing
            }
        }

    suspend fun getUser(uid: String): UserProfile? =
        users.document(uid).get().await().toUserProfile()

    /**
     * Создаёт или изменяет профиль.
     * [newAvatar] — новое фото или null, если фото менять не нужно.
     * Бросает [NicknameTakenException], если ник занят другим человеком.
     */
    suspend fun saveProfile(uid: String, name: String, nickname: String, newAvatar: ByteArray?) {
        val userRef = users.document(uid)
        val nickRef = nicknames.document(nickname)

        // Транзакция: проверка «ник свободен» и запись выполняются как одно целое,
        // поэтому два человека не смогут одновременно занять один и тот же ник.
        val saved = db.runTransaction { tx ->
            val current = tx.get(userRef)
            val nickDoc = tx.get(nickRef)
            if (nickDoc.exists() && nickDoc.getString("uid") != uid) {
                return@runTransaction false
            }

            val oldNickname = current.getString("nickname")
            if (oldNickname != null && oldNickname != nickname) {
                tx.delete(nicknames.document(oldNickname)) // освобождаем прежний ник
            }
            if (!nickDoc.exists()) {
                tx.set(nickRef, mapOf("uid" to uid))
            }

            val data = hashMapOf<String, Any?>(
                "name" to name,
                "nickname" to nickname,
                "updatedAt" to FieldValue.serverTimestamp(),
            )
            if (newAvatar != null) data["avatar"] = Blob.fromBytes(newAvatar)

            if (current.exists()) {
                tx.update(userRef, data)
            } else {
                data["createdAt"] = FieldValue.serverTimestamp()
                if (newAvatar == null) data["avatar"] = null
                tx.set(userRef, data)
            }
            true
        }.await()

        if (!saved) throw NicknameTakenException()
    }

    /**
     * Записывает «указатели» телефон → пользователь и почта → пользователь,
     * чтобы друзья могли найти вас по номеру или по аккаунту Google.
     * Правила базы разрешают записать только свой, подтверждённый номер и почту.
     */
    suspend fun updateSearchIndexes(user: FirebaseUser) {
        val phone = user.phoneNumber
        if (!phone.isNullOrBlank()) {
            phones.document(phone).set(mapOf("uid" to user.uid)).await()
        }
        val email = user.email?.trim()?.lowercase()
        if (!email.isNullOrBlank() && user.isEmailVerified && isValidDocId(email)) {
            emails.document(email).set(mapOf("uid" to user.uid)).await()
        }
    }

    /** Поиск по нику. Возвращает uid найденного человека или null. */
    suspend fun findUidByNickname(nickname: String): String? =
        nicknames.document(nickname).get().await().getString("uid")

    /** Поиск по номеру телефона в виде +79161234567. */
    suspend fun findUidByPhone(phone: String): String? =
        phones.document(phone).get().await().getString("uid")

    /** Поиск по адресу почты аккаунта Google. */
    suspend fun findUidByEmail(email: String): String? {
        val key = email.trim().lowercase()
        if (!isValidDocId(key)) return null
        return emails.document(key).get().await().getString("uid")
    }

    /** Имя документа в Firestore не может быть пустым и содержать «/». */
    private fun isValidDocId(id: String): Boolean = id.isNotEmpty() && !id.contains('/')

    private fun DocumentSnapshot.toUserProfile(): UserProfile? {
        if (!exists()) return null
        return UserProfile(
            uid = id,
            name = getString("name").orEmpty(),
            nickname = getString("nickname").orEmpty(),
            avatar = getBlob("avatar")?.toBytes(),
        )
    }
}
