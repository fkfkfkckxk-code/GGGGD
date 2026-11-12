package com.svyaz.app.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.svyaz.app.appContainer
import com.svyaz.app.data.ProfileState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** В каком состоянии приложение: от этого зависит, какой экран показать. */
sealed interface SessionState {
    /** Ещё выясняем, вошёл ли пользователь и есть ли у него профиль. */
    data object Loading : SessionState

    /** Никто не вошёл — показываем экран входа. */
    data object SignedOut : SessionState

    /** Вошёл впервые — нужно заполнить профиль. */
    data class NeedsProfile(val uid: String) : SessionState

    /** Всё готово — показываем чаты. */
    data class Ready(val uid: String) : SessionState
}

/** Следит за входом в аккаунт и наличием профиля. */
class SessionViewModel : ViewModel() {

    private val container = appContainer

    /** Меняем это число, чтобы заново подключиться к серверу по кнопке «Повторить». */
    private val retryCounter = MutableStateFlow(0)

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<SessionState> =
        combine(container.auth.authState, retryCounter) { user, _ -> user }
            .flatMapLatest { user ->
                if (user == null) {
                    flowOf(SessionState.SignedOut)
                } else {
                    container.users.observeProfileState(user.uid)
                        .map { profile ->
                            when (profile) {
                                ProfileState.Loading -> SessionState.Loading
                                ProfileState.Missing -> SessionState.NeedsProfile(user.uid)
                                is ProfileState.Exists -> SessionState.Ready(user.uid)
                            }
                        }
                        .onStart { emit(SessionState.Loading) }
                }
            }
            .distinctUntilChanged()
            .stateIn(viewModelScope, SharingStarted.Eagerly, SessionState.Loading)

    init {
        viewModelScope.launch {
            state.collect { session ->
                when (session) {
                    is SessionState.Ready -> onSignedIn(session.uid)
                    SessionState.SignedOut -> onSignedOut()
                    else -> Unit
                }
            }
        }
    }

    private fun onSignedIn(uid: String) {
        container.callManager.setUser(uid)
        // Обновляем записи для поиска, чтобы друзья могли найти меня по телефону и почте
        val user = container.auth.currentUser ?: return
        viewModelScope.launch {
            runCatching { container.users.updateSearchIndexes(user) }
                .onFailure { Log.w("Session", "Не удалось обновить записи для поиска", it) }
        }
    }

    private fun onSignedOut() {
        container.callManager.setUser(null)
        container.voicePlayer.stop()
        container.users.clearCache()
    }

    fun retry() {
        retryCounter.value += 1
    }

    fun signOut() {
        viewModelScope.launch { container.auth.signOut() }
    }
}
