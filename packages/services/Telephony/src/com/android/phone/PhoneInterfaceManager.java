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

package com.android.phone;

import android.app.ActivityManager;
import android.app.AppOpsManager;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.Uri;
import android.os.AsyncResult;
import android.os.Process; // NFC SEEK
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.ServiceManager;
import android.os.UserHandle;
import android.telephony.NeighboringCellInfo;
import android.telephony.CellInfo;
import android.telephony.ServiceState;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Log;

import com.android.internal.telephony.CallManager;
import com.android.internal.telephony.DefaultPhoneNotifier;
import com.android.internal.telephony.ITelephony;
import com.android.internal.telephony.IccCard;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.CommandException;
import com.android.internal.telephony.PhoneConstants;


import java.util.List;
import java.util.ArrayList;

//MTK-START [mtk04070][111117][ALPS00093395]MTK added
import android.util.Slog;
import android.os.SystemProperties;
import com.android.internal.telephony.gemini.GeminiPhone;
import com.android.internal.telephony.gemini.GeminiNetworkSubUtil;
import com.android.internal.telephony.CommandException;
import android.telephony.BtSimapOperResponse;
import com.mediatek.common.featureoption.FeatureOption;
import java.util.Iterator;
import com.android.internal.telephony.Call;
//JB TEMP import com.mediatek.vt.VTManager;
import com.android.internal.telephony.uicc.IccFileHandler;
//MTK-END [mtk04070][111117][ALPS00093395]MTK added
//MTK-START [mtk03851][111216]MTK added
import android.os.Messenger;
import android.os.IBinder;
import android.os.Parcelable;
import android.os.RemoteException;
//MTK-END [mtk03851][111216]MTK added
import com.android.internal.telephony.PhoneProxy;
import com.mediatek.phone.DualTalkUtils;
import com.mediatek.phone.gemini.GeminiUtils;
import com.mediatek.phone.vt.VTCallUtils;

//MTK-START [mtk02772]sdk api refactoring start
import com.android.internal.telephony.IPhoneSubInfo;
import com.android.internal.telephony.TelephonyProperties;
//MTK-END [mtk02772]sdk api refactoring end

/// M: [mtk04070][130909]To check permission for Mobile Manager Service/Application. @{
import android.content.pm.PackageManager;
import android.os.ServiceManager;
import com.mediatek.common.mom.IMobileManager;
import com.mediatek.common.mom.IMobileManagerService;
import com.mediatek.common.mom.SubPermissions;
/// @}

import com.mediatek.common.telephony.ITelephonyEx;

/**
 * Implementation of the ITelephony interface.
 */
public class PhoneInterfaceManager extends ITelephony.Stub {
    private static final String LOG_TAG = "PhoneInterfaceManager";
    //MTK-START [mtk04070][111117][ALPS00093395]MTK modified
    private static final boolean DBG = true;//(PhoneGlobals.DBG_LEVEL >= 2);
    //MTK-END [mtk04070][111117][ALPS00093395]MTK modified
    private static final boolean DBG_LOC = false;

    // Message codes used with mMainThreadHandler
    private static final int CMD_HANDLE_PIN_MMI = 1;
    private static final int CMD_HANDLE_NEIGHBORING_CELL = 2;
    private static final int EVENT_NEIGHBORING_CELL_DONE = 3;
    private static final int CMD_ANSWER_RINGING_CALL = 4;
    private static final int CMD_END_CALL = 5;  // not used yet
    private static final int CMD_SILENCE_RINGER = 6;

    /** The singleton instance. */
    private static PhoneInterfaceManager sInstance;

    PhoneGlobals mApp;
    Phone mPhone;
    CallManager mCM;
    AppOpsManager mAppOps;
    MainThreadHandler mMainThreadHandler;
    CallHandlerServiceProxy mCallHandlerService;
    //MTK-START [mtk04070][111117][ALPS00093395]MTK added
    /* Fion add start */
    private static final int CMD_END_CALL_GEMINI = 7; 
    private static final int CMD_ANSWER_RINGING_CALL_GEMINI = 8;
    /* Fion add end */

    /* Adjust modem radio power for Lenovo SAR requirement. */
    private static final int CMD_ADJUST_MODEM_RADIO_POWER = 35;

    /* 3G switch start */
    private ArrayList<Integer> m3GSwitchLocks = new ArrayList<Integer>();
    private static int m3GSwitchLockCounter;
    /* 3G switch end */

    /* SIM related system property start*/
    private String[] ITEL_PROPERTY_SIM_STATE = {
        TelephonyProperties.PROPERTY_SIM_STATE,
        TelephonyProperties.PROPERTY_SIM_STATE_2,
        TelephonyProperties.PROPERTY_SIM_STATE_3,
        TelephonyProperties.PROPERTY_SIM_STATE_4,
    };

    private String[] ITEL_PROPERTY_ICC_OPERATOR_NUMERIC = {
        TelephonyProperties.PROPERTY_ICC_OPERATOR_NUMERIC,
        TelephonyProperties.PROPERTY_ICC_OPERATOR_NUMERIC_2,
        TelephonyProperties.PROPERTY_ICC_OPERATOR_NUMERIC_3,
        TelephonyProperties.PROPERTY_ICC_OPERATOR_NUMERIC_4,
    };
    
    private String[] ITEL_PROPERTY_ICC_OPERATOR_ALPHA = {
        TelephonyProperties.PROPERTY_ICC_OPERATOR_ALPHA,
        TelephonyProperties.PROPERTY_ICC_OPERATOR_ALPHA_2,
        TelephonyProperties.PROPERTY_ICC_OPERATOR_ALPHA_3,
        TelephonyProperties.PROPERTY_ICC_OPERATOR_ALPHA_4,
    };

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

    /* SIM related system property end*/

    private class PinMmiGemini {
        public String dialString;
        public Integer simId;

        public PinMmiGemini(String dialString, Integer simId) {
            this.dialString = dialString;
            this.simId = simId;
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

            switch (msg.what) {
                case CMD_HANDLE_PIN_MMI:
                    //MTK-START [mtk04070][111117][ALPS00093395]MTK modified
                    PinMmiGemini pinmmi;
                    request = (MainThreadRequest) msg.obj;
                    pinmmi = (PinMmiGemini) request.argument;
                    if (pinmmi.simId != -1) {
                        request.result = Boolean.valueOf(
                                ((GeminiPhone) mPhone).getPhonebyId(pinmmi.simId).handlePinMmi(pinmmi.dialString));
                    } else {
                        if (GeminiUtils.isGeminiSupport()) {
                            request.result = Boolean.valueOf(
                                    ((GeminiPhone) mPhone).handlePinMmi(pinmmi.dialString));
                        } else {
                            request.result = Boolean.valueOf(
                                    mPhone.handlePinMmi(pinmmi.dialString));
                        }
                    }
                    //MTK-END [mtk04070][111117][ALPS00093395]MTK modified
                    // Wake up the requesting thread
                    synchronized (request) {
                        request.notifyAll();
                    }
                    break;

                case CMD_HANDLE_NEIGHBORING_CELL:
                    request = (MainThreadRequest) msg.obj;
                    onCompleted = obtainMessage(EVENT_NEIGHBORING_CELL_DONE,
                            request);

                    //MTK-START [mtk04070][111117][ALPS00093395]MTK modified
                    if ((request.argument == null) ||(!GeminiUtils.isGeminiSupport())){
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

                case CMD_ANSWER_RINGING_CALL:
                    //MTK-START [mtk04070][111117][ALPS00093395]MTK modified
                    if (FeatureOption.MTK_VT3G324M_SUPPORT) {
                        if (VTCallUtils.isVTRinging()) {
                        } else {
                            answerRingingCallInternal();
                        }
                    } else {
                        answerRingingCallInternal();
                    }
                    //MTK-END [mtk04070][111117][ALPS00093395]MTK modified
                    break;

                case CMD_SILENCE_RINGER:
                    silenceRingerInternal();
                    break;

                case CMD_END_CALL:
                    //MTK-START [mtk04070][111117][ALPS00093395]MTK added
                	/*if( FeatureOption.MTK_VT3G324M_SUPPORT == true )
                	{         
                		try{
                			InCallScreen ics = PhoneGlobals.getInstance().getInCallScreenInstance();
                			if(!VTCallUtils.isVTActive()){
                				ics.getVTInCallScreenInstance().setVTScreenMode(Constants.VTScreenMode.VT_SCREEN_CLOSE);     			
                    		}else{
                    			ics.getVTInCallScreenInstance().setVTScreenMode(Constants.VTScreenMode.VT_SCREEN_OPEN);
                    		}
                			ics.getVTInCallScreenInstance().updateVTScreen(ics.getVTInCallScreenInstance().getVTScreenMode());
                		}catch(Exception ex)
                		{}
                	
                	}*/
                    //MTK-END [mtk04070][111117][ALPS00093395]MTK added
                	
                    request = (MainThreadRequest) msg.obj;
                    boolean hungUp = false;

                    ///M:for both CDMA && GSM case, CMD_END_CALL takes the same
                    // action:
                    // if there is ringing call, hang up ringing call
                    // else if there is foreground call, hang up foreground call
                    // else if there is background call, hang up background call @{

                    // int phoneType = mPhone.getPhoneType();
                    // if (phoneType == PhoneConstants.PHONE_TYPE_CDMA) {
                    // // CDMA: If the user presses the Power button we treat it as
                    // // ending the complete call session
                    // hungUp = PhoneUtils.hangupRingingAndActive(mPhone);
                    // } else if (phoneType == PhoneConstants.PHONE_TYPE_GSM) {
                    // // GSM: End the call as per the Phone state
                    // hungUp = PhoneUtils.hangup(mCM);
                    // } else {
                    // throw new IllegalStateException("Unexpected phone type: " +
                    // phoneType);
                    // }

                    if (mCM != null) {
                        hungUp = PhoneUtils.hangup(mCM);
                        if (DBG) {
                            log("CMD_END_CALL: " + (hungUp ? "hung up!" : "no call to hang up"));
                        }
                    }
                    ///M: @}

                    request.result = hungUp;
                    // Wake up the requesting thread
                    synchronized (request) {
                        request.notifyAll();
                    }
                    break;

                //MTK-START [mtk04070][111117][ALPS00093395]MTK modified
                case CMD_END_CALL_GEMINI:
                    if (GeminiUtils.isGeminiSupport()) {
                        /*JB TEMP
                        if (FeatureOption.MTK_VT3G324M_SUPPORT) {

                    		try{                	
                    			InCallScreen ics2 = PhoneGlobals.getInstance().getInCallScreenInstance();
                    			if(!VTCallUtils.isVTActive()){
                    				ics2.getVTInCallScreenInstance().setVTScreenMode(Constants.VTScreenMode.VT_SCREEN_CLOSE);     			
                        		}else{
                        			ics2.getVTInCallScreenInstance().setVTScreenMode(Constants.VTScreenMode.VT_SCREEN_OPEN);
                        		}
                    			ics2.getVTInCallScreenInstance().updateVTScreen(ics2.getVTInCallScreenInstance().getVTScreenMode()); 
                    		}catch(Exception ex)  
                    		{}  
                    	}
                    	*/

                        request = (MainThreadRequest) msg.obj;
                        boolean hungUpGemini = false;
                        final int slotId = (int) (msg.arg1);

                        log("CMD_END_CALL_GEMINI: msg.arg1" + slotId);

                        int phoneTypeGemini = ((GeminiPhone) mPhone).getPhoneTypeGemini(slotId);

                        if (phoneTypeGemini == PhoneConstants.PHONE_TYPE_CDMA) {
                            hungUpGemini = PhoneUtils.hangupRingingAndActive(mPhone);
                        } else if (phoneTypeGemini == PhoneConstants.PHONE_TYPE_GSM) {
                            hungUpGemini = PhoneUtils.hangup(mCM);
                        } else {
                            throw new IllegalStateException("Unexpected phone type: "
                                    + phoneTypeGemini);
                        }
                    
                        if (DBG) log("CMD_END_CALL_GEMINI: " + (hungUpGemini ? "hung up!" : "no call to hang up"));
                        request.result = hungUpGemini;
                        synchronized (request) {
                            request.notifyAll();
                        }                
                    }
                    break;
                    
                case CMD_ANSWER_RINGING_CALL_GEMINI:
                    if (GeminiUtils.isGeminiSupport()) {
                        if (FeatureOption.MTK_VT3G324M_SUPPORT) {
                            answerRingingCallInternal();
                        } else {
                            answerRingingCallInternal();
                        }
                    }
                    break;

                default:
                    Log.w(LOG_TAG, "MainThreadHandler: unexpected message code: " + msg.what);
                    break;
            }
        }
    }

