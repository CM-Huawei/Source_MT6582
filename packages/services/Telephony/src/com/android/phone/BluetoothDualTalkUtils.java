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

import android.util.Log;
import com.android.internal.telephony.Call;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.CallManager;
import com.mediatek.phone.DualTalkUtils;
import java.util.List;

/**
 * Phone/telephony related utilities functions for dual talk to use
 * @hide
 */
public class BluetoothDualTalkUtils {

    private static final String TAG = "BluetoothDualTalkUtils";
    private CallManager mCM;
    private DualTalkUtils mDualTalk;

    //constructor
    public BluetoothDualTalkUtils(CallManager cm, DualTalkUtils dtu) {
        mCM = cm;
        mDualTalk = dtu;
    }

    public Call getActiveFgCall(){
        log("[getActiveFgCall]");

        Call call = null;
        List<Call> listCall = mCM.getForegroundCalls();
        log("getActiveFgCall: foregroundCalls="+ listCall);

        /// C+G
        if(isGsmActive() && isCdmaActive()){
	    ///   if there's an active GSM call, then choose the active GSM call.
        ///      otherwise, choose the CDMA call.
            Call gsmCall = null, cdmaCall = null;
            for(Call c : listCall){
           	    int phoneType = c.getPhone().getPhoneType();
                if((PhoneConstants.PHONE_TYPE_GSM == phoneType) && (Call.State.IDLE != c.getState())){
                    gsmCall = c;
                }

                if(PhoneConstants.PHONE_TYPE_CDMA == phoneType){
                    cdmaCall = c;
                }
            }
		    call = (null != gsmCall) ? gsmCall : cdmaCall;
            //dumpCallDetails("getActiveFgCall: call", call);
        }
	     
        if(null == call)
            call = mCM.getActiveFgCall();

        log("[[getActiveFgCall]]");
        return call;
    }

    public Call getFirstFgCall(int type){
        log("[getFirstActiveFgCall]");
        log("getFirstActiveFgCall: type="+type);
    
        Call call = null;
        if(isSupportPhone(type)){
            List<Call> listCall = null;
    
        listCall = mCM.getForegroundCalls();
        log("getFirstActiveFgCall: foregroundCalls="+listCall);
        for(Call c : listCall){
            if(type == c.getPhone().getPhoneType())
                call = c;
              }
        }else{
            log("getFirstActiveFgCall: no match type!");
        }
    
        //dumpCallDetails("getFirstActiveFgCall: c=", call);
        log("[[getFirstActiveFgCall]]");
        return call;
    }
    
    public Call getFirstBgCall(int type){
        log("[getFirstBgCall]");
        log("getFirstBgCall: type="+type);
    
        Call call = null;
        if(isSupportPhone(type)){
            List<Call> listCall = null;
    
        listCall = mCM.getBackgroundCalls();
        log("getFirstBgCall: backgroundCalls="+listCall);
        for(Call c : listCall){
            if(type == c.getPhone().getPhoneType())
                call = c;
            }
        }else{
            log("getFirstBgCall: no match type!");
        }
    
        //dumpCallDetails("getFirstBgCall: c=", call);
        log("[[getFirstBgCall]]");
        return call;
    }


    public Call getFirstActiveBgCall(){
        log("[getFirstActiveBgCall]");
        Call call = null;
        List<Call> listCall = mCM.getBackgroundCalls();
        log("getFirstActiveBgCall: backgroundCalls="+ listCall);
        /// C+G
        if(isGsmActive() && isCdmaActive()){
            Call gsmFgCall=null, gsmBgCall=null;
    
            gsmFgCall = getFirstFgCall(PhoneConstants.PHONE_TYPE_GSM);
            gsmBgCall = getFirstBgCall(PhoneConstants.PHONE_TYPE_GSM);
    
            if(Call.State.IDLE != gsmFgCall.getState()){
                if(gsmBgCall.getState().isAlive()){
                    /// GSM: AH --> get GSM's H
                    call = gsmBgCall;
                }else{
                    /// GSM: A
                    /// CDMA: any
                    call = getFirstFgCall(PhoneConstants.PHONE_TYPE_CDMA);
                }
            }else{
                call = gsmBgCall;
            }
        }
    
        if(null == call)
        call = mCM.getFirstActiveBgCall();
    
        log("[[getFirstActiveBgCall]]");
        return call;
    
    }


    public Call getFirstActiveRingingCall(){
        return mCM.getFirstActiveRingingCall();
    }

    public Call getSecondActiveRingCall() {
        return mDualTalk.getSecondActiveRingCall();
    }

    public List<Call> getRingingCalls() {
        return mCM.getRingingCalls();
    }

    public boolean isSupportPhone(int type){
        boolean bSupportPhoneType = false;
    
        if(type == PhoneConstants.PHONE_TYPE_CDMA|| type == PhoneConstants.PHONE_TYPE_GSM){
            bSupportPhoneType = true;
        }
    
        return bSupportPhoneType;
    }

    public boolean isPhoneActive(int type){
        log("[isPhoneActive]");
        log("isPhoneActive: type="+type);
    
        boolean bPhoneActive = false;
        if(type == PhoneConstants.PHONE_TYPE_CDMA || type == PhoneConstants.PHONE_TYPE_GSM){
            List<Call> listCall = null;
    
            if(!bPhoneActive){
                listCall = mCM.getForegroundCalls();
                log("isPhoneActive: foregroundCalls="+listCall);
                for(Call c : listCall){
                    if(type == c.getPhone().getPhoneType() && Call.State.IDLE != c.getState())
                        bPhoneActive = true;
                }
            }
    
            if(!bPhoneActive){
                listCall = mCM.getBackgroundCalls();
                log("isPhoneActive: backgroundCalls="+listCall);
                for(Call c : listCall){
                    if(type == c.getPhone().getPhoneType() && Call.State.IDLE != c.getState())
                        bPhoneActive = true;        
                }
            }
        
            if(!bPhoneActive){
                listCall = mCM.getRingingCalls();
                log("isPhoneActive: ringingCalls="+listCall);
                for(Call c : listCall){
                    if(type == c.getPhone().getPhoneType() && Call.State.IDLE != c.getState())
                        bPhoneActive = true;
                }
            }
        }else{
            log("isPhoneActive: no match type!");
        }
    
        log("isPhoneActive: bPhoneActive="+bPhoneActive);
        log("[[isPhoneActive]]");
        return bPhoneActive;
    }
    
    public boolean isGsmActive(){
        return isPhoneActive(PhoneConstants.PHONE_TYPE_GSM);
    }
    
    public boolean isCdmaActive(){
        return isPhoneActive(PhoneConstants.PHONE_TYPE_CDMA);
    }

    public boolean hangupRingingCall(Call ringing) {
        return PhoneUtils.hangupRingingCall(ringing);
    }

    boolean hangupHoldingCall(Call background) {
        return PhoneUtils.hangupHoldingCall(background);
    }

    private void log(String msg) {
        Log.d(TAG, msg);
    }
}

