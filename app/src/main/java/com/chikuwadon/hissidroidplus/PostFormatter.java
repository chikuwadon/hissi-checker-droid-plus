package com.chikuwadon.hissidroidplus;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Canonical copied post formatting. Reference counts are intentionally display-only. */
public final class PostFormatter {
    private PostFormatter(){}
    private static final Pattern EMPTY_MAIL=Pattern.compile("\\[\\s*\\]");
    private static final Pattern MAIL=Pattern.compile("\\[\\s*([^]\\s][^]]*?)\\s*\\]");
    private static final Pattern DATE_SEPARATOR=Pattern.compile("\\s*[:：]\\s*(?=20\\d{2}[/.-])");
    private static final Pattern ID=Pattern.compile("(?i)\\bID\\s*[:：]\\s*([A-Za-z0-9+./_-]+)");
    private static final Pattern NUMBER=Pattern.compile("^\\s*(\\d{1,6})(?:\\(\\d+\\))?\\s*[:：]?\\s*");
    private static final Pattern DATE=Pattern.compile("20\\d{2}/\\d{1,2}/\\d{1,2}");
    public static String normalizedInfo(String info){
        if(info==null)return "";
        String normalized=EMPTY_MAIL.matcher(info).replaceAll(" ");
        normalized=MAIL.matcher(normalized).replaceAll(" $1 ");
        normalized=DATE_SEPARATOR.matcher(normalized).replaceAll(" ");
        return normalized.replaceAll("\\s+"," ").trim();
    }
    public static String id(CheckerResult.Post p){
        Matcher m=ID.matcher(p.author+" "+p.meta);
        return m.find()?"ID:"+m.group(1):"";
    }
    /** Canonical post number, name, optional mail, date and ID. */
    public static String header(CheckerResult.Post p){
        String meta=p.meta==null?"":p.meta;
        String source=DATE.matcher(meta).find()?meta:meta+" "+p.author;
        source=normalizedInfo(source);
        Matcher number=NUMBER.matcher(source);
        if(number.find())source=number.group(1)+" "+source.substring(number.end()).trim();
        // Saved Edge HTML may repeat number/name after the ID. Keep only one header.
        Matcher id=ID.matcher(source);
        if(id.find()){
            source=source.substring(0,id.end());
            source=source.replaceFirst("(?i)ID\\s*[:：]\\s*", "ID:");
        }
        return source.trim().replaceAll("\\s+"," ");
    }
    public static String full(CheckerResult.Post p){
        return header(p)+"\n"+(p.body==null?"":p.body.trim());
    }
    public static String withThread(CheckerResult.Post p){
        StringBuilder out=new StringBuilder();
        if(!p.title.trim().isEmpty())out.append(p.title.trim()).append('\n');
        if(!p.link.trim().isEmpty())out.append(p.link.trim()).append('\n');
        return out.append(full(p)).toString().trim();
    }
    public static String append(String old,String next){
        String a=old==null?"":old.trim(),b=next==null?"":next.trim();
        if(a.isEmpty())return b;
        if(b.isEmpty())return a;
        return a+"\n\n"+b;
    }
}