    /**
     * Posts the specified command to be executed on the main thread,
     * waits for the request to complete, and returns the result.
     * @see #sendRequestAsync
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

    /**
     * Asynchronous ("fire and forget") version of sendRequest():
     * Posts the specified command to be executed on the main thread, and
     * returns immediately.
     * @see #sendRequest
     */
    private void sendRequestAsync(int command) {
        mMainThreadHandler.sendEmptyMessage(command);
    }

    /**
     * Initialize the singleton PhoneInterfaceManager instance.
     * This is only done once, at startup, from PhoneApp.onCreate().
     */
    /* package */ static PhoneInterfaceManager init(PhoneGlobals app, Phone phone,
            CallHandlerServiceProxy callHandlerService) {
        synchronized (PhoneInterfaceManager.class) {
            if (sInstance == null) {
                sInstance = new PhoneInterfaceManager(app, phone, callHandlerService);
            } else {
                Log.wtf(LOG_TAG, "init() called multiple times!  sInstance = " + sInstance);
            }
            return sInstance;
        }
    }

    /** Private constructor; @see init() */
    private PhoneInterfaceManager(PhoneGlobals app, Phone phone,
            CallHandlerServiceProxy callHandlerService) {
        mApp = app;
        mPhone = phone;
        mCM = PhoneGlobals.getInstance().mCM;
        mAppOps = (AppOpsManager)app.getSystemService(Context.APP_OPS_SERVICE);
        mMainThreadHandler = new MainThreadHandler();
        mCallHandlerService = callHandlerService;
        publish();
    }

    private void publish() {
        if (DBG) log("publish: " + this);

        ServiceManager.addService("phone", this);
    }

    //
    // Implementation of the ITelephony interface.
    //

    public void dial(String number) {
        if (DBG) log("dial: " + number);
        // No permission check needed here: This is just a wrapper around the
        // ACTION_DIAL intent, which is available to any app since it puts up
        // the UI before it does anything.

        String url = createTelUrl(number);
        if (url == null) {
            return;
        }

        // PENDING: should we just silently fail if phone is offhook or ringing?
        //MTK-START [mtk04070][111117][ALPS00093395]MTK modified
        /* Fion add start */
        PhoneConstants.State state; // IDLE, RINGING, or OFFHOOK
        if (GeminiUtils.isGeminiSupport()) {
            state = ((GeminiPhone) mPhone).getState();
        } else {
            state = mCM.getState();
        }
        /* Fion add end */
        //MTK-END [mtk04070][111117][ALPS00093395]MTK modified
        if (state != PhoneConstants.State.OFFHOOK && state != PhoneConstants.State.RINGING) {
            Intent  intent = new Intent(Intent.ACTION_DIAL, Uri.parse(url));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            mApp.startActivity(intent);
        }
    }

    public void call(String callingPackage, String number) {
        if (DBG) log("call: " + number);

        /// M: [mtk04070][130724]To check permission for Mobile Manager Service/Application. @{
        if (FeatureOption.MTK_MOBILE_MANAGEMENT) {
            if (!checkMoMSPhonePermission(number)) {
                return;
            }
        }
        /// @}

        // This is just a wrapper around the ACTION_CALL intent, but we still
        // need to do a permission check since we're calling startActivity()
        // from the context of the phone app.
        enforceCallPermission();

        if (mAppOps.noteOp(AppOpsManager.OP_CALL_PHONE, Binder.getCallingUid(), callingPackage)
                != AppOpsManager.MODE_ALLOWED) {
            return;
        }

        String url = createTelUrl(number);
        if (url == null) {
            return;
        }

        Intent intent = new Intent(Intent.ACTION_CALL, Uri.parse(url));
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        mApp.startActivity(intent);
    }

    private boolean showCallScreenInternal(boolean specifyInitialDialpadState,
                                           boolean showDialpad) {
        if (!PhoneGlobals.sVoiceCapable) {
            // Never allow the InCallScreen to appear on data-only devices.
            return false;
        }
        if (isIdle()) {
            return false;
        }
        // If the phone isn't idle then go to the in-call screen
        long callingId = Binder.clearCallingIdentity();

        mCallHandlerService.bringToForeground(showDialpad);

        Binder.restoreCallingIdentity(callingId);
        return true;
    }

    // Show the in-call screen without specifying the initial dialpad state.
    public boolean showCallScreen() {
        return showCallScreenInternal(false, false);
    }

    // The variation of showCallScreen() that specifies the initial dialpad state.
    // (Ideally this would be called showCallScreen() too, just with a different
    // signature, but AIDL doesn't allow that.)
    public boolean showCallScreenWithDialpad(boolean showDialpad) {
        return showCallScreenInternal(true, showDialpad);
    }

    /**
     * End a call based on call state
     * @return true is a call was ended
     */
    public boolean endCall() {
        enforceCallPermission();
        return (Boolean) sendRequest(CMD_END_CALL, null);
    }

    public void answerRingingCall() {
        if (DBG) log("answerRingingCall...");
        // TODO: there should eventually be a separate "ANSWER_PHONE" permission,
        // but that can probably wait till the big TelephonyManager API overhaul.
        // For now, protect this call with the MODIFY_PHONE_STATE permission.
        enforceModifyPermission();
        sendRequestAsync(CMD_ANSWER_RINGING_CALL);
    }

    /**
     * Make the actual telephony calls to implement answerRingingCall().
     * This should only be called from the main thread of the Phone app.
     * @see #answerRingingCall
     *
     * TODO: it would be nice to return true if we answered the call, or
     * false if there wasn't actually a ringing incoming call, or some
     * other error occurred.  (In other words, pass back the return value
     * from PhoneUtils.answerCall() or PhoneUtils.answerAndEndActive().)
     * But that would require calling this method via sendRequest() rather
     * than sendRequestAsync(), and right now we don't actually *need* that
     * return value, so let's just return void for now.
     */
    private void answerRingingCallInternal() {
        //MTK-START [mtk04070][111117][ALPS00093395]MTK modified
        /* Fion add start */
        boolean hasRingingCall , hasActiveCall , hasHoldingCall;

        if (GeminiUtils.isGeminiSupport()) {
            hasRingingCall = !((GeminiPhone) mPhone).getRingingCall().isIdle();
            hasActiveCall = !((GeminiPhone) mPhone).getForegroundCall().isIdle();
            hasHoldingCall = !((GeminiPhone) mPhone).getBackgroundCall().isIdle();
        } else {
            hasRingingCall = !mPhone.getRingingCall().isIdle();
            hasActiveCall = !mPhone.getForegroundCall().isIdle();
            hasHoldingCall = !mPhone.getBackgroundCall().isIdle();
        }
        /* Fion add end */
        //MTK-END [mtk04070][111117][ALPS00093395]MTK modified

        Call ringing = mCM.getFirstActiveRingingCall();

        if (hasRingingCall) {
            //MTK-START [mtk04070][111117][ALPS00093395]MTK modified
            //final boolean hasActiveCall = !mPhone.getForegroundCall().isIdle();
            //final boolean hasHoldingCall = !mPhone.getBackgroundCall().isIdle();
            //MTK-END [mtk04070][111117][ALPS00093395]MTK modified
            if (hasActiveCall && hasHoldingCall) {
                // Both lines are in use!
                // TODO: provide a flag to let the caller specify what
                // policy to use if both lines are in use.  (The current
                // behavior is hardwired to "answer incoming, end ongoing",
                // which is how the CALL button is specced to behave.)
                PhoneUtils.answerAndEndActive(mCM, ringing);
                return;
            } else {
                // answerCall() will automatically hold the current active
                // call, if there is one.
                PhoneUtils.answerCall(ringing);
                return;
            }
        } else {
            /* Solve [ALPS00272855]Can not answer SIP MT call via headset, mtk04070, 20120425  */
            /* Check if the type of ringing call is SIP */
            hasRingingCall = mCM.hasActiveRingingCall();
	    if (hasRingingCall) {
		Phone phone = mCM.getRingingPhone();
		int phoneType = phone.getPhoneType();
	        if (phoneType == PhoneConstants.PHONE_TYPE_SIP) {
                   log("answerRingingCallInternal: answering (SIP)...");
                   if (mCM.hasActiveFgCall() &&
                      mCM.getFgPhone().getPhoneType() == PhoneConstants.PHONE_TYPE_CDMA) {
                      // Similar to the PHONE_TYPE_CDMA handling.
                      // The incoming call is SIP call and the ongoing
                      // call is a CDMA call. The CDMA network does not
                      // support holding an active call, so there's no
                      // way to swap between a CDMA call and a SIP call.
                      // So for now, we just don't allow a CDMA call and
                      // a SIP call to be active at the same time.We'll
                      // "answer incoming, end ongoing" in this case.
                      if (DBG) log("answerRingingCallInternal: answer SIP incoming and end CDMA ongoing");
                      PhoneUtils.answerAndEndActive(mCM, ringing);
                   } else if (mCM.hasActiveFgCall() &&
                             (mCM.getFgPhone().getPhoneType() != PhoneConstants.PHONE_TYPE_CDMA) &&
                             mCM.hasActiveBgCall()) {
                             PhoneUtils.answerAndEndActive(mCM, ringing);
                   } else {
                     PhoneUtils.answerCall(ringing);
                   }
	        }
	    }/* hasRingingCall */
            return;
        }
    }

    public void silenceRinger() {
        if (DBG) log("silenceRinger...");
        // TODO: find a more appropriate permission to check here.
        // (That can probably wait till the big TelephonyManager API overhaul.
        // For now, protect this call with the MODIFY_PHONE_STATE permission.)
        enforceModifyPermission();
        sendRequestAsync(CMD_SILENCE_RINGER);
    }

    /**
     * Internal implemenation of silenceRinger().
     * This should only be called from the main thread of the Phone app.
     * @see #silenceRinger
     */
    private void silenceRingerInternal() {
        //MTK-START [mtk04070][111117][ALPS00093395]MTK modified
        /* Fion add start */
        PhoneConstants.State state;
        /*if (FeatureOption.MTK_GEMINI_SUPPORT == true)
        {
            state = ((GeminiPhone)mPhone).getState();  // IDLE, RINGING, or OFFHOOK	
        }
        else
        {
            state = mPhone.getState();  // IDLE, RINGING, or OFFHOOK
        }*/
        state = mCM.getState();
        if ((state == PhoneConstants.State.RINGING)
            && mApp.notifier.isRinging()) {
        /* Fion add end */
            // Ringer is actually playing, so silence it.
            if (DBG) log("silenceRingerInternal: silencing...");
            //Yunfei.Liu Google removed setAudioConotrolState on Android2.3, further check this
            //PhoneUtils.setAudioControlState(PhoneUtils.AUDIO_IDLE);
            mApp.notifier.silenceRinger();
        }
        //MTK-END [mtk04070][111117][ALPS00093395]MTK modified
    }

    public boolean isOffhook() {
        //MTK-START [mtk04070][111117][ALPS00093395]MTK modified
        /* Solve [ALPS00292626]The SIP call can't be ended when we has enabled "Power button ends call". mtk04070, 20120530 */
        PhoneConstants.State state = mCM.getState();
        /*
        if (FeatureOption.MTK_GEMINI_SUPPORT == true)
        {
            state = ((GeminiPhone)mPhone).getState();  // IDLE, RINGING, or OFFHOOK	
        }
        else
        {
            state = mPhone.getState();  // IDLE, RINGING, or OFFHOOK
        }			
        */
        
        Log.d(LOG_TAG, "state = " + state);
		
        return (state == PhoneConstants.State.OFFHOOK);
        //MTK-END [mtk04070][111117][ALPS00093395]MTK modified
    }

