package com.chikuwadon.hissidroidplus

import java.util.ArrayList
import java.util.LinkedHashMap

/** Shared search-result model independent of UI widgets. */
class CheckerResult {
    class Post @JvmOverloads constructor(
        @JvmField val title: String,
        @JvmField val author: String,
        @JvmField val meta: String,
        @JvmField val body: String,
        @JvmField val link: String,
        references: Int = 0
    ) {
        /** Display-only reference count: do not include in copies or names. */
        @JvmField val referenceCount: Int = references.coerceAtLeast(0)
    }

    @JvmField var rank: String = ""
    @JvmField var sourceUrl: String = ""
    @JvmField var diagnostic: String = ""
    @JvmField var sourceThreadSummary: String = ""
    @JvmField var reportedPostCount: Int = -1
    @JvmField var hourlyCounts: IntArray? = null
    @JvmField var referencedCount: Int = -1
    @JvmField var reportedThreadCount: Int = -1
    @JvmField val posts: MutableList<Post> = ArrayList()
    @JvmField val threads: MutableMap<String, String> = LinkedHashMap()
    @JvmField val names: MutableList<String> = ArrayList()
}
