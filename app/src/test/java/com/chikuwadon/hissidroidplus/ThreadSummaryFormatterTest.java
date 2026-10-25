package com.chikuwadon.hissidroidplus;

import org.junit.Test;
import static org.junit.Assert.*;

public final class ThreadSummaryFormatterTest {
    private static final String EDGE_URL=
        "https://www.kyodemo.net/sdemo/b/e_e_liveedge/?bs=hi&k=id%3ATxxLmfrCk";

    @Test public void preservesOfficialKyodemoCopyFormat(){
        CheckerQuery q=CheckerQuery.parseUrl(EDGE_URL);
        CheckerResult result=new CheckerResult();
        String source="日付: 2026/10/25\nレス数: 48\n被参照数: 14\n"+
            "スレッド数: 13 (10スレ作成)\n"+
            "ID:TxxLmfrCk の書き込みスレッド一覧：\n"+
            "空飛べるゲームて面白いのに少ないよな (3)\n"+EDGE_URL;
        result.sourceThreadSummary=source;
        assertEquals(source,ThreadSummaryFormatter.format(q,result));
    }

    @Test public void createsFiveChThreadSummaryFromAvailableData(){
        CheckerQuery q=CheckerQuery.hissi("android","20261025","qNScHaYr0");
        CheckerResult result=new CheckerResult();
        result.reportedPostCount=2;
        result.posts.add(new CheckerResult.Post("スレッド甲","名無し",
                "1 名無し 2026/10/25(日) 10:00:00 ID:qNScHaYr0",
                "テスト","https://egg.5ch.net/test/read.cgi/android/1761342000/1"));
        result.posts.add(new CheckerResult.Post("スレッド甲","名無し",
                "2 名無し 2026/10/25(日) 10:01:00 ID:qNScHaYr0",
                "テスト２","https://egg.5ch.net/test/read.cgi/android/1761342000/2"));
        String text=ThreadSummaryFormatter.format(q,result);
        assertTrue(text.startsWith("日付: 2026/10/25\nレス数: 2\nスレッド数: 1\n"));
        assertTrue(text.contains("ID:qNScHaYr0 の書き込みスレッド一覧："));
        assertTrue(text.contains("スレッド甲 (2)"));
        assertTrue(text.contains("http://hissi.org/read.php/android/20261025/"));
        assertFalse(text.contains("被参照数: 0"));
        assertFalse(text.contains("10スレ作成"));
    }

    @Test public void neverFabricatesThreadNamesOrCounts(){
        CheckerResult empty=new CheckerResult();
        CheckerQuery q=CheckerQuery.edgeSearch("liveedge","20261025","ABCDEF");
        assertEquals("",ThreadSummaryFormatter.format(q,empty));
    }

    @Test public void readsSiteSummaryAndPostsTogether(){
        CheckerQuery q=CheckerQuery.parseUrl(EDGE_URL);
        String summary="日付: 2026/10/25\nレス数: 48\n被参照数: 14\n"+
            "スレッド数: 13 (10スレ作成)\nID:TxxLmfrCk の書き込みスレッド一覧：\n"+
            "あるスレ (3)\n"+EDGE_URL;
        String html="<textarea id='copy-mess'>"+summary+"</textarea>"+
            "<div class='box content'><h2><a href='https://www.kyodemo.net/sdemo/r/e_e_liveedge/1761342000/'>あるスレ</a></h2>"+
            "<div class='post'><div class='r-head'><a href='https://www.kyodemo.net/sdemo/r/e_e_liveedge/1761342000/1'>1</a>"+
            "エッヂの名無しさん 2026/10/25(日) 10:01:02.000 ID:TxxLmfrCk</div>"+
            "本文</div></div>";
        CheckerResult result=EdgeAdapter.parse(html,q);
        assertEquals(1,result.posts.size());
        assertEquals(48,result.reportedPostCount);
        assertEquals(14,result.referencedCount);
        assertEquals(13,result.reportedThreadCount);
        assertEquals(summary,ThreadSummaryFormatter.format(q,result));
    }
}
