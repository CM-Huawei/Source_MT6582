/*
 * Copyright (C) 2012 The Android Open Source Project
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

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothHeadset;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.IBluetoothHeadsetPhone;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncResult;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.os.ServiceManager;
import android.os.SystemProperties;
import android.telephony.PhoneNumberUtils;
import android.telephony.ServiceState;
import android.util.Log;

import com.android.internal.telephony.Call;
import com.android.internal.telephony.Connection;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.CallManager;

import java.util.LinkedList;
import java.util.List;

///M:  DAUL TALK SUPPORT @{
import java.util.HashMap;
import com.android.internal.telephony.CallStateException;
/// }@

import com.mediatek.common.featureoption.FeatureOption;

/// M: Vedio Call support @{
import com.mediatek.phone.vt.VTCallUtils;
/// @}

import com.mediatek.phone.wrapper.CallManagerWrapper;
import com.mediatek.phone.wrapper.TelephonyManagerWrapper;

/// M: For SIM Access @{
import android.provider.Settings;
import android.provider.Telephony.SIMInfo;
//import com.android.internal.telephony.ITelephony;
import com.mediatek.common.telephony.ITelephonyEx;
/// @}

/// M: DAUL TALK SUPPORT @{
import com.mediatek.phone.DualTalkUtils;
/// @}

/**
 * Bluetooth headset manager for the Phone app.
 * @hide
 */
public class BluetoothPhoneService extends Service {
    private static final String TAG = "BluetoothPhoneService";
    private static final boolean DBG = (PhoneGlobals.DBG_LEVEL >= 1)
            && (SystemProperties.getInt("ro.debuggable", 0) == 1);
    private static final boolean VDBG = (PhoneGlobals.DBG_LEVEL >= 1);  // even more logging

    private static final String MODIFY_PHONE_STATE = android.Manifest.permission.MODIFY_PHONE_STATE;

    private BluetoothAdapter mAdapter;
    private CallManager mCM;
    /// M: GEMINI support @{
    private static final String GEMINI_SIM_NUM = "persist.gemini.sim_num";
    /// @}

    /// M: For SIM Access @{
    private final static ITelephonyEx iTel =
            ITelephonyEx.Stub.asInterface(ServiceManager.getService(Context.TELEPHONY_SERVICEEX));
    /// @}

    private BluetoothHeadset mBluetoothHeadset;

    private PowerManager mPowerManager;

    private WakeLock mStartCallWakeLock;  // held while waiting for the intent to start call

    private PhoneConstants.State mPhoneState = PhoneConstants.State.IDLE;
    CdmaPhoneCallState.PhoneCallState mCdmaThreeWayCallState =
                                            CdmaPhoneCallState.PhoneCallState.IDLE;

    private Call.State mForegroundCallState;
    private Call.State mRingingCallState;
    private CallNumber mRingNumber;
    // number of active calls
    int mNumActive;
    // number of background (held) calls
    int mNumHeld;
    ///M:  DAUL TALK SUPPORT @{    
    int mOldNumHeld;
    int mOldNumActive;
    boolean mDialingAdded;
    /// }@

    long mBgndEarliestConnectionTime = 0;

    // CDMA specific flag used in context with BT devices having display capabilities
    // to show which Caller is active. This state might not be always true as in CDMA
    // networks if a caller drops off no update is provided to the Phone.
    // This flag is just used as a toggle to provide a update to the BT device to specify
    // which caller is active.
    private boolean mCdmaIsSecondCallActive = false;
    private boolean mCdmaCallsSwapped = false;

    private long[] mClccTimestamps; // Timestamps associated with each clcc index
    private boolean[] mClccUsed;     // Is this clcc index in use

    private static final int GSM_MAX_CONNECTIONS = 6;  // Max connections allowed by GSM
    private static final int CDMA_MAX_CONNECTIONS = 2;  // Max connections allowed by CDMA

/// M: BT HFP in Dual Talk @{
    private HashMap mHashCdmaHoldTime = null;
    private boolean mIsLimitDTCall = true;
    private Phone mPrevInCallPhone = null;
    private BluetoothDualTalkUtils mBtDTUtil= null;
    private PhoneGlobals mPhoneGlobals= null;


    private HashMap<Call, Call.State> mCallStates = new HashMap<Call, Call.State>();
    
/// @}

/// M: Delay SCO Disconnect for Busy Tone
    private static final int USER_BUSY_TONE_TIME = 3000; //Delay 3 seconds for playingbusy tone
    private boolean mPlayBusyTone = false;
/// @}

    @Override
    public void onCreate() {
        super.onCreate();
        mCM = CallManager.getInstance();
        mAdapter = BluetoothAdapter.getDefaultAdapter();
        if (mAdapter == null) {
            if (VDBG) Log.d(TAG, "mAdapter null");
            return;
        }

        mPowerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
        mStartCallWakeLock = mPowerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
                                                       TAG + ":StartCall");
        mStartCallWakeLock.setReferenceCounted(false);

        mAdapter.getProfileProxy(this, mProfileListener, BluetoothProfile.HEADSET);

        mForegroundCallState = Call.State.IDLE;
        mRingingCallState = Call.State.IDLE;
        mNumActive = 0;
        mNumHeld = 0;
        ///M:  DAUL TALK SUPPORT @{    
        mOldNumActive = 0;
        mOldNumHeld = 0;
        mDialingAdded = false;
        /// }@

        mRingNumber = new CallNumber("", 0);

        ///M: DAUL TALK SUPPORT @{ 
        if (true == FeatureOption.MTK_DT_SUPPORT) {
            mHashCdmaHoldTime = new HashMap<Call, Long>();
            mBtDTUtil = new BluetoothDualTalkUtils(mCM, DualTalkUtils.getInstance());
            mPhoneGlobals = PhoneGlobals.getInstance();
        }

        if(true == FeatureOption.MTK_DT_SUPPORT)
            handlePreciseCallStateChangeDualTalk(null);
        else
            handlePreciseCallStateChange(null);
        /// }@

        registerPhoneEvents(true);

        ///M: DAUL TALK SUPPORT @{ 
        int MAX_CONNECTIONS;

        if(true == FeatureOption.MTK_DT_SUPPORT)
            MAX_CONNECTIONS = GSM_MAX_CONNECTIONS*2;
        else
            MAX_CONNECTIONS = GSM_MAX_CONNECTIONS;
        
