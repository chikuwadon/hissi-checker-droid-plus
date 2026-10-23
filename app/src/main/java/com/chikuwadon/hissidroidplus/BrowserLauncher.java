package com.chikuwadon.hissidroidplus;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Build;

/** Open in a browser using the system resolver, even if Android hides installed packages. */
public final class BrowserLauncher {
    private BrowserLauncher(){}
    public static boolean open(Activity activity,String url){
        if(url==null||url.isEmpty())return false;
        Uri uri=Uri.parse(url);
        if(!"https".equalsIgnoreCase(uri.getScheme())&&
                !"http".equalsIgnoreCase(uri.getScheme()))return false;
        Intent base=new Intent(Intent.ACTION_VIEW,uri)
                .addCategory(Intent.CATEGORY_BROWSABLE);
        PackageManager pm=activity.getPackageManager();
        // Respect the user's browser default when it is visible.
        ResolveInfo preferred=pm.resolveActivity(base,PackageManager.MATCH_DEFAULT_ONLY);
        if(preferred!=null&&preferred.activityInfo!=null){
            String pkg=preferred.activityInfo.packageName;
            if(!activity.getPackageName().equals(pkg)&&!"android".equals(pkg)){
                try{
                    activity.startActivity(new Intent(base).setClassName(
                        pkg,preferred.activityInfo.name));
                    return true;
                }catch(Exception ignored){}
            }
        }
        // Querying installed browsers may return zero on Android 11+ even
        // when several browsers are installed. The SYSTEM chooser can see them.
        Intent chooser=Intent.createChooser(base,"ブラウザで開く");
        if(Build.VERSION.SDK_INT>=29){
            chooser.putExtra(Intent.EXTRA_EXCLUDE_COMPONENTS,new ComponentName[]{
                new ComponentName(activity,ResultActivity.class),
                new ComponentName(activity,EdgeSearchActivity.class)
            });
        }
        try{activity.startActivity(chooser);return true;}
        catch(Exception ignored){}
        android.widget.Toast.makeText(activity,"ブラウザで開けませんでした",
                android.widget.Toast.LENGTH_SHORT).show();
        return false;
    }
}
