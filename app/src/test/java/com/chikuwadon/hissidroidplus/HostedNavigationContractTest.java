package com.chikuwadon.hissidroidplus;

import org.junit.Test;
import static org.junit.Assert.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.charset.StandardCharsets;

public class HostedNavigationContractTest {
    private String source(String name) throws Exception {
        return new String(Files.readAllBytes(Paths.get("src/main/java/com/chikuwadon/hissidroidplus/" + name)), StandardCharsets.UTF_8);
    }

    @Test public void topLevelSectionsShareOneWindowAndNavigationBar() throws Exception {
        String home = source("HomeActivity.kt");
        String nav = source("MainNavigation.kt");
        assertTrue(home.contains("FragmentContainerView(this)"));
        assertTrue(home.contains("MainNavigation.attach(this, shell"));
        assertTrue(home.contains("transaction.commitNow()"));
        assertTrue(home.contains("transaction.hide(fragment)"));
        assertTrue(home.contains("Lifecycle.State.STARTED"));
        assertTrue(nav.contains("if (a is HomeActivity && destination in hosted)"));
        assertTrue(nav.contains("a.showDestination(destination)"));
        assertFalse(nav.contains("Intent(a, ArchiveActivity::class.java)"));
        assertFalse(nav.contains("Intent(a, SettingsActivity::class.java)"));
        assertFalse(nav.contains("Intent(a, HistoryActivity::class.java)"));
        assertFalse(nav.contains("Intent(a, FavoritesActivity::class.java)"));
        assertTrue(home.contains("MainNavigation.Destination.HISTORY -> HistoryFragment()"));
        assertTrue(home.contains("MainNavigation.Destination.FAVORITES -> FavoritesFragment()"));
        assertTrue(home.contains("val target = destination"));
        assertTrue(nav.contains("if (!isSelected) navigate(a, destination)"));
        assertTrue(source("HostedSections.kt").contains("class SettingsFragment : Fragment()"));
        assertTrue(source("HostedSections.kt").contains("class ArchiveFragment : Fragment()"));
        assertFalse(source("HostedSections.kt").contains("MainNavigation.attach"));
    }

    @Test public void historyViewsHaveSeparateStateAndSafeLifetimes() throws Exception {
        String hosted = source("HostedSections.kt");
        String history = source("HistoryActivity.kt");
        String home = source("HomeActivity.kt");
        assertTrue(hosted.contains("class FavoritesFragment : HistoryFragment()"));
        assertTrue(hosted.contains("ViewModelProvider(this)[HistoryScreenState::class.java]"));
        assertTrue(hosted.contains("override fun onResume()"));
        assertTrue(hosted.contains("content?.resume()"));
        assertTrue(hosted.contains("override fun onDestroyView()"));
        assertTrue(hosted.contains("content?.dispose()"));
        assertTrue(hosted.contains("content = null"));
        assertTrue(history.contains("disposed || host.isDestroyed"));
        assertTrue(history.contains("generation != refreshGeneration"));
        assertTrue(history.contains("list.setSelectionFromTop(position.coerceIn(0, rows.lastIndex), offset)"));
        assertTrue(home.contains("(active as? HistoryFragment)?.handleBack() == true"));
        assertTrue(history.contains("if (!selection) return false"));
    }

    @Test public void savedDestinationAndNewIntentAreHandled() throws Exception {
        String home = source("HomeActivity.kt");
        assertTrue(home.contains("outState.putString(\"main_destination\", activeDestination.name)"));
        assertTrue(home.contains("supportFragmentManager.isStateSaved"));
        assertTrue(home.contains("override fun onPostResume()"));
        assertTrue(home.contains("override fun onNewIntent(newIntent: Intent)"));
        assertTrue(home.contains("sectionBack = object : OnBackPressedCallback(true)"));
        assertTrue(home.contains("MainNavigation.exitFromHome(this@HomeActivity)"));
        assertTrue(source("MainNavigation.kt").contains("is HomeActivity -> a.activeDestination"));
    }
}
