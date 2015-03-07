package com.mediatek.phone;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.RegistrantList;
import android.util.Log;

import com.android.internal.telephony.TelephonyIntents;
import com.mediatek.telephony.SimInfoManager;
import com.mediatek.telephony.SimInfoManager.SimInfoRecord;

import java.util.HashMap;
import java.util.List;

public class SIMInfoWrapper {
    private static final String TAG = "SIMInfoWrapper";
    private static final boolean DBG = true;

    private Context mContext;
    private static List<SimInfoRecord> mAllSimInfoList = null;
    private static List<SimInfoRecord> mInsertedSimInfoList = null;

    private HashMap<Integer,SimInfoRecord> mAllSimInfoMap = new HashMap<Integer, SimInfoRecord>();
    private HashMap<Integer,SimInfoRecord> mInsertedSimInfoMap = new HashMap<Integer, SimInfoRecord>();
    private HashMap<Integer,Integer> mSlotIdSimIdPairs = new HashMap<Integer,Integer>();
    private HashMap<Integer,Integer> mSimIdSlotIdPairs = new HashMap<Integer,Integer>();

    private RegistrantList mSimInfoUpdateRegistrantList = new RegistrantList();

    private boolean mIsNeedToInitSimInfo;
    /// M: broadcast receiver registered flag.
    private boolean mIsBrRegistered = false;

    private static SIMInfoWrapper sSIMInfoWrapper;

    public void updateSimInfoCache() {
        if (DBG) {
            log("updateSimInfoCache()");
        }
        if (mAllSimInfoList != null) {
            mAllSimInfoList = SimInfoManager.getAllSimInfoList(mContext);
            if (mAllSimInfoList != null) {
                //mAllSimCount = mAllSimInfoList.size();
                mAllSimInfoMap = new HashMap<Integer, SimInfoRecord>();
                mSimIdSlotIdPairs = new HashMap<Integer, Integer>();
                for (SimInfoRecord item : mAllSimInfoList) {
                    int simId = getCheckedSimId(item);
                    if (simId != -1) {
                        mAllSimInfoMap.put(simId, item);
                        mSimIdSlotIdPairs.put(simId, item.mSimSlotId);
                    }
                }
                if (DBG) {
                    log("[updateSimInfo] update mAllSimInfoList");
                }
            } else {
                if (DBG) {
                    log("[updateSimInfo] updated mAllSimInfoList is null");
                }
                return;
            }
        }

        if (mInsertedSimInfoList != null) {
            mInsertedSimInfoList = SimInfoManager.getInsertedSimInfoList(mContext);
            if (mInsertedSimInfoList != null) {
                //mInsertedSimCount = mInsertedSimInfoList.size();
                mInsertedSimInfoMap = new HashMap<Integer, SimInfoRecord>();
                mSlotIdSimIdPairs = new HashMap<Integer, Integer>();
                for (SimInfoRecord item : mInsertedSimInfoList) {
                    int simId = getCheckedSimId(item);
                    if (simId != -1) {
                        mInsertedSimInfoMap.put(simId, item);
                        mSlotIdSimIdPairs.put(item.mSimSlotId, simId);
                    }
                }
                if (DBG) {
                    log("[updateSimInfo] update mInsertedSimInfoList");
                }
            } else {
                if (DBG) {
                    log("[updateSimInfo] updated mInsertedSimInfoList is null");
                }
                return;
            }
        }
        mSimInfoUpdateRegistrantList.notifyRegistrants();
    }

    /**
     * SimInfoRecord wrapper constructor. Build SimInfoRecord according to input type
     * 
     * @param context
     * @param isInsertSimOrAll
     */
    private SIMInfoWrapper() {
        mAllSimInfoMap = new HashMap<Integer, SimInfoRecord>();
        mInsertedSimInfoMap = new HashMap<Integer, SimInfoRecord>();
        mSlotIdSimIdPairs = new HashMap<Integer,Integer>();
        mSimIdSlotIdPairs = new HashMap<Integer,Integer>();
    }

    private int getCheckedSimId(SimInfoRecord item) {
        if (item != null && item.mSimInfoId > 0) {
            return (int) item.mSimInfoId;
        } else {
            if (DBG) {
                log("[getCheckedSimId]Wrong simId is "
                        + (item == null ? -1 : item.mSimInfoId));
            }
            return -1;
        }
    }

