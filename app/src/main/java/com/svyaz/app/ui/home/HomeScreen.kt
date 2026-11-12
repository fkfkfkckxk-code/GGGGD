package com.svyaz.app.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.svyaz.app.appContainer
import com.svyaz.app.data.Chat
import com.svyaz.app.data.Friendship
import com.svyaz.app.ui.common.Avatar
import com.svyaz.app.ui.common.EmptyState
import com.svyaz.app.ui.common.rememberUser
import com.svyaz.app.util.formatChatListTime

/** Главный экран с тремя вкладками: «Чаты», «Друзья», «Профиль». */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    myUid: String,
    onOpenChat: (peerUid: String) -> Unit,
    onAddFriend: () -> Unit,
    onEditProfile: () -> Unit,
    onSignOut: () -> Unit,
) {
    val viewModel: HomeViewModel = viewModel(key = "home-$myUid") { HomeViewModel(myUid) }
    val chats by viewModel.chats.collectAsStateWithLifecycle()
    val friendships by viewModel.friendships.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }

    val unreadChats = chats.count { it.unreadFor(myUid) > 0 }
    val incomingRequests = friendships.count { !it.isAccepted && it.requestedBy != myUid }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        when (tab) {
                            0 -> "Чаты"
                            1 -> "Друзья"
                            else -> "Профиль"
                        },
                    )
                },
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = tab == 0,
                    onClick = { tab = 0 },
                    icon = {
                        TabIcon(badge = unreadChats) {
                            Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = null)
                        }
                    },
                    label = { Text("Чаты") },
                )
                NavigationBarItem(
                    selected = tab == 1,
                    onClick = { tab = 1 },
                    icon = {
                        TabIcon(badge = incomingRequests) {
                            Icon(Icons.Filled.People, contentDescription = null)
                        }
                    },
                    label = { Text("Друзья") },
                )
                NavigationBarItem(
                    selected = tab == 2,
                    onClick = { tab = 2 },
                    icon = { Icon(Icons.Filled.Person, contentDescription = null) },
                    label = { Text("Профиль") },
                )
            }
        },
        floatingActionButton = {
            if (tab == 1) {
                ExtendedFloatingActionButton(
                    onClick = onAddFriend,
                    icon = { Icon(Icons.Filled.PersonAdd, contentDescription = null) },
                    text = { Text("Добавить друга") },
                )
            }
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when (tab) {
                0 -> ChatsTab(myUid = myUid, chats = chats, onOpenChat = onOpenChat)
                1 -> FriendsTab(
                    myUid = myUid,
                    friendships = friendships,
                    onOpenChat = onOpenChat,
                    onAccept = viewModel::acceptRequest,
                    onRemove = viewModel::removeFriendship,
                )

                else -> ProfileTab(myUid = myUid, onEditProfile = onEditProfile, onSignOut = onSignOut)
            }
        }
    }
}

/** Значок вкладки с красным кружком-счётчиком. */
@Composable
private fun TabIcon(badge: Int, icon: @Composable () -> Unit) {
    BadgedBox(
        badge = {
            if (badge > 0) Badge { Text(if (badge > 99) "99+" else badge.toString()) }
        },
    ) {
        icon()
    }
}

// ---------------------------------------------------------------------------
// Вкладка «Чаты»
// ---------------------------------------------------------------------------

@Composable
private fun ChatsTab(myUid: String, chats: List<Chat>, onOpenChat: (String) -> Unit) {
    if (chats.isEmpty()) {
        EmptyState(
            title = "Пока нет чатов",
            hint = "Откройте вкладку «Друзья», выберите друга и напишите ему.",
        )
        return
    }
    LazyColumn(Modifier.fillMaxSize()) {
        items(chats, key = { it.id }) { chat ->
            ChatRow(myUid = myUid, chat = chat, onClick = { onOpenChat(chat.otherUid(myUid)) })
        }
    }
}

