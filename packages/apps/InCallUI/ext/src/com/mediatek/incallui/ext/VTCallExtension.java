
package com.mediatek.incallui.ext;

import com.android.services.telephony.common.ICallCommandService;

import android.content.Context;
import android.view.KeyEvent;

import android.view.Menu;
import android.view.MotionEvent;
import android.view.View;

public class VTCallExtension {
    public void onViewCreated(Context context, View root, View.OnTouchListener listener, ICallCommandService service) {
        InCallUIPluginDefault.log("VTCallExtension onViewCreated DEFAULT");
    }

    public boolean onTouch(View v, MotionEvent event, boolean isVTPeerBigger) {
        InCallUIPluginDefault.log("VTCallExtension onTouch DEFAULT");
        return false;
    }

    public boolean onPrepareOptionMenu(Menu menu) {
        InCallUIPluginDefault.log("VTCallExtension onPrepareOptionMenu DEFAULT");
        return false;
    }

    public void resetFlags() {
        InCallUIPluginDefault.log("VTCallExtension resetFlags DEFAULT");
    }
    
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        InCallUIPluginDefault.log("VTCallExtension onKeyDown DEFAULT");
        return false;
    }
}
