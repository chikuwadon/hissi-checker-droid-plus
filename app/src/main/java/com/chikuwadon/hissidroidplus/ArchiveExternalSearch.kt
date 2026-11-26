package com.chikuwadon.hissidroidplus

import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/** Exact GET form fields verified in the saved provider search forms.
 *  Result HTML is not yet verified; these URLs are opened in the external browser. */
object ArchiveExternalSearch {
    @JvmStatic
    fun url(provider: ArchiveProvider, query: ArchiveQuery): String {
        query.validate()
        val (base, parameters) = when (provider) {
            ArchiveProvider.EDGE_KYODEMO, ArchiveProvider.FIVECH_KYODEMO -> {
                val key = query.keyword.trim() + query.exclude.trim().split(Regex("\\s+"))
                    .filter { it.isNotBlank() }.joinToString("") { " -" + it.trimStart('-') }
                "https://www.kyodemo.net/sdemo/s" to linkedMapOf(
                    "bs" to "", "kt" to if (provider == ArchiveProvider.EDGE_KYODEMO) "ak" else "5_",
                    "fr" to query.from, "to" to query.to, "k" to key)
            }
            ArchiveProvider.FIVECH_NUKO -> {
                val fields = linkedMapOf("q" to query.keyword.trim(), "kako" to "1", "guid" to "ON")
                if (query.exact) fields["qs"] = "完全一致"
                if (query.externalBoards) fields["ex"] = "1"
                "https://n2ch.net/q/-/" to fields
            }
            ArchiveProvider.EDGE_ARCHIVE -> return ArchiveRepository.url(query, 1)
        }
        return base + "?" + parameters.entries.joinToString("&") {
            it.key + "=" + URLEncoder.encode(it.value, StandardCharsets.UTF_8.name())
        }
    }

    @JvmStatic
    fun metadata(entry: ArchiveThread): String =
        listOfNotNull(entry.date.takeIf { it.isNotBlank() }, "レス: " + entry.replies).joinToString("　")
}
