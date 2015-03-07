/* Copyright Statement:
 *
 * This software/firmware and related documentation ("MediaTek Software") are
 * protected under relevant copyright laws. The information contained herein
 * is confidential and proprietary to MediaTek Inc. and/or its licensors.
 * Without the prior written permission of MediaTek inc. and/or its licensors,
 * any reproduction, modification, use or disclosure of MediaTek Software,
 * and information contained herein, in whole or in part, shall be strictly prohibited.
 *
 * MediaTek Inc. (C) 2010. All rights reserved.
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

package com.android.internal.telephony;

import android.os.AsyncResult;
import android.os.Handler;
import android.os.Message;
import android.os.Registrant;
import android.os.RegistrantList;
import android.os.SystemClock;
import android.telephony.CellInfo;
import android.telephony.ServiceState;
import android.telephony.SignalStrength;
import android.util.Pair;
import android.util.TimeUtils;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

import com.android.internal.telephony.dataconnection.DcTrackerBase;
import com.android.internal.telephony.uicc.IccCardApplicationStatus.AppState;
import com.android.internal.telephony.uicc.IccRecords;
import com.android.internal.telephony.uicc.UiccCardApplication;
import com.android.internal.telephony.uicc.UiccController;

/**
 * {@hide}
 */
public abstract class ServiceStateTracker extends Handler {


    //MTK-START [ALPS415367]For MR1 Migration
    //public ServiceState ss = new ServiceState();
    //protected ServiceState newSS = new ServiceState();
    public ServiceState mSS;
    protected ServiceState mNewSS;
    //MTK-END [ALPS415367]For MR1 Migration

    protected static final String PROP_FORCE_ROAMING = "telephony.test.forceRoaming";

    protected CommandsInterface mCi;
    protected UiccController mUiccController = null;
    protected UiccCardApplication mUiccApplcation = null;
    protected IccRecords mIccRecords = null;

    protected PhoneBase mPhoneBase;

    /* MR2 migration , remove mLastCellInfo and add new members START */
    protected boolean mVoiceCapable;

////    protected CellInfo mLastCellInfo = null;
    private static final long LAST_CELL_INFO_LIST_MAX_AGE_MS = 2000;
    protected long mLastCellInfoListTime;
    protected List<CellInfo> mLastCellInfoList = null;
    /* MR2 migration , remove mLastCellInfo and add new members END */
    protected int mCellInfoRate = Integer.MAX_VALUE;

    // This is final as subclasses alias to a more specific type
    // so we don't want the reference to change.
    protected final CellInfo mCellInfo;

    //MTK-START [ALPS415367]For MR1 Migration
    //protected SignalStrength mSignalStrength = new SignalStrength();
    protected SignalStrength mSignalStrength;
    //MTK-NED [ALPS415367]For MR1 Migration

    //MTK-START [mtk04070][111125][ALPS00093395]MTK added
    static final String LOG_TAG = "ServiceStateTracker";
    //MTK-END [mtk04070][111125][ALPS00093395]MTK added


    // TODO - this should not be public
    public RestrictedState mRestrictedState = new RestrictedState();

    /* The otaspMode passed to PhoneStateListener#onOtaspChanged */
    static public final int OTASP_UNINITIALIZED = 0;
    static public final int OTASP_UNKNOWN = 1;
    static public final int OTASP_NEEDED = 2;
    static public final int OTASP_NOT_NEEDED = 3;

    /**
     * A unique identifier to track requests associated with a poll
     * and ignore stale responses.  The value is a count-down of
     * expected responses in this pollingContext.
     */
    protected int[] mPollingContext;
    protected boolean mDesiredPowerState;

    /**
     *  Values correspond to ServiceState.RIL_RADIO_TECHNOLOGY_ definitions.
     */
////    protected int mRilRadioTechnology = 0;
////    protected int mNewRilRadioTechnology = 0;
//// MR2 migration remove to ServiceState

    /**
     * By default, strength polling is enabled.  However, if we're
     * getting unsolicited signal strength updates from the radio, set
     * value to true and don't bother polling any more.
     */
    protected boolean mDontPollSignalStrength = false;

    protected RegistrantList mRoamingOnRegistrants = new RegistrantList();
    protected RegistrantList mRoamingOffRegistrants = new RegistrantList();
    protected RegistrantList mAttachedRegistrants = new RegistrantList();
    protected RegistrantList mDetachedRegistrants = new RegistrantList();
    protected RegistrantList mDataRegStateOrRatChangedRegistrants = new RegistrantList();
    protected RegistrantList mNetworkAttachedRegistrants = new RegistrantList();
    protected RegistrantList mPsRestrictEnabledRegistrants = new RegistrantList();
    protected RegistrantList mPsRestrictDisabledRegistrants = new RegistrantList();

    /* Radio power off pending flag and tag counter */
    protected boolean mPendingRadioPowerOffAfterDataOff = false;
    private int mPendingRadioPowerOffAfterDataOffTag = 0;

    protected  static final boolean DBG = true;
    protected static final boolean VDBG = false;

