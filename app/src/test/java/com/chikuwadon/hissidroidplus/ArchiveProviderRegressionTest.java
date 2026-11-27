package com.chikuwadon.hissidroidplus;

import org.junit.Test;
import static org.junit.Assert.*;
import java.nio.file.*;
import java.nio.charset.*;
import java.util.*;

public class ArchiveProviderRegressionTest {
    @Test public void savedNukoFormAndResults() throws Exception {
        String html = Files.readString(Paths.get("src/test/resources/nuko-all-board-regression.html"), StandardCharsets.UTF_8);
        for (Charset charset : Arrays.asList(StandardCharsets.UTF_8, Charset.forName("windows-31j"))) {
            String decoded = NukoResponseDecoder.decode(html.getBytes(charset),
                charset.equals(StandardCharsets.UTF_8) ? Charset.forName("windows-31j") : StandardCharsets.UTF_8);
            ArchivePage page = ArchiveProviderParser.nuko(decoded, 1);
            assertEquals(153269L, page.getTotal());
            assertEquals(20, page.getPageSize());
            assertEquals(2, page.getItems().size());
            assertEquals("なんでも実況G", page.getItems().get(1).getBoard());
            assertEquals(2, page.getItems().get(1).getReplies());
            assertTrue(page.getHasNext());
        }
        ArchivePage wide = ArchiveProviderParser.nuko(html.replace("(153269ｽﾚ､585板)", "（１５３２６９スレ、５８５板）"), 1);
        assertEquals(153269L, wide.getTotal());
        assertEquals(1, ArchiveProviderParser.nuko(html, 1, "livegalileo").getItems().size());
    }
    @Test public void nukoBoardParameterAndPagination() {
        ArchiveQuery q = new ArchiveQuery("安倍晋三", "", "", "", "", "", "new", "", false, false, "livegalileo", "なんでも実況G");
        String url = ArchiveExternalSearch.url(ArchiveProvider.FIVECH_NUKO, q, 2);
        assertTrue(url.contains("&b=livegalileo"));
        assertTrue(url.contains("&kako=1"));
        assertTrue(url.contains("&p=1"));
        assertFalse(ArchiveExternalSearch.url(ArchiveProvider.FIVECH_NUKO, new ArchiveQuery("x")).contains("&b="));
    }
    @Test public void kyodemoOriginalThreadUsesOfficialHostAndValidatesBoard() {
        String raw = "https://www.kyodemo.net/sdemo/r/livegalileo/1795704174/";
        assertEquals("https://nova.5ch.io/test/read.cgi/livegalileo/1795704174/",
            ArchiveOriginalLink.kyodemo5ch(raw, "livegalileo", "https://nova.5ch.io/livegalileo/"));
        assertEquals(raw, ArchiveOriginalLink.kyodemo5ch(raw, "livegalileo", "https://evil.example/livegalileo/"));
        assertEquals(raw, ArchiveOriginalLink.kyodemo5ch(raw, "livegalileo", "https://nova.5ch.io/android/"));
        assertEquals(raw, ArchiveOriginalLink.kyodemo5ch(raw, "livegalileo", ""));
        List<BoardMenu.Category> groups = BoardMenu.parseHtml("<b>実況</b><a href='https://nova.5ch.io/livegalileo/'>なんでも実況G</a>");
        assertEquals("https://nova.5ch.io/livegalileo/", groups.get(0).boards.get(0).address);
    }
}
