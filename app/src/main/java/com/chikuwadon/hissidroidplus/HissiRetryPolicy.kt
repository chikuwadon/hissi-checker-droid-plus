package com.chikuwadon.hissidroidplus

import java.io.IOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketException
import java.net.SocketTimeoutException

/** Retry the known hissi.org endpoint only for transport-level failures. */
object HissiRetryPolicy {
    @JvmStatic
    fun shouldRetry(url: String?, failure: IOException): Boolean {
        if (url == null || !url.startsWith("http://hissi.org/read.php/")) return false
        var cause: Throwable? = failure
        while (cause != null) {
            if (cause is SocketTimeoutException ||
                cause is ConnectException ||
                cause is NoRouteToHostException ||
                cause is SocketException
            ) return true
            cause = cause.cause
        }
        return false
    }
}
