package com.chikuwadon.hissidroidplus;

import org.junit.Test;
import static org.junit.Assert.*;

public final class EdgeAdapterTest {
    @Test public void extractsStructuredRow() {
        CheckerQuery q=CheckerQuery.parseUrl("https://www.kyodemo.net/sdemo/b/e_e_liveedge/?hi=abCD123&key=1707378532&date=20240208");
        String html="<html><body><table><tr><td class='name'>名無しさん</td>"
                +"<td><a href='https://www.kyodemo.net/sdemo/r/e_e_liveedge/1707378532/3'>レス3</a>"
                +"<blockquote>テスト&amp;文章</blockquote></td></tr></table></body></html>";
        CheckerResult result=EdgeAdapter.parse(html,q);
        assertEquals(1,result.posts.size());
        assertEquals("テスト&文章",result.posts.get(0).body);
    }
    @Test public void doesNotTreatNavigationAsPost() {
        CheckerQuery q=CheckerQuery.parseUrl("https://www.kyodemo.net/sdemo/b/e_e_liveedge/?hi=abCD123&key=1707378532&date=20240208");
        CheckerResult result=EdgeAdapter.parse("<a href='https://www.kyodemo.net/sdemo/r/e_e_liveedge/1707378532/'>次へ</a>",q);
        assertEquals(0,result.posts.size());
    }
    @Test public void parsesGeneralResponseClass() {
        CheckerQuery q=CheckerQuery.parseUrl("https://www.kyodemo.net/sdemo/b/e_e_liveedge/?hi=abCD123&key=1707378532&date=20240208");
        String html="<div class='response'><a href='https://www.kyodemo.net/sdemo/r/e_e_liveedge/1707378532/8'>8</a>"
                +"<span class='username'>名無し</span><div class='res-text'>本文テスト</div></div>";
        CheckerResult out=EdgeAdapter.parse(html,q);
        assertEquals(1,out.posts.size());
        assertEquals("本文テスト",out.posts.get(0).body);
        assertEquals("名無し",out.names.get(0));
    }
    @Test public void structuralDiagnosticDoesNotLeakMessageBodyOrIds() {
        String html="<html><body><div class='post'>ID:secret123<blockquote>非公開テキスト</blockquote>"
                +"<a href='https://www.kyodemo.net/sdemo/r/e_e_liveedge/1707378532/8'>8</a>"
                +"</div></body></html>";
        String report=EdgeAdapter.describePage(html);
        assertTrue(report.contains("スレリンク="));
        assertFalse(report.contains("secret123"));
        assertFalse(report.contains("非公開テキスト"));
    }
    @Test public void identifiesDeniedPageWithoutInventingPosts() {
        CheckerQuery q=CheckerQuery.parseUrl("https://www.kyodemo.net/sdemo/b/e_e_liveedge/?hi=abCD123&key=1707378532&date=20240208");
        String html="<html><title>403 Forbidden</title><body>Access Denied</body></html>";
        assertEquals(0,EdgeAdapter.parse(html,q).posts.size());
        assertTrue(EdgeAdapter.explainEmptyPage(html).contains("アクセス"));
    }
    @Test public void recognizesAlternatePostClasses() {
        CheckerQuery q=CheckerQuery.parseUrl("https://www.kyodemo.net/sdemo/b/e_e_liveedge/?hi=abCD123&key=1707378532&date=20240208");
        String html="<article class='res-item'><span class='res-name'>エッヂの名無し</span>"+
                "<div class='res-text'>投稿内容</div>"+
                "<a href='/sdemo/r/e_e_liveedge/1707378532/7'>7</a></article>";
        CheckerResult result=EdgeAdapter.parse(html,q);
        assertEquals(1,result.posts.size());
        assertEquals("投稿内容",result.posts.get(0).body);
        assertEquals("エッヂの名無し",result.posts.get(0).author);
    }
}
