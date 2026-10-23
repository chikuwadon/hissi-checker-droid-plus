package com.chikuwadon.hissidroidplus;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

/** Reads site-provided analysis, preserving unknown sections and metric explanations. */
public final class EdgeAnalysis {
    public static final class Section {
        public final String title,body,notes;
        /** Explanations keyed to site-provided metric label (e.g. 文章力). */
        public final Map<String,String> explanations;
        Section(String title,String body,String notes,Map<String,String> explanations){
            this.title=title;this.body=body;this.notes=notes;
            this.explanations=Collections.unmodifiableMap(new LinkedHashMap<>(explanations));
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
            Map<String,String> reasons=new LinkedHashMap<>();
            for(Element child:box.children()){
                boolean heading=child.hasClass("title")||child.tagName().matches("h2|h3|h4");
                if(heading){
                    append(result,title,body,notes,reasons);
                    title=child.text().trim();
                    body.setLength(0);notes.setLength(0);reasons.clear();
                }else if(!title.isEmpty()&&!child.tagName().equals("br")){
                    Element safe=child.clone();
                    for(Element note:safe.select("details.rating-reason")){
                        parseExplanations(note,reasons,notes);
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
            append(result,title,body,notes,reasons);
        }
        return new EdgeAnalysis(result,available,found);
    }
    private static void parseExplanations(Element note,Map<String,String> explanations,StringBuilder extras){
        Element content=note.selectFirst(".details-c");
        if(content==null){
            addExtra(extras,note.text().trim());
            return;
        }
        String label="";
        int matches=0;
        for(Element part:content.children()){
            if(part.hasClass("bx-label")){
                label=part.text().trim();
            }else if(!label.isEmpty()&&!part.normalName().equals("br")){
                String description=part.text().trim();
                if(!description.isEmpty()){
                    explanations.put(label,description);
                    label="";matches++;
                }
            }
        }
        // New site layouts may remove bx-label: keep their description safely.
        if(matches==0)addExtra(extras,content.text().trim());
    }
    private static void addExtra(StringBuilder extras,String value){
        if(value.isEmpty())return;
        if(extras.length()>0)extras.append("\n");
        extras.append(value);
    }
    private static void append(List<Section> out,String title,StringBuilder body,
                               StringBuilder notes,Map<String,String> reasons){
        String a=body.toString().trim(),b=notes.toString().trim();
        if(!title.isEmpty()&&(!a.isEmpty()||!b.isEmpty()||!reasons.isEmpty()))
            out.add(new Section(title,a,b,reasons));
    }
}
