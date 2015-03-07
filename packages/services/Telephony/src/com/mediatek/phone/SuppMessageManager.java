
package com.mediatek.phone;

import java.util.ArrayList;
import java.util.List;

import android.app.AlertDialog;
import android.os.AsyncResult;
import android.content.Context;
import android.telephony.PhoneNumberUtils;
import android.util.Log;
import android.view.WindowManager;
import android.widget.Toast;

import com.android.internal.telephony.Call;
import com.android.internal.telephony.CallManager;
import com.android.internal.telephony.CallStateException;
import com.android.internal.telephony.CallerInfo;
import com.android.internal.telephony.Connection;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.gsm.SuppCrssNotification;
import com.android.internal.telephony.gsm.SuppServiceNotification;
import com.android.phone.CallTime;
import com.android.phone.InCallScreen;
import com.android.phone.PhoneGlobals;
import com.android.phone.PhoneUtils;
import com.android.phone.R;

public class SuppMessageManager {

    private static final String LOG_TAG = "SuppMessageManager";
    private static final boolean DBG = true;
    private DualTalkUtils mDualTalk;
    private CallManager mCM;
    private Context mContext;

    private static final int SUP_TYPE = 0x91;

    public SuppMessageManager() {
        mDualTalk = DualTalkUtils.getInstance();
        mCM = CallManager.getInstance();
        mContext = PhoneGlobals.getInstance().getApplicationContext();
    }

    /**
     * Handle a failure notification for a supplementary service (i.e.
     * conference, switch, separate, transfer, etc.).
     */
    public String onSuppServiceFailed(AsyncResult r) {
        Phone.SuppService service = (Phone.SuppService) r.result;
        if (DBG)
            log("onSuppServiceFailed: " + service);

        String errorMessage = "";
        switch (service) {
            case SWITCH:
                // Attempt to switch foreground and background/incoming calls
                // failed ("Failed to switch calls")

                // / M: For ALPS00513091 @{
                if (PhoneUtils.getPhoneSwapStatus()) {
                    PhoneUtils.setPhoneSwapStatus(false);
                }
                // / @}
                errorMessage = mContext.getResources().getString(
                        R.string.incall_error_supp_service_switch);
                // / M: for DualTalk @{
                if (DualTalkUtils.isSupportDualTalk() && mDualTalk != null) {
                    if (mDualTalk.isCdmaAndGsmActive()) {
                        log("onSuppServiceFailed: can't hold, so hangup!");
                        PhoneUtils.hangup(mCM.getActiveFgCall());
                        Toast.makeText(mContext, R.string.end_call_because_can_not_hold,
                                Toast.LENGTH_LONG).show();
                        return "";
                    }
                }
                handleSwitchFailed();
                // / @}
                break;

            case SEPARATE:
                // Attempt to separate a call from a conference call
                // failed ("Failed to separate out call")
                errorMessage = mContext.getResources().getString(
                        R.string.incall_error_supp_service_separate);
                break;

            case TRANSFER:
                // Attempt to connect foreground and background calls to
                // each other (and hanging up user's line) failed ("Call
                // transfer failed")
                errorMessage = mContext.getResources().getString(
                        R.string.incall_error_supp_service_transfer);
                break;

            case CONFERENCE:
                // Attempt to add a call to conference call failed
                // ("Conference call failed")
                errorMessage = mContext.getResources().getString(
                        R.string.incall_error_supp_service_conference);
                break;

            case REJECT:
                // Attempt to reject an incoming call failed
                // ("Call rejection failed")
                errorMessage = mContext.getResources().getString(
                        R.string.incall_error_supp_service_reject);
                break;

            case HANGUP:
                // Attempt to release a call failed
                // ("Failed to release call(s)")
                errorMessage = mContext.getResources().getString(
                        R.string.incall_error_supp_service_hangup);
                break;

            case UNKNOWN:
            default:
                // Attempt to use a service we don't recognize or support
                // ("Unsupported service" or "Selected service failed")
                errorMessage = mContext.getResources().getString(
                        R.string.incall_error_supp_service_unknown);
                break;
        }
        return errorMessage;
    }

