/*
 * Copyright (C) 2006 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.internal.telephony.dataconnection;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.database.ContentObserver;
import android.net.ConnectivityManager;
import android.net.LinkCapabilities;
import android.net.LinkProperties;
import android.net.NetworkInfo;
import android.net.TrafficStats;
import android.net.wifi.WifiManager;
import android.os.AsyncResult;
//[New R8 modem FD]
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.os.Messenger;
import android.os.SystemClock;
import android.os.SystemProperties;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.provider.Settings.SettingNotFoundException;
import android.telephony.ServiceState;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.EventLog;
import android.telephony.Rlog;

import com.android.internal.R;
import com.android.internal.telephony.dataconnection.DcFailCause;
import com.android.internal.telephony.dataconnection.DcAsyncChannel;
import com.android.internal.telephony.TelephonyIntents;
import com.android.internal.telephony.DctConstants;
import com.android.internal.telephony.DctConstants.State;
import com.android.internal.telephony.EventLogTags;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneBase;
import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.uicc.IccRecords;
import com.android.internal.telephony.uicc.UiccController;
import com.android.internal.util.AsyncChannel;
import com.android.internal.util.ArrayUtils;
import com.android.internal.util.Protocol;

//[New R8 modem FD]
import com.android.internal.telephony.gsm.FDModeType;
//import com.mediatek.common.telephony.gsm.FDModeType;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.PriorityQueue;

//MTK-START [mtk04070][111205][ALPS00093395]MTK added
import com.mediatek.common.featureoption.FeatureOption;
import static android.provider.Settings.System.GPRS_CONNECTION_SETTING;
import static android.provider.Settings.System.GPRS_CONNECTION_SETTING_DEFAULT;
import android.provider.Telephony.SIMInfo;
import android.provider.Telephony.SimInfo;
//MTK-END [mtk04070][111205][ALPS00093395]MTK added


/**
 * {@hide}
 */
public abstract class DcTrackerBase extends Handler {
    protected static final boolean DBG = true;
    protected static final boolean VDBG = true;
    protected static final boolean VDBG_STALL = true; // STOPSHIP if true
    protected static final boolean RADIO_TESTS = false;

    //[New R8 modem FD]
    protected static final String PROPERTY_RIL_FD_MODE = "ril.fd.mode";
    protected static final String PROPERTY_3G_SWITCH = "gsm.3gswitch";

    /**
     * Constants for the data connection activity:
     * physical link down/up
     */
    protected static final int DATA_CONNECTION_ACTIVE_PH_LINK_INACTIVE = 0;
    protected static final int DATA_CONNECTION_ACTIVE_PH_LINK_DOWN = 1;
    protected static final int DATA_CONNECTION_ACTIVE_PH_LINK_UP = 2;

    /** Delay between APN attempts.
        Note the property override mechanism is there just for testing purpose only. */
    protected static final int APN_DELAY_DEFAULT_MILLIS = 20000;

    /** Delay between APN attempts when in fail fast mode */
    protected static final int APN_FAIL_FAST_DELAY_DEFAULT_MILLIS = 3000;

    AlarmManager mAlarmManager;

    protected Object mDataEnabledLock = new Object();

    // responds to the setInternalDataEnabled call - used internally to turn off data
    // for example during emergency calls
    protected boolean mInternalDataEnabled = true;

    // responds to public (user) API to enable/disable data use
    // independent of mInternalDataEnabled and requests for APN access
    // persisted
    protected boolean mUserDataEnabled = true;

    // TODO: move away from static state once 5587429 is fixed.
    protected static boolean sPolicyDataEnabled = true;

    private boolean[] mDataEnabled = new boolean[DctConstants.APN_NUM_TYPES];

    private int mEnabledCount = 0;

    /* Currently requested APN type (TODO: This should probably be a parameter not a member) */
    protected String mRequestedApnType = PhoneConstants.APN_TYPE_DEFAULT;

    /** Retry configuration: A doubling of retry times from 5secs to 30minutes */
    protected static final String DEFAULT_DATA_RETRY_CONFIG = "default_randomization=2000,"
        + "5000,10000,20000,40000,80000:5000,160000:5000,"
        + "320000:5000,640000:5000,1280000:5000,1800000:5000";

    /** Retry configuration for secondary networks: 4 tries in 20 sec */
    protected static final String SECONDARY_DATA_RETRY_CONFIG =
            "max_retries=3, 5000, 5000, 5000";

    /** Slow poll when attempting connection recovery. */
    protected static final int POLL_NETSTAT_SLOW_MILLIS = 5000;
    /** Default max failure count before attempting to network re-registration. */
    protected static final int DEFAULT_MAX_PDP_RESET_FAIL = 3;

    /**
     * After detecting a potential connection problem, this is the max number
     * of subsequent polls before attempting recovery.
     */
    protected static final int NO_RECV_POLL_LIMIT = 24;
    // 1 sec. default polling interval when screen is on.
    protected static final int POLL_NETSTAT_MILLIS = 1000;
    // 10 min. default polling interval when screen is off.
    protected static final int POLL_NETSTAT_SCREEN_OFF_MILLIS = 1000*60*10;
    // 2 min for round trip time
    protected static final int POLL_LONGEST_RTT = 120 * 1000;
    // Default sent packets without ack which triggers initial recovery steps
    protected static final int NUMBER_SENT_PACKETS_OF_HANG = 10;
    // how long to wait before switching back to default APN
    protected static final int RESTORE_DEFAULT_APN_DELAY = 1 * 60 * 1000;
    // system property that can override the above value
    protected static final String APN_RESTORE_DELAY_PROP_NAME = "android.telephony.apn-restore";
    // represents an invalid IP address
    protected static final String NULL_IP = "0.0.0.0";

    // Default for the data stall alarm while non-aggressive stall detection
    protected static final int DATA_STALL_ALARM_NON_AGGRESSIVE_DELAY_IN_MS_DEFAULT = 1000 * 60 * 6;
    // Default for the data stall alarm for aggressive stall detection
    protected static final int DATA_STALL_ALARM_AGGRESSIVE_DELAY_IN_MS_DEFAULT = 1000 * 60;
    // If attempt is less than this value we're doing first level recovery
    protected static final int DATA_STALL_NO_RECV_POLL_LIMIT = 1;
    // Tag for tracking stale alarms
    protected static final String DATA_STALL_ALARM_TAG_EXTRA = "data.stall.alram.tag";

    protected static final boolean DATA_STALL_SUSPECTED = true;
    protected static final boolean DATA_STALL_NOT_SUSPECTED = false;

    protected String RADIO_RESET_PROPERTY = "gsm.radioreset";

    protected static final String INTENT_RECONNECT_ALARM =
            "com.android.internal.telephony.data-reconnect";
    protected static final String INTENT_RECONNECT_ALARM_EXTRA_TYPE = "reconnect_alarm_extra_type";
    // TODO: See if we can remove INTENT_RECONNECT_ALARM
    //       having to have different values for GSM and
    //       CDMA. If so we can then remove the need for
    //       getActionIntentReconnectAlarm.
    protected static final String INTENT_RECONNECT_ALARM_EXTRA_REASON =
        "reconnect_alarm_extra_reason";

    //[mr2] TODO: removed start, with isDataSetupCompleteOk() , check wait for DcTracker
    // Used for debugging. Send the INTENT with an optional counter value with the number
    // of times the setup is to fail before succeeding. If the counter isn't passed the
    // setup will fail once. Example fail two times with FailCause.SIGNAL_LOST(-3)
    // adb shell am broadcast \
    //  -a com.android.internal.telephony.dataconnectiontracker.intent_set_fail_data_setup_counter \
    //  --ei fail_data_setup_counter 3 --ei fail_data_setup_fail_cause -3
    protected static final String INTENT_SET_FAIL_DATA_SETUP_COUNTER =
        "com.android.internal.telephony.dataconnection.dctrackerbase.intent_set_fail_data_setup_counter";
    protected static final String FAIL_DATA_SETUP_COUNTER = "fail_data_setup_counter";
    protected int mFailDataSetupCounter = 0;
    protected static final String FAIL_DATA_SETUP_FAIL_CAUSE = "fail_data_setup_fail_cause";
    protected DcFailCause mFailDataSetupFailCause = DcFailCause.ERROR_UNSPECIFIED;
    //[mr2] TODO: removed end
    
    //[mr2] added start
    protected static final String INTENT_RESTART_TRYSETUP_ALARM =
            "com.android.internal.telephony.data-restart-trysetup";
    protected static final String INTENT_RESTART_TRYSETUP_ALARM_EXTRA_TYPE =
            "restart_trysetup_alarm_extra_type";

    protected static final String INTENT_DATA_STALL_ALARM =
            "com.android.internal.telephony.data-stall";
    //[mr2] added end

    protected static final String DEFALUT_DATA_ON_BOOT_PROP = "net.def_data_on_boot";

    protected DcTesterFailBringUpAll mDcTesterFailBringUpAll;
    protected DcController mDcc;

    // member variables
    protected PhoneBase mPhone;
    protected UiccController mUiccController;
    protected AtomicReference<IccRecords> mIccRecords = new AtomicReference<IccRecords>();
    protected DctConstants.Activity mActivity = DctConstants.Activity.NONE;
    protected DctConstants.State mState = DctConstants.State.IDLE;
    protected Handler mDataConnectionTracker = null;

    /* Add by mtk01411 */
    protected boolean mRadioAvailable = false;
    protected boolean mRecordsLoaded = false;

    protected long mTxPkts;
    protected long mRxPkts;
    protected int mNetStatPollPeriod;
    protected boolean mNetStatPollEnabled = false;

    protected TxRxSum mDataStallTxRxSum = new TxRxSum(0, 0);
    // Used to track stale data stall alarms.
    protected int mDataStallAlarmTag = (int) SystemClock.elapsedRealtime();
    // The current data stall alarm intent
    protected PendingIntent mDataStallAlarmIntent = null;
    // Number of packets sent since the last received packet
    protected long mSentSinceLastRecv;
    // Controls when a simple recovery attempt it to be tried
    protected int mNoRecvPollCount = 0;
    // [KK] added:Refrence counter for enabling fail fast
    protected static int sEnableFailFastRefCounter = 0;    
    // True if data stall detection is enabled
    protected volatile boolean mDataStallDetectionEnabled = true;

    protected volatile boolean mFailFast = false;

    // True when in voice call
    protected boolean mInVoiceCall = false;

    // wifi connection status will be updated by sticky intent
    protected boolean mIsWifiConnected = false;

    /** Intent sent when the reconnect alarm fires. */
    protected PendingIntent mReconnectIntent = null;

    /** CID of active data connection */
    protected int mCidActive;

    // When false we will not auto attach and manually attaching is required.
    protected boolean mAutoAttachOnCreation = false;

    // State of screen
    // (TODO: Reconsider tying directly to screen, maybe this is
    //        really a lower power mode")
    protected boolean mIsScreenOn = true;

    //[New R8 modem FD]
    protected boolean mChargingMode = false;
    protected int mEnableFDOnCharing = 0;

    /** Allows the generation of unique Id's for DataConnection objects */
    protected AtomicInteger mUniqueIdGenerator = new AtomicInteger(0);

    /** The data connections. */
    protected HashMap<Integer, DataConnection> mDataConnections =
        new HashMap<Integer, DataConnection>();

    /** The data connection async channels */
    protected HashMap<Integer, DcAsyncChannel> mDataConnectionAcHashMap =
        new HashMap<Integer, DcAsyncChannel>();

    /** Convert an ApnType string to Id (TODO: Use "enumeration" instead of String for ApnType) */
    protected HashMap<String, Integer> mApnToDataConnectionId =
                                    new HashMap<String, Integer>();

    /** Phone.APN_TYPE_* ===> ApnContext */
    protected final ConcurrentHashMap<String, ApnContext> mApnContexts =
                                    new ConcurrentHashMap<String, ApnContext>();

    /** kept in sync with mApnContexts
     * Higher numbers are higher priority and sorted so highest priority is first */
    protected final PriorityQueue<ApnContext>mPrioritySortedApnContexts =
            new PriorityQueue<ApnContext>(5,
            new Comparator<ApnContext>() {
                public int compare(ApnContext c1, ApnContext c2) {
                    return c2.priority - c1.priority;
                }
            } );

    /* Currently active APN */
    protected ApnSetting mActiveApn;

    /** allApns holds all apns */
    protected ArrayList<ApnSetting> mAllApnSettings = null;

    /** preferred apn */
    protected ApnSetting mPreferredApn = null;

    /** Is packet service restricted by network */
    protected boolean mIsPsRestricted = false;

    /* Once disposed dont handle any messages */
    protected boolean mIsDisposed = false;

    protected ContentResolver mResolver;

    //[KK] added start, TODO: check this mechansim
    /* Set to true with CMD_ENABLE_MOBILE_PROVISIONING */
    protected boolean mIsProvisioning = false;

    /* The Url passed as object parameter in CMD_ENABLE_MOBILE_PROVISIONING */
    protected String mProvisioningUrl = null;

    /* Intent for the provisioning apn alarm */
    protected static final String INTENT_PROVISIONING_APN_ALARM =
            "com.android.internal.telephony.provisioning_apn_alarm";

    /* Tag for tracking stale alarms */
    protected static final String PROVISIONING_APN_ALARM_TAG_EXTRA = "provisioning.apn.alarm.tag";

