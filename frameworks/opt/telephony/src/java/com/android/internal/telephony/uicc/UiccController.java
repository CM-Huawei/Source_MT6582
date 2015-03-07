/*
 * Copyright (C) 2011-2012 The Android Open Source Project
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

package com.android.internal.telephony.uicc;

import android.content.Context;
import android.os.AsyncResult;
import android.os.Handler;
import android.os.Message;
import android.os.Registrant;
import android.os.RegistrantList;
import android.telephony.Rlog;

import com.android.internal.telephony.CommandsInterface;
import com.android.internal.telephony.uicc.IccCardApplicationStatus.AppState;
import com.android.internal.telephony.uicc.IccCardApplicationStatus.AppType;
import com.android.internal.telephony.uicc.IccCardApplicationStatus.PersoSubState;
import java.io.FileDescriptor;
import java.io.PrintWriter;
import com.android.internal.telephony.Phone;

import com.mediatek.common.featureoption.FeatureOption;
import com.mediatek.common.MediatekClassFactory;
import com.mediatek.common.telephony.IUiccControllerExt;
import android.content.SharedPreferences;
import android.app.Notification;
import android.app.NotificationManager;
import android.content.Intent;
import android.app.PendingIntent;
import android.provider.Settings;
import android.content.res.Resources;

import android.content.BroadcastReceiver;
import android.content.IntentFilter;
import com.android.internal.telephony.gemini.GeminiPhone;
import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.PhoneFactory;
import android.telephony.ServiceState;
import com.android.internal.telephony.TelephonyIntents;
import com.android.internal.telephony.DefaultSIMSettings;
import android.os.SystemProperties;

//ALPS00776430: Clear MSISDN Start
import static android.Manifest.permission.READ_PHONE_STATE;
import android.app.ActivityManagerNative;
import android.os.UserHandle;
import android.provider.Telephony.SIMInfo;
//ALPS00776430: Clear MSISDN End

/**
 * This class is responsible for keeping all knowledge about
 * Universal Integrated Circuit Card (UICC), also know as SIM's,
 * in the system. It is also used as API to get appropriate
 * applications to pass them to phone and service trackers.
 *
 * UiccController is created with the call to make() function.
 * UiccController is a singleton and make() must only be called once
 * and throws an exception if called multiple times.
 *
 * Once created UiccController registers with RIL for "on" and "unsol_sim_status_changed"
 * notifications. When such notification arrives UiccController will call
 * getIccCardStatus (GET_SIM_STATUS). Based on the response of GET_SIM_STATUS
 * request appropriate tree of uicc objects will be created.
 *
 * Following is class diagram for uicc classes:
 *
 *                       UiccController
 *                            #
 *                            |
 *                        UiccCard
 *                          #   #
 *                          |   ------------------
 *                    UiccCardApplication    CatService
 *                      #            #
 *                      |            |
 *                 IccRecords    IccFileHandler
 *                 ^ ^ ^           ^ ^ ^ ^ ^
 *    SIMRecords---- | |           | | | | ---SIMFileHandler
 *    RuimRecords----- |           | | | ----RuimFileHandler
 *    IsimUiccRecords---           | | -----UsimFileHandler
 *                                 | ------CsimFileHandler
 *                                 ----IsimFileHandler
 *
 * Legend: # stands for Composition
 *         ^ stands for Generalization
 *
 * See also {@link com.android.internal.telephony.IccCard}
 * and {@link com.android.internal.telephony.uicc.IccCardProxy}
 */
public class UiccController extends Handler {
    private static final boolean DBG = true;
    private static final String LOG_TAG = "UiccController";

    public static final int APP_FAM_3GPP =  1;
    public static final int APP_FAM_3GPP2 = 2;
    public static final int APP_FAM_IMS   = 3;

    private static final int EVENT_ICC_STATUS_CHANGED = 1;
    private static final int EVENT_GET_ICC_STATUS_DONE = 2;
    
    protected static final int EVENT_RADIO_AVAILABLE = 100;
    protected static final int EVENT_VIRTUAL_SIM_ON = 101;
    protected static final int EVENT_VIRTUAL_SIM_OFF = 102;
    protected static final int EVENT_SIM_MISSING = 103;
    protected static final int EVENT_QUERY_SIM_MISSING_STATUS = 104;
    protected static final int EVENT_SIM_RECOVERY = 105;
    protected static final int EVENT_GET_ICC_STATUS_DONE_FOR_SIM_MISSING = 106;
    protected static final int EVENT_GET_ICC_STATUS_DONE_FOR_SIM_RECOVERY = 107;
    protected static final int EVENT_QUERY_ICCID_DONE_FOR_HOT_SWAP = 108;
    protected static final int EVENT_SIM_PLUG_OUT= 109;
    protected static final int EVENT_SIM_PLUG_IN = 110;
    protected static final int EVENT_HOTSWAP_GET_ICC_STATUS_DONE = 111;
    protected static final int EVENT_QUERY_SIM_STATUS_FOR_PLUG_IN = 112;
    protected static final int EVENT_QUERY_SIM_MISSING = 113;
    protected static final int EVENT_INVALID_SIM_DETECTED = 114;
    protected static final int EVENT_REPOLL_SML_STATE = 115;

