package com.m3upro

import java.net.URI

/** Real stream check: status code + content sniffing (+ optional HLS segment test). Pure Kotlin. */
object Probe {
    private val FILE = Regex("(?i)\\.(mp4|mkv|avi|mov|mp3|aac|flv|webm|m4a)(\\?|$)")

    private fun isTs(b: ByteArray): Boolean {
        for (off in 0 until minOf(188, b.size)) {
            if (b[off] != 0x47.toByte()) continue
            if (off + 188 < b.size) {
                if (b[off + 188] == 0x47.toByte() && (off + 376 >= b.size || b[off + 376] == 0x47.toByte())) return true
            } else if (off == 0 && b.size >= 4) return true
        }
        return false
    }

    fun sniff(b: ByteArray, ct: String): String {
        if (b.isEmpty()) return "empty"
        val s = String(b, Charsets.ISO_8859_1)
        val t = s.trimStart('\uFEFF', ' ', '\r', '\n', '\t', '\u00EF', '\u00BB', '\u00BF')
        if (t.startsWith("#EXTM3U")) return "hls"
        if (isTs(b)) return "ts"
        if (b.size >= 12) {
            val sig = s.substring(4, 8)
            if (sig == "ftyp" || sig == "moov" || sig == "styp" || sig == "moof") return "mp4"
        }
        if (b.size >= 4 && b[0] == 0x1A.toByte() && b[1] == 0x45.toByte() && b[2] == 0xDF.toByte() && b[3] == 0xA3.toByte()) return "mkv"
        if (s.startsWith("FLV")) return "flv"
        if (s.startsWith("OggS") || s.startsWith("ID3") || s.startsWith("RIFF") || s.startsWith("fLaC")) return "audio"
        if (b.size >= 2 && (b[0].toInt() and 0xFF) == 0xFF && (b[1].toInt() and 0xE0) == 0xE0) return "audio"
        val lower = t.take(400).lowercase()
        if (lower.contains("<mpd")) return "dash"
        if (lower.startsWith("<")) return "html"
        val snippet = t.replace(Regex("\\s+"), " ").take(70)
        if (ct.contains("json") || (ct.startsWith("text/") && !ct.contains("mpegurl"))) return "text:$snippet"
        val printable = s.count { it == '\n' || it == '\r' || it == '\t' || it in ' '..'~' }
        if (b.size < 1024 && printable * 10 >= s.length * 9) return "text:$snippet"
        return "binary"
    }

    private fun resolve(base: String, ref: String): String {
        if (ref.startsWith("http", true)) return ref
        return try { URI(base).resolve(ref).toString() } catch (e: Exception) { base.substringBeforeLast('/') + "/" + ref }
    }

    private fun firstUri(txt: String, afterTag: String): String? {
        val i = txt.indexOf(afterTag)
        if (i < 0) return null
        for (line in txt.substring(i).lineSequence().drop(1)) {
            val l = line.trim()
            if (l.isNotEmpty() && !l.startsWith("#")) return l
        }
        return null
    }

    private fun hls(http: Http, url: String, h: Map<String, String>, t: Int, ms: Int, slow: String): CheckResult {
        var cur = url
        var txt = try { http.text(cur, h, t) } catch (e: Exception) { return CheckResult(2, ms, "Playlist: " + Errs.friendly(e)) }
        var depth = 0
        while (txt.contains("#EXT-X-STREAM-INF") && depth < 2) {
            val uri = firstUri(txt, "#EXT-X-STREAM-INF") ?: return CheckResult(2, ms, "Empty variant list")
            cur = resolve(cur, uri)
            txt = try { http.text(cur, h, t) } catch (e: Exception) { return CheckResult(2, ms, "Variant: " + Errs.friendly(e)) }
            depth++
        }
        if (!txt.contains("#EXTINF") && !txt.contains("#EXT-X-TARGETDURATION")) return CheckResult(2, ms, "No segments in playlist")
        val seg = firstUri(txt, "#EXTINF") ?: return CheckResult(2, ms, "No segments in playlist")
        val sr = try { http.peek(resolve(cur, seg), h, t, 1024) } catch (e: Exception) { return CheckResult(2, ms, "Segment: " + Errs.friendly(e)) }
        if (sr.code !in 200..299) return CheckResult(2, ms, "Segment HTTP ${sr.code}")
        if (sr.head.isEmpty()) return CheckResult(2, ms, "Segment empty")
        val k = sniff(sr.head, sr.contentType.lowercase())
        if ((k == "html" || k.startsWith("text:")) && !txt.contains("#EXT-X-KEY")) return CheckResult(2, ms, "Segment is not video")
        return CheckResult(1, ms, "OK • HLS$slow")
    }

    fun check(http: Http, url: String, ua: String, ref: String, timeoutSec: Int, thorough: Boolean): CheckResult {
        if (!url.startsWith("http", true)) return CheckResult(0, 0, "Unsupported link (" + url.substringBefore(':') + ")")
        val h = HashMap<String, String>()
        h["User-Agent"] = ua.ifBlank { "VLC/3.0.18 LibVLC/3.0.18" }
        h["Accept"] = "*/*"
        if (ref.isNotBlank()) h["Referer"] = ref
        if (FILE.containsMatchIn(url)) h["Range"] = "bytes=0-1023"
        val r: Resp = try { http.peek(url, h, timeoutSec, 1024) } catch (e: Exception) {
            val d = Errs.friendly(e)
            if (d != "Timeout") return CheckResult(2, 0, d)
            try { http.peek(url, h, timeoutSec, 1024) } catch (e2: Exception) { return CheckResult(2, 0, Errs.friendly(e2)) }
        }
        val ms = r.ms
        when (r.code) {
            429, 503, 509 -> return CheckResult(0, ms, "Busy (${r.code}) - re-check later")
            401, 403 -> return CheckResult(2, ms, "HTTP ${r.code} (blocked / expired)")
            404, 410 -> return CheckResult(2, ms, "HTTP ${r.code} (not found)")
        }
        if (r.code !in 200..299) return CheckResult(2, ms, "HTTP ${r.code}")
        val slow = if (ms > 3000) " (slow ${ms / 1000}s)" else ""
        val kind = sniff(r.head, r.contentType.lowercase())
        return when {
            kind == "empty" -> CheckResult(2, ms, "Empty response")
            kind == "html" -> CheckResult(2, ms, "HTML page, not a stream")
            kind.startsWith("text:") -> CheckResult(2, ms, "Server says: " + kind.removePrefix("text:"))
            kind == "hls" -> if (thorough) hls(http, url, h, timeoutSec, ms, slow) else CheckResult(1, ms, "OK • HLS$slow")
            kind == "ts" -> CheckResult(1, ms, "OK • TS$slow")
            kind == "mp4" -> CheckResult(1, ms, "OK • MP4$slow")
            kind == "mkv" -> CheckResult(1, ms, "OK • MKV$slow")
            kind == "dash" -> CheckResult(1, ms, "OK • DASH$slow")
            kind == "flv" -> CheckResult(1, ms, "OK • FLV$slow")
            kind == "audio" -> CheckResult(1, ms, "OK • Audio$slow")
            else -> CheckResult(1, ms, "OK • Stream$slow")
        }
    }
}