    /* Debug property for overriding the PROVISIONING_APN_ALARM_DELAY_IN_MS */
    protected static final String DEBUG_PROV_APN_ALARM =
            "persist.debug.prov_apn_alarm";

    /* Default for the provisioning apn alarm timeout */
    protected static final int PROVISIONING_APN_ALARM_DELAY_IN_MS_DEFAULT = 1000 * 60 * 15;

    /* The provision apn alarm intent used to disable the provisioning apn */
    protected PendingIntent mProvisioningApnAlarmIntent = null;

    /* Used to track stale provisioning apn alarms */
    protected int mProvisioningApnAlarmTag = (int) SystemClock.elapsedRealtime();

    protected AsyncChannel mReplyAc = new AsyncChannel();
    //[KK] added end

    //MTK-START [mtk04070][111205][ALPS00093395]MTK added
    protected final String LOG_TAG = "DcTrackerBase";

    /* Add by vendor */
    private static final String INTENT_RECONNECT_ALARM_APN_TYPE ="apnType";
    //MTK-END [mtk04070][111205][ALPS00093395]MTK added

    // M: Roaming settings
    private static final String mRoamingSetting[] 
            = {Settings.Global.DATA_ROAMING, Settings.Global.DATA_ROAMING_2, Settings.Global.DATA_ROAMING_3, Settings.Global.DATA_ROAMING_4};

    private static final String PREFERENCE_GPRS = "com.mtk.GPRS";
    private static final String PREF_ATTACH_MODE = "ATTACH_MODE";
    private static final String PREF_ATTACH_MODE_SIM = "ATTACH_MODE_SIM";
    private static final int ATTACH_MODE_NOT_SPECIFY = -1;
    private static final String ACTION_SET_PS_ATTACH_MODE = "com.mtk.GPRS.ACTION_SET_PS_ATTACH_MODE";
    private static final String KEY_ATTACH_MODE = "attach_mode";

    public DctConstants.State getState() {
        //logd("getState = " + mState);
        return mState;
    }
    protected BroadcastReceiver mIntentReceiver = new BroadcastReceiver ()
    {
        @Override
        public void onReceive(Context context, Intent intent)
        {
            String action = intent.getAction();
            if (DBG) log("onReceive: action=" + action);
            if (action.equals(Intent.ACTION_SCREEN_ON)) {
                mIsScreenOn = true;
                stopNetStatPoll();
                startNetStatPoll();
                restartDataStallAlarm();

                //[New R8 modem FD]
                int FD_MD_Enable_Mode = Integer.parseInt(SystemProperties.get(PROPERTY_RIL_FD_MODE, "0"));               
                int FDSimID = SystemProperties.getInt("gsm.3gswitch", 1)-1;                
                if (DBG) log("FD_MD_Enable_Mode=" + FD_MD_Enable_Mode + ", 3gSimID=" + FDSimID + ", when switching to SCREEN ON");				
                if (FeatureOption.MTK_FD_SUPPORT && (FD_MD_Enable_Mode == 1)) {
                    //FD_MD_Enable_Mode == 1: It means that the Fast Dormancy polling & decision mechanism is implemented by modem side
                    if (mPhone.getMySimId() == FDSimID) {
                        mPhone.mCi.setFDMode(FDModeType.INFO_MD_SCREEN_STATUS.ordinal(), 1, -1, obtainMessage(DctConstants.EVENT_FD_MODE_SET));
                    }                    
                } else {
                    //FD_MD_Enable_Mode == 0: It means that the Fast Dormancy polling & decision mechanism is implemented by AP side               
                    stopScriPoll();
                    startScriPoll();
                }

            } else if (action.equals(Intent.ACTION_SCREEN_OFF)) {
                mIsScreenOn = false;
                stopNetStatPoll();
                startNetStatPoll();
                restartDataStallAlarm();

                //[New R8 modem FD]
                int FD_MD_Enable_Mode = Integer.parseInt(SystemProperties.get(PROPERTY_RIL_FD_MODE, "0"));                
                int FDSimID = SystemProperties.getInt("gsm.3gswitch", 1)-1;                
                if (FeatureOption.MTK_FD_SUPPORT && (FD_MD_Enable_Mode == 1)) {
                    //FD_MD_Enable_Mode == 1: It means that the Fast Dormancy polling & decision mechanism is implemented by modem side
                    if (DBG) log("FD_MD_Enable_Mode=" + FD_MD_Enable_Mode + ", 3gSimID=" + FDSimID + ", when switching to SCREEN OFF");
                    if (mPhone.getMySimId() == FDSimID) {
                        mPhone.mCi.setFDMode(FDModeType.INFO_MD_SCREEN_STATUS.ordinal(), 0, -1, obtainMessage(DctConstants.EVENT_FD_MODE_SET));
                    }
                } else {
                    //FD_MD_Enable_Mode == 0: It means that the Fast Dormancy polling & decision mechanism is implemented by AP side  
                    stopScriPoll();
                    startScriPoll();
                }
            } else if (action.startsWith(INTENT_RECONNECT_ALARM)) {
                if (DBG) log("Reconnect alarm. Previous state was " + mState);
                onActionIntentReconnectAlarm(intent);
            } else if (action.startsWith(INTENT_RESTART_TRYSETUP_ALARM)) {
                //[mr2] added
                if (DBG) log("Restart trySetup alarm");
                onActionIntentRestartTrySetupAlarm(intent);
            } else if (action.equals(INTENT_DATA_STALL_ALARM)) {
                if (FeatureOption.MTK_GEMINI_SUPPORT) {
                    if (mPhone.getMySimId() == intent.getIntExtra(PhoneConstants.GEMINI_SIM_ID_KEY, -1)) {
                        onActionIntentDataStallAlarm(intent);
                    }
                } else {
                    onActionIntentDataStallAlarm(intent);
                }
            } else if (action.equals(INTENT_PROVISIONING_APN_ALARM)) {
                //[KK] added
                onActionIntentProvisioningApnAlarm(intent);                
            } else if (action.equals(WifiManager.NETWORK_STATE_CHANGED_ACTION)) {
                final android.net.NetworkInfo networkInfo = (NetworkInfo)
                        intent.getParcelableExtra(WifiManager.EXTRA_NETWORK_INFO);
                mIsWifiConnected = (networkInfo != null && networkInfo.isConnected());
                if (DBG) log("NETWORK_STATE_CHANGED_ACTION: mIsWifiConnected=" + mIsWifiConnected);
                //MTK-START [mtk04070][111121][ALPS00093395]MTK added
                logd("Recv WIFIMgr NW State Changed Intent:mIsWifiConnected=" + mIsWifiConnected);
                    /*Modify by mtk80372:2010-11-01 for alps00131911*/        
                    if (FeatureOption.MTK_GEMINI_SUPPORT) {
                        int currentDataConnectionSimId = getDataConnectionFromSetting();
                        logd("Recv WIFIMgr NW State Changed Intent: currentDataConnectionSimId is " + currentDataConnectionSimId + " and mPhone.getMySimId() is " + mPhone.getMySimId());
                        if ( mPhone.getMySimId()== currentDataConnectionSimId) {
                            if (mIsWifiConnected && isApnTypeEnabled(PhoneConstants.APN_TYPE_DEFAULT)) {
                                logd("mIsWifiConnected is true and DEFAULT is enabled.");
                                mPhone.disableApnType(PhoneConstants.APN_TYPE_DEFAULT);
                            } else if (!mIsWifiConnected && !isApnTypeEnabled(PhoneConstants.APN_TYPE_DEFAULT)){
                                logd("mIsWifiConnected is false and DEFAULT is NOT enabled.");                            
                                mPhone.enableApnType(PhoneConstants.APN_TYPE_DEFAULT);
                            }
                        }
                    }        
                //MTK-END [mtk04070][111121][ALPS00093395]MTK added
            } else if (action.equals(WifiManager.WIFI_STATE_CHANGED_ACTION)) {
                final boolean enabled = intent.getIntExtra(WifiManager.EXTRA_WIFI_STATE,
                        WifiManager.WIFI_STATE_UNKNOWN) == WifiManager.WIFI_STATE_ENABLED;
                logd("Recv WIFIMgr WIFI State Changed Intent:mIsWifiConnected=" + mIsWifiConnected + ",enabled=" + enabled); 
                if (!enabled) {
                    // when WiFi got disabled, the NETWORK_STATE_CHANGED_ACTION
                    // quit and won't report disconnected until next enabling.
                    mIsWifiConnected = false;
                    //MTK-START [mtk04070][111121][ALPS00093395]MTK added
                    if (FeatureOption.MTK_GEMINI_SUPPORT) {
                        int currentDataConnectionSimId = getDataConnectionFromSetting();
                        logd("Recv WIFIMgr WIFI State Changed Intent: currentDataConnectionSimId is " + currentDataConnectionSimId + " and mPhone.getMySimId() is " + mPhone.getMySimId());
                        if (mPhone.getMySimId()== currentDataConnectionSimId) {
                            if (!isApnTypeEnabled(PhoneConstants.APN_TYPE_DEFAULT)){
                                mPhone.enableApnType(PhoneConstants.APN_TYPE_DEFAULT);
                            }
                        }
                    }
                    //MTK-END [mtk04070][111121][ALPS00093395]MTK added
                }
                if (DBG) log("WIFI_STATE_CHANGED_ACTION: enabled=" + enabled
                        + " mIsWifiConnected=" + mIsWifiConnected);
            } else if (action.equals(INTENT_SET_FAIL_DATA_SETUP_COUNTER)) {
                //[Mr2] TODL: removed
                /*mFailDataSetupCounter = intent.getIntExtra(FAIL_DATA_SETUP_COUNTER, 1);
                mFailDataSetupFailCause = DcFailCause.fromInt(
                        intent.getIntExtra(FAIL_DATA_SETUP_FAIL_CAUSE,
                                                    DcFailCause.ERROR_UNSPECIFIED.getErrorCode()));
                if (DBG) log("set mFailDataSetupCounter=" + mFailDataSetupCounter +
                        " mFailDataSetupFailCause=" + mFailDataSetupFailCause);*/
            } else if (action.equals(Intent.ACTION_BATTERY_CHANGED)) {
                //[New R8 modem FD]
                int status = intent.getIntExtra("status", 0);
                int plugged = intent.getIntExtra("plugged", 0);
                boolean previousChargingMode = mChargingMode;
                
                String sChargingModeStr="",sPluggedStr="";
                if (status == BatteryManager.BATTERY_STATUS_CHARGING)	{
                    mChargingMode = true;
                    sChargingModeStr = "Charging";	
                } else {
                	  mChargingMode = false;
                	  sChargingModeStr = "Non-Charging";
                }
      
                if (plugged == BatteryManager.BATTERY_PLUGGED_AC) {
                    sPluggedStr="Plugged in AC";	
                } else if (plugged == BatteryManager.BATTERY_PLUGGED_USB) {
                	  sPluggedStr="Plugged in USB";
                }
                //log("Update mChargingMode=" + mChargingMode + ", status=" + status + "(" + sPluggedStr + ")");
                
                if ((plugged == BatteryManager.BATTERY_PLUGGED_AC) || (plugged == BatteryManager.BATTERY_PLUGGED_USB)) {
                    mChargingMode = true;
                    //log("Force to set mChargingMode as true when plugged in AC or USB");	
                }
                
                //[New R8 modem FD]
                int FD_MD_Enable_Mode = Integer.parseInt(SystemProperties.get(PROPERTY_RIL_FD_MODE, "0"));
                int FDSimID = SystemProperties.getInt("gsm.3gswitch", 1)-1;                
                
                int previousEnableFDOnCharging = mEnableFDOnCharing;
                mEnableFDOnCharing = Integer.parseInt(SystemProperties.get("fd.on.charge", "0"));
                
                if ((previousChargingMode != mChargingMode) || (previousEnableFDOnCharging != mEnableFDOnCharing)) {
                    if (DBG) log("FD_MD_Enable_Mode=" + FD_MD_Enable_Mode + ", 3gSimID=" + FDSimID + ", when charging state is changed");
                    if (DBG) log("previousEnableFDOnCharging=" + previousEnableFDOnCharging + ", mEnableFDOnCharing=" + mEnableFDOnCharing + ", when charging state is changed");
                    if (DBG) log("previousChargingMode=" + previousChargingMode + ", mChargingMode=" + mChargingMode + ", status=" + status + "(" + sPluggedStr + ")");
                }
                
                if (FeatureOption.MTK_FD_SUPPORT && (FD_MD_Enable_Mode == 1)) {
                    //FD_MD_Enable_Mode == 1: It means that the Fast Dormancy polling & decision mechanism is implemented by modem side
                    if (mPhone.getMySimId() == FDSimID) {
                        if ((previousChargingMode != mChargingMode) || (previousEnableFDOnCharging != mEnableFDOnCharing)) {
                            if (mChargingMode  && (mEnableFDOnCharing == 0)) { 
                                mPhone.mCi.setFDMode(FDModeType.DISABLE_MD_FD.ordinal(), -1, -1, obtainMessage(DctConstants.EVENT_FD_MODE_SET));
                            } else {
                                mPhone.mCi.setFDMode(FDModeType.ENABLE_MD_FD.ordinal(), -1, -1, obtainMessage(DctConstants.EVENT_FD_MODE_SET));                             	
                            }     
                        }
                    }                    
                }	
            } else if (action.equals(ACTION_SET_PS_ATTACH_MODE)) {
                int simId = intent.getIntExtra(PhoneConstants.GEMINI_SIM_ID_KEY, PhoneConstants.GEMINI_SIM_1);
                if (simId == mPhone.getMySimId()) {
                    int attachMode = intent.getIntExtra(KEY_ATTACH_MODE, ATTACH_MODE_NOT_SPECIFY);
                    logd("receive ACTION_SET_PS_ATTACH_MODE [" + simId + ", " + attachMode + "]");
                    SharedPreferences preference = context.getSharedPreferences(PREFERENCE_GPRS, 0);
                    SharedPreferences.Editor editor = preference.edit();
                    editor.putInt(PREF_ATTACH_MODE, attachMode);
                    editor.putInt(PREF_ATTACH_MODE_SIM, simId);
                    editor.commit();
                    SystemProperties.set("persist.radio.gprs.attach.type", attachMode == 1 ? "1" : "0");
                }
            }
        }
    };

