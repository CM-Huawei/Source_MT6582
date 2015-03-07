/* Copyright Statement:
 *
 * This software/firmware and related documentation ("MediaTek Software") are
 * protected under relevant copyright laws. The information contained herein
 * is confidential and proprietary to MediaTek Inc. and/or its licensors.
 * Without the prior written permission of MediaTek inc. and/or its licensors,
 * any reproduction, modification, use or disclosure of MediaTek Software,
 * and information contained herein, in whole or in part, shall be strictly prohibited.
 */
/* MediaTek Inc. (C) 2010. All rights reserved.
 *
 * BY OPENING THIS FILE, RECEIVER HEREBY UNEQUIVOCALLY ACKNOWLEDGES AND AGREES
 * THAT THE SOFTWARE/FIRMWARE AND ITS DOCUMENTATIONS ("MEDIATEK SOFTWARE")
 * RECEIVED FROM MEDIATEK AND/OR ITS REPRESENTATIVES ARE PROVIDED TO RECEIVER ON
 * AN "AS-IS" BASIS ONLY. MEDIATEK EXPRESSLY DISCLAIMS ANY AND ALL WARRANTIES,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE OR NONINFRINGEMENT.
 * NEITHER DOES MEDIATEK PROVIDE ANY WARRANTY WHATSOEVER WITH RESPECT TO THE
 * SOFTWARE OF ANY THIRD PARTY WHICH MAY BE USED BY, INCORPORATED IN, OR
 * SUPPLIED WITH THE MEDIATEK SOFTWARE, AND RECEIVER AGREES TO LOOK ONLY TO SUCH
 * THIRD PARTY FOR ANY WARRANTY CLAIM RELATING THERETO. RECEIVER EXPRESSLY ACKNOWLEDGES
 * THAT IT IS RECEIVER'S SOLE RESPONSIBILITY TO OBTAIN FROM ANY THIRD PARTY ALL PROPER LICENSES
 * CONTAINED IN MEDIATEK SOFTWARE. MEDIATEK SHALL ALSO NOT BE RESPONSIBLE FOR ANY MEDIATEK
 * SOFTWARE RELEASES MADE TO RECEIVER'S SPECIFICATION OR TO CONFORM TO A PARTICULAR
 * STANDARD OR OPEN FORUM. RECEIVER'S SOLE AND EXCLUSIVE REMEDY AND MEDIATEK'S ENTIRE AND
 * CUMULATIVE LIABILITY WITH RESPECT TO THE MEDIATEK SOFTWARE RELEASED HEREUNDER WILL BE,
 * AT MEDIATEK'S OPTION, TO REVISE OR REPLACE THE MEDIATEK SOFTWARE AT ISSUE,
 * OR REFUND ANY SOFTWARE LICENSE FEES OR SERVICE CHARGE PAID BY RECEIVER TO
 * MEDIATEK FOR SUCH MEDIATEK SOFTWARE AT ISSUE.
 *
 * The following software/firmware and/or related documentation ("MediaTek Software")
 * have been modified by MediaTek Inc. All revisions are subject to any receiver's
 * applicable license agreements with MediaTek Inc.
 */

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

package com.mediatek.settings;

import java.lang.ref.WeakReference;

import android.app.ActionBar;
import android.app.Dialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.AsyncResult;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.SystemProperties;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.preference.PreferenceScreen;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.MenuItem;
import android.widget.Toast;

import com.android.internal.telephony.IccCard;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.TelephonyIntents;

import com.android.phone.ChangeIccPinScreen;
import com.android.phone.EditPinPreference;
import com.android.phone.FdnList;
import com.android.phone.PhoneGlobals;
import com.android.phone.PhoneUtils;
import com.android.phone.R;

import com.mediatek.phone.GeminiConstants;
import com.mediatek.phone.PhoneLog;
import com.mediatek.phone.gemini.GeminiUtils;
import com.mediatek.phone.wrapper.ITelephonyWrapper;
import com.mediatek.phone.wrapper.PhoneWrapper;
import com.mediatek.telephony.SimInfoManager;
import com.mediatek.telephony.SimInfoManager.SimInfoRecord;
import com.mediatek.phone.ext.ExtensionManager;
import com.mediatek.phone.ext.SettingsExtension;

