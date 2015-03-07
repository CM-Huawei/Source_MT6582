package com.mediatek.phone.gemini;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import junit.framework.Assert;

import com.mediatek.common.featureoption.FeatureOption;
import com.mediatek.phone.GeminiConstants;
import com.mediatek.phone.SIMInfoWrapper;
import com.mediatek.phone.wrapper.ITelephonyWrapper;
import com.mediatek.phone.wrapper.PhoneWrapper;
import com.mediatek.phone.wrapper.TelephonyManagerWrapper;
import com.mediatek.telephony.SimInfoManager;
import com.mediatek.telephony.SimInfoManager.SimInfoRecord;
import com.mediatek.telephony.TelephonyManagerEx;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Message;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.SystemProperties;
import android.preference.Preference;
import android.provider.Settings;
import android.telephony.TelephonyManager;
import android.telephony.ServiceState;
import android.util.Log;
import android.widget.Toast;

import com.android.internal.telephony.Call;
import com.android.internal.telephony.CallStateException;
import com.android.internal.telephony.CallerInfo;
import com.android.internal.telephony.CallerInfoAsyncQuery;
import com.android.internal.telephony.CallerInfoAsyncQuery.OnQueryCompleteListener;
import com.android.internal.telephony.ITelephony;
import com.mediatek.common.telephony.ITelephonyEx;
import com.android.internal.telephony.IccCard;
import com.android.internal.telephony.uicc.IccFileHandler;
import com.android.internal.telephony.MmiCode;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.TelephonyProperties;
import com.android.phone.PhoneGlobals;
import com.android.phone.PhoneUtils;
import com.android.phone.R;

import com.mediatek.gemini.simui.CommonUtils;
import com.mediatek.settings.PreCheckForRunning;

/**
 * M:Gemini+ For slot information and related methods.
 */
public final class GeminiUtils extends CommonUtils {
    private static final String TAG = "Gemini";
    private static final boolean DEBUG = true;

    private static GeminiUtils sInstance = new GeminiUtils();

    private static final int GET_SIM_RETRY_EMPTY = -1;

    public static final String TRANSACTION_START = "com.android.mms.transaction.START";
    public static final String TRANSACTION_STOP = "com.android.mms.transaction.STOP";

    public static final String EXTRA_CDMA_SUPPORT = "EXTRA_CDMA_SUPPORT";
    public static final String INTENT_CARD_SELECT = "com.mediatek.gemini.action.SELECT_SIM";

    // Icc card type
    public static final String SIM_TYPE_USIM_TAG = "USIM";
    public static final String SIM_TYPE_SIM_TAG = "SIM";

    // SimSelectActicity's title key, value will pass to settings
    private static final String EXTRA_TITLE = "title";
    public static final long UNDEFINED_SIM_ID = -1;

    public static final String THEME_RESOURCE_ID = "Theme_resource_id";
    public static final int UNDEFINED_SLOT_ID = -1;
    public static final int REQUEST_SIM_SELECT = 100;
    public static final int PROGRESS_DIALOG = 400;
    // smart 3g switch
    public static final int SWITCH_MANUAL_ALLOWED_SLOT1 = 1;
    public static final int SWITCH_MANUAL_ALLOWED_SLOT2 = 2;
    public static final int SWITCH_MANUAL_ALLOWED_SLOT3 = 4;
    public static final int SWITCH_MANUAL_ALLOWED_SLOT4 = 8;

    public static final int MODEM_MASK_LTE = 0x80;

    public static final String GEMINI_FDN_URI[] = {
        "content://icc/fdn1",
        "content://icc/fdn2",
        "content://icc/fdn3",
        "content://icc/fdn4",
    };

    public static final String GEMINI_PIN2_RETRY[] = {
        "gsm.sim.retry.pin2",
        "gsm.sim.retry.pin2.2",
        "gsm.sim.retry.pin2.3",
        "gsm.sim.retry.pin2.4",
    };

