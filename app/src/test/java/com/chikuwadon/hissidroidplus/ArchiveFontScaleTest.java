package com.chikuwadon.hissidroidplus;

import org.junit.Test;
import static org.junit.Assert.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;

public class ArchiveFontScaleTest {
    private String source(String name) throws Exception {
        return Files.readString(Paths.get("src/main/java/com/chikuwadon/hissidroidplus/" + name), StandardCharsets.UTF_8);
    }

    @Test public void percentStepsAreBoundedAndRoundTrip() {
        assertEquals(100, ArchiveFontScale.DEFAULT);
        assertEquals(70, ArchiveFontScale.normalize(Integer.MIN_VALUE));
        assertEquals(160, ArchiveFontScale.normalize(Integer.MAX_VALUE));
        assertEquals(100, ArchiveFontScale.normalize(102));
        assertEquals(105, ArchiveFontScale.normalize(103));
        for (int i = 0; i <= ArchiveFontScale.MAX_STEP; i++)
            assertEquals(i, ArchiveFontScale.toProgress(ArchiveFontScale.fromProgress(i)));
        assertEquals(6, ArchiveFontScale.toProgress(100));
    }

    @Test public void fontControlsStayInlineAndResizeRecycledRows() throws Exception {
        String a = source("ArchiveActivity.kt");
        assertTrue(a.contains("links.addView(fontButton, LinearLayout.LayoutParams(dp(48), -1))"));
        assertTrue(a.contains("searchControls.addView(fontControls, if (bottom) 1 else 2"));
        assertTrue(a.contains("applyFontPercent(percent)"));
        assertTrue(a.contains("textSize = 15f * scale"));
        assertTrue(a.contains("textSize = 12f * scale"));
        assertTrue(a.contains("row.minimumHeight = dp((58 * scale).toInt())"));
        assertTrue(a.contains("adapter.notifyDataSetChanged()"));
        assertTrue(a.contains("list.setSelectionFromTop(index.coerceIn"));
        assertTrue(a.contains("AppSettings.archiveFontPercent(host)"));
        assertTrue(a.contains("AppSettings.setArchiveFontPercent(host, percent)"));
        assertTrue(a.contains("AppSettings.setArchiveFontPercent(host, ArchiveFontScale.DEFAULT)"));
        String prefs = source("AppSettings.kt");
        assertTrue(prefs.contains("getInt(ARCHIVE_FONT_PERCENT, ArchiveFontScale.DEFAULT)"));
        assertTrue(prefs.contains("putInt(ARCHIVE_FONT_PERCENT, ArchiveFontScale.normalize(percent))"));
    }
}
