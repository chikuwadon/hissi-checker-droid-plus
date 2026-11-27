package com.chikuwadon.hissidroidplus;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import org.junit.Test;
import static org.junit.Assert.*;

public final class DevelopmentVersionContractTest {
    private static String projectFile(String path) throws Exception {
        return new String(Files.readAllBytes(Paths.get("../" + path)),
            StandardCharsets.UTF_8);
    }

    @Test public void qaVersionIsNewerThanPublished103() throws Exception {
        String gradle = projectFile("app/build.gradle");
        assertTrue(gradle.contains("versionCode = 79"));
        assertTrue(gradle.contains("versionName = '1.1.0-dev.34'"));
        String menu = projectFile(
            "app/src/main/java/com/chikuwadon/hissidroidplus/ThemedMenu.kt");
        assertTrue(menu.contains("PackageQueries.packageInfo(a.packageManager, a.packageName).versionName"));
        assertTrue(menu.contains("Hissi Checker Droid Plus  /  v"));
    }

    @Test public void artifactContainsVersionedApkFilenameNotAppRelease() throws Exception {
        String workflow = projectFile(".github/workflows/public-ci.yml");
        String filename = "hissi-checker-droid-plus-v1.1.0-dev.34.apk";
        assertTrue(workflow.contains("name: hissi-checker-droid-plus-v1.1.0-dev.34-signed"));
        assertTrue(workflow.contains("app/build/outputs/apk/release/" + filename));
        assertTrue(workflow.contains("mv app/build/outputs/apk/release/app-release.apk"));
        assertTrue(workflow.contains("apksigner\" verify --verbose"));
    }
}
