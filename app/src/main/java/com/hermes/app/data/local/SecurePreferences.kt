package com.hermes.app.data.local

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SecurePreferences @Inject constructor(
    @ApplicationContext context: Context
) {
    private val sharedPreferences: SharedPreferences

    init {
        // Генерация или получение мастер-ключа шифрования в Android Keystore
        val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)

        // Инициализация шифрованных SharedPreferences для НФТ-5
        sharedPreferences = EncryptedSharedPreferences.create(
            "secure_prefs",
            masterKeyAlias,
            context,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
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
        get() = sharedPreferences.getString(KEY_TAILSCALE_HOST, "100.100.100.100") // дефолтный заглушечный IP внутри Tailscale сети
        set(value) {
            sharedPreferences.edit().putString(KEY_TAILSCALE_HOST, value).apply()
        }

    var serverPort: Int
        get() = sharedPreferences.getInt(KEY_SERVER_PORT, 8642) // Дефолтный порт бэкенда Hermes API Server
        set(value) {
            sharedPreferences.edit().putInt(KEY_SERVER_PORT, value).apply()
        }

    fun clear() {
        sharedPreferences.edit().clear().apply()
    }
}
