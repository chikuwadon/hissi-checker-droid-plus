package com.chikuwadon.hissidroidplus;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** A lightweight, locally derived 24-hour histogram, with no external chart dependency. */
public final class HourlyChartView extends View {
    private final Paint paint=new Paint(3);
    private final int[] hours=new int[24];
    private static final Pattern TIME=Pattern.compile("(?:\\d{4}/\\d{1,2}/\\d{1,2}\\([^)]*\\)\\s+)?([01]\\d|2[0-3]):[0-5]\\d(?::[0-5]\\d)?");
    public HourlyChartView(Context c){this(c,null);}
    /** Required by Android LayoutInflater when this view is referenced in XML. */
    public HourlyChartView(Context c, AttributeSet attrs){super(c,attrs);setMinimumHeight(dp(55));}
    public HourlyChartView(Context c, AttributeSet attrs, int defStyleAttr){super(c,attrs,defStyleAttr);setMinimumHeight(dp(55));}
    private int dp(int x){return Math.round(x*getResources().getDisplayMetrics().density);}
    public int setPosts(List<CheckerResult.Post> posts){
        java.util.Arrays.fill(hours,0);int total=0;
        for(CheckerResult.Post post:posts){
            Matcher m=TIME.matcher(post.meta);
            if(m.find()){hours[Integer.parseInt(m.group(1))]++;total++;}
        }
        invalidate();return total;
    }
    public void setCounts(int[] values){
        java.util.Arrays.fill(hours,0);
        if(values!=null&&values.length==24)System.arraycopy(values,0,hours,0,24);
        invalidate();
    }
    @Override protected void onMeasure(int w,int h){super.onMeasure(w,MeasureSpec.makeMeasureSpec(dp(64),MeasureSpec.EXACTLY));}
    @Override protected void onDraw(Canvas canvas){
        super.onDraw(canvas);int max=0;for(int count:hours)max=Math.max(max,count);
        int w=getWidth(),height=getHeight(),top=dp(7),bottom=height-dp(17);
        if(max==0)return;
        float unit=w/24f,barWidth=Math.max(1,unit-dp(2));
        paint.setColor(getResources().getColor(R.color.accent,null));
        for(int h=0;h<24;h++){
            float bar=(bottom-top)*hours[h]/(float)max;
            if(hours[h]>0)canvas.drawRoundRect(h*unit+dp(1),bottom-bar,h*unit+barWidth,bottom,dp(1),dp(1),paint);
        }
        paint.setColor(getResources().getColor(R.color.text_secondary,null));paint.setTextSize(dp(10));
        canvas.drawText("0",dp(2),height-dp(2),paint);
        canvas.drawText("6",6*unit,height-dp(2),paint);
        canvas.drawText("12",12*unit,height-dp(2),paint);
        canvas.drawText("18",18*unit,height-dp(2),paint);
        canvas.drawText("23",w-dp(15),height-dp(2),paint);
    }
}
