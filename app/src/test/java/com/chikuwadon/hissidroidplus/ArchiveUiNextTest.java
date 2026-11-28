package com.chikuwadon.hissidroidplus;

import org.junit.Test;
import org.jsoup.Jsoup;
import static org.junit.Assert.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;

public class ArchiveUiNextTest {
    private String source(String f) throws Exception {
        return Files.readString(Paths.get("src/main/java/com/chikuwadon/hissidroidplus/" + f), StandardCharsets.UTF_8);
    }
    @Test public void datesNeverUseFutabaSequenceNumberOrTitle() {
        assertEquals("2026/11/28 12:34:56", KyodemoDates.parse("2026-11-28T03:34:56Z"));
        assertEquals("11/28 12:34", KyodemoDates.parse("11/28 12:34 11/28 13:00"));
        assertEquals("", KyodemoDates.parse("02/30 12:00"));
        assertEquals("", KyodemoDates.created(Jsoup.parse("<article class='panel-block'><a><h2 class='cltitle'>2026/11/28 12:00</h2><small>33</small></a></article>").selectFirst("a")));
        assertEquals("11/28 12:34", KyodemoDates.created(Jsoup.parse("<article class='panel-block'><a><h2 class='cltitle'>title</h2><small>11/28 12:34 11/28 13:00</small></a></article>").selectFirst("a")));
    }
    @Test public void orderingAndRestorePolicy() throws Exception {
        assertEquals(ArchiveProvider.FIVECH_KYODEMO.ordinal()+1, ArchiveProvider.OTHER_KYODEMO.ordinal());
        assertEquals(MainNavigation.Destination.RESULT, MainNavigation.decodeDestination("RESULT"));
        assertEquals(MainNavigation.Destination.HOME, MainNavigation.decodeDestination("unknown"));
        String a = source("ArchiveActivity.kt");
        assertTrue(a.contains("otherServiceButton.setOnClickListener"));
        assertTrue(a.contains("OtherBoardSelection.add(host, code"));
        assertTrue(a.contains("Typeface.BOLD"));
        assertTrue(a.contains("ForegroundColorSpan(host.getColor(R.color.accent))"));
        assertTrue(source("HomeActivity.kt").contains("MainNavigation.lastDestination(this)"));
        assertTrue(source("ResultActivity.kt").contains("MainNavigation.rememberDestination(this, MainNavigation.Destination.RESULT)"));
    }
}
