package com.chikuwadon.hissidroidplus;

import org.junit.Test;
import static org.junit.Assert.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

/** Verify API-specific URL filters do not regress to claiming every Edge page. */
public final class EdgeDeepLinkRoutingContractTest {
    private static String source(String path)throws Exception{
        return new String(Files.readAllBytes(Paths.get("src/main/"+path)),StandardCharsets.UTF_8);
    }

    @Test public void modernHandlerFiltersByIdQueryParameters()throws Exception{
        String xml=source("AndroidManifest.xml");
        assertTrue(xml.contains("android:name=\".FilteredEdgeLinkActivity\""));
        assertTrue(xml.contains("android:enabled=\"@bool/enable_filtered_edge_links\""));
        assertTrue(xml.contains("<uri-relative-filter-group android:allow=\"true\">"));
        assertTrue(xml.contains("android:query=\"bs=hi\""));
        assertTrue(xml.contains("android:queryPrefix=\"k=id:\""));
        assertTrue(xml.contains("android:queryPrefix=\"hi=\""));
        assertTrue(xml.contains("android:queryPrefix=\"key=\""));
        assertTrue(xml.contains("android:queryPrefix=\"date=\""));
        assertTrue(xml.contains("android:path=\"/__not_a_checker_page__\""));
        assertTrue(xml.contains("android:pathPrefix=\"/sdemo/b/e_e_\""));
        assertFalse(xml.contains("android:pathPrefix=\"/sdemo/r/\""));
    }

    @Test public void apiSpecificFlagsDisableBroadRoutingOnAndroid15()throws Exception{
        String oldFlags=source("res/values/edge_link_handlers.xml");
        String modernFlags=source("res/values-v35/edge_link_handlers.xml");
        assertTrue(oldFlags.contains("name=\"enable_legacy_edge_links\">true"));
        assertTrue(oldFlags.contains("name=\"enable_filtered_edge_links\">false"));
        assertTrue(modernFlags.contains("name=\"enable_legacy_edge_links\">false"));
        assertTrue(modernFlags.contains("name=\"enable_filtered_edge_links\">true"));
    }

    @Test public void oldAndroidRoutesNonCheckerUrlsToBrowserWithoutOpeningResultUi()throws Exception{
        String activity=new String(Files.readAllBytes(Paths.get(
                "src/main/java/com/chikuwadon/hissidroidplus/ResultActivity.java")),
                StandardCharsets.UTF_8);
        assertTrue(activity.contains("if (redirectNonCheckerWebLink()) return;"));
        assertTrue(activity.contains("CheckerQuery.parseUrl(url)"));
        assertTrue(activity.contains("BrowserLauncher.open(this,url)"));
        assertTrue(activity.contains("finish();"));
    }
}
