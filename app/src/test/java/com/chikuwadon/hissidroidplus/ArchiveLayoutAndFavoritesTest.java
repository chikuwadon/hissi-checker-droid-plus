package com.chikuwadon.hissidroidplus;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.junit.Test;
import static org.junit.Assert.*;

public class ArchiveLayoutAndFavoritesTest {
    private String source(String file) throws Exception {
        return new String(Files.readAllBytes(Paths.get(
            "src/main/java/com/chikuwadon/hissidroidplus/" + file)), StandardCharsets.UTF_8);
    }

    @Test public void favoriteBoardCodesAreValidatedSortedAndDeduplicated() {
        assertEquals(Arrays.asList("android", "livegalileo", "news4vip"),
            BoardFavorites.normalized(Arrays.asList("news4vip", "android", "livegalileo", "android", "../bad", "", "a/b")));
        assertFalse(BoardFavorites.valid("https://example.com"));
        assertFalse(BoardFavorites.valid("x".repeat(61)));
    }

    @Test public void homeAndArchiveShareExistingFavoriteStore() throws Exception {
        String home = source("HomeActivity.kt");
        String archive = source("ArchiveActivity.kt");
        String store = source("BoardFavorites.kt");
        assertTrue(home.contains("BoardFavorites.add(this, current)"));
        assertTrue(home.contains("BoardFavorites.read(this)"));
        assertTrue(home.contains("BoardFavorites.clear(this)"));
        assertTrue(archive.contains("BoardFavorites.add(host, code)"));
        assertTrue(archive.contains("BoardFavorites.read(host)"));
        assertTrue(store.contains("HomeActivity::class.java.simpleName"));
        assertTrue(store.contains("favorite_boards"));
        assertTrue(archive.contains("selectBoard(code,"));
    }

    @Test public void selectingProviderDoesNotAutomaticallyOpenBoardChooser() throws Exception {
        String archive = source("ArchiveActivity.kt");
        int start = archive.indexOf("provider.setOnClickListener");
        int end = archive.indexOf("boardRow = LinearLayout", start);
        assertTrue(start >= 0 && end > start);
        assertFalse(archive.substring(start, end).contains("showBoards()"));
        assertTrue(archive.contains("boardButton.setOnClickListener { showBoards() }"));
        assertFalse(archive.contains("AppBar.title(host, \"過去ログ検索\")"));
    }

    @Test public void bottomPlacementReordersOnlySearchControlsAndAppliesOnResume() throws Exception {
        String archive = source("ArchiveActivity.kt");
        String settings = source("SettingsActivity.kt");
        String prefs = source("AppSettings.kt");
        assertTrue(prefs.contains("getBoolean(ARCHIVE_SEARCH_BOTTOM, false)"));
        assertTrue(settings.contains("AppSettings.setArchiveSearchAtBottom(host, index == 1)"));
        assertTrue(archive.contains("searchControls.addView(searchLinks, if (bottom) 0 else 1"));
        assertTrue(archive.contains("val target = if (bottom) searchBottom else searchTop"));
        assertTrue(archive.contains("if (bottomPlacement == bottom) return"));
        assertTrue(archive.indexOf("root.addView(pager") < archive.indexOf("root.addView(searchBottom"));
        assertTrue(archive.contains("content?.resume()"));
        String hosted = source("HostedSections.kt");
        assertTrue(hosted.substring(0, hosted.indexOf("class SettingsFragment")).contains("content?.resume()"));
        int start = archive.indexOf("private fun applySearchPlacement()");
        int end = archive.indexOf("fun resume()", start);
        String placement = archive.substring(start, end);
        assertFalse(placement.contains("state.pages.clear()"));
        assertFalse(placement.contains("load("));
        assertFalse(placement.contains("notifyDataSetChanged"));
    }
}
