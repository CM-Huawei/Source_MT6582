package com.mediatek.settings;

import android.app.ActionBar;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.app.StatusBarManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.DialogInterface.OnClickListener;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.ServiceManager;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.provider.Settings;
import android.telephony.ServiceState;
import android.view.MenuItem;
import android.view.Window;
import android.view.WindowManager;

import com.android.internal.telephony.ITelephony;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneFactory;
import com.android.internal.telephony.TelephonyIntents;
import com.android.internal.telephony.TelephonyIntents;
import com.android.phone.PhoneGlobals;
import com.android.phone.R;

import com.mediatek.gemini.simui.SimSelectDialogPreference;
import com.mediatek.phone.PhoneLog;
import com.mediatek.phone.PhoneFeatureConstants.FeatureOption;
import com.mediatek.phone.PhoneInterfaceManagerEx;
import com.mediatek.phone.ext.ExtensionManager;
import com.mediatek.phone.ext.SettingsExtension;
import com.mediatek.phone.gemini.GeminiUtils;
import com.mediatek.phone.wrapper.PhoneWrapper;
import com.mediatek.telephony.SimInfoManager;
import com.mediatek.telephony.SimInfoManager.SimInfoRecord;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Modem3GCapabilitySwitch extends PreferenceActivity 
        implements Preference.OnPreferenceChangeListener {

    public static final String SERVICE_LIST_KEY = "preferred_3g_service_key";
    public static final String NETWORK_MODE_KEY = "preferred_network_mode_key";

    private SimSelectDialogPreference mServiceList = null;
    private ListPreference mNetworkMode = null;
    private static final boolean DBG = true;
    private static final String TAG = "Settings/Modem3GCapabilitySwitch";
    
    PhoneInterfaceManagerEx mPhoneMgrEx = null;
    private Phone mPhone;
    private NetWorkHandler mNetworkHandler;
    private StatusBarManager mStatusBarManager = null;
    private ModemSwitchReceiver mSwitchReceiver;
    //As this activity may be destroyed and re-instance, give them a public "progress dialog"
    
    private static final long SIMID_3G_SERVICE_OFF = -1;
    private static final int SIMID_3G_SERVICE_NOT_SET = -2;

    private static final int PROGRESS_DIALOG_3G = 300;
    private static final int SWITCH_3G_TIME_OUT_MSG = 1000;
    ///For 3G switch fail time out set 1 min 
    private static final int SWITCH_3G_TIME_OUT_VALUE = 60000;

    private boolean mIsAirplaneModeOn;
    private static int sInstanceFlag = 0;
    private int mInstanceIndex = 0;
    private SettingsExtension mExtension;
    // smart 3g switch
    private boolean mIs3GSwitchManualChangeAllowed = false;
    private int mManualAllowedSlot = -1;
    private List<SimInfoRecord> mSimInfoList;

    private static final int DIALOG_3G_CAPABILITY_SWITCH = 500;
    private long mCurrent3GSim;
    private long mSelected3GSim;

    private Handler mTimerHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            if (SWITCH_3G_TIME_OUT_MSG == msg.what) {
                PhoneLog.d("TEST","3G switch time out remove the progress dialog");
                removeDialog(PROGRESS_DIALOG_3G);
                setStatusBarEnableStatus(true);
            }
        }
    }; 

    public Modem3GCapabilitySwitch() {
        mInstanceIndex = ++sInstanceFlag;
        PhoneLog.i(TAG, "Modem3GCapabilitySwitch(), instanceIndex=" + mInstanceIndex);
    }
    
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        PhoneLog.d(TAG,"onCreate()");
        addPreferencesFromResource(R.xml.service_3g_setting);
        mExtension = ExtensionManager.getInstance().getSettingsExtension();
        // init preference
        initPreference();
        // init variable
        initPhoneAnd3GSwitch();
        // init IntentFilter
        IntentFilter intentFilter = initIntentFilter();
        registerReceiver(mSwitchReceiver, intentFilter);

        ActionBar actionBar = getActionBar();
        if (actionBar != null) {
            // android.R.id.home will be triggered in onOptionsItemSelected()
            actionBar.setDisplayHomeAsUpEnabled(true);
        }
    }

    protected void onResume() {
        super.onResume();
        long simId = SIMID_3G_SERVICE_NOT_SET;
        int slot = mPhoneMgrEx.get3GCapabilitySIM();
        if (slot == SIMID_3G_SERVICE_OFF) {
            simId = slot;
        } else {
            SimInfoRecord info = SimInfoManager.getSimInfoBySlot(this, slot);
            simId = info != null ? info.mSimInfoId : SIMID_3G_SERVICE_NOT_SET;
        }
        init3GSwitchPref();
        updateSummarys(simId);
        updateNetworkAnd3GServiceStatus();
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

    private void updateNetworkAnd3GServiceStatus() {
        int slot = mPhoneMgrEx.get3GCapabilitySIM();
        PhoneLog.d(TAG, "updateNetworkMode(), 3G capability slot=" + slot);
        boolean locked =  mPhoneMgrEx.is3GSwitchLocked();
        if (mNetworkMode != null) {
            if (!locked && slot != -1 && !PhoneWrapper.isRadioOffBySlot(slot, this)) {
                mNetworkMode.setEnabled(true);
                PhoneLog.d(TAG, "Try to get preferred network mode for slot " + slot);
                PhoneWrapper.getPreferredNetworkType(mPhone,
                        mNetworkHandler.obtainMessage(NetWorkHandler.MESSAGE_GET_PREFERRED_NETWORK_TYPE), slot);
            } else {
                mNetworkMode.setEnabled(false);
                mNetworkMode.setSummary("");
            }
        }

        mServiceList.setEnabled(!mIsAirplaneModeOn && !locked);

        if (mIsAirplaneModeOn) {
            dismissDialogs();
        }
    }

    private void initPhoneAnd3GSwitch() {
        mPhone = PhoneFactory.getDefaultPhone();
        mPhoneMgrEx = PhoneGlobals.getInstance().phoneMgrEx;
        mIs3GSwitchManualChangeAllowed = mPhoneMgrEx.is3GSwitchManualChange3GAllowed();
        PhoneLog.d(TAG, "mIs3GSwitchManualChangeAllowed: " + mIs3GSwitchManualChangeAllowed);
        if (!mIs3GSwitchManualChangeAllowed) {
            mManualAllowedSlot = mPhoneMgrEx.get3GSwitchAllowed3GSlots();
            PhoneLog.d(TAG, "mManualAllowedSlot: " + mManualAllowedSlot);
        }
        mNetworkHandler = new NetWorkHandler(Modem3GCapabilitySwitch.this,mNetworkMode);

        mSwitchReceiver = new ModemSwitchReceiver();
        mIsAirplaneModeOn = Settings.System.getInt(
                getContentResolver(), Settings.System.AIRPLANE_MODE_ON, -1) == 1;
        init3GSwitchPref();
    }
    
    private IntentFilter initIntentFilter() {
        IntentFilter intentFilter = new IntentFilter(PhoneWrapper.EVENT_3G_SWITCH_LOCK_CHANGED);
        intentFilter.addAction(PhoneWrapper.EVENT_PRE_3G_SWITCH);
        intentFilter.addAction(PhoneWrapper.EVENT_3G_SWITCH_DONE);
        intentFilter.addAction(Intent.ACTION_AIRPLANE_MODE_CHANGED);
        intentFilter.addAction(TelephonyIntents.ACTION_SIM_INDICATOR_STATE_CHANGED);
        intentFilter.addAction(TelephonyIntents.ACTION_SIM_INFO_UPDATE);
        return intentFilter;
    }
    
    private void initPreference() {
        mServiceList = (SimSelectDialogPreference)findPreference(SERVICE_LIST_KEY);
        mServiceList.setOnPreferenceChangeListener(this);
        mNetworkMode = (ListPreference)findPreference(NETWORK_MODE_KEY);
        mNetworkMode.setOnPreferenceChangeListener(this);
        mExtension.removeNMOpFor3GSwitch(getPreferenceScreen(), mNetworkMode);
    }

    private void updateSummarys(long simId) {
        PhoneLog.d(TAG, "updateSummarys(), simId=" + simId);
        // restore current 3G sim ID
        mCurrent3GSim = simId;
        mServiceList.setValue(simId);
        if (simId == SIMID_3G_SERVICE_OFF) {
            if (mNetworkMode != null) {
                mNetworkMode.setSummary("");
            }
        } else if (simId == SIMID_3G_SERVICE_NOT_SET) {
            //Clear the summary
            mServiceList.setSummary("");
            mNetworkMode.setSummary("");
        } else {
            SimInfoRecord info = SimInfoManager.getSimInfoById(this, simId);
            if (info != null) {
                //if the 3G service slot is radio off, disable the network mode
                boolean isPowerOn = !PhoneWrapper.isRadioOffBySlot(info.mSimSlotId, this);
                PhoneLog.d(TAG, "updateSummarys(), SIM " + simId + " power status is " + isPowerOn);
                if (!isPowerOn) {
                    mNetworkMode.setSummary("");
                }
            }
        }
    }
    
    public void changeForNetworkMode(Object objValue) {
        mNetworkMode.setValue((String) objValue);
        int buttonNetworkMode = Integer.valueOf((String) objValue).intValue();
        int settingsNetworkMode = Settings.Global.getInt(
                mPhone.getContext().getContentResolver(),
                Settings.Global.PREFERRED_NETWORK_MODE, Phone.PREFERRED_NT_MODE);
        if (buttonNetworkMode != settingsNetworkMode) {
            showDialog(GeminiUtils.PROGRESS_DIALOG);            
            mNetworkMode.setSummary(mNetworkMode.getEntry());
            
            Settings.Global.putInt(mPhone.getContext().getContentResolver(),
                    Settings.Global.PREFERRED_NETWORK_MODE,
                    buttonNetworkMode);
            //Set the modem network mode
            int slot = mPhoneMgrEx.get3GCapabilitySIM();
            PhoneWrapper.setPreferredNetworkType(mPhone, buttonNetworkMode, mNetworkHandler.obtainMessage(NetWorkHandler.MESSAGE_SET_PREFERRED_NETWORK_TYPE), slot);
        }
    }

    public boolean onPreferenceChange(Preference preference, Object objValue) {
        if (preference == mServiceList) {
            mSelected3GSim = Long.valueOf(objValue.toString());
            if (mSelected3GSim != mCurrent3GSim) {
                if (FeatureOption.MTK_VT3G324M_SUPPORT) {
                    showDialog(DIALOG_3G_CAPABILITY_SWITCH);
                } else {
                    handleServiceSwitch(mSelected3GSim);
                }
            }
        } else if (preference == mNetworkMode) {
            changeForNetworkMode(objValue);
        }
        return true;
    }

    public Dialog onCreateDialog(int id) {
        PhoneLog.d(TAG, "Create and show the dialog[id = " + id + "]");
        Dialog dialog = null;
        switch (id) {
        case GeminiUtils.PROGRESS_DIALOG:
            ProgressDialog progress = new ProgressDialog(this);
            progress.setMessage(getResources().getString(R.string.updating_settings));
            progress.setCancelable(false);
            dialog = progress;
            break;
        case PROGRESS_DIALOG_3G:
            ProgressDialog progress3g = new ProgressDialog(this);
            progress3g.setMessage(getResources().getString(R.string.modem_switching));
            progress3g.setCancelable(false);
            Window win = progress3g.getWindow();
            WindowManager.LayoutParams lp = win.getAttributes();
            lp.flags |= WindowManager.LayoutParams.FLAG_HOMEKEY_DISPATCHED;
            win.setAttributes(lp);
            dialog = progress3g;
            break;
        case DIALOG_3G_CAPABILITY_SWITCH:
            int msgId = mSelected3GSim == SIMID_3G_SERVICE_OFF ? R.string.confirm_3g_switch_to_off
                    : R.string.confirm_3g_switch;
            AlertDialog alert = new AlertDialog.Builder(this)
                   .setTitle(android.R.string.dialog_alert_title)
                   .setPositiveButton(R.string.buttonTxtContinue, new OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            handleServiceSwitch(mSelected3GSim);
                        }
                    })
                    .setNegativeButton(R.string.cancel, new OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            mServiceList.setValue(mCurrent3GSim);
                        }
                    })
                    .setCancelable(false)
                    .setMessage(msgId)
                    .create();
            dialog = alert;
            break;
        default:
            break;
        }
        return dialog;
    }

    @Override
    protected void onPrepareDialog(int id, Dialog dialog) {
        switch (id) {
        case DIALOG_3G_CAPABILITY_SWITCH:
            int msgId = mSelected3GSim == SIMID_3G_SERVICE_OFF ? R.string.confirm_3g_switch_to_off
                    : R.string.confirm_3g_switch;
            ((AlertDialog)dialog).setMessage(getResources().getString(msgId));
            break;
        default:
            break;
        }
        super.onPrepareDialog(id, dialog);
    }

    private void clearAfterSwitch(Intent it) {
        long simId3G = SIMID_3G_SERVICE_NOT_SET;
        PhoneLog.d(TAG, "clearAfterSwitch(), remove switching dialog");
        removeDialog(PROGRESS_DIALOG_3G);
        setStatusBarEnableStatus(true);
        //the slot which supports 3g service after switch
        //then get the simid which inserted to the 3g slot
        int slot3G = it.getIntExtra(PhoneWrapper.EXTRA_3G_SIM, SIMID_3G_SERVICE_NOT_SET);
        if (slot3G == SIMID_3G_SERVICE_OFF) {
            simId3G = SIMID_3G_SERVICE_OFF;
        } else {
            SimInfoRecord info = SimInfoManager.getSimInfoBySlot(this, slot3G);
            if (info != null) {
                simId3G = info.mSimInfoId;
            }
        }
        updateSummarys(simId3G);
        updateNetworkAnd3GServiceStatus();
    }
    
    private void handleServiceSwitch(long simId) {
        if (mPhoneMgrEx.is3GSwitchLocked()) {
            PhoneLog.d(TAG, "Switch has been locked, return");
            mServiceList.setValue(mCurrent3GSim);
            return ;
        }
        PhoneLog.d(TAG, "handleServiceSwitch(" + simId + "), show switching dialog first");
        showDialog(PROGRESS_DIALOG_3G);
        setStatusBarEnableStatus(false);
        int slotId = -1;
        if (simId != -1) {
            SimInfoRecord info = SimInfoManager.getSimInfoById(this, simId);
            slotId = info == null ? -1 : info.mSimSlotId;
        }
        if (mPhoneMgrEx.set3GCapabilitySIM(slotId)) {
            PhoneLog.d(TAG, "Receive ok for the switch, and starting the waiting...");
        } else {
            PhoneLog.d(TAG, "Receive error for the switch & Dismiss switching didalog");
            removeDialog(PROGRESS_DIALOG_3G);
            setStatusBarEnableStatus(true);
            mServiceList.setValue(mCurrent3GSim);
        }
    }
    
    protected void onDestroy() {
        super.onDestroy();
        PhoneLog.d(TAG, "Instance[" + mInstanceIndex + "]." + "onDestroy()");
        
        if (mSwitchReceiver != null) {
            unregisterReceiver(mSwitchReceiver);
        }
        //restore status bar status after finish, to avoid unexpected event
        setStatusBarEnableStatus(true);
        mTimerHandler.removeMessages(SWITCH_3G_TIME_OUT_MSG);
    }
    
    /**
     * When switching modem, the status bar should be disabled
     * @param enabled
     */
    private void setStatusBarEnableStatus(boolean enabled) {
        PhoneLog.i(TAG, "setStatusBarEnableStatus(" + enabled + ")");
        if (mStatusBarManager == null) {
            mStatusBarManager = (StatusBarManager)getSystemService(Context.STATUS_BAR_SERVICE);
        }
        if (mStatusBarManager != null) {
            if (enabled) {
                mStatusBarManager.disable(StatusBarManager.DISABLE_NONE);
            } else {
                mStatusBarManager.disable(StatusBarManager.DISABLE_EXPAND |
                                          StatusBarManager.DISABLE_RECENT |
                                          StatusBarManager.DISABLE_HOME);
            }
        } else {
            PhoneLog.e(TAG, "Fail to get status bar instance");
        }
    }
    
    class ModemSwitchReceiver extends BroadcastReceiver {
        
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (PhoneWrapper.EVENT_3G_SWITCH_LOCK_CHANGED.equals(action)) {
                PhoneLog.d(TAG, "receives EVENT_3G_SWITCH_LOCK_CHANGED...");
                boolean bLocked = intent.getBooleanExtra(PhoneWrapper.EXTRA_3G_SWITCH_LOCKED, false);
                /// M: ALPS00653849 @{
                // When phone state change, we update all Items.
                updateNetworkAnd3GServiceStatus();
                /// @}
            } else if (PhoneWrapper.EVENT_PRE_3G_SWITCH.equals(action)) {
                PhoneLog.d(TAG, "Starting the switch......@" + this);
                showDialog(PROGRESS_DIALOG_3G);
                showInstanceIndex("Receive starting switch broadcast");
                setStatusBarEnableStatus(false);
                mTimerHandler.sendEmptyMessageDelayed(SWITCH_3G_TIME_OUT_MSG, SWITCH_3G_TIME_OUT_VALUE);
                if (mNetworkMode.getDialog() != null) {
                    mNetworkMode.getDialog().dismiss();
                }
            } else if (PhoneWrapper.EVENT_3G_SWITCH_DONE.equals(action)) {
                PhoneLog.d(TAG, "Done the switch......@" + this);
                showInstanceIndex("Receive switch done broadcast");
                clearAfterSwitch(intent);
                mTimerHandler.removeMessages(SWITCH_3G_TIME_OUT_MSG);
            } else if (Intent.ACTION_AIRPLANE_MODE_CHANGED.equals(action)) {
                mIsAirplaneModeOn = intent.getBooleanExtra("state", false);
                PhoneLog.d(TAG, "mIsAirplaneModeOn new  state is [" + mIsAirplaneModeOn + "]");
                removeDialog(DIALOG_3G_CAPABILITY_SWITCH);
                updateNetworkAnd3GServiceStatus();
            } else if (action.equals(TelephonyIntents.ACTION_SIM_INFO_UPDATE)) {
                PhoneLog.d(TAG, "ACTION_SIM_INFO_UPDATE received");
                removeDialog(DIALOG_3G_CAPABILITY_SWITCH);
                List<SimInfoRecord> temp = SimInfoManager.getInsertedSimInfoList(Modem3GCapabilitySwitch.this);
                if (temp.size() > 0) {
                    init3GSwitchPref();
                    long simId = SIMID_3G_SERVICE_NOT_SET;
                    int slot = mPhoneMgrEx.get3GCapabilitySIM();
                    //update summary
                    if (slot == SIMID_3G_SERVICE_OFF) {
                        simId = slot;
                    } else {
                        SimInfoRecord info = SimInfoManager.getSimInfoBySlot(Modem3GCapabilitySwitch.this, slot);
                        simId = info != null ? info.mSimInfoId : SIMID_3G_SERVICE_NOT_SET;
                    }
                    updateSummarys(simId);
                    updateNetworkAnd3GServiceStatus();
                } else {
                    finish();
                } 
            } else if (action.equals(TelephonyIntents.ACTION_SIM_INDICATOR_STATE_CHANGED)) {
                PhoneLog.d(TAG, "receives ACTION_SIM_INDICATOR_STATE_CHANGED...");
                int state = intent.getIntExtra(TelephonyIntents.INTENT_KEY_ICC_STATE, -1);
                int slotId = intent.getIntExtra(TelephonyIntents.INTENT_KEY_ICC_SLOT, -1);
                PhoneLog.d(TAG,"state = " + state + " slotId = " + slotId);
                updateNetworkAnd3GServiceStatus();
                mServiceList.updateSimIndicator(slotId, state);
            }
        }
    }
    
    private void showInstanceIndex(String msg) {
        if (DBG) {
            PhoneLog.i(TAG, "Instance[" + mInstanceIndex + "]: " + msg);
        }
    }

    private void init3GSwitchPref() {
        mSimInfoList = SimInfoManager.getInsertedSimInfoList(this);
        Collections.sort(mSimInfoList, new GeminiUtils.SIMInfoComparable());
        List<Integer> simIndicatorList = new ArrayList<Integer>();
        List<Long> entryValues = new ArrayList<Long>();
        List<Boolean> itemStatus = new ArrayList<Boolean>();
        for (SimInfoRecord siminfo : mSimInfoList) {
            simIndicatorList.add(GeminiUtils.getSimIndicator(this, siminfo.mSimSlotId));
            entryValues.add(siminfo.mSimInfoId);
            // for smart 3g switch
            itemStatus.add(GeminiUtils.is3GSwitchManualEnableSlot(
                    siminfo.mSimSlotId, mIs3GSwitchManualChangeAllowed, mManualAllowedSlot));
        }
        List<String> normalList3gSwitch = new ArrayList<String>();
        // feature: For ALPS00791254 remove 3g switch off radio and NMOpFor3GSwitch @{
        if (mSimInfoList.size() > 0 && !mExtension.isRemoveRadioOffFor3GSwitchFlag()) {
            normalList3gSwitch.add(getString(R.string.service_3g_off));
            entryValues.add(SIMID_3G_SERVICE_OFF);
        }
        mServiceList.setEntriesData(mSimInfoList, simIndicatorList, normalList3gSwitch, itemStatus);
        mServiceList.setEntryValues(entryValues);
    }

    /**
     * Add for ALPS01266612
     * If the activity is on the background, we hide the dialogs
     */
    protected void onPause() {
        // TODO Auto-generated method stub
        super.onPause();
        PhoneLog.d(TAG, "onPause....");
        dismissDialogs();
    }

    /**
     * Dismiss the dialogs belongs to the activity
     */
    private void dismissDialogs() {
        PhoneLog.d(TAG, "dismissDialogs...");
        Dialog switchDialog = mServiceList.getDialog();
        if (switchDialog != null && switchDialog.isShowing()) {
            switchDialog.dismiss();
        }
        Dialog netWorkDialog = mNetworkMode.getDialog();
        if (netWorkDialog != null && netWorkDialog.isShowing()) {
            netWorkDialog.dismiss();
        }
    }
}
