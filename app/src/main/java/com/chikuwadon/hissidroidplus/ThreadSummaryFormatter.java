package com.chikuwadon.hissidroidplus;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Formats the full available per-ID thread list without inventing site statistics. */
public final class ThreadSummaryFormatter {
    private static final Pattern DATE=Pattern.compile("(20\\d{2})[/.-](\\d{1,2})[/.-](\\d{1,2})");
    private ThreadSummaryFormatter(){}

    public static String format(CheckerQuery query,CheckerResult result){
        if(result==null)return "";
        // Kyodemo exposes its own copy-ready text, including per-thread
        // counts and the number of threads created. Preserve that information.
        String fromSite=result.sourceThreadSummary;
        if(fromSite!=null && !fromSite.trim().isEmpty())
            return fromSite.trim();
        LinkedHashMap<String,String> threads=PostIndex.uniqueThreads(result);
        if(threads.isEmpty())return "";
        String date=query==null?"":query.date;
        if(!date.matches("\\d{8}")){
            if(query!=null && query.site==CheckerQuery.Site.HISSI){
                Matcher urlDate=Pattern.compile("/read\\.php/[A-Za-z0-9_]+/(\\d{8})/").matcher(query.url);
                if(urlDate.find())date=urlDate.group(1);
            }
            if(!date.matches("\\d{8}"))for(CheckerResult.Post post:result.posts){
                Matcher m=DATE.matcher(post.meta);
                if(m.find()){
                    date=String.format(java.util.Locale.ROOT,"%s%02d%02d",m.group(1),
                        Integer.parseInt(m.group(2)),Integer.parseInt(m.group(3)));
                    break;
                }
            }
        }
        String id=query==null?"":query.id;
        if(id==null || id.isEmpty())for(CheckerResult.Post post:result.posts){
            String found=PostFormatter.id(post);
            if(!found.isEmpty()){id=found.substring(3);break;}
        }
        String url=result.sourceUrl.isEmpty()?(query==null?"":query.url):result.sourceUrl;
        StringBuilder b=new StringBuilder();
        if(date!=null && date.matches("\\d{8}"))
            b.append("日付: ").append(date,0,4).append('/').append(date,4,6)
                .append('/').append(date,6,8).append('\n');
        b.append("レス数: ").append(result.reportedPostCount>=0?
                result.reportedPostCount:result.posts.size()).append('\n');
        if(result.referencedCount>=0)
            b.append("被参照数: ").append(result.referencedCount).append('\n');
        b.append("スレッド数: ").append(result.reportedThreadCount>=0?
                result.reportedThreadCount:threads.size()).append('\n');
        b.append("ID:").append(id==null?"":id).append(" の書き込みスレッド一覧：\n");
        Map<String,Integer> counts=PostIndex.threadPostCounts(result);
        for(Map.Entry<String,String> entry:threads.entrySet()){
            String title=entry.getValue()==null || entry.getValue().trim().isEmpty()?
                "スレッド":entry.getValue().trim();
            int count=counts.getOrDefault(PostIndex.threadKey(entry.getKey()),0);
            b.append(title);
            if(count>0)b.append(" (").append(count).append(')');
            b.append('\n');
        }
        if(url!=null && !url.isEmpty())b.append(url).append('\n');
        return b.toString().trim();
    }
}
