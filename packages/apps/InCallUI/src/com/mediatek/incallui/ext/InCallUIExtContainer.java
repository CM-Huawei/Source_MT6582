
package com.mediatek.incallui.ext;

import java.util.ArrayList;
import java.util.List;

import android.app.Activity;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;

import com.android.incallui.Log;
import com.android.services.telephony.common.Call;

public class InCallUIExtContainer extends InCallUIExtension {
    private List<InCallUIExtension> mSubList = null;

    public InCallUIExtContainer() {
        mSubList = new ArrayList<InCallUIExtension>();
    }

    public void add(InCallUIExtension item) {
        if (mSubList == null) {
            mSubList = new ArrayList<InCallUIExtension>();
        }

        mSubList.add(item);
    }

    public void onCreate(Bundle icicle, Activity inCallActivity, IInCallScreen iInCallScreen) {
        if (mSubList == null || mSubList.isEmpty()) {
            Log.w(this, "onCreate, but no plug-ins.");
            return;
        }

        Log.d(this, "onCreate. icicle = " + icicle);
        for (InCallUIExtension item : mSubList) {
            item.onCreate(icicle, inCallActivity, iInCallScreen);
        }
    }

    public void onDestroy(Activity inCallActivity) {
        if (mSubList == null || mSubList.isEmpty()) {
            Log.w(this, "onDestroy, but no plug-ins.");
            return;
        }
        Log.d(this, "onDestroy.");
        for (InCallUIExtension item : mSubList) {
            item.onDestroy(inCallActivity);
        }
    }

    public void setupMenuItems(Menu menu, Call call) {
        if (mSubList == null || mSubList.isEmpty()) {
            Log.w(this, "setupMenuItems, but no plug-ins.");
            return;
        }
        Log.d(this, "setupMenuItems. menu = " + menu);
        for (InCallUIExtension item : mSubList) {
            item.setupMenuItems(menu, call);
        }
    }

    public boolean handleMenuItemClick(MenuItem menuItem) {
        if (mSubList == null || mSubList.isEmpty()) {
            Log.w(this, "handleMenuItemClick, but no plug-ins.");
            return false;
        }
        Log.d(this, "handleMenuItemClick. menuId = " + menuItem.getItemId());
        for (InCallUIExtension item : mSubList) {
            if (item.handleMenuItemClick(menuItem)) {
                Log.d(this, "handleMenuItemClick, plug-in return true.");
                return true;
            }
        }
        return false;
    }
}
