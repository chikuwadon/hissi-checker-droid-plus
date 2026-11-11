package com.chikuwadon.hissidroidplus

import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets

/** Preserve valid UTF-8, falling back to Shift_JIS only for malformed raw source bytes. */
object HissiHtmlDecoder {
    const val FALLBACK_MARKER: String = "\u0001HISSI_SHIFT_JIS\u0001"
    private val SJIS: Charset = Charset.forName("Shift_JIS")

    @JvmStatic
    fun decode(bytes: ByteArray, declared: Charset, hissi: Boolean): String {
        val initial = String(bytes, declared)
        if (!hissi || declared != StandardCharsets.UTF_8) return initial
        try {
            StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
            return initial
        } catch (_: CharacterCodingException) {
            val alternative = String(bytes, SJIS)
            return if (countReplacement(alternative) < countReplacement(initial)) {
                FALLBACK_MARKER + alternative
            } else initial
        }
    }

    @JvmStatic
    fun countReplacement(value: String): Int = value.count { it == '\uFFFD' }
}