        mClccTimestamps = new long[MAX_CONNECTIONS];
        mClccUsed = new boolean[MAX_CONNECTIONS];
        for (int i = 0; i < MAX_CONNECTIONS; i++) {
            mClccUsed[i] = false;
        }
        ///}@

    }

    @Override
    public void onStart(Intent intent, int startId) {
        if (mAdapter == null) {
            Log.w(TAG, "Stopping Bluetooth BluetoothPhoneService Service: device does not have BT");
            stopSelf();
        }
        if (VDBG) Log.d(TAG, "BluetoothPhoneService started");
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (DBG) log("Stopping Bluetooth BluetoothPhoneService Service");
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    private static final int PRECISE_CALL_STATE_CHANGED = 1;
    private static final int PHONE_CDMA_CALL_WAITING = 2;
    private static final int LIST_CURRENT_CALLS = 3;
    private static final int QUERY_PHONE_STATE = 4;
    private static final int CDMA_SWAP_SECOND_CALL_STATE = 5;
    private static final int CDMA_SET_SECOND_CALL_STATE = 6;
    private static final int PHONE_ON_DISCONNECT = 7;

    /// M: fix CDMA Swap update too fast @{
    private static final int CDMA_SWAP_SECOND_CALL_STATE_BT = 8;
    /// @}

    /// M: GEMINI support @{
    private static final int PHONE_INCOMING_RING = 9;
    /// @}
    /// M: Vedio Call support @{
    private static final int PHONE_VT_RING_INFO = 13;
    /// @}

    ///M: DAUL TALK SUPPORT @{ 
    private static final int RESTRICT_MULTITLAKS = 16;
    /// @}

    /// M: Delay SCO Disconnect for Busy Tone
    private static final int MESSAGE_DELAY_MO_CALL_END = 17;
    /// @}

    private Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            if (VDBG) Log.d(TAG, "handleMessage: " + msg.what);
            switch(msg.what) {
                case PRECISE_CALL_STATE_CHANGED:
                case PHONE_CDMA_CALL_WAITING:
                case PHONE_ON_DISCONNECT:
                    Connection connection = null;
                    if (((AsyncResult) msg.obj).result instanceof Connection) {
                        connection = (Connection) ((AsyncResult) msg.obj).result;
                    }
                    /// M: Delay SCO Disconnect for Busy Tone
                    if(msg.what == PHONE_ON_DISCONNECT){    
                        if(handlePhoneDisconnect(connection)){
                            startUserBusyTone();
                            break;
                        }
                    }
                    /// @}
                    ///M: DAUL TALK SUPPORT @{ 
                    if(true == FeatureOption.MTK_DT_SUPPORT)
                        ///always use null to prevnet PHONE_ON_DISCONNECT passing wrong connection to be used for ring number
                        handlePreciseCallStateChangeDualTalk(null);
                    else
                        handlePreciseCallStateChange(null);
                    ///}@
                    break;
                case LIST_CURRENT_CALLS:
                    handleListCurrentCalls();
                    break;
                case QUERY_PHONE_STATE:
                    handleQueryPhoneState();
                    break;
                case CDMA_SWAP_SECOND_CALL_STATE:
                    handleCdmaSwapSecondCallState();
                    break;

                /// M: fix CDMA Swap update too fast @{
                case CDMA_SWAP_SECOND_CALL_STATE_BT:
                    handleCdmaSwapSecondCallStateBT();
                    break;
                /// @}

                case CDMA_SET_SECOND_CALL_STATE:
                    handleCdmaSetSecondCallState((Boolean) msg.obj);
                    break;
                /// M: GEMINI support @{
                case PHONE_INCOMING_RING:
                /// @}
                /// M: Vedio Call support @{
                case PHONE_VT_RING_INFO:
                /// @}
                    if (VDBG) Log.d(TAG, "handleMessage: receive ring event");
                    break;

                ///M: DAUL TALK SUPPORT @{ 
                case RESTRICT_MULTITLAKS:
                    log("RESTRICT_MULTITLAKS");
                    if(true == FeatureOption.MTK_DT_SUPPORT)
                        restrictMultiTalks(false);
                    break;
                /// @}

                /// M: Delay SCO Disconnect for Busy Tone
                case MESSAGE_DELAY_MO_CALL_END:
                    handleDelayMOCallEnd();
                    break;
                /// @}
            }
        }
    };

    private void updateBtPhoneStateAfterRadioTechnologyChange() {
        if(VDBG) Log.d(TAG, "updateBtPhoneStateAfterRadioTechnologyChange...");
        registerPhoneEvents(false);
        registerPhoneEvents(true);
    }

    ///M: DAUL TALK SUPPORT  utilty functions to realize dual talk@{ 
    void dumpCallDetails(String callName, Call call){
        log("[dumpCallDetails]");
        log("dumpCallDetails: "+callName+"="+call);
        if(null != call){
            log("dumpCallDetails: "+callName+".getConnections()="+call.getConnections());
            log("dumpCallDetails: "+callName+".getPhone()="+call.getPhone());
        }
        log("[[dumpCallDetails]]");
    }

    void dumpCurrentCallStatus(){
        log("[dumpCurrentCallStatus]");
    
        List<Call> listCall = null;
        List<Connection> listConn = null;
          
        log("dumpCurrentCallStatus: dump Foreground Calls");
        listCall = mCM.getForegroundCalls();
        log("dumpCurrentCallStatus: Foreground Calls ="+listCall);
        for(Call c : listCall){
            dumpCallDetails("fg call=", c);
        }

        log("dumpCurrentCallStatus: dump Background Calls");
        listCall = mCM.getBackgroundCalls();
        log("dumpCurrentCallStatus: Background Calls ="+listCall);
        for(Call c : listCall){
            dumpCallDetails("bg call=",c);
        }
    
        log("dumpCurrentCallStatus: dump Ringing Calls");
        listCall = mCM.getRingingCalls();
        log("dumpCurrentCallStatus: Ringing Calls ="+listCall);
        for(Call c : listCall){
            dumpCallDetails("rg call=",c);
        }
    
        log("[[dumpCurrentCallStatus]]");
    }

    /// the method can be invoked by the uesr
    ///   (A, null)      --> (A, A)
    ///   (A, AH) switch --> (A, AH)  
    private long getCdmaHoldTime(Call c){
        log("[getCdmaHoldTime]");
        long cdmaHoldTime = c.getEarliestConnection().getCreateTime() ;
        if(mHashCdmaHoldTime.containsKey(c)){
            cdmaHoldTime = (Long)mHashCdmaHoldTime.get(c);
        }else{
            log("getCdmaHoldTime: no cdma hold time!");
            dumpCallDetails("c", c);
        }
        log("getCdmaHoldTime: cdmaHoldTime="+cdmaHoldTime);
        log("[[getCdmaHoldTime]]");
        return cdmaHoldTime;
    }

    private Call chooseTheEarlierHoldCall(Call c1, Call c2){
        long c1Time = c1.getEarliestConnection().getCreateTime();
        long c2Time = c2.getEarliestConnection().getCreateTime();
    
        if(PhoneConstants.PHONE_TYPE_CDMA == c1.getPhone().getPhoneType()){
            c1Time = getCdmaHoldTime(c1);
        }
    
        if(PhoneConstants.PHONE_TYPE_CDMA == c2.getPhone().getPhoneType()){
            c2Time = getCdmaHoldTime(c2);
        }
    
        return (c1Time > c2Time ? c2 : c1);
    }

    Call chooseTheLatterCall(Call c1, Call c2){
        return (c1.getEarliestConnection().getCreateTime() > c2.getEarliestConnection().getCreateTime()) ? c1 : c2;
    }

    private boolean canSwapInDiffSim(){
        log("[canSwapInDiffSim]");
        boolean bCanSwapInDiffSim = false;
    
        Phone phone = null;
    
        dumpCurrentCallStatus();
        /// traverse all the calls in Foreground: ACTIVE, OUTGOING 
        ///                                & Background; HOLD
        List<Call> listCall = null;
        listCall = mCM.getForegroundCalls();
        log("canSwapInDiffSim: getForegroundCalls "+listCall);
        for(Call c : listCall){
            log("canSwapInDiffSim: getForegroundCalls Call.State.ACTIVE="+Call.State.ACTIVE+ " c.getState()="+c.getState());
            dumpCallDetails("canSwapInDiffSim", c);
            if(Call.State.ACTIVE == c.getState()){
                if(null == phone){
                    phone = c.getPhone();
                    log("canSwapInDiffSim: TalkState IDLE --> SINGLE");
                    dumpCallDetails("getCurrentTalkState 1st call", c);
                }else if(phone != c.getPhone()){
                    log("canSwapInDiffSim: TalkState SINGLE --> MULTI");
                    dumpCallDetails("canSwapInDiffSim 2nd call", c);
                    bCanSwapInDiffSim = true;
                    break;
                }
            }
        }
    
        if(bCanSwapInDiffSim){
            log("canSwapInDiffSim: return directly");
            log("[[canSwapInDiffSim]]");
            return bCanSwapInDiffSim;
        }

        listCall = mCM.getBackgroundCalls();
        log("canSwapInDiffSim: getBackgroundCalls "+listCall);
        for(Call c : listCall){
            log("canSwapInDiffSim: getBackgroundCalls Call.State.ACTIVE="+Call.State.ACTIVE+ "c.getState()="+c.getState());
            dumpCallDetails("canSwapInDiffSim", c);
            if(Call.State.HOLDING == c.getState()){
                if(null == phone){
                    phone = c.getPhone();
                    log("canSwapInDiffSim: TalkState IDLE --> SINGLE");
                    dumpCallDetails("getCurrentTalkState 1st call", c);
                }else if(phone != c.getPhone()){
                    log("canSwapInDiffSim: TalkState SINGLE --> MULTI");
                    dumpCallDetails("canSwapInDiffSim 2nd call", c);
                    bCanSwapInDiffSim = true;
                    break;
                }
            }
        }
        log("canSwapInDiffSim: bCanSwapInDiffSim="+bCanSwapInDiffSim);
        log("[[canSwapInDiffSim]]");
        return bCanSwapInDiffSim;
    }

    private void setPrevCallState(Call callObj)
    {
        Call.State state = null;
        
        if(callObj == null)
            return;
        
        state = callObj.getState();
        
        log("setPrevCallState: Call=" + callObj);
        mCallStates.put(callObj, callObj.getState());
    }
    
    private Call.State getPrevCallState(Call callObj)
    {
        Call.State state = mCallStates.get(callObj);
        
        log("getPrevCallState: PrevState=" + state + ", Current Call=" + callObj);
        return state;
    }

    private long generateCdmaHoldTime(Call activeForegroundCall){
        log("[generateCdmaHoldTime]");
        long maxHoldTime = activeForegroundCall.getEarliestConnection().getCreateTime();
        List<Call> listCall = mCM.getBackgroundCalls();
        for(Call c : listCall){
            if(Call.State.HOLDING == c.getState()){
                if(maxHoldTime < c.getEarliestConnection().getCreateTime()){
                    maxHoldTime = c.getEarliestConnection().getCreateTime();
                    log("generateCdmaHoldTime: update hold time=" + maxHoldTime);
                }
            }
        }
        log("generateCdmaHoldTime: maxHoldTime=" + maxHoldTime);
        log("[[generateCdmaHoldTime]]");
        return maxHoldTime+1;
    }

    private void resetCdmaHoldTime(){
        log("[resetCdmaHoldTime]");
        if(null != mHashCdmaHoldTime){
            mHashCdmaHoldTime.clear();
        }else{
            log("updateCdmaHoldTime: mHashCdmaHoldTime = null");
        }
        log("[[resetCdmaHoldTime]]");
    }

    private void updateCdmaHoldTime(boolean bCanSwapInDiffSim){
        log("[updateCdmaHoldTime]");
        if(bCanSwapInDiffSim){
            if(null != mHashCdmaHoldTime){
                Call foregroundCall = mBtDTUtil.getActiveFgCall();
                if(PhoneConstants.PHONE_TYPE_GSM == foregroundCall.getPhone().getPhoneType() && foregroundCall.getState().isAlive()){
                    List<Call> listCall = mCM.getForegroundCalls();
                    for(Call c : listCall){
                        if(PhoneConstants.PHONE_TYPE_CDMA == c.getPhone().getPhoneType() && Call.State.ACTIVE == c.getState() && !mHashCdmaHoldTime.containsKey(c)){
                            /// add key and value
                            Long l = generateCdmaHoldTime(c);
                            mHashCdmaHoldTime.put(c, l);
                        }else{
                            log("updateCdmaHoldTime: c is already in hash");
                            dumpCallDetails("call", c);
                        }
                    }
                }else{
                    resetCdmaHoldTime();
                }
            }else{
                log("updateCdmaHoldTime: mHashCdmaHoldTime = null");
            }
        }else{
            resetCdmaHoldTime();
        }
        log("updateCdmaHoldTime: mHashCdmaHoldTime="+mHashCdmaHoldTime);
        log("[[updateCdmaHoldTime]]");
    }

    private boolean isMultiTalksSwapped(){
        log("[isMultiTalksSwapped]");
        boolean bCallSwapped = false;

        Call call = mBtDTUtil.getActiveFgCall();
        Phone curInCallPhone = call.getPhone();
        if(null != mPrevInCallPhone){
            bCallSwapped = (mPrevInCallPhone != curInCallPhone ? true : false);
            mPrevInCallPhone = curInCallPhone;
        }else{
            // first time
            mPrevInCallPhone = call.getPhone();
        }
    
        log("isMultiTalksSwapped: bCallSwapped="+bCallSwapped);
        log("[[isMultiTalksSwapped]]");
        return bCallSwapped;
    }

    private void resetMultiTalksSwapData(){
        log("[resetMultiTalksSwapData]");
        mPrevInCallPhone = null;
        log("[[resetMultiTalksSwapData]]");
    }   

    private boolean isHFPConnected(){
        boolean isConnected = false;

        if(mBluetoothHeadset != null){
            List<BluetoothDevice> deviceList = mBluetoothHeadset.getConnectedDevices();
            if(!deviceList.isEmpty()){
                isConnected = true;
            }
        }

        return isConnected;
    }

    ///    @return
    ///    true  --> use the relaxing restrictions
    ///    false --> use the strict restrictions
    private boolean isRelaxMultiTalksRestrict(){
        return !mIsLimitDTCall;
    }

    private boolean executeIIRestrictIfNeed(boolean bIsConnected, Call ringingCall){
        log("[executeIIRestrictIfNeed]");
    
        boolean bExecuted = false;
    
        if(isRelaxMultiTalksRestrict()){
            log("executeIIRestrictIfNeed: return directly due to isRelaxMultiTalksRestrict() == true");
            log("[[executeIIRestrictIfNeed]]");
            return bExecuted;
        }
    
        /// Check (I,I) conflict.
        List<Call> ringingCalls = mCM.getRingingCalls();
        for(Call rCall : ringingCalls){
            if(rCall != ringingCall && rCall.isRinging()){
                bExecuted = true;
                log("executeIIRestrictIfNeed: (I,I) conflict occurs");
                dumpCallDetails("executeIIRestrictIfNeed 1st rCall", ringingCall);
                dumpCallDetails("executeIIRestrictIfNeed 2nd rCall", rCall);
                if(null != mBtDTUtil){
                    Call hangupCall = null;
                    if(!bIsConnected){
                        hangupCall = mBtDTUtil.getSecondActiveRingCall();
                        log("executeIIRestrictIfNeed: hangup call(background i)="+hangupCall);
                    }else{
                        hangupCall = chooseTheLatterCall(ringingCall, rCall);
                        log("executeIIRestrictIfNeed: hangup call(latter i)="+hangupCall);
                    }
    
                    dumpCallDetails("executeIIRestrictIfNeed hangupCall", hangupCall);
                    try{
                        hangupCall.hangup(Connection.DisconnectCause.INCOMING_MISSED);
                    }catch(CallStateException e){
                        log("executeIIRestrictIfNeed: exception occurs e="+e.toString());
                    }

                    ringingCall = (ringingCall == hangupCall) ? rCall : ringingCall;

                    mRingNumber = getCallNumber(null, ringingCall);
                    
                }else{
                    log("executeIIRestrictIfNeed: fail to hangup background incoming call due to mBtDTUtil = null!");
                }
            }
        }
    
        log("executeIIRestrictIfNeed:bExecuted="+bExecuted);
        log("[[executeIIRestrictIfNeed]]");
        return bExecuted;
    }
    
    private boolean executeIORestrictIfNeed(Call ringingCall){
        log("[executeIORestrictIfNeed]");
    
        boolean bExecuted = false;
    
        if(isRelaxMultiTalksRestrict()){
            log("executeIORestrictIfNeed: return directly due to isRelaxMultiTalksRestrict() == true");
            log("[[executeIORestrictIfNeed]]");
            return bExecuted;
        }

        List<Call> foregroundCalls = mCM.getForegroundCalls();
        for(Call call : foregroundCalls){
            if(call.getState().isDialing()){
                log("executeIORestrictIfNeed: (I,O) (O,I) conflict occurs");
                dumpCallDetails("executeIORestrictIfNeed iCall", ringingCall);
                dumpCallDetails("executeIORestrictIfNeed oCall", call);
                try{
                    call.getPhone().hangupActiveCall();
                    dumpCallDetails("executeIORestrictIfNeed hangupCall(Success)",call);
                }catch(CallStateException e){
                    log("executeIORestrictIfNeed: exception occurs e="+e.toString());
                    dumpCallDetails("executeIORestrictIfNeed hangupCall(Fail)",call);
                }
                bExecuted = true;
                break;
            }
        }
    
        log("executeIORestrictIfNeed: bExecuted="+bExecuted);
        log("[[executeIORestrictIfNeed]]");
        return bExecuted;
    }
    
    private boolean executeHHRestrictIfNeed(boolean bIsConnected){
        log("[executeHHRestrictIfNeed]");
    
        boolean bExecuted = false;
        Call activeFgCall = mBtDTUtil.getActiveFgCall();
        Phone inCallPhone = null;
        List<Call> listHoldCall = new LinkedList<Call>();
    
        /// MultiTalks (C+G): if GSM has an active call,
        ///                   GSM will be the master.
        if(Call.State.ACTIVE == activeFgCall.getState() || activeFgCall.getState().isDialing()){
            inCallPhone = activeFgCall.getPhone();
            dumpCallDetails("executeHHRestrictIfNeed active call", activeFgCall);
        }
    
        log("executeHHRestrictIfNeed traverse foreground call");
        List<Call> listCall = mCM.getForegroundCalls();
        if(null != inCallPhone){
            for(Call c : listCall){
                if(c.getState() == Call.State.ACTIVE  && c.getPhone() != inCallPhone){
                    listHoldCall.add(c);
                    dumpCallDetails("executeHHRestrictIfNeed add hold call", c);
                }   
            }
        }
    
        log("executeHHRestrictIfNeed traverse background call");
        listCall = mCM.getBackgroundCalls();
        for(Call c : listCall){
            if(c.getState() == Call.State.HOLDING){
                listHoldCall.add(c);
                dumpCallDetails("executeHHRestrictIfNeed add hold call", c);
            }
        }
    
        Call firstHoldCall = null, secondHoldCall = null;
        for(Call c : listHoldCall){
            boolean bSkip = true;
    
            if(null != firstHoldCall|| null != secondHoldCall){
                bSkip = false;
            }
    
            if(null == firstHoldCall){
                firstHoldCall = c;
                if(bSkip)
                    continue;
            }
    
            if(null == secondHoldCall){
                secondHoldCall = c;
                if(bSkip)
                    continue;
            }
    
            /// use special method to determine the earlier hold call
    
            //only for the first time bt connected, we need to handle (H,H)
            //otherwise, phone shall not let this happen unless it is a temp status which we shall skip the status update. EX: (A,H)->(H,H)->(H,A)
            if(!bIsConnected)
            {      
                Call hangupCall = chooseTheEarlierHoldCall(firstHoldCall, secondHoldCall);
                if(PhoneConstants.PHONE_TYPE_CDMA != hangupCall.getPhone().getPhoneType()){
                    log("executeHHRestrictIfNeed: PhoneUtils.hangupHoldingCall(hangupCall)[!CDMA Call]");
                    mBtDTUtil.hangupHoldingCall(hangupCall);
                }else{
                    log("executeHHRestrictIfNeed: hangupCall.hangup()[CDMA Call]");
                    try{
                        hangupCall.hangup();
                    }catch(CallStateException e){
                        log("executeHHRestrictIfNeed: exception occurs e="+e.toString());
                    }
                }
    
                if(hangupCall == firstHoldCall)
                    firstHoldCall = null;
                else
                    secondHoldCall = null;
            }
            else
            {
                log("executeHHRestrictIfNeed: temp (H,H) occurred");
            }
            bExecuted = true;       
        }
    
        log("executeHHRestrictIfNeed: bExecuted="+bExecuted);
        log("[[executeHHRestrictIfNeed]]");
        return bExecuted;
    }


    ///    @param bIsConnected: true  --> fall through all restrict rules
    ///                                       false --> return immediately when executing any restrict rule
    ///
    ///    @return
    ///    true  --> a restrict rule is executed
    ///    false --> no rule is executed
    private boolean restrictMultiTalks(boolean bIsConnected){
        log("[restrictMultiTalks]");
        boolean bHasRestrictOccurred = false;

        Call ringingCall = mBtDTUtil.getFirstActiveRingingCall();
        dumpCallDetails("restrictMultiTalks: ringingCall=", ringingCall);
        if(ringingCall.isRinging()){
            /// Check (I,O) conflict.
            ///       (O,I)
            if(executeIORestrictIfNeed(ringingCall))
                bHasRestrictOccurred = true;
            /// Check (I,I) conflict.
            if(executeIIRestrictIfNeed(bIsConnected, ringingCall))
                 bHasRestrictOccurred = true;
        }
    
        /// Check (H,H) conflict
        if(executeHHRestrictIfNeed(bIsConnected))
            bHasRestrictOccurred = true;
    
        log("restrictMultiTalks:bHasRestrictOccurred="+bHasRestrictOccurred);
        log("[[restrictMultiTalks]]");
        return bHasRestrictOccurred;
    }


    private boolean checkUnreasonableStates(){
        log("[checkUnreasonableStates]");

        List<Call> fgCalls = mCM.getForegroundCalls();

        Call  active_call = null;

        if( true != FeatureOption.EVDO_DT_SUPPORT ){
            for(Call c1 : fgCalls){
                if(c1.getState() == Call.State.ACTIVE || c1.getState() == Call.State.DIALING || c1.getState() == Call.State.ALERTING ){
                    for(Call c2 : fgCalls){
                        if(c2 != c1){
                            if(c2.getState() == Call.State.ACTIVE || c2.getState() == Call.State.DIALING || c2.getState() == Call.State.ALERTING ){
                                log("checkUnreasonableStates : two forground calls exist at the same time");
                                return true;
                            }
                        }
                    }
                }
            }
        }

        log("[[checkUnreasonableStates]]");
        return false;
    }

/// M: Delay SCO Disconnect for Busy Tone
     private void startUserBusyTone() {
         log("startUserBusyTone");
         mPlayBusyTone = true;
     }     

     private void stopUserBusyTone(){
         log("stopUserBusyTone");
         mPlayBusyTone = false;
         updatePhoneStateChanged(0, 0, CALL_STATE_IDLE, null, 0);
     }

     private boolean processDelayMOCallEnd(){ 
        log("[processDelayMOCallEnd]"); 
    
        PhoneConstants.State newState = mCM.getState(); 
    
       if(newState == PhoneConstants.State.IDLE){ ///No call at all
            if(mPlayBusyTone == true){
               sendDelayMOCallEndMsg();
               return true;
            }            
       }else{ /// There is still active/hold/ringing call
            if( mHandler.hasMessages(MESSAGE_DELAY_MO_CALL_END) ){
                log("handlePreciseCallStateChange: MESSAGE_DELAY_MO_CALL_END is sent while there is new call"); 
                mHandler.removeMessages(MESSAGE_DELAY_MO_CALL_END); 
                stopUserBusyTone();
            }else{
               mPlayBusyTone = false;
            }
        } 
    
        log("[[processDelayMOCallEnd]]"); 
        return false; //Continue
    }
