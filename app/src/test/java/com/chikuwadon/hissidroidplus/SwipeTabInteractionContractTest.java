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
        assertTrue(menu.contains("val checkableWidth = ceil(toggleTextWidth.toDouble()).toInt() + dp(a, 93)"));
        assertTrue(menu.contains("val availableWidth = a.resources.displayMetrics.widthPixels - dp(a, 24)"));
        assertTrue(menu.contains("isSingleLine = true"));
        assertTrue(menu.contains("ellipsize = android.text.TextUtils.TruncateAt.END"));
    }

    @Test public void overflowCheckboxDoesNotDismissWhenToggled() throws Exception {
        String menu = source("java/com/chikuwadon/hissidroidplus/ThemedMenu.kt");
        String home = source("java/com/chikuwadon/hissidroidplus/HomeActivity.kt");
        String result = source("java/com/chikuwadon/hissidroidplus/ResultActivity.kt");
        assertTrue(menu.contains("fun showOverflowToggle("));
        assertTrue(menu.contains("row.setOnClickListener { checkbox.isChecked = !checkbox.isChecked }"));
        assertTrue(menu.contains("setOnCheckedChangeListener { _, enabled -> onToggle(enabled) }"));
        assertTrue(home.contains("ThemedMenu.showOverflowToggle(this, anchor,"));
        assertTrue(result.contains("ThemedMenu.showOverflowToggle(this, anchor, labels, 2,"));
    }
}
