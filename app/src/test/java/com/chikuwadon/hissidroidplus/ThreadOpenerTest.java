package com.chikuwadon.hissidroidplus;

import static org.junit.Assert.*;
import org.junit.Test;
import java.util.List;

public final class ThreadOpenerTest {
    @Test public void chmateTriesRegisteredShortBoardBeforeCanonical(){
        List<String> urls=ThreadOpener.chMateUrls(
            EdgeThreadLink.original("https://www.kyodemo.net/sdemo/r/e_e_liveedge/1792741575/555"));
        assertEquals(4,urls.size());
        assertEquals("http://bbs.eddibb.cc/liveedge/1792741575/555",urls.get(0));
        assertEquals("https://bbs.eddibb.cc/liveedge/1792741575/555",urls.get(1));
        assertEquals("http://bbs.eddibb.cc/test/read.cgi/liveedge/1792741575/555",urls.get(2));
        assertEquals("https://bbs.eddibb.cc/test/read.cgi/liveedge/1792741575/555",urls.get(3));
    }
    @Test public void threadWithoutResponseNumberStillWorks(){
        List<String> urls=ThreadOpener.chMateUrls(
            EdgeThreadLink.original("https://bbs.eddibb.cc/liveedge/1792741575"));
        assertEquals("http://bbs.eddibb.cc/liveedge/1792741575",urls.get(0));
        assertEquals("https://bbs.eddibb.cc/test/read.cgi/liveedge/1792741575/",urls.get(3));
    }
    @Test public void unknownHostOrPathsNeverTargetChMate(){
        assertTrue(ThreadOpener.chMateUrls("https://hissi.org/read.php/android/20261023/ABC.html").isEmpty());
        assertTrue(ThreadOpener.chMateUrls("https://evil.example/test/read.cgi/liveedge/1792741575/").isEmpty());
        assertTrue(ThreadOpener.chMateUrls("https://bbs.eddibb.cc/other").isEmpty());
        assertTrue(ThreadOpener.chMateUrls("https://bbs.eddibb.cc/test/read.cgi/liveedge/../1792741575/").isEmpty());
    }
}
