/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.internal.telephony;

import android.Manifest;
import android.app.Activity;
import android.app.AppOpsManager;
import android.content.Context;
import android.content.Intent;
import android.os.Message;
import android.provider.Telephony;
import android.telephony.SmsCbMessage;

// MTK-START
import com.android.internal.telephony.PhoneConstants;
// For MTK multiuser in 3gdatasms:MTK_ONLY_OWNER_SIM_SUPPORT 
import com.mediatek.common.telephony.IOnlyOwnerSimSupport;
import com.mediatek.common.MediatekClassFactory;
// MTK-END

/**
 * Dispatch new Cell Broadcasts to receivers. Acquires a private wakelock until the broadcast
 * completes and our result receiver is called.
 */
public class CellBroadcastHandler extends WakeLockStateMachine {

    // MTK-START
    // For MTK multiuser in 3gdatasms:MTK_ONLY_OWNER_SIM_SUPPORT 
    private IOnlyOwnerSimSupport mOnlyOwnerSimSupport = null;
    // MTK-END

    private CellBroadcastHandler(Context context) {
        this("CellBroadcastHandler", context, null);
    }

    protected CellBroadcastHandler(String debugTag, Context context, PhoneBase phone) {
        super(debugTag, context, phone);

        // MTK-START
        // For MTK multiuser in 3gdatasms:MTK_ONLY_OWNER_SIM_SUPPORT @{ 
        mOnlyOwnerSimSupport = MediatekClassFactory.createInstance(IOnlyOwnerSimSupport.class,mContext,true);
        if (mOnlyOwnerSimSupport != null) {
            String actualClassName = mOnlyOwnerSimSupport.getClass().getName();
            log("initial mOnlyOwnerSimSupport done, actual class name is " + actualClassName);
        } else {
            loge("FAIL! intial mOnlyOwnerSimSupport");
        }
        // MTK-END
    }

    /**
     * Create a new CellBroadcastHandler.
     * @param context the context to use for dispatching Intents
     * @return the new handler
     */
    public static CellBroadcastHandler makeCellBroadcastHandler(Context context) {
        CellBroadcastHandler handler = new CellBroadcastHandler(context);
        handler.start();
        return handler;
    }

    /**
     * Handle Cell Broadcast messages from {@code CdmaInboundSmsHandler}.
     * 3GPP-format Cell Broadcast messages sent from radio are handled in the subclass.
     *
     * @param message the message to process
     * @return true if an ordered broadcast was sent; false on failure
     */
    @Override
    protected boolean handleSmsMessage(Message message) {
        if (message.obj instanceof SmsCbMessage) {
            handleBroadcastSms((SmsCbMessage) message.obj);
            return true;
        } else {
            loge("handleMessage got object of type: " + message.obj.getClass().getName());
            return false;
        }
    }

    /**
     * Dispatch a Cell Broadcast message to listeners.
     * @param message the Cell Broadcast to broadcast
     */
    protected void handleBroadcastSms(SmsCbMessage message) {
        String receiverPermission;
        int appOp;
        Intent intent;
        if (message.isEmergencyMessage()) {
            // MTK-START
            log("Dispatching emergency SMS CB:" + message);
            // MTK-END
            intent = new Intent(Telephony.Sms.Intents.SMS_EMERGENCY_CB_RECEIVED_ACTION);
            receiverPermission = Manifest.permission.RECEIVE_EMERGENCY_BROADCAST;
            appOp = AppOpsManager.OP_RECEIVE_EMERGECY_SMS;
        } else {
            log("Dispatching SMS CB");
            intent = new Intent(Telephony.Sms.Intents.SMS_CB_RECEIVED_ACTION);
            receiverPermission = Manifest.permission.RECEIVE_SMS;
            appOp = AppOpsManager.OP_RECEIVE_SMS;

            // MTK-START
            // For MTK multiuser in 3gdatasms:MTK_ONLY_OWNER_SIM_SUPPORT @{ 
            if(!mOnlyOwnerSimSupport.isCurrentUserOwner()) {
                intent.setAction(mOnlyOwnerSimSupport.MTK_NORMALUSER_CB_ACTION);
            }
            // MTK-END
        }
        intent.putExtra("message", message);
        // MTK-START
        intent.putExtra(PhoneConstants.GEMINI_SIM_ID_KEY, mSimId);
        // MTK-END
        mContext.sendOrderedBroadcast(intent, receiverPermission, appOp, mReceiver,
                getHandler(), Activity.RESULT_OK, null, null);
    }

    // MTK-START
    /**
     * Implemented by subclass to handle messages in {@link IdleState}. 
     * It is used to handle the ETWS primary notification. The different
     * domain should handle by itself. Default will not handle this message.
     * @param message the message to process
     * @return true to transition to {@link WaitingState}; false to stay in {@link IdleState}
     */
    @Override
    protected boolean handleEtwsPrimaryNotification(Message message) {
        return false;
    }
    // MTK-END
}
