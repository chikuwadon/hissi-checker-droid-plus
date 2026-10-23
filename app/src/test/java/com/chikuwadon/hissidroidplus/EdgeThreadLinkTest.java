package com.chikuwadon.hissidroidplus;
import static org.junit.Assert.*;
import org.junit.Test;

public final class EdgeThreadLinkTest {
    @Test public void convertsArchiveToOriginalThreadAndPost(){
        assertEquals("https://bbs.eddibb.cc/test/read.cgi/liveedge/1792726415/42",
            EdgeThreadLink.original("https://www.kyodemo.net/sdemo/r/e_e_liveedge/1792726415/42"));
        assertEquals("https://bbs.eddibb.cc/test/read.cgi/liveedge/1792726415/",
            EdgeThreadLink.original("https://www.kyodemo.net/sdemo/r/e_e_liveedge/1792726415/"));
    }
    @Test public void leavesUnknownSourcesAlone(){
        String src="https://evil.example/sdemo/r/e_e_liveedge/1792726415";
        assertEquals(src,EdgeThreadLink.original(src));
        assertEquals("https://www.kyodemo.net/sdemo/b/e_e_liveedge/",
          EdgeThreadLink.original("https://www.kyodemo.net/sdemo/b/e_e_liveedge/"));
    }
}
