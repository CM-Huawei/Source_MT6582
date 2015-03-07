package com.mediatek.systemui.statusbar.util;

import android.content.Context;
import android.content.Intent;
import android.hardware.display.DisplayManager;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.SystemProperties;
import android.provider.Settings;
import android.telephony.PhoneStateListener;
import android.telephony.ServiceState;
import android.telephony.TelephonyManager;

import com.android.internal.telephony.ITelephony;
import com.android.internal.telephony.ITelephonyRegistry;
import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.TelephonyIntents;

//import com.android.systemui.R;

import com.mediatek.common.telephony.ITelephonyEx;
import com.mediatek.telephony.TelephonyManagerEx;
import com.mediatek.xlog.Xlog;
import com.mediatek.telephony.SimInfoManager;
import com.mediatek.common.featureoption.FeatureOption;

import java.util.List;
import java.util.Comparator;
import java.util.ArrayList;
import java.util.Collections;

/**
 * M: [SystemUI] Support "dual SIM" and "Notification toolbar".
 */
public class SIMHelper {

    public static final String TAG = "SIMHelper";

    private static String mNetworkNameDefault;
    private static String mNetworkNameSeparator;
    
    private static List<SimInfoManager.SimInfoRecord> sSimInfos;

    private static String sIsOptr = null;
    private static String sBaseband = null;

    private static ITelephony sITelephony;

    private static TelephonyManagerEx mTMEx = null;
    private static int mGeminiSimNum = PhoneConstants.GEMINI_SIM_NUM;
    private static boolean[] simInserted;

    private SIMHelper() {
    }

    /**
     * Get the default SIM id of the assigned business.
     *
     * @param context
     * @param businessType
     * @return The default SIM id, or -1 if it is not defined.
     */
    public static long getDefaultSIM(Context context, String businessType) {
        return Settings.System.getLong(context.getContentResolver(), businessType, -1);
    }

    public static void setDefaultSIM(Context context, String businessType, long simId) {
        Settings.System.putLong(context.getContentResolver(), businessType, simId);
    }

    public static List<SimInfoManager.SimInfoRecord> getSIMInfoList(Context context) {
        if (sSimInfos == null || sSimInfos.size() == 0) {
            sSimInfos = getSortedSIMInfoList(context);
        }
        return sSimInfos;
    }

    /**
     * Get the SIM info of the assigned SIM id.
     *
     * @param context
     * @param simId
     * @return The SIM info, or null if it doesn't exist.
     */
    public static SimInfoManager.SimInfoRecord getSIMInfo(Context context, long simId) {
        if (sSimInfos == null || sSimInfos.size() == 0) {
            getSIMInfoList(context);
        }
        for (SimInfoManager.SimInfoRecord info : sSimInfos) {
            if (info.mSimInfoId == simId) {
                return info;
            }
        }
        return null;
    }

    /**
     * Get the SIM info of the assigned SLOT id.
     *
     * @param context
     * @param slotId
     * @return The SIM info, or null if it doesn't exist.
     */
    public static SimInfoManager.SimInfoRecord getSIMInfoBySlot(Context context, int slotId) {
        if(!isSimInserted(slotId)) {
            return null;
        }
        if (sSimInfos == null || sSimInfos.size() == 0) {
            getSIMInfoList(context);
        }
        if (sSimInfos == null) {
            return null;
        }

        for (SimInfoManager.SimInfoRecord info : sSimInfos) {
            if (info.mSimSlotId == slotId) {
                return info;
            }
        }
        return null;
    }

    private static List<SimInfoManager.SimInfoRecord> getSortedSIMInfoList(Context context) {
        List<SimInfoManager.SimInfoRecord> simInfoList = SimInfoManager.getInsertedSimInfoList(context);
        Collections.sort(simInfoList, new Comparator<SimInfoManager.SimInfoRecord>() {
            @Override
            public int compare(SimInfoManager.SimInfoRecord a, SimInfoManager.SimInfoRecord b) {
                if(a.mSimSlotId < b.mSimSlotId) {
                    return -1;
                } else if (a.mSimSlotId > b.mSimSlotId) {
                    return 1;
                } else {
                    return 0;
                }
            }
        });
        return simInfoList;
    }

    public static void updateSIMInfos(Context context) {
        sSimInfos = null;
        sSimInfos = getSortedSIMInfoList(context);
    }

    public static long getSIMIdBySlot(Context context, int slotId) {
        SimInfoManager.SimInfoRecord simInfo = getSIMInfoBySlot(context, slotId);
        if (simInfo == null) {
            return 0;
        }
        return simInfo.mSimInfoId;
    }