    private static final int SML_FEATURE_NO_NEED_BROADCAST_INTENT = 0;
    private static final int SML_FEATURE_NEED_BROADCAST_INTENT = 1;

    private static final Object mLock = new Object();
    private static UiccController[] mInstance = {null, null, null, null};
    
    private Context mContext;
    private CommandsInterface mCi;
    private UiccCard mUiccCard;
    private int mSimId;
    private boolean mIsHotSwap = false;
    private boolean mClearMsisdn = false;

    private static final String ACTION_RESET_MODEM = "android.intent.action.sim.ACTION_RESET_MODEM";
    private static final String PROPERTY_3G_SWITCH = "gsm.3gswitch";

    private RegistrantList mIccChangedRegistrants = new RegistrantList();
    private RegistrantList mRecoveryRegistrants = new RegistrantList();


    private int[] UICCCONTROLLER_STRING_NOTIFICATION_SIM_MISSING = {
        com.mediatek.internal.R.string.sim_missing_slot1,
        com.mediatek.internal.R.string.sim_missing_slot2,
        com.mediatek.internal.R.string.sim_missing_slot3,
        com.mediatek.internal.R.string.sim_missing_slot4
    };
   
    private int[] UICCCONTROLLER_STRING_NOTIFICATION_VIRTUAL_SIM_ON = {
        com.mediatek.internal.R.string.virtual_sim_on_slot1,
        com.mediatek.internal.R.string.virtual_sim_on_slot2,
        com.mediatek.internal.R.string.virtual_sim_on_slot3,
        com.mediatek.internal.R.string.virtual_sim_on_slot4
    };

    private static IUiccControllerExt mUiccControllerExt;
    static {
        try{
            mUiccControllerExt = MediatekClassFactory.createInstance(IUiccControllerExt.class);
        } catch (Exception e){
            e.printStackTrace();
        }
    }
    
    public static UiccController make(Context c, CommandsInterface ci) {
        synchronized (mLock) {
            if (mInstance[0] != null) {
                throw new RuntimeException("UiccController.make() should only be called once");
            }
            mInstance[0] = new UiccController(c, ci);
            return mInstance[0];
        }
    }

    public static UiccController make(Context c, CommandsInterface ci, int simId) {
        synchronized (mLock) {
            if (FeatureOption.MTK_GEMINI_SUPPORT) {
                if(mInstance[simId] != null) {
                    throw new RuntimeException("UiccController.make() should only be called once");
                }
                mInstance[simId] = new UiccController(c, ci, simId);
                return mInstance[simId];
            } else {
                if (mInstance[0] != null) {
                    throw new RuntimeException("UiccController.make() should only be called once");
                }
                mInstance[0] = new UiccController(c, ci);
                return mInstance[0];
            }
        }
    }
        
    public static UiccController getInstance() {
        synchronized (mLock) {
            if (mInstance[0] == null) {
                throw new RuntimeException(
                        "UiccController.getInstance can't be called before make()");
            }
            return mInstance[0];
        }
    }

    public static UiccController getInstance(int simId) {
        synchronized (mLock) {
            if (FeatureOption.MTK_GEMINI_SUPPORT) {
                if(mInstance[simId] == null) {
                    throw new RuntimeException(
                        "UiccController.getInstance can't be called before make()");
                }
                return mInstance[simId];
            } else {
                if (mInstance[0] == null) {
                    throw new RuntimeException(
                        "UiccController.getInstance can't be called before make()");
                }
                return mInstance[0];
            }
        }
    }
        
    public UiccCard getUiccCard() {
        synchronized (mLock) {
            return mUiccCard;
        }
    }

    // Easy to use API
    public UiccCardApplication getUiccCardApplication(int family) {
        synchronized (mLock) {
            if (mUiccCard != null) {
                return mUiccCard.getApplication(family);
            }
            return null;
        }
    }

    // Easy to use API
    public IccRecords getIccRecords(int family) {
        synchronized (mLock) {
            if (mUiccCard != null) {
                UiccCardApplication app = mUiccCard.getApplication(family);
                if (app != null) {
                    return app.getIccRecords();
                }
            }
            return null;
        }
    }

