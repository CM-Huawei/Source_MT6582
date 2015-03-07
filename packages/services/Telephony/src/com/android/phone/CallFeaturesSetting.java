package com.android.phone;

import android.app.Activity;
import android.app.ActionBar;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Vibrator;
import android.preference.CheckBoxPreference;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.preference.PreferenceScreen;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.MenuItem;

import com.android.internal.telephony.TelephonyIntents;

import com.mediatek.common.featureoption.FeatureOption;
import com.mediatek.phone.GeminiConstants;
import com.mediatek.phone.PhoneLog;
import com.mediatek.phone.gemini.GeminiUtils;
import com.mediatek.phone.wrapper.PhoneWrapper;
import com.mediatek.settings.CallBarring;
import com.mediatek.settings.PreCheckForRunning;
import com.mediatek.settings.VoiceMailSetting;
import com.mediatek.telephony.SimInfoManager;
import com.mediatek.telephony.SimInfoManager.SimInfoRecord;

import java.lang.ref.WeakReference;
import java.util.List;

public class CallFeaturesSetting extends Activity {

    // Used for phone's other modules
    public static final String HAC_KEY = "HACSetting";
    public static final String HAC_VAL_ON = "ON";
    public static final String HAC_VAL_OFF = "OFF";

 // intent action to bring up voice mail settings
    public static final String ACTION_ADD_VOICEMAIL =
        "com.android.phone.CallFeaturesSetting.ADD_VOICEMAIL";
    // intent action sent by this activity to a voice mail provider
    // to trigger its configuration UI
    public static final String ACTION_CONFIGURE_VOICEMAIL =
        "com.android.phone.CallFeaturesSetting.CONFIGURE_VOICEMAIL";
    // Extra put in the return from VM provider config containing voicemail number to set
    public static final String VM_NUMBER_EXTRA = "com.android.phone.VoicemailNumber";
    // Extra put in the return from VM provider config containing call forwarding number to set
    public static final String FWD_NUMBER_EXTRA = "com.android.phone.ForwardingNumber";
    // Extra put in the return from VM provider config containing call forwarding number to set
    public static final String FWD_NUMBER_TIME_EXTRA = "com.android.phone.ForwardingNumberTime";
    // If the VM provider returns non null value in this extra we will force the user to
    // choose another VM provider
    public static final String SIGNOUT_EXTRA = "com.android.phone.Signout";
    ///Google: Add for Migration 4.3 but we don't use @{
    //Information about logical "up" Activity
    private static final String UP_ACTIVITY_PACKAGE = "com.android.dialer";
    private static final String UP_ACTIVITY_CLASS =
            "com.android.dialer.DialtactsActivity";
    /// @}

    private static final String LOG_TAG = "Settings/CallFeaturesSetting";
    private static final boolean DBG = true; // (PhoneApp.DBG_LEVEL >= 2);

    private static final String BUTTON_CALL_FWD_KEY    = "button_cf_expand_key";
    private static final String BUTTON_CALL_BAR_KEY    = "button_cb_expand_key";
    private static final String BUTTON_CALL_ADDITIONAL_KEY    = "button_more_expand_key";
    private static final String BUTTON_CALL_VOICEMAIL_KEY    = "button_voicemail_key";
    private static final String BUTTON_IP_PREFIX_KEY = "button_ip_prefix_key";
    private static final String TARGET_PREFERENCE_KEY = "target_preference_key";

    private Preference mButtonVoiceMail;
    private Preference mButtonCallFwd;
    private Preference mButtonCallBar;
    private Preference mButtonCallAdditional;
    private Preference mButtonIpPrefix;

