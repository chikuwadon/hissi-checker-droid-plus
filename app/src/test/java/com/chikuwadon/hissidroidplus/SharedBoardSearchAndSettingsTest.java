package com.chikuwadon.hissidroidplus;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.charset.StandardCharsets;
import org.junit.Test;
import static org.junit.Assert.*;

public class SharedBoardSearchAndSettingsTest {
    private String source(String file) throws Exception {
        return new String(Files.readAllBytes(Paths.get(
            "src/main/java/com/chikuwadon/hissidroidplus/" + file)), StandardCharsets.UTF_8);
    }

    @Test public void homeSearchIconIsNextToRefreshAndSelectsBoardCode() throws Exception {
        String home = source("HomeActivity.kt");
        assertTrue(home.contains("search = { showBoardSearch(groups, source) }"));
        int search = home.indexOf("titleRow.addView(icon,");
        int update = home.indexOf("titleRow.addView(update,");
        assertTrue(search >= 0 && update > search);
        assertTrue(home.contains("BoardSearchDialog.show(this, groups"));
        assertTrue(home.contains("board.setText(entry.code)"));
        assertTrue(home.contains("refreshBoards()"));
    }

    @Test public void bothHostsUseTheSameSearchUiAndCatalog() throws Exception {
        String shared = source("BoardSearchDialog.kt");
        String archive = source("ArchiveActivity.kt");
        assertTrue(shared.contains("BoardPickerSearch.index(groups)"));
        assertTrue(shared.contains("BoardPickerSearch.filter(index, input.text.toString())"));
        assertTrue(shared.contains("pick(entry)"));
        assertTrue(shared.contains("contentDescription = \"板を検索\""));
        assertTrue(archive.contains("BoardSearchDialog.icon(host)"));
        assertTrue(archive.contains("BoardSearchDialog.show(host, groups"));
        assertFalse(archive.contains("BoardPickerSearch.filter("));
    }

    @Test public void settingsTitleIsOmittedInBothHostsWithoutRemovingNavigation() throws Exception {
        String shell = source("MainSectionActivity.kt");
        String settings = source("SettingsActivity.kt");
        String hosted = source("HostedSections.kt");
        assertTrue(shell.contains("if (title != null) root.addView(AppBar.title(host, title)"));
        assertTrue(settings.contains("screen(null, MainNavigation.Destination.SETTINGS)"));
        assertTrue(hosted.contains("content.populate(content.build(root, null))"));
        assertTrue(shell.contains("MainNavigation.attach(this, root, destination)"));
        assertTrue(settings.contains("caption(body, \"外観\")"));
    }
}
