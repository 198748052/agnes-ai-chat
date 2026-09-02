package com.agnesai.chat

import android.app.Application
import com.agnesai.chat.di.AppContainer

class AgnesChatApplication : Application() {

    lateinit var appContainer: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        appContainer = AppContainer(this)
    }

    override fun onTerminate() {
        super.onTerminate()
        appContainer.onCleared()
    }
}