/// @}

    private class CallInfo
    {
        //private int mNumActive = 0;
        //private int mOldNumActive = 0;
        //private int mNumHeld = 0;
        //private int mOldNumHeld = 0;
        private Call mForegroundCall = null;
        private Call.State mBackgroundCallState = Call.State.IDLE;
        private Call.State mOldForegroundCallState = Call.State.IDLE;
        private Call.State mOldRingingCallState = Call.State.IDLE;
        private CallNumber mOldRingNumber = new CallNumber("", 0);
        private List<Call> mFgCalls = null;        

    }

/// M: Delay SCO Disconnect for Busy Tone
    //Handler for PHONE_DISCONNECT message        
    private synchronized boolean handlePhoneDisconnect(Connection c){
        log("handlePhoneDisconnect : cause="+c.getDisconnectCause());
        ///Check if the disconnection is caused by user busy            
        if(isHFPConnected() && c.getDisconnectCause() == Connection.DisconnectCause.BUSY) {
            /// No active and held calls and outgoing call exist */
            int callState = convertCallState(mRingingCallState, mForegroundCallState);
            log("handlePhoneDisconnect : mNumActive:"+mNumActive+", mNumHeld:"+mNumHeld+", callState:"+callState);
            if((mNumActive + mNumHeld) == 0 && ( callState == CALL_STATE_DIALING || callState == CALL_STATE_ALERTING)){
                if(mBluetoothHeadset != null && mBluetoothHeadset.isAudioOn()){
                    return true;
                }
           }
        }
        return false;
    }

    //Handler for MESSAGE_DELAY_MO_CALL_END message
    private synchronized void handleDelayMOCallEnd(){
        log("handleDelayMOCallEnd");
        stopUserBusyTone();
    }
    
    private void sendDelayMOCallEndMsg(){
        Message msg = mHandler.obtainMessage(MESSAGE_DELAY_MO_CALL_END);
        log("sendDelayMOCallEndMsg");
        mHandler.sendMessageDelayed(msg, USER_BUSY_TONE_TIME);
    }
