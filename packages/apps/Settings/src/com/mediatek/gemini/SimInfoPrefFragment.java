package com.mediatek.gemini;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.ServiceManager;
import android.preference.PreferenceScreen;
import android.provider.Settings;

import com.android.internal.telephony.ITelephony;
import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.TelephonyIntents;
import com.android.settings.SettingsPreferenceFragment;

import com.mediatek.common.featureoption.FeatureOption;
import com.mediatek.common.telephony.ITelephonyEx;
import com.mediatek.gemini.simui.SimCardInfoPreference;
import com.mediatek.gemini.simui.SimInfoViewUtil.WidgetType;
import com.mediatek.telephony.SimInfoManager;
import com.mediatek.telephony.SimInfoManager.SimInfoRecord;
import com.mediatek.xlog.Xlog;

import java.util.Collections;
import java.util.List;

public class SimInfoPrefFragment extends SettingsPreferenceFragment {
    
    private static final String TAG = "SimInfoPrefFragment";
    
    
    // LisSimInfoPrefFragmentSimInfoPrefFragmentt record current preference
    protected List<SimInfoRecord> mSimInfoList;
    
    private ITelephony mTelephony;
    private ITelephonyEx mTelephonyEx;
    private IntentFilter mIntentFilter;
    private PreferenceScreen mSimPrefMainScreen;
    //Type of widget view
    private WidgetType mType = WidgetType.None;
    
