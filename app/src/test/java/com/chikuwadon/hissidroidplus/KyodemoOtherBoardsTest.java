package com.chikuwadon.hissidroidplus;
import org.junit.Test;
import static org.junit.Assert.*;

public class KyodemoOtherBoardsTest {
    @Test public void menuAcceptsOnlyExpectedServiceAndTrustedLinks() {
        KyodemoOtherBoards.Service m = KyodemoOtherBoards.services.get(0);
        KyodemoOtherBoards.Menu menu = KyodemoOtherBoards.parse(m, "m_",
            "<a href='/sdemo/b/m_tokyo/'>東京</a><a href='/sdemo/b/m_tokyo/'>重複</a>" +
            "<a href='https://evil.example/sdemo/b/m_tama/'>偽</a><a href='/sdemo/b/f_b/'>別サービス</a>");
        assertEquals(1, menu.getBoards().size());
        assertEquals("m_tokyo", menu.getBoards().get(0).code);
        KyodemoOtherBoards.Service s = KyodemoOtherBoards.services.get(3);
        assertEquals("s_music", KyodemoOtherBoards.parse(s, "s_", "<a href='/sdemo/k/s_music'>音楽</a>").getCategories().get(0).getCode());
        assertFalse(KyodemoOtherBoards.validBoard("e_e_liveedge"));
        assertFalse(KyodemoOtherBoards.validBoard("../bad"));
    }
    @Test public void otherBoardSearchUsesScopedPath() {
        ArchiveQuery q = new ArchiveQuery("Android", "", "", "", "", "", "new", "", false, false, "m_tokyo", "東京");
        assertTrue(ArchiveExternalSearch.url(ArchiveProvider.OTHER_KYODEMO, q, 2).startsWith("https://www.kyodemo.net/sdemo/b/m_tokyo/51?"));
    }
}
