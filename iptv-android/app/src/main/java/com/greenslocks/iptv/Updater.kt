package com.greenslocks.iptv

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.URL

data class Release(val build: Int, val name: String, val url: String)

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val release: Release) : UpdateState
    data class Downloading(val percent: Int) : UpdateState
    data class Failed(val message: String) : UpdateState
}

/** Checks this project's GitHub releases for a newer build and installs it over the current app. */
object Updater {
    private const val REPO = "https://api.github.com/repos/jrvance2/Greens-Locks/releases"

    private fun get(url: String): String {
        val conn = URL(url).openConnection().apply {
            connectTimeout = 15000; readTimeout = 20000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "VanceTV-Updater")
        }
        return conn.getInputStream().bufferedReader().use { it.readText() }
    }

    private fun apkOf(json: JSONObject, build: Int): Release? {
        val assets = json.optJSONArray("assets") ?: return null
        for (i in 0 until assets.length()) {
            val a = assets.getJSONObject(i)
            if (a.optString("name").endsWith(".apk")) return Release(build, json.optString("name"), a.optString("browser_download_url"))
        }
        return null
    }

    /**
     * Stable app: the newest normal release ("build-N"). Beta app: the single moving "beta-latest" pre-release, whose
     * title ends in "build N" (GitHub's public release list hides pre-releases, but a tag lookup works). Stable users never see it.
     */
    suspend fun latest(): Release? = withContext(Dispatchers.IO) {
        if (!BuildConfig.BETA) {
            val json = JSONObject(get("$REPO/latest"))
            val build = json.optString("tag_name").removePrefix("build-").toIntOrNull() ?: return@withContext null
            return@withContext apkOf(json, build)
        }
        val json = JSONObject(get("$REPO/tags/beta-latest"))
        val build = Regex("build (\\d+)").find(json.optString("name"))?.groupValues?.get(1)?.toIntOrNull() ?: return@withContext null
        apkOf(json, build)
    }

    suspend fun download(ctx: Context, url: String, onProgress: (Int) -> Unit): File = withContext(Dispatchers.IO) {
        val dir = File(ctx.cacheDir, "updates").apply { mkdirs() }
        val out = File(dir, "VanceTV.apk")
        val conn = URL(url).openConnection().apply {
            connectTimeout = 15000; readTimeout = 30000
            setRequestProperty("User-Agent", "VanceTV-Updater")
        }
        val total = conn.contentLengthLong
        conn.getInputStream().use { input ->
            out.outputStream().use { o ->
                val buf = ByteArray(64 * 1024)
                var done = 0L
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    o.write(buf, 0, n)
                    done += n
                    if (total > 0) onProgress((done * 100 / total).toInt())
                }
            }
        }
        out
    }

    /** Opens the system installer. Returns false if the app first needs permission to install apps. */
    fun install(ctx: Context, apk: File): Boolean {
        if (Build.VERSION.SDK_INT >= 26 && !ctx.packageManager.canRequestPackageInstalls()) {
            ctx.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${ctx.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            return false
        }
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", apk)
        ctx.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        return true
    }
}
