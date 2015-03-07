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

package com.android.phone;


import com.android.internal.telephony.Call;
import com.android.internal.telephony.CallerInfo;
import com.android.internal.telephony.Connection;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.TelephonyCapabilities;
import com.android.phone.common.CallLogAsync;

import android.database.sqlite.SQLiteDiskIOException;
import android.net.Uri;
import android.os.SystemProperties;
import android.provider.CallLog.Calls;
import android.telephony.PhoneNumberUtils;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;

import com.mediatek.calloption.CallOptionUtils;
import com.mediatek.phone.gemini.GeminiUtils;
import com.mediatek.phone.PhoneFeatureConstants.FeatureOption;
import com.mediatek.phone.provider.CallHistoryAsync;
import com.mediatek.phone.wrapper.ITelephonyWrapper;
import com.mediatek.phone.PhoneLog;
import com.mediatek.phone.SIMInfoWrapper;
import com.mediatek.telephony.SimInfoManager;
import com.mediatek.telephony.SimInfoManager.SimInfoRecord;

import java.util.List;


/**
 * Helper class for interacting with the call log.
 */
class CallLogger {
    private static final String LOG_TAG = CallLogger.class.getSimpleName();
    private static final boolean DBG = (PhoneGlobals.DBG_LEVEL >= 1) &&
        (SystemProperties.getInt("ro.debuggable", 0) == 1);
    private static final boolean VDBG = (PhoneGlobals.DBG_LEVEL >= 2);

    private PhoneGlobals mApplication;
    private CallLogAsync mCallLog;
    public CallLogger(PhoneGlobals application, CallLogAsync callLogAsync) {
        mApplication = application;
        mCallLog = callLogAsync;
    }

    /**
     * Logs a call to the call log based on the connection object passed in.
     *
     * @param c The connection object for the call being logged.
     * @param callLogType The type of call log entry.
     * @param slotId The slot id for the call being logged.
     */
    public void logCall(Connection c, int callLogType, int slotId) {
        final String number = c.getAddress();
        final long date = c.getCreateTime();
        final long duration = c.getDurationMillis();
        final Phone phone = c.getCall().getPhone();

        final CallerInfo ci = getCallerInfoFromConnection(c);  // May be null.
        final String logNumber = getLogNumber(c, ci);

        if (DBG) {
            log("- onDisconnect(): logNumber set to:" + PhoneUtils.toLogSafePhoneNumber(logNumber) +
                ", number set to: " + PhoneUtils.toLogSafePhoneNumber(number));
        }

        /// M: @{
        mPhoneType = phone.getPhoneType();
        mIsComing = c.isIncoming();
        if (FeatureOption.MTK_VT3G324M_SUPPORT) {
            mVtCall = c.isVideo() ? 1 : 0;
        }
        PhoneLog.d(LOG_TAG, "number=" + number + ", duration=" + duration+", isVT="+ mVtCall);
        /// @}

        /// M: For ALPS00114062 @{
        // show every connection's last time of conference call.
        if (needToShowCallTime(c, duration)) {
            Toast.makeText(mApplication.getApplicationContext(),
                    formatDuration((int) (duration / 1000)), Toast.LENGTH_SHORT).show();
        }
        /// @}

        // TODO: In getLogNumber we use the presentation from
        // the connection for the CNAP. Should we use the one
        // below instead? (comes from caller info)

        // For international calls, 011 needs to be logged as +
        final int presentation = getPresentation(c, ci);

        final boolean isOtaspNumber = TelephonyCapabilities.supportsOtasp(phone)
                && phone.isOtaSpNumber(number);

        // Don't log OTASP calls.
        if (!isOtaspNumber) {
            logCall(ci, logNumber, presentation, callLogType, date, duration, slotId);
        }
    }

    /**
     * Came as logCall(Connection,int) but calculates the call type from the connection object.
     */
    public void logCall(Connection c, int slotId) {
        final Connection.DisconnectCause cause = c.getDisconnectCause();

        // Set the "type" to be displayed in the call log (see constants in CallLog.Calls)
        final int callLogType;

        if (c.isIncoming()) {
            callLogType = (PhoneUtils.shouldAutoReject(c) && cause == Connection.DisconnectCause.INCOMING_REJECTED) ? Calls.AUTOREJECTED_TYPE
                    : ((cause == Connection.DisconnectCause.INCOMING_MISSED ? Calls.MISSED_TYPE : Calls.INCOMING_TYPE));
        } else {
            callLogType = Calls.OUTGOING_TYPE;
        }
        if (VDBG) log("- callLogType: " + callLogType + ", UserData: " + c.getUserData());

        logCall(c, callLogType, slotId);
    }

