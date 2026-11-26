package com.chikuwadon.hissidroidplus;

import java.util.Collections;
import org.junit.Test;
import static org.junit.Assert.*;

public class ArchivePageCacheTest {
    private final ArchiveQuery query = new ArchiveQuery("Android");
    private final ArchiveProvider provider = ArchiveProvider.EDGE_ARCHIVE;

    private ArchivePage page(int number) {
        return new ArchivePage(Collections.emptyList(), 1234L, number, number < 25);
    }

    @Test public void rangeAndPageCountHandleFirstMiddleLastAndEmpty() {
        assertEquals(25, page(1).getTotalPages());
        assertEquals(1L, page(1).getFirstIndex());
        assertEquals(50L, page(1).getLastIndex());
        assertEquals(101L, page(3).getFirstIndex());
        assertEquals(150L, page(3).getLastIndex());
        assertEquals(1201L, page(25).getFirstIndex());
        assertEquals(1234L, page(25).getLastIndex());
        ArchivePage zero = new ArchivePage(Collections.emptyList(), 0, 1, false);
        assertEquals(0, zero.getTotalPages());
        assertEquals(0L, zero.getFirstIndex());
        assertEquals(0L, zero.getLastIndex());
        ArchivePage exact = new ArchivePage(Collections.emptyList(), 100L, 2, false);
        assertEquals(2, exact.getTotalPages());
        assertEquals(100L, exact.getLastIndex());
    }

    @Test public void cacheKeepsPageAndScrollPositionWithoutReload() {
        ArchivePageCache cache = new ArchivePageCache();
        ArchivePage a = page(1);
        cache.put(provider, query, a);
        cache.rememberPosition(provider, query, 1, 23, -12);
        cache.put(provider, query, page(2));
        ArchivePageCache.Cached cached = cache.get(provider, query, 1);
        assertSame(a, cached.getResult());
        assertEquals(23, cached.getPosition().getIndex());
        assertEquals(-12, cached.getPosition().getOffset());
    }

    @Test public void cacheIsolatesProvidersAndAllSearchParameters() {
        ArchivePageCache cache = new ArchivePageCache();
        cache.put(provider, query, page(1));
        assertNull(cache.get(ArchiveProvider.EDGE_KYODEMO, query, 1));
        assertNull(cache.get(provider, new ArchiveQuery("other"), 1));
        assertNull(cache.get(provider, new ArchiveQuery("Android", "", "", "", "", "100"), 1));
        assertNull(cache.get(provider, new ArchiveQuery("Android", "", "", "", "", "", "old"), 1));
        assertNull(cache.get(provider, new ArchiveQuery("Android", "", "", "", "", "", "new", "on"), 1));
        assertNull(cache.get(provider, query, 2));
        cache.clear();
        assertEquals(0, cache.size());
    }

    @Test public void leastRecentlyUsedPageIsEvictedAtCapacity() {
        ArchivePageCache cache = new ArchivePageCache(2);
        cache.put(provider, query, page(1));
        cache.put(provider, query, page(2));
        assertNotNull(cache.get(provider, query, 1));
        cache.put(provider, query, page(3));
        assertEquals(2, cache.size());
        assertNull(cache.get(provider, query, 2));
        assertNotNull(cache.get(provider, query, 1));
        assertNotNull(cache.get(provider, query, 3));
    }

    @Test public void defaultCacheIsBoundedAndFilteringDoesNotChangeServerRange() {
        ArchivePageCache cache = new ArchivePageCache();
        for (int i = 1; i <= 13; i++) cache.put(provider, query, page(i));
        assertEquals(12, cache.size());
        assertNull(cache.get(provider, query, 1));
        ArchivePage filtered = ArchiveRepository.filterRange(page(3),
            new ArchiveQuery("Android", "", "100", "", "", "200"));
        assertEquals(101L, filtered.getFirstIndex());
        assertEquals(150L, filtered.getLastIndex());
        assertEquals(25, filtered.getTotalPages());
        assertTrue(filtered.getHasNext());
    }
}
