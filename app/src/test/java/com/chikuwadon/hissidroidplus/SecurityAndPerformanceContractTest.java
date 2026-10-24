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
        String transport=code("HttpTransport.java");
        assertTrue(transport.contains("static final CookieJar WEBVIEW_COOKIES"));
        assertTrue(transport.contains("enableWebViewCookies()"));
        for(String file:new String[]{"ResultActivity.java","EdgeSearchActivity.java"}){
            String source=code(file);
            assertTrue(source.contains("HttpTransport.enableWebViewCookies();"));
            assertFalse(source.contains("HttpTransport.setCookieJar(new"));
        }
    }
    @Test public void edgeSearchRecyclesListRows() throws Exception {
        String activity=code("EdgeSearchActivity.java");
        assertTrue(activity.contains("reuse instanceof LinearLayout"));
        assertTrue(activity.contains("box.setTag(row)"));
        assertTrue(activity.contains("row.title.setText(title)"));
    }
    @Test public void historyAvoidsRecreatingRowsAndFavoriteReads() throws Exception {
        String history=code("HistoryActivity.java");
        assertTrue(history.contains("convert.getTag() instanceof HistoryRow"));
        assertTrue(history.contains("favoriteUrls.contains(entry.url)"));
        assertTrue(history.contains("favoriteUrls.clear()"));
        assertTrue(history.contains("List<SearchHistory.Entry> favorites=SearchHistory.favorites(this)"));
        assertFalse(history.contains("SearchHistory.isFavorite(HistoryActivity.this,entry.url)"));
    }
    @Test public void analysisHtmlParsingUsesWorkerAndDropsStaleResponses() throws Exception {
        String source=code("EdgeAnalysisActivity.java");
        assertTrue(source.contains("parserWorker.execute(()->{"));
        assertTrue(source.contains("EdgeAnalysis.parse(html)"));
        assertTrue(source.contains("requestedCycle!=cycle"));
        assertTrue(source.contains("parserWorker.shutdownNow()"));
    }
    @Test public void manifestDeclaresOnlyInternetPermission() throws Exception {
        String manifest=new String(Files.readAllBytes(Paths.get(
                "src/main/AndroidManifest.xml")),StandardCharsets.UTF_8);
        assertEquals(1,manifest.split("<uses-permission ",-1).length-1);
        assertTrue(manifest.contains("android.permission.INTERNET"));
        assertFalse(manifest.contains("android:debuggable=\\\"true\\\""));
    }
    @Test public void fallbackHtmlParsingDoesNotBlockMainThread() throws Exception {
        String activity=code("ResultActivity.java");
        assertTrue(activity.contains("worker.execute(()->{"));
        assertTrue(activity.contains("EdgeAdapter.parse(html,query)"));
        assertTrue(activity.contains("parsing[0]=true"));
        assertTrue(activity.contains("json.length()>12*1024*1024"));
        assertTrue(activity.contains("runOnUiThread(()->{"));
    }
}
