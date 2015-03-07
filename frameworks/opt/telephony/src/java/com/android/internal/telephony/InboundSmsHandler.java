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

import android.app.Activity;
import android.app.AppOpsManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.SQLException;
import android.net.Uri;
import android.os.AsyncResult;
import android.os.Build;
import android.os.Message;
import android.os.PowerManager;
import android.os.SystemProperties;
import android.provider.Telephony;
import android.provider.Telephony.Sms.Intents;
import android.telephony.Rlog;
import android.telephony.SmsMessage;
import android.telephony.TelephonyManager;

import com.android.internal.util.HexDump;
import com.android.internal.util.State;
import com.android.internal.util.StateMachine;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;

import static android.telephony.TelephonyManager.PHONE_TYPE_CDMA;

// MTK-START
import com.android.internal.telephony.PhoneConstants;
import android.os.Bundle;
import com.mediatek.common.MediatekClassFactory;
import com.mediatek.common.featureoption.FeatureOption;

// Concatenated feature
import com.mediatek.common.sms.IConcatenatedSmsFwkExt;
import com.mediatek.common.sms.TimerRecord;

// Mobile manager service feature
import com.mediatek.common.mom.IMobileManager;
import com.mediatek.common.mom.IMobileManagerService;
import android.os.ServiceManager;
import android.content.pm.PackageManager;
import android.os.RemoteException;
import android.content.IntentFilter;
// Mobile manager service for phone privacy lock
import com.mediatek.common.ppl.IPplSmsFilter;

// Tablet multi-user feature
import com.mediatek.common.telephony.IOnlyOwnerSimSupport;
// MTK-END

/**
 * This class broadcasts incoming SMS messages to interested apps after storing them in
 * the SmsProvider "raw" table and ACKing them to the SMSC. After each message has been
 * broadcast, its parts are removed from the raw table. If the device crashes after ACKing
 * but before the broadcast completes, the pending messages will be rebroadcast on the next boot.
 *
 * <p>The state machine starts in {@link IdleState} state. When the {@link SMSDispatcher} receives a
 * new SMS from the radio, it calls {@link #dispatchNormalMessage},
 * which sends a message to the state machine, causing the wakelock to be acquired in
 * {@link #haltedProcessMessage}, which transitions to {@link DeliveringState} state, where the message
 * is saved to the raw table, then acknowledged via the {@link SMSDispatcher} which called us.
 *
 * <p>After saving the SMS, if the message is complete (either single-part or the final segment
 * of a multi-part SMS), we broadcast the completed PDUs as an ordered broadcast, then transition to
 * {@link WaitingState} state to wait for the broadcast to complete. When the local
 * {@link BroadcastReceiver} is called with the result, it sends {@link #EVENT_BROADCAST_COMPLETE}
 * to the state machine, causing us to either broadcast the next pending message (if one has
 * arrived while waiting for the broadcast to complete), or to transition back to the halted state
 * after all messages are processed. Then the wakelock is released and we wait for the next SMS.
 */
public abstract class InboundSmsHandler extends StateMachine {
    protected static final boolean DBG = true;
    private static final boolean VDBG = false;  // STOPSHIP if true, logs user data

    /** Query projection for checking for duplicate message segments. */
    private static final String[] PDU_PROJECTION = {
            "pdu"
    };

    /** Query projection for combining concatenated message segments. */
    private static final String[] PDU_SEQUENCE_PORT_PROJECTION = {
            "pdu",
            "sequence",
            "destination_port"
    };

    static final int PDU_COLUMN = 0;
    static final int SEQUENCE_COLUMN = 1;
    static final int DESTINATION_PORT_COLUMN = 2;
    static final int DATE_COLUMN = 3;
    static final int REFERENCE_NUMBER_COLUMN = 4;
    static final int COUNT_COLUMN = 5;
    static final int ADDRESS_COLUMN = 6;
    static final int ID_COLUMN = 7;

    static final String SELECT_BY_ID = "_id=?";
    // MTK-START
    static final String SELECT_BY_REFERENCE = "address=? AND reference_number=? AND count=? AND sim_id=?";
    // VIA-START
    static final String SELECT_BY_ADDRESS = "address=?";
    // VIA-END
    // MTK-END

    /** New SMS received as an AsyncResult. */
    public static final int EVENT_NEW_SMS = 1;

    /** Message type containing a {@link InboundSmsTracker} ready to broadcast to listeners. */
    static final int EVENT_BROADCAST_SMS = 2;

    /** Message from resultReceiver notifying {@link WaitingState} of a completed broadcast. */
    static final int EVENT_BROADCAST_COMPLETE = 3;

    /** Sent on exit from {@link WaitingState} to return to idle after sending all broadcasts. */
    static final int EVENT_RETURN_TO_IDLE = 4;

    /** Release wakelock after a short timeout when returning to idle state. */
    static final int EVENT_RELEASE_WAKELOCK = 5;

    /** Sent by {@link SmsBroadcastUndelivered} after cleaning the raw table. */
    static final int EVENT_START_ACCEPTING_SMS = 6;

    /** Update phone object */
    static final int EVENT_UPDATE_PHONE_OBJECT = 7;

    /** Wakelock release delay when returning to idle state. */
    private static final int WAKELOCK_TIMEOUT = 3000;

    /** URI for raw table of SMS provider. */
    private static final Uri sRawUri = Uri.withAppendedPath(Telephony.Sms.CONTENT_URI, "raw");

    protected final Context mContext;
    private final ContentResolver mResolver;

    /** Special handler for WAP push messages. */
    private final WapPushOverSms mWapPush;

    /** Wake lock to ensure device stays awake while dispatching the SMS intents. */
    final PowerManager.WakeLock mWakeLock;

    /** DefaultState throws an exception or logs an error for unhandled message types. */
    final DefaultState mDefaultState = new DefaultState();

    /** Startup state. Waiting for {@link SmsBroadcastUndelivered} to complete. */
    final StartupState mStartupState = new StartupState();

    /** Idle state. Waiting for messages to process. */
    final IdleState mIdleState = new IdleState();

    /** Delivering state. Saves the PDU in the raw table and acknowledges to SMSC. */
    final DeliveringState mDeliveringState = new DeliveringState();

    /** Broadcasting state. Waits for current broadcast to complete before delivering next. */
    final WaitingState mWaitingState = new WaitingState();

    /** Helper class to check whether storage is available for incoming messages. */
    // MTK-START, change as public for GsmSmsDispatcher to use
    public SmsStorageMonitor mStorageMonitor;
    // MTK-END

    private final boolean mSmsReceiveDisabled;

    protected PhoneBase mPhone;

    protected CellBroadcastHandler mCellBroadcastHandler;


    // MTK-START
    /** Identify the SIM */
    protected int mSimId = PhoneConstants.GEMINI_SIM_1;

    /** DM operator files */
    protected DMOperatorFile mDMOperatorFile = null;

    /** sms database raw table locker */
    protected Object mRawLock = new Object();

