package com.chikuwadon.hissidroidplus;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.widget.Toast;
import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Strict ChMate handoff: supported VIEW handler or an explicit, honest fallback. */
public final class ThreadOpener {
    private static final String MATE="jp.co.airfront.android.a2chMate";
    private static final Pattern THREAD=Pattern.compile(
        "^/test/read\\.cgi/([A-Za-z0-9_]{1,60})/(\\d{10,20})/(\\d{1,6})?$");
    private ThreadOpener(){}

    static List<String> chMateUrls(String source){
        LinkedHashSet<String> urls=new LinkedHashSet<>();
        try{
            URI uri=URI.create(source);
            if(!"https".equalsIgnoreCase(uri.getScheme())||
                    !"bbs.eddibb.cc".equalsIgnoreCase(uri.getHost())||
                    uri.getRawUserInfo()!=null||uri.getPort()!=-1)return new ArrayList<>();
            Matcher m=THREAD.matcher(uri.getRawPath());
            if(!m.matches())return new ArrayList<>();
            String suffix=m.group(3)==null?"":"/"+m.group(3);
            String shortPath="/"+m.group(1)+"/"+m.group(2)+suffix;
            String canonical="/test/read.cgi/"+m.group(1)+"/"+m.group(2)+"/"+
                    (m.group(3)==null?"":m.group(3));
            urls.add("http://bbs.eddibb.cc"+shortPath);
            urls.add("https://bbs.eddibb.cc"+shortPath);
            urls.add("http://bbs.eddibb.cc"+canonical);
            urls.add("https://bbs.eddibb.cc"+canonical);
        }catch(Exception ignored){}
        return new ArrayList<>(urls);
    }

    static boolean isMateInstalled(PackageManager pm){
        try{
            pm.getPackageInfo(MATE,0);
            return true;
        }catch(PackageManager.NameNotFoundException noApp){return false;}
        catch(RuntimeException notVisible){return true;}
    }

    static Intent viewIntent(String url,String pkg){
        Intent intent=new Intent(Intent.ACTION_VIEW,Uri.parse(url));
        if(pkg!=null)intent.setPackage(pkg);
        else intent.addCategory(Intent.CATEGORY_BROWSABLE);
        return intent;
    }

    /**
     * A package name does not grant permission to launch arbitrary activities.
     * Query the actual exported ACTION_VIEW handler before attempting a launch;
     * never equate an Android chooser/browser launch with success in ChMate.
     */
    static boolean mateHandles(PackageManager pm,Intent intent){
        try{
            List<ResolveInfo> handlers=pm.queryIntentActivities(intent,PackageManager.MATCH_DEFAULT_ONLY);
            for(ResolveInfo handler:handlers){
                if(handler.activityInfo!=null&&
                        MATE.equals(handler.activityInfo.packageName)&&
                        handler.activityInfo.exported)return true;
            }
        }catch(RuntimeException ignored){}
        return false;
    }

    private static boolean launch(Activity activity,Intent intent){
        try{activity.startActivity(intent);return true;}
        catch(ActivityNotFoundException|SecurityException error){return false;}
    }

    private static boolean copyAndLaunchMate(Activity activity,String url){
        try{
            ClipboardManager clipboard=(ClipboardManager)
                    activity.getSystemService(Context.CLIPBOARD_SERVICE);
            if(clipboard==null)return false;
            clipboard.setPrimaryClip(ClipData.newPlainText("スレッドURL",url));
            Intent mate=activity.getPackageManager().getLaunchIntentForPackage(MATE);
            if(mate==null)return false;
            activity.startActivity(mate);
            Toast.makeText(activity,
                    "URLをコピーしました。ChMateの「URLを指定して開く」に貼り付けてください",
                    Toast.LENGTH_LONG).show();
            return true;
        }catch(RuntimeException error){return false;}
    }

    public static void open(Activity activity,String raw,boolean edge){
        if(raw==null||raw.isEmpty())return;
        String normalized=edge?EdgeThreadLink.original(raw):raw;
        if(!edge){
            if(!launch(activity,viewIntent(normalized,null)))
                BrowserLauncher.open(activity,normalized);
            return;
        }
        List<String> candidates=chMateUrls(normalized);
        if(candidates.isEmpty()){
            BrowserLauncher.open(activity,normalized);
            return;
        }
        PackageManager pm=activity.getPackageManager();
        if(!isMateInstalled(pm)){
            BrowserLauncher.open(activity,normalized);
            return;
        }
        // Check which exported intents ChMate really declares. It is normal
        // that no handler matches custom boards on Android 12+.
        for(String url:candidates){
            Intent intent=viewIntent(url,MATE);
            if(mateHandles(pm,intent)&&launch(activity,intent))return;
        }
        ThemedMenu.choose(activity,"スレッドを開く",
            new String[]{"URLをコピーしてChMateを開く","外部ブラウザで開く"},-1,which->{
                if(which==0&&!copyAndLaunchMate(activity,normalized)){
                    Toast.makeText(activity,"ChMateを起動できませんでした",Toast.LENGTH_LONG).show();
                }else if(which==1){
                    BrowserLauncher.open(activity,normalized);
                }
            });
    }
}
