package com.chikuwadon.hissidroidplus;

import static org.junit.Assert.*;
import org.junit.Test;

public final class ThreadOpenerTest {
    @Test public void chmateReceivesHttpCanonicalEdgeThread(){
        assertEquals("http://bbs.eddibb.cc/test/read.cgi/liveedge/1792741575/",
            ThreadOpener.mateHttpUrl(EdgeThreadLink.original(
                "https://bbs.eddibb.cc/liveedge/1792741575")));
        assertEquals("http://bbs.eddibb.cc/test/read.cgi/liveedge/1792741575/555",
            ThreadOpener.mateHttpUrl(EdgeThreadLink.original(
                "https://www.kyodemo.net/sdemo/r/e_e_liveedge/1792741575/555")));
    }
    @Test public void neverTreatHissiOrUnknownHostsAsChmateThread(){
        assertEquals("",ThreadOpener.mateHttpUrl("https://hissi.org/read.php/android/20261023/ABC.html"));
        assertEquals("",ThreadOpener.mateHttpUrl("https://evil.example/test/read.cgi/liveedge/1792741575/"));
        assertEquals("",ThreadOpener.mateHttpUrl("https://bbs.eddibb.cc/other"));
    }
}
