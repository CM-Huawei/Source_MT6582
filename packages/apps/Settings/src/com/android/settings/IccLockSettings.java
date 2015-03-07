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

package com.android.settings;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.os.AsyncResult;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.SystemProperties;
import android.preference.CheckBoxPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceScreen;
import android.provider.Settings;
import android.util.Log;
import android.widget.Toast;

import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.PhoneFactory;
import com.android.internal.telephony.TelephonyIntents;
import com.android.internal.telephony.gemini.GeminiPhone;

import com.mediatek.CellConnService.CellConnMgr;
import com.mediatek.common.featureoption.FeatureOption;
import com.mediatek.gemini.GeminiUtils;
import com.mediatek.settings.ext.ISettingsMiscExt;
import com.mediatek.settings.ext.ISimRoamingExt;
import com.mediatek.telephony.SimInfoManager;
import com.mediatek.telephony.SimInfoManager.SimInfoRecord;
import com.mediatek.xlog.Xlog;

import java.util.List;

/**
 * Implements the preference screen to enable/disable ICC lock and
 * also the dialogs to change the ICC PIN. In the former case, enabling/disabling
 * the ICC lock will prompt the user for the current PIN.
 * In the Change PIN case, it prompts the user for old pin, new pin and new pin
 * again before attempting to change it. Calls the SimCard interface to execute
 * these operations.
 *
 */
