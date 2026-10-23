package com.chikuwadon.hissidroidplus;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Bounded recent history and an independent, non-evicting favorites collection. */
public final class SearchHistory {
    private static final String STORE="search_history_store";
    private static final String RECENT="recent_v1", FAVORITES="favorites_v1";
    private static final int LIMIT=100;
    private SearchHistory(){}

    public static final class Entry {
        public final String url,site,board,date,id;
        public final long viewedAt;
        public final int posts;
        Entry(String url,String site,String board,String date,String id,long viewedAt,int posts){
            this.url=url;this.site=site;this.board=board;this.date=date;this.id=id;
            this.viewedAt=viewedAt;this.posts=posts;
        }
        public String title(){return ("edge".equals(site)?"エッヂ":"5ch")+" / "+(board.isEmpty()?"不明な板":board);}
        public String effectiveDate(){
            if(date!=null && date.matches("\\d{8}"))return date;
            // Older recorded 5ch URL entries had no date in CheckerQuery.
            java.util.regex.Matcher m=java.util.regex.Pattern
                    .compile("/read\\.php/[A-Za-z0-9_]+/(\\d{8})/").matcher(url);
            return m.find()?m.group(1):(date==null?"":date);
        }
        public String detail(){
            String day=effectiveDate();
            String dateLabel=day.matches("\\d{8}")?
                    day.substring(0,4)+"/"+day.substring(4,6)+"/"+day.substring(6):day;
            String idLabel=id.isEmpty()?"":("  ID:"+id.replaceFirst("(?i)^ID\\s*[:：]\\s*",""));
            return (dateLabel.isEmpty()?"":dateLabel)+idLabel+"  レス:"+posts;
        }
    }
    private static SharedPreferences prefs(Context c){return c.getSharedPreferences(STORE,Context.MODE_PRIVATE);}
    private static List<Entry> load(Context c,String key){
        List<Entry> out=new ArrayList<>();
        String source=prefs(c).getString(key,"[]");
        try {
            JSONArray json=new JSONArray(source);
            for(int i=0;i<json.length();i++){
                JSONObject o=json.optJSONObject(i);
                if(o==null)continue;
                String url=o.optString("url","");
                if(!url.startsWith("https://")&&!url.startsWith("http://"))continue;
                out.add(new Entry(url,o.optString("site","5ch"),
                        o.optString("board",""),o.optString("date",""),o.optString("id",""),
                        o.optLong("time",0),o.optInt("posts",0)));
            }
        }catch(JSONException ignored){}
        return out;
    }
    private static void save(Context c,String key,List<Entry> list){
        JSONArray array=new JSONArray();
        for(Entry e:list){
            JSONObject obj=new JSONObject();
            try{
                obj.put("url",e.url);obj.put("site",e.site);obj.put("board",e.board);
                obj.put("date",e.date);obj.put("id",e.id);
                obj.put("time",e.viewedAt);obj.put("posts",e.posts);
                array.put(obj);
            }catch(JSONException ignored){}
        }
        prefs(c).edit().putString(key,array.toString()).apply();
    }
    public static synchronized void record(Context c,CheckerQuery q,CheckerResult result){
        if(q==null||result==null||result.posts.isEmpty())return;
        String url=result.sourceUrl;
        if(url==null||url.isEmpty())
            url=q.site==CheckerQuery.Site.HISSI?HissiAdapter.resultUrl(q.board,q.date,q.id):q.url;
        if(url==null||url.isEmpty())return;
        String id=q.id;
        if((id==null||id.isEmpty())&&!result.posts.isEmpty()){
            String extracted=PostFormatter.id(result.posts.get(0));
            if(extracted.startsWith("ID:"))id=extracted.substring(3);
        }
        Entry entry=new Entry(url,q.site==CheckerQuery.Site.EDGE?"edge":"5ch",
                q.board,(q.date.isEmpty()?dateFromUrl(url):q.date),
                id==null?"":id,System.currentTimeMillis(),
                result.reportedPostCount>=0?result.reportedPostCount:result.posts.size());
        List<Entry> recent=load(c,RECENT);
        recent.removeIf(e->e.url.equals(entry.url));
        recent.add(0,entry);
        if(recent.size()>LIMIT)recent=new ArrayList<>(recent.subList(0,LIMIT));
        save(c,RECENT,recent);
        List<Entry> saved=load(c,FAVORITES);
        for(int i=0;i<saved.size();i++)if(saved.get(i).url.equals(url)){
            saved.set(i,entry);
            save(c,FAVORITES,saved);
            break;
        }
    }
    private static String dateFromUrl(String url){
        java.util.regex.Matcher m=java.util.regex.Pattern.compile(
                "/read\\.php/[A-Za-z0-9_]+/(\\d{8})/").matcher(url);
        return m.find()?m.group(1):"";
    }
    public static synchronized List<Entry> recents(Context c){return load(c,RECENT);}
    public static synchronized List<Entry> favorites(Context c){return load(c,FAVORITES);}
    public static synchronized boolean isFavorite(Context c,String url){
        for(Entry e:load(c,FAVORITES))if(e.url.equals(url))return true;
        return false;
    }
    public static synchronized void toggleFavorite(Context c,Entry entry){
        List<Entry> favorites=load(c,FAVORITES);
        boolean removed=favorites.removeIf(e->e.url.equals(entry.url));
        if(!removed)favorites.add(0,entry);
        save(c,FAVORITES,favorites);
    }
    public static List<Entry> sorted(List<Entry> source,int mode){
        List<Entry> copy=new ArrayList<>(source);
        if(mode==1)copy.sort(Comparator.comparingLong(e->e.viewedAt));
        else if(mode==2)copy.sort(Comparator.comparing((Entry e)->e.board.toLowerCase(Locale.ROOT)).thenComparing((Entry e)->-e.viewedAt));
        else if(mode==3)copy.sort(Comparator.comparing((Entry e)->e.effectiveDate()).reversed().thenComparing((Entry e)->-e.viewedAt));
        else copy.sort(Comparator.comparingLong((Entry e)->e.viewedAt).reversed());
        return copy;
    }
}