    public boolean isRinging() {
        //MTK-START [mtk04070][111117][ALPS00093395]MTK modified
        PhoneConstants.State state = mCM.getState();
        /*if (FeatureOption.MTK_GEMINI_SUPPORT == true)
        {
            state = ((GeminiPhone)mPhone).getState();  // IDLE, RINGING, or OFFHOOK
            
            //Give an chance to get the correct status for SIP on gemini platform
            if (state == PhoneConstants.State.IDLE)
            {
            	state = mCM.getState();
            }
        }
        else
        {
            state = mPhone.getState();  // IDLE, RINGING, or OFFHOOK
        }*/			
        return (state == PhoneConstants.State.RINGING);
        //MTK-END [mtk04070][111117][ALPS00093395]MTK modified
    }

    public boolean isIdle() {
        //MTK-START [mtk04070][111117][ALPS00093395]MTK modified
        PhoneConstants.State state; // IDLE, RINGING, or OFFHOOK    
        if (GeminiUtils.isGeminiSupport()) {
            state = ((GeminiPhone) mPhone).getState();
            if (state == PhoneConstants.State.IDLE) {
                state = mCM.getState();
            }
        } else {
            state = mPhone.getState();
            if (state == PhoneConstants.State.IDLE) {
                state = mCM.getState();
            }
        }
        return (state == PhoneConstants.State.IDLE);
        //MTK-END [mtk04070][111117][ALPS00093395]MTK modified
    }

    public boolean isSimPinEnabled() {
        enforceReadPermission();
        return (PhoneGlobals.getInstance().isSimPinEnabled());
    }

    public boolean supplyPin(String pin) {
        int [] resultArray = supplyPinReportResult(pin);
        return (resultArray[0] == PhoneConstants.PIN_RESULT_SUCCESS) ? true : false;
    }

    public boolean supplyPuk(String puk, String pin) {
        int [] resultArray = supplyPukReportResult(puk, pin);
        return (resultArray[0] == PhoneConstants.PIN_RESULT_SUCCESS) ? true : false;
    }

    /** {@hide} */
    public int[] supplyPinReportResult(String pin) {
        enforceModifyPermission();
        final UnlockSim checkSimPin = new UnlockSim(mPhone.getIccCard());
        checkSimPin.start();
        return checkSimPin.unlockSim(null, pin);
    }

    /** {@hide} */
    public int[] supplyPukReportResult(String puk, String pin) {
        enforceModifyPermission();
        final UnlockSim checkSimPuk = new UnlockSim(mPhone.getIccCard());
        checkSimPuk.start();
        return checkSimPuk.unlockSim(puk, pin);
    }

    /**
     * Helper thread to turn async call to SimCard#supplyPin into
     * a synchronous one.
     */
    private static class UnlockSim extends Thread {

        private final IccCard mSimCard;

        private boolean mDone = false;
        private int mResult = PhoneConstants.PIN_GENERAL_FAILURE;
        private int mRetryCount = -1;

        // For replies from SimCard interface
        private Handler mHandler;

