package com.chikuwadon.hissidroidplus

import android.content.Context
import org.json.JSONArray

/** Independent keyword-only history, newest first, bounded and stored locally. */
object ArchiveKeywordHistory {
    private const val KEY = "archive_keyword_history_v1"
    private const val LIMIT = 100
    fun read(context: Context): List<String> {
        val raw = AppSettings.prefs(context).getString(KEY, "[]") ?: "[]"
        val json = runCatching { JSONArray(raw) }.getOrNull() ?: return emptyList()
        return (0 until minOf(json.length(), LIMIT)).mapNotNull {
            json.optString(it).takeIf { word -> word.isNotBlank() && word.length <= 200 }
        }.distinct()
    }
    @JvmStatic
    fun recent(old: List<String>, keyword: String): List<String> {
        val word = keyword.trim()
        if (word.isBlank() || word.length > 200) return old.take(LIMIT)
        return (listOf(word) + old.filterNot { it == word }).take(LIMIT)
    }
    fun record(context: Context, keyword: String) = write(context, recent(read(context), keyword))
    fun remove(context: Context, keyword: String) = write(context, read(context).filterNot { it == keyword })
    fun clear(context: Context) { AppSettings.prefs(context).edit().remove(KEY).apply() }
    private fun write(context: Context, entries: List<String>) {
        AppSettings.prefs(context).edit().putString(KEY, JSONArray(entries).toString()).apply()
    }
}
