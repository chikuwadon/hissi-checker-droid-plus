package com.chikuwadon.hissidroidplus;

import org.junit.Test;
import org.jsoup.Jsoup;
import static org.junit.Assert.*;

/** Minimal fixtures retaining the structure and data of the two supplied MHT captures. */
public class KyodemoMhtRegressionTest {
    private String row(String board, String key, String start, String end, int replies) {
        return "<article class='panel-block'><a href='https://www.kyodemo.net/sdemo/r/"+board+"/"+key+"/'>"
            + "<h2 class='cltitle'>1:Sample</h2><small class='right_i'><b>"+replies+"</b></small>"
            + "<small class='left_i'><i class='fas fa-clock'></i><span>"+start+"</span>"
            + "<i class='fas fa-edit'></i><span>"+end+"<span class='tag'>update</span></span></small></a></article>";
    }
    @Test public void datesReadFirstSpanEvenWhenAdjacent() throws Exception {
        String html = row("s_computer_45040", "1795601857", "11/25 19:17", "11/28 17:19", 138);
        assertEquals("2026/11/25 19:17", KyodemoDates.created(Jsoup.parse(html).selectFirst("a"), 2026));
        String old = row("f_84", "1234567890", "2025/12/24", "2025/12/25", 32);
        assertEquals("2025/12/24", KyodemoDates.created(Jsoup.parse(old).selectFirst("a"), 2026));
    }
    @Test public void punipuniSearchUsesExactCapturedFormAndCappedCount() throws Exception {
        ArchiveQuery q = new ArchiveQuery("★", "", "", "", "", "", "new", "", false, false, "e_p_vaporeon", "ぷにぷに");
        assertEquals("https://www.kyodemo.net/sdemo/b/e_p_vaporeon/?bs=&kt=k_&fr=&to=&k=%81%9A",
            ArchiveExternalSearch.url(ArchiveProvider.OTHER_KYODEMO,q,1));
        String html = "<title>ぷにぷに板 （「★」の全ログ検索結果） - ぷにぷに</title><small class='d-scnt'>5000+スレ(1/100)</small>"
            + row("e_p_vaporeon", "1795861557", "11/28 19:25", "11/28 19:44",96);
        ArchivePage page = ArchiveProviderParser.kyodemoOther(html,1,"e_p_vaporeon","ぷにぷに");
        assertEquals(5000L,page.getTotal());
        assertEquals(100,page.getTotalPages());
        assertTrue(page.getTotalCapped());
        assertEquals(96,page.getItems().get(0).getReplies());
        assertTrue(page.getItems().get(0).getDate().endsWith("/11/28 19:25"));
    }
    @Test public void originalUrlsKeepArchiveAndOriginalSeparate() {
        String sh = "https://www.kyodemo.net/sdemo/r/s_computer_45040/1793701433/";
        assertEquals("https://jbbs.shitaraba.net/bbs/read.cgi/computer/45040/1793701433/", ArchiveOriginalLink.kyodemoOther(sh));
        assertEquals("https://bbs.punipuni.eu/test/read.cgi/vaporeon/1795861557/", ArchiveOriginalLink.kyodemoOther("https://www.kyodemo.net/sdemo/r/e_p_vaporeon/1795861557/"));
        assertEquals("https://machi.to/bbs/read.cgi/hokkaidou/1777204050/", ArchiveOriginalLink.kyodemoOther("https://www.kyodemo.net/sdemo/r/m_hokkaidou/1777204050/"));
        assertEquals("", ArchiveOriginalLink.kyodemoOther("https://evil.example/sdemo/r/s_computer_45040/1793701433/"));
        assertEquals("", ThreadOpener.validatedOtherThreadUrl("https://bbs.punipuni.eu.evil.example/test/read.cgi/vaporeon/1795861557/"));
        String ft = "https://www.kyodemo.net/sdemo/r/f_84/1234567890/";
        assertEquals("https://img.2chan.net/b/res/1234567890.htm", ArchiveOriginalLink.originalFromDetail(ft,
            "<footer>元スレ：<a href='https://img.2chan.net/b/res/1234567890.htm'>https://img.2chan.net/b/res/1234567890.htm</a></footer>"));
        assertEquals("", ArchiveOriginalLink.originalFromDetail(ft,
            "<article>元スレ：<a href='https://img.2chan.net/b/res/1234567890.htm'>fake</a></article>"));
    }
}