    public static int getSIMColorIdBySlot(Context context, int slotId) {
        SimInfoManager.SimInfoRecord simInfo = getSIMInfoBySlot(context, slotId);
        if (simInfo == null) {
            return -1;
        }
        return simInfo.mColor;
    }


    public static boolean checkSimCardDataConnBySlotId(Context context, int slotId) {
        SimInfoManager.SimInfoRecord simInfo = getSIMInfoBySlot(context, slotId);
        if (simInfo == null) {
            return false;
        }
        int simState = getSimIndicatorStateGemini(simInfo.mSimSlotId);
        if (simState == PhoneConstants.SIM_INDICATOR_ROAMING
                || simState == PhoneConstants.SIM_INDICATOR_CONNECTED
                || simState == PhoneConstants.SIM_INDICATOR_ROAMINGCONNECTED
                || simState == PhoneConstants.SIM_INDICATOR_NORMAL) {
            return true;
        } else {
            return false;
        }
    }

    /// M: Check the data connection is connected? real one. @{
    public static boolean isTelephonyDataConnected(Context context) {
        TelephonyManager tm = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
        if (tm != null && TelephonyManager.DATA_CONNECTED == tm.getDataState()) {
            Xlog.d(TAG, "isTelephonyDataConnected called, the data state is " + tm.getDataState());
            return true;
        }
        return false;
    }
    /// M: }@

    /// M: check the sim card data connection is valid @{
    public static boolean checkSimCardDataConn() {
        int simState = getSimIndicatorState();
        if (simState == PhoneConstants.SIM_INDICATOR_ROAMING
                || simState == PhoneConstants.SIM_INDICATOR_CONNECTED
                || simState == PhoneConstants.SIM_INDICATOR_ROAMINGCONNECTED
                || simState == PhoneConstants.SIM_INDICATOR_NORMAL) {
            return true;
        } else {
            return false;
        }
    }
    /// M: }@

    public static boolean is3GSupported() {
        if (sBaseband == null) {
            sBaseband = SystemProperties.get("gsm.baseband.capability");
        }
        if ((sBaseband != null) && (sBaseband.length() != 0)
                && (Integer.parseInt(sBaseband) <= 3)) {
            return false;
        } else {
            return true;
        }
    }

    public static int getSimIndicatorState() {
        try {
             ITelephony telephony = ITelephony.Stub.asInterface(ServiceManager.getService(Context.TELEPHONY_SERVICE));
             if (telephony != null) {
                 return telephony.getSimIndicatorState();
             } else {
                 // This can happen when the ITelephony interface is not up yet.
                 return PhoneConstants.SIM_INDICATOR_UNKNOWN;
             }
        } catch (RemoteException ex) {
            // the phone process is restarting.
            return PhoneConstants.SIM_INDICATOR_UNKNOWN;
        } catch (NullPointerException ex) {
            return PhoneConstants.SIM_INDICATOR_UNKNOWN;
        }
    }

    public static int getSimIndicatorStateGemini(int simId) {
        try {
            if (isGemini()) {
                 ITelephonyEx mTelephonyEx = ITelephonyEx.Stub.asInterface(ServiceManager.getService(Context.TELEPHONY_SERVICEEX));
                 if (mTelephonyEx != null) {
                     final int mState = mTelephonyEx.getSimIndicatorState(simId);
                     if ((mState == PhoneConstants.SIM_INDICATOR_CONNECTED)
                        && (TelephonyManager.DATA_CONNECTED != TelephonyManagerEx.getDefault().getDataState(simId))) {
                        /// M: Sync the data connected state with TelephonyManager, and fallback to normal.
                        Xlog.d(TAG, "getSimIndicatorStateGemini called, fallback to normal and simId is " + simId);
                        return PhoneConstants.SIM_INDICATOR_NORMAL;
                     }
                     return mState;
                 } else {
                     // This can happen when the ITelephony interface is not up yet.
                     return PhoneConstants.SIM_INDICATOR_UNKNOWN;
                 }
            } else {
                return getSimIndicatorState();
            }
        } catch (RemoteException ex) {
            // the phone process is restarting.
            return PhoneConstants.SIM_INDICATOR_UNKNOWN;
        } catch (NullPointerException ex) {
            return PhoneConstants.SIM_INDICATOR_UNKNOWN;
        }
    }

    public static boolean isTelephonyDataConnectedBySimId(int simId) {
        try {
            if (TelephonyManager.DATA_CONNECTED == TelephonyManagerEx.getDefault().getDataState(simId)) {
                Xlog.d(TAG, "isTelephonyDataConnectedBySimId called, data is connected and simId is " + simId);
                return true;
            } else {
                Xlog.d(TAG, "isTelephonyDataConnectedBySimId called, data is not connected and simId is " + simId);
                return false;
            }
        } catch (NullPointerException ex) {
            return false;
        }
    }

