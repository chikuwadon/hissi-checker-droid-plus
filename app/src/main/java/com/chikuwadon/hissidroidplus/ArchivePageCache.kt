package com.chikuwadon.hissidroidplus

/** Session-scoped, access-ordered cache. All access stays on the UI owner thread. */
class ArchivePageCache @JvmOverloads constructor(private val capacity: Int = 12) {
    init { require(capacity > 0) }
    private data class Key(val provider: ArchiveProvider, val query: ArchiveQuery, val page: Int)
    data class Position(val index: Int, val offset: Int)
    data class Cached(val result: ArchivePage, val position: Position = Position(0, 0))
    private val pages = LinkedHashMap<Key, Cached>(16, 0.75f, true)

    fun get(provider: ArchiveProvider, query: ArchiveQuery, page: Int): Cached? =
        pages[Key(provider, query, page)]

    fun put(provider: ArchiveProvider, query: ArchiveQuery, result: ArchivePage) {
        pages[Key(provider, query, result.page)] = Cached(result)
        while (pages.size > capacity) pages.remove(pages.keys.first())
    }

    fun rememberPosition(provider: ArchiveProvider, query: ArchiveQuery, page: Int, index: Int, offset: Int) {
        val key = Key(provider, query, page)
        val cached = pages[key] ?: return
        pages[key] = cached.copy(position = Position(index.coerceAtLeast(0), offset))
    }

    fun clear() = pages.clear()
    fun size(): Int = pages.size
}
