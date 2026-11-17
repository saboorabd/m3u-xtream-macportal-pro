package com.m3upro

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage

const val APP_VERSION = "3.0"

object Pal {
    val ok = Color(0xFF34D399)
    val bad = Color(0xFFF87171)
    val warn = Color(0xFFFBBF24)
    val unk = Color(0xFF64748B)
    val violet = Color(0xFF8B7BFF)
    val blue = Color(0xFF6EA8FF)
}

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Color(0xFF6EA8FF), onPrimary = Color(0xFF07101F),
            primaryContainer = Color(0xFF1E3A6B), onPrimaryContainer = Color(0xFFD6E4FF),
            secondary = Color(0xFF5EEAD4), tertiary = Color(0xFF8B7BFF),
            background = Color(0xFF0B0F17), onBackground = Color(0xFFE6EAF2),
            surface = Color(0xFF0B0F17), onSurface = Color(0xFFE6EAF2),
            surfaceVariant = Color(0xFF151C2A), onSurfaceVariant = Color(0xFF9AA6BD),
            surfaceContainer = Color(0xFF101623), surfaceContainerHighest = Color(0xFF1B2436),
            outline = Color(0xFF2B3750), error = Color(0xFFF87171)
        ), content = content
    )
}

@Composable
fun Dot(s: Int) {
    val c = when (s) { 1 -> Pal.ok; 2 -> Pal.bad; else -> Pal.unk }
    Box(Modifier.size(10.dp).clip(CircleShape).background(c))
}

@Composable
fun Logo(c: Channel, size: Int) {
    Box(
        Modifier.size(size.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center
    ) {
        Text(c.name.trim().take(1).uppercase(), fontWeight = FontWeight.Bold, fontSize = (size / 2.4).sp,
            color = MaterialTheme.colorScheme.primary)
        if (c.logo.startsWith("http")) AsyncImage(c.logo, null, Modifier.fillMaxSize().padding(4.dp), contentScale = ContentScale.Fit)
    }
}

@Composable
fun StatBar(ok: Int, dead: Int, unk: Int, height: Int = 8) {
    Row(Modifier.fillMaxWidth().height(height.dp).clip(RoundedCornerShape(6.dp)).background(Color(0xFF222C40))) {
        if (ok > 0) Box(Modifier.weight(ok.toFloat()).fillMaxHeight().background(Pal.ok))
        if (dead > 0) Box(Modifier.weight(dead.toFloat()).fillMaxHeight().background(Pal.bad))
        if (unk > 0) Box(Modifier.weight(unk.toFloat()).fillMaxHeight().background(Color(0xFF3B475E)))
    }
}

@Composable
fun KindBadge(kind: String) {
    val (label, color) = when (kind) {
        "xtream" -> "XC" to Pal.violet
        "portal" -> "MAC" to Pal.warn
        "file" -> "FILE" to Pal.blue
        "text" -> "TXT" to Pal.blue
        else -> "M3U" to Pal.ok
    }
    Box(Modifier.size(46.dp).clip(RoundedCornerShape(14.dp)).background(color.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
        Text(label, color = color, fontWeight = FontWeight.Bold, fontSize = 13.sp)
    }
}

@Composable
fun StatusPill(c: Channel) {
    val (txt, col) = when (c.status) {
        1 -> (if (c.ms > 0) "${c.ms} ms" else "OK") to Pal.ok
        2 -> c.note.ifBlank { "Dead" }.take(16) to Pal.bad
        else -> (if (c.note.isNotBlank()) c.note.take(16) else "—") to Pal.unk
    }
    Box(Modifier.clip(RoundedCornerShape(10.dp)).background(col.copy(alpha = 0.14f)).padding(horizontal = 8.dp, vertical = 3.dp)) {
        Text(txt, color = col, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

// ------------------------------------------------------------------ shared dialogs
@Composable
fun InputDialog(title: String, label: String, initial: String = "", confirm: String = "OK", onDismiss: () -> Unit, onOk: (String) -> Unit) {
    var v by remember { mutableStateOf(initial) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(title) },
        text = { OutlinedTextField(v, { v = it }, label = { Text(label) }, singleLine = true) },
        confirmButton = { TextButton({ onOk(v) }) { Text(confirm) } },
        dismissButton = { TextButton(onDismiss) { Text("Cancel") } })
}

@Composable
fun PickPlaylistDialog(title: String, options: List<Playlist>, onDismiss: () -> Unit, onPick: (Playlist) -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text(title) },
        text = {
            if (options.isEmpty()) Text("No other playlists yet.")
            else Column {
                options.forEach { p ->
                    ListItem(modifier = Modifier.clickable { onPick(p) },
                        headlineContent = { Text(p.name) },
                        supportingContent = { Text("${p.channels.size} channels") })
                }
            }
        },
        confirmButton = {}, dismissButton = { TextButton(onDismiss) { Text("Cancel") } })
}

@Composable
fun RefreshDialog(vm: MainVm, p: Playlist, onDismiss: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Refresh \"${p.name}\"") },
        text = { Text("Download the playlist again.\n\nAdd new: keeps your edits, only adds channels that are new.\nReplace all: starts fresh from the source (you can undo).") },
        confirmButton = {
            Row {
                TextButton({ onDismiss(); vm.refresh(p.id, false) }) { Text("Add new") }
                TextButton({ onDismiss(); vm.refresh(p.id, true) }) { Text("Replace all") }
            }
        },
        dismissButton = { TextButton(onDismiss) { Text("Cancel") } })
}

