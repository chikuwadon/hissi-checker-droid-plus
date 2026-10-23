package com.chikuwadon.hissidroidplus;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

/** Bounded HTTP transport, called exclusively from a worker thread. */
public final class HttpTransport {
    private static final int LIMIT=4*1024*1024;
    private static volatile String diagnostics="未取得";
    private static volatile String userAgent="";
    public static void setUserAgent(String ua){if(ua!=null && !ua.trim().isEmpty())userAgent=ua;}
    public static String lastDiagnostic(){return diagnostics;}
    private static final Pattern CHARSET=Pattern.compile("(?i)charset\\s*=\\s*[\"' ]*([A-Za-z0-9_-]+)");
    public interface CookieJar { String read(String url); void save(String url,String cookie); }
    private static volatile CookieJar cookieJar;
    public static void setCookieJar(CookieJar jar){cookieJar=jar;}
    private HttpTransport(){}
    public static String get(String url, Charset charset)throws IOException{return request(url,null,charset);}
    public static String post(String url,String data,Charset charset)throws IOException{return request(url,data,charset);}
    private static String request(String url,String form,Charset charset)throws IOException{
        String current=url;
        for(int redirects=0;redirects<4;redirects++){
            URL target=new URL(current);
            validate(target);
            HttpURLConnection c=(HttpURLConnection)target.openConnection();
            c.setConnectTimeout(10000);c.setReadTimeout(15000);
            c.setInstanceFollowRedirects(false);
            if(!userAgent.isEmpty()) c.setRequestProperty("User-Agent",userAgent);
            c.setRequestProperty("Accept-Language","ja-JP,ja;q=0.9,en-US;q=0.5");
            c.setRequestProperty("Accept","text/html,application/xhtml+xml,application/json;q=0.8,*/*;q=0.6");
            c.setRequestProperty("Accept-Encoding","identity");
            // The Android WebView in this app and HttpURLConnection may reuse
            // same-origin session cookies. Do not import cookies from other apps.
            if(target.getHost().endsWith("kyodemo.net")) {
                String cookie=cookieJar==null?null:cookieJar.read(target.toExternalForm());
                if(cookie!=null && !cookie.isEmpty())c.setRequestProperty("Cookie",cookie);
                c.setRequestProperty("Referer","https://www.kyodemo.net/sdemo/");
            }
            try {
                if(form!=null){
                    c.setRequestMethod("POST");c.setDoOutput(true);
                    c.setRequestProperty("Content-Type","application/x-www-form-urlencoded; charset=UTF-8");
                    try(OutputStream out=c.getOutputStream()){out.write(form.getBytes(StandardCharsets.UTF_8));}
                }
                boolean hadCookie=c.getRequestProperty("Cookie")!=null;
                int status=c.getResponseCode();
                String contentType=c.getContentType();
                diagnostics="HTTP="+status+" / 形式="+(contentType==null?"未指定":contentType.replaceAll("[\\r\\n]"," "))+
                    " / リダイレクト="+redirects+" / Cookie送信="+(hadCookie?"あり":"なし")+
                    " / UA="+(!userAgent.isEmpty()?"Android WebView":"標準HTTP");
                if(target.getHost().endsWith("kyodemo.net")){
                    for(Map.Entry<String,List<String>> entry:c.getHeaderFields().entrySet()){
                        if(entry.getKey()==null||!entry.getKey().equalsIgnoreCase("Set-Cookie"))continue;
                        for(String v:entry.getValue()){
                            try{if(cookieJar!=null)cookieJar.save(target.toExternalForm(),v);}
                            catch(RuntimeException ignored){}
                        }
                    }
                }
                if(status==301||status==302||status==303||status==307||status==308){
                    String location=c.getHeaderField("Location");
                    if(location==null)throw new IOException("Redirect without Location");
                    URL next=new URL(target,location);validate(next);
                    current=next.toExternalForm();
                    if(status==301||status==302||status==303)form=null;
                    continue;
                }
                if(status!=200)throw new IOException("HTTP "+status);
                if(c.getContentLengthLong()>LIMIT)throw new IOException("HTMLが大きすぎます");
                try(InputStream input=c.getInputStream();ByteArrayOutputStream output=new ByteArrayOutputStream()){
                    byte[] buffer=new byte[8192];int n;
                    while((n=input.read(buffer))!=-1){
                        if(output.size()+n>LIMIT)throw new IOException("HTMLが大きすぎます");
                        output.write(buffer,0,n);
                    }
                    byte[] data=output.toByteArray();
                    Charset chosen=charset;
                    if(contentType!=null){
                        Matcher match=CHARSET.matcher(contentType);
                        if(match.find())try{chosen=Charset.forName(match.group(1));}
                        catch(Exception ignored){}
                    }
                    if(contentType==null || !CHARSET.matcher(contentType).find()){
                        String head=new String(data,0,Math.min(data.length,3072),StandardCharsets.ISO_8859_1);
                        Matcher match=CHARSET.matcher(head);
                        if(match.find())try{chosen=Charset.forName(match.group(1));}
                        catch(Exception ignored){}
                    }
                    String decoded=HissiHtmlDecoder.decode(data,chosen,
                            target.getHost().equalsIgnoreCase("hissi.org") ||
                            target.getHost().equalsIgnoreCase("www.hissi.org"));
                    diagnostics+=" / 文字コード="+chosen.name()
                            +(decoded.startsWith(HissiHtmlDecoder.FALLBACK_MARKER)?"(Shift_JIS再判定)":"")
                            +" / 受信="+data.length+"B";
                    if(decoded.startsWith(HissiHtmlDecoder.FALLBACK_MARKER))
                        decoded=decoded.substring(HissiHtmlDecoder.FALLBACK_MARKER.length());
                    return decoded;
                }
            }finally{c.disconnect();}
        }
        throw new IOException("Redirectが多すぎます");
    }
    private static void validate(URL target)throws IOException{
        String host=target.getHost(),proto=target.getProtocol();
        if(target.getUserInfo()!=null||target.getPort()!=-1)throw new IOException("不正なURLです");
        if((host.equalsIgnoreCase("kyodemo.net")||host.equalsIgnoreCase("www.kyodemo.net"))&&proto.equals("https"))return;
        if((host.equalsIgnoreCase("hissi.org")||host.equalsIgnoreCase("www.hissi.org")) &&
                (proto.equals("http")||proto.equals("https")))return;
        if(host.equalsIgnoreCase("menu.5ch.io")&&proto.equals("https"))return;
        throw new IOException("通信先が許可されていません");
    }
}
