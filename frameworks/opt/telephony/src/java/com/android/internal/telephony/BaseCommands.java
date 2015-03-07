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

package com.android.internal.telephony;


import android.content.Context;
import android.os.RegistrantList;
import android.os.Registrant;
import android.os.Handler;
import android.os.AsyncResult;
import android.os.SystemProperties;
import android.telephony.TelephonyManager;
import android.telephony.Rlog;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.mediatek.common.telephony.gsm.PBEntry;

//MTK-START [mtk04070][111118][ALPS00093395]MTK added
import android.os.Message;
//MTK-END [mtk04070][111118][ALPS00093395]MTK added

import android.telephony.SmsParameters;
import com.mediatek.common.telephony.gsm.FemtoCellInfo;

/**
 * {@hide}
 */
public abstract class BaseCommands implements CommandsInterface {
    static final String LOG_TAG = "RILB";

    //***** Instance Variables
    protected Context mContext;
    protected RadioState mState = RadioState.RADIO_UNAVAILABLE;
    protected RadioState mSimState = RadioState.RADIO_UNAVAILABLE;
    protected RadioState mRuimState = RadioState.RADIO_UNAVAILABLE;
    protected RadioState mNvState = RadioState.RADIO_UNAVAILABLE;
    protected Object mStateMonitor = new Object();

    protected RegistrantList mRadioStateChangedRegistrants = new RegistrantList();
    protected RegistrantList mOnRegistrants = new RegistrantList();
    protected RegistrantList mAvailRegistrants = new RegistrantList();
    protected RegistrantList mOffOrNotAvailRegistrants = new RegistrantList();
    protected RegistrantList mNotAvailRegistrants = new RegistrantList();
    protected RegistrantList mSIMReadyRegistrants = new RegistrantList();
    protected RegistrantList mSIMLockedRegistrants = new RegistrantList();
    protected RegistrantList mRUIMReadyRegistrants = new RegistrantList();
    protected RegistrantList mRUIMLockedRegistrants = new RegistrantList();
    protected RegistrantList mNVReadyRegistrants = new RegistrantList();
    protected RegistrantList mCallStateRegistrants = new RegistrantList();
    protected RegistrantList mVoiceNetworkStateRegistrants = new RegistrantList();
    protected RegistrantList mDataNetworkStateRegistrants = new RegistrantList();
    protected RegistrantList mVoiceRadioTechChangedRegistrants = new RegistrantList();
    protected RegistrantList mImsNetworkStateChangedRegistrants = new RegistrantList();
    protected RegistrantList mIccStatusChangedRegistrants = new RegistrantList();
    protected RegistrantList mVoicePrivacyOnRegistrants = new RegistrantList();
    protected RegistrantList mVoicePrivacyOffRegistrants = new RegistrantList();
    protected Registrant mUnsolOemHookRawRegistrant;
    protected RegistrantList mOtaProvisionRegistrants = new RegistrantList();
    protected RegistrantList mCallWaitingInfoRegistrants = new RegistrantList();
    protected RegistrantList mDisplayInfoRegistrants = new RegistrantList();
    protected RegistrantList mSignalInfoRegistrants = new RegistrantList();
    protected RegistrantList mNumberInfoRegistrants = new RegistrantList();
    protected RegistrantList mRedirNumInfoRegistrants = new RegistrantList();
    protected RegistrantList mLineControlInfoRegistrants = new RegistrantList();
    protected RegistrantList mT53ClirInfoRegistrants = new RegistrantList();
    protected RegistrantList mT53AudCntrlInfoRegistrants = new RegistrantList();
    protected RegistrantList mRingbackToneRegistrants = new RegistrantList();
    protected RegistrantList mResendIncallMuteRegistrants = new RegistrantList();
    protected RegistrantList mCdmaSubscriptionChangedRegistrants = new RegistrantList();
    protected RegistrantList mCdmaPrlChangedRegistrants = new RegistrantList();
    protected RegistrantList mExitEmergencyCallbackModeRegistrants = new RegistrantList();
    protected RegistrantList mRilConnectedRegistrants = new RegistrantList();
    protected RegistrantList mIccRefreshRegistrants = new RegistrantList();
    protected RegistrantList mRilCellInfoListRegistrants = new RegistrantList();
    protected RegistrantList mFemtoCellInfoRegistrants = new RegistrantList();

    //VIA-START VIA AGPS
    protected RegistrantList mViaGpsEvent = new RegistrantList();
    //VIA-END VIA AGPS
    protected Registrant mGsmSmsRegistrant;
    protected Registrant mCdmaSmsRegistrant;
    protected Registrant mNITZTimeRegistrant;
    protected Registrant mSignalStrengthRegistrant;
    protected Registrant mUSSDRegistrant;
    protected Registrant mSmsOnSimRegistrant;
    protected Registrant mSmsStatusRegistrant;
    protected Registrant mSsnRegistrant;
    protected Registrant mCatSessionEndRegistrant;
    protected Registrant mCatProCmdRegistrant;
    protected Registrant mCatEventRegistrant;
    protected Registrant mCatCallSetUpRegistrant;
    protected Registrant mIccSmsFullRegistrant;
    protected Registrant mEmergencyCallbackModeRegistrant;
    protected Registrant mRingRegistrant;
    protected Registrant mRestrictedStateRegistrant;
    protected Registrant mGsmBroadcastSmsRegistrant;
    protected Registrant mStkEvdlCallRegistrant;

    //MTK-START [mtk04070][111118][ALPS00093395]MTK added 
    protected RegistrantList mNeighboringInfoRegistrants = new RegistrantList();
    protected RegistrantList mNetworkInfoRegistrants = new RegistrantList();
    /* mtk00732 add for CFU notification */
    protected RegistrantList mCallForwardingInfoRegistrants = new RegistrantList();
    protected RegistrantList mPhbReadyRegistrants = new RegistrantList();
    protected RegistrantList mSmsReadyRegistrants = new RegistrantList();
    protected RegistrantList mCallProgressInfoRegistrants = new RegistrantList();
    protected RegistrantList mSpeechInfoRegistrants = new RegistrantList();
    protected RegistrantList mSimInsertedStatusRegistrants = new RegistrantList();
    protected RegistrantList mSimMissing = new RegistrantList();
    protected RegistrantList mSimRecovery = new RegistrantList();
    protected RegistrantList mVirtualSimOn = new RegistrantList();
    protected RegistrantList mVirtualSimOff= new RegistrantList();
    protected RegistrantList mSimPlugOutRegistrants= new RegistrantList();
    protected RegistrantList mSimPlugInRegistrants= new RegistrantList();
    /* vt start */
    protected RegistrantList mVtStatusInfoRegistrants = new RegistrantList();
    protected RegistrantList mVtRingRegistrants = new RegistrantList();
    /* vt end */
    //Add by mtk80372 for Barcode Number
    protected RegistrantList mSNRegistrants = new RegistrantList();
    protected Registrant mCallRelatedSuppSvcRegistrant;
    protected Registrant mMeSmsFullRegistrant;
    protected Registrant mScriResultRegistrant;
    protected Registrant mIncomingCallIndicationRegistrant;
    protected Registrant mGprsDetachRegistrant;
    //MTK-END [mtk04070][111118][ALPS00093395]MTK added 


