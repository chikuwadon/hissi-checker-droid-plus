package com.chikuwadon.hissidroidplus;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.charset.StandardCharsets;
import org.junit.Test;
import static org.junit.Assert.*;

/** Source contracts for platform-version dispatch; device integration still needs QA. */
public class PlatformApiCompatibilityTest {
    private String source(String name) throws Exception {
        return Files.readString(Paths.get("src/main/java/com/chikuwadon/hissidroidplus/" + name), StandardCharsets.UTF_8);
    }

    @Test public void packageQueriesUseTypedFlagsWithLegacyFallback() throws Exception {
        String q = source("PackageQueries.kt");
        assertEquals(3, q.split("Build.VERSION.SDK_INT >= 33", -1).length - 1);
        assertTrue(q.contains("pm.getPackageInfo(name, PackageManager.PackageInfoFlags.of(0L))"));
        assertTrue(q.contains("pm.getPackageInfo(name, 0)"));
        assertTrue(q.contains("pm.resolveActivity(intent, PackageManager.ResolveInfoFlags.of(flags.toLong()))"));
        assertTrue(q.contains("pm.resolveActivity(intent, flags)"));
        assertTrue(q.contains("pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(flags.toLong()))"));
        assertTrue(q.contains("pm.queryIntentActivities(intent, flags)"));
    }

    @Test public void browserAndReaderDiscoveryKeepExistingMatchFlags() throws Exception {
        assertTrue(source("ThreadOpener.kt").contains("PackageQueries.packageInfo(pm, pkg)"));
        String browser = source("BrowserLauncher.kt");
        assertTrue(browser.contains("PackageQueries.resolve(pm, generic, PackageManager.MATCH_DEFAULT_ONLY)"));
        assertTrue(browser.contains("PackageQueries.query(pm, generic, 0)"));
        String external = source("ExternalThreadApps.kt");
        assertTrue(external.contains("PackageQueries.query(pm, view(url), PackageManager.MATCH_DEFAULT_ONLY)"));
        assertTrue(external.contains("if (!activity.exported) continue"));
        assertTrue(external.contains("Intent.FLAG_ACTIVITY_REQUIRE_NON_BROWSER"));
    }

    @Test public void systemBarAppearanceUsesCompatControllerWithoutResettingOtherFlags() throws Exception {
        String settings = source("AppSettings.kt");
        assertTrue(settings.contains("WindowCompat.getInsetsController(activity.window, activity.window.decorView)"));
        assertTrue(settings.contains("controller.isAppearanceLightStatusBars = light"));
        assertTrue(settings.contains("controller.isAppearanceLightNavigationBars = light"));
        assertFalse(settings.contains("systemUiVisibility"));
        assertTrue(settings.contains("configureBarBackgrounds(activity)"));
        assertTrue(settings.contains("activity.window.statusBarColor = activity.getColor(R.color.surface)"));
        assertTrue(settings.contains("activity.window.navigationBarColor = activity.getColor(R.color.surface)"));
    }
}
