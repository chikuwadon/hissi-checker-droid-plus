package com.chikuwadon.hissidroidplus

import java.util.Collections
import java.util.LinkedHashMap
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

/** Preserve unknown Edge analysis sections and site-supplied explanations. */
class EdgeAnalysis private constructor(
    items: List<Section>,
    @JvmField val startAvailable: Boolean,
    @JvmField val hasStartButton: Boolean
) {
    class Section(
        @JvmField val title: String,
        @JvmField val body: String,
        @JvmField val notes: String,
        explanations: Map<String, String>
    ) {
        @JvmField val explanations: Map<String, String> =
            Collections.unmodifiableMap(LinkedHashMap(explanations))
    }

    @JvmField val sections: List<Section> = Collections.unmodifiableList(items)

    fun hasResult(): Boolean = sections.isNotEmpty()

    companion object {
        @JvmStatic
        fun parse(html: String?): EdgeAnalysis {
            val doc = Jsoup.parse(html ?: "")
            var start: Element? = null
            for (item in doc.select("a.button, button")) {
                val value = item.text().trim()
                if (value == "分析" || value == "分析を開始") {
                    start = item
                    break
                }
            }
            val found = start != null
            val available = start != null &&
                !start.hasAttr("disabled") &&
                !start.hasClass("is-disabled") &&
                !start.hasClass("disabled")

            val result = ArrayList<Section>()
            val box = doc.selectFirst("#toggle-w-tawindow .box.d-box")
                ?: doc.selectFirst("#toggle-w-tawindow .d-box")
                ?: doc.selectFirst("#wt .d-box")
            if (box != null) {
                var title = ""
                val body = StringBuilder()
                val notes = StringBuilder()
                val explanations = LinkedHashMap<String, String>()
                for (child in box.children()) {
                    val heading = child.hasClass("title") ||
                        child.tagName().matches(Regex("h2|h3|h4"))
                    if (heading) {
                        append(result, title, body, notes, explanations)
                        title = child.text().trim()
                        body.setLength(0)
                        notes.setLength(0)
                        explanations.clear()
                    } else if (title.isNotEmpty() && child.tagName() != "br") {
                        val safe = child.clone()
                        for (reason in safe.select("details.rating-reason")) {
                            parseExplanations(reason, explanations, notes)
                            reason.remove()
                        }
                        safe.select("script,style").remove()
                        val value = safe.wholeText().replace('\u00a0', ' ').trim()
                        if (value.isNotEmpty()) {
                            if (body.isNotEmpty()) body.append('\n')
                            body.append(value)
                        }
                    }
                }
                append(result, title, body, notes, explanations)
            }
            return EdgeAnalysis(result, available, found)
        }

        private fun parseExplanations(
            note: Element,
            explanations: MutableMap<String, String>,
            extras: StringBuilder
        ) {
            val content = note.selectFirst(".details-c")
            if (content == null) {
                addExtra(extras, note.text().trim())
                return
            }
            var label = ""
            var matched = 0
            for (part in content.children()) {
                if (part.hasClass("bx-label")) {
                    label = part.text().trim()
                } else if (label.isNotEmpty() && part.normalName() != "br") {
                    val description = part.text().trim()
                    if (description.isNotEmpty()) {
                        explanations[label] = description
                        label = ""
                        matched++
                    }
                }
            }
            if (matched == 0) addExtra(extras, content.text().trim())
        }

        private fun addExtra(extras: StringBuilder, value: String) {
            if (value.isEmpty()) return
            if (extras.isNotEmpty()) extras.append('\n')
            extras.append(value)
        }

        private fun append(
            out: MutableList<Section>,
            title: String,
            body: StringBuilder,
            notes: StringBuilder,
            explanations: Map<String, String>
        ) {
            val description = body.toString().trim()
            val extra = notes.toString().trim()
            if (title.isNotEmpty() &&
                (description.isNotEmpty() || extra.isNotEmpty() || explanations.isNotEmpty())
            ) out.add(Section(title, description, extra, explanations))
        }
    }
}
