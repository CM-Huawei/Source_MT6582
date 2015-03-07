package com.mediatek.gemini;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.os.RemoteException;
import android.preference.PreferenceActivity;
import android.provider.Settings;

import com.android.internal.telephony.ITelephony;
import com.android.internal.telephony.PhoneConstants;

import com.mediatek.common.featureoption.FeatureOption;
import com.mediatek.common.telephony.ITelephonyEx;
import com.mediatek.telephony.SimInfoManager;
import com.mediatek.telephony.SimInfoManager.SimInfoRecord;
import com.mediatek.xlog.Xlog;

import java.util.Comparator;
import java.util.List;

/**
 * Contains utility functions for getting framework resource
 */
public class GeminiUtils {

    public static final int INTERNET_COLOR_ID = SimInfoManager.SimBackgroundDarkRes.length;
    public static final int IMAGE_GRAY = 75;// 30% of 0xff in transparent
    public static final int ORIGINAL_IMAGE = 255;
    public static int sG3SlotID = PhoneConstants.GEMINI_SIM_1;
    public static final String EXTRA_SIMID = "simid";
    // stands for slot Id (0 or 1 ) not sim id which is int not long
    public static final String EXTRA_SLOTID = "slotid";
    public static final String INTENT_CARD_SELECT = "com.mediatek.gemini.action.SELECT_SIM";
    public static final int REQUEST_SIM_SELECT = 7777;
    public static final int UNDEFINED_SLOT_ID = -1;
    public static final int UNDEFINED_SIM_ID = -1;
    public static final int ERROR_SLOT_ID = -2;
    public static final int PIN1_REQUEST_CODE = 302;
    private static final String TAG = "GeminiUtils";
    
    /**
     * a class for sort the sim info in order of slot id
     *
     */
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
        switch (state) {
        case PhoneConstants.SIM_INDICATOR_RADIOOFF:
            return com.mediatek.internal.R.drawable.sim_radio_off;
        case PhoneConstants.SIM_INDICATOR_LOCKED:
            return com.mediatek.internal.R.drawable.sim_locked;
        case PhoneConstants.SIM_INDICATOR_INVALID:
            return com.mediatek.internal.R.drawable.sim_invalid;
        case PhoneConstants.SIM_INDICATOR_SEARCHING:
            return com.mediatek.internal.R.drawable.sim_searching;
        case PhoneConstants.SIM_INDICATOR_ROAMING:
            return com.mediatek.internal.R.drawable.sim_roaming;
        case PhoneConstants.SIM_INDICATOR_CONNECTED:
            return com.mediatek.internal.R.drawable.sim_connected;
        case PhoneConstants.SIM_INDICATOR_ROAMINGCONNECTED:
            return com.mediatek.internal.R.drawable.sim_roaming_connected;
        default:
            return PhoneConstants.SIM_INDICATOR_UNKNOWN;
        }
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
     * Get slot Id for selecting sim card
     * 
     * @param context Context
     * @return  If only one sim inserted return mSimSlotId, otherwise return -1 for launch select sim activity
     */
    public static int getTargetSlotId(Context context) {
        List<SimInfoRecord> simInfoList = SimInfoManager.getInsertedSimInfoList(context);
        int simSize = simInfoList.size();
        int slotId = UNDEFINED_SLOT_ID;
        if (simSize == 1) {
            slotId = simInfoList.get(0).mSimSlotId;
        }
        return slotId;
    }
    
    /**
     * Go back selecting sim activity when press back key
     * @param activity the activity
     * @param needFinish finish the activity or not
     */
    public static void goBackSimSelection(Activity activity, boolean needFinish) {
        if (!activity.getFragmentManager().popBackStackImmediate()) {
            Intent it = activity.getIntent();
            int slotId = it.getIntExtra(GeminiUtils.EXTRA_SLOTID, GeminiUtils.ERROR_SLOT_ID);
            Xlog.d(TAG, "slotid is " + slotId);
            if (slotId != GeminiUtils.ERROR_SLOT_ID) {
                activity.finish();
            } else {
                backToSimcardUnlock(activity , needFinish);
            }
        }
    }

