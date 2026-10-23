package com.chikuwadon.hissidroidplus;

import java.net.URI;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Maps only validated kyodemo Edge archive links to the originating board. */
public final class EdgeThreadLink {
    private static final Pattern ARCHIVE=Pattern.compile(
        "^/sdemo/r/e_e_([A-Za-z0-9_]{1,60})/(\\d{10,20})(?:/(\\d{1,6}))?/?$");
    private EdgeThreadLink(){}
    public static String original(String input){
        if(input==null)return "";
        try{
            URI uri=URI.create(input);
            if(!"http".equalsIgnoreCase(uri.getScheme())&&
                    !"https".equalsIgnoreCase(uri.getScheme()))return input;
            String host=uri.getHost();
            if(!"www.kyodemo.net".equalsIgnoreCase(host)&&
                    !"kyodemo.net".equalsIgnoreCase(host))return input;
            if(uri.getUserInfo()!=null||uri.getPort()!=-1)return input;
            Matcher match=ARCHIVE.matcher(uri.getPath());
            if(!match.matches())return input;
            String target="https://bbs.eddibb.cc/test/read.cgi/"+
                    match.group(1)+"/"+match.group(2)+"/";
            if(match.group(3)!=null)target+=match.group(3);
            return target;
        }catch(Exception ignored){return input;}
    }
}
