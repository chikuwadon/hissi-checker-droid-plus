package com.chikuwadon.hissidroidplus;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.net.Uri;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;

/**
 * Shared UI surface for app menus. Overflow menus attach to the toolbar's
 * action icon; selection and About panels are centered and theme-aware.
 * Do not use platform default gray list dialogs for application menus.
 */
public final class ThemedMenu {
    public interface Selection { void onSelected(int index); }
    private ThemedMenu(){}

    private static int dp(Activity a,int amount){
        return Math.round(a.getResources().getDisplayMetrics().density*amount);
    }
    private static GradientDrawable panel(Activity a,int radius){
        GradientDrawable bg=new GradientDrawable();
        bg.setColor(a.getColor(R.color.panel));
        bg.setCornerRadius(dp(a,radius));
        bg.setStroke(dp(a,1),a.getColor(R.color.border));
        return bg;
    }
    private static TextView text(Activity a,String value,int size,int color){
        TextView result=new TextView(a);
        result.setText(value);result.setTextSize(size);result.setTextColor(color);
        result.setGravity(Gravity.CENTER_VERTICAL);
        result.setFontFeatureSettings("kern");
        return result;
    }
    private static void applyTouch(Activity a,View view){
        view.setBackground(new RippleDrawable(
                ColorStateList.valueOf(a.getColor(R.color.panel_pressed)),
                new ColorDrawable(Color.TRANSPARENT),null));
    }
    private static int panelWidth(Activity a,int preferred){
        return Math.min(a.getResources().getDisplayMetrics().widthPixels-dp(a,32),dp(a,preferred));
    }
    private static LinearLayout column(Activity a,int padding){
        LinearLayout panel=new LinearLayout(a);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(a,padding),dp(a,padding),dp(a,padding),dp(a,padding));
        panel.setBackground(panel(a,15));
        return panel;
    }

    /** Compact anchored toolbar popup, not a modal AlertDialog. */
    public static void showOverflow(Activity a,View anchor,String[] items,Selection selected){
        if(a.isFinishing()||a.isDestroyed())return;
        LinearLayout panel=column(a,5);
        // Fit the panel to the labels instead of reserving a wide fixed 304dp box.
        TextView measure=text(a,"",14,a.getColor(R.color.text_primary));
        float labelWidth=0;
        for(String item:items)labelWidth=Math.max(labelWidth,measure.getPaint().measureText(item));
        int desired=(int)Math.ceil(labelWidth)+dp(a,48);
        int popupWidth=Math.min(panelWidth(a,304),Math.max(dp(a,156),desired));
        final PopupWindow popup=new PopupWindow(panel,popupWidth,
                ViewGroup.LayoutParams.WRAP_CONTENT,true);
        popup.setOutsideTouchable(true);
        popup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        popup.setElevation(dp(a,10));
        for(int i=0;i<items.length;i++){
            final int index=i;
            TextView row=text(a,items[i],14,a.getColor(R.color.text_primary));
            row.setMinimumHeight(dp(a,48));
            row.setPadding(dp(a,15),dp(a,10),dp(a,12),dp(a,10));
            row.setSingleLine(false);
            applyTouch(a,row);
            panel.addView(row,new LinearLayout.LayoutParams(-1,-2));
            row.setOnClickListener(v->{popup.dismiss();selected.onSelected(index);});
        }
        popup.showAsDropDown(anchor,0,-dp(a,2),Gravity.END);
    }

    private static AlertDialog dialog(Activity a,LinearLayout panel){
        AlertDialog d=new AlertDialog.Builder(a).create();
        d.setView(panel);
        d.show();
        Window w=d.getWindow();
        if(w!=null){
            w.setBackgroundDrawableResource(android.R.color.transparent);
            w.setGravity(Gravity.CENTER);
            w.setLayout(panelWidth(a,420),WindowManager.LayoutParams.WRAP_CONTENT);
        }
        return d;
    }
    private static TextView title(Activity a,String name){
        TextView heading=text(a,name,17,a.getColor(R.color.text_primary));
        heading.setPadding(dp(a,8),dp(a,4),dp(a,8),dp(a,11));
        return heading;
    }
    private static void addClose(Activity a,LinearLayout panel,AlertDialog[] holder){
        TextView close=text(a,"閉じる",14,a.getColor(R.color.accent));
        close.setGravity(Gravity.END|Gravity.CENTER_VERTICAL);
        close.setPadding(dp(a,10),dp(a,3),dp(a,12),dp(a,3));
        close.setMinimumHeight(dp(a,46));
        applyTouch(a,close);
        panel.addView(close);
        close.setOnClickListener(v->{if(holder[0]!=null)holder[0].dismiss();});
    }
    public static void choose(Activity a,String heading,String[] labels,int selectedIndex,Selection onChoice){
        if(a.isFinishing()||a.isDestroyed())return;
        LinearLayout panel=column(a,10);
        panel.addView(title(a,heading));
        final AlertDialog[] holder={null};
        for(int i=0;i<labels.length;i++){
            final int index=i;
            LinearLayout row=new LinearLayout(a);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setMinimumHeight(dp(a,48));
            applyTouch(a,row);
            TextView option=text(a,labels[i],14,a.getColor(R.color.text_primary));
            option.setPadding(dp(a,10),dp(a,8),0,dp(a,8));
            row.addView(option,new LinearLayout.LayoutParams(0,-2,1));
            TextView check=text(a,i==selectedIndex?"✓":"",19,a.getColor(R.color.accent));
            check.setGravity(Gravity.CENTER);
            row.addView(check,new LinearLayout.LayoutParams(dp(a,42),dp(a,42)));
            panel.addView(row);
            row.setOnClickListener(v->{
                holder[0].dismiss();
                onChoice.onSelected(index);
            });
        }
        addClose(a,panel,holder);
        holder[0]=dialog(a,panel);
    }
    private static void aboutLine(Activity a,LinearLayout panel,String line,int color){
        TextView row=text(a,line,13,color);
        row.setPadding(dp(a,8),dp(a,2),dp(a,8),dp(a,5));
        panel.addView(row);
    }
    private static void aboutHeading(Activity a,LinearLayout panel,String heading){
        TextView row=text(a,heading,13,a.getColor(R.color.text_primary));
        row.setTypeface(android.graphics.Typeface.DEFAULT,android.graphics.Typeface.BOLD);
        row.setPadding(dp(a,8),dp(a,10),dp(a,8),dp(a,4));
        panel.addView(row);
    }
    private static void aboutLink(Activity a,LinearLayout panel,String label,String uri){
        TextView link=text(a,label+"  ↗",13,a.getColor(R.color.accent));
        link.setPadding(dp(a,8),dp(a,7),dp(a,8),dp(a,7));
        link.setMinimumHeight(dp(a,42));
        applyTouch(a,link);
        panel.addView(link);
        link.setOnClickListener(v->{
            try {
                Intent view=new Intent(Intent.ACTION_VIEW,Uri.parse(uri));
                view.addCategory(Intent.CATEGORY_BROWSABLE);
                a.startActivity(view);
            } catch(Exception ignored){}
        });
    }
    public static void showAbout(Activity a){
        if(a.isFinishing()||a.isDestroyed())return;
        String version="不明";
        try {version=a.getPackageManager().getPackageInfo(a.getPackageName(),0).versionName;}
        catch(Exception ignored){}
        LinearLayout panel=column(a,12);
        panel.addView(title(a,"必死チェッカーdroid +"));
        TextView caption=text(a,"Hissi Checker Droid Plus  /  v"+version,13,a.getColor(R.color.text_secondary));
        caption.setPadding(dp(a,8),0,dp(a,8),dp(a,9));
        panel.addView(caption);

        android.widget.ScrollView scroll=new android.widget.ScrollView(a);
        scroll.setFillViewport(false);
        scroll.setVerticalScrollBarEnabled(true);
        int maxBodyHeight=Math.min(dp(a,420),
                (int)(a.getResources().getDisplayMetrics().heightPixels*0.57f));
        panel.addView(scroll,new LinearLayout.LayoutParams(-1,maxBodyHeight));
        LinearLayout content=new LinearLayout(a);
        content.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(content);
        int secondary=a.getColor(R.color.text_secondary);
        aboutHeading(a,content,"アプリについて");
        aboutLine(a,content,"5chのID別投稿を、レス・スレ・名前ごとに確認できるAndroidアプリです。オリジナルの必死チェッカーdroid v1.5を参考に独立再構築しています。",secondary);
        aboutHeading(a,content,"主な機能");
        aboutLine(a,content,"ID検索、板選択、レス・スレ・名前の一覧、投稿のコピー、ChMate連携、時間帯別の投稿グラフ。エッヂのID検索と書き込み分析にも対応しています。",secondary);
        aboutHeading(a,content,"アプリ情報");
        aboutLine(a,content,"バージョン："+version+"\n対応OS：Android 8.0以降\nアプリID："+a.getPackageName()+"\n開発：chikuwadon\n状態：開発・検証中",secondary);
        aboutHeading(a,content,"データと通信");
        aboutLine(a,content,"検索時は指定先サイトへアクセスします。板や検索条件の一部は端末内に保存されます。各サイトとの通信や取得結果は、接続先の仕様に依存します。",secondary);
        aboutHeading(a,content,"原作・オープンソース");
        aboutLine(a,content,"原作：必死チェッカーdroid v1.5\nHTML解析：jsoup（Apache License 2.0）\n原作および各サービス・素材の権利は、それぞれの権利者に帰属します。本アプリは非公式の独立開発版です。",secondary);
        aboutHeading(a,content,"プロジェクト");
        aboutLink(a,content,"GitHub リポジトリ","https://github.com/chikuwadon/hissi-checker-droid-plus");
        aboutLink(a,content,"変更履歴・ライセンス情報","https://github.com/chikuwadon/hissi-checker-droid-plus/blob/main/README.md");
        final AlertDialog[] holder={null};
        addClose(a,panel,holder);
        holder[0]=dialog(a,panel);
    }
}
