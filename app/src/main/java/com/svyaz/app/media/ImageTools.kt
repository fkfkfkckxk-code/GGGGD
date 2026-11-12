package com.svyaz.app.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.core.graphics.scale
import androidx.exifinterface.media.ExifInterface
import com.svyaz.app.data.MediaRepository
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.min

/** Готовое к отправке фото: сжатые данные и размеры картинки. */
class PreparedImage(val jpeg: ByteArray, val width: Int, val height: Int)

/** Подготовка картинок: уменьшение и сжатие перед отправкой на сервер. */
object ImageTools {

    private const val CHAT_PHOTO_MAX_SIDE = 1280
    private const val AVATAR_SIDE = 256

    /** Фото для чата: длинная сторона не больше 1280 точек, размер файла в пределах лимита. */
    fun prepareChatPhoto(context: Context, uri: Uri): PreparedImage? {
        val bitmap = decodeScaled(context, uri, CHAT_PHOTO_MAX_SIDE) ?: return null
        // Понижаем качество, пока файл не уложится в лимит
        var quality = 85
        var bytes = bitmap.toJpeg(quality)
        while (bytes.size > MediaRepository.MAX_BYTES && quality > 40) {
            quality -= 15
            bytes = bitmap.toJpeg(quality)
        }
        if (bytes.size > MediaRepository.MAX_BYTES) return null
        return PreparedImage(bytes, bitmap.width, bitmap.height)
    }

    /** Фото профиля: квадрат 256×256, вырезанный из середины картинки. */
    fun prepareAvatar(context: Context, uri: Uri): ByteArray? {
        val bitmap = decodeScaled(context, uri, AVATAR_SIDE * 2) ?: return null
        val side = min(bitmap.width, bitmap.height)
        val square = Bitmap.createBitmap(
            bitmap, (bitmap.width - side) / 2, (bitmap.height - side) / 2, side, side,
        )
        return square.scale(AVATAR_SIDE, AVATAR_SIDE).toJpeg(85)
    }

    private fun Bitmap.toJpeg(quality: Int): ByteArray {
        val out = ByteArrayOutputStream()
        compress(Bitmap.CompressFormat.JPEG, quality, out)
        return out.toByteArray()
    }

    /** Читает картинку, сразу уменьшая её, чтобы большое фото не заняло всю память. */
    private fun decodeScaled(context: Context, uri: Uri, maxSide: Int): Bitmap? = try {
        val resolver = context.contentResolver

        // 1. Узнаём размеры, не загружая саму картинку
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        val largest = max(bounds.outWidth, bounds.outHeight)
        if (largest <= 0) {
            null
        } else {
            // 2. Загружаем уменьшенной в 2, 4, 8… раз
            var sample = 1
            while (largest / (sample * 2) >= maxSide) sample *= 2
            val options = BitmapFactory.Options().apply { inSampleSize = sample }
            val bitmap = resolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, options)
            }

            // 3. Доводим до нужного размера и поворачиваем, как было снято камерой
            bitmap?.let {
                val scale = min(1f, maxSide.toFloat() / max(it.width, it.height))
                val matrix = Matrix().apply {
                    postScale(scale, scale)
                    postRotate(readRotation(context, uri))
                }
                Bitmap.createBitmap(it, 0, 0, it.width, it.height, matrix, true)
            }
        }
    } catch (e: Exception) {
        null
    } catch (e: OutOfMemoryError) {
        null
    }

    /** Камера часто хранит фото «лёжа» и отдельно записывает, на сколько градусов его повернуть. */
    private fun readRotation(context: Context, uri: Uri): Float {
        val degrees = context.contentResolver.openInputStream(uri)?.use {
            ExifInterface(it).rotationDegrees
        }
        return (degrees ?: 0).toFloat()
    }
}
