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

package com.android.internal.telephony;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.app.PendingIntent.CanceledException;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.database.ContentObserver;
import android.database.sqlite.SqliteWrapper;
import android.net.Uri;
import android.os.AsyncResult;
import android.os.Binder;
import android.os.Handler;
import android.os.Message;
import android.os.SystemProperties;
import android.provider.Settings;
import android.provider.Telephony;
import android.provider.Telephony.Sms;
import android.telephony.PhoneNumberUtils;
import android.telephony.Rlog;
import android.telephony.ServiceState;
import android.telephony.TelephonyManager;
import android.text.Html;
import android.text.Spanned;
import android.util.EventLog;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.TextView;

import com.android.internal.R;
import com.android.internal.telephony.GsmAlphabet.TextEncodingDetails;
import com.android.internal.telephony.ImsSMSDispatcher;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

import static android.telephony.SmsManager.RESULT_ERROR_FDN_CHECK_FAILURE;
import static android.telephony.SmsManager.RESULT_ERROR_GENERIC_FAILURE;
import static android.telephony.SmsManager.RESULT_ERROR_LIMIT_EXCEEDED;
import static android.telephony.SmsManager.RESULT_ERROR_NO_SERVICE;
import static android.telephony.SmsManager.RESULT_ERROR_NULL_PDU;
import static android.telephony.SmsManager.RESULT_ERROR_RADIO_OFF;

// MTK-START
import android.os.Bundle;
import android.content.IntentFilter;
import com.android.internal.telephony.PhoneConstants;
import java.util.List;
import android.app.NotificationManager;
import android.app.Notification;
import com.mediatek.common.featureoption.FeatureOption;
import com.mediatek.common.telephony.ITelephonyEx;

// SMS ready
import android.provider.Telephony.Sms.Intents;
import android.os.PowerManager;

// DM-Agent
import android.content.BroadcastReceiver;
import android.os.ServiceManager;
import android.os.RemoteException;

// for Netqin lib
import android.telephony.SmsMessage;
import com.netqin.NqSmsFilter;

import com.mediatek.common.MediatekClassFactory;
import android.app.ActivityManager;
import java.util.Iterator;

// Mobile manager service for phone privacy lock
import com.mediatek.common.ppl.IPplSmsFilter;
// MTK-END

public abstract class SMSDispatcher extends Handler {
    static final String TAG = "SMSDispatcher";    // accessed from inner class
    static final boolean DBG = false;
    private static final String SEND_NEXT_MSG_EXTRA = "SendNextMsg";

    // MTK-START
    /** Default timeout for SMS sent query */
    private static final int DEFAULT_SMS_TIMEOUT = 6000;
    // MTK-END

    /** Permission required to send SMS to short codes without user confirmation. */
    private static final String SEND_SMS_NO_CONFIRMATION_PERMISSION =
            "android.permission.SEND_SMS_NO_CONFIRMATION";

    private static final int PREMIUM_RULE_USE_SIM = 1;
    private static final int PREMIUM_RULE_USE_NETWORK = 2;
    private static final int PREMIUM_RULE_USE_BOTH = 3;
    private final AtomicInteger mPremiumSmsRule = new AtomicInteger(PREMIUM_RULE_USE_SIM);
    private final SettingsObserver mSettingsObserver;

    /** SMS send complete. */
    protected static final int EVENT_SEND_SMS_COMPLETE = 2;

    /** Retry sending a previously failed SMS message */
    private static final int EVENT_SEND_RETRY = 3;

    /** Confirmation required for sending a large number of messages. */
    private static final int EVENT_SEND_LIMIT_REACHED_CONFIRMATION = 4;

    /** Send the user confirmed SMS */
    static final int EVENT_SEND_CONFIRMED_SMS = 5;  // accessed from inner class

    /** Don't send SMS (user did not confirm). */
    static final int EVENT_STOP_SENDING = 7;        // accessed from inner class

    /** Confirmation required for third-party apps sending to an SMS short code. */
    private static final int EVENT_CONFIRM_SEND_TO_POSSIBLE_PREMIUM_SHORT_CODE = 8;

    /** Confirmation required for third-party apps sending to an SMS short code. */
    private static final int EVENT_CONFIRM_SEND_TO_PREMIUM_SHORT_CODE = 9;

    /** Handle status report from {@code CdmaInboundSmsHandler}. */
    protected static final int EVENT_HANDLE_STATUS_REPORT = 10;

    /** Radio is ON */
    protected static final int EVENT_RADIO_ON = 11;

    /** IMS registration/SMS format changed */
    protected static final int EVENT_IMS_STATE_CHANGED = 12;

    /** Callback from RIL_REQUEST_IMS_REGISTRATION_STATE */
    protected static final int EVENT_IMS_STATE_DONE = 13;

    // other
    protected static final int EVENT_NEW_ICC_SMS = 14;
    protected static final int EVENT_ICC_CHANGED = 15;

    // MTK-START
    /** Activate/Inactivate Cell Broadcast complete */
    static final protected int EVENT_ACTIVATE_CB_COMPLETE = 101;

    /** Get Cell Broadcast Configuration complete */
    static final protected int EVENT_GET_CB_CONFIG_COMPLETE = 102;

    /** Set Cell Broadcast Configuration complete */
    static final protected int EVENT_SET_CB_CONFIG_COMPLETE = 103;

    /** Get Cell Broadcast Configuration complete */
    static final protected int EVENT_QUERY_CB_ACTIVATION_COMPLETE = 104;

    /** SMS subsystem in the modem is ready */
    static final protected int EVENT_SMS_READY = 105;

    /** reducted message handling */
    static final protected int EVENT_HANDLE_REDUCTED_MESSAGE = 106;
    static final protected int EVENT_REDUCTED_MESSAGE_TIMEOUT = 107;

    /** copy text message to the ICC card */
    static final protected int EVENT_COPY_TEXT_MESSAGE_DONE = 108;
    // MTK-END

    protected PhoneBase mPhone;
    protected final Context mContext;
    protected final ContentResolver mResolver;
    protected final CommandsInterface mCi;
    protected final TelephonyManager mTelephonyManager;

    /** Maximum number of times to retry sending a failed SMS. */
    private static final int MAX_SEND_RETRIES = 3;
    /** Delay before next send attempt on a failed SMS, in milliseconds. */
    private static final int SEND_RETRY_DELAY = 2000;
    /** single part SMS */
    private static final int SINGLE_PART_SMS = 1;
    /** Message sending queue limit */
    private static final int MO_MSG_QUEUE_LIMIT = 5;

    /**
     * Message reference for a CONCATENATED_8_BIT_REFERENCE or
     * CONCATENATED_16_BIT_REFERENCE message set.  Should be
     * incremented for each set of concatenated messages.
     * Static field shared by all dispatcher objects.
     */
    private static int sConcatenatedRef = new Random().nextInt(256);

    /** Outgoing message counter. Shared by all dispatchers. */
    private SmsUsageMonitor mUsageMonitor;

    private ImsSMSDispatcher mImsSMSDispatcher;

    /** Number of outgoing SmsTrackers waiting for user confirmation. */
    private int mPendingTrackerCount;

    // MTK-START
    /**
     * This list is used to maintain the unsent Sms Tracker
     * we have this queue list to avoid we send a lot of SEND_SMS request to RIL
     * and block other commands.
     * So we only send the next SEND_SMS request after the previously request has been completed
     */
    protected ArrayList<SmsTracker> mSTrackersQueue = new ArrayList<SmsTracker>(MO_MSG_QUEUE_LIMIT);
    // MTK-END

    /* Flags indicating whether the current device allows sms service */
    protected boolean mSmsCapable = true;
    protected boolean mSmsSendDisabled;

    protected int mRemainingMessages = -1;

    protected static int getNextConcatenatedRef() {
        sConcatenatedRef += 1;
        return sConcatenatedRef;
    }

    // MTK-START
    /**
     * Hold the wake lock for 5 seconds, which should be enough time for
     * any receiver(s) to grab its own wake lock.(SMS ready intent)
     */
    protected static final int WAKE_LOCK_TIMEOUT = 5000;
    /** Wake lock to ensure device stays awake while dispatching the SMS ready intent. */
    protected PowerManager.WakeLock mWakeLock;

    // MTK_OPTR_PROTECT_START
    protected static boolean isDmLock = false;
    // MTK_OPTR_PROTECT_END

    // flag of storage status
    /** For FTA test only */
    protected boolean mStorageAvailable = true;

    protected int mSimId = PhoneConstants.GEMINI_SIM_1;

    protected boolean mSmsReady = false;

    // for copying text message to ICC card
    protected int messageCountNeedCopy = 0;
    protected Object mLock = new Object();
    protected boolean mSuccess = true;

    // for Netqin SMS checking
    private static SmsHeader.ConcatRef sConcatRef = null;
    private static boolean sRefuseSent = true;
    private static int sConcatMsgCount = 0;

    protected static String PDU_SIZE = "pdu_size";

    /** Mobile manager service for phone privacy lock */
    private IPplSmsFilter mPplSmsFilter = null;
    // MTK-END

