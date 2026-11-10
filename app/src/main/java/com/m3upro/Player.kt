package com.m3upro

import android.app.Activity
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay
import kotlin.math.max

@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(vm: MainVm, channels: List<Channel>, start: Int, onClose: () -> Unit) {
    if (channels.isEmpty()) { LaunchedEffect(Unit) { onClose() }; return }
    val ctx = LocalContext.current
    val activity = ctx as? Activity
    val view = LocalView.current
    val n = channels.size

    var idx by remember { mutableIntStateOf(start.coerceIn(0, n - 1)) }
    var controls by remember { mutableStateOf(true) }
    var panel by remember { mutableStateOf(false) }
    var playing by remember { mutableStateOf(true) }
    var buffering by remember { mutableStateOf(true) }
    var err by remember { mutableStateOf<String?>(null) }
    var resize by remember { mutableIntStateOf(0) }
    var retry by remember { mutableIntStateOf(0) }
    var poke by remember { mutableIntStateOf(0) }
    val favs = remember { mutableStateMapOf<String, Boolean>().apply { channels.forEach { put(it.url, it.fav) } } }
    val guide by vm.epg.collectAsState()
    val now by produceState(System.currentTimeMillis()) { while (true) { delay(15_000); value = System.currentTimeMillis() } }

    val ch = channels[idx]
    val progs = remember(ch, guide) { vm.progs(ch) }
    val cur = progs.firstOrNull { now >= it.start && now < it.stop }
    val next = progs.firstOrNull { it.start >= (cur?.stop ?: now) }

    val exo = remember {
        val http = DefaultHttpDataSource.Factory()
            .setUserAgent("VLC/3.0.18 LibVLC/3.0.18")
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(10_000).setReadTimeoutMs(15_000)
        ExoPlayer.Builder(ctx).setMediaSourceFactory(DefaultMediaSourceFactory(ctx).setDataSourceFactory(http)).build()
    }
    DisposableEffect(Unit) {
        val l = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) { buffering = state == Player.STATE_BUFFERING }
            override fun onIsPlayingChanged(isPlaying: Boolean) { playing = isPlaying }
            override fun onPlayerError(error: PlaybackException) { err = error.errorCodeName; buffering = false }
        }
        exo.addListener(l)
        val w = activity?.window
        val c = w?.let { WindowCompat.getInsetsController(it, view) }
        c?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        c?.hide(WindowInsetsCompat.Type.systemBars())
        onDispose {
            exo.removeListener(l); exo.release()
            c?.show(WindowInsetsCompat.Type.systemBars())
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    LaunchedEffect(idx, retry) {
        err = null; buffering = true
        try {
            val u = vm.resolve(ch.url)
            exo.setMediaItem(MediaItem.fromUri(u)); exo.prepare(); exo.playWhenReady = true
        } catch (e: Exception) { err = e.message ?: "Could not open stream"; buffering = false }
    }
    LaunchedEffect(controls, panel, poke) { if (controls && !panel) { delay(4_000); controls = false } }

    fun go(d: Int) { idx = (idx + d + n) % n; poke++ }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = { c -> PlayerView(c).apply { useController = false; player = exo; keepScreenOn = true } },
            update = {
                it.resizeMode = when (resize) {
                    0 -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                    1 -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                    else -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                }
            },
            modifier = Modifier.fillMaxSize()
        )
        // gestures: tap = toggle controls, swipe up/down = next/previous channel
        Box(
            Modifier.fillMaxSize()
                .pointerInput(Unit) { detectTapGestures(onTap = { if (panel) panel = false else controls = !controls }) }
                .pointerInput(idx) {
                    var d = 0f
                    detectVerticalDragGestures(
                        onDragStart = { d = 0f },
                        onDragEnd = { if (d < -140) go(1) else if (d > 140) go(-1) },
                        onVerticalDrag = { _, dy -> d += dy })
                }
        )

        if (buffering && err == null) CircularProgressIndicator(Modifier.align(Alignment.Center), color = Color.White)
        err?.let { e ->
            Column(Modifier.align(Alignment.Center).background(Color(0xCC000000), MaterialTheme.shapes.large).padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Can't play this stream", color = Color.White, fontWeight = FontWeight.Bold)
                Text(e, color = Color(0xFFB0B7C3), style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button({ retry++ }) { Text("Retry") }
                    OutlinedButton({ go(1) }) { Text("Next channel") }
                }
            }
        }

        AnimatedVisibility(controls, Modifier.fillMaxSize(), enter = fadeIn(), exit = fadeOut()) {
            Box(Modifier.fillMaxSize()) {
                Row(
                    Modifier.align(Alignment.TopCenter).fillMaxWidth()
                        .background(Brush.verticalGradient(listOf(Color(0xCC000000), Color.Transparent)))
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClose) { Icon(Icons.Default.ArrowBack, "Back", tint = Color.White) }
                    Column(Modifier.weight(1f)) {
                        Text(ch.name, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(cur?.title ?: ch.group, color = Color(0xFFB0B7C3), maxLines = 1,
                            overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                    }
                    TextButton({ resize = (resize + 1) % 3; poke++ }) { Text(listOf("Fit", "Fill", "Zoom")[resize], color = Color.White) }
                    IconButton({
                        val land = ctx.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
                        activity?.requestedOrientation = if (land) ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
                        else ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                        poke++
                    }) { Icon(Icons.Default.Refresh, "Rotate", tint = Color.White) }
                    IconButton({ vm.toggleFav(ch.url); favs[ch.url] = !(favs[ch.url] ?: false); poke++ }) {
                        Icon(if (favs[ch.url] == true) Icons.Default.Favorite else Icons.Default.FavoriteBorder, "Favorite",
                            tint = if (favs[ch.url] == true) Color(0xFFFF6B8B) else Color.White)
                    }
                }
                Column(
                    Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                        .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xDD000000))))
                        .padding(16.dp)
                ) {
                    if (cur != null) {
                        Text("NOW  ${hm(cur.start)}–${hm(cur.stop)}  ${cur.title}", color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Spacer(Modifier.height(6.dp))
                        LinearProgressIndicator(
                            progress = { ((now - cur.start).toFloat() / (cur.stop - cur.start)).coerceIn(0f, 1f) },
                            Modifier.fillMaxWidth())
                    } else Text("No guide data", color = Color(0xFFB0B7C3), style = MaterialTheme.typography.bodySmall)
                    next?.let { Text("NEXT ${hm(it.start)}  ${it.title}", color = Color(0xFFB0B7C3), maxLines = 1,
                        overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp)) }
                    Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically) {
                        IconButton({ go(-1) }) { Icon(Icons.Default.KeyboardArrowDown, "Previous", tint = Color.White) }
                        FilledTonalIconButton({ if (exo.isPlaying) exo.pause() else exo.play(); poke++ }) {
                            if (playing) Text("⏸") else Icon(Icons.Default.PlayArrow, "Play")
                        }
                        IconButton({ go(1) }) { Icon(Icons.Default.KeyboardArrowUp, "Next", tint = Color.White) }
                        IconButton({ panel = true }) { Icon(Icons.Default.Menu, "Channels", tint = Color.White) }
                    }
                }
            }
        }

        AnimatedVisibility(panel, Modifier.align(Alignment.CenterEnd), enter = fadeIn(), exit = fadeOut()) {
            val ls = rememberLazyListState()
            LaunchedEffect(Unit) { ls.scrollToItem(max(0, idx - 2)) }
            LazyColumn(Modifier.width(300.dp).fillMaxHeight().background(Color(0xEE0E1116)), state = ls) {
                itemsIndexed(channels) { i, c ->
                    val p = vm.progs(c).firstOrNull { now >= it.start && now < it.stop }
                    Row(
                        Modifier.fillMaxWidth().clickable { idx = i; panel = false; poke++ }
                            .background(if (i == idx) Color(0x337C9CFF) else Color.Transparent).padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Logo(c, 36)
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(c.name, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(p?.title ?: "—", color = Color(0xFFB0B7C3), maxLines = 1,
                                overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}