/// @}

    private void updatePhoneStateChanged(int numActive, int numHeld, int callState, String number, int type){

        log("updatePhoneStateChanged: numActive:"+ numActive +" numHeld:"+ numHeld +" callState:"+ callState +" number:" + number + " type:"+ type);
        mBluetoothHeadset.phoneStateChanged(numActive, numHeld, callState, number, type);

    }

    private void handleSpecialCasesForDualTalk(CallInfo info){

        boolean callState = mNumActive > 0 || mNumHeld > 0;
        boolean prevCallState = mOldNumActive > 0 || mOldNumHeld > 0;

        //call status is still "In Call" , but this may just be a disconnected / disconnecting call followed by a connected call
        if ( true == callState  && callState == prevCallState) {
            // Get the previous state of the current active call
            Call.State prevState = getPrevCallState(info.mForegroundCall);
        
           /**Case 1.1  Modified for [ALPS00451276]
                       ** Check if the active foreground call has just been picked up
                       ** If yes, this must mean the previous Active foreground call has been disconnected
                       ** But if there was a previous / current hold call, don't fake it since we shall not send (2,0) when there was / is hold call
                       **
                       ** Deal with the "hanging up Active call & picking up Waiting call" scenario
                       ** We may miss the call disconnecting step. For example:
                       **
                       **     (Fg: Active          , Fg: Idle    |Ring: Waiting)
                       ** ->(Fg: Disconnected, Fg: Active |Ring: Idle     )
                       *
                       **     (Fg: Active            |Ring: Waiting, X) 
                       ** ->(Fg: Disconnecting |Ring: Waiting, X) 
                       ** ->(Fg: Active            |Ring: Idle     , X)
                       ** @{ */
            if (mForegroundCallState == Call.State.ACTIVE && (mNumHeld == 0 && mOldNumHeld == 0)
                    && (prevState == Call.State.IDLE || prevState == Call.State.DISCONNECTED || prevState == Call.State.DISCONNECTING)) 
            {
                // fake the previous "Not In Call" state, and set mOldNumActive to 0 for next in call state change
                mOldNumActive = 0;

                if (mBluetoothHeadset != null) {
                    
                    updatePhoneStateChanged(mOldNumActive, mOldNumHeld,
                        convertCallState(info.mOldRingingCallState, info.mOldForegroundCallState),
                        info.mOldRingNumber.mNumber, info.mOldRingNumber.mType);
                }

                log("handleSpecialCasesForDualTalk: AW --> DW --> A occurs !");
            }
        
            /** Case 1.2
                         ** (A, H) --> (DISCONNECTING, H) --> (null, A) 
                         ** Thus, originally, there will be (call, callheld) = (1,1), (1,1), (1,0)
                         ** this wil miss-lead to the Hold call is hanged up instead of the real case that the origianl active call is hanged up, 
                         ** so we change the hold state from 1 to 2 for this case
                        ** @{ */
            //seems this case can be handled by MR1 original code, don't need to take care as special case since
            //if(mForegroundCallState == Call.State.DISCONNECTING && prevState == Call.State.ACTIVE && info.mBackgroundCallState == Call.State.HOLDING)
            //{
            //    if (mBluetoothHeadset != null) {
            //        updatePhoneStateChanged(0, 1,
            //            convertCallState(info.mOldRingingCallState, info.mOldForegroundCallState),
            //            info.mOldRingNumber.mNumber, info.mOldRingNumber.mType);
            //    }
            //    
            //    log("handleSpecialCasesForDualTalk: A|H --> D|H --> X|A occurs !");
            //}
        
        
            /** Case 1.3
                         ** (I, AH) -> (A, DH)
                         ** answer I will automatically end A, but in the original transition, we will only send (3,0) to remote which will not reveal the truth of the original A is ended.
                         ** so we shall try to add an (I, H) and send (4,2) to remote 
                        ** @{ */
            {
                Call disCall = null;
                Call activeCall = null;
        
                for(Call c : info.mFgCalls){
                    if(c.getState() == Call.State.DISCONNECTED || c.getState() == Call.State.DISCONNECTING){
                        disCall = c;
                    }else if(c.getState() == Call.State.ACTIVE){
                        activeCall = c;
                    }
                }
        
                if(disCall != null && activeCall != null){
                    Call.State prevDisState = getPrevCallState(disCall);
                    log("handleSpecialCasesForDualTalk: prevDisState="+ prevDisState);
        
                    if(prevDisState == Call.State.ACTIVE )
                    {
                        Phone activePhone = activeCall.getPhone();
                        Call.State prevRingState = getPrevCallState(activePhone.getRingingCall());
                        
                        log("handleSpecialCasesForDualTalk: prevRingState="+ prevRingState);
                        
                        if(prevRingState == Call.State.INCOMING || prevRingState == Call.State.WAITING){
                          // (I,A) -> (A,D)
                            if(mOldNumHeld > 0 && mNumHeld > 0)
                            {
                                log("handleSpecialCasesForDualTalk: (I,AH) -> (A,DH) occurs");

                                if (mBluetoothHeadset != null) {
                                    updatePhoneStateChanged(0, 1,
                                        convertCallState(info.mOldRingingCallState, info.mOldForegroundCallState),
                                        info.mOldRingNumber.mNumber, info.mOldRingNumber.mType);
                                }

                                mOldNumActive = 0;
                            }
                        }                            
                    }
        
                }
            }
        }

        /**Case 2 
                    ** when restrct I,O case, (I,O) -> (I,N), we return directly without notifying remote device the outgoing call
                    ** callsetup:2 (or 3) -> callsetup: 1
                    ** we shall make it  callsetup:2 (or 3) -> callsetup: 0 -> callsetup: 1
                    **
                    ** @{ */
        {

            if(info.mOldRingingCallState == Call.State.IDLE
               && (info.mOldForegroundCallState == Call.State.DIALING || info.mOldForegroundCallState == Call.State.ALERTING)
               && (mRingingCallState == Call.State.INCOMING || mRingingCallState == Call.State.WAITING)) {

                log("handleSpecialCasesForDualTalk: (I,O) restriction occurs");
                
                if (mBluetoothHeadset != null) {
                    updatePhoneStateChanged(mOldNumActive, mOldNumHeld,
                        CALL_STATE_IDLE,
                        info.mOldRingNumber.mNumber, info.mOldRingNumber.mType);
                }

            }
        }

        /**Case 3 
                    ** when dial outgoing call when there is an active call in other modem, we might get (Active, Dialing) / (Active, Alerting) update which will be ignored until the Active call becomes Holding
                    ** if the outgoing call become Alerting before the Active Call changed to Holding, we wil miss sending the Dailling to remote side
                    ** we shall add a dailing update to remote in this case
                    **
                    ** @{ */
        {
            if(mForegroundCallState == Call.State.ALERTING && info.mOldForegroundCallState == Call.State.DIALING){
                mDialingAdded = true;
            }
            else if(mForegroundCallState == Call.State.ALERTING && info.mOldForegroundCallState != Call.State.DIALING && mDialingAdded == false){

                log("handleSpecialCasesForDualTalk: Dilaing status is missed, add one for it");

                if (mBluetoothHeadset != null) {
                    updatePhoneStateChanged(mNumActive, mNumHeld,
                        CALL_STATE_DIALING,
                        mRingNumber.mNumber, mRingNumber.mType);
                    mDialingAdded = true;
                }
            }
            // in case there are multiple ALERTING reported which added duplicated DIALING
            else if (mForegroundCallState != Call.State.ALERTING && info.mOldForegroundCallState == Call.State.ALERTING){
                mDialingAdded = false;
            }
        }


    }

    private void handlePreciseCallStateChangeDualTalk(Connection connection) {

        log("[handlePreciseCallStateChangeDualTalk]");

        // get foreground call state
        //mOldNumActive = mNumActive;
        //mOldNumHeld = mNumHeld;
        Call.State oldRingingCallState = mRingingCallState;
        Call.State oldForegroundCallState = mForegroundCallState;
        CallNumber oldRingNumber = mRingNumber;

        log("handlePreciseCallStateChangeDualTalk: NumActive: "+ mNumActive +" NumHeld: "+ mNumHeld +
            " RingingCallState: "+ mRingingCallState +" ForegroundCallState: "+ mForegroundCallState +
            " RingNumber: "+ mRingNumber.mNumber+ " RingType: "+ mRingNumber.mType);

        boolean bCanSwapInDiffSim = canSwapInDiffSim();

        int activePhoneType = PhoneConstants.PHONE_TYPE_NONE;

        if( true == FeatureOption.EVDO_DT_SUPPORT ){
            updateCdmaHoldTime(bCanSwapInDiffSim);
        }

        if(!bCanSwapInDiffSim){
            resetMultiTalksSwapData();
        }

        if(isHFPConnected()){

            boolean return_directly = false;
            
            if(checkUnreasonableStates()){
                log("handlePreciseCallStateChangeDualTalk: return directly for unreasonable states");
                return_directly = true;
                
            }else if(restrictMultiTalks(true)){
                log("handlePreciseCallStateChangeDualTalk: return directly for restriction");
                return_directly = true;

            }else if(processDelayMOCallEnd()){
                log("handlePreciseCallStateChangeDualTalk: return directly for delay MO call end");
                return_directly = true;
            }

            if(return_directly){
                log("[[handlePreciseCallStateChangeDualTalk]]");                
                return;
            }
        }else{
            mPlayBusyTone = false;
        }


        // set global variables after restriction
        mOldNumActive = mNumActive;
        mOldNumHeld = mNumHeld;

        /// foreground call: active call, outgoing call
        ///   CDMA doesn't have holding call.
        ///   If GSM and CDMA have active call at the same time, 
        ///   we will treat GSM as active and CDMA as holding call (even it's in active).
        /// background call: holding call
        /// ringing call: incoming call, waiting call
        Call foregroundCall = mBtDTUtil.getActiveFgCall();
        Call backgroundCall = mBtDTUtil.getFirstActiveBgCall();
        Call ringingCall = mBtDTUtil.getFirstActiveRingingCall();

        if (VDBG)
            Log.d(TAG, " handlePreciseCallStateChangeDualTalk: foreground: " + foregroundCall +
                " background: " + backgroundCall + " ringing: " + ringingCall);

        mForegroundCallState = foregroundCall.getState();

        if(mForegroundCallState == Call.State.ACTIVE){
            mNumActive = 1;
            activePhoneType = foregroundCall.getPhone().getPhoneType();
        }else if(mForegroundCallState != Call.State.IDLE){
            mNumActive = 0;
            activePhoneType = foregroundCall.getPhone().getPhoneType();
        }else{
            mNumActive = 0;
        }

        Log.d(TAG, " handlePreciseCallStateChangeDualTalk: mForegroundCallState: " + mForegroundCallState + " mNumActive: " + mNumActive + " activePhoneType: " + activePhoneType);

        mRingingCallState = ringingCall.getState();
        mRingNumber = getCallNumber(connection, ringingCall);

        List<Call> fgCalls = mCM.getForegroundCalls();
        List<Call> ringCalls = mCM.getRingingCalls();

        mNumHeld = getNumHeldUmts();

        // as long as there is an non-idle cmda call and  gsm call is active, then we treat cdma call as held call
        if(mBtDTUtil.isCdmaActive() && mNumHeld == 0 ){

            if(activePhoneType == PhoneConstants.PHONE_TYPE_GSM){
                if (mPhoneGlobals.cdmaPhoneCallState != null){
                    CdmaPhoneCallState.PhoneCallState currCdmaCallState = mPhoneGlobals.cdmaPhoneCallState.getCurrentCallState();
                    if(currCdmaCallState != CdmaPhoneCallState.PhoneCallState.IDLE){
                        Call CdmaFgCall = mBtDTUtil.getFirstFgCall(PhoneConstants.PHONE_TYPE_CDMA);
                        if(CdmaFgCall.getState() != Call.State.DISCONNECTING && CdmaFgCall.getState() != Call.State.DIALING && CdmaFgCall.getState() != Call.State.ALERTING)
                            mNumHeld = 1 ;
                    }
                }

            }else if(mForegroundCallState != Call.State.DISCONNECTING){
                mNumHeld = getNumHeldCdma();
            }
        }

        Log.d(TAG, " handlePreciseCallStateChangeDualTalk: mNumHeld: " + mNumHeld + " mRingingCallState: " + mRingingCallState );

        CallInfo info = new CallInfo();
        info.mForegroundCall = foregroundCall;
        info.mBackgroundCallState = backgroundCall.getState();
        info.mOldForegroundCallState = oldForegroundCallState;
        info.mOldRingingCallState = oldRingingCallState;
        info.mOldRingNumber = oldRingNumber;
        info.mFgCalls = fgCalls;

        handleSpecialCasesForDualTalk(info);

        // below cdma special case handling shall only be carried out when active call is not GSM
        if (mBtDTUtil.isCdmaActive() && null != mPhoneGlobals.cdmaPhoneCallState && activePhoneType != PhoneConstants.PHONE_TYPE_GSM) {
            //mNumHeld = getNumHeldCdma();
            //PhoneGlobals app = PhoneGlobals.getInstance();
            //if (mCdmaPhoneState != null) {
            CdmaPhoneCallState.PhoneCallState currCdmaThreeWayCallState =
                    mPhoneGlobals.cdmaPhoneCallState.getCurrentCallState();
            CdmaPhoneCallState.PhoneCallState prevCdmaThreeWayCallState =
                mPhoneGlobals.cdmaPhoneCallState.getPreviousCallState();

            log("handlePreciseCallStateChangeDualTalk: CDMA call state: " + currCdmaThreeWayCallState + 
                " prev state:" + prevCdmaThreeWayCallState +
                " mCdmaThreeWayCallState:" + mCdmaThreeWayCallState);

            if ((mBluetoothHeadset != null) &&
                (mCdmaThreeWayCallState != currCdmaThreeWayCallState)) {
                // In CDMA, the network does not provide any feedback
                // to the phone when the 2nd MO call goes through the
                // stages of DIALING > ALERTING -> ACTIVE we fake the
                // sequence
                log("handlePreciseCallStateChangeDualTalk : CDMA 3way call state change. mNumActive: " + mNumActive +
                    " mNumHeld: " + mNumHeld + " IsThreeWayCallOrigStateDialing: " +
                    mPhoneGlobals.cdmaPhoneCallState.IsThreeWayCallOrigStateDialing());
                if ((currCdmaThreeWayCallState ==
                        CdmaPhoneCallState.PhoneCallState.THRWAY_ACTIVE)
                            && mPhoneGlobals.cdmaPhoneCallState.IsThreeWayCallOrigStateDialing()) {
                    // Mimic dialing, put the call on hold, alerting
                    updatePhoneStateChanged(0, mNumHeld,
                        convertCallState(Call.State.IDLE, Call.State.DIALING),
                        mRingNumber.mNumber, mRingNumber.mType);

                    updatePhoneStateChanged(0, mNumHeld,
                        convertCallState(Call.State.IDLE, Call.State.ALERTING),
                        mRingNumber.mNumber, mRingNumber.mType);

                }

                // In CDMA, the network does not provide any feedback to
                // the phone when a user merges a 3way call or swaps
                // between two calls we need to send a CIEV response
                // indicating that a call state got changed which should
                // trigger a CLCC update request from the BT client.
                if (currCdmaThreeWayCallState ==
                        CdmaPhoneCallState.PhoneCallState.CONF_CALL &&
                        prevCdmaThreeWayCallState ==
                          CdmaPhoneCallState.PhoneCallState.THRWAY_ACTIVE) {
                    log("handlePreciseCallStateChangeDualTalk: CDMA 3way conf call. mNumActive: " + mNumActive +
                        " mNumHeld: " + mNumHeld);
                    updatePhoneStateChanged(mNumActive, mNumHeld,
                        convertCallState(Call.State.IDLE, mForegroundCallState),
                        mRingNumber.mNumber, mRingNumber.mType);
                }
            }
            mCdmaThreeWayCallState = currCdmaThreeWayCallState;
        }

        boolean callsSwitched = false;
        if(bCanSwapInDiffSim){
            // a method to determine whether it's switched or not
            callsSwitched = isMultiTalksSwapped();
            log("handlePreciseCallStateChangeDualTalk: callsSwitched="+ callsSwitched + "swap call in MultiTalks Mode");
        }else{
            if(mBtDTUtil.isCdmaActive() && mCdmaThreeWayCallState == CdmaPhoneCallState.PhoneCallState.CONF_CALL){
                callsSwitched = mCdmaCallsSwapped;
                log("handlePreciseCallStateChangeDualTalk: callsSwitched="+ callsSwitched +
                    " in SingleTalk Mode (CDMA)");
            }else{
                /// !mBtDTUtil.isCdmaActive()
                ///   it means it only has GSM now.
                if(PhoneConstants.PHONE_TYPE_CDMA != backgroundCall.getPhone().getPhoneType()){
                       callsSwitched =
                            (mNumHeld == 1 && !(backgroundCall.getEarliestConnectTime() ==
                            mBgndEarliestConnectionTime));
                        log("handlePreciseCallStateChangeDualTalk: callsSwitched="+ callsSwitched +
                        " in SingleTalk Mode (GSM)");
                }
            }
        }
        
        /// update creation time for hold call
        if(PhoneConstants.PHONE_TYPE_CDMA != backgroundCall.getPhone().getPhoneType()){
            mBgndEarliestConnectionTime = backgroundCall.getEarliestConnectTime();
            log("handlePreciseCallStateChangeDualTalk: callsSwitched="+ callsSwitched +
                " update connection create time="+ mBgndEarliestConnectionTime);
        }

        // Save current call states for future use
        for(Call callObjF : fgCalls)
        {
            setPrevCallState(callObjF);
        }
        for(Call callObjR : ringCalls)
        {
            setPrevCallState(callObjR);
        }

        if (mNumActive != mOldNumActive || mNumHeld != mOldNumHeld ||
            mRingingCallState != oldRingingCallState ||
            mForegroundCallState != oldForegroundCallState ||
            !mRingNumber.equalTo(oldRingNumber) ||
            callsSwitched) {
            if (mBluetoothHeadset != null) {
                updatePhoneStateChanged(mNumActive, mNumHeld,
                    convertCallState(mRingingCallState, mForegroundCallState),
                    mRingNumber.mNumber, mRingNumber.mType);

            }
        }

        log("[[handlePreciseCallStateChangeDualTalk]]");
        
    }

    private boolean handleCommonSpecialCases(boolean callState, boolean prevCallState, CallInfo info){
        boolean continue_process = true;

        /**Case 1
                ** SIP call might change state too fast thus dialing would not be reported and only alerting is acquired.
                ** we shall add a dailing update to remote in this case
                ** @{ */
        {
            if(mForegroundCallState == Call.State.ALERTING && info.mOldForegroundCallState == Call.State.DIALING){
                mDialingAdded = true;
            }
            else if(mForegroundCallState == Call.State.ALERTING && info.mOldForegroundCallState != Call.State.DIALING && mDialingAdded == false){
        
                log("handleCommonSpecialCases case 1: Dilaing status is missed, add one for it");
        
                if (mBluetoothHeadset != null) {
                    updatePhoneStateChanged(mNumActive, mNumHeld,
                        CALL_STATE_DIALING,
                        mRingNumber.mNumber, mRingNumber.mType);
                    mDialingAdded = true;
                }
            }
            // in case there are multiple ALERTING reported which added duplicated DIALING
            else if (mForegroundCallState != Call.State.ALERTING && info.mOldForegroundCallState == Call.State.ALERTING){
                mDialingAdded = false;
            }
        }

        /**Case 2
                **  under 1A1H1I (SIP or GSM incoming), reject the incoming call wil trigger incoming call become disconnecting -> disconnected -> idle from incoming or waiting
                **  Since all three of them will be translated as callsetup idle and since currently there is 1A1H, thus the update will trigger (4,1) from native layer
                **  we shall ignore the transition states and update only the transition to idle.
                ** @{ */
        {
            if(mNumHeld > 0 && mNumActive >0 )
            {
                if( (info.mOldRingingCallState == Call.State.INCOMING || info.mOldRingingCallState == Call.State.WAITING) && 
                    (mRingingCallState == Call.State.DISCONNECTED || mRingingCallState == Call.State.DISCONNECTING) )
                {
                        log("handleCommonSpecialCases case 2: ignore incoming ending transition states. mRingingCallState:" + mRingingCallState);
                        continue_process = false;
                }
            }
        }

        return continue_process;

    }

    private boolean handleSipSpecialCases(boolean callState, boolean prevCallState, CallInfo info){

        boolean continue_process = true;

        /**Case 1
                **  under 1A1H1I (SIP or GSM incoming), another incoming call from the other phone will be reported for a short time then rejected by telephony framework.
                **  we shall ignore this temp incoming call change update
                **  but in current design when both 1A1H are of SIP phone, SIP incoming call will not be rejected directly by SIP phone thus won't be reported
                ** @{ */
        {
            if(mNumHeld > 0 && mNumActive >0 )
            {
                if( info.mOldRingingCallState == Call.State.WAITING && mRingingCallState == Call.State.WAITING )
                {
                    if(!mRingNumber.equalTo(info.mOldRingNumber))
                    {
                        log("handleSipSpecialCases case 1: ignore temp incoming update");
                        continue_process = false;
                    }
                }
            }
        }

        /**Case 2
                **  Active : SIP, Holding: GSM, swap in this case will generate a temp status of 2 Hoding calls. 
                **  we shall ignore this temp case
                ** @{ */
        {
            if(mNumHeld > 1)
            {
                log("handleSipSpecialCases case 2: mNumHeld =" + mNumHeld);            
                continue_process = false;
            }
        }

        return continue_process;

    }

    private boolean handleSpecialCases(CallInfo info){

        boolean callState = mNumActive > 0 || mNumHeld > 0;
        boolean prevCallState = mOldNumActive > 0 || mOldNumHeld > 0;
        boolean continue_process = true;

        continue_process = handleCommonSpecialCases(callState, prevCallState, info);

        if(continue_process)
        {
           continue_process = handleSipSpecialCases(callState, prevCallState, info);
        }
        
        return continue_process;

    }
    ///@}

    private void handlePreciseCallStateChange(Connection connection) {

        log("[handlePreciseCallStateChange]");

        // get foreground call state
        int oldNumActive = mNumActive;
        int oldNumHeld = mNumHeld;
        Call.State oldRingingCallState = mRingingCallState;
        Call.State oldForegroundCallState = mForegroundCallState;
        CallNumber oldRingNumber = mRingNumber;

        Call foregroundCall = mCM.getActiveFgCall();

        log("handlePreciseCallStateChange old: NumActive: "+ mNumActive +" NumHeld: "+ mNumHeld +
            " RingingCallState: "+ mRingingCallState +" ForegroundCallState: "+ mForegroundCallState +
            " RingNumber: "+ mRingNumber.mNumber+ " RingType: "+ mRingNumber.mType);

        if (VDBG)
            Log.d(TAG, " handlePreciseCallStateChange: foreground: " + foregroundCall +
                " background: " + mCM.getFirstActiveBgCall() + " ringing: " +
                mCM.getFirstActiveRingingCall());

     /// M: Delay SCO Disconnect for Busy Tone
        if(processDelayMOCallEnd()){
            log("handlePreciseCallStateChange: return directly for delay MO call end");
            return;
        }
     /// @}

        mForegroundCallState = foregroundCall.getState();
        /* if in transition, do not update */
        if (mForegroundCallState == Call.State.DISCONNECTING)
        {
            log("handlePreciseCallStateChange. Call disconnecting, wait before update");
            log("[[handlePreciseCallStateChange]]");
            return;
        }
        else
            mNumActive = (mForegroundCallState == Call.State.ACTIVE) ? 1 : 0;

        Call ringingCall = mCM.getFirstActiveRingingCall();
        mRingingCallState = ringingCall.getState();
        mRingNumber = getCallNumber(connection, ringingCall);

        if (mCM.getDefaultPhone().getPhoneType() == PhoneConstants.PHONE_TYPE_CDMA) {
            mNumHeld = getNumHeldCdma();
            PhoneGlobals app = PhoneGlobals.getInstance();
            if (app.cdmaPhoneCallState != null) {
                CdmaPhoneCallState.PhoneCallState currCdmaThreeWayCallState =
                        app.cdmaPhoneCallState.getCurrentCallState();
                CdmaPhoneCallState.PhoneCallState prevCdmaThreeWayCallState =
                    app.cdmaPhoneCallState.getPreviousCallState();

                log("CDMA call state: " + currCdmaThreeWayCallState + " prev state:" +
                    prevCdmaThreeWayCallState);

                if ((mBluetoothHeadset != null) &&
                    (mCdmaThreeWayCallState != currCdmaThreeWayCallState)) {
                    // In CDMA, the network does not provide any feedback
                    // to the phone when the 2nd MO call goes through the
                    // stages of DIALING > ALERTING -> ACTIVE we fake the
                    // sequence
                    log("CDMA 3way call state change. mNumActive: " + mNumActive +
                        " mNumHeld: " + mNumHeld + " IsThreeWayCallOrigStateDialing: " +
                        app.cdmaPhoneCallState.IsThreeWayCallOrigStateDialing());
                    if ((currCdmaThreeWayCallState ==
                            CdmaPhoneCallState.PhoneCallState.THRWAY_ACTIVE)
                                && app.cdmaPhoneCallState.IsThreeWayCallOrigStateDialing()) {
                        // Mimic dialing, put the call on hold, alerting
                        updatePhoneStateChanged(0, mNumHeld,
                            convertCallState(Call.State.IDLE, Call.State.DIALING),
                            mRingNumber.mNumber, mRingNumber.mType);

                        updatePhoneStateChanged(0, mNumHeld,
                            convertCallState(Call.State.IDLE, Call.State.ALERTING),
                            mRingNumber.mNumber, mRingNumber.mType);

                    }

                    // In CDMA, the network does not provide any feedback to
                    // the phone when a user merges a 3way call or swaps
                    // between two calls we need to send a CIEV response
                    // indicating that a call state got changed which should
                    // trigger a CLCC update request from the BT client.
                    if (currCdmaThreeWayCallState ==
                            CdmaPhoneCallState.PhoneCallState.CONF_CALL &&
                            prevCdmaThreeWayCallState ==
                              CdmaPhoneCallState.PhoneCallState.THRWAY_ACTIVE) {
                        log("CDMA 3way conf call. mNumActive: " + mNumActive +
                            " mNumHeld: " + mNumHeld);
                        updatePhoneStateChanged(mNumActive, mNumHeld,
                            convertCallState(Call.State.IDLE, mForegroundCallState),
                            mRingNumber.mNumber, mRingNumber.mType);
                    }
                }
                mCdmaThreeWayCallState = currCdmaThreeWayCallState;
            }
        } else {
            mNumHeld = getNumHeldUmts();
        }

        CallInfo info = new CallInfo();
        info.mForegroundCall = foregroundCall;
        //info.mBackgroundCallState = backgroundCall.getState();
        info.mOldForegroundCallState = oldForegroundCallState;
        info.mOldRingingCallState = oldRingingCallState;
        info.mOldRingNumber = oldRingNumber;
        //info.mFgCalls = fgCalls;

        if(!handleSpecialCases(info))
        {
            mNumActive = oldNumActive;
            mNumHeld = oldNumHeld;
            mRingingCallState = oldRingingCallState;
            mForegroundCallState = oldForegroundCallState;
            mRingNumber = oldRingNumber;
            return;
        }

        boolean callsSwitched = false;
        if (mCM.getDefaultPhone().getPhoneType() == PhoneConstants.PHONE_TYPE_CDMA &&
            mCdmaThreeWayCallState == CdmaPhoneCallState.PhoneCallState.CONF_CALL) {
            callsSwitched = mCdmaCallsSwapped;
        } else {
            Call backgroundCall = mCM.getFirstActiveBgCall();
            callsSwitched =
                (mNumHeld == 1 && ! (backgroundCall.getEarliestConnectTime() ==
                    mBgndEarliestConnectionTime));
            mBgndEarliestConnectionTime = backgroundCall.getEarliestConnectTime();
        }
        log("handlePreciseCallStateChange new: NumActive: "+ mNumActive +" NumHeld: "+ mNumHeld +
            " RingingCallState: "+ mRingingCallState +" ForegroundCallState: "+ mForegroundCallState +
            " RingNumber: "+ mRingNumber.mNumber+ " RingType: "+ mRingNumber.mType + " CallsSwitched: " + callsSwitched);


        if (mNumActive != oldNumActive || mNumHeld != oldNumHeld ||
            mRingingCallState != oldRingingCallState ||
            mForegroundCallState != oldForegroundCallState ||
            !mRingNumber.equalTo(oldRingNumber) ||
            callsSwitched) {
            if (mBluetoothHeadset != null) {
                updatePhoneStateChanged(mNumActive, mNumHeld,
                    convertCallState(mRingingCallState, mForegroundCallState),
                    mRingNumber.mNumber, mRingNumber.mType);
            }
        }

        log("[[handlePreciseCallStateChange]]");
        
    }

    private void handleListCurrentCalls() {
        Phone phone = mCM.getDefaultPhone();
        int phoneType = phone.getPhoneType();

        // TODO(BT) handle virtual call

        ///M: DAUL TALK SUPPORT @{ 
        if( true == FeatureOption.EVDO_DT_SUPPORT ){
            listCurrentCallsEVDO();        
        }else{
        ///}@
            if (phoneType == PhoneConstants.PHONE_TYPE_CDMA) {
                listCurrentCallsCdma();
            } else if (phoneType == PhoneConstants.PHONE_TYPE_GSM) {
                listCurrentCallsGsm();
            } else {
                Log.e(TAG, "Unexpected phone type: " + phoneType);
            }
        }        
        // end the result
        // when index is 0, other parameter does not matter
        mBluetoothHeadset.clccResponse(0, 0, 0, 0, false, "", 0);
    }

    private void handleQueryPhoneState() {
        if (mBluetoothHeadset != null) {
            updatePhoneStateChanged(mNumActive, mNumHeld,
                convertCallState(mRingingCallState, mForegroundCallState),
                mRingNumber.mNumber, mRingNumber.mType);
        }
    }

    private int getNumHeldUmts() {
        int countHeld = 0;
        List<Call> heldCalls = mCM.getBackgroundCalls();

        for (Call call : heldCalls) {
            if (call.getState() == Call.State.HOLDING || call.getState() == Call.State.DISCONNECTING) {
                countHeld++;
            }
        }
        return countHeld;
    }

    private int getNumHeldCdma() {
        int numHeld = 0;
        //PhoneGlobals app = PhoneGlobals.getInstance();
        if (mPhoneGlobals.cdmaPhoneCallState != null) {
            CdmaPhoneCallState.PhoneCallState curr3WayCallState =
                mPhoneGlobals.cdmaPhoneCallState.getCurrentCallState();
            CdmaPhoneCallState.PhoneCallState prev3WayCallState =
                mPhoneGlobals.cdmaPhoneCallState.getPreviousCallState();

            log("CDMA call state: " + curr3WayCallState + " prev state:" +
                prev3WayCallState);
            if (curr3WayCallState == CdmaPhoneCallState.PhoneCallState.CONF_CALL) {
                if (prev3WayCallState == CdmaPhoneCallState.PhoneCallState.THRWAY_ACTIVE) {
                    numHeld = 0; //0: no calls held, as now *both* the caller are active
                } else {
                    numHeld = 1; //1: held call and active call, as on answering a
                    // Call Waiting, one of the caller *is* put on hold
                }
            } else if (curr3WayCallState == CdmaPhoneCallState.PhoneCallState.THRWAY_ACTIVE) {
                numHeld = 1; //1: held call and active call, as on make a 3 Way Call
                // the first caller *is* put on hold
            } else {
                numHeld = 0; //0: no calls held as this is a SINGLE_ACTIVE call
            }
        }
        log("getNumHeldCdma: numHeld=" + numHeld);
        return numHeld;
    }

    private CallNumber getCallNumber(Connection connection, Call call) {
        String number = null;
        int type = 128;
        // find phone number and type
        if (connection == null) {
            connection = call.getEarliestConnection();
            if (connection == null) {
                Log.e(TAG, "Could not get a handle on Connection object for the call");
            }
        }
        if (connection != null) {
            number = connection.getAddress();
            if (number != null) {
                type = PhoneNumberUtils.toaFromString(number);
            }
        }
        if (number == null) {
            number = "";
        }

        /// M: @{
        Log.d(TAG, "getCallNumber: number = " + number);
        /// @}

        return new CallNumber(number, type);
    }

    private class CallNumber
    {
        private String mNumber = null;
        private int mType = 0;

        private CallNumber(String number, int type) {
            mNumber = number;
            mType = type;
        }

        private boolean equalTo(CallNumber callNumber)
        {
            if (mType != callNumber.mType) return false;

            if (mNumber != null && mNumber.compareTo(callNumber.mNumber) == 0) {
                return true;
            }
            return false;
        }
    }

    private BluetoothProfile.ServiceListener mProfileListener =
            new BluetoothProfile.ServiceListener() {
        public void onServiceConnected(int profile, BluetoothProfile proxy) {
            mBluetoothHeadset = (BluetoothHeadset) proxy;
            log("onServiceConnected, mBluetoothHeadset = " + proxy);
        }
        public void onServiceDisconnected(int profile) {
            mBluetoothHeadset = null;
            log("onServiceDisconnected, mBluetoothHeadset = null");
        }
    };

    private void listCurrentCallsGsm() {
        // Collect all known connections
        // clccConnections isindexed by CLCC index

        ///M: DAUL TALK SUPPORT : enlarge the limit of number of connections in dual talks project @{ 
        int MAX_CONNECTIONS;

        if(FeatureOption.MTK_DT_SUPPORT == true){
            MAX_CONNECTIONS = GSM_MAX_CONNECTIONS*2;
        }else{
            MAX_CONNECTIONS = GSM_MAX_CONNECTIONS;    
        }

        Connection[] clccConnections = new Connection[MAX_CONNECTIONS];
        /// }@
        LinkedList<Connection> newConnections = new LinkedList<Connection>();
        LinkedList<Connection> connections = new LinkedList<Connection>();

        Call foregroundCall = mCM.getActiveFgCall();
        Call backgroundCall = mCM.getFirstActiveBgCall();
        Call ringingCall = mCM.getFirstActiveRingingCall();

        if (ringingCall.getState().isAlive()) {
            connections.addAll(ringingCall.getConnections());
        }
        if (foregroundCall.getState().isAlive()) {
            connections.addAll(foregroundCall.getConnections());
        }
        if (backgroundCall.getState().isAlive()) {
            connections.addAll(backgroundCall.getConnections());
        }

        // Mark connections that we already known about
        ///M: DAUL TALK SUPPORT : enlarge the limit of number of connections in dual talks project @{ 
        boolean clccUsed[] = new boolean[MAX_CONNECTIONS];
        for (int i = 0; i < MAX_CONNECTIONS; i++) {
            clccUsed[i] = mClccUsed[i];
            mClccUsed[i] = false;
        }
        for (Connection c : connections) {
            boolean found = false;
            long timestamp = c.getCreateTime();
            for (int i = 0; i < MAX_CONNECTIONS; i++) {
                if (clccUsed[i] && timestamp == mClccTimestamps[i]) {
                    mClccUsed[i] = true;
                    found = true;
                    clccConnections[i] = c;
                    break;
                }
            }
            if (!found) {
                newConnections.add(c);
            }
        }
        ///}@

        // Find a CLCC index for new connections
        while (!newConnections.isEmpty()) {
            // Find lowest empty index
            int i = 0;
            while (mClccUsed[i]) i++;
            // Find earliest connection
            long earliestTimestamp = newConnections.get(0).getCreateTime();
            Connection earliestConnection = newConnections.get(0);
            for (int j = 0; j < newConnections.size(); j++) {
                long timestamp = newConnections.get(j).getCreateTime();
                if (timestamp < earliestTimestamp) {
                    earliestTimestamp = timestamp;
                    earliestConnection = newConnections.get(j);
                }
            }

            // update
            mClccUsed[i] = true;
            mClccTimestamps[i] = earliestTimestamp;
            clccConnections[i] = earliestConnection;
            newConnections.remove(earliestConnection);
        }

        // Send CLCC response to Bluetooth headset service
        for (int i = 0; i < clccConnections.length; i++) {
            if (mClccUsed[i]) {
                sendClccResponseGsm(i, clccConnections[i]);
            }
        }
    }

    /** Convert a Connection object into a single +CLCC result */
    private void sendClccResponseGsm(int index, Connection connection) {
        int state = convertCallState(connection.getState());
        boolean mpty = false;
        Call call = connection.getCall();
        if (call != null) {
            mpty = call.isMultiparty();
        }

        int direction = connection.isIncoming() ? 1 : 0;

        String number = connection.getAddress();
        int type = -1;
        if (number != null) {
            type = PhoneNumberUtils.toaFromString(number);
        }

        mBluetoothHeadset.clccResponse(index + 1, direction, state, 0, mpty, number, type);
    }

    /** Build the +CLCC result for CDMA
     *  The complexity arises from the fact that we need to maintain the same
     *  CLCC index even as a call moves between states. */
    private synchronized void listCurrentCallsCdma() {
        // In CDMA at one time a user can have only two live/active connections
        Connection[] clccConnections = new Connection[CDMA_MAX_CONNECTIONS];// indexed by CLCC index
        Call foregroundCall = mCM.getActiveFgCall();
        Call ringingCall = mCM.getFirstActiveRingingCall();

        Call.State ringingCallState = ringingCall.getState();
        // If the Ringing Call state is INCOMING, that means this is the very first call
        // hence there should not be any Foreground Call
        if (ringingCallState == Call.State.INCOMING) {
            if (VDBG) log("Filling clccConnections[0] for INCOMING state");
            clccConnections[0] = ringingCall.getLatestConnection();
        } else if (foregroundCall.getState().isAlive()) {
            // Getting Foreground Call connection based on Call state
            if (ringingCall.isRinging()) {
                if (VDBG) log("Filling clccConnections[0] & [1] for CALL WAITING state");
                clccConnections[0] = foregroundCall.getEarliestConnection();
                clccConnections[1] = ringingCall.getLatestConnection();
            } else {
                if (foregroundCall.getConnections().size() <= 1) {
                    // Single call scenario
                    if (VDBG) {
                        log("Filling clccConnections[0] with ForgroundCall latest connection");
                    }
                    clccConnections[0] = foregroundCall.getLatestConnection();
                } else {
                    // Multiple Call scenario. This would be true for both
                    // CONF_CALL and THRWAY_ACTIVE state
                    if (VDBG) {
                        log("Filling clccConnections[0] & [1] with ForgroundCall connections");
                    }
                    clccConnections[0] = foregroundCall.getEarliestConnection();
                    clccConnections[1] = foregroundCall.getLatestConnection();
                }
            }
        }

        // Update the mCdmaIsSecondCallActive flag based on the Phone call state
        if (PhoneGlobals.getInstance().cdmaPhoneCallState.getCurrentCallState()
                == CdmaPhoneCallState.PhoneCallState.SINGLE_ACTIVE) {
            Message msg = mHandler.obtainMessage(CDMA_SET_SECOND_CALL_STATE, false);
            mHandler.sendMessage(msg);
        } else if (PhoneGlobals.getInstance().cdmaPhoneCallState.getCurrentCallState()
                == CdmaPhoneCallState.PhoneCallState.THRWAY_ACTIVE) {
            Message msg = mHandler.obtainMessage(CDMA_SET_SECOND_CALL_STATE, true);
            mHandler.sendMessage(msg);
        }

        // send CLCC result
        for (int i = 0; (i < clccConnections.length) && (clccConnections[i] != null); i++) {
            sendClccResponseCdma(i, clccConnections[i]);
        }
    }

    /** Send ClCC results for a Connection object for CDMA phone */
    private void sendClccResponseCdma(int index, Connection connection) {
        int state;
        PhoneGlobals app = PhoneGlobals.getInstance();
        CdmaPhoneCallState.PhoneCallState currCdmaCallState =
                app.cdmaPhoneCallState.getCurrentCallState();
        CdmaPhoneCallState.PhoneCallState prevCdmaCallState =
                app.cdmaPhoneCallState.getPreviousCallState();

        if ((prevCdmaCallState == CdmaPhoneCallState.PhoneCallState.THRWAY_ACTIVE)
                && (currCdmaCallState == CdmaPhoneCallState.PhoneCallState.CONF_CALL)) {
            // If the current state is reached after merging two calls
            // we set the state of all the connections as ACTIVE
            state = CALL_STATE_ACTIVE;
        } else {
            Call.State callState = connection.getState();
            switch (callState) {
            case ACTIVE:
                // For CDMA since both the connections are set as active by FW after accepting
                // a Call waiting or making a 3 way call, we need to set the state specifically
                // to ACTIVE/HOLDING based on the mCdmaIsSecondCallActive flag. This way the
                // CLCC result will allow BT devices to enable the swap or merge options
                if (index == 0) { // For the 1st active connection
                    state = mCdmaIsSecondCallActive ? CALL_STATE_HELD : CALL_STATE_ACTIVE;
                } else { // for the 2nd active connection
                    state = mCdmaIsSecondCallActive ? CALL_STATE_ACTIVE : CALL_STATE_HELD;
                }
                break;
            case HOLDING:
                state = CALL_STATE_HELD;
                break;
            case DIALING:
                state = CALL_STATE_DIALING;
                break;
            case ALERTING:
                state = CALL_STATE_ALERTING;
                break;
            case INCOMING:
                state = CALL_STATE_INCOMING;
                break;
            case WAITING:
                state = CALL_STATE_WAITING;
                break;
            default:
                Log.e(TAG, "bad call state: " + callState);
                return;
            }
        }

        boolean mpty = false;
        if (currCdmaCallState == CdmaPhoneCallState.PhoneCallState.CONF_CALL) {
            if (prevCdmaCallState == CdmaPhoneCallState.PhoneCallState.THRWAY_ACTIVE) {
                // If the current state is reached after merging two calls
                // we set the multiparty call true.
                mpty = true;
            } // else
                // CALL_CONF state is not from merging two calls, but from
                // accepting the second call. In this case first will be on
                // hold in most cases but in some cases its already merged.
                // However, we will follow the common case and the test case
                // as per Bluetooth SIG PTS
        }

        int direction = connection.isIncoming() ? 1 : 0;

        String number = connection.getAddress();
        int type = -1;
        if (number != null) {
            type = PhoneNumberUtils.toaFromString(number);
        } else {
            number = "";
        }

        mBluetoothHeadset.clccResponse(index + 1, direction, state, 0, mpty, number, type);
    }

