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

package com.android.internal.telephony.gsm;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.database.ContentObserver;
import android.os.AsyncResult;
import android.os.Build;
import android.os.Handler;
import android.os.Message;
import android.os.PowerManager;
import android.os.SystemClock;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.provider.Settings;
import android.provider.Settings.SettingNotFoundException;
import android.telephony.CellIdentityGsm;
import android.telephony.CellIdentityLte;
import android.telephony.CellIdentityWcdma;
import android.telephony.CellInfo;
import android.telephony.CellInfoGsm;
import android.telephony.CellInfoLte;
import android.telephony.CellInfoWcdma;
import android.telephony.CellLocation;
import android.telephony.Rlog;
import android.telephony.ServiceState;
import android.telephony.SignalStrength;
import android.telephony.gsm.GsmCellLocation;
import android.text.TextUtils;
import android.util.EventLog;
import android.util.TimeUtils;
import android.os.Registrant;
import android.os.RegistrantList;

import com.android.internal.telephony.CommandException;
import com.android.internal.telephony.CommandsInterface;
import com.android.internal.telephony.EventLogTags;
import com.android.internal.telephony.MccTable;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneProxy;
import com.android.internal.telephony.RILConstants;
import com.android.internal.telephony.RestrictedState;
import com.android.internal.telephony.ServiceStateTracker;
import com.android.internal.telephony.TelephonyIntents;
import com.android.internal.telephony.TelephonyProperties;
import com.android.internal.telephony.dataconnection.DcTrackerBase;
import com.android.internal.telephony.dataconnection.DcTracker;
import com.android.internal.telephony.uicc.IccCardApplicationStatus.AppState;
import com.android.internal.telephony.uicc.IccRecords;
import com.android.internal.telephony.uicc.SIMRecords;
import com.android.internal.telephony.uicc.UiccCardApplication;
import com.android.internal.telephony.uicc.UiccController;
import com.android.internal.telephony.IccCardConstants;
import com.android.internal.telephony.uicc.IccRefreshResponse;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;

//MTK-START [mtk03851][111124]MTK added
import com.mediatek.common.featureoption.FeatureOption;
import com.android.internal.telephony.RIL;
import android.provider.Telephony.SIMInfo;
import android.telephony.TelephonyManager;
import com.mediatek.common.MediatekClassFactory;
import com.mediatek.common.telephony.IServiceStateExt;
import com.android.internal.telephony.gemini.GeminiNetworkSubUtil;
import com.android.internal.telephony.PhoneFactory;
import android.net.ConnectivityManager;
//MTK-END [mtk03851][111124]MTK added

 //[ALPS00435948] MTK ADD-START
import com.mediatek.internal.R;
 //[ALPS00435948] MTK ADD-END

import com.android.internal.telephony.gemini.GeminiPhone;
import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.PhoneFactory;
import android.provider.Telephony.Intents;
import android.app.Activity;
import android.app.Service;
import android.content.ServiceConnection;
import android.content.ComponentName;
import java.util.Timer;
import java.util.TimerTask;

/**
 * {@hide}
 */
public final class GsmServiceStateTracker extends ServiceStateTracker {
    static final String LOG_TAG = "GSM";
    private static final boolean VDBG = false;

    GSMPhone mPhone;
    GsmCellLocation mCellLoc;
    GsmCellLocation mNewCellLoc;
    int mPreferredNetworkType;

    private int gprsState = ServiceState.STATE_OUT_OF_SERVICE;
    private int newGPRSState = ServiceState.STATE_OUT_OF_SERVICE;
    private int mMaxDataCalls = 1;
    private int mNewMaxDataCalls = 1;
    private int mReasonDataDenied = -1;
    private int mNewReasonDataDenied = -1;

    protected static final int EVENT_SET_GPRS_CONN_TYPE_DONE = 51;
    protected static final int EVENT_SET_GPRS_CONN_RETRY = 52;

    private int gprsConnType = 0;
    /**
     * GSM roaming status solely based on TS 27.007 7.2 CREG. Only used by
     * handlePollStateResult to store CREG roaming result.
     */
    private boolean mGsmRoaming = false;

    /**
     * Data roaming status solely based on TS 27.007 10.1.19 CGREG. Only used by
     * handlePollStateResult to store CGREG roaming result.
     */
    private boolean mDataRoaming = false;

    /**
     * Mark when service state is in emergency call only mode
     */
    private boolean mEmergencyOnly = false;

    /**
     * Sometimes we get the NITZ time before we know what country we
     * are in. Keep the time zone information from the NITZ string so
     * we can fix the time zone once know the country.
     */
    private boolean mNeedFixZoneAfterNitz = false;
    private int mZoneOffset;
    private boolean mZoneDst;
    private long mZoneTime;
    private boolean mGotCountryCode = false;
    private ContentResolver mCr;

    /** Boolean is true is setTimeFromNITZString was called */
    private boolean mNitzUpdatedTime = false;

    String mSavedTimeZone;
    long mSavedTime;
    long mSavedAtTime;

    /**
     * We can't register for SIM_RECORDS_LOADED immediately because the
     * SIMRecords object may not be instantiated yet.
     */
    // remark for MR1 Migration
    //private boolean mNeedToRegForSimLoaded;

    /** Started the recheck process after finding gprs should registered but not. */
    private boolean mStartedGprsRegCheck = false;

    /** Already sent the event-log for no gprs register. */
    private boolean mReportedGprsNoReg = false;

    /**
     * The Notification object given to the NotificationManager.
     */
    private Notification mNotification;

    /** Wake lock used while setting time of day. */
    private PowerManager.WakeLock mWakeLock;
    private static final String WAKELOCK_TAG = "ServiceStateTracker";

    /** Keep track of SPN display rules, so we only broadcast intent if something changes. */
    private String mCurSpn = null;
    private String mCurPlmn = null;

    //MTK-START [ALPS415367]For MR1 Migration
    //private int curSpnRule = 0;
    private boolean mCurShowPlmn = false;
    private boolean mCurShowSpn = false;
    //MTK-END [ALPS415367]For MR1 Migration

    private String mHhbName = null;
    private String mCsgId = null;
    private int mFemtocellDomain = 0;

    /** waiting period before recheck gprs and voice registration. */
    static final int DEFAULT_GPRS_CHECK_PERIOD_MILLIS = 60 * 1000;

    /* mtk01616 ALPS00236452: manufacturer maintained table for specific operator with multiple PLMN id */
    // ALFMS00040828 - add "46008"
    private String[][] customEhplmn = {{"46000","46002","46007","46008"},
                                       {"45400","45402","45418"},
                                       {"45403","45404"},
                                       {"45412","45413"},                                       
    	                               {"45416","45419"},
                                       {"45501","45504"},
                                       {"45503","45505"},
                                       {"45002","45008"},
                                       {"52501","52502"},
                                       {"43602","43612"},
                                       {"52010","52099"},
                                       {"24001","24005"},
                                       {"26207","26208"},
                                       {"23430","23431","23432"},
                                       {"72402","72403","72404"},
                                       {"72406","72410","72411","72423"},
                                       {"72432","72433","72434"},
                                       {"31026","31031","310160","310200","310210","310220","310230","310240","310250","310260","310270","310660"},
                                       {"310150","310170","310380","310410"}};

    /** Notification type. */
    static final int PS_ENABLED = 1001;            // Access Control blocks data service
    static final int PS_DISABLED = 1002;           // Access Control enables data service
    static final int CS_ENABLED = 1003;            // Access Control blocks all voice/sms service
    static final int CS_DISABLED = 1004;           // Access Control enables all voice/sms service
    static final int CS_NORMAL_ENABLED = 1005;     // Access Control blocks normal voice/sms service
    static final int CS_EMERGENCY_ENABLED = 1006;  // Access Control blocks emergency call service

    /** Notification id. */
    static final int PS_NOTIFICATION = 888;  // Id to update and cancel PS restricted
    static final int CS_NOTIFICATION = 999;  // Id to update and cancel CS restricted

    /** mtk01616_120613 Notification id. */
    static final int REJECT_NOTIFICATION = 890;
    static final int REJECT_NOTIFICATION_2 = 8902;
    public boolean dontUpdateNetworkStateFlag = false;

//MTK-START [mtk03851][111124]MTK added
    protected static final int EVENT_SET_AUTO_SELECT_NETWORK_DONE = 50;
    /** Indicate the first radio state changed **/
    private boolean mFirstRadioChange = true;
    private boolean mIs3GTo2G = false;

    /** Auto attach PS service when SIM Ready **/
    private int mAutoGprsAttach = 1;
    private int mSimId;
    /**
     *  Values correspond to ServiceStateTracker.DATA_ACCESS_ definitions.
     */
    private int ps_networkType = 0;
    private int newps_networkType = 0;
    private int DEFAULT_GPRS_RETRY_PERIOD_MILLIS = 30 * 1000;
    private int explict_update_spn = 0;


    private String mLastRegisteredPLMN = null;
    private String mLastPSRegisteredPLMN = null;

    private boolean mEverIVSR = false;	/* ALPS00324111: at least one chance to do IVSR  */

    //MTK-ADD: for for CS not registered , PS regsitered (ex: LTE PS only mode or 2/3G PS only SIM card or CS domain network registeration temporary failure
    private boolean isCsInvalidCard=false;
    
    private RegistrantList ratPsChangedRegistrants = new RegistrantList();
    private RegistrantList ratCsChangedRegistrants = new RegistrantList();

    /** Notification id. */
    static final int PS_NOTIFICATION_2 = 8882;  // Id to update and cancel PS restricted
    static final int CS_NOTIFICATION_2 = 9992;  // Id to update and cancel CS restricted
    private IServiceStateExt mServiceStateExt;

    private String mSimState = IccCardConstants.INTENT_VALUE_ICC_UNKNOWN;

    //MTK-START : [ALPS01262709] update TimeZone by MCC/MNC
    /* manufacturer maintained table for specific timezone 
         with multiple timezone of country in time_zones_by_country.xml */
    private String[][] mTimeZoneIdOfCapitalCity = {{"au","Australia/Sydney"},
                                                   {"br","America/Sao_Paulo"},
                                                   {"ca","America/Toronto"},
                                                   {"cl","America/Santiago"},
                                                   {"es","Europe/Madrid"},
                                                   {"gl","America/Godthab"},
                                                   {"id","Asia/Jakarta"},
                                                   {"kz","Asia/Almaty"},
                                                   {"mx","America/Mexico_City"},
                                                   {"pf","Pacific/Tahiti"},
                                                   {"ru","Europe/Moscow"},
                                                   {"us","America/New_York"}
                                                  };
    //MTK-END [ALPS01262709]

//MTK-END [mtk03851][111124]MTK added

    private boolean mIsImeiLock = false;
    private boolean mIsTurnOnRadio = false;

    //[ALPS01132085] for NetworkType display abnormal
    private boolean mIsScreenOn = false;    

    private static Timer mCellInfoTimer = null;
  
    //[ALPS01035028] -- start
    CommandsInterface.RadioState mRadioState = CommandsInterface.RadioState.RADIO_UNAVAILABLE;
    //[ALPS01035028] -- end
    private BroadcastReceiver mIntentReceiver = new BroadcastReceiver() {

        //[ALPS01012597] -- START
        public static final String INTENT_KEY_DETECT_STATUS = "simDetectStatus";        
        public static final String INTENT_KEY_NEW_SIM_SLOT = "newSIMSlot";        
        public static final String EXTRA_VALUE_NEW_SIM = "NEW";

        private static final int STATUS_SIM1_INSERTED = 0x01;
        private static final int STATUS_SIM2_INSERTED = 0x02;
        private static final int STATUS_SIM3_INSERTED = 0x04;
        private static final int STATUS_SIM4_INSERTED = 0x08;
        //[ALPS01012597] -- END
        
        @Override
        public void onReceive(Context context, Intent intent) {

            //MTK-START: For KK Migration
            if (!mPhone.mIsTheCurrentActivePhone) {
                Rlog.e(LOG_TAG, "Received Intent " + intent +
                        " while being destroyed. Ignoring.");
                return;
            }
            //MTK-END: For KK Migration

            log("BroadcastReceiver: " + intent.getAction());
            if (intent.getAction().equals(Intent.ACTION_LOCALE_CHANGED)) {
                // update emergency string whenever locale changed
                updateSpnDisplay();
            } else if (intent.getAction().equals(Intent.ACTION_SCREEN_ON)) {
                log("ACTION_SCREEN_ON");
                //[ALPS01132085] for NetworkType display abnormal
                mIsScreenOn = true;                
                pollState();
                log("set explict_update_spn = 1");
                explict_update_spn = 1;
                if (mServiceStateExt.needEMMRRS()) {
                    if (mSimId == getDataConnectionSimId()) {
                        getEINFO(EVENT_ENABLE_EMMRRS_STATUS);
                    }
                }
            } else if (intent.getAction().equals(Intent.ACTION_SCREEN_OFF)) {
                log("ACTION_SCREEN_OFF");
                //[ALPS01132085] for NetworkType display abnormal
                mIsScreenOn = false;
                if (mServiceStateExt.needEMMRRS()) {
                    if (mSimId == getDataConnectionSimId()) {
                        getEINFO(EVENT_DISABLE_EMMRRS_STATUS);
                    }
                }
            } else if (intent.getAction().equals("com.mtk.TEST_TRM")){ //ALPS00242220
                int mode = intent.getIntExtra("mode", 2); 
                int slot = intent.getIntExtra("slot", 0);//RFU
                log("TEST mode"+mode+" slot="+slot);

                if (PhoneFactory.isDualTalkMode()) {
                    if (PhoneFactory.getTelephonyMode() == PhoneFactory.MODE_0_NONE) {
                        if((mode == 2)&&(mSimId == PhoneConstants.GEMINI_SIM_1))
                            mPhone.setTRM(2,null);
                    } else {
                        if(mode == 2) {
                            if ((PhoneFactory.getFirstMD() == 1 && mSimId == PhoneConstants.GEMINI_SIM_1) ||
                                (PhoneFactory.getFirstMD() == 2 && mSimId == PhoneConstants.GEMINI_SIM_2))
                            {
                                mPhone.setTRM(mode, null);
                            }
                        } else if (mode == 6) {
                            if ((PhoneFactory.getFirstMD() == 1 && mSimId == PhoneConstants.GEMINI_SIM_2) ||
                                (PhoneFactory.getFirstMD() == 2 && mSimId == PhoneConstants.GEMINI_SIM_1))
                            {
                                mPhone.setTRM(mode, null);
                            }
                        }
                    }
                } else {
                    if((mode == 2)&&(mSimId == PhoneConstants.GEMINI_SIM_1))
                        mPhone.setTRM(2,null);
                }
            } else if (TelephonyIntents.ACTION_SIM_STATE_CHANGED.equals(intent.getAction())) {
                log("ACTION_SIM_STATE_CHANGED");
                
                //[ALPS01010930] -- Add 
                String previousSimState = mSimState;

                if (FeatureOption.MTK_GEMINI_SUPPORT == true) {
                    int slotId = intent.getIntExtra(PhoneConstants.GEMINI_SIM_ID_KEY, -1);
                    if(slotId == mSimId) {
                        mSimState = intent.getStringExtra(IccCardConstants.INTENT_KEY_ICC_STATE);
                        log("SIM state change, simId: " + mSimId + " simState[" + mSimState + "]");
                    }
                } else {
                    mSimState = intent.getStringExtra(IccCardConstants.INTENT_KEY_ICC_STATE);
                    log("SIM state change, simId: " + mSimId + " simState[" + mSimState + "]");
                }
                
                //[ALPS01010930] -- START
                // ServcieState and RegState will set to OUT_OF_SERVICE when SIM state is LOCKED or ABSENT 
                // If SIM state changed to READY then do pollState() to sync. ServcieState and RegState.
                // And tigger updateSimIndicateState when pollStateDone.
                if (!previousSimState.equals(mSimState)){
                    if ((IccCardConstants.INTENT_VALUE_ICC_LOCKED.equals(previousSimState) ||
                          IccCardConstants.INTENT_VALUE_ICC_ABSENT.equals(previousSimState)) &&
                        IccCardConstants.INTENT_VALUE_ICC_READY.equals(mSimState)){
                        log("excuate pollState() to sync. ServcieState and RegStat ");
                        pollState();
                    }
                }
                //[ALPS01010930] -- END
                
            } else if (TelephonyIntents.ACTION_SIM_DETECTED.equals(intent.getAction())) {
                //[ALPS01012597] -- START
                // reset MM infromation from network(+CIEV:10) when SIM card switched
                log("ACTION_SIM_DETECTED");				
                String status = intent.getStringExtra(INTENT_KEY_DETECT_STATUS);
                int mNewSimSlot = intent.getIntExtra(INTENT_KEY_NEW_SIM_SLOT, -1);
                log("SIM_DETECTED, status: " + status + " newSimSlot: " + mNewSimSlot);
                switch (mSimId) {
                    case PhoneConstants.GEMINI_SIM_1:
                        if ((mNewSimSlot & STATUS_SIM1_INSERTED) == STATUS_SIM1_INSERTED){
                            SystemProperties.set(TelephonyProperties.PROPERTY_NITZ_OPER_CODE, null);
                            SystemProperties.set(TelephonyProperties.PROPERTY_NITZ_OPER_LNAME, null);
                            SystemProperties.set(TelephonyProperties.PROPERTY_NITZ_OPER_SNAME, null);
                            log("reset SIM1's MM infromation from Netwowrk");
                        }
                        break;
                    case PhoneConstants.GEMINI_SIM_2:
                        if ((mNewSimSlot & STATUS_SIM2_INSERTED) == STATUS_SIM2_INSERTED){
                            SystemProperties.set(TelephonyProperties.PROPERTY_NITZ_OPER_CODE2, null);
                            SystemProperties.set(TelephonyProperties.PROPERTY_NITZ_OPER_LNAME2, null);
                            SystemProperties.set(TelephonyProperties.PROPERTY_NITZ_OPER_SNAME2, null);
                            log("reset SIM2's MM infromation from Netwowrk");
                        }
                        break;
                    case PhoneConstants.GEMINI_SIM_3:
                        if ((mNewSimSlot & STATUS_SIM3_INSERTED) == STATUS_SIM3_INSERTED){
                            SystemProperties.set(TelephonyProperties.PROPERTY_NITZ_OPER_CODE3, null);
                            SystemProperties.set(TelephonyProperties.PROPERTY_NITZ_OPER_LNAME3, null);
                            SystemProperties.set(TelephonyProperties.PROPERTY_NITZ_OPER_SNAME3, null);
                            log("reset SIM3's MM infromation from Netwowrk");
                        }
                        break;
                    case PhoneConstants.GEMINI_SIM_4:
                        if ((mNewSimSlot & STATUS_SIM4_INSERTED) == STATUS_SIM4_INSERTED){
                            SystemProperties.set(TelephonyProperties.PROPERTY_NITZ_OPER_CODE4, null);
                            SystemProperties.set(TelephonyProperties.PROPERTY_NITZ_OPER_LNAME4, null);
                            SystemProperties.set(TelephonyProperties.PROPERTY_NITZ_OPER_SNAME4, null);
                            log("reset SIM4's MM infromation from Netwowrk");
                        }
                        break;
                    default:
                        log("no SIM inserted");
                        break;                        
                }
                //[ALPS01012597] -- END
            }        
        }
    };

    private ContentObserver mAutoTimeObserver = new ContentObserver(new Handler()) {
        @Override
        public void onChange(boolean selfChange) {
            Rlog.i("GsmServiceStateTracker", "Auto time state changed");
            revertToNitzTime();
        }
    };

    private ContentObserver mAutoTimeZoneObserver = new ContentObserver(new Handler()) {
        @Override
        public void onChange(boolean selfChange) {
            Rlog.i("GsmServiceStateTracker", "Auto time zone state changed");
            revertToNitzTimeZone();
        }
    };

    //MTK-START [MTK80515] [ALPS00368272]
    private ContentObserver mDataConnectionSimSettingObserver = new ContentObserver(new Handler()) {
        @Override
        public void onChange(boolean selfChange) {
            log("Data Connection Sim Setting changed");
            if (mServiceStateExt.needEMMRRS()) {
                if (mSimId == getDataConnectionSimId()) {
                    getEINFO(EVENT_ENABLE_EMMRRS_STATUS);
                } else {
                    getEINFO(EVENT_DISABLE_EMMRRS_STATUS);
                }
            }
        }
    };
    //MTK-END [MTK80515] [ALPS00368272]

    public GsmServiceStateTracker(GSMPhone phone) {
        super(phone, phone.mCi, new CellInfoGsm(), phone.getMySimId());

        mPhone = phone;
        mCellLoc = new GsmCellLocation();
        mNewCellLoc = new GsmCellLocation();

//MTK-START [mtk03851][111124]MTK added
        mSimId = phone.getMySimId();
//MTK-START [mtk03851][111124]MTK added

        mCi = phone.mCi;
        //MTK-START [ALPS415367]For MR1 Migration
        //ss = new ServiceState(mSimId);
        //newSS = new ServiceState(mSimId);
        //cellLoc = new GsmCellLocation();
        //newCellLoc = new GsmCellLocation();
        //mSignalStrength = new SignalStrength(mSimId);
        //MTK-END [ALPS415367]For MR1 Migration

        PowerManager powerManager =
                (PowerManager)phone.getContext().getSystemService(Context.POWER_SERVICE);
        mWakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKELOCK_TAG);

        try{
            mServiceStateExt = MediatekClassFactory.createInstance(IServiceStateExt.class, phone.getContext());
        } catch (Exception e){
            e.printStackTrace();
        }

        mCi.registerForAvailable(this, EVENT_RADIO_AVAILABLE, null);
        mCi.registerForRadioStateChanged(this, EVENT_RADIO_STATE_CHANGED, null);

        mCi.registerForVoiceNetworkStateChanged(this, EVENT_NETWORK_STATE_CHANGED, null);
        mCi.registerForPsNetworkStateChanged(this, EVENT_PS_NETWORK_STATE_CHANGED, null);
        mCi.setOnNITZTime(this, EVENT_NITZ_TIME, null);
        mCi.registerForSimPlugOut(this, EVENT_SIM_PLUG_OUT, null);

        //MTK-START [ALPS415367]For MR1 Migration
        //mCi.setOnSignalStrengthUpdate(this, EVENT_SIGNAL_STRENGTH_UPDATE, null);
        //MTK-END [ALPS415367]For MR1 Migration

        mCi.setOnRestrictedStateChanged(this, EVENT_RESTRICTED_STATE_CHANGED, null);
        mCi.registerForSIMReady(this, EVENT_SIM_READY, null);
        mCi.setGprsDetach(this, EVENT_DATA_CONNECTION_DETACHED, null);
        mCi.setInvalidSimInfo(this, EVENT_INVALID_SIM_INFO, null);//ALPS00248788
        if(mServiceStateExt.isImeiLocked())
            mCi.registerForIMEILock(this, EVENT_IMEI_LOCK, null);

        mCi.registerForIccRefresh(this,EVENT_ICC_REFRESH,null);

        if(FeatureOption.MTK_FEMTO_CELL_SUPPORT)
            mCi.registerForFemtoCellInfo(this,EVENT_FEMTO_CELL_INFO,null);

