/*
 * Copyright (C) 2011 The Android Open Source Project
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

import com.android.phone.CallGatewayManager.RawGatewayInfo;
import com.android.phone.Constants.CallStatusCode;
import com.android.phone.ErrorDialogActivity;
import com.android.phone.OtaUtils.CdmaOtaScreenState;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.provider.CallLog.Calls;
import android.telephony.PhoneNumberUtils;
import android.telephony.ServiceState;
import android.util.Log;
import android.text.TextUtils;

import com.android.internal.telephony.Call;
import com.android.internal.telephony.CallManager;
import com.android.internal.telephony.CallStateException;
import com.android.internal.telephony.Connection;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.TelephonyCapabilities;
import com.android.internal.telephony.sip.SipPhone;
import com.android.phone.Constants.CallStatusCode;
import com.android.phone.OtaUtils.CdmaOtaScreenState;

import com.mediatek.phone.DualTalkUtils;
import com.mediatek.phone.EmergencyRuleHandler;
import com.mediatek.phone.PhoneFeatureConstants.FeatureOption;
import com.mediatek.phone.gemini.GeminiUtils;
import com.mediatek.phone.vt.VTCallUtils;
import com.mediatek.phone.wrapper.CallManagerWrapper;
import com.mediatek.phone.wrapper.PhoneWrapper;

/**
 * Phone app module in charge of "call control".
 *
 * This is a singleton object which acts as the interface to the telephony layer
 * (and other parts of the Android framework) for all user-initiated telephony
 * functionality, like making outgoing calls.
 *
 * This functionality includes things like:
 *   - actually running the placeCall() method and handling errors or retries
 *   - running the whole "emergency call in airplane mode" sequence
 *   - running the state machine of MMI sequences
 *   - restoring/resetting mute and speaker state when a new call starts
 *   - updating the prox sensor wake lock state
 *   - resolving what the voicemail: intent should mean (and making the call)
 *
 * The single CallController instance stays around forever; it's not tied
 * to the lifecycle of any particular Activity (like the InCallScreen).
 * There's also no implementation of onscreen UI here (that's all in InCallScreen).
 *
 * Note that this class does not handle asynchronous events from the telephony
 * layer, like reacting to an incoming call; see CallNotifier for that.  This
 * class purely handles actions initiated by the user, like outgoing calls.
 */
public class CallController extends Handler {
    private static final String TAG = "CallController";
    /**
     * M:
     * Original Code:
     * private static final boolean DBG =
     *      (PhoneGlobals.DBG_LEVEL >= 1) && (SystemProperties.getInt("ro.debuggable", 0) == 1);
     * Do not check in with VDBG = true, since that may write PII to the system log.
     * private static final boolean VDBG = false;
     * @{
     */
    private static final boolean DBG = true;    /** M: Change Feature Open the debug info*/
    private static final boolean VDBG = true;   /** M: Change Feature Open the debug info*/
    /** @} */

    /** The singleton CallController instance. */
    private static CallController sInstance;

    private PhoneGlobals mApp;
    private CallManager mCM;
    private CallLogger mCallLogger;
    final private CallGatewayManager mCallGatewayManager;

    /** Helper object for emergency calls in some rare use cases.  Created lazily. */
    private EmergencyCallHelper mEmergencyCallHelper;


    //
    // Message codes; see handleMessage().
    //

    private static final int THREEWAY_CALLERINFO_DISPLAY_DONE = 1;

    //
    // Misc constants.
    //

    // Amount of time the UI should display "Dialing" when initiating a CDMA
    // 3way call.  (See comments on the THRWAY_ACTIVE case in
    // placeCallInternal() for more info.)
    private static final int THREEWAY_CALLERINFO_DISPLAY_TIME = 3000; // msec


    /**
     * Initialize the singleton CallController instance.
     *
     * This is only done once, at startup, from PhoneApp.onCreate().
     * From then on, the CallController instance is available via the
     * PhoneApp's public "callController" field, which is why there's no
     * getInstance() method here.
     */
    /* package */ static CallController init(PhoneGlobals app, CallLogger callLogger,
            CallGatewayManager callGatewayManager) {
        synchronized (CallController.class) {
            if (sInstance == null) {
                sInstance = new CallController(app, callLogger, callGatewayManager);
            } else {
                Log.wtf(TAG, "init() called multiple times!  sInstance = " + sInstance);
            }
            return sInstance;
        }
    }

    /**
     * Private constructor (this is a singleton).
     * @see init()
     */
    private CallController(PhoneGlobals app, CallLogger callLogger,
            CallGatewayManager callGatewayManager) {
        if (DBG) log("CallController constructor: app = " + app);
        mApp = app;
        mCM = app.mCM;
        mCallLogger = callLogger;
        mCallGatewayManager = callGatewayManager;
    }

    @Override
    public void handleMessage(Message msg) {
        if (VDBG) log("handleMessage: " + msg);
        if (handleMessageMtk(msg)) {
            return;
        }

        switch (msg.what) {

            case THREEWAY_CALLERINFO_DISPLAY_DONE:
                if (DBG) log("THREEWAY_CALLERINFO_DISPLAY_DONE...");

                if (mApp.cdmaPhoneCallState.getCurrentCallState()
                    == CdmaPhoneCallState.PhoneCallState.THRWAY_ACTIVE) {
                    // Reset the mThreeWayCallOrigStateDialing state
                    mApp.cdmaPhoneCallState.setThreeWayCallOrigState(false);

                    mApp.getCallModeler().setCdmaOutgoing3WayCall(null);
                }
                break;

            default:
                Log.wtf(TAG, "handleMessage: unexpected code: " + msg);
                break;
        }
    }

    //
    // Outgoing call sequence
    //

