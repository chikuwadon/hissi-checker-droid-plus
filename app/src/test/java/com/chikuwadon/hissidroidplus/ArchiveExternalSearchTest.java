package com.chikuwadon.hissidroidplus;

import org.junit.Test;
import static org.junit.Assert.*;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

public class ArchiveExternalSearchTest {
    @Test public void kyodemoUsesObservedArchiveFormAndAndExclusionSyntax() throws Exception {
        ArchiveQuery query = new ArchiveQuery("Android スマホ", "広告 -PR", "", "2026-11-01", "2026-11-26", "", "new", "", false, false, "android", "Android");
        String url = ArchiveExternalSearch.url(ArchiveProvider.FIVECH_KYODEMO, query);
        assertTrue(url.startsWith("https://www.kyodemo.net/sdemo/b/android/?bs=&kt=k_&fr=2026-11-01&to=2026-11-26&k="));
        assertEquals("Android スマホ -広告 -PR",
            URLDecoder.decode(url.substring(url.indexOf("&k=") + 3), "windows-31j"));
        assertTrue(ArchiveExternalSearch.url(ArchiveProvider.FIVECH_KYODEMO, query).contains("kt=k_"));
    }

    @Test public void nukoUsesObservedPastLogAndSubmitOptions() {
        ArchiveQuery basic = new ArchiveQuery("Android & 日本語");
        String normal = ArchiveExternalSearch.url(ArchiveProvider.FIVECH_NUKO, basic);
        assertTrue(normal.startsWith("https://n2ch.net/q/-/?q=Android+%26+%93%FA%96%7B%8C%EA&kako=1&guid=ON"));
        assertFalse(normal.contains("qs="));
        assertFalse(normal.contains("ex="));
        ArchiveQuery exact = new ArchiveQuery("Android", "", "", "", "", "", "new", "", true, true);
        String expanded = ArchiveExternalSearch.url(ArchiveProvider.FIVECH_NUKO, exact);
        assertTrue(expanded.contains("qs=%8A%AE%91%53%88%EA%92%76"));
        assertTrue(expanded.contains("ex=1"));
    }

    @Test public void nativeEdgeRequestKeepsItsExistingParameters() {
        ArchiveQuery query = new ArchiveQuery("Android", "広告", "10", "", "", "100", "old", "on");
        assertEquals(ArchiveRepository.url(query, 1), ArchiveExternalSearch.url(ArchiveProvider.EDGE_ARCHIVE, query));
    }

    @Test public void threadMetadataShowsDateBeforeReplyCount() {
        assertEquals("2026/11/26 12:01:02　レス: 128",
            ArchiveExternalSearch.metadata(new ArchiveThread("title", "url", 128, "2026/11/26 12:01:02")));
        assertEquals("レス: 128",
            ArchiveExternalSearch.metadata(new ArchiveThread("title", "url", 128, "")));
    }
}
