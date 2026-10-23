package com.chikuwadon.hissidroidplus;

import static org.junit.Assert.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.Test;

/** Regression guard: do not accidentally bundle an extracted legacy board list. */
public final class BoardMenuSourceContractTest {
    @Test public void boardsAreNotBundledFromLegacyApk() throws Exception {
        assertFalse(Files.exists(Paths.get("src/main/assets/original_boards.tsv")));
        Path activity=Paths.get("src/main/java/com/chikuwadon/hissidroidplus/HomeActivity.java");
        String home=new String(Files.readAllBytes(activity), StandardCharsets.UTF_8);
        assertFalse(home.contains("original_boards.tsv"));
        assertFalse(home.contains("BoardMenu.bundled("));
        assertTrue(home.contains("bbsmenu_5ch.json"));
        assertTrue(home.contains("bbsmenu_5ch.html"));
        assertTrue(home.contains("fetchOfficialMenu()"));
        String menu=new String(Files.readAllBytes(Paths.get(
          "src/main/java/com/chikuwadon/hissidroidplus/BoardMenu.java")),StandardCharsets.UTF_8);
        assertTrue(menu.contains("https://menu.5ch.io/bbsmenu.json"));
        assertTrue(menu.contains("https://menu.5ch.io/bbsmenu.html"));
    }
}