    /**
     * Logs a call to the call from the parameters passed in.
     */
    public void logCall(CallerInfo ci, String number, int presentation, int callType, long start,
                        long duration, int slotId) {
        final boolean isEmergencyNumber = PhoneNumberUtils.isLocalEmergencyNumber(number,
                mApplication);

        // On some devices, to avoid accidental redialing of
        // emergency numbers, we *never* log emergency calls to
        // the Call Log.  (This behavior is set on a per-product
        // basis, based on carrier requirements.)
        final boolean okToLogEmergencyNumber =
            mApplication.getResources().getBoolean(
                        R.bool.allow_emergency_numbers_in_call_log);

        // Don't log emergency numbers if the device doesn't allow it,
        boolean isOkToLogThisCall = !isEmergencyNumber || okToLogEmergencyNumber;

        if (isOkToLogThisCall) {
            if (DBG) {
                log("sending Calllog entry: " + ci + ", " + PhoneUtils.toLogSafePhoneNumber(number)
                    + "," + presentation + ", " + callType + ", " + start + ", " + duration);
            }

            /// M: For GEMINI or Other type call(Ex: sip call/video call) @{
            CallLogAsync.AddCallArgs args;
            if (mPhoneType == PhoneConstants.PHONE_TYPE_CDMA && mIsCdmaCallWaitingReject) {
                args = getCallArgsForCdmaCallWaitingReject(ci, number,
                        presentation, callType, start, duration, slotId);
            } else {
                args = getCallArgs(ci, number, presentation, callType, start, duration, slotId);
            }
            /// @}

            try {
                mCallLog.addCall(args);
            } catch (SQLiteDiskIOException e) {
                // TODO Auto-generated catch block
                Log.e(LOG_TAG, "Error!! - logCall() Disk Full!");
                e.printStackTrace();
            }
        }
        reset();
    }

    /**
     * Get the caller info.
     *
     * @param conn The phone connection.
     * @return The CallerInfo associated with the connection. Maybe null.
     */
    public static CallerInfo getCallerInfoFromConnection(Connection conn) {
        CallerInfo ci = null;
        Object o = conn.getUserData();

        if ((o == null) || (o instanceof CallerInfo)) {
            ci = (CallerInfo) o;
        } else if (o instanceof Uri) {
            ci = CallerInfo.getCallerInfo(PhoneGlobals.getInstance().getApplicationContext(), (Uri) o);
        } else {
            ci = ((PhoneUtils.CallerInfoToken) o).currentInfo;
        }
        return ci;
    }

    /**
     * Retrieve the phone number from the caller info or the connection.
     *
     * For incoming call the number is in the Connection object. For
     * outgoing call we use the CallerInfo phoneNumber field if
     * present. All the processing should have been done already (CDMA vs GSM numbers).
     *
     * If CallerInfo is missing the phone number, get it from the connection.
     * Apply the Call Name Presentation (CNAP) transform in the connection on the number.
     *
     * @param conn The phone connection.
     * @param callerInfo The CallerInfo. Maybe null.
     * @return the phone number.
     */
    private String getLogNumber(Connection conn, CallerInfo callerInfo) {
        String number = null;

        if (conn.isIncoming()) {
            number = conn.getAddress();
        } else {
            // For emergency and voicemail calls,
            // CallerInfo.phoneNumber does *not* contain a valid phone
            // number.  Instead it contains an I18N'd string such as
            // "Emergency Number" or "Voice Mail" so we get the number
            // from the connection.
            if (null == callerInfo || TextUtils.isEmpty(callerInfo.phoneNumber) ||
                callerInfo.isEmergencyNumber() || callerInfo.isVoiceMailNumber()) {
                if (conn.getCall().getPhone().getPhoneType() == PhoneConstants.PHONE_TYPE_CDMA) {
                    // In cdma getAddress() is not always equals to getOrigDialString().
                    number = conn.getOrigDialString();
                } else {
                    number = conn.getAddress();
                }
            } else {
                number = callerInfo.phoneNumber;
            }
        }

        if (null == number) {
            return null;
        } else {
            int presentation = conn.getNumberPresentation();

            // Do final CNAP modifications.
            String newNumber = PhoneUtils.modifyForSpecialCnapCases(mApplication, callerInfo,
                                                          number, presentation);

            if (!PhoneNumberUtils.isUriNumber(number)) {
                number = PhoneNumberUtils.stripSeparators(number);
            }
            if (VDBG) log("getLogNumber: " + number);
            return number;
        }
    }

