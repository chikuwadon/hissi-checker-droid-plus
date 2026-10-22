package com.chikuwadon.hissidroidplus;

import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ArrayAdapter;
import android.widget.TextView;
import java.io.InputStreamReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Original APK 1.5 reimplementation: modern search and official two-level board menu. */
public final class HomeActivity extends Activity {
    private EditText board,id,edge;
    private Button dateSummary;
    private final Calendar selected=Calendar.getInstance();
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private android.content.SharedPreferences prefs;
    private boolean busy;
    @Override protected void attachBaseContext(Context base){super.attachBaseContext(AppSettings.themeContext(base));}
    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        setContentView(R.layout.activity_home);
        AppTitle.apply(findViewById(R.id.app_title));
        AppSettings.configureBars(this);
        findViewById(R.id.home_more).setOnClickListener(v->showSettings());
        applyInsets();
        board=findViewById(R.id.board);id=findViewById(R.id.user_id);edge=findViewById(R.id.edge_url);
        dateSummary=findViewById(R.id.date_summary);
        prefs=getPreferences(MODE_PRIVATE);
        board.setText(prefs.getString("board",""));
        id.setText(prefs.getString("last_id",""));
        long time=prefs.getLong("last_date",-1L);
        if(time>0)selected.setTimeInMillis(time);
        refreshDate();
        dateSummary.setOnClickListener(v->{
            DatePickerDialog dialog=new DatePickerDialog(this,(picker,year,month,day)->{
                selected.set(year,month,day);refreshDate();prefs.edit().putLong("last_date",selected.getTimeInMillis()).apply();
            },selected.get(Calendar.YEAR),selected.get(Calendar.MONTH),selected.get(Calendar.DAY_OF_MONTH));
            dialog.show();
        });
        findViewById(R.id.search).setOnClickListener(v->{
            try {
                String d=String.format(Locale.ROOT,"%04d%02d%02d",selected.get(Calendar.YEAR),selected.get(Calendar.MONTH)+1,selected.get(Calendar.DAY_OF_MONTH));
                CheckerQuery query=CheckerQuery.hissi(board.getText().toString().trim(),d,id.getText().toString().trim());
                prefs.edit().putString("board",query.board).putString("last_id",query.id).apply();
                Intent next=new Intent(this,ResultActivity.class);
                next.putExtra("board",query.board);next.putExtra("date",query.date);next.putExtra("id",query.id);
                startActivity(next);
            }catch(IllegalArgumentException e){Toast.makeText(this,e.getMessage(),Toast.LENGTH_LONG).show();}
        });
        findViewById(R.id.open_edge).setOnClickListener(v->{
            try {
                CheckerQuery query=CheckerQuery.parseUrl(edge.getText().toString());
                if(query.site!=CheckerQuery.Site.EDGE)throw new IllegalArgumentException("エッヂのURLを指定してください");
                startActivity(new Intent(this,ResultActivity.class).setData(android.net.Uri.parse(query.url)));
            }catch(IllegalArgumentException e){Toast.makeText(this,e.getMessage(),Toast.LENGTH_LONG).show();}
        });
        findViewById(R.id.choose_board).setOnClickListener(v->showBoards());
        findViewById(R.id.add_favorite).setOnClickListener(v->{
            String current=board.getText().toString().trim();
            if(!current.matches("[A-Za-z0-9_]+")){Toast.makeText(this,"板名を指定してください",Toast.LENGTH_SHORT).show();return;}
            java.util.Set<String> favorites=new java.util.HashSet<>(prefs.getStringSet("favorite_boards",Collections.emptySet()));
            favorites.add(current);prefs.edit().putStringSet("favorite_boards",favorites).apply();
            Toast.makeText(this,"お気に入りに登録しました",Toast.LENGTH_SHORT).show();
        });
        findViewById(R.id.show_favorites).setOnClickListener(v->{
            List<String> favorites=new ArrayList<>(prefs.getStringSet("favorite_boards",Collections.emptySet()));
            Collections.sort(favorites);
            if(favorites.isEmpty()){Toast.makeText(this,"お気に入りがありません",Toast.LENGTH_SHORT).show();return;}
            showList("お気に入り",favorites,position->board.setText(favorites.get(position)),()->{
                new AlertDialog.Builder(this).setTitle("お気に入りの削除")
                    .setMessage("登録済みの板をすべて削除しますか？")
                    .setNegativeButton("キャンセル",null)
                    .setPositiveButton("削除",(d,w)->prefs.edit().remove("favorite_boards").apply()).show();
            });
        });
    }
    private void showSettings(){
        String[] options={"テーマ", "リンクの開き方", "このアプリについて"};
        new AlertDialog.Builder(this).setTitle("設定").setItems(options,(d,i)->{
            if(i==0)showThemePicker();
            else if(i==1)showLinkModePicker();
            else new AlertDialog.Builder(this).setTitle("必死チェッカーdroid +")
                    .setMessage("Hissi Checker Droid Plus / v0.1.4\nオリジナルAPKを基準に再構築。\nエッヂは実験的対応です。")
                    .setPositiveButton("閉じる",null).show();
        }).setNegativeButton("閉じる",null).show();
    }
    private void showThemePicker(){
        String[] values={AppSettings.THEME_DARK,AppSettings.THEME_LIGHT,AppSettings.THEME_SYSTEM};
        String[] labels={"ダーク", "ライト", "端末の設定に合わせる"};
        String selectedTheme=AppSettings.prefs(this).getString(AppSettings.THEME,AppSettings.THEME_DARK);
        int checked=java.util.Arrays.asList(values).indexOf(selectedTheme);
        new AlertDialog.Builder(this).setTitle("テーマ").setSingleChoiceItems(labels,checked,(d,i)->{
            AppSettings.prefs(this).edit().putString(AppSettings.THEME,values[i]).apply();
            d.dismiss();recreate();
        }).setNegativeButton("キャンセル",null).show();
    }
    private void showLinkModePicker(){
        String[] labels={"ChMate連携（戻ると元の画面へ）", "単独アプリ（別タスクで開く）"};
        int selectedMode=AppSettings.standalone(this)?1:0;
        new AlertDialog.Builder(this).setTitle("外部リンクの開き方")
            .setSingleChoiceItems(labels,selectedMode,(d,i)->{
                AppSettings.prefs(this).edit().putString(AppSettings.LINK_MODE,
                    i==1?AppSettings.MODE_STANDALONE:AppSettings.MODE_CONNECTED).apply();
                d.dismiss();
            })
            .setNegativeButton("キャンセル",null).show();
    }

    private void refreshDate(){
        dateSummary.setText(String.format(Locale.JAPAN,"%04d年%02d月%02d日  ▾",selected.get(Calendar.YEAR),selected.get(Calendar.MONTH)+1,selected.get(Calendar.DAY_OF_MONTH)));
    }
    private void showBoards(){
        if(busy)return;
        busy=true;
        Toast.makeText(this,"5chの板一覧を更新中…",Toast.LENGTH_SHORT).show();
        worker.execute(()->{
            List<BoardMenu.Category> groups=null;String info="5ch公式メニュー（最新取得）";
            try{
                String json=HttpTransport.get(BoardMenu.JSON_URL,StandardCharsets.UTF_8);
                groups=BoardMenu.parseJson(json);
                try(FileOutputStream file=openFileOutput("bbsmenu_5ch.json",MODE_PRIVATE)){file.write(json.getBytes(StandardCharsets.UTF_8));}
            }catch(Exception first){
                try{
                    String html=HttpTransport.get(BoardMenu.HTML_URL,StandardCharsets.UTF_8);
                    groups=BoardMenu.parseHtml(html);
                    if(groups.isEmpty())throw new IllegalStateException("HTMLを解析できません");
                    info="5ch公式メニュー（HTML取得）";
                }catch(Exception second){
                    try{
                        String cached;
                        try(FileInputStream in=openFileInput("bbsmenu_5ch.json")){cached=readCached(in);}
                        groups=BoardMenu.parseJson(cached);
                        info="前回取得した5ch公式メニュー（オフライン）";
                    }catch(Exception third){
                        try{
                            groups=BoardMenu.bundled(BoardCatalog.read(new InputStreamReader(getAssets().open("original_boards.tsv"),StandardCharsets.UTF_8)));
                            info="旧版の保存リスト（最新ではありません）";
                        }catch(Exception ignored){}
                    }
                }
            }
            final List<BoardMenu.Category> menu=groups;
            final String label=info;
            runOnUiThread(()->{
                busy=false;
                if(isFinishing()||isDestroyed())return;
                if(menu==null||menu.isEmpty()){Toast.makeText(this,"板一覧を取得できませんでした",Toast.LENGTH_LONG).show();return;}
                showCategories(menu,label);
            });
        });
    }
    private static String readCached(java.io.InputStream in) throws java.io.IOException {
        try(java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream()){
            byte[] buffer=new byte[8192];int n;
            while((n=in.read(buffer))!=-1){
                if(out.size()+n>4*1024*1024)throw new java.io.IOException("Too large");
                out.write(buffer,0,n);
            }
            return new String(out.toByteArray(),StandardCharsets.UTF_8);
        }
    }
    private void showCategories(List<BoardMenu.Category> groups,String source){
        List<String> labels=new ArrayList<>();
        for(BoardMenu.Category g:groups)labels.add(g.name+"  （"+g.boards.size()+"）");
        showList("カテゴリを選択\n"+source,labels,index->showCategory(groups,source,index),null);
    }
    private void showCategory(List<BoardMenu.Category> groups,String source,int index){
        BoardMenu.Category category=groups.get(index);
        List<String> labels=new ArrayList<>();
        for(BoardCatalog.Board b:category.boards)labels.add(b.toString());
        showList(category.name+"  ·  "+labels.size()+"板",labels,
            position->board.setText(category.boards.get(position).code),()->showCategories(groups,source));
    }
    private interface Pick { void selected(int position); }
    /** Scrollable centered dialog with predictable cyan touch feedback; never a bottom sheet. */
    private void showList(String title,List<String> labels,Pick pick,Runnable back){
        if(isFinishing()||isDestroyed())return;
        LinearLayout panel=new LinearLayout(this);panel.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg=new GradientDrawable();bg.setColor(Color.rgb(25,36,40));bg.setCornerRadius(dp(16));
        bg.setStroke(dp(1),Color.rgb(53,71,76));panel.setBackground(bg);
        TextView heading=new TextView(this);heading.setText(title);heading.setTextSize(17);heading.setTextColor(0xffEDF5F5);
        heading.setPadding(dp(18),dp(17),dp(18),dp(11));panel.addView(heading);
        ListView list=new ListView(this);list.setDivider(new android.graphics.drawable.ColorDrawable(0xff2C3C3F));list.setDividerHeight(dp(1));
        list.setSelector(new android.graphics.drawable.ColorDrawable(0xff164B4E));
        ArrayAdapter<String> adapter=new ArrayAdapter<String>(this,android.R.layout.simple_list_item_1,labels){
            @Override public View getView(int position,View convert,android.view.ViewGroup parent){
                TextView row=(TextView)super.getView(position,convert,parent);
                row.setTextColor(0xffDFE8E9);row.setTextSize(14);row.setMinHeight(dp(49));
                row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(dp(18),dp(8),dp(14),dp(8));return row;
            }
        };
        list.setAdapter(adapter);panel.addView(list,new LinearLayout.LayoutParams(-1,0,1));
        TextView close=new TextView(this);close.setText(back==null?"閉じる":"カテゴリに戻る");close.setTextSize(14);
        close.setTextColor(0xff51CCC4);close.setGravity(Gravity.END|Gravity.CENTER_VERTICAL);
        close.setPadding(dp(18),0,dp(18),0);close.setHeight(dp(52));panel.addView(close);
        AlertDialog dialog=new AlertDialog.Builder(this).create();dialog.setView(panel);dialog.show();
        Window w=dialog.getWindow();if(w!=null){w.setBackgroundDrawableResource(android.R.color.transparent);
            w.setGravity(Gravity.CENTER);w.setLayout(getResources().getDisplayMetrics().widthPixels-dp(34),
                Math.min((int)(getResources().getDisplayMetrics().heightPixels*0.77),dp(700)));}
        list.setOnItemClickListener((p,v,i,key)->{dialog.dismiss();pick.selected(i);});
        close.setOnClickListener(v->{dialog.dismiss();if(back!=null)back.run();});
    }
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private void applyInsets(){
        if(android.os.Build.VERSION.SDK_INT>=30){
            getWindow().setDecorFitsSystemWindows(false);
            View root=findViewById(android.R.id.content);
            final int top=root.getPaddingTop(),bottom=root.getPaddingBottom();
            root.setOnApplyWindowInsetsListener((v,insets)->{
                android.graphics.Insets bars=insets.getInsets(WindowInsets.Type.statusBars()|WindowInsets.Type.navigationBars()|WindowInsets.Type.displayCutout());
                v.setPadding(v.getPaddingLeft(),top+bars.top,v.getPaddingRight(),bottom+bars.bottom);
                return WindowInsets.CONSUMED;
            });root.requestApplyInsets();
        }
    }
    @Override protected void onDestroy(){worker.shutdownNow();super.onDestroy();}
}
