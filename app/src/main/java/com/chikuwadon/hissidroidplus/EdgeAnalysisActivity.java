package com.chikuwadon.hissidroidplus;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceError;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebResourceResponse;
import org.json.JSONArray;

/** Displays site-provided analysis and triggers site analysis only on a user tap. */
public final class EdgeAnalysisActivity extends Activity {
    public static final String EXTRA_URL="analysis_url";
    private WebView web;
    private String url;
    private LinearLayout entries;
    private TextView notice,runButton;
    private boolean loaded=false,started=false;
    private int cycle=0;
    private int lastHttpError=0;

    @Override protected void attachBaseContext(Context base){super.attachBaseContext(AppSettings.themeContext(base));}
    private int dp(int value){return Math.round(value*getResources().getDisplayMetrics().density);}
    private TextView text(String value,int size,int color){
        TextView t=new TextView(this);t.setText(value);t.setTextSize(size);
        t.setTextColor(color);t.setGravity(Gravity.CENTER_VERTICAL);
        return t;
    }
    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        AppSettings.configureBars(this);
        try {
            CheckerQuery query=CheckerQuery.parseUrl(getIntent().getStringExtra(EXTRA_URL));
            if(query.site!=CheckerQuery.Site.EDGE)throw new IllegalArgumentException();
            url=query.url;
        }catch(Exception invalid){finish();return;}
        LinearLayout root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(getColor(R.color.surface));
        setContentView(root);
        if(android.os.Build.VERSION.SDK_INT>=30){
            getWindow().setDecorFitsSystemWindows(false);
            root.setOnApplyWindowInsetsListener((v,insets)->{
                android.graphics.Insets bars=insets.getInsets(
                        WindowInsets.Type.statusBars()|WindowInsets.Type.navigationBars()|WindowInsets.Type.displayCutout());
                v.setPadding(0,bars.top,0,bars.bottom);
                return WindowInsets.CONSUMED;
            });
            root.requestApplyInsets();
        }
        LinearLayout bar=new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(10),0,dp(10),0);
        root.addView(bar,new LinearLayout.LayoutParams(-1,dp(48)));
        TextView back=text("‹",32,getColor(R.color.text_primary));
        back.setGravity(Gravity.CENTER);
        bar.addView(back,new LinearLayout.LayoutParams(dp(42),-1));
        back.setOnClickListener(v->finish());
        TextView heading=text("書き込み分析",17,getColor(R.color.text_primary));
        bar.addView(heading,new LinearLayout.LayoutParams(0,-1,1));
        TextView open=text("サイトで開く",12,getColor(R.color.accent));
        open.setGravity(Gravity.CENTER);
        bar.addView(open,new LinearLayout.LayoutParams(-2,-1));
        open.setOnClickListener(v->openBrowser());

        notice=text("サイトの分析情報を確認しています…",13,getColor(R.color.text_secondary));
        notice.setPadding(dp(16),dp(10),dp(16),dp(10));
        root.addView(notice,new LinearLayout.LayoutParams(-1,-2));

        runButton=text("分析を開始",14,getColor(R.color.text_primary));
        runButton.setGravity(Gravity.CENTER);
        runButton.setMinimumHeight(dp(44));
        runButton.setVisibility(View.GONE);
        runButton.setPadding(dp(12),0,dp(12),0);
        GradientDrawable shape=new GradientDrawable();
        shape.setColor(getColor(R.color.button_surface));
        shape.setCornerRadius(dp(9));
        runButton.setBackground(new RippleDrawable(
                ColorStateList.valueOf(getColor(R.color.panel_pressed)),shape,null));
        LinearLayout.LayoutParams buttonParams=new LinearLayout.LayoutParams(-1,dp(44));
        buttonParams.setMargins(dp(16),dp(6),dp(16),dp(6));
        root.addView(runButton,buttonParams);
        runButton.setOnClickListener(v->startSiteAnalysis());

        ScrollView scroll=new ScrollView(this);
        scroll.setFillViewport(false);
        root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        entries=new LinearLayout(this);
        entries.setOrientation(LinearLayout.VERTICAL);
        entries.setPadding(dp(16),dp(5),dp(16),dp(20));
        scroll.addView(entries);

        web=new WebView(this);
        // Keep a same-origin WebView alive for the site's own JS/session,
        // but render sanitized plain-text results in native widgets.
        web.getSettings().setJavaScriptEnabled(true);
        web.getSettings().setDomStorageEnabled(true);
        web.getSettings().setLoadsImagesAutomatically(false);
        web.getSettings().setBlockNetworkImage(true);
        android.webkit.CookieManager.getInstance().setAcceptCookie(true);
        web.setWebViewClient(new WebViewClient(){
            @Override public boolean shouldOverrideUrlLoading(WebView view,WebResourceRequest request){
                Uri requested=request.getUrl();
                String host=requested.getHost();
                return !"https".equalsIgnoreCase(requested.getScheme())||
                        !("www.kyodemo.net".equalsIgnoreCase(host)||
                            "kyodemo.net".equalsIgnoreCase(host));
            }
            @Override public void onReceivedHttpError(WebView view,WebResourceRequest request,WebResourceResponse response){
                if(request.isForMainFrame()){
                    lastHttpError=response.getStatusCode();
                    notice.setText("サイトのHTTP応答: "+lastHttpError+
                            "。アクセス制限がある場合はブラウザで操作してください。");
                }
            }
            @Override public void onReceivedError(WebView view,WebResourceRequest request,WebResourceError error){
                if(request.isForMainFrame())notice.setText(
                        "サイトに接続できません。外部ブラウザで確認してください。");
            }
            @Override public void onPageFinished(WebView view,String at){
                loaded=true;
                inspectPage();
            }
        });
        root.addView(web,new LinearLayout.LayoutParams(dp(1),dp(1)));
        web.setAlpha(0.01f);
        web.loadUrl(url);
    }
    private void inspectPage(){
        if(web==null || isFinishing()||isDestroyed())return;
        web.evaluateJavascript("(function(){return document.documentElement.outerHTML})()",value->{
            if(value==null||value.equals("null")||isFinishing()||isDestroyed())return;
            try {
                String html=new JSONArray("["+value+"]").getString(0);
                EdgeAnalysis analysis=EdgeAnalysis.parse(html);
                if(analysis.hasResult()){
                    started=false;
                    runButton.setVisibility(View.GONE);
                    notice.setText("サイトの書き込み分析結果");
                    entries.removeAllViews();
                    for(EdgeAnalysis.Section section:analysis.sections)
                        renderSection(section);
                }else if(!started){
                    entries.removeAllViews();
                    if(analysis.startAvailable){
                        notice.setText("まだ分析されていません。実行するとサイト側で分析が開始されます。");
                        runButton.setVisibility(View.VISIBLE);
                        runButton.setEnabled(true);
                    }else{
                        runButton.setVisibility(View.GONE);
                        if(lastHttpError==0)notice.setText(analysis.hasStartButton?
                                "サイト側の分析ボタンが利用できません。サイトで確認してください。":
                                "分析結果または分析ボタンを確認できませんでした。サイトで確認してください。");
                    }
                }
            }catch(Exception failed){
                if(!started)notice.setText("分析情報を読み取れません。サイトで確認してください。");
            }
        });
    }
    /** Group known metric patterns into rows while preserving unknown site sections verbatim. */
    private void renderSection(EdgeAnalysis.Section section){
        LinearLayout panel=new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(13),dp(11),dp(13),dp(13));
        GradientDrawable background=new GradientDrawable();
        background.setColor(getColor(R.color.panel));
        background.setCornerRadius(dp(11));
        background.setStroke(dp(1),getColor(R.color.divider));
        panel.setBackground(background);
        LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-1,-2);
        params.setMargins(0,0,0,dp(10));
        entries.addView(panel,params);
        TextView heading=text(section.title,15,getColor(R.color.accent));
        heading.setPadding(0,0,0,dp(10));
        panel.addView(heading);
        String body=section.body==null?"":section.body.trim();
        boolean isPercent=section.title.contains("分類") ||
                body.matches("(?s).*[:：]\\s*\\d{1,3}%.*");
        java.util.regex.Pattern pattern=isPercent
                ? java.util.regex.Pattern.compile("([^/:：\\n]{1,30})\\s*[:：]\\s*(\\d{1,3})%")
                : java.util.regex.Pattern.compile("([^/:：\\n]{1,30})\\s*[:：]\\s*([SABCDEF][+-]?)(?=\\s|/|$)");
        java.util.regex.Matcher matcher=pattern.matcher(body);
        int rendered=0;
        while(matcher.find()&&rendered<30){
            String name=matcher.group(1).trim().replaceFirst("^[/\\s]+","").trim();
            String value=matcher.group(2);
            if(name.isEmpty())continue;
            LinearLayout row=new LinearLayout(this);
            row.setOrientation(LinearLayout.VERTICAL);
            row.setPadding(0,0,0,dp(9));
            panel.addView(row);
            LinearLayout line=new LinearLayout(this);
            line.setGravity(Gravity.CENTER_VERTICAL);
            row.addView(line);
            TextView label=text(name,13,getColor(R.color.text_primary));
            line.addView(label,new LinearLayout.LayoutParams(0,-2,1));
            TextView score=text(value+(isPercent?"%":""),13,getColor(R.color.accent));
            line.addView(score);
            if(isPercent){
                int count=Math.min(100,Integer.parseInt(value));
                LinearLayout track=new LinearLayout(this);
                GradientDrawable fill=new GradientDrawable();
                fill.setColor(getColor(R.color.panel_pressed));
                fill.setCornerRadius(dp(3));
                track.setBackground(fill);
                LinearLayout.LayoutParams barp=new LinearLayout.LayoutParams(-1,dp(5));
                barp.setMargins(0,dp(5),0,0);
                row.addView(track,barp);
                View bar=new View(this);
                GradientDrawable barShape=new GradientDrawable();
                barShape.setColor(getColor(R.color.accent));
                barShape.setCornerRadius(dp(3));
                bar.setBackground(barShape);
                track.setWeightSum(100);
                track.addView(bar,new LinearLayout.LayoutParams(0,-1,count));
                if(count<100)track.addView(new View(this),
                        new LinearLayout.LayoutParams(0,-1,100-count));
            }
            rendered++;
        }
        if(rendered==0){
            addNarrative(panel,body,13,getColor(R.color.text_primary));
        }else{
            // Preserve any extra text not represented by known metrics.
            String remainder=pattern.matcher(body).replaceAll(" ")
                    .replaceAll("[/\\s]+"," ").trim();
            if(!remainder.isEmpty()&&!remainder.equals("/"))
                addNarrative(panel,remainder,12,getColor(R.color.text_secondary));
        }
        if(section.notes!=null&&!section.notes.trim().isEmpty()){
            View divider=new View(this);
            divider.setBackgroundColor(getColor(R.color.divider));
            LinearLayout.LayoutParams dividerParams=new LinearLayout.LayoutParams(-1,dp(1));
            dividerParams.setMargins(0,dp(5),0,dp(9));
            panel.addView(divider,dividerParams);
            addNarrative(panel,section.notes,12,getColor(R.color.text_secondary));
        }
    }
    private void addNarrative(LinearLayout parent,String value,int size,int color){
        if(value==null||value.isEmpty())return;
        TextView body=text(value,size,color);
        body.setTextIsSelectable(true);
        body.setLineSpacing(dp(3),1f);
        body.setGravity(Gravity.START);
        parent.addView(body);
    }
    private void startSiteAnalysis(){
        if(web==null||!loaded||started)return;
        started=true;
        runButton.setEnabled(false);
        notice.setText("サイト側の分析ボタンを実行しています…");
        // Do NOT run this on page load. This is only called from a real user click.
        String script="(function(){"+
            "var a=Array.from(document.querySelectorAll('a.button,button')).find(function(e){"+
            "var t=(e.textContent||'').trim();return t==='分析'||t==='分析を開始';});"+
            "if(!a)return 'missing';"+
            "if(a.hasAttribute('disabled')||a.classList.contains('is-disabled'))return 'disabled';"+
            "a.click();return 'clicked';"+
            "})()";
        web.evaluateJavascript(script,value->{
            if(isFinishing()||isDestroyed())return;
            if(value!=null&&value.contains("clicked")){
                cycle++;
                int mark=cycle;
                for(int delay:new int[]{1500,3500,7000,13000})
                    web.postDelayed(()->{if(web!=null&&started&&mark==cycle)inspectPage();},delay);
                web.postDelayed(()->{
                    if(started&&mark==cycle&&!isFinishing()){
                        started=false;
                        notice.setText("サイトから分析結果を確認できませんでした。サイトで開いて操作してください。");
                        runButton.setEnabled(true);
                    }
                },15000);
            }else{
                started=false;
                runButton.setEnabled(true);
                notice.setText("サイト側の分析ボタンを実行できません。サイトで開いてください。");
            }
        });
    }
    private void openBrowser(){
        Intent intent=new Intent(Intent.ACTION_VIEW,Uri.parse(url+"#wt"));
        intent.addCategory(Intent.CATEGORY_BROWSABLE);
        try{startActivity(intent);}catch(Exception ignored){}
    }
    @Override protected void onDestroy(){
        cycle++;
        if(web!=null){
            web.stopLoading();
            web.setWebViewClient(null);
            if(web.getParent() instanceof android.view.ViewGroup)
                ((android.view.ViewGroup)web.getParent()).removeView(web);
            web.destroy();web=null;
        }
        super.onDestroy();
    }
}