    //MTK-START [mtk04070][111125][ALPS00093395]Replace 20 with 10
    /** Signal strength poll rate. */
    protected static final int POLL_PERIOD_MILLIS = 10 * 1000;
    //MTK-END [mtk04070][111125][ALPS00093395]Replace 20 with 10

    /** Waiting period before recheck gprs and voice registration. */
    public static final int DEFAULT_GPRS_CHECK_PERIOD_MILLIS = 60 * 1000;

    /** GSM events */
    protected static final int EVENT_RADIO_STATE_CHANGED               = 1;
    protected static final int EVENT_NETWORK_STATE_CHANGED             = 2;
    protected static final int EVENT_GET_SIGNAL_STRENGTH               = 3;
    protected static final int EVENT_POLL_STATE_REGISTRATION           = 4;
    protected static final int EVENT_POLL_STATE_GPRS                   = 5;
    protected static final int EVENT_POLL_STATE_OPERATOR               = 6;
    protected static final int EVENT_POLL_SIGNAL_STRENGTH              = 10;
    protected static final int EVENT_NITZ_TIME                         = 11;
    protected static final int EVENT_SIGNAL_STRENGTH_UPDATE            = 12;
    protected static final int EVENT_RADIO_AVAILABLE                   = 13;
    protected static final int EVENT_POLL_STATE_NETWORK_SELECTION_MODE = 14;
    protected static final int EVENT_GET_LOC_DONE                      = 15;
    protected static final int EVENT_SIM_RECORDS_LOADED                = 16;
    protected static final int EVENT_SIM_READY                         = 17;
    protected static final int EVENT_LOCATION_UPDATES_ENABLED          = 18;
    protected static final int EVENT_GET_PREFERRED_NETWORK_TYPE        = 19;
    protected static final int EVENT_SET_PREFERRED_NETWORK_TYPE        = 20;
    protected static final int EVENT_RESET_PREFERRED_NETWORK_TYPE      = 21;
    protected static final int EVENT_CHECK_REPORT_GPRS                 = 22;
    protected static final int EVENT_RESTRICTED_STATE_CHANGED          = 23;

    /** CDMA events */
    protected static final int EVENT_POLL_STATE_REGISTRATION_CDMA      = 24;
    protected static final int EVENT_POLL_STATE_OPERATOR_CDMA          = 25;
    protected static final int EVENT_RUIM_READY                        = 26;
    protected static final int EVENT_RUIM_RECORDS_LOADED               = 27;
    protected static final int EVENT_POLL_SIGNAL_STRENGTH_CDMA         = 28;
    protected static final int EVENT_GET_SIGNAL_STRENGTH_CDMA          = 29;
    protected static final int EVENT_NETWORK_STATE_CHANGED_CDMA        = 30;
    protected static final int EVENT_GET_LOC_DONE_CDMA                 = 31;
    //protected static final int EVENT_UNUSED                            = 32;
    protected static final int EVENT_NV_LOADED                         = 33;
    protected static final int EVENT_POLL_STATE_CDMA_SUBSCRIPTION      = 34;
    protected static final int EVENT_NV_READY                          = 35;
    protected static final int EVENT_ERI_FILE_LOADED                   = 36;
    protected static final int EVENT_OTA_PROVISION_STATUS_CHANGE       = 37;
    protected static final int EVENT_SET_RADIO_POWER_OFF               = 38;
    protected static final int EVENT_CDMA_SUBSCRIPTION_SOURCE_CHANGED  = 39;
    protected static final int EVENT_CDMA_PRL_VERSION_CHANGED          = 40;
    protected static final int EVENT_RADIO_ON                          = 41;
    protected static final int EVENT_ICC_CHANGED                       = 42;
    protected static final int EVENT_GET_CELL_INFO_LIST                = 43; // MR2 newly added
    protected static final int EVENT_UNSOL_CELL_INFO_LIST              = 44; // MR2 newly added

    /** MTK added events begin*/
    protected static final int EVENT_DATA_CONNECTION_DETACHED = 100;
    protected static final int EVENT_INVALID_SIM_INFO = 101; //ALPS00248788	
    protected static final int EVENT_PS_NETWORK_STATE_CHANGED = 102;
    protected static final int EVENT_UPDATE_SELECTION_MODE = 103; /* ALPS00316998 */
    protected static final int EVENT_IMEI_LOCK = 104; /* ALPS00296298 */
    protected static final int EVENT_DISABLE_EMMRRS_STATUS = 105;
    protected static final int EVENT_ENABLE_EMMRRS_STATUS = 106;
    protected static final int EVENT_SET_SIM_RECOVERY_ON = 107;
    protected static final int EVENT_GET_SIM_RECOVERY_ON = 108;
    protected static final int EVENT_SIM_PLUG_OUT = 109;		
    protected static final int EVENT_ICC_REFRESH = 110;		
    protected static final int EVENT_FEMTO_CELL_INFO = 111;	
    protected static final int EVENT_GET_CELL_INFO_LIST_BY_RATE                = 112;
    /** MTK added events end*/

