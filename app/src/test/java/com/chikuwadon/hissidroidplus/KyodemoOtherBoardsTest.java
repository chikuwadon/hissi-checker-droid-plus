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
        KyodemoOtherBoards.Service s = KyodemoOtherBoards.services.get(2);
        assertEquals("s_music", KyodemoOtherBoards.parse(s, "s_", "<a href='/sdemo/k/s_music'>音楽</a>").getCategories().get(0).getCode());
        assertFalse(KyodemoOtherBoards.validBoard("e_e_liveedge"));
        assertFalse(KyodemoOtherBoards.validBoard("../bad"));
    }
    @Test public void otherBoardSearchUsesScopedPath() {
        ArchiveQuery q = new ArchiveQuery("Android", "", "", "", "", "", "new", "", false, false, "m_tokyo", "東京");
        assertTrue(ArchiveExternalSearch.url(ArchiveProvider.OTHER_KYODEMO, q, 2).startsWith("https://www.kyodemo.net/sdemo/b/m_tokyo/51?"));
    }

    @Test public void observedRootMenuLinksCoverAllTenServices() {
        String[] roots = {"m_", "f_", "s_", "o_", "i_", "e_y_", "e_j_", "e_p_", "e_o_", "e_"};
        String[] paths = {"b/m_hokkaidou/", "b/f_84/", "k/s_music", "k/o_1", "k/i_1", "b/e_y_yaruzatsu01/", "b/e_j_hololiveneet/", "b/e_p_vaporeon/", "b/e_o_poverty/", "b/e_e_edge/"};
        String[] labels = {"北海道地区", "ホロライブ", "音楽/芸能/芸術/ファッション", "人気", "人気", "やる夫系雑談", "ホロライブニート", "ぷにぷに板", "嫌儲.org", "なんでも実況(エッジ2)"};
        assertEquals(10, KyodemoOtherBoards.services.size());
        for (int i = 0; i < roots.length; i++) {
            KyodemoOtherBoards.Service service = KyodemoOtherBoards.services.get(i);
            assertEquals(roots[i], service.getRoot());
            KyodemoOtherBoards.Menu menu = KyodemoOtherBoards.parse(service, roots[i],
                "<a href='https://www.kyodemo.net/sdemo/" + paths[i] + "'>" + labels[i] + "</a>");
            assertEquals(1, menu.getBoards().size() + menu.getCategories().size());
        }
    }

    @Test public void sharedOtherMenuIsSplitWithoutChangingRealUrls() {
        String html = "<a href='/sdemo/b/e_y_yaruo100/'>やる夫板100</a>"
            + "<a href='/sdemo/b/e_j_hololiveneet/'>ホロライブニート</a>"
            + "<a href='/sdemo/b/e_p_vaporeon/'>ぷにぷに板</a>"
            + "<a href='/sdemo/b/e_o_poverty/'>嫌儲.org</a>"
            + "<a href='/sdemo/b/e_e_liveedge/'>エッヂ</a>"
            + "<a href='/sdemo/b/e_e_edge/'>なんでも実況(エッジ2)</a>"
            + "<a href='/sdemo/b/e_n_dartkakuni/'>[PD]半角二次元</a>"
            + "<a href='/sdemo/b/e_d_demo/'>[デモ]運用情報</a>";
        for (int i = 5; i < 10; i++) {
            KyodemoOtherBoards.Service service = KyodemoOtherBoards.services.get(i);
            assertEquals("e_", KyodemoOtherBoards.menuRoot(service));
            KyodemoOtherBoards.Menu menu = KyodemoOtherBoards.parse(service, service.getRoot(), html);
            assertEquals(i == 9 ? 3 : 1, menu.getBoards().size());
            for (BoardCatalog.Board board : menu.getBoards())
                assertEquals(service, KyodemoOtherBoards.serviceFor(board.code));
        }
        assertFalse(KyodemoOtherBoards.validBoard("e_e_liveedge"));
    }

    @Test public void missingRemoteGroupsAreRestoredFromVerifiedSnapshot() {
        int[] counts = {4, 6, 3, 13, 3};
        for (int i = 5; i < 10; i++) {
            KyodemoOtherBoards.Service service = KyodemoOtherBoards.services.get(i);
            KyodemoOtherBoards.Menu empty = KyodemoOtherBoards.parse(service, service.getRoot(), "<html></html>");
            KyodemoOtherBoards.Menu merged = KyodemoOtherBoards.mergeKnown(service, service.getRoot(), empty);
            assertEquals(counts[i-5], merged.getBoards().size());
            for (BoardCatalog.Board board : merged.getBoards())
                assertEquals(service, KyodemoOtherBoards.serviceFor(board.code));
        }
        KyodemoOtherBoards.Item favorite = new KyodemoOtherBoards.Item("e_j_hololiveneet", "ホロライブニート");
        assertEquals("JPNKN / ホロライブニート", OtherBoardSelection.displayLabel(favorite));
    }
}
