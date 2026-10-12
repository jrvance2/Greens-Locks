package com.greenslocks.iptv

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.thread

/** A recording to make: [url] is the live transport-stream address, [startMs] when it should begin. */
data class RecJob(val id: String, val url: String, val name: String, val startMs: Long, val durMs: Long)

class RecState(val name: String, val file: File) {
    @Volatile var stop = false
    @Volatile var bytes = 0L
}

object Recorder {
    val active = ConcurrentHashMap<String, RecState>()
    private const val JOBS = "rec_jobs"
    private const val SEP = "\u0002"

    fun dir(ctx: Context): File = File(ctx.getExternalFilesDir(null), "recordings").also { it.mkdirs() }

    fun files(ctx: Context): List<File> =
        dir(ctx).listFiles { f -> f.isFile && f.length() > 0 }.orEmpty().sortedByDescending { it.lastModified() }

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("iptv", Context.MODE_PRIVATE)

    fun jobs(ctx: Context): List<RecJob> =
        prefs(ctx).getString(JOBS, "").orEmpty().split(SEP).filter { it.isNotEmpty() }.mapNotNull { enc ->
            runCatching {
                val o = JSONObject(Crypto.decrypt(enc) ?: return@runCatching null)
                RecJob(o.getString("id"), o.getString("url"), o.getString("name"), o.getLong("start"), o.getLong("dur"))
            }.getOrNull()
        }

    private fun saveJobs(ctx: Context, list: List<RecJob>) {
        val enc = list.map {
            Crypto.encrypt(JSONObject().put("id", it.id).put("url", it.url).put("name", it.name).put("start", it.startMs).put("dur", it.durMs).toString())
        }
        prefs(ctx).edit().putString(JOBS, enc.joinToString(SEP)).apply()
    }

    private fun pending(ctx: Context, id: String): PendingIntent =
        PendingIntent.getBroadcast(
            ctx, id.hashCode(), Intent(ctx, RecordReceiver::class.java).putExtra("id", id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    fun schedule(ctx: Context, job: RecJob) {
        saveJobs(ctx, jobs(ctx).filter { it.id != job.id } + job)
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val exact = Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()
        val at = maxOf(job.startMs, System.currentTimeMillis() + 1000)
        if (exact) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending(ctx, job.id))
        else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending(ctx, job.id))
    }

    fun cancel(ctx: Context, id: String) {
        saveJobs(ctx, jobs(ctx).filter { it.id != id })
        (ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager).cancel(pending(ctx, id))
    }

    fun startNow(ctx: Context, job: RecJob) {
        ContextCompat.startForegroundService(
            ctx,
            Intent(ctx, RecordService::class.java).putExtra("id", job.id).putExtra("url", job.url)
                .putExtra("name", job.name).putExtra("dur", job.durMs),
        )
    }
}

class RecordReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val id = intent.getStringExtra("id") ?: return
        val job = Recorder.jobs(ctx).firstOrNull { it.id == id } ?: return
        Recorder.cancel(ctx, id)
        val remaining = job.durMs - (System.currentTimeMillis() - job.startMs).coerceAtLeast(0)
        if (remaining > 10_000) runCatching { Recorder.startNow(ctx, job.copy(durMs = remaining)) }
    }
}

class RecordService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val id = intent?.getStringExtra("id") ?: return START_NOT_STICKY
        val url = intent.getStringExtra("url") ?: return START_NOT_STICKY
        val name = intent.getStringExtra("name").orEmpty()
        val dur = intent.getLongExtra("dur", 3_600_000L)
        Net.setMode(getSharedPreferences("iptv", MODE_PRIVATE).getInt("uaMode", 0))
        goForeground(name)
        val stamp = SimpleDateFormat("MMdd_HHmm", Locale.US).format(Date())
        val safe = name.replace(Regex("[^A-Za-z0-9._-]+"), "_").take(60)
        val file = File(Recorder.dir(this), "${safe}_$stamp.ts")
        val state = RecState(name, file)
        Recorder.active[id] = state
        thread(name = "record-$id") {
            runCatching {
                val conn = (URL(url).openConnection() as HttpURLConnection).apply { setRequestProperty("User-Agent", Net.userAgent); connectTimeout = 15000; readTimeout = 30000 }
                val end = System.currentTimeMillis() + dur
                conn.inputStream.use { input ->
                    file.outputStream().use { out ->
                        val buf = ByteArray(64 * 1024)
                        while (!state.stop && System.currentTimeMillis() < end) {
                            val n = input.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n)
                            state.bytes += n
                        }
                    }
                }
            }
            if (state.bytes == 0L) file.delete()
            Recorder.active.remove(id)
            if (Recorder.active.isEmpty()) {
                stopForeground(true)
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun goForeground(name: String) {
        val chan = "recording"
        if (Build.VERSION.SDK_INT >= 26) {
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(NotificationChannel(chan, "Recording", NotificationManager.IMPORTANCE_LOW))
        }
        val n: Notification = NotificationCompat.Builder(this, chan)
            .setSmallIcon(android.R.drawable.presence_video_online)
            .setContentTitle("Recording")
            .setContentText(name)
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= 29) startForeground(7, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        else startForeground(7, n)
    }
}
