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
    @Test public void parsesOriginalSiteUrl() {
        CheckerQuery q=CheckerQuery.parseUrl("http://hissi.org/read.php/news4vip/20261022/example.html");
        assertEquals(CheckerQuery.Site.HISSI,q.site);
    }
    @Test public void parsesSharedPost() {
        CheckerQuery q=CheckerQuery.fromSharedText("read.cgi/news4vip/1792649855/32\n2026/10/22(木) 15:00 ID:AbC123");
        assertEquals("AbC123",q.id);
        assertEquals("news4vip",q.board);
    }
    @Test(expected=IllegalArgumentException.class) public void rejectsFakeDomain() {
        CheckerQuery.parseUrl("https://kyodemo.net.invalid/sdemo/b/e_e_liveedge/?hi=x&key=1792649855&date=20261022");
    }
    @Test(expected=IllegalArgumentException.class) public void rejectsInvalidDate() {
        CheckerQuery.hissi("news4vip","20260230","xxx");
    }
}
