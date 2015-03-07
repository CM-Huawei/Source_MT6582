package com.mediatek.phone.ext;

import android.app.Activity;
import android.preference.PreferenceScreen;
import android.util.Log;

public class DefaultCallSettingsConnection implements ICallSettingsConnection {

    private static final String TAG = "DefaultCallSettingsConnection";
    protected static final String CDMA_CALL_OPTION_CLASS_NAME = "com.mediatek.settings.CdmaCallWaitingOptions";

    public void startCallSettingsActivity(Activity activity) {
        Log.d(TAG, "DefaultCallSettingsConnection startCallSettingsActivity");
    }

    public void setCallForwardPrefsTitle(PreferenceScreen prefSet) {
        Log.d(TAG, "DefaultCallSettingsConnection setCallForwardPrefsTitle");
    }

    public String getCdmaCallOptionClassName() {
        return CDMA_CALL_OPTION_CLASS_NAME;
    }
}
