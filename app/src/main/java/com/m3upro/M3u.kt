package com.m3upro

import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.io.StringReader

data class Parsed(val channels: List<Channel>, val epg: String)

object M3u {
    private val MANAGED = setOf("tvg-id", "tvg-logo", "group-title")

    fun parse(text: String): List<Channel> = parseReader(BufferedReader(StringReader(text))).channels

    fun parseStream(ins: InputStream): Parsed =
        parseReader(BufferedReader(InputStreamReader(ins, Charsets.UTF_8), 1 shl 16))

    /** Reads attributes of an #EXTINF line without regex (fast for 50k+ channels). Returns display name. */
    private fun extinf(line: String, attrs: ArrayList<Pair<String, String>>): String {
        var i = line.indexOf(':')
        i = if (i < 0) line.length else i + 1
        val n = line.length
        // duration
        while (i < n && line[i] != ' ' && line[i] != ',' && line[i] != '\t') i++
        while (i < n) {
            while (i < n && (line[i] == ' ' || line[i] == '\t')) i++
            if (i >= n) break
            if (line[i] == ',') return line.substring(i + 1).trim()
            val ks = i
            while (i < n && line[i] != '=' && line[i] != ' ' && line[i] != ',') i++
            val key = line.substring(ks, i)
            if (i < n && line[i] == '=') {
                i++
                if (i < n && (line[i] == '"' || line[i] == '\'')) {
                    val q = line[i]
                    i++
                    val vs = i
                    while (i < n && line[i] != q) i++
                    attrs.add(key to line.substring(vs, minOf(i, n)))
                    if (i < n) i++
                } else {
                    val vs = i
                    while (i < n && line[i] != ' ' && line[i] != ',') i++
                    attrs.add(key to line.substring(vs, i))
                }
            }
        }
        return ""
    }

    fun parseReader(br: BufferedReader): Parsed {
        val out = ArrayList<Channel>()
        var epg = ""
        var name = ""; var group = ""; var logo = ""; var tvg = ""; var extra = ""
        var drm = ""; var key = ""; var ua = ""; var ref = ""
        val tags = StringBuilder()
        var have = false
        val attrs = ArrayList<Pair<String, String>>()
        var line = br.readLine()
        var first = true
        while (line != null) {
            var l = line.trim()
            if (first) { l = l.trimStart('\uFEFF'); first = false }
            if (l.isNotEmpty()) {
                if (l.startsWith("#EXTM3U", true)) {
                    val a = ArrayList<Pair<String, String>>()
                    extinf(l.replaceFirst("#EXTM3U", "#EXTINF:0"), a)
                    for ((k, v) in a) if (k.equals("url-tvg", true) || k.equals("x-tvg-url", true)) epg = v.split(",").first().trim()
                } else if (l.startsWith("#EXTINF", true)) {
                    attrs.clear()
                    name = extinf(l, attrs)
                    val ex = StringBuilder()
                    for ((k, v) in attrs) {
                        when (k.lowercase()) {
                            "tvg-id" -> tvg = v
                            "tvg-logo" -> logo = v
                            "group-title" -> group = v
                            else -> ex.append(' ').append(k).append("=\"").append(v).append('"')
                        }
                    }
                    extra = ex.toString()
                    have = true
                } else if (l.startsWith("#EXTGRP:", true)) {
                    if (group.isEmpty()) group = l.substringAfter(":").trim()
                } else if (l.startsWith("#KODIPROP:", true)) {
                    val kv = l.substringAfter(":")
                    val k = kv.substringBefore("=").lowercase()
                    val v = kv.substringAfter("=", "").trim()
                    if (k.endsWith("license_type")) drm = v
                    else if (k.endsWith("license_key")) key = v
                    else tags.append(l).append('\n')
                } else if (l.startsWith("#EXTVLCOPT:", true)) {
                    val kv = l.substringAfter(":")
                    val k = kv.substringBefore("=").lowercase()
                    val v = kv.substringAfter("=", "").trim()
                    if (k == "http-user-agent") ua = v
                    else if (k == "http-referrer" || k == "http-referer") ref = v
                    else tags.append(l).append('\n')
                } else if (l.startsWith("#")) {
                    tags.append(l).append('\n')
                } else {
                    var url = l
                    if (url.contains("|")) {
                        val parts = url.split("|", limit = 2)
                        url = parts[0]
                        for (h in parts[1].split("&")) {
                            val kv = h.split("=", limit = 2)
                            if (kv.size == 2) {
                                val hk = kv[0].lowercase()
                                if (hk == "user-agent") ua = kv[1] else if (hk == "referer" || hk == "referrer") ref = kv[1]
                            }
                        }
                    }
                    out.add(Channel(
                        name = if (have && name.isNotEmpty()) name else url, url = url, group = group, logo = logo,
                        tvgId = tvg, drm = drm, key = key, ua = ua, ref = ref, extra = extra,
                        tags = tags.toString().trimEnd('\n')
                    ))
                    name = ""; group = ""; logo = ""; tvg = ""; extra = ""
                    drm = ""; key = ""; ua = ""; ref = ""
                    tags.setLength(0); have = false
                }
            }
            line = br.readLine()
        }
        return Parsed(out, epg)
    }

    fun export(list: List<Channel>, epg: String = ""): String {
        val sb = StringBuilder("#EXTM3U")
        if (epg.isNotEmpty()) sb.append(" url-tvg=\"").append(epg).append('"')
        sb.append('\n')
        for (c in list) {
            if (c.drm.isNotEmpty()) sb.append("#KODIPROP:inputstream.adaptive.license_type=").append(c.drm).append('\n')
            if (c.key.isNotEmpty()) sb.append("#KODIPROP:inputstream.adaptive.license_key=").append(c.key).append('\n')
            if (c.ua.isNotEmpty()) sb.append("#EXTVLCOPT:http-user-agent=").append(c.ua).append('\n')
            if (c.ref.isNotEmpty()) sb.append("#EXTVLCOPT:http-referrer=").append(c.ref).append('\n')
            sb.append("#EXTINF:-1")
            if (c.tvgId.isNotEmpty()) sb.append(" tvg-id=\"").append(c.tvgId).append('"')
            if (c.logo.isNotEmpty()) sb.append(" tvg-logo=\"").append(c.logo).append('"')
            if (c.group.isNotEmpty()) sb.append(" group-title=\"").append(c.group).append('"')
            sb.append(c.extra)
            sb.append(',').append(c.name).append('\n')
            if (c.tags.isNotEmpty()) sb.append(c.tags).append('\n')
            sb.append(c.url).append('\n')
        }
        return sb.toString()
    }
}
