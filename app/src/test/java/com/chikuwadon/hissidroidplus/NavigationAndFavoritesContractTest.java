package com.chikuwadon.hissidroidplus;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.charset.StandardCharsets;
import org.junit.Test;
import static org.junit.Assert.*;
public class NavigationAndFavoritesContractTest {
    private String source(String name) throws Exception {
        return new String(Files.readAllBytes(Paths.get("src/main/java/com/chikuwadon/hissidroidplus/" + name)), StandardCharsets.UTF_8);
    }
    @Test public void fastScrollAndStarHaveSeparateTouchLanes() throws Exception {
        String h = source("HistoryActivity.kt");
        assertTrue(h.contains("isFastScrollEnabled = true"));
        assertTrue(h.contains("scrollBarStyle = View.SCROLLBARS_OUTSIDE_OVERLAY"));
        assertTrue(h.contains("setPadding(dp(10), dp(6), dp(48), dp(6))"));
        assertTrue(h.contains("row.addView(star, LinearLayout.LayoutParams(dp(48), dp(48)))"));
    }
    @Test public void favoritesSelectionNeverDeletesNormalHistory() throws Exception {
        String h = source("HistoryActivity.kt");
        assertTrue(h.contains("menuButton.visibility = View.VISIBLE"));
        assertTrue(h.contains("if (favorite) SearchHistory.removeFavorites(this, selected)"));
        assertTrue(h.contains("if (favorite) SearchHistory.clearFavorites(this)"));
        String store = source("SearchHistory.kt");
        assertTrue(store.contains("deleteEntries(context, \"favorites\", urls)"));
        assertTrue(store.contains("deleteEntries(context, \"recent\", urls)"));
        assertTrue(store.contains("db(context).delete(\"favorites\", null, null)"));
    }
    @Test public void onlyOptionalNavigationItemsCanBeHidden() throws Exception {
        String nav = source("MainNavigation.kt");
        assertTrue(nav.contains("listOf(Destination.ARCHIVE, Destination.HISTORY, Destination.FAVORITES)"));
        assertTrue(nav.contains("if (destination !in configurable) return"));
        assertTrue(nav.contains("destination !in configurable ||"));
        assertTrue(nav.contains("visibility = if (isVisible(a, destination)) View.VISIBLE else View.GONE"));
        assertTrue(source("SettingsActivity.kt").contains("MainNavigation.refresh(this@SettingsActivity)"));
    }
    @Test public void retentionValuesShareOneRowAndBrowserLabelIsCompact() throws Exception {
        String s = source("SettingsActivity.kt");
        assertTrue(s.contains("limitRow.addView(limitLabel"));
        assertTrue(s.contains("limitRow.addView(currentLabel"));
        String result = source("ResultActivity.kt");
        assertFalse(result.contains("検索結果を外部ブラウザで開く"));
        assertTrue(result.contains("外部ブラウザで開く"));
    }
}
