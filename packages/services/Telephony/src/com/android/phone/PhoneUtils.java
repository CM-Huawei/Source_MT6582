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

import android.app.ActivityManagerNative;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.bluetooth.IBluetoothHeadsetPhone;
import android.content.ActivityNotFoundException;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.content.ComponentName;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.content.ServiceConnection;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.media.AudioManager;
import android.net.Uri;
import android.net.sip.SipManager;
import android.os.AsyncResult;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.StatFs;
import android.os.SystemClock;
import android.os.SystemProperties;
import android.os.storage.StorageManager;
import android.os.storage.StorageVolume;
import android.provider.Settings;
import android.provider.ContactsContract.Contacts;
import android.provider.Settings.SettingNotFoundException;
import android.telephony.PhoneNumberUtils;
import android.text.InputFilter;
import android.text.TextUtils;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.ListAdapter;
import android.widget.Toast;
import android.widget.TextView;

import com.android.internal.telephony.Call;
import com.android.internal.telephony.CallManager;
import com.android.internal.telephony.CallStateException;
import com.android.internal.telephony.CallerInfo;
import com.android.internal.telephony.CallerInfoAsyncQuery;
import com.android.internal.telephony.Connection;
import com.android.internal.telephony.MmiCode;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.TelephonyCapabilities;
import com.android.internal.telephony.TelephonyProperties;
import com.android.internal.telephony.cdma.CdmaConnection;
import com.android.internal.telephony.gemini.*;
import com.android.internal.telephony.sip.SipPhone;
import com.android.phone.CallGatewayManager.RawGatewayInfo;
import com.android.services.telephony.common.AudioMode;
import com.mediatek.common.dm.DmAgent;
import com.mediatek.phone.DualTalkUtils;
import com.mediatek.phone.ext.ExtensionManager;
import com.mediatek.phone.gemini.GeminiUtils;
import com.mediatek.phone.PhoneFeatureConstants.FeatureOption;
import com.mediatek.phone.BlackListManager;
import com.mediatek.phone.CallPickerAdapter;
import com.mediatek.phone.PhoneLog;
import com.mediatek.phone.PhoneRaiseDetector;
import com.mediatek.phone.SIMInfoWrapper;
import com.mediatek.phone.UssdAlertActivity;
import com.mediatek.phone.vt.VTCallUtils;
import com.mediatek.phone.wrapper.CallManagerWrapper;
import com.mediatek.phone.wrapper.PhoneWrapper;
import com.mediatek.storage.StorageManagerEx;
import com.mediatek.telephony.SimInfoManager.SimInfoRecord;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.ConcurrentModificationException;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;

import com.mediatek.common.MediatekClassFactory;
import com.mediatek.common.telephony.IServiceStateExt;

/**
 * Misc utilities for the Phone app.
 */
public class PhoneUtils {
    private static final String LOG_TAG = "PhoneUtils";
//Google: private static final boolean DBG = (PhoneGlobals.DBG_LEVEL >= 2);
    private static final boolean DBG = true;

    /** Control stack trace for Audio Mode settings */
    private static final boolean DBG_SETAUDIOMODE_STACK = false;

    /** Identifier for the "Add Call" intent extra. */
    static final String ADD_CALL_MODE_KEY = "add_call_mode";

    // Return codes from placeCall()
    public static final int CALL_STATUS_DIALED = 0;  // The number was successfully dialed
    public static final int CALL_STATUS_DIALED_MMI = 1;  // The specified number was an MMI code
    public static final int CALL_STATUS_FAILED = 2;  // The call failed

    // State of the Phone's audio modes
    // Each state can move to the other states, but within the state only certain
    //  transitions for AudioManager.setMode() are allowed.
    public static final int AUDIO_IDLE = 0;  /** audio behaviour at phone idle */
    public static final int AUDIO_RINGING = 1;  /** audio behaviour while ringing */
    public static final int AUDIO_OFFHOOK = 2;  /** audio behaviour while in call. */
    // USSD string length for MMI operations
    static final int MIN_USSD_LEN = 1;
    static final int MAX_USSD_LEN = 160;

    /** Speaker state, persisting between wired headset connection events */
    private static boolean sIsSpeakerEnabled = false;

    /** Hash table to store mute (Boolean) values based upon the connection.*/
    private static Hashtable<Connection, Boolean> sConnectionMuteTable =
        new Hashtable<Connection, Boolean>();

    /** Static handler for the connection/mute tracking */
    private static ConnectionHandler mConnectionHandler;

    /** Phone state changed event*/
    private static final int PHONE_STATE_CHANGED = -1;

    /** check status then decide whether answerCall */
    private static final int MSG_CHECK_STATUS_ANSWERCALL = 100;

    /** poll phone DISCONNECTING status interval */
    private static final int DISCONNECTING_POLLING_INTERVAL_MS = 200;

    /** poll phone DISCONNECTING status times limit */
    private static final int DISCONNECTING_POLLING_TIMES_LIMIT = 8;

    /** Define for not a special CNAP string */
    private static final int CNAP_SPECIAL_CASE_NO = -1;

    /** Noise suppression status as selected by user */
    private static boolean sIsNoiseSuppressionEnabled = true;

    private static class FgRingCalls {
        private Call fgCall;
        private Call ringing;
        public FgRingCalls(Call fg, Call ring) {
            fgCall = fg;
            ringing = ring;
        }
    }

    /**
     * Handler that tracks the connections and updates the value of the
     * Mute settings for each connection as needed.
     */
    private static class ConnectionHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            if (handleMessageMtk(msg)){
                return;
            }

