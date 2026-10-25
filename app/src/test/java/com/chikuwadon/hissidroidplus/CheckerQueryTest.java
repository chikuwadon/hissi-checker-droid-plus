package com.chikuwadon.hissidroidplus;

import static org.junit.Assert.*;
import org.junit.Test;

public class CheckerQueryTest {
    @Test public void parsesEdgeSample() {
        CheckerQuery q=CheckerQuery.parseUrl("https://www.kyodemo.net/sdemo/b/e_e_liveedge/?hi=Ar7vkh7JR&key=1792649855&date=20261022");
        assertEquals(CheckerQuery.Site.EDGE,q.site);
        assertEquals("Ar7vkh7JR",q.id);
        assertEquals("20261022",q.date);
    }
    @Test public void acceptsOptionalIdPrefix(){
        assertEquals("AbC123",CheckerQuery.hissi("news4vip","20261022"," ID: AbC123 ").id);
        assertEquals("AbC123",CheckerQuery.hissi("news4vip","20261022","ID：AbC123").id);
        assertEquals("AbC123",CheckerQuery.hissi("news4vip","20261022","AbC123").id);
    }
    @Test public void parsesHissiSiteUrl() {
        CheckerQuery q=CheckerQuery.parseUrl("http://hissi.org/read.php/news4vip/20261022/example.html");
        assertEquals(CheckerQuery.Site.HISSI,q.site);
    }
    @Test public void parsesSharedPost() {
        CheckerQuery q=CheckerQuery.fromSharedText("read.cgi/news4vip/1792649855/32\n2026/10/22(木) 15:00 ID:AbC123");
        assertEquals("AbC123",q.id);
        assertEquals("news4vip",q.board);
    }
    @Test public void parsesEdgePostSharedByChMateOrHaiagaru(){
        String shared="https://bbs.eddibb.cc/test/read.cgi/liveedge/1761342000/1\\n"+
                "1 (1) エッヂの名無しさん 2026/10/25(日) 06:11:31.090 ID:K/wziC9v9";
        CheckerQuery q=CheckerQuery.fromSharedText(shared);
        assertEquals(CheckerQuery.Site.EDGE,q.site);
        assertEquals("liveedge",q.board);
        assertEquals("20261025",q.date);
        assertEquals("K/wziC9v9",q.id);
        assertEquals("",q.url);
    }
    @Test public void routesLegacyChMateExtrasFromKnownEdgeBoard(){
        CheckerQuery q=CheckerQuery.edgeSearch("liveedge","20261025","ID: K/wziC9v9");
        assertEquals(CheckerQuery.Site.EDGE,q.site);
        assertEquals("K/wziC9v9",q.id);
        assertEquals("",q.url);
        assertTrue(CheckerQuery.isKnownEdgeBoard("liveedge"));
        assertFalse(CheckerQuery.isKnownEdgeBoard("news4vip"));
    }
    @Test public void domainlessEdgePostIsNotConvertedInto5chSearch(){
        CheckerQuery q=CheckerQuery.fromSharedText(
                "read.cgi/liveedge/1761342000/2\\n2026/10/25(日) 06:12 ID:M9NT8Y9PE");
        assertEquals(CheckerQuery.Site.EDGE,q.site);
        assertEquals("M9NT8Y9PE",q.id);
    }
    @Test public void preservesOrdinary5chPostShare(){
        CheckerQuery q=CheckerQuery.fromSharedText(
                "https://example.5ch.net/test/read.cgi/news4vip/1761342000/1\\n"+
                "2026/10/25(日) 06:11 ID:Example42");
        assertEquals(CheckerQuery.Site.HISSI,q.site);
        assertEquals("news4vip",q.board);
    }
    @Test public void usesRealSourceDomainRatherThanBoardName(){
        CheckerQuery edge=CheckerQuery.fromExternalPayload(
                "https://bbs.eddibb.cc/test/read.cgi/anotherboard/1761342000/5",
                null,"anotherboard","20261025","Example123");
        assertEquals(CheckerQuery.Site.EDGE,edge.site);
        assertEquals("anotherboard",edge.board);
        assertEquals("",edge.url);

        // Even a board called "liveedge" cannot override an actual 5ch URL.
        CheckerQuery five=CheckerQuery.fromExternalPayload(
                "https://example.5ch.net/test/read.cgi/liveedge/1761342000/5",
                null,"liveedge","20261025","Example123");
        assertEquals(CheckerQuery.Site.HISSI,five.site);
        assertEquals("liveedge",five.board);
    }
    @Test public void edgeDomainOverridesLegacyBoardHeuristicsInSharedText(){
        String post="https://bbs.eddibb.cc/test/read.cgi/anotherboard/1761342000/1\\n"+
                "2026/10/25(日) 06:11:31.090 ID:Example123";
        CheckerQuery q=CheckerQuery.fromExternalPayload(null,post,null,null,null);
        assertEquals(CheckerQuery.Site.EDGE,q.site);
        assertEquals("anotherboard",q.board);
        assertEquals("Example123",q.id);
    }
    @Test public void sourceDomainMustMatchExactly(){
        assertNull(CheckerQuery.sourceFromThreadUrl(
                "https://bbs.eddibb.cc.evil.example/test/read.cgi/liveedge/1761342000/1"));
        assertNull(CheckerQuery.sourceFromThreadUrl(
                "https://bbs.eddibb.cc:8443/test/read.cgi/liveedge/1761342000/1"));
        assertNull(CheckerQuery.sourceFromThreadUrl(
                "javascript:https://bbs.eddibb.cc/test/read.cgi/liveedge/1761342000/1"));
        assertEquals(CheckerQuery.Site.EDGE,CheckerQuery.sourceFromThreadUrl(
                "https://bbs.eddibb.cc/test/read.cgi/liveedge/1761342000/1"));
    }
    @Test public void known5chSourceOverridesLegacyBoardHeuristicInText(){
        String post="https://example.5ch.net/test/read.cgi/liveedge/1761342000/1\\n"+
                "2026/10/25(日) 06:11:31.090 ID:Example123";
        CheckerQuery q=CheckerQuery.fromSharedText(post);
        assertEquals(CheckerQuery.Site.HISSI,q.site);
    }
    @Test(expected=IllegalArgumentException.class) public void rejectsFakeDomain() {
        CheckerQuery.parseUrl("https://kyodemo.net.invalid/sdemo/b/e_e_liveedge/?hi=x&key=1792649855&date=20261022");
    }
    @Test(expected=IllegalArgumentException.class) public void rejectsInvalidDate() {
        CheckerQuery.hissi("news4vip","20260230","xxx");
    }
}