    /**
     * Initiate an outgoing call.
     *
     * Here's the most typical outgoing call sequence:
     *
     *  (1) OutgoingCallBroadcaster receives a CALL intent and sends the
     *      NEW_OUTGOING_CALL broadcast
     *
     *  (2) The broadcast finally reaches OutgoingCallReceiver, which stashes
     *      away a copy of the original CALL intent and launches
     *      SipCallOptionHandler
     *
     *  (3) SipCallOptionHandler decides whether this is a PSTN or SIP call (and
     *      in some cases brings up a dialog to let the user choose), and
     *      ultimately calls CallController.placeCall() (from the
     *      setResultAndFinish() method) with the stashed-away intent from step
     *      (2) as the "intent" parameter.
     *
     *  (4) Here in CallController.placeCall() we read the phone number or SIP
     *      address out of the intent and actually initiate the call, and
     *      simultaneously launch the InCallScreen to display the in-call UI.
     *
     *  (5) We handle various errors by directing the InCallScreen to
     *      display error messages or dialogs (via the InCallUiState
     *      "pending call status code" flag), and in some cases we also
     *      sometimes continue working in the background to resolve the
     *      problem (like in the case of an emergency call while in
     *      airplane mode).  Any time that some onscreen indication to the
     *      user needs to change, we update the "status dialog" info in
     *      the inCallUiState and (re)launch the InCallScreen to make sure
     *      it's visible.
     */
    public void placeCall(Intent intent) {
        log("placeCall()...  intent = " + intent);
        if (VDBG) log("                extras = " + intent.getExtras());

        // TODO: Do we need to hold a wake lock while this method runs?
        //       Or did we already acquire one somewhere earlier
        //       in this sequence (like when we first received the CALL intent?)

        if (intent == null) {
            Log.wtf(TAG, "placeCall: called with null intent");
            throw new IllegalArgumentException("placeCall: called with null intent");
        }

        String action = intent.getAction();
        Uri uri = intent.getData();
        if (uri == null) {
            Log.wtf(TAG, "placeCall: intent had no data");
            throw new IllegalArgumentException("placeCall: intent had no data");
        }

        /// M: For ALPS00879741 @{
        // Need to check empty number, or else will get exception.
        if (isEmptyNumber(intent)) {
            return;
        }
        /// @}

        String scheme = uri.getScheme();
        String number = PhoneNumberUtils.getNumberFromIntent(intent, mApp);
        if (VDBG) {
            log("- action: " + action);
            log("- uri: " + uri);
            log("- scheme: " + scheme);
            log("- number: " + number);
        }

        /// M: if there is a invalid call, disconnect it first @{
        if (!placeCallPreCheck(intent, number)){
            return;
        }
        /// @}

        // This method should only be used with the various flavors of CALL
        // intents.  (It doesn't make sense for any other action to trigger an
        // outgoing call!)
        if (!(Intent.ACTION_CALL.equals(action)
              || Intent.ACTION_CALL_EMERGENCY.equals(action)
              || Intent.ACTION_CALL_PRIVILEGED.equals(action))) {
            Log.wtf(TAG, "placeCall: unexpected intent action " + action);
            throw new IllegalArgumentException("Unexpected action: " + action);
        }

        // Check to see if this is an OTASP call (the "activation" call
        // used to provision CDMA devices), and if so, do some
        // OTASP-specific setup.
        Phone phone = mApp.mCM.getDefaultPhone();
        if (TelephonyCapabilities.supportsOtasp(phone)) {
            checkForOtaspCall(intent);
        }

        // Clear out the "restore mute state" flag since we're
        // initiating a brand-new call.
        //
        // (This call to setRestoreMuteOnInCallResume(false) informs the
        // phone app that we're dealing with a new connection
        // (i.e. placing an outgoing call, and NOT handling an aborted
        // "Add Call" request), so we should let the mute state be handled
        // by the PhoneUtils phone state change handler.)
        mApp.setRestoreMuteOnInCallResume(false);

        CallStatusCode status = placeCallInternal(intent);
        if (DBG) log(" status = " + status);

        switch (status) {
            // Call was placed successfully:
            case SUCCESS:
            case EXITED_ECM:
                if (DBG) log("==> placeCall(): success from placeCallInternal(): " + status);
                /// M: For ALPS00932409. @{
                // Turn on the speaker when the feature option is on.
                forceSpeakerOnByNeeded();
                /// @}
                break;

            /** M: New Feature video call drop back to voice call @{ */
            case DROP_VOICECALL:
                Bundle bExtras = intent.getExtras();
                final int slot = bExtras.getInt(Constants.EXTRA_SLOT_ID);
                String realNumber = null;
                try {
                    realNumber = PhoneUtils.getInitialNumber(intent);
                } catch (Exception e) {
                    if (DBG) log(e.toString());
                    realNumber = null;
                }
                if (DBG) log("ready for show dialog ... ");
                handleDropVoiceCall(status, realNumber, slot);
                break;
            /** @} */

            default:
                // Any other status code is a failure.
                log("==> placeCall(): failure code from placeCallInternal(): " + status);
                // Handle the various error conditions that can occur when
                // initiating an outgoing call, typically by directing the
                // InCallScreen to display a diagnostic message (via the
                // "pending call status code" flag.)
                handleOutgoingCallError(status);
                break;
        }

        // Finally, regardless of whether we successfully initiated the
        // outgoing call or not, force the InCallScreen to come to the
        // foreground.
        //
        // (For successful calls the the user will just see the normal
        // in-call UI.  Or if there was an error, the InCallScreen will
        // notice the InCallUiState pending call status code flag and display an
        // error indication instead.)
    }

