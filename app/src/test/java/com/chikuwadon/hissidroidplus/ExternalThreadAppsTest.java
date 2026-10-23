package com.chikuwadon.hissidroidplus;
import static org.junit.Assert.*;
import org.junit.Test;
import java.util.List;
import java.util.HashSet;

public final class ExternalThreadAppsTest {
    @Test public void canonicalBeforeAlternates(){
        String target="https://bbs.eddibb.cc/test/read.cgi/liveedge/1792741575/";
        List<String> urls=ExternalThreadApps.urlVariants(target);
        assertEquals(target,urls.get(0));
        assertTrue(urls.contains("https://bbs.eddibb.cc/liveedge/1792741575"));
        assertTrue(urls.contains("http://bbs.eddibb.cc/test/read.cgi/liveedge/1792741575/"));
        assertEquals(4,urls.size());
        assertEquals(urls.size(),new HashSet<>(urls).size());
    }
    @Test public void responsePositionRetained(){
        String target="https://bbs.eddibb.cc/test/read.cgi/liveedge/1792741575/555";
        List<String> urls=ExternalThreadApps.urlVariants(target);
        assertEquals(target,urls.get(0));
        assertTrue(urls.contains("http://bbs.eddibb.cc/liveedge/1792741575/555"));
    }
}
