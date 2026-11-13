package com.m3upro

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.UUID

class MainVm(app: Application) : AndroidViewModel(app) {
    private val store = Store(app)
    val playlists = MutableStateFlow(store.load())
    val busy = MutableStateFlow<String?>(null)
    val progress = MutableStateFlow(-1f)
    val message = MutableStateFlow<String?>(null)
    val canUndo = MutableStateFlow<Set<String>>(emptySet())
    val settings = MutableStateFlow(Settings(store.deepCheck, store.timeoutSec, store.concurrency))

    private val undoStacks = HashMap<String, ArrayDeque<List<Channel>>>()
    private val saveLock = Any()
    private var job: Job? = null

    init { startupCheck() }

    // ------------------------------------------------------------ state helpers
    private fun persist() {
        viewModelScope.launch(Dispatchers.IO) { synchronized(saveLock) { store.save(playlists.value) } }
    }

    fun update(id: String, f: (Playlist) -> Playlist) {
        playlists.value = playlists.value.map { if (it.id == id) f(it) else it }
        persist()
    }

    private fun refreshUndoFlag() {
        canUndo.value = undoStacks.filter { it.value.isNotEmpty() }.keys.toSet()
    }

    /** Every edit goes through here so it can be undone. */
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

    private fun addPlaylist(name: String, kind: String, source: String, meta: String, ch: List<Channel>, info: String = ""): String {
        val id = UUID.randomUUID().toString()
        playlists.value = playlists.value +
            Playlist(id, name, kind, source, meta, System.currentTimeMillis(), 1, ch, info)
        persist()
        return id
    }

    fun delete(id: String) {
        playlists.value = playlists.value.filter { it.id != id }
        undoStacks.remove(id)
        refreshUndoFlag()
        persist()
    }

    fun rename(id: String, name: String) { if (name.isNotBlank()) update(id) { it.copy(name = name.trim()) } }

    fun setSettings(s: Settings) {
        store.deepCheck = s.deep; store.timeoutSec = s.timeout; store.concurrency = s.concurrency
        settings.value = s
    }

    private fun portalCfg(p: Playlist): PortalCfg {
        val m = try { JSONObject(p.meta) } catch (e: Exception) { JSONObject() }
        return PortalCfg(p.source, m.optString("mac"), m.optString("d1"), m.optString("d2"), m.optString("sn"))
    }

    // ------------------------------------------------------------ adding sources
    fun addFromUrl(name: String, url: String) {
        viewModelScope.launch {
            busy.value = "Downloading playlist…"
            try {
                val ch = withContext(Dispatchers.Default) { M3u.parse(Net.get(url, true)) }
                if (ch.isEmpty()) error("No channels found in this playlist")
                val nm = name.ifBlank { url.substringAfterLast('/').substringBefore('?').substringBeforeLast('.').ifBlank { hostOf(url) } }
                addPlaylist(nm, "m3u", url, "", ch)
                message.value = "Added ${ch.size} channels"
            } catch (e: Exception) { message.value = "Failed: ${Net.friendly(e)}" }
            finally { busy.value = null }
        }
    }

    fun addFromText(name: String, text: String) {
        viewModelScope.launch {
            busy.value = "Parsing…"
            try {
                val ch = withContext(Dispatchers.Default) { M3u.parse(text) }
                if (ch.isEmpty()) error("No channels found in the text")
                addPlaylist(name.ifBlank { "Pasted playlist" }, "text", "", "", ch)
                message.value = "Added ${ch.size} channels"
            } catch (e: Exception) { message.value = "Failed: ${Net.friendly(e)}" }
            finally { busy.value = null }
        }
    }

    fun addFromUri(uri: Uri) {
        viewModelScope.launch {
            busy.value = "Reading file…"
            try {
                val ch = withContext(Dispatchers.IO) {
                    val t = getApplication<Application>().contentResolver.openInputStream(uri)!!
                        .bufferedReader().use { it.readText() }
                    M3u.parse(t)
                }
                if (ch.isEmpty()) error("No channels found in this file")
                val nm = uri.lastPathSegment?.substringAfterLast('/')?.substringBeforeLast('.') ?: "Local playlist"
                addPlaylist(nm.ifBlank { "Local playlist" }, "file", "", "", ch)
                message.value = "Added ${ch.size} channels"
            } catch (e: Exception) { message.value = "Failed: ${Net.friendly(e)}" }
            finally { busy.value = null }
        }
    }

    fun xtreamLogin(name: String, server: String, user: String, pass: String, ext: String, movies: Boolean) {
        viewModelScope.launch {
            busy.value = "Logging in to Xtream…"
            try {
                val r = Xtream.fetch(server, user, pass, ext, movies)
                if (r.channels.isEmpty()) error("No channels returned")
                val meta = JSONObject().put("user", user).put("pass", pass).put("ext", ext).put("movies", movies).toString()
                addPlaylist(name.ifBlank { "Xtream: " + hostOf(server) }, "xtream", server, meta, r.channels, r.info)
                message.value = "Added ${r.channels.size} channels"
            } catch (e: Exception) { message.value = "Xtream failed: ${Net.friendly(e)}" }
            finally { busy.value = null }
        }
    }

