package com.svyaz.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.svyaz.app.appContainer
import com.svyaz.app.call.CallPhase
import com.svyaz.app.ui.auth.LoginScreen
import com.svyaz.app.ui.call.CallScreen
import com.svyaz.app.ui.chat.ChatScreen
import com.svyaz.app.ui.friends.AddFriendScreen
import com.svyaz.app.ui.home.HomeScreen
import com.svyaz.app.ui.profile.ProfileEditScreen
import com.svyaz.app.ui.theme.SvyazTheme
import com.svyaz.app.util.UiMessages
import kotlinx.coroutines.delay

/**
 * Корень интерфейса: решает, какой экран показать (вход, создание профиля или чаты),
 * а поверх рисует экран звонка и всплывающие сообщения.
 */
@Composable
fun AppRoot() {
    val sessionViewModel: SessionViewModel = viewModel()
    val session by sessionViewModel.state.collectAsStateWithLifecycle()
    val call by appContainer.callManager.state.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(Unit) {
        UiMessages.messages.collect { snackbarHostState.showSnackbar(it) }
    }

    SvyazTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Box(Modifier.fillMaxSize()) {
                when (val current = session) {
                    SessionState.Loading -> LoadingScreen(
                        onRetry = sessionViewModel::retry,
                        onSignOut = sessionViewModel::signOut,
                    )

                    SessionState.SignedOut -> LoginScreen()

                    is SessionState.NeedsProfile -> ProfileEditScreen(
                        uid = current.uid,
                        isFirstSetup = true,
                        onDone = {},
                        onSignOut = sessionViewModel::signOut,
                    )

                    // key: у другого пользователя — своя, заново созданная навигация
                    is SessionState.Ready -> key(current.uid) {
                        MainNavigation(myUid = current.uid, onSignOut = sessionViewModel::signOut)
                    }
                }

                if (call.phase != CallPhase.IDLE) {
                    CallScreen(call)
                }

                SnackbarHost(
                    hostState = snackbarHostState,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .imePadding()
                        .padding(bottom = 80.dp),
                )
            }
        }
    }
}

/** Переходы между экранами для вошедшего пользователя. */
@Composable
private fun MainNavigation(myUid: String, onSignOut: () -> Unit) {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = "home") {
        composable("home") {
            HomeScreen(
                myUid = myUid,
                onOpenChat = { peerUid -> navController.navigate("chat/$peerUid") },
                onAddFriend = { navController.navigate("add_friend") },
                onEditProfile = { navController.navigate("edit_profile") },
                onSignOut = onSignOut,
            )
        }
        composable("chat/{peerUid}") { entry ->
            val peerUid = entry.arguments?.getString("peerUid") ?: return@composable
            ChatScreen(myUid = myUid, peerUid = peerUid, onBack = { navController.popBackStack() })
        }
        composable("add_friend") {
            AddFriendScreen(myUid = myUid, onBack = { navController.popBackStack() })
        }
        composable("edit_profile") {
            ProfileEditScreen(
                uid = myUid,
                isFirstSetup = false,
                onDone = { navController.popBackStack() },
            )
        }
    }
}

/** Экран ожидания. Если ждать приходится долго — предлагает повторить или выйти. */
@Composable
private fun LoadingScreen(onRetry: () -> Unit, onSignOut: () -> Unit) {
    var isSlow by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(6_000)
        isSlow = true
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator()
        if (isSlow) {
            Spacer(Modifier.height(24.dp))
            Text(
                text = "Не получается связаться с сервером.\nПроверьте подключение к интернету.",
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
            Button(onClick = onRetry) { Text("Повторить") }
            TextButton(onClick = onSignOut) { Text("Выйти из аккаунта") }
        }
    }
}
