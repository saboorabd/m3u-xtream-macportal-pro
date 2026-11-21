package com.m3upro

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** In-app diagnostics: Settings -> "Copy debug log" gives an exact trace of what failed (secrets are masked). */
object DebugLog {
    private val lines = ArrayDeque<String>()
    private val fmt = SimpleDateFormat("HH:mm:ss", Locale.US)

    fun mask(s: String): String = s
        .replace(Regex("(?i)(password|pass|pwd|token|username|user)=[^&\\s]+"), "$1=***")
        .replace(Regex("/(live|movie|series)/[^/\\s]+/[^/\\s]+/"), "/$1/***/***/")
        .replace(Regex("(?i)mac=[0-9a-f:]{17}"), "mac=**")

    fun d(msg: String) = add("D", msg)

    fun e(msg: String, t: Throwable? = null) {
        val extra = if (t != null) " | " + t.javaClass.simpleName + ": " + (t.message ?: "") +
            (t.stackTrace.firstOrNull()?.let { " @ " + it.fileName + ":" + it.lineNumber } ?: "") else ""
        add("E", msg + extra)
    }

    private fun add(level: String, msg: String) {
        synchronized(lines) {
            lines.addLast(fmt.format(Date()) + " " + level + " " + mask(msg))
            while (lines.size > 400) lines.removeFirst()
        }
    }

    fun dump(): String = synchronized(lines) { lines.joinToString("\n") }
    fun clear() { synchronized(lines) { lines.clear() } }
}
