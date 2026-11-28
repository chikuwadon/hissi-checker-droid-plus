package com.chikuwadon.hissidroidplus;
import org.junit.Test;
import static org.junit.Assert.*;
import java.io.IOException;

public class ArchiveProviderParserTest {
    @Test public void kyodemoPageTwoPreservesHighlightedTitleAndBoard() throws Exception {
        String html = "<p>460スレ(2/10)</p><article class='panel-block'><a href='/sdemo/r/android/1793641629/'>"
            + "<h2 class='cltitle'>51:Sample<span class='cllight'>Android</span> test</h2>"
            + "<small class='right_i'><b>11</b><span>0/h</span></small><small class='left_i'><span>Android</span>11/03 02:47</small>"
            + "</a></article>"
            + "<article class='panel-block'><a href='/sdemo/r/livegalileo/1793641629/'><h2 class='cltitle'>Unrelated</h2><small class='right_i'><b>8</b></small></a></article>";
        ArchivePage page = ArchiveProviderParser.kyodemoBoard(html, 2, "android", "Android");
        assertEquals(460, page.getTotal()); assertEquals(10, page.getTotalPages());
        assertEquals(51, page.getFirstIndex()); assertEquals(100, page.getLastIndex());
        assertTrue(page.getHasNext()); assertEquals("SampleAndroid test", page.getItems().get(0).getTitle());
        assertEquals(1, page.getItems().size());
        assertEquals("Android", page.getItems().get(0).getBoard());
        assertEquals(11, page.getItems().get(0).getReplies());
        assertThrows(IOException.class, () -> ArchiveProviderParser.kyodemoBoard(html, 1, "android", "Android"));
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
        assertTrue(ArchiveExternalSearch.url(ArchiveProvider.EDGE_KYODEMO, q, 2).contains("/51?"));
        assertTrue(ArchiveExternalSearch.url(ArchiveProvider.FIVECH_NUKO, q, 2).contains("&p=1"));
        assertFalse(ArchiveExternalSearch.url(ArchiveProvider.FIVECH_NUKO, q, 1).contains("&p="));
    }
    @Test public void invalidResponsesAreNotReportedAsEmptyResults() {
        assertThrows(IOException.class, () -> ArchiveProviderParser.kyodemoBoard("Access denied", 1, "android", "Android"));
        assertThrows(IOException.class, () -> ArchiveProviderParser.nuko("Access denied", 1));
    }

    @Test public void otherBoardSearchAcceptsVerifiedCountVariantsAndExplicitZero() throws Exception {
        String row = "<article class='panel-block'><a href='/sdemo/r/m_hokkaidou/1784728920/'><h2 class='cltitle'>1:北見市</h2>"
            + "<small class='right_i'><b>25</b></small></a></article>";
        ArchivePage full = ArchiveProviderParser.kyodemoOther("<title>北海道地区 全ログ検索結果</title><p>1件（1／1）</p>" + row, 1, "m_hokkaidou", "北海道地区");
        assertEquals(1L, full.getTotal());
        assertEquals(25, full.getItems().get(0).getReplies());
        ArchivePage implicit = ArchiveProviderParser.kyodemoOther("<title>北海道地区 全ログ検索結果</title>" + row, 1, "m_hokkaidou", "北海道地区");
        assertEquals(1, implicit.getItems().size());
        assertEquals(0, ArchiveProviderParser.kyodemoOther("<title>検索結果</title><p>該当するスレッドがありません</p>", 1, "m_hokkaidou", "北海道地区").getItems().size());
        assertThrows(IOException.class, () -> ArchiveProviderParser.kyodemoOther("<title>北海道地区</title>" + row, 1, "m_hokkaidou", "北海道地区"));
        assertThrows(IOException.class, () -> ArchiveProviderParser.kyodemoOther("<title>Forbidden</title>", 1, "m_hokkaidou", "北海道地区"));
        assertThrows(IOException.class, () -> ArchiveProviderParser.kyodemoOther("<title>検索結果</title>" + row, 2, "m_hokkaidou", "北海道地区"));
    }
}
