package com.m3upro

import java.net.URLDecoder

/** Result of "paste anything" detection. */
data class Smart(
    val kind: String,            // portal | xtream | m3u | text | unknown
    val url: String = "",
    val user: String = "",
    val pass: String = "",
    val mac: String = "",
    val d1: String = "",
    val d2: String = "",
    val sn: String = "",
    val name: String = ""
) {
    val complete: Boolean
        get() = when (kind) {
            "portal" -> url.isNotBlank() && mac.isNotBlank()
            "xtream" -> url.isNotBlank() && user.isNotBlank() && pass.isNotBlank()
            "m3u" -> url.isNotBlank()
            "text" -> true
            else -> false
        }

    fun describe(): String = when (kind) {
        "portal" -> "MAC portal" + (if (mac.isNotBlank()) " • $mac" else " • MAC missing")
        "xtream" -> "Xtream Codes" + (if (user.isNotBlank()) " • $user" else "")
        "m3u" -> "M3U playlist link"
        "text" -> "Pasted M3U text"
        else -> "Not recognised yet"
    }
}

object SmartDetect {
    private val MAC = Regex("(?i)(?<![0-9a-f])[0-9a-f]{2}([:-])[0-9a-f]{2}(\\1[0-9a-f]{2}){4}(?![0-9a-f])")
    private val URL = Regex("https?://[^\\s\"'<>]+")
    private val LABEL = Regex("^\\s*([A-Za-z][A-Za-z0-9 _.\\-/]{0,30}?)\\s*(?::|=|\\s[-–]\\s)\\s*(.+?)\\s*$")
    private val TOKEN = Regex("^[A-Za-z0-9]{8,}$")

    fun normMac(s: String): String {
        val hex = s.filter { it.isLetterOrDigit() }.uppercase()
        if (hex.length != 12 || !hex.all { it in '0'..'9' || it in 'A'..'F' }) return ""
        return hex.chunked(2).joinToString(":")
    }

    private fun clean(u: String) = u.trim().trimEnd('.', ',', ';', ')', ']', '"', '\'')

    private fun query(url: String, key: String): String {
        val q = url.substringAfter("?", "")
        for (p in q.split("&")) {
            val kv = p.split("=", limit = 2)
            if (kv.size == 2 && kv[0].equals(key, true)) return try { URLDecoder.decode(kv[1], "UTF-8") } catch (e: Exception) { kv[1] }
        }
        return ""
    }

    fun origin(url: String): String {
        val s = url.trim()
        val scheme = if (s.startsWith("https", true)) "https" else "http"
        val rest = s.substringAfter("://", s).substringBefore("/").substringBefore("?")
        return "$scheme://$rest"
    }

    fun detect(input: String): Smart {
        val text = input.trim()
        if (text.isEmpty()) return Smart("unknown")
        if (text.startsWith("#EXTM3U") || text.contains("#EXTINF")) return Smart("text", name = "Pasted playlist")

        var url = ""; var user = ""; var pass = ""; var mac = ""; var d1 = ""; var d2 = ""; var sn = ""; var name = ""
        val used = HashSet<Int>()
        val lines = text.lines()

        for ((idx, raw) in lines.withIndex()) {
            val m = LABEL.find(raw) ?: continue
            val label = m.groupValues[1].lowercase().trim()
            var value = m.groupValues[2].trim().trim('"', '\'')
            if (label == "http" || label == "https") continue
            when {
                label.contains("url") || label.contains("portal") || label.contains("server") ||
                    label.contains("host") || label.contains("link") -> { if (url.isEmpty()) url = clean(value); used.add(idx) }
                label.contains("mac") -> { val n = normMac(value); if (n.isNotEmpty()) { mac = n; used.add(idx) } }
                label.contains("serial") || label == "sn" || label.startsWith("sn ") || label == "s/n" -> { sn = value; used.add(idx) }
                (label.contains("device") || label.contains("id")) && label.contains("2") -> { d2 = value; used.add(idx) }
                label.contains("device") || label.startsWith("id") || label == "dev id" -> { if (d1.isEmpty()) d1 = value; used.add(idx) }
                label.startsWith("user") || label == "login" || label == "username" -> { user = value; used.add(idx) }
                label.startsWith("pass") || label == "pwd" -> { pass = value; used.add(idx) }
                label == "name" || label == "title" -> { name = value; used.add(idx) }
            }
        }

        if (mac.isEmpty()) MAC.find(text)?.let { mac = normMac(it.value) }
        if (url.isEmpty()) URL.find(text)?.let { url = clean(it.value) }

        // credentials hidden in an Xtream-style link
        if (user.isEmpty() && url.contains("username=", true)) user = query(url, "username")
        if (pass.isEmpty() && url.contains("password=", true)) pass = query(url, "password")
        val live = Regex("/(?:live|movie|series)/([^/]+)/([^/]+)/").find(url)
        if (live != null && user.isEmpty()) { user = live.groupValues[1]; pass = live.groupValues[2] }

        val isPortalLike = url.contains("/c/") || url.contains("portal.php") || url.contains("stalker_portal") || url.endsWith("/c")
        if (mac.isNotEmpty() || (isPortalLike && user.isEmpty())) {
            // positional fallback for unlabeled serial / device ids
            if (sn.isEmpty() || d1.isEmpty() || d2.isEmpty()) {
                val tokens = ArrayList<String>()
                for ((idx, raw) in lines.withIndex()) {
                    if (idx in used) continue
                    val t = raw.trim()
                    if (t.isEmpty() || URL.containsMatchIn(t) || MAC.containsMatchIn(t)) continue
                    if (TOKEN.matches(t)) tokens.add(t)
                }
                for (t in tokens) {
                    if (sn.isEmpty()) sn = t else if (d1.isEmpty()) d1 = t else if (d2.isEmpty()) d2 = t
                }
            }
            return Smart("portal", url = url, mac = mac, d1 = d1, d2 = d2, sn = sn,
                name = name.ifBlank { if (url.isNotBlank()) "Portal: " + hostOf(url) else "Portal" })
        }
        if (url.isNotEmpty() && user.isNotEmpty() && pass.isNotEmpty()) {
            return Smart("xtream", url = origin(url), user = user, pass = pass, name = name.ifBlank { "Xtream: " + hostOf(url) })
        }
        if (url.isNotEmpty()) {
            return Smart("m3u", url = url, name = name.ifBlank {
                url.substringAfterLast('/').substringBefore('?').substringBeforeLast('.').ifBlank { hostOf(url) }
            })
        }
        return Smart("unknown")
    }
}