    public void onSuppServiceNotification(AsyncResult r) {
        SuppServiceNotification notification = (SuppServiceNotification) r.result;
        if (DBG) {
            log("onSuppServiceNotification: " + notification);
        }

        String msg = null;
        // MO
        if (notification.notificationType == 0) {
            msg = getSuppServiceMOStringId(notification);
        } else if (notification.notificationType == 1) {
            // MT
            String str = "";
            msg = getSuppServiceMTStringId(notification);
            // not 0x91 should add + .
            if (notification.type == SUP_TYPE) {
                if (notification.number != null && notification.number.length() != 0) {
                    str = " +" + notification.number;
                }
            }
            msg = msg + str;
        }
        // Display Message
        // Modified for ALPS00982299.
        PhoneGlobals.getInstance().notificationMgr.postTransientNotification(msg);
    }

    private String getSuppServiceMOStringId(SuppServiceNotification notification) {
        // TODO Replace the strings.
        String retStr = "";
        switch (notification.code) {
            case SuppServiceNotification.MO_CODE_UNCONDITIONAL_CF_ACTIVE:
                retStr = mContext.getResources()
                        .getString(R.string.mo_code_unconditional_cf_active);
                break;
            case SuppServiceNotification.MO_CODE_SOME_CF_ACTIVE:
                retStr = mContext.getResources().getString(R.string.mo_code_some_cf_active);
                break;
            case SuppServiceNotification.MO_CODE_CALL_FORWARDED:
                retStr = mContext.getResources().getString(R.string.mo_code_call_forwarded);
                break;
            case SuppServiceNotification.MO_CODE_CALL_IS_WAITING:
                retStr = mContext.getResources().getString(R.string.mo_code_call_is_waiting);
                break;
            case SuppServiceNotification.MO_CODE_CUG_CALL:
                retStr = mContext.getResources().getString(R.string.mo_code_cug_call);
                retStr = retStr + " " + notification.index;
                break;
            case SuppServiceNotification.MO_CODE_OUTGOING_CALLS_BARRED:
                retStr = mContext.getResources().getString(R.string.mo_code_outgoing_calls_barred);
                break;
            case SuppServiceNotification.MO_CODE_INCOMING_CALLS_BARRED:
                retStr = mContext.getResources().getString(R.string.mo_code_incoming_calls_barred);
                break;
            case SuppServiceNotification.MO_CODE_CLIR_SUPPRESSION_REJECTED:
                retStr = mContext.getResources().getString(
                        R.string.mo_code_clir_suppression_rejected);
                break;
            case SuppServiceNotification.MO_CODE_CALL_DEFLECTED:
                retStr = mContext.getResources().getString(R.string.mo_code_call_deflected);
                break;
            default:
                // Attempt to use a service we don't recognize or support
                // ("Unsupported service" or "Selected service failed")
                retStr = mContext.getResources().getString(
                        R.string.incall_error_supp_service_unknown);
                break;
        }
        return retStr;
    }

