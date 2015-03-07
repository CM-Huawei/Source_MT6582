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

package com.android.internal.telephony.cdma;

import android.app.Activity;
import android.app.PendingIntent;
import android.app.PendingIntent.CanceledException;
import android.content.Intent;
import android.os.Message;
import android.os.SystemProperties;
import android.provider.Telephony.Sms;
import android.telephony.Rlog;
import android.telephony.SmsManager;

import com.android.internal.telephony.ImsSMSDispatcher;
import com.android.internal.telephony.PhoneBase;
import com.android.internal.telephony.SMSDispatcher;
import com.android.internal.telephony.SmsConstants;
import com.android.internal.telephony.SmsHeader;
import com.android.internal.telephony.SmsUsageMonitor;
import com.android.internal.telephony.TelephonyProperties;
import com.android.internal.telephony.cdma.sms.UserData;

import java.util.HashMap;

import static android.telephony.SmsManager.RESULT_ERROR_SUCCESS;
import static android.telephony.SmsManager.RESULT_ERROR_SIM_MEM_FULL;
import static android.telephony.SmsManager.RESULT_ERROR_GENERIC_FAILURE;
import static android.telephony.SmsManager.RESULT_ERROR_INVALID_ADDRESS;

// VIA add begin
import java.util.ArrayList;
import java.util.List;
import android.os.Bundle;
import android.os.AsyncResult;
import android.provider.Telephony.Sms.Intents;
import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.GsmAlphabet.TextEncodingDetails;
import com.android.internal.telephony.IccUtils;
import com.android.internal.telephony.SmsRawData;
import com.android.internal.telephony.uicc.UiccController;
import com.android.internal.telephony.uicc.UiccCardApplication;
import static android.telephony.SmsManager.STATUS_ON_ICC_READ;
import static android.telephony.SmsManager.STATUS_ON_ICC_SENT;
import static android.telephony.SmsManager.STATUS_ON_ICC_UNREAD;
import static android.telephony.SmsManager.STATUS_ON_ICC_UNSENT;
// VIA add for code completion of sendTextWithExtraParams begin
import static android.telephony.SmsManager.RESULT_ERROR_NULL_PDU;
import static android.telephony.SmsManager.EXTRA_PARAMS_VALIDITY_PERIOD;
import static android.telephony.SmsManager.EXTRA_PARAMS_ENCODING_TYPE;
// VIA add for code completion of sendTextWithExtraParams end
// VIA add end

public class CdmaSMSDispatcher extends SMSDispatcher {
    private static final String TAG = "CdmaSMSDispatcher";
    private static final boolean VDBG = true;

    // VIA add for query sms registe feasibility begin
    private UiccController mUiccController = null;
    private UiccCardApplication mUiccApplcation = null;
    private static final int EVENT_CDMA_SMS_DISPATCHER_BASE = 2000;
    private static final int EVENT_ICC_CHANGED = EVENT_CDMA_SMS_DISPATCHER_BASE + 1;
    private static final int EVENT_RUIM_READY = EVENT_CDMA_SMS_DISPATCHER_BASE + 2;
    private static final int EVENT_QUERY_CDMA_NETWORK_REGISTER_STATE = EVENT_CDMA_SMS_DISPATCHER_BASE + 3;
    private static final int EVENT_QUERY_CDMA_NETWORK_REGISTER_STATE_DONE = EVENT_CDMA_SMS_DISPATCHER_BASE + 4;
    private static final int EVENT_QUERY_CDMA_MODEM_SMS_INIT_STATE = EVENT_CDMA_SMS_DISPATCHER_BASE + 5;
    private static final int EVENT_QUERY_CDMA_MODEM_SMS_INIT_STATE_DONE = EVENT_CDMA_SMS_DISPATCHER_BASE + 6;

    /* retry max times */
    private static final int QUERY_SMS_REGISTER_FEASIBILITY_MAX_RETRY_TIMES = 100;
    /* retry delay time in milliseconds, 10 seconds */
    private static final int QUERY_SMS_REGISTER_FEASIBILITY_DELAY_MILLISECONDS = 10000;

    private int mQueryCDMASmsRegisterFeasibilityTimes = 0;
    private boolean mSmsRegisterFeasibilityQuerying = false;

    private boolean mCdmaNetworkRegistered = false;
    private boolean mCdmaModemSmsInitDone = false;
    // VIA add end


    public CdmaSMSDispatcher(PhoneBase phone, SmsUsageMonitor usageMonitor,
            ImsSMSDispatcher imsSMSDispatcher) {
        super(phone, usageMonitor, imsSMSDispatcher);

        Rlog.d(TAG, "CdmaSMSDispatcher created");

        // VIA add begin
        mUiccController = UiccController.getInstance(mSimId);
        if (mUiccController != null) {
            if (phone.getPhoneType() == PhoneConstants.PHONE_TYPE_CDMA) {
                mUiccController.registerForIccChanged(this, EVENT_ICC_CHANGED, null);
                Rlog.d(TAG, "viacode, mUiccController != null, register for icc change successly for CDMA");
            } else {
                Rlog.d(TAG, "viacode, is not CDMA phone, dont do CT sms register work");
            }
        } else {
            Rlog.e(TAG, "viacode, mUiccController = null, cant register for icc change");
        }
        // VIA add end

    }

