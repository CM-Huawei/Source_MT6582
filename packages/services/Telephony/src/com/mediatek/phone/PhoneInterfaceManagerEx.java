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
 * Copyright (C) 2006 The Android Open Source Project
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

package com.mediatek.phone;

import android.app.AppOpsManager;

import android.os.AsyncResult;
import android.app.ActivityManager;
import android.os.Process;
import android.os.UserHandle;
import android.os.Bundle;
import android.os.Binder;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.SystemProperties;
import android.util.Log;

import com.android.internal.telephony.CommandException;
import com.android.internal.telephony.CallManager;
import com.android.internal.telephony.DefaultPhoneNotifier;
import com.android.internal.telephony.gemini.GeminiPhone;
import com.android.internal.telephony.IccCard;
import com.android.internal.telephony.IPhoneSubInfo;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.PhoneProxy;
import com.android.internal.telephony.uicc.IccFileHandler;
import com.android.internal.telephony.DefaultPhoneNotifier;
import com.android.internal.telephony.TelephonyProperties;
import com.android.internal.telephony.TelephonyIntents;

// NFC SEEK start
import com.android.internal.telephony.uicc.IccIoResult;
import com.android.internal.telephony.uicc.IccUtils;
// NFC SEEK end

import android.telephony.NeighboringCellInfo;
import android.telephony.CellInfo;
import android.telephony.ServiceState;

import com.android.phone.PhoneGlobals;
import com.mediatek.common.featureoption.FeatureOption;
import com.mediatek.common.telephony.ITelephonyEx;
import com.mediatek.phone.gemini.GeminiUtils;

import android.content.Intent;
import java.util.Iterator;
import java.util.List;
import java.util.ArrayList;

/// M: For the getInternationalCardType()'s dummy implementation.
import com.mediatek.common.telephony.internationalroaming.InternationalRoamingConstants;

/**
 * Implementation of the ITelephony interface.
 */
public class PhoneInterfaceManagerEx extends ITelephonyEx.Stub {

    private static final String LOG_TAG = "PhoneInterfaceManagerEx";
    private static final boolean DBG = true;

    /** The singleton instance. */
    private static PhoneInterfaceManagerEx sInstance;

    PhoneGlobals mApp;
    Phone mPhone;
    CallManager mCM;
    AppOpsManager mAppOps;
    MainThreadHandler mMainThreadHandler;

    // Query SIM phonebook Adn stroage info thread
    private QueryAdnInfoThread mAdnInfoThread = null;

    /* Query network lock start */

    // Verify network lock result.
    public static final int VERIFY_RESULT_PASS = 0;
    public static final int VERIFY_INCORRECT_PASSWORD = 1;
    public static final int VERIFY_RESULT_EXCEPTION = 2;

    // Total network lock count.
    public static final int NETWORK_LOCK_TOTAL_COUNT = 5;
    
    public static final String QUERY_SIMME_LOCK_RESULT = 
            "com.android.phone.QUERY_SIMME_LOCK_RESULT";

    public static final String SIMME_LOCK_LEFT_COUNT = 
            "com.android.phone.SIMME_LOCK_LEFT_COUNT";
    /* Query network lock end */

    /* SMS Center Address start*/
    private static final int CMD_HANDLE_GET_SCA = 11;
    private static final int CMD_GET_SCA_DONE = 12;
    private static final int CMD_HANDLE_SET_SCA = 13;
    private static final int CMD_SET_SCA_DONE = 14;
    /* SMS Center Address end*/

    /* 3G switch start */
    private ArrayList<Integer> m3GSwitchLocks = new ArrayList<Integer>();
    private static int m3GSwitchLockCounter;
    /* 3G switch end */

    // NFC SEEK start
    private static final int CMD_EXCHANGE_APDU = 15;
    private static final int EVENT_EXCHANGE_APDU_DONE = 16;
    private static final int CMD_OPEN_CHANNEL = 17;
    private static final int EVENT_OPEN_CHANNEL_DONE = 18;
    private static final int CMD_CLOSE_CHANNEL = 19;
    private static final int EVENT_CLOSE_CHANNEL_DONE = 20;
    private static final int CMD_SIM_IO = 21;
    private static final int EVENT_SIM_IO_DONE = 22;
    private static final int CMD_GET_ATR = 23;
    private static final int EVENT_GET_ATR_DONE = 24;
    private static final int CMD_OPEN_CHANNEL_WITH_SW = 25;
    private static final int EVENT_OPEN_CHANNEL_WITH_SW_DONE = 26;
    private int[] mLastError = new int[] {0,0,0,0};
    // NFC SEEK end

    //move from PhoneInterfaceManager for LEGO -- start
    private static final int CMD_HANDLE_NEIGHBORING_CELL = 27;
    private static final int EVENT_NEIGHBORING_CELL_DONE = 28;

    private String[] ITEL_PROPERTY_ICC_OPERATOR_ISO_COUNTRY = {
        TelephonyProperties.PROPERTY_ICC_OPERATOR_ISO_COUNTRY,
        TelephonyProperties.PROPERTY_ICC_OPERATOR_ISO_COUNTRY_2,
        TelephonyProperties.PROPERTY_ICC_OPERATOR_ISO_COUNTRY_3,
        TelephonyProperties.PROPERTY_ICC_OPERATOR_ISO_COUNTRY_4,
    };

    private String[] PHONE_SUBINFO_SERVICE = {
        "iphonesubinfo",
        "iphonesubinfo2",
        "iphonesubinfo3",
        "iphonesubinfo4",
    };
    //move from PhoneInterfaceManager for LEGO -- end

    /**
     * Initialize the singleton PhoneInterfaceManagerEx instance.
     * This is only done once, at startup, from PhoneGlobals.onCreate().
     */
    /* package */ 
    public static PhoneInterfaceManagerEx init(PhoneGlobals app, Phone phone) {
        synchronized (PhoneInterfaceManagerEx.class) {
            if (sInstance == null) {
                sInstance = new PhoneInterfaceManagerEx(app, phone);
            } else {
                Log.wtf(LOG_TAG, "init() called multiple times!  sInstance = " + sInstance);
            }
            return sInstance;
        }
    }

    /** Private constructor; @see init() */
    private PhoneInterfaceManagerEx(PhoneGlobals app, Phone phone) {
        mApp = app;
        mPhone = phone;
        mCM = PhoneGlobals.getInstance().mCM;
        mMainThreadHandler = new MainThreadHandler();
        publish();
    }

    private void publish() {
        if (DBG) log("publish: " + this);

        ServiceManager.addService("phoneEx", this);
    }

    private void log(String msg) {
        Log.d(LOG_TAG, "[PhoneIntfMgrEx] " + msg);
    }

    private void loge(String msg) {
        Log.e(LOG_TAG, "[PhoneIntfMgrEx] " + msg);
    }

    private static final class IccAPDUArgument {

        public int channel, cla, command, p1, p2, p3;
        public String pathId;
        public String data;
        public String pin2;
        public int simSlotId;

        public IccAPDUArgument(int cla, int command, int channel,
                int p1, int p2, int p3, String pathId, int simSlotId) {
            this.channel = channel;
            this.cla = cla;
            this.command = command;
            this.p1 = p1;
            this.p2 = p2;
            this.p3 = p3;
            this.pathId = pathId;
            data = null;
            pin2 = null;
            this.simSlotId = simSlotId;
        }
        
        public IccAPDUArgument(int cla, int command, int channel,
                int p1, int p2, int p3, String pathId, String data, String pin2,int simSlotId) {
            this.channel = channel;
            this.cla = cla;
            this.command = command;
            this.p1 = p1;
            this.p2 = p2;
            this.p3 = p3;
            this.pathId = pathId;
            this.data = data;
            this.pin2 = pin2;
            this.simSlotId = simSlotId;
        }
    }

    private class ScAddrGemini {
        public String scAddr;
        public int simId;

        public ScAddrGemini(String addr, int id) {
            this.scAddr = addr;
            if(id == PhoneConstants.GEMINI_SIM_1 ||
               id == PhoneConstants.GEMINI_SIM_2 ||
               id == PhoneConstants.GEMINI_SIM_3 ||
               id == PhoneConstants.GEMINI_SIM_4) {
                   simId = id;
               } else {
                   simId = PhoneConstants.GEMINI_SIM_1;
               }
        }
     }

    /**
     * A request object for use with {@link MainThreadHandler}. Requesters should wait() on the
     * request after sending. The main thread will notify the request when it is complete.
     */
    private static final class MainThreadRequest {
        /** The argument to use for the request */
        public Object argument;
        /** The result of the request that is run on the main thread */
        public Object result;

        public MainThreadRequest(Object argument) {
            this.argument = argument;
        }
    }

