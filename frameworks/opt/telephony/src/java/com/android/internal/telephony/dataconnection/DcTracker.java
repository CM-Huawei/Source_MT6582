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

package com.android.internal.telephony.dataconnection;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.ActivityNotFoundException;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.ConnectivityManager;
import android.net.LinkAddress;
import android.net.LinkCapabilities;
import android.net.LinkProperties;
import android.net.LinkProperties.CompareResult;
import android.net.wifi.WifiManager;
import android.net.NetworkConfig;
import android.net.NetworkUtils;
import android.net.ProxyProperties;
import android.net.TrafficStats;
import android.net.Uri;
import android.os.AsyncResult;
import android.os.Build;
import android.os.Message;
import android.os.ServiceManager;
import android.os.Messenger;
import android.os.SystemClock;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.provider.Settings;
import android.provider.Telephony;
import android.telephony.CellLocation;
import android.telephony.ServiceState;
import android.telephony.TelephonyManager;
import android.telephony.cdma.CdmaCellLocation;
import android.telephony.gsm.GsmCellLocation;
import com.android.internal.telephony.gsm.GsmCallTracker;
import android.text.TextUtils;
import android.util.EventLog;
import android.telephony.Rlog;

import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneBase;
import com.android.internal.telephony.DctConstants;
import com.android.internal.telephony.DctConstants.State;
import com.android.internal.telephony.EventLogTags;
import com.android.internal.telephony.TelephonyIntents;
import com.android.internal.telephony.IccCard;
import com.android.internal.telephony.ITelephony;
import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.RILConstants;
import com.android.internal.telephony.RetryManager;
import com.android.internal.telephony.gsm.GSMPhone;
import com.android.internal.telephony.gsm.GsmServiceStateTracker;
import com.android.internal.telephony.uicc.UiccCard;
import com.android.internal.telephony.uicc.IccRecords;
import com.android.internal.telephony.uicc.UiccController;
import com.android.internal.util.AsyncChannel;

//[New R8 modem FD]
import com.android.internal.telephony.gsm.FDModeType;
import com.android.internal.telephony.gsm.FDTimer;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Collection;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

//MTK-START [mtk04070][111205][ALPS00093395]MTK added
import com.android.internal.telephony.gemini.GeminiNetworkSubUtil;
import com.android.internal.telephony.gemini.GeminiPhone;
import android.content.BroadcastReceiver;
import com.mediatek.common.featureoption.FeatureOption;
import static android.provider.Settings.System.GPRS_CONNECTION_SETTING;
import static android.provider.Settings.System.GPRS_CONNECTION_SETTING_DEFAULT;
import android.provider.Telephony.SIMInfo;
import com.android.internal.telephony.PhoneFactory;
import com.mediatek.common.MediatekClassFactory;
import com.mediatek.common.telephony.ITetheringExt;
import com.mediatek.common.telephony.IApnSetting;
import com.mediatek.common.telephony.IGsmDCTExt;
//MTK-END [mtk04070][111205][ALPS00093395]MTK added


/**
 * {@hide}
 */
public class DcTracker extends DcTrackerBase {
    protected final String LOG_TAG = "DCT";

    private static final int MSG_RESTART_RADIO_OFF_DONE = 999;
    private static final int MSG_RESTART_RADIO_ON_DONE = 998;
    /**
     * Handles changes to the APN db.
     */
    private class ApnChangeObserver extends ContentObserver {
        public ApnChangeObserver () {
            super(mDataConnectionTracker);
        }

        @Override
        public void onChange(boolean selfChange) {
            sendMessage(obtainMessage(DctConstants.EVENT_APN_CHANGED));
        }
    }

    //***** Instance Variables

    private boolean mReregisterOnReconnectFailure = false;


    //***** Constants

    // Used by puppetmaster/*/radio_stress.py
    private static final String PUPPET_MASTER_RADIO_STRESS_TEST = "gsm.defaultpdpcontext.active";

    private static final int POLL_PDP_MILLIS = 5 * 1000;

    static final Uri PREFERAPN_NO_UPDATE_URI =
                        Uri.parse("content://telephony/carriers/preferapn_no_update");
    static final String APN_ID = "apn_id";

    private boolean mCanSetPreferApn = false;
    private AtomicBoolean mAttached = new AtomicBoolean(false);//[KK]
    
    //MTK-BEGIN
    private ApnSetting mPreferredTetheringApn = null;
    private boolean canSetPreferTetheringApn = false;
    private ArrayList<String> mWaitingApnList = new ArrayList<String>();
    private ArrayList<String> mWaitingNonDefaultApn = new ArrayList<String>();
    private int mLogSimId = 0;
    static final Uri CONTENT_URI_GEMINI[]
            = {Telephony.Carriers.SIM1Carriers.CONTENT_URI,
            Telephony.Carriers.SIM2Carriers.CONTENT_URI,
            Telephony.Carriers.SIM3Carriers.CONTENT_URI,
            Telephony.Carriers.SIM4Carriers.CONTENT_URI};
    static final Uri PREFERAPN_NO_UPDATE_URI_GEMINI[] 
            = {Uri.parse("content://telephony/carriers_sim1/preferapn_no_update"),
            Uri.parse("content://telephony/carriers_sim2/preferapn_no_update"),
            Uri.parse("content://telephony/carriers_sim3/preferapn_no_update"),
            Uri.parse("content://telephony/carriers_sim4/preferapn_no_update")};
    static final String PROPERTY_AVAILABLE_CID = "ril.cid.num";
    //MTK-END

    private static final boolean DATA_STALL_SUSPECTED = true;
    private static final boolean DATA_STALL_NOT_SUSPECTED = false;

    public static final String PROPERTY_RIL_TEST_SIM[] = {
        "gsm.sim.ril.testsim",
        "gsm.sim.ril.testsim.2",
        "gsm.sim.ril.testsim.3",
        "gsm.sim.ril.testsim.4",
    };

    /** Watches for changes to the APN db. */
    private ApnChangeObserver mApnObserver;

    //MTK-START [mtk04070][111205][ALPS00093395]MTK added
    private GSMPhone mGsmPhone;
    /// M: SCRI and Fast Dormancy Feature @}
    //Add for SCRI, Fast Dormancy
    private ScriManager mScriManager;
    protected boolean scriPollEnabled = false;
    protected long scriTxPkts=0, scriRxPkts=0;
    //[New R8 modem FD]
    protected FDTimer mFDTimer;

    private boolean mIsUmtsMode = false;
    private boolean mIsCallPrefer = false;

    //[ALPS00098656][mtk04070]Disable Fast Dormancy when in Tethered mode
    private boolean mIsTetheredMode = false;
    /// @}
    //MTK-END [mtk04070][111205][ALPS00093395]MTK added

    private static final int PDP_CONNECTION_POOL_SIZE = (FeatureOption.PURE_AP_USE_EXTERNAL_MODEM)? 2 : 3;

    private ITetheringExt mTetheringExt;
    private IGsmDCTExt mGsmDCTExt;

    //MTK: for prevent onApnChanged and onRecordLoaded happened at the same time
    private Integer mCreateApnLock = new Integer(0);

    //***** Constructor

    public DcTracker(PhoneBase p) {
        super(p);
        if (DBG) log("GsmDCT.constructor");
        //MTK-START [mtk04070][111205][ALPS00093395]MTK added
        if (p.getPhoneType() == PhoneConstants.PHONE_TYPE_GSM){   
            mGsmPhone = (GSMPhone)p;
        }
        mLogSimId = mPhone.getMySimId(); //sync with DcTrackerBase for log (same as GSMSST too)           
        //MTK-END [mtk04070][111205][ALPS00093395]MTK added

        p.mCi.registerForAvailable (this, DctConstants.EVENT_RADIO_AVAILABLE, null);
        p.mCi.registerForOffOrNotAvailable(this, DctConstants.EVENT_RADIO_OFF_OR_NOT_AVAILABLE, null);
        p.mCi.registerForDataNetworkStateChanged (this, DctConstants.EVENT_DATA_STATE_CHANGED, null);
        p.getCallTracker().registerForVoiceCallEnded (this, DctConstants.EVENT_VOICE_CALL_ENDED, null);
        p.getCallTracker().registerForVoiceCallStarted (this, DctConstants.EVENT_VOICE_CALL_STARTED, null);
        p.getServiceStateTracker().registerForDataConnectionAttached(this,
                DctConstants.EVENT_DATA_CONNECTION_ATTACHED, null);
        p.getServiceStateTracker().registerForDataConnectionDetached(this,
                DctConstants.EVENT_DATA_CONNECTION_DETACHED, null);
        p.getServiceStateTracker().registerForRoamingOn(this, DctConstants.EVENT_ROAMING_ON, null);
        p.getServiceStateTracker().registerForRoamingOff(this, DctConstants.EVENT_ROAMING_OFF, null);
        p.getServiceStateTracker().registerForPsRestrictedEnabled(this,
                DctConstants.EVENT_PS_RESTRICT_ENABLED, null);
        p.getServiceStateTracker().registerForPsRestrictedDisabled(this,
                DctConstants.EVENT_PS_RESTRICT_DISABLED, null);      

        p.mCi.registerForGetAvailableNetworksDone(this, DctConstants.EVENT_GET_AVAILABLE_NETWORK_DONE, null);
        //[RB release workaround]
        p.mCi.setOnPacketsFlushNotification(this, DctConstants.EVENT_SET_PACKETS_FLUSH, null);
        //[RB release workaround]
        p.mCi.registerForRacUpdate(this, DctConstants.EVENT_RAC_UPDATED, null);

        if (p.getPhoneType() == PhoneConstants.PHONE_TYPE_GSM){                    
            ((GsmServiceStateTracker)mGsmPhone.getServiceStateTracker()).registerForPsRegistrants(this, 
                    DctConstants.EVENT_PS_RAT_CHANGED, null);
            //mGsmPhone.mSST.registerForPsRegistrants(this, 
            //        DctConstants.EVENT_PS_RAT_CHANGED, null);

            /// M: SCRI and Fast Dormancy Feature @{
            //MTK-START [mtk04070][111205][ALPS00093395]MTK added
            //Register for handling SCRI events
            registerSCRIEvent(mGsmPhone);
            //MTK-END [mtk04070][111205][ALPS00093395]MTK added
            /// @}
        }
        

        //[New R8 modem FD]
        mFDTimer = new FDTimer(p);
        

        mDataConnectionTracker = this;

        mApnObserver = new ApnChangeObserver();
        if (FeatureOption.MTK_GEMINI_SUPPORT) {
            Uri geminiUri = CONTENT_URI_GEMINI[mPhone.getMySimId()];
            p.getContext().getContentResolver().registerContentObserver(geminiUri, true, mApnObserver);
        } else {
            p.getContext().getContentResolver().registerContentObserver(
                Telephony.Carriers.CONTENT_URI, true, mApnObserver);
        }

        // MTK START
        try{
            mTetheringExt = MediatekClassFactory.createInstance(ITetheringExt.class, p.getContext(), mPhone.getMySimId());
            mGsmDCTExt = MediatekClassFactory.createInstance(IGsmDCTExt.class, p.getContext());
        } catch (Exception e){
            e.printStackTrace();
        }
        // MTK END

        initApnContexts();//[KK] rename
        //[mr2] TODO: checked! mr2 reserve this part, but removed DcTracker broadcast part, so we reserve it temporary
        broadcastMessenger();


        //[mr2] added start
        for (ApnContext apnContext : mApnContexts.values()) {
            // Register the reconnect and restart actions.
            IntentFilter filter = new IntentFilter();
            filter.addAction(INTENT_RECONNECT_ALARM + '.' + apnContext.getApnType());
            filter.addAction(INTENT_RESTART_TRYSETUP_ALARM + '.' + apnContext.getApnType());
            mPhone.getContext().registerReceiver(mIntentReceiver, filter, null, mPhone);
        }

        ConnectivityManager cm = (ConnectivityManager)p.getContext().getSystemService(
                Context.CONNECTIVITY_SERVICE);
        cm.supplyMessenger(ConnectivityManager.TYPE_MOBILE, new Messenger(this));
        cm.supplyMessenger(ConnectivityManager.TYPE_MOBILE_MMS, new Messenger(this));
        cm.supplyMessenger(ConnectivityManager.TYPE_MOBILE_SUPL, new Messenger(this));
        cm.supplyMessenger(ConnectivityManager.TYPE_MOBILE_DUN, new Messenger(this));
        cm.supplyMessenger(ConnectivityManager.TYPE_MOBILE_HIPRI, new Messenger(this));
        cm.supplyMessenger(ConnectivityManager.TYPE_MOBILE_FOTA, new Messenger(this));
        cm.supplyMessenger(ConnectivityManager.TYPE_MOBILE_IMS, new Messenger(this));
        cm.supplyMessenger(ConnectivityManager.TYPE_MOBILE_CBS, new Messenger(this));
        //MTK: may be need to add other types
        //cm.supplyMessenger(ConnectivityManager.TYPE_MOBILE_DM, new Messenger(this));
        //cm.supplyMessenger(ConnectivityManager.TYPE_MOBILE_NET, new Messenger(this));
        //cm.supplyMessenger(ConnectivityManager.TYPE_MOBILE_WAP, new Messenger(this));
        //cm.supplyMessenger(ConnectivityManager.TYPE_MOBILE_CMMAIL, new Messenger(this));
        //cm.supplyMessenger(ConnectivityManager.TYPE_MOBILE_RCSE, new Messenger(this));                
        //[mr2] added end

    }

    @Override
    public void dispose() {
        if (DBG) log("GsmDCT.dispose");
        cleanUpAllConnections(true, null);

        super.dispose();

        //Unregister for all events
        mPhone.mCi.unregisterForAvailable(this);
        mPhone.mCi.unregisterForOffOrNotAvailable(this);
        IccRecords r = mIccRecords.get();
        if (r != null) { r.unregisterForRecordsLoaded(this);}
        mPhone.mCi.unregisterForDataNetworkStateChanged(this);
        mPhone.getCallTracker().unregisterForVoiceCallEnded(this);
        mPhone.getCallTracker().unregisterForVoiceCallStarted(this);
        mPhone.getServiceStateTracker().unregisterForDataConnectionAttached(this);
        mPhone.getServiceStateTracker().unregisterForDataConnectionDetached(this);
        mPhone.getServiceStateTracker().unregisterForRoamingOn(this);
        mPhone.getServiceStateTracker().unregisterForRoamingOff(this);
        mPhone.getServiceStateTracker().unregisterForPsRestrictedEnabled(this);
        mPhone.getServiceStateTracker().unregisterForPsRestrictedDisabled(this);

        if (mPhone.getPhoneType() == PhoneConstants.PHONE_TYPE_GSM){     
            
            ((GsmServiceStateTracker)mGsmPhone.getServiceStateTracker()).unregisterForPsRegistrants(this);
            //mGsmPhone.mSST.unregisterForPsRegistrants(this);
            for (int i=0; i<PhoneConstants.GEMINI_SIM_NUM; i++) {
                if (i != mGsmPhone.getMySimId() && mGsmPhone.getPeerPhones(i) != null) {
                    mGsmPhone.getPeerPhones(i).getCallTracker().unregisterForVoiceCallEnded(this);
                    mGsmPhone.getPeerPhones(i).getCallTracker().unregisterForVoiceCallStarted(this);
                }
            }


        }

        mPhone.getContext().getContentResolver().unregisterContentObserver(this.mApnObserver);
        mApnContexts.clear();
        mPrioritySortedApnContexts.clear();

        //MTK
        mPhone.mCi.unSetGprsDetach(this);
        mPhone.mCi.unregisterForGetAvailableNetworksDone(this);
        mPhone.mCi.unregisterForRacUpdate(this);

        destroyDataConnections();
    }

    @Override
    public boolean isApnTypeActive(String type) {
        ApnContext apnContext = mApnContexts.get(type);
        if (apnContext == null) return false;

        return (apnContext.getDcAc() != null);
    }

    @Override
    public boolean isDataPossible(String apnType) {
        ApnContext apnContext = mApnContexts.get(apnType);
        if (apnContext == null) {
            return false;
        }
        boolean apnContextIsEnabled = apnContext.isEnabled();
        DctConstants.State apnContextState = apnContext.getState();
        boolean apnTypePossible = !(apnContextIsEnabled &&
                (apnContextState == DctConstants.State.FAILED));
        boolean dataAllowed = isDataAllowed();
        boolean possible = dataAllowed && apnTypePossible;

        if (VDBG) {
            log(String.format("isDataPossible(%s): possible=%b isDataAllowed=%b " +
                    "apnTypePossible=%b apnContextisEnabled=%b apnContextState()=%s",
                    apnType, possible, dataAllowed, apnTypePossible,
                    apnContextIsEnabled, apnContextState));
        }
        return possible;
    }

    @Override
    protected void finalize() {
        if(DBG) log("finalize");
    }

    //[KK] Modified
    private ApnContext addApnContext(String type, NetworkConfig networkConfig) {
        ApnContext apnContext = new ApnContext(mPhone.getContext(), type, LOG_TAG, networkConfig);
        apnContext.setDependencyMet(networkConfig.dependencyMet);
        //private ApnContext addApnContext(String type) {
        //    ApnContext apnContext = new ApnContext(type, LOG_TAG);
        //    apnContext.setDependencyMet(false);        
        mApnContexts.put(type, apnContext);
        mPrioritySortedApnContexts.add(apnContext);
        return apnContext;
    }

    protected void initApnContexts() {
        log("initApnContexts: E");
        boolean defaultEnabled = SystemProperties.getBoolean(DEFALUT_DATA_ON_BOOT_PROP, true);
        // Load device network attributes from resources
        String[] networkConfigStrings = mPhone.getContext().getResources().getStringArray(
                com.android.internal.R.array.networkAttributes);
        for (String networkConfigString : networkConfigStrings) {
            NetworkConfig networkConfig = new NetworkConfig(networkConfigString);
            ApnContext apnContext = null;

            switch (networkConfig.type) {
            case ConnectivityManager.TYPE_MOBILE:
                apnContext = addApnContext(PhoneConstants.APN_TYPE_DEFAULT, networkConfig);
                apnContext.setEnabled(defaultEnabled);
                break;
            case ConnectivityManager.TYPE_MOBILE_MMS:
                apnContext = addApnContext(PhoneConstants.APN_TYPE_MMS, networkConfig);
                break;
            case ConnectivityManager.TYPE_MOBILE_SUPL:
                apnContext = addApnContext(PhoneConstants.APN_TYPE_SUPL, networkConfig);
                break;
            case ConnectivityManager.TYPE_MOBILE_DUN:
                apnContext = addApnContext(PhoneConstants.APN_TYPE_DUN, networkConfig);
                break;
            case ConnectivityManager.TYPE_MOBILE_HIPRI:
                apnContext = addApnContext(PhoneConstants.APN_TYPE_HIPRI, networkConfig);
                /* [KK] Removed
                ApnContext defaultContext = mApnContexts.get(PhoneConstants.APN_TYPE_DEFAULT);
                if (defaultContext != null) {
                    applyNewState(apnContext, apnContext.isEnabled(),
                            defaultContext.getDependencyMet());
                } else {
                    // the default will set the hipri dep-met when it is created
                }
                continue;*/
                break;
            case ConnectivityManager.TYPE_MOBILE_FOTA:
                apnContext = addApnContext(PhoneConstants.APN_TYPE_FOTA, networkConfig);
                break;
            case ConnectivityManager.TYPE_MOBILE_IMS:
                apnContext = addApnContext(PhoneConstants.APN_TYPE_IMS, networkConfig);
                break;
            case ConnectivityManager.TYPE_MOBILE_CBS:
                apnContext = addApnContext(PhoneConstants.APN_TYPE_CBS, networkConfig);
                break;
            case ConnectivityManager.TYPE_MOBILE_IA:
                apnContext = addApnContext(PhoneConstants.APN_TYPE_IA, networkConfig);
                break; //[KK] added
            case ConnectivityManager.TYPE_MOBILE_DM:
                apnContext = addApnContext(PhoneConstants.APN_TYPE_DM, networkConfig);
                break;
            case ConnectivityManager.TYPE_MOBILE_NET:
                apnContext = addApnContext(PhoneConstants.APN_TYPE_NET, networkConfig);
                break;
            case ConnectivityManager.TYPE_MOBILE_WAP:
                apnContext = addApnContext(PhoneConstants.APN_TYPE_WAP, networkConfig);
                break;
            case ConnectivityManager.TYPE_MOBILE_CMMAIL:
                apnContext = addApnContext(PhoneConstants.APN_TYPE_CMMAIL, networkConfig);
                break;
            case ConnectivityManager.TYPE_MOBILE_RCSE:
                apnContext = addApnContext(PhoneConstants.APN_TYPE_RCSE, networkConfig);
                break;
            default:
                log("initApnContexts: skipping unknown type=" + networkConfig.type);
                continue;
            }
            log("initApnContexts: apnContext=" + apnContext);
            /*[KK] removed
            if (apnContext != null) {
                // set the prop, but also apply the newly set enabled and dependency values
                onSetDependencyMet(apnContext.getApnType(), networkConfig.dependencyMet);
            }*/
        }
        log("initApnContexts: X mApnContexts=" + mApnContexts);        
    }

    @Override
    public LinkProperties getLinkProperties(String apnType) {
        ApnContext apnContext = mApnContexts.get(apnType);
        if (apnContext != null) {
            DcAsyncChannel dcac = apnContext.getDcAc();
            if (dcac != null) {
                if (DBG) log("return link properites for " + apnType);
                return dcac.getLinkPropertiesSync();
            }
        }
        if (DBG) log("return new LinkProperties");
        return new LinkProperties();
    }

    @Override
    public LinkCapabilities getLinkCapabilities(String apnType) {
        ApnContext apnContext = mApnContexts.get(apnType);
        if (apnContext!=null) {
            DcAsyncChannel dataConnectionAc = apnContext.getDcAc();
            if (dataConnectionAc != null) {
                if (DBG) log("get active pdp is not null, return link Capabilities for " + apnType);
                return dataConnectionAc.getLinkCapabilitiesSync();
            }
        }
        if (DBG) log("return new LinkCapabilities");
        return new LinkCapabilities();
    }

