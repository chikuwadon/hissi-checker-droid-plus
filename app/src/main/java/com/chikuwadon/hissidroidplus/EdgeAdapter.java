package com.chikuwadon.hissidroidplus;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.regex.Matcher;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

/** HTML format adapter for Edge's kyodemo checker, isolated from Activity and 5ch parser. */
public final class EdgeAdapter {
    private static final String BLOCKS="article, li.post, tr.post, .post, .res, .post-item, "
            +".response, .resitem, .res-item, .res-row, .post-row, .posting, dl";
    private static final String BODIES="blockquote, .message, .body, .post-content, .resbody, "
            +".res-body, .res-text, .message-body, .post-body, .posttext, .post_message, "
            +".comment-body, .comment, .msg, .text-content, dd";
    private static final Pattern POST_DATE=Pattern.compile("20\\d{2}[/-]\\d{1,2}[/-]\\d{1,2}");
    public EdgeAdapter() {}

    public CheckerResult load(CheckerQuery query)throws IOException {
        String html=HttpTransport.get(query.url,Charset.forName("Shift_JIS"));
        CheckerResult result=parse(html,query);
        result.sourceUrl=query.url;
        result.diagnostic=describePage(html);
        return result;
    }
    public static CheckerResult parse(String html,CheckerQuery query) {
        CheckerResult result=new CheckerResult();
        if(html==null || html.isEmpty())return result;
        Document doc=Jsoup.parse(html,query.url);
        // Actual kyodemo saved-page layout (MHT, 2026-10): each thread
        // section has an h2 and direct .post children; body is a text node.
        parseKyodemo(doc,result);
        if(!result.posts.isEmpty()) {
            PostIndex.collectNames(result);
            result.diagnostic=describePage(html);
            return result;
        }
        Set<String> seen=new HashSet<>();
        for(Element node:doc.select(BLOCKS)) parseBlock(node,result,seen,false);
        // Pages without CSS classes: search around post links, not navigation links.
        for(Element a:doc.select("a[href]")) {
            if(!isThreadLink(a.absUrl("href")))continue;
            Element ancestor=a;
            for(int depth=0;depth<7&&ancestor!=null;depth++,ancestor=ancestor.parent()) {
                String type=ancestor.tagName();
                if(type.equals("article") || type.equals("tr") || type.equals("li") ||
                   type.equals("dl") || type.equals("section") ||
                   (type.equals("div") && (ancestor.is(".res,.post,.response,.post-item,.res-row,.posting")
                     || ancestor.selectFirst(BODIES)!=null))) {
                    parseBlock(ancestor,result,seen,false);
                    break;
                }
            }
        }
        // Fallback for text-only rows: still require both a real thread link and
        // a date/ID within the same row, to avoid reporting navigation as posts.
        if(result.posts.isEmpty()) {
            for(Element row:doc.select("tr, li, article, dl, .res-row, .post-row"))
                parseBlock(row,result,seen,true);
        }
        PostIndex.collectNames(result);
        result.diagnostic=describePage(html);
        return result;
    }
    private static final Pattern RES_DATE=Pattern.compile("20\\d{2}/\\d{2}/\\d{2}\\([^)]*\\)\\s+\\d{2}:\\d{2}:\\d{2}(?:\\.\\d+)?");
    private static final Pattern POST_HEAD=Pattern.compile("^\\d+\\s*[:：]\\s*");
    private static final Pattern SUMMARY=Pattern.compile("(?m)^(レス数|被参照数|スレッド数)\\s*[:：]\\s*(\\d+)");

    private static void parseKyodemo(Document doc, CheckerResult result) {
        Element summary=doc.selectFirst("textarea#copy-mess");
        if(summary!=null){
            String info=summary.val();
            Matcher match=SUMMARY.matcher(info);
            while(match.find()){
                int n;
                try{n=Integer.parseInt(match.group(2));}catch(NumberFormatException ignored){continue;}
                if(match.group(1).equals("レス数"))result.reportedPostCount=n;
                else if(match.group(1).equals("被参照数"))result.referencedCount=n;
                else result.reportedThreadCount=n;
            }
        }
        Set<String> seen=new HashSet<>();
        for(Element section:doc.select(".box.content, .box.post-group, .thread-content")) {
            Element heading=section.selectFirst("h2 a[href], h3 a[href], .thread-title a[href]");
            if(heading==null)continue;
            String title=heading.text().trim();
            String groupLink=heading.absUrl("href");
            if(title.isEmpty()||!isThreadLink(groupLink))continue;
            String root=PostIndex.openableThreadUrl(groupLink);
            result.threads.putIfAbsent(root,title);
            for(Element node:section.select(".post")) {
                Element head=node.selectFirst(".r-head");
                if(head==null)continue;
                Element link=head.selectFirst("a[href]");
                if(link==null)continue;
                String href=link.absUrl("href");
                if(!isThreadLink(href)||!seen.add(href))continue;
                String meta=head.text().trim();
                String raw=POST_HEAD.matcher(meta).replaceFirst("").trim();
                String author="";
                Matcher date=RES_DATE.matcher(raw);
                if(date.find()) author=raw.substring(0,date.start()).trim();
                else author=raw.replaceFirst("\\s+ID:.*$", "").trim();
                Element bodyNode=node.clone();
                Element bodyHead=bodyNode.selectFirst(".r-head");
                if(bodyHead!=null)bodyHead.remove();
                String body=bodyNode.wholeText().replace('\u00a0',' ').trim();
                if(body.isEmpty())body=bodyNode.text().trim();
                if(body.isEmpty()||body.length()>30000)continue;
                result.posts.add(new CheckerResult.Post(title,author,meta,body,href));
                if(!author.isEmpty()&&!result.names.contains(author))result.names.add(author);
            }
        }
    }

