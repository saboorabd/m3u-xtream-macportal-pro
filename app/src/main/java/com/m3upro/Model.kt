package com.m3upro

data class Channel(
    val name: String,
    val url: String,
    val group: String = "",
    val logo: String = "",
    val tvgId: String = "",
    val status: Int = 0,       // 0 unchecked / unknown, 1 working, 2 dead
    val ms: Int = 0,           // time to first byte
    val note: String = "",     // "OK", "HTTP 404", "Timeout" ...
    val drm: String = "",
    val key: String = "",
    val ua: String = "",
    val ref: String = "",
    val raw: String = "",      // original portal command when url was resolved to a real link
    val extra: String = "",    // unknown #EXTINF attributes (catchup, tvg-name ...), preserved on export
    val tags: String = ""      // unknown tag lines (#EXTGRP, other #KODIPROP ...), preserved on export
)

data class Playlist(
    val id: String,
    val name: String,
    val kind: String,          // m3u | file | text | xtream | portal
    val source: String,        // url / server / portal url
    val meta: String,          // json with credentials (xtream / portal)
    val lastChecked: Long,
    val state: Int,            // 0 unknown, 1 source ok, 2 source problem
    val channels: List<Channel>,
    val info: String = "",     // account info (status, expiry, connections)
    val epg: String = ""       // url-tvg of the playlist header
)

data class PortalCfg(
    val url: String = "",
    val mac: String = "",
    val deviceId: String = "",
    val deviceId2: String = "",
    val serial: String = ""
)

data class Settings(val deep: Boolean, val timeout: Int, val concurrency: Int, val thorough: Boolean)

data class Fetched(val channels: List<Channel>, val info: String, val epg: String = "")
data class CheckResult(val status: Int, val ms: Int, val note: String)

fun hostOf(s: String): String = s.substringAfter("://").substringBefore("/").ifBlank { s }
