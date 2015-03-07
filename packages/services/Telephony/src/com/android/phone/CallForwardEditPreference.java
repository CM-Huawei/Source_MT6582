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

package com.android.phone;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.res.TypedArray;
import android.os.AsyncResult;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.View;

import static com.android.phone.TimeConsumingPreferenceActivity.RESPONSE_ERROR;

import com.android.internal.telephony.CallForwardInfo;
import com.android.internal.telephony.CommandException;
import com.android.internal.telephony.CommandsInterface;
import com.android.internal.telephony.Phone;

import com.mediatek.phone.TimeConsumingPreferenceListener;
import com.mediatek.phone.gemini.GeminiUtils;
import com.mediatek.phone.wrapper.PhoneWrapper;
import com.mediatek.settings.CallSettings;
import com.mediatek.xlog.Xlog;


public class CallForwardEditPreference extends EditPhoneNumberPreference {
    private static final String LOG_TAG = "Settings/CallForwardEditPreference";
    private static final boolean DBG = true; //(PhoneApp.DBG_LEVEL >= 2);

    private static final String SRC_TAGS[]       = {"{0}"};
    private CharSequence mSummaryOnTemplate;
    /**
     * Remembers which button was clicked by a user. If no button is clicked yet, this should have
     * {@link DialogInterface#BUTTON_NEGATIVE}, meaning "cancel".
     *
     * TODO: consider removing this variable and having getButtonClicked() in
     * EditPhoneNumberPreference instead.
     */
    private int mButtonClicked;
    private int mServiceClass;
    private MyHandler mHandler = new MyHandler();
    int reason;
    Phone mPhone;
    CallForwardInfo mCallForwardInfo;
    TimeConsumingPreferenceListener mTcpListener;
    /// M: for gemini phone & handle other cf when cfu get fail @{
    private int mSimId;
    private boolean mCancel = false;
    private boolean mResult = true;
    private CallForwardInfo mLastCallForwardInfo;
    private static final String BUTTON_CFB_KEY   = "button_cfb_key";
    private static final String BUTTON_CFNRY_KEY = "button_cfnry_key";
    private static final String BUTTON_CFNRC_KEY = "button_cfnrc_key";
    /// @}

    public CallForwardEditPreference(Context context, AttributeSet attrs) {
        super(context, attrs);

        mPhone = PhoneGlobals.getPhone();
        mSummaryOnTemplate = this.getSummaryOn();

        TypedArray a = context.obtainStyledAttributes(attrs,
                R.styleable.CallForwardEditPreference, 0, R.style.EditPhoneNumberPreference);
        mServiceClass = a.getInt(R.styleable.CallForwardEditPreference_serviceClass,
                CommandsInterface.SERVICE_CLASS_VOICE);
        reason = a.getInt(R.styleable.CallForwardEditPreference_reason,
                CommandsInterface.CF_REASON_UNCONDITIONAL);
        a.recycle();

        if (DBG) {
            Xlog.d(LOG_TAG, "mServiceClass=" + mServiceClass + ", reason=" + reason);
        }
    }

    public CallForwardEditPreference(Context context) {
        this(context, null);
    }
    /// M: the simid is for support gemini phone
    void init(TimeConsumingPreferenceListener listener, boolean skipReading, int simId) {    
        mTcpListener = listener;
        Xlog.d(LOG_TAG, "tcpListener =" + mTcpListener);
        if (!skipReading) {
            /// M: Modified for VT call support. @{
            // Google code:
            /*
            phone.getCallForwardingOption(reason,
                    mHandler.obtainMessage(MyHandler.MESSAGE_GET_CF,
                            // unused in this case
                            CommandsInterface.CF_ACTION_DISABLE,
                            MyHandler.MESSAGE_GET_CF, null));
            */
            if (GeminiUtils.isGeminiSupport()) {
                mSimId = simId;
                Xlog.d(LOG_TAG, "init - mSimId =" + mSimId);
            }
            if (mServiceClass == CommandsInterface.SERVICE_CLASS_VIDEO) {
                PhoneWrapper.getVtCallForwardingOption(
                        mPhone, reason,
                        mHandler.obtainMessage(MyHandler.MESSAGE_GET_CF, reason,
                                MyHandler.MESSAGE_GET_CF, null), simId);
            } else {
                PhoneWrapper.getCallForwardingOption(
                        mPhone, reason,
                        mHandler.obtainMessage(MyHandler.MESSAGE_GET_CF, reason,
                                MyHandler.MESSAGE_GET_CF, null), simId);
            }
            /// @}

            if (mTcpListener != null) {
                mTcpListener.onStarted(this, true);
            }
        }
    }

