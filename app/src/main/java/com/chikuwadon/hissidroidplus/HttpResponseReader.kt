package com.chikuwadon.hissidroidplus

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.InterruptedIOException

/** A worker sees its own most recent request, not another screen's concurrent response. */
internal class HttpRequestDiagnostics {
    private val local = ThreadLocal<String>()
    fun get(): String = local.get() ?: "未取得"
    fun set(value: String) = local.set(value)
}

/** Bounded response buffering; deliberately independent of Android and HTTP connections. */
object HttpResponseReader {
    @JvmStatic
    @Throws(InterruptedIOException::class)
    fun checkInterrupted() {
        if (Thread.currentThread().isInterrupted) throw InterruptedIOException("通信を中断しました")
    }

    @JvmStatic
    @Throws(IOException::class)
    fun read(input: InputStream, limit: Int, expectedLength: Long): ByteArray {
        require(limit > 0)
        if (expectedLength > limit) throw IOException("HTMLが大きすぎます")
        val initial = if (expectedLength > 0) expectedLength.coerceAtMost(64 * 1024L).toInt()
            else minOf(8192, limit)
        val output = ByteArrayOutputStream(initial)
        val buffer = ByteArray(minOf(8192, limit))
        while (true) {
            checkInterrupted()
            val n = input.read(buffer)
            checkInterrupted()
            if (n < 0) break
            if (n == 0) {
                // Defensive progress for a nonconforming stream.
                val single = input.read()
                checkInterrupted()
                if (single < 0) break
                if (output.size() >= limit) throw IOException("HTMLが大きすぎます")
                output.write(single)
            } else {
                if (n > limit - output.size()) throw IOException("HTMLが大きすぎます")
                output.write(buffer, 0, n)
            }
        }
        return output.toByteArray()
    }
}
