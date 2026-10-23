package com.chikuwadon.hissidroidplus;
import static org.junit.Assert.*;
import org.junit.Test;

public final class HissiConnectionFallbackTest {
    @Test public void preservesHostAndPath(){
        assertEquals("http://hissi.org/read.php/android/20261023/Sm5GeVZTa3ow.html",
            HissiAdapter.httpFallbackUrl("https://hissi.org/read.php/android/20261023/Sm5GeVZTa3ow.html"));
        assertEquals("",HissiAdapter.httpFallbackUrl("https://example.org/read.php/android/test.html"));
        assertEquals("",HissiAdapter.httpFallbackUrl("http://hissi.org/read.php/android/test.html"));
        assertEquals("",HissiAdapter.httpFallbackUrl("https://hissi.org/other.html"));
    }
}
