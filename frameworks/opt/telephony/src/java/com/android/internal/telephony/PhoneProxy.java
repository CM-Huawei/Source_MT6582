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
 * Copyright (C) 2008 The Android Open Source Project
 * Copyright (c) 2013, The Linux Foundation. All rights reserved.
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


import android.app.ActivityManagerNative;
import android.content.Context;
import android.content.Intent;
import android.net.LinkCapabilities;
import android.net.LinkProperties;
import android.os.AsyncResult;
import android.os.Handler;
import android.os.Message;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.telephony.CellInfo;
import android.telephony.CellLocation;
import android.telephony.ServiceState;
import android.telephony.SignalStrength;
import android.telephony.Rlog;

import com.android.internal.telephony.test.SimulatedRadioControl;
import com.android.internal.telephony.uicc.IccCardProxy;
import com.android.internal.telephony.uicc.IsimRecords;
import com.android.internal.telephony.uicc.UsimServiceTable;
import com.android.internal.telephony.CallManager;

import com.android.internal.telephony.cdma.CDMAPhone;
import com.android.internal.telephony.gsm.GSMPhone;
import com.android.internal.telephony.sip.SipPhone;

//MTK-START [mtk04070][111117][ALPS00093395]MTK added
import com.android.internal.telephony.gsm.SmsBroadcastConfigInfo;
import com.mediatek.common.featureoption.FeatureOption;
import com.android.internal.telephony.gsm.NetworkInfoWithAcT;
import com.android.internal.telephony.uicc.IccFileHandler;
import com.android.internal.telephony.uicc.IccRecords;
//MTK-END [mtk04070][111117][ALPS00093395]MTK added

import java.util.List;
import com.mediatek.common.telephony.gsm.FemtoCellInfo;

public class PhoneProxy extends Handler implements Phone {
    public final static Object lockForRadioTechnologyChange = new Object();

    private Phone mActivePhone;
    private CommandsInterface mCommandsInterface;
    private IccSmsInterfaceManager mIccSmsInterfaceManager;
    private IccPhoneBookInterfaceManagerProxy mIccPhoneBookInterfaceManagerProxy;
    private PhoneSubInfoProxy mPhoneSubInfoProxy;
    private IccCardProxy mIccCardProxy;

    private boolean mResetModemOnRadioTechnologyChange = false;

    private int mRilVersion;

    private static final int EVENT_VOICE_RADIO_TECH_CHANGED = 1;
    private static final int EVENT_RADIO_ON = 2;
    private static final int EVENT_REQUEST_VOICE_RADIO_TECH_DONE = 3;
    private static final int EVENT_RIL_CONNECTED = 4;
    private static final int EVENT_UPDATE_PHONE_OBJECT = 5;

    private static final String LOG_TAG = "PhoneProxy";

    //***** Class Methods
    public PhoneProxy(PhoneBase phone) {
        mActivePhone = phone;
        mResetModemOnRadioTechnologyChange = SystemProperties.getBoolean(
                TelephonyProperties.PROPERTY_RESET_ON_RADIO_TECH_CHANGE, false);
        mIccSmsInterfaceManager =
                new IccSmsInterfaceManager((PhoneBase) this.mActivePhone);
        //MTK-START [mtk04070][111117][ALPS00093395]Modified for Gemini
        if (PhoneConstants.PHONE_TYPE_GSM == phone.getPhoneType()) {
            int simId = ((GSMPhone) phone).getMySimId();
            mIccPhoneBookInterfaceManagerProxy = new IccPhoneBookInterfaceManagerProxy(
                    phone.getIccPhoneBookInterfaceManager(), simId);            
            mPhoneSubInfoProxy = new PhoneSubInfoProxy(phone.getPhoneSubInfo(), simId);
        } else if(PhoneConstants.PHONE_TYPE_CDMA == phone.getPhoneType()) {
            int simId = phone.getMySimId();
            mIccPhoneBookInterfaceManagerProxy = new IccPhoneBookInterfaceManagerProxy(
                    phone.getIccPhoneBookInterfaceManager(), simId);            
            mPhoneSubInfoProxy = new PhoneSubInfoProxy(phone.getPhoneSubInfo(), simId);
        } else {
           mIccPhoneBookInterfaceManagerProxy = new IccPhoneBookInterfaceManagerProxy(
                    phone.getIccPhoneBookInterfaceManager());
            mPhoneSubInfoProxy = new PhoneSubInfoProxy(phone.getPhoneSubInfo());
        }
        //MTK-END [mtk04070][111117][ALPS00093395]Modified for Gemini
        mCommandsInterface = ((PhoneBase)mActivePhone).mCi;

        mCommandsInterface.registerForRilConnected(this, EVENT_RIL_CONNECTED, null);
        mCommandsInterface.registerForOn(this, EVENT_RADIO_ON, null);
        mCommandsInterface.registerForVoiceRadioTechChanged(
                             this, EVENT_VOICE_RADIO_TECH_CHANGED, null);
        mIccCardProxy = new IccCardProxy(phone.getContext(), mCommandsInterface, phone.getMySimId());
        if (phone.getPhoneType() == PhoneConstants.PHONE_TYPE_GSM) {
            // For the purpose of IccCardProxy we only care about the technology family
            mIccCardProxy.setVoiceRadioTech(ServiceState.RIL_RADIO_TECHNOLOGY_UMTS);
        } else if (phone.getPhoneType() == PhoneConstants.PHONE_TYPE_CDMA) {
            mIccCardProxy.setVoiceRadioTech(ServiceState.RIL_RADIO_TECHNOLOGY_1xRTT);
        }                     
 
        if (mCommandsInterface instanceof RIL) {
            ((RIL)mCommandsInterface).startRilReceiver();
        }

        logd("setPhoneComponent()");
        mCommandsInterface.setPhoneComponent(phone); // MVNO-API
    }

    @Override
    public void handleMessage(Message msg) {
        AsyncResult ar = (AsyncResult) msg.obj;
        switch(msg.what) {
        case EVENT_RADIO_ON:
            /* Proactively query voice radio technologies */
            logd("EVENT_RADIO_ON");
            mCommandsInterface.getVoiceRadioTechnology(
                    obtainMessage(EVENT_REQUEST_VOICE_RADIO_TECH_DONE));
            break;

        case EVENT_RIL_CONNECTED:
            if (ar.exception == null && ar.result != null) {
                mRilVersion = (Integer) ar.result;
            } else {
                logd("Unexpected exception on EVENT_RIL_CONNECTED");
                mRilVersion = -1;
            }
            break;

        case EVENT_VOICE_RADIO_TECH_CHANGED:
        case EVENT_REQUEST_VOICE_RADIO_TECH_DONE:
            String what = (msg.what == EVENT_VOICE_RADIO_TECH_CHANGED) ?
                    "EVENT_VOICE_RADIO_TECH_CHANGED" : "EVENT_REQUEST_VOICE_RADIO_TECH_DONE";
            logd("EVENT_VOICE_RADIO_TECH_CHANGED");
            if (ar.exception == null) {
                if ((ar.result != null) && (((int[]) ar.result).length != 0)) {
                    int newVoiceTech = ((int[]) ar.result)[0];
                    logd(what + ": newVoiceTech=" + newVoiceTech);
                    phoneObjectUpdater(newVoiceTech);
                } else {
                    loge(what + ": has no tech!");
                }
            } else {
                loge(what + ": exception=" + ar.exception);
            }
            break;

        case EVENT_UPDATE_PHONE_OBJECT:
            phoneObjectUpdater(msg.arg1);
            break;

        default:
            loge("Error! This handler was not registered for this message type. Message: "
                    + msg.what);
            break;
        }
        super.handleMessage(msg);
    }

