package com.chikuwadon.hissidroidplus

/** Native result retrieval and validation, independent of Android UI and lifecycle. */
class ResultRepository @JvmOverloads constructor(private val transport: Transport = NativeTransport) {
    interface Transport {
        fun resolveEdge(query: CheckerQuery): CheckerQuery?
        fun load(query: CheckerQuery): CheckerResult
        fun nextPage(url: String, page: Int): CheckerResult
        fun diagnostic(): String
    }

    private object NativeTransport : Transport {
        override fun resolveEdge(query: CheckerQuery) =
            EdgeInboundSearch.resolve(query.board, query.date, query.id)

        override fun load(query: CheckerQuery) =
            if (query.site == CheckerQuery.Site.EDGE) EdgeAdapter().load(query)
            else HissiAdapter().load(query)

        override fun nextPage(url: String, page: Int) = HissiAdapter().nextPage(url, page)
        override fun diagnostic() = HttpTransport.lastDiagnostic()
    }

    sealed class Load {
        data class Ready(val query: CheckerQuery, val result: CheckerResult) : Load()
        data class WrongDay(val query: CheckerQuery) : Load()
        object NotFound : Load()
        data class WebFallback(val query: CheckerQuery, val reason: String, val diagnostic: String?) : Load()
        data class Failed(val message: String?, val resolvingEdge: Boolean) : Load()
    }

    /** Call on a worker; the date-verification choice is captured before scheduling. */
    fun load(initial: CheckerQuery, verifyDate: Boolean): Load {
        val inbound = initial.site == CheckerQuery.Site.EDGE && initial.url.isEmpty()
        var target = initial
        try {
            if (inbound) target = transport.resolveEdge(initial) ?: return Load.NotFound
            val parsed = transport.load(target)
            val verify = target.site == CheckerQuery.Site.EDGE && (inbound || verifyDate)
            val result = if (verify && parsed.posts.isNotEmpty()) {
                EdgeSearchValidation.matchingPosts(parsed, target.date, target.id)
            } else parsed
            if (verify && parsed.posts.isNotEmpty() && result.posts.isEmpty()) return Load.WrongDay(target)
            if (target.site == CheckerQuery.Site.EDGE && result.posts.isEmpty()) {
                return Load.WebFallback(target, "通常通信では抽出できません: " + result.diagnostic, result.diagnostic)
            }
            return Load.Ready(target, result)
        } catch (error: Exception) {
            if (inbound && target.url.isEmpty()) return Load.Failed(error.message, true)
            if (target.site == CheckerQuery.Site.EDGE) {
                return Load.WebFallback(target,
                    (error.message ?: "通信失敗") + " / " + transport.diagnostic(), null)
            }
            return Load.Failed(error.message, false)
        }
    }

    fun nextPage(url: String, page: Int): CheckerResult = transport.nextPage(url, page)

    companion object {
        /** Run on the model owner thread; no partial or duplicate posts are inserted. */
        @JvmStatic
        fun appendPage(current: CheckerResult, addition: CheckerResult): Boolean {
            if (addition.posts.isEmpty()) return false
            val seen = HashSet<Pair<String, String>>()
            current.posts.forEach { seen.add(it.link to it.meta) }
            var added = false
            for (post in addition.posts) {
                if (seen.add(post.link to post.meta)) {
                    current.posts.add(post)
                    added = true
                }
            }
            return added
        }
    }
}
