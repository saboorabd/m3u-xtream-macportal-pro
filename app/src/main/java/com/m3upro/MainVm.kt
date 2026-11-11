package com.m3upro

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import java.util.UUID

class MainVm(app: Application) : AndroidViewModel(app) {
    private val store = Store(app)
    val playlists = MutableStateFlow(store.load())
    val busy = MutableStateFlow<String?>(null)
    val progress = MutableStateFlow(-1f)
    val message = MutableStateFlow<String?>(null)
    val deepCheck = MutableStateFlow(store.deepCheck)
    val epg = MutableStateFlow>(emptyMap())
    val epgBusy = MutableStateFlow(false)
    val recents = MutableStateFlow(store.loadRecents())
    val buffer = MutableStateFlow(store.buffer)

    init { autoCheck(); playlists.value.forEach { if (hasGuide(it)) loadEpg(it.id) } }

    private fun hasGuide(p: Playlist) = p.epgUrl.isNotBlank() || p.source.startsWith("portal:")

    fun setDeep(v: Boolean) { store.deepCheck = v; deepCheck.value = v }
    fun portalCfg() = store.loadPortal()

    private fun persist() {
        val snap = playlists.value
        viewModelScope.launch(Dispatchers.IO) { store.save(snap) }
    }

    fun update(id: String, f: (Playlist) -> Playlist) {
        playlists.value = playlists.value.map { if (it.id == id) f(it) else it }
        persist()
    }

    private fun add(name: String, source: String, ch: List<Channel>, epgUrl: String = "") {
        val id = UUID.randomUUID().toString()
        playlists.value = playlists.value +
            Playlist(id, name, source, System.currentTimeMillis(), 1, ch, epgUrl)
        persist()
        if (epgUrl.isNotBlank() || source.startsWith("portal:")) loadEpg(id)
    }

    fun delete(id: String) { playlists.value = playlists.value.filter { it.id != id }; persist() }

    fun addFromUrl(name: String, url: String) {
        viewModelScope.launch {
            busy.value = "Loading playlist…"
            try {
                val (ch, e) = withContext(Dispatchers.Default) {
                    val t = Net.get(url); M3u.parse(t) to M3u.epgUrl(t)
                }
                if (ch.isEmpty()) error("No channels found")
                add(name.ifBlank { url.substringAfterLast('/').substringBefore('?').ifBlank { "Playlist" } }, url, ch, e)
            } catch (e: Exception) { message.value = "Failed: ${e.message}" }
            finally { busy.value = null }
        }
    }

    fun addFromUri(uri: Uri) {
        viewModelScope.launch {
            busy.value = "Reading file…"
            try {
                val (ch, e) = withContext(Dispatchers.IO) {
                    val t = getApplication<Application>().contentResolver.openInputStream(uri)!!
                        .bufferedReader().use { it.readText() }
                    M3u.parse(t) to M3u.epgUrl(t)
                }
                if (ch.isEmpty()) error("No channels found")
                add("Local playlist", "file", ch, e)
            } catch (e: Exception) { message.value = "Failed: ${e.message}" }
            finally { busy.value = null }
        }
    }

    fun portalLogin(cfg: PortalCfg, onDone: () -> Unit) {
        viewModelScope.launch {
            busy.value = "Logging in to portal…"
            try {
                store.savePortal(cfg)
                val ch = Stalker.login(cfg)
                if (ch.isEmpty()) error("No channels returned")
                val host = cfg.url.substringAfter("://").substringBefore("/")
                add("Portal: $host", "portal:${cfg.url}", ch)
                onDone()
            } catch (e: Exception) { message.value = "Login failed: ${e.message}" }
            finally { busy.value = null }
        }
    }

    fun xtreamLogin(server: String, user: String, pass: String) {
        viewModelScope.launch {
            busy.value = "Logging in to Xtream…"
            try {
                val (ch, epgUrl) = Xtream.login(server, user, pass)
                if (ch.isEmpty()) error("No channels returned")
                add("Xtream: " + server.substringAfter("://").substringBefore("/"), "xtream:$server", ch, epgUrl)
            } catch (e: Exception) { message.value = "Xtream failed: ${e.message}" }
            finally { busy.value = null }
        }
    }