    /**
     * A handler that processes messages on the main thread in the phone process. Since many
     * of the Phone calls are not thread safe this is needed to shuttle the requests from the
     * inbound binder threads to the main thread in the phone process.  The Binder thread
     * may provide a {@link MainThreadRequest} object in the msg.obj field that they are waiting
     * on, which will be notified when the operation completes and will contain the result of the
     * request.
     *
     * <p>If a MainThreadRequest object is provided in the msg.obj field,
     * note that request.result must be set to something non-null for the calling thread to
     * unblock.
     */
    private final class MainThreadHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            MainThreadRequest request;
            Message onCompleted;
            AsyncResult ar;
            IccCard iccCard;
            int simSlotId;
            switch (msg.what) {
                case CMD_HANDLE_GET_SCA:
                    request = (MainThreadRequest)msg.obj;
                    onCompleted = obtainMessage(CMD_GET_SCA_DONE, request);

                    if(request.argument == null) {
                       // non-gemini
                    } else {
                       ScAddrGemini sca = (ScAddrGemini)request.argument;
                       int simId = sca.simId;

                       if(FeatureOption.MTK_GEMINI_SUPPORT) {
                           Log.d(LOG_TAG, "[sca get sc gemini");
                           ((GeminiPhone) mPhone).getPhonebyId(simId).getSmscAddress(onCompleted);
                       } else  {
                           Log.d(LOG_TAG, "[sca get sc single");
                           mPhone.getSmscAddress(onCompleted);
                       }
                    }
                    break;

                case CMD_GET_SCA_DONE:
                    ar = (AsyncResult)msg.obj;
                    request = (MainThreadRequest)ar.userObj;

                    if(ar.exception == null && ar.result != null) {
                            Log.d(LOG_TAG, "[sca get result");
                            request.result = ar.result;
                    } else {
                        Log.d(LOG_TAG, "[sca Fail to get sc address");
                            request.result = new String("");
                    }

                    synchronized(request) {
                            Log.d(LOG_TAG, "[sca notify sleep thread");
                            request.notifyAll();
                    }
                    break;

                case CMD_HANDLE_SET_SCA:
                    request = (MainThreadRequest)msg.obj;
                    onCompleted = obtainMessage(CMD_SET_SCA_DONE, request);

                    ScAddrGemini sca = (ScAddrGemini)request.argument;
                    if(sca.simId == -1) {
                            // non-gemini
                    } else {
                        if(FeatureOption.MTK_GEMINI_SUPPORT) {
                                    Log.d(LOG_TAG, "[sca set sc gemini");
                            ((GeminiPhone) mPhone).getPhonebyId(sca.simId).setSmscAddress(sca.scAddr, onCompleted);
                        } else {
                            Log.d(LOG_TAG, "[sca set sc single");
                            mPhone.setSmscAddress(sca.scAddr, onCompleted);
                        }
                    }
                    break;

                case CMD_SET_SCA_DONE:
                    ar = (AsyncResult)msg.obj;
                    request = (MainThreadRequest)ar.userObj;
                    if(ar.exception != null) {
                        Log.d(LOG_TAG, "[sca Fail: set sc address");
                    } else {
                        Log.d(LOG_TAG, "[sca Done: set sc address");
                    }
                    request.result = new Object();
        
                    synchronized(request) {
                        request.notifyAll();
                    }
                    break;
               // NFC SEEK start
               case CMD_SIM_IO:
                    request = (MainThreadRequest) msg.obj;
                    IccAPDUArgument parameters = (IccAPDUArgument) request.argument;

                    if (FeatureOption.MTK_GEMINI_SUPPORT == true) {
                        iccCard = ((GeminiPhone)mPhone).getPhonebyId(parameters.simSlotId).getIccCard() ;
                    } else {
                        iccCard = mPhone.getIccCard();
                    }

                    onCompleted = obtainMessage(EVENT_SIM_IO_DONE, parameters.simSlotId, 0 ,request);
                    iccCard.exchangeSimIo( parameters.cla, /* fileID */
                            parameters.command, parameters.p1, parameters.p2, parameters.p3,
                            parameters.pathId, parameters.data, parameters.pin2, onCompleted);
                    break;
               case EVENT_SIM_IO_DONE:
                    ar = (AsyncResult) msg.obj;
                    request = (MainThreadRequest) ar.userObj;
                    simSlotId = msg.arg1;

                    if (ar.exception == null && ar.result != null) {
                        request.result = ar.result;
                        mLastError[simSlotId] = 0;
                    } else {
                        request.result = new IccIoResult(0x6f, 0, (byte[])null);
                        mLastError[simSlotId] = 1;
                        if ((ar.exception != null) && (ar.exception instanceof CommandException)) {
                            if (((CommandException)ar.exception).getCommandError() ==
                                    CommandException.Error.INVALID_PARAMETER) {
                                mLastError[simSlotId] = 5;
                            }
                        }
                    }
                    synchronized (request) { 
                        request.notifyAll(); 
                    }
                    break;

                case CMD_EXCHANGE_APDU:
                    request = (MainThreadRequest) msg.obj;
                    IccAPDUArgument argument = (IccAPDUArgument) request.argument;

                    if (FeatureOption.MTK_GEMINI_SUPPORT == true) {
                        iccCard = ((GeminiPhone)mPhone).getPhonebyId(argument.simSlotId).getIccCard() ;
                    } else {
                        iccCard = mPhone.getIccCard();
                    }

                    onCompleted = obtainMessage(EVENT_EXCHANGE_APDU_DONE, argument.simSlotId, 0 ,request);

                    iccCard.exchangeApdu(argument.cla, argument.command, argument.channel, 
                            argument.p1, argument.p2, argument.p3, argument.data, onCompleted);
                   break;

                case EVENT_EXCHANGE_APDU_DONE:
                    ar = (AsyncResult) msg.obj;
                    request = (MainThreadRequest) ar.userObj;
                    simSlotId = msg.arg1;
                    if (ar.exception == null && ar.result != null) {
                        request.result = ar.result;
                        mLastError[simSlotId] = 0;
                    } else {
                        request.result = new IccIoResult(0x6f, 0, (byte[])null);
                        mLastError[simSlotId] = 1;
                        if ((ar.exception != null) && (ar.exception instanceof CommandException)) {
                            if (((CommandException)ar.exception).getCommandError()
                                    == CommandException.Error.INVALID_PARAMETER) {
                               mLastError[simSlotId] = 5;
                           }
                       }
                   }
                   synchronized (request) {
                       request.notifyAll();
                   }
                   break;

                case CMD_OPEN_CHANNEL:
                    request = (MainThreadRequest) msg.obj;
                    Bundle openChannel = (Bundle)request.argument;
                    String aid = openChannel.getString("AID", "");
                    simSlotId = openChannel.getInt("SimSlotId", 0);

                    log("SIM: " + simSlotId + ",aid = " + aid);

                    if (FeatureOption.MTK_GEMINI_SUPPORT == true) {
                        iccCard = ((GeminiPhone)mPhone).getPhonebyId(simSlotId).getIccCard() ;
                    } else {
                        iccCard = mPhone.getIccCard();
                    }

                    onCompleted = obtainMessage(EVENT_OPEN_CHANNEL_DONE, simSlotId, 0,request);
                    iccCard.openLogicalChannel(aid, onCompleted);
                    break;

                case EVENT_OPEN_CHANNEL_DONE:
                    ar = (AsyncResult) msg.obj;
                    request = (MainThreadRequest) ar.userObj;
                    simSlotId = msg.arg1;
                    if (ar.exception == null && ar.result != null) {
                        request.result = new Integer(((int[])ar.result)[0]);
                        mLastError[simSlotId] = 0;
                    } else {
                        request.result = new Integer(0);
                        mLastError[simSlotId] = 1;
                        if ((ar.exception != null) && (ar.exception instanceof CommandException)) {
                            if (((CommandException)ar.exception).getCommandError()
                                    == CommandException.Error.MISSING_RESOURCE) {
                                mLastError[simSlotId] = 2;
                            } else {
                                if (((CommandException)ar.exception).getCommandError()
                                    == CommandException.Error.NO_SUCH_ELEMENT) {
                                    mLastError[simSlotId] = 3;
                                }
                            }
                        }
                    }
                    synchronized (request) {
                        request.notifyAll();
                    }
                    break;

                case CMD_CLOSE_CHANNEL:
                    request = (MainThreadRequest) msg.obj;
                    Bundle closeChannel = (Bundle)request.argument;
                    int channel = closeChannel.getInt("Channel", 0);
                    simSlotId = closeChannel.getInt("SimSlotId", 0);

                    log("SIM: " + simSlotId + ",channel = " + channel);

                    if (FeatureOption.MTK_GEMINI_SUPPORT == true) {
                        iccCard = ((GeminiPhone)mPhone).getPhonebyId(simSlotId).getIccCard() ;
                    } else {
                        iccCard = mPhone.getIccCard();
                    }

                    onCompleted = obtainMessage(EVENT_CLOSE_CHANNEL_DONE, simSlotId, 0,request);
                    iccCard.closeLogicalChannel(channel, onCompleted);
                    break;

                case EVENT_CLOSE_CHANNEL_DONE:
                    ar = (AsyncResult) msg.obj;
                    request = (MainThreadRequest) ar.userObj;
                    simSlotId = msg.arg1;
                    if (ar.exception == null) {
                        request.result = new Integer(0);
                        mLastError[simSlotId] = 0;
                    } else {
                        request.result = new Integer(-1);
                        mLastError[simSlotId] = 1;
                        if ((ar.exception != null) && (ar.exception instanceof CommandException)) {
                            if (((CommandException)ar.exception).getCommandError()
                                     == CommandException.Error.INVALID_PARAMETER) {
                                mLastError[simSlotId] = 5;
                            }
                        }
                    }
                    synchronized (request) {
                        request.notifyAll();
                    }
                    break;

                case CMD_GET_ATR:
                    request = (MainThreadRequest) msg.obj;
                    simSlotId = ((Integer)request.argument).intValue();

                    log("SIM: " + simSlotId);

                    if (FeatureOption.MTK_GEMINI_SUPPORT == true) {
                        iccCard = ((GeminiPhone)mPhone).getPhonebyId(simSlotId).getIccCard() ;
                    } else {
                        iccCard = mPhone.getIccCard();
                    }

                    onCompleted = obtainMessage(EVENT_GET_ATR_DONE, simSlotId, 0,request);
                    iccCard.iccGetAtr(onCompleted);
                    break;

                case EVENT_GET_ATR_DONE:
                    ar = (AsyncResult) msg.obj;
                    request = (MainThreadRequest) ar.userObj;
                    simSlotId = msg.arg1;
                    if(ar.exception == null) {
                        request.result = ar.result;
                        mLastError[simSlotId] = 0;
                    } else {
                        request.result = "";
                        mLastError[simSlotId] = 1;
                    }
                    synchronized (request) {
                        request.notifyAll();
                    }
                    break;

                case CMD_OPEN_CHANNEL_WITH_SW:
                    request = (MainThreadRequest) msg.obj;
                    IccAPDUArgument argument3 = (IccAPDUArgument) request.argument;

                    if (FeatureOption.MTK_GEMINI_SUPPORT == true) {
                        iccCard = ((GeminiPhone)mPhone).getPhonebyId(argument3.simSlotId).getIccCard() ;
                    } else {
                        iccCard = mPhone.getIccCard();
                    }

                    onCompleted = obtainMessage(EVENT_OPEN_CHANNEL_WITH_SW_DONE, argument3.simSlotId, 0 ,request);

                    iccCard.openLogicalChannelWithSw(argument3.data, onCompleted);
                    break;

                case EVENT_OPEN_CHANNEL_WITH_SW_DONE:
                    ar = (AsyncResult) msg.obj;
                    request = (MainThreadRequest) ar.userObj;
                    request.result = ar.result;
                    simSlotId = msg.arg1;
                   if (ar.exception == null && ar.result != null) {
                       mLastError[simSlotId] = 0;
                   } else {
                       request.result = new IccIoResult(0xff, 0xff, (byte[])null);
                       mLastError[simSlotId] = 1;
                   }
                   synchronized (request) {
                       request.notifyAll();
                   }
                   break;
               // NFC SEEK end

                //move from PhoneInterfaceManager for LEGO -- start
                case CMD_HANDLE_NEIGHBORING_CELL:
                    request = (MainThreadRequest) msg.obj;
                    onCompleted = obtainMessage(EVENT_NEIGHBORING_CELL_DONE,
                            request);

                    //MTK-START [mtk04070][111117][ALPS00093395]MTK modified
                    if (request.argument == null) {
                    mPhone.getNeighboringCids(onCompleted);
                    } else {
                        Integer simId = (Integer)request.argument;
                        ((GeminiPhone)mPhone).getPhonebyId(simId.intValue()).getNeighboringCids(onCompleted);						
                    }                   
                    //MTK-END [mtk04070][111117][ALPS00093395]MTK modified
                    break; 

                case EVENT_NEIGHBORING_CELL_DONE:
                    ar = (AsyncResult) msg.obj;
                    request = (MainThreadRequest) ar.userObj;
                    if (ar.exception == null && ar.result != null) {
                        request.result = ar.result;
                    } else {
                        // create an empty list to notify the waiting thread
                        request.result = new ArrayList<NeighboringCellInfo>();
                    }
                    // Wake up the requesting thread
                    synchronized (request) {
                        request.notifyAll();
                    }
                    break;
                    //move from PhoneInterfaceManager for LEGO -- end
            }
        }
    }

    /**
     * Posts the specified command to be executed on the main thread,
     * waits for the request to complete, and returns the result.
     * @see sendRequestAsync
     */
    private Object sendRequest(int command, Object argument) {
        if (Looper.myLooper() == mMainThreadHandler.getLooper()) {
            throw new RuntimeException("This method will deadlock if called from the main thread.");
        }

        MainThreadRequest request = new MainThreadRequest(argument);
        Message msg = mMainThreadHandler.obtainMessage(command, request);
        msg.sendToTarget();

        // Wait for the request to complete
        synchronized (request) {
            while (request.result == null) {
                try {
                    request.wait();
                } catch (InterruptedException e) {
                    // Do nothing, go back and wait until the request is complete
                }
            }
        }
        return request.result;
    }

    private static class UnlockSim extends Thread {

        private final IccCard mSimCard;

        private boolean mDone = false;
        private boolean mResult = false;

        // For replies from SimCard interface
        private Handler mHandler;

        private static final int QUERY_NETWORK_STATUS_COMPLETE = 100;
        private static final int SUPPLY_NETWORK_LOCK_COMPLETE = 101;

        private int mVerifyResult = -1;
        private int mSIMMELockRetryCount = -1;

        public UnlockSim(IccCard simCard) {
            mSimCard = simCard;
        }

        @Override
        public void run() {
            Looper.prepare();
            synchronized (UnlockSim.this) {
                mHandler = new Handler() {
                    @Override
                    public void handleMessage(Message msg) {
                        AsyncResult ar = (AsyncResult) msg.obj;
                        switch (msg.what) {
                            case QUERY_NETWORK_STATUS_COMPLETE:
                                synchronized (UnlockSim.this) {
                                    int [] LockState = (int [])ar.result;
                                    if (ar.exception != null) { //Query exception occurs
                                        Log.d (LOG_TAG, "Query network lock fail");
                                        mResult = false;
                                        mDone = true;  
                                    }else{
                                        mSIMMELockRetryCount = LockState[2];
                                        Log.d (LOG_TAG, "[SIMQUERY] Category = " + LockState[0] 
                                            + " ,Network status =" + LockState[1] 
                                            + " ,Retry count = " + LockState[2]);
                                        
                                        mDone = true;
                                        mResult = true;
                                        UnlockSim.this.notifyAll();
                                    }
                                }
                                break;
                            case SUPPLY_NETWORK_LOCK_COMPLETE:
                                Log.d(LOG_TAG, "SUPPLY_NETWORK_LOCK_COMPLETE");
                                synchronized (UnlockSim.this) {
                                    if ((ar.exception != null) &&
                                           (ar.exception instanceof CommandException)) {
                                        Log.d(LOG_TAG, "ar.exception " + ar.exception);
                                        if (((CommandException)ar.exception).getCommandError()
                                            == CommandException.Error.PASSWORD_INCORRECT) {
                                            mVerifyResult = VERIFY_INCORRECT_PASSWORD;
                                       } else {
                                            mVerifyResult = VERIFY_RESULT_EXCEPTION;
                                       }
                                    } else {
                                        mVerifyResult = VERIFY_RESULT_PASS;
                                    }
                                    mDone = true;
                                    UnlockSim.this.notifyAll();
                                }
                                break;
                        }
                    }
                };
                UnlockSim.this.notifyAll();
            }
            Looper.loop();
        }

        synchronized Bundle queryNetworkLock(int category) {

            while (mHandler == null) {
                try {
                    wait();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }

            Log.d(LOG_TAG, "Enter queryNetworkLock");
            Message callback = Message.obtain(mHandler, QUERY_NETWORK_STATUS_COMPLETE);
            mSimCard.QueryIccNetworkLock(category,4,null,null,null,null,callback);

            while (!mDone) {
                try {
                    Log.d(LOG_TAG, "wait for done");
                    wait();
                } catch (InterruptedException e) {
                    // Restore the interrupted status
                    Thread.currentThread().interrupt();
                }
            }

            Bundle bundle = new Bundle();
            bundle.putBoolean(QUERY_SIMME_LOCK_RESULT, mResult);
            bundle.putInt(SIMME_LOCK_LEFT_COUNT, mSIMMELockRetryCount);
            
            Log.d(LOG_TAG, "done");
            return bundle;
        }

        synchronized int supplyNetworkLock(String strPasswd) {

            while (mHandler == null) {
                try {
                    wait();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }

            Log.d(LOG_TAG, "Enter supplyNetworkLock");
            Message callback = Message.obtain(mHandler, SUPPLY_NETWORK_LOCK_COMPLETE);
            mSimCard.supplyNetworkDepersonalization(strPasswd, callback);

            while (!mDone) {
                try {
                    Log.d(LOG_TAG, "wait for done");
                    wait();
                } catch (InterruptedException e) {
                    // Restore the interrupted status
                    Thread.currentThread().interrupt();
                }
            }
            
            Log.d(LOG_TAG, "done");
            return mVerifyResult;
        }      
    }


    public Bundle queryNetworkLock(int category, int simId) {
        final UnlockSim queryNetworkLockState;

        Log.d(LOG_TAG, "queryNetworkLock");        
        if(false == FeatureOption.MTK_GEMINI_SUPPORT) {
            queryNetworkLockState = new UnlockSim(mPhone.getIccCard());
        } else {
            queryNetworkLockState = new UnlockSim(((GeminiPhone)mPhone).getPhonebyId(simId).getIccCard());
        }
        queryNetworkLockState.start();
        return queryNetworkLockState.queryNetworkLock(category);
    }

    public int supplyNetworkDepersonalization(String strPasswd, int simId) {
        final UnlockSim supplyNetworkLock;

        Log.d(LOG_TAG, "supplyNetworkDepersonalization");    
        if(false == FeatureOption.MTK_GEMINI_SUPPORT) {
            supplyNetworkLock = new UnlockSim(mPhone.getIccCard());
        } else {
            supplyNetworkLock = new UnlockSim(((GeminiPhone)mPhone).getPhonebyId(simId).getIccCard());
        }
        supplyNetworkLock.start();
        return supplyNetworkLock.supplyNetworkLock(strPasswd);
    }  

   /**
    * This function is used to get SIM phonebook storage information
    * by sim id.
    *
    * @param simId Indicate which sim(slot) to query
    * @return int[] which incated the storage info
    *         int[0]; // # of remaining entries
    *         int[1]; // # of total entries
    *         int[2]; // # max length of number
    *         int[3]; // # max length of alpha id
    *
    */ 
    public int[] getAdnStorageInfo(int simId) {
        Log.d(LOG_TAG, "getAdnStorageInfo " + simId);
        if (mAdnInfoThread == null) {
            Log.d(LOG_TAG, "getAdnStorageInfo new thread ");
            mAdnInfoThread  = new QueryAdnInfoThread(simId,mPhone);
            mAdnInfoThread.start();
        } else {
            mAdnInfoThread.setSimId(simId);
            Log.d(LOG_TAG, "getAdnStorageInfo old thread ");
        }
        return mAdnInfoThread.GetAdnStorageInfo(); 
    }   

    private static class QueryAdnInfoThread extends Thread {
    
        private int mSimId;
        private boolean mDone = false;
        private int[] recordSize;
    
        private Handler mHandler;
            
        Phone myPhone;
        // For async handler to identify request type
        private static final int EVENT_QUERY_PHB_ADN_INFO = 100;
    
        public QueryAdnInfoThread(int simId, Phone myP) {
            mSimId = simId;
               
            myPhone = myP;
        }
        public void setSimId(int simId) {
            mSimId = simId;
            mDone = false;
        }
        
        @Override
        public void run() {
            Looper.prepare();
            synchronized (QueryAdnInfoThread.this) {
                mHandler = new Handler() {
                    @Override
                    public void handleMessage(Message msg) {
                        AsyncResult ar = (AsyncResult) msg.obj;
                          
                        switch (msg.what) {
                            case EVENT_QUERY_PHB_ADN_INFO:
                                Log.d(LOG_TAG, "EVENT_QUERY_PHB_ADN_INFO");
                                synchronized (QueryAdnInfoThread.this) {
                                    mDone = true;
                                    int[] info = (int[]) (ar.result);
                                    if(info!=null){
                                        recordSize = new int[4];
                                        recordSize[0] = info[0]; // # of remaining entries
                                        recordSize[1] = info[1]; // # of total entries
                                        recordSize[2] = info[2]; // # max length of number
                                        recordSize[3] = info[3]; // # max length of alpha id
                                        Log.d(LOG_TAG,"recordSize[0]="+ recordSize[0]+",recordSize[1]="+ recordSize[1] +
                                                         "recordSize[2]="+ recordSize[2]+",recordSize[3]="+ recordSize[3]);
                                    }
                                    else {
                                        recordSize = new int[4];
                                        recordSize[0] = 0; // # of remaining entries
                                        recordSize[1] = 0; // # of total entries
                                        recordSize[2] = 0; // # max length of number
                                        recordSize[3] = 0; // # max length of alpha id                                           
                                    }
                                    QueryAdnInfoThread.this.notifyAll();
                                      
                                }
                                break;
                            }
                      }
                };
                QueryAdnInfoThread.this.notifyAll();
            }
            Looper.loop();
        }
    
        public int[] GetAdnStorageInfo() {   
            synchronized (QueryAdnInfoThread.this) { 
                while (mHandler == null) {
                    try {                
                        QueryAdnInfoThread.this.wait();
                          
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
                Message response = Message.obtain(mHandler, EVENT_QUERY_PHB_ADN_INFO);
                  
                // protected PhoneBase myPhone = (GeminiPhone)mPhone.getPhonebyId(mSimId);
                IccFileHandler filehandle = null;
                if (FeatureOption.MTK_GEMINI_SUPPORT == true){
                	filehandle = ((PhoneProxy)(((GeminiPhone)myPhone).getPhonebyId(QueryAdnInfoThread.this.mSimId))).getIccFileHandler();
                }
                else {
                	filehandle =((PhoneProxy) myPhone).getIccFileHandler();
                }

                if (filehandle != null) {
                    filehandle.getPhbRecordInfo(response);
                } else {
                    Log.d(LOG_TAG, "GetAdnStorageInfo: filehandle is null.");
                    return null;
                }

                while (!mDone) {
                    try {
                        Log.d(LOG_TAG, "wait for done");
                        QueryAdnInfoThread.this.wait();                    
                    } catch (InterruptedException e) {
                        // Restore the interrupted status
                        Thread.currentThread().interrupt();
                    }
                }
                Log.d(LOG_TAG, "done");
                return recordSize;
            }
        }
    }

   /**
    * This function is used to check if the SIM phonebook is ready
    * by sim id.
    *
    * @param simId Indicate which sim(slot) to query
    * @return true if phone book is ready. 
    * 
    */ 
    public boolean isPhbReady(int simId){
        String strPhbReady = "false";
        if (PhoneConstants.GEMINI_SIM_2 == simId) {
            strPhbReady = SystemProperties.get("gsm.sim.ril.phbready.2", "false");
        } else if (PhoneConstants.GEMINI_SIM_3 == simId) {
            strPhbReady = SystemProperties.get("gsm.sim.ril.phbready.3", "false");
        } else if (PhoneConstants.GEMINI_SIM_4 == simId) {
            strPhbReady = SystemProperties.get("gsm.sim.ril.phbready.4", "false");
        } else {
            strPhbReady = SystemProperties.get("gsm.sim.ril.phbready", "false");
        }   
        
        log("[isPhbReady] sim id:" + simId + ", isPhbReady: " + strPhbReady);
        
        return strPhbReady.equals("true");
    }

    /**
     * @return SMS default SIM.
     * @internal
     */
    public int getSmsDefaultSim() {
        if (FeatureOption.MTK_GEMINI_ENHANCEMENT == true) {
            return ((GeminiPhone)mPhone).getSmsDefaultSim();
        } else {
            return SystemProperties.getInt(PhoneConstants.GEMINI_DEFAULT_SIM_PROP, PhoneConstants.GEMINI_SIM_1);
        }
    }
    
    /**
     * @internal
     */   
    public String getScAddressGemini(int simId) {
            Log.d(LOG_TAG, "getScAddressGemini: enter");
        if(simId != PhoneConstants.GEMINI_SIM_1 &&
           simId != PhoneConstants.GEMINI_SIM_2 &&
           simId != PhoneConstants.GEMINI_SIM_3 &&
           simId != PhoneConstants.GEMINI_SIM_4)
        {
                Log.d(LOG_TAG, "[sca Invalid sim id");
                return null;
        }

        final ScAddrGemini addr = new ScAddrGemini(null, simId);

        Thread sender = new Thread() {
          public void run() {
                  try {
                  addr.scAddr = (String)sendRequest(CMD_HANDLE_GET_SCA, addr);
              } catch(RuntimeException e) {
                  Log.e(LOG_TAG, "[sca getScAddressGemini " + e);
              }
          }
        };
        sender.start();
        try {
          Log.d(LOG_TAG, "[sca thread join");
          sender.join();
        } catch(InterruptedException e) {
        Log.d(LOG_TAG, "[sca throw interrupted exception");
        }

        Log.d(LOG_TAG, "getScAddressGemini: exit with " + addr.scAddr);

        return addr.scAddr;
    }

    /**
     * @internal
     */   
    public void setScAddressGemini(String address, int simId) {
        Log.d(LOG_TAG, "setScAddressGemini: enter");
        if(simId != PhoneConstants.GEMINI_SIM_1 &&
           simId != PhoneConstants.GEMINI_SIM_2 &&
           simId != PhoneConstants.GEMINI_SIM_3 &&
           simId != PhoneConstants.GEMINI_SIM_4)
        {
                Log.d(LOG_TAG, "[sca Invalid sim id");
                return;
        }

        final ScAddrGemini addr = new ScAddrGemini(address, simId);

        Thread sender = new Thread() {
                public void run() {
                        try {
                        addr.scAddr = (String)sendRequest(CMD_HANDLE_SET_SCA, addr);
                } catch(RuntimeException e) {
            Log.e(LOG_TAG, "[sca setScAddressGemini " + e);
                }
                }
        };
        sender.start();
        try {
                Log.d(LOG_TAG, "[sca thread join");
                sender.join();
        } catch(InterruptedException e) {
           Log.d(LOG_TAG, "[sca throw interrupted exception");
        }

        Log.d(LOG_TAG, "setScAddressGemini: exit");
    }

    /**
     * Modem SML change feature
     */
    public void repollIccStateForNetworkLock(int simId, boolean needIntent) { 
        if(false == FeatureOption.MTK_GEMINI_SUPPORT) {
            Log.e(LOG_TAG, "Not Support in Single SIM.");
        } else {
            ((GeminiPhone)mPhone).getPhonebyId(simId).getIccCard().repollIccStateForModemSmlChangeFeatrue(needIntent);
        }
    }
    
    public boolean isAirplanemodeAvailableNow() {
        if(FeatureOption.MTK_GEMINI_SUPPORT == true) {
            return !((GeminiPhone)mPhone).isSwitching3GCapability();
        } else {
            return true;
        }
    }

    private static class SetMsisdn extends Thread {
        private int mSimId;
        private Phone myPhone;
        private boolean mDone = false;
        private int mResult = 0;
        private Handler mHandler;

        private static final String DEFAULT_ALPHATAG = "Default Tag";
        private static final int CMD_SET_MSISDN_COMPLETE = 100;


        public SetMsisdn(Phone myP, int simId) {
            mSimId = simId;
            myPhone = myP;
        }


        @Override
        public void run() {
            Looper.prepare();
            synchronized (SetMsisdn.this) {
                mHandler = new Handler() {
                    @Override
                    public void handleMessage(Message msg) {
                        AsyncResult ar = (AsyncResult) msg.obj;
                        switch (msg.what) {
                            case CMD_SET_MSISDN_COMPLETE:
                                synchronized (SetMsisdn.this) {
                                    if (ar.exception != null) { //Query exception occurs
                                        Log.d (LOG_TAG, "Set msisdn fail");
                                        mDone = true;
                                        mResult = 0;
                                    }else{
                                        Log.d (LOG_TAG, "Set msisdn success");
                                        mDone = true;
                                        mResult = 1;
                                    }
                                    SetMsisdn.this.notifyAll();
                                }
                                break;
                        }
                    }
                };
                SetMsisdn.this.notifyAll();
            }
            Looper.loop();
        }

        synchronized int setLine1Number(String alphaTag, String number) {

            while (mHandler == null) {
                try {
                    wait();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }

            Log.d(LOG_TAG, "Enter setLine1Number");
            Message callback = Message.obtain(mHandler, CMD_SET_MSISDN_COMPLETE);
            String myTag = alphaTag;

            if (FeatureOption.MTK_GEMINI_SUPPORT == true){
                if(myTag == null) {
                    myTag = ((GeminiPhone)myPhone).getPhonebyId(mSimId).getLine1AlphaTag();
                }
            } else {
                if(myTag == null) {
                    myTag = ((PhoneProxy)myPhone).getLine1AlphaTag();
                }
            }

            if(myTag == null || myTag.equals("")) {
                myTag = DEFAULT_ALPHATAG;
            }

            Log.d(LOG_TAG, "SIM" + mSimId + ":Tag = " + myTag + " ,number = " + number);

            if (FeatureOption.MTK_GEMINI_SUPPORT == true){
                ((GeminiPhone)myPhone).getPhonebyId(mSimId).setLine1Number(myTag, number, callback);
            } else {
                ((PhoneProxy) myPhone).setLine1Number(myTag, number, callback);
            }


            while (!mDone) {
                try {
                    Log.d(LOG_TAG, "wait for done");
                    wait();
                } catch (InterruptedException e) {
                    // Restore the interrupted status
                    Thread.currentThread().interrupt();
                }
            }

            Log.d(LOG_TAG, "done");
            return mResult;
        }
    }

    public int setLine1Number(String alphaTag, String number, int simId){

        if(number == null) {
            Log.d(LOG_TAG, "number = null");
            return 0;
        }
        if(simId < 0 || simId > PhoneConstants.GEMINI_SIM_NUM) {
            Log.d(LOG_TAG, "Error simId: " + simId);
            return 0;
        }

        final SetMsisdn setMsisdn;
        Log.d(LOG_TAG, "setLine1Number");

        setMsisdn = new SetMsisdn(mPhone, simId);
        setMsisdn.start();

        return setMsisdn.setLine1Number(alphaTag, number);
    }

   /**
    * Return true if the FDN of the ICC card is enabled
    */
    public boolean isFdnEnabled(int simId) {
        log("isFdnEnabled  simId=" + simId);
        if (FeatureOption.MTK_GEMINI_SUPPORT == true) {
            return ((GeminiPhone) mPhone).getPhonebyId(simId).getIccCard().getIccFdnEnabled();
        } else {
            return mPhone.getIccCard().getIccFdnEnabled();
        }
    }

    /**
     * Query if icc card is existed or not.
     * @param simId Indicate which sim(slot) to query
     * @return true if exists an icc card in given slot.
     */
    public boolean hasIccCard(int simId) {
        log("hasIccCard  simId=" + simId);
        if (FeatureOption.MTK_GEMINI_SUPPORT == true) {
            return ((GeminiPhone) mPhone).getPhonebyId(simId).getIccCard().hasIccCard();
        } else {
            return mPhone.getIccCard().hasIccCard();
        }
    }

    public String getIccCardType(int simId) {
        log("getIccCardType  simId=" + simId);
        if (FeatureOption.MTK_GEMINI_SUPPORT == true) {
            return ((GeminiPhone) mPhone).getPhonebyId(simId).getIccCard().getIccCardType();
        } else {
            return mPhone.getIccCard().getIccCardType();
        }
    }

     /*Add by mtk80372 for Barcode number*/
     public String getSerialNumber(){
         return SystemProperties.get("gsm.serial");
     }

    /**
     * Return ture if the ICC card is a test card
     * @hide
     */
    public boolean isTestIccCard(int simId) {
        String imsi;
        if (FeatureOption.MTK_GEMINI_SUPPORT == true) {
            imsi =((GeminiPhone)mPhone).getPhonebyId(simId).getSubscriberId();
        } else {
            imsi = mPhone.getSubscriberId();
        }

        log("isTestIccCard  simId=" + simId + ", IMSI = " + imsi);

        if (imsi != null) {
            return imsi.substring(0, 5).equals("00101");
        } else {
            return false;
        }

    }

    // NFC SEEK start
    public byte[] transmitIccSimIO(int fileID, int command,
                                               int p1, int p2, int p3, String filePath, int simSlotId) {
        log("NFC test for transmitIccSimIO");
        log("Exchange SIM_IO " + fileID + ":" + command + " " +
                 p1 + " " + p2 + " " + p3 + ":" + filePath);

        int simId = PhoneConstants.GEMINI_SIM_1;
        if (FeatureOption.MTK_GEMINI_SUPPORT == true) {
            simId = simSlotId;
        }
        log("NFC test for transmitIccSimIO: SIM " + simId);

        IccIoResult response = (IccIoResult)sendRequest(CMD_SIM_IO,
                new IccAPDUArgument(fileID, command, -1,
                p1, p2, p3, filePath, simId));

        log("Exchange SIM_IO [R]" + response);
        byte[] result = null;
        int length=2;
        if (response.payload != null) {
            length=2+response.payload.length;
            result=new byte[length];
            System.arraycopy(response.payload,0,result,0,response.payload.length);
        } else result=new byte[length];
        log("Exchange SIM_IO [L] "+length);
        result[length-1]=(byte)response.sw2;
        result[length-2]=(byte)response.sw1;
        return result;
    }


    // [mtk02772] add data argument for UIM, set meId
    public byte[] transmitIccSimIoEx(int fileID, int command,
                                           int p1, int p2, int p3, String filePath, String data, String pin2, int simSlotId) {
        int simId = PhoneConstants.GEMINI_SIM_1;
        if (FeatureOption.MTK_GEMINI_SUPPORT == true) {
            simId = simSlotId;
        }
        log("Exchange SIM_IO Gemini " + fileID + ":" + command + " " +
                 p1 + " " + p2 + " " + p3 + ":" + filePath + ", " + data + ", " + pin2 + ", simId = " + simId);

        IccIoResult response =
                (IccIoResult)sendRequest(CMD_SIM_IO,
                        new IccAPDUArgument(fileID, command, -1,
                        p1, p2, p3, filePath, data, pin2, simId));

        log("Exchange SIM_IO Gemini [R]" + response);
        byte[] result=null; int length=2;
        if (response.payload != null) {
            length=2+response.payload.length;
            result=new byte[length];
            System.arraycopy(response.payload,0,result,0,response.payload.length);
        } else result=new byte[length];
        log("Exchange SIM_IO Gemini [L] "+length);
        result[length-1]=(byte)response.sw2;
        result[length-2]=(byte)response.sw1;
        return result;
    }


    private String exchangeIccApdu(int cla, int command,
            int channel, int p1, int p2, int p3, String data, int simSlotId) {

        log("NFC test for exchangeIccApdu");
        log("> exchangeApdu " + channel + " " + cla + " " +
                command + " " + p1 + " " + p2 + " " + p3 + " " + data);

        int simId = PhoneConstants.GEMINI_SIM_1;
        if (FeatureOption.MTK_GEMINI_SUPPORT == true) {
            simId = simSlotId;
        }
        log("NFC test for exchangeIccApdu: SimId = " + simId);

        IccIoResult response = (IccIoResult)sendRequest(CMD_EXCHANGE_APDU,
                        new IccAPDUArgument(cla, command, channel,
                        p1, p2, p3, null, data, null, simId));
        log("< exchangeApdu " + response);
        String s = Integer.toHexString(
                (response.sw1 << 8) + response.sw2 + 0x10000).substring(1);
        if (response.payload != null)
            s = IccUtils.bytesToHexString(response.payload) + s;
        return s;
    }

    public String transmitIccBasicChannel(int cla, int command,
            int p1, int p2, int p3, String data, int simSlotId) {
        log("NFC test for transmitIccBasicChannel");
        return exchangeIccApdu(cla, command, 0, p1, p2, p3, data, simSlotId);
    }

    public String transmitIccLogicalChannel(int cla, int command,
            int channel, int p1, int p2, int p3, String data, int simSlotId) {
        log("NFC test for transmitIccLogicalChannel");
        return exchangeIccApdu(cla, command, channel, p1, p2, p3, data, simSlotId);
    }
 
    public int openIccLogicalChannel(String AID, int simSlotId) {
        log("NFC test for openIccLogicalChannel");

        int simId = PhoneConstants.GEMINI_SIM_1;
        if (FeatureOption.MTK_GEMINI_SUPPORT == true) {
            simId = simSlotId;
        }
        log("> openIccLogicalChannel " + AID + ",SimId = " + simId);

        Bundle extraInfo = new Bundle();
        extraInfo.putString("AID", AID);
        extraInfo.putInt("SimSlotId", simId);

        Integer channel = (Integer)sendRequest(CMD_OPEN_CHANNEL, extraInfo);
        Log.d(LOG_TAG, "< openIccLogicalChannel " + channel);
        return channel.intValue();
    }

    public boolean closeIccLogicalChannel(int channel, int simSlotId) {
        log("NFC test for closeIccLogicalChannel");

        int simId = PhoneConstants.GEMINI_SIM_1;
        if (FeatureOption.MTK_GEMINI_SUPPORT == true) {
            simId = simSlotId;
        }
        log("> closeIccLogicalChannel " + channel + ",SimId = " + simId);

        Bundle extraInfo = new Bundle();
        extraInfo.putInt("Channel", channel);
        extraInfo.putInt("SimSlotId", simId);

        Integer err = (Integer)sendRequest(CMD_CLOSE_CHANNEL, extraInfo);
        Log.d(LOG_TAG, "< closeIccLogicalChannel " + err +", " + err.intValue());
        String strrr = err.toString();
        Log.d(LOG_TAG, "< closeIccLogicalChannel2 " + strrr);
        if(err.intValue() == 0)
            return true;
        return false;
    }

    public int getLastError(int simSlotId) {
        log("getLastError parameter " + simSlotId + " error");
        if (FeatureOption.MTK_GEMINI_SUPPORT == true) {
            return mLastError[simSlotId];
        } else {
            return mLastError[PhoneConstants.GEMINI_SIM_1];
        }
    }

    public String getIccAtr(int simSlotId) {
        log("NFC test for getIccAtr");

        int simId = PhoneConstants.GEMINI_SIM_1;
        if (FeatureOption.MTK_GEMINI_SUPPORT == true) {
            simId = simSlotId;
        }

        Log.d(LOG_TAG, "> getIccAtr " + ",SimId = " + simId);
        String response = (String)sendRequest(CMD_GET_ATR, new Integer(simId));
        Log.d(LOG_TAG, "< getIccAtr: " + response);
        return response;
    }

    /**
     * @return byte[] = {0x00} if open channel fail;
     * byte[] = {0x00, sw1, sw2}, if select AID fail;
     * byte[] = {0x00, ..., sw1, sw2}, if select AID fail;
     * byte[] = {0x0X, 0x90, 0x00}, 1 <= X <= 3, if select AID successfully;
     * byte[] = {0x0X, ..., 0x90, 0x00}, 1 <= X <= 3, if select AID successfully;
     */
    public byte[] openIccLogicalChannelWithSw(String AID, int simSlotId) {
        log("NFC test for openIccLogicalChannelWithSw");

        int simId = PhoneConstants.GEMINI_SIM_1;
        if (FeatureOption.MTK_GEMINI_SUPPORT == true) {
            simId = simSlotId;
        }

        Log.d(LOG_TAG, "> openIccLogicalChannelWithSw " + AID + ",SimId = " + simId);

        IccIoResult response = (IccIoResult)sendRequest(CMD_OPEN_CHANNEL_WITH_SW,
                        new IccAPDUArgument(0, 0, 0, 0, 0, 0, null, AID, null, simId));
        if(response.sw1 == 0x90 && response.sw2 == 0x00)
            mLastError[simSlotId] = 0;
        else if(response.sw1 == 0x6a && response.sw2 == 0x84)
            mLastError[simSlotId] = 2;
        else if(response.sw1 == 0x6a && response.sw2 == 0x82)
            mLastError[simSlotId] = 3;
        else
            mLastError[simSlotId] = 1;

        byte[] result = null; 
        int length = 2;

        // check if open logical channel fail
        if((byte)response.sw1 == (byte)0xff && (byte)response.sw2 == (byte)0xff) {
            result = new byte[1];
            result[0] = (byte)0x00;
            log("< openIccLogicalChannelWithSw 0");
            return result;
        }

        if (response.payload != null) {
            length = 2 + response.payload.length;
            result = new byte[length];
            System.arraycopy(response.payload, 0, result, 0, response.payload.length);
        } else {
            result = new byte[length];
        }        
        result[length - 1] = (byte)response.sw2;
        result[length - 2] = (byte)response.sw1;

        String funLog = "";
        for(int i = 0 ;i < result.length;i++) {
            funLog = funLog + ((int)result[i] & 0xff) + " ";
        }
        log("< openIccLogicalChannelWithSw " + funLog);
        return result;
    }
    // NFC SEEK end

    public int getInternationalCardType(int simId) {
        if (!FeatureOption.EVDO_DT_SUPPORT ||
            !(mPhone instanceof GeminiPhone))
            return -1;
        Log.d(LOG_TAG, "[SimSw] getInternationalCardType simId="+simId);
        if (FeatureOption.MTK_GEMINI_SUPPORT == true) {
        return ((GeminiPhone)mPhone).getPhonebyId(simId).getIccCard().getInternationalCardType();
        } else {
            return mPhone.getIccCard().getInternationalCardType();
        }
    }

    public boolean isDataConnectivityPossibleGemini(int simId) {
        return ((GeminiPhone)mPhone).getPhonebyId(simId).isDataConnectivityPossible();
    }

    // TODO: need to be removed by Edward, Data LEGO API [start]    
    /**
     * Returns a constant indicating the current data connection state based on sim slot
     * (cellular).
     * 
     * @param simId sim slot
     * @see #DATA_DISCONNECTED
     * @see #DATA_CONNECTING
     * @see #DATA_CONNECTED
     * @see #DATA_SUSPENDED
     */    
    public int getDataStateGemini(int simId) {
        return DefaultPhoneNotifier.convertDataState(((GeminiPhone)mPhone).getPhonebyId(simId).getDataConnectionState());
    }

     /**
     * Returns a constant indicating the type of activity on a data connection
     * (cellular).
     *
     * @param simId sim slot
     * @see #DATA_ACTIVITY_NONE
     * @see #DATA_ACTIVITY_IN
     * @see #DATA_ACTIVITY_OUT
     * @see #DATA_ACTIVITY_INOUT
     * @see #DATA_ACTIVITY_DORMANT
     */
    public int getDataActivityGemini(int simId) {
        return DefaultPhoneNotifier.convertDataActivityState(((GeminiPhone)mPhone).getPhonebyId(simId).getDataActivityState());
    }


    /**
        * 
        *
        * @param enable boolean (true/false)
        * @param simId sim slot
        */
    public void setDataRoamingEnabledGemini(boolean enable,int simId) {
        ((GeminiPhone) mPhone).getPhonebyId(simId).setDataRoamingEnabled(enable);
    }
    // TODO: need to be removed by Edward, Data LEGO API [end]

    /**
        * Returns a constant indicating the current data connection state based on sim slot
        * (cellular).
        * 
        * @param simId sim slot
        * @see #DATA_DISCONNECTED
        * @see #DATA_CONNECTING
        * @see #DATA_CONNECTED
        * @see #DATA_SUSPENDED
        */    
       public int getDataState(int simId) {
           return DefaultPhoneNotifier.convertDataState(((GeminiPhone)mPhone).getPhonebyId(simId).getDataConnectionState());
       }
    
        /**
        * Returns a constant indicating the type of activity on a data connection
        * (cellular).
        *
        * @param simId sim slot
        * @see #DATA_ACTIVITY_NONE
        * @see #DATA_ACTIVITY_IN
        * @see #DATA_ACTIVITY_OUT
        * @see #DATA_ACTIVITY_INOUT
        * @see #DATA_ACTIVITY_DORMANT
        */
       public int getDataActivity(int simId) {
           return DefaultPhoneNotifier.convertDataActivityState(((GeminiPhone)mPhone).getPhonebyId(simId).getDataActivityState());
       }
    
    
       /**
           * 
           *
           * @param enable boolean (true/false)
           * @param simId sim slot
           */
       public void setDataRoamingEnabled(boolean enable,int simId) {
           ((GeminiPhone) mPhone).getPhonebyId(simId).setDataRoamingEnabled(enable);
       }


    public int getCallState(int slotId) {
        if (GeminiUtils.isGeminiSupport()) {
            if (DBG) {
                log("getCallState simId: " + slotId);
            }
            if (!GeminiUtils.isValidSlot(slotId)) {
                if (DBG) {
                    log("getCallState: wrong slot Id");
                }
                return 0;
            }

            return DefaultPhoneNotifier.convertCallState(((GeminiPhone) mPhone)
                    .getPhonebyId(slotId).getState());
        }
        return 0;
    }

   /**
     * get the network service state for specified SIM
     * @param simId Indicate which sim(slot) to query
     * @return service state.
     *
    */ 
    public Bundle getServiceState(int simId){
        Bundle data = new Bundle();

        if (FeatureOption.MTK_GEMINI_SUPPORT == true) {
            ((GeminiPhone)mPhone).getPhonebyId(simId).getServiceState().fillInNotifierBundle(data);		
        } else {
            mPhone.getServiceState().fillInNotifierBundle(data);
        }
		
        return data; 
    }

    /**
     * @internal     
     */
    public boolean handlePinMmi(String dialString, int simId) {
        if (FeatureOption.MTK_GEMINI_SUPPORT == true) {
           return ((GeminiPhone)mPhone).getPhonebyId(simId).handlePinMmi(dialString);	
        } else {
           return mPhone.handlePinMmi(dialString);
        }
    }

    /**
     * @internal     
     */
    public int getSimIndicatorState(int simId) {
        if (FeatureOption.MTK_GEMINI_SUPPORT == true) {
            return ((GeminiPhone)mPhone).getPhonebyId(simId).getSimIndicatorState();
        } else {
            return mPhone.getSimIndicatorState();
        }
    }

    /**
     * Gets the ISO country code equivalent for the SIM provider's country code.
     * <p>
     * @param simId  Indicates which SIM (slot) to query
     * <p>
     * @return       Gets the ISO country code equivalent for the SIM provider's country code.
     */
    public String getSimCountryIso(int simId) {
        String prop = SystemProperties.get(ITEL_PROPERTY_ICC_OPERATOR_ISO_COUNTRY[simId]);
        Log.d( LOG_TAG,"getSimCountryIso simId = " + simId + "prop = " + prop);
        return prop;
    }

/* 3G switch start */
    /**
     * Get current 3G capability SIM. 
     * (PhoneConstants.GEMINI_SIM_1, PhoneConstants.GEMINI_SIM_2, ...)
     * 
     * @return the SIM slot where 3G capability at. (@see PhoneConstants)
     * @internal
     */
    public int get3GCapabilitySIM() {
        if (GeminiUtils.isGeminiSupport()) {
            return ((GeminiPhone) mPhone).get3GCapabilitySIM();
        } else {
            return PhoneConstants.GEMINI_SIM_1;
        }
    }

    
    /**
     * Set 3G capability to specified SIM. 
     * 
     * @param simId sim slot 
     * @return the result of issuing 3g capability set operation (true or false)
     * @internal
     */
    public boolean set3GCapabilitySIM(int simId) {
        boolean result = false;
        if (m3GSwitchLocks.isEmpty()) {
            PhoneConstants.State state = mCM.getState();
            if (state == PhoneConstants.State.IDLE)
                result = ((GeminiPhone)mPhone).set3GCapabilitySIM(simId);
            else
                Log.w(LOG_TAG, "Phone is not idle, cannot 3G switch [" + state + "]");
        } else {
            Log.w(LOG_TAG, "3G switch locked, cannot 3G switch [" + m3GSwitchLocks + "]");
        }
        return result;
    }

    /**
     * To acquire 3G switch lock 
     * (to protect from multi-manipulation to 3g switch flow)
     * 
     * @return the acquired lock Id
     * @internal
     */
    public int aquire3GSwitchLock() {
        Integer lock = new Integer(m3GSwitchLockCounter++);
        m3GSwitchLocks.add(lock);
        
        Intent intent = new Intent(TelephonyIntents.EVENT_3G_SWITCH_LOCK_CHANGED);
        intent.putExtra(TelephonyIntents.EXTRA_3G_SWITCH_LOCKED, true);
        mApp.getApplicationContext().sendBroadcast(intent);
        
        Log.i(LOG_TAG, "aquire 3G lock: " + lock);
        return lock;
    }

    /**
     * To release the acquired 3G switch lock (by lock Id)
     * 
     * @param lockId thd lock Id
     * @return true if the lock Id is released
     * @internal
     */
    public boolean release3GSwitchLock(int lockId) {
        boolean result = false;
        int index = 0;
        Iterator<Integer> it = m3GSwitchLocks.iterator();
        while (it.hasNext()) {
            int storedLockId = it.next();
            if (storedLockId == lockId) {
                int removedLockId = m3GSwitchLocks.remove(index);
                result = (lockId == removedLockId);
                Log.i(LOG_TAG, "removed 3G lockId: " + removedLockId + "[" + lockId + "]");

                Intent intent = new Intent(TelephonyIntents.EVENT_3G_SWITCH_LOCK_CHANGED);
                intent.putExtra(TelephonyIntents.EXTRA_3G_SWITCH_LOCKED, !m3GSwitchLocks.isEmpty());
                mApp.getApplicationContext().sendBroadcast(intent);
                break;
            }
            ++index;
        }
        return result;
    }

    /**
     * Check 3G switch lock status
     * 
     * @return true if 3G switch lock is locked
     * @internal
     */
    public boolean is3GSwitchLocked() {
        return !m3GSwitchLocks.isEmpty();
    }
    
    /**
     * To Check if 3G Switch Manual Control Mode Enabled. 
     * 
     * @return true if 3G Switch manual control mode is enabled, else false;
     * @internal
     */
    public boolean is3GSwitchManualEnabled() {
        if (FeatureOption.MTK_GEMINI_3G_SWITCH) {
            return ((GeminiPhone)mPhone).is3GSwitchManualEnabled();
        } else
            return false;
    }
    
    /**
     * Check if 3G Switch allows Changing 3G SIM Slot in Manual Control Mode.  
     * 
     * @return true if 3G Switch allows Changing 3G SIM Slot in manual control mode, else false;
     * @internal
     */
    public boolean is3GSwitchManualChange3GAllowed() {
        if (FeatureOption.MTK_GEMINI_3G_SWITCH) {
            return ((GeminiPhone)mPhone).is3GSwitchManualChange3GAllowed();
        } else
            return false;
    }
    
    /**
     * To Get 3G Switch Allowed 3G SIM Slots.
     * 
     * Returns an integer showing allowed 3G SIM Slots bitmasks. 
     *   Bit0 for SIM1; Bit1 for SIM2.  
     *   0 for disallowed; 1 for allowed. 
     * 
     * Examples as below: 
     *   0x00000001b: SIM1 is allowed. 
     *   0x00000010b: SIM2 is allowed.
     *   0x00000011b: SIM1, SIM2 are allowed.
     *   0:           no SIM is allowed. 
     * 
     * @return the allowed 3G SIM Slots bitmasks
     * @internal
     */
    public int get3GSwitchAllowed3GSlots() {
        if (FeatureOption.MTK_GEMINI_3G_SWITCH) {
            return ((GeminiPhone)mPhone).get3GSwitchAllowed3GSlots();
        } else
            return 0;
    }
/* 3G switch end */


    //move from PhoneInterfaceManager for LEGO -- start
    public boolean isRadioOn(int simId) {
        if (GeminiUtils.isGeminiSupport()) {    
            return ((GeminiPhone)mPhone).isRadioOnGemini(simId);
        } else {
            return mPhone.getServiceState().getVoiceRegState() != ServiceState.STATE_POWER_OFF;
        }
    }

    @SuppressWarnings("unchecked")
    public List<NeighboringCellInfo> getNeighboringCellInfo(String callingPackage, int simId) {
        try {
            mApp.enforceCallingOrSelfPermission(
                    android.Manifest.permission.ACCESS_FINE_LOCATION, null);
        } catch (SecurityException e) {
            // If we have ACCESS_FINE_LOCATION permission, skip the check
            // for ACCESS_COARSE_LOCATION
            // A failure should throw the SecurityException from
            // ACCESS_COARSE_LOCATION since this is the weaker precondition
            mApp.enforceCallingOrSelfPermission(
                    android.Manifest.permission.ACCESS_COARSE_LOCATION, null);
        }

        if (mAppOps.noteOp(AppOpsManager.OP_NEIGHBORING_CELLS, Binder.getCallingUid(),
                callingPackage) != AppOpsManager.MODE_ALLOWED) {
            return null;
        }
		
        ArrayList<NeighboringCellInfo> cells = null;

        try {
            cells = (ArrayList<NeighboringCellInfo>) sendRequest(
                    CMD_HANDLE_NEIGHBORING_CELL, new Integer(simId));
        } catch (RuntimeException e) {
            Log.e(LOG_TAG, "getNeighboringCellInfo " + e);
        }

        return (List <NeighboringCellInfo>) cells;        
    }

   /**
     *get the network service state for specified SIM
     * @param simId Indicate which sim(slot) to query
     * @return service state.
     *
    */ 
    public Bundle getServiceStateGemini(int simId){
        Bundle data = new Bundle();
        if (GeminiUtils.isGeminiSupport()) {        
            ((GeminiPhone)mPhone).getPhonebyId(simId).getServiceState().fillInNotifierBundle(data);		
        } else {
            mPhone.getServiceState().fillInNotifierBundle(data);
        }
        return data; 
    }

    public Bundle getCellLocation(int simId) {
        try {
            mApp.enforceCallingOrSelfPermission(
                android.Manifest.permission.ACCESS_FINE_LOCATION, null);
        } catch (SecurityException e) {
            // If we have ACCESS_FINE_LOCATION permission, skip the check for ACCESS_COARSE_LOCATION
            // A failure should throw the SecurityException from ACCESS_COARSE_LOCATION since this
            // is the weaker precondition
            mApp.enforceCallingOrSelfPermission(
                android.Manifest.permission.ACCESS_COARSE_LOCATION, null);
        }
        Bundle data = new Bundle();
        if (GeminiUtils.isGeminiSupport()) { 
            ((GeminiPhone) mPhone).getPhonebyId(simId).getCellLocation().fillInNotifierBundle(data);
        } else {
            mPhone.getCellLocation().fillInNotifierBundle(data);
        }
            
        return data;        
    }

    @Override
    public List<CellInfo> getAllCellInfo(int simId) {
        try {
            mApp.enforceCallingOrSelfPermission(
                android.Manifest.permission.ACCESS_FINE_LOCATION, null);
        } catch (SecurityException e) {
            // If we have ACCESS_FINE_LOCATION permission, skip the check for ACCESS_COARSE_LOCATION
            // A failure should throw the SecurityException from ACCESS_COARSE_LOCATION since this
            // is the weaker precondition
            mApp.enforceCallingOrSelfPermission(
                android.Manifest.permission.ACCESS_COARSE_LOCATION, null);
        }

        if (checkIfCallerIsSelfOrForegoundUser()) {
            Log.i(LOG_TAG,"getAllCellInfo: is active user");
            return ((GeminiPhone) mPhone).getPhonebyId(simId).getAllCellInfo();
        } else {
            Log.i(LOG_TAG,"getAllCellInfo: suppress non-active user");
            return null;
        }
    }

    public void setCellInfoListRate(int rateInMillis,int simId) {
        ((GeminiPhone) mPhone).getPhonebyId(simId).setCellInfoListRate(rateInMillis);
    }

    private boolean checkIfCallerIsSelfOrForegoundUser() {
        boolean ok;

        boolean self = Binder.getCallingUid() == Process.myUid();
        if (!self) {
            // Get the caller's user id then clear the calling identity
            // which will be restored in the finally clause.
            int callingUser = UserHandle.getCallingUserId();
            long ident = Binder.clearCallingIdentity();

            try {
                // With calling identity cleared the current user is the foreground user.
                int foregroundUser = ActivityManager.getCurrentUser();
                ok = (foregroundUser == callingUser);
                Log.i(LOG_TAG,"checkIfCallerIsSelfOrForegoundUser: foregroundUser=" + foregroundUser
                        + " callingUser=" + callingUser + " ok=" + ok);
            } catch (Exception ex) {
                Log.e(LOG_TAG,"checkIfCallerIsSelfOrForegoundUser: Exception ex=" + ex);
                ok = false;
            } finally {
                Binder.restoreCallingIdentity(ident);
            }
        } else {
            Log.i(LOG_TAG,"checkIfCallerIsSelfOrForegoundUser: is self");
            ok = true;
        }
        Log.i(LOG_TAG,"checkIfCallerIsSelfOrForegoundUser: ret=" + ok);
        return ok;
    }

    public int getNetworkType(int simId) {                
        return ((GeminiPhone)mPhone).getPhonebyId(simId).getServiceState().getNetworkType();		
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
    public String getSubscriberId(int simId) {
        Log.d( LOG_TAG,"getSubscriberId");
        try {
            IPhoneSubInfo phoneSubInfo= IPhoneSubInfo.Stub.asInterface(ServiceManager.getService(PHONE_SUBINFO_SERVICE[simId]));
            return phoneSubInfo.getSubscriberId();
        } catch (RemoteException ex) {
            return null;
        } catch (NullPointerException ex) {
            // This could happen before phone restarts due to crashing
            return null;
        }
    } 
    //move from PhoneInterfaceManager for LEGO -- end

    public int getActivePhoneType(int simId) {
        if (GeminiUtils.isGeminiSupport()) {
            if (DBG) {
                log("getActivePhoneType simId: " + simId);
            }

            if (!GeminiUtils.isValidSlot(simId)) {
                if (DBG) {
                    log("getActivePhoneType: wrong simId");
                }
                return 0;
            }

            return ((GeminiPhone) mPhone).getPhonebyId(simId).getPhoneType();
        }
        return 0;

    }


   /**
     * Check if phone is hiding network temporary out of service state.
     * @param simId Indicate which sim(slot) to query
     * @return if phone is hiding network temporary out of service state.
     * @hide
     * @internal     
    */
    public int getNetworkHideState(int simId){
        if (FeatureOption.MTK_GEMINI_SUPPORT == true) {
            return ((GeminiPhone)mPhone).getPhonebyId(simId).getNetworkHideState();
        } else {
            return mPhone.getNetworkHideState();
        }    
    }

}