    public static TelephonyManagerEx getDefault(Context context) {
        if (mTMEx == null) {
            mTMEx = new TelephonyManagerEx(context);
        }
        return mTMEx;
    }

    public static ITelephony getITelephony() {
        return sITelephony = ITelephony.Stub.asInterface(ServiceManager.getService(Context.TELEPHONY_SERVICE));
    }

    public static ITelephonyEx getITelephonyEx() {           
        return ITelephonyEx.Stub.asInterface(ServiceManager.getService("phoneEx"));
    }

    private static ITelephonyRegistry mRegistry = ITelephonyRegistry.Stub.asInterface(ServiceManager.getService("telephony.registry"));
    private static ITelephonyRegistry mRegistry2 = ITelephonyRegistry.Stub.asInterface(ServiceManager.getService("telephony.registry2"));

    public static void listen(PhoneStateListener listener, int events, int slotId) {
        try {
            Boolean notifyNow = (getITelephony() != null);
            /// M: Support GeminiPlus
            if (PhoneConstants.GEMINI_SIM_1 == slotId) {
                mRegistry.listen("SystemUI SIMHelper", listener.getCallback(), events, notifyNow);
            } else if(PhoneConstants.GEMINI_SIM_2 == slotId) {
                mRegistry2.listen("SystemUI SIMHelper", listener.getCallback(), events, notifyNow);
            } else if(PhoneConstants.GEMINI_SIM_3 == slotId && PhoneConstants.GEMINI_SIM_NUM >= 3) {
                ITelephonyRegistry.Stub.asInterface(ServiceManager.getService("telephony.registry3")).listen("SystemUI SIMHelper", listener.getCallback(), events, notifyNow);
            } else if(PhoneConstants.GEMINI_SIM_4 == slotId && PhoneConstants.GEMINI_SIM_NUM >= 4) {
                ITelephonyRegistry.Stub.asInterface(ServiceManager.getService("telephony.registry4")).listen("SystemUI SIMHelper", listener.getCallback(), events, notifyNow);
            }
        } catch (RemoteException ex) {
            // system process dead
        } catch (NullPointerException ex) {
            // system process dead
        }
    }

    public static boolean isSimInserted(int slotId) {
        if(simInserted == null) {
            updateSimInsertedStatus();
        }
        if (simInserted != null) {
            if(slotId <= simInserted.length -1) {
                Xlog.d(TAG, "isSimInserted(" + slotId + "), SimInserted=" + simInserted[slotId]);
                return simInserted[slotId];
            } else {
                Xlog.d(TAG, "isSimInserted(" + slotId + "), indexOutOfBound, arraysize=" + simInserted.length);
                return false; // default return false
            }
        } else {
            Xlog.d(TAG, "isSimInserted, simInserted is null");
            return false;
        }
    }

