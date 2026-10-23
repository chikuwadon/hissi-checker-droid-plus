package com.chikuwadon.hissidroidplus;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.widget.BaseAdapter;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Native results list for Edge ID/date searches; no intermediate WebView. */
public final class EdgeSearchActivity extends Activity {
    public static final String BOARD="board",DATE="date",USER_ID="user_id";
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final List<EdgeSearchResults.Item> items=new ArrayList<>();
    private String board,date,id,searchUrl;
    private TextView status;
    private ListView list;
    private final ResultAdapter adapter=new ResultAdapter();
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    @Override protected void attachBaseContext(Context base){
        super.attachBaseContext(AppSettings.themeContext(base));
    }
    private TextView text(String value,int size,int color){
        TextView text=new TextView(this);
        text.setText(value);text.setTextSize(size);text.setTextColor(color);
        text.setGravity(Gravity.CENTER_VERTICAL);return text;
    }
    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        AppSettings.configureBars(this);
        try{
            board=getIntent().getStringExtra(BOARD);
            date=getIntent().getStringExtra(DATE);
            id=getIntent().getStringExtra(USER_ID);
            searchUrl=EdgeSearchLink.searchUrl(board,date,id);
        }catch(Exception invalid){finish();return;}
        HttpTransport.setUserAgent(android.webkit.WebSettings.getDefaultUserAgent(this));
        HttpTransport.setCookieJar(new HttpTransport.CookieJar(){
            @Override public String read(String url){
                return android.webkit.CookieManager.getInstance().getCookie(url);
            }
            @Override public void save(String url,String value){
                android.webkit.CookieManager.getInstance().setCookie(url,value);
            }
        });
        LinearLayout root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(getColor(R.color.surface));
        setContentView(root);
        if(android.os.Build.VERSION.SDK_INT>=30){
            getWindow().setDecorFitsSystemWindows(false);
            root.setOnApplyWindowInsetsListener((v,insets)->{
                android.graphics.Insets bars=insets.getInsets(
                        WindowInsets.Type.statusBars()|WindowInsets.Type.navigationBars()
                        |WindowInsets.Type.displayCutout());
                v.setPadding(0,bars.top,0,bars.bottom);
                return WindowInsets.CONSUMED;
            });root.requestApplyInsets();
        }
        LinearLayout toolbar=new LinearLayout(this);
        toolbar.setGravity(Gravity.CENTER_VERTICAL);
        toolbar.setPadding(dp(9),0,dp(12),0);
        root.addView(toolbar,new LinearLayout.LayoutParams(-1,dp(48)));
        TextView back=text("‹",30,getColor(R.color.text_primary));
        back.setGravity(Gravity.CENTER);
        toolbar.addView(back,new LinearLayout.LayoutParams(dp(40),-1));
        back.setOnClickListener(v->finish());
        TextView title=text("エッヂ / 検索結果",17,getColor(R.color.text_primary));
        toolbar.addView(title,new LinearLayout.LayoutParams(0,-2,1));
        TextView browser=text("サイトで開く",12,getColor(R.color.accent));
        toolbar.addView(browser);
        browser.setOnClickListener(v->BrowserLauncher.open(this,searchUrl));
        status=text("検索しています…",12,getColor(R.color.text_secondary));
        status.setPadding(dp(15),dp(8),dp(15),dp(8));
        root.addView(status);
        list=new ListView(this);
        list.setDivider(new ColorDrawable(getColor(R.color.divider)));
        list.setDividerHeight(dp(1));
        list.setAdapter(adapter);
        list.setOnItemClickListener((p,v,index,rowId)->{
            if(index<0||index>=items.size())return;
            showChecker(items.get(index).checkerUrl);
        });
        root.addView(list,new LinearLayout.LayoutParams(-1,0,1));
        fetch();
    }
    private void fetch(){
        worker.execute(()->{
            try{
                String html=HttpTransport.get(searchUrl,StandardCharsets.UTF_8);
                List<EdgeSearchResults.Item> result=EdgeSearchResults.parse(html,searchUrl,board,date,id);
                runOnUiThread(()->{
                    if(isFinishing()||isDestroyed())return;
                    items.clear();items.addAll(result);
                    adapter.notifyDataSetChanged();
                    if(items.isEmpty()){
                        status.setText("一致するスレッドを取得できませんでした。サイトで確認してください。");
                    }else if(items.size()==1){
                        status.setText("1件の一致スレッドを見つけました。");
                        showChecker(items.get(0).checkerUrl);
                        finish();
                    }else{
                        status.setText(getString(R.string.edge_search_found,items.size()));
                    }
                });
            }catch(Exception error){
                runOnUiThread(()->{
                    if(!isFinishing()&&!isDestroyed())
                        status.setText(getString(R.string.edge_search_error,
                            error.getMessage()==null?"":error.getMessage()));
                });
            }
        });
    }
    private void showChecker(String link){
        startActivity(new Intent(this,ResultActivity.class).setData(Uri.parse(link)));
    }
    @Override protected void onDestroy(){
        worker.shutdownNow();super.onDestroy();
    }
    private final class ResultAdapter extends BaseAdapter {
        @Override public int getCount(){return items.size();}
        @Override public Object getItem(int n){return items.get(n);}
        @Override public long getItemId(int n){return n;}
        @Override public View getView(int position,View reuse,ViewGroup parent){
            EdgeSearchResults.Item item=items.get(position);
            LinearLayout box=new LinearLayout(EdgeSearchActivity.this);
            box.setOrientation(LinearLayout.VERTICAL);
            box.setPadding(dp(16),dp(12),dp(16),dp(12));
            TextView title=text(item.title,14,getColor(R.color.text_primary));
            title.setMaxLines(3);title.setEllipsize(android.text.TextUtils.TruncateAt.END);
            box.addView(title);
            TextView extra=text("必死チェッカーを開く  ›",12,getColor(R.color.accent));
            extra.setPadding(0,dp(5),0,0);
            box.addView(extra);
            return box;
        }
    }
}
