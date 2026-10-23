package com.chikuwadon.hissidroidplus;
import static org.junit.Assert.*;
import org.junit.Test;
public final class HissiPrimaryEndpointTest {
    @Test public void startsWithWorkingHttpEndpointInsteadOfWaitingHttpsTimeout(){
        assertEquals("http://hissi.org/read.php/android/20261023/Sm5GeVZTa3ow.html",
           HissiAdapter.resultUrl("android","20261023","JnFyVSkz0"));
    }
}