    private static void logd(String msg) {
        Rlog.d(LOG_TAG, "[PhoneProxy] " + msg);
    }

    private void logw(String msg) {
        Rlog.w(LOG_TAG, "[PhoneProxy] " + msg);
    }

    private void loge(String msg) {
        Rlog.e(LOG_TAG, "[PhoneProxy] " + msg);
    }

    private void phoneObjectUpdater(int newVoiceRadioTech) {
        logd("phoneObjectUpdater: newVoiceRadioTech=" + newVoiceRadioTech);

        if (mActivePhone != null) {
            // Check for a voice over lte replacement
            if ((newVoiceRadioTech == ServiceState.RIL_RADIO_TECHNOLOGY_LTE)) {
                int volteReplacementRat = mActivePhone.getContext().getResources().getInteger(
                        com.android.internal.R.integer.config_volte_replacement_rat);
                logd("phoneObjectUpdater: volteReplacementRat=" + volteReplacementRat);
                if (volteReplacementRat != ServiceState.RIL_RADIO_TECHNOLOGY_UNKNOWN) {
                    newVoiceRadioTech = volteReplacementRat;
                }
            }

            if(mRilVersion == 6 && getLteOnCdmaMode() == PhoneConstants.LTE_ON_CDMA_TRUE) {
                /*
                 * On v6 RIL, when LTE_ON_CDMA is TRUE, always create CDMALTEPhone
                 * irrespective of the voice radio tech reported.
                 */
                if (mActivePhone.getPhoneType() == PhoneConstants.PHONE_TYPE_CDMA) {
                    logd("phoneObjectUpdater: LTE ON CDMA property is set. Use CDMA Phone" +
                            " newVoiceRadioTech = " + newVoiceRadioTech +
                            " mActivePhone=" + mActivePhone.getPhoneName());
                    return;
                } else {
                    logd("phoneObjectUpdater: LTE ON CDMA property is set. Switch to CDMALTEPhone" +
                            " newVoiceRadioTech = " + newVoiceRadioTech +
                            " mActivePhone=" + mActivePhone.getPhoneName());
                    newVoiceRadioTech = ServiceState.RIL_RADIO_TECHNOLOGY_1xRTT;
                }
            } else {
                if ((ServiceState.isCdma(newVoiceRadioTech) &&
                        mActivePhone.getPhoneType() == PhoneConstants.PHONE_TYPE_CDMA) ||
                        (ServiceState.isGsm(newVoiceRadioTech) &&
                                mActivePhone.getPhoneType() == PhoneConstants.PHONE_TYPE_GSM)) {
                    // Nothing changed. Keep phone as it is.
                    logd("phoneObjectUpdater: No change ignore," +
                            " newVoiceRadioTech = " + newVoiceRadioTech +
                            " mActivePhone=" + mActivePhone.getPhoneName());
                    return;
                }
            }
        }

        if (newVoiceRadioTech == ServiceState.RIL_RADIO_TECHNOLOGY_UNKNOWN) {
            // We need some voice phone object to be active always, so never
            // delete the phone without anything to replace it with!
            logd("phoneObjectUpdater: Unknown rat ignore, "
                    + " newVoiceRadioTech=Unknown. mActivePhone=" + mActivePhone.getPhoneName());
            return;
        }

        boolean oldPowerState = false; // old power state to off
        if (mResetModemOnRadioTechnologyChange) {
            if (mCommandsInterface.getRadioState().isOn()) {
                oldPowerState = true;
                logd("phoneObjectUpdater: Setting Radio Power to Off");
                mCommandsInterface.setRadioPower(false, null);
            }
        }

        deleteAndCreatePhone(newVoiceRadioTech);

        if (mResetModemOnRadioTechnologyChange && oldPowerState) { // restore power state
            logd("phoneObjectUpdater: Resetting Radio");
            mCommandsInterface.setRadioPower(oldPowerState, null);
        }

        // Set the new interfaces in the proxy's
        mIccSmsInterfaceManager.updatePhoneObject((PhoneBase) mActivePhone);
        mIccPhoneBookInterfaceManagerProxy.setmIccPhoneBookInterfaceManager(mActivePhone
                .getIccPhoneBookInterfaceManager());
        mPhoneSubInfoProxy.setmPhoneSubInfo(mActivePhone.getPhoneSubInfo());

        mCommandsInterface = ((PhoneBase)mActivePhone).mCi;
        mIccCardProxy.setVoiceRadioTech(newVoiceRadioTech);

        // Send an Intent to the PhoneApp that we had a radio technology change
        Intent intent = new Intent(TelephonyIntents.ACTION_RADIO_TECHNOLOGY_CHANGED);
        intent.addFlags(Intent.FLAG_RECEIVER_REPLACE_PENDING);
        intent.putExtra(PhoneConstants.PHONE_NAME_KEY, mActivePhone.getPhoneName());
        
        /// M: For to avoid stk processing message incorrectly when the modem restart after sim swtich. @{
        if (PhoneFactory.isInternationalRoamingEnabled()) {
            intent.putExtra(PhoneConstants.PHONE_TYPE_KEY, mActivePhone.getPhoneType());
            intent.putExtra(PhoneConstants.GEMINI_SIM_ID_KEY, mActivePhone.getMySimId());

            Intent intentIVSR = new Intent(TelephonyIntents.ACTION_IVSR_NOTIFY);
            intentIVSR.putExtra(TelephonyIntents.INTENT_KEY_IVSR_ACTION, "start");
            intentIVSR.putExtra(PhoneConstants.GEMINI_SIM_ID_KEY, mActivePhone.getMySimId());
            logd("broadcast ACTION_IVSR_NOTIFY intent");
            ActivityManagerNative.broadcastStickyIntent(intentIVSR, null, UserHandle.USER_ALL);
        }
        /// M: @}

        ActivityManagerNative.broadcastStickyIntent(intent, null, UserHandle.USER_ALL);

    }

