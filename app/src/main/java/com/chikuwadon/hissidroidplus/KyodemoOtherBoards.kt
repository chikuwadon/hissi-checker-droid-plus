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
    // Verified against the supplied Kyodemo menu snapshot and explicit board URLs.
    // The server menu can omit entire groups depending on its returned view.
    private val known = linkedMapOf(
        "e_y_yaruzatsu01" to "やる夫系雑談",
        "e_y_yaruo100" to "やる夫板100",
        "e_y_yaruo001" to "やる夫板のシェルター",
        "e_y_1" to "やる夫の魔改造板",
        "e_j_hololiveneet" to "ホロライブニート",
        "e_j_nijifunbbs" to "にじさんじなかよし",
        "e_j_jptta45" to "Twitchストリーマー",
        "e_j_vspofan2" to "ぶいすぽ",
        "e_j_umesure005" to "ウメスレ",
        "e_j_vyoutuber2" to "VYoutuber2",
        "e_p_vaporeon" to "ぷにぷに",
        "e_p_leafeon" to "なんでも実況L",
        "e_p_livepunipuni" to "なんでも実況P",
        "e_o_poverty" to "嫌儲.org",
        "e_o_liveorg" to "なんでも実況(オルグ)",
        "e_o_news" to "ニュース速報",
        "e_o_operate" to "管理運営",
        "e_o_market" to "投資一般",
        "e_o_game" to "ゲーム",
        "e_o_sugiuraayano" to "ニュース速報(杉浦綾乃)",
        "e_o_donguri" to "どんぐり",
        "e_o_liveuranus" to "なんでも実況U",
        "e_o_lovelive" to "ラブライブ！オルグ!!",
        "e_o_vtuber" to "VTuber",
        "e_o_akari" to "あかり",
        "e_o_gaysaloon" to "同性愛サロン",
        "e_e_edge" to "なんでも実況(エッジ2)",
        "e_n_dartkakuni" to "[PD]半角二次元",
        "e_d_demo" to "[デモ]運用情報"
    )
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

    @JvmStatic fun knownBoards(service: Service): List<BoardCatalog.Board> =
        known.filterKeys { serviceFor(it) == service }.map { BoardCatalog.Board(it.key, it.value) }

    @JvmStatic fun mergeKnown(service: Service, category: String, live: Menu): Menu {
        if (category != service.root) return live
        val boards = linkedMapOf<String, BoardCatalog.Board>()
        knownBoards(service).forEach { boards[it.code] = it }
        live.boards.forEach { boards.putIfAbsent(it.code, it) }
        return Menu(live.categories, boards.values.toList())
    }

    fun fetch(service: Service, category: String): Menu {
        require(service in services && codePattern.matches(category) && category.startsWith(service.root))
        val actual = if (category == service.root) menuRoot(service) else category
        val url = "https://www.kyodemo.net/sdemo/k/" + actual
        val live = try {
            parse(service, category, HttpTransport.get(url, Charset.forName("windows-31j")))
        } catch (failure: java.io.IOException) {
            if (category != service.root || knownBoards(service).isEmpty()) throw failure
            Menu(emptyList(), emptyList())
        }
        return mergeKnown(service, category, live)
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