    /** Concatenated SMS handler. A timer to show concatenated SMS or CT special requirement */
    IConcatenatedSmsFwkExt mConcatenatedSmsFwkExt = null;

    /** Mobile manager service feature. To process a MT sms and check if it could deliver to app */
    private IMobileManagerService mMobileManagerService = null;

    /** Mobile manager service for phone privacy lock */
    private IPplSmsFilter mPplSmsFilter = null;

    /** Tablet Multi-user feature */
    private IOnlyOwnerSimSupport mOnlyOwnerSimSupport = null;
    // MTK-END

    /**
     * Create a new SMS broadcast helper.
     * @param name the class name for logging
     * @param context the context of the phone app
     * @param storageMonitor the SmsStorageMonitor to check for storage availability
     */
    protected InboundSmsHandler(String name, Context context, SmsStorageMonitor storageMonitor,
            PhoneBase phone, CellBroadcastHandler cellBroadcastHandler) {
        super(name);

        mContext = context;
        mStorageMonitor = storageMonitor;
        mPhone = phone;
        mCellBroadcastHandler = cellBroadcastHandler;
        mResolver = context.getContentResolver();
        mWapPush = new WapPushOverSms(context);

        boolean smsCapable = mContext.getResources().getBoolean(
                com.android.internal.R.bool.config_sms_capable);
        mSmsReceiveDisabled = !SystemProperties.getBoolean(
                TelephonyProperties.PROPERTY_SMS_RECEIVE, smsCapable);

        PowerManager pm = (PowerManager) mContext.getSystemService(Context.POWER_SERVICE);
        mWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, name);
        mWakeLock.acquire();    // wake lock released after we enter idle state

        addState(mDefaultState);
        addState(mStartupState, mDefaultState);
        addState(mIdleState, mDefaultState);
        addState(mDeliveringState, mDefaultState);
            addState(mWaitingState, mDeliveringState);

        setInitialState(mStartupState);
        if (DBG) log("created InboundSmsHandler");

        // MTK-START
        mSimId = phone.getMySimId();
        // initialize DM operator info
        log("[DM initialize DM xml");
        mDMOperatorFile = DMOperatorFile.getInstance();
        mDMOperatorFile.initFromRes(mContext);
        mDMOperatorFile.dump();

        // Create concatenated class to handle MTK concatenated behavior
        try {
            mConcatenatedSmsFwkExt = MediatekClassFactory.createInstance(
                    IConcatenatedSmsFwkExt.class,
                    mContext,
                    mSimId);
            if (mConcatenatedSmsFwkExt != null) {
                String actualClassName = mConcatenatedSmsFwkExt.getClass().getName();
                log("initial IConcatenatedSmsFwkExt done, actual class name is " + actualClassName);
            } else {
                log("FAIL! intial mConcatenatedSmsFwkExt");
            }
        } catch (RuntimeException e) {
            loge("FAIL! No IConcatenatedSmsFwkExt");
        }

        // Get the mobile manager service
        if (FeatureOption.MTK_MOBILE_MANAGEMENT) {
            if (mMobileManagerService == null) {
                mMobileManagerService = IMobileManagerService.Stub.asInterface(
                    ServiceManager.getService(Context.MOBILE_SERVICE));
            }
        }

        try {
            mPplSmsFilter = MediatekClassFactory.createInstance(IPplSmsFilter.class, mContext);
            if (mPplSmsFilter != null) {
                String actualClassName = mPplSmsFilter.getClass().getName();
                log("initial mPplSmsFilter done, actual class name is " + actualClassName);
            } else {
                log("FAIL! intial mPplSmsFilter");
            }
        } catch (RuntimeException e) {
            loge("FAIL! No IPplSmsFilter");
        }

        if (FeatureOption.MTK_MOBILE_MANAGEMENT || FeatureOption.MTK_PRIVACY_PROTECTION_LOCK) {
            // Register the Moms intent receiver;
            IntentFilter filter = new IntentFilter();
            filter.addAction(Intents.MOMS_SMS_RECEIVED_ACTION);
            mContext.registerReceiver(mMomsReceiver, filter);
        }

