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
 * Copyright (C) 2007 The Android Open Source Project
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

package com.android.internal.telephony.cat;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.res.Resources.NotFoundException;
import android.os.AsyncResult;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.os.SystemProperties;
import android.provider.Settings;
import android.provider.Settings.SettingNotFoundException;
import android.util.Config;

import com.android.internal.telephony.CommandsInterface;
import com.android.internal.telephony.uicc.IccFileHandler;
import com.android.internal.telephony.uicc.IccRecords;
import com.android.internal.telephony.uicc.IccUtils;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.CallManager;
import com.android.internal.telephony.TelephonyIntents;
import com.android.internal.telephony.uicc.UiccCard;
import com.android.internal.telephony.uicc.UiccCardApplication;
import com.android.internal.telephony.IccCardConstants;
import com.android.internal.telephony.gemini.GeminiPhone;

import java.io.ByteArrayOutputStream;
import java.util.Calendar;
import java.util.Locale;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedList;

import android.database.ContentObserver;
import android.net.Uri;
import android.provider.Settings.System;

//MTK-START [mtk80589][121026][ALPS00376525] STK dialog pop up caused ISVR
import com.android.internal.telephony.TelephonyIntents;
//MTK-END [mtk80589][121026][ALPS00376525] STK dialog pop up caused ISVR
import com.android.internal.telephony.CommandsInterface.RadioState;

import android.telephony.TelephonyManager;
import com.mediatek.common.featureoption.FeatureOption;
import com.mediatek.common.telephony.ITelephonyEx;
import android.os.ServiceManager;
import com.android.internal.telephony.ITelephony;

import com.android.internal.telephony.cat.bip.ErrorValue;
import com.android.internal.telephony.cat.bip.ChannelStatus;
import com.android.internal.telephony.cat.bip.OtherAddress;
import com.android.internal.telephony.cat.bip.BearerDesc;
import com.android.internal.telephony.cat.bip.TransportProtocol;
import com.android.internal.telephony.cat.bip.BipManager;
import com.android.internal.telephony.PhoneFactory;

class RilMessage {
    int mId;
    Object mData;
    ResultCode mResCode;
    boolean mSetUpMenuFromMD;
    
    RilMessage(int msgId, String rawData) {
        mId = msgId;
        mData = rawData;
        mSetUpMenuFromMD = false;
    }

    RilMessage(RilMessage other) {
        mId = other.mId;
        mData = other.mData;
        mResCode = other.mResCode;
        mSetUpMenuFromMD = other.mSetUpMenuFromMD;
    }

    void setSetUpMenuFromMD(boolean flag)
    {
        mSetUpMenuFromMD = flag;
    }
}
class EventDownloadCallInfo {
    int mState;
    int mTi;
    int mIsMTCall;
    int mIsFarEnd;
    int mCauseLen;
    int mCause;

    EventDownloadCallInfo (int state, int ti, int isMTCall, int isFarEnd, int cause_len, int cause) {
        mState = state;
        mTi = ti;
        mIsMTCall = isMTCall;
        mIsFarEnd = isFarEnd;
        mCauseLen = cause_len;
        mCause = cause;
    }
}
/**
 * Class that implements SIM Toolkit Telephony Service. Interacts with the RIL
 * and application. {@hide}
 */
public class CatService extends Handler implements AppInterface {
    private static final boolean DBG = false;

    // Class members
    private static IccRecords mIccRecords;
    private static UiccCardApplication mUiccApplication;

    // Service members.
    // Protects singleton instance lazy initialization.
    private static final Object sInstanceLock = new Object();
    // private static CatService sInstance;
    private CommandsInterface mCmdIf;
    private Context mContext;
    private CatCmdMessage mCurrentCmd = null;
    private CatCmdMessage mMenuCmd = null;

    private RilMessageDecoder mMsgDecoder = null;

    // Service constants.
    static final int MSG_ID_SESSION_END = 1;
    static final int MSG_ID_PROACTIVE_COMMAND = 2;
    static final int MSG_ID_EVENT_NOTIFY = 3;
    static final int MSG_ID_CALL_SETUP = 4;
    static final int MSG_ID_REFRESH = 5;
    static final int MSG_ID_RESPONSE = 6;
    static final int MSG_ID_SIM_READY = 7;
    static final int MSG_ID_EVENT_DOWNLOAD = 8;
    static final int MSG_ID_RIL_MSG_DECODED = 10;
    static final int MSG_ID_DB_HANDLER = 11;

    //MTK-START [mtk80589][121026][ALPS00376525] STK dialog pop up caused ISVR
    private static final int MSG_ID_IVSR_DELAYED = 14;
    //MTK-END [mtk80589][121026][ALPS00376525] STK dialog pop up caused ISVR
    private static final int MSG_ID_DISABLE_DISPLAY_TEXT_DELAYED = 15;
    
    // Events to signal SIM presence or absent in the device.
    private static final int MSG_ID_ICC_RECORDS_LOADED = 20;
    private static final int MSG_ID_EVDL_CALL = 21;
    private static final int MSG_ID_MODEM_EVDL_CALL_CONN_TIMEOUT = 22;
    private static final int MSG_ID_MODEM_EVDL_CALL_DISCONN_TIMEOUT = 23;

    private static final int DEV_ID_KEYPAD = 0x01;
    private static final int DEV_ID_DISPLAY = 0x02;
    private static final int DEV_ID_EARPIECE = 0x03;
    private static final int DEV_ID_UICC = 0x81;
    private static final int DEV_ID_TERMINAL = 0x82;
    private static final int DEV_ID_NETWORK = 0x83;
    static final String STK_DEFAULT = "Default Message";

    // Add by Huibin Mao MTK80229
    // ICS Migration start
    private static CatService sInstanceSim1; // mtk02374 GEMINI
    private static CatService sInstanceSim2;
    //MTK-START [mtk80950][121110][ALPS00XXXXXX] add for Gemini+ the 3rd card and the 4th card
    private static CatService sInstanceSim3;
    private static CatService sInstanceSim4;
    //MTK-END [mtk80950][121110][ALPS00XXXXXX] add for Gemini+ the 3rd card and the 4th card
    private static String sInst1 = "sInstanceSim1";
    private static String sInst2 = "sInstanceSim2";
    //MTK-START [mtk80950][121110][ALPS00XXXXXX] add for Gemini+ the 3rd card and the 4th card
    private static String sInst3 = "sInstanceSim3";
    private static String sInst4 = "sInstanceSim4";
    //MTK-END [mtk80950][121110][ALPS00XXXXXX] add for Gemini+ the 3rd card and the 4th card
    protected static Object mLock = new Object();
    private boolean default_send_setupmenu_tr = true;
    public boolean mGotSetUpMenu = false;
    public boolean mSaveNewSetUpMenu = false;
    private boolean mSetUpMenuFromMD = false;
    private boolean mReadFromPreferenceDone = false;
    private int MODEM_EVDL_TIMEOUT = 2 * 1000;
    private LinkedList<Integer> mEvdlCallConnObjQ = new LinkedList<Integer>();
    private LinkedList<Integer> mEvdlCallDisConnObjQ = new LinkedList<Integer>();
    private int mEvdlCallObj =0;

    private Phone mPhone;
    private byte[] mEventList;
    private int mSimId = -1;
    
    public static final int MSG_ID_OPEN_CHANNEL_DONE = 30;
    public static final int MSG_ID_SEND_DATA_DONE = 31;
    public static final int MSG_ID_RECEIVE_DATA_DONE = 32;
    public static final int MSG_ID_CLOSE_CHANNEL_DONE = 33;
    public static final int MSG_ID_GET_CHANNEL_STATUS_DONE = 34;
    public static final int MSG_ID_CONN_MGR_TIMEOUT = 35;
    public static final int MSG_ID_CACHED_DISPLAY_TEXT_TIMEOUT = 36;
    public static final int MSG_ID_CONN_RETRY_TIMEOUT = 37;

    // Event List Elements
    static final int EVENT_LIST_ELEMENT_MT_CALL = 0x00;
    static final int EVENT_LIST_ELEMENT_CALL_CONNECTED = 0x01;
    static final int EVENT_LIST_ELEMENT_CALL_DISCONNECTED = 0x02;
    static final int EVENT_LIST_ELEMENT_LOCATION_STATUS = 0x03;
    static final int EVENT_LIST_ELEMENT_USER_ACTIVITY = 0x04;
    static final int EVENT_LIST_ELEMENT_IDLE_SCREEN_AVAILABLE = 0x05;
    static final int EVENT_LIST_ELEMENT_CARD_READER_STATUS = 0x06;
    static final int EVENT_LIST_ELEMENT_LANGUAGE_SELECTION = 0x07;
    static final int EVENT_LIST_ELEMENT_BROWSER_TERMINATION = 0x08;
    public static final int EVENT_LIST_ELEMENT_DATA_AVAILABLE = 0x09;
    public static final int EVENT_LIST_ELEMENT_CHANNEL_STATUS = 0x0A;

    static final int ADDITIONAL_INFO_FOR_BIP_NO_SPECIFIC_CAUSE = 0x00;
    static final int ADDITIONAL_INFO_FOR_BIP_NO_CHANNEL_AVAILABLE = 0x01;
    static final int ADDITIONAL_INFO_FOR_BIP_CHANNEL_CLOSED = 0x02;
    static final int ADDITIONAL_INFO_FOR_BIP_CHANNEL_ID_NOT_AVAILABLE = 0x03;
    static final int ADDITIONAL_INFO_FOR_BIP_REQUESTED_BUFFER_SIZE_NOT_AVAILABLE = 0x04;
    static final int ADDITIONAL_INFO_FOR_BIP_SECURITY_ERROR = 0x05;
    static final int ADDITIONAL_INFO_FOR_BIP_REQUESTED_INTERFACE_TRANSPORT_LEVEL_NOT_AVAILABLE = 0x06;

    final static String IDLE_SCREEN_INTENT_NAME = "android.intent.action.IDLE_SCREEN_NEEDED";
    final static String IDLE_SCREEN_ENABLE_KEY = "_enable";
    final static String USER_ACTIVITY_INTENT_NAME = "android.intent.action.stk.USER_ACTIVITY.enable";
    final static String USER_ACTIVITY_ENABLE_KEY = "state";
    static final String ACTION_SHUTDOWN_IPO = "android.intent.action.ACTION_SHUTDOWN_IPO";
    static final String ACTION_PREBOOT_IPO = "android.intent.action.ACTION_PREBOOT_IPO";
    
    public static final String ACTION_CAT_INIT_DONE = "android.intent.action.ACTION_CAT_INIT_DONE";

    /**
     * SIM ID for GEMINI
     */
    public static final int GEMINI_SIM_1 = 0;
    public static final int GEMINI_SIM_2 = 1;
    //MTK-START [mtk80950][121110][ALPS00XXXXXX] add for Gemini+ the 3rd card and the 4th card
    public static final int GEMINI_SIM_3 = 2;
    public static final int GEMINI_SIM_4 = 3;
    //MTK-END [mtk80950][121110][ALPS00XXXXXX] add for Gemini+ the 3rd card and the 4th card
    
    BipManager mBipMgr = null;
    // ICS Migration end
    // [20120420,mtk80601,ALPS264008]
    private int ResultCodeFlag = -1;
    private String simState = null;
    private int simIdfromIntent = 0;
    
    private CatCmdMessage mCachedDisplayTextCmd = null;
    private boolean mHasCachedDTCmd = true;
    
    //MTK-START [mtk80589][121026][ALPS00376525] STK dialog pop up caused ISVR
    private boolean isIvsrBootUp = false;
    private final int IVSR_DELAYED_TIME = 60 * 1000;
    //MTK-END [mtk80589][121026][ALPS00376525] STK dialog pop up caused ISVR
    private boolean isDisplayTextDisabled = false;
    private final int DISABLE_DISPLAY_TEXT_DELAYED_TIME = 30 * 1000;

    boolean mNeedRegisterAgain = false;
    private static final int STK_EVDL_CALL_STATE_CALLCONN = 0;
    private static final int STK_EVDL_CALL_STATE_CALLDISCONN = 1;
    private LinkedList<EventDownloadCallInfo> mEventDownloadCallDisConnInfo = new LinkedList();
    private LinkedList<EventDownloadCallInfo> mEventDownloadCallConnInfo = new LinkedList();
    private int mNumEventDownloadCallDisConn = 0;
    private int mNumEventDownloadCallConn = 0;
    private boolean mIsAllCallDisConn = false;

    /* Only to cache DISPLAY_TEXT at most 120 sec */
    private int CACHED_DISPLAY_TIMEOUT = 120*1000;
    
