package com.chikuwadon.hissidroidplus

import java.text.Normalizer
import java.util.Locale

/** Local search across the same official/cached board catalogue as category selection. */
object BoardPickerSearch {
    data class Entry(val code: String, val name: String, val category: String) {
        internal val key = normalize(name + " " + code + " " + category)
    }

    private fun normalize(value: String): String =
        Normalizer.normalize(value, Normalizer.Form.NFKC).lowercase(Locale.ROOT)

    @JvmStatic
    fun index(groups: List<BoardMenu.Category>): List<Entry> =
        groups.flatMap { group -> group.boards.map { Entry(it.code, it.name, group.name) } }
            .distinctBy { it.code }

    @JvmStatic
    fun filter(index: List<Entry>, query: String): List<Entry> {
        val terms = normalize(query).trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (terms.isEmpty()) return index
        return index.filter { entry -> terms.all { entry.key.contains(it) } }
    }
}

object ImeInsets {
    /** Insets overlap at the bottom edge; adding them would count navigation twice. */
    @JvmStatic fun bottom(systemBars: Int, ime: Int): Int = maxOf(systemBars, ime)
}
