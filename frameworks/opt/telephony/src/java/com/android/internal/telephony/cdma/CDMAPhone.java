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

package com.android.internal.telephony.cdma;

import android.app.ActivityManagerNative;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.SQLException;
import android.net.Uri;
import android.os.AsyncResult;
import android.os.Handler;
import android.os.Message;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.os.Registrant;
import android.os.RegistrantList;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.provider.Telephony;
import android.telephony.CellLocation;
import android.telephony.PhoneNumberUtils;
import android.telephony.ServiceState;
import android.telephony.cdma.CdmaCellLocation;
import android.text.TextUtils;
import android.telephony.Rlog;
import android.telephony.SignalStrength;

import com.android.internal.telephony.CallStateException;
import com.android.internal.telephony.CallTracker;
import com.android.internal.telephony.CommandException;
import com.android.internal.telephony.CommandsInterface;
import com.android.internal.telephony.Connection;
import com.android.internal.telephony.IccPhoneBookInterfaceManager;
import com.android.internal.telephony.IccSmsInterfaceManager;
import com.android.internal.telephony.MccTable;
import com.android.internal.telephony.MmiCode;
import com.android.internal.telephony.OperatorInfo;
import com.android.internal.telephony.PhoneBase;
import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.PhoneNotifier;
import com.android.internal.telephony.PhoneProxy;
import com.android.internal.telephony.PhoneSubInfo;
import com.android.internal.telephony.ServiceStateTracker;
import com.android.internal.telephony.SmsBroadcastUndelivered;
import com.android.internal.telephony.TelephonyIntents;
import com.android.internal.telephony.TelephonyProperties;
import com.android.internal.telephony.UUSInfo;
import com.android.internal.telephony.dataconnection.DcTracker;
import com.android.internal.telephony.uicc.IccException;
import com.android.internal.telephony.uicc.IccRecords;
import com.android.internal.telephony.uicc.RuimRecords;
import com.android.internal.telephony.uicc.UiccCardApplication;
import com.android.internal.telephony.uicc.UiccController;
import com.android.internal.telephony.IccCardConstants;
import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

//support by via start [ALPS00397824]
import android.os.SystemProperties;
import android.provider.Settings;
//support by via end [ALPS00397824]
import static com.android.internal.telephony.TelephonyProperties.PROPERTY_ICC_OPERATOR_ALPHA;
import static com.android.internal.telephony.TelephonyProperties.PROPERTY_ICC_OPERATOR_ISO_COUNTRY;
import static com.android.internal.telephony.TelephonyProperties.PROPERTY_ICC_OPERATOR_NUMERIC;

/// M: MTK added. @{
import static com.android.internal.telephony.TelephonyProperties.PROPERTY_ICC_OPERATOR_ALPHA_2;
import static com.android.internal.telephony.TelephonyProperties.PROPERTY_ICC_OPERATOR_ISO_COUNTRY_2;
import static com.android.internal.telephony.TelephonyProperties.PROPERTY_ICC_OPERATOR_NUMERIC_2;

import com.mediatek.common.featureoption.FeatureOption;
import static android.Manifest.permission.READ_PHONE_STATE;
import com.android.internal.telephony.cdma.PlusCodeToIddNddUtils;
// UTK start
import com.android.internal.telephony.cdma.utk.UtkService;
//UTK end   
/**
 * {@hide}
 */
public class CDMAPhone extends PhoneBase {
    static final String LOG_TAG = "CDMAPhone";
    private static final boolean DBG = true;
    private static final boolean VDBG = false; /* STOP SHIP if true */

    // Default Emergency Callback Mode exit timer
    private static final int DEFAULT_ECM_EXIT_TIMER_VALUE = 300000;

    static final String VM_COUNT_CDMA = "vm_count_key_cdma";
    private static final String VM_NUMBER_CDMA = "vm_number_key_cdma";
    private String mVmNumber = null;

    static final int RESTART_ECM_TIMER = 0; // restart Ecm timer
    static final int CANCEL_ECM_TIMER = 1; // cancel Ecm timer

    // Instance Variables
    CdmaCallTracker mCT;
    CdmaServiceStateTracker mSST;
    CdmaSubscriptionSourceManager mCdmaSSM;
    ArrayList <CdmaMmiCode> mPendingMmis = new ArrayList<CdmaMmiCode>();
    RuimPhoneBookInterfaceManager mRuimPhoneBookInterfaceManager;
    int mCdmaSubscriptionSource = CdmaSubscriptionSourceManager.SUBSCRIPTION_SOURCE_UNKNOWN;
    PhoneSubInfo mSubInfo;
    EriManager mEriManager;
    WakeLock mWakeLock;

    // mEriFileLoadedRegistrants are informed after the ERI text has been loaded
    private final RegistrantList mEriFileLoadedRegistrants = new RegistrantList();

    // mEcmTimerResetRegistrants are informed after Ecm timer is canceled or re-started
    private final RegistrantList mEcmTimerResetRegistrants = new RegistrantList();

    // mEcmExitRespRegistrant is informed after the phone has been exited
    //the emergency callback mode
    //keep track of if phone is in emergency callback mode
    private boolean mIsPhoneInEcmState;
    private Registrant mEcmExitRespRegistrant;
    private Registrant mThreeWayEcmExitRespRegistrant;
    protected String mImei;
    protected String mImeiSv;
    private String mEsn;
    private String mMeid;
    // string to define how the carrier specifies its own ota sp number
    private String mCarrierOtaSpNumSchema;
    private int mSimIndicatorState = PhoneConstants.SIM_INDICATOR_UNKNOWN;

    //via support start
    //[ALPS00384791]
    protected String mUimId;
    //[ALPS00402795]
    private boolean mIsRadioAvailable;
    static final int QUERY_UIM_INSERTED_STATUS_DELAY = 2000; 
    //VIA-START VIA AGPS
    private ViaGpsProcess mViaGpsProcess;
    //VIA-END VIA AGPS
    //via support end

    // UTK start
    private UtkService mUtkService = null;
    //UTK end

    //8.2.11 TC-IRLAB-02011 start
    private final RegistrantList mMccMncChangeRegistrants = new RegistrantList();
    private boolean mCpPauseEnable = false;
    //8.2.11 TC-IRLAB-02011 end

    // A runnable which is used to automatically exit from Ecm after a period of time.
    private Runnable mExitEcmRunnable = new Runnable() {
        @Override
        public void run() {
            exitEmergencyCallbackMode();
        }
    };

    Registrant mPostDialHandler;

    static String PROPERTY_CDMA_HOME_OPERATOR_NUMERIC = "ro.cdma.home.operator.numeric";

    // Constructors
    /// M: Modify constructor methods for sim id parameter. @{
    public CDMAPhone(Context context, CommandsInterface ci, PhoneNotifier notifier, int simId) {
        this(context, ci, notifier, false, simId);
    }
    /// @}

    public CDMAPhone(Context context, CommandsInterface ci, PhoneNotifier notifier) {
        this(context, ci, notifier, false, PhoneConstants.GEMINI_SIM_1);
    }

    public CDMAPhone(Context context, CommandsInterface ci, PhoneNotifier notifier,
            boolean unitTestMode) {
        this(context, ci, notifier, unitTestMode, PhoneConstants.GEMINI_SIM_1);
    }

    public CDMAPhone(Context context, CommandsInterface ci, PhoneNotifier notifier,
            boolean unitTestMode, int simId) {
        super("CDMA", notifier, context, ci, unitTestMode);
        mySimId = simId;
        mUiccController = UiccController.getInstance(mySimId);
        mUiccController.registerForIccChanged(this, EVENT_ICC_CHANGED, null);
        //VIA-START VIA AGPS
        if(FeatureOption.EVDO_DT_VIA_SUPPORT == true){
            mViaGpsProcess = new ViaGpsProcess(context, this,ci,mySimId);
        }
        //VIA-END VIA AGPS
        initSstIcc();
        init(context, notifier);
    }
    /// @}

    protected void initSstIcc() {
        mSST = new CdmaServiceStateTracker(this);
    }

    protected void init(Context context, PhoneNotifier notifier) {
        mCi.setPhoneType(PhoneConstants.PHONE_TYPE_CDMA);
        mCT = new CdmaCallTracker(this);
        mCdmaSSM = CdmaSubscriptionSourceManager.getInstance(context, mCi, this,
                EVENT_CDMA_SUBSCRIPTION_SOURCE_CHANGED, null);
        //VIA-START VIA AGPS
        if(FeatureOption.EVDO_DT_VIA_SUPPORT == true){
            mViaGpsProcess.start();
        }
        //VIA-END VIA AGPS
        mDcTracker = new ViaDcTracker(this);
        mRuimPhoneBookInterfaceManager = new RuimPhoneBookInterfaceManager(this);
        mSubInfo = new PhoneSubInfo(this);
        mEriManager = new EriManager(this, context, EriManager.ERI_FROM_XML);

        mCi.registerForAvailable(this, EVENT_RADIO_AVAILABLE, null);
        registerForRuimRecordEvents();
        mCi.registerForOffOrNotAvailable(this, EVENT_RADIO_OFF_OR_NOT_AVAILABLE, null);
        mCi.registerForOn(this, EVENT_RADIO_ON, null);
        mCi.setOnSuppServiceNotification(this, EVENT_SSN, null);
        mSST.registerForNetworkAttached(this, EVENT_REGISTERED_TO_NETWORK, null);
        mCi.setEmergencyCallbackMode(this, EVENT_EMERGENCY_CALLBACK_MODE_ENTER, null);
        mCi.registerForExitEmergencyCallbackMode(this, EVENT_EXIT_EMERGENCY_CALLBACK_RESPONSE,
                null);
        //8.2.11 TC-IRLAB-02011 start
        mCi.registerForMccMncChange(this, EVENT_CDMA_MCC_MNC_CHANGED, null);
        //8.2.11 TC-IRLAB-02011 end

        PowerManager pm
            = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        mWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,LOG_TAG);

        //Change the system setting
        SystemProperties.set(TelephonyProperties.CURRENT_ACTIVE_PHONE,
                Integer.toString(PhoneConstants.PHONE_TYPE_CDMA));

        // This is needed to handle phone process crashes
        String inEcm=SystemProperties.get(TelephonyProperties.PROPERTY_INECM_MODE, "false");
        mIsPhoneInEcmState = inEcm.equals("true");
        if (mIsPhoneInEcmState) {
            // Send a message which will invoke handleExitEmergencyCallbackMode
            mCi.exitEmergencyCallbackMode(obtainMessage(EVENT_EXIT_EMERGENCY_CALLBACK_RESPONSE));
        }

        // get the string that specifies the carrier OTA Sp number
        mCarrierOtaSpNumSchema = SystemProperties.get(
                TelephonyProperties.PROPERTY_OTASP_NUM_SCHEMA,"");

