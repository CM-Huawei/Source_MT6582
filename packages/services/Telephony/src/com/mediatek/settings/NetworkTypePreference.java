package com.mediatek.settings;

import android.app.ActionBar;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.SystemProperties;
import android.preference.CheckBoxPreference;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.preference.PreferenceScreen;
import android.telephony.TelephonyManager;
import android.view.Menu;
import android.view.MenuItem;

import com.android.internal.telephony.TelephonyIntents;
import com.android.phone.Constants;
import com.android.phone.PhoneGlobals;
import com.android.phone.PhoneInterfaceManager;
import com.mediatek.phone.PhoneLog;
import com.mediatek.phone.gemini.GeminiUtils;
import com.android.phone.R;
import com.mediatek.telephony.SimInfoManager;
import com.mediatek.telephony.SimInfoManager.SimInfoRecord;

import java.util.List;

public class NetworkTypePreference extends android.app.Activity {

    private static final String TAG = "Settings/NetworkTypePreference";
    private static final int MENU_CONFIRM = Menu.FIRST;

    private static final String KEY_NETWORK_TYPE_2G = "key_network_type_2g";
    private static final String KEY_NETWORK_TYPE_3G = "key_network_type_3g";
    private static final String KEY_NETWORK_TYPE_4G = "key_network_type_4g";
    private CheckBoxPreference m2GCheckBoxPreference;
    private CheckBoxPreference m3GCheckBoxPreference;
    private CheckBoxPreference m4GCheckBoxPreference;
    private boolean m2GSelected;
    private boolean m3GSelected;
    private boolean m4GSelected;

    private int mAct;
    private int mSlotId;

    private boolean mAirplaneModeEnabled = false;
    private int mDualSimMode = -1;
    ///M: for adjust setting UI on VXGA device.
    public PreferenceFragment mFragment;

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            PhoneLog.d(TAG, "[action = " + action + "]");
            if (action.equals(Intent.ACTION_AIRPLANE_MODE_CHANGED)) {
                mAirplaneModeEnabled = intent.getBooleanExtra("state", false);
                updateScreen();
            } else if (action.equals(Intent.ACTION_DUAL_SIM_MODE_CHANGED)) {
                mDualSimMode = intent.getIntExtra(Intent.EXTRA_DUAL_SIM_MODE, -1);
                updateScreen();
            }  else if (action.equals(TelephonyIntents.ACTION_SIM_INFO_UPDATE)) {
                GeminiUtils.handleSimHotSwap(NetworkTypePreference.this, mSlotId);
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ///M: for adjust setting UI on VXGA device. @{
        mFragment = new NetworkTypeFragment();
        super.onCreate(savedInstanceState);

        getFragmentManager().beginTransaction()
                .replace(android.R.id.content, mFragment).commit();
        /// @}
    }

    ///M: for adjust setting UI on VXGA device.
    public class NetworkTypeFragment extends PreferenceFragment implements
            Preference.OnPreferenceChangeListener {

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            addPreferencesFromResource(R.xml.network_type);
            ActionBar actionBar = getActivity().getActionBar();
            if (actionBar != null) {
                actionBar.setDisplayHomeAsUpEnabled(true);
            }
            IntentFilter intentFilter = new IntentFilter(
                    Intent.ACTION_AIRPLANE_MODE_CHANGED);
            intentFilter.addAction(TelephonyIntents.ACTION_SIM_INFO_UPDATE);
            if (GeminiUtils.isGeminiSupport()) {
                intentFilter.addAction(Intent.ACTION_DUAL_SIM_MODE_CHANGED);
            }
            getActivity().registerReceiver(mReceiver, intentFilter);
            init();
        }

