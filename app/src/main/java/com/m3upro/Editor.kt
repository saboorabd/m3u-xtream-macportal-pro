package com.m3upro

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

private fun cleanName(s: String): String =
    s.replace(Regex("[\\u200B-\\u200F\\uFEFF]"), "").replace(Regex("\\s+"), " ").trim()

@Composable
private fun Item(text: String, onClick: () -> Unit) {
    DropdownMenuItem(text = { Text(text) }, onClick = onClick)
}

@Composable
private fun Field(label: String, v: String, onChange: (String) -> Unit) {
    OutlinedTextField(v, onChange, Modifier.fillMaxWidth(), label = { Text(label) }, singleLine = true)
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun EditorScreen(vm: MainVm, pl: Playlist, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val undoable by vm.canUndo.collectAsState()
    val allPls by vm.playlists.collectAsState()
    val others = allPls.filter { it.id != pl.id }
    val chans = pl.channels

    var q by remember { mutableStateOf("") }
    var filter by remember { mutableIntStateOf(0) }          // 0 all, 1 working, 2 dead, 3 unchecked, 4 duplicates
    var group by remember { mutableStateOf<String?>(null) }  // null = all groups, "" = ungrouped
    var sel by remember { mutableStateOf(setOf<Int>()) }
    var checkMenu by remember { mutableStateOf(false) }
    var toolsMenu by remember { mutableStateOf(false) }
    var editIdx by remember { mutableStateOf<Int?>(null) }
    var dlg by remember { mutableStateOf("") }
    var groupAction by remember { mutableStateOf<String?>(null) }
    var exportList by remember { mutableStateOf<List<Channel>>(emptyList()) }

    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("audio/x-mpegurl")) { uri ->
        if (uri != null) vm.exportTo(uri, M3u.export(exportList, ""))
    }
    fun export(list: List<Channel>) {
        exportList = list
        exporter.launch(pl.name.replace(Regex("[^\\w.-]"), "_") + ".m3u")
    }

    val dupUrls = remember(chans) { chans.groupingBy { it.url }.eachCount().filter { it.value > 1 }.keys }
    val groupCounts = remember(chans) { chans.groupingBy { it.group }.eachCount().toList().sortedBy { it.first.lowercase() } }
    val shown = remember(chans, q, filter, group, dupUrls) {
        chans.withIndex().filter { (_, c) ->
            (group == null || c.group == group) &&
                (q.isBlank() || c.name.contains(q, true) || c.url.contains(q, true)) &&
                when (filter) {
                    1 -> c.status == 1
                    2 -> c.status == 2
                    3 -> c.status == 0
                    4 -> c.url in dupUrls
                    else -> true
                }
        }.toList()
    }
    val ok = chans.count { it.status == 1 }
    val dead = chans.count { it.status == 2 }
    val unk = chans.size - ok - dead
    val dupCount = chans.count { it.url in dupUrls }

    fun act(f: (List<Channel>) -> List<Channel>) { vm.edit(pl.id, f); sel = emptySet() }
    fun targets(): Set<Int> = if (sel.isNotEmpty()) sel else shown.map { it.index }.toSet()
    fun mapTargets(f: (Channel) -> Channel) {
        val t = targets()
        act { l -> l.mapIndexed { i, c -> if (i in t) f(c) else c } }
    }
    fun toggle(i: Int) { sel = if (i in sel) sel - i else sel + i }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(pl.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${shown.size} of ${chans.size} channels", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                navigationIcon = { IconButton(onBack) { Icon(Icons.Default.ArrowBack, "Back") } },
                actions = {
                    TextButton({ vm.undo(pl.id) }, enabled = pl.id in undoable) { Text("Undo") }
                    Box {
                        IconButton({ checkMenu = true }) { Icon(Icons.Default.PlayArrow, "Check") }
                        DropdownMenu(checkMenu, { checkMenu = false }) {
                            Item("Check all channels") { checkMenu = false; vm.checkChannels(pl.id, null, false) }
                            Item("Check unchecked only") { checkMenu = false; vm.checkChannels(pl.id, null, true) }
                            Item("Check shown (${shown.size})") { checkMenu = false; vm.checkChannels(pl.id, shown.map { it.index }.toSet(), false) }
                            Item("Re-check dead only") { checkMenu = false; vm.checkChannels(pl.id, chans.withIndex().filter { it.value.status == 2 }.map { it.index }.toSet(), false) }
                            Item("Stop checking") { checkMenu = false; vm.cancelCheck() }
                        }
                    }
                    Box {
                        IconButton({ toolsMenu = true }) { Icon(Icons.Default.MoreVert, "Tools") }
                        DropdownMenu(toolsMenu, { toolsMenu = false }) {
                            Item("Add channel…") { toolsMenu = false; dlg = "add" }
                            Item("Find & replace…") { toolsMenu = false; dlg = "find" }
                            Item("Add prefix / suffix…") { toolsMenu = false; dlg = "affix" }
                            Item("Clean names") { toolsMenu = false; mapTargets { it.copy(name = cleanName(it.name)) }; vm.message.value = "Names cleaned" }
                            Item("Manage groups…") { toolsMenu = false; dlg = "groups" }
                            Item("Remove dead channels") { toolsMenu = false; act { l -> l.filter { it.status != 2 } } }
                            Item("Remove duplicates (same URL)") { toolsMenu = false; act { l -> l.distinctBy { it.url } } }
                            Item("Remove duplicates (same name)") { toolsMenu = false; act { l -> l.distinctBy { it.name.trim().lowercase() } } }
                            Item("Sort A–Z") { toolsMenu = false; act { l -> l.sortedBy { it.name.lowercase() } } }
                            Item("Sort by group") { toolsMenu = false; act { l -> l.sortedWith(compareBy<Channel>({ it.group.lowercase() }, { it.name.lowercase() })) } }
                            Item("Sort fastest first") { toolsMenu = false; act { l -> l.sortedWith(compareBy<Channel>({ it.status != 1 }, { if (it.ms == 0) Int.MAX_VALUE else it.ms })) } }
                            Item("Select all shown") { toolsMenu = false; sel = shown.map { it.index }.toSet() }
                            Item("Merge another playlist…") { toolsMenu = false; dlg = "merge" }
                            Item("Export all") { toolsMenu = false; export(chans) }
                            Item("Export working only") { toolsMenu = false; export(chans.filter { it.status == 1 }) }
                            Item("Export shown") { toolsMenu = false; export(shown.map { it.value }) }
                            if (pl.kind == "m3u" || pl.kind == "xtream" || pl.kind == "portal")
                                Item("Refresh from source…") { toolsMenu = false; dlg = "refresh" }
                            if (pl.info.isNotBlank()) Item("Account info") { toolsMenu = false; dlg = "info" }
                        }
                    }
                })
        },
        bottomBar = {
            if (sel.isNotEmpty()) BottomAppBar {
                Row(Modifier.horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
                    Text("${sel.size}", Modifier.padding(start = 16.dp, end = 4.dp), fontWeight = FontWeight.Bold)
                    TextButton({ dlg = "setgroup" }) { Text("Group") }
                    TextButton({ vm.checkChannels(pl.id, sel, false); sel = emptySet() }) { Text("Test") }
                    TextButton({
                        val s = sel
                        act { l -> l.filterIndexed { i, _ -> i in s } + l.filterIndexed { i, _ -> i !in s } }
                    }) { Text("To top") }
                    TextButton({ dlg = "copy" }) { Text("Copy to") }
                    TextButton({ val s = sel; act { l -> l.filterIndexed { i, _ -> i !in s } } }) { Text("Delete") }
                    IconButton({ sel = emptySet() }) { Icon(Icons.Default.Close, "Clear") }
                }
            }
        }
    ) { pad ->
        Column(Modifier.padding(pad)) {
            OutlinedTextField(q, { q = it }, Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                placeholder = { Text("Search name or URL") }, singleLine = true,
                shape = RoundedCornerShape(16.dp), leadingIcon = { Icon(Icons.Default.Search, null) })
            Box(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) { StatBar(ok, dead, unk) }
            LazyRow(contentPadding = PaddingValues(12.dp, 0.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item { FilterChip(filter == 0, { filter = 0 }, { Text("All ${chans.size}") }) }
                item { FilterChip(filter == 1, { filter = 1 }, { Text("✓ Working $ok") }) }
                item { FilterChip(filter == 2, { filter = 2 }, { Text("✕ Dead $dead") }) }
                item { FilterChip(filter == 3, { filter = 3 }, { Text("? Unchecked $unk") }) }
                item { FilterChip(filter == 4, { filter = 4 }, { Text("⧉ Duplicates $dupCount") }) }
            }
            if (groupCounts.size > 1) LazyRow(contentPadding = PaddingValues(12.dp, 0.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item { FilterChip(group == null, { group = null }, { Text("All groups") }) }
                items(groupCounts) { (g, n) ->
                    FilterChip(group == g, { group = if (group == g) null else g }, { Text((g.ifBlank { "Ungrouped" }) + " $n", maxLines = 1) })
                }
            }
            LazyColumn(Modifier.fillMaxSize()) {
                itemsIndexed(shown, key = { _, e -> e.index }) { _, (i, c) ->
                    val isSel = i in sel
                    val noteLine = if (c.note.isNotBlank()) c.note else if (c.status == 1) "OK" else "Not checked"
                    ListItem(
                        modifier = Modifier.combinedClickable(
                            onClick = { if (sel.isNotEmpty()) toggle(i) else editIdx = i },
                            onLongClick = { toggle(i) }),
                        colors = ListItemDefaults.colors(
                            containerColor = if (isSel) MaterialTheme.colorScheme.primaryContainer else Color.Transparent),
                        leadingContent = {
                            if (sel.isNotEmpty()) Checkbox(isSel, { toggle(i) }) else Logo(c, 40)
                        },
                        headlineContent = { Text(c.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        supportingContent = {
                            Text(c.group.ifBlank { "Ungrouped" } + " • " + noteLine, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        },
                        trailingContent = {
                            Column(horizontalAlignment = Alignment.End) {
                                Dot(c.status)
                                if (c.ms > 0) Text("${c.ms} ms", style = MaterialTheme.typography.labelSmall)
                            }
                        })
                }
            }
        }
    }

    // ------------------------------------------------------------ dialogs
    when (dlg) {
        "add" -> AddChannelDialog({ dlg = "" }) { c -> dlg = ""; act { it + c } }
        "find" -> FindReplaceDialog(targets().size, { dlg = "" }) { find, rep, scope, regex ->
            dlg = ""
            try {
                val rx = if (regex) Regex(find) else Regex(Regex.escape(find), RegexOption.IGNORE_CASE)
                val r = if (regex) rep else Regex.escapeReplacement(rep)
                mapTargets { c ->
                    when (scope) {
                        0 -> c.copy(name = rx.replace(c.name, r))
                        1 -> c.copy(group = rx.replace(c.group, r))
                        else -> c.copy(url = rx.replace(c.url, r), status = 0, ms = 0, note = "")
                    }
                }
            } catch (e: Exception) { vm.message.value = "Invalid pattern: ${e.message}" }
        }
        "affix" -> AffixDialog(targets().size, { dlg = "" }) { pre, suf ->
            dlg = ""
            mapTargets { it.copy(name = pre + it.name + suf) }
        }
        "setgroup" -> InputDialog("Set group for ${sel.size} channels", "Group name", "", "Apply", { dlg = "" }) { g ->
            dlg = ""
            val s = sel
            act { l -> l.mapIndexed { i, c -> if (i in s) c.copy(group = g.trim()) else c } }
        }
        "groups" -> AlertDialog(onDismissRequest = { dlg = "" }, title = { Text("Groups (${groupCounts.size})") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    groupCounts.forEach { (g, n) ->
                        ListItem(modifier = Modifier.combinedClickable(onClick = { dlg = ""; groupAction = g }),
                            headlineContent = { Text(g.ifBlank { "Ungrouped" }) },
                            trailingContent = { Text("$n") })
                    }
                }
            },
            confirmButton = {}, dismissButton = { TextButton({ dlg = "" }) { Text("Close") } })
        "merge" -> PickPlaylistDialog("Merge into this playlist", others, { dlg = "" }) { o -> dlg = ""; vm.mergeInto(pl.id, o.id) }
        "copy" -> PickPlaylistDialog("Copy ${sel.size} channels to", others, { dlg = "" }) { o ->
            dlg = ""
            val s = sel
            vm.copyTo(o.id, chans.filterIndexed { i, _ -> i in s })
            sel = emptySet()
        }
        "refresh" -> RefreshDialog(vm, pl) { dlg = "" }
        "info" -> AlertDialog(onDismissRequest = { dlg = "" }, title = { Text("Account info") },
            text = { Text(pl.info) }, confirmButton = { TextButton({ dlg = "" }) { Text("Close") } })
        else -> {}
    }

    groupAction?.let { g ->
        val n = chans.count { it.group == g }
        var rename by remember(g) { mutableStateOf(false) }
        if (rename) {
            InputDialog("Rename group", "New name (existing name merges)", g, "Rename", { groupAction = null }) { nn ->
                groupAction = null
                act { l -> l.map { if (it.group == g) it.copy(group = nn.trim()) else it } }
            }
        } else {
            AlertDialog(onDismissRequest = { groupAction = null }, title = { Text(g.ifBlank { "Ungrouped" } + " ($n)") },
                text = {
                    Column {
                        TextButton({ rename = true }) { Text("Rename / merge into another group") }
                        TextButton({ groupAction = null; act { l -> l.map { if (it.group == g) it.copy(group = "") else it } } }) { Text("Remove group (keep channels)") }
                        TextButton({ groupAction = null; act { l -> l.filter { it.group != g } } }) { Text("Delete group and its channels") }
                    }
                },
                confirmButton = {}, dismissButton = { TextButton({ groupAction = null }) { Text("Close") } })
        }
    }

    editIdx?.let { i ->
        chans.getOrNull(i)?.let { c ->
            EditChannelDialog(c, { editIdx = null },
                onSave = { nc -> editIdx = null; act { l -> l.mapIndexed { k, x -> if (k == i) nc else x } } },
                onTest = { editIdx = null; vm.checkChannels(pl.id, setOf(i), false) },
                onOpen = { vm.openExternal(ctx, pl, c) })
        }
    }
}

@Composable
private fun AddChannelDialog(onDismiss: () -> Unit, onAdd: (Channel) -> Unit) {
    var n by remember { mutableStateOf("") }
    var g by remember { mutableStateOf("") }
    var u by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Add channel") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Field("Name", n) { n = it }
                Field("Group", g) { g = it }
                Field("Stream URL", u) { u = it }
            }
        },
        confirmButton = { TextButton({ onAdd(Channel(name = n.trim(), url = u.trim(), group = g.trim())) }, enabled = n.isNotBlank() && u.isNotBlank()) { Text("Add") } },
        dismissButton = { TextButton(onDismiss) { Text("Cancel") } })
}

