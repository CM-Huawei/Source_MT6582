package com.mediatek.phone.wrapper;

import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;

import com.android.internal.telephony.CallManager;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.PhoneFactory;
import com.mediatek.phone.PhoneLog;
import com.mediatek.phone.gemini.GeminiUtils;
import com.mediatek.telephony.TelephonyManagerEx;

public class TelephonyManagerWrapper {

    private static final String TAG = "TelephonyManagerWrapper";
    /**
     * Get the line 1 phone number (MSISDN).
     *
     * @param phone
     * @param slotId
     * @return phone number
     */
    public static String getLine1Number(Phone phone, int slotId) {
        String line1Number;
        if (GeminiUtils.isGeminiSupport() && GeminiUtils.isValidSlot(slotId)) {
            line1Number = TelephonyManagerEx.getDefault().getLine1Number(slotId);
        } else {
            if (phone == null) {
                line1Number = CallManager.getInstance().getDefaultPhone().getLine1Number();
            } else {
                line1Number = phone.getLine1Number();
            }
        }
        PhoneLog.d(TAG, "[getLine1Number], slotId = " + slotId + "; line1Number = " + line1Number);
        return line1Number;
    }

    /**
     * Returns a constant indicating the call state (cellular) on the device.
     *
     * @param slotId
     * @return
     */
    public static int getCallState(int slotId){
        int callState;
        /* M: add dual talk, @ { */
        if (GeminiUtils.isGeminiSupport() && PhoneFactory.isDualTalkMode()) {
            callState = TelephonyManagerEx.getDefault().getCallState(slotId);
        } else {
            callState = TelephonyManager.getDefault().getCallState();
        }
        PhoneLog.d(TAG, "[getCallState], slotId = " + slotId + "; callState = " + callState);
        return callState;
    }

    /**
     * Returns a constant indicating the state of the
     * device SIM card.
     *
     * @param slotId
     * @return
     */
    public static int getSimState(int slotId){
        int status;
        if (GeminiUtils.isGeminiSupport() && GeminiUtils.isValidSlot(slotId)) {
            // M: GEMINI API should be put in TelephonyManagerEx
            status = TelephonyManagerEx.getDefault().getSimState(slotId);
        } else {
            status = TelephonyManager.getDefault().getSimState();
        }
        PhoneLog.d(TAG, "[getSimState], slotId = " + slotId + "; status = " + status);
        return status;
    }

    /**
     * @return true if a ICC card is present
     *
     * @param slotId
     * @return
     */
    public static boolean hasIccCard(int slotId) {
        boolean hasIccCard;
        if (GeminiUtils.isGeminiSupport() && GeminiUtils.isValidSlot(slotId)) {
            hasIccCard = TelephonyManagerEx.getDefault().hasIccCard(slotId);
        } else {
            hasIccCard = TelephonyManager.getDefault().hasIccCard();
        }
        PhoneLog.d(TAG, "[hasIccCard], slotId = " + slotId + "; hasIccCard = " + hasIccCard);
        return hasIccCard;
    }

    /**
     * Returns the voice mail number. Return null if it is unavailable.
     *
     * @param slotId
     * @return
     */
    public static String getVoiceMailNumber(int slotId){
        String voiceMailNumber = null;
        if (GeminiUtils.isGeminiSupport() && GeminiUtils.isValidSlot(slotId)) {
            voiceMailNumber = TelephonyManagerEx.getDefault().getVoiceMailNumber(slotId);
        } else {
            voiceMailNumber = TelephonyManager.getDefault().getVoiceMailNumber();
        }
        PhoneLog.d(TAG, "[getVoiceMailNumber], slotId = " + slotId + "; voiceMailNumber = " + voiceMailNumber);
        return voiceMailNumber;
    }

    /**
     * Registers a listener object to receive notification of changes
     * in specified telephony states.
     *
     * @param listener
     * @param events
     * @param slotId
     */
    public static void listen(PhoneStateListener listener, int events, int slotId) {
        // if the slot id is invalid, listen default(network use)
        if (GeminiUtils.isGeminiSupport() && GeminiUtils.isValidSlot(slotId)) {
            TelephonyManagerEx.getDefault().listen(listener, events, slotId);
        } else {
            TelephonyManager.getDefault().listen(listener, events);
        }
        PhoneLog.d(TAG, "[listen], slotId = " + slotId);
    }

    /**
     * Returns true if the device is considered roaming on the current
     * network.
     *
     * @param slotId
     * @return
     */
    public static boolean isNetworkRoaming(int slotId) {
        boolean isInRoaming = false;
        if (GeminiUtils.isGeminiSupport()) {
            if (GeminiUtils.isValidSlot(slotId)) {
                isInRoaming = TelephonyManagerEx.getDefault().isNetworkRoaming(slotId);
            }
        } else {
            isInRoaming = TelephonyManager.getDefault().isNetworkRoaming();
        }
        PhoneLog.d(TAG, "[isNetworkRoaming], slotId = " + slotId);
        return isInRoaming;
    }

    /**
     * Get the slot by phoneType
     *
     * @param phoneType
     * @return
     */
    public static int getSlotByPhoneType(int phoneType) {
        int slot = PhoneConstants.GEMINI_SIM_1;
        if (phoneType == PhoneConstants.PHONE_TYPE_CDMA
                || phoneType == PhoneConstants.PHONE_TYPE_GSM) {
            TelephonyManagerEx telephony = TelephonyManagerEx.getDefault();
            final int[] geminiSlots = GeminiUtils.getSlots();
            for (int gs : geminiSlots) {
                if (telephony.getPhoneType(gs) == phoneType) {
                    slot = gs;
                    break;
                }
            }
        }
        PhoneLog.d(TAG, "[getSlotByPhoneType], phoneType = " + phoneType);
        return slot;
    }

    /**
     * Returns a constant indicating the device phone type.  This
     * indicates the type of radio used to transmit voice calls.
     *
     * @param slotId
     * @return
     */
    public static int getPhoneType(int slotId) {
        int phoneType = PhoneConstants.PHONE_TYPE_NONE;
        if (GeminiUtils.isGeminiSupport()) {
            if (GeminiUtils.isValidSlot(slotId)) {
                phoneType = TelephonyManagerEx.getDefault().getPhoneType(slotId);
            }
        } else {
            phoneType = TelephonyManager.getDefault().getPhoneType();
        }
        PhoneLog.d(TAG, "[getPhoneType], phoneType = " + phoneType);
        return phoneType;
    }

    /**
     * Gets the unique subscriber ID, for example, the IMSI for a GSM phone.
     * <p>
     * Required Permission:
     *   {@link android.Manifest.permission#READ_PHONE_STATE READ_PHONE_STATE}
     * <p>
     * @param simId  Indicates which SIM (slot) to query
     * <p>
     * @return       Unique subscriber ID, for example, the IMSI for a GSM phone. Null is returned if it is unavailable.
     */
    public static String getSubscriberId(int slotId) {
        String id = null;
        if (GeminiUtils.isGeminiSupport()) {
            id = TelephonyManagerEx.getDefault().getSubscriberId(slotId);
        } else {
            id = TelephonyManager.getDefault().getSubscriberId();
        }
        PhoneLog.d(TAG, "[getSubscriberId], id = " + id);
        return id;
    }
}
