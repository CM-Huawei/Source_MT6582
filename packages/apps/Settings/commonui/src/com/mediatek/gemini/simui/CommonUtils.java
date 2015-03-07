package com.mediatek.gemini.simui;

import android.app.Activity;
import android.content.Context;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.SystemProperties;
import android.provider.Settings;
import android.util.Log;

import com.android.internal.telephony.ITelephony;
import com.android.internal.telephony.PhoneConstants;

import com.mediatek.common.featureoption.FeatureOption;
import com.mediatek.common.telephony.ITelephonyEx;
import com.mediatek.telephony.SimInfoManager;
import com.mediatek.telephony.SimInfoManager.SimInfoRecord;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Contains utility functions for getting framework resource
 */
public class CommonUtils {
    private static final String TAG = "CommonUtils";

    public static final int INTERNET_COLOR_ID = SimInfoManager.SimBackgroundDarkRes.length;
    public static final int IMAGE_GRAY = 75;// 30% of 0xff in transparent
    public static final int ORIGINAL_IMAGE = 255;

    public static final String EXTRA_SLOTID = "slotId";
    public static final String EXTRA_TITLE_NAME = "EXTRA_TITME_NAME";
    public static final String EXTRA_3G_CARD_ONLY = "EXTRA_3G_CARD_ONLY";

    public static final int MODEM_3G = 0x03;
    public static final int MODEM_MASK_GPRS = 0x01;
    public static final int MODEM_MASK_EDGE = 0x02;
    public static final int MODEM_MASK_WCDMA = 0x04;
    public static final int MODEM_MASK_TDSCDMA = 0x08;
    public static final int MODEM_MASK_HSDPA = 0x10;
    public static final int MODEM_MASK_HSUPA = 0x20;

    public static final String GEMINI_BASEBAND_PROP[] = {
        "gsm.baseband.capability",
        "gsm.baseband.capability2",
        "gsm.baseband.capability3",
        "gsm.baseband.capability4",
    };

    public static class SIMInfoComparable implements Comparator<SimInfoRecord> {

        @Override
        public int compare(SimInfoRecord sim1, SimInfoRecord sim2) {
            return sim1.mSimSlotId - sim2.mSimSlotId;
        }
    }

    /**
     * Get the pic from framework according to sim indicator state 
     * @param state sim indicator state
     * @return the pic res from mediatek framework
     */
    public static int getStatusResource(int state) {
        int resId;
        switch (state) {
        case PhoneConstants.SIM_INDICATOR_RADIOOFF:
            resId = com.mediatek.internal.R.drawable.sim_radio_off;
            break;
        case PhoneConstants.SIM_INDICATOR_LOCKED:
            resId = com.mediatek.internal.R.drawable.sim_locked;
            break;
        case PhoneConstants.SIM_INDICATOR_INVALID:
            resId = com.mediatek.internal.R.drawable.sim_invalid;
            break;
        case PhoneConstants.SIM_INDICATOR_SEARCHING:
            resId = com.mediatek.internal.R.drawable.sim_searching;
            break;
        case PhoneConstants.SIM_INDICATOR_ROAMING:
            resId = com.mediatek.internal.R.drawable.sim_roaming;
            break;
        case PhoneConstants.SIM_INDICATOR_CONNECTED:
            resId = com.mediatek.internal.R.drawable.sim_connected;
            break;
        case PhoneConstants.SIM_INDICATOR_ROAMINGCONNECTED:
            resId = com.mediatek.internal.R.drawable.sim_roaming_connected;
            break;
        default:
            resId = PhoneConstants.SIM_INDICATOR_UNKNOWN;
            break;
        }
        return resId;
    }
    
    /**
     * Get sim color resources
     * @param colorId sim color id
     * @return the color resource 
     */
    public static int getSimColorResource(int colorId) {
        int bgColor = -1;
        if ((colorId >= 0) && (colorId < SimInfoManager.SimBackgroundDarkRes.length)) {
            bgColor = SimInfoManager.SimBackgroundDarkRes[colorId];
        } else if (colorId == INTERNET_COLOR_ID) {
            bgColor = com.mediatek.internal.R.drawable.sim_background_sip;
        } 
        return bgColor;

    }
    
