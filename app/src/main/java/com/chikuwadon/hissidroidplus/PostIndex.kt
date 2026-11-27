package com.chikuwadon.hissidroidplus

import java.net.URI
import java.util.LinkedHashMap
import java.util.LinkedHashSet
import java.util.Locale
import java.util.regex.Pattern

/** Canonical per-thread key and unique author names shared by both sites. */
object PostIndex {
    private val EDGE_THREAD = Pattern.compile(
        "^(/sdemo/r/e_e_[A-Za-z0-9_]+/\\d{10,20})(?:/\\d{1,6})?/?$"
    )
    private val THREAD_PATH = Pattern.compile(
        "^(/test/read\\.cgi/[A-Za-z0-9_]+/\\d{10,20})(?:/(?:\\d{1,6}|l\\d{1,6}|\\d{1,6}-\\d{1,6}))?/?$"
    )
    private val DATE = Pattern.compile("\\s*[:：]?\\s*20\\d{2}/\\d{1,2}/\\d{1,2}.*$", Pattern.DOTALL)
    private val ID = Pattern.compile("\\s+ID\\s*[:：].*$", Pattern.CASE_INSENSITIVE or Pattern.DOTALL)
    private val WACCHOI = Pattern.compile(
        "\\s*[（(](?:ﾜｯﾁｮｲ|ｽﾌﾟ|ｱｳ|ｻｻ|ﾜｷ|ﾌﾞｰ|ﾗｸ|ﾃﾃ|ﾌﾞﾓ|JPW|その他).*",
        Pattern.DOTALL
    )

    @JvmStatic
    fun threadKey(postUrl: String?): String {
        if (postUrl.isNullOrEmpty()) return ""
        return try {
            val uri = URI.create(postUrl)
            val path = uri.path ?: return postUrl
            var matched = THREAD_PATH.matcher(path)
            if (!matched.matches()) matched = EDGE_THREAD.matcher(path)
            val canonical = if (matched.matches()) matched.group(1) + "/" else path
            var host = uri.host?.lowercase(Locale.ROOT) ?: ""
            if (host == "egg.5ch.net" || host == "egg.5ch.io") host = "egg.5ch.net"
            host + canonical
        } catch (_: Exception) {
            postUrl.replaceFirst(FRAGMENT, "")
        }
    }

    @JvmStatic
    fun openableThreadUrl(postUrl: String?): String {
        if (postUrl == null) return ""
        return try {
            val uri = URI.create(postUrl)
            val path = uri.path
            var matched = THREAD_PATH.matcher(path)
            if (!matched.matches()) matched = EDGE_THREAD.matcher(path)
            if (matched.matches()) {
                URI(uri.scheme, uri.userInfo, uri.host, uri.port,
                    matched.group(1) + "/", null, null).toASCIIString()
            } else {
                URI(uri.scheme, uri.userInfo, uri.host, uri.port,
                    path, uri.query, null).toASCIIString()
            }
        } catch (_: Exception) {
            postUrl.replaceFirst(FRAGMENT, "")
        }
    }

    data class ThreadRow(val key: String, val value: String, val count: Int)
    data class DisplayIndex(
        val postKeys: List<String>,
        val threads: List<ThreadRow>,
        val names: List<String>,
        val nameCounts: Map<String, Int>
    )

