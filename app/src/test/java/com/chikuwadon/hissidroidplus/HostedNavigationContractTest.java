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

    @Test public void homeArchiveSettingsShareOneWindowAndNavigationBar() throws Exception {
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
        assertTrue(nav.contains("if (!isSelected) navigate(a, destination)"));
        assertTrue(source("HostedSections.kt").contains("class SettingsFragment : Fragment()"));
        assertTrue(source("HostedSections.kt").contains("class ArchiveFragment : Fragment()"));
        assertFalse(source("HostedSections.kt").contains("MainNavigation.attach"));
    }

    @Test public void savedDestinationAndNewIntentAreHandled() throws Exception {
        String home = source("HomeActivity.kt");
        assertTrue(home.contains("outState.putString(\"main_destination\", activeDestination.name)"));
        assertTrue(home.contains("supportFragmentManager.isStateSaved"));
        assertTrue(home.contains("override fun onPostResume()"));
        assertTrue(home.contains("override fun onNewIntent(newIntent: Intent)"));
        assertTrue(home.contains("sectionBack.isEnabled = target != MainNavigation.Destination.HOME"));
        assertTrue(source("MainNavigation.kt").contains("is HomeActivity -> a.activeDestination"));
    }
}
