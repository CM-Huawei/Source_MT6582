package com.mediatek.incallui.vt;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.widget.Toast;

import com.android.incallui.CallCommandClient;
import com.android.incallui.CallList;
import com.android.incallui.InCallApp;
import com.android.incallui.Log;
import com.android.incallui.R;
import com.android.services.telephony.common.Call;
import com.android.services.telephony.common.Call.DisconnectCause;
import com.mediatek.incallui.InCallUtils;
import com.mediatek.incallui.wrapper.FeatureOptionWrapper;

import java.util.Collection;

public final class VTUtils {

    private static final String TAG = VTUtils.class.getSimpleName();
    private static final int INVALID_RES_ID = -1;

    /**
     * The function to judge whether the call is video call
     * @param call Call object
     * @return true yes false no
     */
    public static boolean isVTCall(Call call) {
        boolean isVT = false;
        if (call != null) {
            isVT = call.isVideoCall();
        }
        Log.d(TAG, "isVTCall()... mCall / isVT: " + call + " / " + isVT);
        return isVT; 
    }

    /**
     * The function to get current VT Call: incoming > outgoing > active > onHold > disconnecting > disconnect.
     * @return the current VT call, may be null
     */
    public static Call getVTCall() {
        Call vtCall = null;
        if (!FeatureOptionWrapper.isSupportVT() || !CallList.getInstance().existsLiveCall()) {
            vtCall = null;
        } else {
            Call ringingCall = CallList.getInstance().getIncomingCall();
            Call outgoingCall = CallList.getInstance().getOutgoingCall();
            Call activeCall = CallList.getInstance().getActiveCall();
            Call holdCall = CallList.getInstance().getBackgroundCall();
            Call disconnectingCall = CallList.getInstance().getDisconnectingCall();
            Call disconnectedCall = CallList.getInstance().getDisconnectedCall();

            if (ringingCall != null && ringingCall.isVideoCall()) {
                vtCall = ringingCall;
            } else if (outgoingCall != null && outgoingCall.isVideoCall()) {
                vtCall = outgoingCall;
            } else if (activeCall != null && activeCall.isVideoCall()) {
                vtCall = activeCall;
            } else if (holdCall != null && holdCall.isVideoCall()) {
                vtCall = holdCall;
            } else if (disconnectingCall != null && disconnectingCall.isVideoCall()) {
                vtCall = disconnectingCall;
            } else if (disconnectedCall != null && disconnectedCall.isVideoCall()) {
                vtCall = disconnectedCall;
            }
        }

        Log.d(TAG, "getVTCall()... vtCall: " + vtCall);
        return vtCall;
    }

    /**
     * The function to judge whether has VT outgoing call based on current CallList.
     * @return
     */
    public static boolean isVTOutgoing() {
        boolean isVTOutgoing = false;
        if (!FeatureOptionWrapper.isSupportVT() || !CallList.getInstance().existsLiveCall()) {
            isVTOutgoing = false;
        } else {
            Call outgoingCall = CallList.getInstance().getOutgoingCall();
            if (outgoingCall != null) {
                isVTOutgoing = outgoingCall.isVideoCall();
            }
        }

        Log.d(TAG, "isVTOutgoing()... isVTOutgoing: " + isVTOutgoing);
        return isVTOutgoing;
    }

    /**
     * The function to judge whether has ringing VT call based on current CallList.
     * @return true for yes, false for no
     */
    public static boolean isVTRinging() {
        boolean isVTRinging = false;
        if (!FeatureOptionWrapper.isSupportVT() || !CallList.getInstance().existsLiveCall()) {
            isVTRinging = false;
        } else {
            Call ringingCall = CallList.getInstance().getIncomingCall();
            if (ringingCall != null) {
                isVTRinging = ringingCall.isVideoCall();
            }
        }

        Log.d(TAG, "isVTRinging()... isVTRinging: " + isVTRinging);
        return isVTRinging;
    }

    /**
     * The function to judge whether VT is totally idle based on current CallList.
     * @return true for yes, false for no
     */
    public static boolean isVTIdle() {
        boolean isVTIdle = true;
        if (!FeatureOptionWrapper.isSupportVT() || !CallList.getInstance().existsLiveCall() ) {
            isVTIdle = true;
        } else {
            Call ringingCall = CallList.getInstance().getIncomingCall();
            Call activeCall = CallList.getInstance().getActiveCall();
            Call holdCall = CallList.getInstance().getBackgroundCall();
            Call outgoingCall = CallList.getInstance().getOutgoingCall();
            Call disconnectingCall = CallList.getInstance().getDisconnectingCall();
            Call disconnectedCall = CallList.getInstance().getDisconnectedCall();

            if (ringingCall != null && ringingCall.isVideoCall()) {
                isVTIdle = false;
            }
            if (isVTIdle && activeCall != null && activeCall.isVideoCall()) {
                isVTIdle = false;
            }
            if (isVTIdle && outgoingCall != null && outgoingCall.isVideoCall()) {
                isVTIdle = false;
            }
            if (isVTIdle && disconnectingCall != null && disconnectingCall.isVideoCall()) {
                isVTIdle = false;
            }
            if (isVTIdle && disconnectedCall != null && disconnectedCall.isVideoCall()) {
                isVTIdle = false;
            }
        }

        Log.d(TAG, "isVTIdle()... isVTIdle: " + isVTIdle);
        return isVTIdle;
    }

    /**
     * The function to judge whether has active VT call based on current CallList.
     * Note: here we only judge the active call.
     * @return true for yes, false for no
     */
    public static boolean isVTActive() {
        boolean isVTActive = false;
        if (!FeatureOptionWrapper.isSupportVT() || !CallList.getInstance().existsLiveCall()) {
            isVTActive = false;
        } else {
            Call activeCall = CallList.getInstance().getActiveCall();
            if (activeCall != null) {
                isVTActive = activeCall.isVideoCall();
            }
        }

        Log.d(TAG, "isVTActive()... isVTActive: " + isVTActive);
        return isVTActive;
    }