    @Override
    // Return all active apn types
    public String[] getActiveApnTypes() {
        if (DBG) log("get all active apn types");
        ArrayList<String> result = new ArrayList<String>();

        for (ApnContext apnContext : mApnContexts.values()) {
            if (mAttached.get() && apnContext.isReady()) {//[KK] TODO: check
            //if (apnContext.isReady()) {
                result.add(apnContext.getApnType());
            }
        }

        return result.toArray(new String[0]);
    }

    @Override
    // Return active apn of specific apn type
    public String getActiveApnString(String apnType) {
        if (VDBG) log( "get active apn string for type:" + apnType);
        ApnContext apnContext = mApnContexts.get(apnType);
        if (apnContext != null) {
            ApnSetting apnSetting = apnContext.getApnSetting();
            if (apnSetting != null) {
                return apnSetting.apn;
            }
        }
        return null;
    }

    @Override
    public boolean isApnTypeEnabled(String apnType) {
        ApnContext apnContext = mApnContexts.get(apnType);
        if (apnContext == null) {
            return false;
        }
        return apnContext.isEnabled();
    }

    @Override
    protected void setState(DctConstants.State s) {
        if (DBG) log("setState should not be used in GSM" + s);
    }

    // Return state of specific apn type
    @Override
    public DctConstants.State getState(String apnType) {
        ApnContext apnContext = mApnContexts.get(apnType);
        if (apnContext != null) {
            return apnContext.getState();
        }
        return DctConstants.State.FAILED;
    }

    //[KK] added : Return if apn type is a provisioning apn.
    @Override
    protected boolean isProvisioningApn(String apnType) {
        ApnContext apnContext = mApnContexts.get(apnType);
        if (apnContext != null) {
            return apnContext.isProvisioningApn();
        }
        return false;
    }


    // Return state of overall
    @Override
    public DctConstants.State getOverallState() {
        boolean isConnecting = false;
        boolean isFailed = true; // All enabled Apns should be FAILED.
        boolean isAnyEnabled = false;
        StringBuilder builder = new StringBuilder();
        for (ApnContext apnContext : mApnContexts.values()) {
            if (apnContext != null) {
                builder.append(apnContext.toString() + ", ");
            }
        }
        if (DBG) log( "overall state is " + builder);
        for (ApnContext apnContext : mApnContexts.values()) {
            if (apnContext.isEnabled()) {
                isAnyEnabled = true;
                switch (apnContext.getState()) {
                case CONNECTED:
                case DISCONNECTING:
                    if (DBG) log("overall state is CONNECTED");
                    return DctConstants.State.CONNECTED;
                case RETRYING:
                case CONNECTING:
                    isConnecting = true;
                    isFailed = false;
                    break;
                case IDLE:
                case SCANNING:
                    isFailed = false;
                    break;
                default:
                    isAnyEnabled = true;
                    break;
                }
            }
        }

        if (!isAnyEnabled) { // Nothing enabled. return IDLE.
            if (DBG) log( "overall state is IDLE");
            return DctConstants.State.IDLE;
        }

        if (isConnecting) {
            if (DBG) log( "overall state is CONNECTING");
            return DctConstants.State.CONNECTING;
        } else if (!isFailed) {
            if (DBG) log( "overall state is IDLE");
            return DctConstants.State.IDLE;
        } else {
            if (DBG) log( "overall state is FAILED");
            return DctConstants.State.FAILED;
        }
    }

    /**
     * Ensure that we are connected to an APN of the specified type.
     *
     * @param apnType the APN type
     * @return Success is indicated by {@code PhoneConstants.APN_ALREADY_ACTIVE} or
     *         {@code PhoneConstants.APN_REQUEST_STARTED}. In the latter case, a
     *         broadcast will be sent by the ConnectivityManager when a
     *         connection to the APN has been established.
     */
    @Override
    public synchronized int enableApnType(String apnType) {
        if (FeatureOption.MTK_GEMINI_SUPPORT) {
            int simId = mPhone.getMySimId();
            ConnectivityManager connectivityManager = (ConnectivityManager)mPhone.getContext().getSystemService(Context.CONNECTIVITY_SERVICE);
            if (connectivityManager != null && connectivityManager.getMobileDataEnabledGemini(simId)) {
                if (SystemProperties.getInt(PROPERTY_RIL_TEST_SIM[simId], 0) == 1) {
                    log("enableApnType SIM" + (simId+1) + " is a test SIM and data is enabled on it, do PS attach");
                    mPhone.mCi.setGprsConnType(GeminiNetworkSubUtil.CONN_TYPE_ALWAYS, null);
                } else {
                    log("enableApnType SIM" + (simId+1) + " is not a test SIM");
                }
            }
        }

        ApnContext apnContext = mApnContexts.get(apnType);
        if (apnContext == null || !isApnTypeAvailable(apnType)) {
            if (DBG) log("enableApnType: " + apnType + " is type not available");
            return PhoneConstants.APN_TYPE_NOT_AVAILABLE;
        }

        // If already active, return
        if (DBG) log("enableApnType: " + apnType + " mState(" + apnContext.getState() + ")");

        if (apnContext.getState() == DctConstants.State.CONNECTED) {
            if (DBG) log("enableApnType: return APN_ALREADY_ACTIVE");
            return PhoneConstants.APN_ALREADY_ACTIVE;
        }
        setEnabled(apnTypeToId(apnType), true);
        if (DBG) {
            log("enableApnType: new apn request for type " + apnType +
                    " return APN_REQUEST_STARTED");
        }
        return PhoneConstants.APN_REQUEST_STARTED;
    }

    @Override
    public synchronized int disableApnType(String type) {
        if (DBG) log("disableApnType:" + type);
        ApnContext apnContext = mApnContexts.get(type);

        if (apnContext != null) {
            setEnabled(apnTypeToId(type), false);
            if (apnContext.getState() != DctConstants.State.IDLE && apnContext.getState()
                    != DctConstants.State.FAILED) {
                if (DBG) log("diableApnType: return APN_REQUEST_STARTED");
                return PhoneConstants.APN_REQUEST_STARTED;
            } else {
                if (DBG) log("disableApnType: return APN_ALREADY_INACTIVE");
                return PhoneConstants.APN_ALREADY_INACTIVE;
            }

        } else {
            if (DBG) {
                log("disableApnType: no apn context was found, return APN_REQUEST_FAILED");
            }
            return PhoneConstants.APN_REQUEST_FAILED;
        }
    }

