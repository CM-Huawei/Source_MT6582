package com.mediatek.settings;

import android.app.ActionBar;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceFragment;
import android.preference.PreferenceScreen;
import android.view.MenuItem;

import com.android.internal.telephony.TelephonyIntents;
import com.android.phone.PhoneUtils;
import com.android.phone.R;

import com.mediatek.common.featureoption.FeatureOption;
import com.mediatek.phone.ext.ICallSettingsConnection;
import com.mediatek.phone.ext.ExtensionManager;
import com.mediatek.phone.PhoneFeatureConstants;
import com.mediatek.phone.gemini.GeminiUtils;
import com.mediatek.phone.wrapper.PhoneWrapper;
import com.mediatek.phone.wrapper.TelephonyManagerWrapper;
import com.mediatek.telephony.SimInfoManager;
import com.mediatek.telephony.SimInfoManager.SimInfoRecord;
import com.mediatek.xlog.Xlog;

import java.util.List;

public class CallSettings extends Activity {
    private static final String LOG_TAG = "Settings/CallSettings";
    static Preference mVTSetting = null;
    static Preference mVoiceSetting = null;
    Preference mSipCallSetting = null;
    /// M: CT call settings.
    private ICallSettingsConnection mExtension;

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            Xlog.d(LOG_TAG, "[action = " + action + "]");
            if (TelephonyIntents.ACTION_SIM_INFO_UPDATE.equals(action)) {
                setScreenEnabled();
            }
        }
    };
    ///M: for adjust setting UI on VXGA device.
    public PreferenceFragment mFragment;

    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        /// M: CT call settings. @{
        mExtension = ExtensionManager.getCallSettingsPlugin(this);
        mExtension.startCallSettingsActivity(this);
        /// @}

        ///M: for adjust setting UI on VXGA device. @{
        mFragment = new CallSettingsFragment();
        getFragmentManager().beginTransaction()
                .replace(android.R.id.content, mFragment).commit();
        /// @}
        
        IntentFilter intentFilter = new IntentFilter(TelephonyIntents.ACTION_SIM_INFO_UPDATE); 
        registerReceiver(mReceiver, intentFilter);

        ActionBar actionBar = getActionBar();
        if (actionBar != null) {
            // android.R.id.home will be triggered in onOptionsItemSelected()
            actionBar.setDisplayHomeAsUpEnabled(true);
        }
    }
    ///M: for adjust setting UI on VXGA device.
    public static class CallSettingsFragment extends PreferenceFragment {

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            addPreferencesFromResource(R.xml.call_feature_setting);
            mVTSetting = this.findPreference("button_vedio_call_key");
            mVoiceSetting = this.findPreference("button_voice_call_key");

            boolean voipSupported = PhoneUtils.isVoipSupported();
            if (!voipSupported || PhoneFeatureConstants.FeatureOption.MTK_CTA_SUPPORT) {
                this.getPreferenceScreen().removePreference(findPreference("button_internet_call_key"));
            }

            //If this video telephony feature is not supported, remove the setting
            if (!FeatureOption.MTK_VT3G324M_SUPPORT) {
                getPreferenceScreen().removePreference(mVTSetting);
                mVTSetting = null;
            }
        }

        public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen, Preference preference) {
            if (preference == mVTSetting) {
                Intent intent = new Intent();
                if (isOnlyVt()) {
                    intent.setClass(getActivity(), VTAdvancedSetting.class);
                } else {
                    intent.setClass(getActivity(), VTAdvancedSettingEx.class);
                }
                getActivity().startActivity(intent);
                return true;
            }
            return false;
        }

        private boolean isOnlyVt() {
            List<SimInfoRecord> siminfoList = SimInfoManager.getInsertedSimInfoList(getActivity());
            return siminfoList.size() == 1 && 
                    GeminiUtils.getBaseband(siminfoList.get(0).mSimSlotId) > GeminiUtils.MODEM_3G;
        }

    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        final int itemId = item.getItemId();
        switch (itemId) {
        case android.R.id.home:
            finish();
            return true;
        default:
            break;
        }
        return super.onOptionsItemSelected(item);
    }
     
    @Override
    public void onResume() {
        super.onResume();
        setScreenEnabled();
    }

    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(mReceiver);
    }

    private void setScreenEnabled() {
        List<SimInfoRecord> insertSim = SimInfoManager.getInsertedSimInfoList(this);
        if (GeminiUtils.isGeminiSupport()) {
            List<SimInfoRecord> insert3GSim = GeminiUtils.get3GSimCards(this.getApplicationContext());
            if (mVTSetting != null)  {
                mVTSetting.setEnabled(insert3GSim.size() > 0);
            }
            mVoiceSetting.setEnabled(insertSim.size() > 0);
         } else {
            boolean hasSimCard = TelephonyManagerWrapper.hasIccCard(PhoneWrapper.UNSPECIFIED_SLOT_ID);
            if (mVTSetting != null)  {
                mVTSetting.setEnabled(hasSimCard);
            }
            mVoiceSetting.setEnabled(hasSimCard);
        }
    }
}
