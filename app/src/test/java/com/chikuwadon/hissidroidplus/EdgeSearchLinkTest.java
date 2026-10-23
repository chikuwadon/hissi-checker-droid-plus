package com.chikuwadon.hissidroidplus;
import org.junit.Test;
import static org.junit.Assert.*;
public final class EdgeSearchLinkTest {
    @Test public void siteSearchUsesIdAndExactDate(){
        String url=EdgeSearchLink.searchUrl("liveedge","20261023","AOvteDVlE");
        assertTrue(url.contains("bs=hi&kt=b&k=AOvteDVlE"));
        assertTrue(url.contains("fr=2026-10-23&to=2026-10-23"));
    }
    @Test public void realThreadKeyFromClickedUrl(){
        String result=EdgeSearchLink.checkerFromThread("liveedge","20261023","ABC12",
            "https://www.kyodemo.net/sdemo/r/e_e_liveedge/1792726415/425");
        assertEquals("https://www.kyodemo.net/sdemo/b/e_e_liveedge/?hi=ABC12&key=1792726415&date=20261023",result);
        assertEquals("",EdgeSearchLink.checkerFromThread("liveedge","20261023","ABC12",
            "https://example.com/sdemo/r/e_e_liveedge/1792726415"));
        assertEquals("",EdgeSearchLink.checkerFromThread("liveedge","20261023","ABC12",
            "https://www.kyodemo.net/sdemo/r/e_e_other/1792726415"));
    }
}
