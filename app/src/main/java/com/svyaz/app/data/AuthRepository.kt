package com.svyaz.app.data

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import com.svyaz.app.util.GoogleNotConfiguredException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.util.concurrent.TimeUnit

/**
 * Вход в приложение: через аккаунт Google и по номеру телефона с кодом из SMS.
 * Всю проверку паролей и кодов выполняет Firebase Authentication.
 */
class AuthRepository(private val appContext: Context) {

    private val auth: FirebaseAuth = FirebaseAuth.getInstance()

    val currentUser: FirebaseUser?
        get() = auth.currentUser

    /** Поток «кто сейчас вошёл в приложение». null — никто не вошёл. */
    val authState: Flow<FirebaseUser?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { trySend(it.currentUser) }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }

    /** Показывает окно выбора аккаунта Google и входит с выбранным аккаунтом. */
    suspend fun signInWithGoogle(activity: Activity) {
        val webClientId = findWebClientId() ?: throw GoogleNotConfiguredException()

        val option = GetSignInWithGoogleOption.Builder(webClientId).build()
        val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
        val result = CredentialManager.create(activity).getCredential(activity, request)

        val credential = result.credential
        if (credential is CustomCredential &&
            credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        ) {
            // Google выдал «пропуск» (токен) — предъявляем его Firebase
            val idToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
            auth.signInWithCredential(GoogleAuthProvider.getCredential(idToken, null)).await()
        } else {
            throw IllegalStateException("Google вернул неожиданный ответ. Попробуйте ещё раз.")
        }
    }

    /**
     * Просит Firebase отправить SMS с кодом на номер [phone] (в виде +79161234567).
     * О результате Firebase сообщит через [callbacks].
     * [resendToken] нужен только для повторной отправки кода.
     */
    fun requestSmsCode(
        activity: Activity,
        phone: String,
        resendToken: PhoneAuthProvider.ForceResendingToken?,
        callbacks: PhoneAuthProvider.OnVerificationStateChangedCallbacks,
    ) {
        auth.setLanguageCode("ru") // текст SMS будет на русском
        val options = PhoneAuthOptions.newBuilder(auth)
            .setPhoneNumber(phone)
            .setTimeout(60L, TimeUnit.SECONDS)
            .setActivity(activity)
            .setCallbacks(callbacks)
        if (resendToken != null) options.setForceResendingToken(resendToken)
        PhoneAuthProvider.verifyPhoneNumber(options.build())
    }

    /** Входит по коду из SMS. [verificationId] Firebase выдаёт при отправке кода. */
    suspend fun signInWithSmsCode(verificationId: String, code: String) {
        signInWithPhoneCredential(PhoneAuthProvider.getCredential(verificationId, code))
    }

    /** Входит, когда телефон сам подтвердил номер (код вводить не пришлось). */
    suspend fun signInWithPhoneCredential(credential: PhoneAuthCredential) {
        auth.signInWithCredential(credential).await()
    }

    suspend fun signOut() {
        auth.signOut()
        // Чтобы при следующем входе Google снова предложил выбрать аккаунт
        runCatching {
            CredentialManager.create(appContext).clearCredentialState(ClearCredentialStateRequest())
        }
    }

    /**
     * Идентификатор для входа через Google. Его создаёт плагин google-services из файла
     * google-services.json, но только если в консоли Firebase включён вход через Google.
     * Ищем по имени, чтобы без него проект всё равно собирался.
     */
    @SuppressLint("DiscouragedApi")
    private fun findWebClientId(): String? {
        val id = appContext.resources.getIdentifier(
            "default_web_client_id", "string", appContext.packageName,
        )
        return if (id == 0) null else appContext.getString(id)
    }
}