    /**
     * Actually make a call to whomever the intent tells us to.
     *
     * Note that there's no need to explicitly update (or refresh) the
     * in-call UI at any point in this method, since a fresh InCallScreen
     * instance will be launched automatically after we return (see
     * placeCall() above.)
     *
     * @param intent the CALL intent describing whom to call
     * @return CallStatusCode.SUCCESS if we successfully initiated an
     *    outgoing call.  If there was some kind of failure, return one of
     *    the other CallStatusCode codes indicating what went wrong.
     */
    private CallStatusCode placeCallInternal(Intent intent) {
        if (DBG) log("placeCallInternal()...  intent = " + intent);

        // TODO: This method is too long.  Break it down into more
        // manageable chunks.

        final Uri uri = intent.getData();
        final String scheme = (uri != null) ? uri.getScheme() : null;
        String number;
        Phone phone = null;
        /// M: @{
        mSlot = intent.getIntExtra(Constants.EXTRA_SLOT_ID, GeminiUtils.getDefaultSlot());
        boolean isVideoCall = false ;
        if (FeatureOption.MTK_VT3G324M_SUPPORT) {
            isVideoCall = intent.getBooleanExtra(Constants.EXTRA_IS_VIDEO_CALL, false);
        }
        log("placeCallInternal() FeatureOption.MTK_VT3G324M_SUPPORT = " + FeatureOption.MTK_VT3G324M_SUPPORT);
        if (DBG) {
            log("placeCallInternal() slot = " + mSlot + ", isVideoCall=" + isVideoCall);
        }
        /// @}

        // Check the current ServiceState to make sure it's OK
        // to even try making a call.
        CallStatusCode okToCallStatus = checkIfOkToInitiateOutgoingCall(
                mCM.getServiceState());

        // TODO: Streamline the logic here.  Currently, the code is
        // unchanged from its original form in InCallScreen.java.  But we
        // should fix a couple of things:
        // - Don't call checkIfOkToInitiateOutgoingCall() more than once
        // - Wrap the try/catch for VoiceMailNumberMissingException
        //   around *only* the call that can throw that exception.

        try {
            number = PhoneUtils.getInitialNumber(intent);
            if (VDBG) log("- actual number to dial: '" + number + "'");

            // find the phone first
            // TODO Need a way to determine which phone to place the call
            // It could be determined by SIP setting, i.e. always,
            // or by number, i.e. for international,
            // or by user selection, i.e., dialog query,
            // or any of combinations
            String sipPhoneUri = intent.getStringExtra(
                    OutgoingCallBroadcaster.EXTRA_SIP_PHONE_URI);
            phone = PhoneUtils.pickPhoneBasedOnNumber(mCM, scheme, number, sipPhoneUri);
            if (VDBG) log("- got Phone instance: " + phone + ", class = " + phone.getClass());

            /// M: @{
            /* Original Code:
            // update okToCallStatus based on new phone
            okToCallStatus = checkIfOkToInitiateOutgoingCall(
                    phone.getServiceState().getState());
            */
            /// @}

        } catch (PhoneUtils.VoiceMailNumberMissingException ex) {
            // If the call status is NOT in an acceptable state, it
            // may effect the way the voicemail number is being
            // retrieved.  Mask the VoiceMailNumberMissingException
            // with the underlying issue of the phone state.
            if (okToCallStatus != CallStatusCode.SUCCESS) {
                if (DBG) log("Voicemail number not reachable in current SIM card state.");
                return okToCallStatus;
            }
            if (DBG) log("VoiceMailNumberMissingException from getInitialNumber()");
            return CallStatusCode.VOICEMAIL_NUMBER_MISSING;
        }

        if (number == null) {
            Log.w(TAG, "placeCall: couldn't get a phone number from Intent " + intent);
            return CallStatusCode.NO_PHONE_NUMBER_SUPPLIED;
        }


        // Sanity-check that ACTION_CALL_EMERGENCY is used if and only if
        // this is a call to an emergency number
        // (This is just a sanity-check; this policy *should* really be
        // enforced in OutgoingCallBroadcaster.onCreate(), which is the
        // main entry point for the CALL and CALL_* intents.)
        /**
         * M: 
         * This may take more time than call isEmergencyNumber.
         * Original Code:
         * boolean isEmergencyNumber = PhoneNumberUtils.isLocalEmergencyNumber(number, mApp);
         * boolean isPotentialEmergencyNumber =
               PhoneNumberUtils.isPotentialLocalEmergencyNumber(number, mApp);
         * @{
         */
        boolean isEmergencyNumber = PhoneUtils.isEmergencyNumber(number);    /** M: Change Feature performance*/
        /** @} */

        boolean isEmergencyIntent = Intent.ACTION_CALL_EMERGENCY.equals(intent.getAction());

        /**
         * add by mediatek .inc
         * description : if it's an emergency number
         * change the call type to voice call
         */
        if (isEmergencyNumber) {
            if (isVideoCall) {
                intent.putExtra(Constants.EXTRA_IS_VIDEO_CALL, false);
                isVideoCall = false;
            }
        }

        /**
         * commend by mediatek .inc
         * description : see above
         * original android code :
        if (isPotentialEmergencyNumber && !isEmergencyIntent) {
            Log.e(TAG, "Non-CALL_EMERGENCY Intent " + intent
                    + " attempted to call potential emergency number " + number
                    + ".");
            return CallStatusCode.CALL_FAILED;
        } else if (!isPotentialEmergencyNumber && isEmergencyIntent) {
            Log.e(TAG, "Received CALL_EMERGENCY Intent " + intent
                    + " with non-potential-emergency number " + number
                    + " -- failing call.");
            return CallStatusCode.CALL_FAILED;
        }
        */
        if (!isEmergencyNumber && isEmergencyIntent) {
            Log.e(TAG, "Received CALL_EMERGENCY Intent " + intent
                    + " with non-potential-emergency number " + number
                    + " -- failing call.");
            return CallStatusCode.CALL_FAILED;
        }

        /**
         * change feature by mediatek .inc
         * description : now we known whether it's an emergency number
         * pick the first available sim
         */
        if (GeminiUtils.isGeminiSupport() && isEmergencyNumber) {
            mSlot = PhoneWrapper.pickBestSlotForEmergencyCall(PhoneGlobals.getPhone(), number);
        }

        if (phone instanceof SipPhone) {
            okToCallStatus = checkIfOkToInitiateOutgoingCall(
                    phone.getServiceState().getState());
            if (DualTalkUtils.isSupportDualTalk()) {
                if (mApp.notifier.mDualTalk == null) {
                    mApp.notifier.mDualTalk = DualTalkUtils.getInstance();
                }
                if (!mApp.notifier.mDualTalk.isPhoneCallAllowed(phone)) {
                    okToCallStatus = CallStatusCode.CALL_FAILED;
                }
            }
        } else {
            okToCallStatus = checkIfOkToInitiateOutgoingCall(
                    phone.getServiceState().getState(), mSlot);
        }

        // for G+C project
        if (isEmergencyNumber && DualTalkUtils.isEvdoDTSupport()) {
            synchronized (this) {
                if (mEmergencyCallHelper == null) {
                    mEmergencyCallHelper = new EmergencyCallHelper(this);
                }
            }

            // MTK solution for G+C ECC. Customer can replace it by needed.
            int eccSlot = intent.getIntExtra(Constants.EXTRA_SLOT_ID, -1);
            log("eccSlot = " + eccSlot);
            if (eccSlot == -1) {
                EmergencyRuleHandler eccRuleHandler = new EmergencyRuleHandler(number);
                eccSlot = eccRuleHandler.getPreferedSlot();
            }

            Phone tempPhone = PhoneGlobals.getPhone();
            boolean isRadioOn = false;
            if (GeminiUtils.isGeminiSupport()) {
                isRadioOn = PhoneWrapper.isRadioOn(tempPhone, eccSlot);
            }

            if (isRadioOn) {
                okToCallStatus = checkIfOkToInitiateOutgoingCall(
                        phone.getServiceState().getState(), eccSlot);
                mSlot = eccSlot;
            } else {
                mEmergencyCallHelper.startEmergencyCallExt(number, eccSlot);
                return CallStatusCode.SUCCESS;
            }
        }

        // If we're trying to call an emergency number, then it's OK to
        // proceed in certain states where we'd otherwise bring up
        // an error dialog:
        // - If we're in EMERGENCY_ONLY mode, then (obviously) you're allowed
        //   to dial emergency numbers.
        // - If we're OUT_OF_SERVICE, we still attempt to make a call,
        //   since the radio will register to any available network.

        if (isEmergencyNumber
            && ((okToCallStatus == CallStatusCode.EMERGENCY_ONLY)
                || (okToCallStatus == CallStatusCode.OUT_OF_SERVICE))) {
            if (DBG) log("placeCall: Emergency number detected with status = " + okToCallStatus);
            okToCallStatus = CallStatusCode.SUCCESS;
            if (DBG) log("==> UPDATING status to: " + okToCallStatus);
        }

        /// M: For ALPS01012222 && ALPS01081839 @{
        // Add case for in the process of switch airplane mode
        // That is: framework service state is CallStatusCode.SUCCESS,but the setting system is airplane mode on.
        // For SipPhone, there is not linked between airplane mode and service state, so here need exclude SipPhone case.
        // Original Code:
        // if (okToCallStatus != CallStatusCode.SUCCESS) {
        if (okToCallStatus != CallStatusCode.SUCCESS ||
                (isInProcessForAirplaneMode(okToCallStatus) && !(phone instanceof SipPhone))) {
        /// @}
            // If this is an emergency call, launch the EmergencyCallHelperService
            // to turn on the radio and retry the call.
            Log.i(TAG, "placeCall: isInProcessForAirplaneMode : " + isInProcessForAirplaneMode(okToCallStatus));
            if (isEmergencyNumber && (
                    okToCallStatus == CallStatusCode.POWER_OFF || isInProcessForAirplaneMode(okToCallStatus))) {
                /// @}
                Log.i(TAG, "placeCall: Trying to make emergency call while POWER_OFF!");

                // If needed, lazily instantiate an EmergencyCallHelper instance.
                synchronized (this) {
                    if (mEmergencyCallHelper == null) {
                        mEmergencyCallHelper = new EmergencyCallHelper(this);
                    }
                }

                // ...and kick off the "emergency call from airplane mode" sequence.
                mEmergencyCallHelper.startEmergencyCallFromAirplaneModeSequence(number);

                // Finally, return CallStatusCode.SUCCESS right now so
                // that the in-call UI will remain visible (in order to
                // display the progress indication.)
                // TODO: or maybe it would be more clear to return a whole
                // new CallStatusCode called "TURNING_ON_RADIO" here.
                // That way, we'd update inCallUiState.progressIndication from
                // the handleOutgoingCallError() method, rather than here.
                return CallStatusCode.SUCCESS;
            } else {
                // Otherwise, just return the (non-SUCCESS) status code
                // back to our caller.
                if (DBG) log("==> placeCallInternal(): non-success status: " + okToCallStatus);

                // Log failed call.
                // Note: Normally, many of these values we gather from the Connection object but
                // since no such object is created for unconnected calls, we have to build them
                // manually.
                // TODO(santoscordon): Try to restructure code so that we can handle failure-
                // condition call logging in a single place (placeCall()) that also has access to
                // the number we attempted to dial (not placeCall()).
                mCallLogger.logCall(null /* callerInfo */, number, 0 /* presentation */,
                        Calls.OUTGOING_TYPE, System.currentTimeMillis(), 0 /* duration */, mSlot);

                return okToCallStatus;
            }
        }

        // We have a valid number, so try to actually place a call:
        // make sure we pass along the intent's URI which is a
        // reference to the contact. We may have a provider gateway
        // phone number to use for the outgoing call.
        Uri contactUri = intent.getData();

        // If a gateway is used, extract the data here and pass that into placeCall.
        final RawGatewayInfo rawGatewayInfo = mCallGatewayManager.getRawGatewayInfo(intent, number);

        /// M: For ALPS01071164. @{
        // when the call is vt call, we need check whether needs auto drop at
        // here, if needs, we should update the vt call extra flag to false and
        // use voice call replace vt call directly.
        if (FeatureOption.MTK_VT3G324M_SUPPORT && isVideoCall) {
            isVideoCall = VTCallUtils.handleVTCallAutoDropBack(mSlot, intent);
        }
        /// @}

        // Watch out: PhoneUtils.placeCall() returns one of the
        // CALL_STATUS_* constants, not a CallStatusCode enum value.
        /// M: @{
        int callStatus = placeCallInternalPlaceCall(phone, number, contactUri,
                        (isEmergencyNumber || isEmergencyIntent), rawGatewayInfo, mCallGatewayManager,
                        mSlot, isVideoCall);
        /// @}

        switch (callStatus) {
            case PhoneUtils.CALL_STATUS_DIALED:
                if (VDBG) log("placeCall: PhoneUtils.placeCall() succeeded for regular call '"
                             + number + "'.");

                /// M:@{
                // original code:
                // boolean voicemailUriSpecified = scheme != null && scheme.equals("voicemail");
                boolean voicemailUriSpecified = PhoneUtils.isVoicemailNumber(number, mSlot, phone);
                /// @}

                // Check for an obscure ECM-related scenario: If the phone
                // is currently in ECM (Emergency callback mode) and we
                // dial a non-emergency number, that automatically
                // *cancels* ECM.  So warn the user about it.
                // (See InCallScreen.showExitingECMDialog() for more info.)
                boolean exitedEcm = false;
                /// M: @{
                Phone selectedPhone = getPhoneForCheckExitedEcm(phone, mSlot);
                /// @}
                if (PhoneUtils.isPhoneInEcm(selectedPhone) && !isEmergencyNumber) {
                    Log.i(TAG, "About to exit ECM because of an outgoing non-emergency call");
                    exitedEcm = true;  // this will cause us to return EXITED_ECM from this method
                }

                if (selectedPhone.getPhoneType() == PhoneConstants.PHONE_TYPE_CDMA) {
                    // Start the timer for 3 Way CallerInfo
                    if (mApp.cdmaPhoneCallState.getCurrentCallState()
                            == CdmaPhoneCallState.PhoneCallState.THRWAY_ACTIVE) {
                        //Unmute for the second MO call
                        PhoneUtils.setMute(false);

                        // This is a "CDMA 3-way call", which means that you're dialing a
                        // 2nd outgoing call while a previous call is already in progress.
                        //
                        // Due to the limitations of CDMA this call doesn't actually go
                        // through the DIALING/ALERTING states, so we can't tell for sure
                        // when (or if) it's actually answered.  But we want to show
                        // *some* indication of what's going on in the UI, so we "fake it"
                        // by displaying the "Dialing" state for 3 seconds.

                        // Set the mThreeWayCallOrigStateDialing state to true
                        mApp.cdmaPhoneCallState.setThreeWayCallOrigState(true);

                        // Schedule the "Dialing" indication to be taken down in 3 seconds:
                        sendEmptyMessageDelayed(THREEWAY_CALLERINFO_DISPLAY_DONE,
                                                THREEWAY_CALLERINFO_DISPLAY_TIME);
                    }
                }

                // Success!
                if (exitedEcm) {
                    return CallStatusCode.EXITED_ECM;
                } else {
                    return CallStatusCode.SUCCESS;
                }

            case PhoneUtils.CALL_STATUS_DIALED_MMI:
                if (DBG) log("placeCall: specified number was an MMI code: '" + number + "'.");
                // The passed-in number was an MMI code, not a regular phone number!
                // This isn't really a failure; the Dialer may have deliberately
                // fired an ACTION_CALL intent to dial an MMI code, like for a
                // USSD call.
                //
                // Presumably an MMI_INITIATE message will come in shortly
                // (and we'll bring up the "MMI Started" dialog), or else
                // an MMI_COMPLETE will come in (which will take us to a
                // different Activity; see PhoneUtils.displayMMIComplete()).

                /// M: for ALPS00563881 @{
                // when add a new call, we will set mute true, when the new call dial successfully,
                // we will receive phone_state_change message( in PhoneUtils),
                // and then we will restore mute.
                //
                // but ,when dial MMI code (such as dial "2"), the message
                // Phone_state_change will not be sent, so we should restore mute state here.
                //
                // MTK add
                PhoneUtils.restoreMuteState();
                /// @}
                return CallStatusCode.DIALED_MMI;

            case Constants.CALL_STATUS_FAILED:
                Log.w(TAG, "placeCall: PhoneUtils.placeCall() FAILED for number '"
                      + number + "'.");
                // We couldn't successfully place the call; there was some
                // failure in the telephony layer.

                // Log failed call.
                mCallLogger.logCall(null /* callerInfo */, number, 0 /* presentation */,
                        Calls.OUTGOING_TYPE, System.currentTimeMillis(), 0 /* duration */, mSlot);

                return CallStatusCode.CALL_FAILED;

            case Constants.CALL_STATUS_DROP_VOICECALL:
                Log.w(TAG, "DROP_VOICECALL:" + number);
                return CallStatusCode.DROP_VOICECALL;

            default:
                Log.wtf(TAG, "placeCall: unknown callStatus " + callStatus
                        + " from PhoneUtils.placeCall() for number '" + number + "'.");
                return CallStatusCode.SUCCESS;  // Try to continue anyway...
        }
    }

