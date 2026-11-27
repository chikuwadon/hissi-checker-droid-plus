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
            "list.setOnItemClickListener { _, _, index, _ -> showEntryAfterRipple(index) }"));
        assertTrue(code.contains("val showActions = View.OnClickListener { showEntryAfterRipple(index) }"));
        assertTrue(code.contains("title.setOnClickListener(showActions)"));
        assertTrue(code.contains("subtitle.setOnClickListener(showActions)"));
        assertTrue(code.contains("body.setOnClickListener(showActions)"));
        assertTrue(code.contains("list.postOnAnimation {"));
        assertTrue(code.contains("if (!isFinishing && !isDestroyed && hasWindowFocus() && tappedTab == tab) showEntry(index)"));
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

    @Test public void resultMenuHasOnlyResultActionsInBothSites() throws Exception {
        assertEquals(java.util.Arrays.asList(ResultMenuAction.ANALYSIS, ResultMenuAction.BROWSER,
            ResultMenuAction.COPY_URL, ResultMenuAction.COPY_ID, ResultMenuAction.COPY_THREADS,
            ResultMenuAction.COPY_POSTS), ResultMenuAction.forSite(true));
        assertEquals(java.util.Arrays.asList(ResultMenuAction.BROWSER, ResultMenuAction.COPY_URL,
            ResultMenuAction.COPY_ID, ResultMenuAction.COPY_THREADS, ResultMenuAction.COPY_POSTS),
            ResultMenuAction.forSite(false));
        for (ResultMenuAction action : ResultMenuAction.values()) {
            assertFalse(java.util.Arrays.asList("ホーム画面に戻る", "検索履歴", "タブ切替アニメーション", "閉じる")
                .contains(action.getLabel()));
        }
        String code = activity();
        assertTrue(code.contains("ResultMenuAction.forSite(edge)"));
        assertTrue(code.contains("ThemedMenu.showOverflow(this, anchor, actions.map"));
        assertFalse(code.contains("ThemedMenu.showOverflowToggle(this, anchor"));
        assertTrue(code.contains("ResultMenuAction.ANALYSIS -> openEdgeAnalysis()"));
        assertTrue(code.contains("ResultMenuAction.BROWSER -> openExternalBrowser(resultUrl())"));
        assertTrue(code.contains("ResultMenuAction.COPY_URL -> copyResultUrl()"));
        assertTrue(code.contains("ResultMenuAction.COPY_ID -> copySearchedId()"));
        assertTrue(code.contains("ResultMenuAction.COPY_THREADS -> copyWrittenThreads()"));
        assertTrue(code.contains("ResultMenuAction.COPY_POSTS -> copyAllVisible()"));
        assertTrue(code.contains("Intent.FLAG_ACTIVITY_NEW_TASK or"));
        assertTrue(code.contains("MainNavigation.navigate(this, destination)"));
    }
}
