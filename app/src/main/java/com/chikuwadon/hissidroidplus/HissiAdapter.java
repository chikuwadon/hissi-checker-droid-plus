package com.chikuwadon.hissidroidplus;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

/** New adapter derived from the HTML markers found in original v1.5's DEX. */
public final class HissiAdapter {
    private static final Charset SJIS=Charset.forName("Shift_JIS");
    private static final Pattern REDIRECT=Pattern.compile("(?i)(?:url\\s*=\\s*['\\\"]?)([^'\\\"\\s>]+)");
    // The actual page may reorder anchor attributes or omit target="_blank".
    // Keep all text between </b> and <dd> so date / ID / mail are not lost.
    private static final Pattern POST=Pattern.compile(
        "(?is)<a\\b[^>]*?\\bhref\\s*=\\s*['\\\"]?(https?://[^\\s'\\\"<>]+)['\\\"]?[^>]*>(.*?)</a>\\s*<br\\s*/?>\\s*"+
        "(\\d{1,6})\\s*[:：]\\s*<b\\b[^>]*>(.*?)</b>\\s*(.*?)<dd\\b[^>]*>(.*?)(?=</dl>|$)");
    private static final Pattern NUMBER=Pattern.compile("(?i)<font\\s+size\\s*=\\s*5\\s+color\\s*=\\s*#FF6347[^>]*>(.*?)</td>.*?<td\\s+width\\s*=\\s*40[^>]*>(\\d+)</td>",Pattern.DOTALL);

    /** Hissi encodes the original ID using standard Base64 without trailing padding. */
    static String resultUrl(String board,String date,String id) {
        String encoded=java.util.Base64.getEncoder().withoutPadding()
                .encodeToString(id.getBytes(StandardCharsets.UTF_8));
        return "https://hissi.org/read.php/"+board+"/"+date+"/"+encoded+".html";
    }

    /** Retry over HTTP only for a known HTTPS connection failure on hissi.org. */
    static String httpFallbackUrl(String url){
        try{
            java.net.URI uri=new java.net.URI(url);
            if(!"https".equalsIgnoreCase(uri.getScheme()))return "";
            if(!("hissi.org".equalsIgnoreCase(uri.getHost()) ||
                    "www.hissi.org".equalsIgnoreCase(uri.getHost())))return "";
            if(uri.getPort()!=-1||uri.getRawUserInfo()!=null)return "";
            if(!uri.getRawPath().startsWith("/read.php/"))return "";
            return new java.net.URI("http",null,uri.getHost(),-1,uri.getRawPath(),
                    uri.getRawQuery(),uri.getRawFragment()).toASCIIString();
        }catch(Exception badUrl){return "";}
    }
    private static boolean connectionFailure(IOException error){
        Throwable cause=error;
        while(cause!=null){
            if(cause instanceof java.net.SocketTimeoutException ||
                    cause instanceof java.net.ConnectException ||
                    cause instanceof java.net.NoRouteToHostException)
                return true;
            cause=cause.getCause();
        }
        return false;
    }
    private static class Response {
        final String html,url;
        Response(String html,String url){this.html=html;this.url=url;}
    }
    private static Response getPage(String url)throws IOException{
        try{
            return new Response(HttpTransport.get(url,SJIS),url);
        }catch(IOException httpsFailure){
            String fallback=httpFallbackUrl(url);
            if(fallback.isEmpty()||!connectionFailure(httpsFailure))throw httpsFailure;
            try{
                return new Response(HttpTransport.get(fallback,SJIS),fallback);
            }catch(IOException httpFailure){
                httpFailure.addSuppressed(httpsFailure);
                throw httpFailure;
            }
        }
    }
    public CheckerResult load(CheckerQuery query)throws IOException {
        String url=query.url.isEmpty()?resultUrl(query.board,query.date,query.id):query.url;
        CheckerResult result=new CheckerResult();
        IOException canonicalError=null;
        try {
            Response primary=getPage(url);
            url=primary.url;
            result=parse(primary.html,url);
        }catch(IOException ex){canonicalError=ex;}
        if(result.posts.isEmpty()){
            String all=url+(url.contains("?")?"&":"?")+"thread=all&name=all";
            try{
                Response secondaryPage=getPage(all);
                CheckerResult secondary=parse(secondaryPage.html,secondaryPage.url);
                if(!secondary.posts.isEmpty()||result.reportedPostCount<0){
                    result=secondary;
                    url=secondaryPage.url.replaceFirst("[?&]thread=all&name=all$", "");
                }
            }catch(IOException ex){
                if(canonicalError!=null)ex.addSuppressed(canonicalError);
                if(result.reportedPostCount<0)throw ex;
            }
        }
        if(canonicalError!=null&&result.posts.isEmpty()&&result.reportedPostCount<0)
            throw canonicalError;
        result.sourceUrl=url;
        result.diagnostic=HttpTransport.lastDiagnostic()+
                " / 解析="+result.posts.size()+"件 / 表示集計="+result.reportedPostCount+"件"
                +(result.diagnostic.isEmpty()?"":" / "+result.diagnostic);
        return result;
    }
    public CheckerResult nextPage(String url,int page)throws IOException{
        if(page<1||page>20)throw new IOException("ページ番号が不正です");
        String link=url+(url.contains("?")?"&":"?")+"p="+page;
        Response received=getPage(link);
        CheckerResult result=parse(received.html,received.url);
        result.sourceUrl=url;
        return result;
    }