    /** VIA added events begin*/
    protected static final int EVENT_QUERY_NITZ_TIME = 200;
    protected static final int EVENT_GET_NITZ_TIME = 201;
    //add by via [ALPS00421033]
    protected static final int EVENT_NETWORK_TYPE_CHANGED = 202;	
    //VIA-START SET ETS
    protected static final int EVENT_ETS_DEV_CHANGED = 203; 
    //VIA-END SET ETS
    /** VIA added events end*/

    protected static final String TIMEZONE_PROPERTY = "persist.sys.timezone";

    /**
     * List of ISO codes for countries that can have an offset of
     * GMT+0 when not in daylight savings time.  This ignores some
     * small places such as the Canary Islands (Spain) and
     * Danmarkshavn (Denmark).  The list must be sorted by code.
    */
    protected static final String[] GMT_COUNTRY_CODES = {
        "bf", // Burkina Faso
        "ci", // Cote d'Ivoire
        "eh", // Western Sahara
        "fo", // Faroe Islands, Denmark
        "gb", // United Kingdom of Great Britain and Northern Ireland
        "gh", // Ghana
        "gm", // Gambia
        "gn", // Guinea
        "gw", // Guinea Bissau
        "ie", // Ireland
        "lr", // Liberia
        "is", // Iceland
        "ma", // Morocco
        "ml", // Mali
        "mr", // Mauritania
        "pt", // Portugal
        "sl", // Sierra Leone
        "sn", // Senegal
        "st", // Sao Tome and Principe
        "tg", // Togo
    };

    /* MR2 newly added function */
    private class CellInfoResult {
        List<CellInfo> list;
        Object lockObj = new Object();
    }

    /** Reason for registration denial. */
    protected static final String REGISTRATION_DENIED_GEN  = "General";
    protected static final String REGISTRATION_DENIED_AUTH = "Authentication Failure";

    //MTK-START [mtk03851][111220]MTK added
    /**
     *  Access technology currently in use.
     */
    protected static final int DATA_ACCESS_UNKNOWN = 0;
    protected static final int DATA_ACCESS_GPRS = 1;
    protected static final int DATA_ACCESS_EDGE = 2;
    protected static final int DATA_ACCESS_UMTS = 3;
    protected static final int DATA_ACCESS_CDMA_IS95A = 4;
    protected static final int DATA_ACCESS_CDMA_IS95B = 5;
    protected static final int DATA_ACCESS_CDMA_1xRTT = 6;
    protected static final int DATA_ACCESS_CDMA_EvDo_0 = 7;
    protected static final int DATA_ACCESS_CDMA_EvDo_A = 8;
    protected static final int DATA_ACCESS_HSDPA = 9;
    protected static final int DATA_ACCESS_HSUPA = 10;
    protected static final int DATA_ACCESS_HSPA = 11;
    protected static final int DATA_ACCESS_CDMA_EvDo_B = 12;
    //MTK-END [mtk03851][111220]MTK added

    protected ServiceStateTracker(PhoneBase phoneBase, CommandsInterface ci, CellInfo cellInfo) {
        mPhoneBase = phoneBase;
        mCellInfo = cellInfo;
        mCi = ci;
        mVoiceCapable = mPhoneBase.getContext().getResources().getBoolean(
                com.android.internal.R.bool.config_voice_capable);
        mUiccController = UiccController.getInstance();
        mUiccController.registerForIccChanged(this, EVENT_ICC_CHANGED, null);
        mCi.setOnSignalStrengthUpdate(this, EVENT_SIGNAL_STRENGTH_UPDATE, null);
        mCi.registerForCellInfoList(this, EVENT_UNSOL_CELL_INFO_LIST, null);
        //MTK-START: For KK Migration
        mPhoneBase.setSystemProperty(TelephonyProperties.PROPERTY_DATA_NETWORK_TYPE,
            ServiceState.rilRadioTechnologyToString(ServiceState.RIL_RADIO_TECHNOLOGY_UNKNOWN));
        //MTK-END: For KK Migration
    }

    //MTK-START [ALPS415367]For MR1 Migration
    protected ServiceStateTracker(PhoneBase phoneBase, CommandsInterface ci, CellInfo cellInfo, int mSimId) {
        mPhoneBase = phoneBase;
        mCellInfo = cellInfo;
        mCi = ci;
        mVoiceCapable = mPhoneBase.getContext().getResources().getBoolean(
                com.android.internal.R.bool.config_voice_capable); // MR2 migration
        mUiccController = UiccController.getInstance(phoneBase.getMySimId());
        mUiccController.registerForIccChanged(this, EVENT_ICC_CHANGED, null);
        mCi.setOnSignalStrengthUpdate(this, EVENT_SIGNAL_STRENGTH_UPDATE, null);
        mCi.registerForCellInfoList(this, EVENT_UNSOL_CELL_INFO_LIST, null);
        mSignalStrength = new SignalStrength(mSimId);
        mSS = new ServiceState(mSimId);
        mNewSS = new ServiceState(mSimId);

        //MTK-START: For KK Migration
        mPhoneBase.setSystemProperty(TelephonyProperties.PROPERTY_DATA_NETWORK_TYPE,
            ServiceState.rilRadioTechnologyToString(ServiceState.RIL_RADIO_TECHNOLOGY_UNKNOWN));
        //MTK-END: For KK Migration
    }
    //MTK-END [ALPS415367]For MR1 Migration

