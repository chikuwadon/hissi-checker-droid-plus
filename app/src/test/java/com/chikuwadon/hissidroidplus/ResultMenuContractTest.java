package com.chikuwadon.hissidroidplus;

import static org.junit.Assert.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import org.junit.Test;

/** Keep the entry visible for both 5ch and Edge without changing menu offsets. */
public final class ResultMenuContractTest {
    @Test public void homeImmediatelyFollowsHistoryForBothSites() throws Exception {
        String code=new String(Files.readAllBytes(Paths.get(
                "src/main/java/com/chikuwadon/hissidroidplus/ResultActivity.java")),
                StandardCharsets.UTF_8);
        String adjacent="\"検索履歴\",\"ホーム画面に戻る\"";
        assertEquals(2,code.split(java.util.regex.Pattern.quote(adjacent),-1).length-1);
        assertTrue(code.contains("else if(which==1)openHome();"));
        assertTrue(code.contains("if(edge&&which==2)openEdgeAnalysis();"));
        assertTrue(code.contains("else if(action==2)openExternalBrowser("));
        assertTrue(code.contains("Intent.FLAG_ACTIVITY_NEW_TASK|"));
        assertTrue(code.contains("Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP"));
    }
}