    // VIA add for query sms registe feasibility begin
    private void notifyCdmaSmsAutoRegisterBeFeasible() {
        if (!mCdmaNetworkRegistered || !mCdmaModemSmsInitDone) {
               Rlog.e(TAG, "viacode, notifyCdmaSmsAutoRegisterBeFeasible failed for not both true");
               Rlog.e(TAG, "viacode, mCdmaNetworkRegistered = " + mCdmaNetworkRegistered + " , mCdmaModemSmsInitDone = " + mCdmaModemSmsInitDone);
               return;
        }

        Intent intent = new Intent(Intents.CDMA_AUTO_SMS_REGISTER_FEASIBLE_ACTION);
        // Hold a wake lock for WAKE_LOCK_TIMEOUT seconds, enough to give any
        // receivers time to take their own wake locks.
        mWakeLock.acquire(WAKE_LOCK_TIMEOUT);
        intent.putExtra("rTime", System.currentTimeMillis());
        intent.putExtra(PhoneConstants.GEMINI_SIM_ID_KEY, mSimId);
        mContext.sendBroadcast(intent);
        Rlog.d(TAG, "viacode, app can do auto sms register work now");
        mSmsRegisterFeasibilityQuerying = false;
    }

    // register for ruim ready when icc card is attached
    private void onUpdateIccAvailability() {
        Rlog.d(TAG, "viacode, CdmaSMSDispatcher received EVENT_ICC_CHANGED");
        if (mUiccController == null) {
            Rlog.e(TAG, "viacode, but mUiccController == null, cant do nothing");
            return;
        }

        UiccCardApplication newUiccApplication =
                mUiccController.getUiccCardApplication(UiccController.APP_FAM_3GPP2);

        if (mUiccApplcation != newUiccApplication) {
            if (mUiccApplcation != null) {
                Rlog.d(TAG, "viacode, Removing stale icc objects, mUiccApplcation have changed!");
                mUiccApplcation.unregisterForReady(this);
                mUiccApplcation = null;
            }
            if (newUiccApplication != null) {
                Rlog.d(TAG, "viacode, New card found!");
                mUiccApplcation = newUiccApplication;
                mUiccApplcation.registerForReady(this, EVENT_RUIM_READY, null);
                Rlog.d(TAG, "viacode, register for EVENT_RUIM_READY successly");
            }
        }
    }

    @Override
    protected String getFormat() {
        return SmsConstants.FORMAT_3GPP2;
    }

    @Override
    public void handleMessage(Message msg) {
        switch (msg.what) {
        case EVENT_ICC_CHANGED:
            onUpdateIccAvailability();
            break;
        case EVENT_RUIM_READY:
            if (!mSmsRegisterFeasibilityQuerying) {
                Rlog.d(TAG, "viacode, received EVENT_RUIM_READY in CDMASmsDispather");
                mSmsRegisterFeasibilityQuerying = true;
                mQueryCDMASmsRegisterFeasibilityTimes = 0;
                mQueryCDMASmsRegisterFeasibilityTimes++;
                mCi.queryCDMANetWorkRegistrationState(obtainMessage(EVENT_QUERY_CDMA_NETWORK_REGISTER_STATE_DONE));
            } else {
                Rlog.e(TAG, "viacode, received EVENT_RUIM_READY, but query processing, dont need query multi.");
            }
            break;
        case EVENT_QUERY_CDMA_NETWORK_REGISTER_STATE:
            mQueryCDMASmsRegisterFeasibilityTimes++;
            Rlog.d(TAG, "viacode, do network register state query again, retry times = " + mQueryCDMASmsRegisterFeasibilityTimes);
            if (mQueryCDMASmsRegisterFeasibilityTimes > QUERY_SMS_REGISTER_FEASIBILITY_MAX_RETRY_TIMES) {
               Rlog.e(TAG, "over retry limits(" + QUERY_SMS_REGISTER_FEASIBILITY_MAX_RETRY_TIMES
                               + "), query cdma sms register feasibility failed");
                 // over the retry times limits, give up querying
                 mSmsRegisterFeasibilityQuerying = false;
            } else {
                mCi.queryCDMANetWorkRegistrationState(obtainMessage(EVENT_QUERY_CDMA_NETWORK_REGISTER_STATE_DONE));
            }
            break;

        case EVENT_QUERY_CDMA_NETWORK_REGISTER_STATE_DONE:
            if (mSmsRegisterFeasibilityQuerying) {
                AsyncResult aresult = (AsyncResult) msg.obj;
                if (aresult != null) {
                    int res[] = (int[])aresult.result;
                    if (res != null && res.length == 1 && res[0] == 1) {
                        // network register done! to query modem sms init state
                        // reset times
                        mQueryCDMASmsRegisterFeasibilityTimes = 0;
                        mCdmaNetworkRegistered = true;
                        sendEmptyMessage(EVENT_QUERY_CDMA_MODEM_SMS_INIT_STATE);
                    } else {
                        // not ok , query again after a moment
                        sendEmptyMessageDelayed(EVENT_QUERY_CDMA_NETWORK_REGISTER_STATE, QUERY_SMS_REGISTER_FEASIBILITY_DELAY_MILLISECONDS);
                    }
                } else {
                    // not ok , query again after a moment
                    sendEmptyMessageDelayed(EVENT_QUERY_CDMA_NETWORK_REGISTER_STATE, QUERY_SMS_REGISTER_FEASIBILITY_DELAY_MILLISECONDS);
                }
            } else {
                Rlog.e(TAG, "viacode, received EVENT_QUERY_CDMA_NETWORK_REGISTER_STATE_DONE but is not querying");
                Rlog.e(TAG, "viacode, must make sure is retrying in the same instance,"
                          + " drop this done EVENT if is not retry querying.");
            }
            break;

        case EVENT_QUERY_CDMA_MODEM_SMS_INIT_STATE:
            mQueryCDMASmsRegisterFeasibilityTimes++;
            Rlog.d(TAG, "viacode, do sms init state query again, retry times = " + mQueryCDMASmsRegisterFeasibilityTimes);
            if (mQueryCDMASmsRegisterFeasibilityTimes > QUERY_SMS_REGISTER_FEASIBILITY_MAX_RETRY_TIMES) {
               Rlog.e(TAG, "over retry limits(" + QUERY_SMS_REGISTER_FEASIBILITY_MAX_RETRY_TIMES
                               + "), query cdma modem sms init state failed");
                 // over the retry times limits, give up querying
                 mSmsRegisterFeasibilityQuerying = false;
            } else {
                mCi.queryCDMASmsAndPBStatus(obtainMessage(EVENT_QUERY_CDMA_MODEM_SMS_INIT_STATE_DONE));
            }
            break;

        case EVENT_QUERY_CDMA_MODEM_SMS_INIT_STATE_DONE:
            if (mSmsRegisterFeasibilityQuerying) {
                AsyncResult asyncresult = (AsyncResult) msg.obj;
                if (asyncresult != null) {
                    int queryres[] = (int[])asyncresult.result;
                    if (queryres != null && queryres.length == 2 && queryres[0] == 1) {
                        // modem sms init done! to broadcast
                        // reset times
                        mQueryCDMASmsRegisterFeasibilityTimes = 0;
                        mCdmaModemSmsInitDone = true;
                        notifyCdmaSmsAutoRegisterBeFeasible();
                    } else {
                        // not ok , query again after a moment
                        sendEmptyMessageDelayed(EVENT_QUERY_CDMA_MODEM_SMS_INIT_STATE, QUERY_SMS_REGISTER_FEASIBILITY_DELAY_MILLISECONDS);
                    }
                } else {
                    // not ok , query again after a moment
                    sendEmptyMessageDelayed(EVENT_QUERY_CDMA_MODEM_SMS_INIT_STATE, QUERY_SMS_REGISTER_FEASIBILITY_DELAY_MILLISECONDS);
                }
            } else {
                Rlog.e(TAG, "viacode, received EVENT_QUERY_CDMA_MODEM_SMS_INIT_STATE_DONE but is not querying");
                Rlog.e(TAG, "viacode, must make sure is retrying in the same instance,"
                          + " drop this done EVENT if is not retry querying.");
            }
            break;

        default:
            super.handleMessage(msg);
        }
    }
    // VIA add end