    //MTK-START [ALPS415367]For MR1 Migration
    public void dispose() {
        mCi.unSetOnSignalStrengthUpdate(this);
        mUiccController.unregisterForIccChanged(this); // MR2 migration
        mCi.unregisterForCellInfoList(this); // MR2 migration
    }
    //MTK-END [ALPS415367]For MR1 Migration

    public boolean getDesiredPowerState() {
        return mDesiredPowerState;
    }

    private SignalStrength mLastSignalStrength = null;
    protected boolean notifySignalStrength() {
        boolean notified = false;
        synchronized(mCellInfo) {
            if (!mSignalStrength.equals(mLastSignalStrength)) {
                try {
                    //MTK-START [ALPS415367]For MR1 Migration
                    // This takes care of delayed EVENT_POLL_SIGNAL_STRENGTH (scheduled after
                    // POLL_PERIOD_MILLIS) during Radio Technology Change)
                    //20120317 ALPS_00253948	 ignore unknown RSSI state (99) 		  
                    if((mSignalStrength.getGsmSignalStrength()== 99)&&(mSS.getState() == ServiceState.STATE_IN_SERVICE)){   
                        log("Ignore rssi 99(unknown)");
                    }
                    else {
                        if (DBG) {
                            log("notifySignalStrength: mSignalStrength.getLevel=" +
                                    mSignalStrength.getLevel());
                        }
                        mPhoneBase.notifySignalStrength();
                        mLastSignalStrength = new SignalStrength(mSignalStrength);
                        notified = true;
                    }
                    //MTK-END [ALPS415367]For MR1 Migration
                } catch (NullPointerException ex) {
                    loge("updateSignalStrength() Phone already destroyed: " + ex
                            + "SignalStrength not notified");
                }
            }
        }
        return notified;
    }

    /* KK newly added function */
    /**
     * Notify all mDataConnectionRatChangeRegistrants using an
     * AsyncResult in msg.obj where AsyncResult#result contains the
     * new RAT as an Integer Object.
     */
    protected void notifyDataRegStateRilRadioTechnologyChanged() {
        int rat = mSS.getRilDataRadioTechnology();
        int drs = mSS.getDataRegState();
        if (DBG) log("notifyDataRegStateRilRadioTechnologyChanged: drs=" + drs + " rat=" + rat);
        mPhoneBase.setSystemProperty(TelephonyProperties.PROPERTY_DATA_NETWORK_TYPE,
                ServiceState.rilRadioTechnologyToString(rat));
        mDataRegStateOrRatChangedRegistrants.notifyResult(new Pair<Integer, Integer>(drs, rat));
    }

    /* MR2 newly added function */
    /**
     * Some operators have been known to report registration failure
     * data only devices, to fix that use DataRegState.
     */
    protected void useDataRegStateForDataOnlyDevices() {
        if (mVoiceCapable == false) {
            if (DBG) {
                log("useDataRegStateForDataOnlyDevice: VoiceRegState=" + mNewSS.getVoiceRegState()
                    + " DataRegState=" + mNewSS.getDataRegState());
            }
            // TODO: Consider not lying and instead have callers know the difference. 
            mNewSS.setVoiceRegState(mNewSS.getDataRegState());

            /* Integrate ALPS00286197 with MR2 data only device state update */
            mNewSS.setRegState(ServiceState.REGISTRATION_STATE_HOME_NETWORK);						
        }
    }

    /* KK newly added function */
    protected void updatePhoneObject() {
        mPhoneBase.updatePhoneObject(mSS.getRilVoiceRadioTechnology());
    }

    /**
     * Registration point for combined roaming on
     * combined roaming is true when roaming is true and ONS differs SPN
     *
     * @param h handler to notify
     * @param what what code of message when delivered
     * @param obj placed in Message.obj
     */
    public  void registerForRoamingOn(Handler h, int what, Object obj) {
        Registrant r = new Registrant(h, what, obj);
        mRoamingOnRegistrants.add(r);

        if (mSS.getRoaming()) {
            r.notifyRegistrant();
        }
    }

    public  void unregisterForRoamingOn(Handler h) {
        mRoamingOnRegistrants.remove(h);
    }

    /**
     * Registration point for combined roaming off
     * combined roaming is true when roaming is true and ONS differs SPN
     *
     * @param h handler to notify
     * @param what what code of message when delivered
     * @param obj placed in Message.obj
     */
    public  void registerForRoamingOff(Handler h, int what, Object obj) {
        Registrant r = new Registrant(h, what, obj);
        mRoamingOffRegistrants.add(r);

        if (!mSS.getRoaming()) {
            r.notifyRegistrant();
        }
    }

    public  void unregisterForRoamingOff(Handler h) {
        mRoamingOffRegistrants.remove(h);
    }

    /**
     * Re-register network by toggling preferred network type.
     * This is a work-around to deregister and register network since there is
     * no ril api to set COPS=2 (deregister) only.
     *
     * @param onComplete is dispatched when this is complete.  it will be
     * an AsyncResult, and onComplete.obj.exception will be non-null
     * on failure.
     */
    public void reRegisterNetwork(Message onComplete) {
        mCi.getPreferredNetworkType(
                obtainMessage(EVENT_GET_PREFERRED_NETWORK_TYPE, onComplete));
    }