    /**
     * Checks the current ServiceState to make sure it's OK
     * to try making an outgoing call to the specified number.
     *
     * @return CallStatusCode.SUCCESS if it's OK to try calling the specified
     *    number.  If not, like if the radio is powered off or we have no
     *    signal, return one of the other CallStatusCode codes indicating what
     *    the problem is.
     */
    private CallStatusCode checkIfOkToInitiateOutgoingCall(int state) {
        if (VDBG) log("checkIfOkToInitiateOutgoingCall: ServiceState = " + state);

        switch (state) {
            case ServiceState.STATE_IN_SERVICE:
                // Normal operation.  It's OK to make outgoing calls.
                return CallStatusCode.SUCCESS;

            case ServiceState.STATE_POWER_OFF:
                // Radio is explictly powered off.
                return CallStatusCode.POWER_OFF;

            case ServiceState.STATE_EMERGENCY_ONLY:
                // The phone is registered, but locked. Only emergency
                // numbers are allowed.
                // Note that as of Android 2.0 at least, the telephony layer
                // does not actually use ServiceState.STATE_EMERGENCY_ONLY,
                // mainly since there's no guarantee that the radio/RIL can
                // make this distinction.  So in practice the
                // CallStatusCode.EMERGENCY_ONLY state and the string
                // "incall_error_emergency_only" are totally unused.
                return CallStatusCode.EMERGENCY_ONLY;

            case ServiceState.STATE_OUT_OF_SERVICE:
                // No network connection.
                return CallStatusCode.OUT_OF_SERVICE;

            default:
                throw new IllegalStateException("Unexpected ServiceState: " + state);
        }
    }



