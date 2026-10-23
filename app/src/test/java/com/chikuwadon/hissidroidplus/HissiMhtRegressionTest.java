package com.chikuwadon.hissidroidplus;
import static org.junit.Assert.*;
import org.junit.Test;

/** The fixture mimics the real 2026-10-23 MHT structure but is anonymized. */
public final class HissiMhtRegressionTest {
    @Test public void brokenSeparatorStillParsesAllSevenPosts(){
        int[] numbers={421,426,431,432,434,435,439};
        StringBuilder page=new StringBuilder("<html><body><h4>������</h4>");
        for(int n:numbers){
            page.append("<dl style='user-select: text'><dt style='user-select: text'>")
                .append("<a target='_blank' href='http://egg.5ch.io/test/read.cgi/android/1792500780/")
                .append(n).append("'>Example thread</a><br>")
                .append(n).append(" �F<b></b>◆Example <b> </b>(ﾜｯﾁｮｲ 1234-test)")
                .append("[sage]�F2026/10/23(金) 12:00:00 ID:Abcd1234")
                .append("</dt><dd>本文").append(n).append("<br/>続き</dd></dl>");
        }
        page.append("</body></html>");
        CheckerResult result=HissiAdapter.parse(page.toString(),
                "https://hissi.org/read.php/android/20261023/QUJDRA.html");
        assertEquals(7,result.posts.size());
        assertEquals(421,Integer.parseInt(result.posts.get(0).link.replaceAll("^.*/","")));
        assertEquals(439,Integer.parseInt(result.posts.get(6).link.replaceAll("^.*/","")));
        assertTrue(result.posts.get(0).body.contains("本文421"));
        assertTrue(PostFormatter.header(result.posts.get(0)).startsWith("421 "));
        assertEquals(1,PostIndex.uniqueThreads(result).size());
        assertEquals(7,PostIndex.threadPostCounts(result).values().iterator().next().intValue());
        assertEquals(7,PostIndex.namePostCounts(result).get("◆Example").intValue());
    }
    @Test public void unrelatedDefinitionIsIgnored(){
        String html="<dl><dt>説明</dt><dd>本文でない</dd></dl>"+
            "<dl><dt><a href='http://egg.5ch.io/test/read.cgi/android/1792500780/421'>T</a>"+
            "<br>421 ：<b>名無しさん</b>[sage] 2026/10/23(金) ID:AAA</dt><dd>正常な本文</dd></dl>";
        CheckerResult result=HissiAdapter.parse(html,"https://hissi.org/");
        assertEquals(1,result.posts.size());
        assertEquals("名無しさん",result.posts.get(0).author);
    }
}