    private String getSuppServiceMTStringId(SuppServiceNotification notification) {
        // TODO Replace the strings.
        String retStr = "";
        switch (notification.code) {
            case SuppServiceNotification.MT_CODE_FORWARDED_CALL:
                retStr = mContext.getResources().getString(R.string.mt_code_forwarded_call);
                break;
            case SuppServiceNotification.MT_CODE_CUG_CALL:
                retStr = mContext.getResources().getString(R.string.mt_code_cug_call);
                retStr = retStr + " " + notification.index;
                break;
            case SuppServiceNotification.MT_CODE_CALL_ON_HOLD:
                retStr = mContext.getResources().getString(R.string.mt_code_call_on_hold);
                break;
            case SuppServiceNotification.MT_CODE_CALL_RETRIEVED:
                retStr = mContext.getResources().getString(R.string.mt_code_call_retrieved);
                break;
            case SuppServiceNotification.MT_CODE_MULTI_PARTY_CALL:
                retStr = mContext.getResources().getString(R.string.mt_code_multi_party_call);
                break;
            case SuppServiceNotification.MT_CODE_ON_HOLD_CALL_RELEASED:
                retStr = mContext.getResources().getString(R.string.mt_code_on_hold_call_released);
                break;
            case SuppServiceNotification.MT_CODE_FORWARD_CHECK_RECEIVED:
                retStr = mContext.getResources().getString(R.string.mt_code_forward_check_received);
                break;
            case SuppServiceNotification.MT_CODE_CALL_CONNECTING_ECT:
                retStr = mContext.getResources().getString(R.string.mt_code_call_connecting_ect);
                break;
            case SuppServiceNotification.MT_CODE_CALL_CONNECTED_ECT:
                retStr = mContext.getResources().getString(R.string.mt_code_call_connected_ect);
                break;
            case SuppServiceNotification.MT_CODE_DEFLECTED_CALL:
                retStr = mContext.getResources().getString(R.string.mt_code_deflected_call);
                break;
            case SuppServiceNotification.MT_CODE_ADDITIONAL_CALL_FORWARDED:
                retStr = mContext.getResources().getString(
                        R.string.mt_code_additional_call_forwarded);
                break;
            case SuppServiceNotification.MT_CODE_FORWARDED_CF:
                retStr = mContext.getResources().getString(R.string.mt_code_forwarded_call) + "("
                        + mContext.getResources().getString(R.string.mt_code_forwarded_cf) + ")";
                break;
            case SuppServiceNotification.MT_CODE_FORWARDED_CF_UNCOND:
                retStr = mContext.getResources().getString(R.string.mt_code_forwarded_call) + "("
                        + mContext.getResources().getString(R.string.mt_code_forwarded_cf_uncond)
                        + ")";
                break;
            case SuppServiceNotification.MT_CODE_FORWARDED_CF_COND:
                retStr = mContext.getResources().getString(R.string.mt_code_forwarded_call) + "("
                        + mContext.getResources().getString(R.string.mt_code_forwarded_cf_cond)
                        + ")";
                break;
            case SuppServiceNotification.MT_CODE_FORWARDED_CF_BUSY:
                retStr = mContext.getResources().getString(R.string.mt_code_forwarded_call) + "("
                        + mContext.getResources().getString(R.string.mt_code_forwarded_cf_busy)
                        + ")";
                break;
            case SuppServiceNotification.MT_CODE_FORWARDED_CF_NO_REPLY:
                retStr = mContext.getResources().getString(R.string.mt_code_forwarded_call) + "("
                        + mContext.getResources().getString(R.string.mt_code_forwarded_cf_no_reply)
                        + ")";
                break;
            case SuppServiceNotification.MT_CODE_FORWARDED_CF_NOT_REACHABLE:
                retStr = mContext.getResources().getString(R.string.mt_code_forwarded_call)
                        + "("
                        + mContext.getResources().getString(
                                R.string.mt_code_forwarded_cf_not_reachable) + ")";
                break;
            default:
                // Attempt to use a service we don't recognize or support
                // ("Unsupported service" or "Selected service failed")
                retStr = mContext.getResources().getString(
                        R.string.incall_error_supp_service_unknown);
                break;
        }
        return retStr;
    }

    void handleSwitchFailed() {
        if (mCM.getState() == PhoneConstants.State.RINGING) {
            //This hold must be triggered by answer the ringing call
            //pop a toast and update the incallscreen.
            log("send message to update screen after 500ms");
//            requestUpdateScreenDelay(500);
            Toast.makeText(mContext, R.string.incall_error_supp_service_switch, Toast.LENGTH_LONG).show();
            return;
        }
        List<Call> activeCalls = null;
        if (DualTalkUtils.isSupportDualTalk() && mDualTalk != null) {
            activeCalls = mDualTalk.getAllActiveCalls();
        } else {
            activeCalls = new ArrayList<Call>();
            List<Phone> phoneList = mCM.getAllPhones();
            for (Phone phone : phoneList) {
                if (DBG) {
                    log("active phone = " + phone + " phone state = " + phone.getState());
                }
                if (phone.getState() == PhoneConstants.State.OFFHOOK) {
                    Call fgCall = phone.getForegroundCall();
                    if (DBG) {
                        log("active call = " + fgCall.getConnections() + " state = " + fgCall.getState());
                    }
                    if (fgCall != null && fgCall.getState().isAlive()) {
                        activeCalls.add(fgCall);
                    }
                }
            }
        }
        if (activeCalls == null || activeCalls.size() < 2) {
            log("This is only one ACTIVE call, so do nothing.");
        } else {
            long firstDuration = CallTime.getCallDuration(activeCalls.get(0));
            long secondDuration = CallTime.getCallDuration(activeCalls.get(1));
            log("More than one ACTIVE calls, hangup the latest.");
            try {
                if (firstDuration > secondDuration) {
                    activeCalls.get(1).hangup();
                } else {
                    activeCalls.get(0).hangup();
                }
            } catch (CallStateException e) {
            }
        }
    }

