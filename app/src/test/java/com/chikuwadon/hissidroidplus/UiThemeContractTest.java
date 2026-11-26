package com.chikuwadon.hissidroidplus;

import org.junit.Test;
import static org.junit.Assert.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.charset.StandardCharsets;

public class UiThemeContractTest {
    private String read(String path) throws Exception {
        return new String(Files.readAllBytes(Paths.get("src/main/" + path)), StandardCharsets.UTF_8);
    }
    private String source(String name) throws Exception {
        return read("java/com/chikuwadon/hissidroidplus/" + name);
    }
    @Test public void homeTitleIsOutsideScrollAndSubtitleRemoved() throws Exception {
        String xml = read("res/layout/activity_home.xml");
        assertTrue(xml.indexOf("@+id/app_title") < xml.indexOf("<ScrollView"));
        assertFalse(xml.contains("@string/search_posts_by_id"));
        assertTrue(xml.contains("android:layout_height=\"56dp\""));
    }
    @Test public void sectionAndHistoryShareToolbarTitle() throws Exception {
        assertTrue(source("MainSectionActivity.kt").contains("AppBar.title(host, title)"));
        String history = source("HistoryActivity.kt");
        assertTrue(history.contains("AppBar.title(host, if (favoritesScreen)"));
        assertTrue(history.contains("AppBar.Icon.SEARCH"));
        assertTrue(history.contains("AppBar.Icon.SELECT"));
        assertTrue(history.contains("AppBar.Icon.DONE"));
        assertTrue(history.contains("dp(56)"));
    }
    @Test public void homeAndHistoryUseSharedThemedCalendar() throws Exception {
        assertFalse(source("HomeActivity.kt").contains("DatePickerDialog"));
        String history = source("HistoryActivity.kt");
        assertFalse(history.contains("DatePickerDialog"));
        assertTrue(history.contains("ThemedMenu.showForm(host, panel)"));
        assertTrue(history.contains("CalendarDialog.show(host, day)"));
        String calendar = source("CalendarDialog.kt");
        assertTrue(calendar.contains("ThemedMenu.formPanel(a)"));
        assertTrue(calendar.contains("R.color.panel_pressed"));
        assertTrue(calendar.contains("onSelected(selected)"));
    }
    @Test public void retentionSliderHasNoRedundantNote() throws Exception {
        String settings = source("SettingsActivity.kt");
        assertFalse(settings.contains("100件～10000件（100件単位）、右端：無制限"));
        assertTrue(settings.contains("HistoryRetention.LAST_PROGRESS"));
    }
}