    /**
     * Create a new SMS dispatcher.
     * @param phone the Phone to use
     * @param usageMonitor the SmsUsageMonitor to use
     */
    protected SMSDispatcher(PhoneBase phone, SmsUsageMonitor usageMonitor,
            ImsSMSDispatcher imsSMSDispatcher) {
        mPhone = phone;
        mImsSMSDispatcher = imsSMSDispatcher;
        mContext = phone.getContext();
        mResolver = mContext.getContentResolver();
        mCi = phone.mCi;
        mUsageMonitor = usageMonitor;
        mTelephonyManager = (TelephonyManager) mContext.getSystemService(Context.TELEPHONY_SERVICE);
        mSettingsObserver = new SettingsObserver(this, mPremiumSmsRule, mContext);
        mContext.getContentResolver().registerContentObserver(Settings.Global.getUriFor(
                Settings.Global.SMS_SHORT_CODE_RULE), false, mSettingsObserver);

        mSmsCapable = mContext.getResources().getBoolean(
                com.android.internal.R.bool.config_sms_capable);
        mSmsSendDisabled = !SystemProperties.getBoolean(
                                TelephonyProperties.PROPERTY_SMS_SEND, mSmsCapable);
        Rlog.d(TAG, "SMSDispatcher: ctor mSmsCapable=" + mSmsCapable + " format=" + getFormat()
                + " mSmsSendDisabled=" + mSmsSendDisabled);

        // MTK-START
        createWakelock();
        mCi.registerForSmsReady(this, EVENT_SMS_READY, null);

        mSimId = mPhone.getMySimId();

        // MTK_OPTR_PROTECT_START
        // register DM broadcast receiver
        IntentFilter dmFilter = new IntentFilter();
        dmFilter.addAction("com.mediatek.dm.LAWMO_LOCK");
        dmFilter.addAction("com.mediatek.dm.LAWMO_UNLOCK");
        mContext.registerReceiver(mDMLockReceiver, dmFilter);
        // MTK_OPTR_PROTECT_END

        // Create the instance for phone privacy lock
       if (FeatureOption.MTK_PRIVACY_PROTECTION_LOCK) {
           try {
                mPplSmsFilter = MediatekClassFactory.createInstance(IPplSmsFilter.class, mContext);
                if (mPplSmsFilter != null) {
                    String actualClassName = mPplSmsFilter.getClass().getName();
                    Rlog.d(TAG, "initial mPplSmsFilter done, actual class name is " + actualClassName);
                } else {
                    Rlog.d(TAG, "FAIL! intial mPplSmsFilter");
                }
            } catch (RuntimeException e) {
                Rlog.e(TAG, "FAIL! No IPplSmsFilter");
            }
        }
        // MTK-END
    }

    /**
     * Observe the secure setting for updated premium sms determination rules
     */
    private static class SettingsObserver extends ContentObserver {
        private final AtomicInteger mPremiumSmsRule;
        private final Context mContext;
        SettingsObserver(Handler handler, AtomicInteger premiumSmsRule, Context context) {
            super(handler);
            mPremiumSmsRule = premiumSmsRule;
            mContext = context;
            onChange(false); // load initial value;
        }

        @Override
        public void onChange(boolean selfChange) {
            mPremiumSmsRule.set(Settings.Global.getInt(mContext.getContentResolver(),
                    Settings.Global.SMS_SHORT_CODE_RULE, PREMIUM_RULE_USE_SIM));
        }
    }

    protected void updatePhoneObject(PhoneBase phone) {
        mPhone = phone;
        mUsageMonitor = phone.mSmsUsageMonitor;
        Rlog.d(TAG, "Active phone changed to " + mPhone.getPhoneName() );
    }

    /** Unregister for incoming SMS events. */
    public void dispose() {
        mContext.getContentResolver().unregisterContentObserver(mSettingsObserver);
    }

    /**
     * The format of the message PDU in the associated broadcast intent.
     * This will be either "3gpp" for GSM/UMTS/LTE messages in 3GPP format
     * or "3gpp2" for CDMA/LTE messages in 3GPP2 format.
     *
     * Note: All applications which handle incoming SMS messages by processing the
     * SMS_RECEIVED_ACTION broadcast intent MUST pass the "format" extra from the intent
     * into the new methods in {@link android.telephony.SmsMessage} which take an
     * extra format parameter. This is required in order to correctly decode the PDU on
     * devices which require support for both 3GPP and 3GPP2 formats at the same time,
     * such as CDMA/LTE devices and GSM/CDMA world phones.
     *
     * @return the format of the message PDU
     */
    protected abstract String getFormat();

    /**
     * Pass the Message object to subclass to handle. Currently used to pass CDMA status reports
     * from {@link com.android.internal.telephony.cdma.CdmaInboundSmsHandler}.
     * @param o the SmsMessage containing the status report
     */
    protected void handleStatusReport(Object o) {
        Rlog.d(TAG, "handleStatusReport() called with no subclass.");
    }

    // MTK-START
    @Override
    protected void finalize() throws Throwable {
        super.finalize();
        Rlog.d(TAG, "SMSDispatcher finalized");
    }
    // MTK-END

    /* TODO: Need to figure out how to keep track of status report routing in a
     *       persistent manner. If the phone process restarts (reboot or crash),
     *       we will lose this list and any status reports that come in after
     *       will be dropped.
     */
    /** Sent messages awaiting a delivery status report. */
    protected final ArrayList<SmsTracker> deliveryPendingList = new ArrayList<SmsTracker>();

    /**
     * Handles events coming from the phone stack. Overridden from handler.
     *
     * @param msg the message to handle
     */
    @Override
    public void handleMessage(Message msg) {
        switch (msg.what) {
        case EVENT_SEND_SMS_COMPLETE:
            // An outbound SMS has been successfully transferred, or failed.
            handleSendComplete((AsyncResult) msg.obj);
            break;

        case EVENT_SEND_RETRY:
            Rlog.d(TAG, "SMS retry..");
            sendRetrySms((SmsTracker) msg.obj);
            break;

        case EVENT_SEND_LIMIT_REACHED_CONFIRMATION:
            handleReachSentLimit((SmsTracker)(msg.obj));
            break;

        case EVENT_CONFIRM_SEND_TO_POSSIBLE_PREMIUM_SHORT_CODE:
            handleConfirmShortCode(false, (SmsTracker)(msg.obj));
            break;

        case EVENT_CONFIRM_SEND_TO_PREMIUM_SHORT_CODE:
            handleConfirmShortCode(true, (SmsTracker)(msg.obj));
            break;

        case EVENT_SEND_CONFIRMED_SMS:
        {
            SmsTracker tracker = (SmsTracker) msg.obj;
            if (tracker.isMultipart()) {
                sendMultipartSms(tracker);
            } else {
                sendSms(tracker);
            }
            mPendingTrackerCount--;
            break;
        }

        case EVENT_STOP_SENDING:
        {
            SmsTracker tracker = (SmsTracker) msg.obj;
            if (tracker.mSentIntent != null) {
                try {
                    tracker.mSentIntent.send(RESULT_ERROR_LIMIT_EXCEEDED);
                } catch (CanceledException ex) {
                    Rlog.e(TAG, "failed to send RESULT_ERROR_LIMIT_EXCEEDED");
                }
            }
            mPendingTrackerCount--;
            break;
        }

        case EVENT_HANDLE_STATUS_REPORT:
            handleStatusReport(msg.obj);
            break;

        // MTK-START
        case EVENT_ACTIVATE_CB_COMPLETE:
        case EVENT_GET_CB_CONFIG_COMPLETE:
        case EVENT_SET_CB_CONFIG_COMPLETE:
        {
            AsyncResult ar;
            ar = (AsyncResult) msg.obj;
            AsyncResult.forMessage((Message) ar.userObj, ar.result, ar.exception);
            ((Message) ar.userObj).sendToTarget();
            break;
        }

        case EVENT_QUERY_CB_ACTIVATION_COMPLETE:
            handleQueryCbActivation((AsyncResult) msg.obj);
            break;

        case EVENT_SMS_READY:
        {
            Rlog.d(TAG, "SMS is ready, SIM: " + mSimId);
            mSmsReady = true;

            notifySmsReady(mSmsReady);
            break;
        }

        case EVENT_COPY_TEXT_MESSAGE_DONE:
        {
            AsyncResult ar;
            ar = (AsyncResult)msg.obj;
            synchronized (mLock) {
                mSuccess = (ar.exception == null);

                if(mSuccess == true) {
                    Rlog.d(TAG, "[copyText success to copy one");
                    messageCountNeedCopy -= 1;
                } else {
                    Rlog.d(TAG, "[copyText fail to copy one");
                    messageCountNeedCopy = 0;
                }

                mLock.notifyAll();
            }
            break;
        }

        case EVENT_HANDLE_REDUCTED_MESSAGE:
            handleDeductedMessage((SmsTracker)(msg.obj));
            break;

        case EVENT_REDUCTED_MESSAGE_TIMEOUT:
        {
            SmsTracker tracker = (SmsTracker) msg.obj;

            if (tracker != null) {
                try {
                    if(tracker.mSentIntent != null) {
                        tracker.mSentIntent.send(RESULT_ERROR_LIMIT_EXCEEDED);
                    }
                } catch (CanceledException ex) {
                    Rlog.e(TAG, "failed to send back RESULT_ERROR_LIMIT_EXCEEDED");
                }
            }

            while(sConcatMsgCount > 0 && mPendingTrackerCount > 0) {
                sConcatMsgCount -= 1;
            }
            break;
        }
        // MTK-END

        default:
            Rlog.e(TAG, "handleMessage() ignoring message of unexpected type " + msg.what);
        }
    }