    /**
     * Handles the various error conditions that can occur when initiating
     * an outgoing call.
     *
     * Most error conditions are "handled" by simply displaying an error
     * message to the user.  This is accomplished by setting the
     * inCallUiState pending call status code flag, which tells the
     * InCallScreen to display an appropriate message to the user when the
     * in-call UI comes to the foreground.
     *
     * @param status one of the CallStatusCode error codes.
     */
    private void handleOutgoingCallError(CallStatusCode status) {
        if (DBG) log("handleOutgoingCallError(): status = " + status);
        final Intent intent = new Intent(mApp, ErrorDialogActivity.class);
        // In most cases we simply want to have the InCallScreen display
        // an appropriate error dialog, so we simply copy the specified
        // status code into the InCallUiState "pending call status code"
        // field.  (See InCallScreen.showStatusIndication() for the next
        // step of the sequence.)
        int errorMessageId = -1;
        switch (status) {
            case SUCCESS:
                // This case shouldn't happen; you're only supposed to call
                // handleOutgoingCallError() if there was actually an error!
                Log.wtf(TAG, "handleOutgoingCallError: SUCCESS isn't an error");
                break;

            case CALL_FAILED:
                // We couldn't successfully place the call; there was some
                // failure in the telephony layer.
                // TODO: Need UI spec for this failure case; for now just
                // show a generic error.
                errorMessageId = R.string.incall_error_call_failed;
                break;

            case POWER_OFF:
                // Radio is explictly powered off, presumably because the
                // device is in airplane mode.
                //
                // TODO: For now this UI is ultra-simple: we simply display
                // a message telling the user to turn off airplane mode.
                // But it might be nicer for the dialog to offer the option
                // to turn the radio on right there (and automatically retry
                // the call once network registration is complete.)
                errorMessageId = R.string.incall_error_power_off;
                break;

            case EMERGENCY_ONLY:
                // Only emergency numbers are allowed, but we tried to dial
                // a non-emergency number.
                // (This state is currently unused; see comments above.)
                errorMessageId = R.string.incall_error_emergency_only;
                break;

            case OUT_OF_SERVICE:
                // No network connection.
                errorMessageId = R.string.incall_error_out_of_service;
                break;

            case NO_PHONE_NUMBER_SUPPLIED:
                // The supplied Intent didn't contain a valid phone number.
                // (This is rare and should only ever happen with broken
                // 3rd-party apps.) For now just show a generic error.
                errorMessageId = R.string.incall_error_no_phone_number_supplied;
                break;
            case VOICEMAIL_NUMBER_MISSING:
                // Bring up the "Missing Voicemail Number" dialog, which
                // will ultimately take us to some other Activity (or else
                // just bail out of this activity.)

                // Send a request to the InCallScreen to display the
                // "voicemail missing" dialog when it (the InCallScreen)
                // comes to the foreground.
                intent.putExtra(ErrorDialogActivity.SHOW_MISSING_VOICEMAIL_NO_DIALOG_EXTRA, true);
                break;

            case DIALED_MMI:
                // Our initial phone number was actually an MMI sequence.
                // There's no real "error" here, but we do bring up the
                // a Toast (as requested of the New UI paradigm).
                //
                // In-call MMIs do not trigger the normal MMI Initiate
                // Notifications, so we should notify the user here.
                // Otherwise, the code in PhoneUtils.java should handle
                // user notifications in the form of Toasts or Dialogs.
                //
                // TODO: Rather than launching a toast from here, it would
                // be cleaner to just set a pending call status code here,
                // and then let the InCallScreen display the toast...
                final Intent mmiIntent = new Intent(mApp, MMIDialogActivity.class);
                /// M: ALPS01276588 indictor which slot to send ussd @{
                mmiIntent.putExtra(Constants.EXTRA_SLOT_ID, mSlot);
                /// @}
                mmiIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK |
                        Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
                mApp.startActivity(mmiIntent);
                return;
            default:
                Log.wtf(TAG, "handleOutgoingCallError: unexpected status code " + status);
                // Show a generic "call failed" error.
                errorMessageId = R.string.incall_error_call_failed;
                break;
        }
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
        if (errorMessageId != -1) {
            intent.putExtra(ErrorDialogActivity.ERROR_MESSAGE_ID_EXTRA, errorMessageId);
        }
        mApp.startActivity(intent);
    }

