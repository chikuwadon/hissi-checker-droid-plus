package com.chikuwadon.hissidroidplus;

import java.net.URI;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Normalize known Edge URLs to the ChMate-compatible /test/read.cgi/ form. */
public final class EdgeThreadLink {
    private static final Pattern ARCHIVE=Pattern.compile(
        "^/sdemo/r/e_e_([A-Za-z0-9_]{1,60})/(\\d{10,20})(?:/(\\d{1,6}))?/?$");
    private static final Pattern SHORT=Pattern.compile(
        "^/([A-Za-z0-9_]{1,60})/(\\d{10,20})(?:/(\\d{1,6}))?/?$");
    private static final Pattern READ_CGI=Pattern.compile(
        "^/test/read\\.cgi/([A-Za-z0-9_]{1,60})/(\\d{10,20})(?:/(\\d{1,6}))?/?$");
    private EdgeThreadLink(){}
    public static String original(String input){
        if(input==null)return "";
        try{
            URI uri=URI.create(input);
            if(!"http".equalsIgnoreCase(uri.getScheme())&&
                    !"https".equalsIgnoreCase(uri.getScheme()))return input;
            if(uri.getRawUserInfo()!=null||uri.getPort()!=-1)return input;
            String host=uri.getHost();
            String path=uri.getPath();
            if(host==null||path==null)return input;
            Matcher matched;
            if("kyodemo.net".equalsIgnoreCase(host)||
                    "www.kyodemo.net".equalsIgnoreCase(host)){
                matched=ARCHIVE.matcher(path);
            }else if("bbs.eddibb.cc".equalsIgnoreCase(host)){
                matched=SHORT.matcher(path);
                if(!matched.matches())matched=READ_CGI.matcher(path);
            }else return input;
            if(!matched.matches())return input;
            StringBuilder output=new StringBuilder("https://bbs.eddibb.cc/test/read.cgi/")
                .append(matched.group(1)).append('/').append(matched.group(2)).append('/');
            if(matched.group(3)!=null)output.append(matched.group(3));
            return output.toString();
        }catch(Exception ignored){return input;}
    }
}
