package com.chikuwadon.hissidroidplus;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.charset.StandardCharsets;
import org.junit.Test;
import static org.junit.Assert.*;
public final class MainNavigationContractTest {
    private static String source(String file) throws Exception {
        return new String(Files.readAllBytes(Paths.get("src/main/java/com/chikuwadon/hissidroidplus/" + file)), StandardCharsets.UTF_8);
    }
    @Test public void topLevelNavigationDoesNotReduceResultArea() throws Exception {
        String nav = source("MainNavigation.kt");
        for (String name : new String[]{"HOME","ARCHIVE","HISTORY","FAVORITES","SETTINGS"})
            assertTrue(nav.contains(name));
        assertTrue(source("HomeActivity.kt").contains("MainNavigation.attach"));
        assertTrue(source("HistoryActivity.kt").contains("MainNavigation.attach"));
        assertFalse(source("ResultActivity.kt").contains("MainNavigation.attach"));
        assertFalse(source("EdgeAnalysisActivity.kt").contains("MainNavigation.attach"));
        assertTrue(nav.contains("Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP"));
    }
    @Test public void archiveLaunchesOnlyExplicitProviderLinks() throws Exception {
        String archive = source("ArchiveActivity.kt");
        assertTrue(archive.contains("https://n2ch.net/?guid=ON"));
        assertTrue(archive.contains("https://eddiarchive3rd.boy.jp/"));
        assertTrue(archive.contains("https://www.kyodemo.net/sdemo/"));
        assertFalse(archive.contains("HttpTransport"));
    }
}
