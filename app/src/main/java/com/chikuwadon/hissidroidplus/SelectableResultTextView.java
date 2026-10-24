package com.chikuwadon.hissidroidplus;

import android.content.Context;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import android.widget.TextView;

/**
 * Keeps normal result-row taps responsive while retaining Android's native
 * text selection handles. Defers the platform long-click action slightly to
 * avoid treating a deliberate tap as a text-selection gesture.
 */
public final class SelectableResultTextView extends TextView {
    static final int MIN_LONG_PRESS_MS=700;
    private final int touchSlop;
    private boolean touchActive,moved,selectionStarted,dispatchNativeLongClick;
    private long downTime;
    private float downX,downY;
    private Runnable pendingLongClick;

    public SelectableResultTextView(Context context,AttributeSet attrs){
        super(context,attrs);
        touchSlop=ViewConfiguration.get(context).getScaledTouchSlop();
    }

    static int selectionDelayMs(int systemTimeout){
        return Math.max(MIN_LONG_PRESS_MS,systemTimeout+150);
    }

    private void clearPending(){
        if(pendingLongClick!=null){
            removeCallbacks(pendingLongClick);
            pendingLongClick=null;
        }
    }

    @Override public boolean performLongClick(){
        if(dispatchNativeLongClick || !touchActive)return super.performLongClick();
        long elapsed=SystemClock.uptimeMillis()-downTime;
        long wait=selectionDelayMs(ViewConfiguration.getLongPressTimeout())-elapsed;
        if(wait<=0){
            selectionStarted=true;
            return super.performLongClick();
        }
        if(pendingLongClick==null){
            final float x=downX,y=downY;
            pendingLongClick=()->{
                pendingLongClick=null;
                if(!touchActive || moved || !isAttachedToWindow())return;
                selectionStarted=true;
                dispatchNativeLongClick=true;
                try{
                    performLongClick(x,y);
                }finally{
                    dispatchNativeLongClick=false;
                }
            };
            postDelayed(pendingLongClick,wait);
        }
        // Consume Android's earlier long-click without selecting text yet.
        return true;
    }

    @Override public boolean onTouchEvent(MotionEvent event){
        int action=event.getActionMasked();
        if(action==MotionEvent.ACTION_DOWN){
            clearPending();
            touchActive=true;
            moved=false;
            selectionStarted=false;
            downTime=event.getEventTime();
            downX=event.getX();
            downY=event.getY();
        }else if(action==MotionEvent.ACTION_MOVE){
            if(touchActive && (Math.abs(event.getX()-downX)>touchSlop ||
                    Math.abs(event.getY()-downY)>touchSlop)){
                moved=true;
                clearPending();
            }
        }else if(action==MotionEvent.ACTION_UP){
            boolean ordinaryTap=touchActive && !moved && !selectionStarted;
            touchActive=false;
            clearPending();
            if(ordinaryTap){
                // Selectable TextViews may consume UP for focus instead of
                // calling the registered click listener. Dispatch exactly once.
                cancelLongPress();
                setPressed(false);
                return performClick() || true;
            }
        }else if(action==MotionEvent.ACTION_CANCEL){
            touchActive=false;
            clearPending();
        }
        return super.onTouchEvent(event);
    }

    @Override protected void onDetachedFromWindow(){
        touchActive=false;
        clearPending();
        super.onDetachedFromWindow();
    }
}