    @Override
    protected boolean isApnTypeAvailable(String type) {
        if (type.equals(PhoneConstants.APN_TYPE_DUN) && fetchDunApn() != null) {
            return true;
        }

        if (mAllApnSettings != null) {
            for (ApnSetting apn : mAllApnSettings) {
                if (apn.canHandleType(type)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Report on whether data connectivity is enabled for any APN.
     * @return {@code false} if data connectivity has been explicitly disabled,
     * {@code true} otherwise.
     */
    @Override
    public boolean getAnyDataEnabled() {
        synchronized (mDataEnabledLock) {
            mUserDataEnabled = Settings.Global.getInt(
                mPhone.getContext().getContentResolver(), Settings.Global.MOBILE_DATA, 1) == 1;

            if (!(mInternalDataEnabled && mUserDataEnabled && sPolicyDataEnabled)) return false;
            for (ApnContext apnContext : mApnContexts.values()) {
                // Make sure we don't have a context that is going down
                // and is explicitly disabled.
                if (isDataAllowed(apnContext)) {
                    return true;
                }
            }
            return false;
        }
    }

    private boolean isDataAllowed(ApnContext apnContext) {
        return apnContext.isReady() && isDataAllowed();
    }


    //[mr2] MTK
    public boolean isNotDefaultTypeDataEnabled() {
        synchronized (mDataEnabledLock) {
            if (!(mInternalDataEnabled && sPolicyDataEnabled))
                return false;
            for (ApnContext apnContext : mApnContexts.values()) {
                // Make sure we dont have a context that going down
                // and is explicitly disabled.
                if (!PhoneConstants.APN_TYPE_DEFAULT.equals(apnContext.getApnType()) && isDataAllowed(apnContext)) {
                    return true;
                }
            }
            return false;
        }
    }



    //****** Called from ServiceStateTracker
    /**
     * Invoked when ServiceStateTracker observes a transition from GPRS
     * attach to detach.
     */
    protected void onDataConnectionDetached() {
        /*
         * We presently believe it is unnecessary to tear down the PDP context
         * when GPRS detaches, but we should stop the network polling.
         */
        if (DBG) log ("onDataConnectionDetached: stop polling and notify detached");
        stopNetStatPoll();
        stopDataStallAlarm();
        notifyDataConnection(Phone.REASON_DATA_DETACHED);
        mAttached.set(false);//[KK]
        
        /// M: SCRI and Fast Dormancy Feature @{ 
        //MTK-START [mtk04070][111205][ALPS00093395]Stop SCRI polling
        /* Add by MTK03594 */
        if (DBG) log ("onDataConnectionDetached: stopScriPoll()");
        stopScriPoll();
        //MTK-END [mtk04070][111205][ALPS00093395]Stop SCRI polling
        /// @}

    }

    private void onDataConnectionAttached() {
        if (DBG) log("onDataConnectionAttached");
        mAttached.set(true);//[KK]
        if (getOverallState() == DctConstants.State.CONNECTED) {
            if (DBG) log("onDataConnectionAttached: start polling notify attached");
            startNetStatPoll();
            startDataStallAlarm(DATA_STALL_NOT_SUSPECTED);
            notifyDataConnection(Phone.REASON_DATA_ATTACHED);
        } else {
            // update APN availability so that APN can be enabled.
            notifyOffApnsOfAvailability(Phone.REASON_DATA_ATTACHED);
        }
        mAutoAttachOnCreation = true;
        setupDataOnConnectableApns(Phone.REASON_DATA_ATTACHED);

        
    }

    @Override
    protected boolean isDataAllowed() {
        final boolean internalDataEnabled;
        synchronized (mDataEnabledLock) {
            internalDataEnabled = mInternalDataEnabled;
        }

        //int gprsState = mPhone.getServiceStateTracker().getCurrentDataConnectionState();//[KK] removed
        boolean attachedState = mAttached.get();//[KK]
        boolean desiredPowerState = mPhone.getServiceStateTracker().getDesiredPowerState();
        IccRecords r = mIccRecords.get();
        boolean recordsLoaded = (r != null) ? r.getRecordsLoaded() : false;
        boolean isPeerPhoneIdle = true;
        if (FeatureOption.MTK_GEMINI_SUPPORT) {

            if (mPhone.getPhoneType() == PhoneConstants.PHONE_TYPE_GSM){
                for (int i=PhoneConstants.GEMINI_SIM_1; i<PhoneConstants.GEMINI_SIM_NUM; i++) {
                    if (i != mGsmPhone.getMySimId() && mGsmPhone.getPeerPhones(i) != null && 
                            mGsmPhone.getPeerPhones(i).getState() != PhoneConstants.State.IDLE) {
                        isPeerPhoneIdle = false;
                        break;
                    }
                }
            }
            
        }

        boolean allowed =
                    (attachedState || mAutoAttachOnCreation) &&
                    recordsLoaded &&
                    (mPhone.getState() == PhoneConstants.State.IDLE ||
                     mPhone.getServiceStateTracker().isConcurrentVoiceAndDataAllowed()) &&
                    internalDataEnabled &&
                    (!mPhone.getServiceState().getRoaming() || getDataOnRoamingEnabled()) &&
                    // TODO: confirm if need to modify it in Gemini+
                    // !mIsPsRestricted &&
                    desiredPowerState &&
                    !mPhone.mCi.isGettingAvailableNetworks() &&
                    isPeerPhoneIdle;
        if (!allowed && DBG) {
            String reason = "";
            if (!((attachedState) || mAutoAttachOnCreation)) {
                reason += " - attachedState= " + attachedState;
            }
            if (!recordsLoaded) reason += " - SIM not loaded";
            if (mPhone.getState() != PhoneConstants.State.IDLE &&
                    !mPhone.getServiceStateTracker().isConcurrentVoiceAndDataAllowed()) {
                reason += " - PhoneState= " + mPhone.getState();
                reason += " - Concurrent voice and data not allowed";
            }
            if (!internalDataEnabled) reason += " - mInternalDataEnabled= false";
            if (mPhone.getServiceState().getRoaming() && !getDataOnRoamingEnabled()) {
                reason += " - Roaming and data roaming not enabled";
            }
            if (mIsPsRestricted) reason += " - mIsPsRestricted= true";
            if (!desiredPowerState) reason += " - desiredPowerState= false";
            if (mPhone.mCi.isGettingAvailableNetworks()) reason += " - querying available network";
            if (!isPeerPhoneIdle) reason += " - Peer phone is not IDLE";
            if (DBG) log("isDataAllowed: not allowed due to" + reason);
        }
        return allowed;
    }


    private void setupDataOnConnectableApns(String reason) {
        if (DBG) log("setupDataOnConnectableApns: " + reason);

        for (ApnContext apnContext : mPrioritySortedApnContexts) {
            if (DBG) log("setupDataOnConnectableApns: apnContext " + apnContext);
            if (apnContext.getState() == DctConstants.State.FAILED) {
                apnContext.setState(DctConstants.State.IDLE);
            }
            if (apnContext.isConnectable()) {
                log("setupDataOnConnectableApns: isConnectable() call trySetupData");
                apnContext.setReason(reason);
                trySetupData(apnContext);
            }
        }
    }

    private boolean trySetupData(String reason, String type) {
        if (DBG) {
            log("trySetupData: " + type + " due to " + (reason == null ? "(unspecified)" : reason)
                    + " isPsRestricted=" + mIsPsRestricted);
        }

        if (type == null) {
            type = PhoneConstants.APN_TYPE_DEFAULT;
        }

        ApnContext apnContext = mApnContexts.get(type);

        if (apnContext == null ){
            if (DBG) log("trySetupData new apn context for type:" + type);
            apnContext = new ApnContext(mPhone.getContext(), type, LOG_TAG);//[KK]
            mApnContexts.put(type, apnContext);
        }
        apnContext.setReason(reason);

        return trySetupData(apnContext);
    }

    private boolean trySetupData(ApnContext apnContext) {
        String apnType = apnContext.getApnType();
        if (DBG) {
            log("trySetupData for type:" + apnType +
                    " due to " + apnContext.getReason());
            log("trySetupData with mIsPsRestricted=" + mIsPsRestricted);
        }

        if (mPhone.getSimulatedRadioControl() != null) {
            // Assume data is connected on the simulator
            // FIXME  this can be improved
            apnContext.setState(DctConstants.State.CONNECTED);
            mPhone.notifyDataConnection(apnContext.getReason(), apnType);

            log("trySetupData: (fix?) We're on the simulator; assuming data is connected");
            return true;
        }
        //MTK begin
        if(FeatureOption.MTK_GEMINI_SUPPORT && PhoneConstants.APN_TYPE_DEFAULT.equals(apnType)){
            int gprsDefaultSIM = getDataConnectionFromSetting();
            GeminiPhone mGeminiPhone = (GeminiPhone)PhoneFactory.getDefaultPhone();

            logd("gprsDefaultSIM:" + gprsDefaultSIM);
            if(gprsDefaultSIM != mPhone.getMySimId()){
                  logd("The setting is off(1)");
                  return false;
            }else if(gprsDefaultSIM < 0){
               logd("The setting is off(2)");
               return false;
            } else if ( mGeminiPhone != null && mGeminiPhone.isGprsDetachingOrDetached(mPhone.getMySimId()) &&
                    !TextUtils.equals(apnContext.getReason(), Phone.REASON_DATA_ATTACHED)) {
                logd("trySetupData: detaching or detached state.");
                return false;
            }
         }
        //MTK end

        if (apnContext.getState() == DctConstants.State.DISCONNECTING) {
            if (DBG) logd("trySetupData:" + apnContext.getApnType() + " is DISCONNECTING, trun on reactive flag.");
            apnContext.setReactive(true);
        }

        boolean desiredPowerState = mPhone.getServiceStateTracker().getDesiredPowerState();
        boolean anyDataEnabled = (FeatureOption.MTK_BSP_PACKAGE || 
                !isDataAllowedAsOff(apnType))? getAnyDataEnabled() : isNotDefaultTypeDataEnabled();

        if (apnContext.isConnectable() &&
                isDataAllowed(apnContext) && anyDataEnabled && !isEmergency()) {
            if (apnContext.getState() == DctConstants.State.FAILED) {
                if (DBG) log("trySetupData: make a FAILED ApnContext IDLE so its reusable");
                apnContext.setState(DctConstants.State.IDLE);
            }
            int radioTech = mPhone.getServiceState().getRilDataRadioTechnology();//[KK]
            if (apnContext.getState() == DctConstants.State.IDLE) {
                //ArrayList<ApnSetting> waitingApns = buildWaitingApns(apnContext.getApnType());
                ArrayList<ApnSetting> waitingApns = buildWaitingApns(apnContext.getApnType(),
                                        radioTech); //[KK]
                if (waitingApns.isEmpty()) {
                    if (DBG) log("trySetupData: No APN found");
                    notifyNoData(DcFailCause.MISSING_UNKNOWN_APN, apnContext);
                    notifyOffApnsOfAvailability(apnContext.getReason());
                    if (DBG) log("trySetupData: X No APN found retValue=false");
                    return false;
                } else {
                    apnContext.setWaitingApns(waitingApns);
                    if (DBG) {
                        log ("trySetupData: Create from mAllApnSettings : "
                                    + apnListToString(mAllApnSettings));
                    }
                }
            }

            if (DBG) {
                log("trySetupData: call setupData, waitingApns : "
                        + apnListToString(apnContext.getWaitingApns()));
            }
            // apnContext.setReason(apnContext.getReason());
            //boolean retValue = setupData(apnContext);
            boolean retValue = setupData(apnContext, radioTech);//[KK]
            notifyOffApnsOfAvailability(apnContext.getReason());

            if (DBG) log("trySetupData: X retValue=" + retValue);
            return retValue;
        } else {
            // TODO: check the condition.
            if (DBG) log ("try setup data but not executed [" + mInternalDataEnabled + "," + mUserDataEnabled + "," + sPolicyDataEnabled + "]");
            if (!apnContext.getApnType().equals(PhoneConstants.APN_TYPE_DEFAULT)
                    && apnContext.isConnectable()) {
                mPhone.notifyDataConnectionFailed(apnContext.getReason(), apnContext.getApnType());
            }
            notifyOffApnsOfAvailability(apnContext.getReason());
            if (DBG) log ("trySetupData: X apnContext not 'ready' retValue=false");
            return false;
        }
    }

    @Override
    // Disabled apn's still need avail/unavail notificiations - send them out
    protected void notifyOffApnsOfAvailability(String reason) {
        for (ApnContext apnContext : mApnContexts.values()) {
            if (!mAttached.get() || !apnContext.isReady()) {//[KK]
            //if (!apnContext.isReady()) {             
                if (VDBG) log("notifyOffApnOfAvailability type:" + apnContext.getApnType());
                mPhone.notifyDataConnection(reason != null ? reason : apnContext.getReason(),
                                            apnContext.getApnType(),
                                            PhoneConstants.DataState.DISCONNECTED);
            } else {
                if (VDBG) {
                    log("notifyOffApnsOfAvailability skipped apn due to attached && isReady " +
                            apnContext.toString());
                }
            }
        }
    }

    /**
     * If tearDown is true, this only tears down a CONNECTED session. Presently,
     * there is no mechanism for abandoning an CONNECTING session,
     * but would likely involve cancelling pending async requests or
     * setting a flag or new state to ignore them when they came in
     * @param tearDown true if the underlying DataConnection should be
     * disconnected.
     * @param reason reason for the clean up.
     * @return boolean - true if we did cleanup any connections, false if they
     *                   were already all disconnected.
     */
    protected boolean cleanUpAllConnections(boolean tearDown, String reason) {
        if (DBG) log("cleanUpAllConnections: tearDown=" + tearDown + " reason=" + reason);
        boolean didDisconnect = false;

        for (ApnContext apnContext : mApnContexts.values()) {
            if (apnContext.isDisconnected() == false) didDisconnect = true;
            // TODO - only do cleanup if not disconnected
            apnContext.setReason(reason);
            cleanUpConnection(tearDown, apnContext);
        }

        stopNetStatPoll();
        stopDataStallAlarm();

        // TODO: Do we need mRequestedApnType?
        mRequestedApnType = PhoneConstants.APN_TYPE_DEFAULT;
        return didDisconnect;
    }

    /**
     * Cleanup all connections.
     *
     * TODO: Cleanup only a specified connection passed as a parameter.
     *       Also, make sure when you clean up a conn, if it is last apply
     *       logic as though it is cleanupAllConnections
     *
     * @param cause for the clean up.
     */

    @Override
    protected void onCleanUpAllConnections(String cause) {
        cleanUpAllConnections(true, cause);
    }

    private void cleanUpConnection(boolean tearDown, ApnContext apnContext) {

        if (apnContext == null) {
            if (DBG) log("cleanUpConnection: apn context is null");
            return;
        }

        DcAsyncChannel dcac = apnContext.getDcAc();
        if (DBG) {
            log("cleanUpConnection: E tearDown=" + tearDown + " reason=" + apnContext.getReason() +
                    " apnContext=" + apnContext);
        }
        if (tearDown) {
            if (apnContext.isDisconnected()) {
                // The request is tearDown and but ApnContext is not connected.
                // If apnContext is not enabled anymore, break the linkage to the DCAC/DC.
                apnContext.setState(DctConstants.State.IDLE);
                if (!apnContext.isReady()) {
                    if (dcac != null) {
                        dcac.tearDown(apnContext, "", null);
                    }
                    apnContext.setDataConnectionAc(null);
                }
                
                //[mr2] MTK added
                // If original state is FAILED, we should notify data possible again since data is disabled.
                mPhone.notifyDataConnection(apnContext.getReason(), apnContext.getApnType());
            } else {
                // Connection is still there. Try to clean up.
                if (dcac != null) {
                    if (apnContext.getState() != DctConstants.State.DISCONNECTING) {
                        boolean disconnectAll = false;
                        if (PhoneConstants.APN_TYPE_DUN.equals(apnContext.getApnType())) {
                            ApnSetting dunSetting = fetchDunApn();
                            if (dunSetting != null &&
                                    dunSetting.equals(apnContext.getApnSetting())) {
                                if (DBG) log("tearing down dedicated DUN connection");
                                // we need to tear it down - we brought it up just for dun and
                                // other people are camped on it and now dun is done.  We need
                                // to stop using it and let the normal apn list get used to find
                                // connections for the remaining desired connections
                                disconnectAll = true;
                            }
                        }
                        if (DBG) {
                            log("cleanUpConnection: tearing down" + (disconnectAll ? " all" :""));
                        }
                        Message msg = obtainMessage(DctConstants.EVENT_DISCONNECT_DONE, apnContext);
                        if (disconnectAll) {
                            apnContext.getDcAc().tearDownAll(apnContext.getReason(), msg);
                        } else {
                            apnContext.getDcAc()
                                .tearDown(apnContext, apnContext.getReason(), msg);
                        }
                        apnContext.setState(DctConstants.State.DISCONNECTING);
                    }
                } else {
                    // apn is connected but no reference to dcac.
                    // Should not be happen, but reset the state in case.
                    apnContext.setState(DctConstants.State.IDLE);
                    mPhone.notifyDataConnection(apnContext.getReason(),
                                                apnContext.getApnType());
                }
            }
        } else {
            // force clean up the data connection.
            if (dcac != null) dcac.reqReset();
            apnContext.setState(DctConstants.State.IDLE);
            mPhone.notifyDataConnection(apnContext.getReason(), apnContext.getApnType());
            apnContext.setDataConnectionAc(null);
        }

        // Make sure reconnection alarm is cleaned up if there is no ApnContext
        // associated to the connection.
        if (dcac != null) {
            //[mr2]
            cancelReconnectAlarm(apnContext);
        }
        if (DBG) {
            log("cleanUpConnection: X tearDown=" + tearDown + " reason=" + apnContext.getReason() +
                    " apnContext=" + apnContext + " dcac=" + apnContext.getDcAc());
        }
    }

    /**
     * Cancels the alarm associated with apnContext.
     *
     * @param apnContext on which the alarm should be stopped.
     */
    private void cancelReconnectAlarm(ApnContext apnContext) {
        if (apnContext == null) return;

        PendingIntent intent = apnContext.getReconnectIntent();

        if (intent != null) {
                AlarmManager am =
                    (AlarmManager) mPhone.getContext().getSystemService(Context.ALARM_SERVICE);
                am.cancel(intent);
                apnContext.setReconnectIntent(null);
        }
    }

    /**
     * @param types comma delimited list of APN types
     * @return array of APN types
     */
    private String[] parseTypes(String types) {
        String[] result;
        // If unset, set to DEFAULT.
        if (types == null || types.equals("")) {
            result = new String[1];
            result[0] = PhoneConstants.APN_TYPE_ALL;
        } else {
            result = types.split(",");
        }
        return result;
    }

   //[mr2] Create ApnList start
   private boolean imsiMatches(String imsiDB, String imsiSIM) {
        // Note: imsiDB value has digit number or 'x' character for seperating USIM information
        // for MVNO operator. And then digit number is matched at same order and 'x' character
        // could replace by any digit number.
        // ex) if imsiDB inserted '310260x10xxxxxx' for GG Operator,
        //     that means first 6 digits, 8th and 9th digit
        //     should be set in USIM for GG Operator.
        int len = imsiDB.length();
        int idxCompare = 0;

        if (len <= 0) return false;
        if (len > imsiSIM.length()) return false;

        for (int idx=0; idx<len; idx++) {
            char c = imsiDB.charAt(idx);
            if ((c == 'x') || (c == 'X') || (c == imsiSIM.charAt(idx))) {
                continue;
            } else {
                return false;
            }
        }
        return true;
    }

    private boolean mvnoMatches(IccRecords r, String mvno_type, String mvno_match_data) {
        if (mvno_type.equalsIgnoreCase("spn")) {
            if ((r.getServiceProviderName() != null) &&
                    r.getServiceProviderName().equalsIgnoreCase(mvno_match_data)) {
                return true;
            }
        } else if (mvno_type.equalsIgnoreCase("imsi")) {
            String imsiSIM = r.getIMSI();
            if ((imsiSIM != null) && imsiMatches(mvno_match_data, imsiSIM)) {
                return true;
            }
        } else if (mvno_type.equalsIgnoreCase("gid")) {
            String gid1 = r.getGid1();
            //[KK] modified
            int mvno_match_data_length = mvno_match_data.length();
            if ((gid1 != null) && (gid1.length() >= mvno_match_data_length) &&
                    gid1.substring(0, mvno_match_data_length).equalsIgnoreCase(mvno_match_data)) {
                return true;
            }
        } else if (mvno_type.equalsIgnoreCase("pnn")) {
            String pnn = mPhone.getMvnoPattern(mvno_type);       
            if ((pnn != null) && pnn.equalsIgnoreCase(mvno_match_data)) {
                return true;
            }            
        }
        return false;
    }

    private ApnSetting makeApnSetting(Cursor cursor) {
        String[] types = parseTypes(
                cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Carriers.TYPE)));
        ApnSetting apn = new ApnSetting(
                cursor.getInt(cursor.getColumnIndexOrThrow(Telephony.Carriers._ID)),
                cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Carriers.NUMERIC)),
                cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Carriers.NAME)),
                cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Carriers.APN)),
                NetworkUtils.trimV4AddrZeros(
                        cursor.getString(
                        cursor.getColumnIndexOrThrow(Telephony.Carriers.PROXY))),
                cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Carriers.PORT)),
                NetworkUtils.trimV4AddrZeros(
                        cursor.getString(
                        cursor.getColumnIndexOrThrow(Telephony.Carriers.MMSC))),
                NetworkUtils.trimV4AddrZeros(
                        cursor.getString(
                        cursor.getColumnIndexOrThrow(Telephony.Carriers.MMSPROXY))),
                cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Carriers.MMSPORT)),
                cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Carriers.USER)),
                cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Carriers.PASSWORD)),
                cursor.getInt(cursor.getColumnIndexOrThrow(Telephony.Carriers.AUTH_TYPE)),
                types,
                cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Carriers.PROTOCOL)),
                cursor.getString(cursor.getColumnIndexOrThrow(
                        Telephony.Carriers.ROAMING_PROTOCOL)),
                cursor.getInt(cursor.getColumnIndexOrThrow(
                        Telephony.Carriers.CARRIER_ENABLED)) == 1,
                cursor.getInt(cursor.getColumnIndexOrThrow(Telephony.Carriers.BEARER)));
        return apn;
    }

    private ArrayList<ApnSetting> createApnList(Cursor cursor) {
        ArrayList<ApnSetting> result = new ArrayList<ApnSetting>();
        IccRecords r = mIccRecords.get();

        if (cursor.moveToFirst()) {
            String mvnoType = null;
            String mvnoMatchData = null;
            do {
                String cursorMvnoType = cursor.getString(
                        cursor.getColumnIndexOrThrow(Telephony.Carriers.MVNO_TYPE));
                String cursorMvnoMatchData = cursor.getString(
                        cursor.getColumnIndexOrThrow(Telephony.Carriers.MVNO_MATCH_DATA));
                if (mvnoType != null) {
                    if (mvnoType.equals(cursorMvnoType) &&
                            mvnoMatchData.equals(cursorMvnoMatchData)) {
                        result.add(makeApnSetting(cursor));
                    }
                } else {
                    // no mvno match yet
                    if (mvnoMatches(r, cursorMvnoType, cursorMvnoMatchData)) {
                        // first match - toss out non-mvno data
                        result.clear();
                        mvnoType = cursorMvnoType;
                        mvnoMatchData = cursorMvnoMatchData;
                        result.add(makeApnSetting(cursor));
                    } else {
                        // add only non-mvno data
                        if (cursorMvnoType.equals("")) {
                            result.add(makeApnSetting(cursor));
                        }
                    }
                }
            } while (cursor.moveToNext());
        }
        if (DBG) log("createApnList: X result=" + result);
        return result;
    }
   //[mr2] Create ApnList end

    private boolean dataConnectionNotInUse(DcAsyncChannel dcac) {
        if (DBG) log("dataConnectionNotInUse: check if dcac is inuse dcac=" + dcac);
        for (ApnContext apnContext : mApnContexts.values()) {
            if (apnContext.getDcAc() == dcac) {
                if (DBG) log("dataConnectionNotInUse: in use by apnContext=" + apnContext);
                return false;
            }
        }
        // TODO: Fix retry handling so free DataConnections have empty apnlists.
        // Probably move retry handling into DataConnections and reduce complexity
        // of DCT.
        if (DBG) log("dataConnectionNotInUse: tearDownAll");
        dcac.tearDownAll("No connection", null);
        if (DBG) log("dataConnectionNotInUse: not in use return true");
        return true;
    }

    private DcAsyncChannel findFreeDataConnection() {
        for (DcAsyncChannel dcac : mDataConnectionAcHashMap.values()) {
            if (dcac.isInactiveSync() && dataConnectionNotInUse(dcac)) {
                if (DBG) {
                    log("findFreeDataConnection: found free DataConnection=" +
                        " dcac=" + dcac);
                }
                return dcac;
            }
        }
        log("findFreeDataConnection: NO free DataConnection");
        return null;
    }

    //[mr2] TODO: check    
    protected DcAsyncChannel findReadyDataConnection(ApnSetting apn) {
        if (apn == null) {
            return null;
        }
        if (DBG) {
            log("findReadyDataConnection: apn string <" + apn + ">" +
                    " dcacs.size=" + mDataConnectionAcHashMap.size());
        }
        for (DcAsyncChannel dcac : mDataConnectionAcHashMap.values()) {
            ApnSetting apnSetting = dcac.getApnSettingSync();
            if (DBG) {
                log("findReadyDataConnection: dc apn string <" +
                         (apnSetting != null ? (apnSetting.toString()) : "null") + ">");
            }
            if ((apnSetting != null) && TextUtils.equals(apnSetting.toString(), apn.toString())) {
                //DataConnection dc = dcac.dataConnection;
                if (DBG) {
                    log("findReadyDataConnection: found ready DataConnection=" +
                        " dcac=" + dcac );
                }
                return dcac;
            }
        }
        return null;
    }    

    protected boolean onlySupportOnePdp() {
        return false;
    }

    private boolean setupData(ApnContext apnContext, int radioTech) {//[KK]
    //private boolean setupData(ApnContext apnContext) {
        if (DBG) log("setupData: apnContext=" + apnContext);
        ApnSetting apnSetting;
        DcAsyncChannel dcac;

        int profileId = getApnProfileID(apnContext.getApnType());
        apnSetting = apnContext.getNextWaitingApn();
        if (apnSetting == null) {
            if (DBG) log("setupData: return for no apn found!");
            return false;
        }

        //[mr2] MTK added
        if (getAvailableCidNum() < 1) {
            if (DBG) log("No available CID now.");
            if (!PhoneConstants.APN_TYPE_DEFAULT.equals(apnContext.getApnType())) {
                if (!mWaitingNonDefaultApn.contains(apnContext.getApnType())) {
                    mWaitingNonDefaultApn.add(apnContext.getApnType());
                }
                disableApnType(PhoneConstants.APN_TYPE_DEFAULT);
            }
            return false;
        }

        dcac = checkForCompatibleConnectedApnContext(apnContext);
        /*[Mr2] add
        if (dcac != null) {
            // Get the dcacApnSetting for the connection we want to share.
            ApnSetting dcacApnSetting = dcac.getApnSettingSync();
            if (dcacApnSetting != null) {
                // Setting is good, so use it.
                apnSetting = dcacApnSetting;
            }
        }*/
     
        // M: check if the dc's APN setting is prefered APN for default connection.
        if (dcac != null && PhoneConstants.APN_TYPE_DEFAULT.equals(apnContext.getApnType())) {
            ApnSetting dcApnSetting = dcac.getApnSettingSync();
            if (dcApnSetting != null && !dcApnSetting.apn.equals(apnSetting.apn)) {
                if (DBG) log("The existing DC is not using prefered APN.");
                dcac = null;
            }
        }

        if (dcac == null) {
            if (isOnlySingleDcAllowed(radioTech)) {
                if (isHigherPriorityApnContextActive(apnContext)) {
                    if (DBG) {
                        log("setupData: Higher priority ApnContext active.  Ignoring call");
                    }
                    return false;
                }

                // Only lower priority calls left.  Disconnect them all in this single PDP case
                // so that we can bring up the requested higher priority call (once we receive
                // repsonse for deactivate request for the calls we are about to disconnect
                if (cleanUpAllConnections(true, Phone.REASON_SINGLE_PDN_ARBITRATION)) {
                    // If any call actually requested to be disconnected, means we can't
                    // bring up this connection yet as we need to wait for those data calls
                    // to be disconnected.
                    if (DBG) log("setupData: Some calls are disconnecting first.  Wait and retry");
                    return false;
                }

                // No other calls are active, so proceed
                if (DBG) log("setupData: Single pdp. Continue setting up data call.");
            }    
                
            //[mr2] TODO: need to check
            //dcac = findReadyDataConnection(apnSetting);

            if (dcac == null) {
                if (DBG) log("setupData: No ready DataConnection found!");
                // TODO: When allocating you are mapping type to id. If more than 1 free,
                // then could findFreeDataConnection get the wrong one??
                dcac = findFreeDataConnection();

            }


            if (dcac == null) {
                dcac = createDataConnection();
            }

            if (dcac == null) {
                if (PhoneFactory.isDualTalkMode()
                        || onlySupportOnePdp()) {
                    //M: in dual-talk project, we only have single pdp ability.
                    if (apnContext.getApnType() == PhoneConstants.APN_TYPE_DEFAULT)
                    {
                        if (DBG) log("setupData: No free GsmDataConnection found!");
                        return false;
                    }
                    else
                    {
                        ApnContext DisableapnContext = mApnContexts.get(PhoneConstants.APN_TYPE_DEFAULT);
                        clearWaitingApn();
                        cleanUpConnection(true, DisableapnContext);                        
                        mWaitingApnList.add(apnContext.getApnType());
                        return true;
                    }
                } else {
                    if (DBG) log("setupData: No free DataConnection and couldn't create one, WEIRD");
                    return false;
                }
            }
        } else {//[mr2] MTK add
            apnSetting = mDataConnectionAcHashMap.get(dcac.getDataConnectionIdSync()).getApnSettingSync();
        }

        //[mr2] MTK added
        if (apnContext.getDcAc() != null && apnContext.getDcAc() != dcac) {
            if (DBG) log("setupData: dcac not null and not equal to assigned dcac.");
            apnContext.setDataConnectionAc(null);
        }

        apnContext.setDataConnectionAc(dcac);
        apnContext.setApnSetting(apnSetting);
        apnContext.setState(DctConstants.State.CONNECTING);
        mPhone.notifyDataConnection(apnContext.getReason(), apnContext.getApnType());

        //[mr2] TODO: removed  , but mms part need to check 
        // If reconnect alarm is active on this DataConnection, wait for the alarm being
        // fired so that we don't disruppt data retry pattern engaged.
        /*
        if (apnContext.getDcAc().getReconnectIntentSync() != null) {
            if (DBG) log("setupData: data reconnection pending");
            apnContext.setState(DctConstants.State.FAILED);
            if (PhoneConstants.APN_TYPE_MMS.equals(apnContext.getApnType())) {
                mPhone.notifyDataConnection(apnContext.getReason(), apnContext.getApnType(), PhoneConstants.DataState.CONNECTING);
            } else {
                mPhone.notifyDataConnection(apnContext.getReason(), apnContext.getApnType());
            }
            return true;
        }*/

        //[Mr2] MTK added start
        if (apnContext.getApnType() == PhoneConstants.APN_TYPE_MMS) {
            mWaitingApnList.clear();
            
            /** 
             * M: if MMS's proxy IP address is the same as current connected interface 
             *    and their APN is not the same, try to disable the existed one.
             *    Then, setup MMS's interface.
             */
            for (ApnContext currApnCtx : mApnContexts.values()) {
                ApnSetting apn = currApnCtx.getApnSetting();

                if (currApnCtx == apnContext)
                    continue;            
                if ((apn != null) && !currApnCtx.isDisconnected() && 
                            !apn.equals(apnSetting) && (isSameProxy(apn, apnSetting) && !apn.apn.equals(apnSetting.apn))) {
                    if (DBG) logd("setupData: disable conflict APN " + currApnCtx.getApnType());
                    disableApnType(currApnCtx.getApnType());
                    mWaitingApnList.add(currApnCtx.getApnType());
                }
            }
        }
        //[Mr2] MTK added end

        Message msg = obtainMessage();
        msg.what = DctConstants.EVENT_DATA_SETUP_COMPLETE;
        msg.obj = apnContext;
        //dcac.bringUp(apnContext, getInitialMaxRetry(), profileId, msg);
        dcac.bringUp(apnContext, getInitialMaxRetry(), profileId, radioTech, msg);//[KK]

        if (DBG) log("setupData: initing!");
        return true;
    }

    /**
     * Handles changes to the APN database.
     */
    private void onApnChanged() {
        DctConstants.State overallState = getOverallState();
        boolean isDisconnected = (overallState == DctConstants.State.IDLE ||
                overallState == DctConstants.State.FAILED);

        if (mPhone instanceof GSMPhone) {
            ((GSMPhone)mPhone).updateCurrentCarrierInProvider();                        
        }

        // TODO: It'd be nice to only do this if the changed entrie(s)
        // match the current operator.
        if (DBG) log("onApnChanged: createAllApnList and cleanUpAllConnections");
        ArrayList<ApnSetting> previous_allApns = mAllApnSettings;
        ApnSetting previous_preferredApn = mPreferredApn;
        createAllApnList();
        boolean isSameApnSetting = false;
        if ((previous_allApns == null && mAllApnSettings == null)){
            if (previous_preferredApn == null && mPreferredApn == null) {
                isSameApnSetting = true;
            } else if (previous_preferredApn != null && previous_preferredApn.equals(mPreferredApn)) {
                isSameApnSetting = true;
            }
        } else if (previous_allApns != null && mAllApnSettings != null) {
            String pre_all_str = "";
            String all_str = "";
            for (ApnSetting s : previous_allApns) {
                pre_all_str += s.toString();
            }
            for (ApnSetting t : mAllApnSettings) {
                all_str += t.toString();
            }
            if (pre_all_str.equals(all_str)) {
                if (previous_preferredApn == null && mPreferredApn == null) {
                    isSameApnSetting = true;
                } else if (previous_preferredApn != null && previous_preferredApn.equals(mPreferredApn)) {
                    isSameApnSetting = true;
                    //TODO MTK remove
                }
            } else { 
                //ALPS01236413, all apn settins is changed but, previous and current preferredApn not changed
                if (previous_preferredApn != null && previous_preferredApn.equals(mPreferredApn)) {
                    isSameApnSetting = true;
                }
            }
        }
        
        if (isSameApnSetting) {
            if (DBG) log("onApnChanged: not changed.");
            return;
        }

        if (DBG) log("onApnChanged: previous_preferredApn [" + previous_preferredApn + "] mPreferredApn [" + mPreferredApn + "]");
        setInitialAttachApn(); //[KK] TODO: check        
        cleanUpAllConnections(!isDisconnected, Phone.REASON_APN_CHANGED);
        if (isDisconnected) {
            sendMessage(obtainMessage(DctConstants.EVENT_TRY_SETUP_DATA, Phone.REASON_APN_CHANGED));
            //setupDataOnConnectableApns(Phone.REASON_APN_CHANGED);
        }

        int gprsDefaultSIM = getDataConnectionFromSetting();
        if ((FeatureOption.MTK_GEMINI_SUPPORT && gprsDefaultSIM == mPhone.getMySimId()) ||
                (!FeatureOption.MTK_GEMINI_SUPPORT && mUserDataEnabled)) {
            // Enable default APN context if default APN context is not enabled.
            if (!isApnTypeEnabled(PhoneConstants.APN_TYPE_DEFAULT)) {
                if (DBG) log("onApnChanged: Default APN context is not enabled.");
                enableApnType(PhoneConstants.APN_TYPE_DEFAULT);
            }
        }
    }

    /**
     * @param cid Connection id provided from RIL.
     * @return DataConnectionAc associated with specified cid.
     */
    private DcAsyncChannel findDataConnectionAcByCid(int cid) {
        for (DcAsyncChannel dcac : mDataConnectionAcHashMap.values()) {
            if (dcac.getCidSync() == cid) {
                return dcac;
            }
        }
        return null;
    }

    /**
     * @param dcacs Collection of DataConnectionAc reported from RIL.
     * @return List of ApnContext which is connected, but is not present in
     *         data connection list reported from RIL.
     */
    //[mr2] TODO: Removed
    /*
    private List<ApnContext> findApnContextToClean(Collection<DcAsyncChannel> dcacs) {
        if (dcacs == null) return null;

        if (DBG) log("findApnContextToClean(ar): E dcacs=" + dcacs);

        ArrayList<ApnContext> list = new ArrayList<ApnContext>();
        for (ApnContext apnContext : mApnContexts.values()) {
            if (apnContext.getState() == DctConstants.State.CONNECTED) {
                boolean found = false;
                for (DcAsyncChannel dcac : dcacs) {
                    if (dcac == apnContext.getDcAc()) {
                        // ApnContext holds the ref to dcac present in data call list.
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    // ApnContext does not have dcac reported in data call list.
                    // Fetch all the ApnContexts that map to this dcac which are in
                    // INITING state too.
                    if (DBG) log("findApnContextToClean(ar): Connected apn not found in the list (" +
                                 apnContext.toString() + ")");
                    if (apnContext.getDcAc() != null) {
                        list.addAll(apnContext.getDcAc().getApnListSync());
                    } else {
                        list.add(apnContext);
                    }
                }
            }
        }
        if (DBG) log("findApnContextToClean(ar): X list=" + list);
        return list;
    }    */

    /**
     * @param ar is the result of RIL_REQUEST_DATA_CALL_LIST
     * or RIL_UNSOL_DATA_CALL_LIST_CHANGED
     */
    private void onDataStateChanged (AsyncResult ar) {
        ArrayList<DataCallResponse> dataCallStates;

        if (DBG) log("onDataStateChanged(ar): E");
        dataCallStates = (ArrayList<DataCallResponse>)(ar.result);

        if (ar.exception != null) {
            // This is probably "radio not available" or something
            // of that sort. If so, the whole connection is going
            // to come down soon anyway
            if (DBG) log("onDataStateChanged(ar): exception; likely radio not available, ignore");
            //mtk03851: since we have exception, we should assune there is no any pdp context
            dataCallStates = new ArrayList<DataCallResponse>(0);
        }

        int size = dataCallStates.size();
        if (DBG) log("onDataStateChanged(ar): DataCallState size=" + size);

        //[mr2] TODO: MTK start
        if (size == 0) {
            /** M: if current list is not null and we receive zero length list, 
             *  we will try to cleanup all pending connections
             */
            Collection<DataConnection> collection = mDataConnections.values();
            Iterator<DataConnection> iterator = collection.iterator();
            while (iterator.hasNext()) {
                DataConnection dataConnection = iterator.next();
                DcAsyncChannel dataConnectionAc = mDataConnectionAcHashMap.get(dataConnection.getDataConnectionId());
              // M: If current data connection reference count is greater than 0 and state should be "ACTIVATE" then tear down it.
                if (dataConnectionAc != null &&
                    dataConnectionAc.isActiveSync())
                {
                    loge("found unlinked DataConnection, to tear down it");
                    dataConnectionAc.tearDownAll(Phone.REASON_DATA_DETACHED, null);
                }
            }
        }
        //[mr2] MTK end
        
        // Create a hash map to store the dataCallState of each DataConnectionAc
        HashMap<DataCallResponse, DcAsyncChannel> dataCallStateToDcac;
        dataCallStateToDcac = new HashMap<DataCallResponse, DcAsyncChannel>();
        for (DataCallResponse dataCallState : dataCallStates) {
            DcAsyncChannel dcac = findDataConnectionAcByCid(dataCallState.cid);

            if (dcac != null) dataCallStateToDcac.put(dataCallState, dcac);
        }

        // A list of apns to cleanup, those that aren't in the list we know we have to cleanup
        //[mr2] TODO: removed
        //List<ApnContext> apnsToCleanup = findApnContextToClean(dataCallStateToDcac.values());


        // Check if we should start or stop polling, by looking
        // for dormant and active connections.
        boolean isAnyDataCallDormant = false;
        boolean isAnyDataCallActive = false;
        // Find which connections have changed state and send a notification or cleanup
        for (DataCallResponse newState : dataCallStates) {
            if (newState.active == DATA_CONNECTION_ACTIVE_PH_LINK_UP) isAnyDataCallActive = true;
            if (newState.active == DATA_CONNECTION_ACTIVE_PH_LINK_DOWN) isAnyDataCallDormant = true;
        }

        //[mr2] TODO : Mr2 in this part is questionable!!?  no sync 

        //[mr2] TODO : need to patch MTK modify start
        /*
        for (DataCallResponse newState : dataCallStates) {
            DcAsyncChannel dcac = dataCallStateToDcac.get(newState);

            if (dcac == null) {
                loge("onDataStateChanged(ar): No associated DataConnection ignore");

                // M: Deactivate unlinked PDP context
                loge("Deactivate unlinked PDP context.");
                mPhone.mCi.deactivateDataCall(newState.cid, RILConstants.DEACTIVATE_REASON_PDP_RESET, obtainMessage(DctConstants.EVENT_RESET_PDP_DONE, newState.cid, 0));

                continue;
            }

            if (newState.active == DATA_CONNECTION_ACTIVE_PH_LINK_UP) isAnyDataCallActive = true;
            if (newState.active == DATA_CONNECTION_ACTIVE_PH_LINK_DOWN) isAnyDataCallDormant = true;

            // The list of apn's associated with this DataConnection
            Collection<ApnContext> apns = dcac.getApnListSync();

            // Find which ApnContexts of this DC are in the "Connected/Connecting" state.
            ArrayList<ApnContext> connectedApns = new ArrayList<ApnContext>();
            for (ApnContext apnContext : apns) {
                if (apnContext.getState() == DctConstants.State.CONNECTED ||
                       apnContext.getState() == DctConstants.State.CONNECTING ||
                       apnContext.getState() == DctConstants.State.INITING) {
                    connectedApns.add(apnContext);
                }
            }
            if (connectedApns.size() == 0) {
                if (DBG) log("onDataStateChanged(ar): no connected apns");
            } else {
                // Determine if the connection/apnContext should be cleaned up
                // or just a notification should be sent out.
                if (DBG) log("onDataStateChanged(ar): Found ConnId=" + newState.cid
                        + " newState=" + newState.toString());
                if (newState.active == 0) {
                    if (DBG) {
                        log("onDataStateChanged(ar): inactive, cleanup apns=" + connectedApns);
                    }
                    apnsToCleanup.addAll(connectedApns);
                } else {
                    // Its active so update the DataConnections link properties
                    UpdateLinkPropertyResult result =
                        dcac.updateLinkPropertiesDataCallStateSync(newState);
                    if (result.oldLp.equals(result.newLp)) {
                        if (DBG) log("onDataStateChanged(ar): no change");
                    } else {
                        if (result.oldLp.isIdenticalInterfaceName(result.newLp)) {
                            if (! result.oldLp.isIdenticalDnses(result.newLp) ||
                                    ! result.oldLp.isIdenticalRoutes(result.newLp) ||
                                    ! result.oldLp.isIdenticalHttpProxy(result.newLp) ||
                                    ! result.oldLp.isIdenticalAddresses(result.newLp)) {
                                // If the same address type was removed and added we need to cleanup
                                CompareResult<LinkAddress> car =
                                    result.oldLp.compareAddresses(result.newLp);
                                if (DBG) {
                                    log("onDataStateChanged: oldLp=" + result.oldLp +
                                            " newLp=" + result.newLp + " car=" + car);
                                }

                                // M: If LP changes always clean up connection instead of update LP
                                apnsToCleanup.addAll(connectedApns);                                                              
                                
                            } else {
                                if (DBG) {
                                    log("onDataStateChanged(ar): no changes");
                                }
                            }
                        } else {
                            //the first time we setup data call, we encounter that the interface is changed
                            //but the old interface is null (not setup yet)
                            //we should ignore cleaning up apn in this case
                            if (result.oldLp.getInterfaceName() != null) {
                            if (DBG) {
                                log("onDataStateChanged(ar): interface change, cleanup apns="
                                        + connectedApns);
                            }
                                apnsToCleanup.addAll(connectedApns);
                            } else {
                                if (DBG) {
                                    log("onDataStateChanged(ar): interface change but no old interface, not to cleanup apns"
                                            + connectedApns);
                                }
                            }
                        }
                    }
                }
            }
        }
        */
        //[mr2] TODO : need to patch MTK modify end

        if (isAnyDataCallDormant && !isAnyDataCallActive) {
            // There is no way to indicate link activity per APN right now. So
            // Link Activity will be considered dormant only when all data calls
            // are dormant.
            // If a single data call is in dormant state and none of the data
            // calls are active broadcast overall link state as dormant.
            mActivity = DctConstants.Activity.DORMANT;
            if (DBG) {
                log("onDataStateChanged: Data Activity updated to DORMANT. stopNetStatePoll");
            }
            stopNetStatPoll();
        } else {
            mActivity = DctConstants.Activity.NONE;
            if (DBG) {
                log("onDataStateChanged: Data Activity updated to NONE. " +
                         "isAnyDataCallActive = " + isAnyDataCallActive +
                         " isAnyDataCallDormant = " + isAnyDataCallDormant);
            }
            if (isAnyDataCallActive) startNetStatPoll();
        }

        //[mr2] Removed
        /*
        mPhone.notifyDataActivity();

        if (apnsToCleanup.size() != 0) {
            // Add an event log when the network drops PDP
            int cid = getCellLocationId();
            EventLog.writeEvent(EventLogTags.PDP_NETWORK_DROP, cid,
                                TelephonyManager.getDefault().getNetworkType());
        }

        // Cleanup those dropped connections
        if (DBG) log("onDataStateChange(ar): apnsToCleanup=" + apnsToCleanup);
        for (ApnContext apnContext : apnsToCleanup) {
            cleanUpConnection(true, apnContext);
        }
        */

        if (DBG) log("onDataStateChanged(ar): X");
    }

    //[KK] TODO : Need to removed
    private void notifyDefaultData(ApnContext apnContext) {
        if (DBG) {
            log("notifyDefaultData: type=" + apnContext.getApnType()
                + ", reason:" + apnContext.getReason());
        }

        // M: If context is disabled and state is DISCONNECTING, 
        // should not change the state to confuse the following enableApnType() which returns PhoneConstants.APN_ALREADY_ACTIVE as CONNECTED
        if (apnContext.getState() != DctConstants.State.DISCONNECTING) {
            apnContext.setState(DctConstants.State.CONNECTED);
        }

        // setState(DctConstants.State.CONNECTED);
        mPhone.notifyDataConnection(apnContext.getReason(), apnContext.getApnType());
        startNetStatPoll();
        startDataStallAlarm(DATA_STALL_NOT_SUSPECTED);
    }

    // TODO: For multiple Active APNs not exactly sure how to do this.
    @Override
    protected void gotoIdleAndNotifyDataConnection(String reason) {
        if (DBG) log("gotoIdleAndNotifyDataConnection: reason=" + reason);
        notifyDataConnection(reason);
        mActiveApn = null;
    }

    /**
     * "Active" here means ApnContext isEnabled() and not in FAILED state
     * @param apnContext to compare with
     * @return true if higher priority active apn found
     */
    private boolean isHigherPriorityApnContextActive(ApnContext apnContext) {
        for (ApnContext otherContext : mPrioritySortedApnContexts) {
            if (apnContext.getApnType().equalsIgnoreCase(otherContext.getApnType())) return false;
            if (otherContext.isEnabled() && otherContext.getState() != DctConstants.State.FAILED) {
                return true;
            }
        }
        return false;
    }

    /**
     * Reports if we support multiple connections or not.
     * This is a combination of factors, based on carrier and RAT.
     * @param rilRadioTech the RIL Radio Tech currently in use
     * @return true if only single DataConnection is allowed
     */
    private boolean isOnlySingleDcAllowed(int rilRadioTech) {
        int[] singleDcRats = mPhone.getContext().getResources().getIntArray(
                com.android.internal.R.array.config_onlySingleDcAllowed);
        boolean onlySingleDcAllowed = false;
        if (Build.IS_DEBUGGABLE &&
                SystemProperties.getBoolean("persist.telephony.test.singleDc", false)) {
            onlySingleDcAllowed = true;
        }
        if (singleDcRats != null) {
            for (int i=0; i < singleDcRats.length && onlySingleDcAllowed == false; i++) {
                if (rilRadioTech == singleDcRats[i]) onlySingleDcAllowed = true;
            }
        }

        if (DBG) log("isOnlySingleDcAllowed(" + rilRadioTech + "): " + onlySingleDcAllowed);
        return onlySingleDcAllowed;
    }

    @Override
    protected void restartRadio() {
        if (DBG) log("restartRadio: ************TURN OFF RADIO**************");
        if (FeatureOption.MTK_GEMINI_SUPPORT) {
            int simId = mPhone.getMySimId();
            int dualSimMode = Settings.System.getInt(mPhone.getContext().getContentResolver(), Settings.System.DUAL_SIM_MODE_SETTING, 0);
            if (DBG) log("restartRadio: dual sim mode: " + dualSimMode);
            cleanUpAllConnections(true, Phone.REASON_RADIO_TURNED_OFF);
            mPhone.getServiceStateTracker().powerOffRadioSafely(this);
            //for dual sim we need restart radio power manually
            log("Start to radio off [" + dualSimMode + ", " + (dualSimMode & ~(simId+1)) + "]");

            if (PhoneFactory.isDualTalkMode()) {
                // M: As Dual Talk mode, just set radio mode via current phone.
                mPhone.mCi.setRadioMode(GeminiNetworkSubUtil.MODE_FLIGHT_MODE, obtainMessage(MSG_RESTART_RADIO_OFF_DONE, dualSimMode, 0));                
            } else {
                //we should always set radio power through 3G protocol
                GeminiPhone mGeminiPhone = (GeminiPhone)PhoneFactory.getDefaultPhone();
                int sim3G = mGeminiPhone.get3GSimId();
                if (sim3G == simId) {
                    mPhone.mCi.setRadioMode((dualSimMode & ~(simId+1)), obtainMessage(MSG_RESTART_RADIO_OFF_DONE, dualSimMode, 0));
                } else {
                    log("set radio off through peer phone(" + sim3G + ") since current phone is 2G protocol");
                    if (mPhone instanceof GSMPhone) {
                        GSMPhone peerPhone = ((GSMPhone)mPhone).getPeerPhones(sim3G);
                       ((PhoneBase)peerPhone).mCi.setRadioMode((dualSimMode & ~(simId+1)), obtainMessage(MSG_RESTART_RADIO_OFF_DONE, dualSimMode, 0));
                    }
                }
            }
        } else {
            cleanUpAllConnections(true, Phone.REASON_RADIO_TURNED_OFF);
            mPhone.getServiceStateTracker().powerOffRadioSafely(this);
            /* Note: no need to call setRadioPower(true).  Assuming the desired
             * radio power state is still ON (as tracked by ServiceStateTracker),
             * ServiceStateTracker will call setRadioPower when it receives the
             * RADIO_STATE_CHANGED notification for the power off.  And if the
             * desired power state has changed in the interim, we don't want to
             * override it with an unconditional power on.
             */
        }

        int reset = Integer.parseInt(SystemProperties.get("net.ppp.reset-by-timeout", "0"));
        SystemProperties.set("net.ppp.reset-by-timeout", String.valueOf(reset+1));
    }

    /**
     * Return true if data connection need to be setup after disconnected due to
     * reason.
     *
     * @param reason the reason why data is disconnected
     * @return true if try setup data connection is need for this reason
     */
    private boolean retryAfterDisconnected(ApnContext apnContext) {
        boolean retry = true;
        String reason = apnContext.getReason();

        if ( Phone.REASON_RADIO_TURNED_OFF.equals(reason) ||
                (isOnlySingleDcAllowed(mPhone.getServiceState().getRilDataRadioTechnology())
                 && isHigherPriorityApnContextActive(apnContext))) {
            retry = false;
        }
        return retry;
    }

    private void startAlarmForReconnect(int delay, ApnContext apnContext) {

        //[Mr2] MTK start
        if(FeatureOption.MTK_GEMINI_SUPPORT){
            if (isGeminiDcStateDetachingOrDetached(mPhone.getMySimId())) {
               logw("Current SIM is not active, stop reconnect.");
               return;
            }     
        }   
        //[Mr2] MTK end

        String apnType = apnContext.getApnType();
        Intent intent = new Intent(INTENT_RECONNECT_ALARM + "." + apnType);       
        intent.putExtra(INTENT_RECONNECT_ALARM_EXTRA_REASON, apnContext.getReason());
        intent.putExtra(INTENT_RECONNECT_ALARM_EXTRA_TYPE, apnType);

        if (DBG) {
            log("startAlarmForReconnect: delay=" + delay + " action=" + intent.getAction()
                    + " apn=" + apnContext);
        }

        //MTK
        if (FeatureOption.MTK_GEMINI_SUPPORT) {
            intent.putExtra(PhoneConstants.GEMINI_SIM_ID_KEY, mPhone.getMySimId());
        }

        PendingIntent alarmIntent = PendingIntent.getBroadcast (mPhone.getContext(), 0,
                                        intent, PendingIntent.FLAG_UPDATE_CURRENT);
        apnContext.setReconnectIntent(alarmIntent);
        mAlarmManager.set(AlarmManager.ELAPSED_REALTIME_WAKEUP,
                SystemClock.elapsedRealtime() + delay, alarmIntent);
    }

    private void startAlarmForRestartTrySetup(int delay, ApnContext apnContext) {
        String apnType = apnContext.getApnType();
        Intent intent = new Intent(INTENT_RESTART_TRYSETUP_ALARM + "." + apnType);
        intent.putExtra(INTENT_RESTART_TRYSETUP_ALARM_EXTRA_TYPE, apnType);

        if (DBG) {
            log("startAlarmForRestartTrySetup: delay=" + delay + " action=" + intent.getAction()
                    + " apn=" + apnContext);
        }

        //MTK
        if (FeatureOption.MTK_GEMINI_SUPPORT) {
            intent.putExtra(PhoneConstants.GEMINI_SIM_ID_KEY, mPhone.getMySimId());
        }

        
        PendingIntent alarmIntent = PendingIntent.getBroadcast (mPhone.getContext(), 0,
                                        intent, PendingIntent.FLAG_UPDATE_CURRENT);
        apnContext.setReconnectIntent(alarmIntent);
        mAlarmManager.set(AlarmManager.ELAPSED_REALTIME_WAKEUP,
                SystemClock.elapsedRealtime() + delay, alarmIntent);
    }

    /*
    @Override
    protected String getActionIntentReconnectAlarm(){
        return "";
    }
    
    @Override
    protected String getActionIntentDataStallAlarm(){
        return "";
    }
    */

    private void notifyNoData(DcFailCause lastFailCauseCode,
                              ApnContext apnContext) {
        if (DBG) log( "notifyNoData: type=" + apnContext.getApnType());
        //apnContext.setState(DctConstants.State.FAILED);
        if (lastFailCauseCode.isPermanentFail()
            && (!apnContext.getApnType().equals(PhoneConstants.APN_TYPE_DEFAULT))) {
            mPhone.notifyDataConnectionFailed(apnContext.getReason(), apnContext.getApnType());
        }
    }

    private void onRecordsLoaded() {
        if (DBG) log("onRecordsLoaded: createAllApnList");
        final int gprsDefaultSIM = getDataConnectionFromSetting();
        logd("onRecordsLoaded gprsDefaultSIM:" + gprsDefaultSIM);
        int nSlotId = mPhone.getMySimId();
        if(gprsDefaultSIM == nSlotId){
           // mGsmPhone.setGprsConnType(GeminiNetworkSubUtil.CONN_TYPE_ALWAYS);
        }

        // M: register peer phone notifications
        if (FeatureOption.MTK_GEMINI_SUPPORT) {
            if (mPhone.getPhoneType() == PhoneConstants.PHONE_TYPE_GSM){
                for (int i=0; i<PhoneConstants.GEMINI_SIM_NUM; i++) {
                    if (i != mGsmPhone.getMySimId() && mGsmPhone.getPeerPhones(i) != null) {
                        mGsmPhone.getPeerPhones(i).getCallTracker().unregisterForVoiceCallEnded(this);
                        mGsmPhone.getPeerPhones(i).getCallTracker().unregisterForVoiceCallStarted(this);
                        mGsmPhone.getPeerPhones(i).getCallTracker().registerForVoiceCallEnded(this, DctConstants.EVENT_VOICE_CALL_ENDED_PEER, null);
                        mGsmPhone.getPeerPhones(i).getCallTracker().registerForVoiceCallStarted(this, DctConstants.EVENT_VOICE_CALL_STARTED_PEER, null);
                    }
                }
            }
        }

        boolean bGetDataCallList = true;
        if (FeatureOption.MTK_GEMINI_SUPPORT) {
            boolean bDataEnabled = getAnyDataEnabled();
            boolean bIsDualTalkMode = PhoneFactory.isDualTalkMode();
            log("Data Setting on SIM: " + gprsDefaultSIM + ", current SIM Slot Id: " + nSlotId + 
                " Data enabled: " + bDataEnabled + " DualTalkMode: " + bIsDualTalkMode);
            if (!bIsDualTalkMode && bDataEnabled && gprsDefaultSIM != nSlotId) {
                bGetDataCallList = false; // Gemini support (not dual talk) and still have data enabled, don't update data call list
            }
        }
        
        if (bGetDataCallList) {
            mDcc.getDataCallList();
        }

        // MTK Put the query to threads
        new Thread(new Runnable() {
            public void run() {

                synchronized (mCreateApnLock){                    
                    syncRoamingSetting();
                    createAllApnList();
                    if (!FeatureOption.MTK_GEMINI_SUPPORT) {
                        //currently only single SIM solution support this
                        setInitialAttachApn();
                    }

                    int slotId = mPhone.getMySimId();
                    if (mPhone.mCi.getRadioState().isOn()) {
                        if (DBG) log("onRecordsLoaded: notifying data availability");
                        notifyOffApnsOfAvailability(Phone.REASON_SIM_LOADED);
                    }
                    if (FeatureOption.MTK_GEMINI_SUPPORT && gprsDefaultSIM == slotId){
                        // Enable default APN context if default data is enabled
                        if (isGeminiDcStateDetachingOrDetached(slotId)) {
                            GeminiPhone geminiPhone = (GeminiPhone)PhoneFactory.getDefaultPhone();
                            geminiPhone.enableApnTypeGemini(PhoneConstants.APN_TYPE_DEFAULT, slotId);
                        } else {
                            enableApnType(PhoneConstants.APN_TYPE_DEFAULT);
                        }
                    } else if (!FeatureOption.MTK_GEMINI_SUPPORT && mUserDataEnabled)  {
                        // Enable default APN context if default data is enabled.
                        enableApnType(PhoneConstants.APN_TYPE_DEFAULT);
                    }
                    
                    // Need to re-schedule setup data request by sending message to prevent synchronization problem,
                    // since we spawn thread here to process createAllApnList(). (ALPS00294899)

                    // [mr2] 
                    //setupDataOnConnectableApns(Phone.REASON_SIM_LOADED);                
                    sendMessage(obtainMessage(DctConstants.EVENT_TRY_SETUP_DATA, Phone.REASON_SIM_LOADED));                

                }
                
            }
        }).start();

    }

    @Override
    protected void onSetDependencyMet(String apnType, boolean met) {
        // don't allow users to tweak hipri to work around default dependency not met
        if (PhoneConstants.APN_TYPE_HIPRI.equals(apnType)) return;

        ApnContext apnContext = mApnContexts.get(apnType);
        if (apnContext == null) {
            loge("onSetDependencyMet: ApnContext not found in onSetDependencyMet(" +
                    apnType + ", " + met + ")");
            return;
        }
        applyNewState(apnContext, apnContext.isEnabled(), met);
        if (PhoneConstants.APN_TYPE_DEFAULT.equals(apnType)) {
            // tie actions on default to similar actions on HIPRI regarding dependencyMet
            apnContext = mApnContexts.get(PhoneConstants.APN_TYPE_HIPRI);
            if (apnContext != null) applyNewState(apnContext, apnContext.isEnabled(), met);
        }
    }

    private void applyNewState(ApnContext apnContext, boolean enabled, boolean met) {
        boolean cleanup = false;
        boolean trySetup = false;
        if (DBG) {
            log("applyNewState(" + apnContext.getApnType() + ", " + enabled +
                    "(" + apnContext.isEnabled() + "), " + met + "(" +
                    apnContext.getDependencyMet() +"))");
        }
        if (apnContext.isReady()) {
            if (enabled && met) {
                DctConstants.State state = apnContext.getState();
                switch(state) {
                    case CONNECTING:
                    case SCANNING:
                    case CONNECTED:
                    case DISCONNECTING:
                        // We're "READY" and active so just return
                        if (DBG) log("applyNewState: 'ready' so return");
                        return;
                    case IDLE:
                         // fall through: this is unexpected but if it happens cleanup and try setup
                    case FAILED:
                    case RETRYING: {
                        // We're "READY" but not active so disconnect (cleanup = true) and
                        // connect (trySetup = true) to be sure we retry the connection.
                        trySetup = true;
                        apnContext.setReason(Phone.REASON_DATA_ENABLED);
                        break;
                    }
                }
            } else if (!enabled) {
                cleanup = true;
                apnContext.setReason(Phone.REASON_DATA_DISABLED);
            } else {
                cleanup = true;
                apnContext.setReason(Phone.REASON_DATA_DEPENDENCY_UNMET);
            }
        } else {
            if (enabled && met) {
                if (apnContext.isEnabled()) {
                    apnContext.setReason(Phone.REASON_DATA_DEPENDENCY_MET);
                } else {
                    apnContext.setReason(Phone.REASON_DATA_ENABLED);
                }
                if (apnContext.getState() == DctConstants.State.FAILED) {
                    apnContext.setState(DctConstants.State.IDLE);
                }
                trySetup = true;
            }
        }
        apnContext.setEnabled(enabled);
        apnContext.setDependencyMet(met);
        if (cleanup) cleanUpConnection(true, apnContext);
        if (trySetup) trySetupData(apnContext);
    }

    private DcAsyncChannel checkForCompatibleConnectedApnContext(ApnContext apnContext) {
        String apnType = apnContext.getApnType();
        ApnSetting dunSetting = null;

        if (PhoneConstants.APN_TYPE_DUN.equals(apnType)) {
            dunSetting = fetchDunApn();
        }
        if (DBG) {
            log("checkForCompatibleConnectedApnContext: apnContext=" + apnContext );
        }

        DcAsyncChannel potentialDcac = null;
        ApnContext potentialApnCtx = null;
        for (ApnContext curApnCtx : mApnContexts.values()) {
            DcAsyncChannel curDcac = curApnCtx.getDcAc();
            if (curDcac != null) {
                ApnSetting apnSetting = curApnCtx.getApnSetting();
                if (dunSetting != null) {
                    if (dunSetting.equals(apnSetting)) {
                        switch (curApnCtx.getState()) {
                            case CONNECTED:
                                if (DBG) {
                                    log("checkForCompatibleConnectedApnContext:"
                                            + " found dun conn=" + curDcac
                                            + " curApnCtx=" + curApnCtx);
                                }
                                return curDcac;
                            case RETRYING:
                            case CONNECTING:
                                potentialDcac = curDcac;
                                potentialApnCtx = curApnCtx;
                            default:
                                // Not connected, potential unchanged
                                break;
                        }
                    }
                } else if (apnSetting != null && apnSetting.canHandleType(apnType)) {
                    switch (curApnCtx.getState()) {
                        case CONNECTED:
                            if (DBG) {
                                log("checkForCompatibleConnectedApnContext:"
                                        + " found canHandle conn=" + curDcac
                                        + " curApnCtx=" + curApnCtx);
                            }
                            return curDcac;
                        case RETRYING:
                        case CONNECTING:
                            potentialDcac = curDcac;
                            potentialApnCtx = curApnCtx;
                        default:
                            // Not connected, potential unchanged
                            break;
                    }
                }
            } else {
                if (VDBG) {
                    log("checkForCompatibleConnectedApnContext: not conn curApnCtx=" + curApnCtx);
                }
            }
        }
        if (potentialDcac != null) {
            if (DBG) {
                log("checkForCompatibleConnectedApnContext: found potential conn=" + potentialDcac
                        + " curApnCtx=" + potentialApnCtx);
            }
            return potentialDcac;
        }

        if (DBG) log("checkForCompatibleConnectedApnContext: NO conn apnContext=" + apnContext);
        return null;
    }

    @Override
    protected void onEnableApn(int apnId, int enabled) {
        ApnContext apnContext = mApnContexts.get(apnIdToType(apnId));
        if (apnContext == null) {
            loge("onEnableApn(" + apnId + ", " + enabled + "): NO ApnContext");
            return;
        }
        // TODO change our retry manager to use the appropriate numbers for the new APN
        if (DBG) log("onEnableApn: apnContext=" + apnContext + " call applyNewState");
        applyNewState(apnContext, enabled == DctConstants.ENABLED, apnContext.getDependencyMet());
    }

    @Override
    // TODO: We shouldnt need this.
    protected boolean onTrySetupData(String reason) {
        if (DBG) log("onTrySetupData: reason=" + reason);
        setupDataOnConnectableApns(reason);
        return true;
    }

    protected boolean onTrySetupData(ApnContext apnContext) {
        if (DBG) log("onTrySetupData: apnContext=" + apnContext);
        return trySetupData(apnContext);
    }

    @Override
    protected void onRoamingOff() {
        if (DBG) log("onRoamingOff");

        if (mUserDataEnabled == false) return;

        if (getDataOnRoamingEnabled() == false) {
            notifyOffApnsOfAvailability(Phone.REASON_ROAMING_OFF);
            setupDataOnConnectableApns(Phone.REASON_ROAMING_OFF);
        } else {
            notifyDataConnection(Phone.REASON_ROAMING_OFF);
        }
    }

    @Override
    protected void onRoamingOn() {
        if (mUserDataEnabled == false) return;

        if (getDataOnRoamingEnabled()) {
            if (DBG) log("onRoamingOn: setup data on roaming");
            setupDataOnConnectableApns(Phone.REASON_ROAMING_ON);
            notifyDataConnection(Phone.REASON_ROAMING_ON);
        } else {
            if (DBG) log("onRoamingOn: Tear down data connection on roaming.");
            cleanUpAllConnections(true, Phone.REASON_ROAMING_ON);
            notifyOffApnsOfAvailability(Phone.REASON_ROAMING_ON);
        }
    }

    @Override
    protected void onRadioAvailable() {
        if (DBG) log("onRadioAvailable");
        if (mPhone.getSimulatedRadioControl() != null) {
            // Assume data is connected on the simulator
            // FIXME  this can be improved
            // setState(DctConstants.State.CONNECTED);
            notifyDataConnection(null);

            log("onRadioAvailable: We're on the simulator; assuming data is connected");
        }

        IccRecords r = mIccRecords.get();
        if (r != null && r.getRecordsLoaded()) {
            notifyOffApnsOfAvailability(null);
        }

        if (getOverallState() != DctConstants.State.IDLE) {
            cleanUpConnection(true, null);
        }
    }

    @Override
    protected void onRadioOffOrNotAvailable() {
        // Make sure our reconnect delay starts at the initial value
        // next time the radio comes on

        mReregisterOnReconnectFailure = false;

        if (mPhone.getSimulatedRadioControl() != null) {
            // Assume data is connected on the simulator
            // FIXME  this can be improved
            log("We're on the simulator; assuming radio off is meaningless");
        } else {
            if (DBG) log("onRadioOffOrNotAvailable: is off and clean up all connections");
            cleanUpAllConnections(false, Phone.REASON_RADIO_TURNED_OFF);
        }
        notifyOffApnsOfAvailability(null);
    }

    //[KK] added
    @Override
    protected void completeConnection(ApnContext apnContext) {
        boolean isProvApn = apnContext.isProvisioningApn();

        if (DBG) log("completeConnection: successful, notify the world apnContext=" + apnContext);

        if (mIsProvisioning && !TextUtils.isEmpty(mProvisioningUrl)) {
            if (DBG) {
                log("completeConnection: MOBILE_PROVISIONING_ACTION url="
                        + mProvisioningUrl);
            }
            Intent newIntent = Intent.makeMainSelectorActivity(Intent.ACTION_MAIN,
                    Intent.CATEGORY_APP_BROWSER);
            newIntent.setData(Uri.parse(mProvisioningUrl));
            newIntent.setFlags(Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT |
                    Intent.FLAG_ACTIVITY_NEW_TASK);
            try {
                mPhone.getContext().startActivity(newIntent);
            } catch (ActivityNotFoundException e) {
                loge("completeConnection: startActivityAsUser failed" + e);
            }
        }
        mIsProvisioning = false;
        mProvisioningUrl = null;

        mPhone.notifyDataConnection(apnContext.getReason(), apnContext.getApnType());
        startNetStatPoll();
        startDataStallAlarm(DATA_STALL_NOT_SUSPECTED);
    }


    /**
     * A SETUP (aka bringUp) has completed, possibly with an error. If
     * there is an error this method will call {@link #onDataSetupCompleteError}.
     */
    @Override
    protected void onDataSetupComplete(AsyncResult ar) {

        DcFailCause cause = DcFailCause.UNKNOWN;
        boolean handleError = false;
        ApnContext apnContext = null;

        if(ar.userObj instanceof ApnContext){
            apnContext = (ApnContext)ar.userObj;
        } else {
            throw new RuntimeException("onDataSetupComplete: No apnContext");
        }

        //if (isDataSetupCompleteOk(ar)) {
        if (ar.exception == null) {
            DcAsyncChannel dcac = apnContext.getDcAc();

            if (RADIO_TESTS) {
                // Note: To change radio.test.onDSC.null.dcac from command line you need to
                // adb root and adb remount and from the command line you can only change the
                // value to 1 once. To change it a second time you can reboot or execute
                // adb shell stop and then adb shell start. The command line to set the value is:
                //   adb shell sqlite3 /data/data/com.android.providers.settings/databases/settings.db "insert into system (name,value) values ('radio.test.onDSC.null.dcac', '1');"
                ContentResolver cr = mPhone.getContext().getContentResolver();
                String radioTestProperty = "radio.test.onDSC.null.dcac";
                if (Settings.System.getInt(cr, radioTestProperty, 0) == 1) {
                    log("onDataSetupComplete: " + radioTestProperty +
                            " is true, set dcac to null and reset property to false");
                    dcac = null;
                    Settings.System.putInt(cr, radioTestProperty, 0);
                    log("onDataSetupComplete: " + radioTestProperty + "=" +
                            Settings.System.getInt(mPhone.getContext().getContentResolver(),
                                    radioTestProperty, -1));
                }
            }
            if (dcac == null) {
                log("onDataSetupComplete: no connection to DC, handle as error");
                cause = DcFailCause.CONNECTION_TO_DATACONNECTIONAC_BROKEN;
                handleError = true;
            } else {
                ApnSetting apn = apnContext.getApnSetting();
                if (DBG) {
                    log("onDataSetupComplete: success apn=" + (apn == null ? "unknown" : apn.apn));
                }
                if (apn != null && apn.proxy != null && apn.proxy.length() != 0) {
                    try {
                        String port = apn.port;
                        if (TextUtils.isEmpty(port)) port = "8080";
                        ProxyProperties proxy = new ProxyProperties(apn.proxy,
                                Integer.parseInt(port), null);
                        dcac.setLinkPropertiesHttpProxySync(proxy);
                    } catch (NumberFormatException e) {
                        loge("onDataSetupComplete: NumberFormatException making ProxyProperties (" +
                                apn.port + "): " + e);
                    }
                }

                // everything is setup
                if(TextUtils.equals(apnContext.getApnType(),PhoneConstants.APN_TYPE_DEFAULT)) {
                    SystemProperties.set(PUPPET_MASTER_RADIO_STRESS_TEST, "true");
                    if (mCanSetPreferApn && mPreferredApn == null) {
                        if (DBG) log("onDataSetupComplete: PREFERED APN is null");
                        mPreferredApn = apn;
                        if (mPreferredApn != null) {
                            setPreferredApn(mPreferredApn.id);
                        }
                    }
                } else {
                    SystemProperties.set(PUPPET_MASTER_RADIO_STRESS_TEST, "false");
                }

                mTetheringExt.onDataSetupComplete(apnContext);

                // Notify call start again if call is not IDLE and not concurrent
                //[Mr2] TODO: MTK added, ((GsmCallTracker)mGsmPhone.getCallTracker()).mState  <---public
                
                if (mPhone.getPhoneType() == PhoneConstants.PHONE_TYPE_GSM){
                    if (((GsmCallTracker)mGsmPhone.getCallTracker()).mState != PhoneConstants.State.IDLE &&
                            !mPhone.getServiceStateTracker().isConcurrentVoiceAndDataAllowed()) {
                        if (DBG) log("onDataSetupComplete: In 2G phone call, notify data REASON_VOICE_CALL_STARTED");
                        notifyDataConnection(Phone.REASON_VOICE_CALL_STARTED);
                    }
                }
                
                notifyDefaultData(apnContext);
                //[KK] added  start  TODO: just using original MTK solution notifyDefaultData(apnContext) for ALPS01328972
                // A connection is setup
                // apnContext.setState(DctConstants.State.CONNECTED); // <-- KK original code
                boolean isProvApn = apnContext.isProvisioningApn();
                if ((!isProvApn) || mIsProvisioning) {
                    // Complete the connection normally notifying the world we're connected.
                    // We do this if this isn't a special provisioning apn or if we've been
                    // told its time to provision.
                    completeConnection(apnContext);
                } else {
                    // This is a provisioning APN that we're reporting as connected. Later
                    // when the user desires to upgrade this to a "default" connection,
                    // mIsProvisioning == true, we'll go through the code path above.
                    // mIsProvisioning becomes true when CMD_ENABLE_MOBILE_PROVISIONING
                    // is sent to the DCT.
                    if (DBG) {
                        log("onDataSetupComplete: successful, BUT send connected to prov apn as"
                                + " mIsProvisioning:" + mIsProvisioning + " == false"
                                + " && (isProvisioningApn:" + isProvApn + " == true");
                    }

                    Intent intent = new Intent(
                            TelephonyIntents.ACTION_DATA_CONNECTION_CONNECTED_TO_PROVISIONING_APN);
                    intent.putExtra(PhoneConstants.DATA_APN_KEY, apnContext.getApnSetting().apn);
                    intent.putExtra(PhoneConstants.DATA_APN_TYPE_KEY, apnContext.getApnType());

                    String apnType = apnContext.getApnType();
                    LinkProperties linkProperties = getLinkProperties(apnType);
                    if (linkProperties != null) {
                        intent.putExtra(PhoneConstants.DATA_LINK_PROPERTIES_KEY, linkProperties);
                        String iface = linkProperties.getInterfaceName();
                        if (iface != null) {
                            intent.putExtra(PhoneConstants.DATA_IFACE_NAME_KEY, iface);
                        }
                    }
                    LinkCapabilities linkCapabilities = getLinkCapabilities(apnType);
                    if (linkCapabilities != null) {
                        intent.putExtra(PhoneConstants.DATA_LINK_CAPABILITIES_KEY, linkCapabilities);
                    }

                    mPhone.getContext().sendBroadcastAsUser(intent, UserHandle.ALL);
                }
                if (DBG) {
                    log("onDataSetupComplete: SETUP complete type=" + apnContext.getApnType()
                        + ", reason:" + apnContext.getReason());
                }
                //[KK] added end  TODO: check
                
                /// M: SCRI and Fast Dormancy Feature @{
                //MTK-START [mtk04070][111205][ALPS00093395]Add for SCRI 
                /* Add by MTK03594 for SCRI feature */                                
                startScriPoll();                
                //MTK-END [mtk04070][111205][ALPS00093395]Add for SCRI
                /// @} 
            }
        } else {
            cause = (DcFailCause) (ar.result);
            if (DBG) {
                ApnSetting apn = apnContext.getApnSetting();
                log(String.format("onDataSetupComplete: error apn=%s cause=%s",
                        (apn == null ? "unknown" : apn.apn), cause));
            }
            if (cause.isEventLoggable()) {
                // Log this failure to the Event Logs.
                int cid = getCellLocationId();
                EventLog.writeEvent(EventLogTags.PDP_SETUP_FAIL,
                        cause.ordinal(), cid, TelephonyManager.getDefault().getNetworkType());
            }

            // Count permanent failures and remove the APN we just tried
            if (cause.isPermanentFail()) apnContext.decWaitingApnsPermFailCount();

            apnContext.removeWaitingApn(apnContext.getApnSetting());
            //[mr2] MTK added start
            if (cause == DcFailCause.GMM_ERROR &&
                    mPhone.getServiceStateTracker().getCurrentDataConnectionState() != ServiceState.STATE_IN_SERVICE) {
                // stop retry for GMM_ERROR and let GPRS attach event to trigger
                apnContext.setWaitingApns(new ArrayList<ApnSetting>());
                log("onDataSetupComplete: GMM_ERROR, wait for GPRS attach to retry.");
            } 
            //[mr2] MTK added end
            if (DBG) {
                log(String.format("onDataSetupComplete: WaitingApns.size=%d" +
                        " WaitingApnsPermFailureCountDown=%d",
                        apnContext.getWaitingApns().size(),
                        apnContext.getWaitingApnsPermFailCount()));
            }
            handleError = true;
        }

        if (handleError) {
            onDataSetupCompleteError(ar);
        }
    }

    /**
     * @return number of milli-seconds to delay between trying apns'
     */
    private int getApnDelay() {
        if (mFailFast) {
            return SystemProperties.getInt("persist.radio.apn_ff_delay",
                    APN_FAIL_FAST_DELAY_DEFAULT_MILLIS);
        } else {
            return SystemProperties.getInt("persist.radio.apn_delay", APN_DELAY_DEFAULT_MILLIS);
        }
    }

    /**
     * Error has occurred during the SETUP {aka bringUP} request and the DCT
     * should either try the next waiting APN or start over from the
     * beginning if the list is empty. Between each SETUP request there will
     * be a delay defined by {@link #getApnDelay()}.
     */
    @Override
    protected void onDataSetupCompleteError(AsyncResult ar) {
        String reason = "";
        ApnContext apnContext = null;

        if(ar.userObj instanceof ApnContext){
            apnContext = (ApnContext)ar.userObj;
        } else {
            throw new RuntimeException("onDataSetupCompleteError: No apnContext");
        }

        // See if there are more APN's to try
        if (apnContext.getWaitingApns().isEmpty()) {
            apnContext.setState(DctConstants.State.FAILED);
            mPhone.notifyDataConnection(Phone.REASON_APN_FAILED, apnContext.getApnType());

            apnContext.setDataConnectionAc(null);

            if (apnContext.getWaitingApnsPermFailCount() == 0) {
                if (DBG) {
                    log("onDataSetupCompleteError: All APN's had permanent failures, stop retrying");
                }

                // M: try to enable apn which is in waiting list
                if (apnContext.getApnType() == PhoneConstants.APN_TYPE_MMS) {
                    enableWaitingApn();
                }

                if (PhoneFactory.isDualTalkMode()) {
                    if (apnContext.getApnType() != PhoneConstants.APN_TYPE_DEFAULT && mWaitingApnList.isEmpty()) {
                        // try to restore default
                        trySetupData(Phone.REASON_DATA_ENABLED, PhoneConstants.APN_TYPE_DEFAULT);
                    }
                }
                //M: end

                
            } else {
                int delay = getApnDelay();
                if (DBG) {
                    log("onDataSetupCompleteError: Not all APN's had permanent failures delay="
                            + delay);
                }
                startAlarmForRestartTrySetup(delay, apnContext);            
            }
        } else {
            if (DBG) log("onDataSetupCompleteError: Try next APN");
            apnContext.setState(DctConstants.State.SCANNING);
            // Wait a bit before trying the next APN, so that
            // we're not tying up the RIL command channel
            startAlarmForReconnect(getApnDelay(), apnContext);
        }
    }

    /**
     * Called when EVENT_DISCONNECT_DONE is received.
     */
    @Override
    protected void onDisconnectDone(int connId, AsyncResult ar) {
        boolean enableApnRet = false;
        ApnContext apnContext = null;

        if (ar.userObj instanceof ApnContext) {
            apnContext = (ApnContext) ar.userObj;
        } else {
            loge("onDisconnectDone: Invalid ar in onDisconnectDone, ignore");
            return;
        }

        if(DBG) log("onDisconnectDone: EVENT_DISCONNECT_DONE apnContext=" + apnContext);

        // M: try to enable apn which is in waiting list. 
        // In dual-talk, due to we only have single pdp, 
        // we need to disconnect the existed interface and setup the new one.
        if(PhoneFactory.isDualTalkMode()) {
            enableApnRet = enableWaitingApn();
            if (enableApnRet) {
                if (apnContext.getApnType() == PhoneConstants.APN_TYPE_DEFAULT) {
                    // avoid default retry, ban retry
                    apnContext.setReason(Phone.REASON_RADIO_TURNED_OFF);
                    logd("onDisconnectoinDone: set reason to radio turned off to avoid retry.");
                }
            }
        } else {
            if (apnContext.getApnType() == PhoneConstants.APN_TYPE_MMS) {
                enableWaitingApn();
            }
        }

        if (!mWaitingNonDefaultApn.isEmpty()) {
            enableApnType(mWaitingNonDefaultApn.remove(0));
        }

        apnContext.setState(DctConstants.State.IDLE);
        
        // M: handle connection reactive case. Try to setup it later after receive disconnect done event
        if (apnContext.isReactive() && apnContext.isReady()) {
            if(DBG) log("onDisconnectDone(): isReactive() == true, notify " + apnContext.getApnType() +" APN with state CONNECTING");
            mPhone.notifyDataConnection(apnContext.getReason(), apnContext.getApnType(), PhoneConstants.DataState.CONNECTING);
        } else {
            mPhone.notifyDataConnection(apnContext.getReason(), apnContext.getApnType());
        }
        apnContext.setReactive(false);

        // if all data connection are gone, check whether Airplane mode request was
        // pending.
        if (isDisconnected()) {
            /// M: SCRI and Fast Dormancy Feature @{ 
            //Modify by mtk01411: Only all data connections are terminated, it is necessary to invoke the stopScriPoll()
            if(DBG) log("All data connections are terminated:stopScriPoll()");                    	
            stopScriPoll();
            /// @}  
            
            if (mPhone.getServiceStateTracker().processPendingRadioPowerOffAfterDataOff()) {
                if(DBG) log("onDisconnectDone: radio will be turned off, no retries");
                // Radio will be turned off. No need to retry data setup
                apnContext.setApnSetting(null);
                apnContext.setDataConnectionAc(null);
                return;
            }
        }
        /// M: SCRI and Fast Dormancy Feature @{        
        //MTK-START [mtk04070][111205][ALPS00093395]Stop SCRI polling
        //Modify by mtk01411: Only all data connections are terminated, it is necessary to invoke the stopScriPoll()
        //stopScriPoll();
        //MTK-END [mtk04070][111205][ALPS00093395]Stop SCRI polling
        /// @}
        // If APN is still enabled, try to bring it back up automatically
        if (mAttached.get() && apnContext.isReady() && retryAfterDisconnected(apnContext)) {
            SystemProperties.set(PUPPET_MASTER_RADIO_STRESS_TEST, "false");
            // Wait a bit before trying the next APN, so that
            // we're not tying up the RIL command channel.
            // This also helps in any external dependency to turn off the context.
            if(DBG) log("onDisconnectDone: attached, ready and retry after disconnect");
            startAlarmForReconnect(getApnDelay(), apnContext);
        } else {
            boolean restartRadioAfterProvisioning = mPhone.getContext().getResources().getBoolean(
                    com.android.internal.R.bool.config_restartRadioAfterProvisioning);

            if (apnContext.isProvisioningApn() && restartRadioAfterProvisioning) {
                log("onDisconnectDone: restartRadio after provisioning");
                restartRadio();
            }
            apnContext.setApnSetting(null);
            apnContext.setDataConnectionAc(null);
            if (isOnlySingleDcAllowed(mPhone.getServiceState().getRilDataRadioTechnology())) {
                if(DBG) log("onDisconnectDone: isOnlySigneDcAllowed true so setup single apn");
                setupDataOnConnectableApns(Phone.REASON_SINGLE_PDN_ARBITRATION);
            } else {
                if(DBG) log("onDisconnectDone: not retrying");
            }
        }

        if (PhoneFactory.isDualTalkMode()) {
            if (enableApnRet == false) {
                if (apnContext.getApnType() != PhoneConstants.APN_TYPE_DEFAULT) {
                    // try to restore default
                    trySetupData(Phone.REASON_DATA_ENABLED, PhoneConstants.APN_TYPE_DEFAULT);
                }
            }
        }
    }

    /**
     * Called when EVENT_DISCONNECT_DC_RETRYING is received.
     */
    @Override
    protected void onDisconnectDcRetrying(int connId, AsyncResult ar) {
        // We could just do this in DC!!!
        ApnContext apnContext = null;

        if (ar.userObj instanceof ApnContext) {
            apnContext = (ApnContext) ar.userObj;
        } else {
            loge("onDisconnectDcRetrying: Invalid ar in onDisconnectDone, ignore");
            return;
        }

        apnContext.setState(DctConstants.State.RETRYING);
        if(DBG) log("onDisconnectDcRetrying: apnContext=" + apnContext);

        mPhone.notifyDataConnection(apnContext.getReason(), apnContext.getApnType());
    }

    protected void onPollPdp() {
        if (getOverallState() == DctConstants.State.CONNECTED) {
            // only poll when connected
            mPhone.mCi.getDataCallList(obtainMessage(DctConstants.EVENT_DATA_STATE_CHANGED));
            sendMessageDelayed(obtainMessage(DctConstants.EVENT_POLL_PDP), POLL_PDP_MILLIS);
        }
    }

    @Override
    protected void onVoiceCallStarted() {
        if (DBG) log("onVoiceCallStarted");
        mInVoiceCall = true;
        if (!mPhone.getServiceStateTracker().isConcurrentVoiceAndDataAllowed()) {
            notifyDataConnection(Phone.REASON_VOICE_CALL_STARTED);            
        }
 
        if (isConnected() && ! mPhone.getServiceStateTracker().isConcurrentVoiceAndDataAllowed()) {
            if (DBG) log("onVoiceCallStarted stop polling");
            stopNetStatPoll();
            stopDataStallAlarm();
        }
    }

    @Override
    protected void onVoiceCallEnded() {
        if (DBG) log("onVoiceCallEnded");
        mInVoiceCall = false;
        if (!mPhone.getServiceStateTracker().isConcurrentVoiceAndDataAllowed()) {
            notifyDataConnection(Phone.REASON_VOICE_CALL_ENDED);
        }

        if (isConnected()) {
            if (!mPhone.getServiceStateTracker().isConcurrentVoiceAndDataAllowed()) {
                startNetStatPoll();
                startDataStallAlarm(DATA_STALL_NOT_SUSPECTED);
            } else {
                // clean slate after call end.
                resetPollStats();
            }
        }
        // reset reconnect timer
        setupDataOnConnectableApns(Phone.REASON_VOICE_CALL_ENDED);
    }
        
    protected void onVoiceCallEndedPeer() {
        if (DBG) log("onVoiceCallEndedPeer");

        notifyDataConnection(Phone.REASON_VOICE_CALL_ENDED);

        if (isConnected()) {
            startNetStatPoll();
            startDataStallAlarm(DATA_STALL_NOT_SUSPECTED);
        }
        // reset reconnect timer
        setupDataOnConnectableApns(Phone.REASON_VOICE_CALL_ENDED);
    }

    @Override
    protected void onCleanUpConnection(boolean tearDown, int apnId, String reason) {
        if (DBG) log("onCleanUpConnection");
        ApnContext apnContext = mApnContexts.get(apnIdToType(apnId));
        if (apnContext != null) {
            apnContext.setReason(reason);
            cleanUpConnection(tearDown, apnContext);
        }
    }

    @Override
    protected boolean isConnected() {
        for (ApnContext apnContext : mApnContexts.values()) {
            if (apnContext.getState() ==DctConstants.State.CONNECTED) {
                // At least one context is connected, return true
                return true;
            }
        }
        // There are not any contexts connected, return false
        return false;
    }

    @Override
    public boolean isDisconnected() {
        for (ApnContext apnContext : mApnContexts.values()) {
            if (!apnContext.isDisconnected()) {
                // At least one context was not disconnected return false
                return false;
            }
        }
        // All contexts were disconnected so return true
        return true;
    }

    @Override
    protected void notifyDataConnection(String reason) {
        if (DBG) log("notifyDataConnection: reason=" + reason);
        for (ApnContext apnContext : mApnContexts.values()) {
            if (mAttached.get() && apnContext.isReady()) { //[KK]          
            //if (apnContext.isReady()) {
                if (DBG) log("notifyDataConnection: type:"+apnContext.getApnType());
                mPhone.notifyDataConnection(reason != null ? reason : apnContext.getReason(),
                        apnContext.getApnType());
            }
        }
        notifyOffApnsOfAvailability(reason);
    }

    /**
     * Based on the sim operator numeric, create a list for all possible
     * Data Connections and setup the preferredApn.
     */
    private void createAllApnList() {
        boolean hasResult = false;
        mAllApnSettings = new ArrayList<ApnSetting>();
        IccRecords r = mIccRecords.get();
        String operator = (r != null) ? r.getOperatorNumeric() : "";
        if (operator != null) {
            String selection = "numeric = '" + operator + "'";
            // query only enabled apn.
            // carrier_enabled : 1 means enabled apn, 0 disabled apn.
            selection += " and carrier_enabled = 1";
            if (DBG) log("createAllApnList: selection=" + selection);
            Cursor cursor = null;
            if (FeatureOption.MTK_GEMINI_SUPPORT) {
                Uri geminiUri = CONTENT_URI_GEMINI[mPhone.getMySimId()];
                cursor = mPhone.getContext().getContentResolver().query(geminiUri, null, selection, null, null);
            } else {
                cursor = mPhone.getContext().getContentResolver().query(
                    Telephony.Carriers.CONTENT_URI, null, selection, null, null);
            }

            if (cursor != null) {
                if (cursor.getCount() > 0) {
                    mAllApnSettings = createApnList(cursor);
                    hasResult = true;
                }
                cursor.close();
            }
        }

        if (!hasResult) {
            mAllApnSettings = new ArrayList<ApnSetting>();
        }

        if (mAllApnSettings.isEmpty()) {
            if (DBG) log("createAllApnList: No APN found for carrier: " + operator);
            mPreferredApn = null;
            // TODO: What is the right behaviour?
            //notifyNoData(GsmDataConnection.FailCause.MISSING_UNKNOWN_APN);
        } else {
            mPreferredApn = getPreferredApn();
            //MTK start
            if (DBG) log("createAllApnList: mPreferredApn_XXX=" + mPreferredApn);
            if (r != null && (mPreferredApn == null || !mPreferredApn.numeric.equals(operator))) {
                Cursor cursor = mGsmDCTExt.getOptPreferredApn(r.getIMSI(),
                        r.getOperatorNumeric(), mPhone.getMySimId());
                if (cursor != null) {
                    ArrayList<ApnSetting> result = createApnList(cursor);
                    cursor.close();
                    if (result != null && result.size() > 0) {
                        mPreferredApn = result.get(0);
                    }
                }
            }
            //MTK end
            if (mPreferredApn != null && !mPreferredApn.numeric.equals(operator)) {
                mPreferredApn = null;
                setPreferredApn(-1);
            }
            if (DBG) log("createAllApnList: mPreferredApn=" + mPreferredApn);
        }

        mTetheringExt.onCreateAllApnList(new ArrayList<IApnSetting>(mAllApnSettings), operator);

        if (DBG) log("createAllApnList: X mAllApnSettings=" + mAllApnSettings);
    }

    protected int getPdpConnectionPoolSize() {
        return PDP_CONNECTION_POOL_SIZE;
    }

    /** Return the DC AsyncChannel for the new data connection */
    private DcAsyncChannel createDataConnection() {
        if (DBG) log("createDataConnection E");

        int id = mUniqueIdGenerator.getAndIncrement();

        // TODO: need to fix before dual talk enabled
        //if (id >= TelephonyManager.getMaxPdpNum(mPhone.getMySimId())) {
        //    loge("Max PDP count is "+ TelephonyManager.getMaxPdpNum(mPhone.getMySimId())+",but request " + (id + 1));
        //    return null;
        //}
        if (id >= getPdpConnectionPoolSize()) {
            loge("Max PDP count is " + getPdpConnectionPoolSize() + ",but request " + (id + 1));
            return null;
        }
        DataConnection conn = DataConnection.makeDataConnection(mPhone, id,
                                          this, mDcTesterFailBringUpAll, mDcc);

        mDataConnections.put(id, conn);
        DcAsyncChannel dcac = new DcAsyncChannel(conn, LOG_TAG);
        int status = dcac.fullyConnectSync(mPhone.getContext(), this, conn.getHandler());
        if (status == AsyncChannel.STATUS_SUCCESSFUL) {
            mDataConnectionAcHashMap.put(dcac.getDataConnectionIdSync(), dcac);
        } else {
            loge("createDataConnection: Could not connect to dcac=" + dcac + " status=" + status);
        }

        // install reconnect intent filter for this data connection.
        //[Mr2] Need to removed and check
        /*
        IntentFilter filter = new IntentFilter();
        filter.addAction(INTENT_RECONNECT_ALARM + '.' + mGsmPhone.getMySimId() + id);
        mPhone.getContext().registerReceiver(mIntentReceiver, filter, null, mPhone);

        if (DBG) log("createDataConnection() X id=" + id + " dc=" + conn);
        return conn;*/


        if (DBG) log("createDataConnection() X id=" + id + " dc=" + conn);
        return dcac;
    }

    private void destroyDataConnections() {
        if(mDataConnections != null) {
            if (DBG) log("destroyDataConnections: clear mDataConnectionList");
            mDataConnections.clear();
        } else {
            if (DBG) log("destroyDataConnections: mDataConnecitonList is empty, ignore");
        }
    }

    /**
     * Build a list of APNs to be used to create PDP's.
     *
     * @param requestedApnType
     * @return waitingApns list to be used to create PDP
     *          error when waitingApns.isEmpty()
     */
    private ArrayList<ApnSetting> buildWaitingApns(String requestedApnType, int radioTech) {//[KK]
    //private ArrayList<ApnSetting> buildWaitingApns(String requestedApnType) {
        if (DBG) log("buildWaitingApns: E requestedApnType=" + requestedApnType);
        ArrayList<ApnSetting> apnList = new ArrayList<ApnSetting>();

        if (requestedApnType.equals(PhoneConstants.APN_TYPE_DUN)) {
            ApnSetting dun = fetchDunApn();
            if (dun != null) {
                apnList.add(dun);
                if (DBG) log("buildWaitingApns: X added APN_TYPE_DUN apnList=" + apnList);
                return apnList;
            }
        }

        if (requestedApnType.equals(PhoneConstants.APN_TYPE_DM)) {
            ArrayList<ApnSetting> dm = fetchDMApn();
            return dm;
        }

        IccRecords r = mIccRecords.get();
        String operator = (r != null) ? r.getOperatorNumeric() : "";

        //int radioTech = mPhone.getServiceState().getRilDataRadioTechnology();[KK] removed

        mTetheringExt.onBuildWaitingApns(requestedApnType, new ArrayList<IApnSetting>(apnList), operator);


        // This is a workaround for a bug (7305641) where we don't failover to other
        // suitable APNs if our preferred APN fails.  On prepaid ATT sims we need to
        // failover to a provisioning APN, but once we've used their default data
        // connection we are locked to it for life.  This change allows ATT devices
        // to say they don't want to use preferred at all.
        boolean usePreferred = true;
        try {
            usePreferred = ! mPhone.getContext().getResources().getBoolean(com.android.
                    internal.R.bool.config_dontPreferApn);
        } catch (Resources.NotFoundException e) {
            if (DBG) log("buildWaitingApns: usePreferred NotFoundException set to true");
            usePreferred = true;
        }
        if (DBG) {
            log("buildWaitingApns: usePreferred=" + usePreferred
                    + " canSetPreferApn=" + mCanSetPreferApn
                    + " mPreferredApn=" + mPreferredApn
                    + " operator=" + operator + " radioTech=" + radioTech
                    + " IccRecords r=" + r);
        }

        if (usePreferred && mCanSetPreferApn && mPreferredApn != null &&
                mPreferredApn.canHandleType(requestedApnType)) {
            if (DBG) {
                log("buildWaitingApns: Preferred APN:" + operator + ":"
                        + mPreferredApn.numeric + ":" + mPreferredApn);
            }
            if (mPreferredApn.numeric.equals(operator)) {
                if (mPreferredApn.bearer == 0 || mPreferredApn.bearer == radioTech) {
                    apnList.add(mPreferredApn);
                    if (DBG) log("buildWaitingApns: X added preferred apnList=" + apnList);
                    return apnList;
                } else {
                    if (DBG) log("buildWaitingApns: no preferred APN");
                    setPreferredApn(-1);
                    mPreferredApn = null;
                }
            } else {
                if (DBG) log("buildWaitingApns: no preferred APN");
                setPreferredApn(-1);
                mPreferredApn = null;
            }
        }
        if (mAllApnSettings != null) {
            if (DBG) log("buildWaitingApns: mAllApnSettings=" + mAllApnSettings);
            for (ApnSetting apn : mAllApnSettings) {
                if (DBG) log("buildWaitingApns: apn=" + apn);
                if (apn.canHandleType(requestedApnType)) {
                    if (apn.bearer == 0 || apn.bearer == radioTech) {
                        if (DBG) log("buildWaitingApns: adding apn=" + apn.toString());
                        apnList.add(apn);
                    } else {
                        if (DBG) {
                            log("buildWaitingApns: bearer:" + apn.bearer + " != "
                                    + "radioTech:" + radioTech);
                        }
                    }
                } else {
                if (DBG) {
                    log("buildWaitingApns: couldn't handle requesedApnType="
                            + requestedApnType);
                    }
                }
            }
        } else {
            loge("mAllApnSettings is empty!");
        }
        if (DBG) log("buildWaitingApns: X apnList=" + apnList);
        return apnList;
    }

    private String apnListToString (ArrayList<ApnSetting> apns) {
        StringBuilder result = new StringBuilder();
        for (int i = 0, size = apns.size(); i < size; i++) {
            result.append('[')
                  .append(apns.get(i).toString())
                  .append(']');
        }
        return result.toString();
    }

    private void setPreferredApn(int pos) {
        if (!mCanSetPreferApn) {
            log("setPreferredApn: X !canSEtPreferApn");
            return;
        }

        log("setPreferredApn: delete");
        ContentResolver resolver = mPhone.getContext().getContentResolver();
        if (FeatureOption.MTK_GEMINI_SUPPORT) {
            Uri geminiUri = PREFERAPN_NO_UPDATE_URI_GEMINI[mPhone.getMySimId()];
            resolver.delete(geminiUri, null, null);
        } else {
            resolver.delete(PREFERAPN_NO_UPDATE_URI, null, null);
        }

        if (pos >= 0) {
            log("setPreferredApn: insert");
            ContentValues values = new ContentValues();
            values.put(APN_ID, pos);
            if (FeatureOption.MTK_GEMINI_SUPPORT) {
                Uri geminiUri = PREFERAPN_NO_UPDATE_URI_GEMINI[mPhone.getMySimId()];
                resolver.insert(geminiUri, values);
            } else {
                resolver.insert(PREFERAPN_NO_UPDATE_URI, values);
            }
        }
    }

    private ApnSetting getPreferredApn() {
        if (mAllApnSettings.isEmpty()) {
            log("getPreferredApn: X not found mAllApnSettings.isEmpty");
            return null;
        }

        Uri queryPreferApnUri = PREFERAPN_NO_UPDATE_URI;
        if (FeatureOption.MTK_GEMINI_SUPPORT) {
            queryPreferApnUri = PREFERAPN_NO_UPDATE_URI_GEMINI[mPhone.getMySimId()];
        }
        Cursor cursor = mPhone.getContext().getContentResolver().query(
                queryPreferApnUri, new String[] { "_id", "name", "apn" },
                null, null, Telephony.Carriers.DEFAULT_SORT_ORDER);

        if (cursor != null) {
            mCanSetPreferApn = true;
            if (DBG) log("getPreferredApn: canSetPreferApn= " + mCanSetPreferApn + ",count " + cursor.getCount());
        } else {
            mCanSetPreferApn = false;
            if (DBG) log("getPreferredApn: canSetPreferApn= " + mCanSetPreferApn);
        }
        log("getPreferredApn: mRequestedApnType=" + mRequestedApnType + " cursor=" + cursor
                + " cursor.count=" + ((cursor != null) ? cursor.getCount() : 0));

        if (mCanSetPreferApn && cursor.getCount() > 0) {
            int pos;
            cursor.moveToFirst();
            pos = cursor.getInt(cursor.getColumnIndexOrThrow(Telephony.Carriers._ID));
            for(ApnSetting p: mAllApnSettings) {
                log("getPreferredApn: apnSetting=" + p);
                if (p.id == pos && p.canHandleType(mRequestedApnType)) {
                    log("getPreferredApn: X found apnSetting" + p);
                    cursor.close();
                    return p;
                }
            }
        }

        if (cursor != null) {
            cursor.close();
        }

        log("getPreferredApn: X not found");
        return null;
    }

    @Override
    public void handleMessage (Message msg) {
        if (DBG) log("handleMessage msg=" + msg);

        if (!mPhone.mIsTheCurrentActivePhone || mIsDisposed) {
            loge("handleMessage: Ignore GSM msgs since GSM phone is inactive");
            return;
        }

        switch (msg.what) {
            case DctConstants.EVENT_RECORDS_LOADED:
                onRecordsLoaded();
                break;

            case DctConstants.EVENT_DATA_CONNECTION_DETACHED:
                onDataConnectionDetached();
                break;

            case DctConstants.EVENT_DATA_CONNECTION_ATTACHED:
                onDataConnectionAttached();
                break;

            case DctConstants.EVENT_DATA_STATE_CHANGED:
                onDataStateChanged((AsyncResult) msg.obj);
                break;

            case DctConstants.EVENT_POLL_PDP:
                onPollPdp();
                break;

            case DctConstants.EVENT_DO_RECOVERY:
                doRecovery();
                break;

            case DctConstants.EVENT_APN_CHANGED:
                new Thread(new Runnable() {
                    public void run() {
                        synchronized (mCreateApnLock){
                            onApnChanged();
                        }
                    }
                }).start();                
                break;

            case DctConstants.EVENT_PS_RESTRICT_ENABLED:
                /**
                 * We don't need to explicitly to tear down the PDP context
                 * when PS restricted is enabled. The base band will deactive
                 * PDP context and notify us with PDP_CONTEXT_CHANGED.
                 * But we should stop the network polling and prevent reset PDP.
                 */
                if (DBG) log("EVENT_PS_RESTRICT_ENABLED " + mIsPsRestricted);
                stopNetStatPoll();
                stopDataStallAlarm();
                mIsPsRestricted = true;
                break;

            case DctConstants.EVENT_PS_RESTRICT_DISABLED:
                /**
                 * When PS restrict is removed, we need setup PDP connection if
                 * PDP connection is down.
                 */
	            ConnectivityManager cnnm = (ConnectivityManager)mPhone.getContext().getSystemService(
                                            Context.CONNECTIVITY_SERVICE);
                if (DBG) log("EVENT_PS_RESTRICT_DISABLED " + mIsPsRestricted);
                mIsPsRestricted  = false;

                /// M: SCRI and Fast Dormancy Feature @{
                //MTK-START [mtk04070][111205][ALPS00093395]Add for SCRI                
                startScriPoll();
                //MTK-END [mtk04070][111205][ALPS00093395]Add for SCRI
                /// @}
                
                if (isConnected()) {
                    startNetStatPoll();
                    startDataStallAlarm(DATA_STALL_NOT_SUSPECTED);
                } else {
                    // TODO: Should all PDN states be checked to fail?
                    if (mState ==DctConstants.State.FAILED) {
                        cleanUpAllConnections(false, Phone.REASON_PS_RESTRICT_ENABLED);
                        mReregisterOnReconnectFailure = false;
                    }
                    ApnContext apnContext = mApnContexts.get(PhoneConstants.APN_TYPE_DEFAULT);
                    if (apnContext != null) {
                        apnContext.setReason(Phone.REASON_PS_RESTRICT_ENABLED);
                        trySetupData(apnContext);
                    } else {
                        loge("**** Default ApnContext not found ****");
                        if (Build.IS_DEBUGGABLE && cnnm.isNetworkSupported(ConnectivityManager.TYPE_MOBILE)) {
                            throw new RuntimeException("Default ApnContext not found");
                        }
                    }
                }
                break;

            case DctConstants.EVENT_TRY_SETUP_DATA:
                if (msg.obj instanceof ApnContext) {
                    onTrySetupData((ApnContext)msg.obj);
                } else if (msg.obj instanceof String) {
                    onTrySetupData((String)msg.obj);
                } else {
                    loge("EVENT_TRY_SETUP request w/o apnContext or String");
                }
                break;

            case DctConstants.EVENT_CLEAN_UP_CONNECTION:
                boolean tearDown = (msg.arg1 == 0) ? false : true;
                if (DBG) log("EVENT_CLEAN_UP_CONNECTION tearDown=" + tearDown);
                if (msg.obj instanceof ApnContext) {
                    cleanUpConnection(tearDown, (ApnContext)msg.obj);
                } else {
                    loge("EVENT_CLEAN_UP_CONNECTION request w/o apn context");
                    //[mr2] added
                    super.handleMessage(msg);
                }
                break;

            /// M: SCRI and Fast Dormancy Feature @{    
            //MTK-START [mtk04070][111205][ALPS00093395]Add for SCRI                
            case DctConstants.EVENT_SCRI_RESULT:
                logd("[SCRI]EVENT_SCRI_RESULT");
                handleScriResult((AsyncResult) msg.obj);
                break;
  
            case DctConstants.EVENT_SCRI_RETRY_TIMER:
                logd("[SCRI]EVENT_SCRI_RETRY_TIMER");
                //[Begin]Solve [ALPS00239224]Failed to send MMS, mtk04070, 20120227.
                if (mScriManager.isDataTransmitting()) {
                    logd("[SCRI]Data is transmitting, cancel retry mechanism.");
                    mScriManager.mScriRetryCounter = 0;
                    mScriManager.setScriState(ScriManager.STATE_ACTIVATED);
                    mScriManager.setScriDataCount(true);
                    mScriManager.setPsSessionStatus(true);
                }
                else {
                    sendScriCmd(true);
                }	
                //[End]Solve [ALPS00239224]Failed to send MMS, mtk04070, 20120227.
                break;

            case DctConstants.EVENT_SCRI_CMD_RESULT:
                logd("[SCRI]EVENT_SCRI_CMD_RESULT");
                AsyncResult ar= (AsyncResult) msg.obj;
                if(ar.exception != null) {
                   logd("command error in +ESCRI");
                   mScriManager.setScriState(ScriManager.STATE_ACTIVATED);
                }
                break;
                
            case DctConstants.EVENT_NW_RAT_CHANGED:
                logd("[SCRI]EVENT_NW_RAT_CHANGED");
                Integer rat = (Integer) ((AsyncResult) msg.obj).result;                
                int result = mScriManager.getScriNwType(rat.intValue());
                
                switch(result){
                    case ScriManager.SCRI_3G:
                        /// M: Fast Dormancy:Fix InterRAT problem ALPS00364331 @{
                        logd("[SCRI] InterRAT to 3G, Set mIsUmtsMode as true before startScriPoll()");
                        mIsUmtsMode = true;
                        startScriPoll();
                        //mIsUmtsMode = true;
                        /// @}
                        break;
                    case ScriManager.SCRI_2G:
                        stopScriPoll();
                        mIsUmtsMode = false;
                        break;
                    case ScriManager.SCRI_NONE:
                        break;                    
                    }
                break;
            //MTK-END [mtk04070][111205][ALPS00093395]Add for SCRI                
            /// @}
            case MSG_RESTART_RADIO_OFF_DONE:
                Rlog.i(LOG_TAG,"MSG_RESTART_RADIO_OFF_DONE");
                int simId = mPhone.getMySimId();
                if (PhoneFactory.isDualTalkMode()) {
                    // As Dual Talk mode, just set radio mode via current phone.
                    mPhone.mCi.setRadioMode(GeminiNetworkSubUtil.MODE_SIM1_ONLY, obtainMessage(MSG_RESTART_RADIO_ON_DONE));
                } else {
                    GeminiPhone mGeminiPhone = (GeminiPhone)PhoneFactory.getDefaultPhone();
                    int sim3G = mGeminiPhone.get3GSimId();
                    if (sim3G == simId) {
                        mPhone.mCi.setRadioMode(msg.arg1, obtainMessage(MSG_RESTART_RADIO_ON_DONE));
                    } else {
                        log("set radio on through peer phone(" + sim3G + ") since current phone is 2G protocol");
                        if (mPhone instanceof GSMPhone) {
                            GSMPhone peerPhone = ((GSMPhone)mPhone).getPeerPhones(sim3G);
                            ((PhoneBase)peerPhone).mCi.setRadioMode(msg.arg1, obtainMessage(MSG_RESTART_RADIO_ON_DONE));
                        }
                    }
                }
                break;
            case MSG_RESTART_RADIO_ON_DONE:
                Rlog.i(LOG_TAG,"MSG_RESTART_RADIO_ON_DONE");
                break;
            case DctConstants.EVENT_PS_RAT_CHANGED:
                // RAT change is only nofity active APNs in GsmServiceStateTracker
                // Here notify "off" APNs for RAT change.
                logd("EVENT_PS_RAT_CHANGED");
                notifyOffApnsOfAvailability(Phone.REASON_NW_TYPE_CHANGED);
                break;
            case DctConstants.EVENT_GET_AVAILABLE_NETWORK_DONE:
                logd("EVENT_GET_AVAILABLE_NETWORK_DONE");
                setupDataOnConnectableApns(Phone.REASON_PS_RESTRICT_DISABLED);
                break;
            case DctConstants.EVENT_VOICE_CALL_STARTED_PEER:
                logd("EVENT_VOICE_CALL_STARTED_PEER");
                onVoiceCallStarted();
                break;
            case DctConstants.EVENT_VOICE_CALL_ENDED_PEER:
                logd("EVENT_VOICE_CALL_ENDED_PEER");
                onVoiceCallEndedPeer();
                break;
            case DctConstants.EVENT_RESET_PDP_DONE:
                logd("EVENT_RESET_PDP_DONE cid=" + msg.arg1);
                break;

            //[RB release workaround]
            case DctConstants.EVENT_SET_PACKETS_FLUSH:
                int cid = ((int[])(((AsyncResult)msg.obj).result))[0];
                logd("EVENT_SET_PACKETS_FLUSH cid=" + cid);
                onPacketsFlush(cid);
                break;
            //[RB release workaround]
//CSD
            case DctConstants.EVENT_SETUP_DATA_CONNECTION_DONE:
                logd("EVENT_SETUP_DATA_CONNECTION_DONE");
                onDialUpCsdDone((AsyncResult)msg.obj);
                break;
                
            case DctConstants.EVENT_RAC_UPDATED:
                //[sm_cause]
                if (mGsmDCTExt.needRacUpdate()){
                    logd("EVENT_RAC_UPDATED");
                    mReregisterOnReconnectFailure = false;
                    setupDataOnConnectableApns(Phone.REASON_PS_RESTRICT_DISABLED);
                }
                break;             

            default:
                // handle the message in the super class DataConnectionTracker
                super.handleMessage(msg);
                break;
        }
    }

    //[RB release workaround]
    protected void onPacketsFlush(int cid) {
        DcAsyncChannel dcac = findDataConnectionAcByCid(cid);
        if (dcac != null) {
            ApnContext apnContext = mApnContexts.get(PhoneConstants.APN_TYPE_MMS);
            if ((apnContext != null) &&
                (apnContext.getState() == DctConstants.State.CONNECTED) &&
                (apnContext.getDcAc() == dcac)) {
                Intent intent = new Intent(DctConstants.ACTION_SET_PACKETS_FLUSH);
                mPhone.getContext().sendBroadcast(intent);
                return;
            }
        }

        logd("onPacketsFlush() not found, cid=" + cid);
    }
    //[RB release workaround]

    protected int getApnProfileID(String apnType) {
        if (TextUtils.equals(apnType, PhoneConstants.APN_TYPE_IMS)) {
            return RILConstants.DATA_PROFILE_IMS;
        } else if (TextUtils.equals(apnType, PhoneConstants.APN_TYPE_FOTA)) {
            return RILConstants.DATA_PROFILE_FOTA;
        } else if (TextUtils.equals(apnType, PhoneConstants.APN_TYPE_CBS)) {
            return RILConstants.DATA_PROFILE_CBS;
        //[KK] added
        } else if (TextUtils.equals(apnType, PhoneConstants.APN_TYPE_IA)) {
            return RILConstants.DATA_PROFILE_DEFAULT; // DEFAULT for now
        } else if (TextUtils.equals(apnType, PhoneConstants.APN_TYPE_DUN)) {
            return RILConstants.DATA_PROFILE_TETHERED;            
        //[KK] end
        } else if (TextUtils.equals(apnType, PhoneConstants.APN_TYPE_MMS)) {
            return RILConstants.DATA_PROFILE_MTK_MMS;
        } else if (TextUtils.equals(apnType, PhoneConstants.APN_TYPE_DEFAULT)) {
            return RILConstants.DATA_PROFILE_MTK_DEFAULT;
        } else {
            return RILConstants.DATA_PROFILE_DEFAULT;
        }
    }

    private int getCellLocationId() {
        int cid = -1;
        CellLocation loc = mPhone.getCellLocation();

        if (loc != null) {
            if (loc instanceof GsmCellLocation) {
                cid = ((GsmCellLocation)loc).getCid();
            } else if (loc instanceof CdmaCellLocation) {
                cid = ((CdmaCellLocation)loc).getBaseStationId();
            }
        }
        return cid;
    }

    @Override
    protected void onUpdateIcc() {
        if (mUiccController == null ) {
            return;
        }

        // VIA PATCH START
        log("onUpdateIcc: PhoneType : " + mPhone.getPhoneType());
        IccRecords newIccRecords = mUiccController.getIccRecords(mPhone.getPhoneType() == PhoneConstants.PHONE_TYPE_CDMA 
                                                                                                ? UiccController.APP_FAM_3GPP2 : UiccController.APP_FAM_3GPP);
        // VIA PATCH END

        IccRecords r = mIccRecords.get();
        if (r != newIccRecords) {
            if (r != null) {
                log("Removing stale icc objects.");
                r.unregisterForRecordsLoaded(this);
                mIccRecords.set(null);
            }
            if (newIccRecords != null) {
                log("New records found");
                mIccRecords.set(newIccRecords);
                newIccRecords.registerForRecordsLoaded(
                        this, DctConstants.EVENT_RECORDS_LOADED, null);
            }
        }
    }

    /* Add by vendor for Multiple PDP Context */
    private boolean isSameProxy(ApnSetting apn1, ApnSetting apn2){
        if (apn1 == null || apn2 == null){
            return false;
        }
        String proxy1;
        if (apn1.canHandleType(PhoneConstants.APN_TYPE_MMS)){
            proxy1 = apn1.mmsProxy;
        }else{
            proxy1 = apn1.proxy;
        }
        String proxy2;
        if (apn2.canHandleType(PhoneConstants.APN_TYPE_MMS)){
            proxy2 = apn2.mmsProxy;
        }else{
            proxy2 = apn2.proxy;
        }
        /* Fix NULL Pointer Exception problem: proxy1 may be null */ 
        if (proxy1 != null && proxy2 != null && !proxy1.equals("") && !proxy2.equals(""))
            return proxy1.equalsIgnoreCase(proxy2);
        else {
            logd("isSameProxy():proxy1=" + proxy1 + ",proxy2=" + proxy2);
            return false;
        }
    }	

    private boolean enableWaitingApn() {
        boolean ret = false;
        Iterator<String> iterWaitingApn = mWaitingApnList.iterator();

        if (DBG) logd("Reconnect waiting APNs if have.");
        while (iterWaitingApn.hasNext()) {
             enableApnType(iterWaitingApn.next());
             ret = true;
        }
        mWaitingApnList.clear();            
        return ret;
    }

    private void clearWaitingApn() {
        Iterator<String> iterWaitingApn = mWaitingApnList.iterator();

        if (DBG) logd("Reconnect waiting APNs if have.");
        while (iterWaitingApn.hasNext()) {
             mPhone.notifyDataConnection(Phone.REASON_APN_FAILED, iterWaitingApn.next());
        }
        mWaitingApnList.clear();  
    }

    public void gprsDetachResetAPN() {
        // To ensure all context are reset since GPRS detached.

        //[Mr2] TODO: check
        /*
        for (ApnContext apnContext : mApnContexts.values()) {
            if (DBG) logd("Reset APN since GPRS detached [" + apnContext.getApnType() + "]");
            DataConnection dataConnection = apnContext.getDataConnection();
            if (dataConnection != null) {
                DctConstants.State state = apnContext.getState();
                if (state == DctConstants.State.CONNECTED || state == DctConstants.State.CONNECTING) {
                    Message msg = obtainMessage(DctConstants.EVENT_DISCONNECT_DONE, apnContext);
                    dataConnection.tearDown(Phone.REASON_DATA_DETACHED, msg);
                }
            }
            apnContext.setState(DctConstants.State.IDLE);
            apnContext.setApnSetting(null);
            //apnContext.setDataConnection(null);
            apnContext.setDataConnectionAc(null);
        }*/
    }


    private boolean isDomesticRoaming() {
        boolean roaming = mPhone.getServiceState().getRoaming();
        String operatorNumeric = mPhone.getServiceState().getOperatorNumeric();
        String imsi = mPhone.getSubscriberId();        
        boolean sameMcc = false;
        try {
            sameMcc = imsi.substring(0, 3).equals(operatorNumeric.substring(0, 3));
        } catch (Exception e) {
        }
        
        if (DBG) logd("getDataOnRoamingEnabled(): roaming=" + roaming + ", sameMcc=" + sameMcc);

        return (roaming && sameMcc);
    }

    @Override
    public boolean getDataOnRoamingEnabled() {
        return (super.getDataOnRoamingEnabled() ||
                (isDomesticRoaming() && mGsmDCTExt.isDomesticRoamingEnabled()));    
    }

    @Override
    protected boolean isDataAllowedAsOff(String apnType) {
        return mGsmDCTExt.isDataAllowedAsOff(apnType);
    }

    //[mr2] TODO: removed
    /*
    public void abortDataConnection() {
        if (DBG) logd("abortDataConnection()");
        for (DataConnection dc : mDataConnections.values()) {
            dc.removeConnectMsg();
        }
    }*/
    //MTK-END [mtk04070][111205][ALPS00093395]MTK proprietary methods/classes/receivers


    @Override
    protected void log(String s) {
        if (FeatureOption.MTK_GEMINI_SUPPORT) {
            Rlog.d(LOG_TAG, "[DCT][simId" + mLogSimId + "]"+ s);
        } else {
            Rlog.d(LOG_TAG, "[DCT] " + s);
        }
    }
    

    protected void logi(String s) {
        if (FeatureOption.MTK_GEMINI_SUPPORT) {
            Rlog.i(LOG_TAG, "[DCT][simId" + mLogSimId + "]"+ s);
        } else {
            Rlog.i(LOG_TAG, "[DCT] " + s);
        }
    }

    protected void logw(String s) {
        if (FeatureOption.MTK_GEMINI_SUPPORT) {
            Rlog.w(LOG_TAG, "[DCT][simId" + mLogSimId + "]"+ s);
        } else {
            Rlog.w(LOG_TAG, "[DCT] " + s);
        }
    }
    @Override
    protected void loge(String s) {
        if (FeatureOption.MTK_GEMINI_SUPPORT) {
            Rlog.e(LOG_TAG, "[DCT][simId" + mLogSimId + "]"+ s);
        } else {
            Rlog.e(LOG_TAG, "[DCT] " + s);
        }
    }

    @Override
    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        pw.println("DataConnectionTracker extends:");
        super.dump(fd, pw, args);
        pw.println(" mReregisterOnReconnectFailure=" + mReregisterOnReconnectFailure);
        pw.println(" canSetPreferApn=" + mCanSetPreferApn);
        pw.println(" mApnObserver=" + mApnObserver);
        pw.println(" getOverallState=" + getOverallState());
        pw.println(" mDataConnectionAsyncChannels=%s\n" + mDataConnectionAcHashMap);
        pw.println(" mAttached=" + mAttached.get());//[KK]
    }

    /// M: SCRI and Fast Dormancy Feature @{
    //MTK-START [mtk04070][111205][ALPS00093395]MTK proprietary methods/classes/receivers 
    void registerSCRIEvent(GSMPhone p) {
        if (FeatureOption.MTK_FD_SUPPORT) {
            mScriManager = new ScriManager();
            
            if(Settings.System.getInt(p.getContext().getContentResolver(), Settings.System.GPRS_TRANSFER_SETTING, 0) == 1){
                mIsCallPrefer = true;
            }else{
                mIsCallPrefer = false;
            }
                                    
            mScriManager.reset();
            p.mCi.setScriResult(this, DctConstants.EVENT_SCRI_RESULT, null);  //Register with unsolicated result            
            ((GsmServiceStateTracker)p.getServiceStateTracker()).registerForRatRegistrants(this, DctConstants.EVENT_NW_RAT_CHANGED, null);
            //p.mSST.registerForRatRegistrants(this, DctConstants.EVENT_NW_RAT_CHANGED, null);            

            IntentFilter filter = new IntentFilter();
            //Add for SCRI by MTK03594
            filter.addAction(TelephonyIntents.ACTION_SIM_STATE_CHANGED);
            filter.addAction(TelephonyIntents.ACTION_GPRS_TRANSFER_TYPE);

            //[ALPS00098656][mtk04070]Disable Fast Dormancy when in Tethered mode
            filter.addAction(ConnectivityManager.ACTION_TETHER_STATE_CHANGED);

            // TODO: Why is this registering the phone as the receiver of the intent
            //       and not its own handler?
            p.getContext().registerReceiver(mIntentReceiverScri, filter, null, p);
               
            //[ALPS00098656][mtk04070]Disable Fast Dormancy when in Tethered mode
            /* Get current tethered mode */
            ConnectivityManager connMgr = (ConnectivityManager) mPhone.getContext().getSystemService(Context.CONNECTIVITY_SERVICE);
            if ((connMgr != null) && (connMgr.getTetheredIfaces() != null))
            { 
               mIsTetheredMode = (connMgr.getTetheredIfaces().length > 0);
               logd("[GsmDataConnectionTracker Constructor]mIsTetheredMode = " + mIsTetheredMode + "mChargingMode=" + mChargingMode);    
               //[New R8 modem FD]
               if((!mIsTetheredMode) && (!mChargingMode)) {
                   updateFDMDEnableStatus(true);
               } else {
                   updateFDMDEnableStatus(false);
               }  
            }
        }
    }
    
    //Add for SCRI design
    //Function to start SCRI polling service
    protected void startScriPoll(){        
        if(FeatureOption.MTK_FD_SUPPORT) {
            if(DBG) logd("[SCRI] startScriPoll (" + scriPollEnabled + "," + mScriManager.getScriState() + "," + mIsUmtsMode + ")");
            //[New R8 modem FD]
            int FD_MD_Enable_Mode = SystemProperties.getInt(PROPERTY_RIL_FD_MODE, 0);
            if(DBG) logd("[SCRI] startScriPoll:FD_MD_Enable_Mode:" + FD_MD_Enable_Mode);
            if(scriPollEnabled == false && mIsUmtsMode && isConnected() && FD_MD_Enable_Mode == 0) {
                if(mScriManager.getScriState() == ScriManager.STATE_NONE) {
                     scriPollEnabled = true;                     
                     mPollScriStat.run();
                     mScriManager.setPsSessionStatus(true);
                     mScriManager.setScriState(ScriManager.STATE_ACTIVATED);
               }
            }
       }
        
    }

    //Function to stop SCRI polling service
    protected void stopScriPoll()
    {                            
        if(FeatureOption.MTK_FD_SUPPORT && (mScriManager.getScriState() != ScriManager.STATE_NONE)) {
            if(DBG) logd("[SCRI]stopScriPoll");
            mScriManager.reset();
            scriPollEnabled = false;
            mScriManager.setScriState(ScriManager.STATE_NONE);
            mScriManager.setPsSessionStatus(false);
            mDataConnectionTracker.removeMessages(DctConstants.EVENT_SCRI_RETRY_TIMER);
            //[mr2] TODO: check
            //removeCallbacks(mPollScriStat);
        }
    }
    
    protected void handleScriResult(AsyncResult ar){
        Integer scriResult = (Integer)(ar.result);
        if(DBG) logd("[SCRI] handleScriResult :" + scriResult);
        
        if (ar.exception == null) {
            if (scriResult == ScriManager.SCRI_RESULT_REQ_SENT || 
                scriResult == ScriManager.SCRI_NO_PS_DATA_SESSION ||
                scriResult == ScriManager.SCRI_NOT_ALLOWED) {      //[ALPS00097617][mtk04070]Handle SCRI_NOT_ALLOWED event
                mScriManager.setScriState(ScriManager.STATE_ACTIVATED);
                mScriManager.setPsSessionStatus(false);
                if (scriResult == ScriManager.SCRI_RESULT_REQ_SENT || scriResult == ScriManager.SCRI_NO_PS_DATA_SESSION) {
                    if (mScriManager.mFirstESCRIRAUFollowOnProceed) {                 	
                        mScriManager.mFirstESCRIRAUFollowOnProceed = false;
                        logd("1st AT+ESCRI=1 for RAUFollowOnProceed is sent to modem successfully");
                    }
                }
            }
            //Add by mtk01411 to handle RAU with FollowOnProceed and RRC connected scenario
            else if(scriResult == ScriManager.SCRI_RAU_ENABLED) {
                if(DBG) logd("[SCRI] RAU with FollowOnProceed: RRC in connected state,scriState=" + mScriManager.getScriState());	
                mScriManager.mPeriodicRAUFollowOnProceedEnable = true; 
                mScriManager.mFirstESCRIRAUFollowOnProceed = true; 
            
            }else{
                if (DBG) logd("[SCRI] mScriManager.retryCounter :" + mScriManager.mScriRetryCounter);
                mScriManager.setPsSessionStatus(false);
                if (mScriManager.mScriRetryCounter < ScriManager.SCRI_MAX_RETRY_COUNTER) {
                    mScriManager.setScriState(ScriManager.STATE_RETRY);

                    if(mIsScreenOn) {
                        mScriManager.mScriRetryTimer = mScriManager.mScriTriggerDataCounter * 1000;
                    } else {
                        mScriManager.mScriRetryTimer = mScriManager.mScriTriggerDataOffCounter * 1000;
                    }

                    if(mScriManager.mScriRetryTimer > ScriManager.SCRI_MAX_RETRY_TIMERS) {
                        mScriManager.mScriRetryTimer = ScriManager.SCRI_MAX_RETRY_TIMERS;
                    }

                    mScriManager.mScriRetryCounter++;
                    Message msg = mDataConnectionTracker.obtainMessage(DctConstants.EVENT_SCRI_RETRY_TIMER, null);
                    mDataConnectionTracker.sendMessageDelayed(msg, mScriManager.mScriRetryTimer);
                    logd("[SCRI] Retry counter = " + mScriManager.mScriRetryCounter + ", timeout = " + mScriManager.mScriRetryTimer);
                } else {
                    //No retry
                    mScriManager.mScriRetryCounter = 0;
                    mScriManager.setScriState(ScriManager.STATE_ACTIVATED);
                }
            }
        } else {
            mScriManager.setScriState(ScriManager.STATE_RETRY);
            Message msg = mDataConnectionTracker.obtainMessage(DctConstants.EVENT_SCRI_RETRY_TIMER, null);
            mDataConnectionTracker.sendMessageDelayed(msg, ScriManager.SCRI_MAX_RETRY_TIMERS);
        }
    }

    protected void sendScriCmd(boolean retry) {
        try{
            if((mScriManager.getPsSessionStatus() || retry) && 
               (mPhone.getState() == PhoneConstants.State.IDLE) && 
               mIsUmtsMode && 
               (!mIsTetheredMode) && (!mChargingMode))   //[ALPS00098656][mtk04070]Disable Fast Dormancy when in Tethered mode
            {
                logd("[SCRI] Send SCRI command:" + mIsCallPrefer + ":" + retry);
                if(!mIsScreenOn) {
                    mPhone.mCi.setScri(true, obtainMessage(DctConstants.EVENT_SCRI_CMD_RESULT));
                    mScriManager.setScriState(ScriManager.STATE_ACTIVIATING);
                }else{
                    boolean forceFlag = false;
                    
                    //Send SCRI with force flag when the data prefer is on and both sims are on
                    if(FeatureOption.MTK_GEMINI_SUPPORT && !mIsCallPrefer){
                        GeminiPhone mGeminiPhone = (GeminiPhone)PhoneFactory.getDefaultPhone();
                        for (int peerSimId=PhoneConstants.GEMINI_SIM_1; peerSimId<PhoneConstants.GEMINI_SIM_NUM; peerSimId++) {
                            if(peerSimId != mPhone.getMySimId() &&
                                    mGeminiPhone.isRadioOnGemini(peerSimId)){
                                forceFlag = true;
                                break;
                            }
                        }
                    }
                    
                    //Only for operator (not CMCC) have the chance to set forceFlag as true when SCREEN is ON 
                    forceFlag = mGsmDCTExt.getFDForceFlag(forceFlag);
                    
                    //Send SCRI with force flag as TRUE when receiving the RAU with FollowOnProceed (+ESCRI:6)
                    if (mScriManager.mFirstESCRIRAUFollowOnProceed) {
                        forceFlag = true;
                        logd("[SCRI]Screen ON: but RAUFollowOnProceed sets forceFlag as true");
                    }

                    logd("[SCRI]Screen ON: send AT+ESCRI with forceFlag=" + forceFlag);                    
                    mPhone.mCi.setScri(forceFlag, obtainMessage(DctConstants.EVENT_SCRI_CMD_RESULT));
                    mScriManager.setScriState(ScriManager.STATE_ACTIVIATING);
                                        
                }
            } else {
                logd("[SCRI] Ingore SCRI command due to (" + mScriManager.getPsSessionStatus() + ";" + mPhone.getState() + ";" + ")");
                logd("[SCRI] mIsUmtsMode = " + mIsUmtsMode);
                logd("[SCRI] mIsTetheredMode = " + mIsTetheredMode);
                    mScriManager.setScriState(ScriManager.STATE_ACTIVATED);                
                }            
        }catch(Exception e){
           e.printStackTrace();
        }
    }
    //ADD_END for SCRI

    BroadcastReceiver mIntentReceiverScri = new BroadcastReceiver ()
    {
        @Override
        public void onReceive(Context context, Intent intent)
        {
            String action = intent.getAction();
            /*
               Some actions are handled in DataConnectionTracker.java
            */
            if(action.equals(TelephonyIntents.ACTION_SIM_STATE_CHANGED)){  //add by MTK03594 for SCRI
                if (FeatureOption.MTK_GEMINI_SUPPORT && FeatureOption.MTK_FD_SUPPORT) {
                    //Check SIM2 state during data prefer condition
                }
            } else if(action.equals(TelephonyIntents.ACTION_GPRS_TRANSFER_TYPE)) {
                int gprsTransferType = intent.getIntExtra(Phone.GEMINI_GPRS_TRANSFER_TYPE, 0);
                logd("GPRS Transfer type:" + gprsTransferType);
                if(gprsTransferType == 1) {
                    mIsCallPrefer = true;
                } else {
                    mIsCallPrefer = false;
                }
            } else if(action.equals(ConnectivityManager.ACTION_TETHER_STATE_CHANGED)) {
                //[ALPS00098656][mtk04070]Disable Fast Dormancy when in Tethered mode
                logd("Received ConnectivityManager.ACTION_TETHER_STATE_CHANGED");
                ArrayList<String> active = intent.getStringArrayListExtra(ConnectivityManager.EXTRA_ACTIVE_TETHER);            	
                mIsTetheredMode = ((active != null) && (active.size() > 0));
                logd("[TETHER_STATE_CHANGED]mIsTetheredMode = " + mIsTetheredMode + "mChargingMode=" + mChargingMode);
                //[New R8 modem FD]
                if((!mIsTetheredMode) && (!mChargingMode)) {
                    updateFDMDEnableStatus(true);
                } else {
                    updateFDMDEnableStatus(false);	
                }
            }
        }/* End of onReceive */
    };
    


    private class ScriManager{
        protected static final boolean DBG = true;
        
        static public final int STATE_NONE=0;
        static public final int STATE_ACTIVIATING=1;
        static public final int STATE_ACTIVATED=2;
        static public final int STATE_RETRY=3;

        static public final int SCRI_NONE = 0;
        static public final int SCRI_3G = 1;
        static public final int SCRI_2G = 2;

        static public final int SCRI_RESULT_REQ_SENT = 0;
        static public final int SCRI_CS_SESSION_ONGOING = 1;
        static public final int SCRI_PS_SIGNALLING_ONGOING = 2;
        static public final int SCRI_NO_PS_DATA_SESSION = 3;
        static public final int SCRI_REQ_NOT_SENT = 4; 
        //[ALPS00097617][mtk04070]Refine handleScriResult function to handle SCRI_NOT_ALLOWED 
        static public final int SCRI_NOT_ALLOWED = 5; 
        //Add by mtk01411 to handle RAU with FollowOnProceed and RRC connected scenario
        static public final int SCRI_RAU_ENABLED = 6; 
        
        static public final int SCRI_MAX_RETRY_COUNTER = 3;
        static public final int SCRI_MAX_RETRY_TIMERS = 30 * 1000;
        
        public int mScriGuardTimer;
        public int mScriPollTimer;
        public int mScriTriggerDataCounter;
        public int mScriTriggerDataOffCounter;
        public int mScriRetryTimer;
        public int mScriRetryCounter;

        //Add by mtk01411 to handle RAU with FollowOnProceed and RRC connected scenario
        public boolean mPeriodicRAUFollowOnProceedEnable = false;
        public boolean mFirstESCRIRAUFollowOnProceed = false;
        private boolean mScriNeeded;
        private boolean mPsSession;
        private boolean mGuardTimerExpired;
        private int mScriState;
        private int mScriDataCounter;
        private int mScriAddCounter;
        private int mNwType;
        
        protected final String LOG_TAG = "GSM";
            
        public ScriManager(){
            mScriGuardTimer = 0;
            mScriPollTimer = 0;
            mScriDataCounter = 0;
            mScriRetryTimer = 0;            
            mScriAddCounter = 0;
            mScriTriggerDataCounter = 0;
            mScriTriggerDataOffCounter = 0;
            mScriRetryCounter = 0;
            mPsSession = false;
            
            mScriNeeded = false;
            mGuardTimerExpired = false;
            mScriState = STATE_NONE;

            mNwType = SCRI_NONE;
        }

        public void setScriTimer()
        {
            String  str = null;
            Integer val = 0;  

            try {
                //Get scri guard timer
                str = SystemProperties.get("persist.radio.fd.guard.timer", "60");
                val = Integer.parseInt(str);
                if(val < 5 || val > 3600) val = 60;
                mScriGuardTimer = val * 1000;            

                //Get scri poll timer
                str = SystemProperties.get("persist.radio.fd.poll.timer", "5");
                val = Integer.parseInt(str);
                if(val <= 0 || val > 600) val = 5;
                mScriAddCounter = val;
                mScriPollTimer = val * 1000;

                //Get scri data counter for screen on
                str = SystemProperties.get("persist.radio.fd.counter", "20");
                val = Integer.parseInt(str);
                if(val < 5 || val > 3600) val = 20;
                mScriTriggerDataCounter = val;
            
                //Get scri data counter for screen off
                str = SystemProperties.get("persist.radio.fd.off.counter", "20");
                val = Integer.parseInt(str);
                if(val < 5 || val > 3600) val = 20;
                mScriTriggerDataOffCounter = val;

                //Get scri retry timer
                str = SystemProperties.get("persist.radio.fd.retry.timer", "20");
                val = Integer.parseInt(str);
                if(val < 5 || val > 600) val = 20;
                mScriRetryTimer = val * 1000;
            
                if (DBG) Rlog.d(LOG_TAG, "[SCRI] init value (" + mScriGuardTimer + "," + mScriPollTimer + ","+ mScriTriggerDataCounter + "," + mScriTriggerDataOffCounter + "," + mScriRetryTimer + ")");
            } catch (Exception e) {
                        e.printStackTrace();
                        mScriGuardTimer = 60 * 1000;
                        mScriPollTimer = 5 * 1000;
                        mScriTriggerDataCounter = 20;
                        mScriTriggerDataOffCounter = 20;
                        mScriRetryTimer = 20 * 1000;
                        mScriAddCounter = 5;
            }/* End of try-catch */
        }

        public void reset(){
            mScriNeeded = false;
            mGuardTimerExpired = false;
            mPsSession = false;
            mScriRetryCounter = 0;
            mScriState = STATE_NONE;
            mScriDataCounter = 0;
            mScriAddCounter = mScriPollTimer/1000;
            setScriTimer();
        }
            
        public void setScriState(int scriState){
            mScriState = scriState;
        }

        public int getScriState(){
            return mScriState;
        }

        public void setPsSessionStatus(boolean hasPsSession) {
            if(hasPsSession) {
                mScriRetryCounter = 0;
            }
           mPsSession = hasPsSession;
        }

        public boolean getPsSessionStatus() {
           return mPsSession;
        }
        
        public void setScriDataCount(boolean reset){
            if(reset == false){
                mScriDataCounter+=mScriAddCounter;
            }else{
                mScriDataCounter = 0;
            }
            if(DBG) Rlog.d(LOG_TAG, "[SCRI]setScriDataCount:" + mScriDataCounter);
        }

        public boolean isPollTimerTrigger(boolean isScreenOn) {
            if (isScreenOn) {
               return mScriDataCounter >= mScriTriggerDataCounter;
            } else {
               return mScriDataCounter >= mScriTriggerDataOffCounter;
            }
        }

        public int getScriNwType(int networktype){
            if(DBG) Rlog.d(LOG_TAG, "[SCRI]getScriNwType:" + networktype);
            int nwType = 0;
            
            if(networktype >= TelephonyManager.NETWORK_TYPE_UMTS){
                nwType = SCRI_3G;
            }else if(networktype == TelephonyManager.NETWORK_TYPE_GPRS || networktype == TelephonyManager.NETWORK_TYPE_EDGE){
                nwType = SCRI_2G;
            }else{
                nwType = SCRI_NONE;
            }

            //Only consider 2G -> 3G & 3G -> 2G
            if(nwType != SCRI_NONE && mNwType != nwType)
            {
               mNwType = nwType;
            }else{
               nwType = SCRI_NONE;
            }
            
            return nwType;
        }

        public boolean isDataTransmitting() {
            long deltaTx, deltaRx;
            long preTxPkts = scriTxPkts, preRxPkts = scriRxPkts;

           if(PhoneFactory.isDualTalkMode()) {
               TxRxSum curTxRxSum = new TxRxSum();
   
               curTxRxSum.updateTxRxSum();
               scriTxPkts = curTxRxSum.txPkts;
               scriRxPkts = curTxRxSum.rxPkts;
           } else {
               scriTxPkts = TrafficStats.getMobileTxPackets();
               scriRxPkts = TrafficStats.getMobileRxPackets();
           }

            Rlog.d(LOG_TAG, "[SCRI]tx: " + preTxPkts + " ==> " + scriTxPkts);
            Rlog.d(LOG_TAG, "[SCRI]rx  " + preRxPkts + " ==> " + scriRxPkts);

            deltaTx = scriTxPkts - preTxPkts;
            deltaRx = scriRxPkts - preRxPkts;
            Rlog.d(LOG_TAG, "[SCRI]delta rx " + deltaRx + " tx " + deltaTx);

            return (deltaTx > 0 || deltaRx > 0);
        }
    }

    private Runnable mPollScriStat = new Runnable(){
        public void run() {
            boolean resetFlag = false;

            resetFlag = mScriManager.isDataTransmitting();

            //Add by mtk01411 to handle RAU with FollowOnProceed and RRC connected scenario
            if (mScriManager.mPeriodicRAUFollowOnProceedEnable) {
                logd("[SCRI] Detect RAU FollowOnProceed:Force to let resetFlag as true (regard PS session exist)");
                resetFlag = true;
                mScriManager.mPeriodicRAUFollowOnProceedEnable = false;	
            }

            if (mScriManager.getScriState() == ScriManager.STATE_ACTIVATED || mScriManager.getScriState() == ScriManager.STATE_RETRY) {
                logd("[SCRI]act:" + resetFlag);
            
                if (resetFlag){
                    mScriManager.setPsSessionStatus(true);
                    //Disable retry command due to data transfer
                    if (mScriManager.getScriState() == ScriManager.STATE_RETRY) {
                        mDataConnectionTracker.removeMessages(DctConstants.EVENT_SCRI_RETRY_TIMER);
                        mScriManager.setScriState(ScriManager.STATE_ACTIVATED);
                    }
                }
                
                mScriManager.setScriDataCount(resetFlag);
                if (mScriManager.isPollTimerTrigger(mIsScreenOn))
                {
                    mScriManager.setScriDataCount(true);      
                    sendScriCmd(false);
                }
            }

            logd("mPollScriStat");
            if (scriPollEnabled) {
               mDataConnectionTracker.postDelayed(this, mScriManager.mScriPollTimer);
           }
        }/* End of run() */

    };
    /// @}

    /* Add by vendor for Multiple PDP Context used in notify overall data state scenario */
    //@Override
    //public String getActiveApnType() {
        // TODO Auto-generated method stub
        /* Note by mtk01411: Currently, this API is invoked by DefaultPhoneNotifier::notifyDataConnection(sender, reason) */
        /* => Without specifying the apnType: In this case, it means that report the overall data state */
        /* Return the null for apnType to query overall data state */
    //    return null;
    //}

    private boolean isCuImsi(String imsi){

        if(imsi != null){
           int mcc = Integer.parseInt(imsi.substring(0,3));
           int mnc = Integer.parseInt(imsi.substring(3,5));
                  
           logd("mcc mnc:" + mcc +":"+ mnc);

            if(mcc == 460 && mnc == 01){
               return true;
            }
            
            if (mcc == 001) {
                return true;
            }
       }

       return false;
  }

    private ArrayList<ApnSetting> fetchDMApn() {
        IccRecords r = mIccRecords.get();
        String operator = (r != null) ? r.getOperatorNumeric() : "";
        /* Add by mtk01411 */
        logd("fetchDMApn():operator=" + operator);
        if (operator != null) {
            String selection = "numeric = '" + operator + "'";
            Cursor dmCursor = null;
            if (FeatureOption.MTK_GEMINI_SUPPORT) {
                if (mPhone.getMySimId() == PhoneConstants.GEMINI_SIM_1) {
                    dmCursor = mPhone.getContext().getContentResolver().query(
                                   Telephony.Carriers.CONTENT_URI_DM, null, selection, null, Telephony.Carriers.DEFAULT_SORT_ORDER);
                } else {
                    dmCursor = mPhone.getContext().getContentResolver().query(
                                   Telephony.Carriers.GeminiCarriers.CONTENT_URI_DM, null, selection, null, Telephony.Carriers.DEFAULT_SORT_ORDER);
                }

            } else {
                dmCursor = mPhone.getContext().getContentResolver().query(
                               Telephony.Carriers.CONTENT_URI_DM, null, selection, null, Telephony.Carriers.DEFAULT_SORT_ORDER);
            }

            if (dmCursor != null) {
                try {
                    /* Add by mtk01411 */
                    logd("fetchDMApn(): dmCursor_count=" + Integer.toString(dmCursor.getCount()));
                    if (dmCursor.getCount() > 0) {
                        return createApnList(dmCursor);
                    }
                } finally {
                    if (dmCursor != null) {
                        dmCursor.close();
                    }
                }
            }
        }
        return new ArrayList<ApnSetting>();
    }

    //[New R8 modem FD]
    /**
       * setFDTimerValue
       * @param String array for new Timer Value
       * @param Message for on complete
       * @internal
       */
    public int setFDTimerValue(String newTimerValue[], Message onComplete) {
        return mFDTimer.setFDTimerValue(newTimerValue, onComplete);
    }

    //[New R8 modem FD]
    /**
       * getFDTimerValue
       * @return FD Timer String array
       * @internal
       */
    public String[] getFDTimerValue() {
        return mFDTimer.getFDTimerValue();
    } 

    //[New R8 modem FD]
    public void updateFDMDEnableStatus(boolean enabled) {
        int FD_MD_Enable_Mode = Integer.parseInt(SystemProperties.get(PROPERTY_RIL_FD_MODE, "0"));        
        int FDSimID = SystemProperties.getInt("gsm.3gswitch", 1)-1;//Gemini+
        if (DBG) logd("updateFDMDEnableStatus():enabled=" + enabled + ",FD_MD_Enable_Mode=" + FD_MD_Enable_Mode + ", 3gSimID=" + FDSimID);
        if (FD_MD_Enable_Mode == 1 && FeatureOption.MTK_FD_SUPPORT && mPhone.getMySimId() == FDSimID) {
            if (enabled) { 
                mPhone.mCi.setFDMode(FDModeType.ENABLE_MD_FD.ordinal(), -1, -1, obtainMessage(DctConstants.EVENT_FD_MODE_SET));
            } else {
                mPhone.mCi.setFDMode(FDModeType.DISABLE_MD_FD.ordinal(), -1, -1, obtainMessage(DctConstants.EVENT_FD_MODE_SET)); 
            }            
        }
    }

    // M: For multiple SIM support to check is any connection active
    @Override
    public boolean isAllConnectionInactive() {
        boolean retValue = true;
        for (DcAsyncChannel dcac : mDataConnectionAcHashMap.values()) {
            if (!dcac.isInactiveSync()) {
                if (DBG) logd("Found active DC=" + dcac);
                retValue = false;
                break;
            }
        }
        if (DBG) logd("isAllConnectionInactive(): retValue=" + retValue);
        return retValue;
    }

    protected int getAvailableCidNum() {
        return SystemProperties.getInt(PROPERTY_AVAILABLE_CID, 6);
    }
    
    private boolean isGeminiDcStateDetachingOrDetached(int slotId) {
        boolean bRet = false;
        GeminiPhone geminiPhone = (GeminiPhone)PhoneFactory.getDefaultPhone();
        if (geminiPhone != null &&
            geminiPhone.isGprsDetachingOrDetached(slotId)) {
            bRet = true;
        }
        if (DBG) logd("isGeminiDcStateDetachingOrDetached, ret: " + bRet);
        return bRet;
    }

    // MTK
    public void deactivatePdpByCid(int cid) {        
        mPhone.mCi.deactivateDataCall(cid, RILConstants.DEACTIVATE_REASON_PDP_RESET, obtainMessage(DctConstants.EVENT_RESET_PDP_DONE, cid, 0));
    }

    @Override
    public int dialUpCsd(int simId, String dialUpNumber) {
        Message msg = obtainMessage(DctConstants.EVENT_SETUP_DATA_CONNECTION_DONE);
        mPhone.mCi.dialUpCsd(dialUpNumber, Integer.toString(simId), msg);
        return 1;
    }

    private void onDialUpCsdDone(AsyncResult ar) {

        Intent intent = new Intent(TelephonyIntents.ACTION_ANY_DATA_CONNECTION_STATE_CHANGED_CSD);
        DataCallResponse response = (DataCallResponse)ar.result;
        LinkProperties lp = new LinkProperties();
        boolean state = true;

        if(ar.exception != null) {
            if (DBG) logd("onDialUpCsdDone: ar exception null");
            state = false;            
        } else {
        
            if (response != null) {
                response.setLinkProperties(lp, true);
            } else {
                if (DBG) logd("onDialUpCsdDone: response null");            
                state = false;
            }

            intent.putExtra(PhoneConstants.DATA_LINK_PROPERTIES_KEY, lp);
            String iface = lp.getInterfaceName();
            if (iface != null) {
                intent.putExtra(PhoneConstants.DATA_IFACE_NAME_KEY, iface);
            }
            intent.putExtra(PhoneConstants.DATA_LINK_CAPABILITIES_KEY, new LinkCapabilities());
        }

        intent.putExtra("state", state);
        mGsmPhone.getContext().sendStickyBroadcast(intent);
    }
    
}
