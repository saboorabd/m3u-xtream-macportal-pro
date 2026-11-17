package com.m3upro

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

@Composable
fun Field(label: String, v: String, onChange: (String) -> Unit) {
    OutlinedTextField(v, onChange, Modifier.fillMaxWidth(), label = { Text(label) }, singleLine = true)
}

/** Full-screen, searchable category picker (the answer to "I can't find my category in a 200-group playlist"). */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CategorySheet(
    groups: List<GroupCount>, total: Int, selected: String?,
    onPick: (String?) -> Unit, onManage: (String) -> Unit, onDismiss: () -> Unit
) {
    var q by remember { mutableStateOf("") }
    var sort by remember { mutableIntStateOf(0) }  // 0 A-Z, 1 most channels, 2 most dead
    val list = remember(groups, q, sort) {
        val tokens = q.lowercase().split(' ').filter { it.isNotBlank() }
        val f = if (tokens.isEmpty()) groups else groups.filter { g ->
            val n = g.name.lowercase().ifEmpty { "ungrouped" }
            tokens.all { n.contains(it) }
        }
        when (sort) {
            1 -> f.sortedByDescending { it.count }
            2 -> f.sortedByDescending { it.dead }
            else -> f
        }
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.statusBarsPadding().navigationBarsPadding()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onDismiss) { Icon(Icons.Default.Close, "Close") }
                    Text("Categories (${groups.size})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                OutlinedTextField(q, { q = it }, Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                    placeholder = { Text("Search categories") }, singleLine = true, shape = RoundedCornerShape(16.dp),
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    trailingIcon = { if (q.isNotEmpty()) IconButton({ q = "" }) { Icon(Icons.Default.Close, "Clear") } })
                Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(sort == 0, { sort = 0 }, { Text("A–Z") })
                    FilterChip(sort == 1, { sort = 1 }, { Text("Most channels") })
                    FilterChip(sort == 2, { sort = 2 }, { Text("Most dead") })
                }
                LazyColumn(Modifier.fillMaxSize()) {
                    item {
                        ListItem(modifier = Modifier.combinedClickable(onClick = { onPick(null) }),
                            headlineContent = { Text("All categories", fontWeight = FontWeight.SemiBold) },
                            trailingContent = { Text("$total") },
                            leadingContent = { Text("📂") })
                    }
                    items(list, key = { it.name }) { g ->
                        ListItem(
                            modifier = Modifier.combinedClickable(onClick = { onPick(g.name) }, onLongClick = { onManage(g.name) }),
                            headlineContent = { Text(g.name.ifBlank { "Ungrouped" }, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            supportingContent = { StatBar(g.ok, g.dead, g.count - g.ok - g.dead, 4) },
                            trailingContent = { Text("${g.count}") },
                            leadingContent = { if (selected == g.name) Text("✓", color = Pal.ok, fontWeight = FontWeight.Bold) else Text("📁") })
                    }
                    item { Text("Tip: long-press a category to rename, merge or delete it.", Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
        }
    }
}

@Composable
fun GroupActionDialog(name: String, count: Int, onDismiss: () -> Unit, onRename: (String) -> Unit, onUngroup: () -> Unit, onDelete: () -> Unit) {
    var rename by remember(name) { mutableStateOf(false) }
    if (rename) {
        InputDialog("Rename category", "New name (an existing name merges)", name, "Rename", onDismiss) { onRename(it.trim()) }
    } else {
        AlertDialog(onDismissRequest = onDismiss, title = { Text(name.ifBlank { "Ungrouped" } + " ($count)") },
            text = {
                Column {
                    TextButton({ rename = true }) { Text("Rename / merge into another category") }
                    TextButton(onUngroup) { Text("Remove category (keep channels)") }
                    TextButton(onDelete) { Text("Delete category and its channels") }
                }
            },
            confirmButton = {}, dismissButton = { TextButton(onDismiss) { Text("Close") } })
    }
}

@Composable
fun AddChannelDialog(onDismiss: () -> Unit, onAdd: (Channel) -> Unit) {
    var n by remember { mutableStateOf("") }
    var g by remember { mutableStateOf("") }
    var u by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Add channel") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Field("Name", n) { n = it }
                Field("Category", g) { g = it }
                Field("Stream URL", u) { u = it }
            }
        },
        confirmButton = { TextButton({ onAdd(Channel(name = n.trim(), url = u.trim(), group = g.trim())) }, enabled = n.isNotBlank() && u.isNotBlank()) { Text("Add") } },
        dismissButton = { TextButton(onDismiss) { Text("Cancel") } })
}

