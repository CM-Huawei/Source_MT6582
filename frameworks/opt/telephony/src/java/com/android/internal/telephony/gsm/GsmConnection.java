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

package com.android.internal.telephony.gsm;
import android.content.Context;
import android.os.AsyncResult;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.PowerManager;
import android.os.Registrant;
import android.os.SystemClock;
import android.telephony.Rlog;
import android.telephony.PhoneNumberUtils;
import android.telephony.ServiceState;
import android.text.TextUtils;

import com.android.internal.telephony.*;
import com.android.internal.telephony.uicc.UiccCardApplication;
import com.android.internal.telephony.uicc.UiccController;
import com.android.internal.telephony.uicc.IccCardApplicationStatus.AppState;

/// M: [ALPS00330882]Easy porting for OP07 - ADAPT test case. @{
import com.mediatek.common.MediatekClassFactory;
import com.mediatek.common.telephony.IGsmConnectionExt;
/// @}

/**
 * {@hide}
 */
public class GsmConnection extends Connection {
    private static final String LOG_TAG = "GsmConnection";
    private static final boolean DBG = true;

    //***** Instance Variables

    GsmCallTracker mOwner;
    GsmCall mParent;

    String mAddress;     // MAY BE NULL!!!
    String mDialString;          // outgoing calls only
    String mPostDialString;      // outgoing calls only
    boolean mIsIncoming;
    boolean mDisconnected;

    int mIndex;          // index in GsmCallTracker.connections[], -1 if unassigned
                        // The GSM index is 1 + this

    /// M: [mtk04070][111125][ALPS00093395]MTK added for video call.
    boolean isVideo;

    /*
     * These time/timespan values are based on System.currentTimeMillis(),
     * i.e., "wall clock" time.
     */
    long mCreateTime;
    long mConnectTime;
    long mDisconnectTime;

    /*
     * These time/timespan values are based on SystemClock.elapsedRealTime(),
     * i.e., time since boot.  They are appropriate for comparison and
     * calculating deltas.
     */
    long mConnectTimeReal;
    long mDuration;
    long mHoldingStartTime;  // The time when the Connection last transitioned
                            // into HOLDING

    int mNextPostDialChar;       // index into postDialString

    DisconnectCause mCause = DisconnectCause.NOT_DISCONNECTED;
    PostDialState mPostDialState = PostDialState.NOT_STARTED;
    int mNumberPresentation = PhoneConstants.PRESENTATION_ALLOWED;
    UUSInfo mUusInfo;

    Handler mHandler;

    private PowerManager.WakeLock mPartialWakeLock;

    //***** Event Constants
    static final int EVENT_DTMF_DONE = 1;
    static final int EVENT_PAUSE_DONE = 2;
    static final int EVENT_NEXT_POST_DIAL = 3;
    static final int EVENT_WAKE_LOCK_TIMEOUT = 4;

    //***** Constants
    /// M: [mtk04070][111125][ALPS00093395]Replace 100 with 500.
    static final int PAUSE_DELAY_FIRST_MILLIS = 500;
    static final int PAUSE_DELAY_MILLIS = 3 * 1000;
    static final int WAKE_LOCK_TIMEOUT_MILLIS = 60*1000;

    /// M: [ALPS00330882]Easy porting for OP07 - ADAPT test case. @{
    private static IGsmConnectionExt mGsmConnectionExt;
    static {
        try{
            mGsmConnectionExt = MediatekClassFactory.createInstance(IGsmConnectionExt.class);
        } catch (Exception e){
            e.printStackTrace();
        }
    }
    /// @}


    //***** Inner Classes

    class MyHandler extends Handler {
        MyHandler(Looper l) {super(l);}

        @Override
        public void
        handleMessage(Message msg) {

            switch (msg.what) {
                case EVENT_NEXT_POST_DIAL:
                case EVENT_DTMF_DONE:
                case EVENT_PAUSE_DONE:
                    processNextPostDialChar();
                    break;
                case EVENT_WAKE_LOCK_TIMEOUT:
                    releaseWakeLock();
                    break;
            }
        }
    }

    //***** Constructors