    Handler mTimeoutHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            if (false == FeatureOption.MTK_BSP_PACKAGE) {
                switch(msg.what) {
                    case MSG_ID_CACHED_DISPLAY_TEXT_TIMEOUT:
                        CatLog.d(this, "Cache DISPLAY_TEXT time out, sim_id: " + mSimId);
                        clearCachedDisplayText(mSimId);
                        break;
                    case MSG_ID_MODEM_EVDL_CALL_CONN_TIMEOUT:
                        CatLog.d(this, "modem MODEM_EVDL_CALL_CONN_TIMEOUT timout");                                                                                    
                        if(0 < mNumEventDownloadCallConn)
                            mNumEventDownloadCallConn--;
                        break;                        
                    case MSG_ID_MODEM_EVDL_CALL_DISCONN_TIMEOUT:
                        CatLog.d(this, "modem MODEM_EVDL_CALL_DISCONN_TIMEOUT timout");                                                                                    
                        if(0 < mNumEventDownloadCallDisConn)
                            mNumEventDownloadCallDisConn--;
                        break;                        
                }
            }
            if (msg.what == MSG_ID_DISABLE_DISPLAY_TEXT_DELAYED) {
                CatLog.d(this, "[Reset Disable Display Text flag because timeout");
                isDisplayTextDisabled = false;
            }
        }
    };

    void cancelTimeOut(int msg) {
        CatLog.d(this, "cancelTimeOut, sim_id: " + mSimId + ", msg id: " + msg);
        mTimeoutHandler.removeMessages(msg);
    }

    void startTimeOut(int msg, long delay) {
        CatLog.d(this, "startTimeOut, sim_id: " + mSimId + ", msg id: " + msg);
        cancelTimeOut(msg);
        mTimeoutHandler.sendMessageDelayed(mTimeoutHandler.obtainMessage(msg), delay);
    }
    
    private final BroadcastReceiver mStkIdleScreenAvailableReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String evtAction = intent.getAction();
            int evdl = 0x04;

            CatLog.d("CatService", "mStkIdleScreenAvailableReceiver() - evtAction[" + evtAction + "]");

            if (evtAction.equals("android.intent.action.stk.IDLE_SCREEN_AVAILABLE")) {
                CatLog.d("CatService", "mStkIdleScreenAvailableReceiver() - Received[IDLE_SCREEN_AVAILABLE]");
                evdl = 0x05;
            } else {
                CatLog.d("CatService", "mStkIdleScreenAvailableReceiver() - Received needn't handle!");
                return;
            }
            CatResponseMessage resMsg = new CatResponseMessage();
            resMsg.setEventId(evdl);
            resMsg.setSourceId(0x02);
            resMsg.setDestinationId(0x81);
            resMsg.setAdditionalInfo(null);
            resMsg.setOneShot(true);
            CatLog.d("CatService", "handle Idle Screen Available");
            CatService.this.onEventDownload(resMsg);
        }
    }; 

    private void clearCachedDisplayText(int sim_id) {
        if (false == FeatureOption.MTK_BSP_PACKAGE) {
            CatLog.d("CatService", "clearCachedDisplayText, sim_id: " + sim_id + ", mSimId: " + mSimId + ", mCachedDisplayTextCmd: " + ((mCachedDisplayTextCmd != null)? 1 : 0));
            if (sim_id == mSimId) {
                if (mCachedDisplayTextCmd != null) {    
                    CatResponseMessage resMsg = new CatResponseMessage(mCachedDisplayTextCmd);
                    resMsg.setResultCode(ResultCode.UICC_SESSION_TERM_BY_USER);
                    handleCmdResponse(resMsg);
                    mCachedDisplayTextCmd = null;
                        
                    // unregister the ContentObserver object, because
                    // we just need to cache the first DISPLAY_TEXT
                    unregisterPowerOnSequenceObserver();
                } else {
                    // unregister the ContentObserver object, because
                    // we just need to cache the first DISPLAY_TEXT
                    if(mHasCachedDTCmd) {
                        unregisterPowerOnSequenceObserver();
                        resetPowerOnSequenceFlag();
                    }
                }
            }
        }
    }

    private final BroadcastReceiver mClearDisplayTextReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (false == FeatureOption.MTK_BSP_PACKAGE) {
                if (AppInterface.CLEAR_DISPLAY_TEXT_CMD.equals(intent.getAction())) {
                    int sim_id = intent.getIntExtra("SIM_ID", -1);
                    CatLog.d("CatService", "mClearDisplayTextReceiver, sim_id: " + sim_id);
                    clearCachedDisplayText(sim_id);
                }
            }
        }
    };

    /* Intentionally private for singleton */
    private CatService(Phone phone,CommandsInterface ci, UiccCardApplication ca, IccRecords ir, Context context,
            IccFileHandler fh, UiccCard ic, int simId) {
        if (ci == null || ca == null || ir == null || context == null || fh == null
                || ic == null) {
            throw new NullPointerException(
                    "Service: Input parameters must not be null");
        }
        mCmdIf = ci;
        mContext = context;
        mSimId = simId;
        mPhone = phone;

        CatLog.d(this, "simId " + simId);

        // Get the RilMessagesDecoder for decoding the messages.
        mMsgDecoder = RilMessageDecoder.getInstance(this, fh, simId);

        // Register ril events handling.
        mCmdIf.setOnCatSessionEnd(this, MSG_ID_SESSION_END, null);
        mCmdIf.setOnCatProactiveCmd(this, MSG_ID_PROACTIVE_COMMAND, null);
        mCmdIf.setOnCatEvent(this, MSG_ID_EVENT_NOTIFY, null);
        mCmdIf.setOnCatCallSetUp(this, MSG_ID_CALL_SETUP, null);
        // mCmdIf.setOnSimRefresh(this, MSG_ID_REFRESH, null);
        if (false == FeatureOption.MTK_BSP_PACKAGE) {
            mCmdIf.setOnStkEvdlCall(this, MSG_ID_EVDL_CALL, null);
        }
        mIccRecords = ir;
        mUiccApplication = ca;

        // Register for SIM ready event.
        mCmdIf.registerForSIMReady(this, MSG_ID_SIM_READY, null);
        mCmdIf.registerForRUIMReady(this, MSG_ID_SIM_READY, null);
        mCmdIf.registerForNVReady(this, MSG_ID_SIM_READY, null);
        mIccRecords.registerForRecordsLoaded(this, MSG_ID_ICC_RECORDS_LOADED, null);

        // Add by Huibin Mao MTK80229
        // ICS Migration start
        CatLog.d(this, "Get BipManager");
        mBipMgr = BipManager.getInstance(context, this, simId);
        if (mBipMgr == null) {
            CatLog.d(this, "BipManager is null!!");
        }
        IntentFilter intentFilter = new IntentFilter(ACTION_SHUTDOWN_IPO);
        //MTK-START [mtk80589][121026][ALPS00376525] STK dialog pop up caused ISVR
        intentFilter.addAction(TelephonyIntents.ACTION_IVSR_NOTIFY);
        //MTK-END [mtk80589][121026][ALPS00376525] STK dialog pop up caused ISVR
        intentFilter.addAction(TelephonyIntents.ACTION_SIM_RECOVERY_DONE);
        intentFilter.addAction(TelephonyIntents.ACTION_MD_TYPE_CHANGE);
        IntentFilter mSIMStateChangeFilter = new IntentFilter(
                TelephonyIntents.ACTION_SIM_STATE_CHANGED);
        mSIMStateChangeFilter.addAction(TelephonyIntents.ACTION_RADIO_TECHNOLOGY_CHANGED);
        mContext.registerReceiver(CatServiceReceiver, intentFilter);
        mContext.registerReceiver(CatServiceReceiver, mSIMStateChangeFilter);
        IntentFilter mIdleScreenAvailableFilter = new IntentFilter("android.intent.action.stk.IDLE_SCREEN_AVAILABLE");
        mContext.registerReceiver(mStkIdleScreenAvailableReceiver, mIdleScreenAvailableFilter);
        CatLog.d(this, "CatService: is running");
        // ICS Migration end
        
        // notify RILJ send cached STK proactive command
        CatLog.d(this, "[CachedStk notify RIL to send cached command");
        Intent intentForCachedCommand = new Intent(ACTION_CAT_INIT_DONE);
        intentForCachedCommand.putExtra(PhoneConstants.GEMINI_SIM_ID_KEY, mSimId);
        mContext.sendBroadcast(intentForCachedCommand);
        if (false == FeatureOption.MTK_BSP_PACKAGE) {
            // register a ContentObserver to listen PoS flag
            registerPowerOnSequenceObserver();
    
            /* Register to clear cached display text command */
            IntentFilter ClearDisplayTextFilter = new IntentFilter(AppInterface.CLEAR_DISPLAY_TEXT_CMD);
            mContext.registerReceiver(mClearDisplayTextReceiver, ClearDisplayTextFilter);
        }
    }

    public void dispose() {
        CatLog.d(this, "CatService: dispose");
        mIccRecords.unregisterForRecordsLoaded(this);
        mCmdIf.unSetOnCatSessionEnd(this);
        mCmdIf.unSetOnCatProactiveCmd(this);
        mCmdIf.unSetOnCatEvent(this);
        mCmdIf.unSetOnCatCallSetUp(this);
        if (false == FeatureOption.MTK_BSP_PACKAGE) {
            mCmdIf.unSetOnStkEvdlCall(this);
        }
        removeCallbacksAndMessages(null);

        mNeedRegisterAgain = true;
    }

    @Override
    protected void finalize() {
        CatLog.d(this, "Service finalized");
    }

    private void handleRilMsg(RilMessage rilMsg) {
        if (rilMsg == null) {
            return;
        }

        // dispatch messages
        CommandParams cmdParams = null;
        switch (rilMsg.mId) {
            case MSG_ID_EVENT_NOTIFY:
                cmdParams = (CommandParams) rilMsg.mData;
                if (cmdParams != null) {
                    if (rilMsg.mResCode == ResultCode.OK) {
                        handleCommand(cmdParams, false);
                    } else {
                        // Add by Huibin Mao MTK80229
                        // ICS Migration start
                        CatLog.d(this, "event notify error code: " + rilMsg.mResCode);
                        if (rilMsg.mResCode == ResultCode.PRFRMD_ICON_NOT_DISPLAYED && (
                            cmdParams.mCmdDet.typeOfCommand == 0x11  //send SS
                            || cmdParams.mCmdDet.typeOfCommand == 0x12  //send USSD
                            || cmdParams.mCmdDet.typeOfCommand == 0x13  // send SMS
                            || cmdParams.mCmdDet.typeOfCommand == 0x14  //send DTMF
                            )) {
                            CatLog.d("[BIP]", "notify user text message even though get icon fail");
                            handleCommand(cmdParams, false);
                        }
                        if (cmdParams.mCmdDet.typeOfCommand == 0x40) {
                            CatLog.d("[BIP]", "Open Channel with ResultCode");
                            handleCommand(cmdParams, false);
                        }
                        // ICS Migration end
                    }
                    // ICS Migration end
                }
                break;
            case MSG_ID_PROACTIVE_COMMAND:
                try {
                    cmdParams = (CommandParams) rilMsg.mData;
                } catch (ClassCastException e) {
                    // for error handling : cast exception
                    CatLog.d(this, "Fail to parse proactive command");
                    // Don't send Terminal Resp if command detail is not available
                    if (mCurrentCmd != null) {
                        sendTerminalResponse(mCurrentCmd.mCmdDet, ResultCode.CMD_DATA_NOT_UNDERSTOOD,
                                false, 0x00, null);
                    }
                    break;
                }
                if (cmdParams != null) {
                    if (rilMsg.mResCode == ResultCode.OK) {
                        ResultCodeFlag = 0;
                        mSetUpMenuFromMD = rilMsg.mSetUpMenuFromMD;
                        handleCommand(cmdParams, true);
                    } else if (rilMsg.mResCode == ResultCode.PRFRMD_ICON_NOT_DISPLAYED) {
                        ResultCodeFlag = 4;
                        mSetUpMenuFromMD = rilMsg.mSetUpMenuFromMD;
                        handleCommand(cmdParams, true);
                    } else {
                        // for proactive commands that couldn't be decoded
                        // successfully respond with the code generated by the
                        // message decoder.
                        CatLog.d("CAT", "SS-handleMessage: invalid proactive command: "
                                + cmdParams.mCmdDet.typeOfCommand);
                        sendTerminalResponse(cmdParams.mCmdDet, rilMsg.mResCode,
                                false, 0, null);
                    }
                }
                break;
            case MSG_ID_REFRESH:
                cmdParams = (CommandParams) rilMsg.mData;
                if (cmdParams != null) {
                    handleCommand(cmdParams, false);
                }
                break;
            case MSG_ID_SESSION_END:
                handleSessionEnd();
                break;
            case MSG_ID_CALL_SETUP:
                // prior event notify command supplied all the information
                // needed for set up call processing.
                break;
        }
    }

    /**
     * Handles RIL_UNSOL_STK_EVENT_NOTIFY or RIL_UNSOL_STK_PROACTIVE_COMMAND command
     * from RIL.
     * Sends valid proactive command data to the application using intents.
     * RIL_REQUEST_STK_SEND_TERMINAL_RESPONSE will be send back if the command is
     * from RIL_UNSOL_STK_PROACTIVE_COMMAND.
     */
    private void handleCommand(CommandParams cmdParams, boolean isProactiveCmd) {
        CatLog.d(this, cmdParams.getCommandType().name());

        CharSequence message;
        CatCmdMessage cmdMsg = new CatCmdMessage(cmdParams);

        // Add by Huibin Mao MTK80229
        // ICS Migration start
        Message response = null;
        // ICS Migration end

        // add for [ALPS00245360] should not show DISPLAY_TEXT dialog when alarm
        // booting
        boolean isAlarmState = false;
        boolean isFlightMode = false;
        int flightMode = 0;

        switch (cmdParams.getCommandType()) {
            case SET_UP_MENU:
                if (removeMenu(cmdMsg.getMenu())) {
                    mMenuCmd = null;
                } else {
                    mMenuCmd = cmdMsg;
                }

                /*
                if(! default_send_setupmenu_tr) {
                    default_send_setupmenu_tr = true;
                    break;
                }
                */
                CatLog.d("CAT", "mSetUpMenuFromMD: " + mSetUpMenuFromMD);
                if (cmdMsg.getMenu() != null) {
                    cmdMsg.getMenu().setSetUpMenuFlag(((mSetUpMenuFromMD == true)? 1 : 0));
                }
                if (!mSetUpMenuFromMD)
                {
                    break;
                }
                mSetUpMenuFromMD = false;
                if (ResultCodeFlag == 0) {
                    sendTerminalResponse(cmdParams.mCmdDet, ResultCode.OK, false, 0, null);
                } else if (ResultCodeFlag == 4) {
                    sendTerminalResponse(cmdParams.mCmdDet, ResultCode.PRFRMD_ICON_NOT_DISPLAYED,
                            false, 0, null);
                } else {
                }
                break;
            case DISPLAY_TEXT:
                if (false == FeatureOption.MTK_BSP_PACKAGE) {
                    if(mHasCachedDTCmd) {
                        int OOBE_Value = System.getInt(mContext.getContentResolver(), System.OOBE_DISPLAY, System.OOBE_DISPLAY_DEFAULT);
                        CatLog.d(this, "handle DISPLAY_TEXT, OOBE_Value: " + OOBE_Value);
                        if (OOBE_Value == System.OOBE_DISPLAY_DEFAULT)
                        {
                            CatLog.d(this, "[CacheDT cache DISPLAY_TEXT");
                            // we modify the flag only if it is 0
                            int seqValue = System.getInt(
                                mContext.getContentResolver(),
                                System.DIALOG_SEQUENCE_SETTINGS,
                                System.DIALOG_SEQUENCE_DEFAULT);
                            CatLog.d(this, "seqValue in CatService, " + seqValue);
    
                            /* workaround */
                            if (seqValue != System.DIALOG_SEQUENCE_STK)
                            {
                                mCachedDisplayTextCmd = cmdMsg;
                                
                                if (seqValue == System.DIALOG_SEQUENCE_DEFAULT) {
                                    // try to set flag to DIALOG_SEQUENCE_STK
                                    System.putInt(
                                        mContext.getContentResolver(),
                                        System.DIALOG_SEQUENCE_SETTINGS,
                                        System.DIALOG_SEQUENCE_STK);
                                }
                                
                                CatLog.d(this, "[CacheDT set current cmd as DISPLAY_TEXT");
                                mCurrentCmd = cmdMsg;
                                startTimeOut(MSG_ID_CACHED_DISPLAY_TEXT_TIMEOUT, CACHED_DISPLAY_TIMEOUT);
                                return;
                            }
                        }
                        else
                        {
                            /* Can not do DISPLAY_TEXT because OOBE is activate */
                            mCachedDisplayTextCmd = cmdMsg;
                            mCurrentCmd = cmdMsg;
                            startTimeOut(MSG_ID_CACHED_DISPLAY_TEXT_TIMEOUT, CACHED_DISPLAY_TIMEOUT);
                            return;
                        }
                    }
                }
                // when application is not required to respond, send an
                // immediate response.
                /*
                 * if (!cmdMsg.geTextMessage().responseNeeded) {
                 * sendTerminalResponse(cmdParams.mCmdDet, ResultCode.OK, false,
                 * 0, null); }
                 */
                // add for [ALPS00245360] should not show DISPLAY_TEXT dialog
                // when alarm booting
                isAlarmState = isAlarmBoot();
                try {
                    flightMode = Settings.Global.getInt(mContext.getContentResolver(),
                            Settings.Global.AIRPLANE_MODE_ON);
                } catch (SettingNotFoundException e) {
                    CatLog.d(this, "fail to get property from Settings");
                    flightMode = 0;
                }
                isFlightMode = (flightMode != 0);
                CatLog.d(this, "isAlarmState = " + isAlarmState + ", isFlightMode = "
                        + isFlightMode + ", flightMode = " + flightMode);

                if (isAlarmState && isFlightMode) {
                    sendTerminalResponse(cmdParams.mCmdDet, ResultCode.OK, false, 0, null);
                    return;
                }

                // add for SetupWizard
                if (checkSetupWizardInstalled() == true) {
                    sendTerminalResponse(cmdParams.mCmdDet, ResultCode.BACKWARD_MOVE_BY_USER, false,
                            0, null);
                    return;
                }
            
                //MTK-START [mtk80589][121026][ALPS00376525] STK dialog pop up caused ISVR
                if(isIvsrBootUp) {
                    CatLog.d(this, "[IVSR send TR directly");
                    sendTerminalResponse(cmdParams.mCmdDet, ResultCode.BACKWARD_MOVE_BY_USER, false, 0, null);
                    return;
                }
                //MTK-END [mtk80589][121026][ALPS00376525] STK dialog pop up caused ISVR
                if(isDisplayTextDisabled) {
                    CatLog.d(this, "[Sim Recovery send TR directly");
                    sendTerminalResponse(cmdParams.mCmdDet, ResultCode.BACKWARD_MOVE_BY_USER, false, 0, null);
                    return;
                }
            
                break;
            case REFRESH:
                // ME side only handles refresh commands which meant to remove
                // IDLE
                // MODE TEXT.
                cmdParams.mCmdDet.typeOfCommand = CommandType.SET_UP_IDLE_MODE_TEXT.value();
                CatLog.d(this, "remove event list because of SIM Refresh");
                mEventList = null;
                break;
            case SET_UP_IDLE_MODE_TEXT:
                // sendTerminalResponse(cmdParams.mCmdDet, ResultCode.OK, false,
                // 0, null);

                if (((DisplayTextParams) cmdParams).mTextMsg.icon != null
                        && ((DisplayTextParams) cmdParams).mTextMsg.iconSelfExplanatory == false
                        && ((DisplayTextParams) cmdParams).mTextMsg.text == null) {
                    sendTerminalResponse(cmdParams.mCmdDet, ResultCode.CMD_DATA_NOT_UNDERSTOOD,
                            false,
                            0, null);
                    return;
                } else {
                    sendTerminalResponse(cmdParams.mCmdDet, ResultCode.OK, false,
                            0, null);
                }
                break;
            case PROVIDE_LOCAL_INFORMATION:

                ResponseData resp = null;

                if (cmdParams.mCmdDet.commandQualifier == 0x03) {

                    Calendar cal = Calendar.getInstance();
                    int temp = 0;
                    int hibyte = 0;
                    int lobyte = 0;
                    byte[] datetime = new byte[7];

                    temp = cal.get(Calendar.YEAR) - 2000;
                    hibyte = temp / 10;
                    lobyte = (temp % 10) << 4;
                    datetime[0] = (byte) (lobyte | hibyte);

                    temp = cal.get(Calendar.MONTH) + 1;
                    hibyte = temp / 10;
                    lobyte = (temp % 10) << 4;
                    datetime[1] = (byte) (lobyte | hibyte);

                    temp = cal.get(Calendar.DATE);
                    hibyte = temp / 10;
                    lobyte = (temp % 10) << 4;
                    datetime[2] = (byte) (lobyte | hibyte);

                    temp = cal.get(Calendar.HOUR_OF_DAY);
                    hibyte = temp / 10;
                    lobyte = (temp % 10) << 4;
                    datetime[3] = (byte) (lobyte | hibyte);

                    temp = cal.get(Calendar.MINUTE);
                    hibyte = temp / 10;
                    lobyte = (temp % 10) << 4;
                    datetime[4] = (byte) (lobyte | hibyte);

                    temp = cal.get(Calendar.SECOND);
                    hibyte = temp / 10;
                    lobyte = (temp % 10) << 4;
                    datetime[5] = (byte) (lobyte | hibyte);

                    // the ZONE_OFFSET is expressed in quarters of an hour
                    temp = cal.get(Calendar.ZONE_OFFSET) / (15 * 60 * 1000);
                    hibyte = temp / 10;
                    lobyte = (temp % 10) << 4;
                    datetime[6] = (byte) (lobyte | hibyte);

                    resp = new ProvideLocalInformationResponseData(datetime[0],
                            datetime[1], datetime[2], datetime[3], datetime[4], datetime[5],
                            datetime[6]);

                    sendTerminalResponse(cmdParams.mCmdDet, ResultCode.OK, false,
                            0, resp);

                    return;
                } else if (cmdParams.mCmdDet.commandQualifier == 0x04) {

                    byte[] lang = new byte[2];
                    Locale locale = Locale.getDefault();

                    lang[0] = (byte) locale.getLanguage().charAt(0);
                    lang[1] = (byte) locale.getLanguage().charAt(1);

                    resp = new ProvideLocalInformationResponseData(lang);

                    sendTerminalResponse(cmdParams.mCmdDet, ResultCode.OK, false,
                            0, resp);

                    return;
                }
                return;

                /*
                 * case PROVIDE_LOCAL_INFORMATION: ResponseData resp; switch
                 * (cmdParams.mCmdDet.commandQualifier) { case
                 * CommandParamsFactory.DTTZ_SETTING: resp = new
                 * DTTZResponseData(null);
                 * sendTerminalResponse(cmdParams.mCmdDet, ResultCode.OK, false,
                 * 0, resp); break; case CommandParamsFactory.LANGUAGE_SETTING:
                 * resp = new
                 * LanguageResponseData(Locale.getDefault().getLanguage());
                 * sendTerminalResponse(cmdParams.mCmdDet, ResultCode.OK, false,
                 * 0, resp); break; default:
                 * sendTerminalResponse(cmdParams.mCmdDet, ResultCode.OK, false,
                 * 0, null); return; }
                 */
            case LAUNCH_BROWSER:
                if ((((LaunchBrowserParams) cmdParams).mConfirmMsg.text != null)
                        && (((LaunchBrowserParams) cmdParams).mConfirmMsg.text.equals(STK_DEFAULT))) {
                    message = mContext.getText(com.android.internal.R.string.launchBrowserDefault);
                    ((LaunchBrowserParams) cmdParams).mConfirmMsg.text = message.toString();
                }
                break;
            case SELECT_ITEM:
                // add for [ALPS00245360] should not show DISPLAY_TEXT dialog
                // when alarm booting
                isAlarmState = isAlarmBoot();
                try {
                    flightMode = Settings.Global.getInt(mContext.getContentResolver(),
                            Settings.Global.AIRPLANE_MODE_ON);
                } catch (SettingNotFoundException e) {
                    CatLog.d(this, "fail to get property from Settings");
                    flightMode = 0;
                }
                isFlightMode = (flightMode != 0);
                CatLog.d(this, "isAlarmState = " + isAlarmState + ", isFlightMode = "
                        + isFlightMode + ", flightMode = " + flightMode);
                if (isAlarmState && isFlightMode) {
                    sendTerminalResponse(cmdParams.mCmdDet, ResultCode.UICC_SESSION_TERM_BY_USER,
                            false, 0, null);
                    return;
                }
                break;
            case GET_INPUT:
            case GET_INKEY:
                if (!(simState == null || simState.length() == 0
                        || IccCardConstants.INTENT_VALUE_ICC_READY.equals(simState)
                        || IccCardConstants.INTENT_VALUE_ICC_IMSI.equals(simState) 
                        || IccCardConstants.INTENT_VALUE_ICC_LOADED.equals(simState))) {
                    sendTerminalResponse(cmdParams.mCmdDet, ResultCode.TERMINAL_CRNTLY_UNABLE_TO_PROCESS, false, 0, null);
                    return;
                }
                break;
            case SEND_DTMF:
            case SEND_SMS:
            case SEND_SS:
            case SEND_USSD:
                if ((((DisplayTextParams) cmdParams).mTextMsg.text != null)
                        && (((DisplayTextParams) cmdParams).mTextMsg.text.equals(STK_DEFAULT))) {
                    message = mContext.getText(com.android.internal.R.string.sending);
                    ((DisplayTextParams) cmdParams).mTextMsg.text = message.toString();
                }
                break;
            case PLAY_TONE:
                break;
            case SET_UP_CALL:
                if ((((CallSetupParams) cmdParams).mConfirmMsg.text != null)
                        && (((CallSetupParams) cmdParams).mConfirmMsg.text.equals(STK_DEFAULT))) {
                    message = mContext.getText(com.android.internal.R.string.SetupCallDefault);
                    ((CallSetupParams) cmdParams).mConfirmMsg.text = message.toString();
                }
                break;
            // Add by Huibin Mao MTK80229
            // ICS Migration start
            case SET_UP_EVENT_LIST:
                mEventList = ((SetupEventListParams) cmdParams).eventList;
                return;
            case OPEN_CHANNEL:
                CatLog.d(this, "SS-handleProactiveCommand: process OPEN_CHANNEL");
                PhoneConstants.State call_state = PhoneConstants.State.IDLE;
                CallManager callmgr = CallManager.getInstance();
                if(mPhone == null) {
                    CatLog.d(this, "SS-handleProactiveCommand: mphone is null.");
                    Phone myPhone = PhoneFactory.getDefaultPhone();
                    if (FeatureOption.MTK_GEMINI_SUPPORT == true) {
                        if(myPhone != null) {
                            mPhone = ((GeminiPhone)myPhone).getPhonebyId(mSimId);
                        } else {
                            CatLog.d("CatService", "myPhone is still null");
                            break;
                        }
                    }
                }
                
                if(mPhone.getServiceState().getNetworkType() <= TelephonyManager.NETWORK_TYPE_EDGE && null != callmgr)
                {               
                    call_state = callmgr.getState();
                
                    if (call_state != PhoneConstants.State.IDLE) {
                        CatLog.d("[BIP]", "SS-handleProactiveCommand: ME is busy on call");
                        cmdMsg.mChannelStatusData = new ChannelStatus(mBipMgr.getFreeChannelId(),
                                ChannelStatus.CHANNEL_STATUS_NO_LINK,
                                ChannelStatus.CHANNEL_STATUS_INFO_NO_FURTHER_INFO);
                        cmdMsg.mChannelStatusData.mChannelStatus = ChannelStatus.CHANNEL_STATUS_NO_LINK;
                        mCurrentCmd = cmdMsg;
                        response = obtainMessage(MSG_ID_OPEN_CHANNEL_DONE,
                                ErrorValue.ME_IS_BUSY_ON_CALL, 0, cmdMsg);
                        response.sendToTarget();
                        return;
                    }
                } else {
                    CatLog.d("[BIP]", "SS-handleProactiveCommand: type:"+mPhone.getServiceState().getNetworkType()+",or null callmgr");
                }
                break;
            case CLOSE_CHANNEL:
                CatLog.d(this, "SS-handleProactiveCommand: process CLOSE_CHANNEL");
                response = obtainMessage(MSG_ID_CLOSE_CHANNEL_DONE);
                mCmdMessage = cmdMsg;
                mBipMgr.closeChannel(response);
                break;
            case RECEIVE_DATA:
                CatLog.d(this, "SS-handleProactiveCommand: process RECEIVE_DATA");
                mCmdMessage = cmdMsg;
                response = obtainMessage(MSG_ID_RECEIVE_DATA_DONE);
                mBipMgr.receiveData(response);
                break;
            case SEND_DATA:
                CatLog.d(this, "SS-handleProactiveCommand: process SEND_DATA");
                mCmdMessage = cmdMsg;
                response = obtainMessage(MSG_ID_SEND_DATA_DONE);
                mBipMgr.sendData(response);
                break;
            case GET_CHANNEL_STATUS:
                CatLog.d(this, "SS-handleProactiveCommand: process GET_CHANNEL_STATUS");
                mCmdMessage = cmdMsg;
                response = obtainMessage(MSG_ID_GET_CHANNEL_STATUS_DONE);
                mBipMgr.getChannelStatus(response);
                break;
            // ICS Migration end
            default:
                CatLog.d(this, "SS-handleProactiveCommand: Unsupported command");
                return;
        }
        mCurrentCmd = cmdMsg;
        Intent intent = null;

        //MTK-START [mtk80950][121110][ALPS00XXXXXX] add for Gemini+ the 3rd card and the 4th card
        if (mSimId == GEMINI_SIM_1) {
            intent = new Intent(AppInterface.CAT_CMD_ACTION);
            CatLog.d(this, "SS-handleProactiveCommand: sending CAT_CMD_ACTION");
        } else if(mSimId == GEMINI_SIM_2) {
            intent = new Intent(AppInterface.CAT_CMD_ACTION_2);
            CatLog.d(this, "SS-handleProactiveCommand: sending CAT_CMD_ACTION_2");
        } else if (mSimId == GEMINI_SIM_3) {
            intent = new Intent(AppInterface.CAT_CMD_ACTION_3);
            CatLog.d(this, "SS-handleProactiveCommand: sending CAT_CMD_ACTION_3");
        } else {//mSimId == GEMINI_SIM_4
            intent = new Intent(AppInterface.CAT_CMD_ACTION_4);
            CatLog.d(this, "SS-handleProactiveCommand: sending CAT_CMD_ACTION_4");
        }
        //MTK-END [mtk80950][121110][ALPS00XXXXXX] add for Gemini+ the 3rd card and the 4th card
        intent.putExtra("STK CMD", cmdMsg);
        mContext.sendBroadcast(intent);
    }

    /**
     * Handles RIL_UNSOL_STK_SESSION_END unsolicited command from RIL.
     */
    private void handleSessionEnd() {
        CatLog.d(this, "SS-handleSessionEnd: SESSION END, sim id: " + mSimId);
        Intent intent = null;

        mCurrentCmd = mMenuCmd;
        
        //MTK-START [mtk80950][121110][ALPS00XXXXXX] add for Gemini+ the 3rd card and the 4th card
        if (mSimId == GEMINI_SIM_1) {
            intent = new Intent(AppInterface.CAT_SESSION_END_ACTION);
        } else if(mSimId == GEMINI_SIM_2) {
            intent = new Intent(AppInterface.CAT_SESSION_END_ACTION_2);
        } else if(mSimId == GEMINI_SIM_3) {
            intent = new Intent(AppInterface.CAT_SESSION_END_ACTION_3);
        } else {//mSimId == GEMINI_SIM_4
            intent = new Intent(AppInterface.CAT_SESSION_END_ACTION_4);
        }
        //MTK-END [mtk80950][121110][ALPS00XXXXXX] add for Gemini+ the 3rd card and the 4th card
        mContext.sendBroadcast(intent);
    }

    private void sendTerminalResponse(CommandDetails cmdDet,
            ResultCode resultCode, boolean includeAdditionalInfo,
            int additionalInfo, ResponseData resp) {

        if (cmdDet == null) {
            CatLog.d(this, "SS-sendTR: cmdDet is null");
            return;
        }
        CatLog.d(this, "SS-sendTR: command type is " + cmdDet.typeOfCommand);
        ByteArrayOutputStream buf = new ByteArrayOutputStream();

        Input cmdInput = null;
        if (mCurrentCmd != null) {
            cmdInput = mCurrentCmd.geInput();
        }

        // command details
        int tag = ComprehensionTlvTag.COMMAND_DETAILS.value();
        if (cmdDet.compRequired) {
            tag |= 0x80;
        }
        buf.write(tag);
        buf.write(0x03); // length
        buf.write(cmdDet.commandNumber);
        buf.write(cmdDet.typeOfCommand);
        buf.write(cmdDet.commandQualifier);

        // device identities
        // According to TS102.223/TS31.111 section 6.8 Structure of
        // TERMINAL RESPONSE, "For all SIMPLE-TLV objects with Min=N,
        // the ME should set the CR(comprehension required) flag to
        // comprehension not required.(CR=0)"
        // Since DEVICE_IDENTITIES and DURATION TLVs have Min=N,
        // the CR flag is not set.
        tag = 0x80 | ComprehensionTlvTag.DEVICE_IDENTITIES.value();
        buf.write(tag);
        buf.write(0x02); // length
        buf.write(DEV_ID_TERMINAL); // source device id
        buf.write(DEV_ID_UICC); // destination device id

        // result
        tag = 0x80 | ComprehensionTlvTag.RESULT.value();
        buf.write(tag);
        int length = includeAdditionalInfo ? 2 : 1;
        buf.write(length);
        buf.write(resultCode.value());

        // additional info
        if (includeAdditionalInfo) {
            buf.write(additionalInfo);
        }

        // Fill optional data for each corresponding command
        if (resp != null) {
            CatLog.d(this, "SS-sendTR: write response data into TR");
            resp.format(buf);
        } else {
            encodeOptionalTags(cmdDet, resultCode, cmdInput, buf);
        }

        byte[] rawData = buf.toByteArray();
        String hexString = IccUtils.bytesToHexString(rawData);
        if (Config.LOGD) {
            CatLog.d(this, "TERMINAL RESPONSE: " + hexString);
        }

        mCmdIf.sendTerminalResponse(hexString, null);
    }

    private void encodeOptionalTags(CommandDetails cmdDet,
            ResultCode resultCode, Input cmdInput, ByteArrayOutputStream buf) {
        CommandType cmdType = AppInterface.CommandType.fromInt(cmdDet.typeOfCommand);
        if (cmdType != null) {
            switch (cmdType) {
                case GET_INKEY:
                    // ETSI TS 102 384,27.22.4.2.8.4.2.
                    // If it is a response for GET_INKEY command and the
                    // response timeout
                    // occured, then add DURATION TLV for variable timeout case.
                    if ((resultCode.value() == ResultCode.NO_RESPONSE_FROM_USER.value()) &&
                            (cmdInput != null) && (cmdInput.duration != null)) {
                        getInKeyResponse(buf, cmdInput);
                    }
                    break;
                case PROVIDE_LOCAL_INFORMATION:
                    if ((cmdDet.commandQualifier == CommandParamsFactory.LANGUAGE_SETTING) &&
                            (resultCode.value() == ResultCode.OK.value())) {
                        getPliResponse(buf);
                    }
                    break;
                default:
                    CatLog.d(this, "encodeOptionalTags() Unsupported Cmd:" + cmdDet.typeOfCommand);
                    break;
            }
        } else {
            CatLog.d(this, "encodeOptionalTags() bad Cmd:" + cmdDet.typeOfCommand);
        }
    }

    private void getInKeyResponse(ByteArrayOutputStream buf, Input cmdInput) {
        int tag = ComprehensionTlvTag.DURATION.value();

        buf.write(tag);
        buf.write(0x02); // length
        buf.write(cmdInput.duration.timeUnit.SECOND.value()); // Time
        // (Unit,Seconds)
        buf.write(cmdInput.duration.timeInterval); // Time Duration
    }

    private void getPliResponse(ByteArrayOutputStream buf) {

        // Locale Language Setting
        String lang = SystemProperties.get("persist.sys.language");

        if (lang != null) {
            // tag
            int tag = ComprehensionTlvTag.LANGUAGE.value();
            buf.write(tag);
            ResponseData.writeLength(buf, lang.length());
            buf.write(lang.getBytes(), 0, lang.length());
        }
    }

    private void sendMenuSelection(int menuId, boolean helpRequired) {

        CatLog.d("CatService", "sendMenuSelection SET_UP_MENU");

        ByteArrayOutputStream buf = new ByteArrayOutputStream();

        // tag
        int tag = BerTlv.BER_MENU_SELECTION_TAG;
        buf.write(tag);

        // length
        buf.write(0x00); // place holder

        // device identities
        tag = 0x80 | ComprehensionTlvTag.DEVICE_IDENTITIES.value();
        buf.write(tag);
        buf.write(0x02); // length
        buf.write(DEV_ID_KEYPAD); // source device id
        buf.write(DEV_ID_UICC); // destination device id

        // item identifier
        tag = 0x80 | ComprehensionTlvTag.ITEM_ID.value();
        buf.write(tag);
        buf.write(0x01); // length
        buf.write(menuId); // menu identifier chosen

        // help request
        if (helpRequired) {
            tag = ComprehensionTlvTag.HELP_REQUEST.value();
            buf.write(tag);
            buf.write(0x00); // length
        }

        byte[] rawData = buf.toByteArray();

        // write real length
        int len = rawData.length - 2; // minus (tag + length)
        rawData[1] = (byte) len;

        String hexString = IccUtils.bytesToHexString(rawData);

        CatLog.d("CatService", "sendMenuSelection before  mCmdIf.sendEnvelope(hexString, null);");
        mCmdIf.sendEnvelope(hexString, null);
        CatLog.d("CatService", "sendMenuSelection before  mCmdIf.sendEnvelope(hexString, null);");
        cancelTimeOut(MSG_ID_DISABLE_DISPLAY_TEXT_DELAYED);
        CatLog.d(this, "[Reset Disable Display Text flag because MENU_SELECTION");
        isDisplayTextDisabled = false;
    }
    private void writeCallDisConnED(ByteArrayOutputStream buffer) {
        if (false == FeatureOption.MTK_BSP_PACKAGE) {
            EventDownloadCallInfo evdlcallInfo = mEventDownloadCallDisConnInfo.removeFirst();        
            int tag = 0;

            if(null != evdlcallInfo) {
                CatLog.d(this, "SS-eventDownload: event is CALL_DISCONNECTED.["+evdlcallInfo.mIsFarEnd+","+evdlcallInfo.mTi+","+evdlcallInfo.mCauseLen+","+evdlcallInfo.mCause+"]");
                buffer.write((1 == evdlcallInfo.mIsFarEnd) ? DEV_ID_NETWORK:DEV_ID_TERMINAL);//source device id
                buffer.write(DEV_ID_UICC);//destination device id
                tag = ComprehensionTlvTag.TRANSACTION_ID.value();
                buffer.write(tag);
                buffer.write(0x01);
                buffer.write(evdlcallInfo.mTi);//transaction id
                if(0 == evdlcallInfo.mCauseLen) {
                    tag = 0x80 | ComprehensionTlvTag.CAUSE.value();
                    buffer.write(tag);
                    buffer.write(0x00);
                } else if ( 0xFF != evdlcallInfo.mCauseLen){
                    tag = 0x80 | ComprehensionTlvTag.CAUSE.value();
                    buffer.write(tag);
                    buffer.write(evdlcallInfo.mCauseLen);//cause  
                    for(int i = evdlcallInfo.mCauseLen-1; i >= 0 ; i--) {
                        int temp = ((evdlcallInfo.mCause >> (i * 8)) & 0xFF);
                        CatLog.d(this, "SS-eventDownload:cause:"+Integer.toHexString(temp));
                        buffer.write((evdlcallInfo.mCause >> (i * 8)) & 0xFF);//write high byte first
                    }
                } else {
                    CatLog.d(this, "SS-eventDownload:no cause value");
                }
            } else {
                CatLog.d(this, "SS-eventDownload:X null evdlcallInfo");
            }        
        }
    }
    private void eventDownload(int event, int sourceId, int destinationId,
            byte[] additionalInfo, boolean oneShot) {

        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        // remove the event list?
        if (null == mEventList || mEventList.length == 0) {
            CatLog.d(this, "SS-eventDownload: event list null");
            return;
        }
        // If there is no specific event in the event list,
        // StkService should not send ENVELOPE command to SIM
        CatLog.d(this, "SS-eventDownload: event list length:"+mEventList.length);
        for (int index = 0; index < mEventList.length;) {
            CatLog.d(this, "SS-eventDownload: event ["+mEventList[index]+"]");
            if (mEventList[index] == event) {
                // if (true == oneShot){
                if (event == EVENT_LIST_ELEMENT_IDLE_SCREEN_AVAILABLE) {
                    CatLog.d(this, "SS-eventDownload: event is IDLE_SCREEN_AVAILABLE");
                    CatLog.d(this, "SS-eventDownload: sent intent with idle = false");
                    Intent intent = new Intent(IDLE_SCREEN_INTENT_NAME);
                    intent.putExtra(IDLE_SCREEN_ENABLE_KEY, false);
                    mContext.sendBroadcast(intent);

                    // IWindowManager wm =
                    // IWindowManager.Stub.asInterface(ServiceManager.getService("window"));
                    // try {
                    // wm.setEventDownloadNeeded(false);
                    // } catch (RemoteException e) {
                    // StkLog.d(this,
                    // "Exception when set EventDownloadNeeded = false in WindowManager");
                    // }
                } else if (event == EVENT_LIST_ELEMENT_USER_ACTIVITY) {
                    CatLog.d(this, "SS-eventDownload: event is USER_ACTIVITY");
                    Intent intent = new Intent(USER_ACTIVITY_INTENT_NAME);
                    intent.putExtra(USER_ACTIVITY_ENABLE_KEY, false);
                    mContext.sendBroadcast(intent);
                } else if (event == EVENT_LIST_ELEMENT_CALL_CONNECTED) {
                    CatLog.d(this, "SS-eventDownload: event is CALL_CONNECTED");                    
                } else if (event == EVENT_LIST_ELEMENT_CALL_DISCONNECTED) {
                    CatLog.d(this, "SS-eventDownload: event is CALL_DISCONNECTED");                    
                }

                if (true == oneShot) {
                    mEventList[index] = 0;
                }
                // }
                break;
            } else {
                index++;
                if (index == mEventList.length) {
                    return;
                }
            }
        }

        // tag
        int tag = BerTlv.BER_EVENT_DOWNLOAD_TAG;
        buf.write(tag);

        // length
        buf.write(0x00); // place holder, assume length < 128.

        // event list
        tag = 0x80 | ComprehensionTlvTag.EVENT_LIST.value();
        buf.write(tag);
        buf.write(0x01); // length
        buf.write(event); // event value

        // device identities
        tag = 0x80 | ComprehensionTlvTag.DEVICE_IDENTITIES.value();
        buf.write(tag);
        buf.write(0x02); // length
        if (false == FeatureOption.MTK_BSP_PACKAGE) {
            if (event == EVENT_LIST_ELEMENT_CALL_DISCONNECTED){
                if(0 < mEventDownloadCallDisConnInfo.size()){
                    if(true == mIsAllCallDisConn) {
                        while(0 < mEventDownloadCallDisConnInfo.size()) {
                            writeCallDisConnED(buf);
                        }
                    } else {
                        writeCallDisConnED(buf);
                    }
                } else {
                    CatLog.d(this, "SS-eventDownload: Wait 2s for modem CALL_DISCONNECTED");
                    Message msg1 = this.obtainMessage(MSG_ID_MODEM_EVDL_CALL_DISCONN_TIMEOUT);
                    if(mEvdlCallObj>0xFFFF) mEvdlCallObj = 0;
                    msg1.obj = new Integer(mEvdlCallObj++);
                    mEvdlCallDisConnObjQ.add((Integer)msg1.obj);
                    mTimeoutHandler.sendMessageDelayed(msg1,MODEM_EVDL_TIMEOUT);
                
                    mNumEventDownloadCallDisConn++;
                    CatLog.d(this, "SS-eventDownload: mNumEventDownloadCallDisConn ++.["+mNumEventDownloadCallDisConn+"]");
                    return;
                }
            } else if (event == EVENT_LIST_ELEMENT_CALL_CONNECTED) {
                if(0 < mEventDownloadCallConnInfo.size()){
                    EventDownloadCallInfo evdlcallInfo = mEventDownloadCallConnInfo.removeFirst();
                    if(null != evdlcallInfo) {
                        CatLog.d(this, "SS-eventDownload: event is CALL_CONNECTED.["+evdlcallInfo.mIsMTCall+","+evdlcallInfo.mTi+"]");
                        buf.write((1 == evdlcallInfo.mIsMTCall) ? DEV_ID_TERMINAL:DEV_ID_NETWORK);//source device id
                        buf.write(DEV_ID_UICC);//destination device id
                        tag = ComprehensionTlvTag.TRANSACTION_ID.value();
                        buf.write(tag);
                        buf.write(0x01);                    
                        buf.write(evdlcallInfo.mTi);//transaction id
                    } else {
                        CatLog.d(this, "SS-eventDownload:O null evdlcallInfo");                
                    }
                } else {
                    Message msg1 = this.obtainMessage(MSG_ID_MODEM_EVDL_CALL_CONN_TIMEOUT);
                    if(mEvdlCallObj>0xFFFF) mEvdlCallObj = 0;                
                    msg1.obj = new Integer(mEvdlCallObj++);
                    mEvdlCallConnObjQ.add((Integer)msg1.obj);
                    mTimeoutHandler.sendMessageDelayed(msg1,MODEM_EVDL_TIMEOUT);
                    
                    mNumEventDownloadCallConn++;
                    CatLog.d(this, "SS-eventDownload: mNumEventDownloadCallConn ++.["+mNumEventDownloadCallConn+"]");
                    return;
                }              
            } else {
                buf.write(sourceId); // source device id
                buf.write(destinationId); // destination device id
            }
        } else {
            buf.write(sourceId); // source device id
            buf.write(destinationId); // destination device id
        }
        // additional information
        if (additionalInfo != null) {
            for (byte b : additionalInfo) {
                buf.write(b);
            }
        }

        byte[] rawData = buf.toByteArray();

        // write real length
        int len = rawData.length - 2; // minus (tag + length)
        rawData[1] = (byte) len;

        String hexString = IccUtils.bytesToHexString(rawData);

        mCmdIf.sendEnvelope(hexString, null);
    }

    private void registerSATcb()
    {
        CatLog.d("CatService", "registerSATcb, mNeedRegisterAgain: " + mNeedRegisterAgain);
        if (mNeedRegisterAgain)
        {
            /* CatService has been disposed before so register callback again */
            mCmdIf.setOnCatSessionEnd(this, MSG_ID_SESSION_END, null);
            mCmdIf.setOnCatProactiveCmd(this, MSG_ID_PROACTIVE_COMMAND, null);
            mCmdIf.setOnCatEvent(this, MSG_ID_EVENT_NOTIFY, null);
            mCmdIf.setOnCatCallSetUp(this, MSG_ID_CALL_SETUP, null);
            if (false == FeatureOption.MTK_BSP_PACKAGE) {
                mCmdIf.setOnStkEvdlCall(this, MSG_ID_EVDL_CALL, null);            
            }
            mNeedRegisterAgain = false;

            CatLog.d("CatService", "notify RIL to send cached command again");
            Intent intentForCachedCommand = new Intent(ACTION_CAT_INIT_DONE);
            intentForCachedCommand.putExtra(PhoneConstants.GEMINI_SIM_ID_KEY, mSimId);
            mContext.sendBroadcast(intentForCachedCommand);
        }
    }
    /**
     * Used for instantiating/updating the Service from the GsmPhone or
     * CdmaPhone constructor.
     * 
     * @param ci CommandsInterface object
     * @param ir IccRecords object
     * @param context phone app context
     * @param fh Icc file handler
     * @param ic Icc card
     * @return The only Service object in the system
     */
    // mtk02374 GEMINI
    public static CatService getInstance(Phone phone,CommandsInterface ci,
            Context context, UiccCard ic, int simId) 
    {
        UiccCardApplication ca = null;
        IccFileHandler fh = null;
        IccRecords ir = null;
        if (ic != null) {
            /* Since Cat is not tied to any application, but rather is Uicc application
             * in itself - just get first FileHandler and IccRecords object
             */
            ca = ic.getApplicationIndex(0);
            if (ca != null) {
                fh = ca.getIccFileHandler();
                ir = ca.getIccRecords();
            }
        }
        CatLog.d("CatService", "call getInstance 1");
        if (phone == null) {
            CatLog.d("CatService", "GsmPhone is null, simId: "+simId);
        }
        synchronized (sInstanceLock) 
        {
            String cmd = null;
            //MTK-START [mtk80950][121110][ALPS00XXXXXX] add for Gemini+ the 3rd card and the 4th card
            if ((GEMINI_SIM_1 == simId && sInstanceSim1 == null) 
                || (GEMINI_SIM_2 == simId && sInstanceSim2 == null) 
                || (GEMINI_SIM_3 == simId && sInstanceSim3 == null)
                || (GEMINI_SIM_4 == simId && sInstanceSim4 == null)) 
            {
                //MTK-END [mtk80950][121110][ALPS00XXXXXX] add for Gemini+ the 3rd card and the 4th card
                CatService tempInstance = null;
                if (ci == null || ca == null || ir == null || context == null || fh == null
                    || ic == null) {
                    CatLog.d("CatService", "ci: " + ((ci != null)? 1 : 0) + ", ca: " + ((ca != null)? 1 : 0) + ", ir: " + ((ir != null)? 1 : 0)
                             + ", context: " + ((context != null)? 1 : 0) + ", fh: " + ((fh != null)? 1 : 0) + ", ic: " + ((ic != null)? 1 : 0));
                    return null;
                }
                if (phone == null) {
                    CatLog.d("CatService", "Phone is null, simId: "+simId);
                    Phone myPhone = PhoneFactory.getDefaultPhone();
                    if (FeatureOption.MTK_GEMINI_SUPPORT == true) {
                        if(PhoneConstants.GEMINI_SIM_3 == simId && FeatureOption.MTK_GEMINI_3SIM_SUPPORT) {
                            phone = ((GeminiPhone)myPhone).getPhonebyId(PhoneConstants.GEMINI_SIM_3);
                        } else if(PhoneConstants.GEMINI_SIM_4 == simId && FeatureOption.MTK_GEMINI_4SIM_SUPPORT) {
                            phone = ((GeminiPhone)myPhone).getPhonebyId(PhoneConstants.GEMINI_SIM_4);                
                        } else {
                            phone = ((GeminiPhone)myPhone).getPhonebyId(simId);                
                        }
                    } else {
                        phone = myPhone;
                    }
                }
                
                HandlerThread thread = new HandlerThread("Cat Telephony service");
                thread.start();
                tempInstance  = new CatService(phone,ci, ca, ir, context, fh, ic,simId);
                //MTK-START [mtk80950][121110][ALPS00XXXXXX] add for Gemini+ the 3rd card and the 4th card
                if (GEMINI_SIM_1 == simId) {
                    CatLog.d(tempInstance, "read data from sInstSim1");
                    cmd = readCmdFromPreference(tempInstance, context, sInst1);
                    CatLog.d(tempInstance, "NEW sInstanceSim1");
                    sInstanceSim1 = tempInstance;
                } else if(GEMINI_SIM_2 == simId) {
                    CatLog.d(tempInstance, "read data from sInstSim2");
                    cmd = readCmdFromPreference(tempInstance, context, sInst2);
                    CatLog.d(tempInstance, "NEW sInstanceSim2");
                    sInstanceSim2 = tempInstance;
                } else if(GEMINI_SIM_3 == simId) {
                    CatLog.d(tempInstance, "read data from sInstSim3");
                    cmd = readCmdFromPreference(tempInstance, context, sInst3);
                    CatLog.d(tempInstance, "NEW sInstanceSim3");
                    sInstanceSim3 = tempInstance;
                } else { //(GEMINI_SIM_4 == simId)
                    CatLog.d(tempInstance, "read data from sInstSim4");
                    cmd = readCmdFromPreference(tempInstance, context, sInst4);
                    CatLog.d(tempInstance, "NEW sInstanceSim4");
                    sInstanceSim4 = tempInstance;
                }
                //MTK-END [mtk80950][121110][ALPS00XXXXXX] add for Gemini+ the 3rd card and the 4th card
                handleProactiveCmdFromDB(tempInstance, cmd);
            } else if ((ir != null) && (mIccRecords != ir)) {
                CatLog.d("CatService", "Reinitialize the Service with SIMRecords");
                mIccRecords = ir;

                // re-Register for SIM ready event.
                // mIccRecords.registerForRecordsLoaded(sInstance,
                // MSG_ID_ICC_RECORDS_LOADED, null);
                // re-Register for SIM ready event.
                //MTK-START [mtk80950][121110][ALPS00XXXXXX] add for Gemini+ the 3rd card and the 4th card
                if (GEMINI_SIM_1 == simId) {
                    CatLog.d("CatService", "read data from sInstSim1");
                    cmd = readCmdFromPreference(sInstanceSim1, context, sInst1);
                    if (mIccRecords != null) {
                        mIccRecords.unregisterForRecordsLoaded(sInstanceSim1);
                    }
                    mIccRecords = ir;
                    mUiccApplication = ca;
                    mIccRecords.registerForRecordsLoaded(sInstanceSim1, MSG_ID_ICC_RECORDS_LOADED, null);
                    handleProactiveCmdFromDB(sInstanceSim1, cmd);
                } else if(GEMINI_SIM_2 == simId) {
                    CatLog.d("CatService", "read data from sInstSim2");
                    cmd = readCmdFromPreference(sInstanceSim2, context, sInst2);
                    if (mIccRecords != null) {
                        mIccRecords.unregisterForRecordsLoaded(sInstanceSim2);
                    }
                    mIccRecords = ir;
                    mUiccApplication = ca;
                    mIccRecords.registerForRecordsLoaded(sInstanceSim2, MSG_ID_ICC_RECORDS_LOADED, null);
                    handleProactiveCmdFromDB(sInstanceSim2, cmd);
                } else if (GEMINI_SIM_3 == simId) {
                    CatLog.d("CatService", "read data from sInstSim3");
                    cmd = readCmdFromPreference(sInstanceSim3, context, sInst3);
                    if (mIccRecords != null) {
                        mIccRecords.unregisterForRecordsLoaded(sInstanceSim3);
                    }
                    mIccRecords = ir;
                    mUiccApplication = ca;
                    mIccRecords.registerForRecordsLoaded(sInstanceSim3, MSG_ID_ICC_RECORDS_LOADED, null);
                    handleProactiveCmdFromDB(sInstanceSim3, cmd);
                } else {//(GEMINI_SIM_4 == simId)
                    CatLog.d("CatService", "read data from sInstSim4");
                    cmd = readCmdFromPreference(sInstanceSim4, context, sInst4);
                    if (mIccRecords != null) {
                        mIccRecords.unregisterForRecordsLoaded(sInstanceSim4);
                    }
                    mIccRecords = ir;
                    mUiccApplication = ca;
                    mIccRecords.registerForRecordsLoaded(sInstanceSim4, MSG_ID_ICC_RECORDS_LOADED, null);
                    handleProactiveCmdFromDB(sInstanceSim4, cmd);
                }
                //MTK-END [mtk80950][121110][ALPS00XXXXXX] add for Gemini+ the 3rd card and the 4th card
                CatLog.d("CatService", "sr changed reinitialize and return current sInstance");
            } else {
                CatLog.d("CatService", "Return current sInstance");
            }

//MTK-START [mtk80950][121110][ALPS00XXXXXX] add for Gemini+ the 3rd card and the 4th card
            if(GEMINI_SIM_1 == simId) {
                sInstanceSim1.registerSATcb();
                return sInstanceSim1;
            } else if(GEMINI_SIM_2 == simId) {
                sInstanceSim2.registerSATcb();
                return sInstanceSim2;
            } else if(GEMINI_SIM_3 == simId) {
                sInstanceSim3.registerSATcb();
                return sInstanceSim3;
            } else {//GEMINI_SIM_4 == simId
                sInstanceSim4.registerSATcb();
                return sInstanceSim4;
            }
//MTK-END [mtk80950][121110][ALPS00XXXXXX] add for Gemini+ the 3rd card and the 4th card
        }

    }


    public static CatService getInstance(CommandsInterface ci,Context context,UiccCard ic) 
    {
        CatLog.d("CatService", "call getInstance 2");
        int sim_id = GEMINI_SIM_1;
        if (ic != null)
        {
            sim_id = ic.getMySimId();
            CatLog.d("CatService", "get SIM id from UiccCard. sim id: " + sim_id);
        }
        return getInstance(null, ci, context, ic, sim_id);
    }

    /**
     * Used by application to get an AppInterface object.
     * 
     * @return The only Service object in the system
     */
    public static AppInterface getInstance(int simId) {
        CatLog.d("CatService", "call getInstance 3");
        return getInstance(null, null, null, null, simId);
    }

    /**
     * Used by application to get an AppInterface object.
     * 
     * @return The only Service object in the system
     */
    public static AppInterface getInstance() {
        CatLog.d("CatService", "call getInstance 4");
        return getInstance(null, null, null, null, GEMINI_SIM_1);
    }

    /* when read set up menu data from db, handle it*/
    private static void handleProactiveCmdFromDB(CatService inst, String data) {
        if (false == FeatureOption.MTK_BSP_PACKAGE) {
            if(data == null) {
                CatLog.d("CatService", "handleProactiveCmdFromDB: cmd = null");
                return;
            }
        	
            inst.default_send_setupmenu_tr = false; //not send setup menu tr
        
            CatLog.d("CatService", " handleProactiveCmdFromDB: cmd = " + data + " from: " + inst);
           	RilMessage rilMsg = new RilMessage(MSG_ID_PROACTIVE_COMMAND, data);
           	inst.mMsgDecoder.sendStartDecodingMessageParams(rilMsg);
           	CatLog.d("CatService", "handleProactiveCmdFromDB: over");
        }else {
            CatLog.d("CatService", "BSP package does not support db cache.");
        }
    }
    
    /* if the second byte is "81", and the seventh byte is "25", this cmd is valid set up menu cmd
     * if the second byte is not "81", but the sixth byte is "25", this cmd is valid set up menu cmd, too.
     * else, it is not a set up menu, no need to save it into db
     */
    private boolean isSetUpMenuCmd(String cmd) {
        boolean validCmd = false;

        if(cmd == null) {
            return false;
        }

        if((cmd.charAt(2) == '8') && (cmd.charAt(3) == '1')) {
            if((cmd.charAt(12) == '2') && (cmd.charAt(13) == '5')) {
                validCmd = true;
            }
        } else {
            if((cmd.charAt(10) == '2') && (cmd.charAt(11) == '5')) {
                validCmd = true;
            }
        }

        return validCmd;
    }

    /**
     * Query if the framework got SET_UP_MENU from modem or not
     * @internal
     */
    public static boolean getSaveNewSetUpMenuFlag(int sim_id)
    {
        boolean result = false;
        if ((sInstanceSim1 != null) && (sim_id == PhoneConstants.GEMINI_SIM_1)) {
            result = sInstanceSim1.mSaveNewSetUpMenu;
            CatLog.d("CatService", "1, mSaveNewSetUpMenu: " + sInstanceSim1.mSaveNewSetUpMenu);
        } else if ((sInstanceSim2 != null) && (sim_id == PhoneConstants.GEMINI_SIM_2)) {
            result = sInstanceSim2.mSaveNewSetUpMenu;
            CatLog.d("CatService", "2, mSaveNewSetUpMenu: " + sInstanceSim2.mSaveNewSetUpMenu);
        } else if ((sInstanceSim3 != null) && (sim_id == PhoneConstants.GEMINI_SIM_3)) {
            result = sInstanceSim3.mSaveNewSetUpMenu;
            CatLog.d("CatService", "3, mSaveNewSetUpMenu: " + sInstanceSim3.mSaveNewSetUpMenu);
        } else if ((sInstanceSim4 != null) && (sim_id == PhoneConstants.GEMINI_SIM_4)) {
            result = sInstanceSim4.mSaveNewSetUpMenu;
            CatLog.d("CatService", "4, mSaveNewSetUpMenu: " + sInstanceSim4.mSaveNewSetUpMenu);
        }

        return result;
    }
    
    @Override
    public void handleMessage(Message msg) {
        CatCmdMessage cmd = null;
        ResponseData resp = null;
        int ret = 0;
        CatLog.d("[BIP]", "SS-handleMessage: msg: "+msg.what);
        switch (msg.what) {
            case MSG_ID_SESSION_END:
            case MSG_ID_PROACTIVE_COMMAND:
            case MSG_ID_EVENT_NOTIFY:
            case MSG_ID_REFRESH:
                CatLog.d(this, "ril message arrived");
                String data = null;
                boolean flag = false;
                if (msg.obj != null) {
                    AsyncResult ar = (AsyncResult) msg.obj;
                    if (ar != null && ar.result != null) {
                        try {
                            data = (String) ar.result;
                            if (false == FeatureOption.MTK_BSP_PACKAGE) {
                                //if the data is valid set up cmd, save it into db
                                boolean isValid = isSetUpMenuCmd(data);
                                if(isValid && this == sInstanceSim1) {
                                    CatLog.d(this, "ril message arrived : save data to db 1");
                                    saveCmdToPreference(mContext, sInst1, data);
                                    mSaveNewSetUpMenu = true;
                                    flag = true;
                                } else if (isValid && this == sInstanceSim2) {
                                    CatLog.d(this, "ril message arrived : save data to db 2");
                                    saveCmdToPreference(mContext, sInst2, data);
                                    mSaveNewSetUpMenu = true;
                                    flag = true;
                                } else if (isValid && this == sInstanceSim3) {
                                    CatLog.d(this, "ril message arrived : save data to db 3");
                                    saveCmdToPreference(mContext, sInst3, data);
                                    mSaveNewSetUpMenu = true;
                                    flag = true;
                                } else if (isValid && this == sInstanceSim4) {
                                    CatLog.d(this, "ril message arrived : save data to db 4");
                                    saveCmdToPreference(mContext, sInst4, data);
                                    mSaveNewSetUpMenu = true;
                                    flag = true;
                                }
                            } else {
                                CatLog.d(this, "BSP package always set SET_UP_MENU from MD.");
                                flag = true;
                            }
                        } catch (ClassCastException e) {
                            break;
                        }
                    }
                }
                RilMessage rilMsg = new RilMessage(msg.what, data);
                rilMsg.setSetUpMenuFromMD(flag);
                mMsgDecoder.sendStartDecodingMessageParams(rilMsg);
                break;
            case MSG_ID_CALL_SETUP:
                mMsgDecoder.sendStartDecodingMessageParams(new RilMessage(msg.what, null));
                break;
            case MSG_ID_ICC_RECORDS_LOADED:
                break;
            case MSG_ID_RIL_MSG_DECODED:
                handleRilMsg((RilMessage) msg.obj);
                break;
            case MSG_ID_RESPONSE:
                handleCmdResponse((CatResponseMessage) msg.obj);
                break;
            case MSG_ID_EVENT_DOWNLOAD:
                handleEventDownload((CatResponseMessage) msg.obj);
                break;
            case MSG_ID_DB_HANDLER:
                handleDBHandler(msg.arg1);
                break;
            case MSG_ID_OPEN_CHANNEL_DONE:
                ret = msg.arg1;
                cmd = (CatCmdMessage) msg.obj;
                // resp = new OpenChannelResponseData(cmd.mChannelStatus,
                // cmd.mBearerDesc, cmd.mBufferSize);
                if (mCurrentCmd == null)
                {
                    CatLog.d("[BIP]", "SS-handleMessage: skip open channel response because current cmd is null");
                    break;
                }
                else if (mCurrentCmd != null)
                {
                    if (mCurrentCmd.mCmdDet.typeOfCommand != CommandType.OPEN_CHANNEL.value())
                    {
                        CatLog.d("[BIP]", "SS-handleMessage: skip open channel response because current cmd type is not OPEN_CHANNEL");
                        break;
                    }
                }
                if (ret == ErrorValue.NO_ERROR) {
                    resp = new OpenChannelResponseDataEx(cmd.mChannelStatusData, cmd.mBearerDesc,
                            cmd.mBufferSize,cmd.mTransportProtocol.protocolType);
                    CatLog.d("[BIP]", "SS-handleMessage: open channel successfully");
                    sendTerminalResponse(mCurrentCmd.mCmdDet, ResultCode.OK, false, 0, resp);
                } else if (ret == ErrorValue.COMMAND_PERFORMED_WITH_MODIFICATION) {
                    resp = new OpenChannelResponseDataEx(cmd.mChannelStatusData, cmd.mBearerDesc,
                            cmd.mBufferSize,cmd.mTransportProtocol.protocolType);
                    CatLog.d("[BIP]", "SS-handleMessage: open channel with modified parameters");
                    sendTerminalResponse(mCurrentCmd.mCmdDet, ResultCode.PRFRMD_WITH_MODIFICATION,
                            false, 0, resp);
                } else if (ret == ErrorValue.ME_IS_BUSY_ON_CALL) {
                    resp = new OpenChannelResponseDataEx(null, cmd.mBearerDesc, cmd.mBufferSize, 
                            cmd.mTransportProtocol.protocolType);
                    CatLog.d("[BIP]", "SS-handleMessage: ME is busy on call");
                    sendTerminalResponse(mCurrentCmd.mCmdDet,
                            ResultCode.TERMINAL_CRNTLY_UNABLE_TO_PROCESS, true,
                            ADDITIONAL_INFO_FOR_BIP_CHANNEL_CLOSED, resp);
                } else {
                    resp = new OpenChannelResponseDataEx(cmd.mChannelStatusData, cmd.mBearerDesc,
                            cmd.mBufferSize,cmd.mTransportProtocol.protocolType);
                    CatLog.d("[BIP]", "SS-handleMessage: open channel failed");
                    sendTerminalResponse(cmd.mCmdDet, ResultCode.BIP_ERROR, false, 0, resp);
                }
                break;
            case MSG_ID_SEND_DATA_DONE:
                ret = msg.arg1;
                int size = msg.arg2;
                cmd = (CatCmdMessage) msg.obj;
                resp = new SendDataResponseData(size);
                if (ret == ErrorValue.NO_ERROR) {
                    sendTerminalResponse(cmd.mCmdDet, ResultCode.OK, false, 0, resp);
                } else if (ret == ErrorValue.CHANNEL_ID_NOT_VALID) {
                    sendTerminalResponse(cmd.mCmdDet, ResultCode.BIP_ERROR, true,
                            ADDITIONAL_INFO_FOR_BIP_CHANNEL_ID_NOT_AVAILABLE, null);
                } else {
                    sendTerminalResponse(cmd.mCmdDet, ResultCode.BIP_ERROR, false, 0, resp);
                }
                break;
            case MSG_ID_RECEIVE_DATA_DONE:
                ret = msg.arg1;
                cmd = (CatCmdMessage) msg.obj;
                byte[] buffer = cmd.mChannelData;
                int remainingCount = cmd.mRemainingDataLength;

                resp = new ReceiveDataResponseData(buffer, remainingCount);
                if (ret == ErrorValue.NO_ERROR) {
                    sendTerminalResponse(cmd.mCmdDet, ResultCode.OK, false, 0, resp);
                } else if (ret == ErrorValue.MISSING_DATA) {
                    sendTerminalResponse(cmd.mCmdDet, ResultCode.PRFRMD_WITH_MISSING_INFO, false,
                            0, resp);
                } else {
                    sendTerminalResponse(cmd.mCmdDet, ResultCode.BIP_ERROR, false, 0, null);
                }
                break;
            case MSG_ID_CLOSE_CHANNEL_DONE:
                cmd = (CatCmdMessage) msg.obj;
                if (msg.arg1 == ErrorValue.NO_ERROR) {
                    sendTerminalResponse(cmd.mCmdDet, ResultCode.OK, false, 0, null);
                } else if (msg.arg1 == ErrorValue.CHANNEL_ID_NOT_VALID) {
                    sendTerminalResponse(cmd.mCmdDet, ResultCode.BIP_ERROR, true,
                            ADDITIONAL_INFO_FOR_BIP_CHANNEL_ID_NOT_AVAILABLE, null);
                } else if (msg.arg1 == ErrorValue.CHANNEL_ALREADY_CLOSED) {
                    sendTerminalResponse(cmd.mCmdDet, ResultCode.BIP_ERROR, true,
                            ADDITIONAL_INFO_FOR_BIP_CHANNEL_CLOSED, null);
                }
                break;
            case MSG_ID_GET_CHANNEL_STATUS_DONE:
                ArrayList arrList = null;
                ret = msg.arg1;
                cmd = (CatCmdMessage) msg.obj;
                arrList = (ArrayList)cmd.mChannelStatusList;//(CatCmdMessage) msg.obj;

                CatLog.d("[BIP]", "SS-handleCmdResponse: MSG_ID_GET_CHANNEL_STATUS_DONE:" + arrList.size());
                resp = new GetMultipleChannelStatusResponseData(arrList);
                sendTerminalResponse(cmd.mCmdDet, ResultCode.OK, false, 0, resp);
                break;
            case MSG_ID_CONN_MGR_TIMEOUT:
                cmd = (CatCmdMessage) msg.obj;
                resp = new OpenChannelResponseDataEx(cmd.mChannelStatusData, cmd.mBearerDesc,
                        cmd.mBufferSize, cmd.mTransportProtocol.protocolType);
                CatLog.d("[BIP]", "SS-handleMessage: timeout for ConnMgr intent. "
                        + cmd.mCmdDet.typeOfCommand);
                sendTerminalResponse(cmd.mCmdDet, ResultCode.BIP_ERROR, false, 0, resp);
                mBipMgr.setOpenInProgressFlag(false);
                mBipMgr.setConnMgrTimeoutFlag(true);
                break;
            case MSG_ID_CONN_RETRY_TIMEOUT:
                CatLog.d("[BIP]", "SS-handleMessage: timeout for ConnMgr RETRY. ");
                mBipMgr.reOpenChannel();
                break;
            case MSG_ID_SIM_READY:
                CatLog.d(this, "SIM ready. Reporting STK service running now...");
                if (false == FeatureOption.MTK_BSP_PACKAGE) {
                    mCmdIf.setStkEvdlCallByAP(0, null);//Evnet download for call is handled by AP
                } else {
                    mCmdIf.setStkEvdlCallByAP(1, null);//Evnet download for call is handled by MODEM (Default for MODME)
                }
                mCmdIf.reportStkServiceIsRunning(null);
                break;
    
            //MTK-START [mtk80589][121026][ALPS00376525] STK dialog pop up caused ISVR
            case MSG_ID_IVSR_DELAYED:
                CatLog.d(this, "[IVSR cancel IVSR flag");
                isIvsrBootUp = false;
                break;
            //MTK-END [mtk80589][121026][ALPS00376525] STK dialog pop up caused ISVR
            case MSG_ID_EVDL_CALL:
                if (false == FeatureOption.MTK_BSP_PACKAGE) {
                    CatLog.d(this, "RIL event download for call.");
                    if (msg.obj != null) {
                        AsyncResult ar = (AsyncResult) msg.obj;
                        if (ar != null && ar.result != null) {
                            int[] evdlCalldata = (int[])ar.result;
                            EventDownloadCallInfo eventDownloadCallInfo = new EventDownloadCallInfo(
                                evdlCalldata[0],evdlCalldata[1],
                                evdlCalldata[2],evdlCalldata[3],
                                evdlCalldata[4],evdlCalldata[5]);

                            if(0xFF > eventDownloadCallInfo.mCauseLen) { //0xFF ; it means there is no cause tag.
                                eventDownloadCallInfo.mCauseLen >>= 1;//eventDownloadCallInfo.mCauseLen >> 1;//hex string len -> ascii string len
                            } else {
                                eventDownloadCallInfo.mCauseLen = 0xFF;
                            }
                            if(STK_EVDL_CALL_STATE_CALLCONN == evdlCalldata[0]){
                                mEventDownloadCallConnInfo.add(eventDownloadCallInfo);                            
                                if(mNumEventDownloadCallConn > 0){
                                    mNumEventDownloadCallConn--;
                                    this.removeMessages(MSG_ID_MODEM_EVDL_CALL_CONN_TIMEOUT, mEvdlCallConnObjQ.removeFirst());
                                    CatLog.d(this, "mNumEventDownloadCallConn --.["+mNumEventDownloadCallConn+"]");                                
                                    eventDownload(EVENT_LIST_ELEMENT_CALL_CONNECTED,0,0,null,false);
                                }
                            } else {
                                mEventDownloadCallDisConnInfo.add(eventDownloadCallInfo);
                                if(mNumEventDownloadCallDisConn > 0){
                                    mNumEventDownloadCallDisConn--;
                                    this.removeMessages(MSG_ID_MODEM_EVDL_CALL_DISCONN_TIMEOUT, mEvdlCallDisConnObjQ.removeFirst());
                                    CatLog.d(this, "mNumEventDownloadCallDisConn --.["+mNumEventDownloadCallDisConn+"]");
                                    eventDownload(EVENT_LIST_ELEMENT_CALL_DISCONNECTED,0,0,null,false);
                                }
                            }
                            CatLog.d(this, "Evdl data:"+evdlCalldata[0]+","+evdlCalldata[1]+","+evdlCalldata[2]+","+evdlCalldata[3]+","+evdlCalldata[4]);                        
                        }
                    }
                }
                break;
            default:
                throw new AssertionError("Unrecognized CAT command: " + msg.what);
        }
    }

    @Override
    public synchronized void onCmdResponse(CatResponseMessage resMsg) {
        if (resMsg == null) {
            return;
        }
        // queue a response message.
        Message msg = obtainMessage(MSG_ID_RESPONSE, resMsg);
        msg.sendToTarget();
    }

    public synchronized void onEventDownload(CatResponseMessage resMsg) {
        if (resMsg == null) {
            return;
        }
        // queue a response message.
        Message msg = obtainMessage(MSG_ID_EVENT_DOWNLOAD, resMsg);
        msg.sendToTarget();
    }

    public synchronized void onDBHandler(int sim_id)
    {
        // queue a response message.
        Message msg = obtainMessage(MSG_ID_DB_HANDLER, sim_id, 0);
        msg.sendToTarget();
    }
    
    private boolean validateResponse(CatResponseMessage resMsg) {
        boolean ret = false;
        if (mCurrentCmd != null) {
            // ret = (resMsg.cmdDet.compareTo(mCurrentCmd.mCmdDet));
            ret = (resMsg.mCmdDet.typeOfCommand == mCurrentCmd.mCmdDet.typeOfCommand);
            CatLog.d(this, "SS-validateResponse: ret=" + ret +
                    " [" + resMsg.mCmdDet.typeOfCommand +
                    "/" + mCurrentCmd.mCmdDet.typeOfCommand + "]");
            return ret;
        }
        CatLog.d(this, "SS-validateResponse: mCurrentCmd is null");
        return false;
    }

    private boolean removeMenu(Menu menu) {
        try {
            if (menu.items.size() == 1 && menu.items.get(0) == null) {
                return true;
            }
        } catch (NullPointerException e) {
            CatLog.d(this, "Unable to get Menu's items size");
            return true;
        }
        return false;
    }

    private void handleEventDownload(CatResponseMessage resMsg) {
        eventDownload(resMsg.mEvent, resMsg.mSourceId, resMsg.mDestinationId,
                resMsg.mAdditionalInfo, resMsg.mOneShot);
    }

    private void handleDBHandler(int sim_id) 
    {
        CatLog.d(this, "handleDBHandler, sim_id: " + sim_id);
        if (sim_id == GEMINI_SIM_1) {
            saveCmdToPreference(mContext, sInst1, null);
        } else if (sim_id == GEMINI_SIM_2) {
            saveCmdToPreference(mContext, sInst2, null);
        } else if (sim_id == GEMINI_SIM_3) {
            saveCmdToPreference(mContext, sInst3, null);
        } else if (sim_id == GEMINI_SIM_4) {
            saveCmdToPreference(mContext, sInst4, null);
        }
    }

    private void handleCmdResponse(CatResponseMessage resMsg) {
        // Make sure the response details match the last valid command. An
        // invalid
        // response is a one that doesn't have a corresponding proactive command
        // and sending it can "confuse" the baseband/ril.
        // One reason for out of order responses can be UI glitches. For
        // example,
        // if the application launch an activity, and that activity is stored
        // by the framework inside the history stack. That activity will be
        // available for relaunch using the latest application dialog
        // (long press on the home button). Relaunching that activity can send
        // the same command's result again to the CatService and can cause it to
        // get out of sync with the SIM.
        if (!validateResponse(resMsg)) {
            return;
        }
        ResponseData resp = null;
        boolean helpRequired = false;
        CommandDetails cmdDet = resMsg.getCmdDetails();
        AppInterface.CommandType type = AppInterface.CommandType.fromInt(cmdDet.typeOfCommand);

        switch (resMsg.mResCode) {
            case HELP_INFO_REQUIRED:
                helpRequired = true;
                // fall through
            case OK:
            case PRFRMD_WITH_PARTIAL_COMPREHENSION:
            case PRFRMD_WITH_MISSING_INFO:
            case PRFRMD_WITH_ADDITIONAL_EFS_READ:
            case PRFRMD_ICON_NOT_DISPLAYED:
            case PRFRMD_MODIFIED_BY_NAA:
            case PRFRMD_LIMITED_SERVICE:
            case PRFRMD_WITH_MODIFICATION:
            case PRFRMD_NAA_NOT_ACTIVE:
            case PRFRMD_TONE_NOT_PLAYED:
            case TERMINAL_CRNTLY_UNABLE_TO_PROCESS:                
                switch (type) {
                    case SET_UP_MENU:
                        CatLog.d("CatService", "SET_UP_MENU");
                        helpRequired = resMsg.mResCode == ResultCode.HELP_INFO_REQUIRED;
                        sendMenuSelection(resMsg.mUsersMenuSelection, helpRequired);
                        return;
                    case SELECT_ITEM:
                        CatLog.d("CatService", "SELECT_ITEM");
                        resp = new SelectItemResponseData(resMsg.mUsersMenuSelection);
                        break;
                    case GET_INPUT:
                    case GET_INKEY:
                        Input input = mCurrentCmd.geInput();
                        if (!input.yesNo) {
                            // when help is requested there is no need to send
                            // the text
                            // string object.
                            if (!helpRequired) {
                                resp = new GetInkeyInputResponseData(resMsg.mUsersInput,
                                        input.ucs2, input.packed);
                            }
                        } else {
                            resp = new GetInkeyInputResponseData(
                                    resMsg.mUsersYesNoSelection);
                        }
                        break;
                    case DISPLAY_TEXT:
                        if (false == FeatureOption.MTK_BSP_PACKAGE) {
                            if(mHasCachedDTCmd) {
                                resetPowerOnSequenceFlag();
                            }
                        }
                    case LAUNCH_BROWSER:
                        break;
                    case SET_UP_CALL:
                        // mCmdIf.handleCallSetupRequestFromSim(resMsg.usersConfirm,
                        // null);
                        mCmdIf.handleCallSetupRequestFromSim(resMsg.mUsersConfirm, resMsg.mResCode
                                .value(), null);
                        // No need to send terminal response for SET UP CALL.
                        // The user's
                        // confirmation result is send back using a dedicated
                        // ril message
                        // invoked by the CommandInterface call above.
                        mCurrentCmd = null;
                        return;
                    case OPEN_CHANNEL:
                        CatLog.d("[BIP]", "SS-handleCmdResponse: user accept to open channel");
                        Message response = obtainMessage(MSG_ID_OPEN_CHANNEL_DONE);
                        if (mCurrentCmd != null) {
                            mCmdMessage = mCurrentCmd;
                            mBipMgr.openChannel(response);
                        } else {
                            CatLog.d("[BIP]", "SS-handleCmdResponse: invalid OPEN_CHANNEL");
                        }
                        return;
                    default:
                        break;                        
                }
                break;
            case NO_RESPONSE_FROM_USER:
            case UICC_SESSION_TERM_BY_USER:
            case BACKWARD_MOVE_BY_USER:
            case CMD_DATA_NOT_UNDERSTOOD:
                switch (type) {
                    case SET_UP_CALL:
                        CatLog.d(this, "SS-handleCmdResponse: [BACKWARD_MOVE_BY_USER] userConfirm["
                                + resMsg.mUsersConfirm + "] resultCode[" + resMsg.mResCode.value()
                                + "]");
                        mCmdIf.handleCallSetupRequestFromSim(false,
                                ResultCode.BACKWARD_MOVE_BY_USER.value(), null);
                        break;
                    case DISPLAY_TEXT:
                        if (false == FeatureOption.MTK_BSP_PACKAGE) {
                            if(mHasCachedDTCmd) {
                                resetPowerOnSequenceFlag();
                            }
                        }
                        break;
                    case OPEN_CHANNEL:
                        if (mCurrentCmd != null
                                && mCurrentCmd.mBearerDesc != null
                                && mCurrentCmd.mBufferSize > 0) {
                            resp = new OpenChannelResponseDataEx(null, mCurrentCmd.mBearerDesc,
                                    mCurrentCmd.mBufferSize, mCurrentCmd.mTransportProtocol.protocolType);
                            sendTerminalResponse(cmdDet, resMsg.mResCode, false, 0, resp);
                        } else {
                            CatLog.d("[BIP]", "SS-handleCmdResponse: mCurrentCmd = null");
                        }
                        break;
                }
                resp = null;
                break;
            case NETWORK_CRNTLY_UNABLE_TO_PROCESS:
                switch (type) {
                    case SET_UP_CALL:
                        mCmdIf.handleCallSetupRequestFromSim(resMsg.mUsersConfirm, resMsg.mResCode
                                .value(), null);
                        // No need to send terminal response for SET UP CALL.
                        // The user's
                        // confirmation result is send back using a dedicated
                        // ril message
                        // invoked by the CommandInterface call above.
                        mCurrentCmd = null;
                        return;
                    case DISPLAY_TEXT:
                        if (false == FeatureOption.MTK_BSP_PACKAGE) {
                            if(mHasCachedDTCmd) {
                                resetPowerOnSequenceFlag();
                            }
                        }
                        if (resMsg.mAdditionalInfo != null && resMsg.mAdditionalInfo.length > 0
                                && (int) (resMsg.mAdditionalInfo[0]) != 0) {
                            sendTerminalResponse(cmdDet, resMsg.mResCode, true,
                                    (int) (resMsg.mAdditionalInfo[0]), resp);
                            mCurrentCmd = null;
                            return;
                        }
                        break;
                    default:
                        break;
                }
                break;
            case USER_NOT_ACCEPT:
                switch (AppInterface.CommandType.fromInt(cmdDet.typeOfCommand)) {
                    case OPEN_CHANNEL:
                        CatLog.d("[BIP]", "SS-handleCmdResponse: User don't accept open channel");
                        if (mCurrentCmd != null
                                                        // && mCurrentCmd.mChannelStatusData != null
                                && mCurrentCmd.mBearerDesc != null
                                && mCurrentCmd.mBufferSize > 0) {
                            // resp = new
                            // OpenChannelResponseData(mCurrentCmd.mChannelStatusData,
                            // mCurrentCmd.mBearerDesc,
                            // mCurrentCmd.mBufferSize);
                            resp = new OpenChannelResponseDataEx(null, mCurrentCmd.mBearerDesc,
                                    mCurrentCmd.mBufferSize, mCurrentCmd.mTransportProtocol.protocolType);
                            sendTerminalResponse(cmdDet, resMsg.mResCode, false, 0, resp);
                        } else {
                            if (mCurrentCmd == null) {
                                CatLog.d("[BIP]", "SS-handleCmdResponse: mCurrent is null");
                            } else {
                                CatLog.d("[BIP]", "SS-handleCmdResponse: other params is invalid");
                            }
                        }
                        return;
                    default:
                        break;
                }
                break;
            case LAUNCH_BROWSER_ERROR:
                if (cmdDet.typeOfCommand == AppInterface.CommandType.LAUNCH_BROWSER.value()) {
                    CatLog.d(this, "send TR for LAUNCH_BROWSER_ERROR");
                    sendTerminalResponse(cmdDet, resMsg.mResCode, true, 0x02, null);
                    return;
                }
                break;
            default:
                return;
        }
        sendTerminalResponse(cmdDet, resMsg.mResCode, resMsg.mIncludeAdditionalInfo,
            (true == resMsg.mIncludeAdditionalInfo && 
            resMsg.mAdditionalInfo != null && 
            resMsg.mAdditionalInfo.length > 0)?(int)(resMsg.mAdditionalInfo[0]):0, 
            resp);
        mCurrentCmd = null;
    }

    public Context getContext() {
        return mContext;
    }

    private BroadcastReceiver CatServiceReceiver = new BroadcastReceiver() {
        public static final String INTENT_KEY_DETECT_STATUS = "simDetectStatus";
        public static final String EXTRA_VALUE_REMOVE_SIM = "REMOVE";

        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            CatLog.d(this, "CatServiceReceiver action: " + action);
            //MTK-START [mtk80589][121026][ALPS00376525] STK dialog pop up caused ISVR
            if(action.equals(ACTION_SHUTDOWN_IPO)) {
                CatLog.d(this, "remove event list because of ipo shutdown");
                mEventList = null;
                mSaveNewSetUpMenu = false;
            } else if(action.equals(TelephonyIntents.ACTION_IVSR_NOTIFY)) {
                if(mSimId != intent.getIntExtra(PhoneConstants.GEMINI_SIM_ID_KEY, PhoneConstants.GEMINI_SIM_1)) {
                    return;
                }
                // don't send DISPLAY_TEXT to app becasue of IVSR
                String ivsrAction = intent.getStringExtra(TelephonyIntents.INTENT_KEY_IVSR_ACTION);
                if(ivsrAction.equals("start")) {
                    CatLog.d(this, "[IVSR set IVSR flag");
                    isIvsrBootUp = true;
                    sendEmptyMessageDelayed(MSG_ID_IVSR_DELAYED, IVSR_DELAYED_TIME);
                }
            } else if (action.equals(TelephonyIntents.ACTION_SIM_RECOVERY_DONE) || action.equals(TelephonyIntents.ACTION_MD_TYPE_CHANGE)) {
                /* Do not show display text because sim reset this time may be triggerd by SIM Recovery or World Phone */
                if (action.equals(TelephonyIntents.ACTION_SIM_RECOVERY_DONE)) {
                    CatLog.d(this, "[Set SIM Recovery flag, sim: " + mSimId + ", isDisplayTextDisabled: " + ((isDisplayTextDisabled)? 1 : 0));
                } else {
                    CatLog.d(this, "[World phone flag: " + mSimId + ", isDisplayTextDisabled: " + ((isDisplayTextDisabled)? 1 : 0));
                }
                startTimeOut(MSG_ID_DISABLE_DISPLAY_TEXT_DELAYED, DISABLE_DISPLAY_TEXT_DELAYED_TIME);
                isDisplayTextDisabled = true;
            }
            //MTK-END [mtk80589][121026][ALPS00376525] STK dialog pop up caused ISVR
        
            if (TelephonyIntents.ACTION_SIM_STATE_CHANGED.equals(intent.getAction())) {
                int id = intent.getIntExtra(PhoneConstants.GEMINI_SIM_ID_KEY, -1);
                CatLog.d(this, "SIM state change, id: " + id + ", simId: " + mSimId);
                if(id == mSimId) {
                    simState = intent.getStringExtra(IccCardConstants.INTENT_KEY_ICC_STATE);
                    simIdfromIntent = id;
                    CatLog.d(this, "simIdfromIntent[" + simIdfromIntent + "],simState[" + simState + "]");
                    if ((IccCardConstants.INTENT_VALUE_ICC_ABSENT).equals(simState)) {
                        clearCachedDisplayText(id);
                        mSaveNewSetUpMenu = false;
                        //MTK-START [mtk80950][131009][ALPS01065969] when sim absent, need to clear SET_UP_MENU data from DB, or else, insert a sim card which is not supported STK will show the stk menu of the last sim card.
                        handleDBHandler(mSimId);
                        //MTK-END [mtk80950][131009][ALPS01065969] when sim absent, need to clear SET_UP_MENU data from DB, or else, insert a sim card which is not supported STK will show the stk menu of the last sim card.
                    }
                }
            } else if (TelephonyIntents.ACTION_RADIO_TECHNOLOGY_CHANGED.equals(intent.getAction())) {
                int SIMID = intent.getIntExtra(PhoneConstants.GEMINI_SIM_ID_KEY,-1);
                String newType = intent.getStringExtra(PhoneConstants.PHONE_NAME_KEY);
                CatLog.d(this, "GSM/CDMA changes, instant sim id: " + mSimId + ", sim id: " + SIMID + ", new type: " + newType);
                if (SIMID == mSimId && newType.equals("CDMA"))
                {
                    mSaveNewSetUpMenu = false;
                }
            }
        }
    };

    // MTK-START [ALPS00092673] Orange feature merge back added by mtk80589 in
    // 2011.11.15
    /*
     * Detail description: This feature provides a interface to get menu title
     * string from EF_SUME
     */
    // MTK_OP03_PROTECT_START
    //public String getMenuTitleFromEf() {
    //    return mIccRecords.getMenuTitleFromEf();
    //}

    // MTK_OP03_PROTECT_END
    // MTK-END [ALPS00092673] Orange feature merge back added by mtk80589 in
    // 2011.11.15

    // add for [ALPS00245360] should not show DISPLAY_TEXT dialog when alarm
    // booting
    private boolean isAlarmBoot() {
        String bootReason = SystemProperties.get("sys.boot.reason");
        return (bootReason != null && bootReason.equals("1"));
    }

    private boolean checkSetupWizardInstalled() {
        final String packageName = "com.google.android.setupwizard";
        final String activityName = "com.google.android.setupwizard.SetupWizardActivity";

        PackageManager pm = mContext.getPackageManager();
        if (pm == null) {
            CatLog.d(this, "fail to get PM");
            return false;
        }

        // ComponentName cm = new ComponentName(packageName, activityName);
        boolean isPkgInstalled = true;
        try {
            pm.getInstallerPackageName(packageName);
        } catch (IllegalArgumentException e) {
            CatLog.d(this, "fail to get SetupWizard package");
            isPkgInstalled = false;
        }

        if (isPkgInstalled == true) {
            int pkgEnabledState = pm.getComponentEnabledSetting(new ComponentName(packageName,
                    activityName));
            if (pkgEnabledState == PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                    || pkgEnabledState == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT) {
                CatLog.d(this, "should not show DISPLAY_TEXT immediately");
                return true;
            }
            else
            {
                CatLog.d(this, "Setup Wizard Activity is not activate");
            }
        }

        CatLog.d(this, "isPkgInstalled = false");
        return false;
    }
    
    private ContentObserver mPowerOnSequenceObserver = new ContentObserver(this) {
        @Override
        public void onChange(boolean selfChange, Uri uri) {
            // handle change here
            if (false == FeatureOption.MTK_BSP_PACKAGE) {
                int OOBE_Value = System.getInt(
                    mContext.getContentResolver(),
                    System.OOBE_DISPLAY,
                    System.OOBE_DISPLAY_DEFAULT);
    
                CatLog.d(this, "mPowerOnSequenceObserver onChange, OOBE_Value: " + OOBE_Value);
                if (OOBE_Value == System.OOBE_DISPLAY_DEFAULT)
                {
                    int seqValue = System.getInt(
                        mContext.getContentResolver(),
                        System.DIALOG_SEQUENCE_SETTINGS,
                        System.DIALOG_SEQUENCE_DEFAULT);
    
                    CatLog.d(this, "mPowerOnSequenceObserver onChange, " + seqValue);
                    if (seqValue == System.DIALOG_SEQUENCE_STK) {
                        // send DISPLAY_TEXT to app
                        if(mCachedDisplayTextCmd != null) {
                            /* Check if phone can show the dialog or not before sending DISPLAY_TEXT */
                            boolean isAlarmState = isAlarmBoot();
                            boolean isFlightMode = false;
                            int flightMode = 0;
                            try {
                                flightMode = Settings.Global.getInt(mContext.getContentResolver(),
                                        Settings.Global.AIRPLANE_MODE_ON);
                            } catch (SettingNotFoundException e) {
                                CatLog.d(this, "fail to get property from Settings");
                                flightMode = 0;
                            }
                            isFlightMode = (flightMode != 0);
                            CatLog.d(this, "isAlarmState = " + isAlarmState + ", isFlightMode = "
                                    + isFlightMode + ", flightMode = " + flightMode);
    
                            if (isAlarmState && isFlightMode) {
                                resetPowerOnSequenceFlag();
                                sendTerminalResponse(mCachedDisplayTextCmd.mCmdDet, ResultCode.OK, false, 0, null);
                                mCachedDisplayTextCmd = null;
                                unregisterPowerOnSequenceObserver();
                                return;
                            }
    
                            // add for SetupWizard
                            if (checkSetupWizardInstalled() == true) {
                                resetPowerOnSequenceFlag();
                                sendTerminalResponse(mCachedDisplayTextCmd.mCmdDet, ResultCode.BACKWARD_MOVE_BY_USER, false,
                                        0, null);
                                mCachedDisplayTextCmd = null;
                                unregisterPowerOnSequenceObserver();
                                return;
                            }
                        
                            //MTK-START [mtk80589][121026][ALPS00376525] STK dialog pop up caused ISVR
                            if(isIvsrBootUp) {
                                CatLog.d(this, "[IVSR send TR directly");
                                resetPowerOnSequenceFlag();
                                sendTerminalResponse(mCachedDisplayTextCmd.mCmdDet, ResultCode.BACKWARD_MOVE_BY_USER, false, 0, null);
                                mCachedDisplayTextCmd = null;
                                unregisterPowerOnSequenceObserver();
                                return;
                            }
                            //MTK-END [mtk80589][121026][ALPS00376525] STK dialog pop up caused ISVR
                            if(isDisplayTextDisabled) {
                                CatLog.d(this, "[SIM Recovery send TR directly");
                                resetPowerOnSequenceFlag();
                                sendTerminalResponse(mCachedDisplayTextCmd.mCmdDet, ResultCode.BACKWARD_MOVE_BY_USER, false, 0, null);
                                mCachedDisplayTextCmd = null;
                                unregisterPowerOnSequenceObserver();
                                return;
                            }
                            CatLog.d(this, "send DISPLAY_TEXT to app");
                            sendCatCmd(mCachedDisplayTextCmd);
                            mCachedDisplayTextCmd = null;
                            
                            // unregister the ContentObserver object, because
                            // we just need to cache the first DISPLAY_TEXT
                            unregisterPowerOnSequenceObserver();
                        }
                    } else if (seqValue == System.DIALOG_SEQUENCE_DEFAULT) {
                        if(mCachedDisplayTextCmd != null) {
                            // set flag to DIALOG_SEQUENCE_STK
                            System.putInt(
                                mContext.getContentResolver(),
                                System.DIALOG_SEQUENCE_SETTINGS,
                                System.DIALOG_SEQUENCE_STK);
                        }
                    }
                }
            }
        }
        
        @Override
        public void onChange(boolean selfChange) {
            onChange(selfChange, null);
        }
    };
    
    private void sendCatCmd(CatCmdMessage cmdMsg) {
        CatLog.d(this, "call sendCatCmd, sim id: " + mSimId);
        Intent intent = null;

        if (mSimId == PhoneConstants.GEMINI_SIM_2) {
            intent = new Intent(AppInterface.CAT_CMD_ACTION_2);
            CatLog.d(this, "SS-sendCatCmd: sending CAT_CMD_ACTION_2");
        } else if (mSimId == PhoneConstants.GEMINI_SIM_1) {
            intent = new Intent(AppInterface.CAT_CMD_ACTION);
            CatLog.d(this, "SS-sendCatCmd: sending CAT_CMD_ACTION");
        } else if (mSimId == PhoneConstants.GEMINI_SIM_3) {
            intent = new Intent(AppInterface.CAT_CMD_ACTION_3);
            CatLog.d(this, "SS-sendCatCmd: sending CAT_CMD_ACTION_3");
        } else if (mSimId == PhoneConstants.GEMINI_SIM_4) {
            intent = new Intent(AppInterface.CAT_CMD_ACTION_4);
            CatLog.d(this, "SS-sendCatCmd: sending CAT_CMD_ACTION_4");
        }
        intent.putExtra("STK CMD", cmdMsg);
        mContext.sendBroadcast(intent);
    }
    
    private void registerPowerOnSequenceObserver() {
        if (false == FeatureOption.MTK_BSP_PACKAGE) {
            CatLog.d(this, "call registerPowerOnSequenceObserver");
            Uri uri = Settings.System.getUriFor(System.DIALOG_SEQUENCE_SETTINGS);
            mContext.getContentResolver().registerContentObserver(
                uri, false, mPowerOnSequenceObserver);
            mContext.getContentResolver().registerContentObserver(
                Settings.System.getUriFor(System.OOBE_DISPLAY), false, mPowerOnSequenceObserver);
            mHasCachedDTCmd = true;
        }
    }
    
    private void unregisterPowerOnSequenceObserver() {
        if (false == FeatureOption.MTK_BSP_PACKAGE) {
            CatLog.d(this, "call unregisterPowerOnSequenceObserver");
            mContext.getContentResolver().unregisterContentObserver(
                mPowerOnSequenceObserver);
            cancelTimeOut(MSG_ID_CACHED_DISPLAY_TEXT_TIMEOUT);
        }
    }
    
    private void resetPowerOnSequenceFlag() {
        if (false == FeatureOption.MTK_BSP_PACKAGE) {
            int seqValue = System.getInt(
                        mContext.getContentResolver(),
                        System.DIALOG_SEQUENCE_SETTINGS,
                        System.DIALOG_SEQUENCE_DEFAULT);
            CatLog.d(this, "call resetPowerOnSequenceFlag, seqValue: " + seqValue);
    
            if (seqValue == System.DIALOG_SEQUENCE_STK)
            {
                System.putInt(
                    mContext.getContentResolver(),
                    System.DIALOG_SEQUENCE_SETTINGS,
                    System.DIALOG_SEQUENCE_DEFAULT);
            }    
            mHasCachedDTCmd = false;
        }
    }

    private CatCmdMessage mCmdMessage = null;
    
    public CatCmdMessage getCmdMessage() {
        CatLog.d(this, "getCmdMessage, command type: " + ((mCmdMessage != null && mCmdMessage.mCmdDet != null)? mCmdMessage.mCmdDet.typeOfCommand : -1));
        return mCmdMessage;
    }

    public IccRecords getIccRecords() {
        return mIccRecords;
    }

    private static void saveCmdToPreference(Context context, String key, String cmd) {
        SharedPreferences preferences = null;
        Editor editor = null;
        synchronized (mLock) {
            CatLog.d("CatService", "saveCmdToPreference, key: " + key + ", cmd: " + cmd);
            preferences = context.getSharedPreferences("set_up_menu", Context.MODE_PRIVATE);
            editor = preferences.edit();
            editor.putString(key, cmd);
            editor.apply();
        }
    }

    private static String readCmdFromPreference(CatService inst, Context context, String key) {
        SharedPreferences preferences = null;
        String cmd = String.valueOf("");

        synchronized (mLock) {
            if (!inst.mReadFromPreferenceDone) {
                preferences = context.getSharedPreferences("set_up_menu", Context.MODE_PRIVATE);
                cmd = preferences.getString(key, "");
                inst.mReadFromPreferenceDone = true;
                CatLog.d("CatService", "readCmdFromPreference, key: " + key + ", cmd: " + cmd);
            } else {
                CatLog.d("CatService", "readCmdFromPreference, do not read again");
            }
        }
        if (cmd.length() == 0) {
            cmd = null;
        }
        return cmd;
    }
    public void setAllCallDisConn(boolean isDisConn) {
        if (false == FeatureOption.MTK_BSP_PACKAGE) {
            mIsAllCallDisConn = isDisConn;
        }
    }
    public boolean isCallDisConnReceived() {
        if (false == FeatureOption.MTK_BSP_PACKAGE) {
            return (0 < mEventDownloadCallDisConnInfo.size());
        } else {
            return false;
        }
    }
}
