package com.chikuwadon.hissidroidplus;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Site's own ID search and explicit thread-to-checker conversion. No synthetic thread keys. */
public final class EdgeSearchLink {
    private static final Pattern KYODEMO_THREAD=Pattern.compile(
        "^/sdemo/r/e_e_([A-Za-z0-9_]+)/([0-9]{10,20})(?:/.*)?$");
    private static final Pattern BBS_THREAD=Pattern.compile(
        "^/test/read\\.cgi/([A-Za-z0-9_]+)/([0-9]{10,20})(?:/.*)?$");
    private static final Pattern BBS_SHORT=Pattern.compile(
        "^/([A-Za-z0-9_]+)/([0-9]{10,20})(?:/.*)?$");
    private EdgeSearchLink(){}
    private static String enc(String val){
        try{return URLEncoder.encode(val,"UTF-8").replace("+","%20");}
        catch(java.io.UnsupportedEncodingException impossible){throw new AssertionError(impossible);}
    }
    public static String searchUrlAnyDate(String board,String id){
        if(board==null||!board.matches("[A-Za-z0-9_]{1,60}"))
            throw new IllegalArgumentException("Invalid board");
        if(id==null||!id.matches("[A-Za-z0-9+./_-]{1,64}"))
            throw new IllegalArgumentException("Invalid ID");
        return "https://www.kyodemo.net/sdemo/b/e_e_"+board+
                "/?bs=hi&kt=k_&k="+enc(id);
    }
    public static String searchUrl(String board,String date,String id){
        CheckerQuery.validateDate(date);
        String day=date.substring(0,4)+"-"+date.substring(4,6)+"-"+date.substring(6);
        return searchUrlAnyDate(board,id)+"&fr="+day+"&to="+day;
    }
    public static String checkerFromThread(String board,String date,String id,String clicked){
        try{
            URI uri=URI.create(clicked);
            String host=uri.getHost(),path=uri.getPath();
            if(host==null||path==null||uri.getPort()!=-1||uri.getUserInfo()!=null)return "";
            Matcher m;
            if(host.equalsIgnoreCase("www.kyodemo.net")||host.equalsIgnoreCase("kyodemo.net"))
                m=KYODEMO_THREAD.matcher(path);
            else if(host.equalsIgnoreCase("bbs.eddibb.cc")){
                m=BBS_THREAD.matcher(path);
                if(!m.matches())m=BBS_SHORT.matcher(path);
            }else return "";
            if(!m.matches()||!board.equals(m.group(1)))return "";
            String key=m.group(2);
            return "https://www.kyodemo.net/sdemo/b/e_e_"+board+
                    "/?hi="+enc(id)+"&key="+key+"&date="+date;
        }catch(Exception invalid){return "";}
    }
}
