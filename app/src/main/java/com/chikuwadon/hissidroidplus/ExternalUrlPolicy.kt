package com.chikuwadon.hissidroidplus

import java.net.URI

/** Strict URI parsing at the boundary between untrusted HTML and external navigation. */
object ExternalUrlPolicy {
    private fun webUri(raw: String?): URI? {
        if (raw.isNullOrBlank() || raw.any { it.isISOControl() }) return null
        return try {
            URI.create(raw).takeIf {
                (it.scheme.equals("https", true) || it.scheme.equals("http", true)) &&
                    !it.host.isNullOrEmpty() && it.rawUserInfo == null && !it.isOpaque &&
                    (it.port == -1 || it.port in 1..65535)
            }
        } catch (_: IllegalArgumentException) { null }
    }

    @JvmStatic fun isWebUrl(raw: String?): Boolean = webUri(raw) != null

    /** The embedded checker may only read DOM from the expected HTTPS origins. */
    @JvmStatic fun isCheckerWebOrigin(raw: String?): Boolean {
        val uri = webUri(raw) ?: return false
        return uri.scheme.equals("https", true) && uri.port == -1 &&
            (uri.host.equals("kyodemo.net", true) || uri.host.equals("www.kyodemo.net", true))
    }
}
