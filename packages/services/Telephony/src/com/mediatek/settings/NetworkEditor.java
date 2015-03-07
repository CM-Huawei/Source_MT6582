package com.mediatek.settings;

import android.app.ActionBar;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.preference.EditTextPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.preference.PreferenceScreen;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Menu;
import android.view.MenuItem;
import android.view.WindowManager;
import android.widget.EditText;

import com.android.internal.telephony.TelephonyIntents;
import com.android.phone.Constants;
import com.android.phone.PhoneGlobals;
import com.android.phone.R;

import com.mediatek.phone.gemini.GeminiUtils;
import com.mediatek.phone.wrapper.PhoneWrapper;
import com.mediatek.phone.wrapper.TelephonyManagerWrapper;
import com.mediatek.phone.PhoneLog;

import java.util.List;

public class NetworkEditor extends android.app.Activity 
        implements TextWatcher {

    private static final String TAG = "Settings/NetworkEditor";
    private static final int MENU_DELETE = Menu.FIRST;
    private static final int MENU_SAVE = Menu.FIRST + 1;
    private static final int MENU_DISCARD = Menu.FIRST + 2;
    private static final int DIALOG_NETWORK_ID = 0;

    private static final String BUTTON_PRIORITY_KEY = "priority_key";
    private final static String KEY_NETWORK_TYPE = "key_network_type";
    private static final String BUTTON_NETWORK_ID_KEY = "network_id_key";

    public static final String PLMN_NAME = "plmn_name";
    public static final String PLMN_CODE = "plmn_code";
    public static final String PLMN_PRIORITY = "plmn_priority";
    public static final String PLMN_SERVICE = "plmn_service";
    public static final String PLMN_SIZE = "plmn_size";
    public static final String PLMN_ADD = "plmn_add";
    public static final String PLMN_SLOT = "plmn_slot";

    public static final int RESULT_MODIFY = 100;
    public static final int RESULT_DELETE = 200;

    private static final int INDEX_2G = 0;
    private static final int INDEX_3G = 1;
    private static final int INDEX_4G = 2;
    private static final int INDEX_2G_3G = 3;
    private static final int INDEX_2G_4G = 4;
    private static final int INDEX_3G_4G = 5;
    private static final int INDEX_2G_3G_4G = 6;

    public static final int RIL_NONE = 0x0;
    public static final int RIL_NONE2 = 0x2;
    public static final int RIL_2G = 0x1;
    public static final int RIL_3G = 0x4;
    public static final int RIL_4G = 0x8;
    public static final int RIL_2G_3G = 0x5;
    public static final int RIL_2G_4G = 0x9;
    public static final int RIL_3G_4G = 0xC;
    public static final int RIL_2G_3G_4G = 0xD;

    private Preference mNetworkId = null;
    private EditTextPreference mPriority = null;
    private Preference mNetworkMode = null;

    private String mNotSet = null;
    private String mPLMNName;
    private boolean mAirplaneModeEnabled = false;
    private int mDualSimMode = -1;
    private IntentFilter mIntentFilter;
    private int mSlotId;
    private int mAct;
    private boolean mActSupport = true;
    private EditText mNetworkIdText;
    private AlertDialog mIdDialog = null;
    // Add for ALPS00759515 keep the editing NetworkInfo
    private NetworkInfo mNetworkInfo;
    ///M: for adjust setting UI on VXGA device.
    public PreferenceFragment mFragment;

    private PhoneStateListener mPhoneStateListener = new PhoneStateListener() {
        @Override
        public void onCallStateChanged(int state, String incomingNumber) {
            super.onCallStateChanged(state, incomingNumber);
            switch(state) {
            case TelephonyManager.CALL_STATE_IDLE:
                setScreenEnabled();
                break;
            default:
                break;
            }
        }
    };

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction(); 
            if (action.equals(Intent.ACTION_AIRPLANE_MODE_CHANGED)) {
                mAirplaneModeEnabled = intent.getBooleanExtra("state", false);
                setScreenEnabled();
            } else if (action.equals(Intent.ACTION_DUAL_SIM_MODE_CHANGED)) {
                mDualSimMode = intent.getIntExtra(Intent.EXTRA_DUAL_SIM_MODE, -1);
                setScreenEnabled();
            }  else if (action.equals(TelephonyIntents.ACTION_SIM_INFO_UPDATE)) {
                ///M: add for hot swap {
                GeminiUtils.handleSimHotSwap(NetworkEditor.this, mSlotId);
                ///@}
            }
        }
    };

    private OnClickListener mNetworkIdListener = new OnClickListener() {
        @Override
        public void onClick(DialogInterface dialog, int which) {
            if (which == DialogInterface.BUTTON_POSITIVE) {
                /// M: ALPS00726774 @{
                // save number in order to ust it on activity re-onresume.
                mNetworkInfo.setNetworkId(checkNull(mNetworkIdText.getText().toString()));
                mNetworkId.setSummary(checkNull(mNetworkIdText.getText().toString()));
                // for ALPS01021086
                // invalidate the menu here, or the "save" item will not be enabled
                invalidateOptionsMenu();
                /// @}
            }
        }
    };

    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        ///M: for adjust setting UI on VXGA device. @{
        mFragment = new NetworkEditorFragment();
        getFragmentManager().beginTransaction()
                .replace(android.R.id.content, mFragment).commit();
        /// @}
    }

    ///M: for adjust setting UI on VXGA device.
    public class NetworkEditorFragment extends PreferenceFragment
            implements Preference.OnPreferenceChangeListener {

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);

            addPreferencesFromResource(R.xml.plmn_editor);
            mNotSet = getResources().getString(
                    R.string.voicemail_number_not_set);
            mNetworkId = (Preference) findPreference(BUTTON_NETWORK_ID_KEY);
            mPriority = (EditTextPreference) findPreference(BUTTON_PRIORITY_KEY);
            mNetworkMode = (Preference) findPreference(KEY_NETWORK_TYPE);
            mPriority.setOnPreferenceChangeListener(this);
            TelephonyManagerWrapper.listen(mPhoneStateListener,
                    PhoneStateListener.LISTEN_CALL_STATE,
                    PhoneWrapper.UNSPECIFIED_SLOT_ID);
            mIntentFilter = new IntentFilter(
                    Intent.ACTION_AIRPLANE_MODE_CHANGED);
            if (GeminiUtils.isGeminiSupport()) {
                mIntentFilter.addAction(Intent.ACTION_DUAL_SIM_MODE_CHANGED);
            }
            mIntentFilter.addAction(TelephonyIntents.ACTION_SIM_INFO_UPDATE);
            mNetworkInfo = new NetworkInfo();
            getActivity().registerReceiver(mReceiver, mIntentFilter);
            ActionBar actionBar = getActivity().getActionBar();
            if (actionBar != null) {
                // android.R.id.home will be triggered in
                // onOptionsItemSelected()
                actionBar.setDisplayHomeAsUpEnabled(true);
            }
        }

        public boolean onPreferenceChange(Preference preference, Object objValue) {
            String value = objValue.toString();
            if (preference == mPriority) {
                int priority = 0;
                try {
                    priority = Integer.parseInt(String.valueOf(mPriority.getSummary()));
                } catch (NumberFormatException e) {
                    PhoneLog.d(TAG, "onPreferenceChange new value for priority error");
                }
                mNetworkInfo.setPriority(priority);
                mPriority.setSummary(checkNull(value));
            }
            return true;
        }

        @Override
        public boolean onPreferenceTreeClick(PreferenceScreen screen, Preference preference) {
            if (preference == mNetworkId) {
                getActivity().removeDialog(DIALOG_NETWORK_ID);
                getActivity().showDialog(DIALOG_NETWORK_ID);
                validate();
            } else if (preference == mNetworkMode) {
                Intent intent = createNetworkTypeIntent(preference);
                getActivity().startActivityForResult(intent, 0);
                return true;
            }
            return super.onPreferenceTreeClick(screen, preference);
        }
    }

    protected void onResume() {
        super.onResume();
        createNetworkInfo(getIntent());
        mAirplaneModeEnabled = android.provider.Settings.System.getInt(getContentResolver(),
                android.provider.Settings.System.AIRPLANE_MODE_ON, -1) == 1;
        if (GeminiUtils.isGeminiSupport()) {
            mDualSimMode = android.provider.Settings.System.getInt(getContentResolver(),
                    android.provider.Settings.System.DUAL_SIM_MODE_SETTING, -1);
        }
        setScreenEnabled();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(mReceiver);
        TelephonyManagerWrapper.listen(mPhoneStateListener, PhoneStateListener.LISTEN_NONE,
                PhoneWrapper.UNSPECIFIED_SLOT_ID);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        if (!getIntent().getBooleanExtra(PLMN_ADD, false)) {
            menu.add(0, MENU_DELETE, 0, com.android.internal.R.string.delete);
        }
        menu.add(0, MENU_SAVE, 0, R.string.save);
        menu.add(0, MENU_DISCARD, 0, com.android.internal.R.string.cancel);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        boolean isShouldEnabled = false;
        boolean isIdle = (TelephonyManagerWrapper.getCallState(PhoneWrapper.UNSPECIFIED_SLOT_ID) == TelephonyManager.CALL_STATE_IDLE);
        isShouldEnabled = isIdle && (!mAirplaneModeEnabled) && (mDualSimMode != 0);
        boolean isEmpty = mNotSet.equals(mNetworkId.getSummary()) || mNotSet.equals(mPriority.getSummary());
        if (menu != null) {
            menu.setGroupEnabled(0, isShouldEnabled);
            if (getIntent().getBooleanExtra(PLMN_ADD, true)) {
                menu.getItem(0).setEnabled(isShouldEnabled && !isEmpty);
            } else {
                menu.getItem(1).setEnabled(isShouldEnabled && !isEmpty);
            }
        }
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
        case MENU_DELETE:
            setRemovedNetwork();
            break;
        case MENU_SAVE:
            validateAndSetResult();
            break;
        case MENU_DISCARD:
            break;
        case android.R.id.home:
            finish();
            return true;
        default:
            break;
        }
        finish();
        return super.onOptionsItemSelected(item);
    }

    private void validateAndSetResult() {
        Intent intent = new Intent(this, PLMNListPreference.class);
        setResult(RESULT_MODIFY, intent);
        genNetworkInfo(intent);
    }

    private void genNetworkInfo(Intent intent) {
        intent.putExtra(NetworkEditor.PLMN_NAME, checkNotSet(mPLMNName));
        intent.putExtra(NetworkEditor.PLMN_CODE, mNetworkId.getSummary());
        int priority = 0;
        int size = getIntent().getIntExtra(PLMN_SIZE, 0);
        try {
            priority = Integer.parseInt(String.valueOf(mPriority.getSummary()));
        } catch (NumberFormatException e) {
            PhoneLog.d(TAG, "parse value of basband error");
        }
        if (getIntent().getBooleanExtra(PLMN_ADD, false)) {
            if (priority > size) {
                priority = size;
            }
        } else {
            if (priority >= size) {
                priority = size - 1;
            }
        }
        intent.putExtra(NetworkEditor.PLMN_PRIORITY, priority);
        /// FroALPS00759515 Here we change the preference value again,
        //  because the priority here may is not the same as the value,
        //  which has been stored in onPreferenceChange().
        commitPreferenceStringValue(BUTTON_PRIORITY_KEY, String.valueOf(priority));
        try {
            intent.putExtra(NetworkEditor.PLMN_SERVICE, mAct);
        } catch (NumberFormatException e) {
            intent.putExtra(NetworkEditor.PLMN_SERVICE, covertApNW2Ril(0));
        }
    }

    private void setRemovedNetwork() {
        Intent intent = new Intent(this, PLMNListPreference.class);
        setResult(RESULT_DELETE, intent);
        genNetworkInfo(intent);
    }

    /**
     * convert Network Ril to index of plmn_prefer_network_type_choices.
     * <bit3, bit2, bit1, bit0> --> <E-UTRAN_ACT, UTRAN_ACT, GSM_COMPACT_ACT, GSM_ACT> --> <4G, 3G, not use, 2G>
     * when read from modem, bit1 may be 0 / 1; but when write, we always write it as 0.
     */
    public static int covertRilNW2Ap(int rilNW) {
        int result = INDEX_2G;
        boolean is2GEnable = (rilNW & NetworkEditor.RIL_2G) != 0;
        boolean is3GEnable = (rilNW & NetworkEditor.RIL_3G) != 0;
        boolean is4GEnable = (rilNW & NetworkEditor.RIL_4G) != 0;

        if (is2GEnable && is3GEnable && is4GEnable) {
            result = INDEX_2G_3G_4G;
        } else if (!is2GEnable && is3GEnable && is4GEnable) {
            result = INDEX_3G_4G;
        } else if (is2GEnable && !is3GEnable && is4GEnable) {
            result = INDEX_2G_4G;
        } else if (is2GEnable && is3GEnable && !is4GEnable) {
            result = INDEX_2G_3G;
        } else if (!is2GEnable && !is3GEnable && is4GEnable) {
            result = INDEX_4G;
        } else if (!is2GEnable && is3GEnable && !is4GEnable) {
            result = INDEX_3G;
        } else if (is2GEnable && !is3GEnable && !is4GEnable) {
            result = INDEX_2G;
        }

        return result;
    }

    public static int covertApNW2Ril(int modeIndex) {
        int result = 0;
        switch (modeIndex) {
        case INDEX_2G:
            result = RIL_2G;
            break;
        case INDEX_3G:
            result = RIL_3G;
            break;
        case INDEX_4G:
            result = RIL_4G;
            break;
        case INDEX_2G_3G:
            result = RIL_2G_3G;
            break;
        case INDEX_2G_4G:
            result = RIL_2G_4G;
            break;
        case INDEX_3G_4G:
            result = RIL_3G_4G;
            break;
        case INDEX_2G_3G_4G:
            result = RIL_2G_3G_4G;
            break;
        default:
            break;
        }
        return result;
    }

    private void createNetworkInfo(Intent intent) {
        mPLMNName = intent.getStringExtra(PLMN_NAME);
        updateNetWorkInfo(intent);
        mSlotId = intent.getIntExtra(PLMN_SLOT, -1);
        mAct = intent.getIntExtra(PLMN_SERVICE, 0);
        // mAct == 0 || mAct == 2 means new a plmn or modem pass us a none-set
        // value(please refer covertRilNW2Ap(...) to see its define), set 2G as default.
        if (mAct == RIL_NONE || mAct == RIL_NONE2) {
            mAct = RIL_2G;
        }
    }

    private String checkNotSet(String value) {
        if (value == null || value.equals(mNotSet)) {
            return "";
        } else {
            return value;
        }
    }

    private String checkNull(String value) {
        if (value == null || value.length() == 0) {
            return mNotSet;
        } else {
            return value;
        }
    }

    private void setScreenEnabled() {
        boolean isShouldEnabled = false;
        boolean isIdle = (TelephonyManagerWrapper.getCallState(PhoneWrapper.UNSPECIFIED_SLOT_ID) == TelephonyManager.CALL_STATE_IDLE);
        isShouldEnabled = isIdle && (!mAirplaneModeEnabled) && (mDualSimMode != 0);
        mFragment.getPreferenceScreen().setEnabled(isShouldEnabled);
        invalidateOptionsMenu();
        mNetworkMode.setEnabled(mActSupport && isShouldEnabled);
    }

    @Override
    protected void onActivityResult(final int requestCode, final int resultCode,
            final Intent intent) {
        PhoneLog.d(TAG, "onActivityResult: requestCode = " + requestCode + ", resultCode = " + resultCode);
        if (resultCode == RESULT_OK) {
            mAct = intent.getIntExtra(PLMN_SERVICE, 0);
            getIntent().putExtra(PLMN_SERVICE, mAct);
            setIntent(getIntent());
            updateNetworkType(intent);
        }
    }

    @Override
    public Dialog onCreateDialog(int id) {
        if (id == DIALOG_NETWORK_ID) {
            mNetworkIdText = new EditText(this);
            if (!mNotSet.equals(mNetworkId.getSummary())) {
                mNetworkIdText.setText(mNetworkId.getSummary());
            }
            mNetworkIdText.addTextChangedListener(this);
            mNetworkIdText.setInputType(InputType.TYPE_CLASS_NUMBER);
            mIdDialog = new AlertDialog.Builder(this)
                .setTitle(getResources().getString(R.string.network_id))
                .setView(mNetworkIdText)
                .setPositiveButton(getResources().getString(com.android.internal.R.string.ok), mNetworkIdListener)
                .setNegativeButton(getResources().getString(com.android.internal.R.string.cancel), null)
                .create();
            mIdDialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
            return mIdDialog;
        }
        return null;
    }

    public void validate() {
        int len = mNetworkIdText.getText().toString().length();
        boolean state = true;
        if (len < 5 || len > 6) {
            state = false;
        }
        if (mIdDialog != null) {
            mIdDialog.getButton(DialogInterface.BUTTON_POSITIVE).setEnabled(state);
        }
    }

    @Override
    public void afterTextChanged(Editable s) {
        validate();
    }

    @Override
    public void beforeTextChanged(CharSequence s, int start, int count,
              int after) {
        // work done in afterTextChanged
    }

    @Override
    public void onTextChanged(CharSequence s, int start, int before, int count) {
        // work done in afterTextChanged
    }

    /**
     * Add for ALPS00759515 Commit String value to Phone Preference
     * @param key
     * @param value
     */
    private void commitPreferenceStringValue(String key, String value) {
        SharedPreferences mPreferences = this.getSharedPreferences(
                Constants.PHONE_PREFERENCE_NAME, Context.MODE_WORLD_READABLE);
        SharedPreferences.Editor editor = mPreferences.edit();
        editor.putString(key, value);
        editor.apply();
    }

    /**
     * Add for ALPS00759515 update the preference screen.
     * @param intent
     */
    private void updateNetWorkInfo(Intent intent) {
        PhoneLog.d(TAG, "---updateNetWorkInfo-- " + mNetworkInfo.getPriority() + " : "
                + mNetworkInfo.getNetworkId() + " : " + mNetworkInfo.getNetWorkMode());
        // NetworkId:
        if (TextUtils.isEmpty(mNetworkInfo.getNetworkId())) {
            mNetworkInfo.setNetworkId(intent.getStringExtra(PLMN_CODE));
        }
        mNetworkId.setSummary(checkNull(mNetworkInfo.getNetworkId()));
        // Priority:
        if (mNetworkInfo.mPriority == -1) {
            mNetworkInfo.setPriority(intent.getIntExtra(PLMN_PRIORITY, 0));
        }
        mPriority.setSummary(String.valueOf(mNetworkInfo.getPriority()));
        /// M: ALPS00804766 @{
        // set value of EditTextPreference Dialog textview.
        mPriority.setText(String.valueOf(mNetworkInfo.getPriority()));
        /// @}
        // NetworkMode
        if(TextUtils.isEmpty(mNetworkInfo.getNetWorkMode())) {
            int act = intent.getIntExtra(PLMN_SERVICE, 0);
            //if act is not supported, disable mNetworkMode
            PhoneLog.d(TAG, "act = " + act);
            if (!getIntent().getBooleanExtra(PLMN_ADD, true)) {
                mActSupport = act != 0;
            }
            PhoneLog.d(TAG, "mActSupport = " + mActSupport);
            act = covertRilNW2Ap(act);
            mNetworkInfo.setNetWorkMode(getResources().getStringArray(R.array.plmn_prefer_network_type_choices)[act]);
        }
        mNetworkMode.setSummary(mNetworkInfo.getNetWorkMode());
    }

    /**
     * Add for ALPS00759515
     * Keep NetworkEditor info.
     */
    class NetworkInfo {
        private String mNetworkId;
        private int mPriority;
        private String mNetWorkMode;

        public NetworkInfo() {
            mNetworkId = null;
            mPriority = -1;
            mNetWorkMode = null;
        }

        public String getNetworkId() {
            return mNetworkId;
        }

        public void setNetworkId(String mNetworkId) {
            this.mNetworkId = mNetworkId;
        }

        public int getPriority() {
            return mPriority;
        }

        public void setPriority(int mPriority) {
            this.mPriority = mPriority;
        }

        public String getNetWorkMode() {
            return mNetWorkMode;
        }

        public void setNetWorkMode(String mNetWorkMode) {
            this.mNetWorkMode = mNetWorkMode;
        }
    }

    private Intent createNetworkTypeIntent(Preference preference) {
        Intent intent = preference.getIntent();
        PhoneLog.d(TAG, "createNetworkTypeIntent(): mSlotId = " + mSlotId + ", mAct = " + mAct);
        intent.putExtra(PLMN_SLOT, mSlotId);
        intent.putExtra(PLMN_SERVICE, mAct);
        return intent;
    }

    private void updateNetworkType(Intent intent) {
        int act = intent.getIntExtra(PLMN_SERVICE, 0);
        int index = covertRilNW2Ap(act);
        PhoneLog.d(TAG, "updateNetworkType: act = " + act + ", index = " + index);
        mNetworkInfo.setNetWorkMode(getResources().getStringArray(R.array.plmn_prefer_network_type_choices)[index]);
        mNetworkMode.setSummary(mNetworkInfo.getNetWorkMode());
    }

}