    @Override
    public void dispose() {
        super.dispose();
        //via add for China Telecom auto-register sms begin
        Rlog.d(TAG, "CdmaSMSDispatcher dispose");
        removeMessages(EVENT_QUERY_CDMA_NETWORK_REGISTER_STATE);
        removeMessages(EVENT_QUERY_CDMA_MODEM_SMS_INIT_STATE);

        if (mUiccController != null) {
            mUiccController.unregisterForIccChanged(this);
        }
        if (mUiccApplcation != null) {
            mUiccApplcation.unregisterForReady(this);
        }
        //via add for China Telecom auto-register sms end
    }

    /**
     * Send the SMS status report to the dispatcher thread to process.
     * @param sms the CDMA SMS message containing the status report
     */
    void sendStatusReportMessage(SmsMessage sms) {
        if (VDBG) Rlog.d(TAG, "sending EVENT_HANDLE_STATUS_REPORT message");
        sendMessage(obtainMessage(EVENT_HANDLE_STATUS_REPORT, sms));
    }

    @Override
    protected void handleStatusReport(Object o) {
        if (o instanceof SmsMessage) {
            if (VDBG) Rlog.d(TAG, "calling handleCdmaStatusReport()");
            handleCdmaStatusReport((SmsMessage) o);
        } else {
            Rlog.e(TAG, "handleStatusReport() called for object type " + o.getClass().getName());
        }
    }

    /**
     * Called from parent class to handle status report from {@code CdmaInboundSmsHandler}.
     * @param sms the CDMA SMS message to process
     */
    void handleCdmaStatusReport(SmsMessage sms) {
        for (int i = 0, count = deliveryPendingList.size(); i < count; i++) {
            SmsTracker tracker = deliveryPendingList.get(i);
            if (tracker.mMessageRef == sms.mMessageRef) {
                // Found it.  Remove from list and broadcast.
                deliveryPendingList.remove(i);
                // Update the message status (COMPLETE)
                tracker.updateSentMessageStatus(mContext, Sms.STATUS_COMPLETE);

                PendingIntent intent = tracker.mDeliveryIntent;
                Intent fillIn = new Intent();
                fillIn.putExtra("pdu", sms.getPdu());
                fillIn.putExtra("format", getFormat());
                try {
                    intent.send(mContext, Activity.RESULT_OK, fillIn);
                } catch (CanceledException ex) {}
                break;  // Only expect to see one tracker matching this message.
            }
        }
    }

    /** {@inheritDoc} */
    @Override
    protected void sendData(String destAddr, String scAddr, int destPort,
            byte[] data, PendingIntent sentIntent, PendingIntent deliveryIntent) {
        SmsMessage.SubmitPdu pdu = SmsMessage.getSubmitPdu(
                scAddr, destAddr, destPort, data, (deliveryIntent != null));
        HashMap map = getSmsTrackerMap(destAddr, scAddr, destPort, data, pdu);
        SmsTracker tracker = getSmsTracker(map, sentIntent, deliveryIntent,
                getFormat());
        sendSubmitPdu(tracker);
    }

    /** {@inheritDoc} */
    @Override
    protected void sendText(String destAddr, String scAddr, String text,
            PendingIntent sentIntent, PendingIntent deliveryIntent) {
        SmsMessage.SubmitPdu pdu = SmsMessage.getSubmitPdu(
                scAddr, destAddr, text, (deliveryIntent != null), null);
        HashMap map = getSmsTrackerMap(destAddr, scAddr, text, pdu);
        SmsTracker tracker = getSmsTracker(map, sentIntent,
                deliveryIntent, getFormat());
        sendSubmitPdu(tracker);
    }

