package com.greenslocks.iptv

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.tvprovider.media.tv.TvContractCompat
import androidx.tvprovider.media.tv.WatchNextProgram

/** Puts unfinished movies and episodes in the Android TV home screen's "Continue watching" row. */
object WatchNext {
    fun publish(ctx: Context) {
        runCatching {
            val store = Store(ctx.getSharedPreferences("iptv", Context.MODE_PRIVATE))
            val resolver = ctx.contentResolver
            resolver.delete(TvContractCompat.WatchNextPrograms.CONTENT_URI, null, null)
            store.list("hist").mapNotNull(::decodeEntry)
                .filter { it.kind == Kind.MOVIE || it.kind == Kind.EPISODE }
                .take(8)
                .forEach { e ->
                    val pos = store.long("pos_${e.kind}_${e.id}")
                    val dur = store.long("dur_${e.kind}_${e.id}")
                    if (pos <= 0 || dur <= 0 || pos > dur * 0.95) return@forEach
                    val title = if (e.kind == Kind.EPISODE && e.parent.isNotEmpty()) "${e.parent} · ${e.title}" else e.title
                    val b = WatchNextProgram.Builder()
                        .setType(TvContractCompat.WatchNextPrograms.TYPE_MOVIE)
                        .setWatchNextType(TvContractCompat.WatchNextPrograms.WATCH_NEXT_TYPE_CONTINUE)
                        .setLastEngagementTimeUtcMillis(System.currentTimeMillis())
                        .setTitle(title)
                        .setLastPlaybackPositionMillis(pos.toInt())
                        .setDurationMillis(dur.toInt())
                        .setInternalProviderId(e.key())
                        .setIntent(Intent(ctx, MainActivity::class.java).putExtra("play_entry", e.encode()))
                    if (e.icon.startsWith("http")) {
                        b.setPosterArtUri(Uri.parse(e.icon))
                        b.setPosterArtAspectRatio(TvContractCompat.PreviewPrograms.ASPECT_RATIO_2_3)
                    }
                    resolver.insert(TvContractCompat.WatchNextPrograms.CONTENT_URI, b.build().toContentValues())
                }
        }
    }
}
