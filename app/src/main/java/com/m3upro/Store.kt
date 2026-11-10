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

    fun savePortal(c: PortalCfg) = prefs.edit()
        .putString("p_url", c.url).putString("p_mac", c.mac).putString("p_d1", c.deviceId)
        .putString("p_d2", c.deviceId2).putString("p_sn", c.serial).apply()

    fun loadPortal() = PortalCfg(
        prefs.getString("p_url", "") ?: "", prefs.getString("p_mac", "") ?: "",
        prefs.getString("p_d1", "") ?: "", prefs.getString("p_d2", "") ?: "",
        prefs.getString("p_sn", "") ?: ""
    )

    fun load(): List<Playlist> = try {
        val a = JSONArray(file.readText())
        (0 until a.length()).map { i ->
            val o = a.getJSONObject(i)
            val ch = o.getJSONArray("ch")
            Playlist(
                o.getString("id"), o.getString("name"), o.optString("source"),
                o.optLong("checked"), o.optInt("state"),
                (0 until ch.length()).map { j ->
                    val c = ch.getJSONObject(j)
                    Channel(c.optString("n"), c.optString("u"), c.optString("g"),
                        c.optString("l"), c.optString("t"), c.optInt("s"), c.optBoolean("f"))
                },
                o.optString("epg")
            )
        }
    } catch (e: Exception) { emptyList() }

    fun save(list: List<Playlist>) {
        val a = JSONArray()
        for (p in list) {
            val ch = JSONArray()
            for (c in p.channels) ch.put(JSONObject()
                .put("n", c.name).put("u", c.url).put("g", c.group)
                .put("l", c.logo).put("t", c.tvgId).put("s", c.status).put("f", c.fav))
            a.put(JSONObject().put("id", p.id).put("name", p.name).put("source", p.source)
                .put("checked", p.lastChecked).put("state", p.state).put("epg", p.epgUrl).put("ch", ch))
        }
        file.writeText(a.toString())
    }
}
