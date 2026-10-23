package com.chikuwadon.hissidroidplus;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.widget.AbsListView;
import android.widget.BaseAdapter;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;

/** Compact, scrollable history/favorites UI; favorites are independent of the 100 recent entries. */
public final class HistoryActivity extends Activity {
    private int tab=0,sort=0;
    private final String[] sorts={"新しい順","古い順","板名順","検索日順"};
    private TextView recentTab,favoriteTab,sortLabel;
    private ListView list;
    private final List<SearchHistory.Entry> rows=new ArrayList<>();
    private final EntriesAdapter adapter=new EntriesAdapter();
    @Override protected void attachBaseContext(Context base){super.attachBaseContext(AppSettings.themeContext(base));}
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private TextView text(String value,int size,int color){
        TextView v=new TextView(this);v.setText(value);v.setTextSize(size);
        v.setTextColor(color);v.setGravity(Gravity.CENTER_VERTICAL);return v;
    }
    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        AppSettings.configureBars(this);
        LinearLayout root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(getColor(R.color.surface));
        setContentView(root);
        if(android.os.Build.VERSION.SDK_INT>=30){
            getWindow().setDecorFitsSystemWindows(false);
            final int top=root.getPaddingTop(),bottom=root.getPaddingBottom();
            root.setOnApplyWindowInsetsListener((v,insets)->{
                android.graphics.Insets bars=insets.getInsets(
                    WindowInsets.Type.statusBars()|WindowInsets.Type.navigationBars()|WindowInsets.Type.displayCutout());
                v.setPadding(v.getPaddingLeft(),top+bars.top,v.getPaddingRight(),bottom+bars.bottom);
                return WindowInsets.CONSUMED;
            });
            root.requestApplyInsets();
        }
        LinearLayout toolbar=new LinearLayout(this);toolbar.setGravity(Gravity.CENTER_VERTICAL);
        toolbar.setPadding(dp(8),0,dp(8),0);
        root.addView(toolbar,new LinearLayout.LayoutParams(-1,dp(52)));
        TextView back=text("‹",32,getColor(R.color.text_primary));back.setGravity(Gravity.CENTER);
        back.setContentDescription("戻る");
        toolbar.addView(back,new LinearLayout.LayoutParams(dp(44),dp(48)));
        back.setOnClickListener(v->finish());
        TextView title=text("検索履歴",18,getColor(R.color.text_primary));
        toolbar.addView(title,new LinearLayout.LayoutParams(0,-2,1));
        sortLabel=text("新しい順 ▾",13,getColor(R.color.accent));
        sortLabel.setGravity(Gravity.CENTER);
        sortLabel.setPadding(dp(8),0,dp(8),0);
        toolbar.addView(sortLabel,new LinearLayout.LayoutParams(-2,dp(48)));
        sortLabel.setOnClickListener(v->ThemedMenu.showOverflow(this,sortLabel,sorts,i->{
            sort=i;refresh();
        }));
        LinearLayout tabs=new LinearLayout(this);tabs.setPadding(dp(4),0,dp(4),0);
        root.addView(tabs,new LinearLayout.LayoutParams(-1,dp(44)));
        recentTab=text("履歴",14,getColor(R.color.accent));
        favoriteTab=text("お気に入り",14,getColor(R.color.text_secondary));
        recentTab.setGravity(Gravity.CENTER);favoriteTab.setGravity(Gravity.CENTER);
        tabs.addView(recentTab,new LinearLayout.LayoutParams(0,-1,1));
        tabs.addView(favoriteTab,new LinearLayout.LayoutParams(0,-1,1));
        recentTab.setOnClickListener(v->{tab=0;refresh();});
        favoriteTab.setOnClickListener(v->{tab=1;refresh();});
        list=new ListView(this);
        list.setDivider(new ColorDrawable(getColor(R.color.divider)));
        list.setDividerHeight(dp(1));
        list.setSelector(new ColorDrawable(getColor(R.color.panel_pressed)));
        list.setFastScrollEnabled(true);
        list.setAdapter(adapter);
        root.addView(list,new LinearLayout.LayoutParams(-1,0,1));
        list.setOnItemClickListener((parent,view,position,id)->{
            if(position<0||position>=rows.size())return;
            String url=rows.get(position).url;
            try {CheckerQuery.parseUrl(url);} catch(Exception invalid){
                android.widget.Toast.makeText(this,"履歴のURLが無効です",android.widget.Toast.LENGTH_SHORT).show();return;
            }
            startActivity(new Intent(this,ResultActivity.class).setData(Uri.parse(url)));
        });
        refresh();
    }
    @Override protected void onResume(){super.onResume();if(list!=null)refresh();}
    private void refresh(){
        if(list==null)return;
        List<SearchHistory.Entry> data=tab==0?SearchHistory.recents(this):SearchHistory.favorites(this);
        rows.clear();rows.addAll(SearchHistory.sorted(data,sort));
        recentTab.setText("履歴 ("+SearchHistory.recents(this).size()+")");
        favoriteTab.setText("お気に入り ("+SearchHistory.favorites(this).size()+")");
        recentTab.setTextColor(getColor(tab==0?R.color.accent:R.color.text_secondary));
        favoriteTab.setTextColor(getColor(tab==1?R.color.accent:R.color.text_secondary));
        sortLabel.setText(sorts[sort]+" ▾");
        adapter.notifyDataSetChanged();
    }
    private final class EntriesAdapter extends BaseAdapter {
        @Override public int getCount(){return rows.size();}
        @Override public Object getItem(int position){return rows.get(position);}
        @Override public long getItemId(int position){return position;}
        @Override public View getView(int position,View convert,ViewGroup parent){
            SearchHistory.Entry entry=rows.get(position);
            LinearLayout row=new LinearLayout(HistoryActivity.this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(14),dp(6),dp(9),dp(6));
            row.setMinimumHeight(dp(64));
            LinearLayout labels=new LinearLayout(HistoryActivity.this);
            labels.setOrientation(LinearLayout.VERTICAL);
            labels.setGravity(Gravity.CENTER_VERTICAL);
            TextView title=text(entry.title(),14,getColor(R.color.text_primary));
            title.setSingleLine(true);
            title.setEllipsize(android.text.TextUtils.TruncateAt.END);
            labels.addView(title);
            TextView desc=text(entry.detail(),11,getColor(R.color.text_secondary));
            desc.setSingleLine(true);desc.setEllipsize(android.text.TextUtils.TruncateAt.END);
            labels.addView(desc);
            row.addView(labels,new LinearLayout.LayoutParams(0,dp(52),1));
            boolean saved=SearchHistory.isFavorite(HistoryActivity.this,entry.url);
            TextView star=text(saved?"★":"☆",23,saved?getColor(R.color.accent):getColor(R.color.text_secondary));
            star.setGravity(Gravity.CENTER);
            star.setContentDescription(saved?"お気に入りを解除":"お気に入りに保存");
            star.setFocusable(false);
            row.addView(star,new LinearLayout.LayoutParams(dp(48),dp(48)));
            star.setOnClickListener(v->{
                SearchHistory.toggleFavorite(HistoryActivity.this,entry);
                refresh();
            });
            return row;
        }
    }
}