    /**
     * Public API to get SIMInfoWrapper instance
     * 
     * @param context
     * @param isInsertSim
     * @return SIMInfoWrapper instance
     */
    public static synchronized SIMInfoWrapper getDefault() {
        if (sIsNullResult) {
            return null;
        }
        if (sSIMInfoWrapper == null) {
            sSIMInfoWrapper = new SIMInfoWrapper();
        }
        if (sSIMInfoWrapper.mIsNeedToInitSimInfo) {
            sSIMInfoWrapper.initSimInfo();

            /// Add for for ALPS00540397 @{
            if (mAllSimInfoList != null && mInsertedSimInfoList != null) {
                if (DBG) {
                    log("getDefault() initSimInfo failed");
                }
                sSIMInfoWrapper.mIsNeedToInitSimInfo = false;
            }
            /// @}

        }
        return sSIMInfoWrapper;
    }

    /**
     * get cached SIM info list
     * 
     * @return
     */
    public List<SimInfoRecord> getSimInfoList() {
        return mAllSimInfoList;
    }

    /**
     * get cached SIM info list
     * 
     * @return
     */
    public List<SimInfoRecord> getAllSimInfoList() {
        return mAllSimInfoList;
    }

    /**
     * get cached SIM info list
     * 
     * @return
     */
    public List<SimInfoRecord> getInsertedSimInfoList() {
        return mInsertedSimInfoList;
    }

    /**
     * get SimInfoRecord cached HashMap
     * 
     * @return
     */
    public HashMap<Integer, SimInfoRecord> getSimInfoMap() {
        return mAllSimInfoMap;
    }

    /**
     * get SimInfoRecord cached HashMap
     * 
     * @return
     */
    public HashMap<Integer, SimInfoRecord> getInsertedSimInfoMap() {
        return mInsertedSimInfoMap;
    }

    /**
     * get cached SimInfoRecord from HashMap
     * 
     * @param id
     * @return
     */
    public SimInfoRecord getSimInfoById(int id) {
        return mAllSimInfoMap.get(id);
    }

    public SimInfoRecord getSimInfoBySlot(int slot) {
        SimInfoRecord simInfo = null;
        /// M: For ALPS00540397
        if (mInsertedSimInfoList != null) {
            for (int i = 0; i < mInsertedSimInfoList.size(); i++) {
                simInfo =   mInsertedSimInfoList.get(i);
                if (simInfo.mSimSlotId == slot) {
                    return simInfo;
                }
            }
        }
        return null;
    }

    /**
     * get SIM color according to input id
     * 
     * @param id
     * @return
     */
    public int getSimColorById(int id) {
        SimInfoRecord simInfo = mAllSimInfoMap.get(id);
        return (simInfo == null) ? -1 : simInfo.mColor;
    }

    /**
     * get SIM display name according to input id
     * 
     * @param id
     * @return
     */
    public String getSimDisplayNameById(int id) {
        SimInfoRecord simInfo = mAllSimInfoMap.get(id);
        return (simInfo == null) ? null : simInfo.mDisplayName;
    }

    /**
     * get SIM slot according to input id
     * 
     * @param id
     * @return
     */
    public int getSimSlotById(int id) {
        SimInfoRecord simInfo = mAllSimInfoMap.get(id);
        return (simInfo == null) ? -1 : simInfo.mSimSlotId;
    }

    /**
     * get cached SimInfoRecord from HashMap
     * 
     * @param id
     * @return
     */
    public SimInfoRecord getInsertedSimInfoById(int id) {
        return mInsertedSimInfoMap.get(id);
    }

    /**
     * get SIM color according to input id
     * 
     * @param id
     * @return
     */
    public int getInsertedSimColorById(int id) {
        SimInfoRecord simInfo = mInsertedSimInfoMap.get(id);
        return (simInfo == null) ? -1 : simInfo.mColor;
    }

    /**
     * get SIM display name according to input id
     * 
     * @param id
     * @return
     */
    public String getInsertedSimDisplayNameById(int id) {
        SimInfoRecord simInfo = mInsertedSimInfoMap.get(id);
        return (simInfo == null) ? null : simInfo.mDisplayName;
    }

    /**
     * get SIM slot according to input id
     * 
     * @param id
     * @return
     */
    public int getInsertedSimSlotById(int id) {
        SimInfoRecord simInfo = mInsertedSimInfoMap.get(id);
        return (simInfo == null) ? -1 : simInfo.mSimSlotId;
    }

    /**
     * get all SIM count according to Input
     * 
     * @return
     */
    public int getAllSimCount() {
        if (mAllSimInfoList != null) {
            return mAllSimInfoList.size();
        } else {
            return 0;
        }
    }

    /**
     * get inserted SIM count according to Input
     * 
     * @return
     */
    public int getInsertedSimCount() {
        if (mInsertedSimInfoList != null) {
            return mInsertedSimInfoList.size();
        } else {
            return 0;
        }
    }
    