@Composable
private fun FindReplaceDialog(count: Int, onDismiss: () -> Unit, onApply: (String, String, Int, Boolean) -> Unit) {
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
                    FilterChip(scope == 1, { scope = 1 }, { Text("Groups") })
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
private fun AffixDialog(count: Int, onDismiss: () -> Unit, onApply: (String, String) -> Unit) {
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
private fun EditChannelDialog(c: Channel, onDismiss: () -> Unit, onSave: (Channel) -> Unit, onTest: () -> Unit, onOpen: () -> Unit) {
    var n by remember(c) { mutableStateOf(c.name) }
    var g by remember(c) { mutableStateOf(c.group) }
    var u by remember(c) { mutableStateOf(c.url) }
    var lg by remember(c) { mutableStateOf(c.logo) }
    var t by remember(c) { mutableStateOf(c.tvgId) }
    var ua by remember(c) { mutableStateOf(c.ua) }
    var rf by remember(c) { mutableStateOf(c.ref) }
    var d by remember(c) { mutableStateOf(c.drm) }
    var k by remember(c) { mutableStateOf(c.key) }
    AlertDialog(onDismissRequest = onDismiss,
        title = { Text("Edit channel") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (c.status != 0) Text("Last check: ${if (c.note.isBlank()) "OK" else c.note}" + if (c.ms > 0) " (${c.ms} ms)" else "",
                    style = MaterialTheme.typography.labelMedium, color = if (c.status == 1) Color(0xFF34D399) else Color(0xFFF87171))
                Field("Name", n) { n = it }
                Field("Group", g) { g = it }
                Field("Stream URL", u) { u = it }
                Field("Logo URL", lg) { lg = it }
                Field("TVG ID", t) { t = it }
                Field("User-Agent", ua) { ua = it }
                Field("Referer", rf) { rf = it }
                Field("DRM type (clearkey / widevine)", d) { d = it }
                Field("DRM key / license URL", k) { k = it }
            }
        },
        confirmButton = {
            TextButton({
                val same = u.trim() == c.url
                onSave(c.copy(name = n.trim(), group = g.trim(), url = u.trim(), logo = lg.trim(), tvgId = t.trim(),
                    ua = ua.trim(), ref = rf.trim(), drm = d.trim(), key = k.trim(),
                    status = if (same) c.status else 0, ms = if (same) c.ms else 0, note = if (same) c.note else ""))
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