    public static final String GEMINI_PUK2_RETRY[] = {
        "gsm.sim.retry.puk2",
        "gsm.sim.retry.puk2.2",
        "gsm.sim.retry.puk2.3",
        "gsm.sim.retry.puk2.4",
    };

    private static final String PACKAGE_NAME = "com.android.phone";
    private static final String CDMA_CHANGE_FEATURE[][] = {
        {"button_fdn_key", ""},
        {"button_cf_expand_key", "com.mediatek.settings.CdmaCallForwardOptions"},
        {"button_cb_expand_key", ""},
        {"button_plmn_key", ""},
        {"button_carrier_sel_key", ""},
        {"button_more_expand_key", "com.mediatek.settings.CdmaCallWaitingOptions"},
    };


    /**
     * get the slot number of device.
     * 
     * @return
     */
    public static final int getSlotCount() {
        return GeminiConstants.SLOTS.length;
    }

    /**
     * PhoneConstants.GEMINI_SIM_1, PhoneConstants.GEMINI_SIM_2...
     * 
     * @return
     */
    public static int[] getSlots() {
        return GeminiConstants.SLOTS;
    }

    /**
     * @return PhoneConstants.GEMINI_SIM_1
     */
    public static int getDefaultSlot() {
        return GeminiConstants.SLOT_ID_1;
    }

    /**
     * Find a CDMA slot by {@link PHONE_TYPE_CDMA}
     * 
     * @return CDMA Slot Id
     */
    public static int getCDMASlot() {
        return GeminiUtils.getSlotByPhoneType(PhoneConstants.PHONE_TYPE_CDMA);
    }

    /**
     * check the slotId value.
     * 
     * @param slotId
     * @return
     */
    public static boolean isValidSlot(int slotId) {
        final int[] geminiSlots = getSlots();
        for (int i = 0; i < geminiSlots.length; i++) {
            if (geminiSlots[i] == slotId) {
                return true;
            }
        }
        return false;
    }

    /**
     * Get the operator name by slotId.
     * 
     * @param slotId
     * @return
     */
    public static String getOperatorName(int slotId) {
        String operatorName = null;
        if (isGeminiSupport() && isValidSlot(slotId)) {
            SimInfoRecord simInfo = SIMInfoWrapper.getDefault().getSimInfoBySlot(slotId);
            if (simInfo != null) {
                operatorName = simInfo.mDisplayName;
                log("getOperatorName, operatorName= " + simInfo.mDisplayName);
            }
        } else {
            operatorName = getSystemProperties(TelephonyProperties.PROPERTY_OPERATOR_ALPHA);
        }
        log("getOperatorName, slotId=" + slotId + " operatorName=" + operatorName);
        return operatorName;
    }

    public static String getNetworkOperatorName(Call call) {
        if (isGeminiSupport() && call != null) {
            String operatorName = null;
            SimInfoRecord info = PhoneUtils.getSimInfoByCall(call);
            if (null != info && call.getState() != Call.State.IDLE) {
                final int slotIndex = getIndexInArray(info.mSimSlotId, getSlots());
                if (slotIndex >= 0) {
                    return getSystemProperties(GeminiConstants.PROPERTY_OPERATOR_ALPHAS[slotIndex]);
                }
            }
        }
        return PhoneWrapper.getNetworkOperatorName();
    }

    /**
     * get VT network operator name. The operator name gets from SystemProperties.
     * {@link PROPERTY_OPERATOR_GEMINI} {@link get3GCapabilitySIM}
     * 
     * @return
     */
    public static String getVTNetworkOperatorName(Call call) {
        String operatorName = null;
        if (isGeminiSupport() && call != null) {
            int slot = -1;
            SimInfoRecord info = PhoneUtils.getSimInfoByCall(call);
            if (null != call && null != info && call.getState() != Call.State.IDLE) {
                slot = info.mSimSlotId;
            }
            int index = getIndexInArray(slot, getSlots());
            if (index >= 0) {
                operatorName = getSystemProperties(GeminiConstants.PROPERTY_OPERATOR_ALPHAS[index]);
            }
        } else {
            operatorName = getSystemProperties(TelephonyProperties.PROPERTY_OPERATOR_ALPHA);
        }
        log("getVTNetworkOperatorName, operatorName= " + operatorName);
        return operatorName;
    }

