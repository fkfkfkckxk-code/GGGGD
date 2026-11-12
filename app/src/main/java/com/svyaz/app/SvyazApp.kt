package com.svyaz.app

import android.app.Application

/**
 * Класс приложения. Android создаёт его один раз при запуске,
 * раньше любых экранов. Здесь мы создаём общие объекты (см. AppContainer).
 */
class SvyazApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        container = AppContainer(this)
    }

    companion object {
        lateinit var instance: SvyazApp
            private set
    }
}

/** Короткий доступ к общим объектам из любого места приложения. */
val appContainer: AppContainer
    get() = SvyazApp.instance.container