        // Get the multi-user plug-in class
        try {
            mOnlyOwnerSimSupport = MediatekClassFactory.createInstance(IOnlyOwnerSimSupport.class,mContext, true);
            if (mOnlyOwnerSimSupport != null) {
                String actualClassName = mOnlyOwnerSimSupport.getClass().getName();
                log("initial mOnlyOwnerSimSupport done, actual class name is " + actualClassName);
            } else {
                loge("FAIL! intial mOnlyOwnerSimSupport");
            }
        } catch (RuntimeException e) {
            loge("FAIL! No IOnlyOwnerSimSupport");
        }
        // MTK-END
    }

    /**
     * Tell the state machine to quit after processing all messages.
     */
    public void dispose() {
        quit();
    }

    /**
     * Update the phone object when it changes.
     */
    public void updatePhoneObject(PhoneBase phone) {
        sendMessage(EVENT_UPDATE_PHONE_OBJECT, phone);
    }

    /**
     * Dispose of the WAP push object and release the wakelock.
     */
    @Override
    protected void onQuitting() {
        mWapPush.dispose();

        while (mWakeLock.isHeld()) {
            mWakeLock.release();
        }

        // MTK-START
        // De-register the receiver from context
        if (FeatureOption.MTK_MOBILE_MANAGEMENT || FeatureOption.MTK_PRIVACY_PROTECTION_LOCK) {
            mContext.unregisterReceiver(mMomsReceiver);
        }
        // MTK-END
    }

    /**
     * This parent state throws an exception (for debug builds) or prints an error for unhandled
     * message types.
     */
    class DefaultState extends State {
        @Override
        public boolean processMessage(Message msg) {
            switch (msg.what) {
                case EVENT_UPDATE_PHONE_OBJECT: {
                    onUpdatePhoneObject((PhoneBase) msg.obj);
                    break;
                }
                default: {
                    String errorText = "processMessage: unhandled message type " + msg.what;
                    if (Build.IS_DEBUGGABLE) {
                        throw new RuntimeException(errorText);
                    } else {
                        loge(errorText);
                    }
                    break;
                }
            }
            return HANDLED;
        }
    }

    /**
     * The Startup state waits for {@link SmsBroadcastUndelivered} to process the raw table and
     * notify the state machine to broadcast any complete PDUs that might not have been broadcast.
     */
    class StartupState extends State {
        @Override
        public boolean processMessage(Message msg) {
            switch (msg.what) {
                case EVENT_NEW_SMS:
                case EVENT_BROADCAST_SMS:
                // MTK-START
                case IConcatenatedSmsFwkExt.EVENT_DISPATCH_CONCATE_SMS_SEGMENTS:
                // MTK-END
                    deferMessage(msg);
                    return HANDLED;

                case EVENT_START_ACCEPTING_SMS:
                    transitionTo(mIdleState);
                    return HANDLED;

                case EVENT_BROADCAST_COMPLETE:
                case EVENT_RETURN_TO_IDLE:
                case EVENT_RELEASE_WAKELOCK:
                default:
                    // let DefaultState handle these unexpected message types
                    return NOT_HANDLED;
            }
        }
    }

    /**
     * In the idle state the wakelock is released until a new SM arrives, then we transition
     * to Delivering mode to handle it, acquiring the wakelock on exit.
     */
    class IdleState extends State {
        @Override
        public void enter() {
            if (DBG) log("entering Idle state");
            sendMessageDelayed(EVENT_RELEASE_WAKELOCK, WAKELOCK_TIMEOUT);
        }

        @Override
        public void exit() {
            mWakeLock.acquire();
            if (DBG) log("acquired wakelock, leaving Idle state");
        }

        @Override
        public boolean processMessage(Message msg) {
            if (DBG) log("Idle state processing message type " + msg.what);
            switch (msg.what) {
                case EVENT_NEW_SMS:
                case EVENT_BROADCAST_SMS:
                // MTK-START
                case IConcatenatedSmsFwkExt.EVENT_DISPATCH_CONCATE_SMS_SEGMENTS:
                // MTK-END
                    deferMessage(msg);
                    transitionTo(mDeliveringState);
                    return HANDLED;

                case EVENT_RELEASE_WAKELOCK:
                    mWakeLock.release();
                    if (DBG) {
                        if (mWakeLock.isHeld()) {
                            // this is okay as long as we call release() for every acquire()
                            log("mWakeLock is still held after release");
                        } else {
                            log("mWakeLock released");
                        }
                    }
                    return HANDLED;

                case EVENT_RETURN_TO_IDLE:
                    // already in idle state; ignore
                    return HANDLED;

                case EVENT_BROADCAST_COMPLETE:
                case EVENT_START_ACCEPTING_SMS:
                default:
                    // let DefaultState handle these unexpected message types
                    return NOT_HANDLED;
            }
        }
    }

    /**
     * In the delivering state, the inbound SMS is processed and stored in the raw table.
     * The message is acknowledged before we exit this state. If there is a message to broadcast,
     * transition to {@link WaitingState} state to send the ordered broadcast and wait for the
     * results. When all messages have been processed, the halting state will release the wakelock.
     */
    class DeliveringState extends State {
        @Override
        public void enter() {
            if (DBG) log("entering Delivering state");
        }

        @Override
        public void exit() {
            if (DBG) log("leaving Delivering state");
        }

        @Override
        public boolean processMessage(Message msg) {
            switch (msg.what) {
                case EVENT_NEW_SMS:
                    // handle new SMS from RIL
                    handleNewSms((AsyncResult) msg.obj);
                    sendMessage(EVENT_RETURN_TO_IDLE);
                    return HANDLED;

                case EVENT_BROADCAST_SMS:
                    // if any broadcasts were sent, transition to waiting state
                    if (processMessagePart((InboundSmsTracker) msg.obj)) {
                        transitionTo(mWaitingState);
                    }
                    return HANDLED;

                case EVENT_RETURN_TO_IDLE:
                    // return to idle after processing all other messages
                    transitionTo(mIdleState);
                    return HANDLED;

                case EVENT_RELEASE_WAKELOCK:
                    mWakeLock.release();    // decrement wakelock from previous entry to Idle
                    if (!mWakeLock.isHeld()) {
                        // wakelock should still be held until 3 seconds after we enter Idle
                        loge("mWakeLock released while delivering/broadcasting!");
                    }
                    return HANDLED;

                // MTK-START
                case IConcatenatedSmsFwkExt.EVENT_DISPATCH_CONCATE_SMS_SEGMENTS:
                    if (dispatchConcateSmsParts((TimerRecord) msg.obj)) {
                        transitionTo(mWaitingState);
                    }
                    return HANDLED;
                // MTK-END

                // we shouldn't get this message type in this state, log error and halt.
                case EVENT_BROADCAST_COMPLETE:
                case EVENT_START_ACCEPTING_SMS:
                default:
                    // let DefaultState handle these unexpected message types
                    return NOT_HANDLED;
            }
        }
    }

    /**
     * The waiting state delegates handling of new SMS to parent {@link DeliveringState}, but
     * defers handling of the {@link #EVENT_BROADCAST_SMS} phase until after the current
     * result receiver sends {@link #EVENT_BROADCAST_COMPLETE}. Before transitioning to
     * {@link DeliveringState}, {@link #EVENT_RETURN_TO_IDLE} is sent to transition to
     * {@link IdleState} after any deferred {@link #EVENT_BROADCAST_SMS} messages are handled.
     */
    class WaitingState extends State {
        @Override
        public boolean processMessage(Message msg) {
            switch (msg.what) {
                case EVENT_BROADCAST_SMS:
                // MTK-START
                case IConcatenatedSmsFwkExt.EVENT_DISPATCH_CONCATE_SMS_SEGMENTS:
                // MTK-END
                    // defer until the current broadcast completes
                    deferMessage(msg);
                    return HANDLED;

                case EVENT_BROADCAST_COMPLETE:
                    // return to idle after handling all deferred messages
                    sendMessage(EVENT_RETURN_TO_IDLE);
                    transitionTo(mDeliveringState);
                    return HANDLED;

                case EVENT_RETURN_TO_IDLE:
                    // not ready to return to idle; ignore
                    return HANDLED;

                default:
                    // parent state handles the other message types
                    return NOT_HANDLED;
            }
        }
    }

    void handleNewSms(AsyncResult ar) {
        if (ar.exception != null) {
            loge("Exception processing incoming SMS: " + ar.exception);
            return;
        }

        int result;
        try {
            SmsMessage sms = (SmsMessage) ar.result;
            result = dispatchMessage(sms.mWrappedSmsMessage);
        } catch (RuntimeException ex) {
            loge("Exception dispatching message", ex);
            result = Intents.RESULT_SMS_GENERIC_ERROR;
        }

        // RESULT_OK means that the SMS will be acknowledged by special handling,
        // e.g. for SMS-PP data download. Any other result, we should ack here.
        if (result != Activity.RESULT_OK) {
            boolean handled = (result == Intents.RESULT_SMS_HANDLED);
            notifyAndAcknowledgeLastIncomingSms(handled, result, null);
        }
    }

    /**
     * Process an SMS message from the RIL, calling subclass methods to handle 3GPP and
     * 3GPP2-specific message types.
     *
     * @param smsb the SmsMessageBase object from the RIL
     * @return a result code from {@link android.provider.Telephony.Sms.Intents},
     *  or {@link Activity#RESULT_OK} for delayed acknowledgment to SMSC
     */
    public int dispatchMessage(SmsMessageBase smsb) {
        // If sms is null, there was a parsing error.
        if (smsb == null) {
            loge("dispatchSmsMessage: message is null");
            return Intents.RESULT_SMS_GENERIC_ERROR;
        }

        if (mSmsReceiveDisabled) {
            // Device doesn't support receiving SMS,
            log("Received short message on device which doesn't support "
                    + "receiving SMS. Ignored.");
            return Intents.RESULT_SMS_HANDLED;
        }

        return dispatchMessageRadioSpecific(smsb);
    }

    /**
     * Process voicemail notification, SMS-PP data download, CDMA CMAS, CDMA WAP push, and other
     * 3GPP/3GPP2-specific messages. Regular SMS messages are handled by calling the shared
     * {@link #dispatchNormalMessage} from this class.
     *
     * @param smsb the SmsMessageBase object from the RIL
     * @return a result code from {@link android.provider.Telephony.Sms.Intents},
     *  or {@link Activity#RESULT_OK} for delayed acknowledgment to SMSC
     */
    protected abstract int dispatchMessageRadioSpecific(SmsMessageBase smsb);

    /**
     * Send an acknowledge message to the SMSC.
     * @param success indicates that last message was successfully received.
     * @param result result code indicating any error
     * @param response callback message sent when operation completes.
     */
    protected abstract void acknowledgeLastIncomingSms(boolean success,
            int result, Message response);

    /**
     * Called when the phone changes the default method updates mPhone
     * mStorageMonitor and mCellBroadcastHandler.updatePhoneObject.
     * Override if different or other behavior is desired.
     *
     * @param phone
     */
    protected void onUpdatePhoneObject(PhoneBase phone) {
        mPhone = phone;
        mStorageMonitor = mPhone.mSmsStorageMonitor;
        log("onUpdatePhoneObject: phone=" + mPhone.getClass().getSimpleName());
    }

    /**
     * Notify interested apps if the framework has rejected an incoming SMS,
     * and send an acknowledge message to the network.
     * @param success indicates that last message was successfully received.
     * @param result result code indicating any error
     * @param response callback message sent when operation completes.
     */
    void notifyAndAcknowledgeLastIncomingSms(boolean success,
            int result, Message response) {
        if (!success) {
            // broadcast SMS_REJECTED_ACTION intent
            Intent intent = new Intent(Intents.SMS_REJECTED_ACTION);
            intent.putExtra("result", result);
            mContext.sendBroadcast(intent, android.Manifest.permission.RECEIVE_SMS);
        }
        acknowledgeLastIncomingSms(success, result, response);
    }

    /**
     * Return true if this handler is for 3GPP2 messages; false for 3GPP format.
     * @return true for the 3GPP2 handler; false for the 3GPP handler
     */
    protected abstract boolean is3gpp2();

    /**
     * Dispatch a normal incoming SMS. This is called from {@link #dispatchMessageRadioSpecific}
     * if no format-specific handling was required. Saves the PDU to the SMS provider raw table,
     * creates an {@link InboundSmsTracker}, then sends it to the state machine as an
     * {@link #EVENT_BROADCAST_SMS}. Returns {@link Intents#RESULT_SMS_HANDLED} or an error value.
     *
     * @param sms the message to dispatch
     * @return {@link Intents#RESULT_SMS_HANDLED} if the message was accepted, or an error status
     */
    protected int dispatchNormalMessage(SmsMessageBase sms) {
        SmsHeader smsHeader = sms.getUserDataHeader();
        InboundSmsTracker tracker;

        if ((smsHeader == null) || (smsHeader.concatRef == null)) {
            // Message is not concatenated.
            int destPort = -1;
            if (smsHeader != null && smsHeader.portAddrs != null) {
                // The message was sent to a port.
                destPort = smsHeader.portAddrs.destPort;
                if (DBG) log("destination port: " + destPort);
            }

            tracker = new InboundSmsTracker(sms.getPdu(), sms.getTimestampMillis(), destPort,
                    is3gpp2(), false);
        } else {
            // Create a tracker for this message segment.
            SmsHeader.ConcatRef concatRef = smsHeader.concatRef;
            SmsHeader.PortAddrs portAddrs = smsHeader.portAddrs;
            int destPort = (portAddrs != null ? portAddrs.destPort : -1);

            // MTK-START
            // Add sim id
            tracker = new InboundSmsTracker(sms.getPdu(), sms.getTimestampMillis(), destPort,
                    is3gpp2(), sms.getOriginatingAddress(), concatRef.refNumber,
                    concatRef.seqNumber, concatRef.msgCount, false, mSimId);
            // MTK-END
        }

        if (VDBG) log("created tracker: " + tracker);
        return addTrackerToRawTableAndSendMessage(tracker);
    }

    /**
     * Helper to add the tracker to the raw table and then send a message to broadcast it, if
     * successful. Returns the SMS intent status to return to the SMSC.
     * @param tracker the tracker to save to the raw table and then deliver
     * @return {@link Intents#RESULT_SMS_HANDLED} or {@link Intents#RESULT_SMS_GENERIC_ERROR}
     * or {@link Intents#RESULT_SMS_DUPLICATED}
     */
    protected int addTrackerToRawTableAndSendMessage(InboundSmsTracker tracker) {
        switch(addTrackerToRawTable(tracker)) {
        case Intents.RESULT_SMS_HANDLED:
            sendMessage(EVENT_BROADCAST_SMS, tracker);
            return Intents.RESULT_SMS_HANDLED;

        case Intents.RESULT_SMS_DUPLICATED:
            return Intents.RESULT_SMS_HANDLED;

        case Intents.RESULT_SMS_GENERIC_ERROR:
        default:
            return Intents.RESULT_SMS_GENERIC_ERROR;
        }
    }

    /**
     * Process the inbound SMS segment. If the message is complete, send it as an ordered
     * broadcast to interested receivers and return true. If the message is a segment of an
     * incomplete multi-part SMS, return false.
     * @param tracker the tracker containing the message segment to process
     * @return true if an ordered broadcast was sent; false if waiting for more message segments
     */
    boolean processMessagePart(InboundSmsTracker tracker) {
        int messageCount = tracker.getMessageCount();
        byte[][] pdus;
        int destPort = tracker.getDestPort();

        if (messageCount == 1) {
            // single-part message
            pdus = new byte[][]{tracker.getPdu()};
        } else {

            // MTK-START
            // To lock the raw table of sms database
            synchronized (mRawLock) {
            // MTK-END
                // multi-part message
                Cursor cursor = null;
                try {
                    // used by several query selection arguments
                    String address = tracker.getAddress();
                    String refNumber = Integer.toString(tracker.getReferenceNumber());
                    String count = Integer.toString(tracker.getMessageCount());
                    // MTK-START
                    String simId = Integer.toString(mSimId);
                    // MTK-END

                    // query for all segments and broadcast message if we have all the parts
                    // MTK-START
                    // Add sim id as a query arguments
                    String[] whereArgs = {address, refNumber, count, simId};
                    // MTK-END
                    // VIA-START
                    // CDMA wap push
                    String where = SELECT_BY_REFERENCE;
                    if (tracker.getIndexOffset() == 0) {
                        // cdma wap push has diffrence reference_number for multi-segment push
                        where = "address=?";
                        whereArgs = new String[] {address};
                    }
                    cursor = mResolver.query(sRawUri, PDU_SEQUENCE_PORT_PROJECTION,
                            where, whereArgs, null);
                    // VIA-END

                    int cursorCount = cursor.getCount();
                    if (cursorCount < messageCount) {
                        // Wait for the other message parts to arrive. It's also possible for the last
                        // segment to arrive before processing the EVENT_BROADCAST_SMS for one of the
                        // earlier segments. In that case, the broadcast will be sent as soon as all
                        // segments are in the table, and any later EVENT_BROADCAST_SMS messages will
                        // get a row count of 0 and return.

                        // MTK-START
                        // Refresh the timer if receive another new concatenated segments but not finish
                        if(tracker.getIndexOffset() == 1 && tracker.getDestPort() == -1) {
                            log("ConcatenatedSmsFwkExt: refresh timer, ref = " + tracker.getReferenceNumber());
                            TimerRecord record = mConcatenatedSmsFwkExt.queryTimerRecord(
                                                        tracker.getAddress(),
                                                        tracker.getReferenceNumber(),
                                                        tracker.getMessageCount());
                            if(record == null) {
                                log("ConcatenatedSmsFwkExt: fail to get TimerRecord to refresh timer");
                            } else {
                                mConcatenatedSmsFwkExt.refreshTimer(getHandler(), record);
                            }
                        }
                        // MTK-END
                        return false;
                    }

                    if(tracker.getIndexOffset() == 1 && tracker.getDestPort() == -1) {
                        // cancel the timer, because all segments are in place
                        log("ConcatenatedSmsFwkExt: cancel timer, ref = " + tracker.getReferenceNumber());
                        TimerRecord record = mConcatenatedSmsFwkExt.queryTimerRecord(
                                                    tracker.getAddress(),
                                                    tracker.getReferenceNumber(),
                                                    tracker.getMessageCount());
                        if(record == null) {
                            log("ConcatenatedSmsFwkExt: fail to get TimerRecord to cancel timer");
                        } else {
                            mConcatenatedSmsFwkExt.cancelTimer(getHandler(), record);
                        }
                    }

                    // All the parts are in place, deal with them
                    pdus = new byte[messageCount][];
                    while (cursor.moveToNext()) {
                        // subtract offset to convert sequence to 0-based array index
                        int index = cursor.getInt(SEQUENCE_COLUMN) - tracker.getIndexOffset();

                        pdus[index] = HexDump.hexStringToByteArray(cursor.getString(PDU_COLUMN));

                        // Read the destination port from the first segment (needed for CDMA WAP PDU).
                        // It's not a bad idea to prefer the port from the first segment in other cases.
                        if (index == 0 && !cursor.isNull(DESTINATION_PORT_COLUMN)) {
                            int port = cursor.getInt(DESTINATION_PORT_COLUMN);
                            // strip format flags and convert to real port number, or -1
                            port = InboundSmsTracker.getRealDestPort(port);
                            if (port != -1) {
                                destPort = port;
                            }
                        }
                    }
                } catch (SQLException e) {
                    loge("Can't access multipart SMS database", e);
                    return false;
                } finally {
                    if (cursor != null) {
                        cursor.close();
                    }
                }
            }
        }

        BroadcastReceiver resultReceiver = new SmsBroadcastReceiver(tracker);

        if (destPort == SmsHeader.PORT_WAP_PUSH) {
            // Build up the data stream
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            for (byte[] pdu : pdus) {
                // 3GPP needs to extract the User Data from the PDU; 3GPP2 has already done this
                if (!tracker.is3gpp2()) {
                    SmsMessage msg = SmsMessage.createFromPdu(pdu, SmsConstants.FORMAT_3GPP);
                    // MTK-START
                    if (msg != null) {
                        pdu = msg.getUserData();
                    }
                    // MTK-END
                }
                output.write(pdu, 0, pdu.length);
            }

            // MTK-START
            int result;
            // Put the extra information on bundle
            if (FeatureOption.MTK_WAPPUSH_SUPPORT) {
                log("dispatch wap push pdu with addr & sc addr");
                SmsMessage sms = SmsMessage.createFromPdu(pdus[0], tracker.getFormat());
                Bundle bundle = new Bundle();
                if (sms != null) {
                    bundle.putString(Telephony.WapPush.ADDR, sms.getOriginatingAddress());
                    String sca = sms.getServiceCenterAddress();
                    if (sca == null) {
                        /* null for app is not a item, it needs to transfer to empty string */
                        sca = "";
                    }
                    bundle.putString(Telephony.WapPush.SERVICE_ADDR, sca);
                }

                result = mWapPush.dispatchWapPdu(output.toByteArray(), resultReceiver, this, bundle);
            } else {
                //int result = mWapPush.dispatchWapPdu(output.toByteArray(), resultReceiver, this);
                log("dispatch wap push pdu");
                result = mWapPush.dispatchWapPdu(output.toByteArray(), resultReceiver, this);
            }
            // MTK-END

            if (DBG) log("dispatchWapPdu() returned " + result);
            // result is Activity.RESULT_OK if an ordered broadcast was sent
            return (result == Activity.RESULT_OK);
        }

        Intent intent;
        if (destPort == -1) {
            intent = new Intent(Intents.SMS_DELIVER_ACTION);

            // Direct the intent to only the default SMS app. If we can't find a default SMS app
            // then sent it to all broadcast receivers.
            ComponentName componentName = SmsApplication.getDefaultSmsApplication(mContext, true);
            if (componentName != null) {
                // Deliver SMS message only to this receiver
                intent.setComponent(componentName);
                log("Delivering SMS to: " + componentName.getPackageName() +
                        " " + componentName.getClassName());
            }

            // MTK-START
            // To check if needs to add upload flag to app
            int uploadFlag = IConcatenatedSmsFwkExt.UPLOAD_FLAG_NEW;

            if (tracker.getIndexOffset() == 1 && tracker.getDestPort() == -1) {
                SmsMessage msg = SmsMessage.createFromPdu(pdus[0], tracker.getFormat());
                if(msg != null) {
                    SmsHeader udh = msg.getUserDataHeader();
                    if(udh != null && udh.concatRef != null) {
                        TimerRecord tr = new TimerRecord(msg.getOriginatingAddress(),
                                                         udh.concatRef.refNumber,
                                                         udh.concatRef.msgCount);
                        uploadFlag = mConcatenatedSmsFwkExt.getUploadFlag(tr);
                    }
                }
                log("uploadFlag=" + uploadFlag);
            }
            if(uploadFlag == IConcatenatedSmsFwkExt.UPLOAD_FLAG_UPDATE) {
                log("dispatch all pdus with upload flag");
                intent.putExtra(IConcatenatedSmsFwkExt.UPLOAD_FLAG_TAG, uploadFlag);
            }

            // If mobile manager service feature turns on,
            // change and send to mobile manager service to check permission first
            // Moms -> default sms application -> others
            if (FeatureOption.MTK_MOBILE_MANAGEMENT || FeatureOption.MTK_PRIVACY_PROTECTION_LOCK) {
                // Change action as "android.intent.action.MOMS_SMS_RECEIVED" to let Moms check first
                intent.setAction(Intents.MOMS_SMS_RECEIVED_ACTION);
                intent.setComponent(null);
            }

            // If it is not the current owner, dispatch to multi-user module to cache
            if(mOnlyOwnerSimSupport != null && !mOnlyOwnerSimSupport.isCurrentUserOwner()){
                intent.setAction(mOnlyOwnerSimSupport.MTK_NORMALUSER_SMS_ACTION);
                intent.setComponent(null);
            }
            // MTK-END
        } else {
            // MTK-START
            // DM operator support
            if(mDMOperatorFile.searchMatchOp(tracker.getAddress(), destPort)) {
                log("we receive a DM register SMS");
                intent = new Intent(Intents.DM_REGISTER_SMS_RECEIVED_ACTION);
                intent.putExtra("pdus", pdus);
                dispatchIntent(intent, "android.permission.RECEIVE_DM_REGISTER_SMS", AppOpsManager.OP_RECEIVE_SMS, resultReceiver);
                return true;
            } else if (destPort == 8025 || destPort == 7275 || destPort == 7276) {
                log("=========== Send SMS For A-GPS SUPL NI ========");
                Uri uri = Uri.parse("sms://localhost:" + destPort);
                intent = new Intent(Intents.DATA_SMS_RECEIVED_ACTION, uri);
                intent.putExtra("pdus", pdus);
                intent.putExtra("format", tracker.getFormat());
                dispatchIntent(intent, null,  AppOpsManager.OP_RECEIVE_SMS, resultReceiver);   //for AGPS only
                return true;
            }
            // MTK-END

            Uri uri = Uri.parse("sms://localhost:" + destPort);
            intent = new Intent(Intents.DATA_SMS_RECEIVED_ACTION, uri);
        }

        intent.putExtra("pdus", pdus);
        intent.putExtra("format", tracker.getFormat());
        dispatchIntent(intent, android.Manifest.permission.RECEIVE_SMS,
                AppOpsManager.OP_RECEIVE_SMS, resultReceiver);
        return true;
    }

    /**
     * Dispatch the intent with the specified permission, appOp, and result receiver, using
     * this state machine's handler thread to run the result receiver.
     *
     * @param intent the intent to broadcast
     * @param permission receivers are required to have this permission
     * @param appOp app op that is being performed when dispatching to a receiver
     */
    void dispatchIntent(Intent intent, String permission, int appOp,
            BroadcastReceiver resultReceiver) {
        intent.addFlags(Intent.FLAG_RECEIVER_NO_ABORT);
        // MTK-START
        intent.putExtra("rTime", System.currentTimeMillis());
        intent.putExtra(PhoneConstants.GEMINI_SIM_ID_KEY, mSimId);
        // MTK-END
        mContext.sendOrderedBroadcast(intent, permission, appOp, resultReceiver,
                getHandler(), Activity.RESULT_OK, null, null);
    }

    /**
     * Helper for {@link SmsBroadcastUndelivered} to delete an old message in the raw table.
     */
    void deleteFromRawTable(String deleteWhere, String[] deleteWhereArgs) {
        // MTK-START
        synchronized (mRawLock) {
        // MTK-END
            int rows = mResolver.delete(sRawUri, deleteWhere, deleteWhereArgs);
            if (rows == 0) {
                loge("No rows were deleted from raw table!");
            } else if (DBG) {
                log("Deleted " + rows + " rows from raw table.");
            }
        }
    }

    /**
     * Insert a message PDU into the raw table so we can acknowledge it immediately.
     * If the device crashes before the broadcast to listeners completes, it will be delivered
     * from the raw table on the next device boot. For single-part messages, the deleteWhere
     * and deleteWhereArgs fields of the tracker will be set to delete the correct row after
     * the ordered broadcast completes.
     *
     * @param tracker the tracker to add to the raw table
     * @return true on success; false on failure to write to database
     */
    private int addTrackerToRawTable(InboundSmsTracker tracker) {
        // MTK-START
        // To lock the raw table of sms database
        synchronized (mRawLock) {
        // MTK-END
            if (tracker.getMessageCount() != 1) {
                // check for duplicate message segments
                Cursor cursor = null;
                try {
                    // sequence numbers are 1-based except for CDMA WAP, which is 0-based
                    int sequence = tracker.getSequenceNumber();

                    // convert to strings for query
                    String address = tracker.getAddress();
                    String refNumber = Integer.toString(tracker.getReferenceNumber());
                    String count = Integer.toString(tracker.getMessageCount());

                    String seqNumber = Integer.toString(sequence);
                    // MTK-START
                    String simId = Integer.toString(mSimId);
                    // MTK-END

                    // set the delete selection args for multi-part message
                    // MTK-START, add sim id
                    String[] deleteWhereArgs = {address, refNumber, count, simId};
                    // MTK-END
                    tracker.setDeleteWhere(SELECT_BY_REFERENCE, deleteWhereArgs);

                    // MTK-START
                    // Check for duplicate message segments
                    cursor = mResolver.query(sRawUri, PDU_PROJECTION,
                            "address=? AND reference_number=? AND count=? AND sequence=? AND sim_id=?",
                            new String[] {address, refNumber, count, seqNumber, simId}, null);
                    // MTK-END

                    // moveToNext() returns false if no duplicates were found
                    if (cursor.moveToNext()) {
                        loge("Discarding duplicate message segment, refNumber=" + refNumber
                                + " seqNumber=" + seqNumber);
                        String oldPduString = cursor.getString(PDU_COLUMN);
                        byte[] pdu = tracker.getPdu();
                        byte[] oldPdu = HexDump.hexStringToByteArray(oldPduString);
                        if (!Arrays.equals(oldPdu, tracker.getPdu())) {
                            loge("Warning: dup message segment PDU of length " + pdu.length
                                    + " is different from existing PDU of length " + oldPdu.length);
                        }
                        return Intents.RESULT_SMS_DUPLICATED;   // reject message
                    }
                    cursor.close();
                } catch (SQLException e) {
                    loge("Can't access multipart SMS database", e);
                    return Intents.RESULT_SMS_GENERIC_ERROR;    // reject message
                } finally {
                    if (cursor != null) {
                        cursor.close();
                    }
                }
            }

            ContentValues values = tracker.getContentValues();

            if (VDBG) log("adding content values to raw table: " + values.toString());
            Uri newUri = mResolver.insert(sRawUri, values);
            if (DBG) log("URI of new row -> " + newUri);

            // MTK-START
            // check whether the message is the first segment of one
            // concatenated sms
            boolean isFirstSegment = false;
            if (tracker.getReferenceNumber() != -1) {
                isFirstSegment = mConcatenatedSmsFwkExt.isFirstConcatenatedSegment(
                                                                tracker.getAddress(),
                                                                tracker.getReferenceNumber());
            }
            // Not a CDMA-wap-push && not a data SMS && it is the first segment
            if (tracker.getIndexOffset() == 1 && tracker.getDestPort() == -1 && isFirstSegment == true) {
                log("ConcatenatedSmsFwkExt: the first segment, ref = " + tracker.getReferenceNumber());
                log("ConcatenatedSmsFwkExt: start a new timer");
                TimerRecord record = new TimerRecord(tracker.getAddress(), tracker.getReferenceNumber(), tracker.getMessageCount());
                if(record == null) {
                    log("ConcatenatedSmsFwkExt: fail to new TimerRecord to start timer");
                }
                mConcatenatedSmsFwkExt.startTimer(getHandler(), record);
            }
            // MTK-END

            try {
                long rowId = ContentUris.parseId(newUri);
                if (tracker.getMessageCount() == 1) {
                    // set the delete selection args for single-part message
                    tracker.setDeleteWhere(SELECT_BY_ID, new String[]{Long.toString(rowId)});
                }
                return Intents.RESULT_SMS_HANDLED;
            } catch (Exception e) {
                loge("error parsing URI for new row: " + newUri, e);
                return Intents.RESULT_SMS_GENERIC_ERROR;
            }
        }
    }

    /**
     * Returns whether the default message format for the current radio technology is 3GPP2.
     * @return true if the radio technology uses 3GPP2 format by default, false for 3GPP format
     */
    static boolean isCurrentFormat3gpp2() {
        int activePhone = TelephonyManager.getDefault().getCurrentPhoneType();
        return (PHONE_TYPE_CDMA == activePhone);
    }

    /**
     * Handler for an {@link InboundSmsTracker} broadcast. Deletes PDUs from the raw table and
     * logs the broadcast duration (as an error if the other receivers were especially slow).
     */
    private final class SmsBroadcastReceiver extends BroadcastReceiver {
        private final String mDeleteWhere;
        private final String[] mDeleteWhereArgs;
        private long mBroadcastTimeNano;

        SmsBroadcastReceiver(InboundSmsTracker tracker) {
            mDeleteWhere = tracker.getDeleteWhere();
            mDeleteWhereArgs = tracker.getDeleteWhereArgs();
            mBroadcastTimeNano = System.nanoTime();
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action.equals(Intents.SMS_DELIVER_ACTION)) {
                // Now dispatch the notification only intent
                intent.setAction(Intents.SMS_RECEIVED_ACTION);
                intent.setComponent(null);
                dispatchIntent(intent, android.Manifest.permission.RECEIVE_SMS,
                        AppOpsManager.OP_RECEIVE_SMS, this);
            } else if (action.equals(Intents.WAP_PUSH_DELIVER_ACTION)) {
                // Now dispatch the notification only intent
                intent.setAction(Intents.WAP_PUSH_RECEIVED_ACTION);
                intent.setComponent(null);
                dispatchIntent(intent, android.Manifest.permission.RECEIVE_SMS,
                        AppOpsManager.OP_RECEIVE_SMS, this);
            // MTK-START
            } else if (action.equals(Intents.MOMS_SMS_RECEIVED_ACTION)) {
                // If result code is RESULT_SMS_REJECT_BY_MOMS, it means that Mobile manager service
                // doesn't permits to disptch. Delete it from raw table.
                // Otherwise, dispatch to default sms application first and follow google flow
                int rc = getResultCode();
                if (rc == Intents.RESULT_SMS_REJECT_BY_MOMS) {
                    log("[Moms] Reject by mobile manager service and delete from raw table. Result code:" + rc);
                    deleteFromRawTable(mDeleteWhere, mDeleteWhereArgs);
                    sendMessage(EVENT_BROADCAST_COMPLETE);
                } else {
                    log("[Moms] Permit to dispatch, send to sms default application first. Result code:" + rc);
                    intent.setAction(Intents.SMS_DELIVER_ACTION);
                    // Direct the intent to only the default SMS app. If we can't find a default SMS app
                    // then sent it to all broadcast receivers.
                    ComponentName componentName = SmsApplication.getDefaultSmsApplication(mContext, true);
                    if (componentName != null) {
                        // Deliver SMS message only to this receiver
                        intent.setComponent(componentName);
                        log("Delivering SMS to: " + componentName.getPackageName() +
                                " " + componentName.getClassName());
                    }
                    dispatchIntent(intent, android.Manifest.permission.RECEIVE_SMS,
                            AppOpsManager.OP_RECEIVE_SMS, this);
                }
            // MTK-END
            } else {
                // Now that the intents have been deleted we can clean up the PDU data.
                if (!Intents.DATA_SMS_RECEIVED_ACTION.equals(action)
                        && !Intents.DATA_SMS_RECEIVED_ACTION.equals(action)
                        && !Intents.WAP_PUSH_RECEIVED_ACTION.equals(action)) {
                    loge("unexpected BroadcastReceiver action: " + action);
                }

                int rc = getResultCode();
                if ((rc != Activity.RESULT_OK) && (rc != Intents.RESULT_SMS_HANDLED)) {
                    loge("a broadcast receiver set the result code to " + rc
                            + ", deleting from raw table anyway!");
                } else if (DBG) {
                    log("successful broadcast, deleting from raw table.");
                }

                deleteFromRawTable(mDeleteWhere, mDeleteWhereArgs);
                sendMessage(EVENT_BROADCAST_COMPLETE);

                int durationMillis = (int) ((System.nanoTime() - mBroadcastTimeNano) / 1000000);
                if (durationMillis >= 5000) {
                    loge("Slow ordered broadcast completion time: " + durationMillis + " ms");
                } else if (DBG) {
                    log("ordered broadcast completed in: " + durationMillis + " ms");
                }
            }
        }
    }

    /**
     * Log with debug level.
     * @param s the string to log
     */
    @Override
    protected void log(String s) {
        Rlog.d(getName(), s);
    }

    /**
     * Log with error level.
     * @param s the string to log
     */
    @Override
    protected void loge(String s) {
        Rlog.e(getName(), s);
    }

    /**
     * Log with error level.
     * @param s the string to log
     * @param e is a Throwable which logs additional information.
     */
    @Override
    protected void loge(String s, Throwable e) {
        Rlog.e(getName(), s, e);
    }

    // MTK-START
    protected boolean dispatchConcateSmsParts(TimerRecord record) {
        boolean handled = false;

        log("ConcatenatedSmsFwkExt: receive timeout message");
        if (record == null) {
            log("ConcatenatedSmsFwkExt: null TimerRecord in msg");
            return false;
        }
        log("ConcatenatedSmsFwkExt: timer is expired, dispatch existed segments. refNumber = "
                        + record.refNumber);

        // create null tracker for FSM flow
        InboundSmsTracker smsTracker = new InboundSmsTracker(null, 0, 0, false, false);
        smsTracker.setDeleteWhere(null, null);
        SmsBroadcastReceiver receiver = new SmsBroadcastReceiver(smsTracker);

        synchronized (mRawLock) {
            byte[][] pdus = mConcatenatedSmsFwkExt.queryExistedSegments(record);
            if (pdus != null && pdus.length > 0) {
                int flag = mConcatenatedSmsFwkExt.getUploadFlag(record);
                if(flag == IConcatenatedSmsFwkExt.UPLOAD_FLAG_UPDATE || flag == IConcatenatedSmsFwkExt.UPLOAD_FLAG_NEW) {
                    mConcatenatedSmsFwkExt.setUploadFlag(record);

                    Intent intent = new Intent(Intents.SMS_DELIVER_ACTION);
                    // Direct the intent to only the default SMS app. If we can't find a default SMS app
                    // then sent it to all broadcast receivers.
                    ComponentName componentName = SmsApplication.getDefaultSmsApplication(mContext, true);
                    if (componentName != null) {
                        // Deliver SMS message only to this receiver
                        intent.setComponent(componentName);
                        log("Delivering SMS to: " + componentName.getPackageName() +
                                " " + componentName.getClassName());
                    }

                    // If mobile manager service feature turns on,
                    // change and send to mobile manager service to check permission first
                    // Moms -> default sms application -> others
                    if (FeatureOption.MTK_MOBILE_MANAGEMENT || FeatureOption.MTK_PRIVACY_PROTECTION_LOCK) {
                        // Change action as "android.intent.action.MOMS_SMS_RECEIVED" to let Moms check first
                        intent.setAction(Intents.MOMS_SMS_RECEIVED_ACTION);
                        intent.setComponent(null);
                    }
                    // If it is not the current owner, dispatch to multi-user module to cache
                    if(mOnlyOwnerSimSupport != null && !mOnlyOwnerSimSupport.isCurrentUserOwner()){
                        intent.setAction(mOnlyOwnerSimSupport.MTK_NORMALUSER_SMS_ACTION);
                        intent.setComponent(null);
                    }
                    intent.putExtra("pdus", pdus);
                    if (!is3gpp2()) {
                        intent.putExtra("format", SmsMessage.FORMAT_3GPP);
                    } else {
                        intent.putExtra("format", SmsMessage.FORMAT_3GPP2);
                    }
                    intent.putExtra(IConcatenatedSmsFwkExt.UPLOAD_FLAG_TAG, flag);
                    dispatchIntent(intent, android.Manifest.permission.RECEIVE_SMS,
                                   AppOpsManager.OP_RECEIVE_SMS, receiver);

                    handled = true;
                } else {
                    log("ConcatenatedSmsFwkExt: invalid upload flag");
                }
            } else {
                log("ConcatenatedSmsFwkExt: no pdus to be dispatched");
            }
            // don't delete segments here, we will delete these after 12 hours
            log("ConcatenatedSmsFwkExt: delete segment(s), ref = " + record.refNumber);
            mConcatenatedSmsFwkExt.deleteExistedSegments(record);
        }

        return handled;
    }

    /**
     * Mobile manager service intent handler.
     * To handle the intent that send from sms finite state machine.
     */
    private BroadcastReceiver mMomsReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            log("[Moms] Receive a intent to check permission");

            String intentFormat = intent.getStringExtra("format");

            int trackerId = intent.getIntExtra(PhoneConstants.GEMINI_SIM_ID_KEY, -1);
            log("[Moms] intentFormat ="+intentFormat+", trackerId="+trackerId);

            if (trackerId == mSimId &&
                ((is3gpp2() && (intentFormat.compareTo(SmsConstants.FORMAT_3GPP2) == 0 )) ||
                 (!is3gpp2() && (intentFormat.compareTo(SmsConstants.FORMAT_3GPP) == 0 ))) )
            {
                if (intent.getAction().equals(Intents.MOMS_SMS_RECEIVED_ACTION) &&
                    MomsPermissionCheck(intent) != PackageManager.PERMISSION_GRANTED) {
                    // Not permit to send to rest app
                    setResultCode(Intents.RESULT_SMS_REJECT_BY_MOMS);
                    }
                    else {
                    // Set to result to the resultReceiver
                    // Permit to send to rest app including default sms application
                    setResultCode(Intents.RESULT_SMS_ACCEPT_BY_MOMS);
                }
            }
        }
    };

    /**
     * Mobile manager service check if this MT sms has permission to dispatch
     */
    protected int MomsPermissionCheck(Intent intent) {
        log("[Moms] PermissionCheck");
        int checkResult = PackageManager.PERMISSION_GRANTED;

        if (FeatureOption.MTK_MOBILE_MANAGEMENT || FeatureOption.MTK_PRIVACY_PROTECTION_LOCK) {
            try {
                /* CTA-level3 for phone privacy lock */
                if (FeatureOption.MTK_PRIVACY_PROTECTION_LOCK) {
                    if (checkResult == PackageManager.PERMISSION_GRANTED) {
                        log("[Moms] Phone privacy check start");

                        Bundle pplData = new Bundle();
                        Object[] messages = (Object[]) intent.getExtra("pdus");
                        byte[][] pdus = new byte[messages.length][];
                        for (int i = 0; i < messages.length; i++) {
                            pdus[i] = (byte[]) messages[i];
                        }

                        pplData.putSerializable(mPplSmsFilter.KEY_PDUS, pdus);
                        pplData.putString(mPplSmsFilter.KEY_FORMAT, (String) intent.getExtra("format"));
                        pplData.putInt(mPplSmsFilter.KEY_SIM_ID, mSimId);
                        pplData.putInt(mPplSmsFilter.KEY_SMS_TYPE, 0);

                        boolean pplResult = false;
                        pplResult = mPplSmsFilter.pplFilter(pplData);
                        log("[Moms] Phone privacy check end, Need to filter(result) = " + pplResult);
                        if (pplResult == true) {
                            checkResult = PackageManager.PERMISSION_DENIED;
                        }
                    }
                }

                if (FeatureOption.MTK_MOBILE_MANAGEMENT) {
                    log("[Moms] getInterceptionEnabledSetting = " + mMobileManagerService.getInterceptionEnabledSetting());
                    if (checkResult == PackageManager.PERMISSION_GRANTED &&
                        mMobileManagerService.getInterceptionEnabledSetting()) {
                        checkResult = PackageManager.PERMISSION_DENIED;
                        Bundle params = new Bundle();
                        params.putParcelable(IMobileManager.SMS_MESSAGE_INTENT, intent);
                        params.putInt(IMobileManager.SMS_MESSAGE_SIMID, mSimId);
                        checkResult = mMobileManagerService.triggerManagerApListener
                               (IMobileManager.CONTROLLER_MESSAGE_INTERCEPT, params, PackageManager.PERMISSION_GRANTED);
                        log("[Moms] dispatchPdus, checkResult="+checkResult);
                    }
                }
            }
            catch (RemoteException e) {
                loge("[Moms] Suppressing notification faild!");
            }
        }

        return checkResult;
    }
    // MTK-END
}
