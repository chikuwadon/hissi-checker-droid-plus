package com.chikuwadon.hissidroidplus

import org.jsoup.Jsoup
import java.net.URI
import java.nio.charset.Charset

object KyodemoOtherBoards {
    data class Service(val label: String, val root: String)
    data class Item(val code: String, val label: String)
    data class Menu(val categories: List<Item>, val boards: List<BoardCatalog.Board>)
    @JvmField val services = listOf(Service("まちBBS", "m_"), Service("ふたばちゃんねる", "f_"),
        Service("その他", "e_"), Service("したらば", "s_"), Service("おーぷん2ch", "o_"), Service("いつもch", "i_"))
    private val codePattern = Regex("[A-Za-z0-9_]{1,60}")
    @JvmStatic fun validBoard(code: String): Boolean =
        codePattern.matches(code) && code != "e_e_liveedge" && services.any { code.startsWith(it.root) && code.length > it.root.length }

    fun fetch(service: Service, category: String): Menu {
        require(service in services && codePattern.matches(category) && category.startsWith(service.root))
        val url = "https://www.kyodemo.net/sdemo/k/" + category
        return parse(service, category, HttpTransport.get(url, Charset.forName("windows-31j")))
    }
    @JvmStatic fun parse(service: Service, category: String, html: String): Menu {
        require(service in services && codePattern.matches(category) && category.startsWith(service.root))
        val doc = Jsoup.parse(html, "https://www.kyodemo.net/sdemo/k/" + category)
        val categories = linkedMapOf<String, Item>()
        val boards = linkedMapOf<String, BoardCatalog.Board>()
        for (a in doc.select("a[href]")) {
            val uri = runCatching { URI.create(a.absUrl("href")) }.getOrNull() ?: continue
            if (uri.scheme != "https" || uri.host != "www.kyodemo.net" || uri.rawUserInfo != null ||
                uri.port != -1 || uri.rawQuery != null || uri.rawFragment != null) continue
            val parts = uri.path.trim('/').split('/')
            if (parts.size != 3 || parts[0] != "sdemo") continue
            val code = parts[2]
            val label = a.text().trim()
            if (!codePattern.matches(code) || !code.startsWith(service.root) || code == category ||
                code == service.root || label.isEmpty() || label.length > 100) continue
            when (parts[1]) {
                "k" -> categories.putIfAbsent(code, Item(code, label))
                "b" -> if (validBoard(code)) boards.putIfAbsent(code, BoardCatalog.Board(code, label))
            }
        }
        return Menu(categories.values.toList(), boards.values.toList())
    }
}
