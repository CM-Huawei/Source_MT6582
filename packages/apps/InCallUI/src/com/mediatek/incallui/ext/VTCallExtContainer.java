
package com.mediatek.incallui.ext;

import java.util.ArrayList;
import java.util.List;

import com.android.incallui.Log;
import com.android.services.telephony.common.ICallCommandService;

import android.content.Context;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MotionEvent;
import android.view.View;

class VTCallExtContainer extends VTCallExtension{

    private List<VTCallExtension> mSubList = null;

    public VTCallExtContainer() {
        mSubList = new ArrayList<VTCallExtension>();
    }

    public void add(VTCallExtension item) {
        if (mSubList == null) {
            mSubList = new ArrayList<VTCallExtension>();
        }

        mSubList.add(item);
    }

    public void onViewCreated(Context context, View root, View.OnTouchListener listener,ICallCommandService service) {
        if (mSubList == null || mSubList.isEmpty()) {
            Log.w(this, "onViewCreated, but no plug-ins.");
            return;
        }
        Log.d(this, "onViewCreated.");
        for (VTCallExtension item : mSubList) {
            item.onViewCreated(context, root, listener, service);
        }
    }

    public boolean onTouch(View v, MotionEvent event, boolean isVTPeerBigger) {
        if (mSubList == null || mSubList.isEmpty()) {
            Log.w(this, "onTouch, but no plug-ins.");
            return false;
        }

        for (VTCallExtension item : mSubList) {
            if (item.onTouch(v, event, isVTPeerBigger)) {
                Log.d(this, "onTouch, plug-in return true. isVTPeerBigger=" + isVTPeerBigger);
                return true;
            }
        }
        Log.d(this, "onTouch, return default. isVTPeerBigger=" + isVTPeerBigger);
        return false;
    }

    public boolean onPrepareOptionMenu(Menu menu) {
        if (mSubList == null || mSubList.isEmpty()) {
            Log.w(this, "onPrepareOptionMenu, but no plug-ins.");
            return false;
        }

        for (VTCallExtension item : mSubList) {
            if (item.onPrepareOptionMenu(menu)) {
                Log.d(this, "onPrepareOptionMenu, plug-in return true.");
                return true;
            }
        }
        Log.d(this, "onPrepareOptionMenu, return default.");
        return false;
    }

    public void resetFlags() {
        if (mSubList == null || mSubList.isEmpty()) {
            Log.w(this, "resetFlags, but no plug-ins.");
            return;
        }
        Log.d(this, "resetFlags.");
        for (VTCallExtension item : mSubList) {
            item.resetFlags();
        }
    }

    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (mSubList == null || mSubList.isEmpty()) {
            Log.w(this, "resetFlags, but no plug-ins.");
            return false;
        }
        Log.d(this, "resetFlags.");
        for (VTCallExtension item : mSubList) {
            if (item.onKeyDown(keyCode, event)){
                return true;
            }
        }
        return false;
    }
}
