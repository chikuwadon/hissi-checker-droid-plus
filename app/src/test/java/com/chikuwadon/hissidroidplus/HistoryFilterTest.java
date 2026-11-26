package com.chikuwadon.hissidroidplus;
import org.junit.Test;
import static org.junit.Assert.*;
public class HistoryFilterTest {
    @Test public void sliderHasOneHundredFinitePositionsAndUnlimited() {
        assertEquals(100, HistoryRetention.fromProgress(0));
        assertEquals(10000, HistoryRetention.fromProgress(99));
        assertEquals(0, HistoryRetention.fromProgress(100));
        assertEquals(100, HistoryRetention.toProgress(0));
        assertEquals(99, HistoryRetention.toProgress(10000));
        assertTrue(HistoryRetention.reduced(0,100));
        assertFalse(HistoryRetention.reduced(100,0));
    }
    @Test public void filterMatchesBoardIdAndInclusiveDateRange() {
        SearchHistory.Entry e = new SearchHistory.Entry("https://hissi.org/read.php/android/20261126/a.html","5ch","android","20261126","AbC123",1795651200000L,3);
        assertTrue(new HistoryFilter("AND","AbC","20261126","20261126",true).matches(e));
        assertFalse(new HistoryFilter("AND","abc","","",true).matches(e));
        assertFalse(new HistoryFilter("other","","","",true).matches(e));
        assertFalse(new HistoryFilter("","","20261127","",true).matches(e));
        assertTrue(new HistoryFilter("","ID:AbC","","",false).matches(e));
    }
}
