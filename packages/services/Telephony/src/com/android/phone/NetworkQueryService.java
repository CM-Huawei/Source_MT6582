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
 * Copyright (C) 2008 The Android Open Source Project
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

import android.app.Service;
import android.content.Intent;
import android.os.AsyncResult;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.RemoteCallbackList;
import android.os.RemoteException;
import android.util.Log;

import com.android.internal.telephony.OperatorInfo;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.PhoneFactory;

import com.mediatek.common.featureoption.FeatureOption;
import com.mediatek.phone.PhoneLog;
import com.mediatek.phone.ext.ExtensionManager;
import com.mediatek.phone.ext.SettingsExtension;
import com.mediatek.phone.gemini.GeminiUtils;
import com.mediatek.phone.wrapper.PhoneWrapper;
import com.mediatek.xlog.Xlog;

import java.util.ArrayList;

/**
 * Service code used to assist in querying the network for service
 * availability.   
 */
public class NetworkQueryService extends Service {
    // debug data
    private static final String LOG_TAG = "Settings/NetworkQuery";
    private static final boolean DBG = true;

    // static events
    private static final int EVENT_NETWORK_SCAN_COMPLETED = 100; 
    
    // static states indicating the query status of the service 
    private static final int QUERY_READY = -1;
    private static final int QUERY_IS_RUNNING = -2;
    
    // error statuses that will be retured in the callback.
    public static final int QUERY_OK = 0;
    public static final int QUERY_EXCEPTION = 1;
    
    /** state of the query service */
    private int mState;
    
    /** local handle to the phone object */
    private Phone mPhone;

    /**
     * Class for clients to access.  Because we know this service always
     * runs in the same process as its clients, we don't need to deal with
     * IPC.
     */
    public class LocalBinder extends Binder {
        public INetworkQueryService getService() {
            return mBinder;
        }
    }
    private final IBinder mLocalBinder = new LocalBinder();

