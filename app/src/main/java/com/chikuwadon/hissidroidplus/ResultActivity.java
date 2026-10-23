package com.chikuwadon.hissidroidplus;

import android.app.Activity;
import android.content.Context;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.content.pm.ResolveInfo;
import android.content.pm.ActivityInfo;
import android.view.Gravity;
import android.view.Window;
import android.view.WindowManager;
import android.util.DisplayMetrics;
import android.graphics.drawable.GradientDrawable;
import android.graphics.Color;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.FrameLayout;
import android.view.WindowInsets;
import java.io.IOException;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceError;
import org.json.JSONArray;
import org.json.JSONException;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.AbsListView;
import android.widget.PopupMenu;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class ResultActivity extends Activity {
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private CheckerQuery query;
    private CheckerResult result=new CheckerResult();
    private List<Map.Entry<String,String>> threadRows=new ArrayList<>();
    private Map<String,Integer> threadCounts=new java.util.LinkedHashMap<>();
    private Map<String,Integer> nameCounts=new java.util.LinkedHashMap<>();
    private int tab=0;
    private final int[] positions=new int[3];
    private final int[] offsets=new int[3];
    private TextView status;
    private ListView list;
    private RowAdapter adapter;
    private boolean loading=true;
    private boolean paging=false;
    private boolean noMorePages=false;
    private int nextPage=1;
    private boolean fallbackAttempted=false;
    private String edgeDiagnostic="未取得";
    private WebView fallbackView;

    @Override protected void attachBaseContext(Context base){super.attachBaseContext(AppSettings.themeContext(base));}
    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        if (routeStandaloneLinkToOwnTask()) return;
        setContentView(R.layout.activity_results);
        AppTitle.apply(findViewById(R.id.toolbar_title));
        AppSettings.configureBars(this);
        findViewById(R.id.stat_summary).setOnClickListener(v->{
            View chart=findViewById(R.id.hourly_chart);
            boolean expanding=chart.getVisibility()!=View.VISIBLE;
            chart.setVisibility(expanding?View.VISIBLE:View.GONE);
            AppSettings.prefs(this).edit().putBoolean("show_hourly_chart",expanding).apply();
            updateStats();
        });
        HttpTransport.setUserAgent(android.webkit.WebSettings.getDefaultUserAgent(this));
        HttpTransport.setCookieJar(new HttpTransport.CookieJar(){
            @Override public String read(String url){return android.webkit.CookieManager.getInstance().getCookie(url);}
            @Override public void save(String url,String value){android.webkit.CookieManager.getInstance().setCookie(url,value);}
        });
        applySystemInsets();
        findViewById(R.id.edge_analysis_link).setOnClickListener(v->openEdgeAnalysis());
        status=findViewById(R.id.status);
        list=findViewById(R.id.items);
        adapter=new RowAdapter();list.setAdapter(adapter);
        findViewById(R.id.tab_posts).setOnClickListener(v->selectTab(0));
        findViewById(R.id.tab_threads).setOnClickListener(v->selectTab(1));
        findViewById(R.id.tab_names).setOnClickListener(v->selectTab(2));
        findViewById(R.id.toolbar_back).setOnClickListener(v->exitResults());
        findViewById(R.id.toolbar_more).setOnClickListener(v -> {
            boolean edge=query!=null && query.site==CheckerQuery.Site.EDGE;
            String[] labels=edge
                    ? new String[]{"検索履歴","書き込み分析","検索結果を外部ブラウザで開く","IDをコピー","表示中のレスをすべてコピー","閉じる"}
                    : new String[]{"検索履歴","検索結果を外部ブラウザで開く","IDをコピー","表示中のレスをすべてコピー","閉じる"};
            ThemedMenu.showOverflow(this,v,labels,which -> {
                if(which==0)startActivity(new Intent(this,HistoryActivity.class));
                else {
                    int action=edge?which-1:which;
                    if(edge&&which==1)openEdgeAnalysis();
                    else if(action==1)openExternalBrowser(result.sourceUrl.isEmpty()?(query==null?"":query.url):result.sourceUrl);
                    else if(action==2)copySearchedId();
                    else if(action==3)copyAllVisible();
                    else exitResults();
                }
            });
        });
        selectTab(0);
        list.setOnItemClickListener((parent,v,index,id)->showEntry(index));
        list.setOnTouchListener(new View.OnTouchListener(){
            private float downX,downY;
            @Override public boolean onTouch(View view,android.view.MotionEvent event){
                if(event.getActionMasked()==android.view.MotionEvent.ACTION_DOWN){
                    downX=event.getX();downY=event.getY();return false;
                }
                if(event.getActionMasked()==android.view.MotionEvent.ACTION_UP){
                    float dx=event.getX()-downX,dy=event.getY()-downY;
                    if(Math.abs(dx)>dp(65) && Math.abs(dx)>Math.abs(dy)*1.35f){
                        int next=tab+(dx<0?1:-1);
                        if(next>=0 && next<3)selectTab(next);
                        return true;
                    }
                }
                return false;
            }
        });
        list.setOnScrollListener(new AbsListView.OnScrollListener(){
            @Override public void onScrollStateChanged(AbsListView view,int state){}
            @Override public void onScroll(AbsListView view,int first,int visible,int total){
                if(tab==0 && total>0 && first+visible>=total-2)loadNextPage();
            }
        });
        try {query=extract(getIntent());}
        catch(Exception e){showError(e.getMessage());return;}
        status.setText(query.site==CheckerQuery.Site.EDGE?"エッヂ / 取得中…":"5ch / 取得中…");
        worker.execute(()->{
            try{
                CheckerResult received=query.site==CheckerQuery.Site.EDGE?new EdgeAdapter().load(query):new HissiAdapter().load(query);
                runOnUiThread(()->{
                    if(isFinishing()||isDestroyed())return;
                    if(query.site==CheckerQuery.Site.EDGE && received.posts.isEmpty()){
                        edgeDiagnostic=received.diagnostic;
                        loadEdgeWithWebView("通常通信では抽出できません: "+received.diagnostic);return;
                    }
                    result=received;rebuildIndexes();
                    loading=false;adapter.notifyDataSetChanged();updateHeader();
                    if(!result.posts.isEmpty())SearchHistory.record(this,query,result);
                    if(result.posts.isEmpty()){
                        showEmpty();
                    }
                });
            }catch(Exception e){runOnUiThread(()->{
                if(isFinishing()||isDestroyed())return;
                if(query.site==CheckerQuery.Site.EDGE)loadEdgeWithWebView(
                    (e.getMessage()==null?"通信失敗":e.getMessage())+" / "+HttpTransport.lastDiagnostic());
                else showError(e.getMessage());
            });}
        });
    }
    private void loadNextPage() {
        if(query==null || query.site!=CheckerQuery.Site.HISSI || loading || paging || noMorePages ||
                result.sourceUrl.isEmpty() || result.posts.isEmpty() || nextPage>20 ||
                result.reportedPostCount<0 || result.posts.size()>=result.reportedPostCount) return;
        paging=true;
        final int page=nextPage;
        worker.execute(()->{
            try {
                CheckerResult addition=new HissiAdapter().nextPage(result.sourceUrl,page);
                runOnUiThread(()->{
                    if(isFinishing()||isDestroyed())return;
                    if(addition.posts.isEmpty())noMorePages=true;
                    else{
                        int before=result.posts.size();
                        java.util.HashSet<String> seen=new java.util.HashSet<>();
                        for(CheckerResult.Post post:result.posts)seen.add(post.link+post.meta);
                        for(CheckerResult.Post post:addition.posts)if(seen.add(post.link+post.meta))result.posts.add(post);
                        if(result.posts.size()==before)noMorePages=true;
                    }
                    nextPage=page+1;
                    paging=false;
                    rebuildIndexes();adapter.notifyDataSetChanged();updateHeader();
                });
            } catch(Exception ex){
                runOnUiThread(()->{
                    paging=false;noMorePages=true;
                    if(!isFinishing()&&!isDestroyed())Toast.makeText(this,"追加レスを取得できませんでした",Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    /**
     * Connected mode stays in the caller's task. Standalone mode is explicitly
     * re-launched in our own task only once, without a translucent trampoline.
     */
    private boolean routeStandaloneLinkToOwnTask() {
        Intent incoming=getIntent();
        if(incoming==null)return false;
        String action=incoming.getAction();
        if(!Intent.ACTION_VIEW.equals(action)&&!Intent.ACTION_SEND.equals(action))return false;
        if(!AppSettings.standalone(this) || incoming.getBooleanExtra("internal_standalone_routed",false))return false;
        Intent standalone=new Intent(this,ResultActivity.class);
        standalone.setAction(action);
        if(incoming.getType()!=null)standalone.setDataAndType(incoming.getData(),incoming.getType());
        else standalone.setData(incoming.getData());
        if(incoming.getExtras()!=null)standalone.putExtras(incoming.getExtras());
        standalone.putExtra("internal_standalone_routed",true);
        standalone.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_MULTIPLE_TASK);
        startActivity(standalone);
        finish();
        return true;
    }
    private boolean launchedExternally(){
        String action=getIntent().getAction();
        return Intent.ACTION_SEND.equals(action) || Intent.ACTION_VIEW.equals(action);
    }
    private void exitResults(){
        if(launchedExternally() && isTaskRoot() && android.os.Build.VERSION.SDK_INT>=21){finishAndRemoveTask();}
        else finish();
    }
    private CheckerQuery extract(Intent incoming){
        String url=incoming.getDataString();
        if(url==null&&Intent.ACTION_SEND.equals(incoming.getAction())){
            CharSequence shared=incoming.getCharSequenceExtra(Intent.EXTRA_TEXT);
            if(shared!=null)url=shared.toString();
        }
        if(url!=null)return CheckerQuery.fromSharedText(url);
        return CheckerQuery.hissi(incoming.getStringExtra("board"),incoming.getStringExtra("date"),incoming.getStringExtra("id"));
    }
    private void updateHeader(){
        String source=query.site==CheckerQuery.Site.EDGE?"エッヂ":"5ch";
        status.setText(source+" / "+(query.id.isEmpty()?query.board:query.id)+"  レス:"+result.posts.size()
                +(result.reportedPostCount<0?"":" / "+result.reportedPostCount));
        ((TextView)findViewById(R.id.tab_posts)).setText("レス ("+result.posts.size()+")");
        ((TextView)findViewById(R.id.tab_threads)).setText("スレ ("+threadRows.size()+")");
        ((TextView)findViewById(R.id.tab_names)).setText("名前 ("+result.names.size()+")");
        updateStats();
    }
    private void openEdgeAnalysis(){
        if(query==null||query.site!=CheckerQuery.Site.EDGE)return;
        String url=result.sourceUrl.isEmpty()?query.url:result.sourceUrl;
        if(url==null||url.isEmpty())return;
        startActivity(new Intent(this,EdgeAnalysisActivity.class)
                .putExtra(EdgeAnalysisActivity.EXTRA_URL,url));
    }
    private void updateStats(){
        View block=findViewById(R.id.stat_block);
        TextView summary=findViewById(R.id.stat_summary);
        HourlyChartView chart=findViewById(R.id.hourly_chart);
        if(result.posts.isEmpty()){block.setVisibility(View.GONE);return;}
        block.setVisibility(View.VISIBLE);
        findViewById(R.id.edge_analysis_link).setVisibility(
                query!=null&&query.site==CheckerQuery.Site.EDGE?View.VISIBLE:View.GONE);
        int dated=chart.setPosts(result.posts);
        if(result.hourlyCounts!=null){chart.setCounts(result.hourlyCounts);dated=1;}
        StringBuilder label=new StringBuilder();
        if(!result.rank.isEmpty())label.append(result.rank).append("  ·  ");
        label.append("レス ").append(result.reportedPostCount>=0?result.reportedPostCount:result.posts.size());
        label.append("  /  スレ ").append(result.reportedThreadCount>=0?result.reportedThreadCount:threadRows.size());
        if(result.referencedCount>=0)label.append("  /  被参照 ").append(result.referencedCount);
        if(dated>0){
            boolean show=AppSettings.prefs(this).getBoolean("show_hourly_chart",true);
            chart.setVisibility(show?View.VISIBLE:View.GONE);
            label.append(show?"  ▴":"  ▾");
        }else chart.setVisibility(View.GONE);
        summary.setText(label.toString());
    }
    private void selectTab(int index){
        positions[tab]=list.getFirstVisiblePosition();
        View first=list.getChildAt(0);
        offsets[tab]=first==null?0:first.getTop();
        tab=index;
        int[] tabs={R.id.tab_posts,R.id.tab_threads,R.id.tab_names};
        for (int j=0;j<tabs.length;j++) {
            TextView button=findViewById(tabs[j]);
            // Underline selection only; retain a single flat background on all tabs.
            button.setSelected(j==tab);
            button.setTextColor(getColor(j==tab?R.color.accent:R.color.text_secondary));
            button.setContentDescription(button.getText() + (j==tab?"、選択中":""));
        }
        adapter.notifyDataSetChanged();
        list.setSelectionFromTop(positions[tab],offsets[tab]);
    }
    private void showEntry(int i){
        if(loading || query==null) return;
        if(tab==0){
            if(i>=result.posts.size())return;
            CheckerResult.Post post=result.posts.get(i);
            showMenu(post.title,new String[]{"スレを開く","スレタイと全体をコピー","全体をコピー",
                "スレタイと全体を追加コピー","全体を追加コピー","IDをコピー","リンクをコピー"},which->{
                switch(which){
                    case 0:openThread(post.link);break;
                    case 1:copy(PostFormatter.withThread(post),false);break;
                    case 2:copy(PostFormatter.full(post),false);break;
                    case 3:copy(PostFormatter.withThread(post),true);break;
                    case 4:copy(PostFormatter.full(post),true);break;
                    case 5:{String id=PostFormatter.id(post);if(id.isEmpty())Toast.makeText(this,"IDが見つかりません",Toast.LENGTH_SHORT).show();else copy(id,false);break;}
                    case 6:copy(post.link,false);break;
                }
            });
        } else if(tab==1){
            if(i>=threadRows.size())return;
            Map.Entry<String,String> entry=threadRows.get(i);
            showMenu(entry.getValue(),new String[]{"スレを開く","スレタイをコピー","リンクをコピー","スレタイとリンクをコピー","スレタイとリンクを追加コピー"},which->{
                if(which==0)openThread(entry.getKey());
                else if(which==1)copy(entry.getValue(),false);
                else if(which==2)copy(entry.getKey(),false);
                else if(which==3)copy(entry.getValue()+"\n"+entry.getKey(),false);
                else if(which==4)copy(entry.getValue()+"\n"+entry.getKey(),true);
            });
        }else if(i<result.names.size()){
            String name=result.names.get(i);
            showMenu(name,new String[]{"名前をコピー","名前を追加コピー"},which->copy(name,which==1));
        }
    }
    private interface MenuSelected{void invoke(int which);}
    private GradientDrawable panelBackground(){
        GradientDrawable bg=new GradientDrawable();
        bg.setColor(getColor(R.color.panel));bg.setCornerRadius(dp(17));
        bg.setStroke(dp(1),getColor(R.color.divider));return bg;
    }
    private TextView dialogText(String text,int size,int color){
        TextView view=new TextView(this);view.setText(text);view.setTextSize(size);
        view.setTextColor(color);view.setFontFeatureSettings("kern");return view;
    }
    private LinearLayout dialogPanel(String title){
        LinearLayout outer=new LinearLayout(this);outer.setOrientation(LinearLayout.VERTICAL);
        outer.setPadding(dp(18),dp(17),dp(18),dp(12));outer.setBackground(panelBackground());
        TextView heading=dialogText(title,17,getColor(R.color.text_primary));
        heading.setMaxLines(2);heading.setEllipsize(android.text.TextUtils.TruncateAt.END);
        heading.setPadding(0,0,0,dp(11));outer.addView(heading);
        return outer;
    }
    private TextView buttonText(String text){
        TextView button=dialogText(text,14,getColor(R.color.accent));
        button.setGravity(Gravity.CENTER);button.setMinWidth(dp(76));button.setMinHeight(dp(46));
        button.setBackground(new android.graphics.drawable.RippleDrawable(
                android.content.res.ColorStateList.valueOf(0x3345BDB6),
                new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT),null));
        return button;
    }
    private void finishPanel(LinearLayout panel,AlertDialog dialog,String right,Runnable rightAction){
        LinearLayout buttons=new LinearLayout(this);buttons.setGravity(Gravity.END|Gravity.CENTER_VERTICAL);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        TextView cancel=buttonText(right);cancel.setOnClickListener(v->{dialog.dismiss();if(rightAction!=null)rightAction.run();});
        buttons.addView(cancel);panel.addView(buttons);
        dialog.setView(panel);showCentered(dialog);
    }
    private void showMenu(String title,String[] items,MenuSelected callback){
        LinearLayout panel=dialogPanel(title==null||title.isEmpty()?"操作":title);
        AlertDialog dialog=new AlertDialog.Builder(this).create();
        for(int i=0;i<items.length;i++){
            final int which=i;
            TextView row=dialogText(items[i],15,getColor(R.color.text_primary));
            row.setGravity(Gravity.CENTER_VERTICAL);row.setMinHeight(dp(49));
            row.setPadding(dp(9),0,dp(9),0);
            row.setBackground(new android.graphics.drawable.RippleDrawable(
                    android.content.res.ColorStateList.valueOf(0x3345BDB6),
                    new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT),null));
            panel.addView(row,new LinearLayout.LayoutParams(-1,-2));
            row.setOnClickListener(v->{dialog.dismiss();callback.invoke(which);});
        }
        finishPanel(panel,dialog,"キャンセル",null);
    }
    private void showMessage(String title,String body,String left,Runnable onLeft,String right,Runnable onRight){
        LinearLayout panel=dialogPanel(title);
        TextView message=dialogText(body,14,getColor(R.color.text_secondary));
        message.setTextIsSelectable(true);
        message.setPadding(0,0,0,dp(15));panel.addView(message);
        AlertDialog dialog=new AlertDialog.Builder(this).create();
        LinearLayout buttons=new LinearLayout(this);buttons.setGravity(Gravity.END|Gravity.CENTER_VERTICAL);
        if(left!=null){TextView leftButton=buttonText(left);buttons.addView(leftButton);
            leftButton.setOnClickListener(v->{dialog.dismiss();if(onLeft!=null)onLeft.run();});}
        TextView rightButton=buttonText(right);rightButton.setTextColor(getColor(R.color.text_primary));
        buttons.addView(rightButton);
        rightButton.setOnClickListener(v->{dialog.dismiss();if(onRight!=null)onRight.run();});
        panel.addView(buttons);dialog.setView(panel);showCentered(dialog);
    }
    private void showCentered(AlertDialog dialog){
        if(isFinishing()||isDestroyed())return;
        dialog.show();
        Window window=dialog.getWindow();
        if(window!=null){
            window.setBackgroundDrawableResource(android.R.color.transparent);
            WindowManager.LayoutParams params=window.getAttributes();
            params.gravity=Gravity.CENTER;params.y=0;window.setAttributes(params);
            int w=getResources().getDisplayMetrics().widthPixels;
            window.setLayout(Math.min(w-dp(28),dp(480)),WindowManager.LayoutParams.WRAP_CONTENT);
        }
    }
    private int dp(int value){return Math.round(value*getResources().getDisplayMetrics().density);}
    private void rebuildIndexes(){
        PostIndex.collectNames(result);
        threadRows=new ArrayList<>(PostIndex.uniqueThreads(result).entrySet());
        threadCounts=PostIndex.threadPostCounts(result);
        nameCounts=PostIndex.namePostCounts(result);
    }
    private void applySystemInsets(){
        View root=findViewById(android.R.id.content);
        if(android.os.Build.VERSION.SDK_INT>=30){
            getWindow().setDecorFitsSystemWindows(false);
            final int startTop=root.getPaddingTop(),startBottom=root.getPaddingBottom();
            root.setOnApplyWindowInsetsListener((view,insets)->{
                android.graphics.Insets bars=insets.getInsets(WindowInsets.Type.statusBars()|WindowInsets.Type.navigationBars()|WindowInsets.Type.displayCutout());
                view.setPadding(view.getPaddingLeft(),startTop+bars.top,view.getPaddingRight(),startBottom+bars.bottom);
                return WindowInsets.CONSUMED;
            });root.requestApplyInsets();
        }
    }
    private void showEmpty(){
        String page=result.sourceUrl.isEmpty()?(query==null?"":query.url):result.sourceUrl;
        boolean hissi=query!=null && query.site==CheckerQuery.Site.HISSI;
        String message="ページが存在しないか、HTML形式が想定と違う可能性があります。";
        if(hissi && result.reportedPostCount>0)
            message="5chのサイトでは"+result.reportedPostCount+"件の投稿が確認できますが、レス本文を解析できませんでした。"+
                    "\n診断: "+result.diagnostic;
        showMessage("レスを確認できませんでした",message,
            page.isEmpty()?null:"外部ブラウザで開く",()->openExternalBrowser(page),"閉じる",this::exitResults);
    }
    private void copy(String value,boolean append){
        ClipboardManager clipboard=(ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
        String text=value==null?"":value;
        if(append&&clipboard.hasPrimaryClip()&&clipboard.getPrimaryClip()!=null&&clipboard.getPrimaryClip().getItemCount()>0){
            CharSequence old=clipboard.getPrimaryClip().getItemAt(0).coerceToText(this);
            text=PostFormatter.append(old==null?"":old.toString(),value);
        }
        clipboard.setPrimaryClip(ClipData.newPlainText("必死チェッカーdroid +",text));
        // Android 13+ already displays a native clipboard confirmation overlay.
        if(android.os.Build.VERSION.SDK_INT<33)
            Toast.makeText(this,"コピーしました",Toast.LENGTH_SHORT).show();
    }
    /** WebView fallback for anti-bot/javascript pages. Never silently treat an access-denied page as no posts. */
    private void loadEdgeWithWebView(String firstError){
        if(fallbackAttempted){showError(firstError==null?"エッヂのページ構造を解析できませんでした":firstError);return;}
        fallbackAttempted=true;
        edgeDiagnostic=firstError==null?"通信成功／ネイティブ解析0件":firstError;
        status.setText("エッヂ / ブラウザ方式で再取得中…");
        WebView web=new WebView(this);fallbackView=web;
        web.setAlpha(0.01f);
        web.setClickable(false);
        FrameLayout content=findViewById(android.R.id.content);
        content.addView(web,new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,FrameLayout.LayoutParams.MATCH_PARENT));
        web.getSettings().setJavaScriptEnabled(true);
        web.getSettings().setDomStorageEnabled(true);
        web.getSettings().setLoadsImagesAutomatically(false);
        web.getSettings().setBlockNetworkImage(true);
        final boolean[] completed={false};
        final String[] detail={firstError==null?"解析できるレスがありませんでした":firstError};
        android.webkit.CookieManager.getInstance().setAcceptCookie(true);
        web.setWebViewClient(new WebViewClient(){
            @Override public boolean shouldOverrideUrlLoading(WebView view,WebResourceRequest request){
                Uri url=request.getUrl();String host=url.getHost();
                if(host==null || !(host.equals("www.kyodemo.net")||host.equals("kyodemo.net"))){
                    detail[0]="外部サイトへリダイレクトされました";return true;
                }
                return false;
            }
            @Override public void onReceivedError(WebView view,WebResourceRequest request,WebResourceError error){
                if(request.isForMainFrame()){
                    detail[0]="WebView通信エラー: "+error.getErrorCode();
                    edgeDiagnostic=detail[0];
                }
            }
            @Override public void onReceivedHttpError(WebView view, WebResourceRequest request,
                    android.webkit.WebResourceResponse response) {
                if(request.isForMainFrame()){
                    detail[0]="エッヂ側のHTTP応答: "+response.getStatusCode();
                    edgeDiagnostic=detail[0];
                }
            }
            @Override public void onPageFinished(WebView view,String url){
                // Some server-rendered pages attach posts after DOMContentLoaded.
                // Probe multiple times rather than assuming everything is ready at 1.3s.
                final int[] delays={900,2200,4200,7100};
                for (int delay:delays) view.postDelayed(()->{
                    if(completed[0] || isFinishing() || isDestroyed()) return;
                    view.evaluateJavascript("(function(){return document.documentElement.outerHTML})()",json->{
                        if(completed[0] || isFinishing() || isDestroyed()) return;
                        try {
                            String html=new JSONArray("["+json+"]").getString(0);
                            CheckerResult found=EdgeAdapter.parse(html,query);
                            if(found.posts.isEmpty()){
                                String cause=EdgeAdapter.explainEmptyPage(html);
                                // Keep a real HTTP status if we have one; it is more
                                // informative than a generic structural failure.
                                if(!detail[0].matches(".*HTTP.*\\d{3}.*") ||
                                        !cause.contains("HTML構造未対応")) detail[0]=cause;
                                edgeDiagnostic=detail[0]+" / HTML="+html.length()+"文字";
                            } else {
                                completed[0]=true;
                                found.sourceUrl=query.url;
                                result=found;
                                rebuildIndexes();loading=false;adapter.notifyDataSetChanged();updateHeader();
                                SearchHistory.record(ResultActivity.this,query,result);
                                cleanupWebView(view);
                            }
                        } catch(Exception ex) {
                            detail[0]="WebViewのHTML解析に失敗しました";
                        }
                    });
                }, delay);
            }
        });
        web.loadUrl(query.url);
        web.postDelayed(()->{
            if(!completed[0]&&!isFinishing()&&!isDestroyed()){
                completed[0]=true;showError(detail[0]);
                cleanupWebView(web);
            }
        },11000);
    }

    private void cleanupWebView(WebView web){
        if(web==null) return;
        web.stopLoading();
        web.setWebViewClient(null);
        if(web.getParent() instanceof android.view.ViewGroup)
            ((android.view.ViewGroup)web.getParent()).removeView(web);
        web.destroy();
        if(fallbackView==web)fallbackView=null;
    }

    private void showError(String message){
        loading=false;
        status.setText("取得に失敗しました");
        String text=message==null?"通信・解析エラーが発生しました":message;
        if(query!=null && query.site==CheckerQuery.Site.EDGE)
            edgeDiagnostic=text;
        String url=query==null?"":query.url;
        showMessage("レスを取得できませんでした",text,
            url.isEmpty()?null:"外部ブラウザで開く",()->openExternalBrowser(url),"閉じる",this::exitResults);
    }
    private void openThread(String url){
        ThreadOpener.open(this,url,query!=null&&query.site==CheckerQuery.Site.EDGE);
    }
    /** Browser-only actions must never bounce back through our deep links. */
    private void openExternalBrowser(String url){
        if(url==null||url.isEmpty()){
            Toast.makeText(this,"リンクがありません",Toast.LENGTH_SHORT).show();
            return;
        }
        BrowserLauncher.open(this,url);
    }
    @Override public boolean onCreateOptionsMenu(Menu menu){
        menu.add(0,1,0,"表示中のレスをすべてコピー");
        menu.add(0,2,1,"検索結果URLを開く");
        return true;
    }
    @Override public boolean onOptionsItemSelected(MenuItem item){
        if(item.getItemId()==1){copyAllVisible();return true;}
        if(item.getItemId()==2){openExternalBrowser(result.sourceUrl.isEmpty()?(query==null?"":query.url):result.sourceUrl);return true;}
        return super.onOptionsItemSelected(item);
    }
    private void copySearchedId(){
        String id=query==null?"":query.id;
        if(id!=null&&!id.isEmpty())id="ID:"+id.replaceFirst("(?i)^ID\\s*[:：]\\s*","");
        if((id==null||id.isEmpty())&&!result.posts.isEmpty())id=PostFormatter.id(result.posts.get(0));
        if(id==null||id.isEmpty()){Toast.makeText(this,"IDが見つかりません",Toast.LENGTH_SHORT).show();return;}
        copy(id,false);
    }
    private void copyAllVisible(){
        StringBuilder b=new StringBuilder();
        java.util.HashSet<String> titlesAdded=new java.util.HashSet<>();
        for(CheckerResult.Post p:result.posts){
            if(b.length()>0)b.append("\n\n");
            String key=PostIndex.threadKey(p.link);
            boolean first=titlesAdded.add(key);
            b.append(first?PostFormatter.withThread(p):PostFormatter.full(p));
        }
        copy(b.toString(),false);
    }
    @Override protected void onDestroy(){worker.shutdownNow();if(fallbackView!=null){fallbackView.stopLoading();if(fallbackView.getParent() instanceof android.view.ViewGroup)((android.view.ViewGroup)fallbackView.getParent()).removeView(fallbackView);fallbackView.destroy();fallbackView=null;}super.onDestroy();}
    /** Muted reference badge appears only in the UI, never in author aggregation or copied text. */
    private CharSequence displayHeader(CheckerResult.Post post){
        String header=PostFormatter.header(post);
        if(post.referenceCount<=0)return header;
        java.util.regex.Matcher number=java.util.regex.Pattern.compile("^\\d+").matcher(header);
        if(!number.find())return header;
        int at=number.end();
        String mark="("+post.referenceCount+")";
        android.text.SpannableString span=new android.text.SpannableString(
                header.substring(0,at)+mark+header.substring(at));
        int accent=getColor(R.color.accent),secondary=getColor(R.color.text_secondary);
        int mixed=android.graphics.Color.rgb(
                (android.graphics.Color.red(accent)*55+android.graphics.Color.red(secondary)*45)/100,
                (android.graphics.Color.green(accent)*55+android.graphics.Color.green(secondary)*45)/100,
                (android.graphics.Color.blue(accent)*55+android.graphics.Color.blue(secondary)*45)/100);
        span.setSpan(new android.text.style.ForegroundColorSpan(mixed),
                at,at+mark.length(),android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        span.setSpan(new android.text.style.RelativeSizeSpan(0.88f),
                at,at+mark.length(),android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return span;
    }

    /** Count suffix uses the ordinary text color; it is not part of copied data. */
    private CharSequence labelWithCount(String label,int count){
        if(count<=0)return label;
        String suffix=" ("+count+")";
        android.text.SpannableString span=new android.text.SpannableString(label+suffix);
        span.setSpan(new android.text.style.ForegroundColorSpan(getColor(R.color.text_primary)),
                label.length(),span.length(),android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return span;
    }
    private final class RowAdapter extends BaseAdapter {
        @Override public int getCount(){return tab==0?result.posts.size():tab==1?threadRows.size():result.names.size();}
        @Override public Object getItem(int pos){return pos;}
        @Override public long getItemId(int pos){return pos;}
        @Override public View getView(int index,View convert,ViewGroup parent){
            View view=convert==null?LayoutInflater.from(ResultActivity.this).inflate(R.layout.item_row,parent,false):convert;
            TextView title=view.findViewById(R.id.item_title),subtitle=view.findViewById(R.id.item_subtitle),body=view.findViewById(R.id.item_body);
            if(tab==0){
                CheckerResult.Post p=result.posts.get(index);
                boolean sameThread=index>0 && PostIndex.threadKey(p.link).equals(PostIndex.threadKey(result.posts.get(index-1).link));
                title.setVisibility(sameThread?View.GONE:View.VISIBLE);
                title.setText(p.title);subtitle.setVisibility(View.VISIBLE);subtitle.setText(displayHeader(p));body.setText(p.body);
                body.setVisibility(View.VISIBLE);
            } else if(tab==1){
                title.setVisibility(View.VISIBLE);
                Map.Entry<String,String> e=threadRows.get(index);
                int count=threadCounts.getOrDefault(PostIndex.threadKey(e.getKey()),0);
                title.setText(labelWithCount(e.getValue(),count));
                subtitle.setText("");subtitle.setVisibility(View.GONE);body.setVisibility(View.GONE);
            }else{
                title.setVisibility(View.VISIBLE);
                String name=result.names.get(index);
                int count=nameCounts.getOrDefault(name,0);
                title.setText(labelWithCount(name,count));
                subtitle.setVisibility(View.GONE);body.setVisibility(View.GONE);
            }
            return view;
        }
    }
}
