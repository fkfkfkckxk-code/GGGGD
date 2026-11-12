package com.svyaz.app.ui.auth

import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

/** Экран входа: через Google или по номеру телефона с кодом из SMS. */
@Composable
fun LoginScreen(viewModel: AuthViewModel = viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val activity = LocalActivity.current

    // Системная кнопка «Назад» возвращает на предыдущий шаг входа
    BackHandler(enabled = state.step != AuthStep.CHOOSE) { viewModel.back() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            modifier = Modifier.widthIn(max = 420.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            when (state.step) {
                AuthStep.CHOOSE -> ChooseStep(
                    state = state,
                    onGoogle = { activity?.let(viewModel::signInWithGoogle) },
                    onPhone = viewModel::showPhoneStep,
                )

                AuthStep.PHONE -> PhoneStep(
                    state = state,
                    onPhoneChange = viewModel::onPhoneChange,
                    onSendCode = { activity?.let(viewModel::sendCode) },
                    onBack = viewModel::back,
                )

                AuthStep.CODE -> CodeStep(
                    state = state,
                    onCodeChange = viewModel::onCodeChange,
                    onConfirm = viewModel::confirmCode,
                    onResend = { activity?.let(viewModel::sendCode) },
                    onBack = viewModel::back,
                )
            }
        }
    }
}

@Composable
private fun ChooseStep(state: AuthUiState, onGoogle: () -> Unit, onPhone: () -> Unit) {
    Box(
        modifier = Modifier
            .size(96.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.Chat,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.size(44.dp),
        )
    }
    Spacer(Modifier.height(24.dp))
    Text("Связь", style = MaterialTheme.typography.headlineLarge)
    Text(
        text = "Сообщения и звонки для друзей",
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 4.dp),
    )
    Spacer(Modifier.height(40.dp))

    Button(onClick = onGoogle, enabled = !state.isLoading, modifier = Modifier.fillMaxWidth()) {
        if (state.isLoading) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        } else {
            Text("Войти через Google")
        }
    }
    Spacer(Modifier.height(12.dp))
    OutlinedButton(onClick = onPhone, enabled = !state.isLoading, modifier = Modifier.fillMaxWidth()) {
        Text("Войти по номеру телефона")
    }
    ErrorText(state.error)
}

@Composable
private fun PhoneStep(
    state: AuthUiState,
    onPhoneChange: (String) -> Unit,
    onSendCode: () -> Unit,
    onBack: () -> Unit,
) {
    Text("Вход по номеру", style = MaterialTheme.typography.headlineSmall)
    Text(
        text = "Мы отправим на этот номер SMS с кодом",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(top = 4.dp),
    )
    Spacer(Modifier.height(24.dp))
    OutlinedTextField(
        value = state.phone,
        onValueChange = onPhoneChange,
        label = { Text("Номер телефона") },
        placeholder = { Text("+7 916 123-45-67") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onSendCode() }),
        modifier = Modifier.fillMaxWidth(),
    )
    ErrorText(state.error)
    Spacer(Modifier.height(16.dp))
    Button(onClick = onSendCode, enabled = !state.isLoading, modifier = Modifier.fillMaxWidth()) {
        if (state.isLoading) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        } else {
            Text("Получить код")
        }
    }
    TextButton(onClick = onBack) { Text("Назад") }
}

@Composable
private fun CodeStep(
    state: AuthUiState,
    onCodeChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onResend: () -> Unit,
    onBack: () -> Unit,
) {
    Text("Код из SMS", style = MaterialTheme.typography.headlineSmall)
    Text(
        text = "Отправили код на номер ${state.phone}",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(top = 4.dp),
    )
    Spacer(Modifier.height(24.dp))
    OutlinedTextField(
        value = state.code,
        onValueChange = onCodeChange,
        label = { Text("6 цифр") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onConfirm() }),
        modifier = Modifier.fillMaxWidth(),
    )
    ErrorText(state.error)
    Spacer(Modifier.height(16.dp))
    Button(onClick = onConfirm, enabled = !state.isLoading, modifier = Modifier.fillMaxWidth()) {
        if (state.isLoading) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        } else {
            Text("Войти")
        }
    }
    TextButton(onClick = onResend, enabled = state.resendInSec == 0 && !state.isLoading) {
        Text(
            if (state.resendInSec > 0) {
                "Отправить ещё раз через ${state.resendInSec} с"
            } else {
                "Отправить код ещё раз"
            },
        )
    }
    TextButton(onClick = onBack) { Text("Изменить номер") }
}

@Composable
private fun ErrorText(error: String?) {
    if (error != null) {
        Text(
            text = error,
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 12.dp),
        )
    }
}