    /** {@inheritDoc} */
    @Override
    protected TextEncodingDetails calculateLength(CharSequence messageBody,
            boolean use7bitOnly) {
        return SmsMessage.calculateLength(messageBody, use7bitOnly);
    }

    /** {@inheritDoc} */
    @Override
    protected void sendNewSubmitPdu(String destinationAddress, String scAddress,
            String message, SmsHeader smsHeader, int encoding,
            PendingIntent sentIntent, PendingIntent deliveryIntent, boolean lastPart) {
        UserData uData = new UserData();
        uData.payloadStr = message;
        uData.userDataHeader = smsHeader;
        if (encoding == SmsConstants.ENCODING_7BIT) {
            //VIA Modifyed: Replace ENCODING_GSM_7BIT_ALPHABET with ENCODING_7BIT_ASCII.
            uData.msgEncoding = UserData.ENCODING_7BIT_ASCII;
        } else { // assume UTF-16
            uData.msgEncoding = UserData.ENCODING_UNICODE_16;
        }
        uData.msgEncodingSet = true;

        /* By setting the statusReportRequested bit only for the
         * last message fragment, this will result in only one
         * callback to the sender when that last fragment delivery
         * has been acknowledged. */
        SmsMessage.SubmitPdu submitPdu = SmsMessage.getSubmitPdu(destinationAddress,
                uData, (deliveryIntent != null) && lastPart);

        HashMap map = getSmsTrackerMap(destinationAddress, scAddress,
                message, submitPdu);
        SmsTracker tracker = getSmsTracker(map, sentIntent,
                deliveryIntent, getFormat());
        sendSubmitPdu(tracker);
    }

    protected void sendSubmitPdu(SmsTracker tracker) {
        if (SystemProperties.getBoolean(TelephonyProperties.PROPERTY_INECM_MODE, false)) {
            if (tracker.mSentIntent != null) {
                try {
                    tracker.mSentIntent.send(SmsManager.RESULT_ERROR_NO_SERVICE);
                } catch (CanceledException ex) {}
            }
            if (VDBG) {
                Rlog.d(TAG, "Block SMS in Emergency Callback mode");
            }
            return;
        }
        sendRawPdu(tracker);
    }

    /** {@inheritDoc} */
    @Override
    protected void sendSms(SmsTracker tracker) {
        HashMap<String, Object> map = tracker.mData;

        // byte[] smsc = (byte[]) map.get("smsc");  // unused for CDMA
        byte[] pdu = (byte[]) map.get("pdu");

        Message reply = obtainMessage(EVENT_SEND_SMS_COMPLETE, tracker);

        Rlog.d(TAG, "sendSms: "
                +" isIms()="+isIms()
                +" mRetryCount="+tracker.mRetryCount
                +" mImsRetry="+tracker.mImsRetry
                +" mMessageRef="+tracker.mMessageRef
                +" SS=" +mPhone.getServiceState().getState());

        // sms over cdma is used:
        //   if sms over IMS is not supported AND
        //   this is not a retry case after sms over IMS failed
        //     indicated by mImsRetry > 0
        if (0 == tracker.mImsRetry && !isIms()) {
            mCi.sendCdmaSms(pdu, reply);
        } else {
            mCi.sendImsCdmaSms(pdu, tracker.mImsRetry, tracker.mMessageRef, reply);
            // increment it here, so in case of SMS_FAIL_RETRY over IMS
            // next retry will be sent using IMS request again.
            tracker.mImsRetry++;
        }
    }

    /** {@inheritDoc} */
    protected void sendData(String destAddr, String scAddr, int destPort, int originalPort,
            byte[] data, PendingIntent sentIntent, PendingIntent deliveryIntent) {
        // impl
        Rlog.d(TAG, "viacode: CdmaSMSDispatcher, implemented by VIA for interfaces needed. sendData");
        SmsMessage.SubmitPdu pdu = SmsMessage.getSubmitPdu(
                scAddr, destAddr, destPort, originalPort, data, (deliveryIntent != null));
        if (pdu == null) {
            Rlog.d(TAG, "via sendData error: invalid paramters, pdu == null.");
            return;
        }
        HashMap map =  getSmsTrackerMap(destAddr, scAddr, destPort, data, pdu);
        SmsTracker tracker = getSmsTracker(map, sentIntent, deliveryIntent, getFormat());
        sendRawPdu(tracker);
    }

    /** {@inheritDoc} */
    protected void sendMultipartData(
            String destAddr, String scAddr, int destPort,
            ArrayList<SmsRawData> data, ArrayList<PendingIntent> sentIntents,
            ArrayList<PendingIntent> deliveryIntents) {
        // impl
        Rlog.e(TAG, "Error! The functionality sendMultipartData is not implemented for CDMA.");
    }

    /** {@inheritDoc} */
    protected void sendText(String destAddr, String scAddr, String text,
            int destPort,PendingIntent sentIntent, PendingIntent deliveryIntent) {

        Rlog.e(TAG, "Error! The functionality sendText with port is not implemented for CDMA.");
    }