///M: DAUL TALK SUPPORT  C+G case @{ 
    private synchronized void listCurrentCallsEVDO() {

        Connection[] clccConnections = new Connection[CDMA_MAX_CONNECTIONS + GSM_MAX_CONNECTIONS];
        LinkedList<Connection> connections = new LinkedList<Connection>();

        Call ringingCall = mCM.getFirstActiveRingingCall();
        List<Call> bCalls = mCM.getBackgroundCalls();
        List<Call> fCalls = mCM.getForegroundCalls();

        if (ringingCall.getState().isAlive()) {
            //shall be only one ringing call
            connections.addAll(ringingCall.getConnections());
        }

        for(Call fCall : fCalls){
            if(fCall.getState().isAlive()){
                connections.addAll(fCall.getConnections());
            }
        }

        for(Call bCall : bCalls){
            if (bCall.getState().isAlive()) {
                connections.addAll(bCall.getConnections());
            }
        }

        int i = 0;
        for (Connection c : connections) {
            if(i < CDMA_MAX_CONNECTIONS + GSM_MAX_CONNECTIONS){
                clccConnections[i] = c;
                i++;
            }
            else{
                log("evdoGetClccResult : exceeds the connection number limit");
            }
        }
 
        // Update the mCdmaIsSecondCallActive flag based on the Phone call state
        if (PhoneGlobals.getInstance().cdmaPhoneCallState.getCurrentCallState()
                == CdmaPhoneCallState.PhoneCallState.SINGLE_ACTIVE) {
            Message msg = mHandler.obtainMessage(CDMA_SET_SECOND_CALL_STATE, false);
            mHandler.sendMessage(msg);
        } else if (PhoneGlobals.getInstance().cdmaPhoneCallState.getCurrentCallState()
                == CdmaPhoneCallState.PhoneCallState.THRWAY_ACTIVE) {
            Message msg = mHandler.obtainMessage(CDMA_SET_SECOND_CALL_STATE, true);
            mHandler.sendMessage(msg);
        }

        // Send CLCC response to Bluetooth headset service
        for (i = 0; (i < clccConnections.length) && (clccConnections[i] != null); i++) {
            sendClccResponseEVDO(i,clccConnections[i]);
        }

        return ;
    }

    /** Convert a Connection object into a single +CLCC result for C+G phones */
    private void sendClccResponseEVDO(int index, Connection c) {

        int state = 0;
        log("[API] evdoConnectionToClccEntry : index="+String.valueOf(index)+" state=" + c.getState());

        int phoneType =  c.getCall().getPhone().getPhoneType();

        Call backgroundCall = mCM.getFirstActiveBgCall();
        Call foregroundCall = mCM.getActiveFgCall();
        List<Call> fCalls = mCM.getForegroundCalls();

        PhoneGlobals app = PhoneGlobals.getInstance();
        CdmaPhoneCallState.PhoneCallState currCdmaCallState =
                app.cdmaPhoneCallState.getCurrentCallState();
        CdmaPhoneCallState.PhoneCallState prevCdmaCallState =
                app.cdmaPhoneCallState.getPreviousCallState();

        if ((phoneType == PhoneConstants.PHONE_TYPE_CDMA) && (prevCdmaCallState == CdmaPhoneCallState.PhoneCallState.THRWAY_ACTIVE) && (currCdmaCallState == CdmaPhoneCallState.PhoneCallState.CONF_CALL)){
            // If the current state is reached after merging two calls
            // we set the state of all the connections as ACTIVE
            log("evdoConnectionToClccEntry : CDMA THRWAY to CONF CALL");
            state = CALL_STATE_ACTIVE;
        }else {
            switch(c.getState()){
                case ACTIVE:
                {
                    if(phoneType == PhoneConstants.PHONE_TYPE_CDMA){
                        boolean found = false;
                        for(Call fCall : fCalls){
                            phoneType = fCall.getPhone().getPhoneType();
                            if(phoneType == PhoneConstants.PHONE_TYPE_CDMA){
                                if(fCall.getConnections().size() <= 1){
                                    // Single call scenario
                                    log("evdoConnectionToClccEntry : single CDMA call");
                                    state = CALL_STATE_ACTIVE;
                                }else{
                                    // Multiple Call scenario. This would be true for both
                                    // CONF_CALL and THRWAY_ACTIVE state
                                    log("evdoConnectionToClccEntry : multi CDMA call, mCdmaIsSecondCallActive =" + mCdmaIsSecondCallActive);
                                    if(c == fCall.getEarliestConnection()){
                                        log("evdoConnectionToClccEntry : earliest connection");
                                        state = mCdmaIsSecondCallActive ? CALL_STATE_HELD : CALL_STATE_ACTIVE;
                                    }else{
                                        log("evdoConnectionToClccEntry : last connection");
                                        state = mCdmaIsSecondCallActive ? CALL_STATE_ACTIVE : CALL_STATE_HELD;
                                    }
                                }
                                found = true;
                                break;
                            }
                        }
                        if(!found){
                            log("evdoConnectionToClccEntry : can't match CDMA call");
                            return;
                        }
                    }else if(phoneType == PhoneConstants.PHONE_TYPE_GSM){
                        state = CALL_STATE_ACTIVE;
                    }
                    else{
                        log("evdoConnectionToClccEntry : wrong phone type for ACTIVE connection");
                        return;
                    }
                    break;
                }

                case HOLDING:
                    state = CALL_STATE_HELD;
                    break;
                case DIALING:
                    state = CALL_STATE_DIALING;
                    break;
                case ALERTING:
                    state = CALL_STATE_ALERTING;
                    break;
                case INCOMING:
                {
                    if (backgroundCall.getState().isAlive() || foregroundCall.getState().isAlive()) {
                        log("evdoConnectionToClccEntry : INCOMING with holding or active call, switch to WAITING");
                        state = CALL_STATE_WAITING;
                    }else{
                        state = CALL_STATE_INCOMING;
                    }
                    break;
                }
                case WAITING:
                    state = CALL_STATE_INCOMING;
                    break;
                default:
                    return;  // bad state
            }
        }

        boolean mpty = false;
        if(phoneType == PhoneConstants.PHONE_TYPE_CDMA){
            if (currCdmaCallState == CdmaPhoneCallState.PhoneCallState.CONF_CALL) {
                if (prevCdmaCallState == CdmaPhoneCallState.PhoneCallState.THRWAY_ACTIVE) {
                    // If the current state is reached after merging two calls
                    // we set the multiparty call true.
                    mpty = true;
                } else {
                    // CALL_CONF state is not from merging two calls, but from
                    // accepting the second call. In this case first will be on
                    // hold in most cases but in some cases its already merged.
                    // However, we will follow the common case and the test case
                    // as per Bluetooth SIG PTS
                    mpty = false;
                }
            } else {
                mpty = false;
            }
        }else if (phoneType == PhoneConstants.PHONE_TYPE_GSM){
            Call call = c.getCall();
            if (call != null) {
                mpty = call.isMultiparty();
            }
        }

        int direction = c.isIncoming() ? 1 : 0;

        String number = c.getAddress();

        int type = -1;
        if (number != null) {
            type = PhoneNumberUtils.toaFromString(number);
        }

        mBluetoothHeadset.clccResponse(index + 1, direction, state, 0, mpty, number, type);

        return;
    }

