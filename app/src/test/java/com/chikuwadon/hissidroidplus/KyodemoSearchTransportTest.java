package com.chikuwadon.hissidroidplus;

import org.junit.Test;
import static org.junit.Assert.*;

public class KyodemoSearchTransportTest {
    private static final String CAPTURED = "https://www.kyodemo.net/sdemo/b/e_p_vaporeon/?bs=&kt=k_&fr=&to=&k=%8C%B4%90_";
    @Test public void browserFormUsesSafeBytesWithinShiftJisCharacters() {
        ArchiveQuery q = new ArchiveQuery("原神", "", "", "", "", "", "new", "", false, false, "e_p_vaporeon", "ぷにぷに");
        assertEquals(CAPTURED, ArchiveExternalSearch.url(ArchiveProvider.OTHER_KYODEMO, q, 1));
        assertEquals("%81%9A", ArchiveExternalSearch.encodeKyodemoField("★"));
        assertEquals("A+B%26C%2B", ArchiveExternalSearch.encodeKyodemoField("A B&C+"));
        assertEquals("原神", ArchiveExternalSearch.decodeKyodemoField("%8C%B4%90_"));
        assertEquals("原神", ArchiveExternalSearch.decodeKyodemoField("%8C%B4%90%5F"));
        assertEquals("A B&C+", ArchiveExternalSearch.decodeKyodemoField("A+B%26C%2B"));
        for (String value : new String[]{"原神", "★", "Android スマホ -広告 -PR", "安倍晋三", "あいうえお", "a+b & c", "日本語"}) {
            assertEquals(value, ArchiveExternalSearch.decodeKyodemoField(ArchiveExternalSearch.encodeKyodemoField(value)));
        }
    }
    @Test public void browserFallbackRejectsIndexWrongBoardAndLostSearchConditions() {
        String root = "https://www.kyodemo.net/sdemo/b/e_p_vaporeon/";
        assertTrue(KyodemoArchiveBrowserFallback.allowed(root, CAPTURED));
        assertFalse(KyodemoArchiveBrowserFallback.matchesSearch(root, CAPTURED));
        assertTrue(KyodemoArchiveBrowserFallback.matchesSearch(CAPTURED, CAPTURED));
        assertTrue(KyodemoArchiveBrowserFallback.matchesSearch(root + "?kt=k_&k=%8C%B4%90%5F", CAPTURED));
        assertFalse(KyodemoArchiveBrowserFallback.matchesSearch(root + "?kt=k_&k=other", CAPTURED));
        assertFalse(KyodemoArchiveBrowserFallback.matchesSearch(root + "?kt=k_&k=%8C%B4%90A", CAPTURED));
        assertFalse(KyodemoArchiveBrowserFallback.matchesSearch(root + "?kt=k_&k=%8C%B4%90%ZZ", CAPTURED));
        assertFalse(KyodemoArchiveBrowserFallback.allowed("https://evil.example/sdemo/b/e_p_vaporeon/", CAPTURED));
        assertFalse(KyodemoArchiveBrowserFallback.allowed("https://www.kyodemo.net/sdemo/b/e_p_leafeon/", CAPTURED));
        assertFalse(KyodemoArchiveBrowserFallback.matchesSearch(root + "?kt=k_&k=%8C%B4%90_", CAPTURED + "&o1=1"));
        String page2 = root + "51?kt=k_&k=%8C%B4%90_&j=1";
        assertFalse(KyodemoArchiveBrowserFallback.matchesSearch(CAPTURED, page2));
    }

    @Test public void pageLinkMatchesActualCapturedPagingLinksAndPreservesFilters() {
        String root = "https://www.kyodemo.net/sdemo/b/e_p_vaporeon/";
        assertEquals(root + "?k=%8C%B4%90_&kt=k_&j=1", ArchiveExternalSearch.kyodemoPageLink(CAPTURED));
        assertEquals(root + "51?k=%8C%B4%90_&kt=k_&j=1", ArchiveExternalSearch.kyodemoPageLink(
            root + "51?bs=&kt=k_&fr=&to=&k=%8C%B4%90_&j=1"));
        String filtered = CAPTURED + "&o1=1&j=a";
        String compact = ArchiveExternalSearch.kyodemoPageLink(filtered);
        assertTrue(compact.endsWith("&o1=1&j=a"));
        assertTrue(KyodemoArchiveBrowserFallback.matchesSearch(compact, filtered));
        assertFalse(KyodemoArchiveBrowserFallback.matchesSearch(root, CAPTURED));
        assertTrue(KyodemoArchiveBrowserFallback.matchesSearch(root + "1?k=%8C%B4%90_&kt=k_&j=1", CAPTURED));
        assertTrue(KyodemoArchiveBrowserFallback.allowed(root.substring(0, root.length()-1), CAPTURED));
        assertFalse(KyodemoArchiveBrowserFallback.matchesSearch(root + "51?k=%8C%B4%90_&kt=k_&j=1", CAPTURED));
    }
    @Test public void categoryFilterRetainsOriginalIndicesIncludingDuplicateLabels() {
        java.util.List<String> keys = java.util.Arrays.asList("人気 o_1", "運営 o_2", "ニュース o_5", "運営 o_8", "AA o_10");
        assertEquals(java.util.Arrays.asList(1,3), CategoryLabelSearch.indices(keys,"運営"));
        assertEquals(java.util.Arrays.asList(3), CategoryLabelSearch.indices(keys,"運営 o_8"));
        assertEquals(java.util.Arrays.asList(4), CategoryLabelSearch.indices(keys,"ａａ"));
        assertEquals(java.util.Collections.emptyList(), CategoryLabelSearch.indices(keys,"不存在"));
    }
}
