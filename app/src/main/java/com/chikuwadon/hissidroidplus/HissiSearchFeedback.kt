package com.chikuwadon.hissidroidplus

/** Never label an arbitrary HTTP error as a successful empty search. */
object HissiSearchFeedback {
    @JvmStatic
    fun missingSearchResult(query: CheckerQuery?, error: String?): Boolean =
        query != null &&
            query.site == CheckerQuery.Site.HISSI &&
            query.url.isEmpty() &&
            error?.trim() == "HTTP 404"
}