///@}

    private void handleCdmaSwapSecondCallState() {
        if (VDBG) log("cdmaSwapSecondCallState: Toggling mCdmaIsSecondCallActive");
        mCdmaIsSecondCallActive = !mCdmaIsSecondCallActive;
        mCdmaCallsSwapped = true;
    }


    /// M: fix CDMA Swap update too fast @{
    private void handleCdmaSwapSecondCallStateBT() {
        if (VDBG) log("cdmaSwapSecondCallState: Toggling mCdmaIsSecondCallActive from BT");
        mCdmaIsSecondCallActive = !mCdmaIsSecondCallActive;
        mCdmaCallsSwapped = true;

        Call backgroundCall = mCM.getFirstActiveBgCall();
        PhoneUtils.switchHoldingAndActive(backgroundCall);
    }
    /// @}

    private void handleCdmaSetSecondCallState(boolean state) {
        if (VDBG) log("cdmaSetSecondCallState: Setting mCdmaIsSecondCallActive to " + state);
        mCdmaIsSecondCallActive = state;

        if (!mCdmaIsSecondCallActive) {
            mCdmaCallsSwapped = false;
        }
    }

    ///M: DAUL TALK SUPPORT : C+G case @{ 
    private enum DT_EVDO_SCENARIO {
        DT_SCENARIO_N,      // 0
        DT_SCENARIO_N_I,    // 1
        DT_SCENARIO_N_A,    // 2 <- N_2A, N_H, N_AH
        DT_SCENARIO_N_IA,   // 3 <- N_IH, N_IAH
        DT_SCENARIO_I_N,    // 4
        DT_SCENARIO_I_A,    // 5 <- I_2A, I_H, I_AH
        DT_SCENARIO_A_N,    // 6
        DT_SCENARIO_A_I,    // 7
        DT_SCENARIO_A_IH,   // 8 <- A_IA
        DT_SCENARIO_A_H,    // 9 <- A_A, A_AH, A_2A
        DT_SCENARIO_H_N,    // 10
        DT_SCENARIO_H_I,    // 11
        DT_SCENARIO_H_IA,   // 12
        DT_SCENARIO_H_A,    // 13 <- H_2A, H_AH
        DT_SCENARIO_IA_N,   // 14
        DT_SCENARIO_IA_H,   // 15 <-IA_A, IA_AH, IA_2A
        DT_SCENARIO_IH_N,   // 16
        DT_SCENARIO_IH_A,   // 17 <-IH_2A, IH_AH
        DT_SCENARIO_AH_N,   // 18
        DT_SCENARIO_AH_I,   // 19
        DT_SCENARIO_IAH_N   // 20
        ;
    }


    private DT_EVDO_SCENARIO evdoAnalyzeDTScenario() {
        log("evdoAnalyzeDTScenario");

        Call ringingCall = mCM.getFirstActiveRingingCall();
        Call backgroundCall = mCM.getFirstActiveBgCall();
        List<Call> fCalls = mCM.getForegroundCalls();

        boolean gsmI = false;
        boolean gsmA = false;
        boolean gsmH = false;
        boolean cdmaI = false;
        boolean cdmaA = false;

        int phoneType;

        DT_EVDO_SCENARIO scenario = DT_EVDO_SCENARIO.DT_SCENARIO_N;

        if(FeatureOption.EVDO_DT_SUPPORT == false)
        {
            log("[Err] evdoAnalyzeDTScenario EVDO dualTalk not support");
            return scenario;
        }

        if(ringingCall != null && ringingCall.getState().isRinging())
        {
            phoneType = ringingCall.getPhone().getPhoneType();
            if(phoneType == PhoneConstants.PHONE_TYPE_CDMA)
            {
                cdmaI = true;
            }
            else{
                gsmI = true;
            }
        }

        if(backgroundCall != null && backgroundCall.getState().isAlive())
        {
            gsmH = true;
        }

        for(Call aCall : fCalls){
            if(aCall.getState() == Call.State.ACTIVE){
                phoneType = aCall.getPhone().getPhoneType();

                if(phoneType == PhoneConstants.PHONE_TYPE_CDMA){
                    cdmaA = true;
                }
                else{
                    gsmA = true;
                }
            }
        }

        //only CDMA
        if(!gsmI && !gsmH && !gsmA){
            if(cdmaI && cdmaA){
                scenario = DT_EVDO_SCENARIO.DT_SCENARIO_N_IA;
            }else if(cdmaI){
                scenario = DT_EVDO_SCENARIO.DT_SCENARIO_N_I;
            }else if(cdmaA){
                scenario = DT_EVDO_SCENARIO.DT_SCENARIO_N_A;
            }else{
                log("[Err] evdoAnalyzeDTScenario (N,N)");
            }
            
        }

        //GSM only I
        if(gsmI && !gsmH && !gsmA){
            if(cdmaI && cdmaA){
                log("[Err] evdoAnalyzeDTScenario (I,IA)");
            }else if(cdmaI){
                log("[Err] evdoAnalyzeDTScenario (I,I)");
            }else if(cdmaA){
                scenario = DT_EVDO_SCENARIO.DT_SCENARIO_I_A;
            }else{
                scenario = DT_EVDO_SCENARIO.DT_SCENARIO_I_N;
            }
        }

        //GSM only A
        if(!gsmI && !gsmH && gsmA){
            if(cdmaI && cdmaA){
                scenario = DT_EVDO_SCENARIO.DT_SCENARIO_A_IH;
            }else if(cdmaI){
                scenario = DT_EVDO_SCENARIO.DT_SCENARIO_A_I;
            }else if(cdmaA){
                scenario = DT_EVDO_SCENARIO.DT_SCENARIO_A_H;
            }else{
                scenario = DT_EVDO_SCENARIO.DT_SCENARIO_A_N;
            }
        }

        //GSM only H
        if(!gsmI && gsmH && !gsmA){
           if(cdmaI && cdmaA){
                scenario = DT_EVDO_SCENARIO.DT_SCENARIO_H_IA;
           }else if(cdmaI){
                scenario = DT_EVDO_SCENARIO.DT_SCENARIO_H_I;
           }else if(cdmaA){
                scenario = DT_EVDO_SCENARIO.DT_SCENARIO_H_A;
           }else{
                scenario = DT_EVDO_SCENARIO.DT_SCENARIO_H_N;
           }  
        }

        //GSM IA
        if(gsmI && !gsmH && gsmA){
            if(cdmaI && cdmaA){
                log("[Err] evdoAnalyzeDTScenario (IA,IA)");
            }else if(cdmaI){
                log("[Err] evdoAnalyzeDTScenario (IA,I)");
            }else if(cdmaA){
                scenario = DT_EVDO_SCENARIO.DT_SCENARIO_IA_H;
            }else{
                scenario = DT_EVDO_SCENARIO.DT_SCENARIO_IA_N;
            }
        }

        //GSM IH
        if(gsmI && gsmH && !gsmA){
            if(cdmaI && cdmaA){
                log("[Err] evdoAnalyzeDTScenario (IH,IA)");
            }else if(cdmaI){
                log("[Err] evdoAnalyzeDTScenario (IH,I)");
            }else if(cdmaA){
                scenario = DT_EVDO_SCENARIO.DT_SCENARIO_IH_A;
            }else{
                scenario = DT_EVDO_SCENARIO.DT_SCENARIO_IH_N;
            }
        }

        //GSM AH
        if(!gsmI && gsmH && gsmA){
            if(cdmaI && cdmaA){
                log("[Err] evdoAnalyzeDTScenario (AH,IA)");
            }else if(cdmaI){
                scenario = DT_EVDO_SCENARIO.DT_SCENARIO_AH_I;
            }else if(cdmaA){
                log("[Err] evdoAnalyzeDTScenario (AH,A)");
            }else{
                scenario = DT_EVDO_SCENARIO.DT_SCENARIO_AH_N;
            }
        }

        //GSM IAH
        if(gsmI && gsmH && gsmA){
            if(cdmaI && cdmaA){
                log("[Err] evdoAnalyzeDTScenario (IAH,IA)");
            }else if(cdmaI){
                log("[Err] evdoAnalyzeDTScenario (IAH,I)");
            }else if(cdmaA){
                log("[Err] evdoAnalyzeDTScenario (IAH,IA)");
            }else{
                scenario = DT_EVDO_SCENARIO.DT_SCENARIO_IAH_N;
            }
        }

        
        log("evdoAnalyzeDTScenario final =" + scenario);
        
        return scenario;
        
    }

    private boolean evdoDTChldHandler0(DT_EVDO_SCENARIO scenario) {
        log("evdoDTChldHandler0 : scenario = " + scenario);

        int phoneType ;
        
        Call ringingCall = mCM.getFirstActiveRingingCall();
        Call backgroundCall = mCM.getFirstActiveBgCall();
        List<Call> fCalls = mCM.getForegroundCalls();

        switch(scenario){
            //hangup incoming call
            case DT_SCENARIO_N_I:
            case DT_SCENARIO_I_N:
            case DT_SCENARIO_I_A:
            case DT_SCENARIO_A_I:
            case DT_SCENARIO_H_I:
            case DT_SCENARIO_IA_N:
            case DT_SCENARIO_IA_H:
            case DT_SCENARIO_IH_N:
            case DT_SCENARIO_IH_A:
            case DT_SCENARIO_AH_I:
            case DT_SCENARIO_IAH_N:{
                log("CHLD 0 : hangup incoming call 1");
                try{
                    ringingCall.hangup();
                } catch (CallStateException ex){
                    log("CHLD 0 : catch CallStateException");
                    return false;
                }
                return true;
            }

            // CDMA cann't not hangup incoming call without hangup the active. 
            // by hanging up the connection, CDMA framework will help to deceive user that the incoming call has been hanged up while not sending command to network.
            case DT_SCENARIO_N_IA:
            case DT_SCENARIO_A_IH:
            case DT_SCENARIO_H_IA:
            {
                log("CHLD 0 : hangup incoming call 2");

                try{
                    ringingCall.getLatestConnection().hangup();
                } catch (CallStateException ex){
                    log("CHLD 0 : catch CallStateException");
                    return false;
                }
                return true;
            }

            //N/A
            case DT_SCENARIO_N_A:
            case DT_SCENARIO_A_N:{
                log("CHLD 0 : N/A");
                return false;
            }

            //hangup CDMA Active call (hold call or mute indeed)
            case DT_SCENARIO_A_H:{
                for(Call aCall : fCalls){
                    if(aCall.getState() == Call.State.ACTIVE){
                        phoneType = aCall.getPhone().getPhoneType();
                        if(phoneType == PhoneConstants.PHONE_TYPE_CDMA){
                            log("CHLD 0 : hangup CDMA Active Call (hold call or mute indeed)");
                            try{
                                aCall.hangup();
                            } catch (CallStateException ex){
                                log("CHLD 0 : catch CallStateException");
                                return false;
                            }
                            return true;
                        }
                    }
                }
                log("CHLD 0 : N/A");
                return false;
            }

            //hangup GSM hold call
            case DT_SCENARIO_H_N:
            case DT_SCENARIO_H_A:
            case DT_SCENARIO_AH_N:{
                log("CHLD 0 : hangup GSM Hold Call");
                try{
                    backgroundCall.hangup();
                } catch (CallStateException ex){
                    log("CHLD 0 : catch CallStateException");
                    return false;
                }
                return true;
            }

            default:
                log("CHLD 0 : wrong scenario");
                return false;

        }
    }

    private boolean evdoDTChldHandler1(DT_EVDO_SCENARIO scenario) {
        log("evdoDTChldHandler1 : scenario = " + scenario);

        int phoneType ;
        
        Call ringingCall = mCM.getFirstActiveRingingCall();
        Call backgroundCall = mCM.getFirstActiveBgCall();
        List<Call> fCalls = mCM.getForegroundCalls();

        switch(scenario){
            case DT_SCENARIO_N_I:
            case DT_SCENARIO_N_IA:
            case DT_SCENARIO_I_N:
            case DT_SCENARIO_H_I:
            case DT_SCENARIO_IH_N:
            {
                log("CHLD 1 : answer incoming call");
                PhoneGlobals.getInstance().phoneMgr.answerRingingCall();

                if(scenario == DT_EVDO_SCENARIO.DT_SCENARIO_N_IA){
                    Message msg = mHandler.obtainMessage(CDMA_SET_SECOND_CALL_STATE, true);
                    mHandler.sendMessage(msg);
                }
                    
                return true;
            }

            case DT_SCENARIO_N_A:
            case DT_SCENARIO_H_A:
            {
                for(Call aCall : fCalls){
                    if(aCall.getState() == Call.State.ACTIVE){
                        phoneType = aCall.getPhone().getPhoneType();
                        if(phoneType == PhoneConstants.PHONE_TYPE_CDMA){
                            log("CHLD 1 : hangup CDMA Active Call");
                            // use PhoneUtils ' hangup to active the hold call at the same time since they are in different modem
                            PhoneUtils.hangup(aCall);
                            return true;
                        }
                    }
                }
                log("CHLD 1 : N/A");
                return false;
            }

            case DT_SCENARIO_I_A:
            case DT_SCENARIO_IA_N:
            case DT_SCENARIO_IH_A:
            case DT_SCENARIO_IAH_N:
            {
                log("CHLD 1 : answer ringing call and end active call");
                PhoneUtils.answerAndEndActive(mCM, ringingCall);
                return true;
            }

            case DT_SCENARIO_A_N:
            {
                for(Call aCall : fCalls){
                    if(aCall.getState() == Call.State.ACTIVE){
                        phoneType = aCall.getPhone().getPhoneType();
                        if(phoneType == PhoneConstants.PHONE_TYPE_GSM){
                            log("CHLD 1 : hangup GSM Active Call");
                            try{
                                aCall.hangup();
                            } catch (CallStateException ex){
                                log("CHLD 1 : catch CallStateException");
                                return false;
                            }
                            return true;
                        }
                    }
                }
                log("CHLD 1 : N/A");
                return false;
            }

            // can not use PhoneUtils.answerAndEndActive since it AH in the same modem will make the incoming call being hold as the consequence.
            // picking up the incoming call will end the actvie call by phone app for planner's spec definition.
            case DT_SCENARIO_AH_I:
            {

                PhoneGlobals.getInstance().phoneMgr.answerRingingCall();
                return true;
            }


            case DT_SCENARIO_A_I:
            case DT_SCENARIO_A_IH:
            case DT_SCENARIO_IA_H:
            {
                for(Call aCall : fCalls){
                    if(aCall.getState() == Call.State.ACTIVE){
                        phoneType = aCall.getPhone().getPhoneType();
                        if(phoneType == PhoneConstants.PHONE_TYPE_GSM){
                            log("CHLD 1 : hangup GSM Active Call and answer incoming call");

                            try{                            
                                aCall.hangup();
                            } catch (CallStateException ex){
                                log("CHLD 1 : catch CallStateException");
                                return false;
                            }

                            // for GSM IA case, call.hangup() will directly pick up the incoming call, no need to answer again
                            if(scenario != DT_EVDO_SCENARIO.DT_SCENARIO_IA_H){
                                PhoneGlobals.getInstance().phoneMgr.answerRingingCall();
                            }
                            if(scenario == DT_EVDO_SCENARIO.DT_SCENARIO_A_IH){
                                Message msg = mHandler.obtainMessage(CDMA_SET_SECOND_CALL_STATE, true);
                                mHandler.sendMessage(msg);
                            }
                            return true;
                        }
                    }
                }
                log("CHLD 1 : N/A");                
                return false;
            }

            case DT_SCENARIO_A_H:
            case DT_SCENARIO_AH_N:
            {
                for(Call aCall : fCalls){
                    if(aCall.getState() == Call.State.ACTIVE){
                        phoneType = aCall.getPhone().getPhoneType();
                        if(phoneType == PhoneConstants.PHONE_TYPE_GSM){
                            log("CHLD 1 : hangup GSM Active Call");
                            PhoneUtils.hangupActiveCall(aCall);
                            return true;
                        }
                    }
                }
                log("CHLD 1 : N/A");
                return false;
            }

            case DT_SCENARIO_H_N:{
                log("CHLD 1 : switch GSM Hold call");
                PhoneUtils.switchHoldingAndActive(backgroundCall);
                return true;
            }

            case DT_SCENARIO_H_IA:
            {
                log("CHLD 1 : end GSM Hold call and answer incoming call");

                try{                
                    backgroundCall.hangup();
                } catch (CallStateException ex){
                    log("CHLD 1 : catch CallStateException");
                    return false;
                }

                PhoneGlobals.getInstance().phoneMgr.answerRingingCall();
                Message msg = mHandler.obtainMessage(CDMA_SET_SECOND_CALL_STATE, true);
                mHandler.sendMessage(msg);

                return true;
            }

            default:
                log("CHLD 1 : wrong scenario");
                return false;

        }
    }

    private boolean evdoDTChldHandler2(DT_EVDO_SCENARIO scenario) {
        log("evdoDTChldHandler2 : scenario =" + scenario);

        int phoneType ;
        
        Call ringingCall = mCM.getFirstActiveRingingCall();
        Call backgroundCall = mCM.getFirstActiveBgCall();
        Call foregroundCall = mCM.getActiveFgCall();
        List<Call> fCalls = mCM.getForegroundCalls();

        switch(scenario){
            case DT_SCENARIO_N_I:
            case DT_SCENARIO_N_IA:
            case DT_SCENARIO_I_N:
            case DT_SCENARIO_I_A:
            case DT_SCENARIO_H_I:
            case DT_SCENARIO_H_IA:
            case DT_SCENARIO_IA_N:
            case DT_SCENARIO_IH_N:
            {
                log("CHLD 2 : answer incoming call");
                PhoneGlobals.getInstance().phoneMgr.answerRingingCall();

                if(scenario == DT_EVDO_SCENARIO.DT_SCENARIO_N_IA || scenario == DT_EVDO_SCENARIO.DT_SCENARIO_H_IA){
                    Message msg = mHandler.obtainMessage(CDMA_SET_SECOND_CALL_STATE, true);
                    mHandler.sendMessage(msg);
                }
                    
                return true;
            }

            case DT_SCENARIO_N_A:
            {
                if(PhoneGlobals.getInstance().cdmaPhoneCallState.getCurrentCallState()== CdmaPhoneCallState.PhoneCallState.CONF_CALL){
                     log("CHLD 2 : swap CDMA AH for it is in CONF_CALL state");

                     /// M: fix CDMA Swap update too fast @{
                     // Toggle the second callers active state flag
                     Message msg = Message.obtain(mHandler, CDMA_SWAP_SECOND_CALL_STATE_BT);
                     /// @}

                     mHandler.sendMessage(msg);
                     return true;
                }

                log("CHLD 2 : N/A");
                return false;
            }

            case DT_SCENARIO_A_N:
            {
                log("CHLD 2 : switch GSM Active call to Hold");
                try{                
                    foregroundCall.getPhone().switchHoldingAndActive();
                } catch (CallStateException ex){
                    log("CHLD 2 : catch CallStateException");
                    return false;
                }
                return true;
            }

            case DT_SCENARIO_A_I:
            case DT_SCENARIO_A_IH:
            {
                for(Call aCall : fCalls){
                    if(aCall.getState() == Call.State.ACTIVE){
                        phoneType = aCall.getPhone().getPhoneType();
                        if(phoneType == PhoneConstants.PHONE_TYPE_GSM){
                            log("CHLD 2 : switch GSM Active call to Hold and answer incoming call");
                            try{
                                aCall.getPhone().switchHoldingAndActive();
                            } catch (CallStateException ex){
                                log("CHLD 2 : catch CallStateException");
                                return false;
                            }
                            PhoneGlobals.getInstance().phoneMgr.answerRingingCall();
                            if(scenario == DT_EVDO_SCENARIO.DT_SCENARIO_A_IH){
                                Message msg = mHandler.obtainMessage(CDMA_SET_SECOND_CALL_STATE, true);
                                mHandler.sendMessage(msg);
                            }
                            return true;
                        }
                    }
                }
                log("CHLD 2 : N/A");
                return false;
            }

            case DT_SCENARIO_A_H:
            {
                for(Call aCall : fCalls){
                    if(aCall.getState() == Call.State.ACTIVE){
                        phoneType = aCall.getPhone().getPhoneType();
                        if(phoneType == PhoneConstants.PHONE_TYPE_GSM){
                            log("CHLD 2 : switch GSM Active call to Hold");
                            try{
                                aCall.getPhone().switchHoldingAndActive();
                            } catch (CallStateException ex){
                                log("CHLD 2 : catch CallStateException");
                                return false;
                            }
                            return true;
                        }
                    }
                }
                log("CHLD 2 : N/A");                
                return false;
            }

            case DT_SCENARIO_H_N:
            {
                log("CHLD 2 : switch GSM Hold call to Active ");
                PhoneUtils.switchHoldingAndActive(backgroundCall);
                return true;
            }

            case DT_SCENARIO_H_A:
            case DT_SCENARIO_AH_N:
            {
                log("CHLD 2 : swap Hold and Active calls ");
                PhoneUtils.switchHoldingAndActive(backgroundCall);
                return true;
            }

            case DT_SCENARIO_IA_H:
            case DT_SCENARIO_IAH_N:
            {
                for(Call aCall : fCalls){
                    if(aCall.getState() == Call.State.ACTIVE){
                        phoneType = aCall.getPhone().getPhoneType();
                        if(phoneType == PhoneConstants.PHONE_TYPE_GSM){
                            log("CHLD 2 : hangup GSM Active Call and answer incoming call");
                            try{
                                aCall.hangup();
                            } catch (CallStateException ex){
                                log("CHLD 2 : catch CallStateException");
                                return false;
                            }
                            // for GSM IA case, call.hangup() will directly pick up the incoming call, no need to answer again
                            if(scenario != DT_EVDO_SCENARIO.DT_SCENARIO_IA_H){
                                PhoneGlobals.getInstance().phoneMgr.answerRingingCall();
                            }
                            return true;
                        }
                    }
                }
                log("CHLD 2 : N/A");
                return false;
            }

            case DT_SCENARIO_AH_I:
            {
                PhoneGlobals.getInstance().phoneMgr.answerRingingCall();
                return true;
            }

            case DT_SCENARIO_IH_A:
            {
                log("CHLD 1 : answer ringing call and end active call");
                PhoneUtils.answerAndEndActive(mCM, ringingCall);
                return true;
            }
            
            default :
                log("CHLD 1 : wrong scenario");
                return false;

        }
    }

    private boolean evdoDTChldHandler3(DT_EVDO_SCENARIO scenario) {
        log("evdoDTChldHandler3 : scenario =" + scenario);

        switch(scenario){
            case DT_SCENARIO_N_A:
        //block cases which has incoming call in the front
//            case DT_SCENARIO_I_A:
            {
                CdmaPhoneCallState.PhoneCallState state = PhoneGlobals.getInstance().cdmaPhoneCallState.getCurrentCallState();
                if (state == CdmaPhoneCallState.PhoneCallState.THRWAY_ACTIVE) {

                    log("CHLD 3 : merge calls for CDMA THRWAY ACTIVE ");

                    PhoneUtils.mergeCalls();
                    return true;
                }
                log("CHLD 3 : do not merge call for CDMA not in THRWAY ACTIVE");
                return false;
            }
            case DT_SCENARIO_AH_N:
//            case DT_SCENARIO_AH_I:
//            case DT_SCENARIO_IAH_N:
            {
                log("CHLD 3 : merge calls ");

                PhoneUtils.mergeCalls();
                return true;                
            }

            default :
                log("CHLD 3 : wrong scenario ");
                return false;
        }
    }

    //handle CHLD commnad for EVDO + G project
    private boolean handleEVDOChld(int chld) {

        enforceCallingOrSelfPermission(MODIFY_PHONE_STATE, null);

        //To ease the maintain effort, we try to break down all the cases  and do seperate handling instead of trying to mix the similar ones up
        DT_EVDO_SCENARIO scenario = evdoAnalyzeDTScenario();
        boolean result;
        
        if (chld == CHLD_TYPE_RELEASEHELD) {
            result = evdoDTChldHandler0(scenario);
        } else if (chld == CHLD_TYPE_RELEASEACTIVE_ACCEPTHELD) {
            result = evdoDTChldHandler1(scenario);
        } else if (chld == CHLD_TYPE_HOLDACTIVE_ACCEPTHELD) {
            result = evdoDTChldHandler2(scenario);
        } else if (chld == CHLD_TYPE_ADDHELDTOCONF) {
            result = evdoDTChldHandler3(scenario);
        } else {
            log("CHLD : wrong parameter");
            result = false;
        }

        return result;

    }

    private boolean handleNormalChld(int chld) {
        enforceCallingOrSelfPermission(MODIFY_PHONE_STATE, null);
        Phone phone = mCM.getDefaultPhone();
        int phoneType = phone.getPhoneType();
        Call ringingCall = mCM.getFirstActiveRingingCall();
        Call backgroundCall = mCM.getFirstActiveBgCall();
        
        if (chld == CHLD_TYPE_RELEASEHELD) {
            if (ringingCall.isRinging()) {
                return PhoneUtils.hangupRingingCall(ringingCall);
            } else {
                return PhoneUtils.hangupHoldingCall(backgroundCall);
            }
        } else if (chld == CHLD_TYPE_RELEASEACTIVE_ACCEPTHELD) {
            if (phoneType == PhoneConstants.PHONE_TYPE_CDMA) {
                if (ringingCall.isRinging()) {
                    // Hangup the active call and then answer call waiting call.
                    if (VDBG) log("CHLD:1 Callwaiting Answer call");
                    PhoneUtils.hangupRingingAndActive(phone);
                } else {
                    // If there is no Call waiting then just hangup
                    // the active call. In CDMA this mean that the complete
                    // call session would be ended
                    if (VDBG) log("CHLD:1 Hangup Call");
                    PhoneUtils.hangup(PhoneGlobals.getInstance().mCM);
                }
                return true;
            } else if (phoneType == PhoneConstants.PHONE_TYPE_GSM) {
                // Hangup active call, answer held call
                return PhoneUtils.answerAndEndActive(PhoneGlobals.getInstance().mCM, ringingCall);
            } else {
                Log.e(TAG, "bad phone type: " + phoneType);
                return false;
            }
        } else if (chld == CHLD_TYPE_HOLDACTIVE_ACCEPTHELD) {
            if (phoneType == PhoneConstants.PHONE_TYPE_CDMA) {
                // For CDMA, the way we switch to a new incoming call is by
                // calling PhoneUtils.answerCall(). switchAndHoldActive() won't
                // properly update the call state within telephony.
                // If the Phone state is already in CONF_CALL then we simply send
                // a flash cmd by calling switchHoldingAndActive()
                if (ringingCall.isRinging()) {
                    if (VDBG) log("CHLD:2 Callwaiting Answer call");
                    PhoneUtils.answerCall(ringingCall);
                    PhoneUtils.setMute(false);
                    // Setting the second callers state flag to TRUE (i.e. active)
                    Message msg = mHandler.obtainMessage(CDMA_SET_SECOND_CALL_STATE, true);
                    mHandler.sendMessage(msg);
                    return true;
                } else if (PhoneGlobals.getInstance().cdmaPhoneCallState
                           .getCurrentCallState()
                           == CdmaPhoneCallState.PhoneCallState.CONF_CALL) {
                    if (VDBG) log("CHLD:2 Swap Calls");

                    /// M: fix CDMA Swap update too fast @{
                    // Toggle the second callers active state flag
                    Message msg = Message.obtain(mHandler, CDMA_SWAP_SECOND_CALL_STATE_BT);
                    /// @}

                    mHandler.sendMessage(msg);
                    return true;
                }
                Log.e(TAG, "CDMA fail to do hold active and accept held");
                return false;
            } else if (phoneType == PhoneConstants.PHONE_TYPE_GSM) {
                PhoneUtils.switchHoldingAndActive(backgroundCall);
                return true;
            } else {
                Log.e(TAG, "Unexpected phone type: " + phoneType);
                return false;
            }
        } else if (chld == CHLD_TYPE_ADDHELDTOCONF) {
            if (phoneType == PhoneConstants.PHONE_TYPE_CDMA) {
                CdmaPhoneCallState.PhoneCallState state =
                    PhoneGlobals.getInstance().cdmaPhoneCallState.getCurrentCallState();
                // For CDMA, we need to check if the call is in THRWAY_ACTIVE state
                if (state == CdmaPhoneCallState.PhoneCallState.THRWAY_ACTIVE) {
                    if (VDBG) log("CHLD:3 Merge Calls");
                    PhoneUtils.mergeCalls();
                    return true;
                }   else if (state == CdmaPhoneCallState.PhoneCallState.CONF_CALL) {
                    // State is CONF_CALL already and we are getting a merge call
                    // This can happen when CONF_CALL was entered from a Call Waiting
                    // TODO(BT)
                    return false;
                }
                Log.e(TAG, "GSG no call to add conference");
                return false;
            } else if (phoneType == PhoneConstants.PHONE_TYPE_GSM) {
                if (mCM.hasActiveFgCall() && mCM.hasActiveBgCall()) {
                    ///M: DAUL TALK SUPPORT  @{ 
                    if (true == FeatureOption.MTK_DT_SUPPORT) {
                        Phone fgPhone = mCM.getActiveFgCall().getPhone();
                        Phone bgPhone = mCM.getFirstActiveBgCall().getPhone();
                        boolean sameChannel = (fgPhone == bgPhone);
                        if (sameChannel) {
                            PhoneUtils.mergeCalls();
                        } else { // M: we could not merge calls in different SIM
                            Log.e(TAG, "calls are of different SIMs");
                            return false;
                        }
                    } else {
                        PhoneUtils.mergeCalls();
                    }
                    ///}@

                    return true;
                } else {
                    Log.e(TAG, "GSG no call to merge");
                    return false;
                }
            } else {
                Log.e(TAG, "Unexpected phone type: " + phoneType);
                return false;
            }                
        } else {
            Log.e(TAG, "bad CHLD value: " + chld);
            return false;
        }

    }

