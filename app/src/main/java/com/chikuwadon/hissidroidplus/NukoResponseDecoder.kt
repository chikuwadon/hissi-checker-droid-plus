package com.chikuwadon.hissidroidplus

import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.text.Normalizer

/** Search form encoding and response encoding need not be identical. */
object NukoResponseDecoder {
    @JvmStatic fun decode(bytes: ByteArray, declared: Charset): String {
        val initial = String(bytes, declared)
        fun hasResult(value: String): Boolean =
            Normalizer.normalize(value, Normalizer.Form.NFKC).contains("検索結果")
        if (hasResult(initial)) return initial
        for (charset in listOf(StandardCharsets.UTF_8, Charset.forName("windows-31j"), Charset.forName("EUC-JP"))) {
            if (charset == declared) continue
            val candidate = String(bytes, charset)
            if (hasResult(candidate)) return candidate
        }
        return initial
    }
}
