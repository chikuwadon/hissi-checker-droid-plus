package com.chikuwadon.hissidroidplus

import java.net.URLEncoder
import java.nio.charset.Charset

/** Exact GET form fields verified in the saved provider search forms.
 *  Shared by native search and the external browser. */
object ArchiveExternalSearch {
    private val formCharset = Charset.forName("windows-31j")
    private val whitespace = Regex("\\s+")

    // Browser form encoding operates on encoded bytes, not UTF-16 characters.
    // For example 原神 ends in the Shift_JIS byte 0x5f, which Chromium sends as "_".
    @JvmStatic fun encodeKyodemoField(value: String): String = buildString {
        val hex = "0123456789ABCDEF"
        for (byte in value.toByteArray(formCharset)) {
            val b = byte.toInt() and 255
            when {
                b == 32 -> append('+')
                b in 65..90 || b in 97..122 || b in 48..57 || b == 42 || b == 45 || b == 46 || b == 95 -> append(b.toChar())
                else -> { append('%'); append(hex[b ushr 4]); append(hex[b and 15]) }
            }
        }
    }

    @JvmStatic
    @JvmOverloads
    fun url(provider: ArchiveProvider, query: ArchiveQuery, page: Int = 1): String {
        query.validate()
        require(page in 1..ArchiveRepository.MAX_PAGE)
        val (base, parameters) = when (provider) {
            ArchiveProvider.EDGE_KYODEMO, ArchiveProvider.FIVECH_KYODEMO, ArchiveProvider.OTHER_KYODEMO -> {
                val key = query.keyword.trim() + query.exclude.trim().split(whitespace)
                    .filter { it.isNotBlank() }.joinToString("") { " -" + it.trimStart('-') }
                val board = if (provider == ArchiveProvider.EDGE_KYODEMO) "e_e_liveedge" else query.boardCode
                require(board.matches(Regex("[A-Za-z0-9_]{1,60}"))) { "板を選択してください" }
                if (provider == ArchiveProvider.OTHER_KYODEMO) require(KyodemoOtherBoards.validBoard(board)) { "その他の掲示板を選択してください" }
                val baseUrl = "https://www.kyodemo.net/sdemo/b/" + board + "/" +
                    if (page == 1) "" else ((page.toLong() - 1) * 50 + 1).toString()
                baseUrl to linkedMapOf("bs" to "", "kt" to "k_",
                    "fr" to query.from, "to" to query.to, "k" to key).apply {
                    // The captured successful all-log form for Punipuni has no rf parameter.
                    // rf is a board-specific link option, not a search-form field.
                    when (query.sort) {
                        "old" -> put("j", "a")
                        "replies" -> put("r", "1")
                        else -> if (page > 1) put("j", "1")
                    }
                    when (query.minimum) {
                        "50" -> put("o1", "1")
                        "300" -> put("o2", "1")
                        "900" -> put("o3", "1")
                    }
                }
            }
            ArchiveProvider.FIVECH_NUKO -> {
                val fields = linkedMapOf("q" to query.keyword.trim(), "kako" to "1", "guid" to "ON")
                if (query.boardCode.isNotEmpty()) fields["b"] = query.boardCode
                if (query.exact) fields["qs"] = "完全一致"
                if (query.externalBoards) fields["ex"] = "1"
                if (page > 1) fields["p"] = (page - 1).toString()
                "https://n2ch.net/q/-/" to fields
            }
            ArchiveProvider.EDGE_ARCHIVE -> return ArchiveRepository.url(query, page)
        }
        return base + "?" + parameters.entries.joinToString("&") {
            it.key + "=" + if (provider == ArchiveProvider.FIVECH_NUKO)
                URLEncoder.encode(it.value, formCharset.name()) else encodeKyodemoField(it.value)
        }
    }

    @JvmStatic
    fun metadata(entry: ArchiveThread): String =
        listOfNotNull(entry.date.takeIf { it.isNotBlank() }, "レス: " + entry.replies, entry.board.takeIf { it.isNotBlank() }).joinToString("　")
}
