package com.chikuwadon.hissidroidplus;

import org.junit.Test;
import static org.junit.Assert.*;

public class ResultRepositoryTest {
    private static final CheckerQuery HISSI = CheckerQuery.hissi("news4vip", "20261126", "example");
    private static final CheckerQuery INBOUND = CheckerQuery.edgeSearch("liveedge", "20261126", "example");
    private static final CheckerQuery EDGE = CheckerQuery.parseUrl(
        "https://www.kyodemo.net/sdemo/b/e_e_liveedge/?hi=example&key=1792726415&date=20261126");
    private static CheckerResult.Post post(String day, String id, String link) {
        return new CheckerResult.Post("title", "author", "1 2026/11/" + day + " 12:00:00 ID:" + id, "text", link);
    }
    private static CheckerResult result(CheckerResult.Post... posts) {
        CheckerResult r = new CheckerResult();
        java.util.Collections.addAll(r.posts, posts);
        return r;
    }
    private static class Fake implements ResultRepository.Transport {
        CheckerQuery resolved = EDGE;
        CheckerQuery loaded;
        CheckerResult data = result();
        RuntimeException resolutionError, loadError;
        String pageUrl;
        int pageNumber;
        @Override public CheckerQuery resolveEdge(CheckerQuery q) {
            if (resolutionError != null) throw resolutionError;
            return resolved;
        }
        @Override public CheckerResult load(CheckerQuery q) {
            loaded = q;
            if (loadError != null) throw loadError;
            return data;
        }
        @Override public CheckerResult nextPage(String url, int page) {
            pageUrl = url;
            pageNumber = page;
            return data;
        }
        @Override public String diagnostic() { return "network detail"; }
    }
    @Test public void hissiDoesNotApplyEdgeDateFilter() {
        Fake f = new Fake();
        f.data = result(post("25", "other", "link"));
        ResultRepository.Load.Ready ready = (ResultRepository.Load.Ready)
            new ResultRepository(f).load(HISSI, true);
        assertSame(HISSI, f.loaded);
        assertSame(f.data, ready.getResult());
    }
    @Test public void inboundEdgeResolvesAndFiltersDateAndId() {
        Fake f = new Fake();
        CheckerResult.Post correct = post("26", "example", "link1");
        f.data = result(correct, post("25", "example", "link2"), post("26", "other", "link3"));
        ResultRepository.Load.Ready ready = (ResultRepository.Load.Ready)
            new ResultRepository(f).load(INBOUND, false);
        assertSame(EDGE, f.loaded);
        assertSame(EDGE, ready.getQuery());
        assertEquals(1, ready.getResult().posts.size());
        assertSame(correct, ready.getResult().posts.get(0));
    }
    @Test public void wrongDayDoesNotFallBackToUnfilteredPosts() {
        Fake f = new Fake();
        f.data = result(post("25", "example", "link"));
        assertTrue(new ResultRepository(f).load(EDGE, true) instanceof ResultRepository.Load.WrongDay);
        assertTrue(new ResultRepository(f).load(EDGE, false) instanceof ResultRepository.Load.Ready);
    }
    @Test public void unresolvedEdgeDoesNotFetchOrFallback() {
        Fake f = new Fake();
        f.resolved = null;
        assertTrue(new ResultRepository(f).load(INBOUND, true) instanceof ResultRepository.Load.NotFound);
        assertNull(f.loaded);
        f.resolutionError = new IllegalStateException("resolve failure");
        ResultRepository.Load.Failed failure = (ResultRepository.Load.Failed)
            new ResultRepository(f).load(INBOUND, true);
        assertTrue(failure.getResolvingEdge());
        assertEquals("resolve failure", failure.getMessage());
    }
    @Test public void emptyEdgeAndNetworkFailureUseValidatedFallback() {
        Fake f = new Fake();
        f.data.diagnostic = "empty parser";
        ResultRepository.Load.WebFallback empty = (ResultRepository.Load.WebFallback)
            new ResultRepository(f).load(EDGE, false);
        assertSame(EDGE, empty.getQuery());
        assertEquals("empty parser", empty.getDiagnostic());
        f.loadError = new IllegalStateException("network failure");
        ResultRepository.Load.WebFallback fallback = (ResultRepository.Load.WebFallback)
            new ResultRepository(f).load(INBOUND, true);
        assertSame(EDGE, fallback.getQuery());
        assertTrue(fallback.getReason().contains("network detail"));
        ResultRepository.Load.Failed failed = (ResultRepository.Load.Failed)
            new ResultRepository(f).load(HISSI, false);
        assertFalse(failed.getResolvingEdge());
    }
    @Test public void paginationKeepsOrderAndAvoidsConcatenationCollisions() {
        CheckerResult.Post first = new CheckerResult.Post("", "", "bc", "", "a");
        CheckerResult.Post collision = new CheckerResult.Post("", "", "c", "", "ab");
        CheckerResult current = result(first);
        Fake f = new Fake();
        f.data = result(first, collision, collision);
        ResultRepository repository = new ResultRepository(f);
        assertTrue(ResultRepository.appendPage(current, repository.nextPage("source", 2)));
        assertEquals("source", f.pageUrl);
        assertEquals(2, f.pageNumber);
        assertEquals(java.util.Arrays.asList(first, collision), current.posts);
        assertFalse(ResultRepository.appendPage(current, f.data));
        assertFalse(ResultRepository.appendPage(current, result()));
    }
}