        // system setting property AIRPLANE_MODE_ON is set in Settings.
        int airplaneMode = Settings.Global.getInt(
                phone.getContext().getContentResolver(),
                Settings.Global.AIRPLANE_MODE_ON, 0);
        /* for consistent UI ,SIM Management for single sim project START */
        if (!FeatureOption.MTK_GEMINI_SUPPORT) {
            int simMode = Settings.System.getInt(phone.getContext().getContentResolver(), Settings.System.DUAL_SIM_MODE_SETTING, 1);
            /* ALPS00447303 */
            Rlog.e(LOG_TAG, "Set mDesiredPowerState in setRadioPowerOn. simMode="+simMode+",airplaneMode="+airplaneMode);
            mDesiredPowerState = (simMode > 0) && (! (airplaneMode > 0));                        						
        }
        /* for consistent UI ,SIM Management for single sim project  END*/
        else{
            mDesiredPowerState = ! (airplaneMode > 0);
        }			
        Rlog.e(LOG_TAG, "Final mDesiredPowerState for single sim. [" + mDesiredPowerState + "] airplaneMode=" + airplaneMode);

        mCr = phone.getContext().getContentResolver();
        mCr.registerContentObserver(
                Settings.Global.getUriFor(Settings.Global.AUTO_TIME), true,
                mAutoTimeObserver);
        mCr.registerContentObserver(
                Settings.Global.getUriFor(Settings.Global.AUTO_TIME_ZONE), true,
                mAutoTimeZoneObserver);

        setSignalStrengthDefaultValues();
        //MTK-START [ALPS415367]For MR1 Migration
        //mNeedToRegForSimLoaded = true;
        //MTK-END [ALPS415367]For MR1 Migration

