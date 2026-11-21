package com.m3upro

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Compact on-disk format (channels as arrays) = faster load/save for 50k+ channel lists. Reads the old format too. */
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
    var thorough: Boolean
        get() = prefs.getBoolean("thorough", true)
        set(v) = prefs.edit().putBoolean("thorough", v).apply()

    private fun encode(c: Channel): JSONArray = JSONArray()
        .put(c.name).put(c.url).put(c.group).put(c.logo).put(c.tvgId).put(c.status).put(c.ms).put(c.note)
        .put(c.drm).put(c.key).put(c.ua).put(c.ref).put(c.raw).put(c.extra).put(c.tags)

    private fun decode(a: JSONArray): Channel = Channel(
        name = a.optString(0), url = a.optString(1), group = a.optString(2), logo = a.optString(3), tvgId = a.optString(4),
        status = a.optInt(5), ms = a.optInt(6), note = a.optString(7), drm = a.optString(8), key = a.optString(9),
        ua = a.optString(10), ref = a.optString(11), raw = a.optString(12), extra = a.optString(13), tags = a.optString(14)
    )

    private fun decodeLegacy(c: JSONObject): Channel = Channel(
        name = c.optString("n"), url = c.optString("u"), group = c.optString("g"), logo = c.optString("l"),
        tvgId = c.optString("t"), status = c.optInt("s"), ms = c.optInt("ms"), note = c.optString("note"),
        drm = c.optString("d"), key = c.optString("k"), ua = c.optString("ua"), ref = c.optString("r")
    )

    fun load(): List<Playlist> = try {
        val text = file.readText()
        val arr = if (text.trimStart().startsWith("[")) JSONArray(text) else JSONObject(text).getJSONArray("pl")
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            val ch = o.getJSONArray("ch")
            val source = o.optString("source")
            var kind = o.optString("kind")
            if (kind.isBlank()) kind = if (source.startsWith("xtream:")) "xtream" else if (source.startsWith("portal:")) "portal"
                else if (source.startsWith("http")) "m3u" else "file"
            val list = ArrayList<Channel>(ch.length())
            for (j in 0 until ch.length()) {
                val e = ch.get(j)
                list.add(if (e is JSONArray) decode(e) else decodeLegacy(e as JSONObject))
            }
            Playlist(
                id = o.getString("id"), name = o.getString("name"), kind = kind, source = source,
                meta = o.optString("meta"), lastChecked = o.optLong("checked"), state = o.optInt("state"),
                channels = list, info = o.optString("info"), epg = o.optString("epg")
            )
        }
    } catch (e: Exception) {
        if (file.exists()) {
            DebugLog.e("Store.load failed - keeping a copy as playlists.json.bad", e)
            try { file.copyTo(File(file.path + ".bad"), overwrite = true) } catch (x: Exception) { /* ignore */ }
        }
        emptyList()
    }

    fun save(list: List<Playlist>) {
        val arr = JSONArray()
        for (p in list) {
            val ch = JSONArray()
            for (c in p.channels) ch.put(encode(c))
            arr.put(JSONObject().put("id", p.id).put("name", p.name).put("kind", p.kind).put("source", p.source)
                .put("meta", p.meta).put("checked", p.lastChecked).put("state", p.state)
                .put("info", p.info).put("epg", p.epg).put("ch", ch))
        }
        val tmp = File(file.path + ".tmp")
        tmp.writeText(JSONObject().put("v", 2).put("pl", arr).toString())
        tmp.renameTo(file)
    }
}
