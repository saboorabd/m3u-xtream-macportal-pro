package com.m3upro

import org.json.JSONObject
import java.net.URI
import java.net.URLEncoder

/** Stalker / Ministra "MAC portal" client. Plain Kotlin on top of [Http]. */
object Stalker {
    private const val UA = "Mozilla/5.0 (QtEmbedded; U; Linux; C) AppleWebKit/533.3 (KHTML, like Gecko) MAG200 stbapp ver: 2 rev: 250 Safari/533.3"
    private const val VER = "ImageDescription: 0.2.18-r14-pub-250; ImageDate: Fri Jan 15 15:20:44 EET 2016; PORTAL version: 5.5.0; API Version: JS API version: 328; STB API version: 134; Player Engine version: 0x566"

    class Sess(val ep: String, val token: String, val cfg: PortalCfg)

    private val sessions = HashMap<String, Sess>()
    private fun key(c: PortalCfg) = c.url.trim().lowercase() + "|" + c.mac.trim().lowercase()
    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    fun origin(ep: String): String =
        try { val u = URI(ep); u.scheme + "://" + u.authority } catch (e: Exception) { ep.substringBefore("/", ep) }

    fun endpoints(u: String): List<String> {
        var b = u.trim().trimEnd('/')
        if (!b.startsWith("http", true)) b = "http://$b"
        if (b.endsWith(".php")) return listOf(b)
        b = b.removeSuffix("/c").trimEnd('/')
        if (b.endsWith("/stalker_portal")) return listOf("$b/server/load.php", "$b/portal.php")
        return listOf("$b/portal.php", "$b/server/load.php", "$b/stalker_portal/server/load.php")
    }

    private fun headers(ep: String, cfg: PortalCfg, token: String?): Map<String, String> {
        val h = LinkedHashMap<String, String>()
        h["User-Agent"] = UA
        h["X-User-Agent"] = "Model: MAG250; Link: WiFi"
        h["Cookie"] = "mac=${cfg.mac}; stb_lang=en; timezone=GMT"
        h["Referer"] = origin(ep) + "/c/"
        h["Accept"] = "*/*"
        if (token != null) h["Authorization"] = "Bearer $token"
        return h
    }

    private fun call(http: Http, ep: String, params: String, cfg: PortalCfg, token: String?, timeout: Int = 30): JSONObject {
        val body = try { http.text("$ep?$params&JsHttpRequest=1-xml", headers(ep, cfg, token), timeout) }
        catch (e: HttpError) { throw IllegalStateException("HTTP ${e.code}") }
        try { return JSONObject(body.trim().trimStart('\uFEFF')) }
        catch (e: Exception) { throw IllegalStateException("not a portal API response") }
    }

    fun connect(http: Http, cfg: PortalCfg, stage: (String) -> Unit = {}): Sess {
        val tried = ArrayList<String>()
        for (ep in endpoints(cfg.url)) {
            try {
                stage("Connecting to portal…")
                val hs = call(http, ep, "type=stb&action=handshake&token=&prehash=0", cfg, null)
                val token = hs.optJSONObject("js")?.optString("token") ?: ""
                if (token.isBlank()) throw IllegalStateException("no token (wrong MAC or blocked)")
                stage("Authenticating…")
                val metrics = enc("{\"mac\":\"${cfg.mac}\",\"sn\":\"${cfg.serial}\",\"type\":\"STB\",\"model\":\"MAG250\",\"uid\":\"\",\"random\":\"\"}")
                call(http, ep, "type=stb&action=get_profile&hd=1&ver=${enc(VER)}&num_banks=2&sn=${enc(cfg.serial)}" +
                    "&stb_type=MAG250&client_type=STB&image_version=218&video_out=hdmi" +
                    "&device_id=${enc(cfg.deviceId)}&device_id2=${enc(cfg.deviceId2)}&signature=&auth_second_step=1" +
                    "&hw_version=1.7-BD-00&not_valid_token=0&metrics=$metrics&api_signature=262&prehash=", cfg, token)
                val s = Sess(ep, token, cfg)
                synchronized(sessions) { sessions[key(cfg)] = s }
                return s
            } catch (e: Exception) {
                tried.add(ep.substringAfter("://").substringAfter("/", "/") + " → " + (e.message ?: Errs.friendly(e)))
            }
        }
        throw IllegalStateException("Portal login failed. " + tried.takeLast(3).joinToString("; "))
    }