    // Easy to use API
    public IccFileHandler getIccFileHandler(int family) {
        synchronized (mLock) {
            if (mUiccCard != null) {
                UiccCardApplication app = mUiccCard.getApplication(family);
                if (app != null) {
                    return app.getIccFileHandler();
                }
            }
            return null;
        }
    }

    //Notifies when card status changes
    public void registerForIccChanged(Handler h, int what, Object obj) {
        synchronized (mLock) {
            Registrant r = new Registrant (h, what, obj);
            mIccChangedRegistrants.add(r);
            //Notify registrant right after registering, so that it will get the latest ICC status,
            //otherwise which may not happen until there is an actual change in ICC status.
            r.notifyRegistrant();
        }
    }

    public void unregisterForIccChanged(Handler h) {
        synchronized (mLock) {
            mIccChangedRegistrants.remove(h);
        }
    }

    //Notifies when card status changes
    public void registerForIccRecovery(Handler h, int what, Object obj) {
        synchronized (mLock) {
            Registrant r = new Registrant (h, what, obj);
            mRecoveryRegistrants.add(r);
            //Notify registrant right after registering, so that it will get the latest ICC status,
            //otherwise which may not happen until there is an actual change in ICC status.
            r.notifyRegistrant();
        }
    }

    public void unregisterForIccRecovery(Handler h) {
        synchronized (mLock) {
            mRecoveryRegistrants.remove(h);
        }
    }

