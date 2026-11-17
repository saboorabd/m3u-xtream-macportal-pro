package com.m3upro

import java.io.InputStream

class HttpError(val code: Int, msg: String) : Exception(msg)

class Resp(val code: Int, val contentType: String, val head: ByteArray, val ms: Int, val finalUrl: String)

/** Tiny HTTP abstraction so the protocol logic is plain Kotlin (testable without Android). */
interface Http {
    /** GET, whole body as text. Throws [HttpError] on non-2xx. */
    fun text(url: String, headers: Map<String, String> = emptyMap(), timeoutSec: Int = 20): String

    /** GET as a stream (big playlists / API arrays). Caller closes. Throws [HttpError] on non-2xx. */
    fun open(url: String, headers: Map<String, String> = emptyMap(), timeoutSec: Int = 60): InputStream

    /** GET reading only the first [maxBytes]. Never throws for HTTP error codes; throws IOException on network errors. */
    fun peek(url: String, headers: Map<String, String> = emptyMap(), timeoutSec: Int = 8, maxBytes: Int = 1024): Resp
}

object Errs {
    fun friendly(e: Throwable): String = when (e) {
        is java.net.SocketTimeoutException -> "Timeout"
        is java.io.InterruptedIOException -> "Timeout"
        is java.net.UnknownHostException -> "DNS failed"
        is java.net.ConnectException -> "Connection refused"
        is javax.net.ssl.SSLException -> "SSL error"
        is IllegalArgumentException -> "Invalid URL"
        is HttpError -> "HTTP ${e.code}"
        else -> (e.message ?: e.javaClass.simpleName).take(120)
    }
}
