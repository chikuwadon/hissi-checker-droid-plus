package com.chikuwadon.hissidroidplus;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.charset.StandardCharsets;
import org.junit.Test;
import static org.junit.Assert.*;
public class ResultAndHistoryUiContractTest {
    private String source(String name) throws Exception {
        return new String(Files.readAllBytes(Paths.get("src/main/java/com/chikuwadon/hissidroidplus/" + name)), StandardCharsets.UTF_8);
    }
    @Test public void resultMenuIsBesideTabsAndBackRetainsResult() throws Exception {
        String xml = new String(Files.readAllBytes(Paths.get("src/main/res/layout/activity_results.xml")), StandardCharsets.UTF_8);
        assertFalse(xml.contains("@+id/toolbar_title"));
        assertFalse(xml.contains("@+id/toolbar_back"));
        assertTrue(xml.indexOf("@+id/toolbar_more") > xml.indexOf("@+id/tab_names"));
        assertTrue(xml.indexOf("@+id/toolbar_more") < xml.indexOf("@+id/status"));
        String result = source("ResultActivity.kt");
        assertTrue(result.contains("registerBackAction { backFromResult() }"));
        assertTrue(source("CheckerActivity.kt").contains("onBackPressedDispatcher.addCallback(this"));
        int start = result.indexOf("private fun backFromResult()");
        int end = result.indexOf("private fun exitResults()", start);
        assertTrue(start >= 0 && end > start);
        String back = result.substring(start, end);
        assertTrue(back.contains("MainNavigation.backToPrevious(this, MainNavigation.Destination.RESULT)"));
        assertTrue(back.contains("MainNavigation.exitFromResult(this)"));
        assertFalse(back.contains("launchedExternally()"));
        assertFalse(back.contains("AppSettings.standalone(this)"));
        assertTrue(back.indexOf("MainNavigation.backToPrevious") < back.indexOf("MainNavigation.exitFromResult"));
    }
    @Test public void starUsesIndependentClickAndExplicitDatabaseState() throws Exception {
        String history = source("HistoryActivity.kt");
        assertTrue(history.contains("holder.star.setOnClickListener { changeFavorite(entry) }"));
        assertTrue(history.contains("holder.star.isFocusable = false"));
        assertTrue(history.contains("ViewGroup.FOCUS_BLOCK_DESCENDANTS"));
        assertTrue(history.contains("SearchHistory.setFavorite(host, entry, desired)"));
        assertTrue(history.contains("pendingFavorites[entry.url] ?: favoriteUrls.contains(entry.url)"));
        assertTrue(source("SearchHistory.kt").contains("fun setFavorite(context: Context, entry: Entry, enabled: Boolean)"));
    }
    @Test public void retentionShowsSavedAndProposedValues() throws Exception {
        String settings = source("SettingsActivity.kt");
        assertTrue(settings.contains("現在："));
        assertTrue(settings.contains("HistoryRetention.label(currentLimit)"));
        assertTrue(settings.contains("HistoryRetention.label(pendingLimit)"));
        assertTrue(settings.contains("ThemedMenu.formButton(this, \"保存件数を適用\")"));
    }
}
