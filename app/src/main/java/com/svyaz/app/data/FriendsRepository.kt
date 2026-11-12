package com.svyaz.app.data

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

/**
 * Друзья и заявки в друзья.
 *
 * Для каждой пары людей в базе есть не больше одной записи friendships/{pairId}:
 *  - статус "pending"  — заявка отправлена и ждёт ответа;
 *  - статус "accepted" — заявку приняли, вы друзья;
 *  - записи нет        — вы не связаны (заявку отклонили, отозвали или дружбу удалили).
 */
class FriendsRepository {

    private val friendships = FirebaseFirestore.getInstance().collection(Collections.FRIENDSHIPS)

    /** Все мои друзья и заявки (входящие и исходящие) — обновляются сами. */
    fun observeFriendships(myUid: String): Flow<List<Friendship>> =
        friendships.whereArrayContains("members", myUid).liveSnapshots().map { snapshot ->
            snapshot.documents.mapNotNull { doc ->
                @Suppress("UNCHECKED_CAST")
                val members = doc.get("members") as? List<String> ?: return@mapNotNull null
                Friendship(
                    id = doc.id,
                    members = members,
                    requestedBy = doc.getString("requestedBy").orEmpty(),
                    status = doc.getString("status").orEmpty(),
                )
            }
        }

    /** Отправить заявку в друзья. */
    suspend fun sendRequest(myUid: String, otherUid: String) {
        val data = mapOf(
            "members" to listOf(myUid, otherUid).sorted(),
            "requestedBy" to myUid,
            "status" to Friendship.STATUS_PENDING,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp(),
        )
        friendships.document(pairId(myUid, otherUid)).set(data).await()
    }

    /** Принять входящую заявку. */
    suspend fun accept(friendshipId: String) {
        friendships.document(friendshipId).update(
            mapOf(
                "status" to Friendship.STATUS_ACCEPTED,
                "updatedAt" to FieldValue.serverTimestamp(),
            ),
        ).await()
    }

    /** Отклонить заявку, отозвать свою заявку или удалить человека из друзей. */
    suspend fun remove(friendshipId: String) {
        friendships.document(friendshipId).delete().await()
    }
}