@Composable
private fun ChatRow(myUid: String, chat: Chat, onClick: () -> Unit) {
    val peer = rememberUser(chat.otherUid(myUid))
    val unread = chat.unreadFor(myUid)
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        leadingContent = { Avatar(peer, size = 52.dp) },
        headlineContent = {
            Text(
                text = peer?.name ?: "…",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontWeight = if (unread > 0) FontWeight.SemiBold else FontWeight.Normal,
            )
        },
        supportingContent = {
            Text(
                text = (if (chat.lastSenderId == myUid) "Вы: " else "") + chat.lastText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        trailingContent = {
            Column(horizontalAlignment = Alignment.End) {
                chat.updatedAt?.let {
                    Text(formatChatListTime(it), style = MaterialTheme.typography.labelSmall)
                }
                if (unread > 0) {
                    Spacer(Modifier.height(4.dp))
                    Badge(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ) {
                        Text(if (unread > 99) "99+" else unread.toString())
                    }
                }
            }
        },
    )
}

// ---------------------------------------------------------------------------
// Вкладка «Друзья»
// ---------------------------------------------------------------------------

@Composable
private fun FriendsTab(
    myUid: String,
    friendships: List<Friendship>,
    onOpenChat: (String) -> Unit,
    onAccept: (Friendship) -> Unit,
    onRemove: (Friendship) -> Unit,
) {
    val incoming = friendships.filter { !it.isAccepted && it.requestedBy != myUid }
    val outgoing = friendships.filter { !it.isAccepted && it.requestedBy == myUid }
    val friends = friendships.filter { it.isAccepted }

    /** Друг, удаление которого нужно подтвердить. */
    var friendToRemove by remember { mutableStateOf<Friendship?>(null) }

    if (friendships.isEmpty()) {
        EmptyState(
            title = "Здесь будут ваши друзья",
            hint = "Нажмите «Добавить друга» и найдите человека по нику, номеру телефона " +
                "или почте Google.",
        )
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        // Отступ снизу, чтобы кнопка «Добавить друга» не закрывала последнюю строку
        contentPadding = PaddingValues(bottom = 88.dp),
    ) {
        if (incoming.isNotEmpty()) {
            item(key = "incoming-title") { SectionTitle("Заявки в друзья") }
            items(incoming, key = { it.id }) { request ->
                PersonRow(
                    uid = request.otherUid(myUid),
                    trailing = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { onRemove(request) }) {
                                Icon(Icons.Filled.Close, contentDescription = "Отклонить")
                            }
                            FilledTonalIconButton(onClick = { onAccept(request) }) {
                                Icon(Icons.Filled.Check, contentDescription = "Принять")
                            }
                        }
                    },
                )
            }
        }

        if (friends.isNotEmpty()) {
            item(key = "friends-title") { SectionTitle("Друзья") }
            items(friends, key = { it.id }) { friend ->
                var menuOpen by remember { mutableStateOf(false) }
                PersonRow(
                    uid = friend.otherUid(myUid),
                    onClick = { onOpenChat(friend.otherUid(myUid)) },
                    trailing = {
                        Box {
                            IconButton(onClick = { menuOpen = true }) {
                                Icon(Icons.Filled.MoreVert, contentDescription = "Ещё")
                            }
                            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                DropdownMenuItem(
                                    text = { Text("Удалить из друзей") },
                                    onClick = {
                                        menuOpen = false
                                        friendToRemove = friend
                                    },
                                )
                            }
                        }
                    },
                )
            }
        }

        if (outgoing.isNotEmpty()) {
            item(key = "outgoing-title") { SectionTitle("Вы отправили заявку") }
            items(outgoing, key = { it.id }) { request ->
                PersonRow(
                    uid = request.otherUid(myUid),
                    trailing = {
                        TextButton(onClick = { onRemove(request) }) { Text("Отменить") }
                    },
                )
            }
        }
    }

    friendToRemove?.let { friend ->
        AlertDialog(
            onDismissRequest = { friendToRemove = null },
            title = { Text("Удалить из друзей?") },
            text = { Text("Вы не сможете писать и звонить друг другу, пока снова не добавитесь в друзья.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onRemove(friend)
                        friendToRemove = null
                    },
                ) { Text("Удалить") }
            },
            dismissButton = {
                TextButton(onClick = { friendToRemove = null }) { Text("Отмена") }
            },
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
    )
}

/** Строка с человеком: фото, имя, ник и кнопки справа. */
@Composable
private fun PersonRow(
    uid: String,
    trailing: @Composable () -> Unit,
    onClick: (() -> Unit)? = null,
) {
    val user = rememberUser(uid)
    ListItem(
        modifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier,
        leadingContent = { Avatar(user, size = 48.dp) },
        headlineContent = {
            Text(user?.name ?: "…", maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        supportingContent = {
            Text(user?.nickname?.let { "@$it" } ?: "", maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        trailingContent = trailing,
    )
}

// ---------------------------------------------------------------------------
// Вкладка «Профиль»
// ---------------------------------------------------------------------------

@Composable
private fun ProfileTab(myUid: String, onEditProfile: () -> Unit, onSignOut: () -> Unit) {
    val me = rememberUser(myUid)
    val account = appContainer.auth.currentUser

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top,
    ) {
        Avatar(me, size = 112.dp)
        Spacer(Modifier.height(16.dp))
        Text(me?.name ?: "…", style = MaterialTheme.typography.headlineSmall)
        Text(
            text = me?.nickname?.let { "@$it" } ?: "",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(16.dp))

        // Как меня могут найти друзья, кроме ника
        account?.phoneNumber?.takeIf { it.isNotBlank() }?.let {
            Text("Телефон: $it", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        account?.email?.takeIf { it.isNotBlank() }?.let {
            Text("Google: $it", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(24.dp))

        Column(modifier = Modifier.widthIn(max = 420.dp)) {
            Button(onClick = onEditProfile, modifier = Modifier.fillMaxWidth()) {
                Text("Редактировать профиль")
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onSignOut, modifier = Modifier.fillMaxWidth()) {
                Text("Выйти из аккаунта")
            }
        }
    }
}
