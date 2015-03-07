package com.mediatek.gemini;

import android.content.Intent;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceScreen;

import com.android.internal.telephony.PhoneConstants;

import com.mediatek.gemini.simui.SimCardInfoPreference;
import com.mediatek.xlog.Xlog;

public class SelectSimCardFragment extends SimInfoPrefFragment {

    private static final String TAG = "SelectSimCardFragment";
    
    @Override
    public void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        Xlog.d(TAG,"onCreate()");
    }

    @Override
    public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen, Preference preference) {
        if (preference instanceof SimCardInfoPreference) {
            SimCardInfoPreference simInfoPref = (SimCardInfoPreference) preference;
            Intent intent = new Intent();
            int slotId = simInfoPref.getSimSlotId();
            long simId = simInfoPref.getSimInfoId();
            intent.putExtra(GeminiUtils.EXTRA_SLOTID, slotId);
            intent.putExtra(GeminiUtils.EXTRA_SIMID, simId);
            intent.putExtra(PhoneConstants.GEMINI_SIM_ID_KEY, slotId);
            Xlog.d(TAG,"onPreferenceTreeClick with slotId = " + slotId + " and simid = " + simId);
            getActivity().setResult(getActivity().RESULT_OK, intent);
            finish();
            return true;
        }
        return false;
    }
}
