package com.m3upro

import org.json.JSONObject
import java.io.Reader

object Json {
    /**
     * Streams a top-level JSON array of objects: constant memory even for 50 MB provider lists.
     * Throws IllegalStateException with the server text if the response is not an array.
     */
    fun eachObject(r: Reader, cb: (JSONObject) -> Unit) {
        val buf = CharArray(65536)
        var depth = 0
        var inStr = false
        var esc = false
        var started = false
        val sb = StringBuilder()
        var n = r.read(buf)
        while (n > 0) {
            for (i in 0 until n) {
                val ch = buf[i]
                if (!started) {
                    if (ch.isWhitespace() || ch == '\uFEFF') continue
                    if (ch != '[') {
                        val rest = StringBuilder().append(ch)
                        var k = i + 1
                        while (k < n && rest.length < 200) { rest.append(buf[k]); k++ }
                        throw IllegalStateException("Server said: " + rest.toString().replace(Regex("\\s+"), " ").take(160))
                    }
                    started = true; depth = 1
                    continue
                }
                if (inStr) {
                    if (depth >= 2) sb.append(ch)
                    if (esc) esc = false else if (ch == '\\') esc = true else if (ch == '"') inStr = false
                } else when (ch) {
                    '"' -> { inStr = true; if (depth >= 2) sb.append(ch) }
                    '{', '[' -> { depth++; if (depth >= 2) sb.append(ch) }
                    '}', ']' -> {
                        if (depth >= 2) sb.append(ch)
                        depth--
                        if (depth == 1 && ch == '}') { cb(JSONObject(sb.toString())); sb.setLength(0) }
                    }
                    else -> if (depth >= 2) sb.append(ch)
                }
            }
            n = r.read(buf)
        }
        if (!started) throw IllegalStateException("Empty response from server")
    }
}
