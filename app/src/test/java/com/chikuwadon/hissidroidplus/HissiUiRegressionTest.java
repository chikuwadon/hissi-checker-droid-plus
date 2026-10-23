package com.chikuwadon.hissidroidplus;
import static org.junit.Assert.*;
import org.junit.Test;

public final class HissiUiRegressionTest {
    @Test public void multipleDtDdPairsProduceTwoPosts(){
        String html="<h3>書き込みレス一覧</h3><dl>"+
          "<dt><a href='https://egg.5ch.net/test/read.cgi/android/1792500780/31'>開発スレ</a><br>"+
          "31 ：<b>名無しさん</b>[sage]：2026/10/22(木) 11:22:33 ID:Abcd0123</dt><dd>本文A</dd>"+
          "<dt><a href='https://egg.5ch.net/test/read.cgi/android/1792500780/35'>開発スレ</a><br>"+
          "35 ：<b>名無しさん</b>[sage]：2026/10/22(木) 11:28:33 ID:Abcd0123</dt><dd>本文B</dd>"+
          "</dl>";
        CheckerResult data=HissiAdapter.parse(html,"https://hissi.org/read.php/android/20261022/QUJDRA.html");
        assertEquals(2,data.posts.size());
        assertEquals(2,PostIndex.threadPostCounts(data).values().iterator().next().intValue());
        assertEquals(2,PostIndex.namePostCounts(data).get("名無しさん").intValue());
    }
    @Test public void responseCountsAreNotCopied(){
        CheckerResult data=new CheckerResult();
        data.posts.add(new CheckerResult.Post("開発スレ","名無しさん","31 名無しさん 2026/10/22(木) ID:Abcd0123","本文A","http://egg.5ch.io/test/read.cgi/android/1792500780/31"));
        data.posts.add(new CheckerResult.Post("開発スレ","名無しさん","35 名無しさん 2026/10/22(木) ID:Abcd0123","本文B","https://egg.5ch.net/test/read.cgi/android/1792500780/35"));
        assertEquals(2,PostIndex.threadPostCounts(data).values().iterator().next().intValue());
        assertEquals(2,PostIndex.namePostCounts(data).get("名無しさん").intValue());
        assertFalse(PostFormatter.withThread(data.posts.get(0)).contains(" (2)"));
        assertTrue(PostIndex.uniqueThreads(data).containsValue("開発スレ"));
    }
}