    /**
     * get 3G capability slotId by ITelephony.get3GCapabilitySIM();
     * 
     * @return the SIM id which support 3G.
     */
    public static int get3GCapabilitySIM() {
        ITelephonyEx iTelephonyEx = PhoneGlobals.getInstance().phoneMgrEx;
        try {
            final int slot3G = iTelephonyEx.get3GCapabilitySIM();
            log("get3GCapabilitySIM, slot3G" + slot3G);
            return slot3G;
        } catch (RemoteException re) {
            log("get3GCapabilitySIM, " + re.getMessage() + ", return -1");
            return -1;
        }
    }

    /**
     * get 3G Sim Card
     * @param context
     * @return
     */
    public static List<SimInfoRecord> get3GSimCards(Context context) {
        List<SimInfoRecord> siminfoList = new ArrayList<SimInfoRecord>();
        if (FeatureOption.MTK_GEMINI_3G_SWITCH) {
            int slotId = get3GCapabilitySIM();
            if (isValidSlot(slotId)) {
                List<SimInfoRecord> simInserted = SimInfoManager.getInsertedSimInfoList(context);
                for (SimInfoRecord simInfo : simInserted) {
                    if (simInfo.mSimSlotId == slotId) {
                        siminfoList.add(simInfo);
                    }
                }
            }
        } else {
            List<SimInfoRecord> simInserted = SimInfoManager.getInsertedSimInfoList(context);
            for (SimInfoRecord simInfo : simInserted) {
                int baseband = getBaseband(simInfo.mSimSlotId);
                if (baseband > MODEM_3G) {
                    siminfoList.add(simInfo);
                }
            }
        }
        return siminfoList;
    }

    /**
     * get voiceMailNumber by slotId.
     * 
     * @param slotId
     * @return
     */
    public static String getVoiceMailNumber(int slotId) {
        String voiceMailNumber = null;
        voiceMailNumber = TelephonyManagerWrapper.getVoiceMailNumber(slotId);
        return voiceMailNumber;
    }

    public static Uri getSimFdnUri(int slotId) {
        Uri uri = null;
        final int index = getIndexInArray(slotId, getSlots());
        if (index >= 0) {
            uri = Uri.parse(GeminiConstants.FDN_CONTENT_GEMINI[index]);
        } else {
            uri = Uri.parse(GeminiConstants.FDN_CONTENT);
        }
        return uri;
    }

    public static int getPinRetryNumber(int slotId) {
        final int index = getIndexInArray(slotId, getSlots());
        int number = -1;
        if (index >= 0) {
            number = getIntSystemProperties(GeminiConstants.GSM_SIM_RETRY_PIN_GEMINI[index], -1);
        }
        return number;
    }

    public static int getPin2RetryNumber(int slotId) {
        final int index = getIndexInArray(slotId, getSlots());
        int number = -1;
        if (index >= 0) {
            number = getIntSystemProperties(GeminiConstants.GSM_SIM_RETRY_PIN2_GEMINI[index], -1);
        }
        return number;
    }

    public static int getPuk2RetryNumber(int slotId) {
        final int index = getIndexInArray(slotId, getSlots());
        int number = -1;
        if (index >= 0) {
            number = getIntSystemProperties(GeminiConstants.GSM_SIM_RETRY_PUK2_GEMINI[index], -1);
        }
        return number;
    }