        // Monitor locale change
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_LOCALE_CHANGED);
        filter.addAction(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        filter.addAction("com.mtk.TEST_TRM");//ALPS00242220
        filter.addAction(TelephonyIntents.ACTION_SIM_STATE_CHANGED); //for 8389 3G dongle
        filter.addAction(TelephonyIntents.ACTION_SIM_DETECTED); //ALPS01012597

        phone.getContext().registerReceiver(mIntentReceiver, filter);
        if (FeatureOption.MTK_GEMINI_SUPPORT) {
            mAutoGprsAttach = 0;
        }
        // Gsm doesn't support OTASP so its not needed
        phone.notifyOtaspChanged(OTASP_NOT_NEEDED);

        SystemProperties.set(TelephonyProperties.PROPERTY_ROAMING_INDICATOR_NEEDED, "false");
        SystemProperties.set(TelephonyProperties.PROPERTY_ROAMING_INDICATOR_NEEDED_2, "false");

        //MTK-START [MTK80515] [ALPS00368272]
        mCr.registerContentObserver(
                Settings.System.getUriFor(Settings.System.GPRS_CONNECTION_SIM_SETTING), true,
                mDataConnectionSimSettingObserver);
        if (mServiceStateExt.needEMMRRS()) {
            if (mSimId == getDataConnectionSimId()) {
                getEINFO(EVENT_ENABLE_EMMRRS_STATUS);
            } else {
                getEINFO(EVENT_DISABLE_EMMRRS_STATUS);
            }
        }
        //MTK-END [MTK80515] [ALPS00368272]
    }

    @Override
    public void dispose() {
        checkCorrectThread();
        log("ServiceStateTracker dispose");

        // Unregister for all events.
        mCi.unregisterForAvailable(this);
        mCi.unregisterForRadioStateChanged(this);
        mCi.unregisterForVoiceNetworkStateChanged(this);
        mCi.unregisterForSimPlugOut(this);

        if (mUiccApplcation != null) {mUiccApplcation.unregisterForReady(this);}

        if (mIccRecords != null) {mIccRecords.unregisterForRecordsLoaded(this);}
        mCi.unSetOnRestrictedStateChanged(this);
        mCi.unSetOnNITZTime(this);
        mCr.unregisterContentObserver(mAutoTimeObserver);
        mCr.unregisterContentObserver(mAutoTimeZoneObserver);
        if(mServiceStateExt.isImeiLocked())
            mCi.unregisterForIMEILock(this);

        if(FeatureOption.MTK_FEMTO_CELL_SUPPORT)
            mCi.unregisterForFemtoCellInfo(this);

        mCi.unregisterForIccRefresh(this);

        mPhone.getContext().unregisterReceiver(mIntentReceiver);
        super.dispose();
    }

    @Override
    protected void finalize() {
        if(DBG) log("finalize");
    }

    @Override
    protected Phone getPhone() {
        return mPhone;
    }

    @Override
    public void handleMessage (Message msg) {
        AsyncResult ar;
        int[] ints;
        String[] strings;
        Message message;
        int testMode = 0, attachType = 0;

        if (!mPhone.mIsTheCurrentActivePhone) {
            Rlog.e(LOG_TAG, "Received message " + msg +
                    "[" + msg.what + "] while being destroyed. Ignoring.");
            return;
        }
        switch (msg.what) {
            case EVENT_GET_SIM_RECOVERY_ON:
                break;
            case EVENT_SET_SIM_RECOVERY_ON:
                break;
            case EVENT_RADIO_AVAILABLE:
                //this is unnecessary
                //setPowerStateToDesired();
                break;

            case EVENT_SIM_READY:
                //ALPS00607052 add for AT&T [GSM-BTR-1-7942]
                if (!FeatureOption.MTK_RAT_BALANCING){
                    // Set the network type, in case the radio does not restore it.
                    mCi.setCurrentPreferredNetworkType();
                }

                //ALPS00279048 START
                // CRO setting always enable
                mPhone.setCRO(1, null);
                //ALPS00279048 END

                // ALPS00310187 START
                // HOO setting always enable
                mPhone.setCRO(3,null);
                // ALPS00310187 END

                // remark for MR1 Migration START
                // The SIM is now ready i.e if it was locked
                // it has been unlocked. At this stage, the radio is already
                // powered on.
                //if (mNeedToRegForSimLoaded) {
                //    mPhone.mIccRecords.get().registerForRecordsLoaded(this,
                //            EVENT_SIM_RECORDS_LOADED, null);
                //    mNeedToRegForSimLoaded = false;
                //}
                // remark for MR1 Migration END

                // restore the previous network selection.
                // [ALPS00224837], do not restore network selection, modem will decide selection mode
                //phone.restoreSavedNetworkSelection(null);

                // Set GPRS transfer type: 0:data prefer, 1:call prefer
                int transferType = Settings.System.getInt(mPhone.getContext().getContentResolver(),
                                                                                Settings.System.GPRS_TRANSFER_SETTING,
                                                                                Settings.System.GPRS_TRANSFER_SETTING_DEFAULT);
                mCi.setGprsTransferType(transferType, null);
                log("transferType:" + transferType);

                // In non-Gemini project, always set GPRS connection type to ALWAYS
                testMode = SystemProperties.getInt("gsm.gcf.testmode", 0);

                //Check UE is set to test mode or not
                log("testMode:" + testMode);
                Context context = mPhone.getContext();
                if (testMode == 0) {
                    if (mAutoGprsAttach == 1) {
                        attachType = SystemProperties.getInt("persist.radio.gprs.attach.type", 1);
                        log("attachType:" + attachType);
                        ////if(attachType == 1){
                            /* ALPS00300484 : Remove set gprs connection type here. it's too late */
                            ////  setGprsConnType(1);
                        ////}
                    } else if (mAutoGprsAttach == 2) {
                        if (FeatureOption.MTK_GEMINI_SUPPORT) {
                            //Disable for Gemini Enhancment by MTK03594
                            if(!FeatureOption.MTK_GEMINI_ENHANCEMENT){
                                Intent intent = new Intent(Intents.ACTION_GPRS_CONNECTION_TYPE_SELECT);
                                intent.putExtra(PhoneConstants.GEMINI_SIM_ID_KEY, mSimId);
                                context.sendStickyBroadcast(intent);
                                log("Broadcast: ACTION_GPRS_CONNECTION_TYPE_SELECT");
                            }
                            mAutoGprsAttach = 0;
                        }
                    }

                    if (FeatureOption.MTK_GEMINI_SUPPORT) {
                        ConnectivityManager connectivityManager = (ConnectivityManager)context.getSystemService(Context.CONNECTIVITY_SERVICE);
                        if (connectivityManager != null && connectivityManager.getMobileDataEnabledGemini(mSimId)) {
                            if (SystemProperties.getInt(DcTracker.PROPERTY_RIL_TEST_SIM[mSimId], 0) == 1) {
                                log("SIM" + (mSimId+1) + " is a test SIM and data is enabled on it, do PS attach");
                                setPsConnType(1);
                            } else {
                                log("SIM" + (mSimId+1) + " is not a test SIM");
                            }
                        }
                    }
                }
                pollState();
                // Signal strength polling stops when radio is off
                queueNextSignalStrengthPoll();

                //mPhone.getSimRecoveryOn(obtainMessage(EVENT_GET_SIM_RECOVERY_ON));
                break;

            case EVENT_RADIO_STATE_CHANGED:
                // This will do nothing in the radio not
                // available case

                //[ALPS01035028] -- start
                ar = (AsyncResult) msg.obj;
                if (ar.result != null){
                    mRadioState = (CommandsInterface.RadioState)(ar.result);
                    log("update mRadioState =" + mRadioState);
                }
                //[ALPS01035028] -- end

                setPowerStateToDesired();
                pollState();
                break;

            case EVENT_NETWORK_STATE_CHANGED:
                //ALPS00283717
                ar = (AsyncResult) msg.obj;
                onNetworkStateChangeResult(ar);
                pollState();
                break;

            case EVENT_PS_NETWORK_STATE_CHANGED:
                mIs3GTo2G = false;
                pollState();
                break;

            case EVENT_GET_SIGNAL_STRENGTH:
                // This callback is called when signal strength is polled
                // all by itself

                //ALPS01035028 - start 
                //if (!(mCi.getRadioState().isOn())) {
                if (!mRadioState.isOn()) {
                //ALPS01035028 - end
                    // Polling will continue when radio turns back on
                    return;
                }
                ar = (AsyncResult) msg.obj;

                //MTK-START [ALPS415367]For MR1 Migration
                ar = onGsmSignalStrengthResult(ar);
                //MTK-END [ALPS415367]For MR1 Migration

                onSignalStrengthResult(ar, true);
                queueNextSignalStrengthPoll();

                break;

            case EVENT_GET_LOC_DONE:
                ar = (AsyncResult) msg.obj;

                if (ar.exception == null) {
                    String states[] = (String[])ar.result;
                    int lac = -1;
                    int cid = -1;
                    if (states.length >= 3) {
                        try {
                            if (states[1] != null && states[1].length() > 0) {
                                lac = Integer.parseInt(states[1], 16);
                            }
                            if (states[2] != null && states[2].length() > 0) {
                                cid = Integer.parseInt(states[2], 16);
                            }
                        } catch (NumberFormatException ex) {
                            Rlog.w(LOG_TAG, "error parsing location: " + ex);
                        }
                    }
                    mCellLoc.setLacAndCid(lac, cid);
                    mPhone.notifyLocationChanged();
                }

                // Release any temporary cell lock, which could have been
                // acquired to allow a single-shot location update.
                disableSingleLocationUpdate();
                break;

            case EVENT_UPDATE_SELECTION_MODE:
                ar = (AsyncResult) msg.obj;
                if (ar.exception == null) {
                    ints = (int[])ar.result;
                    if(ints[0] == 1)
                    {
                        /* ALPS00316998 */
                        log("Start manual selection mode reminder service");
                        Intent sIntent = new Intent();
                        sIntent.setClassName("com.android.phone","com.mediatek.settings.NoNetworkPopUpService");
                        mPhone.getContext().startService(sIntent);
                    }
                }
                break;

            case EVENT_POLL_STATE_REGISTRATION:
            case EVENT_POLL_STATE_GPRS:
            case EVENT_POLL_STATE_OPERATOR:
            case EVENT_POLL_STATE_NETWORK_SELECTION_MODE:
                ar = (AsyncResult) msg.obj;

                handlePollStateResult(msg.what, ar);
                break;

            case EVENT_POLL_SIGNAL_STRENGTH:
                // Just poll signal strength...not part of pollState()

                mCi.getSignalStrength(obtainMessage(EVENT_GET_SIGNAL_STRENGTH));
                break;

            case EVENT_NITZ_TIME:
                ar = (AsyncResult) msg.obj;

                String nitzString = (String)((Object[])ar.result)[0];
                long nitzReceiveTime = ((Long)((Object[])ar.result)[1]).longValue();

                setTimeFromNITZString(nitzString, nitzReceiveTime);
                break;

            case EVENT_SIGNAL_STRENGTH_UPDATE:
                // This is a notification from
                // CommandsInterface.setOnSignalStrengthUpdate

                ar = (AsyncResult) msg.obj;
                ar = onGsmSignalStrengthResult(ar);
                onSignalStrengthResult(ar, true);

                // [ALPS00127981]
                // If rssi=99, poll again
                if (ar.result == null) {
                    if (mDontPollSignalStrength == true) {
                        mDontPollSignalStrength = false;
                        queueNextSignalStrengthPoll();
                    }
                } else {
                mDontPollSignalStrength = true;
                }
                break;

            case EVENT_SIM_RECORDS_LOADED:
                //MTK-START: For KK Migration
                log("EVENT_SIM_RECORDS_LOADED: what=" + msg.what);
                updatePhoneObject();
                //MTK-END: For KK Migration
                
                // MVNO-API
                    log("MTK_MVNO_SUPPORT refreshSpnDisplay()");
                    // pollState() result may be faster than load EF complete, so update ss.alphaLongShortName
                    refreshSpnDisplay();

                String newImsi = mPhone.getSubscriberId();
                boolean bImsiChanged = false;
                String imsiSetting = "gsm.sim.imsi";

                if (mSimId == PhoneConstants.GEMINI_SIM_2) { 
                    imsiSetting = "gsm.sim.imsi.2";
                }else if (mSimId == PhoneConstants.GEMINI_SIM_3) { 
                    imsiSetting = "gsm.sim.imsi.3";                    
                }else if (mSimId == PhoneConstants.GEMINI_SIM_4) { 
                    imsiSetting = "gsm.sim.imsi.4";                    
                    }

                String oldImsi = Settings.System.getString(mPhone.getContext().getContentResolver(), imsiSetting);
                if(oldImsi == null || !oldImsi.equals(newImsi)) {	  
                    Rlog.d(LOG_TAG, "GSST: Sim"+ (mSimId+1) + " Card changed  lastImsi is " + oldImsi + " newImsi is " + newImsi); 
                    bImsiChanged = true;
                    Settings.System.putString(mPhone.getContext().getContentResolver(), imsiSetting, newImsi);
                }

		        // if(bImsiChanged && (ss.getState() != ServiceState.STATE_IN_SERVICE) 	&& ss.getIsManualSelection()) {
                if(bImsiChanged && mSS.getIsManualSelection()) {
                    Rlog.d(LOG_TAG, "GSST: service state is out of service with manual network selection mode,  setNetworkSelectionModeAutomatic " );
                    mPhone.setNetworkSelectionModeAutomatic(obtainMessage(EVENT_SET_AUTO_SELECT_NETWORK_DONE));
                }
                break;

            case EVENT_LOCATION_UPDATES_ENABLED:
                ar = (AsyncResult) msg.obj;

                if (ar.exception == null) {
                    mCi.getVoiceRegistrationState(obtainMessage(EVENT_GET_LOC_DONE, null));
                }
                break;

            case EVENT_SET_PREFERRED_NETWORK_TYPE:
                ar = (AsyncResult) msg.obj;
                // Don't care the result, only use for dereg network (COPS=2)
                message = obtainMessage(EVENT_RESET_PREFERRED_NETWORK_TYPE, ar.userObj);
                mCi.setPreferredNetworkType(mPreferredNetworkType, message);
                break;

            case EVENT_RESET_PREFERRED_NETWORK_TYPE:
                ar = (AsyncResult) msg.obj;
                if (ar.userObj != null) {
                    AsyncResult.forMessage(((Message) ar.userObj)).exception
                            = ar.exception;
                    ((Message) ar.userObj).sendToTarget();
                }
                break;

            case EVENT_GET_PREFERRED_NETWORK_TYPE:
                ar = (AsyncResult) msg.obj;

                if (ar.exception == null) {
                    mPreferredNetworkType = ((int[])ar.result)[0];
                } else {
                    mPreferredNetworkType = RILConstants.NETWORK_MODE_GLOBAL;
                }

                message = obtainMessage(EVENT_SET_PREFERRED_NETWORK_TYPE, ar.userObj);
                int toggledNetworkType = RILConstants.NETWORK_MODE_GLOBAL;

                mCi.setPreferredNetworkType(toggledNetworkType, message);
                break;

            case EVENT_CHECK_REPORT_GPRS:
                if (mSS != null && !isGprsConsistent(gprsState, mSS.getVoiceRegState())) {

                    // Can't register data service while voice service is ok
                    // i.e. CREG is ok while CGREG is not
                    // possible a network or baseband side error
                    GsmCellLocation loc = ((GsmCellLocation)mPhone.getCellLocation());
                    EventLog.writeEvent(EventLogTags.DATA_NETWORK_REGISTRATION_FAIL,
                            mSS.getOperatorNumeric(), loc != null ? loc.getCid() : -1);
                    mReportedGprsNoReg = true;
                }
                mStartedGprsRegCheck = false;
                break;

            case EVENT_RESTRICTED_STATE_CHANGED:
                // This is a notification from
                // CommandsInterface.setOnRestrictedStateChanged

                if (DBG) log("EVENT_RESTRICTED_STATE_CHANGED");

                ar = (AsyncResult) msg.obj;

                onRestrictedStateChanged(ar);
                break;
            case EVENT_SET_AUTO_SELECT_NETWORK_DONE:
                log("GSST EVENT_SET_AUTO_SELECT_NETWORK_DONE");
                break;
            case EVENT_SET_GPRS_CONN_TYPE_DONE:
                Rlog.d(LOG_TAG, "GSST EVENT_SET_GPRS_CONN_TYPE_DONE");
                ar = (AsyncResult) msg.obj;
                // M: For single SIM, turn on radio after set connection type done.
                if (!FeatureOption.MTK_GEMINI_SUPPORT) {
                    setPowerStateToDesired();
                }
                if(ar.exception != null){
                    //ALPS01034796: if not our modem, don't retry endless for this cmd
                    if (!FeatureOption.MTK_3GDONGLE_SUPPORT){
                        sendMessageDelayed(obtainMessage(EVENT_SET_GPRS_CONN_RETRY, null), DEFAULT_GPRS_RETRY_PERIOD_MILLIS);
                    }                               
                }
                break;
            case EVENT_SET_GPRS_CONN_RETRY:
                Rlog.d(LOG_TAG, "EVENT_SET_GPRS_CONN_RETRY");
                ServiceState ss = mPhone.getServiceState();
                if (ss == null) break;
                Rlog.d(LOG_TAG, "GSST EVENT_SET_GPRS_CONN_RETRY ServiceState " + mSS.getVoiceRegState());
                if (mSS.getState() == ServiceState.STATE_POWER_OFF) {
                    break;
                }
                int airplanMode = Settings.Global.getInt(mPhone.getContext().getContentResolver(), Settings.Global.AIRPLANE_MODE_ON, 0);
                Rlog.d(LOG_TAG, "GSST EVENT_SET_GPRS_CONN_RETRY airplanMode " + airplanMode);
                if (airplanMode > 0) {
                    break;
                }
                setPsConnType(gprsConnType);
                break;
            case EVENT_DATA_CONNECTION_DETACHED:
                Rlog.d(LOG_TAG, "EVENT_DATA_CONNECTION_DETACHED: set gprsState=STATE_OUT_OF_SERVICE");
                gprsState = ServiceState.STATE_OUT_OF_SERVICE;
                ps_networkType = DATA_ACCESS_UNKNOWN;
				
                if (mSimId == PhoneConstants.GEMINI_SIM_1) {
                    mPhone.setSystemProperty(TelephonyProperties.PROPERTY_DATA_NETWORK_TYPE, networkTypeToString(ps_networkType));
                } else if (mSimId == PhoneConstants.GEMINI_SIM_2){
                    mPhone.setSystemProperty(TelephonyProperties.PROPERTY_DATA_NETWORK_TYPE_2, networkTypeToString(ps_networkType));
                } else if (mSimId == PhoneConstants.GEMINI_SIM_3){
                    mPhone.setSystemProperty(TelephonyProperties.PROPERTY_DATA_NETWORK_TYPE_3, networkTypeToString(ps_networkType));
                } else if (mSimId == PhoneConstants.GEMINI_SIM_4){
                    mPhone.setSystemProperty(TelephonyProperties.PROPERTY_DATA_NETWORK_TYPE_4, networkTypeToString(ps_networkType));
                }
                mDetachedRegistrants.notifyRegistrants();
                break;
            case EVENT_INVALID_SIM_INFO: //ALPS00248788
                if (DBG) log("EVENT_INVALID_SIM_INFO");
                ar = (AsyncResult) msg.obj;
                onInvalidSimInfoReceived(ar);
                break;
            case EVENT_IMEI_LOCK: //ALPS00296298
                if (DBG) log("EVENT_IMEI_LOCK");
                mIsImeiLock = true;
                break;
            case EVENT_ENABLE_EMMRRS_STATUS:
                ar= (AsyncResult) msg.obj;
                log("EVENT_ENABLE_EMMRRS_STATUS start");
                if (ar.exception == null) {
                    String data[] = (String [])ar.result;
                    log("EVENT_ENABLE_EMMRRS_STATUS, data[0] is : " + data[0]);
                    log("EVENT_ENABLE_EMMRRS_STATUS, einfo value is : " + data[0].substring(8));
                    int oldValue = Integer.valueOf(data[0].substring(8));
                    int value = oldValue | 0x80;
                    log("EVENT_ENABLE_EMMRRS_STATUS, einfo value change is : " + value);
                    if (oldValue != value) {
                        setEINFO(value, null);
                    }
                }
                log("EVENT_ENABLE_EMMRRS_STATUS end");
                break;
            case EVENT_DISABLE_EMMRRS_STATUS:
                ar= (AsyncResult) msg.obj;
                log("EVENT_DISABLE_EMMRRS_STATUS start");
                if (ar.exception == null) {
                    String data[] = (String [])ar.result;
                    log("EVENT_DISABLE_EMMRRS_STATUS, data[0] is : " + data[0]);
                    log("EVENT_DISABLE_EMMRRS_STATUS, einfo value is : " + data[0].substring(8));

                    try{					
                        int oldValue = Integer.valueOf(data[0].substring(8));
                        int value = oldValue & 0xff7f;
                        log("EVENT_DISABLE_EMMRRS_STATUS, einfo value change is : " + value);
                        if (oldValue != value) {
                            setEINFO(value, null);
                        }
                    } catch (NumberFormatException ex) {
                        loge("Unexpected einfo value : " + ex);
                    }						
                }
                log("EVENT_DISABLE_EMMRRS_STATUS end");
                break;
            case EVENT_SIM_PLUG_OUT: //ALPS00296298
                if (DBG) log("set explict_update_spn due to EVENT_SIM_PLUG_OUT");
                explict_update_spn = 1;
                break;			

            case EVENT_ICC_REFRESH:
                ar = (AsyncResult)msg.obj;
                if (ar.exception == null) {
                    IccRefreshResponse res = ((IccRefreshResponse)ar.result);
                    if(res.refreshResult == 6){
                        /* ALPS00949490 */
                        mLastRegisteredPLMN = null;
                        mLastPSRegisteredPLMN = null;
                        log("Reset mLastRegisteredPLMN and mLastPSRegisteredPLMN");
                    }						
                }				
                break;	

            case EVENT_FEMTO_CELL_INFO:
                ar = (AsyncResult) msg.obj;
                onFemtoCellInfoResult(ar);
                break;
				
            default:
                super.handleMessage(msg);
            break;
        }
    }

    @Override
    protected void updateCellInfoRate(){
        log("updateCellInfoRate(),mCellInfoRate= "+mCellInfoRate);   
		if((mCellInfoRate != Integer.MAX_VALUE) &&(mCellInfoRate != 0)) {

			if(mCellInfoTimer!= null){
                log("cancel previous timer if any");   						  				
                mCellInfoTimer.cancel();	
                mCellInfoTimer = null;				
			}
				 
            mCellInfoTimer = new Timer(true);
			
			log("schedule timer with period = "+mCellInfoRate+" ms");   			
            mCellInfoTimer.schedule(new timerTask(),mCellInfoRate);
		}else if((mCellInfoRate == 0) || (mCellInfoRate == Integer.MAX_VALUE)){
			if(mCellInfoTimer!= null){		
                log("cancel cell info timer if any");   						
                mCellInfoTimer.cancel();
                mCellInfoTimer = null;
			}
        }		
    }

    public class timerTask extends TimerTask {
        public void run(){ 
            log("CellInfo Timeout invoke getAllCellInfoByRate()");   
            if((mCellInfoRate != Integer.MAX_VALUE) &&(mCellInfoRate != 0) && (mCellInfoTimer!= null)) {
                log("timerTask schedule timer with period = "+mCellInfoRate+" ms");   
                mCellInfoTimer.schedule(new timerTask(),mCellInfoRate);
            }

            new Thread(new Runnable() {
                public void run() {
                    log("timerTask invoke getAllCellInfoByRate() in another thread");   					
                    getAllCellInfoByRate();		
                }
            }).start();

		}
    };

    @Override
    protected void setPowerStateToDesired() {
        log("setPowerStateToDesired mDesiredPowerState:" + mDesiredPowerState +
                //ALPS01035028 - start
                //" current radio state:" + mCi.getRadioState() +
                " current radio state:" + mRadioState+
                //ALPS01035028 - end
    			" mFirstRadioChange:" + mFirstRadioChange);
        // If we want it on and it's off, turn it on
        //ALPS01035028 - start
        //if (mDesiredPowerState
        //    && mCi.getRadioState() == CommandsInterface.RadioState.RADIO_OFF) {
        if (mDesiredPowerState
            && mRadioState == CommandsInterface.RadioState.RADIO_OFF) {
        //ALPS01035028 - end
            if (!FeatureOption.MTK_GEMINI_SUPPORT) {
                /* ALPS00439250 START : For single SIM project ,AP might NOT set the DUAL_SIM_MODE_SETTING
                                 However, for consistent UI, we will check the setting. So make sure to set it in framework */
                Settings.System.putInt(mPhone.getContext().getContentResolver(), 
                                       Settings.System.DUAL_SIM_MODE_SETTING, 
                                       GeminiNetworkSubUtil.MODE_SIM1_ONLY);
                /* ALPS00439250 END */

                if (!mIsTurnOnRadio) {
                    setPsConnType(2);
                    mIsTurnOnRadio = true;
                } else {
                    mIsTurnOnRadio = false;
                    // M: Turn on radio after set connetion type done.
                    mCi.setRadioPower(true, null);
                    /* ALPS00316998 */
                    log("check manual selection mode when setPowerStateToDesired and set dual_sim_mode_setting to 1");
                    mCi.getNetworkSelectionMode(obtainMessage(EVENT_UPDATE_SELECTION_MODE));
                }
            }
        //ALPS01035028 - start
        //} else if (!mDesiredPowerState && mCi.getRadioState().isOn()) {
        } else if (!mDesiredPowerState && mRadioState.isOn()) {
        //ALPS01035028 - end
            /* ALPS00439250 START : For single SIM project ,AP might NOT set the DUAL_SIM_MODE_SETTING
                          However, for consistent UI, we will check the setting. So make sure to set it in framework */
            if (!FeatureOption.MTK_GEMINI_SUPPORT) {
                int airplanMode = Settings.Global.getInt(mPhone.getContext().getContentResolver(), Settings.Global.AIRPLANE_MODE_ON, 0);
                if(airplanMode == 0){				
                    Settings.System.putInt(mPhone.getContext().getContentResolver(), 
                                           Settings.System.DUAL_SIM_MODE_SETTING, 
                                           GeminiNetworkSubUtil.MODE_FLIGHT_MODE);
                    log("Set dual_sim_mode_setting to 0");				
                }        
            }        
            /* ALPS00439250 END */			
			
            // If it's on and available and we want it off gracefully
            DcTrackerBase dcTracker = mPhone.mDcTracker;
            powerOffRadioSafely(dcTracker);
        //ALPS01035028 - start
        //} else if (!mDesiredPowerState && !mCi.getRadioState().isOn() && mFirstRadioChange) { //mtk added
        } else if (!mDesiredPowerState && !mRadioState.isOn() && mFirstRadioChange) { //mtk added
        //ALPS01035028 - end
        	// For boot up in Airplane mode, we would like to startup modem in cfun_state=4
            if (!FeatureOption.MTK_GEMINI_SUPPORT) {
                //MTK-START [ALPS00644650] force notify service state to reomve airplane icon
                log("force notify service state for UI consistent");
                mPhone.notifyServiceStateChanged(mSS);
                //MTK-END [ALPS00644650]
                mCi.setRadioPower(false, null);
            }
        }// Otherwise, we're in the desired state

        if (mFirstRadioChange) {
            //ALPS01035028 - start
            //if (mCi.getRadioState() == CommandsInterface.RadioState.RADIO_UNAVAILABLE) {
            if (mRadioState == CommandsInterface.RadioState.RADIO_UNAVAILABLE) {                
            //ALPS01035028 - start
                log("First radio changed but radio unavailable, not to set first radio change off");
            } else {
                log("First radio changed and radio available, set first radio change off");
                mFirstRadioChange = false;
            }
        }

        // M: remove set GPRS connection type retry when radio off.
        if (!mDesiredPowerState) {
            removeGprsConnTypeRetry();
        }
    }

    @Override
    protected void hangupAndPowerOff() {
        // hang up all active voice calls
        if (mPhone.isInCall()) {
            log("Hangup call ...");
            mPhone.mCT.mRingingCall.hangupIfAlive();
            mPhone.mCT.mBackgroundCall.hangupIfAlive();
            mPhone.mCT.mForegroundCall.hangupIfAlive();
        }

        if (!FeatureOption.MTK_GEMINI_SUPPORT) {
            mCi.setRadioPower(false, null);
        }
    }

    /**
     * Handle the result of one of the pollState()-related requests
     */
    @Override
        protected void handlePollStateResult (int what, AsyncResult ar) {
        int ints[];
        String states[];

        //MTK-ADD Start : for CS not registered , PS regsitered (ex: LTE PS only mode or 2/3G PS only SIM card or CS domain network registeration temporary failure
        /* update  mNewCellLoc when CS is not registered but PS is registered */
        int psLac = -1;
        int psCid = -1;
        //MTK-ADD END: for CS not registered , PS regsitered (ex: LTE PS only mode or 2/3G PS only SIM card or CS domain network registeration temporary failure

        // Ignore stale requests from last poll
        if (ar.userObj != mPollingContext) return;

        if (ar.exception != null) {
            CommandException.Error err=null;

            if (ar.exception instanceof CommandException) {
                err = ((CommandException)(ar.exception)).getCommandError();
            }

            if (err == CommandException.Error.RADIO_NOT_AVAILABLE) {
                // Radio has crashed or turned off
                cancelPollState();
                return;
            }
            //ALPS01035028 - start
            //if (!mCi.getRadioState().isOn()) {
            if (!mRadioState.isOn()) {
            //ALPS01035028 - end
                // Radio has crashed or turned off
                cancelPollState();
                return;
            }

            if (err != CommandException.Error.OP_NOT_ALLOWED_BEFORE_REG_NW &&
                    err != CommandException.Error.OP_NOT_ALLOWED_BEFORE_REG_NW) {
                log("RIL implementation has returned an error where it must succeed" + ar.exception);
            }
        } else try {
            switch (what) {
                case EVENT_POLL_STATE_REGISTRATION:
                    states = (String[])ar.result;
                    int lac = -1;
                    int cid = -1;
                    int type = ServiceState.RIL_RADIO_TECHNOLOGY_UNKNOWN;
                    int regState = ServiceState.RIL_REG_STATE_UNKNOWN;
                    int reasonRegStateDenied = -1;
                    int psc = -1;
                    if (states.length > 0) {
                        try {
                            regState = Integer.parseInt(states[0]);
                            if (states.length >= 3) {
                                if (states[1] != null && states[1].length() > 0) {
                                    //[ALPS00907900]-START
                                    int tempLac = Integer.parseInt(states[1], 16);
                                    if (tempLac < 0){
                                        log("set Lac to previous value");
                                        tempLac = mCellLoc.getLac();
                                    }
                                    lac = tempLac;
                                    //[ALPS00907900]-END
                                }
                                if (states[2] != null && states[2].length() > 0) {
                                    //[ALPS00907900]-START
                                    int tempCid = Integer.parseInt(states[2], 16);
                                    if (tempCid < 0){
                                        log("set Cid to previous value");
                                        tempCid = mCellLoc.getCid();
                                    }
                                    cid = tempCid;
                                    //[ALPS00907900]-END
                                }
                                if (states.length >=4 && states[3] != null && states[3].length() > 0) {
                                    //[ALPS01132085] for NetworkType display abnormal
                                    //update network type when screen is on or screen is off but not registered
                                    if (mIsScreenOn || (!mIsScreenOn &&((regState!=1)||(regState!=5)))){
                                        type = Integer.parseInt(states[3]);
                                        mNewSS.setRilVoiceRadioTechnology(type);
                                    }
                                }
                            }
                            log("EVENT_POLL_STATE_REGISTRATION mSS getRilVoiceRadioTechnology:" + mSS.getRilVoiceRadioTechnology() +
                                    ", regState:" + regState +
                                    ", NewSS RilVoiceRadioTechnology:" + mNewSS.getRilVoiceRadioTechnology() +
                                    ", lac:" + lac +
                                    ", cid:" + cid);
                        } catch (NumberFormatException ex) {
                            loge("error parsing RegistrationState: " + ex);
                        }
                    }

                    mGsmRoaming = regCodeIsRoaming(regState);
                    mNewSS.setState (regCodeToServiceState(regState));
                    mNewSS.setRegState(regState);

                    /*
                    // [ALPS00225065] For Gemini special handle,
                    // When SIM blocked, treat as out of service
                    if (FeatureOption.MTK_GEMINI_SUPPORT) {
                        if (mCi.getRadioState() == CommandsInterface.RadioState.SIM_LOCKED_OR_ABSENT) {
                            newSS.setState(ServiceState.STATE_OUT_OF_SERVICE);
                        }
                    }
                    */
                    if (FeatureOption.MTK_GEMINI_SUPPORT) {
                        if (IccCardConstants.INTENT_VALUE_ICC_LOCKED.equals(mSimState)||
                            IccCardConstants.INTENT_VALUE_ICC_ABSENT.equals(mSimState)) {
                            log("SIM state is lock or absent, treat as OUT_OF_SERVICE");
                            mNewSS.setState(ServiceState.STATE_OUT_OF_SERVICE);
                            //[ALPS01010930] to consistent RegState and ServcieState 
                            mNewSS.setRegState(ServiceState.REGISTRATION_STATE_NOT_REGISTERED_AND_NOT_SEARCHING);
                        }
                    }
//ALPS00283717: distiguish limited service and no service
/*
                    if (regState == 10 || regState == 12 || regState == 13 || regState == 14) {
                        mEmergencyOnly = true;
                    } else {
                        mEmergencyOnly = false;
                    }
*/
                    // LAC and CID are -1 if not avail. LAC and CID will be updated in onNetworkStateChangeResult() when in OUT_SERVICE
                    if (states.length > 3) {
                    	log("states.length > 3");

                        /* ALPS00291583: ignore unknown lac or cid value */
                        if(lac==0xfffe || cid==0x0fffffff)
                        {
                            log("unknown lac:"+lac+"or cid:"+cid);
                        }
                        else
                        {
                            /* AT+CREG? result won't include <lac> and <cid> when  in OUT_SERVICE */
                            if(regCodeToServiceState(regState) != ServiceState.STATE_OUT_OF_SERVICE){                        
                                mNewCellLoc.setLacAndCid(lac, cid);
                            }
                        }
                    	//if (mSimId == PhoneConstants.GEMINI_SIM_1) {
                    	//	SystemProperties.set(TelephonyProperties.PROPERTY_CS_NETWORK_TYPE, Integer.toString(mNewRadioTechnology));
                    	//	log("PROPERTY_CS_NETWORK_TYPE" + SystemProperties.get(TelephonyProperties.PROPERTY_CS_NETWORK_TYPE));
                    	//} else {
                     //	SystemProperties.set(TelephonyProperties.PROPERTY_CS_NETWORK_TYPE_2, Integer.toString(mNewRadioTechnology));
                    	//	log("PROPERTY_CS_NETWORK_TYPE_2" + SystemProperties.get(TelephonyProperties.PROPERTY_CS_NETWORK_TYPE_2));
                    	//}
                    }
                    mNewCellLoc.setPsc(psc);
                break;

                case EVENT_POLL_STATE_GPRS:
                    states = (String[])ar.result;

                    regState = -1;
                    mNewReasonDataDenied = -1;
                    mNewMaxDataCalls = 1;
                    if (states.length > 0) {
                        try {
                            regState = Integer.parseInt(states[0]);

                            //MTK-ADD Start : for CS not registered , PS regsitered (ex: LTE PS only mode or 2/3G PS only SIM card or CS domain network registeration temporary failure
                            if (states.length >= 3) {
                                if (states[1] != null && states[1].length() > 0) {
                                    int tempLac = Integer.parseInt(states[1], 16);
                                    if (tempLac < 0){
                                        log("set Lac to previous value");
                                        tempLac = mCellLoc.getLac();
                                    }
                                    psLac = tempLac;
                                }
                                if (states[2] != null && states[2].length() > 0) {
                                    int tempCid = Integer.parseInt(states[2], 16);
                                    if (tempCid < 0){
                                        log("set Cid to previous value");
                                        tempCid = mCellLoc.getCid();
                                    }
                                    psCid = tempCid;
                                }
                            }    
                            //MTK-ADD END : for CS not registered , PS regsitered (ex: LTE PS only mode or 2/3G PS only SIM card or CS domain network registeration temporary failure

                            // states[3] (if present) is the current radio technology
                            if (states.length >= 4 && states[3] != null) {
                                newps_networkType = Integer.parseInt(states[3]);
                            }
                            if (states.length >= 5 && states[4] != null) {
                                log("<cell_data_speed_support> " + states[4]);
                            }
                            if (states.length >= 6 && states[5] != null) {
                                log("<max_data_bearer_capability> " + states[5]);
                            }
                            if ((states.length >= 7 ) && (regState == 3)) {
                                mNewReasonDataDenied = Integer.parseInt(states[6]);
                            }
                            if (states.length >= 8) {
                                mNewMaxDataCalls = Integer.parseInt(states[7]);
                            }
                        } catch (NumberFormatException ex) {
                            loge("error parsing GprsRegistrationState: " + ex);
                        }
                    }
                    newGPRSState = regCodeToServiceState(regState);

                    //for MR2 update Data Registration state
                    mNewSS.setDataRegState(newGPRSState);

                    mDataRoaming = regCodeIsRoaming(regState);
                    //mNewRilRadioTechnology = newps_networkType;                    
                    mNewSS.setRilDataRadioTechnology(newps_networkType);
                    
                break;

                case EVENT_POLL_STATE_OPERATOR:
                    String opNames[] = (String[])ar.result;

                    if (opNames != null && opNames.length >= 3) {
                        log("long:" +opNames[0] + " short:" + opNames[1] + " numeric:" + opNames[2]);
                        mNewSS.setOperatorName (opNames[0], opNames[1], opNames[2]);
                    }
                break;

                case EVENT_POLL_STATE_NETWORK_SELECTION_MODE:
                    ints = (int[])ar.result;
                    mNewSS.setIsManualSelection(ints[0] == 1);
                    if((mSS.getIsManualSelection() == true) && (mNewSS.getIsManualSelection() == false)){
                        log("Selection mode change from manual to auto");

                        if (FeatureOption.MTK_GEMINI_SUPPORT) {
                            if (mPhone instanceof GSMPhone){
                                boolean allPhoneInAutoMode = true;
                                for(int simIdx=PhoneConstants.GEMINI_SIM_1;simIdx<PhoneConstants.GEMINI_SIM_NUM;simIdx++){                     
                                    GSMPhone peerPhone = ((GSMPhone)mPhone).getPeerPhones(simIdx);
                                    if (peerPhone != null){
                                        if(peerPhone.getServiceState().getIsManualSelection()== true){
                                            log("Phone"+ (simIdx+1)+" is NOT in manual selection mode,shell keep reminder service");										   
                                            allPhoneInAutoMode = false;											   
                                            break;                                       
                                        }
                                    }
                                }		
                                if(allPhoneInAutoMode == true){
                                    log("All sim are NOT in manual selection mode,stop reminder service");
                                    Intent sIntent = new Intent();     
                                    sIntent.setClassName("com.android.phone","com.mediatek.settings.NoNetworkPopUpService"); 
                                    mPhone.getContext().stopService(sIntent);		
                                }                                
                            }
                        }
                        else{
                            log("Stop manual selection mode reminder service");
                            Intent sIntent = new Intent();
                            sIntent.setClassName("com.android.phone","com.mediatek.settings.NoNetworkPopUpService");
                            mPhone.getContext().stopService(sIntent);
                        }
                    }
                    else if((mSS.getIsManualSelection() == false) && (mNewSS.getIsManualSelection() == true)){
                        log("Selection mode change from auto to manual");
                        Intent sIntent = new Intent();
                        sIntent.setClassName("com.android.phone","com.mediatek.settings.NoNetworkPopUpService");
                        mPhone.getContext().startService(sIntent);
                    }
                break;
            }

        } catch (RuntimeException ex) {
            Rlog.e(LOG_TAG, "Exception while polling service state. "
                            + "Probably malformed RIL response.", ex);
        }

        mPollingContext[0]--;

        if (mPollingContext[0] == 0) {
            /**
             * [ALPS00006527]
             * Only when CS in service, treat PS as in service
             */
            if (mNewSS.getState() != ServiceState.STATE_IN_SERVICE) {
                //MTK-ADD START : for CS not registered , PS regsitered (ex: LTE PS only mode or 2/3G PS only SIM card or CS domain network registeration temporary failure
                if (mNewSS.getDataRegState() != ServiceState.STATE_IN_SERVICE) {

                    log("CS and PS are not registered");
                    /* Integrate ALPS00286197 with MR2 data only device state update */
                    if(mVoiceCapable){
                        newGPRSState = regCodeToServiceState(0);
                        log("For Data only device newGPRSState=" +newGPRSState);
                    }
                    mDataRoaming = regCodeIsRoaming(0);
                } else {
                    //when CS not registered, we update cellLoc by +CGREG
                    log("update cellLoc by +CGREG");
                    mNewCellLoc.setLacAndCid(psLac, psCid);
                }
                //MTK-ADD END : for CS not registered , PS regsitered (ex: LTE PS only mode or 2/3G PS only SIM card or CS domain network registeration temporary failure
            }

            /**
             * Since the roaming state of gsm service (from +CREG) and
             * data service (from +CGREG) could be different, the new SS
             * is set to roaming when either is true.
             *
             * There are exceptions for the above rule.
             * The new SS is not set as roaming while gsm service reports
             * roaming but indeed it is same operator.
             * And the operator is considered non roaming.
             *
             * The test for the operators is to handle special roaming
             * agreements and MVNO's.
             */

            //BEGIN mtk03923[20120206][ALPS00117799][ALPS00230295]
            //Only check roaming indication from CREG (CS domain)
            //boolean roaming = (mGsmRoaming || mDataRoaming);
            boolean roaming = mGsmRoaming;
            //END   mtk03923[20120206][ALPS00117799][ALPS00230295]
            // [ALPS00220720] remove this particular check.
            // Still display roaming even in the same operator

            /*
            //MTK-START: For KK Migration
            if ((mGsmRoaming && isSameNamedOperators(mNewSS)
                        && !isSameNamedOperatorConsideredRoaming(mNewSS))
                    || isOperatorConsideredNonRoaming(mNewSS)) {
                roaming = false;
            }
            //MTK-ENDT: For KK Migration            
            */

            mNewSS.setRoaming(roaming);
            mNewSS.setEmergencyOnly(mEmergencyOnly);
            pollStateDone();
        }
    }

    private void setSignalStrengthDefaultValues() {
        mSignalStrength = new SignalStrength(mSimId, 99, -1, -1, -1, -1, -1, -1, 99,
                                             SignalStrength.INVALID,
                                             SignalStrength.INVALID,
                                             SignalStrength.INVALID,
                                             SignalStrength.INVALID,
                                             true, 0, 0, 0);
    }

    /**
     * A complete "service state" from our perspective is
     * composed of a handful of separate requests to the radio.
     *
     * We make all of these requests at once, but then abandon them
     * and start over again if the radio notifies us that some
     * event has changed
     */
    private void pollState() {
        mPollingContext = new int[1];
        mPollingContext[0] = 0;
        //ALPS01035028 - start
        //log("mCi.getRadioState() is " + mCi.getRadioState());
        log("pollState mRadioState is " + mRadioState);
        //ALPS01035028 - end

        //ALPS00267573
        if(dontUpdateNetworkStateFlag == true)
        {
            log("pollState is ignored!!");
            return;
        }		
        //ALPS01035028 - start
        //CommandsInterface.RadioState radioState = mCi.getRadioState();
        CommandsInterface.RadioState radioState = mRadioState;
        //ALPS01035028 - start
        if (radioState == CommandsInterface.RadioState.SIM_LOCKED_OR_ABSENT) {
            //Since when there is no SIM inserted, the radio state is set to locked or absent
            //In this case, the service state will be incorrect if the radio is turned off
            //So we use airplane mode and dualSimMode to deside if the radio state is radio off
            int airplaneMode = Settings.Global.getInt(
                    mPhone.getContext().getContentResolver(),
                    Settings.Global.AIRPLANE_MODE_ON, 0);
            int dualSimModeSetting = Settings.System.getInt(
                    mPhone.getContext().getContentResolver(),
                    Settings.System.DUAL_SIM_MODE_SETTING, GeminiNetworkSubUtil.MODE_DUAL_SIM);

            /* ALPS00439915: to prevent shutdown thread can't get RADIO_OFF state */
            int isPowerOff = 0;
            if(!PhoneFactory.isDualTalkMode()){
                /* ALPS00438909 BSP package don't support IPO */
                isPowerOff = SystemProperties.getInt("ril.ipo.radiooff", 0);
            }else{
                /* ALPS00462176 */            
                if(mSimId == PhoneConstants.GEMINI_SIM_2){            
                    isPowerOff = SystemProperties.getInt("ril.ipo.radiooff.2", 0);
                    log("Dualtalk SIM2 isPowerOff="+isPowerOff);
                }else{
                    isPowerOff = SystemProperties.getInt("ril.ipo.radiooff", 0);
                }				
            }		 		

            log("Now airplaneMode="+airplaneMode+",dualSimModeSetting="+dualSimModeSetting+",isPowerOff="+isPowerOff);			

            if ((airplaneMode == 1) || (isPowerOff ==1)) {
                radioState = CommandsInterface.RadioState.RADIO_OFF;
            } else {
                //MTK-START: for LEGO move GSMPhone.isSimInsert() API
                boolean hasSIMInserted = false;
                boolean hasPeerSIMInserted = false;
                
                Phone currentPhone;

                Phone peerGsmPhone = mPhone.getPeerPhone();
                Phone currentPeerPhone;
                
                if (FeatureOption.MTK_GEMINI_SUPPORT && peerGsmPhone != null) {
                    currentPeerPhone = ((GeminiPhone)(PhoneFactory.getDefaultPhone())).getPhonebyId(peerGsmPhone.getMySimId());
                    hasPeerSIMInserted = currentPeerPhone.getIccCard().hasIccCard();
                    //boolean hasPeerSIMInserted = FeatureOption.MTK_GEMINI_SUPPORT && peerPhone != null ? peerPhone.isSimInsert() : false;
                }

                if (FeatureOption.MTK_GEMINI_SUPPORT) {
                    currentPhone = ((GeminiPhone)(PhoneFactory.getDefaultPhone())).getPhonebyId(mSimId);
                } else {
                    currentPhone = (PhoneProxy)(PhoneFactory.getDefaultPhone());
                }
                hasSIMInserted = currentPhone.getIccCard().hasIccCard();
                 //boolean hasSIMInserted = mPhone.isSimInsert();

                if(FeatureOption.MTK_GEMINI_SUPPORT){
                    for(int simIdx=PhoneConstants.GEMINI_SIM_1;simIdx<PhoneConstants.GEMINI_SIM_NUM;simIdx++){
                        currentPeerPhone = ((GeminiPhone)(PhoneFactory.getDefaultPhone())).getPhonebyId(simIdx);
                        if( currentPeerPhone != null){
                        //if(mPhone.getPeerPhones(simIdx)!=null){
                            if(currentPeerPhone.getIccCard().hasIccCard()){
                            //if((mPhone.getPeerPhones(simIdx).isSimInsert()== true)){
                                hasPeerSIMInserted = true;
                                break;
                            }							
                        }							
                    }											
                }
                //MTK-END: for LEGO move GSMPhone.isSimInsert() API
                
                if (hasPeerSIMInserted || hasSIMInserted) {
                    if ((dualSimModeSetting & (GeminiNetworkSubUtil.MODE_SIM1_ONLY << mPhone.getMySimId())) == 0)
                        radioState = CommandsInterface.RadioState.RADIO_OFF;
                    
                } else if (!mDesiredPowerState) {
                //} else if (mPhone.getMySimId() != PhoneConstants.GEMINI_SIM_1){
                    //because when no SIM inserted, we still power on SIM1 for emergency call
                    //if this is not SIM1, we have to transfer state to radio-off
                    radioState = CommandsInterface.RadioState.RADIO_OFF;
                }
            }
            log("pollState is locked or absent, transfer to [" + radioState + "]");
        }

        switch (radioState) {
            case RADIO_UNAVAILABLE:
            case RADIO_OFF:
                mNewSS.setStateOff();
                mNewCellLoc.setStateInvalid();
                setSignalStrengthDefaultValues();
                mGotCountryCode = false;
                mNitzUpdatedTime = false;
                mIs3GTo2G = false; /* ALPS00348630 reset flag */
                mGsmRoaming = false;
                mNewReasonDataDenied = -1;
                mNewMaxDataCalls = 1;
                newps_networkType = ServiceState.RIL_RADIO_TECHNOLOGY_UNKNOWN;
                newGPRSState = ServiceState.STATE_POWER_OFF;
                mDataRoaming = false;
                mNewSS.setRilVoiceRadioTechnology(ServiceState.RIL_RADIO_TECHNOLOGY_UNKNOWN);
                //[ALPS00423362]
                mEmergencyOnly = false;

                //[ALPS00439473] MTK add - START
                mDontPollSignalStrength = false;
                setLastSignalStrengthDefaultValues(true);
                //[ALPS00439473] MTK add - END

                //MTK-ADD : for CS not registered , PS regsitered (ex: LTE PS only mode or 2/3G PS only SIM card or CS domain network registeration temporary failure
                isCsInvalidCard = false;

                pollStateDone();
                break;

            case RUIM_NOT_READY:
            case RUIM_READY:
            case RUIM_LOCKED_OR_ABSENT:
            case NV_NOT_READY:
            case NV_READY:
                if (DBG) log("Radio Technology Change ongoing, setting SS to off");
                mNewSS.setStateOff();
                mNewCellLoc.setStateInvalid();
                setSignalStrengthDefaultValues();
                mGotCountryCode = false;

                //NOTE: pollStateDone() is not needed in this case
                break;

            default:
                // Issue all poll-related commands at once
                // then count down the responses, which
                // are allowed to arrive out-of-order

                mPollingContext[0]++;
                mCi.getOperator(
                    obtainMessage(
                        EVENT_POLL_STATE_OPERATOR, mPollingContext));

                mPollingContext[0]++;
                mCi.getDataRegistrationState(
                    obtainMessage(
                        EVENT_POLL_STATE_GPRS, mPollingContext));

                mPollingContext[0]++;
                mCi.getVoiceRegistrationState(
                    obtainMessage(
                        EVENT_POLL_STATE_REGISTRATION, mPollingContext));

                mPollingContext[0]++;
                mCi.getNetworkSelectionMode(
                    obtainMessage(
                        EVENT_POLL_STATE_NETWORK_SELECTION_MODE, mPollingContext));
                break;
        }
    }

    private static String networkTypeToString(int type) {
        //Network Type from GPRS_REGISTRATION_STATE
        String ret = "unknown";

        switch (type) {
            case DATA_ACCESS_GPRS:
                ret = "GPRS";
                break;
            case DATA_ACCESS_EDGE:
                ret = "EDGE";
                break;
            case DATA_ACCESS_UMTS:
                ret = "UMTS";
                break;
            case DATA_ACCESS_HSDPA:
                ret = "HSDPA";
                break;
            case DATA_ACCESS_HSUPA:
                ret = "HSUPA";
                break;
            case DATA_ACCESS_HSPA:
                ret = "HSPA";
                break;
            default:
                break;
        }
        Rlog.e(LOG_TAG, "networkTypeToString: " + ret);
        return ret;
    }

    private void pollStateDone() {
        // PS & CS network type summarize -->
        // From 3G to 2G, CS NW type is ensured responding firstly. Before receiving
        // PS NW type change URC, PS NW type should always take CS NW type.
        if ((mNewSS.getRilVoiceRadioTechnology() > ServiceState.RIL_RADIO_TECHNOLOGY_UNKNOWN &&
                mNewSS.getRilVoiceRadioTechnology() <= ServiceState.RIL_RADIO_TECHNOLOGY_EDGE) &&
                mSS.getRilVoiceRadioTechnology() >= ServiceState.RIL_RADIO_TECHNOLOGY_UMTS) {
            mIs3GTo2G = true;
            log("pollStateDone(): mIs3GTo2G = true");
        }
        if (mIs3GTo2G == true) {
            newps_networkType = mNewSS.getRilVoiceRadioTechnology();
            //[ALPS01200539]-START: set data network type to new service state object.
            //Network type icon on status bar is reference to this type when it receiving data connection changed.
            mNewSS.setRilDataRadioTechnology(newps_networkType);
            //[ALPS01200539]-End
        } else if (newps_networkType > mNewSS.getRilVoiceRadioTechnology()) {    
            mNewSS.setRilVoiceRadioTechnology(newps_networkType);
            log("set RilVoiceRadioTechnology as:" + newps_networkType);
        }
        // <-- end of  PS & CS network type summarize

        if (DBG) {
            log("Poll ServiceState done: " +
                " oldSS=[" + mSS + "] newSS=[" + mNewSS + "]" +
                " oldMaxDataCalls=" + mMaxDataCalls +
                " mNewMaxDataCalls=" + mNewMaxDataCalls +
                " oldReasonDataDenied=" + mReasonDataDenied +
                " mNewReasonDataDenied=" + mNewReasonDataDenied +
                " oldGprsType=" + ps_networkType +
                " newGprsType=" + newps_networkType);
        }

        if (Build.IS_DEBUGGABLE && SystemProperties.getBoolean(PROP_FORCE_ROAMING, false)) {
            mNewSS.setRoaming(true);
        }

        useDataRegStateForDataOnlyDevices();

        boolean hasRegistered =
            mSS.getVoiceRegState() != ServiceState.STATE_IN_SERVICE
            && mNewSS.getVoiceRegState() == ServiceState.STATE_IN_SERVICE;

        boolean hasDeregistered =
            mSS.getVoiceRegState() == ServiceState.STATE_IN_SERVICE
            && mNewSS.getVoiceRegState() != ServiceState.STATE_IN_SERVICE;

        boolean hasGprsAttached =
                mSS.getDataRegState() != ServiceState.STATE_IN_SERVICE
                && mNewSS.getDataRegState() == ServiceState.STATE_IN_SERVICE;

        boolean hasPSNetworkTypeChanged = ps_networkType != newps_networkType;

        boolean hasDataRegStateChanged =
                mSS.getDataRegState() != mNewSS.getDataRegState();

        boolean hasVoiceRegStateChanged =
                mSS.getVoiceRegState() != mNewSS.getVoiceRegState();

////        boolean hasRadioTechnologyChanged = mRilRadioTechnology != mNewRilRadioTechnology;

        boolean hasRilVoiceRadioTechnologyChanged =
                mSS.getRilVoiceRadioTechnology() != mNewSS.getRilVoiceRadioTechnology();

        boolean hasRilDataRadioTechnologyChanged =
                mSS.getRilDataRadioTechnology() != mNewSS.getRilDataRadioTechnology();

        boolean hasChanged = !mNewSS.equals(mSS);

        boolean hasRoamingOn = !mSS.getRoaming() && mNewSS.getRoaming();

        boolean hasRoamingOff = mSS.getRoaming() && !mNewSS.getRoaming();

        boolean hasLocationChanged = !mNewCellLoc.equals(mCellLoc);

        //// boolean hasRegStateChanged = ss.getRegState() != newSS.getRegState();

        boolean hasLacChanged = mNewCellLoc.getLac() != mCellLoc.getLac();

        log("pollStateDone,hasRegistered:"+hasRegistered+",hasDeregistered:"+hasDeregistered+
        		",hasGprsAttached:"+hasGprsAttached+
        		",hasPSNetworkTypeChanged:"+hasPSNetworkTypeChanged+",hasRilVoiceRadioTechnologyChanged:"+hasRilVoiceRadioTechnologyChanged+
        		",hasRilDataRadioTechnologyChanged:"+hasRilDataRadioTechnologyChanged+",hasVoiceRegStateChanged:"+hasVoiceRegStateChanged+",hasDataRegStateChanged:"+hasDataRegStateChanged+
                        ",hasChanged:"+hasChanged+",hasRoamingOn:"+hasRoamingOn+",hasRoamingOff:"+hasRoamingOff+
        		",hasLocationChanged:"+hasLocationChanged+",hasLacChanged:"+hasLacChanged);
        // Add an event log when connection state changes
        if (hasVoiceRegStateChanged || hasDataRegStateChanged) {
            EventLog.writeEvent(EventLogTags.GSM_SERVICE_STATE_CHANGE,
                mSS.getVoiceRegState(), mSS.getDataRegState(), 
                mNewSS.getVoiceRegState(), mNewSS.getDataRegState());
        }

        mServiceStateExt.onPollStateDone(mSS, mNewSS, gprsState, newGPRSState);

        gprsState = newGPRSState;

        ps_networkType = newps_networkType;

        if (hasPSNetworkTypeChanged) {
            if (mSimId == PhoneConstants.GEMINI_SIM_1) {
                mPhone.setSystemProperty(TelephonyProperties.PROPERTY_DATA_NETWORK_TYPE, networkTypeToString(ps_networkType));
            } else if (mSimId == PhoneConstants.GEMINI_SIM_2){
                mPhone.setSystemProperty(TelephonyProperties.PROPERTY_DATA_NETWORK_TYPE_2, networkTypeToString(ps_networkType));
            } else if (mSimId == PhoneConstants.GEMINI_SIM_3){
                mPhone.setSystemProperty(TelephonyProperties.PROPERTY_DATA_NETWORK_TYPE_3, networkTypeToString(ps_networkType));
            } else if (mSimId == PhoneConstants.GEMINI_SIM_4){
                mPhone.setSystemProperty(TelephonyProperties.PROPERTY_DATA_NETWORK_TYPE_4, networkTypeToString(ps_networkType));
            }
            ratPsChangedRegistrants.notifyRegistrants(new AsyncResult(null, ps_networkType, null));
        }

        // Add an event log when network type switched
        // TODO: we may add filtering to reduce the event logged,
        // i.e. check preferred network setting, only switch to 2G, etc
        if (hasRilVoiceRadioTechnologyChanged) {
            int cid = -1;
            //MTK-START: For KK Migration            
            GsmCellLocation loc = mNewCellLoc;
            if (loc != null) cid = loc.getCid();
            // NOTE: this code was previously located after mSS and mNewSS are swapped, so
            // existing logs were incorrectly using the new state for "network_from"
            // and STATE_OUT_OF_SERVICE for "network_to". To avoid confusion, use a new log tag
            // to record the correct states.
            EventLog.writeEvent(EventLogTags.GSM_RAT_SWITCHED_NEW, cid,
                    mSS.getRilVoiceRadioTechnology(),
                    mNewSS.getRilVoiceRadioTechnology());
            if (DBG) {
                log("RAT switched "
                        + ServiceState.rilRadioTechnologyToString(mSS.getRilVoiceRadioTechnology())
                        + " -> "
                        + ServiceState.rilRadioTechnologyToString(
                                mNewSS.getRilVoiceRadioTechnology()) + " at cell " + cid);
            }
            //MTK-END: For KK Migration

            if (mSimId == PhoneConstants.GEMINI_SIM_1) {
                SystemProperties.set(TelephonyProperties.PROPERTY_CS_NETWORK_TYPE, Integer.toString(mNewSS.getRilVoiceRadioTechnology()));
            } else if (mSimId == PhoneConstants.GEMINI_SIM_2){
                SystemProperties.set(TelephonyProperties.PROPERTY_CS_NETWORK_TYPE_2, Integer.toString(mNewSS.getRilVoiceRadioTechnology()));
            } else if (mSimId == PhoneConstants.GEMINI_SIM_3){
                SystemProperties.set(TelephonyProperties.PROPERTY_CS_NETWORK_TYPE_3, Integer.toString(mNewSS.getRilVoiceRadioTechnology()));
            } else if (mSimId == PhoneConstants.GEMINI_SIM_4){
                SystemProperties.set(TelephonyProperties.PROPERTY_CS_NETWORK_TYPE_4, Integer.toString(mNewSS.getRilVoiceRadioTechnology()));
            }
            updateSpnDisplay(false);
            ratCsChangedRegistrants.notifyRegistrants(new AsyncResult(null, mNewSS.getRilVoiceRadioTechnology(), null));
        }

        //MTK-START: For KK Migration
        // swap mSS and mNewSS to put new state in mSS
        ServiceState tss = mSS;
        mSS = mNewSS;
        mNewSS = tss;
        // clean slate for next time
        //mNewSS.setStateOutOfService();

        // swap mCellLoc and mNewCellLoc to put new state in mCellLoc
        GsmCellLocation tcl = mCellLoc;
        mCellLoc = mNewCellLoc;
        mNewCellLoc = tcl;
        //MTK-END: For KK Migration

        gprsState = newGPRSState;
        mReasonDataDenied = mNewReasonDataDenied;
        mMaxDataCalls = mNewMaxDataCalls;

        //MTK-START: For KK Migration            
        if ((hasRilVoiceRadioTechnologyChanged) && (!mPhone.getUnitTestMode())) {
            updatePhoneObject();
        }
        //MTK-END: For KK Migration

        //mSS.setRilVoiceRadioTechnology(mNewSS.getRilVoiceRadioTechnology());
        
        // this new state has been applied - forget it until we get a new new state
        ////mNewRilRadioTechnology = 0;
        mNewSS.setRilVoiceRadioTechnology(0);
        log("After swap mSS.RilVoiceRadioTechnology=" + mSS.getRilVoiceRadioTechnology() +
                " mNewSS.RilVoiceRadioTechnology=" + mNewSS.getRilVoiceRadioTechnology());
        //mNewSS.setStateOutOfService(); // clean slate for next time

        if (hasRegistered) {
            mNetworkAttachedRegistrants.notifyRegistrants();
            mLastRegisteredPLMN = mSS.getOperatorNumeric() ;
            log("mLastRegisteredPLMN= "+mLastRegisteredPLMN);

            if (DBG) {
                log("pollStateDone: registering current mNitzUpdatedTime=" +
                        mNitzUpdatedTime + " changing to false");
            }
            mNitzUpdatedTime = false;
        }

        if(explict_update_spn ==1)
        {
             /* ALPS00273961 :Screen on, modem explictly send CREG URC , but still not able to update screen due to hasChanged is false
                           In this case , we update SPN display by explict_update_spn */
             if(!hasChanged)
             {
                 log("explict_update_spn trigger to refresh SPN");
                 updateSpnDisplay(true);
             }
             explict_update_spn = 0;
        }

        if (hasChanged) {
            updateSpnDisplay();
            String operatorNumeric = mSS.getOperatorNumeric();
            String prevOperatorNumeric;

            if (PhoneConstants.GEMINI_SIM_1 == mSimId) {
                prevOperatorNumeric = SystemProperties.get(TelephonyProperties.PROPERTY_OPERATOR_NUMERIC, "");
                mPhone.setSystemProperty(TelephonyProperties.PROPERTY_OPERATOR_ALPHA, mSS.getOperatorAlphaLong());
                mPhone.setSystemProperty(TelephonyProperties.PROPERTY_OPERATOR_NUMERIC, mSS.getOperatorNumeric());
                mPhone.setSystemProperty(TelephonyProperties.PROPERTY_OPERATOR_ISROAMING, mSS.getRoaming() ? "true" : "false");
            } else if (PhoneConstants.GEMINI_SIM_2 == mSimId){
                prevOperatorNumeric = SystemProperties.get(TelephonyProperties.PROPERTY_OPERATOR_NUMERIC_2, "");
                mPhone.setSystemProperty(TelephonyProperties.PROPERTY_OPERATOR_ALPHA_2, mSS.getOperatorAlphaLong());
                mPhone.setSystemProperty(TelephonyProperties.PROPERTY_OPERATOR_NUMERIC_2, mSS.getOperatorNumeric());
                mPhone.setSystemProperty(TelephonyProperties.PROPERTY_OPERATOR_ISROAMING_2, mSS.getRoaming() ? "true" : "false");
            } else if (PhoneConstants.GEMINI_SIM_3 == mSimId){
                prevOperatorNumeric = SystemProperties.get(TelephonyProperties.PROPERTY_OPERATOR_NUMERIC_3, "");
                mPhone.setSystemProperty(TelephonyProperties.PROPERTY_OPERATOR_ALPHA_3, mSS.getOperatorAlphaLong());                
                mPhone.setSystemProperty(TelephonyProperties.PROPERTY_OPERATOR_NUMERIC_3, mSS.getOperatorNumeric());
                mPhone.setSystemProperty(TelephonyProperties.PROPERTY_OPERATOR_ISROAMING_3, mSS.getRoaming() ? "true" : "false");
            } else {
                prevOperatorNumeric = SystemProperties.get(TelephonyProperties.PROPERTY_OPERATOR_NUMERIC_4, "");
                mPhone.setSystemProperty(TelephonyProperties.PROPERTY_OPERATOR_ALPHA_4, mSS.getOperatorAlphaLong());                
                mPhone.setSystemProperty(TelephonyProperties.PROPERTY_OPERATOR_NUMERIC_4, mSS.getOperatorNumeric());
                mPhone.setSystemProperty(TelephonyProperties.PROPERTY_OPERATOR_ISROAMING_4, mSS.getRoaming() ? "true" : "false");
            }

            if (operatorNumeric == null) {
                if (DBG) log("operatorNumeric is null");
 
                if (PhoneConstants.GEMINI_SIM_1 == mSimId) {
                    mPhone.setSystemProperty(TelephonyProperties.PROPERTY_OPERATOR_ISO_COUNTRY, "");
                } else if (PhoneConstants.GEMINI_SIM_2 == mSimId){
                    mPhone.setSystemProperty(TelephonyProperties.PROPERTY_OPERATOR_ISO_COUNTRY_2, "");
                } else if (PhoneConstants.GEMINI_SIM_3 == mSimId){
                    mPhone.setSystemProperty(TelephonyProperties.PROPERTY_OPERATOR_ISO_COUNTRY_3, "");
                } else if (PhoneConstants.GEMINI_SIM_4 == mSimId){
                    mPhone.setSystemProperty(TelephonyProperties.PROPERTY_OPERATOR_ISO_COUNTRY_4, "");
                }
                mGotCountryCode = false;
                mNitzUpdatedTime = false;
            } else {
                String iso = "";
                String mcc = operatorNumeric.substring(0, 3);
                try{
                    iso = MccTable.countryCodeForMcc(Integer.parseInt(
                            operatorNumeric.substring(0,3)));
                } catch ( NumberFormatException ex){
                    Rlog.w(LOG_TAG, "countryCodeForMcc error" + ex);
                } catch ( StringIndexOutOfBoundsException ex) {
                    Rlog.w(LOG_TAG, "countryCodeForMcc error" + ex);
                }

                if (PhoneConstants.GEMINI_SIM_1 == mSimId) {
                	mPhone.setSystemProperty(TelephonyProperties.PROPERTY_OPERATOR_ISO_COUNTRY, iso);
                } else if (PhoneConstants.GEMINI_SIM_2 == mSimId){
                    mPhone.setSystemProperty(TelephonyProperties.PROPERTY_OPERATOR_ISO_COUNTRY_2, iso);
                } else if (PhoneConstants.GEMINI_SIM_3 == mSimId){
                    mPhone.setSystemProperty(TelephonyProperties.PROPERTY_OPERATOR_ISO_COUNTRY_3, iso);
                } else if (PhoneConstants.GEMINI_SIM_4 == mSimId){
                    mPhone.setSystemProperty(TelephonyProperties.PROPERTY_OPERATOR_ISO_COUNTRY_4, iso);
                }
                mGotCountryCode = true;

                TimeZone zone = null;

                if (!mNitzUpdatedTime && !mcc.equals("000") && !TextUtils.isEmpty(iso) &&
                        getAutoTimeZone()) {

                    // Test both paths if ignore nitz is true
                    boolean testOneUniqueOffsetPath = SystemProperties.getBoolean(
                                TelephonyProperties.PROPERTY_IGNORE_NITZ, false) &&
                                    ((SystemClock.uptimeMillis() & 1) == 0);
                    
                    ArrayList<TimeZone> uniqueZones = TimeUtils.getTimeZonesWithUniqueOffsets(iso);
                    if ((uniqueZones.size() == 1) || testOneUniqueOffsetPath) {
                        zone = uniqueZones.get(0);
                        if (DBG) {
                           log("pollStateDone: no nitz but one TZ for iso-cc=" + iso +
                                   " with zone.getID=" + zone.getID() +
                                   " testOneUniqueOffsetPath=" + testOneUniqueOffsetPath);
                        }
                        setAndBroadcastNetworkSetTimeZone(zone.getID());
                    //MTK-START: [ALPS01262709] update time with MCC/MNC
                    //} else {
                    } else if(uniqueZones.size()>1) {
                        log("uniqueZones.size="+uniqueZones.size());
                        zone = getTimeZonesWithCapitalCity(iso);
                        setAndBroadcastNetworkSetTimeZone(zone.getID());
                    //MTK-END: [ALPS01262709] update time with MCC/MNC
                    } else {
                        if (DBG) {
                            log("pollStateDone: there are " + uniqueZones.size() +
                                " unique offsets for iso-cc='" + iso +
                                " testOneUniqueOffsetPath=" + testOneUniqueOffsetPath +
                                "', do nothing");
                        }
                    }
                }

                if (shouldFixTimeZoneNow(mPhone, operatorNumeric, prevOperatorNumeric,
                        mNeedFixZoneAfterNitz)) {
                    // If the offset is (0, false) and the timezone property
                    // is set, use the timezone property rather than
                    // GMT.
                    String zoneName = SystemProperties.get(TIMEZONE_PROPERTY);
                    if (DBG) {
                        log("pollStateDone: fix time zone zoneName='" + zoneName +
                            "' mZoneOffset=" + mZoneOffset + " mZoneDst=" + mZoneDst +
                            " iso-cc='" + iso +
                            "' iso-cc-idx=" + Arrays.binarySearch(GMT_COUNTRY_CODES, iso));
                    }

                    if (iso.equals("")){
                        // Country code not found.  This is likely a test network.
                        // Get a TimeZone based only on the NITZ parameters (best guess).
                        zone = getNitzTimeZone(mZoneOffset, mZoneDst, mZoneTime);
                        if (DBG) log("pollStateDone: using NITZ TimeZone");
                    }else if ((mZoneOffset == 0) && (mZoneDst == false) &&
                        (zoneName != null) && (zoneName.length() > 0) &&
                        (Arrays.binarySearch(GMT_COUNTRY_CODES, iso) < 0)) {
                        // "(mZoneOffset == 0) && (mZoneDst == false) &&
                        //  (Arrays.binarySearch(GMT_COUNTRY_CODES, iso) < 0)"
                        // means that we received a NITZ string telling
                        // it is in GMT+0 w/ DST time zone
                        // BUT iso tells is NOT, e.g, a wrong NITZ reporting
                        // local time w/ 0 offset.
                        zone = TimeZone.getDefault();

                        //MTK-ADD-Start: [ALPS01262709] try ot fix timezone by MCC
                        try {
                            String mccTz = MccTable.defaultTimeZoneForMcc(Integer.parseInt(mcc));
                            if(mccTz != null){
                                zone = TimeZone.getTimeZone(mccTz );
                                if (DBG) log("pollStateDone: try to fixTimeZone mcc:"+ mcc +" mccTz:"+mccTz);
                            }
                        } catch (Exception e) {
                            log("pollStateDone: parse error: mcc="+mcc);
                        }
                        //MTK-ADD-END: [ALPS01262709] try ot fix timezone by MCC

                        if (mNeedFixZoneAfterNitz) {
                            // For wrong NITZ reporting local time w/ 0 offset,
                            // need adjust time to reflect default timezone setting
                            long ctm = System.currentTimeMillis();
                            long tzOffset = zone.getOffset(ctm);
                            if (DBG) {
                                log("pollStateDone: tzOffset=" + tzOffset + " ltod=" +
                                        TimeUtils.logTimeOfDay(ctm));
                            }
                            if (getAutoTime()) {
                                long adj = ctm - tzOffset;
                                if (DBG) log("pollStateDone: adj ltod=" +
                                        TimeUtils.logTimeOfDay(adj));
                                setAndBroadcastNetworkSetTime(adj);
                            } else {
                                // Adjust the saved NITZ time to account for tzOffset.
                                mSavedTime = mSavedTime - tzOffset;
                            }
                        }
                        if (DBG) log("pollStateDone: using default TimeZone");
                    } else {
                        zone = TimeUtils.getTimeZone(mZoneOffset, mZoneDst, mZoneTime, iso);
                        if (DBG) log("pollStateDone: using getTimeZone(off, dst, time, iso)");
                    }

                    mNeedFixZoneAfterNitz = false;

                    if (zone != null) {
                        log("pollStateDone: zone != null zone.getID=" + zone.getID());
                        if (getAutoTimeZone()) {
                            setAndBroadcastNetworkSetTimeZone(zone.getID());
                        }
                        saveNitzTimeZone(zone.getID());
                    } else {
                        log("pollStateDone: zone == null");
                    }
                }
            }

            if (hasVoiceRegStateChanged) {
                if (mSS.getVoiceRegState() == ServiceState.REGISTRATION_STATE_UNKNOWN
                    && (1 == Settings.Global.getInt(mPhone.getContext().getContentResolver(), Settings.Global.AIRPLANE_MODE_ON, -1))) {
                    int serviceState = mPhone.getServiceState().getVoiceRegState();
                    if (serviceState != ServiceState.STATE_POWER_OFF) {
                        mSS.setStateOff();
                    }
                }

                //MTK-ADD START : for CS not registered , PS regsitered (ex: LTE PS only mode or 2/3G PS only SIM card or CS domain network registeration temporary failure
                log("pollStateDone reset isCsInvalidCard=false");
                isCsInvalidCard = false;
                //MTK-ADD END : for CS not registered , PS regsitered (ex: LTE PS only mode or 2/3G PS only SIM card or CS domain network registeration temporary failure

                mPhone.updateSimIndicateState();
            }
            mPhone.notifyServiceStateChanged(mSS);

            if (hasRegistered) {
                /* ALPS00296741: to handle searching state to registered scenario,we force status bar to refresh signal icon */
                log("force update signal strength after notifyServiceStateChanged");
                mPhone.notifySignalStrength();
            }
        }
        else
        {
            if((hasLacChanged == true) &&(mSS.getVoiceRegState() == ServiceState.STATE_IN_SERVICE))
            {
                /* EONS display might be changed due to LAC changed */
                log("force updateSpnDisplay due to LAC changed");
                updateSpnDisplay();
            }
        }

        if (hasGprsAttached) {
            mAttachedRegistrants.notifyRegistrants();
            mLastPSRegisteredPLMN = mSS.getOperatorNumeric() ;
            log("mLastPSRegisteredPLMN= "+mLastPSRegisteredPLMN);
        }

        //MTK-START: [ALPS01316672] fix KK Migration error
        if (hasRilDataRadioTechnologyChanged || hasPSNetworkTypeChanged ||
            hasDataRegStateChanged) {
        //if (hasRilVoiceRadioTechnologyChanged || hasPSNetworkTypeChanged) {
        //MTK-End: [ALPS01316672] fix KK Migration error            
            //MTK-START: For KK Migration
            notifyDataRegStateRilRadioTechnologyChanged();            
            //MTK-END: For KK Migration
            mPhone.notifyDataConnection(Phone.REASON_NW_TYPE_CHANGED);
        }

        if (hasLocationChanged) {
        	mPhone.notifyLocationChanged();
        }

        if (hasRoamingOn) {
            Settings.System.putInt(mPhone.getContext().getContentResolver(),
                                            Settings.System.ROAMING_INDICATION_NEEDED,
                                            1);

            if (mSimId == PhoneConstants.GEMINI_SIM_1) {
                SystemProperties.set(TelephonyProperties.PROPERTY_ROAMING_INDICATOR_NEEDED, "true");
            } else if (mSimId == PhoneConstants.GEMINI_SIM_2){
                SystemProperties.set(TelephonyProperties.PROPERTY_ROAMING_INDICATOR_NEEDED_2, "true");
            } else if (mSimId == PhoneConstants.GEMINI_SIM_3){
                SystemProperties.set(TelephonyProperties.PROPERTY_ROAMING_INDICATOR_NEEDED_3, "true");
            } else if (mSimId == PhoneConstants.GEMINI_SIM_4){
                SystemProperties.set(TelephonyProperties.PROPERTY_ROAMING_INDICATOR_NEEDED_4, "true");
            }
            mRoamingOnRegistrants.notifyRegistrants();
        }

        if (hasRoamingOff) {
            Settings.System.putInt(mPhone.getContext().getContentResolver(),
                                            Settings.System.ROAMING_INDICATION_NEEDED,
                                            0);
			
            if (mSimId == PhoneConstants.GEMINI_SIM_1) {
                SystemProperties.set(TelephonyProperties.PROPERTY_ROAMING_INDICATOR_NEEDED, "false");
            } else if (mSimId == PhoneConstants.GEMINI_SIM_2){
                SystemProperties.set(TelephonyProperties.PROPERTY_ROAMING_INDICATOR_NEEDED_2, "false");
            } else if (mSimId == PhoneConstants.GEMINI_SIM_3){
                SystemProperties.set(TelephonyProperties.PROPERTY_ROAMING_INDICATOR_NEEDED_3, "false");
            } else if (mSimId == PhoneConstants.GEMINI_SIM_4){
                SystemProperties.set(TelephonyProperties.PROPERTY_ROAMING_INDICATOR_NEEDED_4, "false");
            }
            mRoamingOffRegistrants.notifyRegistrants();
        }

        if (! isGprsConsistent(mSS.getDataRegState(),  mSS.getVoiceRegState())) {
            if (!mStartedGprsRegCheck && !mReportedGprsNoReg) {
                mStartedGprsRegCheck = true;

                int check_period = Settings.Global.getInt(
                        mPhone.getContext().getContentResolver(),
                        Settings.Global.GPRS_REGISTER_CHECK_PERIOD_MS,
                        DEFAULT_GPRS_CHECK_PERIOD_MILLIS);
                sendMessageDelayed(obtainMessage(EVENT_CHECK_REPORT_GPRS),
                        check_period);
            }
        } else {
            mReportedGprsNoReg = false;
        }
    }

    /**
     * Check if GPRS got registered while voice is registered.
     *
     * @param dataRegState i.e. CGREG in GSM
     * @param voiceRegState i.e. CREG in GSM
     * @return false if device only register to voice but not gprs
     */
    private boolean isGprsConsistent(int dataRegState, int voiceRegState) {
        return !((voiceRegState == ServiceState.STATE_IN_SERVICE) &&
                (dataRegState != ServiceState.STATE_IN_SERVICE));
    }

    /**
     * Returns a TimeZone object based only on parameters from the NITZ string.
     */
    private TimeZone getNitzTimeZone(int offset, boolean dst, long when) {
        TimeZone guess = findTimeZone(offset, dst, when);
        if (guess == null) {
            // Couldn't find a proper timezone.  Perhaps the DST data is wrong.
            guess = findTimeZone(offset, !dst, when);
        }
        if (DBG) log("getNitzTimeZone returning " + (guess == null ? guess : guess.getID()));
        return guess;
    }

    private TimeZone findTimeZone(int offset, boolean dst, long when) {
    	log("[NITZ],findTimeZone,offset:"+offset+",dst:"+dst+",when:"+when);
        int rawOffset = offset;
        if (dst) {
            rawOffset -= 3600000;
        }
        String[] zones = TimeZone.getAvailableIDs(rawOffset);
        TimeZone guess = null;
        Date d = new Date(when);
        for (String zone : zones) {
            TimeZone tz = TimeZone.getTimeZone(zone);
            if (tz.getOffset(when) == offset &&
                tz.inDaylightTime(d) == dst) {
                guess = tz;
                log("[NITZ],find time zone.");
                break;
            }
        }

        return guess;
    }

    private void queueNextSignalStrengthPoll() {
        if (mDontPollSignalStrength) {
            // The radio is telling us about signal strength changes
            // we don't have to ask it
            return;
        }

        Message msg;

        msg = obtainMessage();
        msg.what = EVENT_POLL_SIGNAL_STRENGTH;

        long nextTime;

        // TODO Don't poll signal strength if screen is off
        sendMessageDelayed(msg, POLL_PERIOD_MILLIS);
    }


    private void onFemtoCellInfoResult(AsyncResult ar) {
        String info[];
	    int isCsgCell = 0;

        if (ar.exception != null || ar.result == null) {
           loge("onFemtoCellInfo exception");
        } else {
            info = (String[])ar.result;

            if (info.length > 0) {

                if (info[0] != null && info[0].length() > 0) {
                    mFemtocellDomain = Integer.parseInt(info[0]);
                    log("onFemtoCellInfo: mFemtocellDomain set to "+mFemtocellDomain);					
                }
				
                if (info[5] != null && info[5].length() > 0) {
                   isCsgCell = Integer.parseInt(info[5]);
                }

                log("onFemtoCellInfo: domain= "+mFemtocellDomain+",isCsgCell= "+isCsgCell);
				
                if(isCsgCell == 1){
                    if (info[6] != null && info[6].length() > 0) {
                        mCsgId = info[6];
                        log("onFemtoCellInfo: mCsgId set to "+mCsgId);						
                    }						

                    if (info[8] != null && info[8].length() > 0) {
                        mHhbName = new String(RIL.hexStringToBytes(info[8]));						
                        log("onFemtoCellInfo: mHhbName set from "+ info[8] +" to "+mHhbName);
                    } else {
                        mHhbName = null;
                        log("onFemtoCellInfo: mHhbName is not available ,set to null");						
                    }
                } else {
                    mCsgId = null;
                    mHhbName = null;
                    log("onFemtoCellInfo: csgId and hnbName are cleared");										
                }

                Intent intent = new Intent(Intents.SPN_STRINGS_UPDATED_ACTION);
                intent.putExtra(PhoneConstants.GEMINI_SIM_ID_KEY, mSimId);

                if (!FeatureOption.MTK_GEMINI_SUPPORT) {
                    intent.addFlags(Intent.FLAG_RECEIVER_REPLACE_PENDING);
                }

                intent.putExtra(Intents.EXTRA_SHOW_SPN, mCurShowSpn);
                intent.putExtra(Intents.EXTRA_SPN, mCurSpn);
                intent.putExtra(Intents.EXTRA_SHOW_PLMN, mCurShowPlmn);
                intent.putExtra(Intents.EXTRA_PLMN, mCurPlmn);
                // Femtocell (CSG) info
                intent.putExtra(Intents.EXTRA_HNB_NAME, mHhbName);
                intent.putExtra(Intents.EXTRA_CSG_ID, mCsgId);
                intent.putExtra(Intents.EXTRA_DOMAIN, mFemtocellDomain);

                mPhone.getContext().sendStickyBroadcast(intent);
            }
        }		
    }

    /* ALPS01139189 START */
    private void broadcastHideNetworkState(String action,int state) {
        if (DBG) log("broadcastHideNetworkUpdate action="+action+"state="+state);
        Intent intent = new Intent(TelephonyIntents.ACTION_HIDE_NW_STATE);
        intent.addFlags(Intent.FLAG_RECEIVER_REPLACE_PENDING);
        intent.putExtra(TelephonyIntents.EXTRA_ACTION, action);
        intent.putExtra(TelephonyIntents.EXTRA_REAL_SERVICE_STATE, state);		
        intent.putExtra(PhoneConstants.GEMINI_SIM_ID_KEY, mSimId);		
        mPhone.getContext().sendStickyBroadcastAsUser(intent, UserHandle.ALL);
    }
    /* ALPS01139189 END */
	
    private void onNetworkStateChangeResult(AsyncResult ar) {
        String info[];
        int state = -1;
        int lac= -1;
        int cid = -1;
        int Act= -1;
        int cause= -1;

        /* Note: There might not be full +CREG URC info when screen off
                   Full URC format: +CREG:  <stat>, <lac>, <cid>, <Act>,<cause> */
        if (ar.exception != null || ar.result == null) {
           loge("onNetworkStateChangeResult exception");
        } else {
            info = (String[])ar.result;

            if (info.length > 0) {

                state = Integer.parseInt(info[0]);

                if (info[1] != null && info[1].length() > 0) {
                   lac = Integer.parseInt(info[1], 16);
                }

                if (info[2] != null && info[2].length() > 0) {
                   //TODO: fix JE (java.lang.NumberFormatException: Invalid int: "ffffffff")
                   if ( info[2].equals("FFFFFFFF") || info[2].equals("ffffffff")){
                       log("Invalid cid:"+info[2]);
                       info[2] = "0000ffff";
                   }
                   cid = Integer.parseInt(info[2], 16);
                }

                if (info[3] != null && info[3].length() > 0) {
                   Act = Integer.parseInt(info[3]);
                }

                if (info[4] != null && info[4].length() > 0) {
                   cause = Integer.parseInt(info[4]);
                }

                log("onNetworkStateChangeResult state:"+state+" lac:"+lac+" cid:"+cid+" Act:"+Act+" cause:"+cause);

                //ALPS00267573 CDR-ONS-245
                if(mServiceStateExt.needIgnoredState(mSS.getVoiceRegState(),state,cause) == true)
                {
                    //MTK-ADD START : for CS not registered , PS regsitered (ex: LTE PS only mode or 2/3G PS only SIM card or CS domain network registeration temporary failure
                    /* in case of CS not registered but PS regsitered, it will fasle alarm "CS invalid".*/
                    log("onNetworkStateChangeResult isCsInvalidCard:"+isCsInvalidCard);
                    if (!isCsInvalidCard){                    
                        if(dontUpdateNetworkStateFlag == false){
                            broadcastHideNetworkState("start",ServiceState.STATE_OUT_OF_SERVICE);
                        }
                        dontUpdateNetworkStateFlag = true;
                    } //end of if (!isCsInvalidCard)       
                    return;
                } else {
                   if(dontUpdateNetworkStateFlag == true){
                        broadcastHideNetworkState("stop",ServiceState.STATE_OUT_OF_SERVICE);
                    }
                    dontUpdateNetworkStateFlag = false;
                }

                /* AT+CREG? result won't include <lac>,<cid> when phone is NOT registered.
                   So we wpdate mNewCellLoc via +CREG URC when phone is not registered to network ,so that CellLoc can be updated when pollStateDone  */
                if((lac!=-1)&&(cid!=-1)&&(regCodeToServiceState(state) == ServiceState.STATE_OUT_OF_SERVICE) ){
                    // ignore unknown lac or cid value                
                    if(lac==0xfffe || cid==0x0fffffff)
                    {
                        log("unknown lac:"+lac+"or cid:"+cid);
                    }
                    else
                    {
                        log("mNewCellLoc Updated, lac:"+lac+"and cid:"+cid);                    
                        mNewCellLoc.setLacAndCid(lac, cid);
                    }
                }
				
                // ALPS00283696 CDR-NWS-241
                if(mServiceStateExt.needRejectCauseNotification(cause) == true)
                {
                    setRejectCauseNotification(cause);
                }


                // ALPS00283717 CDR-NWS-190
                if(mServiceStateExt.setEmergencyCallsOnly(state,cid) == 1)
                {
                    log("onNetworkStateChangeResult set mEmergencyOnly");
                    mEmergencyOnly = true;
                }
                else if(mServiceStateExt.setEmergencyCallsOnly(state,cid) == 0)
                {
                    if(mEmergencyOnly == true)
                    {
                        log("onNetworkStateChangeResult reset mEmergencyOnly");
                        mEmergencyOnly = false;
                    }
                }

            } else {
                loge("onNetworkStateChangeResult length zero");
            }
        }

        return;
    }


    /**
     *  Send signal-strength-changed notification if changed.
     *  Called both for solicited and unsolicited signal strength updates.
     */

