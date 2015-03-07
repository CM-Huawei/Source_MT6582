/*
 * Copyright (C) 2008 The Android Open Source Project
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

package com.android.keyguard;

import android.content.Context;
import android.content.Intent;
import android.os.PowerManager;
import android.os.SystemClock;
import android.os.Bundle;
import android.os.UserHandle;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.telephony.ServiceState;
import android.telephony.TelephonyManager;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.widget.Button;

import com.android.keyguard.KeyguardSecurityModel.SecurityMode;
import com.android.internal.telephony.ITelephony;
import com.android.internal.telephony.IccCardConstants;
import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.IccCardConstants.State;
import com.android.internal.widget.LockPatternUtils;

import com.mediatek.keyguard.ext.IEmergencyButtonExt;
import com.mediatek.keyguard.ext.KeyguardPluginFactory;
import com.mediatek.common.telephony.ITelephonyEx;

/**
 * This class implements a smart emergency button that updates itself based
 * on telephony state.  When the phone is idle, it is an emergency call button.
 * When there's a call in progress, it presents an appropriate message and
 * allows the user to return to the call.
 */
public class EmergencyButton extends Button {

    private static final int EMERGENCY_CALL_TIMEOUT = 10000; // screen timeout after starting e.d.
    private static final String ACTION_EMERGENCY_DIAL = "com.android.phone.EmergencyDialer.DIAL";
    /// M: CTA new feature
    private static final String TAG = "EmergencyButton";

    KeyguardUpdateMonitorCallback mInfoCallback = new KeyguardUpdateMonitorCallback() {

        @Override
        public void onSimStateChanged(State simState, int simId) {
            int phoneState = KeyguardUpdateMonitor.getInstance(mContext).getPhoneState();
            updateEmergencyCallButton(phoneState);
        }

        void onPhoneStateChanged(int phoneState) {
            updateEmergencyCallButton(phoneState);
        }
        

        /// M: CTA new feature @{
        @Override
        public void onRefreshCarrierInfo(CharSequence plmn, CharSequence spn, int simId) {
            KeyguardUtils.xlogD(TAG, "onRefreshCarrierInfo plmn=" + plmn + ", spn=" + spn + ", simId=" + simId);
            int phoneState = KeyguardUpdateMonitor.getInstance(mContext).getPhoneState();
            updateEmergencyCallButton(phoneState);
        }
        /// M @}
    };
    private LockPatternUtils mLockPatternUtils;
    private PowerManager mPowerManager;

    /// M: For the extra info of the intent to start emergency dialer
    private IEmergencyButtonExt mEmergencyButtonExt;

    public EmergencyButton(Context context) {
        this(context, null);
    }

