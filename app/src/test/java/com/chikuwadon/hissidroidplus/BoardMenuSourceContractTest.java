package com.chikuwadon.hissidroidplus;

import static org.junit.Assert.*;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.Test;

/** The bundled board listing is generated from the official menu, not an original app APK. */
public final class BoardMenuSourceContractTest {
    @Test public void generatedSnapshotHasDistinctValidatedBoardCodes() throws Exception {
        assertFalse(Files.exists(Paths.get("src/main/assets/original_boards.tsv")));
        Path source=Paths.get("src/main/assets/offline_boards.tsv");
        assertTrue(Files.isRegularFile(source));
        List<BoardMenu.Category> categories;
        try(java.io.Reader reader=Files.newBufferedReader(source,StandardCharsets.UTF_8)){
            categories=BoardMenu.parseOfflineSnapshot(reader);
        }
        assertTrue("Category count",categories.size()>=10);
        int boards=0;
        Set<String> unique=new HashSet<>();
        for(BoardMenu.Category c:categories){
            assertFalse(c.name.isEmpty());
            for(BoardCatalog.Board b:c.boards){
                assertTrue("Board ID: "+b.code,b.code.matches("[A-Za-z0-9_]{1,60}"));
                assertTrue("Duplicate board: "+b.code,unique.add(b.code));
                assertFalse(b.name.isEmpty());
                boards++;
            }
        }
        assertTrue("Expected useful offline coverage",boards>=100);
        assertTrue("Known board from official menu",unique.contains("news4vip"));
    }
    @Test public void parserRejectsInvalidAndDuplicateEntries() throws Exception {
        String sample="テスト\tnews4vip\tニュー速VIP\n"+
                "テスト\tnews4vip\t重複\n"+
                "テスト\tbad/path\t不正な板\n"+
                "別カテゴリ\tsoftware\tソフトウェア\n"+
                "不完全な行\n";
        List<BoardMenu.Category> result=BoardMenu.parseOfflineSnapshot(new StringReader(sample));
        assertEquals(2,result.size());
        assertEquals(1,result.get(0).boards.size());
        assertEquals("news4vip",result.get(0).boards.get(0).code);
        assertEquals("software",result.get(1).boards.get(0).code);
    }
    @Test public void noLegacyResourceOrDownloaderRemains() throws Exception {
        String home=new String(Files.readAllBytes(Paths.get(
                "src/main/java/com/chikuwadon/hissidroidplus/HomeActivity.java")),StandardCharsets.UTF_8);
        assertTrue(home.contains("offline_boards.tsv"));
        assertTrue(home.contains("bbsmenu_5ch.json"));
        assertTrue(home.contains("bbsmenu_5ch.html"));
        assertTrue(home.contains("fetchOfficialMenu()"));
        String code=new String(Files.readAllBytes(Paths.get(
                "src/main/java/com/chikuwadon/hissidroidplus/BoardMenu.java")),StandardCharsets.UTF_8);
        assertTrue(code.contains("https://menu.5ch.io/bbsmenu.json"));
        assertTrue(code.contains("https://menu.5ch.io/bbsmenu.html"));
    }
}
