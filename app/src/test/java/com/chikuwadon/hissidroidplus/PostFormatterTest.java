package com.chikuwadon.hissidroidplus;
import org.junit.Test;
import static org.junit.Assert.*;
public class PostFormatterTest {
    private final CheckerResult.Post post = new CheckerResult.Post(
        "【専ブラ】5ちゃんねるブラウザ「ChMate」part269",
        "名無しさん＠お腹いっぱい。 (ﾜｯﾁｮｲ be0e-SUQD)[] : 2026/10/22(木) 18:05:17.70 ID:2BwK4lBW0",
        "991", "ゼロからと言うのが流行ってるだけで1からが正しいしわかりやすい",
        "http://egg.5ch.io/test/read.cgi/android/1784035194/991");

    @Test public void fullCopyResponseNumberFirstNoThread(){
        String value=PostFormatter.full(post);
        assertEquals("991 名無しさん＠お腹いっぱい。 (ﾜｯﾁｮｲ be0e-SUQD) 2026/10/22(木) 18:05:17.70 ID:2BwK4lBW0\n"
                +"ゼロからと言うのが流行ってるだけで1からが正しいしわかりやすい",value);
    }
    @Test public void titleAndUrlGoBeforeResponse(){
        String value=PostFormatter.withThread(post);
        assertTrue(value.startsWith(post.title+"\n"+post.link+"\n991 名無しさん＠お腹いっぱい。"));
        assertTrue(value.endsWith(post.body));
    }
    @Test public void appendingKeepsExistingTextWithoutDuplicates(){
        assertEquals("old\n\n"+PostFormatter.full(post),PostFormatter.append("old",PostFormatter.full(post)));
        assertEquals(PostFormatter.withThread(post),PostFormatter.append("",PostFormatter.withThread(post)));
    }
    @Test public void mailBracketsAndColonAreRemoved(){
        assertEquals("名無し sage 2026/10/22",PostFormatter.normalizedInfo("名無し [sage] ：2026/10/22"));
        assertEquals("名無し 2026/10/22",PostFormatter.normalizedInfo("名無し [] ：2026/10/22"));
        assertEquals("名無し 2026/10/22",PostFormatter.normalizedInfo("名無し  ：2026/10/22"));
    }
    @Test public void rawIdIsCopied(){assertEquals("2BwK4lBW0",PostFormatter.id(post));}
}
