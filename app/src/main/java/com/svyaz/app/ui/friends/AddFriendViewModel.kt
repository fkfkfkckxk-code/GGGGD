package com.svyaz.app.ui.friends

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.svyaz.app.appContainer
import com.svyaz.app.data.Friendship
import com.svyaz.app.data.UserProfile
import com.svyaz.app.util.isValidNickname
import com.svyaz.app.util.normalizePhone
import com.svyaz.app.util.toUserMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Три способа найти человека. */
enum class SearchMode(val title: String) {
    NICKNAME("Ник"),
    PHONE("Телефон"),
    EMAIL("Google"),
}

data class AddFriendState(
    val mode: SearchMode = SearchMode.NICKNAME,
    val query: String = "",
    val isSearching: Boolean = false,
    /** Найденный человек. */
    val found: UserProfile? = null,
    /** Подсказка или ошибка под полем поиска. */
    val message: String? = null,
    /** Идёт отправка заявки. */
    val isSending: Boolean = false,
)

/** Логика экрана «Добавить друга». */
class AddFriendViewModel(private val myUid: String) : ViewModel() {

    private val users = appContainer.users
    private val friends = appContainer.friends

    private val _state = MutableStateFlow(AddFriendState())
    val state: StateFlow<AddFriendState> = _state.asStateFlow()

    /** Мои друзья и заявки — чтобы показать, в каких мы отношениях с найденным человеком. */
    val friendships: StateFlow<List<Friendship>> = friends.observeFriendships(myUid)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setMode(mode: SearchMode) {
        _state.update { AddFriendState(mode = mode) }
    }

    fun onQueryChange(value: String) {
        // В поле ника значок «@» уже нарисован — набранный вручную убираем
        val cleaned = if (_state.value.mode == SearchMode.NICKNAME) value.trimStart('@') else value
        _state.update { it.copy(query = cleaned.take(100), message = null) }
    }

    fun search() {
        val mode = _state.value.mode
        val query = _state.value.query.trim()
        if (query.isEmpty() || _state.value.isSearching) return

        _state.update { it.copy(isSearching = true, found = null, message = null) }
        viewModelScope.launch {
            try {
                val uid = when (mode) {
                    SearchMode.NICKNAME -> {
                        val nickname = query.removePrefix("@").lowercase()
                        if (!isValidNickname(nickname)) {
                            showMessage("Ник — это латинские буквы, цифры и «_», от 3 до 20 символов")
                            return@launch
                        }
                        users.findUidByNickname(nickname)
                    }

                    SearchMode.PHONE -> {
                        val phone = normalizePhone(query)
                        if (phone == null) {
                            showMessage("Введите номер полностью, например +7 916 123-45-67")
                            return@launch
                        }
                        users.findUidByPhone(phone)
                    }

                    SearchMode.EMAIL -> {
                        if (!query.contains('@')) {
                            showMessage("Введите адрес почты, например ivan@gmail.com")
                            return@launch
                        }
                        users.findUidByEmail(query)
                    }
                }
                val profile = uid?.let { users.getUser(it) }
                if (profile == null) {
                    showMessage("Никого не нашли. Проверьте, что друг уже зарегистрировался в приложении.")
                } else {
                    _state.update { it.copy(isSearching = false, found = profile) }
                }
            } catch (e: Exception) {
                showMessage(e.toUserMessage())
            }
        }
    }

    fun sendRequest() {
        val target = _state.value.found ?: return
        runFriendAction { friends.sendRequest(myUid, target.uid) }
    }

    fun acceptRequest(friendship: Friendship) {
        runFriendAction { friends.accept(friendship.id) }
    }

    private fun runFriendAction(action: suspend () -> Unit) {
        if (_state.value.isSending) return
        _state.update { it.copy(isSending = true, message = null) }
        viewModelScope.launch {
            try {
                action()
                _state.update { it.copy(isSending = false) }
            } catch (e: Exception) {
                _state.update { it.copy(isSending = false, message = e.toUserMessage()) }
            }
        }
    }

    private fun showMessage(text: String) {
        _state.update { it.copy(isSearching = false, message = text) }
    }
}
