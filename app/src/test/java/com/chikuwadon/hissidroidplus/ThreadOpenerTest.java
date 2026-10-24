package com.chikuwadon.hissidroidplus;

import static org.junit.Assert.*;
import org.junit.Test;
import java.util.List;
import java.util.Arrays;

public final class ThreadOpenerTest {
    @Test public void validatedCandidatesRetainPostNumber(){
        List<String> urls=ThreadOpener.chMateUrls(
            EdgeThreadLink.original("https://www.kyodemo.net/sdemo/r/e_e_liveedge/1792741575/555"));
        assertEquals(4,urls.size());
        assertEquals("http://bbs.eddibb.cc/liveedge/1792741575/555",urls.get(0));
        assertEquals("https://bbs.eddibb.cc/test/read.cgi/liveedge/1792741575/555",urls.get(3));
    }
    @Test public void canonicalUrlIsPassedToExplicitResListActivity(){
        String expected="https://bbs.eddibb.cc/test/read.cgi/liveedge/1792741575/555";
        assertEquals(expected,ThreadOpener.validatedThreadUrl(
            "https://www.kyodemo.net/sdemo/r/e_e_liveedge/1792741575/555"));
        assertEquals(expected,ThreadOpener.validatedThreadUrl(
            "https://bbs.eddibb.cc/liveedge/1792741575/555"));
        assertEquals("https://bbs.eddibb.cc/test/read.cgi/liveedge/1792741575/",
            ThreadOpener.validatedThreadUrl(
                "https://bbs.eddibb.cc/liveedge/1792741575"));
    }
    @Test public void unknownHostsAreRejectedFromExplicitChMateLaunch(){
        assertEquals("",ThreadOpener.validatedThreadUrl(
            "https://other.example/test/read.cgi/liveedge/1792741575/"));
        assertEquals("",ThreadOpener.validatedThreadUrl(
            "https://bbs.eddibb.cc/invalid"));
    }
    @Test public void readerSelectionUsesInstalledApplicationsOnly(){
        String mate="jp.co.airfront.android.a2chMate";
        String haiagaru="jp.co.airfront.android.a2chMate.haiagaru";
        assertEquals(Arrays.asList(mate,haiagaru),ThreadOpener.readerPackages(true,true));
        assertEquals(Arrays.asList(mate),ThreadOpener.readerPackages(true,false));
        assertEquals(Arrays.asList(haiagaru),ThreadOpener.readerPackages(false,true));
        assertTrue(ThreadOpener.readerPackages(false,false).isEmpty());
    }

    @Test public void androidManifestQueriesBothKnownReaderPackages() throws Exception {
        String manifest=new String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(
            "src/main/AndroidManifest.xml")),java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(manifest.contains("jp.co.airfront.android.a2chMate"));
        assertTrue(manifest.contains("jp.co.airfront.android.a2chMate.haiagaru"));
    }

    @Test public void validatesWithoutUnknownHost(){
        assertTrue(ThreadOpener.chMateUrls("https://unknown.example/test/read.cgi/liveedge/1792741575/").isEmpty());
        assertTrue(ThreadOpener.chMateUrls("https://hissi.org/read.php/android/20261023/x.html").isEmpty());
        assertTrue(ThreadOpener.chMateUrls("https://bbs.eddibb.cc/other").isEmpty());
    }
}
