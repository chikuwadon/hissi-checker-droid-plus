package com.chikuwadon.hissidroidplus;
import static org.junit.Assert.*;
import org.junit.Test;
import java.util.List;

public final class EdgeSearchResultsTest {
    @Test public void uniqueNativeSearchResultsFromSiteLinks(){
        String html="<a href='/sdemo/r/e_e_liveedge/1792726415/3'>スレA</a>"+
            "<a href='/sdemo/r/e_e_liveedge/1792726415/8'>スレA (重複)</a>"+
            "<a href='/sdemo/r/e_e_liveedge/1792726499/15'>スレB</a>"+
            "<a href='https://example.com/sdemo/r/e_e_liveedge/1792726500'>外部</a>";
        List<EdgeSearchResults.Item> result=EdgeSearchResults.parse(
            html,"https://www.kyodemo.net/sdemo/b/e_e_liveedge/",
            "liveedge","20261023","TESTid");
        assertEquals(2,result.size());
        assertEquals("スレA",result.get(0).title);
        assertTrue(result.get(0).checkerUrl.contains("key=1792726415"));
        assertTrue(result.get(1).checkerUrl.contains("key=1792726499"));
    }
    @Test public void noSyntheticKeyWhenNoThreadFound(){
        assertTrue(EdgeSearchResults.parse("<p>検索なし</p>",
                "https://www.kyodemo.net/sdemo/b/e_e_liveedge/",
                "liveedge","20261023","ABC").isEmpty());
    }
}