    @Override
    protected void onBindDialogView(View view) {
        // default the button clicked to be the cancel button.
        mButtonClicked = DialogInterface.BUTTON_NEGATIVE;
        super.onBindDialogView(view);
    }

    @Override
    public void onClick(DialogInterface dialog, int which) {
        if (which == DialogInterface.BUTTON_NEUTRAL) {
            this.setSummaryOn("");
        }
        super.onClick(dialog, which);
        mButtonClicked = which;
    }

    @Override
    protected void onDialogClosed(boolean positiveResult) {
        super.onDialogClosed(positiveResult);
        /// M: when activity destroy & press cancel button do not enter
        if (mCancel || 0 == mButtonClicked) {
           return;
        }
        if (DBG) {
            Xlog.d(LOG_TAG, "mButtonClicked=" + mButtonClicked
                + ", positiveResult=" + positiveResult);
        }
        // Ignore this event if the user clicked the cancel button, or if the dialog is dismissed
        // without any button being pressed (back button press or click event outside the dialog).
        if (this.mButtonClicked != DialogInterface.BUTTON_NEGATIVE) {
            int action = (isToggled() || (mButtonClicked == DialogInterface.BUTTON_POSITIVE)) ?
                    CommandsInterface.CF_ACTION_REGISTRATION :
                    CommandsInterface.CF_ACTION_DISABLE;
            int time = (reason != CommandsInterface.CF_REASON_NO_REPLY) ? 0 : 20;
            final String number = getPhoneNumber();

            if (DBG) {
                Xlog.d(LOG_TAG, "callForwardInfo=" + mCallForwardInfo);
            }

            /// M:We save last callForwardInfo used for judge the set result.
            mLastCallForwardInfo = mCallForwardInfo;

            if (action == CommandsInterface.CF_ACTION_REGISTRATION
                    && mCallForwardInfo != null
                    && mCallForwardInfo.status == 1
                    && number.equals(mCallForwardInfo.number)) {
                // no change, do nothing
                if (DBG) {
                    Xlog.d(LOG_TAG, "no change, do nothing");
                }
            } else {
                // set to network
                if (DBG) {
                    Xlog.d(LOG_TAG, "reason=" + reason + ", action=" + action
                        + ", number=" + number);
                }

                // Display no forwarding number while we're waiting for
                // confirmation
                setSummaryOn("");

                // the interface of Phone.setCallForwardingOption has error:
                // should be action, reason...
                /// M: Modified for vt call support. @{
                // Google code:
                /*
                phone.setCallForwardingOption(action,
                        reason,
                        number,
                        time,
                        mHandler.obtainMessage(MyHandler.MESSAGE_SET_CF,
                                action,
                                MyHandler.MESSAGE_SET_CF));
                */
                if (mServiceClass == CommandsInterface.SERVICE_CLASS_VIDEO) {
                    PhoneWrapper.setVtCallForwardingOption(
                            mPhone, action, reason, number, time,
                            mHandler.obtainMessage(MyHandler.MESSAGE_SET_CF, action,
                                    MyHandler.MESSAGE_SET_CF), mSimId);
                } else {
                    PhoneWrapper.setCallForwardingOption(
                            mPhone, action, reason, number, time,
                            mHandler.obtainMessage(MyHandler.MESSAGE_SET_CF, action,
                                    MyHandler.MESSAGE_SET_CF), mSimId);
                }
                /// @}
                if (mTcpListener != null) {
                    mTcpListener.onStarted(this, false);
                }
            }
        }
        mButtonClicked = 0;
    }

    void handleCallForwardResult(CallForwardInfo cf) {
        mCallForwardInfo = cf;
        if (DBG) {
            Xlog.d(LOG_TAG, "handleGetCFResponse done, callForwardInfo=" + mCallForwardInfo);
        }

        setToggled(mCallForwardInfo.status == 1);
        setPhoneNumber(mCallForwardInfo.number);
        /// M: Update the summary text to avoid the phonenum == {0} after switch language
        updateSummaryText();
        updatePrefStatus();
    }
    /// M: update ui after set done    
    private void updatePrefStatus() {
        if (0 == this.reason) {
            PreferenceManager pm = this.getPreferenceManager();
            if (isToggled() && isEnabled()) {
                pm.findPreference(BUTTON_CFB_KEY).setEnabled(false);
                pm.findPreference(BUTTON_CFNRY_KEY).setEnabled(false);
                pm.findPreference(BUTTON_CFNRC_KEY).setEnabled(false);
            } else {
                if (!isToggled() && isEnabled()) {
                    pm.findPreference(BUTTON_CFB_KEY).setEnabled(true);
                }
                pm.findPreference(BUTTON_CFNRY_KEY).setEnabled(true);
                pm.findPreference(BUTTON_CFNRC_KEY).setEnabled(true);
            }
        }
    }

