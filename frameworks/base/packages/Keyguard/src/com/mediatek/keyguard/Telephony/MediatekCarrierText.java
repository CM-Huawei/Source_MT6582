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

package com.android.keyguard;

import android.content.Context;
import android.net.ConnectivityManager;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.android.internal.telephony.IccCardConstants;
import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.IccCardConstants.State;
import com.android.internal.widget.LockPatternUtils;

import com.mediatek.keyguard.ext.KeyguardPluginFactory;
import com.mediatek.keyguard.ext.ICarrierTextExt;
import com.mediatek.keyguard.ext.IOperatorSIMString;
import com.mediatek.keyguard.ext.IOperatorSIMString.SIMChangedTag;

//import com.android.internal.R;

public class MediatekCarrierText extends LinearLayout {
    private static final String TAG = "MediatekCarrierText";
    private static CharSequence mSeparator;

    private LockPatternUtils mLockPatternUtils;

    /// M: Support GeminiPlus
    private TextView mCarrierView[];
    private TextView mCarrierDivider[];
    private State mSimState[];
    private StatusMode mStatusMode[];
    private CharSequence mPlmn[];
    private CharSequence mSpn[];
    private CharSequence mHnbName[];
    private CharSequence mCsgId[];
    
    /// M: To get the proper SIM UIM string according to operator. 
    private IOperatorSIMString mIOperatorSIMString;
    /// M: To changed the plmn  CHINA TELECOM to China Telecom,just for CT feature
    private ICarrierTextExt mCarrierTextExt;
    
    private KeyguardUpdateMonitorCallback mCallback = new KeyguardUpdateMonitorCallback() {
        @Override
        public void onRefreshCarrierInfo(CharSequence plmn, CharSequence spn, int simId) {
            KeyguardUtils.xlogD(TAG, "onRefreshCarrierInfo plmn=" + plmn + ", spn=" + spn + ", simId=" + simId);
            if (KeyguardUtils.isValidSimId(simId)) {
                mPlmn[simId] = plmn;
                mSpn[simId] = spn;
                mHnbName[simId] = KeyguardUpdateMonitor.getInstance(mContext).getTelephonyHnbName(simId);
                mCsgId[simId] = KeyguardUpdateMonitor.getInstance(mContext).getTelephonyCsgId(simId);
                updateCarrierText(mSimState[simId], mPlmn[simId], mSpn[simId], simId);
            }
        }
        
        @Override
        public void onSimStateChanged(IccCardConstants.State simState, int simId) {
            KeyguardUtils.xlogD(TAG, "onSimStateChanged simId=" + simId + ", mSimState=" + simState);
            if (KeyguardUtils.isValidSimId(simId)) {
                mSimState[simId] = simState;
                updateCarrierText(mSimState[simId], mPlmn[simId], mSpn[simId], simId);
            }
        }
        
        @Override
        public void onSearchNetworkUpdate(int simId, boolean switchOn) {
            KeyguardUtils.xlogD(TAG, "onSearchNetworkUpdate simId=" + simId + ", switchOn=" + switchOn);
            if (!KeyguardUtils.isValidSimId(simId)) {
                simId = PhoneConstants.GEMINI_SIM_1;
            }
            if (switchOn) {
                String carrierText = getContext().getString(R.string.network_searching);
                mStatusMode[simId] = StatusMode.NetworkSearching;
                updateCarrierTextForSearchNetwork(carrierText, simId);
            } else {
                mStatusMode[simId] = getStatusForIccState(mSimState[simId]);
                updateCarrierText(mSimState[simId], mPlmn[simId], mSpn[simId], simId);
            }
        }
        /// Mediatek add end@}
    };

    /**
     * The status of this lock screen. Primarily used for widgets on LockScreen.
     */
    private static enum StatusMode {
        Normal, // Normal case (sim card present, it's not locked)
        NetworkLocked, // SIM card is 'network locked'.
        SimMissing, // SIM card is missing.
        SimMissingLocked, // SIM card is missing, and device isn't provisioned; don't allow access
        SimPukLocked, // SIM card is PUK locked because SIM entered wrong too many times
        SimLocked, // SIM card is currently locked
        SimPermDisabled, // SIM card is permanently disabled due to PUK unlock failure
        SimNotReady, // SIM is not ready yet. May never be on devices w/o a SIM.
        
