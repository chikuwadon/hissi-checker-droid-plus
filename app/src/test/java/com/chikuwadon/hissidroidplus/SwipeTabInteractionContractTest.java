package com.chikuwadon.hissidroidplus;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import org.junit.Test;
import static org.junit.Assert.*;

/** Guard the source-level touch path across selectable rows and the list. */
public final class SwipeTabInteractionContractTest {
    private static String source(String path) throws Exception {
        return new String(Files.readAllBytes(Paths.get("src/main/" + path)),
            StandardCharsets.UTF_8);
    }

    @Test public void parentReceivesHorizontalGesturesEvenWhenChildrenConsumeTouch() throws Exception {
        String layout = source("res/layout/activity_results.xml");
        String container = source("java/com/chikuwadon/hissidroidplus/SwipeTabContainer.kt");
        String activity = source("java/com/chikuwadon/hissidroidplus/ResultActivity.kt");
        assertTrue(layout.contains("<com.chikuwadon.hissidroidplus.SwipeTabContainer"));
        assertTrue(layout.contains("android:id=\"@+id/swipe_container\""));
        assertTrue(layout.contains("android:id=\"@+id/items\""));
        assertTrue(container.contains("override fun dispatchTouchEvent(event: MotionEvent)"));
        assertTrue(container.contains("super.dispatchTouchEvent(cancel)"));
        assertTrue(container.contains("callback?.onDrag(dx)"));
        assertTrue(container.contains("callback?.onRelease("));
        assertTrue(container.contains("event.eventTime - downTime >= longPressMs"));
        assertTrue(activity.contains("swipeContainer.callback = object : SwipeTabContainer.Callback"));
        assertFalse(activity.contains("list.setOnTouchListener("));
    }

    @Test public void slideTransitionsHaveTwoPagesAndCleanBitmap() throws Exception {
        String code = source("java/com/chikuwadon/hissidroidplus/ResultActivity.kt");
        assertTrue(code.contains("Bitmap.createBitmap(list.width, list.height"));
        assertTrue(code.contains("swipeContainer.addView(image"));
        assertTrue(code.contains("previewOverlay?.translationX = distance"));
        assertTrue(code.contains("finishTabPreview(target == previewTo)"));
        assertTrue(code.contains("previewBitmap?.recycle()"));
        assertTrue(code.contains("if (AppSettings.tabAnimations(this) && beginTabPreview(index))"));
    }

    @Test public void animationSettingFitsOnOneLine() throws Exception {
        String menu = source("java/com/chikuwadon/hissidroidplus/ThemedMenu.kt");
        String home = source("java/com/chikuwadon/hissidroidplus/HomeActivity.kt");
        assertTrue(home.contains("\"タブ切替アニメーション\""));
        assertTrue(menu.contains("val toggleTextWidth = items.getOrNull(toggleIndex)"));
        assertTrue(menu.contains("val checkableWidth = ceil(toggleTextWidth.toDouble()).toInt() + dp(a, 70)"));
        assertTrue(menu.contains("val availableWidth = a.resources.displayMetrics.widthPixels - dp(a, 24)"));
        assertTrue(menu.contains("isSingleLine = true"));
        assertTrue(menu.contains("ellipsize = android.text.TextUtils.TruncateAt.END"));
        assertTrue(menu.contains("row.addView(title, LinearLayout.LayoutParams(-2, -2))"));
        assertTrue(menu.contains("row.addView(View(a), LinearLayout.LayoutParams(dp(a, 4), 1))"));
        assertTrue(menu.contains("row.addView(checkbox, LinearLayout.LayoutParams(dp(a, 38), dp(a, 48)))"));
        assertTrue(menu.contains("val width = min(availableWidth, max(dp(a, 170)"));
    }

    @Test public void allMenusHaveExpandingRippleFeedback() throws Exception {
        String menu = source("java/com/chikuwadon/hissidroidplus/ThemedMenu.kt");
        String feedback = source("java/com/chikuwadon/hissidroidplus/TouchFeedback.kt");
        assertTrue(feedback.contains("RippleDrawable("));
        assertTrue(feedback.contains("cornerRadius = dp(context, cornerDp)"));
        assertTrue(feedback.contains("view.foreground = ripple(view.context, cornerDp)"));
        assertTrue(menu.contains("private fun applyMenuTouch(a: Activity, view: View)"));
        assertTrue(menu.contains("TouchFeedback.foreground(view)"));
        assertFalse(menu.contains("menuRowBackground("));
        assertFalse(menu.contains("StateListDrawable"));
        assertFalse(menu.contains("row.isActivated = enabled"));
        assertTrue(menu.contains("row.postDelayed({"));
        assertTrue(menu.contains("}, 120L)"));
    }

