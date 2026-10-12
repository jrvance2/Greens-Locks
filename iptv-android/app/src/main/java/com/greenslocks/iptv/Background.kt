package com.greenslocks.iptv

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/** Refreshes the channel lists and the extra guide while the app is closed, so the next launch is instant and current. */
class LibraryRefreshWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val ctx = applicationContext
        val store = Store(ctx.getSharedPreferences("iptv", Context.MODE_PRIVATE))
        val url = store.url()
        if (url.isBlank()) return Result.success()
        Net.setMode(store.int("uaMode", 0))
        val ttl = LIB_TTL_MS[store.int("libCache", 2).coerceIn(0, LIB_TTL_MS.lastIndex)]
        val x = xtreamFromUrl(url)
        if (x != null && ttl > 0) {
            runCatching { CachedProvider(x, libCacheDir(ctx, url)) { ttl }.refreshLists() }
        }
        val xml = store.string("xmltvUrl").orEmpty()
        if (xml.isNotBlank()) {
            runCatching {
                XmltvStore(ctx, store.active).refresh(xml)
                store.setString("xmltvAt", System.currentTimeMillis().toString())
            }
        }
        return Result.success()
    }
}

object BackgroundRefresh {
    private const val NAME = "library-refresh"

    fun apply(ctx: Context, on: Boolean) {
        val wm = WorkManager.getInstance(ctx)
        if (!on) { wm.cancelUniqueWork(NAME); return }
        val req = PeriodicWorkRequestBuilder<LibraryRefreshWorker>(6, TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        wm.enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, req)
    }
}