    /**
     * Local handler to receive the network query compete callback
     * from the RIL.
     */
    Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                // if the scan is complete, broadcast the results.
                // to all registerd callbacks.
                case EVENT_NETWORK_SCAN_COMPLETED:
                case EVENT_NETWORK_SCAN_COMPLETED_2:
                case EVENT_NETWORK_SCAN_COMPLETED_3:
                case EVENT_NETWORK_SCAN_COMPLETED_4:
                    // / M: GEMINI @{
                    if (!isValidMessage(msg)) {
                        return;
                    }
                    /// @}
                    if (DBG) log("scan completed, broadcasting results");
                    broadcastQueryResults((AsyncResult) msg.obj);
                    break;
                /// M: Add cancel action to getAvailableNetworks. @{
                case EVENT_CANCEL_NETWORK_SCAN_COMPLETED:
                case EVENT_CANCEL_NETWORK_SCAN_COMPLETED_2:
                case EVENT_CANCEL_NETWORK_SCAN_COMPLETED_3:
                case EVENT_CANCEL_NETWORK_SCAN_COMPLETED_4:
                    // We do nothing...
                    if (DBG) log("cancel get available networks action... ");
                    break;
                /// @}
            }
        }
    };

    /** 
     * List of callback objects, also used to synchronize access to 
     * itself and to changes in state.
     */
    final RemoteCallbackList<INetworkQueryServiceCallback> mCallbacks =
        new RemoteCallbackList<INetworkQueryServiceCallback>();
    
    /**
     * Implementation of the INetworkQueryService interface.
     */
    private final INetworkQueryService.Stub mBinder = new INetworkQueryService.Stub() {
        
        /**
         * Starts a query with a INetworkQueryServiceCallback object if
         * one has not been started yet.  Ignore the new query request
         * if the query has been started already.  Either way, place the
         * callback object in the queue to be notified upon request 
         * completion.
         */
        public void startNetworkQuery(INetworkQueryServiceCallback cb) {
            if (cb != null) {
                // register the callback to the list of callbacks.
                synchronized (mCallbacks) {
                    mCallbacks.register(cb);
                    if (DBG) log("registering callback " + cb.getClass().toString());

                    switch (mState) {
                        case QUERY_READY:
                            // TODO: we may want to install a timeout here in case we
                            // do not get a timely response from the RIL.
                            /// M: support gemini phone. @{
                            // Google code:
                            /*
                            mPhone.getAvailableNetworks(
                                    mHandler.obtainMessage(EVENT_NETWORK_SCAN_COMPLETED));
                            */
                            int msgType = EVENT_NETWORK_SCAN_COMPLETED;
                            if (FeatureOption.MTK_GEMINI_SUPPORT) {
                                msgType = getSimMsgType(mSlotId);
                                log("startNetworkQuery---msgType=" + msgType);
                            }
                            PhoneWrapper.getAvailableNetworks(mPhone,
                                    mHandler.obtainMessage(msgType), mSlotId);
                            /// @}
                            mState = QUERY_IS_RUNNING;
                            if (DBG) log("starting new query");
                            break;

                        // do nothing if we're currently busy.
                        case QUERY_IS_RUNNING:
                            if (DBG) log("query already in progress");
                            break;
                        default:
                    }
                }
            }
        }

        /**
         * Stops a query with a INetworkQueryServiceCallback object as
         * a token.
         */
        public void stopNetworkQuery(INetworkQueryServiceCallback cb) {
            // currently we just unregister the callback, since there is 
            // no way to tell the RIL to terminate the query request.  
            // This means that the RIL may still be busy after the stop 
            // request was made, but the state tracking logic ensures 
            // that the delay will only last for 1 request even with 
            // repeated button presses in the NetworkSetting activity.

            /// M: Add cancel action to getAvailableNetworks. @{
            if (DBG) log("[stopNetworkQuery] cancelAvailableNetworks to slot = "
                    + mSlotId + "; mState = " + mState);
            if (QUERY_IS_RUNNING == mState) {
                PhoneWrapper.cancelAvailableNetworks(mPhone,
                        mHandler.obtainMessage(EVENT_CANCEL_NETWORK_SCAN_COMPLETED_GEMINI[mSlotId]), mSlotId);
            }
            /// @}

            if (cb != null) {
                synchronized (mCallbacks) {
                    if (DBG) log("unregistering callback " + cb.getClass().toString());
                    mCallbacks.unregister(cb);
                }
            }
        }
    };
    
    @Override
    public void onCreate() {
        mState = QUERY_READY;
        mPhone = PhoneFactory.getDefaultPhone();
        /// M: Initialize plugin, to public mBinder for remote client
        mSettingsExt = ExtensionManager.getInstance().getSettingsExtension();
    }
    
    /**
     * Required for service implementation.
     */
    /*
    @Override
    public void onStart(Intent intent, int startId) {
    }
    */

    /**
     * Handle the bind request.
     */
    @Override
    public IBinder onBind(Intent intent) {
        // TODO: Currently, return only the LocalBinder instance.  If we
        // end up requiring support for a remote binder, we will need to 
        // return mBinder as well, depending upon the intent.
        if (DBG) {
            log("binding service implementation");
        }
        /// M: Return remote binder for client in OP09 plugin @{
        if (!mSettingsExt.shouldPublicRemoteBinder()) {
            return mLocalBinder;
        } else {
            return mBinder;
        }
        /// @}
    }

    /**
     * Broadcast the results from the query to all registered callback
     * objects. 
     */
    private void broadcastQueryResults(AsyncResult ar) {
        // reset the state.
        synchronized (mCallbacks) {
            mState = QUERY_READY;
            
            // see if we need to do any work.
            if (ar == null) {
                if (DBG) {
                    log("AsyncResult is null.");
                }
                return;
            }
    
            // TODO: we may need greater accuracy here, but for now, just a
            // simple status integer will suffice.
            int exception = (ar.exception == null) ? QUERY_OK : QUERY_EXCEPTION;
            if (DBG) {
                log("AsyncResult has exception " + exception);
            }
            
            // Make the calls to all the registered callbacks.
            for (int i = (mCallbacks.beginBroadcast() - 1); i >= 0; i--) {
                INetworkQueryServiceCallback cb = mCallbacks.getBroadcastItem(i); 
                if (DBG) {
                    log("broadcasting results to " + cb.getClass().toString());
                }
                try {
                    cb.onQueryComplete((ArrayList<OperatorInfo>) ar.result, exception);
                } catch (RemoteException e) {
                    log("e = " + e);
                }
            }
            
            // finish up.
            mCallbacks.finishBroadcast();
        }
    }

    private static void log(String msg) {
        Log.d(LOG_TAG, msg);
    }

    // ------------------- MTK -------------------------
    /// M: GEMINI phone @{
    private static final int EVENT_NETWORK_SCAN_COMPLETED_2 = 101;
    private static final int EVENT_NETWORK_SCAN_COMPLETED_3 = 102;
    private static final int EVENT_NETWORK_SCAN_COMPLETED_4 = 103;
    private static final int[] EVENT_NETWORK_SCAN_COMPLETED_GEMINI = {
            EVENT_NETWORK_SCAN_COMPLETED, EVENT_NETWORK_SCAN_COMPLETED_2,
            EVENT_NETWORK_SCAN_COMPLETED_3, EVENT_NETWORK_SCAN_COMPLETED_4
    };

    // Add cancel action to getAvailableNetworks. @{
    private static final int EVENT_CANCEL_NETWORK_SCAN_COMPLETED = 200;
    private static final int EVENT_CANCEL_NETWORK_SCAN_COMPLETED_2 = 201;
    private static final int EVENT_CANCEL_NETWORK_SCAN_COMPLETED_3 = 202;
    private static final int EVENT_CANCEL_NETWORK_SCAN_COMPLETED_4 = 203;
    private static final int[] EVENT_CANCEL_NETWORK_SCAN_COMPLETED_GEMINI = {
        EVENT_CANCEL_NETWORK_SCAN_COMPLETED, EVENT_CANCEL_NETWORK_SCAN_COMPLETED_2,
        EVENT_CANCEL_NETWORK_SCAN_COMPLETED_3, EVENT_CANCEL_NETWORK_SCAN_COMPLETED_4,
    };
    // @}

    private int mSlotId = -1;
    /// @}

    /// M: CT plugin, used to public INetworkQueryService for remote process call
    private SettingsExtension mSettingsExt;

    /**
     * If the message is not for current mSlot, re_query for available networks.
     * @param msg
     * @return
     */
    private boolean isValidMessage(Message msg) {
        int slotId = GeminiUtils.getIndexInArray(msg.what, EVENT_NETWORK_SCAN_COMPLETED_GEMINI);
        PhoneLog.d(LOG_TAG, "mSlotId=" + mSlotId + ", current messget slotId=" + slotId);

        if (GeminiUtils.isGeminiSupport() && mSlotId != slotId) {
            PhoneLog.d(LOG_TAG, "slot " + mSlotId + " receives the query result of slot " + slotId);
            mState = QUERY_READY;
            PhoneWrapper.getAvailableNetworks(mPhone, mHandler.obtainMessage(getSimMsgType(mSlotId)), mSlotId);
            mState = QUERY_IS_RUNNING;
            return false;
        }
        return true;
    }

    // start a new query
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            mSlotId = intent.getIntExtra(PhoneConstants.GEMINI_SIM_ID_KEY, -1);
        } else {
            mSlotId = -1;
        }

        PhoneLog.d(LOG_TAG, "onStartCommand, slot=" + mSlotId);
        return Service.START_NOT_STICKY;
    }

    private int getSimMsgType(int slotId) {
        int msgType = EVENT_NETWORK_SCAN_COMPLETED;
        switch (mSlotId) {
        case PhoneConstants.GEMINI_SIM_1:
            msgType = EVENT_NETWORK_SCAN_COMPLETED;
            break;
        case PhoneConstants.GEMINI_SIM_2:
            msgType = EVENT_NETWORK_SCAN_COMPLETED_2;
            break;
        case PhoneConstants.GEMINI_SIM_3:
            msgType = EVENT_NETWORK_SCAN_COMPLETED_3;
            break;
        case PhoneConstants.GEMINI_SIM_4:
            msgType = EVENT_NETWORK_SCAN_COMPLETED_4;
            break;
        default:
            log("Error wrong sim id");
        }
        return msgType;
    }
}
