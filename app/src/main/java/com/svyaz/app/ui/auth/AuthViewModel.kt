package com.svyaz.app.ui.auth

import android.app.Activity
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.FirebaseException
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthProvider
import com.svyaz.app.appContainer
import com.svyaz.app.util.normalizePhone
import com.svyaz.app.util.toUserMessage
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Шаги экрана входа. */
enum class AuthStep {
    /** Выбор способа входа. */
    CHOOSE,

    /** Ввод номера телефона. */
    PHONE,

    /** Ввод кода из SMS. */
    CODE,
}

data class AuthUiState(
    val step: AuthStep = AuthStep.CHOOSE,
    val phone: String = "+7",
    val code: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
    /** Через сколько секунд можно запросить код ещё раз. */
    val resendInSec: Int = 0,
)

/** Логика экрана входа. */
class AuthViewModel : ViewModel() {

    private val auth = appContainer.auth

    private val _state = MutableStateFlow(AuthUiState())
    val state: StateFlow<AuthUiState> = _state.asStateFlow()

    private var verificationId: String? = null
    private var resendToken: PhoneAuthProvider.ForceResendingToken? = null
    private var sentToPhone: String? = null
    private var timerJob: Job? = null

    /** Ответы Firebase на просьбу отправить SMS. */
    private val smsCallbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {

        override fun onCodeSent(id: String, token: PhoneAuthProvider.ForceResendingToken) {
            verificationId = id
            resendToken = token
            _state.update { it.copy(step = AuthStep.CODE, code = "", isLoading = false, error = null) }
            startResendTimer()
        }

        override fun onVerificationCompleted(credential: PhoneAuthCredential) {
            // Телефон сам прочитал SMS и подтвердил номер — код вводить не нужно
            signIn { auth.signInWithPhoneCredential(credential) }
        }

        override fun onVerificationFailed(error: FirebaseException) {
            _state.update { it.copy(isLoading = false, error = error.toUserMessage()) }
        }
    }

    fun signInWithGoogle(activity: Activity) {
        if (_state.value.isLoading) return
        signIn {
            try {
                auth.signInWithGoogle(activity)
            } catch (e: GetCredentialCancellationException) {
                // Пользователь сам закрыл окно выбора аккаунта — это не ошибка
            }
        }
    }

    fun showPhoneStep() {
        _state.update { it.copy(step = AuthStep.PHONE, error = null) }
    }

    /** Шаг назад: с ввода кода — к номеру, с номера — к выбору способа входа. */
    fun back() {
        _state.update {
            when (it.step) {
                AuthStep.CODE -> it.copy(step = AuthStep.PHONE, code = "", error = null, isLoading = false)
                else -> it.copy(step = AuthStep.CHOOSE, error = null, isLoading = false)
            }
        }
    }

    fun onPhoneChange(value: String) {
        val cleaned = value.filter { it in '0'..'9' || it in "+ -()" }.take(20)
        _state.update { it.copy(phone = cleaned, error = null) }
    }

    fun onCodeChange(value: String) {
        val cleaned = value.filter { it in '0'..'9' }.take(6)
        _state.update { it.copy(code = cleaned, error = null) }
    }

    /** Отправить SMS с кодом (или отправить повторно). */
    fun sendCode(activity: Activity) {
        val phone = normalizePhone(_state.value.phone)
        if (phone == null) {
            _state.update { it.copy(error = "Введите номер полностью, например +7 916 123-45-67") }
            return
        }
        _state.update { it.copy(isLoading = true, error = null) }
        // Повторная отправка на тот же номер требует «квитанцию» от прошлой отправки
        val token = if (phone == sentToPhone) resendToken else null
        sentToPhone = phone
        auth.requestSmsCode(activity, phone, token, smsCallbacks)
    }

    fun confirmCode() {
        val id = verificationId ?: return
        val code = _state.value.code
        if (code.length < 6) {
            _state.update { it.copy(error = "Введите 6 цифр из SMS") }
            return
        }
        signIn { auth.signInWithSmsCode(id, code) }
    }

    private fun signIn(block: suspend () -> Unit) {
        _state.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            try {
                block()
                if (auth.currentUser != null) {
                    // Вход выполнен: возвращаем экран в исходный вид на случай следующего входа
                    timerJob?.cancel()
                    _state.value = AuthUiState()
                    return@launch
                }
            } catch (e: Exception) {
                _state.update { it.copy(error = e.toUserMessage()) }
            }
            _state.update { it.copy(isLoading = false) }
        }
    }

    private fun startResendTimer() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            for (seconds in 60 downTo 0) {
                _state.update { it.copy(resendInSec = seconds) }
                delay(1_000)
            }
        }
    }
}
