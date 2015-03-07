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

package com.android.internal.telephony.gsm;

import android.app.Activity;
import android.content.Context;
import android.os.Message;
import android.provider.Telephony.Sms.Intents;

import com.android.internal.telephony.CommandsInterface;
import com.android.internal.telephony.InboundSmsHandler;
import com.android.internal.telephony.PhoneBase;
import com.android.internal.telephony.SmsConstants;
import com.android.internal.telephony.SmsMessageBase;
import com.android.internal.telephony.SmsStorageMonitor;
import com.android.internal.telephony.uicc.UsimServiceTable;

// MTK-START
import com.mediatek.common.MediatekClassFactory;

// duplicate sms enhancement
import com.mediatek.common.sms.IDupSmsFilterExt;
// MTK-END

/**
 * This class broadcasts incoming SMS messages to interested apps after storing them in
 * the SmsProvider "raw" table and ACKing them to the SMSC. After each message has been
 */
public class GsmInboundSmsHandler extends InboundSmsHandler {

    /** Handler for SMS-PP data download messages to UICC. */
    private final UsimDataDownloadHandler mDataDownloadHandler;

    // MTK-START
    /** Check if any duplicated SMS */
    private IDupSmsFilterExt mDupSmsFilterExt = null;
    // MTK-END

    /**
     * Create a new GSM inbound SMS handler.
     */
    private GsmInboundSmsHandler(Context context, SmsStorageMonitor storageMonitor,
            PhoneBase phone) {
        super("GsmInboundSmsHandler", context, storageMonitor, phone,
                GsmCellBroadcastHandler.makeGsmCellBroadcastHandler(context, phone));
        phone.mCi.setOnNewGsmSms(getHandler(), EVENT_NEW_SMS, null);
        mDataDownloadHandler = new UsimDataDownloadHandler(phone.mCi);

        // MTK-START
        mDupSmsFilterExt = MediatekClassFactory.createInstance(IDupSmsFilterExt.class, context, mPhone.getMySimId());
        if (mDupSmsFilterExt != null) {
            String actualClassName = mDupSmsFilterExt.getClass().getName();
            log("initial IDupSmsFilterExt done, actual class name is " + actualClassName);
        } else {
            log("FAIL! intial IDupSmsFilterExt");
        }
        // MTK-END
    }

    /**
     * Unregister for GSM SMS.
     */
    @Override
    protected void onQuitting() {
        mPhone.mCi.unSetOnNewGsmSms(getHandler());
        mCellBroadcastHandler.dispose();

        if (DBG) log("unregistered for 3GPP SMS");
        super.onQuitting();     // release wakelock
    }

    /**
     * Wait for state machine to enter startup state. We can't send any messages until then.
     */
    public static GsmInboundSmsHandler makeInboundSmsHandler(Context context,
            SmsStorageMonitor storageMonitor, PhoneBase phone) {
        GsmInboundSmsHandler handler = new GsmInboundSmsHandler(context, storageMonitor, phone);
        handler.start();
        return handler;
    }

    /**
     * Return true if this handler is for 3GPP2 messages; false for 3GPP format.
     * @return false (3GPP)
     */
    @Override
    protected boolean is3gpp2() {
        return false;
    }

    /**
     * Handle type zero, SMS-PP data download, and 3GPP/CPHS MWI type SMS. Normal SMS messages
     * are handled by {@link #dispatchNormalMessage} in parent class.
     *
     * @param smsb the SmsMessageBase object from the RIL
     * @return a result code from {@link android.provider.Telephony.Sms.Intents},
     *  or {@link Activity#RESULT_OK} for delayed acknowledgment to SMSC
     */
    @Override
    protected int dispatchMessageRadioSpecific(SmsMessageBase smsb) {
        SmsMessage sms = (SmsMessage) smsb;

        // MTK-START
        // Remove the duplicated sms for a period of time
        if (mDupSmsFilterExt.containDupSms(sms.getPdu())) {
            log("discard dup sms");
            return Intents.RESULT_SMS_HANDLED;
        }
        // MTK-END

        if (sms.isTypeZero()) {
            // As per 3GPP TS 23.040 9.2.3.9, Type Zero messages should not be
            // Displayed/Stored/Notified. They should only be acknowledged.
            log("Received short message type 0, Don't display or store it. Send Ack");
            return Intents.RESULT_SMS_HANDLED;
        }

        // Send SMS-PP data download messages to UICC. See 3GPP TS 31.111 section 7.1.1.
        if (sms.isUsimDataDownload()) {
            UsimServiceTable ust = mPhone.getUsimServiceTable();
            return mDataDownloadHandler.handleUsimDataDownload(ust, sms);
        }

        boolean handled = false;
        if (sms.isMWISetMessage()) {
            mPhone.setVoiceMessageWaiting(1, -1);  // line 1: unknown number of msgs waiting
            handled = sms.isMwiDontStore();
            if (DBG) log("Received voice mail indicator set SMS shouldStore=" + !handled);
        } else if (sms.isMWIClearMessage()) {
            mPhone.setVoiceMessageWaiting(1, 0);   // line 1: no msgs waiting
            handled = sms.isMwiDontStore();
            if (DBG) log("Received voice mail indicator clear SMS shouldStore=" + !handled);
        }
        if (handled) {
            return Intents.RESULT_SMS_HANDLED;
        }

        if (!mStorageMonitor.isStorageAvailable() &&
                sms.getMessageClass() != SmsConstants.MessageClass.CLASS_0) {
            // It's a storable message and there's no storage available.  Bail.
            // (See TS 23.038 for a description of class 0 messages.)
            return Intents.RESULT_SMS_OUT_OF_MEMORY;
        }

        return dispatchNormalMessage(smsb);
    }

    /**
     * Send an acknowledge message.
     * @param success indicates that last message was successfully received.
     * @param result result code indicating any error
     * @param response callback message sent when operation completes.
     */
    @Override
    protected void acknowledgeLastIncomingSms(boolean success, int result, Message response) {
        mPhone.mCi.acknowledgeLastIncomingGsmSms(success, resultToCause(result), response);
    }

    /**
     * Called when the phone changes the default method updates mPhone
     * mStorageMonitor and mCellBroadcastHandler.updatePhoneObject.
     * Override if different or other behavior is desired.
     *
     * @param phone
     */
    @Override
    protected void onUpdatePhoneObject(PhoneBase phone) {
        super.onUpdatePhoneObject(phone);
        log("onUpdatePhoneObject: dispose of old CellBroadcastHandler and make a new one");
        mCellBroadcastHandler.dispose();
        mCellBroadcastHandler = GsmCellBroadcastHandler
                .makeGsmCellBroadcastHandler(mContext, phone);
    }

    /**
     * Convert Android result code to 3GPP SMS failure cause.
     * @param rc the Android SMS intent result value
     * @return 0 for success, or a 3GPP SMS failure cause value
     */
    private static int resultToCause(int rc) {
        switch (rc) {
            case Activity.RESULT_OK:
            case Intents.RESULT_SMS_HANDLED:
                // Cause code is ignored on success.
                return 0;
            case Intents.RESULT_SMS_OUT_OF_MEMORY:
                return CommandsInterface.GSM_SMS_FAIL_CAUSE_MEMORY_CAPACITY_EXCEEDED;
            case Intents.RESULT_SMS_GENERIC_ERROR:
            default:
                return CommandsInterface.GSM_SMS_FAIL_CAUSE_UNSPECIFIED_ERROR;
        }
    }
}