    /** This is probably an MT call that we first saw in a CLCC response */
    /*package*/
    GsmConnection (Context context, DriverCall dc, GsmCallTracker ct, int index) {
        createWakeLock(context);
        acquireWakeLock();

        mOwner = ct;
        mHandler = new MyHandler(mOwner.getLooper());

        mAddress = dc.number;

        mIsIncoming = dc.isMT;
        mCreateTime = System.currentTimeMillis();
        mCnapName = dc.name;
        mCnapNamePresentation = dc.namePresentation;
        mNumberPresentation = dc.numberPresentation;
        mUusInfo = dc.uusInfo;

        mIndex = index;

        /// M: [mtk04070][111125][ALPS00093395]MTK added for video call. 
        isVideo = dc.isVideo;

        mParent = parentFromDCState (dc.state);
        mParent.attach(this, dc);
    }

    /** This is an MO call, created when dialing */
    /*package*/
    GsmConnection (Context context, String dialString, GsmCallTracker ct, GsmCall parent) {
        createWakeLock(context);
        acquireWakeLock();

        mOwner = ct;
        mHandler = new MyHandler(mOwner.getLooper());

        mDialString = dialString;

        mAddress = PhoneNumberUtils.extractNetworkPortionAlt(dialString);
        mPostDialString = PhoneNumberUtils.extractPostDialPortion(dialString);

        mIndex = -1;

        mIsIncoming = false;
        mCnapName = null;
        mCnapNamePresentation = PhoneConstants.PRESENTATION_ALLOWED;
        mNumberPresentation = PhoneConstants.PRESENTATION_ALLOWED;
        mCreateTime = System.currentTimeMillis();

        mParent = parent;
        parent.attachFake(this, GsmCall.State.DIALING);
    }

    public void dispose() {
    }

    static boolean
    equalsHandlesNulls (Object a, Object b) {
        return (a == null) ? (b == null) : a.equals (b);
    }

    /*package*/ boolean
    compareTo(DriverCall c) {
        // On mobile originated (MO) calls, the phone number may have changed
        // due to a SIM Toolkit call control modification.
        //
        // We assume we know when MO calls are created (since we created them)
        // and therefore don't need to compare the phone number anyway.
        if (! (mIsIncoming || c.isMT)) return true;

        // ... but we can compare phone numbers on MT calls, and we have
        // no control over when they begin, so we might as well

        String cAddress = PhoneNumberUtils.stringFromStringAndTOA(c.number, c.TOA);
        return mIsIncoming == c.isMT && equalsHandlesNulls(mAddress, cAddress);
    }

    @Override
    public String getAddress() {
        return mAddress;
    }

    @Override
    public GsmCall getCall() {
        return mParent;
    }

    @Override
    public long getCreateTime() {
        return mCreateTime;
    }

    @Override
    public long getConnectTime() {
        return mConnectTime;
    }

    @Override
    public long getDisconnectTime() {
        return mDisconnectTime;
    }

    @Override
    public long getDurationMillis() {
        if (mConnectTimeReal == 0) {
            return 0;
        } else if (mDuration == 0) {
            return SystemClock.elapsedRealtime() - mConnectTimeReal;
        } else {
            return mDuration;
        }
    }

    @Override
    public long getHoldDurationMillis() {
        if (getState() != GsmCall.State.HOLDING) {
            // If not holding, return 0
            return 0;
        } else {
            return SystemClock.elapsedRealtime() - mHoldingStartTime;
        }
    }

    @Override
    public DisconnectCause getDisconnectCause() {
        return mCause;
    }

    @Override
    public boolean isIncoming() {
        return mIsIncoming;
    }

    @Override
    public GsmCall.State getState() {
        if (mDisconnected) {
            return GsmCall.State.DISCONNECTED;
        } else {
            return super.getState();
        }
    }

    @Override
    public void hangup() throws CallStateException {
        if (!mDisconnected) {
            mOwner.hangup(this);
        } else {
            throw new CallStateException ("disconnected");
        }
    }

    /* [ALPS00475147] Add by mtk01411 */
    public void hangup(DisconnectCause discRingingConnCause) throws CallStateException {
        if (!mDisconnected) {
            mOwner.hangup(this);
            /* Only for RingingCall's connection to provide this interface */
            if (mParent == mOwner.mRingingCall && mOwner.mRingingCall.hasConnection(this)) { 			
                mCause = discRingingConnCause;
                Rlog.d(LOG_TAG, "[GSMConn] hangup RingingConn with cause=" + discRingingConnCause + ", and ringCall state=" + mParent.mState);				
                if (mParent.mState == Call.State.INCOMING || mParent.mState == Call.State.WAITING) {
                    mParent.mState = Call.State.DISCONNECTING;
                }
            }			
        } else {
            throw new CallStateException ("disconnected");
        }
    }

