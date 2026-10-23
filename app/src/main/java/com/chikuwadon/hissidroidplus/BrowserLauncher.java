package com.chikuwadon.hissidroidplus;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Build;
import java.util.LinkedHashSet;
import java.util.List;

/** Route only browser actions to an actual browser; never rebound into ResultActivity. */
public final class BrowserLauncher {
    private BrowserLauncher(){}

    public static boolean open(Activity activity,String url){
        if(url==null||url.isEmpty())return false;
        Uri target=Uri.parse(url);
        if(!"https".equalsIgnoreCase(target.getScheme())&&
                !"http".equalsIgnoreCase(target.getScheme()))return false;

        Intent base=new Intent(Intent.ACTION_VIEW,target);
        base.addCategory(Intent.CATEGORY_BROWSABLE);
        PackageManager pm=activity.getPackageManager();

        // Resolve the *generic* HTTPS destination to learn the user's default
        // browser. Resolving a kyodemo/hissi deep link can select OUR OWN APP.
        Intent generic=new Intent(Intent.ACTION_VIEW,
                Uri.parse("https://www.example.com/"));
        generic.addCategory(Intent.CATEGORY_BROWSABLE);

        LinkedHashSet<String> packages=new LinkedHashSet<>();
        try{
            ResolveInfo preferred=pm.resolveActivity(generic,PackageManager.MATCH_DEFAULT_ONLY);
            if(preferred!=null&&preferred.activityInfo!=null)
                packages.add(preferred.activityInfo.packageName);
        }catch(Exception ignored){}

        // Android 11+ may hide apps from queryIntentActivities() even when
        // startActivity() with a package name would succeed. Try candidates
        // directly rather than treating an empty query as "not installed".
        String[] common={
            "com.android.chrome", "com.sec.android.app.sbrowser",
            "org.mozilla.firefox", "org.mozilla.firefox_beta",
            "com.brave.browser", "com.microsoft.emmx",
            "com.duckduckgo.mobile.android", "com.opera.browser",
            "com.opera.mini.native", "com.vivaldi.browser",
            "com.kiwibrowser.browser", "org.chromium.chrome",
            "com.google.android.apps.chrome"
        };
        for(String pkg:common)packages.add(pkg);
        try{
            List<ResolveInfo> found=pm.queryIntentActivities(generic,0);
            for(ResolveInfo item:found)
                if(item.activityInfo!=null)packages.add(item.activityInfo.packageName);
        }catch(Exception ignored){}

        for(String pkg:packages){
            if(pkg==null||pkg.isEmpty()||pkg.equals(activity.getPackageName())||
                    pkg.equals("android")||pkg.equals("com.android.internal.app"))continue;
            try{
                // setPackage finds the browser's real exported VIEW activity,
                // avoiding the invalid hand-selected component names.
                activity.startActivity(new Intent(base).setPackage(pkg));
                return true;
            }catch(Exception ignored){}
        }

        // Fallback: let Android resolve, while excluding our own deep-link activity.
        try{
            Intent chooser=Intent.createChooser(base,"ブラウザで開く");
            if(Build.VERSION.SDK_INT>=29)
                chooser.putExtra(Intent.EXTRA_EXCLUDE_COMPONENTS,
                        new ComponentName[]{new ComponentName(activity,ResultActivity.class)});
            activity.startActivity(chooser);
            return true;
        }catch(Exception ignored){}
        android.widget.Toast.makeText(activity,"ブラウザで開けませんでした",
                android.widget.Toast.LENGTH_SHORT).show();
        return false;
    }
}
