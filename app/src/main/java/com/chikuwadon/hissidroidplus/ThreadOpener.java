package com.chikuwadon.hissidroidplus;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Android doesn't automatically associate ChMate with custom Edge domains.
 * Try validated alternate thread URLs explicitly, and do not confuse a
 * chooser launching Chrome with successful ChMate integration.
 */
public final class ThreadOpener {
    private static final String MATE="jp.co.airfront.android.a2chMate";
    private static final Pattern THREAD=Pattern.compile(
        "^/test/read\\.cgi/([A-Za-z0-9_]{1,60})/(\\d{10,20})/(\\d{1,6})?$");
    private ThreadOpener(){}

    static boolean isMateInstalled(PackageManager pm){
        try{
            pm.getPackageInfo(MATE,0);
            return true;
        }catch(PackageManager.NameNotFoundException notInstalled){
            return false;
        }catch(RuntimeException failure){
            // PackageManager can refuse visibility on some OEM systems.
            // The AndroidManifest package query is declared; if in doubt,
            // let the caller test targeted intents rather than assume absent.
            return true;
        }
    }

    static List<String> chMateUrls(String source){
        LinkedHashSet<String> urls=new LinkedHashSet<>();
        try{
            URI uri=URI.create(source);
            if(!"https".equalsIgnoreCase(uri.getScheme())||
                    !"bbs.eddibb.cc".equalsIgnoreCase(uri.getHost())||
                    uri.getRawUserInfo()!=null||uri.getPort()!=-1)return new ArrayList<>();
            Matcher match=THREAD.matcher(uri.getRawPath());
            if(!match.matches())return new ArrayList<>();
            String suffix=match.group(3)==null?"":"/"+match.group(3);
            String shortPath="/"+match.group(1)+"/"+match.group(2)+suffix;
            String canonical="/test/read.cgi/"+match.group(1)+"/"+match.group(2)+"/"+(match.group(3)==null?"":match.group(3));
            // ChMate users normally register the external board at
            // http://bbs.eddibb.cc/liveedge/; try that host/path first.
            urls.add("http://bbs.eddibb.cc"+shortPath);
            urls.add("https://bbs.eddibb.cc"+shortPath);
            urls.add("http://bbs.eddibb.cc"+canonical);
            urls.add("https://bbs.eddibb.cc"+canonical);
        }catch(Exception invalid){}
        return new ArrayList<>(urls);
    }

    private static boolean launch(Activity activity,Intent intent){
        try{
            activity.startActivity(intent);
            return true;
        }catch(ActivityNotFoundException|SecurityException error){
            return false;
        }
    }

    static Intent view(String url,String packageName){
        Intent intent=new Intent(Intent.ACTION_VIEW,Uri.parse(url));
        if(packageName==null){
            intent.addCategory(Intent.CATEGORY_BROWSABLE);
        }else{
            // A package-scoped intent doesn't need CATEGORY_BROWSABLE.
            // Requiring that category excludes ChMate activities whose
            // filters handle VIEW but are not marked as browser handlers.
            intent.setPackage(packageName);
        }
        return intent;
    }

    public static void open(Activity activity,String raw,boolean edge){
        if(raw==null||raw.isEmpty())return;
        String normalized=edge?EdgeThreadLink.original(raw):raw;
        if(!edge){
            if(!launch(activity,view(normalized,null)))
                BrowserLauncher.open(activity,normalized);
            return;
        }
        List<String> candidates=chMateUrls(normalized);
        if(candidates.isEmpty()){
            BrowserLauncher.open(activity,normalized);
            return;
        }
        // If ChMate is absent, open the user's browser DIRECTLY: never
        // present an intermediate "choose a thread reader" dialog.
        if(!isMateInstalled(activity.getPackageManager())){
            BrowserLauncher.open(activity,normalized);
            return;
        }
        for(String url:candidates){
            if(launch(activity,view(url,MATE)))return;
        }
        // All package-scoped intents were rejected. Do not silently launch
        // Chrome and report it as though the ChMate request succeeded.
        ThemedMenu.confirm(activity,"ChMateで開けませんでした",
            "ChMateがエッヂのURLを外部から受け付けませんでした。"+
            "ChMateの「URLを指定して開く」でエッヂ板を登録すると利用できる場合があります。"+
            "\n\n代わりにブラウザで開きますか？",
            "ブラウザで開く",()->BrowserLauncher.open(activity,normalized));
    }
}