    @Override
    public void handleMessage (Message msg) {
        synchronized (mLock) {
            switch (msg.what) {
                case EVENT_ICC_STATUS_CHANGED:
                    if (DBG) log("Received EVENT_ICC_STATUS_CHANGED, calling getIccCardStatus");
                    mCi.getIccCardStatus(obtainMessage(EVENT_GET_ICC_STATUS_DONE));
                    break;
                case EVENT_GET_ICC_STATUS_DONE:
                    if (DBG) log("Received EVENT_GET_ICC_STATUS_DONE");
                    AsyncResult ar = (AsyncResult)msg.obj;
                    onGetIccCardStatusDone(ar,false);
                    break;
                case EVENT_RADIO_AVAILABLE: 
                    if (DBG) log("Received EVENT_RADIO_AVAILABLE");
                    if (!FeatureOption.MTK_3GDONGLE_SUPPORT) {                        
                        getSimMissingStatus();
                        //mCi.queryIccId(obtainMessage(EVENT_QUERY_ICCID_DONE_FOR_HOT_SWAP));                    
                    }
                    break;
                case EVENT_QUERY_SIM_MISSING_STATUS:
                    ar = (AsyncResult)msg.obj;
                    if (ar.exception == null) {
                        int[] result = (int[]) ar.result;
                        if (result != null && result[0] == 0) {
                            log("EVENT_QUERY_SIM_MISSING_STATUS, execute notifySimMissing");
                            mCi.notifySimMissing();
                            mCi.getIccCardStatus(obtainMessage(EVENT_GET_ICC_STATUS_DONE));
                            if (!FeatureOption.MTK_GEMINI_SUPPORT) {
                                String enCryState = SystemProperties.get("vold.decrypt");
                                log("Ready to update SIM info because SIM not found, enCryState: " + ((enCryState != null)? enCryState : "null"));
                                if(enCryState == null || "".equals(enCryState) || "trigger_restart_framework".equals(enCryState)) {
                                    Phone phone = PhoneFactory.getDefaultPhone();
                                    DefaultSIMSettings.onAllIccidQueryComplete(mContext, phone, null, null, null, null, false);
                                }
                            }
                        }
                        else if(result != null && result[0] == 14) {
                            log("EVENT_QUERY_SIM_MISSING_STATUS, SIM busy and execute again");
                            getSimMissingStatus();
                        }
                        else {
                            if(result == null) {
                                log("EVENT_QUERY_SIM_MISSING_STATUS, card is null");
                            }
                            else { // result[0] == 1
                                log("EVENT_QUERY_SIM_MISSING_STATUS, card is present");
                                disableSimMissingNotification();
                                //ALPS00971614: We should send AT+ICCID after detect SIM Missing status
                                mCi.queryIccId(obtainMessage(EVENT_QUERY_ICCID_DONE_FOR_HOT_SWAP));
                            }
                        }
                    }
                    else {
                        log("EVENT_QUERY_SIM_MISSING_STATUS, exception");
                    }
                    break;
                case EVENT_VIRTUAL_SIM_ON:
                    if (FeatureOption.MTK_GEMINI_SUPPORT) 
                    {
                        if (DBG) log("handleMessage (EVENT_VIRTUAL_SIM_ON),MTK_GEMINI_SUPPORT on");
                        int simId = getMySimId();
                        int dualSimMode = Settings.System.getInt(mContext.getContentResolver(), Settings.System.DUAL_SIM_MODE_SETTING, 0);
                        mCi.setRadioMode((dualSimMode & (simId+1)), null);
                    }
                    else 
                    {
                        if (DBG) log("handleMessage (EVENT_VIRTUAL_SIM_ON),MTK_GEMINI_SUPPORT off");
                                          mCi.setRadioPower(true,null);
                    }
                    mCi.getIccCardStatus(obtainMessage(EVENT_GET_ICC_STATUS_DONE));
                    setNotificationVirtual(EVENT_VIRTUAL_SIM_ON);
                    SharedPreferences shOn = mContext.getSharedPreferences("AutoAnswer", 1);
                    SharedPreferences.Editor editorOn = shOn.edit();
                    editorOn.putBoolean("flag", true);
                    editorOn.commit();
                break;
               case EVENT_VIRTUAL_SIM_OFF:
                          if (DBG) log("handleMessage (EVENT_VIRTUAL_SIM_OFF)");
                    mCi.getIccCardStatus(obtainMessage(EVENT_GET_ICC_STATUS_DONE));
                    removeNotificationVirtual(EVENT_VIRTUAL_SIM_ON);
                    setNotification(EVENT_SIM_MISSING);
                    SharedPreferences shOff = mContext.getSharedPreferences("AutoAnswer", 1);
                    SharedPreferences.Editor editorOff = shOff.edit();
                    editorOff.putBoolean("flag", false);
                    editorOff.commit();
                break;
                case EVENT_SIM_RECOVERY:
                    if (DBG) log("handleMessage (EVENT_SIM_RECOVERY)");
                    mCi.getIccCardStatus(obtainMessage(EVENT_GET_ICC_STATUS_DONE_FOR_SIM_RECOVERY));
                    mRecoveryRegistrants.notifyRegistrants();
                    disableSimMissingNotification();

                    //ALPS01209124
                    Intent intent = new Intent();
                    intent.setAction(TelephonyIntents.ACTION_SIM_RECOVERY_DONE);
                    mContext.sendBroadcast(intent);
                    break;
                case EVENT_SIM_MISSING: 
                    if (DBG) log("handleMessage (EVENT_SIM_MISSING)");
                    setNotification(EVENT_SIM_MISSING);
                    mCi.getIccCardStatus(obtainMessage(EVENT_GET_ICC_STATUS_DONE_FOR_SIM_MISSING));
                    break;
                case EVENT_SIM_PLUG_OUT:
                    if (DBG) log("handleMessage (EVENT_SIM_PLUG_OUT)");
                    setNotification(EVENT_SIM_MISSING);
                    mCi.getIccCardStatus(obtainMessage(EVENT_HOTSWAP_GET_ICC_STATUS_DONE));
                    break;
                case EVENT_SIM_PLUG_IN:
                    if (DBG) log("handleMessage (EVENT_SIM_PLUG_IN)");
                    disableSimMissingNotification();
                    mCi.detectSimMissing(obtainMessage(EVENT_QUERY_SIM_STATUS_FOR_PLUG_IN));
                    break;
                case EVENT_QUERY_SIM_MISSING:
                    mCi.detectSimMissing(obtainMessage(EVENT_QUERY_SIM_STATUS_FOR_PLUG_IN));
                    break;
                case EVENT_QUERY_SIM_STATUS_FOR_PLUG_IN:
                    ar = (AsyncResult)msg.obj;
                    if (ar.exception == null) {
                        int[] result = (int[]) ar.result;
                        if (result == null) {
                            if (DBG) log("should not happen this one, ril_sim will always return success when query sim missing");
                        } else {
                            Phone defaultPhone = PhoneFactory.getDefaultPhone();
                            switch (result[0]) {
                                case 0:
                                    if (DBG) log("EVENT_QUERY_SIM_STATUS_FOR_PLUG_IN, card is null");
                                    sendEmptyMessage(EVENT_SIM_MISSING);
                                    //If card not present, we also need inform Gemini Phone.
                                    if(PhoneFactory.isSupportCommonSlot() == true) {
                                        ((GeminiPhone)defaultPhone).plugInNoSimSlot(getMySimId());
                                    }
                                    break;
                                case 14:
                                    if (DBG) log("SIM busy, retry query missing status 100ms later");
                                    sendEmptyMessageDelayed(EVENT_QUERY_SIM_MISSING, 100);
                                    break;
                                default:
                                    if (FeatureOption.MTK_GEMINI_SUPPORT) {
                                        mIsHotSwap = true;
                                        ((GeminiPhone)defaultPhone).onSimHotSwap(getMySimId(), true);
                                    } else {
                                        int airplaneMode = Settings.System.getInt(
                                            mContext.getContentResolver(),
                                            Settings.System.AIRPLANE_MODE_ON, 0);
                                    
                                        int dualSimMode = Settings.System.getInt(
                                                mContext.getContentResolver(),
                                                Settings.System.DUAL_SIM_MODE_SETTING,
                                                Settings.System.DUAL_SIM_MODE_SETTING_DEFAULT);
                                    
                                        log("airplaneMode:" + airplaneMode + " dualSimMode:" + dualSimMode);
                                        if((airplaneMode == 0) &&
                                            ((dualSimMode & (1 << getMySimId())) > 0 )) {
                                            mCi.getIccCardStatus(obtainMessage(EVENT_HOTSWAP_GET_ICC_STATUS_DONE));
                                        }  
                                    
                                        mCi.queryIccId( obtainMessage(EVENT_QUERY_ICCID_DONE_FOR_HOT_SWAP));
                                    }
                                    break;
                            }
                        }
                    } else {
                        if (DBG) log("EVENT_QUERY_SIM_STATUS_FOR_PLUG_IN, exception");
                    }
                    break;
                case EVENT_QUERY_ICCID_DONE_FOR_HOT_SWAP:
                    if (DBG) log("handleMessage (EVENT_QUERY_ICCID_DONE_FOR_HOT_SWAP)");
                    String iccid = null;
                    ar = (AsyncResult)msg.obj; 
    
                    if (ar.exception == null && (ar.result != null) && !( ((String)ar.result).equals("") )) {
                        iccid = (String)ar.result;
                        if (DBG) log("IccId = " + iccid);
                    } else { 
                        if (DBG) log("iccid error");
                    }
    
                    if (!FeatureOption.MTK_GEMINI_SUPPORT) {
                        String enCryState = SystemProperties.get("vold.decrypt");
                        if(enCryState == null || "".equals(enCryState) || "trigger_restart_framework".equals(enCryState)) {
                            Phone phone = PhoneFactory.getDefaultPhone();
                            DefaultSIMSettings.onAllIccidQueryComplete(mContext, phone, iccid, null, null, null, false);
                        }
                    }
                    break;
                case EVENT_HOTSWAP_GET_ICC_STATUS_DONE:
                    if (DBG) log("Received EVENT_HOTSWAP_GET_ICC_STATUS_DONE");
                    ar = (AsyncResult)msg.obj;
                    onGetIccCardStatusDone(ar,true);
                    break;
                case EVENT_GET_ICC_STATUS_DONE_FOR_SIM_MISSING:
                    if (DBG) log("Received EVENT_GET_ICC_STATUS_DONE_FOR_SIM_MISSING");
                    ar = (AsyncResult)msg.obj;
                    onGetIccCardStatusDone(ar, false);
                case EVENT_GET_ICC_STATUS_DONE_FOR_SIM_RECOVERY:
                    if (DBG) log("Received EVENT_GET_ICC_STATUS_DONE_FOR_SIM_RECOVERY");
                    ar = (AsyncResult)msg.obj;
                    onGetIccCardStatusDone(ar, false);
                    break;
                case EVENT_INVALID_SIM_DETECTED:
                    if (DBG) log("handleMessage (EVENT_INVALID_SIM_DETECTED)");
                    setNotification(EVENT_SIM_MISSING);
                    mCi.getIccCardStatus(obtainMessage(EVENT_HOTSWAP_GET_ICC_STATUS_DONE));
                    break;
                case EVENT_REPOLL_SML_STATE:
                    if (DBG) log("Received EVENT_REPOLL_SML_STATE");
                    ar = (AsyncResult)msg.obj;
                    boolean needIntent = msg.arg1 == SML_FEATURE_NEED_BROADCAST_INTENT ? true : false;

                    //Update Uicc Card status.
                    onGetIccCardStatusDone(ar,false);

                    // If we still in Network lock, broadcast intent if caller need this intent.
                    if (mUiccCard != null && needIntent == true) {
                        UiccCardApplication app = mUiccCard.getApplication(APP_FAM_3GPP);
                        if (app == null) {
                            if (DBG) log("UiccCardApplication = null");
                            break;
                        }
                        if(app.getState() == AppState.APPSTATE_SUBSCRIPTION_PERSO) {
                            Intent it = new Intent();
                            if (null == it) {
                                Rlog.e(LOG_TAG, "New intent failed");
                                return;
                            }
                            if (DBG) log("Broadcast ACTION_UNLOCK_SIM_LOCK");
                            it.setAction(TelephonyIntents.ACTION_UNLOCK_SIM_LOCK);
                            it.putExtra(TelephonyIntents.EXTRA_SIM_SLOT, mSimId);
                            it.putExtra(TelephonyIntents.EXTRA_UNLOCK_TYPE, 
                                    TelephonyIntents.VERIFY_TYPE_SIMMELOCK);
                            it.putExtra(TelephonyIntents.EXTRA_SIMME_LOCK_TYPE, 
                                    parsePersoType(app.getPersoSubState()));
                            mContext.sendBroadcast(it);
                        }
                    }                    
                    break;
                default:
                    Rlog.e(LOG_TAG, " Unknown Event " + msg.what);
            }
        }
    }

