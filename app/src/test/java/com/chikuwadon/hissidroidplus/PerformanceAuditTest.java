package com.chikuwadon.hissidroidplus;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.charset.StandardCharsets;
import org.junit.Test;
import static org.junit.Assert.*;

public class PerformanceAuditTest {
    private String source(String name) throws Exception {
        return Files.readString(Paths.get("src/main/java/com/chikuwadon/hissidroidplus/" + name), StandardCharsets.UTF_8);
    }

    @Test public void edgeDiagnosticsReuseParsedDocumentAndAnchorList() throws Exception {
        String s = source("EdgeAdapter.kt");
        assertTrue(s.contains("result.diagnostic = describeDocument(doc, html.length)"));
        assertTrue(s.contains("if (it.diagnostic.isEmpty()) it.diagnostic = describePage(html)"));
        assertTrue(s.contains("val anchors = doc.select(\"a[href]\")"));
        assertTrue(s.contains("var thread = anchors.count"));
        assertTrue(s.contains("thread = anchors.count"));
        String diag = EdgeAdapter.describePage("<html><body><a href='https://bbs.eddibb.cc/test/read.cgi/liveedge/1792500781/'>thread</a><p>captcha</p></body></html>");
        assertTrue(diag.contains("リンク=1"));
        assertTrue(diag.contains("スレリンク=1"));
        assertTrue(diag.contains("アクセス制限の疑い"));
        assertEquals("受信内容なし", EdgeAdapter.describePage(""));
    }

    @Test public void chartReusesSummaryUntilResultIndexChanges() throws Exception {
        String s = source("ResultActivity.kt");
        assertTrue(s.contains("val dated = chartDated ?: run"));
        assertTrue(s.contains("val counts = result.hourlyCounts"));
        assertTrue(s.contains("chart.setCounts(counts)"));
        assertTrue(s.contains("} else chart.setPosts(result.posts)"));
        assertTrue(s.contains("chartDated = computed"));
        int start = s.indexOf("private fun rebuildIndexes()");
        int end = s.indexOf("private fun applySystemInsets()", start);
        assertTrue(start >= 0 && end > start);
        assertTrue(s.substring(start, end).contains("chartDated = null"));
    }

    @Test public void archiveAndTransportAvoidDuplicateTextScans() throws Exception {
        String a = source("ArchiveProviderParser.kt");
        assertTrue(a.contains("val plain = normalizedNukoText(doc.text())"));
        assertTrue(a.contains("nukoCount.find(plain)"));
        assertTrue(a.contains("nukoRange.find(plain)"));
        String t = source("HttpTransport.kt");
        assertTrue(t.contains("val hasHeaderCharset = headerCharset?.find() == true"));
        assertTrue(t.contains("headerCharset!!.group(1)"));
        assertTrue(t.contains("data.size.coerceAtMost(3072)"));
        assertTrue(t.contains("HttpResponseReader.read(input, LIMIT, connection.contentLengthLong)"));
    }
}
