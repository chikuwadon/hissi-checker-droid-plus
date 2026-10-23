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
    @Test public void prefixedIdIsCopied(){assertEquals("ID:2BwK4lBW0",PostFormatter.id(post));}
    @Test public void edgeReferenceCountIsDisplayOnly(){
        CheckerResult.Post edge=new CheckerResult.Post("スレッドA","エッヂの名無し",
                "506(1):エッヂの名無し 2026/10/23(金) 08:38:49.033 ID:3.43FEyAx 506(1):エッヂの名無し",
                ">>495\n本文","https://www.kyodemo.net/sdemo/r/e_e_liveedge/1790000000/506",1);
        String copy=PostFormatter.full(edge);
        assertEquals("506 エッヂの名無し 2026/10/23(金) 08:38:49.033 ID:3.43FEyAx\n>>495\n本文",copy);
        assertFalse(copy.contains("(1)"));
        assertEquals("ID:3.43FEyAx",PostFormatter.id(edge));
    }
    @Test public void edgeSageEmailIsPreserved(){
        CheckerResult.Post edge=new CheckerResult.Post("スレ","エッヂの名無し",
            "331:エッヂの名無し [sage] 2026/10/23(金) 10:42:53.468 ID:3.43FEyAx",
            "本文","https://www.kyodemo.net/sdemo/r/e_e_liveedge/1790000000/331",0);
        assertEquals("331 エッヂの名無し sage 2026/10/23(金) 10:42:53.468 ID:3.43FEyAx\n本文",
                PostFormatter.full(edge));
    }

}
