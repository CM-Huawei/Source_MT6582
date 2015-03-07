
package com.mediatek.phone.wrapper;

import android.content.Context;
import android.os.RemoteException;
import android.util.Log;

import com.android.internal.telephony.ITelephony;
import com.android.internal.telephony.PhoneConstants;
import com.android.phone.PhoneGlobals;

import com.mediatek.common.telephony.ITelephonyEx;
import com.mediatek.phone.PhoneLog;
import com.mediatek.phone.gemini.GeminiUtils;
import com.mediatek.xlog.Xlog;

public class ITelephonyWrapper {
    private static final String TAG = "ITelephonyWrapper";

    public static ITelephonyEx mITelephonyEx = PhoneGlobals.getInstance().phoneMgrEx;
    public static ITelephony mITelephony = PhoneGlobals.getInstance().phoneMgr;
    public static final int DEFAULT_SLOT_ID = 0;

    public static boolean isPhbReady(int slotId) {
        try {
            if (GeminiUtils.isGeminiSupport()) {
                return mITelephonyEx.isPhbReady(slotId);
            } else {
                return mITelephonyEx.isPhbReady(DEFAULT_SLOT_ID);
            }
        } catch (RemoteException e) {
            PhoneLog.w(TAG, "e.getMessage is " + e.getMessage());
        }
        return false;
    }

    /**
     * Returns the IccCard type. Return "SIM" for SIM card or "USIM" for USIM
     * card.
     *
     * @param slotId Indicate which sim(slot) to query, if not support Gemini,
     *            please set slotId as 0.
     * @return
     */
    public static String getIccCardType(int slotId) {
        String iccCardType = "";
        try {
            iccCardType = mITelephonyEx.getIccCardType(slotId);
        } catch (RemoteException e) {
            PhoneLog.w(TAG, "e.getMessage is " + e.getMessage());
        }
        return iccCardType;
    }

    /**
     * get the services state for default SIM
     *
     * @param slotId
     * @return sim indicator state
     */
    public static int getSimIndicatorState(int slotId) {
        int simIndicatorState = PhoneConstants.SIM_INDICATOR_UNKNOWN;
        try {
            if (GeminiUtils.isGeminiSupport()) {
                simIndicatorState = mITelephonyEx.getSimIndicatorState(slotId);
            } else {
                simIndicatorState = mITelephony.getSimIndicatorState();
            }
        } catch (android.os.RemoteException e) {
            PhoneLog.w(TAG, "[getSimIndicatorState] e = " + e);
        }
        return simIndicatorState;
    }

    /**
     * Return ture if the ICC card is a test card
     *
     */
    public static boolean isTestIccCard() {
        boolean retval = false;
        try {
            if (GeminiUtils.isGeminiSupport()) {
                int slot = PhoneWrapper.getSlotNotIdle(PhoneGlobals.getInstance().phone);
                PhoneLog.d(TAG, "slot = " + slot);
                if (slot != -1) {
                    retval = mITelephonyEx.isTestIccCard(slot);
                }
            } else {
                retval = mITelephonyEx.isTestIccCard(PhoneConstants.GEMINI_SIM_1);
            }
        } catch (RemoteException e) {
            PhoneLog.w(TAG, "e.getMessage is " + e.getMessage());
        }
        return retval;
    }

    /**
     * Query if icc card is existed or not.
     *
     * @param simId Indicate which sim(slot) to query, if not support Gemini,
     *            please set slotId as 0.
     * @return true if exists an icc card in given slot.
     */
    public static boolean hasIccCard(int slotId) {
        boolean result = false;
        try {
            result = mITelephonyEx.hasIccCard(slotId);
        } catch (Exception e) {
            PhoneLog.w(TAG, "e.getMessage is " + e.getMessage());
        }
        PhoneLog.d(TAG, "[hasIccCard], slotId = " + slotId + "; result = " + result);
        return result;
    }
}
