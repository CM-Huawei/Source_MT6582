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

import android.os.AsyncResult;
import android.os.Handler;
import android.os.Message;
import android.os.SystemProperties;
import android.text.TextUtils;
import com.android.internal.telephony.CommandException;

import java.io.FileDescriptor;
import java.io.PrintWriter;


/**
 * {@hide}
 */
public abstract class CallTracker extends Handler {

    /// M: [mtk04070][111118][ALPS00093395]Change to true.
    private static final boolean DBG_POLL = true;

    //***** Constants

    static final int POLL_DELAY_MSEC = 250;

    protected int mPendingOperations;
    protected boolean mNeedsPoll;
    protected Message mLastRelevantPoll;

    public CommandsInterface mCi;


    //***** Events

    protected static final int EVENT_POLL_CALLS_RESULT             = 1;
    protected static final int EVENT_CALL_STATE_CHANGE             = 2;
    protected static final int EVENT_REPOLL_AFTER_DELAY            = 3;
    protected static final int EVENT_OPERATION_COMPLETE            = 4;
    protected static final int EVENT_GET_LAST_CALL_FAIL_CAUSE      = 5;

    protected static final int EVENT_SWITCH_RESULT                 = 8;
    protected static final int EVENT_RADIO_AVAILABLE               = 9;
    protected static final int EVENT_RADIO_NOT_AVAILABLE           = 10;
    protected static final int EVENT_CONFERENCE_RESULT             = 11;
    protected static final int EVENT_SEPARATE_RESULT               = 12;
    protected static final int EVENT_ECT_RESULT                    = 13;
    protected static final int EVENT_EXIT_ECM_RESPONSE_CDMA        = 14;
    protected static final int EVENT_CALL_WAITING_INFO_CDMA        = 15;
    protected static final int EVENT_THREE_WAY_DIAL_L2_RESULT_CDMA = 16;

    /// M: [mtk04070][111118][ALPS00093395]MTK added new events. @{
    protected static final int EVENT_CALL_PROGRESS_INFO            = 17;
    protected static final int EVENT_DIAL_CALL_RESULT              = 18;
    protected static final int EVENT_HANG_UP_RESULT                = 19;
    protected static final int EVENT_INCOMING_CALL_INDICATION      = 20;
    /// @}

    //Add by via for register CDMA call accepted message[begin]
    protected static final int EVENT_CDMA_CALL_ACCEPTED            = 21;
    protected static final int EVENT_CDMA_DIAL_THREEWAY_DELAY      = 22;
    //Add by via for register CDMA call accepted message[end]

    protected static final int EVENT_RADIO_OFF_OR_NOT_AVAILABLE    = 23;
    protected static final int EVENT_CNAP_INDICATION               = 24;
    //Add by via for EXIT EMC when dial threeway[begin]
    protected static final int EVENT_EXIT_ECM_RESPONSE_DIAL_THREEWAY = 25;
    //Add by via for EXIT EMC when dial threeway[end]
    
    protected void pollCallsWhenSafe() {
        mNeedsPoll = true;

        if (checkNoOperationsPending()) {
            mLastRelevantPoll = obtainMessage(EVENT_POLL_CALLS_RESULT);
            mCi.getCurrentCalls(mLastRelevantPoll);
        }
    }

    protected void
    pollCallsAfterDelay() {
        Message msg = obtainMessage();

        msg.what = EVENT_REPOLL_AFTER_DELAY;
        sendMessageDelayed(msg, POLL_DELAY_MSEC);
    }

    protected boolean
    isCommandExceptionRadioNotAvailable(Throwable e) {
        return e != null && e instanceof CommandException
                && ((CommandException)e).getCommandError()
                        == CommandException.Error.RADIO_NOT_AVAILABLE;
    }

    protected abstract void handlePollCalls(AsyncResult ar);

    protected void handleRadioAvailable() {
        pollCallsWhenSafe();
    }

    /**
     * Obtain a complete message that indicates that this operation
     * does not require polling of getCurrentCalls(). However, if other
     * operations that do need getCurrentCalls() are pending or are
     * scheduled while this operation is pending, the invocation
     * of getCurrentCalls() will be postponed until this
     * operation is also complete.
     */
    protected Message
    obtainNoPollCompleteMessage(int what) {
        mPendingOperations++;
        mLastRelevantPoll = null;
        return obtainMessage(what);
    }

    /**
     * @return true if we're idle or there's a call to getCurrentCalls() pending
     * but nothing else
     */
    private boolean
    checkNoOperationsPending() {
        if (DBG_POLL) log("checkNoOperationsPending: pendingOperations=" +
                mPendingOperations);
        return mPendingOperations == 0;
    }

    /**
     * Routine called from dial to check if the number is a test Emergency number
     * and if so remap the number. This allows a short emergency number to be remapped
     * to a regular number for testing how the frameworks handles emergency numbers
     * without actually calling an emergency number.
     *
     * This is not a full test and is not a substitute for testing real emergency
     * numbers but can be useful.
     *
     * To use this feature set a system property ril.test.emergencynumber to a pair of
     * numbers separated by a colon. If the first number matches the number parameter
     * this routine returns the second number. Example:
     *
     * ril.test.emergencynumber=112:1-123-123-45678
     *
     * To test Dial 112 take call then hang up on MO device to enter ECM
     * see RIL#processSolicited RIL_REQUEST_HANGUP_FOREGROUND_RESUME_BACKGROUND
     *
     * @param dialString to test if it should be remapped
     * @return the same number or the remapped number.
     */
    protected String checkForTestEmergencyNumber(String dialString) {
        String testEn = SystemProperties.get("ril.test.emergencynumber");
        if (DBG_POLL) {
            log("checkForTestEmergencyNumber: dialString=" + dialString +
                " testEn=" + testEn);
        }
        if (!TextUtils.isEmpty(testEn)) {
            String values[] = testEn.split(":");
            log("checkForTestEmergencyNumber: values.length=" + values.length);
            if (values.length == 2) {
                if (values[0].equals(
                        android.telephony.PhoneNumberUtils.stripSeparators(dialString))) {
                    mCi.testingEmergencyCall();
                    log("checkForTestEmergencyNumber: remap " +
                            dialString + " to " + values[1]);
                    dialString = values[1];
                }
            }
        }
        return dialString;
    }

    //***** Overridden from Handler
    @Override
    public abstract void handleMessage (Message msg);
    public abstract void registerForVoiceCallStarted(Handler h, int what, Object obj);
    public abstract void unregisterForVoiceCallStarted(Handler h);
    public abstract void registerForVoiceCallEnded(Handler h, int what, Object obj);
    public abstract void unregisterForVoiceCallEnded(Handler h);

    protected abstract void log(String msg);

    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        pw.println("CallTracker:");
        pw.println(" mPendingOperations=" + mPendingOperations);
        pw.println(" mNeedsPoll=" + mNeedsPoll);
        pw.println(" mLastRelevantPoll=" + mLastRelevantPoll);
    }
}
