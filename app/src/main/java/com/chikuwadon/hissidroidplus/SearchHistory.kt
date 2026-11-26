package com.chikuwadon.hissidroidplus

import android.content.Context
import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log
import java.util.concurrent.Executors
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
    private const val LIMIT_KEY = "history_limit"
    private val worker = Executors.newSingleThreadExecutor()
    private var helper: Store? = null

    fun runAsync(action: () -> Unit) {
        worker.execute {
            try { action() } catch (error: Exception) {
                Log.e("SearchHistory", "History operation failed", error)
            }
        }
    }

    fun limit(context: Context): Int = HistoryRetention.normalize(
        AppSettings.prefs(context).getInt(LIMIT_KEY, HistoryRetention.DEFAULT)
    )

    fun updateLimit(context: Context, value: Int, completion: (Boolean) -> Unit) {
        val app = context.applicationContext
        runAsync {
            val success = try {
                val normalized = HistoryRetention.normalize(value)
                val database = db(app)
                check(AppSettings.prefs(app).edit().putInt(LIMIT_KEY, normalized).commit())
                trim(database, normalized)
                true
            } catch (error: Exception) {
                Log.e("SearchHistory", "Unable to change history retention", error)
                false
            }
            completion(success)
        }
    }
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

    private fun legacy(context: Context, key: String): MutableList<Entry> {
        val out = ArrayList<Entry>()
        val source = context.getSharedPreferences(STORE, Context.MODE_PRIVATE).getString(key, "[]")
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

    private fun values(entry: Entry) = ContentValues().apply {
        put("url", entry.url)
        put("site", entry.site)
        put("board", entry.board)
        put("day", entry.date)
        put("user_id", entry.id)
        put("viewed_at", entry.viewedAt)
        put("posts", entry.posts)
    }

    /** SQLiteOpenHelper wraps onCreate in a transaction: preserve legacy data until import succeeds. */
    private class Store(private val app: Context) : SQLiteOpenHelper(app, "search_history.db", null, 1) {
        override fun onCreate(db: SQLiteDatabase) {
            for ((table, key) in listOf("recent" to RECENT, "favorites" to FAVORITES)) {
                db.execSQL("CREATE TABLE $table (url TEXT PRIMARY KEY NOT NULL, site TEXT NOT NULL, board TEXT NOT NULL, day TEXT NOT NULL, user_id TEXT NOT NULL, viewed_at INTEGER NOT NULL, posts INTEGER NOT NULL)")
                db.execSQL("CREATE INDEX ${table}_viewed ON $table(viewed_at DESC)")
                for (entry in legacy(app, key)) {
                    check(db.insertWithOnConflict(table, null, values(entry), SQLiteDatabase.CONFLICT_REPLACE) != -1L)
                }
            }
        }
        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            error("Unsupported history database upgrade")
        }
    }

    @Synchronized
    private fun db(context: Context): SQLiteDatabase {
        val app = context.applicationContext
        val store = helper ?: Store(app).also { helper = it }
        val database = store.writableDatabase
        // The old records are removed only after the SQLite creation transaction committed.
        val old = app.getSharedPreferences(STORE, Context.MODE_PRIVATE)
        if (old.contains(RECENT) || old.contains(FAVORITES)) {
            old.edit().remove(RECENT).remove(FAVORITES).apply()
        }
        return database
    }

    private fun load(context: Context, table: String): List<Entry> {
        val out = ArrayList<Entry>()
        db(context).query(table, arrayOf("url", "site", "board", "day", "user_id", "viewed_at", "posts"),
            null, null, null, null, "viewed_at DESC, url ASC").use { cursor ->
            while (cursor.moveToNext()) {
                out.add(Entry(cursor.getString(0), cursor.getString(1), cursor.getString(2),
                    cursor.getString(3), cursor.getString(4), cursor.getLong(5), cursor.getInt(6)))
            }
        }
        return out
    }

    private fun trim(database: SQLiteDatabase, limit: Int) {
        if (limit == HistoryRetention.UNLIMITED) return
        database.execSQL("DELETE FROM recent WHERE url IN (SELECT url FROM recent ORDER BY viewed_at DESC, url ASC LIMIT -1 OFFSET ?)", arrayOf<Any>(limit))
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
        val app = context.applicationContext
        runAsync {
            val database = db(app)
            database.beginTransaction()
            try {
                check(database.insertWithOnConflict("recent", null, values(entry), SQLiteDatabase.CONFLICT_REPLACE) != -1L)
                trim(database, limit(app))
                database.update("favorites", values(entry), "url = ?", arrayOf(url))
                database.setTransactionSuccessful()
            } finally {
                database.endTransaction()
            }
        }
    }

    /** Normal history deletion must never modify favorites. */
    @JvmStatic fun deleteRecent(context: Context, url: String) {
        db(context).delete("recent", "url = ?", arrayOf(url))
    }

    @JvmStatic fun deleteRecents(context: Context, urls: Set<String>) {
        val database = db(context)
        database.beginTransaction()
        try {
            for (chunk in urls.chunked(400)) {
                val placeholders = chunk.joinToString(",") { "?" }
                database.delete("recent", "url IN ($placeholders)", chunk.toTypedArray())
            }
            database.setTransactionSuccessful()
        } finally {
            database.endTransaction()
        }
    }

    @JvmStatic fun clearRecents(context: Context) {
        db(context).delete("recent", null, null)
    }

    @JvmStatic fun recents(context: Context): List<Entry> = load(context, "recent")
    @JvmStatic fun favorites(context: Context): List<Entry> = load(context, "favorites")

    @JvmStatic fun isFavorite(context: Context, url: String): Boolean =
        db(context).query("favorites", arrayOf("url"), "url = ?", arrayOf(url), null, null, null, "1").use { it.moveToFirst() }

    @JvmStatic fun removeFavorite(context: Context, url: String) {
        db(context).delete("favorites", "url = ?", arrayOf(url))
    }

    @JvmStatic fun setFavorite(context: Context, entry: Entry, enabled: Boolean) {
        val database = db(context)
        if (enabled) {
            check(database.insertWithOnConflict("favorites", null, values(entry), SQLiteDatabase.CONFLICT_REPLACE) != -1L)
        } else {
            database.delete("favorites", "url = ?", arrayOf(entry.url))
        }
    }

    @JvmStatic fun toggleFavorite(context: Context, entry: Entry) {
        val database = db(context)
        if (database.delete("favorites", "url = ?", arrayOf(entry.url)) == 0) {
            check(database.insertWithOnConflict("favorites", null, values(entry), SQLiteDatabase.CONFLICT_REPLACE) != -1L)
        }
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
