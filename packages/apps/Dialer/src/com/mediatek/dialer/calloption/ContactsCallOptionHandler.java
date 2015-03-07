package com.mediatek.dialer.calloption;

import android.content.Context;
import android.content.Intent;
import android.os.ServiceManager;
import android.util.Log;

import com.android.dialer.DialerApplication;
import com.android.internal.telephony.ITelephony;
import com.android.phone.Constants;
import com.mediatek.calloption.CallOptionBaseHandler;
import com.mediatek.calloption.CallOptionHandler;
import com.mediatek.calloption.CallOptionHandlerFactory;
import com.mediatek.calloption.SimAssociateHandler;
import com.mediatek.contacts.ContactsFeatureConstants.FeatureOption;
import com.mediatek.contacts.ExtensionManager;
import com.mediatek.contacts.simcontact.SlotUtils;

public class ContactsCallOptionHandler extends CallOptionHandler
                                       implements CallOptionBaseHandler.ICallOptionResultHandle {

    private static final String TAG = "ContactsCallOptionHandler";

    private Context mActivityContext;

    public ContactsCallOptionHandler(Context activityContext, CallOptionHandlerFactory callOptionHandlerFactory) {
        super(callOptionHandlerFactory);
        mActivityContext = activityContext;
    }
    /**
     * The entry for making an call
     * @param intent the call intent
     */
    public void doCallOptionHandle(Intent intent) {
        //if (FeatureOption.MTK_GEMINI_SUPPORT) {

        final ITelephony telephony =
            ITelephony.Stub.asInterface(ServiceManager.getService(Context.TELEPHONY_SERVICE));
        if (null == telephony) {
            Log.w(TAG, "Can not get telephony service object");
            return;
        }
        SimAssociateHandler.getInstance(DialerApplication.getInstance()).load();
        if (ExtensionManager.getInstance().getContactsCallOptionHandlerExtension().doCallOptionHandle(
                mCallOptionHandlerList, mActivityContext, DialerApplication.getInstance(),
                intent, this, DialerApplication.getInstance().cellConnMgr,
                telephony, FeatureOption.MTK_GEMINI_SUPPORT,
                FeatureOption.MTK_GEMINI_3G_SWITCH)) {
            return;
        }
        super.doCallOptionHandle(mActivityContext, DialerApplication.getInstance(), intent,
                                 this, DialerApplication.getInstance().cellConnMgr,
                                 telephony, SlotUtils.isGeminiEnabled(),
                                 FeatureOption.MTK_GEMINI_3G_SWITCH);

        //} else {
            //intent.setClassName(Constants.PHONE_PACKAGE, Constants.OUTGOING_CALL_BROADCASTER);
            //DialerApplication.getInstance().startActivity(intent);
        //}
    }

    public void onHandlingFinish() {
    }

    public void onContinueCallProcess(Intent intent) {
        /** M: Ensure the Dialogs be dismissed before launch a new "call" @{ */
        dismissDialogs();
        /** @} */
        intent.setAction(Constants.OUTGOING_CALL_RECEIVER);
        intent.setClassName(Constants.PHONE_PACKAGE, Constants.OUTGOING_CALL_RECEIVER);
        DialerApplication.getInstance().sendBroadcast(intent);
    }

    public void onPlaceCallDirectly(Intent intent) {
        intent.setAction(Constants.OUTGOING_CALL_RECEIVER);
        intent.setClassName(Constants.PHONE_PACKAGE, Constants.OUTGOING_CALL_RECEIVER);
        DialerApplication.getInstance().sendBroadcast(intent);
    }

    private static void log(String msg) {
        Log.d(TAG, msg);
    }
}
