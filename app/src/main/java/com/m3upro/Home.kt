package com.m3upro

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.text.DateFormat
import java.util.Date

@Composable
private fun UrlDialog(onDismiss: () -> Unit, onOk: (String, String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Add M3U from URL") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text("Name (optional)") }, singleLine = true)
                OutlinedTextField(url, { url = it }, Modifier.fillMaxWidth(), label = { Text("M3U / M3U8 URL") })
            }
        },
        confirmButton = { TextButton({ onOk(name.trim(), url.trim()) }, enabled = url.isNotBlank()) { Text("Add") } },
        dismissButton = { TextButton(onDismiss) { Text("Cancel") } })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(vm: MainVm, pls: List<Playlist>, onOpen: (String) -> Unit, onPortal: (Smart?) -> Unit) {
    val stats by vm.stats.collectAsState()
    var dlg by remember { mutableStateOf("") }
    var menuFor by remember { mutableStateOf<String?>(null) }
    var delP by remember { mutableStateOf<Playlist?>(null) }
    var renP by remember { mutableStateOf<Playlist?>(null) }
    var refreshP by remember { mutableStateOf<Playlist?>(null) }
    var exportText by remember { mutableStateOf("") }
    var smart by remember { mutableStateOf("") }
    var prevLen by remember { mutableIntStateOf(0) }
    val det = remember(smart) { SmartDetect.detect(smart) }
    val clip = LocalClipboardManager.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let(vm::addFromUri) }
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("audio/x-mpegurl")) { uri ->
        if (uri != null) vm.exportTo(uri, exportText)
    }
    val fmt = remember { DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT) }
    val tot = stats.values.sumOf { it.total }
    val ok = stats.values.sumOf { it.ok }
    val dead = stats.values.sumOf { it.dead }
    val unk = tot - ok - dead

    // paste a complete portal / Xtream block => fetched automatically
    LaunchedEffect(smart) {
        val jump = smart.length - prevLen
        prevLen = smart.length
        if (jump >= 15 && det.complete && (det.kind == "portal" || det.kind == "xtream")) {
            delay(500)
            vm.smartAdd(det, smart)
            smart = ""; prevLen = 0
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(title = {
                Column {
                    Text("M3U Tools Pro", fontWeight = FontWeight.Bold)
                    Text("v$APP_VERSION", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }, actions = {
                IconButton({ vm.recheckSources() }) { Icon(Icons.Default.Refresh, "Recheck sources") }
                IconButton({ dlg = "settings" }) { Icon(Icons.Default.Settings, "Settings") }
            })
        }
    ) { pad ->
        LazyColumn(Modifier.padding(pad).fillMaxSize(), contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {

            item {
                Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))
                    .background(Brush.linearGradient(listOf(Color(0xFF1E3A6B), Color(0xFF2A2460)))).padding(20.dp)) {
                    Column {
                        Text("${pls.size} playlists  •  $tot channels", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(12.dp))
                        StatBar(ok, dead, unk)
                        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            Text("✓ $ok working", style = MaterialTheme.typography.labelMedium, color = Pal.ok)
                            Text("✕ $dead dead", style = MaterialTheme.typography.labelMedium, color = Pal.bad)
                            Text("? $unk unchecked", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }

            item {
                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Smart add", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Text("Paste an M3U link, an Xtream link, or your MAC portal details (URL, MAC, serial, device IDs). It detects the type and fetches automatically.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        OutlinedTextField(smart, { smart = it }, Modifier.fillMaxWidth().padding(top = 10.dp),
                            placeholder = { Text("Paste here…") }, minLines = 2, maxLines = 6, shape = RoundedCornerShape(14.dp))
                        if (smart.isNotBlank()) {
                            Spacer(Modifier.height(8.dp))
                            Text(det.describe(), style = MaterialTheme.typography.labelLarge,
                                color = if (det.complete) Pal.ok else Pal.warn, fontWeight = FontWeight.SemiBold)
                            if (det.kind == "portal") {
                                fun t(b: Boolean) = if (b) "✓" else "✗"
                                Text("URL ${t(det.url.isNotBlank())}   MAC ${t(det.mac.isNotBlank())}   Serial ${t(det.sn.isNotBlank())}   ID1 ${t(det.d1.isNotBlank())}   ID2 ${t(det.d2.isNotBlank())}",
                                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            OutlinedButton({ clip.getText()?.text?.let { smart = it } }) { Text("Paste") }
                            Button({ vm.smartAdd(det, smart); smart = ""; prevLen = 0 }, enabled = det.complete) { Text("Fetch") }
                            if (det.kind == "portal") TextButton({ onPortal(det) }) { Text("Review") }
                        }
                    }
                }
            }

            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("🔗" to "M3U URL", "📁" to "File", "⚡" to "Xtream", "📡" to "MAC").forEachIndexed { k, (icon, label) ->
                        Card(onClick = {
                            when (k) {
                                0 -> dlg = "url"
                                1 -> picker.launch(arrayOf("*/*"))
                                2 -> dlg = "xtream"
                                else -> onPortal(null)
                            }
                        }, Modifier.weight(1f), shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest)) {
                            Column(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(icon, fontSize = 22.sp)
                                Text(label, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center)
                            }
                        }
                    }
                }
            }

            item { Text("Your playlists", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp)) }

            if (pls.isEmpty()) item {
                Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("📋", fontSize = 40.sp)
                    Spacer(Modifier.height(6.dp))
                    Text("Nothing here yet", style = MaterialTheme.typography.titleMedium)
                    Text("Use Smart add above, or pick a source.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            items(pls, key = { it.id }) { p ->
                val st = stats[p.id] ?: Stats(p.channels.size, 0, 0, p.channels.size)
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
                                Text("${p.channels.size} channels" + if (p.source.isNotBlank()) "  •  " + hostOf(p.source) else "",
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
                                        exportText = M3u.export(p.channels, p.epg)
                                        exporter.launch(p.name.replace(Regex("[^\\w.-]"), "_") + ".m3u")
                                    })
                                    DropdownMenuItem(text = { Text("Rename") }, onClick = { menuFor = null; renP = p })
                                    DropdownMenuItem(text = { Text("Delete") }, onClick = { menuFor = null; delP = p })
                                }
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        StatBar(st.ok, st.dead, st.unk)
                        Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            Text("✓ ${st.ok}", style = MaterialTheme.typography.labelSmall, color = Pal.ok)
                            Text("✕ ${st.dead}", style = MaterialTheme.typography.labelSmall, color = Pal.bad)
                            Text("? ${st.unk}", style = MaterialTheme.typography.labelSmall)
                            Spacer(Modifier.weight(1f))
                            Text(if (p.lastChecked > 0) fmt.format(Date(p.lastChecked)) else "", style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (summary.isNotBlank()) Text(summary, Modifier.padding(top = 6.dp), style = MaterialTheme.typography.labelMedium,
                            color = if (bad) Pal.bad else MaterialTheme.colorScheme.secondary)
                    }
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }

    when (dlg) {
        "url" -> UrlDialog({ dlg = "" }) { n, u -> dlg = ""; vm.addFromUrl(n, u) }
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
