package com.chikuwadon.hissidroidplus;
import org.junit.Test;
import static org.junit.Assert.*;
import java.io.IOException;

public class ArchiveProviderParserTest {
    @Test public void kyodemoPageTwoPreservesHighlightedTitleAndBoard() throws Exception {
        String html = "<p>460スレ(2/10)</p><article class='panel-block'><a href='/sdemo/r/android/1793641629/'>"
            + "<h2 class='cltitle'>51:Sample<span class='cllight'>Android</span> test</h2>"
            + "<small class='right_i'><b>11</b><span>0/h</span></small><small class='left_i'><span>Android</span>11/03 02:47</small>"
            + "</a></article>";
        ArchivePage page = ArchiveProviderParser.kyodemo(html, 2);
        assertEquals(460, page.getTotal()); assertEquals(10, page.getTotalPages());
        assertEquals(51, page.getFirstIndex()); assertEquals(100, page.getLastIndex());
        assertTrue(page.getHasNext()); assertEquals("SampleAndroid test", page.getItems().get(0).getTitle());
        assertEquals("Android", page.getItems().get(0).getBoard());
        assertEquals(11, page.getItems().get(0).getReplies());
        assertThrows(IOException.class, () -> ArchiveProviderParser.kyodemo(html, 1));
    }
    @Test public void nukoPageTwoUsesTwentyRowsAndArchiveLink() throws Exception {
        String html = "<p>「sample」の検索結果(129ｽﾚ､21板)</p>[前|21-40件目|次]<hr>"
            + "21:<a href='/r/-/android/1793629388/?guid=ON&amp;rc=14'>Sample (14)</a> [Android]<br>"
            + "<a href='https://evil.example/r/-/android/1793629388/'>Bad (3)</a>";
        ArchivePage page = ArchiveProviderParser.nuko(html, 2);
        assertEquals(129, page.getTotal()); assertEquals(7, page.getTotalPages());
        assertEquals(21, page.getFirstIndex()); assertEquals(40, page.getLastIndex());
        assertEquals(1, page.getItems().size()); assertEquals("Android", page.getItems().get(0).getBoard());
        assertEquals("https://n2ch.net/r/-/android/1793629388/?guid=ON&rc=14", page.getItems().get(0).getUrl());
        assertThrows(IOException.class, () -> ArchiveProviderParser.nuko(html, 1));
    }
    @Test public void providerPageParametersAreDifferent() {
        ArchiveQuery q = new ArchiveQuery("sample");
        assertTrue(ArchiveExternalSearch.url(ArchiveProvider.EDGE_KYODEMO, q, 2).contains("&j=2"));
        assertTrue(ArchiveExternalSearch.url(ArchiveProvider.FIVECH_NUKO, q, 2).contains("&p=1"));
        assertFalse(ArchiveExternalSearch.url(ArchiveProvider.FIVECH_NUKO, q, 1).contains("&p="));
    }
    @Test public void invalidResponsesAreNotReportedAsEmptyResults() {
        assertThrows(IOException.class, () -> ArchiveProviderParser.kyodemo("Access denied", 1));
        assertThrows(IOException.class, () -> ArchiveProviderParser.nuko("Access denied", 1));
    }
}