        // Sets operator properties by retrieving from build-time system property
        String operatorAlpha = SystemProperties.get("ro.cdma.home.operator.alpha");
        String operatorNumeric = SystemProperties.get(PROPERTY_CDMA_HOME_OPERATOR_NUMERIC);
        log("init: operatorAlpha='" + operatorAlpha
                + "' operatorNumeric='" + operatorNumeric + "'");
        if (mUiccController.getUiccCardApplication(UiccController.APP_FAM_3GPP) == null) {
            log("init: APP_FAM_3GPP == NULL");
            if (!TextUtils.isEmpty(operatorAlpha)) {
                log("init: set 'gsm.sim.operator.alpha' to operator='" + operatorAlpha + "'");
                setSystemProperty(PROPERTY_ICC_OPERATOR_ALPHA, operatorAlpha);
            }
            if (!TextUtils.isEmpty(operatorNumeric)) {
                log("init: set 'gsm.sim.operator.numeric' to operator='" + operatorNumeric + "'");
                /// M: Set system property according to sim id. @{
                if (getMySimId() == PhoneConstants.GEMINI_SIM_1) {
                    setSystemProperty(PROPERTY_ICC_OPERATOR_NUMERIC, operatorNumeric);
                } else {
                    setSystemProperty(PROPERTY_ICC_OPERATOR_NUMERIC_2, operatorNumeric);
                }
                /// @}
            }
            setIsoCountryProperty(operatorNumeric);
        }

        // Sets current entry in the telephony carrier table
        updateCurrentCarrierInProvider(operatorNumeric);

