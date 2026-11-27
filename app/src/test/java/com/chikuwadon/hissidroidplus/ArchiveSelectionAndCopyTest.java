package com.chikuwadon.hissidroidplus;

import org.junit.Test;
import static org.junit.Assert.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;

public class ArchiveSelectionAndCopyTest {
    private String source(String name) throws Exception {
        return Files.readString(Paths.get("src/main/java/com/chikuwadon/hissidroidplus/" + name), StandardCharsets.UTF_8);
    }
    @Test public void selectionsRestoreByProviderNameAndValidateBoard() {
        assertEquals(ArchiveProvider.FIVECH_NUKO, ArchiveSelectionStore.parseProvider("FIVECH_NUKO"));
        assertEquals(ArchiveProvider.EDGE_ARCHIVE, ArchiveSelectionStore.parseProvider("invalid"));
        ArchiveQuery restored = ArchiveSelectionStore.restoreDraft("livegalileo", "なんでも実況G");
        assertEquals("livegalileo", restored.getBoardCode());
        assertEquals("なんでも実況G", restored.getBoardName());
        assertEquals("", restored.getKeyword());
        assertEquals("", ArchiveSelectionStore.restoreDraft("../bad", "bad").getBoardCode());
        assertEquals("", ArchiveSelectionStore.restoreDraft("", "").getBoardCode());
    }
    @Test public void allProviderCopyLinksUseCanonicalThreads() {
        String nuko = "https://n2ch.net/r/-/livegalileo/1795102868/?guid=ON&rc=2";
        String kyodemo = "https://www.kyodemo.net/sdemo/r/livegalileo/1795102868/";
        String address = "https://nova.5ch.io/livegalileo/";
        String original = "https://nova.5ch.io/test/read.cgi/livegalileo/1795102868/";
        assertEquals("livegalileo", ArchiveOriginalLink.boardCode(nuko));
        assertEquals("livegalileo", ArchiveOriginalLink.boardCode(kyodemo));
        assertEquals(original, ArchiveOriginalLink.nuko5ch(nuko, "livegalileo", address));
        assertEquals(original, ArchiveOriginalLink.kyodemo5ch(kyodemo, "livegalileo", address));
        assertEquals(nuko, ArchiveOriginalLink.nuko5ch(nuko, "livegalileo", "https://evil.example/livegalileo/"));
        assertEquals(nuko, ArchiveOriginalLink.nuko5ch(nuko, "livejupiter", "https://nova.5ch.io/livejupiter/"));
        assertEquals("https://bbs.eddibb.cc/test/read.cgi/liveedge/1795102868/",
            ThreadOpener.validatedThreadUrl("https://www.kyodemo.net/sdemo/r/e_e_liveedge/1795102868/"));
        assertEquals("", ThreadOpener.validated5chThreadUrl(nuko));
    }
    @Test public void uiPersistsImmediatelyAndNeverCopiesUnresolvedArchiveUrl() throws Exception {
        String a = source("ArchiveActivity.kt");
        assertTrue(a.contains("state.provider = ArchiveSelectionStore.provider(host)"));
        assertTrue(a.contains("ArchiveSelectionStore.draft(host, choice)"));
        assertEquals(2, a.split("ArchiveSelectionStore.save\\(host, state.provider, state.draft\\)", -1).length - 1);
        assertTrue(a.contains("val original = entry.originalUrl"));
        assertTrue(a.contains("元スレッドURLを確認できませんでした"));
        assertTrue(a.contains("2 -> original"));
        assertTrue(a.contains("entry.copy(originalUrl = original"));
        assertTrue(a.contains("ThreadOpener.open(host, original, false)"));
        assertTrue(a.contains("検索サービスのページを外部ブラウザで開きますか？"));
    }
    @Test public void nukoNativeUsesScopedRealBrowserUaAndReportsEmptyBody() throws Exception {
        String a = source("ArchiveActivity.kt");
        assertTrue(a.contains("WebSettings.getDefaultUserAgent(host)"));
        assertTrue(a.contains("ArchiveRepository.search(requested, query, page, browserUa)"));
        assertTrue(a.contains("Nuko native success"));
        assertTrue(a.contains("Nuko WebView success"));
        String h = source("HttpTransport.kt");
        assertTrue(h.contains("val requestUserAgent = nukoUserAgent ?: userAgent"));
        assertTrue(h.contains("Content-Length="));
        assertTrue(h.contains("Content-Encoding="));
        String repo = source("ArchiveRepository.kt");
        assertTrue(repo.contains("HttpTransport.getForNuko(target, charset, nukoUserAgent)"));
        assertTrue(repo.contains("html.isEmpty()"));
    }
    @Test public void nukoFallbackIsBoundedAndOnlyUsedForNativeParseFailure() throws Exception {
        String a = source("ArchiveActivity.kt");
        assertTrue(a.contains("failure is NukoPageFormatException"));
        assertTrue(a.contains("nukoBrowser?.close()"));
        String w = source("NukoBrowserFallback.kt");
        assertTrue(w.contains("uri.rawPath == \"/q/-/\""));
        assertTrue(w.contains("handler.postDelayed(timeout, 20000)"));
        assertTrue(w.contains("allowFileAccess = false"));
        assertTrue(w.contains("allowContentAccess = false"));
        assertTrue(w.contains("setAcceptThirdPartyCookies(view, false)"));
        assertFalse(w.contains("addJavascriptInterface"));
        assertTrue(w.contains("request.isForMainFrame"));
        NukoPageFormatException e = assertThrows(NukoPageFormatException.class,
            () -> ArchiveProviderParser.nuko("<form><input name='q'></form>", 1));
        assertTrue(e.getMessage().contains("検索フォーム=true"));
        assertTrue(e.getMessage().contains("スレリンク=0"));
    }
}
