package com.svyaz.app

import android.content.Context
import com.svyaz.app.call.CallManager
import com.svyaz.app.data.AuthRepository
import com.svyaz.app.data.CallRepository
import com.svyaz.app.data.ChatRepository
import com.svyaz.app.data.FriendsRepository
import com.svyaz.app.data.MediaRepository
import com.svyaz.app.data.UserRepository
import com.svyaz.app.media.VoicePlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * «Ящик с инструментами» приложения: здесь в одном экземпляре создаются объекты,
 * которые работают с сервером, звуком и звонками. Экраны берут их отсюда.
 */
class AppContainer(context: Context) {

    /** Область для фоновых задач, которые живут, пока запущено приложение. */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val auth = AuthRepository(context)
    val users = UserRepository(appScope)
    val friends = FriendsRepository()
    val media = MediaRepository(context)
    val chats = ChatRepository(media)
    val voicePlayer = VoicePlayer(appScope)
    val callManager = CallManager(context, CallRepository(), appScope)
}
