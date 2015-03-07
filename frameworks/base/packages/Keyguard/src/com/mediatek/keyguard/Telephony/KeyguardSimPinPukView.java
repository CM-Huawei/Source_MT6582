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

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.INotificationManager;
import android.app.ITransientNotification;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.SystemProperties;
import android.provider.Settings;
import android.telephony.TelephonyManager;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.text.method.DigitsKeyListener;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.view.WindowManagerImpl;
import android.view.View.OnClickListener;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.LinearLayout.LayoutParams;
import android.widget.TextView.OnEditorActionListener;

//import com.android.internal.policy.impl.keyguard.EmergencyButton;
//import com.android.internal.policy.impl.keyguard.KeyguardUpdateMonitor;
import com.android.internal.telephony.ITelephony;
import com.android.internal.telephony.IccCardConstants;
import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.TelephonyProperties;
import com.android.internal.telephony.IccCardConstants.State;
import com.android.internal.telephony.TelephonyIntents;

import com.mediatek.common.telephony.ITelephonyEx;
import com.mediatek.keyguard.ext.IKeyguardUtilExt;
import com.mediatek.keyguard.ext.KeyguardPluginFactory;
import com.mediatek.keyguard.ext.ICardInfoExt;
import com.mediatek.keyguard.ext.IOperatorSIMString;
import com.mediatek.keyguard.ext.IOperatorSIMString.SIMChangedTag;

/**
 * M: Displays a PIN/PUK pad for unlocking.
 */
