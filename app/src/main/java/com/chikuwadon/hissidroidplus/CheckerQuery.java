package com.chikuwadon.hissidroidplus;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Immutable, validated request; no Android or mod dependencies. */
public final class CheckerQuery {
    public enum Site { HISSI, EDGE }
    private static final Pattern BOARD = Pattern.compile("[A-Za-z0-9_]{1,60}");
    private static final Pattern HISSI_PATH = Pattern.compile("^/read\\.php/([A-Za-z0-9_]+)/[^?#]*\\.html$");
    private static final Pattern SHARED_BOARD = Pattern.compile("(?:read\\.cgi|read\\.php)/([A-Za-z0-9_]+)/");
    private static final Pattern SHARED_DATE = Pattern.compile("([0-9]{4})/([0-9]{1,2})/([0-9]{1,2})");
    private static final Pattern SHARED_ID = Pattern.compile("(?i)\\bID\\s*[:：]\\s*([A-Za-z0-9+./_-]+)");
    private static final Pattern EDGE_PATH = Pattern.compile("^/sdemo/b/e_e_([A-Za-z0-9_]+)/?$");
    public final Site site;
    public final String board;
    public final String id;
    public final String date;
    public final String key;
    public final String url;

    private CheckerQuery(Site site, String board, String id, String date, String key, String url) {
        this.site=site;this.board=board;this.id=id;this.date=date;this.key=key;this.url=url;
    }
    public static CheckerQuery hissi(String board,String date,String id) {
        if(board==null||!BOARD.matcher(board).matches()) throw new IllegalArgumentException("板名が不正です");
        checkDate(date);
        if(id==null||id.trim().isEmpty())throw new IllegalArgumentException("IDが不正です");
        String cleaned=id.trim().replaceFirst("(?i)^ID\\s*[:：]\\s*", "").trim();
        if(cleaned.length()>80)throw new IllegalArgumentException("IDが不正です");
        if(cleaned.isEmpty()) throw new IllegalArgumentException("IDが不正です");
        return new CheckerQuery(Site.HISSI,board,cleaned,date,"","");
    }
    /** Edge searches received from an external client lack a checker page key. */
    public static CheckerQuery edgeSearch(String board,String date,String id){
        if(board==null || !BOARD.matcher(board).matches())
            throw new IllegalArgumentException("エッヂの板名が不正です");
        checkDate(date);
        if(id==null)throw new IllegalArgumentException("エッヂのIDが不正です");
        String cleaned=id.trim().replaceFirst("(?i)^ID\\s*[:：]\\s*","").trim();
        if(!cleaned.matches("[A-Za-z0-9+./_-]{1,64}"))
            throw new IllegalArgumentException("エッヂのIDが不正です");
        return new CheckerQuery(Site.EDGE,board,cleaned,date,"","");
    }

    /** Only unambiguous Edge board IDs are inferred from legacy extras. */
    public static boolean isKnownEdgeBoard(String board){
        return "liveedge".equalsIgnoreCase(board);
    }

    public static CheckerQuery parseUrl(String input) {
        if(input==null)throw new IllegalArgumentException("URLがありません");
        String url=findUrl(input);
        URI uri;
        try { uri=URI.create(url); } catch(Exception e){throw new IllegalArgumentException("URLが不正です",e);}
        String host=uri.getHost();
        if (host==null||uri.getRawUserInfo()!=null||uri.getPort()!=-1) throw new IllegalArgumentException("接続先が不正です");
        String path=uri.getPath();
        if ((host.equalsIgnoreCase("hissi.org")||host.equalsIgnoreCase("www.hissi.org")) && ("http".equals(uri.getScheme())||"https".equals(uri.getScheme()))) {
            Matcher match=HISSI_PATH.matcher(path);
            if(!match.matches()) throw new IllegalArgumentException("hissi.orgの結果URLを指定してください");
            return new CheckerQuery(Site.HISSI,match.group(1),"","","",url);
        }
        if ((host.equalsIgnoreCase("kyodemo.net")||host.equalsIgnoreCase("www.kyodemo.net")) && "https".equals(uri.getScheme())) {
            Matcher match=EDGE_PATH.matcher(path);
            if(!match.matches())throw new IllegalArgumentException("エッヂ用のURLではありません");
            String id=arg(uri.getRawQuery(),"hi"),key=arg(uri.getRawQuery(),"key"),date=arg(uri.getRawQuery(),"date");
            if(id==null||!id.matches("[A-Za-z0-9+./_-]{1,64}")||key==null||!key.matches("\\d{10,20}"))throw new IllegalArgumentException("hiまたはkeyが不正です");
            checkDate(date);
            String canonical="https://www.kyodemo.net/sdemo/b/e_e_"+match.group(1)+"/?hi="+enc(id)+"&key="+key+"&date="+date;
            String token=arg(uri.getRawQuery(),"c");
            if(token!=null && token.matches("[A-Za-z0-9_-]{1,48}"))
                canonical+="&c="+enc(token);
            return new CheckerQuery(Site.EDGE,match.group(1),id,date,key,canonical);
        }
        throw new IllegalArgumentException("未対応のURLです");
    }
    /** Identify the originating service by the URL's real host, never board name. */
    static Site sourceFromThreadUrl(String url){
        if(url==null)return null;
        try{
            URI uri=URI.create(url.replaceAll("[.,。]+$",""));
            if(!"http".equalsIgnoreCase(uri.getScheme()) &&
                    !"https".equalsIgnoreCase(uri.getScheme()))return null;
            if(uri.getRawUserInfo()!=null || uri.getPort()!=-1)return null;
            String host=uri.getHost(),path=uri.getPath();
            if(host==null||path==null)return null;
            if(host.equalsIgnoreCase("bbs.eddibb.cc") &&
                    path.matches("^/(?:test/read\\.cgi/)?[A-Za-z0-9_]+/[0-9]{10,20}(?:/.*)?$"))
                return Site.EDGE;
            if((host.equalsIgnoreCase("kyodemo.net")||
                    host.equalsIgnoreCase("www.kyodemo.net")) &&
                    path.matches("^/sdemo/r/e_e_[A-Za-z0-9_]+/[0-9]{10,20}(?:/.*)?$"))
                return Site.EDGE;
            String lower=host.toLowerCase(Locale.ROOT);
            if((lower.equals("5ch.net")||lower.endsWith(".5ch.net")||
                    lower.equals("2ch.net")||lower.endsWith(".2ch.net")||
                    lower.equals("bbspink.com")||lower.endsWith(".bbspink.com")) &&
                    path.matches("^/test/read\\.cgi/[A-Za-z0-9_]+/[0-9]{10,20}(?:/.*)?$"))
                return Site.HISSI;
        }catch(IllegalArgumentException ignored){}
        return null;
    }

