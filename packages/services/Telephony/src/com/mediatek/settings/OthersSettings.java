package com.mediatek.settings;

import android.app.ActionBar;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceFragment;
import android.preference.PreferenceScreen;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.MenuItem;

import com.android.internal.telephony.Phone;
import com.android.internal.telephony.TelephonyIntents;
import com.android.internal.telephony.cdma.TtyIntent;
import com.android.phone.PhoneUtils;
import com.android.phone.R;

import com.mediatek.phone.GeminiConstants;
import com.mediatek.phone.PhoneLog;
import com.mediatek.phone.ext.ExtensionManager;
import com.mediatek.phone.ext.OthersSettingsExtension;
import com.mediatek.phone.gemini.GeminiUtils;
import com.mediatek.phone.wrapper.PhoneWrapper;
import com.mediatek.telephony.SimInfoManager;
import com.mediatek.telephony.SimInfoManager.SimInfoRecord;

import java.lang.ref.WeakReference;
import java.util.List;

public class OthersSettings extends android.app.Activity { 
    private static final String BUTTON_OTHERS_FDN_KEY     = "button_fdn_key";
    private static final String BUTTON_OTHERS_MINUTE_REMINDER_KEY    = "minute_reminder_key";
    private static final String BUTTON_OTHERS_DUAL_MIC_KEY = "dual_mic_key";
    private static final String BUTTON_TTY_KEY    = "button_tty_mode_key";
    private static final String BUTTON_INTER_KEY    = "international_dialing_key";
    /// M: For ALPS01062292. @{
    // used to save or obtain the key of target preference from Bundle.
    private static final String TARGET_PREFERENCE_KEY = "target_preference_key";
    /// @}

    private static final String LOG_TAG = "Settings/OthersSettings";
    private Preference mButtonFdn;
    private CheckBoxPreference mButtonMr;
    private CheckBoxPreference mButtonDualMic;
    private ListPreference mButtonTTY;
    private CheckBoxPreference mButtonInter;
    
    private static final int DEFAULT_INTER_DIALING_VALUE = 0;
    private static final int INTER_DIALING_ON = 1;
    private static final int INTER_DIALING_OFF = 0;
    
    private int mSlotId = 0;
    private PreCheckForRunning mPreCfr = null;
    private Preference mTargetPreference;
    
