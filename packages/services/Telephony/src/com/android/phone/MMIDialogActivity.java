/*
 * Copyright (C) 2013 The Android Open Source Project
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

import android.app.Activity;
import android.app.Dialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.AsyncResult;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.widget.Toast;

import com.android.internal.telephony.CallManager;
import com.android.internal.telephony.MmiCode;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneConstants;
import com.google.common.base.Preconditions;
import com.mediatek.phone.PhoneLog;
import com.mediatek.phone.gemini.GeminiUtils;
import com.mediatek.phone.wrapper.PhoneWrapper;

import java.util.List;

/**
 * Used to display a dialog from within the Telephony service when running an USSD code
 */
public class MMIDialogActivity extends Activity {
    private static final String TAG = MMIDialogActivity.class.getSimpleName();

    private Dialog mMMIDialog;

    private Handler mHandler;

    private CallManager mCM = CallManager.getInstance();
    private Phone mPhone = PhoneGlobals.getPhone();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        initSlot();
        mHandler = new Handler() {
                @Override
                public void handleMessage(Message msg) {
                    switch (msg.what) {
                        case PhoneGlobals.MMI_COMPLETE:
                            onMMIComplete((MmiCode) ((AsyncResult) msg.obj).result);
                            break;
                        case PhoneGlobals.MMI_CANCEL:
                            onMMICancel();
                            break;
                    }
                }
        };
        mCM.registerForMmiComplete(mHandler, PhoneGlobals.MMI_COMPLETE, null);
        if (mCM.getState() == PhoneConstants.State.OFFHOOK) {
            Toast.makeText(this, R.string.incall_status_dialed_mmi, Toast.LENGTH_SHORT).show();
        }

        /// M: For ALPS01374729. @{
        // register listener to listen the home key and recentApp has pressed.
        registerReceiver(mReceiver, mIntentFilter);
        PhoneUtils.sNoNeedStartUssdActivity = false;
        /// @}

        showMMIDialog();
    }

    private void showMMIDialog() {
        /// M: ALPS01276588 get MMICode instance refer to mSlot @{
        //
        /* Google code
        final List<? extends MmiCode> codes = mPhone.getPendingMmiCodes();
        */
        final List<? extends MmiCode> codes = PhoneWrapper.getPendingMmiCodes(mPhone, mSlot);
        PhoneLog.d(TAG, "code size: " + codes.size());
        /// @}
        if (codes.size() > 0) {
            final MmiCode mmiCode = codes.get(0);
            final Message message = Message.obtain(mHandler, PhoneGlobals.MMI_CANCEL);
            mMMIDialog = PhoneUtils.displayMMIInitiate(this, mmiCode, message, mMMIDialog);
        } else {
            finish();
        }
    }

    /**
     * Handles an MMI_COMPLETE event, which is triggered by telephony
     */
    private void onMMIComplete(MmiCode mmiCode) {
        // Check the code to see if the request is ready to
        // finish, this includes any MMI state that is not
        // PENDING.

        // if phone is a CDMA phone display feature code completed message
        int phoneType = mPhone.getPhoneType();
        if (phoneType == PhoneConstants.PHONE_TYPE_CDMA) {
            PhoneUtils.displayMMICompleteExt(mPhone, this, mmiCode, null, null, mSlot);
        } else if (phoneType == PhoneConstants.PHONE_TYPE_GSM) {
            if (mmiCode.getState() != MmiCode.State.PENDING) {
                PhoneLog.d(TAG, "Got MMI_COMPLETE, finishing dialog activity...");
                dismissDialogsAndFinish();
            }
        }
    }

    /**
     * Handles an MMI_CANCEL event, which is triggered by the button
     * (labeled either "OK" or "Cancel") on the "MMI Started" dialog.
     * @see PhoneUtils#cancelMmiCode(Phone)
     */
    private void onMMICancel() {
        Log.v(TAG, "onMMICancel()...");

        // First of all, cancel the outstanding MMI code (if possible.)
        PhoneUtils.cancelMmiCodeExt(mPhone, mSlot);

        // Regardless of whether the current MMI code was cancelable, the
        // PhoneApp will get an MMI_COMPLETE event very soon, which will
        // take us to the MMI Complete dialog (see
        // PhoneUtils.displayMMIComplete().)
        //
        // But until that event comes in, we *don't* want to stay here on
        // the in-call screen, since we'll be visible in a
        // partially-constructed state as soon as the "MMI Started" dialog
        // gets dismissed. So let's forcibly bail out right now.
        PhoneLog.d(TAG, "onMMICancel: finishing InCallScreen...");
        dismissDialogsAndFinish();
    }

    private void dismissDialogsAndFinish() {
        if (mMMIDialog != null) {
            mMMIDialog.dismiss();
        }
        if (mHandler != null) {
            mCM.unregisterForMmiComplete(mHandler);
        }
        finish();
    }
/// --------------------------------------------Mediatek---------------------------------------------
    int mSlot = GeminiUtils.getDefaultSlot();

    private void initSlot() {
        Intent intent = getIntent();
        Preconditions.checkNotNull(intent);
        mSlot = intent.getIntExtra(Constants.EXTRA_SLOT_ID, GeminiUtils.getDefaultSlot());
        PhoneLog.d(TAG, "intent = " + intent + ", slotid = " + mSlot);
    }

    /// For ALPS01374729. @{
    @Override
    protected void onDestroy() {
        PhoneLog.d(TAG, "onDestroy");
        unregisterReceiver(mReceiver);
        super.onDestroy();
    }

    // When user want to leave this activity by press home key or Recent App, we
    // should cancel the current ussd code.
    public IntentFilter mIntentFilter = new IntentFilter(Intent.ACTION_CLOSE_SYSTEM_DIALOGS);
    public BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            PhoneLog.d(TAG, "onReceive: cancel the request dialog. action = " + action);
            if (action.equals(Intent.ACTION_CLOSE_SYSTEM_DIALOGS)) {
                String reason = intent.getStringExtra("reason");
                if (reason != null && reason.equals("homekey")) {
                    PhoneUtils.cancelMmiCodeExt(mPhone, mSlot);
                    dismissDialogsAndFinish();
                    PhoneUtils.cancelUssdDialog();
                    PhoneUtils.dismissUssdDialog();
                    if (PhoneUtils.sUssdActivity == null) {
                        PhoneUtils.sNoNeedStartUssdActivity = true;
                    } else {
                        PhoneUtils.sNoNeedStartUssdActivity = false;
                    }
                }
            }
        }
    };
    /// @}
}