            switch (msg.what) {
                case MSG_CHECK_STATUS_ANSWERCALL:
                    FgRingCalls frC = (FgRingCalls) msg.obj;
                    // wait for finishing disconnecting
                    // before check the ringing call state
                    if ((frC.fgCall != null) &&
                        (frC.fgCall.getState() == Call.State.DISCONNECTING) &&
                        (msg.arg1 < DISCONNECTING_POLLING_TIMES_LIMIT)) {
                        Message retryMsg =
                            mConnectionHandler.obtainMessage(MSG_CHECK_STATUS_ANSWERCALL);
                        retryMsg.arg1 = 1 + msg.arg1;
                        retryMsg.obj = msg.obj;
                        mConnectionHandler.sendMessageDelayed(retryMsg,
                            DISCONNECTING_POLLING_INTERVAL_MS);
                    // since hangupActiveCall() also accepts the ringing call
                    // check if the ringing call was already answered or not
                    // only answer it when the call still is ringing
                    } else if (frC.ringing.isRinging()) {
                        if (msg.arg1 == DISCONNECTING_POLLING_TIMES_LIMIT) {
                            Log.e(LOG_TAG, "DISCONNECTING time out");
                        }
                        answerCall(frC.ringing);
                    }
                    break;
                case PHONE_STATE_CHANGED:
                    if (DBG) log("ConnectionHandler: updating mute state for each connection");
                    AsyncResult ar = (AsyncResult) msg.obj;
                    CallManager cm = (CallManager) ar.userObj;

                    // update the foreground connections, if there are new connections.
                    // Have to get all foreground calls instead of the active one
                    // because there may two foreground calls co-exist in shore period
                    // (a racing condition based on which phone changes firstly)
                    // Otherwise the connection may get deleted.
                    List<Connection> fgConnections = new ArrayList<Connection>();
                    for (Call fgCall : cm.getForegroundCalls()) {
                        if (!fgCall.isIdle()) {
                            fgConnections.addAll(fgCall.getConnections());
                        }
                    }
                    for (Connection cn : fgConnections) {
                        if (sConnectionMuteTable.get(cn) == null) {
                            sConnectionMuteTable.put(cn, Boolean.FALSE);
                        }
                    }

                    // mute is connection based operation, we need loop over
                    // all background calls instead of the first one to update
                    // the background connections, if there are new connections.
                    List<Connection> bgConnections = new ArrayList<Connection>();
                    for (Call bgCall : cm.getBackgroundCalls()) {
                        if (!bgCall.isIdle()) {
                            bgConnections.addAll(bgCall.getConnections());
                        }
                    }
                    for (Connection cn : bgConnections) {
                        if (sConnectionMuteTable.get(cn) == null) {
                          sConnectionMuteTable.put(cn, Boolean.FALSE);
                        }
                    }

                    // Check to see if there are any lingering connections here
                    // (disconnected connections), use old-school iterators to avoid
                    // concurrent modification exceptions.
                    Connection cn;
                    for (Iterator<Connection> cnlist = sConnectionMuteTable.keySet().iterator();
                            cnlist.hasNext();) {
                        cn = cnlist.next();
                        if (!fgConnections.contains(cn) && !bgConnections.contains(cn)) {
                            if (DBG) log("connection '" + cn + "' not accounted for, removing.");
                            cnlist.remove();
                        }
                    }

                    // Restore the mute state of the foreground call if we're not IDLE,
                    // otherwise just clear the mute state. This is really saying that
                    // as long as there is one or more connections, we should update
                    // the mute state with the earliest connection on the foreground
                    // call, and that with no connections, we should be back to a
                    // non-mute state.
                    if (cm.getState() != PhoneConstants.State.IDLE) {
                        restoreMuteState();
                    } else {
                        setMuteInternal(cm.getFgPhone(), false);
                    }

                    break;
            }
        }
    }

    /**
     * Register the ConnectionHandler with the phone, to receive connection events
     */
    public static void initializeConnectionHandler(CallManager cm) {
        if (mConnectionHandler == null) {
            mConnectionHandler = new ConnectionHandler();
        }

        /// M: @{
        // Original code:
        // cm.registerForPreciseCallStateChanged(mConnectionHandler, PHONE_STATE_CHANGED, cm);
        CallManagerWrapper.registerForPreciseCallStateChanged(mConnectionHandler,
                PHONE_STATE_CHANGED, cm);
        /// @}
    }

    /** This class is never instantiated. */
    private PhoneUtils() {
    }

    /**
     * Answer the currently-ringing call.
     *
     * @return true if we answered the call, or false if there wasn't
     *         actually a ringing incoming call, or some other error occurred.
     *
     * @see #answerAndEndHolding(CallManager, Call)
     * @see #answerAndEndActive(CallManager, Call)
     */
    /* package */ static boolean answerCall(Call ringingCall) {
        log("answerCall(" + ringingCall + ")...");
        final PhoneGlobals app = PhoneGlobals.getInstance();
        final CallNotifier notifier = app.notifier;

        // If the ringer is currently ringing and/or vibrating, stop it
        // right now (before actually answering the call.)
        notifier.silenceRinger();

        /// M: update audio control state
        PhoneUtils.setAudioControlState(PhoneUtils.AUDIO_OFFHOOK);

        final Phone phone = ringingCall.getPhone();
        final boolean phoneIsCdma = (phone.getPhoneType() == PhoneConstants.PHONE_TYPE_CDMA);
        boolean answered = false;
        IBluetoothHeadsetPhone btPhone = null;

        if (phoneIsCdma) {
            // Stop any signalInfo tone being played when a Call waiting gets answered
            if (ringingCall.getState() == Call.State.WAITING) {
                notifier.stopSignalInfoTone();
            }
        }

        if (ringingCall != null && ringingCall.isRinging()) {
            if (DBG) log("answerCall: call state = " + ringingCall.getState());
            try {
                if (phoneIsCdma) {
                    if (app.cdmaPhoneCallState.getCurrentCallState()
                            == CdmaPhoneCallState.PhoneCallState.IDLE) {
                        // This is the FIRST incoming call being answered.
                        // Set the Phone Call State to SINGLE_ACTIVE
                        app.cdmaPhoneCallState.setCurrentCallState(
                                CdmaPhoneCallState.PhoneCallState.SINGLE_ACTIVE);
                    } else {
                        // This is the CALL WAITING call being answered.
                        // Set the Phone Call State to CONF_CALL
                        app.cdmaPhoneCallState.setCurrentCallState(
                                CdmaPhoneCallState.PhoneCallState.CONF_CALL);
                        // Enable "Add Call" option after answering a Call Waiting as the user
                        // should be allowed to add another call in case one of the parties
                        // drops off
                        app.cdmaPhoneCallState.setAddCallMenuStateAfterCallWaiting(true);

                        // If a BluetoothPhoneService is valid we need to set the second call state
                        // so that the Bluetooth client can update the Call state correctly when
                        // a call waiting is answered from the Phone.
                        btPhone = app.getBluetoothPhoneService();
                        if (btPhone != null) {
                            try {
                                btPhone.cdmaSetSecondCallState(true);
                            } catch (RemoteException e) {
                                Log.e(LOG_TAG, Log.getStackTraceString(new Throwable()));
                            }
                        }
                    }
                }

                final boolean isRealIncomingCall = isRealIncomingCall(ringingCall.getState());

                //if (DBG) log("sPhone.acceptCall");
                app.mCM.acceptCall(ringingCall);
                answered = true;

                // Always reset to "unmuted" for a freshly-answered call
                /// M: delete original code
                // setMute(false);

                setAudioMode();

                // Check is phone in any dock, and turn on speaker accordingly
                final boolean speakerActivated = activateSpeakerIfDocked(phone);

                final BluetoothManager btManager = app.getBluetoothManager();
                if (!forceSpeakerOn(app, true)) {
                /// @}

                // When answering a phone call, the user will move the phone near to her/his ear
                // and start conversation, without checking its speaker status. If some other
                // application turned on the speaker mode before the call and didn't turn it off,
                // Phone app would need to be responsible for the speaker phone.
                // Here, we turn off the speaker if
                // - the phone call is the first in-coming call,
                // - we did not activate speaker by ourselves during the process above, and
                // - Bluetooth headset is not in use.
                if (isRealIncomingCall && !speakerActivated && isSpeakerOn(app)
                        && !btManager.isBluetoothHeadsetAudioOn()
                        && !VTCallUtils.isVideoCall(ringingCall) /** M: VT */) {
                    // This is not an error but might cause users' confusion. Add log just in case.
                    Log.i(LOG_TAG, "Forcing speaker off due to new incoming call...");
                    turnOnSpeaker(app, false, true);
                }
                /// M: @{
                }
                /// @}
            } catch (CallStateException ex) {
                Log.w(LOG_TAG, "answerCall: caught " + ex, ex);

                if (phoneIsCdma) {
                    // restore the cdmaPhoneCallState and btPhone.cdmaSetSecondCallState:
                    app.cdmaPhoneCallState.setCurrentCallState(
                            app.cdmaPhoneCallState.getPreviousCallState());
                    if (btPhone != null) {
                        try {
                            btPhone.cdmaSetSecondCallState(false);
                        } catch (RemoteException e) {
                            Log.e(LOG_TAG, Log.getStackTraceString(new Throwable()));
                        }
                    }
                }
            }
        }
        return answered;
    }

    /**
     * Hangs up all active calls.
     */
    static void hangupAllCalls(CallManager cm) {
        final Call ringing = cm.getFirstActiveRingingCall();
        final Call fg = cm.getActiveFgCall();
        final Call bg = cm.getFirstActiveBgCall();

        // We go in reverse order, BG->FG->RINGING because hanging up a ringing call or an active
        // call can move a bg call to a fg call which would force us to loop over each call
        // several times.  This ordering works best to ensure we dont have any more calls.
        if (bg != null && !bg.isIdle()) {
            hangup(bg);
        }
        if (fg != null && !fg.isIdle()) {
            hangup(fg);
        }
        if (ringing != null && !ringing.isIdle()) {
            hangupRingingCall(fg);
        }
    }

    /*
     * Smart "hang up" helper method which hangs up exactly one connection,
     * based on the current Phone state, as follows:
     * <ul>
     * <li>If there's a ringing call, hang that up.
     * <li>Else if there's a foreground call, hang that up.
     * <li>Else if there's a background call, hang that up.
     * <li>Otherwise do nothing.
     * </ul>
     * @return true if we successfully hung up, or false
     *              if there were no active calls at all.
     */
    public static boolean hangup(CallManager cm) {
        boolean hungup = false;
        Call ringing = null;
        Call fg = null;
        Call bg = null;
        DualTalkUtils dtUtils = null;

        if (DualTalkUtils.isSupportDualTalk()) {
            dtUtils = DualTalkUtils.getInstance();
        }

        if (DualTalkUtils.isSupportDualTalk() && dtUtils.hasMultipleRingingCall()) {
            //this can't be reached.
            ringing = dtUtils.getFirstActiveRingingCall();
            hangupForDualTalk(ringing);
            return true;
        } else if (DualTalkUtils.isSupportDualTalk() && dtUtils.hasDualHoldCallsOnly()) {
            fg = dtUtils.getFirstActiveBgCall();
            ringing = dtUtils.getFirstActiveRingingCall();
        } else if (DualTalkUtils.isSupportDualTalk() && dtUtils.isDualTalkMultipleHoldCase()) {
            fg = dtUtils.getActiveFgCall();
            ringing = dtUtils.getFirstActiveRingingCall();
        } else if (DualTalkUtils.isSupportDualTalk() && dtUtils.isCdmaAndGsmActive()) {
            //DualTalkUtils will not swap the phone in active phone list
            //TBD: consider adjust the order in active phone list.
            ringing = cm.getFirstActiveRingingCall();
            fg = dtUtils.getActiveFgCall();
            bg = dtUtils.getFirstActiveBgCall();
        } else {
            ringing = cm.getFirstActiveRingingCall();
            fg = cm.getActiveFgCall();
            bg = cm.getFirstActiveBgCall();
        }

        if (!ringing.isIdle()) {
            if (DBG) {
                log("hangup(): hanging up ringing call");
            }
            hungup = hangupRingingCall(ringing);
        } else if (!fg.isIdle() || fg.mState == Call.State.DISCONNECTING) {
            if (DBG) {
                log("hangup(): hanging up foreground call");
            }
            hungup = hangup(fg);
        } else if (!bg.isIdle() || bg.mState == Call.State.DISCONNECTING) {
            log("hangup(): hanging up background call");
            hungup = hangup(bg);
        } else {
            // No call to hang up!  This is unlikely in normal usage,
            // since the UI shouldn't be providing an "End call" button in
            // the first place.  (But it *can* happen, rarely, if an
            // active call happens to disconnect on its own right when the
            // user is trying to hang up..)
            log("hangup(): no active call to hang up");
        }
        if (DBG) log("==> hungup = " + hungup);

        return hungup;
    }

    static boolean hangupRingingCall(Call ringing) {
        if (DBG) log("hangup ringing call");
        int phoneType = ringing.getPhone().getPhoneType();
        Call.State state = ringing.getState();

        if (state == Call.State.INCOMING) {
            // Regular incoming call (with no other active calls)
            log("hangupRingingCall(): regular incoming call: hangup()");
            return hangup(ringing);
        } else if (state == Call.State.WAITING) {
            // Call-waiting: there's an incoming call, but another call is
            // already active.
            // TODO: It would be better for the telephony layer to provide
            // a "hangupWaitingCall()" API that works on all devices,
            // rather than us having to check the phone type here and do
            // the notifier.sendCdmaCallWaitingReject() hack for CDMA phones.
            if (phoneType == PhoneConstants.PHONE_TYPE_CDMA) {
                // CDMA: Ringing call and Call waiting hangup is handled differently.
                // For Call waiting we DO NOT call the conventional hangup(call) function
                // as in CDMA we just want to hangup the Call waiting connection.
                log("hangupRingingCall(): CDMA-specific call-waiting hangup");
                final CallNotifier notifier = PhoneGlobals.getInstance().notifier;
                notifier.sendCdmaCallWaitingReject();
                return true;
            } else {
                // Otherwise, the regular hangup() API works for
                // call-waiting calls too.
                log("hangupRingingCall(): call-waiting call: hangup()");
                return hangup(ringing);
            }
        } else {
            // Unexpected state: the ringing call isn't INCOMING or
            // WAITING, so there's no reason to have called
            // hangupRingingCall() in the first place.
            // (Presumably the incoming call went away at the exact moment
            // we got here, so just do nothing.)
            Log.w(LOG_TAG, "hangupRingingCall: no INCOMING or WAITING call");
            return false;
        }
    }

    static boolean hangupActiveCall(Call foreground) {
        if (DBG) log("hangup active call");
        return hangup(foreground);
    }

    static boolean hangupHoldingCall(Call background) {
        if (DBG) log("hangup holding call");
        return hangup(background);
    }

    /**
     * Used in CDMA phones to end the complete Call session
     * @param phone the Phone object.
     * @return true if *any* call was successfully hung up
     */
    static boolean hangupRingingAndActive(Phone phone) {
        boolean hungUpRingingCall = false;
        boolean hungUpFgCall = false;
        Call ringingCall = phone.getRingingCall();
        Call fgCall = phone.getForegroundCall();

        // Hang up any Ringing Call
        if (!ringingCall.isIdle()) {
            log("hangupRingingAndActive: Hang up Ringing Call");
            hungUpRingingCall = hangupRingingCall(ringingCall);
        }

        // Hang up any Active Call
        if (!fgCall.isIdle()) {
            log("hangupRingingAndActive: Hang up Foreground Call");
            hungUpFgCall = hangupActiveCall(fgCall);
        }

        return hungUpRingingCall || hungUpFgCall;
    }

    /**
     * Trivial wrapper around Call.hangup(), except that we return a
     * boolean success code rather than throwing CallStateException on
     * failure.
     *
     * @return true if the call was successfully hung up, or false
     *         if the call wasn't actually active.
     */
    public static boolean hangup(Call call) {
        try {
            CallManager cm = PhoneGlobals.getInstance().mCM;
            //Resolved for ALPS00036146
            if (call.getState() == Call.State.ACTIVE && cm.hasActiveBgCall() && !cm.hasActiveRingingCall()) {
                // handle foreground call hangup while there is background call
                if (DBG) {
                    log("- hangup(Call): hangupForegroundResumeBackground...");
                }
                DualTalkUtils dt = DualTalkUtils.getInstance();
                if (DualTalkUtils.isSupportDualTalk() && dt.isDualTalkMultipleHoldCase()) {
                    //CallManager always gets the error foregrond call...
                    if (dt.isCdmaAndGsmActive()) {
                        call.hangup();
                    } else {
                        cm.hangupForegroundResumeBackground(dt.getFirstActiveBgCall());
                    }
                } else {
                    cm.hangupForegroundResumeBackground(cm.getFirstActiveBgCall());
                }
            } else if (call.getState() == Call.State.ACTIVE && cm.hasActiveBgCall() && cm.hasActiveRingingCall()) {
                Call fg = cm.getActiveFgCall();
                Call bg = cm.getFirstActiveBgCall();
                if (fg.getPhone() == bg.getPhone() 
                        && fg.getPhone().getPhoneType() == PhoneConstants.PHONE_TYPE_GSM
                        && cm.getRingingPhone().getPhoneType() == PhoneConstants.PHONE_TYPE_SIP) {
                    call.getPhone().hangupActiveCall();
                } else {
                    call.hangup();
                }
            } else if (call.getState() == Call.State.ACTIVE
                    && cm.getState() != PhoneConstants.State.RINGING && DualTalkUtils.isSupportDualTalk()
                    && DualTalkUtils.getInstance().isCdmaAndGsmActive()) {
                //ALPS00438600
                //When the active call is GSM and there is an CDMA single call exist.
                log("Both cdma & gsm active call exist, hangup gsm call and switch cdma call");
                DualTalkUtils dtUtils = DualTalkUtils.getInstance();
                Call foregroundCall = dtUtils.getActiveFgCall();
                Call cdmaCall = dtUtils.getFirstActiveBgCall();
                
                log("foreground call = " + foregroundCall.getConnections());
                foregroundCall.hangup();

                log("background call = " + cdmaCall.getConnections());
                if (cdmaCall.getPhone().getPhoneType() == PhoneConstants.PHONE_TYPE_CDMA
                        && !hasMultipleConnections(cdmaCall)) {
                    cdmaCall.getPhone().switchHoldingAndActive();
                    log("switch cdma call = " + cdmaCall.getConnections());
                }
            } else {
                log("- hangup(Call): regular hangup()...");
                call.hangup();
            }
            return true;
        } catch (CallStateException ex) {
            Log.e(LOG_TAG, "Call hangup: caught " + ex, ex);
        }

        return false;
    }

    /**
     * Trivial wrapper around Connection.hangup(), except that we silently
     * do nothing (rather than throwing CallStateException) if the
     * connection wasn't actually active.
     */
    static void hangup(Connection c) {
        try {
            if (c != null) {
                c.hangup();
            }
        } catch (CallStateException ex) {
            Log.w(LOG_TAG, "Connection hangup: caught " + ex, ex);
        }
    }

    static boolean answerAndEndHolding(CallManager cm, Call ringing) {
        if (DBG) log("end holding & answer waiting: 1");
        if (!hangupHoldingCall(cm.getFirstActiveBgCall())) {
            Log.e(LOG_TAG, "end holding failed!");
            return false;
        }

        if (DBG) log("end holding & answer waiting: 2");
        return answerCall(ringing);

    }

    /**
     * Answers the incoming call specified by "ringing", and ends the currently active phone call.
     *
     * This method is useful when's there's an incoming call which we cannot manage with the
     * current call. e.g. when you are having a phone call with CDMA network and has received
     * a SIP call, then we won't expect our telephony can manage those phone calls simultaneously.
     * Note that some types of network may allow multiple phone calls at once; GSM allows to hold
     * an ongoing phone call, so we don't need to end the active call. The caller of this method
     * needs to check if the network allows multiple phone calls or not.
     *
     * @see #answerCall(Call)
     * @see InCallScreen#internalAnswerCall()
     */
    /* package */ static boolean answerAndEndActive(CallManager cm, Call ringing) {
        if (DBG) log("answerAndEndActive()...");

        /// M: For VT @{
        if (FeatureOption.MTK_VT3G324M_SUPPORT && VTCallUtils.isVTRinging()) {
            // There can not use InCallScreen's internalAnswerCall() directly.
            // becasue the caller(like bluetooth) may be not main thread, can not touch UI in internalAnswerCall();
            // use PhoneInterfaceManager instead, which will handle operation in MainThreadHandler.
            PhoneGlobals.getInstance().phoneMgr.answerRingingCall();
            return true;
        }

        Phone fgPhone = cm.getActiveFgCall().getPhone();
        Phone ringingPhone = ringing.getPhone();
        /// @}

        // Unlike the answerCall() method, we *don't* need to stop the
        // ringer or change audio modes here since the user is already
        // in-call, which means that the audio mode is already set
        // correctly, and that we wouldn't have started the ringer in the
        // first place.

        // hanging up the active call also accepts the waiting call
        // while active call and waiting call are from the same phone
        // i.e. both from GSM phone
        Call fgCall = cm.getActiveFgCall();
        if (!hangupActiveCall(fgCall)) {
            Log.w(LOG_TAG, "end active call failed!");
            return false;
        }
        /// M: For ALPS01270717. @{
        // Google code.
        /*
        mConnectionHandler.removeMessages(MSG_CHECK_STATUS_ANSWERCALL);
        Message msg = mConnectionHandler.obtainMessage(MSG_CHECK_STATUS_ANSWERCALL);
        msg.arg1 = 1;
        msg.obj = new FgRingCalls(fgCall, ringing);
        mConnectionHandler.sendMessage(msg);
        */
        if (ringing.isRinging()
                && (fgPhone != ringingPhone || (fgPhone == ringingPhone && (fgPhone instanceof SipPhone)))) {
            return answerCall(ringing);
        }
        /// @}

        return true;
    }

    /**
     * For a CDMA phone, advance the call state upon making a new
     * outgoing call.
     *
     * <pre>
     *   IDLE -> SINGLE_ACTIVE
     * or
     *   SINGLE_ACTIVE -> THRWAY_ACTIVE
     * </pre>
     * @param app The phone instance.
     */
    private static void updateCdmaCallStateOnNewOutgoingCall(PhoneGlobals app,
            Connection connection) {
        if (app.cdmaPhoneCallState.getCurrentCallState() ==
            CdmaPhoneCallState.PhoneCallState.IDLE) {
            // This is the first outgoing call. Set the Phone Call State to ACTIVE
            app.cdmaPhoneCallState.setCurrentCallState(
                CdmaPhoneCallState.PhoneCallState.SINGLE_ACTIVE);
        } else {
            // This is the second outgoing call. Set the Phone Call State to 3WAY
            app.cdmaPhoneCallState.setCurrentCallState(
                CdmaPhoneCallState.PhoneCallState.THRWAY_ACTIVE);
            app.getCallModeler().setCdmaOutgoing3WayCall(connection);
        }
    }
    /**
     * @see placeCall below
     */
    public static int placeCall(Context context, Phone phone, String number, Uri contactRef,
            boolean isEmergencyCall) {
        return placeCall(context, phone, number, contactRef, isEmergencyCall,
                CallGatewayManager.EMPTY_INFO, null);
    }

    /**
     * Dial the number using the phone passed in.
     *
     * If the connection is establised, this method issues a sync call
     * that may block to query the caller info.
     * TODO: Change the logic to use the async query.
     *
     * @param context To perform the CallerInfo query.
     * @param phone the Phone object.
     * @param number to be dialed as requested by the user. This is
     * NOT the phone number to connect to. It is used only to build the
     * call card and to update the call log. See above for restrictions.
     * @param contactRef that triggered the call. Typically a 'tel:'
     * uri but can also be a 'content://contacts' one.
     * @param isEmergencyCall indicates that whether or not this is an
     * emergency call
     * @param gatewayUri Is the address used to setup the connection, null
     * if not using a gateway
     *
     * @return either CALL_STATUS_DIALED or CALL_STATUS_FAILED
     */
    public static int placeCall(Context context, Phone phone, String number, Uri contactRef,
            boolean isEmergencyCall, RawGatewayInfo gatewayInfo, CallGatewayManager callGateway) {
        /// M: GEMINI+
        return placeCallGemini(context, phone, number, contactRef, isEmergencyCall,
                gatewayInfo, callGateway, -1);
    }

    public static int placeCallGemini(Context context, Phone phone,
            String number, Uri contactRef, boolean isEmergencyCall,
            RawGatewayInfo gatewayInfo, CallGatewayManager callGateway, int simId) {
        final Uri gatewayUri = gatewayInfo.gatewayUri;
        //Profiler.trace(Profiler.PhoneUtilsEnterPlaceCallGemini);
        if (DBG) {
            log("placeCall '" + number + "' GW:'" + gatewayUri + "'");
        }
        
        if (PhoneGlobals.getInstance().mCM.getState() == PhoneConstants.State.IDLE) {
            PhoneGlobals.getInstance().notifier.resetBeforeCall();
            setAudioMode();
        }
        
        if (!VTCallUtils.isVTIdle()) {
            return Constants.CALL_STATUS_FAILED;
        }
        
        final PhoneGlobals app = PhoneGlobals.getInstance();

        boolean useGateway = false;
        if (null != gatewayUri &&
            !isEmergencyCall &&
            PhoneUtils.isRoutableViaGateway(number)) {  // Filter out MMI, OTA and other codes.
            useGateway = true;
        }

        int status = CALL_STATUS_DIALED;
        Connection connection;
        String numberToDial;
        if (useGateway) {
            // TODO: 'tel' should be a constant defined in framework base
            // somewhere (it is in webkit.)
            if (null == gatewayUri || !Constants.SCHEME_TEL.equals(gatewayUri.getScheme())) {
                Log.e(LOG_TAG, "Unsupported URL:" + gatewayUri);
                return Constants.CALL_STATUS_FAILED;
            }

            // We can use getSchemeSpecificPart because we don't allow #
            // in the gateway numbers (treated a fragment delim.) However
            // if we allow more complex gateway numbers sequence (with
            // passwords or whatnot) that use #, this may break.
            // TODO: Need to support MMI codes.
            numberToDial = gatewayUri.getSchemeSpecificPart();
        } else {
            numberToDial = number;
        }

        // Remember if the phone state was in IDLE state before this call.
        // After calling CallManager#dial(), getState() will return different state.
        final boolean initiallyIdle = app.mCM.getState() == PhoneConstants.State.IDLE;

        boolean isSipCall = phone.getPhoneType() == PhoneConstants.PHONE_TYPE_SIP;
        try {
            /// M: @{
            // Original code:
            // connection = app.mCM.dial(phone, numberToDial);
            connection = CallManagerWrapper.dial(phone, numberToDial, simId);
            /// @}
        } catch (CallStateException ex) {
            // CallStateException means a new outgoing call is not currently
            // possible: either no more call slots exist, or there's another
            // call already in the process of dialing or ringing.
            Log.w(LOG_TAG, "Exception from app.mCM.dial()", ex);
            return Constants.CALL_STATUS_FAILED;

            // Note that it's possible for CallManager.dial() to return
            // null *without* throwing an exception; that indicates that
            // we dialed an MMI (see below).
        }

        // Now that the call is successful, we can save the gateway info for the call
        if (callGateway != null) {
            callGateway.setGatewayInfoForConnection(connection, gatewayInfo);
        }
        /// M:Gemini+ @{
        // Original code:
        // int phoneType = phone.getPhoneType();
        int phoneType = PhoneWrapper.getPhoneType(phone, simId);
        /// @}

        // On GSM phones, null is returned for MMI codes
        if (null == connection) {
            if (phoneType == PhoneConstants.PHONE_TYPE_GSM && gatewayUri == null) {
                if (DBG) log("dialed MMI code: " + number);
                status = CALL_STATUS_DIALED_MMI;
            } else {
                status = Constants.CALL_STATUS_FAILED;
            }
        } else {
            if (phoneType == PhoneConstants.PHONE_TYPE_CDMA) {
                updateCdmaCallStateOnNewOutgoingCall(app, connection);
            }

            if (gatewayUri == null) {
                // phone.dial() succeeded: we're now in a normal phone call.
                // attach the URI to the CallerInfo Object if it is there,
                // otherwise just attach the Uri Reference.
                // if the uri does not have a "content" scheme, then we treat
                // it as if it does NOT have a unique reference.
                String content = context.getContentResolver().SCHEME_CONTENT;
                if ((contactRef != null) && (contactRef.getScheme().equals(content))) {
                    Object userDataObject = connection.getUserData();
                    if (userDataObject == null) {
                        connection.setUserData(contactRef);
                    } else {
                        // TODO: This branch is dead code, we have
                        // just created the connection which has
                        // no user data (null) by default.
                        if (userDataObject instanceof CallerInfo) {
                        ((CallerInfo) userDataObject).contactRefUri = contactRef;
                        } else {
                        ((CallerInfoToken) userDataObject).currentInfo.contactRefUri =
                            contactRef;
                        }
                    }
                }
                }

            startGetCallerInfo(context, connection, null, null, gatewayInfo);

            // Always set mute to off when we are dialing an emergency number
            if (isEmergencyCall) {
                setMute(false);
            }

            /// M: Remove Original code
            // setAudioMode();

            if (DBG) log("about to activate speaker");
            // Check is phone in any dock, and turn on speaker accordingly
            final boolean speakerActivated = activateSpeakerIfDocked(phone);

            final BluetoothManager btManager = app.getBluetoothManager();
            // See also similar logic in answerCall().
            if (initiallyIdle && !speakerActivated && isSpeakerOn(app)
                    && !btManager.isBluetoothHeadsetAudioOn()) {
                // This is not an error but might cause users' confusion. Add log just in case.
                Log.i(LOG_TAG, "Forcing speaker off when initiating a new outgoing call...");
                PhoneUtils.turnOnSpeaker(app, false, true);
            }
        }

        return status;
    }

    /* package */ static String toLogSafePhoneNumber(String number) {
        // For unknown number, log empty string.
        if (number == null) {
            return "";
        }
        if (DBG) {
            // When VDBG is true we emit PII.
            return number;
        }

        // Do exactly same thing as Uri#toSafeString() does, which will enable us to compare
        // sanitized phone numbers.
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < number.length(); i++) {
            char c = number.charAt(i);
            if (c == '-' || c == '@' || c == '.') {
                builder.append(c);
            } else {
                builder.append('x');
            }
        }
        return builder.toString();
    }

    /**
     * Wrapper function to control when to send an empty Flash command to the network.
     * Mainly needed for CDMA networks, such as scenarios when we need to send a blank flash
     * to the network prior to placing a 3-way call for it to be successful.
     */
    static void sendEmptyFlash(Phone phone) {
        if (phone.getPhoneType() == PhoneConstants.PHONE_TYPE_CDMA) {
            Call fgCall = phone.getForegroundCall();
            if (fgCall.getState() == Call.State.ACTIVE) {
                // Send the empty flash
                if (DBG) Log.d(LOG_TAG, "onReceive: (CDMA) sending empty flash to network");
                switchHoldingAndActive(phone.getBackgroundCall());
            }
        }
    }

    /**
     * @param heldCall is the background call want to be swapped
     */
    static void switchHoldingAndActive(Call heldCall) {
        log("switchHoldingAndActive()...");

        /// M: For ALPS01265629. @{
        // When there has a active video call, can not hold this call.
        if (VTCallUtils.isVideoCall(CallManager.getInstance().getActiveFgCall())) {
            log("switchHoldingAndActive(), there has a video call and can not do switch with this.");
            return;
        }
        /// @}

        /// M: ALPS00513091 @{
        // Need to tell bt this is a switching process and not hangup H H
        DualTalkUtils dt = PhoneGlobals.getInstance().notifier.mDualTalk;
        if (DualTalkUtils.isSupportDualTalk() &&
                dt != null && dt.isMultiplePhoneActive()) {
            log("switchHoldingAndActive(), sPhoneSwapStatus set true!");
            sPhoneSwapStatus = true;
        }
        /// @}

        try {
            CallManager cm = PhoneGlobals.getInstance().mCM;
            if (heldCall.isIdle()) {
                // no heldCall, so it is to hold active call
                cm.switchHoldingAndActive(cm.getFgPhone().getBackgroundCall());
            } else {
                // has particular heldCall, so to switch
                cm.switchHoldingAndActive(heldCall);
            }
            setAudioMode(cm);
        } catch (CallStateException ex) {
            Log.w(LOG_TAG, "switchHoldingAndActive: caught " + ex, ex);
        }
    }

    /**
     * Restore the mute setting from the earliest connection of the
     * foreground call.
     */
    static Boolean restoreMuteState() {
        Phone phone = PhoneGlobals.getInstance().mCM.getFgPhone();

        /// M: @ {
        // get the earliest connection
        // Original code:
        // Connection c = phone.getForegroundCall().getEarliestConnection();
        Connection c;
        if (GeminiUtils.isGeminiSupport()) {
            Call fgCall = null;
            DualTalkUtils dt = DualTalkUtils.getInstance();
            if (DualTalkUtils.isSupportDualTalk() && dt != null
                    && dt.isCdmaAndGsmActive()) {
                fgCall = dt.getActiveFgCall();
                //Tells the true phone that match with the real foregroundcall.
                phone = fgCall.getPhone();
            } else {
                fgCall = PhoneGlobals.getInstance().mCM.getActiveFgCall();
            }
            c = fgCall.getEarliestConnection();
        } else {
            c = phone.getForegroundCall().getEarliestConnection();
        }
        /// @}
        // only do this if connection is not null.
        if (c != null) {

            int phoneType = phone.getPhoneType();

            // retrieve the mute value.
            Boolean shouldMute = null;

            // In CDMA, mute is not maintained per Connection. Single mute apply for
            // a call where  call can have multiple connections such as
            // Three way and Call Waiting.  Therefore retrieving Mute state for
            // latest connection can apply for all connection in that call
            if (phoneType == PhoneConstants.PHONE_TYPE_CDMA) {
                shouldMute = sConnectionMuteTable.get(
                        phone.getForegroundCall().getLatestConnection());
            } else if ((phoneType == PhoneConstants.PHONE_TYPE_GSM)
                    || (phoneType == PhoneConstants.PHONE_TYPE_SIP)) {
                shouldMute = sConnectionMuteTable.get(c);
            }
            if (shouldMute == null) {
                if (DBG) log("problem retrieving mute value for this connection.");
                shouldMute = Boolean.FALSE;
            }

            // set the mute value and return the result.
            setMute (shouldMute.booleanValue());
            return shouldMute;
        }
        return Boolean.valueOf(getMute());
    }

    static void mergeCalls() {
        mergeCalls(PhoneGlobals.getInstance().mCM);
    }

    static void mergeCalls(CallManager cm) {
        int phoneType = cm.getFgPhone().getPhoneType();
        /// M: @{
        DualTalkUtils dt = DualTalkUtils.getInstance();
        if (DualTalkUtils.isSupportDualTalk()) {
            Call call = dt.getActiveFgCall();
            if (call != null) {
                phoneType = call.getPhone().getPhoneType();
            }
        }
        /// @}
        if (phoneType == PhoneConstants.PHONE_TYPE_CDMA) {
            log("mergeCalls(): CDMA...");
            PhoneGlobals app = PhoneGlobals.getInstance();
            if (app.cdmaPhoneCallState.getCurrentCallState()
                    == CdmaPhoneCallState.PhoneCallState.THRWAY_ACTIVE) {
                // Set the Phone Call State to conference
                app.cdmaPhoneCallState.setCurrentCallState(
                        CdmaPhoneCallState.PhoneCallState.CONF_CALL);

                // Send flash cmd
                // TODO: Need to change the call from switchHoldingAndActive to
                // something meaningful as we are not actually trying to swap calls but
                // instead are merging two calls by sending a Flash command.
                log("- sending flash...");
                switchHoldingAndActive(cm.getFirstActiveBgCall());
            /// M: @{
            } else if (DualTalkUtils.isSupportDualTalk()) {
                 //DualTalkUtils dt = PhoneGlobals.getInstance().notifier.mDualTalk;
                 Call fg = dt.getActiveFgCall();
                 PhoneLog.d(LOG_TAG, "we don't know how to do exactly, so just switch the cdma call");
                 try {
                     fg.getPhone().switchHoldingAndActive();
                 } catch (CallStateException e) {
                     log(e.toString());
                 }
            }
            /// @}
        } else {
            try {
                log("mergeCalls(): calling cm.conference()...");
                /// M: @{
                if (DualTalkUtils.isSupportDualTalk() && dt.isDualTalkMultipleHoldCase()) {
                    if (dt.isCdmaAndGsmActive()) {
                        Phone conPhone = dt.getActiveFgCall().getPhone();
                        if (conPhone.canConference()) {
                            conPhone.conference();
                            log("mergeCalls: confrence calls on phone = " + conPhone);
                        } else {
                            log("mergeCalls: can't confrence on phone = " + conPhone);
                        }
                    } else {
                        cm.conference(dt.getFirstActiveBgCall());
                    }
                } else {
                /// @}
                    cm.conference(cm.getFirstActiveBgCall());
                }
            } catch (CallStateException ex) {
                Log.w(LOG_TAG, "mergeCalls: caught " + ex, ex);
            }
        }
    }

    static void separateCall(Connection c) {
        try {
            if (DBG) log("separateCall: " + toLogSafePhoneNumber(c.getAddress()));
            c.separate();
        } catch (CallStateException ex) {
            Log.w(LOG_TAG, "separateCall: caught " + ex, ex);
        }
    }

    /**
     * Handle the MMIInitiate message and put up an alert that lets
     * the user cancel the operation, if applicable.
     *
     * @param context context to get strings.
     * @param mmiCode the MmiCode object being started.
     * @param buttonCallbackMessage message to post when button is clicked.
     * @param previousAlert a previous alert used in this activity.
     * @return the dialog handle
     */
    static Dialog displayMMIInitiate(Context context,
                                          MmiCode mmiCode,
                                          Message buttonCallbackMessage,
                                          Dialog previousAlert) {
        if (DBG) log("displayMMIInitiate: " + mmiCode);
        if (previousAlert != null) {
            previousAlert.dismiss();
        }

        // The UI paradigm we are using now requests that all dialogs have
        // user interaction, and that any other messages to the user should
        // be by way of Toasts.
        //
        // In adhering to this request, all MMI initiating "OK" dialogs
        // (non-cancelable MMIs) that end up being closed when the MMI
        // completes (thereby showing a completion dialog) are being
        // replaced with Toasts.
        //
        // As a side effect, moving to Toasts for the non-cancelable MMIs
        // also means that buttonCallbackMessage (which was tied into "OK")
        // is no longer invokable for these dialogs.  This is not a problem
        // since the only callback messages we supported were for cancelable
        // MMIs anyway.
        //
        // A cancelable MMI is really just a USSD request. The term
        // "cancelable" here means that we can cancel the request when the
        // system prompts us for a response, NOT while the network is
        // processing the MMI request.  Any request to cancel a USSD while
        // the network is NOT ready for a response may be ignored.
        //
        // With this in mind, we replace the cancelable alert dialog with
        // a progress dialog, displayed until we receive a request from
        // the the network.  For more information, please see the comments
        // in the displayMMIComplete() method below.
        //
        // Anything that is NOT a USSD request is a normal MMI request,
        // which will bring up a toast (desribed above).

        boolean isCancelable = (mmiCode != null) && mmiCode.isCancelable();

        if (!isCancelable) {
            if (DBG) log("not a USSD code, displaying status toast.");
            CharSequence text = context.getText(R.string.mmiStarted);
            Toast.makeText(context, text, Toast.LENGTH_SHORT).show();
            return null;
        } else {
            if (DBG) log("running USSD code, displaying indeterminate progress.");

            // create the indeterminate progress dialog and display it.
            ProgressDialog pd = new ProgressDialog(context);
            pd.setMessage(context.getText(R.string.ussdRunning));
            pd.setCancelable(false);
            pd.setIndeterminate(true);
            pd.getWindow().addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);

            pd.show();

            /// M: Plug-in, using for cancel USSD
            ExtensionManager.getInstance().getMmiCodeExtension().onMmiCodeStarted(context, mmiCode,
                    buttonCallbackMessage, pd);
            return pd;
        }

    }

    /**
     * Handle the MMIComplete message and fire off an intent to display
     * the message.
     *
     * @param context context to get strings.
     * @param mmiCode MMI result.
     * @param previousAlert a previous alert used in this activity.
     */
    static void displayMMIComplete(final Phone phone, Context context, final MmiCode mmiCode,
            Message dismissCallbackMessage,
            AlertDialog previousAlert) {
        /// M: GEMINI+
        displayMMICompleteExt(phone, context, mmiCode, dismissCallbackMessage, previousAlert,
                PhoneConstants.GEMINI_SIM_1);
    }

    static void displayMMICompleteExt(final Phone phone, Context context, final MmiCode mmiCode,
            Message dismissCallbackMessage, AlertDialog previousAlert, final int simId) {
        final PhoneGlobals app = PhoneGlobals.getInstance();
        CharSequence text;
        int title = 0;  // title for the progress dialog, if needed.
        MmiCode.State state = mmiCode.getState();

        if (DBG) log("displayMMIComplete: state=" + state);
        sCurCode = mmiCode;

        switch (state) {
            case PENDING:
                // USSD code asking for feedback from user.
                text = mmiCode.getMessage();
                if (DBG) log("- using text from PENDING MMI message: '" + text + "'");
                break;
            case CANCELLED:
                text = null;
                /// M: ALPS01374729. @{
                // Need dismiss the pending activity.
                dismissUssdDialog();
                /// @}
                return;
            case COMPLETE:
                sMmiFinished = true;
                if (app.getPUKEntryActivity() != null) {
                    // if an attempt to unPUK the device was made, we specify
                    // the title and the message here.
                    title = com.android.internal.R.string.PinMmi;
                    text = context.getText(R.string.puk_unlocked);
                    break;
                }
                // All other conditions for the COMPLETE mmi state will cause
                // the case to fall through to message logic in common with
                // the FAILED case.

            case FAILED:
                text = mmiCode.getMessage();
                if (DBG) log("- using text from MMI message: '" + text + "'");
                /// M: @{
                dismissUssdDialog();
                /// @}
                break;
            default:
                throw new IllegalStateException("Unexpected MmiCode state: " + state);
        }

        if (previousAlert != null) {
            previousAlert.dismiss();
        }

        // Check to see if a UI exists for the PUK activation.  If it does
        // exist, then it indicates that we're trying to unblock the PUK.
        /// M: @{
        // This dialog is not useful but also cause some problem when Sim me lock exists after
        // PUK locked is unlocked by **05*... way, so delete them
        // Original code:
        /*
        if ((app.getPUKEntryActivity() != null) && (state == MmiCode.State.COMPLETE)) {
            if (DBG) log("displaying PUK unblocking progress dialog.");

            // create the progress dialog, make sure the flags and type are
            // set correctly.
            ProgressDialog pd = new ProgressDialog(app);
            pd.setTitle(title);
            pd.setMessage(text);
            pd.setCancelable(false);
            pd.setIndeterminate(true);
            pd.getWindow().setType(WindowManager.LayoutParams.TYPE_SYSTEM_DIALOG);
            pd.getWindow().addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);

            // display the dialog
            pd.show();

            // indicate to the Phone app that the progress dialog has
            // been assigned for the PUK unlock / SIM READY process.
            app.setPukEntryProgressDialog(pd);

        } else {
        */
        /// @}
        if ((app.getPUKEntryActivity() == null) || (state != MmiCode.State.COMPLETE)) {
            // In case of failure to unlock, we'll need to reset the
            // PUK unlock activity, so that the user may try again.
            if (app.getPUKEntryActivity() != null) {
                app.setPukEntryActivity(null);
            }

            // A USSD in a pending state means that it is still
            // interacting with the user.
            if (state != MmiCode.State.PENDING) {
                if (DBG) log("MMI code has finished running.");

                if (DBG) log("Extended NW displayMMIInitiate (" + text + ")");
                if (text == null || text.length() == 0)
                    return;


                // displaying system alert dialog on the screen instead of
                // using another activity to display the message.  This
                // places the message at the forefront of the UI.
                /// M: @{
                // Original code:
                /*
                AlertDialog newDialog = new AlertDialog.Builder(context)
                        .setMessage(text)
                        .setPositiveButton(R.string.ok, null)
                        .setCancelable(true)
                        .create();

                newDialog.getWindow().setType(
                        WindowManager.LayoutParams.TYPE_SYSTEM_DIALOG);
                newDialog.getWindow().addFlags(
                        WindowManager.LayoutParams.FLAG_DIM_BEHIND);

                newDialog.show();
                */
                // inflate the layout with the scrolling text area for the dialog.
                LayoutInflater inflater = (LayoutInflater) context.getSystemService(
                        Context.LAYOUT_INFLATER_SERVICE);
                View dialogView = inflater.inflate(R.layout.dialog_ussd_response, null);
                TextView msg = (TextView) dialogView.findViewById(R.id.msg);
                msg.setText(text);    
                TextView ussdUpdateView = (TextView) dialogView.findViewById(R.id.ussd_update);
                ussdUpdateView.setVisibility(View.GONE);
                EditText inputText = (EditText) dialogView.findViewById(R.id.input_field);
                inputText.setVisibility(View.GONE);
                /*
                 * auto update the UI,because some App change the Phone mode that will cause 
                 * some USSD response information lost.
                 * For example ,when Camera first init.
                 */
                autoUpdateUssdReponseUi(dialogView);
                // displaying system alert dialog on the screen instead of
                // using another activity to display the message.  This
                // places the message at the forefront of the UI.
                /*
                 * Original Code:
                AlertDialog newDialog = new AlertDialog.Builder(context)
                        .setView(dialogView)
                        .setPositiveButton(R.string.ok, null)
                        .setCancelable(true)
                        .create();

                newDialog.getWindow().setType(
                        WindowManager.LayoutParams.TYPE_SYSTEM_DIALOG);
                newDialog.getWindow().addFlags(
                        WindowManager.LayoutParams.FLAG_DIM_BEHIND);

                newDialog.show();
                */

                displayMmiDialog(context, text, UssdAlertActivity.USSD_DIALOG_NOTIFICATION, simId);
                /// M: Plug-in, using for cancel USSD @{
                log("displayMMICompleteExt: mmiCode = " + mmiCode+" dismissCallbackMessage="+dismissCallbackMessage);
                ExtensionManager.getInstance().getMmiCodeExtension().onMmiCodeStarted(context, mmiCode,
                        dismissCallbackMessage, previousAlert);
                /// @}
            } else {
                if (DBG) log("USSD code has requested user input. Constructing input dialog.");

                // USSD MMI code that is interacting with the user.  The
                // basic set of steps is this:
                //   1. User enters a USSD request
                //   2. We recognize the request and displayMMIInitiate
                //      (above) creates a progress dialog.
                //   3. Request returns and we get a PENDING or COMPLETE
                //      message.
                //   4. These MMI messages are caught in the PhoneGlobals
                //      (onMMIComplete) and the InCallScreen
                //      (mHandler.handleMessage) which bring up this dialog
                //      and closes the original progress dialog,
                //      respectively.
                //   5. If the message is anything other than PENDING,
                //      we are done, and the alert dialog (directly above)
                //      displays the outcome.
                //   6. If the network is requesting more information from
                //      the user, the MMI will be in a PENDING state, and
                //      we display this dialog with the message.
                //   7. User input, or cancel requests result in a return
                //      to step 1.  Keep in mind that this is the only
                //      time that a USSD should be canceled.

                // inflate the layout with the scrolling text area for the dialog.
                LayoutInflater inflater = (LayoutInflater) context.getSystemService(
                        Context.LAYOUT_INFLATER_SERVICE);
                View dialogView = inflater.inflate(R.layout.dialog_ussd_response, null);

                /// M: @{
                TextView msg = (TextView) dialogView.findViewById(R.id.msg);
                msg.setText(text);
                msg.setWidth(MIN_WIDTH);
                /// @}

                // get the input field.
                final EditText inputText = (EditText) dialogView.findViewById(R.id.input_field);
                //Disable the long click, because we haven't the window context, see ALPS00241709 for details.
                inputText.setLongClickable(false);

                /// M: add
                inputText.setFilters(new InputFilter[] {new InputFilter.LengthFilter(182)});

                // specify the dialog's click listener, with SEND and CANCEL logic.
                final DialogInterface.OnClickListener mUSSDDialogListener =
                    new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int whichButton) {
                            switch (whichButton) {
                                case DialogInterface.BUTTON_POSITIVE:
                                    // As per spec 24.080, valid length of ussd string
                                    // is 1 - 160. If length is out of the range then
                                    // display toast message & Cancel MMI operation.
                                    if (inputText.length() < MIN_USSD_LEN
                                            || inputText.length() > MAX_USSD_LEN) {
                                        Toast.makeText(app,
                                                app.getResources().getString(R.string.enter_input,
                                                MIN_USSD_LEN, MAX_USSD_LEN),
                                                Toast.LENGTH_LONG).show();
                                        if (mmiCode.isCancelable()) {
                                            mmiCode.cancel();
                                        }
                                    } else {
                                    /// M: Gemini+ @{
                                    // Original code:
                                    /*
                                    phone.sendUssdResponse(inputText.getText().toString());
                                    */
                                    PhoneWrapper.sendUssdResponse(phone, inputText.getText().toString(), simId);
                                    sDialog = null;
                                    sUssdActivity = null;
                                    /// @}
                                    }
                                     break;
                                case DialogInterface.BUTTON_NEGATIVE:
                                    if (mmiCode.isCancelable()) {
                                        mmiCode.cancel();
                                    }
                                    /// M: @{
                                    sDialog = null;
                                    sUssdActivity = null;
                                    /// @}
                                    break;
                            }
                        }
                    };

                /// M: @{

                /// @}
                // build the dialog
                final AlertDialog newDialog = new AlertDialog.Builder(context)
                        /*.setMessage(text)*/
                        .setView(dialogView)
                        .setPositiveButton(R.string.send_button, /*mUSSDDialogListener*/null)
                        .setNegativeButton(R.string.cancel, /*mUSSDDialogListener*/null)
                        .setCancelable(false)
                        .create();

                /// M: Add Cancel Listener @{
                newDialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
                    public void onCancel(DialogInterface dialog) {
                        // TODO Auto-generated method stub
                        if (mmiCode.isCancelable()) {
                            mmiCode.cancel();
                        }
                    }
                });
                /// @}

                // attach the key listener to the dialog's input field and make
                // sure focus is set.
                final View.OnKeyListener mUSSDDialogInputListener =
                    new View.OnKeyListener() {
                        public boolean onKey(View v, int keyCode, KeyEvent event) {
                            switch (keyCode) {
                                case KeyEvent.KEYCODE_CALL:
                                    /// M: @{
                                    return true;
                                    /// @}
                                case KeyEvent.KEYCODE_ENTER:
                                    /// M: @{
                                    //  Need to check KeyEvent.ACTION_DOWN or UP
                                    // Original code:
                                    // if(event.getAction() == KeyEvent.ACTION_DOWN) {
                                        // phone.sendUssdResponse(inputText.getText().toString());
                                    if (event.getAction() == KeyEvent.ACTION_UP) {
                                        PhoneWrapper.sendUssdResponse(phone, inputText.getText().toString(), simId);
                                    /// @}
                                        newDialog.dismiss();
                                    }
                                    return true;
                            }
                            return false;
                        }
                    };
                inputText.setOnKeyListener(mUSSDDialogInputListener);
                inputText.requestFocus();

                // set the window properties of the dialog
                newDialog.getWindow().setType(
                        WindowManager.LayoutParams.TYPE_SYSTEM_DIALOG);
                newDialog.getWindow().addFlags(
                        WindowManager.LayoutParams.FLAG_DIM_BEHIND);

                // now show the dialog!
                /// M: @{
                // Original Code:
                // newDialog.show();
                displayMmiDialog(context, text, UssdAlertActivity.USSD_DIALOG_REQUEST, simId);
                /// @}
            }
        }
    }

    /**
     * Cancels the current pending MMI operation, if applicable.
     * @return true if we canceled an MMI operation, or false
     *         if the current pending MMI wasn't cancelable
     *         or if there was no current pending MMI at all.
     *
     * @see displayMMIInitiate
     */
    /// M: @{
    // Original code:
    /*
    static boolean cancelMmiCode(Phone phone) {
        List<? extends MmiCode> pendingMmis = phone.getPendingMmiCodes();
    */
    static boolean cancelMmiCodeExt(Phone phone, int slotId) {
        PhoneLog.d(LOG_TAG, "cancelMmiCodeExt, slotId="+slotId);
        List<? extends MmiCode> pendingMmis = PhoneWrapper.getPendingMmiCodes(phone, slotId);
    /// @}
        int count = pendingMmis.size();
        if (DBG) log("cancelMmiCode: num pending MMIs = " + count);

        boolean canceled = false;
        if (count > 0) {
            // assume that we only have one pending MMI operation active at a time.
            // I don't think it's possible to enter multiple MMI codes concurrently
            // in the phone UI, because during the MMI operation, an Alert panel
            // is displayed, which prevents more MMI code from being entered.
            MmiCode mmiCode = pendingMmis.get(0);
            if (mmiCode.isCancelable()) {
                mmiCode.cancel();
                canceled = true;
            }
        }
        return canceled;
    }

    public static class VoiceMailNumberMissingException extends Exception {
        VoiceMailNumberMissingException() {
            super();
        }

        VoiceMailNumberMissingException(String msg) {
            super(msg);
        }
    }

    /**
     * Given an Intent (which is presumably the ACTION_CALL intent that
     * initiated this outgoing call), figure out the actual phone number we
     * should dial.
     *
     * Note that the returned "number" may actually be a SIP address,
     * if the specified intent contains a sip: URI.
     *
     * This method is basically a wrapper around PhoneUtils.getNumberFromIntent(),
     * except it's also aware of the EXTRA_ACTUAL_NUMBER_TO_DIAL extra.
     * (That extra, if present, tells us the exact string to pass down to the
     * telephony layer.  It's guaranteed to be safe to dial: it's either a PSTN
     * phone number with separators and keypad letters stripped out, or a raw
     * unencoded SIP address.)
     *
     * @return the phone number corresponding to the specified Intent, or null
     *   if the Intent has no action or if the intent's data is malformed or
     *   missing.
     *
     * @throws VoiceMailNumberMissingException if the intent
     *   contains a "voicemail" URI, but there's no voicemail
     *   number configured on the device.
     */
    public static String getInitialNumber(Intent intent)
            throws PhoneUtils.VoiceMailNumberMissingException {
        if (DBG) log("getInitialNumber(): " + intent);

        String action = intent.getAction();
        if (TextUtils.isEmpty(action)) {
            return null;
        }

        // If the EXTRA_ACTUAL_NUMBER_TO_DIAL extra is present, get the phone
        // number from there.  (That extra takes precedence over the actual data
        // included in the intent.)
        if (intent.hasExtra(Constants.EXTRA_ACTUAL_NUMBER_TO_DIAL)) {
            String actualNumberToDial =
                    intent.getStringExtra(Constants.EXTRA_ACTUAL_NUMBER_TO_DIAL);
            if (DBG) {
                log("==> got EXTRA_ACTUAL_NUMBER_TO_DIAL; returning '"
                        + toLogSafePhoneNumber(actualNumberToDial) + "'");
            }
            return actualNumberToDial;
        }

        return getNumberFromIntent(PhoneGlobals.getInstance(), intent);
    }

    /**
     * Gets the phone number to be called from an intent.  Requires a Context
     * to access the contacts database, and a Phone to access the voicemail
     * number.
     *
     * <p>If <code>phone</code> is <code>null</code>, the function will return
     * <code>null</code> for <code>voicemail:</code> URIs;
     * if <code>context</code> is <code>null</code>, the function will return
     * <code>null</code> for person/phone URIs.</p>
     *
     * <p>If the intent contains a <code>sip:</code> URI, the returned
     * "number" is actually the SIP address.
     *
     * @param context a context to use (or
     * @param intent the intent
     *
     * @throws VoiceMailNumberMissingException if <code>intent</code> contains
     *         a <code>voicemail:</code> URI, but <code>phone</code> does not
     *         have a voicemail number set.
     *
     * @return the phone number (or SIP address) that would be called by the intent,
     *         or <code>null</code> if the number cannot be found.
     */
    static String getNumberFromIntent(Context context, Intent intent)
            throws VoiceMailNumberMissingException {
        Uri uri = intent.getData();
        String scheme = uri.getScheme();

        // The sip: scheme is simple: just treat the rest of the URI as a
        // SIP address.
        if (Constants.SCHEME_SIP.equals(scheme)) {
            return uri.getSchemeSpecificPart();
        }

        // Otherwise, let PhoneNumberUtils.getNumberFromIntent() handle
        // the other cases (i.e. tel: and voicemail: and contact: URIs.)

        final String number = PhoneNumberUtils.getNumberFromIntent(intent, context);

        // Check for a voicemail-dialing request.  If the voicemail number is
        // empty, throw a VoiceMailNumberMissingException.
        if (Constants.SCHEME_VOICEMAIL.equals(scheme) &&
                (number == null || TextUtils.isEmpty(number)))
            throw new VoiceMailNumberMissingException();

        return number;
    }

    /**
     * Returns the caller-id info corresponding to the specified Connection.
     * (This is just a simple wrapper around CallerInfo.getCallerInfo(): we
     * extract a phone number from the specified Connection, and feed that
     * number into CallerInfo.getCallerInfo().)
     *
     * The returned CallerInfo may be null in certain error cases, like if the
     * specified Connection was null, or if we weren't able to get a valid
     * phone number from the Connection.
     *
     * Finally, if the getCallerInfo() call did succeed, we save the resulting
     * CallerInfo object in the "userData" field of the Connection.
     *
     * NOTE: This API should be avoided, with preference given to the
     * asynchronous startGetCallerInfo API.
     */
    public static CallerInfo getCallerInfo(Context context, Connection c) {
        CallerInfo info = null;

        if (c != null) {
            //See if there is a URI attached.  If there is, this means
            //that there is no CallerInfo queried yet, so we'll need to
            //replace the URI with a full CallerInfo object.
            Object userDataObject = c.getUserData();
            if (userDataObject instanceof Uri) {
                info = CallerInfo.getCallerInfo(context, (Uri) userDataObject);
                if (info != null) {
                    c.setUserData(info);
                }
            } else {
                if (userDataObject instanceof CallerInfoToken) {
                    //temporary result, while query is running
                    info = ((CallerInfoToken) userDataObject).currentInfo;
                } else {
                    //final query result
                    info = (CallerInfo) userDataObject;
                }
                if (info == null) {
                    // No URI, or Existing CallerInfo, so we'll have to make do with
                    // querying a new CallerInfo using the connection's phone number.
                    String number = c.getAddress();

                    if (DBG) log("getCallerInfo: number = " + number);

                    if (!TextUtils.isEmpty(number)) {
                        /// M: @{
                        // Original code:
                        // info = CallerInfo.getCallerInfo(context, number);
                        int simId = PhoneWrapper.getSlotNotIdle(PhoneGlobals.getInstance().phone);
                        if (DBG) {
                            log("simId=" + simId);
                        }
                        info = PhoneWrapper.getCallerInfo(context, number, simId);
                        /// @}
                        if (info != null) {
                            c.setUserData(info);
                        }
                    }
                }
            }
        }
        return info;
    }

    /**
     * Class returned by the startGetCallerInfo call to package a temporary
     * CallerInfo Object, to be superceded by the CallerInfo Object passed
     * into the listener when the query with token mAsyncQueryToken is complete.
     */
    public static class CallerInfoToken {
        /**indicates that there will no longer be updates to this request.*/
        public boolean isFinal;

        public CallerInfo currentInfo;
        public CallerInfoAsyncQuery asyncQuery;
    }

    /**
     * Start a CallerInfo Query based on the earliest connection in the call.
     */
    public static CallerInfoToken startGetCallerInfo(Context context, Call call,
            CallerInfoAsyncQuery.OnQueryCompleteListener listener, Object cookie) {
        Connection conn = null;
        int phoneType = call.getPhone().getPhoneType();
        if (phoneType == PhoneConstants.PHONE_TYPE_CDMA) {
            conn = call.getLatestConnection();
        } else if ((phoneType == PhoneConstants.PHONE_TYPE_GSM)
                || (phoneType == PhoneConstants.PHONE_TYPE_SIP)) {
            conn = call.getEarliestConnection();
        } else {
            throw new IllegalStateException("Unexpected phone type: " + phoneType);
        }

        return startGetCallerInfo(context, conn, listener, cookie);
    }

    public static CallerInfoToken startGetCallerInfo(Context context, Connection c,
            CallerInfoAsyncQuery.OnQueryCompleteListener listener, Object cookie) {
        return startGetCallerInfo(context, c, listener, cookie, null);
    }
    /**
     * place a temporary callerinfo object in the hands of the caller and notify
     * caller when the actual query is done.
     */
    public static CallerInfoToken startGetCallerInfo(Context context, Connection c,
            CallerInfoAsyncQuery.OnQueryCompleteListener listener, Object cookie,
            RawGatewayInfo info) {
        CallerInfoToken cit;

        if (c == null) {
            //TODO: perhaps throw an exception here.
            cit = new CallerInfoToken();
            cit.asyncQuery = null;
            return cit;
        }
        /// M: Sip Connection or not
        boolean isSipConn = c.getCall().getPhone().getPhoneType() == PhoneConstants.PHONE_TYPE_SIP;

        Object userDataObject = c.getUserData();

        // There are now 3 states for the Connection's userData object:
        //
        //   (1) Uri - query has not been executed yet
        //
        //   (2) CallerInfoToken - query is executing, but has not completed.
        //
        //   (3) CallerInfo - query has executed.
        //
        // In each case we have slightly different behaviour:
        //   1. If the query has not been executed yet (Uri or null), we start
        //      query execution asynchronously, and note it by attaching a
        //      CallerInfoToken as the userData.
        //   2. If the query is executing (CallerInfoToken), we've essentially
        //      reached a state where we've received multiple requests for the
        //      same callerInfo.  That means that once the query is complete,
        //      we'll need to execute the additional listener requested.
        //   3. If the query has already been executed (CallerInfo), we just
        //      return the CallerInfo object as expected.
        //   4. Regarding isFinal - there are cases where the CallerInfo object
        //      will not be attached, like when the number is empty (caller id
        //      blocking).  This flag is used to indicate that the
        //      CallerInfoToken object is going to be permanent since no
        //      query results will be returned.  In the case where a query
        //      has been completed, this flag is used to indicate to the caller
        //      that the data will not be updated since it is valid.
        //
        //      Note: For the case where a number is NOT retrievable, we leave
        //      the CallerInfo as null in the CallerInfoToken.  This is
        //      something of a departure from the original code, since the old
        //      code manufactured a CallerInfo object regardless of the query
        //      outcome.  From now on, we will append an empty CallerInfo
        //      object, to mirror previous behaviour, and to avoid Null Pointer
        //      Exceptions.

        /// M:Gemini+ @{
        int slotId = PhoneWrapper.getSlotNotIdle(PhoneGlobals.getInstance().phone);
        PhoneLog.d(LOG_TAG, "startGetCallerInfo slotId=" + slotId);
        /// @}

        if (userDataObject instanceof Uri) {
            // State (1): query has not been executed yet

            //create a dummy callerinfo, populate with what we know from URI.
            cit = new CallerInfoToken();
            cit.currentInfo = new CallerInfo();
            cit.asyncQuery = CallerInfoAsyncQuery.startQuery(QUERY_TOKEN, context,
                    (Uri) userDataObject, sCallerInfoQueryListener, c);
            cit.asyncQuery.addQueryListener(QUERY_TOKEN, listener, cookie);
            cit.isFinal = false;

            c.setUserData(cit);

            if (DBG) log("startGetCallerInfo: query based on Uri: " + userDataObject);

        } else if (userDataObject == null) {
            // No URI, or Existing CallerInfo, so we'll have to make do with
            // querying a new CallerInfo using the connection's phone number.
            String number = c.getAddress();
            if (info != null && info != CallGatewayManager.EMPTY_INFO) {
                // Gateway number, the connection number is actually the gateway number.
                // need to lookup via dialed number.
                number = info.trueNumber;
            }

            if (DBG) {
                log("PhoneUtils.startGetCallerInfo: new query for phone number...");
                log("- number (address): " + toLogSafePhoneNumber(number));
                log("- c: " + c);
                log("- phone: " + c.getCall().getPhone());
                int phoneType = c.getCall().getPhone().getPhoneType();
                log("- phoneType: " + phoneType);
                switch (phoneType) {
                    case PhoneConstants.PHONE_TYPE_NONE: log("  ==> PHONE_TYPE_NONE"); break;
                    case PhoneConstants.PHONE_TYPE_GSM: log("  ==> PHONE_TYPE_GSM"); break;
                    case PhoneConstants.PHONE_TYPE_CDMA: log("  ==> PHONE_TYPE_CDMA"); break;
                    case PhoneConstants.PHONE_TYPE_SIP: log("  ==> PHONE_TYPE_SIP"); break;
                    default: log("  ==> Unknown phone type"); break;
                }
            }

            cit = new CallerInfoToken();
            cit.currentInfo = new CallerInfo();

            // Store CNAP information retrieved from the Connection (we want to do this
            // here regardless of whether the number is empty or not).
            cit.currentInfo.cnapName =  c.getCnapName();
            cit.currentInfo.name = cit.currentInfo.cnapName; // This can still get overwritten
                                                             // by ContactInfo later
            cit.currentInfo.numberPresentation = c.getNumberPresentation();
            cit.currentInfo.namePresentation = c.getCnapNamePresentation();

            if (DBG) {
                log("startGetCallerInfo: number = " + number);
                log("startGetCallerInfo: CNAP Info from FW(1): name="
                    + cit.currentInfo.cnapName
                    + ", Name/Number Pres=" + cit.currentInfo.numberPresentation);
            }

            // handling case where number is null (caller id hidden) as well.
            if (!TextUtils.isEmpty(number)) {
                // Check for special CNAP cases and modify the CallerInfo accordingly
                // to be sure we keep the right information to display/log later
                number = modifyForSpecialCnapCases(context, cit.currentInfo, number,
                        cit.currentInfo.numberPresentation);

                cit.currentInfo.phoneNumber = number;
                // For scenarios where we may receive a valid number from the network but a
                // restricted/unavailable presentation, we do not want to perform a contact query
                // (see note on isFinal above). So we set isFinal to true here as well.
                if (cit.currentInfo.numberPresentation != PhoneConstants.PRESENTATION_ALLOWED) {
                    cit.isFinal = true;
                } else {
                    if (DBG) log("==> Actually starting CallerInfoAsyncQuery.startQuery()...");
                    /// M: Gemini + @{ 
                    /* Original code:
                    cit.asyncQuery = CallerInfoAsyncQuery.startQuery(QUERY_TOKEN, context,
                            number, sCallerInfoQueryListener, c); */
                    cit.asyncQuery = GeminiUtils.startQueryGemini(QUERY_TOKEN, context, number,
                            sCallerInfoQueryListener, c, slotId, isSipConn);
                    /// @}

                    cit.asyncQuery.addQueryListener(QUERY_TOKEN, listener, cookie);
                    cit.isFinal = false;
                }
            } else {
                // This is the case where we are querying on a number that
                // is null or empty, like a caller whose caller id is
                // blocked or empty (CLIR).  The previous behaviour was to
                // throw a null CallerInfo object back to the user, but
                // this departure is somewhat cleaner.
                if (DBG) log("startGetCallerInfo: No query to start, send trivial reply.");
                cit.isFinal = true; // please see note on isFinal, above.
            }

            c.setUserData(cit);

            if (DBG) {
                log("startGetCallerInfo: query based on number: " + toLogSafePhoneNumber(number));
            }

        } else if (userDataObject instanceof CallerInfoToken) {
            // State (2): query is executing, but has not completed.

            // just tack on this listener to the queue.
            cit = (CallerInfoToken) userDataObject;

            // handling case where number is null (caller id hidden) as well.
            if (cit.asyncQuery != null) {
                cit.asyncQuery.addQueryListener(QUERY_TOKEN, listener, cookie);

                if (DBG) log("startGetCallerInfo: query already running, adding listener: " +
                        listener.getClass().toString());
            } else {
                // handling case where number/name gets updated later on by the network
                String updatedNumber = c.getAddress();
                if (info != null) {
                    // Gateway number, the connection number is actually the gateway number.
                    // need to lookup via dialed number.
                    updatedNumber = info.trueNumber;
                }
                if (DBG) {
                    log("startGetCallerInfo: updatedNumber initially = "
                            + toLogSafePhoneNumber(updatedNumber));
                }
                if (!TextUtils.isEmpty(updatedNumber)) {
                    // Store CNAP information retrieved from the Connection
                    cit.currentInfo.cnapName =  c.getCnapName();
                    // This can still get overwritten by ContactInfo
                    cit.currentInfo.name = cit.currentInfo.cnapName;
                    cit.currentInfo.numberPresentation = c.getNumberPresentation();
                    cit.currentInfo.namePresentation = c.getCnapNamePresentation();

                    updatedNumber = modifyForSpecialCnapCases(context, cit.currentInfo,
                            updatedNumber, cit.currentInfo.numberPresentation);

                    cit.currentInfo.phoneNumber = updatedNumber;
                    if (DBG) {
                        log("startGetCallerInfo: updatedNumber="
                                + toLogSafePhoneNumber(updatedNumber));
                    }
                    if (DBG) {
                        log("startGetCallerInfo: CNAP Info from FW(2): name="
                                + cit.currentInfo.cnapName
                                + ", Name/Number Pres=" + cit.currentInfo.numberPresentation);
                    } else if (DBG) {
                        log("startGetCallerInfo: CNAP Info from FW(2)");
                    }
                    // For scenarios where we may receive a valid number from the network but a
                    // restricted/unavailable presentation, we do not want to perform a contact query
                    // (see note on isFinal above). So we set isFinal to true here as well.
                    if (cit.currentInfo.numberPresentation != PhoneConstants.PRESENTATION_ALLOWED) {
                        cit.isFinal = true;
                    } else {
                        /// M:Gemini+ @{
                        // Original code:
                        // cit.asyncQuery = CallerInfoAsyncQuery.startQuery(QUERY_TOKEN, context,
                        //        updatedNumber, sCallerInfoQueryListener, c);
                        cit.asyncQuery = GeminiUtils.startQueryGemini(QUERY_TOKEN, context, updatedNumber,
                                sCallerInfoQueryListener, c, slotId, isSipConn);
                        /// @}
                        cit.asyncQuery.addQueryListener(QUERY_TOKEN, listener, cookie);
                        cit.isFinal = false;
                    }
                } else {
                    if (DBG) log("startGetCallerInfo: No query to attach to, send trivial reply.");
                    if (cit.currentInfo == null) {
                        cit.currentInfo = new CallerInfo();
                    }
                    // Store CNAP information retrieved from the Connection
                    cit.currentInfo.cnapName = c.getCnapName();  // This can still get
                                                                 // overwritten by ContactInfo
                    cit.currentInfo.name = cit.currentInfo.cnapName;
                    cit.currentInfo.numberPresentation = c.getNumberPresentation();
                    cit.currentInfo.namePresentation = c.getCnapNamePresentation();

                    if (DBG) {
                        log("startGetCallerInfo: CNAP Info from FW(3): name="
                                + cit.currentInfo.cnapName
                                + ", Name/Number Pres=" + cit.currentInfo.numberPresentation);
                    } else if (DBG) {
                        log("startGetCallerInfo: CNAP Info from FW(3)");
                    }
                    cit.isFinal = true; // please see note on isFinal, above.
                }
            }
        } else {
            // State (3): query is complete.

            // The connection's userDataObject is a full-fledged
            // CallerInfo instance.  Wrap it in a CallerInfoToken and
            // return it to the user.

            cit = new CallerInfoToken();
            cit.currentInfo = (CallerInfo) userDataObject;
            cit.asyncQuery = null;
            cit.isFinal = true;
            // since the query is already done, call the listener.
            if (DBG) log("startGetCallerInfo: query already done, returning CallerInfo");
            if (DBG) log("==> cit.currentInfo = " + cit.currentInfo);
        }
        return cit;
    }

    /**
     * Static CallerInfoAsyncQuery.OnQueryCompleteListener instance that
     * we use with all our CallerInfoAsyncQuery.startQuery() requests.
     */
    private static final int QUERY_TOKEN = -1;
    static CallerInfoAsyncQuery.OnQueryCompleteListener sCallerInfoQueryListener =
        new CallerInfoAsyncQuery.OnQueryCompleteListener () {
            /**
             * When the query completes, we stash the resulting CallerInfo
             * object away in the Connection's "userData" (where it will
             * later be retrieved by the in-call UI.)
             */
            public void onQueryComplete(int token, Object cookie, CallerInfo ci) {
                if (DBG) log("query complete, updating connection.userdata");
                Connection conn = (Connection) cookie;
                /// M: Sip or not
                boolean isSipConn = conn.getCall().getPhone().getPhoneType() == PhoneConstants.PHONE_TYPE_SIP;
                // Added a check if CallerInfo is coming from ContactInfo or from Connection.
                // If no ContactInfo, then we want to use CNAP information coming from network
                if (DBG) log("- onQueryComplete: CallerInfo:" + ci);
                if (ci.contactExists || ci.isEmergencyNumber() || ci.isVoiceMailNumber()) {
                    // If the number presentation has not been set by
                    // the ContactInfo, use the one from the
                    // connection.

                    // TODO: Need a new util method to merge the info
                    // from the Connection in a CallerInfo object.
                    // Here 'ci' is a new CallerInfo instance read
                    // from the DB. It has lost all the connection
                    // info preset before the query (see PhoneUtils
                    // line 1334). We should have a method to merge
                    // back into this new instance the info from the
                    // connection object not set by the DB. If the
                    // Connection already has a CallerInfo instance in
                    // userData, then we could use this instance to
                    // fill 'ci' in. The same routine could be used in
                    // PhoneUtils.
                    if (0 == ci.numberPresentation) {
                        ci.numberPresentation = conn.getNumberPresentation();
                    }
                } else {
                    // No matching contact was found for this number.
                    // Return a new CallerInfo based solely on the CNAP
                    // information from the network.

                    CallerInfo newCi = getCallerInfo(null, conn);

                    // ...but copy over the (few) things we care about
                    // from the original CallerInfo object:
                    if (newCi != null) {
                        newCi.phoneNumber = ci.phoneNumber; // To get formatted phone number
                        newCi.geoDescription = ci.geoDescription; // To get geo description string
                        ci = newCi;
                    }
                }

                /// M: @{
                // We don't show the voice mail when the voice mail is set by cell network
                if (isSipConn && !ci.contactExists && !ci.isEmergencyNumber() && ci.isVoiceMailNumber()) {
                    ci.phoneNumber = conn.getAddress();
                }
                /// @}

                if (DBG) log("==> Stashing CallerInfo " + ci + " into the connection...");
                conn.setUserData(ci);
            }
        };


    /**
     * Returns a single "name" for the specified given a CallerInfo object.
     * If the name is null, return defaultString as the default value, usually
     * context.getString(R.string.unknown).
     */
    public static String getCompactNameFromCallerInfo(CallerInfo ci, Context context) {
        if (DBG) log("getCompactNameFromCallerInfo: info = " + ci);

        String compactName = null;
        if (ci != null) {
            if (TextUtils.isEmpty(ci.name)) {
                // Perform any modifications for special CNAP cases to
                // the phone number being displayed, if applicable.
                compactName = modifyForSpecialCnapCases(context, ci, ci.phoneNumber,
                                                        ci.numberPresentation);
            } else {
                // Don't call modifyForSpecialCnapCases on regular name. See b/2160795.
                compactName = ci.name;
            }
        }

        if ((compactName == null) || (TextUtils.isEmpty(compactName))) {
            // If we're still null/empty here, then check if we have a presentation
            // string that takes precedence that we could return, otherwise display
            // "unknown" string.

            /// M: For ALPS00891611
            // if number is not null and Callerinfo.numberPresentation equal 1, open the override function.
            // original code
            // if (ci != null && ci.numberPresentation == PhoneConstants.PRESENTATION_RESTRICTED) {
            if (ci != null
                    && TextUtils.isEmpty(ci.phoneNumber)
                    && ci.numberPresentation == PhoneConstants.PRESENTATION_RESTRICTED) {
                compactName = context.getString(R.string.private_num);
            } else if (ci != null && ci.numberPresentation == PhoneConstants.PRESENTATION_PAYPHONE) {
                compactName = context.getString(R.string.payphone);
            } else {
                compactName = context.getString(R.string.unknown);
            }
        }
        if (DBG) log("getCompactNameFromCallerInfo: compactName=" + compactName);
        return compactName;
    }

    /**
     * Returns true if the specified Call is a "conference call", meaning
     * that it owns more than one Connection object.  This information is
     * used to trigger certain UI changes that appear when a conference
     * call is active (like displaying the label "Conference call", and
     * enabling the "Manage conference" UI.)
     *
     * Watch out: This method simply checks the number of Connections,
     * *not* their states.  So if a Call has (for example) one ACTIVE
     * connection and one DISCONNECTED connection, this method will return
     * true (which is unintuitive, since the Call isn't *really* a
     * conference call any more.)
     *
     * @return true if the specified call has more than one connection (in any state.)
     */
    static boolean isConferenceCall(Call call) {
        // CDMA phones don't have the same concept of "conference call" as
        // GSM phones do; there's no special "conference call" state of
        // the UI or a "manage conference" function.  (Instead, when
        // you're in a 3-way call, all we can do is display the "generic"
        // state of the UI.)  So as far as the in-call UI is concerned,
        // Conference corresponds to generic display.
        final PhoneGlobals app = PhoneGlobals.getInstance();
        int phoneType = call.getPhone().getPhoneType();
        if (phoneType == PhoneConstants.PHONE_TYPE_CDMA) {
            CdmaPhoneCallState.PhoneCallState state = app.cdmaPhoneCallState.getCurrentCallState();
            if ((state == CdmaPhoneCallState.PhoneCallState.CONF_CALL)
                    || ((state == CdmaPhoneCallState.PhoneCallState.THRWAY_ACTIVE)
                    && !app.cdmaPhoneCallState.IsThreeWayCallOrigStateDialing())) {
                return true;
            }
        /// M: @{
        /*
         * Original Code:
        } else {
            List<Connection> connections = call.getConnections();
            if (connections != null && connections.size() > 1) {
                return true;
            }
        }
        */
        } else if (phoneType == PhoneConstants.PHONE_TYPE_GSM) {
            return call.isMptyCall;
        } else if (phoneType == PhoneConstants.PHONE_TYPE_SIP) {
            // clear the disconnected connection.
            if (Call.State.INCOMING == call.getState()) {
                call.getPhone().clearDisconnected();
            }
            List<Connection> connections = call.getConnections();
            if (connections != null && connections.size() > 1) {
                return true;
            }
        }
        /// @}
        return false;

        // TODO: We may still want to change the semantics of this method
        // to say that a given call is only really a conference call if
        // the number of ACTIVE connections, not the total number of
        // connections, is greater than one.  (See warning comment in the
        // javadoc above.)
        // Here's an implementation of that:
        //        if (connections == null) {
        //            return false;
        //        }
        //        int numActiveConnections = 0;
        //        for (Connection conn : connections) {
        //            if (DBG) log("  - CONN: " + conn + ", state = " + conn.getState());
        //            if (conn.getState() == Call.State.ACTIVE) numActiveConnections++;
        //            if (numActiveConnections > 1) {
        //                return true;
        //            }
        //        }
        //        return false;
    }

    /**
     * Launch the Dialer to start a new call.
     * This is just a wrapper around the ACTION_DIAL intent.
     */
    /* package */ static boolean startNewCall(final CallManager cm) {
        final PhoneGlobals app = PhoneGlobals.getInstance();

        // Sanity-check that this is OK given the current state of the phone.
        if (!okToAddCall(cm)) {
            Log.w(LOG_TAG, "startNewCall: can't add a new call in the current state");
            dumpCallManager();
            return false;
        }

        /// M: design change for ALPS01262892 && ALPS01258249 && ALPS01236444 @{
        // the mute function when add call is not well designed on KK
        // the mute && unmute action should be done only in Telephony 
        // if applicable, mute the call while we're showing the add call UI.
        if (cm.hasActiveFgCall()) {
            setMuteInternal(cm.getActiveFgCall().getPhone(), true);
        }
        /// @}

        Intent intent = new Intent(Intent.ACTION_DIAL);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        // when we request the dialer come up, we also want to inform
        // it that we're going through the "add call" option from the
        // InCallScreen / PhoneUtils.
        intent.putExtra(ADD_CALL_MODE_KEY, true);
        try {
            app.startActivity(intent);
        } catch (ActivityNotFoundException e) {
            // This is rather rare but possible.
            // Note: this method is used even when the phone is encrypted. At that moment
            // the system may not find any Activity which can accept this Intent.
            Log.e(LOG_TAG, "Activity for adding calls isn't found.");
            return false;
        }

        return true;
    }

    /**
     * Turns on/off speaker.
     *
     * @param context Context
     * @param flag True when speaker should be on. False otherwise.
     * @param store True when the settings should be stored in the device.
     */
    public static void turnOnSpeaker(Context context, boolean flag, boolean store) {
        turnOnSpeaker(context, flag, store, true);
    }
    
    public static void turnOnSpeaker(Context context, boolean flag, boolean store, boolean isUpdateNotification) {
        if (DBG) log("turnOnSpeaker(flag=" + flag + ", store=" + store + ")...");
        final PhoneGlobals app = PhoneGlobals.getInstance();

        AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        audioManager.setSpeakerphoneOn(flag);

        // record the speaker-enable value
        if (store) {
            sIsSpeakerEnabled = flag;
        }

        /// M: @{
        // Update the status bar icon
        if (isUpdateNotification) {
        /// @}
            app.notificationMgr.updateSpeakerNotification(flag);
        }

        // We also need to make a fresh call to PhoneApp.updateWakeState()
        // any time the speaker state changes, since the screen timeout is
        // sometimes different depending on whether or not the speaker is
        // in use.
        app.updateWakeState();

        app.mCM.setEchoSuppressionEnabled(flag);
    }

    /**
     * Restore the speaker mode, called after a wired headset disconnect
     * event.
     */
    static void restoreSpeakerMode(Context context) {
        if (DBG) log("restoreSpeakerMode, restoring to: " + sIsSpeakerEnabled);

        if (!forceSpeakerOn(context, false)) {

            if (isSpeakerOn(context) != sIsSpeakerEnabled) {
                turnOnSpeaker(context, sIsSpeakerEnabled, false);
            }
        }
    }

    static boolean isSpeakerOn(Context context) {
        AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        return audioManager.isSpeakerphoneOn();
    }


    static void turnOnNoiseSuppression(Context context, boolean flag, boolean store) {
        if (DBG) log("turnOnNoiseSuppression: " + flag);
        AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);

        if (!context.getResources().getBoolean(R.bool.has_in_call_noise_suppression)) {
            return;
        }

        if (flag) {
            audioManager.setParameters("noise_suppression=auto");
        } else {
            audioManager.setParameters("noise_suppression=off");
        }

        // record the speaker-enable value
        if (store) {
            sIsNoiseSuppressionEnabled = flag;
        }

        // TODO: implement and manage ICON

    }

    static void restoreNoiseSuppression(Context context) {
        if (DBG) log("restoreNoiseSuppression, restoring to: " + sIsNoiseSuppressionEnabled);

        if (!context.getResources().getBoolean(R.bool.has_in_call_noise_suppression)) {
            return;
        }

        // change the mode if needed.
        if (isNoiseSuppressionOn(context) != sIsNoiseSuppressionEnabled) {
            turnOnNoiseSuppression(context, sIsNoiseSuppressionEnabled, false);
        }
    }

    static boolean isNoiseSuppressionOn(Context context) {

        if (!context.getResources().getBoolean(R.bool.has_in_call_noise_suppression)) {
            return false;
        }

        AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        String noiseSuppression = audioManager.getParameters("noise_suppression");
        if (DBG) log("isNoiseSuppressionOn: " + noiseSuppression);
        if (noiseSuppression.contains("off")) {
            return false;
        } else {
            return true;
        }
    }

    /**
     *
     * Mute / umute the foreground phone, which has the current foreground call
     *
     * All muting / unmuting from the in-call UI should go through this
     * wrapper.
     *
     * Wrapper around Phone.setMute() and setMicrophoneMute().
     * It also updates the connectionMuteTable and mute icon in the status bar.
     *
     */
    static void setMute(boolean muted) {
        CallManager cm = PhoneGlobals.getInstance().mCM;

        if (isInEmergencyCall(cm)) {
            muted = false;
        }
        // make the call to mute the audio
        setMuteInternal(cm.getFgPhone(), muted);

        /// M: @{
        Call fgCall = null;
        DualTalkUtils dt = DualTalkUtils.getInstance();
        if (DualTalkUtils.isSupportDualTalk() && dt != null
                && dt.isCdmaAndGsmActive()) {
            fgCall = dt.getActiveFgCall();
        } else {
            fgCall = PhoneGlobals.getInstance().mCM.getActiveFgCall();
        }
        /// @}
        // update the foreground connections to match.  This includes
        // all the connections on conference calls.
        for (Connection cn : fgCall.getConnections()) {
            if (sConnectionMuteTable.get(cn) == null) {
                if (DBG) log("problem retrieving mute value for this connection.");
            }
            sConnectionMuteTable.put(cn, Boolean.valueOf(muted));
        }

        /// M: design change for ALPS01262892 && ALPS01258249 && ALPS01236444 @{
        // the mute function when add call is not well designed on KK
        // do not mute/unmute background connections
        // MTK delete

        /*
        // update the background connections to match.  This includes
        // all the connections on conference calls.
        if (cm.hasActiveBgCall()) {
            for (Connection cn : cm.getFirstActiveBgCall().getConnections()) {
                if (sConnectionMuteTable.get(cn) == null) {
                    if (DBG) log("problem retrieving mute value for this connection.");
                }
                ///M: if is bgCall, we should keep the original mute state. @{
                Boolean state = sConnectionMuteTable.get(cn);
                if(state == null){
                    state = Boolean.valueOf(muted);
                }
                /// @}
                sConnectionMuteTable.put(cn, state);
            }
        }
        */
    }

    static boolean isInEmergencyCall(CallManager cm) {
        for (Connection cn : cm.getActiveFgCall().getConnections()) {
            if (PhoneNumberUtils.isLocalEmergencyNumber(cn.getAddress(),
                    PhoneGlobals.getInstance())) {
                return true;
            }
        }
        return false;
    }


    /**
     * Internally used muting function.
     */
    private static void setMuteInternal(Phone phone, boolean muted) {
        final PhoneGlobals app = PhoneGlobals.getInstance();
        Context context = phone.getContext();

        /// M: For ALPS01265452. @{
        // save the old state firstly.
        boolean oldMuted = getMute();
        /// @}

        boolean routeToAudioManager =
            context.getResources().getBoolean(R.bool.send_mic_mute_to_AudioManager);
        if (routeToAudioManager) {
            AudioManager audioManager =
                (AudioManager) phone.getContext().getSystemService(Context.AUDIO_SERVICE);
            if (DBG) log("setMuteInternal: using setMicrophoneMute(" + muted + ")...");
            audioManager.setMicrophoneMute(muted);
        } else {
            if (DBG) log("setMuteInternal: using phone.setMute(" + muted + ")...");
            phone.setMute(muted);
        }
        app.notificationMgr.updateMuteNotification();
        /// M: For ALPS01265452. @{
        // If mute state is really changed, we notify listeners.
        if (oldMuted != muted) {
            app.getAudioRouter().onMuteChange(muted);
        }
        /// @}
    }

    /**
     * Get the mute state of foreground phone, which has the current
     * foreground call
     */
    public static boolean getMute() {
        final PhoneGlobals app = PhoneGlobals.getInstance();

        boolean routeToAudioManager =
            app.getResources().getBoolean(R.bool.send_mic_mute_to_AudioManager);
        if (routeToAudioManager) {
            AudioManager audioManager =
                (AudioManager) app.getSystemService(Context.AUDIO_SERVICE);
            return audioManager.isMicrophoneMute();
        } else {
            return app.mCM.getMute();
        }
    }

    /* package */ public static void setAudioMode() {
        setAudioMode(PhoneGlobals.getInstance().mCM);
    }

    /**
     * Sets the audio mode per current phone state.
     */
    /* package */ static void setAudioMode(CallManager cm) {
        if (DBG) Log.d(LOG_TAG, "setAudioMode()..." + cm.getState());

        Context context = PhoneGlobals.getInstance();
        AudioManager audioManager = (AudioManager)
                context.getSystemService(Context.AUDIO_SERVICE);
        int modeBefore = audioManager.getMode();

        /// M: @{
        boolean isSipPhone = cm.getFgPhone().getPhoneType() == PhoneConstants.PHONE_TYPE_SIP;
        int mode = getExpectedAudioMode();
        if (PhoneUtils.isSupportFeature("TTY") && !isSipPhone) {
            if ((mode == AudioManager.MODE_NORMAL) && (!sTtyMode.equals("tty_off") && sIsOpen)) {
                audioManager.setParameters("tty_mode=" + "tty_off");
                sIsOpen = false;
            }
        }

        cm.setAudioMode();

        if (PhoneUtils.isSupportFeature("TTY") && !isSipPhone) {
            if ((mode == AudioManager.MODE_IN_CALL) && (!sTtyMode.equals("tty_off") && !sIsOpen)) {
                audioManager.setParameters("tty_mode=" + sTtyMode);
                sIsOpen = true;
            }
        }
        /// @}

        int modeAfter = audioManager.getMode();

        if (modeBefore != modeAfter) {
            // Enable stack dump only when actively debugging ("new Throwable()" is expensive!)
            if (DBG_SETAUDIOMODE_STACK) Log.d(LOG_TAG, "Stack:", new Throwable("stack dump"));
        } else {
            if (DBG) Log.d(LOG_TAG, "setAudioMode() no change: "
                    + audioModeToString(modeBefore));
        }
    }

    private static String audioModeToString(int mode) {
        switch (mode) {
            case AudioManager.MODE_INVALID: return "MODE_INVALID";
            case AudioManager.MODE_CURRENT: return "MODE_CURRENT";
            case AudioManager.MODE_NORMAL: return "MODE_NORMAL";
            case AudioManager.MODE_RINGTONE: return "MODE_RINGTONE";
            case AudioManager.MODE_IN_CALL: return "MODE_IN_CALL";
            default: return String.valueOf(mode);
        }
    }

    /**
     * Handles the wired headset button while in-call.
     *
     * This is called from the PhoneApp, not from the InCallScreen,
     * since the HEADSETHOOK button means "mute or unmute the current
     * call" *any* time a call is active, even if the user isn't actually
     * on the in-call screen.
     *
     * @return true if we consumed the event.
     */
    /* package */ static boolean handleHeadsetHook(Phone phone, KeyEvent event) {
        if (DBG) log("handleHeadsetHook()..." + event.getAction() + " " + event.getRepeatCount());
        final PhoneGlobals app = PhoneGlobals.getInstance();

        // If the phone is totally idle, we ignore HEADSETHOOK events
        // (and instead let them fall through to the media player.)
        if (phone.getState() == PhoneConstants.State.IDLE) {
            return false;
        }

        // Ok, the phone is in use.
        // The headset button button means "Answer" if an incoming call is
        // ringing.  If not, it toggles the mute / unmute state.
        //
        // And in any case we *always* consume this event; this means
        // that the usual mediaplayer-related behavior of the headset
        // button will NEVER happen while the user is on a call.

        final boolean hasRingingCall = !phone.getRingingCall().isIdle();
        final boolean hasActiveCall = !phone.getForegroundCall().isIdle();
        final boolean hasHoldingCall = !phone.getBackgroundCall().isIdle();

        if (hasRingingCall &&
            event.getRepeatCount() == 0 &&
            event.getAction() == KeyEvent.ACTION_UP) {
            // If an incoming call is ringing, answer it (just like with the
            // CALL button):
            int phoneType = phone.getPhoneType();
            if (phoneType == PhoneConstants.PHONE_TYPE_CDMA) {
                answerCall(phone.getRingingCall());
            } else if ((phoneType == PhoneConstants.PHONE_TYPE_GSM)
                    || (phoneType == PhoneConstants.PHONE_TYPE_SIP)) {
                if (hasActiveCall && hasHoldingCall) {
                    if (DBG) log("handleHeadsetHook: ringing (both lines in use) ==> answer!");
                    answerAndEndActive(app.mCM, phone.getRingingCall());
                } else {
                    if (DBG) log("handleHeadsetHook: ringing ==> answer!");
                    // answerCall() will automatically hold the current
                    // active call, if there is one.
                    answerCall(phone.getRingingCall());
                }
            } else {
                throw new IllegalStateException("Unexpected phone type: " + phoneType);
            }
        } else {
            // No incoming ringing call.
            if (event.isLongPress()) {
                if (DBG) log("handleHeadsetHook: longpress -> hangup");
                hangup(app.mCM);
            }
            else if (event.getAction() == KeyEvent.ACTION_UP &&
                     event.getRepeatCount() == 0) {
                Connection c = phone.getForegroundCall().getLatestConnection();
                // If it is NOT an emg #, toggle the mute state. Otherwise, ignore the hook.
                if (c != null && !PhoneNumberUtils.isLocalEmergencyNumber(c.getAddress(),
                                                                          PhoneGlobals.getInstance())) {
                    if (getMute()) {
                        if (DBG) log("handleHeadsetHook: UNmuting...");
                        setMute(false);
                    } else {
                        if (DBG) log("handleHeadsetHook: muting...");
                        setMute(true);
                    }
                }
            }
        }

        // Even if the InCallScreen is the current activity, there's no
        // need to force it to update, because (1) if we answered a
        // ringing call, the InCallScreen will imminently get a phone
        // state change event (causing an update), and (2) if we muted or
        // unmuted, the setMute() call automagically updates the status
        // bar, and there's no "mute" indication in the InCallScreen
        // itself (other than the menu item, which only ever stays
        // onscreen for a second anyway.)
        // TODO: (2) isn't entirely true anymore. Once we return our result
        // to the PhoneGlobals, we ask InCallScreen to update its control widgets
        // in case we changed mute or speaker state and phones with touch-
        // screen [toggle] buttons need to update themselves.

        return true;
    }

    /**
     * Look for ANY connections on the phone that qualify as being
     * disconnected.
     *
     * @return true if we find a connection that is disconnected over
     * all the phone's call objects.
     */
    /* package */ static boolean hasDisconnectedConnections(CallManager cm) {
        /*
        return hasDisconnectedConnections(phone.getForegroundCall()) ||
                hasDisconnectedConnections(phone.getBackgroundCall()) ||
                hasDisconnectedConnections(phone.getRingingCall());
        */
        /// M: 
        return hasDisconnectedConnections(cm.getActiveFgCall()) ||
            hasDisconnectedConnections(cm.getFirstActiveBgCall()) ||
            hasDisconnectedConnections(cm.getFirstActiveRingingCall());
    }

    /**
     * Iterate over all connections in a call to see if there are any
     * that are not alive (disconnected or idle).
     *
     * @return true if we find a connection that is disconnected, and
     * pending removal via
     * {@link com.android.internal.telephony.gsm.GsmCall#clearDisconnected()}.
     */
    private static final boolean hasDisconnectedConnections(Call call) {
        // look through all connections for non-active ones.

        /// M: For ALPS00953426 @{
        // After called call.getConnections() in sub thread, may switch to main thread.
        // And in main thread, will clear connections in call object.
        // After that, will cause null pointer exception.
        //
        // Google code:
        /*
        for (Connection c : call.getConnections()) {
            if (!c.isAlive()) {
                return true;
            }
        }
        */
        try {
            for (Connection c : call.getConnections()) {
                if (!c.isAlive()) {
                    return true;
                }
            }
        } catch (NullPointerException e) {
            log("The connections is set to null: " + e);
        } catch (ConcurrentModificationException e) {
            log("The ArrayListIterator has got exception: " + e);
        }
        /// @}

        return false;
    }

    //
    // Misc UI policy helper functions
    //

    /**
     * @return true if we're allowed to hold calls, given the current
     * state of the Phone.
     */
    /* package */ static boolean okToHoldCall(CallManager cm) {
        final Call fgCall = cm.getActiveFgCall();
        final boolean hasHoldingCall = cm.hasActiveBgCall();
        final Call.State fgCallState = fgCall.getState();

        // The "Hold" control is disabled entirely if there's
        // no way to either hold or unhold in the current state.
        final boolean okToHold = (fgCallState == Call.State.ACTIVE) && !hasHoldingCall;
        final boolean okToUnhold = cm.hasActiveBgCall() && (fgCallState == Call.State.IDLE);
        final boolean canHold = okToHold || okToUnhold;

        return canHold;
    }

    /**
     * @return true if we support holding calls, given the current
     * state of the Phone.
     */
    /* package */ static boolean okToSupportHold(CallManager cm) {
        boolean supportsHold = false;

        final Call fgCall = cm.getActiveFgCall();
        final boolean hasHoldingCall = cm.hasActiveBgCall();
        final Call.State fgCallState = fgCall.getState();

        if (TelephonyCapabilities.supportsHoldAndUnhold(fgCall.getPhone())) {
            // This phone has the concept of explicit "Hold" and "Unhold" actions.
            supportsHold = true;
        } else if (hasHoldingCall && (fgCallState == Call.State.IDLE)) {
            // Even when foreground phone device doesn't support hold/unhold, phone devices
            // for background holding calls may do.
            final Call bgCall = cm.getFirstActiveBgCall();
            if (bgCall != null &&
                    TelephonyCapabilities.supportsHoldAndUnhold(bgCall.getPhone())) {
                supportsHold = true;
            }
        }
        return supportsHold;
    }

    /**
     * @return true if we're allowed to swap calls, given the current
     * state of the Phone.
     */
    /* package */ static boolean okToSwapCalls(CallManager cm) {
        int phoneType = cm.getDefaultPhone().getPhoneType();

        /// M: DualTalk @{
        DualTalkUtils dt = DualTalkUtils.getInstance();
        if (DualTalkUtils.isSupportDualTalk()) {
            Call call = dt.getActiveFgCall();
            if (call != null) {
                phoneType = call.getPhone().getPhoneType();
            }
        }

        if (DualTalkUtils.isSupportDualTalk() && dt.isCdmaAndGsmActive()) {
            Call dtFgCall = dt.getActiveFgCall();
            Call dtBgCall = dt.getFirstActiveBgCall();
            return (dtFgCall.getState() == Call.State.ACTIVE)
                    && (dtBgCall.getState() == Call.State.ACTIVE || dtBgCall.getState() == Call.State.HOLDING);
        /// @}
        } else if (phoneType == PhoneConstants.PHONE_TYPE_CDMA) {
            // CDMA: "Swap" is enabled only when the phone reaches a *generic*.
            // state by either accepting a Call Waiting or by merging two calls
            PhoneGlobals app = PhoneGlobals.getInstance();
            return (app.cdmaPhoneCallState.getCurrentCallState()
                    == CdmaPhoneCallState.PhoneCallState.CONF_CALL);
        } else if ((phoneType == PhoneConstants.PHONE_TYPE_GSM)
                || (phoneType == PhoneConstants.PHONE_TYPE_SIP)) {
            // GSM: "Swap" is available if both lines are in use and there's no
            // incoming call.  (Actually we need to verify that the active
            // call really is in the ACTIVE state and the holding call really
            // is in the HOLDING state, since you *can't* actually swap calls
            // when the foreground call is DIALING or ALERTING.)
            return !cm.hasActiveRingingCall()
                    && ((cm.getActiveFgCall().getState() == Call.State.ACTIVE || PhoneUtils.hasActivefgEccCall(cm))
                    && (cm.getFirstActiveBgCall().getState() == Call.State.HOLDING)
                    /*|| holdAndActiveFromDifPhone(cm)*/);
        } else {
            throw new IllegalStateException("Unexpected phone type: " + phoneType);
        }
    }

    /**
     * @return true if we're allowed to merge calls, given the current
     * state of the Phone.
     */
    /* package */ static boolean okToMergeCalls(CallManager cm) {
        int phoneType = cm.getFgPhone().getPhoneType();
        /// @{
        DualTalkUtils dt = DualTalkUtils.getInstance();
        if (DualTalkUtils.isSupportDualTalk()) {
            Call call = dt.getActiveFgCall();
            if (call != null) {
                phoneType = call.getPhone().getPhoneType();
            }
        }
        /// @}
        if (phoneType == PhoneConstants.PHONE_TYPE_CDMA) {
            // CDMA: "Merge" is enabled only when the user is in a 3Way call.
            PhoneGlobals app = PhoneGlobals.getInstance();
            return ((app.cdmaPhoneCallState.getCurrentCallState()
                    == CdmaPhoneCallState.PhoneCallState.THRWAY_ACTIVE)
                    && !app.cdmaPhoneCallState.IsThreeWayCallOrigStateDialing());
        } else {
            // GSM: "Merge" is available if both lines are in use and there's no
            // incoming call, *and* the current conference isn't already
            // "full".
            // TODO: shall move all okToMerge logic to CallManager
            return !cm.hasActiveRingingCall() && cm.hasActiveFgCall()
                    && cm.getActiveFgCall().getState() != Call.State.DIALING 
                    && cm.getActiveFgCall().getState() != Call.State.ALERTING 
                    && cm.hasActiveBgCall() 
                    && (cm.canConference(cm.getFirstActiveBgCall())
                            || (DualTalkUtils.isSupportDualTalk()
                                    && PhoneGlobals.getInstance().notifier.mDualTalk.isDualTalkMultipleHoldCase()));
        }
    }

    /**
     * @return true if the UI should let you add a new call, given the current
     * state of the Phone.
     */
    /* package */ static boolean okToAddCall(CallManager cm) {
        Phone phone = cm.getActiveFgCall().getPhone();
        /// M: @{
        if (DualTalkUtils.isSupportDualTalk()) {
            DualTalkUtils dt = DualTalkUtils.getInstance();
            if (dt != null) {
                phone = dt.getActiveFgCall().getPhone();
            }
        }
        /// @}
        // "Add call" is never allowed in emergency callback mode (ECM).
        if (isPhoneInEcm(phone)) {
            return false;
        }

        int phoneType = phone.getPhoneType();
        final Call.State fgCallState = cm.getActiveFgCall().getState();
        if (phoneType == PhoneConstants.PHONE_TYPE_CDMA) {
           // CDMA: "Add call" button is only enabled when:
           // - ForegroundCall is in ACTIVE state
           // - After 30 seconds of user Ignoring/Missing a Call Waiting call.
            PhoneGlobals app = PhoneGlobals.getInstance();
            return ((fgCallState == Call.State.ACTIVE)
                    && (app.cdmaPhoneCallState.getAddCallMenuStateAfterCallWaiting()));
        } else if ((phoneType == PhoneConstants.PHONE_TYPE_GSM)
                || (phoneType == PhoneConstants.PHONE_TYPE_SIP)) {
            // GSM: "Add call" is available only if ALL of the following are true:
            // - There's no incoming ringing call
            // - There's < 2 lines in use
            // - The foreground call is ACTIVE or IDLE or DISCONNECTED.
            //   (We mainly need to make sure it *isn't* DIALING or ALERTING.)
            final boolean hasRingingCall = cm.hasActiveRingingCall();
            final boolean hasActiveCall = cm.hasActiveFgCall();
            final boolean hasHoldingCall = cm.hasActiveBgCall();
            boolean allLinesTaken = hasActiveCall && hasHoldingCall;
            /// M: @{
            if (DualTalkUtils.isSupportDualTalk() && allLinesTaken) {
                allLinesTaken = !DualTalkUtils.getInstance().canAddCallForDualTalk();
            }
            /// @}

            return !hasRingingCall
                    && !allLinesTaken
                    && ((fgCallState == Call.State.ACTIVE)
                        || (fgCallState == Call.State.IDLE)
                        || (fgCallState == Call.State.DISCONNECTED));
        } else {
            throw new IllegalStateException("Unexpected phone type: " + phoneType);
        }
    }

    /**
     * Based on the input CNAP number string,
     * @return _RESTRICTED or _UNKNOWN for all the special CNAP strings.
     * Otherwise, return CNAP_SPECIAL_CASE_NO.
     */
    private static int checkCnapSpecialCases(String n) {
        if (n.equals("PRIVATE") ||
                n.equals("P") ||
                n.equals("RES")) {
            if (DBG) log("checkCnapSpecialCases, PRIVATE string: " + n);
            return PhoneConstants.PRESENTATION_RESTRICTED;
        } else if (n.equals("UNAVAILABLE") ||
                n.equals("UNKNOWN") ||
                n.equals("UNA") ||
                n.equals("U")) {
            if (DBG) log("checkCnapSpecialCases, UNKNOWN string: " + n);
            return PhoneConstants.PRESENTATION_UNKNOWN;
        } else {
            if (DBG) log("checkCnapSpecialCases, normal str. number: " + n);
            return CNAP_SPECIAL_CASE_NO;
        }
    }

    /**
     * Handles certain "corner cases" for CNAP. When we receive weird phone numbers
     * from the network to indicate different number presentations, convert them to
     * expected number and presentation values within the CallerInfo object.
     * @param number number we use to verify if we are in a corner case
     * @param presentation presentation value used to verify if we are in a corner case
     * @return the new String that should be used for the phone number
     */
    /* package */ static String modifyForSpecialCnapCases(Context context, CallerInfo ci,
            String number, int presentation) {
        // Obviously we return number if ci == null, but still return number if
        // number == null, because in these cases the correct string will still be
        // displayed/logged after this function returns based on the presentation value.
        if (ci == null || number == null) return number;

        if (DBG) {
            log("modifyForSpecialCnapCases: initially, number="
                    + toLogSafePhoneNumber(number)
                    + ", presentation=" + presentation + " ci " + ci);
        }

        // "ABSENT NUMBER" is a possible value we could get from the network as the
        // phone number, so if this happens, change it to "Unknown" in the CallerInfo
        // and fix the presentation to be the same.
        final String[] absentNumberValues =
                context.getResources().getStringArray(R.array.absent_num);
        if (Arrays.asList(absentNumberValues).contains(number)
                && presentation == PhoneConstants.PRESENTATION_ALLOWED) {
            number = context.getString(R.string.unknown);
            ci.numberPresentation = PhoneConstants.PRESENTATION_UNKNOWN;
        }

        // Check for other special "corner cases" for CNAP and fix them similarly. Corner
        // cases only apply if we received an allowed presentation from the network, so check
        // if we think we have an allowed presentation, or if the CallerInfo presentation doesn't
        // match the presentation passed in for verification (meaning we changed it previously
        // because it's a corner case and we're being called from a different entry point).
        if (ci.numberPresentation == PhoneConstants.PRESENTATION_ALLOWED
                || (ci.numberPresentation != presentation
                        && presentation == PhoneConstants.PRESENTATION_ALLOWED)) {
            int cnapSpecialCase = checkCnapSpecialCases(number);
            if (cnapSpecialCase != CNAP_SPECIAL_CASE_NO) {
                // For all special strings, change number & numberPresentation.
                /// M: For ALPS00891611
                // if number is not null and Callerinfo.numberPresentation equal 1, open the override function.
                // original code
                // if (cnapSpecialCase == PhoneConstants.PRESENTATION_RESTRICTED) {
                if (TextUtils.isEmpty(number) && cnapSpecialCase == PhoneConstants.PRESENTATION_RESTRICTED) {
                    number = context.getString(R.string.private_num);
                } else if (cnapSpecialCase == PhoneConstants.PRESENTATION_UNKNOWN) {
                    number = context.getString(R.string.unknown);
                }
                if (DBG) {
                    log("SpecialCnap: number=" + toLogSafePhoneNumber(number)
                            + "; presentation now=" + cnapSpecialCase);
                }
                ci.numberPresentation = cnapSpecialCase;
            }
        }
        if (DBG) {
            log("modifyForSpecialCnapCases: returning number string="
                    + toLogSafePhoneNumber(number));
        }
        return number;
    }

    //
    // Support for 3rd party phone service providers.
    //

    /**
     * Check if a phone number can be route through a 3rd party
     * gateway. The number must be a global phone number in numerical
     * form (1-800-666-SEXY won't work).
     *
     * MMI codes and the like cannot be used as a dial number for the
     * gateway either.
     *
     * @param number To be dialed via a 3rd party gateway.
     * @return true If the number can be routed through the 3rd party network.
     */
    private static boolean isRoutableViaGateway(String number) {
        if (TextUtils.isEmpty(number)) {
            return false;
        }
        number = PhoneNumberUtils.stripSeparators(number);
        if (!number.equals(PhoneNumberUtils.convertKeypadLettersToDigits(number))) {
            return false;
        }
        number = PhoneNumberUtils.extractNetworkPortion(number);
        return PhoneNumberUtils.isGlobalPhoneNumber(number);
    }

   /**
    * This function is called when phone answers or places a call.
    * Check if the phone is in a car dock or desk dock.
    * If yes, turn on the speaker, when no wired or BT headsets are connected.
    * Otherwise do nothing.
    * @return true if activated
    */
    private static boolean activateSpeakerIfDocked(Phone phone) {
        if (DBG) log("activateSpeakerIfDocked()...");

        boolean activated = false;
        if (PhoneGlobals.mDockState != Intent.EXTRA_DOCK_STATE_UNDOCKED) {
            if (DBG) log("activateSpeakerIfDocked(): In a dock -> may need to turn on speaker.");
            final PhoneGlobals app = PhoneGlobals.getInstance();

            // TODO: This function should move to AudioRouter
            final BluetoothManager btManager = app.getBluetoothManager();
            final WiredHeadsetManager wiredHeadset = app.getWiredHeadsetManager();
            final AudioRouter audioRouter = app.getAudioRouter();

            if (!wiredHeadset.isHeadsetPlugged() && !btManager.isBluetoothHeadsetAudioOn()) {
                audioRouter.setSpeaker(true);
                activated = true;
            }
        }
        return activated;
    }


    /**
     * Returns whether the phone is in ECM ("Emergency Callback Mode") or not.
     */
    public static boolean isPhoneInEcm(Phone phone) {
        if ((phone != null) && TelephonyCapabilities.supportsEcm(phone)) {
            // For phones that support ECM, return true iff PROPERTY_INECM_MODE == "true".
            // TODO: There ought to be a better API for this than just
            // exposing a system property all the way up to the app layer,
            // probably a method like "inEcm()" provided by the telephony
            // layer.
            String ecmMode =
                    SystemProperties.get(TelephonyProperties.PROPERTY_INECM_MODE);
            if (ecmMode != null) {
                return ecmMode.equals("true");
            }
        }
        return false;
    }

    /**
     * Returns the most appropriate Phone object to handle a call
     * to the specified number.
     *
     * @param cm the CallManager.
     * @param scheme the scheme from the data URI that the number originally came from.
     * @param number the phone number, or SIP address.
     */
    public static Phone pickPhoneBasedOnNumber(CallManager cm,
            String scheme, String number, String primarySipUri) {
        if (DBG) {
            log("pickPhoneBasedOnNumber: scheme " + scheme
                    + ", number " + toLogSafePhoneNumber(number)
                    + ", sipUri "
                    + (primarySipUri != null ? Uri.parse(primarySipUri).toSafeString() : "null"));
        }

        if (primarySipUri != null) {
            Phone phone = getSipPhoneFromUri(cm, primarySipUri);
            if (phone != null) return phone;
        }

        return CallManagerWrapper.getDefaultPhone();
    }

    public static Phone getSipPhoneFromUri(CallManager cm, String target) {
        for (Phone phone : cm.getAllPhones()) {
            if (phone.getPhoneType() == PhoneConstants.PHONE_TYPE_SIP) {
                String sipUri = ((SipPhone) phone).getSipUri();
                if (target.equals(sipUri)) {
                    if (DBG) log("- pickPhoneBasedOnNumber:" +
                            "found SipPhone! obj = " + phone + ", "
                            + phone.getClass());
                    return phone;
                }
            }
        }
        return null;
    }

    /**
     * Returns true when the given call is in INCOMING state and there's no foreground phone call,
     * meaning the call is the first real incoming call the phone is having.
     */
    public static boolean isRealIncomingCall(Call.State state) {
        return (state == Call.State.INCOMING 
                && !PhoneGlobals.getInstance().mCM.hasActiveFgCall()
                && !PhoneGlobals.getInstance().mCM.hasActiveBgCall());
    }

    private static boolean sVoipSupported = false;
    static {
        PhoneGlobals app = PhoneGlobals.getInstance();
        sVoipSupported = SipManager.isVoipSupported(app)
                && app.getResources().getBoolean(com.android.internal.R.bool.config_built_in_sip_phone)
                && app.getResources().getBoolean(com.android.internal.R.bool.config_voice_capable);
    }

    /**
     * @return true if this device supports voice calls using the built-in SIP stack.
     */
    public static boolean isVoipSupported() {
        return sVoipSupported;
    }

    public static String getPresentationString(Context context, int presentation) {
        String name = context.getString(R.string.unknown);
        if (presentation == PhoneConstants.PRESENTATION_RESTRICTED) {
            name = context.getString(R.string.private_num);
        } else if (presentation == PhoneConstants.PRESENTATION_PAYPHONE) {
            name = context.getString(R.string.payphone);
        }
        return name;
    }

    public static void sendViewNotificationAsync(Context context, Uri contactUri) {
        if (DBG) Log.d(LOG_TAG, "Send view notification to Contacts (uri: " + contactUri + ")");
        Intent intent = new Intent("com.android.contacts.VIEW_NOTIFICATION", contactUri);
        intent.setClassName("com.android.contacts",
                "com.android.contacts.ViewNotificationService");
        context.startService(intent);
    }

    //
    // General phone and call state debugging/testing code
    //

    /* package */ static void dumpCallState(Phone phone) {
        PhoneGlobals app = PhoneGlobals.getInstance();
        Log.d(LOG_TAG, "dumpCallState():");

        /// M: @{
        Log.d(LOG_TAG, "- Phone: " + phone + ", name = " + phone.getPhoneName()
                + ", state = " + phone.getState());
        if (GeminiUtils.isGeminiSupport()) {
            final int[] geminiSlots = GeminiUtils.getSlots();
            for (int gs : geminiSlots) {
                Log.d(LOG_TAG, "- Phone slot=" + gs + ", name=" + phone.getPhoneName() + ", state="
                        + PhoneWrapper.getState(phone, gs));
            }
        }

        StringBuilder b = new StringBuilder(128);

        Call call = phone.getForegroundCall();
        b.setLength(0);
        b.append("  - FG call: ").append(call.getState());
        b.append(" isAlive ").append(call.getState().isAlive());
        b.append(" isRinging ").append(call.getState().isRinging());
        b.append(" isDialing ").append(call.getState().isDialing());
        b.append(" isIdle ").append(call.isIdle());
        b.append(" hasConnections ").append(call.hasConnections());
        Log.d(LOG_TAG, b.toString());


        call = phone.getBackgroundCall();
        b.setLength(0);
        b.append("  - BG call: ").append(call.getState());
        b.append(" isAlive ").append(call.getState().isAlive());
        b.append(" isRinging ").append(call.getState().isRinging());
        b.append(" isDialing ").append(call.getState().isDialing());
        b.append(" isIdle ").append(call.isIdle());
        b.append(" hasConnections ").append(call.hasConnections());
        Log.d(LOG_TAG, b.toString());

        call = phone.getRingingCall();
        b.setLength(0);
        b.append("  - RINGING call: ").append(call.getState());
        b.append(" isAlive ").append(call.getState().isAlive());
        b.append(" isRinging ").append(call.getState().isRinging());
        b.append(" isDialing ").append(call.getState().isDialing());
        b.append(" isIdle ").append(call.isIdle());
        b.append(" hasConnections ").append(call.hasConnections());
        Log.d(LOG_TAG, b.toString());


        final boolean hasRingingCall = !phone.getRingingCall().isIdle();
        final boolean hasActiveCall = !phone.getForegroundCall().isIdle();
        final boolean hasHoldingCall = !phone.getBackgroundCall().isIdle();
        final boolean allLinesTaken = hasActiveCall && hasHoldingCall;
        b.setLength(0);
        b.append("  - hasRingingCall ").append(hasRingingCall);
        b.append(" hasActiveCall ").append(hasActiveCall);
        b.append(" hasHoldingCall ").append(hasHoldingCall);
        b.append(" allLinesTaken ").append(allLinesTaken);
        Log.d(LOG_TAG, b.toString());

        // On CDMA phones, dump out the CdmaPhoneCallState too:
        if (phone.getPhoneType() == PhoneConstants.PHONE_TYPE_CDMA) {
            if (app.cdmaPhoneCallState != null) {
                Log.d(LOG_TAG, "  - CDMA call state: "
                      + app.cdmaPhoneCallState.getCurrentCallState());
            } else {
                Log.d(LOG_TAG, "  - CDMA device, but null cdmaPhoneCallState!");
            }
        }

        // Watch out: the isRinging() call below does NOT tell us anything
        // about the state of the telephony layer; it merely tells us whether
        // the Ringer manager is currently playing the ringtone.
        boolean ringing = app.getRinger().isRinging();
        Log.d(LOG_TAG, "  - Ringer state: " + ringing);
    }

    private static void log(String msg) {
        Log.d(LOG_TAG, msg);
    }

    static void dumpCallManager() {
        Call call;
        CallManager cm = PhoneGlobals.getInstance().mCM;
        StringBuilder b = new StringBuilder(128);



        Log.d(LOG_TAG, "############### dumpCallManager() ##############");
        // TODO: Don't log "cm" itself, since CallManager.toString()
        // already spews out almost all this same information.
        // We should fix CallManager.toString() to be more minimal, and
        // use an explicit dumpState() method for the verbose dump.
        // Log.d(LOG_TAG, "CallManager: " + cm
        //         + ", state = " + cm.getState());
        Log.d(LOG_TAG, "CallManager: state = " + cm.getState());
        b.setLength(0);
        call = cm.getActiveFgCall();
        b.append(" - FG call: ").append(cm.hasActiveFgCall()? "YES ": "NO ");
        b.append(call);
        b.append( "  State: ").append(cm.getActiveFgCallState());
        b.append( "  Conn: ").append(cm.getFgCallConnections());
        Log.d(LOG_TAG, b.toString());
        b.setLength(0);
        call = cm.getFirstActiveBgCall();
        b.append(" - BG call: ").append(cm.hasActiveBgCall()? "YES ": "NO ");
        b.append(call);
        b.append( "  State: ").append(cm.getFirstActiveBgCall().getState());
        b.append( "  Conn: ").append(cm.getBgCallConnections());
        Log.d(LOG_TAG, b.toString());
        b.setLength(0);
        call = cm.getFirstActiveRingingCall();
        b.append(" - RINGING call: ").append(cm.hasActiveRingingCall()? "YES ": "NO ");
        b.append(call);
        b.append( "  State: ").append(cm.getFirstActiveRingingCall().getState());
        Log.d(LOG_TAG, b.toString());



        for (Phone phone : CallManager.getInstance().getAllPhones()) {
            if (phone != null) {
                Log.d(LOG_TAG, "Phone: " + phone + ", name = " + phone.getPhoneName()
                        + ", state = " + phone.getState());
                b.setLength(0);
                call = phone.getForegroundCall();
                b.append(" - FG call: ").append(call);
                b.append( "  State: ").append(call.getState());
                b.append( "  Conn: ").append(call.hasConnections());
                Log.d(LOG_TAG, b.toString());
                b.setLength(0);
                call = phone.getBackgroundCall();
                b.append(" - BG call: ").append(call);
                b.append( "  State: ").append(call.getState());
                b.append( "  Conn: ").append(call.hasConnections());
                Log.d(LOG_TAG, b.toString());b.setLength(0);
                call = phone.getRingingCall();
                b.append(" - RINGING call: ").append(call);
                b.append( "  State: ").append(call.getState());
                b.append( "  Conn: ").append(call.hasConnections());
                Log.d(LOG_TAG, b.toString());
            }
        }

        Log.d(LOG_TAG, "############## END dumpCallManager() ###############");
    }

    /**
     * @return if the context is in landscape orientation.
     */
    public static boolean isLandscape(Context context) {
        return context.getResources().getConfiguration().orientation
                == Configuration.ORIENTATION_LANDSCAPE;
    }

    // ------------------------------- MTK ---------------------------------------------
    private static final String DUALMIC_MODE = "Enable_Dual_Mic_Setting";

    /// M: @{
    private static int sAudioBehaviourState = AUDIO_IDLE;

    // static method to set the audio control state.
    public static void setAudioControlState(int newState) {
        sAudioBehaviourState = newState;
    }

    public static int getAudioControlState() {
        return sAudioBehaviourState;
    }
    /// @}

    /// M: displayMMICompleteExt @{
    private static final int MIN_LENGTH = 6;
    private static final int MIN_WIDTH = 270;
    ///  @}

    /// M: ALPS00513091 @{
    // Bluetooth H H status
    private static boolean sPhoneSwapStatus = false;

    public static boolean getPhoneSwapStatus() {
        return sPhoneSwapStatus;
    }

    public static void setPhoneSwapStatus(boolean status) {
        sPhoneSwapStatus = status;
    }
    /// @}

    /// M: For TTY usage @{
    private static String sTtyMode = "tty_off";
    private static boolean sIsOpen = false;

    public static void setTtyMode(String mode) {
        sTtyMode = mode;
    }

    public static void openTTY() {
        if (!PhoneGlobals.getInstance().isEnableTTY()) {
            return;
        }
        Context context = PhoneGlobals.getInstance();
        AudioManager audioManager = (AudioManager)context.getSystemService(Context.AUDIO_SERVICE);
        if (!sTtyMode.equals("tty_off") && !sIsOpen) {
            audioManager.setParameters("tty_mode=" + sTtyMode);
            sIsOpen = true;
        }
    }
    /// @}

    /**
     * For ConnectionHandler handleMessage.
     */
    private static boolean handleMessageMtk(Message msg) {
        PhoneLog.d(LOG_TAG, "handleMessageMtk ConnectionHandler: msg=" + msg.what);
        switch (msg.what) {
            case PHONE_SPEECH_INFO:
            case PHONE_SPEECH_INFO2:
            case PHONE_SPEECH_INFO3:
            case PHONE_SPEECH_INFO4:
                PhoneLog.d(LOG_TAG, "ConnectionHandler: PHONE_SPEECH_INFO-" + msg.what);
                setAudioMode();
                int slotId = GeminiUtils.getSlotIdByRegisterEvent(msg.what,
                        PHONE_SPEECH_INFO_GEMINI);
                CallManagerWrapper.unregisterForSpeechInfo(mConnectionHandler, slotId);
                break;
            // For Dualtalk. @{
            case DUALTALK_SELECT_CALL_DIALOG:
                log("+showSelectCallDialog, holdList" + (List<Call>) msg.obj);
                showSelectCallDialog((List<Call>) msg.obj);
                break;
            /// @}
            default:
                return false;
        }
        return true;
    }

    // Add for recording the USSD dialog, and use to dismiss the dialog when
    // enter airplane mode
    private static Dialog sDialog = null;
    private static boolean sMmiFinished = false;
    // Used for the activity ussd dialog
    public static UssdAlertActivity sUssdActivity = null;
    private static MmiCode sCurCode = null;

    private static void autoUpdateUssdReponseUi(View dialogView) {
        TextView justForUpdate = (TextView) dialogView.findViewById(R.id.ussd_update);
        justForUpdate.setWidth(1);
        justForUpdate.setText(R.string.fdn_contact_name_number_invalid);
        justForUpdate.setFocusableInTouchMode(true);
    }

    //Add for recording the USSD dialog, and use to cancel the dialog when enter airplane mode
    public static void dismissMMIDialog() {
        if (null != sDialog) {
            sDialog.cancel();
            sDialog = null;
        }
    }

    public static void dismissUssdDialog() {
        if (sUssdActivity != null) {
            sUssdActivity.dismiss();
            sUssdActivity = null;
        }
    }

    public static boolean getMmiFinished() {
        return sMmiFinished;
    }

    public static void setMmiFinished(boolean state) {
        sMmiFinished = state;
    }

    public static boolean hangupEx(CallManager cm) {
        boolean hungup = false;
        Call ringing = null;
        Call fg = null;
        Call bg = null;
        Call call = null;
        DualTalkUtils dtUtils = null;

        if (DualTalkUtils.isSupportDualTalk()) {
            dtUtils = DualTalkUtils.getInstance();
        }

        if (DualTalkUtils.isSupportDualTalk() && dtUtils.hasMultipleRingingCall()) {
            //this can't be reached.
            ringing = dtUtils.getFirstActiveRingingCall();
            hangupForDualTalk(ringing);
            return true;
        } else if (DualTalkUtils.isSupportDualTalk() && dtUtils.hasDualHoldCallsOnly()) {
            fg = dtUtils.getFirstActiveBgCall();
            ringing = dtUtils.getFirstActiveRingingCall();
        } else if (DualTalkUtils.isSupportDualTalk() && dtUtils.isDualTalkMultipleHoldCase()) {
            fg = dtUtils.getActiveFgCall();
            ringing = dtUtils.getFirstActiveRingingCall();
        } else {
            ringing = cm.getFirstActiveRingingCall();
            fg = cm.getActiveFgCall();
            bg = cm.getFirstActiveBgCall();
        }

        if (!ringing.isIdle()) {
            log("hangup(): hanging up ringing call");
            hungup = hangupRingingCall(ringing);
            call = ringing;
        } else if (!fg.isIdle() || fg.mState == Call.State.DISCONNECTING) {
            log("hangup(): hanging up foreground call");
            hungup = hangup(fg);
            call = fg;
        } else if (!bg.isIdle() || bg.mState == Call.State.DISCONNECTING) {
            log("hangup(): hanging up background call");
            hungup = hangup(bg);
            call = bg;
        } else {
            // No call to hang up!  This is unlikely in normal usage,
            // since the UI shouldn't be providing an "End call" button in
            // the first place.  (But it *can* happen, rarely, if an
            // active call happens to disconnect on its own right when the
            // user is trying to hang up..)
            log("hangup(): no active call to hang up");
        }
        if (DBG) log("==> hungup = " + hungup);


        return hungup;
    }

    public static void placeCallRegister(Phone phone) {
        boolean isSipCall = phone.getPhoneType() == PhoneConstants.PHONE_TYPE_SIP;
        PhoneLog.d(LOG_TAG, "placeCallRegister: ");

        CallManagerWrapper.registerForSpeechInfo(phone, mConnectionHandler,
                PHONE_SPEECH_INFO_GEMINI);
    }

    static int getExpectedAudioMode() {
        int mode = AudioManager.MODE_NORMAL;
        CallManager cm = PhoneGlobals.getInstance().mCM;
        switch (cm.getState()) {
            case RINGING:
                mode = AudioManager.MODE_RINGTONE;
                break;
            case OFFHOOK:
                Phone fgPhone = cm.getFgPhone();
                // Enable IN_CALL mode while foreground call is in DIALING,
                // ALERTING, ACTIVE and DISCONNECTING state and not from
                // sipPhone
                if (cm.getActiveFgCallState() != Call.State.IDLE
                        && cm.getActiveFgCallState() != Call.State.DISCONNECTED
                        && !(fgPhone instanceof SipPhone)) {
                    mode = AudioManager.MODE_IN_CALL;
                }
                break;
            default:
                PhoneLog.d(LOG_TAG,
                        "cm.getState() is neither RINGING nor OFFHOOK in getExpectedAudioMode().");
                break;
        }

        return mode;
    }

    /**
     * Start a CallerInfo Query based on the earliest connection in the call.
     */
    public static CallerInfoToken startGetCallerInfo(Context context, Call call,
            CallerInfoAsyncQuery.OnQueryCompleteListener listener, Object cookie, boolean needClearUserData) {
        Connection conn = null;
        int phoneType = call.getPhone().getPhoneType();
        if (phoneType == PhoneConstants.PHONE_TYPE_CDMA) {
            conn = call.getLatestConnection();
        } else if ((phoneType == PhoneConstants.PHONE_TYPE_GSM)
                || (phoneType == PhoneConstants.PHONE_TYPE_SIP)) {
            conn = call.getEarliestConnection();
        } else {
            throw new IllegalStateException("Unexpected phone type: " + phoneType);
        }

        if (null != conn && needClearUserData) {
            conn.clearUserData();
        }

        return startGetCallerInfo(context, conn, listener, cookie);
    }

    public static boolean holdAndActiveFromDifPhone(CallManager cm) {
        boolean isDiffrentPhone = false;
        List<Phone> array = cm.getAllPhones();
        boolean found = false;
        for (Phone p : array) {
            if (p.getState() == PhoneConstants.State.OFFHOOK) {
                if (!found) {
                    found = true;
                } else {
                    isDiffrentPhone = true;
                    break;
                }
            }
        }
        return isDiffrentPhone;
    }

    // For Google default, the swap button and hold button no dependency,
    // but about our solution, the swap and hold is exclusive:If the hold button
    // display, the swap must hide
    // so we need this method to make sure the swap can be displayed
    public static boolean okToShowSwapButton(CallManager cm) {
        Call fgCall = cm.getActiveFgCall();
        Call bgCall = cm.getFirstActiveBgCall();
        DualTalkUtils dt = DualTalkUtils.getInstance();
        Call realFgCall = dt == null ? null : dt.getActiveFgCall();
        if (DBG) {
            log("okToShowSwapButton dt = " + dt);
            log("okToShowSwapButton realFgCall = " + realFgCall);
        }
        if (DualTalkUtils.isSupportDualTalk()
                && (dt != null && dt.isCdmaAndGsmActive()
                || realFgCall != null
                        && realFgCall.getPhone().getPhoneType() == PhoneConstants.PHONE_TYPE_CDMA
                        && PhoneUtils.hasMultipleConnections(realFgCall))) {
            return true;
        } else if ((fgCall.getState().isAlive() && bgCall.getState() == Call.State.HOLDING)
                || holdAndActiveFromDifPhone(cm)) {
            return true;
        }

        return false;
    }

    public static boolean hasActivefgEccCall(CallManager cm) {
        return PhoneUtils.hasActivefgEccCall(cm.getActiveFgCall());
    }

    static boolean isVoicemailNumber(Uri uri) {
        return uri != null && "voicemail".equals(uri.getScheme().toString());
    }

    static boolean hasActivefgEccCall(Call call) {
        if (call == null) {
            return false;
        }
        Connection connection = call.getEarliestConnection();
        return (call.getState() == Call.State.DIALING || call.getState() == Call.State.ALERTING) &&
                connection != null &&
                !TextUtils.isEmpty(connection.getAddress()) &&
                PhoneNumberUtils.isEmergencyNumber(connection.getAddress());
    }

    static boolean isEccCall(Call call) {
        Connection connection = call.getEarliestConnection();
        return (connection != null &&
                !TextUtils.isEmpty(connection.getAddress()) && PhoneNumberUtils
                .isEmergencyNumber(connection.getAddress()));
    }

    public static boolean isDMLocked() {
        boolean locked = false;
        try {
            IBinder binder = ServiceManager.getService("DmAgent");
            DmAgent agent = null;
            if (binder != null) {
                agent = DmAgent.Stub.asInterface(binder);
            }
            if (agent != null) {
                locked = agent.isLockFlagSet();
            }
        } catch (RemoteException e) {
            e.printStackTrace();
        }
        if (DBG) {
            log("isDMLocked(): locked = " + locked);
        }
        return locked;
    }

    public static void setDualMicMode(String dualMic) {
        Context context = PhoneGlobals.getInstance().getApplicationContext();
        if (context == null) {
            return;
        }
        AudioManager audioManager = (AudioManager)
                context.getSystemService(Context.AUDIO_SERVICE);
        audioManager.setParameters(DUALMIC_MODE + "=" + dualMic);
    }

    public static boolean isSupportFeature(String feature) {
        if (feature == null) {
            return false;
        }

        if (feature.equals("TTY")) {
            return FeatureOption.MTK_TTY_SUPPORT;
        } else if (feature.equals("DUAL_MIC")) {
            return FeatureOption.MTK_DUAL_MIC_SUPPORT;
        } else if (feature.equals("IP_DIAL")) {
            return true;
        } else if (feature.equals("3G_SWITCH")) {
            return FeatureOption.MTK_GEMINI_3G_SWITCH;
        } else if (feature.equals("VT_VOICE_RECORDING")) {
            return true;
        } else if (feature.equals("VT_VIDEO_RECORDING")) {
            return true;
        } else if (feature.equals("PHONE_VOICE_RECORDING")) {
            return FeatureOption.MTK_PHONE_VOICE_RECORDING;
        }
        return false;
    }

    // The private added extra for call, should be added code here
    public static void checkAndCopyPrivateExtras(final Intent origIntent, Intent newIntent) {
        int slot = origIntent.getIntExtra(Constants.EXTRA_SLOT_ID, -1);
        if (-1 != slot) {
            newIntent.putExtra(Constants.EXTRA_SLOT_ID, slot);
        }
        if (FeatureOption.MTK_VT3G324M_SUPPORT) {
            boolean isVideoCall = origIntent.getBooleanExtra(Constants.EXTRA_IS_VIDEO_CALL, false);
            if (isVideoCall) {
                newIntent.putExtra(Constants.EXTRA_IS_VIDEO_CALL, isVideoCall);
            }
        }
        long simId = origIntent.getLongExtra(Constants.EXTRA_ORIGINAL_SIM_ID, Settings.System.DEFAULT_SIM_NOT_SET);
        if (-1 != simId) {
            newIntent.putExtra(Constants.EXTRA_ORIGINAL_SIM_ID, simId);
        }
        boolean isIpCall = origIntent.getBooleanExtra(Constants.EXTRA_IS_IP_DIAL, false);
        if (isIpCall) {
            newIntent.putExtra(Constants.EXTRA_IS_IP_DIAL, isIpCall);
        }
        boolean isFollowSimManagement = origIntent.getBooleanExtra(Constants.EXTRA_FOLLOW_SIM_MANAGEMENT, false);
        if (isFollowSimManagement) {
            newIntent.putExtra(Constants.EXTRA_FOLLOW_SIM_MANAGEMENT, isFollowSimManagement);
        }
    }
    /* Temp Delete For Build Error
    TODO: Review these changes from google
    public static void setMMICommandToService(final String number) {
        if (sNwService != null) {
            try {
                sNwService.setMmiString(number);
                if (DBG) {
                    log("Extended NW bindService setUssdString (" + number + ")");
                }
            } catch (RemoteException e) {
                sNwService = null;
            }
        }
    }
    */
    public static SimInfoRecord getActiveSimInfo() {
        if (GeminiUtils.isGeminiSupport()) {
            int slot = PhoneWrapper.getSlotNotIdle(PhoneGlobals.getInstance().phone);

            if (slot == -1) {
                int[] geminiSlots = GeminiUtils.getSlots();
                for (int gs : geminiSlots) {
                    if (PhoneWrapper.getPendingMmiCodes(PhoneGlobals.getInstance().phone, gs).size() != 0) {
                        slot = gs;
                        break;
                    }
                }
                if (DBG) {
                    log("updateSimIndicator, running mmi, slot = " + slot);
                }
                return null;
            } else {
                SimInfoRecord simInfo = SIMInfoWrapper.getDefault().getSimInfoBySlot(slot);
                if (simInfo != null) {
                    if (DBG) {
                        log("updateSimIndicator slot = " + slot + " simInfo :");
                        log("displayName = " + simInfo.mDisplayName);
                        log("color       = " + simInfo.mColor);
                    }
                }
                return simInfo;
            }
        }
        // For single sim card case, no need get sim info
        return null;
    }
    
    /**
     * Returns the special card title used in emergency callback mode (ECM),
     * which shows your own phone number.
     */
    public static String getECMCardTitle(Context context, Phone phone) {
        String rawNumber = phone.getLine1Number();  // may be null or empty
        String formattedNumber;
        if (!TextUtils.isEmpty(rawNumber)) {
            formattedNumber = PhoneNumberUtils.formatNumber(rawNumber);
        } else {
            formattedNumber = context.getString(R.string.unknown);
        }
        String titleFormat = context.getString(R.string.card_title_my_phone_number);
        return String.format(titleFormat, formattedNumber);
    }
    
    public static long getDiskAvailableSize() {
        File sdCardDirectory = new File(StorageManagerEx.getDefaultPath());
        StatFs statfs;
        try {
            if (sdCardDirectory.exists() && sdCardDirectory.isDirectory()) {
                statfs = new StatFs(sdCardDirectory.getPath());
            } else {
                log("-----diskSpaceAvailable: sdCardDirectory is null----");
                return -1;
            }
        } catch (IllegalArgumentException e) {
            log("-----diskSpaceAvailable: IllegalArgumentException----");
            return -1;
        }
        long blockSize = statfs.getBlockSize();
        long availBlocks = statfs.getAvailableBlocks();
        long totalSize = blockSize * availBlocks;
        return totalSize;
    }

    // The unit of input parameter is BYTE
    public static boolean diskSpaceAvailable(long sizeAvailable) {
        return (getDiskAvailableSize() - sizeAvailable) > 0;
    }

    public static boolean diskSpaceAvailable(String defaultPath, long sizeAvailable) {
        if (null == defaultPath) {     
            return diskSpaceAvailable(sizeAvailable);
        } else {
            File sdCardDirectory = new File(defaultPath);
            StatFs statfs;
            try {
                if (sdCardDirectory.exists() && sdCardDirectory.isDirectory()) {
                    statfs = new StatFs(sdCardDirectory.getPath());
                } else {
                    log("-----diskSpaceAvailable: sdCardDirectory is null----");
                    return false;
                }
            } catch (IllegalArgumentException e) {
                log("-----diskSpaceAvailable: IllegalArgumentException----");
                return false;
            }
            long blockSize = statfs.getBlockSize();
            long availBlocks = statfs.getAvailableBlocks();
            long totalSize = blockSize * availBlocks;
            return (totalSize - sizeAvailable) > 0;
        }
    }    

    public static boolean isExternalStorageMounted() {
        StorageManager storageManager = (StorageManager) PhoneGlobals.getInstance().getSystemService(
                Context.STORAGE_SERVICE);
        if (null == storageManager) {
            log("-----story manager is null----");
            return false;
        }
        String storageState = storageManager.getVolumeState(StorageManagerEx.getDefaultPath());
        return storageState.equals(Environment.MEDIA_MOUNTED) ? true : false;
    }

    public static String getExternalStorageDefaultPath() {
        return StorageManagerEx.getDefaultPath();
    }

    static void displayMmiDialog(Context context, CharSequence text, int type, int slot) {
        /// M: For ALPS01374729. @{
        // When the current MMICode has been cancelled, we don't start UssdAlertActivity at here.
        if (sNoNeedStartUssdActivity) {
            PhoneLog.d(LOG_TAG, "displayMmiDialog(), current MMICode is canceled, do nothing");
            sNoNeedStartUssdActivity = false;
            return;
        }
        /// @}
        Intent intent = new Intent();
        intent.setClass(context, com.mediatek.phone.UssdAlertActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.putExtra(UssdAlertActivity.USSD_MESSAGE_EXTRA, text);
        intent.putExtra(UssdAlertActivity.USSD_TYPE_EXTRA, type);
        intent.putExtra(UssdAlertActivity.USSD_SLOT_ID, slot);
        context.startActivity(intent);
    }
    
    public static void cancelUssdDialog() {
        if (sCurCode != null && sCurCode.isCancelable()) {
            sCurCode.cancel();
        }
    }

    //This is ugly, but we have no choice because of SipPhone doesn't implement the method
    public static void hangupAllCalls() {
        hangupAllCalls(false, null);
    }

    public static void hangupAllCalls(boolean includeRingCalls, Call ringCallToKeep) {
        /// Change for ALPS01276488.
        //  We should get the phone in order, for we should hangup the calls
        //  in forground, so the phone list's first phone should contain the
        //  forground call. @{
        //CallManager cm = PhoneGlobals.getInstance().mCM;
        List<Phone> phones = getAllPhonesInOrder();
        /// @}
        try {
            for (Phone phone : phones) {
                Call fg = phone.getForegroundCall();
                Call bg = phone.getBackgroundCall();
                Call ring = phone.getRingingCall();
                if (phone.getState() != PhoneConstants.State.IDLE) {
                    if (!(phone instanceof SipPhone)) {
                        log(phone.toString() + "   " + phone.getClass().toString());
                        if ((fg != null && fg.getState().isAlive()) && (bg != null && bg.getState().isAlive())) {
                            phone.hangupAll();
                        } else if (fg != null && fg.getState().isAlive()) {
                            fg.hangup();
                        } else if (bg != null && bg.getState().isAlive()) {
                            bg.hangup();
                        }
                    } else {
                        log(phone.toString() + "   " + phone.getClass().toString());
                        if (fg != null && fg.getState().isAlive()) {
                            fg.hangup();
                        }
                        if (bg != null && bg.getState().isAlive()) {
                            bg.hangup();
                        }
                    }
                    if (includeRingCalls && null != ring && ring != ringCallToKeep) {
                        ring.hangup();
                    }
                } else {
                    log("Phone is idle  " + phone.toString() + "   " + phone.getClass().toString());
                }
            }
        } catch (CallStateException e) {
            log(e.toString());
        }
    }

    public static void hangupForDualTalk(Call call) {
        Phone phone = call.getPhone();
        try {
            call.hangup();
        } catch (CallStateException e) {
            Log.d(LOG_TAG, e.toString());
        }
    }

    public static boolean hasMultipleConnections(Call call) {
        if (call == null) {
            return false;
        }
        return call.getConnections().size() > 1;
    }

    /**
     * To query whether should AutoReject
     *
     * @return true if shoud auto reject
     */
    public static boolean shouldAutoReject(final Connection c) {
        log("shouldAutoReject...");
            if (c == null) {
                return false;
            }

            // first check by connection userdata
            Object userDataObject = c.getUserData();
            if (userDataObject instanceof CallerInfo) {
                CallerInfo callerInfo = (CallerInfo) userDataObject;
                log("instanceof CallerInfo, flag is " + callerInfo.shouldSendToVoicemail);
                if (callerInfo.shouldSendToVoicemail) {
                    return true;
                }
            }

            //check by address
            BlackListManager bm= new BlackListManager(PhoneGlobals.getInstance());
            String address = c.getAddress();
            if (!TextUtils.isEmpty(address)) {
                // check the call is in black list or not.
                boolean isInBlackList = bm.shouldBlock(address,
                        c.isVideo() ? BlackListManager.VIDEO_CALL_REJECT_MODE : BlackListManager.VOICE_CALL_REJECT_MODE);
                // check the call should be auto rejected or not.
                // using CallerInfoCache'sendToVoicemail instead of query from startGetCallerInfo().
                // see ALPS00580998; ALPS00581104
                boolean isAutoReject = false;
                final CallerInfoCache.CacheEntry entry = PhoneGlobals.getInstance().callerInfoCache.getCacheEntry(address);
                if (entry != null) {
                    isAutoReject = entry.sendToVoicemail;
                }
                log("the call should be rejected by black list or auto reject: " + isInBlackList + " / " + isAutoReject);
                if (isInBlackList || isAutoReject) {
                    return true;
                }
            }
            return false;
    }

    private static boolean forceSpeakerOn(Context context, boolean checkHeadSet) {
        boolean forceSpeakerOn = false;
        if (FeatureOption.MTK_TB_APP_CALL_FORCE_SPEAKER_ON) {
            PhoneLog.d(LOG_TAG, "forceSpeakerOn, MTK_TB_APP_CALL_FORCE_SPEAKER_ON");

            forceSpeakerOn = !isSpeakerOn(context);
            if (checkHeadSet) {
                PhoneGlobals app = PhoneGlobals.getInstance();
                forceSpeakerOn = forceSpeakerOn && !app.getWiredHeadsetManager().isHeadsetPlugged()
                        && !app.getBluetoothManager().isBluetoothHeadsetAudioOn();
            } else {
                /// M: ALPS01281219 @{
                /// Even don't care the headset status, still to check the incoming call...
                forceSpeakerOn = forceSpeakerOn &&
                        !isRealIncomingCall(PhoneGlobals.getInstance().mCM.getFirstActiveRingingCall().getState());
                /// @}
            }
            
            if (forceSpeakerOn) {
                PhoneLog.d(LOG_TAG, "forceSpeakerOn,  turnOnSpeaker");
                turnOnSpeaker(context, true, true);
                return true;
            }
        }
        return false;
    }

    /// M:Gemini+ @{
    public static final int PHONE_SPEECH_INFO = -2;
    public static final int PHONE_SPEECH_INFO2 = -102;
    public static final int PHONE_SPEECH_INFO3 = -202;
    public static final int PHONE_SPEECH_INFO4 = -302;
    public static final int[] PHONE_SPEECH_INFO_GEMINI = new int[] { PHONE_SPEECH_INFO, PHONE_SPEECH_INFO2,
            PHONE_SPEECH_INFO3, PHONE_SPEECH_INFO4 };

    public static String specialNumberTransfer(String number) {
        if (null == number) {
            return null;
        }
        number = number.replace('p', PhoneNumberUtils.PAUSE).replace('w', PhoneNumberUtils.WAIT);
        number = PhoneNumberUtils.convertKeypadLettersToDigits(number);
        number = PhoneNumberUtils.stripSeparators(number);
        return number;
    }

    public static boolean isVideoCall(Call call) {
        if (call == null /* || !call.getState().isAlive() */) {
            return false;
        }

        Connection c = call.getLatestConnection();
        if (c == null) {
            return false;
        } else {
            return c.isVideo();
        }
    }

    public static boolean hasMultiplePhoneActive() {
        CallManager cm = PhoneGlobals.getInstance().mCM;
        if (null == cm || cm.getState() == PhoneConstants.State.IDLE) {
            if (DBG) {
                log("CallManager says in idle state!");
            }
            return false;
        }

        List<Phone> phoneList = cm.getAllPhones();
        log("CallManager says in idle state!" + phoneList);
        int count = 0;
        // Maybe need to check the call status??
        for (Phone phone : phoneList) {
            if (phone.getState() == PhoneConstants.State.OFFHOOK) {
                count++;
                if (DBG) {
                    log("non IDLE phone = " + phone.toString());
                }
                if (count > 1) {
                    if (DBG) {
                        log("More than one phone active!");
                    }
                    return true;
                }
            }
        }
        if (DBG) {
            log("Strange! no phone active but we go here!");
        }
        return false;
    }

    /**
     * In some case, the all phones are IDLE, but we need the call information to update the 
     * CallCard for DISCONNECTED or DISCONNECTING status
     * @param call
     * @return
     */
    public static SimInfoRecord getSimInfoByCall(Call call) {
        if (call == null || call.getPhone() == null) {
            return null;
        }

        Phone phone = call.getPhone();
        if (phone.getPhoneType() == PhoneConstants.PHONE_TYPE_CDMA) {
            return SIMInfoWrapper.getDefault().getSimInfoBySlot(GeminiUtils.getCDMASlot());
        }

        /// M:Gemini+
        final String serialNumber = phone.getIccSerialNumber();
        final int[] geminiSlots = GeminiUtils.getSlots();
        PhoneLog.d(LOG_TAG, "getSimInfoByCall, serialNumber=" + serialNumber);
        for (int gs : geminiSlots) {
            SimInfoRecord info = SIMInfoWrapper.getDefault().getSimInfoBySlot(gs);
            if (info != null && (info.mIccId != null) && (info.mIccId.equals(serialNumber))) {
                PhoneLog.d(LOG_TAG, "getSimInfoByCall, slotId=" + info.mSimSlotId + ", iccId=" + info.mIccId);
                return info;
            }
        }

        return null;
    }

    public static boolean isVoicemailNumber(String number, int slot, Phone phone) {
        /// M: For ALPS00602127 @{
        // if it is sip call, it is not voice mail call.
        if (phone != null && phone instanceof SipPhone) {
            return false;
        }
        /// @}

        boolean isVoicemail = false;
        // / M:Gemini+
        String voiceMailNumber = GeminiUtils.getVoiceMailNumber(slot);
        if (voiceMailNumber != null && PhoneNumberUtils.compare(voiceMailNumber, number)) {
            isVoicemail = true;
        }
        return isVoicemail;
    }

    static boolean isEmergencyNumber(String number) {
        if (!DualTalkUtils.isEvdoDTSupport()) {
            return PhoneNumberUtils.isEmergencyNumber(number);
        }
        
        return PhoneNumberUtils.isEmergencyNumberExt(number, PhoneConstants.PHONE_TYPE_CDMA)
                || PhoneNumberUtils.isEmergencyNumberExt(number, PhoneConstants.PHONE_TYPE_GSM);
    }

    public static String getCallFailedString(Call call) {
        Connection c = call.getEarliestConnection();
        if (c == null) {
            if (DBG) log("getCallFailedString: connection is null, using default values.");
            // if this connection is null, just assume that the
            // default case occurs.
            return PhoneGlobals.getInstance().getString(R.string.card_title_call_ended);
        } else {
            return getCallFailedString(c.getDisconnectCause());
        }
    }

    public static String getCallFailedString(Connection.DisconnectCause cause) {
        int resID;

        if (cause == null) {
            if (DBG) log("getCallFailedString: connection is null, using default values.");
            // if this connection is null, just assume that the
            // default case occurs.
            resID = R.string.card_title_call_ended;
        } else {
            switch (cause) {
                case BUSY:
                    resID = R.string.callFailed_userBusy;
                    break;

                case CONGESTION:
                    resID = R.string.callFailed_congestion;
                    break;

                case TIMED_OUT:
                    resID = R.string.callFailed_timedOut;
                    break;

                case SERVER_UNREACHABLE:
                    resID = R.string.callFailed_server_unreachable;
                    break;

                case NUMBER_UNREACHABLE:
                    resID = R.string.callFailed_number_unreachable;
                    break;

                case INVALID_CREDENTIALS:
                    resID = R.string.callFailed_invalid_credentials;
                    break;

                case SERVER_ERROR:
                    resID = R.string.callFailed_server_error;
                    break;

                case OUT_OF_NETWORK:
                    resID = R.string.callFailed_out_of_network;
                    break;

                case LOST_SIGNAL:
                case CDMA_DROP:
                    resID = R.string.callFailed_noSignal;
                    break;

                case LIMIT_EXCEEDED:
                    resID = R.string.callFailed_limitExceeded;
                    break;

                case POWER_OFF:
                    resID = R.string.callFailed_powerOff;
                    break;

                case ICC_ERROR:
                    ///M: #Only emergency# text replace in case IMEI locked @{
                    IServiceStateExt serviceStateExt =
                            MediatekClassFactory.createInstance(IServiceStateExt.class, PhoneGlobals.getInstance());
                    resID = serviceStateExt.isImeiLocked() ? R.string.callFailed_cdma_notEmergency :
                                (FeatureOption.EVDO_DT_SUPPORT ? R.string.callFailed_simError_cdma :
                                                                 R.string.callFailed_simError);
                    ///@}
                    break;

                case OUT_OF_SERVICE:
                    resID = R.string.callFailed_outOfService;
                    break;

                case INVALID_NUMBER:
                case UNOBTAINABLE_NUMBER:
                    resID = R.string.callFailed_unobtainable_number;
                    break;

                default:
                    resID = R.string.card_title_call_ended;
                    break;
            }
        }
        return PhoneGlobals.getInstance().getString(resID);
    }

    static boolean hangupAllHoldCalls() {
        if (DualTalkUtils.isSupportDualTalk()) {
            DualTalkUtils dt = DualTalkUtils.getInstance();

            if (dt.isDualTalkMultipleHoldCase()) {
                Call firstBgCall = dt.getFirstActiveBgCall();
                Call secondBgCall = dt.getSecondActiveBgCall();
                try {
                    firstBgCall.hangup();
                    secondBgCall.hangup();
                } catch (CallStateException e) {
                    log("hangupAllHoldCalls: " + e);
                }
            }
        }
        return true;
    }

    /**
     * For Override Function and hide phone number function (PhoneConstants.PRESENTATION_RESTRICTED),
     * we should decide whether Phone number is null. if phonenumber != null and Callerinfo.numberPresentation
     * equal 1, open Override function.
     * @param phoneNumber
     * @param context
     * @param presentation
     * @return
     */
    public static String getPresentationStringEx(String phoneNumber, Context context, int presentation) {
        String name = null;
        if (presentation == PhoneConstants.PRESENTATION_RESTRICTED && TextUtils.isEmpty(phoneNumber)) {
            name = context.getString(R.string.private_num);
        } else if (presentation == PhoneConstants.PRESENTATION_PAYPHONE) {
            name = context.getString(R.string.payphone);
        } else if (presentation == PhoneConstants.PRESENTATION_UNKNOWN || TextUtils.isEmpty(phoneNumber)) {
            name = context.getString(R.string.unknown);
        } else {
            name = phoneNumber;
        }
        PhoneLog.d(LOG_TAG, "name = " + name + " phoneNumber = " + phoneNumber + " presentation = " + presentation);
        return name;
    }

    /**
     * Get whether is on airplane mode from setting system
     *   0  -->  Off   1  -->  on
     * @return true if onAirplandMode, else false
     */
    public static boolean isOnAirplaneMode() {
        int mode = 0;
        try {
            mode = Settings.Global.getInt(PhoneGlobals.getInstance().getContentResolver(),
                    Settings.Global.AIRPLANE_MODE_ON);
        } catch (SettingNotFoundException e) {
            PhoneLog.i(LOG_TAG, "fail to get airlane mode");
            mode = 0;
        }
        PhoneLog.i(LOG_TAG, "airlane mode is " + mode);
        return (mode != 0);
    }


    public static boolean okToRecordVoice(CallManager cm) {
        boolean retval = false;
        if (!FeatureOption.MTK_PHONE_VOICE_RECORDING) {
            //For dualtalk solution, because of audio's limitation, don't support voice record
            return retval;
        }

        final Call ringingCall = cm.getFirstActiveRingingCall();

        if (ringingCall.getState() == Call.State.IDLE) {
            Log.d(LOG_TAG, "fgCall state:" + cm.getActiveFgCall().getState());
            Log.d(LOG_TAG, "phoneType" + cm.getFgPhone().getPhoneType());

            /**
             * M: Bug Fix for CR: ALPS00473260
             * Original Code:
             * Call fgCall = mCM.getActiveFgCall();
             * if (fgCall.getState() == Call.State.ACTIVE 
             *         && mCM.getFgPhone().getPhoneType() != PhoneConstants.PHONE_TYPE_SIP) {
             *    retval = true;
             * }
             * @{
             */
            Call fgCall = null;
            if (DualTalkUtils.isSupportDualTalk()) {
                fgCall = DualTalkUtils.getInstance().getActiveFgCall();
            } else {
                fgCall = cm.getActiveFgCall();
            }
            if (fgCall.getState() == Call.State.ACTIVE
                    && fgCall.getPhone().getPhoneType() != PhoneConstants.PHONE_TYPE_SIP) {
                retval = true;
            }
            /** @} End - Bug Fix for CR: ALPS00473260 */

        }

        return retval;
    }

    static int getPhoneTypeMTK() {
        int phoneType = PhoneConstants.PHONE_TYPE_GSM;
        if (DualTalkUtils.isSupportDualTalk() && DualTalkUtils.getInstance() != null) {
            Phone phone = DualTalkUtils.getInstance().getFirstPhone();
            if (phone != null) {
                phoneType = phone.getPhoneType();
            }
        } else {
            phoneType = PhoneGlobals.getPhone().getPhoneType();
        }
        return phoneType;
    }

    static void checkCallKeyForVT(int phoneType) {
        boolean ignoreThisCallKey = false;
        if (FeatureOption.MTK_VT3G324M_SUPPORT) {
            if (phoneType == PhoneConstants.PHONE_TYPE_GSM) {
                if (VTCallUtils.isVideoCall(CallManager.getInstance().getActiveFgCall())) {
                    ignoreThisCallKey = true;
                }
            }
        }
        if (DBG) {
            log("handleCallKey: ignoreThisCallKey = " + ignoreThisCallKey);
        }
        if (!ignoreThisCallKey) {
            // Really means "hold" in this state
            switchHoldingAndActive(CallManager.getInstance().getFirstActiveBgCall());
        }
    }

    /**
     * Answer a ringing call. This method does nothing if there's no ringing or
     * waiting call.
     */
    static void internalAnswerCall() {
        if (DBG)
            log("internalAnswerCall()...");

        DualTalkUtils dualTalk = DualTalkUtils.getInstance();
        CallManager callManager = CallManager.getInstance();
        final boolean hasRingingCall = callManager.hasActiveRingingCall();

        // / M: for Video Call @{
        if (FeatureOption.MTK_VT3G324M_SUPPORT && VTCallUtils.isVTRinging()) {
            // mVTInCallScreen.internalAnswerVTCallPre();
            VTCallUtils.internalAnwerVTCallPre();
        }
        // / @}
        // / M: for DualTalk @{
        if (DualTalkUtils.isSupportDualTalk()) {
            if (dualTalk.hasMultipleRingingCall() || dualTalk.isDualTalkAnswerCase()
                    || dualTalk.isRingingWhenOutgoing()) {
                if (FeatureOption.MTK_VT3G324M_SUPPORT && VTCallUtils.isVTRinging()) {
                    internalAnswerVideoCallForDualTalk();
                } else {
                    internalAnswerCallForDualTalk();
                }
                return;
            }
        }
        // / @}

        if (hasRingingCall) {
            Phone phone = callManager.getRingingPhone();
            Call ringing = callManager.getFirstActiveRingingCall();
            int phoneType = phone.getPhoneType();
            if (phoneType == PhoneConstants.PHONE_TYPE_CDMA) {
                if (DBG)
                    log("internalAnswerCall: answering (CDMA)...");
                if (callManager.hasActiveFgCall()
                        && callManager.getFgPhone().getPhoneType() == PhoneConstants.PHONE_TYPE_SIP) {
                    // The incoming call is CDMA call and the ongoing
                    // call is a SIP call. The CDMA network does not
                    // support holding an active call, so there's no
                    // way to swap between a CDMA call and a SIP call.
                    // So for now, we just don't allow a CDMA call and
                    // a SIP call to be active at the same time.We'll
                    // "answer incoming, end ongoing" in this case.
                    if (DBG)
                        log("internalAnswerCall: answer " + "CDMA incoming and end SIP ongoing");
                    answerAndEndActive(callManager, ringing);
                } else {
                    answerCall(ringing);
                }
            } else if (phoneType == PhoneConstants.PHONE_TYPE_SIP) {
                if (DBG)
                    log("internalAnswerCall: answering (SIP)...");
                if (callManager.hasActiveFgCall()
                        && callManager.getFgPhone().getPhoneType() == PhoneConstants.PHONE_TYPE_CDMA) {
                    // Similar to the PHONE_TYPE_CDMA handling.
                    // The incoming call is SIP call and the ongoing
                    // call is a CDMA call. The CDMA network does not
                    // support holding an active call, so there's no
                    // way to swap between a CDMA call and a SIP call.
                    // So for now, we just don't allow a CDMA call and
                    // a SIP call to be active at the same time.We'll
                    // "answer incoming, end ongoing" in this case.
                    if (DBG)
                        log("internalAnswerCall: answer " + "SIP incoming and end CDMA ongoing");
                    answerAndEndActive(callManager, ringing);
                    // / M: @{
                    // if GSM Phone has both active FG call and BG call, answer
                    // the
                    // ringing and end the FG call
                } else if (callManager.hasActiveFgCall()
                        && callManager.getFgPhone().getPhoneType() != PhoneConstants.PHONE_TYPE_CDMA
                        && callManager.hasActiveBgCall()) {
                    answerAndEndActive(callManager, ringing);
                    // / @}
                } else {
                    // / M: for Video Call @{
                    if (callManager.hasActiveFgCall()
                            && VTCallUtils.isVideoCall(callManager.getActiveFgCall())) {
                        try {
                            callManager.getFgPhone().hangupActiveCall();
                        } catch (CallStateException e) {
                            log(e.toString());
                        }
                    }
                    // / @}
                    answerCall(ringing);
                }
            } else if (phoneType == PhoneConstants.PHONE_TYPE_GSM) {
                if (DBG)
                    log("internalAnswerCall: answering (GSM)...");
                // GSM: this is usually just a wrapper around
                // PhoneUtils.answerCall(), *but* we also need to do
                // something special for the "both lines in use" case.

                final boolean hasActiveCall = callManager.hasActiveFgCall();
                final boolean hasHoldingCall = callManager.hasActiveBgCall();

                // / M: for Video Call @{
                if (FeatureOption.MTK_VT3G324M_SUPPORT) {
                    Call fg = callManager.getActiveFgCall();
                    // On dualtalk solution, when video call and voice call
                    // maybe co-exist(1A+1R)
                    if (VTCallUtils.isVTRinging() || (hasActiveCall && VTCallUtils.isVideoCall(fg))) {
                        if (DBG) {
                            log("internalAnswerCall: is VT ringing now, so call PhoneUtils.answerCall(ringing) anyway !");
                        }
                        if (hasActiveCall) {
                            if (DBG) {
                                log("internalAnswerCall: is VT ringing now, first disconnect active call!");
                            }
                            Phone p = callManager.getFgPhone();
                            if (p != callManager.getRingingPhone()) {
                                try {
                                    if (p instanceof SipPhone) {
                                        callManager.getActiveFgCall().hangup();
                                    } else {
                                        if (VTCallUtils.isVideoCall(fg)) {
                                            PhoneGlobals.getInstance().notifier.resetAudioState();
                                        }
                                        p.hangupActiveCall();
                                    }
                                } catch (CallStateException e) {
                                    log(e.toString());
                                }
                            }
                        } else if (hasHoldingCall && VTCallUtils.isVideoCall(ringing)) {
                            // the holdingCall must be voice call, hangup it
                            if (DBG) {
                                log("internalAnswerCall: is VT ringing now, first disconnect holding call!");
                            }
                            try {
                                callManager.getFirstActiveBgCall().hangup();
                            } catch (CallStateException e) {
                                log(e.toString());
                            }
                        }
                        answerCall(ringing);
                        return;
                    }
                }
                // / @}

                if (hasActiveCall && hasHoldingCall) {
                    if (DBG)
                        log("internalAnswerCall: answering (both lines in use!)...");
                    // The relatively rare case where both lines are
                    // already in use. We "answer incoming, end ongoing"
                    // in this case, according to the current UI spec.

                    // / M: @{
                    //
                    // original code
                    // PhoneUtils.answerAndEndActive(mCM, ringing);
                    answerAndEndActive(callManager, callManager.getFirstActiveRingingCall());
                    // / @}

                    // / M: for ALPS00127695 @{
                    // setOnAnswerAndEndFlag(true);
                    // / @}

                    // Alternatively, we could use
                    // PhoneUtils.answerAndEndHolding(mPhone);
                    // here to end the on-hold call instead.
                } else {
                    if (DBG)
                        log("internalAnswerCall: answering...");
                    answerCall(ringing); // Automatically holds the current
                                         // active call,
                                         // if there is one
                }
            } else {
                throw new IllegalStateException("Unexpected phone type: " + phoneType);
            }
        }
    }

    static void internalAnswerVideoCallForDualTalk() {
        Call ringingCall = DualTalkUtils.getInstance().getFirstActiveRingingCall();
        if (null != ringingCall) {
            hangupAllCalls(true, ringingCall);
            if (DBG) {
                log("hangup all calls except current ring call");
            }
            answerCall(ringingCall);
        }
    }

    /**
     * Change Feature by mediatek .inc description : support for dualtalk
     */
    static void internalAnswerCallForDualTalk() {
        DualTalkUtils dualTalk = DualTalkUtils.getInstance();
        CallManager callManager = CallManager.getInstance();
        Call ringing = dualTalk.getFirstActiveRingingCall();
        // In order to make the answer process simply, firtly, check there is
        // outgoingcall, if exist, disconnect it;

        if (dualTalk.isRingingWhenOutgoing()) {
            if (DBG) {
                log("internalAnswerCallForDualTalk: " + "ringing when dialing");
            }
            Call call = dualTalk.getSecondActiveFgCall();
            if (call.getState().isDialing()) {
                try {
                    Phone phone = call.getPhone();
                    if (phone instanceof SipPhone) {
                        call.hangup();
                    } else {
                        if (FeatureOption.MTK_VT3G324M_SUPPORT) {
                            if (VTCallUtils.isVideoCall(call) && call.getState().isAlive()) {
                                PhoneGlobals.getInstance().notifier.resetAudioState();
                            }
                        }
                        phone.hangupActiveCall();
                    }
                } catch (Exception e) {
                    log("internalAnswerCallForDualTalk:Exception ");
                }
            } else if (call.getPhone().getPhoneType() == PhoneConstants.PHONE_TYPE_CDMA
                    && call.getState().isAlive()) {
                try {
                    call.hangup();
                } catch (CallStateException e) {
                    log(e.toString());
                }
            }
        }

        List<Call> list = dualTalk.getAllNoIdleCalls();
        int callCount = list.size();

        try {
            if (callCount > 2) {
                if (DBG) {
                    log("internalAnswerCallForDualTalk: " + "has more than two calls exist.");
                }
                // This offen occurs in W+G platform.
                // On C+G platform, the only case is: CDMA has an active(real be
                // hold in network) call,
                // and the GSM has active + hold call, then GSM has a ringing
                // call
                // re-design the handle: If the cdma call doesn't support hold,
                // the cdma maybe has
                // an incoming call...
                if (dualTalk.hasActiveCdmaPhone()) {
                    handleAnswerAndEnd(dualTalk.getActiveFgCall());
                    if (DBG) {
                        log("internalAnswerCallForDualTalk (C+G): hangup the active call!");
                    }
                } else {
                    handleAnswerAndEnd(callManager.getActiveFgCall());
                }
                return;
            } else if (callCount == 2) {
                if (DBG) {
                    log("internalAnswerCallForDualTalk: " + "has two calls exist.");
                }

                // Special handling for BT case(if the BT connected, doesn't
                // enter HH case??!!)
                if (PhoneGlobals.getInstance().isBTConnected()
                        && list.get(0).getPhone() != list.get(1).getPhone()) {
                    Call noCdmaCall = dualTalk.getFirstActiveRingingCall();
                    if (noCdmaCall.getPhone().getPhoneType() != PhoneConstants.PHONE_TYPE_CDMA) {
                        handleAnswerAndEnd(dualTalk.getActiveFgCall());
                        log("internalAnswerCallForDualTalk: BT connected, so hangup active call.");
                        return;
                    }
                }

                if (list.get(0).getPhone() == list.get(1).getPhone()) {
                    if (DBG) {
                        log("internalAnswerCallForDualTalk: "
                                + "two calls exist in the same phone.");
                    }
                    handleAnswerAndEnd(callManager.getActiveFgCall());
                    return;
                } else {
                    if (DBG) {
                        log("internalAnswerCallForDualTalk: "
                                + "two calls exist in diffrent phone.");
                    }
                    if (dualTalk.hasActiveOrHoldBothCdmaAndGsm()) {
                        // because gsm has the exact status, so we deduce the
                        // cdma call status and then
                        // decide if hold operation is needed by cdma call.
                        Phone gsmPhone = dualTalk.getActiveGsmPhone();
                        Phone cdmaPhone = dualTalk.getActiveCdmaPhone();

                        Call cCall = cdmaPhone.getForegroundCall();
                        if (hasMultipleConnections(cCall)) {
                            log("internalAnswerCallForDualTalk: cdma has multiple connections, disconneted it!");
                            cCall.hangup();
                            answerCall(ringing);
                            return;
                        }
                        if (gsmPhone.getForegroundCall().getState().isAlive()) {
                            // cdma call is hold, and the ringing call must be
                            // gsm call
                            ringing.getPhone().acceptCall();
                            if (DBG) {
                                log("internalAnswerCallForDualTalk: "
                                        + "cdma hold + gsm active + gsm ringing");
                            }
                        } else {
                            // gsm has hold call
                            if (DBG) {
                                log("internalAnswerCallForDualTalk: "
                                        + "cdma active + gsm holding + cdma ringing/gsm ringing");
                            }
                            answerCall(ringing);
                        }
                    } else {
                        // This is for W+G handler
                        for (Call call : list) {
                            Call.State state = call.getState();
                            if (state == Call.State.ACTIVE) {
                                if (ringing.getPhone() != call.getPhone()) {
                                    call.getPhone().switchHoldingAndActive();
                                }
                                answerCall(ringing);
                                break;
                            } else if (state == Call.State.HOLDING) {
                                // this maybe confuse, need further check: this
                                // happend when the dialing is disconnected
                                answerCall(ringing);
                            }
                        }
                    }
                }
            } else if (callCount == 1) {
                if (DBG) {
                    log("internalAnswerCallForDualTalk: " + "there is one call exist.");
                }
                Call call = list.get(0);
                // First check if the only ACTIVE call is CDMA (three-way or
                // call-waitting) call
                if (call.getPhone().getPhoneType() == PhoneConstants.PHONE_TYPE_CDMA
                        && hasMultipleConnections(call)) {
                    log("internalAnswerCallForDualTalk: "
                            + "check if need to hangup cdma multiple call before answer the ringing call!");
                    if (ringing.getPhone() != call.getPhone()) {
                        call.hangup();
                    }
                    answerCall(ringing);
                } else if (call.getPhone() == ringing.getPhone()) {
                    answerCall(ringing);
                } else if (call.getState() == Call.State.ACTIVE) {
                    if (VTCallUtils.isVideoCall(call) || VTCallUtils.isVideoCall(ringing)) {
                        if (DBG) {
                            log("internalAnswerCallForDualTalk: "
                                    + "there is one video call, hangup current call!");
                        }
                        Phone phone = call.getPhone();
                        if (phone instanceof SipPhone) {
                            call.hangup();
                        } else {
                            phone.hangupActiveCall();
                        }
                    }
                    answerCall(ringing);
                } else {
                    answerCall(ringing);
                }
            } else if (callCount == 0) {
                if (DBG) {
                    log("internalAnswerCallForDualTalk: " + "there is no call exist.");
                }
                handleVideoAndVoiceIncoming();
                answerCall(ringing);
            }
        } catch (Exception e) {
            log(e.toString());
        }
    }

    static void handleAnswerAndEnd(Call call) {
        DualTalkUtils dualTalk = DualTalkUtils.getInstance();
        log("+handleAnswerAndEnd");
        List<Call> list = dualTalk.getAllNoIdleCalls();
        int size = list.size();
        try {
            if (call.getState().isAlive()) {
                Phone phone = call.getPhone();
                if (call.getState() == Call.State.ACTIVE) {
                    log("+handleAnswerAndEnd: " + "hangup Call.State.ACTIVE");
                    if (phone instanceof SipPhone) {
                        call.hangup();
                    } else {
                        phone.hangupActiveCall();
                    }
                } else if (call.getState() == Call.State.HOLDING) {
                    log("+handleAnswerAndEnd: " + "hangup Call.State.HOLDING and switch H&A");
                    call.hangup();
                    phone.switchHoldingAndActive();
                }
            }
        } catch (Exception e) {
            log(e.toString());
        }

        Call ringCall = dualTalk.getFirstActiveRingingCall();
        if (dualTalk.hasActiveCdmaPhone()
                && (ringCall.getPhone().getPhoneType() != PhoneConstants.PHONE_TYPE_CDMA)) {
            if (DBG) {
                log("handleAnswerAndEnd: cdma phone has acttive call, don't switch it and answer the ringing only");
            }
            try {
                ringCall.getPhone().acceptCall();
            } catch (Exception e) {
                log(e.toString());
            }
        } else {
            answerCall(dualTalk.getFirstActiveRingingCall());
        }
        log("-handleAnswerAndEnd");
    }

    static void handleVideoAndVoiceIncoming() {
        DualTalkUtils dualTalk = DualTalkUtils.getInstance();
        if (dualTalk == null || !DualTalkUtils.isSupportDualTalk()) {
            return;
        }

        if (!dualTalk.hasMultipleRingingCall()) {
            return;
        }

        Call firstRinging = dualTalk.getFirstActiveRingingCall();
        Call secondRinging = dualTalk.getSecondActiveRingCall();

        // there is no video call exist, return
        if (!VTCallUtils.isVideoCall(firstRinging) && !VTCallUtils.isVideoCall(secondRinging)) {
            return;
        }

        if (secondRinging.getState().isAlive()) {
            try {
                secondRinging.hangup();
            } catch (Exception e) {
                log(e.toString());
            }
        }
    }

    static void handleSwapForDualTalk() {
        DualTalkUtils dualTalk = DualTalkUtils.getInstance();
        if (DualTalkUtils.isSupportDualTalk() && dualTalk.isCdmaAndGsmActive()) {
            handleSwapCdmaAndGsm();
        } else if (DualTalkUtils.isSupportDualTalk() && dualTalk.hasDualHoldCallsOnly()) {
            // According to planner's define:
            // If there are two calls both in hold status, when tap the
            // swap call button, it will unhold the "background hold call"
            Call bgHoldCall = dualTalk.getSecondActiveBgCall();
            try {
                bgHoldCall.getPhone().switchHoldingAndActive();
            } catch (CallStateException e) {
                log("internalSwapCalls exception = " + e);
            }
            // Before solution: only switch the two hold call but not change the
            // status
            // mDualTalk.switchCalls();
            // updateScreen();
        } else if (DualTalkUtils.isSupportDualTalk() && dualTalk.isDualTalkMultipleHoldCase()) {
            Call fgCall = dualTalk.getActiveFgCall();
            Phone fgPhone = fgCall.getPhone();
            if (fgPhone.getBackgroundCall().getState().isAlive()) {
                if (DBG) {
                    log("Cal foreground phone's switchHoldingAndActive");
                }
                try {
                    fgPhone.switchHoldingAndActive();
                } catch (CallStateException e) {
                    log(e.toString());
                }
            } else {
                if (DBG) {
                    log("PhoneUtils.switchHoldingAndActive");
                }
                PhoneUtils.switchHoldingAndActive(dualTalk.getFirstActiveBgCall());
            }
            // PhoneUtils.switchHoldingAndActive(mCM.getFirstActiveBgCall());
        } else {
            PhoneUtils.switchHoldingAndActive(CallManager.getInstance().getFirstActiveBgCall());
        }
    }

    /**
     * we can go here, means both CDMA and GSM are active: 1.cdma has one call,
     * gsm has one call: switch between gsm and cdma phone 2.cdma has one call,
     * gsm has two call: switch between gsm's active and hold call 3.cdma has
     * two call, gsm has one call: switch gsm, and don't switch cdma phone, but
     * switch the audio path
     */
    static void handleSwapCdmaAndGsm() {

        DualTalkUtils dualTalk = DualTalkUtils.getInstance();
        Call fgCall = dualTalk.getActiveFgCall();
        Call bgCall = dualTalk.getFirstActiveBgCall();

        boolean fgPhoneSupportHold = fgCall.getPhone().getPhoneType() != PhoneConstants.PHONE_TYPE_CDMA;
        boolean bgPhoneSupportHold = bgCall.getPhone().getPhoneType() != PhoneConstants.PHONE_TYPE_CDMA;

        if (DBG) {
            log("handleSwapCdmaAndGsm fgCall = " + fgCall.getConnections());
            log("handleSwapCdmaAndGsm bgCall = " + bgCall.getConnections());
        }

        // cdma has one call, gsm has two call: switch between gsm's active and
        // hold call
        if (fgPhoneSupportHold && bgPhoneSupportHold) {
            log("handleSwapCdmaAndGsm: switch between two GSM calls.");
            try {
                fgCall.getPhone().switchHoldingAndActive();
            } catch (Exception e) {
                log(e.toString());
            }
            // Call CallManager's special api
        } else if (!fgPhoneSupportHold) {
            if (PhoneUtils.hasMultipleConnections(fgCall)) {
                log("handleSwapCdmaAndGsm: cdma has multiple calls and in foreground, only switch the audio.");
                // off cdma audio
                try {
                    bgCall.getPhone().switchHoldingAndActive();
                } catch (Exception e) {
                    log(e.toString());
                }
            } else {
                log("handleSwapCdmaAndGsm: cdma has single call and in foreground, switch by phone");
                try {
                    fgCall.getPhone().switchHoldingAndActive();
                    bgCall.getPhone().switchHoldingAndActive();
                } catch (Exception e) {
                    log(e.toString());
                }
            }

        } else if (fgPhoneSupportHold) {
            if (PhoneUtils.hasMultipleConnections(bgCall)) {
                log("handleSwapCdmaAndGsm: cdma has multiple calls and in background, only switch the audio");
                // on cdma audio
                try {
                    fgCall.getPhone().switchHoldingAndActive();
                } catch (Exception e) {
                    log(e.toString());
                }
            } else {
                log("handleSwapCdmaAndGsm: cdma has single call and in background, switch by phone");
                try {
                    fgCall.getPhone().switchHoldingAndActive();
                    bgCall.getPhone().switchHoldingAndActive();
                } catch (Exception e) {
                    log(e.toString());
                }
            }
        }
    }

    public static void acceptVtCallWithVoiceOnly(CallManager callManager, Call ringingCall) {
        if (DBG) {
            log("acceptVtCallWithVoiceOnly() ! ringingCall: " + ringingCall);
        }

        try {
            if (DBG) {
                log("acceptVtCallWithVoiceOnly() : call CallManager.acceptVtCallWithVoiceOnly() start ");
            }
            callManager.acceptVtCallWithVoiceOnly(ringingCall);
            if (DBG) {
                log("acceptVtCallWithVoiceOnly() : call CallManager.acceptVtCallWithVoiceOnly() end ");
            }
        } catch (CallStateException e) {
            e.printStackTrace();
        }
    }

    public static void setSpeakerForVT(boolean onOff) {
        PhoneGlobals mApp = PhoneGlobals.getInstance();
        WiredHeadsetManager wiredHeadsetManager = mApp.getWiredHeadsetManager();
        BluetoothManager bluetoothManager = mApp.getBluetoothManager();
        AudioRouter audioRouter = mApp.getAudioRouter();
        log("wiredHeadsetManager = " + wiredHeadsetManager
                + ", bluetoothManager = " + bluetoothManager
                + ", audioRouter = " + audioRouter);
        if (wiredHeadsetManager != null &&
                !wiredHeadsetManager.isHeadsetPlugged() &&
                bluetoothManager != null &&
                /// For ALPS01276535. @{
                // we can not turn off the BT depends on whether it audio on,
                // the broadcast may be take some time and cause a state error.
                !bluetoothManager.isBluetoothAvailable() &&
                /// @}
                audioRouter != null) {
            audioRouter.setSpeaker(onOff);

            turnOnSpeaker(mApp.getApplicationContext(), onOff, true);
        }
    }

    // all calls state
    private static final int ACTIVE_VT_CALL = 10;  // has active VT call
    private static final int ACTIVE_CALL_IS_NOT_EXIST = 11;  // has not active call
    private static final int ACTIVE_VOICE_CALL_ONLY_ONE = 12;  // has only one active call
    private static final int ACTIVE_AND_HOLD_IN_SAME_PHONE = 13;  // has active and hold in same phone
    private static final int ACTIVE_OR_HOLD_NOT_IN_SAME_PHONE = 14;  // has active and hold in not same phone or only one active phone
    private static final int ACTIVE_AND_TWO_HOLD = 15;  // has multi hold call
    private static final int ACTIVE_OUTGOING_CALL = 16;  // has outgoing call

    static String getRejectCallNotifyMsg() {
        String msg = null;
        Context context = PhoneGlobals.getInstance().getApplicationContext();
        CallManager cm = CallManager.getInstance();
        if (DualTalkUtils.isSupportDualTalk()) {
            DualTalkUtils dualTalk = DualTalkUtils.getInstance();
            boolean hasMultiRingCall = dualTalk.hasMultipleRingingCall();
            boolean firstRingCallIsVT = false;
            boolean secondRingCallIsVT = false;
            Call firstRingCall = dualTalk.getFirstActiveRingingCall();
            Call secondRingCall = dualTalk.getSecondActiveRingCall();

            if (FeatureOption.MTK_VT3G324M_SUPPORT ) {
                if (VTCallUtils.isVideoCall(firstRingCall)) {
                    firstRingCallIsVT = true;
                }
                if (VTCallUtils.isVideoCall(secondRingCall)) {
                    secondRingCallIsVT = true;
                }
            }

            switch (getAllCallsStateMsg(cm)) {
                case ACTIVE_OUTGOING_CALL:
                    if (hasMultiRingCall) {
                        msg = context.getString(R.string.dualtalk_tip_accept_disconnect_outgoing_reject_incoming);
                    } else {
                        msg = context.getString(R.string.dualtalk_tip_accept_disconnect_outgoing);
                    }
                    break;

                case ACTIVE_VT_CALL:
                    if (hasMultiRingCall) {
                        if (firstRingCallIsVT) {
                            msg = context.getString(
                                    R.string.dualtalk_tip_accept_disconnect_current_reject_incoming);
                        } else {
                            msg = context.getString(R.string.dualtalk_tip_accept_disconnect_current);
                        }
                    } else {
                        msg = context.getString(R.string.dualtalk_tip_accept_disconnect_current);
                    }
                    break;

                case ACTIVE_AND_TWO_HOLD:
                    msg = context.getString(R.string.dualtalk_tip_accept_disconnect_current);
                    break;

                case ACTIVE_AND_HOLD_IN_SAME_PHONE:
                    if (hasMultiRingCall) {
                        if (firstRingCallIsVT) {
                            msg = context.getString(R.string.dualtalk_tip_accept_disconnect_voice);
                        } else {
                            msg = context.getString(
                                    R.string.dualtalk_tip_accept_disconnect_current_reject_incoming);
                        }
                    } else {
                        if (firstRingCallIsVT) {
                            msg = context.getString(R.string.dualtalk_tip_accept_disconnect_voice);
                        } else {
                            msg = context.getString(R.string.dualtalk_tip_accept_disconnect_current);
                        }
                    }
                    break;

                case ACTIVE_CALL_IS_NOT_EXIST:
                    if (hasMultiRingCall && (firstRingCallIsVT || secondRingCallIsVT)) {
                        msg = context.getString(R.string.dualtalk_tip_accept_disconnect_incoming);
                    }
                    break;

                case ACTIVE_OR_HOLD_NOT_IN_SAME_PHONE:
                    if (hasMultiRingCall) {
                        if (firstRingCallIsVT) {
                            msg = context.getString(R.string.dualtalk_tip_accept_disconnect_voice);
                        } else if (secondRingCallIsVT) {
                            msg = context.getString(
                                    R.string.dualtalk_tip_accept_disconnect_current_reject_incoming);
                        }
                    } else if (firstRingCallIsVT) {
                        msg = context.getString(R.string.dualtalk_tip_accept_disconnect_voice);
                    }
                    break;

                case ACTIVE_VOICE_CALL_ONLY_ONE:
                    if (hasMultiRingCall) {
                        if (firstRingCallIsVT) {
                            msg = context.getString(
                                    R.string.dualtalk_tip_accept_disconnect_current_reject_incoming);
                        } else if (!secondRingCallIsVT) {
                            Call fgCall = dualTalk.getActiveFgCall();
                            if (fgCall.getState() == Call.State.ACTIVE
                                    && fgCall.getPhone() != firstRingCall.getPhone()) {
                                msg = context.getString(R.string.dualtalk_tip_accept_disconnect_incoming);
                            }
                        }
                    } else if (firstRingCallIsVT) {
                        msg = context.getString(R.string.dualtalk_tip_accept_disconnect_current);
                    }
                    break;

                default:
                    break;
            }
            log("getRejectCallNotifyMsg: get dual talk msg=" + msg);
        } else if (cm.hasActiveFgCall() && cm.hasActiveBgCall()) {
            // single talk
            msg = context.getString(R.string.dualtalk_tip_accept_disconnect_current);
            log("getRejectCallNotifyMsg: get single talk msg=" + msg);
        }

        log("getRejectCallNotifyMsg msg=" + msg);
        return msg;
    }

    static int getAllCallsStateMsg(CallManager callManager) {
        DualTalkUtils dt = DualTalkUtils.getInstance();
        if (null == dt) {
            return -1;
        }

        // has one outgoing call.
        if (dt.isRingingWhenOutgoing()) {
            log("ACTIVE_OUTGOING_CALL");
            return ACTIVE_OUTGOING_CALL;
        }

        // has active VT call
        if (VTCallUtils.isVTActive()) {
            log("ACTIVE_VT_CALL");
            return ACTIVE_VT_CALL;
        }

        // has one active call and two hold call.
        if (dt.isDualTalkMultipleHoldCase()) {
            log("ACTIVE_AND_TWO_HOLD");
            return ACTIVE_AND_TWO_HOLD;
        }

        // has one active call and one hold call in same phone
        Call fgCall = callManager.getActiveFgCall();
        Call bgCall = callManager.getFirstActiveBgCall();
        if (fgCall != null && fgCall.getState().isAlive()
                && bgCall != null && bgCall.getState().isAlive()
                && fgCall.getPhone() == bgCall.getPhone()) {
            log("ACTIVE_AND_HOLD_IN_SAME_PHONE");
            return ACTIVE_AND_HOLD_IN_SAME_PHONE;
        }

        // has one active call and one hold call not in same phone
        if ((fgCall != null && fgCall.getState().isAlive()
                && bgCall != null && bgCall.getState().isAlive()
                && fgCall.getPhone() != bgCall.getPhone())
                || dt.hasDualHoldCallsOnly()) {
            log("ACTIVE_OR_HOLD_NOT_IN_SAME_PHONE");
            return ACTIVE_OR_HOLD_NOT_IN_SAME_PHONE;
        }

        // has not active call
        List<Call> activeCalls = dt.getAllActiveCalls();
        activeCalls = dt.getAllActiveCalls();
        log("activeCalls : " + activeCalls.size());
        if (0 == activeCalls.size()) {
            log("ACTIVE_CALL_IS_NOT_EXIST");
            return ACTIVE_CALL_IS_NOT_EXIST;
        }

         // has only one active voice call
         if (1 == activeCalls.size() && !VTCallUtils.isVTActive()) {
            log("ACTIVE_CALL_ONLY_ONE");
            return ACTIVE_VOICE_CALL_ONLY_ONE;
        }

        return -1;
    }

    // Dualtalk start. @{
    private static long mLastClickActionTime = 0;
    private static final int DUALTALK_SELECT_CALL_DIALOG = 0;

    static List<Call> getRingingCalls() {
        List<Call> callList = new ArrayList<Call>();
        if (DualTalkUtils.isSupportDualTalk()) {
            Call firstRingingCall = DualTalkUtils.getInstance().getFirstActiveRingingCall();
            Call secondRingingCall = DualTalkUtils.getInstance().getSecondActiveRingCall();
            if (firstRingingCall != null) {
                callList.add(firstRingingCall);
            }
            if (secondRingingCall != null) {
                callList.add(secondRingingCall);
            }
        } else {
            callList = CallManager.getInstance().getRingingCalls();
        }
        return callList;
    }

    static List<Call> getForegroundCalls() {
        List<Call> callList = new ArrayList<Call>();
        if (DualTalkUtils.isSupportDualTalk()) {
            Call firstFgCall = DualTalkUtils.getInstance().getActiveFgCall();
            Call secondFgCall = DualTalkUtils.getInstance().getSecondActiveFgCall();
            if (firstFgCall != null) {
                callList.add(firstFgCall);
            }
            if (secondFgCall != null) {
                callList.add(secondFgCall);
            }
        } else {
            callList = CallManager.getInstance().getForegroundCalls();
        }
        return callList;
    }

    static List<Call> getBackgroundCalls() {
        List<Call> callList = new ArrayList<Call>();
        if (DualTalkUtils.isSupportDualTalk()) {
            Call firstBgCall = DualTalkUtils.getInstance().getFirstActiveBgCall();
            Call secondBgCall = DualTalkUtils.getInstance().getSecondActiveBgCall();
            if (firstBgCall != null) {
                callList.add(firstBgCall);
            }
            if (secondBgCall != null) {
                callList.add(secondBgCall);
            }
        } else {
            callList = CallManager.getInstance().getBackgroundCalls();
        }
        return callList;
    }

    static void onHoldClick() {
        final boolean hasActiveCall = CallManager.getInstance().hasActiveFgCall();
        final boolean hasHoldingCall = CallManager.getInstance().hasActiveBgCall();
        final boolean haveMultipleHoldingCall = (DualTalkUtils.getInstance() != null)
                && DualTalkUtils.getInstance().hasDualHoldCallsOnly();
        log("onHoldClick: hasActiveCall = " + hasActiveCall + ", hasHoldingCall = "
                + hasHoldingCall);
        if (hasActiveCall && !hasHoldingCall) {
            // There's only one line in use, and that line is active.
            // Really means "hold" in this state
            switchHoldingAndActive(CallManager.getInstance().getFirstActiveBgCall()); 
        } else if (!hasActiveCall && hasHoldingCall && !haveMultipleHoldingCall) {
            // There's only one line in use, and that line is on hold.
            // Really means "unhold" in this state
            switchHoldingAndActive(CallManager.getInstance().getFirstActiveBgCall()); 
        } else {
            // Either zero or 2 lines are in use; "hold/unhold" is meaningless.
            // For c+g project, if allow hold gsm and cdma, the call status will
            // be error!!!
            switchHoldingAndActiveForDualTalk(hasActiveCall);
        }
    }

    static void onSwapClick() {
        long currentTime = SystemClock.uptimeMillis();
        if (currentTime - mLastClickActionTime > 1000) {
            if (DualTalkUtils.isSupportDualTalk()
                    && DualTalkUtils.getInstance().isDualTalkMultipleHoldCase()) {
                List<Call> list = DualTalkUtils.getInstance().getAllHoldCalls();
                selectWhichCallActive(list);
            } else {
                internalSwapCalls();
            }
            mLastClickActionTime = currentTime;
        }
    }

    static void switchHoldingAndActiveForDualTalk(final boolean hasActiveCall) {
        if (DualTalkUtils.isSupportDualTalk() && DualTalkUtils.getInstance() != null
                && DualTalkUtils.getInstance().isMultiplePhoneActive()
                && !DualTalkUtils.getInstance().hasActiveCdmaPhone()) {
            if (hasActiveCall) {
                log("switchHoldingAndActiveForDualTalk: has active call.");
                Call fgCall = DualTalkUtils.getInstance().getActiveFgCall();
                try {
                    fgCall.getPhone().switchHoldingAndActive();
                } catch (CallStateException e) {
                    log("switchHoldingAndActiveForDualTalk: CallStateException.");
                }
            } else {
                log("switchHoldingAndActiveForDualTalk: has two background calls");
                Call bgCall = DualTalkUtils.getInstance().getFirstActiveBgCall();
                try {
                    bgCall.getPhone().switchHoldingAndActive();
                } catch (CallStateException e) {
                    log("switchHoldingAndActiveForDualTalk: CallStateException.");
                }
            }
        }
    }

    static void selectWhichCallActive(final List<Call> holdList) {
        if (DBG) {
            log("+selectWhichCallActive, holdList" + holdList);
        }
        if (DBG) {
            for (Call call : holdList) {
                log("selectWhichCallActive " + call.getConnections());
            }
        }
        if (mConnectionHandler != null) {
            mConnectionHandler.sendMessage(mConnectionHandler
                    .obtainMessage(DUALTALK_SELECT_CALL_DIALOG, holdList));
        } else {
            log("-selectWhichCallActive, do nothing, mConnectionHandler is null");
        }
        if (DBG) {
            log("-selectWhichCallActive");
        }
    }

    static void showSelectCallDialog(final List<Call> holdList) {
        log("+showSelectCallDialog, holdList" + holdList);
        Context context = PhoneGlobals.getInstance().getApplicationContext();
        AlertDialog callSelectDialog = null;
        if (null == callSelectDialog || !callSelectDialog.isShowing()) {
            AlertDialog.Builder builder = new AlertDialog.Builder(context).setNegativeButton(
                    context.getResources().getString(android.R.string.cancel),
                    (DialogInterface.OnClickListener) null);

            CallPickerAdapter callPickerAdapter = new CallPickerAdapter(context, holdList);
            if (2 == holdList.size()) {
                callPickerAdapter.setOperatorName(getOperatorNameByCall(holdList.get(0)), 
                        getOperatorNameByCall(holdList.get(1)));
                callPickerAdapter.setOperatorColor(getOperatorColorByCall(holdList.get(0)),
                        getOperatorColorByCall(holdList.get(1)));
                callPickerAdapter.setCallerInfoName(getCallInfoName(1),
                        getCallInfoName(2));

                builder.setSingleChoiceItems(callPickerAdapter, -1,
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int which) {
                                final AlertDialog alert = (AlertDialog) dialog;
                                final ListAdapter listAdapter = alert.getListView().getAdapter();
                                Call call = (Call) listAdapter.getItem(which);
                                Call firstBgCall = DualTalkUtils.getInstance().getFirstActiveBgCall();
                                Call secondBgCall = DualTalkUtils.getInstance().getSecondActiveBgCall();
                                if (null != call && null != firstBgCall && null != secondBgCall) {
                                    Phone firstPhone = firstBgCall.getPhone();
                                    Phone secondPhone = secondBgCall.getPhone();
                                    if (DBG) {
                                        log("select call at phone :" + call.getPhone() + " firstPhone " + firstPhone
                                                + " secondPhone " + secondPhone);
                                    }

                                    if (call.getPhone() == firstPhone) {
                                        //This is ugly, only for the craze clicking and clicking......
                                        //This is only for tester's operation[ALPS00446633]
                                        mLastClickActionTime = SystemClock.uptimeMillis();
                                        if (DualTalkUtils.getInstance().isCdmaAndGsmActive()) {
                                            try {
                                                call.getPhone().switchHoldingAndActive();
                                            } catch (CallStateException ce) {
                                                log("selectWhichCallActive switch exception " + ce);
                                            }
                                        } else {
                                            switchHoldingAndActive(call);
                                        }
                                    } else {
                                        handleUnholdAndEnd(DualTalkUtils.getInstance().getActiveFgCall());
                                    }
                                }
                                dialog.dismiss();
                            }
                        }).setTitle(context.getResources().getString(R.string.which_call_to_activate));
                callSelectDialog = builder.create();
                callSelectDialog.getWindow().setType(WindowManager.LayoutParams.TYPE_SYSTEM_ALERT);
                //Need to check whether excute this in main handler.
                callSelectDialog.show();
            }
        }
    }

    /**
     * get OperatorName by call.
     * Called in InCallSreen's slectWichCallActive(...).
     */
    static String getOperatorNameByCall(Call call) {
        if (call == null) {
            return null;
        }

        if (GeminiUtils.isGeminiSupport()) {
            SimInfoRecord info = getSimInfoByCall(call);
            if (info != null && !TextUtils.isEmpty(info.mDisplayName)
                        && (call.getPhone().getPhoneType() != PhoneConstants.PHONE_TYPE_SIP)) {
                return info.mDisplayName;
            } else if (call.getPhone().getPhoneType() == PhoneConstants.PHONE_TYPE_SIP) {
                return PhoneGlobals.getInstance().getApplicationContext().getString(R.string.incall_call_type_label_sip);
            }
        } else {
            return SystemProperties.get(TelephonyProperties.PROPERTY_OPERATOR_ALPHA);
        }
        return null;
    }

    /**
     * get OperatorColor by call.
     * Called in InCallSreen's slectWichCallActive(...).
     */
    static int getOperatorColorByCall(Call call) {
        if (call == null) {
            return -1;
        }

        if (GeminiUtils.isGeminiSupport()) {
            SimInfoRecord info = PhoneUtils.getSimInfoByCall(call);
            if (info != null && !TextUtils.isEmpty(info.mDisplayName)
                        && (call.getPhone().getPhoneType() != PhoneConstants.PHONE_TYPE_SIP)) {
                return info.mColor;
            }
        }
        return -1;
    }

    /**
     * Called in InCallSreen's slectWichCallActive(...).
     * @param position
     * @return
     */
    static String getCallInfoName(int position) {
        return null;
    }

    static void internalSwapCalls() {
        if (DBG) log("internalSwapCalls()...");

        handleSwapForDualTalk();

        // If we have a valid BluetoothPhoneService then since CDMA network or
        // Telephony FW does not send us information on which caller got swapped
        // we need to update the second call active state in BluetoothPhoneService internally
        if (CallManager.getInstance().getBgPhone().getPhoneType() == PhoneConstants.PHONE_TYPE_CDMA) {
            IBluetoothHeadsetPhone btPhone = PhoneGlobals.getInstance().getBluetoothPhoneService();
            if (btPhone != null) {
                try {
                    btPhone.cdmaSwapSecondCallState();
                } catch (RemoteException e) {
                    Log.e(LOG_TAG, Log.getStackTraceString(new Throwable()));
                }
            }
        }
    }

    static void swapCallsByCondition() {
        long currentTime = SystemClock.uptimeMillis();
        if (okToSwapCalls(CallManager.getInstance()) && (currentTime - mLastClickActionTime > 1000)) {
            internalSwapCalls();
            mLastClickActionTime = currentTime;
            if (DBG) {
                log("Respond the swap click action.");
            }
        } else {
            if (DBG) {
                log("Ignore the swap click action.");
            }
        }
    }

    static void handleHoldAndUnhold() {
        if (!DualTalkUtils.isSupportDualTalk()) {
            return ;
        }
        Call fgCall = DualTalkUtils.getInstance().getActiveFgCall();
        Call bgCall = DualTalkUtils.getInstance().getFirstActiveBgCall();
        try {
            if (fgCall.getState().isAlive()) {
                fgCall.getPhone().switchHoldingAndActive();
            } else if (bgCall.getState().isAlive()) {
                bgCall.getPhone().switchHoldingAndActive();
            }
        } catch (Exception e) {
            log("handleHoldAndUnhold: " + e.toString());
        }
    }

    static void handleUnholdAndEnd(Call call) {
        log("+handleUnholdAndEnd");
        List<Call> list = DualTalkUtils.getInstance().getAllNoIdleCalls();
        int size = list.size();
        try {
            if (call.getState().isAlive()) {
                Phone phone = call.getPhone();
                if (call.getState() == Call.State.ACTIVE) {
                    log("+handleUnholdAndEnd: " + "hangup Call.State.ACTIVE");
                    if (phone instanceof SipPhone) {
                        call.hangup();
                    } else {
                        phone.hangupActiveCall();
                    }
                } else if (call.getState() == Call.State.HOLDING) {
                    log("+handleUnholdAndEnd: " + "hangup Call.State.HOLDING and switch H&A");
                    call.hangup();
                    phone.switchHoldingAndActive();
                }
            }
            DualTalkUtils.getInstance().getSecondActiveBgCall().getPhone().switchHoldingAndActive();
        } catch (Exception e) {
            log(e.toString());
        }
        log("-handleUnholdAndEnd");
    }

    static void secondaryHoldPhotoClicked() {
        if (DualTalkUtils.isSupportDualTalk()
                && DualTalkUtils.getInstance().isDualTalkMultipleHoldCase()) {
            handleUnholdAndEnd(DualTalkUtils.getInstance().getActiveFgCall());
        } else if (okToSwapCalls(PhoneGlobals.getInstance().mCM)) {
            internalSwapCalls();
        }
    }

    // Dualtalk end. @}
    
    /// M: for ALPS01269092 @{
    // set speaker mode when pull headset on video calling
    public static void setSpeaker(boolean flag) {
            sIsSpeakerEnabled = flag;
    }
    /// @}

    /**
     * M: Get the phones in order, if there are active call, make sure
     *  the first phone contain the forgroundcall.
     * @return
     */
    private static List<Phone> getAllPhonesInOrder() {
        CallManager cm = PhoneGlobals.getInstance().mCM;
        List<Phone> phones = cm.getAllPhones();
        List<Phone> phonesInOrder = new ArrayList<Phone>();
        for (Phone phone : phones) {
            if (phone.getForegroundCall() != null) {
                phonesInOrder.add(0, phone);
            } else {
                phonesInOrder.add(phone);
            }
        }
        return phonesInOrder;
    }

    /**
     * when phone close to face, ture off speaker to make the p-sensor enable.
     */
    public static void onPhoneRaised() {
        log("onPhoneRaised()");
        PhoneGlobals phoneGlobals = PhoneGlobals.getInstance();
        if (PhoneRaiseDetector.isValidCondition()
                && PhoneUtils.isSpeakerOn(phoneGlobals.getApplicationContext())
                && phoneGlobals.getBluetoothManager() != null
                && !phoneGlobals.getBluetoothManager().isBluetoothAvailable()) {
            phoneGlobals.getAudioRouter().setSpeaker(false);
            turnOnSpeaker(phoneGlobals.getApplicationContext(), false, true);
        } else {
            log("onPhoneRaised(), condition in not satisfy, not toogle speaker");
        }
    }

    /**
     * when only incoming call, should turn off speaker.
     */
    public static void updateSpeaker() {
        CallManager callManager = CallManager.getInstance();
        final boolean hasRingingCall = callManager.hasActiveRingingCall();
        final PhoneGlobals app = PhoneGlobals.getInstance();
        if (hasRingingCall) {
            Phone phone = callManager.getRingingPhone();
            Call ringingCall = callManager.getFirstActiveRingingCall();
            final boolean isRealIncomingCall = isRealIncomingCall(ringingCall.getState());
            final boolean speakerActivated = activateSpeakerIfDocked(ringingCall.getPhone());
            final BluetoothManager btManager = app.getBluetoothManager();
            if (isRealIncomingCall && !speakerActivated && isSpeakerOn(app)
                    && !btManager.isBluetoothHeadsetAudioOn()
                    && !VTCallUtils.isVideoCall(ringingCall) /** M: VT */) {
                // This is not an error but might cause users' confusion. Add log just in case.
                Log.i(LOG_TAG, "Forcing speaker off due to new incoming call...");
                turnOnSpeaker(app, false, true);
            }
        }
    }

    /// M: For ALPS01374729. @{
    public static boolean sNoNeedStartUssdActivity = false;
    /// @}
}
