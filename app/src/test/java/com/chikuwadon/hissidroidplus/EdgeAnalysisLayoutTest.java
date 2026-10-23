package com.chikuwadon.hissidroidplus;
import static org.junit.Assert.*;
import org.junit.Test;

public final class EdgeAnalysisLayoutTest {
    @Test public void detailsAreSeparatedAndAllSectionsPreserved(){
        String html="<div id='toggle-w-tawindow'><div class='box d-box'>"+
            "<div class='title'>投稿診断</div><div><span>文章力:<b>B</b></span> / "+
            "<span>語彙力:<b>C</b></span><details class='rating-reason'><summary>ⓘ</summary>"+
            "<span class='details-c'>文章力：根拠の説明</span></details></div>"+
            "<div class='title'>投稿分類</div><div>感想:42% / 批判:11%</div>"+
            "<h3>将来追加された項目</h3><div>未知の形式でも全文を表示</div>"+
            "</div></div>";
        EdgeAnalysis x=EdgeAnalysis.parse(html);
        assertEquals(3,x.sections.size());
        assertEquals("投稿診断",x.sections.get(0).title);
        assertTrue(x.sections.get(0).body.contains("語彙力"));
        assertFalse(x.sections.get(0).body.contains("根拠の説明"));
        assertTrue(x.sections.get(0).notes.contains("根拠の説明"));
        assertTrue(x.sections.get(2).body.contains("全文を表示"));
    }
}
