package com.mediatek.phone.calloption;

import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.android.phone.PhoneGlobals;
import com.mediatek.calloption.CallOptionBaseHandler;
import com.mediatek.calloption.CallOptionHandler;
import com.mediatek.calloption.CallOptionHandlerFactory;
import com.mediatek.calloption.SimAssociateHandler;
import com.mediatek.phone.PhoneFeatureConstants.FeatureOption;
import com.mediatek.phone.ext.ExtensionManager;

public class PhoneCallOptionHandler extends CallOptionHandler {

    private static final String TAG = "PhoneCallOptionHandler";

    public PhoneCallOptionHandler(CallOptionHandlerFactory callOptionHandlerFactory) {
        super(callOptionHandlerFactory);
    }

    public void doCallOptionHandle(Intent intent, Context activityContext,
                                   CallOptionBaseHandler.ICallOptionResultHandle resultListener) {
        SimAssociateHandler.getInstance(PhoneGlobals.getInstance()).load();
        if (ExtensionManager.getInstance().getPhoneCallOptionHandlerExtension().doCallOptionHandle(
                mCallOptionHandlerList, activityContext, PhoneGlobals.getInstance(), intent,
                resultListener, PhoneGlobals.getInstance().mCellConnMgr,
                PhoneGlobals.getInstance().phoneMgr, FeatureOption.MTK_GEMINI_SUPPORT,
                FeatureOption.MTK_GEMINI_3G_SWITCH)) {
            return;
        }
        super.doCallOptionHandle(activityContext, PhoneGlobals.getInstance(), intent,
                                 resultListener, PhoneGlobals.getInstance().mCellConnMgr,
                                 PhoneGlobals.getInstance().phoneMgr, FeatureOption.MTK_GEMINI_SUPPORT,
                                 FeatureOption.MTK_GEMINI_3G_SWITCH);
    }

    private static void log(String msg) {
        Log.d(TAG, msg);
    }
}