    //ALPS00249116
    protected RegistrantList mPsNetworkStateRegistrants = new RegistrantList();

    protected Registrant mEfCspPlmnModeBitRegistrant;

    protected RegistrantList mImeiLockRegistrant = new RegistrantList();

    //ALPS00248788
    protected Registrant mInvalidSimInfoRegistrant;

    protected RegistrantList mGetAvailableNetworkDoneRegistrant = new RegistrantList();

    //Add by gfzhu VIA start
    protected RegistrantList mAcceptedRegistrant = new RegistrantList();
    //Add by gfzhu VIA end

    // via support start [ALPS00421033]
    protected RegistrantList mNetworkTypeChangedRegistrant = new RegistrantList();
    // via support end [ALPS00421033]

    // [mtk02772] start
    // add for cdma slot inserted by gsm card
    protected RegistrantList mInvalidSimDetectedRegistrant = new RegistrantList();
    // [mtk02772] end

    // UTK start
    protected Registrant mUtkSessionEndRegistrant;
    protected Registrant mUtkProCmdRegistrant;
    protected Registrant mUtkEventRegistrant;
    //UTK end

    //8.2.11 TC-IRLAB-02011 start
    protected RegistrantList mMccMncChangeRegistrants = new RegistrantList();
    //8.2.11 TC-IRLAB-02011 end
    protected Registrant mCnapNotifyRegistrant;
    
    protected Registrant mEtwsNotificationRegistrant;

    protected Registrant mPlmnChangeNotificationRegistrant;
    protected Registrant mGSMSuspendedRegistrant;

    protected Registrant mFemtoCellInfoRegistrant;

    //MTK-START [mtk80881]for packets flush indication
    protected Registrant mPacketsFlushNotificationRegistrant;
    //MTK-END

    protected RegistrantList mCipherIndicationRegistrant = new RegistrantList();
    //sm cause rac
    protected RegistrantList mRacUpdateRegistrants = new RegistrantList();
    
    // Preferred network type received from PhoneFactory.
    // This is used when establishing a connection to the
    // vendor ril so it starts up in the correct mode.
    protected int mPreferredNetworkType;
    // CDMA subscription received from PhoneFactory
    protected int mCdmaSubscription;
    // Type of Phone, GSM or CDMA. Set by CDMAPhone or GSMPhone.
    protected int mPhoneType;
    // RIL Version
    protected int mRilVersion = -1;

    protected boolean mbWaitingForECFURegistrants = false;
    protected Object mCfuReturnValue;

    protected Phone mPhone = null; // MVNO-API

    public BaseCommands(Context context) {
        mContext = context;  // May be null (if so we won't log statistics)
    }

    //***** CommandsInterface implementation

    @Override
    public RadioState getRadioState() {
        return mState;
    }

    @Override
    public RadioState getSimState() {
        return mSimState;
    }

    @Override
    public RadioState getRuimState() {
        return mRuimState;
    }

    @Override
    public RadioState getNvState() {
        return mNvState;
    }


    @Override
    public void registerForRadioStateChanged(Handler h, int what, Object obj) {
        Registrant r = new Registrant (h, what, obj);

        synchronized (mStateMonitor) {
            mRadioStateChangedRegistrants.add(r);
            r.notifyRegistrant(new AsyncResult(null, mState, null));
        }
    }

    @Override
    public void unregisterForRadioStateChanged(Handler h) {
        synchronized (mStateMonitor) {
            mRadioStateChangedRegistrants.remove(h);
        }
    }

    public void registerForImsNetworkStateChanged(Handler h, int what, Object obj) {
        Registrant r = new Registrant (h, what, obj);
        mImsNetworkStateChangedRegistrants.add(r);
    }

    public void unregisterForImsNetworkStateChanged(Handler h) {
        mImsNetworkStateChangedRegistrants.remove(h);
    }

    @Override
    public void registerForOn(Handler h, int what, Object obj) {
        Registrant r = new Registrant (h, what, obj);

        synchronized (mStateMonitor) {
            mOnRegistrants.add(r);

            if (mState.isOn()) {
                r.notifyRegistrant(new AsyncResult(null, null, null));
            }
        }
    }
    @Override
    public void unregisterForOn(Handler h) {
        synchronized (mStateMonitor) {
            mOnRegistrants.remove(h);
        }
    }


    @Override
    public void registerForAvailable(Handler h, int what, Object obj) {
        Registrant r = new Registrant (h, what, obj);

        synchronized (mStateMonitor) {
            mAvailRegistrants.add(r);

            if (mState.isAvailable()) {
                r.notifyRegistrant(new AsyncResult(null, null, null));
            }
        }
    }

    @Override
    public void unregisterForAvailable(Handler h) {
        synchronized(mStateMonitor) {
            mAvailRegistrants.remove(h);
        }
    }

    @Override
    public void registerForNotAvailable(Handler h, int what, Object obj) {
        Registrant r = new Registrant (h, what, obj);

        synchronized (mStateMonitor) {
            mNotAvailRegistrants.add(r);

            if (!mState.isAvailable()) {
                r.notifyRegistrant(new AsyncResult(null, null, null));
            }
        }
    }

    @Override
    public void unregisterForNotAvailable(Handler h) {
        synchronized (mStateMonitor) {
            mNotAvailRegistrants.remove(h);
        }
    }

    @Override
    public void registerForOffOrNotAvailable(Handler h, int what, Object obj) {
        Registrant r = new Registrant (h, what, obj);

        synchronized (mStateMonitor) {
            mOffOrNotAvailRegistrants.add(r);

            if (mState == RadioState.RADIO_OFF || !mState.isAvailable()) {
                r.notifyRegistrant(new AsyncResult(null, null, null));
            }
        }
    }
    @Override
    public void unregisterForOffOrNotAvailable(Handler h) {
        synchronized(mStateMonitor) {
            mOffOrNotAvailRegistrants.remove(h);
        }
    }


    /** Any transition into SIM_READY */
    @Override
    public void registerForSIMReady(Handler h, int what, Object obj) {
        Registrant r = new Registrant (h, what, obj);

        synchronized (mStateMonitor) {
            mSIMReadyRegistrants.add(r);

            if (mSimState.isSIMReady()) {
                r.notifyRegistrant(new AsyncResult(null, null, null));
            }
        }
    }

    @Override
    public void unregisterForSIMReady(Handler h) {
        synchronized (mStateMonitor) {
            mSIMReadyRegistrants.remove(h);
        }
    }

    /** Any transition into RUIM_READY */
    @Override
    public void registerForRUIMReady(Handler h, int what, Object obj) {
        Registrant r = new Registrant (h, what, obj);

        synchronized (mStateMonitor) {
            mRUIMReadyRegistrants.add(r);

            if (mRuimState.isRUIMReady()) {
                r.notifyRegistrant(new AsyncResult(null, null, null));
            }
        }
    }

    @Override
    public void unregisterForRUIMReady(Handler h) {
        synchronized(mStateMonitor) {
            mRUIMReadyRegistrants.remove(h);
        }
    }