        /// M: mediatek add sim state
        SimUnknown,
        NetworkSearching;  //The sim card is ready, but searching network
    }

    /// M: Support GeminiPlus
    private void initMembers() {
        mCarrierView = new TextView[4];
        mCarrierDivider = new TextView[3];
        mStatusMode = new StatusMode[KeyguardUtils.getNumOfSim()];
        mSimState = new State[KeyguardUtils.getNumOfSim()];
        mPlmn = new CharSequence[KeyguardUtils.getNumOfSim()];
        mSpn = new CharSequence[KeyguardUtils.getNumOfSim()];
        mHnbName = new CharSequence[KeyguardUtils.getNumOfSim()];
        mCsgId = new CharSequence[KeyguardUtils.getNumOfSim()];
        for (int i = PhoneConstants.GEMINI_SIM_1; i <= KeyguardUtils.getMaxSimId(); i++) {
            mStatusMode[i] = StatusMode.Normal;
            mSimState[i] = IccCardConstants.State.READY;
            mPlmn[i] = null;
            mSpn[i] = null;
            mHnbName[i] = null;
            mCsgId[i] = null;
        }
    }
    
    public MediatekCarrierText(Context context) {
        this(context, null);
        initMembers();
    }

    public MediatekCarrierText(Context context, AttributeSet attrs) {
        super(context, attrs);
        initMembers();
        LayoutInflater inflater = (LayoutInflater) context.getSystemService(
                Context.LAYOUT_INFLATER_SERVICE);
        if (KeyguardUtils.isGemini()) {
            inflater.inflate(R.layout.mtk_carrier_view_gemini, this, true);
        } else {
            inflater.inflate(R.layout.mtk_carrier_view, this, true);
        }
        mLockPatternUtils = new LockPatternUtils(mContext);
        
        /// M: Init the plugin for changing the String with SIM according to Operator.
        mIOperatorSIMString = KeyguardPluginFactory.getOperatorSIMString(mContext);
        mCarrierTextExt = KeyguardPluginFactory.getCarrierTextExt(mContext);
    }
    
    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        
        mSeparator = getResources().getString(R.string.kg_text_message_separator);
        if (KeyguardUtils.isGemini()) {
            mCarrierView[PhoneConstants.GEMINI_SIM_1] = (TextView)findViewById(R.id.carrier);
            mCarrierView[PhoneConstants.GEMINI_SIM_2] = (TextView)findViewById(R.id.carrierGemini);
            mCarrierView[PhoneConstants.GEMINI_SIM_3] = (TextView) findViewById(R.id.carrierGemini3);
            mCarrierView[PhoneConstants.GEMINI_SIM_4] = (TextView) findViewById(R.id.carrierGemini4);
            mCarrierDivider[PhoneConstants.GEMINI_SIM_1] = (TextView) findViewById(R.id.carrierDivider);
            mCarrierDivider[PhoneConstants.GEMINI_SIM_2] = (TextView) findViewById(R.id.carrierDivider2);
            mCarrierDivider[PhoneConstants.GEMINI_SIM_3] = (TextView) findViewById(R.id.carrierDivider3);
            mCarrierView[PhoneConstants.GEMINI_SIM_1].setSelected(true);
            mCarrierView[PhoneConstants.GEMINI_SIM_2].setSelected(true);
            mCarrierView[PhoneConstants.GEMINI_SIM_3].setSelected(true);
            mCarrierView[PhoneConstants.GEMINI_SIM_4].setSelected(true);
        } else {
            mCarrierView[PhoneConstants.GEMINI_SIM_1] = (TextView)findViewById(R.id.carrier);
            mCarrierView[PhoneConstants.GEMINI_SIM_1].setSelected(true); // Allow marquee to work.
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        KeyguardUpdateMonitor.getInstance(mContext).registerCallback(mCallback);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        KeyguardUpdateMonitor.getInstance(mContext).removeCallback(mCallback);
    }

