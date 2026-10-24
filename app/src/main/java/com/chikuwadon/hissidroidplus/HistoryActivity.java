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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Latest 100 visits and independent starred entries, with explicit destructive actions. */
public final class HistoryActivity extends Activity {
    private int tab=0,sort=0;
    private boolean selection=false;
    // Highlight the long-pressed row only while its confirmation dialog is visible.
    private String pendingConfirmationUrl=null;
    private final Set<String> chosen=new HashSet<>();
    private final String[] sorts={"新しい順","古い順","板名順","検索日順"};
    private TextView recentTab,favoriteTab,sortLabel,toolbarTitle,menuButton,selectedActions;
    private TextView cancelSelection,deleteSelected;
    private ListView list;
    private LinearLayout selectionActions;
    private final List<SearchHistory.Entry> rows=new ArrayList<>();
    private final Set<String> favoriteUrls=new HashSet<>();
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
        if(android.os.Build.VERSION.SDK_INT>=33){
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                    android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT,
                    this::navigateBack);
        }
        if(android.os.Build.VERSION.SDK_INT>=30){
            getWindow().setDecorFitsSystemWindows(false);
            root.setOnApplyWindowInsetsListener((v,insets)->{
                android.graphics.Insets bars=insets.getInsets(
                    WindowInsets.Type.statusBars()|WindowInsets.Type.navigationBars()|WindowInsets.Type.displayCutout());
                v.setPadding(v.getPaddingLeft(),bars.top,v.getPaddingRight(),bars.bottom);
                return WindowInsets.CONSUMED;
            });root.requestApplyInsets();
        }
        LinearLayout toolbar=new LinearLayout(this);
        toolbar.setGravity(Gravity.CENTER_VERTICAL);
        toolbar.setPadding(dp(8),0,dp(8),0);
        root.addView(toolbar,new LinearLayout.LayoutParams(-1,dp(52)));
        TextView back=text("‹",32,getColor(R.color.text_primary));
        back.setGravity(Gravity.CENTER);
        back.setContentDescription("戻る");
        toolbar.addView(back,new LinearLayout.LayoutParams(dp(44),dp(48)));
        back.setOnClickListener(v->navigateBack());
        toolbarTitle=text("検索履歴",18,getColor(R.color.text_primary));
        toolbar.addView(toolbarTitle,new LinearLayout.LayoutParams(0,-2,1));
        sortLabel=text("新しい順 ▾",13,getColor(R.color.accent));
        sortLabel.setGravity(Gravity.CENTER);
        sortLabel.setPadding(dp(8),0,dp(8),0);
        toolbar.addView(sortLabel,new LinearLayout.LayoutParams(-2,dp(48)));
        sortLabel.setOnClickListener(v->ThemedMenu.showOverflow(this,sortLabel,sorts,i->{sort=i;refresh();}));
        menuButton=text("⋮",25,getColor(R.color.text_primary));
        menuButton.setGravity(Gravity.CENTER);
        menuButton.setContentDescription("履歴の操作");
        toolbar.addView(menuButton,new LinearLayout.LayoutParams(dp(44),dp(48)));
        menuButton.setOnClickListener(v->ThemedMenu.showOverflow(this,menuButton,
            new String[]{"複数選択","履歴を全削除"},i->{
                if(i==0){tab=0;selection=true;chosen.clear();refresh();}
                else confirmClear();
            }));

        LinearLayout tabs=new LinearLayout(this);tabs.setPadding(dp(4),0,dp(4),0);
        root.addView(tabs,new LinearLayout.LayoutParams(-1,dp(44)));
        recentTab=text("履歴",14,getColor(R.color.accent));
        favoriteTab=text("お気に入り",14,getColor(R.color.text_secondary));
        recentTab.setGravity(Gravity.CENTER);favoriteTab.setGravity(Gravity.CENTER);
        tabs.addView(recentTab,new LinearLayout.LayoutParams(0,-1,1));
        tabs.addView(favoriteTab,new LinearLayout.LayoutParams(0,-1,1));
        recentTab.setOnClickListener(v->{tab=0;refresh();});
        favoriteTab.setOnClickListener(v->{
            if(selection){selection=false;chosen.clear();}
            tab=1;refresh();
        });
        LinearLayout actions=new LinearLayout(this);
        actions.setGravity(Gravity.CENTER_VERTICAL);
        actions.setPadding(dp(9),0,dp(9),0);
        root.addView(actions,new LinearLayout.LayoutParams(-1,dp(48)));
        cancelSelection=text("解除",13,getColor(R.color.text_primary));
        cancelSelection.setGravity(Gravity.CENTER);
        actions.addView(cancelSelection,new LinearLayout.LayoutParams(dp(62),dp(46)));
        cancelSelection.setOnClickListener(v->{selection=false;chosen.clear();refresh();});
        selectedActions=text("",13,getColor(R.color.text_secondary));
        selectedActions.setGravity(Gravity.CENTER);
        actions.addView(selectedActions,new LinearLayout.LayoutParams(0,dp(46),1));
        deleteSelected=text("削除",14,getColor(R.color.accent));
        deleteSelected.setGravity(Gravity.CENTER);
        actions.addView(deleteSelected,new LinearLayout.LayoutParams(dp(74),dp(46)));
        deleteSelected.setOnClickListener(v->confirmSelected());
        actions.setTag("selectionActions");
        selectionActions=actions;

        list=new ListView(this);
        list.setDivider(new ColorDrawable(getColor(R.color.divider)));
        list.setDividerHeight(dp(1));
        // ListView selector remains latched to the previously focused row on Android.
        // Only the actual checked items may have a persistent tint.
        list.setSelector(new ColorDrawable(android.graphics.Color.TRANSPARENT));
        list.setFastScrollEnabled(true);
        list.setAdapter(adapter);
        root.addView(list,new LinearLayout.LayoutParams(-1,0,1));
        list.setOnItemClickListener((parent,view,position,id)->{
            if(position<0||position>=rows.size())return;
            SearchHistory.Entry entry=rows.get(position);
            if(selection){toggleSelection(entry.url);return;}
            String url=entry.url;
            try{CheckerQuery.parseUrl(url);}
            catch(Exception invalid){
                android.widget.Toast.makeText(this,"履歴のURLが無効です",android.widget.Toast.LENGTH_SHORT).show();
                return;
            }
            startActivity(new Intent(this,ResultActivity.class).setData(Uri.parse(url)));
        });
        list.setOnItemLongClickListener((parent,view,position,id)->{
            if(position<0||position>=rows.size())return true;
            SearchHistory.Entry entry=rows.get(position);
            if(selection){toggleSelection(entry.url);return true;}
            pendingConfirmationUrl=entry.url;
            adapter.notifyDataSetChanged();
            Runnable clearPending=()->{
                pendingConfirmationUrl=null;
                if(!isFinishing()&&!isDestroyed())adapter.notifyDataSetChanged();
            };
            if(tab==0)ThemedMenu.confirm(this,"履歴の削除",
                "この履歴を削除しますか？\nお気に入り登録は維持されます。","削除",
                ()->{SearchHistory.deleteRecent(this,entry.url);refresh();},
                clearPending);
            else ThemedMenu.confirm(this,"お気に入りの解除",
                "お気に入り登録を解除しますか？","解除",()->{
                    if(SearchHistory.isFavorite(this,entry.url))
                        SearchHistory.toggleFavorite(this,entry);
                    refresh();
                },clearPending);
            return true;
        });
        refresh();
    }
    private void toggleSelection(String url){
        if(!chosen.add(url))chosen.remove(url);
        refresh();
    }
    private void confirmSelected(){
        if(chosen.isEmpty())return;
        ThemedMenu.confirm(this,"選択した履歴の削除",
            "選択した"+chosen.size()+"件を削除しますか？\nお気に入りは維持されます。","削除",()->{
                SearchHistory.deleteRecents(this,new HashSet<>(chosen));
                chosen.clear();selection=false;refresh();
            });
    }
    private void confirmClear(){
        ThemedMenu.confirm(this,"履歴の全削除",
            "通常履歴をすべて削除しますか？\nお気に入りは残ります。","全削除",()->{
                SearchHistory.clearRecents(this);chosen.clear();selection=false;refresh();
            });
    }
    @Override protected void onResume(){super.onResume();if(list!=null)refresh();}
    /** Android 13+ / 16 back gestures no longer reliably call onBackPressed(). */
    private void navigateBack(){
        if(selection){
            selection=false;
            chosen.clear();
            refresh();
        }else finish();
    }
    private void refresh(){
        if(list==null)return;
        List<SearchHistory.Entry> recents=SearchHistory.recents(this);
        List<SearchHistory.Entry> favorites=SearchHistory.favorites(this);
        rows.clear();
        rows.addAll(SearchHistory.sorted(tab==0?recents:favorites,sort));
        favoriteUrls.clear();
        for(SearchHistory.Entry favorite:favorites)favoriteUrls.add(favorite.url);
        recentTab.setText(getString(R.string.history_recent_count,recents.size()));
        favoriteTab.setText(getString(R.string.history_favorite_count,favorites.size()));
        recentTab.setTextColor(getColor(tab==0?R.color.accent:R.color.text_secondary));
        favoriteTab.setTextColor(getColor(tab==1?R.color.accent:R.color.text_secondary));
        sortLabel.setText(getString(R.string.history_sort_label,sorts[sort]));
        sortLabel.setVisibility(selection?View.GONE:View.VISIBLE);
        menuButton.setVisibility(selection?View.GONE:View.VISIBLE);
        toolbarTitle.setText(selection?"履歴を選択":"検索履歴");
        selectionActions.setVisibility(selection?View.VISIBLE:View.GONE);
        selectedActions.setText(getString(R.string.history_selected_count,chosen.size()));
        deleteSelected.setEnabled(!chosen.isEmpty());
        deleteSelected.setAlpha(chosen.isEmpty()?0.45f:1f);
        adapter.notifyDataSetChanged();
    }
    // API 26–32 hardware and gesture back fallback. API 33+ uses the
    // platform callback registered in onCreate, so this is deliberately kept.
    @SuppressWarnings("deprecation")
    @android.annotation.SuppressLint("GestureBackNavigation")
    @Override public void onBackPressed(){
        navigateBack();
    }
    private static final class HistoryRow {
        final LinearLayout root;
        final TextView number,title,detail,star;
        HistoryRow(LinearLayout root,TextView number,TextView title,TextView detail,TextView star){
            this.root=root;this.number=number;this.title=title;
            this.detail=detail;this.star=star;
        }
    }
    private final class EntriesAdapter extends BaseAdapter {
        @Override public int getCount(){return rows.size();}
        @Override public Object getItem(int i){return rows.get(i);}
        @Override public long getItemId(int i){return i;}
        @Override public View getView(int position,View convert,ViewGroup parent){
            SearchHistory.Entry entry=rows.get(position);
            HistoryRow holder;
            if(convert instanceof LinearLayout && convert.getTag() instanceof HistoryRow){
                holder=(HistoryRow)convert.getTag();
            }else{
                LinearLayout row=new LinearLayout(HistoryActivity.this);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(dp(10),dp(6),dp(6),dp(6));
                row.setMinimumHeight(dp(64));
                TextView number=text("",12,getColor(R.color.text_secondary));
                number.setGravity(Gravity.CENTER);
                row.addView(number,new LinearLayout.LayoutParams(dp(34),dp(48)));
                LinearLayout labels=new LinearLayout(HistoryActivity.this);
                labels.setOrientation(LinearLayout.VERTICAL);
                labels.setGravity(Gravity.CENTER_VERTICAL);
                TextView title=text("",14,getColor(R.color.text_primary));
                title.setSingleLine(true);
                title.setEllipsize(android.text.TextUtils.TruncateAt.END);
                labels.addView(title);
                TextView detail=text("",11,getColor(R.color.text_secondary));
                detail.setSingleLine(true);
                detail.setEllipsize(android.text.TextUtils.TruncateAt.END);
                labels.addView(detail);
                row.addView(labels,new LinearLayout.LayoutParams(0,dp(52),1));
                TextView star=text("",23,getColor(R.color.text_secondary));
                star.setGravity(Gravity.CENTER);
                row.addView(star,new LinearLayout.LayoutParams(dp(48),dp(48)));
                holder=new HistoryRow(row,number,title,detail,star);
                row.setTag(holder);
            }
            boolean checked=selection && chosen.contains(entry.url);
            if(checked || entry.url.equals(pendingConfirmationUrl))
                holder.root.setBackgroundColor(getColor(R.color.panel_pressed));
            else
                holder.root.setBackground(new android.graphics.drawable.RippleDrawable(
                    android.content.res.ColorStateList.valueOf(getColor(R.color.panel_pressed)),
                    new ColorDrawable(android.graphics.Color.TRANSPARENT),null));
            holder.number.setText(selection?(checked?"☑":"☐"):Integer.toString(position+1));
            holder.number.setTextSize(selection?21:12);
            holder.number.setTextColor(getColor(checked?R.color.accent:R.color.text_secondary));
            holder.title.setText(entry.title());
            holder.detail.setText(entry.detail());
            holder.star.setVisibility(selection?View.GONE:View.VISIBLE);
            if(!selection){
                boolean starred=favoriteUrls.contains(entry.url);
                holder.star.setText(starred?"★":"☆");
                holder.star.setTextColor(getColor(starred?R.color.accent:R.color.text_secondary));
                holder.star.setContentDescription(starred?"お気に入りを解除":"お気に入りに登録");
                holder.star.setOnClickListener(v->{
                    SearchHistory.toggleFavorite(HistoryActivity.this,entry);
                    refresh();
                });
            }else holder.star.setOnClickListener(null);
            return holder.root;
        }
    }
}