    private void deleteAndCreatePhone(int newVoiceRadioTech) {

        String outgoingPhoneName = "Unknown";
        Phone oldPhone = mActivePhone;

        if (oldPhone != null) {
            outgoingPhoneName = ((PhoneBase) oldPhone).getPhoneName();
        }

        logd("Switching Voice Phone : " + outgoingPhoneName + " >>> "
                + (ServiceState.isGsm(newVoiceRadioTech) ? "GSM" : "CDMA"));

        if (oldPhone != null) {
            CallManager.getInstance().unregisterPhone(oldPhone);
            logd("Disposing old phone..");
            oldPhone.dispose();
        }

        // Give the garbage collector a hint to start the garbage collection
        // asap NOTE this has been disabled since radio technology change could
        // happen during e.g. a multimedia playing and could slow the system.
        // Tests needs to be done to see the effects of the GC call here when
        // system is busy.
        // System.gc();

        if (PhoneFactory.isEVDODTSupport()) {
            /// M: For International roaming switch phone @{
            if (ServiceState.isCdma(newVoiceRadioTech)) {
                mActivePhone = PhoneFactory.getCdmaPhone(getMySimId());
            } else if (ServiceState.isGsm(newVoiceRadioTech)) {
                mActivePhone = PhoneFactory.getGsmPhone(getMySimId());
            }
            /// M: @}
        } else {
            if (ServiceState.isCdma(newVoiceRadioTech)) {
                mActivePhone = PhoneFactory.getCdmaPhone();
            } else if (ServiceState.isGsm(newVoiceRadioTech)) {
                mActivePhone = PhoneFactory.getGsmPhone();
            }
        }

        if (oldPhone != null && oldPhone.getPhoneType() == PhoneConstants.PHONE_TYPE_GSM) {
            int simId = oldPhone.getMySimId();
            for (int i = 0; i < PhoneConstants.GEMINI_SIM_NUM; i++) {
                Phone peerPhone = ((GSMPhone) oldPhone).getPeerPhones(i);
                if (peerPhone != null) {
                    if (mActivePhone.getPhoneType() == PhoneConstants.PHONE_TYPE_GSM) {
                        int peerSimId = peerPhone.getMySimId();
                        ((GSMPhone) peerPhone).setPeerPhones((GSMPhone) mActivePhone, simId);
                        ((GSMPhone) mActivePhone).setPeerPhones((GSMPhone) peerPhone, peerSimId);
                    } else {
                        ((GSMPhone) peerPhone).setPeerPhones(null, simId);
                    }
                }
            }
        }

        if (oldPhone != null) {
            oldPhone.removeReferences();
        }

        if(mActivePhone != null) {
            CallManager.getInstance().registerPhone(mActivePhone);

            // we will register sipPhone when we call CM.registerPhone()
            if(!(mActivePhone instanceof SipPhone)) {
                CallManager.getInstance().registerForPhoneStates(mActivePhone);
            }
        }

        oldPhone = null;
    }

    @Override
    public void updatePhoneObject(int voiceRadioTech) {
        logd("updatePhoneObject: radioTechnology=" + voiceRadioTech);
        sendMessage(obtainMessage(EVENT_UPDATE_PHONE_OBJECT, voiceRadioTech, 0, null));
    }

    @Override
    public ServiceState getServiceState() {
        return mActivePhone.getServiceState();
    }

    @Override
    public CellLocation getCellLocation() {
        return mActivePhone.getCellLocation();
    }

