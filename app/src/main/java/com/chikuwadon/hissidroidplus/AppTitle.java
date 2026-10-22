package com.chikuwadon.hissidroidplus;
import android.graphics.Color;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.widget.TextView;
public final class AppTitle {
    private AppTitle(){}
    public static void apply(TextView view){
        String title="必死チェッカーdroid +";
        SpannableString styled=new SpannableString(title);
        styled.setSpan(new ForegroundColorSpan(Color.rgb(0,184,175)),title.indexOf("droid"),title.length(),Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        view.setText(styled);
    }
}
