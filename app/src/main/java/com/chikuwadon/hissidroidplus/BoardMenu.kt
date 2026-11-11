package com.chikuwadon.hissidroidplus

import java.io.BufferedReader
import java.io.IOException
import java.io.Reader
import java.net.URI
import java.nio.charset.StandardCharsets
import java.util.LinkedHashMap
import java.util.LinkedHashSet
import org.json.JSONObject
import org.jsoup.Jsoup

/** Official 5ch BBS menu, HTML fallback, and validated offline snapshot. */
object BoardMenu {
    const val JSON_URL = "https://menu.5ch.io/bbsmenu.json"
    const val HTML_URL = "https://menu.5ch.io/bbsmenu.html"

    class Category(@JvmField val name: String) {
        @JvmField val boards: MutableList<BoardCatalog.Board> = ArrayList()
    }

    @JvmStatic
    @Throws(IOException::class)
    fun fetch(): List<Category> {
        var error: IOException = IOException("公式JSONに板がありません")
        try {
            val groups = parseJson(HttpTransport.get(JSON_URL, StandardCharsets.UTF_8))
            if (groups.isNotEmpty()) return groups
        } catch (failure: Exception) {
            error = IOException("公式JSONを取得できません", failure)
        }
        try {
            val groups = parseHtml(HttpTransport.get(HTML_URL, StandardCharsets.UTF_8))
            if (groups.isNotEmpty()) return groups
        } catch (failure: Exception) {
            error.addSuppressed(failure)
        }
        throw error
    }

    /** Reject invalid or duplicate rows from the generated public BBS menu. */
    @JvmStatic
    @Throws(IOException::class)
    fun parseOfflineSnapshot(source: Reader): List<Category> {
        val groups = LinkedHashMap<String, Category>()
        val seen = LinkedHashSet<String>()
        BufferedReader(source).use { input ->
            while (true) {
                val line = input.readLine() ?: break
                val cells = line.split('\t')
                if (cells.size != 3) continue
                val category = cells[0].trim()
                val code = cells[1].trim()
                val name = cells[2].trim()
                if (category.isEmpty() || category.length > 100 ||
                    name.isEmpty() || name.length > 100 ||
                    !code.matches(Regex("[A-Za-z0-9_]{1,60}")) || !seen.add(code)
                ) continue
                groups.getOrPut(category) { Category(category) }.boards.add(BoardCatalog.Board(code, name))
            }
        }
        return ArrayList(groups.values)
    }

    @JvmStatic
    fun parseJson(raw: String): List<Category> {
        val root = JSONObject(raw)
        val groups = root.optJSONArray("menu_list")
            ?: throw IllegalArgumentException("menu_list not found")
        val result = ArrayList<Category>()
        for (i in 0 until groups.length()) {
            val item = groups.optJSONObject(i) ?: continue
            val title = item.optString("category_name", "").trim()
            val entries = item.optJSONArray("category_content") ?: continue
            if (title.isEmpty()) continue
            val category = Category(title)
            val seen = LinkedHashSet<String>()
            for (index in 0 until entries.length()) {
                val entry = entries.optJSONObject(index) ?: continue
                val label = entry.optString("board_name", "").trim()
                val code = entry.optString("directory_name", "").trim()
                val address = entry.optString("url", "")
                if (!validBoard(address, code) || label.isEmpty() || !seen.add(code)) continue
                category.boards.add(BoardCatalog.Board(code, label))
            }
            if (category.boards.isNotEmpty()) result.add(category)
        }
        if (result.isEmpty()) throw IllegalArgumentException("No boards in official menu")
        return result
    }

    private fun validBoard(url: String, code: String): Boolean {
        if (!code.matches(Regex("[A-Za-z0-9_]{1,60}"))) return false
        return try {
            val uri = URI.create(url)
            val host = uri.host ?: return false
            if (!(host.endsWith(".5ch.io") || host.endsWith(".5ch.net") ||
                  host == "5ch.io")) return false
            if (!uri.scheme.equals("https", true) && !uri.scheme.equals("http", true)) return false
            val path = uri.path ?: return false
            path.matches(Regex("/" + code + "/?"))
        } catch (_: Exception) {
            false
        }
    }

    @JvmStatic
    fun parseHtml(html: String): List<Category> {
        val doc = Jsoup.parse(html, HTML_URL)
        val groups = LinkedHashMap<String, Category>()
        var current = ""
        for (element in doc.body().allElements) {
            if (element.tagName().matches(Regex("h2|h3|h4|b|strong")) &&
                element.children().isEmpty()
            ) {
                val title = element.text().trim()
                if (title.isNotEmpty() && title.length < 42) current = title
            } else if (element.tagName() == "a" && current.isNotEmpty()) {
                val url = element.absUrl("href")
                val path = try { URI.create(url).path } catch (_: Exception) { continue }
                if (path == null || !path.matches(Regex("/[A-Za-z0-9_]+/?"))) continue
                val code = path.replace("/", "")
                if (!validBoard(url, code)) continue
                val group = groups.getOrPut(current) { Category(current) }
                val title = element.text().trim()
                if (title.isEmpty() || group.boards.any { it.code == code }) continue
                group.boards.add(BoardCatalog.Board(code, title))
            }
        }
        return groups.values.filter { it.boards.isNotEmpty() }
    }
}