    @Override
    public void separate() throws CallStateException {
        if (!mDisconnected) {
            mOwner.separate(this);
        } else {
            throw new CallStateException ("disconnected");
        }
    }

    @Override
    public PostDialState getPostDialState() {
        return mPostDialState;
    }

    @Override
    public void proceedAfterWaitChar() {
        if (mPostDialState != PostDialState.WAIT) {
            Rlog.w(LOG_TAG, "GsmConnection.proceedAfterWaitChar(): Expected "
                + "getPostDialState() to be WAIT but was " + mPostDialState);
            return;
        }

        setPostDialState(PostDialState.STARTED);

        processNextPostDialChar();
    }

    @Override
    public void proceedAfterWildChar(String str) {
        if (mPostDialState != PostDialState.WILD) {
            Rlog.w(LOG_TAG, "GsmConnection.proceedAfterWaitChar(): Expected "
                + "getPostDialState() to be WILD but was " + mPostDialState);
            return;
        }

        setPostDialState(PostDialState.STARTED);

            // make a new postDialString, with the wild char replacement string
            // at the beginning, followed by the remaining postDialString.

            StringBuilder buf = new StringBuilder(str);
        buf.append(mPostDialString.substring(mNextPostDialChar));
        mPostDialString = buf.toString();
        mNextPostDialChar = 0;
            if (Phone.DEBUG_PHONE) {
                log("proceedAfterWildChar: new postDialString is " +
                    mPostDialString);
            }

            processNextPostDialChar();
        }

    @Override
    public void cancelPostDial() {
        setPostDialState(PostDialState.CANCELLED);
    }

    /**
     * Called when this Connection is being hung up locally (eg, user pressed "end")
     * Note that at this point, the hangup request has been dispatched to the radio
     * but no response has yet been received so update() has not yet been called
     */
    void
    onHangupLocal() {
        mCause = DisconnectCause.LOCAL;
    }

