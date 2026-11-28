package com.chikuwadon.hissidroidplus;

import org.junit.Test;
import static org.junit.Assert.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.charset.StandardCharsets;

public class ResultStateContractTest {
    private String source(String name) throws Exception {
        return new String(Files.readAllBytes(Paths.get("src/main/java/com/chikuwadon/hissidroidplus/" + name)), StandardCharsets.UTF_8);
    }

    @Test public void retainedModelKeepsPaginationWithoutActivityReferences() throws Exception {
        ResultScreenState state = new ResultScreenState();
        CheckerQuery query = CheckerQuery.hissi("news4vip", "20261126", "example");
        CheckerResult result = new CheckerResult();
        result.posts.add(new CheckerResult.Post("title", "name", "meta", "body", "link"));
        state.setSnapshot(new ResultScreenState.Snapshot(query, result, 3, false, "ok"));
        assertSame(query, state.getSnapshot().getQuery());
        assertSame(result, state.getSnapshot().getResult());
        assertEquals(3, state.getSnapshot().getNextPage());
        assertFalse(state.getSnapshot().getNoMorePages());
        String vm = source("ResultScreenState.kt");
        assertFalse(vm.contains("import android.app.Activity"));
        assertFalse(vm.contains("import android.view."));
    }

    @Test public void restoreOnlySmallUiStateAndReuseModelBeforeNetwork() throws Exception {
        String code = source("ResultActivity.kt");
        assertTrue(code.contains("ViewModelProvider(this)[ResultScreenState::class.java]"));
        assertTrue(code.contains("val retained = if (state != null) screenState.snapshot else null"));
        int start = code.indexOf("if (retained != null)");
        int end = code.indexOf("status.text = if (initial.site", start);
        assertTrue(start >= 0 && end > start);
        String reuse = code.substring(start, end);
        assertTrue(reuse.contains("result = retained.result"));
        assertTrue(reuse.contains("showTabContents(tab)"));
        assertTrue(reuse.contains("return"));
        assertFalse(reuse.contains("SearchHistory.record"));
        assertTrue(code.contains("outState.putIntArray(\"result_positions\", positions.copyOf())"));
        assertTrue(code.contains("outState.putIntArray(\"result_offsets\", offsets.copyOf())"));
        assertTrue(code.contains("restoreNavigationState(state)"));
        assertTrue(code.contains("if (MainNavigation.backToPrevious(this, MainNavigation.Destination.RESULT)) return"));
        assertTrue(code.contains("MainNavigation.exitFromResult(this)"));
        assertTrue(code.contains("state == null && routeStandaloneLinkToOwnTask()"));
    }
}