    /**
     * Get the presentation from the callerinfo if not null otherwise,
     * get it from the connection.
     *
     * @param conn The phone connection.
     * @param callerInfo The CallerInfo. Maybe null.
     * @return The presentation to use in the logs.
     */
    public static int getPresentation(Connection conn, CallerInfo callerInfo) {
        int presentation;

        if (null == callerInfo) {
            presentation = conn.getNumberPresentation();
        } else {
            presentation = callerInfo.numberPresentation;
            if (DBG) log("- getPresentation(): ignoring connection's presentation: " +
                         conn.getNumberPresentation());
        }
        if (DBG) log("- getPresentation: presentation: " + presentation);
        return presentation;
    }

    private static void log(String msg) {
        Log.d(LOG_TAG, msg);
    }

    // --------------------------------------- MTK ---------------------------------------
    private final static int CALL_TYPE_VIDEO = 1;
    private int mVtCall = 0;  // add for VT call
    private int mPhoneType = PhoneConstants.PHONE_TYPE_NONE;
    private boolean mIsComing = false;
    private boolean mIsCdmaCallWaitingReject = false;
    /*
     * New Feature by Mediatek Begin.
     *   CR ID: ALPS00114062
     */
    private String formatDuration(long elapsedSeconds) {
        long minutes = 0;
        long seconds = 0;

        if (elapsedSeconds >= 60) {
            minutes = elapsedSeconds / 60;
            elapsedSeconds -= minutes * 60;
        }
        seconds = elapsedSeconds;

        return (mApplication.getString(R.string.card_title_call_ended) + "(" +
            mApplication.getString(R.string.callDurationFormat, minutes, seconds) + ")");
    }


    /**
     * for CDMA when Timeout or Ignore Waiting Call
     *
     */
    public void setCdmaCallWaitingReject(boolean isCdmaCallWaitingReject) {
        mIsCdmaCallWaitingReject = isCdmaCallWaitingReject;
    }

    /**
     * Get the call arguments when CallWaiting reject for CDMA.
     * @param ci The caller information for the call being logged.
     * @param number The number for the call being logged.
     * @param presentation The presentation to use in the logs.
     * @param callType The type of call log entry.
     * @param start The start time of call being logged.
     * @param duration The duration for call being logged.
     * @param slotId The slot id for the call being logged.
     * @return CallLogAsync.AddCallArgs related args for the call being logged.
     */
    private CallLogAsync.AddCallArgs getCallArgsForCdmaCallWaitingReject(CallerInfo ci,
            String number, int presentation, int callType, long start, long duration, int slotId) {
        if (DBG) {
            log("getCallArgsForCdmaCallWaitingReject");
        }
        CallLogAsync.AddCallArgs cdmaArgs;
        if (GeminiUtils.isGeminiSupport()) {
            if (DBG) {
                log("getCallArgsForCdmaCallWaitingReject, support gemini.");
            }
            int cdmaSimId = 0;
            if (ITelephonyWrapper.hasIccCard(slotId)) {
                SimInfoRecord si = SimInfoManager.getSimInfoBySlot(PhoneGlobals.getInstance(),
                        slotId);
                if (si != null) {
                    cdmaSimId = (int) si.mSimInfoId;
                }
            }
            cdmaArgs = new CallLogAsync.AddCallArgs(mApplication, ci, number, presentation,
                    callType, start, duration, cdmaSimId);
        } else {
            cdmaArgs = new CallLogAsync.AddCallArgs(mApplication, ci, number, presentation,
                    callType, start, duration);
        }
        return cdmaArgs;
    }

