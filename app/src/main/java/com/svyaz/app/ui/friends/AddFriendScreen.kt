package com.svyaz.app.ui.friends

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.svyaz.app.data.Friendship
import com.svyaz.app.data.UserProfile
import com.svyaz.app.data.pairId
import com.svyaz.app.ui.common.Avatar

/** Экран поиска человека: по нику, по номеру телефона или по почте аккаунта Google. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddFriendScreen(myUid: String, onBack: () -> Unit) {
    val viewModel: AddFriendViewModel = viewModel(key = "add-friend-$myUid") {
        AddFriendViewModel(myUid)
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val friendships by viewModel.friendships.collectAsStateWithLifecycle()
    val keyboard = LocalSoftwareKeyboardController.current

    val search = {
        keyboard?.hide()
        viewModel.search()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Добавить друга") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .consumeWindowInsets(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            Text("Как искать", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(8.dp))
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                SearchMode.entries.forEachIndexed { index, mode ->
                    SegmentedButton(
                        selected = state.mode == mode,
                        onClick = { viewModel.setMode(mode) },
                        shape = SegmentedButtonDefaults.itemShape(index, SearchMode.entries.size),
                    ) {
                        Text(mode.title)
                    }
                }
            }
            Spacer(Modifier.height(16.dp))

            OutlinedTextField(
                value = state.query,
                onValueChange = viewModel::onQueryChange,
                label = {
                    Text(
                        when (state.mode) {
                            SearchMode.NICKNAME -> "Ник друга"
                            SearchMode.PHONE -> "Номер телефона друга"
                            SearchMode.EMAIL -> "Почта аккаунта Google"
                        },
                    )
                },
                placeholder = {
                    Text(
                        when (state.mode) {
                            SearchMode.NICKNAME -> "ivan_petrov"
                            SearchMode.PHONE -> "+7 916 123-45-67"
                            SearchMode.EMAIL -> "ivan@gmail.com"
                        },
                    )
                },
                prefix = { if (state.mode == SearchMode.NICKNAME) Text("@") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = when (state.mode) {
                        SearchMode.NICKNAME -> KeyboardType.Ascii
                        SearchMode.PHONE -> KeyboardType.Phone
                        SearchMode.EMAIL -> KeyboardType.Email
                    },
                    autoCorrectEnabled = false,
                    imeAction = ImeAction.Search,
                ),
                keyboardActions = KeyboardActions(onSearch = { search() }),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = search,
                enabled = !state.isSearching && state.query.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (state.isSearching) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Text("Найти")
                }
            }

            if (state.message != null) {
                Text(
                    text = state.message.orEmpty(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 16.dp),
                )
            }

            state.found?.let { person ->
                Spacer(Modifier.height(20.dp))
                FoundPersonCard(
                    myUid = myUid,
                    person = person,
                    // Запись о дружбе с этим человеком, если она уже есть
                    friendship = friendships.firstOrNull { it.id == pairId(myUid, person.uid) },
                    isSending = state.isSending,
                    onSendRequest = viewModel::sendRequest,
                    onAccept = viewModel::acceptRequest,
                )
            }
        }
    }
}

/** Карточка найденного человека с кнопкой, которая зависит от ваших отношений. */
@Composable
private fun FoundPersonCard(
    myUid: String,
    person: UserProfile,
    friendship: Friendship?,
    isSending: Boolean,
    onSendRequest: () -> Unit,
    onAccept: (Friendship) -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Avatar(person, size = 56.dp)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        text = person.name,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "@${person.nickname}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))

            when {
                person.uid == myUid ->
                    Text("Это вы 🙂", color = MaterialTheme.colorScheme.onSurfaceVariant)

                friendship == null ->
                    Button(onClick = onSendRequest, enabled = !isSending, modifier = Modifier.fillMaxWidth()) {
                        Text("Добавить в друзья")
                    }

                friendship.isAccepted ->
                    Text("Вы уже друзья", color = MaterialTheme.colorScheme.primary)

                friendship.requestedBy == myUid ->
                    Text(
                        "Заявка отправлена. Ждём, когда её примут.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                else ->
                    Button(
                        onClick = { onAccept(friendship) },
                        enabled = !isSending,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Принять заявку в друзья")
                    }
            }
        }
    }
}
