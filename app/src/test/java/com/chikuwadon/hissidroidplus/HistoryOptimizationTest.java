package com.chikuwadon.hissidroidplus;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

public class HistoryOptimizationTest {
    private String source(String file) throws Exception {
        return new String(Files.readAllBytes(Paths.get(
            "src/main/java/com/chikuwadon/hissidroidplus/" + file)), StandardCharsets.UTF_8);
    }

    private SearchHistory.Entry entry(String url, String board, String day, String id, long time) {
        return new SearchHistory.Entry(url, "5ch", board, day, id, time, 1);
    }

    @Test public void optimizedSortKeepsCaseInsensitiveBoardAndStableDateOrdering() {
        SearchHistory.Entry a = entry("https://hissi.org/read.php/b/20261125/a.html", "B", "", "ID:AbC", 30);
        SearchHistory.Entry b = entry("https://hissi.org/read.php/a/20261126/b.html", "a", "20261126", "def", 20);
        SearchHistory.Entry c = entry("https://hissi.org/read.php/a/20261126/c.html", "A", "20261126", "ghi", 10);
        List<SearchHistory.Entry> rows = Arrays.asList(a,b,c);
        assertEquals(Arrays.asList(b,c,a), SearchHistory.sorted(rows, 2));
        assertEquals(Arrays.asList(b,c,a), SearchHistory.sorted(rows, 3));
        assertEquals(Arrays.asList(c,b,a), SearchHistory.sorted(rows, 1));
        assertEquals(rows, SearchHistory.sorted(rows, 0));
    }

    @Test public void filterRetainsCaseSensitiveIdAndInclusiveFallbackDate() {
        SearchHistory.Entry e = entry("https://hissi.org/read.php/android/20261126/a.html",
            "Android", "", "ID：AbC", 1795651200000L);
        assertTrue(new HistoryFilter("","ID:AbC","20261126","20261126",true).matches(e));
        assertFalse(new HistoryFilter("","abc","","",false).matches(e));
        assertTrue(new HistoryFilter("AND","","","",false).matches(e));
        assertTrue(new HistoryFilter("","","","",false).matches(e));
        assertFalse(new HistoryFilter("","","20261127","",true).matches(e));
    }

    @Test public void visibleCollectionOnlyIsDecodedAndDefaultOrderIsReused() throws Exception {
        String store = source("SearchHistory.kt");
        String screen = source("HistoryActivity.kt");
        assertTrue(store.contains("val source = load(database, if (favoritesScreen) \"favorites\" else \"recent\")"));
        assertTrue(store.contains("if (favoritesScreen) count(database, \"recent\") else source.size"));
        assertTrue(store.contains("database.query(\"favorites\", arrayOf(\"url\")"));
        assertTrue(store.contains("if (filter.active()) source.filter"));
        assertTrue(store.contains("if (sort == 0) matched else sorted(matched, sort)"));
        assertTrue(store.contains("viewed_at DESC, url ASC"));
        assertTrue(screen.contains("rows.addAll(snapshot.rows)"));
        assertTrue(screen.contains("generation != refreshGeneration"));
        assertFalse(screen.contains("SearchHistory.recents(host)"));
        assertFalse(screen.contains("SearchHistory.favorites(host)"));
        assertTrue(store.contains("SQLiteOpenHelper(app, \"search_history.db\", null, 1)"));
    }

    @Test public void cachedMenuUsesSameBoundedReaderAsNetwork() throws Exception {
        String home = source("HomeActivity.kt");
        assertTrue(home.contains("HttpResponseReader.read(input, 4 * 1024 * 1024, -1)"));
        assertFalse(home.contains("ByteArrayOutputStream"));
    }
}
