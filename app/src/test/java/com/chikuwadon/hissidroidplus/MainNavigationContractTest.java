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
    @Test public void bottomNavigationDoesNotPlayActivityTransitions() throws Exception {
        String nav = source("MainNavigation.kt");
        assertTrue(nav.contains("Intent.FLAG_ACTIVITY_NO_ANIMATION"));
        assertTrue(nav.contains("ActivityOptions.makeCustomAnimation(a, 0, 0)"));
        assertTrue(nav.contains("a.overridePendingTransition(0, 0)"));
        assertFalse(source("ResultActivity.kt").contains("FLAG_ACTIVITY_NO_ANIMATION"));
    }
    @Test public void settingCheckboxIsRightAlignedAndRowIsAccessible() throws Exception {
        String settings = source("SettingsActivity.kt");
        int label = settings.indexOf("animationRow.addView(label, LinearLayout.LayoutParams(0, -2, 1f))");
        int checkbox = settings.indexOf("animationRow.addView(checkbox, LinearLayout.LayoutParams(dp(40), dp(48)))");
        assertTrue(label >= 0 && checkbox > label);
        assertTrue(settings.contains("animationRow.setOnClickListener { checkbox.isChecked = !checkbox.isChecked }"));
        assertTrue(settings.contains("info.isCheckable = true"));
        assertTrue(settings.contains("info.isChecked = checkbox.isChecked"));
    }
    @Test public void archiveLaunchesOnlyExplicitProviderLinks() throws Exception {
        String archive = source("ArchiveActivity.kt");
        assertTrue(archive.contains("https://n2ch.net/?guid=ON"));
        assertTrue(archive.contains("https://eddiarchive3rd.boy.jp/"));
        assertTrue(archive.contains("https://www.kyodemo.net/sdemo/"));
        assertFalse(archive.contains("HttpTransport"));
    }
}