    /**
     * Get the call arguments when disconnected.
     * @param ci The caller information for the call being logged.
     * @param number The number for the call being logged.
     * @param presentation The presentation to use in the logs.
     * @param callType The type of call log entry.
     * @param start The start time of call being logged.
     * @param duration The duration of call being logged.
     * @param slotId The slot id for the call being logged.
     * @return CallLogAsync.AddCallArgs related args for the call being logged.
     */
    private CallLogAsync.AddCallArgs getCallArgs(CallerInfo ci,
            String number, int presentation, int callType, long start,
            long duration, int slotId) {
        CallLogAsync.AddCallArgs args;
        int simIdEx = CallNotifier.CALL_TYPE_NONE;
        //Get the phone type for sip
        boolean isSipCall = false;
        if (mPhoneType == PhoneConstants.PHONE_TYPE_SIP) {
            isSipCall = true;
        }
        if (!GeminiUtils.isGeminiSupport() || isSipCall) {
            //Single Card
            if (isSipCall) {
                simIdEx = CallNotifier.CALL_TYPE_SIP;
            } else {
                simIdEx = CallNotifier.CALL_TYPE_NONE;
                if (ITelephonyWrapper.hasIccCard(PhoneConstants.GEMINI_SIM_1)) {
                    SimInfoRecord info = SIMInfoWrapper.getDefault().getSimInfoBySlot(0);
                    if (info != null) {
                        simIdEx = (int)info.mSimInfoId;
                    } else {
                        //Give an default simId, in most case, this is invalid
                        simIdEx = 1;
                    }
                }
                if(DBG) {
                    log("for single card, simIdEx = " + simIdEx);
                }
            }
        } else {
            //dual SIM
            // Geminni Enhancement: change call log to sim id;
            SimInfoRecord si;
            if (ITelephonyWrapper.hasIccCard(slotId)) {
               si = SimInfoManager.getSimInfoBySlot(PhoneGlobals.getInstance(), slotId);
               if (si != null) {
                   simIdEx = (int)si.mSimInfoId;
               }
            }
            if(DBG) {
                log("for dual SIM, simIdEx = " + simIdEx);
            }
        }

        if (FeatureOption.MTK_VT3G324M_SUPPORT) {
            args = new CallLogAsync.AddCallArgs(
                           mApplication, ci, number, presentation,
                           callType, start, duration, simIdEx, mVtCall);
        } else {
            args = new CallLogAsync.AddCallArgs(
                           mApplication, ci, number, presentation,
                           callType, start, duration, simIdEx);
        }

        addCallHistoryAsync(number, start, duration, isSipCall, slotId);
        return args;
    }

    /**
     * Add call number info to call history database for international dialing feature
     * !!!! need to check okToLogThisCall is suitable for international dialing feature
     * @param number The number for the call being logged.
     * @param start The start time of the call being logged.
     * @param duration The duration of the call being logged.
     * @param isSipCall whether sip call
     * @param slotId The slot id for the call being logged.
    */
    private void addCallHistoryAsync(String number, long start, long duration, boolean isSipCall,
            int slotId) {
        final boolean isEmergencyNumber = PhoneNumberUtils.isLocalEmergencyNumber(number,
                mApplication);
        if (!isEmergencyNumber && !mIsComing && mVtCall != CALL_TYPE_VIDEO && !isSipCall
                && duration >= CallNotifier.CALL_DURATION_THRESHOLD_FOR_CALL_HISTORY && slotId > -1) {
            String countryISO = CallOptionUtils.getCurrentCountryISO(PhoneGlobals.getInstance());
            try {
                new CallHistoryAsync().addCall(new CallHistoryAsync.AddCallArgs(PhoneGlobals
                        .getInstance(), number, countryISO, start, slotId, GeminiUtils
                        .isGeminiSupport()));
            } catch (SQLiteDiskIOException e) {
                // TODO Auto-generated catch block
                Log.e(LOG_TAG, "Error!! - onDisconnect() Disk Full!");
                e.printStackTrace();
            }
        }
    }

    /**
     * clear these flags
     */
    public void reset() {
        mVtCall = 0;
        mPhoneType = PhoneConstants.PHONE_TYPE_NONE;
        mIsComing = false;
        mIsCdmaCallWaitingReject = false;
    }


    /**
     *to query whether need to show call time, if all connections in a conference call is hangup
     *in the same time, we show the lastest connection's call time
     * @return true if need show call time
     */
    private boolean needToShowCallTime(Connection c, long duration) {
        if (0 != duration / 1000) {
            if (!c.getCall().isMultiparty()) {
                log("needToShowCallTime(), not conference call, show call time...");
                return true;
            } else {
                long minDuration = duration;
                List<Connection> connections = c.getCall().getConnections();
                for (Connection conn : connections) {
                    if (conn.getState() == Call.State.ACTIVE) {
                        log("needToShowCallTime(), still have active connection!");
                        return false;
                    }
                    if (conn.getDurationMillis() < minDuration) {
                        // get the latest connection in the conference call
                        minDuration = conn.getDurationMillis();
                    }
                }
                if (duration == minDuration) {
                    log("needToShowCallTime(), current is the lastest connection in Conference call, show call time...");
                    return true;
                }
            }
        }
        return false;
    }
}
