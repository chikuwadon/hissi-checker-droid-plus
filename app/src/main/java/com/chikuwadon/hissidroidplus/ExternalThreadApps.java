package com.chikuwadon.hissidroidplus;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Build;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Discover non-ChMate reader activities through URL filters without a package allowlist. */
final class ExternalThreadApps {
    private static final String MATE="jp.co.airfront.android.a2chMate";
    private ExternalThreadApps(){}

    static final class Handler {
        final String packageName,activityName,url;
        Handler(String pkg,String activity,String url){
            this.packageName=pkg;this.activityName=activity;this.url=url;
        }
    }
    private static Intent view(String url){
        return new Intent(Intent.ACTION_VIEW,Uri.parse(url))
                .addCategory(Intent.CATEGORY_BROWSABLE);
    }
    private static List<ResolveInfo> query(PackageManager pm,String url){
        try{
            List<ResolveInfo> found=pm.queryIntentActivities(view(url),PackageManager.MATCH_DEFAULT_ONLY);
            return found==null?Collections.emptyList():found;
        }catch(RuntimeException ignored){return Collections.emptyList();}
    }
    static List<String> urlVariants(String canonical){
        LinkedHashSet<String> urls=new LinkedHashSet<>();
        urls.add(canonical);
        urls.addAll(ThreadOpener.chMateUrls(canonical));
        return new ArrayList<>(urls);
    }
    static List<Handler> find(PackageManager pm,String canonical,String ownPackage){
        Set<String> genericWeb=new HashSet<>();
        for(String url:new String[]{"https://www.example.com/","http://www.example.com/"})
            for(ResolveInfo info:query(pm,url))
                if(info.activityInfo!=null)genericWeb.add(info.activityInfo.packageName);
        Map<String,Handler> readers=new LinkedHashMap<>();
        for(String url:urlVariants(canonical)){
            for(ResolveInfo info:query(pm,url)){
                ActivityInfo ai=info.activityInfo;
                if(ai==null||!ai.exported||ai.packageName==null||ai.name==null)continue;
                String pkg=ai.packageName;
                if(pkg.equals(ownPackage)||pkg.equals(MATE)||pkg.equals("android")||
                        pkg.equals("com.android.internal.app")||genericWeb.contains(pkg)||
                        BrowserLauncher.isKnownBrowserPackage(pkg))continue;
                readers.putIfAbsent(pkg,new Handler(pkg,ai.name,url));
            }
        }
        return new ArrayList<>(readers.values());
    }
    private static Intent explicit(Handler candidate){
        return view(candidate.url).setComponent(
                new ComponentName(candidate.packageName,candidate.activityName));
    }
    private static boolean launch(Activity activity,Intent intent){
        try{activity.startActivity(intent);return true;}
        catch(ActivityNotFoundException|SecurityException error){return false;}
    }
    static void open(Activity activity,String canonical){
        List<Handler> readers=find(activity.getPackageManager(),canonical,activity.getPackageName());
        if(readers.size()==1){
            if(launch(activity,explicit(readers.get(0))))return;
        }else if(readers.size()>1){
            Intent[] additional=new Intent[readers.size()-1];
            for(int i=1;i<readers.size();i++)additional[i-1]=explicit(readers.get(i));
            Intent chooser=Intent.createChooser(explicit(readers.get(0)),"スレッドを開くアプリ");
            chooser.putExtra(Intent.EXTRA_INITIAL_INTENTS,additional);
            if(launch(activity,chooser))return;
        }
        if(Build.VERSION.SDK_INT>=30){
            for(String url:urlVariants(canonical)){
                Intent nonBrowser=view(url).addFlags(Intent.FLAG_ACTIVITY_REQUIRE_NON_BROWSER);
                if(launch(activity,nonBrowser))return;
            }
        }
        BrowserLauncher.open(activity,canonical);
    }
}
