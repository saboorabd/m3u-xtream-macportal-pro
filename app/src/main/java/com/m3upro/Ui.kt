package com.m3upro

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage

const val APP_VERSION = "3.1"

object Pal {
    val ok = Color(0xFF34D399)
    val bad = Color(0xFFF87171)
    val warn = Color(0xFFFBBF24)
    val unk = Color(0xFF64748B)
    val violet = Color(0xFF8B7BFF)
    val blue = Color(0xFF6EA8FF)
    val g1 = Color(0xFF1B3A78)
    val g2 = Color(0xFF3A2A7A)
}

private val AppType = Typography(
    headlineSmall = TextStyle(fontSize = 26.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.4).sp),
    titleLarge = TextStyle(fontSize = 21.sp, fontWeight = FontWeight.Bold),
    titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
    titleSmall = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 15.sp),
    bodyMedium = TextStyle(fontSize = 14.sp),
    bodySmall = TextStyle(fontSize = 12.5.sp),
    labelLarge = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
    labelMedium = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium)
)

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
        ),
        typography = AppType,
        content = content
    )
}

// ------------------------------------------------------------------ building blocks
@Composable
fun Ic(id: Int, tint: Color = LocalContentColor.current, size: Dp = 22.dp, modifier: Modifier = Modifier) {
    Icon(painterResource(id), null, modifier.size(size), tint)
}

@Composable
fun LogoMark(size: Dp) {
    Box(
        Modifier.size(size).clip(RoundedCornerShape(size * 0.28f))
            .background(Brush.linearGradient(listOf(Pal.g1, Pal.g2))),
        contentAlignment = Alignment.Center
    ) {
        Image(painterResource(R.drawable.ic_launcher_foreground), null, Modifier.fillMaxSize().scale(1.55f))
    }
}

@Composable
fun SoftCard(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    val base = modifier.clip(shape)
        .background(MaterialTheme.colorScheme.surfaceVariant)
        .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.55f), shape)
    Column(if (onClick != null) base.clickable(onClick = onClick) else base, content = content)
}

@Composable
fun GradientButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, icon: Int? = null) {
    val shape = RoundedCornerShape(16.dp)
    val bg = if (enabled) Brush.horizontalGradient(listOf(Color(0xFF5B8DEF), Color(0xFF8B7BFF)))
    else Brush.horizontalGradient(listOf(Color(0xFF232C40), Color(0xFF232C40)))
    Row(
        modifier.height(52.dp).clip(shape).background(bg).clickable(enabled = enabled, onClick = onClick).padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) { Ic(icon, if (enabled) Color.White else Color(0xFF6B7690), 20.dp); Spacer(Modifier.width(8.dp)) }
        Text(text, color = if (enabled) Color.White else Color(0xFF6B7690), fontWeight = FontWeight.Bold)
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(text.uppercase(), modifier.padding(top = 6.dp), style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant, letterSpacing = 1.2.sp)
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
    val total = (ok + dead + unk).coerceAtLeast(1).toFloat()
    val a by animateFloatAsState(ok / total, label = "ok")
    val b by animateFloatAsState(dead / total, label = "dead")
    val c by animateFloatAsState(unk / total, label = "unk")
    Row(Modifier.fillMaxWidth().height(height.dp).clip(RoundedCornerShape(6.dp)).background(Color(0xFF222C40))) {
        if (a > 0.001f) Box(Modifier.weight(a).fillMaxHeight().background(Pal.ok))
        if (b > 0.001f) Box(Modifier.weight(b).fillMaxHeight().background(Pal.bad))
        if (c > 0.001f) Box(Modifier.weight(c).fillMaxHeight().background(Color(0xFF3B475E)))
    }
}

@Composable
fun KindBadge(kind: String) {
    val (icon, color) = when (kind) {
        "xtream" -> R.drawable.ic_server to Pal.violet
        "portal" -> R.drawable.ic_router to Pal.warn
        "file" -> R.drawable.ic_folder to Pal.blue
        "text" -> R.drawable.ic_paste to Pal.blue
        else -> R.drawable.ic_link to Pal.ok
    }
    Box(Modifier.size(46.dp).clip(RoundedCornerShape(14.dp)).background(color.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
        Ic(icon, color, 24.dp)
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

// ------------------------------------------------------------------ app shell (bottom navigation)
@Composable
fun App(vm: MainVm) {
    val pls by vm.playlists.collectAsState()
    val loading by vm.loading.collectAsState()
    val busy by vm.busy.collectAsState()
    val prog by vm.progress.collectAsState()
    val msg by vm.message.collectAsState()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var openId by remember { mutableStateOf<String?>(null) }
    var portalOpen by remember { mutableStateOf(false) }
    var portalInit by remember { mutableStateOf<Smart?>(null) }
    val snack = remember { SnackbarHostState() }

    LaunchedEffect(msg) { msg?.let { snack.showSnackbar(it); vm.message.value = null } }
    LaunchedEffect(loading) { if (!loading && pls.isEmpty()) tab = 1 }
    BackHandler(openId != null || portalOpen || tab != 0) {
        if (portalOpen) portalOpen = false else if (openId != null) openId = null else tab = 0
    }
    val open = pls.firstOrNull { it.id == openId }
    val showNav = !loading && !portalOpen && open == null

    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        LogoMark(72.dp)
                        Spacer(Modifier.height(16.dp))
                        CircularProgressIndicator(Modifier.size(26.dp), strokeWidth = 3.dp)
                        Spacer(Modifier.height(10.dp))
                        Text("Loading your playlists…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                portalOpen -> PortalScreen(vm, portalInit) { portalOpen = false }
                open != null -> EditorScreen(vm, open) { openId = null }
                tab == 0 -> LibraryScreen(vm, pls, onOpen = { vm.openEditor(it); openId = it }, onGoAdd = { tab = 1 })
                tab == 1 -> AddScreen(vm, onPortal = { portalInit = it; portalOpen = true }, onAdded = { tab = 0 })
                else -> SettingsScreen(vm)
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
        if (showNav) NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
            NavigationBarItem(tab == 0, { tab = 0 }, { Ic(R.drawable.ic_library) }, label = { Text("Library") })
            NavigationBarItem(tab == 1, { tab = 1 }, { Icon(Icons.Default.Add, null) }, label = { Text("Add") })
            NavigationBarItem(tab == 2, { tab = 2 }, { Icon(Icons.Default.Settings, null) }, label = { Text("Settings") })
        }
    }
}