    private fun accountInfo(http: Http, s: Sess): String {
        val sb = StringBuilder("Type: MAC portal\nMAC: ${s.cfg.mac}\n")
        try {
            val js = call(http, s.ep, "type=account_info&action=get_main_info", s.cfg, s.token).optJSONObject("js")
            if (js != null) for (k in listOf("login", "phone", "end_date", "expire_billing_date", "tariff_plan", "tariff_plan_name", "account_balance")) {
                val v = js.optString(k)
                if (v.isBlank() || v == "null") continue
                val label = when (k) {
                    "login" -> "Login"
                    "phone", "end_date", "expire_billing_date" -> "Expiry"
                    "account_balance" -> "Balance"
                    else -> "Plan"
                }
                sb.append(label).append(": ").append(v).append("\n")
            }
        } catch (e: Exception) { /* info is optional */ }
        return sb.toString().trim()
    }

    fun account(http: Http, cfg: PortalCfg): String = accountInfo(http, connect(http, cfg))

    private fun allChannels(http: Http, s: Sess): List<JSONObject> {
        val js = call(http, s.ep, "type=itv&action=get_all_channels", s.cfg, s.token, 90).optJSONObject("js") ?: return emptyList()
        val d = js.optJSONArray("data") ?: return emptyList()
        return (0 until d.length()).map { d.getJSONObject(it) }
    }

    private fun pages(http: Http, s: Sess, stage: (String) -> Unit): ArrayList<JSONObject> {
        val out = ArrayList<JSONObject>()
        var page = 1
        var total = Int.MAX_VALUE
        while (out.size < total && page <= 400) {
            val js = call(http, s.ep, "type=itv&action=get_ordered_list&genre=*&fav=0&sortby=number&hd=0&p=$page", s.cfg, s.token, 60)
                .optJSONObject("js") ?: break
            val d = js.optJSONArray("data") ?: break
            if (d.length() == 0) break
            for (i in 0 until d.length()) out.add(d.getJSONObject(i))
            total = js.optInt("total_items", out.size)
            stage("Loading channels… ${out.size}")
            page++
        }
        return out
    }

    private fun logoUrl(ep: String, logo: String): String {
        if (logo.isBlank()) return ""
        if (logo.startsWith("http")) return logo
        val base = if (ep.contains("/stalker_portal/")) ep.substringBefore("/server/load.php") else origin(ep)
        return "$base/misc/logos/320/$logo"
    }

    fun fetch(http: Http, cfg: PortalCfg, stage: (String) -> Unit = {}): Fetched {
        val s = connect(http, cfg, stage)
        stage("Reading account…")
        val info = accountInfo(http, s)
        stage("Loading categories…")
        val genres = HashMap<String, String>()
        try {
            val g = call(http, s.ep, "type=itv&action=get_genres", cfg, s.token).optJSONArray("js")
            if (g != null) for (i in 0 until g.length()) {
                val o = g.getJSONObject(i)
                genres[o.optString("id")] = o.optString("title")
            }
        } catch (e: Exception) { /* groups are optional */ }
        stage("Loading channels…")
        var raw: List<JSONObject> = try { allChannels(http, s) } catch (e: Exception) { emptyList() }
        if (raw.isEmpty()) raw = pages(http, s, stage)
        if (raw.isEmpty()) throw IllegalStateException("Portal returned no channels (account expired or MAC not active)")
        val list = raw.map { o ->
            Channel(
                name = o.optString("name"),
                url = "portal://" + o.optString("cmd"),
                group = genres[o.optString("tv_genre_id")] ?: "",
                logo = logoUrl(s.ep, o.optString("logo")),
                tvgId = o.optString("id")
            )
        }
        stage("Done: ${list.size} channels")
        return Fetched(list, info)
    }

    private val lock = Any()

    /** One shared session per portal+MAC. Handshakes are serialized: a new handshake invalidates the previous token. */
    fun session(http: Http, cfg: PortalCfg): Sess = synchronized(lock) { sessions[key(cfg)] ?: connect(http, cfg) }

    /** create_link: turns a portal channel command into the real stream URL. */
    fun resolve(http: Http, cmd: String, cfg: PortalCfg): String {
        fun go(s: Sess): String {
            val js = call(http, s.ep, "type=itv&action=create_link&cmd=${enc(cmd)}&series=&forced_storage=undefined" +
                "&disable_ad=0&download=0&force_ch_link_check=0", s.cfg, s.token).optJSONObject("js")
                ?: throw IllegalStateException("Portal refused the link request")
            val c = js.optString("cmd")
            if (c.isBlank()) throw IllegalStateException("Portal returned no link")
            return Regex("https?://\\S+").find(c)?.value ?: c
        }
        val s1 = session(http, cfg)
        return try { go(s1) } catch (e: Exception) {
            // token may have expired: reconnect once (or reuse a session another thread just created)
            val s2 = synchronized(lock) { val cur = sessions[key(cfg)]; if (cur != null && cur !== s1) cur else connect(http, cfg) }
            try { go(s2) } catch (e2: Exception) {
                val direct = Regex("https?://\\S+").find(cmd)?.value
                if (direct != null && !direct.contains("localhost")) direct else throw e2
            }
        }
    }
}
