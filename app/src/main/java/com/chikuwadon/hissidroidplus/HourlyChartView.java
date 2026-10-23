package com.chikuwadon.hissidroidplus;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Lightweight local histogram: 6-hour guide lines and tappable hourly values. */
public final class HourlyChartView extends View {
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final int[] hours=new int[24];
    private int selected=-1;
    private static final Pattern TIME=Pattern.compile("(?:\\d{4}/\\d{1,2}/\\d{1,2}\\([^)]*\\)\\s+)?([01]\\d|2[0-3]):[0-5]\\d(?::[0-5]\\d)?");

    public HourlyChartView(Context c){this(c,null);}
    public HourlyChartView(Context c,AttributeSet attrs){super(c,attrs);init();}
    public HourlyChartView(Context c,AttributeSet attrs,int defStyleAttr){super(c,attrs,defStyleAttr);init();}
    private void init(){setMinimumHeight(dp(74));setClickable(true);setContentDescription("時間帯別投稿数グラフ。時間をタップすると投稿数を確認できます");}
    private int dp(int amount){return Math.round(amount*getResources().getDisplayMetrics().density);}
    public int setPosts(List<CheckerResult.Post> posts){
        java.util.Arrays.fill(hours,0);int total=0;
        for(CheckerResult.Post p:posts){
            Matcher match=TIME.matcher(p.meta);
            if(match.find()){hours[Integer.parseInt(match.group(1))]++;total++;}
        }
        invalidate();return total;
    }
    public void setCounts(int[] count){
        java.util.Arrays.fill(hours,0);
        if(count!=null&&count.length==24)System.arraycopy(count,0,hours,0,24);
        invalidate();
    }
    @Override protected void onMeasure(int w,int h){
        super.onMeasure(w,MeasureSpec.makeMeasureSpec(dp(74),MeasureSpec.EXACTLY));
    }
    @Override public boolean onTouchEvent(MotionEvent event){
        if(event.getActionMasked()==MotionEvent.ACTION_DOWN)return true;
        if(event.getActionMasked()==MotionEvent.ACTION_UP){
            selected=Math.min(23,Math.max(0,(int)(24*event.getX()/Math.max(1,getWidth()))));
            setContentDescription(String.format(java.util.Locale.JAPAN,"%d時台: %d件",selected,hours[selected]));
            invalidate();performClick();return true;
        }
        return super.onTouchEvent(event);
    }
    @Override public boolean performClick(){super.performClick();return true;}
    @Override protected void onDraw(Canvas canvas){
        super.onDraw(canvas);
        int width=getWidth(),height=getHeight();
        if(width<=0)return;
        int top=dp(17),bottom=height-dp(18),max=1;
        for(int count:hours)max=Math.max(max,count);
        float unit=width/24f;
        int guide=getResources().getColor(R.color.text_secondary,null);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(1f,getResources().getDisplayMetrics().density*0.7f));
        paint.setColor(Color.argb(60,Color.red(guide),Color.green(guide),Color.blue(guide)));
        for(int hour=0;hour<=24;hour+=6) {
            float x=Math.min(width-1,hour*unit);
            canvas.drawLine(x,top,x,bottom,paint);
        }
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(getResources().getColor(R.color.accent,null));
        for(int hour=0;hour<24;hour++){
            float bar=(bottom-top)*hours[hour]/(float)max;
            if(hours[hour]>0){
                float left=hour*unit+dp(1);
                float right=Math.max(left+1, (hour+1)*unit-dp(1));
                canvas.drawRoundRect(left,bottom-bar,right,bottom,dp(1),dp(1),paint);
            }
        }
        paint.setColor(guide);
        paint.setTextSize(dp(10));
        canvas.drawText("0",dp(2),height-dp(3),paint);
        canvas.drawText("6",6*unit,height-dp(3),paint);
        canvas.drawText("12",12*unit,height-dp(3),paint);
        canvas.drawText("18",18*unit,height-dp(3),paint);
        canvas.drawText("23",width-dp(16),height-dp(3),paint);
        if(selected>=0){
            paint.setColor(getResources().getColor(R.color.text_primary,null));
            paint.setTextSize(dp(12));
            String info=selected+"時台: "+hours[selected]+"件";
            float x=selected*unit;
            canvas.drawText(info,Math.max(dp(2),Math.min(x,width-paint.measureText(info)-dp(2))),dp(12),paint);
        }
    }
}