    /**
     * get the index position of value in the array. If the array doesn't
     * contains the value, return -1.
     * 
     * @param value
     * @param array
     * @return
     */
    public static int getIndexInArray(int value, int[] array) {
        for (int i = 0; i < array.length; i++) {
            if (value == array[i]) {
                return i;
            }
        }
        log("getIndexInArray failed, value=" + value + ", array=" + array.toString());
        return -1;
    }

    /**
     * @see FeatureOption.MTK_GEMINI_SUPPORT
     * @see FeatureOption.MTK_GEMINI_3SIM_SUPPORT
     * @see FeatureOption.MTK_GEMINI_4SIM_SUPPORT
     * @return true if the device has 2 or more slots
     */
    public static boolean isGeminiSupport() {
        return GeminiConstants.SOLT_NUM >= 2;
    }

    /**
     * agency method for application to get {@link CallerInfoAsyncQuery}. if
     * GEMINI and isSipPhone is false, it calls
     * {@link CallerInfoAsyncQuery#startQueryGemini}, else
     * {@link CallerInfoAsyncQuery#startQuery}
     *
     * @param token
     * @param context
     * @param number
     * @param listener
     * @param cookie
     * @param simId
     * @param isSipPhone
     * @return
     */
    public static CallerInfoAsyncQuery startQueryGemini(int token, Context context, String number,
            OnQueryCompleteListener listener, Object cookie, int simId, boolean isSipPhone) {
        CallerInfoAsyncQuery asyncQuery = null;
        if (isGeminiSupport() && !isSipPhone) {
            asyncQuery = CallerInfoAsyncQuery.startQueryEx(token, context, number, listener,
                    cookie, simId);

        } else {
            asyncQuery = CallerInfoAsyncQuery.startQuery(token, context, number, listener, cookie);
        }
        return asyncQuery;
    }

    /**
     * get slotId by phone type
     *
     * @param phoneType {@link PhoneConstants.PHONE_TYPE_CDMA},
     *            {@link PhoneConstants.PHONE_TYPE_GSM}
     * @return
     */
    public static int getSlotByPhoneType(int phoneType) {
        int slot = PhoneConstants.GEMINI_SIM_1;
        slot = TelephonyManagerWrapper.getSlotByPhoneType(phoneType);
        log("getSlotByPhoneType with phontType = " + phoneType + " and return slot = " + slot);
        return slot;
    }

    public static boolean isPhbReady(Phone phone, int slotId) {
        Assert.assertNotNull(phone);
        final boolean isPhbReady = ITelephonyWrapper.isPhbReady(slotId);
        log("getIccRecordsLoaded : isPhbReady:" + isPhbReady + ", slotId:" + slotId);
        return isPhbReady;
    }

    /**
     * @param phone
     * @return true if the ring call contains only disconnected connections
     */
    public static boolean isPhoneRingingCallIdle(Phone phone) {
        Assert.assertNotNull(phone);
        log("isPhoneRingingCallIdle :" + phone.getRingingCall().isIdle());
        return phone.getRingingCall().isIdle();
    }

    /**
     * @param phone
     * @return true if the fg call contains only disconnected connections
     */
    public static boolean isPhoneForegroundCallIdle(Phone phone) {
        Assert.assertNotNull(phone);
        log("isPhoneForegroundCallIdle :" + phone.getForegroundCall().isIdle());
        return phone.getForegroundCall().isIdle();
    }

    /**
     * @param phone
     * @return true if the bg call contains only disconnected connections
     */
    public static boolean isPhoneBackgroundCallIdle(Phone phone) {
        Assert.assertNotNull(phone);
        log("isPhoneBackgroundCallIdle :" + phone.getBackgroundCall().isIdle());
        return phone.getBackgroundCall().isIdle();
    }

    /**
     * return true if the phone's foregroundCall call state is {@link Call#State#DIALING}
     * 
     * @param phone
     * @return
     */
    public static boolean isDialing(Phone phone) {
        Assert.assertNotNull(phone);
        boolean isDialing = false;
        Call fgCall = phone.getForegroundCall();
        isDialing = (fgCall.getState() == Call.State.DIALING);
        log("isDialing, isDialing:" + isDialing);
        return isDialing;
    }

