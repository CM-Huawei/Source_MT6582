/* Copyright Statement:
 *
 * This software/firmware and related documentation ("MediaTek Software") are
 * protected under relevant copyright laws. The information contained herein
 * is confidential and proprietary to MediaTek Inc. and/or its licensors.
 * Without the prior written permission of MediaTek inc. and/or its licensors,
 * any reproduction, modification, use or disclosure of MediaTek Software,
 * and information contained herein, in whole or in part, shall be strictly prohibited.
 */
/* MediaTek Inc. (C) 2010. All rights reserved. 
 *
 * BY OPENING THIS FILE, RECEIVER HEREBY UNEQUIVOCALLY ACKNOWLEDGES AND AGREES
 * THAT THE SOFTWARE/FIRMWARE AND ITS DOCUMENTATIONS ("MEDIATEK SOFTWARE")
 * RECEIVED FROM MEDIATEK AND/OR ITS REPRESENTATIVES ARE PROVIDED TO RECEIVER ON
 * AN "AS-IS" BASIS ONLY. MEDIATEK EXPRESSLY DISCLAIMS ANY AND ALL WARRANTIES,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE OR NONINFRINGEMENT.
 * NEITHER DOES MEDIATEK PROVIDE ANY WARRANTY WHATSOEVER WITH RESPECT TO THE
 * SOFTWARE OF ANY THIRD PARTY WHICH MAY BE USED BY, INCORPORATED IN, OR
 * SUPPLIED WITH THE MEDIATEK SOFTWARE, AND RECEIVER AGREES TO LOOK ONLY TO SUCH
 * THIRD PARTY FOR ANY WARRANTY CLAIM RELATING THERETO. RECEIVER EXPRESSLY ACKNOWLEDGES
 * THAT IT IS RECEIVER'S SOLE RESPONSIBILITY TO OBTAIN FROM ANY THIRD PARTY ALL PROPER LICENSES
 * CONTAINED IN MEDIATEK SOFTWARE. MEDIATEK SHALL ALSO NOT BE RESPONSIBLE FOR ANY MEDIATEK
 * SOFTWARE RELEASES MADE TO RECEIVER'S SPECIFICATION OR TO CONFORM TO A PARTICULAR
 * STANDARD OR OPEN FORUM. RECEIVER'S SOLE AND EXCLUSIVE REMEDY AND MEDIATEK'S ENTIRE AND
 * CUMULATIVE LIABILITY WITH RESPECT TO THE MEDIATEK SOFTWARE RELEASED HEREUNDER WILL BE,
 * AT MEDIATEK'S OPTION, TO REVISE OR REPLACE THE MEDIATEK SOFTWARE AT ISSUE,
 * OR REFUND ANY SOFTWARE LICENSE FEES OR SERVICE CHARGE PAID BY RECEIVER TO
 * MEDIATEK FOR SUCH MEDIATEK SOFTWARE AT ISSUE.
 *
 * The following software/firmware and/or related documentation ("MediaTek Software")
 * have been modified by MediaTek Inc. All revisions are subject to any receiver's
 * applicable license agreements with MediaTek Inc.
 */

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

import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.SQLException;
import android.net.Uri;
import android.os.AsyncResult;
import android.os.Handler;
import android.os.Message;
import android.os.Registrant;
import android.os.RegistrantList;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.preference.PreferenceManager;
import android.provider.Telephony;
import android.telephony.CellLocation;
import android.telephony.PhoneNumberUtils;
import android.telephony.ServiceState;
import com.android.internal.telephony.CallTracker;
import android.text.TextUtils;
import android.telephony.Rlog;

import static com.android.internal.telephony.CommandsInterface.CF_ACTION_DISABLE;
import static com.android.internal.telephony.CommandsInterface.CF_ACTION_ENABLE;
import static com.android.internal.telephony.CommandsInterface.CF_ACTION_ERASURE;
import static com.android.internal.telephony.CommandsInterface.CF_ACTION_REGISTRATION;
import static com.android.internal.telephony.CommandsInterface.CF_REASON_ALL;
import static com.android.internal.telephony.CommandsInterface.CF_REASON_ALL_CONDITIONAL;
import static com.android.internal.telephony.CommandsInterface.CF_REASON_NO_REPLY;
import static com.android.internal.telephony.CommandsInterface.CF_REASON_NOT_REACHABLE;
import static com.android.internal.telephony.CommandsInterface.CF_REASON_BUSY;
import static com.android.internal.telephony.CommandsInterface.CF_REASON_UNCONDITIONAL;
import static com.android.internal.telephony.CommandsInterface.SERVICE_CLASS_VOICE;
import static com.android.internal.telephony.TelephonyProperties.PROPERTY_BASEBAND_VERSION;
import static com.android.internal.telephony.TelephonyProperties.PROPERTY_BASEBAND_VERSION_2;

import com.android.internal.telephony.SmsBroadcastUndelivered;
import com.android.internal.telephony.dataconnection.DcTracker;
import com.android.internal.telephony.CallForwardInfo;
import com.android.internal.telephony.CallStateException;
import com.android.internal.telephony.CommandsInterface;
import com.android.internal.telephony.Connection;
import com.android.internal.telephony.IccPhoneBookInterfaceManager;
import com.android.internal.telephony.IccSmsInterfaceManager;
import com.android.internal.telephony.MmiCode;
import com.android.internal.telephony.OperatorInfo;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneBase;
import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.PhoneNotifier;
import com.android.internal.telephony.PhoneProxy;
import com.android.internal.telephony.PhoneSubInfo;
import com.android.internal.telephony.TelephonyProperties;
import com.android.internal.telephony.UUSInfo;
import com.android.internal.telephony.test.SimulatedRadioControl;
import com.android.internal.telephony.uicc.IccRecords;
import com.android.internal.telephony.uicc.IccVmNotSupportedException;
import com.android.internal.telephony.uicc.UiccCardApplication;
import com.android.internal.telephony.uicc.UiccController;
import com.android.internal.telephony.ServiceStateTracker;

import java.io.FileDescriptor;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

//MTK-START [mtk04070][111118][ALPS00093395]MTK added
import android.content.BroadcastReceiver;
import android.content.IntentFilter;
import android.provider.Telephony.SIMInfo;
import android.telephony.SignalStrength;
import com.android.internal.telephony.cat.CatService;
import com.android.internal.telephony.IccCardConstants;
import com.android.internal.telephony.uicc.IccFileHandler;
import com.android.internal.telephony.uicc.SIMRecords;
import com.android.internal.telephony.PhoneConstants;

import static android.Manifest.permission.READ_PHONE_STATE;
import android.app.ActivityManagerNative;
import android.content.Intent;
import android.provider.Settings;
import com.android.internal.telephony.TelephonyIntents;
import com.android.internal.telephony.IccCard;
import com.mediatek.common.featureoption.FeatureOption;
import com.android.internal.telephony.PhoneFactory;
import com.android.internal.telephony.gemini.GeminiPhone;
/* Add by vendor */
import com.android.internal.telephony.DctConstants.State;
import com.android.internal.telephony.PhoneConstants.DataState;
//MTK-END [mtk04070][111118][ALPS00093395]MTK added
  
// ALPS00294581 Replace "RIL_UNSOL_SIM_MISSING in RIL.java" with "acively query SIM missing status"
import android.app.Notification;
import android.app.NotificationManager;
// ALPS00294581 Replace "RIL_UNSOL_SIM_MISSING in RIL.java" with "acively query SIM missing status"
import com.android.internal.telephony.PhoneFactory;
import com.mediatek.common.MediatekClassFactory;
import com.mediatek.common.telephony.IWorldPhone;
import com.mediatek.common.telephony.gsm.FemtoCellInfo;

//[New R8 modem FD]
import com.android.internal.telephony.gsm.FDTimer;


/**
 * {@hide}
 */
public class GSMPhone extends PhoneBase {
    // NOTE that LOG_TAG here is "GSM", which means that log messages
    // from this file will go into the radio log rather than the main
    // log.  (Use "adb logcat -b radio" to see them.)
    static final String LOG_TAG = "GSMPhone";
    private static final boolean LOCAL_DEBUG = true;
    private static final boolean VDBG = false; /* STOPSHIP if true */
    private static final boolean DBG_PORT = false; /* STOPSHIP if true */

    // Key used to read/write current ciphering state
    public static final String CIPHERING_KEY = "ciphering_key";
    // Key used to read/write voice mail number
    public static final String VM_NUMBER = "vm_number_key";
    // Key used to read/write the SIM IMSI used for storing the voice mail
    public static final String VM_SIM_IMSI = "vm_sim_imsi_key";

    // Instance Variables
    GsmCallTracker mCT;
    //[Mr2] change protect to public
    //public GsmServiceStateTracker mSST;
    GsmServiceStateTracker mSST;
    GsmSMSDispatcher mSMS;
    SIMRecords mSIMRecords;
    ArrayList <GsmMmiCode> mPendingMMIs = new ArrayList<GsmMmiCode>();
    SimPhoneBookInterfaceManager mSimPhoneBookIntManager;
    PhoneSubInfo mSubInfo;

    CatService mStkService;

    Registrant mPostDialHandler;

    /** List of Registrants to receive Supplementary Service Notifications. */
    RegistrantList mSsnRegistrants = new RegistrantList();

    Thread mDebugPortThread;
    ServerSocket mDebugSocket;

    private String mImei;
    private String mImeiSv;
    private String mVmNumber;

    //MTK-START [mtk04070][111118][ALPS00093395]MTK added
    public static final String UTRAN_INDICATOR = "3G";
    public static final String LTE_INDICATOR = "4G";

    public static final String ACT_TYPE_GSM = "0";
    public static final String ACT_TYPE_UTRAN = "2";
    public static final String ACT_TYPE_LTE = "7";

    //MTK-START [ALPS00444610] MTK added
////    private static final String PROPERTY_ICCID_SIM1 = "ril.iccid.sim1";
////    private static final String PROPERTY_ICCID_SIM2 = "ril.iccid.sim2";
    private String[] PROPERTY_ICCID_SIM = {
        "ril.iccid.sim1",
        "ril.iccid.sim2",
        "ril.iccid.sim3",
        "ril.iccid.sim4",
    };

    private String[] PROPERTY_SIM_STATE = {
        TelephonyProperties.PROPERTY_SIM_STATE,
        TelephonyProperties.PROPERTY_SIM_STATE_2,
        TelephonyProperties.PROPERTY_SIM_STATE_3,
        TelephonyProperties.PROPERTY_SIM_STATE_4,
    };

    private static final String ICCID_STRING_FOR_NO_SIM = "N/A";

    private static final String CFU_QUERY_ICCID_PROP = "persist.radio.cfu.iccid.";
    private static final String CFU_QUERY_SIM_CHANGED_PROP = "persist.radio.cfu.change.";
    
    /** List of Registrants to receive CRSS Notifications. */
    RegistrantList mCallRelatedSuppSvcRegistrants = new RegistrantList();

    private int mReportedRadioResets;
    private int mReportedAttemptedConnects;
    private int mReportedSuccessfulConnects;
    private int mSimIndicatorState = PhoneConstants.SIM_INDICATOR_UNKNOWN;

    private static final int EVENT_GET_3G_CAPABILITY_WHEN_RADIO_AVAILABLE = 500;
    /**
    *   For Gemini project, use peerPhone to keep the instance of other peer phone.
    */
    private GSMPhone mPeerPhone = null;
    private GSMPhone mPeerPhones[] = {null,null,null,null};
    
    /**
    * mImeiAbnormal=0, Valid IMEI
    * mImeiAbnormal=1, IMEI is null or not valid format
    * mImeiAbnormal=2, Phone1/Phone2 have same IMEI
    */
    private int mImeiAbnormal = 0;

    //Add by mtk80372 for Barcode Number
    private String mSN;

    /* 3G switch start */
    private static int m3GCapabilitySIM = -1;
    private static int mTargetNetworkMode;
    private boolean mIsToResetRadio;
    private boolean mIsRadioAvailable;
    /* 3G switch end */
    
    /* MTK World Phone start*/
    public IWorldPhone mWorldPhone;
    /* MTK World Phone end*/
    
    private static final int EVENT_QUERY_AVAILABLE_NETWORK = 0x500;
    private static final int EVENT_ABORT_QUERY_AVAILABLE_NETWORK = 0x501;

    private boolean needQueryCfu = true;
	
    /* To solve [ALPS00455020]No CFU icon showed on status bar. */
    private boolean mIsCfuRegistered = false;

    private ArrayList<String> mConnectedApns;
    
    /* For solving ALPS01023811
       To determine if CFU query is for power-on query. 
    */
    private int mCfuQueryRetryCount = 0;
    private static final String CFU_QUERY_PROPERTY_NAME = "gsm.poweron.cfu.query.";
    private static final int cfuQueryWaitTime = 1000;
    private static final int CFU_QUERY_MAX_COUNT = 60;
    
    //MTK-END [mtk04070][111118][ALPS00093395]MTK added

    // for cipher
    private int mSimEnableCipher = -1;
    private int mCsCipherStatus = 1;
    private int mPsCipherStatus = 1;

    // Create Cfu (Call forward unconditional) so that dialling number &
    // mOnComplete (Message object passed by client) can be packed &
    // given as a single Cfu object as user data to RIL.
    private static class Cfu {
        final String mSetCfNumber;
        final Message mOnComplete;

        Cfu(String cfNumber, Message onComplete) {
            mSetCfNumber = cfNumber;
            mOnComplete = onComplete;
        }
    }
    
    // Constructors
    //MTK-START [mtk04070][111118][ALPS00093395]Add and modified constructor methods
    public
    GSMPhone (Context context, CommandsInterface ci, PhoneNotifier notifier, int simId) {
        this(context, ci, notifier, false, simId);
    }

    public
    GSMPhone (Context context, CommandsInterface ci, PhoneNotifier notifier, boolean unitTestMode) {
        this(context,ci,notifier, unitTestMode, PhoneConstants.GEMINI_SIM_1);
    }

    public
    GSMPhone (Context context, CommandsInterface ci, PhoneNotifier notifier) {
        this(context,ci,notifier, false, PhoneConstants.GEMINI_SIM_1);
    }

