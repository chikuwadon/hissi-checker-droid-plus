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
        final PopupWindow popup=new PopupWindow(panel,panelWidth(a,304),
                ViewGroup.LayoutParams.WRAP_CONTENT,true);
        popup.setOutsideTouchable(true);
        popup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        popup.setElevation(dp(a,10));
        for(int i=0;i<items.length;i++){
            final int index=i;
            TextView row=text(a,items[i],14,a.getColor(R.color.text_primary));
            row.setMinHeight(dp(a,48));
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
        close.setMinHeight(dp(a,46));
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
            row.setMinHeight(dp(a,48));
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
    public static void showAbout(Activity a){
        if(a.isFinishing()||a.isDestroyed())return;
        String version="不明";
        try {version=a.getPackageManager().getPackageInfo(a.getPackageName(),0).versionName;}
        catch(Exception ignored){}
        final LinearLayout panel=column(a,12);
        panel.addView(title(a,"必死チェッカーdroid +"));
        TextView versionLine=text(a,"バージョン  "+version,14,a.getColor(R.color.text_secondary));
        versionLine.setPadding(dp(a,8),dp(a,6),dp(a,8),dp(a,12));
        panel.addView(versionLine);
        TextView github=text(a,"GitHub  ↗",14,a.getColor(R.color.accent));
        github.setPadding(dp(a,8),dp(a,8),dp(a,8),dp(a,8));
        github.setMinHeight(dp(a,46));applyTouch(a,github);
        panel.addView(github);
        final AlertDialog[] holder={null};
        github.setOnClickListener(v->{
            Intent view=new Intent(Intent.ACTION_VIEW,Uri.parse(
                    "https://github.com/chikuwadon/hissi-checker-droid-plus"));
            view.addCategory(Intent.CATEGORY_BROWSABLE);
            try { a.startActivity(view); }catch(Exception ignored){}
        });
        addClose(a,panel,holder);
        holder[0]=dialog(a,panel);
    }
}
