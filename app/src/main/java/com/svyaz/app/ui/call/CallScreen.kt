package com.svyaz.app.ui.call

import android.Manifest
import android.os.Build
import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.svyaz.app.appContainer
import com.svyaz.app.call.CallPhase
import com.svyaz.app.call.CallUiState
import com.svyaz.app.ui.common.Avatar
import com.svyaz.app.ui.common.rememberUser
import com.svyaz.app.ui.theme.CallGreen
import com.svyaz.app.ui.theme.CallRed
import com.svyaz.app.util.UiMessages
import com.svyaz.app.util.formatDuration
import kotlinx.coroutines.delay

/**
 * Экран звонка. Показывается поверх остальных экранов,
 * пока идёт вызов или разговор.
 */
@Composable
fun CallScreen(call: CallUiState) {
    val manager = appContainer.callManager
    val peer = call.peerUid?.let { rememberUser(it) }

    // Кнопка «Назад» не должна случайно закрывать звонок
    BackHandler(enabled = true) {}

    val acceptPermissions = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        if (result[Manifest.permission.RECORD_AUDIO] == true) {
            manager.accept()
        } else {
            UiMessages.show("Чтобы ответить, разрешите доступ к микрофону")
        }
    }
    val accept = {
        val permissions = mutableListOf(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions += Manifest.permission.POST_NOTIFICATIONS
        }
        acceptPermissions.launch(permissions.toTypedArray())
    }

    // Счётчик длительности разговора: обновляем дважды в секунду
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(call.connectedAt) {
        while (call.connectedAt != null) {
            now = SystemClock.elapsedRealtime()
            delay(500)
        }
    }

    val statusText = when (call.phase) {
        CallPhase.OUTGOING -> "Вызов…"
        CallPhase.INCOMING -> "Входящий звонок"
        CallPhase.CONNECTING -> "Соединение…"
        CallPhase.ACTIVE -> formatDuration(now - (call.connectedAt ?: now))
        CallPhase.ENDED -> call.endMessage ?: "Звонок завершён"
        CallPhase.IDLE -> ""
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.weight(1f))
            Avatar(peer, size = 128.dp)
            Spacer(Modifier.height(24.dp))
            Text(
                text = peer?.name ?: "…",
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = statusText,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.weight(1.5f))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.Top,
            ) {
                when (call.phase) {
                    CallPhase.INCOMING -> {
                        CallButton(
                            icon = Icons.Filled.CallEnd,
                            label = "Отклонить",
                            containerColor = CallRed,
                            contentColor = Color.White,
                            onClick = manager::decline,
                        )
                        CallButton(
                            icon = Icons.Filled.Call,
                            label = "Принять",
                            containerColor = CallGreen,
                            contentColor = Color.White,
                            onClick = accept,
                        )
                    }

                    CallPhase.OUTGOING, CallPhase.CONNECTING, CallPhase.ACTIVE -> {
                        ToggleCallButton(
                            icon = if (call.isMuted) Icons.Filled.MicOff else Icons.Filled.Mic,
                            label = if (call.isMuted) "Микрофон выключен" else "Микрофон",
                            isOn = call.isMuted,
                            onClick = manager::toggleMute,
                        )
                        ToggleCallButton(
                            icon = Icons.AutoMirrored.Filled.VolumeUp,
                            label = "Громкая связь",
                            isOn = call.isSpeakerOn,
                            onClick = manager::toggleSpeaker,
                        )
                        CallButton(
                            icon = Icons.Filled.CallEnd,
                            label = "Завершить",
                            containerColor = CallRed,
                            contentColor = Color.White,
                            onClick = manager::hangUp,
                        )
                    }

                    else -> Unit
                }
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}

/** Кнопка-переключатель (микрофон, громкая связь): включённая подсвечивается. */
@Composable
private fun ToggleCallButton(icon: ImageVector, label: String, isOn: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    CallButton(
        icon = icon,
        label = label,
        containerColor = if (isOn) colors.primary else colors.surfaceContainerHighest,
        contentColor = if (isOn) colors.onPrimary else colors.onSurface,
        onClick = onClick,
    )
}

/** Большая круглая кнопка с подписью. */
@Composable
private fun CallButton(
    icon: ImageVector,
    label: String,
    containerColor: Color,
    contentColor: Color,
    onClick: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        FilledIconButton(
            onClick = onClick,
            modifier = Modifier.size(68.dp),
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = containerColor,
                contentColor = contentColor,
            ),
        ) {
            Icon(icon, contentDescription = label, modifier = Modifier.size(30.dp))
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
