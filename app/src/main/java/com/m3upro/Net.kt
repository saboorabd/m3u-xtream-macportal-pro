package com.m3upro

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.URLEncoder
import java.net.UnknownHostException
import java.text.DateFormat
import java.util.Date
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLException

data class Fetched(val channels: List<Channel>, val info: String)
data class CheckResult(val status: Int, val ms: Int, val note: String)

object Net {
    const val UA = "VLC/3.0.18 LibVLC/3.0.18"

    val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS).readTimeout(20, TimeUnit.SECONDS)
        .followRedirects(true).followSslRedirects(true).build()

    val slow: OkHttpClient = client.newBuilder()
        .readTimeout(120, TimeUnit.SECONDS).callTimeout(180, TimeUnit.SECONDS).build()

    private fun req(url: String) = Request.Builder().url(url).header("User-Agent", UA).build()

    suspend fun get(url: String, big: Boolean = false): String = withContext(Dispatchers.IO) {
        (if (big) slow else client).newCall(req(url)).execute().use {
            if (!it.isSuccessful) error("HTTP ${it.code}")
            it.body!!.string()
        }
    }

    suspend fun reachable(url: String): Boolean = withContext(Dispatchers.IO) {
        try { client.newCall(req(url)).execute().use { it.code in 200..399 } } catch (e: Exception) { false }
    }

    fun friendly(e: Exception): String = when (e) {
        is SocketTimeoutException -> "Timeout"
        is java.io.InterruptedIOException -> "Timeout"
        is UnknownHostException -> "DNS failed"
        is ConnectException -> "Connection refused"
        is SSLException -> "SSL error"
        is IllegalArgumentException -> "Invalid URL"
        else -> e.message ?: e.javaClass.simpleName
    }
}

/** Real stream check: status code, content sanity (HTML error pages / fake HLS), and response time. */
object Checker {
    private fun client(timeoutSec: Int): OkHttpClient = Net.client.newBuilder()
        .connectTimeout(timeoutSec.toLong(), TimeUnit.SECONDS)
        .readTimeout(timeoutSec.toLong(), TimeUnit.SECONDS)
        .callTimeout((timeoutSec * 2).toLong(), TimeUnit.SECONDS).build()

    fun probe(client: OkHttpClient, url: String, ua: String, ref: String): CheckResult {
        if (!url.startsWith("http", true)) return CheckResult(0, 0, "Unsupported link type")
        val t0 = System.nanoTime()
        fun elapsed(): Int = ((System.nanoTime() - t0) / 1_000_000).toInt()
        return try {
            val rb = Request.Builder().url(url).header("User-Agent", ua.ifBlank { Net.UA })
            if (ref.isNotBlank()) rb.header("Referer", ref)
            client.newCall(rb.build()).execute().use { r ->
                val ms = elapsed()
                if (!r.isSuccessful) return@use CheckResult(2, ms, "HTTP ${r.code}")
                val ct = (r.header("Content-Type") ?: "").lowercase()
                if (ct.contains("text/html")) return@use CheckResult(2, ms, "HTML page, not a stream")
                val body = try { r.peekBody(512).bytes() } catch (e: Exception) { ByteArray(0) }
                if (body.isEmpty()) return@use CheckResult(2, ms, "Empty response")
                val isHls = url.contains(".m3u8", true) || ct.contains("mpegurl")
                if (isHls && !String(body).contains("#EXTM3U")) return@use CheckResult(2, ms, "Invalid HLS playlist")
                CheckResult(1, ms, "OK")
            }
        } catch (e: Exception) {
            CheckResult(2, elapsed(), Net.friendly(e))
        }
    }

