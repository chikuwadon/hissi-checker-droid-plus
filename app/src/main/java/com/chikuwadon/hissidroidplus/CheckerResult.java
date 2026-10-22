package com.chikuwadon.hissidroidplus;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Shared immutable-ish transport model independent of UI widgets. */
public final class CheckerResult {
    public static final class Post {
        public final String title, author, meta, body, link;
        public Post(String title,String author,String meta,String body,String link){
            this.title=title;this.author=author;this.meta=meta;this.body=body;this.link=link;
        }
    }
    public String rank="";
    public String sourceUrl="";
    public String diagnostic="";
    public int reportedPostCount=-1;
    public int[] hourlyCounts;
    public int referencedCount=-1;
    public int reportedThreadCount=-1;
    public final List<Post> posts=new ArrayList<>();
    public final Map<String,String> threads=new LinkedHashMap<>();
    public final List<String> names=new ArrayList<>();
}
