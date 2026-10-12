package com.greenslocks.iptv

import okhttp3.ConnectionPool
import okhttp3.Dns
import okhttp3.OkHttpClient
import java.net.Inet6Address
import java.net.InetAddress
import java.util.concurrent.TimeUnit

/** Shared network settings: one HTTP client for streaming, and the name the app announces itself with. */
object Net {
    val UA_NAMES = listOf("Vance TV", "VLC", "TiviMate", "Chrome (Android TV)")
    private val UA_VALUES = listOf(
        "VanceTV/${BuildConfig.VERSION_NAME} (Android)",
        "VLC/3.0.20 LibVLC/3.0.20",
        "TiviMate/5.1.6 (Android)",
        "Mozilla/5.0 (Linux; Android 11; SHIELD Android TV) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
    )

    @Volatile var userAgent: String = UA_VALUES[0]
        private set

    fun setMode(index: Int) { userAgent = UA_VALUES[index.coerceIn(0, UA_VALUES.lastIndex)] }

    /**
     * Streaming client: reuses connections, follows http/https redirects, retries dropped connections,
     * and tries IPv4 addresses first (many IPTV hosts have broken IPv6).
     */
    val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .followRedirects(true)
            .followSslRedirects(true)
            .connectionPool(ConnectionPool(5, 2, TimeUnit.MINUTES))
            .dns(object : Dns {
                override fun lookup(hostname: String): List<InetAddress> =
                    Dns.SYSTEM.lookup(hostname).sortedBy { it is Inet6Address }
            })
            .build()
    }
}
