package com.greenslocks.iptv

import android.content.Context
import android.os.Build
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Keeps the last few crashes in a file, so the exact error can be read on the TV or pulled with adb. */
object CrashLog {
    private const val MAX = 24_000

    fun format(t: Throwable, version: String, device: String, time: Long): String {
        val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(time))
        val trace = t.stackTraceToString().take(6_000)
        return "=== $stamp  ·  v$version  ·  $device ===\n$trace\n\n"
    }

    /** Drops the oldest crashes once the file grows past [max] characters. */
    fun trim(text: String, max: Int = MAX): String {
        if (text.length <= max) return text
        val cut = text.indexOf("=== ", text.length - max)
        return if (cut >= 0) text.substring(cut) else text.takeLast(max)
    }

    /** The newest crash report in the file, or null. */
    fun latest(text: String): String? {
        val i = text.lastIndexOf("=== ")
        return if (i >= 0) text.substring(i).trim() else null
    }

    fun file(ctx: Context): File = File(ctx.getExternalFilesDir(null) ?: ctx.filesDir, "crash.log")

    fun read(ctx: Context): String? = runCatching { file(ctx).takeIf { it.exists() }?.readText()?.takeIf { it.isNotBlank() } }.getOrNull()

    fun clear(ctx: Context) { runCatching { file(ctx).delete() } }

    fun install(ctx: Context) {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        val app = ctx.applicationContext
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching {
                val f = file(app)
                val device = "${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE}"
                val old = if (f.exists()) f.readText() else ""
                f.writeText(trim(old + format(error, BuildConfig.VERSION_NAME, device, System.currentTimeMillis())))
            }
            previous?.uncaughtException(thread, error)
        }
    }
}
