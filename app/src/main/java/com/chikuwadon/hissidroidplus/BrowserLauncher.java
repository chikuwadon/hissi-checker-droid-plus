package com.chikuwadon.hissidroidplus;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import java.util.List;

/** Open pages in an external browser, not the application's own URL handler. */
public final class BrowserLauncher {
    private BrowserLauncher(){}
    public static boolean open(Activity activity,String url){
        if(url==null||url.isEmpty())return false;
        Uri uri=Uri.parse(url);
        if(!"https".equalsIgnoreCase(uri.getScheme())&&
                !"http".equalsIgnoreCase(uri.getScheme()))return false;
        Intent base=new Intent(Intent.ACTION_VIEW,uri);
        base.addCategory(Intent.CATEGORY_BROWSABLE);
        PackageManager pm=activity.getPackageManager();
        // Prefer the user's default browser when it isn't our own activity.
        ResolveInfo preferred=pm.resolveActivity(base,PackageManager.MATCH_DEFAULT_ONLY);
        if(preferred!=null&&preferred.activityInfo!=null){
            String pkg=preferred.activityInfo.packageName;
            if(!activity.getPackageName().equals(pkg)&&
                    !"android".equals(pkg)&&!"com.android.internal.app".equals(pkg)){
                Intent explicit=new Intent(base).setClassName(pkg,preferred.activityInfo.name);
                try{activity.startActivity(explicit);return true;}
                catch(Exception ignored){}
            }
        }
        List<ResolveInfo> choices=pm.queryIntentActivities(base,PackageManager.MATCH_DEFAULT_ONLY);
        String[] common={"com.android.chrome","com.sec.android.app.sbrowser",
                "org.mozilla.firefox","com.brave.browser","com.microsoft.emmx",
                "com.duckduckgo.mobile.android","com.opera.browser"};
        for(String candidate:common)for(ResolveInfo item:choices){
            if(item.activityInfo==null||!candidate.equals(item.activityInfo.packageName))continue;
            Intent explicit=new Intent(base).setClassName(candidate,item.activityInfo.name);
            try{activity.startActivity(explicit);return true;}catch(Exception ignored){}
        }
        // Use any other external browsable handler, never ourselves.
        for(ResolveInfo item:choices){
            if(item.activityInfo==null||
                    activity.getPackageName().equals(item.activityInfo.packageName))continue;
            Intent explicit=new Intent(base).setClassName(
                    item.activityInfo.packageName,item.activityInfo.name);
            try{activity.startActivity(explicit);return true;}catch(Exception ignored){}
        }
        android.widget.Toast.makeText(activity,"外部ブラウザが見つかりません",
                android.widget.Toast.LENGTH_SHORT).show();
        return false;
    }
}
