package com.chikuwadon.hissidroidplus;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.widget.Toast;
import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Direct ChMate handoff using exported Activities verified by ADB on 2026-10-23. */
public final class ThreadOpener {
    private static final String MATE="jp.co.airfront.android.a2chMate";
    // Default package when Haiagaru's "Change ChMate package name" patch is enabled.
    private static final String HAIAGARU="jp.co.airfront.android.a2chMate.haiagaru";
    // Verified by "adb shell am start -W -a android.intent.action.VIEW -n <component> -d <thread>".
    private static final String RES_LIST="jp.syoboi.a2chMate.activity.ResListActivity";
    private static final String OPEN_URL="app.morphe.extension.chmate.OpenUrlActivity";
    private static final Pattern THREAD=Pattern.compile(
        "^/test/read\\.cgi/([A-Za-z0-9_]{1,60})/(\\d{10,20})/(\\d{1,6})?$");
    private static final Pattern FIVECH_THREAD=Pattern.compile(
        "^/test/read\\.cgi/[A-Za-z0-9_]{1,60}/\\d{10,20}(?:/[A-Za-z0-9_,+\\-]{1,50})?/?$");
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
        return isReaderInstalled(pm,MATE);
    }

    private static boolean isReaderInstalled(PackageManager pm,String pkg){
        try{
            pm.getPackageInfo(pkg,0);
            return true;
        }catch(PackageManager.NameNotFoundException noApp){return false;}
        // Both known package names are declared in AndroidManifest <queries>.
        catch(RuntimeException notVisible){return false;}
    }

    /** Visible reader choices, in stable UI order. */
    static List<String> readerPackages(boolean mateInstalled,boolean haiagaruInstalled){
        List<String> packages=new ArrayList<>();
        if(mateInstalled)packages.add(MATE);
        if(haiagaruInstalled)packages.add(HAIAGARU);
        return packages;
    }

    private static String readerLabel(String pkg){
        return HAIAGARU.equals(pkg)?"Haiagaru":"ChMate";
    }

    static Intent viewIntent(String url,String pkg){
        Intent intent=new Intent(Intent.ACTION_VIEW,Uri.parse(url));
        if(pkg!=null)intent.setPackage(pkg);
        else intent.addCategory(Intent.CATEGORY_BROWSABLE);
        return intent;
    }

    /** Only target verified Edge thread URLs when choosing a ChMate Activity. */
    static String validatedThreadUrl(String raw){
        String canonical=EdgeThreadLink.original(raw);
        return chMateUrls(canonical).isEmpty()?"":canonical;
    }

    /** Accept only real 5ch thread URLs for package-specific handoff. */
    static String validated5chThreadUrl(String raw){
        if(raw==null)return "";
        try{
            URI uri=URI.create(raw);
            String scheme=uri.getScheme(),host=uri.getHost();
            if(scheme==null || (!scheme.equalsIgnoreCase("http") &&
                    !scheme.equalsIgnoreCase("https")))return "";
            if(host==null || uri.getRawUserInfo()!=null || uri.getPort()!=-1 ||
                    uri.getRawQuery()!=null || uri.getRawFragment()!=null)return "";
            String lower=host.toLowerCase(java.util.Locale.ROOT);
            boolean is5ch=lower.equals("5ch.net")||lower.endsWith(".5ch.net")||
                    lower.equals("5ch.io")||lower.endsWith(".5ch.io");
            if(!is5ch || !FIVECH_THREAD.matcher(uri.getRawPath()).matches())return "";
            return raw;
        }catch(IllegalArgumentException ignored){return "";}
    }

    /** Explicit component bypasses domain filters, unlike setPackage() alone. */
    private static Intent directThreadIntent(String url,String pkg,String activityClass){
        return new Intent(Intent.ACTION_VIEW,Uri.parse(url))
                .setClassName(pkg,activityClass);
    }

    private static boolean launch(Activity activity,Intent intent){
        try{activity.startActivity(intent);return true;}
        catch(ActivityNotFoundException|SecurityException error){return false;}
    }

    private static boolean copyAndLaunchReader(Activity activity,String url,String pkg){
        try{
            Intent launchIntent=activity.getPackageManager().getLaunchIntentForPackage(pkg);
            if(launchIntent==null)return false;
            ClipboardManager clipboard=(ClipboardManager)
                    activity.getSystemService(Context.CLIPBOARD_SERVICE);
            if(clipboard==null)return false;
            clipboard.setPrimaryClip(ClipData.newPlainText("スレッドURL",url));
            activity.startActivity(launchIntent);
            Toast.makeText(activity,
                    "URLをコピーしました。"+readerLabel(pkg)+"の「URLを指定して開く」に貼り付けてください",
                    Toast.LENGTH_LONG).show();
            return true;
        }catch(RuntimeException error){return false;}
    }

    private static void openInReader(Activity activity,String url,String pkg,boolean edge){
        // ChMate normally resolves 5ch URLs through its VIEW intent filters.
        if(!edge && launch(activity,viewIntent(url,pkg)))return;
        // Explicit Activity handoff is required for some Edge links.
        if(launch(activity,directThreadIntent(url,pkg,RES_LIST)))return;
        if(launch(activity,directThreadIntent(url,pkg,OPEN_URL)))return;
        String label=readerLabel(pkg);
        ThemedMenu.choose(activity,"スレッドを開く",
            new String[]{"URLをコピーして"+label+"を開く","他のアプリで開く","外部ブラウザで開く"},
            -1,which->{
                if(which==0 && !copyAndLaunchReader(activity,url,pkg))
                    Toast.makeText(activity,label+"を起動できませんでした",Toast.LENGTH_LONG).show();
                else if(which==1)ExternalThreadApps.open(activity,url);
                else if(which==2)BrowserLauncher.open(activity,url);
            });
    }

    private static String modeForPackage(String pkg){
        return HAIAGARU.equals(pkg)?AppSettings.THREAD_HAIAGARU:AppSettings.THREAD_CHMATE;
    }

    /** Route 5ch and Edge through the same persistent app preference. */
    private static void chooseReader(Activity activity,String url,boolean edge,List<String> readers){
        if(readers.isEmpty()){
            ExternalThreadApps.open(activity,url);
            return;
        }
        String[] labels=new String[readers.size()+2];
        for(int i=0;i<readers.size();i++)labels[i]=readerLabel(readers.get(i))+"で開く";
        labels[readers.size()]="他のアプリで開く";
        labels[readers.size()+1]="外部ブラウザで開く";
        ThemedMenu.chooseWithAlways(activity,"スレッドを開く",labels,(which,always)->{
            String selection=which<readers.size()?
                    modeForPackage(readers.get(which)):
                    which==readers.size()?AppSettings.THREAD_OTHER:AppSettings.THREAD_BROWSER;
            // An unchecked choice remains one-time, even after a saved preference became unavailable.
            AppSettings.setThreadOpenMode(activity,always?selection:AppSettings.THREAD_ASK);
            if(which<readers.size())openInReader(activity,url,readers.get(which),edge);
            else if(which==readers.size())ExternalThreadApps.open(activity,url);
            else BrowserLauncher.open(activity,url);
        });
    }

    public static void open(Activity activity,String raw,boolean edge){
        if(raw==null||raw.isEmpty())return;
        String normalized=edge?EdgeThreadLink.original(raw):raw;
        String accepted=edge?validatedThreadUrl(raw):validated5chThreadUrl(raw);
        if(accepted.isEmpty()){
            // Preserve implicit VIEW support for non-standard links.
            if(!edge && launch(activity,viewIntent(normalized,null)))return;
            BrowserLauncher.open(activity,normalized);
            return;
        }
        PackageManager pm=activity.getPackageManager();
        List<String> readers=readerPackages(
            isMateInstalled(pm),isReaderInstalled(pm,HAIAGARU));
        String mode=AppSettings.threadOpenMode(activity);
        if(AppSettings.THREAD_BROWSER.equals(mode)){
            BrowserLauncher.open(activity,accepted);
            return;
        }
        if(AppSettings.THREAD_OTHER.equals(mode)){
            ExternalThreadApps.open(activity,accepted);
            return;
        }
        if(AppSettings.THREAD_CHMATE.equals(mode)&&readers.contains(MATE)){
            openInReader(activity,accepted,MATE,edge);
            return;
        }
        if(AppSettings.THREAD_HAIAGARU.equals(mode)&&readers.contains(HAIAGARU)){
            openInReader(activity,accepted,HAIAGARU,edge);
            return;
        }
        if(readers.isEmpty()){
            ExternalThreadApps.open(activity,accepted);
        }else if(readers.size()==1 && AppSettings.THREAD_ASK.equals(mode)){
            openInReader(activity,accepted,readers.get(0),edge);
        }else{
            // Two apps installed, or a previously saved app is no longer available.
            chooseReader(activity,accepted,edge,readers);
        }
    }
}
