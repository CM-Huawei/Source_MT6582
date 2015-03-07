/* Copyright Statement:
 *
 * This software/firmware and related documentation ("MediaTek Software") are
 * protected under relevant copyright laws. The information contained herein
 * is confidential and proprietary to MediaTek Inc. and/or its licensors.
 * Without the prior written permission of MediaTek inc. and/or its licensors,
 * any reproduction, modification, use or disclosure of MediaTek Software,
 * and information contained herein, in whole or in part, shall be strictly prohibited.
 *
 * MediaTek Inc. (C) 2010. All rights reserved.
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

import android.content.Context;
import android.os.AsyncResult;
import android.os.Handler;
import android.os.Message;
import android.preference.ListPreference;
import android.util.AttributeSet;
import android.util.Log;

import static com.android.phone.TimeConsumingPreferenceActivity.RESPONSE_ERROR;

import com.android.internal.telephony.CommandException;
import com.android.internal.telephony.CommandsInterface;
import com.android.internal.telephony.Phone;

import com.mediatek.phone.PhoneLog;
import com.mediatek.phone.TimeConsumingPreferenceListener;
import com.mediatek.phone.gemini.GeminiUtils;
import com.mediatek.phone.wrapper.PhoneWrapper;
import com.mediatek.settings.CallSettings;

public class CLIRListPreference extends ListPreference {
    private static final String LOG_TAG = "Settings/CLIRListPreference";
    private static final boolean DBG = true; //(PhoneApp.DBG_LEVEL >= 2);

    private final MyHandler mHandler = new MyHandler();
    private final Phone mPhone;
    private TimeConsumingPreferenceListener mTcpListener;

    int clirArray[];

    public CLIRListPreference(Context context, AttributeSet attrs) {
        super(context, attrs);

        mPhone = PhoneGlobals.getPhone();
    }

    public CLIRListPreference(Context context) {
        this(context, null);
    }

    @Override
    protected void onDialogClosed(boolean positiveResult) {
        super.onDialogClosed(positiveResult);

        /// M: Modified for gemini phone. @{
        // Google code:
        /*
        mPhone.setOutgoingCallerIdDisplay(findIndexOfValue(getValue()),
                mHandler.obtainMessage(MyHandler.MESSAGE_SET_CLIR));
        */
        PhoneWrapper.setOutgoingCallerIdDisplay(mPhone, findIndexOfValue(getValue()),
                mHandler.obtainMessage(MyHandler.MESSAGE_SET_CLIR), mSlotId);
        /// @}
        if (mTcpListener != null) {
            mTcpListener.onStarted(this, false);
        }
    }

    /* package */void init(TimeConsumingPreferenceListener listener, boolean skipReading, int slotId) {
        PhoneLog.d(LOG_TAG, "init, slotId = " + slotId);
        mTcpListener = listener;
        mSlotId = slotId;

        if (!skipReading) {
            /// M: Modified for gemini phone. @{
            // Google code:
            /*
            mPhone.getOutgoingCallerIdDisplay(mHandler.obtainMessage(MyHandler.MESSAGE_GET_CLIR,
                    MyHandler.MESSAGE_GET_CLIR, MyHandler.MESSAGE_GET_CLIR));
            */
            PhoneWrapper.getOutgoingCallerIdDisplay(mPhone, mHandler.obtainMessage(
                    MyHandler.MESSAGE_GET_CLIR, MyHandler.MESSAGE_GET_CLIR,
                    MyHandler.MESSAGE_GET_CLIR), mSlotId);
            /// @}
            if (mTcpListener != null) {
                mTcpListener.onStarted(this, true);
            }
        }
    }

    /* package */ void handleGetCLIRResult(int tmpClirArray[]) {
        clirArray = tmpClirArray;
        final boolean enabled =
                tmpClirArray[1] == 1 || tmpClirArray[1] == 3 || tmpClirArray[1] == 4;
        setEnabled(enabled);

        // set the value of the preference based upon the clirArgs.
        int value = CommandsInterface.CLIR_DEFAULT;
        switch (tmpClirArray[1]) {
            case 1: // Permanently provisioned
            case 3: // Temporary presentation disallowed
            case 4: // Temporary presentation allowed
                switch (tmpClirArray[0]) {
                    case 1: // CLIR invoked
                        value = CommandsInterface.CLIR_INVOCATION;
                        break;
                    case 2: // CLIR suppressed
                        value = CommandsInterface.CLIR_SUPPRESSION;
                        break;
                    case 0: // Network default
                    default:
                        value = CommandsInterface.CLIR_DEFAULT;
                        break;
                }
                break;
            case 0: // Not Provisioned
            case 2: // Unknown (network error, etc)
            default:
                value = CommandsInterface.CLIR_DEFAULT;
                break;
        }
        setValueIndex(value);

        // set the string summary to reflect the value
        int summary = R.string.sum_default_caller_id;
        switch (value) {
            case CommandsInterface.CLIR_SUPPRESSION:
                summary = R.string.sum_show_caller_id;
                break;
            case CommandsInterface.CLIR_INVOCATION:
                summary = R.string.sum_hide_caller_id;
                break;
            case CommandsInterface.CLIR_DEFAULT:
                summary = R.string.sum_default_caller_id;
                break;
            default:
                break;
        }
        setSummary(summary);
    }

    private class MyHandler extends Handler {
        static final int MESSAGE_GET_CLIR = 0;
        static final int MESSAGE_SET_CLIR = 1;

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MESSAGE_GET_CLIR:
                    handleGetCLIRResponse(msg);
                    break;
                case MESSAGE_SET_CLIR:
                    handleSetCLIRResponse(msg);
                    break;
            }
        }

        private void handleGetCLIRResponse(Message msg) {
            AsyncResult ar = (AsyncResult) msg.obj;

            if (msg.arg2 == MESSAGE_SET_CLIR) {
                mTcpListener.onFinished(CLIRListPreference.this, false);
            } else {
                mTcpListener.onFinished(CLIRListPreference.this, true);
            }
            clirArray = null;
            if (ar.exception != null) {
                /// M: @{
                PhoneLog.d(LOG_TAG, "handleGetCLIRResponse: ar.exception=" + ar.exception);
                setEnabled(false);
                /// @}
                mTcpListener.onException(CLIRListPreference.this, (CommandException) ar.exception);
            } else if (ar.userObj instanceof Throwable) {
                mTcpListener.onError(CLIRListPreference.this, RESPONSE_ERROR);
            } else {
                int clirArray[] = (int[]) ar.result;
                if (clirArray.length != 2) {
                    mTcpListener.onError(CLIRListPreference.this, RESPONSE_ERROR);
                } else {
                    if (DBG) {
                        Log.d(LOG_TAG, "handleGetCLIRResponse: CLIR successfully queried,"
                                + " clirArray[0]=" + clirArray[0]
                                + ", clirArray[1]=" + clirArray[1]);
                    }
                    handleGetCLIRResult(clirArray);
                }
            }
        }

        private void handleSetCLIRResponse(Message msg) {
            AsyncResult ar = (AsyncResult) msg.obj;

            if (ar.exception != null) {
                if (DBG) Log.d(LOG_TAG, "handleSetCallWaitingResponse: ar.exception="+ar.exception);
                //setEnabled(false);
            }
            if (DBG) Log.d(LOG_TAG, "handleSetCallWaitingResponse: re get");

            /// M: Modified for gemini phone. @{
            // Google code:
            /*
            mPhone.getOutgoingCallerIdDisplay(obtainMessage(MESSAGE_GET_CLIR,
                    MESSAGE_SET_CLIR, MESSAGE_SET_CLIR, ar.exception));
            */
            PhoneWrapper.getOutgoingCallerIdDisplay(mPhone,
                    obtainMessage(MESSAGE_GET_CLIR, MESSAGE_SET_CLIR, MESSAGE_SET_CLIR,
                            ar.exception), mSlotId);
            /// @}
        }
    }

    // --------------- MTK --------------------
    /// M: GEMINI
    private int mSlotId = -1;
}