    private static String getSystemProperties(String key) {
        return SystemProperties.get(key);
    }

    private static int getIntSystemProperties(String key, int defValue) {
        return SystemProperties.getInt(key, defValue);
    }

    public static boolean isSimInService(int slotId) {
        int state = PhoneWrapper.getServiceState(PhoneGlobals.getPhone(), slotId).getState();
        Log.d(TAG, "isSimInService state: "  + state);
        return state == android.telephony.ServiceState.STATE_IN_SERVICE;
    }

    public static boolean isSimStateReady(int slot) {
        boolean isSimStateReady = false;
        isSimStateReady = TelephonyManager.SIM_STATE_READY == TelephonyManagerWrapper.getSimState(slot);
        Log.d(TAG, "isSimStateReady isSimStateReady: "  + isSimStateReady);
        return isSimStateReady;
    }

    public static void goUpToTopLevelSetting(Activity activity, Class<?> targetClass) {
        Intent intent = new Intent(activity.getApplicationContext(), targetClass);
        intent.setAction(Intent.ACTION_MAIN);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        activity.startActivity(intent);
        activity.finish();
    }

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

    public static long getSiminfoIdBySimSlotId(int slotId, List<SimInfoRecord> simInfoList) {
        for (SimInfoRecord siminfo : simInfoList) {
            if (siminfo.mSimSlotId == slotId) {
                return siminfo.mSimInfoId;
            }
        }
        return UNDEFINED_SLOT_ID;
    }

    /**
     * add this for smart 3G switch(2.0)
     * @param slotId
     * @return
     */
    public static boolean is3GSwitchManualEnableSlot(int slotId, boolean is3GSwitchManualChangeAllowed, int manualAllowedSlot) {
        if (manualAllowedSlot == 0) {
            log("not sim card support manual 3G Switch");
            return false;
        }
        boolean is3GSwitchManualSupportSlot = false;
        int[] manualAllowedSlotId = query3GSwitchManualEnableSlotId(manualAllowedSlot);
        if (is3GSwitchManualChangeAllowed) {
            is3GSwitchManualSupportSlot = true;
        } else {
            for (int slotItem : manualAllowedSlotId) {
                if (slotItem == slotId) {
                    is3GSwitchManualSupportSlot = true;
                    break;
                }
            }
        }
        log("is3GSwitchManualSupportSlot, slotId: " + is3GSwitchManualSupportSlot + " , " +slotId);
        return is3GSwitchManualSupportSlot;
    }

    /**
     * add this for smart 3G switch(2.0)
     * @param manualAllowedSlot
     * @return
     */
    public static int[] query3GSwitchManualEnableSlotId(int manualAllowedSlot) {
        int[] manualAllowedSlotId =  {-1, -1, -1, -1};
        if ((manualAllowedSlot & SWITCH_MANUAL_ALLOWED_SLOT1) > 0) {
            manualAllowedSlotId[0] = PhoneConstants.GEMINI_SIM_1;
        } else if ((manualAllowedSlot & SWITCH_MANUAL_ALLOWED_SLOT2) > 0) {
            manualAllowedSlotId[1] = PhoneConstants.GEMINI_SIM_2;
        } else if ((manualAllowedSlot & SWITCH_MANUAL_ALLOWED_SLOT3) > 0) {
            manualAllowedSlotId[2] = PhoneConstants.GEMINI_SIM_3;
        } else if ((manualAllowedSlot & SWITCH_MANUAL_ALLOWED_SLOT4) > 0) {
            manualAllowedSlotId[3] = PhoneConstants.GEMINI_SIM_4;
        }
        return manualAllowedSlotId;
    }

