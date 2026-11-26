package com.chikuwadon.hissidroidplus;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.charset.StandardCharsets;
import org.junit.Test;
import static org.junit.Assert.*;

public class InteractionRegressionTest {
    private String source(String name) throws Exception {
        return Files.readString(Paths.get("src/main/java/com/chikuwadon/hissidroidplus/" + name), StandardCharsets.UTF_8);
    }

    @Test public void historyRejectsStaleRowAndDestructiveActionsDuringRefresh() throws Exception {
        String h = source("HistoryActivity.kt");
        assertTrue(h.contains("if (refreshing || position !in rows.indices) return@setOnItemClickListener"));
        assertTrue(h.contains("if (refreshing || position !in rows.indices) return@setOnItemLongClickListener true"));
        assertTrue(h.contains("if (refreshing || chosen.isEmpty()) return"));
        assertTrue(h.contains("private fun confirmClear() {\n        if (refreshing) return"));
        assertTrue(h.contains("holder.star.isEnabled = !refreshing && !pendingFavorites.containsKey(entry.url)"));
        assertTrue(h.contains("deleteSelected.isEnabled = !refreshing && chosen.isNotEmpty()"));
        assertTrue(h.contains("cancelSelection.setOnClickListener {\n            if (refreshing) return@setOnClickListener"));
    }

    @Test public void supersededHistoryRefreshesSkipReadsAndOnlyNewestResultUnlocksRows() throws Exception {
        String h = source("HistoryActivity.kt");
        assertTrue(h.contains("@Volatile private var refreshGeneration"));
        int start = h.indexOf("private fun refresh()");
        int end = h.indexOf("private fun changeFavorite(", start);
        assertTrue(start >= 0 && end > start);
        String refresh = h.substring(start, end);
        assertTrue(refresh.contains("refreshing = true"));
        assertTrue(refresh.indexOf("if (generation != refreshGeneration) return@runAsync") < refresh.indexOf("SearchHistory.screenSnapshot"));
        assertTrue(refresh.contains("if (isFinishing || isDestroyed || generation != refreshGeneration) return@runOnUiThread\n                    refreshing = false"));
        assertTrue(refresh.contains("if (!isFinishing && !isDestroyed && generation == refreshGeneration) {\n                        refreshing = false"));
        assertTrue(refresh.contains("rows.clear()\n                        chosen.clear()\n                        favoriteUrls.clear()"));
    }

    @Test public void switchingToHomeAlsoDismissesOutgoingKeyboardBeforeHidingFragment() throws Exception {
        String h = source("HomeActivity.kt");
        int start = h.indexOf("fun showDestination(");
        int end = h.indexOf("override fun onSaveInstanceState", start);
        assertTrue(start >= 0 && end > start);
        String navigation = h.substring(start, end);
        assertTrue(navigation.contains("if (target != activeDestination)"));
        int hide = navigation.indexOf(".hideSoftInputFromWindow(");
        int commit = navigation.indexOf("transaction.commitNow()");
        assertTrue(hide >= 0 && commit > hide);
        assertTrue(navigation.contains("ViewCompat.requestApplyInsets"));
        assertFalse(navigation.contains("if (target != MainNavigation.Destination.HOME)"));
    }

    @Test public void archiveBrowserActionIsGuardedWhileQueryIsLoading() throws Exception {
        String a = source("ArchiveActivity.kt");
        assertTrue(a.contains("browser.setOnClickListener {\n            if (loading || disposed) return@setOnClickListener"));
        assertTrue(a.contains("browser.isEnabled = !loading"));
        assertTrue(a.contains("BrowserLauncher.open(host, target)"));
    }
}