    /**
     * Called when SMS send completes. Broadcasts a sentIntent on success.
     * On failure, either sets up retries or broadcasts a sentIntent with
     * the failure in the result code.
     *
     * @param ar AsyncResult passed into the message handler.  ar.result should
     *           an SmsResponse instance if send was successful.  ar.userObj
     *           should be an SmsTracker instance.
     */
    protected void handleSendComplete(AsyncResult ar) {
        SmsTracker tracker = (SmsTracker) ar.userObj;
        PendingIntent sentIntent = tracker.mSentIntent;

        // MTK-START
        int szPdu = 0;
        if(tracker != null) {
            HashMap map = tracker.mData;
            if(map != null) {
                int smscLength = (map.get("smsc") == null) ? 0 : (((byte[])map.get("smsc")).length);
                int pduLength = (map.get("pdu") == null) ? 0 : (((byte[])map.get("pdu")).length);
                szPdu = smscLength + pduLength;
            }
        }
        synchronized (mSTrackersQueue) {
            // remove the first tracker and send the next one if any
            Rlog.d(TAG, "Remove Tracker");
            SmsTracker tempTracker = (!mSTrackersQueue.isEmpty()) ? mSTrackersQueue.remove(0) : null;
            if(tempTracker != null && tempTracker.equals(tracker)) {
                Rlog.d(TAG, "[pdu size: " + szPdu);
            }

            if (!mSTrackersQueue.isEmpty()) {
                SmsTracker sendtracker = mSTrackersQueue.get(0);

                sendSms(sendtracker);
            }
        }
        // MTK-END

        if (ar.result != null) {
            tracker.mMessageRef = ((SmsResponse)ar.result).mMessageRef;
        } else {
            Rlog.d(TAG, "SmsResponse was null");
        }

        if (ar.exception == null) {
            if (DBG) Rlog.d(TAG, "SMS send complete. Broadcasting intent: " + sentIntent);

            // MTK-START
            boolean pplResult = false;
            if (FeatureOption.MTK_PRIVACY_PROTECTION_LOCK) {
                String defaultSmsPackage = Sms.getDefaultSmsPackage(mContext);
                if (defaultSmsPackage == null ||
                        !defaultSmsPackage.equals(tracker.mAppInfo.applicationInfo.packageName)) {
                    /* Start to check phone privacy check if it does not need to write to database */
                    Rlog.d(TAG, "[Moms] Phone privacy check start");
                    pplResult = true;
                    byte[][] pdus = new byte[1][];
                    int pduLength = ((byte[]) tracker.mData.get("pdu")).length;
                    pdus[0] = new byte[pduLength + 1];
                    //0x00 means no SMSC specified.
                    pdus[0][0]=0x00;
                    System.arraycopy((byte[]) tracker.mData.get("pdu"), 0, pdus[0], 1, pduLength);

                    Bundle pplData = new Bundle();
                    pplData.putSerializable(mPplSmsFilter.KEY_PDUS, pdus);
                    pplData.putString(mPplSmsFilter.KEY_FORMAT, getFormat());
                    pplData.putInt(mPplSmsFilter.KEY_SIM_ID, mSimId);
                    pplData.putInt(mPplSmsFilter.KEY_SMS_TYPE, 1);

                    pplResult = mPplSmsFilter.pplFilter(pplData);
                    Rlog.d(TAG, "[Moms] Phone privacy check end, Need to filter(result) = " + pplResult);
                }
            }
            // MTK-END

            if ((pplResult == false) && SmsApplication.shouldWriteMessageForPackage(
                    tracker.mAppInfo.applicationInfo.packageName, mContext)) {
                // Persist it into the SMS database as a sent message
                // so the user can see it in their default app.
                tracker.writeSentMessage(mContext);
            }

            if (tracker.mDeliveryIntent != null) {
                // Expecting a status report.  Add it to the list.
                deliveryPendingList.add(tracker);
            }

            if (sentIntent != null) {
                try {
                    if (mRemainingMessages > -1) {
                        mRemainingMessages--;
                    }

                    if (mRemainingMessages == 0) {
                        Intent sendNext = new Intent();
                        sendNext.putExtra(SEND_NEXT_MSG_EXTRA, true);
                        // MTK-START
                        sendNext.putExtra(PDU_SIZE, szPdu);
                        // MTK-END
                        sentIntent.send(mContext, Activity.RESULT_OK, sendNext);
                    } else {
                        // MTK-START
                        Intent fillIn = new Intent();
                        fillIn.putExtra(PDU_SIZE, szPdu);
                        sentIntent.send(mContext, Activity.RESULT_OK, fillIn);
                        // MTK-END
                        // sentIntent.send(Activity.RESULT_OK);
                    }
                } catch (CanceledException ex) {
                    Rlog.d(TAG, "CanceledException happened when send sms success with sentIntent");
                }
            } else {
                Rlog.d(TAG, "Send sms success without sentIntent");
            }
        } else {
            if (DBG) Rlog.d(TAG, "SMS send failed");

            // MTK-START
            // for ALPS00044719
            boolean isTestIccCard = false;
            try {
                ITelephonyEx telephony = ITelephonyEx.Stub.asInterface(
                        ServiceManager.getService(Context.TELEPHONY_SERVICEEX));
                if (telephony != null) {
                    isTestIccCard = telephony.isTestIccCard(mSimId);
                }
            } catch (RemoteException ex) {
                // This shouldn't happen in the normal case
                Rlog.d(TAG, "SD-handleSendComplete: RemoteException: " + ex.getMessage());
            } catch (NullPointerException ex) {
                // This could happen before phone restarts due to crashing
                Rlog.d(TAG, "SD-handleSendComplete: NullPointerException: " + ex.getMessage());
            }

            Rlog.d(TAG, "SD-handleSendComplete: SIM" + mSimId + " isTestIccCard " + isTestIccCard);
            // for ALPS00044719
            // MTK-END

            int ss = mPhone.getServiceState().getState();

            if ( tracker.mImsRetry > 0 && ss != ServiceState.STATE_IN_SERVICE) {
                // This is retry after failure over IMS but voice is not available.
                // Set retry to max allowed, so no retry is sent and
                //   cause RESULT_ERROR_GENERIC_FAILURE to be returned to app.
                tracker.mRetryCount = MAX_SEND_RETRIES;

                Rlog.d(TAG, "handleSendComplete: Skipping retry: "
                +" isIms()="+isIms()
                +" mRetryCount="+tracker.mRetryCount
                +" mImsRetry="+tracker.mImsRetry
                +" mMessageRef="+tracker.mMessageRef
                +" SS= "+mPhone.getServiceState().getState());
            }

            // if sms over IMS is not supported on data and voice is not available...
            if (!isIms() && ss != ServiceState.STATE_IN_SERVICE) {
                Rlog.d(TAG, "handleSendComplete: No service");
                handleNotInService(ss, tracker.mSentIntent);
            /* MTK-START: No need to retry due to modem has already retry it */
//          } else if ((((CommandException)(ar.exception)).getCommandError()
//                  == CommandException.Error.SMS_FAIL_RETRY) &&
//                 tracker.mRetryCount < MAX_SEND_RETRIES) {
//              // Retry after a delay if needed.
//              // TODO: According to TS 23.040, 9.2.3.6, we should resend
//              //       with the same TP-MR as the failed message, and
//              //       TP-RD set to 1.  However, we don't have a means of
//              //       knowing the MR for the failed message (EF_SMSstatus
//              //       may or may not have the MR corresponding to this
//              //       message, depending on the failure).  Also, in some
//              //       implementations this retry is handled by the baseband.
//              tracker.mRetryCount++;
//              Message retryMsg = obtainMessage(EVENT_SEND_RETRY, tracker);
//              sendMessageDelayed(retryMsg, SEND_RETRY_DELAY);
            /* MTK-END: No need to retry due to modem has already retry it */
            } else if (tracker.mSentIntent != null) {
                int error = RESULT_ERROR_GENERIC_FAILURE;

                if (((CommandException)(ar.exception)).getCommandError()
                        == CommandException.Error.FDN_CHECK_FAILURE) {
                    error = RESULT_ERROR_FDN_CHECK_FAILURE;
                }
                // Done retrying; return an error to the app.
                try {
                    Intent fillIn = new Intent();
                    // MTK-START
                    // add pdu size
                    fillIn.putExtra(PDU_SIZE, szPdu);
                    // MTK-END
                    if (ar.result != null) {
                        fillIn.putExtra("errorCode", ((SmsResponse)ar.result).mErrorCode);
                    }
                    if (mRemainingMessages > -1) {
                        mRemainingMessages--;
                    }

                    if (mRemainingMessages == 0) {
                        fillIn.putExtra(SEND_NEXT_MSG_EXTRA, true);
                    }

                    tracker.mSentIntent.send(mContext, error, fillIn);
                } catch (CanceledException ex) {
                    Rlog.d(TAG, "CanceledException happened when send sms fail with sentIntent");
                }
            } else {
                Rlog.d(TAG, "Send sms fail without sentIntent");
            }
        }
    }

    /**
     * Handles outbound message when the phone is not in service.
     *
     * @param ss     Current service state.  Valid values are:
     *                  OUT_OF_SERVICE
     *                  EMERGENCY_ONLY
     *                  POWER_OFF
     * @param sentIntent the PendingIntent to send the error to
     */
    protected static void handleNotInService(int ss, PendingIntent sentIntent) {
        if (sentIntent != null) {
            try {
                if (ss == ServiceState.STATE_POWER_OFF) {
                    sentIntent.send(RESULT_ERROR_RADIO_OFF);
                } else {
                    sentIntent.send(RESULT_ERROR_NO_SERVICE);
                }
            } catch (CanceledException ex) {
                Rlog.d(TAG, "CanceledException happened when send sms fail with sentIntent due to no service");
            }
        } else {
            Rlog.d(TAG, "Send sms fail without sentIntent due to no service");
        }
    }

    /**
     * Send a data based SMS to a specific application port.
     *
     * @param destAddr the address to send the message to
     * @param scAddr is the service center address or null to use
     *  the current default SMSC
     * @param destPort the port to deliver the message to
     * @param data the body of the message to send
     * @param sentIntent if not NULL this <code>PendingIntent</code> is
     *  broadcast when the message is successfully sent, or failed.
     *  The result code will be <code>Activity.RESULT_OK<code> for success,
     *  or one of these errors:<br>
     *  <code>RESULT_ERROR_GENERIC_FAILURE</code><br>
     *  <code>RESULT_ERROR_RADIO_OFF</code><br>
     *  <code>RESULT_ERROR_NULL_PDU</code><br>
     *  <code>RESULT_ERROR_NO_SERVICE</code><br>.
     *  For <code>RESULT_ERROR_GENERIC_FAILURE</code> the sentIntent may include
     *  the extra "errorCode" containing a radio technology specific value,
     *  generally only useful for troubleshooting.<br>
     *  The per-application based SMS control checks sentIntent. If sentIntent
     *  is NULL the caller will be checked against all unknown applications,
     *  which cause smaller number of SMS to be sent in checking period.
     * @param deliveryIntent if not NULL this <code>PendingIntent</code> is
     *  broadcast when the message is delivered to the recipient.  The
     *  raw pdu of the status report is in the extended data ("pdu").
     */
    protected abstract void sendData(String destAddr, String scAddr, int destPort,
            byte[] data, PendingIntent sentIntent, PendingIntent deliveryIntent);

