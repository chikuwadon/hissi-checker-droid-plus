package com.chikuwadon.hissidroidplus

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.util.Locale
import java.util.regex.Pattern

/** Bounded recent history and an independent, non-evicting favorites collection. */
object SearchHistory {
    private const val STORE = "search_history_store"
    private const val RECENT = "recent_v1"
    private const val FAVORITES = "favorites_v1"
    private const val LIMIT = 100
    private val RESULT_DAY = Pattern.compile("/read\\.php/[A-Za-z0-9_]+/(\\d{8})/")

    /** Fields and JSON keys deliberately remain compatible with the Java version. */
    class Entry(
        @JvmField val url: String,
        @JvmField val site: String,
        @JvmField val board: String,
        @JvmField val date: String,
        @JvmField val id: String,
        @JvmField val viewedAt: Long,
        @JvmField val posts: Int
    ) {
        fun title(): String = (if (site == "edge") "エッヂ" else "5ch") +
            " / " + (if (board.isEmpty()) "不明な板" else board)

        fun effectiveDate(): String {
            if (date.matches(Regex("\\d{8}"))) return date
            val matched = RESULT_DAY.matcher(url)
            return if (matched.find()) matched.group(1) else date
        }

        fun detail(): String {
            val day = effectiveDate()
            val label = if (day.matches(Regex("\\d{8}"))) {
                day.substring(0, 4) + "/" + day.substring(4, 6) + "/" + day.substring(6)
            } else day
            val idLabel = if (id.isEmpty()) "" else
                "  ID:" + id.replaceFirst(Regex("(?i)^ID\\s*[:：]\\s*"), "")
            return label + idLabel + "  レス:" + posts
        }
    }

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(STORE, Context.MODE_PRIVATE)

    private fun load(context: Context, key: String): MutableList<Entry> {
        val out = ArrayList<Entry>()
        val source = prefs(context).getString(key, "[]")
        try {
            val json = JSONArray(source)
            for (i in 0 until json.length()) {
                val row = json.optJSONObject(i) ?: continue
                val url = row.optString("url", "")
                if (!url.startsWith("https://") && !url.startsWith("http://")) continue
                out.add(Entry(
                    url, row.optString("site", "5ch"), row.optString("board", ""),
                    row.optString("date", ""), row.optString("id", ""),
                    row.optLong("time", 0), row.optInt("posts", 0)
                ))
            }
        } catch (_: JSONException) {
            // Preserve only valid stored entries.
        }
        return out
    }

    private fun save(context: Context, key: String, entries: List<Entry>) {
        val array = JSONArray()
        for (entry in entries) {
            val json = JSONObject()
            try {
                json.put("url", entry.url)
                json.put("site", entry.site)
                json.put("board", entry.board)
                json.put("date", entry.date)
                json.put("id", entry.id)
                json.put("time", entry.viewedAt)
                json.put("posts", entry.posts)
                array.put(json)
            } catch (_: JSONException) {
                // Ignore one malformed row.
            }
        }
        prefs(context).edit().putString(key, array.toString()).apply()
    }

    private fun dateFromUrl(url: String): String {
        val matched = RESULT_DAY.matcher(url)
        return if (matched.find()) matched.group(1) else ""
    }

    @JvmStatic
    @Synchronized
    fun record(context: Context, query: CheckerQuery?, result: CheckerResult?) {
        if (query == null || result == null || result.posts.isEmpty()) return
        val url = result.sourceUrl.ifEmpty {
            if (query.site == CheckerQuery.Site.HISSI)
                HissiAdapter.resultUrl(query.board, query.date, query.id)
            else query.url
        }
        if (url.isEmpty()) return
        var id = query.id
        if (id.isEmpty()) {
            val extracted = PostFormatter.id(result.posts[0])
            if (extracted.startsWith("ID:")) id = extracted.substring(3)
        }
        val entry = Entry(
            url, if (query.site == CheckerQuery.Site.EDGE) "edge" else "5ch",
            query.board, if (query.date.isEmpty()) dateFromUrl(url) else query.date,
            id, System.currentTimeMillis(),
            if (result.reportedPostCount >= 0) result.reportedPostCount else result.posts.size
        )
        val recent = load(context, RECENT)
        recent.removeAll { it.url == entry.url }
        recent.add(0, entry)
        if (recent.size > LIMIT) recent.subList(LIMIT, recent.size).clear()
        save(context, RECENT, recent)
        val saved = load(context, FAVORITES)
        val index = saved.indexOfFirst { it.url == url }
        if (index >= 0) {
            saved[index] = entry
            save(context, FAVORITES, saved)
        }
    }

    /** Normal history deletion must never modify favorites. */
    @JvmStatic @Synchronized
    fun deleteRecent(context: Context, url: String) {
        val recent = load(context, RECENT)
        recent.removeAll { it.url == url }
        save(context, RECENT, recent)
    }

    @JvmStatic @Synchronized
    fun deleteRecents(context: Context, urls: Set<String>) {
        val recent = load(context, RECENT)
        recent.removeAll { it.url in urls }
        save(context, RECENT, recent)
    }

    @JvmStatic @Synchronized
    fun clearRecents(context: Context) {
        save(context, RECENT, emptyList())
    }

    @JvmStatic @Synchronized
    fun recents(context: Context): List<Entry> = load(context, RECENT)

    @JvmStatic @Synchronized
    fun favorites(context: Context): List<Entry> = load(context, FAVORITES)

    @JvmStatic @Synchronized
    fun isFavorite(context: Context, url: String): Boolean =
        load(context, FAVORITES).any { it.url == url }

    @JvmStatic @Synchronized
    fun toggleFavorite(context: Context, entry: Entry) {
        val favorites = load(context, FAVORITES)
        val removed = favorites.removeAll { it.url == entry.url }
        if (!removed) favorites.add(0, entry)
        save(context, FAVORITES, favorites)
    }

    @JvmStatic
    fun sorted(source: List<Entry>, mode: Int): List<Entry> =
        when (mode) {
            1 -> source.sortedBy { it.viewedAt }
            2 -> source.sortedWith(compareBy<Entry> { it.board.lowercase(Locale.ROOT) }
                .thenByDescending { it.viewedAt })
            3 -> source.sortedWith(compareByDescending<Entry> { it.effectiveDate() }
                .thenByDescending { it.viewedAt })
            else -> source.sortedByDescending { it.viewedAt }
        }
}