    public int getSlotIdBySimId(int simId) {
        // return mSlotIdSimIdPairs.get(simId);
        Integer i = mSimIdSlotIdPairs.get(simId);
        return ((i == null) ? -1 : i);
    }

    public int getSimIdBySlotId(int slotId) {
        // return mSimIdSlotIdPairs.get(slotId);
        Integer i = mSlotIdSimIdPairs.get(slotId);
        return ((i == null) ? -1 : i);
    }

    /**
     * Get Sim Display Name according to slot id
     * 
     * @param slotId
     * @return
     */
    public String getSimDisplayNameBySlotId(int slotId) {
        String simDisplayName = null;
        int i = getSimIdBySlotId(slotId);
        simDisplayName = getSimDisplayNameById(i);
        return simDisplayName;
    }

    public void registerForSimInfoUpdate(Handler h, int what, Object obj) {
        mSimInfoUpdateRegistrantList.addUnique(h, what, obj);
    }

    public void unregisterForSimInfoUpdate(Handler h) {
        mSimInfoUpdateRegistrantList.remove(h);
    }
    
    public int getSimBackgroundResByColorId(int colorId) {
        log("getSimBackgroundResByColorId() colorId = " + colorId);
        if (colorId < 0 || colorId > 3) {
            colorId = 0;
        }
        return SimInfoManager.SimBackgroundRes[colorId];
    }

    public int getSimBackgroundDarkResByColorId(int colorId) {
        log("getSimBackgroundDarkResByColorId() colorId = " + colorId);
        if (colorId < 0 || colorId > 3) {
            colorId = 0;
        }
        return SimInfoManager.SimBackgroundDarkRes[colorId];
    }

    public int getSimBackgroundLightResByColorId(int colorId) {
        log("getSimBackgroundLightResByColorId() colorId = " + colorId);
        if (colorId < 0 || colorId > 3) {
            colorId = 0;
        }
        return SimInfoManager.SimBackgroundLightRes[colorId];
    }

    public void init(Context context) {
        mContext = context;
        IntentFilter iFilter = new IntentFilter();
        iFilter.addAction(Intent.SIM_SETTINGS_INFO_CHANGED);
        iFilter.addAction(TelephonyIntents.ACTION_SIM_INFO_UPDATE);
        iFilter.addAction(TelephonyIntents.ACTION_SIM_NAME_UPDATE);
        mContext.registerReceiver(mReceiver, iFilter);
        mIsBrRegistered = mIsNeedToInitSimInfo = true;
    }

    private void initSimInfo() {
        mAllSimInfoList = SimInfoManager.getAllSimInfoList(mContext);
        mInsertedSimInfoList = SimInfoManager.getInsertedSimInfoList(mContext);

        if (mAllSimInfoList == null || mInsertedSimInfoList == null) {
            log("[SIMInfoWrapper] mSimInfoList OR mInsertedSimInfoList is null");
            return;
        }

        //mAllSimCount = mAllSimInfoList.size();
        //mInsertedSimCount = mInsertedSimInfoList.size();
        
        for (SimInfoRecord item : mAllSimInfoList) {
            int simId = getCheckedSimId(item);
            if (simId != -1) {
                mAllSimInfoMap.put(simId, item);
                mSimIdSlotIdPairs.put(simId, item.mSimSlotId);
            }
        }

        for (SimInfoRecord item : mInsertedSimInfoList) {
            int simId = getCheckedSimId(item);
            if (simId != -1) {
                mInsertedSimInfoMap.put(simId, item);
                mSlotIdSimIdPairs.put(item.mSimSlotId, simId);
            }
        }
    }
    /**
     * Unregister context receiver
     * Should called when the context is end of life.
     */
    public void release() {
        if (mContext != null && mIsBrRegistered) {
            mContext.unregisterReceiver(mReceiver);
            mIsBrRegistered = false;
        }
    }

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (DBG) {
                log("onReceive(), action = " + action);
            }
            if (action.equals(Intent.SIM_SETTINGS_INFO_CHANGED) ||
                action.equals(TelephonyIntents.ACTION_SIM_NAME_UPDATE) ||
                action.equals(TelephonyIntents.ACTION_SIM_INFO_UPDATE)) {
                updateSimInfoCache();
            }
        }
    };

    private static void log(String msg) {
        Log.d(TAG, msg);
    }

    /**
     * For test 
     */
    private static boolean sIsNullResult = false;
    public static void setNull(boolean testMode) {
        sSIMInfoWrapper = null;
        sIsNullResult = testMode;
    }
}
