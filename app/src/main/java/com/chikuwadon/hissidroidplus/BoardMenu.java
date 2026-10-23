package com.chikuwadon.hissidroidplus;

import org.json.JSONArray;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/** Fetch the 5ch official BBS menu, group by category, and retain last successful response. */
public final class BoardMenu {
    public static final String JSON_URL="https://menu.5ch.io/bbsmenu.json";
    public static final String HTML_URL="https://menu.5ch.io/bbsmenu.html";
    public static final class Category {
        public final String name;
        public final List<BoardCatalog.Board> boards=new ArrayList<>();
        Category(String name){this.name=name;}
    }
    private BoardMenu(){}
    public static List<Category> fetch() throws IOException {
        IOException error;
        try {
            List<Category> parsed=parseJson(HttpTransport.get(JSON_URL,StandardCharsets.UTF_8));
            if(!parsed.isEmpty())return parsed;
            error=new IOException("公式JSONに板がありません");
        } catch(Exception e){error=new IOException("公式JSONを取得できません",e);}
        try {
            List<Category> parsed=parseHtml(HttpTransport.get(HTML_URL,StandardCharsets.UTF_8));
            if(!parsed.isEmpty())return parsed;
        } catch(Exception e){error.addSuppressed(e);}
        throw error;
    }
    public static List<Category> parseJson(String raw) throws Exception {
        JSONObject root=new JSONObject(raw);
        JSONArray groups=root.optJSONArray("menu_list");
        if(groups==null)throw new IllegalArgumentException("menu_list not found");
        List<Category> result=new ArrayList<>();
        for(int i=0;i<groups.length();i++){
            JSONObject g=groups.optJSONObject(i);if(g==null)continue;
            String title=g.optString("category_name", "").trim();
            if(title.isEmpty())continue;
            JSONArray items=g.optJSONArray("category_content");if(items==null)continue;
            Category group=new Category(title);
            LinkedHashSet<String> seen=new LinkedHashSet<>();
            for(int j=0;j<items.length();j++){
                JSONObject entry=items.optJSONObject(j);if(entry==null)continue;
                String label=entry.optString("board_name", "").trim();
                String code=entry.optString("directory_name", "").trim();
                String address=entry.optString("url", "");
                if(!validBoard(address,code)||label.isEmpty()||!seen.add(code))continue;
                group.boards.add(new BoardCatalog.Board(code,label));
            }
            if(!group.boards.isEmpty())result.add(group);
        }
        if(result.isEmpty())throw new IllegalArgumentException("No boards in official menu");
        return result;
    }
    private static boolean validBoard(String url,String code){
        if(!code.matches("[A-Za-z0-9_]{1,60}"))return false;
        try {
            URI uri=URI.create(url);
            String host=uri.getHost();
            if(host==null||!(host.endsWith(".5ch.io")||host.endsWith(".5ch.net")||host.equals("5ch.io")))return false;
            if(!"https".equalsIgnoreCase(uri.getScheme())&&!"http".equalsIgnoreCase(uri.getScheme()))return false;
            String path=uri.getPath();
            return path!=null&&path.matches("/"+code+"/?");
        }catch(Exception e){return false;}
    }
    public static List<Category> parseHtml(String html){
        Document doc=Jsoup.parse(html,HTML_URL);
        // Classic BBSMENU uses bolded category labels followed by links. Do not
        // invent board categories when the format changes.
        LinkedHashMap<String,Category> groups=new LinkedHashMap<>();
        String category="";
        for(Element element:doc.body().getAllElements()){
            if(element.tagName().matches("h2|h3|h4|b|strong") && element.children().isEmpty()){
                String text=element.text().trim();
                if(!text.isEmpty()&&text.length()<42)category=text;
            }else if(element.tagName().equals("a")&&!category.isEmpty()){
                String url=element.absUrl("href");
                String path;
                try{path=URI.create(url).getPath();}catch(Exception e){continue;}
                if(path==null||!path.matches("/[A-Za-z0-9_]+/?"))continue;
                String code=path.replace("/","");
                if(!validBoard(url,code))continue;
                Category group=groups.computeIfAbsent(category,Category::new);
                String title=element.text().trim();
                if(title.isEmpty())continue;
                boolean exists=false;
                for(BoardCatalog.Board b:group.boards)if(b.code.equals(code)){exists=true;break;}
                if(!exists)group.boards.add(new BoardCatalog.Board(code,title));
            }
        }
        List<Category> out=new ArrayList<>();for(Category g:groups.values())if(!g.boards.isEmpty())out.add(g);
        return out;
    }
}
