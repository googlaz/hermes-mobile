package com.hermes.app

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class HermesApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Инициализация библиотек (при необходимости, например логгеры/Tailscale)
    }
}
