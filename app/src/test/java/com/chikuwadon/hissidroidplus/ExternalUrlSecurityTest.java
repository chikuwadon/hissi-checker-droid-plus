package com.chikuwadon.hissidroidplus;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.charset.StandardCharsets;
import org.junit.Test;
import static org.junit.Assert.*;

public class ExternalUrlSecurityTest {
    private String source(String name) throws Exception {
        return Files.readString(Paths.get("src/main/java/com/chikuwadon/hissidroidplus/" + name), StandardCharsets.UTF_8);
    }

    @Test public void browserAcceptsNormalWebUrlsButNotNonWebOrAmbiguousUris() {
        assertTrue(ExternalUrlPolicy.isWebUrl("http://hissi.org/read.php/android/20261126/x.html"));
        assertTrue(ExternalUrlPolicy.isWebUrl("https://n2ch.net/r/-/android/1793629388/?guid=ON&rc=14"));
        assertTrue(ExternalUrlPolicy.isWebUrl("https://example.com:8443/path?q=x"));
        for (String raw : new String[]{null, "", "file:///data/local/tmp/test", "content://example/path",
            "javascript:alert(1)", "intent://example/#Intent;end", "https://user@kyodemo.net/",
            "https://", "https:example.com", "https://example.com:99999/", "https://example.com/\n"}) {
            assertFalse(String.valueOf(raw), ExternalUrlPolicy.isWebUrl(raw));
        }
    }

    @Test public void embeddedCheckerOnlyAcceptsExpectedHttpsOrigins() {
        assertTrue(ExternalUrlPolicy.isCheckerWebOrigin("https://www.kyodemo.net/sdemo/b/e_e_liveedge/?bs=hi&k=id%3Ax"));
        assertTrue(ExternalUrlPolicy.isCheckerWebOrigin("https://KYODEMO.NET/sdemo/"));
        for (String raw : new String[]{null, "http://kyodemo.net/", "https://kyodemo.net.evil.example/",
            "https://evil.example/kyodemo.net/", "https://kyodemo.net@evil.example/",
            "https://user@kyodemo.net/", "https://kyodemo.net:443/", "https://sub.kyodemo.net/",
            "file:///test", "about:blank"}) {
            assertFalse(String.valueOf(raw), ExternalUrlPolicy.isCheckerWebOrigin(raw));
        }
    }

    @Test public void externalThreadRoutingUsesValidationBeforeReaderDispatch() throws Exception {
        String t = source("ThreadOpener.kt");
        int start = t.indexOf("fun open(activity: Activity, raw:");
        assertTrue(start >= 0);
        String body = t.substring(start);
        assertTrue(body.indexOf("ExternalUrlPolicy.isWebUrl(raw)") < body.indexOf("validatedThreadUrl(source)"));
        assertFalse(body.contains("launch(activity, viewIntent(normalized, null))"));
        assertTrue(body.contains("BrowserLauncher.open(activity, normalized)"));
        assertTrue(source("BrowserLauncher.kt").contains("ExternalUrlPolicy.isWebUrl(url)"));
    }

    @Test public void webViewChecksOriginAndLifetimeBeforeReadingOrPublishingDom() throws Exception {
        String s = source("ResultActivity.kt");
        assertTrue(s.contains("ExternalUrlPolicy.isCheckerWebOrigin(request.url)"));
        assertTrue(s.contains("ExternalUrlPolicy.isCheckerWebOrigin(incoming.url.toString())"));
        assertTrue(s.contains("ExternalUrlPolicy.isCheckerWebOrigin(url)) return"));
        assertTrue(s.contains("!ExternalUrlPolicy.isCheckerWebOrigin(view.url)"));
        assertTrue(s.contains("mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW"));
        assertTrue(s.contains("safeBrowsingEnabled = true"));
        assertTrue(s.contains("setAcceptThirdPartyCookies(web, false)"));
        assertTrue(s.contains("javaScriptCanOpenWindowsAutomatically = false"));
        assertTrue(s.contains("setSupportMultipleWindows(false)"));
        assertTrue(s.contains("allowFileAccess = false"));
        assertTrue(s.contains("allowContentAccess = false"));
        assertFalse(s.contains("addJavascriptInterface("));
        assertTrue(s.contains("if (view == null || fallbackView !== view) return\n        fallbackView = null"));
    }
}
