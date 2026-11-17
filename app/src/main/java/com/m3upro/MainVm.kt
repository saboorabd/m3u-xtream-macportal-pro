package com.m3upro

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.UUID

data class Stats(val total: Int, val ok: Int, val dead: Int, val unk: Int)
class ListRow(val index: Int, val ch: Channel)
class GroupCount(val name: String, val count: Int, val ok: Int, val dead: Int)
class EditorView(
    val rows: List<ListRow>, val total: Int, val ok: Int, val dead: Int, val unk: Int, val dup: Int,
    val groups: List<GroupCount>, val matched: List<GroupCount>
) {
    companion object { val EMPTY = EditorView(emptyList(), 0, 0, 0, 0, 0, emptyList(), emptyList()) }
}
private class Inputs(val pls: List<Playlist>, val id: String, val text: String, val filter: Int, val group: String?)

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class MainVm(app: Application) : AndroidViewModel(app) {
    private val store = Store(app)

    val playlists = MutableStateFlow<List<Playlist>>(emptyList())
    val loading = MutableStateFlow(true)
    val busy = MutableStateFlow<String?>(null)
    val progress = MutableStateFlow(-1f)
    val message = MutableStateFlow<String?>(null)
    val canUndo = MutableStateFlow<Set<String>>(emptySet())
    val settings = MutableStateFlow(Settings(store.deepCheck, store.timeoutSec, store.concurrency, store.thorough))

    // editor query (text is debounced; everything is computed off the main thread)
    private val qId = MutableStateFlow("")
    val qText = MutableStateFlow("")
    val qFilter = MutableStateFlow(0)   // 0 all, 1 working, 2 dead, 3 unchecked, 4 duplicates
    val qGroup = MutableStateFlow<String?>(null)

    val stats: StateFlow<Map<String, Stats>> = playlists
        .map { l -> l.associate { p -> p.id to statsOf(p.channels) } }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    val view: StateFlow<EditorView> = combine(playlists, qId, qText.debounce(140), qFilter, qGroup) { pls, id, t, f, g ->
        Inputs(pls, id, t, f, g)
    }.conflate().map { compute(it) }.flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.Eagerly, EditorView.EMPTY)

    private val undoStacks = HashMap<String, ArrayDeque<List<Channel>>>()
    private val saveLock = Any()
    private var saveJob: Job? = null
    private var job: Job? = null

    init {
        viewModelScope.launch {
            val l = withContext(Dispatchers.IO) { store.load() }
            playlists.value = l
            loading.value = false
            startupCheck()
        }
    }

    // ------------------------------------------------------------------ derived data
    private fun statsOf(ch: List<Channel>): Stats {
        var ok = 0; var dead = 0
        for (c in ch) if (c.status == 1) ok++ else if (c.status == 2) dead++
        return Stats(ch.size, ok, dead, ch.size - ok - dead)
    }

    private fun compute(i: Inputs): EditorView {
        val p = i.pls.firstOrNull { it.id == i.id } ?: return EditorView.EMPTY
        val ch = p.channels
        val seen = HashSet<String>(ch.size * 2)
        val dupSet = HashSet<String>()
        for (c in ch) if (!seen.add(c.url)) dupSet.add(c.url)
        val gmap = LinkedHashMap<String, IntArray>()
        var ok = 0; var dead = 0
        for (c in ch) {
            val a = gmap.getOrPut(c.group) { IntArray(3) }
            a[0]++
            if (c.status == 1) { a[1]++; ok++ } else if (c.status == 2) { a[2]++; dead++ }
        }
        val groups = gmap.entries.map { GroupCount(it.key, it.value[0], it.value[1], it.value[2]) }
            .sortedBy { it.name.lowercase() }
        val tokens = i.text.lowercase().split(' ').filter { it.isNotBlank() }
        val matched = if (tokens.isEmpty()) emptyList() else groups.filter { g ->
            val n = g.name.lowercase().ifEmpty { "ungrouped" }
            tokens.all { n.contains(it) }
        }.take(14)
        val rows = ArrayList<ListRow>()
        for ((idx, c) in ch.withIndex()) {
            if (i.group != null && c.group != i.group) continue
            when (i.filter) {
                1 -> if (c.status != 1) continue
                2 -> if (c.status != 2) continue
                3 -> if (c.status != 0) continue
                4 -> if (c.url !in dupSet) continue
            }
            if (tokens.isNotEmpty()) {
                val hay = (c.name + " " + c.group).lowercase()
                if (!tokens.all { hay.contains(it) }) continue
            }
            rows.add(ListRow(idx, c))
        }
        return EditorView(rows, ch.size, ok, dead, ch.size - ok - dead, ch.count { it.url in dupSet }, groups, matched)
    }

    fun openEditor(id: String) {
        qText.value = ""; qFilter.value = 0; qGroup.value = null; qId.value = id
    }
    fun setQuery(t: String) { qText.value = t }
    fun setFilter(f: Int) { qFilter.value = f }
    fun setGroup(g: String?) { qGroup.value = g }

    // ------------------------------------------------------------------ state helpers
    private fun persist(immediate: Boolean = false) {
        saveJob?.cancel()
        saveJob = viewModelScope.launch(Dispatchers.IO) {
            if (!immediate) delay(500)
            synchronized(saveLock) { store.save(playlists.value) }
        }
    }

    override fun onCleared() {
        synchronized(saveLock) { store.save(playlists.value) }
    }

    fun update(id: String, f: (Playlist) -> Playlist) {
        playlists.update { l -> l.map { if (it.id == id) f(it) else it } }
        persist()
    }

    private fun refreshUndoFlag() { canUndo.value = undoStacks.filter { it.value.isNotEmpty() }.keys.toSet() }

    /** Every channel edit goes through here so it can be undone. */
    fun edit(id: String, f: (List<Channel>) -> List<Channel>) {
        val p = playlists.value.firstOrNull { it.id == id } ?: return
        val st = undoStacks.getOrPut(id) { ArrayDeque() }
        st.addLast(p.channels)
        if (st.size > 30) st.removeFirst()
        refreshUndoFlag()
        update(id) { it.copy(channels = f(it.channels)) }
    }

    fun undo(id: String) {
        val st = undoStacks[id] ?: return
        if (st.isEmpty()) return
        val prev = st.removeLast()
        refreshUndoFlag()
        update(id) { it.copy(channels = prev) }
        message.value = "Undone"
    }

    private fun addPlaylist(name: String, kind: String, source: String, meta: String, ch: List<Channel>, info: String = "", epg: String = "") {
        val id = UUID.randomUUID().toString()
        playlists.update { it + Playlist(id, name, kind, source, meta, System.currentTimeMillis(), 1, ch, info, epg) }
        persist(true)
    }

    fun delete(id: String) {
        playlists.update { l -> l.filter { it.id != id } }
        undoStacks.remove(id); refreshUndoFlag(); persist(true)
    }

    fun rename(id: String, name: String) { if (name.isNotBlank()) update(id) { it.copy(name = name.trim()) } }
    fun setPlaylistInfo(id: String, name: String, epg: String) {
        update(id) { it.copy(name = name.ifBlank { it.name }.trim(), epg = epg.trim()) }
    }

    fun setSettings(s: Settings) {
        store.deepCheck = s.deep; store.timeoutSec = s.timeout; store.concurrency = s.concurrency; store.thorough = s.thorough
        settings.value = s
    }

    private fun portalCfg(p: Playlist): PortalCfg {
        val m = try { JSONObject(p.meta) } catch (e: Exception) { JSONObject() }
        return PortalCfg(p.source, m.optString("mac"), m.optString("d1"), m.optString("d2"), m.optString("sn"))
    }

    private fun stage(s: String) { busy.value = s }

    // ------------------------------------------------------------------ adding sources
    fun addFromUrl(name: String, url: String) {
        viewModelScope.launch {
            busy.value = "Downloading playlist…"
            try {
                val p = withContext(Dispatchers.IO) { Net.http.open(url, emptyMap(), 120).use { M3u.parseStream(it) } }
                if (p.channels.isEmpty()) error("No channels found in this playlist")
                val nm = name.ifBlank { url.substringAfterLast('/').substringBefore('?').substringBeforeLast('.').ifBlank { hostOf(url) } }
                addPlaylist(nm, "m3u", url, "", p.channels, "", p.epg)
                message.value = "Added ${p.channels.size} channels"
            } catch (e: Exception) { message.value = "Could not load: ${Net.friendly(e)}" }
            finally { busy.value = null }
        }
    }

    fun addFromText(name: String, text: String) {
        viewModelScope.launch {
            busy.value = "Reading playlist…"
            try {
                val p = withContext(Dispatchers.Default) { M3u.parseReader(java.io.BufferedReader(java.io.StringReader(text))) }
                if (p.channels.isEmpty()) error("No channels found in the text")
                addPlaylist(name.ifBlank { "Pasted playlist" }, "text", "", "", p.channels, "", p.epg)
                message.value = "Added ${p.channels.size} channels"
            } catch (e: Exception) { message.value = "Could not read: ${Net.friendly(e)}" }
            finally { busy.value = null }
        }
    }

    fun addFromUri(uri: Uri) {
        viewModelScope.launch {
            busy.value = "Reading file…"
            try {
                val p = withContext(Dispatchers.IO) {
                    getApplication<Application>().contentResolver.openInputStream(uri)!!.use { M3u.parseStream(it) }
                }
                if (p.channels.isEmpty()) error("No channels found in this file")
                val nm = uri.lastPathSegment?.substringAfterLast('/')?.substringBeforeLast('.') ?: "Local playlist"
                addPlaylist(nm.ifBlank { "Local playlist" }, "file", "", "", p.channels, "", p.epg)
                message.value = "Added ${p.channels.size} channels"
            } catch (e: Exception) { message.value = "Could not read: ${Net.friendly(e)}" }
            finally { busy.value = null }
        }
    }

    fun xtreamLogin(name: String, server: String, user: String, pass: String, ext: String, movies: Boolean, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            busy.value = "Logging in…"
            try {
                val r = withContext(Dispatchers.IO) { Xtream.fetch(Net.http, server, user, pass, ext, movies) { stage(it) } }
                if (r.channels.isEmpty()) error("No channels returned")
                val meta = JSONObject().put("user", user).put("pass", pass).put("ext", ext).put("movies", movies).toString()
                addPlaylist(name.ifBlank { "Xtream: " + hostOf(server) }, "xtream", Xtream.base(server), meta, r.channels, r.info, "")
                message.value = "Added ${r.channels.size} channels"
                onDone()
            } catch (e: Exception) { message.value = Net.friendly(e) }
            finally { busy.value = null }
        }
    }

    fun portalLogin(name: String, cfg: PortalCfg, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            busy.value = "Connecting to portal…"
            try {
                val r = withContext(Dispatchers.IO) { Stalker.fetch(Net.http, cfg) { stage(it) } }
                val meta = JSONObject().put("mac", cfg.mac).put("d1", cfg.deviceId).put("d2", cfg.deviceId2).put("sn", cfg.serial).toString()
                addPlaylist(name.ifBlank { "Portal: " + hostOf(cfg.url) }, "portal", cfg.url, meta, r.channels, r.info, "")
                message.value = "Added ${r.channels.size} channels"
                onDone()
            } catch (e: Exception) { message.value = Net.friendly(e) }
            finally { busy.value = null }
        }
    }

    /** "Paste anything": routes to the right importer. */
    fun smartAdd(s: Smart, rawText: String, onDone: () -> Unit = {}) {
        when (s.kind) {
            "portal" -> portalLogin(s.name, PortalCfg(s.url, s.mac, s.d1, s.d2, s.sn), onDone)
            "xtream" -> xtreamLogin(s.name, s.url, s.user, s.pass, "m3u8", false, onDone)
            "m3u" -> { addFromUrl(s.name, s.url); onDone() }
            "text" -> { addFromText(s.name, rawText); onDone() }
            else -> message.value = "Could not recognise this. Paste a link or portal details."
        }
    }

    // ------------------------------------------------------------------ refresh
    private fun fetchFor(p: Playlist): Fetched = when (p.kind) {
        "m3u" -> { val r = Net.http.open(p.source, emptyMap(), 120).use { M3u.parseStream(it) }; Fetched(r.channels, p.info, r.epg) }
        "xtream" -> {
            val m = try { JSONObject(p.meta) } catch (e: Exception) { JSONObject() }
            Xtream.fetch(Net.http, p.source, m.optString("user"), m.optString("pass"), m.optString("ext", "m3u8"), m.optBoolean("movies")) { stage(it) }
        }
        "portal" -> Stalker.fetch(Net.http, portalCfg(p)) { stage(it) }
        else -> error("This playlist has no online source")
    }

    fun refresh(id: String, replace: Boolean) {
        viewModelScope.launch {
            val p = playlists.value.firstOrNull { it.id == id } ?: return@launch
            busy.value = "Refreshing ${p.name}…"
            try {
                val r = withContext(Dispatchers.IO) { fetchFor(p) }
                val before = p.channels.size
                edit(id) { old ->
                    if (replace) r.channels
                    else {
                        val have = old.map { it.raw.ifBlank { it.url } }.toHashSet()
                        old + r.channels.filter { it.url !in have }
                    }
                }
                update(id) { it.copy(info = r.info.ifBlank { it.info }, epg = r.epg.ifBlank { it.epg }, state = 1, lastChecked = System.currentTimeMillis()) }
                persist(true)
                val after = playlists.value.firstOrNull { it.id == id }?.channels?.size ?: before
                message.value = "Refreshed: $before → $after channels"
            } catch (e: Exception) { message.value = "Refresh failed: ${Net.friendly(e)}" }
            finally { busy.value = null }
        }
    }

    // ------------------------------------------------------------------ launch check (source + account)
    private suspend fun startupCheck() {
        coroutineScope {
            playlists.value.map { p -> async(Dispatchers.IO) { verify(p) } }.awaitAll()
        }
        if (store.deepCheck) for (p in playlists.value) runCheck(p.id, null, false)
    }

    private fun verify(p: Playlist) {
        try {
            when (p.kind) {
                "m3u" -> {
                    val ok = Net.reachable(p.source)
                    update(p.id) { it.copy(state = if (ok) 1 else 2, lastChecked = System.currentTimeMillis()) }
                }
                "xtream" -> {
                    val m = try { JSONObject(p.meta) } catch (e: Exception) { JSONObject() }
                    val info = Xtream.account(Net.http, p.source, m.optString("user"), m.optString("pass"))
                    update(p.id) { it.copy(state = 1, info = info, lastChecked = System.currentTimeMillis()) }
                }
                "portal" -> {
                    val info = Stalker.account(Net.http, portalCfg(p))
                    update(p.id) { it.copy(state = 1, info = info, lastChecked = System.currentTimeMillis()) }
                }
                else -> {}
            }
        } catch (e: Exception) {
            update(p.id) { it.copy(state = 2, lastChecked = System.currentTimeMillis()) }
        }
    }

    fun recheckSources() {
        viewModelScope.launch {
            busy.value = "Checking sources…"
            try { coroutineScope { playlists.value.map { p -> async(Dispatchers.IO) { verify(p) } }.awaitAll() } }
            finally { busy.value = null }
        }
    }

    // ------------------------------------------------------------------ channel checker
    fun checkChannels(id: String, only: Set<Int>?, uncheckedOnly: Boolean) {
        job?.cancel()
        job = viewModelScope.launch { runCheck(id, only, uncheckedOnly) }
    }

    fun cancelCheck() {
        if (busy.value != null) busy.value = "Stopping…"
        job?.cancel()
    }

    private fun checkOne(c: Channel, st: Settings, portal: PortalCfg?): CheckResult = try {
        var u = c.url
        if (u.startsWith("portal://")) {
            if (portal == null) CheckResult(0, 0, "Portal login missing")
            else {
                u = Stalker.resolve(Net.http, u.removePrefix("portal://"), portal)
                Probe.check(Net.http, u, c.ua, c.ref, st.timeout, st.thorough)
            }
        } else Probe.check(Net.http, u, c.ua, c.ref, st.timeout, st.thorough)
    } catch (e: CancellationException) { throw e } catch (e: Exception) { CheckResult(2, 0, Errs.friendly(e)) }

    private suspend fun runCheck(id: String, only: Set<Int>?, uncheckedOnly: Boolean) {
        val p0 = playlists.value.firstOrNull { it.id == id } ?: return
        val targets = p0.channels.withIndex()
            .filter { (i, c) -> (only == null || i in only) && (!uncheckedOnly || c.status == 0) }
            .map { it.index to it.value.url }
        if (targets.isEmpty()) { message.value = "Nothing to check"; return }
        val st = settings.value
        val portal = if (p0.kind == "portal") portalCfg(p0) else null
        val conc = if (portal != null) minOf(st.concurrency, 6) else st.concurrency
        val sem = Semaphore(conc.coerceAtLeast(1))
        val total = targets.size
        var done = 0
        busy.value = "Checking ${p0.name}"
        progress.value = 0f
        try {
            if (portal != null) withContext(Dispatchers.IO) { Stalker.session(Net.http, portal) }
            for (chunk in targets.chunked(120)) {
                val cur = playlists.value.firstOrNull { it.id == id }?.channels ?: break
                val items = chunk.filter { (i, u) -> cur.getOrNull(i)?.url == u }
                if (items.isNotEmpty()) {
                    val res = coroutineScope {
                        items.map { (i, _) ->
                            val ch = cur[i]
                            async(Dispatchers.IO) { sem.withPermit { checkOne(ch, st, portal) } }
                        }.awaitAll()
                    }
                    update(id) { p ->
                        val arr = p.channels.toMutableList()
                        items.forEachIndexed { k, (i, u) ->
                            if (arr.getOrNull(i)?.url == u) arr[i] = arr[i].copy(status = res[k].status, ms = res[k].ms, note = res[k].note)
                        }
                        p.copy(channels = arr, lastChecked = System.currentTimeMillis())
                    }
                }
                done += chunk.size
                progress.value = done / total.toFloat()
            }
        } finally {
            busy.value = null
            progress.value = -1f
            persist(true)
        }
        val fin = playlists.value.firstOrNull { it.id == id }?.channels ?: return
        message.value = "Working ${fin.count { it.status == 1 }} • Dead ${fin.count { it.status == 2 }} • Unchecked ${fin.count { it.status == 0 }}"
    }

    /** MAC portal: replace portal://cmd with the real stream URL (original kept in [Channel.raw]). */
    fun resolveLinks(id: String, only: Set<Int>?) {
        job?.cancel()
        job = viewModelScope.launch {
            val p0 = playlists.value.firstOrNull { it.id == id } ?: return@launch
            if (p0.kind != "portal") { message.value = "Only needed for MAC portal playlists"; return@launch }
            val cfg = portalCfg(p0)
            val targets = p0.channels.withIndex()
                .filter { (i, c) -> c.url.startsWith("portal://") && (only == null || i in only) }
                .map { it.index to it.value.url }
            if (targets.isEmpty()) { message.value = "Links are already resolved"; return@launch }
            busy.value = "Getting real stream links…"
            progress.value = 0f
            var done = 0; var failed = 0
            try {
                withContext(Dispatchers.IO) { Stalker.session(Net.http, cfg) }
                val sem = Semaphore(6)
                for (chunk in targets.chunked(60)) {
                    val res = coroutineScope {
                        chunk.map { (i, u) ->
                            async(Dispatchers.IO) {
                                sem.withPermit {
                                    try { Triple(i, u, Stalker.resolve(Net.http, u.removePrefix("portal://"), cfg)) }
                                    catch (e: CancellationException) { throw e }
                                    catch (e: Exception) { Triple(i, u, "") }
                                }
                            }
                        }.awaitAll()
                    }
                    failed += res.count { it.third.isEmpty() }
                    update(id) { p ->
                        val arr = p.channels.toMutableList()
                        for ((i, u, real) in res) if (real.isNotEmpty() && arr.getOrNull(i)?.url == u) arr[i] = arr[i].copy(url = real, raw = u)
                        p.copy(channels = arr)
                    }
                    done += chunk.size
                    progress.value = done / targets.size.toFloat()
                }
            } finally {
                busy.value = null; progress.value = -1f; persist(true)
            }
            message.value = "Resolved ${targets.size - failed} links" + if (failed > 0) " • $failed failed" else ""
        }
    }

    // ------------------------------------------------------------------ playlist operations
    fun mergeInto(targetId: String, sourceId: String) {
        val src = playlists.value.firstOrNull { it.id == sourceId }?.channels ?: return
        edit(targetId) { old ->
            val have = old.map { it.url }.toHashSet()
            old + src.filter { it.url !in have }
        }
        message.value = "Merged"
    }

    fun copyTo(targetId: String, chs: List<Channel>) {
        edit(targetId) { old ->
            val have = old.map { it.url }.toHashSet()
            old + chs.filter { it.url !in have }
        }
        message.value = "Copied ${chs.size} channels"
    }

    fun exportTo(uri: Uri, text: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                getApplication<Application>().contentResolver.openOutputStream(uri)!!
                    .bufferedWriter().use { it.write(text) }
                message.value = "Exported"
            } catch (e: Exception) { message.value = "Export failed" }
        }
    }

    /** Opens a channel in any installed player (VLC, MX Player ...). */
    fun openExternal(ctx: Context, p: Playlist, c: Channel) {
        viewModelScope.launch {
            try {
                val u = if (c.url.startsWith("portal://"))
                    withContext(Dispatchers.IO) { Stalker.resolve(Net.http, c.url.removePrefix("portal://"), portalCfg(p)) } else c.url
                val i = Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse(u), "video/*")
                ctx.startActivity(Intent.createChooser(i, "Open with").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } catch (e: Exception) { message.value = "Could not open: ${Net.friendly(e)}" }
        }
    }
}