    /**
     * @return all available cell information or null if none.
     */
    @Override
    public List<CellInfo> getAllCellInfo() {
        return mActivePhone.getAllCellInfo();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setCellInfoListRate(int rateInMillis) {
        mActivePhone.setCellInfoListRate(rateInMillis);
    }

    @Override
    public PhoneConstants.DataState getDataConnectionState() {
        return mActivePhone.getDataConnectionState(PhoneConstants.APN_TYPE_DEFAULT);
    }

    @Override
    public PhoneConstants.DataState getDataConnectionState(String apnType) {
        return mActivePhone.getDataConnectionState(apnType);
    }

    @Override
    public DataActivityState getDataActivityState() {
        return mActivePhone.getDataActivityState();
    }

    @Override
    public Context getContext() {
        return mActivePhone.getContext();
    }

    @Override
    public void disableDnsCheck(boolean b) {
        mActivePhone.disableDnsCheck(b);
    }

    @Override
    public boolean isDnsCheckDisabled() {
        return mActivePhone.isDnsCheckDisabled();
    }

    @Override
    public PhoneConstants.State getState() {
        return mActivePhone.getState();
    }

    @Override
    public String getPhoneName() {
        return mActivePhone.getPhoneName();
    }

    @Override
    public int getPhoneType() {
        return mActivePhone.getPhoneType();
    }

    @Override
    public String[] getActiveApnTypes() {
        return mActivePhone.getActiveApnTypes();
    }

    @Override
    public String getActiveApnHost(String apnType) {
        return mActivePhone.getActiveApnHost(apnType);
    }

    @Override
    public LinkProperties getLinkProperties(String apnType) {
        return mActivePhone.getLinkProperties(apnType);
    }

    @Override
    public LinkCapabilities getLinkCapabilities(String apnType) {
        return mActivePhone.getLinkCapabilities(apnType);
    }

    @Override
    public SignalStrength getSignalStrength() {
        return mActivePhone.getSignalStrength();
    }

    @Override
    public void registerForUnknownConnection(Handler h, int what, Object obj) {
        mActivePhone.registerForUnknownConnection(h, what, obj);
    }

    @Override
    public void unregisterForUnknownConnection(Handler h) {
        mActivePhone.unregisterForUnknownConnection(h);
    }

    @Override
    public void registerForPreciseCallStateChanged(Handler h, int what, Object obj) {
        mActivePhone.registerForPreciseCallStateChanged(h, what, obj);
    }

    @Override
    public void unregisterForPreciseCallStateChanged(Handler h) {
        mActivePhone.unregisterForPreciseCallStateChanged(h);
    }

    @Override
    public void registerForNewRingingConnection(Handler h, int what, Object obj) {
        mActivePhone.registerForNewRingingConnection(h, what, obj);
    }

    @Override
    public void unregisterForNewRingingConnection(Handler h) {
        mActivePhone.unregisterForNewRingingConnection(h);
    }

    @Override
    public void registerForIncomingRing(Handler h, int what, Object obj) {
        mActivePhone.registerForIncomingRing(h, what, obj);
    }

    @Override
    public void unregisterForIncomingRing(Handler h) {
        mActivePhone.unregisterForIncomingRing(h);
    }

    @Override
    public void registerForDisconnect(Handler h, int what, Object obj) {
        mActivePhone.registerForDisconnect(h, what, obj);
    }

    @Override
    public void unregisterForDisconnect(Handler h) {
        mActivePhone.unregisterForDisconnect(h);
    }

    @Override
    public void registerForMmiInitiate(Handler h, int what, Object obj) {
        mActivePhone.registerForMmiInitiate(h, what, obj);
    }

    @Override
    public void unregisterForMmiInitiate(Handler h) {
        mActivePhone.unregisterForMmiInitiate(h);
    }

    @Override
    public void registerForMmiComplete(Handler h, int what, Object obj) {
        mActivePhone.registerForMmiComplete(h, what, obj);
    }

    @Override
    public void unregisterForMmiComplete(Handler h) {
        mActivePhone.unregisterForMmiComplete(h);
    }

    @Override
    public List<? extends MmiCode> getPendingMmiCodes() {
        return mActivePhone.getPendingMmiCodes();
    }

    @Override
    public void sendUssdResponse(String ussdMessge) {
        mActivePhone.sendUssdResponse(ussdMessge);
    }

    @Override
    public void registerForServiceStateChanged(Handler h, int what, Object obj) {
        mActivePhone.registerForServiceStateChanged(h, what, obj);
    }

    @Override
    public void unregisterForServiceStateChanged(Handler h) {
        mActivePhone.unregisterForServiceStateChanged(h);
    }

    @Override
    public void registerForSuppServiceNotification(Handler h, int what, Object obj) {
        mActivePhone.registerForSuppServiceNotification(h, what, obj);
    }

    @Override
    public void unregisterForSuppServiceNotification(Handler h) {
        mActivePhone.unregisterForSuppServiceNotification(h);
    }

    @Override
    public void registerForSuppServiceFailed(Handler h, int what, Object obj) {
        mActivePhone.registerForSuppServiceFailed(h, what, obj);
    }

    @Override
    public void unregisterForSuppServiceFailed(Handler h) {
        mActivePhone.unregisterForSuppServiceFailed(h);
    }

    @Override
    public void registerForInCallVoicePrivacyOn(Handler h, int what, Object obj){
        mActivePhone.registerForInCallVoicePrivacyOn(h,what,obj);
    }

    @Override
    public void unregisterForInCallVoicePrivacyOn(Handler h){
        mActivePhone.unregisterForInCallVoicePrivacyOn(h);
    }

    @Override
    public void registerForInCallVoicePrivacyOff(Handler h, int what, Object obj){
        mActivePhone.registerForInCallVoicePrivacyOff(h,what,obj);
    }

    @Override
    public void unregisterForInCallVoicePrivacyOff(Handler h){
        mActivePhone.unregisterForInCallVoicePrivacyOff(h);
    }

    @Override
    public void registerForCdmaOtaStatusChange(Handler h, int what, Object obj) {
        mActivePhone.registerForCdmaOtaStatusChange(h,what,obj);
    }

    @Override
    public void unregisterForCdmaOtaStatusChange(Handler h) {
         mActivePhone.unregisterForCdmaOtaStatusChange(h);
    }

    @Override
    public void registerForSubscriptionInfoReady(Handler h, int what, Object obj) {
        mActivePhone.registerForSubscriptionInfoReady(h, what, obj);
    }

    @Override
    public void unregisterForSubscriptionInfoReady(Handler h) {
        mActivePhone.unregisterForSubscriptionInfoReady(h);
    }

    @Override
    public void registerForEcmTimerReset(Handler h, int what, Object obj) {
        mActivePhone.registerForEcmTimerReset(h,what,obj);
    }

    @Override
    public void unregisterForEcmTimerReset(Handler h) {
        mActivePhone.unregisterForEcmTimerReset(h);
    }

    @Override
    public void registerForRingbackTone(Handler h, int what, Object obj) {
        mActivePhone.registerForRingbackTone(h,what,obj);
    }

    @Override
    public void unregisterForRingbackTone(Handler h) {
        mActivePhone.unregisterForRingbackTone(h);
    }

    @Override
    public void registerForResendIncallMute(Handler h, int what, Object obj) {
        mActivePhone.registerForResendIncallMute(h,what,obj);
    }

    @Override
    public void unregisterForResendIncallMute(Handler h) {
        mActivePhone.unregisterForResendIncallMute(h);
    }

    @Override
    public boolean getIccRecordsLoaded() {
        return mIccCardProxy.getIccRecordsLoaded();
    }

    @Override
    public IccCard getIccCard() {
        return mIccCardProxy;
    }

    @Override
    public void acceptCall() throws CallStateException {
        mActivePhone.acceptCall();
    }

    @Override
    public void rejectCall() throws CallStateException {
        mActivePhone.rejectCall();
    }

    @Override
    public void switchHoldingAndActive() throws CallStateException {
        mActivePhone.switchHoldingAndActive();
    }

    @Override
    public boolean canConference() {
        return mActivePhone.canConference();
    }

    @Override
    public void conference() throws CallStateException {
        mActivePhone.conference();
    }

    @Override
    public void enableEnhancedVoicePrivacy(boolean enable, Message onComplete) {
        mActivePhone.enableEnhancedVoicePrivacy(enable, onComplete);
    }

    @Override
    public void getEnhancedVoicePrivacy(Message onComplete) {
        mActivePhone.getEnhancedVoicePrivacy(onComplete);
    }

    @Override
    public boolean canTransfer() {
        return mActivePhone.canTransfer();
    }

    @Override
    public void explicitCallTransfer() throws CallStateException {
        mActivePhone.explicitCallTransfer();
    }

    @Override
    public void clearDisconnected() {
        mActivePhone.clearDisconnected();
    }

    @Override
    public Call getForegroundCall() {
        return mActivePhone.getForegroundCall();
    }

    @Override
    public Call getBackgroundCall() {
        return mActivePhone.getBackgroundCall();
    }

    @Override
    public Call getRingingCall() {
        return mActivePhone.getRingingCall();
    }

    @Override
    public Connection dial(String dialString) throws CallStateException {
        return mActivePhone.dial(dialString);
    }

    @Override
    public Connection dial(String dialString, UUSInfo uusInfo) throws CallStateException {
        return mActivePhone.dial(dialString, uusInfo);
    }

    @Override
    public boolean handlePinMmi(String dialString) {
        return mActivePhone.handlePinMmi(dialString);
    }

    @Override
    public boolean handleInCallMmiCommands(String command) throws CallStateException {
        return mActivePhone.handleInCallMmiCommands(command);
    }

    @Override
    public void sendDtmf(char c) {
        mActivePhone.sendDtmf(c);
    }

    @Override
    public void startDtmf(char c) {
        mActivePhone.startDtmf(c);
    }

    @Override
    public void stopDtmf() {
        mActivePhone.stopDtmf();
    }

    @Override
    public void setRadioPower(boolean power) {
        mActivePhone.setRadioPower(power);
    }

/* Dual Talk */
    public void setRadioPower(boolean power, Message what) {
        mCommandsInterface.setRadioPower(power, what);
    }
/* End of Dual Talk */

    @Override
    public boolean getMessageWaitingIndicator() {
        return mActivePhone.getMessageWaitingIndicator();
    }

    @Override
    public boolean getCallForwardingIndicator() {
        return mActivePhone.getCallForwardingIndicator();
    }

    @Override
    public String getLine1Number() {
        return mActivePhone.getLine1Number();
    }

    @Override
    public String getCdmaMin() {
        return mActivePhone.getCdmaMin();
    }

    @Override
    public boolean isMinInfoReady() {
        return mActivePhone.isMinInfoReady();
    }

    @Override
    public String getCdmaPrlVersion() {
        return mActivePhone.getCdmaPrlVersion();
    }

    @Override
    public String getLine1AlphaTag() {
        return mActivePhone.getLine1AlphaTag();
    }

    @Override
    public void setLine1Number(String alphaTag, String number, Message onComplete) {
        mActivePhone.setLine1Number(alphaTag, number, onComplete);
    }

    @Override
    public String getVoiceMailNumber() {
        return mActivePhone.getVoiceMailNumber();
    }

     /** @hide */
    @Override
    public int getVoiceMessageCount(){
        return mActivePhone.getVoiceMessageCount();
    }

    @Override
    public String getVoiceMailAlphaTag() {
        return mActivePhone.getVoiceMailAlphaTag();
    }

    @Override
    public void setVoiceMailNumber(String alphaTag,String voiceMailNumber,
            Message onComplete) {
        mActivePhone.setVoiceMailNumber(alphaTag, voiceMailNumber, onComplete);
    }

    @Override
    public void getCallForwardingOption(int commandInterfaceCFReason,
            Message onComplete) {
        mActivePhone.getCallForwardingOption(commandInterfaceCFReason,
                onComplete);
    }

    @Override
    public void setCallForwardingOption(int commandInterfaceCFReason,
            int commandInterfaceCFAction, String dialingNumber,
            int timerSeconds, Message onComplete) {
        mActivePhone.setCallForwardingOption(commandInterfaceCFReason,
            commandInterfaceCFAction, dialingNumber, timerSeconds, onComplete);
    }

    @Override
    public void getOutgoingCallerIdDisplay(Message onComplete) {
        mActivePhone.getOutgoingCallerIdDisplay(onComplete);
    }

    @Override
    public void setOutgoingCallerIdDisplay(int commandInterfaceCLIRMode,
            Message onComplete) {
        mActivePhone.setOutgoingCallerIdDisplay(commandInterfaceCLIRMode,
                onComplete);
    }

    @Override
    public void getCallWaiting(Message onComplete) {
        mActivePhone.getCallWaiting(onComplete);
    }

    @Override
    public void setCallWaiting(boolean enable, Message onComplete) {
        mActivePhone.setCallWaiting(enable, onComplete);
    }

    @Override
    public void getAvailableNetworks(Message response) {
        mActivePhone.getAvailableNetworks(response);
    }

    @Override
    public void cancelAvailableNetworks(Message response) {
        mActivePhone.cancelAvailableNetworks(response);
    }

    @Override
    public void setNetworkSelectionModeAutomatic(Message response) {
        mActivePhone.setNetworkSelectionModeAutomatic(response);
    }

    @Override
    public void selectNetworkManually(OperatorInfo network, Message response) {
        mActivePhone.selectNetworkManually(network, response);
    }

    @Override
    public void setPreferredNetworkType(int networkType, Message response) {
        mActivePhone.setPreferredNetworkType(networkType, response);
    }

    @Override
    public void getPreferredNetworkType(Message response) {
        mActivePhone.getPreferredNetworkType(response);
    }

    @Override
    public void getNeighboringCids(Message response) {
        mActivePhone.getNeighboringCids(response);
    }

    @Override
    public void setOnPostDialCharacter(Handler h, int what, Object obj) {
        mActivePhone.setOnPostDialCharacter(h, what, obj);
    }

    @Override
    public void setMute(boolean muted) {
        mActivePhone.setMute(muted);
    }

    @Override
    public boolean getMute() {
        return mActivePhone.getMute();
    }

    @Override
    public void setEchoSuppressionEnabled(boolean enabled) {
        mActivePhone.setEchoSuppressionEnabled(enabled);
    }

    @Override
    public void invokeOemRilRequestRaw(byte[] data, Message response) {
        mActivePhone.invokeOemRilRequestRaw(data, response);
    }

    @Override
    public void invokeOemRilRequestStrings(String[] strings, Message response) {
        mActivePhone.invokeOemRilRequestStrings(strings, response);
    }

    @Override
    public void getDataCallList(Message response) {
        mActivePhone.getDataCallList(response);
    }

    @Override
    public void updateServiceLocation() {
        mActivePhone.updateServiceLocation();
    }

    @Override
    public void enableLocationUpdates() {
        mActivePhone.enableLocationUpdates();
    }

    @Override
    public void disableLocationUpdates() {
        mActivePhone.disableLocationUpdates();
    }

    @Override
    public void setUnitTestMode(boolean f) {
        mActivePhone.setUnitTestMode(f);
    }

    @Override
    public boolean getUnitTestMode() {
        return mActivePhone.getUnitTestMode();
    }

    @Override
    public void setBandMode(int bandMode, Message response) {
        mActivePhone.setBandMode(bandMode, response);
    }

    @Override
    public void queryAvailableBandMode(Message response) {
        mActivePhone.queryAvailableBandMode(response);
    }

    @Override
    public boolean getDataRoamingEnabled() {
        return mActivePhone.getDataRoamingEnabled();
    }

    @Override
    public void setDataRoamingEnabled(boolean enable) {
        mActivePhone.setDataRoamingEnabled(enable);
    }

    @Override
    public void queryCdmaRoamingPreference(Message response) {
        mActivePhone.queryCdmaRoamingPreference(response);
    }

    @Override
    public void setCdmaRoamingPreference(int cdmaRoamingType, Message response) {
        mActivePhone.setCdmaRoamingPreference(cdmaRoamingType, response);
    }

    @Override
    public void setCdmaSubscription(int cdmaSubscriptionType, Message response) {
        mActivePhone.setCdmaSubscription(cdmaSubscriptionType, response);
    }

    @Override
    public SimulatedRadioControl getSimulatedRadioControl() {
        return mActivePhone.getSimulatedRadioControl();
    }

    @Override
    public int enableApnType(String type) {
        return mActivePhone.enableApnType(type);
    }

    @Override
    public int disableApnType(String type) {
        return mActivePhone.disableApnType(type);
    }

    @Override
    public boolean isDataConnectivityPossible() {
        return mActivePhone.isDataConnectivityPossible(PhoneConstants.APN_TYPE_DEFAULT);
    }

    @Override
    public boolean isDataConnectivityPossible(String apnType) {
        return mActivePhone.isDataConnectivityPossible(apnType);
    }

    @Override
    public String getDeviceId() {
        return mActivePhone.getDeviceId();
    }

    @Override
    public String getDeviceSvn() {
        return mActivePhone.getDeviceSvn();
    }

    @Override
    public String getSubscriberId() {
        return mActivePhone.getSubscriberId();
    }

    @Override
    public String getGroupIdLevel1() {
        return mActivePhone.getGroupIdLevel1();
    }

    @Override
    public String getIccSerialNumber() {
        return mActivePhone.getIccSerialNumber();
    }

    @Override
    public String getEsn() {
        return mActivePhone.getEsn();
    }

    @Override
    public String getMeid() {
        return mActivePhone.getMeid();
    }

    @Override
    public String getMsisdn() {
        return mActivePhone.getMsisdn();
    }

    @Override
    public String getImei() {
        return mActivePhone.getImei();
    }

    @Override
    public PhoneSubInfo getPhoneSubInfo(){
        return mActivePhone.getPhoneSubInfo();
    }

    @Override
    public IccPhoneBookInterfaceManager getIccPhoneBookInterfaceManager(){
        return mActivePhone.getIccPhoneBookInterfaceManager();
    }

    @Override
    public void setTTYMode(int ttyMode, Message onComplete) {
        mActivePhone.setTTYMode(ttyMode, onComplete);
    }

    @Override
    public void queryTTYMode(Message onComplete) {
        mActivePhone.queryTTYMode(onComplete);
    }

    @Override
    public void activateCellBroadcastSms(int activate, Message response) {
        mActivePhone.activateCellBroadcastSms(activate, response);
    }

    @Override
    public void getCellBroadcastSmsConfig(Message response) {
        mActivePhone.getCellBroadcastSmsConfig(response);
    }

    @Override
    public void setCellBroadcastSmsConfig(int[] configValuesArray, Message response) {
        mActivePhone.setCellBroadcastSmsConfig(configValuesArray, response);
    }

    @Override
    public void notifyDataActivity() {
         mActivePhone.notifyDataActivity();
    }

    @Override
    public void getSmscAddress(Message result) {
        mActivePhone.getSmscAddress(result);
    }

    @Override
    public void setSmscAddress(String address, Message result) {
        mActivePhone.setSmscAddress(address, result);
    }

    @Override
    public int getCdmaEriIconIndex() {
        return mActivePhone.getCdmaEriIconIndex();
    }

    @Override
    public String getCdmaEriText() {
        return mActivePhone.getCdmaEriText();
    }

    @Override
    public int getCdmaEriIconMode() {
        return mActivePhone.getCdmaEriIconMode();
    }

    public Phone getActivePhone() {
        return mActivePhone;
    }

    @Override
    public void sendBurstDtmf(String dtmfString, int on, int off, Message onComplete){
        mActivePhone.sendBurstDtmf(dtmfString, on, off, onComplete);
    }

    @Override
    public void exitEmergencyCallbackMode(){
        mActivePhone.exitEmergencyCallbackMode();
    }

    @Override
    public boolean needsOtaServiceProvisioning(){
        return mActivePhone.needsOtaServiceProvisioning();
    }

    @Override
    public boolean isOtaSpNumber(String dialStr){
        return mActivePhone.isOtaSpNumber(dialStr);
    }

    @Override
    public void registerForCallWaiting(Handler h, int what, Object obj){
        mActivePhone.registerForCallWaiting(h,what,obj);
    }

    @Override
    public void unregisterForCallWaiting(Handler h){
        mActivePhone.unregisterForCallWaiting(h);
    }

    @Override
    public void registerForSignalInfo(Handler h, int what, Object obj) {
        mActivePhone.registerForSignalInfo(h,what,obj);
    }

    @Override
    public void unregisterForSignalInfo(Handler h) {
        mActivePhone.unregisterForSignalInfo(h);
    }

    @Override
    public void registerForDisplayInfo(Handler h, int what, Object obj) {
        mActivePhone.registerForDisplayInfo(h,what,obj);
    }

    @Override
    public void unregisterForDisplayInfo(Handler h) {
        mActivePhone.unregisterForDisplayInfo(h);
    }

    @Override
    public void registerForNumberInfo(Handler h, int what, Object obj) {
        mActivePhone.registerForNumberInfo(h, what, obj);
    }

    @Override
    public void unregisterForNumberInfo(Handler h) {
        mActivePhone.unregisterForNumberInfo(h);
    }

    @Override
    public void registerForRedirectedNumberInfo(Handler h, int what, Object obj) {
        mActivePhone.registerForRedirectedNumberInfo(h, what, obj);
    }

    @Override
    public void unregisterForRedirectedNumberInfo(Handler h) {
        mActivePhone.unregisterForRedirectedNumberInfo(h);
    }

    @Override
    public void registerForLineControlInfo(Handler h, int what, Object obj) {
        mActivePhone.registerForLineControlInfo( h, what, obj);
    }

    @Override
    public void unregisterForLineControlInfo(Handler h) {
        mActivePhone.unregisterForLineControlInfo(h);
    }

    @Override
    public void registerFoT53ClirlInfo(Handler h, int what, Object obj) {
        mActivePhone.registerFoT53ClirlInfo(h, what, obj);
    }

    @Override
    public void unregisterForT53ClirInfo(Handler h) {
        mActivePhone.unregisterForT53ClirInfo(h);
    }

    @Override
    public void registerForT53AudioControlInfo(Handler h, int what, Object obj) {
        mActivePhone.registerForT53AudioControlInfo( h, what, obj);
    }

    @Override
    public void unregisterForT53AudioControlInfo(Handler h) {
        mActivePhone.unregisterForT53AudioControlInfo(h);
    }

    public void registerForRadioOffOrNotAvailableNotification(Handler h, int what, Object obj) {
        mActivePhone.registerForRadioOffOrNotAvailableNotification( h, what, obj);
    }

    public void unregisterForRadioOffOrNotAvailableNotification(Handler h) {
        mActivePhone.unregisterForRadioOffOrNotAvailableNotification(h);
    }
    
    public void unregisterForAvailable(Handler h) {
        mCommandsInterface.unregisterForAvailable(h);
    }

    public void registerForAvailable(Handler h, int what, Object obj) {
        mCommandsInterface.registerForAvailable(h, what, obj);
    }
    
    @Override
    public void setOnEcbModeExitResponse(Handler h, int what, Object obj){
        mActivePhone.setOnEcbModeExitResponse(h,what,obj);
    }

    @Override
    public void unsetOnEcbModeExitResponse(Handler h){
        mActivePhone.unsetOnEcbModeExitResponse(h);
    }

    @Override
    public boolean isCspPlmnEnabled() {
        return mActivePhone.isCspPlmnEnabled();
    }

     // ALPS00302698 ENS

    @Override
    public IsimRecords getIsimRecords() {
        return mActivePhone.getIsimRecords();
    }

    @Override
    public void requestIsimAuthentication(String nonce, Message response) {
        mActivePhone.requestIsimAuthentication(nonce, response);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getLteOnCdmaMode() {
        return mActivePhone.getLteOnCdmaMode();
    }

    @Override
    public void setVoiceMessageWaiting(int line, int countWaiting) {
        mActivePhone.setVoiceMessageWaiting(line, countWaiting);
    }

    @Override
    public UsimServiceTable getUsimServiceTable() {
        return mActivePhone.getUsimServiceTable();
    }

    @Override
    public void dispose() {
        mCommandsInterface.unregisterForOn(this);
        mCommandsInterface.unregisterForVoiceRadioTechChanged(this);
        mCommandsInterface.unregisterForRilConnected(this);
    }

    @Override
    public void removeReferences() {
        mActivePhone = null;
        mCommandsInterface = null;
    }

    //MTK-START [mtk04070][111117][ALPS00093395]MTK proprietary methods
    /* Add by vendor for Multiple PDP Context */
    public String getActiveApnType() {
        // TODO Auto-generated method stub
        return mActivePhone.getActiveApnType();
    }

    /* Add by vendor for Multiple PDP Context */
    public String getApnForType(String type) {
        return mActivePhone.getApnForType(type);
    }

    public void registerForCrssSuppServiceNotification(Handler h, int what, Object obj) {
        mActivePhone.registerForCrssSuppServiceNotification(h, what, obj);
    }

    public void unregisterForCrssSuppServiceNotification(Handler h) {
        mActivePhone.unregisterForCrssSuppServiceNotification(h);
    }
    
    public int getLastCallFailCause() {
        return mActivePhone.getLastCallFailCause();
    }

    /* vt start */
    public Connection vtDial(String dialString) throws CallStateException {
        return mActivePhone.vtDial(dialString);
    }

    public Connection vtDial(String dialString, UUSInfo uusInfo) throws CallStateException {
        return mActivePhone.vtDial(dialString, uusInfo);
    }

    public void acceptVtCallWithVoiceOnly() throws CallStateException {
        mActivePhone.acceptVtCallWithVoiceOnly();
    }
    /* vt end */

    public void setRadioPower(boolean power, boolean shutdown) {
        mActivePhone.setRadioPower(power, shutdown);
    }

    public void setRadioMode(int mode, Message what) {
        mCommandsInterface.setRadioMode(mode, what);
    }

    public void setAutoGprsAttach(int auto) {
        if(mActivePhone.getPhoneType() == PhoneConstants.PHONE_TYPE_GSM)
        { 
           ((GSMPhone)mActivePhone).setAutoGprsAttach(auto);
        }
    }

    public void setGprsConnType(int type) {
        ((PhoneBase)mActivePhone).setPsConnType(type);
    }

    public void getPdpContextList(Message result) {
        getDataCallList(result);
    }

    /* Add by mtk01411: For GeminiDataSubUtil to notify data connection when returned value of enableApnType() is error */
    public void notifyDataConnection(String reason, String apnType){
        ((PhoneBase)mActivePhone).notifyDataConnection(reason, apnType);
    }

    public String[] getDnsServers(String apnType) {
        return mActivePhone.getDnsServers(apnType);
    }

    //Add by mtk80372 for Barcode Number
    public String getSN() {
        return mActivePhone.getSN();
    }

    public void setCellBroadcastSmsConfig(SmsBroadcastConfigInfo[] chIdList, 
            SmsBroadcastConfigInfo[] langList, Message response) {
        mActivePhone.setCellBroadcastSmsConfig(chIdList, langList, response);    
    }

    public void queryCellBroadcastSmsActivation(Message response)
    {
        mActivePhone.queryCellBroadcastSmsActivation(response);    
    }

    /*Add by mtk80372 for Barcode number*/
    public void getMobileRevisionAndImei(int type,Message result){
        mActivePhone.getMobileRevisionAndImei(type,result);
    }

    public void getFacilityLock(String facility, String password, Message onComplete) {
        mActivePhone.getFacilityLock(facility, password, onComplete);
    }

    public void setFacilityLock(String facility, boolean enable, String password, Message onComplete) {
        mActivePhone.setFacilityLock(facility, enable, password, onComplete);
    }

    public void changeBarringPassword(String facility, String oldPwd, String newPwd, Message onComplete) {
        mActivePhone.changeBarringPassword(facility, oldPwd, newPwd, onComplete);
    }

    public void changeBarringPassword(String facility, String oldPwd, String newPwd, String newCfm, Message onComplete) {
        mActivePhone.changeBarringPassword(facility, oldPwd, newPwd, newCfm, onComplete);
    }

    public void hangupAll() throws CallStateException {
    	mActivePhone.hangupAll();
    }

    public void hangupAllEx() throws CallStateException {
    	mActivePhone.hangupAllEx();
    }

    public void hangupActiveCall() throws CallStateException {
        mActivePhone.hangupActiveCall();
    }
    public void getCurrentCallMeter(Message result) {
    	mActivePhone.getCurrentCallMeter(result);
    }
    	
    public void getAccumulatedCallMeter(Message result) {
    	mActivePhone.getAccumulatedCallMeter(result);
    }
    	
    public void getAccumulatedCallMeterMaximum(Message result) {
    	mActivePhone.getAccumulatedCallMeterMaximum(result);
    }
    	
    public void getPpuAndCurrency(Message result) {
    	mActivePhone.getPpuAndCurrency(result);
    }	
    	
    public void setAccumulatedCallMeterMaximum(String acmmax, String pin2, Message result) {
    	mActivePhone.setAccumulatedCallMeterMaximum(acmmax, pin2, result);
    }
    	
    public void resetAccumulatedCallMeter(String pin2, Message result) {
    	mActivePhone.resetAccumulatedCallMeter(pin2, result);
    }
    	
    public void setPpuAndCurrency(String currency, String ppu, String pin2, Message result) {
    	mActivePhone.setPpuAndCurrency(currency, ppu, pin2, result);
    }

    public void registerForNeighboringInfo(Handler h, int what, Object obj) {
        mActivePhone.registerForNeighboringInfo(h, what, obj);
    }

    public void unregisterForNeighboringInfo(Handler h) {
        mActivePhone.unregisterForNeighboringInfo(h);
    }	

    public void registerForNetworkInfo(Handler h, int what, Object obj) {
        mActivePhone.registerForNetworkInfo(h, what, obj);
    }

    public void unregisterForNetworkInfo(Handler h) {
        mActivePhone.unregisterForNetworkInfo(h);	
    } 

    public void refreshSpnDisplay() {
        mActivePhone.refreshSpnDisplay();
    }

    public void registerForSimInsertedStatus(Handler h, int what, Object obj) {
        mCommandsInterface.registerForSimInsertedStatus(h, what, obj);
    }

    public void unregisterForSimInsertedStatus(Handler h) {
        mCommandsInterface.unregisterForSimInsertedStatus(h);
    }

    /**
     * M: Add for international roaming feature.
     * 
     * @param h
     */
    public void unregisterForRilConnected(Handler h) {
        mCommandsInterface.unregisterForRilConnected(h);
    }

    /**
     * M: Add for international roaming feature.
     * 
     * @param h
     * @param what
     * @param obj
     */
    public void registerForRilConnected(Handler h, int what, Object obj) {
        mCommandsInterface.registerForRilConnected(h, what, obj);
    }

    public void registerForSimMissing(Handler h, int what, Object obj) {
        mCommandsInterface.registerForSimInsertedStatus(h, what, obj);
    }

    public void unregisterForSimMissing(Handler h) {
        mCommandsInterface.unregisterForSimInsertedStatus(h);
    }
    public int getSimInsertedStatus() {
        return ((RIL)mCommandsInterface).getSimInsertedStatus();
    }

    public int getNetworkHideState(){
        return mActivePhone.getNetworkHideState();
    }

    public int getMySimId() {
        return mActivePhone.getMySimId();
    }

    public void registerForSpeechInfo(Handler h, int what, Object obj) {
        mActivePhone.registerForSpeechInfo(h, what, obj);
    }

    public void unregisterForSpeechInfo(Handler h) {
        mActivePhone.unregisterForSpeechInfo(h);
    } 

    /* vt start */
    public void registerForVtStatusInfo(Handler h, int what, Object obj) {
        mActivePhone.registerForVtStatusInfo(h, what, obj);
    }

    public void unregisterForVtStatusInfo(Handler h) {
        mActivePhone.unregisterForVtStatusInfo(h);
    } 

    public void registerForVtRingInfo(Handler h, int what, Object obj) {
        mActivePhone.registerForVtRingInfo(h, what, obj);
    }

    public void unregisterForVtRingInfo(Handler h) {
        mActivePhone.unregisterForVtRingInfo(h);
    }

    public void registerForVtReplaceDisconnect(Handler h, int what, Object obj) {
        mActivePhone.registerForVtReplaceDisconnect(h, what, obj);
    }

    public void unregisterForVtReplaceDisconnect(Handler h) {
        mActivePhone.unregisterForVtReplaceDisconnect(h);
    }

    public void registerForVoiceCallIncomingIndication(
            Handler h, int what, Object obj) {
        mActivePhone.registerForVoiceCallIncomingIndication(h,what,obj);
    }

    public void unregisterForVoiceCallIncomingIndication(Handler h) {
        mActivePhone.unregisterForVoiceCallIncomingIndication(h);
    }

    
    public void registerForCipherIndication(Handler h, int what, Object obj) {
        mActivePhone.registerForCipherIndication(h, what, obj);
    }

    public void unregisterForCipherIndication(Handler h) {
        mActivePhone.unregisterForCipherIndication(h);
    }
    
    public void getVtCallForwardingOption(int commandInterfaceCFReason,
                                          Message onComplete) {
        mActivePhone.getVtCallForwardingOption(commandInterfaceCFReason, onComplete);
    }

    public void setVtCallForwardingOption(int commandInterfaceCFReason,
                                          int commandInterfaceCFAction,
                                          String dialingNumber,
                                          int timerSeconds,
                                          Message onComplete) {
        mActivePhone.setVtCallForwardingOption(commandInterfaceCFReason,
                                               commandInterfaceCFAction,
                                               dialingNumber,
                                               timerSeconds,
                                               onComplete);
    }

    public void getVtCallWaiting(Message onComplete) {
        mActivePhone.getVtCallWaiting(onComplete);
    }

    public void setVtCallWaiting(boolean enable, Message onComplete) {
        mActivePhone.setVtCallWaiting(enable, onComplete);
    }

    public void getVtFacilityLock(String facility, String password, Message onComplete) {
        mActivePhone.getVtFacilityLock(facility, password, onComplete);
    }

    public void setVtFacilityLock(String facility, boolean enable, String password, Message onComplete) {
        mActivePhone.setVtFacilityLock(facility, enable, password, onComplete);
    }
    /* vt end */
    /**
         * set GPRS transfer type: data prefer/call prefer
         */
    public void setGprsTransferType(int type, Message response) {
        mActivePhone.setGprsTransferType(type, response);
    }
    public IccFileHandler getIccFileHandler(){
	if(mActivePhone.getPhoneType() == PhoneConstants.PHONE_TYPE_GSM)
	{ 
	    return ((GSMPhone)mActivePhone).getIccFileHandler();
	}
	else
	{
	    return ((CDMAPhone)mActivePhone).getIccFileHandler();
	}
    }

    public IccServiceStatus getIccServiceStatus(IccService enService){
	if(mActivePhone.getPhoneType() == PhoneConstants.PHONE_TYPE_GSM)
	{ 	    
	    return ((GSMPhone)mActivePhone).getIccServiceStatus(enService);
	}
	else
	{
	    return ((CDMAPhone)mActivePhone).getIccServiceStatus(enService);
	}
    }
    // TODO LEGO SIM
    public void sendBtSimProfile(int nAction, int nType, String strData, Message response) {
         mActivePhone.sendBtSimProfile(nAction, nType, strData, response);
    }

    public void updateSimIndicateState(){
        mActivePhone.updateSimIndicateState();
    }

    public int getSimIndicatorState() {
        return mActivePhone.getSimIndicatorState();
    }

    public void doSimAuthentication (String strRand, Message result) {
        mActivePhone.doSimAuthentication(strRand,  result);
    }

    public void doUSimAuthentication (String strRand, String strAutn, Message result) {
	 mActivePhone.doUSimAuthentication(strRand, strAutn, result);
    }
//MTK-START [mtk80601][111212][ALPS00093395]IPO feature
    public void setRadioPowerOn(){
        mActivePhone.setRadioPowerOn();
    }
//MTK-END [mtk80601][111212][ALPS00093395]IPO feature
    public void updateMobileData() {
        ((PhoneBase)mActivePhone).updateMobileData();
    }

/* 3G switch start */
    public int get3GCapabilitySIM() {
        return mActivePhone.get3GCapabilitySIM();
    }

    public boolean set3GCapabilitySIM(int simId) {
        return mActivePhone.set3GCapabilitySIM(simId);
    }

    public boolean isRadioAvailable() {
        if (mActivePhone instanceof GSMPhone)
            return ((GSMPhone)mActivePhone).isRadioAvailable();
        else if (mActivePhone instanceof CDMAPhone)
            return ((CDMAPhone)mActivePhone).isRadioAvailable();
        else
            return false;
	}

    public boolean isGSMRadioAvailable() {
        if (mActivePhone instanceof GSMPhone)
            return ((GSMPhone)mActivePhone).isRadioAvailable();
        else
            return false;
    }
    public static boolean is3GSwitchEnable() {
        return FeatureOption.MTK_GEMINI_3G_SWITCH;
    }

    public boolean isBspPackage() {
        return FeatureOption.MTK_BSP_PACKAGE;
    }
/* 3G switch end */

    public boolean isWCDMAPrefered() {
        return FeatureOption.MTK_RAT_WCDMA_PREFERRED;
    }

    public void getPolCapability(Message onComplete) {
        mActivePhone.getPolCapability(onComplete);
    }

    public void getPreferedOperatorList(Message onComplete) {
        mActivePhone.getPreferedOperatorList(onComplete);
    }

    public void setPolEntry(NetworkInfoWithAcT networkWithAct, Message onComplete) {
        mActivePhone.setPolEntry(networkWithAct, onComplete);
    }

    public void forceNotifyServiceStateChange() {
        if (mActivePhone instanceof GSMPhone)
            ((GSMPhone)mActivePhone).forceNotifyServiceStateChange();
    }

    public void setPreferredNetworkTypeRIL(int NetworkType) {
        logd("PhoneProxy,setPreferredNetworkTypeRIL,Type="+ NetworkType);
        if (mActivePhone instanceof GSMPhone)
           ((GSMPhone)mActivePhone).setPreferredNetworkTypeRIL(NetworkType);
        else
           logd("mActivePhone is not GSMPhone");

    }

    public void setCurrentPreferredNetworkType() {
        logd("PhoneProxy,setCurrentPreferredNetworkType");
        if (mActivePhone instanceof GSMPhone)
            ((GSMPhone)mActivePhone).setCurrentPreferredNetworkType();
        else
            logd("mActivePhone is not GSMPhone");
    }

    //ALPS00279048
    public void setCRO(int onoff, Message onComplete) {
        mActivePhone.setCRO(onoff, onComplete);
    }
	
    // ALPS00294581
    public void notifySimMissingStatus(boolean isSimInsert) {
        mActivePhone.notifySimMissingStatus(isSimInsert);
    }

    // ALPS00302702 RAT balancing
    public int getEfRatBalancing() {
        return mActivePhone.getEfRatBalancing();
    }

    // MVNO-API START
    public String getMvnoMatchType() {
        return mActivePhone.getMvnoMatchType();
    }
 
    public String getMvnoPattern(String type){
        return mActivePhone.getMvnoPattern(type);
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
        return mActivePhone.setFDTimerValue(newTimerValue, onComplete);
    }

    //[New R8 modem FD]
    /**
       * @return String FD Timer Value
       * @internal
       */
    public String[] getFDTimerValue() {
        return mActivePhone.getFDTimerValue();
    }	

    // Femtocell (CSG) feature START
    public void getFemtoCellList(String operatorNumeric,int rat,Message response){
        mActivePhone.getFemtoCellList(operatorNumeric,rat,response);
    }		

    public void abortFemtoCellList(Message response){
        mActivePhone.abortFemtoCellList(response);
    }		

    public void selectFemtoCell(FemtoCellInfo femtocell, Message response){
        mActivePhone.selectFemtoCell(femtocell, response);
    }
    // Femtocell (CSG) feature END

    //MTK-END [mtk04070][111117][ALPS00093395]MTK proprietary methods

    //Add by gfzhu VIA start
    public void requestSwitchHPF (boolean enableHPF, Message response) {
        mActivePhone.requestSwitchHPF(enableHPF, response);
    }

    public void setAvoidSYS (boolean avoidSYS, Message response) {
        mActivePhone.setAvoidSYS(avoidSYS, response);
    }

    public void getAvoidSYSList (Message response) {
        mActivePhone.getAvoidSYSList(response);
    }

    public void queryCDMANetworkInfo (Message response){
        mActivePhone.queryCDMANetworkInfo(response);
    }

    public int dialUpCsd(int simId, String dialUpNumber) {
        return mActivePhone.dialUpCsd(simId, dialUpNumber);
    }

    
    //Add by gfzhu VIA end

    /**
     * M: Add for international roaming feature to switch phone object in PhoneProxy.
     * 
     * @param newVoiceRadioTech
     * @hide
     */
    public void updatePhoneObjectForSwitchPhone(int newVoiceRadioTech) {
        updatePhoneObject(newVoiceRadioTech);
    }
}