@Composable
fun SettingsDialog(vm: MainVm, onDismiss: () -> Unit) {
    val s by vm.settings.collectAsState()
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Checker settings") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Timeout per channel", fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(5, 8, 15, 25).forEach { t -> FilterChip(s.timeout == t, { vm.setSettings(s.copy(timeout = t)) }, { Text("${t}s") }) }
                }
                Text("Parallel checks (higher = faster)", fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(10, 20, 40, 80).forEach { t -> FilterChip(s.concurrency == t, { vm.setSettings(s.copy(concurrency = t)) }, { Text("$t") }) }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Thorough HLS check")
                        Text("Also downloads one video segment, so a playlist that exists but has no video is marked dead.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(s.thorough, { vm.setSettings(s.copy(thorough = it)) })
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Check every channel when the app opens", Modifier.weight(1f))
                    Switch(s.deep, { vm.setSettings(s.copy(deep = it)) })
                }
            }
        },
        confirmButton = { TextButton(onDismiss) { Text("Done") } })
}

// ------------------------------------------------------------------ app shell
@Composable
fun App(vm: MainVm) {
    val pls by vm.playlists.collectAsState()
    val loading by vm.loading.collectAsState()
    val busy by vm.busy.collectAsState()
    val prog by vm.progress.collectAsState()
    val msg by vm.message.collectAsState()
    var openId by remember { mutableStateOf<String?>(null) }
    var portalOpen by remember { mutableStateOf(false) }
    var portalInit by remember { mutableStateOf<Smart?>(null) }
    val snack = remember { SnackbarHostState() }

    LaunchedEffect(msg) { msg?.let { snack.showSnackbar(it); vm.message.value = null } }
    BackHandler(openId != null || portalOpen) { if (portalOpen) portalOpen = false else openId = null }

    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            val open = pls.firstOrNull { it.id == openId }
            when {
                loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(12.dp))
                        Text("Loading your playlists…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                portalOpen -> PortalScreen(vm, portalInit) { portalOpen = false }
                open != null -> EditorScreen(vm, open) { openId = null }
                else -> HomeScreen(vm, pls,
                    onOpen = { vm.openEditor(it); openId = it },
                    onPortal = { portalInit = it; portalOpen = true })
            }
            SnackbarHost(snack, Modifier.align(Alignment.BottomCenter))
        }
        AnimatedVisibility(busy != null) {
            Surface(Modifier.fillMaxWidth(), tonalElevation = 8.dp, color = MaterialTheme.colorScheme.surfaceContainerHighest) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(busy ?: "", Modifier.weight(1f), style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (prog >= 0f) Text("${(prog * 100).toInt()}%", style = MaterialTheme.typography.labelLarge)
                        TextButton({ vm.cancelCheck() }) { Text("Stop") }
                    }
                    if (prog >= 0f) LinearProgressIndicator(progress = { prog }, Modifier.fillMaxWidth())
                    else LinearProgressIndicator(Modifier.fillMaxWidth())
                }
            }
        }
    }
}
