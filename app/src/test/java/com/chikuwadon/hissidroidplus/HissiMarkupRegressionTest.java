package com.chikuwadon.hissidroidplus;
import static org.junit.Assert.*;
import org.junit.Test;

public final class HissiMarkupRegressionTest {
    @Test public void canonicalResultUrlUsesBase64WithoutPadding(){
        assertEquals("https://hissi.org/read.php/wmotenai/20260424/RXhhalVJaDA.html",
            HissiAdapter.resultUrl("wmotenai","20260424","ExajUIh0"));
        assertEquals("https://hissi.org/read.php/livebase/20260605/YzU5VU1GNys.html",
            HissiAdapter.resultUrl("livebase","20260605","c59UMF7+"));
    }

    @Test public void attributeOrderAndExtraDateMarkupDontHidePosts(){
        String page="<html><body><h3>書き込み順位＆時間帯一覧</h3>"+
                "<div>13 位/581 ID中</div>"+
                "<h3>書き込みレス一覧</h3>"+
                "<dl><a class='thread' target='_blank' href='https://egg.5ch.net/test/read.cgi/wmotenai/200/46'>"+
                "お気に入りスレ</a><br/>"+
                "46 ：<b class='name'>彼氏いない歴774年</b>[sage]：2026/04/24(金) 02:36:44.94 ID:ExajUIh0"+
                "<dd>レス本文 &amp; サンプル<br/>次の行</dd></dl>"+
                "<dl><a href='https://egg.5ch.net/test/read.cgi/wmotenai/200/47' target='_blank'>"+
                "お気に入りスレ</a><br>"+
                "47 : <b>彼氏いない歴774年</b>[]：2026/04/24(金) 02:38:53.15 ID:ExajUIh0"+
                "<dd>二番目の投稿</dd></dl></body></html>";
        CheckerResult data=HissiAdapter.parse(page,"https://hissi.org/read.php/wmotenai/20260424/RXhhalVJaDA.html");
        assertEquals(2,data.posts.size());
        assertEquals("13位 / 581ID中",data.rank);
        assertTrue(data.posts.get(0).body.contains("レス本文 & サンプル"));
        assertTrue(PostFormatter.header(data.posts.get(0)).startsWith("46 "));
        assertTrue(PostFormatter.header(data.posts.get(0)).contains("ID:ExajUIh0"));
        assertTrue(PostFormatter.header(data.posts.get(0)).contains("sage"));
        assertEquals(1,PostIndex.uniqueThreads(data).size());
    }
}
