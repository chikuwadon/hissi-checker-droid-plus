package com.chikuwadon.hissidroidplus;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

/** Parses link-based site search without inventing thread keys or posts. */
public final class EdgeSearchResults {
    public static final class Item {
        public final String title,checkerUrl;
        Item(String title,String checkerUrl){this.title=title;this.checkerUrl=checkerUrl;}
    }
    private EdgeSearchResults(){}
    private static final Pattern VISIBLE_DATE=Pattern.compile(
        "(?<![0-9])(20[0-9]{2})[/-]([0-9]{1,2})[/-]([0-9]{1,2})(?![0-9])");
    /** Use a date written in the site's search row if available; otherwise the
     * thread's creation date (JST) is only a starting point for opening the page.
     * This is not an assertion that every post in the thread has this date.
     */
    static String fallbackDateForLink(Element link,String href){
        Element row=link.closest("tr, li, article, .thread-item, .search-result");
        if(row!=null){
            Matcher match=VISIBLE_DATE.matcher(row.text());
            if(match.find())try{
                LocalDate date=LocalDate.of(Integer.parseInt(match.group(1)),
                    Integer.parseInt(match.group(2)),Integer.parseInt(match.group(3)));
                return date.format(DateTimeFormatter.BASIC_ISO_DATE);
            }catch(Exception ignored){}
        }
        try{
            Matcher m=Pattern.compile("/([0-9]{10})(?:/|$)").matcher(
                java.net.URI.create(href).getPath());
            if(m.find())return Instant.ofEpochSecond(Long.parseLong(m.group(1)))
                .atZone(ZoneId.of("Asia/Tokyo")).toLocalDate()
                .format(DateTimeFormatter.BASIC_ISO_DATE);
        }catch(Exception ignored){}
        return "";
    }
    /** A thread cannot contain a post dated before its creation (Edge timestamps are JST).
     * Only treat 10-digit Unix thread keys as creation times; other formats remain eligible.
     */
    static boolean threadStartsAfterSelectedDay(String checkerUrl,String date){
        try{
            CheckerQuery q=CheckerQuery.parseUrl(checkerUrl);
            if(!q.key.matches("[0-9]{10}"))return false;
            LocalDate created=Instant.ofEpochSecond(Long.parseLong(q.key))
                    .atZone(ZoneId.of("Asia/Tokyo")).toLocalDate();
            LocalDate selected=LocalDate.parse(date,java.time.format.DateTimeFormatter.BASIC_ISO_DATE);
            return created.isAfter(selected);
        }catch(Exception ignored){return false;}
    }
    public static List<Item> parse(String html,String url,String board,String date,String id){
        Document doc=Jsoup.parse(html==null?"":html,url);
        LinkedHashMap<String,Item> unique=new LinkedHashMap<>();
        for(Element anchor:doc.select("a[href]")){
            String href=anchor.absUrl("href");
            String day=date==null?fallbackDateForLink(anchor,href):date;
            if(day.isEmpty())continue;
            String checker=EdgeSearchLink.checkerFromThread(board,day,id,href);
            if(checker.isEmpty()||threadStartsAfterSelectedDay(checker,date))continue;
            String title=anchor.text().trim();
            if(title.isEmpty()||title.matches("\\d+")) {
                Element nearby=anchor.closest("li, tr, article, div.post, .thread-item");
                if(nearby!=null)title=nearby.text().trim();
            }
            if(title.isEmpty())title="スレッド "+(unique.size()+1);
            if(title.length()>180)title=title.substring(0,180);
            unique.putIfAbsent(checker,new Item(title,checker));
        }
        return new ArrayList<>(unique.values());
    }
}
