package com.m3upro

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import kotlinx.coroutines.delay
import java.text.DateFormat
import java.util.Date

@Composable
private fun BigStat(value: Int, label: String, color: Color, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text("$value", style = MaterialTheme.typography.titleLarge, color = color)
        Text(label, style = MaterialTheme.typography.labelSmall, color = Color(0xFFC3CCE0))
    }
}

@Composable
fun LibraryScreen(vm: MainVm, pls: List<Playlist>, onOpen: (String) -> Unit, onGoAdd: () -> Unit) {
    val stats by vm.stats.collectAsState()
    var menuFor by remember { mutableStateOf<String?>(null) }
    var delP by remember { mutableStateOf<Playlist?>(null) }
    var renP by remember { mutableStateOf<Playlist?>(null) }
    var refreshP by remember { mutableStateOf<Playlist?>(null) }
    var exportText by remember { mutableStateOf("") }
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("audio/x-mpegurl")) { uri ->
        if (uri != null) vm.exportTo(uri, exportText)
    }
    val fmt = remember { DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT) }
    val tot = stats.values.sumOf { it.total }
    val ok = stats.values.sumOf { it.ok }
    val dead = stats.values.sumOf { it.dead }
    val unk = tot - ok - dead

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp, 12.dp, 16.dp, 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                LogoMark(46.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Library", style = MaterialTheme.typography.headlineSmall)
                    Text("${pls.size} playlists  •  $tot channels", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton({ vm.recheckSources() }) { Icon(Icons.Default.Refresh, "Recheck sources") }
            }
        }

        if (pls.isNotEmpty()) item {
            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))
                .background(Brush.linearGradient(listOf(Pal.g1, Pal.g2))).padding(20.dp)) {
                Column {
                    Row(Modifier.fillMaxWidth()) {
                        BigStat(ok, "working", Pal.ok, Modifier.weight(1f))
                        BigStat(dead, "dead", Pal.bad, Modifier.weight(1f))
                        BigStat(unk, "unchecked", Color.White, Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(14.dp))
                    StatBar(ok, dead, unk, 10)
                    Spacer(Modifier.height(16.dp))
                    GradientButton("Check everything", { vm.checkAllPlaylists() }, Modifier.fillMaxWidth(), icon = R.drawable.ic_sparkle)
                }
            }
        }

        if (pls.isEmpty()) item {
            SoftCard(Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(64.dp).clip(CircleShape).background(Pal.blue.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
                        Ic(R.drawable.ic_library, Pal.blue, 32.dp)
                    }
                    Spacer(Modifier.height(14.dp))
                    Text("No playlists yet", style = MaterialTheme.typography.titleMedium)
                    Text("Paste a link or your portal details and everything is fetched for you.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp, bottom = 16.dp))
                    GradientButton("Add your first playlist", onGoAdd, icon = R.drawable.ic_sparkle)
                }
            }
        }

        items(pls, key = { it.id }) { p ->
            val st = stats[p.id] ?: Stats(p.channels.size, 0, 0, p.channels.size)
            val bad = p.state == 2 || p.info.contains("expired", true) || p.info.contains("disabled", true) || p.info.contains("banned", true)
            val summary = p.info.lines().filter {
                it.startsWith("Status") || it.startsWith("Expires") || it.startsWith("Expiry") || it.startsWith("Connections")
            }.joinToString("  •  ")
            SoftCard(Modifier.fillMaxWidth(), onClick = { onOpen(p.id) }) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        KindBadge(p.kind)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(p.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
                    Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        Text("${st.ok} ok", style = MaterialTheme.typography.labelSmall, color = Pal.ok)
                        Text("${st.dead} dead", style = MaterialTheme.typography.labelSmall, color = Pal.bad)
                        Text("${st.unk} unchecked", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.weight(1f))
                        Text(if (p.lastChecked > 0) fmt.format(Date(p.lastChecked)) else "", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (summary.isNotBlank()) Text(summary, Modifier.padding(top = 8.dp), style = MaterialTheme.typography.labelMedium,
                        color = if (bad) Pal.bad else MaterialTheme.colorScheme.secondary)
                }
            }
        }
    }

    delP?.let { p ->
        AlertDialog(onDismissRequest = { delP = null }, title = { Text("Delete \"${p.name}\"?") },
            confirmButton = { TextButton({ vm.delete(p.id); delP = null }) { Text("Delete") } },
            dismissButton = { TextButton({ delP = null }) { Text("Cancel") } })
    }
    renP?.let { p -> InputDialog("Rename playlist", "Name", p.name, "Rename", { renP = null }) { vm.rename(p.id, it); renP = null } }
    refreshP?.let { p -> RefreshDialog(vm, p) { refreshP = null } }
}

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

