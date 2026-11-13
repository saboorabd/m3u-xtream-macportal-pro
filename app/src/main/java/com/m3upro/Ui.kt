package com.m3upro

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import java.text.DateFormat
import java.util.Date

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Color(0xFF7C9CFF), onPrimary = Color(0xFF0B1020),
            primaryContainer = Color(0xFF26356B), onPrimaryContainer = Color(0xFFDDE5FF),
            secondary = Color(0xFF5EEAD4), background = Color(0xFF0E1116),
            surface = Color(0xFF0E1116), surfaceVariant = Color(0xFF1A2029),
            surfaceContainer = Color(0xFF141922), surfaceContainerHighest = Color(0xFF1F2630)
        ), content = content
    )
}

@Composable
fun Dot(s: Int) {
    val c = when (s) { 1 -> Color(0xFF34D399); 2 -> Color(0xFFF87171); else -> Color(0xFF6B7280) }
    Box(Modifier.size(10.dp).clip(CircleShape).background(c))
}

@Composable
fun Logo(c: Channel, size: Int) {
    Box(
        Modifier.size(size.dp).clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center
    ) {
        Text(c.name.trim().take(1).uppercase(), fontWeight = FontWeight.Bold, fontSize = (size / 2.4).sp,
            color = MaterialTheme.colorScheme.primary)
        if (c.logo.startsWith("http")) AsyncImage(c.logo, null, Modifier.fillMaxSize().padding(3.dp), contentScale = ContentScale.Fit)
    }
}

@Composable
fun StatBar(ok: Int, dead: Int, unk: Int) {
    Row(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(Color(0xFF2A323E))) {
        if (ok > 0) Box(Modifier.weight(ok.toFloat()).fillMaxHeight().background(Color(0xFF34D399)))
        if (dead > 0) Box(Modifier.weight(dead.toFloat()).fillMaxHeight().background(Color(0xFFF87171)))
        if (unk > 0) Box(Modifier.weight(unk.toFloat()).fillMaxHeight().background(Color(0xFF4B5563)))
    }
}

@Composable
fun KindBadge(kind: String) {
    val (label, color) = when (kind) {
        "xtream" -> "XC" to Color(0xFF8B5CF6)
        "portal" -> "MAC" to Color(0xFFF59E0B)
        "file" -> "FILE" to Color(0xFF38BDF8)
        "text" -> "TXT" to Color(0xFF38BDF8)
        else -> "M3U" to Color(0xFF34D399)
    }
    Box(Modifier.size(46.dp).clip(RoundedCornerShape(14.dp)).background(color.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
        Text(label, color = color, fontWeight = FontWeight.Bold, fontSize = 13.sp)
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
            else Column(Modifier.verticalScroll(rememberScrollState())) {
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
                    Text("Check every channel when the app opens", Modifier.weight(1f))
                    Switch(s.deep, { vm.setSettings(s.copy(deep = it)) })
                }
            }
        },
        confirmButton = { TextButton(onDismiss) { Text("Done") } })
}

@Composable
private fun UrlDialog(onDismiss: () -> Unit, onOk: (String, String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Add M3U from URL") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Name (optional)") }, singleLine = true)
                OutlinedTextField(url, { url = it }, label = { Text("M3U / M3U8 URL") })
            }
        },
        confirmButton = { TextButton({ onOk(name.trim(), url.trim()) }, enabled = url.isNotBlank()) { Text("Add") } },
        dismissButton = { TextButton(onDismiss) { Text("Cancel") } })
}

@Composable
private fun TextDialog(onDismiss: () -> Unit, onOk: (String, String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var text by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Paste M3U text") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Name (optional)") }, singleLine = true)
                OutlinedTextField(text, { text = it }, label = { Text("#EXTM3U ...") }, minLines = 6, maxLines = 10)
            }
        },
        confirmButton = { TextButton({ onOk(name.trim(), text) }, enabled = text.isNotBlank()) { Text("Add") } },
        dismissButton = { TextButton(onDismiss) { Text("Cancel") } })
}