        @Override
        public boolean onPreferenceChange(Preference preference, Object objValue) {
            // Because onPreferenceChange() is called before setValue(); so isChecked() is last value.
            PhoneLog.d(TAG, "onPreferenceChange: " + preference);
            if (preference == m2GCheckBoxPreference) {
                m2GSelected = !m2GCheckBoxPreference.isChecked();
            } else if (preference == m3GCheckBoxPreference) {
                m3GSelected = !m3GCheckBoxPreference.isChecked();
            } else if (preference == m4GCheckBoxPreference) {
                m4GSelected = !m4GCheckBoxPreference.isChecked();
            }
            invalidateOptionsMenu();
            return true;
        }

    }

    @Override
    public void onResume() {
        super.onResume();
        mAirplaneModeEnabled = android.provider.Settings.System.getInt(getContentResolver(),
                android.provider.Settings.System.AIRPLANE_MODE_ON, -1) == 1;
        if (GeminiUtils.isGeminiSupport()) {
            mDualSimMode = android.provider.Settings.System.getInt(getContentResolver(),
                    android.provider.Settings.System.DUAL_SIM_MODE_SETTING, -1);
        }
        updateScreen();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(mReceiver);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        menu.add(0, MENU_CONFIRM, 0, R.string.save);
        menu.getItem(0).setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM | MenuItem.SHOW_AS_ACTION_WITH_TEXT);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        if (!mAirplaneModeEnabled && (mDualSimMode != 0)&& (m2GSelected || m3GSelected || m4GSelected)) {
            menu.getItem(0).setEnabled(true);
        } else {
            menu.getItem(0).setEnabled(false);
        }
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
        case MENU_CONFIRM:
            passResult();
            break;
        case android.R.id.home:
            break;
        default:
            break;
        }
        finish();
        return super.onOptionsItemSelected(item);
    }

    private void init() {
        m2GCheckBoxPreference = (CheckBoxPreference)mFragment.findPreference(KEY_NETWORK_TYPE_2G);
        m3GCheckBoxPreference = (CheckBoxPreference)mFragment.findPreference(KEY_NETWORK_TYPE_3G);
        m4GCheckBoxPreference = (CheckBoxPreference)mFragment.findPreference(KEY_NETWORK_TYPE_4G);

        if (m2GCheckBoxPreference != null) {
            m2GCheckBoxPreference.setOnPreferenceChangeListener((Preference.OnPreferenceChangeListener)mFragment);
        }
        if (m3GCheckBoxPreference != null) {
            m3GCheckBoxPreference.setOnPreferenceChangeListener((Preference.OnPreferenceChangeListener)mFragment);
        }
        if (m4GCheckBoxPreference != null) {
            m4GCheckBoxPreference.setOnPreferenceChangeListener((Preference.OnPreferenceChangeListener)mFragment);
        }

        Intent intent = getIntent();
        mSlotId = intent.getIntExtra(NetworkEditor.PLMN_SLOT, -1);
        mAct = intent.getIntExtra(NetworkEditor.PLMN_SERVICE, 0);

        PhoneLog.d(TAG, "init(): mSlotId = " + mSlotId + ", mAct = " + mAct);
        convertAct2Selections(mAct);

        if (!is4GSimCard(mSlotId)) {
            PhoneLog.d(TAG, "init(): the baseband of the sim card do not contain 4G. remove 4G CheckBox item.");
            PreferenceScreen prefSet = mFragment.getPreferenceScreen();
            prefSet.removePreference(m4GCheckBoxPreference);
        }
    }

    private void updateScreen() {
        m2GCheckBoxPreference.setChecked(m2GSelected);
        m3GCheckBoxPreference.setChecked(m3GSelected);
        m4GCheckBoxPreference.setChecked(m4GSelected);
        if (!mAirplaneModeEnabled && (mDualSimMode != 0)) {
            setCheckBoxEnable(true);
        } else {
            setCheckBoxEnable(false);
        }
        invalidateOptionsMenu();
    }

    private void setCheckBoxEnable(boolean enabled) {
        m2GCheckBoxPreference.setEnabled(enabled);
        m3GCheckBoxPreference.setEnabled(enabled);
        m4GCheckBoxPreference.setEnabled(enabled);
    }

    /**
     * pass the selections to NetworkEditor.
     */
    private void passResult() {
        int act = convertSelections2Act();
        Intent intent = new Intent(this, NetworkEditor.class);
        intent.putExtra(NetworkEditor.PLMN_SERVICE, act);
        setResult(RESULT_OK, intent);
        dumpInfo();
    }

    /**
     * parse Network Ril to flags.
     * <bit3, bit2, bit1, bit0> --> <E-UTRAN_ACT, UTRAN_ACT, GSM_COMPACT_ACT, GSM_ACT> --> <4G, 3G, not use, 2G>
     * @param act
     */
    private void convertAct2Selections(int act) {
        if (act > NetworkEditor.RIL_2G_3G_4G || act < NetworkEditor.RIL_2G) {
            return;
        }
        m2GSelected = (act & NetworkEditor.RIL_2G) != 0;
        m3GSelected = (act & NetworkEditor.RIL_3G) != 0;
        m4GSelected = (act & NetworkEditor.RIL_4G) != 0;
    }

    private int convertSelections2Act() {
        int result = 0;
        if (m2GSelected) {
            result += NetworkEditor.RIL_2G;
        }
        if (m3GSelected) {
            result += NetworkEditor.RIL_3G;
        }
        if (m4GSelected) {
            result += NetworkEditor.RIL_4G;
        }
        return result;
    }

    /**
     * judge baseband of the sim card is 4G or not.
     * @param slot
     * @return
     */
    private boolean is4GSimCard(int slot) {
        boolean is4GSimCard = false;
        if (GeminiUtils.getBaseband(slot) >= GeminiUtils.MODEM_MASK_LTE) {
            is4GSimCard = true;
        }
        return is4GSimCard;
    }

    private void dumpInfo() {
        PhoneLog.d(TAG, "------------Dump NetworkType Info begin-------------");
        PhoneLog.d(TAG, "init(): mAct: " + mAct);
        PhoneLog.d(TAG, "init(): mSlotId: " + mSlotId);
        PhoneLog.d(TAG, "init(): is4GSimCard(mSlotId) : " + is4GSimCard(mSlotId));
        PhoneLog.d(TAG, "passResult(): m2GSelected: " + m2GSelected);
        PhoneLog.d(TAG, "passResult(): m3GSelected: " + m3GSelected);
        PhoneLog.d(TAG, "passResult(): m4GSelected: " + m4GSelected);
        PhoneLog.d(TAG, "passResult(): act: " + convertSelections2Act());
        PhoneLog.d(TAG, "------------Dump NetworkType Info end---------------");
    }

}
