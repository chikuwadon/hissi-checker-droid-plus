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
    private static final Pattern POST=Pattern.compile(
        "(?is)<a\\s+href\\s*=\\s*['\\\"]?(https?://[^\\s>]+?)\\s+target\\s*=\\s*['\\\"]?_blank['\\\"]?\\s*>"+
        "(.*?)</a>\\s*<br\\s*/?>\\s*(\\d{1,6})\\s*：\\s*<b>(.*?)<dd>(.*?)(?=<br\\s*/?>\\s*</dl>|</tr>|$)");
    private static final Pattern NUMBER=Pattern.compile("(?i)<font\\s+size\\s*=\\s*5\\s+color\\s*=\\s*#FF6347[^>]*>(.*?)</td>.*?<td\\s+width\\s*=\\s*40[^>]*>(\\d+)</td>",Pattern.DOTALL);

    public CheckerResult load(CheckerQuery query)throws IOException {
        String url=query.url;
        if(url.isEmpty()){
            String base="http://hissi.org/read.php/"+query.board+"/";
            String form="date="+enc(query.date)+"&ID="+enc(query.id);
            String response=HttpTransport.post(base+"search/",form,SJIS);
            Matcher m=REDIRECT.matcher(response);
            if(!m.find())throw new IOException("検索結果のリンクが見つかりません");
            String value=m.group(1);
            try{url=URI.create(base).resolve(value).toASCIIString();}
            catch(Exception ex){throw new IOException("検索結果URLを解釈できません",ex);}
            try {
                URI responseUrl=URI.create(url);
                String host=responseUrl.getHost();
                if(host==null||!(host.equalsIgnoreCase("hissi.org")||host.equalsIgnoreCase("www.hissi.org")) ||
                        !responseUrl.getPath().startsWith("/read.php/"))
                    throw new IOException("不正な検索結果URLです");
            }catch(IllegalArgumentException ex){throw new IOException("不正な検索結果URLです",ex);}
        }
        String all=url+(url.contains("?")?"&":"?")+"thread=all&name=all";
        String html=HttpTransport.get(all,SJIS);
        CheckerResult result=parse(html,all);
        // The site's normal HTML is the compatibility baseline. If the optional
        // all-mode query changes format, retry the unmodified result URL.
        if(result.posts.isEmpty()) {
            CheckerResult basic=parse(HttpTransport.get(url,SJIS),url);
            if(!basic.posts.isEmpty() || !basic.threads.isEmpty())result=basic;
        }
        result.sourceUrl=url;
        result.diagnostic=HttpTransport.lastDiagnostic();
        return result;
    }

    public CheckerResult nextPage(String url, int page) throws IOException {
        if(page<1 || page>20)throw new IOException("ページ番号が不正です");
        CheckerResult result=parse(HttpTransport.get(url+"&p="+page,SJIS),url);
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
        if(placement.find()) result.rank=placement.group(1)+"位 / "+placement.group(2)+"ID中";
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
        if(postsStart<0)return result;
        String section=html.substring(postsStart);
        Matcher posts=POST.matcher(section);
        while(posts.find()){
            String link=Jsoup.parseBodyFragment("<a href=\""+posts.group(1)+"\">open</a>",base).selectFirst("a").absUrl("href");
            if(!(link.startsWith("http://")||link.startsWith("https://")))continue;
            String thread=Jsoup.parseBodyFragment(posts.group(2)).text();
            String number=posts.group(3);
            String name=PostFormatter.normalizedInfo(Jsoup.parseBodyFragment(posts.group(4)).text());
            String body=Jsoup.parseBodyFragment(posts.group(5)).wholeText().trim();
            // The original site embeds HTML entities inside response bodies.
            // jsoup has already decoded entities. Avoid parsing text as markup twice.
            result.posts.add(new CheckerResult.Post(thread,name,number,body,link));
            result.threads.putIfAbsent(PostIndex.openableThreadUrl(link),thread);
        }
        PostIndex.collectNames(result);
        return result;
    }
    private static String enc(String value){return URLEncoder.encode(value,StandardCharsets.UTF_8);}
}