public class IccLockSettings extends PreferenceActivity
        implements EditPinPreference.OnPinEnteredListener {
    private static final String TAG = "IccLockSettings";
    private static final boolean DBG = true;

    private static final int OFF_MODE = 0;
    // State when enabling/disabling ICC lock
    private static final int ICC_LOCK_MODE = 1;
    // State when entering the old pin
    private static final int ICC_OLD_MODE = 2;
    // State when entering the new pin - first time
    private static final int ICC_NEW_MODE = 3;
    // State when entering the new pin - second time
    private static final int ICC_REENTER_MODE = 4;

    // Keys in xml file
    private static final String PIN_DIALOG = "sim_pin";
    private static final String PIN_TOGGLE = "sim_toggle";
    // Keys in icicle
    private static final String DIALOG_STATE = "dialogState";
    private static final String DIALOG_PIN = "dialogPin";
    private static final String DIALOG_ERROR = "dialogError";
    private static final String ENABLE_TO_STATE = "enableState";

    // Save and restore inputted PIN code when configuration changed
    // (ex. portrait<-->landscape) during change PIN code
    private static final String OLD_PINCODE = "oldPinCode";
    private static final String NEW_PINCODE = "newPinCode";

    private static final int MIN_PIN_LENGTH = 4;
    private static final int MAX_PIN_LENGTH = 8;
    // Which dialog to show next when popped up
    private int mDialogState = OFF_MODE;

    private String mPin;
    private String mOldPin;
    private String mNewPin;
    private String mError;
    // Are we trying to enable or disable ICC lock?
    private boolean mToState;

    private Phone mPhone;

    private EditPinPreference mPinDialog;
    private CheckBoxPreference mPinToggle;

    private Resources mRes;

     ///M: set constant value for no retry left add for debug
    private static final int GET_SIM_RETRY_EMPTY = -100;    
    
    // For async handler to identify request type
    private static final int MSG_ENABLE_ICC_PIN_COMPLETE = 100;
    private static final int MSG_CHANGE_ICC_PIN_COMPLETE = 101;
    private static final int MSG_SIM_STATE_CHANGED = 102;
    private static final int SLOT_ALL = -1;

    // For Gemini
    private int mSlotId = -1;
    private GeminiPhone mGeminiPhone;
    private static final String SIM_ID = "sim_id";
    private boolean mIsUnlockFollow = false;
    private boolean mIsShouldBeFinished = false; 
    private boolean mIsDeadLocked = false;

    private ISimRoamingExt mSimRoamingExt;
    private ISettingsMiscExt mExt;

    // For replies from IccCard interface
    private Handler mHandler = new Handler() {
        public void handleMessage(Message msg) {
            AsyncResult ar = (AsyncResult) msg.obj;
            switch (msg.what) {
            case MSG_ENABLE_ICC_PIN_COMPLETE:
                Xlog.d(TAG, "MSG_ENABLE_ICC_PIN_COMPLETE");
                iccLockChanged(ar.exception == null, msg.arg1);
                break;
            case MSG_CHANGE_ICC_PIN_COMPLETE:
                Xlog.d(TAG, "MSG_CHANGE_ICC_PIN_COMPLETE");
                iccPinChanged(ar.exception == null, msg.arg1);
                break;
            case MSG_SIM_STATE_CHANGED:
                Xlog.d(TAG, "MSG_SIM_STATE_CHANGED");
                updatePreferences();
                updateOnSimLockStateChanged();
                break;
            default: 
                break;
            }

            return;
        }
    };

    private final BroadcastReceiver mSimStateReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (TelephonyIntents.ACTION_SIM_STATE_CHANGED.equals(action)) {
                Xlog.d(TAG,"receive ACTION_SIM_STATE_CHANGED");
                mHandler.sendMessage(mHandler.obtainMessage(MSG_SIM_STATE_CHANGED));
            } else if (TelephonyIntents.ACTION_SIM_INFO_UPDATE.equals(action)) {
                ///M: add for hot swap feature
                Xlog.d(TAG,"receive ACTION_SIM_INFO_UPDATE");
                List<SimInfoRecord> simList = SimInfoManager.getInsertedSimInfoList(IccLockSettings.this);
                if (simList != null) {
                    if (simList.size() == 0) {
                        // Hot swap and no card so go to settings
                        Xlog.d(TAG, "Hot swap_simList.size()=" + simList.size());
                        GeminiUtils.goBackSettings(IccLockSettings.this);
                    } else if (GeminiUtils.getSiminfoIdBySimSlotId(mSlotId, simList) 
                            == GeminiUtils.UNDEFINED_SIM_ID) {
                        finish();
                    }
                }
            } else if (action.equals(Intent.ACTION_AIRPLANE_MODE_CHANGED)) {
                boolean airplaneMode = intent.getBooleanExtra("state", false);
                Xlog.d(TAG,"airplaneMode" + airplaneMode);
                if (airplaneMode) {
                    mCellMgr.handleCellConn(mSlotId,CellConnMgr.REQUEST_TYPE_SIMLOCK);
                    if (isIccLockEnabled()) {
                        mPinDialog.setEnabled(false);
                    }
                    mPinToggle.setEnabled(false);
                }
            }
        }
    };

    ///M: unlock sim pin/ me lock
    private Runnable mServiceComplete = new Runnable() {
        public void run() {
            int nRet = mCellMgr.getResult();
            if (mCellMgr.RESULT_OK != nRet
                    && mCellMgr.RESULT_STATE_NORMAL != nRet) {
                Xlog.d(TAG, "mCell Mgr Result is not OK");
                mIsShouldBeFinished = true;
                GeminiUtils.backToSimcardUnlock(IccLockSettings.this, true);
                return;
            } else {
                Xlog.d(TAG, "mServiceComplete + Enable mPinToggle");
                mPinToggle.setEnabled(true);
            }
            mIsUnlockFollow = false;
        }
    };

    // create unlock object
    private CellConnMgr mCellMgr = new CellConnMgr(mServiceComplete);

    // For top-level settings screen to query
    boolean isIccLockEnabled() {
        // return PhoneFactory.getDefaultPhone().getIccCard().getIccLockEnabled();
        return getCurrentPhone().getIccCard().getIccLockEnabled();
    }

    String getSummary(Context context) {
        Resources res = context.getResources();
        String summary = isIccLockEnabled()
                ? res.getString(R.string.sim_lock_on)
                : res.getString(R.string.sim_lock_off);
        return summary;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (Utils.isMonkeyRunning()) {
            finish();
            return;
        }
        /// M: Initialize Plugin
        mExt = Utils.getMiscPlugin(this);
        
        addPreferencesFromResource(R.xml.sim_lock_settings);
        ///M: add by MTK
        mCellMgr.register(this);
        ///M: modify for Gemini
        if (FeatureOption.MTK_GEMINI_SUPPORT) {
            mGeminiPhone = (GeminiPhone) PhoneFactory.getDefaultPhone();
        } else {
            mPhone = PhoneFactory.getDefaultPhone();
        }
        mPinDialog = (EditPinPreference) findPreference(PIN_DIALOG);
        mPinToggle = (CheckBoxPreference) findPreference(PIN_TOGGLE);
        mRes = getResources();
        // Don't need any changes to be remembered
        getPreferenceScreen().setPersistent(false);
        mSlotId = GeminiUtils.getTargetSlotId(this);
        Xlog.d(TAG, "mSlotId is : " + mSlotId);
        if (mSlotId == GeminiUtils.UNDEFINED_SLOT_ID) {
            // need to select the sim card so jump to card selection activity
            GeminiUtils.startSelectSimActivity(this,R.string.sim_lock_settings);
        } else {
            updateTitle(mSlotId);
            updatePreferences();
        }
        Xlog.d(TAG, "mDialogState is : " + mDialogState);
        if (savedInstanceState != null
                && savedInstanceState.containsKey(DIALOG_STATE)) {
            mDialogState = savedInstanceState.getInt(DIALOG_STATE);
            mPin = savedInstanceState.getString(DIALOG_PIN);
            mError = savedInstanceState.getString(DIALOG_ERROR);
            mToState = savedInstanceState.getBoolean(ENABLE_TO_STATE);
            Xlog.d(TAG, "mDialogState is : " + mDialogState + " mPin is : " + mPin + " mError is : " + mError + " mToState  is : " + mToState);
            // Restore inputted PIN code
            switch (mDialogState) {
            case ICC_NEW_MODE:
                mOldPin = savedInstanceState.getString(OLD_PINCODE);
                Xlog.d(TAG, "mOldPin  is : " + mOldPin);
                break;

            case ICC_REENTER_MODE:
                mOldPin = savedInstanceState.getString(OLD_PINCODE);
                mNewPin = savedInstanceState.getString(NEW_PINCODE);
                Xlog.d(TAG, "mOldPin  is : " + mOldPin + " mNewPin   is : " + mNewPin);
                break;

            case ICC_LOCK_MODE:
            case ICC_OLD_MODE:
            default:
                break;
            }
        }

        mPinDialog.setOnPinEnteredListener(this);

        mSimRoamingExt = Utils.getSimRoamingExtPlugin(this);

    }

    private void updatePreferences() {
        boolean iccEnable = isIccLockEnabled();
        Xlog.d(TAG, "iccEnable: " + iccEnable);
        mPinToggle.setChecked(iccEnable);
        /** M: add by MTK handle air plane mode issue @ { */
        boolean isAirPlaneModeOn = Settings.Global.getInt(getContentResolver(),
                Settings.Global.AIRPLANE_MODE_ON, 0) != 0;
        if (!isAirPlaneModeOn && iccEnable) {
            mPinDialog.setEnabled(true);
        }
        /** @ }  */
    }

    @Override
    protected void onResume() {
        super.onResume();
        
        ///M: customize SIM strings @{
        String title = mExt.customizeSimDisplayString(
            getTitle().toString(), SLOT_ALL);
        setTitle(title);
        
        String simPinToggle = mRes.getString(R.string.sim_pin_toggle);
        String simPinToggleCT = mExt.customizeSimDisplayString(
                simPinToggle, mSlotId);
        mPinToggle.setTitle(simPinToggleCT);
        
        String simPinChange = mRes.getString(R.string.sim_pin_change);
        String simPinChangeCT = mExt.customizeSimDisplayString(simPinChange, mSlotId);
        mPinDialog.setTitle(simPinChangeCT);
        /// @}
        
        
        ///M: add by MTK
        Xlog.d(TAG, "mIsShouldBeFinished: " + mIsShouldBeFinished);
        if (mIsShouldBeFinished) {
            finish();
            return;
        }
       
        if (mSlotId != GeminiUtils.UNDEFINED_SLOT_ID) {
            // ACTION_SIM_STATE_CHANGED is sticky, so we'll receive current state after this call,
            // which will call updatePreferences().
            final IntentFilter filter = new IntentFilter(TelephonyIntents.ACTION_SIM_STATE_CHANGED);
            ///M: add for hot swap
            filter.addAction(TelephonyIntents.ACTION_SIM_INFO_UPDATE);
            filter.addAction(Intent.ACTION_AIRPLANE_MODE_CHANGED);
            registerReceiver(mSimStateReceiver, filter);
            
            if (mDialogState != OFF_MODE) {
                showPinDialog();
            } else {
                // Prep for standard click on "Change PIN"
                resetDialogState();
            }
            if (!mIsUnlockFollow) {
                mIsUnlockFollow = true;
                mPinToggle.setEnabled(false);
                if (getRetryPinCount() == 0
                        || getRetryPinCount() == GET_SIM_RETRY_EMPTY) {
                    Xlog.d(TAG, "OnResume: postDelay call - handleCellConn 1");
                    mHandler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            mCellMgr.handleCellConn(
                                    IccLockSettings.this.mSlotId,
                                    CellConnMgr.REQUEST_TYPE_SIMLOCK | 0x80000000);
                        }
                    }, 500);

                } else {
                    Xlog.d(TAG, "OnResume: postDelay call - handleCellConn 2");
                    mHandler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            mCellMgr.handleCellConn(
                                    IccLockSettings.this.mSlotId,
                                    CellConnMgr.REQUEST_TYPE_SIMLOCK);
                        }
                    }, 500);
                }
            }
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mSlotId != GeminiUtils.UNDEFINED_SLOT_ID) {
            unregisterReceiver(mSimStateReceiver);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mCellMgr.unregister();
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        // Need to store this state for slider open/close
        // There is one case where the dialog is popped up by the preference
        // framework. In that case, let the preference framework store the
        // dialog state. In other cases, where this activity manually launches
        // the dialog, store the state of the dialog.
        Xlog.d(TAG, "onSaveInstanceState");
        if (mPinDialog.isDialogOpen()) {
            out.putInt(DIALOG_STATE, mDialogState);
            out.putString(DIALOG_PIN, mPinDialog.getEditText().getText().toString());
            out.putString(DIALOG_ERROR, mError);
            out.putBoolean(ENABLE_TO_STATE, mToState);
            out.putInt(SIM_ID, mSlotId);

            // Save inputted PIN code
            switch (mDialogState) {
                case ICC_NEW_MODE:
                    out.putString(OLD_PINCODE, mOldPin);
                    break;

                case ICC_REENTER_MODE:
                    out.putString(OLD_PINCODE, mOldPin);
                    out.putString(NEW_PINCODE, mNewPin);
                    break;

                case ICC_LOCK_MODE:
                case ICC_OLD_MODE:
                default:
                    break;
            }
        } else {
            super.onSaveInstanceState(out);
        }
    }

    private void showPinDialog() {
        if (mDialogState == OFF_MODE) {
            return;
        }
        setDialogValues();

        mPinDialog.showPinDialog();
    }

    private void setDialogValues() {
        mPinDialog.setText(mPin);
        String message = "";
        /// M: Replace SIM to SIM/UIM in the sim tip strings @{
        String simPinHints = mExt.customizeSimDisplayString(mRes.getString(R.string.sim_enter_pin_hints), mSlotId);
        String enableSimLock = mExt.customizeSimDisplayString(mRes.getString(R.string.sim_enable_sim_lock), mSlotId); 
        String disableSimLock = mExt.customizeSimDisplayString(mRes.getString(R.string.sim_disable_sim_lock), mSlotId);
        String simChangePin = mExt.customizeSimDisplayString(mRes.getString(R.string.sim_change_pin), mSlotId);
        switch (mDialogState) {
        case ICC_LOCK_MODE:
            message = mExt.customizeSimDisplayString(mRes.getString(R.string.sim_enter_pin), mSlotId);
            message = message + " " + simPinHints;
            mPinDialog.setDialogTitle((mToState ?
                    enableSimLock
                    : disableSimLock) + "  " + getRetryPin());
            break;
        case ICC_OLD_MODE:
                message = mExt.customizeSimDisplayString(mRes.getString(R.string.sim_enter_old), mSlotId);
                message = message + " " + simPinHints;
                mPinDialog.setDialogTitle(simChangePin + "  " + getRetryPin());
                break;
            case ICC_NEW_MODE:
                message = mExt.customizeSimDisplayString(mRes.getString(R.string.sim_enter_new), mSlotId);
                message = message + " " + simPinHints;
                mPinDialog.setDialogTitle(simChangePin);
                break;
            case ICC_REENTER_MODE:
                message = mExt.customizeSimDisplayString(mRes.getString(R.string.sim_reenter_new), mSlotId);
                message = message + " " + simPinHints;
                mPinDialog.setDialogTitle(simChangePin);
                break;
            default:
                break;
        }
        /// @}
        if (mError != null) {
            message = mError + "\n" + message;
            mError = null;
        }
        mPinDialog.setDialogMessage(message);
    }

    public void onPinEntered(EditPinPreference preference, boolean positiveResult) {
        if (!positiveResult) {
            resetDialogState();
            return;
        }

        mPin = preference.getText();
        if (!reasonablePin(mPin)) {
            // inject error message and display dialog again
            mError = mRes.getString(R.string.sim_bad_pin);
            showPinDialog();
            return;
        }
        switch (mDialogState) {
        case ICC_LOCK_MODE:
            tryChangeIccLockState();
            break;
        case ICC_OLD_MODE:
            mOldPin = mPin;
            mDialogState = ICC_NEW_MODE;
            mError = null;
            mPin = null;
            showPinDialog();
            break;
        case ICC_NEW_MODE:
            mNewPin = mPin;
            mDialogState = ICC_REENTER_MODE;
            mPin = null;
            showPinDialog();
            break;
        case ICC_REENTER_MODE:
            if (!mPin.equals(mNewPin)) {
                mError = mRes.getString(R.string.sim_pins_dont_match);
                mDialogState = ICC_NEW_MODE;
                mError = null;
                mPin = null;
                showPinDialog();
            } else {
                mError = null;
                tryChangePin();
            }
            break;
        default:
            break;
        }
    }

    public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen, Preference preference) {
        if (preference == mPinToggle) {
            // Get the new, preferred state
            mToState = mPinToggle.isChecked();
            // Flip it back and pop up pin dialog
            mPinToggle.setChecked(!mToState);
            mDialogState = ICC_LOCK_MODE;
            showPinDialog();
        } else if (preference == mPinDialog) {
            mDialogState = ICC_OLD_MODE;
            return false;
        }
        return true;
    }

    private void tryChangeIccLockState() {
        // Try to change icc lock. If it succeeds, toggle the lock state and
        // reset dialog state. Else inject error message and show dialog again.
        Message callback = Message.obtain(mHandler, MSG_ENABLE_ICC_PIN_COMPLETE);
        getCurrentPhone().getIccCard().setIccLockEnabled(mToState, mPin, callback);
        // Disable the setting till the response is received.
        mPinToggle.setEnabled(false);
    }

    private void iccLockChanged(boolean success, int attemptsRemaining) {
        if (success) {
            mPinToggle.setChecked(mToState);
            ///M: add for display customize toast.
            mSimRoamingExt.showPinToast(mToState);
        } else {
            Toast.makeText(this, getPinPasswordErrorMessage(attemptsRemaining), Toast.LENGTH_LONG)
                    .show();
            noRetryPinAvailable(); 
        }
        mPinToggle.setEnabled(true);
        resetDialogState();
    }

    private void iccPinChanged(boolean success, int attemptsRemaining) {
        if (!success) {
            Toast.makeText(this, getPinPasswordErrorMessage(attemptsRemaining),
                    Toast.LENGTH_LONG)
                    .show();

            ///M: add for fix bug
            noRetryPinAvailable();
        } else {
            /// M: Replace SIM to SIM/UIM
            String simChangeSucceeded = mExt.customizeSimDisplayString(
            		mRes.getString(R.string.sim_change_succeeded), mSlotId);
            Toast.makeText(this, simChangeSucceeded, Toast.LENGTH_SHORT).show();
        }
        resetDialogState();
    }

    private void tryChangePin() {
        Message callback = Message.obtain(mHandler, MSG_CHANGE_ICC_PIN_COMPLETE);
        getCurrentPhone().getIccCard().changeIccLockPassword(mOldPin,
                mNewPin, callback);
    }

    private String getPinPasswordErrorMessage(int attemptsRemaining) {
        String displayMessage;

        if (attemptsRemaining == 0) {
            displayMessage = mRes.getString(R.string.wrong_pin_code_pukked);
        } else if (attemptsRemaining > 0) {
            displayMessage = mRes
                    .getQuantityString(R.plurals.wrong_pin_code, attemptsRemaining,
                            attemptsRemaining);
        } else {
            displayMessage = mRes.getString(R.string.pin_failed);
        }
        /// M: Replace SIM to SIM/UIM
        displayMessage = mExt.customizeSimDisplayString(displayMessage, mSlotId);
        if (DBG) Log.d(TAG, "getPinPasswordErrorMessage:"
                + " attemptsRemaining=" + attemptsRemaining + " displayMessage=" + displayMessage);
        return displayMessage;
    }

    private boolean reasonablePin(String pin) {
        if (pin == null || pin.length() < MIN_PIN_LENGTH || pin.length() > MAX_PIN_LENGTH) {
            return false;
        } else {
            return true;
        }
    }

    private void resetDialogState() {
        mError = null;
        mDialogState = ICC_OLD_MODE; // Default for when Change PIN is clicked
        mPin = "";
        setDialogValues();
        mDialogState = OFF_MODE;
    }

    private Phone getCurrentPhone() {
    	return FeatureOption.MTK_GEMINI_SUPPORT ? mGeminiPhone.getPhonebyId(mSlotId) : mPhone;
    }

    private void updateOnSimLockStateChanged() {
        Xlog.d(TAG, "updateOnSimLockStateChanged()+mIsDeadLocked="
                + mIsDeadLocked);
        if (getRetryPinCount() > 0 && mIsDeadLocked) {
            Xlog.d(TAG, "Restore state");
            mPinToggle.setEnabled(true);
            mIsDeadLocked = false;
            resetDialogState();
        }
    }

    private void updateTitle(int slotId) {
        SimInfoRecord simInfo = SimInfoManager.getSimInfoBySlot(this,
                slotId);
        Xlog.d(TAG, "simInfo is null: " + (simInfo == null));
        if (simInfo != null) {
            String simDisplayName = simInfo.mDisplayName;
            Xlog.d(TAG, "simDisplayName: " + simDisplayName);
            if (simDisplayName != null && !simDisplayName.equals("")) {
                setTitle(simDisplayName);
            }
        }
    }

    /*
    * M: if no retry available then disable the selection
    */
    private boolean noRetryPinAvailable() {
        if (getRetryPinCount() == 0 || getRetryPinCount() == GET_SIM_RETRY_EMPTY) {
            Xlog.d(TAG,"getRetryPinCount() = " + getRetryPinCount());
            mPinToggle.setEnabled(false);
            mIsDeadLocked = true;
            return true;
        }    
        return false;
    }

    private String getRetryPin() {
        int mPinRetryCount = getRetryPinCount();
        switch (mPinRetryCount) {
        case GET_SIM_RETRY_EMPTY:
            return " ";
        default:
            Xlog.d(TAG, " retry pin " + getString(R.string.sim_remain,mPinRetryCount));
            return getString(R.string.sim_remain,mPinRetryCount);
        }
    }
    
    private int getRetryPinCount() {
        if (!FeatureOption.MTK_GEMINI_SUPPORT) {
            return SystemProperties.getInt("gsm.sim.retry.pin1",
                    GET_SIM_RETRY_EMPTY);
        } else {
            if (PhoneConstants.GEMINI_SIM_1 == mSlotId) {
                return SystemProperties.getInt("gsm.sim.retry.pin1",
                        GET_SIM_RETRY_EMPTY);
            } else if (PhoneConstants.GEMINI_SIM_2 == mSlotId) {
                return SystemProperties.getInt("gsm.sim.retry.pin1.2",
                        GET_SIM_RETRY_EMPTY);
            } else if (PhoneConstants.GEMINI_SIM_3 == mSlotId) {
                return SystemProperties.getInt("gsm.sim.retry.pin1.3",
                        GET_SIM_RETRY_EMPTY);
            } else if (PhoneConstants.GEMINI_SIM_4 == mSlotId) {
                return SystemProperties.getInt("gsm.sim.retry.pin1.4",
                        GET_SIM_RETRY_EMPTY);
            } else {
                Xlog.e(TAG, "getRetryPinCount sim id error");
                return -1;
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (GeminiUtils.REQUEST_SIM_SELECT == requestCode) {
            Xlog.d(TAG, "onActivityResult() requestCode=" + requestCode
                    + " resultCode=" + resultCode);
            if (RESULT_OK == resultCode) {
                mSlotId = data.getIntExtra(GeminiUtils.EXTRA_SLOTID, -1);
                Xlog.d(TAG, "mSlotId: " + mSlotId);
                updateTitle(mSlotId);
                updatePreferences();
            } else {
                finish();
            }
        }
    }
            
    @Override
    public void onBackPressed() {
        GeminiUtils.goBackSimSelection(this, true);
    }
}
