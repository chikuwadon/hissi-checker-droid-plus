package com.chikuwadon.hissidroidplus;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class ArchiveBoardSearchTest {
    @Test public void fivechBoardUsesArchiveScopeAndOffset() {
        ArchiveQuery q = new ArchiveQuery("安倍晋三", "", "", "", "", "", "new", "", false, false, "livegalileo", "なんでも実況G");
        assertEquals("https://www.kyodemo.net/sdemo/b/livegalileo/?bs=&kt=k_&fr=&to=&k=%88%C0%94%7B%90W%8EO", ArchiveExternalSearch.url(ArchiveProvider.FIVECH_KYODEMO,q));
        assertTrue(ArchiveExternalSearch.url(ArchiveProvider.FIVECH_KYODEMO,q,3).contains("/101?"));
        assertThrows(IllegalArgumentException.class, () -> ArchiveExternalSearch.url(ArchiveProvider.FIVECH_KYODEMO,new ArchiveQuery("x")));
        assertThrows(IllegalArgumentException.class, () -> new ArchiveQuery("x","","","","","","new","",false,false,"../x","").validate());
    }
    @Test public void cappedResultsAreRecognizedAndBoardNamesRetained() throws Exception {
        String html = "<p>5000+スレ(3/100)</p><article class='panel-block'><a href='/sdemo/r/livegalileo/1793863900/'><h2 class='cltitle'>101:Sample</h2><small class='right_i'><b>13</b></small><small class='left_i'><span>11/05 16:31</span></small></a></article>";
        ArchivePage page = ArchiveProviderParser.kyodemoBoard(html,3,"livegalileo","なんでも実況G");
        assertTrue(page.getTotalCapped());
        assertEquals(5000L,page.getTotal());
        assertEquals(100,page.getTotalPages());
        assertEquals(101L,page.getFirstIndex());
        assertEquals(150L,page.getLastIndex());
        assertEquals("なんでも実況G",page.getItems().get(0).getBoard());
    }
    @Test public void keywordHistoryMovesDuplicateToFrontAndBoundsSize() {
        List<String> initial = Arrays.asList("abc","def","ghi");
        assertEquals(Arrays.asList("def","abc","ghi"),ArchiveKeywordHistory.recent(initial," def "));
        assertEquals(initial,ArchiveKeywordHistory.recent(initial," "));
        List<String> many = new ArrayList<>();
        for(int i=0;i<150;i++) many=ArchiveKeywordHistory.recent(many,"word"+i);
        assertEquals(100,many.size()); assertEquals("word149",many.get(0)); assertFalse(many.contains("word0"));
    }
}
