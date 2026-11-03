package com.wordlens.app

import android.app.Application

class WordLensApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }

    override fun onTerminate() {
        super.onTerminate()
        runCatching { container.tts.shutdown() }
    }
}