        // For async handler to identify request type
        private static final int SUPPLY_PIN_COMPLETE = 100;
        //MTK-START [mtk04070][111117][ALPS00093395]MTK added
        private static final int SUPPLY_PUK_COMPLETE = 101;
        //MTK-END [mtk04070][111117][ALPS00093395]MTK added

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
                            case SUPPLY_PIN_COMPLETE:
                            //MTK-START [mtk04070][111117][ALPS00093395]MTK added
                            case SUPPLY_PUK_COMPLETE:
                            //MTK-END [mtk04070][111117][ALPS00093395]MTK added
                                Log.d(LOG_TAG, "SUPPLY_PIN_COMPLETE");
                                synchronized (UnlockSim.this) {
                                    mRetryCount = msg.arg1;
                                    if (ar.exception != null) {
                                        if (ar.exception instanceof CommandException &&
                                                ((CommandException)(ar.exception)).getCommandError()
                                                == CommandException.Error.PASSWORD_INCORRECT) {
                                            mResult = PhoneConstants.PIN_PASSWORD_INCORRECT;
                                        } else {
                                            mResult = PhoneConstants.PIN_GENERAL_FAILURE;
                                        }
                                    } else {
                                        mResult = PhoneConstants.PIN_RESULT_SUCCESS;
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

        /*
         * Use PIN or PUK to unlock SIM card
         *
         * If PUK is null, unlock SIM card with PIN
         *
         * If PUK is not null, unlock SIM card with PUK and set PIN code
         */
        synchronized int[] unlockSim(String puk, String pin) {

            while (mHandler == null) {
                try {
                    wait();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            Message callback = Message.obtain(mHandler, SUPPLY_PIN_COMPLETE);

            if (puk == null) {
                mSimCard.supplyPin(pin, callback);
            } else {
                mSimCard.supplyPuk(puk, pin, callback);
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
            int[] resultArray = new int[2];
            resultArray[0] = mResult;
            resultArray[1] = mRetryCount;
            return resultArray;
        }
    }

    public void updateServiceLocation() {
        // No permission check needed here: this call is harmless, and it's
        // needed for the ServiceState.requestStateUpdate() call (which is
        // already intentionally exposed to 3rd parties.)
        mPhone.updateServiceLocation();
    }

    public boolean isRadioOn() {
        /// M:Gemini+ @{
        if (GeminiUtils.isGeminiSupport()) {
            final int[] slots = GeminiUtils.getSlots();
            GeminiPhone gPhone = (GeminiPhone) mPhone;
            for (int slot : slots) {
                if (gPhone.getPhonebyId(slot).getServiceState().getState() != ServiceState.STATE_POWER_OFF) {					
                    return true;
                }
            }
            return false;
        }
        /// @}
        return mPhone.getServiceState().getVoiceRegState() != ServiceState.STATE_POWER_OFF;
    }

    public void toggleRadioOnOff() {
        enforceModifyPermission();
        /// M:Gemini+ @{
        if (GeminiUtils.isGeminiSupport()) {
            ((GeminiPhone) mPhone).setRadioMode(isRadioOn() ? GeminiNetworkSubUtil.MODE_FLIGHT_MODE
                    : GeminiNetworkSubUtil.MODE_DUAL_SIM);
        } else {
            mPhone.setRadioPower(!isRadioOn());
        }
        /// @}
    }
    public boolean setRadio(boolean turnOn) {
        enforceModifyPermission();
        if ((mPhone.getServiceState().getVoiceRegState() != ServiceState.STATE_POWER_OFF) != turnOn) {
            toggleRadioOnOff();
        }
        return true;
    }
    public boolean setRadioPower(boolean turnOn) {
        enforceModifyPermission();
        mPhone.setRadioPower(turnOn);
        return true;
    }

    public boolean enableDataConnectivity() {
        enforceModifyPermission();
        ConnectivityManager cm =
                (ConnectivityManager)mApp.getSystemService(Context.CONNECTIVITY_SERVICE);
        cm.setMobileDataEnabled(true);
        return true;
    }

    public int enableApnType(String type) {
        enforceModifyPermission();
        return mPhone.enableApnType(type);
    }

    public int disableApnType(String type) {
        enforceModifyPermission();
        return mPhone.disableApnType(type);
    }

    public boolean disableDataConnectivity() {
        enforceModifyPermission();
        ConnectivityManager cm =
                (ConnectivityManager)mApp.getSystemService(Context.CONNECTIVITY_SERVICE);
        cm.setMobileDataEnabled(false);
        return true;
    }

    public boolean isDataConnectivityPossible() {
        return mPhone.isDataConnectivityPossible();
    }

    public boolean handlePinMmi(String dialString) {
        enforceModifyPermission();
        //MTK-START [mtk04070][111117][ALPS00093395]MTK modified
        return (Boolean) sendRequest(CMD_HANDLE_PIN_MMI, new PinMmiGemini(dialString, -1));
        //MTK-END [mtk04070][111117][ALPS00093395]MTK modified
    }

    public void cancelMissedCallsNotification() {
        enforceModifyPermission();
        mApp.notificationMgr.cancelMissedCallNotification();
    }

    public int getCallState() {
        //Modify for ALPS00408676 to use CallManager's state to report
        //return DefaultPhoneNotifier.convertCallState(mPhone.getState());
        return DefaultPhoneNotifier.convertCallState(mCM.getState());
    }

    public int getDataState() {
        return DefaultPhoneNotifier.convertDataState(mPhone.getDataConnectionState());
    }

    public int getDataActivity() {
        return DefaultPhoneNotifier.convertDataActivityState(mPhone.getDataActivityState());
    }

    @Override
    public Bundle getCellLocation() {
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

        /// M: To check permission for Mobile Manager Service/Application. @{
        if (FeatureOption.MTK_MOBILE_MANAGEMENT) {
            if (!checkMoMSLocationPermission()) {
                return new Bundle();
            }
        }
        /// @}

        if (checkIfCallerIsSelfOrForegoundUser()) {
            if (DBG_LOC) log("getCellLocation: is active user");
            Bundle data = new Bundle();
            mPhone.getCellLocation().fillInNotifierBundle(data);
            return data;
        } else {
            if (DBG_LOC) log("getCellLocation: suppress non-active user");
            return null;
        }
    }

    @Override
    public void enableLocationUpdates() {
        mApp.enforceCallingOrSelfPermission(
                android.Manifest.permission.CONTROL_LOCATION_UPDATES, null);
        mPhone.enableLocationUpdates();
    }

    @Override
    public void disableLocationUpdates() {
        mApp.enforceCallingOrSelfPermission(
                android.Manifest.permission.CONTROL_LOCATION_UPDATES, null);
        mPhone.disableLocationUpdates();
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<NeighboringCellInfo> getNeighboringCellInfo(String callingPackage) {
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

        /// M: To check permission for Mobile Manager Service/Application. @{
        if (FeatureOption.MTK_MOBILE_MANAGEMENT) {
            if (!checkMoMSLocationPermission()) {
                return new ArrayList<NeighboringCellInfo>();
            }
        }
        /// @}

        if (checkIfCallerIsSelfOrForegoundUser()) {
            if (DBG_LOC) log("getNeighboringCellInfo: is active user");

            ArrayList<NeighboringCellInfo> cells = null;

            try {
                cells = (ArrayList<NeighboringCellInfo>) sendRequest(
                        CMD_HANDLE_NEIGHBORING_CELL, null);
            } catch (RuntimeException e) {
                Log.e(LOG_TAG, "getNeighboringCellInfo " + e);
            }
            return cells;
        } else {
            if (DBG_LOC) log("getNeighboringCellInfo: suppress non-active user");
            return null;
        }
    }


    @Override
    public List<CellInfo> getAllCellInfo() {
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

        /// M: To check permission for Mobile Manager Service/Application. @{
        if (FeatureOption.MTK_MOBILE_MANAGEMENT) {
            if (!checkMoMSLocationPermission()) {
                return new ArrayList<CellInfo>();
            }
        }
        /// @}

        if (checkIfCallerIsSelfOrForegoundUser()) {
            if (DBG_LOC) log("getAllCellInfo: is active user");
            return mPhone.getAllCellInfo();
        } else {
            if (DBG_LOC) log("getAllCellInfo: suppress non-active user");
            return null;
        }
    }

    public void setCellInfoListRate(int rateInMillis) {
        mPhone.setCellInfoListRate(rateInMillis);
    }

    //
    // Internal helper methods.
    //

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
                if (DBG_LOC) {
                    log("checkIfCallerIsSelfOrForegoundUser: foregroundUser=" + foregroundUser
                            + " callingUser=" + callingUser + " ok=" + ok);
                }
            } catch (Exception ex) {
                if (DBG_LOC) loge("checkIfCallerIsSelfOrForegoundUser: Exception ex=" + ex);
                ok = false;
            } finally {
                Binder.restoreCallingIdentity(ident);
            }
        } else {
            if (DBG_LOC) log("checkIfCallerIsSelfOrForegoundUser: is self");
            ok = true;
        }
        if (DBG_LOC) log("checkIfCallerIsSelfOrForegoundUser: ret=" + ok);
        return ok;
    }

    /**
     * Make sure the caller has the READ_PHONE_STATE permission.
     *
     * @throws SecurityException if the caller does not have the required permission
     */
    private void enforceReadPermission() {
        mApp.enforceCallingOrSelfPermission(android.Manifest.permission.READ_PHONE_STATE, null);
    }

    /**
     * Make sure the caller has the MODIFY_PHONE_STATE permission.
     *
     * @throws SecurityException if the caller does not have the required permission
     */
    private void enforceModifyPermission() {
        mApp.enforceCallingOrSelfPermission(android.Manifest.permission.MODIFY_PHONE_STATE, null);
    }

    /**
     * Make sure the caller has the CALL_PHONE permission.
     *
     * @throws SecurityException if the caller does not have the required permission
     */
    private void enforceCallPermission() {
        mApp.enforceCallingOrSelfPermission(android.Manifest.permission.CALL_PHONE, null);
    }


    private String createTelUrl(String number) {
        if (TextUtils.isEmpty(number)) {
            return null;
        }

        StringBuilder buf = new StringBuilder("tel:");
        buf.append(number);
        return buf.toString();
    }

    private void log(String msg) {
        Log.d(LOG_TAG, "[PhoneIntfMgr] " + msg);
    }

    private void loge(String msg) {
        Log.e(LOG_TAG, "[PhoneIntfMgr] " + msg);
    }

    public int getActivePhoneType() {
        return mPhone.getPhoneType();
    }

    /**
     * Returns the CDMA ERI icon index to display
     */
    public int getCdmaEriIconIndex() {
        return mPhone.getCdmaEriIconIndex();
    }

    /**
     * Returns the CDMA ERI icon mode,
     * 0 - ON
     * 1 - FLASHING
     */
    public int getCdmaEriIconMode() {
        return mPhone.getCdmaEriIconMode();
    }

    /**
     * Returns the CDMA ERI text,
     */
    public String getCdmaEriText() {
        return mPhone.getCdmaEriText();
    }

    /**
     * Returns true if CDMA provisioning needs to run.
     */
    public boolean needsOtaServiceProvisioning() {
        return mPhone.needsOtaServiceProvisioning();
    }

    /**
     * Returns the unread count of voicemails
     */
    public int getVoiceMessageCount() {
        return mPhone.getVoiceMessageCount();
    }

    /**
     * Returns the data network type
     *
     * @Deprecated to be removed Q3 2013 use {@link #getDataNetworkType}.
     */
    @Override
    public int getNetworkType() {
        return mPhone.getServiceState().getDataNetworkType();
    }

    /**
     * Returns the data network type
     */
    @Override
    public int getDataNetworkType() {
        return mPhone.getServiceState().getDataNetworkType();
    }

    /**
     * Returns the data network type
     */
    @Override
    public int getVoiceNetworkType() {
        return mPhone.getServiceState().getVoiceNetworkType();
    }

    /**
     * @return true if a ICC card is present
     */
    public boolean hasIccCard() {
        return mPhone.getIccCard().hasIccCard();
    }

    /**
     * Return if the current radio is LTE on CDMA. This
     * is a tri-state return value as for a period of time
     * the mode may be unknown.
     *
     * @return {@link Phone#LTE_ON_CDMA_UNKNOWN}, {@link Phone#LTE_ON_CDMA_FALSE}
     * or {@link PHone#LTE_ON_CDMA_TRUE}
     */
    public int getLteOnCdmaMode() {
        return mPhone.getLteOnCdmaMode();
    }

    //MTK-START [mtk04070][111117][ALPS00093395]MTK proprietary methods
    public boolean isVoiceIdle() {
        PhoneConstants.State state;// IDLE, RINGING, or OFFHOOK

        if (GeminiUtils.isGeminiSupport()) {
            state = ((GeminiPhone) mPhone).getState();
        } else {
            state = mPhone.getState();
        }

        return (state == PhoneConstants.State.IDLE);
    }

   public int btSimapConnectSIM(int simId,  BtSimapOperResponse btRsp) {
       final SendBtSimapProfile sendBtSapTh = SendBtSimapProfile.getInstance(mPhone);
       sendBtSapTh.setBtOperResponse(btRsp);
       if(sendBtSapTh.getState() == Thread.State.NEW) {
         sendBtSapTh.start();
       }
       int ret = sendBtSapTh.btSimapConnectSIM(simId);
       Log.d(LOG_TAG, "btSimapConnectSIM ret is " + ret + " btRsp.curType " + btRsp.getCurType() 
	 	+ " suptype " + btRsp.getSupportType() + " atr " + btRsp.getAtrString());	
       return ret;	
  
   }

   public int btSimapDisconnectSIM() {
   	Log.d(LOG_TAG, "btSimapDisconnectSIM");
       final SendBtSimapProfile sendBtSapTh = SendBtSimapProfile.getInstance(mPhone);
       if(sendBtSapTh.getState() == Thread.State.NEW) {
           sendBtSapTh.start();
       } 
       return sendBtSapTh.btSimapDisconnectSIM();
   }

   public int btSimapApduRequest(int type, String cmdAPDU,  BtSimapOperResponse btRsp) {
       final SendBtSimapProfile sendBtSapTh = SendBtSimapProfile.getInstance(mPhone);
       sendBtSapTh.setBtOperResponse(btRsp);
       if(sendBtSapTh.getState() == Thread.State.NEW) {
           sendBtSapTh.start();
       }
       return sendBtSapTh.btSimapApduRequest(type, cmdAPDU);
   }

   public int btSimapResetSIM(int type,  BtSimapOperResponse btRsp) {
       final SendBtSimapProfile sendBtSapTh = SendBtSimapProfile.getInstance(mPhone);
       sendBtSapTh.setBtOperResponse(btRsp);
       if(sendBtSapTh.getState() == Thread.State.NEW) {
           sendBtSapTh.start();
       }
       return sendBtSapTh.btSimapResetSIM(type);
   }

   public int btSimapPowerOnSIM(int type,  BtSimapOperResponse btRsp) {
       final SendBtSimapProfile sendBtSapTh = SendBtSimapProfile.getInstance(mPhone);
       sendBtSapTh.setBtOperResponse(btRsp);
       if(sendBtSapTh.getState() == Thread.State.NEW) {
           sendBtSapTh.start();
       }
       return sendBtSapTh.btSimapPowerOnSIM(type);
   }

   public int btSimapPowerOffSIM() {
       final SendBtSimapProfile sendBtSapTh = SendBtSimapProfile.getInstance(mPhone);
       if(sendBtSapTh.getState() == Thread.State.NEW) {
           sendBtSapTh.start();
       }
       return sendBtSapTh.btSimapPowerOffSIM();
   }
       /**
     * Helper thread to turn async call to {@link Phone#sendBtSimProfile} into
     * a synchronous one.
     */
    private static class SendBtSimapProfile extends Thread {
        private Phone mBtSapPhone;
        private boolean mDone = false;
        private String mStrResult = null;
        private ArrayList mResult;
	private int mRet = 1;	
        private BtSimapOperResponse mBtRsp;
        private Handler mHandler;

        private static SendBtSimapProfile sInstance;
        static final Object sInstSync = new Object();
        // For async handler to identify request type
        private static final int BTSAP_CONNECT_COMPLETE = 300;
	private static final int BTSAP_DISCONNECT_COMPLETE = 301;	
        private static final int BTSAP_POWERON_COMPLETE = 302;
        private static final int BTSAP_POWEROFF_COMPLETE = 303;
        private static final int BTSAP_RESETSIM_COMPLETE = 304;
        private static final int BTSAP_TRANSFER_APDU_COMPLETE = 305;

        public static SendBtSimapProfile getInstance(Phone phone) {
            synchronized (sInstSync) {
                if (sInstance == null) {
                    sInstance = new SendBtSimapProfile(phone);
                }
            }
            return sInstance;
        }
        private SendBtSimapProfile(Phone phone) {
            mBtSapPhone = phone;
            mBtRsp = null;
        } 


        public void setBtOperResponse(BtSimapOperResponse btRsp) {
            mBtRsp = btRsp;
        } 
	
        @Override
        public void run() {
            Looper.prepare();
            synchronized (SendBtSimapProfile.this) {
                mHandler = new Handler() {
                    @Override
                    public void handleMessage(Message msg) {
                        AsyncResult ar = (AsyncResult) msg.obj;
                        switch (msg.what) {
                            case BTSAP_CONNECT_COMPLETE:								
                                Log.d(LOG_TAG, "BTSAP_CONNECT_COMPLETE");
                                synchronized (SendBtSimapProfile.this) {
                                    if (ar.exception != null) {
                                        CommandException ce = (CommandException) ar.exception;
                                        if (ce.getCommandError() == CommandException.Error.BT_SAP_CARD_REMOVED){
                                            mRet = 4;
                                        }else if (ce.getCommandError() == CommandException.Error.BT_SAP_NOT_ACCESSIBLE){
                                            mRet = 2;
                                        }else {
                                            mRet = 1;
                                        }	
					     Log.e(LOG_TAG, "Exception BTSAP_CONNECT, Exception:" + ar.exception);
                                    } else {
                                        mStrResult = (String)(ar.result);
                                        Log.d(LOG_TAG, "BTSAP_CONNECT_COMPLETE  mStrResult " + mStrResult);	 
                                        String[] splited = mStrResult.split(",");

                                        try {	
                                            mBtRsp.setCurType(Integer.parseInt(splited[0].trim()));
                                            mBtRsp.setSupportType(Integer.parseInt(splited[1].trim()));
                                            mBtRsp.setAtrString(splited[2]);
                                            Log.d(LOG_TAG, "BTSAP_CONNECT_COMPLETE curType " + mBtRsp.getCurType() + " SupType " + mBtRsp.getSupportType() + " ATR " + mBtRsp.getAtrString());
                                        } catch (NumberFormatException e) {
                                            Log.d(LOG_TAG, "NumberFormatException" );
                                        }

                                        mRet = 0;
					     //Log.d(LOG_TAG, "BTSAP_CONNECT_COMPLETE curType " + (String)(mResult.get(0)) + " SupType " + (String)(mResult.get(1)) + " ATR " + (String)(mResult.get(2)));					 
					 }
					 
					//Log.d(LOG_TAG, "BTSAP_CONNECT_COMPLETE curType " + mBtRsp.getCurType() + " SupType " + mBtRsp.getSupportType() + " ATR " + mBtRsp.getAtrString());				
                                    mDone = true;
                                    SendBtSimapProfile.this.notifyAll();
                                }
                                break;
				case BTSAP_DISCONNECT_COMPLETE:								
                                Log.d(LOG_TAG, "BTSAP_DISCONNECT_COMPLETE");
                                synchronized (SendBtSimapProfile.this) {
                                    if (ar.exception != null) {
                                        CommandException ce = (CommandException) ar.exception;
                                        if (ce.getCommandError() == CommandException.Error.BT_SAP_CARD_REMOVED){
                                            mRet = 4;
                                        }else if (ce.getCommandError() == CommandException.Error.BT_SAP_NOT_ACCESSIBLE){
                                            mRet = 2;
                                        }else {
                                            mRet = 1;
                                        }	
                                        Log.e(LOG_TAG, "Exception BTSAP_DISCONNECT, Exception:" + ar.exception);	 
                                    } else {
                                        mRet = 0;
                                    }
                                    Log.d(LOG_TAG, "BTSAP_DISCONNECT_COMPLETE result is "+ mRet);				
                                    mDone = true;
                                    SendBtSimapProfile.this.notifyAll();
                                }
                                break;
				case BTSAP_POWERON_COMPLETE:								
                                Log.d(LOG_TAG, "BTSAP_POWERON_COMPLETE");
                                synchronized (SendBtSimapProfile.this) {
                                    if (ar.exception != null) {
                                        CommandException ce = (CommandException) ar.exception;
                                        if (ce.getCommandError() == CommandException.Error.BT_SAP_CARD_REMOVED){
                                            mRet = 4;
                                        }else if (ce.getCommandError() == CommandException.Error.BT_SAP_NOT_ACCESSIBLE){
                                            mRet = 2;
                                        }else {
                                            mRet = 1;
                                        }	 
					     Log.e(LOG_TAG, "Exception POWERON_COMPLETE, Exception:" + ar.exception);	 
                                    } else {
                                        mStrResult = (String)(ar.result);
                                        Log.d(LOG_TAG, "BTSAP_POWERON_COMPLETE  mStrResult " + mStrResult);	 
                                        String[] splited = mStrResult.split(",");

                                        try {	
                                            mBtRsp.setCurType(Integer.parseInt(splited[0].trim()));
                                            mBtRsp.setAtrString(splited[1]);
                                            Log.d(LOG_TAG, "BTSAP_POWERON_COMPLETE curType " + mBtRsp.getCurType() + " ATR " + mBtRsp.getAtrString());
                                        } catch (NumberFormatException e) {
                                            Log.d(LOG_TAG, "NumberFormatException" );
                                        }
                                        mRet = 0;
                                    }
			
                                    mDone = true;
                                    SendBtSimapProfile.this.notifyAll();
                                }
                                break;		
				case BTSAP_POWEROFF_COMPLETE:								
                                Log.d(LOG_TAG, "BTSAP_POWEROFF_COMPLETE");
                                synchronized (SendBtSimapProfile.this) {
                                    if (ar.exception != null) {
                                        CommandException ce = (CommandException) ar.exception;
                                        if (ce.getCommandError() == CommandException.Error.BT_SAP_CARD_REMOVED){
                                            mRet = 4;
                                        }else if (ce.getCommandError() == CommandException.Error.BT_SAP_NOT_ACCESSIBLE){
                                            mRet = 2;
                                        }else {
                                            mRet = 1;
                                        }	
                                        Log.e(LOG_TAG, "Exception BTSAP_POWEROFF, Exception:" + ar.exception);	 
                                    } else {
                                        mRet = 0;
                                    }
                                    Log.d(LOG_TAG, "BTSAP_POWEROFF_COMPLETE result is " + mRet);				
                                    mDone = true;
                                    SendBtSimapProfile.this.notifyAll();
                                }
                                break;	
				case BTSAP_RESETSIM_COMPLETE:								
                                Log.d(LOG_TAG, "BTSAP_RESETSIM_COMPLETE");
                                synchronized (SendBtSimapProfile.this) {
                                    if (ar.exception != null) {
                                        CommandException ce = (CommandException) ar.exception;
                                        if (ce.getCommandError() == CommandException.Error.BT_SAP_CARD_REMOVED){
                                            mRet = 4;
                                        }else if (ce.getCommandError() == CommandException.Error.BT_SAP_NOT_ACCESSIBLE){
                                            mRet = 2;
                                        }else {
                                            mRet = 1;
                                        }	
                                        Log.e(LOG_TAG, "Exception BTSAP_RESETSIM, Exception:" + ar.exception);	 
                                    } else {
                                        mStrResult = (String)(ar.result);
                                        Log.d(LOG_TAG, "BTSAP_RESETSIM_COMPLETE  mStrResult " + mStrResult);	 
                                        String[] splited = mStrResult.split(",");

                                        try {	
                                            mBtRsp.setCurType(Integer.parseInt(splited[0].trim()));
                                            mBtRsp.setAtrString(splited[1]);
                                            Log.d(LOG_TAG, "BTSAP_RESETSIM_COMPLETE curType " + mBtRsp.getCurType() + " ATR " + mBtRsp.getAtrString());
                                        } catch (NumberFormatException e) {
                                            Log.d(LOG_TAG, "NumberFormatException" );
                                        }
                                        mRet = 0;
                                    }

                                    mDone = true;
                                    SendBtSimapProfile.this.notifyAll();
                                }
                                break;						
				case BTSAP_TRANSFER_APDU_COMPLETE:								
                                Log.d(LOG_TAG, "BTSAP_TRANSFER_APDU_COMPLETE");
                                synchronized (SendBtSimapProfile.this) {
                                    if (ar.exception != null) {
                                        CommandException ce = (CommandException) ar.exception;
                                        if (ce.getCommandError() == CommandException.Error.BT_SAP_CARD_REMOVED){
                                            mRet = 4;
                                        }else if (ce.getCommandError() == CommandException.Error.BT_SAP_NOT_ACCESSIBLE){
                                            mRet = 2;
                                        }else {
                                            mRet = 1;
                                        }	 
						 
                                        Log.e(LOG_TAG, "Exception BTSAP_TRANSFER_APDU, Exception:" + ar.exception);	 
                                    } else {
                                        mBtRsp.setApduString((String)(ar.result));
                                        Log.d(LOG_TAG, "BTSAP_TRANSFER_APDU_COMPLETE result is " + mBtRsp.getApduString());				
                                        mRet = 0;
                                    }
					
                                    mDone = true;
                                    SendBtSimapProfile.this.notifyAll();
                                }
                                break;						
                        }
                    }
                };
                SendBtSimapProfile.this.notifyAll();
            }
            Looper.loop();
        }

        synchronized int btSimapConnectSIM(int simId) {
            int ret = 0;
            while (mHandler == null) {
                try {
                    wait();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            mDone = false;
            Message callback = Message.obtain(mHandler, BTSAP_CONNECT_COMPLETE);
            if (GeminiUtils.isGeminiSupport()) {
                ((GeminiPhone) mBtSapPhone).getPhonebyId(simId).sendBtSimProfile(0, 0, null, callback);
            } else {
                mBtSapPhone.sendBtSimProfile(0, 0, null, callback);
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
            if (mRet == 0) {
                // parse result
                if (GeminiUtils.isGeminiSupport()) {
                    ((GeminiPhone) mBtSapPhone).setBtConnectedSimId(simId);
                    Log.d(LOG_TAG, "synchronized btSimapConnectSIM GEMINI connect Sim is "
                            + ((GeminiPhone) mBtSapPhone).getBtConnectedSimId());
                }
                Log.d(LOG_TAG, "btSimapConnectSIM curType " + mBtRsp.getCurType() + " SupType "
                        + mBtRsp.getSupportType() + " ATR " + mBtRsp.getAtrString());
            } else {
                ret = mRet;
            }

            Log.d(LOG_TAG, "synchronized btSimapConnectSIM ret " + ret);
            return ret;
        }

        synchronized int btSimapDisconnectSIM() {
            int ret = 0;
            while (mHandler == null) {
                try {
                    wait();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            Log.d(LOG_TAG, "synchronized btSimapDisconnectSIM");		
            mDone = false;
            Message callback = Message.obtain(mHandler, BTSAP_DISCONNECT_COMPLETE);
            if (GeminiUtils.isGeminiSupport()) {
                final int slotId = ((GeminiPhone) mBtSapPhone).getBtConnectedSimId();
                Log.d(LOG_TAG, "synchronized btSimapDisconnectSIM GEMINI connect Sim is " + slotId);
                if (GeminiUtils.isValidSlot(slotId)) {
                    ((GeminiPhone) mBtSapPhone).getPhonebyId(slotId).sendBtSimProfile(1, 0, null, callback);
                } else {
                    ret = 7; // No sim has been connected
                    return ret;
                }
            } else {
                Log.d(LOG_TAG, "synchronized btSimapDisconnectSIM  not gemini ");
                mBtSapPhone.sendBtSimProfile(1, 0, null, callback);
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
            if (mRet == 0) {
                if (GeminiUtils.isGeminiSupport()) {
                    ((GeminiPhone) mBtSapPhone).setBtConnectedSimId(-1);
                }
            }
            ret = mRet;
            Log.d(LOG_TAG, "synchronized btSimapDisconnectSIM ret " + ret);   	 
            return ret;
        }

        synchronized int btSimapResetSIM(int type) {
            int ret = 0;
            while (mHandler == null) {
                try {
                    wait();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            mDone = false;
            Message callback = Message.obtain(mHandler, BTSAP_RESETSIM_COMPLETE);

            if (GeminiUtils.isGeminiSupport()) {
                final int slotId = ((GeminiPhone) mBtSapPhone).getBtConnectedSimId();
                Log.d(LOG_TAG, "synchronized btSimapResetSIM GEMINI connect slot is " + slotId);
                if (GeminiUtils.isValidSlot(slotId)) {
                    ((GeminiPhone) mBtSapPhone).getPhonebyId(slotId).sendBtSimProfile(4, type, null, callback);
                } else {
                    ret = 7; // No sim has been connected
                    return ret;
                }
            } else {
                mBtSapPhone.sendBtSimProfile(4, type, null, callback);
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
            if (mRet == 0)	{
                Log.d(LOG_TAG, "btSimapResetSIM curType " + mBtRsp.getCurType() + " ATR " + mBtRsp.getAtrString());		 
            } else {
                ret = mRet;
            }	

            Log.d(LOG_TAG, "synchronized btSimapResetSIM ret " + ret);   	 
            return ret;
        }

        synchronized int btSimapPowerOnSIM(int type)  {
            int ret = 0;
            while (mHandler == null) {
                try {
                    wait();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            mDone = false;
            Message callback = Message.obtain(mHandler, BTSAP_POWERON_COMPLETE);

            if (GeminiUtils.isGeminiSupport()) {
                final int slotId = ((GeminiPhone) mBtSapPhone).getBtConnectedSimId();
                Log.d(LOG_TAG, "synchronized btSimapResetSIM GEMINI connect slot is " + slotId);
                if (GeminiUtils.isValidSlot(slotId)) {
                    ((GeminiPhone) mBtSapPhone).getPhonebyId(slotId).sendBtSimProfile(2, type, null, callback);
                } else {
                    ret = 7; // No sim has been connected
                    return ret;
                }
            } else {
                mBtSapPhone.sendBtSimProfile(2, type, null, callback);
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
            if (mRet == 0)	{
                Log.d(LOG_TAG, "btSimapPowerOnSIM curType " + mBtRsp.getCurType() + " ATR " + mBtRsp.getAtrString());		 
            } else {
	        ret = mRet;
            }	
            Log.d(LOG_TAG, "synchronized btSimapPowerOnSIM ret " + ret);    
            return ret;
        }

        synchronized int btSimapPowerOffSIM() {
            int ret = 0;
            while (mHandler == null) {
                try {
                    wait();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            mDone = false;
            Message callback = Message.obtain(mHandler, BTSAP_POWEROFF_COMPLETE);

            if (GeminiUtils.isGeminiSupport()) {
                final int slotId = ((GeminiPhone) mBtSapPhone).getBtConnectedSimId();
                Log.d(LOG_TAG, "synchronized btSimapResetSIM GEMINI connect slot is " + slotId);
                if (GeminiUtils.isValidSlot(slotId)) {
                    ((GeminiPhone) mBtSapPhone).getPhonebyId(slotId).sendBtSimProfile(3, 0, null, callback);
                } else {
                    ret = 7; // No sim has been connected
                    return ret;
                }
            } else {
                mBtSapPhone.sendBtSimProfile(3, 0, null, callback);
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
            ret = mRet;
            Log.d(LOG_TAG, "synchronized btSimapPowerOffSIM ret " + ret);     
            return ret;
        }
	 
        synchronized int btSimapApduRequest(int type, String cmdAPDU) {
            int ret = 0;
            while (mHandler == null) {
                try {
                    wait();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            mDone = false;
            Message callback = Message.obtain(mHandler, BTSAP_TRANSFER_APDU_COMPLETE);

            if (GeminiUtils.isGeminiSupport()) {
                final int slotId = ((GeminiPhone) mBtSapPhone).getBtConnectedSimId();
                Log.d(LOG_TAG, "synchronized btSimapApduRequest GEMINI connect slotId is " + slotId);
                if (GeminiUtils.isValidSlot(slotId)) {
                    ((GeminiPhone) mBtSapPhone).getPhonebyId(slotId).sendBtSimProfile(5, type, cmdAPDU, callback);
                } else {
                    ret = 7; // No sim has been connected
                    return ret;
                }
            } else {
                mBtSapPhone.sendBtSimProfile(5, type, cmdAPDU, callback);
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
            if (mRet == 0)	{
                Log.d(LOG_TAG, "btSimapApduRequest APDU " + mBtRsp.getApduString());		 
            } else {
                ret = mRet;
            }	

            Log.d(LOG_TAG, "synchronized btSimapApduRequest ret " + ret);  	 
            return ret;
        }
    }
	   
  public String simAuth(String strRand) {
        final SimAuth doSimAuth = new SimAuth(mPhone);
        doSimAuth.start();
        return doSimAuth.doSimAuth(strRand);
    }

    public String uSimAuth(String strRand, String strAutn) {
        final SimAuth doUSimAuth = new SimAuth(mPhone);
        doUSimAuth.start();
        return doUSimAuth.doUSimAuth(strRand, strAutn);
    }

    /**
     * Helper thread to turn async call to {@link #SimAuthentication} into
     * a synchronous one.
     */
    private static class SimAuth extends Thread {
      //  private final IccCard mSimCard;
        private Phone mSAPhone;
        private boolean mDone = false;
        private String mResult = null;

        // For replies from SimCard interface
        private Handler mHandler;

        // For async handler to identify request type
        private static final int SIM_AUTH_COMPLETE = 200;
        private static final int USIM_AUTH_COMPLETE = 201;

 	public SimAuth(Phone phone) {
            mSAPhone = phone;
        } 
        @Override
        public void run() {
            Looper.prepare();
            synchronized (SimAuth.this) {
                mHandler = new Handler() {
                    @Override
                    public void handleMessage(Message msg) {
                        AsyncResult ar = (AsyncResult) msg.obj;
                        switch (msg.what) {
                            case SIM_AUTH_COMPLETE:
                            case USIM_AUTH_COMPLETE:
                                Log.d(LOG_TAG, "SIM_AUTH_COMPLETE");
                                synchronized (SimAuth.this) {
					 if (ar.exception != null) {
					     mResult = null;	 
					 } else {
					     mResult = (String)(ar.result);
					 }
					Log.d(LOG_TAG, "SIM_AUTH_COMPLETE result is " + mResult);				
                                    mDone = true;
                                    SimAuth.this.notifyAll();
                                }
                                break;
                        }
                    }
                };
                SimAuth.this.notifyAll();
            }
            Looper.loop();
        }

        synchronized String doSimAuth(String strRand) {

            while (mHandler == null) {
                try {
                    wait();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            Message callback = Message.obtain(mHandler, SIM_AUTH_COMPLETE);

            mSAPhone.doSimAuthentication(strRand, callback);
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

        synchronized String doUSimAuth(String strRand, String strAutn) {

            while (mHandler == null) {
                try {
                    wait();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            Message callback = Message.obtain(mHandler, USIM_AUTH_COMPLETE);

            mSAPhone.doUSimAuthentication(strRand, strAutn, callback);
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

	 synchronized String doSimAuthGemini(String strRand, int simId) {

            while (mHandler == null) {
                try {
                    wait();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            Message callback = Message.obtain(mHandler, SIM_AUTH_COMPLETE);

            ((GeminiPhone)mSAPhone).getPhonebyId(simId).doSimAuthentication(strRand, callback);
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

	 synchronized String doUSimAuthGemini(String strRand, String strAutn, int simId) {

            while (mHandler == null) {
                try {
                    wait();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            Message callback = Message.obtain(mHandler, USIM_AUTH_COMPLETE);

            ((GeminiPhone)mSAPhone).getPhonebyId(simId).doUSimAuthentication(strRand, strAutn, callback);
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
    
    public boolean setRadioOff() {
        enforceModifyPermission();
        if (GeminiUtils.isGeminiSupport()) {
            ((GeminiPhone) mPhone).setRadioMode(GeminiNetworkSubUtil.MODE_POWER_OFF);
        } else {
            mPhone.setRadioPower(false, true);
        }
        return true;
    }
    
    public int getPreciseCallState() {
        return DefaultPhoneNotifier.convertCallState(mCM.getState());
    }
    
   /**
     *get the services state for default SIM
     * @return sim indicator state.    
     *
    */ 
    public int getSimIndicatorState() {         
        return mPhone.getSimIndicatorState();

   }

   /**
     *get the network service state for default SIM
     * @return service state.    
     *
    */ 
    public Bundle getServiceState(){
        Bundle data = new Bundle();
        mPhone.getServiceState().fillInNotifierBundle(data);
        return data;     
    }

    /** --------------- MTK --------------------*/
    /* Fion add start */
    public void dialGemini(String number, int slotId) {
        if (GeminiUtils.isGeminiSupport()) {
            if (DBG) {
                log("dialGemini: " + number + ", slotId=" + slotId);
            }
            if (!GeminiUtils.isValidSlot(slotId)) {
                if (DBG) {
                    log("dialGemini: wrong slot id");
                }
                return;
            }
            String url = createTelUrl(number);
            if (url == null) {
                return;
            }

            /* get phone state */
            PhoneConstants.State state = ((GeminiPhone) mPhone).getPhonebyId(slotId).getState();

            if (state != PhoneConstants.State.OFFHOOK && state != PhoneConstants.State.RINGING) {
                Intent intent = new Intent(Intent.ACTION_DIAL, Uri.parse(url));
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                intent.putExtra(PhoneConstants.GEMINI_SIM_ID_KEY, slotId);
                mApp.startActivity(intent);
            }
        }
    }

    public void callGemini(String number, int slotId) {
        if (GeminiUtils.isGeminiSupport()) {
            if (DBG) {
                log("callGemini: " + number + ", slotId=" + slotId);
            }
            if (!GeminiUtils.isValidSlot(slotId)) {
                if (DBG) {
                    log("callGemini: wrong slot id");
                }
                return;
            }
            /// M: [mtk04070][130724]To check permission for Mobile Manager Service/Application. @{
            if (FeatureOption.MTK_MOBILE_MANAGEMENT) {
            	  if (!checkMoMSPhonePermission(number)) {
            		   return;
            	  }
            }
            /// @}
            enforceCallPermission();

            String url = createTelUrl(number);
            if (url == null) {
                return;
            }

            Intent intent = new Intent(Intent.ACTION_CALL, Uri.parse(url));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            // JB TEMP intent.setClassName(mApp,
            // PhoneGlobals.getCallScreenClassName());
            intent.putExtra(PhoneConstants.GEMINI_SIM_ID_KEY, slotId);
            mApp.startActivity(intent);
        }
    }

    /**
    * To check sub-permission for MoMS before using API.
    * 
    * @param subPermission	The permission to be checked.
    *
    * @return Return true if the permission is granted else return false.
    */
    private boolean checkMoMSPermission(String subPermission, Bundle data) {
       try {
              int result = PackageManager.PERMISSION_GRANTED;
              IMobileManagerService mMobileManager;
              IBinder binder = ServiceManager.getService(Context.MOBILE_SERVICE);
              mMobileManager = IMobileManagerService.Stub.asInterface(binder);
              if (data != null) {
                  result = mMobileManager.checkPermissionWithData(subPermission, Binder.getCallingUid(), data);
              } else {
                  result = mMobileManager.checkPermission(subPermission, Binder.getCallingUid());
              }
              if (result != PackageManager.PERMISSION_GRANTED) {
                 log("[Error]" + subPermission + " is not granted!!");
                 return false;
              }
       } catch (Exception e) {
              log("[Error]Failed to chcek permission: " +  subPermission);
              return false;
       }
       return true;
    }

    private boolean checkMoMSLocationPermission() {
        return checkMoMSPermission(SubPermissions.ACCESS_LOCATION, null);
    }

    private boolean checkMoMSPhonePermission(String number) {
        Bundle extraInfo = new Bundle();
        extraInfo.putString(IMobileManager.PARAMETER_PHONENUMBER, number);
        return checkMoMSPermission(SubPermissions.MAKE_CALL, extraInfo);
    }

    private boolean showCallScreenInternalGemini(boolean specifyInitialDialpadState,
                                           boolean initialDialpadState, int slotId) {
        if (GeminiUtils.isGeminiSupport()) {
            if (isIdleGemini(slotId)) {
                return false;
            }

            long callingId = Binder.clearCallingIdentity();
            try {
                Intent intent = null;
                intent.putExtra(PhoneConstants.GEMINI_SIM_ID_KEY, slotId);
                mApp.startActivity(intent);
            } finally {
                Binder.restoreCallingIdentity(callingId);
            }
        }
        return true;
    }

    public boolean showCallScreenGemini(int simId) {
        if (DBG) {
            log("showCallScreenGemini simId: " + simId);
        }

        return true;
    }

    public boolean showCallScreenWithDialpadGemini(boolean showDialpad, int slotId) {
        if (GeminiUtils.isGeminiSupport()) {
            if (DBG) {
                log("showCallScreenWithDialpadGemini simId: " + slotId);
            }
            if (!GeminiUtils.isValidSlot(slotId)) {
                if (DBG) {
                    log("showCallScreenWithDialpadGemini: wrong slot id");
                }
                return false;
            }

            showCallScreenInternalGemini(true, showDialpad, slotId);
        }
        return true;
    }

    public boolean endCallGemini(int slotId) {
        if (GeminiUtils.isGeminiSupport()) {
            if (DBG) {
                log("endCallGemini slotId: " + slotId);
            }
            if (!GeminiUtils.isValidSlot(slotId)) {
                if (DBG) {
                    log("endCallGemini: wrong slot id");
                }
                return false;
            }

            enforceCallPermission();

            if (Looper.myLooper() == mMainThreadHandler.getLooper()) {
                throw new RuntimeException(
                        "This method will deadlock if called from the main thread.");
            }

            MainThreadRequest request = new MainThreadRequest(null);
            Message msg = mMainThreadHandler.obtainMessage(CMD_END_CALL_GEMINI, slotId, 0, request);
            msg.sendToTarget();

            synchronized (request) {
                while (request.result == null) {
                    try {
                        request.wait();
                    } catch (InterruptedException e) {
                    }
                }
            }
            return (Boolean) (request.result);
        }
        return false;
    }

    public void answerRingingCallGemini(int slotId) {
        if (GeminiUtils.isGeminiSupport()) {
            if (DBG) {
                log("answerRingingCallGemini slot: " + slotId);
            }
            if (!GeminiUtils.isValidSlot(slotId)) {
                if (DBG) {
                    log("answerRingingCallGemini: wrong slot id");
                }
                return;
            }

            enforceModifyPermission();

            // review if need modify for not
            sendRequestAsync(CMD_ANSWER_RINGING_CALL_GEMINI);
        }
        return;
    }

    /* seem no need this Gemini api : review it */
    public void silenceRingerGemini(int slotId) {
        if (GeminiUtils.isGeminiSupport()) {
            if (DBG) {
                log("silenceRingerGemini slotId: " + slotId);
            }

            if (!GeminiUtils.isValidSlot(slotId)) {
                if (DBG) {
                    log("silenceRingerGemini: wrong slot id");
                }
                return;
            }
            enforceModifyPermission();
            sendRequestAsync(CMD_SILENCE_RINGER);
        }
    }

    public boolean isOffhookGemini(int slotId) {
        if (GeminiUtils.isGeminiSupport()) {
            if (DBG) {
                log("isOffhookGemini slotId: " + slotId);
            }

            if (!GeminiUtils.isValidSlot(slotId)) {
                if (DBG) {
                    log("isOffhookGemini: wrong slot id");
                }
                return false;
            }

            return (((GeminiPhone) mPhone).getPhonebyId(slotId).getState() == PhoneConstants.State.OFFHOOK);
        }
        return false;
    }

    public boolean isRingingGemini(int slotId) {
        if (GeminiUtils.isGeminiSupport()) {
            if (DBG) {
                log("isRingingGemini slotId: " + slotId);
            }

            if (!GeminiUtils.isValidSlot(slotId)) {
                if (DBG) {
                    log("isRingingGemini: wrong slot id");
                }
                return false;
            }

            return (((GeminiPhone) mPhone).getPhonebyId(slotId).getState() == PhoneConstants.State.RINGING);
        }
        return false;
    }

    public boolean isIdleGemini(int slotId) {
        if (GeminiUtils.isGeminiSupport()) {
            if (DBG) {
                log("isIdleGemini slotId: " + slotId);
            }

            if (!GeminiUtils.isValidSlot(slotId)) {
                if (DBG) {
                    log("isIdleGemini: wrong slot Id");
                }
                return false;
            }

            return (((GeminiPhone) mPhone).getPhonebyId(slotId).getState() == PhoneConstants.State.IDLE);
        }
        return false;
    }

    /* seem no need this Gemini api : review it */
    public void cancelMissedCallsNotificationGemini(int slotId) {
        if (GeminiUtils.isGeminiSupport()) {
            if (DBG) {
                log("cancelMissedCallsNotificationGemini slotid: " + slotId);
            }

            if (!GeminiUtils.isValidSlot(slotId)) {
                if (DBG) {
                    log("cancelMissedCallsNotificationGemini: wrong slot Id");
                }
                return;
            }

            enforceModifyPermission();
            mApp.notificationMgr.cancelMissedCallNotification();
        }
    }

    public int getActivePhoneTypeGemini(int slotId) {
        if (GeminiUtils.isGeminiSupport()) {
            if (DBG) {
                log("getActivePhoneTypeGemini slotId: " + slotId);
            }

            if (!GeminiUtils.isValidSlot(slotId)) {
                if (DBG) {
                    log("getActivePhoneTypeGemini: wrong slot Id");
                }
                return 0;
            }

            return ((GeminiPhone) mPhone).getPhoneTypeGemini(slotId);
        }
        return 0;

    }

    /* Fion add end */
    public boolean supplyPinGemini(String pin, int simId) {
        enforceModifyPermission();
        final UnlockSim checkSimPin = new UnlockSim(((GeminiPhone) mPhone).getPhonebyId(simId).getIccCard());
        checkSimPin.start();
        int [] resultArray = checkSimPin.unlockSim(null, pin);
        return (resultArray[0] == PhoneConstants.PIN_RESULT_SUCCESS) ? true : false;
    }

    public boolean supplyPukGemini(String puk, String pin, int simId) {
        enforceModifyPermission();
        final UnlockSim checkSimPin = new UnlockSim(((GeminiPhone)mPhone).getPhonebyId(simId).getIccCard());
        checkSimPin.start();  
        int [] resultArray = checkSimPin.unlockSim(puk, pin);
        return (resultArray[0] == PhoneConstants.PIN_RESULT_SUCCESS) ? true : false;   
    }

    public String simAuthGemini(String strRand, int simId) {
        Log.d(LOG_TAG, "simAuthGemini  strRand is " + strRand + " simId " + simId);
        final SimAuth doSimAuth = new SimAuth(mPhone);
        doSimAuth.start();
        String strRes = doSimAuth.doSimAuthGemini(strRand, simId);
        Log.d(LOG_TAG, "simAuthGemini Result is " + strRes);
        return strRes;
    }

    public String uSimAuthGemini(String strRand, String strAutn, int simId) {
        final SimAuth doUSimAuth = new SimAuth(mPhone);
        doUSimAuth.start();
        return doUSimAuth.doUSimAuthGemini(strRand, strAutn, simId);
    }

    public void updateServiceLocationGemini(int simId) {
        Slog.w(LOG_TAG, "Warning,updateServiceLocationGemini", new Throwable("tst"));
        ((GeminiPhone) mPhone).getPhonebyId(simId).updateServiceLocation();
    }

    public void enableLocationUpdatesGemini(int simId) {
        mApp.enforceCallingOrSelfPermission(
                android.Manifest.permission.CONTROL_LOCATION_UPDATES, null);
        ((GeminiPhone) mPhone).getPhonebyId(simId).enableLocationUpdates();		
    }

    public void disableLocationUpdatesGemini(int simId) {
        mApp.enforceCallingOrSelfPermission(
                android.Manifest.permission.CONTROL_LOCATION_UPDATES, null);
        ((GeminiPhone) mPhone).getPhonebyId(simId).disableLocationUpdates();		
    }

    //TODO: Remove for LEGO (move to ItelephonyEx and rename)
    public boolean isRadioOnGemini(int simId) {
        return ((GeminiPhone)mPhone).isRadioOnGemini(simId);
    }
    public void setGprsTransferType(int type) {   
        mPhone.setGprsTransferType(type, null);
    }

     /*Add by mtk80372 for Barcode number*/
    public void getMobileRevisionAndImei(int type, Message message){
        mPhone.getMobileRevisionAndImei(type, message);
    }
     
    //TODO:remove for LEGO     
    //public void setGprsTransferTypeGemini(int type, int simId) {
    //    ((GeminiPhone)mPhone).getPhonebyId(simId).setGprsTransferType(type, null);
    //}

    //TODO:remove for LEGO(move to ItelephonyEx and rename)
    public int getNetworkTypeGemini(int simId) {                
        return ((GeminiPhone)mPhone).getPhonebyId(simId).getServiceState().getNetworkType();		
    }    

    //MTK-START [mtk02772]sdk api refactoring start

    /**
     * Gets a constant indicating the state of the device SIM card.
     * <p>
     * @param simId  Indicates which SIM (slot) to query
     * <p>
     * @return       Constant indicating the state of the device SIM card
     */
    public int getSimState(int simId) {
        String prop = SystemProperties.get(ITEL_PROPERTY_SIM_STATE[simId]);
        Log.d( LOG_TAG,"getSimState simId = " + simId + "prop = " + prop);

        if ("ABSENT".equals(prop)) {
            return android.telephony.TelephonyManager.SIM_STATE_ABSENT;
        }
        else if ("PIN_REQUIRED".equals(prop)) {
            return android.telephony.TelephonyManager.SIM_STATE_PIN_REQUIRED;
        }
        else if ("PUK_REQUIRED".equals(prop)) {
            return android.telephony.TelephonyManager.SIM_STATE_PUK_REQUIRED;
        }
        else if ("NETWORK_LOCKED".equals(prop)) {
            return android.telephony.TelephonyManager.SIM_STATE_NETWORK_LOCKED;
        }
        else if ("READY".equals(prop)) {
            return android.telephony.TelephonyManager.SIM_STATE_READY;
        }
        else {
            return android.telephony.TelephonyManager.SIM_STATE_UNKNOWN;
        }
    }

    /**
     * Gets the MCC+MNC (mobile country code + mobile network code) of the provider of the SIM. 5 or 6 decimal digits. 
     *
     * Availability: SIM state must be SIM_STATE_READY.
     * <p>
     * @param simId  Indicates which SIM (slot) to query
     * <p>
     * @return       MCC+MNC (mobile country code + mobile network code) of the provider of the SIM. 5 or 6 decimal digits.
     */
    public String getSimOperator(int simId) {
        String prop = SystemProperties.get(ITEL_PROPERTY_ICC_OPERATOR_NUMERIC[simId]);
        Log.d( LOG_TAG,"getSimOperator simId = " + simId + "getSimOperator = " + prop);
        return prop;
    }


    /**
     * Gets the Service Provider Name (SPN).
     *
     * Availability: SIM state must be SIM_STATE_READY.
     * <p>
     * @param simId  Indicates which SIM (slot) to query
     * <p>
     * @return       Service Provider Name (SPN).
     */
    public String getSimOperatorName(int simId) {
        String prop = SystemProperties.get(ITEL_PROPERTY_ICC_OPERATOR_ALPHA[simId]);
        Log.d( LOG_TAG,"getSimOperatorName simId = " + simId + "prop = " + prop);
        return prop;
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

    /**
     * @param simId sim card id
     * @return Get IPhoneSubInfo service
     */
    public IPhoneSubInfo getSubscriberInfo(int simId) {
        Log.d( LOG_TAG,"getSubscriberInfo simId = " + simId);
        return IPhoneSubInfo.Stub.asInterface(ServiceManager.getService(PHONE_SUBINFO_SERVICE[simId]));
    }

    /**
     * Gets the serial number of the SIM, if applicable
     * <p>
     * Required Permission:
     *   {@link android.Manifest.permission#READ_PHONE_STATE READ_PHONE_STATE}
     * <p>
     * @param simId  Indicates which SIM (slot) to query
     * <p>
     * @return       Serial number of the SIM, if applicable. Null is returned if it is unavailable.
     */
    public String getSimSerialNumber(int simId) {
        Log.d( LOG_TAG,"getSimSerialNumber");
        try {
            return getSubscriberInfo(simId).getIccSerialNumber();
        } catch (RemoteException ex) {
            return null;
        } catch (NullPointerException ex) {
            // This could happen before phone restarts due to crashing
            return null;
        }
    }

    /**
     * Gemini
     * Returns the alphabetic identifier associated with the line 1 number.
     * Return null if it is unavailable.
     * <p>
     * Requires Permission:
     *   {@link android.Manifest.permission#READ_PHONE_STATE READ_PHONE_STATE}
     * @hide
     */
    public String getLine1AlphaTag(int simId) {
        try {
            return getSubscriberInfo(simId).getLine1AlphaTag();
        } catch (RemoteException ex) {
            return null;
        } catch (NullPointerException ex) {
            // This could happen before phone restarts due to crashing
            return null;
        }
    }

    /**
     * Gets the voice mail number.
     * <p>
     * Required Permission:
     *   {@link android.Manifest.permission#READ_PHONE_STATE READ_PHONE_STATE}
     * <p>
     * @param simId  Indicates which SIM (slot) to query
     * <p>
     * @return       Voice mail number. Null is returned if it is unavailable.
     */
    public String getVoiceMailNumber(int simId) {
        Log.d( LOG_TAG,"getVoiceMailNumber");
        try {
            return getSubscriberInfo(simId).getVoiceMailNumber();
        } catch (RemoteException ex) {
            return null;
        } catch (NullPointerException ex) {
            // This could happen before phone restarts due to crashing
            return null;
        }
    }

    /**
     * Retrieves the alphabetic identifier associated with the voice mail number.
     * <p>
     * Required Permission:
     *   {@link android.Manifest.permission#READ_PHONE_STATE READ_PHONE_STATE}
     * <p>
     * @param simId  Indicates which SIM (slot) to query
     * <p>
     * @return       the Alphabetic identifier associated with the voice mail number
     */
    public String getVoiceMailAlphaTag(int simId) {
        Log.d( LOG_TAG,"getVoiceMailAlphaTag");
        try {
            return getSubscriberInfo(simId).getVoiceMailAlphaTag();
        } catch (RemoteException ex) {
            return null;
        } catch (NullPointerException ex) {
            // This could happen before phone restarts due to crashing
            return null;
        }
    }
    //MTK-START [mtk02772]sdk api refactoring end
    
    /**
     * Enable PDP interface by apn type and sim id
     *
     * @param type enable pdp interface by apn type, such as PhoneConstants.APN_TYPE_MMS, etc.
     * @param simId Indicate which sim(slot) to query
     * @return PhoneConstants.APN_REQUEST_STARTED: action is already started
     * PhoneConstants.APN_ALREADY_ACTIVE: interface has already active
     * PhoneConstants.APN_TYPE_NOT_AVAILABLE: invalid APN type
     * PhoneConstants.APN_REQUEST_FAILED: request failed
     * PhoneConstants.APN_REQUEST_FAILED_DUE_TO_RADIO_OFF: readio turn off
     * @see #disableApnTypeGemini()
     */
    public int enableApnTypeGemini(String type, int simId) {
        enforceModifyPermission();
        return ((GeminiPhone)mPhone).enableApnTypeGemini(type, simId);
    }

    public int disableApnTypeGemini(String type, int simId) {
        enforceModifyPermission();
        return ((GeminiPhone)mPhone).disableApnTypeGemini(type, simId);
    }
    
    public int getVoiceMessageCountGemini(int simId) {
        return mPhone.getVoiceMessageCount();
    }

    public void setDefaultPhone(int simId) {
        log("setDefaultPhone to SIM" + (simId+1));
        ((GeminiPhone)mPhone).setDefaultPhone(simId);
    }

    public boolean isVTIdle()
    {
/*JB TEMP
    	if( FeatureOption.MTK_VT3G324M_SUPPORT == true )
    	{
    		return VTCallUtils.isVTIdle() ;
    	}
    	else
    	{
    		return true;
    	}
*/
        return true;
    }

    /**
     * @return SMS default SIM. 
     * @internal
     */ 
    public int getSmsDefaultSim() {
        if (GeminiUtils.isGeminiSupport()) {
            return ((GeminiPhone) mPhone).getSmsDefaultSim();
        } else {
            return SystemProperties.getInt(PhoneConstants.GEMINI_DEFAULT_SIM_PROP,
                    PhoneConstants.GEMINI_SIM_1);
        }
    }
        
    //MTK-END [mtk04070][111117][ALPS00093395]MTK proprietary methods
    //MTK-START [mtk03851][111216][ALPS00093395]MTK proprietary methods
    private ArrayList<MessengerWrapper> mMessengerWrapperList = new ArrayList<MessengerWrapper>();

    private class MessengerWrapper {
        private Messenger mMessenger;

        private Handler mInternalHandler = new Handler(mMainThreadHandler.getLooper()) {
            @Override
            public void handleMessage(Message msg) {
                try {
                    Log.d(LOG_TAG, "MessengerWrapper callback triggered: " + msg.what);
                    mMessenger.send(Message.obtain(this, msg.what));
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
            }
        };

        public MessengerWrapper(IBinder binder) {
            mMessenger = new Messenger(binder);
        }

        public Messenger getMessenger() {
            return mMessenger;
        }

        public Handler getHandler() {
            return mInternalHandler;
        }
    };

    public void registerForSimModeChange(IBinder binder, int what) {
        if (binder != null) {
            Log.d(LOG_TAG, "registerForSimModeChange: " + binder + ", " + what);
            MessengerWrapper messengerWrapper = new MessengerWrapper(binder);
            mMessengerWrapperList.add(messengerWrapper);
            ((GeminiPhone)mPhone).registerForSimModeChange(messengerWrapper.getHandler(), what, null);
        }
    }

    public void unregisterForSimModeChange(IBinder binder) {
        Iterator<MessengerWrapper> iter = mMessengerWrapperList.iterator();
        while (iter.hasNext()) {
            MessengerWrapper messengerWrapper = (MessengerWrapper)iter.next();
            if (messengerWrapper.getMessenger().getBinder() == binder) {
                ((GeminiPhone)mPhone).unregisterForSimModeChange(messengerWrapper.getHandler());
                mMessengerWrapperList.remove(messengerWrapper);
                break;
            }
        }
    }

    /*  set all gsm.roaming.indcator.needed.x properties.
         *  @internal
        */
    public void setRoamingIndicatorNeddedProperty(boolean property) {
        for(int simIdx=PhoneConstants.GEMINI_SIM_1;simIdx<PhoneConstants.GEMINI_SIM_NUM;simIdx++){          
            String property_name = "gsm.roaming.indicator.needed";
            if(simIdx > PhoneConstants.GEMINI_SIM_1){
                property_name = property_name + "." + (simIdx+1) ;
            }					
            SystemProperties.set(property_name, property ? "true" : "false");
        }
    }

    /**
     * Get the count of missed call.
     *
     * @return Return the count of missed call. 
     */
    public int getMissedCallCount() {
        return mApp.notificationMgr.getMissedCallCount();
    }
    
    /**
      Description : Adjust modem radio power for Lenovo SAR requirement.
	  AT command format: AT+ERFTX=<op>,<para1>,<para2>
	  Description : When <op>=1	 -->  TX power reduction
				    <para1>:  2G L1 reduction level, default is 0
				    <para2>:  3G L1 reduction level, default is 0
				    level scope : 0 ~ 64
      Arthur      : mtk04070
      Date        : 2012.01.09
      Return value: True for success, false for failure
    */
    public boolean adjustModemRadioPower(int level_2G, int level_3G) {
        boolean result = ((level_2G >= 0) && (level_2G <= 255) && (level_3G >= 0) && (level_3G <= 255));
        Log.d(LOG_TAG, "adjustModemRadioPower");
        if (true == result) {
            String cmdStr[] = { "AT+ERFTX=1,", "" };
            cmdStr[0] = cmdStr[0] + level_2G + "," + level_3G;
            mPhone.invokeOemRilRequestStrings(cmdStr, 
		   	                mMainThreadHandler.obtainMessage(CMD_ADJUST_MODEM_RADIO_POWER));
            Log.d(LOG_TAG, cmdStr[0] + " ");
        }
        
        return result;
    }

    /**
      Description      : Adjust modem radio power by band for Lenovo SAR requirement.
      AT command format: AT+ERFTX=<op>,<rat>,<band>,<para1>...<paraX>
      Description : <op>=3	 -->  TX power reduction by given band
                    <rat>    -->  1 for 2G, 2 for 3G
                    <band>   -->  2G or 3G band value
				    <para1>~<paraX> -->  Reduction level
				    level scope : 0 ~ 255
      Arthur      : mtk04070
      Date        : 2012.05.31
      Return value: True for success, false for failure
    */
    public boolean adjustModemRadioPowerByBand(int rat, int band, int level) {
        int i, count = (rat == 1) ? 8 : 2;
        int totalParameters = (rat == 1) ? 32 : 40;
        int headParameters, restParameters; 
        String cmdStr[] = { "AT+ERFTX=3,", "" };

        headParameters = count * band; 
        restParameters = totalParameters - headParameters - count;
        cmdStr[0] = cmdStr[0] + rat;
        for (i = 0; i < headParameters; i++) {
	    cmdStr[0] = cmdStr[0] + ",";
        }
        for (i = 0; i < count; i++) {
            cmdStr[0] = cmdStr[0] + "," + level;
        }
        for (i = 0; i < restParameters; i++) {
	    cmdStr[0] = cmdStr[0] + ",";
        }
        Log.d(LOG_TAG, "adjustModemRadioPowerByBand - " + cmdStr[0]);
        mPhone.invokeOemRilRequestStrings(cmdStr, 
                                          mMainThreadHandler.obtainMessage(CMD_ADJUST_MODEM_RADIO_POWER));
        
        return true;
    }

    // MVNO-API START
    public String getMvnoMatchType(int simId) {
        if (!GeminiUtils.isGeminiSupport()) {
            Log.d(LOG_TAG, "getMvnoMatchType: Single Card");
            return mPhone.getMvnoMatchType();
        } else {
            Log.d(LOG_TAG, "getMvnoMatchType: Gemini Card");
            return ((GeminiPhone)mPhone).getMvnoMatchType(simId);
        }
    }
 
    public String getMvnoPattern(String type, int simId){
        if (!GeminiUtils.isGeminiSupport()) {
            Log.d(LOG_TAG, "getMvnoPattern: Single Card");
            return mPhone.getMvnoPattern(type);
        } else {
            Log.d(LOG_TAG, "getMvnoPattern: Gemini Card");
            return ((GeminiPhone)mPhone).getMvnoPattern(type, simId);
        }
    }
    // MVNO-API END
    //MTK-END [mtk03851][111216][ALPS00093395]MTK proprietary methods

    /**
     * Gemini
     * Returns the alphabetic name of current registered operator.
     * <p>
     * Availability: Only when user is registered to a network. Result may be
     * unreliable on CDMA networks (use {@link #getPhoneType()} to determine if
     * on a CDMA network).
     */
    public String getNetworkOperatorNameGemini(int slotId) {
        Log.d(LOG_TAG, "getNetworkOperatorName slotId=" + slotId);

        if (slotId == PhoneConstants.GEMINI_SIM_4) {
            return SystemProperties.get(TelephonyProperties.PROPERTY_OPERATOR_ALPHA_4);
        } else if (slotId == PhoneConstants.GEMINI_SIM_3) {
            return SystemProperties.get(TelephonyProperties.PROPERTY_OPERATOR_ALPHA_3);
        } else if (slotId == PhoneConstants.GEMINI_SIM_2) {
            return SystemProperties.get(TelephonyProperties.PROPERTY_OPERATOR_ALPHA_2);
        } else {
            return SystemProperties.get(TelephonyProperties.PROPERTY_OPERATOR_ALPHA);
        }
    }

    /**
     * Gemini
     * Returns the numeric name (MCC+MNC) of current registered operator.
     * <p>
     * Availability: Only when user is registered to a network. Result may be
     * unreliable on CDMA networks (use {@link #getPhoneType()} to determine if
     * on a CDMA network).
     */
    public String getNetworkOperatorGemini(int slotId) {
        Log.d(LOG_TAG, "getNetworkOperator slotId=" + slotId);
        if (slotId == PhoneConstants.GEMINI_SIM_4) {
            return SystemProperties.get(TelephonyProperties.PROPERTY_OPERATOR_NUMERIC_4);
        } else if (slotId == PhoneConstants.GEMINI_SIM_3) {
            return SystemProperties.get(TelephonyProperties.PROPERTY_OPERATOR_NUMERIC_3);
        } else if (slotId == PhoneConstants.GEMINI_SIM_2) {
            return SystemProperties.get(TelephonyProperties.PROPERTY_OPERATOR_NUMERIC_2);
        } else {
            return SystemProperties.get(TelephonyProperties.PROPERTY_OPERATOR_NUMERIC);
        }
    }

    /**
     * Gemini
     * Returns true if the device is considered roaming on the current
     * network, for GSM purposes.
     * <p>
     * Availability: Only when user registered to a network.
     */
    public boolean isNetworkRoamingGemini(int slotId) {
        Log.d(LOG_TAG, "isNetworkRoaming slotId=" + slotId);
        if (slotId == PhoneConstants.GEMINI_SIM_4) {
            return "true".equals(SystemProperties
                    .get(TelephonyProperties.PROPERTY_OPERATOR_ISROAMING_4));
        } else if (slotId == PhoneConstants.GEMINI_SIM_3) {
            return "true".equals(SystemProperties
                    .get(TelephonyProperties.PROPERTY_OPERATOR_ISROAMING_3));
        } else if (slotId == PhoneConstants.GEMINI_SIM_2) {
            return "true".equals(SystemProperties
                    .get(TelephonyProperties.PROPERTY_OPERATOR_ISROAMING_2));
        } else {
            return "true".equals(SystemProperties
                    .get(TelephonyProperties.PROPERTY_OPERATOR_ISROAMING));
        }
    }

    /**
     * Gemini
     * Returns the ISO country code equivilent of the current registered
     * operator's MCC (Mobile Country Code).
     * <p>
     * Availability: Only when user is registered to a network. Result may be
     * unreliable on CDMA networks (use {@link #getPhoneType()} to determine if
     * on a CDMA network).
     */
    public String getNetworkCountryIsoGemini(int slotId) {
        Log.d(LOG_TAG, "getNetworkCountryIso simId=" + slotId);
        if (slotId == PhoneConstants.GEMINI_SIM_4) {
            return SystemProperties.get(TelephonyProperties.PROPERTY_OPERATOR_ISO_COUNTRY_4);
        } else if (slotId == PhoneConstants.GEMINI_SIM_3) {
            return SystemProperties.get(TelephonyProperties.PROPERTY_OPERATOR_ISO_COUNTRY_3);
        } else if (slotId == PhoneConstants.GEMINI_SIM_2) {
            return SystemProperties.get(TelephonyProperties.PROPERTY_OPERATOR_ISO_COUNTRY_2);
        } else {
            return SystemProperties.get(TelephonyProperties.PROPERTY_OPERATOR_ISO_COUNTRY);
        }
    }
    public int dialUpCsd(int simId, String dialUpNumber) {
        Log.d( LOG_TAG, "dialUpCsd simId=" + simId + " dialUpNumber=" + dialUpNumber);
        return mPhone.dialUpCsd(simId, dialUpNumber);
    }
}
