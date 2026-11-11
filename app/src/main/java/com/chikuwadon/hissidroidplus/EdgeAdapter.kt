package com.chikuwadon.hissidroidplus

import java.io.IOException
import java.nio.charset.Charset
import java.util.HashSet
import java.util.Locale
import java.util.regex.Pattern
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

/** Edge checker HTML reader, separate from UI and the 5ch parser. */
class EdgeAdapter {
    @Throws(IOException::class)
    fun load(query: CheckerQuery): CheckerResult =
        decode(HttpTransport.get(query.url, Charset.forName("Shift_JIS")), query)

    @Throws(IOException::class)
    fun loadForVerification(query: CheckerQuery): CheckerResult =
        decode(HttpTransport.getForVerification(query.url, Charset.forName("Shift_JIS")), query)

    companion object {
        private const val BLOCKS =
            "article, li.post, tr.post, .post, .res, .post-item, " +
            ".response, .resitem, .res-item, .res-row, .post-row, .posting, dl"
        private const val BODIES =
            "blockquote, .message, .body, .post-content, .resbody, " +
            ".res-body, .res-text, .message-body, .post-body, .posttext, .post_message, " +
            ".comment-body, .comment, .msg, .text-content, dd"
        private val POST_DATE = Pattern.compile("20\\d{2}[/-]\\d{1,2}[/-]\\d{1,2}")
        private val RES_DATE = Pattern.compile(
            "20\\d{2}/\\d{2}/\\d{2}\\([^)]*\\)\\s+\\d{2}:\\d{2}:\\d{2}(?:\\.\\d+)?"
        )
        private val POST_HEAD = Pattern.compile("^\\s*\\d{1,6}(?:\\(\\d+\\))?\\s*[:：]?\\s*")
        private val POST_REF = Pattern.compile("^\\s*\\d{1,6}\\s*\\((\\d+)\\)")
        private val SUMMARY = Pattern.compile("(?m)^(レス数|被参照数|スレッド数)\\s*[:：]\\s*(\\d+)")
        private val KNOWN_BLOCKS = setOf("res", "post", "response", "post-item", "res-row", "posting")

        private fun decode(html: String, query: CheckerQuery): CheckerResult =
            parse(html, query).also {
                it.sourceUrl = query.url
                it.diagnostic = describePage(html)
            }

        @JvmStatic
        fun parse(html: String?, query: CheckerQuery): CheckerResult {
            val result = CheckerResult()
            if (html.isNullOrEmpty()) return result
            val doc = Jsoup.parse(html, query.url)
            parseKyodemo(doc, result)
            if (result.posts.isNotEmpty()) {
                PostIndex.collectNames(result)
                result.diagnostic = describePage(html)
                return result
            }
            val seen = HashSet<String>()
            for (node in doc.select(BLOCKS)) parseBlock(node, result, seen, false)
            for (anchor in doc.select("a[href]")) {
                if (!isThreadLink(anchor.absUrl("href"))) continue
                var ancestor: Element? = anchor
                for (depth in 0 until 7) {
                    val current = ancestor ?: break
                    val type = current.tagName()
                    if (type == "article" || type == "tr" || type == "li" ||
                        type == "dl" || type == "section" ||
                        (type == "div" &&
                            (current.classNames().any { it in KNOWN_BLOCKS } ||
                                current.selectFirst(BODIES) != null))
                    ) {
                        parseBlock(current, result, seen, false)
                        break
                    }
                    ancestor = current.parent()
                }
            }
            if (result.posts.isEmpty()) {
                for (row in doc.select("tr, li, article, dl, .res-row, .post-row")) {
                    parseBlock(row, result, seen, true)
                }
            }
            PostIndex.collectNames(result)
            result.diagnostic = describePage(html)
            return result
        }

        private fun parseKyodemo(doc: Document, result: CheckerResult) {
            val summary = doc.selectFirst("textarea#copy-mess")
            if (summary != null) {
                val info = summary.wholeText()
                if (info.length <= 100_000 &&
                    info.contains("書き込みスレッド一覧") && info.contains("ID:")
                ) result.sourceThreadSummary = info.trim()
                val matched = SUMMARY.matcher(info)
                while (matched.find()) {
                    val value = matched.group(2).toIntOrNull() ?: continue
                    when (matched.group(1)) {
                        "レス数" -> result.reportedPostCount = value
                        "被参照数" -> result.referencedCount = value
                        else -> result.reportedThreadCount = value
                    }
                }
            }
            val seen = HashSet<String>()
            for (section in doc.select(".box.content, .box.post-group, .thread-content")) {
                val heading = section.selectFirst("h2 a[href], h3 a[href], .thread-title a[href]")
                    ?: continue
                val title = heading.text().trim()
                val groupLink = heading.absUrl("href")
                if (title.isEmpty() || !isThreadLink(groupLink)) continue
                result.threads.putIfAbsent(PostIndex.openableThreadUrl(groupLink), title)
                for (node in section.select(".post")) {
                    val head = node.selectFirst(".r-head") ?: continue
                    val link = head.selectFirst("a[href]") ?: continue
                    val href = link.absUrl("href")
                    if (!isThreadLink(href) || !seen.add(href)) continue
                    val meta = head.text().trim()
                    val linkedNumber = link.text().trim()
                    var number = linkedNumber.replaceFirst(Regex("[^0-9].*$"), "")
                    if (number.isEmpty()) {
                        number = meta.replaceFirst(Regex("^\\s*(\\d+).*$"), "$1")
                    }
                    var refs = 0
                    var matchedRef = POST_REF.matcher(linkedNumber)
                    var found = matchedRef.find()
                    if (!found) {
                        matchedRef = POST_REF.matcher(meta)
                        found = matchedRef.find()
                    }
                    if (found) refs = matchedRef.group(1).toIntOrNull() ?: 0
                    val raw = POST_HEAD.matcher(meta).replaceFirst("").trim()
                    val date = RES_DATE.matcher(raw)
                    val author = if (date.find()) {
                        PostIndex.cleanAuthor(raw.substring(0, date.start()))
                    } else {
                        PostIndex.cleanAuthor(raw.replaceFirst(Regex("\\s+ID:.*$"), ""))
                    }
                    val header = PostFormatter.header(
                        CheckerResult.Post(title, author, number + " " + raw, "", href)
                    )
                    val bodyNode = node.clone()
                    bodyNode.selectFirst(".r-head")?.remove()
                    var body = bodyNode.wholeText().replace('\u00a0', ' ').trim()
                    if (body.isEmpty()) body = bodyNode.text().trim()
                    if (body.isEmpty() || body.length > 30_000) continue
                    result.posts.add(CheckerResult.Post(title, author, header, body, href, refs))
                    if (author.isNotEmpty() && author !in result.names) result.names.add(author)
                }
            }
        }

        private fun parseBlock(
            node: Element, result: CheckerResult, seen: MutableSet<String>, allowLoose: Boolean
        ) {
            val anchor = node.select("a[href]").firstOrNull {
                isThreadLink(it.absUrl("href"))
            } ?: return
            val href = anchor.absUrl("href")
            var content = node.selectFirst(BODIES)
            if (content == null && allowLoose && POST_DATE.matcher(node.text()).find()) {
                for (part in node.select("p, td, .content, .text")) {
                    if (part === anchor || part.select("a[href]").isNotEmpty()) continue
                    if (part.text().trim().length > 6) {
                        content = part
                        break
                    }
                }
            }
            val body = content?.wholeText()?.trim() ?: return
            if (body.isEmpty() || body.length > 30_000) return
            if (!seen.add(href + "\n" + body)) return
            var title = node.select("h2,h3,.thread-title,.thread_title,.title").text()
            if (title.isEmpty()) title = anchor.attr("title").trim()
            if (title.isEmpty()) title = anchor.text().trim()
            val author = node.select(
                ".name,.username,.resname,.res-name,.post-name,.author,.poster-name"
            ).text()
            val meta = node.select(".date,.time,.meta,.resdate,.post-time,.poster-meta").text()
            result.posts.add(CheckerResult.Post(title, author, meta, body, href))
            if (author.isNotEmpty() && author !in result.names) result.names.add(author)
            result.threads.putIfAbsent(
                PostIndex.openableThreadUrl(href), if (title.isEmpty()) "スレッド" else title
            )
        }

        @JvmStatic
        fun isThreadLink(href: String?): Boolean {
            if (href.isNullOrEmpty()) return false
            return href.contains("bbs.eddibb.cc/test/read.cgi/") ||
                href.matches(Regex("https?://bbs\\.eddibb\\.cc/[a-zA-Z0-9_]+/\\d{10,20}.*")) ||
                href.contains("/sdemo/r/e_e_")
        }

        @JvmStatic
        fun explainEmptyPage(html: String?): String {
            if (html.isNullOrBlank()) return "取得したページが空です"
            val plain = Jsoup.parse(html).text().lowercase(Locale.ROOT)
            if (plain.contains("403 forbidden") || plain.contains("access denied") ||
                plain.contains("error 403")) return "アクセスが拒否されています（HTTP 403）"
            if (plain.contains("captcha") || plain.contains("cloudflare") ||
                plain.contains("verify you are human") || plain.contains("just a moment")) {
                return "サイト側の認証・アクセス制限画面です"
            }
            if (plain.contains("404 not found")) return "ページが見つかりません（404）"
            return "ページは取得できましたが、レスを抽出できません（HTML構造未対応の可能性）"
        }

        @JvmStatic
        fun describePage(html: String?): String {
            if (html.isNullOrEmpty()) return "受信内容なし"
            val doc = Jsoup.parse(html)
            val links = doc.select("a[href]").size
            var thread = doc.select("a[href]").count { isThreadLink(it.absUrl("href")) }
            if (thread == 0) {
                thread = doc.select("a[href]").count {
                    val href = it.attr("href")
                    href.contains("/sdemo/r/e_e_") || href.contains("/test/read.cgi/")
                }
            }
            val bodies = doc.select(BODIES).size
            val blocks = doc.select(BLOCKS).size
            val all = doc.text().lowercase(Locale.ROOT)
            val blocked = all.contains("403 forbidden") || all.contains("access denied") ||
                all.contains("captcha") || all.contains("cloudflare") ||
                all.contains("verify you are human")
            return "HTML=" + html.length + "文字 / リンク=" + links +
                " / スレリンク=" + thread + " / 本文候補=" + bodies +
                " / 投稿候補=" + blocks + (if (blocked) " / アクセス制限の疑い" else "")
        }
    }
}