/**
 * FDN settings UI for the Phone app.
 * Rewritten to look and behave closer to the other preferences.
 */
public class FdnSetting2 extends android.app.Activity {

    private Phone mPhone;

    private int mSlotId = GeminiUtils.getDefaultSlot();
    private int mRetryPin2Old;
    private int mRetryPin2New;
    /**
     * Events we handle.
     * The first is used for toggling FDN enable, the second for the PIN change.
     */
    private static final int EVENT_PIN2_ENTRY_COMPLETE = 100;
    private static final String LOG_TAG = "Settings/FdnSetting2";


    // String keys for preference lookup
    // We only care about the pin preferences here, the manage FDN contacts
    // Preference is handled solely in xml.
    private static final String BUTTON_FDN_ENABLE_KEY = "button_fdn_enable_key";
    private static final String BUTTON_CHANGE_PIN2_KEY = "button_change_pin2_key";
    private static final String BUTTON_FDN_LIST_KEY = "button_fdn_list_key";

    private EditPinPreference mButtonEnableFDN;
    private Preference mButtonChangePin2;
    private Preference mButtonFDNList;
    private boolean mFdnSupport = true;

    // size limits for the pin.
    private static final int MIN_PIN_LENGTH = 4;
    private static final int MAX_PIN_LENGTH = 8;
    private static final int GET_SIM_RETRY_EMPTY = -1;
    /// M: CT plugin for SIM to SIM/UIM
    private SettingsExtension mExt;

    private final BroadcastReceiver mReceiver = new FdnSetting2BroadcastReceiver();
    ///M: for adjust setting UI on VXGA device.
    public PreferenceFragment mFragment;
    private int getRetryPuk2Count() {
        String puk2RetryStr;
        if (GeminiUtils.isGeminiSupport()) {
            puk2RetryStr = GeminiUtils.GEMINI_PUK2_RETRY[mSlotId];
        } else {
            puk2RetryStr = "gsm.sim.retry.puk2";
        }
        return SystemProperties.getInt(puk2RetryStr, GET_SIM_RETRY_EMPTY);
    }

    private int getRetryPin2Count() {
        String pin2RetryString;
        if (GeminiUtils.isGeminiSupport()) {
            pin2RetryString = GeminiUtils.GEMINI_PIN2_RETRY[mSlotId];
        } else {
            pin2RetryString = "gsm.sim.retry.pin2";
        }
        return SystemProperties.getInt(pin2RetryString,GET_SIM_RETRY_EMPTY);
    }

    private String getRetryPin2() {
        int retryCount = getRetryPin2Count();
        mRetryPin2New = retryCount;
        PhoneLog.d(LOG_TAG, "getRetryPin2 retryCount =" + retryCount);
        switch (retryCount) {
        case GET_SIM_RETRY_EMPTY:
            PhoneLog.d(LOG_TAG, "getRetryPin2,GET_SIM_RETRY_EMPTY");
            return " ";
        case 1:
            return "(" + getString(R.string.one_retry_left) + ")";
        default:
            return "(" + getString(R.string.retries_left,retryCount) + ")";
        }
    }

    private void resetFDNDialog(int strId) {
        if (strId != 0) {
            mButtonEnableFDN.setDialogMessage(getString(strId) + "\n"
                    + getString(R.string.enter_pin2_text) + "\n" 
                    + getRetryPin2());
        } else {
            PhoneLog.d(LOG_TAG, "resetFDNDialog 0");
            mButtonEnableFDN.setDialogMessage(getString(R.string.enter_pin2_text) + "\n" 
                    + getRetryPin2());
        }
    }

