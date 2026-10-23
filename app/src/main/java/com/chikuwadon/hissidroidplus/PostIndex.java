package com.chikuwadon.hissidroidplus;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Canonical per-thread key and unique author names, shared by both site adapters. */
public final class PostIndex {
    private static final Pattern THREAD = Pattern.compile("^(/test/read\\.cgi/[A-Za-z0-9_]+/\\d{10,20})(?:/\\d{1,6})?/?$");
    private static final Pattern EDGE_THREAD = Pattern.compile("^(/sdemo/r/e_e_[A-Za-z0-9_]+/\\d{10,20})(?:/\\d{1,6})?/?$");
    
    private static final Pattern DATE = Pattern.compile("\\s*[:：]?\\s*20\\d{2}/\\d{1,2}/\\d{1,2}.*$",Pattern.DOTALL);
    private static final Pattern ID = Pattern.compile("\\s+ID\\s*[:：].*$",Pattern.CASE_INSENSITIVE|Pattern.DOTALL);
    private static final Pattern WACCHOI = Pattern.compile("\\s*[（(](?:ﾜｯﾁｮｲ|ｽﾌﾟ|ｱｳ|ｻｻ|ﾜｷ|ﾌﾞｰ|ﾗｸ|ﾃﾃ|ﾌﾞﾓ|JPW|その他).*",Pattern.DOTALL);
    private static final Pattern THREAD_PATH=Pattern.compile("^(/test/read\\.cgi/[A-Za-z0-9_]+/\\d{10,20})(?:/(?:\\d{1,6}|l\\d{1,6}|\\d{1,6}-\\d{1,6}))?/?$");
    private PostIndex() {}

    public static String threadKey(String postUrl) {
        if(postUrl==null || postUrl.isEmpty())return "";
        try {
            URI uri=URI.create(postUrl);
            String path=uri.getPath();
            if(path==null)return postUrl;
            Matcher m=THREAD_PATH.matcher(path);
            if(!m.matches())m=EDGE_THREAD.matcher(path);
            String canonical=path;
            if(m.matches())canonical=m.group(1)+"/";
            // http and https variants of the same underlying thread are equivalent
            String host=uri.getHost()==null?"":uri.getHost().toLowerCase(java.util.Locale.ROOT);
            if(host.equals("egg.5ch.net") || host.equals("egg.5ch.io"))host="egg.5ch.net";
            return host+canonical;
        } catch(Exception ex){return postUrl.replaceFirst("#.*$","");}
    }
    public static String openableThreadUrl(String postUrl){
        if(postUrl==null)return "";
        try {
            URI uri=URI.create(postUrl);
            String path=uri.getPath();
            Matcher m=THREAD_PATH.matcher(path);
            if(!m.matches())m=EDGE_THREAD.matcher(path);
            if(m.matches()){
                return new URI(uri.getScheme(),uri.getUserInfo(),uri.getHost(),uri.getPort(),m.group(1)+"/",null,null).toASCIIString();
            }
            return new URI(uri.getScheme(),uri.getUserInfo(),uri.getHost(),uri.getPort(),path,uri.getQuery(),null).toASCIIString();
        }catch(Exception ex){return postUrl.replaceFirst("#.*$","");}
    }
    public static LinkedHashMap<String,String> uniqueThreads(CheckerResult result){
        LinkedHashMap<String,String> output=new LinkedHashMap<>();
        LinkedHashSet<String> keys=new LinkedHashSet<>();
        for(CheckerResult.Post post: result.posts){
            if(post.link.isEmpty())continue;
            String key=threadKey(post.link);
            if(keys.add(key))output.put(openableThreadUrl(post.link),post.title.isEmpty()?"スレッド":post.title);
        }
        for(Map.Entry<String,String> entry: result.threads.entrySet()){
            String key=threadKey(entry.getKey());
            if(keys.add(key))output.put(openableThreadUrl(entry.getKey()),entry.getValue());
        }
        return output;
    }
    /** The checker HTML places name, trip and date in a single metadata field. */
    public static String cleanAuthor(String raw){
        if(raw==null)return "";
        String value=raw.replaceAll("<[^>]*>"," ").replace('\u00a0',' ').trim();
        // Edge response numbers and reference badges are not author names.
        value=value.replaceFirst("^\\d{1,6}(?:\\(\\d+\\))?\\s*[:：]\\s*","");
        value=value.replaceFirst("\\s+\\d{1,6}\\(\\d+\\)\\s*[:：].*$","");
        value=ID.matcher(value).replaceFirst("");
        value=DATE.matcher(value).replaceFirst("");
        value=value.replaceFirst("(?i)\\s+(?:sage|age)\\s*$", "");
        value=WACCHOI.matcher(value).replaceFirst("");
        int idx=value.indexOf(" [");if(idx>0)value=value.substring(0,idx);
        idx=value.indexOf("[");if(idx>0)value=value.substring(0,idx);
        return value.trim().length()>100?"":value.trim();
    }
    public static void collectNames(CheckerResult result){
        LinkedHashSet<String> seen=new LinkedHashSet<>();
        for(String name:result.names){String clean=cleanAuthor(name);if(!clean.isEmpty())seen.add(clean);}
        for(CheckerResult.Post post:result.posts){
            String name=cleanAuthor(post.author);
            if(!name.isEmpty())seen.add(name);
        }
        result.names.clear();result.names.addAll(seen);
    }
}
