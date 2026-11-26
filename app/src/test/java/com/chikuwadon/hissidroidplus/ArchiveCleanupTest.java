package com.chikuwadon.hissidroidplus;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.charset.StandardCharsets;
import org.junit.Test;
import static org.junit.Assert.*;

public class ArchiveCleanupTest {
    private String source(String file) throws Exception {
        return Files.readString(Paths.get("src/main/java/com/chikuwadon/hissidroidplus/" + file), StandardCharsets.UTF_8);
    }

    @Test public void searchIsAlwaysNativeAndExplicitBrowserActionRemains() throws Exception {
        String activity = source("ArchiveActivity.kt");
        assertFalse(activity.contains("nativeSearch"));
        int start = activity.indexOf("private fun startSearch()");
        int end = activity.indexOf("private fun load(", start);
        assertTrue(start >= 0 && end > start);
        String search = activity.substring(start, end);
        assertTrue(search.contains("load(query, 1, fresh = true)"));
        assertFalse(search.contains("BrowserLauncher"));
        assertTrue(activity.contains("BrowserLauncher.open(host, target)"));
        assertTrue(activity.contains("外部ブラウザ"));
        assertTrue(activity.contains("state.pages.get(requested, query, page)"));
        assertTrue(activity.contains("ArchiveKeywordHistory.record(host, query.keyword)"));
    }

    @Test public void bothKyodemoChoicesRequireBoardScopedParsing() throws Exception {
        String parser = source("ArchiveProviderParser.kt");
        String repository = source("ArchiveRepository.kt");
        assertFalse(parser.contains("fun kyodemo(html:"));
        assertTrue(parser.contains("path.groupValues[1] != code"));
        assertTrue(repository.contains("ArchiveProvider.EDGE_KYODEMO -> ArchiveProviderParser.kyodemoEdge"));
        assertTrue(repository.contains("ArchiveProvider.FIVECH_KYODEMO -> ArchiveProviderParser.kyodemoBoard"));
    }
}