    /**
     * Checks the current outgoing call to see if it's an OTASP call (the
     * "activation" call used to provision CDMA devices).  If so, do any
     * necessary OTASP-specific setup before actually placing the call.
     */
    private void checkForOtaspCall(Intent intent) {
        if (OtaUtils.isOtaspCallIntent(intent)) {
            Log.i(TAG, "checkForOtaspCall: handling OTASP intent! " + intent);

            // ("OTASP-specific setup" basically means creating and initializing
            // the OtaUtils instance.  Note that this setup needs to be here in
            // the CallController.placeCall() sequence, *not* in
            // OtaUtils.startInteractiveOtasp(), since it's also possible to
            // start an OTASP call by manually dialing "*228" (in which case
            // OtaUtils.startInteractiveOtasp() never gets run at all.)
            OtaUtils.setupOtaspCall(intent);
        } else {
            if (DBG) log("checkForOtaspCall: not an OTASP call.");
        }
    }


    //
    // Debugging
    //

    private static void log(String msg) {
        Log.d(TAG, msg);
    }


    /** --------------- MTK --------------------*/
    private static final int PHONE_STATE_CHANGED = 10;
    private static final int RESEND_ECC_CALL = 100;

    private Intent mCallIntent = null;
    private int mSlot = GeminiUtils.getDefaultSlot();