    private static Site sourceFromShare(String text,String board){
        Matcher urls=Pattern.compile("https?://[^\\s<>'\\\"）]+",Pattern.CASE_INSENSITIVE)
                .matcher(text.replace("&amp;","&"));
        while(urls.find()){
            String url=urls.group();
            Site site=sourceFromThreadUrl(url);
            if(site==null)continue;
            // Only accept a source whose URL actually contains the parsed board.
            try{
                String path=URI.create(url).getPath();
                if(site==Site.EDGE && path.contains("/e_e_"+board+"/"))return site;
                if(path.contains("/"+board+"/"))return site;
            }catch(IllegalArgumentException ignored){}
        }
        return null;
    }

    /** Match the share text emitted by conventional bulletin board readers. */
    public static CheckerQuery fromSharedText(String shared) {
        if(shared==null||shared.isEmpty()) throw new IllegalArgumentException("共有データがありません");
        if(shared.contains("kyodemo.net/sdemo/b/")||shared.contains("hissi.org/read.php/"))
            return parseUrl(shared);
        Matcher board=SHARED_BOARD.matcher(shared);
        Matcher date=SHARED_DATE.matcher(shared);
        Matcher id=SHARED_ID.matcher(shared);
        if(!board.find()||!date.find()||!id.find())
            throw new IllegalArgumentException("板・日付・IDを共有テキストから取得できません");
        String normalized=String.format(Locale.ROOT,"%s%02d%02d",date.group(1),
                Integer.parseInt(date.group(2)),Integer.parseInt(date.group(3)));
        Site source=sourceFromShare(shared,board.group(1));
        if(source==Site.EDGE)return edgeSearch(board.group(1),normalized,id.group(1));
        if(source==Site.HISSI)return hissi(board.group(1),normalized,id.group(1));
        // Legacy payloads sometimes omit the host entirely. This remains a
        // compatibility fallback, not the primary identification method.
        if(isKnownEdgeBoard(board.group(1)))
            return edgeSearch(board.group(1),normalized,id.group(1));
        return hissi(board.group(1),normalized,id.group(1));
    }

    private static String findUrl(String shared) {
        Matcher m=Pattern.compile("https?://[^\\s<>'\\\"）]+",Pattern.CASE_INSENSITIVE).matcher(shared.replace("&amp;","&"));
        if(!m.find())throw new IllegalArgumentException("URLが見つかりません");
        return m.group().replaceAll("[.,。]+$","");
    }
    private static String arg(String query,String name) {
        if(query==null)return null;
        for(String segment:query.split("&")) {
            int equal=segment.indexOf('=');
            if(equal<=0||!segment.substring(0,equal).equals(name))continue;
            try {return java.net.URLDecoder.decode(segment.substring(equal+1),"UTF-8");}
            catch(java.io.UnsupportedEncodingException e){throw new AssertionError(e);}
        }
        return null;
    }
    private static String enc(String s){
        try{return URLEncoder.encode(s,"UTF-8").replace("+","%20");}
        catch(java.io.UnsupportedEncodingException impossible){throw new AssertionError(impossible);}
    }
    public static void validateDate(String date){checkDate(date);}
    private static void checkDate(String date) {
        if(date==null||!date.matches("\\d{8}"))throw new IllegalArgumentException("日付はYYYYMMDD形式にしてください");
        try {LocalDate.parse(date,DateTimeFormatter.BASIC_ISO_DATE);}catch(DateTimeParseException e){throw new IllegalArgumentException("日付が不正です",e);}
    }
}
