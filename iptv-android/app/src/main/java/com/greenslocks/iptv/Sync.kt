package com.greenslocks.iptv

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.ServerSocket
import java.net.Socket
import java.net.URL
import kotlin.concurrent.thread

/** Serves this device's settings backup to another Vance TV on the home network, guarded by a code. */
class SyncServer(private val code: String, private val payload: () -> String) {
    @Volatile private var socket: ServerSocket? = null

    fun start(): Int {
        val s = (8776..8786).firstNotNullOfOrNull { port -> runCatching { ServerSocket(port) }.getOrNull() } ?: return -1
        socket = s
        thread(isDaemon = true, name = "sync") {
            while (!s.isClosed) {
                val client = runCatching { s.accept() }.getOrNull() ?: break
                runCatching { handle(client) }
            }
        }
        return s.localPort
    }

    fun stop() { runCatching { socket?.close() } }

    private fun handle(client: Socket) = client.use { c ->
        c.soTimeout = 5000
        val request = c.getInputStream().bufferedReader().readLine().orEmpty()
        val ok = request.startsWith("GET /backup") && request.contains("code=$code")
        val body = (if (ok) payload() else "forbidden").toByteArray()
        c.getOutputStream().apply {
            write("HTTP/1.1 ${if (ok) "200 OK" else "403 Forbidden"}\r\nContent-Type: application/json; charset=utf-8\r\nContent-Length: ${body.size}\r\nConnection: close\r\n\r\n".toByteArray())
            write(body)
            flush()
        }
    }
}

suspend fun fetchSync(address: String, code: String): String? = withContext(Dispatchers.IO) {
    runCatching {
        URL("http://$address/backup?code=$code").openConnection()
            .apply { connectTimeout = 5000; readTimeout = 10000 }
            .getInputStream().bufferedReader().use { it.readText() }
    }.getOrNull()
}
