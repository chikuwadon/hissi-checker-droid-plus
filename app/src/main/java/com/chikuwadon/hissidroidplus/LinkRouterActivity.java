package com.chikuwadon.hissidroidplus;
import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

/** Routes incoming VIEW/SEND to either ChMate's task or an independent task. */
public final class LinkRouterActivity extends Activity {
    @Override public void onCreate(Bundle saved){
        super.onCreate(saved);
        Intent incoming=getIntent();
        Intent next=new Intent(this,ResultActivity.class);
        next.setAction(incoming.getAction());
        if(incoming.getData()!=null)next.setData(incoming.getData());
        if(incoming.getExtras()!=null)next.putExtras(incoming.getExtras());
        if(AppSettings.standalone(this)) {
            next.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_MULTIPLE_TASK);
        } else {
            next.setFlags(0);
        }
        startActivity(next);
        finish();
    }
}