    public void
    setRadioPower(boolean power) {
        mDesiredPowerState = power;

        setPowerStateToDesired();
    }

    /**
     * These two flags manage the behavior of the cell lock -- the
     * lock should be held if either flag is true.  The intention is
     * to allow temporary acquisition of the lock to get a single
     * update.  Such a lock grab and release can thus be made to not
     * interfere with more permanent lock holds -- in other words, the
     * lock will only be released if both flags are false, and so
     * releases by temporary users will only affect the lock state if
     * there is no continuous user.
     */
   //// private boolean mWantContinuousLocationUpdates;
   //// private boolean mWantSingleLocationUpdate;

    public void enableSingleLocationUpdate() {
    	/** currently,modem do not support the function.
        if (mWantSingleLocationUpdate || mWantContinuousLocationUpdates) return;
        mWantSingleLocationUpdate = true;
        mCi.setLocationUpdates(true, obtainMessage(EVENT_LOCATION_UPDATES_ENABLED));
        **/
    }

    public void enableLocationUpdates() {
    	/** currently,modem do not support the function.
        if (mWantSingleLocationUpdate || mWantContinuousLocationUpdates) return;
        mWantContinuousLocationUpdates = true;
        mCi.setLocationUpdates(true, obtainMessage(EVENT_LOCATION_UPDATES_ENABLED));
        **/
    }

    protected void disableSingleLocationUpdate() {
    	/** currently,modem do not support the function.
        mWantSingleLocationUpdate = false;
        if (!mWantSingleLocationUpdate && !mWantContinuousLocationUpdates) {
            mCi.setLocationUpdates(false, null);
        }
        **/
    }

    public void disableLocationUpdates() {
    	/** currently,modem do not support the function.
        mWantContinuousLocationUpdates = false;
        if (!mWantSingleLocationUpdate && !mWantContinuousLocationUpdates) {
            mCi.setLocationUpdates(false, null);
        }
        **/
    }

    @Override
    public void handleMessage(Message msg) {
        switch (msg.what) {
            case EVENT_SET_RADIO_POWER_OFF:
                synchronized(this) {
                    if (mPendingRadioPowerOffAfterDataOff &&
                            (msg.arg1 == mPendingRadioPowerOffAfterDataOffTag)) {
                        if (DBG) log("EVENT_SET_RADIO_OFF, turn radio off now.");
                        hangupAndPowerOff();
                        mPendingRadioPowerOffAfterDataOffTag += 1;
                        mPendingRadioPowerOffAfterDataOff = false;
                    } else {
                        log("EVENT_SET_RADIO_OFF is stale arg1=" + msg.arg1 +
                                "!= tag=" + mPendingRadioPowerOffAfterDataOffTag);
                    }
                }
                break;

            case EVENT_ICC_CHANGED:
                onUpdateIccAvailability();
                break;

            /* MR2 newly added event handling START */
            case EVENT_GET_CELL_INFO_LIST_BY_RATE:
            case EVENT_GET_CELL_INFO_LIST: {
                AsyncResult ar = (AsyncResult) msg.obj;
                CellInfoResult result = (CellInfoResult) ar.userObj;
                synchronized(result.lockObj) {
                    if (ar.exception != null) {
                        log("EVENT_GET_CELL_INFO_LIST: error ret null, e=" + ar.exception);
                        result.list = null;
                    } else {
                        result.list = (List<CellInfo>) ar.result;
                        log("EVENT_GET_CELL_INFO_LIST: size=" + result.list.size()+ " list=" + result.list);
                    }
                    mLastCellInfoListTime = SystemClock.elapsedRealtime();
                    mLastCellInfoList = result.list;
                    if(msg.what == EVENT_GET_CELL_INFO_LIST_BY_RATE){
                        log("EVENT_GET_CELL_INFO_LIST_BY_RATE notify result");												
                        mPhoneBase.notifyCellInfo(result.list);
                    }else{						
                        result.lockObj.notify();
                        log("EVENT_GET_CELL_INFO_LIST notify result");						
                    }						
                }
                break;
            }

            case EVENT_UNSOL_CELL_INFO_LIST: {
                AsyncResult ar = (AsyncResult) msg.obj;
                if (ar.exception != null) {
                    log("EVENT_UNSOL_CELL_INFO_LIST: error ignoring, e=" + ar.exception);
                } else {
                    List<CellInfo> list = (List<CellInfo>) ar.result;
                    if (DBG) {
                        log("EVENT_UNSOL_CELL_INFO_LIST: size=" + list.size()
                                + " list=" + list);
                    }
                    mLastCellInfoListTime = SystemClock.elapsedRealtime();
                    mLastCellInfoList = list;
                    mPhoneBase.notifyCellInfo(list);
                }
                break;
            }
            /* MR2 newly added event handling END */

            default:
                log("Unhandled message with number: " + msg.what);
                break;
        }
    }

