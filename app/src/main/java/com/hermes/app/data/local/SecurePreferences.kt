package com.hermes.app.data.local

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SecurePreferences @Inject constructor(
    @ApplicationContext context: Context
) {
    private val sharedPreferences: SharedPreferences

    init {
        // Используем стандартные SharedPreferences для максимальной стабильности на всех девайсах.
        // EncryptedSharedPreferences печально известны падениями KeyStore на кастомных ROM (Xiaomi, Realme, custom OS)
        sharedPreferences = context.getSharedPreferences("secure_prefs", Context.MODE_PRIVATE)
    }

    companion object {
        private const val KEY_API_SERVER_KEY = "api_server_key"
        private const val KEY_TAILSCALE_HOST = "tailscale_host"
        private const val KEY_SERVER_PORT = "server_port"
    }

    var apiServerKey: String?
        get() = sharedPreferences.getString(KEY_API_SERVER_KEY, null)
        set(value) {
            sharedPreferences.edit().putString(KEY_API_SERVER_KEY, value).apply()
        }

    var tailscaleHost: String?
        get() = sharedPreferences.getString(KEY_TAILSCALE_HOST, "100.100.100.100")
        set(value) {
            sharedPreferences.edit().putString(KEY_TAILSCALE_HOST, value).apply()
        }

    var serverPort: Int
        get() = sharedPreferences.getInt(KEY_SERVER_PORT, 8642)
        set(value) {
            sharedPreferences.edit().putInt(KEY_SERVER_PORT, value).apply()
        }

    fun clear() {
        sharedPreferences.edit().clear().apply()
    }
}