    /** {@inheritDoc} */
    protected void sendMultipartText(String destinationAddress, String scAddress,
            ArrayList<String> parts, int destPort, ArrayList<PendingIntent> sentIntents,
            ArrayList<PendingIntent> deliveryIntents) {
        // impl
        Rlog.d(TAG, "viacode: CdmaSMSDispatcher, implemented by VIA for interfaces needed. sendMultipartText");

        int refNumber = getNextConcatenatedRef() & 0x00FF;
        int msgCount = parts.size();
        int encoding = android.telephony.SmsMessage.ENCODING_UNKNOWN;

        TextEncodingDetails[] encodingForParts = new TextEncodingDetails[msgCount];
        if (encoding == android.telephony.SmsMessage.ENCODING_UNKNOWN) {
            Rlog.d(TAG, "viacode: unkown encoding, to find one best.");
            for (int i = 0; i < msgCount; i++) {
                TextEncodingDetails details = calculateLength(parts.get(i), false);
                if (encoding != details.codeUnitSize
                        && (encoding == android.telephony.SmsMessage.ENCODING_UNKNOWN
                                || encoding == android.telephony.SmsMessage.ENCODING_7BIT)) {
                    encoding = details.codeUnitSize;
                }
                encodingForParts[i] = details;
            }
        }

        mRemainingMessages = msgCount;

        for (int i = 0; i < msgCount; i++) {
            SmsHeader.ConcatRef concatRef = new SmsHeader.ConcatRef();
            concatRef.refNumber = refNumber;
            concatRef.seqNumber = i + 1;  // 1-based sequence
            concatRef.msgCount = msgCount;
            concatRef.isEightBits = true;

            SmsHeader smsHeader = new SmsHeader();
            smsHeader.concatRef = concatRef;

            if (destPort >= 0) {
                SmsHeader.PortAddrs portAddrs = new SmsHeader.PortAddrs();
                portAddrs.destPort = destPort;
                portAddrs.origPort = 0;
                portAddrs.areEightBits = false;

                smsHeader.portAddrs = portAddrs;
            }

            PendingIntent sentIntent = null;
            if (sentIntents != null && sentIntents.size() > i) {
                sentIntent = sentIntents.get(i);
            }

            PendingIntent deliveryIntent = null;
            if (deliveryIntents != null && deliveryIntents.size() > i) {
                deliveryIntent = deliveryIntents.get(i);
            }

            UserData uData = new UserData();
            uData.payloadStr = parts.get(i);
            uData.userDataHeader = smsHeader;
            if (encodingForParts[i].codeUnitSize == android.telephony.SmsMessage.ENCODING_7BIT) {
                uData.msgEncoding = UserData.ENCODING_7BIT_ASCII;
            } else if (encodingForParts[i].codeUnitSize == android.telephony.SmsMessage.ENCODING_8BIT) {
                uData.msgEncoding = UserData.ENCODING_OCTET;
            } else {
                uData.msgEncoding = UserData.ENCODING_UNICODE_16;
            }
            uData.msgEncodingSet = true;

            Rlog.d(TAG, "viacode: to use the encoding type [" + uData.msgEncoding + "] to send the " + i + " part");
            /* By setting the statusReportRequested bit only for the
             * last message fragment, this will result in only one
             * callback to the sender when that last fragment delivery
             * has been acknowledged. */
            SmsMessage.SubmitPdu submitPdu = SmsMessage.getSubmitPdu(destinationAddress,
                    uData, (deliveryIntent != null)&&(i == (msgCount - 1)));

            if(submitPdu != null) {
                HashMap map =  getSmsTrackerMap(destinationAddress, scAddress, parts.get(i), submitPdu);
                SmsTracker tracker = getSmsTracker(map, sentIntent, deliveryIntent, getFormat());
                sendSubmitPdu(tracker);
            } else {
                Rlog.d(TAG, "viacode: sendMultipartTextWithEncodingType: submitPdu is null");
                if(sentIntent != null) {
                    try {
                        sentIntent.send(RESULT_ERROR_NULL_PDU);
                    } catch (CanceledException ex) {
                        Rlog.e(TAG, "viacode: failed to send back RESULT_ERROR_NULL_PDU");
                    }
                }
            }
        }
    }

    public int copyTextMessageToIccCard(String scAddress, String address, List<String> text,
                    int status, long timestamp) {
        Rlog.d(TAG, "CDMASMSDispatcher: copy text message to icc card");
        /*
         * if(checkPhoneNumber(scAddress)) { Rlog.d(TAG,
         * "[copyText invalid sc address"); scAddress = null; }
         * if(checkPhoneNumber(address) == false) { Rlog.d(TAG,
         * "[copyText invalid dest address"); return
         * RESULT_ERROR_INVALID_ADDRESS; }
         */

        mSuccess = true;

        int msgCount = text.size();
        // we should check the available storage of SIM here,
        // but now we suppose it always be true
        if (true) {
            Rlog.d(TAG, "[copyText storage available");
        } else {
            Rlog.d(TAG, "[copyText storage unavailable");
            return RESULT_ERROR_SIM_MEM_FULL;
        }

        if (status == STATUS_ON_ICC_READ || status == STATUS_ON_ICC_UNREAD) {
            Rlog.d(TAG, "[copyText to encode deliver pdu");
        } else if (status == STATUS_ON_ICC_SENT || status == STATUS_ON_ICC_UNSENT) {
            Rlog.d(TAG, "[copyText to encode submit pdu");
        } else {
            Rlog.d(TAG, "[copyText invalid status, default is deliver pdu");
            return RESULT_ERROR_GENERIC_FAILURE;
        }

        Rlog.d(TAG, "[copyText msgCount " + msgCount);
        if (msgCount > 1) {
            Rlog.d(TAG, "[copyText multi-part message");
        } else if (msgCount == 1) {
            Rlog.d(TAG, "[copyText single-part message");
        } else {
            Rlog.d(TAG, "[copyText invalid message count");
            return RESULT_ERROR_GENERIC_FAILURE;
        }

        for (int i = 0; i < msgCount; ++i) {
            if (mSuccess == false) {
                Rlog.d(TAG, "[copyText Exception happened when copy message");
                return RESULT_ERROR_GENERIC_FAILURE;
            }

            SmsMessage.SubmitPdu pdu = SmsMessage.createEfPdu(address, text.get(i), timestamp);

            if (pdu != null) {
                Rlog.d(TAG, "[copyText write submit pdu into UIM");
                mCi.writeSmsToRuim(status, IccUtils.bytesToHexString(pdu.encodedMessage),
                        obtainMessage(EVENT_COPY_TEXT_MESSAGE_DONE));
            } else {
                return RESULT_ERROR_GENERIC_FAILURE;
            }

            synchronized (mLock) {
                try {
                    Rlog.d(TAG, "[copyText wait until the message be wrote in UIM");
                    mLock.wait();
                } catch (InterruptedException e) {
                    Rlog.d(TAG, "[copyText interrupted while trying to copy text message into UIM");
                    return RESULT_ERROR_GENERIC_FAILURE;
                }
            }
            Rlog.d(TAG, "[copyText thread is waked up");
        }

        if (mSuccess == true) {
            Rlog.d(TAG, "[copyText all messages have been copied into UIM");
            return RESULT_ERROR_SUCCESS;
        }

        Rlog.d(TAG, "[copyText copy failed");
        return RESULT_ERROR_GENERIC_FAILURE;
    }