    protected abstract Phone getPhone();
    protected abstract void handlePollStateResult(int what, AsyncResult ar);
    protected void updateSpnDisplay(){}
    protected abstract void setPowerStateToDesired();
    protected abstract void onUpdateIccAvailability();
    protected abstract void log(String s);
    protected abstract void loge(String s);

    public abstract int getCurrentDataConnectionState();
    public abstract boolean isConcurrentVoiceAndDataAllowed();
    public void removeGprsConnTypeRetry() {}

    public void refreshSpnDisplay() {}
    public void setPsConnType(int type) {}

    /**
     * Registration point for transition into DataConnection attached.
     * @param h handler to notify
     * @param what what code of message when delivered
     * @param obj placed in Message.obj
     */
    public void registerForDataConnectionAttached(Handler h, int what, Object obj) {
        Registrant r = new Registrant(h, what, obj);
        mAttachedRegistrants.add(r);

        if (getCurrentDataConnectionState() == ServiceState.STATE_IN_SERVICE) {
            r.notifyRegistrant();
        }
    }
    public void unregisterForDataConnectionAttached(Handler h) {
        mAttachedRegistrants.remove(h);
    }

    /**
     * Registration point for transition into DataConnection detached.
     * @param h handler to notify
     * @param what what code of message when delivered
     * @param obj placed in Message.obj
     */
    public void registerForDataConnectionDetached(Handler h, int what, Object obj) {
        Registrant r = new Registrant(h, what, obj);
        mDetachedRegistrants.add(r);

        if (getCurrentDataConnectionState() != ServiceState.STATE_IN_SERVICE) {
            r.notifyRegistrant();
        }
    }
    public void unregisterForDataConnectionDetached(Handler h) {
        mDetachedRegistrants.remove(h);
    }

    /* KK newly added function */
    /**
     * Registration for DataConnection RIL Data Radio Technology changing. The
     * new radio technology will be returned AsyncResult#result as an Integer Object.
     * The AsyncResult will be in the notification Message#obj.
     *
     * @param h handler to notify
     * @param what what code of message when delivered
     * @param obj placed in Message.obj
     */
    public void registerForDataRegStateOrRatChanged(Handler h, int what, Object obj) {
        Registrant r = new Registrant(h, what, obj);
        mDataRegStateOrRatChangedRegistrants.add(r);
        notifyDataRegStateRilRadioTechnologyChanged();
    }
    /* KK newly added function */
    public void unregisterForDataRegStateOrRatChanged(Handler h) {
        mDataRegStateOrRatChangedRegistrants.remove(h);
    }

    /**
     * Registration point for transition into network attached.
     * @param h handler to notify
     * @param what what code of message when delivered
     * @param obj in Message.obj
     */
    public void registerForNetworkAttached(Handler h, int what, Object obj) {
        Registrant r = new Registrant(h, what, obj);

        mNetworkAttachedRegistrants.add(r);
        if (mSS.getVoiceRegState() == ServiceState.STATE_IN_SERVICE) {
            r.notifyRegistrant();
        }
    }
    public void unregisterForNetworkAttached(Handler h) {
        mNetworkAttachedRegistrants.remove(h);
    }

    /**
     * Registration point for transition into packet service restricted zone.
     * @param h handler to notify
     * @param what what code of message when delivered
     * @param obj placed in Message.obj
     */
    public void registerForPsRestrictedEnabled(Handler h, int what, Object obj) {
        Registrant r = new Registrant(h, what, obj);
        mPsRestrictEnabledRegistrants.add(r);

        if (mRestrictedState.isPsRestricted()) {
            r.notifyRegistrant();
        }
    }

    public void unregisterForPsRestrictedEnabled(Handler h) {
        mPsRestrictEnabledRegistrants.remove(h);
    }

    /**
     * Registration point for transition out of packet service restricted zone.
     * @param h handler to notify
     * @param what what code of message when delivered
     * @param obj placed in Message.obj
     */
    public void registerForPsRestrictedDisabled(Handler h, int what, Object obj) {
        Registrant r = new Registrant(h, what, obj);
        mPsRestrictDisabledRegistrants.add(r);

        if (mRestrictedState.isPsRestricted()) {
            r.notifyRegistrant();
        }
    }

    public void unregisterForPsRestrictedDisabled(Handler h) {
        mPsRestrictDisabledRegistrants.remove(h);
    }

    /**
     * Clean up existing voice and data connection then turn off radio power.
     *
     * Hang up the existing voice calls to decrease call drop rate.
     */
    public void powerOffRadioSafely(DcTrackerBase dcTracker) {
        synchronized (this) {
            if (!mPendingRadioPowerOffAfterDataOff) {
                // To minimize race conditions we call cleanUpAllConnections on
                // both if else paths instead of before this isDisconnected test.
                if (dcTracker.isDisconnected()) {
                    // To minimize race conditions we do this after isDisconnected
                    dcTracker.cleanUpAllConnections(Phone.REASON_RADIO_TURNED_OFF);
                    if (DBG) log("Data disconnected, turn off radio right away.");
                    hangupAndPowerOff();
                } else {
                    dcTracker.cleanUpAllConnections(Phone.REASON_RADIO_TURNED_OFF);
                    Message msg = Message.obtain(this);
                    msg.what = EVENT_SET_RADIO_POWER_OFF;
                    msg.arg1 = ++mPendingRadioPowerOffAfterDataOffTag;
                    if (sendMessageDelayed(msg, 5000)) {
                        if (DBG) log("Wait upto 5s for data to disconnect, then turn off radio.");
                        mPendingRadioPowerOffAfterDataOff = true;
                    } else {
                        log("Cannot send delayed Msg, turn off radio right away.");
                        hangupAndPowerOff();
                    }
                }
            }
        }
    }