    DisconnectCause
    disconnectCauseFromCode(int causeCode) {
        /**
         * See 22.001 Annex F.4 for mapping of cause codes
         * to local tones
         */

        /// M: [mtk04070][111125][ALPS00093395]MTK added/refined. @{
        switch (causeCode) {
            case CallFailCause.USER_BUSY:
                return DisconnectCause.BUSY;

            // case CallFailCause.NO_CIRCUIT_AVAIL:
            case CallFailCause.TEMPORARY_FAILURE:
            // case CallFailCause.SWITCHING_CONGESTION:
            case CallFailCause.CHANNEL_NOT_AVAIL:
            case CallFailCause.QOS_NOT_AVAIL:
            // case CallFailCause.BEARER_NOT_AVAIL:
                return DisconnectCause.CONGESTION;

            case CallFailCause.NO_CIRCUIT_AVAIL:
                return DisconnectCause.NO_CIRCUIT_AVAIL;

            case CallFailCause.SWITCHING_CONGESTION:
                return DisconnectCause.SWITCHING_CONGESTION;

            case CallFailCause.BEARER_NOT_AVAIL:
                return DisconnectCause.BEARER_NOT_AVAIL;

            case CallFailCause.ACM_LIMIT_EXCEEDED:
                return DisconnectCause.LIMIT_EXCEEDED;

            case CallFailCause.CALL_BARRED:
                return DisconnectCause.CALL_BARRED;

            case CallFailCause.FDN_BLOCKED:
                return DisconnectCause.FDN_BLOCKED;

            case CallFailCause.UNOBTAINABLE_NUMBER:
                return DisconnectCause.UNOBTAINABLE_NUMBER;

            case CallFailCause.NO_ROUTE_TO_DESTINATION:
                return DisconnectCause.NO_ROUTE_TO_DESTINATION;

            case CallFailCause.NO_USER_RESPONDING:
                return DisconnectCause.NO_USER_RESPONDING;

            case CallFailCause.USER_ALERTING_NO_ANSWER:
                return DisconnectCause.USER_ALERTING_NO_ANSWER;

            case CallFailCause.CALL_REJECTED:
                return DisconnectCause.CALL_REJECTED;

            case CallFailCause.NORMAL_UNSPECIFIED:
                return DisconnectCause.NORMAL_UNSPECIFIED;

            case CallFailCause.INVALID_NUMBER_FORMAT:
                return DisconnectCause.INVALID_NUMBER_FORMAT;

            case CallFailCause.FACILITY_REJECTED:
                return DisconnectCause.FACILITY_REJECTED;

            case CallFailCause.RESOURCE_UNAVAILABLE:
                return DisconnectCause.RESOURCE_UNAVAILABLE;

            case CallFailCause.BEARER_NOT_AUTHORIZED:
                return DisconnectCause.BEARER_NOT_AUTHORIZED;

            case CallFailCause.SERVICE_NOT_AVAILABLE:
            //For solving [ALPS00228887] Voice call prompt should be given out if outgoing phone not support VT call.
            //2012.02.09, mtk04070
            case CallFailCause.NETWORK_OUT_OF_ORDER:
                return DisconnectCause.SERVICE_NOT_AVAILABLE;

            case CallFailCause.BEARER_NOT_IMPLEMENT:
                return DisconnectCause.BEARER_NOT_IMPLEMENT;

            case CallFailCause.FACILITY_NOT_IMPLEMENT:
                return DisconnectCause.FACILITY_NOT_IMPLEMENT;

            case CallFailCause.RESTRICTED_BEARER_AVAILABLE:
                return DisconnectCause.RESTRICTED_BEARER_AVAILABLE;

            case CallFailCause.OPTION_NOT_AVAILABLE:
                return DisconnectCause.OPTION_NOT_AVAILABLE;

            case CallFailCause.INCOMPATIBLE_DESTINATION:
                return DisconnectCause.INCOMPATIBLE_DESTINATION;

            case CallFailCause.CM_MM_RR_CONNECTION_RELEASE:
                return DisconnectCause.CM_MM_RR_CONNECTION_RELEASE;

            case CallFailCause.ERROR_UNSPECIFIED:
            case CallFailCause.NORMAL_CLEARING:
            default:
                GSMPhone phone = mOwner.mPhone;
                int serviceState = phone.getServiceState().getState();
                UiccCardApplication cardApp = UiccController
                        .getInstance(phone.getMySimId())
                        .getUiccCardApplication(UiccController.APP_FAM_3GPP);
                AppState uiccAppState = (cardApp != null) ? cardApp.getState() :
                                                            AppState.APPSTATE_UNKNOWN;

				Rlog.d(LOG_TAG, "serviceState = " + serviceState);
	
                if (serviceState == ServiceState.STATE_POWER_OFF) {
                    return DisconnectCause.POWER_OFF;
                } else if (serviceState == ServiceState.STATE_OUT_OF_SERVICE
                        || serviceState == ServiceState.STATE_EMERGENCY_ONLY ) {
					/* 
					   Return OUT_OF_SERVICE when ECC is normal clearing will 
					   cause ECC retry in EmergencyCallHelper, so return NORMAL. 
					   mtk04070, 2012.02.17
					*/
                    if (causeCode == CallFailCause.NORMAL_CLEARING) {
 					    return DisconnectCause.NORMAL;
                    }
					else {
                    return DisconnectCause.OUT_OF_SERVICE;
					}	
                } else if (uiccAppState != AppState.APPSTATE_READY) {
                    return DisconnectCause.ICC_ERROR;
                } else if (causeCode == CallFailCause.ERROR_UNSPECIFIED) {
                    if (phone.mSST.mRestrictedState.isCsRestricted()) {
                        return DisconnectCause.CS_RESTRICTED;
                    } else if (phone.mSST.mRestrictedState.isCsEmergencyRestricted()) {
                        return DisconnectCause.CS_RESTRICTED_EMERGENCY;
                    } else if (phone.mSST.mRestrictedState.isCsNormalRestricted()) {
                        return DisconnectCause.CS_RESTRICTED_NORMAL;
                    } else {
                        return DisconnectCause.ERROR_UNSPECIFIED;
                    }
                } else if (causeCode == CallFailCause.NORMAL_CLEARING) {
                    return DisconnectCause.NORMAL;
                } else {
                    // If nothing else matches, report unknown call drop reason
                    // to app, not NORMAL call end.
                    return DisconnectCause.ERROR_UNSPECIFIED;
                }
        }
        /// @}
    }

    /*package*/ void
    onRemoteDisconnect(int causeCode) {
        onDisconnect(disconnectCauseFromCode(causeCode));
    }

