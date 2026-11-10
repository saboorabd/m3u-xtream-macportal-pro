package com.m3upro

object M3u {
    private val attr = Regex("""([\w-]+)="([^"]*)"""")

    fun parse(text: String): List<Channel> {
        val out = ArrayList<Channel>()
        var name = ""; var group = ""; var logo = ""; var id = ""
        for (raw in text.lineSequence()) {
            val l = raw.trim()
            if (l.isEmpty()) continue
            when {
                l.startsWith("#EXTINF", true) -> {
                    val a = attr.findAll(l).associate { it.groupValues[1].lowercase() to it.groupValues[2] }
                    name = l.substringAfterLast(",", "").trim()
                    group = a["group-title"] ?: ""
                    logo = a["tvg-logo"] ?: ""
                    id = a["tvg-id"] ?: ""
                }
                l.startsWith("#EXTGRP:", true) -> group = l.substringAfter(":").trim()
                l.startsWith("#") -> {}
                else -> {
                    out.add(Channel(name.ifEmpty { l }, l, group, logo, id))
                    name = ""; group = ""; logo = ""; id = ""
                }
            }
        }
        return out
    }

    fun epgUrl(text: String): String {
        val head = text.lineSequence().firstOrNull { it.startsWith("#EXTM3U") } ?: return ""
        return Regex("""(?:url-tvg|x-tvg-url)="([^"]*)"""").find(head)
            ?.groupValues?.get(1)?.split(",")?.firstOrNull()?.trim() ?: ""
    }

    fun export(list: List<Channel>, epg: String = ""): String {
        val sb = StringBuilder("#EXTM3U")
        if (epg.isNotEmpty()) sb.append(" url-tvg=\"$epg\"")
        sb.append("\n")
        for (c in list) {
            sb.append("#EXTINF:-1")
            if (c.tvgId.isNotEmpty()) sb.append(" tvg-id=\"${c.tvgId}\"")
            if (c.logo.isNotEmpty()) sb.append(" tvg-logo=\"${c.logo}\"")
            if (c.group.isNotEmpty()) sb.append(" group-title=\"${c.group}\"")
            sb.append(",").append(c.name).append("\n").append(c.url).append("\n")
        }
        return sb.toString()
    }
}