    private OthersSettingsExtension mExtension;
    
    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            PhoneLog.d(LOG_TAG, "[action = " + action + "]");
            if (Intent.ACTION_AIRPLANE_MODE_CHANGED.equals(action)) {
                setScreenEnabled();
            } else if (TelephonyIntents.ACTION_SIM_INDICATOR_STATE_CHANGED.equals(action)) {
                setScreenEnabled();
            } else if (TelephonyIntents.ACTION_SIM_INFO_UPDATE.equals(action)) {
                setScreenEnabled();
            }
        }
    };
    /// M: modified to fragment for VXGA device UI.
    public PreferenceFragment mFragment;

    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        
        /// M: modified to fragment for VXGA device UI.
        mFragment = new OthersSettingsFragment();
        getFragmentManager().beginTransaction()
                .replace(android.R.id.content, mFragment).commit();
    }
    /// M: modified to fragment for VXGA device UI.
    public static class OthersSettingsFragment extends PreferenceFragment implements
                                Preference.OnPreferenceChangeListener{
        WeakReference<OthersSettings> activityRef = null;

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            activityRef = new WeakReference<OthersSettings>((OthersSettings)getActivity()); 
            activityRef.get().mExtension = ExtensionManager.getInstance().getOthersSettingsExtension();
            addPreferencesFromResource(R.xml.others_settings);
            activityRef.get().mButtonFdn = findPreference(BUTTON_OTHERS_FDN_KEY);
            activityRef.get().mButtonMr = (CheckBoxPreference)findPreference(BUTTON_OTHERS_MINUTE_REMINDER_KEY);
            activityRef.get().mButtonDualMic = (CheckBoxPreference)findPreference(BUTTON_OTHERS_DUAL_MIC_KEY);
            activityRef.get().mButtonInter = (CheckBoxPreference)findPreference(BUTTON_INTER_KEY);

            activityRef.get().mExtension.customizeCallRejectFeature(getPreferenceScreen());

            if (!PhoneUtils.isSupportFeature("DUAL_MIC")) {
                this.getPreferenceScreen().removePreference(activityRef.get().mButtonDualMic);
            }

            if (activityRef.get().mButtonMr != null) {
                activityRef.get().mButtonMr.setOnPreferenceChangeListener(this);
            }

            if (activityRef.get().mButtonDualMic != null) {
                activityRef.get().mButtonDualMic.setOnPreferenceChangeListener(this);
            }
            activityRef.get().mButtonTTY = (ListPreference) findPreference(BUTTON_TTY_KEY);

            if (activityRef.get().mButtonTTY != null) {
                if (PhoneUtils.isSupportFeature("TTY")) {
                    activityRef.get().mButtonTTY.setOnPreferenceChangeListener(this);
                } else {
                    getPreferenceScreen().removePreference(activityRef.get().mButtonTTY);
                    activityRef.get().mButtonTTY = null;
                }
            }
            if (activityRef.get().mButtonInter != null) {
                activityRef.get().mButtonInter.setOnPreferenceChangeListener(this);
                int checkedStatus = Settings.System.getInt(activityRef.get().getContentResolver(), 
                        Settings.System.INTER_DIAL_SETTING, DEFAULT_INTER_DIALING_VALUE); 
                activityRef.get().mButtonInter.setChecked(checkedStatus != 0);
                PhoneLog.d(LOG_TAG, "onResume isChecked in DB:" + (checkedStatus != 0));
            }

            activityRef.get().mPreCfr = new PreCheckForRunning(getActivity());

            ActionBar actionBar = getActivity().getActionBar();
            if (actionBar != null) {
                // android.R.id.home will be triggered in onOptionsItemSelected()
                actionBar.setDisplayHomeAsUpEnabled(true);
            }
            IntentFilter intentFilter = new IntentFilter(Intent.ACTION_AIRPLANE_MODE_CHANGED); 
            intentFilter.addAction(TelephonyIntents.ACTION_SIM_INDICATOR_STATE_CHANGED);
            intentFilter.addAction(TelephonyIntents.ACTION_SIM_INFO_UPDATE);
            getActivity().registerReceiver(activityRef.get().mReceiver, intentFilter);
        }

        @Override
        public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen, Preference preference) {
            if (activityRef.get().mButtonFdn == preference) {
                activityRef.get().mTargetPreference = preference;
                activityRef.get().mSlotId = GeminiUtils.getSlotId(getActivity(), preference.getTitle().toString(), android.R.style.Theme_Holo_Light_DialogWhenLarge);
                if (activityRef.get().mSlotId != GeminiUtils.UNDEFINED_SLOT_ID) {
                    GeminiUtils.startActivity(activityRef.get().mSlotId, preference, activityRef.get().mPreCfr);
                }
                return true;
            }
            return false;
        }

         public boolean onPreferenceChange(Preference preference, Object objValue) {
             if (preference == activityRef.get().mButtonDualMic) {
                 if (activityRef.get().mButtonDualMic.isChecked()) {
                     PhoneLog.d(LOG_TAG, "onPreferenceChange mButtonDualmic turn on");
                     PhoneUtils.setDualMicMode("0");
                 } else {
                     PhoneLog.d(LOG_TAG, "onPreferenceChange mButtonDualmic turn off");
                     PhoneUtils.setDualMicMode("1");
                 }
             } else if (preference == activityRef.get().mButtonTTY) {
                 activityRef.get().handleTTYChange(preference, objValue);
             } else if (preference == activityRef.get().mButtonInter) {
                 if ((Boolean)objValue) {
                     Settings.System.putInt(activityRef.get().getContentResolver(), Settings.System.INTER_DIAL_SETTING, INTER_DIALING_ON); 
                 } else {
                     Settings.System.putInt(activityRef.get().getContentResolver(), Settings.System.INTER_DIAL_SETTING, INTER_DIALING_OFF); 
                 }
                 PhoneLog.d(LOG_TAG, "onPreferenceChange mButtonInter turn :"
                        + Settings.System.getInt(activityRef.get().getContentResolver(), Settings.System.INTER_DIAL_SETTING, -1));
             }
             return true;
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

    public void onResume() {
        super.onResume();
        setScreenEnabled();

        if (mButtonTTY != null) {
            int settingsTtyMode = Settings.Secure.getInt(getContentResolver(),
                    Settings.Secure.PREFERRED_TTY_MODE,
                    Phone.TTY_MODE_OFF);
            mButtonTTY.setValue(Integer.toString(settingsTtyMode));
            updatePreferredTtyModeSummary(settingsTtyMode);
        }
    }
     
    protected void onDestroy() {
        super.onDestroy();
        if (mPreCfr != null) {
            mPreCfr.deRegister();
        }
        unregisterReceiver(mReceiver);
    }

    private void handleTTYChange(Preference preference, Object objValue) {
        int buttonTtyMode;
        buttonTtyMode = Integer.valueOf((String) objValue).intValue();
        int settingsTtyMode = android.provider.Settings.Secure.getInt(
                getContentResolver(),
                android.provider.Settings.Secure.PREFERRED_TTY_MODE, Phone.TTY_MODE_OFF);
        PhoneLog.d(LOG_TAG, "handleTTYChange: requesting set TTY mode enable (TTY) to" +
                Integer.toString(buttonTtyMode));

        if (buttonTtyMode != settingsTtyMode) {
            switch(buttonTtyMode) {
            case Phone.TTY_MODE_OFF:
            case Phone.TTY_MODE_FULL:
            case Phone.TTY_MODE_HCO:
            case Phone.TTY_MODE_VCO:
                android.provider.Settings.Secure.putInt(getContentResolver(),
                        android.provider.Settings.Secure.PREFERRED_TTY_MODE, buttonTtyMode);
                break;
            default:
                buttonTtyMode = Phone.TTY_MODE_OFF;
            }

            mButtonTTY.setValue(Integer.toString(buttonTtyMode));
            updatePreferredTtyModeSummary(buttonTtyMode);
            Intent ttyModeChanged = new Intent(TtyIntent.TTY_PREFERRED_MODE_CHANGE_ACTION);
            ttyModeChanged.putExtra(TtyIntent.TTY_PREFFERED_MODE, buttonTtyMode);
            sendBroadcast(ttyModeChanged);
        }
    }
    
    private void updatePreferredTtyModeSummary(int ttyMode) {
        String [] txts = getResources().getStringArray(R.array.tty_mode_entries);
        switch(ttyMode) {
            case Phone.TTY_MODE_OFF:
            case Phone.TTY_MODE_HCO:
            case Phone.TTY_MODE_VCO:
            case Phone.TTY_MODE_FULL:
                mButtonTTY.setSummary(txts[ttyMode]);
                break;
            default:
                mButtonTTY.setEnabled(false);
                mButtonTTY.setSummary(txts[Phone.TTY_MODE_OFF]);
        }
    }

    private void setScreenEnabled() {
        boolean airplaneModeOn = android.provider.Settings.System.getInt(getContentResolver(),
                android.provider.Settings.System.AIRPLANE_MODE_ON, -1) == 1;

        List<SimInfoRecord> insertSim = SimInfoManager.getInsertedSimInfoList(this);
        if (insertSim.size() == 0) {
            mButtonFdn.setEnabled(false);
        } else if (insertSim.size() == 1) {
            int slotId = insertSim.get(0).mSimSlotId;
            mButtonFdn.setEnabled(!PhoneWrapper.isRadioOffBySlot(slotId, this));
        } else {
            mButtonFdn.setEnabled(true);
        }
    }

    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        PhoneLog.d(LOG_TAG, "reqCode=" + requestCode + ",resCode=" + resultCode);
        if (GeminiUtils.REQUEST_SIM_SELECT == requestCode) {
            if (RESULT_OK == resultCode) {
                mSlotId = data.getIntExtra(GeminiConstants.SLOT_ID_KEY, GeminiUtils.UNDEFINED_SLOT_ID);
            }
        }
        PhoneLog.d(LOG_TAG, "mSlot=" + mSlotId);
        if (mSlotId != GeminiUtils.UNDEFINED_SLOT_ID) {
            GeminiUtils.startActivity(mSlotId, mTargetPreference, mPreCfr);
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        /// M: For ALPS01062292. @{
        // Save the preference and slotId and will use these to restore correct
        // states when Activity onCreate or onRestoreInstanceState.
        if (mTargetPreference != null) {
            outState.putString(TARGET_PREFERENCE_KEY, mTargetPreference.getKey());
        }
        PhoneLog.d(LOG_TAG, "[onSaveInstanceState], mSlotId = " + mSlotId);
        outState.putInt(GeminiConstants.SLOT_ID_KEY, mSlotId);
        /// @}
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        /// M: For ALPS01062292. @{
        String targetPreferenceKey = savedInstanceState.getString(TARGET_PREFERENCE_KEY, "");
        if (!TextUtils.isEmpty(targetPreferenceKey)) {
            PreferenceScreen prefSet = mFragment.getPreferenceScreen();
            mTargetPreference = prefSet.findPreference(targetPreferenceKey);
        }
        mSlotId = savedInstanceState.getInt(GeminiConstants.SLOT_ID_KEY);
        PhoneLog.d(LOG_TAG, "[onRestoreInstanceState], mSlotId = " + mSlotId);
        /// @}
    }
}