    /** Called when the radio indicates the connection has been disconnected */
    /*package*/ boolean
    onDisconnect(DisconnectCause cause) {
        boolean changed = false;

        mCause = cause;

        if (!mDisconnected) {
            mIndex = -1;

            mDisconnectTime = System.currentTimeMillis();
            mDuration = SystemClock.elapsedRealtime() - mConnectTimeReal;
            mDisconnected = true;

            if (DBG) Rlog.d(LOG_TAG, "onDisconnect: cause=" + cause);

            mOwner.mPhone.notifyDisconnect(this);

            if (mParent != null) {
                changed = mParent.connectionDisconnected(this);
            }
        }
        releaseWakeLock();
        return changed;
    }

    // Returns true if state has changed, false if nothing changed
    /*package*/ boolean
    update (DriverCall dc) {
        GsmCall newParent;
        boolean changed = false;
        boolean wasConnectingInOrOut = isConnectingInOrOut();
        boolean wasHolding = (getState() == GsmCall.State.HOLDING);

        newParent = parentFromDCState(dc.state);

        if (!equalsHandlesNulls(mAddress, dc.number)) {
            if (Phone.DEBUG_PHONE) log("update: phone # changed!");

            /* To solve ALPS01365144, if the current address(phone number) is a substring of new one,
               do not need to update it */
            boolean shouldUpdateNumber = ((mAddress == null) || mAddress.isEmpty());
            if ((dc.number != null) && (!dc.number.isEmpty()) && !shouldUpdateNumber) {
               shouldUpdateNumber = (!dc.number.endsWith(mAddress));
               log("[GsmConnection][update]shouldUpdateNumber = " + shouldUpdateNumber);
            }

            if (shouldUpdateNumber) {
               mAddress = dc.number;
               changed = true;
            }
        }

        // A null cnapName should be the same as ""
        if (TextUtils.isEmpty(dc.name)) {
            if (!TextUtils.isEmpty(mCnapName)) {
                changed = true;
                mCnapName = "";
            }
        } else if (!dc.name.equals(mCnapName)) {
            changed = true;
            mCnapName = dc.name;
        }

        if (Phone.DEBUG_PHONE) log("--dssds----"+mCnapName);
        mCnapNamePresentation = dc.namePresentation;
        mNumberPresentation = dc.numberPresentation;

        /// M: [mtk04070][111125][ALPS00093395]For VT. @{
        if (isVideo != dc.isVideo) {
             isVideo = dc.isVideo;
             changed = true;
        }
        /// @}

        if (newParent != mParent) {
            if (mParent != null) {
                mParent.detach(this);
            }
            newParent.attach(this, dc);
            mParent = newParent;
            changed = true;
        } else {
            boolean parentStateChange;
            parentStateChange = mParent.update (this, dc);
            changed = changed || parentStateChange;
        }

        /** Some state-transition events */

        /// M: [mtk04070][111125][ALPS00093395]Show more log information. @{
        if (Phone.DEBUG_PHONE) log(
                "update: id=" + (mIndex + 1) +
                ", parent=" + mParent +
                ", hasNewParent=" + (newParent != mParent) +
                ", wasConnectingInOrOut=" + wasConnectingInOrOut +
                ", wasHolding=" + wasHolding +
                ", isConnectingInOrOut=" + isConnectingInOrOut() +
                ", changed=" + changed +
                ", isVideo=" + isVideo);
        /// @}


        if (wasConnectingInOrOut && !isConnectingInOrOut()) {
            onConnectedInOrOut();
        }

        if (changed && !wasHolding && (getState() == GsmCall.State.HOLDING)) {
            // We've transitioned into HOLDING
            onStartedHolding();
        }

        return changed;
    }

    /**
     * Called when this Connection is in the foregroundCall
     * when a dial is initiated.
     * We know we're ACTIVE, and we know we're going to end up
     * HOLDING in the backgroundCall
     */
    void
    fakeHoldBeforeDial() {
        if (mParent != null) {
            mParent.detach(this);
        }

        mParent = mOwner.mBackgroundCall;
        mParent.attachFake(this, GsmCall.State.HOLDING);

        onStartedHolding();
    }

    /*package*/ int
    getGSMIndex() throws CallStateException {
        if (mIndex >= 0) {
            return mIndex + 1;
        } else {
            throw new CallStateException ("GSM index not yet assigned");
        }
    }

