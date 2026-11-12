package com.svyaz.app.data

import android.util.Log
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import com.svyaz.app.util.UiMessages
import com.svyaz.app.util.toUserMessage
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/*
 * Помощники для «живых» данных Firestore.
 * Поток (Flow) присылает новое значение каждый раз, когда данные в базе меняются.
 * Если сервер отказал (например, не опубликованы правила доступа), приложение
 * не падает: ошибка показывается пользователю, а поток просто завершается.
 */

private const val TAG = "Firestore"

/** Живое содержимое одного документа. */
fun DocumentReference.liveSnapshots(
    metadataChanges: MetadataChanges = MetadataChanges.EXCLUDE,
): Flow<DocumentSnapshot> = callbackFlow {
    val registration = addSnapshotListener(metadataChanges) { snapshot, error ->
        if (error != null) {
            Log.w(TAG, "Ошибка чтения документа $path", error)
            UiMessages.show(error.toUserMessage())
            close()
        } else if (snapshot != null) {
            trySend(snapshot)
        }
    }
    awaitClose { registration.remove() }
}

/** Живой результат запроса (списка документов). */
fun Query.liveSnapshots(
    metadataChanges: MetadataChanges = MetadataChanges.EXCLUDE,
): Flow<QuerySnapshot> = callbackFlow {
    val registration = addSnapshotListener(metadataChanges) { snapshot, error ->
        if (error != null) {
            Log.w(TAG, "Ошибка запроса", error)
            UiMessages.show(error.toUserMessage())
            close()
        } else if (snapshot != null) {
            trySend(snapshot)
        }
    }
    awaitClose { registration.remove() }
}
