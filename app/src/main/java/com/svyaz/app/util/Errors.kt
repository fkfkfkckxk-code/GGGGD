package com.svyaz.app.util

import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

/** Ник уже занят другим пользователем. */
class NicknameTakenException : Exception("Этот ник уже занят")

/** В проекте Firebase не включён вход через Google (или не обновлён google-services.json). */
class GoogleNotConfiguredException : Exception("Вход через Google не настроен")

/**
 * Короткие сообщения пользователю (всплывают внизу экрана).
 * Вызвать можно из любого места: UiMessages.show("текст").
 */
object UiMessages {
    private val _messages = MutableSharedFlow<String>(
        extraBufferCapacity = 5,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val messages: SharedFlow<String> = _messages

    private var lastText: String? = null
    private var lastTime = 0L

    fun show(text: String) {
        // Одинаковые сообщения подряд не повторяем: несколько экранов
        // могут сообщить об одной и той же проблеме одновременно.
        val now = System.currentTimeMillis()
        if (text == lastText && now - lastTime < 5_000) return
        lastText = text
        lastTime = now
        _messages.tryEmit(text)
    }
}

/** Превращает техническую ошибку в понятный текст на русском. */
fun Throwable.toUserMessage(): String {
    val raw = message.orEmpty()
    return when {
        this is NicknameTakenException ->
            "Этот ник уже занят. Придумайте другой."

        this is GoogleNotConfiguredException ->
            "Вход через Google не настроен: включите способ входа Google в консоли Firebase " +
                "и заново скачайте файл google-services.json (README, шаг 5)."

        this is FirebaseNetworkException ->
            "Нет связи с сервером. Проверьте интернет."

        raw.contains("BILLING_NOT_ENABLED", ignoreCase = true) ->
            "SMS не отправляются: в проекте Firebase не подключена оплата. " +
                "Для проверки используйте тестовый номер (README, шаг 5)."

        raw.contains("region", ignoreCase = true) && raw.contains("SMS", ignoreCase = true) ->
            "Отправка SMS в эту страну выключена в настройках Firebase (README, шаг 5)."

        this is FirebaseTooManyRequestsException ->
            "Слишком много попыток. Подождите немного и попробуйте снова."

        this is FirebaseAuthInvalidCredentialsException ->
            "Неверный номер телефона или код из SMS."

        this is NoCredentialException ->
            "На телефоне не найден аккаунт Google. Добавьте его в настройках телефона."

        this is GetCredentialException ->
            if (raw.contains("28444") || raw.contains("Developer console", ignoreCase = true)) {
                "Вход через Google не настроен: добавьте отпечаток SHA-1 в консоли Firebase " +
                    "(README, шаг 4)."
            } else {
                "Не удалось войти через Google. Попробуйте ещё раз."
            }

        this is FirebaseFirestoreException -> when (code) {
            FirebaseFirestoreException.Code.PERMISSION_DENIED ->
                if (FirebaseAuth.getInstance().currentUser == null) {
                    "Вы вышли из аккаунта."
                } else {
                    "Сервер отклонил действие. Если это происходит постоянно — проверьте, " +
                        "что правила из файла firestore.rules опубликованы (README, шаг 6)."
                }

            FirebaseFirestoreException.Code.UNAVAILABLE ->
                "Нет связи с сервером. Проверьте интернет."

            FirebaseFirestoreException.Code.NOT_FOUND ->
                "База данных не найдена. Создайте базу Firestore в консоли Firebase (README, шаг 6)."

            else -> "Ошибка базы данных: ${code.name}"
        }

        raw.isNotBlank() -> raw
        else -> "Что-то пошло не так. Попробуйте ещё раз."
    }
}
