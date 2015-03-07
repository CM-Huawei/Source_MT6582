package com.mediatek.settings;

import android.app.ActionBar;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceScreen;
import android.telephony.ServiceState;
import android.util.Log;
import android.view.MenuItem;

import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.TelephonyIntents;
import com.android.phone.PhoneGlobals;
import com.android.phone.R;
import com.android.phone.TimeConsumingPreferenceActivity;

import com.mediatek.common.featureoption.FeatureOption;
import com.mediatek.phone.gemini.GeminiUtils;
import com.mediatek.phone.wrapper.PhoneWrapper;
import com.mediatek.telephony.SimInfoManager;
import com.mediatek.telephony.SimInfoManager.SimInfoRecord;
import com.mediatek.xlog.Xlog;

import java.util.List;

public class CellBroadcastActivity extends TimeConsumingPreferenceActivity {
    private static final String BUTTON_CB_CHECKBOX_KEY     = "enable_cellBroadcast";
    private static final String BUTTON_CB_SETTINGS_KEY     = "cbsettings";
    private static final String LOG_TAG = "Settings/CellBroadcastActivity";
    int mSlotId = PhoneConstants.GEMINI_SIM_1;
    private ServiceState mServiceState;
    private Phone mPhone;

    private CellBroadcastCheckBox mCBCheckBox = null;
    private Preference mCBSetting = null;

    private boolean mAirplaneModeEnabled = false;
    private int mDualSimMode = -1;
    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            Xlog.d(LOG_TAG, "[action = " + action + "]");
            if (TelephonyIntents.ACTION_SIM_INFO_UPDATE.equals(action)) {
                setScreenEnabled();
            } else if (Intent.ACTION_AIRPLANE_MODE_CHANGED.equals(action)) {
                mAirplaneModeEnabled = intent.getBooleanExtra("state", false);
                Log.d(LOG_TAG, "mAirplaneModeEnabled: " +mAirplaneModeEnabled);
                /// M: ALPS00740653 @{
                // when airplane mode is on, the phone state must not be out of service,
                // but ,but when airplane mode is off, the phone state may be out of service,
                // so airplane mode is off, we do not enable screen until phone state is in service.
                if (mAirplaneModeEnabled) {
                    setScreenEnabled();
                }
                /// @}
            } else if (action.equals(Intent.ACTION_DUAL_SIM_MODE_CHANGED)) {
                mDualSimMode = intent.getIntExtra(Intent.EXTRA_DUAL_SIM_MODE, -1);
                setScreenEnabled();
            } else if (action.equals(TelephonyIntents.ACTION_SERVICE_STATE_CHANGED)) {
                setScreenEnabled();
            }
        }
    };

    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        addPreferencesFromResource(R.xml.cell_broad_cast);
        mSlotId = getIntent().getIntExtra(PhoneConstants.GEMINI_SIM_ID_KEY, 0);
        mPhone = PhoneGlobals.getPhone();

        mCBCheckBox = (CellBroadcastCheckBox)findPreference(BUTTON_CB_CHECKBOX_KEY);
        mCBSetting = findPreference(BUTTON_CB_SETTINGS_KEY);
        mCBCheckBox.setSummary(mCBCheckBox.isChecked() 
                ? R.string.sum_cell_broadcast_control_on : R.string.sum_cell_broadcast_control_off);

        if (null != getIntent().getStringExtra(MultipleSimActivity.SUB_TITLE_NAME)) {
            setTitle(getIntent().getStringExtra(MultipleSimActivity.SUB_TITLE_NAME));
        }
        if (mCBCheckBox != null) {
            mCBCheckBox.init(this, false, mSlotId);
        }
        /// M: ALPS00670751 @{
        registerBroadcast();
        /// @}
        ActionBar actionBar = getActionBar();
        if (actionBar != null) {
            // android.R.id.home will be triggered in onOptionsItemSelected()
            actionBar.setDisplayHomeAsUpEnabled(true);
        }
    }

    @Override
    public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen, Preference preference) {
        if (preference == mCBSetting) {
            Intent intent = new Intent(this, CellBroadcastSettings.class);
            intent.putExtra(PhoneConstants.GEMINI_SIM_ID_KEY, mSlotId);
            this.startActivity(intent);
            return true;
        }
        return false;
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

    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(mReceiver);
    }

    private void setScreenEnabled() {
        ///M: add for hot swap {
        GeminiUtils.handleSimHotSwap(this, mSlotId);
        ///@}

        /// M: ALPS00670751 @{
        // when no airplane , exist sim card and  have dual sim mode
        // set screen disable
        enableScreen();
        /// @}
    }

    @Override
    public void onResume() {
        super.onResume();
        mAirplaneModeEnabled = android.provider.Settings.System.getInt(getContentResolver(),
                android.provider.Settings.Global.AIRPLANE_MODE_ON, -1) == 1;
        if (GeminiUtils.isGeminiSupport()) {
            mDualSimMode = android.provider.Settings.System.getInt(getContentResolver(),
                    android.provider.Settings.System.DUAL_SIM_MODE_SETTING, -1);
            Log.d(LOG_TAG, "onResume(), mDualSimMode=" + mDualSimMode);
        }
        setScreenEnabled();
    }

    private void enableScreen() {
        List<SimInfoRecord> insertedSimInfoList = SimInfoManager.getInsertedSimInfoList(this);
        boolean isShouldEnabled = false;
        boolean isHasSimCard = ((insertedSimInfoList != null) && (insertedSimInfoList.size() > 0));
        isShouldEnabled = (!mAirplaneModeEnabled) && (mDualSimMode != 0) && isHasSimCard;
        getPreferenceScreen().setEnabled(isShouldEnabled);
    }

    private void registerBroadcast() {
        IntentFilter intentFilter = new IntentFilter(TelephonyIntents.ACTION_SIM_INFO_UPDATE);
        intentFilter.addAction(TelephonyIntents.ACTION_SERVICE_STATE_CHANGED);
        intentFilter.addAction(Intent.ACTION_AIRPLANE_MODE_CHANGED);
        if (FeatureOption.MTK_GEMINI_SUPPORT) {
            intentFilter.addAction(Intent.ACTION_DUAL_SIM_MODE_CHANGED);
        }
        registerReceiver(mReceiver, intentFilter);
    }

    private boolean isPhoneReady(int slotId) {
        int state = ServiceState.STATE_OUT_OF_SERVICE;
        state = PhoneWrapper.getServiceState(mPhone, slotId).getState();
        Log.d(LOG_TAG, "isPhoneReady: "  + state);
        if (state == ServiceState.STATE_IN_SERVICE) {
            return true;
        }
        return false;
    }
}
