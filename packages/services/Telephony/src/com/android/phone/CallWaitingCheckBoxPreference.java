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

import android.content.Context;
import android.os.AsyncResult;
import android.os.Handler;
import android.os.Message;
import android.preference.CheckBoxPreference;
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

public class CallWaitingCheckBoxPreference extends CheckBoxPreference {
    private static final String LOG_TAG = "Settings/CallWaitingCheckBoxPreference";
    private static final boolean DBG = true;// (PhoneApp.DBG_LEVEL >= 2);

    private final MyHandler mHandler = new MyHandler();
    private final Phone mPhone;
    private TimeConsumingPreferenceListener mTcpListener;

    public CallWaitingCheckBoxPreference(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        mPhone = PhoneGlobals.getPhone();
    }

    public CallWaitingCheckBoxPreference(Context context, AttributeSet attrs) {
        this(context, attrs, com.android.internal.R.attr.checkBoxPreferenceStyle);
    }

    public CallWaitingCheckBoxPreference(Context context) {
        this(context, null);
    }

    void init(TimeConsumingPreferenceListener listener, boolean skipReading, int slotId) {
        mTcpListener = listener;
        mSlotId = slotId;
        PhoneLog.d(LOG_TAG, "init, slotId = " + slotId);

        if (!skipReading) {
            /// M: For VT. @{
            /* Original Code:
            mPhone.getCallWaiting(mHandler.obtainMessage(MyHandler.MESSAGE_GET_CALL_WAITING,
                    MyHandler.MESSAGE_GET_CALL_WAITING, MyHandler.MESSAGE_GET_CALL_WAITING));
             */
            if (mServiceClass == CommandsInterface.SERVICE_CLASS_VIDEO) {
                PhoneWrapper.getVtCallWaiting(
                        mPhone, mHandler.obtainMessage(MyHandler.MESSAGE_GET_CALL_WAITING,
                                MyHandler.MESSAGE_GET_CALL_WAITING,
                                MyHandler.MESSAGE_GET_CALL_WAITING), mSlotId);
            } else {
                PhoneWrapper.getCallWaiting(
                        mPhone, mHandler.obtainMessage(MyHandler.MESSAGE_GET_CALL_WAITING,
                                MyHandler.MESSAGE_GET_CALL_WAITING,
                                MyHandler.MESSAGE_GET_CALL_WAITING), mSlotId);
            }
            /// @}
            if (mTcpListener != null) {
                mTcpListener.onStarted(this, true);
            }
        }
    }

    @Override
    protected void onClick() {
        super.onClick();
        boolean toState = isChecked();
        /// M: For VT. @{
        /* Original Code:
         * mPhone.setCallWaiting(isChecked(),
         * mHandler.obtainMessage(MyHandler.MESSAGE_SET_CALL_WAITING));
         */
        if (mServiceClass == CommandsInterface.SERVICE_CLASS_VIDEO) {
            PhoneWrapper.setVtCallWaiting(mPhone, toState,
                    mHandler.obtainMessage(MyHandler.MESSAGE_SET_CALL_WAITING), mSlotId);
        } else {
            PhoneWrapper.setCallWaiting(mPhone, toState,
                    mHandler.obtainMessage(MyHandler.MESSAGE_SET_CALL_WAITING), mSlotId);
        }
        /// @}
        if (mTcpListener != null) {
            mTcpListener.onStarted(this, false);
        }
    }

    private class MyHandler extends Handler {
        static final int MESSAGE_GET_CALL_WAITING = 0;
        static final int MESSAGE_SET_CALL_WAITING = 1;

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MESSAGE_GET_CALL_WAITING:
                    handleGetCallWaitingResponse(msg);
                    break;
                case MESSAGE_SET_CALL_WAITING:
                    handleSetCallWaitingResponse(msg);
                    break;
            }
        }

        private void handleGetCallWaitingResponse(Message msg) {
            AsyncResult ar = (AsyncResult) msg.obj;

            if (mTcpListener != null) {
                if (msg.arg2 == MESSAGE_SET_CALL_WAITING) {
                    mTcpListener.onFinished(CallWaitingCheckBoxPreference.this, false);
                } else {
                    mTcpListener.onFinished(CallWaitingCheckBoxPreference.this, true);
                }
            }

            if (ar.exception != null) {
                if (DBG) {
                    Log.d(LOG_TAG, "handleGetCallWaitingResponse: ar.exception=" + ar.exception);
                }
                /// M: set enable
                setEnabled(false);

                if (mTcpListener != null) {
                    mTcpListener.onException(CallWaitingCheckBoxPreference.this,
                            (CommandException)ar.exception);
                }
            } else if (ar.userObj instanceof Throwable) {
                if (mTcpListener != null) {
                    mTcpListener.onError(CallWaitingCheckBoxPreference.this, RESPONSE_ERROR);
                }
            } else {
                PhoneLog.d(LOG_TAG, "handleGetCallWaitingResponse: CW state successfully queried.");
                int[] cwArray = (int[])ar.result;
                // If cwArray[0] is = 1, then cwArray[1] must follow,
                // with the TS 27.007 service class bit vector of services
                // for which call waiting is enabled.
                try {
                    setChecked(((cwArray[0] == 1) && ((cwArray[1] & 0x01) == 0x01)));
                } catch (ArrayIndexOutOfBoundsException e) {
                    Log.e(LOG_TAG, "handleGetCallWaitingResponse: improper result: err ="
                            + e.getMessage());
                }
            }
        }

        private void handleSetCallWaitingResponse(Message msg) {
            AsyncResult ar = (AsyncResult) msg.obj;

            if (ar.exception != null) {
                if (DBG) {
                    Log.d(LOG_TAG, "handleSetCallWaitingResponse: ar.exception=" + ar.exception);
                }
                //setEnabled(false);
            }
            if (DBG) Log.d(LOG_TAG, "handleSetCallWaitingResponse: re get");

            /// M: For VT call. @{
            // Google code:
            /*
             * mPhone.getCallWaiting(obtainMessage(MESSAGE_GET_CALL_WAITING,
             * MESSAGE_SET_CALL_WAITING, MESSAGE_SET_CALL_WAITING,
             * ar.exception));
             */
            if (mServiceClass == CommandsInterface.SERVICE_CLASS_VIDEO) {
                PhoneWrapper.getVtCallWaiting(
                        mPhone,
                        obtainMessage(MESSAGE_GET_CALL_WAITING, MESSAGE_SET_CALL_WAITING,
                                MESSAGE_SET_CALL_WAITING, ar.exception), mSlotId);
            } else {
                PhoneWrapper.getCallWaiting(
                        mPhone,
                        obtainMessage(MESSAGE_GET_CALL_WAITING, MESSAGE_SET_CALL_WAITING,
                                MESSAGE_SET_CALL_WAITING, ar.exception), mSlotId);
            }
            /// @}
        }
    }

    // -------------------- MTK -----------------------------
    // M: support for GEMINI
    private int mSlotId = -1;
    private int mServiceClass = CommandsInterface.SERVICE_CLASS_VOICE;

    public void setServiceClass(int serviceClass) {
        mServiceClass = serviceClass;
    }
}