    // ---- Guide (EPG)
    fun loadEpg(id: String, force: Boolean = false) {
        viewModelScope.launch {
            val p = playlists.value.firstOrNull { it.id == id } ?: return@launch
            epgBusy.value = true
            try {
                val d = when {
                    p.source.startsWith("portal:") -> Stalker.epg(store.loadPortal())
                    p.epgUrl.isNotBlank() -> Epg.load(p.epgUrl, getApplication<Application>().cacheDir, force)
                    else -> null
                }
                if (d != null) epg.value = epg.value + (id to d)
            } catch (e: Exception) { message.value = "Guide: ${e.message}" }
            finally { epgBusy.value = false }
        }
    }

    fun setEpgUrl(id: String, url: String) { update(id) { it.copy(epgUrl = url.trim()) }; loadEpg(id, true) }

    fun progs(c: Channel): List<Prog> {
        for (d in epg.value.values) { val l = d.forChannel(c); if (l.isNotEmpty()) return l }
        return emptyList()
    }

    // ---- Library tools
    fun merge(ids: List<String>, name: String, dedupe: Boolean) {
        val src = playlists.value.filter { it.id in ids }
        var ch = src.flatMap { it.channels }
        if (dedupe) ch = ch.distinctBy { it.url }
        if (ch.isEmpty()) return
        add(name.ifBlank { "Merged" }, "merged", ch, src.map { it.epgUrl }.firstOrNull { it.isNotBlank() } ?: "")
        message.value = "Merged ${ch.size} channels"
    }

    fun copyTo(targetId: String, chs: List<Channel>) {
        update(targetId) { p -> p.copy(channels = (p.channels + chs).distinctBy { it.url }) }
        message.value = "Copied ${chs.size} channel(s)"
    }

    fun cycleBuffer() { val v = (buffer.value + 1) % 3; store.buffer = v; buffer.value = v }

    fun played(c: Channel) {
        val l = (listOf(c) + recents.value.filter { it.url != c.url }).take(20)
        recents.value = l
        viewModelScope.launch(Dispatchers.IO) { store.saveRecents(l) }
    }

    /** Channel logo: playlist logo first, otherwise the icon from the guide (XMLTV). */
    fun logo(c: Channel): String {
        if (c.logo.startsWith("http")) return c.logo
        for (d in epg.value.values) { val l = d.logoFor(c); if (l.isNotEmpty()) return l }
        return ""
    }

    // ---- Playback / favorites
    suspend fun resolve(url: String): String =
        if (url.startsWith("portal://")) Stalker.resolve(url.removePrefix("portal://"), store.loadPortal()) else url

    fun toggleFav(url: String) {
        playlists.value = playlists.value.map { p ->
            p.copy(channels = p.channels.map { if (it.url == url) it.copy(fav = !it.fav) else it })
        }
        persist()
    }

    // ---- Checks
    fun autoCheck() {
        viewModelScope.launch {
            for (p in playlists.value) {
                if (!p.source.startsWith("http")) continue
                val ok = Net.reachable(p.source)
                update(p.id) { it.copy(state = if (ok) 1 else 2, lastChecked = System.currentTimeMillis()) }
            }
            if (store.deepCheck) playlists.value.forEach { runCheck(it.id) }
        }
    }

    fun checkChannels(id: String) { viewModelScope.launch { runCheck(id) } }

    private suspend fun runCheck(id: String) {
        val p = playlists.value.firstOrNull { it.id == id } ?: return
        busy.value = "Checking ${p.name}"; progress.value = 0f
        val res = Checker.checkAll(p.channels) { d, t -> progress.value = d / t.toFloat() }
        update(id) { it.copy(channels = res, lastChecked = System.currentTimeMillis()) }
        busy.value = null; progress.value = -1f
        message.value = "Working: ${res.count { it.status == 1 }} • Dead: ${res.count { it.status == 2 }}"
    }

    fun export(uri: Uri, p: Playlist) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                getApplication<Application>().contentResolver.openOutputStream(uri)!!
                    .bufferedWriter().use { it.write(M3u.export(p.channels, p.epgUrl)) }
                message.value = "Exported ${p.channels.size} channels"
            } catch (e: Exception) { message.value = "Export failed" }
        }
    }
}
