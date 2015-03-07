package com.mediatek.gemini;

import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.app.StatusBarManager;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.ContentObserver;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.sip.SipManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.SystemProperties;
import android.preference.Preference;
import android.preference.PreferenceGroup;
import android.preference.PreferenceScreen;
import android.provider.Settings;
import android.telephony.PhoneStateListener;
import android.telephony.ServiceState;
import android.telephony.TelephonyManager;
import android.view.Window;
import android.view.WindowManager;
import android.widget.CompoundButton;

import com.android.internal.telephony.ITelephony;
import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.TelephonyIntents;

import com.android.settings.R;
import com.android.settings.SettingsPreferenceFragment;
import com.android.settings.Utils;

import com.mediatek.CellConnService.CellConnMgr;
import com.mediatek.common.featureoption.FeatureOption;
import com.mediatek.common.telephony.ITelephonyEx;
import com.mediatek.gemini.simui.SimCardInfoPreference;
import com.mediatek.gemini.simui.SimInfoViewUtil.WidgetType;
import com.mediatek.gemini.simui.SimSelectDialogPreference;
import com.mediatek.settings.OobeUtils;
import com.mediatek.settings.ext.ISettingsMiscExt;
import com.mediatek.settings.ext.ISimManagementExt;
import com.mediatek.telephony.SimInfoManager;
import com.mediatek.telephony.SimInfoManager.SimInfoRecord;
import com.mediatek.telephony.TelephonyManagerEx;
import com.mediatek.xlog.Xlog;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class SimManagement extends SettingsPreferenceFragment implements
        Preference.OnPreferenceChangeListener {

    private static final String TAG = "SimManagementSettings";
    // key of preference of sim managment
    private static final String KEY_SIM_INFO_CATEGORY = "sim_info";
    private static final String KEY_GENERAL_SETTINGS_CATEGORY = "general_settings";
    private static final String KEY_DEFAULT_SIM_SETTINGS_CATEGORY = "default_sim";
    private static final String KEY_VOICE_CALL_SIM_SETTING = "voice_call_sim_setting";
    private static final String KEY_VIDEO_CALL_SIM_SETTING = "video_call_sim_setting";
    private static final String KEY_SMS_SIM_SETTING = "sms_sim_setting";
    private static final String KEY_GPRS_SIM_SETTING = "gprs_sim_setting";
    private static final String KEY_SIM_CONTACTS_SETTINGS = "contacts_sim";
    private static final String KEY_3G_SERVICE_SETTING = "3g_service_settings";

    private boolean mVTCallItemAvailable = false;
    private boolean mIsVoipAvailable = true;
    private boolean mIs3gOff = false;
    private static final String TRANSACTION_START = "com.android.mms.transaction.START";
    private static final String TRANSACTION_STOP = "com.android.mms.transaction.STOP";
    private static final String ACTION_DATA_USAGE_DISABLED_DIALOG_OK =
            "com.mediatek.systemui.net.action.ACTION_DATA_USAGE_DISABLED_DIALOG_OK"; 
    private static final String CONFIRM_DIALOG_MSG_ID = "confirm_dialog_msg_id";
    private static final String PROGRESS_DIALOG_MSG_ID = "progress_dialog_msg_id";
    private static final String VT_SELECTED_ID = "vt_selected_id";
    private static final String GPRS_SELECTED_ID = "gprs_selected_id";
    // which simid is selected when switch video call
    private long mSelectedVideoSimId;
    private boolean mIsSlot1Insert = false;
    private boolean mIsSlot2Insert = false;

    // time out length
    private static final int DETACH_DATA_CONN_TIME_OUT = 10000;// in ms
    private static final int ATTACH_DATA_CONN_TIME_OUT = 30000;// in ms
    private static final int DIALOG_NOT_REMOVE_TIME_OUT = 1000;// in ms
    private static final int SWITCH_3G_TIME_OUT = 60000;// in ms
    private static final int VIDEO_CALL_OFF = -1;
    
    // when sim radio switch complete receive msg with this id
    private static final int EVENT_DUAL_SIM_MODE_CHANGED_COMPLETE = 1;
    // time out message event
    private static final int DATA_SWITCH_TIME_OUT_MSG = 2000;
    private static final int DIALOG_NOT_SHOW_SUCCESS_MSG = DATA_SWITCH_TIME_OUT_MSG + 1;
    private static final int DATA_3G_SWITCH_TIME_OUT_MSG = DATA_SWITCH_TIME_OUT_MSG + 2;
    // Dialog Id for different task
    private static final int PROGRESS_DIALOG = 1000;
    private static final int DIALOG_3G_MODEM_SWITCH_CONFIRM = PROGRESS_DIALOG + 1;
    private static final int DIALOG_GPRS_SWITCH_CONFIRM = PROGRESS_DIALOG + 2;
    // constant for current sim mode
    private static final int ALL_RADIO_OFF = 0;
    private static final int SIM_SLOT_1_RADIO_ON = 1;
    private static final int SIM_SLOT_2_RADIO_ON = 2;
    private static final int ALL_RADIO_ON = 3;

    private SimSelectDialogPreference mVoiceCallSimSetting;
    private SimSelectDialogPreference mSmsSimSetting;
    private SimSelectDialogPreference mVideoCallSimSetting;
    private SimSelectDialogPreference mGprsSimSetting;


    private PreferenceScreen mSimAndContacts;

    private TelephonyManager mTelephonyManager;
    private TelephonyManagerEx mTelephonyManagerEx;
    private ITelephony mTelephony;
    private ITelephonyEx mTelephonyEx;
    // a list store the siminfo variable
    private List<SimInfoRecord> mSimInfoList = new ArrayList<SimInfoRecord>();
    private List<SimCardInfoPreference> mSimInfoPreference = new ArrayList<SimCardInfoPreference>();

    // to prevent click too fast to switch card 1 and 2 in radio on/off
    private boolean mIsSIMRadioSwitching = false;

    private IntentFilter mIntentFilter;
    private int mDataSwitchMsgIndex = -1;
    private CellConnMgr mCellConnMgr;
    private boolean mIsVoiceCapable = false;
    private boolean mIsSmsCapable = false;
    private boolean mIsDataConnectActing = false;
    private ISimManagementExt mExt;
    private ISettingsMiscExt mMiscExt;
    private int mSimNum;
    private long mSelectedGprsSimId;
    private boolean mRemoveProgDlg = false;
    private int mProDlgMsgId = -1;
    private boolean mNoNeedRestoreProgDlg = false;
        

    ///M: add for consistent_UI single sim to enable data connection
    private ConnectivityManager mConnService;

    //add for smart 3G switch
    private boolean mIs3GSwitchManualEnabled;
    private boolean mIsManuAllowed;
    private boolean mIsManuSelected;

    private ContentObserver mGprsDefaultSIMObserver = new ContentObserver(new Handler()) {
        @Override
        public void onChange(boolean selfChange) {
            updateDataConnPrefe();
        }
    };
    
    private ContentObserver mVoiceCallObserver = new ContentObserver(new Handler()) {
        @Override
        public void onChange(boolean selfChange) {
            updateVoiceCallPrefe();
        }
    };
    
    private PhoneStateListener mPhoneServiceListener = new PhoneStateListener() {
        @Override
        public void onServiceStateChanged(ServiceState serviceState) {
            int state = serviceState.getState();
            handleRadioStatus(state);
        }
    };
    /*
     * Timeout handler to remove the dialog always showing if modem not send
     * connected or disconnected intent
     */
    private Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            if (DATA_SWITCH_TIME_OUT_MSG == msg.what) {
                Xlog.i(TAG, "reveive time out msg...");
                removeProgDlg();
                mIsDataConnectActing = false;
                updateDataConnPrefe();
            } else if (DIALOG_NOT_SHOW_SUCCESS_MSG == msg.what) {
                Xlog.i(TAG, "handle abnormal progress dialog not showing");
                dealWithSwitchComplete();    
            } else if (DATA_3G_SWITCH_TIME_OUT_MSG == msg.what) {
                Xlog.d(TAG,"3G switch time out remove the progress dialog");
                removeProgDlg();
                setStatusBarEnableStatus(true);
                updateVideoCallDefaultSIM();
            } else if (EVENT_DUAL_SIM_MODE_CHANGED_COMPLETE == msg.what) {
                Xlog.d(TAG, "dual sim mode changed");
                dealWithSwitchComplete();
            }
        }
    };

    // receive when sim card radio switch complete
    private Messenger mSwitchRadioStateMsg = new Messenger(mHandler);

    // Receiver to handle different actions
    private BroadcastReceiver mSimReceiver = new BroadcastReceiver() {
        boolean mAirplaneOn = false;
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            Xlog.d(TAG,"mSimReceiver action = " + action);
            if (action.equals(TelephonyIntents.ACTION_SIM_INFO_UPDATE)
                    || action.equals(Intent.SIM_SETTINGS_INFO_CHANGED)
                    || action.equals(TelephonyIntents.ACTION_SIM_NAME_UPDATE)) {
                int previousSimNum = mSimNum;
                getSimInfo();
                Xlog.d(TAG,"previousSimNum = " + previousSimNum + " mSimNum = " + mSimNum);
                if (previousSimNum != mSimNum) {
                    // sim hot swap happend need to re-init the UI
                    initPreferenceUI();
                } else {
                    // only sim info update like name, color etc only update sim info iteself
                    updateSimInfoForPreference();
                }
            } else if (action.equals(TelephonyIntents.ACTION_SIM_INDICATOR_STATE_CHANGED)) {
                int state = intent.getIntExtra(TelephonyIntents.INTENT_KEY_ICC_STATE, -1);
                int slotId = intent.getIntExtra(TelephonyIntents.INTENT_KEY_ICC_SLOT, -1);
                Xlog.d(TAG,"state = " + state + " slotId = " + slotId + " mAirplaneOn = " + mAirplaneOn);
                if (!mAirplaneOn) {
                    updatePrefIndicator(slotId,state);
                }
            } else if (action.equals(Intent.ACTION_AIRPLANE_MODE_CHANGED)) {
                dealDlgOnAirplaneMode();
                mAirplaneOn = intent.getBooleanExtra("state", false);
                Xlog.d(TAG,"air plane mode is = " + mAirplaneOn);
                mGprsSimSetting.setEnableNormalItem(!mAirplaneOn);
                // when air plane mode changed, change the mGprsSimSetting status immediately.
                mGprsSimSetting.setEnabled(isGPRSEnable());
                
                for (SimCardInfoPreference pref : mSimInfoPreference) {
                    pref.enableWidget(!mAirplaneOn);
                    pref.setChecked(isRadioInOn(pref.getSimSlotId()));
                }
                //Update indicator for airplane mode, if enable to set indicator radio off directly, if disable
                // also need to re-query the true indicator status @CR1034663
                for (SimInfoRecord siminfo : mSimInfoList) {
                    updatePrefIndicator(siminfo.mSimSlotId, getSimIndicator(siminfo.mSimSlotId));
                }
            } else if (action.equals(TRANSACTION_START)) {
                // Disable dataconnection pref to prohibit to switch data
                // connection
                Xlog.d(TAG, "MMS starting dismiss GPRS selection dialog to prohbit data switch");
                handleTransaction(true);
            } else if (action.equals(TRANSACTION_STOP)) {
                
                // if data connection is disable then able when mms transition
                // stop if all radio is not set off
                Xlog.d(TAG, "MMS stopped dismiss GPRS selection dialog");
                handleTransaction(false);
            } else if (action.equals(TelephonyIntents.EVENT_3G_SWITCH_LOCK_CHANGED)) {
                if (mVTCallItemAvailable) {
                    boolean lockState = intent.getBooleanExtra(
                            TelephonyIntents.EXTRA_3G_SWITCH_LOCKED, false);
                    mVideoCallSimSetting.setEnabled(!(mIs3gOff || lockState || mSimNum == 1
                            || !mIs3GSwitchManualEnabled));
                    Xlog.d(TAG, "mIs3gOff=" + mIs3gOff + " lockState="
                            + lockState);
                }
            } else if (action.equals(TelephonyIntents.EVENT_3G_SWITCH_DONE)) {
                // remove the loading dialog when 3g service switch done
                mHandler.removeMessages(DATA_3G_SWITCH_TIME_OUT_MSG);
                removeProgDlg();
                setStatusBarEnableStatus(true);
                updateVideoCallDefaultSIM();
            } else if (action.equals(TelephonyIntents.ACTION_ANY_DATA_CONNECTION_STATE_CHANGED)) {
                int slotId = intent.getIntExtra(PhoneConstants.GEMINI_SIM_ID_KEY, -1);
                String apnTypeList = intent.getStringExtra(PhoneConstants.DATA_APN_TYPE_KEY);
                PhoneConstants.DataState state = getMobileDataState(intent);
                /* {@M Auto open the other card's data connection. when current card is radio off */ 
                mExt.dealWithDataConnChanged(intent, isResumed());
                /*@}*/
                if ((state == PhoneConstants.DataState.CONNECTED) || 
                    (state == PhoneConstants.DataState.DISCONNECTED)) {
                    if ((PhoneConstants.APN_TYPE_DEFAULT.equals(apnTypeList))) {
                        Xlog.d(TAG, "****the slot " + slotId + state + 
                                    " mIsDataConnectActing=" + mIsDataConnectActing);
                        if (mIsDataConnectActing) {
                            mHandler.removeMessages(DATA_SWITCH_TIME_OUT_MSG);
                            removeProgDlg();
                            mIsDataConnectActing = false;   
                        }
                    }
                }

            } else if (action.equals(ACTION_DATA_USAGE_DISABLED_DIALOG_OK)) {
                if (mIsDataConnectActing) {
                    mIsDataConnectActing = false;
                    removeProgDlg();    
                }
            }
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Xlog.d(TAG, "onCreate Sim Management");
        addPreferencesFromResource(R.xml.sim_management);
        // initialize variables 
        mTelephony = ITelephony.Stub.asInterface(ServiceManager.getService(Context.TELEPHONY_SERVICE));
        mTelephonyEx = ITelephonyEx.Stub.asInterface(ServiceManager.getService(Context.TELEPHONY_SERVICEEX));
        mTelephonyManagerEx = TelephonyManagerEx.getDefault();
        if (FeatureOption.MTK_GEMINI_SUPPORT) {
            try {
                if (mTelephony != null && mTelephonyEx != null) {
                    mTelephony.registerForSimModeChange(mSwitchRadioStateMsg
                        .getBinder(), EVENT_DUAL_SIM_MODE_CHANGED_COMPLETE);
                    mIsManuAllowed = mTelephonyEx.is3GSwitchManualEnabled();
                    mIsManuSelected = mTelephonyEx.is3GSwitchManualChange3GAllowed();
                    mIs3GSwitchManualEnabled = mIsManuAllowed && mIsManuSelected;
                    Xlog.d(TAG,"mIsManuAllowed = " + mIsManuAllowed + "mIsManuSelected = " + mIsManuSelected +
                               " mIs3GSwitchManualEnabled = " + mIs3GSwitchManualEnabled);
                }
            } catch (RemoteException e) {
                Xlog.e(TAG, "mTelephonyEx exception");
                return;
            }    
        }
        ///M: initilize connectivity manager for consistent_UI
        mConnService = ConnectivityManager.from(getActivity());
        mTelephonyManager = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
        mIsVoiceCapable = mTelephonyManager.isVoiceCapable();
        mIsSmsCapable = mTelephonyManager.isSmsCapable();
        // Only single sim project use this to monitor radio service state
        if (!FeatureOption.MTK_GEMINI_SUPPORT) {
            mTelephonyManager.listen(mPhoneServiceListener,PhoneStateListener.LISTEN_SERVICE_STATE);
        }
        mCellConnMgr = new CellConnMgr();
        mCellConnMgr.register(getActivity());
        mExt = Utils.getSimManagmentExtPlugin(getActivity());
        mMiscExt = Utils.getMiscPlugin(getActivity());
        if (isSupportVTCallSetting()) {
            mVTCallItemAvailable = true;
        }
        Xlog.d(TAG, "mIsVoiceCapable=" + mIsVoiceCapable + 
                    " mIsSmsCapable=" + mIsSmsCapable + 
                    " mVTCallItemAvailable=" + mVTCallItemAvailable);
        // initialize layout
        PreferenceGroup parent = (PreferenceGroup) findPreference(KEY_GENERAL_SETTINGS_CATEGORY);
        // /M: plug in of sim management
        
        mExt.updateSimManagementPref(parent);
        //*}/
        initIntentFilter();
        findEachPreference();

        removeDefaultSettingsItem();

        getSimInfo();

        getActivity().registerReceiver(mSimReceiver, mIntentFilter);
        
        //handler the process is killed as dialog showing
        if (savedInstanceState != null) {
            mDataSwitchMsgIndex = savedInstanceState.getInt(CONFIRM_DIALOG_MSG_ID,-1);
            mProDlgMsgId = savedInstanceState.getInt(PROGRESS_DIALOG_MSG_ID,-1);
            // if the value !=-1 means there is dialog showing
            if (mProDlgMsgId != -1) {
                //Progress dialog no need to restore so remove on resume()
                Xlog.d(TAG,"mProDlgMsgId != -1 to remove dialog");
                mNoNeedRestoreProgDlg = true;
            }
            mSelectedVideoSimId = savedInstanceState.getLong(VT_SELECTED_ID, -1);
            mSelectedGprsSimId = savedInstanceState.getLong(GPRS_SELECTED_ID, -1);
            Xlog.d(TAG,"onrestore the dailog msg id with mDataSwitchMsgIndex = " + 
                        mDataSwitchMsgIndex + " mProDlgMsgId = " + mProDlgMsgId);
            Xlog.d(TAG,"onrestore mSelectedVideoSimId = " + 
                        mSelectedVideoSimId + " mSelectedGprsSimId = " + mSelectedGprsSimId);
        }
        /// M: for CT to replace the SIM to UIM
        replaceIMString();
        // oobe
        OobeUtils.setSimView(this, getActivity().getIntent());
    }

    /**
     * M: Replace SIM to SIM/UIM
     */
    private void replaceIMString() {
        PreferenceGroup simInfoListCategory = (PreferenceGroup) findPreference(KEY_SIM_INFO_CATEGORY);
        if (simInfoListCategory != null) {
            simInfoListCategory.setTitle(mMiscExt.customizeSimDisplayString(getString(R.string.gemini_sim_info_title), 
                GeminiUtils.UNDEFINED_SLOT_ID));
        }
        getActivity().setTitle(mMiscExt.customizeSimDisplayString(getString(R.string.gemini_sim_management_title),
            GeminiUtils.UNDEFINED_SLOT_ID));
        mSimAndContacts.setSummary(mMiscExt.customizeSimDisplayString(getString(R.string.gemini_contacts_sim_summary),
            GeminiUtils.UNDEFINED_SLOT_ID));
    }
    
    private void handleTransaction(boolean started) {
        mGprsSimSetting.setEnabled(!started);
        Dialog dlg = mGprsSimSetting.getDialog();
        if (dlg != null && dlg.isShowing()) {
            dlg.dismiss();
        }
    }

    private void findEachPreference() {
        mVoiceCallSimSetting = (SimSelectDialogPreference) findPreference(KEY_VOICE_CALL_SIM_SETTING);
        mVideoCallSimSetting = (SimSelectDialogPreference) findPreference(KEY_VIDEO_CALL_SIM_SETTING);
        mSmsSimSetting = (SimSelectDialogPreference) findPreference(KEY_SMS_SIM_SETTING);
        mGprsSimSetting = (SimSelectDialogPreference) findPreference(KEY_GPRS_SIM_SETTING);
        mSimAndContacts = (PreferenceScreen) findPreference(KEY_SIM_CONTACTS_SETTINGS);

        mVoiceCallSimSetting.setOnPreferenceChangeListener(this);
        mVideoCallSimSetting.setOnPreferenceChangeListener(this);
        mSmsSimSetting.setOnPreferenceChangeListener(this);
        mGprsSimSetting.setOnPreferenceChangeListener(this);
        /*M: set the icon for each preference (because of the onbinding of preference is called after the onresume,
              so before onresume it always get the icon with null) then set the icon in code rather xml@{*/
        setIconForDefaultSimPref();
    }

    ///M: set the icon for each preference voice/sms/video/data connection
    private void setIconForDefaultSimPref() {
        mVoiceCallSimSetting.setIcon(R.drawable.gemini_voice_call);
        mVideoCallSimSetting.setIcon(R.drawable.gemini_video_call);
        mSmsSimSetting.setIcon(R.drawable.gemini_sms);
        mGprsSimSetting.setIcon(R.drawable.gemini_data_connection);
    }
    
    /**
     * Remove some items due to the devices may not support voice or sms or vt ie. tablet
     * 
     */
    private void removeDefaultSettingsItem() {
        Xlog.d(TAG, "removeGeneralSettingsItem");
        PreferenceGroup pref = (PreferenceGroup)findPreference(KEY_DEFAULT_SIM_SETTINGS_CATEGORY);
        if (!mIsVoiceCapable) {
            pref.removePreference(mVoiceCallSimSetting);
            pref.removePreference(mVideoCallSimSetting);
        } else {
            if (!mVTCallItemAvailable) {
                pref.removePreference(mVideoCallSimSetting);
            }
        }
        if (!mIsSmsCapable) {
            pref.removePreference(mSmsSimSetting);
        }
        //For consistent UI, single sim need to remove some items due to unnecessary to show
        if (!FeatureOption.MTK_GEMINI_SUPPORT) {
            pref.removePreference(mSmsSimSetting);   
            pref.removePreference(mVideoCallSimSetting); 
            PreferenceGroup generalSettings = (PreferenceGroup) findPreference(KEY_GENERAL_SETTINGS_CATEGORY);         
            if (generalSettings != null) {
                generalSettings.removePreference(mSimAndContacts);
            }
        }
    }

    private void initIntentFilter() {
        Xlog.d(TAG, "initIntentFilter");
        mIntentFilter = new IntentFilter(
                TelephonyIntents.ACTION_SIM_INDICATOR_STATE_CHANGED);
        mIntentFilter.addAction(Intent.SIM_SETTINGS_INFO_CHANGED);
        mIntentFilter
                .addAction(TelephonyIntents.ACTION_ANY_DATA_CONNECTION_STATE_CHANGED);
        mIntentFilter.addAction(Intent.ACTION_AIRPLANE_MODE_CHANGED);
        mIntentFilter.addAction(TRANSACTION_START);
        mIntentFilter.addAction(TRANSACTION_STOP);
        mIntentFilter.addAction(TelephonyIntents.ACTION_SIM_NAME_UPDATE);
        mIntentFilter.addAction(TelephonyIntents.ACTION_SIM_INFO_UPDATE);
        mIntentFilter.addAction(ACTION_DATA_USAGE_DISABLED_DIALOG_OK);
        if (FeatureOption.MTK_GEMINI_3G_SWITCH) {
            mIntentFilter.addAction(TelephonyIntents.EVENT_3G_SWITCH_LOCK_CHANGED);
            mIntentFilter.addAction(TelephonyIntents.EVENT_3G_SWITCH_DONE);
        }
    }

    private void updatePrefIndicator(int slotId, int indicator) {
        Xlog.d(TAG,"updatePrefIndicator with slotId = " + slotId + " indicator = " + indicator);
        mVoiceCallSimSetting.updateSimIndicator(slotId, indicator);
        mSmsSimSetting.updateSimIndicator(slotId, indicator);
        mVideoCallSimSetting.updateSimIndicator(slotId, indicator);
        mGprsSimSetting.updateSimIndicator(slotId, indicator);
        for (SimCardInfoPreference pref : mSimInfoPreference) {
            if (pref.getSimSlotId() == slotId) {
                pref.setSimIndicator(indicator);
            }
        }
    }
    
    @Override
    public void onResume() {
        super.onResume();
        Xlog.d(TAG, "onResume()");
        if (mNoNeedRestoreProgDlg && (!"tablet".equals(SystemProperties.get("ro.build.characteristics")))) {
            //for CR ALPS00597595
            Xlog.d(TAG,"Unexpected is killed so restore the state but for progess dialog" + 
            " no need as the state has lost");
            removeDialog(PROGRESS_DIALOG);
            mNoNeedRestoreProgDlg = false;
        }
        // internet call item can be changed from call settings, for single sim project
        // if internet call item is disabled voice call should be removed from category
        
        updateVoipPreference();

        initPreferenceUI();

        if (FeatureOption.MTK_GEMINI_SUPPORT) {
            getContentResolver().registerContentObserver(
                Settings.System.getUriFor(Settings.System.GPRS_CONNECTION_SIM_SETTING),
                                        false, mGprsDefaultSIMObserver);
        } else {
            getContentResolver().registerContentObserver(
                Settings.Secure.getUriFor(Settings.Global.MOBILE_DATA),
                                        false, mGprsDefaultSIMObserver);
        }
        getContentResolver().registerContentObserver(
                Settings.System.getUriFor(Settings.System.VOICE_CALL_SIM_SETTING), 
                                        false, mVoiceCallObserver);
        
        dealDialogOnResume();
        /* {@M Auto open the other card's data connection. when current card is radio off */
        mExt.dealWithDataConnChanged(null, isResumed());
        /*@}*/
    }

    private void updateVoipPreference() {
        mIsVoipAvailable = isVoipAvailable();
        Xlog.d(TAG, "mIsVoipAvailable=" + mIsVoipAvailable);
        if (!FeatureOption.MTK_GEMINI_SUPPORT && !mIsVoipAvailable) {
            PreferenceGroup pref = (PreferenceGroup) findPreference(KEY_DEFAULT_SIM_SETTINGS_CATEGORY);
            if (pref != null) {
                pref.removePreference(mVoiceCallSimSetting);
            }
        }
    }
    
    private boolean isSimInsertedIn(long simId) {
        for (SimInfoRecord siminfo : mSimInfoList) {
            if (siminfo.mSimInfoId == simId) {
                return true;
            }    
        }
        Xlog.d(TAG,"simid = " + simId + " not inserted in phone");
        return false;
    }

    private void dealDialogOnResume() {
        Xlog.d(TAG,"dealDialogOnResume");
        // if any progress dialog showing and the state is finished mRemoveProgDlg is true then
        if (mRemoveProgDlg) {
            Xlog.d(TAG,"on resume to remove dialog");    
            removeDialog(PROGRESS_DIALOG);
            mRemoveProgDlg = false;
        }
        Xlog.d(TAG,"mRemoveProgDlg = " + mRemoveProgDlg);   

        // if 3G switch confirm dialog is showing while current the radio is off then removed
        if (isRadioOff() && isDialogShowing(DIALOG_3G_MODEM_SWITCH_CONFIRM)) {
            removeDialog(DIALOG_3G_MODEM_SWITCH_CONFIRM);
        }
        if (isRadioOff() && isDialogShowing(DIALOG_GPRS_SWITCH_CONFIRM)) {
            removeDialog(DIALOG_GPRS_SWITCH_CONFIRM);    
        }
    }
    private void dealDlgOnAirplaneMode() {
        if (isResumed() && isRadioOff()) {
            Xlog.d(TAG,"dealDlgOnAirplaneMode");
            if (isDialogShowing(DIALOG_3G_MODEM_SWITCH_CONFIRM)) {
                removeDialog(DIALOG_3G_MODEM_SWITCH_CONFIRM);
                updateVideoCallDefaultSIM();
            } else if (isDialogShowing(DIALOG_GPRS_SWITCH_CONFIRM)) {
                removeDialog(DIALOG_GPRS_SWITCH_CONFIRM);    
                updateDataConnPrefe();
            }       
        }
    }
    private void removeUnusedPref() {
        Xlog.d(TAG, "removeUnusedPref()");
        PreferenceGroup pref = (PreferenceGroup) findPreference(KEY_DEFAULT_SIM_SETTINGS_CATEGORY);
        ///M: oobe @{
        if (pref == null) {
            return;
        }
        /// @}

        if (!mIsVoiceCapable) {
            pref.removePreference(mVoiceCallSimSetting);
            pref.removePreference(mVideoCallSimSetting);
            if (!mIsSmsCapable) {
                pref.removePreference(mSmsSimSetting);
            }
        }
        if (!FeatureOption.MTK_GEMINI_SUPPORT && 
                !mIsVoipAvailable) {
            pref.removePreference(mVoiceCallSimSetting);
        }
        // if not support vtcall feature then remove this feature
        if (!mVTCallItemAvailable) {
            Xlog.d(TAG, "Video call is " + mVTCallItemAvailable + " remove the pref");
            pref.removePreference(mVideoCallSimSetting);
        }
    }

    private void getSimInfo() {
        Xlog.d(TAG, "getSimInfo()");
        mSimInfoList = SimInfoManager.getInsertedSimInfoList(getActivity());
        mSimNum = mSimInfoList.size();
        Xlog.d(TAG,"total inserted sim card =" + mSimNum);
        Collections.sort(mSimInfoList, new GeminiUtils.SIMInfoComparable());
        // for debug purpose to show the actual sim information
        int slot;
        for (int i = 0; i < mSimInfoList.size(); i++) {
            Xlog.i(TAG, "siminfo.mDisplayName = " + mSimInfoList.get(i).mDisplayName);
            Xlog.i(TAG, "siminfo.mNumber = " + mSimInfoList.get(i).mNumber);
            slot = mSimInfoList.get(i).mSimSlotId;
            Xlog.i(TAG, "siminfo.mSimSlotId = " + slot);
            if (slot == PhoneConstants.GEMINI_SIM_1) {
                mIsSlot1Insert = true;    
            } else if (slot == PhoneConstants.GEMINI_SIM_2) {
                mIsSlot2Insert = true;
            }
            Xlog.i(TAG, "siminfo.mColor = " + mSimInfoList.get(i).mColor);
            Xlog.i(TAG, "siminfo.mDispalyNumberFormat = "
                    + mSimInfoList.get(i).mDispalyNumberFormat);
            Xlog.i(TAG, "siminfo.mSimInfoId = " + mSimInfoList.get(i).mSimInfoId);
        }
    }

    /**
     * Init the UI with specific sim info and condition, should be called onResume and when
     * sim inserted number has been modified
     */
    private void initPreferenceUI() {
        Xlog.d(TAG, "initPreferenceUI() and update UI");
        // no sim card - > sim card
        // sim card -> no sim card enable/disable whole screen
        getPreferenceScreen().setEnabled(mSimNum > 0);
        addSimInfoPreference();
        initSimSelectDialogPref();
        setPreferenceProperty();
        if (mSimNum == 0) {
            setNoSimInfoUi();
        }   
    }
    
    private void updateSimInfoForPreference() {
        Xlog.d(TAG,"updateSimInfoForPreference");
        mVoiceCallSimSetting.updateSimInfoList(mSimInfoList);
        mSmsSimSetting.updateSimInfoList(mSimInfoList);
        mVideoCallSimSetting.updateSimInfoList(mSimInfoList);
        mGprsSimSetting.setEnabled(isGPRSEnable());
        mGprsSimSetting.updateSimInfoList(mSimInfoList);
        for (SimCardInfoPreference pref : mSimInfoPreference) {
            for (SimInfoRecord siminfo : mSimInfoList) {
                if (pref.getSimInfoId() == siminfo.mSimInfoId) {
                    pref.setSimInfoRecord(siminfo);
                }
            }
        }
    }

    private void setNoSimInfoUi() {
        ///M: oobe @{
        PreferenceGroup simInfoListCategory = (PreferenceGroup) findPreference(KEY_SIM_INFO_CATEGORY);
        if (simInfoListCategory == null || OobeUtils.isOobeMode(this)) {
            return;
        }
        simInfoListCategory.removeAll();
        Preference pref = new Preference(getActivity());
        if (pref != null) {
            /// Replace SIM to SIM/UIM
            pref.setTitle(mMiscExt.customizeSimDisplayString(getString(R.string.gemini_no_sim_indicator),
                    GeminiUtils.UNDEFINED_SLOT_ID));
            simInfoListCategory.addPreference(pref);
        }
        getPreferenceScreen().setEnabled(false);
        /// @}
        // for internet call to enable the voice call setting
        if (mIsVoipAvailable) {
            mVoiceCallSimSetting.setEnabled(true);
        } else {
            // finish due to no sim cards in however due to fragment can not finish under background
            // so only resumed to finish
            if (isResumed()) {
                Xlog.d(TAG,"finish() sim management for sim hot swap as mSimNum = " + mSimNum);
                if ("tablet".equals(SystemProperties.get("ro.build.characteristics"))) {
                    if (!getResources().getBoolean(
                            com.android.internal.R.bool.preferences_prefer_dual_pane)) {
                        Xlog.i(TAG, "[Tablet] It is single pane, so finish it!");
                        finish();
                    } else {
                        Xlog.i(TAG, "[Tablet] It is multi pane, so do not finish it!");
                    }
                } else {
                    if (!getResources().getBoolean(
                        com.android.internal.R.bool.preferences_prefer_dual_pane)) {
                        Xlog.i(TAG, "[Tablet] It is single pane, so finish it 2!");
                        finish();
                     }
                }
            }
        }
    }
    
    /**
     * By using TelephonyManager to get radio state
     * @param slotId sim slot Id
     * @return is radio in on
     */
    private boolean isRadioInOn(int slotId) {
        Xlog.d(TAG,"isRadioInOn with slotId = " + slotId);
        if (isRadioOff()) {
            return false;    
        }
        boolean isRadioInOn = false;
        try {
            if (FeatureOption.MTK_GEMINI_SUPPORT) {
                isRadioInOn = mTelephonyEx.isRadioOn(slotId);
            } else {
                isRadioInOn = mTelephony.isRadioOn();
            }
        } catch (RemoteException e) {
            Xlog.e(TAG, "mTelephony exception");
        }
        Xlog.d(TAG,"isRadioInOn = " + isRadioInOn);
        return isRadioInOn;
    }
    
    /**
     * Init a SimCardInfoPreference with specific check state and some other property for sim info category
     * @param simInfo sim info from sim info list
     * @return a customization SimCardInfoPreference
     */
    private SimCardInfoPreference initSimInfoPreference(final SimInfoRecord simInfo) {
        int status = getSimIndicator(simInfo.mSimSlotId);
        boolean isRadioOn;// sim card is trurned on
        boolean isAirplaneModeOn = Settings.System.getInt(getContentResolver(), Settings.System.AIRPLANE_MODE_ON, -1) == 1;
        final SimCardInfoPreference simInfoPref = 
                new SimCardInfoPreference(WidgetType.Switch, getActivity(), isAirplaneModeOn);
        simInfoPref.setSimInfoProperty(simInfo,status);
        // this preference won't disable whole view only partion, so set this property false if further 
        //need to enable/disable should consider adjust this property
        simInfoPref.setShouldDisableView(false);
        simInfoPref.setWidgetClickable(true);
        Xlog.i(TAG, "simid status is  " + status);
        isRadioOn = isRadioInOn(simInfo.mSimSlotId);
        simInfoPref.setChecked(isRadioOn);
        Xlog.d(TAG, "sim card " + simInfo.mSimSlotId + " radio state is isRadioOn=" + isRadioOn);
        simInfoPref.setCheckedChangeListener(
                new android.widget.CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                    Xlog.i(TAG, "receive slot " + simInfo.mSimSlotId
                            + " switch is clicking! with isChecked=" + isChecked);
                    //Fix seldom CR ALPS01209244
                    if (!isResumed()) {
                        //CR:ALPS01375095
                        simInfoPref.setChecked(!isChecked);
                        Xlog.e(TAG,"Need to check how it happend as no in resume but receive on checked in with UI");
                        return;
                    }
                    if (!mIsSIMRadioSwitching) {
                        Xlog.d(TAG, "start to turn radio in " + isChecked);
                        mIsSIMRadioSwitching = true;
                        simInfoPref.setChecked(isChecked);
                        if (PhoneConstants.GEMINI_SIM_NUM > 2) {
                            //for geimini plus
                            switchGeminiPlusSimRadioState(simInfo.mSimSlotId);
                        } else {
                            switchSimRadioState(simInfo.mSimSlotId, isChecked);    
                        }
                    } else {
                        Xlog.d(TAG,"Click too fast it is switching " + "and set the switch to previous state");
                        simInfoPref.setChecked(!isChecked);
                    }
                }
            });
        return simInfoPref;
    }
    
    private void handleRadioStatus(int state) {
        Xlog.d(TAG,"service state = " + state + " mIsSIMRadioSwitching = " + mIsSIMRadioSwitching);
        if (mIsSIMRadioSwitching) {
            mIsSIMRadioSwitching = false;
        }
        for (SimCardInfoPreference pref : mSimInfoPreference) {
                pref.setChecked(isRadioInOn(pref.getSimSlotId()));
        }
        mGprsSimSetting.setEnabled(isGPRSEnable());
     }
    
    /**
     * add SimCardInfoPreference into siminfo category
     */
    private void addSimInfoPreference() {
        Xlog.d(TAG, "addSimInfoPreference()");
        PreferenceGroup simInfoListCategory = (PreferenceGroup) findPreference(KEY_SIM_INFO_CATEGORY);
        ///M: for OOBE it may be removed
        if (simInfoListCategory != null) {
            // If added sim info preference not same as sim number means hot swap happend need to re-init category
            if (mSimInfoPreference.size() != mSimNum) {
                simInfoListCategory.removeAll();
                mSimInfoPreference.clear();
                for (final SimInfoRecord siminfo : mSimInfoList) {
                    SimCardInfoPreference simInfoPref = initSimInfoPreference(siminfo);
                    mSimInfoPreference.add(simInfoPref);
                    simInfoListCategory.addPreference(simInfoPref);
                }
                // if no sim card inserted need to add a specific title preference
                if (mSimNum == 0) {
                    Preference pref = new Preference(getActivity());
                    if (pref != null) {
                        pref.setTitle(R.string.gemini_no_sim_indicator);
                        simInfoListCategory.addPreference(pref);
                    }
                }
            }
        }
    }
    
    private void initSimSelectDialogPref() {
        Xlog.d(TAG, "initSimSelecDialogPref()");
        List<Long> simIdlist = new ArrayList<Long>();
        List<Integer> simIndicatorList = new ArrayList<Integer>();
        List<Boolean> itemStatusList = new ArrayList<Boolean>();
        List<Long> entryValues = new ArrayList<Long>();
        for (SimInfoRecord siminfo : mSimInfoList) {
            simIdlist.add(siminfo.mSimInfoId);
            simIndicatorList.add(getSimIndicator(siminfo.mSimSlotId));
            itemStatusList.add(true);
            entryValues.add(siminfo.mSimInfoId);
        }

        List<String> normalListVoice = getNormalItem(GenSimSettingType.VOICE);
        Xlog.d(TAG,"normalListVoice = " + normalListVoice.size());
        mVoiceCallSimSetting.setEntriesData(mSimInfoList, simIndicatorList, normalListVoice, itemStatusList);
        mVoiceCallSimSetting.setEntryValues(getEntryValues(entryValues,GenSimSettingType.VOICE));
        
        //only if more than one card need to add alway ask item
        List<String> normalListSms = getNormalItem(GenSimSettingType.SMS);
        Xlog.d(TAG,"normalListSms = " + normalListSms.size());
        mSmsSimSetting.setEntriesData(mSimInfoList, simIndicatorList, normalListSms, itemStatusList);
        mSmsSimSetting.setEntryValues(getEntryValues(entryValues,GenSimSettingType.SMS));
        // only when vt call support then enable to add sim info into
        if (mVTCallItemAvailable) {
            List<String> normalListVt = getNormalItem(GenSimSettingType.VIDEO);
            Xlog.d(TAG,"normalListVt = " + normalListVt.size());
            mVideoCallSimSetting.setEntriesData(mSimInfoList, simIndicatorList, normalListVt, itemStatusList);
            mVideoCallSimSetting.setEntryValues(getEntryValues(entryValues,GenSimSettingType.VIDEO));
        }
        // for data connection always add close item no matter how many sim card insert
        List<String> normalListGprs = getNormalItem(GenSimSettingType.DATA);
        Xlog.d(TAG,"normalListGprs = " + normalListGprs.size());
        mGprsSimSetting.setEntriesData(mSimInfoList, simIndicatorList, normalListGprs, itemStatusList);
        mGprsSimSetting.setEntryValues(getEntryValues(entryValues,GenSimSettingType.DATA));
        setSimSelectEnableState();
    }
    
    /**
     * Based on consistent UI now the general setttings need to be disable 
     * if there is only one item available to choose
     */
    private void setSimSelectEnableState() {
        if (mVTCallItemAvailable) {
            if (mVideoCallSimSetting.getItemCount() <= 1) {
                mVideoCallSimSetting.setEnabled(false);    
            } else {
                if (mIs3GSwitchManualEnabled) {
                    mVideoCallSimSetting.setEnabled(true);    
                } else {
                    mVideoCallSimSetting.setEnabled(false);     
                }    
            }
        }
        if (mIsVoiceCapable) {
            if (mVoiceCallSimSetting.getItemCount() <= 1) {
                mVoiceCallSimSetting.setEnabled(false); 
            } else {
                mVoiceCallSimSetting.setEnabled(true); 
            }
        }
        if (mSmsSimSetting.getItemCount() <= 1) {
            mSmsSimSetting.setEnabled(false);
        } else {
            mSmsSimSetting.setEnabled(true);
        }
        /// Binding contacts disable if only one card inserted
        if (mSimNum == 1) {
            mSimAndContacts.setEnabled(false);
        } else if (mSimNum > 1) { 
            mSimAndContacts.setEnabled(true);
        }
    }
    
    private List<String> getNormalItem(GenSimSettingType type) {
        List<String> list = new ArrayList<String>();
        switch (type) {
            case VOICE:
                if (mIsVoipAvailable) {
                    Xlog.d(TAG, "set internet call item");
                    list.add(getString(R.string.gemini_intenet_call));
                    list.add(getString(R.string.gemini_default_sim_always_ask)); 
                } else {
                    if (mSimNum > 1) {
                        list.add(getString(R.string.gemini_default_sim_always_ask)); 
                    }    
                }
                mExt.customizeVoiceChoiceArray(list,mIsVoipAvailable);
            break;
            case SMS:
                if (mSimNum > 1) {
                    list.add(getString(R.string.gemini_default_sim_always_ask));
                }
                mExt.customizeSmsChoiceArray(list);
            break;
            case VIDEO:
            break;
            case DATA:
                if (mSimNum > 0) {
                    list.add(getString(R.string.gemini_default_sim_never));    
                }
            break;
            default:
                Xlog.d(TAG,"pass wrong type");
            break;
        }
        return list;
    }
    
    private List<Long> getEntryValues(List<Long> origList, GenSimSettingType type) {
        List<Long> list = new ArrayList<Long>(origList);
        switch (type) {
            case VOICE:
                if (mIsVoipAvailable) {
                    Xlog.d(TAG, "set internet call item");
                    list.add(Settings.System.VOICE_CALL_SIM_SETTING_INTERNET);
                    list.add(Settings.System.DEFAULT_SIM_SETTING_ALWAYS_ASK);
                } else {
                    if (mSimNum > 1) {
                        list.add(Settings.System.DEFAULT_SIM_SETTING_ALWAYS_ASK);
                    }
                }
                break;
            case SMS:
                if (mSimNum > 1) {
                    list.add(Settings.System.DEFAULT_SIM_SETTING_ALWAYS_ASK);
                }
                mExt.customizeSmsChoiceValueArray(list);
                break;
            case VIDEO:
                Xlog.d(TAG,"only sim id no need add other items");
                break;
            case DATA:
                if (mSimNum > 0) {
                    list.add(Settings.System.GPRS_CONNECTION_SIM_SETTING_NEVER);
                }
                break;
            default:
                break;
        }
        return list;
    }
    
    private int current3GSlotId() {
        int slot3G = VIDEO_CALL_OFF;
        try {
            if (mTelephonyEx != null) {
                slot3G = mTelephonyEx.get3GCapabilitySIM();
            }
        } catch (RemoteException e) {
            Xlog.e(TAG, "mTelephonyEx exception");
        }
        return slot3G;
    }

    private void setPreferenceProperty() {
        long voicecallID = getDataValue(Settings.System.VOICE_CALL_SIM_SETTING);
        long smsID = getDataValue(Settings.System.SMS_SIM_SETTING);
        long dataconnectionID;
        if (FeatureOption.MTK_GEMINI_SUPPORT) {
            dataconnectionID = getDataValue(Settings.System.GPRS_CONNECTION_SIM_SETTING);
        } else {
            // single sim data connection get from connectivity manager, close data connection 0
            dataconnectionID = mSimNum > 0 && mConnService.getMobileDataEnabled() ? mSimInfoList.get(0).mSimInfoId :
                                                                     Settings.System.GPRS_CONNECTION_SIM_SETTING_NEVER;
        }
        int videocallSlotID = current3GSlotId();
        Xlog.i(TAG, "voicecallID =" + voicecallID + " smsID =" + smsID
                + " dataconnectionID =" + dataconnectionID
                + " videocallSlotID =" + videocallSlotID);
        mVoiceCallSimSetting.setValue(voicecallID);
        if (voicecallID == Settings.System.DEFAULT_SIM_NOT_SET) {
            mVoiceCallSimSetting.setSummary(R.string.apn_not_set);
        }
        mSmsSimSetting.setValue(smsID);
        mGprsSimSetting.setValue(dataconnectionID);
        mGprsSimSetting.setEnabled(isGPRSEnable());
        
        if (mVTCallItemAvailable) {
            if (videocallSlotID == VIDEO_CALL_OFF || (!mIsManuSelected && !mIsManuAllowed)) {
                mIs3gOff = true;
                mVideoCallSimSetting.setSummary(R.string.gemini_default_sim_3g_off);
            } else {
                SimInfoRecord siminfo = findSIMInofBySlotId(videocallSlotID);
                if (siminfo != null) {
                    mVideoCallSimSetting.setValue(siminfo.mSimInfoId);
                }
            }
            try {
                if (mTelephonyEx != null) {
                    ///M: if only one card inserted also disable the video item
                    mVideoCallSimSetting.setEnabled(!(mIs3gOff || mTelephonyEx.is3GSwitchLocked() || 
                                                      mSimNum <= 1 || !mIs3GSwitchManualEnabled));
                    Xlog.i(TAG, "mIs3gOff=" + mIs3gOff);
                    Xlog.i(TAG, "mTelephonyEx.is3GSwitchLocked() is "
                            + mTelephonyEx.is3GSwitchLocked());
                    }
            } catch (RemoteException e) {
                    Xlog.e(TAG, "mTelephonyEx exception");
            }
        }
    }

    @Override
    public boolean onPreferenceChange(Preference arg0, Object arg1) {
        final String key = arg0.getKey();
        Xlog.i(TAG, "Enter onPreferenceChange function with " + key);
        if (KEY_VOICE_CALL_SIM_SETTING.equals(key)) {
            Settings.System.putLong(getContentResolver(),
                    Settings.System.VOICE_CALL_SIM_SETTING, (Long) arg1);
            Intent intent = new Intent(
                    Intent.ACTION_VOICE_CALL_DEFAULT_SIM_CHANGED);
            intent.putExtra("simid", (Long) arg1);
            getActivity().sendBroadcast(intent);
            Xlog.d(TAG, "send broadcast voice call change with simid="
                    + (Long) arg1);
            updateDefaultSIMSummary(mVoiceCallSimSetting, (Long) arg1);
        } else if (KEY_VIDEO_CALL_SIM_SETTING.equals(key)) {
            if (FeatureOption.MTK_GEMINI_3G_SWITCH) {
                mSelectedVideoSimId = (Long) arg1;
                showDialog(DIALOG_3G_MODEM_SWITCH_CONFIRM);
                setOnCancelListener(new DialogInterface.OnCancelListener() {
                    public void onCancel(DialogInterface dialog) {
                        updateVideoCallDefaultSIM();
                    }
                });
            }
        } else if (KEY_SMS_SIM_SETTING.equals(key)) {
            Settings.System.putLong(getContentResolver(),
                    Settings.System.SMS_SIM_SETTING, (Long) arg1);
            Intent intent = new Intent(Intent.ACTION_SMS_DEFAULT_SIM_CHANGED);
            intent.putExtra("simid", (Long) arg1);
            getActivity().sendBroadcast(intent);
            Xlog.d(TAG, "send broadcast sms change with simid=" + (Long) arg1);
            updateDefaultSIMSummary(mSmsSimSetting, (Long) arg1);
        } else if (KEY_GPRS_SIM_SETTING.equals(key)) {
            long simid = ((Long) arg1).longValue();
            Xlog.d(TAG, "value=" + simid);
            int slotId = GeminiUtils.getSimSlotIdBySimInfoId(simid, mSimInfoList);
            if (slotId != GeminiUtils.UNDEFINED_SLOT_ID && getSimIndicator(slotId) == PhoneConstants.SIM_INDICATOR_LOCKED) {
                mCellConnMgr.handleCellConn(slotId, GeminiUtils.PIN1_REQUEST_CODE);
                return true;
            }
            ///M: only gemini need to show a dialop @{
            if (FeatureOption.MTK_GEMINI_SUPPORT) {
                if (simid == 0) {
                    ///M: turn off data connection no need to get whether to show connection dlg
                    mDataSwitchMsgIndex = -1;    
                } else {
                    mDataSwitchMsgIndex = dataSwitchConfirmDlgMsg(simid);    
                }
            }
            ///@}
            if (mDataSwitchMsgIndex == -1 || !FeatureOption.MTK_GEMINI_SUPPORT) {
                switchGprsDefaultSIM(simid);
            } else {
                mSelectedGprsSimId = simid;
                showDialog(DIALOG_GPRS_SWITCH_CONFIRM);
                setOnCancelListener(new DialogInterface.OnCancelListener() {
                    public void onCancel(DialogInterface dialog) {
                        updateDataConnPrefe();
                    }
                });
            }
        }
        return true;
    }

    @Override
    public void onPause() {
        super.onPause();
        Xlog.d(TAG, "OnPause()");
        getContentResolver().unregisterContentObserver(mGprsDefaultSIMObserver);
        getContentResolver().unregisterContentObserver(mVoiceCallObserver);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Xlog.d(TAG, "onDestroy()");
        getActivity().unregisterReceiver(mSimReceiver);
        if (!FeatureOption.MTK_GEMINI_SUPPORT) {
            mTelephonyManager.listen(mPhoneServiceListener,PhoneStateListener.LISTEN_NONE);
        }
        if (FeatureOption.MTK_GEMINI_SUPPORT) {
            try {
                if (mTelephony != null) {
                    mTelephony.unregisterForSimModeChange(mSwitchRadioStateMsg.getBinder());
                }
            } catch (RemoteException e) {
                Xlog.e(TAG, "mTelephony exception");
                return;
            }    
        }
        mCellConnMgr.unregister();
        mHandler.removeCallbacksAndMessages(null);
        if ("tablet".equals(SystemProperties.get("ro.build.characteristics"))) {
            setStatusBarEnableStatus(true);
        }
    }

    private void updateDefaultSIMSummary(SimSelectDialogPreference pref, Long simid) {
        Xlog.d(TAG, "updateDefaultSIMSummary() with simid=" + simid);
        if (simid > 0) {
            SimInfoRecord siminfo = getSIMInfoById(simid);
            if (siminfo != null) {
                pref.setSummary(siminfo.mDisplayName);
            }
        } else if (simid == Settings.System.VOICE_CALL_SIM_SETTING_INTERNET) {
            pref.setSummary(R.string.gemini_intenet_call);
        } else if (simid == Settings.System.DEFAULT_SIM_SETTING_ALWAYS_ASK) {
            pref.setSummary(R.string.gemini_default_sim_always_ask);
        } else if (simid == Settings.System.GPRS_CONNECTION_SIM_SETTING_NEVER) {
            pref.setSummary(R.string.gemini_default_sim_never);
        } else if (simid == Settings.System.SMS_SIM_SETTING_AUTO) {
            mExt.updateDefaultSIMSummary(pref, simid);
        }
    }

    /**
     * Get the corresponding siminfo by simid
     * 
     * @param simid
     *            is the sim card id
     */
    private SimInfoRecord getSIMInfoById(Long simid) {
        for (SimInfoRecord siminfo : mSimInfoList) {
            if (siminfo.mSimInfoId == simid) {
                return siminfo;
            }
        }
        Xlog.d(TAG, "Error there is no correct siminfo found by simid " + simid);
        return null;
    }

    private int dataSwitchConfirmDlgMsg(long simid) {
        SimInfoRecord siminfo = findSIMInfoBySimId(simid);
        TelephonyManagerEx telephonyManagerEx = TelephonyManagerEx.getDefault();
        boolean isInRoaming = telephonyManagerEx.isNetworkRoaming(siminfo.mSimSlotId);
        boolean isRoamingDataAllowed = (siminfo.mDataRoaming == SimInfoManager.DATA_ROAMING_ENABLE);
        Xlog.d(TAG, "isInRoaming=" + isInRoaming + " isRoamingDataAllowed="
                + isRoamingDataAllowed);
        // by support 3G switch when data connection switch
        // and to a slot not current set 3G service
        if (isInRoaming) {
            if (!isRoamingDataAllowed) {
                if (FeatureOption.MTK_GEMINI_3G_SWITCH  && mIs3GSwitchManualEnabled) {
                    if (siminfo.mSimSlotId != current3GSlotId()) {
                        // under roaming but not abled and switch card is not 3G
                        // slot, \
                        // to pormpt user turn on roaming and how to modify to
                        // 3G service
                        return R.string.gemini_3g_disable_warning_case3;
                    } else {
                        // switch card is 3G slot but not able to roaming
                        // so only prompt to turn on roaming
                        return R.string.gemini_3g_disable_warning_case0;
                    }
                } else {
                    // no support 3G service so only prompt user to turn on
                    // roaming
                    return R.string.gemini_3g_disable_warning_case0;
                }
            } else {
                if (FeatureOption.MTK_GEMINI_3G_SWITCH  && mIs3GSwitchManualEnabled) {
                    if (siminfo.mSimSlotId != current3GSlotId()) {
                        // by support 3g switch and switched sim is not
                        // 3g slot to prompt user how to modify 3G service
                        return R.string.gemini_3g_disable_warning_case1;
                    }
                }
            }
        } else {
            if (FeatureOption.MTK_GEMINI_3G_SWITCH
                    && mIs3GSwitchManualEnabled
                    && siminfo.mSimSlotId != current3GSlotId()) {
                // not in roaming but switched sim is not 3G
                // slot so prompt user to modify 3G service
                return R.string.gemini_3g_disable_warning_case1;
            }

        }
        return -1;
    }

    private SimInfoRecord findSIMInfoBySimId(long simid) {
        for (SimInfoRecord siminfo : mSimInfoList) {
            if (siminfo.mSimInfoId == simid) {
                return siminfo;
            }
        }
        Xlog.d(TAG, "Error happend on findSIMInfoBySimId no siminfo find");
        return null;
    }

    private SimInfoRecord findSIMInofBySlotId(int mslot) {
        for (SimInfoRecord siminfo : mSimInfoList) {
            if (siminfo.mSimSlotId == mslot) {
                return siminfo;
            }
        }
        Xlog.d(TAG, "Error happend on findSIMInofBySlotId no siminfo find");
        return null;
    }

    @Override
    public Dialog onCreateDialog(int id) {
        Xlog.d(TAG,"onCreateDialog() with id = " + id);
        Builder builder = new AlertDialog.Builder(getActivity());
        AlertDialog alertDlg;
        switch (id) {
        case PROGRESS_DIALOG:
            ProgressDialog dialog = new ProgressDialog(getActivity());
            dialog.setMessage(getResources().getString(mProDlgMsgId));
            dialog.setIndeterminate(true);
            if (mProDlgMsgId == R.string.gemini_3g_modem_switching_message) {
                Xlog.d(TAG,"3G switch to dispatch home key");
                Window win = dialog.getWindow();
                WindowManager.LayoutParams lp = win.getAttributes();
                lp.flags |= WindowManager.LayoutParams.FLAG_HOMEKEY_DISPATCHED;
                win.setAttributes(lp);
                setStatusBarEnableStatus(false);
            }
            return dialog;
        case DIALOG_GPRS_SWITCH_CONFIRM:
            builder.setTitle(android.R.string.dialog_alert_title);
            builder.setIcon(android.R.drawable.ic_dialog_alert);
            builder.setMessage(getResources().getString(mDataSwitchMsgIndex));
            builder.setPositiveButton(android.R.string.yes,
                    new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog,int whichButton) {
                            if (mDataSwitchMsgIndex == R.string.gemini_3g_disable_warning_case0 
                                    || mDataSwitchMsgIndex == R.string.gemini_3g_disable_warning_case3) {
                                enableDataRoaming(mSelectedGprsSimId);
                            }
                            switchGprsDefaultSIM(mSelectedGprsSimId);
                        }
                    });
            builder.setNegativeButton(android.R.string.no,
                    new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog,int whichButton) {
                            updateDataConnPrefe();
                        }
                    });
            alertDlg = builder.create();
            return alertDlg;
        case DIALOG_3G_MODEM_SWITCH_CONFIRM:
            builder.setTitle(android.R.string.dialog_alert_title);
            builder.setIcon(android.R.drawable.ic_dialog_alert);
            builder.setMessage(getResources().getString(
                    R.string.gemini_3g_modem_switch_confirm_message));
            builder.setPositiveButton(android.R.string.yes,
                    new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog,int whichButton) {
                            switchVideoCallDefaultSIM(mSelectedVideoSimId);
                        }
                    });
            builder.setNegativeButton(android.R.string.no,
                    new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog,int whichButton) {
                            updateVideoCallDefaultSIM();
                        }
                    });
            alertDlg = builder.create();
            return alertDlg;
        default:
            return null;
        }
    }
    
    private int getSimIndicator(int slotId) {
        Xlog.d(TAG,"getSimIndicator---slotId=" + slotId);
        if (isRadioOff()) {
            Xlog.d(TAG,"Force the state to be radio off as airplane mode or dual sim mode");
            return PhoneConstants.SIM_INDICATOR_RADIOOFF;    
        } else {
            return FeatureOption.MTK_GEMINI_SUPPORT ? 
                    GeminiUtils.getSimIndicatorGemini(getContentResolver(), mTelephonyEx,slotId) : 
                    GeminiUtils.getSimIndicator(getContentResolver(), mTelephony);
        }
    }

    private void switchSimRadioState(int slot, boolean isChecked) {
        Xlog.d(TAG,"switchSimRadioState");
        int dualSimMode = Settings.System.getInt(this.getContentResolver(),
                Settings.System.DUAL_SIM_MODE_SETTING, -1);
        Xlog.i(TAG, "The current dual sim mode is " + dualSimMode);
        /* {@M Auto open the other card's data connection. when current card is radio off */ 
        mExt.setToClosedSimSlot(-1);
        /*@}*/
        int dualState = 0;
        boolean isRadioOn = false;
        switch (dualSimMode) {
        case ALL_RADIO_OFF:
            if (slot == PhoneConstants.GEMINI_SIM_1) {
                dualState = SIM_SLOT_1_RADIO_ON;
            } else if (slot == PhoneConstants.GEMINI_SIM_2) {
                dualState = SIM_SLOT_2_RADIO_ON;
            }
            Xlog.d(TAG, "Turning on only sim " + slot);
            isRadioOn = true;
            break;
        case SIM_SLOT_1_RADIO_ON:
            if (slot == PhoneConstants.GEMINI_SIM_1) {
                if (isChecked) {
                    Xlog.d(TAG,"try to turn on slot 1 again since it is already on");
                    dualState = dualSimMode;
                    isRadioOn = true; 
                } else {
                    dualState = ALL_RADIO_OFF;
                    isRadioOn = false;    
                }
                Xlog.d(TAG, "Turning off sim " + slot
                        + " and all sim radio is off");
            } else if (slot == PhoneConstants.GEMINI_SIM_2) {
                if (mIsSlot1Insert) {
                    dualState = ALL_RADIO_ON;
                    Xlog.d(TAG, "sim 0 was radio on and now turning on sim "
                            + slot);
                } else {
                    dualState = SIM_SLOT_2_RADIO_ON;
                    Xlog.d(TAG, "Turning on only sim " + slot);
                }
                isRadioOn = true;
            }
            break;
        case SIM_SLOT_2_RADIO_ON:
            if (slot == PhoneConstants.GEMINI_SIM_2) {
                if (isChecked) {
                    Xlog.d(TAG,"try to turn on slot 2 again since it is already on");
                    dualState = dualSimMode;
                    isRadioOn = true;     
                } else {
                    dualState = ALL_RADIO_OFF;
                    isRadioOn = false;   
                }
                Xlog.d(TAG, "Turning off sim " + slot
                        + " and all sim radio is off");
            } else if (slot == PhoneConstants.GEMINI_SIM_1) {
                if (mIsSlot2Insert) {
                    dualState = ALL_RADIO_ON;
                    Xlog.d(TAG, "sim 1 was radio on and now turning on sim " + slot);
                } else {
                    dualState = SIM_SLOT_1_RADIO_ON;
                    Xlog.d(TAG, "Turning on only sim " + slot);
                }
                isRadioOn = true;
            }
            break;
        case ALL_RADIO_ON:
            if (!isChecked) {
                if (slot == PhoneConstants.GEMINI_SIM_1) {
                    dualState = SIM_SLOT_2_RADIO_ON;
                    /* {@M Auto open the other card's data connection. when current card is radio off */ 
                    mExt.setToClosedSimSlot(PhoneConstants.GEMINI_SIM_1);
                    Xlog.d(TAG,"setToClosedSimSlot(PhoneConstants.GEMINI_SIM_1)");
                    /*@}*/
                    } else if (slot == PhoneConstants.GEMINI_SIM_2) {
                        dualState = SIM_SLOT_1_RADIO_ON;
                        /* {@M Auto open the other card's data connection. when current card is radio off */ 
                        mExt.setToClosedSimSlot(PhoneConstants.GEMINI_SIM_2);
                        Xlog.d(TAG,"setToClosedSimSlot(PhoneConstants.GEMINI_SIM_2)");
                        /*@}*/
                    }
                Xlog.d(TAG, "Turning off only sim " + slot);
                isRadioOn = false;    
            } else {
                Xlog.d(TAG,"try to turn on but actually they are all on");
                dualState = dualSimMode;
                isRadioOn = true;    
            }
            break;
        default:
            Xlog.d(TAG, "Error not correct values");
            return;
        }
        ///M: only gemini support to show a dialog, for single sim do not show this dlg @{
        if (FeatureOption.MTK_GEMINI_SUPPORT) {
            int msgId = 0;
            if (isRadioOn) {
                msgId = R.string.gemini_sim_mode_progress_activating_message;
            } else {
                msgId = R.string.gemini_sim_mode_progress_deactivating_message;
            }
            showProgressDlg(msgId);
        }
        ///@}
        Xlog.d(TAG, "dualState=" + dualState + " isRadioOn=" + isRadioOn);
        Settings.System.putInt(this.getContentResolver(),
                Settings.System.DUAL_SIM_MODE_SETTING, dualState);
        Intent intent = new Intent(Intent.ACTION_DUAL_SIM_MODE_CHANGED);
        intent.putExtra(Intent.EXTRA_DUAL_SIM_MODE, dualState);
        getActivity().sendBroadcast(intent);
    }

     private int getInverseNumber(int num) {
        int constNum = 4;
        String inverseStr = Integer.toBinaryString(~num);
        String str = inverseStr.substring(inverseStr.length() - constNum);
        int inverseNum = Integer.parseInt(str , 2);
        Xlog.d(TAG,"inverseNum = " + inverseNum);
        return inverseNum;
    }

    private void switchGeminiPlusSimRadioState(int slot) {
        Xlog.d(TAG,"switchGeminiPlusSimRadioState");
        int dualSimMode = Settings.System.getInt(this.getContentResolver(),
                Settings.System.DUAL_SIM_MODE_SETTING, -1);
        int modeSlot = slot;
        int dualState;
        boolean isRadioOn = false;
        Xlog.i(TAG, "The current dual sim mode is " + dualSimMode + "with slot = " + slot);
        switch (slot) {
            case PhoneConstants.GEMINI_SIM_1:
            modeSlot = 1;//01
            break;
            case PhoneConstants.GEMINI_SIM_2:
            modeSlot = 2;//10
            break;
            case PhoneConstants.GEMINI_SIM_3:
            modeSlot = 4;//100
            break;
            case PhoneConstants.GEMINI_SIM_4:
            modeSlot = 8;//1000
            break;
            default:
            Xlog.d(TAG,"error of the slot = " + slot);
            break;
        }
        if ((dualSimMode & modeSlot) > 0) {
            dualState = dualSimMode & getInverseNumber(modeSlot);
            isRadioOn = false;
        } else {
            dualState = dualSimMode | modeSlot;
            isRadioOn = true;
        }
        ///M: only gemini support to show a dialog, for single sim do not show this dlg @{
        if (FeatureOption.MTK_GEMINI_SUPPORT) {
            int msgId = 0;
            if (isRadioOn) {
                msgId = R.string.gemini_sim_mode_progress_activating_message;
            } else {
                msgId = R.string.gemini_sim_mode_progress_deactivating_message;
            }
            showProgressDlg(msgId);
        }
        ///@}
        Xlog.d(TAG, "dualState=" + dualState + " isRadioOn=" + isRadioOn);
        Settings.System.putInt(this.getContentResolver(),
                Settings.System.DUAL_SIM_MODE_SETTING, dualState);
        Intent intent = new Intent(Intent.ACTION_DUAL_SIM_MODE_CHANGED);
        intent.putExtra(Intent.EXTRA_DUAL_SIM_MODE, dualState);
        getActivity().sendBroadcast(intent);
    }

    private void dealWithSwitchComplete() {
        Xlog.d(TAG, "dealWithSwitchComplete()");
        Xlog.d(TAG, "mIsSIMModeSwitching is " + mIsSIMRadioSwitching);
        if (getActivity() == null) {
            return;
        }
        // dual sim mode changed so need to update switch state
        for (SimCardInfoPreference pref : mSimInfoPreference) {
            Xlog.d(TAG,"Since the dual sim mode changed, update switch state");
            pref.setChecked(isRadioInOn(pref.getSimSlotId()));
        }
        mGprsSimSetting.setEnabled(isGPRSEnable());
        if (!mIsSIMRadioSwitching) {
            Xlog.i(TAG, "dual mode change by other not sim management");
        } else {
            if (!isDialogShowing(PROGRESS_DIALOG)) {
                Xlog.d(TAG,"Dialog is not show yet but dual sim modechange has sent msg");
                mHandler.sendEmptyMessageDelayed(DIALOG_NOT_SHOW_SUCCESS_MSG, DIALOG_NOT_REMOVE_TIME_OUT);
            } else {
                removeProgDlg();
                mIsSIMRadioSwitching = false;    
            }
        }
        /* {@M Auto open the other card's data connection. when current card is radio off */
        mExt.showChangeDataConnDialog(this, isResumed());
        /*@}*/
    }

    private void removeProgDlg() {
        Xlog.d(TAG,"removeProgDlg()");
        if (isResumed()) {
            Xlog.d(TAG,"Progress Dialog removed");
            removeDialog(PROGRESS_DIALOG);    
        } else {
            Xlog.d(TAG,"under onpause not enable to remove set flag as true");
            mRemoveProgDlg = true;
        }    
    }
    /**
     *show attach gprs dialog and revent time out to send a delay msg
     * 
     */
    private void showDataConnDialog(boolean isConnect) {
        long delaytime = 0;
        if (isConnect) {
            delaytime = ATTACH_DATA_CONN_TIME_OUT;
        } else {
            delaytime = DETACH_DATA_CONN_TIME_OUT;
        }
        mHandler.sendEmptyMessageDelayed(DATA_SWITCH_TIME_OUT_MSG, delaytime);
        showProgressDlg(R.string.gemini_data_connection_progress_message);
        mIsDataConnectActing = true;
    }

    private void showProgressDlg(int dialogMsg) {
        Xlog.d(TAG,"showProgressDlg() with dialogMsg = " + dialogMsg);
        mProDlgMsgId = dialogMsg;
        showDialog(PROGRESS_DIALOG);
        setCancelable(false);
    }
    private static PhoneConstants.DataState getMobileDataState(Intent intent) {
        String str = intent.getStringExtra(PhoneConstants.STATE_KEY);
        if (str != null) {
            return Enum.valueOf(PhoneConstants.DataState.class, str);
        } else {
            return PhoneConstants.DataState.DISCONNECTED;
        }
    }

    /*
     * Update dataconnection prefe with new selected value and new sim name as
     * summary
     */
    private void updateDataConnPrefe() {
        long simid = Settings.System.GPRS_CONNECTION_SIM_SETTING_NEVER;
        if (FeatureOption.MTK_GEMINI_SUPPORT) {
            simid = Settings.System.getLong(getContentResolver(),
                    Settings.System.GPRS_CONNECTION_SIM_SETTING,
                    Settings.System.DEFAULT_SIM_NOT_SET);
        } else {
            if (mConnService.getMobileDataEnabled()) {
                simid = mSimInfoList.get(0).mSimInfoId;
            }
        }
        Xlog.i(TAG, "Gprs connection SIM changed with simid is " + simid);
        mGprsSimSetting.setValue(simid);
    }
    
    /**
     * update the voice call dialog.
     */
    private void updateVoiceCallPrefe() {
        long voicecallID = getDataValue(Settings.System.VOICE_CALL_SIM_SETTING);
        Xlog.i(TAG, "voice call SIM changed with simid is " + voicecallID);
        mVoiceCallSimSetting.setValue(voicecallID);
    }

    /**
     * update video call default SIM value and summary
     */

    private void updateVideoCallDefaultSIM() {
        Xlog.d(TAG, "updateVideoCallDefaultSIM()");
        if (mTelephonyEx != null) {
            try {
                int slotId = mTelephonyEx.get3GCapabilitySIM();
                Xlog.d(TAG, "updateVideoCallDefaultSIM()---slotId=" + slotId);
                if (slotId < 0) {
                    return;
                }
                SimInfoRecord siminfo = findSIMInofBySlotId(slotId);
                if (siminfo != null) {
                    mVideoCallSimSetting.setValue(siminfo.mSimInfoId);
                } 
            } catch (RemoteException e) {
                Xlog.e(TAG, "mTelephonyEx exception");
            }
        }
    }

    /**
     * Check if voip is supported and is enabled
     */
    private boolean isVoipAvailable() {
        int isInternetCallEnabled = android.provider.Settings.System.getInt(
                getContentResolver(),
                android.provider.Settings.System.ENABLE_INTERNET_CALL, 0);
        return (SipManager.isVoipSupported(getActivity()))
                && (isInternetCallEnabled != 0);

    }

    /**
     * switch data connection default SIM
     * 
     * @param value: sim id of the new default SIM
     */
    private void switchGprsDefaultSIM(long simid) {
        Xlog.d(TAG, "switchGprsDefaultSIM() with simid=" + simid);
        if (simid < 0 || simid > 0 && !isSimInsertedIn(simid)) {
            Xlog.d(TAG,"simid = " + simid + " not available anymore");
            return;
        }
        boolean isConnect = (simid > 0) ? true : false;
        if (FeatureOption.MTK_GEMINI_SUPPORT) {
            long curConSimId = Settings.System.getLong(getContentResolver(),
                Settings.System.GPRS_CONNECTION_SIM_SETTING,
                Settings.System.DEFAULT_SIM_NOT_SET);
            Xlog.d(TAG,"curConSimId=" + curConSimId);
            if (simid == curConSimId) {
                return;
            }
            Intent intent = new Intent(Intent.ACTION_DATA_DEFAULT_SIM_CHANGED);
            intent.putExtra("simid", simid);
            // simid>0 means one of sim card is selected
            // and <0 is close id which is -1 so mean disconnect
            getActivity().sendBroadcast(intent);   
            showDataConnDialog(isConnect); 
        } else {
            ///M: for consistent_UI only sinlge sim use connectivity set the state of data connection
            mConnService.setMobileDataEnabled(isConnect);
        }
    }

    private void enableDataRoaming(long value) {
        Xlog.d(TAG, "enableDataRoaming with SimId=" + value);
        SimInfoRecord simInfo = SimInfoManager.getSimInfoById(getActivity(), value);
        int slotId = -1;
        if (simInfo != null) {
            slotId = simInfo.mSimSlotId;
        } else {
            Xlog.e(TAG, "SimInfoManager query failed");
            return;
        }
        
        //Fix bug CR ALPS00609944 handle sim is plug out and do nothing
        if (isSimInsertedIn(value)) {
            try {
                if (mTelephonyManagerEx != null) {
                    
                    mTelephonyManagerEx.setDataRoamingEnabled(true, slotId);
                }
            } catch (RemoteException e) {
                Xlog.e(TAG, "mTelephony exception");
                return;
            }
            SimInfoManager.setDataRoaming(getActivity(), SimInfoManager.DATA_ROAMING_ENABLE, value);    
        } else {
            Xlog.d(TAG,"sim Id " + value + " not inserted in phone do nothing");
        }
    }

    /**
     * switch video call prefer sim if 3G switch feature is enabled
     * 
     * @param slotID
     */
    private void switchVideoCallDefaultSIM(long simid) {
        Xlog.i(TAG, "switchVideoCallDefaultSIM to " + simid);
        if (mTelephonyEx != null) {
            SimInfoRecord siminfo = findSIMInfoBySimId(simid);
            Xlog.i(TAG, "siminfo = " + siminfo);
            if (siminfo == null) {
                Xlog.d(TAG, "Error no corrent siminfo found");
                return;
            }
            try {
                Xlog.i(TAG, "set sim slot " + siminfo.mSimSlotId
                        + " with 3G capability");
                if (mTelephonyEx.set3GCapabilitySIM(siminfo.mSimSlotId)) {
                    showProgressDlg(R.string.gemini_3g_modem_switching_message);
                    mHandler.sendEmptyMessageDelayed(DATA_3G_SWITCH_TIME_OUT_MSG, SWITCH_3G_TIME_OUT);
                } else {
                    updateVideoCallDefaultSIM();
                }
            } catch (RemoteException e) {
                Xlog.e(TAG, "mTelephonyEx exception");
                return;
            }

        }
    }

    private boolean isSupportVTCallSetting() {
        return (FeatureOption.MTK_VT3G324M_SUPPORT && FeatureOption.MTK_GEMINI_3G_SWITCH 
                && mIs3GSwitchManualEnabled && mIsVoiceCapable);
    }
    /**
     * When switching modem, the status bar should be disabled
     * @param enabled
     */
    private void setStatusBarEnableStatus(boolean enabled) {
        Xlog.i(TAG, "setStatusBarEnableStatus(" + enabled + ")");
        StatusBarManager statusBarManager;
        statusBarManager = (StatusBarManager)getSystemService(Context.STATUS_BAR_SERVICE);
        if (statusBarManager != null) {
            if (enabled) {
                statusBarManager.disable(StatusBarManager.DISABLE_NONE);
            } else {
                statusBarManager.disable(StatusBarManager.DISABLE_EXPAND |
                                         StatusBarManager.DISABLE_RECENT |
                                         StatusBarManager.DISABLE_HOME);
            }
        } else {
            Xlog.e(TAG, "Fail to get status bar instance");
        }
    }

    private long getDataValue(String dataString) {
        return Settings.System.getLong(getContentResolver(), dataString,
                Settings.System.DEFAULT_SIM_NOT_SET);
    }

    /**
     * Returns whether is in airplance or mms is under transaction
     * 
     * @return is airplane or mms is in transaction
     * 
     */
    private boolean isGPRSEnable() {
        boolean isMMSProcess = false;
        /// M: fix JE, CR ALPS01263732 {@
        if (getActivity() == null) {
            return false;
        }
        /// @}
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm != null) {
            NetworkInfo networkInfo = cm.getNetworkInfo(ConnectivityManager.TYPE_MOBILE_MMS);
            if (networkInfo != null) {
                NetworkInfo.State state = networkInfo.getState();
                Xlog.d(TAG,"mms state = " + state);
                isMMSProcess = (state == NetworkInfo.State.CONNECTING
                    || state == NetworkInfo.State.CONNECTED);
            }
        }
        boolean isRadioOff = isRadioOff();
        Xlog.d(TAG, "isMMSProcess=" + isMMSProcess + " isRadioOff="
                + isRadioOff);
        return !(isMMSProcess || isRadioOff);
    }
    /**
     * @return is airplane mode or all sim card is set on radio off
     * 
     */
    private boolean isRadioOff() {
        boolean isAllRadioOff = (Settings.System.getInt(getContentResolver(),
                Settings.System.AIRPLANE_MODE_ON, -1) == 1)
                || (Settings.System.getInt(getContentResolver(),
                        Settings.System.DUAL_SIM_MODE_SETTING, -1) == ALL_RADIO_OFF)
                || mSimNum == 0;
        Xlog.d(TAG, "isAllRadioOff=" + isAllRadioOff);
        return isAllRadioOff;
    }

    @Override
    public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen, Preference preference) {
        Xlog.i(TAG, "preference: " + preference.toString());
        String key = preference.getKey();
        if (preference instanceof SimCardInfoPreference) {
            Xlog.i(TAG, "onPreferenceTreeClick");
            SimCardInfoPreference simPreference = (SimCardInfoPreference) preference;
            long simId = simPreference.getSimInfoId();
            Bundle extras = new Bundle();
            extras.putLong(GeminiUtils.EXTRA_SIMID, simId);
            Xlog.i(TAG, "sim id is  " + simId);
            // oobe
            OobeUtils.startSimEditor(this, extras);
        } else {
            return super.onPreferenceTreeClick(preferenceScreen, preference);
        }
        return true;
    }
    
    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (isDialogShowing(PROGRESS_DIALOG)) {
            outState.putInt(PROGRESS_DIALOG_MSG_ID,mProDlgMsgId);
        } else if (isDialogShowing(DIALOG_GPRS_SWITCH_CONFIRM)) {
            outState.putInt(CONFIRM_DIALOG_MSG_ID,mDataSwitchMsgIndex);
        }
        outState.putLong(VT_SELECTED_ID, mSelectedVideoSimId);
        outState.putLong(GPRS_SELECTED_ID, mSelectedGprsSimId);
    }
    
    enum GenSimSettingType {
        VOICE, SMS, VIDEO, DATA
    }
}