// ------------------------------------------------------------------ app shell
@Composable
fun App(vm: MainVm) {
    val pls by vm.playlists.collectAsState()
    val busy by vm.busy.collectAsState()
    val prog by vm.progress.collectAsState()
    val msg by vm.message.collectAsState()
    var openId by remember { mutableStateOf<String?>(null) }
    var portal by remember { mutableStateOf(false) }
    val snack = remember { SnackbarHostState() }
    LaunchedEffect(msg) { msg?.let { snack.showSnackbar(it); vm.message.value = null } }
    BackHandler(openId != null || portal) { if (portal) portal = false else openId = null }

    Box(Modifier.fillMaxSize()) {
        val open = pls.firstOrNull { it.id == openId }
        when {
            portal -> PortalScreen(vm) { portal = false }
            open != null -> EditorScreen(vm, open) { openId = null }
            else -> HomeScreen(vm, pls, { openId = it }, { portal = true })
        }
        busy?.let { text ->
            Surface(Modifier.align(Alignment.TopCenter).fillMaxWidth(), tonalElevation = 8.dp) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(text, Modifier.weight(1f), style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (prog >= 0f) Text("${(prog * 100).toInt()}%", style = MaterialTheme.typography.labelLarge)
                        TextButton({ vm.cancelCheck() }) { Text("Stop") }
                    }
                    if (prog >= 0f) LinearProgressIndicator(progress = { prog }, Modifier.fillMaxWidth())
                    else LinearProgressIndicator(Modifier.fillMaxWidth())
                }
            }
        }
        SnackbarHost(snack, Modifier.align(Alignment.BottomCenter).navigationBarsPadding())
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(vm: MainVm, pls: List<Playlist>, onOpen: (String) -> Unit, onPortal: () -> Unit) {
    var dlg by remember { mutableStateOf("") }
    var menuFor by remember { mutableStateOf<String?>(null) }
    var delP by remember { mutableStateOf<Playlist?>(null) }
    var renP by remember { mutableStateOf<Playlist?>(null) }
    var refreshP by remember { mutableStateOf<Playlist?>(null) }
    var exportText by remember { mutableStateOf("") }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let(vm::addFromUri) }
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("audio/x-mpegurl")) { uri ->
        if (uri != null) vm.exportTo(uri, exportText)
    }
    val fmt = remember { DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT) }
    val total = pls.sumOf { it.channels.size }
    val ok = pls.sumOf { p -> p.channels.count { it.status == 1 } }
    val dead = pls.sumOf { p -> p.channels.count { it.status == 2 } }
    val unk = total - ok - dead

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("M3U Tools Pro", fontWeight = FontWeight.Bold) }, actions = {
                IconButton({ vm.recheckSources() }) { Icon(Icons.Default.Refresh, "Recheck sources") }
                IconButton({ dlg = "settings" }) { Icon(Icons.Default.Settings, "Settings") }
            })
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = { dlg = "add" }) {
                Icon(Icons.Default.Add, null)
                Spacer(Modifier.width(8.dp))
                Text("Add playlist")
            }
        }
    ) { pad ->
        LazyColumn(Modifier.padding(pad).fillMaxSize(), contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))
                    .background(Brush.linearGradient(listOf(Color(0xFF26356B), Color(0xFF1A2029)))).padding(20.dp)) {
                    Column {
                        Text("${pls.size} playlists • $total channels", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(12.dp))
                        StatBar(ok, dead, unk)
                        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            Text("✓ $ok working", style = MaterialTheme.typography.labelMedium, color = Color(0xFF34D399))
                            Text("✕ $dead dead", style = MaterialTheme.typography.labelMedium, color = Color(0xFFF87171))
                            Text("? $unk unchecked", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
            if (pls.isEmpty()) item {
                Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("📋", fontSize = 44.sp)
                    Spacer(Modifier.height(8.dp))
                    Text("No playlists yet", style = MaterialTheme.typography.titleMedium)
                    Text("Add an M3U, Xtream or MAC portal source to check and edit it.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            items(pls, key = { it.id }) { p ->
                val pok = p.channels.count { it.status == 1 }
                val pdead = p.channels.count { it.status == 2 }
                val punk = p.channels.size - pok - pdead
                val bad = p.state == 2 || p.info.contains("expired", true) || p.info.contains("disabled", true) || p.info.contains("banned", true)
                val summary = p.info.lines().filter {
                    it.startsWith("Status") || it.startsWith("Expires") || it.startsWith("Expiry") || it.startsWith("Connections")
                }.joinToString("  •  ")
                Card(onClick = { onOpen(p.id) }, Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            KindBadge(p.kind)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(p.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("${p.channels.size} channels" + if (p.source.isNotBlank()) " • " + hostOf(p.source) else "",
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            Dot(if (bad) 2 else p.state)
                            Box {
                                IconButton({ menuFor = p.id }) { Icon(Icons.Default.MoreVert, "Menu") }
                                DropdownMenu(menuFor == p.id, { menuFor = null }) {
                                    DropdownMenuItem(text = { Text("Check all channels") }, onClick = { menuFor = null; vm.checkChannels(p.id, null, false) })
                                    if (p.kind == "m3u" || p.kind == "xtream" || p.kind == "portal")
                                        DropdownMenuItem(text = { Text("Refresh from source") }, onClick = { menuFor = null; refreshP = p })
                                    DropdownMenuItem(text = { Text("Export .m3u") }, onClick = {
                                        menuFor = null
                                        exportText = M3u.export(p.channels, "")
                                        exporter.launch(p.name.replace(Regex("[^\\w.-]"), "_") + ".m3u")
                                    })
                                    DropdownMenuItem(text = { Text("Rename") }, onClick = { menuFor = null; renP = p })
                                    DropdownMenuItem(text = { Text("Delete") }, onClick = { menuFor = null; delP = p })
                                }
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        StatBar(pok, pdead, punk)
                        Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            Text("✓ $pok", style = MaterialTheme.typography.labelSmall, color = Color(0xFF34D399))
                            Text("✕ $pdead", style = MaterialTheme.typography.labelSmall, color = Color(0xFFF87171))
                            Text("? $punk", style = MaterialTheme.typography.labelSmall)
                            Spacer(Modifier.weight(1f))
                            Text(if (p.lastChecked > 0) fmt.format(Date(p.lastChecked)) else "", style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (summary.isNotBlank()) Text(summary, Modifier.padding(top = 6.dp), style = MaterialTheme.typography.labelMedium,
                            color = if (bad) Color(0xFFF87171) else MaterialTheme.colorScheme.secondary)
                    }
                }
            }
            item { Spacer(Modifier.height(80.dp)) }
        }
    }

    when (dlg) {
        "add" -> AlertDialog(onDismissRequest = { dlg = "" }, title = { Text("Add playlist") },
            text = {
                Column {
                    ListItem(modifier = Modifier.clickable { dlg = "url" }, headlineContent = { Text("M3U / M3U8 URL") }, supportingContent = { Text("Download from a link") })
                    ListItem(modifier = Modifier.clickable { dlg = ""; picker.launch(arrayOf("*/*")) }, headlineContent = { Text("M3U file") }, supportingContent = { Text("Pick a file from your phone") })
                    ListItem(modifier = Modifier.clickable { dlg = "text" }, headlineContent = { Text("Paste M3U text") }, supportingContent = { Text("Copy-paste playlist content") })
                    ListItem(modifier = Modifier.clickable { dlg = "xtream" }, headlineContent = { Text("Xtream Codes") }, supportingContent = { Text("Server, username, password") })
                    ListItem(modifier = Modifier.clickable { dlg = ""; onPortal() }, headlineContent = { Text("MAC portal (Stalker)") }, supportingContent = { Text("URL, MAC, device IDs, serial") })
                }
            },
            confirmButton = {}, dismissButton = { TextButton({ dlg = "" }) { Text("Close") } })
        "url" -> UrlDialog({ dlg = "" }) { n, u -> dlg = ""; vm.addFromUrl(n, u) }
        "text" -> TextDialog({ dlg = "" }) { n, t -> dlg = ""; vm.addFromText(n, t) }
        "xtream" -> XtreamDialog(vm) { dlg = "" }
        "settings" -> SettingsDialog(vm) { dlg = "" }
        else -> {}
    }
    delP?.let { p ->
        AlertDialog(onDismissRequest = { delP = null }, title = { Text("Delete \"${p.name}\"?") },
            confirmButton = { TextButton({ vm.delete(p.id); delP = null }) { Text("Delete") } },
            dismissButton = { TextButton({ delP = null }) { Text("Cancel") } })
    }
    renP?.let { p -> InputDialog("Rename playlist", "Name", p.name, "Rename", { renP = null }) { vm.rename(p.id, it); renP = null } }
    refreshP?.let { p -> RefreshDialog(vm, p) { refreshP = null } }
}
