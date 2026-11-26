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
        assertTrue(h.contains("isFastScrollEnabled = false"));
        assertTrue(h.contains("verticalScrollbarPosition = View.SCROLLBAR_POSITION_RIGHT"));
        assertTrue(h.contains("FrameLayout.LayoutParams(dp(12), -1, Gravity.RIGHT)"));
        assertTrue(h.contains("setPadding(dp(10), dp(6), dp(12), dp(6))"));
        String rail = source("HistoryScrollRail.kt");
        assertTrue(rail.contains("list.setSelectionFromTop"));
        assertTrue(rail.contains("event.x < 0f || event.x >= width"));
        assertTrue(h.contains("row.addView(star, LinearLayout.LayoutParams(dp(48), dp(48)))"));
    }
    @Test public void favoritesHaveNoDirectStarToggle() throws Exception {
        String h = source("HistoryActivity.kt");
        assertTrue(h.contains("val showStar = !favoritesScreen && !selection"));
        assertTrue(h.contains("holder.star.visibility = if (showStar) View.VISIBLE else View.GONE"));
        assertTrue(h.contains("if (favoritesScreen || selection || pendingFavorites.containsKey(entry.url)) return"));
        assertTrue(h.contains("holder.star.setOnClickListener(null)"));
    }
    @Test public void favoritesSelectionNeverDeletesNormalHistory() throws Exception {
        String h = source("HistoryActivity.kt");
        assertTrue(h.contains("menuButton.visibility = View.VISIBLE"));
        assertTrue(h.contains("if (favorite) SearchHistory.removeFavorites(host, selected)"));
        assertTrue(h.contains("if (favorite) SearchHistory.clearFavorites(host)"));
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
        assertTrue(source("SettingsActivity.kt").contains("MainNavigation.refresh(host)"));
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
