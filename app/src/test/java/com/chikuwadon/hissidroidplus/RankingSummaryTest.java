package com.chikuwadon.hissidroidplus;
import org.junit.Test;
import static org.junit.Assert.*;
public final class RankingSummaryTest {
    @Test public void parsesRankAndHourlyCountsFromSummary(){
        StringBuilder html=new StringBuilder("<h3>書き込み順位＆時間帯一覧</h3>8 位/6274 ID中 時間 ");
        for(int i=0;i<24;i++)html.append(i).append(' ');
        html.append("Total 書き込み数 ");
        for(int i=0;i<24;i++)html.append(i==12?7:0).append(' ');
        html.append("7 使用した名前一覧");
        CheckerResult result=HissiAdapter.parse(html.toString(),"https://hissi.org/read.php/news/20260614/test.html");
        assertEquals("8位 / 6274 ID中",result.rank);
        assertNotNull(result.hourlyCounts);
        assertEquals(7,result.hourlyCounts[12]);
        assertEquals(0,result.hourlyCounts[0]);
    }
}
