package com.mediatek.settings;

import android.app.ActionBar;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.preference.EditTextPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.PreferenceFragment;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.MenuItem;

import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.TelephonyIntents;
import com.android.phone.R;

import com.mediatek.phone.GeminiConstants;
import com.mediatek.phone.PhoneLog;
import com.mediatek.phone.gemini.GeminiUtils;
import com.mediatek.telephony.SimInfoManager;
import com.mediatek.telephony.SimInfoManager.SimInfoRecord;

import java.util.List;

public class IpPrefixPreference extends Activity implements TextWatcher {
    private static final String IP_PREFIX_NUMBER_EDIT_KEY = "button_ip_prefix_edit_key";
    private static final String TAG = "IpPrefixPreference";
    private EditTextPreference mButtonIpPrefix = null;

    // the default slot Id 0;
    private int mSlotId = 0;

    ///M: add for hot swap {
    private IntentFilter mIntentFilter;
    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            PhoneLog.d(TAG, "action: " + action);
            if (action.equals(TelephonyIntents.ACTION_SIM_INFO_UPDATE)) {
                ///M: add for hot swap {
                GeminiUtils.handleSimHotSwap(IpPrefixPreference.this, mSlotId);
                ///@}
            }
        }
    };
    ///@}
    ///M: for adjust setting UI on VXGA device.
    public PreferenceFragment mFragment;

    protected void onCreate(Bundle icicle) {

        super.onCreate(icicle);
        ///M: for adjust setting UI on VXGA device.@{
        mFragment = new IpPreferenceFragment();
        getFragmentManager().beginTransaction()
                .replace(android.R.id.content, mFragment).commit();
        /// @}
    }
    ///M: for adjust setting UI on VXGA device.
    public class IpPreferenceFragment extends PreferenceFragment implements
            OnPreferenceChangeListener {

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            addPreferencesFromResource(R.xml.ip_prefix_setting);
            mButtonIpPrefix = (EditTextPreference)this.findPreference(IP_PREFIX_NUMBER_EDIT_KEY);
            mButtonIpPrefix.setOnPreferenceChangeListener(this);

            initSlotId();
            ActionBar actionBar = getActivity().getActionBar();
            if (actionBar != null) {
                // android.R.id.home will be triggered in onOptionsItemSelected()
                actionBar.setDisplayHomeAsUpEnabled(true);
            }
            registerCallBacks();
        }

        public boolean onPreferenceChange(Preference preference, Object newValue) {
            mButtonIpPrefix.setSummary(newValue.toString());
            mButtonIpPrefix.setText(newValue.toString());
            if (newValue == null || "".equals(newValue)) {
                mButtonIpPrefix.setSummary(R.string.ip_prefix_edit_default_sum);
            }
            saveIpPrefix(newValue.toString());
            return false;
        }
    }

    protected void onResume() {
        super.onResume();
        String preFix = this.getIpPrefix(mSlotId);
        PhoneLog.d(TAG, "preFix: " + preFix);
        if ((preFix != null) && (!"".equals(preFix))) {
            mButtonIpPrefix.setSummary(preFix);
            mButtonIpPrefix.setText(preFix);
        } else {
            mButtonIpPrefix.setSummary(R.string.ip_prefix_edit_default_sum);
            mButtonIpPrefix.setText("");
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

    private void saveIpPrefix(String str) {
        PhoneLog.d(TAG, "save str: " + str);
        String key = getIpPrefixKey();
        if (!Settings.System.putString(this.getContentResolver(), key, str)) {
            Log.d("IpPrefixPreference", "Store ip prefix error!");
        }
    }

    private String getIpPrefix(int slot) {
        String key = getIpPrefixKey();
        return Settings.System.getString(this.getContentResolver(),key);
    }
    
    public void beforeTextChanged(CharSequence s, int start,
            int count, int after) {
        
    }
    
    public void onTextChanged(CharSequence s, int start, int before, int count) {
        
    }
    
    public void afterTextChanged(Editable s) {
        
    }

    @Override
    protected void onDestroy() {
        // TODO Auto-generated method stub
        super.onDestroy();
        ///M:add for hot swap {
        unregisterReceiver(mReceiver);
        ///@}
    }

    private void initSlotId() {
        if (GeminiUtils.isGeminiSupport()) {
            mSlotId = getIntent().getIntExtra(GeminiConstants.SLOT_ID_KEY, GeminiUtils.UNDEFINED_SLOT_ID);
            PhoneLog.d(TAG,"[initSlotId][mSlotId = " + mSlotId + "]");
            SimInfoRecord siminfo = SimInfoManager.getSimInfoBySlot(this, mSlotId);
            if (siminfo != null) {
                setTitle(siminfo.mDisplayName);
            }
        }
    }

    private void registerCallBacks() {
        ///M: add for hot swap {
        mIntentFilter = new IntentFilter(TelephonyIntents.ACTION_SIM_INFO_UPDATE);
        registerReceiver(mReceiver, mIntentFilter);
        registerReceiver(mReceiver, mIntentFilter);
        ///@}
    }

    /**
     * the prefix value key depends on simId.
     * @return
     */
    private String getIpPrefixKey() {
        String key = "ipprefix";
        SimInfoRecord info = SimInfoManager.getSimInfoBySlot(this, mSlotId);
        if (info != null) {
            key += Long.valueOf(info.mSimInfoId).toString();
        }
        PhoneLog.d(TAG, "getIpPrefixKey key : " + key + ", slotId: " + mSlotId);
        return key;
    }

}
