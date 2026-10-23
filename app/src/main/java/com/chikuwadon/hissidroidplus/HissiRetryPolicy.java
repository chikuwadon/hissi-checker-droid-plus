package com.chikuwadon.hissidroidplus;

import java.io.IOException;
import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.SocketException;
import java.net.SocketTimeoutException;

/** Retry a known hissi.org HTTP endpoint only for transport-level errors. */
final class HissiRetryPolicy {
    private HissiRetryPolicy(){}
    static boolean shouldRetry(String url,IOException failure){
        if(url==null||!url.startsWith("http://hissi.org/read.php/"))return false;
        Throwable cause=failure;
        while(cause!=null){
            if(cause instanceof SocketTimeoutException||
                    cause instanceof ConnectException||
                    cause instanceof NoRouteToHostException||
                    cause instanceof SocketException)return true;
            cause=cause.getCause();
        }
        return false;
    }
}
