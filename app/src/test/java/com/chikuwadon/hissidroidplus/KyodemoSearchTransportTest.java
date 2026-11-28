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
    }
    @Test public void browserFallbackRejectsIndexWrongBoardAndLostSearchConditions() {
        String root = "https://www.kyodemo.net/sdemo/b/e_p_vaporeon/";
        assertTrue(KyodemoArchiveBrowserFallback.allowed(root, CAPTURED));
        assertFalse(KyodemoArchiveBrowserFallback.matchesSearch(root, CAPTURED));
        assertTrue(KyodemoArchiveBrowserFallback.matchesSearch(CAPTURED, CAPTURED));
        assertTrue(KyodemoArchiveBrowserFallback.matchesSearch(root + "?kt=k_&k=%8C%B4%90%5F", CAPTURED));
        assertFalse(KyodemoArchiveBrowserFallback.matchesSearch(root + "?kt=k_&k=other", CAPTURED));
        assertFalse(KyodemoArchiveBrowserFallback.allowed("https://evil.example/sdemo/b/e_p_vaporeon/", CAPTURED));
        assertFalse(KyodemoArchiveBrowserFallback.allowed("https://www.kyodemo.net/sdemo/b/e_p_leafeon/", CAPTURED));
        assertFalse(KyodemoArchiveBrowserFallback.matchesSearch(root + "?kt=k_&k=%8C%B4%90_", CAPTURED + "&o1=1"));
        String page2 = root + "51?kt=k_&k=%8C%B4%90_&j=1";
        assertFalse(KyodemoArchiveBrowserFallback.matchesSearch(CAPTURED, page2));
    }
}
