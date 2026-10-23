package com.chikuwadon.hissidroidplus;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

/**
 * Read ONLY analysis already supplied by kyodemo. Never synthesize ratings
 * locally. The captured 2026-10-23 site has #wt and #toggle-w-tawindow.
 */
public final class EdgeAnalysis {
    public static final class Section {
        public final String title,body;
        Section(String title,String body){this.title=title;this.body=body;}
    }
    public final List<Section> sections;
    public final boolean startAvailable;
    public final boolean hasStartButton;
    private EdgeAnalysis(List<Section> sections,boolean available,boolean hasButton){
        this.sections=Collections.unmodifiableList(sections);
        this.startAvailable=available;
        this.hasStartButton=hasButton;
    }
    public boolean hasResult(){return !sections.isEmpty();}
    public static EdgeAnalysis parse(String html){
        Document doc=Jsoup.parse(html==null?"":html);
        Element start=null;
        for(Element e:doc.select("a.button, button")){
            String text=e.text().trim();
            if(text.equals("分析") || text.equals("分析を開始")){start=e;break;}
        }
        boolean button=start!=null;
        boolean available=button&&!start.hasAttr("disabled")
                && !start.hasClass("is-disabled");
        List<Section> sections=new ArrayList<>();
        Element box=doc.selectFirst("#toggle-w-tawindow .box.d-box");
        if(box!=null){
            String title=null;
            StringBuilder body=new StringBuilder();
            for(Element child:box.children()){
                if(child.hasClass("title")){
                    if(title!=null)appendSection(sections,title,body);
                    title=child.text().trim();
                    body.setLength(0);
                }else if(title!=null){
                    String s=child.tagName().equals("br")?"\n":child.wholeText();
                    if(s!=null&&!s.trim().isEmpty()) {
                        if(body.length()>0)body.append('\n');
                        body.append(s.trim());
                    }
                }
            }
            if(title!=null)appendSection(sections,title,body);
        }
        return new EdgeAnalysis(sections,available,button);
    }
    private static void appendSection(List<Section> out,String title,StringBuilder text){
        if(!title.isEmpty()&&!text.toString().trim().isEmpty())
            out.add(new Section(title,text.toString().trim()));
    }
}