    public EmergencyButton(Context context, AttributeSet attrs) {
        super(context, attrs);
        /// M: Init keyguard operator plugin @{
        try {
            mEmergencyButtonExt = KeyguardPluginFactory.getEmergencyButtonExt(context);
        } catch (Exception e) {
            e.printStackTrace();
        }
        /// @}
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        KeyguardUpdateMonitor.getInstance(mContext).registerCallback(mInfoCallback);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        KeyguardUpdateMonitor.getInstance(mContext).removeCallback(mInfoCallback);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mLockPatternUtils = new LockPatternUtils(mContext);
        mPowerManager = (PowerManager) mContext.getSystemService(Context.POWER_SERVICE);
        setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                takeEmergencyCallAction();
            }
        });
        int phoneState = KeyguardUpdateMonitor.getInstance(mContext).getPhoneState();
        
        /// M: Save secure query result here, when lockscreen is created, secure result should
        /// stay unchanged @{
        mIsSecure = mLockPatternUtils.isSecure();
        /// @}
        
        updateEmergencyCallButton(phoneState);
    }

    /**
     * Shows the emergency dialer or returns the user to the existing call.
     */
    public void takeEmergencyCallAction() {
        // TODO: implement a shorter timeout once new PowerManager API is ready.
        // should be the equivalent to the old userActivity(EMERGENCY_CALL_TIMEOUT)
        mPowerManager.userActivity(SystemClock.uptimeMillis(), true);
        if (TelephonyManager.getDefault().getCallState()
                == TelephonyManager.CALL_STATE_OFFHOOK) {
            mLockPatternUtils.resumeCall();
        } else {
            final boolean bypassHandler = true;
            KeyguardUpdateMonitor.getInstance(mContext).reportEmergencyCallAction(bypassHandler);
            Intent intent = new Intent(ACTION_EMERGENCY_DIAL);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                    | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
            /// M: Fill the extra info the intent to start emergency dialer.
            mEmergencyButtonExt.addSlotIdForEmergencyDialer(intent, getCurSlotId());
            getContext().startActivityAsUser(intent,
                    new UserHandle(mLockPatternUtils.getCurrentUser()));
        }
    }

    void updateEmergencyCallButton(int phoneState) {
        boolean enabled = false;
        if (phoneState == TelephonyManager.CALL_STATE_OFFHOOK) {
            enabled = true; // always show "return to call" if phone is off-hook
        } else if (mLockPatternUtils.isEmergencyCallCapable()) {
            boolean simLocked = KeyguardUpdateMonitor.getInstance(mContext).isSimLocked();
            if (simLocked) {
                // Some countries can't handle emergency calls while SIM is locked.
                enabled = mLockPatternUtils.isEmergencyCallEnabledWhileSimLocked();
            } else {
                // True if we need to show a secure screen (pin/pattern/SIM pin/SIM puk);
                // hides emergency button on "Slide" screen if device is not secure.
                /// M: Optimization, do not query db for secure state in every ECC update, 
                /// only query once when view creation is done @{
                // enabled = mLockPatternUtils.isSecure();
                enabled = mIsSecure;
                /// @}
            }
        }
        /// M: If antitheft lock is on, we should also show ECC button @{
        boolean antiTheftLocked = AntiTheftManager.isAntiTheftLocked();
        /// M: Always show ECC button on slide lockscreen or not
        boolean keyguardUtilShowEcc = mEmergencyButtonExt.enableEccOnSlide();
        /// M:CTA new feature
        boolean eccShouldShow = eccButtonShouldShow();
        enabled = (enabled || keyguardUtilShowEcc || antiTheftLocked) && eccShouldShow;
                Log.i(TAG, "enabled= " + enabled + ", antiTheftLocked=" + antiTheftLocked + ", keyguardUtilShowEcc="
                + keyguardUtilShowEcc + ", eccShouldShow=" + eccShouldShow);
        /// @}
        mLockPatternUtils.updateEmergencyCallButtonState(this, phoneState, enabled, false);
    }


    /// M: CTA new feature
    private boolean eccButtonShouldShow(){
        Bundle bd = null;
        int maxSimId = KeyguardUtils.getMaxSimId();
        boolean[] isServiceSupportEcc = new boolean[maxSimId + 1];

        try {
            ITelephonyEx phoneEx = ITelephonyEx.Stub.asInterface(
                ServiceManager.checkService("phoneEx"));

            if (phoneEx != null) {
                for (int i = PhoneConstants.GEMINI_SIM_1; i <= KeyguardUtils.getMaxSimId(); i++) {
                    bd = phoneEx.getServiceState(i);
                    ServiceState ss = ServiceState.newFromBundle(bd);
                    Log.i(TAG, "ss.getState() = " + ss.getState()+" ss.isEmergencyOnly()="+ss.isEmergencyOnly()+" for simId="+i);
                    if (ServiceState.STATE_IN_SERVICE == ss.getState() || ss.isEmergencyOnly()) {  //Full service or Limited service
                        isServiceSupportEcc[i] = true;
                    } else {
                        isServiceSupportEcc[i] = false;
                    }
                }
            }
        } catch (RemoteException e) {
            Log.i(TAG, "getServiceState error e:" + e.getMessage());
        }

        return mEmergencyButtonExt.isSimInService(isServiceSupportEcc, getCurSlotId());
    }
    ///  @}

    /// M: Optimization, save lockpatternUtils's isSecure state
    private boolean mIsSecure;

    /**
     * M: Add for operator customization.
     * Get current sim slot id of PIN/PUK lock via security mode.
     *
     * @return Current sim slot id,
     *      return 0-3, current lockscreen is PIN/PUK,
     *      return other values, current lockscreen is not PIN/PUK.
     */
    private int getCurSlotId() {
        KeyguardSecurityModel securityModel = new KeyguardSecurityModel(mContext);
        return securityModel.getSecurityMode().ordinal() - SecurityMode.SimPinPukMe1.ordinal();
    }
}
