package com.m3upro

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class Store(private val ctx: Context) {
    private val file get() = File(ctx.filesDir, "playlists.json")
    private val prefs = ctx.getSharedPreferences("prefs", Context.MODE_PRIVATE)

    var deepCheck: Boolean
        get() = prefs.getBoolean("deep", false)
        set(v) = prefs.edit().putBoolean("deep", v).apply()
    var timeoutSec: Int
        get() = prefs.getInt("timeout", 8)
        set(v) = prefs.edit().putInt("timeout", v).apply()
    var concurrency: Int
        get() = prefs.getInt("conc", 20)
        set(v) = prefs.edit().putInt("conc", v).apply()

    private fun enc(c: Channel): JSONObject = JSONObject()
        .put("n", c.name).put("u", c.url).put("g", c.group).put("l", c.logo).put("t", c.tvgId)
        .put("s", c.status).put("ms", c.ms).put("note", c.note)
        .put("d", c.drm).put("k", c.key).put("ua", c.ua).put("r", c.ref)

    private fun dec(c: JSONObject): Channel = Channel(
        name = c.optString("n"), url = c.optString("u"), group = c.optString("g"),
        logo = c.optString("l"), tvgId = c.optString("t"), status = c.optInt("s"),
        ms = c.optInt("ms"), note = c.optString("note"),
        drm = c.optString("d"), key = c.optString("k"), ua = c.optString("ua"), ref = c.optString("r")
    )

    fun load(): List<Playlist> = try {
        val a = JSONArray(file.readText())
        (0 until a.length()).map { i ->
            val o = a.getJSONObject(i)
            val ch = o.getJSONArray("ch")
            val source = o.optString("source")
            var kind = o.optString("kind")
            if (kind.isBlank()) kind = if (source.startsWith("xtream:")) "xtream" else if (source.startsWith("portal:")) "portal"
                else if (source.startsWith("http")) "m3u" else "file"
            Playlist(
                id = o.getString("id"), name = o.getString("name"), kind = kind, source = source,
                meta = o.optString("meta"), lastChecked = o.optLong("checked"), state = o.optInt("state"),
                channels = (0 until ch.length()).map { j -> dec(ch.getJSONObject(j)) },
                info = o.optString("info")
            )
        }
    } catch (e: Exception) { emptyList() }

    fun save(list: List<Playlist>) {
        val a = JSONArray()
        for (p in list) {
            val ch = JSONArray()
            for (c in p.channels) ch.put(enc(c))
            a.put(JSONObject().put("id", p.id).put("name", p.name).put("kind", p.kind).put("source", p.source)
                .put("meta", p.meta).put("checked", p.lastChecked).put("state", p.state)
                .put("info", p.info).put("ch", ch))
        }
        val tmp = File(file.path + ".tmp")
        tmp.writeText(a.toString())
        tmp.renameTo(file)
    }
}
