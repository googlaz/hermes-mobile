package com.hermes.app.data.remote

import android.content.Context
import com.hermes.app.data.local.SecurePreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import java.net.InetAddress
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TailscaleManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val securePreferences: SecurePreferences
) {
    /**
     * Возвращает true, если текущий введенный адрес является корректным Tailscale-адресом.
     * Стандартная подсеть Tailscale (CGNAT) всегда начинается с "100." (например, 100.115.92.14).
     * Также поддерживаются доменные имена MagicDNS (заканчивющиеся на .ts.net).
     */
    fun isTailscaleAddressValid(address: String?): Boolean {
        if (address.isNullOrBlank()) return false
        val trimmed = address.trim()
        
        // Маска 100.64.0.0/10 для Tailscale IP
        if (trimmed.startsWith("100.")) {
            val parts = trimmed.split(".")
            if (parts.size == 4) {
                val secondOctet = parts[1].toIntOrNull()
                if (secondOctet != null && secondOctet in 64..127) {
                    return true
                }
            }
        }
        
        // Либо MagicDNS домен
        if (trimmed.endsWith(".ts.net", ignoreCase = true)) {
            return true
        }

        // Локальный лупбэк (для эмулятора, Pitfall 6)
        if (trimmed == "10.0.2.2" || trimmed == "localhost" || trimmed == "127.0.0.1") {
            return true
        }

        return false
    }

    /**
     * Осуществляет резолв хоста (включая MagicDNS с пингом) в фоновом режиме.
     */
    suspend fun resolveHost(host: String): InetAddress? {
        return try {
            // JVM DNS резолвер автоматически разруливает MagicDNS, если Tailscale VPN включен в Android системе
            InetAddress.getByName(host)
        } catch (e: Exception) {
            null
        }
    }
}