@Composable
fun FindReplaceDialog(count: Int, onDismiss: () -> Unit, onApply: (String, String, Int, Boolean) -> Unit) {
    var find by remember { mutableStateOf("") }
    var rep by remember { mutableStateOf("") }
    var scope by remember { mutableIntStateOf(0) }
    var regex by remember { mutableStateOf(false) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Find & replace") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Field("Find", find) { find = it }
                Field("Replace with", rep) { rep = it }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(scope == 0, { scope = 0 }, { Text("Names") })
                    FilterChip(scope == 1, { scope = 1 }, { Text("Categories") })
                    FilterChip(scope == 2, { scope = 2 }, { Text("URLs") })
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(regex, { regex = it })
                    Text("Regular expression")
                }
                Text("Applies to $count channels (selected, otherwise everything shown).", style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { TextButton({ onApply(find, rep, scope, regex) }, enabled = find.isNotEmpty()) { Text("Apply") } },
        dismissButton = { TextButton(onDismiss) { Text("Cancel") } })
}

@Composable
fun AffixDialog(count: Int, onDismiss: () -> Unit, onApply: (String, String) -> Unit) {
    var pre by remember { mutableStateOf("") }
    var suf by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Prefix / suffix") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Field("Prefix", pre) { pre = it }
                Field("Suffix", suf) { suf = it }
                Text("Applies to $count channel names.", style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { TextButton({ onApply(pre, suf) }, enabled = pre.isNotEmpty() || suf.isNotEmpty()) { Text("Apply") } },
        dismissButton = { TextButton(onDismiss) { Text("Cancel") } })
}

@Composable
fun QuickFixDialog(count: Int, onDismiss: () -> Unit, onPick: (Int) -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Quick fixes") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("Applies to $count channel names.", style = MaterialTheme.typography.bodySmall)
                Organize.QUICK.forEachIndexed { i, (label, _) ->
                    ListItem(modifier = Modifier.clickable { onPick(i) }, headlineContent = { Text(label) })
                }
            }
        },
        confirmButton = {}, dismissButton = { TextButton(onDismiss) { Text("Close") } })
}

@Composable
fun OrganizeDialog(count: Int, onDismiss: () -> Unit, onApply: (Boolean) -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Smart organize") },
        text = {
            Text("Creates categories automatically from channel names: prefixes like IN| or [TAMIL], and keywords such as Sports, News, Movies, Kids, Music.\n\nApplies to $count channels. You can undo.")
        },
        confirmButton = {
            Row {
                TextButton({ onApply(true) }) { Text("Only ungrouped") }
                TextButton({ onApply(false) }) { Text("All") }
            }
        },
        dismissButton = { TextButton(onDismiss) { Text("Cancel") } })
}