     public static void backToSimcardUnlock(Activity activity, boolean needFinish) {
        List<SimInfoRecord> simInfoList = SimInfoManager
                .getInsertedSimInfoList(activity);
        int simSize = simInfoList.size();
        if (simSize > 1) {
            Intent intent = new Intent();
            intent.setClassName(activity.getApplication().getPackageName(),
                    activity.getClass().getCanonicalName());
            Xlog.d(TAG, "packageName: "
                    + activity.getApplication().getPackageName()
                    + "className: " + activity.getClass().getCanonicalName());
            activity.startActivity(intent);
            if (needFinish) {
                activity.finish();
            }
        } else {
            activity.finish();
        }
    }
    
    /**
     * Get the sim inidicators for single sim
     * @param iTelephony Itelephony interface
     * @return sim indicator
     */
    public static int getSimIndicator(ContentResolver resolver, ITelephony iTelephony) {
        Xlog.d(TAG,"getSimIndicator for single");
        boolean isAirplaneOn = Settings.System.getInt(resolver,
                Settings.System.AIRPLANE_MODE_ON, -1) == 1;
        if (isAirplaneOn) {
            Xlog.d(TAG,"isAirplaneOn = " + isAirplaneOn);
            return PhoneConstants.SIM_INDICATOR_RADIOOFF;
        }
        int indicator = PhoneConstants.SIM_INDICATOR_UNKNOWN;
        if (iTelephony != null) {
            try {
                indicator = iTelephony.getSimIndicatorState();
            } catch (RemoteException e) {
                Xlog.e(TAG, "RemoteException");
            } catch (NullPointerException ex) {
                Xlog.e(TAG, "NullPointerException");
            }
        }
        return indicator;
    }
    
    /**
     * Get the sim inidicators for gemini
     * @param resolver
     * @param iTelephonyEx
     * @param slotId the slot to get indicator
     * @return the sim indicator of the slot
     */
    public static int getSimIndicatorGemini(ContentResolver resolver, ITelephonyEx iTelephonyEx, int slotId) {
        Xlog.d(TAG,"getSimIndicator---slotId=" + slotId);
        boolean isAirplaneOn = Settings.System.getInt(resolver,
                Settings.System.AIRPLANE_MODE_ON, -1) == 1;
        if (isAirplaneOn) {
            Xlog.d(TAG,"isAirplaneOn = " + isAirplaneOn);
            return PhoneConstants.SIM_INDICATOR_RADIOOFF;
        }
        int indicator = PhoneConstants.SIM_INDICATOR_UNKNOWN;
        if (iTelephonyEx != null) {
            try {
                indicator = iTelephonyEx.getSimIndicatorState(slotId);
            } catch (RemoteException e) {
                Xlog.e(TAG, "RemoteException");
            } catch (NullPointerException ex) {
                Xlog.e(TAG, "NullPointerException");
            }
        }
        return indicator;
    }
    
    /**
     * Get sim slot Id by passing the sim info id
     * @param simInfoId sim info id
     * @param simInfoList a SimInfoRecord list
     * @return the sim slot id or -1
     */
    public static int getSimSlotIdBySimInfoId(long simInfoId, List<SimInfoRecord> simInfoList) {
        for (SimInfoRecord siminfo : simInfoList) {
            if (siminfo.mSimInfoId == simInfoId) {
                return siminfo.mSimSlotId;
            }
        }
        return UNDEFINED_SLOT_ID;
    }
    
    /**
     * Get sim info id by passing the sim slot id
     * @param slotId sim slot id
     * @param simInfoList a SimInfoRecord list
     * @return the sim info id or -1
     */
    public static long getSiminfoIdBySimSlotId(int slotId, List<SimInfoRecord> simInfoList) {
        for (SimInfoRecord siminfo : simInfoList) {
            if (siminfo.mSimSlotId == slotId) {
                return siminfo.mSimInfoId;
            }
        }
        return UNDEFINED_SIM_ID;
    }
    
    /**
     * Go back to settings main activity
     * @param activity Activity context
     */
    public static void goBackSettings(Activity activity) {
        Intent intent = new Intent(activity, com.android.settings.Settings.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        activity.startActivity(intent);
        activity.finish();
    }
    
    /**
     * Start the SelectSimActivity for select SIM card
     * @param activity parent activity
     * @param titleId title id to pass, if no title pass <= 0 
     */
    public static void startSelectSimActivity(Activity activity, int titleId) {
        Intent intent = new Intent();
        intent.setAction(INTENT_CARD_SELECT);
        if (titleId >= 0) {
            intent.putExtra(PreferenceActivity.EXTRA_SHOW_FRAGMENT_TITLE, titleId);    
        }
        activity.startActivityForResult(intent, REQUEST_SIM_SELECT);
    }
}
