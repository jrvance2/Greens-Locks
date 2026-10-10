package com.greenslocks.iptv

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URL

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme(colorScheme = darkColorScheme()) { Surface { App() } } }
    }
}

private suspend fun loadPlaylist(url: String): List<Channel> = withContext(Dispatchers.IO) {
    val conn = URL(url).openConnection().apply { connectTimeout = 15000; readTimeout = 30000 }
    M3uParser.parse(conn.getInputStream().bufferedReader().use { it.readText() })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun App() {
    val ctx = LocalContext.current
    val prefs = remember { ctx.getSharedPreferences("iptv", Context.MODE_PRIVATE) }
    val scope = rememberCoroutineScope()

    var playlistUrl by remember { mutableStateOf(prefs.getString("url", "") ?: "") }
    var channels by remember { mutableStateOf(emptyList<Channel>()) }
    var status by remember { mutableStateOf("Enter the URL of your M3U playlist") }
    var query by remember { mutableStateOf("") }
    var group by remember { mutableStateOf<String?>(null) }
    var current by remember { mutableStateOf<Channel?>(null) }

    val player = remember { ExoPlayer.Builder(ctx).build() }
    DisposableEffect(Unit) { onDispose { player.release() } }
    LaunchedEffect(current) {
        current?.let {
            player.setMediaItem(MediaItem.fromUri(it.url))
            player.prepare()
            player.playWhenReady = true
        }
    }

    fun load() {
        prefs.edit().putString("url", playlistUrl).apply()
        status = "Loading…"
        scope.launch {
            runCatching { loadPlaylist(playlistUrl.trim()) }
                .onSuccess { channels = it; status = "${it.size} channels" }
                .onFailure { status = "Failed: ${it.message}" }
        }
    }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        if (current != null) {
            AndroidView(
                factory = { PlayerView(it).apply { this.player = player } },
                modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f),
            )
            Text(current!!.name, Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                style = MaterialTheme.typography.titleMedium)
        }
        Row(Modifier.padding(8.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            OutlinedTextField(
                value = playlistUrl, onValueChange = { playlistUrl = it },
                label = { Text("M3U playlist URL") }, singleLine = true,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            Button(onClick = ::load) { Text("Load") }
        }
        Text(status, Modifier.padding(horizontal = 12.dp), style = MaterialTheme.typography.bodySmall)
        if (channels.isNotEmpty()) {
            OutlinedTextField(
                value = query, onValueChange = { query = it }, label = { Text("Search") },
                singleLine = true, modifier = Modifier.fillMaxWidth().padding(8.dp),
            )
            val groups = remember(channels) { channels.map { it.group }.distinct().sorted() }
            LazyRow(Modifier.padding(horizontal = 8.dp)) {
                item { FilterChip(group == null, { group = null }, { Text("All") }) }
                items(groups) { g ->
                    Spacer(Modifier.width(6.dp))
                    FilterChip(group == g, { group = g }, { Text(g) })
                }
            }
            val shown = channels.filter {
                (group == null || it.group == group) && it.name.contains(query, ignoreCase = true)
            }
            LazyColumn(Modifier.fillMaxSize()) {
                items(shown) { ch ->
                    ListItem(
                        headlineContent = { Text(ch.name) },
                        supportingContent = { Text(ch.group) },
                        modifier = Modifier.clickable { current = ch },
                    )
                }
            }
        }
    }
}