    public static CheckerResult parse(String html,String base) {
        CheckerResult result=new CheckerResult();
        if(html==null||html.isEmpty())return result;
        Document doc=Jsoup.parse(html,base);
        // Compact ranking and 24 hourly counts when present in the source page.
        // Never synthesize an official rank if the site did not provide one.
        String flat=doc.text().replaceAll("\\s+"," ");
        Matcher placement=Pattern.compile("(\\d[\\d,]*)\\s*位\\s*/\\s*(\\d[\\d,]*)\\s*ID中").matcher(flat);
        if(placement.find()) result.rank=placement.group(1)+"位 / "+placement.group(2)+" ID中";
        Matcher hourly=Pattern.compile("書き込み数\\s+((?:\\d+\\s+){24})\\d+").matcher(flat);
        if(hourly.find()){
            String[] values=hourly.group(1).trim().split("\\s+");
            if(values.length==24){
                result.hourlyCounts=new int[24];
                try{for(int k=0;k<24;k++)result.hourlyCounts[k]=Integer.parseInt(values[k]);}
                catch(NumberFormatException ignored){result.hourlyCounts=null;}
            }
        }
        Matcher count=NUMBER.matcher(html);
        if(count.find()){
            if(result.rank.isEmpty())result.rank=Jsoup.parseBodyFragment(count.group(1)).text();
            result.rank=result.rank.replaceAll("(\\d)\\s*ID中","$1 ID中");
            try{result.reportedPostCount=Integer.parseInt(count.group(2));}
            catch(NumberFormatException ignored){}
        }
        int threadStart=html.indexOf("書き込んだスレッド一覧");
        int postsStart=html.indexOf("書き込みレス一覧");
        if(threadStart>=0&&postsStart>threadStart){
            String section=html.substring(threadStart,postsStart);
            Document fragment=Jsoup.parseBodyFragment(section,base);
            for(Element link:fragment.select("a[href]")){
                String absolute=link.absUrl("href");
                String title=link.text().trim();
                if(!absolute.isEmpty()&&!title.isEmpty())result.threads.putIfAbsent(absolute,title);
            }
            // Names are the short lines in the first table cell of the original summary.
            Element first=fragment.selectFirst("td");
            if(first!=null){
                LinkedHashSet<String> names=new LinkedHashSet<>();
                for(String line:first.html().split("(?i)<br\\s*/?>")){
                    String name=Jsoup.parseBodyFragment(line).text().trim();
                    if(!name.isEmpty()&&name.length()<128&&!name.contains("書き込んだスレッド一覧"))names.add(name);
                }
                result.names.addAll(names);
            }
        }
        String section=postsStart<0?html:html.substring(postsStart);
        Matcher posts=POST.matcher(section);
        java.util.HashSet<String> seen=new java.util.HashSet<>();
        parseDomPosts(section,base,result,seen);
        if(result.posts.isEmpty())while(posts.find()){
            Element anchor=Jsoup.parseBodyFragment(
                    "<a href=\""+posts.group(1)+"\">open</a>",base).selectFirst("a");
            if(anchor==null)continue;
            String link=anchor.absUrl("href");
            if(!(link.startsWith("http://")||link.startsWith("https://")))continue;
            String thread=Jsoup.parseBodyFragment(posts.group(2)).text().trim();
            String number=posts.group(3);
            String name=PostFormatter.normalizedInfo(Jsoup.parseBodyFragment(posts.group(4)).text());
            String extras=PostFormatter.normalizedInfo(Jsoup.parseBodyFragment(posts.group(5)).text());
            String meta=PostFormatter.normalizedInfo(number+" "+name+" "+extras);
            String body=Jsoup.parseBodyFragment(posts.group(6)).wholeText().trim();
            // The site can list duplicate blocks if all-mode is enabled.
            if(seen.add(link+"|"+number)){
                result.posts.add(new CheckerResult.Post(thread,name,meta,body,link));
                result.threads.putIfAbsent(PostIndex.openableThreadUrl(link),thread);
            }
        }
        // Some layouts put attributes or extra elements around the header,
        // making direct HTML regex matching impossible. Parse <dl>/<dd> DOM
        // containers rather than returning a misleading "0 posts" result.
        PostIndex.collectNames(result);
        if(result.posts.isEmpty()) {
            Document structure=Jsoup.parseBodyFragment(section,base);
            result.diagnostic="HTML構造: 見出し="+(postsStart>=0)
                    +", dl="+structure.select("dl").size()
                    +", dt="+structure.select("dt").size()
                    +", dd="+structure.select("dd").size()
                    +", スレURL="+structure.select("a[href*=read.cgi]").size();
        }
        return result;
    }
    /**
     * Hissi outputs one dt/dd pair per post. The post number lives in both
     * the URL and dt text, and is reliable even if the legacy Shift-JIS
     * separator was already replaced with U+FFFD by the server or browser.
     */
    private static final Pattern RESPONSE_URL=Pattern.compile("/(\\d{1,6})/?(?:[?#].*)?$");
    private static final Pattern LEADING_NUMBER=Pattern.compile("^\\s*(\\d{1,6})\\b");
    private static final Pattern DATE_IN_HEADER=Pattern.compile("20\\d{2}[/.-]\\d{1,2}[/.-]\\d{1,2}");
    private static final Pattern MAIL_IN_HEADER=Pattern.compile("\\[[^]]*\\]");
    private static void parseDomPosts(String html,String base,CheckerResult result,java.util.Set<String> seen){
        Document doc=Jsoup.parseBodyFragment(html,base);
        for(Element dd:doc.select("dd")){
            // The actual site places dt and dd next to one another inside dl.
            // Ignore unrelated <dd> descriptions without a matching header.
            Element dt=dd.previousElementSibling();
            if(dt==null || !dt.normalName().equals("dt"))continue;
            Element link=null;
            for(Element a:dt.select("a[href]")){
                if(isThreadLink(a.absUrl("href"))){link=a;break;}
            }
            if(link==null)continue;
            String href=link.absUrl("href");
            Matcher urlNumber=RESPONSE_URL.matcher(href);
            if(!urlNumber.find())continue;
            String number=urlNumber.group(1);
            Element withoutLink=dt.clone();
            withoutLink.select("a[href]").remove();
            String raw=PostFormatter.normalizedInfo(withoutLink.text());
            Matcher displayedNumber=LEADING_NUMBER.matcher(raw);
            if(!displayedNumber.find() || !number.equals(displayedNumber.group(1)))continue;
            String tail=raw.substring(displayedNumber.end()).trim();
            // A broken UTF-8 interpretation of fullwidth ':' appears as "�F".
            tail=tail.replaceFirst("^(?:[:：]|\\uFFFD[Ff])\\s*","");
            if(!DATE_IN_HEADER.matcher(tail).find())continue;
            String author=tail;
            Matcher date=DATE_IN_HEADER.matcher(author);
            if(date.find())author=author.substring(0,date.start());
            Matcher mail=MAIL_IN_HEADER.matcher(author);
            if(mail.find())author=author.substring(0,mail.start());
            // Parenthesized 5ch connection metadata follows the author, not
            // the name. Never incorporate it into name-tab grouping.
            author=author.replaceFirst("\\s+[（(][^）)]*[）)]\\s*$","").trim();
            author=PostIndex.cleanAuthor(author);
            String meta=PostFormatter.normalizedInfo(number+" "+tail)
                    .replaceAll("\\uFFFD[Ff](?=20\\d{2}[/.-])"," ");
            String title=link.text().trim();
            String body=dd.wholeText().trim();
            if(seen.add(href+"|"+number)){
                result.posts.add(new CheckerResult.Post(title,author,meta,body,href));
                result.threads.putIfAbsent(PostIndex.openableThreadUrl(href),title);
            }
        }
    }
    private static boolean isThreadLink(String href){
        return (href.startsWith("http://") || href.startsWith("https://"))
                && (href.contains("/test/read.cgi/") || href.contains("/sdemo/r/e_e_"));
    }
    private static String enc(String value){return URLEncoder.encode(value,StandardCharsets.UTF_8);}
}
