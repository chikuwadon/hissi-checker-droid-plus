package com.chikuwadon.hissidroidplus;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Shared immutable-ish transport model independent of UI widgets. */
public final class CheckerResult {
    public static final class Post {
        public final String title, author, meta, body, link;
        /** Reference count is UI-only metadata. Never include it in copied text or names. */
        public final int referenceCount;
        public Post(String title,String author,String meta,String body,String link){
            this(title,author,meta,body,link,0);
        }
        public Post(String title,String author,String meta,String body,String link,int references){
            this.title=title;this.author=author;this.meta=meta;this.body=body;this.link=link;
            this.referenceCount=Math.max(0,references);
        }
    }
    public String rank="";
    public String sourceUrl="";
    public String diagnostic="";
    /** Optional copy-ready thread summary supplied by the source site. */
    public String sourceThreadSummary="";
    public int reportedPostCount=-1;
    public int[] hourlyCounts;
    public int referencedCount=-1;
    public int reportedThreadCount=-1;
    public final List<Post> posts=new ArrayList<>();
    public final Map<String,String> threads=new LinkedHashMap<>();
    public final List<String> names=new ArrayList<>();
}
