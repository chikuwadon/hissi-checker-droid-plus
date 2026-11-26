package com.chikuwadon.hissidroidplus;

import org.junit.Test;
import static org.junit.Assert.*;
import java.io.IOException;

public class ArchiveRepositoryTest {
    @Test public void queryEncodesInputsAndKeepsPageAndSort() {
        ArchiveQuery q = new ArchiveQuery("Android & 日本語", "除外+", "10", "2026-11-01", "2026-11-26");
        String url = ArchiveRepository.url(q, 2);
        assertTrue(url.startsWith("https://eddiarchive3rd.boy.jp/?"));
        assertTrue(url.contains("keyword=Android+%26+%E6%97%A5%E6%9C%AC%E8%AA%9E"));
        assertTrue(url.contains("keyword_exclude=%E9%99%A4%E5%A4%96%2B"));
        assertTrue(url.contains("NoR=10"));
        assertTrue(url.contains("start_date=2026-11-01"));
        assertTrue(url.contains("end_date=2026-11-26"));
        assertTrue(url.contains("sort=new"));
        assertTrue(url.contains("page=2"));
    }
    @Test public void validatesDateRangeAndMinimum() {
        assertThrows(IllegalArgumentException.class, () ->
            new ArchiveQuery("x", "", "-1", "", "").validate());
        assertThrows(IllegalArgumentException.class, () ->
            new ArchiveQuery("x", "", "", "2026-11-26", "2026-11-01").validate());
        assertThrows(IllegalArgumentException.class, () ->
            new ArchiveQuery(" ", "", "", "", "").validate());
    }
    @Test public void parsesAndDeduplicatesTrustedThreadRows() throws Exception {
        String html = "<p>検索結果：1,234件（0.1秒）</p>"
            + "<div><a href='https://bbs.eddibb.cc/test/read.cgi/liveedge/1795681839/'>Android (12)</a> 2026/11/26 12:01:02</div>"
            + "<div><a href='https://bbs.eddibb.cc/test/read.cgi/liveedge/1795681839/'>duplicate (12)</a></div>"
            + "<a href='https://evil.example/test/read.cgi/liveedge/1795681839/'>evil (9)</a>"
            + "<a href='https://bbs.eddibb.cc/other'>other (9)</a>"
            + "<a href='?page=2&amp;sort=new'>2</a>";
        ArchivePage p = ArchiveRepository.parse(html, 1);
        assertEquals(1234L, p.getTotal());
        assertTrue(p.getHasNext());
        assertEquals(1, p.getItems().size());
        ArchiveThread t = p.getItems().get(0);
        assertEquals("Android", t.getTitle());
        assertEquals(12, t.getReplies());
        assertEquals("2026/11/26 12:01:02", t.getDate());
        assertEquals("https://bbs.eddibb.cc/test/read.cgi/liveedge/1795681839/", t.getUrl());
    }
    @Test public void zeroResultsAreNotAnErrorButUnknownMarkupIs() throws Exception {
        assertEquals(0, ArchiveRepository.parse("<p>検索結果：0件</p>", 1).getItems().size());
        assertThrows(IOException.class, () -> ArchiveRepository.parse("<p>Access denied</p>", 1));
        assertThrows(IOException.class, () -> ArchiveRepository.parse("<p>検索結果：10件</p>", 1));
    }
    @Test public void unimplementedProvidersAreNotSentToNativeTransport() {
        assertTrue(ArchiveProvider.EDGE_ARCHIVE.getNativeSearch());
        assertFalse(ArchiveProvider.FIVECH_NUKO.getNativeSearch());
        assertFalse(ArchiveProvider.FIVECH_KYODEMO.getNativeSearch());
        assertFalse(ArchiveProvider.EDGE_KYODEMO.getNativeSearch());
    }
}
