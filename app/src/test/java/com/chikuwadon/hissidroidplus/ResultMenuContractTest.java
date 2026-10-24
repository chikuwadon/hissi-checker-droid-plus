package com.chikuwadon.hissidroidplus;

import static org.junit.Assert.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import org.junit.Test;

/** Keep the entry visible for both 5ch and Edge without changing menu offsets. */
public final class ResultMenuContractTest {
    @Test public void selectionIsEnabledBeforeTheLongPressGesture() throws Exception {
        String code=new String(Files.readAllBytes(Paths.get(
                "src/main/java/com/chikuwadon/hissidroidplus/ResultActivity.java")),
                StandardCharsets.UTF_8);
        String layout=new String(Files.readAllBytes(Paths.get(
                "src/main/res/layout/item_row.xml")),StandardCharsets.UTF_8);
        // Native TextViews must receive the original gesture, rather than
        // a synthetic second long-click after ListView has consumed it.
        assertFalse(code.contains("setOnItemLongClickListener"));
        assertFalse(code.contains("performLongClick"));
        assertFalse(code.contains("beginTextSelection"));
        for(String id:new String[]{"item_title","item_subtitle","item_body"}){
            assertTrue(layout.contains("android:id=\"@+id/"+id+"\" android:textIsSelectable=\"true\""));
        }
        assertEquals(3,layout.split(java.util.regex.Pattern.quote(
                "<com.chikuwadon.hissidroidplus.SelectableResultTextView"),-1).length-1);
        // Selectable text consumes short taps, so forward taps to the
        // existing action menu instead of changing its behavior.
        assertTrue(code.contains("list.setOnItemClickListener((parent,v,index,id)->showEntry(index))"));
        assertTrue(code.contains("View.OnClickListener showActions=clicked->showEntry(index)"));
        assertTrue(code.contains("title.setOnClickListener(showActions)"));
        assertTrue(code.contains("subtitle.setOnClickListener(showActions)"));
        assertTrue(code.contains("body.setOnClickListener(showActions)"));
    }

    @Test public void longPressHasComfortableDelayAndTapHandling() throws Exception {
        assertEquals(700,SelectableResultTextView.selectionDelayMs(500));
        assertEquals(950,SelectableResultTextView.selectionDelayMs(800));
        String code=new String(Files.readAllBytes(Paths.get(
                "src/main/java/com/chikuwadon/hissidroidplus/SelectableResultTextView.java")),
                StandardCharsets.UTF_8);
        assertTrue(code.contains("MotionEvent.obtain(event)"));
        assertTrue(code.contains("cancel.setAction(MotionEvent.ACTION_CANCEL)"));
        assertTrue(code.contains("performClick()"));
        assertTrue(code.contains("MotionEvent.ACTION_CANCEL"));
        assertTrue(code.contains("touchSlop"));
        assertTrue(code.contains("clearPending()"));
    }

    @Test public void selectionSurvivesRecycledRowsAndUnchangedData() throws Exception {
        String activity=new String(Files.readAllBytes(Paths.get(
                "src/main/java/com/chikuwadon/hissidroidplus/ResultActivity.java")),
                StandardCharsets.UTF_8);
        String selectable=new String(Files.readAllBytes(Paths.get(
                "src/main/java/com/chikuwadon/hissidroidplus/SelectableResultTextView.java")),
                StandardCharsets.UTF_8);
        assertTrue(activity.contains("view.setTag(identity)"));
        assertTrue(activity.contains("android.text.TextUtils.equals(target.getText(),content)"));
        assertTrue(activity.contains("refreshSelectionAfterRebind()"));
        assertTrue(selectable.contains("onAttachedToWindow()"));
        assertTrue(selectable.contains("onDetachedFromWindow()"));
        assertTrue(selectable.contains("generation!=selectionRefreshGeneration"));
        assertTrue(selectable.contains("!isAttachedToWindow() || touchActive"));
        assertTrue(selectable.contains("setTextIsSelectable(false)"));
        assertTrue(selectable.contains("setTextIsSelectable(true)"));
    }

    @Test public void homeImmediatelyFollowsHistoryForBothSites() throws Exception {
        String code=new String(Files.readAllBytes(Paths.get(
                "src/main/java/com/chikuwadon/hissidroidplus/ResultActivity.java")),
                StandardCharsets.UTF_8);
        String adjacent="\"検索履歴\",\"ホーム画面に戻る\"";
        assertEquals(2,code.split(java.util.regex.Pattern.quote(adjacent),-1).length-1);
        assertTrue(code.contains("else if(which==1)openHome();"));
        assertTrue(code.contains("if(edge&&which==2)openEdgeAnalysis();"));
        assertTrue(code.contains("else if(action==2)openExternalBrowser("));
        assertTrue(code.contains("Intent.FLAG_ACTIVITY_NEW_TASK|"));
        assertTrue(code.contains("Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP"));
    }
}