@Composable
private fun RowScope.SourceTile(icon: Int, color: Color, title: String, desc: String, onClick: () -> Unit) {
    SoftCard(Modifier.weight(1f), onClick = onClick) {
        Column(Modifier.padding(14.dp)) {
            Box(Modifier.size(42.dp).clip(RoundedCornerShape(12.dp)).background(color.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
                Ic(icon, color, 22.dp)
            }
            Spacer(Modifier.height(10.dp))
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
        }
    }
}

@Composable
fun AddScreen(vm: MainVm, onPortal: (Smart?) -> Unit, onAdded: () -> Unit) {
    var dlg by remember { mutableStateOf("") }
    var smart by remember { mutableStateOf("") }
    var prevLen by remember { mutableIntStateOf(0) }
    val det = remember(smart) { SmartDetect.detect(smart) }
    val clip = LocalClipboardManager.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) { vm.addFromUri(uri); onAdded() }
    }

    // a complete portal / Xtream block pasted => fetched automatically
    LaunchedEffect(smart) {
        val jump = smart.length - prevLen
        prevLen = smart.length
        if (jump >= 15 && det.complete && (det.kind == "portal" || det.kind == "xtream")) {
            delay(500)
            vm.smartAdd(det, smart, onAdded)
            smart = ""; prevLen = 0
        }
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp, 12.dp, 16.dp, 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Column {
                Text("Add source", style = MaterialTheme.typography.headlineSmall)
                Text("Paste, and it does the rest.", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item {
            SoftCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Ic(R.drawable.ic_sparkle, Pal.violet, 20.dp)
                        Spacer(Modifier.width(8.dp))
                        Text("Smart add", style = MaterialTheme.typography.titleMedium)
                    }
                    Text("M3U link, Xtream link, or MAC portal details (URL, MAC, serial, device IDs).",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp))
                    OutlinedTextField(smart, { smart = it }, Modifier.fillMaxWidth().padding(top = 12.dp),
                        placeholder = { Text("Paste here…") }, minLines = 3, maxLines = 7, shape = RoundedCornerShape(14.dp))
                    AnimatedVisibility(smart.isNotBlank()) {
                        Column(Modifier.padding(top = 10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Dot(if (det.complete) 1 else 0)
                                Spacer(Modifier.width(8.dp))
                                Text(det.describe(), style = MaterialTheme.typography.labelLarge,
                                    color = if (det.complete) Pal.ok else Pal.warn)
                            }
                            if (det.kind == "portal") {
                                fun t(b: Boolean) = if (b) "✓" else "✗"
                                Text("URL ${t(det.url.isNotBlank())}   MAC ${t(det.mac.isNotBlank())}   Serial ${t(det.sn.isNotBlank())}   ID1 ${t(det.d1.isNotBlank())}   ID2 ${t(det.d2.isNotBlank())}",
                                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 4.dp))
                            }
                        }
                    }
                    Row(Modifier.padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedButton({ clip.getText()?.text?.let { smart = it } }, shape = RoundedCornerShape(14.dp), modifier = Modifier.height(52.dp)) {
                            Ic(R.drawable.ic_paste, size = 18.dp)
                            Spacer(Modifier.width(6.dp))
                            Text("Paste")
                        }
                        GradientButton("Fetch", { vm.smartAdd(det, smart, onAdded); smart = ""; prevLen = 0 },
                            Modifier.weight(1f), enabled = det.complete)
                    }
                    if (det.kind == "portal") TextButton({ onPortal(det) }) { Text("Review fields first") }
                }
            }
        }
        item { SectionLabel("Or pick a source") }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SourceTile(R.drawable.ic_link, Pal.ok, "M3U link", "Download a playlist from a URL") { dlg = "url" }
                SourceTile(R.drawable.ic_folder, Pal.blue, "Playlist file", "Open an .m3u from your phone") { picker.launch(arrayOf("*/*")) }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SourceTile(R.drawable.ic_server, Pal.violet, "Xtream Codes", "Server, username, password") { dlg = "xtream" }
                SourceTile(R.drawable.ic_router, Pal.warn, "MAC portal", "URL, MAC, serial, device IDs") { onPortal(null) }
            }
        }
        item {
            SoftCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Good to know", style = MaterialTheme.typography.titleSmall)
                    Text("• A pasted portal or Xtream block is fetched automatically.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("• Xtream and M3U channels already have direct links. For portals use \"Get real stream links\" in the editor.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("• Something failed? Settings → Diagnostics → Copy debug log.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }

    when (dlg) {
        "url" -> UrlDialog({ dlg = "" }) { n, u -> dlg = ""; vm.addFromUrl(n, u); onAdded() }
        "xtream" -> XtreamDialog(vm) { dlg = ""; onAdded() }
        else -> {}
    }
}
