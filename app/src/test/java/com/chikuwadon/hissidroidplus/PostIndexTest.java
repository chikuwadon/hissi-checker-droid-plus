package com.chikuwadon.hissidroidplus;
import org.junit.Test;
import static org.junit.Assert.*;
public final class PostIndexTest {
    @Test public void sameThreadDespiteDifferentResponseNumbers(){
        CheckerResult result=new CheckerResult();
        result.posts.add(new CheckerResult.Post("Part3","◆8LNH56sLs6 (ﾜｯﾁｮｲ eefa-LqYm)[sage] : 2026/10/22(木) 15:29 ID:ZeeBPzOf0","332","本文1","http://egg.5ch.io/test/read.cgi/android/1792500780/332"));
        result.posts.add(new CheckerResult.Post("Part3","◆8LNH56sLs6 (ﾜｯﾁｮｲ eefa-LqYm)[sage] : 2026/10/22(木) 15:32 ID:ZeeBPzOf0","334","本文2","http://egg.5ch.io/test/read.cgi/android/1792500780/334"));
        result.threads.put("http://egg.5ch.io/test/read.cgi/android/1792500780/50","Part3");
        assertEquals(1,PostIndex.uniqueThreads(result).size());
        PostIndex.collectNames(result);
        assertEquals(1,result.names.size());
        assertEquals("◆8LNH56sLs6",result.names.get(0));
    }
    @Test public void differentLinkVariantsDeduplicate(){
        CheckerResult result=new CheckerResult();
        result.posts.add(new CheckerResult.Post("T","名無し","10","A","https://egg.5ch.net/test/read.cgi/android/1792500780/10"));
        result.threads.put("http://egg.5ch.io/test/read.cgi/android/1792500780/l50", "T");
        result.threads.put("https://egg.5ch.io/test/read.cgi/android/1792500780/50", "T");
        assertEquals(1,PostIndex.uniqueThreads(result).size());
    }
    @Test public void separateThreadsAreSeparate(){
        CheckerResult result=new CheckerResult();
        result.threads.put("https://egg.5ch.net/test/read.cgi/android/1792500780/49","Part3");
        result.threads.put("https://egg.5ch.net/test/read.cgi/android/1792500790/40","Part4");
        assertEquals(2,PostIndex.uniqueThreads(result).size());
    }
    @Test public void preserveThreadLink(){
        assertEquals("http://egg.5ch.io/test/read.cgi/android/1792500780/",PostIndex.openableThreadUrl("http://egg.5ch.io/test/read.cgi/android/1792500780/334"));
    }
}
