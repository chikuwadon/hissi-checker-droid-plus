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

    @Test public void observedRootMenuLinksCoverAllSixServices() {
        String[] roots = {"m_", "f_", "e_", "s_", "o_", "i_"};
        String[] paths = {"b/m_hokkaidou/", "b/f_84/", "b/e_y_yaruzatsu01/", "k/s_music", "k/o_1", "k/i_1"};
        String[] labels = {"北海道地区", "ホロライブ", "やる夫系雑談", "音楽/芸能/芸術/ファッション", "人気", "人気"};
        for (int i = 0; i < roots.length; i++) {
            KyodemoOtherBoards.Service service = KyodemoOtherBoards.services.get(i);
            assertEquals(roots[i], service.getRoot());
            KyodemoOtherBoards.Menu menu = KyodemoOtherBoards.parse(service, roots[i],
                "<a href='https://www.kyodemo.net/sdemo/" + paths[i] + "'>" + labels[i] + "</a>");
            assertEquals(1, menu.getBoards().size() + menu.getCategories().size());
        }
    }
}
