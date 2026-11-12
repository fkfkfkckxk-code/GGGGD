package com.svyaz.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.svyaz.app.appContainer
import com.svyaz.app.data.Chat
import com.svyaz.app.data.Friendship
import com.svyaz.app.util.UiMessages
import com.svyaz.app.util.toUserMessage
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Date

/** Данные главного экрана: список чатов, друзья и заявки. */
class HomeViewModel(private val myUid: String) : ViewModel() {

    private val chatRepository = appContainer.chats
    private val friendsRepository = appContainer.friends

    /** Для каких чатов уже отправлена отметка «доставлено»: чат → время последнего сообщения. */
    private val deliveredMarks = mutableMapOf<String, Date?>()

    val chats: StateFlow<List<Chat>> = chatRepository.observeChats(myUid)
        .onEach(::markDelivered)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val friendships: StateFlow<List<Friendship>> = friendsRepository.observeFriendships(myUid)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /**
     * Как только телефон узнал о новом сообщении собеседника, сообщаем серверу,
     * что оно доставлено — у отправителя появится вторая галочка.
     */
    private fun markDelivered(chats: List<Chat>) {
        for (chat in chats) {
            if (chat.lastSenderId == myUid || chat.unreadFor(myUid) == 0L) continue
            if (deliveredMarks.containsKey(chat.id) && deliveredMarks[chat.id] == chat.updatedAt) continue
            deliveredMarks[chat.id] = chat.updatedAt
            viewModelScope.launch {
                runCatching { chatRepository.markDelivered(chat.id, myUid) }
            }
        }
    }

    fun acceptRequest(friendship: Friendship) = runAction { friendsRepository.accept(friendship.id) }

    /** Отклонить заявку, отозвать свою или удалить из друзей. */
    fun removeFriendship(friendship: Friendship) = runAction { friendsRepository.remove(friendship.id) }

    private fun runAction(action: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                action()
            } catch (e: Exception) {
                UiMessages.show(e.toUserMessage())
            }
        }
    }
}
