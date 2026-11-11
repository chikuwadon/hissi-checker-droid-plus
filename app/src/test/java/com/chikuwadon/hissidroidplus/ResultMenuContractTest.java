package com.chikuwadon.hissidroidplus;

import static org.junit.Assert.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import org.junit.Test;

/** Preserve result actions, text selection and menu ordering across Kotlin migration. */
public final class ResultMenuContractTest {
    private static String activity() throws Exception {
        return new String(Files.readAllBytes(Paths.get(
            "src/main/java/com/chikuwadon/hissidroidplus/ResultActivity.kt")),
            StandardCharsets.UTF_8);
    }

    @Test public void selectionIsEnabledBeforeTheLongPressGesture() throws Exception {
        String code=activity();
        String layout=new String(Files.readAllBytes(Paths.get(
            "src/main/res/layout/item_row.xml")),StandardCharsets.UTF_8);
        // Native selectable TextViews receive the gesture directly.
        assertFalse(code.contains("setOnItemLongClickListener"));
        assertFalse(code.contains("performLongClick"));
        assertFalse(code.contains("beginTextSelection"));
        for(String id:new String[]{"item_title","item_subtitle","item_body"}){
            assertTrue(layout.contains("android:id=\"@+id/"+id+"\" android:textIsSelectable=\"true\""));
        }
        assertEquals(3,layout.split(java.util.regex.Pattern.quote(
            "<com.chikuwadon.hissidroidplus.SelectableResultTextView"),-1).length-1);
        assertTrue(code.contains(
            "list.setOnItemClickListener { _, _, index, _ -> showEntry(index) }"));
        assertTrue(code.contains("val showActions = View.OnClickListener { showEntry(index) }"));
        assertTrue(code.contains("title.setOnClickListener(showActions)"));
        assertTrue(code.contains("subtitle.setOnClickListener(showActions)"));
        assertTrue(code.contains("body.setOnClickListener(showActions)"));
    }

    @Test public void longPressHasComfortableDelayAndTapHandling() throws Exception {
        assertEquals(700,SelectableResultTextView.selectionDelayMs(500));
        assertEquals(950,SelectableResultTextView.selectionDelayMs(800));
        String code=new String(Files.readAllBytes(Paths.get(
                "src/main/java/com/chikuwadon/hissidroidplus/SelectableResultTextView.kt")),
                StandardCharsets.UTF_8);
        assertTrue(code.contains("MotionEvent.obtain(event)"));
        assertTrue(code.contains("cancel.action = MotionEvent.ACTION_CANCEL"));
        assertTrue(code.contains("performClick()"));
        assertTrue(code.contains("MotionEvent.ACTION_CANCEL"));
        assertTrue(code.contains("touchSlop"));
        assertTrue(code.contains("clearPending()"));
    }

    @Test public void selectionSurvivesRecycledRowsAndUnchangedData() throws Exception {
        String code=activity();
        String selectable=new String(Files.readAllBytes(Paths.get(
                "src/main/java/com/chikuwadon/hissidroidplus/SelectableResultTextView.kt")),
                StandardCharsets.UTF_8);
        assertTrue(code.contains("view.tag = identity"));
        assertTrue(code.contains("!TextUtils.equals(target.text, content)"));
        assertTrue(code.contains("refreshSelectionAfterRebind()"));
        assertTrue(selectable.contains("onAttachedToWindow()"));
        assertTrue(selectable.contains("onDetachedFromWindow()"));
        assertTrue(selectable.contains("generation != selectionRefreshGeneration"));
        assertTrue(selectable.contains("!isAttachedToWindow || touchActive"));
        assertTrue(selectable.contains("setTextIsSelectable(false)"));
        assertTrue(selectable.contains("setTextIsSelectable(true)"));
    }

    @Test public void homePrecedesHistoryForBothSites() throws Exception {
        String code=activity();
        String adjacent="\"ホーム画面に戻る\", \"検索履歴\"";
        assertEquals(2,code.split(java.util.regex.Pattern.quote(adjacent),-1).length-1);
        assertTrue(code.contains("which == 0 -> openHome()"));
        assertTrue(code.contains(
            "which == 1 -> startActivity(Intent(this, HistoryActivity::class.java))"));
        assertTrue(code.contains("edge && which == 2 -> openEdgeAnalysis()"));
        assertTrue(code.contains("when (which - if (edge) 3 else 2)"));
        assertTrue(code.contains("1 -> copyResultUrl()"));
        assertTrue(code.contains("2 -> copySearchedId()"));
        assertTrue(code.contains("3 -> copyWrittenThreads()"));
        assertTrue(code.contains("4 -> copyAllVisible()"));
        String order="\"URLをコピー\", \"IDをコピー\",";
        assertEquals(2,code.split(java.util.regex.Pattern.quote(order),-1).length-1);
        assertTrue(code.contains(
            "\"書き込みスレをコピー\", \"表示中のレスをすべてコピー\""));
        assertTrue(code.contains("Intent.FLAG_ACTIVITY_NEW_TASK or"));
        assertTrue(code.contains(
            "Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP"));
    }
}
