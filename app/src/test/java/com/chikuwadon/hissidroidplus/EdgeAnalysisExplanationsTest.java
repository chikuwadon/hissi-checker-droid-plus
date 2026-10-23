package com.chikuwadon.hissidroidplus;
import org.junit.Test;
import static org.junit.Assert.*;
public final class EdgeAnalysisExplanationsTest {
    @Test public void attachesReasonToItsOwnMetricInsteadOfOneLargeParagraph(){
        String html="<div id='toggle-w-tawindow'><div class='box d-box'>"+
          "<div class='title'>投稿診断</div><div>文章力:<b>C</b> / 語彙力:<b>B</b>"+
          "<details class='rating-reason'><summary>ⓘ</summary><span class='details-c'>"+
          "<span class='bx-label'>文章力</span><br><span>文章力の説明。</span><br>"+
          "<span class='bx-label'>語彙力</span><br><span>語彙力の説明。</span>"+
          "</span></details></div></div></div>";
        EdgeAnalysis result=EdgeAnalysis.parse(html);
        assertEquals(1,result.sections.size());
        EdgeAnalysis.Section section=result.sections.get(0);
        assertEquals("文章力の説明。",section.explanations.get("文章力"));
        assertEquals("語彙力の説明。",section.explanations.get("語彙力"));
        assertFalse(section.notes.contains("文章力の説明。"));
    }
}