    private UiccController(Context c, CommandsInterface ci) {
        if (DBG) log("Creating UiccController");
        mContext = c;
        mCi = ci;
        mSimId = PhoneConstants.GEMINI_SIM_1;
        mCi.registerForIccStatusChanged(this, EVENT_ICC_STATUS_CHANGED, null);
        // TODO remove this once modem correctly notifies the unsols
        mCi.registerForOn(this, EVENT_ICC_STATUS_CHANGED, null);

        if(!FeatureOption.MTK_GEMINI_SUPPORT) {
            // GEMINI phone has its own initial flow by receive sim inserted status
            mCi.registerForAvailable(this, EVENT_RADIO_AVAILABLE, null);
        }
        mCi.registerForVirtualSimOn(this, EVENT_VIRTUAL_SIM_ON, null);
        mCi.registerForVirtualSimOff(this, EVENT_VIRTUAL_SIM_OFF, null);
        mCi.registerForSimMissing(this, EVENT_SIM_MISSING, null);
        mCi.registerForSimRecovery(this, EVENT_SIM_RECOVERY, null);
        mCi.registerForSimPlugOut(this, EVENT_SIM_PLUG_OUT, null);
        mCi.registerForSimPlugIn(this, EVENT_SIM_PLUG_IN, null);
        mCi.registerForInvalidSimDetected(this, EVENT_INVALID_SIM_DETECTED, null);

        IntentFilter filter = new IntentFilter();
        filter.addAction("android.intent.action.ACTION_SHUTDOWN_IPO");
        filter.addAction(GeminiPhone.EVENT_INITIALIZATION_FRAMEWORK_DONE);
        filter.addAction(TelephonyIntents.ACTION_SIM_INFO_UPDATE);
        filter.addAction(ACTION_RESET_MODEM);
        mContext.registerReceiver(mIntentReceiver, filter);
    }