    private Runnable mPollNetStat = new Runnable()
    {
        @Override
        public void run() {
            updateDataActivity();

            if (mIsScreenOn) {
                mNetStatPollPeriod = Settings.Global.getInt(mResolver,
                        Settings.Global.PDP_WATCHDOG_POLL_INTERVAL_MS, POLL_NETSTAT_MILLIS);
            } else {
                mNetStatPollPeriod = Settings.Global.getInt(mResolver,
                        Settings.Global.PDP_WATCHDOG_LONG_POLL_INTERVAL_MS,
                        POLL_NETSTAT_SCREEN_OFF_MILLIS);
            }

            if (mNetStatPollEnabled) {
                mDataConnectionTracker.postDelayed(this, mNetStatPollPeriod);
            }
        }
    };

    private class DataRoamingSettingObserver extends ContentObserver {

        public DataRoamingSettingObserver(Handler handler, Context context) {
            super(handler);
            mResolver = context.getContentResolver();
        }

        public void register() {
            if (FeatureOption.MTK_GEMINI_SUPPORT) {
                mResolver.registerContentObserver(
                        Settings.Global.getUriFor(mRoamingSetting[mPhone.getMySimId()]), false, this);
            } else {
                mResolver.registerContentObserver(
                    Settings.Global.getUriFor(Settings.Global.DATA_ROAMING), false, this);
            }
        }

        public void unregister() {            
            mResolver.unregisterContentObserver(this);
        }

        @Override
        public void onChange(boolean selfChange) {
            // already running on mPhone handler thread
            if (mPhone.getServiceState().getRoaming()) {
                sendMessage(obtainMessage(DctConstants.EVENT_ROAMING_ON));
            }

        }
    }
    private final DataRoamingSettingObserver mDataRoamingSettingObserver;

    //[mr2] MTK: used to update Data Activity and CDMA, in class TxRxSum.updateTxRxSum()
    protected ArrayList<String> getInterfaceList() {
            ArrayList<String> interfaceList = new ArrayList<String>();

            for (ApnContext apnContext : mApnContexts.values()) {
                if (apnContext.getState() == DctConstants.State.CONNECTED) {
                    DcAsyncChannel dcac = apnContext.getDcAc();
                    if (dcac == null) continue;

                    LinkProperties linkProp = dcac.getLinkPropertiesSync();
                    if (linkProp == null) continue;

                    String iface = linkProp.getInterfaceName();
                    if (iface != null &&
                            !interfaceList.contains(iface)) {
                        interfaceList.add(iface);
                    }
                }
            }
            return interfaceList;
    }

    //[mr2] added
    /**
     * The Initial MaxRetry sent to a DataConnection as a parameter
     * to DataConnectionAc.bringUp. This value can be defined at compile
     * time using the SystemProperty Settings.Global.DCT_INITIAL_MAX_RETRY
     * and at runtime using gservices to change Settings.Global.DCT_INITIAL_MAX_RETRY.
     */
    private static final int DEFAULT_MDC_INITIAL_RETRY = 1;
    protected int getInitialMaxRetry() {
        if (mFailFast) {
            return 0;
        }
        // Get default value from system property or use DEFAULT_MDC_INITIAL_RETRY
        int value = SystemProperties.getInt(
                Settings.Global.MDC_INITIAL_MAX_RETRY, DEFAULT_MDC_INITIAL_RETRY);

        // Check if its been overridden
        return Settings.Global.getInt(mResolver,
                Settings.Global.MDC_INITIAL_MAX_RETRY, value);
    }


    /**
     * Maintian the sum of transmit and receive packets.
     *
     * The packet counts are initizlied and reset to -1 and
     * remain -1 until they can be updated.
     */
    public class TxRxSum {
        public long txPkts;
        public long rxPkts;

        public TxRxSum() {
            reset();
        }

        public TxRxSum(long txPkts, long rxPkts) {
            this.txPkts = txPkts;
            this.rxPkts = rxPkts;
        }

        public TxRxSum(TxRxSum sum) {
            txPkts = sum.txPkts;
            rxPkts = sum.rxPkts;
        }

        public void reset() {
            txPkts = -1;
            rxPkts = -1;
        }

        @Override
        public String toString() {
            return "{txSum=" + txPkts + " rxSum=" + rxPkts + "}";
        }

        public void updateTxRxSum() {
            // M: Google's method can't distinguish SIM1/SIM2 interface on dual talk project.
            //this.txPkts = TrafficStats.getMobileTcpTxPackets();
            //this.rxPkts = TrafficStats.getMobileTcpRxPackets();

            boolean txUpdated = false, rxUpdated = false;
            long txSum = 0, rxSum = 0;
            ArrayList<String> interfaceList = getInterfaceList();

            for (int i=0; i<interfaceList.size(); i++) {
                long stats = TrafficStats.getTxPackets(interfaceList.get(i));
                if (stats > 0) {
                    txUpdated = true;
                    txSum += stats;
                }
                stats = TrafficStats.getRxPackets(interfaceList.get(i));
                if (stats > 0) {
                    rxUpdated = true;
                    rxSum += stats;
                }
            }
            if (txUpdated) this.txPkts = txSum;
            if (rxUpdated) this.rxPkts = rxSum;            
        }
    }

    //[mr2] TODO: check removed if, need to check sync to DcTracker
    protected boolean isDataSetupCompleteOk(AsyncResult ar) {
        if (ar.exception != null) {
            if (DBG) log("isDataSetupCompleteOk return false, ar.result=" + ar.result);
            return false;
        }
        if (mFailDataSetupCounter <= 0) {
            if (DBG) log("isDataSetupCompleteOk return true");
            return true;
        }
        ar.result = mFailDataSetupFailCause;
        if (DBG) {
            log("isDataSetupCompleteOk return false" +
                    " mFailDataSetupCounter=" + mFailDataSetupCounter +
                    " mFailDataSetupFailCause=" + mFailDataSetupFailCause);
        }
        mFailDataSetupCounter -= 1;
        return false;
    }

    //[Mr2] TODO: checked, temporary use
    protected void onActionIntentReconnectAlarm(Intent intent) {

        //MTK
        if (FeatureOption.MTK_GEMINI_SUPPORT) {
           int reconnect_for_simId = intent.getIntExtra(PhoneConstants.GEMINI_SIM_ID_KEY, PhoneConstants.GEMINI_SIM_1);
           logd("GPRS reconnect alarm triggered by simId=" + reconnect_for_simId + ". Previous state was " + getState());  
           if (reconnect_for_simId != mPhone.getMySimId()) {
              return;
           }
        } else {
           logd("GPRS reconnect alarm. Previous state was " + getState());
        }

        //[Mr2] start
        String reason = intent.getStringExtra(INTENT_RECONNECT_ALARM_EXTRA_REASON);
        String apnType = intent.getStringExtra(INTENT_RECONNECT_ALARM_EXTRA_TYPE);

        ApnContext apnContext = mApnContexts.get(apnType);

        if (DBG) {
            log("onActionIntentReconnectAlarm: mState=" + mState + " reason=" + reason +
                    " apnType=" + apnType + " apnContext=" + apnContext +
                    " mDataConnectionAsyncChannels=" + mDataConnectionAcHashMap);
        }

        if ((apnContext != null) && (apnContext.isEnabled())) {
            apnContext.setReason(reason);
            DctConstants.State apnContextState = apnContext.getState();
            if (DBG) {
                log("onActionIntentReconnectAlarm: apnContext state=" + apnContextState);
            }
            if ((apnContextState == DctConstants.State.FAILED)
                    || (apnContextState == DctConstants.State.IDLE)) {
                if (DBG) {
                    log("onActionIntentReconnectAlarm: state is FAILED|IDLE, disassociate");
                }
                DcAsyncChannel dcac = apnContext.getDcAc();
                if (dcac != null) {
                    dcac.tearDown(apnContext, "", null);
                }
                apnContext.setDataConnectionAc(null);
                apnContext.setState(DctConstants.State.IDLE);
            } else {
                if (DBG) log("onActionIntentReconnectAlarm: keep associated");
            }
            // TODO: IF already associated should we send the EVENT_TRY_SETUP_DATA???
            sendMessage(obtainMessage(DctConstants.EVENT_TRY_SETUP_DATA, apnContext));
            
            apnContext.setReconnectIntent(null);
        }
        //[Mr2] end

        //[Mr1+MTK] start
        /*
        String reason = intent.getStringExtra(INTENT_RECONNECT_ALARM_EXTRA_REASON);
        int reconnect_apnTypeId = intent.getIntExtra(INTENT_RECONNECT_ALARM_APN_TYPE, DctConstants.APN_DEFAULT_ID);
        logd("Handle reconnect alarm with reason=" + reason + ",and reconnect to apnType=" + apnIdToType(reconnect_apnTypeId));

        // Modified by vendor: Carry APN Type to retry setup data call 
        Message msg = obtainMessage(DctConstants.EVENT_TRY_SETUP_DATA);
        msg.obj = reason;
        // [Note by mtk01411] For Multiple PDP Context feature: It should reconnect to the apnType carried in this intent 
        msg.arg2 =  intent.getIntExtra(INTENT_RECONNECT_ALARM_APN_TYPE, DctConstants.APN_DEFAULT_ID);
        sendMessage(msg);        
        */
        //[Mr1+MTK] end
        
    }
    
    protected void onActionIntentRestartTrySetupAlarm(Intent intent) {


        //MTK
        if (FeatureOption.MTK_GEMINI_SUPPORT) {
           int restart_for_simId = intent.getIntExtra(PhoneConstants.GEMINI_SIM_ID_KEY, PhoneConstants.GEMINI_SIM_1);
           logd("GPRS reconnect alarm triggered by simId=" + restart_for_simId + ". Previous state was " + getState());  
           if (restart_for_simId != mPhone.getMySimId()) {
              return;
           }
        } else {
           logd("GPRS reconnect alarm. Previous state was " + getState());
        }
        
        String apnType = intent.getStringExtra(INTENT_RESTART_TRYSETUP_ALARM_EXTRA_TYPE);
        ApnContext apnContext = mApnContexts.get(apnType);
        if (DBG) {
            log("onActionIntentRestartTrySetupAlarm: mState=" + mState +
                    " apnType=" + apnType + " apnContext=" + apnContext +
                    " mDataConnectionAsyncChannels=" + mDataConnectionAcHashMap);
        }
        sendMessage(obtainMessage(DctConstants.EVENT_TRY_SETUP_DATA, apnContext));
    }

    protected void onActionIntentDataStallAlarm(Intent intent) {
        if (VDBG_STALL) log("onActionIntentDataStallAlarm: action=" + intent.getAction());
        Message msg = obtainMessage(DctConstants.EVENT_DATA_STALL_ALARM, intent.getAction());
        msg.arg1 = intent.getIntExtra(DATA_STALL_ALARM_TAG_EXTRA, 0);
        sendMessage(msg);
    }

    //[KK]  added
    ConnectivityManager mCm;

