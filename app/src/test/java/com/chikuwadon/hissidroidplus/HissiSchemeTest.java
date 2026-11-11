package com.chikuwadon.hissidroidplus;

import org.junit.Test;
import static org.junit.Assert.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

/** ChMate custom URL contract, independent of Android UI. */
public final class HissiSchemeTest {
    @Test public void recognizes5chHostAndKeepsBoardAndId(){
        HissiScheme q=HissiScheme.parse("hissi:hayabusa9.5ch.net/news4vip/AbC123");
        assertEquals(CheckerQuery.Site.HISSI,q.site);
        assertEquals("news4vip",q.board);
        assertEquals("AbC123",q.id);
        assertEquals("",q.date);
        HissiScheme alternative=HissiScheme.parse("hissi:egg.5ch.io/android/AbC123");
        assertEquals(CheckerQuery.Site.HISSI,alternative.site);
        assertEquals("android",alternative.board);
    }
    @Test public void recognizesEdgeFromHostRatherThanBoardName(){
        HissiScheme q=HissiScheme.parse("hissi:bbs.eddibb.cc/testboard/K%2Fwzi%2BC9v9");
        assertEquals(CheckerQuery.Site.EDGE,q.site);
        assertEquals("testboard",q.board);
        assertEquals("K/wzi+C9v9",q.id);
        assertEquals("",q.date);
        HissiScheme five=HissiScheme.parse("hissi:hayabusa.5ch.net/liveedge/AbC");
        assertEquals(CheckerQuery.Site.HISSI,five.site);
    }
    @Test public void acceptsLiteralPlusAndOptionalDate(){
        HissiScheme q=HissiScheme.parse("hissi:bbs.eddibb.cc/liveedge/ID:Ab+C?date=20261025");
        assertEquals("Ab+C",q.id);
        assertEquals("20261025",q.date);
    }
    @Test(expected=IllegalArgumentException.class)
    public void rejectsSpoofedHost(){
        HissiScheme.parse("hissi:bbs.eddibb.cc.evil.example/liveedge/AbC");
    }
    @Test(expected=IllegalArgumentException.class)
    public void rejectsDifferentScheme(){
        HissiScheme.parse("javascript:bbs.eddibb.cc/liveedge/AbC");
    }
    @Test(expected=IllegalArgumentException.class)
    public void rejectsInvalidBoard(){
        HissiScheme.parse("hissi:bbs.eddibb.cc/../AbC");
    }
    @Test(expected=IllegalArgumentException.class)
    public void rejectsMalformedPercentEncoding(){
        HissiScheme.parse("hissi:bbs.eddibb.cc/liveedge/%0AAbC");
    }
    @Test(expected=IllegalArgumentException.class)
    public void rejectsBadDate(){
        HissiScheme.parse("hissi:hayabusa9.5ch.net/news4vip/AbC?date=20260230");
    }
    @Test public void appIsDeclaredAsChMateSchemeReceiver()throws Exception{
        String xml=new String(Files.readAllBytes(Paths.get("src/main/AndroidManifest.xml")),
                StandardCharsets.UTF_8);
        assertTrue(xml.contains("android:scheme=\"hissi\""));
        assertTrue(xml.contains("android:name=\".HomeActivity\" android:exported=\"true\""));
        assertTrue(xml.contains("android.intent.category.BROWSABLE"));
        String home=new String(Files.readAllBytes(Paths.get(
                "src/main/java/com/chikuwadon/hissidroidplus/HomeActivity.kt")),
                StandardCharsets.UTF_8);
        assertTrue(home.contains("HissiScheme.parse(inbound.dataString)"));
        assertTrue(home.contains("searchHissiRecent(request.board, request.id)"));
        assertTrue(home.contains("openEdgeDirect(request.board, day, request.id, anyDate)"));
    }
}