    private UiccController(Context c, CommandsInterface ci, int simId) {
        if (DBG) log("Creating UiccController simId " + simId);
        mContext = c;
        mCi = ci;
        mSimId = simId;
        mCi.registerForIccStatusChanged(this, EVENT_ICC_STATUS_CHANGED, null);
        // TODO remove this once modem correctly notifies the unsols
        mCi.registerForOn(this, EVENT_ICC_STATUS_CHANGED, null);
        mCi.registerForVirtualSimOn(this, EVENT_VIRTUAL_SIM_ON, null);
        mCi.registerForVirtualSimOff(this, EVENT_VIRTUAL_SIM_OFF, null);
        mCi.registerForSimMissing(this, EVENT_SIM_MISSING, null);
        mCi.registerForSimRecovery(this, EVENT_SIM_RECOVERY, null);
        mCi.registerForSimPlugOut(this, EVENT_SIM_PLUG_OUT, null);
        mCi.registerForSimPlugIn(this, EVENT_SIM_PLUG_IN, null);
        mCi.registerForInvalidSimDetected(this, EVENT_INVALID_SIM_DETECTED, null);

        IntentFilter filter = new IntentFilter();
        filter.addAction("android.intent.action.ACTION_SHUTDOWN_IPO");
        filter.addAction(GeminiPhone.EVENT_INITIALIZATION_FRAMEWORK_DONE);
        filter.addAction(TelephonyIntents.ACTION_SIM_INFO_UPDATE);
        filter.addAction(ACTION_RESET_MODEM);
        mContext.registerReceiver(mIntentReceiver, filter);
    }
    