    suspend fun run(chs: List<Channel>, concurrency: Int, timeoutSec: Int, portal: PortalCfg?): List<CheckResult> = coroutineScope {
        val cl = client(timeoutSec)
        val sem = Semaphore(concurrency.coerceAtLeast(1))
        chs.map { c ->
            async(Dispatchers.IO) {
                sem.withPermit {
                    try {
                        var u = c.url
                        if (u.startsWith("portal://")) {
                            if (portal == null) return@withPermit CheckResult(0, 0, "Portal login missing")
                            u = Stalker.resolve(u.removePrefix("portal://"), portal)
                        }
                        probe(cl, u, c.ua, c.ref)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        CheckResult(2, 0, Net.friendly(e))
                    }
                }
            }
        }.awaitAll()
    }
}

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

    private fun info(root: JSONObject): String {
        val ui = root.optJSONObject("user_info") ?: return ""
        val sb = StringBuilder("Type: Xtream Codes\n")
        sb.append("Status: ").append(ui.optString("status").ifBlank { "Unknown" }).append("\n")
        sb.append("Expires: ").append(expiry(ui.optString("exp_date"))).append("\n")
        sb.append("Connections: ").append(ui.optString("active_cons").ifBlank { "0" })
            .append(" / ").append(ui.optString("max_connections").ifBlank { "?" }).append("\n")
        if (ui.optString("is_trial") == "1") sb.append("Trial account\n")
        return sb.toString().trim()
    }

    private suspend fun login(b: String, user: String, pass: String): JSONObject {
        val root = JSONObject(Net.get("$b/player_api.php?username=${enc(user)}&password=${enc(pass)}"))
        val ui = root.optJSONObject("user_info") ?: error("Invalid server response")
        if (ui.optString("auth") != "1") error("Login rejected (wrong username/password)")
        return root
    }

    /** Only account status (used for the launch check). */
    suspend fun account(server: String, user: String, pass: String): String =
        withContext(Dispatchers.IO) { info(login(base(server), user, pass)) }

    suspend fun fetch(server: String, user: String, pass: String, ext: String, movies: Boolean): Fetched =
        withContext(Dispatchers.IO) {
            val b = base(server)
            val api = "$b/player_api.php?username=${enc(user)}&password=${enc(pass)}"
            try {
                val root = login(b, user, pass)
                val cats = HashMap<String, String>()
                val ca = JSONArray(Net.get("$api&action=get_live_categories", true))
                for (i in 0 until ca.length()) {
                    val o = ca.getJSONObject(i)
                    cats[o.optString("category_id")] = o.optString("category_name")
                }
                val list = ArrayList<Channel>()
                val sa = JSONArray(Net.get("$api&action=get_live_streams", true))
                for (i in 0 until sa.length()) {
                    val o = sa.getJSONObject(i)
                    list.add(Channel(
                        name = o.optString("name"),
                        url = "$b/live/$user/$pass/${o.optString("stream_id")}.$ext",
                        group = cats[o.optString("category_id")] ?: "",
                        logo = o.optString("stream_icon"),
                        tvgId = o.optString("epg_channel_id")
                    ))
                }
                if (movies) {
                    try {
                        val vcats = HashMap<String, String>()
                        val vc = JSONArray(Net.get("$api&action=get_vod_categories", true))
                        for (i in 0 until vc.length()) {
                            val o = vc.getJSONObject(i)
                            vcats[o.optString("category_id")] = o.optString("category_name")
                        }
                        val vs = JSONArray(Net.get("$api&action=get_vod_streams", true))
                        for (i in 0 until vs.length()) {
                            val o = vs.getJSONObject(i)
                            val x = o.optString("container_extension").ifBlank { "mp4" }
                            list.add(Channel(
                                name = o.optString("name"),
                                url = "$b/movie/$user/$pass/${o.optString("stream_id")}.$x",
                                group = "Movies • " + (vcats[o.optString("category_id")] ?: ""),
                                logo = o.optString("stream_icon")
                            ))
                        }
                    } catch (e: Exception) { /* movies are optional */ }
                }
                Fetched(list, info(root))
            } catch (e: Exception) {
                // fallback for panels that block player_api: classic M3U endpoint
                try {
                    val text = Net.get("$b/get.php?username=${enc(user)}&password=${enc(pass)}&type=m3u_plus&output=$ext", true)
                    val ch = M3u.parse(text)
                    if (ch.isEmpty()) throw e
                    Fetched(ch, "Type: Xtream Codes\nLoaded via get.php (account details unavailable)")
                } catch (e2: Exception) { throw e }
            }
        }
}

/** Stalker / MAC-portal client. */
object Stalker {
    private const val UA = "Mozilla/5.0 (QtEmbedded; U; Linux; C) AppleWebKit/533.3 (KHTML, like Gecko) MAG200 stbapp ver: 2 rev: 250 Safari/533.3"
    private class Sess(val ep: String, val token: String, val cfg: PortalCfg)
    private val sessions = HashMap<String, Sess>()

    private fun key(c: PortalCfg) = c.url.trim().lowercase() + "|" + c.mac.trim().lowercase()
    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    private fun endpoints(u: String): List<String> {
        var b = u.trim().trimEnd('/')
        if (!b.startsWith("http", true)) b = "http://$b"
        if (b.endsWith(".php")) return listOf(b)
        b = b.removeSuffix("/c").trimEnd('/')
        if (b.endsWith("/stalker_portal")) return listOf("$b/server/load.php", "$b/portal.php")
        return listOf("$b/portal.php", "$b/server/load.php", "$b/stalker_portal/server/load.php")
    }

