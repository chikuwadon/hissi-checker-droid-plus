package com.chikuwadon.hissidroidplus;

import org.junit.Test;
import static org.junit.Assert.*;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

public class ArchiveExternalSearchTest {
    @Test public void kyodemoUsesObservedArchiveFormAndAndExclusionSyntax() throws Exception {
        ArchiveQuery query = new ArchiveQuery("Android スマホ", "広告 -PR", "", "2026-11-01", "2026-11-26");
        String url = ArchiveExternalSearch.url(ArchiveProvider.EDGE_KYODEMO, query);
        assertTrue(url.startsWith("https://www.kyodemo.net/sdemo/s?bs=&kt=ak&fr=2026-11-01&to=2026-11-26&k="));
        assertEquals("Android スマホ -広告 -PR",
            URLDecoder.decode(url.substring(url.indexOf("&k=") + 3), StandardCharsets.UTF_8.name()));
        assertTrue(ArchiveExternalSearch.url(ArchiveProvider.FIVECH_KYODEMO, query).contains("kt=5_"));
    }

    @Test public void nukoUsesObservedPastLogAndSubmitOptions() {
        ArchiveQuery basic = new ArchiveQuery("Android & 日本語");
        String normal = ArchiveExternalSearch.url(ArchiveProvider.FIVECH_NUKO, basic);
        assertTrue(normal.startsWith("https://n2ch.net/q/-/?q=Android+%26+%E6%97%A5%E6%9C%AC%E8%AA%9E&kako=1&guid=ON"));
        assertFalse(normal.contains("qs="));
        assertFalse(normal.contains("ex="));
        ArchiveQuery exact = new ArchiveQuery("Android", "", "", "", "", "", "new", "", true, true);
        String expanded = ArchiveExternalSearch.url(ArchiveProvider.FIVECH_NUKO, exact);
        assertTrue(expanded.contains("qs=%E5%AE%8C%E5%85%A8%E4%B8%80%E8%87%B4"));
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