    /** {@inheritDoc} */
    protected void sendTextWithEncodingType(
            String destAddr,
            String scAddr,
            String text,
            int encodingType,
            PendingIntent sentIntent,
            PendingIntent deliveryIntent) {
        // impl
        Rlog.d(TAG, "viacode: CdmaSMSDispatcher, implemented by VIA for interfaces needed. sendTextWithEncodingType");

        int encoding = encodingType;
        Rlog.d(TAG, "viacode: want to use encoding = " + encoding);

        // check is a valid encoding type
        if (encoding < 0x00 || encoding > 0x0A) {
            Rlog.w(TAG, "viacode: unavalid encoding = " + encoding);
            Rlog.w(TAG, "viacode: to use the unkown default.");
            encoding = android.telephony.SmsMessage.ENCODING_UNKNOWN;
        }

        if (encoding == android.telephony.SmsMessage.ENCODING_UNKNOWN) {
            Rlog.d(TAG, "viacode: unkown encoding, to find one best.");
            TextEncodingDetails details = calculateLength(text, false);
            if (encoding != details.codeUnitSize
                    && (encoding == android.telephony.SmsMessage.ENCODING_UNKNOWN
                            || encoding == android.telephony.SmsMessage.ENCODING_7BIT)) {
                encoding = details.codeUnitSize;
            }
        }

        UserData uData = new UserData();
        uData.payloadStr = text;
        if (encoding == android.telephony.SmsMessage.ENCODING_7BIT) {
            uData.msgEncoding = UserData.ENCODING_7BIT_ASCII;
        } else if (encoding == android.telephony.SmsMessage.ENCODING_8BIT) {
            uData.msgEncoding = UserData.ENCODING_OCTET;
        } else {
            uData.msgEncoding = UserData.ENCODING_UNICODE_16;
        }
        uData.msgEncodingSet = true;

        /* By setting the statusReportRequested bit only for the
         * last message fragment, this will result in only one
         * callback to the sender when that last fragment delivery
         * has been acknowledged. */
        SmsMessage.SubmitPdu submitPdu = SmsMessage.getSubmitPdu(destAddr,
                uData, (deliveryIntent != null));

        if(submitPdu != null) {
            HashMap map =  getSmsTrackerMap(destAddr, scAddr, text, submitPdu);
            SmsTracker tracker = getSmsTracker(map, sentIntent, deliveryIntent, getFormat());
            sendSubmitPdu(tracker);
        } else {
            Rlog.d(TAG, "viacode : sendTextWithEncodingType: submitPdu is null");
            if(sentIntent != null) {
                try {
                    sentIntent.send(RESULT_ERROR_NULL_PDU);
                } catch (CanceledException ex) {
                    Rlog.e(TAG, "viacode: failed to send back RESULT_ERROR_NULL_PDU");
                }
            }
        }
    }