    private void updateSummaryText() {
        if (isToggled()) {
            CharSequence summaryOn;
            final String number = getRawPhoneNumber();
            if (number != null && number.length() > 0) {
                String values[] = { number };
                summaryOn = TextUtils.replace(mSummaryOnTemplate, SRC_TAGS, values);
            } else {
                summaryOn = getContext().getString(R.string.sum_cfu_enabled_no_number);
            }
            setSummaryOn(summaryOn);
        }

    }

    // Message protocol:
    // what: get vs. set
    // arg1: action -- register vs. disable
    // arg2: get vs. set for the preceding request
    private class MyHandler extends Handler {
        private static final int MESSAGE_GET_CF = 0;
        private static final int MESSAGE_SET_CF = 1;

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MESSAGE_GET_CF:
                    handleGetCFResponse(msg);
                    break;
                case MESSAGE_SET_CF:
                    handleSetCFResponse(msg);
                    break;
                /// M: for ALPS01274494 @{
                // retry if exception occurs
                case MESSAGE_HANDLE_GET_CF_RETRY:
                    handleGetCFResponse(msg);
                    break;
                case MESSAGE_GET_CF_RETRY_DELAY:
                    getCFRetryOnce();
                    break;
                /// @}
                default:
                    break;
            }
        }

        private void handleGetCFResponse(Message msg) {
            if (DBG) {
                Xlog.d(LOG_TAG, "handleGetCFResponse: done");
            }

            boolean foundServiceClass = false;
            AsyncResult ar = (AsyncResult) msg.obj;
            boolean isUserException = false;
            mCallForwardInfo = null;
            if (ar.exception != null) {
                if (DBG) {
                    Xlog.d(LOG_TAG, "handleGetCFResponse: ar.exception=" + ar.exception);
                }
                /// M: for ALPS01274494 @{
                // for LTE network, modem has limitation that exception may occur in the case when get CF immediately after set CF,
                // so this is workaround, when exception happened, we get CF again after delay 1s to cover this case and
                // reduce the impact to UE 
                if(msg.arg2 == MESSAGE_SET_CF && msg.what != MESSAGE_HANDLE_GET_CF_RETRY) {
                    Xlog.d(LOG_TAG, "retry once to get CF ~~");
                    sendMessageDelayed(obtainMessage(MESSAGE_GET_CF_RETRY_DELAY), GET_CF_RETRY_DELAY);
                    return;
                } else {
                /// @}
                    setEnabled(false);
                    mResult = false;
                    mTcpListener.onException(CallForwardEditPreference.this,
                            (CommandException) ar.exception);
                }
            } else {
                if (ar.userObj instanceof Throwable) {
                    if (DBG) {
                        Xlog.d(LOG_TAG, "userObj == Throwable");
                    }
                    isUserException = true;
                    mTcpListener.onError(CallForwardEditPreference.this, RESPONSE_ERROR);
                    mTcpListener.onUpdate(mTcpListener, isUserException);
                }
                CallForwardInfo cfInfoArray[] = (CallForwardInfo[]) ar.result;
                if (cfInfoArray.length == 0) {
                    if (DBG) {
                        Xlog.d(LOG_TAG, "handleGetCFResponse: cfInfoArray.length==0");
                    }
                    setEnabled(false);
                    mResult = false;
                    mTcpListener.onError(CallForwardEditPreference.this, RESPONSE_ERROR);
                } else {
                    mResult = true;
                    /// M:Enable the preference if the query is ok
                    setEnabled(true);

                    for (int i = 0, length = cfInfoArray.length; i < length; i++) {
                        if (DBG) {
                            Xlog.d(LOG_TAG, "handleGetCFResponse, cfInfoArray[" + i + "]="
                                + cfInfoArray[i]);
                        }
                        if ((mServiceClass & cfInfoArray[i].serviceClass) != 0) {
                            
                            /// M: Found the correct serviceClass
                            foundServiceClass = true;
                            // corresponding class
                            CallForwardInfo info = cfInfoArray[i];
                            handleCallForwardResult(info);

                            // Show an alert if we got a success response but
                            // with unexpected values.
                            // Currently only handle the fail-to-disable case
                            // since we haven't observed fail-to-enable.
                            if (msg.arg2 == MESSAGE_SET_CF &&
                                    msg.arg1 == CommandsInterface.CF_ACTION_DISABLE &&
                                    info.status == 1) {
                                CharSequence s;
                                switch (reason) {
                                    case CommandsInterface.CF_REASON_BUSY:
                                        s = getContext().getText(R.string.disable_cfb_forbidden);
                                        break;
                                    case CommandsInterface.CF_REASON_NO_REPLY:
                                        s = getContext().getText(R.string.disable_cfnry_forbidden);
                                        break;
                                    default: // not reachable
                                        s = getContext().getText(R.string.disable_cfnrc_forbidden);
                                }
                                AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
                                builder.setNeutralButton(R.string.close_dialog, null);
                                builder.setTitle(getContext().getText(R.string.error_updating_title));
                                builder.setMessage(s);
                                builder.setCancelable(true);
                                builder.create().show();
                            }
                        }
                    }
                }
            }

            if ((!foundServiceClass) && isToggled()) {
               setToggled(false);

            }
            /// M: Make sure the query to serialize
            Xlog.d(LOG_TAG, "tcpListener2 =" + mTcpListener);
            if (msg.arg2 == MESSAGE_SET_CF) {
                mTcpListener.onFinished(CallForwardEditPreference.this, false);
            } else {
                mTcpListener.onFinished(CallForwardEditPreference.this, true);
            }

            if ((msg.arg2 == MESSAGE_SET_CF) && (reason == 0) 
                    && (!isToggled())) {
                if (((mTcpListener instanceof GsmUmtsCallForwardOptions) 
                        && (mLastCallForwardInfo != null) && (mLastCallForwardInfo.status == 1)
                        && (mCallForwardInfo != null) && (mCallForwardInfo.status == 0))) {
                    ((GsmUmtsCallForwardOptions) mTcpListener).refreshSettings(true);
                }
            }

            // Now whether or not we got a new number, reset our enabled
            // summary text since it may have been replaced by an empty
            // placeholder.
            updateSummaryText();
        }

        private void handleSetCFResponse(Message msg) {
            AsyncResult ar = (AsyncResult) msg.obj;

            if (ar.exception != null) {
                if (DBG) {
                    Xlog.d(LOG_TAG, "handleSetCFResponse: ar.exception=" + ar.exception);
                }
                // setEnabled(false);
            }
            if (DBG) {
                Xlog.d(LOG_TAG, "handleSetCFResponse: re get");
            }
            Xlog.d(LOG_TAG, "mSimdId: " + mSimId);            
            /// M: Modified for vt call. @{
            // Google code:
            /*
            phone.getCallForwardingOption(reason,
                    obtainMessage(MESSAGE_GET_CF, msg.arg1, MESSAGE_SET_CF, ar.exception));
            */
            if (mServiceClass == CommandsInterface.SERVICE_CLASS_VIDEO) {
                PhoneWrapper.getVtCallForwardingOption(mPhone, reason,
                        obtainMessage(MESSAGE_GET_CF, msg.arg1, MESSAGE_SET_CF, ar.exception),
                        mSimId);
            } else {
                PhoneWrapper.getCallForwardingOption(mPhone, reason,
                        obtainMessage(MESSAGE_GET_CF, msg.arg1, MESSAGE_SET_CF, ar.exception),
                        mSimId);
            }
            /// @}
        }

        /// M: for ALPS01274494 @{
        // add retry mechanism when exception occurs
        private static final int MESSAGE_GET_CF_RETRY_DELAY = 2;
        private static final int MESSAGE_HANDLE_GET_CF_RETRY = 3;
        private static final int GET_CF_RETRY_DELAY = 1000;
        private void getCFRetryOnce() {
            Xlog.d(LOG_TAG, "getCFRetryOnce~~");
            if (mServiceClass == CommandsInterface.SERVICE_CLASS_VIDEO) {
                PhoneWrapper.getVtCallForwardingOption(mPhone, reason, mHandler
                        .obtainMessage(MyHandler.MESSAGE_HANDLE_GET_CF_RETRY, reason,
                                MyHandler.MESSAGE_SET_CF, null), mSimId);
            } else {
                PhoneWrapper.getCallForwardingOption(mPhone, reason, mHandler
                        .obtainMessage(MyHandler.MESSAGE_HANDLE_GET_CF_RETRY, reason,
                                MyHandler.MESSAGE_SET_CF, null), mSimId);
            }
        }
        /// @}
    }

    public void setStatus(boolean status) {
        mCancel = status;
    }
    
    public boolean isSuccess() {
       return mResult;
    }
    
    public void setServiceClass(int serviceClass) {
        mServiceClass = serviceClass;
    }
}