    /**
     * process the pending request to turn radio off after data is disconnected
     *
     * return true if there is pending request to process; false otherwise.
     */
    public boolean processPendingRadioPowerOffAfterDataOff() {
        synchronized(this) {
            if (mPendingRadioPowerOffAfterDataOff) {
                if (DBG) log("Process pending request to turn radio off.");
                mPendingRadioPowerOffAfterDataOffTag += 1;
                hangupAndPowerOff();
                mPendingRadioPowerOffAfterDataOff = false;
                return true;
            }
            return false;
        }
    }

   /**
     * send signal-strength-changed notification if changed Called both for
     * solicited and unsolicited signal strength updates
     *
     * @return true if the signal strength changed and a notification was sent.
     */
    protected boolean onSignalStrengthResult(AsyncResult ar, boolean isGsm) {
        if ((DBG) && (mLastSignalStrength != null)){
            log("onSignalStrengthResult():  LastSignalStrength=" +
                    mLastSignalStrength.toString());
        }

        // This signal is used for both voice and data radio signal so parse
        // all fields
        if ((ar.exception == null) && (ar.result != null)) {
            mSignalStrength = (SignalStrength) ar.result;
            mSignalStrength.validateInput();
            mSignalStrength.setGsm(isGsm);
			if (DBG) log("onSignalStrengthResult():new mSignalStrength="+ mSignalStrength.toString());
        } else {
            log("onSignalStrengthResult() Exception from RIL : " + ar.exception);
            if(isGsm){
                mSignalStrength = new SignalStrength(mSignalStrength.getMySimId());
            } else {
                mSignalStrength = new SignalStrength(isGsm);
            }
        }

        return notifySignalStrength();
    }

    /**
     * Hang up all voice call and turn off radio. Implemented by derived class.
     */
    protected abstract void hangupAndPowerOff();

    /** Cancel a pending (if any) pollState() operation */
    protected void cancelPollState() {
        // This will effectively cancel the rest of the poll requests.
        mPollingContext = new int[1];
    }

    //MTK-START [mtk03851][111124]MTK proprietary methods
    public void setRadioPower(boolean power, boolean shutdown) {
        mDesiredPowerState = power;
        if (shutdown) {
            mCi.setRadioPowerOff(null);
        } else {
            this.setRadioPower(power);
        }
    }
    //MTK-END [mtk03851][111124]MTK proprietary methods

    /**
     * Return true if time zone needs fixing.
     *
     * @param phoneBase
     * @param operatorNumeric
     * @param prevOperatorNumeric
     * @param needToFixTimeZone
     * @return true if time zone needs to be fixed
     */
    protected boolean shouldFixTimeZoneNow(PhoneBase phoneBase, String operatorNumeric,
            String prevOperatorNumeric, boolean needToFixTimeZone) {
        // Return false if the mcc isn't valid as we don't know where we are.
        // Return true if we have an IccCard and the mcc changed or we
        // need to fix it because when the NITZ time came in we didn't
        // know the country code.

        // If mcc is invalid then we'll return false
        int mcc;
        try {
            mcc = Integer.parseInt(operatorNumeric.substring(0, 3));
        } catch (Exception e) {
            if (DBG) {
                log("shouldFixTimeZoneNow: no mcc, operatorNumeric=" + operatorNumeric +
                        " retVal=false");
            }
            return false;
        }

        // If prevMcc is invalid will make it different from mcc
        // so we'll return true if the card exists.
        int prevMcc;
        try {
            prevMcc = Integer.parseInt(prevOperatorNumeric.substring(0, 3));
        } catch (Exception e) {
            prevMcc = mcc + 1;
        }

        // Determine if the Icc card exists
        boolean iccCardExist = false;
        if (mUiccApplcation != null) {
            iccCardExist = mUiccApplcation.getState() != AppState.APPSTATE_UNKNOWN;
        }

        // Determine retVal
        boolean retVal = ((iccCardExist && (mcc != prevMcc)) || needToFixTimeZone);
        if (DBG) {
            long ctm = System.currentTimeMillis();
            log("shouldFixTimeZoneNow: retVal=" + retVal +
                    " iccCardExist=" + iccCardExist +
                    " operatorNumeric=" + operatorNumeric + " mcc=" + mcc +
                    " prevOperatorNumeric=" + prevOperatorNumeric + " prevMcc=" + prevMcc +
                    " needToFixTimeZone=" + needToFixTimeZone +
                    " ltod=" + TimeUtils.logTimeOfDay(ctm));
        }
        return retVal;
    }

