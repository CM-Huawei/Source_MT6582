package com.mediatek.settings;

import android.app.Activity;
import android.os.AsyncResult;
import android.os.Handler;
import android.os.Message;
import android.preference.ListPreference;


import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneFactory;
import com.android.phone.PhoneGlobals;

import com.mediatek.phone.PhoneLog;
import com.mediatek.phone.PhoneInterfaceManagerEx;
import com.mediatek.phone.gemini.GeminiUtils;
import com.mediatek.phone.wrapper.PhoneWrapper;

public class NetWorkHandler extends Handler {
    private static final String TAG = "NetWorkHandler";
    private static final int PREFERRED_NETWORK_MODE = Phone.PREFERRED_NT_MODE;
    public static final int MESSAGE_GET_PREFERRED_NETWORK_TYPE = 0;
    public static final int MESSAGE_SET_PREFERRED_NETWORK_TYPE = 1;

    private ListPreference mPreference;
    private Phone mPhone;
    private PhoneInterfaceManagerEx mPhoneMgrEx;
    private Activity mActivity;

    public NetWorkHandler(Activity activity, ListPreference preference) {
        mPreference = preference;
        mPhone = PhoneFactory.getDefaultPhone();
        mPhoneMgrEx = PhoneGlobals.getInstance().phoneMgrEx;
        mActivity = activity;
    }

    @Override
    public void handleMessage(Message msg) {
        switch (msg.what) {
        case MESSAGE_GET_PREFERRED_NETWORK_TYPE:
            handleGetPreferredNetworkTypeResponse(msg);
            break;

        case MESSAGE_SET_PREFERRED_NETWORK_TYPE:
            handleSetPreferredNetworkTypeResponse(msg);
            break;
        default:
            break;
        }
    }

    private void handleGetPreferredNetworkTypeResponse(Message msg) {
        AsyncResult ar = (AsyncResult) msg.obj;

        if (ar.exception == null) {
            int modemNetworkMode = ((int[]) ar.result)[0];
            PhoneLog.d(TAG, "handleGetPreferredNetworkTypeResponse: modemNetworkMode = "
                    + modemNetworkMode);
            int settingsNetworkMode = android.provider.Settings.Global.getInt(
                    mPhone.getContext().getContentResolver(),
                    android.provider.Settings.Global.PREFERRED_NETWORK_MODE,
                    PREFERRED_NETWORK_MODE);
            PhoneLog.d(TAG, "handleGetPreferredNetworkTypeReponse: settingsNetworkMode = "
                    + settingsNetworkMode);
            // check that modemNetworkMode is from an accepted value
            if (modemNetworkMode == Phone.NT_MODE_WCDMA_PREF
                    || modemNetworkMode == Phone.NT_MODE_GSM_ONLY
                    || modemNetworkMode == Phone.NT_MODE_WCDMA_ONLY
                    || modemNetworkMode == Phone.NT_MODE_GSM_UMTS
                    || modemNetworkMode == Phone.NT_MODE_CDMA
                    || modemNetworkMode == Phone.NT_MODE_CDMA_NO_EVDO
                    || modemNetworkMode == Phone.NT_MODE_EVDO_NO_CDMA
                    || modemNetworkMode == Phone.NT_MODE_GLOBAL) {
                PhoneLog.d(TAG, "handleGetPreferredNetworkTypeResponse: if 1: modemNetworkMode = "
                        + modemNetworkMode);

                // Framework's Phone.NT_MODE_GSM_UMTS is same as app's
                // NT_MODE_WCDMA_PREF, this is related with feature option
                // MTK_RAT_WCDMA_PREFERRED. In app side, we should change
                // the setting system's value to NT_MODE_WCDMA_PREF, and keep
                // sync with Modem's value.
                if (modemNetworkMode == Phone.NT_MODE_GSM_UMTS) {
                    modemNetworkMode = Phone.NT_MODE_WCDMA_PREF;
                    if (settingsNetworkMode != Phone.NT_MODE_WCDMA_PREF) {
                        settingsNetworkMode = Phone.NT_MODE_WCDMA_PREF;
                        android.provider.Settings.Global.putInt(
                                mPhone.getContext().getContentResolver(),
                                android.provider.Settings.Global.PREFERRED_NETWORK_MODE,
                                settingsNetworkMode);
                    }
                } else {
                    if (modemNetworkMode != settingsNetworkMode) {
                        settingsNetworkMode = modemNetworkMode;
                        android.provider.Settings.Global.putInt(
                                mPhone.getContext().getContentResolver(),
                                android.provider.Settings.Global.PREFERRED_NETWORK_MODE,
                                settingsNetworkMode);
                    }
                }

                // changes the mButtonPreferredNetworkMode accordingly to
                // modemNetworkMode
                // add log for status sync not right issue
                PhoneLog.d(TAG, "modemNetworkMode = " + modemNetworkMode +"  preference entry = " + mPreference.getEntry());
                mPreference.setValue(Integer.toString(modemNetworkMode));
                mPreference.setSummary(mPreference.getEntry());
            } else if (modemNetworkMode == Phone.NT_MODE_LTE_ONLY) {
                // LTE Only mode not yet supported on UI, but could be used for
                // testing
                PhoneLog.d(TAG, "handleGetPreferredNetworkTypeResponse: lte only: no action");
            } else {
                PhoneLog.d(TAG, "handleGetPreferredNetworkTypeResponse: else: reset to default");
                resetNetworkModeToDefault();
            }
        }
    }

    private void handleSetPreferredNetworkTypeResponse(Message msg) {
        AsyncResult ar = (AsyncResult) msg.obj;
        PhoneLog.d(TAG, "handleSetPreferredNetworkTypeResponse: ar.exception = " + ar.exception);
        /// M: when set network mode show wait dialog
        mActivity.removeDialog(GeminiUtils.PROGRESS_DIALOG);
        /// M: For ALPS01018921.
        // Remove (ar.exception == null) case, we reput value.
        // In that case we needn't do that again, for we have done that before.
        if (ar.exception != null) {
            int slotId = mPhoneMgrEx.get3GCapabilitySIM();
            PhoneWrapper.getPreferredNetworkType(mPhone, obtainMessage(MESSAGE_GET_PREFERRED_NETWORK_TYPE), slotId);
        }
    }

    private void resetNetworkModeToDefault() {
        // set the mButtonPreferredNetworkMode
        mPreference.setValue(Integer.toString(PREFERRED_NETWORK_MODE));

        // set the Settings.System
        android.provider.Settings.Global.putInt(mPhone.getContext()
                .getContentResolver(),
                android.provider.Settings.Global.PREFERRED_NETWORK_MODE,
                PREFERRED_NETWORK_MODE);
        /// M: support gemini Set the Modem
        int slotId = mPhoneMgrEx.get3GCapabilitySIM();
        PhoneWrapper.setPreferredNetworkType(mPhone, PREFERRED_NETWORK_MODE,
                obtainMessage(NetWorkHandler.MESSAGE_SET_PREFERRED_NETWORK_TYPE), slotId);
    }

}
