package com.mediatek.gemini;

import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.app.Dialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.preference.EditTextPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.PreferenceGroup;
import android.preference.PreferenceScreen;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.Toast;

import com.android.internal.telephony.ITelephony;
import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.TelephonyIntents;
import com.android.settings.R;
import com.android.settings.SettingsPreferenceFragment;
import com.android.settings.Utils;

import com.mediatek.common.featureoption.FeatureOption;
import com.mediatek.common.telephony.ITelephonyEx;
import com.mediatek.gemini.simui.CommonUtils;
import com.mediatek.settings.OobeUtils;
import com.mediatek.settings.ext.ISettingsMiscExt;
import com.mediatek.settings.ext.ISimManagementExt;
import com.mediatek.telephony.SimInfoManager;
import com.mediatek.telephony.SimInfoManager.SimInfoRecord;
import com.mediatek.telephony.TelephonyManagerEx;
import com.mediatek.xlog.Xlog;

import java.util.List;

public class SimInfoEditor extends SettingsPreferenceFragment implements
        OnPreferenceChangeListener, TextWatcher {
    private static final String TAG = "SimInfoEditor";
    private static final int DIALOG_SIM_NAME_DUP = 1010;
    private long mSimID;
    private static final String KEY_SIM_NAME = "sim_name";
    private static final String KEY_SIM_NUMBER = "sim_number";
    private static final String KEY_SIM_COLOR = "sim_color";
    private static final String KEY_SIM_NUMBER_FORMAT = "sim_number_format";
    private static final String KEY_SIM_STATUS = "status_info";
    private static final int NAME_EXISTED = -2;
    private static final int TYPE_NAME = 0;
    private static final int TYPE_COLOR = 1;
    private static final int TYPE_NUMBER = 2;
    private static final int TYPE_FORMAT = 3;
    private static final int SLOT_ALL = -1;

    private static String[] sArrayNumFormat;
    private static String sNotSet;
    private ListPreference mSimNumberFormat;
    private EditTextPreference mSimName;
    private EditTextPreference mSimNumber;
    private ColorPickerPreference mSimColor;
    private Preference mSimStatusPreference;
    private ISimManagementExt mExt;
    private ISettingsMiscExt mMiscExt;
    private ITelephonyEx mTelephonyEx;
    private ITelephony mTelephony;
    private Line1NumberTask mLine1NumberTask;
    private int mSlotId = GeminiUtils.UNDEFINED_SLOT_ID;
    private TelephonyManagerEx mTelephonyManagerEx;
    // Add to monitor airplane mode and to disable if airplane mode on
    private IntentFilter mIntentFilter;
    private BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action.equals(Intent.ACTION_AIRPLANE_MODE_CHANGED)) {
                boolean enable = intent.getBooleanExtra("state", false);
                Xlog.d(TAG, "handle air plane change");
                handleAirPlane(enable);
            } else if (action.equals(TelephonyIntents.ACTION_SIM_INFO_UPDATE)) {
                // /M: add for hot swap
                Xlog.d(TAG, "receive ACTION_SIM_INFO_UPDATE");
                List<SimInfoRecord> simList = SimInfoManager.getInsertedSimInfoList(context);
                if (simList.size() == 0) {
                    // Hot swap and no card so go to settings
                    Xlog.d(TAG, "Hot swap_simList.size()=" + simList.size());
                    GeminiUtils.goBackSettings(SimInfoEditor.this.getActivity());
                } else if (GeminiUtils.getSimSlotIdBySimInfoId(mSimID,simList) == 
                           GeminiUtils.UNDEFINED_SLOT_ID) {
                    finish();
                }
            } else if (TelephonyIntents.ACTION_SIM_INDICATOR_STATE_CHANGED.equals(action)) {
                //since the indicator changed so need to update the indicator as well
                int slotId = intent.getIntExtra(TelephonyIntents.INTENT_KEY_ICC_SLOT, -1);
                int simStatus = intent.getIntExtra(TelephonyIntents.INTENT_KEY_ICC_STATE, -1);
                Xlog.d(TAG,"sim card " + slotId + " with state = " + simStatus);
                if (slotId != -1 && simStatus > 0) {
                    Xlog.d(TAG, "handle radio off status");
                    handleRadioOff(slotId, simStatus);
                }
            }
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        sNotSet = getResources().getString(R.string.apn_not_set);
        Bundle extras = OobeUtils.getSimInfoExtra(this);
        if (extras != null) {
            mSimID = extras.getLong(GeminiUtils.EXTRA_SIMID, -1);
        }
        SimInfoRecord simInfo = SimInfoManager.getSimInfoById(getActivity(), mSimID);
        if (simInfo != null) {
            mSlotId = simInfo.mSimSlotId;
        } else {
            Xlog.e(TAG, "SimInfoManager query failed");
            return;
        }
        
        Xlog.i(TAG, "simid is " + mSimID);
        Xlog.i(TAG, "slotid is " + mSlotId);
        sArrayNumFormat = getResources().getStringArray(
                R.array.gemini_sim_info_number_display_format_entries);
        addPreferencesFromResource(R.xml.sim_info_editor);
        mSimNumberFormat = (ListPreference) findPreference(KEY_SIM_NUMBER_FORMAT);
        mSimNumberFormat.setOnPreferenceChangeListener(this);
        mSimName = (EditTextPreference) findPreference(KEY_SIM_NAME);
        mSimName.setOnPreferenceChangeListener(this);
        mSimNumber = (EditTextPreference) findPreference(KEY_SIM_NUMBER);
        mSimNumber.setOnPreferenceChangeListener(this);
        mSimColor = (ColorPickerPreference) findPreference(KEY_SIM_COLOR);
        mSimColor.setSimID(mSimID);
        mSimColor.setOnPreferenceChangeListener(this);
        mSimStatusPreference = findPreference(KEY_SIM_STATUS);
        // /M: add for plug in
        PreferenceGroup parent = this.getPreferenceScreen();
        mExt = Utils.getSimManagmentExtPlugin(this.getActivity());
        mMiscExt = Utils.getMiscPlugin(this.getActivity());
        mExt.updateSimManagementPref(parent);
        mExt.updateSimEditorPref(this);
        mTelephonyEx = ITelephonyEx.Stub.asInterface(ServiceManager.getService("phoneEx"));
        mTelephony = ITelephony.Stub.asInterface(ServiceManager.getService("phone"));
        mTelephonyManagerEx = TelephonyManagerEx.getDefault();
        /// M: oobe 
        OobeUtils.setSimInfoView(this);

        /// M: customize color editor preference 
        mExt.customizeSimColorEditPreference(this, KEY_SIM_COLOR);
        replaceSIMString();
    }

    /**
     * M: Replace SIM to SIM/UIM
     */
    private void replaceSIMString() {
        getActivity().setTitle(mMiscExt.customizeSimDisplayString(
            getString(R.string.gemini_sim_info_title), mSlotId));
        String editNameTitle = mMiscExt.customizeSimDisplayString(
            getString(R.string.gemini_sim_info_editor_name), mSlotId);
        mSimName.setTitle(editNameTitle);
        mSimName.setDialogTitle(editNameTitle);
        String editNumTitle = mMiscExt.customizeSimDisplayString(
            getString(R.string.gemini_sim_info_editor_number), mSlotId);
        ///M: support keep phone number style
        editNumTitle = CommonUtils.phoneNumString(editNumTitle);
        mSimNumber.setTitle(editNumTitle);
        mSimNumber.setDialogTitle(editNumTitle);
    }

    @Override
    public void onResume() {
        super.onResume();
        updateInfo();
        mSimName.getEditText().addTextChangedListener(this);
        mIntentFilter = new IntentFilter(Intent.ACTION_AIRPLANE_MODE_CHANGED);
        mIntentFilter.addAction(TelephonyIntents.ACTION_SIM_INFO_UPDATE);
        mIntentFilter.addAction(TelephonyIntents.ACTION_SIM_INDICATOR_STATE_CHANGED);
        getActivity().registerReceiver(mReceiver, mIntentFilter);
    }

    @Override
    public void onPause() {
        super.onPause();
        Xlog.d(TAG, "OnPause()");
        getActivity().unregisterReceiver(mReceiver);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();    
        if (mLine1NumberTask != null) {
            mLine1NumberTask.cancel(true);
        }
    }

    // if flight mode is on,handle flight mode, else handle radio status changing.
    private void handleAirPlane(boolean isAirplaneModeOn) {
        if (isAirplaneModeOn) {
            mSimStatusPreference.setEnabled(false);
        }
    }

    @Override
    public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen,
            Preference preference) {
        if (preference.getKey().equals(KEY_SIM_STATUS)) {
            Intent it = new Intent();
            if (FeatureOption.MTK_GEMINI_SUPPORT) {
                it.setClassName("com.android.settings",
                        "com.mediatek.settings.deviceinfo.SimStatusGemini");
                if (mSlotId < 0) {
                    return false;
                }
                it.putExtra(GeminiUtils.EXTRA_SLOTID, mSlotId);
                Xlog.i(TAG, "slotid is " + mSlotId);
            } else {
                it.setClassName("com.android.settings",
                        "com.android.settings.deviceinfo.Status");
            }
            startActivity(it);
        }
        return false;
    }

    private void updateInfo() {
        SimInfoRecord siminfo = SimInfoManager.getSimInfoById(getActivity(), mSimID);
        if (siminfo != null) {
            if (siminfo.mDisplayName == null) {
                mSimName.setSummary(sNotSet);
            } else {
                mSimName.setSummary(siminfo.mDisplayName);
                mSimName.setText(siminfo.mDisplayName);
            }
            String number = getSimNumber();
            updateSimNumber(number);
            ///M: support keep phone number style          
            mSimNumber.setSummary(CommonUtils.phoneNumString(number));
            mSimColor.setInitValue(siminfo.mColor);
            int nIndex = turnNumformatValuetoIndex(siminfo.mDispalyNumberFormat);
            if (nIndex < 0) {
                return;
            }
            mSimNumberFormat.setValueIndex(nIndex);
            mSimNumberFormat.setSummary(sArrayNumFormat[nIndex]);
        }
        int simIndicator = FeatureOption.MTK_GEMINI_SUPPORT ?
                GeminiUtils.getSimIndicatorGemini(getContentResolver(), mTelephonyEx, mSlotId) :
                GeminiUtils.getSimIndicator(getContentResolver(), mTelephony);
        boolean enabled = (PhoneConstants.SIM_INDICATOR_RADIOOFF == simIndicator) ? false
                : true;
        Xlog.i(TAG, "updateInfo simStatusPreference:  " + enabled);
        mSimStatusPreference.setEnabled(enabled);
    }

    private int turnNumformatValuetoIndex(int value) {
        if (value == 0) {
            return 2;
        }
        return (value - 1);
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object objValue) {
        final String key = preference.getKey();
        if (KEY_SIM_NAME.equals(key)) {
            Editable textName = mSimName.getEditText().getText();
            if (textName != null) {
                String name = mSimName.getEditText().getText().toString();
                SimInfoRecord siminfo = SimInfoManager.getSimInfoById(getActivity(), mSimID);
                Xlog.i(TAG, "name is " + name);
                if ((siminfo != null) && (name != null)) {
                    if (name.equals(siminfo.mDisplayName)) {
                        return false;
                    }
                }
                int result = SimInfoManager
                        .setDisplayNameEx(getActivity(), name, mSimID, SimInfoManager.USER_INPUT);

                Xlog.i(TAG, "result is " + result);
                if (result > 0) {
                    // >0 means set successfully
                    mSimName.setSummary(name);
                    Intent intent = new Intent(Intent.SIM_SETTINGS_INFO_CHANGED);
                    intent.putExtra("simid", mSimID);
                    intent.putExtra("type", TYPE_NAME);
                    getActivity().sendBroadcast(intent);
                } else {

                    if (result == NAME_EXISTED) {
                        showDialog(DIALOG_SIM_NAME_DUP);
                    }

                    if ((siminfo != null) && (siminfo.mDisplayName != null)) {
                        mSimName.setText(siminfo.mDisplayName);

                    }
                    return false;
                }
            }

        } else if (KEY_SIM_COLOR.equals(key)) {
            if (SimInfoManager.setColor(getActivity(),
                    ((Integer) objValue).intValue(), mSimID) > 0) {
                Xlog.i(TAG, "set color succeed " + objValue);
                Intent intent = new Intent(Intent.SIM_SETTINGS_INFO_CHANGED);
                intent.putExtra("simid", mSimID);
                intent.putExtra("type", TYPE_COLOR);
                getActivity().sendBroadcast(intent);
            }
        } else if (KEY_SIM_NUMBER.equals(key)) {
            Editable textNumber = mSimNumber.getEditText().getText();
            if (textNumber != null) {
                Xlog.i(TAG, "textNumber != null ");
                ///M: support keep phone number style
                setSimNumber(CommonUtils.phoneNumString(textNumber.toString()));

            }

        } else if (KEY_SIM_NUMBER_FORMAT.equals(key)) {

            int value = Integer.parseInt((String) objValue);
            Xlog.i(TAG, "KEY_SIM_NUMBER_FORMAT is " + value);

            if (value < 0) {
                return false;
            }
            if (SimInfoManager.setDispalyNumberFormat(getActivity(), value, mSimID) > 0) {

                Xlog.i(TAG, "set format succeed " + value);

                int nIndex = turnNumformatValuetoIndex(value);

                mSimNumberFormat.setSummary(sArrayNumFormat[nIndex]);
                Intent intent = new Intent(Intent.SIM_SETTINGS_INFO_CHANGED);
                intent.putExtra("simid", mSimID);
                intent.putExtra("type", TYPE_FORMAT);
                getActivity().sendBroadcast(intent);
            }
        }

        return true;
    }

    @Override
    public void afterTextChanged(Editable arg0) {

    }

    @Override
    public void beforeTextChanged(CharSequence s, int start, int count,
            int after) {
        // TODO Auto-generated method stub

    }

    @Override
    public void onTextChanged(CharSequence s, int start, int before, int count) {
        // TODO Auto-generated method stub
        Dialog d = mSimName.getDialog();
        if (d instanceof AlertDialog) {
            ((AlertDialog) d).getButton(AlertDialog.BUTTON_POSITIVE)
                    .setEnabled(s.length() > 0);
        }

    }

    @Override
    public Dialog onCreateDialog(int id) {

        Builder builder = new AlertDialog.Builder(getActivity());
        AlertDialog alertDlg;
        switch (id) {
        case DIALOG_SIM_NAME_DUP:
            builder.setTitle(getResources().getString(
                    R.string.gemini_sim_info_editor_name_dup_title));
            builder.setIcon(com.android.internal.R.drawable.ic_dialog_alert);
            /// M: CT customization Replace SIM to SIM/UIM @{
            builder.setMessage(mMiscExt.customizeSimDisplayString(
                    getString(R.string.gemini_sim_info_editor_name_dup_msg), SLOT_ALL));
            /// @}
            builder.setPositiveButton(android.R.string.ok,
                    new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog,
                                int whichButton) {
                            // TODO Auto-generated method stub

                        }
                    });
            alertDlg = builder.create();
            return alertDlg;

        default:
            return null;
        }
    }

    private void setSimNumber(String number) {
        mLine1NumberTask = new Line1NumberTask();
        mLine1NumberTask.execute(number);
    }

    private String getSimNumber() {
        String simNumber = null;
        simNumber = mTelephonyManagerEx.getLine1Number(mSlotId);
        Xlog.d(TAG, "simNumber = " + simNumber);
        return simNumber;
    }
    
    private void updateSimNumber(String simNumber) {
        if (simNumber == null || simNumber.isEmpty()) {
            simNumber = sNotSet;
            ///M: support keep phone number style
            mSimNumber.setText(CommonUtils.phoneNumString(""));
        } else {
            ///M: support keep phone number style
            mSimNumber.setText(CommonUtils.phoneNumString(simNumber));
        }
    }

    class Line1NumberTask extends AsyncTask<String, Integer, String> {  
        private static final int SUCCESS = 1;
        private static final int FAIL = 0;

        @Override  
        protected void onPreExecute() {  
            super.onPreExecute();  
            //mSimNumber.setEnabled(false);
        }  
                              
        @Override  
        protected String doInBackground(String... number) {
            int result = FAIL;
            try {
                result = mTelephonyEx.setLine1Number(null, number[0], mSlotId);
            } catch (RemoteException e) {
                Xlog.d(TAG, "get line 1 number error");
            }
            publishProgress(result);

            Xlog.i(TAG, "set number result = " + result);
            return getSimNumber();
        }  
                              
        @Override  
        protected void onProgressUpdate(Integer... result) {
            super.onProgressUpdate(result);  
            if (result[0] == SUCCESS) {
                Toast.makeText(getActivity(), R.string.band_mode_succeeded, Toast.LENGTH_SHORT).show();
                Intent intent = new Intent(Intent.SIM_SETTINGS_INFO_CHANGED);
                intent.putExtra("simid", mSimID);
                intent.putExtra("type", TYPE_NUMBER);
                getActivity().sendBroadcast(intent);
            } else {
                Toast.makeText(getActivity(), R.string.band_mode_failed, Toast.LENGTH_SHORT).show();
            }
        }  

        @Override  
        protected void onPostExecute(String number) {
            super.onPostExecute(number);  
            //mSimNumber.setEnabled(true);
            if (!isCancelled()) {
                ///M: support keep phone number style
                mSimNumber.setSummary(CommonUtils.phoneNumString(number));
                updateSimNumber(number);
            }
        }  

        @Override  
        protected void onCancelled() {
            //mSimNumber.setEnabled(true);
        }
    }  

    // if flight mode is on, we don't handle radio status changing.
    private void handleRadioOff(int slotId, int simStatus) {
        Xlog.d(TAG,"currentSlotId :  " + mSlotId);
        boolean isAirplaneModeOn = Settings.System.getInt(getContentResolver(),
                Settings.System.AIRPLANE_MODE_ON, -1) == 1;
        if (isAirplaneModeOn) {
            mSimStatusPreference.setEnabled(false);
        } else if (mSlotId == slotId) {
            boolean enabled = (PhoneConstants.SIM_INDICATOR_RADIOOFF == simStatus) ? false : true;
            mSimStatusPreference.setEnabled(enabled);
            Xlog.d(TAG, "radio status: " + enabled);
        }
    }
}
