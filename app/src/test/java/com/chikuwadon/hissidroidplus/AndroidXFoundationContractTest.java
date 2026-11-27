package com.chikuwadon.hissidroidplus;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.charset.StandardCharsets;
import org.junit.Test;
import static org.junit.Assert.*;
public class AndroidXFoundationContractTest {
    private String source(String name) throws Exception {
        return new String(Files.readAllBytes(Paths.get("src/main/java/com/chikuwadon/hissidroidplus/" + name)), StandardCharsets.UTF_8);
    }
    @Test public void screensUseSharedLifecycleAndInsets() throws Exception {
        String base = source("CheckerActivity.kt");
        assertTrue(base.contains("FragmentActivity()"));
        assertTrue(base.contains("onBackPressedDispatcher.addCallback(this"));
        assertTrue(base.contains("ViewCompat.setOnApplyWindowInsetsListener"));
        assertTrue(base.contains("if (Build.VERSION.SDK_INT < 30) return"));
        for (String screen : new String[]{"HomeActivity.kt","ResultActivity.kt","HistoryActivity.kt","MainSectionActivity.kt","EdgeAnalysisActivity.kt"}) {
            String s = source(screen);
            assertTrue(screen, s.contains(": CheckerActivity()"));
            assertTrue(screen, s.contains("applyContentInsets("));
            assertFalse(screen, s.contains("onBackInvokedDispatcher.registerOnBackInvokedCallback"));
        }
    }
    @Test public void historyViewModelIsIndependentOfActivityAndPreservesSelection() throws Exception {
        HistoryScreenState state = new HistoryScreenState();
        state.setSelection(true);
        state.getChosen().add("test-url");
        state.setSort(2);
        assertTrue(state.getSelection());
        assertTrue(state.getChosen().contains("test-url"));
        assertEquals(2, state.getSort());
        assertTrue(source("HistoryActivity.kt").contains("ViewModelProvider(this)[HistoryScreenState::class.java]"));
        assertFalse(source("HistoryScreenState.kt").contains("import android.app.Activity"));
    }
}
