package com.chikuwadon.hissidroidplus;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
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
    public static List<Item> parse(String html,String url,String board,String date,String id){
        Document doc=Jsoup.parse(html==null?"":html,url);
        LinkedHashMap<String,Item> unique=new LinkedHashMap<>();
        for(Element anchor:doc.select("a[href]")){
            String href=anchor.absUrl("href");
            String checker=EdgeSearchLink.checkerFromThread(board,date,id,href);
            if(checker.isEmpty())continue;
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
