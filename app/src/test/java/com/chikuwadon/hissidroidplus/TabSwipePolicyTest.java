package com.chikuwadon.hissidroidplus;

import org.junit.Test;
import static org.junit.Assert.*;

public final class TabSwipePolicyTest {
    private static int select(int current,float dx,float dy,float vx){
        return TabSwipePolicy.destination(current,3,dx,dy,vx,44f,18f,520f);
    }
    @Test public void deliberateSwipesSwitchOneTab(){
        assertEquals(1,select(0,-50f,2f,-150f));
        assertEquals(2,select(1,-90f,0f,-500f));
        assertEquals(0,select(1,75f,4f,200f));
    }
    @Test public void fastShortFlingSwitchesButTapDoesNot(){
        assertEquals(1,select(0,-22f,3f,-750f));
        assertEquals(0,select(0,-12f,1f,-1400f));
        assertEquals(0,select(0,-28f,1f,-130f));
        assertEquals(0,select(0,-24f,1f,800f));
    }
    @Test public void verticalScrollingAndTabBoundsAreProtected(){
        assertEquals(1,select(1,110f,120f,1200f));
        assertEquals(0,select(0,100f,0f,1400f));
        assertEquals(2,select(2,-100f,0f,-1400f));
    }
}