    /**
     * Top-level function for creating carrier text. Makes text based on simState, PLMN
     * and SPN as well as device capabilities, such as being emergency call capable.
     *
     * @param simState
     * @param plmn
     * @param spn
     * @param hnbName
     * @param csgid
     * @return
     */
    private CharSequence getCarrierTextForSimState(IccCardConstants.State simState,
            CharSequence plmn, CharSequence spn, CharSequence hnbName, CharSequence csgId, int simId) {
        
        /// M: Onle set plmn to default value if both plmn and spn are null
        if (plmn == null && spn == null) {
            plmn = getDefaultPlmn();
        }

        CharSequence carrierText = null;
        StatusMode status = getStatusForIccState(simState);
        switch (status) {
            case SimUnknown:
            case Normal:
                carrierText = concatenate(plmn, spn);
                carrierText = appendCsgInfo(carrierText, hnbName, csgId);
                break;

            case SimNotReady:
                carrierText = concatenate(plmn, spn);
                carrierText = appendCsgInfo(carrierText, hnbName, csgId);
                break;

            case NetworkLocked:
                carrierText = makeCarrierStringOnEmergencyCapable(
                        mContext.getText(R.string.keyguard_network_locked_message),
                        plmn, hnbName, csgId);
                break;

            case SimMissing:
                // Shows "No SIM card | Emergency calls only" on devices that are voice-capable.
                // This depends on mPlmn containing the text "Emergency calls only" when the radio
                // has some connectivity. Otherwise, it should be null or empty and just show
                // "No SIM card"
                CharSequence simMessage = getContext().getText(R.string.keyguard_missing_sim_message_short);
                carrierText = makeCarrierStringOnEmergencyCapable(simMessage, plmn, hnbName, csgId);
                carrierText = mCarrierTextExt.getTextForSimMissing(simMessage, carrierText, simId);
                break;

            case SimPermDisabled:
                carrierText = getContext().getText(R.string.keyguard_permanent_disabled_sim_message_short);
                break;

            case SimMissingLocked:
                carrierText =  makeCarrierStringOnEmergencyCapable(
                        getContext().getText(R.string.keyguard_missing_sim_message_short),
                        plmn, hnbName, csgId);
                break;

            case SimLocked:
                carrierText = makeCarrierStringOnEmergencyCapable(
                        getContext().getText(R.string.keyguard_sim_locked_message),
                        plmn, hnbName, csgId);
                break;

            case SimPukLocked:
                carrierText = makeCarrierStringOnEmergencyCapable(
                        getContext().getText(R.string.keyguard_sim_puk_locked_message),
                        plmn, hnbName, csgId);
                break;
        }

        KeyguardUtils.xlogD(TAG, "getCarrierTextForSimState simState=" + simState + 
            " PLMN=" + plmn + " SPN=" + spn + " HNB=" + hnbName+ " CSG=" + csgId+ " carrierText=" + carrierText);
        return carrierText;
    }

    /*
     * Add emergencyCallMessage to carrier string only if phone supports emergency calls.
     */
    private CharSequence makeCarrierStringOnEmergencyCapable(
            CharSequence simMessage, CharSequence emergencyCallMessage,
            CharSequence hnbName, CharSequence csgId) {

        CharSequence emergencyCallMessageExtend = emergencyCallMessage;
        if (!TextUtils.isEmpty(emergencyCallMessage)) {
            emergencyCallMessageExtend = appendCsgInfo(emergencyCallMessage, hnbName, csgId);
        }
        
        if (mLockPatternUtils.isEmergencyCallCapable()) {
            return concatenate(simMessage, emergencyCallMessageExtend);
        }
        return simMessage;
    }

