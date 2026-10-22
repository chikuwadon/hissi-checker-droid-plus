package com.chikuwadon.hissidroidplus;

import static org.junit.Assert.*;
import org.junit.Test;

public class ParserContractTest {
    @Test public void syntheticOriginalHissiHtml() {
        String data="<html><body><h3>書き込んだスレッド一覧</h3>"+
           "<table><tr><td>名前<br></td><td><a href='http://example.com/thread/1'>サンプルスレ</a></td></tr></table>"+
           "<h3>書き込みレス一覧</h3>"+
           "<a href=http://example.com/thread/1/12 target=\"_blank\">サンプルスレ</a>"+
           "<br>12 ：<b>名無し</b><dd>こんにちは&amp;世界<br></dl>"+
           "</body></html>";
        CheckerResult result=HissiAdapter.parse(data,"http://hissi.org/read.php/news4vip/example.html");
        assertEquals(1,result.posts.size());
        assertTrue(result.posts.get(0).body.contains("こんにちは&世界"));
    }
    @Test public void emptyEdgeHtmlMustNotInventPosts() {
        CheckerQuery q=CheckerQuery.parseUrl("https://www.kyodemo.net/sdemo/b/e_e_liveedge/?hi=Ar7vkh7JR&key=1792649855&date=20261022");
        assertEquals(0,EdgeAdapter.parse("<html><body>Nothing here</body></html>",q).posts.size());
    }
}