    private synchronized void onGetIccCardStatusDone(AsyncResult ar) {
        if (ar.exception != null) {
            Rlog.e(LOG_TAG,"[SIM " + mSimId + "] Error getting ICC status. "
                    + "RIL_REQUEST_GET_ICC_STATUS should "
                    + "never return an error", ar.exception);
            return;
        }

        IccCardStatus status = (IccCardStatus)ar.result;

        if (status.mCardState == IccCardStatus.CardState.CARDSTATE_PRESENT) {
            if (DBG) log("onGetIccCardStatusDone, disableSimMissingNotification because card is present");
            disableSimMissingNotification();
        }

        if (mUiccCard == null) {
            //Create new card
            mUiccCard = new UiccCard(mContext, mCi, status, mSimId);
        } else {
            //Update already existing card
            mUiccCard.update(mContext, mCi , status);
        }

        if (DBG) log("Notifying IccChangedRegistrants");
        mIccChangedRegistrants.notifyRegistrants();
    }

    private synchronized void onGetIccCardStatusDone(AsyncResult ar, boolean isUpdateSiminfo) {
        if (ar.exception != null) {
            Rlog.e(LOG_TAG,"[SIM " + mSimId + "] Error getting ICC status. "
                    + "RIL_REQUEST_GET_ICC_STATUS should "
                    + "never return an error", ar.exception);
            return;
        }

        IccCardStatus status = (IccCardStatus)ar.result;

        if (status.mCardState == IccCardStatus.CardState.CARDSTATE_PRESENT) {
            if (DBG) log("onGetIccCardStatusDone, disableSimMissingNotification because card is present");
            disableSimMissingNotification();
        }

        if (mUiccCard == null) {
            //Create new card
            mUiccCard = new UiccCard(mContext, mCi, status, mSimId);
        } else {
            //Update already existing card
            mUiccCard.update(mContext, mCi , status, isUpdateSiminfo);
        }

        if (DBG) log("Notifying IccChangedRegistrants, isUpdateSiminfo:" + isUpdateSiminfo);
        mIccChangedRegistrants.notifyRegistrants();
    }

    private void log(String string) {
        //Log.d(LOG_TAG, string);
        Rlog.d(LOG_TAG, "[UiccController][SIM" + mSimId + "] " + string);
    }