    /**
     * Determine the current status of the lock screen given the SIM state and other stuff.
     */
    private StatusMode getStatusForIccState(IccCardConstants.State simState) {
        // Since reading the SIM may take a while, we assume it is present until told otherwise.
        if (simState == null) {
            return StatusMode.SimUnknown;
        }

        final boolean missingAndNotProvisioned =
                !KeyguardUpdateMonitor.getInstance(mContext).isDeviceProvisioned()
                && (simState == IccCardConstants.State.ABSENT ||
                        simState == IccCardConstants.State.PERM_DISABLED);

        // M: Directly maps missing and not Provisioned to SimMissingLocked Status.
        if (missingAndNotProvisioned) {
            return StatusMode.SimMissingLocked;
        }
        //simState = missingAndNotProvisioned ? IccCardConstants.State.NETWORK_LOCKED : simState;
        //@}

        switch (simState) {
            case ABSENT:
                return StatusMode.SimMissing;
            case NETWORK_LOCKED:
                // M: correct IccCard state NETWORK_LOCKED maps to NetowrkLocked.
                return StatusMode.NetworkLocked;
            case NOT_READY:
                return StatusMode.SimNotReady;
            case PIN_REQUIRED:
                return StatusMode.SimLocked;
            case PUK_REQUIRED:
                return StatusMode.SimPukLocked;
            case READY:
                return StatusMode.Normal;
            case PERM_DISABLED:
                return StatusMode.SimPermDisabled;
            case UNKNOWN:
                return StatusMode.SimUnknown;
        }
        return StatusMode.SimMissing;
    }

    private static CharSequence concatenate(CharSequence str1, CharSequence str2) {
        final boolean str1Valid = !TextUtils.isEmpty(str1);
        final boolean str2Valid = !TextUtils.isEmpty(str2);
        if (str1Valid && str2Valid) {
            return new StringBuilder().append(str1).append(mSeparator).append(str2).toString();
        } else if (str1Valid) {
            return str1;
        } else if (str2Valid) {
            return str2;
        } else {
            return "";
        }
    }

    
    protected void updateCarrierText(State simState, CharSequence plmn, CharSequence spn, int simId) {
        KeyguardUtils.xlogD(TAG, "updateCarrierText, simState=" + simState + " plmn=" + plmn + " spn=" + spn +" simId=" + simId);
        TextView toSetCarrierView;
        
        toSetCarrierView = mCarrierView[simId];
        if (StatusMode.NetworkSearching == mStatusMode[simId]) {
            KeyguardUtils.xlogD(TAG, "updateCarrierText, searching network now, don't interrupt it, simState=" + simState);
            return;
        }
        /// M: save statu mode, which will be used to decide show or hide carrier view
        mStatusMode[simId] = getStatusForIccState(simState);

        if (isWifiOnlyDevice()) {
            KeyguardUtils.xlogD(TAG, "updateCarrierText WifiOnly");
            mCarrierView[PhoneConstants.GEMINI_SIM_1].setVisibility(View.GONE);
            return;
        }

        showOrHideCarrier();

        CharSequence text = getCarrierTextForSimState(simState, plmn, spn, mHnbName[simId], mCsgId[simId], simId);
        /// M: Change the String with SIM according to Operator.
        text = mIOperatorSIMString.getOperatorSIMString(text.toString(), simId, SIMChangedTag.DELSIM, mContext);
        
        toSetCarrierView.setText(text != null ? mCarrierTextExt.changedPlmnToCapitalize(text.toString()) : null);
    }

    /**
     * M: Used to check weather this device is wifi only.
     */
    private boolean isWifiOnlyDevice() {
        ConnectivityManager cm = (ConnectivityManager)getContext().getSystemService(Context.CONNECTIVITY_SERVICE);
        return  !(cm.isNetworkSupported(ConnectivityManager.TYPE_MOBILE));
    }
    
