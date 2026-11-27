package com.chikuwadon.hissidroidplus;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

public class ArchiveSearchUiTest {
    private String source(String file) throws Exception {
        return new String(Files.readAllBytes(Paths.get(
            "src/main/java/com/chikuwadon/hissidroidplus/" + file)), StandardCharsets.UTF_8);
    }

    @Test public void boardSearchMatchesNamesCodesCategoriesAndNormalizesWidth() {
        BoardMenu.Category g = new BoardMenu.Category("実況");
        g.boards.add(new BoardCatalog.Board("livegalileo", "なんでも実況G"));
        g.boards.add(new BoardCatalog.Board("news4vip", "ニュー速VIP"));
        BoardMenu.Category a = new BoardMenu.Category("家電");
        a.boards.add(new BoardCatalog.Board("android", "Android"));
        a.boards.add(new BoardCatalog.Board("news4vip", "ニュー速VIP"));
        List<BoardPickerSearch.Entry> index = BoardPickerSearch.index(Arrays.asList(g, a));
        assertEquals(3, index.size());
        assertEquals("livegalileo", BoardPickerSearch.filter(index, "なんでも実況Ｇ").get(0).getCode());
        assertEquals("android", BoardPickerSearch.filter(index, "ＡＮＤＲＯＩＤ").get(0).getCode());
        assertEquals("news4vip", BoardPickerSearch.filter(index, "実況 VIP").get(0).getCode());
        assertTrue(BoardPickerSearch.filter(index, "no-match").isEmpty());
        assertEquals(3, BoardPickerSearch.filter(index, " ").size());
    }

    @Test public void overlappingImeAndSystemInsetsAreNotAddedTwice() {
        assertEquals(24, ImeInsets.bottom(24, 0));
        assertEquals(320, ImeInsets.bottom(24, 320));
        assertEquals(24, ImeInsets.bottom(24, 12));
        assertEquals(0, ImeInsets.bottom(0, 0));
    }

    @Test public void archiveControlsAreBorderlessButInputsRetainTheirBackground() throws Exception {
        String archive = source("ArchiveActivity.kt");
        String menu = source("ThemedMenu.kt");
        assertTrue(archive.contains("ThemedMenu.formButton(host, label, outlined = false)"));
        assertTrue(archive.contains("keyword = ThemedMenu.formInput(host, state.draft.keyword)"));
        assertTrue(menu.contains("setBackgroundResource(R.drawable.input_background)"));
        assertTrue(menu.contains("outlined: Boolean = true"));
        assertTrue(archive.contains("setPadding(dp(4), dp(8), dp(4), dp(4))"));
        assertTrue(archive.contains("search = { showBoardSearch(groups) }"));
        assertTrue(archive.contains("heading.addView(it, LinearLayout.LayoutParams(dp(48), dp(48)))"));
    }

    @Test public void imeHandlingIsAppliedToBothArchiveHostsAndNavigationRestores() throws Exception {
        String base = source("CheckerActivity.kt");
        String home = source("HomeActivity.kt");
        String archive = source("ArchiveActivity.kt");
        String nav = source("MainNavigation.kt");
        assertTrue(base.contains("insets.getInsets(WindowInsetsCompat.Type.ime()).bottom"));
        assertTrue(base.contains("bottom + ImeInsets.bottom(bars.bottom, ime)"));
        assertTrue(home.contains("resizeForIme = true"));
        assertTrue(archive.contains("resizeForIme = true"));
        assertTrue(base.contains("archive && AppSettings.archiveSearchAtBottom(this) && insets.isVisible"));
        assertTrue(nav.contains("if (selected != Destination.ARCHIVE) setArchiveImeVisible(a, false)"));
        assertTrue(nav.contains("findViewWithTag<View>(DIVIDER_TAG)?.visibility = visibility"));
        String manifest = new String(Files.readAllBytes(Paths.get("src/main/AndroidManifest.xml")), StandardCharsets.UTF_8);
        assertTrue(manifest.contains("android:name=\".HomeActivity\" android:exported=\"true\" android:windowSoftInputMode=\"adjustResize\""));
        assertTrue(manifest.contains("android:name=\".ArchiveActivity\" android:exported=\"false\" android:windowSoftInputMode=\"adjustResize\""));
    }
}
