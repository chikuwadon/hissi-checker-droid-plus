package com.chikuwadon.hissidroidplus;

import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

public final class HissiRecentSearchTest {
    @Test public void findsFirstMatchingDayWithoutProbingEverything(){
        List<String> searched=new ArrayList<>();
        HissiRecentSearch.Result found=HissiRecentSearch.find(
            "news4vip","ABC",LocalDate.of(2026,10,24),(b,d,i)->{
                searched.add(d);if(d.equals("20261024"))throw new IOException("HTTP 404");
                return d.equals("20261023");
            });
        assertEquals("20261023",found.day);
        assertEquals(2,found.checkedDays);
        assertEquals(2,searched.size());
        assertEquals("20261024",searched.get(0));
    }
    @Test public void noMatchesAreBoundedToSevenDays(){
        HissiRecentSearch.Result found=HissiRecentSearch.find("news4vip","ABC",
            LocalDate.of(2026,1,2),(b,d,i)->{throw new IOException("HTTP 404");});
        assertNull(found.day);assertNull(found.error);assertEquals(7,found.checkedDays);
    }
    @Test public void connectivityErrorsAreNotReportedAsZeroResults(){
        HissiRecentSearch.Result found=HissiRecentSearch.find("news4vip","ABC",
            LocalDate.of(2026,10,24),(b,d,i)->{throw new IOException("HTTP 403");});
        assertNull(found.day);assertTrue(found.error.contains("403"));
        assertEquals(1,found.checkedDays);
    }
    @Test public void pure404GetsUserFriendlyMessageOnlyForHomeSearch(){
        assertTrue(HissiSearchFeedback.missingSearchResult(
            CheckerQuery.hissi("news4vip","20261024","ABC"),"HTTP 404"));
        assertFalse(HissiSearchFeedback.missingSearchResult(
            CheckerQuery.hissi("news4vip","20261024","ABC"),"HTTP 503"));
        assertFalse(HissiSearchFeedback.missingSearchResult(
            CheckerQuery.parseUrl("http://hissi.org/read.php/news4vip/20261024/QUJD.html"),
            "HTTP 404"));
    }
}