    private Preference mTargetPreference;
    private int mSlotId = GeminiUtils.getDefaultSlot();
    private PreCheckForRunning mPreCfr = null;
    ///M: for adjust setting UI on VXGA device.
    public PreferenceFragment mFragment;

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            PhoneLog.d(LOG_TAG, "[action = " + intent.getAction() + "]");
            setScreenEnabled();
        }
    };

    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        ///M: for adjust setting UI on VXGA device. @{        
        mFragment = new CallFeaturesSettingFragment();
        getFragmentManager().beginTransaction()
                .replace(android.R.id.content, mFragment).commit();
        /// @}
    }
    /// M: for adjust setting UI on VXGA device.
    public static class CallFeaturesSettingFragment extends PreferenceFragment {
        WeakReference<CallFeaturesSetting> activityRef = null;

        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            activityRef = new WeakReference<CallFeaturesSetting>((CallFeaturesSetting)getActivity());
            addPreferencesFromResource(R.xml.voice_call_settings);
            addPreferencesFromResource(R.xml.gsm_umts_call_options);

            PreferenceScreen prefSet = getPreferenceScreen();
            activityRef.get().mButtonCallAdditional = prefSet
                    .findPreference(BUTTON_CALL_ADDITIONAL_KEY);
            activityRef.get().mButtonCallFwd = prefSet.findPreference(BUTTON_CALL_FWD_KEY);
            activityRef.get().mButtonCallBar = prefSet.findPreference(BUTTON_CALL_BAR_KEY);
            activityRef.get().mButtonVoiceMail = prefSet
                    .findPreference(BUTTON_CALL_VOICEMAIL_KEY);
            activityRef.get().mButtonIpPrefix = prefSet.findPreference(BUTTON_IP_PREFIX_KEY);

            if (!PhoneUtils.isSupportFeature("IP_DIAL")) {
                prefSet.removePreference(activityRef.get().mButtonIpPrefix);
                activityRef.get().mButtonIpPrefix = null;
            }

            activityRef.get().mPreCfr = new PreCheckForRunning(getActivity());

            ActionBar actionBar = getActivity().getActionBar();
            if (actionBar != null) {
                // android.R.id.home will be triggered in
                // onOptionsItemSelected()
                actionBar.setDisplayHomeAsUpEnabled(true);
            }
            IntentFilter intentFilter = new IntentFilter(
                    Intent.ACTION_AIRPLANE_MODE_CHANGED);
            intentFilter
                    .addAction(TelephonyIntents.ACTION_SIM_INDICATOR_STATE_CHANGED);
            intentFilter.addAction(TelephonyIntents.ACTION_SIM_INFO_UPDATE);
            getActivity().registerReceiver(activityRef.get().mReceiver, intentFilter);
        }

        @Override
        public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen,
                Preference preference) {
            activityRef.get().mTargetPreference = preference;
            activityRef.get().mSlotId = GeminiUtils.getSlotId(getActivity(), 
                    preference.getTitle().toString(), android.R.style.Theme_Holo_Light_DialogWhenLarge);
            android.util.Log.d("CallFeature", "mSlotId = " + activityRef.get().mSlotId);
            // / M: ALPS010134866, Ip prefix do not to check pin code.
            activityRef.get().internalStartActivity(preference);
            return true;
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        setScreenEnabled();
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

    /**
     *
     * Finish current Activity and go up to the top level Settings ({@link CallFeaturesSetting}).
     * This is useful for implementing "HomeAsUp" capability for second-level Settings.
     */
    public static void goUpToTopLevelSetting(Activity activity) {
        Intent intent = new Intent(activity.getApplicationContext(), CallFeaturesSetting.class);
        intent.setAction(Intent.ACTION_MAIN);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        activity.startActivity(intent);
        activity.finish();
    }


    private void internalStartActivity(Preference preference) {
        if (mSlotId != GeminiUtils.UNDEFINED_SLOT_ID) {
            if ((mButtonIpPrefix != null) && (preference.getKey() != mButtonIpPrefix.getKey())) {
                GeminiUtils.startActivity(mSlotId, preference, mPreCfr);
            } else {
                Intent intent = GeminiUtils.getTargetIntent(mSlotId, preference);
                if (intent != null) {
                    this.startActivity(intent);
                }
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        PhoneLog.d(LOG_TAG, "reqCode=" + requestCode + ",resCode=" + resultCode);
        if (GeminiUtils.REQUEST_SIM_SELECT == requestCode) {
            if (RESULT_OK == resultCode) {
                mSlotId = data.getIntExtra(GeminiConstants.SLOT_ID_KEY, GeminiUtils.UNDEFINED_SLOT_ID);
            } 
        }
        PhoneLog.d(LOG_TAG, "mSlotId=" + mSlotId);
        /// M: ALPS010134866, Ip prefix do not to check pin code.
        internalStartActivity(mTargetPreference);
    }

    protected void onDestroy() {
        super.onDestroy();
        if (mPreCfr != null) {
            mPreCfr.deRegister();
        }
        unregisterReceiver(mReceiver);
    }

    /**
     * Obtain the setting for "vibrate when ringing" setting.
     *
     * Watch out: if the setting is missing in the device, this will try obtaining the old
     * "vibrate on ring" setting from AudioManager, and save the previous setting to the new one.
     */
    public static boolean getVibrateWhenRinging(Context context) {
        Vibrator vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
        if (vibrator == null || !vibrator.hasVibrator()) {
            return false;
        }

        return Settings.System.getInt(context.getContentResolver(),
                Settings.System.VIBRATE_WHEN_RINGING, 0) != 0;
    }

    private void setScreenEnabled() {
        List<SimInfoRecord> list = SimInfoManager.getInsertedSimInfoList(this);
        if (list.size() == 0) {
            finish();
        } else if (list.size() == 1) {
            int slotId = list.get(0).mSimSlotId;
            mFragment.getPreferenceScreen().setEnabled(!PhoneWrapper.isRadioOffBySlot(slotId, this));
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (mTargetPreference != null) {
            outState.putString(TARGET_PREFERENCE_KEY, mTargetPreference.getKey());
        }
        /// M: For ALPS01007004. @{
        PhoneLog.d(LOG_TAG, "[onSaveInstanceState], mSlotId = " + mSlotId);
        outState.putInt(GeminiConstants.SLOT_ID_KEY, mSlotId);
        /// @}
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        String targetPreferenceKey = savedInstanceState.getString(TARGET_PREFERENCE_KEY, "");
        if (!TextUtils.isEmpty(targetPreferenceKey)) {
            PreferenceScreen prefSet = mFragment.getPreferenceScreen();
            mTargetPreference = prefSet.findPreference(targetPreferenceKey);
        }
        /// M: For ALPS01007004. @{
        mSlotId = savedInstanceState.getInt(GeminiConstants.SLOT_ID_KEY);
        PhoneLog.d(LOG_TAG, "[onRestoreInstanceState], mSlotId = " + mSlotId);
        /// @}
    }

}
