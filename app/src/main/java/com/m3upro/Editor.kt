package com.m3upro

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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

@Composable
private fun Item(text: String, onClick: () -> Unit) {
    DropdownMenuItem(text = { Text(text) }, onClick = onClick)
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChannelRow(c: Channel, selected: Boolean, selecting: Boolean, onClick: () -> Unit, onLong: () -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .background(if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f) else Color.Transparent)
            .combinedClickable(onClick = onClick, onLongClick = onLong)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (selecting) Checkbox(selected, null) else Logo(c, 42)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(c.name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(c.group.ifBlank { "Ungrouped" }, maxLines = 1, overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(8.dp))
        StatusPill(c)
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun EditorScreen(vm: MainVm, pl: Playlist, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val view by vm.view.collectAsState()
    val filter by vm.qFilter.collectAsState()
    val group by vm.qGroup.collectAsState()
    val undoable by vm.canUndo.collectAsState()
    val allPls by vm.playlists.collectAsState()
    val others = remember(allPls, pl.id) { allPls.filter { it.id != pl.id } }

    var text by remember { mutableStateOf("") }
    var sel by remember { mutableStateOf(setOf<Int>()) }
    var checkMenu by remember { mutableStateOf(false) }
    var toolsMenu by remember { mutableStateOf(false) }
    var editIdx by remember { mutableStateOf<Int?>(null) }
    var dlg by remember { mutableStateOf("") }
    var groupAction by remember { mutableStateOf<String?>(null) }
    var catSheet by remember { mutableStateOf(false) }
    var exportList by remember { mutableStateOf<List<Channel>>(emptyList()) }
    val listState = rememberLazyListState()
    LaunchedEffect(filter, group, text) { listState.scrollToItem(0) }

    val fileName = pl.name.replace(Regex("[^\\w.-]"), "_") + ".m3u"
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("audio/x-mpegurl")) { uri ->
        if (uri != null) vm.exportTo(uri, M3u.export(exportList, pl.epg))
    }
    fun export(list: List<Channel>) {
        exportList = list
        if (list.any { it.url.startsWith("portal://") }) dlg = "exportAsk" else exporter.launch(fileName)
    }

    fun toggle(i: Int) { sel = if (i in sel) sel - i else sel + i }
    fun act(f: (List<Channel>) -> List<Channel>) { vm.edit(pl.id, f); sel = emptySet() }
    fun shownIdx(): Set<Int> = view.rows.map { it.index }.toSet()
    fun targets(): Set<Int> = if (sel.isNotEmpty()) sel else shownIdx()
    fun mapTargets(f: (Channel) -> Channel) {
        val t = targets()
        act { l -> l.mapIndexed { i, c -> if (i in t) f(c) else c } }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (sel.isNotEmpty()) Text("${sel.size} selected")
                    else Column {
                        Text(pl.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${view.rows.size} of ${view.total} channels", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                navigationIcon = {
                    if (sel.isNotEmpty()) IconButton({ sel = emptySet() }) { Icon(Icons.Default.Close, "Clear selection") }
                    else IconButton(onBack) { Icon(Icons.Default.ArrowBack, "Back") }
                },
                actions = {
                    if (sel.isEmpty()) TextButton({ vm.undo(pl.id) }, enabled = pl.id in undoable) { Text("Undo") }
                    if (sel.isNotEmpty()) TextButton({ sel = shownIdx() }) { Text("All") }
                    Box {
                        IconButton({ checkMenu = true }) { Icon(Icons.Default.PlayArrow, "Check") }
                        DropdownMenu(checkMenu, { checkMenu = false }) {
                            if (sel.isNotEmpty()) Item("Check selected (${sel.size})") { checkMenu = false; vm.checkChannels(pl.id, sel, false); sel = emptySet() }
                            Item("Check all channels") { checkMenu = false; vm.checkChannels(pl.id, null, false) }
                            Item("Check unchecked only") { checkMenu = false; vm.checkChannels(pl.id, null, true) }
                            Item("Check shown (${view.rows.size})") { checkMenu = false; vm.checkChannels(pl.id, shownIdx(), false) }
                            Item("Re-check dead only") {
                                checkMenu = false
                                vm.checkChannels(pl.id, pl.channels.withIndex().filter { it.value.status == 2 }.map { it.index }.toSet(), false)
                            }
                            Item("Stop checking") { checkMenu = false; vm.cancelCheck() }
                        }
                    }
                    Box {
                        IconButton({ toolsMenu = true }) { Icon(Icons.Default.MoreVert, "Tools") }
                        DropdownMenu(toolsMenu, { toolsMenu = false }) {
                            Item("Smart organize (auto categories)…") { toolsMenu = false; dlg = "organize" }
                            Item("Manage categories…") { toolsMenu = false; catSheet = true }
                            Item("Quick fixes…") { toolsMenu = false; dlg = "quick" }
                            Item("Find & replace…") { toolsMenu = false; dlg = "find" }
                            Item("Add prefix / suffix…") { toolsMenu = false; dlg = "affix" }
                            HorizontalDivider()
                            Item("Remove dead channels") { toolsMenu = false; act { l -> l.filter { it.status != 2 } } }
                            Item("Remove duplicates (keep working)") {
                                toolsMenu = false
                                act { l ->
                                    fun rank(c: Channel) = if (c.status == 1) 0 else if (c.status == 0) 1 else 2
                                    val best = HashMap<String, Int>()
                                    l.forEachIndexed { i, c -> val cur = best[c.url]; if (cur == null || rank(c) < rank(l[cur])) best[c.url] = i }
                                    l.filterIndexed { i, c -> best[c.url] == i }
                                }
                            }
                            Item("Remove duplicates (same name)") { toolsMenu = false; act { l -> l.distinctBy { it.name.trim().lowercase() } } }
                            HorizontalDivider()
                            Item("Sort A–Z") { toolsMenu = false; act { l -> l.sortedBy { it.name.lowercase() } } }
                            Item("Sort by category") { toolsMenu = false; act { l -> l.sortedWith(compareBy<Channel>({ it.group.lowercase() }, { it.name.lowercase() })) } }
                            Item("Sort fastest first") { toolsMenu = false; act { l -> l.sortedWith(compareBy<Channel>({ it.status != 1 }, { if (it.ms == 0) Int.MAX_VALUE else it.ms })) } }
                            HorizontalDivider()
                            Item("Add channel…") { toolsMenu = false; dlg = "add" }
                            Item("Merge another playlist…") { toolsMenu = false; dlg = "merge" }
                            if (pl.kind == "portal") Item("Get real stream links") { toolsMenu = false; vm.resolveLinks(pl.id, null) }
                            Item("Export all") { toolsMenu = false; export(pl.channels) }
                            Item("Export working only") { toolsMenu = false; export(pl.channels.filter { it.status == 1 }) }
                            Item("Export shown") { toolsMenu = false; export(view.rows.map { it.ch }) }
                            if (pl.kind == "m3u" || pl.kind == "xtream" || pl.kind == "portal")
                                Item("Refresh from source…") { toolsMenu = false; dlg = "refresh" }
                            Item("Playlist settings…") { toolsMenu = false; dlg = "plsettings" }
                            if (pl.info.isNotBlank()) Item("Account info") { toolsMenu = false; dlg = "info" }
                        }
                    }
                })
        },
        bottomBar = {
            if (sel.isNotEmpty()) BottomAppBar {
                Row(Modifier.horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
                    TextButton({ dlg = "setgroup" }) { Text("Category") }
                    TextButton({ vm.checkChannels(pl.id, sel, false); sel = emptySet() }) { Text("Test") }
                    TextButton({
                        val s = sel
                        act { l -> l.filterIndexed { i, _ -> i in s } + l.filterIndexed { i, _ -> i !in s } }
                    }) { Text("To top") }
                    TextButton({ dlg = "copy" }) { Text("Copy to") }
                    TextButton({ val s = sel; act { l -> l.filterIndexed { i, _ -> i !in s } } }) { Text("Delete") }
                }
            }
        }
    ) { pad ->
        Column(Modifier.padding(pad)) {
            OutlinedTextField(text, { text = it; vm.setQuery(it) }, Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                placeholder = { Text("Search channels or categories") }, singleLine = true,
                shape = RoundedCornerShape(16.dp), leadingIcon = { Icon(Icons.Default.Search, null) },
                trailingIcon = { if (text.isNotEmpty()) IconButton({ text = ""; vm.setQuery("") }) { Icon(Icons.Default.Close, "Clear") } })

            if (text.isNotBlank() && view.matched.isNotEmpty()) {
                Text("Matching categories", Modifier.padding(start = 16.dp, top = 8.dp),
                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                LazyRow(contentPadding = PaddingValues(12.dp, 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(view.matched, key = { it.name }) { g ->
                        AssistChip({ vm.setGroup(g.name) }, { Text(g.name.ifBlank { "Ungrouped" } + "  ${g.count}", maxLines = 1) })
                    }
                }
            }

            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton({ catSheet = true }, Modifier.weight(1f), shape = RoundedCornerShape(14.dp)) {
                    Text("📂  " + (group?.ifBlank { "Ungrouped" } ?: "All categories") + "  ·  ${view.groups.size}",
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (group != null) IconButton({ vm.setGroup(null) }) { Icon(Icons.Default.Close, "Show all categories") }
            }

            LazyRow(contentPadding = PaddingValues(12.dp, 0.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item { FilterChip(filter == 0, { vm.setFilter(0) }, { Text("All ${view.total}") }) }
                item { FilterChip(filter == 1, { vm.setFilter(1) }, { Text("✓ Working ${view.ok}") }) }
                item { FilterChip(filter == 2, { vm.setFilter(2) }, { Text("✕ Dead ${view.dead}") }) }
                item { FilterChip(filter == 3, { vm.setFilter(3) }, { Text("? Unchecked ${view.unk}") }) }
                item { FilterChip(filter == 4, { vm.setFilter(4) }, { Text("⧉ Duplicates ${view.dup}") }) }
            }
            Box(Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) { StatBar(view.ok, view.dead, view.unk, 5) }

            LazyColumn(Modifier.fillMaxSize(), state = listState) {
                if (view.rows.isEmpty()) item {
                    Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(if (view.total == 0 && pl.channels.isNotEmpty()) "Loading…" else "No channels match", style = MaterialTheme.typography.titleSmall)
                        if (view.total > 0) Text("Try a different word, or clear the category / filter.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    }
                }
                items(view.rows, key = { it.index }) { r ->
                    ChannelRow(r.ch, r.index in sel, sel.isNotEmpty(),
                        onClick = { if (sel.isNotEmpty()) toggle(r.index) else editIdx = r.index },
                        onLong = { toggle(r.index) })
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }

    // ------------------------------------------------------------------ dialogs
    if (catSheet) CategorySheet(view.groups, view.total, group,
        onPick = { vm.setGroup(it); catSheet = false },
        onManage = { groupAction = it },
        onDismiss = { catSheet = false })

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
        "affix" -> AffixDialog(targets().size, { dlg = "" }) { pre, suf -> dlg = ""; mapTargets { it.copy(name = pre + it.name + suf) } }
        "quick" -> QuickFixDialog(targets().size, { dlg = "" }) { i ->
            dlg = ""
            val fix = Organize.QUICK[i].second
            mapTargets { it.copy(name = fix(it.name)) }
            vm.message.value = "Done"
        }
        "organize" -> OrganizeDialog(targets().size, { dlg = "" }) { onlyUngrouped ->
            dlg = ""
            val t = targets()
            act { l ->
                val g = Organize.autoGroup(l, onlyUngrouped)
                l.mapIndexed { i, c -> if (i in t) g[i] else c }
            }
            vm.message.value = "Categories updated"
        }
        "setgroup" -> InputDialog("Category for ${sel.size} channels", "Category name", "", "Apply", { dlg = "" }) { g ->
            dlg = ""
            val s = sel
            act { l -> l.mapIndexed { i, c -> if (i in s) c.copy(group = g.trim()) else c } }
        }
        "merge" -> PickPlaylistDialog("Merge into this playlist", others, { dlg = "" }) { o -> dlg = ""; vm.mergeInto(pl.id, o.id) }
        "copy" -> PickPlaylistDialog("Copy ${sel.size} channels to", others, { dlg = "" }) { o ->
            dlg = ""
            val s = sel
            vm.copyTo(o.id, pl.channels.filterIndexed { i, _ -> i in s })
            sel = emptySet()
        }
        "refresh" -> RefreshDialog(vm, pl) { dlg = "" }
        "plsettings" -> PlaylistSettingsDialog(pl, { dlg = "" }) { n, e -> dlg = ""; vm.setPlaylistInfo(pl.id, n, e) }
        "info" -> AlertDialog(onDismissRequest = { dlg = "" }, title = { Text("Account info") },
            text = { Text(pl.info) }, confirmButton = { TextButton({ dlg = "" }) { Text("Close") } })
        "exportAsk" -> AlertDialog(onDismissRequest = { dlg = "" }, title = { Text("Portal channels") },
            text = { Text("These channels still use portal commands, which only work inside this app. Get the real stream links first so the exported M3U works in any player.\n\nNote: portals usually make these links temporary.") },
            confirmButton = { TextButton({ dlg = ""; vm.resolveLinks(pl.id, null) }) { Text("Get real links") } },
            dismissButton = { TextButton({ dlg = ""; exporter.launch(fileName) }) { Text("Export anyway") } })
        else -> {}
    }

    groupAction?.let { g ->
        val n = view.groups.firstOrNull { it.name == g }?.count ?: 0
        GroupActionDialog(g, n, { groupAction = null },
            onRename = { nn -> groupAction = null; act { l -> l.map { if (it.group == g) it.copy(group = nn) else it } } },
            onUngroup = { groupAction = null; act { l -> l.map { if (it.group == g) it.copy(group = "") else it } } },
            onDelete = { groupAction = null; act { l -> l.filter { it.group != g } } })
    }

    editIdx?.let { i ->
        pl.channels.getOrNull(i)?.let { c ->
            EditChannelDialog(c, { editIdx = null },
                onSave = { nc -> editIdx = null; act { l -> l.mapIndexed { k, x -> if (k == i) nc else x } } },
                onTest = { editIdx = null; vm.checkChannels(pl.id, setOf(i), false) },
                onOpen = { vm.openExternal(ctx, pl, c) })
        }
    }
}