    /**
     * An incoming or outgoing call has connected
     */
    void
    onConnectedInOrOut() {
        mConnectTime = System.currentTimeMillis();
        mConnectTimeReal = SystemClock.elapsedRealtime();
        mDuration = 0;

        // bug #678474: incoming call interpreted as missed call, even though
        // it sounds like the user has picked up the call.
        if (Phone.DEBUG_PHONE) {
            log("onConnectedInOrOut: connectTime=" + mConnectTime);
        }

        if (!mIsIncoming) {
            // outgoing calls only
            processNextPostDialChar();
        }
        releaseWakeLock();
    }

    /*package*/ void
    onStartedHolding() {
        mHoldingStartTime = SystemClock.elapsedRealtime();
    }
    /**
     * Performs the appropriate action for a post-dial char, but does not
     * notify application. returns false if the character is invalid and
     * should be ignored
     */
    private boolean
    processPostDialChar(char c) {
        if (PhoneNumberUtils.is12Key(c)) {
            mOwner.mCi.sendDtmf(c, mHandler.obtainMessage(EVENT_DTMF_DONE));
        } else if (c == PhoneNumberUtils.PAUSE) {
            // From TS 22.101:

            // "The first occurrence of the "DTMF Control Digits Separator"
            //  shall be used by the ME to distinguish between the addressing
            //  digits (i.e. the phone number) and the DTMF digits...."

            if (mNextPostDialChar == 1) {
                // The first occurrence.
                // We don't need to pause here, but wait for just a bit anyway
                /// M: [ALPS00330882]Easy porting for OP07 - ADAPT test case. @{
                mHandler.sendMessageDelayed(mHandler.obtainMessage(EVENT_PAUSE_DONE),
                                     mGsmConnectionExt.getFirstPauseDelayMSeconds(PAUSE_DELAY_FIRST_MILLIS));
                /// @}
            } else {
                // It continues...
                // "Upon subsequent occurrences of the separator, the UE shall
                //  pause again for 3 seconds (\u00B1 20 %) before sending any
                //  further DTMF digits."
                mHandler.sendMessageDelayed(mHandler.obtainMessage(EVENT_PAUSE_DONE),
                                            PAUSE_DELAY_MILLIS);
            }
        } else if (c == PhoneNumberUtils.WAIT) {
            setPostDialState(PostDialState.WAIT);
        } else if (c == PhoneNumberUtils.WILD) {
            setPostDialState(PostDialState.WILD);
        } else {
            return false;
        }

        return true;
    }

    @Override
    public String
    getRemainingPostDialString() {
        if (mPostDialState == PostDialState.CANCELLED
            || mPostDialState == PostDialState.COMPLETE
            || mPostDialString == null
            || mPostDialString.length() <= mNextPostDialChar
        ) {
            return "";
        }

        return mPostDialString.substring(mNextPostDialChar);
    }

    @Override
    protected void finalize()
    {
        /**
         * It is understood that This finializer is not guaranteed
         * to be called and the release lock call is here just in
         * case there is some path that doesn't call onDisconnect
         * and or onConnectedInOrOut.
         */
        if (mPartialWakeLock.isHeld()) {
            Rlog.e(LOG_TAG, "[GSMConn] UNEXPECTED; mPartialWakeLock is held when finalizing.");
        }
        releaseWakeLock();
    }

    private void
    processNextPostDialChar() {
        char c = 0;
        Registrant postDialHandler;

        if (mPostDialState == PostDialState.CANCELLED) {
            //Rlog.v("GSM", "##### processNextPostDialChar: postDialState == CANCELLED, bail");
            return;
        }

        /// M: [mtk04070][111125][ALPS00093395]Check disconnected condition. @{
        if (mPostDialString == null ||
                mPostDialString.length() <= mNextPostDialChar ||
                mDisconnected == true) {
        /// @}
            setPostDialState(PostDialState.COMPLETE);

            // notifyMessage.arg1 is 0 on complete
            c = 0;
        } else {
            boolean isValid;

            setPostDialState(PostDialState.STARTED);

            c = mPostDialString.charAt(mNextPostDialChar++);

            isValid = processPostDialChar(c);

            if (!isValid) {
                // Will call processNextPostDialChar
                mHandler.obtainMessage(EVENT_NEXT_POST_DIAL).sendToTarget();
                // Don't notify application
                Rlog.e("GSM", "processNextPostDialChar: c=" + c + " isn't valid!");
                return;
            }
        }

        postDialHandler = mOwner.mPhone.mPostDialHandler;

        Message notifyMessage;

        if (postDialHandler != null
                && (notifyMessage = postDialHandler.messageForRegistrant()) != null) {
            // The AsyncResult.result is the Connection object
            PostDialState state = mPostDialState;
            AsyncResult ar = AsyncResult.forMessage(notifyMessage);
            ar.result = this;
            ar.userObj = state;

            // arg1 is the character that was/is being processed
            notifyMessage.arg1 = c;

            //Rlog.v("GSM", "##### processNextPostDialChar: send msg to postDialHandler, arg1=" + c);
            notifyMessage.sendToTarget();
        }
    }


