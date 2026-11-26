package com.chikuwadon.hissidroidplus;

import static org.junit.Assert.*;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import org.junit.Test;

/** Network allowlist, lifecycle and list recycling invariants. */
public final class SecurityAndPerformanceContractTest {
    private static void checkAllowed(String url,boolean expected) throws Exception {
        Method validate=HttpTransport.class.getDeclaredMethod("validate",URL.class);
        validate.setAccessible(true);
        boolean accepted=true;
        try{validate.invoke(null,new URL(url));}
        catch(InvocationTargetException error){
            if(!(error.getCause() instanceof IOException))throw error;
            accepted=false;
        }
        assertEquals(url,expected,accepted);
    }
    @Test public void outboundHttpIsRestrictedToRequiredOrigins() throws Exception {
        checkAllowed("https://www.kyodemo.net/sdemo/",true);
        checkAllowed("https://kyodemo.net/sdemo/",true);
        checkAllowed("http://hissi.org/read.php/android/20261024/id.html",true);
        checkAllowed("https://hissi.org/read.php/android/20261024/id.html",true);
        checkAllowed("https://menu.5ch.io/bbsmenu.json",true);
        checkAllowed("https://eddiarchive3rd.boy.jp/?keyword=Android",true);
        checkAllowed("http://eddiarchive3rd.boy.jp/",false);
        checkAllowed("https://eddiarchive3rd.boy.jp/other",false);
        checkAllowed("https://eddiarchive3rd.boy.jp.evil.example/",false);
        checkAllowed("https://example.com/",false);
        checkAllowed("https://evilkyodemo.net/sdemo/",false);
        checkAllowed("https://kyodemo.net.evil.example/sdemo/",false);
        checkAllowed("http://kyodemo.net/sdemo/",false);
        checkAllowed("https://menu.5ch.io:444/bbsmenu.json",false);
        checkAllowed("https://user@hissi.org/read.php/a/",false);
        checkAllowed("file:///etc/passwd",false);
    }

    private static String code(String filename) throws Exception {
        return new String(Files.readAllBytes(Paths.get(
            "src/main/java/com/chikuwadon/hissidroidplus/"+filename)),
            StandardCharsets.UTF_8);
    }
    @Test public void httpCookieBridgeDoesNotCaptureAnActivity() throws Exception {
        String transport=code("HttpTransport.kt");
        assertTrue(transport.contains("private val WEBVIEW_COOKIES: CookieJar"));
        assertTrue(transport.contains("enableWebViewCookies()"));
        for(String file:new String[]{"ResultActivity.kt"}){
            String source=code(file);
            assertTrue(source.contains("HttpTransport.enableWebViewCookies()"));
            assertFalse(source.contains("HttpTransport.setCookieJar(new"));
        }
    }
    @Test public void historyAvoidsRecreatingRowsAndFavoriteReads() throws Exception {
        String history=code("HistoryActivity.kt");
        assertTrue(history.contains("convert.tag is HistoryRow"));
        assertTrue(history.contains("favoriteUrls.contains(entry.url)"));
        assertTrue(history.contains("favoriteUrls.clear()"));
        assertTrue(history.contains("val favorites = SearchHistory.favorites(host)"));
        assertFalse(history.contains("SearchHistory.isFavorite(HistoryActivity.this,entry.url)"));
    }
    @Test public void postReadsRequestHeadersBeforeConnecting() throws Exception {
        String transport = code("HttpTransport.kt");
        int headerRead = transport.indexOf("val hadCookie = connection.getRequestProperty");
        int output = transport.indexOf("connection.outputStream.use");
        assertTrue(headerRead >= 0 && output > headerRead);
        assertEquals(1, transport.split("getRequestProperty", -1).length - 1);
    }
    @Test public void historySelectionDoesNotReloadOrSortStoredHistory() throws Exception {
        String history = code("HistoryActivity.kt");
        int start = history.indexOf("private fun toggleSelection");
        int end = history.indexOf("private fun confirmSelected", start);
        assertTrue(start >= 0 && end > start);
        String toggle = history.substring(start, end);
        assertTrue(toggle.contains("updateSelectionUi()"));
        assertFalse(toggle.contains("refresh()"));
        assertFalse(history.contains("holder.root.background = RippleDrawable("));
    }
    @Test public void multitouchDoesNotTriggerResultTapOrLongPress() throws Exception {
        String selectable = code("SelectableResultTextView.kt");
        int start = selectable.indexOf("MotionEvent.ACTION_POINTER_DOWN ->");
        int end = selectable.indexOf("MotionEvent.ACTION_UP ->", start);
        assertTrue(start >= 0 && end > start);
        String pointer = selectable.substring(start, end);
        assertTrue(pointer.contains("moved = true"));
        assertTrue(pointer.contains("clearPending()"));
    }
    @Test public void analysisHtmlParsingUsesWorkerAndDropsStaleResponses() throws Exception {
        String source=code("EdgeAnalysisActivity.kt");
        assertTrue(source.contains("parserWorker.execute {"));
        assertTrue(source.contains("EdgeAnalysis.parse(html)"));
        assertTrue(source.contains("requestedCycle != cycle"));
        assertTrue(source.contains("parserWorker.shutdownNow()"));
    }
    @Test public void manifestDeclaresOnlyInternetPermission() throws Exception {
        String manifest=new String(Files.readAllBytes(Paths.get(
                "src/main/AndroidManifest.xml")),StandardCharsets.UTF_8);
        assertEquals(1,manifest.split("<uses-permission ",-1).length-1);
        assertTrue(manifest.contains("android.permission.INTERNET"));
        assertFalse(manifest.contains("android:debuggable=\"true\""));
    }
    @Test public void fallbackHtmlParsingDoesNotBlockMainThread() throws Exception {
        String activity=code("ResultActivity.kt");
        assertTrue(activity.contains("worker.execute {"));
        assertTrue(activity.contains("EdgeAdapter.parse(html, request)"));
        assertTrue(activity.contains("parsing[0] = true"));
        assertTrue(activity.contains("json.length > 12 * 1024 * 1024"));
        assertTrue(activity.contains("runOnUiThread {"));
    }
}
