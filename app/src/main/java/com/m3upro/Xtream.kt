package com.m3upro

import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStreamReader
import java.net.URLEncoder
import java.text.DateFormat
import java.util.Date

object Xtream {
    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    fun base(s: String): String {
        var b = s.trim().trimEnd('/')
        if (!b.startsWith("http", true)) b = "http://$b"
        b = b.substringBefore("/player_api.php").substringBefore("/get.php").substringBefore("/xmltv.php")
        return b.trimEnd('/').removeSuffix("/c").trimEnd('/')
    }

    private fun expiry(v: String): String {
        val sec = v.toLongOrNull() ?: return "Unlimited"
        return DateFormat.getDateInstance().format(Date(sec * 1000))
    }

    fun info(root: JSONObject): String {
        val ui = root.optJSONObject("user_info") ?: return ""
        val sb = StringBuilder("Type: Xtream Codes\n")
        sb.append("Status: ").append(ui.optString("status").ifBlank { "Unknown" }).append("\n")
        sb.append("Expires: ").append(expiry(ui.optString("exp_date"))).append("\n")
        sb.append("Connections: ").append(ui.optString("active_cons").ifBlank { "0" })
            .append(" / ").append(ui.optString("max_connections").ifBlank { "?" }).append("\n")
        if (ui.optString("is_trial") == "1") sb.append("Trial account\n")
        return sb.toString().trim()
    }

    fun login(http: Http, b: String, user: String, pass: String): JSONObject {
        val body = try { http.text("$b/player_api.php?username=${enc(user)}&password=${enc(pass)}", emptyMap(), 25) }
        catch (e: HttpError) { throw IllegalStateException("Server answered HTTP ${e.code} (check URL / port)") }
        val root = try { JSONObject(body.trim().trimStart('\uFEFF')) }
        catch (e: Exception) { throw IllegalStateException("Server did not answer like Xtream Codes (check URL / port)") }
        val ui = root.optJSONObject("user_info") ?: throw IllegalStateException("Login rejected (no account data)")
        val auth = ui.optString("auth")
        if (auth == "0" || (auth.isBlank() && !ui.optString("status").equals("Active", true)))
            throw IllegalStateException("Login rejected (wrong username/password, expired or blocked)")
        return root
    }

    fun account(http: Http, server: String, user: String, pass: String): String = info(login(http, base(server), user, pass))

    fun fetch(http: Http, server: String, user: String, pass: String, ext: String, movies: Boolean,
              stage: (String) -> Unit = {}): Fetched {
        val b = base(server)
        val api = "$b/player_api.php?username=${enc(user)}&password=${enc(pass)}"
        val epg = "$b/xmltv.php?username=${enc(user)}&password=${enc(pass)}"
        try {
            stage("Logging in…")
            val root = login(http, b, user, pass)
            stage("Loading categories…")
            val cats = HashMap<String, String>()
            try {
                val ca = JSONArray(http.text("$api&action=get_live_categories", emptyMap(), 60))
                for (i in 0 until ca.length()) {
                    val o = ca.getJSONObject(i)
                    cats[o.optString("category_id")] = o.optString("category_name")
                }
            } catch (e: Exception) { /* groups stay empty */ }
            val list = ArrayList<Channel>()
            stage("Loading channels…")
            http.open("$api&action=get_live_streams", emptyMap(), 120).use { ins ->
                Json.eachObject(InputStreamReader(ins, Charsets.UTF_8)) { o ->
                    list.add(Channel(
                        name = o.optString("name"),
                        url = "$b/live/$user/$pass/${o.optString("stream_id")}.$ext",
                        group = cats[o.optString("category_id")] ?: "",
                        logo = o.optString("stream_icon"),
                        tvgId = o.optString("epg_channel_id")
                    ))
                    if (list.size % 2000 == 0) stage("Loading channels… ${list.size}")
                }
            }
            if (movies) {
                stage("Loading movies…")
                try {
                    val vcats = HashMap<String, String>()
                    val vc = JSONArray(http.text("$api&action=get_vod_categories", emptyMap(), 60))
                    for (i in 0 until vc.length()) {
                        val o = vc.getJSONObject(i)
                        vcats[o.optString("category_id")] = o.optString("category_name")
                    }
                    http.open("$api&action=get_vod_streams", emptyMap(), 120).use { ins ->
                        Json.eachObject(InputStreamReader(ins, Charsets.UTF_8)) { o ->
                            val x = o.optString("container_extension").ifBlank { "mp4" }
                            list.add(Channel(
                                name = o.optString("name"),
                                url = "$b/movie/$user/$pass/${o.optString("stream_id")}.$x",
                                group = "Movies • " + (vcats[o.optString("category_id")] ?: ""),
                                logo = o.optString("stream_icon")
                            ))
                        }
                    }
                } catch (e: Exception) { /* movies are optional */ }
            }
            return Fetched(list, info(root), epg)
        } catch (e: Exception) {
            // fallback for panels that block player_api: classic M3U endpoint
            try {
                stage("Trying get.php…")
                val p = http.open("$b/get.php?username=${enc(user)}&password=${enc(pass)}&type=m3u_plus&output=$ext", emptyMap(), 120)
                    .use { M3u.parseStream(it) }
                if (p.channels.isEmpty()) throw e
                return Fetched(p.channels, "Type: Xtream Codes\nLoaded via get.php (account details unavailable)", epg)
            } catch (e2: Exception) { throw e }
        }
    }
}