    /** Derive all display indexes in one post traversal. Preserve source order and
     * the same canonical URL/name rules as the independent copy/analysis helpers. */
    @JvmStatic
    fun displayIndex(result: CheckerResult): DisplayIndex {
        val keys = ArrayList<String>(result.posts.size)
        val names = LinkedHashSet<String>()
        val counts = LinkedHashMap<String, Int>()
        val authors = LinkedHashMap<String, Int>()
        val seen = LinkedHashSet<String>()
        val threads = LinkedHashMap<String, String>()
        for (raw in result.names) {
            val name = cleanAuthor(raw)
            if (name.isNotEmpty()) names.add(name)
        }
        for (post in result.posts) {
            val key = threadKey(post.link)
            keys.add(key)
            if (post.link.isNotEmpty()) {
                counts[key] = counts.getOrDefault(key, 0) + 1
                if (seen.add(key)) threads[openableThreadUrl(post.link)] =
                    if (post.title.isEmpty()) "スレッド" else post.title
            }
            val name = cleanAuthor(post.author)
            if (name.isNotEmpty()) {
                names.add(name)
                authors[name] = authors.getOrDefault(name, 0) + 1
            }
        }
        for ((url, title) in result.threads) {
            val key = threadKey(url)
            if (seen.add(key)) threads[openableThreadUrl(url)] = title
        }
        return DisplayIndex(keys, threads.map { (url, title) ->
            ThreadRow(url, title, counts.getOrDefault(threadKey(url), 0))
        }, names.toList(), authors)
    }

    @JvmStatic
    fun uniqueThreads(result: CheckerResult): LinkedHashMap<String, String> {
        val output = LinkedHashMap<String, String>()
        val seen = LinkedHashSet<String>()
        for (post in result.posts) {
            if (post.link.isEmpty()) continue
            val key = threadKey(post.link)
            if (seen.add(key)) {
                output[openableThreadUrl(post.link)] =
                    if (post.title.isEmpty()) "スレッド" else post.title
            }
        }
        for ((url, title) in result.threads) {
            val key = threadKey(url)
            if (seen.add(key)) output[openableThreadUrl(url)] = title
        }
        return output
    }

    private val MARKUP = Regex("<[^>]*>")
    private val LEADING_NUMBER = Regex("^\\d{1,6}(?:\\(\\d+\\))?\\s*[:：]\\s*")
    private val REFERENCE_NUMBER = Regex("\\s+\\d{1,6}\\(\\d+\\)\\s*[:：].*$")
    private val MAIL_SUFFIX = Regex("(?i)\\s+(?:sage|age)\\s*$")
    private val FRAGMENT = Regex("#.*$")

    /** Strip markup, date, ID and connection metadata from author names. */
    @JvmStatic
    fun cleanAuthor(raw: String?): String {
        if (raw == null) return ""
        var value = raw.replace(MARKUP, " ").replace('\u00a0', ' ').trim()
        value = value.replaceFirst(LEADING_NUMBER, "")
        value = value.replaceFirst(REFERENCE_NUMBER, "")
        value = ID.matcher(value).replaceFirst("")
        value = DATE.matcher(value).replaceFirst("")
        value = value.replaceFirst(MAIL_SUFFIX, "")
        value = WACCHOI.matcher(value).replaceFirst("")
        var index = value.indexOf(" [")
        if (index > 0) value = value.substring(0, index)
        index = value.indexOf("[")
        if (index > 0) value = value.substring(0, index)
        return if (value.trim().length > 100) "" else value.trim()
    }

    @JvmStatic
    fun threadPostCounts(result: CheckerResult): LinkedHashMap<String, Int> {
        val counts = LinkedHashMap<String, Int>()
        for (post in result.posts) {
            if (post.link.isEmpty()) continue
            val key = threadKey(post.link)
            counts[key] = counts.getOrDefault(key, 0) + 1
        }
        return counts
    }

    @JvmStatic
    fun namePostCounts(result: CheckerResult): LinkedHashMap<String, Int> {
        val counts = LinkedHashMap<String, Int>()
        for (post in result.posts) {
            val name = cleanAuthor(post.author)
            if (name.isEmpty()) continue
            counts[name] = counts.getOrDefault(name, 0) + 1
        }
        return counts
    }

    @JvmStatic
    fun collectNames(result: CheckerResult) {
        val seen = LinkedHashSet<String>()
        for (name in result.names) {
            val clean = cleanAuthor(name)
            if (clean.isNotEmpty()) seen.add(clean)
        }
        for (post in result.posts) {
            val name = cleanAuthor(post.author)
            if (name.isNotEmpty()) seen.add(name)
        }
        result.names.clear()
        result.names.addAll(seen)
    }
}