    /**
     * Send a text based SMS.
     *
     * @param destAddr the address to send the message to
     * @param scAddr is the service center address or null to use
     *  the current default SMSC
     * @param text the body of the message to send
     * @param sentIntent if not NULL this <code>PendingIntent</code> is
     *  broadcast when the message is successfully sent, or failed.
     *  The result code will be <code>Activity.RESULT_OK<code> for success,
     *  or one of these errors:<br>
     *  <code>RESULT_ERROR_GENERIC_FAILURE</code><br>
     *  <code>RESULT_ERROR_RADIO_OFF</code><br>
     *  <code>RESULT_ERROR_NULL_PDU</code><br>
     *  <code>RESULT_ERROR_NO_SERVICE</code><br>.
     *  For <code>RESULT_ERROR_GENERIC_FAILURE</code> the sentIntent may include
     *  the extra "errorCode" containing a radio technology specific value,
     *  generally only useful for troubleshooting.<br>
     *  The per-application based SMS control checks sentIntent. If sentIntent
     *  is NULL the caller will be checked against all unknown applications,
     *  which cause smaller number of SMS to be sent in checking period.
     * @param deliveryIntent if not NULL this <code>PendingIntent</code> is
     *  broadcast when the message is delivered to the recipient.  The
     *  raw pdu of the status report is in the extended data ("pdu").
     */
    protected abstract void sendText(String destAddr, String scAddr,
            String text, PendingIntent sentIntent, PendingIntent deliveryIntent);

    /**
     * Calculate the number of septets needed to encode the message.
     *
     * @param messageBody the message to encode
     * @param use7bitOnly ignore (but still count) illegal characters if true
     * @return TextEncodingDetails
     */
    protected abstract TextEncodingDetails calculateLength(CharSequence messageBody,
            boolean use7bitOnly);