    private boolean handleMessageMtk(Message msg) {
        log("handleMessageMtk: " + msg);
        switch (msg.what) {
            case PHONE_STATE_CHANGED:
                PhoneConstants.State state = mCM.getState();
                log("handleMessageMtk: PHONE_STATE_CHANGED with state = " + state);
                if (state == PhoneConstants.State.IDLE) {
                    CallManagerWrapper.unregisterForDisconnect(this);

                    /// M: For ALPS01003330 @{
                    // place an ECC with 1A1H, may receive two disconnect messages before we unregister it.
                    this.removeMessages(PHONE_STATE_CHANGED);
                    /// @}
                    log("handleMessageMtk: re-send the ecc call!");
                    // for ALPS00565730; leave 200ms to make sure
                    // InCallScreen.onDisconnect() finish.
                    // or moveTaskToBack() may cause InCallScreen's disappear,
                    // see ALPS00565730's case.
                    Message message = obtainMessage(RESEND_ECC_CALL);
                    sendMessageDelayed(message, 200);
                } else if (state == PhoneConstants.State.OFFHOOK) {
                    /// For ALPS00976422 if the call which will be send is
                    //  the same as the call active, we will don't place again.
                    log("handleMessageMtk: PHONE_STATE OFFHOOK, isCallIntentNumberSameWithFgCall() "
                            + isCallIntentNumberSameWithFgCall());
                    if (isCallIntentNumberSameWithFgCall()) {
                        mCallIntent = null;
                    }
                } else {
                    log("handleMessageMtk: PHONE_STATE_CHANGED continue waiting...");
                }
                return true;

                /// M: for ALPS00565730 @{
            case RESEND_ECC_CALL:
                if (mCallIntent != null) {
                    log("handleMessageMtk: real re-send the ecc call!");
                    placeCall(mCallIntent);
                }
                return true;
                /// @}

            default:
                log("handleMessageMtk: not mtk msg.");
                break;
        }
        return false;
    }

    /**
     * Before send the call request to FW, first check if there is normal call
     * exist, if there is/are normal call exist, disconnect it.
     *
     * @param intent
     * @param number
     * @return
     */
    private boolean placeCallPreCheck(Intent intent, String number) {
        boolean isEccCall = PhoneUtils.isEmergencyNumber(number);

        PhoneConstants.State state = mCM.getState();
        if (isEccCall && state != PhoneConstants.State.IDLE) {
            Call fgCall = mCM.getActiveFgCall();
            Connection connection = fgCall.getEarliestConnection();
            String activeCallAddress = connection != null ? connection.getAddress() : null;

            Call bgCall = mCM.getFirstActiveBgCall();
            Connection bgconnection = bgCall.getEarliestConnection();
            String bgCallAddress = bgconnection != null ? bgconnection.getAddress() : null;

            // The active call (fg|bg call) isn't ecc, disconnect it.
            if (fgCall != null && fgCall.getState().isAlive()
                    && (!PhoneUtils.isEccCall(fgCall) || PhoneNumberUtils.isSpecialEmergencyNumber(activeCallAddress))
                    || bgCall != null && bgCall.getState().isAlive()
                    && (!PhoneUtils.isEccCall(bgCall) || PhoneNumberUtils.isSpecialEmergencyNumber(bgCallAddress))) {
                CallManagerWrapper.registerForDisconnect(this, PHONE_STATE_CHANGED);
                mCallIntent = intent;
                try {
                    mCM.hangupAllEx();
                    log("placeCallPreCheck: Waiting for disconnect exist calls.");
                    return false;
                } catch (CallStateException e) {
                    log("placeCallPreCheck: catch exception = " + e);
                }
            } else {
                log("placeCallPreCheck: Let's FW reject the call request.");
            }
        }
        return true;
    }

    private CallStatusCode checkIfOkToInitiateOutgoingCall(int state, int slot) {
        log("checkIfOkToInitiateOutgoingCall, state = " + state + " slot = " + slot);
        if (GeminiUtils.isGeminiSupport() && GeminiUtils.isValidSlot(slot)) {
            Phone phone = PhoneGlobals.getInstance().phone;
            if (!DualTalkUtils.isSupportDualTalk()) {
                /// M:Gemini+ @{
                final int[] geminiSlots = GeminiUtils.getSlots();
                for (int gs : geminiSlots) {
                    if (gs != slot && PhoneWrapper.getState(phone, gs) != PhoneConstants.State.IDLE) {
                        return CallStatusCode.CALL_FAILED;
                    }
                }
                /// @}
            }

            if (DualTalkUtils.isSupportDualTalk()) {
                if (mApp.notifier.mDualTalk == null) {
                    mApp.notifier.mDualTalk = DualTalkUtils.getInstance();
                }
                if (!mApp.notifier.mDualTalk.isPhoneCallAllowed(slot)) {
                    return CallStatusCode.CALL_FAILED;
                }
            }

            final int realState = PhoneWrapper.getServiceState(phone, slot).getState();
            log("realState = " + realState);
            return checkIfOkToInitiateOutgoingCall(realState);
        } else {
            return checkIfOkToInitiateOutgoingCall(state);
        }
    }

