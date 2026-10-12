package com.greenslocks.iptv

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import java.io.BufferedInputStream
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import kotlin.concurrent.thread

data class PairInfo(val url: String, val code: String)

/** The TV's address on the home network, e.g. 192.168.1.139. */
fun localIpAddress(): String? = runCatching {
    NetworkInterface.getNetworkInterfaces().toList()
        .filter { it.isUp && !it.isLoopback }
        .flatMap { it.inetAddresses.toList() }
        .filterIsInstance<Inet4Address>()
        .firstOrNull { it.isSiteLocalAddress }
        ?.hostAddress
}.getOrNull()

/**
 * Tiny local web page so you can type your login on a phone instead of a TV remote. It only runs
 * while the login screen is open and only accepts a form that includes the code shown on the TV.
 */
class PairingServer(private val code: String, private val onData: (Map<String, String>) -> Unit) {
    @Volatile private var socket: ServerSocket? = null

    /** Returns the port in use, or -1 if none could be opened. */
    fun start(): Int {
        val s = (8765..8775).firstNotNullOfOrNull { port -> runCatching { ServerSocket(port) }.getOrNull() } ?: return -1
        socket = s
        thread(isDaemon = true, name = "pairing") {
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
        val input = BufferedInputStream(c.getInputStream())
        fun readLine(): String? {
            val sb = StringBuilder()
            while (true) {
                val b = input.read()
                if (b < 0) return if (sb.isEmpty()) null else sb.toString()
                if (b == '\n'.code) return sb.toString().trimEnd('\r')
                sb.append(b.toChar())
            }
        }
        val request = readLine() ?: return@use
        var length = 0
        while (true) {
            val h = readLine() ?: break
            if (h.isEmpty()) break
            if (h.startsWith("content-length:", ignoreCase = true)) length = h.substringAfter(':').trim().toIntOrNull() ?: 0
        }
        var message = ""
        if (request.startsWith("POST") && length in 1..8192) {
            val body = ByteArray(length)
            var read = 0
            while (read < length) {
                val n = input.read(body, read, length - read)
                if (n < 0) break
                read += n
            }
            val form = String(body, 0, read).split('&').mapNotNull { kv ->
                val p = kv.split('=', limit = 2)
                if (p.size == 2) URLDecoder.decode(p[0], "UTF-8") to URLDecoder.decode(p[1], "UTF-8") else null
            }.toMap()
            message = if (form["code"]?.trim() == code) {
                onData(form)
                "<p class=ok>Sent! Look at your TV.</p>"
            } else {
                "<p class=bad>That code doesn't match the one on your TV.</p>"
            }
        }
        val html = page(message).toByteArray()
        c.getOutputStream().apply {
            write("HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=utf-8\r\nContent-Length: ${html.size}\r\nConnection: close\r\n\r\n".toByteArray())
            write(html)
            flush()
        }
    }

    private fun page(message: String) = """<!doctype html><html><head><meta name=viewport content="width=device-width,initial-scale=1">
<title>Vance TV</title><style>
body{font-family:system-ui,sans-serif;background:#080c16;color:#eef2ff;margin:0;padding:24px;max-width:480px}
h1{margin:0 0 4px}p.s{color:#8e9bb8;margin:0 0 20px}label{display:block;margin:14px 0 4px;color:#8e9bb8;font-size:14px}
input{width:100%;box-sizing:border-box;padding:14px;border-radius:10px;border:1px solid #2a3552;background:#111827;color:#fff;font-size:17px}
button{margin-top:22px;width:100%;padding:15px;border:0;border-radius:12px;background:#6c8cff;color:#fff;font-size:18px;font-weight:600}
.ok{background:#12351f;padding:12px;border-radius:10px}.bad{background:#3a1620;padding:12px;border-radius:10px}hr{border:0;border-top:1px solid #2a3552;margin:22px 0}
</style></head><body><h1>Vance TV</h1><p class=s>Send your login to the TV.</p>$message
<form method=post><label>Code shown on your TV</label><input name=code inputmode=numeric autocomplete=off required>
<label>Server (e.g. http://host:port)</label><input name=server autocapitalize=off>
<label>Username</label><input name=user autocapitalize=off autocomplete=off>
<label>Password</label><input name=pass type=password autocomplete=off>
<hr><label>…or paste a playlist URL instead</label><input name=url autocapitalize=off>
<button type=submit>Send to TV</button></form></body></html>"""
}

/** Draws [text] as a QR code. */
@Composable
fun QrCode(text: String, size: Dp, modifier: Modifier = Modifier) {
    val matrix = remember(text) {
        runCatching {
            QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 0, 0, mapOf(EncodeHintType.MARGIN to 1))
        }.getOrNull()
    }
    androidx.compose.foundation.Canvas(modifier.size(size)) {
        drawRect(Color.White)
        val m = matrix ?: return@Canvas
        val cell = this.size.width / m.width
        for (y in 0 until m.height) for (x in 0 until m.width) {
            if (m[x, y]) drawRect(Color.Black, Offset(x * cell, y * cell), Size(cell + 0.5f, cell + 0.5f))
        }
    }
}
