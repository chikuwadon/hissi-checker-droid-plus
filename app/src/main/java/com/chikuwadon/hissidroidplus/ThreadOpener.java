package com.chikuwadon.hissidroidplus;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.widget.Toast;

/**
 * Open known Edge threads in ChMate without relying on Android's
 * auto-verification of the eddibb.cc deep link on Android 12+.
 */
public final class ThreadOpener {
    private static final String MATE="jp.co.airfront.android.a2chMate";
    private ThreadOpener(){}

    /** Return alternate HTTP form, used only for ChMate intent dispatch. */
    static String mateHttpUrl(String normalized){
        try{
            java.net.URI uri=java.net.URI.create(normalized);
            if(!"https".equalsIgnoreCase(uri.getScheme()) ||
                    !"bbs.eddibb.cc".equalsIgnoreCase(uri.getHost()) ||
                    uri.getPort()!=-1 || uri.getRawUserInfo()!=null)return "";
            if(!uri.getRawPath().matches("/test/read\\.cgi/[A-Za-z0-9_]{1,60}/\\d{10,20}/(?:\\d{1,6})?"))return "";
            return "http://"+normalized.substring("https://".length());
        }catch(Exception ignored){return "";}
    }
    private static boolean launch(Activity activity,Intent intent){
        try {activity.startActivity(intent);return true;}
        catch(ActivityNotFoundException|SecurityException error){return false;}
    }
    public static void open(Activity activity,String raw,boolean edge){
        if(raw==null||raw.isEmpty())return;
        String normalized=edge?EdgeThreadLink.original(raw):raw;
        if(!edge){
            if(!launch(activity,create(normalized,null))){
                BrowserLauncher.open(activity,normalized);
            }
            return;
        }
        // ChMate often uses http://bbs.eddibb.cc as its registered external
        // board even when an archive page advertises an HTTPS link.
        String http=mateHttpUrl(normalized);
        if(!http.isEmpty()&&launch(activity,create(http,MATE)))return;
        if(launch(activity,create(normalized,MATE)))return;

        // Do not silently redirect to a browser when ChMate doesn't register
        // this URL. Let Android offer capable apps and avoid opening our own app.
        String use=http.isEmpty()?normalized:http;
        Intent chooser=Intent.createChooser(create(use,null),"スレッドを開くアプリ");
        if(launch(activity,chooser))return;
        Toast.makeText(activity,"スレッドを開けませんでした",Toast.LENGTH_LONG).show();
    }
    private static Intent create(String url,String pkg){
        Intent intent=new Intent(Intent.ACTION_VIEW,Uri.parse(url));
        intent.addCategory(Intent.CATEGORY_BROWSABLE);
        if(pkg!=null)intent.setPackage(pkg);
        return intent;
    }
}
