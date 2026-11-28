package com.chikuwadon.hissidroidplus;
import org.junit.Test;
import static org.junit.Assert.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
public class SavedSearchResultsContractTest {
    private String source(String f) throws Exception {
        return Files.readString(Paths.get("src/main/java/com/chikuwadon/hissidroidplus/" + f), StandardCharsets.UTF_8);
    }
    @Test public void snapshotsArePrivateBoundedAndDefaultOn() throws Exception {
        String s = source("SavedSearchResults.kt");
        assertTrue(s.contains("context.noBackupFilesDir"));
        assertTrue(s.contains("AtomicFile"));
        assertTrue(s.contains("8 * 1024 * 1024"));
        assertTrue(s.contains("idGeneration++"));
        assertTrue(s.contains("archiveGeneration++"));
        assertTrue(source("AppSettings.kt").contains("getBoolean(KEEP_SEARCH_RESULTS, true)"));
        assertTrue(source("AppSettings.kt").contains("if (!enabled) SavedSearchResults.clearAll(context)"));
        assertTrue(source("SettingsActivity.kt").contains("検索結果を保持"));
    }
    @Test public void navigationRestoresWithoutNewSearch() throws Exception {
        assertTrue(source("ResultActivity.kt").contains("SavedSearchResults.readId(this)"));
        assertTrue(source("ResultActivity.kt").contains("SavedSearchResults.saveId(this"));
        assertTrue(source("MainNavigation.kt").contains("SavedSearchResults.hasId(a)"));
        assertTrue(source("MainNavigation.kt").contains("consumeRestoreToken"));
        assertTrue(source("ArchiveActivity.kt").contains("SavedSearchResults.readArchive(host)"));
        assertTrue(source("ArchiveActivity.kt").contains("SavedSearchResults.saveArchive(host"));
    }
}
