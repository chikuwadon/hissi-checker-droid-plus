package com.chikuwadon.hissidroidplus;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Single source of truth for all response copying and display formatting. */
public final class PostFormatter {
    private PostFormatter() {}
    private static final Pattern EMPTY_MAIL=Pattern.compile("\\[\\s*\\]");
    private static final Pattern MAIL=Pattern.compile("\\[\\s*([^]\\s][^]]*?)\\s*\\]");
    private static final Pattern DATE_SEPARATOR=Pattern.compile("\\s*[:：]\\s*(?=20\\d{2}[/.-])");
    private static final Pattern ID=Pattern.compile("(?i)\\bID\\s*[:：]\\s*([A-Za-z0-9+./_-]+)");
    public static String normalizedInfo(String info){
        if(info==null)return "";
        String normalized=EMPTY_MAIL.matcher(info).replaceAll(" ");
        normalized=MAIL.matcher(normalized).replaceAll(" $1 ");
        normalized=DATE_SEPARATOR.matcher(normalized).replaceAll(" ");
        return normalized.replaceAll("\\s+"," ").trim();
    }
    public static String id(CheckerResult.Post p){
        Matcher m=ID.matcher(p.author+" "+p.meta);
        return m.find()?m.group(1):"";
    }
    /** Response number first, followed by name, mail (if any), timestamp and ID. */
    public static String full(CheckerResult.Post p){
        String header=(p.meta.trim()+" "+normalizedInfo(p.author)).trim();
        return header+"\n"+p.body.trim();
    }
    /** Thread title and the post URL appear BEFORE the response. */
    public static String withThread(CheckerResult.Post p){
        StringBuilder text=new StringBuilder();
        if(!p.title.trim().isEmpty())text.append(p.title.trim()).append('\n');
        if(!p.link.trim().isEmpty())text.append(p.link.trim()).append('\n');
        text.append(full(p));
        return text.toString().trim();
    }
    public static String append(String existing,String addition){
        String a=existing==null?"":existing.trim();
        String b=addition==null?"":addition.trim();
        if(a.isEmpty())return b;
        if(b.isEmpty())return a;
        return a+"\n\n"+b;
    }
}
