package com.mediatek.phone;

import android.util.Log;
import android.os.SystemProperties;
import com.android.internal.telephony.Call;
import com.android.internal.telephony.CallManager;
import com.android.internal.telephony.Connection;
import com.android.internal.telephony.cdma.CdmaConnection;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.cdma.CDMAPhone;
import com.android.internal.telephony.gsm.GSMPhone;
import com.android.internal.telephony.sip.SipPhone;
import com.android.phone.PhoneGlobals;
import com.android.internal.telephony.PhoneConstants;
import com.android.phone.PhoneUtils;
import com.mediatek.phone.PhoneFeatureConstants.FeatureOption;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public final class DualTalkUtils {
    private static final String LOG_TAG = "DualTalkUtils";
    public static final String EVDO_DT_SUPPORT = "EVDO_DT_SUPPORT";
    
    private CallManager mCM;
    private static final boolean DBG = true;
    
    private static boolean sIsSupportDualTalk;
    private static boolean sIsEvdoDTSupport;
    
    private final ArrayList<Phone> mActivePhoneList;
    
    private static final DualTalkUtils INSTANCE = new DualTalkUtils();
    
    private static void log(String msg) {
        Log.d(LOG_TAG, msg);
    }
    
    private DualTalkUtils() {
        mActivePhoneList = new ArrayList<Phone>();
        mCM = PhoneGlobals.getInstance().mCM;
    }
    
    public static DualTalkUtils getInstance() {
        return INSTANCE;
    }
    
    /**
     * There are multiple phones in non-idle state
     * @return
     */
    public boolean isMultiplePhoneActive() {
        if (!sIsSupportDualTalk) {
            if (DBG) {
                log("don't support dualtalk!");
            }
            return false;
        }
        
        if (mCM.getState() == PhoneConstants.State.IDLE) {
            if (DBG) {
                log("CallManager says in idle state!");
            }
            return false;
        }
        
        List<Phone> phoneList = mCM.getAllPhones();
        int count = 0;
        //Maybe need to check the call status??
        for (Phone phone : phoneList) {
            if (phone.getState() != PhoneConstants.State.IDLE) {
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
     * Get the first ringing call
     * @return the call is ringing for foreground phone
     */
    public Call getFirstActiveRingingCall() {
       Call call = null;
       if (!mActivePhoneList.isEmpty()) {
           call = mActivePhoneList.get(0).getRingingCall();
       }
       
       if (call == null || !call.getState().isRinging()) {
           call = mCM.getFirstActiveRingingCall();
       }
       
       return call;
    }
    
    /**
     * Get the second active phone's foreground call
     * Consider the case:
     * the first phone is ringing and the second phone has active call
     * @return the call in ACTIVE state of second phone
     */
    public Call getSecondActiveFgCall() {
        Call call = null;
        if (mActivePhoneList.size() > 1) {
            call = mActivePhoneList.get(1).getForegroundCall();
        }
        
        return call;
    }
    
    /**
     * Get the second ringing call
     * when multiple ringing call exist, user can switch the two incoming call
     * @return the background ringing call
     */
    public Call getSecondActiveRingCall() {
        Call call = null;
        if (mActivePhoneList.size() > 1) {
            call = mActivePhoneList.get(1).getRingingCall();
        }
        
        return call;
    }
    
    /**
     * Get the foreground call of the first phone
     * @return the foreground call of the phone in the first position of Active phone list
     */
    public Call getActiveFgCall() {
        Call call = null;
        if (isCdmaAndGsmActive()) {
            Phone cdmaPhone = getActiveCdmaPhone();
            Phone gsmPhone = getActiveGsmPhone();
            Call cdmaFg = cdmaPhone.getForegroundCall();
            Call gsmFg = gsmPhone.getForegroundCall();
            //For C+G platform, we always think the GSM has the exactly call state:
            if (gsmFg.getState().isAlive()) {
                if (cdmaFg.getState() == Call.State.DIALING
                        && gsmFg.getState() == Call.State.ACTIVE) {
                    call = cdmaFg;
                } else {
                    call = gsmFg;
                }
            } else {
                call = cdmaFg;
            }
        } else if (!mActivePhoneList.isEmpty()) {
            if (mActivePhoneList.size() >= 2) {
                if (mActivePhoneList.get(0).getRingingCall().isIdle()
                        && mActivePhoneList.get(0).getForegroundCall().isIdle()
                        && !mActivePhoneList.get(0).getBackgroundCall().isIdle()
                        && mActivePhoneList.get(1).getForegroundCall().getState() == Call.State.ACTIVE) {
                    //ALPS00454660, this is an rare case, but it happens when user reject
                    //the incoming call and tap the menu quickly, at this point, the mActivePhoneList maybe not
                    //update.
                    call = mActivePhoneList.get(1).getForegroundCall();
                } else {
                    call = mActivePhoneList.get(0).getForegroundCall();
                }
            } else {
                call = mActivePhoneList.get(0).getForegroundCall();
            }
        }
        
        //If we need add some protect to return a IDLE call?
        if (call == null) {
            call = mCM.getActiveFgCall();
        }
        return call;
    }
    
    /**
     * Get the first phone's background call
     * @return
     */
    public Call getFirstActiveBgCall() {
        Call call = null;
        if (isCdmaAndGsmActive()) {
            //CDMA & GSM active
            Phone cdmaPhone = getActiveCdmaPhone();
            Phone gsmPhone = getActiveGsmPhone();
            if (gsmPhone.getForegroundCall().getState().isAlive()) {
                if (gsmPhone.getBackgroundCall().getState().isAlive()) {
                    //GSM has ACTIVE call + HOLDING call
                    //CDMA has ACTIVE call
                    call = gsmPhone.getBackgroundCall();
                } else {
                    //GSM has ACTIVE call
                    //CDMA has ACTIVE call
                    call = cdmaPhone.getForegroundCall();
                }
            } else {
                //CDMA ACTIVE call + GSM HOLDING call
                call = gsmPhone.getBackgroundCall();
            }
        } else if (!mActivePhoneList.isEmpty()) {
            if (mActivePhoneList.size() >= 2
                    && mActivePhoneList.get(0).getRingingCall().isIdle()
                    && mActivePhoneList.get(0).getForegroundCall().isIdle()
                    && !mActivePhoneList.get(0).getBackgroundCall().isIdle()
                    && mActivePhoneList.get(1).getForegroundCall().getState() == Call.State.ACTIVE
                    && mActivePhoneList.get(1).getBackgroundCall().getState() == Call.State.HOLDING) {
                call = mActivePhoneList.get(1).getBackgroundCall();
            } else {
                call = mActivePhoneList.get(0).getBackgroundCall();
            }
        }
        //Add a protect for return null background call.
        if (call == null) {
            call = mCM.getFirstActiveBgCall();
        }
        
        return call;
    }
    
    /**
     * Get the second phone's background call
     * 1. first phone have ACTIVE + HOLDING, second has HOLDING
     * 2. first phone has ACTIVE, second has HOLDING
     * 3. first phone has HOLDING, second has HOLDING
     * @return second phone's HOLDING call
     */
    public Call getSecondActiveBgCall() {
        Call call = null;
        if (mActivePhoneList.size() >= 2) {
            Phone phone = mActivePhoneList.get(1);
            if (phone.getPhoneType() == PhoneConstants.PHONE_TYPE_CDMA) {
                call = phone.getForegroundCall();
            } else {
                if (mActivePhoneList.get(0).getRingingCall().isIdle()
                    && mActivePhoneList.get(0).getForegroundCall().isIdle()
                    && !mActivePhoneList.get(0).getBackgroundCall().isIdle()
                    && mActivePhoneList.get(1).getForegroundCall().getState() == Call.State.ACTIVE
                    && mActivePhoneList.get(1).getBackgroundCall().getState() == Call.State.HOLDING) {
                    call = mActivePhoneList.get(0).getBackgroundCall();
                } else {
                    call = phone.getBackgroundCall();
                }
            }
        }
        return call;
    }
    
    
    /**
     * update the Active Phone list according to the phone state
     * 1. Phone is IDLE, remove it from the active phone list;
     * 2. If a active phone(OFFHOOK or RINGING) not in the active phone list, add it to the first position
     * 3. If the phone state change and it already in the active phone list: do further check:
     *    step 1: if one phone has ACTIVE + HOLDING calls, the phone should in first position;
     *    step 2: the phone has call in ACTIVE state, must in the first position(if the first phone
     *    only has HOLDING call but second phone has ACTIVE call)
     */
    public void updateState() {
        if (DBG) {
            log("updateState: start!");
        }
        PhoneConstants.State state = mCM.getState();
        if (state == PhoneConstants.State.IDLE) {
            if (DBG) {
                log("updateState: CM is idle! clear activePhoneList!");
            }
            mActivePhoneList.clear();
            if (sIsEvdoDTSupport) {
                Phone tempPhone = getCDMAPhone();
                if (tempPhone != null) {
                    log("hasActiveGsmPhone, disable HPF");
                    //tempPhone.requestSwitchHPF(false, null);
                }
            }
            return;
        }
        
        Iterator<Phone> it = mActivePhoneList.iterator();
        while (it.hasNext()) {
            Phone phone = it.next();
            if (phone.getState() == PhoneConstants.State.IDLE) {
                if (DBG) {
                    log("updateState: remove "  + phone.getPhoneName() + " activePhoneList!");
                }
                it.remove();
            }
        }
        
        List<Phone> list = getActivePhoneList();
        boolean addFlag = false;
        for (Phone phone : list) {
            if (mActivePhoneList.contains(phone)) {
                continue;
            } else if (mActivePhoneList.size() < 2) {
                //For dualtalk sloution, we only allow two phone active in any time
                mActivePhoneList.add(0, phone);
                addFlag = true;
            }
        }
        
        if (/*(state == PhoneConstants.State.OFFHOOK) && */(mActivePhoneList.size() > 1)) {
            switchPhoneByNeeded();
        }
        
        if (sIsEvdoDTSupport) {
            Phone tempPhone = getCDMAPhone();
            if (tempPhone != null) {
                if (hasActiveGsmPhone()) {
                    log("hasActiveGsmPhone, enable HPF");
                    //tempPhone.requestSwitchHPF(true, null);
                } else {
                    log("hasActiveGsmPhone, disable HPF");
                    //tempPhone.requestSwitchHPF(false, null);
                }
            }
        }

        if (DBG) {
            dumpActivePhone();
            log("updateState: exit!");
        }
    }
    
    private void dumpActivePhone() {
        log("DualTalkUtils dumpActivePhone ******* start *******");
        for (Phone phone : mActivePhoneList) {
            log("Phone = " + phone.getPhoneName() + " Phone = " + phone.toString());
        }
        log("DualTalkUtils dumpActivePhone ******** end  *******");
    }
    
    /**
     * Helper method, return the list that contains the phone not idle
     * @return
     */
    private List<Phone> getActivePhoneList() {
        List<Phone> list = new ArrayList<Phone>();
        List<Phone> listPhones = mCM.getAllPhones();
        for (Phone phone : listPhones) {
            if (phone.getState() != PhoneConstants.State.IDLE) {
                list.add(phone);
            }
        }
        
        return list;
    }
    
    /**
     * This is used to adjust the active phone list when incoming call reach,
     * we always think the later ring call has the higher priority, for example:
     * If the first phone is ringing and user not accept it, then the second phone
     * has ringing call, in that case, the "main" incoming call UI will be updated by 
     * the later ringcall info.
     * @param ringPhone
     */
    public void switchPhoneByNeededForRing(Phone ringPhone) {
        if (!ringPhone.getRingingCall().isRinging()) {
            return;
        }
        
        if (mActivePhoneList.isEmpty()) {
            updateState();
        }
        
        int num = mActivePhoneList.size();
        
        if (ringPhone != mActivePhoneList.get(0)) {
            if (num == 1) {
                mActivePhoneList.add(0, ringPhone);
            } else if ((num == 2) && (ringPhone == mActivePhoneList.get(1))) {
                switchCalls();
            }
        }
    }
    
    
    /**
     * Switch the phone in active phone list.
     * when two calls from different phone and both in HOLDING state, when user tap the "swap"
     * button, we "swap" the phone and this not change the phone and call state;
     * */
    public void switchCalls() {
        if (DBG) {
            log("Enter switchCalls!");
        }
        this.dumpActivePhone();
        if (mActivePhoneList == null || mActivePhoneList.size() < 2) {
            return;
        }
        
        Phone firstPhone = mActivePhoneList.get(0);
        mActivePhoneList.remove(0);
        mActivePhoneList.add(1, firstPhone);
        this.dumpActivePhone();
        if (DBG) {
            log("Exit switchCalls!");
        }
    }
    
    /**
     * This method is called from updateState:
     * this adjust the active phone list when the two phone both in OFFHOOK state:
     */
    private void switchPhoneByNeeded() {
        PhoneConstants.State state = mCM.getState();
        int size = mActivePhoneList.size();
        if (size < 2) {
            return;
        }
        if (state == PhoneConstants.State.OFFHOOK) {
            Phone phone = mActivePhoneList.get(1);
            Phone phone0 = mActivePhoneList.get(0);
            
            if (phone.getForegroundCall().getState().isAlive() 
                    && phone.getBackgroundCall().getState().isAlive()) {
                switchCalls();
            } else if ((phone0.getForegroundCall().getState() == Call.State.IDLE)
                    && (phone0.getBackgroundCall().getState() == Call.State.HOLDING)
                    && (phone.getBackgroundCall().getState() == Call.State.IDLE)
                    && (phone.getForegroundCall().getState() == Call.State.ACTIVE)) {
                //There are two calls exist and user swap the calls
                //For current design, we consider the phone has the active call as "foreground"
                switchCalls();
            }
        } else if (state == PhoneConstants.State.RINGING) {
            Phone phone = mActivePhoneList.get(1);
            Phone phone0 = mActivePhoneList.get(0);
            //ring phone always has the highest priority
            if ((phone0.getState() != PhoneConstants.State.RINGING)
                    && (phone.getState() == PhoneConstants.State.RINGING)) {
                switchCalls();
            }
        }
    }
    
    /**
     * check if the device have two ringing call
     * @return
     */
    public boolean hasMultipleRingingCall() {
        if (!sIsSupportDualTalk) {
            return false;
        }
        
        if (mActivePhoneList.size() < 2) {
            return false;
        }
        
        return (mActivePhoneList.get(0).getRingingCall().getState().isRinging()
                && mActivePhoneList.get(1).getRingingCall().getState().isRinging());
    }
    
    /**
     * Get all the active or hold calls, but except the ringing call
     * @return
     */
    public List<Call> getAllNoIdleCalls() {
        List<Call> callList = new ArrayList<Call>();
        for (Phone phone : mActivePhoneList) {
            Call fgCall = phone.getForegroundCall();
            if (fgCall.getState().isAlive()) {
                callList.add(fgCall);
            }
            Call bgCall = phone.getBackgroundCall();
            if (bgCall.getState().isAlive()) {
                callList.add(bgCall);
            }
            
        }
        return callList;
    }
    
    /**
     * At some time, we need get the all hold call(especially when cdma and gsm all
     * have non-idle call exist).
     * @return
     */
    public List<Call> getAllHoldCalls() {
        List<Call> holdCallList = new ArrayList<Call>();
        boolean bSkipFirstActive = true;
        for (Phone phone : mActivePhoneList) {
            Call fgCall = phone.getForegroundCall();
            if (fgCall.getState().isAlive()) {
                if (bSkipFirstActive) {
                    bSkipFirstActive = false;
                } else {
                    holdCallList.add(fgCall);
                }
            }
            Call bgCall = phone.getBackgroundCall();
            if (bgCall.getState().isAlive()) {
                holdCallList.add(bgCall);
            }
            
        }
        return holdCallList;
    }
    
    /**
     * check if in dual talk answer case:
     * when there are two or more than two calls exist, if user wants to answer the 
     * new incoming call, some special hander is needed.
     * This API is only called when answer an incoming call, so this means must has 
     * incoming call exist 
     * @return
     */
    public boolean isDualTalkAnswerCase() {
        List<Call> list = getAllNoIdleCalls();
        int callCount = list.size();
        
        //Consider the CDMA special case:
        //although have multiple calls exist(call waiting or three-way call), the cdma phone 
        //always has a foreground call
        if (callCount == 1) {
            Call call = list.get(0);
            if (call.getPhone().getPhoneType() == PhoneConstants.PHONE_TYPE_CDMA) {
                if (call.getConnections().size() >= 2) {
                    callCount++;
                }
            }
        }
        
        return callCount >= 2;
    }
    
    /**
     * Check if there are max calls exist:
     * first phone: ACTIVE + HOLDING
     * second phone: HOLDING
     * @return
     */
    public boolean isDualTalkMultipleHoldCase() {
        List<Call> list = getAllNoIdleCalls();
        return list.size() > 2;
    }
    
    
    /**
     * Check if only two hold calls exist.
     * @return
     */
    public boolean hasDualHoldCallsOnly() {
        if (mActivePhoneList.size() < 2) {
            return false;
        }
        
        if (isCdmaAndGsmActive()) {
            //This case didn't happen for C+G.
            return false;
        }
        
        if (mActivePhoneList.get(0).getForegroundCall().getState().isAlive()) {
            return false;
        }
        
        return (getFirstActiveBgCall().getState().isAlive()
                && getSecondActiveBgCall().getState().isAlive());
    }
    
    /**
     * Check if ring when there is one dialing(DIALING, ALERTING) calls exist.
     * According to operator's spec, in that case, if you answer the ring call, 
     * the dialing call will be disconnected.
     * @return
     */
    public boolean isRingingWhenOutgoing() {
        if (!sIsSupportDualTalk || mActivePhoneList.size() < 2) {
            return false;
        }
        
        Phone fgPhone = mActivePhoneList.get(0);
        Phone bgPhone = mActivePhoneList.get(1);
        //VIA confirmed this didn't need the real connected
        /*if (bgPhone.getPhoneType() == PhoneConstants.PHONE_TYPE_CDMA) {
            Call fgCall = bgPhone.getForegroundCall();
            boolean isDialing = false;
            if (fgCall.getState().isAlive()) {
                List connections = fgCall.getConnections();
                if (connections.size() == 1) {
                    CdmaConnection conn = (CdmaConnection) connections.get(0);
                    isDialing = !conn.isIncoming() && !conn.isRealConnected();
                }
            }
            
            return isDialing && fgPhone.getRingingCall().getState().isRinging();
        } else {*/
            return fgPhone.getRingingCall().getState().isRinging()
                   && bgPhone.getForegroundCall().getState().isDialing();
        //}
    }
    
    
    /**
     * According to spec, for dualtalk solution, must support two calls from different
     * phones both in HOLDING state, so add the hold/unhold in the menu to support hold/unhold
     * function.
     * @return
     */
    public boolean isSupportHoldAndUnhold() {
        if (!sIsSupportDualTalk || sIsEvdoDTSupport) {
            //For evdo+g, don't support two hold call in holding status.
            return false;
        }
        
        if (mActivePhoneList.size() < 2) {
            return false;
        }
        
        if (mCM.getState() != PhoneConstants.State.OFFHOOK) {
            return false;
        }
        
        return getAllNoIdleCalls().size() == 2;
    }
    
    /**
     * For current design, dualtalk solution will support only two phones in non-idle state
     * in the same time, this means if there are two phones in non-idle state, the new call
     * in other phone will be rejected.
     * @param phone
     * @return
     */
    public boolean isPhoneCallAllowed(Phone phone) {
        boolean result = true;
        if ((mActivePhoneList.size() >= 2)
                && (mActivePhoneList.get(0) != phone)
                && (mActivePhoneList.get(1) != phone)) {
            result = false;
        }
        
        return result;
    }
    
    /**
     * In some case, we can't get the phone directly, so we have to check this by slot id
     * this is ugly, if it's possible, FW should provide the API to get the phone
     * @param slot
     * @return
     */
    public boolean isPhoneCallAllowed(int slot) {
        boolean result = true;
        if (mActivePhoneList.size() >= 2) {
            boolean found = false;
            for (Phone phone : mActivePhoneList) {
                if (phone instanceof SipPhone) {
                    log("match the SipPhone, do nothing.");
                } else if ((phone instanceof GSMPhone) && (phone.getMySimId() == slot)) {
                    found = true;
                } else if (phone instanceof CDMAPhone) {
                    log("match the CDMAPhone, do nothing.");
                }
            }
            
            if (!found || found && PhoneGlobals.getInstance().isBTConnected()) {
                result = false;
            }
        }
        
        return result;
    }
    
    public boolean canAddCallForDualTalk() {
        if (!sIsSupportDualTalk) {
            return false;
        }
        
        int len = getAllNoIdleCalls().size();
        int count = mActivePhoneList.size();
        if (len == 2) {
            return count == 2;
        } else if (len == 3) {
            return false;
        }
        
        return true;
    }
    
    public boolean canSplitCallFromConference() {
        if (!sIsSupportDualTalk) {
            return false;
        }
        
        //we only consider the multiple phone active case
        if (mActivePhoneList.size() < 2) {
            return false;
        }
        Call fgCall = getActiveFgCall();
        Call bgCall = getFirstActiveBgCall();
        return (fgCall.isMultiparty() && fgCall.getState().isAlive()
                && !bgCall.getState().isAlive());
    }
    
    public static String generateDtmfparam(char c, boolean start) {
        String prefix = "SetWarningTone=";
        if (!start) {
            prefix = "StopWarningTone=";
        }
        if (c >= '0' && c <= '9') {
            return prefix + c;
        } else if (c == '*') {
            return prefix + "10";
        } else if (c == '#') {
            return prefix + "11";
        } else {
            return prefix + "null";
        }
    }
    
    public static String generateBurstDtmfparam(char c, boolean start) {
        String prefix = "SetWarningTone=";
        if (!start) {
            prefix = "StopWarningTone=";
        }
        if (c >= '0' && c <= '9') {
            int iTemp = c - '0' + 17;
            return prefix + String.valueOf(iTemp);
        } else if (c == '*') {
            return prefix + "27";
        } else if (c == '#') {
            return prefix + "28";
        } else {
            return prefix + "null";
        }
    }
    
    public boolean isAllowedIncomingCall(Call ringing) {
        boolean bResult = false;
        
        if (this.mActivePhoneList.size() < 2) {
            return true;
        }
        
        Phone ringPhone = ringing.getPhone();
        
        for (Phone phone : mActivePhoneList) {
            if (phone == ringPhone) {
                bResult = true;
                break;
            }
        }
        
        return bResult;
    }
    
    public boolean hasActiveCdmaPhone() {
        if (!sIsSupportDualTalk) {
            return false;
        }
        
        for (Phone phone : mActivePhoneList) {
            if (phone.getPhoneType() == PhoneConstants.PHONE_TYPE_CDMA) {
                return true;
            }
        }
        
        return false;
    }
    
    public boolean hasActiveGsmPhone() {
        if (!sIsSupportDualTalk) {
            return false;
        }
        
        for (Phone phone : mActivePhoneList) {
            if (phone.getPhoneType() == PhoneConstants.PHONE_TYPE_GSM) {
                return true;
            }
        }
        
        return false;
    }
    
    public boolean isCdmaAndGsmActive() {
        if (!sIsSupportDualTalk) {
            return false;
        }
        
        if (mActivePhoneList.size() < 2) {
            return false;
        }
        //There is any effective way to check this?
        for (Phone phone : mActivePhoneList) {
            if (phone.getPhoneType() == PhoneConstants.PHONE_TYPE_CDMA) {
                return true;
            }
        }
        
        return false;
    }
    
    public boolean hasActiveOrHoldBothCdmaAndGsm() {
        if (!sIsSupportDualTalk || mActivePhoneList.size() < 2) {
            return false;
        }
        
        Phone gsmPhone = getActiveGsmPhone();
        Phone cdmaPhone = getActiveCdmaPhone();
        
        if (gsmPhone != null && cdmaPhone != null) {
            return ((gsmPhone.getForegroundCall().getState().isAlive() 
                    || gsmPhone.getBackgroundCall().getState().isAlive())
                    && cdmaPhone.getForegroundCall().getState().isAlive());
        } else {
            return false;
        }
    }
    
    public Phone getActiveCdmaPhone() {
        Phone p = null;
        for (Phone phone : mActivePhoneList) {
            if (phone.getPhoneType() == PhoneConstants.PHONE_TYPE_CDMA) {
                p = phone;
                break;
            }
        }
        
        return p;
    }

    /**
     * Get the GSM type phone that in active status
     * @return the GSM phone
     */
    public Phone getActiveGsmPhone() {
        Phone p = null;
        for (Phone phone : mActivePhoneList) {
            if (phone.getPhoneType() == PhoneConstants.PHONE_TYPE_GSM 
                    || phone.getPhoneType() == PhoneConstants.PHONE_TYPE_SIP) {
                p = phone;
                break;
            }
        }
        
        return p;
    }
    
    public boolean isCDMAPhoneActive() {
        Phone p = getActiveCdmaPhone();
        return p == null ? false : true;
    }

    /**
     * Whether is CDMA call waiting case
     * @param call the call object
     * @return return true if the call is call waiting case
     */
    public boolean isCdmaCallWaiting(Call call) {
        if (!PhoneUtils.hasMultipleConnections(call)) {
            return false;
        }
        
        List<Connection> conns = call.getConnections();
        return conns.get(1).isIncoming();
    }
    
    /**
     * Get the first active phone
     * @return the phone object
     */
    public Phone getFirstPhone() {
        if (mActivePhoneList != null && !mActivePhoneList.isEmpty()) {
            return mActivePhoneList.get(0);
        }
        
        return null;
    }
    
    public static boolean isSupportDualTalk() {
        return sIsSupportDualTalk;
    }
    
    public static boolean isEvdoDTSupport() {
        return sIsEvdoDTSupport;
    }

    public static boolean getFeatureOption(String name) {
        if (EVDO_DT_SUPPORT.equals(name)) {
            return sIsEvdoDTSupport;
        }
        return false;
    }
    
    public static void init() {
        //In order to support the international sim, when system boot up,
        //must read feature option from system property, this maybe changed
        //by dynamic
        //sIsSupportDualTalk = FeatureOption.MTK_DT_SUPPORT;
        
        int iGemini = SystemProperties.getInt("mediatek.gemini", -1);
        if (iGemini == 0) {
            sIsSupportDualTalk = true;
            sIsEvdoDTSupport = true;
        } else {
            sIsSupportDualTalk = com.mediatek.common.featureoption.FeatureOption.MTK_DT_SUPPORT;
            sIsEvdoDTSupport = com.mediatek.common.featureoption.FeatureOption.EVDO_DT_SUPPORT;
        }
    }
    
    public static void switchDTFeatureOption(boolean dt) {
        sIsSupportDualTalk = dt;
        sIsEvdoDTSupport = dt;
    }
    
    private Phone getCDMAPhone() {
        List<Phone> listPhones = mCM.getAllPhones();
        for (Phone phone : listPhones) {
            if (phone.getPhoneType() == PhoneConstants.PHONE_TYPE_CDMA) {
                return phone;
            }
        }
        
        return null;
    }
    
    public List<Call> getAllActiveCalls() {
        List<Call> callList = new ArrayList<Call>();
        for (Phone phone : mActivePhoneList) {
            Call fgCall = phone.getForegroundCall();
            if (fgCall.getState().isAlive()) {
                callList.add(fgCall);
            }
        }
        return callList;
    }

    /**
     * judge whether the secondary call should be visible or not. Called in updateForegroundCallForDualTalk(...).
     * @param cm
     * @return true for visible.
     */
    public boolean isSecondaryCallVisible() {
        boolean result = false;
        if (isSupportDualTalk() && isDualTalkMultipleHoldCase()) {
            // there are two hold calls + one active call, so must display
            // secondary call.
            result = true;
        } else if (isSupportDualTalk() && hasDualHoldCallsOnly()) {
            // There are two hold call only
            Call firstHold = getFirstActiveBgCall();
            Call secondHold = getSecondActiveBgCall();
            result = firstHold.getPhone() == secondHold.getPhone();
        } else {
            Call fgCall = CallManager.getInstance().getActiveFgCall();
            Call bgCall = CallManager.getInstance().getFirstActiveBgCall();
            if (fgCall != null && (fgCall.getState() != Call.State.IDLE) && bgCall != null
                    && bgCall.getState().isAlive()) {
                result = fgCall.getPhone() == bgCall.getPhone();
            }
        }
        return result;
    }

    /**
     * judge whether the secondary hold call should be visible or not. Called in updateForegroundCallForDualTalk(...).
     * @param cm
     * @return true for visible.
     */
    public boolean isSecondHoldCallVisible() {
        boolean result = false;
        if (isSupportDualTalk() && isDualTalkMultipleHoldCase()) {
            // There are two hold calls, so must display second hold call.
            result = true;
        } else if (isSupportDualTalk() && hasDualHoldCallsOnly()) {
            // There are two hold call only
            Call firstHold = getFirstActiveBgCall();
            Call secondHold = getSecondActiveBgCall();
            result = firstHold.getPhone() != secondHold.getPhone();
        } else {
            Call fgCall = CallManager.getInstance().getActiveFgCall();
            Call bgCall = CallManager.getInstance().getFirstActiveBgCall();
            if (fgCall != null && (fgCall.getState() != Call.State.IDLE) && bgCall != null
                    && bgCall.getState().isAlive()) {
                result = fgCall.getPhone() != bgCall.getPhone();
            }
        }
        return result;
    }
}
