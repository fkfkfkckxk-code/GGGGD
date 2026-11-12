package com.svyaz.app.data

import android.content.Context
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/**
 * Вложения сообщений: фото и голосовые.
 *
 * Сами файлы лежат в базе Firestore (chats/{chatId}/media/{mediaId}), потому что
 * файловое хранилище Firebase Storage доступно только на платном тарифе.
 * Один документ Firestore вмещает до 1 МБ, поэтому фото и голосовые сжимаются.
 * Скачанный файл сохраняется на телефоне и второй раз из сети не загружается.
 */
class MediaRepository(context: Context) {

    private val db = FirebaseFirestore.getInstance()
    private val dir = File(context.cacheDir, "media")

    private fun cachedFile(mediaId: String): File {
        dir.mkdirs()
        return File(dir, mediaId)
    }

    /** Кладёт только что созданный файл в память телефона, чтобы отправитель видел его сразу. */
    fun saveToCache(mediaId: String, bytes: ByteArray) {
        writeSafely(cachedFile(mediaId), bytes)
    }

    /** Возвращает файл вложения: из памяти телефона, а если его там нет — скачивает из базы. */
    suspend fun getFile(chatId: String, mediaId: String): File = withContext(Dispatchers.IO) {
        val file = cachedFile(mediaId)
        if (file.length() > 0) return@withContext file

        val snapshot = db.collection(Collections.CHATS).document(chatId)
            .collection(Collections.MEDIA).document(mediaId)
            .get().await()
        val bytes = snapshot.getBlob("bytes")?.toBytes() ?: throw IOException("Файл не найден")
        writeSafely(file, bytes)
        file
    }

    /** Пишет через временный файл, чтобы не оставить «половинку» при сбое. */
    private fun writeSafely(target: File, bytes: ByteArray) {
        val temp = File(target.parentFile, target.name + ".tmp")
        temp.writeBytes(bytes)
        if (!temp.renameTo(target)) {
            target.writeBytes(bytes)
            temp.delete()
        }
    }

    companion object {
        /** Наибольший размер вложения: с запасом меньше предела Firestore в 1 МБ. */
        const val MAX_BYTES = 900_000
    }
}
