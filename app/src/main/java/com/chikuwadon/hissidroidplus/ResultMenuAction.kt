package com.chikuwadon.hissidroidplus

/** Shared menu order and action identity; no fragile site-specific index offsets. */
enum class ResultMenuAction(val label: String) {
    ANALYSIS("書き込み分析"),
    BROWSER("外部ブラウザで開く"),
    COPY_URL("URLをコピー"),
    COPY_ID("IDをコピー"),
    COPY_THREADS("書き込みスレをコピー"),
    COPY_POSTS("表示中のレスをすべてコピー");

    companion object {
        @JvmStatic
        fun forSite(edge: Boolean): List<ResultMenuAction> =
            if (edge) entries.toList() else entries.filter { it != ANALYSIS }
    }
}