    public
    GSMPhone (Context context, CommandsInterface ci, PhoneNotifier notifier, boolean unitTestMode, int simId) {
        super("GSM", notifier, context, ci, unitTestMode, simId);

        if (ci instanceof SimulatedRadioControl) {
            mSimulatedRadioControl = (SimulatedRadioControl) ci;
        }

        mCi.setPhoneType(PhoneConstants.PHONE_TYPE_GSM);
        //To do: It must be refactory.
        //START
        //mIccCard.set(UiccController.getInstance().getUiccCard());
        //END
        mCT = new GsmCallTracker(this);
        mSST = new GsmServiceStateTracker (this);

        mDcTracker = new DcTracker(this);
        if (!unitTestMode) {
            mSimPhoneBookIntManager = new SimPhoneBookInterfaceManager(this);
            mSubInfo = new PhoneSubInfo(this);
        }
        
        // mStkService = CatService.getInstance(this, mCi, mIccRecords, mContext, getIccFileHandler(), mIccCard.get(), simId);

        mCi.registerForAvailable(this, EVENT_RADIO_AVAILABLE, null);
        registerForSimRecordEvents();
        mCi.registerForOffOrNotAvailable(this, EVENT_RADIO_OFF_OR_NOT_AVAILABLE, null);
        mCi.registerForNotAvailable(this, EVENT_RADIO_NOT_AVAILABLE, null);
        mCi.registerForOn(this, EVENT_RADIO_ON, null);
        mCi.setOnUSSD(this, EVENT_USSD, null);
        mCi.setOnSuppServiceNotification(this, EVENT_SSN, null);
        mSST.registerForNetworkAttached(this, EVENT_REGISTERED_TO_NETWORK, null);

        mCT.registerForVoiceCallIncomingIndication(this,EVENT_VOICE_CALL_INCOMING_INDICATION,null);

        /* register for CRSS Notification */
        mCi.setOnCallRelatedSuppSvc(this, EVENT_CRSS_IND, null);
        //Add by mtk80372 for Barcode Number
        mCi.registerForSN(this, EVENT_GET_BARCODE_NUMBER, null);
        mCi.registerForCipherIndication(this, EVENT_CIPHER_INDICATION, null);

        if (DBG_PORT) {
            try {
                //debugSocket = new LocalServerSocket("com.android.internal.telephony.debug");
                mDebugSocket = new ServerSocket();
                mDebugSocket.setReuseAddress(true);
                mDebugSocket.bind (new InetSocketAddress("127.0.0.1", 6666));

                mDebugPortThread
                    = new Thread(
                        new Runnable() {
                            @Override
                            public void run() {
                                for(;;) {
                                    try {
                                        Socket sock;
                                        sock = mDebugSocket.accept();
                                        Rlog.i(LOG_TAG, "New connection; resetting radio");
                                        mCi.resetRadio(null);
                                        sock.close();
                                    } catch (IOException ex) {
                                        LOGW("Exception accepting socket");
                                        ex.printStackTrace();
                                    }
                                }
                            }
                        },
                        "GSMPhone debug");

                mDebugPortThread.start();

            } catch (IOException ex) {
                LOGW("Failure to open com.android.internal.telephony.debug socket");
                ex.printStackTrace();
            }
        }

        //Change the system property
        SystemProperties.set(TelephonyProperties.CURRENT_ACTIVE_PHONE,
                new Integer(PhoneConstants.PHONE_TYPE_GSM).toString());

        mTargetNetworkMode = Settings.Global.getInt(getContext().getContentResolver(),
            Settings.Global.PREFERRED_NETWORK_MODE, NT_MODE_WCDMA_PREF);
        LOGD("GSMPhone[" + mySimId + "] initialized, network mode: " + mTargetNetworkMode);

        final IntentFilter filter = new IntentFilter();
        filter.addAction(TelephonyIntents.ACTION_SIM_INFO_UPDATE);
        context.registerReceiver(mBroadcastReceiver, filter);

        mConnectedApns = new ArrayList<String>();
        /* Solve [ALPS00279022][Rose][MT6577][Free Test][FM Radio]Some icons are unavailable after you do Modem reset. */
        /* Notify phone state as IDLE when phone app process restarts, mtk04070, 20120512 */
        LOGD("GSMPhone[" + mySimId + "] initialized, notifyPhoneStateChanged");
        notifyPhoneStateChanged();
        
        /* MTK World Phone start*/
        if (PhoneFactory.isWorldPhone() && !FeatureOption.MTK_GEMINI_SUPPORT) {
            try {
                String optr = SystemProperties.get("ro.operator.optr");
                if (optr != null && optr.equals("OP01")) {
    	            mWorldPhone = MediatekClassFactory.createInstance(IWorldPhone.class, IWorldPhone.POLICY_OP01, context, this);
    	        } else {
                    mWorldPhone = MediatekClassFactory.createInstance(IWorldPhone.class, IWorldPhone.POLICY_OM, context, this);
                }
            } catch (Exception e) {
    	        e.printStackTrace();
    	    }
        }
        // /M: For international roaming update the oplmn file.
        /*if (FeatureOption.EVDO_IR_SUPPORT) {
            mSST.updateOplmn();
            LOGD("gsm update oplmn file");
        }*/
    }
    //MTK-END [mtk04070][111118][ALPS00093395]Add and modified constructor methods

    @Override
    public void dispose() {
        synchronized(PhoneProxy.lockForRadioTechnologyChange) {
            super.dispose();
            
            /* MTK World Phone start*/
            if (PhoneFactory.isWorldPhone() && !FeatureOption.MTK_GEMINI_SUPPORT) {
                mWorldPhone.disposeWorldPhone();
            }
            /* MTK World Phone end*/

            //Unregister from all former registered events
            mCi.unregisterForAvailable(this); //EVENT_RADIO_AVAILABLE
            unregisterForSimRecordEvents();
            mCi.unregisterForOffOrNotAvailable(this); //EVENT_RADIO_OFF_OR_NOT_AVAILABLE
            mCi.unregisterForNotAvailable(this); //EVENT_RADIO_NOT_AVAILABLE
            mCi.unregisterForOn(this); //EVENT_RADIO_ON
            mSST.unregisterForNetworkAttached(this); //EVENT_REGISTERED_TO_NETWORK
            mCi.unSetOnUSSD(this);
            mCi.unSetOnSuppServiceNotification(this);
            
            //MTK-START [mtk04070][111118][ALPS00093395]MTK added
            //Add by mtk80372 for Barcode Number
            mCi.unregisterForSN(this);
            //MTK-END [mtk04070][111118][ALPS00093395]MTK added
            mCi.unregisterForCipherIndication(this);

            mPendingMMIs.clear();

            mCT.unregisterForVoiceCallIncomingIndication(this);
            //Force all referenced classes to unregister their former registered events
            mCT.dispose();
            mDcTracker.dispose();
            mSST.dispose();
            mSimPhoneBookIntManager.dispose();
            mSubInfo.dispose();

            removeCallbacksAndMessages(null);

        }
    }

    @Override
    public void removeReferences() {
        LOGD("removeReferences");
        mSimulatedRadioControl = null;
        mSimPhoneBookIntManager = null;
        mSubInfo = null;
        mCT = null;
        mSST = null;
        super.removeReferences();
    }

    @Override
    protected void finalize() {
        if(LOCAL_DEBUG) LOGD("GSMPhone finalized");
    }


    @Override
    public ServiceState
    getServiceState() {
        if (mSST != null) {
            return mSST.mSS;
        } else {
            // avoid potential NPE in EmergencyCallHelper during Phone switch
            return new ServiceState();
        }
    }

    @Override
    public CellLocation getCellLocation() {
        return mSST.getCellLocation();
    }

    @Override
    public PhoneConstants.State getState() {
        return mCT.mState;
    }

    @Override
    public int getPhoneType() {
        return PhoneConstants.PHONE_TYPE_GSM;
    }

    @Override
    public SignalStrength getSignalStrength() {
        //MTK-START [ALPS415367]For MR1 Migration
        //return mSST.mSignalStrength;
        return mSST.getSignalStrength();
        //MTK-END [ALPS415367]For MR1 Migration
    }

    @Override
    public ServiceStateTracker getServiceStateTracker() {
        return mSST;
    }

    @Override
    public CallTracker getCallTracker() {
        return mCT;
    }
	
    public List<? extends MmiCode>
    getPendingMmiCodes() {
        return mPendingMMIs;
    }

