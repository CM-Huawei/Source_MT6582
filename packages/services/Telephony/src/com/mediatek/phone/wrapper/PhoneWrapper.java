/* Copyright Statement:
 *
 * This software/firmware and related documentation ("MediaTek Software") are
 * protected under relevant copyright laws. The information contained herein
 * is confidential and proprietary to MediaTek Inc. and/or its licensors.
 * Without the prior written permission of MediaTek inc. and/or its licensors,
 * any reproduction, modification, use or disclosure of MediaTek Software,
 * and information contained herein, in whole or in part, shall be strictly prohibited.
 */
/* MediaTek Inc. (C) 2013. All rights reserved.
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

package com.mediatek.phone.wrapper;

import java.util.ArrayList;
import java.util.List;

import android.content.ContentResolver;
import android.content.Context;
import android.os.Handler;
import android.os.Message;
import android.os.SystemProperties;
import android.provider.Settings.System;
import android.telephony.ServiceState;

import com.android.internal.telephony.Call;
import com.android.internal.telephony.CallManager;
import com.android.internal.telephony.CallStateException;
import com.android.internal.telephony.CallerInfo;
import com.android.internal.telephony.CommandsInterface;
import com.android.internal.telephony.IccCard;
import com.android.internal.telephony.MmiCode;
import com.android.internal.telephony.OperatorInfo;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.PhoneProxy;
import com.android.internal.telephony.TelephonyIntents;
import com.android.internal.telephony.TelephonyProperties;
import com.android.internal.telephony.gemini.GeminiNetworkSubUtil;
import com.android.internal.telephony.gemini.GeminiPhone;
import com.android.internal.telephony.gsm.NetworkInfoWithAcT;
import com.android.internal.telephony.gsm.SmsBroadcastConfigInfo;
import com.android.phone.PhoneGlobals;

import com.mediatek.common.featureoption.FeatureOption;
import com.mediatek.phone.GeminiConstants;
import com.mediatek.phone.PhoneLog;
import com.mediatek.phone.gemini.GeminiUtils;
import com.mediatek.telephony.SimInfoManager;
import com.mediatek.telephony.SimInfoManager.SimInfoRecord;

import junit.framework.Assert;

public class PhoneWrapper {
    private static final String TAG = "PhoneWrapper";

    private static final int NO_SIM_CONNECTED = 7;// No sim has been connected
    public static final int UNSPECIFIED_SLOT_ID = -1;

    public static final String EXTRA_3G_SWITCH_LOCKED = TelephonyIntents.EXTRA_3G_SWITCH_LOCKED;
    public static final String EXTRA_3G_SIM = TelephonyIntents.EXTRA_3G_SIM;
    public static final String EVENT_3G_SWITCH_START_MD_RESET = TelephonyIntents.EVENT_3G_SWITCH_START_MD_RESET;
    public static final String EVENT_3G_SWITCH_DONE = TelephonyIntents.EVENT_3G_SWITCH_DONE;
    public static final String EVENT_PRE_3G_SWITCH = TelephonyIntents.EVENT_PRE_3G_SWITCH;
    public static final String EVENT_3G_SWITCH_LOCK_CHANGED = TelephonyIntents.EVENT_3G_SWITCH_LOCK_CHANGED;
    /**
     * Get the current ServiceState.
     *
     * @param phone
     * @param slotId
     * @return
     */
    public static ServiceState getServiceState(Phone phone, int slotId) {
        Assert.assertNotNull(phone);
        ServiceState serviceState = null;
        if (GeminiUtils.isGeminiSupport()) {
            PhoneLog.d(TAG, "[getServiceState], slotId = " + slotId);
            if (GeminiUtils.isValidSlot(slotId)) {
                serviceState = ServiceState.newFromBundle(PhoneGlobals.getInstance().phoneMgrEx
                        .getServiceState(slotId));
            }
        } else {
            serviceState = phone.getServiceState();
        }
        return serviceState;
    }

    /**
     * Returns the alpha tag associated with the voice mail number.
     *
     * @param phone
     * @param slotId
     * @return
     */
    public static String getVoiceMailAlphaTag(Phone phone, int slotId) {
        Assert.assertNotNull(phone);
        String alphaTag = "";
        if (GeminiUtils.isGeminiSupport() && !GeminiUtils.isValidSlot(slotId)) {
            PhoneLog.e(TAG, "[getVoiceMailAlphaTag], the slotId is invalid!");
        } else {
            alphaTag = getPhoneBySlotId(phone, slotId).getVoiceMailAlphaTag();
        }
        return alphaTag;
    }

    /**
     * Whether current phone is radio on
     *
     * @param phone
     * @return
     */
    public static boolean isRadioOn(Phone phone) {
        Assert.assertNotNull(phone);
        if (GeminiUtils.isGeminiSupport()) {
            final int[] slots = GeminiUtils.getSlots();
            GeminiPhone gPhone = (GeminiPhone) phone;
            for (int slot : slots) {
                if (gPhone.isRadioOnGemini(slot)) {
                    return true;
                }
            }
            return false;
        }
        return phone.getServiceState().getVoiceRegState() != ServiceState.STATE_POWER_OFF;
    }

    public static boolean isRadioOn(Phone phone, int slotId) {
        Assert.assertNotNull(phone);
        if (GeminiUtils.isGeminiSupport()) {
            if (GeminiUtils.isValidSlot(slotId)) {
                return ((GeminiPhone) phone).isRadioOnGemini(slotId);
            } else {
                PhoneLog.e(TAG, "[isRadioOn], the slotId is invalid");
                return false;
            }
        }
        return phone.getServiceState().getVoiceRegState() != ServiceState.STATE_POWER_OFF;
    }

    /**
     * gets a VT call forwarding option. The return value of
     * ((AsyncResult)onComplete.obj) is an array of CallForwardInfo.
     *
     * @param phone
     * @param commandInterfaceCFReason
     * @param onComplete
     * @param slotId
     */
    public static void getVtCallForwardingOption(Phone phone, int commandInterfaceCFReason,
            Message onComplete, int slotId) {
        Assert.assertNotNull(phone);
        if (GeminiUtils.isGeminiSupport() && !GeminiUtils.isValidSlot(slotId)) {
            PhoneLog.e(TAG, "[getVtCallForwardingOption], the slotId is invalid!");
            return;
        }
        getPhoneBySlotId(phone, slotId).getVtCallForwardingOption(commandInterfaceCFReason,
                onComplete);
    }

    /**
     * gets a call forwarding option. The return value of
     * ((AsyncResult)onComplete.obj) is an array of CallForwardInfo.
     *
     * @param phone
     * @param commandInterfaceCFReason
     * @param onComplete
     * @param slotId
     */
    public static void getCallForwardingOption(Phone phone, int commandInterfaceCFReason,
            Message onComplete, int slotId) {
        Assert.assertNotNull(phone);
        if (GeminiUtils.isGeminiSupport() && !GeminiUtils.isValidSlot(slotId)) {
            PhoneLog.e(TAG, "[getCallForwardingOption], the slotId is invalid!");
            return;
        }
        getPhoneBySlotId(phone, slotId).getCallForwardingOption(commandInterfaceCFReason,
                onComplete);
    }

    /**
     * sets a VT call forwarding option.
     *
     * @param phone
     * @param commandInterfaceCFReason
     * @param commandInterfaceCFAction
     * @param dialingNumber
     * @param timerSeconds
     * @param onComplete
     * @param slotId
     */
    public static void setVtCallForwardingOption(Phone phone, int commandInterfaceCFReason,
            int commandInterfaceCFAction, String dialingNumber, int timerSeconds,
            Message onComplete, int slotId) {
        Assert.assertNotNull(phone);
        if (GeminiUtils.isGeminiSupport() && !GeminiUtils.isValidSlot(slotId)) {
            PhoneLog.e(TAG, "[setVtCallForwardingOption], the slotId is invalid!");
            return;
        }
        getPhoneBySlotId(phone, slotId).setVtCallForwardingOption(commandInterfaceCFReason,
                commandInterfaceCFAction, dialingNumber, timerSeconds, onComplete);
    }

    /**
     * sets a call forwarding option.
     *
     * @param phone
     * @param commandInterfaceCFReason
     * @param commandInterfaceCFAction
     * @param dialingNumber
     * @param timerSeconds
     * @param onComplete
     * @param slotId
     */
    public static void setCallForwardingOption(Phone phone, int commandInterfaceCFReason,
            int commandInterfaceCFAction, String dialingNumber, int timerSeconds,
            Message onComplete, int slotId) {
        Assert.assertNotNull(phone);
        if (GeminiUtils.isGeminiSupport() && !GeminiUtils.isValidSlot(slotId)) {
            PhoneLog.e(TAG, "[setCallForwardingOption], the slotId is invalid!");
            return;
        }
        getPhoneBySlotId(phone, slotId).setCallForwardingOption(commandInterfaceCFReason,
                commandInterfaceCFAction, dialingNumber, timerSeconds, onComplete);
    }

    /**
     * gets VT call waiting activation state. The return value of
     * ((AsyncResult)onComplete.obj) is an array of int, with a length of 1.
     *
     * @param phone
     * @param onComplete
     * @param slotId
     */
    public static void getVtCallWaiting(Phone phone, Message onComplete, int slotId) {
        Assert.assertNotNull(phone);
        if (GeminiUtils.isGeminiSupport() && !GeminiUtils.isValidSlot(slotId)) {
            PhoneLog.e(TAG, "[getVtCallWaiting], the slotId is invalid!");
            return;
        }
        getPhoneBySlotId(phone, slotId).getVtCallWaiting(onComplete);
    }

    /**
     * gets call waiting activation state. The return value of
     * ((AsyncResult)onComplete.obj) is an array of int, with a length of 1.
     *
     * @param phone
     * @param onComplete
     * @param slotId
     */
    public static void getCallWaiting(Phone phone, Message onComplete, int slotId) {
        Assert.assertNotNull(phone);
        if (GeminiUtils.isGeminiSupport() && !GeminiUtils.isValidSlot(slotId)) {
            PhoneLog.e(TAG, "[getCallWaiting], the slotId is invalid!");
            return;
        }
        getPhoneBySlotId(phone, slotId).getCallWaiting(onComplete);
    }

    /**
     * sets a call forwarding option.
     *
     * @param phone
     * @param enable
     * @param onComplete
     * @param slotId
     */
    public static void setCallWaiting(Phone phone, boolean enable, Message onComplete, int slotId) {
        Assert.assertNotNull(phone);
        if (GeminiUtils.isGeminiSupport() && !GeminiUtils.isValidSlot(slotId)) {
            PhoneLog.e(TAG, "[setCallWaiting], the slotId is invalid!");
            return;
        }
        getPhoneBySlotId(phone, slotId).setCallWaiting(enable, onComplete);
    }

    /**
     * sets VT call waiting state.
     *
     * @param phone
     * @param enable
     * @param onComplete
     * @param slotId
     */
    public static void setVtCallWaiting(Phone phone, boolean enable, Message onComplete, int slotId) {
        Assert.assertNotNull(phone);
        if (GeminiUtils.isGeminiSupport() && !GeminiUtils.isValidSlot(slotId)) {
            PhoneLog.e(TAG, "[setVtCallWaiting], the slotId is invalid!");
            return;
        }
        getPhoneBySlotId(phone, slotId).setVtCallWaiting(enable, onComplete);
    }

    /**
     * Get the slotId's IccCard.
     *
     * @param phone
     * @param slotId
     * @return
     */
    public static IccCard getIccCard(Phone phone, int slotId) {
        Assert.assertNotNull(phone);
        IccCard iccCardInterface = null;
        if (GeminiUtils.isGeminiSupport()) {
            PhoneLog.d(TAG, "[getIccCard], slotId = " + slotId);
            if (GeminiUtils.isValidSlot(slotId)) {
                iccCardInterface = ((GeminiPhone) phone).getPhonebyId(slotId).getIccCard();
            }
        } else {
            iccCardInterface = phone.getIccCard();
        }
        return iccCardInterface;
    }

    /**
     * sets outgoing caller id display.
     *
     * @param phone
     * @param commandInterfaceCLIRMode
     * @param onComplete
     * @param slotId
     */
    public static void setOutgoingCallerIdDisplay(Phone phone, int commandInterfaceCLIRMode,
            Message onComplete, int slotId) {
        Assert.assertNotNull(phone);
        if (GeminiUtils.isGeminiSupport() && !GeminiUtils.isValidSlot(slotId)) {
            PhoneLog.e(TAG, "[setOutgoingCallerIdDisplay], the slotId is invalid!");
            return;
        }
        getPhoneBySlotId(phone, slotId).setOutgoingCallerIdDisplay(commandInterfaceCLIRMode,
                onComplete);
    }

    /**
     * gets outgoing caller id display.
     *
     * @param phone
     * @param onComplete
     * @param slotId
     */
    public static void getOutgoingCallerIdDisplay(Phone phone, Message onComplete, int slotId) {
        Assert.assertNotNull(phone);
        if (GeminiUtils.isGeminiSupport() && !GeminiUtils.isValidSlot(slotId)) {
            PhoneLog.e(TAG, "[getOutgoingCallerIdDisplay], the slotId is invalid!");
            return;
        }
        getPhoneBySlotId(phone, slotId).getOutgoingCallerIdDisplay(onComplete);
    }

    /**
     * Returns a list of MMI codes that are pending. (They have initiated but have not yet
     * completed). Presently there is only ever one. Use registerForMmiInitiate and
     * registerForMmiComplete for change notification.
     *
     * @param phone
     * @param slotId
     * @return
     */
    public static List<? extends MmiCode> getPendingMmiCodes(Phone phone, int slotId) {
        Assert.assertNotNull(phone);
        List<? extends MmiCode> mmiCodes = null;
        if (GeminiUtils.isGeminiSupport() && !GeminiUtils.isValidSlot(slotId)) {
            PhoneLog.e(TAG, "[getPendingMmiCodes], the slotId is invalid!");
        } else {
            mmiCodes = getPhoneBySlotId(phone, slotId).getPendingMmiCodes();
        }
        if (mmiCodes == null) {
            mmiCodes = new ArrayList<MmiCode>();
        }
        return mmiCodes;
    }

    /**
     * return true MMI codes size > 0
     *
     * @see getPendingMmiCodes
     * @param phone
     * @return
     */
    public static boolean hasPendingMmi(Phone phone) {
        Assert.assertNotNull(phone);
        int mmiCount = 0;
        if (GeminiUtils.isGeminiSupport()) {
            GeminiPhone gphone = (GeminiPhone) phone;
            final int[] geminiSlots = GeminiUtils.getSlots();
            PhoneLog.d(TAG, "[hasPendingMmi], mmiCount slot size:" + geminiSlots.length);
            for (int gs : geminiSlots) {
                mmiCount += gphone.getPhonebyId(gs).getPendingMmiCodes().size();
            }
        } else {
            mmiCount = phone.getPendingMmiCodes().size();
        }
        PhoneLog.d(TAG, "[hasPendingMmi], mmiCount=" + mmiCount);
        return mmiCount > 0;
    }

    /**
     * used to release all connections in the MS, release all connections with one reqeust together,
     * not seperated.
     *
     * @param phone
     */
    public static void hangupAll(Phone phone) {
        Assert.assertNotNull(phone);
        try {
            if (GeminiUtils.isGeminiSupport()) {
                GeminiPhone gphone = (GeminiPhone) phone;
                int[] geminiSlots = GeminiUtils.getSlots();
                for (int gs : geminiSlots) {
                    gphone.getPhonebyId(gs).hangupAll();
                }
            } else {
                phone.hangupAllEx();
            }
        } catch (CallStateException ex) {
            PhoneLog.d(TAG, "[hangupAll], Error, cannot hangup All Calls");
        }
    }

    public static int getSlotNotIdle(Phone phone) {
        Assert.assertNotNull(phone);
        if (phone instanceof GeminiPhone) {
            GeminiPhone gPhone = (GeminiPhone) phone;
            int[] geminiSlots = GeminiUtils.getSlots();
            for (int slot : geminiSlots) {
                if (gPhone.getPhonebyId(slot).getState() != PhoneConstants.State.IDLE) {
                    return slot;
                }
            }
        } else {
            if (phone.getState() != PhoneConstants.State.IDLE) {
                return GeminiConstants.SLOT_ID_1;
            }
        }
        return -1;
    }

    /**
     * Connects the two calls and disconnects the subscriber from both calls
     * Explicit Call Transfer occurs asynchronously and may fail.
     *
     * @param phone
     */
    public static void explicitCallTransfer(Phone phone) {
        Assert.assertNotNull(phone);
        try {
            int slotId = getSlotNotIdle(phone);
            if (UNSPECIFIED_SLOT_ID == slotId) {
                PhoneLog.d(TAG, "[explicitCallTransfer], all the slots is idle.");
                return;
            }
            getPhoneBySlotId(phone, slotId).explicitCallTransfer();
            PhoneLog.d(TAG, "[explicitCallTransfer], slotId = " + slotId);
        } catch (CallStateException e) {
            e.printStackTrace();
        }
    }

    /**
     * Requests to set the preferred network type for searching and registering
     * (CS/PS domain, RAT, and operation mode)
     *
     * @param phone
     * @param networkType
     * @param response
     * @param slotId
     */
    public static void setPreferredNetworkType(Phone phone, int networkType, Message response, int slotId) {
        Assert.assertNotNull(phone);
        if (GeminiUtils.isGeminiSupport()) {
            if (GeminiUtils.isValidSlot(slotId)) {
                ((GeminiPhone) phone).setPreferredNetworkTypeGemini(networkType, response, slotId);
            }
        } else {
            // Set the modem network mode
            phone.setPreferredNetworkType(networkType, response);
        }
        PhoneLog.d(TAG, "setPreferredNetworkType, modemNetworkMode:" + networkType + " slotId:" + slotId);
    }

    /**
     * Requests to set the preferred network type for searching and registering
     * (CS/PS domain, RAT, and operation mode)
     *
     * @param phone
     * @param response
     * @param slotId
     */
    public static void getPreferredNetworkType(Phone phone, Message response, int slotId) {
        Assert.assertNotNull(phone);
        if (GeminiUtils.isGeminiSupport() && !GeminiUtils.isValidSlot(slotId)) {
            PhoneLog.e(TAG, "[getPreferredNetworkType], the slotId is invalid!");
            return;
        }
        getPhoneBySlotId(phone, slotId).getPreferredNetworkType(response);
    }

    /**
     * Get "Restriction of menu options for manual PLMN selection" bit status
     * from EF_CSP data, this belongs to "Value Added Services Group".
     *
     * @param phone
     * @param context
     * @return
     */
    public static boolean isCspPlmnEnabled(Phone phone, Context context) {
        Assert.assertNotNull(phone);
        boolean isShowPlmn = false;
        if (GeminiUtils.isGeminiSupport()) {
            List<SimInfoRecord> sims = SimInfoManager.getInsertedSimInfoList(context);
            for (SimInfoRecord sim : sims) {
                isShowPlmn |= ((GeminiPhone) phone).getPhonebyId(sim.mSimSlotId).isCspPlmnEnabled();
            }
        } else {
            isShowPlmn = phone.isCspPlmnEnabled();
        }
        return isShowPlmn;
    }

    /**
     * Scan available networks.
     *
     * @param phone
     * @param response
     * @param slotId
     */
    public static void getAvailableNetworks(Phone phone, Message response, int slotId) {
        Assert.assertNotNull(phone);
        if (GeminiUtils.isGeminiSupport()) {
            PhoneLog.d(TAG, "[getAvailableNetworks], slotId = " + slotId);
            if (GeminiUtils.isValidSlot(slotId)) {
                ((GeminiPhone) phone).getAvailableNetworksGemini(response, slotId);
            }
        } else {
            phone.getAvailableNetworks(response);
        }
    }

    /**
     * Cancel Scan available networks.
     *
     * @param phone
     * @param response
     * @param slotId
     */
    public static void cancelAvailableNetworks(Phone phone, Message response, int slotId) {
        Assert.assertNotNull(phone);
        PhoneLog.d(TAG, "[cancelAvailableNetworks], slotId = " + slotId);
        if (GeminiUtils.isGeminiSupport()) {
            if (GeminiUtils.isValidSlot(slotId)) {
                ((GeminiPhone) phone).cancelAvailableNetworksGemini(response, slotId);
            }
        } else {
            phone.cancelAvailableNetworks(response);
        }
    }

    /**
     * Switches network selection mode to "automatic", re-scanning and
     * re-selecting a network if appropriate.
     *
     * @param phone
     * @param response
     * @param slotId
     */
    public static void setNetworkSelectionModeAutomatic(Phone phone, Message response, int slotId) {
        Assert.assertNotNull(phone);
        if (GeminiUtils.isGeminiSupport() && !GeminiUtils.isValidSlot(slotId)) {
            PhoneLog.e(TAG, "[setNetworkSelectionModeAutomatic], the slotId is invalid!");
            return;
        }
        getPhoneBySlotId(phone, slotId).setNetworkSelectionModeAutomatic(response);
    }

    /**
     * Handles PIN MMI commands (PIN/PIN2/PUK/PUK2), which are initiated without SEND (so dial is
     * not appropriate).
     *
     * @param phone
     * @param dialString
     * @param slotId
     * @return
     */
    public static boolean handlePinMmi(Phone phone, String dialString, int slotId) {
        Assert.assertNotNull(phone);
        if (GeminiUtils.isGeminiSupport() && !GeminiUtils.isValidSlot(slotId)) {
            PhoneLog.e(TAG, "[handlePinMmi], the slotId is invalid!");
            return false;
        }
        PhoneLog.d(TAG, "handlePinMmi :" + " dialString:" + dialString
                + ", slotId:" + slotId);
        return getPhoneBySlotId(phone, slotId).handlePinMmi(dialString);
    }

    /**
     * Becasue of support G+C, the GeminiPhone may contains CDMAPhone, so must get the exactly phone
     * type:
     *
     * @param phone
     * @return
     */
    public static int getPhoneType(Phone phone, int slotId) {
        Assert.assertNotNull(phone);
        int phoneType = PhoneConstants.PHONE_TYPE_NONE;
        if (GeminiUtils.isGeminiSupport() && phone instanceof GeminiPhone) {
            if (GeminiUtils.isValidSlot(slotId)) {
                phoneType = ((GeminiPhone) phone).getPhonebyId(slotId).getPhoneType();
            }
        } else {
            phoneType = phone.getPhoneType();
        }
        PhoneLog.d(TAG, "getPhoneType, slotId:" + slotId + ", phoneType:" + phoneType);
        return phoneType;
    }

    /**
     * Sends user response to a USSD REQUEST message. An MmiCode instance representing this response
     * is sent to handlers registered with registerForMmiInitiate.
     *
     * @param phone
     * @param text
     * @param slotId
     */
    public static void sendUssdResponse(Phone phone, String text, int slotId) {
        Assert.assertNotNull(phone);
        if (GeminiUtils.isGeminiSupport() && !GeminiUtils.isValidSlot(slotId)) {
            PhoneLog.e(TAG, "[sendUssdResponse], the slotId is invalid!");
            return;
        }
        getPhoneBySlotId(phone, slotId).sendUssdResponse(text);
    }

    /**
     * Get current coarse-grained voice call state.
     *
     * @param phone
     * @param slotId
     * @return
     */
    public static PhoneConstants.State getState(Phone phone, int slotId) {
        Assert.assertNotNull(phone);
        if (GeminiUtils.isGeminiSupport() && !GeminiUtils.isValidSlot(slotId)) {
            PhoneLog.e(TAG, "[getState], the slotId is invalid!");
            return null;
        }
        return getPhoneBySlotId(phone, slotId).getState();
    }

    /**
     * Get current coarse-grained voice call state.
     *
     * @param phone
     * @return
     */
    public static PhoneConstants.State getState(Phone phone) {
        Assert.assertNotNull(phone);
        PhoneConstants.State state;
        if (GeminiUtils.isGeminiSupport()) {
            state = ((GeminiPhone) phone).getState();
        } else {
            state = CallManager.getInstance().getState();
        }
        return state;
    }

    /**
     * get voice mail number, default gets from Phone.getVoiceMailNumber(), if GEMINI, call by
     * GeminiPhone.
     *
     * @param slotId
     * @return
     */
    public static String getVoiceMailNumber(Phone phone, int slotId) {
        Assert.assertNotNull(phone);
        String vmNumber = "";
        if (GeminiUtils.isGeminiSupport() && !GeminiUtils.isValidSlot(slotId)) {
            PhoneLog.e(TAG, "[getVoiceMailNumber], the slotId is invalid!");
        } else {
            vmNumber = getPhoneBySlotId(phone, slotId).getVoiceMailNumber();
        }
        PhoneLog.d(TAG, "getVoiceMailNumber : vmNumber:" + vmNumber + " slotId=" + slotId);
        return vmNumber;
    }

    public static void setVoiceMailNumber(Phone phone, String alphaTag, String voiceMailNumber,
            Message onComplete, int slotId) {
        if (GeminiUtils.isGeminiSupport() && !GeminiUtils.isValidSlot(slotId)) {
            PhoneLog.e(TAG, "[setVoiceMailNumber], the slotId is invalid!");
            return;
        }
        getPhoneBySlotId(phone, slotId).setVoiceMailNumber(alphaTag, voiceMailNumber, onComplete);
    }

    /**
     * getIccRecordsLoaded(), if GEMINI, call by GeminiPhone.
     *
     * @param slotId
     * @return
     */
    public static boolean getIccRecordsLoaded(Phone phone, int slotId) {
        Assert.assertNotNull(phone);
        boolean iccRecordloaded = false;
        if (GeminiUtils.isGeminiSupport()) {
            if (GeminiUtils.isValidSlot(slotId)) {
                iccRecordloaded = ((GeminiPhone) phone).getPhonebyId(slotId).getIccRecordsLoaded();
            }
        } else {
            iccRecordloaded = phone.getIccRecordsLoaded();
        }
        PhoneLog.d(TAG, "getIccRecordsLoaded : iccRecordloaded:" + iccRecordloaded + ", slotId:" + slotId);
        return iccRecordloaded;
    }

    /**
     * Get voice message waiting indicator status. No change notification available on this
     * interface. Use PhoneStateNotifier or similar instead.]
     *
     * @param phone
     * @param slotId
     * @return
     */
    public static boolean getMessageWaitingIndicator(Phone phone, int slotId) {
        Assert.assertNotNull(phone);
        if (GeminiUtils.isGeminiSupport() && !GeminiUtils.isValidSlot(slotId)) {
            PhoneLog.e(TAG, "[getMessageWaitingIndicator], the slotId is invalid!");
            return false;
        }
        return getPhoneBySlotId(phone, slotId).getMessageWaitingIndicator();
    }

    /**
     *
     * if slotId is valid, check the slot's service state, else check all slots' service sate.
     * @return true if the slot is power off
     */
    public static boolean isSlotPowerOff(Phone phone, int slotId) {
        Assert.assertNotNull(phone);
        if (GeminiUtils.isGeminiSupport()) {
            GeminiPhone gPhone = (GeminiPhone) phone;
            if (GeminiUtils.isValidSlot(slotId)) {
                ServiceState serviceState = ServiceState
                        .newFromBundle(PhoneGlobals.getInstance().phoneMgrEx
                                .getServiceState(slotId));
                return serviceState.getState() == ServiceState.STATE_POWER_OFF;
            }
            int[] geminiSlots = GeminiUtils.getSlots();
            for (int gs : geminiSlots) {
                ServiceState serviceState = ServiceState
                        .newFromBundle(PhoneGlobals.getInstance().phoneMgrEx.getServiceState(gs));
                if (serviceState.getState() != ServiceState.STATE_POWER_OFF) {
                    return false;
                }
            }
            return true;
        }
        return phone.getServiceState().getState() == ServiceState.STATE_POWER_OFF;
    }

    /**
     * get CallerInfo by number & slotId. If GEMINI support, get CallerInfo by
     * CallerInfo.getCallerInfoEx(..), else CallerInfo.getCallerInfo(..)
     *
     * @param context
     * @param number
     * @param slotId
     * @return
     */
    public static CallerInfo getCallerInfo(Context context, String number, int slotId) {
        if (GeminiUtils.isGeminiSupport() && GeminiUtils.isValidSlot(slotId)) {
            return CallerInfo.getCallerInfoEx(context, number, slotId);
        }
        return CallerInfo.getCallerInfo(context, number);
    }

    /**
     * Sets the radio power on/off state (off is sometimes called "airplane mode"). Current state
     * can be gotten via getServiceState(). getState(). Note: This request is asynchronous.
     * getServiceState().getState() will not change immediately after this call.
     * registerForServiceStateChanged() to find out when the request is complete. If shutdown is
     * true, will turn off the radio and SIM power. Used when shutdown the entire phone
     *
     * @param phone
     */
    public static void setRadioPower(Phone phone, int mode) {
        Assert.assertNotNull(phone);
        if (GeminiUtils.isGeminiSupport()) {
            ((GeminiPhone) phone).setRadioMode(mode);
        } else {
            boolean radioStatus = (0 == mode) ? false : true;
            phone.setRadioPower(radioStatus);
        }
        PhoneLog.d(TAG, "[setRadioPower], mode = " + mode);
    }

    /**
     * Expand sendBurstDtmf method for GEMINI.
     */
    public static void sendBurstDtmf(String dtmfString, int on, int off, Message onComplete) {
        if (PhoneGlobals.getPhone() instanceof GeminiPhone) {
            final int cdmaSlot = GeminiUtils.getCDMASlot();
            Phone cdmaPhone = ((GeminiPhone) PhoneGlobals.getPhone()).getPhonebyId(cdmaSlot);
            cdmaPhone.sendBurstDtmf(dtmfString, on, off, onComplete);
        } else {
            CallManager.getInstance().sendBurstDtmf(dtmfString, on, off, onComplete);
        }
    }

    /**
     * Manually selects a network.
     *
     * @param phone
     * @param network
     * @param response
     * @param slotId
     */
    public static void selectNetworkManually(Phone phone, OperatorInfo network, Message response,
            int slotId) {
        if (GeminiUtils.isGeminiSupport() && !GeminiUtils.isValidSlot(slotId)) {
            PhoneLog.e(TAG, "[selectNetworkManually], the slotId is invalid!");
            return;
        }
        getPhoneBySlotId(phone, slotId).selectNetworkManually(network, response);
    }

    /**
     * getVtFacilityLock
     * gets VT Call Barring States. The return value of
     * (AsyncResult)response.obj).result will be an Integer representing
     * the sum of enabled serivice classes (sum of SERVICE_CLASS_*)
     *
     * @param phone
     * @param facility one of CB_FACILTY_*
     * @param password password or "" if not required
     * @param onComplete a callback message when the action is completed.
     * @param slotId
     */
    public static void getVtFacilityLock(Phone phone, String facility, String password,
            Message onComplete, int slotId) {
        if (GeminiUtils.isGeminiSupport() && !GeminiUtils.isValidSlot(slotId)) {
            PhoneLog.e(TAG, "[getVtFacilityLock], the slotId is invalid!");
            return;
        }
        getPhoneBySlotId(phone, slotId).getVtFacilityLock(facility, password, onComplete);
    }

    /**
     * getFacilityLock
     * gets Call Barring States. The return value of
     * (AsyncResult)response.obj).result will be an Integer representing
     * the sum of enabled serivice classes (sum of SERVICE_CLASS_*)
     *
     * @param phone
     * @param facility one of CB_FACILTY_*
     * @param password password or "" if not required
     * @param onComplete a callback message when the action is completed.
     * @param slotId
     */
    public static void getFacilityLock(Phone phone, String facility, String password,
            Message onComplete, int slotId) {
        if (GeminiUtils.isGeminiSupport() && !GeminiUtils.isValidSlot(slotId)) {
            PhoneLog.e(TAG, "[getFacilityLock], the slotId is invalid!");
            return;
        }
        getPhoneBySlotId(phone, slotId).getFacilityLock(facility, password, onComplete);
    }

    /**
     * setFacilityLock
     * sets Call Barring options.
     *
     * @param phone
     * @param facility one of CB_FACILTY_*
     * @param enable true means lock, false means unlock
     * @param password password or "" if not required
     * @param onComplete a callback message when the action is completed.
     * @param slotId
     */
    public static void setFacilityLock(Phone phone, String facility, boolean enable,
            String password, Message onComplete, int slotId) {
        if (GeminiUtils.isGeminiSupport() && !GeminiUtils.isValidSlot(slotId)) {
            PhoneLog.e(TAG, "[setFacilityLock], the slotId is invalid!");
            return;
        }
        getPhoneBySlotId(phone, slotId).setFacilityLock(facility, enable, password, onComplete);
    }

    /**
     * setVtFacilityLock
     * sets VT Call Barring options.
     *
     * @param phone
     * @param facility one of CB_FACILTY_*
     * @param enable true means lock, false means unlock
     * @param password password or "" if not required
     * @param onComplete a callback message when the action is completed.
     * @param slotId
     */
    public static void setVtFacilityLock(Phone phone, String facility, boolean enable,
            String password, Message onComplete, int slotId) {
        if (GeminiUtils.isGeminiSupport() && !GeminiUtils.isValidSlot(slotId)) {
            PhoneLog.e(TAG, "[setVtFacilityLock], the slotId is invalid!");
            return;
        }
        getPhoneBySlotId(phone, slotId).setVtFacilityLock(facility, enable, password, onComplete);
    }

    /**
     * changeBarringPassword
     * changes Call Barring related password.
     *
     * @param phone
     * @param facility one of CB_FACILTY_*
     * @param oldPwd old password
     * @param newPwd new password
     * @param onComplete a callback message when the action is completed.
     * @param slotId
     */
    public static void changeBarringPassword(Phone phone, String facility, String oldPwd,
            String newPwd, Message onComplete, int slotId) {
        if (GeminiUtils.isGeminiSupport() && !GeminiUtils.isValidSlot(slotId)) {
            PhoneLog.e(TAG, "[changeBarringPassword], the slotId is invalid!");
            return;
        }
        getPhoneBySlotId(phone, slotId).changeBarringPassword(facility, oldPwd, newPwd, onComplete);
    }

    /**
     * Query if the Cell broadcast is adtivated or not
     *
     * @param phone
     * @param response
     *            Callback message is empty on completion
     * @hide
     * @param slotId
     */
    public static void queryCellBroadcastSmsActivation(Phone phone, Message response, int slotId) {
        if (GeminiUtils.isGeminiSupport()) {
            if (GeminiUtils.isValidSlot(slotId)) {
                ((GeminiPhone) phone).getPhonebyId(slotId).queryCellBroadcastSmsActivation(response);
            } else {
                PhoneLog.e(TAG, "[queryCellBroadcastSmsActivation], the slotId is invalid!");
            }
        } else {
            phone.queryCellBroadcastSmsActivation(response);
        }
    }

    /**
     * Activate or deactivate cell broadcast SMS.
     *
     * @param phone
     * @param activate
     * @param response
     * @param slotId
     */
    public static void activateCellBroadcastSms(Phone phone, int activate, Message response, int slotId) {
        if (GeminiUtils.isGeminiSupport()) {
            if (GeminiUtils.isValidSlot(slotId)) {
                ((GeminiPhone) phone).getPhonebyId(slotId).activateCellBroadcastSms(activate,response);
            } else {
                PhoneLog.e(TAG, "[activateCellBroadcastSms], the slotId is invalid!");
            }
        } else {
            phone.activateCellBroadcastSms(activate, response);
        }
    }

    /**
     * Configure cell broadcast SMS.
     *
     * @param phone
     * @param chIdList
     * @param langList
     * @param response
     * @param slotId
     */
    public static void setCellBroadcastSmsConfig(Phone phone, SmsBroadcastConfigInfo[] chIdList,
            SmsBroadcastConfigInfo[] langList, Message response, int slotId) {
        if (GeminiUtils.isGeminiSupport()) {
            if (GeminiUtils.isValidSlot(slotId)) {
                ((GeminiPhone) phone).getPhonebyId(slotId).setCellBroadcastSmsConfig(chIdList,
                    langList, response);
            } else {
                PhoneLog.e(TAG, "[setCellBroadcastSmsConfig], the slotId is invalid!");
            }
        } else {
            phone.setCellBroadcastSmsConfig(chIdList, langList, response);
        }
    }

    /**
     * Query the current configuration of cdma cell broadcast SMS.
     *
     * @param phone
     * @param response
     * @param slotId
     */
    public static void getCellBroadcastSmsConfig(Phone phone, Message response, int slotId) {
        if (GeminiUtils.isGeminiSupport()) {
            if (GeminiUtils.isValidSlot(slotId)) {
                ((GeminiPhone) phone).getPhonebyId(slotId).getCellBroadcastSmsConfig(response);
            } else {
                PhoneLog.e(TAG, "[getCellBroadcastSmsConfig], the slotId is invalid!");
            }
        } else {
            phone.getCellBroadcastSmsConfig(response);
        }
    }

    /**
     * Get prefered operator list.
     * @param phone
     * @param slotId
     * @param response
     */
    public static void getPreferedOperatorList(Phone phone, int slotId, Message response) {
        if (GeminiUtils.isGeminiSupport()) {
            if (GeminiUtils.isValidSlot(slotId)) {
                ((GeminiPhone) phone).getPhonebyId(slotId).getPreferedOperatorList(response);
            } else {
                PhoneLog.e(TAG, "[getPreferedOperatorList], the slotId is invalid!");
            }
        } else {
            phone.getPreferedOperatorList(response);
        }
    }

    public static void setPolEntry(Phone phone, int slotId, NetworkInfoWithAcT networkWithAct,
            Message onComplete) {
        if (GeminiUtils.isGeminiSupport()) {
            if (GeminiUtils.isValidSlot(slotId)) {
                ((GeminiPhone) phone).getPhonebyId(slotId).setPolEntry(networkWithAct, onComplete);
            } else {
                PhoneLog.e(TAG, "[setPOLEntry], the slotId is invalid!");
            }
        } else {
            phone.setPolEntry(networkWithAct, onComplete);
        }
    }

    public static void getPolCapability(Phone phone, int slotId, Message onComplete) {
        if (GeminiUtils.isGeminiSupport()) {
            if (GeminiUtils.isValidSlot(slotId)) {
                ((GeminiPhone) phone).getPhonebyId(slotId).getPolCapability(onComplete);
            } else {
                PhoneLog.e(TAG, "[setPOLEntry], the slotId is invalid!");
            }
        } else {
            phone.getPolCapability(onComplete);
        }
        PhoneLog.d(TAG, "slotId: " + slotId);
    }

    /**
     * whether the specified slot id radio off.
     *
     * @param slot
     * @param context
     * @return
     */
    public static boolean isRadioOffBySlot(int slot, Context context) {
        boolean isRadioOff = true;
        Phone phone = PhoneGlobals.getPhone();
        if (phone instanceof GeminiPhone) {
            GeminiPhone dualPhone = (GeminiPhone) phone;
            isRadioOff = !dualPhone.isRadioOnGemini(slot);
        } else {
            isRadioOff = phone.getServiceState().getState() == ServiceState.STATE_POWER_OFF;
        }

        return isRadioOff || GeminiUtils.isAllRadioOff(context);
    }

    /**
     * Pick the best slot for ECC. The best slot should be radio on and in
     * service, if not, it should be on radio, else GEMINI_SIM_1.
     *
     * @param phone
     * @param number
     * @return
     */
    public static int pickBestSlotForEmergencyCall(Phone phone, String number) {
        Assert.assertNotNull(phone);
        if (GeminiUtils.isGeminiSupport()) {
            GeminiPhone gPhone = (GeminiPhone) phone;
            final int[] geminiSlots = GeminiUtils.getSlots();
            final int count = geminiSlots.length;
            boolean[] isRadioOn = new boolean[count];
            for (int i = 0; i < count; i++) {
                isRadioOn[i] = gPhone.isRadioOnGemini(geminiSlots[i]);
                int state = ServiceState.newFromBundle(PhoneGlobals.getInstance().phoneMgrEx
                        .getServiceState(geminiSlots[i])).getState();
                if (isRadioOn[i] && state == ServiceState.STATE_IN_SERVICE) {
                    // the slot is radio on & state is in service
                    PhoneLog.d(TAG, "pickBestSlotForEmergencyCallm, radio on & in service, slot:"
                            + geminiSlots[i]);
                    return geminiSlots[i];
                }
            }
            for (int i = 0; i < count; i++) {
                if (isRadioOn[i]) {
                    // the slot is radio on
                    PhoneLog.d(TAG, "pickBestSlotForEmergencyCallm, radio on, slot:" + geminiSlots[i]);
                    return geminiSlots[i];
                }
            }
        }
        PhoneLog.d(TAG, "pickBestSlotForEmergencyCallm, no gemini");
        return GeminiUtils.getDefaultSlot();
    }

    /**
     * get network operator name, read from SystemProperties.
     *
     * @see PROPERTY_OPERATOR_GEMINI
     * @return
     */
    public static String getNetworkOperatorName() {
        String operatorName = null;
        if (GeminiUtils.isGeminiSupport()) {
            GeminiPhone gphone = (GeminiPhone) PhoneGlobals.getInstance().phone;
            final int[] geminiSlots = GeminiUtils.getSlots();
            for (int i = 0; i < geminiSlots.length; i++) {
                if (gphone.getPhonebyId(geminiSlots[i]).getState() != PhoneConstants.State.IDLE) {
                    operatorName = SystemProperties.get(GeminiConstants.PROPERTY_OPERATOR_ALPHAS[i]);
                    PhoneLog.d(TAG, "getNetworkOperatorName operatorName:" + operatorName + ", slotId:"
                            + geminiSlots[i]);
                    break;
                }
            }
            // Give a chance for get mmi information
            if (operatorName == null
                    && PhoneGlobals.getInstance().mCM.getState() == PhoneConstants.State.IDLE) {
                for (int i = 0; i < geminiSlots.length; i++) {
                    if (gphone.getPhonebyId(geminiSlots[i]).getPendingMmiCodes().size() != 0) {
                        operatorName = SystemProperties.get(GeminiConstants.PROPERTY_OPERATOR_ALPHAS[i]);
                        PhoneLog.d(TAG, "getNetworkOperatorName operatorName:" + operatorName + ", slotId:"
                                + geminiSlots[i]);
                        break;
                    }
                }
            }
        } else {
            operatorName = SystemProperties.get(TelephonyProperties.PROPERTY_OPERATOR_ALPHA);
        }
        PhoneLog.d(TAG, "getNetworkOperatorName operatorName = " + operatorName);
        return operatorName;
    }

    /**
     * Sets the radio power on/off state (off is sometimes called "airplane mode"). Current state
     * can be gotten via getServiceState(). getState(). Note: This request is asynchronous.
     * getServiceState().getState() will not change immediately after this call.
     * registerForServiceStateChanged() to find out when the request is complete.
     *
     * @param phone
     * @param isRadioOn
     * @param contentResolver
     */
    public static void setRadioMode(Phone phone, boolean enabled, ContentResolver contentResolver){
        if (GeminiUtils.isGeminiSupport()) {
            if (enabled) {
                ((GeminiPhone) phone).setRadioMode(GeminiNetworkSubUtil.MODE_FLIGHT_MODE);
            } else {
                int dualSimModeSetting = System.getInt(contentResolver,
                        System.DUAL_SIM_MODE_SETTING, GeminiNetworkSubUtil.MODE_DUAL_SIM);
                ((GeminiPhone) phone).setRadioMode(dualSimModeSetting);
            }
        } else {
            /// M: consistent UI @{
            // SIM Management for single sim project
            if (!enabled) {
                int simModeSetting = System.getInt(contentResolver,
                        System.DUAL_SIM_MODE_SETTING, GeminiNetworkSubUtil.MODE_SIM1_ONLY);
                if (simModeSetting == GeminiNetworkSubUtil.MODE_FLIGHT_MODE) {
                    PhoneLog.d(TAG,"Turn off airplane mode, but Radio still off due to sim mode setting is off");
                    enabled = true;
                }
            }
            /// @}
            phone.setRadioPower(!enabled);
        }
    }

    /**
     * Sets a TTY mode option.{@link Phone#setTTYMode(int, Message)},
     * {@link GeminiPhone#setTTYModeGemini(int, Message, int)}
     *
     * @param phone
     * @param radioMode
     * @param handler
     * @param messageId
     */
    public static void setTTYMode(Phone phone, int radioMode, Handler handler, int messageId) {
        if (GeminiUtils.isGeminiSupport()) {
            GeminiPhone gPhone = (GeminiPhone) phone;
            final int[] geminiSlots = GeminiUtils.getSlots();
            for (int gs : geminiSlots) {
                gPhone.setTTYModeGemini(radioMode, handler.obtainMessage(messageId), gs);
            }
        } else {
            phone.setTTYMode(radioMode, handler.obtainMessage(messageId));
        }
    }

    /**
     * Get phone by slotId
     * @param phone
     * @param slotId
     * @return
     */
    public static Phone getPhoneBySlotId(Phone phone, int slotId) {
        Phone selectedPhone = phone;
        boolean isSipPhone = phone.getPhoneType() == PhoneConstants.PHONE_TYPE_SIP;
        if (GeminiUtils.isGeminiSupport() && !isSipPhone) {
            selectedPhone = ((GeminiPhone) phone).getPhonebyId(slotId);
        }
        PhoneLog.d(TAG, "[getPhoneBySlotId], selectedPhone = " + selectedPhone);
        return selectedPhone;
    }
}
