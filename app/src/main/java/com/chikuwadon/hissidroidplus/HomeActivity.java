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
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Toast;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ArrayAdapter;
import android.widget.TextView;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.nio.charset.Charset;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Independent search UI with a live and cached official 5ch board menu. */
public final class HomeActivity extends Activity {
    private EditText board,id,edge,edgeBoard,edgeId;
    private Button edgeDateSummary;
    private CheckBox hissiAnyDate,edgeAnyDate;
    private final Calendar edgeSelected=Calendar.getInstance();
    private Button dateSummary;
    private final Calendar selected=Calendar.getInstance();
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private android.content.SharedPreferences prefs;
    private boolean busy;
    private List<BoardMenu.Category> boardGroups;
    private String boardSource="";
    @Override protected void attachBaseContext(Context base){super.attachBaseContext(AppSettings.themeContext(base));}
    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        setContentView(R.layout.activity_home);
        AppTitle.apply(findViewById(R.id.app_title));
        AppSettings.configureBars(this);
        findViewById(R.id.home_more).setOnClickListener(this::showSettings);
        applyInsets();
        board=findViewById(R.id.board);id=findViewById(R.id.user_id);edge=findViewById(R.id.edge_url);
        edgeBoard=findViewById(R.id.edge_board);
        edgeId=findViewById(R.id.edge_user_id);
        edgeDateSummary=findViewById(R.id.edge_date_summary);
        edgeBoard.setText(getPreferences(MODE_PRIVATE).getString("edge_board","liveedge"));
        dateSummary=findViewById(R.id.date_summary);
        prefs=getPreferences(MODE_PRIVATE);
        hissiAnyDate=findViewById(R.id.hissi_any_date);
        edgeAnyDate=findViewById(R.id.edge_any_date);
        hissiAnyDate.setChecked(prefs.getBoolean("hissi_recent_search",false));
        edgeAnyDate.setChecked(prefs.getBoolean("edge_any_date",false));
        dateSummary.setVisibility(hissiAnyDate.isChecked()?View.GONE:View.VISIBLE);
        edgeDateSummary.setVisibility(edgeAnyDate.isChecked()?View.GONE:View.VISIBLE);
        hissiAnyDate.setOnCheckedChangeListener((button,checked)->{
            prefs.edit().putBoolean("hissi_recent_search",checked).apply();
            dateSummary.setVisibility(checked?View.GONE:View.VISIBLE);
        });
        edgeAnyDate.setOnCheckedChangeListener((button,checked)->{
            prefs.edit().putBoolean("edge_any_date",checked).apply();
            edgeDateSummary.setVisibility(checked?View.GONE:View.VISIBLE);
        });
        board.setText(prefs.getString("board",""));
        id.setText(prefs.getString("last_id",""));
        long time=prefs.getLong("last_date",-1L);
        if(time>0)selected.setTimeInMillis(time);
        refreshDate();
        long savedEdgeDay=prefs.getLong("edge_last_date",-1L);
        if(savedEdgeDay>0)edgeSelected.setTimeInMillis(savedEdgeDay);
        refreshEdgeDate();
        edgeDateSummary.setOnClickListener(v->{
            DatePickerDialog datePicker=new DatePickerDialog(this,(picker,year,month,day)->{
                edgeSelected.set(year,month,day);
                prefs.edit().putLong("edge_last_date",edgeSelected.getTimeInMillis()).apply();
                refreshEdgeDate();
            },edgeSelected.get(Calendar.YEAR),edgeSelected.get(Calendar.MONTH),edgeSelected.get(Calendar.DAY_OF_MONTH));
            datePicker.show();
        });
        findViewById(R.id.edge_search).setOnClickListener(v->{
            try{
                String code=edgeBoard.getText().toString().trim();
                String user=edgeId.getText().toString().trim()
                        .replaceFirst("(?i)^ID\\s*[:：]\\s*","").trim();
                if(!code.matches("[A-Za-z0-9_]{1,60}"))
                    throw new IllegalArgumentException("エッヂの板名が不正です");
                if(user.isEmpty()||user.length()>64||!user.matches("[A-Za-z0-9+./_-]+"))
                    throw new IllegalArgumentException("エッヂのIDが不正です");
                String date=String.format(Locale.ROOT,"%04d%02d%02d",
                        edgeSelected.get(Calendar.YEAR),edgeSelected.get(Calendar.MONTH)+1,
                        edgeSelected.get(Calendar.DAY_OF_MONTH));
                CheckerQuery.validateDate(date);
                prefs.edit().putString("edge_board",code).putString("edge_last_id",user).apply();
                // Resolve the site's real thread key in the background.
                // Do not launch an intermediate browser or search Activity.
                openEdgeDirect(code,date,user,edgeAnyDate.isChecked());
            }catch(IllegalArgumentException e){
                Toast.makeText(this,e.getMessage(),Toast.LENGTH_LONG).show();
            }
        });
        edgeId.setText(prefs.getString("edge_last_id",""));
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
                if(hissiAnyDate.isChecked()){
                    searchHissiRecent(query.board,query.id);
                }else{
                    Intent next=new Intent(this,ResultActivity.class);
                    next.putExtra("board",query.board);next.putExtra("date",query.date);next.putExtra("id",query.id);
                    startActivity(next);
                }
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
                ThemedMenu.choose(this,"お気に入りをすべて削除しますか？",
                    new String[]{"戻る","すべて削除"},-1,choice->{
                        if(choice==1)prefs.edit().remove("favorite_boards").apply();
                    });
            });
        });
    }
    /** Toolbar overflow stays attached to ⋮ instead of opening a full modal list. */
    private void showSettings(View anchor){
        ThemedMenu.showOverflow(this,anchor,new String[]{
            "検索履歴", "テーマ", "リンクの開き方", "このアプリについて"
        },index->{
            if(index==0)startActivity(new Intent(this,HistoryActivity.class));
            else if(index==1)showThemePicker();
            else if(index==2)showLinkModePicker();
            else ThemedMenu.showAbout(this);
        });
    }
    private void showThemePicker(){
        String[] values={AppSettings.THEME_DARK,AppSettings.THEME_LIGHT,AppSettings.THEME_SYSTEM};
        String[] labels={"ダーク", "ライト", "端末の設定に合わせる"};
        String current=AppSettings.prefs(this).getString(AppSettings.THEME,AppSettings.THEME_DARK);
        int checked=java.util.Arrays.asList(values).indexOf(current);
        ThemedMenu.choose(this,"テーマ",labels,checked,index->{
            AppSettings.prefs(this).edit().putString(AppSettings.THEME,values[index]).apply();
            recreate();
        });
    }
    private void showLinkModePicker(){
        String[] labels={"ChMate連携（元の画面へ戻る）","単独アプリ（別タスクで開く）"};
        ThemedMenu.choose(this,"リンクの開き方",labels,AppSettings.standalone(this)?1:0,index->{
            AppSettings.prefs(this).edit().putString(AppSettings.LINK_MODE,
                    index==1?AppSettings.MODE_STANDALONE:AppSettings.MODE_CONNECTED).apply();
        });
    }

    private void refreshDate(){
        dateSummary.setText(String.format(Locale.JAPAN,"%04d年%02d月%02d日  ▾",selected.get(Calendar.YEAR),selected.get(Calendar.MONTH)+1,selected.get(Calendar.DAY_OF_MONTH)));
    }
    private void refreshEdgeDate(){
        edgeDateSummary.setText(String.format(Locale.JAPAN,"%04d年%02d月%02d日  ▾",
                edgeSelected.get(Calendar.YEAR),edgeSelected.get(Calendar.MONTH)+1,
                edgeSelected.get(Calendar.DAY_OF_MONTH)));
    }
    /** Seven one-day lookups at most. No loop over the whole archive. */
    private void searchHissiRecent(String code,String user){
        if(busy)return;
        busy=true;
        Toast.makeText(this,"5chの直近7日を検索中…",Toast.LENGTH_SHORT).show();
        worker.execute(()->{
            HissiRecentSearch.Result found=HissiRecentSearch.find(code,user,
                LocalDate.now(ZoneId.of("Asia/Tokyo")), (boardName,day,idValue)->{
                    String url=HissiAdapter.resultUrl(boardName,day,idValue);
                    String html=HttpTransport.getForVerification(url,Charset.forName("Shift_JIS"));
                    CheckerResult check=HissiAdapter.parse(html,url);
                    if(check.posts.isEmpty() && check.reportedPostCount<0)
                        throw new java.io.IOException("取得ページの形式を確認できませんでした");
                    return !check.posts.isEmpty()||check.reportedPostCount>0;
                });
            runOnUiThread(()->{
                busy=false;
                if(isFinishing()||isDestroyed())return;
                if(found.day!=null){
                    Intent next=new Intent(this,ResultActivity.class);
                    next.putExtra("board",code);next.putExtra("date",found.day);next.putExtra("id",user);
                    startActivity(next);
                }else if(found.error!=null){
                    Toast.makeText(this,"検索を完了できませんでした（"+
                        found.checkedDays+"日目）: "+found.error,Toast.LENGTH_LONG).show();
                }else{
                    Toast.makeText(this,"直近7日間に該当する投稿は見つかりませんでした",
                        Toast.LENGTH_LONG).show();
                }
            });
        });
    }
    /** Use last successful official menu without network calls on subsequent opens. */
    private void openEdgeDirect(String code,String date,String user,boolean anyDate){
        if(busy)return;
        busy=true;
        Toast.makeText(this,"エッヂのIDを検索中…",Toast.LENGTH_SHORT).show();
        worker.execute(()->{
            try{
                String url=anyDate?EdgeSearchLink.searchUrlAnyDate(code,user):
                        EdgeSearchLink.searchUrl(code,date,user);
                String html=HttpTransport.get(url,StandardCharsets.UTF_8);
                List<EdgeSearchResults.Item> matches=EdgeSearchResults.parse(
                        html,url,code,anyDate?null:date,user);
                // Site search date parameters alone cannot prove that a linked
                // thread contains a post by this ID on the selected day.
                // Examine a bounded number of checker pages, stopping at the
                // first verified match. Preserve WebView fallback if HTTP is
                // inaccessible, but never display unverified posts.
                EdgeSearchResults.Item chosen=null;
                EdgeSearchResults.Item unverified=null;
                int inspected=0;
                for(EdgeSearchResults.Item item:matches){
                    if(anyDate){chosen=item;break;}
                    if(inspected++>=6)break;
                    try{
                        CheckerQuery candidate=CheckerQuery.parseUrl(item.checkerUrl);
                        CheckerResult check=new EdgeAdapter().loadForVerification(candidate);
                        if(check.posts.isEmpty()){
                            // Missing or JavaScript-dependent page: let the result
                            // Activity verify with WebView rather than probing
                            // another six candidates on a slow connection.
                            unverified=item;break;
                        }else if(!EdgeSearchValidation.matchingPosts(check,date,user).posts.isEmpty()){
                            chosen=item;break;
                        }
                    }catch(java.io.IOException inaccessible){
                        // The native request may be blocked while WebView still
                        // works. Allow its fallback but keep strict date checks.
                        unverified=item;break;
                    }
                }
                final EdgeSearchResults.Item ready=chosen==null?unverified:chosen;
                final boolean noMatches=!matches.isEmpty()&&ready==null;
                runOnUiThread(()->{
                    busy=false;
                    if(isFinishing()||isDestroyed())return;
                    if(ready!=null){
                        startActivity(new Intent(this,ResultActivity.class)
                            .setData(android.net.Uri.parse(ready.checkerUrl))
                            .putExtra(ResultActivity.EXTRA_VERIFY_EDGE_SEARCH_DATE,!anyDate));
                    }else{
                        Toast.makeText(this,noMatches
                            ?"指定した日付・IDに一致する投稿はありません"
                            :(anyDate?"該当するIDの投稿が見つかりませんでした":
                                "指定した日付・IDの投稿が見つかりませんでした"),
                            Toast.LENGTH_LONG).show();
                    }
                });
            }catch(Exception error){
                runOnUiThread(()->{
                    busy=false;
                    if(!isFinishing()&&!isDestroyed())
                        Toast.makeText(this,"検索に失敗しました: "+
                            (error.getMessage()==null?"通信エラー":error.getMessage()),
                            Toast.LENGTH_LONG).show();
                });
            }
        });
    }
    private void showBoards(){
        if(busy)return;
        if(boardGroups!=null&&!boardGroups.isEmpty()){
            showCategories(boardGroups,boardSource);return;
        }
        busy=true;
        worker.execute(()->{
            List<BoardMenu.Category> groups=null;
            String origin="保存済みの5ch公式メニュー";
            try{
                try(FileInputStream in=openFileInput("bbsmenu_5ch.json")){
                    groups=BoardMenu.parseJson(readCached(in));
                }
            }catch(Exception ignored){}
            if(groups==null||groups.isEmpty()){
                try(FileInputStream in=openFileInput("bbsmenu_5ch.html")){
                    groups=BoardMenu.parseHtml(readCached(in));
                }catch(Exception ignored){}
            }
            if(groups==null||groups.isEmpty()){
                groups=fetchOfficialMenu();
                origin="5ch公式メニュー（最新取得）";
            }
            if(groups==null||groups.isEmpty()){
                // Generated independently from the publicly served 5ch menu.
                try(InputStreamReader snapshot=new InputStreamReader(
                        getAssets().open("offline_boards.tsv"),StandardCharsets.UTF_8)){
                    groups=BoardMenu.parseOfflineSnapshot(snapshot);
                    origin="内蔵板一覧（5ch公式メニューの保存版）";
                }catch(Exception ignored){}
            }
            final List<BoardMenu.Category> ready=groups;
            final String label=origin;
            runOnUiThread(()->{
                busy=false;
                if(isFinishing()||isDestroyed())return;
                if(ready==null||ready.isEmpty()){
                    Toast.makeText(this,"板一覧を取得できませんでした。通信環境をご確認ください。板名は直接入力できます",Toast.LENGTH_LONG).show();return;
                }
                boardGroups=ready;boardSource=label;
                showCategories(ready,label);
            });
        });
    }
    private List<BoardMenu.Category> fetchOfficialMenu(){
        try{
            String json=HttpTransport.get(BoardMenu.JSON_URL,StandardCharsets.UTF_8);
            List<BoardMenu.Category> groups=BoardMenu.parseJson(json);
            if(!groups.isEmpty()){
                try(FileOutputStream out=openFileOutput("bbsmenu_5ch.json",MODE_PRIVATE)){
                    out.write(json.getBytes(StandardCharsets.UTF_8));
                }
                deleteFile("bbsmenu_5ch.html");
                return groups;
            }
        }catch(Exception ignored){}
        try{
            String html=HttpTransport.get(BoardMenu.HTML_URL,StandardCharsets.UTF_8);
            List<BoardMenu.Category> groups=BoardMenu.parseHtml(html);
            if(!groups.isEmpty()){
                try(FileOutputStream out=openFileOutput("bbsmenu_5ch.html",MODE_PRIVATE)){
                    out.write(html.getBytes(StandardCharsets.UTF_8));
                }
                deleteFile("bbsmenu_5ch.json");
                return groups;
            }
        }catch(Exception ignored){}
        return null;
    }
    private void refreshBoards(){
        if(busy)return;
        busy=true;
        Toast.makeText(this,"5ch公式メニューを更新中…",Toast.LENGTH_SHORT).show();
        worker.execute(()->{
            List<BoardMenu.Category> latest=fetchOfficialMenu();
            runOnUiThread(()->{
                busy=false;
                if(isFinishing()||isDestroyed())return;
                if(latest!=null&&!latest.isEmpty()){
                    boardGroups=latest;
                    boardSource="5ch公式メニュー（更新済み）";
                    showCategories(boardGroups,boardSource);
                }else{
                    Toast.makeText(this,"更新できませんでした。保存済み一覧を表示します",Toast.LENGTH_LONG).show();
                    if(boardGroups!=null)showCategories(boardGroups,boardSource);
                }
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
        showList("カテゴリを選択\n"+source,labels,index->showCategory(groups,source,index),null,true);
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
        showList(title,labels,pick,back,false);
    }
    private void showList(String title,List<String> labels,Pick pick,Runnable back,boolean refresh){
        if(isFinishing()||isDestroyed())return;
        LinearLayout panel=new LinearLayout(this);panel.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg=new GradientDrawable();bg.setColor(getColor(R.color.panel));bg.setCornerRadius(dp(16));
        bg.setStroke(dp(1),getColor(R.color.border));panel.setBackground(bg);
        LinearLayout titleRow=new LinearLayout(this);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        panel.addView(titleRow);
        TextView heading=new TextView(this);
        heading.setText(title);heading.setTextSize(17);
        heading.setTextColor(getColor(R.color.text_primary));
        heading.setPadding(dp(18),dp(17),0,dp(11));
        titleRow.addView(heading,new LinearLayout.LayoutParams(0,-2,1));
        TextView update=new TextView(this);
        update.setText("⟳");update.setContentDescription("5ch公式メニューを手動更新");
        update.setTextSize(26);update.setTextColor(getColor(R.color.accent));
        update.setGravity(Gravity.CENTER);
        update.setVisibility(refresh?View.VISIBLE:View.GONE);
        titleRow.addView(update,new LinearLayout.LayoutParams(dp(54),dp(54)));
        ListView list=new ListView(this);list.setDivider(new android.graphics.drawable.ColorDrawable(getColor(R.color.divider)));list.setDividerHeight(dp(1));
        list.setSelector(new android.graphics.drawable.ColorDrawable(getColor(R.color.panel_pressed)));
        ArrayAdapter<String> adapter=new ArrayAdapter<String>(this,android.R.layout.simple_list_item_1,labels){
            @Override public View getView(int position,View convert,android.view.ViewGroup parent){
                TextView row=(TextView)super.getView(position,convert,parent);
                row.setTextColor(getColor(R.color.text_primary));row.setTextSize(14);row.setMinHeight(dp(49));
                row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(dp(18),dp(8),dp(14),dp(8));return row;
            }
        };
        list.setAdapter(adapter);panel.addView(list,new LinearLayout.LayoutParams(-1,0,1));
        TextView close=new TextView(this);close.setText(back==null?"閉じる":"カテゴリに戻る");close.setTextSize(14);
        close.setTextColor(getColor(R.color.accent));close.setGravity(Gravity.END|Gravity.CENTER_VERTICAL);
        close.setPadding(dp(18),0,dp(18),0);close.setHeight(dp(52));panel.addView(close);
        AlertDialog dialog=new AlertDialog.Builder(this).create();dialog.setView(panel);dialog.show();
        Window w=dialog.getWindow();if(w!=null){w.setBackgroundDrawableResource(android.R.color.transparent);
            w.setGravity(Gravity.CENTER);w.setLayout(getResources().getDisplayMetrics().widthPixels-dp(34),
                Math.min((int)(getResources().getDisplayMetrics().heightPixels*0.77),dp(700)));}
        update.setOnClickListener(v->{dialog.dismiss();refreshBoards();});
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