    /** Any transition into NV_READY */
    @Override
    public void registerForNVReady(Handler h, int what, Object obj) {
        Registrant r = new Registrant (h, what, obj);

        synchronized (mStateMonitor) {
            mNVReadyRegistrants.add(r);

            if (mNvState.isNVReady()) {
                r.notifyRegistrant(new AsyncResult(null, null, null));
            }
        }
    }

    @Override
    public void unregisterForNVReady(Handler h) {
        synchronized (mStateMonitor) {
            mNVReadyRegistrants.remove(h);
        }
    }

    @Override
    public void registerForSIMLockedOrAbsent(Handler h, int what, Object obj) {
        Registrant r = new Registrant (h, what, obj);

        synchronized (mStateMonitor) {
            mSIMLockedRegistrants.add(r);

            if (mSimState == RadioState.SIM_LOCKED_OR_ABSENT) {
                r.notifyRegistrant(new AsyncResult(null, null, null));
            }
        }
    }

    @Override
    public void unregisterForSIMLockedOrAbsent(Handler h) {
        synchronized (mStateMonitor) {
            mSIMLockedRegistrants.remove(h);
        }
    }

    @Override
    public void registerForRUIMLockedOrAbsent(Handler h, int what, Object obj) {
        Registrant r = new Registrant (h, what, obj);

        synchronized (mStateMonitor) {
            mRUIMLockedRegistrants.add(r);

            if (mRuimState == RadioState.RUIM_LOCKED_OR_ABSENT) {
                r.notifyRegistrant(new AsyncResult(null, null, null));
            }
        }
    }

    @Override
    public void unregisterForRUIMLockedOrAbsent(Handler h) {
        synchronized (mStateMonitor) {
            mRUIMLockedRegistrants.remove(h);
        }
    }

    @Override
    public void registerForCallStateChanged(Handler h, int what, Object obj) {
        Registrant r = new Registrant (h, what, obj);

        mCallStateRegistrants.add(r);
    }

    @Override
    public void unregisterForCallStateChanged(Handler h) {
        mCallStateRegistrants.remove(h);
    }

    @Override
    public void registerForVoiceNetworkStateChanged(Handler h, int what, Object obj) {
        Registrant r = new Registrant (h, what, obj);

        mVoiceNetworkStateRegistrants.add(r);
    }

    @Override
    public void unregisterForVoiceNetworkStateChanged(Handler h) {
        mVoiceNetworkStateRegistrants.remove(h);
    }

    @Override
    public void registerForDataNetworkStateChanged(Handler h, int what, Object obj) {
        Registrant r = new Registrant (h, what, obj);

        mDataNetworkStateRegistrants.add(r);
    }

    @Override
    public void unregisterForDataNetworkStateChanged(Handler h) {
        mDataNetworkStateRegistrants.remove(h);
    }

    @Override
    public void registerForVoiceRadioTechChanged(Handler h, int what, Object obj) {
        Registrant r = new Registrant (h, what, obj);
        mVoiceRadioTechChangedRegistrants.add(r);
    }

    @Override
    public void unregisterForVoiceRadioTechChanged(Handler h) {
        mVoiceRadioTechChangedRegistrants.remove(h);
    }

    @Override
    public void registerForIccStatusChanged(Handler h, int what, Object obj) {
        Registrant r = new Registrant (h, what, obj);
        mIccStatusChangedRegistrants.add(r);
    }

    @Override
    public void unregisterForIccStatusChanged(Handler h) {
        mIccStatusChangedRegistrants.remove(h);
    }

    @Override
    public void setOnNewGsmSms(Handler h, int what, Object obj) {
        mGsmSmsRegistrant = new Registrant (h, what, obj);
    }

    @Override
    public void unSetOnNewGsmSms(Handler h) {
        mGsmSmsRegistrant.clear();
    }

    @Override
    public void setOnNewCdmaSms(Handler h, int what, Object obj) {
        mCdmaSmsRegistrant = new Registrant (h, what, obj);
    }

    @Override
    public void unSetOnNewCdmaSms(Handler h) {
        mCdmaSmsRegistrant.clear();
    }

    @Override
    public void setOnNewGsmBroadcastSms(Handler h, int what, Object obj) {
        mGsmBroadcastSmsRegistrant = new Registrant (h, what, obj);
    }

    @Override
    public void unSetOnNewGsmBroadcastSms(Handler h) {
        mGsmBroadcastSmsRegistrant.clear();
    }

    @Override
    public void setOnSmsOnSim(Handler h, int what, Object obj) {
        mSmsOnSimRegistrant = new Registrant (h, what, obj);
    }

    @Override
    public void unSetOnSmsOnSim(Handler h) {
        mSmsOnSimRegistrant.clear();
    }

    @Override
    public void setOnSmsStatus(Handler h, int what, Object obj) {
        mSmsStatusRegistrant = new Registrant (h, what, obj);
    }

    @Override
    public void unSetOnSmsStatus(Handler h) {
        mSmsStatusRegistrant.clear();
    }

    @Override
    public void setOnSignalStrengthUpdate(Handler h, int what, Object obj) {
        mSignalStrengthRegistrant = new Registrant (h, what, obj);
    }

    @Override
    public void unSetOnSignalStrengthUpdate(Handler h) {
        mSignalStrengthRegistrant.clear();
    }

    @Override
    public void setOnNITZTime(Handler h, int what, Object obj) {
        mNITZTimeRegistrant = new Registrant (h, what, obj);
    }

    @Override
    public void unSetOnNITZTime(Handler h) {
        mNITZTimeRegistrant.clear();
    }

    @Override
    public void setOnUSSD(Handler h, int what, Object obj) {
        mUSSDRegistrant = new Registrant (h, what, obj);
    }

    @Override
    public void unSetOnUSSD(Handler h) {
        mUSSDRegistrant.clear();
    }

    @Override
    public void setOnSuppServiceNotification(Handler h, int what, Object obj) {
        mSsnRegistrant = new Registrant (h, what, obj);
    }

    @Override
    public void unSetOnSuppServiceNotification(Handler h) {
        mSsnRegistrant.clear();
    }

    @Override
    public void setOnCatSessionEnd(Handler h, int what, Object obj) {
        mCatSessionEndRegistrant = new Registrant (h, what, obj);
    }

    @Override
    public void unSetOnCatSessionEnd(Handler h) {
        mCatSessionEndRegistrant.clear();
    }

    @Override
    public void setOnCatProactiveCmd(Handler h, int what, Object obj) {
        mCatProCmdRegistrant = new Registrant (h, what, obj);
    }

    @Override
    public void unSetOnCatProactiveCmd(Handler h) {
        mCatProCmdRegistrant.clear();
    }

    @Override
    public void setOnCatEvent(Handler h, int what, Object obj) {
        mCatEventRegistrant = new Registrant (h, what, obj);
    }

    @Override
    public void unSetOnCatEvent(Handler h) {
        mCatEventRegistrant.clear();
    }

    @Override
    public void setOnCatCallSetUp(Handler h, int what, Object obj) {
        mCatCallSetUpRegistrant = new Registrant (h, what, obj);
    }

    @Override
    public void unSetOnCatCallSetUp(Handler h) {
        mCatCallSetUpRegistrant.clear();
    }

    // UTK start
    @Override
    public void setOnUtkSessionEnd(Handler h, int what, Object obj) {
        mUtkSessionEndRegistrant = new Registrant (h, what, obj);
    }