/*    
    private AsyncResult onGsmSignalStrengthResult(AsyncResult ar) {
        AsyncResult ret = new AsyncResult (ar.userObj, null, ar.exception);
        int rssi = 99;
        int mGsmBitErrorRate = -1;
        int mGsmRssiQdbm = 0;
        int mGsmRscpQdbm = 0;
        int mGsmEcn0Qdbm = 0;

        if (ar.exception != null) {
            // -1 = unknown
            // most likely radio is resetting/disconnected
            setSignalStrengthDefaultValues();
        } else {
            //int[] ints = (int[])ar.result;
            SignalStrength s = (SignalStrength)ar.result;
            rssi = s.getGsmSignalStrength();
            if (rssi != 99) {
                // MTK RIL send signal strength information in the following order rssi,ber,rssi_qdbm, rscp_qdbm, ecn0_qdbm
                mGsmRssiQdbm = s.getCdmaDbm();
                mGsmRscpQdbm = s.getCdmaEcio();
                mGsmEcn0Qdbm = s.getEvdoDbm();
                SignalStrength mNewSignalStrength = new SignalStrength(mSimId, rssi, -1, -1, -1, -1, -1, -1, 99,
                                                     SignalStrength.INVALID,
                                                     SignalStrength.INVALID,
                                                     SignalStrength.INVALID,
                                                     SignalStrength.INVALID,
                                                     true, mGsmRssiQdbm, mGsmRscpQdbm, mGsmEcn0Qdbm);

                //if (DBG) log("onGsmSignalStrengthResult():mNewSignalStrength="+ mNewSignalStrength.toString());

                ret = new AsyncResult (ar.userObj, mNewSignalStrength, ar.exception);
            }
        }

        //MTK-START [ALPS415367]For MR1 Migration
        //BEGIN mtk03923 [20120115][ALPS00113979]
        //mSignalStrength = new SignalStrength(rssi, -1, -1, -1, -1, -1, -1, lteSignalStrength, lteRsrp, lteRsrq, lteRssnr, lteCqi, true);
        //MTK-START [mtk04258][120308][ALPS00237725]For CMCC
        //mSignalStrength = new SignalStrength(rssi, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, true, rscpQdbm,mSimId);
        //MTK-END [mtk04258][120308][ALPS00237725]For CMCC
        //BEGIN mtk03923 [20120115][ALPS00113979]

        //if (!mSignalStrength.equals(oldSignalStrength)) {
        //    try { // This takes care of delayed EVENT_POLL_SIGNAL_STRENGTH (scheduled after
        //        // POLL_PERIOD_MILLIS) during Radio Technology Change)
        //        //20120317 ALPS_00253948	 ignore unknown RSSI state (99)
        //        if((rssi == 99)&&(ss.getState() == ServiceState.STATE_IN_SERVICE)){
        //            log("Ignore rssi 99(unknown)");
        //        }
        //        else{
        //            mPhone.notifySignalStrength();
        //        }
        //   } catch (NullPointerException ex) {
        //      log("onSignalStrengthResult() Phone already destroyed: " + ex
        //                + "SignalStrength not notified");
        //   }
        //}
        //MTK-END [ALPS415367]For MR1 Migration

        return ret;
    }
*/    


    /**
     *  Send signal-strength-changed notification if changed.
     *  Called both for solicited and unsolicited signal strength updates.
     */     
    private AsyncResult onGsmSignalStrengthResult(AsyncResult ar) {
        AsyncResult ret = new AsyncResult (ar.userObj, null, ar.exception);

        if (ar.exception != null) {
            // most likely radio is resetting/disconnected
            setSignalStrengthDefaultValues();
        } else {
            SignalStrength s = (SignalStrength)ar.result;
            s.setSimId(mSimId);
            ret = new AsyncResult (ar.userObj, s, ar.exception);
        }
        return ret;
    }

    /**
     * Set restricted state based on the OnRestrictedStateChanged notification
     * If any voice or packet restricted state changes, trigger a UI
     * notification and notify registrants when sim is ready.
     *
     * @param ar an int value of RIL_RESTRICTED_STATE_*
     */
    private void onRestrictedStateChanged(AsyncResult ar) {
        RestrictedState newRs = new RestrictedState();

        if (DBG) log("onRestrictedStateChanged: E rs "+ mRestrictedState);

        if (ar.exception == null) {
            int[] ints = (int[])ar.result;
            int state = ints[0];

            newRs.setCsEmergencyRestricted(
                    ((state & RILConstants.RIL_RESTRICTED_STATE_CS_EMERGENCY) != 0) ||
                    ((state & RILConstants.RIL_RESTRICTED_STATE_CS_ALL) != 0) );
            //ignore the normal call and data restricted state before SIM READY
            if (mUiccApplcation != null && mUiccApplcation.getState() == AppState.APPSTATE_READY) {
                newRs.setCsNormalRestricted(
                        ((state & RILConstants.RIL_RESTRICTED_STATE_CS_NORMAL) != 0) ||
                        ((state & RILConstants.RIL_RESTRICTED_STATE_CS_ALL) != 0) );
                newRs.setPsRestricted(
                        (state & RILConstants.RIL_RESTRICTED_STATE_PS_ALL)!= 0);
            } else {
                log("[DSAC DEB] IccCard state Not ready ");
                if (mRestrictedState.isCsNormalRestricted() &&
                	((state & RILConstants.RIL_RESTRICTED_STATE_CS_NORMAL) == 0 &&
                	(state & RILConstants.RIL_RESTRICTED_STATE_CS_ALL) == 0)) {
                        newRs.setCsNormalRestricted(false);
                }

                if(mRestrictedState.isPsRestricted() && ((state & RILConstants.RIL_RESTRICTED_STATE_PS_ALL) == 0)) {
                    newRs.setPsRestricted(false);
                }
    	    }

            log("[DSAC DEB] new rs "+ newRs);

            if (!mRestrictedState.isPsRestricted() && newRs.isPsRestricted()) {
                mPsRestrictEnabledRegistrants.notifyRegistrants();
                setNotification(PS_ENABLED);
            } else if (mRestrictedState.isPsRestricted() && !newRs.isPsRestricted()) {
                mPsRestrictDisabledRegistrants.notifyRegistrants();
                setNotification(PS_DISABLED);
            }

            /**
             * There are two kind of cs restriction, normal and emergency. So
             * there are 4 x 4 combinations in current and new restricted states
             * and we only need to notify when state is changed.
             */
            if (mRestrictedState.isCsRestricted()) {
                if (!newRs.isCsRestricted()) {
                    // remove all restriction
                    setNotification(CS_DISABLED);
                } else if (!newRs.isCsNormalRestricted()) {
                    // remove normal restriction
                    setNotification(CS_EMERGENCY_ENABLED);
                } else if (!newRs.isCsEmergencyRestricted()) {
                    // remove emergency restriction
                    setNotification(CS_NORMAL_ENABLED);
                }
            } else if (mRestrictedState.isCsEmergencyRestricted() &&
                    !mRestrictedState.isCsNormalRestricted()) {
                if (!newRs.isCsRestricted()) {
                    // remove all restriction
                    setNotification(CS_DISABLED);
                } else if (newRs.isCsRestricted()) {
                    // enable all restriction
                    setNotification(CS_ENABLED);
                } else if (newRs.isCsNormalRestricted()) {
                    // remove emergency restriction and enable normal restriction
                    setNotification(CS_NORMAL_ENABLED);
                }
            } else if (!mRestrictedState.isCsEmergencyRestricted() &&
                    mRestrictedState.isCsNormalRestricted()) {
                if (!newRs.isCsRestricted()) {
                    // remove all restriction
                    setNotification(CS_DISABLED);
                } else if (newRs.isCsRestricted()) {
                    // enable all restriction
                    setNotification(CS_ENABLED);
                } else if (newRs.isCsEmergencyRestricted()) {
                    // remove normal restriction and enable emergency restriction
                    setNotification(CS_EMERGENCY_ENABLED);
                }
            } else {
                if (newRs.isCsRestricted()) {
                    // enable all restriction
                    setNotification(CS_ENABLED);
                } else if (newRs.isCsEmergencyRestricted()) {
                    // enable emergency restriction
                    setNotification(CS_EMERGENCY_ENABLED);
                } else if (newRs.isCsNormalRestricted()) {
                    // enable normal restriction
                    setNotification(CS_NORMAL_ENABLED);
                }
            }

            mRestrictedState = newRs;
        }
        log("onRestrictedStateChanged: X rs "+ mRestrictedState);
    }

    /** code is registration state 0-5 from TS 27.007 7.2 */
    private int regCodeToServiceState(int code) {
        switch (code) {
            case 0:
            case 2: // 2 is "searching"
            case 3: // 3 is "registration denied"
            case 4: // 4 is "unknown" no vaild in current baseband
            case 10:// same as 0, but indicates that emergency call is possible.
            case 12:// same as 2, but indicates that emergency call is possible.
            case 13:// same as 3, but indicates that emergency call is possible.
            case 14:// same as 4, but indicates that emergency call is possible.
                return ServiceState.STATE_OUT_OF_SERVICE;

            case 1:
                return ServiceState.STATE_IN_SERVICE;

            case 5:
                // in service, roam
                return ServiceState.STATE_IN_SERVICE;

            default:
                loge("regCodeToServiceState: unexpected service state " + code);
                return ServiceState.STATE_OUT_OF_SERVICE;
        }
    }


    /**
     * code is registration state 0-5 from TS 27.007 7.2
     * returns true if registered roam, false otherwise
     */
    private boolean regCodeIsRoaming (int code) {
        boolean isRoaming = false;
        // SIMRecords simRecords = (SIMRecords)(mPhone.mIccRecords.get());
        SIMRecords simRecords = null;
    	  IccRecords r = mPhone.mIccRecords.get();
    	  if (r != null) {
            simRecords = (SIMRecords)r;
    	  }
    	          
        //String strHomePlmn = simRecords.getSIMOperatorNumeric();
        String strHomePlmn = (simRecords != null) ? simRecords.getSIMOperatorNumeric() : null;
        String strServingPlmn = mNewSS.getOperatorNumeric();
        boolean isServingPlmnInGroup = false;
        boolean isHomePlmnInGroup = false;

        if(ServiceState.RIL_REG_STATE_ROAMING == code){
            isRoaming = true;
        }


        /* ALPS00296372 */
        if((mServiceStateExt.ignoreDomesticRoaming() == true) && (isRoaming == true) && (strServingPlmn != null) &&(strHomePlmn != null))
        {
            log("ServingPlmn = "+strServingPlmn+"HomePlmn"+strHomePlmn);

            if(strHomePlmn.substring(0, 3).equals(strServingPlmn.substring(0, 3)))
            {
                log("Same MCC,don't set as roaming");
                isRoaming = false;
            }
        }

        int mccmnc = 0;

        if (mPhone.getMySimId() == PhoneConstants.GEMINI_SIM_1) {
            mccmnc = SystemProperties.getInt(TelephonyProperties.PROPERTY_ICC_OPERATOR_NUMERIC, 0);
        } else if (mPhone.getMySimId() == PhoneConstants.GEMINI_SIM_2){
            mccmnc = SystemProperties.getInt(TelephonyProperties.PROPERTY_ICC_OPERATOR_NUMERIC_2, 0);
        } else if (mPhone.getMySimId() == PhoneConstants.GEMINI_SIM_3){
            mccmnc = SystemProperties.getInt(TelephonyProperties.PROPERTY_ICC_OPERATOR_NUMERIC_3, 0);
        } else if (mPhone.getMySimId() == PhoneConstants.GEMINI_SIM_4){
            mccmnc = SystemProperties.getInt(TelephonyProperties.PROPERTY_ICC_OPERATOR_NUMERIC_4, 0);
        }
        String numeric = mNewSS.getOperatorNumeric();
        Rlog.d(LOG_TAG,"numeric:"+numeric+"mccmnc:"+mccmnc);

        isRoaming = mServiceStateExt.isRegCodeRoaming(isRoaming, mccmnc, numeric);

        /* mtk01616 ALPS00236452: check manufacturer maintained table for specific operator with multiple home PLMN id */
        if((isRoaming == true) && (strServingPlmn != null) &&(strHomePlmn != null)){
            log("strServingPlmn = "+strServingPlmn+"strHomePlmn"+strHomePlmn);

            for(int i=0; i <customEhplmn.length; i++){
                //reset flag
                isServingPlmnInGroup = false;
                isHomePlmnInGroup = false;

                //check if serving plmn or home plmn in this group
                for(int j=0; j<	customEhplmn[i].length;j++){
                    if(strServingPlmn.equals(customEhplmn[i][j])){
                        isServingPlmnInGroup = true;
                    }
                    if(strHomePlmn.equals(customEhplmn[i][j])){
                        isHomePlmnInGroup = true;
                    }
                }

                //if serving plmn and home plmn both in the same group , do NOT treat it as roaming
                if((isServingPlmnInGroup == true) && (isHomePlmnInGroup == true)){
                    isRoaming = false;
                    log("Ignore roaming");
                    break;
                }
            }
        }

        return isRoaming;

////        // 5 is  "in service -- roam"
////        return 5 == code;
    }

    /**
     * Set roaming state if operator mcc is the same as sim mcc
     * and ons is different from spn
     *
     * @param s ServiceState hold current ons
     * @return true if same operator
     */
    private boolean isSameNamedOperators(ServiceState s) {
        String spn = SystemProperties.get(TelephonyProperties.PROPERTY_ICC_OPERATOR_ALPHA, "empty");

        String onsl = s.getOperatorAlphaLong();
        String onss = s.getOperatorAlphaShort();

        boolean equalsOnsl = onsl != null && spn.equals(onsl);
        boolean equalsOnss = onss != null && spn.equals(onss);

        return currentMccEqualsSimMcc(s) && (equalsOnsl || equalsOnss);
    }

    /**
     * Compare SIM MCC with Operator MCC
     *
     * @param s ServiceState hold current ons
     * @return true if both are same
     */
    private boolean currentMccEqualsSimMcc(ServiceState s) {
        String simNumeric = SystemProperties.get(
                TelephonyProperties.PROPERTY_ICC_OPERATOR_NUMERIC, "");
        String operatorNumeric = s.getOperatorNumeric();
        boolean equalsMcc = true;

        try {
            equalsMcc = simNumeric.substring(0, 3).
                    equals(operatorNumeric.substring(0, 3));
        } catch (Exception e){
        }
        return equalsMcc;
    }

    /**
     * Do not set roaming state in case of oprators considered non-roaming.
     *
     + Can use mcc or mcc+mnc as item of config_operatorConsideredNonRoaming.
     * For example, 302 or 21407. If mcc or mcc+mnc match with operator,
     * don't set roaming state.
     *
     * @param s ServiceState hold current ons
     * @return false for roaming state set
     */
    private boolean isOperatorConsideredNonRoaming(ServiceState s) {
        String operatorNumeric = s.getOperatorNumeric();
        String[] numericArray = mPhone.getContext().getResources().getStringArray(
                    com.android.internal.R.array.config_operatorConsideredNonRoaming);

        if (numericArray.length == 0 || operatorNumeric == null)
            return false;

        for (String numeric : numericArray) {
            if (operatorNumeric.startsWith(numeric))
                return true;
        }
        return false;
    }

    private boolean isSameNamedOperatorConsideredRoaming(ServiceState s) {
        String operatorNumeric = s.getOperatorNumeric();
        String[] numericArray = mPhone.getContext().getResources().getStringArray(
                    com.android.internal.R.array.config_sameNamedOperatorConsideredRoaming);

        if (numericArray.length == 0 || operatorNumeric == null)
            return false;

        for (String numeric : numericArray) {
            if (operatorNumeric.startsWith(numeric))
                return true;
            else
                return false;
        }
        return false;
    }

    /**
     * @return The current GPRS state. IN_SERVICE is the same as "attached"
     * and OUT_OF_SERVICE is the same as detached.
     */
    @Override
    public int getCurrentDataConnectionState() {
        return mSS.getDataRegState();
    }

    /**
     * @return true if phone is camping on a technology (eg UMTS)
     * that could support voice and data simultaneously.
     */
    @Override
    public boolean isConcurrentVoiceAndDataAllowed() {
        // return (mRilRadioTechnology >= ServiceState.RIL_RADIO_TECHNOLOGY_UMTS);
        boolean isAllowed = (mSS.getRilVoiceRadioTechnology() >= ServiceState.RIL_RADIO_TECHNOLOGY_UMTS);

        // M: Check peer phone is in call or not
        if (FeatureOption.MTK_GEMINI_SUPPORT &&
                !PhoneFactory.isDualTalkMode()) {
            for (int i=PhoneConstants.GEMINI_SIM_1; i<PhoneConstants.GEMINI_SIM_NUM; i++) {
                if (i != mPhone.getMySimId() && mPhone.getPeerPhones(i) != null && 
                        mPhone.getPeerPhones(i).getState() != PhoneConstants.State.IDLE) {
                    if (DBG) log("isConcurrentVoiceAndDataAllowed(): Phone" + i + " is in call");
                    isAllowed = false;
                    break;
                }
            }
        }
        return isAllowed;
    }

    /**
     * @return the current cell location information. Prefer Gsm location
     * information if available otherwise return LTE location information
     */
    public CellLocation getCellLocation() {
        if ((mCellLoc.getLac() >= 0) && (mCellLoc.getCid() >= 0)) {
            if (DBG) log("getCellLocation(): X good mCellLoc=" + mCellLoc);
            return mCellLoc;
        } else {
            List<CellInfo> result = getAllCellInfo();
            if (result != null) {
                // A hack to allow tunneling of LTE information via GsmCellLocation
                // so that older Network Location Providers can return some information
                // on LTE only networks, see bug 9228974.
                //
                // We'll search the return CellInfo array preferring GSM/WCDMA
                // data, but if there is none we'll tunnel the first LTE information
                // in the list.
                //
                // The tunnel'd LTE information is returned as follows:
                //   LAC = TAC field
                //   CID = CI field
                //   PSC = 0.
                GsmCellLocation cellLocOther = new GsmCellLocation();
                for (CellInfo ci : result) {
                    if (ci instanceof CellInfoGsm) {
                        CellInfoGsm cellInfoGsm = (CellInfoGsm)ci;
                        CellIdentityGsm cellIdentityGsm = cellInfoGsm.getCellIdentity();
                        cellLocOther.setLacAndCid(cellIdentityGsm.getLac(),
                                cellIdentityGsm.getCid());
                        cellLocOther.setPsc(cellIdentityGsm.getPsc());
                        if (DBG) log("getCellLocation(): X ret GSM info=" + cellLocOther);
                        return cellLocOther;
                    } else if (ci instanceof CellInfoWcdma) {
                        CellInfoWcdma cellInfoWcdma = (CellInfoWcdma)ci;
                        CellIdentityWcdma cellIdentityWcdma = cellInfoWcdma.getCellIdentity();
                        cellLocOther.setLacAndCid(cellIdentityWcdma.getLac(),
                                cellIdentityWcdma.getCid());
                        cellLocOther.setPsc(cellIdentityWcdma.getPsc());
                        if (DBG) log("getCellLocation(): X ret WCDMA info=" + cellLocOther);
                        return cellLocOther;
                    } else if ((ci instanceof CellInfoLte) &&
                            ((cellLocOther.getLac() < 0) || (cellLocOther.getCid() < 0))) {
                        // We'll return the first good LTE info we get if there is no better answer
                        CellInfoLte cellInfoLte = (CellInfoLte)ci;
                        CellIdentityLte cellIdentityLte = cellInfoLte.getCellIdentity();
                        if ((cellIdentityLte.getTac() != Integer.MAX_VALUE)
                                && (cellIdentityLte.getCi() != Integer.MAX_VALUE)) {
                            cellLocOther.setLacAndCid(cellIdentityLte.getTac(),
                                    cellIdentityLte.getCi());
                            cellLocOther.setPsc(0);
                            if (DBG) {
                                log("getCellLocation(): possible LTE cellLocOther=" + cellLocOther);
                            }
                        }
                    }
                }
                if (DBG) {
                    log("getCellLocation(): X ret best answer cellLocOther=" + cellLocOther);
                }
                return cellLocOther;
            } else {
                if (DBG) {
                    log("getCellLocation(): X empty mCellLoc and CellInfo mCellLoc=" + mCellLoc);
                }
                return mCellLoc;
            }
        }
    }

    /**
     * nitzReceiveTime is time_t that the NITZ time was posted
     */
    private void setTimeFromNITZString (String nitz, long nitzReceiveTime) {
        // "yy/mm/dd,hh:mm:ss(+/-)tz"
        // tz is in number of quarter-hours

        long start = SystemClock.elapsedRealtime();
        if (DBG) {log("NITZ: " + nitz + "," + nitzReceiveTime +
                        " start=" + start + " delay=" + (start - nitzReceiveTime));
        }

        try {
            /* NITZ time (hour:min:sec) will be in UTC but it supplies the timezone
             * offset as well (which we won't worry about until later) */
            Calendar c = Calendar.getInstance(TimeZone.getTimeZone("GMT"));

            c.clear();
            c.set(Calendar.DST_OFFSET, 0);

            String[] nitzSubs = nitz.split("[/:,+-]");

            int year = 2000 + Integer.parseInt(nitzSubs[0]);
            c.set(Calendar.YEAR, year);

            // month is 0 based!
            int month = Integer.parseInt(nitzSubs[1]) - 1;
            c.set(Calendar.MONTH, month);

            int date = Integer.parseInt(nitzSubs[2]);
            c.set(Calendar.DATE, date);

            int hour = Integer.parseInt(nitzSubs[3]);
            c.set(Calendar.HOUR, hour);

            int minute = Integer.parseInt(nitzSubs[4]);
            c.set(Calendar.MINUTE, minute);

            int second = Integer.parseInt(nitzSubs[5]);
            c.set(Calendar.SECOND, second);

            boolean sign = (nitz.indexOf('-') == -1);

            int tzOffset = Integer.parseInt(nitzSubs[6]);

            //MTK-START [ALPS00540036]
            int dst = (nitzSubs.length >= 8 ) ? Integer.parseInt(nitzSubs[7])
                                              :getDstForMcc(getMobileCountryCode(), c.getTimeInMillis());
            
            //int dst = (nitzSubs.length >= 8 ) ? Integer.parseInt(nitzSubs[7])
            //                                  : 0;
            //MTK-END [ALPS00540036]

            // The zone offset received from NITZ is for current local time,
            // so DST correction is already applied.  Don't add it again.
            //
            // tzOffset += dst * 4;
            //
            // We could unapply it if we wanted the raw offset.

            tzOffset = (sign ? 1 : -1) * tzOffset * 15 * 60 * 1000;

            TimeZone    zone = null;

            // As a special extension, the Android emulator appends the name of
            // the host computer's timezone to the nitz string. this is zoneinfo
            // timezone name of the form Area!Location or Area!Location!SubLocation
            // so we need to convert the ! into /
            if (nitzSubs.length >= 9) {
                String  tzname = nitzSubs[8].replace('!','/');
                zone = TimeZone.getTimeZone( tzname );
                log("[NITZ] setTimeFromNITZString,tzname:"+tzname+"zone:"+zone);
            }

            String iso;
            if (mSimId == PhoneConstants.GEMINI_SIM_1) {
                iso = SystemProperties.get(TelephonyProperties.PROPERTY_OPERATOR_ISO_COUNTRY);
            } else if (mSimId == PhoneConstants.GEMINI_SIM_2){
                iso = SystemProperties.get(TelephonyProperties.PROPERTY_OPERATOR_ISO_COUNTRY_2);
            } else if (mSimId == PhoneConstants.GEMINI_SIM_3){
                iso = SystemProperties.get(TelephonyProperties.PROPERTY_OPERATOR_ISO_COUNTRY_3);
            } else {
                iso = SystemProperties.get(TelephonyProperties.PROPERTY_OPERATOR_ISO_COUNTRY_4);
            }
            log("[NITZ] setTimeFromNITZString,mGotCountryCode:"+mGotCountryCode);

            if (zone == null) {

                if (mGotCountryCode) {
                    if (iso != null && iso.length() > 0) {
                        zone = TimeUtils.getTimeZone(tzOffset, dst != 0,
                                c.getTimeInMillis(),
                                iso);
                    } else {
                        // We don't have a valid iso country code.  This is
                        // most likely because we're on a test network that's
                        // using a bogus MCC (eg, "001"), so get a TimeZone
                        // based only on the NITZ parameters.
                        zone = getNitzTimeZone(tzOffset, (dst != 0), c.getTimeInMillis());
                    }
                }
            }

            if ((zone == null) || (mZoneOffset != tzOffset) || (mZoneDst != (dst != 0))){
                // We got the time before the country or the zone has changed
                // so we don't know how to identify the DST rules yet.  Save
                // the information and hope to fix it up later.

                mNeedFixZoneAfterNitz = true;
                mZoneOffset  = tzOffset;
                mZoneDst     = dst != 0;
                mZoneTime    = c.getTimeInMillis();
            }

            if (zone != null) {
                if (getAutoTimeZone()) {
                    setAndBroadcastNetworkSetTimeZone(zone.getID());
                }
                saveNitzTimeZone(zone.getID());
            }

            String ignore = SystemProperties.get("gsm.ignore-nitz");
            if (ignore != null && ignore.equals("yes")) {
                log("NITZ: Not setting clock because gsm.ignore-nitz is set");
                return;
            }

            try {
                mWakeLock.acquire();

                if (getAutoTime()) {
                    long millisSinceNitzReceived
                            = SystemClock.elapsedRealtime() - nitzReceiveTime;

                    if (millisSinceNitzReceived < 0) {
                        // Sanity check: something is wrong
                        if (DBG) {
                            log("NITZ: not setting time, clock has rolled "
                                            + "backwards since NITZ time was received, "
                                            + nitz);
                        }
                        return;
                    }

                    if (millisSinceNitzReceived > Integer.MAX_VALUE) {
                        // If the time is this far off, something is wrong > 24 days!
                        if (DBG) {
                            log("NITZ: not setting time, processing has taken "
                                        + (millisSinceNitzReceived / (1000 * 60 * 60 * 24))
                                        + " days");
                        }
                        return;
                    }

                    // Note: with range checks above, cast to int is safe
                    c.add(Calendar.MILLISECOND, (int)millisSinceNitzReceived);

                    if (DBG) {
                        log("NITZ: Setting time of day to " + c.getTime()
                            + " NITZ receive delay(ms): " + millisSinceNitzReceived
                            + " gained(ms): "
                            + (c.getTimeInMillis() - System.currentTimeMillis())
                            + " from " + nitz);
                    }

                    setAndBroadcastNetworkSetTime(c.getTimeInMillis());
                    Rlog.i(LOG_TAG, "NITZ: after Setting time of day");
                }

                /* Originally, no user to check Android defined property gsm.nitz.time. 
                                 But now Settings need to check if we ever receive NITZ from NW via this property.
                                 This is treated as a only hint to know "if the network support NITZ or not" */				
                if(mSimId == PhoneConstants.GEMINI_SIM_1)
                    SystemProperties.set("gsm.nitz.time", String.valueOf(c.getTimeInMillis()));
                else if(mSimId == PhoneConstants.GEMINI_SIM_2)
                    SystemProperties.set("gsm.nitz.time.2", String.valueOf(c.getTimeInMillis()));
	
                saveNitzTime(c.getTimeInMillis());
                if (DBG) {
                    long end = SystemClock.elapsedRealtime();
                    log("NITZ: end=" + end + " dur=" + (end - start));
                }
                mNitzUpdatedTime = true;
            } finally {
                mWakeLock.release();
            }
        } catch (RuntimeException ex) {
            loge("NITZ: Parsing NITZ time " + nitz + " ex=" + ex);
        }
    }

    private boolean getAutoTime() {
        try {
            return Settings.Global.getInt(mPhone.getContext().getContentResolver(),
                    Settings.Global.AUTO_TIME) > 0;
        } catch (SettingNotFoundException snfe) {
            return true;
        }
    }

    private boolean getAutoTimeZone() {
        try {
            return Settings.Global.getInt(mPhone.getContext().getContentResolver(),
                    Settings.Global.AUTO_TIME_ZONE) > 0;
        } catch (SettingNotFoundException snfe) {
            return true;
        }
    }

    private void saveNitzTimeZone(String zoneId) {
        if (DBG) log("saveNitzTimeZone: zoneId=" + zoneId);
        mSavedTimeZone = zoneId;
    }

    private void saveNitzTime(long time) {
        if (DBG) log("saveNitzTime: time=" + time);
        mSavedTime = time;
        mSavedAtTime = SystemClock.elapsedRealtime();
    }

    /**
     * Set the timezone and send out a sticky broadcast so the system can
     * determine if the timezone was set by the carrier.
     *
     * @param zoneId timezone set by carrier
     */
    private void setAndBroadcastNetworkSetTimeZone(String zoneId) {
        if (DBG) log("setAndBroadcastNetworkSetTimeZone: setTimeZone=" + zoneId);
        AlarmManager alarm =
            (AlarmManager) mPhone.getContext().getSystemService(Context.ALARM_SERVICE);
        alarm.setTimeZone(zoneId);
        Intent intent = new Intent(TelephonyIntents.ACTION_NETWORK_SET_TIMEZONE);
        intent.addFlags(Intent.FLAG_RECEIVER_REPLACE_PENDING);
        intent.putExtra("time-zone", zoneId);
        mPhone.getContext().sendStickyBroadcastAsUser(intent, UserHandle.ALL);
        if (DBG) {
            log("setAndBroadcastNetworkSetTimeZone: call alarm.setTimeZone and broadcast zoneId=" +
                zoneId);
        }
    }

    /**
     * Set the time and Send out a sticky broadcast so the system can determine
     * if the time was set by the carrier.
     *
     * @param time time set by network
     */
    private void setAndBroadcastNetworkSetTime(long time) {
        if (DBG) log("setAndBroadcastNetworkSetTime: time=" + time + "ms");
        SystemClock.setCurrentTimeMillis(time);
        Intent intent = new Intent(TelephonyIntents.ACTION_NETWORK_SET_TIME);
        intent.addFlags(Intent.FLAG_RECEIVER_REPLACE_PENDING);
        intent.putExtra("time", time);
        mPhone.getContext().sendStickyBroadcastAsUser(intent, UserHandle.ALL);
    }

    private void revertToNitzTime() {
        if (Settings.Global.getInt(mPhone.getContext().getContentResolver(),
                Settings.Global.AUTO_TIME, 0) == 0) {
        	log("[NITZ]:revertToNitz,AUTO_TIME is 0");
            return;
        }
       log("[NITZ]:Reverting to NITZ: tz='" + mSavedTimeZone
                + "' mSavedTime=" + mSavedTime
                + " mSavedAtTime=" + mSavedAtTime);
        if (mSavedTime != 0 && mSavedAtTime != 0) {
            /*ALPS00449624 : remove revert time zone , it's moved to revertToNitzTimeZone() */
            setAndBroadcastNetworkSetTime(mSavedTime
                    + (SystemClock.elapsedRealtime() - mSavedAtTime));
        }
    }

    private void revertToNitzTimeZone() {
        if (Settings.Global.getInt(mPhone.getContext().getContentResolver(),
                Settings.Global.AUTO_TIME_ZONE, 0) == 0) {
            return;
        }

        //MTK-add-start [ALPS01267367] for update timezone MCC/MNC
        fixTimeZone();
        //MTK-add-end [ALPS01262709]

        if (DBG) log("Reverting to NITZ TimeZone: tz='" + mSavedTimeZone);
        if (mSavedTimeZone != null) {
            setAndBroadcastNetworkSetTimeZone(mSavedTimeZone);
        }
    }


    /**
     * Post a notification to NotificationManager for network reject cause
     *
     * @param cause
     */
    private void setRejectCauseNotification(int cause) {

        if (DBG) log("setRejectCauseNotification: create notification " + cause);

//toast notification sample code
/*
        Context context = phone.getContext();
        CharSequence text = "";
        int duration = Toast.LENGTH_LONG;

        switch (cause) {
            case 2:
                text = context.getText(com.mediatek.R.string.MMRejectCause2);;
                break;
            case 3:
                text = context.getText(com.mediatek.R.string.MMRejectCause3);;
                break;
            case 5:
                text = context.getText(com.mediatek.R.string.MMRejectCause5);;
                break;
            case 6:
                text = context.getText(com.mediatek.R.string.MMRejectCause6);;
                break;
            default:
                break;
        }

        Toast toast = Toast.makeText(context, text, duration);
        toast.show();
*/
//status notification

        Context context = mPhone.getContext();

        mNotification = new Notification();
        mNotification.when = System.currentTimeMillis();
        mNotification.flags = Notification.FLAG_AUTO_CANCEL;
        mNotification.icon = com.android.internal.R.drawable.stat_sys_warning;
        Intent intent = new Intent();
        mNotification.contentIntent = PendingIntent
        .getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);

        CharSequence details = "";
        CharSequence title = context.getText(com.mediatek.R.string.RejectCauseTitle);
        int notificationId = REJECT_NOTIFICATION;

		if (FeatureOption.MTK_GEMINI_SUPPORT) {
		log("show name log");
        	if (FeatureOption.MTK_GEMINI_ENHANCEMENT == true) {
        		SIMInfo siminfo = SIMInfo.getSIMInfoBySlot(mPhone.getContext(),mSimId);
        		if (siminfo != null){
        			mNotification.simId = siminfo.mSimId;
        			mNotification.simInfoType = 3;
				if (mSimId != PhoneConstants.GEMINI_SIM_1){
					notificationId = REJECT_NOTIFICATION_2;
				}
        		}
        	}else {
			log("show sim1 log");
        		if (mSimId == PhoneConstants.GEMINI_SIM_1) {
        			title = "SIM1-" + context.getText(com.android.internal.R.string.RestrictedChangedTitle);
        		} else {
        			notificationId = REJECT_NOTIFICATION_2;
        			title = "SIM2-" + context.getText(com.android.internal.R.string.RestrictedChangedTitle);
        		}
            }
        }

        switch (cause) {
            case 2:
                details = context.getText(com.mediatek.R.string.MMRejectCause2);;
                break;
            case 3:
                details = context.getText(com.mediatek.R.string.MMRejectCause3);;
                break;
            case 5:
                details = context.getText(com.mediatek.R.string.MMRejectCause5);;
                break;
            case 6:
                details = context.getText(com.mediatek.R.string.MMRejectCause6);;
                break;
            //[ALPS00435948] MTK ADD-START
            case 13:
                details = context.getText(R.string.MMRejectCause13);
                break;
            //[ALPS00435948] MTK ADD-END
            default:
                break;
        }

        if (DBG) log("setRejectCauseNotification: put notification " + title + " / " +details);
        mNotification.tickerText = title;
        mNotification.setLatestEventInfo(context, title, details,
                mNotification.contentIntent);

        NotificationManager notificationManager = (NotificationManager)
            context.getSystemService(Context.NOTIFICATION_SERVICE);

        notificationManager.notify(notificationId, mNotification);
    }


    /**
     * Post a notification to NotificationManager for restricted state
     *
     * @param notifyType is one state of PS/CS_*_ENABLE/DISABLE
     */
    private void setNotification(int notifyType) {
    /* ALPS00339508 :Remove restricted access change notification */
/*
        if (DBG) log("setNotification: create notification " + notifyType);
        Context context = mPhone.getContext();

        mNotification = new Notification();
        mNotification.when = System.currentTimeMillis();
        mNotification.flags = Notification.FLAG_AUTO_CANCEL;
        mNotification.icon = com.android.internal.R.drawable.stat_sys_warning;
        Intent intent = new Intent();
        mNotification.contentIntent = PendingIntent
        .getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);

        CharSequence details = "";
        CharSequence title = context.getText(com.android.internal.R.string.RestrictedChangedTitle);
        int notificationId = CS_NOTIFICATION;
        if (FeatureOption.MTK_GEMINI_SUPPORT && mSimId == PhoneConstants.GEMINI_SIM_2) {
            notificationId = CS_NOTIFICATION_2;
        }

        if (FeatureOption.MTK_GEMINI_SUPPORT) {
            log("show name log");
            if (FeatureOption.MTK_GEMINI_ENHANCEMENT == true) {
                SIMInfo siminfo = SIMInfo.getSIMInfoBySlot(phone.getContext(),mSimId);
                if (siminfo != null){
                    mNotification.simId = siminfo.mSimId;
                    mNotification.simInfoType = 3;
                    if (mSimId != PhoneConstants.GEMINI_SIM_1){
                        notificationId = CS_NOTIFICATION_2;
                    }
                }
             }else {
                 log("show sim1 log");
                 if (mSimId == PhoneConstants.GEMINI_SIM_1) {
                     //title = context.getText(com.mediatek.R.string.RestrictedChangedTitle_SIM1);
                     title = "SIM1-" + context.getText(com.android.internal.R.string.RestrictedChangedTitle);
                 } else {
                     notificationId = CS_NOTIFICATION_2;
                     //title = context.getText(com.mediatek.R.string.RestrictedChangedTitle_SIM2);
                     title = "SIM2-" + context.getText(com.android.internal.R.string.RestrictedChangedTitle);
                 }
            }
        }

        switch (notifyType) {
        case PS_ENABLED:
            if (FeatureOption.MTK_GEMINI_SUPPORT && mSimId == PhoneConstants.GEMINI_SIM_2) {
                notificationId = PS_NOTIFICATION_2;
            } else {
            	notificationId = PS_NOTIFICATION;
            }
            details = context.getText(com.android.internal.R.string.RestrictedOnData);
            break;
        case PS_DISABLED:
            if (FeatureOption.MTK_GEMINI_SUPPORT && mSimId == PhoneConstants.GEMINI_SIM_2) {
                notificationId = PS_NOTIFICATION_2;
            } else {
            	notificationId = PS_NOTIFICATION;
            }
            break;
        case CS_ENABLED:
            details = context.getText(com.android.internal.R.string.RestrictedOnAllVoice);
            break;
        case CS_NORMAL_ENABLED:
            details = context.getText(com.android.internal.R.string.RestrictedOnNormal);
            break;
        case CS_EMERGENCY_ENABLED:
            details = context.getText(com.android.internal.R.string.RestrictedOnEmergency);
            break;
        case CS_DISABLED:
            // do nothing and cancel the notification later
            break;
        }

        if (DBG) log("setNotification: put notification " + title + " / " +details);
        mNotification.tickerText = title;
        mNotification.setLatestEventInfo(context, title, details,
                mNotification.contentIntent);

        NotificationManager notificationManager = (NotificationManager)
            context.getSystemService(Context.NOTIFICATION_SERVICE);

        //if (notifyType == PS_DISABLED || notifyType == CS_DISABLED) {
        //this is a temp solution from GB for resolving restricted mode notification problem (not to notify PS restricted)
        if (notifyType == PS_DISABLED || notifyType == CS_DISABLED || notifyType == PS_ENABLED) {
            // cancel previous post notification
            notificationManager.cancel(notificationId);
        } else {
            // update restricted state notification
            if (FeatureOption.MTK_GEMINI_SUPPORT && notifyType == PS_ENABLED) {
                //since we do not have to notice user that PS restricted
                //if default data SIM is not set to current PS restricted SIM
                //or it is in air plane mode or radio power is off
                int airplaneMode = Settings.Global.getInt(context.getContentResolver(), Settings.Global.AIRPLANE_MODE_ON, 0);
                int dualSimMode = Settings.System.getInt(context.getContentResolver(), Settings.System.DUAL_SIM_MODE_SETTING, 0);
                long dataSimID = Settings.System.getLong(context.getContentResolver(), Settings.System.GPRS_CONNECTION_SIM_SETTING, Settings.System.DEFAULT_SIM_NOT_SET);
                int dataSimSlot = SIMInfo.getSlotById(context, dataSimID);
                if (dataSimSlot == mSimId) {
                    if (airplaneMode != 0)
                        log("set notification but air plane mode, skip");
                    else if (phone.isSimInsert() && !((dualSimMode & (mSimId + 1)) == 0))
                        notificationManager.notify(notificationId, mNotification);
                    else
                        log("set notification but sim radio power off, skip");
                } else {
                    log("set notification but not data enabled SIM, skip");
                }
            } else {
                notificationManager.notify(notificationId, mNotification);
            }
        }
		*/
    }

    // ALPS00297554
    public void resetNotification() {
        int notificationId = CS_NOTIFICATION;
        if (mSimId == PhoneConstants.GEMINI_SIM_2)
            notificationId = CS_NOTIFICATION_2;

        Context context = mPhone.getContext();
        NotificationManager notificationManager = (NotificationManager)context.getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.cancel(notificationId);
    }

    @Override
    protected void onUpdateIccAvailability() {
        if (mUiccController == null ) {
            return;
        }

        UiccCardApplication newUiccApplication =
                mUiccController.getUiccCardApplication(UiccController.APP_FAM_3GPP);

        if (mUiccApplcation != newUiccApplication) {
            if (mUiccApplcation != null) {
                log("Removing stale icc objects.");
                mUiccApplcation.unregisterForReady(this);
                if (mIccRecords != null) {
                    mIccRecords.unregisterForRecordsLoaded(this);
                }
                mIccRecords = null;
                mUiccApplcation = null;
            }
            if (newUiccApplication != null) {
                log("New card found");
                mUiccApplcation = newUiccApplication;
                mIccRecords = mUiccApplcation.getIccRecords();
                mUiccApplcation.registerForReady(this, EVENT_SIM_READY, null);
                if (mIccRecords != null) {
                    mIccRecords.registerForRecordsLoaded(this, EVENT_SIM_RECORDS_LOADED, null);
                }
            }
        }
    }


    @Override
    protected void log(String s) {
        Rlog.d(LOG_TAG, "[GsmSST" + mSimId + "] " + s);
    }

    @Override
    protected void loge(String s) {
        Rlog.e(LOG_TAG, "[GsmSST" + mSimId + "] " + s);
    }

    @Override
    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        pw.println("GsmServiceStateTracker extends:");
        super.dump(fd, pw, args);
        pw.println(" mPhone=" + mPhone);
        pw.println(" mSS=" + mSS);
        pw.println(" mNewSS=" + mNewSS);
        pw.println(" mCellLoc=" + mCellLoc);
        pw.println(" mNewCellLoc=" + mNewCellLoc);
        pw.println(" mPreferredNetworkType=" + mPreferredNetworkType);
        pw.println(" mMaxDataCalls=" + mMaxDataCalls);
        pw.println(" mNewMaxDataCalls=" + mNewMaxDataCalls);
        pw.println(" mReasonDataDenied=" + mReasonDataDenied);
        pw.println(" mNewReasonDataDenied=" + mNewReasonDataDenied);
        pw.println(" mGsmRoaming=" + mGsmRoaming);
        pw.println(" mDataRoaming=" + mDataRoaming);
        pw.println(" mEmergencyOnly=" + mEmergencyOnly);
        pw.println(" mNeedFixZoneAfterNitz=" + mNeedFixZoneAfterNitz);
        pw.println(" mZoneOffset=" + mZoneOffset);
        pw.println(" mZoneDst=" + mZoneDst);
        pw.println(" mZoneTime=" + mZoneTime);
        pw.println(" mGotCountryCode=" + mGotCountryCode);
        pw.println(" mNitzUpdatedTime=" + mNitzUpdatedTime);
        pw.println(" mSavedTimeZone=" + mSavedTimeZone);
        pw.println(" mSavedTime=" + mSavedTime);
        pw.println(" mSavedAtTime=" + mSavedAtTime);
        //MTK-START [ALPS415367]For MR1 Migration
        //pw.println(" mNeedToRegForSimLoaded=" + mNeedToRegForSimLoaded);
        //MTK-END [ALPS415367]For MR1 Migration
        pw.println(" mStartedGprsRegCheck=" + mStartedGprsRegCheck);
        pw.println(" mReportedGprsNoReg=" + mReportedGprsNoReg);
        pw.println(" mNotification=" + mNotification);
        pw.println(" mWakeLock=" + mWakeLock);
        pw.println(" mCurSpn=" + mCurSpn);
        pw.println(" mCurShowSpn=" + mCurShowSpn);
        pw.println(" mCurPlmn=" + mCurPlmn);
        //MTK-START [ALPS415367]For MR1 Migration
        //pw.println(" curSpnRule=" + curSpnRule);
        //MTK-END [ALPS415367]For MR1 Migration
		pw.println(" mCurShowPlmn=" + mCurShowPlmn);
    }

