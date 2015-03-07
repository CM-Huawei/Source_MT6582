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
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import com.mediatek.phone.gemini.GeminiUtils;
import com.mediatek.phone.vt.VTCallUtils;
import com.mediatek.phone.wrapper.ITelephonyWrapper;

/**
 * Used to display an error dialog from within the Telephony service when an outgoing call fails
 */
public class ErrorDialogActivity extends Activity {
    private static final String TAG = ErrorDialogActivity.class.getSimpleName();

    public static final String SHOW_MISSING_VOICEMAIL_NO_DIALOG_EXTRA = "show_missing_voicemail";
    public static final String ERROR_MESSAGE_ID_EXTRA = "error_message_id";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ///  M: For VT drop back to voice call. @{
        final boolean showDropVoiceCallDialog = getIntent().getBooleanExtra(SHOW_VT_DROP_VOICECALL_EXTRA, false);
        if (showDropVoiceCallDialog) {
            final int error = getIntent().getIntExtra(ERROR_MESSAGE_ID_EXTRA, -1);
            mNumber = getIntent().getStringExtra(DROP_VOICECALL_NUMBER_EXTRA);
            mSlot = getIntent().getIntExtra(DROP_VOICECALL_SLOT_EXTRA, -1);
            if (error == -1) {
                Log.e(TAG, "ErrorDialogActivity called with no error type extra for VT drop voical call.");
                finish();
            }
            showDropVoiceCallDialog(error);
            return;
        }
        /// @}

        final boolean showVoicemailDialog = getIntent().getBooleanExtra(
                SHOW_MISSING_VOICEMAIL_NO_DIALOG_EXTRA, false);

        if (showVoicemailDialog) {
            showMissingVoicemailErrorDialog();
        } else {
            final int error = getIntent().getIntExtra(ERROR_MESSAGE_ID_EXTRA, -1);
            if (error == -1) {
                Log.e(TAG, "ErrorDialogActivity called with no error type extra.");
                finish();
            }
            showGenericErrorDialog(error);
        }
    }

    private void showGenericErrorDialog(CharSequence msg) {
        Log.d(TAG, "showGenericErrorDialog()... msg: " + msg);

        final DialogInterface.OnClickListener clickListener;

        final DialogInterface.OnCancelListener cancelListener;
        clickListener = new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                finish();
            }
        };
        cancelListener = new DialogInterface.OnCancelListener() {
            @Override
            public void onCancel(DialogInterface dialog) {
                finish();
            }
        };

        final AlertDialog errorDialog = new AlertDialog.Builder(this)
                .setMessage(msg).setPositiveButton(R.string.ok, clickListener)
                        .setOnCancelListener(cancelListener).create();

        errorDialog.show();
    }

    private void showGenericErrorDialog(int resid) {
        CharSequence msg = getResources().getText(resid);

        /// M: For ALPS0123404 @{
        msg = handlePowerOffCase(resid, msg);
        /// @}

        showGenericErrorDialog(msg);
    }

    private void showMissingVoicemailErrorDialog() {
        final AlertDialog missingVoicemailDialog = new AlertDialog.Builder(this)
        .setTitle(R.string.no_vm_number)
        .setMessage(R.string.no_vm_number_msg)
        .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    dontAddVoiceMailNumber();
                }})
        .setNegativeButton(R.string.add_vm_number_str, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    addVoiceMailNumberPanel(dialog);
                }})
        .setOnCancelListener(new DialogInterface.OnCancelListener() {
                @Override
                public void onCancel(DialogInterface dialog) {
                    dontAddVoiceMailNumber();
                }}).show();
    }


    private void addVoiceMailNumberPanel(DialogInterface dialog) {
        if (dialog != null) {
            dialog.dismiss();
        }

        // navigate to the Voicemail setting in the Call Settings activity.
        Intent intent = new Intent(CallFeaturesSetting.ACTION_ADD_VOICEMAIL);
        intent.setClass(this, CallFeaturesSetting.class);
        startActivity(intent);
        finish();
    }

    private void dontAddVoiceMailNumber() {
        finish();
    }

    //---------------MTK------------

    ///  M: For VT drop back to voice call. @{
    public static final String SHOW_VT_DROP_VOICECALL_EXTRA = "show_drop_voicecall";
    public static final String DROP_VOICECALL_NUMBER_EXTRA = "drop_voicecall_number";
    public static final String DROP_VOICECALL_SLOT_EXTRA = "drop_voicecall_slot";
    
    private String mNumber = null;
    private int mSlot = -1;
    
    private void showDropVoiceCallDialog(int resid) {
        final CharSequence msg = getResources().getText(resid);

        final DialogInterface.OnClickListener clickListener;
        final DialogInterface.OnClickListener clickNegativeListener;

        final DialogInterface.OnCancelListener cancelListener;
        clickListener = new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                // we did not turn on speaker since now, so no need to turn off speaker.
                // PhoneUtils.turnOnSpeaker(PhoneGlobals.getInstance(), false, true);
                VTCallUtils.makeVoiceReCall(mNumber, mSlot);
                finish();
            }
        };
        clickNegativeListener = new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                finish();
            }
        };
        cancelListener = new DialogInterface.OnCancelListener() {
            @Override
            public void onCancel(DialogInterface dialog) {
                finish();
            }
        };
        final AlertDialog errorDialog = new AlertDialog.Builder(this).setMessage(msg)
                .setPositiveButton(R.string.ok, clickListener)
                .setNegativeButton(R.string.cancel, clickNegativeListener)
                .setOnCancelListener(cancelListener)
                .create();
        errorDialog.show();
    }
    /// @}

    /// M: For ALPS0123404 @{
    private CharSequence handlePowerOffCase(int resId, CharSequence defaultValue) {
        CharSequence msg = defaultValue;
        if (resId == R.string.incall_error_power_off) {
            if (null != PhoneGlobals.getInstance().phoneMgr) {
                final int[] geminiSlots = GeminiUtils.getSlots();
                boolean hasInsertSim = false;
                StringBuffer sb = null;
                for (int i = 0; i < geminiSlots.length; i++) {
                    if (!ITelephonyWrapper.hasIccCard(geminiSlots[i])) {
                        if (sb == null) {
                            sb = new StringBuffer().append(geminiSlots[i] + 1);
                        } else {
                            sb.append(", ").append(geminiSlots[i] + 1);
                        }
                    } else {
                        hasInsertSim = true;
                    }
                }

                if (!hasInsertSim) {
                    Log.e(TAG, "should not come here, for PhoneVideoCallOptionHandler has already handle this case.");
                } else if (sb != null) {
                    msg = getResources().getString(
                            R.string.callFailed_simError_slotNumber, sb.toString());
                }
            }
        }
        Log.d(TAG, "handlePowerOffCase()... msg: " + msg);
        return msg;
    }
    /// @}

}