        // Notify voicemails.
        notifier.notifyMessageWaitingChanged(this);
    }

    @Override
    public void dispose() {
        synchronized(PhoneProxy.lockForRadioTechnologyChange) {
            super.dispose();
            log("dispose, mIsPhoneInEcmState:" + mIsPhoneInEcmState);
            //VIA-START VIA AGPS
            if(FeatureOption.EVDO_DT_VIA_SUPPORT == true){
                mViaGpsProcess.stop();
            }
            //VIA-END VIA AGPS

            // UTK start
            if(mUtkService != null) {
                mUtkService.dispose();
                mUtkService = null;
            }
            //UTK end    
            //Unregister from all former registered events
            unregisterForRuimRecordEvents();
            mCi.unregisterForAvailable(this); //EVENT_RADIO_AVAILABLE
            mCi.unregisterForOffOrNotAvailable(this); //EVENT_RADIO_OFF_OR_NOT_AVAILABLE
            mCi.unregisterForOn(this); //EVENT_RADIO_ON
            mSST.unregisterForNetworkAttached(this); //EVENT_REGISTERED_TO_NETWORK
            mCi.unSetOnSuppServiceNotification(this);
            mCi.unregisterForExitEmergencyCallbackMode(this);
            //8.2.11 TC-IRLAB-02011 start
            mCi.unregisterForMccMncChange(this);
            //8.2.11 TC-IRLAB-02011 end
            removeCallbacks(mExitEcmRunnable);

            if (mIsPhoneInEcmState) {
                if (mWakeLock.isHeld()) {
                    mWakeLock.release();
                }
                if (mEcmExitRespRegistrant != null) {
                    mEcmExitRespRegistrant.notifyRegistrant(new AsyncResult(null, null, null));
                }
                //add by via for exit ECM when dial Threeway[begin]
                if (mThreeWayEcmExitRespRegistrant != null) {
                    mThreeWayEcmExitRespRegistrant.notifyRegistrant(new AsyncResult(null, null, null));
                }
                //add by via for exit ECM when dial Threeway[end]

                mIsPhoneInEcmState = false;
                sendEmergencyCallbackModeChange();
            }

            removeCallbacksAndMessages(null);

            mPendingMmis.clear();

            //Force all referenced classes to unregister their former registered events
            mCT.dispose();
            mDcTracker.dispose();
            mSST.dispose();
            mCdmaSSM.dispose(this);
            mRuimPhoneBookInterfaceManager.dispose();
            mSubInfo.dispose();
            mEriManager.dispose();
        }
    }

    @Override
    public void removeReferences() {
        log("removeReferences");
        mRuimPhoneBookInterfaceManager = null;
        mSubInfo = null;
        mCT = null;
        mSST = null;
        mEriManager = null;
        mExitEcmRunnable = null;
        super.removeReferences();
    }

    @Override
    protected void finalize() {
        if(DBG) Rlog.d(LOG_TAG, "CDMAPhone finalized");
        if (mWakeLock.isHeld()) {
            Rlog.e(LOG_TAG, "UNEXPECTED; mWakeLock is held when finalizing.");
            mWakeLock.release();
        }
    }

    @Override
    public ServiceState getServiceState() {
        return mSST.mSS;
    }

    @Override
    public CallTracker getCallTracker() {
        return mCT;
    }

    @Override
    public PhoneConstants.State getState() {
        return mCT.mState;
    }

    @Override
    public ServiceStateTracker getServiceStateTracker() {
        return mSST;
    }

    @Override
    public int getPhoneType() {
        return PhoneConstants.PHONE_TYPE_CDMA;
    }

    @Override
    public boolean canTransfer() {
        Rlog.e(LOG_TAG, "canTransfer: not possible in CDMA");
        return false;
    }

    @Override
    public CdmaCall getRingingCall() {
        return mCT.mRingingCall;
    }

    @Override
    public void setMute(boolean muted) {
        mCT.setMute(muted);
    }

    @Override
    public boolean getMute() {
        return mCT.getMute();
    }

    @Override
    public void conference() {
        // three way calls in CDMA will be handled by feature codes
        Rlog.e(LOG_TAG, "conference: not possible in CDMA");
    }

    @Override
    public void enableEnhancedVoicePrivacy(boolean enable, Message onComplete) {
        mCi.setPreferredVoicePrivacy(enable, onComplete);
    }

    @Override
    public void getEnhancedVoicePrivacy(Message onComplete) {
        mCi.getPreferredVoicePrivacy(onComplete);
    }

    @Override
    public void clearDisconnected() {
        mCT.clearDisconnected();
    }

    @Override
    public DataActivityState getDataActivityState() {
        DataActivityState ret = DataActivityState.NONE;

        if (mSST.getCurrentDataConnectionState() == ServiceState.STATE_IN_SERVICE) {

            switch (mDcTracker.getActivity()) {
                case DATAIN:
                    ret = DataActivityState.DATAIN;
                break;

                case DATAOUT:
                    ret = DataActivityState.DATAOUT;
                break;

                case DATAINANDOUT:
                    ret = DataActivityState.DATAINANDOUT;
                break;

                case DORMANT:
                    ret = DataActivityState.DORMANT;
                break;

                default:
                    ret = DataActivityState.NONE;
                break;
            }
        }
        return ret;
    }

    @Override
    public Connection
    dial (String dialString) throws CallStateException {
        // Need to make sure dialString gets parsed properly
        String newDialString = PhoneNumberUtils.stripSeparators(dialString);
        return mCT.dial(newDialString);
    }

    @Override
    public Connection dial(String dialString, UUSInfo uusInfo) throws CallStateException {
        throw new CallStateException("Sending UUS information NOT supported in CDMA!");
    }

    public SignalStrength getSignalStrength() {
        //MTK-START [ALPS415367]For MR1 Migration
        //return mSST.mSignalStrength;
        return mSST.getSignalStrength();
        //MTK-END [ALPS415367]For MR1 Migration
    }
    @Override
    public boolean
    getMessageWaitingIndicator() {
        return (getVoiceMessageCount() > 0);
    }

    @Override
    public List<? extends MmiCode>
    getPendingMmiCodes() {
        return mPendingMmis;
    }

    @Override
    public void registerForSuppServiceNotification(
            Handler h, int what, Object obj) {
        Rlog.e(LOG_TAG, "method registerForSuppServiceNotification is NOT supported in CDMA!");
    }

    @Override
    public CdmaCall getBackgroundCall() {
        return mCT.mBackgroundCall;
    }

    @Override
    public boolean handleInCallMmiCommands(String dialString) {
        Rlog.e(LOG_TAG, "method handleInCallMmiCommands is NOT supported in CDMA!");
        return false;
    }

    boolean isInCall() {
        CdmaCall.State foregroundCallState = getForegroundCall().getState();
        CdmaCall.State backgroundCallState = getBackgroundCall().getState();
        CdmaCall.State ringingCallState = getRingingCall().getState();

        return (foregroundCallState.isAlive() || backgroundCallState.isAlive() || ringingCallState
                .isAlive());
    }

    @Override
    public void
    setNetworkSelectionModeAutomatic(Message response) {
        Rlog.e(LOG_TAG, "method setNetworkSelectionModeAutomatic is NOT supported in CDMA!");
    }

    @Override
    public void unregisterForSuppServiceNotification(Handler h) {
        Rlog.e(LOG_TAG, "method unregisterForSuppServiceNotification is NOT supported in CDMA!");
    }

    @Override
    public void
    acceptCall() throws CallStateException {
        mCT.acceptCall();
    }

    @Override
    public void
    rejectCall() throws CallStateException {
        mCT.rejectCall();
    }

    @Override
    public void
    switchHoldingAndActive() throws CallStateException {
        mCT.switchWaitingOrHoldingAndActive();
    }

    @Override
    public String getIccSerialNumber() {
        IccRecords r = mIccRecords.get();
        if (r == null) {
            // to get ICCID form SIMRecords because it is on MF.
            r = mUiccController.getIccRecords(UiccController.APP_FAM_3GPP);
        }
        return (r != null) ? r.getIccId() : null;
    }

    @Override
    public String getLine1Number() {
        return mSST.getMdnNumber();
    }

    @Override
    public String getCdmaPrlVersion(){
        return mSST.getPrlVersion();
    }

    @Override
    public String getCdmaMin() {
        return mSST.getCdmaMin();
    }

    @Override
    public boolean isMinInfoReady() {
        return mSST.isMinInfoReady();
    }

    @Override
    public void getCallWaiting(Message onComplete) {
        mCi.queryCallWaiting(CommandsInterface.SERVICE_CLASS_VOICE, onComplete);
    }

    @Override
    public void
    setRadioPower(boolean power) {
        mSST.setRadioPower(power);
    }

    @Override
    public String getEsn() {
        Rlog.d(LOG_TAG, "getEsn() = " + mEsn);

        if(mEsn != null){
            if(mEsn.startsWith("0x") || mEsn.startsWith("0X")){
                mEsn = mEsn.substring(2);
            }
            Rlog.d(LOG_TAG, "getEsn().toUpperCase() = " + mEsn.toUpperCase());
            return mEsn.toUpperCase();
        }
        else{
            return null;
        }
    }
    @Override
    public String getMeid() {
        Rlog.d(LOG_TAG, "getMeid() = " + mMeid);

        if(mMeid != null){
            Rlog.d(LOG_TAG, "getMeid().toUpperCase() = " + mMeid.toUpperCase());
            return mMeid.toUpperCase();
        }
        else{
            return null;
        }
    }

    public boolean isMeidValid(){
        if((getMeid() == null) || getMeid().matches("^0*$")){
            Rlog.d(LOG_TAG, "Meid InValid");
            return false;
        }
        else{
            Rlog.d(LOG_TAG, "Meid Valid");
            return true;
        }
    }

    //returns MEID or ESN in CDMA
    @Override
    public String getDeviceId() {
        String id = getMeid();
        if ((id == null) || id.matches("^0*$")) {
            Rlog.d(LOG_TAG, "getDeviceId(): MEID is not initialized use ESN");
            id = getEsn();
        }
        return id;
    }

    @Override
    public String getDeviceSvn() {
        Rlog.d(LOG_TAG, "getDeviceSvn(): return 0");
        return "0";
    }

    @Override
    public String getSubscriberId() {
        IccRecords r = mIccRecords.get();
        return (r != null) ? r.getIMSI() : null;
    }

    @Override
    public String getGroupIdLevel1() {
        Rlog.e(LOG_TAG, "GID1 is not available in CDMA");
        return null;
    }

    @Override
    public String getImei() {
        Rlog.e(LOG_TAG, "IMEI is not available in CDMA");
        return null;
    }

    @Override
    public boolean canConference() {
        Rlog.e(LOG_TAG, "canConference: not possible in CDMA");
        return false;
    }

    @Override
    public CellLocation getCellLocation() {
        CdmaCellLocation loc = mSST.mCellLoc;

        int mode = Settings.Secure.getInt(getContext().getContentResolver(),
                Settings.Secure.LOCATION_MODE, Settings.Secure.LOCATION_MODE_OFF);
        if (mode == Settings.Secure.LOCATION_MODE_OFF) {
            // clear lat/long values for location privacy
            CdmaCellLocation privateLoc = new CdmaCellLocation();
            privateLoc.setCellLocationData(loc.getBaseStationId(),
                    CdmaCellLocation.INVALID_LAT_LONG,
                    CdmaCellLocation.INVALID_LAT_LONG,
                    loc.getSystemId(), loc.getNetworkId());
            loc = privateLoc;
        }
        return loc;
    }

    @Override
    public CdmaCall getForegroundCall() {
        return mCT.mForegroundCall;
    }

    @Override
    public void
    selectNetworkManually(OperatorInfo network,
            Message response) {
        Rlog.e(LOG_TAG, "selectNetworkManually: not possible in CDMA");
    }

    @Override
    public void setOnPostDialCharacter(Handler h, int what, Object obj) {
        mPostDialHandler = new Registrant(h, what, obj);
    }

    @Override
    public boolean handlePinMmi(String dialString) {
        CdmaMmiCode mmi = CdmaMmiCode.newFromDialString(dialString, this, mUiccApplication.get());

        if (mmi == null) {
            Rlog.e(LOG_TAG, "Mmi is NULL!");
            return false;
        } else if (mmi.isPinPukCommand()) {
            mPendingMmis.add(mmi);
            mMmiRegistrants.notifyRegistrants(new AsyncResult(null, mmi, null));
            mmi.processCode();
            return true;
        }
        Rlog.e(LOG_TAG, "Unrecognized mmi!");
        return false;
    }

    /**
     * Removes the given MMI from the pending list and notifies registrants that
     * it is complete.
     *
     * @param mmi MMI that is done
     */
    void onMMIDone(CdmaMmiCode mmi) {
        /*
         * Only notify complete if it's on the pending list. Otherwise, it's
         * already been handled (eg, previously canceled).
         */
        if (mPendingMmis.remove(mmi)) {
            mMmiCompleteRegistrants.notifyRegistrants(new AsyncResult(null, mmi, null));
        }
    }

    @Override
    public void setLine1Number(String alphaTag, String number, Message onComplete) {
        Rlog.d(LOG_TAG, "setLine1Number: " + number);
        mSST.setMdnNumber(number, onComplete);
    }

    @Override
    public void setCallWaiting(boolean enable, Message onComplete) {
        Rlog.e(LOG_TAG, "method setCallWaiting is NOT supported in CDMA!");
    }

    @Override
    public void updateServiceLocation() {
        mSST.enableSingleLocationUpdate();
    }

    @Override
    public void setDataRoamingEnabled(boolean enable) {
        mDcTracker.setDataOnRoamingEnabled(enable);
    }

    @Override
    public void registerForCdmaOtaStatusChange(Handler h, int what, Object obj) {
        mCi.registerForCdmaOtaProvision(h, what, obj);
    }

    @Override
    public void unregisterForCdmaOtaStatusChange(Handler h) {
        mCi.unregisterForCdmaOtaProvision(h);
    }

    @Override
    public void registerForSubscriptionInfoReady(Handler h, int what, Object obj) {
        mSST.registerForSubscriptionInfoReady(h, what, obj);
    }

    @Override
    public void unregisterForSubscriptionInfoReady(Handler h) {
        mSST.unregisterForSubscriptionInfoReady(h);
    }

    @Override
    public void setOnEcbModeExitResponse(Handler h, int what, Object obj) {
        mEcmExitRespRegistrant = new Registrant (h, what, obj);
    }

    @Override
    public void unsetOnEcbModeExitResponse(Handler h) {
        mEcmExitRespRegistrant.clear();
    }

    //add by via for exit ECM when dial Three way[begin]
    public void setOnThreeWayEcmExitResponse(Handler h, int what, Object obj) {
        mThreeWayEcmExitRespRegistrant = new Registrant(h, what, obj);
    }

    public void unsetOnThreeWayEcmExitResponse(Handler h) {
        mThreeWayEcmExitRespRegistrant.clear();
    }
    //add by via for exit ECM when dial Three way[begin]

    public void registerForCallWaiting(Handler h, int what, Object obj) {
        mCT.registerForCallWaiting(h, what, obj);
    }

    @Override
    public void unregisterForCallWaiting(Handler h) {
        mCT.unregisterForCallWaiting(h);
    }

    @Override
    public void
    getNeighboringCids(Message response) {
        /*
         * This is currently not implemented.  At least as of June
         * 2009, there is no neighbor cell information available for
         * CDMA because some party is resisting making this
         * information readily available.  Consequently, calling this
         * function can have no useful effect.  This situation may
         * (and hopefully will) change in the future.
         */
        if (response != null) {
            CommandException ce = new CommandException(
                    CommandException.Error.REQUEST_NOT_SUPPORTED);
            AsyncResult.forMessage(response).exception = ce;
            response.sendToTarget();
        }
    }

    @Override
    public PhoneConstants.DataState getDataConnectionState(String apnType) {
        PhoneConstants.DataState ret = PhoneConstants.DataState.DISCONNECTED;

        if (mSST == null) {
             // Radio Technology Change is ongoning, dispose() and removeReferences() have
             // already been called

             ret = PhoneConstants.DataState.DISCONNECTED;
        } else if (mSST.getCurrentDataConnectionState() != ServiceState.STATE_IN_SERVICE) {
            // If we're out of service, open TCP sockets may still work
            // but no data will flow
            ret = PhoneConstants.DataState.DISCONNECTED;
        } else if (mDcTracker.isApnTypeEnabled(apnType) == false ||
                mDcTracker.isApnTypeActive(apnType) == false) {
            ret = PhoneConstants.DataState.DISCONNECTED;
        } else {
            switch (mDcTracker.getState(apnType)) {
                case RETRYING:
                case FAILED:
                case IDLE:
                    ret = PhoneConstants.DataState.DISCONNECTED;
                break;

                case CONNECTED:
                case DISCONNECTING:
                    if ( mCT.mState != PhoneConstants.State.IDLE
                            && !mSST.isConcurrentVoiceAndDataAllowed()) {
                        ret = PhoneConstants.DataState.SUSPENDED;
                    } else {
                        ret = PhoneConstants.DataState.CONNECTED;
                    }
                break;

                case CONNECTING:
                case SCANNING:
                    ret = PhoneConstants.DataState.CONNECTING;
                break;
            }
        }

        log("getDataConnectionState apnType=" + apnType + " ret=" + ret);
        return ret;
    }

    @Override
    public void sendUssdResponse(String ussdMessge) {
        Rlog.e(LOG_TAG, "sendUssdResponse: not possible in CDMA");
    }

    @Override
    public void sendDtmf(char c) {
        if (!PhoneNumberUtils.is12Key(c)) {
            Rlog.e(LOG_TAG,
                    "sendDtmf called with invalid character '" + c + "'");
        } else {
            if (mCT.mState ==  PhoneConstants.State.OFFHOOK) {
                mCi.sendDtmf(c, null);
            }
        }
    }

    @Override
    public void startDtmf(char c) {
        if (!PhoneNumberUtils.is12Key(c)) {
            Rlog.e(LOG_TAG,
                    "startDtmf called with invalid character '" + c + "'");
        } else {
            mCi.startDtmf(c, null);
        }
    }

    @Override
    public void stopDtmf() {
        mCi.stopDtmf(null);
    }

    @Override
    public void sendBurstDtmf(String dtmfString, int on, int off, Message onComplete) {
        boolean check = true;
        for (int itr = 0;itr < dtmfString.length(); itr++) {
            if (!PhoneNumberUtils.is12Key(dtmfString.charAt(itr))) {
                Rlog.e(LOG_TAG,
                        "sendDtmf called with invalid character '" + dtmfString.charAt(itr)+ "'");
                check = false;
                break;
            }
        }
        if ((mCT.mState ==  PhoneConstants.State.OFFHOOK)&&(check)) {
            mCi.sendBurstDtmf(dtmfString, on, off, onComplete);
        }
     }

    @Override
    public void getAvailableNetworks(Message response) {
        Rlog.e(LOG_TAG, "getAvailableNetworks: not possible in CDMA");
    }

    @Override
    public void cancelAvailableNetworks(Message response) {
        Rlog.e(LOG_TAG, "cancelAvailableNetworks: not possible in CDMA");
    }

    @Override
    public void setOutgoingCallerIdDisplay(int commandInterfaceCLIRMode, Message onComplete) {
        Rlog.e(LOG_TAG, "setOutgoingCallerIdDisplay: not possible in CDMA");
    }

    @Override
    public void enableLocationUpdates() {
        mSST.enableLocationUpdates();
    }

    @Override
    public void disableLocationUpdates() {
        mSST.disableLocationUpdates();
    }

    @Override
    public void getDataCallList(Message response) {
        mCi.getDataCallList(response);
    }

    @Override
    public boolean getDataRoamingEnabled() {
        return mDcTracker.getDataOnRoamingEnabled();
    }

    @Override
    public void setVoiceMailNumber(String alphaTag,
                                   String voiceMailNumber,
                                   Message onComplete) {
        Message resp;
        mVmNumber = voiceMailNumber;
        resp = obtainMessage(EVENT_SET_VM_NUMBER_DONE, 0, 0, onComplete);
        IccRecords r = mIccRecords.get();
        if (r != null) {
            r.setVoiceMailNumber(alphaTag, mVmNumber, resp);
        }
    }

    @Override
    public String getVoiceMailNumber() {
        String number = null;
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(getContext());
        // TODO: The default value of voicemail number should be read from a system property

        // Read platform settings for dynamic voicemail number
        if (getContext().getResources().getBoolean(com.android.internal
                .R.bool.config_telephony_use_own_number_for_voicemail)) {
            number = sp.getString(VM_NUMBER_CDMA, getLine1Number());
        } else {
            number = sp.getString(VM_NUMBER_CDMA, "*86");
        }
        return number;
    }

    /* Returns Number of Voicemails
     * @hide
     */
    @Override
    public int getVoiceMessageCount() {
        IccRecords r = mIccRecords.get();
        int voicemailCount =  (r != null) ? r.getVoiceMessageCount() : 0;
        // If mRuimRecords.getVoiceMessageCount returns zero, then there is possibility
        // that phone was power cycled and would have lost the voicemail count.
        // So get the count from preferences.
        if (voicemailCount == 0) {
            SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(getContext());
            voicemailCount = sp.getInt(VM_COUNT_CDMA, 0);
        }
        return voicemailCount;
    }

    @Override
    public String getVoiceMailAlphaTag() {
        // TODO: Where can we get this value has to be clarified with QC.
        String ret = "";//TODO: Remove = "", if we know where to get this value.

        //ret = mSIMRecords.getVoiceMailAlphaTag();

        if (ret == null || ret.length() == 0) {
            return mContext.getText(
                com.android.internal.R.string.defaultVoiceMailAlphaTag).toString();
        }

        return ret;
    }

    @Override
    public void getCallForwardingOption(int commandInterfaceCFReason, Message onComplete) {
        Rlog.e(LOG_TAG, "getCallForwardingOption: not possible in CDMA");
    }

    @Override
    public void setCallForwardingOption(int commandInterfaceCFAction,
            int commandInterfaceCFReason,
            String dialingNumber,
            int timerSeconds,
            Message onComplete) {
        Rlog.e(LOG_TAG, "setCallForwardingOption: not possible in CDMA");
    }

    @Override
    public void
    getOutgoingCallerIdDisplay(Message onComplete) {
        Rlog.e(LOG_TAG, "getOutgoingCallerIdDisplay: not possible in CDMA");
    }

    @Override
    public boolean
    getCallForwardingIndicator() {
        Rlog.e(LOG_TAG, "getCallForwardingIndicator: not possible in CDMA");
        return false;
    }

    @Override
    public void explicitCallTransfer() {
        Rlog.e(LOG_TAG, "explicitCallTransfer: not possible in CDMA");
    }

    @Override
    public String getLine1AlphaTag() {
        Rlog.e(LOG_TAG, "getLine1AlphaTag: not possible in CDMA");
        return null;
    }

    /**
     * Notify any interested party of a Phone state change
     * {@link com.android.internal.telephony.PhoneConstants.State}
     */
    /*package*/ void notifyPhoneStateChanged() {
        mNotifier.notifyPhoneState(this);
    }

    /**
     * Notify registrants of a change in the call state. This notifies changes in
     * {@link com.android.internal.telephony.Call.State}. Use this when changes
     * in the precise call state are needed, else use notifyPhoneStateChanged.
     */
    /*package*/ void notifyPreciseCallStateChanged() {
        /* we'd love it if this was package-scoped*/
        super.notifyPreciseCallStateChangedP();
    }

     void notifyServiceStateChanged(ServiceState ss) {
         super.notifyServiceStateChangedP(ss);
     }

     void notifyLocationChanged() {
         mNotifier.notifyCellLocation(this);
     }

    /*package*/ void notifyNewRingingConnection(Connection c) {
        /* we'd love it if this was package-scoped*/
        super.notifyNewRingingConnectionP(c);
    }

    /*package*/ void notifyDisconnect(Connection cn) {
        mDisconnectRegistrants.notifyResult(cn);
    }

    void notifyUnknownConnection() {
        mUnknownConnectionRegistrants.notifyResult(this);
    }

    @Override
    public boolean isInEmergencyCall() {
        return mCT.isInEmergencyCall();
    }

    @Override
    public boolean isInEcm() {
        return mIsPhoneInEcmState;
    }

    void sendEmergencyCallbackModeChange(){
        //Send an Intent
        Intent intent = new Intent(TelephonyIntents.ACTION_EMERGENCY_CALLBACK_MODE_CHANGED);
        intent.putExtra(PhoneConstants.PHONE_IN_ECM_STATE, mIsPhoneInEcmState);
        ActivityManagerNative.broadcastStickyIntent(intent,null,UserHandle.USER_ALL);
        if (DBG) Rlog.d(LOG_TAG, "sendEmergencyCallbackModeChange, mIsPhoneInEcmState:" + mIsPhoneInEcmState);
    }

    @Override
    public void exitEmergencyCallbackMode() {
        if (mWakeLock.isHeld()) {
            mWakeLock.release();
        }
        // Send a message which will invoke handleExitEmergencyCallbackMode
        mCi.exitEmergencyCallbackMode(obtainMessage(EVENT_EXIT_EMERGENCY_CALLBACK_RESPONSE));
    }

    private void handleEnterEmergencyCallbackMode(Message msg) {
        if (DBG) {
            Rlog.d(LOG_TAG, "handleEnterEmergencyCallbackMode,mIsPhoneInEcmState= "
                    + mIsPhoneInEcmState);
        }
        // if phone is not in Ecm mode, and it's changed to Ecm mode
        if (mIsPhoneInEcmState == false) {
            mIsPhoneInEcmState = true;

            //via support start
            //moving notify change action when the exitecmtimer start by wjzhang

            // notify change
            //sendEmergencyCallbackModeChange();

            //via support end
            setSystemProperty(TelephonyProperties.PROPERTY_INECM_MODE, "true");

            //via support start
            //moving exitecmtimer start when the ecm call shut down by wjzhang

            // Post this runnable so we will automatically exit
            // if no one invokes exitEmergencyCallbackMode() directly.
            //long delayInMillis = SystemProperties.getLong(
            //        TelephonyProperties.PROPERTY_ECM_EXIT_TIMER, DEFAULT_ECM_EXIT_TIMER_VALUE);
            //postDelayed(mExitEcmRunnable, delayInMillis);

            //via support end

            // We don't want to go to sleep while in Ecm
            mWakeLock.acquire();
        }
    }

    private void handleExitEmergencyCallbackMode(Message msg) {
        AsyncResult ar = (AsyncResult)msg.obj;
        if (DBG) {
            Rlog.d(LOG_TAG, "handleExitEmergencyCallbackMode, ar.exception:" + ar.exception
                    + ", mIsPhoneInEcmState:" + mIsPhoneInEcmState);
        }
        // Remove pending exit Ecm runnable, if any
        removeCallbacks(mExitEcmRunnable);

        if (mEcmExitRespRegistrant != null) {
            mEcmExitRespRegistrant.notifyRegistrant(ar);
        }

        //add by via for exit ECM when dial Threeway[begin]
        if (mThreeWayEcmExitRespRegistrant != null) {
            mThreeWayEcmExitRespRegistrant.notifyRegistrant(ar);
        }
        //add by via for exit ECM when dial Threeway[end]
        
        // if exiting ecm success
        if (ar.exception == null) {
            if (mIsPhoneInEcmState) {
                mIsPhoneInEcmState = false;
                setSystemProperty(TelephonyProperties.PROPERTY_INECM_MODE, "false");
            }
            // send an Intent
            sendEmergencyCallbackModeChange();
            // Re-initiate data connection
            mDcTracker.setInternalDataEnabled(true);
        }
    }

    /**
     * Handle to cancel or restart Ecm timer in emergency call back mode
     * if action is CANCEL_ECM_TIMER, cancel Ecm timer and notify apps the timer is canceled;
     * otherwise, restart Ecm timer and notify apps the timer is restarted.
     */
    void handleTimerInEmergencyCallbackMode(int action) {
        //via support start [ALPS00395168]
        if (!mIsPhoneInEcmState) {
            if (DBG) {
                Rlog.e(LOG_TAG, "handleTimerInEmergencyCallbackMode, unsupported EcmState " + mIsPhoneInEcmState);
            }
            return;
        }
        //via support end [ALPS00395168]

        switch(action) {
        case CANCEL_ECM_TIMER:
            removeCallbacks(mExitEcmRunnable);
            mEcmTimerResetRegistrants.notifyResult(Boolean.TRUE);
            break;
        case RESTART_ECM_TIMER:
            long delayInMillis = SystemProperties.getLong(
                    TelephonyProperties.PROPERTY_ECM_EXIT_TIMER, DEFAULT_ECM_EXIT_TIMER_VALUE);
            postDelayed(mExitEcmRunnable, delayInMillis);
            mEcmTimerResetRegistrants.notifyResult(Boolean.FALSE);
            
            //via support start
            //moving notify change action when the exitecmtimer start by wjzhang
            // notify change
            sendEmergencyCallbackModeChange();
            //via support end

            break;
        default:
            Rlog.e(LOG_TAG, "handleTimerInEmergencyCallbackMode, unsupported action " + action);
        }
    }

    /**
     * Registration point for Ecm timer reset
     * @param h handler to notify
     * @param what User-defined message code
     * @param obj placed in Message.obj
     */
    @Override
    public void registerForEcmTimerReset(Handler h, int what, Object obj) {
        mEcmTimerResetRegistrants.addUnique(h, what, obj);
    }

    @Override
    public void unregisterForEcmTimerReset(Handler h) {
        mEcmTimerResetRegistrants.remove(h);
    }

    private void broadcastRadioAvailableState() {
        if (DBG) log("sendBroadcastRadioAvailable " + TelephonyIntents.ACTION_RADIO_AVAILABLE + " = " + mIsRadioAvailable);
        Intent intent = new Intent(TelephonyIntents.ACTION_RADIO_AVAILABLE);
        intent.putExtra(TelephonyIntents.EXTRA_RADIO_AVAILABLE_STATE, mIsRadioAvailable);
        ActivityManagerNative.broadcastStickyIntent(intent,null,UserHandle.USER_ALL);
    }

    @Override
    public void handleMessage(Message msg) {
        AsyncResult ar;
        Message     onComplete;

        if (!mIsTheCurrentActivePhone) {
            Rlog.e(LOG_TAG, "Received message " + msg +
                    "[" + msg.what + "] while being destroyed. Ignoring.");
            return;
        }
        switch(msg.what) {
            case EVENT_RADIO_AVAILABLE: {
                Rlog.d(LOG_TAG, "Event EVENT_RADIO_AVAILABLE");
                //via support start [ALPS00402795]
                mIsRadioAvailable = true;
                //via support end [ALPS00402795]
 
                broadcastRadioAvailableState();
                mCi.getBasebandVersion(obtainMessage(EVENT_GET_BASEBAND_VERSION_DONE));

                mCi.getDeviceIdentity(obtainMessage(EVENT_GET_DEVICE_IDENTITY_DONE));
                /// M: MTK added. @{
                updateSimIndicateState();
                Rlog.d(LOG_TAG, "Event QUERY UIM INSERTED STATUS");
                mCi.queryUimInsertedStatus(obtainMessage(EVENT_QUERY_UIM_INSERTED_STATUS_DONE));
                /// @}

                //8.2.11 TC-IRLAB-02011 start
                Rlog.d(LOG_TAG, "EVENT_RADIO_AVAILABLE");
                mCi.enableCdmaRegisterPause(mCpPauseEnable, null);
                //8.2.11 TC-IRLAB-02011 end

                //default value used by cp is 2.
                Rlog.d(LOG_TAG, "setArsiReportThreshold threshold = 1");
                setArsiReportThreshold(2);
                /// @}
            }
            break;

            case EVENT_GET_BASEBAND_VERSION_DONE:{
                ar = (AsyncResult)msg.obj;

                if (ar.exception != null) {
                    break;
                }

                if (DBG) Rlog.d(LOG_TAG, "Baseband version: " + ar.result);
                /// M: Replace with "cdma.version.baseband".
                setSystemProperty("cdma.version.baseband", (String)ar.result);
            }
            break;

            case EVENT_GET_DEVICE_IDENTITY_DONE:{
                ar = (AsyncResult)msg.obj;

                if (ar.exception != null) {
                    break;
                }
                String[] respId = (String[])ar.result;
                mImei = respId[0];
                //via support start [ALPS00384791]
                mUimId = respId[0];
                //via support end [ALPS00384791]
                mImeiSv = respId[1];
                mEsn  =  respId[2];
                mMeid =  respId[3];
                if (DBG) Rlog.d(LOG_TAG, "EVENT_GET_DEVICE_IDENTITY_DONE: " +
                                mUimId +","+mImeiSv+","+mEsn+","+mMeid);
            }
            break;

            case EVENT_EMERGENCY_CALLBACK_MODE_ENTER:{
                handleEnterEmergencyCallbackMode(msg);
            }
            break;

            case EVENT_ICC_RECORD_EVENTS:
                ar = (AsyncResult)msg.obj;
                processIccRecordEvents((Integer)ar.result);
                break;

            case  EVENT_EXIT_EMERGENCY_CALLBACK_RESPONSE:{
                handleExitEmergencyCallbackMode(msg);
            }
            break;

            case EVENT_RUIM_RECORDS_LOADED:{
                Rlog.d(LOG_TAG, "Event EVENT_RUIM_RECORDS_LOADED Received");
                updateCurrentCarrierInProvider();
            }
            break;

            case EVENT_RADIO_OFF_OR_NOT_AVAILABLE:{
                Rlog.d(LOG_TAG, "Event EVENT_RADIO_OFF_OR_NOT_AVAILABLE Received");
                //via support start [ALPS00402795]
                if (!mCi.getRadioState().isAvailable()) {
                    mIsRadioAvailable = false;
                //via support end [ALPS00402795]

                    broadcastRadioAvailableState();
                }

                /// M: MTK added. @{
                updateSimIndicateState();
                Rlog.d(LOG_TAG, "Event QUERY UIM INSERTED STATUS");
                //mCi.queryUimInsertedStatus(obtainMessage(EVENT_QUERY_UIM_INSERTED_STATUS_DONE));
                /// @}
            }
            break;

            case EVENT_RADIO_ON:{
                Rlog.d(LOG_TAG, "Event EVENT_RADIO_ON Received");
                handleCdmaSubscriptionSource(mCdmaSSM.getCdmaSubscriptionSource());
            }
            break;

            case EVENT_CDMA_SUBSCRIPTION_SOURCE_CHANGED:{
                Rlog.d(LOG_TAG, "EVENT_CDMA_SUBSCRIPTION_SOURCE_CHANGED");
                handleCdmaSubscriptionSource(mCdmaSSM.getCdmaSubscriptionSource());
            }
            break;

            case EVENT_SSN:{
                Rlog.d(LOG_TAG, "Event EVENT_SSN Received");
            }
            break;

            case EVENT_REGISTERED_TO_NETWORK:{
                Rlog.d(LOG_TAG, "Event EVENT_REGISTERED_TO_NETWORK Received");
            }
            break;

            case EVENT_NV_READY:{
                Rlog.d(LOG_TAG, "Event EVENT_NV_READY Received");
                prepareEri();
            }
            break;

            case EVENT_SET_VM_NUMBER_DONE:{
                ar = (AsyncResult)msg.obj;
                if (IccException.class.isInstance(ar.exception)) {
                    storeVoiceMailNumber(mVmNumber);
                    ar.exception = null;
                }
                onComplete = (Message) ar.userObj;
                if (onComplete != null) {
                    AsyncResult.forMessage(onComplete, ar.result, ar.exception);
                    onComplete.sendToTarget();
                }
            }
            break;

            //via support start
    	    case EVENT_QUERY_UIM_INSERTED_STATUS_DONE:{
    		    ar = (AsyncResult)msg.obj;
                if (ar.exception == null) {
    		        int uimInsertedStatus = ((int[])ar.result)[0];
                    if (uimInsertedStatus == UIM_STATUS_NO_CARD_INSERTED) {
                        if(DBG)Rlog.d(LOG_TAG, "[CDMAPhone, EVENT_QUERY_UIM_INSERTED_STATUS_DONE, execute notifySimMissing]");
                        mCi.notifyUimInsertedStatus(uimInsertedStatus);
                        }
                    }
                    else {
                        if(DBG)Rlog.w(LOG_TAG, "[CDMAPhone, EVENT_QUERY_UIM_INSERTED_STATUS_DONE, exception]");
                        queryUimInsertStatusDelay(QUERY_UIM_INSERTED_STATUS_DELAY);
                    }
    	        }
    	        break;

            case EVENT_GET_UIM_INSERT_STATUS_RETRY: {
                if(DBG)Rlog.d(LOG_TAG, "Event QUERY UIM INSERTED STATUS, retry....");
                mCi.queryUimInsertedStatus(obtainMessage(EVENT_QUERY_UIM_INSERTED_STATUS_DONE));
            }  
            break;
            //add by via begin [ALPS00389658]
            case EVENT_SET_MEID_DONE:{
                if(DBG)Rlog.d(LOG_TAG, "EVENT_SET_MEID_DONE");
                //Get device identity, MEID has been changed
                ar = (AsyncResult)msg.obj;
                if (ar.exception == null) {
                    mCi.getDeviceIdentity(obtainMessage(EVENT_GET_DEVICE_IDENTITY_DONE));
                }else{
                    if(DBG) Rlog.w(LOG_TAG, "[CDMAPhone, EVENT_SET_MEID_DONE, exception]");
                }
            }
            break;
            //add by via end [ALPS00389658]
            //via support end

            //via support start, fix HANDROID#1535
            case EVENT_RUIM_READY:{
                if(DBG)Rlog.d(LOG_TAG, "CDMAPhone EVENT_RUIM_READY");
                mCi.getDeviceIdentity(obtainMessage(EVENT_GET_DEVICE_IDENTITY_DONE));       
                // UTK start
                //update utkservice
                if(mUiccController != null)
                    mUtkService = UtkService.getInstance(mCi, mContext, mUiccController.getUiccCard());  
                // UTK end
            }
            break;
            //via support end

            //8.2.11 TC-IRLAB-02011 start
            case EVENT_CDMA_MCC_MNC_CHANGED:
              ar = (AsyncResult)msg.obj;
              if (ar.exception != null) {
                  break;
              }
              String mccmnc = (String)ar.result;
              if(DBG)Rlog.d(LOG_TAG, "mccmnc changed mccmnc=" + mccmnc);
              mMccMncChangeRegistrants.notifyRegistrants(new AsyncResult (null, mccmnc, null));    
              if(mCpPauseEnable)mSST.enableServiceStateNotify(false);
              break;
            //8.2.11 TC-IRLAB-02011 end
              
            default:{
                super.handleMessage(msg);
            }
        }
    }

    @Override
    protected void onUpdateIccAvailability() {
        if (mUiccController == null ) {
            return;
        }

        UiccCardApplication newUiccApplication =
                mUiccController.getUiccCardApplication(UiccController.APP_FAM_3GPP2);

        if (newUiccApplication == null) {
            log("can't find 3GPP2 application; trying APP_FAM_3GPP");
            newUiccApplication = mUiccController
                    .getUiccCardApplication(UiccController.APP_FAM_3GPP);
        }

        UiccCardApplication app = mUiccApplication.get();
        if (app != newUiccApplication) {
            if (app != null) {
                log("Removing stale icc objects.");
                if (mIccRecords.get() != null) {
                    unregisterForRuimRecordEvents();
                    mRuimPhoneBookInterfaceManager.updateIccRecords(null);
                }
                mIccRecords.set(null);
                mUiccApplication.set(null);
            }
            if (newUiccApplication != null) {
                log("New Uicc application found");
                mUiccApplication.set(newUiccApplication);
                mIccRecords.set(newUiccApplication.getIccRecords());
                registerForRuimRecordEvents();
                mRuimPhoneBookInterfaceManager.updateIccRecords(mIccRecords.get());

                //via support start, fix HANDROID#1535
                newUiccApplication.registerForReady(this, EVENT_RUIM_READY, null);
                //via support end
            }
        }
    }

    private void processIccRecordEvents(int eventCode) {
        switch (eventCode) {
            case RuimRecords.EVENT_MWI:
                notifyMessageWaitingIndicator();
                break;

            default:
                Rlog.e(LOG_TAG,"Unknown icc records event code " + eventCode);
                break;
        }
    }

    /**
     * Handles the call to get the subscription source
     *
     * @param newSubscriptionSource holds the new CDMA subscription source value
     */
    private void handleCdmaSubscriptionSource(int newSubscriptionSource) {
        if (newSubscriptionSource != mCdmaSubscriptionSource) {
             mCdmaSubscriptionSource = newSubscriptionSource;
             if (newSubscriptionSource == CDMA_SUBSCRIPTION_NV) {
                 // NV is ready when subscription source is NV
                 sendMessage(obtainMessage(EVENT_NV_READY));
             }
        }
    }

    /**
     * Retrieves the PhoneSubInfo of the CDMAPhone
     */
    @Override
    public PhoneSubInfo getPhoneSubInfo() {
        return mSubInfo;
    }

    /**
     * Retrieves the IccPhoneBookInterfaceManager of the CDMAPhone
     */
    @Override
    public IccPhoneBookInterfaceManager getIccPhoneBookInterfaceManager() {
        return mRuimPhoneBookInterfaceManager;
    }

    public void registerForEriFileLoaded(Handler h, int what, Object obj) {
        Registrant r = new Registrant (h, what, obj);
        mEriFileLoadedRegistrants.add(r);
    }

    public void unregisterForEriFileLoaded(Handler h) {
        mEriFileLoadedRegistrants.remove(h);
    }

    // override for allowing access from other classes of this package
    /**
     * {@inheritDoc}
     */
    @Override
    public final void setSystemProperty(String property, String value) {
        super.setSystemProperty(property, value);
    }

    /**
     * Activate or deactivate cell broadcast SMS.
     *
     * @param activate 0 = activate, 1 = deactivate
     * @param response Callback message is empty on completion
     */
    @Override
    public void activateCellBroadcastSms(int activate, Message response) {
        Rlog.e(LOG_TAG, "[CDMAPhone] activateCellBroadcastSms() is obsolete; use SmsManager");
        response.sendToTarget();
    }

    /**
     * Query the current configuration of cdma cell broadcast SMS.
     *
     * @param response Callback message is empty on completion
     */
    @Override
    public void getCellBroadcastSmsConfig(Message response) {
        Rlog.e(LOG_TAG, "[CDMAPhone] getCellBroadcastSmsConfig() is obsolete; use SmsManager");
        response.sendToTarget();
    }

    /**
     * Configure cdma cell broadcast SMS.
     *
     * @param response Callback message is empty on completion
     */
    @Override
    public void setCellBroadcastSmsConfig(int[] configValuesArray, Message response) {
        Rlog.e(LOG_TAG, "[CDMAPhone] setCellBroadcastSmsConfig() is obsolete; use SmsManager");
        response.sendToTarget();
    }

    /**
     * Returns true if OTA Service Provisioning needs to be performed.
     */
    @Override
    public boolean needsOtaServiceProvisioning() {
        return mSST.getOtasp() != ServiceStateTracker.OTASP_NOT_NEEDED;
    }

    private static final String IS683A_FEATURE_CODE = "*228";
    private static final int IS683A_FEATURE_CODE_NUM_DIGITS = 4;
    private static final int IS683A_SYS_SEL_CODE_NUM_DIGITS = 2;
    private static final int IS683A_SYS_SEL_CODE_OFFSET = 4;

    private static final int IS683_CONST_800MHZ_A_BAND = 0;
    private static final int IS683_CONST_800MHZ_B_BAND = 1;
    private static final int IS683_CONST_1900MHZ_A_BLOCK = 2;
    private static final int IS683_CONST_1900MHZ_B_BLOCK = 3;
    private static final int IS683_CONST_1900MHZ_C_BLOCK = 4;
    private static final int IS683_CONST_1900MHZ_D_BLOCK = 5;
    private static final int IS683_CONST_1900MHZ_E_BLOCK = 6;
    private static final int IS683_CONST_1900MHZ_F_BLOCK = 7;
    private static final int INVALID_SYSTEM_SELECTION_CODE = -1;

    private static boolean isIs683OtaSpDialStr(String dialStr) {
        int sysSelCodeInt;
        boolean isOtaspDialString = false;
        int dialStrLen = dialStr.length();

        if (dialStrLen == IS683A_FEATURE_CODE_NUM_DIGITS) {
            if (dialStr.equals(IS683A_FEATURE_CODE)) {
                isOtaspDialString = true;
            }
        } else {
            sysSelCodeInt = extractSelCodeFromOtaSpNum(dialStr);
            switch (sysSelCodeInt) {
                case IS683_CONST_800MHZ_A_BAND:
                case IS683_CONST_800MHZ_B_BAND:
                case IS683_CONST_1900MHZ_A_BLOCK:
                case IS683_CONST_1900MHZ_B_BLOCK:
                case IS683_CONST_1900MHZ_C_BLOCK:
                case IS683_CONST_1900MHZ_D_BLOCK:
                case IS683_CONST_1900MHZ_E_BLOCK:
                case IS683_CONST_1900MHZ_F_BLOCK:
                    isOtaspDialString = true;
                    break;
                default:
                    break;
            }
        }
        return isOtaspDialString;
    }
    /**
     * This function extracts the system selection code from the dial string.
     */
    private static int extractSelCodeFromOtaSpNum(String dialStr) {
        int dialStrLen = dialStr.length();
        int sysSelCodeInt = INVALID_SYSTEM_SELECTION_CODE;

        if ((dialStr.regionMatches(0, IS683A_FEATURE_CODE,
                                   0, IS683A_FEATURE_CODE_NUM_DIGITS)) &&
            (dialStrLen >= (IS683A_FEATURE_CODE_NUM_DIGITS +
                            IS683A_SYS_SEL_CODE_NUM_DIGITS))) {
                // Since we checked the condition above, the system selection code
                // extracted from dialStr will not cause any exception
                sysSelCodeInt = Integer.parseInt (
                                dialStr.substring (IS683A_FEATURE_CODE_NUM_DIGITS,
                                IS683A_FEATURE_CODE_NUM_DIGITS + IS683A_SYS_SEL_CODE_NUM_DIGITS));
        }
        if (DBG) Rlog.d(LOG_TAG, "extractSelCodeFromOtaSpNum " + sysSelCodeInt);
        return sysSelCodeInt;
    }

    /**
     * This function checks if the system selection code extracted from
     * the dial string "sysSelCodeInt' is the system selection code specified
     * in the carrier ota sp number schema "sch".
     */
    private static boolean
    checkOtaSpNumBasedOnSysSelCode (int sysSelCodeInt, String sch[]) {
        boolean isOtaSpNum = false;
        try {
            // Get how many number of system selection code ranges
            int selRc = Integer.parseInt(sch[1]);
            for (int i = 0; i < selRc; i++) {
                if (!TextUtils.isEmpty(sch[i+2]) && !TextUtils.isEmpty(sch[i+3])) {
                    int selMin = Integer.parseInt(sch[i+2]);
                    int selMax = Integer.parseInt(sch[i+3]);
                    // Check if the selection code extracted from the dial string falls
                    // within any of the range pairs specified in the schema.
                    if ((sysSelCodeInt >= selMin) && (sysSelCodeInt <= selMax)) {
                        isOtaSpNum = true;
                        break;
                    }
                }
            }
        } catch (NumberFormatException ex) {
            // If the carrier ota sp number schema is not correct, we still allow dial
            // and only log the error:
            Rlog.e(LOG_TAG, "checkOtaSpNumBasedOnSysSelCode, error", ex);
        }
        return isOtaSpNum;
    }

    // Define the pattern/format for carrier specified OTASP number schema.
    // It separates by comma and/or whitespace.
    private static Pattern pOtaSpNumSchema = Pattern.compile("[,\\s]+");

    /**
     * The following function checks if a dial string is a carrier specified
     * OTASP number or not by checking against the OTASP number schema stored
     * in PROPERTY_OTASP_NUM_SCHEMA.
     *
     * Currently, there are 2 schemas for carriers to specify the OTASP number:
     * 1) Use system selection code:
     *    The schema is:
     *    SELC,the # of code pairs,min1,max1,min2,max2,...
     *    e.g "SELC,3,10,20,30,40,60,70" indicates that there are 3 pairs of
     *    selection codes, and they are {10,20}, {30,40} and {60,70} respectively.
     *
     * 2) Use feature code:
     *    The schema is:
     *    "FC,length of feature code,feature code".
     *     e.g "FC,2,*2" indicates that the length of the feature code is 2,
     *     and the code itself is "*2".
     */
    private boolean isCarrierOtaSpNum(String dialStr) {
        boolean isOtaSpNum = false;
        int sysSelCodeInt = extractSelCodeFromOtaSpNum(dialStr);
        if (sysSelCodeInt == INVALID_SYSTEM_SELECTION_CODE) {
            return isOtaSpNum;
        }
        // mCarrierOtaSpNumSchema is retrieved from PROPERTY_OTASP_NUM_SCHEMA:
        if (!TextUtils.isEmpty(mCarrierOtaSpNumSchema)) {
            Matcher m = pOtaSpNumSchema.matcher(mCarrierOtaSpNumSchema);
            if (DBG) {
                Rlog.d(LOG_TAG, "isCarrierOtaSpNum,schema" + mCarrierOtaSpNumSchema);
            }

            if (m.find()) {
                String sch[] = pOtaSpNumSchema.split(mCarrierOtaSpNumSchema);
                // If carrier uses system selection code mechanism
                if (!TextUtils.isEmpty(sch[0]) && sch[0].equals("SELC")) {
                    if (sysSelCodeInt!=INVALID_SYSTEM_SELECTION_CODE) {
                        isOtaSpNum=checkOtaSpNumBasedOnSysSelCode(sysSelCodeInt,sch);
                    } else {
                        if (DBG) {
                            Rlog.d(LOG_TAG, "isCarrierOtaSpNum,sysSelCodeInt is invalid");
                        }
                    }
                } else if (!TextUtils.isEmpty(sch[0]) && sch[0].equals("FC")) {
                    int fcLen =  Integer.parseInt(sch[1]);
                    String fc = sch[2];
                    if (dialStr.regionMatches(0,fc,0,fcLen)) {
                        isOtaSpNum = true;
                    } else {
                        if (DBG) Rlog.d(LOG_TAG, "isCarrierOtaSpNum,not otasp number");
                    }
                } else {
                    if (DBG) {
                        Rlog.d(LOG_TAG, "isCarrierOtaSpNum,ota schema not supported" + sch[0]);
                    }
                }
            } else {
                if (DBG) {
                    Rlog.d(LOG_TAG, "isCarrierOtaSpNum,ota schema pattern not right" +
                          mCarrierOtaSpNumSchema);
                }
            }
        } else {
            if (DBG) Rlog.d(LOG_TAG, "isCarrierOtaSpNum,ota schema pattern empty");
        }
        return isOtaSpNum;
    }

    /**
     * isOTASPNumber: checks a given number against the IS-683A OTASP dial string and carrier
     * OTASP dial string.
     *
     * @param dialStr the number to look up.
     * @return true if the number is in IS-683A OTASP dial string or carrier OTASP dial string
     */
    @Override
    public  boolean isOtaSpNumber(String dialStr){
        boolean isOtaSpNum = false;
        String dialableStr = PhoneNumberUtils.extractNetworkPortionAlt(dialStr);
        if (dialableStr != null) {
            isOtaSpNum = isIs683OtaSpDialStr(dialableStr);
            if (isOtaSpNum == false) {
                isOtaSpNum = isCarrierOtaSpNum(dialableStr);
            }
        }
        if (DBG) Rlog.d(LOG_TAG, "isOtaSpNumber " + isOtaSpNum);
        return isOtaSpNum;
    }

    @Override
    public int getCdmaEriIconIndex() {
        return getServiceState().getCdmaEriIconIndex();
    }

    /**
     * Returns the CDMA ERI icon mode,
     * 0 - ON
     * 1 - FLASHING
     */
    @Override
    public int getCdmaEriIconMode() {
        return getServiceState().getCdmaEriIconMode();
    }

    /**
     * Returns the CDMA ERI text,
     */
    @Override
    public String getCdmaEriText() {
        int roamInd = getServiceState().getCdmaRoamingIndicator();
        int defRoamInd = getServiceState().getCdmaDefaultRoamingIndicator();
        return mEriManager.getCdmaEriText(roamInd, defRoamInd);
    }

    /**
     * Store the voicemail number in preferences
     */
    private void storeVoiceMailNumber(String number) {
        // Update the preference value of voicemail number
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(getContext());
        SharedPreferences.Editor editor = sp.edit();
        editor.putString(VM_NUMBER_CDMA, number);
        editor.apply();
    }

    /**
     * Sets PROPERTY_ICC_OPERATOR_ISO_COUNTRY property
     *
     */
    private void setIsoCountryProperty(String operatorNumeric) {
        if (TextUtils.isEmpty(operatorNumeric)) {
            log("setIsoCountryProperty: clear 'gsm.sim.operator.iso-country'");
            setSystemProperty(PROPERTY_ICC_OPERATOR_ISO_COUNTRY, "");
        } else {
            String iso = "";
            try {
                iso = MccTable.countryCodeForMcc(Integer.parseInt(
                        operatorNumeric.substring(0,3)));
            } catch (NumberFormatException ex) {
                loge("setIsoCountryProperty: countryCodeForMcc error", ex);
            } catch (StringIndexOutOfBoundsException ex) {
                loge("setIsoCountryProperty: countryCodeForMcc error", ex);
            }

            log("setIsoCountryProperty: set 'gsm.sim.operator.iso-country' to iso=" + iso);
            setSystemProperty(PROPERTY_ICC_OPERATOR_ISO_COUNTRY, iso);
        }
    }

    /**
     * Sets the "current" field in the telephony provider according to the
     * build-time operator numeric property
     *
     * @return true for success; false otherwise.
     */
    boolean updateCurrentCarrierInProvider(String operatorNumeric) {
        log("CDMAPhone: updateCurrentCarrierInProvider called");
        if (!TextUtils.isEmpty(operatorNumeric)) {
            try {
                /// M: Get uri according to sim id. @{
                Uri uri = null;
                uri = Uri.withAppendedPath(getCarriersContentUri(), "current");
                /// @}
                ContentValues map = new ContentValues();
                map.put(Telephony.Carriers.NUMERIC, operatorNumeric);
                log("updateCurrentCarrierInProvider from system: numeric=" + operatorNumeric);
                getContext().getContentResolver().insert(uri, map);

                // Updates MCC MNC device configuration information
                MccTable.updateMccMncConfiguration(mContext, operatorNumeric);

                return true;
            } catch (SQLException e) {
                Rlog.e(LOG_TAG, "Can't store current operator", e);
            }
        }
        return false;
    }

    /**
     * Sets the "current" field in the telephony provider according to the SIM's operator.
     * Implemented in {@link CDMALTEPhone} for CDMA/LTE devices.
     *
     * @return true for success; false otherwise.
     */
    boolean updateCurrentCarrierInProvider() {
        /// M: MTK modified. @{
        if (mIccRecords.get() != null) {
            try {
                Uri uri = null;
                uri = Uri.withAppendedPath(getCarriersContentUri(), "current");
                ContentValues map = new ContentValues();
                map.put(Telephony.Carriers.NUMERIC, ((RuimRecords)mIccRecords.get()).getRUIMOperatorNumeric());
                mContext.getContentResolver().insert(uri, map);
                return true;
            } catch (SQLException e) {
                Rlog.e(LOG_TAG, "Can't store current operator", e);
            }
        } else {
            Rlog.d(LOG_TAG, "updateCurrentCarrierInProvider():mIccRecords is null");
        }
        return false;
        /// @}
    }

    public void prepareEri() {
        mEriManager.loadEriFile();
        if(mEriManager.isEriFileLoaded()) {
            // when the ERI file is loaded
            log("ERI read, notify registrants");
            mEriFileLoadedRegistrants.notifyRegistrants();
        }
    }

    public boolean isEriFileLoaded() {
        return mEriManager.isEriFileLoaded();
    }

    protected void registerForRuimRecordEvents() {
        IccRecords r = mIccRecords.get();
        if (r == null) {
            return;
        }
        r.registerForRecordsEvents(this, EVENT_ICC_RECORD_EVENTS, null);
        r.registerForRecordsLoaded(this, EVENT_RUIM_RECORDS_LOADED, null);
    }

    protected void unregisterForRuimRecordEvents() {
        IccRecords r = mIccRecords.get();
        if (r == null) {
            return;
        }
        r.unregisterForRecordsEvents(this);
        r.unregisterForRecordsLoaded(this);
    }

    protected void log(String s) {
        if (DBG)
            Rlog.d(LOG_TAG, s);
    }

    protected void loge(String s, Exception e) {
        if (DBG)
            Rlog.e(LOG_TAG, s, e);
    }

    @Override
    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        pw.println("CDMAPhone extends:");
        super.dump(fd, pw, args);
        pw.println(" mVmNumber=" + mVmNumber);
        pw.println(" mCT=" + mCT);
        pw.println(" mSST=" + mSST);
        pw.println(" mCdmaSSM=" + mCdmaSSM);
        pw.println(" mPendingMmis=" + mPendingMmis);
        pw.println(" mRuimPhoneBookInterfaceManager=" + mRuimPhoneBookInterfaceManager);
        pw.println(" mCdmaSubscriptionSource=" + mCdmaSubscriptionSource);
        pw.println(" mSubInfo=" + mSubInfo);
        pw.println(" mEriManager=" + mEriManager);
        pw.println(" mWakeLock=" + mWakeLock);
        pw.println(" mIsPhoneInEcmState=" + mIsPhoneInEcmState);
        if (VDBG) pw.println(" mImei=" + mImei);
        if (VDBG) pw.println(" mImeiSv=" + mImeiSv);
        if (VDBG) pw.println(" mEsn=" + mEsn);
        if (VDBG) pw.println(" mMeid=" + mMeid);
        pw.println(" mCarrierOtaSpNumSchema=" + mCarrierOtaSpNumSchema);
        pw.println(" getCdmaEriIconIndex()=" + getCdmaEriIconIndex());
        pw.println(" getCdmaEriIconMode()=" + getCdmaEriIconMode());
        pw.println(" getCdmaEriText()=" + getCdmaEriText());
        pw.println(" isMinInfoReady()=" + isMinInfoReady());
        pw.println(" isCspPlmnEnabled()=" + isCspPlmnEnabled());
    }

    /// M: [mtk04070][111117][ALPS00093395]MTK proprietary methods. @{
    /* vt start */
    public Connection
    vtDial (String dialString) throws CallStateException {
        // Need to make sure dialString gets parsed properly
        String newDialString = PhoneNumberUtils.stripSeparators(dialString);
        return mCT.vtDial(newDialString);
    }

    public Connection vtDial(String dialString, UUSInfo uusInfo) throws CallStateException {
        throw new CallStateException("Sending UUS information NOT supported in CDMA!");
    }

    public void voiceAccept() throws CallStateException {
        throw new CallStateException("voiceAccept() NOT supported in CDMA!");
    }
    /* vt end */

    public void registerForCrssSuppServiceNotification(Handler h, int what, Object obj) {
        Rlog.e(LOG_TAG, "method registerForCrssSuppServiceNotification is NOT supported in CDMA!");
    }

    public void unregisterForCrssSuppServiceNotification(Handler h) {
        Rlog.e(LOG_TAG, "method unregisterForCrssSuppServiceNotification is NOT supported in CDMA!");
    }

    public void
    hangupAll() throws CallStateException {
    	mCT.hangupAll();
    }

    public void
    hangupActiveCall() throws CallStateException {
        mCT.hangupActiveCall();
    }
	
    public void
    getCurrentCallMeter(Message result) {
    	mCT.getCurrentCallMeter(result);
    }
    	
    public void
    getAccumulatedCallMeter(Message result) {
    	mCT.getAccumulatedCallMeter(result);
    }
    	
    public void
    getAccumulatedCallMeterMaximum(Message result) {
    	mCT.getAccumulatedCallMeterMaximum(result);
    }
    	
    public void
    getPpuAndCurrency(Message result) {
    	mCT.getPpuAndCurrency(result);
    }	
    	
    public void
    setAccumulatedCallMeterMaximum(String acmmax, String pin2, Message result) {
    	mCT.setAccumulatedCallMeterMaximum(acmmax, pin2, result);
    }
    	
    public void
    resetAccumulatedCallMeter(String pin2, Message result) {
    	mCT.resetAccumulatedCallMeter(pin2, result);
    }
    	
    public void
    setPpuAndCurrency(String currency, String ppu, String pin2, Message result) {
    	mCT.setPpuAndCurrency(currency, ppu, pin2, result);
    }

    //Modify by gfzhu VIA start
    public void setRadioPowerOn(){
        mSST.setRadioPowerOn();
    }

    public void setRadioPower(boolean power, boolean shutdown) {
       mSST.setRadioPower(power, shutdown);
    }

    //Add by mtk80372 for Barcode Number
    public String getSN() {
        return null;
    }

    public int
    getLastCallFailCause() {
        return 0;
    }

    /* Add by vendor for Multiple PDP Context */
    public String getActiveApnType() {
        // TODO Auto-generated method stub
        return null;
    }

    /* Add by vendor for Multiple PDP Context */
    //public DataState getDataConnectionState(String apnType) {
        // TODO Auto-generated method stub
    //    return null;
    //}

    public void getPdpContextList(Message response) {
        getDataCallList(response);
    }

    /* mtk00732 add call barring service */
    public void getFacilityLock(String facility, String password, Message onComplete) {
        Rlog.e(LOG_TAG, "getFacilityLock: not possible in CDMA");
    }

    /* mtk00732 add call barring service */
    public void setFacilityLock(String facility, boolean enable, String password, Message onComplete) {
        Rlog.e(LOG_TAG, "setFacilityLock: not possible in CDMA");
    }
    
    /* mtk00732 add call barring service */
    public void changeBarringPassword(String facility, String oldPwd, String newPwd, Message onComplete) {
        Rlog.e(LOG_TAG, "changeBarringPassword: not possible in CDMA");
    }
    
    /* mtk00732 add call barring service */
    public void changeBarringPassword(String facility, String oldPwd, String newPwd, String newCfm, Message onComplete) {
        Rlog.e(LOG_TAG, "changeBarringPassword: not possible in CDMA");
    }

    /** via support to add this API
     * Refresh Spn Display due to configuration change
     */
    public void refreshSpnDisplay() {
        mSST.refreshSpnDisplay();
    }
    public IccServiceStatus getIccServiceStatus(IccService enService) {
        return IccServiceStatus.UNKNOWN;
    }

    /* vt start */
    public void getVtCallForwardingOption(int commandInterfaceCFReason, Message onComplete) {
        Rlog.e(LOG_TAG, "getVtCallForwardingOption: not possible in CDMA");
    }

    public void setVtCallForwardingOption(int commandInterfaceCFAction,
            int commandInterfaceCFReason,
            String dialingNumber,
            int timerSeconds,
            Message onComplete) {
        Rlog.e(LOG_TAG, "setVtCallForwardingOption: not possible in CDMA");
    }
    public void getVtCallWaiting(Message onComplete) {
        Rlog.e(LOG_TAG, "getVtCallWaiting: not possible in CDMA");
    }

    public void setVtCallWaiting(boolean enable, Message onComplete) {
        Rlog.e(LOG_TAG, "setVtCallWaiting: not possible in CDMA");
    }

    public void getVtFacilityLock(String facility, String password, Message onComplete) {
        Rlog.e(LOG_TAG, "getVtFacilityLock: not possible in CDMA");
    }

    public void setVtFacilityLock(String facility, boolean enable, String password, Message onComplete) {
        Rlog.e(LOG_TAG, "setVtFacilityLock: not possible in CDMA");
    }
    /* vt end */

     //via support start
    private int getSimIndicatorStateFromStates(IccCardConstants.State simState, ServiceState svState, PhoneConstants.DataState dataState){
    	int retState = PhoneConstants.SIM_INDICATOR_UNKNOWN;
    	if(simState.isLocked()){
    		retState = PhoneConstants.SIM_INDICATOR_LOCKED;
    	}else {
    		int nSvState = svState.getState();
    		int nRegState = svState.getRegState();
    		if(nSvState == ServiceState.STATE_POWER_OFF){
            //support by via start [ALPS00397824]
                int dualSimMode = Settings.System.getInt(getContext().getContentResolver(),
                            Settings.System.DUAL_SIM_MODE_SETTING, -1);
                boolean simOn = !((dualSimMode & (mySimId + 1)) == 0);
                Rlog.d(LOG_TAG, "getSimIndicatorStateFromStates dualSimMode = " +  dualSimMode + ", simOn = " +  simOn);
                if(nRegState == ServiceState.REGISTRATION_STATE_NOT_REGISTERED_AND_NOT_SEARCHING
                && (0 == Settings.System.getInt(getContext().getContentResolver(), Settings.System.AIRPLANE_MODE_ON, -1))
                && simOn) {
                    retState = PhoneConstants.SIM_INDICATOR_SEARCHING;
                } else {
                    retState = PhoneConstants.SIM_INDICATOR_RADIOOFF;
                }
                Rlog.d(LOG_TAG, "getSimIndicatorStateFromStates simState" +  simState
                                 + ", svState " + svState
                                 + ", dataState " + dataState
                                 + ", retState " + retState);
            //support by via end [ALPS00397824]
    		}else if(nSvState == ServiceState.STATE_OUT_OF_SERVICE){			   
    			if(nRegState == ServiceState.REGISTRATION_STATE_NOT_REGISTERED_AND_SEARCHING) {
    				retState = PhoneConstants.SIM_INDICATOR_SEARCHING;
    			}else {
    				retState = PhoneConstants.SIM_INDICATOR_INVALID;
    			}
    		}else if(nSvState == ServiceState.STATE_IN_SERVICE){
    			if (dataState == PhoneConstants.DataState.CONNECTED){
    				retState = svState.getRoaming()? PhoneConstants.SIM_INDICATOR_ROAMINGCONNECTED:
    												  PhoneConstants.SIM_INDICATOR_CONNECTED;
    			   
    			} else {
    				retState = svState.getRoaming()? PhoneConstants.SIM_INDICATOR_ROAMING:
    												  PhoneConstants.SIM_INDICATOR_NORMAL;
    			}
    		}
    	}
    	return retState;
    
    }

    private void broadcastSimIndStateChangedIntent(int nState) {
        Intent intent = new Intent(TelephonyIntents.ACTION_SIM_INDICATOR_STATE_CHANGED);
        intent.putExtra(TelephonyIntents.INTENT_KEY_ICC_STATE, nState);
        intent.putExtra(TelephonyIntents.INTENT_KEY_ICC_SLOT, getMySimId());
        Rlog.d(LOG_TAG, "Broadcasting intent ACTION_SIM_INDICATOR_STATE_CHANGED " +  nState
                + " sim id " + getMySimId());
        ActivityManagerNative.broadcastStickyIntent(intent, READ_PHONE_STATE, UserHandle.USER_ALL);
    }

    public void updateSimIndicateState(){
	//IccCardConstants.State simState = getIccCard().getState();

	    IccCardConstants.State simState = IccCardConstants.State.UNKNOWN;
        String prop = (getMySimId() == PhoneConstants.GEMINI_SIM_1)
                      ? SystemProperties.get(TelephonyProperties.PROPERTY_SIM_STATE)
                      : SystemProperties.get(TelephonyProperties.PROPERTY_SIM_STATE_2);

        if(prop.equals("ABSENT")){
           simState = IccCardConstants.State.ABSENT;
        } else if(prop.equals("PIN_REQUIRED")) {
            simState = IccCardConstants.State.PIN_REQUIRED;
        } else if(prop.equals("PUK_REQUIRED")) {
            simState = IccCardConstants.State.PUK_REQUIRED;
        } else if(prop.equals("NETWORK_LOCKED")) {
            simState = IccCardConstants.State.NETWORK_LOCKED;
        } else if(prop.equals("READY")) {
            simState = IccCardConstants.State.READY;
        } else if(prop.equals("NOT_READY")) {
            simState = IccCardConstants.State.NOT_READY;
        } else if(prop.equals("PERM_DISABLED")) {
            simState = IccCardConstants.State.PERM_DISABLED;
        }
	    PhoneConstants.DataState dataState = getDataConnectionState();
	    ServiceState svState = getServiceState();

    	//cdma 
    	//Need to check the call state of peer phone and change the data state if there is a active call in peer phone.
    	/*if(FeatureOption.MTK_GEMINI_SUPPORT){
    	   CDMAPhone peerPhone = getPeerPhone();
    	   if(peerPhone != null){
    			if(peerPhone.isInCall()){
    				if(DataState.CONNECTED == dataState){
    					dataState = DataState.SUSPENDED;
    				}
    			}
    	   }
    	}*/
    
    	Rlog.d(LOG_TAG, "updateSimIndicateState simState is " + simState + " dataState is " 
    									  + dataState + " svState is " + svState);
    	int newState = getSimIndicatorStateFromStates(simState, svState,dataState);
    	if (mSimIndicatorState != newState){
    		mSimIndicatorState = newState;
    		broadcastSimIndStateChangedIntent(newState);
    	}
    	Rlog.d(LOG_TAG, "updateSimIndicateState new state is " + newState);
    }

    public int getSimIndicateState(){
        return mSimIndicatorState;
    }
    //via support end

    public int getMySimId() {
       return mySimId;
    }

    public void setCdmaConnType(int type) {
        mSST.setCdmaConnType(type);
    }

    // via support start
    //add this function for MTK's resaon, override---20130715
    public void setPsConnType(int type) {
        mSST.setCdmaConnType(type);
    }
    // via support end

     //via support start
    private void queryUimInsertStatusDelay(long delayMillis) {
        Rlog.d(LOG_TAG, "to queryUimInsertStatusDelay, delayMillis = " + delayMillis);
        sendEmptyMessageDelayed(EVENT_GET_UIM_INSERT_STATUS_RETRY, delayMillis);
    }
    //via support end
    public void requestSwitchHPF (boolean enableHPF, Message response) {
        Rlog.d(LOG_TAG, "switch HPF to " + enableHPF);
        mCi.requestSwitchHPF(enableHPF, response);
    }

    public void setAvoidSYS (boolean avoidSYS, Message response) {
        Rlog.d(LOG_TAG, "avoid sys " + avoidSYS);
        mCi.setAvoidSYS(avoidSYS, response);
    }

    public void getAvoidSYSList (Message response) {
        Rlog.d(LOG_TAG, "get avoid sys list");
        mCi.getAvoidSYSList(response);
    }
    public void queryCDMANetworkInfo (Message response){
        Rlog.d(LOG_TAG, "query CDMA network infor");
        mCi.queryCDMANetworkInfo(response);
    }
    //add by via begin [ALPS00402795]
    public boolean isRadioAvailable() {
        return mIsRadioAvailable;
    }
    ////add by via end [ALPS00402795]
    //add by via begin [ALPS00384791]
    public String getUimid() {
        Rlog.d(LOG_TAG, "getUimid() = " + mUimId);

        if(mUimId != null){
            Rlog.d(LOG_TAG, "getUimid().toUpperCase() =" + mUimId.toUpperCase());
            return mUimId.toUpperCase();
        }
        else{
            return null;
        }
    }

    public String getSid() {
        return mSST.getSid();
    }

    public String getNid() {
        return mSST.getNid();
    }

    public String getPrl() {
        return mSST.getPrlVersion();
    }
    //add by via end [ALPS00384791]
    //The meid is only supported for 14 chars of length [ALPS00389658]
    public void setMeid(String meid){
        Rlog.e(LOG_TAG, "setMeid() Meid = " + meid);
        mCi.setMeid(meid, obtainMessage(EVENT_SET_MEID_DONE));
    }

    public Uri getCarriersContentUri() {
        if (FeatureOption.MTK_GEMINI_SUPPORT) {
            if (mySimId == PhoneConstants.GEMINI_SIM_2) {
                return Telephony.Carriers.SIM2Carriers.CONTENT_URI;
            } else {
                return Telephony.Carriers.SIM1Carriers.CONTENT_URI;
            }
        }
        return Telephony.Carriers.CONTENT_URI;
    }

    public void hangupAllEx() throws CallStateException {
        Rlog.d(LOG_TAG, "hangupAllEx");
        hangupAll();
    }

    public void notifySimMissingStatus(boolean isSimInsert) {
        if(!isSimInsert) {
            Rlog.d(LOG_TAG, "[notifySimMissingStatus, card is not present]");
            mCi.notifySimMissing();
        } else {
            Rlog.d(LOG_TAG, "[notifySimMissingStatus, card is present]");
        }
    }
    //8.2.11 TC-IRLAB-02011 start
    public void registerForMccMncChange(Handler h, int what, Object obj) {
        Registrant r = new Registrant (h, what, obj);
        mMccMncChangeRegistrants.add(r);       
        
        mCpPauseEnable = true;
        mCi.enableCdmaRegisterPause(true, null);
        Rlog.d(LOG_TAG, "registerForMccMncChange, mCpPauseEnable="+mCpPauseEnable);
    }
    public void unregisterForMccMncChange(Handler h) {
        mMccMncChangeRegistrants.remove(h);
        if (mMccMncChangeRegistrants.size() == 0) {
            mCpPauseEnable = false;
            mCi.enableCdmaRegisterPause(false, null);
        }
    }
    public void resumeCdmaRegister(Message result){
        mSST.enableServiceStateNotify(true);
        mCi.resumeCdmaRegister(result);
    }
    //8.2.11 TC-IRLAB-02011 end
    public void setTRM(int mode, Message response) {
        Rlog.d(LOG_TAG, "CDMAPhone setTRM mode = " + mode);
        mCi.setViaTRM(mode, response);
    }
    //via support end

    //VIA-START SET ARSI REPORT TRRESHOLD-20130709
    //threshold value : 0 - 31,The smaller the value, the more frequent reporting signal.
    //default value used by cp is 2.
    public void setArsiReportThreshold(int threshold) {
        Rlog.d(LOG_TAG, "setArsiReportThreshold between(0,31) threshold = " + threshold);
        if(threshold < 0 || threshold > 31) { 
            Rlog.d(LOG_TAG, "setArsiReportThreshold threshold = " + threshold + " invalid");
            return;
        }
        mCi.setArsiReportThreshold(threshold, null);
    }
    //VIA-START SET ARSI REPORT TRRESHOLD-20130709
    /// @}

    //via support start-20130719 for MTK's require
    public String checkMccBySidLtmOff(String mccMnc){
        if(mccMnc == null || mccMnc.length() == 0) {
            return mccMnc;
        }

        return PlusCodeToIddNddUtils.checkMccBySidLtmOff(mccMnc);

    }

    public boolean canFormatPlusCode(boolean isCall){
        if(isCall) {
            return PlusCodeToIddNddUtils.canFormatPlusToIddNdd();
        } else {
            return PlusCodeToIddNddUtils.canFormatPlusCodeForSms();
        }
    }

    public String replacePlusCodeWithIdd(String number, boolean isCall){
        if(isCall) {
            return PlusCodeToIddNddUtils.replacePlusCodeWithIddNdd(number);
        } else {
            return PlusCodeToIddNddUtils.removeIddNddAddPlusCodeForSms(number);
        }
    }

    public String replaceIddWithPlusCode(String number, boolean isCall){
        if(isCall) {
            return PlusCodeToIddNddUtils.removeIddNddAddPlusCode(number);
        } else {
            return PlusCodeToIddNddUtils.removeIddNddAddPlusCodeForSms(number);
        }
    }
    //via support end-20130719
}
