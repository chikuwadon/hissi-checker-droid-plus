package com.chikuwadon.hissidroidplus;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.charset.StandardCharsets;
import org.junit.Test;
import static org.junit.Assert.*;

public class AboutAndDocumentationTest {
    private String read(String path) throws Exception {
        return Files.readString(Paths.get(path), StandardCharsets.UTF_8);
    }
    @Test public void aboutUsesRuntimeVersionAndProviderLabels() throws Exception {
        String menu = read("src/main/java/com/chikuwadon/hissidroidplus/ThemedMenu.kt");
        assertTrue(menu.contains("PackageQueries.packageInfo(a.packageManager, a.packageName).versionName"));
        assertTrue(menu.contains("ArchiveProvider.entries.joinToString"));
        assertFalse(menu.contains("\uFF0F"));
        assertFalse(read("src/main/java/com/chikuwadon/hissidroidplus/ResultActivity.kt").contains("\uFF0F"));
        assertTrue(menu.contains("日付指定あり / なし"));
        assertTrue(menu.contains("Kotlin / AndroidX"));
        assertTrue(menu.contains("履歴保存件数（100～10,000件・無制限）"));
        assertTrue(menu.contains("過去ログ検索欄の上下配置"));
        assertTrue(menu.contains("Android 8.0以降"));
        assertTrue(menu.contains("/blob/main/PRIVACY.md"));
        assertTrue(menu.contains("/blob/main/CHANGELOG.md"));
    }
    @Test public void docsMatchOfficialRelease() throws Exception {
        String readme = read("../README.md");
        String changelog = read("../CHANGELOG.md");
        String privacy = read("../PRIVACY.md");
        assertTrue(readme.contains("/releases/tag/v1.1.0"));
        assertFalse(readme.contains("開発中のv1.1.0"));
        assertTrue(changelog.contains("## v1.1.0\n"));
        assertTrue(readme.contains("HissiCheckerDroidPlus-v1.1.0.apk"));
        assertTrue(readme.contains("100～10,000件"));
        assertTrue(privacy.contains("100～10,000件"));
        assertTrue(privacy.contains("eddiarchive3rd.boy.jp"));
        assertTrue(privacy.contains("n2ch.net"));
        for (ArchiveProvider provider : ArchiveProvider.values())
            assertTrue(provider.getLabel(), readme.contains(provider.getLabel()));
    }
}
