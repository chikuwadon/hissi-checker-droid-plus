package com.chikuwadon.hissidroidplus;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.WindowInsets;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceError;
import android.webkit.WebResourceResponse;

/**
 * Search the real site's ID/date index without inventing a thread key.
 * Tapping a thread result supplies the real key needed for the checker.
 */
public final class EdgeSearchActivity extends Activity {
    public static final String BOARD="board",DATE="date",USER_ID="user_id";
    private WebView web;
    private String board,date,id,url;
    @Override protected void attachBaseContext(Context base){super.attachBaseContext(AppSettings.themeContext(base));}
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        AppSettings.configureBars(this);
        try{
            board=getIntent().getStringExtra(BOARD);
            date=getIntent().getStringExtra(DATE);
            id=getIntent().getStringExtra(USER_ID);
            url=EdgeSearchLink.searchUrl(board,date,id);
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
            });root.requestApplyInsets();
        }
        LinearLayout bar=new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(8),0,dp(10),0);
        root.addView(bar,new LinearLayout.LayoutParams(-1,dp(48)));
        TextView back=new TextView(this);
        back.setText("‹");back.setTextSize(30);
        back.setTextColor(getColor(R.color.text_primary));
        back.setGravity(Gravity.CENTER);
        bar.addView(back,new LinearLayout.LayoutParams(dp(42),-1));
        back.setOnClickListener(v->finish());
        TextView title=new TextView(this);
        title.setText("エッヂ / ID検索");title.setTextSize(16);
        title.setTextColor(getColor(R.color.text_primary));
        bar.addView(title,new LinearLayout.LayoutParams(0,-2,1));
        TextView open=new TextView(this);
        open.setText("サイトで開く");open.setTextSize(12);
        open.setTextColor(getColor(R.color.accent));
        open.setGravity(Gravity.CENTER);
        bar.addView(open,new LinearLayout.LayoutParams(-2,dp(44)));
        open.setOnClickListener(v->{
            try {startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(url)).addCategory(Intent.CATEGORY_BROWSABLE));}
            catch(Exception ignored){}
        });
        TextView hint=new TextView(this);
        hint.setText("一致する投稿のスレッドを選ぶと必死チェッカーを開きます。");
        hint.setTextSize(12);hint.setTextColor(getColor(R.color.text_secondary));
        hint.setPadding(dp(12),dp(5),dp(12),dp(6));
        root.addView(hint,new LinearLayout.LayoutParams(-1,-2));
        web=new WebView(this);
        web.getSettings().setJavaScriptEnabled(true);
        web.getSettings().setDomStorageEnabled(true);
        web.getSettings().setLoadsImagesAutomatically(false);
        web.getSettings().setBlockNetworkImage(true);
        web.setBackgroundColor(getColor(R.color.surface));
        web.setWebViewClient(new WebViewClient(){
            @Override public boolean shouldOverrideUrlLoading(WebView view,WebResourceRequest request){
                if(!request.isForMainFrame())return false;
                Uri uri=request.getUrl();
                String tapped=uri.toString();
                // Prefer the real checker URL when the site provides it.
                try{
                    CheckerQuery parsed=CheckerQuery.parseUrl(tapped);
                    if(parsed.site==CheckerQuery.Site.EDGE){
                        startActivity(new Intent(EdgeSearchActivity.this,ResultActivity.class)
                                .setData(Uri.parse(parsed.url)));
                        return true;
                    }
                }catch(Exception ignored){}
                String exact=EdgeSearchLink.checkerFromThread(board,date,id,tapped);
                if(!exact.isEmpty()){
                    startActivity(new Intent(EdgeSearchActivity.this,ResultActivity.class)
                            .setData(Uri.parse(exact)));
                    return true;
                }
                String host=uri.getHost();
                if("https".equalsIgnoreCase(uri.getScheme())
                        && ("www.kyodemo.net".equalsIgnoreCase(host)
                            ||"kyodemo.net".equalsIgnoreCase(host)))return false;
                hint.setText("外部リンクは「サイトで開く」から確認してください。");
                return true;
            }
            @Override public void onReceivedHttpError(WebView view,WebResourceRequest req,WebResourceResponse response){
                if(req.isForMainFrame())hint.setText(
                    "サイトのHTTP応答: "+response.getStatusCode()+"。ブラウザで確認してください。");
            }
            @Override public void onReceivedError(WebView view,WebResourceRequest req,WebResourceError error){
                if(req.isForMainFrame())hint.setText("サイトに接続できません。ブラウザで確認してください。");
            }
        });
        root.addView(web,new LinearLayout.LayoutParams(-1,0,1));
        web.loadUrl(url);
    }
    @Override protected void onDestroy(){
        if(web!=null){
            web.stopLoading();web.setWebViewClient(null);
            web.destroy();web=null;
        }
        super.onDestroy();
    }
}