@Composable
fun PlaylistSettingsDialog(p: Playlist, onDismiss: () -> Unit, onSave: (String, String) -> Unit) {
    var n by remember { mutableStateOf(p.name) }
    var e by remember { mutableStateOf(p.epg) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Playlist settings") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Field("Name", n) { n = it }
                Field("EPG URL (url-tvg)", e) { e = it }
                Text("Saved in the exported #EXTM3U header.", style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { TextButton({ onSave(n, e) }) { Text("Save") } },
        dismissButton = { TextButton(onDismiss) { Text("Cancel") } })
}

@Composable
fun EditChannelDialog(c: Channel, onDismiss: () -> Unit, onSave: (Channel) -> Unit, onTest: () -> Unit, onOpen: () -> Unit) {
    var tab by remember(c) { mutableIntStateOf(0) }
    var n by remember(c) { mutableStateOf(c.name) }
    var g by remember(c) { mutableStateOf(c.group) }
    var u by remember(c) { mutableStateOf(c.url) }
    var lg by remember(c) { mutableStateOf(c.logo) }
    var tv by remember(c) { mutableStateOf(c.tvgId) }
    var ua by remember(c) { mutableStateOf(c.ua) }
    var rf by remember(c) { mutableStateOf(c.ref) }
    var ex by remember(c) { mutableStateOf(c.extra) }
    var tg by remember(c) { mutableStateOf(c.tags) }
    var d by remember(c) { mutableStateOf(c.drm) }
    var k by remember(c) { mutableStateOf(c.key) }
    var raw by remember(c) { mutableStateOf("") }

    fun fromFields(): Channel {
        val same = u.trim() == c.url
        return c.copy(name = n.trim(), group = g.trim(), url = u.trim(), logo = lg.trim(), tvgId = tv.trim(),
            ua = ua.trim(), ref = rf.trim(), drm = d.trim(), key = k.trim(), extra = ex, tags = tg,
            status = if (same) c.status else 0, ms = if (same) c.ms else 0, note = if (same) c.note else "")
    }
    fun switchTo(t: Int) {
        if (tab == 3 && t != 3) {
            M3u.parse(raw).firstOrNull()?.let { p ->
                n = p.name; g = p.group; u = p.url; lg = p.logo; tv = p.tvgId; ua = p.ua; rf = p.ref
                d = p.drm; k = p.key; ex = p.extra; tg = p.tags
            }
        }
        if (t == 3 && tab != 3) raw = M3u.export(listOf(fromFields())).substringAfter("\n")
        tab = t
    }

    AlertDialog(onDismissRequest = onDismiss,
        title = { Text("Edit channel") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (c.status != 0) Text("Last check: " + c.note.ifBlank { "OK" } + if (c.ms > 0) " (${c.ms} ms)" else "",
                    style = MaterialTheme.typography.labelMedium, color = if (c.status == 1) Pal.ok else Pal.bad)
                if (c.raw.isNotBlank()) Text("Portal command: " + c.raw.removePrefix("portal://"), style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("Basic", "Advanced", "DRM", "Raw").forEachIndexed { i, label -> FilterChip(tab == i, { switchTo(i) }, { Text(label) }) }
                }
                when (tab) {
                    0 -> {
                        Field("Name", n) { n = it }
                        Field("Category", g) { g = it }
                        Field("Stream URL", u) { u = it }
                        Field("Logo URL", lg) { lg = it }
                        Field("TVG ID", tv) { tv = it }
                    }
                    1 -> {
                        Field("User-Agent", ua) { ua = it }
                        Field("Referer", rf) { rf = it }
                        Field("Other #EXTINF attributes", ex) { ex = it }
                        OutlinedTextField(tg, { tg = it }, Modifier.fillMaxWidth(), label = { Text("Other tag lines (#KODIPROP, #EXTVLCOPT …)") }, minLines = 2, maxLines = 5)
                    }
                    2 -> {
                        Field("DRM type (clearkey / widevine)", d) { d = it }
                        Field("DRM key or license URL", k) { k = it }
                    }
                    else -> OutlinedTextField(raw, { raw = it }, Modifier.fillMaxWidth(), label = { Text("Raw M3U entry") }, minLines = 5, maxLines = 12)
                }
            }
        },
        confirmButton = {
            TextButton({
                if (tab == 3) switchTo(0)
                onSave(fromFields())
            }) { Text("Save") }
        },
        dismissButton = {
            Row {
                TextButton(onTest) { Text("Test") }
                TextButton(onOpen) { Text("Open") }
                TextButton(onDismiss) { Text("Cancel") }
            }
        })
}