    /**
     * M: Used to control carrier TextView visibility in Gemini.
     * (1) if the device is wifi only, we hide both carrier TextView.
     * (2) if both sim are missing, we shwon only one carrier TextView center.
     * (3) if either one sim is missing, we shwon the visible carrier TextView center.
     * (4) if both sim are not missing, we shwon boteh TextView, one in the left the other right.
     */
    /// M: Support GeminiPlus
    private void showOrHideCarrier() {
        if (isWifiOnlyDevice()) {
            for (int i = PhoneConstants.GEMINI_SIM_1; i <= KeyguardUtils.getMaxSimId(); i++) {
                if(mCarrierView[i] != null) {
                    mCarrierView[i].setVisibility(View.GONE);
                }
            }

            for (int i = PhoneConstants.GEMINI_SIM_1; i <= KeyguardUtils.getMaxSimId()-1; i++) {
                if(mCarrierDivider[i] != null) {
                    mCarrierDivider[i].setVisibility(View.GONE);
                }
            }
        } else {
            int mNumOfSIM = 0;
            TextView mCarrierLeft = null;
            TextView mCarrierRight = null;

            for (int i = PhoneConstants.GEMINI_SIM_1; i <= KeyguardUtils.getMaxSimId()-1; i++) {
                if(mCarrierDivider[i] != null) {
                    mCarrierDivider[i].setVisibility(View.GONE);
                }
            }

            for (int i = PhoneConstants.GEMINI_SIM_1; i <= KeyguardUtils.getMaxSimId(); i++) {
                final boolean simMissing = (mStatusMode[i] == StatusMode.SimMissing
                    || mStatusMode[i] == StatusMode.SimMissingLocked
                    || mStatusMode[i] == StatusMode.SimUnknown
                    || !KeyguardDialogManager.isSIMInserted(i));
                if(!simMissing) {
                    if(mCarrierView[i] != null) {
                        mCarrierView[i].setVisibility(View.VISIBLE);
                    }                        
                    mNumOfSIM++;
                    if(mNumOfSIM == 1) {
                        mCarrierLeft = mCarrierView[i];
                    } else if(mNumOfSIM == 2) {
                        mCarrierRight = mCarrierView[i];
                    }
                    if(mNumOfSIM >= 2 && ((i - 1) >= 0) && (mCarrierDivider != null)) {
                        mCarrierDivider[i-1].setVisibility(View.VISIBLE);
                        mCarrierDivider[i-1].setText("|");
                    }
                } else {
                    if(mCarrierView[i] != null) {
                        mCarrierView[i].setVisibility(View.GONE);
                    }
                }
                if(mCarrierView[i] != null) {
                    mCarrierView[i].setGravity(Gravity.CENTER);
                }
            }

            if(mNumOfSIM == 2) {
                if(mCarrierLeft != null) {
                    mCarrierLeft.setGravity(Gravity.END);
                }
                if(mCarrierRight != null) {
                    mCarrierRight.setGravity(Gravity.START);
                }
            } else if(mNumOfSIM == 0) {
                if(mCarrierView[PhoneConstants.GEMINI_SIM_1] != null) {
                    mCarrierView[PhoneConstants.GEMINI_SIM_1].setVisibility(View.VISIBLE);
                }
                KeyguardUtils.xlogD(TAG, "updateOperatorInfo, force the slotId 0 to visible.");
            }
        }
    }

    private void updateCarrierTextForSearchNetwork(String carrierText, int simId) {
        KeyguardUtils.xlogD(TAG, "updateCarrierTextForSearchNetwork carrierText=" + carrierText + ", simId=" + simId);
        if (isWifiOnlyDevice()) {
            KeyguardUtils.xlogD(TAG, "updateCarrierTextForSearchNetwork WifiOnly");
            mCarrierView[PhoneConstants.GEMINI_SIM_1].setVisibility(View.GONE);
        } else {
            mCarrierView[simId].setText(carrierText);
            showOrHideCarrier();
        }
    }

    private CharSequence getDefaultPlmn() {
        return mContext.getResources().getText(R.string.keyguard_carrier_default);
    }

    
    private CharSequence appendCsgInfo(CharSequence srcText, CharSequence hnbName, CharSequence csgId) {
        CharSequence outText = srcText;
        if (!TextUtils.isEmpty(hnbName)) {
            outText = concatenate(srcText, hnbName);    
        } else if (!TextUtils.isEmpty(csgId)) {
            outText = concatenate(srcText, csgId);
        }
        
        return outText;
    }
}
