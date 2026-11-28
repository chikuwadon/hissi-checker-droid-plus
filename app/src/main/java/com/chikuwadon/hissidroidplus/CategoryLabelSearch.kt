package com.chikuwadon.hissidroidplus

import java.text.Normalizer
import java.util.Locale

/** Stable original positions, including duplicated labels from different category codes. */
object CategoryLabelSearch {
    private fun normal(value: String): String =
        Normalizer.normalize(value, Normalizer.Form.NFKC).lowercase(Locale.ROOT).trim()

    @JvmStatic fun indices(keys: List<String>, query: String): List<Int> {
        val tokens = normal(query).split(Regex("\\s+")).filter { it.isNotEmpty() }
        return keys.indices.filter { index ->
            val text = normal(keys[index])
            tokens.all { text.contains(it) }
        }
    }
}