    /**
     * Get sim color resources in light
     * @param colorId sim color id
     * @return the color resource 
     */
    public static int getSimColorLightResource(int colorId) {
        int bgColor = -1;
        if ((colorId >= 0) && (colorId < SimInfoManager.SimBackgroundDarkRes.length)) {
            bgColor = SimInfoManager.SimBackgroundLightRes[colorId];
        } else if (colorId == INTERNET_COLOR_ID) {
            bgColor = com.mediatek.internal.R.drawable.sim_background_sip;
        } 
        return bgColor;

    }

    public static boolean isAllRadioOff(Context context) {
        int airMode = Settings.System.getInt(context.getContentResolver(), Settings.System.AIRPLANE_MODE_ON, -1);
        int dualMode = Settings.System.getInt(context.getContentResolver(), Settings.System.DUAL_SIM_MODE_SETTING, -1);
        return airMode == 1 || dualMode == 0;
    } 

    public static int getSimIndicator(Context context, int slotId) {
        if (isAllRadioOff(context)) {
            Log.d(TAG, "isAllRadioOff=" + isAllRadioOff(context) + "slotId=" + slotId);
            return PhoneConstants.SIM_INDICATOR_RADIOOFF;
        }

        ITelephony iTelephony = ITelephony.Stub.asInterface(ServiceManager.getService(Context.TELEPHONY_SERVICE));
        ITelephonyEx iTelephonyEx = ITelephonyEx.Stub.asInterface(ServiceManager.getService(Context.TELEPHONY_SERVICEEX));
        int indicator = PhoneConstants.SIM_INDICATOR_UNKNOWN;
        if (iTelephony != null && iTelephonyEx != null) {
            try {
                indicator = FeatureOption.MTK_GEMINI_SUPPORT ? 
                        iTelephonyEx.getSimIndicatorState(slotId)
                        : iTelephony.getSimIndicatorState();
            } catch (RemoteException e) {
                Log.d(TAG, "RemoteException");
            } catch (NullPointerException ex) {
                Log.d(TAG, "NullPointerException");
            }
        }
        return indicator;
    }

    public static List<SimInfoRecord> get3GSimCard(Activity activity) {
        List<SimInfoRecord> siminfoList = new ArrayList<SimInfoRecord>();
        if (FeatureOption.MTK_GEMINI_3G_SWITCH) {
            int slot = get3GCapabilitySim();
            if (slot >= 0) {
                siminfoList.add(SimInfoManager.getSimInfoBySlot(activity, slot));
            }
        } else {
            List<SimInfoRecord> simInserted = SimInfoManager.getInsertedSimInfoList(activity);
            for (SimInfoRecord simInfo : simInserted) {
                int baseband = getBaseband(simInfo.mSimSlotId);
                if (baseband > MODEM_3G) {
                    siminfoList.add(simInfo);
                }
            }
        }
        return siminfoList;
    }

    public static int getBaseband(int slot) {
        String propertyKey = GEMINI_BASEBAND_PROP[slot];

        int baseband = 0;
        try {
            String capability = SystemProperties.get(propertyKey);
            if (capability != null) {
                baseband = Integer.parseInt(capability);
            }
        } catch (NumberFormatException e) {
            Log.d(TAG, "get base band error");
        }
        Log.d(TAG, "[slot = " + slot + "]");
        Log.d(TAG,  "[propertyKey = " + propertyKey + "]");
        Log.d(TAG, "[baseband = " + baseband + "]");
        return baseband;
    }

    private static int get3GCapabilitySim() {
        int slotId = -1;
        ITelephonyEx iTelephonyEx = ITelephonyEx.Stub.asInterface(ServiceManager.getService(Context.TELEPHONY_SERVICEEX));
        try {
            slotId = iTelephonyEx.get3GCapabilitySIM();
        } catch (RemoteException e) {
            Log.e(TAG, "mTelephony exception");
        }
        Log.d(TAG, "updateVideoCallDefaultSIM()---slotId=" + slotId);
        return slotId;
    }
    
    /**
     * support keep phone number style
     * @param number
     * @return the phone number that has common style
     */
    public static String phoneNumString(String number) {
        Log.d(TAG, "phoneNumString, number = " + number);
        if (number != null) {
            number = "\u202a" + number + "\u202c";    
        }
        return number;
    }
}