    /**
     * original code: 
     * int callStatus = PhoneUtils.placeCall(mApp, phone, number,
     *                contactUri, (isEmergencyNumber || isEmergencyIntent),
     *                inCallUiState.providerGatewayUri);
     *
     * @param phone
     * @param number
     * @param contactUri
     * @param isEmergencyCall
     * @param gatewayUri
     * @param slot
     * @param isVideoCall
     * @return
     */
    private int placeCallInternalPlaceCall(Phone phone, String number, Uri contactUri,
            boolean isEmergencyCall, RawGatewayInfo rawGatewayInfo, CallGatewayManager callGatewayManager, int slot, boolean isVideoCall) {

        if (FeatureOption.MTK_VT3G324M_SUPPORT && isVideoCall) {
            return VTCallUtils.placeVTCall(phone, number, contactUri, slot);
        } else if (GeminiUtils.isGeminiSupport()) {
            return PhoneUtils.placeCallGemini(mApp, phone, number, contactUri, isEmergencyCall,
                    rawGatewayInfo, callGatewayManager, slot);
        }
        return PhoneUtils.placeCall(mApp, phone, number, contactUri, isEmergencyCall, rawGatewayInfo, callGatewayManager);
    }

    /**
     * @param phone
     * @param slotId
     * @return
     */
    private Phone getPhoneForCheckExitedEcm(Phone phone, int slotId) {
        // ALPS00473367
        // We don't see the MTKCallManger's code, so don't make sure how the
        // Phone needed when call the dial. so we get the selectedPhone to check
        // if it supports ECM
        if (DualTalkUtils.isEvdoDTSupport()) {
            PhoneWrapper.getPhoneBySlotId(phone, slotId);
        }
        return phone;
    }

    /**
     * For the tablet, open the speaker on ASAP when setup the call
     */
    private void forceSpeakerOnByNeeded() {
        if (FeatureOption.MTK_TB_APP_CALL_FORCE_SPEAKER_ON) {
            Log.d("MTK_TB_APP_CALL_FORCE_SPEAKER_ON", "forceSpeakerOn is true");
            if (!mApp.getWiredHeadsetManager().isHeadsetPlugged()
                && !(mApp.getBluetoothManager().isBluetoothHeadsetAudioOn())) {
                //Only force the speaker ON while not video call and speaker is not ON
                if (!VTCallUtils.isVideoCall(mCM.getActiveFgCall())
                    && !PhoneUtils.isSpeakerOn(mApp)) {
                    Log.d("MTK_TB_APP_CALL_FORCE_SPEAKER_ON", "PhoneUtils.turnOnSpeaker");
                    PhoneUtils.turnOnSpeaker(mApp, true, true, true);
                }
            }
        }
    }

    /**
     * Whether the numer that gets from the specified intent is empty.
     *
     * @param intent used to get the number.
     * @return if the number is empty will return true, else false.
     */
    public boolean isEmptyNumber(Intent intent) {
        try {
            String initNumber = PhoneUtils.getInitialNumber(intent);
            if (TextUtils.isEmpty(initNumber)) {
                log("placeCall: called with empty number");
                return true;
            }
        } catch (PhoneUtils.VoiceMailNumberMissingException ex) {
            log("placeCall: throwed VoiceMailNumberMissingException");
        }
        return false;
    }

    /**
     * Decide whether the fg call's number is the same with mCallIntent's
     * @return true if same, otherwise false
     */
    private boolean isCallIntentNumberSameWithFgCall() {
        Call activeFgCall = mCM.getActiveFgCall();
        if (activeFgCall != null && activeFgCall.getLatestConnection() != null) {
            log("activeFgCall != null && activeFgCall.getLatestConnection() != null");
            String fgCallNumber = activeFgCall.getLatestConnection().getAddress();
            if (fgCallNumber != null) {
                if (fgCallNumber.equalsIgnoreCase(
                        PhoneNumberUtils.getNumberFromIntent(mCallIntent, mApp))) {
                    return true;
                }
            } else {
                log("isCallIntentNumberSameWithFgCall fgCallNumber == null");
            }
        }
        return false;
    }

    /**
     * Return whether the setting system's value is same with Framework state
     * @param serviceState this is the service get from framework
     * @return true is the two value is same else return false
     */
    private boolean isInProcessForAirplaneMode(CallStatusCode serviceState) {
        return (serviceState == CallStatusCode.SUCCESS && PhoneUtils.isOnAirplaneMode()
                || serviceState == CallStatusCode.POWER_OFF && !PhoneUtils.isOnAirplaneMode());
    }

    private void handleDropVoiceCall(CallStatusCode status, String number, int slotId) {
        if (DBG) log("handleDropVoiceCall(): status / number / slotId = " + status + " / " + number + " / " + slotId);
        final Intent intent = new Intent(mApp, ErrorDialogActivity.class);
        int errorMessageId = -1;
        switch (status) {
            case DROP_VOICECALL:
                errorMessageId = R.string.callFailed_dsac_vt_out_of_3G_yourphone;
                break;
            default:
                Log.wtf(TAG, "handleDropVoiceCall: only status of DROP_VOICECALL can call this function.");
                break;
        }
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
        if (errorMessageId != -1) {
            intent.putExtra(ErrorDialogActivity.ERROR_MESSAGE_ID_EXTRA, errorMessageId);
        }
        intent.putExtra(ErrorDialogActivity.SHOW_VT_DROP_VOICECALL_EXTRA, true);
        intent.putExtra(ErrorDialogActivity.DROP_VOICECALL_NUMBER_EXTRA, number);
        intent.putExtra(ErrorDialogActivity.DROP_VOICECALL_SLOT_EXTRA, slotId);
        mApp.startActivity(intent);
    }
   
}
