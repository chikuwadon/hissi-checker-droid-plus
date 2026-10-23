package com.chikuwadon.hissidroidplus;
import java.io.IOException;
import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.SocketTimeoutException;
import org.junit.Test;
import static org.junit.Assert.*;

public final class HissiRetryPolicyTest {
    private static final String URL="http://hissi.org/read.php/android/20261023/ABC.html";
    @Test public void httpStatusDoesNotRetry(){
        for(int status:new int[]{403,404,429,500,503})
            assertFalse(HissiRetryPolicy.shouldRetry(URL,new IOException("HTTP "+status)));
    }
    @Test public void actualTransportErrorsRetry(){
        assertTrue(HissiRetryPolicy.shouldRetry(URL,new SocketTimeoutException()));
        assertTrue(HissiRetryPolicy.shouldRetry(URL,new IOException("wrapped",new ConnectException())));
        assertTrue(HissiRetryPolicy.shouldRetry(URL,new NoRouteToHostException()));
        assertFalse(HissiRetryPolicy.shouldRetry(URL,new IOException("Unexpected EOF")));
    }
    @Test public void noRetryOnOtherHosts(){
        assertFalse(HissiRetryPolicy.shouldRetry("https://hissi.org/read.php/android/20261023/x.html",new ConnectException()));
        assertFalse(HissiRetryPolicy.shouldRetry("http://hissi.org.evil.test/read.php/android/20261023/x.html",new ConnectException()));
    }
}