    fun portalLogin(name: String, cfg: PortalCfg, onDone: () -> Unit) {
        viewModelScope.launch {
            busy.value = "Logging in to portal…"
            try {
                val r = Stalker.fetch(cfg)
                val meta = JSONObject().put("mac", cfg.mac).put("d1", cfg.deviceId).put("d2", cfg.deviceId2).put("sn", cfg.serial).toString()
                addPlaylist(name.ifBlank { "Portal: " + hostOf(cfg.url) }, "portal", cfg.url, meta, r.channels, r.info)
                message.value = "Added ${r.channels.size} channels"
                onDone()
            } catch (e: Exception) { message.value = Net.friendly(e) }
            finally { busy.value = null }
        }
    }

    // ------------------------------------------------------------ refresh from source
    private suspend fun fetchFor(p: Playlist): Fetched = when (p.kind) {
        "m3u" -> Fetched(withContext(Dispatchers.Default) { M3u.parse(Net.get(p.source, true)) }, p.info)
        "xtream" -> {
            val m = try { JSONObject(p.meta) } catch (e: Exception) { JSONObject() }
            Xtream.fetch(p.source, m.optString("user"), m.optString("pass"), m.optString("ext", "m3u8"), m.optBoolean("movies"))
        }
        "portal" -> Stalker.fetch(portalCfg(p))
        else -> error("This playlist has no online source")
    }

    fun refresh(id: String, replace: Boolean) {
        viewModelScope.launch {
            val p = playlists.value.firstOrNull { it.id == id } ?: return@launch
            busy.value = "Refreshing ${p.name}…"
            try {
                val r = fetchFor(p)
                val before = p.channels.size
                edit(id) { old ->
                    if (replace) r.channels
                    else { val have = old.map { it.url }.toHashSet(); old + r.channels.filter { it.url !in have } }
                }
                update(id) { it.copy(info = r.info, state = 1, lastChecked = System.currentTimeMillis()) }
                val after = playlists.value.firstOrNull { it.id == id }?.channels?.size ?: before
                message.value = "Refreshed: $before → $after channels"
            } catch (e: Exception) { message.value = "Refresh failed: ${Net.friendly(e)}" }
            finally { busy.value = null }
        }
    }

    // ------------------------------------------------------------ launch check (source + account)
    private fun startupCheck() {
        viewModelScope.launch {
            for (p in playlists.value) verify(p)
            if (store.deepCheck) for (p in playlists.value) runCheck(p.id, null, false)
        }
    }

    private suspend fun verify(p: Playlist) {
        try {
            when (p.kind) {
                "m3u" -> {
                    val ok = Net.reachable(p.source)
                    update(p.id) { it.copy(state = if (ok) 1 else 2, lastChecked = System.currentTimeMillis()) }
                }
                "xtream" -> {
                    val m = try { JSONObject(p.meta) } catch (e: Exception) { JSONObject() }
                    val info = Xtream.account(p.source, m.optString("user"), m.optString("pass"))
                    update(p.id) { it.copy(state = 1, info = info, lastChecked = System.currentTimeMillis()) }
                }
                "portal" -> {
                    val info = Stalker.account(portalCfg(p))
                    update(p.id) { it.copy(state = 1, info = info, lastChecked = System.currentTimeMillis()) }
                }
                else -> {}
            }
        } catch (e: Exception) {
            update(p.id) { it.copy(state = 2, lastChecked = System.currentTimeMillis()) }
        }
    }

    fun recheckSources() { viewModelScope.launch { for (p in playlists.value) verify(p) } }

    // ------------------------------------------------------------ channel checker
    fun checkChannels(id: String, only: Set<Int>?, uncheckedOnly: Boolean) {
        job?.cancel()
        job = viewModelScope.launch { runCheck(id, only, uncheckedOnly) }
    }

    fun cancelCheck() { job?.cancel() }

    private suspend fun runCheck(id: String, only: Set<Int>?, uncheckedOnly: Boolean) {
        val p0 = playlists.value.firstOrNull { it.id == id } ?: return
        val targets = p0.channels.withIndex()
            .filter { (i, c) -> (only == null || i in only) && (!uncheckedOnly || c.status == 0) }
            .map { it.index to it.value.url }
        if (targets.isEmpty()) { message.value = "Nothing to check"; return }
        val st = settings.value
        val portal = if (p0.kind == "portal") portalCfg(p0) else null
        val total = targets.size
        var done = 0
        busy.value = "Checking ${p0.name}"
        progress.value = 0f
        try {
            for (chunk in targets.chunked(150)) {
                val cur = playlists.value.firstOrNull { it.id == id }?.channels ?: break
                val items = chunk.filter { (i, u) -> cur.getOrNull(i)?.url == u }
                if (items.isNotEmpty()) {
                    val res = Checker.run(items.map { cur[it.first] }, st.concurrency, st.timeout, portal)
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
        }
        val fin = playlists.value.firstOrNull { it.id == id }?.channels ?: return
        message.value = "Working ${fin.count { it.status == 1 }} • Dead ${fin.count { it.status == 2 }} • Unchecked ${fin.count { it.status == 0 }}"
    }

    // ------------------------------------------------------------ playlist operations
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
                val u = if (c.url.startsWith("portal://")) Stalker.resolve(c.url.removePrefix("portal://"), portalCfg(p)) else c.url
                val i = Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse(u), "video/*")
                ctx.startActivity(Intent.createChooser(i, "Open with").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } catch (e: Exception) { message.value = "Could not open: ${Net.friendly(e)}" }
        }
    }
}
