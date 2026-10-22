package com.chikuwadon.hissidroidplus;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;

/** Persistent cross-activity preferences, independent of the hosting task. */
public final class AppSettings {
    private static final String FILE="checker_settings";
    public static final String THEME="theme";
    public static final String LINK_MODE="external_link_mode";
    public static final String THEME_DARK="dark", THEME_LIGHT="light", THEME_SYSTEM="system";
    public static final String MODE_CONNECTED="connected", MODE_STANDALONE="standalone";
    private AppSettings(){}
    public static SharedPreferences prefs(Context context){
        return context.getSharedPreferences(FILE,Context.MODE_PRIVATE);
    }
    public static Context themeContext(Context base){
        String theme=prefs(base).getString(THEME,THEME_DARK);
        if(THEME_SYSTEM.equals(theme))return base;
        Configuration override=new Configuration(base.getResources().getConfiguration());
        int desired=THEME_LIGHT.equals(theme)?Configuration.UI_MODE_NIGHT_NO:Configuration.UI_MODE_NIGHT_YES;
        override.uiMode=(override.uiMode & ~Configuration.UI_MODE_NIGHT_MASK)|desired;
        return base.createConfigurationContext(override);
    }
    public static void configureBars(android.app.Activity activity){
        boolean light=(activity.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
            ==Configuration.UI_MODE_NIGHT_NO;
        int flags=light ? android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                | android.view.View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR : 0;
        activity.getWindow().getDecorView().setSystemUiVisibility(flags);
        activity.getWindow().setStatusBarColor(activity.getColor(R.color.surface));
        activity.getWindow().setNavigationBarColor(activity.getColor(R.color.surface));
    }
    public static boolean standalone(Context context){
        return MODE_STANDALONE.equals(prefs(context).getString(LINK_MODE,MODE_CONNECTED));
    }
}