    public static void handleSimHotSwap(Activity activity, int slotId) {
        List<SimInfoRecord> temp = SimInfoManager.getInsertedSimInfoList(activity);
        Log.d(TAG, "slot id = " + slotId);
        if (GeminiUtils.getSiminfoIdBySimSlotId(slotId, temp) == GeminiUtils.UNDEFINED_SLOT_ID) {
            activity.finish();
        }
    }

    public static int getSlotId(Context context, String title, int themeResId) {
        int slotId = GeminiUtils.getTargetSlotId(context);
        if (slotId == GeminiUtils.UNDEFINED_SLOT_ID) {
            Intent intent = new Intent();
            intent.setAction(GeminiUtils.INTENT_CARD_SELECT);
            intent.putExtra(EXTRA_TITLE, title);
            intent.putExtra(THEME_RESOURCE_ID, themeResId);
            ((Activity)context).startActivityForResult(intent, GeminiUtils.REQUEST_SIM_SELECT);
        }
        Log.d(TAG, "[slotId = " + slotId + "]");
        return slotId;
    }

    public static void startActivity(int slotId, Preference preference, PreCheckForRunning preCfr) {
        Intent intent = getTargetIntent(slotId, preference);
        if (intent !=null) {
            preCfr.checkToRun(intent, slotId, PreCheckForRunning.PIN1_REQUEST_CODE);
        }
    }

    public static Intent getTargetIntent(int slotId, Preference preference) {
        /// M: Copy a new intent instead of using the one saved in preference directly. 
        /// In W+C/EVDO+G project, if user enter CDMA settings, the intent's class name is 
        /// changed to CDMA component class name; and when back to GSM settings, the intent's class
        /// name is not restored to GSM component class name, user still enter CDMA settings
        Intent intent = new Intent(preference.getIntent());
        intent.putExtra(GeminiConstants.SLOT_ID_KEY, slotId);
        if (FeatureOption.EVDO_DT_SUPPORT
                && TelephonyManagerEx.getDefault().getPhoneType(slotId) == PhoneConstants.PHONE_TYPE_CDMA) {
            String cdma = getCDMAFeature(preference.getKey());
            if (cdma.isEmpty()) {
                Toast.makeText(preference.getContext(), preference.getContext().getResources()
                        .getString(R.string.cdma_not_support), Toast.LENGTH_LONG).show();
                return null;
            } else {
                intent.setClassName(PACKAGE_NAME, cdma);
            }
        }
        return intent;
    }

    private static String getCDMAFeature(String key) {
        int length = CDMA_CHANGE_FEATURE.length;
        for (int i = 0; i < length; i++) {
            if (CDMA_CHANGE_FEATURE[i][0].equals(key)) {
                return CDMA_CHANGE_FEATURE[i][1];
            }
        }
        return "";
    }

    /**
     * Find the slot id for the value. ("value" is a member in "array").
     * 
     * @param value
     * @param array
     * @return
     */
    public static int getSlotIdByRegisterEvent(int value, int[] array) {
        Assert.assertNotNull(array);
        final int index = GeminiUtils.getIndexInArray(value, array);
        if (index != -1) {
            final int[] geminiSlots = getSlots();
            return geminiSlots[index];
        }
        return -1;
    }

    /**
     * Judge baseband of the given slot is 4G or not.
     * @param slot
     * @return
     */
    public static boolean is4GSimSlot(int slot) {
        boolean is4GSimSlot = false;
        log("[is4GSimSlot] slot = " + slot);
        if (UNDEFINED_SLOT_ID == slot) {
            //Do nothing
            log("[is4GSimSlot] slot = UNDEFINED_SLOT_ID(-1) !!");
        } else if (getBaseband(slot) >= MODEM_MASK_LTE) {
            is4GSimSlot = true;
        }
        return is4GSimSlot;
    }

    private static void log(String msg) {
        if (DEBUG) {
            Log.d(TAG, msg);
        }
    }
}