public class KeyguardSimPinPukView extends KeyguardAbsKeyInputView
        implements KeyguardSecurityView, OnEditorActionListener, TextWatcher {
    private static final String TAG = "KeyguardSimPinPukView";

    private ProgressDialog mSimUnlockProgressDialog = null;
    private volatile boolean mSimCheckInProgress;
    KeyguardUpdateMonitor mUpdateMonitor = null;
    
    public int mSimId = PhoneConstants.GEMINI_SIM_1;
    
    //private TextView mHeaderText;
    //private TextView mTimesLeft = null;
    private TextView mSIMCardName = null;
    
    private TextView mPinText;
    
    private int mUnlockEnterState;

    private int mPinRetryCount;
    private int mPukRetryCount;

    /// M: Support GeminiPlus
    private boolean mSimFirstBoot[];

    private String mPukText;
    private String mNewPinText;
    private StringBuffer mSb = null;

    // M: Used to set left and right padding of sim card name
    private int mSIMCardNamePadding;
    
    /// M: Save Sim Card dialog, we will close this dialog when phone state change to ringing or offhook
    private AlertDialog mSimCardDialog;
    
    /// M: wait next SIM ME state reflash flag
    private KeyguardSecurityModel mSecurityModel;
    private int mNextRepollStateSimId = -1;
    private IccCardConstants.State mLastSimState = IccCardConstants.State.UNKNOWN;
    
    private static final int SIMLOCK_TYPE_PIN_PUK = 1;
    private static final int SIMLOCK_TYPE_SIMMELOCK = 2;
    static final int VERIFY_TYPE_PIN = 501;
    static final int VERIFY_TYPE_PUK = 502;
    static final int VERIFY_TYPE_SIMMELOCK = 503;
    
 // size limits for the pin.
    private static final int MIN_PIN_LENGTH = 4;
    private static final int MAX_PIN_LENGTH = 8;

    private static final int GET_SIM_RETRY_EMPTY = -1;

    private static final int STATE_ENTER_PIN = 0;
    private static final int STATE_ENTER_PUK = 1;
    private static final int STATE_ENTER_NEW = 2;
    private static final int STATE_REENTER_NEW = 3;
    private static final int STATE_ENTER_FINISH = 4;
    private static final int STATE_ENTER_ME = 5;
    private String[] strLockName = {" [NP]", " [NSP]"," [SP]"," [CP]"," [SIMP]"};// Lock category name string Temp use for QA/RD
    private static final int SIMPINPUK_WAIT_STATE_CHANGE_TIMEOUT = 6000; //ms

    /// M: for card info extend
    private ICardInfoExt mCardInfoExt;
    /// M: for get the proper SIM UIM string according to operator. 
    private IOperatorSIMString mIOperatorSIMString;
    
    /**
     * Used to dismiss SimPinPuk view after a delay
     */
    private Runnable mDismissSimPinPukRunnable = new Runnable() {
        public void run() {
            sendVerifyResult(KeyguardSimPinPukView.VERIFY_TYPE_PIN, false); // notify telephony simPinPuk exiting
            mUpdateMonitor.reportSimUnlocked(mSimId);
        }
    };
    
    KeyguardUpdateMonitorCallback mInfoCallback = new KeyguardUpdateMonitorCallback() {
        
        @Override
        public void onSimStateChanged(IccCardConstants.State simState, int simId) {
            KeyguardUtils.xlogD(TAG, "onSimStateChanged: " + simState + ", simId=" + simId);
            if(simId == mSimId)
            {
                if (mSimUnlockProgressDialog != null) {
                    mSimUnlockProgressDialog.hide();
                }
                mHandler.removeCallbacks(mDismissSimPinPukRunnable);
                
                if (IccCardConstants.State.READY == simState) {
                    simStateReadyProcess();
                } else if (IccCardConstants.State.NOT_READY == simState || IccCardConstants.State.ABSENT == simState) {
                    sendVerifyResult(KeyguardSimPinPukView.VERIFY_TYPE_PIN, false);
                    mCallback.dismiss(true);  // it will try next security screen or finish
                } else if (IccCardConstants.State.NETWORK_LOCKED == simState) {
                    if (0 == getRetryMeCount(mSimId)) { //permanently locked, exit
                        // do not show permanently locked dialog here, it is already show in ViewMediator
                        KeyguardUtils.xlogD(TAG, "onSimStateChanged: ME retrycount is 0, dismiss it");
                        sendVerifyResult(KeyguardSimPinPukView.VERIFY_TYPE_SIMMELOCK, false);
                        mUpdateMonitor.setPINDismiss(mSimId, KeyguardUpdateMonitor.SimLockType.SIM_LOCK_ME, true);
                        mCallback.dismiss(true);
                    } else {
                        updateSimState();   // show next ME lock guiding
                    }
                } else if (IccCardConstants.State.PIN_REQUIRED == simState
                          ||IccCardConstants.State.PUK_REQUIRED == simState ) {
                    // reset pintext and show current sim state again
                    mPinText.setText("");
                    mSecurityMessageDisplay.setMessage(mSb.toString(), true);
                }
                mLastSimState = simState;
            } else if (simId == mNextRepollStateSimId) {
                if (mSimUnlockProgressDialog != null) {
                    mSimUnlockProgressDialog.hide();
                }
                
                if (IccCardConstants.State.READY == simState) {
                    // pretend current sim is still ME lock state
                    mLastSimState = IccCardConstants.State.NETWORK_LOCKED;
                    simStateReadyProcess();
                } else {
                    // exit current SIM unlock to show next SIM unlock
                    mCallback.dismiss(true);  
                    mLastSimState = simState;
                }
            }
        }
    };

    private void initMembers() {
        mSb = new StringBuffer();
        mUpdateMonitor = KeyguardUpdateMonitor.getInstance(getContext());
        mSecurityModel = new KeyguardSecurityModel(getContext());
        
        /// M: Support GeminiPlus
        mSimFirstBoot = new boolean[KeyguardUtils.getNumOfSim()];
        for (int i = 0; i < KeyguardUtils.getNumOfSim(); i++) {
            mSimFirstBoot[i] = false;
        }
    }

    public KeyguardSimPinPukView(Context context) {
        this(context, null);
    }

    public KeyguardSimPinPukView(Context context, AttributeSet attrs) {
        super(context, attrs);
        initMembers();
        /// M: Init keyguard operator plugin @{
        try {
            mKeyguardUtilExt = KeyguardPluginFactory.getKeyguardUtilExt(context);
            mCardInfoExt = KeyguardPluginFactory.getCardInfoExt(context);
            mIOperatorSIMString = KeyguardPluginFactory.getOperatorSIMString(context);
        } catch (Exception e) {
            e.printStackTrace();
        }

        // Watch for interesting updates
        final IntentFilter filter = new IntentFilter();
        filter.addAction(TelephonyIntents.ACTION_SIM_INFO_UPDATE);
        filter.addAction(TelephonyIntents.ACTION_SIM_INSERTED_STATUS);
        filter.addAction(TelephonyIntents.ACTION_SIM_NAME_UPDATE);
        context.registerReceiver(mBroadcastReceiver, filter);
        /// @}
    }
    
    @Override
    public void onAttachedToWindow() {
        mUpdateMonitor.registerCallback(mInfoCallback);
    }

    
    @Override
    public void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        mHandler.removeCallbacks(mDismissSimPinPukRunnable);
        mUpdateMonitor.removeCallback(mInfoCallback);
    }
    
    public void setSimId(int simId) {
        Log.i(TAG,"setSimId="+simId);
        mSimId = simId;
        updateSimState();
        if (KeyguardUtils.isGemini()) {
            /// M: A dialog set view to another one, it did not refresh displaying along with it , so dismiss it and set it to null.
            if (mSimCardDialog != null) {
                if (mSimCardDialog.isShowing()) {
                    mSimCardDialog.dismiss();
                }
                mSimCardDialog = null;
            }
            String siminfoupdate = SystemProperties.get(TelephonyProperties.PROPERTY_SIM_INFO_READY, "false");
            if (siminfoupdate.equals("true")) {
                KeyguardUtils.xlogD(TAG,"siminfo already update, we should read value from the siminfoxxxx");
               dealwithSIMInfoChanged(mSimId);
            }
        }
    }

    public void resetState() {
        //mSecurityMessageDisplay.setMessage(com.android.internal.R.string.kg_sim_pin_instructions, true);
        mPasswordEntry.setEnabled(true);
    }

    @Override
    protected int getPasswordTextViewId() {
        return R.id.simpinEntry;
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        

        final View ok = findViewById(R.id.key_enter);
        if (ok != null) {
            ok.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    doHapticKeyClick();
                    verifyPasswordAndUnlock();
                }
            });
        }

        // The delete button is of the PIN keyboard itself in some (e.g. tablet) layouts,
        // not a separate view
        View pinDelete = findViewById(R.id.delete_button);
        if (pinDelete != null) {
            pinDelete.setVisibility(View.VISIBLE);
            pinDelete.setOnClickListener(new OnClickListener() {
                public void onClick(View v) {
                    CharSequence str = mPasswordEntry.getText();
                    if (str.length() > 0) {
                        mPasswordEntry.setText(str.subSequence(0, str.length()-1));
                    }
                    doHapticKeyClick();
                }
            });
            pinDelete.setOnLongClickListener(new View.OnLongClickListener() {
                public boolean onLongClick(View v) {
                    mPasswordEntry.setText("");
                    doHapticKeyClick();
                    return true;
                }
            });
        }

        mPasswordEntry.setKeyListener(DigitsKeyListener.getInstance());
        mPasswordEntry.setInputType(InputType.TYPE_CLASS_NUMBER
                | InputType.TYPE_NUMBER_VARIATION_PASSWORD);

        mPasswordEntry.requestFocus();
        
        ///M: begin @{
        final Button dismissButton = (Button)findViewById(R.id.key_dismiss);
        if (dismissButton != null) {
            dismissButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    int lockType = SIMLOCK_TYPE_PIN_PUK;
                    int verifyType = VERIFY_TYPE_PIN;
                    
                    if (IccCardConstants.State.PIN_REQUIRED == mUpdateMonitor.getSimState(mSimId)) {
                        mUpdateMonitor.setPINDismiss(mSimId, KeyguardUpdateMonitor.SimLockType.SIM_LOCK_PIN, true);
                        lockType = SIMLOCK_TYPE_PIN_PUK;
                        verifyType = VERIFY_TYPE_PIN;                    
                    } else if (IccCardConstants.State.PUK_REQUIRED == mUpdateMonitor.getSimState(mSimId)){
                        mUpdateMonitor.setPINDismiss(mSimId, KeyguardUpdateMonitor.SimLockType.SIM_LOCK_PUK, true);
                        lockType = SIMLOCK_TYPE_PIN_PUK;
                        verifyType = VERIFY_TYPE_PUK;
                    } else if (IccCardConstants.State.NETWORK_LOCKED == mUpdateMonitor.getSimState(mSimId)){
                        mUpdateMonitor.setPINDismiss(mSimId, KeyguardUpdateMonitor.SimLockType.SIM_LOCK_ME, true);
                        lockType = SIMLOCK_TYPE_SIMMELOCK;
                        verifyType = VERIFY_TYPE_SIMMELOCK;
                    }

                    setSimLockScreenDone(mSimId, lockType);
                    Intent t = new Intent("action_pin_dismiss");
                    t.putExtra("simslot", mSimId);
                    mContext.sendBroadcast(t);

                    mPinText.setText("");
                    sendVerifyResult(verifyType,false);
                    mCallback.userActivity(0);
                    mCallback.dismiss(true);
                    return;
                }
            });
        }
        dismissButton.setText(R.string.dismiss);
        
        mPinText = (TextView)findViewById(R.id.simpinEntry);
        mSIMCardName = (TextView) findViewById(R.id.SIMCardName);
        
        mSIMCardNamePadding = mContext.getResources().
                getDimensionPixelSize(R.dimen.sim_card_name_padding);

        mSecurityMessageDisplay.setTimeout(0); // don't show ownerinfo/charging status by default
    }

    @Override
    public void showUsabilityHint() {
    }
    
    @Override
    public void onPause() {
        if (mSimUnlockProgressDialog != null) {
            mSimUnlockProgressDialog.dismiss();
            mSimUnlockProgressDialog = null;
        }
    }

    @Override
    public void onResume(int reason) {
        if (mSimUnlockProgressDialog != null) {
            mSimUnlockProgressDialog.dismiss();
            mSimUnlockProgressDialog = null;
        }
        
        /// M: if has IME, then hide it @{
        InputMethodManager imm = ((InputMethodManager)mContext.getSystemService(Context.INPUT_METHOD_SERVICE));
        if (imm.isActive()) {
            Log.i(TAG, "IME is showing, we should hide it");
            imm.hideSoftInputFromWindow(this.getWindowToken(), InputMethodManager.HIDE_NOT_ALWAYS);  
        }
        /// @}
    }

    @Override
    protected boolean shouldLockout(long deadline) {
        // SIM PIN doesn't have a timed lockout
        return false;
    }

    private void setInputInvalidAlertDialog(CharSequence message, boolean shouldDisplay) {
        StringBuilder sb = new StringBuilder(message);

        if (shouldDisplay) {
            AlertDialog newDialog = new AlertDialog.Builder(mContext)
            .setMessage(sb)
            .setPositiveButton(com.android.internal.R.string.ok, null)
            .setCancelable(true)
            .create();

            newDialog.getWindow().setType(
                    WindowManager.LayoutParams.TYPE_KEYGUARD_DIALOG);
            newDialog.getWindow().addFlags(
                    WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            newDialog.show();
        } else {
             Toast.makeText(mContext, sb).show();
        }
    }
    
  /*  private int getRetryPukCount(final int simId) {
        /// M: Support GeminiPlus
        if (mSimId == PhoneConstants.GEMINI_SIM_4) {
            return SystemProperties.getInt("gsm.sim.retry.puk1.4",GET_SIM_RETRY_EMPTY);
        } else if (mSimId == PhoneConstants.GEMINI_SIM_3) {
            return SystemProperties.getInt("gsm.sim.retry.puk1.3",GET_SIM_RETRY_EMPTY);
        } else if (mSimId == PhoneConstants.GEMINI_SIM_2) {
            return SystemProperties.getInt("gsm.sim.retry.puk1.2",GET_SIM_RETRY_EMPTY);
        } else {
            return SystemProperties.getInt("gsm.sim.retry.puk1",GET_SIM_RETRY_EMPTY);
        }
    }*/

    private int getRetryPinCount(final int simId) {
        /// M: Support GeminiPlus
        if (mSimId == PhoneConstants.GEMINI_SIM_4) {
            return SystemProperties.getInt("gsm.sim.retry.pin1.4",GET_SIM_RETRY_EMPTY);
        } else if (mSimId == PhoneConstants.GEMINI_SIM_3) {
            return SystemProperties.getInt("gsm.sim.retry.pin1.3",GET_SIM_RETRY_EMPTY);
        } else if (mSimId == PhoneConstants.GEMINI_SIM_2) {
            return SystemProperties.getInt("gsm.sim.retry.pin1.2",GET_SIM_RETRY_EMPTY);
        } else {
            return SystemProperties.getInt("gsm.sim.retry.pin1",GET_SIM_RETRY_EMPTY);
        }
    }

    private int getRetryMeCount(final int simId) {
        return mUpdateMonitor.getSimMeLeftRetryCount(simId);
    }
    private void minusRetryMeCount(final int simId) {
        mUpdateMonitor.minusSimMeLeftRetryCount(simId);
    }
    private String getRetryPuk(final int simId) {
        mPukRetryCount = KeyguardUtils.getRetryPukCount(simId);
        switch (mPukRetryCount) {
        case GET_SIM_RETRY_EMPTY:
            return " ";
        //case 1:
        //    return "(" + mContext.getString(R.string.one_retry_left) + ")";
        default:
            return "(" + mContext.getString(R.string.retries_left, mPukRetryCount) + ")";
        }
    }
    private String getRetryPin(final int simId) {
        mPinRetryCount = getRetryPinCount(simId);
        switch (mPinRetryCount) {
        case GET_SIM_RETRY_EMPTY:
            return " ";
        //case 1:
        //    return "(" + mContext.getString(R.string.one_retry_left) + ")";
        default:
            return "(" + mContext.getString(R.string.retries_left, mPinRetryCount) + ")";
        }
    }
    private String getRetryMe(final int simId) {
        int meRetryCount = getRetryMeCount(simId);
        return "(" + mContext.getString(R.string.retries_left,meRetryCount) + ")";
    }
    private boolean validatePin(String pin, boolean isPUK) {
        // for pin, we have 4-8 numbers, or puk, we use only 8.
        int pinMinimum = isPUK ? MAX_PIN_LENGTH : MIN_PIN_LENGTH;
        // check validity
        if (pin == null || pin.length() < pinMinimum
                || pin.length() > MAX_PIN_LENGTH) {
            return false;
        } else {
            return true;
        }
    }
    
    private void updatePinEnterScreen() {

        switch (mUnlockEnterState) {
            case STATE_ENTER_PUK:
               mPukText = mPinText.getText().toString();
               if (validatePin(mPukText, true)) {
                  mUnlockEnterState = STATE_ENTER_NEW;
                  mSb.delete(0, mSb.length());
                  mSb.append(mContext.getText(R.string.keyguard_password_enter_new_pin_code));
                  mSecurityMessageDisplay.setMessage(mSb.toString(), true);
               } else {
                  mSecurityMessageDisplay.setMessage(R.string.invalidPuk, true);
               }
               break;

             case STATE_ENTER_NEW:
                 mNewPinText = mPinText.getText().toString();
                 if (validatePin(mNewPinText, false)) {
                    mUnlockEnterState = STATE_REENTER_NEW;
                    mSb.delete(0, mSb.length());
                    mSb.append(mContext.getText(R.string.keyguard_password_Confirm_pin_code));
                    mSecurityMessageDisplay.setMessage(mSb.toString(), true);
                 } else {
                    mSecurityMessageDisplay.setMessage(R.string.keyguard_code_length_prompt, true);
                 }
                 break;

             case STATE_REENTER_NEW:
                if (!mNewPinText.equals(mPinText.getText().toString())) {
                    mUnlockEnterState = STATE_ENTER_NEW;
                    mSb.delete(0, mSb.length());
                    mSb.append(mContext.getText(R.string.keyguard_code_donnot_mismatch));
                    mSb.append(mContext.getText(R.string.keyguard_password_enter_new_pin_code));
                    mSecurityMessageDisplay.setMessage(mSb.toString(), true);
                } else {
                   mUnlockEnterState = STATE_ENTER_FINISH;
                   mSecurityMessageDisplay.setMessage("", true);
                }
                break;

                default:
                    break;
        }
        mPinText.setText("");
        mCallback.userActivity(0);
    }

    /**
     * Since the IPC can block, we want to run the request in a separate thread
     * with a callback.
     */
    private abstract class CheckSimPinPuk extends Thread {
        private final String mPin;
        private final String mPuk;
        private boolean mResult;

        protected CheckSimPinPuk(String pin) {
            mPin = pin;
            mPuk = null;
        }
        protected CheckSimPinPuk(String pin, int simId) {
            mPin = pin;
            mPuk = null;
        }

        protected CheckSimPinPuk(String puk, String pin, int simId) {
            mPin = pin;
            mPuk = puk;
        }

        abstract void onSimCheckResponse(boolean success);

        @Override
        public void run() {
            try {
                Log.d(TAG, "CheckSimPinPuk, " + "mSimId =" + mSimId);
                if (KeyguardUtils.isGemini()) {
                    if (mUpdateMonitor.getSimState(mSimId) == IccCardConstants.State.PIN_REQUIRED) {
                        mResult = ITelephony.Stub.asInterface(ServiceManager.checkService("phone")).supplyPinGemini(
                                mPin, mSimId);
                    } else if (mUpdateMonitor.getSimState(mSimId) == IccCardConstants.State.PUK_REQUIRED) {
                        mResult = ITelephony.Stub.asInterface(ServiceManager.checkService("phone")).supplyPukGemini(
                                mPuk, mPin, mSimId);
                    }
                } else {
                    if (mUpdateMonitor.getSimState(mSimId) == IccCardConstants.State.PIN_REQUIRED) {
                        mResult = ITelephony.Stub.asInterface(ServiceManager.checkService("phone")).supplyPin(
                                mPin);
                    } else if (mUpdateMonitor.getSimState(mSimId) == IccCardConstants.State.PUK_REQUIRED) {
                        mResult = ITelephony.Stub.asInterface(ServiceManager.checkService("phone")).supplyPuk(
                                mPuk, mPin);
                    }
                }
                Log.d(TAG, "CheckSimPinPuk, " + "mSimId =" + mSimId+" mResult="+mResult);

				
                if(mResult) {		
                    // Create timer then wait for SIM_STATE_CHANGE for ready or network_lock
                    KeyguardUtils.xlogD(TAG, "CheckSimPinPuk.run(), mResult is true(success), so we postDelayed a timeout runnable object");
                    mHandler.postDelayed(mDismissSimPinPukRunnable, SIMPINPUK_WAIT_STATE_CHANGE_TIMEOUT);
                }
				
                mHandler.post(new Runnable() {
                    public void run() {
                        onSimCheckResponse(mResult);
                    }
                });
            } catch (RemoteException e) {
                mHandler.post(new Runnable() {
                    public void run() {
                        onSimCheckResponse(false);
                    }
                });
            }
        }
    }

    /**
     * Since the IPC can block, we want to run the request in a separate thread
     * with a callback.
     */
    private static final int VERIFY_RESULT_PASS = 0;
    private static final int VERIFY_INCORRECT_PASSWORD = 1;
    private static final int VERIFY_RESULT_EXCEPTION = 2;

    private abstract class CheckSimMe extends Thread {
        private final String mPasswd;
        private int mResult;

        protected CheckSimMe(String passwd, int simId) {
            mPasswd = passwd;
        }
        abstract void onSimMeCheckResponse(final int ret);

        @Override
        public void run() {
            try {
                Log.d(TAG, "CheckMe, " + "mSimId =" + mSimId);
                mResult = ITelephonyEx.Stub.asInterface(ServiceManager.checkService("phoneEx")).supplyNetworkDepersonalization(
                        mPasswd, mSimId);
                Log.d(TAG, "CheckMe, " + "mSimId =" + mSimId+" mResult="+mResult);

                if (VERIFY_RESULT_PASS == mResult) {
                    // Create timer then wait for SIM_STATE_CHANGE for ready or network_lock
                    KeyguardUtils.xlogD(TAG, "CheckSimMe.run(), VERIFY_RESULT_PASS == ret, so we postDelayed a timeout runnable object");
                    mHandler.postDelayed(mDismissSimPinPukRunnable, SIMPINPUK_WAIT_STATE_CHANGE_TIMEOUT);
                }
				
                mHandler.post(new Runnable() {
                    public void run() {
                        onSimMeCheckResponse(mResult);
                    }
                });
            } catch (RemoteException e) {
                mHandler.post(new Runnable() {
                    public void run() {
                        onSimMeCheckResponse(VERIFY_RESULT_EXCEPTION);
                    }
                });
            }
        }
    }

    private Dialog getSimUnlockProgressDialog() {
        if (mSimUnlockProgressDialog == null) {
            mSimUnlockProgressDialog = new ProgressDialog(mContext);
            /// M: Change the String with SIM according to Operator. @{
            String msg = mContext.getString(R.string.kg_sim_unlock_progress_dialog_message);
            msg = mIOperatorSIMString.getOperatorSIMString(msg, -1, SIMChangedTag.DELSIM, mContext);
            mSimUnlockProgressDialog.setMessage(msg);
            /// @}
            mSimUnlockProgressDialog.setIndeterminate(true);
            mSimUnlockProgressDialog.setCancelable(false);
            if (!(mContext instanceof Activity)) {
                mSimUnlockProgressDialog.getWindow().setType(
                        WindowManager.LayoutParams.TYPE_KEYGUARD_DIALOG);
            }
        }
        return mSimUnlockProgressDialog;
    }

    @Override
    protected void verifyPasswordAndUnlock() {
        String entry = mPasswordEntry.getText().toString();
        
        ///M: here only for PIN code
        if ((false == validatePin(entry, false)) && 
            (mUpdateMonitor.getSimState(mSimId) == IccCardConstants.State.PIN_REQUIRED
            ||mUpdateMonitor.getSimState(mSimId) == IccCardConstants.State.NETWORK_LOCKED)) {
            // otherwise, display a message to the user, and don't submit.
            if (mUpdateMonitor.getSimState(mSimId) == IccCardConstants.State.PIN_REQUIRED) {
            mSecurityMessageDisplay.setMessage(R.string.kg_invalid_sim_pin_hint, true);
            } else { 
                // hint to enter 4-8 digits for network_lock mode
                mSecurityMessageDisplay.setMessage(R.string.keyguard_code_length_prompt, true);
            }
            mPasswordEntry.setText("");
            mCallback.userActivity(0);
            return;
        }
        dealWithPinOrPukUnlock();
    }
    
    private void dealWithPinOrPukUnlock() {
        if (mUpdateMonitor.getSimState(mSimId) == IccCardConstants.State.PIN_REQUIRED) {
            KeyguardUtils.xlogD(TAG, "onClick, check PIN, mSimId=" + mSimId);
            checkPin(mSimId);
        } else if (mUpdateMonitor.getSimState(mSimId) == IccCardConstants.State.PUK_REQUIRED) {
            KeyguardUtils.xlogD(TAG, "onClick, check PUK, mSimId=" + mSimId);
            checkPuk(mSimId);
        } else if (mUpdateMonitor.getSimState(mSimId) == IccCardConstants.State.NETWORK_LOCKED) {
            KeyguardUtils.xlogD(TAG, "onClick, check ME, mSimId=" + mSimId);
            checkMe(mSimId);
        } else {
            KeyguardUtils.xlogD(TAG, "wrong status, mSimId="+mSimId);
        }
    }
    
    private void checkPin() {
        checkPin(mSimId);
    }
    
    private void checkPin(int simId) {
        getSimUnlockProgressDialog().show();
        if (!mSimCheckInProgress) {
            mSimCheckInProgress = true; // there should be only one
            new CheckSimPinPuk(mPasswordEntry.getText().toString(), mSimId) {
                void onSimCheckResponse(final boolean success) {
                    KeyguardUtils.xlogD(TAG, "checkPin onSimLockChangedResponse, success = " + success);
                    if (success) {
                        KeyguardUtils.xlogD(TAG, "checkPin onSimLockChangedResponse, success !");
                        if (mKeyguardUtilExt.needShowPassToast()) {
                            KeyguardUtils.xlogD(TAG, "checkPin showPassToast");
                            CharSequence cs = mContext.getString(R.string.pin_pass);
                            Toast.makeText(mContext, cs).show();;
                        }
                    } else {
                        mSb.delete(0, mSb.length());

                        if (mSimUnlockProgressDialog != null) {
                            mSimUnlockProgressDialog.hide();
                        }
                        if (mUnlockEnterState == STATE_ENTER_PIN) {
                            mSb.append(mContext.getText(R.string.keyguard_wrong_code_input));
                            if (0 == getRetryPinCount(mSimId)) { //goto PUK
                                mPinRetryCount = 0;
                                mSb.append(mContext.getText(R.string.keyguard_password_entering_puk_code));
                                mSb.append(" "+getRetryPuk(mSimId));
                                mUnlockEnterState = STATE_ENTER_PUK;
                            } else {
                                mSb.append(mContext.getText(R.string.keyguard_password_enter_pin_code));
                                mSb.append(" "+getRetryPin(mSimId));
                            }
                            mSecurityMessageDisplay.setMessage(mSb.toString(), true);
                            mPinText.setText("");
                        } else if (mUnlockEnterState == STATE_ENTER_PUK) {
                            mSb.append(mContext.getText(R.string.keyguard_wrong_code_input));
                            if (0 == KeyguardUtils.getRetryPukCount(mSimId)) { //goto PUK
                                mSb.append(mContext.getText(R.string.keyguard_password_entering_puk_code));
                                mSb.append(" "+getRetryPuk(mSimId));
                                mUnlockEnterState = STATE_ENTER_PUK;
                            } else {
                                mSb.append(mContext.getText(R.string.keyguard_password_enter_pin_code));
                                mSb.append(" "+getRetryPin(mSimId));
                            }
                            mSecurityMessageDisplay.setMessage(mSb.toString(), true);
                            mPinText.setText("");
                        }
                    }
                    mCallback.userActivity(0);
                    mSimCheckInProgress = false;
                }
            }.start();
        }
    }
    
    private void checkPuk() {
        checkPuk(mSimId);
    }
    
    private void checkPuk(int simId) {
        updatePinEnterScreen();
        if (mUnlockEnterState != STATE_ENTER_FINISH) {
            return;
        }
        getSimUnlockProgressDialog().show();
        if (!mSimCheckInProgress) {
            mSimCheckInProgress = true; // there should be only one
            new CheckSimPinPuk(mPukText, mNewPinText, mSimId) {
                void onSimCheckResponse(final boolean success) {
                    KeyguardUtils.xlogD(TAG, "checkPuk onSimLockChangedResponse, success = " + success);
                    if (success) {                        
                        KeyguardUtils.xlogD(TAG, "checkPuk onSimCheckResponse, success!");
                        if (mKeyguardUtilExt.needShowPassToast()) {
                            KeyguardUtils.xlogD(TAG, "checkPuk showPassToast");
                            CharSequence cs = mContext.getString(R.string.puk_pass);
                            Toast.makeText(mContext, cs).show();;
                        }
                    } else {
                        mSb.delete(0, mSb.length());

                        if (mSimUnlockProgressDialog != null) {
                            mSimUnlockProgressDialog.hide();
                        }
                        int retryCount = KeyguardUtils.getRetryPukCount(mSimId);
                        boolean countChange = (mPukRetryCount != retryCount);
                        String retryInfo = getRetryPuk(mSimId);
                        setSIMCardName(mSimId);
                        mSb.append(mContext.getText(R.string.keyguard_password_entering_puk_code));
                        mSb.append(" "+retryInfo);
                        mSecurityMessageDisplay.setMessage(mSb.toString(), true);
                        mPinText.setText("");
                        mUnlockEnterState = STATE_ENTER_PUK;
                        if (retryCount != 0) {
                            if (countChange) {
                                setInputInvalidAlertDialog(mContext
                                        .getString(R.string.keyguard_password_wrong_puk_code)
                                        + retryInfo, false);
                            } else {
                                setInputInvalidAlertDialog(mContext.getString(R.string.lockscreen_pattern_wrong), false);
                            }
                        } else {
                            setInputInvalidAlertDialog(mContext.getString(R.string.sim_permanently_locked), true);
                            sendVerifyResult(KeyguardSimPinPukView.VERIFY_TYPE_PUK, false);
                            mUpdateMonitor.setPINDismiss(mSimId, KeyguardUpdateMonitor.SimLockType.SIM_LOCK_PUK, true);
                            mCallback.dismiss(true);
                        }
                    }
                    mCallback.userActivity(0);
                    mSimCheckInProgress = false;
                }
            }.start();
        }
    }
    
    private void checkMe() {
        checkMe(mSimId);
    }
    

    private void checkMe(int simId) {
        getSimUnlockProgressDialog().show();
        if (!mSimCheckInProgress) {
            mSimCheckInProgress = true; // there should be only one
            new CheckSimMe(mPasswordEntry.getText().toString(), mSimId) {
                void onSimMeCheckResponse(final int ret) {
                    KeyguardUtils.xlogD(TAG, "checkMe onSimChangedResponse, ret = " + ret);
                    if (VERIFY_RESULT_PASS == ret) {
                        KeyguardUtils.xlogD(TAG, "checkMe VERIFY_RESULT_PASS == ret(we had sent runnable before");
                    } else if (VERIFY_INCORRECT_PASSWORD == ret) {
                        mSb.delete(0, mSb.length());
                        minusRetryMeCount(mSimId);

                        if (mSimUnlockProgressDialog != null) {
                            mSimUnlockProgressDialog.hide();
                        }
                        if (mUnlockEnterState == STATE_ENTER_ME) {
                            if (0 == getRetryMeCount(mSimId)) { //permanently locked
                                setInputInvalidAlertDialog(mContext.getText(R.string.simlock_slot_locked_message), true);
                                sendVerifyResult(KeyguardSimPinPukView.VERIFY_TYPE_SIMMELOCK, false);
                                mUpdateMonitor.setPINDismiss(mSimId, KeyguardUpdateMonitor.SimLockType.SIM_LOCK_ME, true);
                                mCallback.dismiss(true);
                            } else {
                                int category = mUpdateMonitor.getSimMeCategory(mSimId);
                                mSb.append(mContext.getText(R.string.keyguard_wrong_code_input));
                                mSb.append(mContext.getText(R.string.simlock_entersimmelock));
                                mSb.append(strLockName[category]+getRetryMe(mSimId));
                            }
                            mSecurityMessageDisplay.setMessage(mSb.toString(), true);
                            mPinText.setText("");
                        }
                    } else if (VERIFY_RESULT_EXCEPTION == ret) {
                        if (mSimUnlockProgressDialog != null) {
                            mSimUnlockProgressDialog.hide();
                        }
                        setInputInvalidAlertDialog("*** Exception happen, fail to unlock", true);
                        sendVerifyResult(KeyguardSimPinPukView.VERIFY_TYPE_SIMMELOCK, false);
                        mUpdateMonitor.setPINDismiss(mSimId, KeyguardUpdateMonitor.SimLockType.SIM_LOCK_ME, true);
                        mCallback.dismiss(true);
                    }
                    mCallback.userActivity(0);
                    mSimCheckInProgress = false;
                }
            }.start();
        }
    }
    
    private boolean isSimLockDisplay(int slot, int type) {
        if (slot < 0) {
            return false;
        }
        
        Long simLockState = Settings.System
                .getLong(mContext.getContentResolver(), Settings.System.SIM_LOCK_STATE_SETTING, 0);
        Long bitSet = simLockState;
        
        bitSet = bitSet >>> 2 * slot;
        if (SIMLOCK_TYPE_PIN_PUK == type) {
            if (0x1L == (bitSet & 0x1L)) {
                return true;
            } else {
                return false;
            }
        } else if (SIMLOCK_TYPE_SIMMELOCK == type) {
            bitSet = bitSet >>> 1;
            if (0x1L == (bitSet & 0x1L)) {
                return true;
            } else {
                return false;
            }
        }
        return true;
    }
    
    private void setSimLockScreenDone(int slot, int type) {
        if (slot < 0) {
            return ;
        }
        
        if (isSimLockDisplay(slot, type)) {
            KeyguardUtils.xlogD(TAG, "setSimLockScreenDone the SimLock display is done");
            return;
        }
        
        Long simLockState = Settings.System
                .getLong(mContext.getContentResolver(), Settings.System.SIM_LOCK_STATE_SETTING, 0);
        
        Long bitSet = 0x1L;
        
        bitSet = bitSet << (2 * slot);
        KeyguardUtils.xlogD(TAG, "setSimLockScreenDone1 bitset = " + bitSet);
        if (SIMLOCK_TYPE_SIMMELOCK == type) {
            bitSet = bitSet << 1;
        }
        KeyguardUtils.xlogD(TAG, "setSimLockScreenDone2 bitset = " + bitSet);
    
        simLockState += bitSet;
        Settings.System.putLong(mContext.getContentResolver(), Settings.System.SIM_LOCK_STATE_SETTING, simLockState);
    }
    
    public void sendVerifyResult(int verifyType, boolean bRet) {
        KeyguardUtils.xlogD(TAG, "sendVerifyResult verifyType = " + verifyType + " bRet = " + bRet);
        Intent retIntent = new Intent("android.intent.action.CELLCONNSERVICE").putExtra("start_type", "response");
        if (null == retIntent) {
            KeyguardUtils.xlogE(TAG, "sendVerifyResult new retIntent failed");
            return;
        }
        retIntent.putExtra("verfiy_type", verifyType);
        retIntent.putExtra("verfiy_result", bRet);
        mContext.startService(retIntent);
    }
    
    /**
     * Sets the text on the emergency button to indicate what action will be taken.
     * If there's currently a call in progress, the button will take them to the call
     * @param button the button to update
     */
 /*   public void updateEmergencyCallButtonState(Button button) {
        int newState = TelephonyManager.getDefault().getCallState();
        int textId;

        TelephonyManager telephony = (TelephonyManager) getContext().getSystemService(Context.TELEPHONY_SERVICE); 
        boolean isVoiceCapable = (telephony != null && telephony.isVoiceCapable());        

        if (isVoiceCapable) {
            if (newState == TelephonyManager.CALL_STATE_OFFHOOK) {
                // show "return to call" text and show phone icon
                textId = com.android.internal.R.string.lockscreen_return_to_call;
                int phoneCallIcon = R.drawable.mtk_pin_lock_emgencycall_icon;
                button.setCompoundDrawablesWithIntrinsicBounds(phoneCallIcon, 0, 0, 0);
            } else {
                textId = com.android.internal.R.string.lockscreen_emergency_call;
                int emergencyIcon = R.drawable.mtk_pin_lock_emgencycall_icon;
                button.setCompoundDrawablesWithIntrinsicBounds(emergencyIcon, 0, 0, 0);
            }
            button.setText(textId);
        } else {
           button.setVisibility(View.GONE);
        }
    }*/

    /// M: Support GeminiPlus
    private void setForTextNewCard(int simId) {
        TextView forText = (TextView)findViewById(R.id.ForText);
        StringBuffer forSb = new StringBuffer();

        forSb.append(mContext.getString(R.string.slot_id,simId + 1));
        forSb.append(" ");
        forSb.append(mContext.getText(R.string.new_simcard));
        //M: replace SIM to UIM if necessary
        forText.setText(mIOperatorSIMString.getOperatorSIMString(forSb.toString(), simId, SIMChangedTag.SIMTOUIM, mContext));
    }

    private void dealwithSIMInfoChanged(int slotId) {
        String operName = null;

        if (null != mUpdateMonitor && KeyguardUtils.isGemini()) {
            try {
               operName = KeyguardUtils.getOptrNameBySlot(mContext, slotId);
            } catch (IndexOutOfBoundsException e) {
                KeyguardUtils.xlogW(TAG, "getOptrNameBySlot exception, slotId=" + slotId);
            }
        }
        Log.i(TAG, "dealwithSIMInfoChanged, slotId="+slotId+", operName="+operName);
        TextView forText = (TextView)findViewById(R.id.ForText);
        if (null == operName) { //this is the new SIM card inserted
            /// M: Support GeminiPlus
            KeyguardUtils.xlogI(TAG, "SIM" + mSimId + " is first reboot");
            mSimFirstBoot[mSimId] = true;
            setForTextNewCard(mSimId);
            mSIMCardName.setVisibility(View.GONE);
        } else if (mSimId == slotId) {
            KeyguardUtils.xlogD(TAG, "dealwithSIMInfoChanged, we will refresh the SIMinfo");
            forText.setText(mContext.getString(R.string.slot_id,slotId+ 1)+ " ");
            mCardInfoExt.addOptrNameBySlot(mSIMCardName, slotId, mContext, operName);
            mSIMCardName.setVisibility(View.VISIBLE);
        }
    }
    
    private void setSIMCardName(final int slotId) {
        /// M: set SIM card name only for gemini products.
        if (!KeyguardUtils.isGemini()) {
            return;
        }
        
        String s = null;
        try {
            s = KeyguardUtils.getOptrNameBySlot(mContext, slotId);
        } catch (IndexOutOfBoundsException e) {
            KeyguardUtils.xlogW(TAG, "setSIMCardName::getOptrNameBySlot exception, slotId=" + slotId);
        }
        KeyguardUtils.xlogD(TAG, "slotId=" + slotId + ", mSimId=" + mSimId + ",s=" + s);

        /// M: Support GeminiPlus
        if (null != s) {
            mCardInfoExt.addOptrNameBySlot(mSIMCardName, slotId, mContext, s);
        } else if (mSimFirstBoot[mSimId] == true) {
            KeyguardUtils.xlogD(TAG, "setSIMCardName for the first reboot");
            setForTextNewCard(mSimId);
        } else {
            KeyguardUtils.xlogD(TAG, "setSIMCardName for seaching SIM card");
            mSIMCardName.setText(R.string.searching_simcard);
        }
    }

    /// M: Support GeminiPlus    
    public void updateSimState() {
        KeyguardUtils.xlogD(TAG, "updateSimSate, simId=" + mSimId + 
            ", mSimFirstBoot=" + mSimFirstBoot[mSimId]);

        setSIMCardName(mSimId);

        mSb.delete(0, mSb.length());
        IccCardConstants.State state = mUpdateMonitor.getSimState(mSimId);
        if (IccCardConstants.State.PUK_REQUIRED == state) {
           mSb.append(mContext.getText(R.string.keyguard_password_entering_puk_code));
           mSb.append(" "+getRetryPuk(mSimId));
           mUnlockEnterState = STATE_ENTER_PUK;
        } else if (IccCardConstants.State.PIN_REQUIRED == state) {
           mSb.append(mContext.getText(R.string.keyguard_password_enter_pin_code));
           mSb.append(" "+getRetryPin(mSimId));
           mUnlockEnterState = STATE_ENTER_PIN;
        } else if (IccCardConstants.State.NETWORK_LOCKED == state) {
           int category = mUpdateMonitor.getSimMeCategory(mSimId);
           mSb.append(mContext.getText(R.string.simlock_entersimmelock));
           mSb.append(strLockName[category]+getRetryMe(mSimId));
           mUnlockEnterState = STATE_ENTER_ME;
        }

        mPinText.setText("");
        mSecurityMessageDisplay.setMessage(mSb.toString(), true);
    }

    /* M: Override hideBouncer function to reshow message string */
    @Override
    public void hideBouncer(int duration) {
        mSecurityMessageDisplay.setMessage(mSb.toString(), true);
        super.hideBouncer(duration);
    }
    
    ///M: the process after receive SIM_STATE READY event
    /// call repollIccStateForNetworkLock if next locked SIM card is ME lock
    private void simStateReadyProcess() {
        mNextRepollStateSimId = getNextRepollStateSimId();
        if (mNextRepollStateSimId != -1) {
            try {
                getSimUnlockProgressDialog().show();
                Log.d(TAG, "repollIccStateForNetworkLock " + "simId =" + mNextRepollStateSimId);

                ///M: call repollIccStateForNetworkLock will trigger telephony to resend 
                /// sim_ state_change event of specified sim id
                ITelephonyEx.Stub.asInterface(ServiceManager.checkService("phoneEx"))
                    .repollIccStateForNetworkLock(mNextRepollStateSimId, true);
            } catch (RemoteException e) {
                Log.d(TAG, "repollIccStateForNetworkLock exception caught");
            }
        } else {
            mCallback.dismiss(true);  // it will try next security screen or finish
        }
    }

    /// M: check next lock state SIM card is ME lock or not
    /// return simId if we found otherwise return -1
    private int getNextRepollStateSimId(){
        if (IccCardConstants.State.NETWORK_LOCKED == mLastSimState) {
            for (int i = PhoneConstants.GEMINI_SIM_1; i <= KeyguardUtils.getMaxSimId(); i++) {
                if (!mSecurityModel.isPinPukOrMeRequired(i)) {
                    continue;
                }
                
                final IccCardConstants.State simState = mUpdateMonitor.getSimState(i);
                if(simState == IccCardConstants.State.NETWORK_LOCKED) {
                    return i;
                } else {
                    break;  // for PIN or PUK lock, return -1
                }
            }
        }
        return -1;
    }
    
    public static class Toast {
        static final String LOCAL_TAG = "Toast";
        static final boolean LOCAL_LOGV = false;

        final Handler mHandler = new Handler();
        final Context mContext;
        final TN mTN;
        int mGravity = Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM;
        int mY;
        View mView;

        public Toast(Context context) {
            mContext = context;
            mTN = new TN();
            mY = context.getResources().getDimensionPixelSize(com.android.internal.R.dimen.toast_y_offset);
        }

        public static Toast makeText(Context context, CharSequence text) {
            Toast result = new Toast(context);

            LayoutInflater inflate = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            View v = inflate.inflate(com.android.internal.R.layout.transient_notification, null);
            TextView tv = (TextView) v.findViewById(com.android.internal.R.id.message);
            tv.setText(text);

            result.mView = v;

            return result;
        }

        /**
         * Show the view for the specified duration.
         */
        public void show() {
            if (mView == null) {
                throw new RuntimeException("setView must have been called");
            }
            INotificationManager service = getService();
            String pkg = mContext.getPackageName();
            TN tn = mTN;
            try {
                service.enqueueToast(pkg, tn, 0);
            } catch (RemoteException e) {
                // Empty
            }
        }

        /**
         * Close the view if it's showing, or don't show it if it isn't showing yet. You do not normally have to call this.
         * Normally view will disappear on its own after the appropriate duration.
         */
        public void cancel() {
            mTN.hide();
        }

        private INotificationManager mService;

        private INotificationManager getService() {
            if (mService != null) {
                return mService;
            }
            mService = INotificationManager.Stub.asInterface(ServiceManager.getService("notification"));
            return mService;
        }

        private class TN extends ITransientNotification.Stub {
            final Runnable mShow = new Runnable() {
                public void run() {
                    handleShow();
                }
            };

            final Runnable mHide = new Runnable() {
                public void run() {
                    handleHide();
                }
            };

            private final WindowManager.LayoutParams mParams = new WindowManager.LayoutParams();

            WindowManagerImpl mWM;

            TN() {
                final WindowManager.LayoutParams params = mParams;
                params.height = WindowManager.LayoutParams.WRAP_CONTENT;
                params.width = WindowManager.LayoutParams.WRAP_CONTENT;
                params.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON;
                params.format = PixelFormat.TRANSLUCENT;
                params.windowAnimations = com.android.internal.R.style.Animation_Toast;
                params.type = WindowManager.LayoutParams.TYPE_KEYGUARD_DIALOG;
                params.setTitle("Toast");
            }

            /**
             * schedule handleShow into the right thread
             */
            public void show() {
                if (LOCAL_LOGV) {
                    KeyguardUtils.xlogD(LOCAL_TAG, "SHOW: " + this);
                }
                mHandler.post(mShow);
            }

            /**
             * schedule handleHide into the right thread
             */
            public void hide() {
                if (LOCAL_LOGV) {
                    KeyguardUtils.xlogD(LOCAL_TAG, "HIDE: " + this);
                }
                mHandler.post(mHide);
            }

            public void handleShow() {
                if (LOCAL_LOGV) {
                    KeyguardUtils.xlogD(LOCAL_TAG, "HANDLE SHOW: " + this + " mView=" + mView);
                }

                mWM = (WindowManagerImpl)mContext.getSystemService(Context.WINDOW_SERVICE);
                final int gravity = mGravity;
                mParams.gravity = gravity;
                if ((gravity & Gravity.HORIZONTAL_GRAVITY_MASK) == Gravity.FILL_HORIZONTAL) {
                    mParams.horizontalWeight = 1.0f;
                }
                if ((gravity & Gravity.VERTICAL_GRAVITY_MASK) == Gravity.FILL_VERTICAL) {
                    mParams.verticalWeight = 1.0f;
                }
                mParams.y = mY;
                if (mView != null) {
                    if (mView.getParent() != null) {
                        if (LOCAL_LOGV) {
                            KeyguardUtils.xlogD(LOCAL_TAG, "REMOVE! " + mView + " in " + this);
                        }
                        mWM.removeView(mView);
                    }
                    if (LOCAL_LOGV) {
                        KeyguardUtils.xlogD(LOCAL_TAG, "ADD! " + mView + " in " + this);
                    }
                    mWM.addView(mView, mParams);
                }
            }

            public void handleHide() {
                if (LOCAL_LOGV) {
                    KeyguardUtils.xlogD(LOCAL_TAG, "HANDLE HIDE: " + this + " mView=" + mView);
                }
                if (mView != null) {
                    // note: checking parent() just to make sure the view has
                    // been added... i have seen cases where we get here when
                    // the view isn't yet added, so let's try not to crash.
                    if (mView.getParent() != null) {
                        if (LOCAL_LOGV) {
                            KeyguardUtils.xlogD(LOCAL_TAG, "REMOVE! " + mView + " in " + this);
                        }
                        mWM.removeView(mView);
                    }

                    mView = null;
                }
            }
        }
    }
    /// M: Mediatek added variable for Operation plugin feature
    private IKeyguardUtilExt mKeyguardUtilExt;

    /// M: [ALPS00830104] Refresh the information while the window focus is changed.
    @Override
    public void onWindowFocusChanged(boolean hasWindowFocus) {
        if (hasWindowFocus) {
            IccCardConstants.State state = mUpdateMonitor.getSimState(mSimId);
            int pinRetryCount = getRetryPinCount(mSimId);
            int pukRetryCount = KeyguardUtils.getRetryPukCount(mSimId);
            if ((mUnlockEnterState == STATE_ENTER_PIN && mPinRetryCount != pinRetryCount)
                ||(mUnlockEnterState == STATE_ENTER_PUK && mPukRetryCount != pukRetryCount)) {
                updateSimState(); 
            }
        }
    }

    /// M: For Gemini enhancement feature to update siminfo when we received broadcast from Telephony framework
    private static final int MSG_SIMINFO_CHANGED = 1004;
    /// M: For Gemini enhancement feature, after boot up and we saved SimInfo names, Telephony framework may still
    /// update SimInfo names, so we need to handle it in this message hander 
    private static final int MSG_KEYGUARD_SIM_NAME_UPDATE = 1006;
    /// M: Workaround for IPO
    private static final int MSG_KEYGUARD_SIM_INSERT_STATE_CHANGE = 1008;

    
    private final BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (TelephonyIntents.ACTION_SIM_INFO_UPDATE.equals(action)){
                int slotId = intent.getIntExtra("slotId", 0); 
                KeyguardUtils.xlogD(TAG, "sim info update, slotId="+slotId);
                mHandler.sendMessage(mHandler.obtainMessage(
                        MSG_SIMINFO_CHANGED, slotId, 0));
            } else if (TelephonyIntents.ACTION_SIM_INSERTED_STATUS.equals(action)) {
                int slotId = intent.getIntExtra("slotId", 0); 
                KeyguardUtils.xlogD(TAG, "SIM_INSERTED_STATUS, slotId="+slotId);
                mHandler.sendMessage(mHandler.obtainMessage(
                        MSG_KEYGUARD_SIM_INSERT_STATE_CHANGE, slotId, 0));
            } else if (TelephonyIntents.ACTION_SIM_NAME_UPDATE.equals(action)) {
                int slotId = intent.getIntExtra("slotId", 0);
                KeyguardUtils.xlogD(TAG, "SIM_NAME_UPDATE, slotId="+slotId);
                mHandler.sendMessage(mHandler.obtainMessage(
                        MSG_KEYGUARD_SIM_NAME_UPDATE, slotId, 0));
            }
        }
    };
    
    private Handler mHandler = new Handler(Looper.myLooper(), null, true /*async*/) {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_SIMINFO_CHANGED:
                    handleSIMInfoChanged(msg.arg1);
                    break;
                case MSG_KEYGUARD_SIM_INSERT_STATE_CHANGE:
                    KeyguardUtils.xlogD(TAG, "MSG_KEYGUARD_SIM_INSERT_STATE_CHANGE, msg.arg1=" + msg.arg1);
                    handleSimInsertStateChange(msg.arg1);
                    break;
                case MSG_KEYGUARD_SIM_NAME_UPDATE:
                    KeyguardUtils.xlogD(TAG, "MSG_KEYGUARD_SIM_NAME_UPDATE, msg.arg1=" + msg.arg1);
                    handleSIMNameUpdate(msg.arg1);
                    break;
            }
        }
    };

    private void handleSIMInfoChanged(int slotId) { //update the siminfo
        KeyguardUtils.xlogD(TAG,"handleSIMInfoChanged, slotId=" + slotId + ", mSimId=" + mSimId);
        if (KeyguardUtils.isGemini()) {
            if (mSimId == slotId) {
               dealwithSIMInfoChanged(slotId);
            }
        }        
    }

    private void handleSimInsertStateChange(int slotId) {
        onLockScreenUpdate(slotId);
    }

    private void handleSIMNameUpdate(int slotId) {
       if (KeyguardUtils.isGemini()) {
           onLockScreenUpdate(slotId);
       }
    }

    private void onLockScreenUpdate(int slotId) {
        KeyguardUtils.xlogD(TAG, "onLockScreenUpdate name update, slotId=" + slotId + ", mSimId=" + mSimId);
        if (mSimId == slotId) {
            //refresh the name for the SIM Card
            setSIMCardName(slotId);
        }
    }
    
}

