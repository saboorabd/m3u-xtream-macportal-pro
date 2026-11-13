package com.m3upro

data class Channel(
    val name: String,
    val url: String,
    val group: String = "",
    val logo: String = "",
    val tvgId: String = "",
    val status: Int = 0,      // 0 unchecked, 1 working, 2 dead
    val ms: Int = 0,          // response time
    val note: String = "",    // "OK", "HTTP 404", "Timeout" ...
    val drm: String = "",
    val key: String = "",
    val ua: String = "",
    val ref: String = ""
)

data class Playlist(
    val id: String,
    val name: String,
    val kind: String,         // m3u | file | text | xtream | portal
    val source: String,       // url / server / portal url
    val meta: String,         // json with credentials (xtream / portal)
    val lastChecked: Long,
    val state: Int,           // 0 unknown, 1 source ok, 2 source problem
    val channels: List<Channel>,
    val info: String = ""     // account info (status, expiry, connections)
)

data class PortalCfg(
    val url: String = "",
    val mac: String = "",
    val deviceId: String = "",
    val deviceId2: String = "",
    val serial: String = ""
)

data class Settings(val deep: Boolean, val timeout: Int, val concurrency: Int)

fun hostOf(s: String): String = s.substringAfter("://").substringBefore("/").ifBlank { s }