    @Test public void allMainNavigationAndHistoryActionsUseRipple() throws Exception {
        String home = source("java/com/chikuwadon/hissidroidplus/HomeActivity.kt");
        String result = source("java/com/chikuwadon/hissidroidplus/ResultActivity.kt");
        String history = source("java/com/chikuwadon/hissidroidplus/HistoryActivity.kt");
        assertTrue(home.contains("TouchFeedback.foreground(findViewById(R.id.home_more)"));
        assertTrue(home.contains("selector = TouchFeedback.listSelector(this@HomeActivity)"));
        assertTrue(home.contains("TouchFeedback.foreground(close"));
        assertTrue(result.contains("R.id.tab_posts, R.id.tab_threads, R.id.tab_names"));
        assertTrue(result.contains("R.id.toolbar_back, R.id.toolbar_more"));
        assertTrue(result.contains("TouchFeedback.pulse(it)"));
        assertTrue(result.contains("showEntryAfterRipple(index)"));
        assertFalse(result.contains("TouchFeedback.foreground(body, 4)"));
        assertTrue(history.contains("TouchFeedback.foreground(recentTab)"));
        assertTrue(history.contains("TouchFeedback.foreground(favoriteTab)"));
        assertTrue(history.contains("TouchFeedback.foreground(deleteSelected)"));
        assertTrue(history.contains("TouchFeedback.listSelector(this@HistoryActivity)"));
    }

    @Test public void selectableResultRowsTriggerVisibleTouchRipple() throws Exception {
        String layout = source("res/layout/item_row.xml");
        String row = source("java/com/chikuwadon/hissidroidplus/RippleResultRow.kt");
        String result = source("java/com/chikuwadon/hissidroidplus/ResultActivity.kt");
        assertTrue(layout.contains("<com.chikuwadon.hissidroidplus.RippleResultRow"));
        assertTrue(layout.contains("</com.chikuwadon.hissidroidplus.RippleResultRow>"));
        assertTrue(row.contains("override fun dispatchTouchEvent(event: MotionEvent)"));
        assertTrue(row.contains("(foreground as? RippleDrawable)?.setHotspot(event.x, event.y)"));
        assertTrue(row.contains("val handled = super.dispatchTouchEvent(event)"));
        assertTrue(row.contains("postDelayed(clearPress, 160L)"));
        assertTrue(row.contains("isPressed = false"));
        assertTrue(result.contains("list.postDelayed({"));
        assertTrue(result.contains("}, 150L)"));
        assertFalse(result.contains("TouchFeedback.foreground(body, 4)"));
    }

    @Test public void tabTapRippleIsVisibleAfterSelection() throws Exception {
        String feedback = source("java/com/chikuwadon/hissidroidplus/TouchFeedback.kt");
        String result = source("java/com/chikuwadon/hissidroidplus/ResultActivity.kt");
        assertTrue(feedback.contains("fun pulse(view: View)"));
        assertTrue(feedback.contains("ripple.state = intArrayOf(android.R.attr.state_enabled, android.R.attr.state_pressed)"));
        assertTrue(feedback.contains("}, 170L)"));
        assertEquals(3, result.split("TouchFeedback.pulse\\(it\\)", -1).length - 1);
    }

    @Test public void dialogCloseTouchAreaIsOnlyTheButton() throws Exception {
        String menu = source("java/com/chikuwadon/hissidroidplus/ThemedMenu.kt");
        assertTrue(menu.contains("val actions = LinearLayout(a).apply"));
        assertTrue(menu.contains("minimumWidth = dp(a, 80)"));
        assertTrue(menu.contains("actions.addView(close, LinearLayout.LayoutParams("));
        assertTrue(menu.contains("ViewGroup.LayoutParams.WRAP_CONTENT, dp(a, 46)"));
        assertTrue(menu.contains("panel.addView(actions, LinearLayout.LayoutParams("));
        assertFalse(menu.contains("panel.addView(close)"));
    }

    @Test public void overflowCheckboxDoesNotDismissWhenToggled() throws Exception {
        String menu = source("java/com/chikuwadon/hissidroidplus/ThemedMenu.kt");
        String home = source("java/com/chikuwadon/hissidroidplus/HomeActivity.kt");
        String result = source("java/com/chikuwadon/hissidroidplus/ResultActivity.kt");
        assertTrue(menu.contains("fun showOverflowToggle("));
        assertTrue(menu.contains("row.setOnClickListener { checkbox.isChecked = !checkbox.isChecked }"));
        assertTrue(menu.contains("setOnCheckedChangeListener { _, enabled ->"));
        assertTrue(menu.contains("row.contentDescription = value + if (enabled)"));
        assertFalse(menu.contains("row.isActivated = enabled"));
        assertTrue(menu.contains("onToggle(enabled)"));
        assertTrue(home.contains("ThemedMenu.showOverflowToggle(this, anchor,"));
        assertTrue(result.contains("ThemedMenu.showOverflowToggle(this, anchor, labels, 2,"));
    }
}