    /** {@inheritDoc} */
    protected void sendMultipartTextWithEncodingType(
            String destAddr,
            String scAddr,
            ArrayList<String> parts,
            int encodingType,
            ArrayList<PendingIntent> sentIntents,
            ArrayList<PendingIntent> deliveryIntents) {
        // impl
        Rlog.d(TAG, "viacode: CdmaSMSDispatcher, implemented by VIA for interfaces needed. sendMultipartTextWithEncodingType");
        int refNumber = getNextConcatenatedRef() & 0x00FF;
        int msgCount = parts.size();
        int encoding = encodingType;
        Rlog.d(TAG, "viacode: want to use encoding = " + encoding);

        // check is a valid encoding type
        if (encoding < 0x00 || encoding > 0x0A) {
            Rlog.w(TAG, "viacode: unavalid encoding = " + encoding);
            Rlog.w(TAG, "viacode: to use the unkown default.");
            encoding = android.telephony.SmsMessage.ENCODING_UNKNOWN;
        }

        mRemainingMessages = msgCount;

        TextEncodingDetails[] encodingForParts = new TextEncodingDetails[msgCount];
        if (encoding == android.telephony.SmsMessage.ENCODING_UNKNOWN) {
            Rlog.d(TAG, "viacode: unkown encoding, to find one best.");
            for (int i = 0; i < msgCount; i++) {
                TextEncodingDetails details = calculateLength(parts.get(i), false);
                if (encoding != details.codeUnitSize
                        && (encoding == android.telephony.SmsMessage.ENCODING_UNKNOWN
                                || encoding == android.telephony.SmsMessage.ENCODING_7BIT)) {
                    encoding = details.codeUnitSize;
                }
                encodingForParts[i] = details;
            }
        } else {
            Rlog.d(TAG, "viacode: APP want use specified encoding type.");
            for (int i = 0; i < msgCount; i++) {
                TextEncodingDetails details = SmsMessage.calculateLength(parts.get(i), false, encoding);
                details.codeUnitSize = encoding;
                encodingForParts[i] = details;
            }
        }

        Rlog.d(TAG, "viacode: now to send one by one, msgCount = " + msgCount);
        for (int i = 0; i < msgCount; i++) {
            SmsHeader.ConcatRef concatRef = new SmsHeader.ConcatRef();
            concatRef.refNumber = refNumber;
            concatRef.seqNumber = i + 1;  // 1-based sequence
            concatRef.msgCount = msgCount;
            // TODO: We currently set this to true since our messaging app will never
            // send more than 255 parts (it converts the message to MMS well before that).
            // However, we should support 3rd party messaging apps that might need 16-bit
            // references
            // Note:  It's not sufficient to just flip this bit to true; it will have
            // ripple effects (several calculations assume 8-bit ref).
            concatRef.isEightBits = true;
            SmsHeader smsHeader = new SmsHeader();
            smsHeader.concatRef = concatRef;

            PendingIntent sentIntent = null;
            if (sentIntents != null && sentIntents.size() > i) {
                sentIntent = sentIntents.get(i);
            }

            PendingIntent deliveryIntent = null;
            if (deliveryIntents != null && deliveryIntents.size() > i) {
                deliveryIntent = deliveryIntents.get(i);
            }

            UserData uData = new UserData();
            uData.payloadStr = parts.get(i);
            uData.userDataHeader = smsHeader;
            if (encodingForParts[i].codeUnitSize == android.telephony.SmsMessage.ENCODING_7BIT) {
                uData.msgEncoding = UserData.ENCODING_7BIT_ASCII;
            } else if (encodingForParts[i].codeUnitSize == android.telephony.SmsMessage.ENCODING_8BIT) {
                uData.msgEncoding = UserData.ENCODING_OCTET;
            } else {
                uData.msgEncoding = UserData.ENCODING_UNICODE_16;
            }
            uData.msgEncodingSet = true;

            Rlog.d(TAG, "viacode: to use the encoding type [" + uData.msgEncoding + "] to send the " + i + " part");
            /* By setting the statusReportRequested bit only for the
             * last message fragment, this will result in only one
             * callback to the sender when that last fragment delivery
             * has been acknowledged. */
            SmsMessage.SubmitPdu submitPdu = SmsMessage.getSubmitPdu(destAddr,
                    uData, (deliveryIntent != null)&&(i == (msgCount - 1)));

            if(submitPdu != null) {
                HashMap map =  getSmsTrackerMap(destAddr, scAddr, parts.get(i), submitPdu);
                SmsTracker tracker = getSmsTracker(map, sentIntent, deliveryIntent, getFormat());
                sendSubmitPdu(tracker);
            } else {
                Rlog.d(TAG, "viacode: sendMultipartTextWithEncodingType: submitPdu is null");
                if(sentIntent != null) {
                    try {
                        sentIntent.send(RESULT_ERROR_NULL_PDU);
                    } catch (CanceledException ex) {
                        Rlog.e(TAG, "viacode: failed to send back RESULT_ERROR_NULL_PDU");
                    }
                }
            }
        }
    }

    /** {@inheritDoc} */
    public void sendTextWithExtraParams(
            String destAddr,
            String scAddr,
            String text,
            Bundle extraParams,
            PendingIntent sentIntent,
            PendingIntent deliveryIntent) {
        // impl
        Rlog.d(TAG, "viacode: CdmaSMSDispatcher, implemented by VIA for interfaces needed. sendTextWithExtraParams");

        int validityPeriod;
        int encoding;

        if (extraParams == null) {
            Rlog.d(TAG, "viacode: extraParams == null, will encoding with no extra feature.");
            validityPeriod = -1;
            encoding = android.telephony.SmsMessage.ENCODING_UNKNOWN;
        } else {
            validityPeriod = extraParams.getInt(EXTRA_PARAMS_VALIDITY_PERIOD, -1);
            encoding = extraParams.getInt(EXTRA_PARAMS_ENCODING_TYPE, 0);;
        }

        Rlog.d(TAG, "viacode: validityPeriod is " + validityPeriod);
        Rlog.d(TAG, "viacode: want to use encoding = " + encoding);

        // check is a valid encoding type
        if (encoding < 0x00 || encoding > 0x0A) {
            Rlog.w(TAG, "viacode: unavalid encoding = " + encoding);
            Rlog.w(TAG, "viacode: to use the unkown default.");
            encoding = android.telephony.SmsMessage.ENCODING_UNKNOWN;
        }

        if (encoding == android.telephony.SmsMessage.ENCODING_UNKNOWN) {
            Rlog.d(TAG, "viacode: unkown encoding, to find one best.");
            TextEncodingDetails details = calculateLength(text, false);
            if (encoding != details.codeUnitSize
                    && (encoding == android.telephony.SmsMessage.ENCODING_UNKNOWN
                            || encoding == android.telephony.SmsMessage.ENCODING_7BIT)) {
                encoding = details.codeUnitSize;
            }
        }

        SmsMessage.SubmitPdu submitPdu = SmsMessage.getSubmitPdu(scAddr, destAddr, text,
                                                 (deliveryIntent != null), null, encoding, validityPeriod);

        if(submitPdu != null) {
            HashMap map =  getSmsTrackerMap(destAddr, scAddr, text, submitPdu);
            SmsTracker tracker = getSmsTracker(map, sentIntent, deliveryIntent, getFormat());
            sendSubmitPdu(tracker);
        } else {
            Rlog.d(TAG, "viacode : sendTextWithExtraParams: submitPdu is null");
            if(sentIntent != null) {
                try {
                    sentIntent.send(RESULT_ERROR_NULL_PDU);
                } catch (CanceledException ex) {
                    Rlog.e(TAG, "viacode: failed to send back RESULT_ERROR_NULL_PDU");
                }
            }
        }
    }