///}@

    private final IBluetoothHeadsetPhone.Stub mBinder = new IBluetoothHeadsetPhone.Stub() {
        public boolean answerCall() {
            enforceCallingOrSelfPermission(MODIFY_PHONE_STATE, null);
            if(FeatureOption.MTK_VT3G324M_SUPPORT && VTCallUtils.isVTRinging()){
                try{
                    PhoneGlobals.getInstance().touchAnswerVTCall();
                    return true;
                }catch(Exception ex){
                    log("Answer VT call cause exception : " + ex.toString());
                }
                return false;
            }else{
                return PhoneUtils.answerCall(mCM.getFirstActiveRingingCall());
            }
        }

        public boolean hangupCall() {
            enforceCallingOrSelfPermission(MODIFY_PHONE_STATE, null);
            if (mCM.hasActiveFgCall()) {
                return PhoneUtils.hangupActiveCall(mCM.getActiveFgCall());
            } else if (mCM.hasActiveRingingCall()) {
                return PhoneUtils.hangupRingingCall(mCM.getFirstActiveRingingCall());
            } else if (mCM.hasActiveBgCall()) {
                return PhoneUtils.hangupHoldingCall(mCM.getFirstActiveBgCall());
            }
            // TODO(BT) handle virtual voice call
            return false;
        }

        public boolean sendDtmf(int dtmf) {
            enforceCallingOrSelfPermission(MODIFY_PHONE_STATE, null);
            return mCM.sendDtmf((char) dtmf);
        }

        public boolean processChld(int chld) {

        ///M: DAUL TALK SUPPORT  @{ 
            if(FeatureOption.EVDO_DT_SUPPORT == true){
                return handleEVDOChld(chld);
            }else{
                return handleNormalChld(chld);
            }
        ///}@
        }

        public String getNetworkOperator() {
            enforceCallingOrSelfPermission(MODIFY_PHONE_STATE, null);

            /// M: GEMINI support @{
            int slotId = getDefaultSIMInternal();
            return CallManagerWrapper.getDefaultPhone(slotId).getServiceState().getOperatorAlphaLong();
            /// @}
        }

        public String getSubscriberNumber() {
            enforceCallingOrSelfPermission(MODIFY_PHONE_STATE, null);
            /// M: GEMINI support @{
            return TelephonyManagerWrapper.getLine1Number(null, getDefaultSIM());
            /// @}
        }

        public boolean listCurrentCalls() {
            enforceCallingOrSelfPermission(MODIFY_PHONE_STATE, null);
            Message msg = Message.obtain(mHandler, LIST_CURRENT_CALLS);
            mHandler.sendMessage(msg);
            return true;
        }

        public boolean queryPhoneState() {
            enforceCallingOrSelfPermission(MODIFY_PHONE_STATE, null);
            Message msg = Message.obtain(mHandler, QUERY_PHONE_STATE);
            mHandler.sendMessage(msg);
            return true;
        }

        public void updateBtHandsfreeAfterRadioTechnologyChange() {
            enforceCallingOrSelfPermission(MODIFY_PHONE_STATE, null);
            if (VDBG) Log.d(TAG, "updateBtHandsfreeAfterRadioTechnologyChange...");
            updateBtPhoneStateAfterRadioTechnologyChange();
        }

        public void cdmaSwapSecondCallState() {
            enforceCallingOrSelfPermission(MODIFY_PHONE_STATE, null);
            Message msg = Message.obtain(mHandler, CDMA_SWAP_SECOND_CALL_STATE);
            mHandler.sendMessage(msg);
        }

        public void cdmaSetSecondCallState(boolean state) {
            enforceCallingOrSelfPermission(MODIFY_PHONE_STATE, null);
            Message msg = mHandler.obtainMessage(CDMA_SET_SECOND_CALL_STATE, state);
            mHandler.sendMessage(msg);
        }
        
        /// M: For SIM Access @{
        public int getDefaultSIM(){
            enforceCallingOrSelfPermission(MODIFY_PHONE_STATE, null);
            return getDefaultSIMInternal();
        }
        /// @}

        ///M: DAUL TALK SUPPORT : for headset service to notify to do restriction when HFP first connecteds @{ 
        public void restrictMultitalks(){
            enforceCallingOrSelfPermission(MODIFY_PHONE_STATE, null);
            Message msg = Message.obtain(mHandler, RESTRICT_MULTITLAKS);
            mHandler.sendMessage(msg);            
        }
        ///}@
    };

    // match up with bthf_call_state_t of bt_hf.h
    final static int CALL_STATE_ACTIVE = 0;
    final static int CALL_STATE_HELD = 1;
    final static int CALL_STATE_DIALING = 2;
    final static int CALL_STATE_ALERTING = 3;
    final static int CALL_STATE_INCOMING = 4;
    final static int CALL_STATE_WAITING = 5;
    final static int CALL_STATE_IDLE = 6;

    // match up with bthf_chld_type_t of bt_hf.h
    final static int CHLD_TYPE_RELEASEHELD = 0;
    final static int CHLD_TYPE_RELEASEACTIVE_ACCEPTHELD = 1;
    final static int CHLD_TYPE_HOLDACTIVE_ACCEPTHELD = 2;
    final static int CHLD_TYPE_ADDHELDTOCONF = 3;

     /* Convert telephony phone call state into hf hal call state */
    static int convertCallState(Call.State ringingState, Call.State foregroundState) {
        int retval = CALL_STATE_IDLE;

        if ((ringingState == Call.State.INCOMING) ||
            (ringingState == Call.State.WAITING) )
            retval = CALL_STATE_INCOMING;
        else if (foregroundState == Call.State.DIALING)
            retval = CALL_STATE_DIALING;
        else if (foregroundState == Call.State.ALERTING)
            retval = CALL_STATE_ALERTING;
        else
            retval = CALL_STATE_IDLE;

        if (VDBG) {
            Log.v(TAG, "Call state Converted2: " + ringingState + "/" + foregroundState + " -> " +
                    retval);
        }
        return retval;
    }

    static int convertCallState(Call.State callState) {
        int retval = CALL_STATE_IDLE;

        switch (callState) {
        case IDLE:
        case DISCONNECTED:
        case DISCONNECTING:
            retval = CALL_STATE_IDLE;
            break;
        case ACTIVE:
            retval = CALL_STATE_ACTIVE;
            break;
        case HOLDING:
            retval = CALL_STATE_HELD;
            break;
        case DIALING:
            retval = CALL_STATE_DIALING;
            break;
        case ALERTING:
            retval = CALL_STATE_ALERTING;
            break;
        case INCOMING:
            retval = CALL_STATE_INCOMING;
            break;
        case WAITING:
            retval = CALL_STATE_WAITING;
            break;
        default:
            Log.e(TAG, "bad call state: " + callState);
            retval = CALL_STATE_IDLE;
            break;
        }

        if (VDBG) {
            Log.v(TAG, "Call state Converted2: " + callState + " -> " + retval);
        }

        return retval;
    }

    private static void log(String msg) {
        Log.d(TAG, msg);
    }

    /// M: GEMINI support @{
    public static int getSimCount(){
        int simCount = 1;
        if(FeatureOption.MTK_GEMINI_SUPPORT) {
            /// Default value is 2. Compatible with previous platform when this property does not exist.
            String value = SystemProperties.get(GEMINI_SIM_NUM, "2");
            simCount = Integer.parseInt(value);
        }else{
            return simCount; //For non-Gemini project, the SIM num is 1.
        }
        return simCount;
    }

    private int getDefaultSIMInternal() {
        final int firstSim = com.android.internal.telephony.PhoneConstants.GEMINI_SIM_1;
        int defaultSim = firstSim;
        int slotId = (int) Settings.System.getLong(getContentResolver(),
                                                   Settings.System.VOICE_CALL_SIM_SETTING,
                                                   Settings.System.DEFAULT_SIM_NOT_SET);

        log("getDefaultSIM : SIM ID=" + slotId);
        if (slotId == Settings.System.DEFAULT_SIM_NOT_SET
         || slotId == Settings.System.DEFAULT_SIM_SETTING_ALWAYS_ASK){
            log("No default SIM, get first inserted SIM");
            int simNum = getSimCount();
            try {
                /// M: Return first sim found inserted
                for (int simID = firstSim; simID < (firstSim + simNum); simID++) {
                    if (iTel.hasIccCard(simID)) {
                        log("getDefaultSim():first inserted SIM found ["+ (simID + 1) + "]");
                        defaultSim = simID;
                        break;
                    }
                }
                log("getDefaultSim():The default SIM is " + defaultSim);
            }catch (Exception ex){
                log("getDefaultSim():exception thrown [" + ex + ", default SIM set to GEMINI_SIM_1");
                defaultSim = firstSim;
            }
        }else{
            SIMInfo simInfo = SIMInfo.getSIMInfoById(this, slotId);
            if(simInfo != null)
            {
                defaultSim = simInfo.mSlot;
                log("getDefaultSIM : Sim Id in Settings.System.VOICE_CALL_SIM_SETTING=" + defaultSim);
            }
            else
            {
                log("getDefaultSIM : simInfo == null, use firstSim");
            }

        }
        return defaultSim;
    }

    private void registerPhoneEvents(boolean register){
        if(VDBG) Log.d(TAG, "registerPhoneEvents:" + register + " Gemini:" + FeatureOption.MTK_GEMINI_SUPPORT);
        if (register) {
            CallManagerWrapper.registerForPreciseCallStateChanged(mHandler,
                    PRECISE_CALL_STATE_CHANGED);
            CallManagerWrapper.registerForCallWaiting(mHandler, PHONE_CDMA_CALL_WAITING);
            CallManagerWrapper.registerForIncomingRing(mHandler, PHONE_INCOMING_RING);
            CallManagerWrapper.registerForDisconnect(mHandler, PHONE_ON_DISCONNECT);

            /// M: Vedio Call support @{
            if (FeatureOption.MTK_VT3G324M_SUPPORT) {
                CallManagerWrapper.registerForVtRingInfo(mHandler, PHONE_VT_RING_INFO);
            }
            /// @}
        } else {
            CallManagerWrapper.unregisterForPreciseCallStateChanged(mHandler);
            CallManagerWrapper.unregisterForCallWaiting(mHandler);
            CallManagerWrapper.unregisterForIncomingRing(mHandler);
            CallManagerWrapper.unregisterForDisconnect(mHandler);

            /// M: Vedio Call support @{
            if (FeatureOption.MTK_VT3G324M_SUPPORT) {
                CallManagerWrapper.unregisterForVtRingInfo(mHandler);
            }
            /// @}
        }
    }
    /// @}

}