    public static void updateSimInsertedStatus() {

        ITelephonyEx mTelephonyEx = ITelephonyEx.Stub.asInterface(ServiceManager.getService(Context.TELEPHONY_SERVICEEX));
        if (mTelephonyEx != null) {
            try {
                if(simInserted == null) {
                    simInserted = new boolean[mGeminiSimNum];
                }
                for (int i = 0 ; i < mGeminiSimNum ; i++) {
                    simInserted[i] = mTelephonyEx.hasIccCard(i);
                    Xlog.d(TAG, "updateSimInsertedStatus, simInserted(" + i + ") = " + simInserted[i]);
                }
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        } else {
            Xlog.d(TAG, "updateSimInsertedStatus, phone is null");
        }
    }

    public static String getNetworkNameDefault (Context mContext) {
        if (mNetworkNameDefault == null) {
            mNetworkNameDefault = mContext.getString(com.android.internal.R.string.lockscreen_carrier_default);
        }
        return mNetworkNameDefault;
    }

    public static String getNetworkNameSeparator (Context mContext) {
        if (mNetworkNameSeparator == null) {
            mNetworkNameSeparator = mContext.getString(com.android.internal.R.string.kg_text_message_separator);
        }
        return mNetworkNameSeparator;
    }

    public static String getNetworkName (Context mContext, 
            boolean showPlmn, String plmn, boolean showSpn, String spn, boolean showCSG, String csg) {

        String mStrDefault = getNetworkNameDefault(mContext);
        String mStrSeparator = getNetworkNameSeparator(mContext);
        StringBuilder str = new StringBuilder();
        boolean something = false;
        if (showPlmn && plmn != null) {
            str.append(plmn);
            something = true;
        }
        if (showSpn && spn != null) {
            if (something) {
                str.append(mStrSeparator);
            }
            str.append(spn);
            something = true;
        }
        if (showCSG) {
            if (something) {
                str.append(mStrSeparator);
            }
            str.append(csg);
            something = true;
        }

        Xlog.d(TAG, "getNetworkName, showSpn=" + showSpn + " spn=" + spn + " showPlmn=" + showPlmn + " plmn=" + plmn);
        Xlog.d(TAG, "getNetworkName, showCSG=" + csg);

        if (something) {
            return str.toString();
        } else {
            return mStrDefault;
        }
    }

    public static String getCsgInfo(Intent intent) {
        String mHNBName = intent.getStringExtra(TelephonyIntents.EXTRA_HNB_NAME);
        String mCSGID = intent.getStringExtra(TelephonyIntents.EXTRA_CSG_ID);
        return (mHNBName != null) ? mHNBName : mCSGID;
    }

    public static String getNetworkName(Context mContext, Intent intent) {
        boolean showSpn = intent.getBooleanExtra(TelephonyIntents.EXTRA_SHOW_SPN, false);
        String spn = intent.getStringExtra(TelephonyIntents.EXTRA_SPN);
        boolean showPlmn = intent.getBooleanExtra(TelephonyIntents.EXTRA_SHOW_PLMN, false);
        String plmn = intent.getStringExtra(TelephonyIntents.EXTRA_PLMN);
        String mCsgInfo = getCsgInfo(intent);

        return getNetworkName(mContext, showPlmn, plmn, showSpn, spn, 
            (mCsgInfo != null), mCsgInfo);
    }

    public static boolean hasService(ServiceState ss) {
        if (ss != null) {
            // Consider the device to be in service if either voice or data service is available.
            // Some SIM cards are marketed as data-only and do not support voice service, and on
            // these SIM cards, we want to show signal bars for data service as well as the "no
            // service" or "emergency calls only" text that indicates that voice is not available.
            switch (ss.getVoiceRegState()) {
                case ServiceState.STATE_POWER_OFF:
                    return false;
                case ServiceState.STATE_OUT_OF_SERVICE:
                case ServiceState.STATE_EMERGENCY_ONLY:
                    return ss.getDataRegState() == ServiceState.STATE_IN_SERVICE;
                default:
                    return true;
            }
        } else {
            return false;
        }
    }

    public static final boolean isMediatekLteDcSupport() {
        return FeatureOption.MTK_LTE_DC_SUPPORT;
    }

    public static boolean isInternationalRoamingStatus(Context context) {
        boolean isRoaming = false;
        ArrayList<SimInfoManager.SimInfoRecord> simInfoList = (ArrayList<SimInfoManager.SimInfoRecord>) getSIMInfoList(context);
        /// M: Two SIMs inserted.
        if (simInfoList != null && simInfoList.size() == 2) {
            isRoaming = getDefault(context).isNetworkRoaming(PhoneConstants.GEMINI_SIM_1);
        /// M: One SIM inserted.
        } else if (simInfoList != null
                && simInfoList.size() == 1) {
            SimInfoManager.SimInfoRecord simInfo = simInfoList.get(0);
           /// M : Only SIM1 CDMA insearted.
            if (simInfo.mSimSlotId == PhoneConstants.GEMINI_SIM_1) {
                isRoaming = getDefault(context).isNetworkRoaming(PhoneConstants.GEMINI_SIM_1);
            /// M: Only SIM2 GSM insearted.
            } else if (simInfo.mSimSlotId == PhoneConstants.GEMINI_SIM_2) {
                isRoaming = getDefault(context).isNetworkRoaming(simInfo.mSimSlotId);
            }
        }
        Xlog.d(TAG, "isInternationalRoamingStatus called, isRoaming = " + isRoaming);
        return isRoaming;
    }

    public static final boolean isGemini() {
        return FeatureOption.MTK_GEMINI_SUPPORT;
    }

    public static int getNumOfSim() {
        if (isGemini()) {
            return PhoneConstants.GEMINI_SIM_NUM;
        } else {
            return 1;
        }
    }

    public static final boolean isMediatekSmartBookSupport() {
        return FeatureOption.MTK_SMARTBOOK_SUPPORT;
    }

    public static boolean isSmartBookPluggedIn(Context mContext) {
        if (isMediatekSmartBookSupport()) {
            DisplayManager mDisplayManager = (DisplayManager)mContext.getSystemService(Context.DISPLAY_SERVICE);
            return mDisplayManager.isSmartBookPluggedIn();
        } else {
            return false;
        }
    }
}