    public static void makeVoiceReCall(Context context, final String number, final int slot) {
        Log.d(TAG, "makeVoiceReCall(), number is " + number + " slot is " + slot);

        /// For ALPS01315489 @{
        // here we sure will display voice call UI, so clear VT disconnect call to make sure VT call UI won't show again.
        CallList.getInstance().clearDisconnectStateForVT();
        /// @}

        final Intent intent = new Intent(Intent.ACTION_CALL, Uri.fromParts("tel", number, null));
        intent.putExtra(Constants.EXTRA_SLOT_ID, slot);
        intent.putExtra(Constants.EXTRA_INTERNATIONAL_DIAL_OPTION, Constants.INTERNATIONAL_DIAL_OPTION_IGNORE);
        intent.putExtra(Constants.EXTRA_VT_MAKE_VOICE_RECALL, true);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        context.startActivity(intent);
    }

    public static void handleAutoDropBack(Context context, Call call) {
        Log.d(TAG, "handleAutoDropBack, check whether drop back~~");
        if (call == null) {
            return;
        }
        int resId = VTUtils.getResIdForVTReCallDialog(call.getDisconnectCause());
        if (resId != INVALID_RES_ID && context != null) {
            Log.d(TAG, "make auto drop back voice recall, resId = " + resId);
            Toast.makeText(context, context.getResources().getString(R.string.vt_voice_connecting),
                    Toast.LENGTH_LONG).show();
            makeVoiceReCall(context, call.getNumber(), call.getSlotId());
        }
    }

    public static int getResIdForVTErrorDialog(Call.DisconnectCause cause) {
        int resId = INVALID_RES_ID;

        if (cause == Call.DisconnectCause.UNOBTAINABLE_NUMBER
                || cause == Call.DisconnectCause.INVALID_NUMBER_FORMAT
                || cause == Call.DisconnectCause.INVALID_NUMBER) {
            resId = R.string.callFailed_unobtainable_number;
        } else if (cause == Call.DisconnectCause.CM_MM_RR_CONNECTION_RELEASE) {
            resId = R.string.vt_network_unreachable;
        } else if (cause == Call.DisconnectCause.NO_ROUTE_TO_DESTINATION
                || cause == Call.DisconnectCause.CALL_REJECTED
                || cause == Call.DisconnectCause.FACILITY_REJECTED
                || cause == Call.DisconnectCause.NORMAL_UNSPECIFIED
                || cause == Call.DisconnectCause.CONGESTION
                || cause == Call.DisconnectCause.SERVICE_NOT_AVAILABLE
                || cause == Call.DisconnectCause.BEARER_NOT_IMPLEMENT
                || cause == Call.DisconnectCause.FACILITY_NOT_IMPLEMENT
                || cause == Call.DisconnectCause.RESTRICTED_BEARER_AVAILABLE
                || cause == Call.DisconnectCause.OPTION_NOT_AVAILABLE
                || cause == Call.DisconnectCause.ERROR_UNSPECIFIED) {
            resId = R.string.vt_iot_error_01;
        } else if (cause == Call.DisconnectCause.BUSY) {
            resId = R.string.vt_iot_error_02;
        } else if (cause == Call.DisconnectCause.NO_USER_RESPONDING
                || cause == Call.DisconnectCause.USER_ALERTING_NO_ANSWER) {
            resId = R.string.vt_iot_error_03;
        } else if (cause == Call.DisconnectCause.SWITCHING_CONGESTION) {
            resId = R.string.vt_iot_error_04;
        }
        return resId;
    }

    public static int getResIdForVTReCallDialog(Call.DisconnectCause cause) {
        int resId = INVALID_RES_ID;

        if (cause == Call.DisconnectCause.INCOMPATIBLE_DESTINATION) {
            resId = R.string.callFailed_dsac_vt_incompatible_destination;
        } else if (cause == Call.DisconnectCause.RESOURCE_UNAVAILABLE) {
            resId = R.string.callFailed_dsac_vt_resource_unavailable;
        } else if (cause == Call.DisconnectCause.BEARER_NOT_AUTHORIZED) {
            resId = R.string.callFailed_dsac_vt_bear_not_authorized;
        } else if (cause == Call.DisconnectCause.BEARER_NOT_AVAIL) {
            resId = R.string.callFailed_dsac_vt_bearer_not_avail;
        } else if (cause == Call.DisconnectCause.NO_CIRCUIT_AVAIL) {
            resId = R.string.callFailed_dsac_vt_bearer_not_avail;
        }

        return resId;
    }

    public static boolean existNonVTCall() {
        Collection<Call> callList = CallList.getInstance().getCallMap().values();
        if (callList != null) {
            for (Call call : callList) {
                if (!call.isVideoCall()) {
                    Log.d(TAG, "[existNonVTCall]non-VT call exists");
                    return true;
                }
            }
        }
        return false;
    }

    public static void setVTVisible(final boolean bIsVisible) {
        Log.d(TAG, "setVTVisible()... bIsVisible: " + bIsVisible);
        if (bIsVisible) {
            if (VTInCallScreenFlags.getInstance().mVTSurfaceChangedH && VTInCallScreenFlags.getInstance().mVTSurfaceChangedL) {
                Log.d(TAG, "setVTVisible(true)...");
                CallCommandClient.getInstance().setVTVisible(true);
            }
        } else {
            CallCommandClient.getInstance().setVTVisible(false);
        }
    }

}
