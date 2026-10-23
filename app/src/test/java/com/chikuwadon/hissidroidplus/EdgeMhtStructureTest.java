package com.chikuwadon.hissidroidplus;

import org.junit.Test;
import static org.junit.Assert.*;

/** Anonymized structural fixture, transcribed from the attached 2026-10 MHT. */
public final class EdgeMhtStructureTest {
    private static final String PREFIX="https://www.kyodemo.net/sdemo/r/e_e_liveedge/";
    private static String entry(String key, int number, String body) {
        return "<div class='post'><div class='r-head'><strong><a href='"+PREFIX+key+"/"+number+"'>"+number+"</a></strong>"
            +":エッヂの名無し 2026/10/22(木) 22:06:17.986 <strong>ID:example123</strong></div>"
            +body+"</div>";
    }
    @Test public void extractsActualKyodemoLayoutAndSummary() {
        CheckerQuery query=CheckerQuery.parseUrl(
            "https://www.kyodemo.net/sdemo/b/e_e_liveedge/?hi=example123&key=1792674000&date=20261022");
        String html="<!doctype html><html><head><meta charset='Shift_JIS'></head><body>"
            +"<textarea id='copy-mess'>日付: 2026/10/22\nレス数: 3\n被参照数: 2\nスレッド数: 2 (0スレ作成)</textarea>"
            +"<main id='blist'><div class='box content'><h2><a href='"+PREFIX+"1792674000/'>スレッドA</a></h2>"
            +entry("1792674000",42,"本文A")+entry("1792674000",76,"&gt;&gt;42<br>返信内容")+"</div>"
            +"<div class='box content'><h2><a href='"+PREFIX+"1792600000/'>スレッドB</a></h2>"
            +entry("1792600000",5,"本文B")+"</div></main></body></html>";
        CheckerResult data=EdgeAdapter.parse(html,query);
        assertEquals(3,data.posts.size());
        assertEquals(2,PostIndex.uniqueThreads(data).size());
        assertEquals(1,data.names.size());
        assertEquals("エッヂの名無し",data.posts.get(0).author);
        assertEquals("スレッドA",data.posts.get(0).title);
        assertEquals("本文A",data.posts.get(0).body);
        assertTrue(data.posts.get(1).body.contains("返信内容"));
        assertEquals(3,data.reportedPostCount);
        assertEquals(2,data.reportedThreadCount);
        assertEquals(2,data.referencedCount);
    }
    @Test public void duplicateHeaderAndReferenceBadgeAreNotPartOfAuthor(){
        CheckerQuery query=CheckerQuery.parseUrl(
                "https://www.kyodemo.net/sdemo/b/e_e_liveedge/?hi=example123&key=1792674000&date=20261023");
        String page="<html><body><div class='box content'><h2><a href='"+PREFIX+"1792674000/'>タイトル</a></h2>"
            +"<div class='post'><div class='r-head'><a href='"+PREFIX+"1792674000/506'>506(1)</a>"
            +":エッヂの名無し [sage] 2026/10/23(金) 08:38:49.033 ID:example123 "
            +"506(1):エッヂの名無し</div>本文</div></div></body></html>";
        CheckerResult result=EdgeAdapter.parse(page,query);
        assertEquals(1,result.posts.size());
        assertEquals("エッヂの名無し",result.posts.get(0).author);
        assertEquals(1,result.posts.get(0).referenceCount);
        assertEquals("506 エッヂの名無し sage 2026/10/23(金) 08:38:49.033 ID:example123",
                PostFormatter.header(result.posts.get(0)));
        assertEquals(1,result.names.size());
        assertEquals("エッヂの名無し",result.names.get(0));
    }

}
