package com.chikuwadon.hissidroidplus

import java.util.regex.Pattern

/** Canonical copied post formatting. Reference counts stay display-only. */
object PostFormatter {
    private val EMPTY_MAIL = Pattern.compile("\\[\\s*\\]")
    private val MAIL = Pattern.compile("\\[\\s*([^]\\s][^]]*?)\\s*\\]")
    private val DATE_SEPARATOR = Pattern.compile("\\s*[:：]\\s*(?=20\\d{2}[/.-])")
    private val ID = Pattern.compile("(?i)\\bID\\s*[:：]\\s*([A-Za-z0-9+./_-]+)")
    private val NUMBER = Pattern.compile("^\\s*(\\d{1,6})(?:\\(\\d+\\))?\\s*[:：]?\\s*")
    private val DATE = Pattern.compile("20\\d{2}/\\d{1,2}/\\d{1,2}")

    @JvmStatic
    fun normalizedInfo(info: String?): String {
        if (info == null) return ""
        var normalized = EMPTY_MAIL.matcher(info).replaceAll(" ")
        normalized = MAIL.matcher(normalized).replaceAll(" $1 ")
        normalized = DATE_SEPARATOR.matcher(normalized).replaceAll(" ")
        return normalized.replace(Regex("\\s+"), " ").trim()
    }

    @JvmStatic
    fun id(p: CheckerResult.Post): String {
        val matched = ID.matcher(p.author + " " + p.meta)
        return if (matched.find()) "ID:" + matched.group(1) else ""
    }

    /** Canonical post number, name, optional mail, date and ID. */
    @JvmStatic
    fun header(p: CheckerResult.Post): String {
        val meta = p.meta ?: ""
        var source = if (DATE.matcher(meta).find()) meta else meta + " " + p.author
        source = normalizedInfo(source)
        val number = NUMBER.matcher(source)
        if (number.find()) {
            source = number.group(1) + " " + source.substring(number.end()).trim()
        }
        val id = ID.matcher(source)
        if (id.find()) {
            source = source.substring(0, id.end())
            source = source.replaceFirst(Regex("(?i)ID\\s*[:：]\\s*"), "ID:")
        }
        return source.trim().replace(Regex("\\s+"), " ")
    }

    @JvmStatic
    fun full(p: CheckerResult.Post): String =
        header(p) + "\n" + (p.body?.trim() ?: "")

    @JvmStatic
    fun withThread(p: CheckerResult.Post): String = buildString {
        if (p.title.trim().isNotEmpty()) append(p.title.trim()).append('\n')
        if (p.link.trim().isNotEmpty()) append(p.link.trim()).append('\n')
        append(full(p))
    }.trim()

    @JvmStatic
    fun append(old: String?, next: String?): String {
        val a = old?.trim() ?: ""
        val b = next?.trim() ?: ""
        return when {
            a.isEmpty() -> b
            b.isEmpty() -> a
            else -> a + "\n\n" + b
        }
    }
}
