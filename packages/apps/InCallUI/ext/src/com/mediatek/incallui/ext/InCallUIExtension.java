
package com.mediatek.incallui.ext;

import android.app.Activity;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;

import com.android.services.telephony.common.Call;

public class InCallUIExtension {

    public void onCreate(Bundle icicle, Activity inCallActivity, IInCallScreen iInCallScreen) {
        InCallUIPluginDefault.log("InCallUIExtension onCreate DEFAULT");
    }

    public void onDestroy(Activity inCallActivity) {
        InCallUIPluginDefault.log("InCallUIExtension onDestroy DEFAULT");
    }

    public void setupMenuItems(Menu menu, Call call) {
        InCallUIPluginDefault.log("InCallUIExtension setupMenuItems DEFAULT");
    }

    public boolean handleMenuItemClick(MenuItem menuItem) {
        InCallUIPluginDefault.log("InCallUIExtension handleMenuItemClick DEFAULT");
        return false;
    }
}
