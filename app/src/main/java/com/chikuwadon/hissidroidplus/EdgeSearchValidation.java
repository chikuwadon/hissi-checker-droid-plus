package com.chikuwadon.hissidroidplus;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Verify the date and ID in each real post header, not in URL query parameters
 * or the site's page title. Used only for searches initiated with a date on
 * the home screen; externally opened checker links retain their old behavior.
 */
public final class EdgeSearchValidation {
    private static final Pattern DATE=Pattern.compile("(?<!\\d)(20\\d{2})[/-](\\d{1,2})[/-](\\d{1,2})(?!\\d)");
    private static final Pattern ID=Pattern.compile("(?i)(?:^|[^A-Za-z0-9+./_-])ID\\s*[:：]\\s*([A-Za-z0-9+./_-]{1,64})(?=$|[^A-Za-z0-9+./_-])");
    private EdgeSearchValidation(){}

    public static boolean matches(CheckerResult.Post post, String date, String id){
        if(post==null||date==null||id==null||post.meta==null)return false;
        Matcher foundId=ID.matcher(post.meta);
        boolean verifiedId=false;
        while(foundId.find())if(id.equals(foundId.group(1))){verifiedId=true;break;}
        if(!verifiedId)return false;
        Matcher day=DATE.matcher(post.meta);
        while(day.find()){
            String actual=String.format(Locale.ROOT,"%04d%02d%02d",
                Integer.parseInt(day.group(1)),Integer.parseInt(day.group(2)),Integer.parseInt(day.group(3)));
            if(date.equals(actual))return true;
        }
        return false;
    }

    public static CheckerResult matchingPosts(CheckerResult source,String date,String id){
        CheckerQuery.validateDate(date);
        CheckerResult verified=new CheckerResult();
        verified.rank=source.rank;
        verified.sourceUrl=source.sourceUrl;
        verified.diagnostic=source.diagnostic;
        verified.hourlyCounts=null;
        for(CheckerResult.Post post:source.posts)if(matches(post,date,id)){
            verified.posts.add(post);
            if(post.link!=null && !post.link.isEmpty())
                verified.threads.putIfAbsent(PostIndex.openableThreadUrl(post.link),
                    post.title.isEmpty()?"スレッド":post.title);
        }
        verified.reportedPostCount=verified.posts.size();
        verified.reportedThreadCount=verified.threads.size();
        verified.referencedCount=-1;
        PostIndex.collectNames(verified);
        return verified;
    }
}
