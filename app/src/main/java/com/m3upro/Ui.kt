package com.m3upro

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed as gridItemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
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

fun hm(t: Long): String = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(t))

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

data class PlayReq(val channels: List<Channel>, val index: Int)

@Composable
fun App(vm: MainVm) {
    val pls by vm.playlists.collectAsState()
    val busy by vm.busy.collectAsState()
    val prog by vm.progress.collectAsState()
    val msg by vm.message.collectAsState()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var openId by remember { mutableStateOf<String?>(null) }
    var portal by remember { mutableStateOf(false) }
    var play by remember { mutableStateOf<PlayReq?>(null) }
    val snack = remember { SnackbarHostState() }
    LaunchedEffect(msg) { msg?.let { snack.showSnackbar(it); vm.message.value = null } }
    BackHandler(play != null || openId != null || portal) {
        when { play != null -> play = null; portal -> portal = false; else -> openId = null }
    }
    val onPlay: (List<Channel>, Int) -> Unit = { l, i -> play = PlayReq(l, i) }

    Box(Modifier.fillMaxSize()) {
        val open = pls.firstOrNull { it.id == openId }
        val req = play
        when {
            req != null -> PlayerScreen(vm, req.channels, req.index) { play = null }
            portal -> PortalScreen(vm) { portal = false }
            open != null -> EditorScreen(vm, open, onPlay) { openId = null }
            else -> Scaffold(bottomBar = {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                    NavigationBarItem(tab == 0, { tab = 0 }, { Icon(Icons.Default.Home, null) }, label = { Text("Library") })
                    NavigationBarItem(tab == 1, { tab = 1 }, { Icon(Icons.Default.DateRange, null) }, label = { Text("Guide") })
                    NavigationBarItem(tab == 2, { tab = 2 }, { Icon(Icons.Default.Favorite, null) }, label = { Text("Favorites") })
                }
            }) { pad ->
                Box(Modifier.padding(pad)) {
                    when (tab) {
                        0 -> HomeScreen(vm, pls, { openId = it }, { portal = true })
                        1 -> GuideScreen(vm, pls, onPlay)
                        else -> FavoritesScreen(vm, pls, onPlay)
                    }
                }
            }
        }
        if (req == null) busy?.let {
            Surface(Modifier.align(Alignment.TopCenter).fillMaxWidth(), tonalElevation = 6.dp) {
                Column(Modifier.padding(12.dp)) {
                    Text(it, style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.height(6.dp))
                    if (prog >= 0f) LinearProgressIndicator(progress = { prog }, Modifier.fillMaxWidth())
                    else LinearProgressIndicator(Modifier.fillMaxWidth())
                }
            }
        }
        SnackbarHost(snack, Modifier.align(Alignment.BottomCenter).padding(bottom = if (req == null && open == null && !portal) 84.dp else 0.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(vm: MainVm, pls: List<Playlist>, onOpen: (String) -> Unit, onPortal: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    var more by remember { mutableStateOf(false) }
    var urlDlg by remember { mutableStateOf(false) }
    var del by remember { mutableStateOf<Playlist?>(null) }
    val deep by vm.deepCheck.collectAsState()
    val guide by vm.epg.collectAsState()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let(vm::addFromUri) }
    val fmt = remember { DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT) }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("M3U Pro", fontWeight = FontWeight.Bold) }, actions = {
                IconButton({ vm.autoCheck() }) { Icon(Icons.Default.Refresh, "Recheck") }
                Box {
                    IconButton({ more = true }) { Icon(Icons.Default.MoreVert, "More") }
                    DropdownMenu(more, { more = false }) {
                        DropdownMenuItem(text = { Text("Deep-check channels on launch") },
                            trailingIcon = { Checkbox(deep, null) }, onClick = { vm.setDeep(!deep) })
                    }
                }
            })
        },
        floatingActionButton = {
            Box {
                ExtendedFloatingActionButton({ menu = true }, icon = { Icon(Icons.Default.Add, null) }, text = { Text("Add") })
                DropdownMenu(menu, { menu = false }) {
                    DropdownMenuItem(text = { Text("From URL") }, onClick = { menu = false; urlDlg = true })
                    DropdownMenuItem(text = { Text("From file") }, onClick = { menu = false; picker.launch(arrayOf("*/*")) })
                    DropdownMenuItem(text = { Text("MAC portal login") }, onClick = { menu = false; onPortal() })
                }
            }
        }
    ) { pad ->
        LazyColumn(Modifier.padding(pad).fillMaxSize(), contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (pls.isEmpty()) item {
                Column(Modifier.fillMaxWidth().padding(40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("📺", fontSize = 48.sp)
                    Spacer(Modifier.height(8.dp))
                    Text("No playlists yet", style = MaterialTheme.typography.titleMedium)
                    Text("Add from URL, file, or MAC portal.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            items(pls, key = { it.id }) { p ->
                val ok = p.channels.count { it.status == 1 }
                val dead = p.channels.count { it.status == 2 }
                Card(onClick = { onOpen(p.id) }, Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Dot(p.state)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(p.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${p.channels.size} channels • $ok live • $dead dead" + if (guide.containsKey(p.id)) " • Guide ✓" else "",
                                style = MaterialTheme.typography.bodySmall)
                            Text("Checked ${fmt.format(Date(p.lastChecked))}", style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton({ del = p }) { Icon(Icons.Default.Delete, "Delete") }
                    }
                }
            }
            item { Spacer(Modifier.height(72.dp)) }
        }
    }

    if (urlDlg) {
        var name by remember { mutableStateOf("") }
        var url by remember { mutableStateOf("") }
        AlertDialog(onDismissRequest = { urlDlg = false }, title = { Text("Add playlist from URL") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(name, { name = it }, label = { Text("Name (optional)") }, singleLine = true)
                    OutlinedTextField(url, { url = it }, label = { Text("M3U URL") })
                }
            },
            confirmButton = { TextButton({ urlDlg = false; vm.addFromUrl(name, url.trim()) }, enabled = url.isNotBlank()) { Text("Add") } },
            dismissButton = { TextButton({ urlDlg = false }) { Text("Cancel") } })
    }
    del?.let { p ->
        AlertDialog(onDismissRequest = { del = null }, title = { Text("Delete \"${p.name}\"?") },
            confirmButton = { TextButton({ vm.delete(p.id); del = null }) { Text("Delete") } },
            dismissButton = { TextButton({ del = null }) { Text("Cancel") } })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavoritesScreen(vm: MainVm, pls: List<Playlist>, onPlay: (List<Channel>, Int) -> Unit) {
    val guide by vm.epg.collectAsState()
    val favs = remember(pls) { pls.flatMap { p -> p.channels.filter { it.fav } }.distinctBy { it.url } }
    val now = System.currentTimeMillis()
    Scaffold(topBar = { TopAppBar(title = { Text("Favorites", fontWeight = FontWeight.Bold) }) }) { pad ->
        if (favs.isEmpty()) Box(Modifier.padding(pad).fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Tap the heart on any channel to pin it here.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else LazyVerticalGrid(GridCells.Adaptive(170.dp), Modifier.padding(pad), contentPadding = PaddingValues(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            gridItemsIndexed(favs) { i, c ->
                val cur = remember(c, guide) { vm.progs(c).firstOrNull { now >= it.start && now < it.stop } }
                Card(onClick = { onPlay(favs, i) }, shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Column(Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Logo(c, 40); Spacer(Modifier.width(10.dp))
                            Text(c.name, maxLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                        }
                        Spacer(Modifier.height(10.dp))
                        Text(cur?.title ?: "No guide data", maxLines = 1, overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (cur != null) {
                            Spacer(Modifier.height(6.dp))
                            LinearProgressIndicator(progress = { ((now - cur.start).toFloat() / (cur.stop - cur.start)).coerceIn(0f, 1f) },
                                Modifier.fillMaxWidth())
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(vm: MainVm, pl: Playlist, onPlay: (List<Channel>, Int) -> Unit, onBack: () -> Unit) {
    var q by remember { mutableStateOf("") }
    var group by remember { mutableStateOf<String?>(null) }
    var sel by remember { mutableStateOf(setOf<Int>()) }
    var menu by remember { mutableStateOf(false) }
    var groupDlg by remember { mutableStateOf(false) }
    var edit by remember { mutableStateOf<Int?>(null) }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("audio/x-mpegurl")) { it?.let { u -> vm.export(u, pl) } }
    val groups = remember(pl.channels) { pl.channels.map { it.group }.filter { it.isNotBlank() }.distinct().sorted() }
    val shown = remember(pl.channels, q, group) {
        pl.channels.withIndex().filter { (_, c) ->
            (group == null || c.group == group) && (q.isBlank() || c.name.contains(q, true))
        }.toList()
    }
    fun setChannels(f: (List<Channel>) -> List<Channel>) { vm.update(pl.id) { it.copy(channels = f(it.channels)) }; sel = emptySet() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(pl.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { IconButton(onBack) { Icon(Icons.Default.ArrowBack, "Back") } },
                actions = {
                    IconButton({ vm.checkChannels(pl.id) }) { Icon(Icons.Default.Refresh, "Check channels") }
                    Box {
                        IconButton({ menu = true }) { Icon(Icons.Default.MoreVert, "More") }
                        DropdownMenu(menu, { menu = false }) {
                            DropdownMenuItem(text = { Text("Remove dead channels") }, onClick = { menu = false; setChannels { l -> l.filter { it.status != 2 } } })
                            DropdownMenuItem(text = { Text("Remove duplicates") }, onClick = { menu = false; setChannels { l -> l.distinctBy { it.url } } })
                            DropdownMenuItem(text = { Text("Sort A–Z") }, onClick = { menu = false; setChannels { l -> l.sortedBy { it.name.lowercase() } } })
                            DropdownMenuItem(text = { Text("Select all shown") }, onClick = { menu = false; sel = shown.map { it.index }.toSet() })
                            DropdownMenuItem(text = { Text("Export .m3u") }, onClick = { menu = false; export.launch(pl.name.replace(Regex("[^\\w.-]"), "_") + ".m3u") })
                        }
                    }
                })
        },
        bottomBar = {
            if (sel.isNotEmpty()) BottomAppBar {
                Text("${sel.size} selected", Modifier.padding(start = 16.dp).weight(1f))
                TextButton({ groupDlg = true }) { Text("Set group") }
                TextButton({ setChannels { l -> l.filterIndexed { i, _ -> i !in sel } } }) { Text("Delete") }
                IconButton({ sel = emptySet() }) { Icon(Icons.Default.Close, "Clear") }
            }
        }
    ) { pad ->
        Column(Modifier.padding(pad)) {
            OutlinedTextField(q, { q = it }, Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                placeholder = { Text("Search ${pl.channels.size} channels") }, singleLine = true,
                shape = RoundedCornerShape(16.dp), leadingIcon = { Icon(Icons.Default.Search, null) })
            if (groups.isNotEmpty()) LazyRow(contentPadding = PaddingValues(12.dp, 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item { FilterChip(group == null, { group = null }, { Text("All") }) }
                items(groups) { g -> FilterChip(group == g, { group = if (group == g) null else g }, { Text(g, maxLines = 1) }) }
            }
            LazyColumn(Modifier.fillMaxSize()) {
                itemsIndexed(shown, key = { _, it -> it.index }) { pos, (i, c) ->
                    ListItem(
                        modifier = Modifier.clickable { onPlay(shown.map { it.value }, pos) },
                        headlineContent = { Text(c.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        supportingContent = { Text(c.group.ifBlank { "No group" }, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        leadingContent = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(i in sel, { on -> sel = if (on) sel + i else sel - i })
                                Logo(c, 40)
                            }
                        },
                        trailingContent = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Dot(c.status)
                                IconButton({ vm.toggleFav(c.url) }) {
                                    Icon(if (c.fav) Icons.Default.Favorite else Icons.Default.FavoriteBorder, "Favorite",
                                        tint = if (c.fav) Color(0xFFFF6B8B) else LocalContentColor.current)
                                }
                                IconButton({ edit = i }) { Icon(Icons.Default.Edit, "Edit") }
                            }
                        })
                }
            }
        }
    }

    if (groupDlg) {
        var g by remember { mutableStateOf("") }
        AlertDialog(onDismissRequest = { groupDlg = false }, title = { Text("Set group for ${sel.size} channels") },
            text = { OutlinedTextField(g, { g = it }, label = { Text("Group name") }, singleLine = true) },
            confirmButton = { TextButton({ groupDlg = false; setChannels { l -> l.mapIndexed { i, c -> if (i in sel) c.copy(group = g) else c } } }) { Text("Apply") } },
            dismissButton = { TextButton({ groupDlg = false }) { Text("Cancel") } })
    }
    edit?.let { i ->
        pl.channels.getOrNull(i)?.let { c ->
            var n by remember(i) { mutableStateOf(c.name) }
            var g by remember(i) { mutableStateOf(c.group) }
            var u by remember(i) { mutableStateOf(c.url) }
            AlertDialog(onDismissRequest = { edit = null }, title = { Text("Edit channel") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(n, { n = it }, label = { Text("Name") })
                        OutlinedTextField(g, { g = it }, label = { Text("Group") })
                        OutlinedTextField(u, { u = it }, label = { Text("URL") })
                    }
                },
                confirmButton = { TextButton({ edit = null; setChannels { l -> l.mapIndexed { k, x -> if (k == i) x.copy(name = n, group = g, url = u, status = 0) else x } } }) { Text("Save") } },
                dismissButton = { TextButton({ edit = null }) { Text("Cancel") } })
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PortalScreen(vm: MainVm, onBack: () -> Unit) {
    val init = remember { vm.portalCfg() }
    var url by remember { mutableStateOf(init.url) }
    var mac by remember { mutableStateOf(init.mac) }
    var d1 by remember { mutableStateOf(init.deviceId) }
    var d2 by remember { mutableStateOf(init.deviceId2) }
    var sn by remember { mutableStateOf(init.serial) }
    val macOk = Regex("^([0-9A-Fa-f]{2}:){5}[0-9A-Fa-f]{2}$").matches(mac.trim())

    Scaffold(topBar = {
        TopAppBar(title = { Text("MAC portal login") },
            navigationIcon = { IconButton(onBack) { Icon(Icons.Default.ArrowBack, "Back") } })
    }) { pad ->
        Column(Modifier.padding(pad).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(url, { url = it }, Modifier.fillMaxWidth(), label = { Text("Portal URL") }, singleLine = true)
            OutlinedTextField(mac, { mac = it.uppercase() }, Modifier.fillMaxWidth(), label = { Text("MAC address") },
                placeholder = { Text("00:1A:79:XX:XX:XX") }, singleLine = true, isError = mac.isNotEmpty() && !macOk)
            OutlinedTextField(d1, { d1 = it }, Modifier.fillMaxWidth(), label = { Text("Device ID 1") }, singleLine = true)
            OutlinedTextField(d2, { d2 = it }, Modifier.fillMaxWidth(), label = { Text("Device ID 2") }, singleLine = true)
            OutlinedTextField(sn, { sn = it }, Modifier.fillMaxWidth(), label = { Text("Serial number") }, singleLine = true)
            Button(
                onClick = { vm.portalLogin(PortalCfg(url.trim(), mac.trim(), d1.trim(), d2.trim(), sn.trim()), onBack) },
                enabled = url.isNotBlank() && macOk, modifier = Modifier.fillMaxWidth().height(52.dp)
            ) { Text("Login & import channels") }
        }
    }
}
