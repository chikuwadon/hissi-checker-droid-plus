package com.chikuwadon.hissidroidplus;
import static org.junit.Assert.*;
import org.junit.Test;

public final class EdgeAnalysisTest {
    @Test public void siteProvidedAnalysisIsGroupedButNotSynthesized(){
        String html="<div id='wt'>書き込み分析</div>"+
            "<div id='toggle-w-tawindow'><div class='box d-box'>"+
            "<div class='title'>投稿診断</div><div>文章力:<b>B</b> / 語彙力:<b>C</b></div><br>"+
            "<div class='title'>投稿分類</div><div>感想:<b>52%</b></div>"+
            "<div class='title'>反応分類</div><div>好意的:<b>14%</b></div>"+
            "<div class='title'>関心分野</div><div>掲示板</div>"+
            "</div></div><a class='button' disabled href='#'>分析</a>";
        EdgeAnalysis parsed=EdgeAnalysis.parse(html);
        assertTrue(parsed.hasResult());
        assertEquals(4,parsed.sections.size());
        assertEquals("投稿診断",parsed.sections.get(0).title);
        assertTrue(parsed.sections.get(0).body.contains("語彙力:"));
        assertEquals("反応分類",parsed.sections.get(2).title);
        assertFalse(parsed.startAvailable);
    }
    @Test public void incompletePageDoesNotMakeUpAnAnalysis(){
        EdgeAnalysis parsed=EdgeAnalysis.parse("<a class='button' href='#'>分析</a>");
        assertFalse(parsed.hasResult());
        assertTrue(parsed.startAvailable);
        EdgeAnalysis denied=EdgeAnalysis.parse("<a class='button' disabled href='#'>分析</a>");
        assertFalse(denied.hasResult());
        assertFalse(denied.startAvailable);
    }
}