    /**
     * Send a multi-part text based SMS.
     *
     * @param destAddr the address to send the message to
     * @param scAddr is the service center address or null to use
     *   the current default SMSC
     * @param parts an <code>ArrayList</code> of strings that, in order,
     *   comprise the original message
     * @param sentIntents if not null, an <code>ArrayList</code> of
     *   <code>PendingIntent</code>s (one for each message part) that is
     *   broadcast when the corresponding message part has been sent.
     *   The result code will be <code>Activity.RESULT_OK<code> for success,
     *   or one of these errors:
     *   <code>RESULT_ERROR_GENERIC_FAILURE</code>
     *   <code>RESULT_ERROR_RADIO_OFF</code>
     *   <code>RESULT_ERROR_NULL_PDU</code>
     *   <code>RESULT_ERROR_NO_SERVICE</code>.
     *  The per-application based SMS control checks sentIntent. If sentIntent
     *  is NULL the caller will be checked against all unknown applications,
     *  which cause smaller number of SMS to be sent in checking period.
     * @param deliveryIntents if not null, an <code>ArrayList</code> of
     *   <code>PendingIntent</code>s (one for each message part) that is
     *   broadcast when the corresponding message part has been delivered
     *   to the recipient.  The raw pdu of the status report is in the
     *   extended data ("pdu").
     */
    protected void sendMultipartText(String destAddr, String scAddr,
            ArrayList<String> parts, ArrayList<PendingIntent> sentIntents,
            ArrayList<PendingIntent> deliveryIntents) {

        int refNumber = getNextConcatenatedRef() & 0x00FF;
        int msgCount = parts.size();
        int encoding = SmsConstants.ENCODING_UNKNOWN;

        mRemainingMessages = msgCount;

        TextEncodingDetails[] encodingForParts = new TextEncodingDetails[msgCount];
        for (int i = 0; i < msgCount; i++) {
            TextEncodingDetails details = calculateLength(parts.get(i), false);
            if (encoding != details.codeUnitSize
                    && (encoding == SmsConstants.ENCODING_UNKNOWN
                            || encoding == SmsConstants.ENCODING_7BIT)) {
                encoding = details.codeUnitSize;
            }
            encodingForParts[i] = details;
        }

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

            // Set the national language tables for 3GPP 7-bit encoding, if enabled.
            if (encoding == SmsConstants.ENCODING_7BIT) {
                smsHeader.languageTable = encodingForParts[i].languageTable;
                smsHeader.languageShiftTable = encodingForParts[i].languageShiftTable;
            }

            PendingIntent sentIntent = null;
            if (sentIntents != null && sentIntents.size() > i) {
                sentIntent = sentIntents.get(i);
            }

            PendingIntent deliveryIntent = null;
            if (deliveryIntents != null && deliveryIntents.size() > i) {
                deliveryIntent = deliveryIntents.get(i);
            }

            sendNewSubmitPdu(destAddr, scAddr, parts.get(i), smsHeader, encoding,
                    sentIntent, deliveryIntent, (i == (msgCount - 1)));
        }

    }

    /**
     * Create a new SubmitPdu and send it.
     */
    protected abstract void sendNewSubmitPdu(String destinationAddress, String scAddress,
            String message, SmsHeader smsHeader, int encoding,
            PendingIntent sentIntent, PendingIntent deliveryIntent, boolean lastPart);

    /**
     * Send a SMS
     * @param tracker will contain:
     * -smsc the SMSC to send the message through, or NULL for the
     *  default SMSC
     * -pdu the raw PDU to send
     * -sentIntent if not NULL this <code>Intent</code> is
     *  broadcast when the message is successfully sent, or failed.
     *  The result code will be <code>Activity.RESULT_OK<code> for success,
     *  or one of these errors:
     *  <code>RESULT_ERROR_GENERIC_FAILURE</code>
     *  <code>RESULT_ERROR_RADIO_OFF</code>
     *  <code>RESULT_ERROR_NULL_PDU</code>
     *  <code>RESULT_ERROR_NO_SERVICE</code>.
     *  The per-application based SMS control checks sentIntent. If sentIntent
     *  is NULL the caller will be checked against all unknown applications,
     *  which cause smaller number of SMS to be sent in checking period.
     * -deliveryIntent if not NULL this <code>Intent</code> is
     *  broadcast when the message is delivered to the recipient.  The
     *  raw pdu of the status report is in the extended data ("pdu").
     * -param destAddr the destination phone number (for short code confirmation)
     */
    protected void sendRawPdu(SmsTracker tracker) {
        HashMap map = tracker.mData;
        byte pdu[] = (byte[]) map.get("pdu");

        PendingIntent sentIntent = tracker.mSentIntent;
        if (mSmsSendDisabled) {
            if (sentIntent != null) {
                try {
                    sentIntent.send(RESULT_ERROR_NO_SERVICE);
                } catch (CanceledException ex) {}
            }
            Rlog.d(TAG, "Device does not support sending sms.");
            return;
        }

        if (pdu == null) {
            if (sentIntent != null) {
                try {
                    sentIntent.send(RESULT_ERROR_NULL_PDU);
                } catch (CanceledException ex) {}
            }
            return;
        }

        // Get calling app package name via UID from Binder call
        PackageManager pm = mContext.getPackageManager();
        String[] packageNames = pm.getPackagesForUid(Binder.getCallingUid());

        if (packageNames == null || packageNames.length == 0) {
            // Refuse to send SMS if we can't get the calling package name.
            Rlog.e(TAG, "Can't get calling app package name: refusing to send SMS");
            if (sentIntent != null) {
                try {
                    sentIntent.send(RESULT_ERROR_GENERIC_FAILURE);
                } catch (CanceledException ex) {
                    Rlog.e(TAG, "failed to send error result");
                }
            }
            return;
        }

        // MTK-START
        /* Because it may have multiple apks use the same uid, ex. Mms.apk and omacp.apk, we need to
         * exactly find the correct calling apk. We should use running process to check the correct
         * apk. If we could not find the process via pid, this apk may be killed. We will use the
         * default behavior, find the first package name via uid.
         */
        String packageName = getPackageNameViaProcessId(packageNames);
        if (packageName != null) {
            packageNames[0] = packageName;
        }
        Rlog.d(TAG, "sendRawPdu and get the package name via process id: " + packageNames[0]);
        // MTK-END

        // Get package info via packagemanager
        PackageInfo appInfo;
        try {
            // XXX this is lossy- apps can share a UID
            appInfo = pm.getPackageInfo(packageNames[0], PackageManager.GET_SIGNATURES);
        } catch (PackageManager.NameNotFoundException e) {
            Rlog.e(TAG, "Can't get calling app package info: refusing to send SMS");
            if (sentIntent != null) {
                try {
                    sentIntent.send(RESULT_ERROR_GENERIC_FAILURE);
                } catch (CanceledException ex) {
                    Rlog.e(TAG, "failed to send error result");
                }
            }
            return;
        }

        // checkDestination() returns true if the destination is not a premium short code or the
        // sending app is approved to send to short codes. Otherwise, a message is sent to our
        // handler with the SmsTracker to request user confirmation before sending.
        if (checkDestination(tracker)) {
            // check for excessive outgoing SMS usage by this app
            if (!mUsageMonitor.check(appInfo.packageName, SINGLE_PART_SMS)) {
                sendMessage(obtainMessage(EVENT_SEND_LIMIT_REACHED_CONFIRMATION, tracker));
                return;
            }

            int ss = mPhone.getServiceState().getState();

            // if sms over IMS is not supported on data and voice is not available...
            if (!isIms() && ss != ServiceState.STATE_IN_SERVICE) {
                handleNotInService(ss, tracker.mSentIntent);
            } else {
                // MTK-START
                String appName = getAppNameByIntent(sentIntent);
                if(FeatureOption.MTK_SMS_FILTER_SUPPORT == true) {
                    SmsMessage msg = createMessageFromSubmitPdu((byte[])tracker.mData.get("smsc"), (byte[])tracker.mData.get("pdu"));
                    if(msg != null) {
                        boolean ret = checkSmsWithNqFilter(msg.getDestinationAddress(), msg.getMessageBody(), sentIntent);
                        if(ret == false) {
                            Rlog.d(TAG, "[NQ this message is safe");
                            sendSms(tracker);
                        } else {
                            Rlog.d(TAG, "[NQ this message may deduct fees");

                            SmsHeader.ConcatRef newConcatRef = null;
                            if(msg.getUserDataHeader() != null) {
                                newConcatRef = msg.getUserDataHeader().concatRef;
                            }

                            if(newConcatRef != null) {
                                if(sConcatRef == null || sConcatRef.refNumber != newConcatRef.refNumber) {
                                    Rlog.d(TAG, "[NQ this is a new concatenated message, just update");
                                    sConcatRef = newConcatRef;
                                    //sConcatMsgCount = 1;
                                    sendMessage(obtainMessage(EVENT_HANDLE_REDUCTED_MESSAGE, tracker));
                                } else {
                                    Rlog.d(TAG, "[NQ this is the same concatenated message, keep previous operation");
                                    //mSTrackers.add(tracker);
                                    sConcatMsgCount += 1;
                                }
                            } else {
                                Rlog.d(TAG, "[NQ this is a non-concatenated message");
                                //sConcatMsgCount = 0;
                                sendMessage(obtainMessage(EVENT_HANDLE_REDUCTED_MESSAGE, tracker));
                            }
                        }
                    } else {
                        Rlog.d(TAG, "[NQ fail to create message from pdu");
                        sendSms(tracker);
                    }
                } else {
                // MTK-END
                    sendSms(tracker);
                // MTK-START
                }
                // MTK-END
            }
        }
    }

    /**
     * Check if destination is a potential premium short code and sender is not pre-approved to
     * send to short codes.
     *
     * @param tracker the tracker for the SMS to send
     * @return true if the destination is approved; false if user confirmation event was sent
     */
    boolean checkDestination(SmsTracker tracker) {
        if (mContext.checkCallingOrSelfPermission(SEND_SMS_NO_CONFIRMATION_PERMISSION)
                == PackageManager.PERMISSION_GRANTED) {
            return true;            // app is pre-approved to send to short codes
        } else {
            int rule = mPremiumSmsRule.get();
            int smsCategory = SmsUsageMonitor.CATEGORY_NOT_SHORT_CODE;
            if (rule == PREMIUM_RULE_USE_SIM || rule == PREMIUM_RULE_USE_BOTH) {
                String simCountryIso = mTelephonyManager.getSimCountryIso();
                if (simCountryIso == null || simCountryIso.length() != 2) {
                    Rlog.e(TAG, "Can't get SIM country Iso: trying network country Iso");
                    simCountryIso = mTelephonyManager.getNetworkCountryIso();
                }

                smsCategory = mUsageMonitor.checkDestination(tracker.mDestAddress, simCountryIso);
            }
            if (rule == PREMIUM_RULE_USE_NETWORK || rule == PREMIUM_RULE_USE_BOTH) {
                String networkCountryIso = mTelephonyManager.getNetworkCountryIso();
                if (networkCountryIso == null || networkCountryIso.length() != 2) {
                    Rlog.e(TAG, "Can't get Network country Iso: trying SIM country Iso");
                    networkCountryIso = mTelephonyManager.getSimCountryIso();
                }

                smsCategory = SmsUsageMonitor.mergeShortCodeCategories(smsCategory,
                        mUsageMonitor.checkDestination(tracker.mDestAddress, networkCountryIso));
            }

            if (smsCategory == SmsUsageMonitor.CATEGORY_NOT_SHORT_CODE
                    || smsCategory == SmsUsageMonitor.CATEGORY_FREE_SHORT_CODE
                    || smsCategory == SmsUsageMonitor.CATEGORY_STANDARD_SHORT_CODE) {
                return true;    // not a premium short code
            }

            // Wait for user confirmation unless the user has set permission to always allow/deny
            int premiumSmsPermission = mUsageMonitor.getPremiumSmsPermission(
                    tracker.mAppInfo.packageName);
            if (premiumSmsPermission == SmsUsageMonitor.PREMIUM_SMS_PERMISSION_UNKNOWN) {
                // First time trying to send to premium SMS.
                premiumSmsPermission = SmsUsageMonitor.PREMIUM_SMS_PERMISSION_ASK_USER;
            }

            switch (premiumSmsPermission) {
                case SmsUsageMonitor.PREMIUM_SMS_PERMISSION_ALWAYS_ALLOW:
                    Rlog.d(TAG, "User approved this app to send to premium SMS");
                    return true;

                case SmsUsageMonitor.PREMIUM_SMS_PERMISSION_NEVER_ALLOW:
                    Rlog.w(TAG, "User denied this app from sending to premium SMS");
                    sendMessage(obtainMessage(EVENT_STOP_SENDING, tracker));
                    return false;   // reject this message

                case SmsUsageMonitor.PREMIUM_SMS_PERMISSION_ASK_USER:
                default:
                    int event;
                    if (smsCategory == SmsUsageMonitor.CATEGORY_POSSIBLE_PREMIUM_SHORT_CODE) {
                        event = EVENT_CONFIRM_SEND_TO_POSSIBLE_PREMIUM_SHORT_CODE;
                    } else {
                        event = EVENT_CONFIRM_SEND_TO_PREMIUM_SHORT_CODE;
                    }
                    sendMessage(obtainMessage(event, tracker));
                    return false;   // wait for user confirmation
            }
        }
    }

    /**
     * Deny sending an SMS if the outgoing queue limit is reached. Used when the message
     * must be confirmed by the user due to excessive usage or potential premium SMS detected.
     * @param tracker the SmsTracker for the message to send
     * @return true if the message was denied; false to continue with send confirmation
     */
    private boolean denyIfQueueLimitReached(SmsTracker tracker) {
        if (mPendingTrackerCount >= MO_MSG_QUEUE_LIMIT) {
            // Deny sending message when the queue limit is reached.
            try {
                if (tracker.mSentIntent != null) {
                    tracker.mSentIntent.send(RESULT_ERROR_LIMIT_EXCEEDED);
                }
            } catch (CanceledException ex) {
                Rlog.e(TAG, "failed to send back RESULT_ERROR_LIMIT_EXCEEDED");
            }
            return true;
        }
        mPendingTrackerCount++;
        return false;
    }

    /**
     * Returns the label for the specified app package name.
     * @param appPackage the package name of the app requesting to send an SMS
     * @return the label for the specified app, or the package name if getApplicationInfo() fails
     */
    private CharSequence getAppLabel(String appPackage) {
        PackageManager pm = mContext.getPackageManager();
        try {
            ApplicationInfo appInfo = pm.getApplicationInfo(appPackage, 0);
            return appInfo.loadLabel(pm);
        } catch (PackageManager.NameNotFoundException e) {
            Rlog.e(TAG, "PackageManager Name Not Found for package " + appPackage);
            return appPackage;  // fall back to package name if we can't get app label
        }
    }

    /**
     * Post an alert when SMS needs confirmation due to excessive usage.
     * @param tracker an SmsTracker for the current message.
     */
    protected void handleReachSentLimit(SmsTracker tracker) {
        if (denyIfQueueLimitReached(tracker)) {
            return;     // queue limit reached; error was returned to caller
        }

        CharSequence appLabel = getAppLabel(tracker.mAppInfo.packageName);
        Resources r = Resources.getSystem();
        Spanned messageText = Html.fromHtml(r.getString(R.string.sms_control_message, appLabel));

        ConfirmDialogListener listener = new ConfirmDialogListener(tracker, null);

        AlertDialog d = new AlertDialog.Builder(mContext)
                .setTitle(R.string.sms_control_title)
                .setIcon(R.drawable.stat_sys_warning)
                .setMessage(messageText)
                .setPositiveButton(r.getString(R.string.sms_control_yes), listener)
                .setNegativeButton(r.getString(R.string.sms_control_no), listener)
                .setOnCancelListener(listener)
                .create();

        d.getWindow().setType(WindowManager.LayoutParams.TYPE_SYSTEM_ALERT);
        d.show();
    }

    /**
     * Post an alert for user confirmation when sending to a potential short code.
     * @param isPremium true if the destination is known to be a premium short code
     * @param tracker the SmsTracker for the current message.
     */
    protected void handleConfirmShortCode(boolean isPremium, SmsTracker tracker) {
        if (denyIfQueueLimitReached(tracker)) {
            return;     // queue limit reached; error was returned to caller
        }

        int detailsId;
        if (isPremium) {
            detailsId = R.string.sms_premium_short_code_details;
        } else {
            detailsId = R.string.sms_short_code_details;
        }

        CharSequence appLabel = getAppLabel(tracker.mAppInfo.packageName);
        Resources r = Resources.getSystem();
        Spanned messageText = Html.fromHtml(r.getString(R.string.sms_short_code_confirm_message,
                appLabel, tracker.mDestAddress));

        LayoutInflater inflater = (LayoutInflater) mContext.getSystemService(
                Context.LAYOUT_INFLATER_SERVICE);
        View layout = inflater.inflate(R.layout.sms_short_code_confirmation_dialog, null);

        ConfirmDialogListener listener = new ConfirmDialogListener(tracker,
                (TextView)layout.findViewById(R.id.sms_short_code_remember_undo_instruction));


        TextView messageView = (TextView) layout.findViewById(R.id.sms_short_code_confirm_message);
        messageView.setText(messageText);

        ViewGroup detailsLayout = (ViewGroup) layout.findViewById(
                R.id.sms_short_code_detail_layout);
        TextView detailsView = (TextView) detailsLayout.findViewById(
                R.id.sms_short_code_detail_message);
        detailsView.setText(detailsId);

        CheckBox rememberChoice = (CheckBox) layout.findViewById(
                R.id.sms_short_code_remember_choice_checkbox);
        rememberChoice.setOnCheckedChangeListener(listener);

        AlertDialog d = new AlertDialog.Builder(mContext)
                .setView(layout)
                .setPositiveButton(r.getString(R.string.sms_short_code_confirm_allow), listener)
                .setNegativeButton(r.getString(R.string.sms_short_code_confirm_deny), listener)
                .setOnCancelListener(listener)
                .create();

        d.getWindow().setType(WindowManager.LayoutParams.TYPE_SYSTEM_ALERT);
        d.show();

        listener.setPositiveButton(d.getButton(DialogInterface.BUTTON_POSITIVE));
        listener.setNegativeButton(d.getButton(DialogInterface.BUTTON_NEGATIVE));
    }

    /**
     * Returns the premium SMS permission for the specified package. If the package has never
     * been seen before, the default {@link SmsUsageMonitor#PREMIUM_SMS_PERMISSION_ASK_USER}
     * will be returned.
     * @param packageName the name of the package to query permission
     * @return one of {@link SmsUsageMonitor#PREMIUM_SMS_PERMISSION_UNKNOWN},
     *  {@link SmsUsageMonitor#PREMIUM_SMS_PERMISSION_ASK_USER},
     *  {@link SmsUsageMonitor#PREMIUM_SMS_PERMISSION_NEVER_ALLOW}, or
     *  {@link SmsUsageMonitor#PREMIUM_SMS_PERMISSION_ALWAYS_ALLOW}
     */
    public int getPremiumSmsPermission(String packageName) {
        return mUsageMonitor.getPremiumSmsPermission(packageName);
    }

    /**
     * Sets the premium SMS permission for the specified package and save the value asynchronously
     * to persistent storage.
     * @param packageName the name of the package to set permission
     * @param permission one of {@link SmsUsageMonitor#PREMIUM_SMS_PERMISSION_ASK_USER},
     *  {@link SmsUsageMonitor#PREMIUM_SMS_PERMISSION_NEVER_ALLOW}, or
     *  {@link SmsUsageMonitor#PREMIUM_SMS_PERMISSION_ALWAYS_ALLOW}
     */
    public void setPremiumSmsPermission(String packageName, int permission) {
        mUsageMonitor.setPremiumSmsPermission(packageName, permission);
    }

    // MTK-START
    protected static String getAppNameByIntent(PendingIntent intent) {
        Resources r = Resources.getSystem();
        return (intent != null) ? intent.getTargetPackage()
            : "Resource unusable";//r.getString(R.string.sms_control_default_app_name);
    }
    // MTK-END

    /**
     * Send the message along to the radio.
     *
     * @param tracker holds the SMS message to send
     */
    protected abstract void sendSms(SmsTracker tracker);

    /**
     * Retry the message along to the radio.
     *
     * @param tracker holds the SMS message to send
     */
    public void sendRetrySms(SmsTracker tracker) {
        // re-routing to ImsSMSDispatcher
        if (mImsSMSDispatcher != null) {
            mImsSMSDispatcher.sendRetrySms(tracker);
        } else {
            Rlog.e(TAG, mImsSMSDispatcher + " is null. Retry failed");
        }
    }

    /**
     * Send the multi-part SMS based on multipart Sms tracker
     *
     * @param tracker holds the multipart Sms tracker ready to be sent
     */
    private void sendMultipartSms(SmsTracker tracker) {
        ArrayList<String> parts;
        ArrayList<PendingIntent> sentIntents;
        ArrayList<PendingIntent> deliveryIntents;

        HashMap<String, Object> map = tracker.mData;

        String destinationAddress = (String) map.get("destination");
        String scAddress = (String) map.get("scaddress");

        parts = (ArrayList<String>) map.get("parts");
        sentIntents = (ArrayList<PendingIntent>) map.get("sentIntents");
        deliveryIntents = (ArrayList<PendingIntent>) map.get("deliveryIntents");

        // check if in service
        int ss = mPhone.getServiceState().getState();
        // if sms over IMS is not supported on data and voice is not available...
        if (!isIms() && ss != ServiceState.STATE_IN_SERVICE) {
            for (int i = 0, count = parts.size(); i < count; i++) {
                PendingIntent sentIntent = null;
                if (sentIntents != null && sentIntents.size() > i) {
                    sentIntent = sentIntents.get(i);
                }
                handleNotInService(ss, sentIntent);
            }
            return;
        }

        sendMultipartText(destinationAddress, scAddress, parts, sentIntents, deliveryIntents);
    }

    /**
     * Keeps track of an SMS that has been sent to the RIL, until it has
     * successfully been sent, or we're done trying.
     */
    protected static final class SmsTracker {
        // fields need to be public for derived SmsDispatchers
        public final HashMap<String, Object> mData;
        public int mRetryCount;
        public int mImsRetry; // nonzero indicates initial message was sent over Ims
        public int mMessageRef;
        String mFormat;

        public final PendingIntent mSentIntent;
        public final PendingIntent mDeliveryIntent;

        public final PackageInfo mAppInfo;
        public final String mDestAddress;

        private long mTimestamp = System.currentTimeMillis();
        private Uri mSentMessageUri; // Uri of persisted message if we wrote one

        private SmsTracker(HashMap<String, Object> data, PendingIntent sentIntent,
                PendingIntent deliveryIntent, PackageInfo appInfo, String destAddr, String format) {
            mData = data;
            mSentIntent = sentIntent;
            mDeliveryIntent = deliveryIntent;
            mRetryCount = 0;
            mAppInfo = appInfo;
            mDestAddress = destAddr;
            mFormat = format;
            mImsRetry = 0;
            mMessageRef = 0;
        }

        /**
         * Returns whether this tracker holds a multi-part SMS.
         * @return true if the tracker holds a multi-part SMS; false otherwise
         */
        boolean isMultipart() {
            return mData.containsKey("parts");
        }

        /**
         * Persist this as a sent message
         */
        void writeSentMessage(Context context) {
            String text = (String)mData.get("text");
            if (text != null) {
                boolean deliveryReport = (mDeliveryIntent != null);
                // Using invalid threadId 0 here. When the message is inserted into the db, the
                // provider looks up the threadId based on the recipient(s).
                mSentMessageUri = Sms.addMessageToUri(context.getContentResolver(),
                        Telephony.Sms.Sent.CONTENT_URI,
                        mDestAddress,
                        text /*body*/,
                        null /*subject*/,
                        mTimestamp /*date*/,
                        true /*read*/,
                        deliveryReport /*deliveryReport*/,
                        0 /*threadId*/);
            }
        }

        /**
         * Update the status of this message if we persisted it
         */
        public void updateSentMessageStatus(Context context, int status) {
            if (mSentMessageUri != null) {
                // If we wrote this message in writeSentMessage, update it now
                ContentValues values = new ContentValues(1);
                values.put(Sms.STATUS, status);
                SqliteWrapper.update(context, context.getContentResolver(),
                        mSentMessageUri, values, null, null);
            }
        }
    }

    protected SmsTracker getSmsTracker(HashMap<String, Object> data, PendingIntent sentIntent,
            PendingIntent deliveryIntent, String format) {
        // Get calling app package name via UID from Binder call
        PackageManager pm = mContext.getPackageManager();
        String[] packageNames = pm.getPackagesForUid(Binder.getCallingUid());

        // Get package info via packagemanager
        PackageInfo appInfo = null;
        if (packageNames != null && packageNames.length > 0) {
            try {
                // XXX this is lossy- apps can share a UID
                // MTK-START
                /* Because it may have multiple apks use the same uid, ex. Mms.apk and omacp.apk, we need to
                 * exactly find the correct calling apk. We should use running process to check the correct
                 * apk. If we could not find the process via pid, this apk may be killed. We will use the
                 * default behavior, find the first package name via uid.
                */
                String packageName = getPackageNameViaProcessId(packageNames);
                if (packageName != null) {
                    packageNames[0] = packageName;
                }
                Rlog.d(TAG, "SmsTrackerFactory and get the package name via process id: " + packageNames[0]);
                // MTK-END
                appInfo = pm.getPackageInfo(packageNames[0], PackageManager.GET_SIGNATURES);
            } catch (PackageManager.NameNotFoundException e) {
                // error will be logged in sendRawPdu
            }
        }
        // Strip non-digits from destination phone number before checking for short codes
        // and before displaying the number to the user if confirmation is required.
        String destAddr = PhoneNumberUtils.extractNetworkPortion((String) data.get("destAddr"));
        return new SmsTracker(data, sentIntent, deliveryIntent, appInfo, destAddr, format);
    }

    protected HashMap<String, Object> getSmsTrackerMap(String destAddr, String scAddr,
            String text, SmsMessageBase.SubmitPduBase pdu) {
        HashMap<String, Object> map = new HashMap<String, Object>();
        map.put("destAddr", destAddr);
        map.put("scAddr", scAddr);
        map.put("text", text);
        map.put("smsc", pdu.encodedScAddress);
        map.put("pdu", pdu.encodedMessage);
        return map;
    }

    protected HashMap<String, Object> getSmsTrackerMap(String destAddr, String scAddr,
            int destPort, byte[] data, SmsMessageBase.SubmitPduBase pdu) {
        HashMap<String, Object> map = new HashMap<String, Object>();
        map.put("destAddr", destAddr);
        map.put("scAddr", scAddr);
        map.put("destPort", destPort);
        map.put("data", data);
        map.put("smsc", pdu.encodedScAddress);
        map.put("pdu", pdu.encodedMessage);
        return map;
    }

    /**
     * Dialog listener for SMS confirmation dialog.
     */
    private final class ConfirmDialogListener
            implements DialogInterface.OnClickListener, DialogInterface.OnCancelListener,
            CompoundButton.OnCheckedChangeListener {

        private final SmsTracker mTracker;
        private Button mPositiveButton;
        private Button mNegativeButton;
        private boolean mRememberChoice;    // default is unchecked
        private final TextView mRememberUndoInstruction;

        ConfirmDialogListener(SmsTracker tracker, TextView textView) {
            mTracker = tracker;
            mRememberUndoInstruction = textView;
        }

        void setPositiveButton(Button button) {
            mPositiveButton = button;
        }

        void setNegativeButton(Button button) {
            mNegativeButton = button;
        }

        @Override
        public void onClick(DialogInterface dialog, int which) {
            // Always set the SMS permission so that Settings will show a permission setting
            // for the app (it won't be shown until after the app tries to send to a short code).
            int newSmsPermission = SmsUsageMonitor.PREMIUM_SMS_PERMISSION_ASK_USER;

            if (which == DialogInterface.BUTTON_POSITIVE) {
                Rlog.d(TAG, "CONFIRM sending SMS");
                // XXX this is lossy- apps can have more than one signature
                //EventLog.writeEvent(EventLogTags.EXP_DET_SMS_SENT_BY_USER,
                //                    mTracker.mAppInfo.applicationInfo == null ?
                //                    -1 : mTracker.mAppInfo.applicationInfo.uid);
                sendMessage(obtainMessage(EVENT_SEND_CONFIRMED_SMS, mTracker));
                if (mRememberChoice) {
                    newSmsPermission = SmsUsageMonitor.PREMIUM_SMS_PERMISSION_ALWAYS_ALLOW;
                }
            } else if (which == DialogInterface.BUTTON_NEGATIVE) {
                Rlog.d(TAG, "DENY sending SMS");
                // XXX this is lossy- apps can have more than one signature
                //EventLog.writeEvent(EventLogTags.EXP_DET_SMS_DENIED_BY_USER,
                //                    mTracker.mAppInfo.applicationInfo == null ?
                //                    -1 :  mTracker.mAppInfo.applicationInfo.uid);
                sendMessage(obtainMessage(EVENT_STOP_SENDING, mTracker));
                if (mRememberChoice) {
                    newSmsPermission = SmsUsageMonitor.PREMIUM_SMS_PERMISSION_NEVER_ALLOW;
                }
            }
            setPremiumSmsPermission(mTracker.mAppInfo.packageName, newSmsPermission);

            // MTK-START [ALPS000xxxxx] MTK code port to ICS added by mtk80589 in 2011.11.16
            if(FeatureOption.MTK_SMS_FILTER_SUPPORT == true) {
                while(sConcatMsgCount > 0 && mPendingTrackerCount > 0) {
                    if (which == DialogInterface.BUTTON_POSITIVE) {
                        Rlog.d(TAG, "[NQ continue sending " + sConcatMsgCount);
                        sendMessage(obtainMessage(EVENT_SEND_CONFIRMED_SMS, mTracker));
                    } else if (which == DialogInterface.BUTTON_NEGATIVE) {
                        Rlog.d(TAG, "[NQ stop sending " + sConcatMsgCount);
                    }

                    sConcatMsgCount -= 1;
                } // end while(sConcatMsgCount > 0 && mPendingTrackerCount > 0)
            }
            // MTK-END [ALPS000xxxxx] MTK code port to ICS added by mtk80589 in 2011.11.16
        }

        @Override
        public void onCancel(DialogInterface dialog) {
            Rlog.d(TAG, "dialog dismissed: don't send SMS");
            sendMessage(obtainMessage(EVENT_STOP_SENDING, mTracker));
        }

        @Override
        public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
            Rlog.d(TAG, "remember this choice: " + isChecked);
            mRememberChoice = isChecked;
            if (isChecked) {
                mPositiveButton.setText(R.string.sms_short_code_confirm_always_allow);
                mNegativeButton.setText(R.string.sms_short_code_confirm_never_allow);
                if (mRememberUndoInstruction != null) {
                    mRememberUndoInstruction.
                            setText(R.string.sms_short_code_remember_undo_instruction);
                    mRememberUndoInstruction.setPadding(0,0,0,32);
                }
            } else {
                mPositiveButton.setText(R.string.sms_short_code_confirm_allow);
                mNegativeButton.setText(R.string.sms_short_code_confirm_deny);
                if (mRememberUndoInstruction != null) {
                    mRememberUndoInstruction.setText("");
                    mRememberUndoInstruction.setPadding(0,0,0,0);
                }
            }
        }
    }

    public boolean isIms() {
        if (mImsSMSDispatcher != null) {
            return mImsSMSDispatcher.isIms();
        } else {
            Rlog.e(TAG, mImsSMSDispatcher + " is null");
            return false;
        }
    }

    public String getImsSmsFormat() {
        if (mImsSMSDispatcher != null) {
            return mImsSMSDispatcher.getImsSmsFormat();
        } else {
            Rlog.e(TAG, mImsSMSDispatcher + " is null");
            return null;
        }
    }

    // MTK-START
    /**
     * create wake lock for sms ready intent.
     */
    private void createWakelock() {
        PowerManager pm = (PowerManager)mContext.getSystemService(Context.POWER_SERVICE);
        mWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "SMSDispatcher");
        mWakeLock.setReferenceCounted(true);
    }

    /**
     * Called when IccSmsInterfaceManager update SIM card fail due to SIM_FULL.
     */
    protected void handleIccFull() {
        // default not handle anything
        return;
    }

    /**
     * Called when a CB activation result is received.
     *
     * @param ar AsyncResult passed into the message handler.
     */
    protected void handleQueryCbActivation(AsyncResult ar) {
        Rlog.e(TAG, "didn't support cellBoradcast in the CDMA phone");
    }

    // MTK_OPTR_PROTECT_START
    private BroadcastReceiver mDMLockReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            Rlog.d(TAG, "[DM-Lock receive lock/unlock intent");
            if(intent.getAction().equals("com.mediatek.dm.LAWMO_LOCK")) {
                Rlog.d(TAG, "[DM-Lock DM is locked now");
                isDmLock = true;
            } else if(intent.getAction().equals("com.mediatek.dm.LAWMO_UNLOCK")) {
                Rlog.d(TAG, "[DM-Lock DM is unlocked now");
                isDmLock = false;
            }
        }
    };
    // MTK_OPTR_PROTECT_END

    protected boolean checkSmsWithNqFilter(String address, String text, PendingIntent sentIntent) {
        String pkgName = getAppNameByIntent(sentIntent);
        //String appName = mContext.getPackageManager().getApplicationLabel(mContext.getApplicationInfo()).toString();
        String appName = null;
        try {
            ApplicationInfo appInfo = mContext.getPackageManager().getApplicationInfo(pkgName, 0);
            appName = mContext.getPackageManager().getApplicationLabel(appInfo).toString();
        } catch (PackageManager.NameNotFoundException e) {
            appName = "Resource unusable";// Resources.getSystem().getString(R.string.sms_control_default_app_name);
        }

        Rlog.d(TAG, "[NQ address = " + address + ", text = " + text
                + ", pkgName = " + pkgName + ", appName = " + appName);

        boolean isDeductedMessage = false;
        try {
            isDeductedMessage = NqSmsFilter.getInstance(mContext).nqSmsFilter(address, text, pkgName, appName);
        } catch (Exception e) {
            Rlog.d(TAG, "[Nq Exception is thrown when call NqSmsFilter");
        }

        return isDeductedMessage;
    }

    private void handleDeductedMessage(SmsTracker tracker) {
        if (mPendingTrackerCount >= MO_MSG_QUEUE_LIMIT) {
            // Deny the sending when the queue limit is reached.
            try {
                tracker.mSentIntent.send(RESULT_ERROR_LIMIT_EXCEEDED);
            } catch (CanceledException ex) {
                Rlog.e(TAG, "failed to send back RESULT_ERROR_LIMIT_EXCEEDED");
            }
            return;
        }

        Resources r = Resources.getSystem();

        ConfirmDialogListener listener = new ConfirmDialogListener(tracker, null);

        AlertDialog dlg = new AlertDialog.Builder(mContext)
            .setTitle(r.getString(com.mediatek.internal.R.string.nq_sms_filter_title))
            .setMessage(r.getString(com.mediatek.internal.R.string.nq_sms_filter_message))
            .setPositiveButton(r.getString(com.mediatek.internal.R.string.nq_sms_filter_yes), listener)
            .setNegativeButton(r.getString(com.mediatek.internal.R.string.nq_sms_filter_no), listener)
            .create();

        dlg.getWindow().setType(WindowManager.LayoutParams.TYPE_SYSTEM_ALERT);
        dlg.setCancelable(false);
        dlg.show();

        //sendMessageDelayed ( obtainMessage(EVENT_REDUCTED_MESSAGE_TIMEOUT, tracker),
        //        DEFAULT_SMS_TIMEOUT);
    }

    /**
     * Send a data based SMS to a specific application port.
     *
     * @param destAddr the address to send the message to
     * @param scAddr is the service center address or null to use
     *  the current default SMSC
     * @param destPort the port to deliver the message to
     * @param originalPort the port to deliver the message from
     * @param data the body of the message to send
     * @param sentIntent if not NULL this <code>PendingIntent</code> is
     *  broadcast when the message is successfully sent, or failed.
     *  The result code will be <code>Activity.RESULT_OK<code> for success,
     *  or one of these errors:<br>
     *  <code>RESULT_ERROR_GENERIC_FAILURE</code><br>
     *  <code>RESULT_ERROR_RADIO_OFF</code><br>
     *  <code>RESULT_ERROR_NULL_PDU</code><br>
     *  For <code>RESULT_ERROR_GENERIC_FAILURE</code> the sentIntent may include
     *  the extra "errorCode" containing a radio technology specific value,
     *  generally only useful for troubleshooting.<br>
     *  The per-application based SMS control checks sentIntent. If sentIntent
     *  is NULL the caller will be checked against all unknown applications,
     *  which cause smaller number of SMS to be sent in checking period.
     * @param deliveryIntent if not NULL this <code>PendingIntent</code> is
     *  broadcast when the message is delivered to the recipient.  The
     *  raw pdu of the status report is in the extended data ("pdu").
     */
    protected void sendData(String destAddr, String scAddr, int destPort, int originalPort,
            byte[] data, PendingIntent sentIntent, PendingIntent deliveryIntent) {
    }

    /**
     * Send a multi-part data based SMS.
     *
     * @param destinationAddress the address to send the message to
     * @param scAddress is the service center address or null to use
     *   the current default SMSC
     * @param data an <code>ArrayList</code> of strings that, in order,
     *   comprise the original message
     * @param destPort the port to deliver the message to
     * @param data an array of data messages in order,
     *   comprise the original message
     * @param sentIntents if not null, an <code>ArrayList</code> of
     *   <code>PendingIntent</code>s (one for each message part) that is
     *   broadcast when the corresponding message part has been sent.
     *   The result code will be <code>Activity.RESULT_OK<code> for success,
     *   or one of these errors:
     *   <code>RESULT_ERROR_GENERIC_FAILURE</code>
     *   <code>RESULT_ERROR_RADIO_OFF</code>
     *   <code>RESULT_ERROR_NULL_PDU</code>.
     * @param deliveryIntents if not null, an <code>ArrayList</code> of
     *   <code>PendingIntent</code>s (one for each message part) that is
     *   broadcast when the corresponding message part has been delivered
     *   to the recipient.  The raw pdu of the status report is in the
     *   extended data ("pdu").
     */
    protected void sendMultipartData(
            String destAddr, String scAddr, int destPort,
            ArrayList<SmsRawData> data, ArrayList<PendingIntent> sentIntents,
            ArrayList<PendingIntent> deliveryIntents) {
    }

    /**
     * Send a text based SMS to a specified application port.
     *
     * @param destAddr the address to send the message to
     * @param scAddr is the service center address or null to use
     *  the current default SMSC
     * @param text the body of the message to send
     * @param destPort the port to deliver the message to
     * @param sentIntent if not NULL this <code>PendingIntent</code> is
     *  broadcast when the message is sucessfully sent, or failed.
     *  The result code will be <code>Activity.RESULT_OK<code> for success,
     *  or one of these errors:<br>
     *  <code>RESULT_ERROR_GENERIC_FAILURE</code><br>
     *  <code>RESULT_ERROR_RADIO_OFF</code><br>
     *  <code>RESULT_ERROR_NULL_PDU</code><br>
     *  For <code>RESULT_ERROR_GENERIC_FAILURE</code> the sentIntent may include
     *  the extra "errorCode" containing a radio technology specific value,
     *  generally only useful for troubleshooting.<br>
     *  The per-application based SMS control checks sentIntent. If sentIntent
     *  is NULL the caller will be checked against all unknown applications,
     *  which cause smaller number of SMS to be sent in checking period.
     * @param deliveryIntent if not NULL this <code>PendingIntent</code> is
     *  broadcast when the message is delivered to the recipient.  The
     *  raw pdu of the status report is in the extended data ("pdu").
     */
    protected void sendText(String destAddr, String scAddr, String text,
            int destPort,PendingIntent sentIntent, PendingIntent deliveryIntent) {
    }

    /**
     * Send a multi-part text based SMS to a specified application port.
     *
     * @param destAddr the address to send the message to
     * @param scAddr is the service center address or null to use
     *   the current default SMSC
     * @param parts an <code>ArrayList</code> of strings that, in order,
     *   comprise the original message
     * @param destPort the port to deliver the message to
     * @param sentIntents if not null, an <code>ArrayList</code> of
     *   <code>PendingIntent</code>s (one for each message part) that is
     *   broadcast when the corresponding message part has been sent.
     *   The result code will be <code>Activity.RESULT_OK<code> for success,
     *   or one of these errors:
     *   <code>RESULT_ERROR_GENERIC_FAILURE</code>
     *   <code>RESULT_ERROR_RADIO_OFF</code>
     *   <code>RESULT_ERROR_NULL_PDU</code>.
     *  The per-application based SMS control checks sentIntent. If sentIntent
     *  is NULL the caller will be checked against all unknown applicaitons,
     *  which cause smaller number of SMS to be sent in checking period.
     * @param deliveryIntents if not null, an <code>ArrayList</code> of
     *   <code>PendingIntent</code>s (one for each message part) that is
     *   broadcast when the corresponding message part has been delivered
     *   to the recipient.  The raw pdu of the status report is in the
     *   extended data ("pdu").
     */
    protected void sendMultipartText(String destAddr, String scAddr,
            ArrayList<String> parts, int destPort, ArrayList<PendingIntent> sentIntents,
            ArrayList<PendingIntent> deliveryIntents) {
    }

    /**
     * Copy a text SMS to the ICC.
     *
     * @param scAddress Service center address
     * @param address   Destination address or original address
     * @param text      List of message text
     * @param status    message status (STATUS_ON_ICC_READ, STATUS_ON_ICC_UNREAD,
     *                  STATUS_ON_ICC_SENT, STATUS_ON_ICC_UNSENT)
     * @param timestamp Timestamp when service center receive the message
     * @return success or not
     *
     */
    public int copyTextMessageToIccCard(
            String scAddress, String address, List<String> text,
            int status, long timestamp) {
        return 0;
    }

    private void notifySmsReady(boolean isReady) {
        // broadcast SMS_STATE_CHANGED_ACTION intent
        Intent intent = new Intent(Intents.SMS_STATE_CHANGED_ACTION);
        intent.putExtra("ready", isReady);
        intent.putExtra(PhoneConstants.GEMINI_SIM_ID_KEY, mSimId);
        mWakeLock.acquire(WAKE_LOCK_TIMEOUT);
        mContext.sendBroadcast(intent);
    }

    /**
     * Set the memory storage status of the SMS
     * This function is used for FTA test only
     *
     * @param status false for storage full, true for storage available
     *
     */
    protected void setSmsMemoryStatus(boolean status) {
        if (status != mStorageAvailable) {
            mStorageAvailable = status;
            mCi.reportSmsMemoryStatus(status, null);
        }
    }

    protected boolean isSmsReady() {
        return mSmsReady;
    }

    // MTK-START [ALPS00094531] Orange feature SMS Encoding Type Setting by mtk80589 in 2011.11.22
    /**
     * Send an SMS with specified encoding type.
     *
     * @param destAddr the address to send the message to
     * @param scAddr the SMSC to send the message through, or NULL for the
     *  default SMSC
     * @param text the body of the message to send
     * @param encodingType the encoding type of content of message(GSM 7-bit, Unicode or Automatic)
     * @param sentIntent if not NULL this <code>PendingIntent</code> is
     *  broadcast when the message is sucessfully sent, or failed.
     *  The result code will be <code>Activity.RESULT_OK<code> for success,
     *  or one of these errors:<br>
     *  <code>RESULT_ERROR_GENERIC_FAILURE</code><br>
     *  <code>RESULT_ERROR_RADIO_OFF</code><br>
     *  <code>RESULT_ERROR_NULL_PDU</code><br>
     *  For <code>RESULT_ERROR_GENERIC_FAILURE</code> the sentIntent may include
     *  the extra "errorCode" containing a radio technology specific value,
     *  generally only useful for troubleshooting.<br>
     *  The per-application based SMS control checks sentIntent. If sentIntent
     *  is NULL the caller will be checked against all unknown applications,
     *  which cause smaller number of SMS to be sent in checking period.
     * @param deliveryIntent if not NULL this <code>PendingIntent</code> is
     *  broadcast when the message is delivered to the recipient.  The
     *  raw pdu of the status report is in the extended data ("pdu").
     */
    protected void sendTextWithEncodingType(
            String destAddr,
            String scAddr,
            String text,
            int encodingType,
            PendingIntent sentIntent,
            PendingIntent deliveryIntent) {
    }

    /**
     * Send a multi-part text based SMS with specified encoding type.
     *
     * @param destAddr the address to send the message to
     * @param scAddr is the service center address or null to use
     *   the current default SMSC
     * @param parts an <code>ArrayList</code> of strings that, in order,
     *   comprise the original message
     * @param encodingType the encoding type of content of message(GSM 7-bit, Unicode or Automatic)
     * @param sentIntents if not null, an <code>ArrayList</code> of
     *   <code>PendingIntent</code>s (one for each message part) that is
     *   broadcast when the corresponding message part has been sent.
     *   The result code will be <code>Activity.RESULT_OK<code> for success,
     *   or one of these errors:
     *   <code>RESULT_ERROR_GENERIC_FAILURE</code>
     *   <code>RESULT_ERROR_RADIO_OFF</code>
     *   <code>RESULT_ERROR_NULL_PDU</code>.
     * @param deliveryIntents if not null, an <code>ArrayList</code> of
     *   <code>PendingIntent</code>s (one for each message part) that is
     *   broadcast when the corresponding message part has been delivered
     *   to the recipient.  The raw pdu of the status report is in the
     *   extended data ("pdu").
     */
    protected void sendMultipartTextWithEncodingType(
            String destAddr,
            String scAddr,
            ArrayList<String> parts,
            int encodingType,
            ArrayList<PendingIntent> sentIntents,
            ArrayList<PendingIntent> deliveryIntents) {
    }
    // MTK-END [ALPS00094531] Orange feature SMS Encoding Type Setting by mtk80589 in 2011.11.22

    /**
     * Send an SMS with specified encoding type.
     *
     * @param destAddr the address to send the message to
     * @param scAddr the SMSC to send the message through, or NULL for the
     *  default SMSC
     * @param text the body of the message to send
     * @param extraParams extra parameters, such as validity period, encoding type
     * @param sentIntent if not NULL this <code>PendingIntent</code> is
     *  broadcast when the message is sucessfully sent, or failed.
     * @param deliveryIntent if not NULL this <code>PendingIntent</code> is
     *  broadcast when the message is delivered to the recipient.  The
     *  raw pdu of the status report is in the extended data ("pdu").
     */
    public void sendTextWithExtraParams(
            String destAddr,
            String scAddr,
            String text,
            Bundle extraParams,
            PendingIntent sentIntent,
            PendingIntent deliveryIntent) {
    }

    /**
     * Send a multi-part text based SMS with specified encoding type.
     *
     * @param destAddr the address to send the message to
     * @param scAddr is the service center address or null to use
     *   the current default SMSC
     * @param parts an <code>ArrayList</code> of strings that, in order,
     *   comprise the original message
     * @param extraParams extra parameters, such as validity period, encoding type
     * @param sentIntents if not null, an <code>ArrayList</code> of
     *   <code>PendingIntent</code>s (one for each message part) that is
     *   broadcast when the corresponding message part has been sent.
     * @param deliveryIntents if not null, an <code>ArrayList</code> of
     *   <code>PendingIntent</code>s (one for each message part) that is
     *   broadcast when the corresponding message part has been delivered
     *   to the recipient.  The raw pdu of the status report is in the
     *   extended data ("pdu").
     */
    public void sendMultipartTextWithExtraParams(
            String destAddr,
            String scAddr,
            ArrayList<String> parts,
            Bundle extraParams,
            ArrayList<PendingIntent> sentIntents,
            ArrayList<PendingIntent> deliveryIntents) {
    }

    protected SmsMessage createMessageFromSubmitPdu(byte[] smsc, byte[] tpdu) {
        return null;
    }

    /**
     * Because it may have multiple apks use the same uid, ex. Mms.apk and omacp.apk, we need to
     * exactly find the correct calling apk. We should use running process to check the correct
     * apk. If we could not find the process via pid, this apk may be killed. We will use the
     * default behavior, find the first package name via uid.
     *
     * @param packageNames package names query from user id
     * @return null if package names length is zero or could not match the process id; 
     *         package name match the process id
     */
    private String getPackageNameViaProcessId(String[] packageNames) {
        String packageName = null;

        if (packageNames.length == 1) {
            packageName = packageNames[0];
        } else if (packageNames.length > 1) {
            int callingPid = Binder.getCallingPid();

            ActivityManager am = (ActivityManager) mContext.getSystemService(Context.ACTIVITY_SERVICE);
            List processList = am.getRunningAppProcesses();
            Iterator index = processList.iterator();
            while (index.hasNext()) {
                ActivityManager.RunningAppProcessInfo processInfo = (ActivityManager.RunningAppProcessInfo)(index.next());
                if (callingPid == processInfo.pid) {
                    packageName = processInfo.processName;
                    break;
                }
            }
        }

        return packageName;
    }
    // MTK-END
}