    /**
     * Attempt to toggle FDN activation.
     */
    private void toggleFDNEnable(boolean positiveResult) {
        PhoneLog.d(LOG_TAG, "toggleFDNEnable" + positiveResult);
    
        if (!positiveResult) {
            PhoneLog.d(LOG_TAG, "toggleFDNEnable positiveResult is false");
            resetFDNDialog(0);
            mRetryPin2Old = mRetryPin2New;
            PhoneLog.d(LOG_TAG, "toggleFDNEnable mRetryPin2Old=" + mRetryPin2Old);
            return;
        }

        /// M: for ALPS00947291 @{
        // enable FDN will make PHB reset.
        if (!isPhoneBookReady()) {
            PhoneLog.d(LOG_TAG, "PHB is not ready, can not enable fdn");
            return;
        }
        /// @}

        // validate the pin first, before submitting it to the RIL for FDN enable.
        String password = mButtonEnableFDN.getText();
        if (validatePin(password, false)) {
            // get the relevant data for the icc call
            IccCard iccCard = PhoneWrapper.getIccCard(mPhone, mSlotId);
            boolean isEnabled = iccCard.getIccFdnEnabled();
            Message onComplete = mFDNHandler.obtainMessage(EVENT_PIN2_ENTRY_COMPLETE);

            // make fdn request
            iccCard.setIccFdnEnabled(!isEnabled, password, onComplete);
        } else {
            // throw up error if the pin is invalid.
            resetFDNDialog(R.string.invalidPin2);
            mButtonEnableFDN.setText("");
            mButtonEnableFDN.showPinDialog();
        }

        mButtonEnableFDN.setText("");
    }

