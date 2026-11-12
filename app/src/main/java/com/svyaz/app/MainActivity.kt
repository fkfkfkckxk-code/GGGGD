package com.svyaz.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.svyaz.app.ui.AppRoot

/**
 * Единственное «окно» приложения. Все экраны (вход, чаты, звонок)
 * рисуются внутри него — см. ui/AppRoot.kt.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        // Рисуем интерфейс на весь экран, включая область под системными панелями
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent { AppRoot() }
    }

    override fun onStart() {
        super.onStart()
        // Приложение на экране — можно принимать входящие звонки
        appContainer.callManager.setAppVisible(true)
    }

    override fun onStop() {
        super.onStop()
        appContainer.callManager.setAppVisible(false)
    }
}