    /** {@inheritDoc} */
    public void sendMultipartTextWithExtraParams(
            String destAddr,
            String scAddr,
            ArrayList<String> parts,
            Bundle extraParams,
            ArrayList<PendingIntent> sentIntents,
            ArrayList<PendingIntent> deliveryIntents) {
        // impl
        Rlog.d(TAG, "viacode: CdmaSMSDispatcher, implemented by VIA for interfaces needed. sendMultipartTextWithExtraParams");
        int validityPeriod;
        int encoding;

        if (extraParams == null) {
            Rlog.d(TAG, "viacode: extraParams == null, will encoding with no extra feature.");
            validityPeriod = -1;
            encoding = android.telephony.SmsMessage.ENCODING_UNKNOWN;
        } else {
            validityPeriod = extraParams.getInt(EXTRA_PARAMS_VALIDITY_PERIOD, -1);
            encoding = extraParams.getInt(EXTRA_PARAMS_ENCODING_TYPE, 0);;
        }

        int refNumber = getNextConcatenatedRef() & 0x00FF;
        int msgCount = parts.size();

        // check is a valid encoding type
        if (encoding < 0x00 || encoding > 0x0A) {
            Rlog.w(TAG, "viacode: unavalid encoding = " + encoding);
            Rlog.w(TAG, "viacode: to use the unkown default.");
            encoding = android.telephony.SmsMessage.ENCODING_UNKNOWN;
        }

        mRemainingMessages = msgCount;

        TextEncodingDetails[] encodingForParts = new TextEncodingDetails[msgCount];
        if (encoding == android.telephony.SmsMessage.ENCODING_UNKNOWN) {
            Rlog.d(TAG, "viacode: unkown encoding, to find one best.");
            for (int i = 0; i < msgCount; i++) {
                TextEncodingDetails details = calculateLength(parts.get(i), false);
                if (encoding != details.codeUnitSize
                        && (encoding == android.telephony.SmsMessage.ENCODING_UNKNOWN
                                || encoding == android.telephony.SmsMessage.ENCODING_7BIT)) {
                    encoding = details.codeUnitSize;
                }
                encodingForParts[i] = details;
            }
        } else {
            Rlog.d(TAG, "viacode: APP want use specified encoding type.");
            for (int i = 0; i < msgCount; i++) {
                TextEncodingDetails details = SmsMessage.calculateLength(parts.get(i), false, encoding);
                details.codeUnitSize = encoding;
                encodingForParts[i] = details;
            }
        }

        Rlog.d(TAG, "viacode: now to send one by one, msgCount = " + msgCount);
        for (int i = 0; i < msgCount; i++) {
            SmsHeader.ConcatRef concatRef = new SmsHeader.ConcatRef();
            concatRef.refNumber = refNumber;
            concatRef.seqNumber = i + 1;  // 1-based sequence
            concatRef.msgCount = msgCount;
            // TODO: We currently set this to true since our messaging app will never
            // send more than 255 parts (it converts the message to MMS well before that).
            // However, we should support 3rd party messaging apps that might need 16-bit
            // references
            // Note:  It's not sufficient to just flip this bit to true; it will have
            // ripple effects (several calculations assume 8-bit ref).
            concatRef.isEightBits = true;
            SmsHeader smsHeader = new SmsHeader();
            smsHeader.concatRef = concatRef;

            PendingIntent sentIntent = null;
            if (sentIntents != null && sentIntents.size() > i) {
                sentIntent = sentIntents.get(i);
            }

            PendingIntent deliveryIntent = null;
            if (deliveryIntents != null && deliveryIntents.size() > i) {
                deliveryIntent = deliveryIntents.get(i);
            }

            SmsMessage.SubmitPdu submitPdu = SmsMessage.getSubmitPdu(scAddr, destAddr, parts.get(i),
                                                 (deliveryIntent != null), smsHeader, encoding, validityPeriod);
            if(submitPdu != null) {
                HashMap map =  getSmsTrackerMap(destAddr, scAddr, parts.get(i), submitPdu);
                SmsTracker tracker = getSmsTracker(map, sentIntent, deliveryIntent, getFormat());
                sendSubmitPdu(tracker);
            } else {
                Rlog.d(TAG, "viacode: sendMultipartTextWithEncodingType: submitPdu is null");
                if(sentIntent != null) {
                    try {
                        sentIntent.send(RESULT_ERROR_NULL_PDU);
                    } catch (CanceledException ex) {
                        Rlog.e(TAG, "viacode: failed to send back RESULT_ERROR_NULL_PDU");
                    }
                }
            }
        }
    }

    protected android.telephony.SmsMessage createMessageFromSubmitPdu(byte[] smsc, byte[] tpdu) {
        return android.telephony.SmsMessage.createFromPdu(RuimSmsInterfaces.convertSubmitpduToPdu(tpdu), getFormat());
    }
}
