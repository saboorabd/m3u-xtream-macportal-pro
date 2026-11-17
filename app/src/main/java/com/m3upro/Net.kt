package com.m3upro

import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.InputStream
import java.util.concurrent.TimeUnit

/** OkHttp implementation of [Http] (the protocol code itself is plain Kotlin and unit-tested on the JVM). */
class OkHttpHttp : Http {
    private val base: OkHttpClient = OkHttpClient.Builder()
        .followRedirects(true).followSslRedirects(true).retryOnConnectionFailure(true).build()

    private fun client(t: Int, withCallTimeout: Boolean): OkHttpClient {
        val b = base.newBuilder()
            .connectTimeout(minOf(t, 20).toLong(), TimeUnit.SECONDS)
            .readTimeout(t.toLong(), TimeUnit.SECONDS)
        if (withCallTimeout) b.callTimeout((t * 3).toLong(), TimeUnit.SECONDS) else b.callTimeout(0, TimeUnit.SECONDS)
        return b.build()
    }

    private fun req(url: String, headers: Map<String, String>): Request {
        val b = Request.Builder().url(url)
        if (!headers.containsKey("User-Agent")) b.header("User-Agent", Net.UA)
        for ((k, v) in headers) b.header(k, v)
        return b.build()
    }

    override fun text(url: String, headers: Map<String, String>, timeoutSec: Int): String =
        client(timeoutSec, true).newCall(req(url, headers)).execute().use { r ->
            if (!r.isSuccessful) throw HttpError(r.code, "HTTP ${r.code}")
            r.body!!.string()
        }

    override fun open(url: String, headers: Map<String, String>, timeoutSec: Int): InputStream {
        val r = client(timeoutSec, false).newCall(req(url, headers)).execute()
        if (!r.isSuccessful) { val c = r.code; r.close(); throw HttpError(c, "HTTP $c") }
        return r.body!!.byteStream()
    }

    override fun peek(url: String, headers: Map<String, String>, timeoutSec: Int, maxBytes: Int): Resp {
        val t0 = System.nanoTime()
        client(timeoutSec, true).newCall(req(url, headers)).execute().use { r ->
            val ms = ((System.nanoTime() - t0) / 1_000_000).toInt()
            val ct = r.header("Content-Type") ?: ""
            val head = try { r.peekBody(maxBytes.toLong()).bytes() } catch (e: Exception) { ByteArray(0) }
            return Resp(r.code, ct, head, ms, r.request.url.toString())
        }
    }
}

object Net {
    const val UA = "VLC/3.0.18 LibVLC/3.0.18"
    val http: Http = OkHttpHttp()

    fun friendly(e: Throwable): String = Errs.friendly(e)

    fun reachable(url: String): Boolean =
        try { http.peek(url, emptyMap(), 10, 64).code in 200..399 } catch (e: Exception) { false }
}