    // MR1 migration
    /**
     * @return all available cell information or null if none.
     */
    public List<CellInfo> getAllCellInfo() {
        CellInfoResult result = new CellInfoResult();
        if (DBG) log("SST.getAllCellInfo(): enter");

        int ver = mCi.getRilVersion();
		
        if (ver >= 8) {
            if (isCallerOnDifferentThread()) {
                if ((SystemClock.elapsedRealtime() - mLastCellInfoListTime)
                        > LAST_CELL_INFO_LIST_MAX_AGE_MS) {
                    Message msg = obtainMessage(EVENT_GET_CELL_INFO_LIST, result);
                    synchronized(result.lockObj) {
                        mCi.getCellInfoList(msg);
                        try {
                            result.lockObj.wait();
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                            result.list = null;
                        }
                    }
                } else {
                    if (DBG) log("SST.getAllCellInfo(): return last, back to back calls");
                    result.list = mLastCellInfoList;
                }
            } else {
                if (DBG) log("SST.getAllCellInfo(): return last, same thread can't block");
                result.list = mLastCellInfoList;
            }
        } else {
            if (DBG) log("SST.getAllCellInfo(): not implemented");
            result.list = null;
        }
        if (DBG) {
            if (result.list != null) {
                log("SST.getAllCellInfo(): X size=" + result.list.size()
                        + " list=" + result.list);
            } else {
                log("SST.getAllCellInfo(): X size=0 list=null");
            }
        }
        return result.list;
    }


    protected List<CellInfo> getAllCellInfoByRate() {
        CellInfoResult result = new CellInfoResult();
        if (DBG) log("SST.getAllCellInfoByRate(): enter");

        int ver = mCi.getRilVersion();
		
        if (ver >= 8) {
            if (isCallerOnDifferentThread()) {			
                if ((SystemClock.elapsedRealtime() - mLastCellInfoListTime)
                        > LAST_CELL_INFO_LIST_MAX_AGE_MS) {
                    Message msg = obtainMessage(EVENT_GET_CELL_INFO_LIST_BY_RATE, result);
                    synchronized(result.lockObj) {
                        mCi.getCellInfoList(msg);
                        try {
                            result.lockObj.wait();
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                            result.list = null;
                        }
                    }
                } else {
                    if (DBG) log("SST.getAllCellInfo(): return last, back to back calls");
                    result.list = mLastCellInfoList;
                }				
            } else {
                if (DBG) log("SST.getAllCellInfo(): return last, same thread can't block");
                result.list = mLastCellInfoList;
            }				
        } else {
            if (DBG) log("SST.getAllCellInfo(): not implemented");
            result.list = null;
        }
        if (DBG) {
            if (result.list != null) {
                log("SST.getAllCellInfo(): X size=" + result.list.size()
                        + " list=" + result.list);
            } else {
                log("SST.getAllCellInfo(): X size=0 list=null");
            }
        }
        return result.list;
    }

    public void setCellInfoRate(int rateInMillis){
        log("SST.setCellInfoRate()");
        mCellInfoRate = rateInMillis;	
        updateCellInfoRate();
    }

    protected void updateCellInfoRate(){
        log("SST.updateCellInfoRate()");
    }

    // MR1 migration
    /**
     * @return signal strength
     */
    public SignalStrength getSignalStrength() {
        synchronized(mCellInfo) {
            return mSignalStrength;
        }
    }

    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        pw.println("ServiceStateTracker:");
        pw.println(" mSS=" + mSS);
        pw.println(" mNewSS=" + mNewSS);
        pw.println(" mCellInfo=" + mCellInfo);
        pw.println(" mSignalStrength=" + mSignalStrength);
        pw.println(" mRestrictedState=" + mRestrictedState);
        pw.println(" mPollingContext=" + mPollingContext);
        pw.println(" mDesiredPowerState=" + mDesiredPowerState);
        pw.println(" mDontPollSignalStrength=" + mDontPollSignalStrength);
        pw.println(" mPendingRadioPowerOffAfterDataOff=" + mPendingRadioPowerOffAfterDataOff);
        pw.println(" mPendingRadioPowerOffAfterDataOffTag=" + mPendingRadioPowerOffAfterDataOffTag);
    }


    // MR1 migration
    /**
     * Verifies the current thread is the same as the thread originally
     * used in the initialization of this instance. Throws RuntimeException
     * if not.
     *
     * @exception RuntimeException if the current thread is not
     * the thread that originally obtained this PhoneBase instance.
     */
    protected void checkCorrectThread() {
        if (Thread.currentThread() != getLooper().getThread()) {
            throw new RuntimeException(
                    "ServiceStateTracker must be used from within one thread");
        }
    }

    protected void setLastSignalStrengthDefaultValues(boolean isGsm){
        if(isGsm){
            mLastSignalStrength = new SignalStrength(mSignalStrength.getMySimId());
        } else {
            mLastSignalStrength = new SignalStrength(isGsm);
        }
    }

    /* MR2 newly added function */
    protected boolean isCallerOnDifferentThread() {
        boolean value = Thread.currentThread() != getLooper().getThread();
        if (DBG) log("isCallerOnDifferentThread: " + value +",Thread.currentThread(): "+Thread.currentThread()+",getLooper().getThread(): "+getLooper().getThread());

        return value;
    }
}