    @Override
    public PhoneConstants.DataState getDataConnectionState(String apnType) {
        PhoneConstants.DataState ret = PhoneConstants.DataState.DISCONNECTED;

        //MTK-START [mtk04070][111213][ALPS00093395] Temporary solution to avoid apnType NullException
        if (apnType == null) {
            apnType = "";
        }
        //MTK-END [mtk04070][111213][ALPS00093395] Temporary solution to avoid apnType NullException

        if (mSST == null) {
            // Radio Technology Change is ongoning, dispose() and removeReferences() have
            // already been called

            ret = PhoneConstants.DataState.DISCONNECTED;
        } else if (mSST.getCurrentDataConnectionState()
                != ServiceState.STATE_IN_SERVICE) {
            // If we're out of service, open TCP sockets may still work
            // but no data will flow
            String psNetworkType = "";
            switch (getMySimId()) {
                case PhoneConstants.GEMINI_SIM_1:
                    psNetworkType = SystemProperties.get(TelephonyProperties.PROPERTY_DATA_NETWORK_TYPE);
                    break;
                case PhoneConstants.GEMINI_SIM_2:
                    psNetworkType = SystemProperties.get(TelephonyProperties.PROPERTY_DATA_NETWORK_TYPE_2);
                    break;
                case PhoneConstants.GEMINI_SIM_3:
                    psNetworkType = SystemProperties.get(TelephonyProperties.PROPERTY_DATA_NETWORK_TYPE_3);
                    break;
                case PhoneConstants.GEMINI_SIM_4:
                    psNetworkType = SystemProperties.get(TelephonyProperties.PROPERTY_DATA_NETWORK_TYPE_4);
                    break;
                default:
                    psNetworkType = SystemProperties.get(TelephonyProperties.PROPERTY_DATA_NETWORK_TYPE);
                    break;
            }
            int gprsState = 0;//mSST.getCurrentGprsState();
            if (gprsState == ServiceState.STATE_OUT_OF_SERVICE && "Unknown".equals(psNetworkType)) {
                LOGD("GSMPhone[" + mySimId + "] PS out of service and GPRS detached, status is disconnected");
                ret = PhoneConstants.DataState.DISCONNECTED;
            } else {
                if (gprsState == ServiceState.STATE_OUT_OF_SERVICE) {
                    //since we are GPRS attached but currently PS out of service
                    //this means we are in searching state and may be recovered
                    if (!mDcTracker.isApnTypeEnabled(apnType) || !mDcTracker.isApnTypeActive(apnType)) {
                        LOGD("GSMPhone[" + mySimId + "] PS out of service but GPRS attached, status align APN state (DISCONNECTED)");
                        ret = PhoneConstants.DataState.DISCONNECTED;
                    } else {
                        switch (mDcTracker.getState(apnType)) {
                            case RETRYING:
                                LOGD("apnType: " + apnType + " is in retrying state!! return connecting state");
                                ret = PhoneConstants.DataState.CONNECTING;
                                break;
                            case FAILED:
                            case IDLE:
                                ret = PhoneConstants.DataState.DISCONNECTED;
                                break;
                            case CONNECTED:
                            case DISCONNECTING:
                                if ( mCT.mState != PhoneConstants.State.IDLE && !mSST.isConcurrentVoiceAndDataAllowed()) {
                                    ret = PhoneConstants.DataState.SUSPENDED;
                                } else {
                                    ret = PhoneConstants.DataState.CONNECTED;
                                }
                                // M: check peer phone is in call also
                                if (FeatureOption.MTK_GEMINI_SUPPORT) {
                                    for (int i=PhoneConstants.GEMINI_SIM_1; i<PhoneConstants.GEMINI_SIM_NUM; i++) {
                                        if (i != getMySimId() && getPeerPhones(i) != null && 
                                                getPeerPhones(i).getState() != PhoneConstants.State.IDLE) {
                                            Rlog.d(LOG_TAG, "GSMPhone[" + mySimId + "] Phone" + i + " is in call");
                                            ret = PhoneConstants.DataState.SUSPENDED;
                                            break;
                                        }
                                    }
                                }
                                // M: check if current data is allowed
                                if (ret == PhoneConstants.DataState.CONNECTED && getServiceState().getRoaming() &&
                                    !mDcTracker.getDataOnRoamingEnabled()) {
                                    Rlog.d(LOG_TAG, "GSMPhone[" + mySimId + "] Connected but roaming is not allowed.");
                                    ret = PhoneConstants.DataState.DISCONNECTED;
                                }
                                break;
                            //case INITING:
                            case CONNECTING:
                            case SCANNING:
                                ret = PhoneConstants.DataState.CONNECTING;
                            break;
                        }
                        LOGD("GSMPhone[" + mySimId + "] PS out of service but GPRS attached, status align APN state (" + ret + ")");
                    }
                } else {
                    ret = PhoneConstants.DataState.DISCONNECTED;
                }
                
            }
        } else if (mDcTracker.isApnTypeEnabled(apnType) == false ||
                mDcTracker.isApnTypeActive(apnType) == false) {
            //TODO: isApnTypeActive() is just checking whether ApnContext holds
            //      Dataconnection or not. Checking each ApnState below should
            //      provide the same state. Calling isApnTypeActive() can be removed.
            ret = PhoneConstants.DataState.DISCONNECTED;
        } else { /* mSST.gprsState == ServiceState.STATE_IN_SERVICE */
            switch (mDcTracker.getState(apnType)) {
                case RETRYING:
                    if(PhoneConstants.APN_TYPE_MMS.equals(apnType)) {
                        LOGD("mms is retrying!!");
                        ret = PhoneConstants.DataState.CONNECTING;
                        break;
                    }
                    
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
                    // M: check peer phone is in call also
                    if (FeatureOption.MTK_GEMINI_SUPPORT) {
                        for (int i=PhoneConstants.GEMINI_SIM_1; i<PhoneConstants.GEMINI_SIM_NUM; i++) {
                            if (i != getMySimId() && getPeerPhones(i) != null && 
                                    getPeerPhones(i).getState() != PhoneConstants.State.IDLE) {
                                Rlog.d(LOG_TAG, "GSMPhone[" + mySimId + "] Phone" + i + " is in call");
                                ret = PhoneConstants.DataState.SUSPENDED;
                                break;
                            }
                        }
                    }
                    // M: check if current data is allowed
                    if (ret == PhoneConstants.DataState.CONNECTED && getServiceState().getRoaming() &&
                        !mDcTracker.getDataOnRoamingEnabled()) {
                        Rlog.d(LOG_TAG, "GSMPhone[" + mySimId + "] Connected but roaming is not allowed.");
                        ret = PhoneConstants.DataState.DISCONNECTED;
                    }
                break;

                case CONNECTING:
                case SCANNING:
                    ret = PhoneConstants.DataState.CONNECTING;
                break;
            }
        }

        return ret;
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

    /**
     * Notify any interested party of a Phone state change
     * {@link com.android.internal.telephony.PhoneConstants.State}
     */
    /*package*/ void notifyPhoneStateChanged() {
        updateCipherIndication();
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

    /*package*/ void
    notifyNewRingingConnection(Connection c) {
        /* we'd love it if this was package-scoped*/
        super.notifyNewRingingConnectionP(c);
    }

    /*package*/ void
    notifyDisconnect(Connection cn) {
        mDisconnectRegistrants.notifyResult(cn);
    }

    void notifyUnknownConnection() {
        mUnknownConnectionRegistrants.notifyResult(this);
    }

    void notifySuppServiceFailed(SuppService code) {
        mSuppServiceFailedRegistrants.notifyResult(code);
    }

    /*package*/ void
    notifyServiceStateChanged(ServiceState ss) {
        super.notifyServiceStateChangedP(ss);
    }

    /*package*/
    void notifyLocationChanged() {
        mNotifier.notifyCellLocation(this);
    }

    ///*package*/ void
    //notifySignalStrength() {
    //    mNotifier.notifySignalStrength(this);
    //}

    @Override
    public void
    notifyCallForwardingIndicator() {
        mNotifier.notifyCallForwardingChanged(this);
    }

    // override for allowing access from other classes of this package
    /**
     * {@inheritDoc}
     */
    @Override
    public final void
    setSystemProperty(String property, String value) {
        super.setSystemProperty(property, value);
    }

    @Override
    public void registerForSuppServiceNotification(
            Handler h, int what, Object obj) {
        mSsnRegistrants.addUnique(h, what, obj);
        if (mSsnRegistrants.size() == 1) mCi.setSuppServiceNotifications(true, null);
    }

    @Override
    public void unregisterForSuppServiceNotification(Handler h) {
        mSsnRegistrants.remove(h);
        if (mSsnRegistrants.size() == 0) mCi.setSuppServiceNotifications(false, null);
    }

    public void setIncomingCallIndicationResponse(boolean accept) {
        LOGD("setIncomingCallIndicationResponse " + accept);
        mCT.setIncomingCallIndicationResponse(accept);
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
    public boolean canConference() {
        return mCT.canConference();
    }

    public boolean canDial() {
        return mCT.canDial();
    }

    @Override
    public void conference() {
        mCT.conference();
    }

    @Override
    public void clearDisconnected() {
        mCT.clearDisconnected();
    }

    @Override
    public boolean canTransfer() {
        return mCT.canTransfer();
    }

    @Override
    public void explicitCallTransfer() {
        mCT.explicitCallTransfer();
    }

    @Override
    public GsmCall
    getForegroundCall() {
        return mCT.mForegroundCall;
    }

    @Override
    public GsmCall
    getBackgroundCall() {
        return mCT.mBackgroundCall;
    }

    @Override
    public GsmCall
    getRingingCall() {
        return mCT.mRingingCall;
    }

    private boolean handleCallDeflectionIncallSupplementaryService(
            String dialString) {
        if (dialString.length() > 1) {
            return false;
        }

        if (getRingingCall().getState() != GsmCall.State.IDLE) {
            if (LOCAL_DEBUG) LOGD("MmiCode 0: rejectCall");
            try {
                mCT.rejectCall();
            } catch (CallStateException e) {
                if (LOCAL_DEBUG) LOGD("reject failed");
                e.printStackTrace();
                notifySuppServiceFailed(Phone.SuppService.REJECT);
            }
        } else if (getBackgroundCall().getState() != GsmCall.State.IDLE) {
            if (LOCAL_DEBUG) LOGD("MmiCode 0: hangupWaitingOrBackground");
            mCT.hangupWaitingOrBackground();
        }

        return true;
    }

    //MTK-START [mtk04070][111118][ALPS00093395]Replace Log.d with Cclog
    private boolean handleCallWaitingIncallSupplementaryService(
            String dialString) {
        int len = dialString.length();

        if (len > 2) {
            return false;
        }

        GsmCall call = getForegroundCall();

        try {
            if (len > 1) {
                char ch = dialString.charAt(1);
                int callIndex = ch - '0';

                if (callIndex >= 1 && callIndex <= GsmCallTracker.MAX_CONNECTIONS) {
                    if (LOCAL_DEBUG) Cclog("MmiCode 1: hangupConnectionByIndex " +
                            callIndex);
                    mCT.hangupConnectionByIndex(call, callIndex);
                } else {
                    return false;
                }
            } else {
                if (call.getState() != GsmCall.State.IDLE) {
                    if (LOCAL_DEBUG) Cclog("MmiCode 1: hangup foreground");
                    //mCT.hangupForegroundResumeBackground();
                    mCT.hangup(call);
                } else {
                    if (LOCAL_DEBUG) Cclog("MmiCode 1: switchWaitingOrHoldingAndActive");
                    mCT.switchWaitingOrHoldingAndActive();
                }
            }
        } catch (CallStateException e) {
            if (LOCAL_DEBUG) Cclog("hangup failed");
            notifySuppServiceFailed(Phone.SuppService.HANGUP);
        }

        return true;
    }

    private boolean handleCallHoldIncallSupplementaryService(String dialString) {
        int len = dialString.length();

        if (len > 2) {
            return false;
        }

        GsmCall call = getForegroundCall();

        if (len > 1) {
            try {
                char ch = dialString.charAt(1);
                int callIndex = ch - '0';
                GsmConnection conn = mCT.getConnectionByIndex(call, callIndex);

                // gsm index starts at 1, up to 5 connections in a call,
                if (conn != null && callIndex >= 1 && callIndex <= GsmCallTracker.MAX_CONNECTIONS) {
                    if (LOCAL_DEBUG) Cclog("MmiCode 2: separate call "+
                            callIndex);
                    mCT.separate(conn);
                } else {
                    if (LOCAL_DEBUG) Cclog("separate: invalid call index "+
                            callIndex);
                    notifySuppServiceFailed(Phone.SuppService.SEPARATE);
                }
            } catch (CallStateException e) {
                if (LOCAL_DEBUG) Cclog("separate failed");
                notifySuppServiceFailed(Phone.SuppService.SEPARATE);
            }
        } else {
            try {
                if (getRingingCall().getState() != GsmCall.State.IDLE) {
                    if (LOCAL_DEBUG) Cclog("MmiCode 2: accept ringing call");
                    mCT.acceptCall();
                } else {
                    if (LOCAL_DEBUG) Cclog("MmiCode 2: switchWaitingOrHoldingAndActive");
                    mCT.switchWaitingOrHoldingAndActive();
                }
            } catch (CallStateException e) {
                if (LOCAL_DEBUG) Cclog("switch failed");
                notifySuppServiceFailed(Phone.SuppService.SWITCH);
            }
        }

        return true;
    }

    private boolean handleMultipartyIncallSupplementaryService(
            String dialString) {
        if (dialString.length() > 1) {
            return false;
        }

        if (LOCAL_DEBUG) Cclog("MmiCode 3: merge calls");

        conference();

        return true;
    }

    private boolean handleEctIncallSupplementaryService(String dialString) {

        int len = dialString.length();

        if (len != 1) {
            return false;
        }

        if (LOCAL_DEBUG) Cclog("MmiCode 4: explicit call transfer");

        explicitCallTransfer();

        return true;
    }

    private boolean handleCcbsIncallSupplementaryService(String dialString) {
        if (dialString.length() > 1) {
            return false;
        }

        Cclog("MmiCode 5: CCBS not supported!");
        // Treat it as an "unknown" service.
        notifySuppServiceFailed(Phone.SuppService.UNKNOWN);
        return true;
    }
    //MTK-END [mtk04070][111118][ALPS00093395]Replace Log.d with Cclog

    @Override
    public boolean handleInCallMmiCommands(String dialString) {
        if (!isInCall()) {
            return false;
        }

        if (TextUtils.isEmpty(dialString)) {
            return false;
        }

        boolean result = false;
        char ch = dialString.charAt(0);
        switch (ch) {
            case '0':
                //MTK-START [mtk04070][111118][ALPS00093395]MTK modified
                result = handleUdubIncallSupplementaryService(
                        dialString);
                //MTK-END [mtk04070][111118][ALPS00093395]MTK modified
                break;
            case '1':
                result = handleCallWaitingIncallSupplementaryService(
                        dialString);
                break;
            case '2':
                result = handleCallHoldIncallSupplementaryService(dialString);
                break;
            case '3':
                result = handleMultipartyIncallSupplementaryService(dialString);
                break;
            case '4':
                result = handleEctIncallSupplementaryService(dialString);
                break;
            case '5':
                result = handleCcbsIncallSupplementaryService(dialString);
                break;
            default:
                break;
        }

        return result;
    }

    boolean isInCall() {
        GsmCall.State foregroundCallState = getForegroundCall().getState();
        GsmCall.State backgroundCallState = getBackgroundCall().getState();
        GsmCall.State ringingCallState = getRingingCall().getState();

       return (foregroundCallState.isAlive() ||
                backgroundCallState.isAlive() ||
                ringingCallState.isAlive());
    }

    @Override
    public Connection
    dial(String dialString) throws CallStateException {
        return dial(dialString, null);
    }

    @Override
    public Connection
    dial (String dialString, UUSInfo uusInfo) throws CallStateException {
        //MTK-START [mtk04070][111118][ALPS00093395]Add Cclog
        // Need to make sure dialString gets parsed properly
        String newDialString = PhoneNumberUtils.stripSeparators(dialString);
        Cclog("dial:" + dialString + "\n" + "newDial:" + newDialString);

        // handle in-call MMI first if applicable
        if (handleInCallMmiCommands(newDialString)) {
            return null;
        }

        // Only look at the Network portion for mmi
        String networkPortion = PhoneNumberUtils.extractNetworkPortionAlt(newDialString);
        Cclog("network portion:" + networkPortion);
        GsmMmiCode mmi = GsmMmiCode.newFromDialString(networkPortion, this, mUiccApplication.get());
        if (LOCAL_DEBUG) Cclog("dialing w/ mmi '" + mmi + "'...");
        //MTK-END [mtk04070][111118][ALPS00093395]Add Cclog

        if (mmi == null) {
            return mCT.dial(newDialString, uusInfo);
        } else if (mmi.isTemporaryModeCLIR()) {
            return mCT.dial(mmi.mDialingNumber, mmi.getCLIRMode(), uusInfo);
        } else {
            mPendingMMIs.add(mmi);
            mMmiRegistrants.notifyRegistrants(new AsyncResult(null, mmi, null));
            mmi.processCode();

            // FIXME should this return null or something else?
            return null;
        }
    }

    @Override
    public boolean handlePinMmi(String dialString) {
        GsmMmiCode mmi = GsmMmiCode.newFromDialString(dialString, this, mUiccApplication.get());

        if (mmi != null && mmi.isPinPukCommand()) {
            mPendingMMIs.add(mmi);
            mMmiRegistrants.notifyRegistrants(new AsyncResult(null, mmi, null));
            mmi.processCode();
            return true;
        }

        return false;
    }

    @Override
    public void sendUssdResponse(String ussdMessge) {
        GsmMmiCode mmi = GsmMmiCode.newFromUssdUserInput(ussdMessge, this, mUiccApplication.get());
        mPendingMMIs.add(mmi);
        mMmiRegistrants.notifyRegistrants(new AsyncResult(null, mmi, null));
        mmi.sendUssd(ussdMessge);
    }

    //MTK-START [mtk04070][111118][ALPS00093395]Replace Log with Cclog
    @Override
    public void
    sendDtmf(char c) {
        if (!PhoneNumberUtils.is12Key(c)) {
            Cclog("sendDtmf called with invalid character '" + c + "'");
        } else {
            if (mCT.mState ==  PhoneConstants.State.OFFHOOK) {
                mCi.sendDtmf(c, null);
            }
        }
    }

    @Override
    public void
    startDtmf(char c) {
        if (!PhoneNumberUtils.is12Key(c)) {
            Cclog("startDtmf called with invalid character '" + c + "'");
        } else {
            mCi.startDtmf(c, null);
        }
    }

    @Override
    public void
    stopDtmf() {
        mCi.stopDtmf(null);
    }

    public void
    sendBurstDtmf(String dtmfString) {
        Cclog("sendBurstDtmf() is a CDMA method");
    }
    //MTK-END [mtk04070][111118][ALPS00093395]Replace Log with Cclog

    @Override
    public void
    setRadioPower(boolean power) {
        mSST.setRadioPower(power);
        if (!FeatureOption.MTK_GEMINI_SUPPORT && !power && mSST.mSS != null) {
            LOGD("Current service state [" + mSST.mSS + "]");
            if (mSST.mSS.getState() == ServiceState.STATE_POWER_OFF) {
                LOGD("Already in power off state, force notify");
                forceNotifyServiceStateChange();
            }
        }
    }

    private void storeVoiceMailNumber(String number) {
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(getContext());
        SharedPreferences.Editor editor = sp.edit();
        //MTK-START [mtk04070][111118][ALPS00093395]Add mySimId
        editor.putString(VM_NUMBER + mySimId, number);
        //MTK-END [mtk04070][111118][ALPS00093395]Add mySimId
        editor.apply();
        setVmSimImsi(getSubscriberId());
    }

    @Override
    public String getVoiceMailNumber() {
        // Read from the SIM. If its null, try reading from the shared preference area.
        IccRecords r = mIccRecords.get();
        String number = (r != null) ? r.getVoiceMailNumber() : "";
        if (TextUtils.isEmpty(number)) {
            SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(getContext());
            //MTK-START [mtk04070][111118][ALPS00093395]Add mySimId
            number = sp.getString(VM_NUMBER + mySimId, null);
            //MTK-END [mtk04070][111118][ALPS00093395]Add mySimId
        }
        return number;
    }

    private String getVmSimImsi() {
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(getContext());
        //MTK-START [mtk04070][111118][ALPS00093395]Add mySimId
        return sp.getString(VM_SIM_IMSI + mySimId, null);
        //MTK-END [mtk04070][111118][ALPS00093395]Add mySimId
    }

    private void setVmSimImsi(String imsi) {
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(getContext());
        SharedPreferences.Editor editor = sp.edit();
        //MTK-START [mtk04070][111118][ALPS00093395]Add mySimId
        editor.putString(VM_SIM_IMSI + mySimId, imsi);
        //MTK-END [mtk04070][111118][ALPS00093395]Add mySimId
        editor.apply();
    }

    @Override
    public String getVoiceMailAlphaTag() {
        String ret;
        IccRecords r = mIccRecords.get();

        ret = (r != null) ? r.getVoiceMailAlphaTag() : "";

        if (ret == null || ret.length() == 0) {
            return mContext.getText(
                com.android.internal.R.string.defaultVoiceMailAlphaTag).toString();
        }

        return ret;
    }

    @Override
    public String getDeviceId() {
        return mImei;
    }

    @Override
    public String getDeviceSvn() {
        return mImeiSv;
    }

    @Override
    public String getImei() {
        return mImei;
    }

    @Override
    public String getEsn() {
        LOGE("[GSMPhone] getEsn() is a CDMA method");
        return "0";
    }

    public String getMeid() {
        LOGE("[GSMPhone] getMeid() is a CDMA method");
        return "0";
    }

    @Override
    public String getSubscriberId() {
        IccRecords r = mIccRecords.get();
        return (r != null) ? r.getIMSI() : null;
    }

    @Override
    public String getGroupIdLevel1() {
        IccRecords r = mIccRecords.get();
        return (r != null) ? r.getGid1() : null;
    }

    @Override
    public String getLine1Number() {
        IccRecords r = mIccRecords.get();
        return (r != null) ? r.getMsisdnNumber() : null;
    }

    @Override
    public String getMsisdn() {
        IccRecords r = mIccRecords.get();
        return (r != null) ? r.getMsisdnNumber() : null;
    }

    @Override
    public String getLine1AlphaTag() {
        IccRecords r = mIccRecords.get();
        return (r != null) ? r.getMsisdnAlphaTag() : null;
    }

    @Override
    public void setLine1Number(String alphaTag, String number, Message onComplete) {
        IccRecords r = mIccRecords.get();
        if (r != null) {
            r.setMsisdnNumber(alphaTag, number, onComplete);
        } else {
            if (onComplete != null) {
                AsyncResult.forMessage(onComplete).exception 
                        = new RuntimeException("ICC Record is null.");
                onComplete.sendToTarget();
            }
        }
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

    private boolean isValidCommandInterfaceCFReason (int commandInterfaceCFReason) {
        switch (commandInterfaceCFReason) {
        case CF_REASON_UNCONDITIONAL:
        case CF_REASON_BUSY:
        case CF_REASON_NO_REPLY:
        case CF_REASON_NOT_REACHABLE:
        case CF_REASON_ALL:
        case CF_REASON_ALL_CONDITIONAL:
            return true;
        default:
            return false;
        }
    }

    private boolean isValidCommandInterfaceCFAction (int commandInterfaceCFAction) {
        switch (commandInterfaceCFAction) {
        case CF_ACTION_DISABLE:
        case CF_ACTION_ENABLE:
        case CF_ACTION_REGISTRATION:
        case CF_ACTION_ERASURE:
            return true;
        default:
            return false;
        }
    }

    protected  boolean isCfEnable(int action) {
        return (action == CF_ACTION_ENABLE) || (action == CF_ACTION_REGISTRATION);
    }

    @Override
    public void getCallForwardingOption(int commandInterfaceCFReason, Message onComplete) {
        if (isValidCommandInterfaceCFReason(commandInterfaceCFReason)) {
            if (LOCAL_DEBUG) LOGD("requesting call forwarding query.");
            Message resp;
            if (commandInterfaceCFReason == CF_REASON_UNCONDITIONAL) {
                resp = obtainMessage(EVENT_GET_CALL_FORWARD_DONE, onComplete);
            } else {
                resp = onComplete;
            }
            mCi.queryCallForwardStatus(commandInterfaceCFReason,0,null,resp);
        }
    }

    @Override
    public void setCallForwardingOption(int commandInterfaceCFAction,
            int commandInterfaceCFReason,
            String dialingNumber,
            int timerSeconds,
            Message onComplete) {
        if (    (isValidCommandInterfaceCFAction(commandInterfaceCFAction)) &&
                (isValidCommandInterfaceCFReason(commandInterfaceCFReason))) {

            Message resp;
            if (commandInterfaceCFReason == CF_REASON_UNCONDITIONAL) {
                Cfu cfu = new Cfu(dialingNumber, onComplete);
                resp = obtainMessage(EVENT_SET_CALL_FORWARD_DONE,
                        isCfEnable(commandInterfaceCFAction) ? 1 : 0, 0, cfu);
            } else {
                resp = onComplete;
            }
            mCi.setCallForward(commandInterfaceCFAction,
                    commandInterfaceCFReason,
                    CommandsInterface.SERVICE_CLASS_VOICE,
                    dialingNumber,
                    timerSeconds,
                    resp);
        }
    }

    @Override
    public void getOutgoingCallerIdDisplay(Message onComplete) {
        mCi.getCLIR(onComplete);
    }

    @Override
    public void setOutgoingCallerIdDisplay(int commandInterfaceCLIRMode,
                                           Message onComplete) {
        mCi.setCLIR(commandInterfaceCLIRMode,
                obtainMessage(EVENT_SET_CLIR_COMPLETE, commandInterfaceCLIRMode, 0, onComplete));
    }

    @Override
    public void getCallWaiting(Message onComplete) {
        //As per 3GPP TS 24.083, section 1.6 UE doesn't need to send service
        //class parameter in call waiting interrogation  to network
        mCi.queryCallWaiting(CommandsInterface.SERVICE_CLASS_VOICE, onComplete);
    }

    @Override
    public void setCallWaiting(boolean enable, Message onComplete) {
        mCi.setCallWaiting(enable, CommandsInterface.SERVICE_CLASS_VOICE, onComplete);
    }

    @Override
    public void
    getAvailableNetworks(Message response) {
        LOGI("before query available network, cleanup all data connections");
        mDcTracker.cleanUpAllConnections(null);
        Message msg = obtainMessage(EVENT_QUERY_AVAILABLE_NETWORK);
        msg.obj = response;
        sendMessage(msg);
    }

    @Override
    public void
    cancelAvailableNetworks(Message response) {
        Message msg = obtainMessage(EVENT_ABORT_QUERY_AVAILABLE_NETWORK);
        msg.obj = response;
        sendMessage(msg);
    }    

    /**
     * Small container class used to hold information relevant to
     * the carrier selection process. operatorNumeric can be ""
     * if we are looking for automatic selection. operatorAlphaLong is the
     * corresponding operator name.
     */
    private static class NetworkSelectMessage {
        public Message message;
        public String operatorNumeric;
        public String operatorAlphaLong;

        //MTK-START [mtk04070][111118][ALPS00093395]MTK added 
	protected NetworkSelectMessage() {
        //MTK-END [mtk04070][111118][ALPS00093395]MTK added 
	}
    }

    @Override
    public void
    setNetworkSelectionModeAutomatic(Message response) {
        // wrap the response message in our own message along with
        // an empty string (to indicate automatic selection) for the
        // operator's id.
        NetworkSelectMessage nsm = new NetworkSelectMessage();
        nsm.message = response;
        nsm.operatorNumeric = "";
        nsm.operatorAlphaLong = "";

        // get the message
        Message msg = obtainMessage(EVENT_SET_NETWORK_AUTOMATIC_COMPLETE, nsm);
        if (LOCAL_DEBUG)
            LOGD("wrapping and sending message to connect automatically");

        mCi.setNetworkSelectionModeAutomatic(msg);
    }

    @Override
    public void
    selectNetworkManually(OperatorInfo network,
            Message response) {
        // wrap the response message in our own message along with
        // the operator's id.
        NetworkSelectMessage nsm = new NetworkSelectMessage();
        nsm.message = response;
        nsm.operatorNumeric = network.getOperatorNumeric();
        nsm.operatorAlphaLong = network.getOperatorAlphaLong();

        // get the message
        Message msg = obtainMessage(EVENT_SET_NETWORK_MANUAL_COMPLETE, nsm);

        //MTK-START [mtk04070][111118][ALPS00093395]MTK modified 
        String property_name = "gsm.baseband.capability";
        if(mySimId > PhoneConstants.GEMINI_SIM_1){
            property_name = property_name + (mySimId+1) ;
        }		
		
        int basebandCapability = SystemProperties.getInt("property_name", 3); /* ALPS00352231 */			
        LOGD("property_name="+property_name+",basebandCapability=" + basebandCapability);				
        if( 3 > basebandCapability){
            mCi.setNetworkSelectionModeManual(network.getOperatorNumeric(), msg);
        }else{
            String actype = ACT_TYPE_GSM;
            if(network.getOperatorAlphaLong() != null && network.getOperatorAlphaLong().endsWith(UTRAN_INDICATOR)) {
               actype = ACT_TYPE_UTRAN;
            //For LTE support -- start
            } else if (network.getOperatorAlphaLong() != null && network.getOperatorAlphaLong().endsWith(LTE_INDICATOR)){
               actype = ACT_TYPE_LTE;
            }
            //For LTE support -- end            
            mCi.setNetworkSelectionModeManualWithAct(network.getOperatorNumeric(),actype, msg);
        }  
        //MTK-END [mtk04070][111118][ALPS00093395]MTK modified 
    }
    @Override
    public void
    getNeighboringCids(Message response) {
        mCi.getNeighboringCids(response);
    }

    @Override
    public void setOnPostDialCharacter(Handler h, int what, Object obj) {
        mPostDialHandler = new Registrant(h, what, obj);
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
    public void getDataCallList(Message response) {
        mCi.getDataCallList(response);
    }

    @Override
    public void updateServiceLocation() {
        mSST.enableSingleLocationUpdate();
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
    public boolean getDataRoamingEnabled() {
        return mDcTracker.getDataOnRoamingEnabled();
    }

    @Override
    public void setDataRoamingEnabled(boolean enable) {
        mDcTracker.setDataOnRoamingEnabled(enable);
    }

    /**
     * Removes the given MMI from the pending list and notifies
     * registrants that it is complete.
     * @param mmi MMI that is done
     */
    /*package*/ void
    onMMIDone(GsmMmiCode mmi) {
        /* Only notify complete if it's on the pending list.
         * Otherwise, it's already been handled (eg, previously canceled).
         * The exception is cancellation of an incoming USSD-REQUEST, which is
         * not on the list.
         */
        if (mPendingMMIs.remove(mmi) || mmi.isUssdRequest()) {
            mMmiCompleteRegistrants.notifyRegistrants(
                new AsyncResult(null, mmi, null));
        }
    }


    private void
    onNetworkInitiatedUssd(GsmMmiCode mmi) {
        mMmiCompleteRegistrants.notifyRegistrants(
            new AsyncResult(null, mmi, null));
    }


    /** ussdMode is one of CommandsInterface.USSD_MODE_* */
    private void
    onIncomingUSSD (int ussdMode, String ussdMessage) {
        boolean isUssdError;
        boolean isUssdRequest;

        isUssdRequest
            = (ussdMode == CommandsInterface.USSD_MODE_REQUEST);

        //MTK-START [mtk04070][111118][ALPS00093395]MTK modified
        isUssdError
            = ((ussdMode == CommandsInterface.USSD_OPERATION_NOT_SUPPORTED)
               ||(ussdMode == CommandsInterface.USSD_NETWORK_TIMEOUT));
        //MTK-END [mtk04070][111118][ALPS00093395]MTK modified

        // See comments in GsmMmiCode.java
        // USSD requests aren't finished until one
        // of these two events happen
        GsmMmiCode found = null;
        for (int i = 0, s = mPendingMMIs.size() ; i < s; i++) {
            if(mPendingMMIs.get(i).isPendingUSSD()) {
                found = mPendingMMIs.get(i);
                break;
            }
        }

        if (found != null) {
            // Complete pending USSD

            if (isUssdError) {
                found.onUssdFinishedError();
            } else {
                found.onUssdFinished(ussdMessage, isUssdRequest);
            }
        } else { // pending USSD not found
            // The network may initiate its own USSD request

            // ignore everything that isnt a Notify or a Request
            // also, discard if there is no message to present
            if (!isUssdError && ussdMessage != null) {
                GsmMmiCode mmi;
                mmi = GsmMmiCode.newNetworkInitiatedUssd(ussdMessage,
                                                   isUssdRequest,
                                                   GSMPhone.this,
                                                   mUiccApplication.get());
                onNetworkInitiatedUssd(mmi);
            //MTK-START [mtk04070][111118][ALPS00093395]MTK added
            } else if (isUssdError) {
                GsmMmiCode mmi;
                mmi = GsmMmiCode.newNetworkInitiatedUssdError(ussdMessage,
                                                   isUssdRequest,
                                                   GSMPhone.this,
                                                   mUiccApplication.get());
                onNetworkInitiatedUssd(mmi);
            //MTK-END [mtk04070][111118][ALPS00093395]MTK added
            }
        }
    }

    /**
     * Make sure the network knows our preferred setting.
     */
    protected  void syncClirSetting() {
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(getContext());
        /// M: Add key for SIM2 CLIR setting.
        String keyName = (getMySimId()==PhoneConstants.GEMINI_SIM_1) ? CLIR_KEY : CLIR_KEY_2;
        int clirSetting = sp.getInt(CLIR_KEY, -1);
        if (clirSetting >= 0) {
            mCi.setCLIR(clirSetting, null);
        }
    }

    @Override
    public void handleMessage (Message msg) {
        AsyncResult ar;
        Message onComplete;

        if (!mIsTheCurrentActivePhone) {
            Rlog.e(LOG_TAG, "Received message " + msg +
                    "[" + msg.what + "] while being destroyed. Ignoring.");
            return;
        }
        switch (msg.what) {
            case EVENT_RADIO_AVAILABLE: {
                //MTK-START [mtk04070][111118][ALPS00093395]MTK modified
                LOGW("handleMessage(): received EVENT_RADIO_AVAILABLE");
                mCi.getBasebandVersion(
                        obtainMessage(EVENT_GET_BASEBAND_VERSION_DONE));

                mCi.getIMEI(obtainMessage(EVENT_GET_IMEI_DONE));
                mCi.getIMEISV(obtainMessage(EVENT_GET_IMEISV_DONE));
                //Add by mtk80372 for Barcode Number
                mCi.getMobileRevisionAndImei(5,obtainMessage(EVENT_GET_BARCODE_NUMBER));
                LOGW(" call mCi.getBarcodeNum");
                updateSimIndicateState();

                /* ALPS00324111: reset ever IVSR flag */
                if(mSST != null)				
                    mSST.setEverIVSR(false);

                /* 3G switch start */
                if (FeatureOption.MTK_GEMINI_3G_SWITCH) {
                    LOGD("radio available, to get 3G capability");
                    mCi.get3GCapabilitySIM(obtainMessage(EVENT_GET_3G_CAPABILITY_WHEN_RADIO_AVAILABLE));
                } else {
                    mIsRadioAvailable = true;
                }
                /* 3G switch end */
                //MTK-END [mtk04070][111118][ALPS00093395]MTK modified

                if (FeatureOption.MTK_ACMT_DEBUG) {
                    this.invokeOemRilRequestStrings(new String[]{"AT+EACMT=1",""}, null);
                } else {
                    this.invokeOemRilRequestStrings(new String[]{"AT+EACMT=0",""}, null);
                }

                /* ALPS00300484 */			
                if (!FeatureOption.MTK_GEMINI_SUPPORT) {  
                    LOGD("set GPRS always connection type for single SIM project");					
                    setPsConnType(2); 
                }

                // [mtk02772] start
                // [mtk02772] move it to uiccController for JB MR1
                // ALPS00294581 Replace "RIL_UNSOL_SIM_MISSING in RIL.java" with "acively query SIM missing status"
                /*if(!FeatureOption.MTK_GEMINI_SUPPORT) {
                    Rlog.d(LOG_TAG, "query SIM Missing status for single card");
                    getSimMissingStatus();
                }*/
                // [mtk02772] end
                
                //MTK-START [mtk80950][120410][ALPS00266631] check whether download calibration data or not
                //because modem only support sim1

                LOGD("m3GCapabilitySIM = " + m3GCapabilitySIM);
                if (!FeatureOption.MTK_GEMINI_3G_SWITCH && mySimId == PhoneConstants.GEMINI_SIM_1) {
                    mCi.getCalibrationData(obtainMessage(EVENT_GET_CALIBRATION_DATA_DONE));
                }
                //MTK-END [mtk80950][120410][ALPS00266631] check whether download calibration data or not

                int airplaneMode = Settings.Global.getInt(mContext.getContentResolver(), Settings.Global.AIRPLANE_MODE_ON, 0);
                boolean isPowerOff = false;
                if (PhoneFactory.isDualTalkMode()) {
                    if (FeatureOption.MTK_FLIGHT_MODE_POWER_OFF_MD && airplaneMode == 1) {
                        LOGD("Turn off md since airplane mode (md" + mySimId + ")");
                        setRadioPower(false, true);
                        isPowerOff = true;
                    } else if (FeatureOption.MTK_RADIOOFF_POWER_OFF_MD) {
                        int dualSimModeSetting = Settings.System.getInt(mContext.getContentResolver(),
                            Settings.System.DUAL_SIM_MODE_SETTING, PhoneConstants.GEMINI_SIM_1 | PhoneConstants.GEMINI_SIM_2);
                        if ((dualSimModeSetting & (mySimId+1)) == 0) {
                            LOGD("Turn off md " + mySimId);
                            setRadioPower(false, true);
                            isPowerOff = true;
                        }
                    }
                } else {
                    //Property value shall be 1~4,  convert to PhoneConstants.GEMINI_SIM_x
                    int current3GSimId = SystemProperties.getInt("gsm.3gswitch", 1) - 1;
                    LOGD("Turn off md since airplane mode, 3GSimId:" + current3GSimId + " mySimId:" + mySimId);			

                    // EPOF needs to send to protocol stack 1
                    if (getMySimId() == current3GSimId) {
                        if (FeatureOption.MTK_FLIGHT_MODE_POWER_OFF_MD && airplaneMode == 1) {
                            LOGD("Turn off md since airplane mode");
                            setRadioPower(false, true);
                            isPowerOff = true;
                        }
                    }
                }

                if (isPowerOff && !FeatureOption.MTK_GEMINI_SUPPORT) {
                    ServiceState ss = getServiceState();
                    if (ss != null && ss.getState() == ServiceState.STATE_POWER_OFF) {
                        LOGD("Already power-off and force notify service state");
                        forceNotifyServiceStateChange();
                    }
                }
                break;
            }

            case EVENT_RADIO_ON:
                break;

            case EVENT_REGISTERED_TO_NETWORK:
                syncClirSetting();

                if (needQueryCfu) {
                    String cfuSetting = SystemProperties.get(PhoneConstants.CFU_QUERY_TYPE_PROP, PhoneConstants.CFU_QUERY_TYPE_DEF_VALUE);
                    String isTestSim = "0";
                    /// M: Add for CMCC RRM test. @{
                    boolean isRRMEnv = false;
                    String operatorNumeric = null;
                    /// @}
                    if(getMySimId() == PhoneConstants.GEMINI_SIM_1) {
                        isTestSim = SystemProperties.get("gsm.sim.ril.testsim", "0");
                    }
                    else if(getMySimId() == PhoneConstants.GEMINI_SIM_2) {
                        isTestSim = SystemProperties.get("gsm.sim.ril.testsim.2", "0");
                    }

                    /// M: Add for CMCC RRM test. @{
                    // RRM test use 46602 as PLMN, which will not appear in the actual network
                    // Note that this should be modified when the PLMN for RRM test is changed
                    operatorNumeric = getServiceState().getOperatorNumeric();
                    if (operatorNumeric != null && operatorNumeric.equals("46602")) {
                        isRRMEnv = true;
                    }
                    /// @}
                    LOGD("[GSMPhone] CFU_KEY = " + cfuSetting + " isTestSIM : " + isTestSim + " isRRMEnv : " + isRRMEnv);

                    if(isTestSim.equals("0") && isRRMEnv == false) { /// M: Add for CMCC RRM test.
                        String isChangedProp = CFU_QUERY_SIM_CHANGED_PROP + getMySimId();
                        String isChanged = SystemProperties.get(isChangedProp, "0");

                        LOGD("[GSMPhone] isChanged " + isChanged);
                        // 0 : default
                        // 1 : OFF
                        // 2 : ON
                        if (cfuSetting.equals("2") 
                            || (cfuSetting.equals("0") && isChanged.equals("1"))) {
                            /* For solving ALPS01023811 */
                            mCfuQueryRetryCount = 0;
                            queryCfuOrWait();
                            needQueryCfu = false;
                            SystemProperties.set(isChangedProp, "0");
                        }
                    }
                }
                break;

            case EVENT_SIM_RECORDS_LOADED:
                // The "current" may no longer be valid.  MMS depends on this to send properly. TBD
                // M: To prevent DB access in main thread.
                new Thread(new Runnable() {
                    public void run() {
                        updateCurrentCarrierInProvider();
                    }
                }).start();
                
                // Check if this is a different SIM than the previous one. If so unset the
                // voice mail number.
                String imsi = getVmSimImsi();
                String imsiFromSIM = getSubscriberId();
                if (imsi != null && imsiFromSIM != null && !imsiFromSIM.equals(imsi)) {
                    storeVoiceMailNumber(null);
                    setVmSimImsi(null);
                }

                break;

            case EVENT_GET_BASEBAND_VERSION_DONE:
                ar = (AsyncResult)msg.obj;

                if (ar.exception != null) {
                    break;
                }

                if (LOCAL_DEBUG) LOGD("SIM: " + mySimId + " Baseband version: " + ar.result);
                if (FeatureOption.MTK_DT_SUPPORT && mySimId == PhoneConstants.GEMINI_SIM_2){
                    setSystemProperty(PROPERTY_BASEBAND_VERSION_2, (String)ar.result);					
                }else{
                setSystemProperty(PROPERTY_BASEBAND_VERSION, (String)ar.result);
                }
                break;

            case EVENT_GET_IMEI_DONE:
                ar = (AsyncResult)msg.obj;

                //MTK-START [mtk04070][111118][ALPS00093395]MTK modified
                if (ar.exception != null) {
                    LOGW("Null IMEI!!");
                    setDeviceIdAbnormal(1);
                    break;
                }

                mImei = (String)ar.result;
                LOGD("IMEI: " + mImei);

                try {
                    Long.parseLong(mImei);
                    setDeviceIdAbnormal(0);
                } catch (NumberFormatException e) {
                    setDeviceIdAbnormal(1);
                    LOGW("Invalid format IMEI!!");
                }
                //MTK-END [mtk04070][111118][ALPS00093395]MTK modified
                break;

            case EVENT_GET_IMEISV_DONE:
                ar = (AsyncResult)msg.obj;

                if (ar.exception != null) {
                    break;
                }

                mImeiSv = (String)ar.result;
                break;

            //MTK-START [mtk04070][111118][ALPS00093395]MTK added
            //Add by mtk80372 for Barcode Number
            case EVENT_GET_BARCODE_NUMBER:
                LOGW("enter EVENT_GET_BARCODE_NUMBER");
                ar = (AsyncResult)msg.obj;

                if (ar.exception != null) {
                    break;
                }

                LOGW("Barcode number is : " + (String)ar.result);
                mSN = (String)(ar.result);
                break;
            //MTK-END [mtk04070][111118][ALPS00093395]MTK added

            case EVENT_USSD:
                ar = (AsyncResult)msg.obj;

                String[] ussdResult = (String[]) ar.result;

                if (ussdResult.length > 1) {
                    try {
                        onIncomingUSSD(Integer.parseInt(ussdResult[0]), ussdResult[1]);
                    } catch (NumberFormatException e) {
                        LOGW("error parsing USSD");
                    }
                }
                break;
            case EVENT_RADIO_NOT_AVAILABLE:
                mIsRadioAvailable = false;
                break;

            case EVENT_RADIO_OFF_OR_NOT_AVAILABLE:
                // Some MMI requests (eg USSD) are not completed
                // within the course of a CommandsInterface request
                // If the radio shuts off or resets while one of these
                // is pending, we need to clean up.
                //MTK-START [mtk04070][111118][ALPS00093395]MTK modified
                if (!mCi.getRadioState().isAvailable())
                    mIsRadioAvailable = false;
                for (int i = mPendingMMIs.size() - 1; i >= 0; i--) {
                    if (mPendingMMIs.get(i).isPendingUSSD()) {
                        mPendingMMIs.get(i).onUssdFinishedError();
                    }
                }
                updateSimIndicateState();
                //MTK-END [mtk04070][111118][ALPS00093395]MTK modified
                mRadioOffOrNotAvailableRegistrants.notifyRegistrants();
                break;

            case EVENT_VOICE_CALL_INCOMING_INDICATION:
                LOGW("handle EVENT_VOICE_CALL_INCOMING_INDICATION");
                mVoiceCallIncomingIndicationRegistrants.notifyRegistrants(new AsyncResult(null, this, null));
                break;
                
            case EVENT_SSN:
                ar = (AsyncResult)msg.obj;
                SuppServiceNotification not = (SuppServiceNotification) ar.result;
                mSsnRegistrants.notifyRegistrants(ar);
                break;

            case EVENT_SET_CALL_FORWARD_DONE:
                ar = (AsyncResult)msg.obj;
                IccRecords r = mIccRecords.get();
                Cfu cfu = (Cfu) ar.userObj;
                if (ar.exception == null && r != null) {
                    r.setVoiceCallForwardingFlag(1, msg.arg1 == 1, cfu.mSetCfNumber);
                }
                if (cfu.mOnComplete != null) {
                    AsyncResult.forMessage(cfu.mOnComplete, ar.result, ar.exception);
                    cfu.mOnComplete.sendToTarget();
                }
                break;

            case EVENT_SET_VM_NUMBER_DONE:
                ar = (AsyncResult)msg.obj;
                if (IccVmNotSupportedException.class.isInstance(ar.exception)) {
                    storeVoiceMailNumber(mVmNumber);
                    ar.exception = null;
                }
                onComplete = (Message) ar.userObj;
                if (onComplete != null) {
                    AsyncResult.forMessage(onComplete, ar.result, ar.exception);
                    onComplete.sendToTarget();
                }
                break;


            case EVENT_GET_CALL_FORWARD_DONE:
                /* For solving ALPS00997715 */
                setSystemProperty(CFU_QUERY_PROPERTY_NAME + mySimId, "0");
                ar = (AsyncResult)msg.obj;
                LOGD("[EVENT_GET_CALL_FORWARD_DONE]ar.exception = " + ar.exception);
                if (ar.exception == null) {
                    handleCfuQueryResult((CallForwardInfo[])ar.result);
                }
                onComplete = (Message) ar.userObj;
                if (onComplete != null) {
                    AsyncResult.forMessage(onComplete, ar.result, ar.exception);
                    onComplete.sendToTarget();
                }
                break;

            case EVENT_SET_NETWORK_AUTOMATIC:
                ar = (AsyncResult)msg.obj;
                setNetworkSelectionModeAutomatic((Message)ar.result);
                break;

            case EVENT_ICC_RECORD_EVENTS:
                ar = (AsyncResult)msg.obj;
                processIccRecordEvents((Integer)ar.result);
                break;

            // handle the select network completion callbacks.
            case EVENT_SET_NETWORK_MANUAL_COMPLETE:
            case EVENT_SET_NETWORK_AUTOMATIC_COMPLETE:
                handleSetSelectNetwork((AsyncResult) msg.obj);
                break;

            case EVENT_SET_CLIR_COMPLETE:
                ar = (AsyncResult)msg.obj;
                if (ar.exception == null) {
                    saveClirSetting(msg.arg1);
                }
                onComplete = (Message) ar.userObj;
                if (onComplete != null) {
                    AsyncResult.forMessage(onComplete, ar.result, ar.exception);
                    onComplete.sendToTarget();
                }
                break;

             //MTK-START [mtk04070][111118][ALPS00093395]MTK added
             case EVENT_CFU_IND:
                /* Line1 is enabled or disabled while reveiving this EVENT */
                if (mIccRecords.get() != null) {
                   /* Line1 is enabled or disabled while reveiving this EVENT */
                   ar = (AsyncResult)msg.obj;
                   int[] cfuResult = (int[]) ar.result;
                   mIccRecords.get().setVoiceCallForwardingFlag(1, (cfuResult[0] == 1), null);
                }
                break;
             case EVENT_CRSS_IND: 
                ar = (AsyncResult)msg.obj;
                SuppCrssNotification noti = (SuppCrssNotification) ar.result;

                if(noti.code == SuppCrssNotification.CRSS_CALLING_LINE_ID_PREST) {
                    // update numberPresentation in gsmconnection
                    if(getRingingCall().getState() != GsmCall.State.IDLE) {
                        GsmConnection cn = (GsmConnection)(getRingingCall().getConnections().get(0));
                        /* CLI validity value, 
                          0: PRESENTATION_ALLOWED, 
                          1: PRESENTATION_RESTRICTED, 
                          2: PRESENTATION_UNKNOWN
                        */  

                        LOGD("set number presentation to connection : " + noti.cli_validity);
                        switch (noti.cli_validity) {
                            case 1:
                                cn.mNumberPresentation = PhoneConstants.PRESENTATION_RESTRICTED;
                                break;

                            case 2:
                                cn.mNumberPresentation = PhoneConstants.PRESENTATION_UNKNOWN;
                                break;

                            case 0:
                            default:
                                cn.mNumberPresentation = PhoneConstants.PRESENTATION_ALLOWED;
                                break;
                        }
                    }
                }
                mCallRelatedSuppSvcRegistrants.notifyRegistrants(ar);

                break;
/* 3G switch start */
            case EVENT_GET_3G_CAPABILITY_WHEN_RADIO_AVAILABLE:
                mIsRadioAvailable = true;
                LOGI("Radio available query 3G capability done, set radio available");
                //here we not to break and continue handling EVENT_GET_3G_CAPABILITY 
            case EVENT_GET_3G_CAPABILITY: {
                ar = (AsyncResult)msg.obj;
                int[] result = (int[]) ar.result;
                if (result != null) {
                    m3GCapabilitySIM = result[0];
                    if (mIsToResetRadio)
                        mCi.resetRadio(null); //reset modem when 3G capability is changed
                } else {
                    m3GCapabilitySIM = -1;
                }

                /* Gemini+ , after RIL processed, 1: SIM1 , 2: SIM2 , 3:SIM3 ,4: SIM4.  */
                if (m3GCapabilitySIM == 1)
                    m3GCapabilitySIM = PhoneConstants.GEMINI_SIM_1;
                else if (m3GCapabilitySIM == 2)
                    m3GCapabilitySIM = PhoneConstants.GEMINI_SIM_2;
                else if (m3GCapabilitySIM == 3)
                    m3GCapabilitySIM = PhoneConstants.GEMINI_SIM_3;
                else if (m3GCapabilitySIM == 4)
                    m3GCapabilitySIM = PhoneConstants.GEMINI_SIM_4;

                mIsToResetRadio = false;

////                if (((m3GCapabilitySIM == PhoneConstants.GEMINI_SIM_1) && (mySimId == PhoneConstants.GEMINI_SIM_1)) || 
////                   ((m3GCapabilitySIM == PhoneConstants.GEMINI_SIM_2) && (mySimId == PhoneConstants.GEMINI_SIM_2))) {
                if(m3GCapabilitySIM == mySimId){
                    mCi.getCalibrationData(obtainMessage(EVENT_GET_CALIBRATION_DATA_DONE));
                }
                
                if (NT_MODE_GSM_ONLY == mTargetNetworkMode) {
                    m3GCapabilitySIM = -1;
                    LOGI("No 3G but modem 3G capability SIM [" + m3GCapabilitySIM + ", " + mTargetNetworkMode + "]");
                } else {
                    LOGI("3G capability SIM [" + m3GCapabilitySIM + ", " + mTargetNetworkMode + "]");
                }

                Settings.Global.putInt(getContext().getContentResolver(),
                    Settings.Global.PREFERRED_NETWORK_MODE, mTargetNetworkMode);
                break;
            }
            case EVENT_SET_3G_CAPABILITY:
                mIsToResetRadio = true;
                mCi.get3GCapabilitySIM(obtainMessage(EVENT_GET_3G_CAPABILITY));
                break;
/* 3G switch end */
             //MTK-END [mtk04070][111118][ALPS00093395]MTK added

// ALPS00294581 Replace "RIL_UNSOL_SIM_MISSING in RIL.java" with "acively query SIM missing status" for Single card
            case EVENT_QUERY_SIM_MISSING_STATUS:
                ar = (AsyncResult)msg.obj;
                if (ar.exception == null) {
                    int[] result = (int[]) ar.result;
                    if (result != null && result[0] == 0) {
                        LOGW("[EVENT_QUERY_SIM_MISSING_STATUS, execute notifySimMissing]");
                        mCi.notifySimMissing();
                        mSST.resetNotification(); //ALPS00297554
                    } else if(result != null && result[0] == 14) {
                        LOGW("[EVENT_QUERY_SIM_MISSING_STATUS, SIM busy and execute again]");
                        getSimMissingStatus();
                    } else {
                        if(result == null) {
                            LOGW("[EVENT_QUERY_SIM_MISSING_STATUS, card is null]");
                    } else { // result[0] == 1
                            LOGW("[EVENT_QUERY_SIM_MISSING_STATUS, card is present]");
                            //To do: It must be refactory.
                            //START
                            //if (mIccCard.get() != null)
                            //    mIccCard.get().disableSimMissingNotification();
                            //END
                        }
                    }
                } else {
                    LOGW("[EVENT_QUERY_SIM_MISSING_STATUS, exception]");
                }
                break;
// ALPS00294581 Replace "RIL_UNSOL_SIM_MISSING in RIL.java" with "acively query SIM missing status" for Single card

//MTK-START [mtk80950][120410][ALPS00266631] check whether download calibration data or not
            case EVENT_GET_CALIBRATION_DATA_DONE:
                ar = (AsyncResult)msg.obj;
                boolean mCalibrationData = false;

                if (ar.exception != null) {
                    LOGW("Null CALIBRATION DATA!!");
                    mCalibrationData = false;
                    break;
                }

                String mIsDownloaded = (String)ar.result;
                LOGD("mIsDownloaded: " + mIsDownloaded);

                if (mIsDownloaded.equalsIgnoreCase("+ECAL: 1")){
                    mCalibrationData = true;
                } else {
                    mCalibrationData = false;
                }

                updateIsDownloadCalibrationData(mCalibrationData);
                break;
//MTK-END [mtk80950][120410][ALPS00266631] check whether download calibration data or not
            case EVENT_QUERY_AVAILABLE_NETWORK:
                mCi.getAvailableNetworks((Message)msg.obj);
                break;
                
            case EVENT_ABORT_QUERY_AVAILABLE_NETWORK:
                mCi.cancelAvailableNetworks((Message)msg.obj);
                break;

 
            case EVENT_CIPHER_INDICATION: 
                ar = (AsyncResult)msg.obj; 

                int[] cipherStatus = (int[]) ar.result;

                mSimEnableCipher = cipherStatus[0];
                mCsCipherStatus = cipherStatus[1];
                mPsCipherStatus = cipherStatus[2];
                LOGD("cipher indication : "+mSimEnableCipher+" "+mCsCipherStatus+" "+mPsCipherStatus);

                updateCipherIndication();
                
                break;

            case EVENT_CFU_QUERY_TIMEOUT:
                LOGD("[EVENT_CFU_QUERY_TIMEOUT]mCfuQueryRetryCount = " + mCfuQueryRetryCount);
                if (++mCfuQueryRetryCount < CFU_QUERY_MAX_COUNT) {
                   queryCfuOrWait();
                }
                break;

           default:
                super.handleMessage(msg);
        }
    }

    @Override
    protected void onUpdateIccAvailability() {
        if (LOCAL_DEBUG) Rlog.d(LOG_TAG, "[GSMPhone] onUpdateIccAvailability");
        if (mUiccController == null ) {
            return;
        }

        UiccCardApplication newUiccApplication =
                mUiccController.getUiccCardApplication(UiccController.APP_FAM_3GPP);

        UiccCardApplication app = mUiccApplication.get();
        if (app != newUiccApplication) {
            if (app != null) {
                if (LOCAL_DEBUG) Rlog.d(LOG_TAG, "Removing stale icc objects.");
                if (mIccRecords.get() != null) {
                    unregisterForSimRecordEvents();
                    mSimPhoneBookIntManager.updateIccRecords(null);
                }
                mIccRecords.set(null);
                mUiccApplication.set(null);
            }
            if (newUiccApplication != null) {
                if (LOCAL_DEBUG) Rlog.d(LOG_TAG, "New Uicc application found");
                mUiccApplication.set(newUiccApplication);
                mIccRecords.set(newUiccApplication.getIccRecords());
                registerForSimRecordEvents();
                mSimPhoneBookIntManager.updateIccRecords(mIccRecords.get());
            }

            /* To solve [ALPS00455020]No CFU icon showed on status bar. */
            if (!mIsCfuRegistered) {
                mIsCfuRegistered = true;
                /* register for CFU info flag notification */
                mCi.registerForCallForwardingInfo(this, EVENT_CFU_IND, null);
            }
        }
    }

    private void processIccRecordEvents(int eventCode) {
        switch (eventCode) {
            case IccRecords.EVENT_CFI:
                notifyCallForwardingIndicator();
                break;
            case IccRecords.EVENT_MWI:
                notifyMessageWaitingIndicator();
                break;
        }
    }

   /**
     * Sets the "current" field in the telephony provider according to the SIM's operator
     *
     * @return true for success; false otherwise.
     */
    public boolean updateCurrentCarrierInProvider() {
        IccRecords r = mIccRecords.get();
        if (r != null) {
            try {
                //MTK-START [mtk04070][111118][ALPS00093395]MTK added
                Uri uri = null;
                if (FeatureOption.MTK_GEMINI_SUPPORT) {
                    switch (mySimId) {
                        case PhoneConstants.GEMINI_SIM_1:
                            uri = Uri.withAppendedPath(Telephony.Carriers.SIM1Carriers.CONTENT_URI, "current");
                            break;
                        case PhoneConstants.GEMINI_SIM_2:
                            uri = Uri.withAppendedPath(Telephony.Carriers.SIM2Carriers.CONTENT_URI, "current");
                            break;
                        case PhoneConstants.GEMINI_SIM_3:
                            uri = Uri.withAppendedPath(Telephony.Carriers.SIM3Carriers.CONTENT_URI, "current");
                            break;
                        case PhoneConstants.GEMINI_SIM_4:
                            uri = Uri.withAppendedPath(Telephony.Carriers.SIM4Carriers.CONTENT_URI, "current");
                            break;
                    }
                } else {
                    uri = Uri.withAppendedPath(Telephony.Carriers.CONTENT_URI, "current");
                }
                //MTK-END [mtk04070][111118][ALPS00093395]MTK added
                ContentValues map = new ContentValues();
                map.put(Telephony.Carriers.NUMERIC, r.getOperatorNumeric());
                mContext.getContentResolver().insert(uri, map);
                LOGD("updateCurrentCarrierInProvider(): mySimId=" + mySimId + " r.getOperatorNumeric()= " + r.getOperatorNumeric());
                return true;
            } catch (SQLException e) {
                LOGE("Can't store current operator");
                e.printStackTrace();
            }
        }
        //MTK-START [mtk04070][111118][ALPS00093395]MTK added
        else {
            LOGD("updateCurrentCarrierInProvider():mIccRecords is null");
        }
        //MTK-END [mtk04070][111118][ALPS00093395]MTK added
        return false;
    }

    /**
     * Used to track the settings upon completion of the network change.
     */
    private void handleSetSelectNetwork(AsyncResult ar) {
        // look for our wrapper within the asyncresult, skip the rest if it
        // is null.
        if (!(ar.userObj instanceof NetworkSelectMessage)) {
            if (LOCAL_DEBUG) LOGD("unexpected result from user object.");
            return;
        }

        NetworkSelectMessage nsm = (NetworkSelectMessage) ar.userObj;

        // found the object, now we send off the message we had originally
        // attached to the request.
        if (nsm.message != null) {
            if (LOCAL_DEBUG) LOGD("sending original message to recipient");
            AsyncResult.forMessage(nsm.message, ar.result, ar.exception);
            nsm.message.sendToTarget();
        }

        // open the shared preferences editor, and write the value.
        // nsm.operatorNumeric is "" if we're in automatic.selection.
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(getContext());
        SharedPreferences.Editor editor = sp.edit();

        //MTK-START [mtk04070][111118][ALPS00093395]Support Gemini
        if (mySimId == PhoneConstants.GEMINI_SIM_1) {
        editor.putString(NETWORK_SELECTION_KEY, nsm.operatorNumeric);
        editor.putString(NETWORK_SELECTION_NAME_KEY, nsm.operatorAlphaLong);
        } else if (mySimId == PhoneConstants.GEMINI_SIM_2){
            editor.putString(NETWORK_SELECTION_KEY_2, nsm.operatorNumeric);
            editor.putString(NETWORK_SELECTION_NAME_KEY_2, nsm.operatorAlphaLong);
        } else if (mySimId == PhoneConstants.GEMINI_SIM_3){
            editor.putString(NETWORK_SELECTION_KEY_3, nsm.operatorNumeric);
            editor.putString(NETWORK_SELECTION_NAME_KEY_3, nsm.operatorAlphaLong);
        } else if (mySimId == PhoneConstants.GEMINI_SIM_4){
            editor.putString(NETWORK_SELECTION_KEY_4, nsm.operatorNumeric);
            editor.putString(NETWORK_SELECTION_NAME_KEY_4, nsm.operatorAlphaLong);
        }
        //MTK-END [mtk04070][111118][ALPS00093395]Support Gemini

        // commit and log the result.
        if (! editor.commit()) {
            LOGE("failed to commit network selection preference");
        }

    }

    /**
     * Saves CLIR setting so that we can re-apply it as necessary
     * (in case the RIL resets it across reboots).
     */
    public void saveClirSetting(int commandInterfaceCLIRMode) {
        // open the shared preferences editor, and write the value.
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(getContext());
        /// M: Add key for SIM2 CLIR setting.
        String keyName = (getMySimId()==PhoneConstants.GEMINI_SIM_1) ? CLIR_KEY : CLIR_KEY_2;

        SharedPreferences.Editor editor = sp.edit();
        editor.putInt(keyName, commandInterfaceCLIRMode);

        // commit and log the result.
        if (! editor.commit()) {
            LOGE("failed to commit CLIR preference");
        }
    }

    private void handleCfuQueryResult(CallForwardInfo[] infos) {
        IccRecords r = mIccRecords.get();
        if (r != null) {
            if (infos == null || infos.length == 0) {
                // Assume the default is not active
                // Set unconditional CFF in SIM to false
                if (mIccRecords.get() != null) {
                    mIccRecords.get().setVoiceCallForwardingFlag(1, false, null);
                }
            } else {
                for (int i = 0, s = infos.length; i < s; i++) {
                    if ((infos[i].serviceClass & SERVICE_CLASS_VOICE) != 0) {
                        if (mIccRecords.get() != null) {
                            mIccRecords.get().setVoiceCallForwardingFlag(1, (infos[i].status == 1),infos[i].number);
                        }
                        // should only have the one
                        break;
                    }
                }
            }
        }
    }

    /**
     * Retrieves the PhoneSubInfo of the GSMPhone
     */
    @Override
    public PhoneSubInfo getPhoneSubInfo(){
        return mSubInfo;
    }

    /**
     * Retrieves the IccPhoneBookInterfaceManager of the GSMPhone
     */
    @Override
    public IccPhoneBookInterfaceManager getIccPhoneBookInterfaceManager(){
        return mSimPhoneBookIntManager;
    }

    /**
     * Activate or deactivate cell broadcast SMS.
     *
     * @param activate 0 = activate, 1 = deactivate
     * @param response Callback message is empty on completion
     */
    @Override
    public void activateCellBroadcastSms(int activate, Message response) {
        //M:MTK modified start
        mSMS.activateCellBroadcastSms(activate, response);
        LOGE("[GSMPhone] activateCellBroadcastSms() is obsolete; use SmsManager");
        // response.sendToTarget();
        //M:MTK modified end
    }

    /**
     * Query the current configuration of cdma cell broadcast SMS.
     *
     * @param response Callback message is empty on completion
     */
    @Override
    public void getCellBroadcastSmsConfig(Message response) {
        //M:MTK modified start
        //response.sendToTarget();
        mSMS.getCellBroadcastSmsConfig(response);
        LOGE("[GSMPhone] getCellBroadcastSmsConfig() is obsolete; use SmsManager");
        //M:MTK modified end
    }

    /**
     * Configure cdma cell broadcast SMS.
     *
     * @param response Callback message is empty on completion
     */
    @Override
    public void setCellBroadcastSmsConfig(int[] configValuesArray, Message response) {
        LOGE("[GSMPhone] setCellBroadcastSmsConfig() is obsolete; use SmsManager");
        //M:MTK modified start
        //response.sendToTarget();
        //M:MTK modified end
    }

    public boolean isCspPlmnEnabled() {
        IccRecords r = mIccRecords.get();
        return (r != null) ? r.isCspPlmnEnabled() : false;
    }

    private void registerForSimRecordEvents() {
        IccRecords r = mIccRecords.get();
        if (r == null) {
            return;
        }
        r.registerForNetworkSelectionModeAutomatic(
                this, EVENT_SET_NETWORK_AUTOMATIC, null);
        r.registerForRecordsEvents(this, EVENT_ICC_RECORD_EVENTS, null);
        r.registerForRecordsLoaded(this, EVENT_SIM_RECORDS_LOADED, null);
    }

    private void unregisterForSimRecordEvents() {
        IccRecords r = mIccRecords.get();
        if (r == null) {
            return;
        }
        r.unregisterForNetworkSelectionModeAutomatic(this);
        r.unregisterForRecordsEvents(this);
        r.unregisterForRecordsLoaded(this);
    }

    @Override
    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        pw.println("GSMPhone extends:");
        super.dump(fd, pw, args);
        pw.println(" mCT=" + mCT);
        pw.println(" mSST=" + mSST);
        pw.println(" mPendingMMIs=" + mPendingMMIs);
        pw.println(" mSimPhoneBookIntManager=" + mSimPhoneBookIntManager);
        pw.println(" mSubInfo=" + mSubInfo);
        if (VDBG) pw.println(" mImei=" + mImei);
        if (VDBG) pw.println(" mImeiSv=" + mImeiSv);
        pw.println(" mVmNumber=" + mVmNumber);
    }

    private void LOGE(String message) {
        Rlog.e(LOG_TAG, "(" + (mySimId+1) + ") :" + message);
    }
    
    private void LOGI(String message) {
        Rlog.i(LOG_TAG, "(" + (mySimId+1) + ") :" + message);
    }

    private void LOGD(String message) {
        Rlog.d(LOG_TAG, "(" + (mySimId+1) + ") :" + message);
    }

    private void LOGW(String message) {
        Rlog.w(LOG_TAG, "(" + (mySimId+1) + ") :" + message);
    }

    //MTK-START [mtk04070][111118][ALPS00093395]MTK proprietary methods
    /**
     * Request to get my SIM ID
     @internal     
     */    
    public int getMySimId() {
        return mySimId;
    }

    public void setPeerPhone(GSMPhone mPhone){
        mPeerPhone = mPhone;
    }
    
    public GSMPhone getPeerPhone(){
        return mPeerPhone;
    }
    
    public void setPeerPhones(GSMPhone mPhone, int phoneIdx){
        mPeerPhones[phoneIdx] = mPhone;
    }
    
    public GSMPhone getPeerPhones(int phoneIdx){
    /* GSMPhone 1 : mPeerPhones would be {null , GSMphone 2 , GSMphone 3 , null }
           GSMPhone 2 : mPeerPhones would be {GSMphone 1 , null, GSMphone 3, null}
           GSMPhone 3 : mPeerPhones would be {GSMphone 1 , GSMphone 2 , null , null}  */		
        return mPeerPhones[phoneIdx];
    }
    
    /* vt start */
    /* package */ void
    notifyVtReplaceDisconnect(Connection cn) {
        mVtReplaceDisconnectRegistrants.notifyResult(cn);
    }
    /* vt end */

    public void registerForCrssSuppServiceNotification(
            Handler h, int what, Object obj) {
        mCallRelatedSuppSvcRegistrants.addUnique(h, what, obj);
    }

    public void unregisterForCrssSuppServiceNotification(Handler h) {
        mCallRelatedSuppSvcRegistrants.remove(h);
    }

    public void
    hangupAll() throws CallStateException {
        mCT.hangupAll();
    }

    public void hangupAllEx() throws CallStateException {
        LOGD("hangupAllEx");
        mCT.hangupAllEx();
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

    public int
    getLastCallFailCause() {
        LOGD("[CC] causeCode = " + mCT.causeCode);
        return mCT.causeCode;
    }

    private boolean handleUdubIncallSupplementaryService(
            String dialString) {
        if (dialString.length() > 1) {
            return false;
        }

        if (getRingingCall().getState() != GsmCall.State.IDLE || 
             getBackgroundCall().getState() != GsmCall.State.IDLE) {
            if (LOCAL_DEBUG) Cclog("MmiCode 0: hangupWaitingOrBackground");
                mCT.hangupWaitingOrBackground();
        }

        return true;
    }

    /* vt start */
    public Connection
    vtDial(String dialString) throws CallStateException {
        return vtDial(dialString, null);
    }

    public Connection
    vtDial (String dialString, UUSInfo uusInfo) throws CallStateException {
        // Need to make sure dialString gets parsed properly
        String newDialString = PhoneNumberUtils.stripSeparators(dialString);
        Cclog("vtDial:" + dialString + "\n" + "newVtDial:" + newDialString);

        // Only look at the Network portion for mmi
        String networkPortion = PhoneNumberUtils.extractNetworkPortionAlt(newDialString);
        Cclog("network portion:" + networkPortion);
        GsmMmiCode mmi = GsmMmiCode.newFromDialString(networkPortion, this, mUiccApplication.get());
        if (LOCAL_DEBUG) Cclog("dialing w/ mmi '" + mmi + "'...");

        if (mmi == null) {
            return mCT.vtDial(newDialString, uusInfo);
        } else if (mmi.isTemporaryModeCLIR()) {
            return mCT.vtDial(mmi.mDialingNumber, mmi.getCLIRMode(), uusInfo);
        } else {
            //mPendingMMIs.add(mmi);
            //mMmiRegistrants.notifyRegistrants(new AsyncResult(null, mmi, null));
            //mmi.processCode();

            // FIXME should this return null or something else?
            return null;
        }
    }

    public void acceptVtCallWithVoiceOnly() throws CallStateException {
        mCT.acceptVtCallWithVoiceOnly();
    }
    /* vt end */


    public void setRadioPowerOn(){
    //     mCi.setRadioPowerOn(null);
        mSST.setRadioPowerOn();
    }

    public void
    setRadioPower(boolean power, boolean shutdown) {
        mSST.setRadioPower(power,shutdown);
        if (!FeatureOption.MTK_GEMINI_SUPPORT && shutdown && mSST.mSS != null) {
            LOGD("Current service state [" + mSST.mSS + "]");
            if (mSST.mSS.getState() == ServiceState.STATE_POWER_OFF) {
                LOGD("Already in power off state, force notify");
                forceNotifyServiceStateChange();
            }
        }
    }

    public void setAutoGprsAttach(int auto) {
        mSST.setAutoGprsAttach(auto);
    }

    public void setPsConnType(int type) {
        mSST.setPsConnType(type);
    }

    //Add by mtk80372 for Barcode Number
    public String getSN() {
        return mSN;
    }

    public int isDeviceIdAbnormal() {
        return mImeiAbnormal;
    }
    
    public void setDeviceIdAbnormal(int abnormal) {
        mImeiAbnormal = abnormal;
    }

    public void getPdpContextList(Message response) {
        getDataCallList(response);
    }

    /* Add by vendor for Multiple PDP Context */
    public String getActiveApnType() {
        // TODO Auto-generated method stub
        return mDcTracker.getActiveApnType();
    }

    /* Modified by venodr for Multiple PDP Context */
    /*public boolean isDataConnectivityPossible() {
        // TODO Auto-generated method stub
        return isDataConnectivityPossible(PhoneConstants.APN_TYPE_DEFAULT);
    }*/

    /**
     * @internal
     */
    public void setCellBroadcastSmsConfig(SmsBroadcastConfigInfo[] chIdList, 
            SmsBroadcastConfigInfo[] langList, Message response) {

        ArrayList<SmsBroadcastConfigInfo> chid_list = new ArrayList<SmsBroadcastConfigInfo>();
        for (int i=0;i<chIdList.length;i++) {
            chid_list.add(chIdList[i]);
        }

        ArrayList<SmsBroadcastConfigInfo> lang_list = new ArrayList<SmsBroadcastConfigInfo>();
        for (int i=0;i<chIdList.length;i++) {
            lang_list.add(langList[i]);
        }
        mSMS.setCellBroadcastConfig(chid_list, lang_list, response);
    }

    /**
     * @internal
     */
    public void queryCellBroadcastSmsActivation(Message response) {
        mSMS.queryCellBroadcastActivation(response);
    }


    /**
     * Get Call Barring State
     */
    public void getFacilityLock(String facility, String password, Message onComplete) {

        mCi.queryFacilityLock(facility, password, CommandsInterface.SERVICE_CLASS_VOICE,onComplete);

    }

    /**
     * Set Call Barring State
     */

    public void setFacilityLock(String facility, boolean enable, String password, Message onComplete) {

        mCi.setFacilityLock(facility, enable, password, CommandsInterface.SERVICE_CLASS_VOICE, onComplete);

    }

    /**
     * Change Call Barring Password
     */
    public void changeBarringPassword(String facility, String oldPwd, String newPwd, Message onComplete) {

        mCi.changeBarringPassword(facility, oldPwd, newPwd, onComplete);

    }
    
    /**
     * Change Call Barring Password with confirm
     */
    public void changeBarringPassword(String facility, String oldPwd, String newPwd, String newCfm, Message onComplete) {

        mCi.changeBarringPassword(facility, oldPwd, newPwd, newCfm, onComplete);

    }

    /**
     * Refresh Spn Display due to configuration change
     @internal
     */     
    public void refreshSpnDisplay() {
        mSST.refreshSpnDisplay();
    }

    public IccServiceStatus getIccServiceStatus(IccService enService) {
        if(mIccRecords.get() != null) {
            return ((SIMRecords)mIccRecords.get()).getSIMServiceStatus(enService);
        }
        return null;
    }

    /* vt start */
    public void getVtCallForwardingOption(int commandInterfaceCFReason, Message onComplete) {
        if (isValidCommandInterfaceCFReason(commandInterfaceCFReason)) {
            if (LOCAL_DEBUG) LOGD("requesting call forwarding query.");
            mCi.queryCallForwardStatus(commandInterfaceCFReason,0,null,onComplete);
        }
    }

    public void setVtCallForwardingOption(int commandInterfaceCFAction,
            int commandInterfaceCFReason,
            String dialingNumber,
            int timerSeconds,
            Message onComplete) {
        if (    (isValidCommandInterfaceCFAction(commandInterfaceCFAction)) &&
                (isValidCommandInterfaceCFReason(commandInterfaceCFReason))) {

            mCi.setCallForward(commandInterfaceCFAction,
                    commandInterfaceCFReason,
                    CommandsInterface.SERVICE_CLASS_VIDEO,
                    dialingNumber,
                    timerSeconds,
                    onComplete);
        }
    }

    public void getVtCallWaiting(Message onComplete) {
        mCi.queryCallWaiting(CommandsInterface.SERVICE_CLASS_VIDEO, onComplete);
    }

    public void setVtCallWaiting(boolean enable, Message onComplete) {
        mCi.setCallWaiting(enable, CommandsInterface.SERVICE_CLASS_VIDEO, onComplete);
    }

    public void getVtFacilityLock(String facility, String password, Message onComplete) {
        mCi.queryFacilityLock(facility, password, CommandsInterface.SERVICE_CLASS_VIDEO,onComplete);
    }

    public void setVtFacilityLock(String facility, boolean enable, String password, Message onComplete) {
        mCi.setFacilityLock(facility, enable, password, CommandsInterface.SERVICE_CLASS_VIDEO, onComplete);
    }
    /* vt end */

    public void updateSimIndicateState(){
        IccCardConstants.State simState = IccCardConstants.State.UNKNOWN;
        String prop = SystemProperties.get(PROPERTY_SIM_STATE[getMySimId()]);

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

        DataState dataState = PhoneConstants.DataState.DISCONNECTED;
        ServiceState svState = getServiceState();

        synchronized (mConnectedApns) {
            if (!mConnectedApns.isEmpty()) {
                dataState = PhoneConstants.DataState.CONNECTED;
            }
        }

        LOGD("updateSimIndicateState simState is " + simState + " dataState is " 
                                          + dataState + " svState is " + svState);
        int newState = getSimIndicatorStateFromStates(simState, svState,dataState);
        if (mSimIndicatorState != newState){
            mSimIndicatorState = newState;
            broadcastSimIndStateChangedIntent(newState);
        }
        LOGD("updateSimIndicateState new state is " + newState);
    }

    public void updateDataStateOfSimIndication(String apnType, PhoneConstants.DataState state) {
        synchronized (mConnectedApns) {
            if (state == PhoneConstants.DataState.CONNECTED) {
                if (!mConnectedApns.contains(apnType)) {
                    mConnectedApns.add(apnType);
                    LOGD("updateDataStateOfSimIndication add apnType " + apnType);
                }
            } else {
                if (mConnectedApns.remove(apnType)) {
                    LOGD("updateDataStateOfSimIndication remove apnType " + apnType);
                    if (mConnectedApns.isEmpty()) {
                        LOGD("updateDataStateOfSimIndication no more connected apnType.");
                    }
                }
            }
        }
        updateSimIndicateState();
    }

    public int getSimIndicatorState() {
        return mSimIndicatorState;
    }

    /**
       * Check if phone is hiding network temporary out of service state
       * @return if phone is hiding network temporary out of service state.
       */
    public int getNetworkHideState(){
        if(mSST.dontUpdateNetworkStateFlag == true){
            return ServiceState.STATE_OUT_OF_SERVICE;
        } else {
            return mSST.mSS.getState();
        }
    }
	
    /**
      * Get the whole state from SIM State, Service State and Data connection state 
      * @param  simState  of IccCard.State 
      * @param  svState of ServiceState
      * @param  dataState of int type
      * @return sim indicator state.    
      *
     */
    private int getSimIndicatorStateFromStates(IccCardConstants.State simState, ServiceState svState, PhoneConstants.DataState dataState){
        int retState = PhoneConstants.SIM_INDICATOR_UNKNOWN;
        int nSvState = svState.getState();
        int nRegState = svState.getRegState();

        LOGD("getSimIndicatorStateFromStates simState" + simState +
                " nSvState: " + nSvState +
                " DataRegState:" + svState.getDataRegState() +
                " DataNetworkType:" + svState.getDataNetworkType() +
                " dataState:" + dataState);

        if(simState.isLocked()){
            retState = PhoneConstants.SIM_INDICATOR_LOCKED;
        }else {
            if(nSvState == ServiceState.STATE_POWER_OFF){
                retState = PhoneConstants.SIM_INDICATOR_RADIOOFF;

            //MTK-ADD Start : for CS not registered , PS regsitered (ex: LTE PS only mode or 2/3G PS only SIM card or CS domain network registeration temporary failure
            } else if ((nSvState == ServiceState.STATE_IN_SERVICE) ||
                        ((nSvState != ServiceState.STATE_IN_SERVICE) &&
                        (svState.getDataRegState() == ServiceState.STATE_IN_SERVICE))){
                if (dataState == DataState.CONNECTED){
                    retState = svState.getRoaming()? PhoneConstants.SIM_INDICATOR_ROAMINGCONNECTED:
                                                      PhoneConstants.SIM_INDICATOR_CONNECTED;
                } else {
                    retState = svState.getRoaming()? PhoneConstants.SIM_INDICATOR_ROAMING:
                                                      PhoneConstants.SIM_INDICATOR_NORMAL;
                }
                //MTK-ADD END : for CS not registered , PS regsitered (ex: LTE PS only mode or 2/3G PS only SIM card or CS domain network registeration temporary failure

            } else if(nSvState == ServiceState.STATE_OUT_OF_SERVICE){          
                if(nRegState == ServiceState.REGISTRATION_STATE_NOT_REGISTERED_AND_SEARCHING) {
                    retState = PhoneConstants.SIM_INDICATOR_SEARCHING;
                }else {
                    retState = PhoneConstants.SIM_INDICATOR_INVALID;
                }
            //MTK-ADD START : for CS not registered , PS regsitered (ex: LTE PS only mode or 2/3G PS only SIM card or CS domain network registeration temporary failure
            //}else if(nSvState == ServiceState.STATE_IN_SERVICE){
            //    if (dataState == DataState.CONNECTED){
            //        retState = svState.getRoaming()? PhoneConstants.SIM_INDICATOR_ROAMINGCONNECTED:
            //                                          PhoneConstants.SIM_INDICATOR_CONNECTED;
            //       
            //    } else {
            //        retState = svState.getRoaming()? PhoneConstants.SIM_INDICATOR_ROAMING:
            //                                          PhoneConstants.SIM_INDICATOR_NORMAL;
            //}
            //MTK-ADD END : for CS not registered , PS regsitered (ex: LTE PS only mode or 2/3G PS only SIM card or CS domain network registeration temporary failure
            }
        }
        return retState;
    
    }

    private void broadcastSimIndStateChangedIntent(int nState) {
        Intent intent = new Intent(TelephonyIntents.ACTION_SIM_INDICATOR_STATE_CHANGED);
        intent.putExtra(TelephonyIntents.INTENT_KEY_ICC_STATE, nState);
        intent.putExtra(TelephonyIntents.INTENT_KEY_ICC_SLOT, getMySimId());
        LOGD("Broadcasting intent ACTION_SIM_INDICATOR_STATE_CHANGED " +  nState
                + " sim id " + getMySimId());
        ActivityManagerNative.broadcastStickyIntent(intent, READ_PHONE_STATE, UserHandle.USER_ALL);
  /*      if (nState == SIM_INDICATOR_LOCKED){
            mIccRecords.setDefaultNameForNewSIM(null);
        }*/
    }

    /**
     * Request 2G context authentication for SIM/USIM
     */
    public void doSimAuthentication (String strRand, Message result) {
        mCi.doSimAuthentication(strRand,  result);
    }

    /**
     * Request 3G context authentication for USIM
     */
    public void doUSimAuthentication (String strRand, String strAutn, Message result) {
	 mCi.doUSimAuthentication(strRand, strAutn, result);
    }
	

    private void Cclog(String s) {
        if (LOCAL_DEBUG) LOGD("[CC][GsmPhone][SIM" + (getMySimId()==0?"1":"2") +"] " + s);
    }

/* 3G switch start */
    public int get3GCapabilitySIM() {
        if (FeatureOption.MTK_GEMINI_3G_SWITCH) {
            if (LOCAL_DEBUG) LOGD("[" + mySimId + "] get3GCapabilitySIM [" + m3GCapabilitySIM + "]");
            return m3GCapabilitySIM;
        }
        return PhoneConstants.GEMINI_SIM_1;
    }

    public boolean set3GCapabilitySIM(int simId) {
        if (FeatureOption.MTK_GEMINI_3G_SWITCH) {
            if (m3GCapabilitySIM != simId) {
                if (LOCAL_DEBUG) LOGD("[" + mySimId + "] set3GCapabilitySIM [" + simId + "]");
                if (simId == PhoneConstants.GEMINI_SIM_1)
                    simId = 1;
                else if (simId == PhoneConstants.GEMINI_SIM_2)
                    simId = 2;
                else if (simId == PhoneConstants.GEMINI_SIM_3)
                    simId = 3;
                else if (simId == PhoneConstants.GEMINI_SIM_4)
                    simId = 4;				

                if (simId == -1)
                    mTargetNetworkMode = NT_MODE_GSM_ONLY;
                else
                    mTargetNetworkMode = NT_MODE_WCDMA_PREF;

                mCi.set3GCapabilitySIM(simId, obtainMessage(EVENT_SET_3G_CAPABILITY));
                return true;
            } else {
                if (LOCAL_DEBUG) LOGD("[" + mySimId + "] set3GCapabilitySIM to the same SIM[" + simId + "]");
            }
        }
        return false;
    }

    static public void set3GSim(int simId) {
        m3GCapabilitySIM = simId;
        if (simId == -1)
            mTargetNetworkMode = NT_MODE_GSM_ONLY;
        else
            mTargetNetworkMode = NT_MODE_WCDMA_PREF;
    }
/* 3G switch end */

    public void getPolCapability(Message onComplete) {
        mCi.getPOLCapabilty(onComplete);
    }

    public void getPreferedOperatorList(Message onComplete) {
        mCi.getCurrentPOLList(onComplete);
    }

    public void setPolEntry(NetworkInfoWithAcT networkWithAct, Message onComplete) {
        mCi.setPOLEntry(networkWithAct.getPriority(),networkWithAct.getOperatorNumeric(),
                                    networkWithAct.getAccessTechnology(), onComplete);
    }

    public boolean isRadioAvailable() {
        return mIsRadioAvailable;
    }

    public void forceNotifyServiceStateChange() {
        super.notifyServiceStateChangedP(mSST.mSS);
    }

    public void setPreferredNetworkTypeRIL(int NetworkType) {
        mCi.setPreferredNetworkTypeRIL(NetworkType);
    }

    public void setCurrentPreferredNetworkType() {
        mCi.setCurrentPreferredNetworkType();
    }
    
    public void setSimRecoveryOn(int Type, Message response) {
        mCi.setSimRecoveryOn(Type,response);
    }
    
    public void getSimRecoveryOn(Message response) {
        mCi.getSimRecoveryOn(response);
    }

    public void setTRM(int mode, Message response) {
        mCi.setTRM(mode,response);
    }	
    //MTK-END [mtk04070][111118][ALPS00093395]MTK proprietary methods
 
//MTK-START [mtk80950][120410][ALPS00266631]check whether download calibration data or not
    private void updateIsDownloadCalibrationData(boolean mCalibrationData) {
        Intent intent = new Intent(TelephonyIntents.ACTION_DOWNLOAD_CALIBRATION_DATA);
        intent.putExtra(TelephonyIntents.EXTRA_CALIBRATION_DATA, mCalibrationData);
        getContext().sendStickyBroadcast(intent);
    }

    //ALPS00279048
    public void setCRO(int mode, Message onComplete) {
        String cmdStr[] = {"AT+ECRO=0", ""};

        /* ALPS00310187 add mode 2 and 3 support */
        if(mode == 0){
            cmdStr[0] = "AT+ECRO=0";
        }else if(mode == 1){
            cmdStr[0] = "AT+ECRO=1";
        }else if(mode == 2){
            cmdStr[0] = "AT+ECRO=2";
        }else if(mode == 3){
            cmdStr[0] = "AT+ECRO=3";
        }else{
            LOGD("Invalid parameter in setCRO:" + mode);        
            return;
        }   
		
        this.invokeOemRilRequestStrings(cmdStr,onComplete);        
    }

    // ALPS00302702 RAT balancing
    public int getEfRatBalancing() {
        if(mIccRecords.get() != null) {
            return mIccRecords.get().getEfRatBalancing();
        }
        return 0;
    }

//MTK-END [mtk80950][120410][ALPS00266631]check whether download calibration data or not

    // ALPS00294581
    private void getSimMissingStatus() { // Single Card
        mCi.detectSimMissing(obtainMessage(EVENT_QUERY_SIM_MISSING_STATUS));
    }
    public void notifySimMissingStatus(boolean isSimInsert) { // Gemini Card
        if(!isSimInsert) {
            LOGW("[notifySimMissingStatus, card is not present]");
            mCi.notifySimMissing();
            mSST.resetNotification(); //ALPS00297554
        } else {
            LOGW("[notifySimMissingStatus, card is present]");
            //To do: It must be refactory.
            //START
            //if (mIccCard.get() != null)
            //    mIccCard.get().disableSimMissingNotification();
            //END
        }
    }

    // MVNO-API START
    public String getMvnoMatchType() {
        String type = PhoneConstants.MVNO_TYPE_NONE;
        if(mIccRecords.get() != null) {
            type = mIccRecords.get().getMvnoMatchType();
        }
        LOGD("getMvnoMatchType: Type = " + type);
        return type;
    }

    public String getMvnoPattern(String type) {
        String pattern = "";
        LOGD("getMvnoPattern:Type = " + type);

        if(mIccRecords.get() != null) {
            if(type.equals(PhoneConstants.MVNO_TYPE_SPN)) {
                pattern = mIccRecords.get().getSpNameInEfSpn();
            } else if(type.equals(PhoneConstants.MVNO_TYPE_IMSI)) {
                pattern = mIccRecords.get().isOperatorMvnoForImsi();
            } else if(type.equals(PhoneConstants.MVNO_TYPE_PNN)) {
               pattern = mIccRecords.get().isOperatorMvnoForEfPnn();
            } else if(type.equals(PhoneConstants.MVNO_TYPE_GID)) {
               pattern = mIccRecords.get().getGid1();
            } else {
               LOGE("getMvnoPattern: Wrong type.");
            }
        }
        LOGD("getMvnoPattern: pattern = " + pattern);
        return pattern;
    }

    // MVNO-API END

    //[New R8 modem FD]
    /**
       * setFDTimerValue
       * @param String array for new Timer Value
       * @param Message for on complete
       * @internal
       */
    public int setFDTimerValue(String newTimerValue[], Message onComplete) {
        return mDcTracker.setFDTimerValue(newTimerValue, onComplete);
    }

    //[New R8 modem FD]
    /**
       * getFDTimerValue
       * @return FD Timer String array
       * @internal
       */
    public String[] getFDTimerValue() {
        return mDcTracker.getFDTimerValue();
    }	

    // Femtocell (CSG) feature START
    public void getFemtoCellList(String operatorNumeric,int rat,Message response){
        LOGD("getFemtoCellList(),operatorNumeric="+operatorNumeric+",rat="+rat);				
        mCi.getFemtoCellList(operatorNumeric,rat,response);
    }		

    public void abortFemtoCellList(Message response){
        LOGD("abortFemtoCellList()");				
        mCi.abortFemtoCellList(response);		
    }		

    public void selectFemtoCell(FemtoCellInfo femtocell, Message response){
        LOGD("selectFemtoCell(): "+femtocell);				
        mCi.selectFemtoCell(femtocell,response);				
    }
    // Femtocell (CSG) feature END

    public boolean queryCfuOrWait() {
        int sid1 = 99, sid2 = 99;
        if (mySimId == PhoneConstants.GEMINI_SIM_1) {
           sid1 = PhoneConstants.GEMINI_SIM_2;
           sid2 = PhoneConstants.GEMINI_SIM_3;
        } else if (mySimId == PhoneConstants.GEMINI_SIM_2) {
           sid1 = PhoneConstants.GEMINI_SIM_1;
           sid2 = PhoneConstants.GEMINI_SIM_3;
        } else if (mySimId == PhoneConstants.GEMINI_SIM_3) {	
           sid1 = PhoneConstants.GEMINI_SIM_1;
           sid2 = PhoneConstants.GEMINI_SIM_2;
        }
        String oppositePropertyValue1 = SystemProperties.get(CFU_QUERY_PROPERTY_NAME + sid1);
        String oppositePropertyValue2 = SystemProperties.get(CFU_QUERY_PROPERTY_NAME + sid2);
        if ((oppositePropertyValue1.equals("1")) ||
            (oppositePropertyValue2.equals("1"))) {	/* The opposite phone is querying CFU status */
           Message message = obtainMessage(EVENT_CFU_QUERY_TIMEOUT);
           sendMessageDelayed(message, cfuQueryWaitTime);
           return false;
        } else {
           setSystemProperty(CFU_QUERY_PROPERTY_NAME + mySimId, "1");
           mCi.queryCallForwardStatus(CF_REASON_UNCONDITIONAL, SERVICE_CLASS_VOICE, null, obtainMessage(EVENT_GET_CALL_FORWARD_DONE));
           return true;
        }
    }	


    private final BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {

        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (LOCAL_DEBUG) LOGW("received broadcast " + action);

            if (TelephonyIntents.ACTION_SIM_INFO_UPDATE.equals(action)) {
                SIMInfo mySimInfo = SIMInfo.getSIMInfoBySlot(context, getMySimId());
                String mySettingName = CFU_QUERY_ICCID_PROP + getMySimId();
                String oldIccId = SystemProperties.get(mySettingName, "");

                if(mySimInfo != null) {
                    if(!mySimInfo.mICCId.equals(oldIccId)) {
                        LOGW(" mySimId " + mySimId + " mySettingName " + mySettingName + " old iccid : " + oldIccId + " new iccid : " + mySimInfo.mICCId);
                        SystemProperties.set(mySettingName, mySimInfo.mICCId);
                        String isChanged = CFU_QUERY_SIM_CHANGED_PROP + getMySimId();
                        SystemProperties.set(isChanged, "1");
                    needQueryCfu = true;
                    }
                }
            }
        }
    };

    
    private void updateCipherIndication() {
        if(getState() != PhoneConstants.State.IDLE && mSimEnableCipher == 1) {
            int[] cipherResult = new int[2];

            LOGD("GSMPhone[" + mySimId + "] notify cipher status to ap");

            cipherResult[0] = mCsCipherStatus;
            cipherResult[1] = mPsCipherStatus;
            
            mCipherIndicationRegistrants.notifyRegistrants(
                        new AsyncResult (null, cipherResult, null));
        }
    }
}

