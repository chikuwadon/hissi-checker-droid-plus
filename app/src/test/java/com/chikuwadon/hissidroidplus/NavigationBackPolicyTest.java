package com.chikuwadon.hissidroidplus;

import org.junit.Test;
import static org.junit.Assert.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.charset.StandardCharsets;

public class NavigationBackPolicyTest {
    private String source(String file) throws Exception {
        return new String(Files.readAllBytes(Paths.get(
            "src/main/java/com/chikuwadon/hissidroidplus/" + file)), StandardCharsets.UTF_8);
    }

    @Test public void connectedResultWithoutTabNavigationReturnsToCaller() {
        assertTrue(NavigationBackPolicy.exitConnectedResult(true, false, false));
        assertTrue(NavigationBackPolicy.exitConnectedResult(true, false, true));
    }

    @Test public void explicitPeerNavigationCanReturnToLiveHost() {
        assertFalse(NavigationBackPolicy.exitConnectedResult(true, true, true));
    }

    @Test public void staleNavigationAfterHostDestructionDoesNotReopenHome() {
        assertTrue(NavigationBackPolicy.exitConnectedResult(true, true, false));
    }

    @Test public void internalAndStandaloneResultsKeepTheirExistingBackRoute() {
        for (boolean navigated : new boolean[]{false, true}) {
            for (boolean host : new boolean[]{false, true}) {
                assertFalse(NavigationBackPolicy.exitConnectedResult(false, navigated, host));
            }
        }
    }

    @Test public void homeBackFinishesRetainedResultBeforeFinishingHome() throws Exception {
        String nav = source("MainNavigation.kt");
        int start = nav.indexOf("fun exitFromHome(a: HomeActivity)");
        int end = nav.indexOf("private fun result", start);
        assertTrue(start >= 0 && end > start);
        String exit = nav.substring(start, end);
        assertTrue(exit.contains("val retained = result(a)"));
        assertTrue(exit.contains("results.remove(a.taskId)"));
        int result = exit.indexOf("retained.finish()");
        int home = exit.indexOf("a.finish()");
        assertTrue(result >= 0 && home > result);
        assertFalse(exit.contains("navigate("));
        assertFalse(exit.contains("finishAffinity("));
        assertFalse(exit.contains("finishAndRemoveTask("));
        String activity = source("HomeActivity.kt");
        assertTrue(activity.contains("MainNavigation.registerHome(this)"));
        assertTrue(activity.contains("MainNavigation.unregisterHome(this)"));
        assertTrue(activity.contains("MainNavigation.exitFromHome(this@HomeActivity)"));
        assertTrue(activity.contains("(active as? HistoryFragment)?.handleBack() == true"));
        assertTrue(source("ResultActivity.kt").contains(
            "NavigationBackPolicy.exitConnectedResult(connected, enteredViaNavigation, MainNavigation.hasHome(this))"));
    }
}
