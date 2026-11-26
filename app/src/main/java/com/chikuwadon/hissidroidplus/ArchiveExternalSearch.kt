package com.chikuwadon.hissidroidplus

import java.net.URLEncoder
import java.nio.charset.Charset

/** Exact GET form fields verified in the saved provider search forms.
 *  Shared by native search and the external browser. */
object ArchiveExternalSearch {
    private val formCharset = Charset.forName("windows-31j")

    @JvmStatic
    @JvmOverloads
    fun url(provider: ArchiveProvider, query: ArchiveQuery, page: Int = 1): String {
        query.validate()
        require(page in 1..ArchiveRepository.MAX_PAGE)
        val (base, parameters) = when (provider) {
            ArchiveProvider.EDGE_KYODEMO, ArchiveProvider.ALL_KYODEMO, ArchiveProvider.FIVECH_KYODEMO -> {
                val key = query.keyword.trim() + query.exclude.trim().split(Regex("\\s+"))
                    .filter { it.isNotBlank() }.joinToString("") { " -" + it.trimStart('-') }
                val edge = provider == ArchiveProvider.EDGE_KYODEMO
                val baseUrl = if (edge) "https://www.kyodemo.net/sdemo/b/e_e_liveedge/" +
                    if (page == 1) "" else ((page.toLong() - 1) * 50 + 1).toString()
                    else "https://www.kyodemo.net/sdemo/s"
                baseUrl to linkedMapOf(
                    "bs" to "", "kt" to when (provider) {
                        ArchiveProvider.EDGE_KYODEMO -> "k_"
                        ArchiveProvider.ALL_KYODEMO -> "ak"
                        else -> "5_"
                    },
                    "fr" to query.from, "to" to query.to, "k" to key).apply {
                    if (edge) {
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
                    } else if (page > 1) put("j", page.toString())
                }
            }
            ArchiveProvider.FIVECH_NUKO -> {
                val fields = linkedMapOf("q" to query.keyword.trim(), "kako" to "1", "guid" to "ON")
                if (query.exact) fields["qs"] = "完全一致"
                if (query.externalBoards) fields["ex"] = "1"
                if (page > 1) fields["p"] = (page - 1).toString()
                "https://n2ch.net/q/-/" to fields
            }
            ArchiveProvider.EDGE_ARCHIVE -> return ArchiveRepository.url(query, page)
        }
        return base + "?" + parameters.entries.joinToString("&") {
            it.key + "=" + URLEncoder.encode(it.value, formCharset.name())
        }
    }

    @JvmStatic
    fun metadata(entry: ArchiveThread): String =
        listOfNotNull(entry.date.takeIf { it.isNotBlank() }, "レス: " + entry.replies, entry.board.takeIf { it.isNotBlank() }).joinToString("　")
}