    /**
     * Handler for asynchronous replies from the sim.
     */
    private Handler mFDNHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                // when we are enabling FDN, either we are unsuccessful and display
                // a toast, or just update the UI.
            case EVENT_PIN2_ENTRY_COMPLETE:
                AsyncResult ar = (AsyncResult) msg.obj;
                if (ar.exception != null) {
                    if (getRetryPin2Count() == 0) {
                        displayMessage(R.string.puk2_requested);
                        PhoneLog.d(LOG_TAG, "EVENT_PIN2_ENTRY_COMPLETE,puk2_requested");
                        updateFDNPreference();
                    } else {
                        resetFDNDialog(R.string.pin2_invalid);
                        ///M: add for fixing bug ALPS01210800
                        /// sometimes the activity the dialog belonged to is finishing, but the dialog still under creating, so coming JE @{
                        if (!FdnSetting2.this.isFinishing()) {
                           mButtonEnableFDN.showPinDialog();
                        }
                        /// @}
                    }
                } else {
                    updateEnableFDN();
                }
                PhoneLog.d(LOG_TAG, "EVENT_PIN2_ENTRY_COMPLETE");
                mRetryPin2Old = mRetryPin2New;
                PhoneLog.d(LOG_TAG, "EVENT_PIN2_ENTRY_COMPLETE mRetryPin2Old=" + mRetryPin2Old);
                break;
            default:
                break;
            }
        }
    };

    /**
     * Display a toast for message, like the rest of the settings.
     */
    private void displayMessage(int strId) {
        /// M: CT SIM to SIM/UIM @{
        String text = getString(strId);
        if (strId == R.string.puk2_requested) {
            text = mExt.replaceSimBySlot(text, mSlotId);
        }
        Toast.makeText(this, text, Toast.LENGTH_SHORT)
            .show();
        /// @}
    }

    /**
     * Validate the pin entry.
     *
     * @param pin This is the pin to validate
     * @param isPuk Boolean indicating whether we are to treat
     * the pin input as a puk.
     */
    private boolean validatePin(String pin, boolean isPUK) {

        // for pin, we have 4-8 numbers, or puk, we use only 8.
        int pinMinimum = isPUK ? MAX_PIN_LENGTH : MIN_PIN_LENGTH;

        // check validity
        return !(pin == null || pin.length() < pinMinimum || pin.length() > MAX_PIN_LENGTH);
    }

    /**
     * Reflect the updated FDN state in the UI.
     */
    private void updateEnableFDN() {
        IccCard iccCard = PhoneWrapper.getIccCard(mPhone, mSlotId);
        if (iccCard.getIccFdnEnabled()) {
            PhoneLog.d(LOG_TAG, "updateEnableFDN is FdnEnabled=" + R.string.disable_fdn);
            mButtonEnableFDN.setTitle(R.string.enable_fdn_ok);
            mButtonEnableFDN.setSummary(R.string.fdn_enabled);
            mButtonEnableFDN.setDialogTitle(R.string.disable_fdn);
        } else {
            PhoneLog.d(LOG_TAG, "updateEnableFDN is not FdnEnabled=" + R.string.enable_fdn);
            mButtonEnableFDN.setTitle(R.string.disable_fdn_ok);
            mButtonEnableFDN.setSummary(R.string.fdn_disabled);
            mButtonEnableFDN.setDialogTitle(R.string.enable_fdn);
        }
        PhoneLog.d(LOG_TAG, "updateEnableFDN");
        resetFDNDialog(0);
    }

    private void updateFDNPreference() {
        /// M: CT SIM to SIM/UIM
        updateScreen();
        if (getRetryPin2Count() == 0) {
            mRetryPin2New = 0;
            PhoneLog.d(LOG_TAG, "updateFDNPreference, mRetryPin2New=" + mRetryPin2New);
            mButtonChangePin2.setTitle(R.string.unblock_pin2);
            /// M: CT SIM to SIM/UIM. @{
            String textMsg = null;
            if (getRetryPuk2Count() == 0) {
                textMsg = mExt.replaceSimBySlot(getString(R.string.sim_permanently_locked), mSlotId);
            } else {
                textMsg = mExt.replaceSimBySlot(getString(R.string.puk_requested), mSlotId);
            }
            mButtonChangePin2.setSummary(textMsg);
            /// @}
        } else {
            PhoneLog.d(LOG_TAG, "updateFDNPreference");
            mButtonChangePin2.setTitle(R.string.change_pin2);
            mButtonChangePin2.setSummary(R.string.sum_fdn_change_pin);
        }
        updateEnableFDN();
    }

    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        ///M: for adjust setting UI on VXGA device. @{
        mFragment = new FdnSettingFragment();
        getFragmentManager().beginTransaction()
                  .replace(android.R.id.content, mFragment).commit();
        /// @}
    }
    ///M: for adjust setting UI on VXGA device.
    public static class FdnSettingFragment extends PreferenceFragment implements
             EditPinPreference.OnPinEnteredListener, Preference.OnPreferenceClickListener {
        WeakReference<FdnSetting2> activityRef = null;

        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            activityRef = new WeakReference<FdnSetting2>((FdnSetting2)getActivity()); 
            addPreferencesFromResource(R.xml.fdn_setting2);

            activityRef.get().mPhone = PhoneGlobals.getPhone();
            /// M: CT plugin for SIM to SIM/UIM
            activityRef.get().mExt = ExtensionManager.getInstance().getSettingsExtension();

            //get UI object references
            PreferenceScreen prefSet = getPreferenceScreen();
            activityRef.get().mButtonEnableFDN = (EditPinPreference) prefSet.findPreference(BUTTON_FDN_ENABLE_KEY);
            activityRef.get().mButtonChangePin2 = prefSet.findPreference(BUTTON_CHANGE_PIN2_KEY);
            activityRef.get().mButtonFDNList = prefSet.findPreference(BUTTON_FDN_LIST_KEY);
            //assign click listener and update state
            if (null != activityRef.get().mButtonEnableFDN) {
                activityRef.get().mButtonEnableFDN.setOnPinEnteredListener(this);
                activityRef.get().mButtonEnableFDN.getEditText().addTextChangedListener(new TextWatcher() {
                    int endPos;
                    public void afterTextChanged(Editable s) {
                        endPos = activityRef.get().mButtonEnableFDN.getEditText().getSelectionEnd();
                        /// M: ALPS00723209 @{
                        // when input string length is more than max length,
                        // remove the redundant char.
                        if (s.length() > MAX_PIN_LENGTH) {
                            PhoneLog.d(LOG_TAG, "remove redundant char, startPos: " +
                                        MAX_PIN_LENGTH + ", endPos:" + endPos);
                            s.delete(MAX_PIN_LENGTH, endPos);
                        }
                        /// @}
                    }

                    public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                    }
                    public void onTextChanged(CharSequence s, int start, int before,
                        int count) {
                    }
                });
            }
            if (null != activityRef.get().mButtonChangePin2) {
                activityRef.get().mButtonChangePin2.setOnPreferenceClickListener(this);        
            }
            if (null != activityRef.get().mButtonFDNList) {
                activityRef.get().mButtonFDNList.setOnPreferenceClickListener(this);
            }
    
            ActionBar actionBar = getActivity().getActionBar();
            if (actionBar != null) {
                // android.R.id.home will be triggered in onOptionsItemSelected()
                actionBar.setDisplayHomeAsUpEnabled(true);
            }
            activityRef.get().initSlotId();
            activityRef.get().initUIState();
        }

        public boolean onPreferenceClick(Preference preference) {
            PhoneLog.i(LOG_TAG, "onPreferenceClick" + preference.getKey());
            if (preference == activityRef.get().mButtonChangePin2) {
                Intent intent = new Intent();
                intent.putExtra("pin2", true);
                if (activityRef.get().mSlotId >= 0) {
                    intent.putExtra(PhoneConstants.GEMINI_SIM_ID_KEY, activityRef.get().mSlotId);
                }
                intent.setClass(getActivity(), ChangeIccPinScreen.class);
                startActivity(intent);
            }
            
            if (preference == activityRef.get().mButtonFDNList) {
                PhoneLog.i(LOG_TAG, "onPreferenceClick mButtonFDNList");
                if (!activityRef.get().isPhoneBookReady()) {
                    return false;
                }
                Intent intent = new Intent(getActivity(), FdnList.class);
                if (activityRef.get().mSlotId >= 0) {
                    intent.putExtra(PhoneConstants.GEMINI_SIM_ID_KEY, activityRef.get().mSlotId);
                }
                activityRef.get().startActivity(intent);
            }
            return true;
        }

        /**
         * Delegate to the respective handlers.
         */
        public void onPinEntered(EditPinPreference preference, boolean positiveResult) {
            if (preference == activityRef.get().mButtonEnableFDN) {
                PhoneLog.d(LOG_TAG, "onPinEntered");
                activityRef.get().toggleFDNEnable(positiveResult);
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        Dialog fdnDialog = mButtonEnableFDN.getDialog();
        updateFDNPreference();
        PhoneLog.d(LOG_TAG, "onResume, mRetryPin2New= " + mRetryPin2New
                + " mRetryPin2Old=" + mRetryPin2Old);
      
        if (mRetryPin2New != mRetryPin2Old) {
            mRetryPin2Old = mRetryPin2New;
            PhoneLog.d(LOG_TAG, "onResume, fdnDialog= " + fdnDialog);
            if (fdnDialog != null) {
                PhoneLog.d(LOG_TAG, "onResume, fdnDialog.isShowing()=" + fdnDialog.isShowing());
            }
            PhoneLog.d(LOG_TAG, "onResume, second mRetryPin2New= " + mRetryPin2New
                    + " mRetryPin2Old=" + mRetryPin2Old);

            if (fdnDialog != null && fdnDialog.isShowing()) {
               PhoneLog.d(LOG_TAG, "onResume, isShowing");
               mButtonEnableFDN.getDialog().dismiss();
            }
        }
    }
    
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        final int itemId = item.getItemId();
        switch (itemId) {
        case android.R.id.home:
            finish();
            return true;
        default:
            break;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onDestroy() {
        PhoneLog.d(LOG_TAG, "onDestroy");
        super.onDestroy();
        unregisterReceiver(mReceiver);
    }

    private boolean isPhoneBookReady() {
        boolean isPhoneBookReady = ITelephonyWrapper.isPhbReady(mSlotId);
        if (!isPhoneBookReady) {
            showTipToast(getString(R.string.error_title),getString(R.string.fdn_phone_book_busy));
        }
        return isPhoneBookReady;
    }

    public void showTipToast(String title, String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
    }

    private class FdnSetting2BroadcastReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            PhoneLog.d(LOG_TAG, "action: " + action);
            if (action.equals(Intent.ACTION_AIRPLANE_MODE_CHANGED)) {
                updateScreen();
            } else if (action.equals(Intent.ACTION_DUAL_SIM_MODE_CHANGED)) {
                updateScreen();
            } else if (action.equals(TelephonyIntents.ACTION_SIM_INFO_UPDATE)) {
                ///M: add for hot swap
                GeminiUtils.handleSimHotSwap(FdnSetting2.this, mSlotId);
                ///@}
                updateScreen();
            }
        }
    }

    /**
     * mButtonEnableFDN & mButtonFDNList depends on SIM card's status:
     *   1.support fdn 2.Unlocked 3. radio on 4. dual sim status 5. pin2 count 6. puk2 count
     * mButtonChangePin2 depends on SIM card's status:
     *   1.dual sim status 2. radio on 3 puk2 count
     */
    private void updateScreen() {
        boolean isRadioOn = PhoneWrapper.isRadioOn(mPhone, mSlotId);
        boolean isFndButtonOrFdnlistEnable = mFdnSupport
                && (getRetryPin2Count() != 0)
                && (getRetryPuk2Count() != 0)
                && isRadioOn;
        boolean isDualModeSettingOn = true;
        if (GeminiUtils.isGeminiSupport()) {
            isDualModeSettingOn = Settings.System.getInt(getContentResolver(), Settings.System.DUAL_SIM_MODE_SETTING, -1) > 0;
            isFndButtonOrFdnlistEnable &= isDualModeSettingOn;
        }
        PhoneLog.d(LOG_TAG, "isAirplanmodeOn: " + PhoneUtils.isOnAirplaneMode()
                + "\nisRadioOn:" + isRadioOn
                + "\nPin2Count: " + getRetryPin2Count()
                + "\nPuk2Count: " + getRetryPuk2Count()
                + "\nisDualModeSettingOn: " + isDualModeSettingOn
                + "\nmFdnSupport: " + mFdnSupport);

        if (!PhoneUtils.isOnAirplaneMode()) {
            mButtonEnableFDN.setEnabled(isFndButtonOrFdnlistEnable);
            mButtonFDNList.setEnabled(isFndButtonOrFdnlistEnable);
            mButtonChangePin2.setEnabled(isDualModeSettingOn
                    && isRadioOn
                    && getRetryPuk2Count() != 0);
        } else {
            mButtonEnableFDN.setEnabled(false);
            mButtonFDNList.setEnabled(false);
            mButtonChangePin2.setEnabled(false);
        }
    }

    private void registerCallBacks() {
        IntentFilter intentFilter =
            new IntentFilter(Intent.ACTION_AIRPLANE_MODE_CHANGED);
        if (GeminiUtils.isGeminiSupport()) {
            intentFilter.addAction(Intent.ACTION_DUAL_SIM_MODE_CHANGED);
        }
        ///M: add for hot swap {
        intentFilter.addAction(TelephonyIntents.ACTION_SIM_INFO_UPDATE);
        ///@}
        registerReceiver(mReceiver, intentFilter);
    }

    private void initUIState() {
        mButtonEnableFDN.initFdnModeData(mPhone, EditPinPreference.FDN_MODE_FLAG, mSlotId);
        IccCard iccCard = PhoneWrapper.getIccCard(mPhone, mSlotId);
        mFdnSupport = iccCard.isFdnExist();
        updateFDNPreference();
        mRetryPin2Old = getRetryPin2Count();
        PhoneLog.d(LOG_TAG, "onCreate,  mRetryPin2Old=" + mRetryPin2Old);
        /// for ALPS01074702 @{
        // PHB is not ready.
        if (!isPhoneBookReady()) {
            PhoneLog.d(LOG_TAG, "PHB is not ready, can not enable fdn");
            finish();
        }
        /// @}
    }

    private void initSlotId() {
        if (GeminiUtils.isGeminiSupport()) {
            mSlotId = getIntent().getIntExtra(GeminiConstants.SLOT_ID_KEY, GeminiUtils.UNDEFINED_SLOT_ID);
            PhoneLog.d(LOG_TAG,"[mSlotId = " + mSlotId + "]");
            SimInfoRecord siminfo = SimInfoManager.getSimInfoBySlot(this, mSlotId);
            if (siminfo != null) {
                setTitle(siminfo.mDisplayName);
            }
        }
        registerCallBacks();
    }
}