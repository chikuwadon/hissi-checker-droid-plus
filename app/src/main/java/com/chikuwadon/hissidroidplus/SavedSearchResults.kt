package com.chikuwadon.hissidroidplus

import android.content.Context
import android.util.AtomicFile
import org.json.JSONObject
import org.json.JSONArray
import java.io.File
import java.util.concurrent.Executors

/** Bounded private snapshots, independent of search history. No Activity references. */
object SavedSearchResults {
    private const val LIMIT = 8 * 1024 * 1024
    private const val ID = "last_id_result.json"
    private const val ARCHIVE = "last_archive_result.json"
    private val worker = Executors.newSingleThreadExecutor()
    private val lock = Any()
    private var idGeneration = 0L
    private var archiveGeneration = 0L
    private fun file(context: Context, name: String) = AtomicFile(File(context.noBackupFilesDir, name))
    fun hasId(context: Context) = AppSettings.keepSearchResults(context) && file(context, ID).baseFile.isFile
    fun hasArchive(context: Context) = AppSettings.keepSearchResults(context) && file(context, ARCHIVE).baseFile.isFile
    fun clearAll(context: Context) = synchronized(lock) {
        idGeneration++
        archiveGeneration++
        file(context, ID).delete()
        file(context, ARCHIVE).delete()
    }
    fun clearArchive(context: Context) = synchronized(lock) {
        archiveGeneration++
        file(context, ARCHIVE).delete()
    }
    private fun write(context: Context, name: String, generation: Long, encode: () -> JSONObject) {
        val app = context.applicationContext
        worker.execute {
            val bytes = runCatching { encode().put("version", 1).toString().toByteArray(Charsets.UTF_8) }.getOrNull()
            synchronized(lock) {
                if (!AppSettings.keepSearchResults(app) || generation != (if (name == ID) idGeneration else archiveGeneration)) return@synchronized
                val target = file(app, name)
                if (bytes == null || bytes.size > LIMIT) { target.delete(); return@synchronized }
                var stream: java.io.FileOutputStream? = null
                try {
                    stream = target.startWrite()
                    stream.write(bytes)
                    target.finishWrite(stream)
                } catch (_: Exception) {
                    if (stream != null) target.failWrite(stream)
                    target.delete()
                }
            }
        }
    }
    private fun read(context: Context, name: String): JSONObject? = synchronized(lock) {
        if (!AppSettings.keepSearchResults(context)) return@synchronized null
        val target = file(context, name)
        runCatching {
            target.openRead().use { input ->
                val bytes = HttpResponseReader.read(input, LIMIT, -1)
                JSONObject(String(bytes, Charsets.UTF_8)).takeIf { it.getInt("version") == 1 }
            }
        }.getOrNull()
    }
    fun saveId(context: Context, snapshot: ResultScreenState.Snapshot) {
        if (!AppSettings.keepSearchResults(context)) return
        val generation = synchronized(lock) { ++idGeneration }
        val r = snapshot.result
        // Take a shallow copy of mutable collections on their owner thread; Post is immutable.
        val posts = r.posts.toList()
        val threads = r.threads.toMap()
        val names = r.names.toList()
        val hours = r.hourlyCounts?.copyOf()
        val metadata = listOf(r.rank, r.sourceUrl, r.diagnostic, r.sourceThreadSummary)
        val counts = listOf(r.reportedPostCount, r.referencedCount, r.reportedThreadCount)
        write(context, ID, generation) {
            val q = snapshot.query
            JSONObject().put("site", q.site.name).put("board", q.board).put("id", q.id)
                .put("date", q.date).put("url", q.url).put("nextPage", snapshot.nextPage)
                .put("noMore", snapshot.noMorePages).put("edgeDiagnostic", snapshot.edgeDiagnostic)
                .put("meta", JSONArray(metadata)).put("counts", JSONArray(counts))
                .put("hours", hours?.let { JSONArray(it.toList()) } ?: JSONObject.NULL)
                .put("posts", JSONArray().apply { posts.forEach { p ->
                    put(JSONArray(listOf(p.title, p.author, p.meta, p.body, p.link, p.referenceCount)))
                } }).put("threads", JSONObject(threads)).put("names", JSONArray(names))
        }
    }
    fun readId(context: Context): ResultScreenState.Snapshot? = runCatching {
        val j = read(context, ID) ?: return null
        val url = j.getString("url")
        val site = CheckerQuery.Site.valueOf(j.getString("site"))
        val q = if (url.isNotEmpty()) CheckerQuery.parseUrl(url)
        else if (site == CheckerQuery.Site.EDGE) CheckerQuery.edgeSearch(j.getString("board"), j.getString("date"), j.getString("id"))
        else CheckerQuery.hissi(j.getString("board"), j.getString("date"), j.getString("id"))
        require(q.site == site && q.board == j.getString("board"))
        val r = CheckerResult()
        val meta = j.getJSONArray("meta")
        r.rank = meta.getString(0); r.sourceUrl = meta.getString(1)
        r.diagnostic = meta.getString(2); r.sourceThreadSummary = meta.getString(3)
        if (r.sourceUrl.isNotEmpty()) HttpTransport.validate(java.net.URL(r.sourceUrl))
        val counts = j.getJSONArray("counts")
        r.reportedPostCount = counts.getInt(0); r.referencedCount = counts.getInt(1); r.reportedThreadCount = counts.getInt(2)
        j.optJSONArray("hours")?.takeIf { it.length() == 24 }?.let { arr -> r.hourlyCounts = IntArray(24) { arr.getInt(it) } }
        val posts = j.getJSONArray("posts")
        for (i in 0 until posts.length()) {
            val p = posts.getJSONArray(i)
            r.posts.add(CheckerResult.Post(p.getString(0), p.getString(1), p.getString(2), p.getString(3), p.getString(4), p.getInt(5)))
        }
        val threads = j.getJSONObject("threads")
        threads.keys().forEach { key -> r.threads[key] = threads.getString(key) }
        val names = j.getJSONArray("names")
        for (i in 0 until names.length()) r.names.add(names.getString(i))
        ResultScreenState.Snapshot(q, r, j.getInt("nextPage").coerceIn(1, 21), j.getBoolean("noMore"), j.getString("edgeDiagnostic"))
    }.getOrNull()
    data class ArchiveSaved(val provider: ArchiveProvider, val query: ArchiveQuery, val page: ArchivePage)
    fun saveArchive(context: Context, provider: ArchiveProvider, q: ArchiveQuery, page: ArchivePage) {
        if (!AppSettings.keepSearchResults(context)) return
        val generation = synchronized(lock) { ++archiveGeneration }
        write(context, ARCHIVE, generation) {
            JSONObject().put("provider", provider.name)
                .put("query", JSONArray(listOf(q.keyword,q.exclude,q.minimum,q.from,q.to,q.maximum,q.sort,q.fuzzy,q.exact,q.externalBoards,q.boardCode,q.boardName)))
                .put("total", page.total).put("page", page.page).put("next", page.hasNext)
                .put("size", page.pageSize).put("capped", page.totalCapped)
                .put("items", JSONArray().apply { page.items.forEach { p ->
                    put(JSONArray(listOf(p.title,p.url,p.replies,p.date,p.board,p.originalUrl)))
                } })
        }
    }
    fun readArchive(context: Context): ArchiveSaved? = runCatching {
        val j = read(context, ARCHIVE) ?: return null
        val provider = ArchiveProvider.valueOf(j.getString("provider"))
        val a = j.getJSONArray("query")
        val q = ArchiveQuery(a.getString(0),a.getString(1),a.getString(2),a.getString(3),a.getString(4),a.getString(5),
            a.getString(6),a.getString(7),a.getBoolean(8),a.getBoolean(9),a.getString(10),a.getString(11))
        q.validate()
        val items = j.getJSONArray("items")
        require(items.length() <= 50)
        val rows = (0 until items.length()).map { i ->
            val p = items.getJSONArray(i)
            ArchiveThread(p.getString(0),p.getString(1),p.getInt(2),p.getString(3),p.getString(4),p.getString(5))
        }
        val page = ArchivePage(rows,j.getLong("total").coerceAtLeast(0),j.getInt("page").coerceIn(1,ArchiveRepository.MAX_PAGE),
            j.getBoolean("next"),pageSize=j.getInt("size").coerceIn(1,50),totalCapped=j.getBoolean("capped"))
        ArchiveSaved(provider,q,page)
    }.getOrNull()
}
