package com.chikuwadon.hissidroidplus

import android.content.Context
import org.json.JSONObject

/** Keep other boards separate from the existing 5ch favorite codes. */
object OtherBoardSelection {
    private fun prefs(context: Context) = context.getSharedPreferences("other_archive_boards", Context.MODE_PRIVATE)
    @JvmOverloads fun root(context: Context, board: String = ""): String {
        val roots = KyodemoOtherBoards.services.map { it.root }
        return roots.firstOrNull { board.startsWith(it) }
            ?: prefs(context).getString("root", "").orEmpty().takeIf { it in roots }.orEmpty()
    }
    fun setRoot(context: Context, root: String) {
        require(KyodemoOtherBoards.services.any { it.root == root })
        prefs(context).edit().putString("root", root).apply()
    }
    private fun entries(context: Context): JSONObject = runCatching {
        JSONObject(prefs(context).getString("favorites", "{}").orEmpty())
    }.getOrElse { JSONObject() }
    fun favorites(context: Context, root: String): List<KyodemoOtherBoards.Item> {
        if (KyodemoOtherBoards.services.none { it.root == root }) return emptyList()
        val j = entries(context)
        return j.keys().asSequence().filter { it.startsWith(root) && KyodemoOtherBoards.validBoard(it) }
            .map { KyodemoOtherBoards.Item(it, j.optString(it, it).take(100).ifBlank { it }) }
            .sortedBy { it.label }.toList()
    }
    fun add(context: Context, code: String, label: String) {
        require(KyodemoOtherBoards.validBoard(code))
        val j = entries(context)
        j.put(code, label.take(100).ifBlank { code })
        prefs(context).edit().putString("favorites", j.toString()).apply()
    }
}