    @Override
    public void unSetOnUtkSessionEnd(Handler h) {
        mUtkSessionEndRegistrant.clear();
    }

    @Override
    public void setOnUtkProactiveCmd(Handler h, int what, Object obj) {
        mUtkProCmdRegistrant = new Registrant (h, what, obj);
    }

    @Override
    public void unSetOnUtkProactiveCmd(Handler h) {
        mUtkProCmdRegistrant.clear();
    }

    @Override
    public void setOnUtkEvent(Handler h, int what, Object obj) {
        mUtkEventRegistrant = new Registrant (h, what, obj);
    }

    @Override
    public void unSetOnUtkEvent(Handler h) {
        mUtkEventRegistrant.clear();
    }
    //UTK end    

    @Override
    public void setOnIccSmsFull(Handler h, int what, Object obj) {
        mIccSmsFullRegistrant = new Registrant (h, what, obj);
    }

    @Override
    public void unSetOnIccSmsFull(Handler h) {
        mIccSmsFullRegistrant.clear();
    }

    @Override
    public void registerForIccRefresh(Handler h, int what, Object obj) {
        Registrant r = new Registrant (h, what, obj);
        mIccRefreshRegistrants.add(r);
    }
    @Override
    public void setOnIccRefresh(Handler h, int what, Object obj) {
        registerForIccRefresh(h, what, obj);
    }

    @Override
    public void setEmergencyCallbackMode(Handler h, int what, Object obj) {
        mEmergencyCallbackModeRegistrant = new Registrant (h, what, obj);
    }

    @Override
    public void unregisterForIccRefresh(Handler h) {
        mIccRefreshRegistrants.remove(h);
    }
    @Override
    public void unsetOnIccRefresh(Handler h) {
        unregisterForIccRefresh(h);
    }

    @Override
    public void setOnCallRing(Handler h, int what, Object obj) {
        mRingRegistrant = new Registrant (h, what, obj);
    }

    @Override
    public void unSetOnCallRing(Handler h) {
        mRingRegistrant.clear();
    }

    @Override
    public void setOnStkEvdlCall(Handler h, int what, Object obj) {
        mStkEvdlCallRegistrant = new Registrant (h, what, obj);
    }

    @Override
    public void unSetOnStkEvdlCall(Handler h) {
        mStkEvdlCallRegistrant.clear();
    }

    @Override
    public void registerForInCallVoicePrivacyOn(Handler h, int what, Object obj) {
        Registrant r = new Registrant (h, what, obj);
        mVoicePrivacyOnRegistrants.add(r);
    }

    @Override
    public void unregisterForInCallVoicePrivacyOn(Handler h){
        mVoicePrivacyOnRegistrants.remove(h);
    }

    @Override
    public void registerForInCallVoicePrivacyOff(Handler h, int what, Object obj) {
        Registrant r = new Registrant (h, what, obj);
        mVoicePrivacyOffRegistrants.add(r);
    }

    @Override
    public void unregisterForInCallVoicePrivacyOff(Handler h){
        mVoicePrivacyOffRegistrants.remove(h);
    }

    @Override
    public void setOnRestrictedStateChanged(Handler h, int what, Object obj) {
        mRestrictedStateRegistrant = new Registrant (h, what, obj);
    }

    @Override
    public void unSetOnRestrictedStateChanged(Handler h) {
        mRestrictedStateRegistrant.clear();
    }

    @Override
    public void registerForDisplayInfo(Handler h, int what, Object obj) {
        Registrant r = new Registrant (h, what, obj);
        mDisplayInfoRegistrants.add(r);
    }

    @Override
    public void unregisterForDisplayInfo(Handler h) {
        mDisplayInfoRegistrants.remove(h);
    }

    @Override
    public void registerForCallWaitingInfo(Handler h, int what, Object obj) {
        Registrant r = new Registrant (h, what, obj);
        mCallWaitingInfoRegistrants.add(r);
    }

    @Override
    public void unregisterForCallWaitingInfo(Handler h) {
        mCallWaitingInfoRegistrants.remove(h);
    }

    @Override
    public void registerForSignalInfo(Handler h, int what, Object obj) {
        Registrant r = new Registrant (h, what, obj);
        mSignalInfoRegistrants.add(r);
    }

    public void setOnUnsolOemHookRaw(Handler h, int what, Object obj) {
        mUnsolOemHookRawRegistrant = new Registrant (h, what, obj);
    }

    public void unSetOnUnsolOemHookRaw(Handler h) {
        mUnsolOemHookRawRegistrant.clear();
    }

    @Override
    public void unregisterForSignalInfo(Handler h) {
        mSignalInfoRegistrants.remove(h);
    }

    @Override
    public void registerForCdmaOtaProvision(Handler h,int what, Object obj){
        Registrant r = new Registrant (h, what, obj);
        mOtaProvisionRegistrants.add(r);
    }

    @Override
    public void unregisterForCdmaOtaProvision(Handler h){
        mOtaProvisionRegistrants.remove(h);
    }

    @Override
    public void registerForNumberInfo(Handler h,int what, Object obj) {
        Registrant r = new Registrant (h, what, obj);
        mNumberInfoRegistrants.add(r);
    }

    @Override
    public void unregisterForNumberInfo(Handler h){
        mNumberInfoRegistrants.remove(h);
    }

    @Override
     public void registerForRedirectedNumberInfo(Handler h,int what, Object obj) {
        Registrant r = new Registrant (h, what, obj);
        mRedirNumInfoRegistrants.add(r);
    }

     @Override
    public void unregisterForRedirectedNumberInfo(Handler h) {
        mRedirNumInfoRegistrants.remove(h);
    }

    @Override
    public void registerForLineControlInfo(Handler h, int what, Object obj) {
        Registrant r = new Registrant (h, what, obj);
        mLineControlInfoRegistrants.add(r);
    }

    @Override
    public void unregisterForLineControlInfo(Handler h) {
        mLineControlInfoRegistrants.remove(h);
    }

    @Override
    public void registerFoT53ClirlInfo(Handler h,int what, Object obj) {
        Registrant r = new Registrant (h, what, obj);
        mT53ClirInfoRegistrants.add(r);
    }

    @Override
    public void unregisterForT53ClirInfo(Handler h) {
        mT53ClirInfoRegistrants.remove(h);
    }

    @Override
    public void registerForT53AudioControlInfo(Handler h,int what, Object obj) {
        Registrant r = new Registrant (h, what, obj);
        mT53AudCntrlInfoRegistrants.add(r);
    }

    @Override
    public void unregisterForT53AudioControlInfo(Handler h) {
        mT53AudCntrlInfoRegistrants.remove(h);
    }

    @Override
    public void registerForRingbackTone(Handler h, int what, Object obj) {
        Registrant r = new Registrant (h, what, obj);
        mRingbackToneRegistrants.add(r);
    }

    @Override
    public void unregisterForRingbackTone(Handler h) {
        mRingbackToneRegistrants.remove(h);
    }

    @Override
    public void registerForResendIncallMute(Handler h, int what, Object obj) {
        Registrant r = new Registrant (h, what, obj);
        mResendIncallMuteRegistrants.add(r);
    }

