package com.chikuwadon.hissidroidplus

import org.jsoup.Jsoup
import java.net.URI
import java.nio.charset.Charset

object KyodemoOtherBoards {
    data class Service(val label: String, val root: String)
    data class Item(val code: String, val label: String)
    data class Menu(val categories: List<Item>, val boards: List<BoardCatalog.Board>)
    @JvmField val services = listOf(Service("まちBBS", "m_"), Service("ふたばちゃんねる", "f_"),
        Service("したらば", "s_"), Service("おーぷん2ch", "o_"), Service("いつもch", "i_"),
        Service("やる夫", "e_y_"), Service("JPNKN", "e_j_"), Service("ぷにぷに", "e_p_"),
        Service("オルグ", "e_o_"), Service("その他", "e_"))
    private val otherCodes = setOf("e_e_edge", "e_n_dartkakuni", "e_d_demo")
    private val codePattern = Regex("[A-Za-z0-9_]{1,60}")

    @JvmStatic fun serviceFor(code: String): Service? {
        if (!codePattern.matches(code)) return null
        return services.firstOrNull {
            if (it.root == "e_") code in otherCodes
            else code.startsWith(it.root) && code.length > it.root.length
        }
    }
    @JvmStatic fun validBoard(code: String): Boolean = serviceFor(code) != null

    // The four split groups share Kyodemo's actual /k/e_ menu, not synthetic URLs.
    @JvmStatic fun menuRoot(service: Service): String =
        if (service.root.startsWith("e_")) "e_" else service.root

    fun fetch(service: Service, category: String): Menu {
        require(service in services && codePattern.matches(category) && category.startsWith(service.root))
        val actual = if (category == service.root) menuRoot(service) else category
        val url = "https://www.kyodemo.net/sdemo/k/" + actual
        return parse(service, category, HttpTransport.get(url, Charset.forName("windows-31j")))
    }
    @JvmStatic fun parse(service: Service, category: String, html: String): Menu {
        require(service in services && codePattern.matches(category) && category.startsWith(service.root))
        val actual = if (category == service.root) menuRoot(service) else category
        val doc = Jsoup.parse(html, "https://www.kyodemo.net/sdemo/k/" + actual)
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
            if (!codePattern.matches(code) || code == category || code == service.root ||
                label.isEmpty() || label.length > 100) continue
            when (parts[1]) {
                "k" -> if (!service.root.startsWith("e_") && code.startsWith(service.root)) categories.putIfAbsent(code, Item(code, label))
                "b" -> if (serviceFor(code) == service) boards.putIfAbsent(code, BoardCatalog.Board(code, label))
            }
        }
        return Menu(categories.values.toList(), boards.values.toList())
    }
}