//MTK-START [mtk03851][111124]
    public void setRadioPowerOn() {
        // system setting property AIRPLANE_MODE_ON is set in Settings.
        int airplaneMode = Settings.Global.getInt(
                mPhone.getContext().getContentResolver(),
                Settings.Global.AIRPLANE_MODE_ON, 0);
        /* for consistent UI ,SIM Management for single sim project START */
        if (!FeatureOption.MTK_GEMINI_SUPPORT) {
            int simMode = Settings.System.getInt(mPhone.getContext().getContentResolver(), Settings.System.DUAL_SIM_MODE_SETTING, 1);
            /* ALPS00447303 */
            Rlog.e(LOG_TAG, "Set mDesiredPowerState in setRadioPowerOn. simMode="+simMode+",airplaneMode="+airplaneMode);
            mDesiredPowerState = (simMode > 0) && (! (airplaneMode > 0));                        			
        }
        /* for consistent UI ,SIM Management for single sim project  END*/
        else {
            mDesiredPowerState = ! (airplaneMode > 0);
        }			
        Rlog.e(LOG_TAG, "Final mDesiredPowerState in setRadioPowerOn. [" + mDesiredPowerState + "] airplaneMode=" + airplaneMode);

        //since this will trigger radio power on
        //we should reset first radio change here
        mFirstRadioChange = true;

        log("setRadioPowerOn mDesiredPowerState " + mDesiredPowerState);
        mCi.setRadioPowerOn(null);
    }

    public void setEverIVSR(boolean value)
    {
        log("setEverIVSR:" + value);
        mEverIVSR = value;

        /* ALPS00376525 notify IVSR start event */
        if(value == true){
            Intent intent = new Intent(TelephonyIntents.ACTION_IVSR_NOTIFY);
            intent.putExtra(TelephonyIntents.INTENT_KEY_IVSR_ACTION, "start");
            intent.putExtra(PhoneConstants.GEMINI_SIM_ID_KEY, mSimId);

            if (!FeatureOption.MTK_GEMINI_SUPPORT) {
                intent.addFlags(Intent.FLAG_RECEIVER_REPLACE_PENDING);
            }

            log("broadcast ACTION_IVSR_NOTIFY intent");

            mPhone.getContext().sendStickyBroadcastAsUser(intent, UserHandle.ALL);
        }
    }

    public void setAutoGprsAttach(int auto) {
        mAutoGprsAttach = auto;
    }

    public void setPsConnType(int type) {
        log("setPsConnType:" + type);
        removeGprsConnTypeRetry();
        if (FeatureOption.MTK_GEMINI_SUPPORT) {
            DcTrackerBase dcTracker = mPhone.mDcTracker;
            if (type == 0) {
                // Not Gprs Attach (set mMasterDataEnabled as false)
                dcTracker.setDataEnabled(false);
            } else {
                // Auto Gprs Attach then activate the default apn type's pdp context (set mMasterDataEnabled as true)
                dcTracker.setDataEnabled(true);
            }
        }
        
        gprsConnType = type;
        mCi.setGprsConnType(type, obtainMessage(EVENT_SET_GPRS_CONN_TYPE_DONE, null));
    }

    private int updateAllOpertorInfo(String plmn){
        if(plmn!=null){
            mSS.setOperatorAlphaLong(plmn);

            if (mSimId == PhoneConstants.GEMINI_SIM_1) {
                Rlog.d(LOG_TAG, "setOperatorAlphaLong and update PROPERTY_OPERATOR_ALPHA to"+mSS.getOperatorAlphaLong());
                mPhone.setSystemProperty(TelephonyProperties.PROPERTY_OPERATOR_ALPHA, mSS.getOperatorAlphaLong());
            } else if (mSimId == PhoneConstants.GEMINI_SIM_2){
                Rlog.d(LOG_TAG, "setOperatorAlphaLong and update PROPERTY_OPERATOR_ALPHA_2 to"+mSS.getOperatorAlphaLong());
                mPhone.setSystemProperty(TelephonyProperties.PROPERTY_OPERATOR_ALPHA_2, mSS.getOperatorAlphaLong());
            } else if (mSimId == PhoneConstants.GEMINI_SIM_3){
                Rlog.d(LOG_TAG, "setOperatorAlphaLong and update PROPERTY_OPERATOR_ALPHA_3 to"+mSS.getOperatorAlphaLong());				
                mPhone.setSystemProperty(TelephonyProperties.PROPERTY_OPERATOR_ALPHA_3, mSS.getOperatorAlphaLong());                
            } else if (mSimId == PhoneConstants.GEMINI_SIM_4){
                Rlog.d(LOG_TAG, "setOperatorAlphaLong and update PROPERTY_OPERATOR_ALPHA_4 to"+mSS.getOperatorAlphaLong());				
                mPhone.setSystemProperty(TelephonyProperties.PROPERTY_OPERATOR_ALPHA_4, mSS.getOperatorAlphaLong());                
            }
        }
        return 1;
    }

    public void refreshSpnDisplay() {
        String numeric = mSS.getOperatorNumeric();
        String newAlphaLong = null;
        String newAlphaShort = null;
        boolean force = false;

        if (numeric != null) {
            newAlphaLong = mCi.lookupOperatorName(numeric, true);
            newAlphaShort = mCi.lookupOperatorName(numeric, false);
	     if (mSimId == PhoneConstants.GEMINI_SIM_1) {
                mPhone.setSystemProperty(TelephonyProperties.PROPERTY_OPERATOR_ALPHA, newAlphaLong);
            } else if(mSimId == PhoneConstants.GEMINI_SIM_2){
                mPhone.setSystemProperty(TelephonyProperties.PROPERTY_OPERATOR_ALPHA_2, newAlphaLong);
            } else if(mSimId == PhoneConstants.GEMINI_SIM_3){
                mPhone.setSystemProperty(TelephonyProperties.PROPERTY_OPERATOR_ALPHA_3, newAlphaLong);                
            } else if(mSimId == PhoneConstants.GEMINI_SIM_4){
                mPhone.setSystemProperty(TelephonyProperties.PROPERTY_OPERATOR_ALPHA_4, newAlphaLong);                
            }
        } else {
            force = true;
        }

        Rlog.d(LOG_TAG, "refreshSpnDisplay set mSimId=" +mSimId+","+newAlphaLong +","+newAlphaShort+","+numeric);

        mSS.setOperatorName(newAlphaLong, newAlphaShort, numeric);
        updateSpnDisplay(force);
    }

    protected void updateSpnDisplay() {
        updateSpnDisplay(false);
    }

    protected void updateSpnDisplay(boolean forceUpdate) {
        //SIMRecords simRecords = (SIMRecords)(mPhone.mIccRecords.get());
        SIMRecords simRecords = null;
    	  IccRecords r = mPhone.mIccRecords.get();
    	  if (r != null) {
            simRecords = (SIMRecords)r;
    	  }

        //int rule = simRecords.getDisplayRule(ss.getOperatorNumeric());
        int rule = (simRecords != null) ? simRecords.getDisplayRule(mSS.getOperatorNumeric()) : SIMRecords.SPN_RULE_SHOW_PLMN;

        //int rule = SIMRecords.SPN_RULE_SHOW_PLMN;
        String strNumPlmn = mSS.getOperatorNumeric();
        //String spn = simRecords.getServiceProviderName();
        String spn = (simRecords != null) ? simRecords.getServiceProviderName() : "";
        //String plmn = mSS.getOperatorAlphaLong();
        String sEons = null;

        //MTK-START [ALPS415367]For MR1 Migration
        boolean showPlmn = false;
        //MTK-END [ALPS415367]For MR1 Migration

        try {
            //sEons = simRecords.getEonsIfExist(ss.getOperatorNumeric(), cellLoc.getLac(), true);
            sEons = (simRecords != null) ? simRecords.getEonsIfExist(mSS.getOperatorNumeric(), mCellLoc.getLac(), true) : null;
        } catch (RuntimeException ex) {
            Rlog.e(LOG_TAG, "Exception while getEonsIfExist. ", ex);
        }

        String plmn = null;
        
        // [ALPS00460547] - add
        String realPlmn = null;        

        //MTK-START [ALPS415367]For MR1 Migration
        String mSimOperatorNumeric = (simRecords != null) ? simRecords.getSIMOperatorNumeric() : "";
        //MTK-END [ALPS415367]For MR1 Migration

        if(sEons != null) {
            plmn = sEons;
        }
        else if (strNumPlmn != null && strNumPlmn.equals(mSimOperatorNumeric)){
	     Rlog.d(LOG_TAG, "Home PLMN, get CPHS ons");
	     //plmn = simRecords.getSIMCPHSOns();
	     plmn = (simRecords != null) ? simRecords.getSIMCPHSOns() : "";
        }

        if (plmn == null || plmn.equals("")) {
	     Rlog.d(LOG_TAG, "No matched EONS and No CPHS ONS");
            plmn = mSS.getOperatorAlphaLong();
            if (plmn == null || plmn.equals(mSS.getOperatorNumeric())) {
                plmn = mSS.getOperatorAlphaShort();
            }
        }

        /*[ALPS00460547] - star */ 
        //keep operator neme for update PROPERTY_OPERATOR_ALPHA
        realPlmn = plmn;
        /*[ALPS00460547] - end */ 

        // Do not display SPN before get normal service
        //MTK-ADD Start : for CS not registered , PS regsitered (ex: LTE PS only mode or 2/3G PS only SIM card or CS domain network registeration temporary failure
        //if (mSS.getVoiceRegState() != ServiceState.STATE_IN_SERVICE) {
        if ((mSS.getVoiceRegState() != ServiceState.STATE_IN_SERVICE) &&
                (mSS.getDataRegState() != ServiceState.STATE_IN_SERVICE)) {
        //MTK-ADD END : for CS not registered , PS regsitered (ex: LTE PS only mode or 2/3G PS only SIM card or CS domain network registeration temporary failure
            //MTK-START [ALPS415367]For MR1 Migration
            showPlmn = true;
            //rule = SIMRecords.SPN_RULE_SHOW_PLMN;
            //plmn = null;
            //MTK-END [ALPS415367]For MR1 Migration
            plmn = Resources.getSystem().
                    getText(com.android.internal.R.string.lockscreen_carrier_default).toString();

        }
        //ALPS01035028 - start
        log("updateSpnDisplay mEmergencyOnly="+mEmergencyOnly+" mRadioState.isOn()="+mRadioState.isOn()+" getVoiceRegState()="+mSS.getVoiceRegState()+" getDataRegState()"+mSS.getDataRegState());
        //ALPS01035028 - end

        // ALPS00283717 For emergency calls only, pass the EmergencyCallsOnly string via EXTRA_PLMN
        //ALPS01035028 - start
        //if (mEmergencyOnly && mCi.getRadioState().isOn()) {
        //MTK-ADD START : for CS not registered , PS regsitered (ex: LTE PS only mode or 2/3G PS only SIM card or CS domain network registeration temporary failure
        if (mEmergencyOnly && mRadioState.isOn() && (mSS.getDataRegState() != ServiceState.STATE_IN_SERVICE)) {
        //if (mEmergencyOnly && mRadioState.isOn()) {
        //MTK-ADD END : for CS not registered , PS regsitered (ex: LTE PS only mode or 2/3G PS only SIM card or CS domain network registeration temporary failure
        //ALPS01035028 - end
            log("updateSpnDisplay show mEmergencyOnly");

            //MTK-START [ALPS415367]For MR1 Migration
            showPlmn = true;
            //rule = SIMRecords.SPN_RULE_SHOW_PLMN;
            //MTK-END [ALPS415367]For MR1 Migration
            /// M: for special display when no sim inserted and sim pin lock
            plmn = mServiceStateExt.getEccPlmnValue();
        }

        //MTK-START [ALPS00446163] remark
        //MTK-START [ALPS415367]For MR1 Migration		
        //if (ss.getState() == ServiceState.STATE_POWER_OFF) {
        //    plmn = null;
        //}
        //MTK-END [ALPS415367]For MR1 Migration

        /**
        * mImeiAbnormal=0, Valid IMEI
        * mImeiAbnormal=1, IMEI is null or not valid format
        * mImeiAbnormal=2, Phone1/Phone2 have same IMEI
        */
        int imeiAbnormal = mPhone.isDeviceIdAbnormal();
        if (imeiAbnormal == 1) {
            //[ALPS00872883] don't update plmn string when radio is not available
            //ALPS01035028 - start
            //if (mCi.getRadioState() != CommandsInterface.RadioState. RADIO_UNAVAILABLE) {
            if (mRadioState != CommandsInterface.RadioState.RADIO_UNAVAILABLE) {            
            //ALPS01035028 - end
                plmn = Resources.getSystem().getText(com.mediatek.R.string.invalid_imei).toString();
            }
            //MTK-START [ALPS415367]For MR1 Migration
            //rule = SIMRecords.SPN_RULE_SHOW_PLMN;
            //MTK-END [ALPS415367]For MR1 Migration
        } else if (imeiAbnormal == 2) {
            plmn = Resources.getSystem().getText(com.mediatek.R.string.same_imei).toString();
            //MTK-START [ALPS415367]For MR1 Migration
            //rule = SIMRecords.SPN_RULE_SHOW_PLMN;
            //MTK-END [ALPS415367]For MR1 Migration
        } else if (imeiAbnormal == 0) {
            // If CS not registered , PS registered , add "Data connection only" postfix in PLMN name
            if((mSS.getVoiceRegState() != ServiceState.STATE_IN_SERVICE) &&
                (mSS.getDataRegState() == ServiceState.STATE_IN_SERVICE)){                
                plmn = plmn + "(" + Resources.getSystem().getText(com.mediatek.R.string.data_conn_only).toString() +")";
            }else{            
                plmn = mServiceStateExt.onUpdateSpnDisplay(plmn, mSS.getRilVoiceRadioTechnology());
            }
        }

        /* ALPS00296298 */
        if (mIsImeiLock){
            plmn = Resources.getSystem().getText(com.mediatek.R.string.invalid_card).toString();
            //MTK-START [ALPS415367]For MR1 Migration
            //plmn = new String("Invalid Card");
            //rule = SIMRecords.SPN_RULE_SHOW_PLMN;
            //MTK-END [ALPS415367]For MR1 Migration
        }

        //MTK-START [ALPS415367]For MR1 Migration
        //MTK-ADD Start : for CS not registered , PS regsitered (ex: LTE PS only mode or 2/3G PS only SIM card or CS domain network registeration temporary failure
        //if (mSS.getVoiceRegState() == ServiceState.STATE_IN_SERVICE) {
        if ((mSS.getVoiceRegState() == ServiceState.STATE_IN_SERVICE) ||
                (mSS.getDataRegState() == ServiceState.STATE_IN_SERVICE)){
        //MTK-ADD END : for CS not registered , PS regsitered (ex: LTE PS only mode or 2/3G PS only SIM card or CS domain network registeration temporary failure
            showPlmn = !TextUtils.isEmpty(plmn) &&
                    ((rule & SIMRecords.SPN_RULE_SHOW_PLMN)
                            == SIMRecords.SPN_RULE_SHOW_PLMN);
        //MTK-START [ALPS00446163] remark
        //} else if (ss.getState() == ServiceState.STATE_POWER_OFF) {
        //    showPlmn = false;			
        }

        boolean showSpn = !TextUtils.isEmpty(spn)
                && ((rule & SIMRecords.SPN_RULE_SHOW_SPN)
                        == SIMRecords.SPN_RULE_SHOW_SPN);

        //[ALPS00446315]MTK add - START
        if (mSS.getVoiceRegState() == ServiceState.STATE_POWER_OFF) {
            showSpn = false;
            spn = null;
        }
        //[ALPS00446315]MTK add - END

        if (mServiceStateExt.needSpnRuleShowPlmnOnly() && !TextUtils.isEmpty(plmn)) {
            log("origin showSpn:" + showSpn + " showPlmn:" + showPlmn + " rule:" + rule);
            showSpn = false;
            showPlmn = true;
            rule = SIMRecords.SPN_RULE_SHOW_PLMN;
            log("op01 showSpn:" + showSpn + " showPlmn:" + showPlmn + " rule:" + rule);
        }

        //MTK-END [ALPS415367]For MR1 Migration

        //MTK-START [ALPS01276072]
        if ("OP01".equals(SystemProperties.get("ro.operator.optr"))){
            if (!TextUtils.isEmpty(plmn)) {
                log("origin showSpn:" + showSpn + " showPlmn:" + showPlmn + " rule:" + rule);
                showSpn = false;
                showPlmn = true;
                rule = SIMRecords.SPN_RULE_SHOW_PLMN;
                log("op01 showSpn:" + showSpn + " showPlmn:" + showPlmn + " rule:" + rule);
            }
        }
        //MTK-END [ALPS01276072]

        if (showPlmn != mCurShowPlmn
                || showSpn != mCurShowSpn
                || !TextUtils.equals(spn, mCurSpn)
                || !TextUtils.equals(plmn, mCurPlmn)
                || forceUpdate) {

            //MTK-START [ALPS415367]For MR1 Migration
            //boolean showSpn =
            //    (rule & SIMRecords.SPN_RULE_SHOW_SPN) == SIMRecords.SPN_RULE_SHOW_SPN;
            //boolean showPlmn =  (mEmergencyOnly ||
            //    ((rule & SIMRecords.SPN_RULE_SHOW_PLMN) == SIMRecords.SPN_RULE_SHOW_PLMN));
            //MTK-END [ALPS415367]For MR1 Migration

            // MTK-START [ALPS521030] for [CT case][TC-IRLAB-02009]
            if (!mServiceStateExt.allowSpnDisplayed()) {
                log("For CT test case don't show SPN.");
                if (rule == (SIMRecords.SPN_RULE_SHOW_PLMN | SIMRecords.SPN_RULE_SHOW_SPN)) {
                    showSpn = false;
                    spn = null;
                }
            }
            // MTK-END [ALPS521030] for [CT case][TC-IRLAB-02009]

            Intent intent = new Intent(Intents.SPN_STRINGS_UPDATED_ACTION);
            intent.putExtra(PhoneConstants.GEMINI_SIM_ID_KEY, mSimId);

            // [ALPS00125833]
            // For Gemini, share the same intent, do not replace the other one
            if (!FeatureOption.MTK_GEMINI_SUPPORT) {
                intent.addFlags(Intent.FLAG_RECEIVER_REPLACE_PENDING);
            }

            intent.putExtra(Intents.EXTRA_SHOW_SPN, showSpn);
            intent.putExtra(Intents.EXTRA_SPN, spn);
            intent.putExtra(Intents.EXTRA_SHOW_PLMN, showPlmn);
            intent.putExtra(Intents.EXTRA_PLMN, plmn);

            // Femtocell (CSG) info
            intent.putExtra(Intents.EXTRA_HNB_NAME, mHhbName);
            intent.putExtra(Intents.EXTRA_CSG_ID, mCsgId);
            intent.putExtra(Intents.EXTRA_DOMAIN, mFemtocellDomain);

            if (FeatureOption.MTK_3GDONGLE_SUPPORT) {
                mPhone.getContext().sendStickyBroadcastAsUser(intent, UserHandle.ALL);
            } else {
                mPhone.getContext().sendStickyBroadcast(intent);
            }

            /* ALPS00357573 for consistent operator name display */
            if((showSpn == true) && (showPlmn == false) &&(spn!=null)){
                /* When only <spn> is shown , we update with <spn> */
                log("updateAllOpertorInfo with spn");
                updateAllOpertorInfo(spn);
            } else {
                /*[ALPS00460547] - star */
                log("updateAllOpertorInfo with realPlmn");
                updateAllOpertorInfo(realPlmn);
                /*[ALPS00460547] - end */
            }

            log(" showSpn:" + showSpn +
                    " spn:" + spn +
                    " showPlmn:" + showPlmn +
                    " plmn:" + plmn +
                    " rule:" + rule );
        }

        //MTK-START [ALPS415367]For MR1 Migration
        //curSpnRule = rule;
        mCurShowSpn = showSpn;
        //MTK-END [ALPS415367]For MR1 Migration
        mCurShowPlmn = showPlmn;
        mCurSpn = spn;
        mCurPlmn = plmn;
    }

    public void registerForPsRegistrants(Handler h, int what, Object obj) {
        Rlog.d(LOG_TAG, "[DSAC DEB] " + "registerForCsRegistrants");
        Registrant r = new Registrant(h, what, obj);
        ratPsChangedRegistrants.add(r);
    }

    public void unregisterForPsRegistrants(Handler h) {
        ratPsChangedRegistrants.remove(h);
    }

    public void registerForRatRegistrants(Handler h, int what, Object obj) {
        Rlog.d(LOG_TAG, "[DSAC DEB] " + "registerForRatRegistrants");
        Registrant r = new Registrant(h, what, obj);
        ratCsChangedRegistrants.add(r);
    }

    public void unregisterForRatRegistrants(Handler h) {
        ratCsChangedRegistrants.remove(h);
    }

    //ALPS00248788
    private void onInvalidSimInfoReceived(AsyncResult ar) {
        String[] InvalidSimInfo = (String[]) ar.result;
        String plmn = InvalidSimInfo[0];
        int cs_invalid = Integer.parseInt(InvalidSimInfo[1]);
        int ps_invalid = Integer.parseInt(InvalidSimInfo[2]);
        int cause = Integer.parseInt(InvalidSimInfo[3]);
        int testMode = -1;

        // do NOT apply IVSR when in TEST mode
        testMode = SystemProperties.getInt("gsm.gcf.testmode", 0);
        // there is only one test mode in modem. actually it's not SIM dependent , so remove testmode2 property here

        log("onInvalidSimInfoReceived testMode:" + testMode+" cause:"+cause+" cs_invalid:"+cs_invalid+" ps_invalid:"+ps_invalid+" plmn:"+plmn+"mEverIVSR"+mEverIVSR);

        //Check UE is set to test mode or not	(CTA =1,FTA =2 , IOT=3 ...)
        if(testMode != 0){
            log("InvalidSimInfo received during test mode: "+ testMode);
            return;
        }

         //MTK-ADD Start : for CS not registered , PS regsitered (ex: LTE PS only mode or 2/3G PS only SIM card or CS domain network registeration temporary failure
         if (cs_invalid == 1) {
             isCsInvalidCard = true;
         }
         //MTK-ADD END : for CS not registered , PS regsitered (ex: LTE PS only mode or 2/3G PS only SIM card or CS domain network registeration temporary failure

        /* check if CS domain ever sucessfully registered to the invalid SIM PLMN */
        /* Integrate ALPS00286197 with MR2 data only device state update , not to apply CS domain IVSR for data only device */
        if(mVoiceCapable){
            if((cs_invalid == 1)&& (mLastRegisteredPLMN != null) && (plmn.equals(mLastRegisteredPLMN)))
            {
                log("InvalidSimInfo set TRM due to CS invalid");
                setEverIVSR(true);
                mLastRegisteredPLMN = null;
                mLastPSRegisteredPLMN = null;
                mPhone.setTRM(3, null);
                return;
            }
        }

        /* check if PS domain ever sucessfully registered to the invalid SIM PLMN */
        if((ps_invalid == 1)&& (mLastPSRegisteredPLMN != null) && (plmn.equals(mLastPSRegisteredPLMN)))
        {
            log("InvalidSimInfo set TRM due to PS invalid ");
            setEverIVSR(true);
            mLastRegisteredPLMN = null;
            mLastPSRegisteredPLMN = null;
            mPhone.setTRM(3, null);
            return;
        }

        /* ALPS00324111: to force trigger IVSR */
        /* ALPS00407923  : The following code is to "Force trigger IVSR even when MS never register to the network before" 
                  The code was intended to cover the scenario of "invalid SIM NW issue happen at the first network registeration during boot-up". 
                  However, it might cause false alarm IVSR ex: certain sim card only register CS domain network , but PS domain is invalid. 
                  For such sim card, MS will receive invalid SIM at the first PS domain network registeration
                  In such case , to trigger IVSR will be a false alarm,which will cause CS domain network registeration time longer (due to IVSR impact)
                  It's a tradeoff. Please think about the false alarm impact before using the code below.*/
    /*
        if ((mEverIVSR == false) && (gprsState != ServiceState.STATE_IN_SERVICE) &&(mSS.getVoiceRegState() != ServiceState.STATE_IN_SERVICE))
        {
            log("InvalidSimInfo set TRM due to never set IVSR");
            setEverIVSR(true);
            mLastRegisteredPLMN = null;
            mLastPSRegisteredPLMN = null;
            phone.setTRM(3, null);
            return;
        }
        */	

    }

    public void removeGprsConnTypeRetry() {
        removeMessages(EVENT_SET_GPRS_CONN_RETRY);
    }