    @Override
    public void unregisterForResendIncallMute(Handler h) {
        mResendIncallMuteRegistrants.remove(h);
    }

    @Override
    public void registerForCdmaSubscriptionChanged(Handler h, int what, Object obj) {
        Registrant r = new Registrant (h, what, obj);
        mCdmaSubscriptionChangedRegistrants.add(r);
    }

    @Override
    public void unregisterForCdmaSubscriptionChanged(Handler h) {
        mCdmaSubscriptionChangedRegistrants.remove(h);
    }

    @Override
    public void registerForCdmaPrlChanged(Handler h, int what, Object obj) {
        Registrant r = new Registrant (h, what, obj);
        mCdmaPrlChangedRegistrants.add(r);
    }

    @Override
    public void unregisterForCdmaPrlChanged(Handler h) {
        mCdmaPrlChangedRegistrants.remove(h);
    }

    @Override
    public void registerForExitEmergencyCallbackMode(Handler h, int what, Object obj) {
        Registrant r = new Registrant (h, what, obj);
        mExitEmergencyCallbackModeRegistrants.add(r);
    }

    @Override
    public void unregisterForExitEmergencyCallbackMode(Handler h) {
        mExitEmergencyCallbackModeRegistrants.remove(h);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void registerForRilConnected(Handler h, int what, Object obj) {
        Rlog.d(LOG_TAG, "registerForRilConnected h=" + h + " w=" + what);
        Registrant r = new Registrant (h, what, obj);
        mRilConnectedRegistrants.add(r);
        if (mRilVersion != -1) {
            Rlog.d(LOG_TAG, "Notifying: ril connected mRilVersion=" + mRilVersion);
            r.notifyRegistrant(new AsyncResult(null, new Integer(mRilVersion), null));
        }
    }

    @Override
    public void unregisterForRilConnected(Handler h) {
        mRilConnectedRegistrants.remove(h);
    }

    //MTK-START [mtk04070][111223][ALPS00106134]Merge to ICS 4.0.3
    /**
     * {@inheritDoc}
     */
    @Override
    public void setCurrentPreferredNetworkType() {
    }
    //MTK-END [mtk04070][111223][ALPS00106134]Merge to ICS 4.0.3

    //***** Protected Methods
    /**
     * Store new RadioState and send notification based on the changes
     *
     * This function is called only by RIL.java when receiving unsolicited
     * RIL_UNSOL_RESPONSE_RADIO_STATE_CHANGED
     *
     * RadioState has 5 values : RADIO_OFF, RADIO_UNAVAILABLE, SIM_NOT_READY,
     * SIM_LOCKED_OR_ABSENT, and SIM_READY.
     *
     * @param newState new RadioState decoded from RIL_UNSOL_RADIO_STATE_CHANGED
     */
    protected void setRadioState(RadioState newState) {
        RadioState oldState;

        synchronized (mStateMonitor) {
            Rlog.v(LOG_TAG, "setRadioState old: " + mState + " new " + newState);

            oldState = mState;
            mState = newState;

            // For CTA feature, sim1 is radio on if no sim card inserted. 
            // In rild, it is in the state of SIM_LOCKED_OR_ABSENT.
            // if the sim card is pin locked, then after turn on radio of sim, it still the state of SIM_LOCKED_OR_ABSENT
            // special handle for this scenario, always notify radio changed if the state is SIM_LOCKED_OR_ABSENT
            if (oldState == mState && mState != RadioState.SIM_LOCKED_OR_ABSENT) {
                // no state transition
                return;
            }

            // FIXME: Use Constants or Enums
            if(mState.getType() == 0) {
                mSimState = mState;
                mRuimState = mState;
                mNvState = mState;
            }
            else if (mState.getType() == 1) {
                if(mSimState != mState) {
                    mIccStatusChangedRegistrants.notifyRegistrants();
                }
                mSimState = mState;
            }
            else if (mState.getType() == 2) {
                if(mRuimState != mState) {
                    mIccStatusChangedRegistrants.notifyRegistrants();
                }
                mRuimState = mState;
            }
            else if (mState.getType() == 3) {
                mNvState = mState;
            }

            mRadioStateChangedRegistrants.notifyRegistrants(new AsyncResult(null, mState, null));

            if (mState.isAvailable() && !oldState.isAvailable()) {
                Rlog.d(LOG_TAG,"Notifying: radio available");
                mAvailRegistrants.notifyRegistrants();
                onRadioAvailable();
            }

            if (!mState.isAvailable() && oldState.isAvailable()) {
                Rlog.d(LOG_TAG,"Notifying: radio not available");
                mNotAvailRegistrants.notifyRegistrants();
            }
/*
            if (mState.isSIMReady() && !oldState.isSIMReady()) {
                Rlog.d(LOG_TAG,"Notifying: SIM ready");
                mSIMReadyRegistrants.notifyRegistrants();
            }

            if (mState == RadioState.SIM_LOCKED_OR_ABSENT) {
                Rlog.d(LOG_TAG,"Notifying: SIM locked or absent");
                mSIMLockedRegistrants.notifyRegistrants();
            }

            if (mState.isRUIMReady() && !oldState.isRUIMReady()) {
                Rlog.d(LOG_TAG,"Notifying: RUIM ready");
                mRUIMReadyRegistrants.notifyRegistrants();
            }

            if (mState == RadioState.RUIM_LOCKED_OR_ABSENT) {
                Rlog.d(LOG_TAG,"Notifying: RUIM locked or absent");
                mRUIMLockedRegistrants.notifyRegistrants();
            }
            if (mState.isNVReady() && !oldState.isNVReady()) {
                Rlog.d(LOG_TAG,"Notifying: NV ready");
                mNVReadyRegistrants.notifyRegistrants();
            }
*/
            if (mState.isOn() && !oldState.isOn()) {
                Rlog.d(LOG_TAG,"Notifying: Radio On");
                mOnRegistrants.notifyRegistrants();
            }

            if ((!mState.isOn() || !mState.isAvailable())
                && !((!oldState.isOn() || !oldState.isAvailable()))
            ) {
                Rlog.d(LOG_TAG,"Notifying: radio off or not available");
                mOffOrNotAvailRegistrants.notifyRegistrants();
            }

            /* Radio Technology Change events
             * NOTE: isGsm and isCdma have no common states in RADIO_OFF or RADIO_UNAVAILABLE; the
             *   current phone is determined by mPhoneType
             * NOTE: at startup no phone have been created and the RIL determines the mPhoneType
             *   looking based on the networkMode set by the PhoneFactory in the constructor
             */

            if (mState.isGsm() && oldState.isCdma()) {
                Rlog.d(LOG_TAG,"Notifying: radio technology change CDMA to GSM");
                mVoiceRadioTechChangedRegistrants.notifyRegistrants();
            }

            if (mState.isGsm() && !oldState.isOn() && (mPhoneType == PhoneConstants.PHONE_TYPE_CDMA)) {
                Rlog.d(LOG_TAG,"Notifying: radio technology change CDMA OFF to GSM");
                mVoiceRadioTechChangedRegistrants.notifyRegistrants();
            }

            if (mState.isCdma() && oldState.isGsm()) {
                Rlog.d(LOG_TAG,"Notifying: radio technology change GSM to CDMA");
                mVoiceRadioTechChangedRegistrants.notifyRegistrants();
            }

            if (mState.isCdma() && !oldState.isOn() && (mPhoneType == PhoneConstants.PHONE_TYPE_GSM)) {
                Rlog.d(LOG_TAG,"Notifying: radio technology change GSM OFF to CDMA");
                mVoiceRadioTechChangedRegistrants.notifyRegistrants();
            }
        }
    }

    protected void onRadioAvailable() {
    }

    /**
     * The contents of the /proc/cmdline file
     */
    private static String getProcCmdLine()
    {
        String cmdline = "";
        FileInputStream is = null;
        try {
            is = new FileInputStream("/proc/cmdline");
            byte [] buffer = new byte[2048];
            int count = is.read(buffer);
            if (count > 0) {
                cmdline = new String(buffer, 0, count);
            }
        } catch (IOException e) {
            Rlog.d(LOG_TAG, "No /proc/cmdline exception=" + e);
        } finally {
            if (is != null) {
                try {
                    is.close();
                } catch (IOException e) {
                }
            }
        }
        Rlog.d(LOG_TAG, "/proc/cmdline=" + cmdline);
        return cmdline;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getLteOnCdmaMode() {
        return TelephonyManager.getLteOnCdmaModeStatic();
    }

    /** Kernel command line */
    private static final String sKernelCmdLine = getProcCmdLine();

    /** Pattern for selecting the product type from the kernel command line */
    private static final Pattern sProductTypePattern =
        Pattern.compile("\\sproduct_type\\s*=\\s*(\\w+)");

    /** The ProductType used for LTE on CDMA devices */
    private static final String sLteOnCdmaProductType =
        SystemProperties.get(TelephonyProperties.PROPERTY_LTE_ON_CDMA_PRODUCT_TYPE, "");

    /**
     * {@inheritDoc}
     */
    @Override
    public void registerForCellInfoList(Handler h, int what, Object obj) {
        Registrant r = new Registrant (h, what, obj);
        mRilCellInfoListRegistrants.add(r);
    }
    @Override
    public void unregisterForCellInfoList(Handler h) {
        mRilCellInfoListRegistrants.remove(h);
    }

    @Override
    public void testingEmergencyCall() {}

    @Override
    public int getRilVersion() {
        //FIX ME
        ////return mRilVersion;
        return 8;
    }

    //MTK-START [mtk04070][111118][ALPS00093395]MTK proprietary methods
    //Add by mtk80372 for Barcode Number
    public void registerForSN(Handler h, int what, Object obj){
        Registrant r = new Registrant (h, what, obj);

        synchronized (mStateMonitor) {
            mSNRegistrants.add(r);

            if (mState.isAvailable()) {
                r.notifyRegistrant(new AsyncResult(null, null, null));
            }
        }
    }
    //Add by mtk80372 for Barcode Number
    public void unregisterForSN(Handler h){
        synchronized(mStateMonitor) {
            mSNRegistrants.remove(h);
        }        
    }

    public void registerForPhbReady(Handler h, int what, Object obj) {
        Registrant r = new Registrant (h, what, obj);
        mPhbReadyRegistrants.add(r);
    }
    
    public void unregisterForPhbReady(Handler h) {
        mPhbReadyRegistrants.remove(h);
    }

    public void registerForSmsReady(Handler h, int what, Object obj) {
        Registrant r = new Registrant (h, what, obj);
        mSmsReadyRegistrants.add(r);
    }
    
    public void unregisterForSmsReady(Handler h) {
        mSmsReadyRegistrants.remove(h);
    }

    public void setOnMeSmsFull(Handler h, int what, Object obj) {
        mMeSmsFullRegistrant = new Registrant (h, what, obj);
    }

    public void unSetOnMeSmsFull(Handler h) {
        mMeSmsFullRegistrant.clear();
    }

    public void registerForCallForwardingInfo(Handler h, int what, Object obj) {
        Registrant r = new Registrant (h, what, obj);
        mCallForwardingInfoRegistrants.add(r);
        if (mbWaitingForECFURegistrants) { 
           mbWaitingForECFURegistrants = false;
           mCallForwardingInfoRegistrants.notifyRegistrants(new AsyncResult (null, mCfuReturnValue, null));
        }
    }

    public void unregisterForCallForwardingInfo(Handler h) {
        mCallForwardingInfoRegistrants.remove(h);
    }

    public void setOnCallRelatedSuppSvc(Handler h, int what, Object obj) {
        mCallRelatedSuppSvcRegistrant = new Registrant (h, what, obj);
    }

    public void unSetOnCallRelatedSuppSvc(Handler h) {
        mCallRelatedSuppSvcRegistrant.clear();
    }
    

    public void registerForNeighboringInfo(Handler h, int what, Object obj) {
        Registrant r = new Registrant (h, what, obj);
        mNeighboringInfoRegistrants.add(r);
    }

    public void unregisterForNeighboringInfo(Handler h) {
        mNeighboringInfoRegistrants.remove(h);
    }

    public void registerForNetworkInfo(Handler h, int what, Object obj) {
        Registrant r = new Registrant (h, what, obj);
        mNetworkInfoRegistrants.add(r);
    }

    public void unregisterForNetworkInfo(Handler h) {
        mNetworkInfoRegistrants.remove(h);
    } 

    public void registerForCallProgressInfo(Handler h, int what, Object obj) {
        Registrant r = new Registrant (h, what, obj);
        mCallProgressInfoRegistrants.add(r);
    }

    public void unregisterForCallProgressInfo(Handler h) {
        mCallProgressInfoRegistrants.remove(h);
    } 

    public void registerForSpeechInfo(Handler h, int what, Object obj) {
        Registrant r = new Registrant (h, what, obj);
        mSpeechInfoRegistrants.add(r);
    }

    public void unregisterForSpeechInfo(Handler h) {
        mSpeechInfoRegistrants.remove(h);
    } 

    /* vt start */
    public void registerForVtStatusInfo(Handler h, int what, Object obj) {
        Registrant r = new Registrant (h, what, obj);
        mVtStatusInfoRegistrants.add(r);
    }

    public void unregisterForVtStatusInfo(Handler h) {
        mVtStatusInfoRegistrants.remove(h);
    } 

    public void registerForVtRingInfo(Handler h, int what, Object obj) {
        Registrant r = new Registrant (h, what, obj);
        mVtRingRegistrants.add(r);
    }

    public void unregisterForVtRingInfo(Handler h) {
        mVtRingRegistrants.remove(h);
    }
    /* vt end */

    public void registerForSimInsertedStatus(Handler h, int what, Object obj) {
        Registrant r = new Registrant (h, what, obj);
        mSimInsertedStatusRegistrants.add(r);
    }

    public void unregisterForSimInsertedStatus(Handler h) {
        mSimInsertedStatusRegistrants.remove(h);
    }
    
    public void registerForSimMissing(Handler h, int what, Object obj) {
        Registrant r = new Registrant (h, what, obj);
        mSimMissing.add(r);
    }
    public void unregisterForSimMissing(Handler h) {
    	mSimMissing.remove(h);
    }
	
    public void registerForSimRecovery(Handler h, int what, Object obj) {
        Registrant r = new Registrant (h, what, obj);
        mSimRecovery.add(r);
    }

    public void unregisterForSimRecovery(Handler h) {
    	mSimRecovery.remove(h);
    }
	
    public void registerForVirtualSimOn(Handler h, int what, Object obj) {
        Registrant r = new Registrant (h, what, obj);
        mVirtualSimOn.add(r);
    }

    public void unregisterForVirtualSimOn(Handler h) {
    	mVirtualSimOn.remove(h);
    }

    public void registerForVirtualSimOff(Handler h, int what, Object obj) {
        Registrant r = new Registrant (h, what, obj);
        mVirtualSimOff.add(r);
    }

    public void unregisterForVirtualSimOff(Handler h) {
    	mVirtualSimOff.remove(h);
    }

    public void registerForSimPlugOut(Handler h, int what, Object obj) {
        Registrant r = new Registrant (h, what, obj);
        mSimPlugOutRegistrants.add(r);
    }

    public void unregisterForSimPlugOut(Handler h) {
    	mSimPlugOutRegistrants.remove(h);
    }

    public void registerForSimPlugIn(Handler h, int what, Object obj) {
        Registrant r = new Registrant (h, what, obj);
        mSimPlugInRegistrants.add(r);
    }

    public void unregisterForSimPlugIn(Handler h) {
    	mSimPlugInRegistrants.remove(h);
    }

    //sm cause rac
    public void registerForRacUpdate(Handler h, int what, Object obj) {
        Registrant r = new Registrant (h, what, obj);

        mRacUpdateRegistrants.add(r);
    }

    public void unregisterForRacUpdate(Handler h) {
        mRacUpdateRegistrants.remove(h);
    } 

    public void setScriResult(Handler h, int what, Object obj) {
        mScriResultRegistrant = new Registrant (h, what, obj);
    }

    public void unSetScriResult(Handler h) {
        mScriResultRegistrant.clear();
    }

    public void setOnIncomingCallIndication(Handler h, int what, Object obj) {
        mIncomingCallIndicationRegistrant = new Registrant(h, what, obj);
    }

    public void unsetOnIncomingCallIndication(Handler h) {
        mIncomingCallIndicationRegistrant.clear();
    }

    public void setGprsDetach(Handler h, int what, Object obj) {
        mGprsDetachRegistrant = new Registrant(h, what, obj);
    }

    public void unSetGprsDetach(Handler h) {
        if (mGprsDetachRegistrant != null) {
            mGprsDetachRegistrant.clear();
        }
    }

    public void sendBTSIMProfile(int nAction, int nType, String strData, Message response){
    }

    public void doSimAuthentication (String strRand, Message response){
    }

    public void doUSimAuthentication (String strRand, String strAutn,  Message response){
    }
	
    public void setNetworkSelectionModeManualWithAct(String operatorNumeric, 
                                                                  String act, Message result) {
    }

    public void queryIccId(Message result){
    }

    public void get3GCapabilitySIM(Message response) {
	}

    public void set3GCapabilitySIM(int simId, Message response) {
	}

    public void getPOLCapabilty(Message response) {
    }    
    public void getCurrentPOLList(Message response) {
    }
    public void setPOLEntry(int index, String numeric, int nAct, Message response) {
    }

    public void queryUPBCapability(Message response){
    }

    public void editUPBEntry(int entryType, int adnIndex, int entryIndex, String strVal, String tonForNum, Message response) {
    }
    
    public void deleteUPBEntry(int entryType, int adnIndex, int entryIndex, Message response) {
    }
    public void readUPBGasList(int startIndex, int endIndex, Message response) {
    }

    public void readUPBGrpEntry(int adnIndex, Message response) {
    }
    
    public void writeUPBGrpEntry(int adnIndex, int[] grpIds, Message response) {
    }
    //MTK-END [mtk04070][111118][ALPS00093395]MTK proprietary methods
    
    // MTK-START [ALPS000xxxxx] MTK code port to ICS added by mtk80589 in 2011.11.16
    public void setOnNewSMS(Handler h, int what, Object obj) {
        //mSMSRegistrant = new Registrant (h, what, obj);
        setOnNewGsmSms(h, what, obj);
    }

    public void unSetOnNewSMS(Handler h) {
        mGsmSmsRegistrant.clear();
    }
    public void setPreferredNetworkTypeRIL(int NetworkType) {
    }
    // MTK-END [ALPS000xxxxx] MTK code port to ICS added by mtk80589 in 2011.11.16

    //MTK-START [mtkXXXXX][120208][APLS00109092] Replace "RIL_UNSOL_SIM_MISSING in RIL.java" with "acively query SIM missing status"
    public void notifySimMissing() {
    }
    public void detectSimMissing(Message result) {
    }
    
    public void setSimRecoveryOn(int Type, Message response){
    }
    
    public void getSimRecoveryOn( Message response){
    }
    //MTK-END [mtkXXXXX][120208][APLS00109092] Replace "RIL_UNSOL_SIM_MISSING in RIL.java" with "acively query SIM missing status"

    public void registerForPsNetworkStateChanged(Handler h, int what, Object obj) {
        Registrant r = new Registrant (h, what, obj);

        mPsNetworkStateRegistrants.add(r);
    }

    public void unregisterForPsNetworkStateChanged(Handler h) {
        mPsNetworkStateRegistrants.remove(h);
    }    

    //ALPS00302698 START
    public void registerForEfCspPlmnModeBitChanged(Handler h, int what, Object obj) {
        mEfCspPlmnModeBitRegistrant = new Registrant(h, what, obj);
    }

    public void unregisterForEfCspPlmnModeBitChanged(Handler h) {
        mEfCspPlmnModeBitRegistrant.clear();
    }
    //ALPS00302698 END

    //ALPS00248788
    public void setInvalidSimInfo(Handler h, int what, Object obj) {
        mInvalidSimInfoRegistrant = new Registrant (h, what, obj);
    }

    public void unSetInvalidSimInfo(Handler h) {
        mInvalidSimInfoRegistrant.clear();
    }

    public void setTRM(int mode, Message result) {
    }

    public void setViaTRM(int mode, Message result) {
    }

    public void getCalibrationData(Message result){}

    public void getNitzTime (Message result) {
    }
    public void queryUimInsertedStatus(Message result) {
    }
    public void notifyUimInsertedStatus(int status){
    }
    //M for LGE begin
    public void getPhoneBookStringsLength(Message result) {
        
    }
    public void getPhoneBookMemStorage(Message result) {
        
    }
    public void setPhoneBookMemStorage(String storage, String password, Message result) {
        
    }
    
    public void readPhoneBookEntryExt(int index1, int index2, Message result) {
        
    }
    public void writePhoneBookEntryExt(PBEntry entry, Message result) {
        
    }
    //M for LGE end
    //START [mtk80601][111212][ALPS00093395]IPO feature
    public void setRadioPowerOn(Message result) {
    }
    //END [mtk80601][111212][ALPS00093395]IPO feature
    
    public void getSmsParameters(Message response) {
    }
    
    public void registerForIMEILock(Handler h, int what, Object obj) {
        Registrant r = new Registrant (h, what, obj);
        mImeiLockRegistrant.add(r);
    }

    public void unregisterForIMEILock(Handler h) {
        mImeiLockRegistrant.remove(h);
    } 

    //Add by gfzhu VIA start
    public void requestSwitchHPF (boolean enableHPF, Message response){
    }

    public void setAvoidSYS (boolean avoidSYS, Message response){
    }

    public void getAvoidSYSList (Message response){
    }

    public void queryCDMANetworkInfo (Message response){
    }

    public void registerForCallAccepted(Handler h, int what, Object obj) {
        Registrant r = new Registrant (h, what, obj);
        mAcceptedRegistrant.add(r);
    }

    public void unregisterForCallAccepted(Handler h) {
        mAcceptedRegistrant.remove(h);
    }

    public void registerForCipherIndication(Handler h, int what, Object obj) {
        Registrant r = new Registrant (h, what, obj);
        mCipherIndicationRegistrant.add(r);
    }

    public void unregisterForCipherIndication(Handler h) {
        mCipherIndicationRegistrant.remove(h);
    }

    //[ALPS00389658]
    public void setMeid(String meid, Message response){}
//Add by gfzhu VIA end

    public void setSmsParameters(SmsParameters params, Message response) {
    }

    // MVNO-API
    public void setPhoneComponent(Phone phone) {
        this.mPhone = phone;
    }

    public void registerForGetAvailableNetworksDone(Handler h, int what, Object obj) {
        Registrant r = new Registrant (h, what, obj);
        mGetAvailableNetworkDoneRegistrant.add(r);
    }
	
    public void unregisterForGetAvailableNetworksDone(Handler h) {
        mGetAvailableNetworkDoneRegistrant.remove(h);
    }
    
    public void setCnapNotify(Handler h, int what, Object obj) {
        mCnapNotifyRegistrant = new Registrant (h, what, obj);
    }
	
    public void unSetCnapNotify(Handler h) {
        mCnapNotifyRegistrant.clear();
    }	
    
    public void setEtws(int mode,Message result) {}
    
    public void setOnEtwsNotification(Handler h, int what, Object obj) {
        mEtwsNotificationRegistrant = new Registrant(h, what, obj);
    }

    public void unSetOnEtwsNotification(Handler h) {
        mEtwsNotificationRegistrant.clear();
    }
    
    public void setCellBroadcastChannelConfigInfo(String config, int cb_set_type, Message response) {}
    public void setCellBroadcastLanguageConfigInfo(String config, Message response) {}
    public void queryCellBroadcastConfigInfo(Message response) {}
    public void setAllCbLanguageOn(Message response) {}

    //MTK-START [mtk80776] WiFi Calling
    public void uiccSelectApplication(String aid, Message response) {}
    public void uiccDeactivateApplication(int sessionId, Message response) {}
    public void uiccApplicationIO(int sessionId, int command, int fileId, String path,
                    int p1, int p2, int p3, String data, String pin2, Message response) {}
    public void uiccAkaAuthenticate(int sessionId, byte[] rand, byte[] autn, Message response){}
    public void uiccGbaAuthenticateBootstrap(int sessionId, byte[] rand, byte[] autn, Message response){}
    public void uiccGbaAuthenticateNaf(int sessionId, byte[] nafId, Message response){}
    //MTK-END [mtk80776] WiFi Calling

    // VIA add for China Telecom auto-register sms
    public void queryCDMASmsAndPBStatus (Message response){}
    public void queryCDMANetWorkRegistrationState (Message response){}
    // VIA add end for China Telecom auto-register sms

    public void detachPS(Message response) {}
    public void setOnPlmnChangeNotification(Handler h, int what, Object obj) {
        mPlmnChangeNotificationRegistrant = new Registrant(h, what, obj);
    }

    public void unSetOnPlmnChangeNotification(Handler h) {
        mPlmnChangeNotificationRegistrant.clear();
    }

    public void setOnGSMSuspended(Handler h, int what, Object obj) {
        mGSMSuspendedRegistrant = new Registrant(h, what, obj);
    }

    public void unSetOnGSMSuspended(Handler h) {
        mGSMSuspendedRegistrant.clear();
    }

    // Femtocell (CSG) feature START	
    public void getFemtoCellList(String operatorNumeric,int rat,Message response){}
    public void abortFemtoCellList(Message response){}
    public void selectFemtoCell(FemtoCellInfo femtocell, Message response){}
    public void registerForFemtoCellInfo(Handler h, int what, Object obj) {
        Registrant r = new Registrant (h, what, obj);

        mFemtoCellInfoRegistrants.add(r);
    }

    public void unregisterForFemtoCellInfo(Handler h) {
        mFemtoCellInfoRegistrants.remove(h);
    }

    // Femtocell (CSG) feature END
	
	    //via support start [ALPS00421033]
    public void registerForNetworkTypeChanged(Handler h, int what, Object obj) {
        Registrant r = new Registrant (h, what, obj);
        mNetworkTypeChangedRegistrant.add(r);
    }

    public void unregisterForNetworkTypeChanged(Handler h) {
        mNetworkTypeChangedRegistrant.remove(h);
    }
    //via support end [ALPS00421033]


    // [mtk02772] start
    // add for cdma slot inserted by gsm card
    public void registerForInvalidSimDetected(Handler h, int what, Object obj) {
        Registrant r = new Registrant (h, what, obj);
        mInvalidSimDetectedRegistrant.add(r);
    }

    public void unregisterForInvalidSimDetected(Handler h) {
        mInvalidSimDetectedRegistrant.remove(h);
    }
    // [mtk02772] end


    //VIA-START VIA AGPS
    public void registerForViaGpsEvent(Handler h, int what, Object obj) 
    {
        Registrant r = new Registrant (h, what, obj);
        mViaGpsEvent.add(r);
    }
    public void unregisterForViaGpsEvent(Handler h) {
        mViaGpsEvent.remove(h);
    }
    //VIA-END VIA AGPS

    //8.2.11 TC-IRLAB-02011 start
    public void registerForMccMncChange(Handler h, int what, Object obj) {
        Rlog.d(LOG_TAG, "registerForMccMncChange h=" + h + " w=" + what);        
        Registrant r = new Registrant (h, what, obj);
        mMccMncChangeRegistrants.add(r);
    }

    public void unregisterForMccMncChange(Handler h) {
        Rlog.d(LOG_TAG, "unregisterForMccMncChange");
        mMccMncChangeRegistrants.remove(h);        
    }
    //8.2.11 TC-IRLAB-02011 end

    public void setMdnNumber(String mdn, Message response) {}

    // VIA-START SET ARSI REPORT TRRESHOLD-20130709
    public void setArsiReportThreshold(int threshold, Message response) {}
    // VIA-END SET ARSI REPORT TRRESHOLD-20130709

    public void setOnPacketsFlushNotification(Handler h, int what, Object obj)
    {
        mPacketsFlushNotificationRegistrant = new Registrant(h, what, obj);
    }

    public void unSetOnPacketsFlushNotification(Handler h)
    {
        mPacketsFlushNotificationRegistrant.clear();
    }

    // / M: For update OPLMN
    public void setOplmn(String oplmnInfo, Message response) {
    }

    // / M: For get OPLMN Version
    public void getOplmnVersion(Message response) {
    }

    public void dialUpCsd(String dialUpNumber, String slotId, Message result) {
    }
  
}