    /**
     * Default constructor
     */
    protected DcTrackerBase(PhoneBase phone) {
        super();
        if (DBG) log("DCT.constructor");
        mPhone = phone;
        mResolver = mPhone.getContext().getContentResolver(); 
        mUiccController = UiccController.getInstance(mPhone.getMySimId());
        mUiccController.registerForIccChanged(this, DctConstants.EVENT_ICC_CHANGED, null);
        mAlarmManager =
                (AlarmManager) mPhone.getContext().getSystemService(Context.ALARM_SERVICE);
        //[KK] added
        mCm = (ConnectivityManager) mPhone.getContext().getSystemService(
                Context.CONNECTIVITY_SERVICE);


        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        filter.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION);
        filter.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION);
        filter.addAction(INTENT_DATA_STALL_ALARM);
        filter.addAction(INTENT_SET_FAIL_DATA_SETUP_COUNTER);//[mr2] TODO: removed
        filter.addAction(INTENT_PROVISIONING_APN_ALARM);

        //[New R8 modem FD] 
        filter.addAction(Intent.ACTION_BATTERY_CHANGED);

        mUserDataEnabled = Settings.Global.getInt(
                mPhone.getContext().getContentResolver(), Settings.Global.MOBILE_DATA, 1) == 1;

        // TODO: Why is this registering the phone as the receiver of the intent
        //       and not its own handler?
        mPhone.getContext().registerReceiver(mIntentReceiver, filter, null, mPhone);

        // This preference tells us 1) initial condition for "dataEnabled",
        // and 2) whether the RIL will setup the baseband to auto-PS attach.

        //dataEnabled[DctConstants.APN_DEFAULT_ID] = SystemProperties.getBoolean(DEFALUT_DATA_ON_BOOT_PROP,
        //                                                          true);
        boolean dataOnBoot = SystemProperties.getBoolean(DEFALUT_DATA_ON_BOOT_PROP, true);
        mDataEnabled[DctConstants.APN_DEFAULT_ID] = (dataOnBoot && mUserDataEnabled);

        if (mDataEnabled[DctConstants.APN_DEFAULT_ID]) {
            mEnabledCount++;
        }

        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(mPhone.getContext());
        mAutoAttachOnCreation = sp.getBoolean(PhoneBase.DATA_DISABLED_ON_BOOT_KEY, false);
        if (FeatureOption.MTK_GEMINI_SUPPORT) {
            // Combination activation in Gemini
            mAutoAttachOnCreation = true;
        }

        // M: Sync SIM info roaming setting into settings
        if (DBG) log("DataConnectionTracker(): sync roaming setting");
        syncRoamingSetting();

        // watch for changes to Settings.Global.DATA_ROAMING
        mDataRoamingSettingObserver = new DataRoamingSettingObserver(mPhone, mPhone.getContext());
        mDataRoamingSettingObserver.register();

        //[mr2] added start
        HandlerThread dcHandlerThread = new HandlerThread("DcHandlerThread");
        dcHandlerThread.start();
        Handler dcHandler = new Handler(dcHandlerThread.getLooper());
        mDcc = DcController.makeDcc(mPhone, this, dcHandler);
        mDcTesterFailBringUpAll = new DcTesterFailBringUpAll(mPhone, dcHandler);
        //[mr2] added end
    }

    public void dispose() {
        if (DBG) log("DCT.dispose");
        for (DcAsyncChannel dcac : mDataConnectionAcHashMap.values()) {
            dcac.disconnect();
        }
        mDataConnectionAcHashMap.clear();
        mIsDisposed = true;
        mPhone.getContext().unregisterReceiver(this.mIntentReceiver);
        mDataRoamingSettingObserver.unregister();
        mUiccController.unregisterForIccChanged(this);
        //[mr2] added
        mDcc.dispose();
        mDcTesterFailBringUpAll.dispose();
    }

    //[mr2] TODO: checked! mr2 reserve this part, but removed DcTracker broadcast part, so we reserve it temporary
    protected void broadcastMessenger() {
        Intent intent = new Intent(DctConstants.ACTION_DATA_CONNECTION_TRACKER_MESSENGER);
        intent.putExtra(DctConstants.EXTRA_MESSENGER, new Messenger(this));
        mPhone.getContext().sendBroadcast(intent);
    }

    public DctConstants.Activity getActivity() {
        return mActivity;
    }

    public boolean isApnTypeActive(String type) {
        // TODO: support simultaneous with List instead
        if (PhoneConstants.APN_TYPE_DUN.equals(type)) {
            ApnSetting dunApn = fetchDunApn();
            if (dunApn != null) {
                return ((mActiveApn != null) && (dunApn.toString().equals(mActiveApn.toString())));
            }
        }
        return mActiveApn != null && mActiveApn.canHandleType(type);
    }

    protected ApnSetting fetchDunApn() {
        if (SystemProperties.getBoolean("net.tethering.noprovisioning", false)) {
            log("fetchDunApn: net.tethering.noprovisioning=true ret: null");
            return null;
        }
        Context c = mPhone.getContext();
        String apnData = Settings.Global.getString(c.getContentResolver(),
                Settings.Global.TETHER_DUN_APN);
        ApnSetting dunSetting = ApnSetting.fromString(apnData);
        if (dunSetting != null) {
            IccRecords r = mIccRecords.get();
            String operator = (r != null) ? r.getOperatorNumeric() : "";
            if (dunSetting.numeric.equals(operator)) {
                if (VDBG) log("fetchDunApn: global TETHER_DUN_APN dunSetting=" + dunSetting);
                return dunSetting;
            }
        }

        apnData = c.getResources().getString(R.string.config_tether_apndata);
        dunSetting = ApnSetting.fromString(apnData);
        if (VDBG) log("fetchDunApn: config_tether_apndata dunSetting=" + dunSetting);
        return dunSetting;
    }

    public String[] getActiveApnTypes() {
        String[] result;
        if (mActiveApn != null) {
            result = mActiveApn.types;
        } else {
            result = new String[1];
            result[0] = PhoneConstants.APN_TYPE_DEFAULT;
        }
        return result;
    }

    /** TODO: See if we can remove */
    public String getActiveApnString(String apnType) {
        String result = null;
        if (mActiveApn != null) {
            result = mActiveApn.apn;
        }
        return result;
    }

    /**
     * Modify {@link android.provider.Settings.Global#DATA_ROAMING} value.
     */
    public void setDataOnRoamingEnabled(boolean enabled) {
        int simId = mPhone.getMySimId();
        if (getDataOnRoamingEnabled() != enabled) {
            if(FeatureOption.MTK_GEMINI_ENHANCEMENT) {
                SIMInfo simInfo = SIMInfo.getSIMInfoBySlot(mPhone.getContext(), simId);
                if(simInfo != null){
                    if(SIMInfo.setDataRoaming(mPhone.getContext(), enabled ? SimInfo.DATA_ROAMING_ENABLE : SimInfo.DATA_ROAMING_DISABLE, simInfo.mSimId) <= 0){
                        loge("Can't set data romaing in database");
                    } else {   
                        ///M: @{Update to SIMHelper (evdo op09) [start], ToDO: after SIMInfo Refactory, remove this broadcast
                        log("DataRoamingAllowed, send broadcast:  ACTION_SIM_INFO_UPDATE");
                        Intent intent = new Intent(TelephonyIntents.ACTION_SIM_INFO_UPDATE);
                        mPhone.getContext().sendBroadcast(intent);
                    }
                    ///M: }@
                }
            }
            final ContentResolver resolver = mPhone.getContext().getContentResolver();
            if (FeatureOption.MTK_GEMINI_SUPPORT) {
                Settings.Global.putInt(resolver, mRoamingSetting[simId], enabled ? 1 : 0);
            } else {
                Settings.Global.putInt(resolver, Settings.Global.DATA_ROAMING, enabled ? 1 : 0);
            }
            // will trigger handleDataOnRoamingChange() through observer
        }
    }

    /**
     * Return current {@link Settings.Global#DATA_ROAMING} value.
     */
    public boolean getDataOnRoamingEnabled() {
        int simId = mPhone.getMySimId();
        try {
            final ContentResolver resolver = mPhone.getContext().getContentResolver();
            if (FeatureOption.MTK_GEMINI_SUPPORT) {
                return Settings.Global.getInt(resolver, mRoamingSetting[simId]) > 0;
            } else {
                return Settings.Global.getInt(resolver,
                        Settings.Global.DATA_ROAMING) > 0;
            }
        } catch (SettingNotFoundException snfe) {
            return false;
        }
    }

    // abstract methods
    protected abstract void restartRadio();
    protected abstract void log(String s);
    protected abstract void loge(String s);
    protected abstract boolean isDataAllowed();
    protected abstract boolean isApnTypeAvailable(String type);
    public    abstract DctConstants.State getState(String apnType);
    protected abstract boolean isProvisioningApn(String apnType);//[KK] added
    protected abstract void setState(DctConstants.State s);
    protected abstract void gotoIdleAndNotifyDataConnection(String reason);

    protected abstract boolean onTrySetupData(String reason);
    protected abstract void onRoamingOff();
    protected abstract void onRoamingOn();
    protected abstract void onRadioAvailable();
    protected abstract void onRadioOffOrNotAvailable();
    protected abstract void onDataSetupComplete(AsyncResult ar);
    protected abstract void onDataSetupCompleteError(AsyncResult ar);
    protected abstract void onDisconnectDone(int connId, AsyncResult ar);
    protected abstract void onDisconnectDcRetrying(int connId, AsyncResult ar);
    protected abstract void onVoiceCallStarted();
    protected abstract void onVoiceCallEnded();
    protected abstract void onCleanUpConnection(boolean tearDown, int apnId, String reason);
    protected abstract void onCleanUpAllConnections(String cause);
    public abstract boolean isDataPossible(String apnType);
    protected abstract void onUpdateIcc();
    protected abstract void completeConnection(ApnContext apnContext); //[KK] added
    public abstract void deactivatePdpByCid(int cid); // MTK

    //[New R8 modem FD]
    protected void startScriPoll() {
        // It should not declare this API as abstract because CDMA does not have FD feature
    }
    //[New R8 modem FD]    
    protected void stopScriPoll() {
        // It should not declare this API as abstract because CDMA does not have FD feature
    }

    @Override
    public void handleMessage(Message msg) {
        //[New R8 modem FD]    
        AsyncResult ar;    
        switch (msg.what) {
            case AsyncChannel.CMD_CHANNEL_DISCONNECTED: {
                log("DISCONNECTED_CONNECTED: msg=" + msg);
                DcAsyncChannel dcac = (DcAsyncChannel) msg.obj;
                mDataConnectionAcHashMap.remove(dcac.getDataConnectionIdSync());
                dcac.disconnected();
                break;
            }
            case DctConstants.EVENT_ENABLE_NEW_APN:
                onEnableApn(msg.arg1, msg.arg2);
                break;

            case DctConstants.EVENT_TRY_SETUP_DATA:
                String reason = null;
                if (msg.obj instanceof String) {
                    reason = (String) msg.obj;
                }
                onTrySetupData(reason);
                break;

            case DctConstants.EVENT_DATA_STALL_ALARM:
                onDataStallAlarm(msg.arg1);
                break;

            case DctConstants.EVENT_ROAMING_OFF:
                onRoamingOff();
                break;

            case DctConstants.EVENT_ROAMING_ON:
                onRoamingOn();
                break;

            case DctConstants.EVENT_RADIO_AVAILABLE:
                onRadioAvailable();
                break;

            case DctConstants.EVENT_RADIO_OFF_OR_NOT_AVAILABLE:
                onRadioOffOrNotAvailable();
                break;

            case DctConstants.EVENT_DATA_SETUP_COMPLETE:
                mCidActive = msg.arg1;
                onDataSetupComplete((AsyncResult) msg.obj);
                break;

            case DctConstants.EVENT_DATA_SETUP_COMPLETE_ERROR:
                onDataSetupCompleteError((AsyncResult) msg.obj);
                break;

            case DctConstants.EVENT_DISCONNECT_DONE:
                log("DataConnectoinTracker.handleMessage: EVENT_DISCONNECT_DONE msg=" + msg);
                onDisconnectDone(msg.arg1, (AsyncResult) msg.obj);
                break;

            case DctConstants.EVENT_DISCONNECT_DC_RETRYING:
                log("DataConnectionTracker.handleMessage: EVENT_DISCONNECT_DC_RETRYING msg=" + msg);
                onDisconnectDcRetrying(msg.arg1, (AsyncResult) msg.obj);
                break;

            case DctConstants.EVENT_VOICE_CALL_STARTED:
                onVoiceCallStarted();
                break;

            case DctConstants.EVENT_VOICE_CALL_ENDED:
                onVoiceCallEnded();
                break;

            case DctConstants.EVENT_CLEAN_UP_ALL_CONNECTIONS: {
                onCleanUpAllConnections((String) msg.obj);
                break;
            }
            case DctConstants.EVENT_CLEAN_UP_CONNECTION: {
                boolean tearDown = (msg.arg1 == 0) ? false : true;
                onCleanUpConnection(tearDown, msg.arg2, (String) msg.obj);
                break;
            }
            case DctConstants.EVENT_SET_INTERNAL_DATA_ENABLE: {
                boolean enabled = (msg.arg1 == DctConstants.ENABLED) ? true : false;
                onSetInternalDataEnabled(enabled);
                break;
            }
            case DctConstants.EVENT_RESET_DONE: {
                if (DBG) log("EVENT_RESET_DONE");
                onResetDone((AsyncResult) msg.obj);
                break;
            }
            case DctConstants.CMD_SET_USER_DATA_ENABLE: {
                final boolean enabled = (msg.arg1 == DctConstants.ENABLED) ? true : false;
                if (DBG) log("CMD_SET_USER_DATA_ENABLE enabled=" + enabled);
                onSetUserDataEnabled(enabled);
                break;
            }
            case DctConstants.CMD_SET_DEPENDENCY_MET: {
                boolean met = (msg.arg1 == DctConstants.ENABLED) ? true : false;
                if (DBG) log("CMD_SET_DEPENDENCY_MET met=" + met);
                Bundle bundle = msg.getData();
                if (bundle != null) {
                    String apnType = (String)bundle.get(DctConstants.APN_TYPE_KEY);
                    if (apnType != null) {
                        onSetDependencyMet(apnType, met);
                    }
                }
                break;
            }
            case DctConstants.CMD_SET_POLICY_DATA_ENABLE: {
                final boolean enabled = (msg.arg1 == DctConstants.ENABLED) ? true : false;
                onSetPolicyDataEnabled(enabled);
                break;
            }
            //[mr2] added
            case DctConstants.CMD_SET_ENABLE_FAIL_FAST_MOBILE_DATA: {
                //[KK] added start
                sEnableFailFastRefCounter += (msg.arg1 == DctConstants.ENABLED) ? 1 : -1;
                if (DBG) {
                    log("CMD_SET_ENABLE_FAIL_FAST_MOBILE_DATA: "
                            + " sEnableFailFastRefCounter=" + sEnableFailFastRefCounter);
                }
                if (sEnableFailFastRefCounter < 0) {
                    final String s = "CMD_SET_ENABLE_FAIL_FAST_MOBILE_DATA: "
                            + "sEnableFailFastRefCounter:" + sEnableFailFastRefCounter + " < 0";
                    loge(s);
                    sEnableFailFastRefCounter = 0;
                }
                final boolean enabled = sEnableFailFastRefCounter > 0;
                if (DBG) {
                    log("CMD_SET_ENABLE_FAIL_FAST_MOBILE_DATA: enabled=" + enabled
                            + " sEnableFailFastRefCounter=" + sEnableFailFastRefCounter);
                }
                //[KK] added end
                if (mFailFast != enabled) {
                    mFailFast = enabled;
                    mDataStallDetectionEnabled = !enabled;
                    if (mDataStallDetectionEnabled
                            && (getOverallState() == DctConstants.State.CONNECTED)
                            && (!mInVoiceCall ||
                                    mPhone.getServiceStateTracker()
                                        .isConcurrentVoiceAndDataAllowed())) {
                        if (DBG) log("CMD_SET_ENABLE_FAIL_FAST_MOBILE_DATA: start data stall");
                        stopDataStallAlarm();
                        startDataStallAlarm(DATA_STALL_NOT_SUSPECTED);
                    } else {
                        if (DBG) log("CMD_SET_ENABLE_FAIL_FAST_MOBILE_DATA: stop data stall");
                        stopDataStallAlarm();
                    }
                }

                break;
            }
            //[KK] added start
            case DctConstants.CMD_ENABLE_MOBILE_PROVISIONING: {
                // TODO: Right now we know when it ends "successfully" when
                // provisioning apn gets dropped, what happens if the user never
                // succeed, I assume there is a timeout and the network will drop
                // it after a period of time.
                Bundle bundle = msg.getData();
                if (bundle != null) {
                    try {
                        mProvisioningUrl = (String)bundle.get(DctConstants.PROVISIONING_URL_KEY);
                    } catch(ClassCastException e) {
                        loge("CMD_ENABLE_MOBILE_PROVISIONING: provisioning url not a string" + e);
                        mProvisioningUrl = null;
                    }
                }
                if (TextUtils.isEmpty(mProvisioningUrl)) {
                    loge("CMD_ENABLE_MOBILE_PROVISIONING: provisioning url is empty, ignoring");
                    mIsProvisioning = false;
                    mProvisioningUrl = null;
                } else {
                    ApnContext apnContext = mApnContexts.get(PhoneConstants.APN_TYPE_DEFAULT);
                    if (apnContext.isProvisioningApn() && apnContext.getState() == State.CONNECTED){
                        log("CMD_ENABLE_MOBILE_PROVISIONING: mIsProvisioning=true url="
                                + mProvisioningUrl);
                        mIsProvisioning = true;
                        startProvisioningApnAlarm();
                        completeConnection(mApnContexts.get(PhoneConstants.APN_TYPE_DEFAULT));
                    } else {
                        log("CMD_ENABLE_MOBILE_PROVISIONING: No longer connected");
                        mIsProvisioning = false;
                        mProvisioningUrl = null;
                    }
                }
                break;
            }
            case DctConstants.EVENT_PROVISIONING_APN_ALARM: {
                if (DBG) log("EVENT_PROVISIONING_APN_ALARM");
                ApnContext apnCtx = mApnContexts.get("default");
                if (apnCtx.isProvisioningApn() && apnCtx.isConnectedOrConnecting()) {
                    if (mProvisioningApnAlarmTag == msg.arg1) {
                        if (DBG) log("EVENT_PROVISIONING_APN_ALARM: Disconnecting");
                        mIsProvisioning = false;
                        mProvisioningUrl = null;
                        stopProvisioningApnAlarm();
                        sendCleanUpConnection(true, apnCtx);
                    } else {
                        if (DBG) {
                            log("EVENT_PROVISIONING_APN_ALARM: ignore stale tag,"
                                    + " mProvisioningApnAlarmTag:" + mProvisioningApnAlarmTag
                                    + " != arg1:" + msg.arg1);
                        }
                    }
                } else {
                    if (DBG) log("EVENT_PROVISIONING_APN_ALARM: Not connected ignore");
                }
                break;
            }
            case DctConstants.CMD_IS_PROVISIONING_APN: {
                if (DBG) log("CMD_IS_PROVISIONING_APN");
                boolean isProvApn;
                try {
                    String apnType = null;
                    Bundle bundle = msg.getData();
                    if (bundle != null) {
                        apnType = (String)bundle.get(DctConstants.APN_TYPE_KEY);
                    }
                    if (TextUtils.isEmpty(apnType)) {
                        loge("CMD_IS_PROVISIONING_APN: apnType is empty");
                        isProvApn = false;
                    } else {
                        isProvApn = isProvisioningApn(apnType);
                    }
                } catch (ClassCastException e) {
                    loge("CMD_IS_PROVISIONING_APN: NO provisioning url ignoring");
                    isProvApn = false;
                }
                if (DBG) log("CMD_IS_PROVISIONING_APN: ret=" + isProvApn);
                mReplyAc.replyToMessage(msg, DctConstants.CMD_IS_PROVISIONING_APN,
                        isProvApn ? DctConstants.ENABLED : DctConstants.DISABLED);
                break;
            }            
            case DctConstants.EVENT_RESTART_RADIO: {
                restartRadio();
                break;
            }
            //[KK] added end
            case DctConstants.EVENT_ICC_CHANGED:
                onUpdateIcc();
                break;

            //[New R8 modem FD]
            case DctConstants.EVENT_FD_MODE_SET:
                ar = (AsyncResult) msg.obj;				
                if (DBG) log("RECV RSP for EVENT_FD_MODE_SET");
                if (ar.exception == null) {
                    if (DBG) log("SET_FD_MODE OK");
                } else {
                    if (DBG) log("SET_FD_MODE ERROR");                
                }
                break;

            case DctConstants.EVENT_RECOVERY_DETACH_PS:
                if (DBG) log("Recovery, detach done, then setGprsConn:always");
                mPhone.mCi.setGprsConnType(1, obtainMessage(DctConstants.EVENT_SET_GPRS_CONN_TYPE_DONE, null));

                break;

                
            default:
                Rlog.e("DATA", "Unidentified event msg=" + msg);
                break;
        }
    }

    /**
     * Report on whether data connectivity is enabled
     *
     * @return {@code false} if data connectivity has been explicitly disabled,
     *         {@code true} otherwise.
     */
    public boolean getAnyDataEnabled() {
        final boolean result;
        synchronized (mDataEnabledLock) {
            mUserDataEnabled = Settings.Global.getInt(
                mPhone.getContext().getContentResolver(), Settings.Global.MOBILE_DATA, 1) == 1;			
            result = (mInternalDataEnabled && sPolicyDataEnabled
                    && (mUserDataEnabled
                        || (mRequestedApnType.equals(PhoneConstants.APN_TYPE_MMS) && mDataEnabled[DctConstants.APN_MMS_ID]))
                    && (mEnabledCount != 0));
        }
        if (!result && DBG) log("getAnyDataEnabled " + result);
        return result;
    }

    protected boolean isEmergency() {
        final boolean result;
        synchronized (mDataEnabledLock) {
            result = mPhone.isInEcm() || mPhone.isInEmergencyCall();
        }
        log("isEmergency: result=" + result);
        return result;
    }

    protected int apnTypeToId(String type) {
        if (TextUtils.equals(type, PhoneConstants.APN_TYPE_DEFAULT)) {
            return DctConstants.APN_DEFAULT_ID;
        } else if (TextUtils.equals(type, PhoneConstants.APN_TYPE_MMS)) {
            return DctConstants.APN_MMS_ID;
        } else if (TextUtils.equals(type, PhoneConstants.APN_TYPE_SUPL)) {
            return DctConstants.APN_SUPL_ID;
        } else if (TextUtils.equals(type, PhoneConstants.APN_TYPE_DUN)) {
            return DctConstants.APN_DUN_ID;
        } else if (TextUtils.equals(type, PhoneConstants.APN_TYPE_HIPRI)) {
            return DctConstants.APN_HIPRI_ID;
        } else if (TextUtils.equals(type, PhoneConstants.APN_TYPE_IMS)) {
            return DctConstants.APN_IMS_ID;
        } else if (TextUtils.equals(type, PhoneConstants.APN_TYPE_FOTA)) {
            return DctConstants.APN_FOTA_ID;
        } else if (TextUtils.equals(type, PhoneConstants.APN_TYPE_CBS)) {
            return DctConstants.APN_CBS_ID;
        } else if (TextUtils.equals(type, PhoneConstants.APN_TYPE_IA)) {
            return DctConstants.APN_IA_ID; //[KK] added
        } else if (TextUtils.equals(type, PhoneConstants.APN_TYPE_DM)) {
            return DctConstants.APN_DM_ID;
        } else if (TextUtils.equals(type, PhoneConstants.APN_TYPE_NET)) {
            return DctConstants.APN_NET_ID;
        } else if (TextUtils.equals(type, PhoneConstants.APN_TYPE_WAP)) {
            return DctConstants.APN_WAP_ID;
        } else if (TextUtils.equals(type, PhoneConstants.APN_TYPE_CMMAIL)) {
            return DctConstants.APN_CMMAIL_ID;
        } else if (TextUtils.equals(type, PhoneConstants.APN_TYPE_RCSE)) {
            return DctConstants.APN_RCSE_ID;
        } else {
            return DctConstants.APN_INVALID_ID;
        }
    }

    protected String apnIdToType(int id) {
        switch (id) {
        case DctConstants.APN_DEFAULT_ID:
            return PhoneConstants.APN_TYPE_DEFAULT;
        case DctConstants.APN_MMS_ID:
            return PhoneConstants.APN_TYPE_MMS;
        case DctConstants.APN_SUPL_ID:
            return PhoneConstants.APN_TYPE_SUPL;
        case DctConstants.APN_DUN_ID:
            return PhoneConstants.APN_TYPE_DUN;
        case DctConstants.APN_HIPRI_ID:
            return PhoneConstants.APN_TYPE_HIPRI;
        case DctConstants.APN_IMS_ID:
            return PhoneConstants.APN_TYPE_IMS;
        case DctConstants.APN_FOTA_ID:
            return PhoneConstants.APN_TYPE_FOTA;
        case DctConstants.APN_CBS_ID:
            return PhoneConstants.APN_TYPE_CBS;
        case DctConstants.APN_IA_ID:
            return PhoneConstants.APN_TYPE_IA; //[KK] added
        case DctConstants.APN_DM_ID:
            return PhoneConstants.APN_TYPE_DM;
        case DctConstants.APN_NET_ID:
            return PhoneConstants.APN_TYPE_NET;
        case DctConstants.APN_WAP_ID:
            return PhoneConstants.APN_TYPE_WAP;
        case DctConstants.APN_CMMAIL_ID:
            return PhoneConstants.APN_TYPE_CMMAIL;
        case DctConstants.APN_RCSE_ID:
            return PhoneConstants.APN_TYPE_RCSE;
        default:
            log("Unknown id (" + id + ") in apnIdToType");
            return PhoneConstants.APN_TYPE_DEFAULT;
        }
    }

    //MTK-START [mtk04070][111125][ALPS00093395]For solving build error
    /* Add by vendor for Multiple PDP Context */
    public String getActiveApnType() {return null;}
    //MTK-END [mtk04070][111125][ALPS00093395]For solving build error

    public LinkProperties getLinkProperties(String apnType) {
        int id = apnTypeToId(apnType);

        if (isApnIdEnabled(id)) {
            DcAsyncChannel dcac = mDataConnectionAcHashMap.get(0);
            return dcac.getLinkPropertiesSync();
        } else {
            return new LinkProperties();
        }
    }

    public LinkCapabilities getLinkCapabilities(String apnType) {
        int id = apnTypeToId(apnType);
        if (isApnIdEnabled(id)) {
            DcAsyncChannel dcac = mDataConnectionAcHashMap.get(0);
            return dcac.getLinkCapabilitiesSync();
        } else {
            return new LinkCapabilities();
        }
    }

    // tell all active apns of the current condition
    protected void notifyDataConnection(String reason) {
        for (int id = 0; id < DctConstants.APN_NUM_TYPES; id++) {
            // Only active apns' state changed
            if (mDataEnabled[id] && mActiveApn != null
                        && mActiveApn.canHandleType(apnIdToType(id))) {
                mPhone.notifyDataConnection(reason, apnIdToType(id));
            }
        }
        notifyOffApnsOfAvailability(reason);
    }

    // a new APN has gone active and needs to send events to catch up with the
    // current condition
    private void notifyApnIdUpToCurrent(String reason, int apnId) {
        switch (mState) {
            case IDLE:
                break;
            case RETRYING:
            case CONNECTING:
            case SCANNING:
                mPhone.notifyDataConnection(reason, apnIdToType(apnId), PhoneConstants.DataState.CONNECTING);
                break;
            case CONNECTED:
            case DISCONNECTING:
                mPhone.notifyDataConnection(reason, apnIdToType(apnId), PhoneConstants.DataState.CONNECTING);
                mPhone.notifyDataConnection(reason, apnIdToType(apnId), PhoneConstants.DataState.CONNECTED);
                break;
            default:
                // Ignore
                break;
        }
    }

    // since we normally don't send info to a disconnected APN, we need to do this specially
    private void notifyApnIdDisconnected(String reason, int apnId) {
        mPhone.notifyDataConnection(reason, apnIdToType(apnId), PhoneConstants.DataState.DISCONNECTED);
    }

    // disabled apn's still need avail/unavail notificiations - send them out
    protected void notifyOffApnsOfAvailability(String reason) {
        if (DBG) log("notifyOffApnsOfAvailability - reason= " + reason);
        for (int id = 0; id < DctConstants.APN_NUM_TYPES; id++) {
            if (id == apnTypeToId(apnIdToType(id)) && !isApnIdEnabled(id)) {
                notifyApnIdDisconnected(reason, id);
            }
        }
    }

    public boolean isApnTypeEnabled(String apnType) {
        if (apnType == null) {
            return false;
        } else {
            return isApnIdEnabled(apnTypeToId(apnType));
        }
    }

    protected synchronized boolean isApnIdEnabled(int id) {
        if (id != DctConstants.APN_INVALID_ID) {
            return mDataEnabled[id];
        }
        return false;
    }

    /**
     * Ensure that we are connected to an APN of the specified type.
     *
     * @param type the APN type (currently the only valid values are
     *            {@link PhoneConstants#APN_TYPE_MMS} and {@link PhoneConstants#APN_TYPE_SUPL})
     * @return Success is indicated by {@code Phone.APN_ALREADY_ACTIVE} or
     *         {@code Phone.APN_REQUEST_STARTED}. In the latter case, a
     *         broadcast will be sent by the ConnectivityManager when a
     *         connection to the APN has been established.
     */
    public synchronized int enableApnType(String type) {
        int id = apnTypeToId(type);
        if (id == DctConstants.APN_INVALID_ID) {
            return PhoneConstants.APN_REQUEST_FAILED;
        }

        if (DBG) {
            log("enableApnType(" + type + "), isApnTypeActive = " + isApnTypeActive(type)
                    + ", isApnIdEnabled =" + isApnIdEnabled(id) + " and state = " + mState);
        }

        if (!isApnTypeAvailable(type)) {
            if (DBG) log("enableApnType: not available, type=" + type);
            return PhoneConstants.APN_TYPE_NOT_AVAILABLE;
        }

        if (isApnIdEnabled(id)) {
            if (DBG) log("enableApnType: already active, type=" + type);            
            return PhoneConstants.APN_ALREADY_ACTIVE;
        } else {
            setEnabled(id, true);
        }
        return PhoneConstants.APN_REQUEST_STARTED;
    }

    /**
     * The APN of the specified type is no longer needed. Ensure that if use of
     * the default APN has not been explicitly disabled, we are connected to the
     * default APN.
     *
     * @param type the APN type. The only valid values are currently
     *            {@link PhoneConstants#APN_TYPE_MMS} and {@link PhoneConstants#APN_TYPE_SUPL}.
     * @return Success is indicated by {@code PhoneConstants.APN_ALREADY_ACTIVE} or
     *         {@code PhoneConstants.APN_REQUEST_STARTED}. In the latter case, a
     *         broadcast will be sent by the ConnectivityManager when a
     *         connection to the APN has been disconnected. A {@code
     *         PhoneConstants.APN_REQUEST_FAILED} is returned if the type parameter is
     *         invalid or if the apn wasn't enabled.
     */
    public synchronized int disableApnType(String type) {
        if (DBG) log("disableApnType(" + type + ")");
        int id = apnTypeToId(type);
        if (id == DctConstants.APN_INVALID_ID) {
            return PhoneConstants.APN_REQUEST_FAILED;
        }
        if (isApnIdEnabled(id)) {
            setEnabled(id, false);
            if (isApnTypeActive(PhoneConstants.APN_TYPE_DEFAULT)) {
                if (mDataEnabled[DctConstants.APN_DEFAULT_ID]) {
                    return PhoneConstants.APN_ALREADY_ACTIVE;
                } else {
                    return PhoneConstants.APN_REQUEST_STARTED;
                }
            } else {
                return PhoneConstants.APN_REQUEST_STARTED;
            }
        } else {
            return PhoneConstants.APN_REQUEST_FAILED;
        }
    }

    protected void setEnabled(int id, boolean enable) {
        if (DBG) {
            log("setEnabled(" + id + ", " + enable + ") with old state = " + mDataEnabled[id]
                    + " and enabledCount = " + mEnabledCount);
        }
        Message msg = obtainMessage(DctConstants.EVENT_ENABLE_NEW_APN);
        msg.arg1 = id;
        msg.arg2 = (enable ? DctConstants.ENABLED : DctConstants.DISABLED);
        sendMessage(msg);
    }

    protected void onEnableApn(int apnId, int enabled) {
        if (DBG) {
            log("EVENT_APN_ENABLE_REQUEST apnId=" + apnId + ", apnType=" + apnIdToType(apnId) +
                    ", enabled=" + enabled + ", dataEnabled = " + mDataEnabled[apnId] +
                    ", enabledCount = " + mEnabledCount + ", isApnTypeActive = " +
                    isApnTypeActive(apnIdToType(apnId)));
        }
        if (enabled == DctConstants.ENABLED) {
            synchronized (this) {
                if (!mDataEnabled[apnId]) {
                    mDataEnabled[apnId] = true;
                    mEnabledCount++;
                }
            }
            String type = apnIdToType(apnId);
            if (!isApnTypeActive(type)) {
                mRequestedApnType = type;
                onEnableNewApn();
            } else {
                notifyApnIdUpToCurrent(Phone.REASON_APN_SWITCHED, apnId);
            }
        } else {
            // disable
            boolean didDisable = false;
            synchronized (this) {
                if (mDataEnabled[apnId]) {
                    mDataEnabled[apnId] = false;
                    mEnabledCount--;
                    didDisable = true;
                }
            }
            if (didDisable) {
                if ((mEnabledCount == 0) || (apnId == DctConstants.APN_DUN_ID)) {
                    mRequestedApnType = PhoneConstants.APN_TYPE_DEFAULT;
                    onCleanUpConnection(true, apnId, Phone.REASON_DATA_DISABLED);
                }

                    // send the disconnect msg manually, since the normal route wont send
                    // it (it's not enabled)
                    notifyApnIdDisconnected(Phone.REASON_DATA_DISABLED, apnId);
                if (mDataEnabled[DctConstants.APN_DEFAULT_ID] == true
                            && !isApnTypeActive(PhoneConstants.APN_TYPE_DEFAULT)) {
                        // TODO - this is an ugly way to restore the default conn - should be done
                        // by a real contention manager and policy that disconnects the lower pri
                        // stuff as enable requests come in and pops them back on as we disable back
                        // down to the lower pri stuff
                        mRequestedApnType = PhoneConstants.APN_TYPE_DEFAULT;
                        onEnableNewApn();
                }
            }
        }
    }

    /**
     * Called when we switch APNs.
     *
     * mRequestedApnType is set prior to call
     * To be overridden.
     */
    protected void onEnableNewApn() {
    }

    /**
     * Called when EVENT_RESET_DONE is received so goto
     * IDLE state and send notifications to those interested.
     *
     * TODO - currently unused.  Needs to be hooked into DataConnection cleanup
     * TODO - needs to pass some notion of which connection is reset..
     */
    protected void onResetDone(AsyncResult ar) {
        if (DBG) log("EVENT_RESET_DONE");
        String reason = null;
        if (ar.userObj instanceof String) {
            reason = (String) ar.userObj;
        }
        gotoIdleAndNotifyDataConnection(reason);
    }

    /**
     * Prevent mobile data connections from being established, or once again
     * allow mobile data connections. If the state toggles, then either tear
     * down or set up data, as appropriate to match the new state.
     *
     * @param enable indicates whether to enable ({@code true}) or disable (
     *            {@code false}) data
     * @return {@code true} if the operation succeeded
     */
    public boolean setInternalDataEnabled(boolean enable) {
        if (DBG)
            log("setInternalDataEnabled(" + enable + ")");

        Message msg = obtainMessage(DctConstants.EVENT_SET_INTERNAL_DATA_ENABLE);
        msg.arg1 = (enable ? DctConstants.ENABLED : DctConstants.DISABLED);
        sendMessage(msg);
        return true;
    }

    //[mr2] mr2 removed, but MTK reserved, for GsmServiceStateTracker
    public boolean setDataEnabled(boolean enable) {
        //this is for Gemini
        //when restart radio, we do not reset radio actually
        //instead we set gprs to when needed
        //so use this to clean up and expect next retry default data connection
        if (enable) {
            log("setDataEnabled: changed to enabled, try to setup data call");
            //resetAllRetryCounts();
            sendMessage(obtainMessage(DctConstants.EVENT_TRY_SETUP_DATA, Phone.REASON_DATA_ENABLED));
        } else {
            log("setDataEnabled: changed to disabled, cleanUpAllConnections");
            cleanUpAllConnections(null);
        }
        return true;
    }

    protected void onSetInternalDataEnabled(boolean enabled) {
        synchronized (mDataEnabledLock) {
            mInternalDataEnabled = enabled;
            if (enabled) {
                log("onSetInternalDataEnabled: changed to enabled, try to setup data call");
                onTrySetupData(Phone.REASON_DATA_ENABLED);
            } else {
                log("onSetInternalDataEnabled: changed to disabled, cleanUpAllConnections");
                cleanUpAllConnections(null);
            }
        }
    }

    public void cleanUpAllConnections(String cause) {
        Message msg = obtainMessage(DctConstants.EVENT_CLEAN_UP_ALL_CONNECTIONS);
        msg.obj = cause;
        sendMessage(msg);
    }

    public abstract boolean isDisconnected();

    protected void onSetUserDataEnabled(boolean enabled) {
        synchronized (mDataEnabledLock) {
            final boolean prevEnabled = getAnyDataEnabled();
            if (mUserDataEnabled != enabled) {
                mUserDataEnabled = enabled;
                Settings.Global.putInt(mPhone.getContext().getContentResolver(),
                        Settings.Global.MOBILE_DATA, enabled ? 1 : 0);
                if (getDataOnRoamingEnabled() == false &&
                        mPhone.getServiceState().getRoaming() == true) {
                    if (enabled) {
                        notifyOffApnsOfAvailability(Phone.REASON_ROAMING_ON);
                    } else {
                        notifyOffApnsOfAvailability(Phone.REASON_DATA_DISABLED);
                    }
                }
                if (prevEnabled != getAnyDataEnabled()) {
                    if (!prevEnabled) {
                        onTrySetupData(Phone.REASON_DATA_ENABLED);
                    } else {
                        if (FeatureOption.MTK_BSP_PACKAGE ||
                                mPhone.getPhoneType() == PhoneConstants.PHONE_TYPE_CDMA) {
                            onCleanUpAllConnections(Phone.REASON_DATA_DISABLED);
                        } else {
                             for (ApnContext apnContext : mApnContexts.values()) {
                                if (!isDataAllowedAsOff(apnContext.getApnType())) {
                                    apnContext.setReason(Phone.REASON_DATA_DISABLED);
                                    onCleanUpConnection(true, apnTypeToId(apnContext.getApnType()), Phone.REASON_DATA_DISABLED);
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    protected void onSetDependencyMet(String apnType, boolean met) {
    }

    protected void onSetPolicyDataEnabled(boolean enabled) {
        synchronized (mDataEnabledLock) {
            final boolean prevEnabled = getAnyDataEnabled();
            if (sPolicyDataEnabled != enabled) {
                sPolicyDataEnabled = enabled;
                if (prevEnabled != getAnyDataEnabled()) {
                    if (!prevEnabled) {
                        onTrySetupData(Phone.REASON_DATA_ENABLED);
                    } else {
                        onCleanUpAllConnections(Phone.REASON_DATA_DISABLED);
                    }
                }
            }
        }
    }

    protected String getReryConfig(boolean forDefault) {
        int nt = mPhone.getServiceState().getNetworkType();

        if ((nt == TelephonyManager.NETWORK_TYPE_CDMA) ||
            (nt == TelephonyManager.NETWORK_TYPE_1xRTT) ||
            (nt == TelephonyManager.NETWORK_TYPE_EVDO_0) ||
            (nt == TelephonyManager.NETWORK_TYPE_EVDO_A) ||
            (nt == TelephonyManager.NETWORK_TYPE_EVDO_B) ||
            (nt == TelephonyManager.NETWORK_TYPE_EHRPD)) {
            // CDMA variant
            return SystemProperties.get("ro.cdma.data_retry_config");
        } else {
            // Use GSM varient for all others.
            if (forDefault) {
                return SystemProperties.get("ro.gsm.data_retry_config");
            } else {
                return SystemProperties.get("ro.gsm.2nd_data_retry_config");
            }
        }
    }

    protected void resetPollStats() {
        mTxPkts = -1;
        mRxPkts = -1;
        mNetStatPollPeriod = POLL_NETSTAT_MILLIS;
    }

    protected abstract DctConstants.State getOverallState();

    protected void startNetStatPoll() {
        if (getOverallState() == DctConstants.State.CONNECTED && mNetStatPollEnabled == false) {
            if (DBG) log("startNetStatPoll");
            resetPollStats();
            mNetStatPollEnabled = true;
            mPollNetStat.run();
        }
    }

    protected void stopNetStatPoll() {
        mNetStatPollEnabled = false;
        removeCallbacks(mPollNetStat);
        if (DBG) log("stopNetStatPoll");
    }

    public void updateDataActivity() {
        long sent, received;

        DctConstants.Activity newActivity;

        TxRxSum preTxRxSum = new TxRxSum(mTxPkts, mRxPkts);
        TxRxSum curTxRxSum = new TxRxSum();
        curTxRxSum.updateTxRxSum();
        mTxPkts = curTxRxSum.txPkts;
        mRxPkts = curTxRxSum.rxPkts;

        if (VDBG) {
            log("updateDataActivity: curTxRxSum=" + curTxRxSum + " preTxRxSum=" + preTxRxSum);
        }

        if (mNetStatPollEnabled && (preTxRxSum.txPkts > 0 || preTxRxSum.rxPkts > 0)) {
            sent = mTxPkts - preTxRxSum.txPkts;
            received = mRxPkts - preTxRxSum.rxPkts;

            if (VDBG)
                log("updateDataActivity: sent=" + sent + " received=" + received);
            if (sent > 0 && received > 0) {
                newActivity = DctConstants.Activity.DATAINANDOUT;
            } else if (sent > 0 && received == 0) {
                newActivity = DctConstants.Activity.DATAOUT;
            } else if (sent == 0 && received > 0) {
                newActivity = DctConstants.Activity.DATAIN;
            } else {
                newActivity = (mActivity == DctConstants.Activity.DORMANT) ?
                        mActivity : DctConstants.Activity.NONE;
            }

            if (mActivity != newActivity && mIsScreenOn) {
                if (VDBG)
                    log("updateDataActivity: newActivity=" + newActivity);
                mActivity = newActivity;
                mPhone.notifyDataActivity();
            }
        }
    }

    // Recovery action taken in case of data stall
    protected static class RecoveryAction {
        public static final int GET_DATA_CALL_LIST      = 0;
        public static final int CLEANUP                 = 1;
        public static final int REREGISTER              = 2;
        public static final int RADIO_RESTART           = 3;
        public static final int RADIO_RESTART_WITH_PROP = 4;

        private static boolean isAggressiveRecovery(int value) {
            return ((value == RecoveryAction.CLEANUP) ||
                    (value == RecoveryAction.REREGISTER) ||
                    (value == RecoveryAction.RADIO_RESTART) ||
                    (value == RecoveryAction.RADIO_RESTART_WITH_PROP));
        }
    }

    public int getRecoveryAction() {
        int action = Settings.System.getInt(mPhone.getContext().getContentResolver(),
                "radio.data.stall.recovery.action", RecoveryAction.GET_DATA_CALL_LIST);
        if (VDBG_STALL) log("getRecoveryAction: " + action);
        return action;
    }
    public void putRecoveryAction(int action) {
        Settings.System.putInt(mPhone.getContext().getContentResolver(),
                "radio.data.stall.recovery.action", action);
        if (VDBG_STALL) log("putRecoveryAction: " + action);
    }

    protected boolean isConnected() {
        return false;
    }

    protected void doRecovery() {
        if (getOverallState() == DctConstants.State.CONNECTED) {
            // Go through a series of recovery steps, each action transitions to the next action
            int recoveryAction = getRecoveryAction();
            switch (recoveryAction) {
            case RecoveryAction.GET_DATA_CALL_LIST:
                EventLog.writeEvent(EventLogTags.DATA_STALL_RECOVERY_GET_DATA_CALL_LIST,
                        mSentSinceLastRecv);
                if (DBG) log("doRecovery() get data call list");
                mPhone.mCi.getDataCallList(obtainMessage(DctConstants.EVENT_DATA_STATE_CHANGED));
                putRecoveryAction(RecoveryAction.CLEANUP);
                break;
            case RecoveryAction.CLEANUP:
                EventLog.writeEvent(EventLogTags.DATA_STALL_RECOVERY_CLEANUP, mSentSinceLastRecv);
                if (DBG) log("doRecovery() cleanup all connections");
                cleanUpAllConnections(Phone.REASON_PDP_RESET);
                putRecoveryAction(RecoveryAction.REREGISTER);
                break;
            case RecoveryAction.REREGISTER:
                EventLog.writeEvent(EventLogTags.DATA_STALL_RECOVERY_REREGISTER,
                        mSentSinceLastRecv);
                //if (DBG) log("doRecovery() re-register");
                //mPhone.getServiceStateTracker().reRegisterNetwork(null);
                //[new recovery]
                if (DBG) log("doRecovery() reconnect-gprs E");
                cleanUpAllConnections(Phone.REASON_PDP_RESET);
                mPhone.mCi.detachPS(obtainMessage(DctConstants.EVENT_RECOVERY_DETACH_PS));             
                if (DBG) log("doRecovery() reconnect-gprs X");
                putRecoveryAction(RecoveryAction.RADIO_RESTART);
                break;
            case RecoveryAction.RADIO_RESTART:
                EventLog.writeEvent(EventLogTags.DATA_STALL_RECOVERY_RADIO_RESTART,
                        mSentSinceLastRecv);
                if (DBG) log("restarting radio");
                putRecoveryAction(RecoveryAction.RADIO_RESTART_WITH_PROP);
                restartRadio();
                break;
            case RecoveryAction.RADIO_RESTART_WITH_PROP:
                // This is in case radio restart has not recovered the data.
                // It will set an additional "gsm.radioreset" property to tell
                // RIL or system to take further action.
                // The implementation of hard reset recovery action is up to OEM product.
                // Once RADIO_RESET property is consumed, it is expected to set back
                // to false by RIL.
                EventLog.writeEvent(EventLogTags.DATA_STALL_RECOVERY_RADIO_RESTART_WITH_PROP, -1);
                if (DBG) log("restarting radio with gsm.radioreset to true");
                SystemProperties.set(RADIO_RESET_PROPERTY, "true");
                // give 1 sec so property change can be notified.
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {}
                restartRadio();
                putRecoveryAction(RecoveryAction.GET_DATA_CALL_LIST);
                break;
            default:
                throw new RuntimeException("doRecovery: Invalid recoveryAction=" +
                    recoveryAction);
            }
            mSentSinceLastRecv = 0;
        }
    }

    private void updateDataStallInfo() {
        long sent, received;

        TxRxSum preTxRxSum = new TxRxSum(mDataStallTxRxSum);
        mDataStallTxRxSum.updateTxRxSum();

        if (VDBG_STALL) {
            log("updateDataStallInfo: mDataStallTxRxSum=" + mDataStallTxRxSum +
                    " preTxRxSum=" + preTxRxSum);
        }

        sent = mDataStallTxRxSum.txPkts - preTxRxSum.txPkts;
        received = mDataStallTxRxSum.rxPkts - preTxRxSum.rxPkts;

        if (RADIO_TESTS) {
            if (SystemProperties.getBoolean("radio.test.data.stall", false)) {
                log("updateDataStallInfo: radio.test.data.stall true received = 0;");
                received = 0;
            }
        }
        if ( sent > 0 && received > 0 ) {
            if (VDBG_STALL) log("updateDataStallInfo: IN/OUT");
            mSentSinceLastRecv = 0;
            putRecoveryAction(RecoveryAction.GET_DATA_CALL_LIST);
        } else if (sent > 0 && received == 0) {
            if (mPhone.getState() == PhoneConstants.State.IDLE) {
                mSentSinceLastRecv += sent;
            } else {
                mSentSinceLastRecv = 0;
            }
            if (DBG) {
                log("updateDataStallInfo: OUT sent=" + sent +
                        " mSentSinceLastRecv=" + mSentSinceLastRecv);
            }
        } else if (sent == 0 && received > 0) {
            if (VDBG_STALL) log("updateDataStallInfo: IN");
            mSentSinceLastRecv = 0;
            putRecoveryAction(RecoveryAction.GET_DATA_CALL_LIST);
        } else {
            if (VDBG_STALL) log("updateDataStallInfo: NONE");
        }
    }

    protected void onDataStallAlarm(int tag) {
        if (mDataStallAlarmTag != tag) {
            if (DBG) {
                log("onDataStallAlarm: ignore, tag=" + tag + " expecting " + mDataStallAlarmTag);
            }
            return;
        }
        updateDataStallInfo();

        int hangWatchdogTrigger = Settings.Global.getInt(mResolver,
                Settings.Global.PDP_WATCHDOG_TRIGGER_PACKET_COUNT,
                NUMBER_SENT_PACKETS_OF_HANG);

        boolean suspectedStall = DATA_STALL_NOT_SUSPECTED;
        if (mSentSinceLastRecv >= hangWatchdogTrigger) {
            if (DBG) {
                log("onDataStallAlarm: tag=" + tag + " do recovery action=" + getRecoveryAction());
            }
            suspectedStall = DATA_STALL_SUSPECTED;
            sendMessage(obtainMessage(DctConstants.EVENT_DO_RECOVERY));
        } else {
            if (VDBG_STALL) {
                log("onDataStallAlarm: tag=" + tag + " Sent " + String.valueOf(mSentSinceLastRecv) +
                    " pkts since last received, < watchdogTrigger=" + hangWatchdogTrigger);
            }
        }
        startDataStallAlarm(suspectedStall);
    }

    protected boolean isAggressiveRecovery(int action) {
        return RecoveryAction.isAggressiveRecovery(action);
    }

    protected void startDataStallAlarm(boolean suspectedStall) {
        int nextAction = getRecoveryAction();
        int delayInMs;

        if (mDataStallDetectionEnabled && getOverallState() == DctConstants.State.CONNECTED) {
            // If screen is on or data stall is currently suspected, set the alarm
            // with an aggresive timeout.
            if (mIsScreenOn || suspectedStall || isAggressiveRecovery(nextAction)) {
                delayInMs = Settings.Global.getInt(mResolver,
                                           Settings.Global.DATA_STALL_ALARM_AGGRESSIVE_DELAY_IN_MS,
                                           DATA_STALL_ALARM_AGGRESSIVE_DELAY_IN_MS_DEFAULT);
            } else {
                delayInMs = Settings.Global.getInt(mResolver,
                                           Settings.Global.DATA_STALL_ALARM_NON_AGGRESSIVE_DELAY_IN_MS,
                                           DATA_STALL_ALARM_NON_AGGRESSIVE_DELAY_IN_MS_DEFAULT);
            }
    
            mDataStallAlarmTag += 1;
            if (VDBG_STALL) {
                log("startDataStallAlarm: tag=" + mDataStallAlarmTag +
                        " delay=" + (delayInMs / 1000) + "s");
            }
            Intent intent = new Intent(INTENT_DATA_STALL_ALARM);
            intent.putExtra(DATA_STALL_ALARM_TAG_EXTRA, mDataStallAlarmTag);
            if (FeatureOption.MTK_GEMINI_SUPPORT) {
                // M: in gemini project, we need to put extra sim id 
                // to identify wiich sim card has stall problem
                intent.putExtra(PhoneConstants.GEMINI_SIM_ID_KEY, mPhone.getMySimId());
            }
            mDataStallAlarmIntent = PendingIntent.getBroadcast(mPhone.getContext(), 0, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT);
            mAlarmManager.set(AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    SystemClock.elapsedRealtime() + delayInMs, mDataStallAlarmIntent);
        } else {
            if (VDBG_STALL) {
                log("startDataStallAlarm: NOT started, no connection tag=" + mDataStallAlarmTag);
            }
        }
    }

    protected void stopDataStallAlarm() {
        if (VDBG_STALL) {
            log("stopDataStallAlarm: current tag=" + mDataStallAlarmTag +
                    " mDataStallAlarmIntent=" + mDataStallAlarmIntent);
        }
        mDataStallAlarmTag += 1;
        if (mDataStallAlarmIntent != null) {
            mAlarmManager.cancel(mDataStallAlarmIntent);
            mDataStallAlarmIntent = null;
        }
    }

    protected void restartDataStallAlarm() {
        if (isConnected() == false) return;
        // To be called on screen status change.
        // Do not cancel the alarm if it is set with aggressive timeout.
        int nextAction = getRecoveryAction();

        if (isAggressiveRecovery(nextAction)) {
            if (DBG) log("restartDataStallAlarm: action is pending. not resetting the alarm.");
            return;
        }
        if (VDBG_STALL) log("restartDataStallAlarm: stop then start.");
        stopDataStallAlarm();
        startDataStallAlarm(DATA_STALL_NOT_SUSPECTED);
    }

    //[KK] added start, TODO: check
    protected void setInitialAttachApn() {
        ApnSetting iaApnSetting = null;
        ApnSetting defaultApnSetting = null;
        ApnSetting firstApnSetting = null;

        log("setInitialApn: E mPreferredApn=" + mPreferredApn);

        if (mAllApnSettings != null && !mAllApnSettings.isEmpty()) {
            firstApnSetting = mAllApnSettings.get(0);
            log("setInitialApn: firstApnSetting=" + firstApnSetting);

            // Search for Initial APN setting and the first apn that can handle default
            for (ApnSetting apn : mAllApnSettings) {
                // Can't use apn.canHandleType(), as that returns true for APNs that have no type.
                if (ArrayUtils.contains(apn.types, PhoneConstants.APN_TYPE_IA)) {
                    // The Initial Attach APN is highest priority so use it if there is one
                    log("setInitialApn: iaApnSetting=" + apn);
                    iaApnSetting = apn;
                    break;
                } else if ((defaultApnSetting == null)
                        && (apn.canHandleType(PhoneConstants.APN_TYPE_DEFAULT))) {
                    // Use the first default apn if no better choice
                    log("setInitialApn: defaultApnSetting=" + apn);
                    defaultApnSetting = apn;
                }
            }
        }

        // The priority of apn candidates from highest to lowest is:
        //   1) APN_TYPE_IA (Inital Attach)
        //   2) mPreferredApn, i.e. the current preferred apn
        //   3) The first apn that than handle APN_TYPE_DEFAULT
        //   4) The first APN we can find.

        ApnSetting initialAttachApnSetting = null;
        if (iaApnSetting != null) {
            if (DBG) log("setInitialAttachApn: using iaApnSetting");
            initialAttachApnSetting = iaApnSetting;
        } else if (mPreferredApn != null) {
            if (DBG) log("setInitialAttachApn: using mPreferredApn");
            initialAttachApnSetting = mPreferredApn;
        } else if (defaultApnSetting != null) {
            if (DBG) log("setInitialAttachApn: using defaultApnSetting");
            initialAttachApnSetting = defaultApnSetting;
        } else if (firstApnSetting != null) {
            if (DBG) log("setInitialAttachApn: using firstApnSetting");
            initialAttachApnSetting = firstApnSetting;
        }

        if (initialAttachApnSetting == null) {
            if (DBG) log("setInitialAttachApn: X There in no available apn");
        } else {
            if (DBG) log("setInitialAttachApn: X selected Apn=" + initialAttachApnSetting);

            mPhone.mCi.setInitialAttachApn(initialAttachApnSetting.apn,
                    initialAttachApnSetting.protocol, initialAttachApnSetting.authType,
                    initialAttachApnSetting.user, initialAttachApnSetting.password, null);
        }
    }

    protected void onActionIntentProvisioningApnAlarm(Intent intent) {
        if (DBG) log("onActionIntentProvisioningApnAlarm: action=" + intent.getAction());
        Message msg = obtainMessage(DctConstants.EVENT_PROVISIONING_APN_ALARM,
                intent.getAction());
        msg.arg1 = intent.getIntExtra(PROVISIONING_APN_ALARM_TAG_EXTRA, 0);
        sendMessage(msg);
    }

    protected void startProvisioningApnAlarm() {
        int delayInMs = Settings.Global.getInt(mResolver,
                                Settings.Global.PROVISIONING_APN_ALARM_DELAY_IN_MS,
                                PROVISIONING_APN_ALARM_DELAY_IN_MS_DEFAULT);
        if (Build.IS_DEBUGGABLE) {
            // Allow debug code to use a system property to provide another value
            String delayInMsStrg = Integer.toString(delayInMs);
            delayInMsStrg = System.getProperty(DEBUG_PROV_APN_ALARM, delayInMsStrg);
            try {
                delayInMs = Integer.parseInt(delayInMsStrg);
            } catch (NumberFormatException e) {
                loge("startProvisioningApnAlarm: e=" + e);
            }
        }
        mProvisioningApnAlarmTag += 1;
        if (DBG) {
            log("startProvisioningApnAlarm: tag=" + mProvisioningApnAlarmTag +
                    " delay=" + (delayInMs / 1000) + "s");
        }
        Intent intent = new Intent(INTENT_PROVISIONING_APN_ALARM);
        intent.putExtra(PROVISIONING_APN_ALARM_TAG_EXTRA, mProvisioningApnAlarmTag);
        mProvisioningApnAlarmIntent = PendingIntent.getBroadcast(mPhone.getContext(), 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT);
        mAlarmManager.set(AlarmManager.ELAPSED_REALTIME_WAKEUP,
                SystemClock.elapsedRealtime() + delayInMs, mProvisioningApnAlarmIntent);
    }

    protected void stopProvisioningApnAlarm() {
        if (DBG) {
            log("stopProvisioningApnAlarm: current tag=" + mProvisioningApnAlarmTag +
                    " mProvsioningApnAlarmIntent=" + mProvisioningApnAlarmIntent);
        }
        mProvisioningApnAlarmTag += 1;
        if (mProvisioningApnAlarmIntent != null) {
            mAlarmManager.cancel(mProvisioningApnAlarmIntent);
            mProvisioningApnAlarmIntent = null;
        }
    }

    void sendRestartRadio() {
        if (DBG)log("sendRestartRadio:");
        Message msg = obtainMessage(DctConstants.EVENT_RESTART_RADIO);
        sendMessage(msg);
    }    
    //[KK] added end

    //[mr2] for Dcc use
    void sendCleanUpConnection(boolean tearDown, ApnContext apnContext) {
        if (DBG)log("sendCleanUpConnection: tearDown=" + tearDown + " apnContext=" + apnContext);
        Message msg = obtainMessage(DctConstants.EVENT_CLEAN_UP_CONNECTION);
        msg.arg1 = tearDown ? 1 : 0;
        msg.arg2 = 0;
        msg.obj = apnContext;
        sendMessage(msg);
    }

    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        pw.println("DataConnectionTrackerBase:");
        pw.println(" RADIO_TESTS=" + RADIO_TESTS);
        pw.println(" mInternalDataEnabled=" + mInternalDataEnabled);
        pw.println(" mUserDataEnabled=" + mUserDataEnabled);
        pw.println(" sPolicyDataEnabed=" + sPolicyDataEnabled);
        pw.println(" mDataEnabled:");
        for(int i=0; i < mDataEnabled.length; i++) {
            pw.printf("  mDataEnabled[%d]=%b\n", i, mDataEnabled[i]);
        }
        pw.flush();
        pw.println(" mEnabledCount=" + mEnabledCount);
        pw.println(" mRequestedApnType=" + mRequestedApnType);
        pw.println(" mPhone=" + mPhone.getPhoneName());
        pw.println(" mActivity=" + mActivity);
        pw.println(" mState=" + mState);
        pw.println(" mTxPkts=" + mTxPkts);
        pw.println(" mRxPkts=" + mRxPkts);
        pw.println(" mNetStatPollPeriod=" + mNetStatPollPeriod);
        pw.println(" mNetStatPollEnabled=" + mNetStatPollEnabled);
        pw.println(" mDataStallTxRxSum=" + mDataStallTxRxSum);
        pw.println(" mDataStallAlarmTag=" + mDataStallAlarmTag);
        pw.println(" mDataStallDetectionEanbled=" + mDataStallDetectionEnabled);
        pw.println(" mSentSinceLastRecv=" + mSentSinceLastRecv);
        pw.println(" mNoRecvPollCount=" + mNoRecvPollCount);
        pw.println(" mResolver=" + mResolver);
        pw.println(" mIsWifiConnected=" + mIsWifiConnected);
        pw.println(" mReconnectIntent=" + mReconnectIntent);
        pw.println(" mCidActive=" + mCidActive);
        pw.println(" mAutoAttachOnCreation=" + mAutoAttachOnCreation);
        pw.println(" mIsScreenOn=" + mIsScreenOn);
        pw.println(" mUniqueIdGenerator=" + mUniqueIdGenerator);
        pw.flush();
        pw.println(" ***************************************");
        DcController dcc = mDcc;
        if (dcc != null) {
            dcc.dump(fd, pw, args);
        } else {
            pw.println(" mDcc=null");
        }
        pw.println(" ***************************************");
        HashMap<Integer, DataConnection> dcs = mDataConnections;
        if (dcs != null) {
            Set<Entry<Integer, DataConnection> > mDcSet = mDataConnections.entrySet();
            pw.println(" mDataConnections: count=" + mDcSet.size());
            for (Entry<Integer, DataConnection> entry : mDcSet) {
                pw.printf(" *** mDataConnection[%d] \n", entry.getKey());
                entry.getValue().dump(fd, pw, args);
            }
        } else {
            pw.println("mDataConnections=null");
        }
        pw.println(" ***************************************");
        pw.flush();
        HashMap<String, Integer> apnToDcId = mApnToDataConnectionId;
        if (apnToDcId != null) {
            Set<Entry<String, Integer>> apnToDcIdSet = apnToDcId.entrySet();
            pw.println(" mApnToDataConnectonId size=" + apnToDcIdSet.size());
            for (Entry<String, Integer> entry : apnToDcIdSet) {
            pw.printf(" mApnToDataConnectonId[%s]=%d\n", entry.getKey(), entry.getValue());
            }
        } else {
            pw.println("mApnToDataConnectionId=null");
        }
        pw.println(" ***************************************");
        pw.flush();
        ConcurrentHashMap<String, ApnContext> apnCtxs = mApnContexts;
        if (apnCtxs != null) {
            Set<Entry<String, ApnContext>> apnCtxsSet = apnCtxs.entrySet();
            pw.println(" mApnContexts size=" + apnCtxsSet.size());
            for (Entry<String, ApnContext> entry : apnCtxsSet) {
                entry.getValue().dump(fd, pw, args);
            }
            pw.println(" ***************************************");
        } else {
            pw.println(" mApnContexts=null");
        }
        pw.flush();
        pw.println(" mActiveApn=" + mActiveApn);
        ArrayList<ApnSetting> apnSettings = mAllApnSettings;
        if (apnSettings != null) {
            pw.println(" mAllApnSettings size=" + apnSettings.size());
            for (int i=0; i < apnSettings.size(); i++) {
                pw.printf(" mAllApnSettings[%d]: %s\n", i, apnSettings.get(i));
            }
            pw.flush();
        } else {
            pw.println(" mAllApnSettings=null");
        }
        pw.println(" mPreferredApn=" + mPreferredApn);
        pw.println(" mIsPsRestricted=" + mIsPsRestricted);
        pw.println(" mIsDisposed=" + mIsDisposed);
        pw.println(" mIntentReceiver=" + mIntentReceiver);
        pw.println(" mDataRoamingSettingObserver=" + mDataRoamingSettingObserver);
        pw.flush();
    }

    //MTK-START [mtk04070][111205][ALPS00093395]MTK proprietary methods
    protected int getDataConnectionFromSetting(){
        int currentDataConnectionSimId = -1;
        
        // GPRS_CONNECTION_SIM_SETTING and GPRS_CONNECTION_SETTING is synced in ConnectivityService.java
        currentDataConnectionSimId =  Settings.System.getInt(mPhone.getContext().getContentResolver(), GPRS_CONNECTION_SETTING, GPRS_CONNECTION_SETTING_DEFAULT) - 1;            

        logd("Default Data Setting value=" + currentDataConnectionSimId);

        return currentDataConnectionSimId;
    }

    protected void logd(String s) {
        if (FeatureOption.MTK_GEMINI_SUPPORT) {
            Rlog.d(LOG_TAG, "[GDCT][simId" + mPhone.getMySimId()+ "]"+ s);
        } else {
            Rlog.d(LOG_TAG, "[GDCT] " + s);
        }
    }

    public void updateMobileData() {
        //do nothing
    }

    protected boolean isDataAllowedAsOff(String apnType) {
        return false;
    }
    //MTK-END [mtk04070][111205][ALPS00093395]MTK proprietary methods/classes/receivers

    //[New R8 modem FD]
    /**
       * setFDTimerValue
       * @param String array for new Timer Value
       * @param Message for on complete
       * @internal
       */
    public int setFDTimerValue(String newTimerValue[], Message onComplete) {
        return 0;    
    }
    
    //[New R8 modem FD]
    /**
       * getFDTimerValue
       * @return null 
       * @internal
       */
    public String[] getFDTimerValue() {
        return null;
    }

    protected void syncRoamingSetting() {
        if (FeatureOption.MTK_GEMINI_SUPPORT && FeatureOption.MTK_GEMINI_ENHANCEMENT) {
            final ContentResolver resolver = mPhone.getContext().getContentResolver();
            SIMInfo simInfo = SIMInfo.getSIMInfoBySlot(mPhone.getContext(), mPhone.getMySimId());
            if (simInfo != null) {
                boolean dataRoaming = (simInfo.mDataRoaming == SimInfo.DATA_ROAMING_ENABLE);
                logd("dataRoaming = " + dataRoaming);
                Settings.Global.putInt(resolver, mRoamingSetting[mPhone.getMySimId()], dataRoaming ? 1 : 0);
            }
        }
    }

    // M: For multiple SIM support to check is any connection active
    public boolean isAllConnectionInactive() {
        if (DBG) logd("isAllConnectionInactive(): Default impl always return true.");
        return true;
    }


    public int dialUpCsd(int simId, String dialUpNumber) {
        if (DBG) logd("dialUpCsd(): Default impl always do nothing.");
        return 0;
    }


    //via support start
    protected void notifyPreferOrConnectedApn(String id) {}
    //via support end
}