    private BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            Xlog.d(TAG,"action = " + action);
            if (TelephonyIntents.ACTION_SIM_INDICATOR_STATE_CHANGED.equals(action)) {
                //since the indicator changed so need to update the indicator as well
                int state = intent.getIntExtra(TelephonyIntents.INTENT_KEY_ICC_STATE, -1);
                int slotId = intent.getIntExtra(TelephonyIntents.INTENT_KEY_ICC_SLOT, -1);
                Xlog.d(TAG,"sim card " + slotId + " with state = " + state);
                handleSimIndicChange(slotId, state);
            } else if (TelephonyIntents.ACTION_SIM_INFO_UPDATE.equals(action)) {
                // for sim hot swap case if no card inserted need to finish
                if (isNoSimInserted()) {
                    dealNoSimCardIn();
                } else {
                    //refresh the screen since sim info modified
                    initPreferenceScreen();
                }
            } else if (Intent.ACTION_AIRPLANE_MODE_CHANGED.equals(action)) {
                boolean enable = intent.getBooleanExtra("state", false);
                handleAirplaneModeChange(enable);
            }
        }
    };
    
    protected void handleSimIndicChange(int slotId, int state) {
        if (slotId != -1) {
            boolean isAirplaneModeOn = Settings.System.getInt(
                    getContentResolver(), Settings.System.AIRPLANE_MODE_ON, -1) == 1;
            if (isAirplaneModeOn) {
                state = PhoneConstants.SIM_INDICATOR_RADIOOFF;
            }
            SimCardInfoPreference pref = getPreferenceBySlot(slotId);
            if (pref != null) {
                pref.setSimIndicator(state);
                pref.setEnabled(state != PhoneConstants.SIM_INDICATOR_RADIOOFF ? true : false);
            }
        }
    }
    
    protected SimCardInfoPreference getPreferenceBySlot(int slotId) {
        SimCardInfoPreference pref = null;
        for (int index = 0; index < mSimPrefMainScreen.getPreferenceCount(); index ++) {
            pref = (SimCardInfoPreference) mSimPrefMainScreen.getPreference(index);
            if (pref.getSimSlotId() == slotId) {
                Xlog.d(TAG,"get slotId = " + slotId + " related preference");
                return pref;
            }
        }
        return null;
    }
    
    /**
     * add code here for airplane mode changed, default here only update indicator
     * @param enable true for airplane mode on
     */
    protected void handleAirplaneModeChange(boolean isAirplaneOn) {
        if (isAirplaneOn) {
            SimCardInfoPreference pref;
            for (int index = 0; index < mSimPrefMainScreen.getPreferenceCount(); index++) {
                pref = (SimCardInfoPreference) mSimPrefMainScreen.getPreference(index);
                pref.setSimIndicator(PhoneConstants.SIM_INDICATOR_RADIOOFF);
            }
            //Only disable not enable because the sim card may radio off, so not good to enable here
            getPreferenceScreen().setEnabled(false);
        }
        
    }


    protected boolean isNoSimInserted() {
        int simNum = SimInfoManager.getInsertedSimCount(getActivity());
        Xlog.d(TAG, "simNum = " + simNum);
        return simNum == 0;
    }
    
    protected void setWidgetViewType(WidgetType type) {
        mType = type;
    }
    
    @Override
    public void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        mTelephony = ITelephony.Stub.asInterface(ServiceManager.getService(Context.TELEPHONY_SERVICE));
        mTelephonyEx = ITelephonyEx.Stub.asInterface(ServiceManager.getService(Context.TELEPHONY_SERVICEEX));
        mIntentFilter = new IntentFilter(
                TelephonyIntents.ACTION_SIM_INDICATOR_STATE_CHANGED);
        mIntentFilter.addAction(TelephonyIntents.ACTION_SIM_INFO_UPDATE);
        mIntentFilter.addAction(Intent.ACTION_AIRPLANE_MODE_CHANGED);
        //Add a preferencescreen for this preferencefragment
        mSimPrefMainScreen = getPreferenceManager().createPreferenceScreen(getActivity());
        setPreferenceScreen(mSimPrefMainScreen);
    }

    @Override
    public void onResume() {
        super.onResume();
        initPreferenceScreen();
        if (isNoSimInserted()) {
            /*
             * only happened when sim card plug out at background, and switch to
             * this fragment, otherwise no sim card should not enter the
             * selection screen
             */
            dealNoSimCardIn();
        }
        enablePreferenceScreen();
        getActivity().registerReceiver(mReceiver, mIntentFilter);
    }
    
    private void enablePreferenceScreen() {
        // handle airplane mode is on
        boolean isAirplaneModeOn = Settings.System.getInt(
                getContentResolver(), Settings.System.AIRPLANE_MODE_ON, -1) == 1;
        mSimPrefMainScreen.setEnabled(!isAirplaneModeOn);  
    }
    
    @Override
    public void onPause() {
        super.onPause();
        getActivity().unregisterReceiver(mReceiver);
    }

    /**
     * Only call this onResume() and receive the sim info update broadcast
     * 
     */
    private void initPreferenceScreen() {
        getSimInfoRecordList();
        // if two list size not equal means the preference need to add dynamically since 
        // the size of sim cards has changed
        if (mSimInfoList.size() != mSimPrefMainScreen.getPreferenceCount()) {
            addSimPreference();
        } else {
            //only need to update sim info data no need to re add preference
            updatePrefSimInfo();
        }
    }
    
    private void addSimPreference() {
        mSimPrefMainScreen.removeAll();
        for (SimInfoRecord siminfo: mSimInfoList) {
            Xlog.d(TAG,"initPreferenceScreen with slot = " + siminfo.mSimSlotId);
            SimCardInfoPreference infoPref = new SimCardInfoPreference(mType, getActivity());
            setDataIntoSimPref(infoPref,siminfo);
            mSimPrefMainScreen.addPreference(infoPref);
        }
    }
    
    private void updatePrefSimInfo() {
        SimCardInfoPreference pref;
        int index = 0;
        for (SimInfoRecord siminfo: mSimInfoList) {
            SimCardInfoPreference infoPref = (SimCardInfoPreference)mSimPrefMainScreen.getPreference(index++);
            setDataIntoSimPref(infoPref,siminfo);
        }
    }
    
    private void setDataIntoSimPref(SimCardInfoPreference pref, SimInfoRecord siminfo) {
        int indicator = getSimIndicator(siminfo.mSimSlotId);
        pref.setSimInfoProperty(siminfo, indicator);
        pref.setEnabled(indicator == PhoneConstants.SIM_INDICATOR_RADIOOFF ? false : true);
    }
    /**
     * Update the sim info list and sort the list in slotId order
     * 
     */
    protected List<SimInfoRecord> getSimInfoRecordList() {
        mSimInfoList = SimInfoManager.getInsertedSimInfoList(getActivity());
        Collections.sort(mSimInfoList, new GeminiUtils.SIMInfoComparable());
        return mSimInfoList;
    }
    
    /**
     * As sim hot swap and all sim card plugged out, need to finish() however 
     * if child class need other action override this function to deal with no sim card action
     * 
     */
    public void dealNoSimCardIn() {
        finish();
    }
    
    /**
     * Get specific sim indicator status
     * @param slotId sim slot Id
     * @return the indicator
     */
    protected int getSimIndicator(int slotId) {
        return FeatureOption.MTK_GEMINI_SUPPORT ? 
                GeminiUtils.getSimIndicatorGemini(getContentResolver(), mTelephonyEx,slotId) : 
                GeminiUtils.getSimIndicator(getContentResolver(), mTelephony);
    }
    
    
    protected boolean isSimRadioOff(int slotId) {
        int indicator = FeatureOption.MTK_GEMINI_SUPPORT ? 
                GeminiUtils.getSimIndicatorGemini(getContentResolver(), mTelephonyEx,slotId) : 
                GeminiUtils.getSimIndicator(getContentResolver(), mTelephony);
        return  indicator == PhoneConstants.SIM_INDICATOR_RADIOOFF;
    }
}