    private void setNotificationVirtual(int notifyType){
        if(DBG) log("setNotification(): notifyType = "+notifyType);
        Notification notification = new Notification();
        notification.when = System.currentTimeMillis();
        notification.flags = Notification.FLAG_AUTO_CANCEL;
        notification.icon = com.android.internal.R.drawable.stat_sys_warning;
        Intent intent = new Intent();
        notification.contentIntent = PendingIntent.getActivity(mContext, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
        String title = null;
        if (FeatureOption.MTK_GEMINI_SUPPORT == true) {
            title = Resources.getSystem().getText(UICCCONTROLLER_STRING_NOTIFICATION_VIRTUAL_SIM_ON[getMySimId()]).toString();
        }else{
            title = Resources.getSystem().getText(com.mediatek.internal.R.string.virtual_sim_on).toString();
        }
        CharSequence detail = mContext.getText(com.mediatek.internal.R.string.virtual_sim_on).toString();
        notification.tickerText = mContext.getText(com.mediatek.internal.R.string.virtual_sim_on).toString();

        notification.setLatestEventInfo(mContext, title, detail,notification.contentIntent);
        NotificationManager notificationManager = (NotificationManager)mContext.getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.notify(notifyType + getMySimId(), notification);
    }

    private void removeNotificationVirtual(int notifyType) {
        NotificationManager notificationManager = (NotificationManager)mContext.getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.cancel(notifyType + getMySimId());
    }

    //Modem SML change feature.
    public void repollIccStateForModemSmlChangeFeatrue(boolean needIntent) {
        if (DBG) log("repollIccStateForModemSmlChangeFeatrue, needIntent = " + needIntent);
        int arg1 = needIntent == true ? SML_FEATURE_NEED_BROADCAST_INTENT : SML_FEATURE_NO_NEED_BROADCAST_INTENT;
        mCi.getIccCardStatus(obtainMessage(EVENT_REPOLL_SML_STATE, arg1, 0));
    }

    public int parsePersoType(PersoSubState state) {
        if (DBG) log("parsePersoType, state = " + state);
        switch (state){
            case PERSOSUBSTATE_SIM_NETWORK:
                return 0;
            case PERSOSUBSTATE_SIM_NETWORK_SUBSET:
                return 1;
            case PERSOSUBSTATE_SIM_SERVICE_PROVIDER:
                return 2;
            case PERSOSUBSTATE_SIM_CORPORATE:
                return 3;
            case PERSOSUBSTATE_SIM_SIM:
                return 4;
            default:
                break;
        }
        return -1;
    }

    private void setNotification(int notifyType){
        if(DBG) log("setNotification(): notifyType = "+notifyType);
        Notification notification = new Notification();
        notification.when = System.currentTimeMillis();
        notification.flags = Notification.FLAG_AUTO_CANCEL;
        notification.icon = com.android.internal.R.drawable.stat_sys_warning;
        Intent intent = new Intent();
        notification.contentIntent = PendingIntent.getActivity(mContext, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);

        String title = mUiccControllerExt.getMissingTitle(mContext, getMySimId());
        CharSequence detail = mUiccControllerExt.getMissingDetail(mContext);
        
        notification.tickerText = title;
        notification.setLatestEventInfo(mContext, title, detail,notification.contentIntent);
        NotificationManager notificationManager = (NotificationManager)mContext.getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.notify(notifyType + getMySimId(), notification);
    }

    // ALPS00294581
    public void disableSimMissingNotification() {
        NotificationManager notificationManager = (NotificationManager)mContext.getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.cancel(EVENT_SIM_MISSING + getMySimId());
    }

    // ALPS00294581
    private void getSimMissingStatus() { // Single Card
        mCi.detectSimMissing(obtainMessage(EVENT_QUERY_SIM_MISSING_STATUS));
    }

    private final BroadcastReceiver mIntentReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            if (intent == null) return;
            String action = intent.getAction();
            log("mIntentReceiver Receive action " + action);

            if(action.equals("android.intent.action.ACTION_SHUTDOWN_IPO")) {
                log("mIntentReceiver ACTION_SHUTDOWN_IPO");
                disableSimMissingNotification();
            } else if (TelephonyIntents.ACTION_SIM_INFO_UPDATE.equals(action)) {
                //ALPS00776430: Since EF_MSISDN can not be read/wrtie without verify PIN.
                //We need to clear it or update it to avoid user to get the cached data before.
                new Thread() {
                    @Override
                    public void run() {
                        SIMInfo simInfo = SIMInfo.getSIMInfoBySlot(mContext, mSimId);
                        if (simInfo!= null && mClearMsisdn == false) {
                            mClearMsisdn = true;
                            log("Initial sim info.");
                            IccRecords iccRecord = getIccRecords(APP_FAM_3GPP);
                            if(iccRecord != null) {
                                SIMInfo.setNumber(mContext, iccRecord.getMsisdnNumber(), simInfo.mSimId);
                            } else {
                                SIMInfo.setNumber(mContext, "", simInfo.mSimId);
                            }
                            Intent intent = new Intent(TelephonyIntents.ACTION_SIM_INFO_UPDATE);
                            ActivityManagerNative.broadcastStickyIntent(intent, READ_PHONE_STATE, UserHandle.USER_ALL);
                        }
                    }
                }.start();
            } else if (GeminiPhone.EVENT_INITIALIZATION_FRAMEWORK_DONE.equals(action)) {
                if(mIsHotSwap) {
                    mIsHotSwap = false;
                    int airplaneMode = Settings.System.getInt(
                        mContext.getContentResolver(),
                        Settings.System.AIRPLANE_MODE_ON, 0);

                    int dualSimMode = Settings.System.getInt(
                            mContext.getContentResolver(),
                            Settings.System.DUAL_SIM_MODE_SETTING,
                            Settings.System.DUAL_SIM_MODE_SETTING_DEFAULT);

                    log("mIntentReceiver EVENT_INITIALIZATION_FRAMEWORK_DONE airplaneMode:" + airplaneMode + " dualSimMode:" + dualSimMode);
                    if((airplaneMode == 0) &&
                        ((dualSimMode & (1 << getMySimId())) > 0 )) {
                        mCi.getIccCardStatus(obtainMessage(EVENT_HOTSWAP_GET_ICC_STATUS_DONE));
                    }
                }
            } else if(action.equals(ACTION_RESET_MODEM)) {
                int SimIdFor3G = SystemProperties.getInt(PROPERTY_3G_SWITCH, 1)-1;
                if(SimIdFor3G == mSimId) {
                    log("phone " + SimIdFor3G + " will reset modem");
                    mCi.resetRadio(null);
                }
            }
        }
    };

    public int getMySimId() {
        return mSimId;
    }

    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        pw.println("UiccController: " + this);
        pw.println(" mContext=" + mContext);
        pw.println(" mInstance=" + mInstance);
        pw.println(" mCi=" + mCi);
        pw.println(" mUiccCard=" + mUiccCard);
        pw.println(" mIccChangedRegistrants: size=" + mIccChangedRegistrants.size());
        for (int i = 0; i < mIccChangedRegistrants.size(); i++) {
            pw.println("  mIccChangedRegistrants[" + i + "]="
                    + ((Registrant)mIccChangedRegistrants.get(i)).getHandler());
        }
        pw.println();
        pw.flush();
        if (mUiccCard != null) {
            mUiccCard.dump(fd, pw, args);
        }
    }
}
