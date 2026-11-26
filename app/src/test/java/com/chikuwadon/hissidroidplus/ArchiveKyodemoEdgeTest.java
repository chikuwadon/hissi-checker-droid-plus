package com.chikuwadon.hissidroidplus;

import org.junit.Test;
import static org.junit.Assert.*;
import java.io.IOException;

public class ArchiveKyodemoEdgeTest {
    @Test public void boardSearchUsesOffsetPathAndObservedDefaultSort() {
        ArchiveQuery q = new ArchiveQuery("Morphe");
        assertEquals("https://www.kyodemo.net/sdemo/b/e_e_liveedge/?bs=&kt=k_&fr=&to=&k=Morphe",
            ArchiveExternalSearch.url(ArchiveProvider.EDGE_KYODEMO, q, 1));
        assertEquals("https://www.kyodemo.net/sdemo/b/e_e_liveedge/51?bs=&kt=k_&fr=&to=&k=Morphe&j=1",
            ArchiveExternalSearch.url(ArchiveProvider.EDGE_KYODEMO, q, 2));
        assertTrue(ArchiveExternalSearch.url(ArchiveProvider.EDGE_KYODEMO, q, 7).contains("/301?"));
        assertTrue(ArchiveExternalSearch.url(ArchiveProvider.EDGE_KYODEMO, q, 2).endsWith("&j=1"));
    }

    @Test public void observedSortAndReplyFiltersAreSentToServer() {
        ArchiveQuery old = new ArchiveQuery("Morphe", "", "300", "", "", "", "old");
        String url = ArchiveExternalSearch.url(ArchiveProvider.EDGE_KYODEMO, old, 2);
        assertTrue(url.contains("/51?"));
        assertTrue(url.contains("&j=a"));
        assertTrue(url.contains("&o2=1"));
        ArchiveQuery replies = new ArchiveQuery("Morphe", "", "900", "", "", "", "replies");
        String second = ArchiveExternalSearch.url(ArchiveProvider.EDGE_KYODEMO, replies, 1);
        assertTrue(second.contains("&r=1"));
        assertTrue(second.contains("&o3=1"));
    }

    @Test public void edgeBoardResultsUseBoardNameNotDateSpan() throws Exception {
        String html = "<p>322スレ(2/7)</p>"
            + "<article class='panel-block'><a href='https://www.kyodemo.net/sdemo/r/e_e_liveedge/1793128924/'>"
            + "<h2 class='cltitle'>51:Sample <span class='cllight'>Morphe</span></h2>"
            + "<small class='right_i'><b>3</b><span>0/h</span></small>"
            + "<small class='left_i'><span>10/28 04:22</span></small></a></article>"
            + "<article class='panel-block'><a href='/sdemo/r/android/1793128924/'><h2 class='cltitle'>Other board</h2><small class='right_i'><b>5</b></small></a></article>";
        ArchivePage page = ArchiveProviderParser.kyodemoEdge(html, 2);
        assertEquals(322L, page.getTotal());
        assertEquals(7, page.getTotalPages());
        assertEquals(51L, page.getFirstIndex());
        assertEquals(100L, page.getLastIndex());
        assertEquals(1, page.getItems().size());
        ArchiveThread row = page.getItems().get(0);
        assertEquals("エッヂ", row.getBoard());
        assertEquals("Sample Morphe", row.getTitle());
        assertEquals(3, row.getReplies());
        assertEquals("https://bbs.eddibb.cc/test/read.cgi/liveedge/1793128924/", ThreadOpener.validatedThreadUrl(row.getUrl()));
        assertThrows(IOException.class, () -> ArchiveProviderParser.kyodemoEdge(html, 1));
    }
}
