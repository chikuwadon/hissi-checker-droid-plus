package com.chikuwadon.hissidroidplus;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

/** Parse only site-generated analysis, with structural fallbacks for unknown sections. */
public final class EdgeAnalysis {
    public static final class Section {
        public final String title,body,notes;
        Section(String title,String body,String notes){
            this.title=title;this.body=body;this.notes=notes;
        }
    }
    public final List<Section> sections;
    public final boolean startAvailable,hasStartButton;
    private EdgeAnalysis(List<Section> items,boolean start,boolean button){
        sections=Collections.unmodifiableList(items);
        startAvailable=start;hasStartButton=button;
    }
    public boolean hasResult(){return !sections.isEmpty();}
    public static EdgeAnalysis parse(String html){
        Document doc=Jsoup.parse(html==null?"":html);
        Element start=null;
        for(Element e:doc.select("a.button, button")){
            String value=e.text().trim();
            if(value.equals("分析")||value.equals("分析を開始")){start=e;break;}
        }
        boolean found=start!=null;
        boolean available=found&&!start.hasAttr("disabled")
                && !start.hasClass("is-disabled")&&!start.hasClass("disabled");
        List<Section> result=new ArrayList<>();
        Element box=doc.selectFirst("#toggle-w-tawindow .box.d-box");
        if(box==null)box=doc.selectFirst("#toggle-w-tawindow .d-box");
        if(box==null)box=doc.selectFirst("#wt .d-box");
        if(box!=null){
            String title="";
            StringBuilder body=new StringBuilder();
            StringBuilder notes=new StringBuilder();
            for(Element child:box.children()){
                boolean heading=child.hasClass("title") ||
                        child.tagName().matches("h2|h3|h4");
                if(heading){
                    append(result,title,body,notes);
                    title=child.text().trim();
                    body.setLength(0);notes.setLength(0);
                }else if(!title.isEmpty()&&!child.tagName().equals("br")){
                    Element safe=child.clone();
                    for(Element note:safe.select("details.rating-reason")){
                        String detail=note.text().trim();
                        if(!detail.isEmpty()){
                            if(notes.length()>0)notes.append("\n");
                            notes.append(detail);
                        }
                        note.remove();
                    }
                    safe.select("script,style").remove();
                    String val=safe.wholeText().replace('\u00a0',' ').trim();
                    if(!val.isEmpty()){
                        if(body.length()>0)body.append("\n");
                        body.append(val);
                    }
                }
            }
            append(result,title,body,notes);
        }
        return new EdgeAnalysis(result,available,found);
    }
    private static void append(List<Section> out,String title,StringBuilder body,StringBuilder notes){
        String a=body.toString().trim(),b=notes.toString().trim();
        if(!title.isEmpty()&&(!a.isEmpty()||!b.isEmpty()))
            out.add(new Section(title,a,b));
    }
}
