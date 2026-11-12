package com.svyaz.app.ui.profile

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.svyaz.app.appContainer
import com.svyaz.app.media.ImageTools
import com.svyaz.app.util.cleanNickname
import com.svyaz.app.util.isValidNickname
import com.svyaz.app.util.toUserMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ProfileEditState(
    val name: String = "",
    val nickname: String = "",
    val avatar: ByteArray? = null,
    /** Пользователь выбрал новое фото — его нужно сохранить. */
    val avatarChanged: Boolean = false,
    val isSaving: Boolean = false,
    val error: String? = null,
    val saved: Boolean = false,
)

/** Логика экрана создания и редактирования профиля. */
class ProfileEditViewModel(
    private val uid: String,
    isFirstSetup: Boolean,
) : ViewModel() {

    private val users = appContainer.users

    private val _state = MutableStateFlow(ProfileEditState())
    val state: StateFlow<ProfileEditState> = _state.asStateFlow()

    init {
        if (isFirstSetup) {
            // Подставляем имя из аккаунта Google, если оно есть
            val googleName = appContainer.auth.currentUser?.displayName.orEmpty()
            _state.update { it.copy(name = googleName.take(MAX_NAME_LENGTH)) }
        } else {
            viewModelScope.launch {
                val profile = runCatching { users.getUser(uid) }.getOrNull() ?: return@launch
                _state.update {
                    it.copy(name = profile.name, nickname = profile.nickname, avatar = profile.avatar)
                }
            }
        }
    }

    fun onNameChange(value: String) {
        _state.update { it.copy(name = value.take(MAX_NAME_LENGTH), error = null) }
    }

    fun onNicknameChange(value: String) {
        _state.update { it.copy(nickname = cleanNickname(value), error = null) }
    }

    fun onPhotoPicked(context: Context, uri: Uri) {
        viewModelScope.launch {
            val avatar = withContext(Dispatchers.Default) { ImageTools.prepareAvatar(context, uri) }
            if (avatar == null) {
                _state.update { it.copy(error = "Не удалось открыть это фото. Выберите другое.") }
            } else {
                _state.update { it.copy(avatar = avatar, avatarChanged = true, error = null) }
            }
        }
    }

    fun save() {
        val current = _state.value
        val name = current.name.trim()
        when {
            name.isEmpty() -> {
                _state.update { it.copy(error = "Введите имя") }
                return
            }

            !isValidNickname(current.nickname) -> {
                _state.update {
                    it.copy(error = "Ник: латинские буквы, цифры и «_», от 3 до 20 символов")
                }
                return
            }
        }

        _state.update { it.copy(isSaving = true, error = null) }
        viewModelScope.launch {
            try {
                users.saveProfile(
                    uid = uid,
                    name = name,
                    nickname = current.nickname,
                    newAvatar = if (current.avatarChanged) current.avatar else null,
                )
                _state.update { it.copy(isSaving = false, saved = true) }
            } catch (e: Exception) {
                _state.update { it.copy(isSaving = false, error = e.toUserMessage()) }
            }
        }
    }

    private companion object {
        const val MAX_NAME_LENGTH = 40
    }
}