/** Auto-grouping + quick cleanup presets (what the official app only does with Pro regex templates). */
object Organize {
    private val RULES: List<Pair<String, List<String>>> = listOf(
        "Sports" to listOf("sport", "espn", "bein", "eurosport", "cricket", "football", "nba", "nfl", "ufc", "wwe", "f1", "tennis", "golf", "willow", "dazn", "ten 1", "ten 2", "ten 3", "sony ten", "star sports", "supersport", "arena"),
        "News" to listOf("news", "cnn", "bbc", "aljazeera", "al jazeera", "ndtv", "republic", "abp", "aaj tak", "india today", "times now", "dw ", "france 24", "cnbc", "bloomberg", "wion", "thanthi", "puthiya thalaimurai", "headlines"),
        "Movies" to listOf("movie", "cinema", "film", "hbo", "cinemax", "star gold", "zee cinema", "sony max", "pictures", "flix", "kollywood", "tollywood", "bollywood", "mgm", "cine"),
        "Kids" to listOf("kids", "cartoon", "nick", "disney", "pogo", "chutti", "hungama", "sonic", "baby", "junior", "boomerang", "kartoon", "toon"),
        "Music" to listOf("music", "mtv", "vh1", "9xm", "b4u", "zing", "radio", " fm", "mastiii", "trace", "vevo", "songs", "isai", "melody"),
        "Documentary" to listOf("discovery", "national geographic", "nat geo", "animal planet", "history", "tlc", "documentary", "science", "travel", "epic", "fox life"),
        "Religious" to listOf("bhakti", "aastha", "sanskar", "peace tv", "jain", "islamic", "christian", "shalom", "divya", "gospel", "god tv", "ethnic")
    )

    private val LANGS = listOf("tamil", "telugu", "hindi", "malayalam", "kannada", "bengali", "marathi", "punjabi", "gujarati", "english", "arabic", "urdu", "sinhala", "french", "german", "spanish", "turkish", "russian")
    private val PREFIX = Regex("^\\s*(?:\\[([^\\]]{1,12})\\]|([A-Za-z]{2,6})\\s*[|:])\\s*")

    fun guess(c: Channel): String? {
        val m = PREFIX.find(c.name)
        if (m != null) {
            val p = (m.groupValues[1].ifEmpty { m.groupValues[2] }).trim().uppercase()
            if (p.isNotEmpty()) return p
        }
        val n = " " + c.name.lowercase() + " "
        for ((g, keys) in RULES) for (k in keys) if (n.contains(k)) return g
        for (l in LANGS) if (n.contains(l)) return l.replaceFirstChar { it.uppercase() }
        return null
    }

    fun autoGroup(list: List<Channel>, onlyUngrouped: Boolean): List<Channel> = list.map { c ->
        if (onlyUngrouped && c.group.isNotBlank()) c else guess(c)?.let { c.copy(group = it) } ?: c
    }

    val QUICK: List<Pair<String, (String) -> String>> = listOf(
        "Remove country / language prefix (IN| , UK: , [TAMIL])" to { s -> PREFIX.replaceFirst(s, "").trim() },
        "Remove quality tags (HD, FHD, SD, 4K)" to { s ->
            s.replace(Regex("(?i)\\s*[\\[(]?\\b(?:fhd|uhd|hd|sd|4k|hevc|h265|h264)\\b[\\])]?"), "").replace(Regex("\\s+"), " ").trim()
        },
        "Remove emojis & odd symbols" to { s -> s.replace(Regex("[\\p{So}\\p{Cs}\\uFE0F\\u200D]"), "").replace(Regex("\\s+"), " ").trim() },
        "Collapse spaces & trim" to { s -> s.replace(Regex("[\\u200B-\\u200F\\uFEFF]"), "").replace(Regex("\\s+"), " ").trim() }
    )
}