    /** "connecting" means "has never been ACTIVE" for both incoming
     *  and outgoing calls
     */
    private boolean
    isConnectingInOrOut() {
        return mParent == null || mParent == mOwner.mRingingCall
            || mParent.mState == GsmCall.State.DIALING
            || mParent.mState == GsmCall.State.ALERTING;
    }

    private GsmCall
    parentFromDCState (DriverCall.State state) {
        switch (state) {
            case ACTIVE:
            case DIALING:
            case ALERTING:
                return mOwner.mForegroundCall;
            //break;

            case HOLDING:
                return mOwner.mBackgroundCall;
            //break;

            case INCOMING:
            case WAITING:
                return mOwner.mRingingCall;
            //break;

            default:
                throw new RuntimeException("illegal call state: " + state);
        }
    }

    /**
     * Set post dial state and acquire wake lock while switching to "started"
     * state, the wake lock will be released if state switches out of "started"
     * state or after WAKE_LOCK_TIMEOUT_MILLIS.
     * @param s new PostDialState
     */
    private void setPostDialState(PostDialState s) {
        if (mPostDialState != PostDialState.STARTED
                && s == PostDialState.STARTED) {
            acquireWakeLock();
            Message msg = mHandler.obtainMessage(EVENT_WAKE_LOCK_TIMEOUT);
            mHandler.sendMessageDelayed(msg, WAKE_LOCK_TIMEOUT_MILLIS);
        } else if (mPostDialState == PostDialState.STARTED
                && s != PostDialState.STARTED) {
            mHandler.removeMessages(EVENT_WAKE_LOCK_TIMEOUT);
            releaseWakeLock();
        }
        mPostDialState = s;
    }

    private void
    createWakeLock(Context context) {
        PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        mPartialWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, LOG_TAG);
    }

    private void
    acquireWakeLock() {
        log("acquireWakeLock");
        mPartialWakeLock.acquire();
    }

    private void
    releaseWakeLock() {
        synchronized(mPartialWakeLock) {
            if (mPartialWakeLock.isHeld()) {
                log("releaseWakeLock");
                mPartialWakeLock.release();
            }
        }
    }

    private void log(String msg) {
        Rlog.d(LOG_TAG, "[GSMConn] " + msg);
    }

    @Override
    public int getNumberPresentation() {
        return mNumberPresentation;
    }

    @Override
    public UUSInfo getUUSInfo() {
        return mUusInfo;
    }

    /// M: [mtk04070][111125][ALPS00093395]MTK proprietary methods. @{
    public boolean isVideo() {
        return isVideo;
    }

    void
    resumeHoldAfterDialFailed() {
        if (mParent != null) {
            mParent.detach(this);
        }

        mParent = mOwner.mForegroundCall;
        mParent.attachFake(this, GsmCall.State.ACTIVE);
    }

    /*package*/ void
    onReplaceDisconnect(DisconnectCause cause) {
        this.mCause = cause;

        if (!mDisconnected) {
            mIndex = -1;

            mDisconnectTime = System.currentTimeMillis();
            mDuration = SystemClock.elapsedRealtime() - mConnectTimeReal;
            mDisconnected = true;

            log("onReplaceDisconnect: cause=" + cause);

            if (!mOwner.isRejectedByMoms()) {
               mOwner.mPhone.notifyVtReplaceDisconnect(this);
            }

            if (mParent != null) {
                mParent.connectionDisconnected(this);
            }
        }
        releaseWakeLock();
    }

    public String toString() {
        StringBuilder str = new StringBuilder(128);

        str.append("*  -> id: " + (mIndex + 1))
                .append(", num: " + getAddress())
                .append(", MT: " + mIsIncoming)
                .append(", mDisconnected: " + mDisconnected);
        return str.toString();
    }
   /// @}
}