//MTK-END [mtk03851][111124]
    //MTK-START [MTK80515] [ALPS00368272]
    private void getEINFO(int eventId) {
        mPhone.invokeOemRilRequestStrings(new String[]{"AT+EINFO?","+EINFO"}, this.obtainMessage(eventId));
        log("getEINFO for EMMRRS");
    }

    private void setEINFO(int value, Message onComplete) {
        String Cmd[] = new String[2];
        Cmd[0] = "AT+EINFO=" + value;
        Cmd[1] = "+EINFO";
        mPhone.invokeOemRilRequestStrings(Cmd, onComplete);
        log("setEINFO for EMMRRS, ATCmd[0]="+Cmd[0]);
    }

    private int getDataConnectionSimId() {
        int currentDataConnectionSimId = -1;
        if (FeatureOption.MTK_GEMINI_ENHANCEMENT == true) {
            long currentDataConnectionMultiSimId =  Settings.System.getLong(mPhone.getContext().getContentResolver(), Settings.System.GPRS_CONNECTION_SIM_SETTING, Settings.System.DEFAULT_SIM_NOT_SET);
            if (currentDataConnectionMultiSimId != Settings.System.GPRS_CONNECTION_SIM_SETTING_NEVER && currentDataConnectionMultiSimId != Settings.System.DEFAULT_SIM_NOT_SET) {
                 //[ALPS01071933] for LEGO: SimInfoManager API remove -- start
                SIMInfo simInfo = SIMInfo.getSIMInfoById(mPhone.getContext(), currentDataConnectionMultiSimId);
                if (simInfo != null) {
                    currentDataConnectionSimId = simInfo.mSlot;
                } else {
                    currentDataConnectionSimId = -1; 
                }
                 //[ALPS01071933] for LEGO: SimInfoManager API remove -- end
            }
        }else {
            currentDataConnectionSimId =  Settings.System.getInt(mPhone.getContext().getContentResolver(), Settings.System.GPRS_CONNECTION_SETTING, Settings.System.GPRS_CONNECTION_SETTING_DEFAULT) - 1;
        }
        log("Default Data Setting value=" + currentDataConnectionSimId);
        return currentDataConnectionSimId;
    }
    //MTK-END [MTK80515] [ALPS00368272]

    //MTK-START [ALPS00540036]
    private int getDstForMcc(int mcc, long when) {
        int dst=0;

        if (mcc != 0){
            String tzId = MccTable.defaultTimeZoneForMcc(mcc);
            if (tzId != null) {
                TimeZone timeZone = TimeZone.getTimeZone(tzId);
                Date date = new Date(when);
                boolean isInDaylightTime=timeZone.inDaylightTime(date);
                if (isInDaylightTime){
                    dst = 1;
                    log("[NITZ] getDstForMcc: dst="+dst);
                }
            }
        }

        return dst;
    }
    
    private int getMobileCountryCode(){
        int mcc = 0;
        
        String operatorNumeric = mSS.getOperatorNumeric();
        if (operatorNumeric != null){
            try{
                mcc = Integer.parseInt(operatorNumeric.substring(0,3));
            } catch ( NumberFormatException ex){
                Rlog.w(LOG_TAG, "countryCodeForMcc error" + ex);
            } catch ( StringIndexOutOfBoundsException ex) {
                Rlog.w(LOG_TAG, "countryCodeForMcc error" + ex);
            }
        }

        return mcc;
    }
    //MTK-END [ALPS00540036]

    /*
     * M: Check to start the update oplmn mechanism.
     */
    void updateOplmn() {
        log("Prepare to check the oplmn update");
        // OPLMN Update
        if (mSimId == PhoneConstants.GEMINI_SIM_2) {
            mServiceStateExt.updateOplmn(mPhoneBase.getContext(), mCi);
        }
    }

    //MTK-START: update TimeZone by MCC/MNC
    //Find TimeZone in manufacturer maintained table for the country has multiple timezone 
    private TimeZone getTimeZonesWithCapitalCity(String iso){
        TimeZone tz = null;        
        for(int i = 0; i < mTimeZoneIdOfCapitalCity.length; i++){
            if(iso.equals(mTimeZoneIdOfCapitalCity[i][0])){                
                tz = TimeZone.getTimeZone(mTimeZoneIdOfCapitalCity[i][1]);
                log("uses TimeZone of Capital City:"+mTimeZoneIdOfCapitalCity[i][1]);
                break;
            }
        }
        return tz;
    }

    //MTK-Add-start : [ALPS01267367] fix timezone by MCC
    protected void fixTimeZone() {
        TimeZone zone=null;
        String iso = "";
        String operatorNumeric = mSS.getOperatorNumeric();        
        String mcc = null;

        if (operatorNumeric != null){
            mcc = operatorNumeric.substring(0, 3);
        } else {
            log("fixTimeZone but not registered and operatorNumeric is null");
            return;
        }

        try{
            iso = MccTable.countryCodeForMcc(Integer.parseInt(mcc));
        } catch ( NumberFormatException ex){
            loge("fixTimeZone countryCodeForMcc error" + ex);
        } 
    
        if (!mcc.equals("000") && !TextUtils.isEmpty(iso) && getAutoTimeZone()) {

            // Test both paths if ignore nitz is true
            boolean testOneUniqueOffsetPath = SystemProperties.getBoolean(
                        TelephonyProperties.PROPERTY_IGNORE_NITZ, false) &&
                            ((SystemClock.uptimeMillis() & 1) == 0);
            
            ArrayList<TimeZone> uniqueZones = TimeUtils.getTimeZonesWithUniqueOffsets(iso);
            if ((uniqueZones.size() == 1) || testOneUniqueOffsetPath) {
                zone = uniqueZones.get(0);
                if (DBG) {
                   log("fixTimeZone: no nitz but one TZ for iso-cc=" + iso +
                           " with zone.getID=" + zone.getID() +
                           " testOneUniqueOffsetPath=" + testOneUniqueOffsetPath);
                }
                setAndBroadcastNetworkSetTimeZone(zone.getID());
            //MTK-START: [ALPS01262709] update time with MCC/MNC
            //} else {
            } else if(uniqueZones.size()>1) {
                log("uniqueZones.size="+uniqueZones.size());
                zone = getTimeZonesWithCapitalCity(iso);
                setAndBroadcastNetworkSetTimeZone(zone.getID());
            //MTK-END: [ALPS01262709] update time with MCC/MNC
            } else {
                if (DBG) {
                    log("fixTimeZone: there are " + uniqueZones.size() +
                        " unique offsets for iso-cc='" + iso +
                        " testOneUniqueOffsetPath=" + testOneUniqueOffsetPath +
                        "', do nothing");
                }
            }
        }

        if (zone != null) {
            log("fixTimeZone: zone != null zone.getID=" + zone.getID());
            if (getAutoTimeZone()) {
                setAndBroadcastNetworkSetTimeZone(zone.getID());
            }
            saveNitzTimeZone(zone.getID());
        } else {
            log("fixTimeZone: zone == null");
        }        
    }
    //MTK-END:  [ALPS01262709]  update TimeZone by MCC/MNC

}