    private fun call(ep: String, params: String, cfg: PortalCfg, token: String?): JSONObject {
        val rb = Request.Builder().url("$ep?$params&JsHttpRequest=1-xml")
            .header("User-Agent", UA)
            .header("X-User-Agent", "Model: MAG250; Link: WiFi")
            .header("Cookie", "mac=${cfg.mac}; stb_lang=en; timezone=GMT")
        if (token != null) rb.header("Authorization", "Bearer $token")
        Net.client.newCall(rb.build()).execute().use { r ->
            val t = r.body!!.string()
            if (!r.isSuccessful) throw IllegalStateException("HTTP ${r.code}")
            try { return JSONObject(t) } catch (e: Exception) { throw IllegalStateException("Unexpected response (not a portal API)") }
        }
    }

    @Synchronized
    private fun connect(cfg: PortalCfg): Sess {
        var last: Exception? = null
        for (ep in endpoints(cfg.url)) {
            try {
                val hs = call(ep, "type=stb&action=handshake&token=", cfg, null)
                val token = hs.getJSONObject("js").optString("token")
                if (token.isBlank()) throw IllegalStateException("No token received (wrong MAC or blocked)")
                call(ep, "type=stb&action=get_profile&hd=1&num_banks=2&stb_type=MAG250" +
                    "&sn=${enc(cfg.serial)}&device_id=${enc(cfg.deviceId)}&device_id2=${enc(cfg.deviceId2)}" +
                    "&auth_second_step=1", cfg, token)
                val s = Sess(ep, token, cfg)
                synchronized(sessions) { sessions[key(cfg)] = s }
                return s
            } catch (e: Exception) { last = e }
        }
        throw IllegalStateException("Login failed: ${last?.message ?: "unknown error"}")
    }

    private fun accountInfo(s: Sess): String {
        val sb = StringBuilder("Type: MAC portal\nMAC: ${s.cfg.mac}\n")
        try {
            val js = call(s.ep, "type=account_info&action=get_main_info", s.cfg, s.token).optJSONObject("js")
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

    private fun pages(s: Sess): ArrayList<JSONObject> {
        val out = ArrayList<JSONObject>()
        var page = 1
        var total = Int.MAX_VALUE
        while (out.size < total && page <= 300) {
            val js = call(s.ep, "type=itv&action=get_ordered_list&genre=*&fav=0&sortby=number&hd=0&p=$page", s.cfg, s.token)
                .optJSONObject("js") ?: break
            val d = js.optJSONArray("data") ?: break
            if (d.length() == 0) break
            for (i in 0 until d.length()) out.add(d.getJSONObject(i))
            total = js.optInt("total_items", out.size)
            page++
        }
        return out
    }

    suspend fun account(cfg: PortalCfg): String = withContext(Dispatchers.IO) { accountInfo(connect(cfg)) }

    suspend fun fetch(cfg: PortalCfg): Fetched = withContext(Dispatchers.IO) {
        val s = connect(cfg)
        val genres = HashMap<String, String>()
        try {
            val g = call(s.ep, "type=itv&action=get_genres", cfg, s.token).optJSONArray("js")
            if (g != null) for (i in 0 until g.length()) {
                val o = g.getJSONObject(i)
                genres[o.optString("id")] = o.optString("title")
            }
        } catch (e: Exception) { /* groups are optional */ }
        var raw = ArrayList<JSONObject>()
        try {
            val d = call(s.ep, "type=itv&action=get_all_channels", cfg, s.token).optJSONObject("js")?.optJSONArray("data")
            if (d != null) for (i in 0 until d.length()) raw.add(d.getJSONObject(i))
        } catch (e: Exception) { /* fall back to paging */ }
        if (raw.isEmpty()) raw = pages(s)
        if (raw.isEmpty()) error("Portal returned no channels (account expired or MAC not active)")
        val list = raw.map { o ->
            val logo = o.optString("logo")
            Channel(
                name = o.optString("name"),
                url = "portal://" + o.optString("cmd"),
                group = genres[o.optString("tv_genre_id")] ?: "",
                logo = if (logo.startsWith("http")) logo else "",
                tvgId = o.optString("id")
            )
        }
        Fetched(list, accountInfo(s))
    }

    /** create_link: turns a portal channel command into a real stream URL. */
    suspend fun resolve(cmd: String, cfg: PortalCfg): String = withContext(Dispatchers.IO) {
        fun go(s: Sess): String {
            val js = call(s.ep, "type=itv&action=create_link&cmd=${enc(cmd)}&series=&forced_storage=0" +
                "&disable_ad=0&download=0&force_ch_link_check=0", s.cfg, s.token).getJSONObject("js")
            val c = js.optString("cmd")
            if (c.isBlank()) error("Portal returned no link")
            return Regex("https?://\\S+").find(c)?.value ?: c
        }
        val s0 = synchronized(sessions) { sessions[key(cfg)] } ?: connect(cfg)
        try { go(s0) } catch (e: Exception) { go(connect(cfg)) }
    }
}