    private static void parseBlock(Element node,CheckerResult result,Set<String> seen,boolean allowLoose){
        Element anchor=null;
        for(Element a:node.select("a[href]")) {
            if(isThreadLink(a.absUrl("href"))){anchor=a;break;}
        }
        if(anchor==null)return;
        String href=anchor.absUrl("href");
        Element content=node.selectFirst(BODIES);
        if(content==null && allowLoose && POST_DATE.matcher(node.text()).find()) {
            // Only a dated post row may use a generic body container.
            for(Element part:node.select("p, td, .content, .text")) {
                if(part==anchor||!part.select("a[href]").isEmpty())continue;
                if(part.text().trim().length()>6) {content=part;break;}
            }
        }
        if(content==null)return;
        String body=content.wholeText().trim();
        if(body.isEmpty() || body.length()>30000)return;
        if(!seen.add(href+"\n"+body))return;
        String title=node.select("h2,h3,.thread-title,.thread_title,.title").text();
        if(title.isEmpty())title=anchor.attr("title").trim();
        if(title.isEmpty())title=anchor.text().trim();
        String author=node.select(".name,.username,.resname,.res-name,.post-name,.author,.poster-name").text();
        String meta=node.select(".date,.time,.meta,.resdate,.post-time,.poster-meta").text();
        result.posts.add(new CheckerResult.Post(title,author,meta,body,href));
        if(!author.isEmpty()&&!result.names.contains(author))result.names.add(author);
        result.threads.putIfAbsent(PostIndex.openableThreadUrl(href),title.isEmpty()?"スレッド":title);
    }
    static boolean isThreadLink(String href){
        if(href==null || href.isEmpty())return false;
        return href.contains("bbs.eddibb.cc/test/read.cgi/") ||
               href.matches("https?://bbs\\.eddibb\\.cc/[a-zA-Z0-9_]+/\\d{10,20}.*") ||
               href.contains("/sdemo/r/e_e_");
    }
    /** Classify a failed parse without manufacturing posts from navigation or error pages. */
    public static String explainEmptyPage(String html){
        if(html==null||html.trim().isEmpty()) return "取得したページが空です";
        String plain=Jsoup.parse(html).text().toLowerCase(Locale.ROOT);
        if(plain.contains("403 forbidden")||plain.contains("access denied")||plain.contains("error 403"))
            return "アクセスが拒否されています（HTTP 403）";
        if(plain.contains("captcha")||plain.contains("cloudflare")||plain.contains("verify you are human")||plain.contains("just a moment"))
            return "サイト側の認証・アクセス制限画面です";
        if(plain.contains("404 not found"))return "ページが見つかりません（404）";
        return "ページは取得できましたが、レスを抽出できません（HTML構造未対応の可能性）";
    }
    /** Diagnostic contains only structural counts, never post content, IDs or URLs. */
    public static String describePage(String html){
        if(html==null||html.isEmpty())return "受信内容なし";
        Document doc=Jsoup.parse(html);
        int links=doc.select("a[href]").size();int thread=0;
        for(Element a:doc.select("a[href]")) if(isThreadLink(a.absUrl("href")))thread++;
        // absUrl needs a base URI: count also raw absolute/relative thread paths.
        if(thread==0) for(Element a:doc.select("a[href]")) {
            String link=a.attr("href");
            if(link.contains("/sdemo/r/e_e_") || link.contains("/test/read.cgi/"))thread++;
        }
        int bodies=doc.select(BODIES).size();int blocks=doc.select(BLOCKS).size();
        String all=doc.text().toLowerCase(Locale.ROOT);
        boolean blocked=all.contains("403 forbidden")||all.contains("access denied")||
                all.contains("captcha")||all.contains("cloudflare")||all.contains("verify you are human");
        return "HTML="+html.length()+"文字 / リンク="+links+
                " / スレリンク="+thread+" / 本文候補="+bodies+
                " / 投稿候補="+blocks+(blocked?" / アクセス制限の疑い":"");
    }
}