    public void onSuppCrssSuppServiceNotification(AsyncResult r) {
        SuppCrssNotification notification = (SuppCrssNotification) r.result;
        if (DBG) {
            log("SuppCrssNotification: " + notification);
        }
        switch (notification.code) {
        case SuppCrssNotification.CRSS_CALL_WAITING:
            break;
        case SuppCrssNotification.CRSS_CALLED_LINE_ID_PREST:
            doSuppCrssSuppServiceNotification(notification.number);
            break;
        case SuppCrssNotification.CRSS_CALLING_LINE_ID_PREST:
            //ALPS00543022 need to update the incoming call info
            doSuppCrssSuppServiceNotificationforInComing();
            break;
        case SuppCrssNotification.CRSS_CONNECTED_LINE_ID_PREST:
            doSuppCrssSuppServiceNotification(PhoneNumberUtils.stringFromStringAndTOA(
                    notification.number, notification.type));
            break;
        default:
            break;
        }
        return;
    }

    void doSuppCrssSuppServiceNotification(String number) {
        Connection conn = null;
        Call foregroundCall = null;
        if (DualTalkUtils.isSupportDualTalk() && (mDualTalk.isCdmaAndGsmActive())) {
            foregroundCall = mDualTalk.getActiveFgCall();
        } else {
            foregroundCall = mCM.getActiveFgCall();
        }
        if (foregroundCall != null) {
            int phoneType = foregroundCall.getPhone().getPhoneType();
            if (phoneType == PhoneConstants.PHONE_TYPE_CDMA) {
                conn = foregroundCall.getLatestConnection();
            } else if (phoneType == PhoneConstants.PHONE_TYPE_GSM) {
                conn = foregroundCall.getEarliestConnection();
            } else {
                throw new IllegalStateException("Unexpected phone type: "
                + phoneType);
            }
        }
        if (conn == null) {
            // TODO
            if (DBG) {
                log(" Connnection is null");
            }
            return;
        } else {
            Object o = conn.getUserData();
            if (o instanceof CallerInfo) {
                CallerInfo ci = (CallerInfo) o;
                // Update CNAP information if Phone state change occurred
                if (DBG) {
                    log("SuppCrssSuppService ci.phoneNumber:" + ci.phoneNumber);
                }
                // for ALPS01365144, if number is endwith phonenumber, it probably just add country code or area code, which is
                // useless to user, ignore it to avoid network error
                if (number != null && !number.endsWith(ci.phoneNumber) && !ci.isVoiceMailNumber()
                        && !ci.isEmergencyNumber()) {
                    ci.phoneNumber = number;
                }
            } else if (o instanceof PhoneUtils.CallerInfoToken) {
                CallerInfo ci = ((PhoneUtils.CallerInfoToken) o).currentInfo;
                if (number != null && !number.endsWith(ci.phoneNumber) && !ci.isVoiceMailNumber()
                        && !ci.isEmergencyNumber()) {
                    ci.phoneNumber = number;
                }
            }
            conn.setUserData(o);
            PhoneGlobals.getInstance().getCallModeler().updateCalls();
        }
    }

    // when CLIP message received, the incoming call info(like CNAP name, number presentaion)
    // may changed, so need to update to user
    void doSuppCrssSuppServiceNotificationforInComing() {
        if (DBG) {
            log("doSuppCrssSuppServiceNotificationforInComing...");
        }

        Connection conn = null;
        Call ringingCall = mCM.getFirstActiveRingingCall();
        if (ringingCall != null) {
            conn = ringingCall.getLatestConnection();
        }

        if (conn != null) {
            Object o = conn.getUserData();
            if (o instanceof CallerInfo) {
                CallerInfo ci = (CallerInfo) o;
                if (ci.shouldSendToVoicemail) {
                    log("should not update Screen and Notification.");
                    return;
                }
            }
            PhoneGlobals.getInstance().getCallModeler().updateCalls();
//            mApp.notificationMgr.updateInCallNotification();
        }

    }

    private void log(String msg) {
        Log.d(LOG_TAG, msg);
    }
}
