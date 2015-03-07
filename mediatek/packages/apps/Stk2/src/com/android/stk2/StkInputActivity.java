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
 * Copyright (C) 2007 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.stk2;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.drawable.BitmapDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.TextView.BufferType;

import com.android.internal.telephony.IccCard;
import com.android.internal.telephony.TelephonyIntents;
import com.android.internal.telephony.cat.CatLog;
import com.android.internal.telephony.cat.FontSize;
import com.android.internal.telephony.cat.Input;

/**
 * Display a request for a text input a long with a text edit form.
 */
public class StkInputActivity extends Activity implements View.OnClickListener,
        TextWatcher {

    // Members
    private int mState;
    private Context mContext;
    private EditText mTextIn = null;
    private TextView mPromptView = null;
    private View mYesNoLayout = null;
    private View mNormalLayout = null;
    private Input mStkInput = null;

    // private CharSequence tempString = null;

    // Constants
    private static final int STATE_TEXT = 1;
    private static final int STATE_YES_NO = 2;

    static final String YES_STR_RESPONSE = "YES";
    static final String NO_STR_RESPONSE = "NO";

    // Font size factor values.
    static final float NORMAL_FONT_FACTOR = 1;
    static final float LARGE_FONT_FACTOR = 2;
    static final float SMALL_FONT_FACTOR = (1 / 2);

    // message id for time out
    private static final int MSG_ID_TIMEOUT = 1;
    private static final int MSG_ID_FINISH = 2;

    private static final int DELAY_TIME = 300;
    StkAppService appService = StkAppService.getInstance();

    private static final String LOGTAG = "Stk2-IA ";
    private boolean mbSendResp = false;

    private final IntentFilter mSIMStateChangeFilter = new IntentFilter(
            TelephonyIntents.ACTION_SIM_STATE_CHANGED);

    private final BroadcastReceiver mSIMStateChangeReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (TelephonyIntents.ACTION_SIM_STATE_CHANGED.equals(intent.getAction())) {

                String simState = intent.getStringExtra(IccCard.INTENT_KEY_ICC_STATE);
                int simId = intent.getIntExtra(
                        com.android.internal.telephony.Phone.GEMINI_SIM_ID_KEY, -1);

                CatLog.d(LOGTAG, "mSIMStateChangeReceiver() - simId[" + simId + "]  state["
                        + simState + "]");

                if ((simId == com.android.internal.telephony.Phone.GEMINI_SIM_2) &&
                        (IccCard.INTENT_VALUE_ICC_NOT_READY.equals(simState))) {
                    StkInputActivity.this.cancelTimeOut();
                    sendResponse(StkAppService.RES_ID_INPUT, NO_STR_RESPONSE, false);
                    StkInputActivity.this.finish();
                }
            }
        }
    };

    Handler mTimeoutHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_ID_TIMEOUT:
                                // mAcceptUsersInput = false;
                    sendResponse(StkAppService.RES_ID_TIMEOUT);
                    finish();
                    break;
                case MSG_ID_FINISH:
                                finish();
                    break;
            }
        }
    };

    // Click listener to handle buttons press..
    public void onClick(View v) {
        String input = null;

        switch (v.getId()) {
            case R.id.button_ok:
                // Check that text entered is valid .
                if (!verfiyTypedText()) {
                    return;
                }
                input = mTextIn.getText().toString();
                break;
            // Yes/No layout buttons.
            case R.id.button_yes:
                input = YES_STR_RESPONSE;
                break;
            case R.id.button_no:
                input = NO_STR_RESPONSE;
                break;
        }

        sendResponse(StkAppService.RES_ID_INPUT, input, false);
        finish();
    }

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        CatLog.d(LOGTAG, "onCreate - mbSendResp[" + mbSendResp + "]");

        // Set the layout for this activity.
        requestWindowFeature(Window.FEATURE_LEFT_ICON);
        setContentView(R.layout.stk_input);

        // Initialize members
        mTextIn = (EditText) this.findViewById(R.id.in_text);
        mPromptView = (TextView) this.findViewById(R.id.prompt);

        // Set buttons listeners.
        Button okButton = (Button) findViewById(R.id.button_ok);
        Button yesButton = (Button) findViewById(R.id.button_yes);
        Button noButton = (Button) findViewById(R.id.button_no);

        okButton.setOnClickListener(this);
        yesButton.setOnClickListener(this);
        noButton.setOnClickListener(this);

        mYesNoLayout = findViewById(R.id.yes_no_layout);
        mNormalLayout = findViewById(R.id.normal_layout);

        // Get the calling intent type: text/key, and setup the
        // display parameters.
        Intent intent = getIntent();
        if (intent != null) {
            mStkInput = intent.getParcelableExtra("INPUT");
            if (mStkInput == null) {
                finish();
            } else {
                mState = mStkInput.yesNo ? STATE_YES_NO : STATE_TEXT;
                configInputDisplay();
            }
        } else {
            finish();
        }
        mContext = getBaseContext();

        registerReceiver(mSIMStateChangeReceiver, mSIMStateChangeFilter);
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);

        mTextIn.addTextChangedListener(this);
    }

    @Override
    public void onResume() {
        super.onResume();
        CatLog.d(LOGTAG, "onResume - mbSendResp[" + mbSendResp + "]");
        appService.indicateInputVisibility(true);
        startTimeOut();
    }

    @Override
    public void onPause() {
        super.onPause();
        CatLog.d(LOGTAG, "onPause - mbSendResp[" + mbSendResp + "]");
        appService.indicateInputVisibility(false);
        cancelTimeOut();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        CatLog.d(LOGTAG, "onDestroy - before Send End Session mbSendResp[" + mbSendResp + "]");
        if (!mbSendResp) {
            CatLog.d(LOGTAG, "onDestroy - Send End Session");
            sendResponse(StkAppService.RES_ID_END_SESSION);
        }

        unregisterReceiver(mSIMStateChangeReceiver);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_BACK:
                CatLog.d(LOGTAG, "onKeyDown - KEYCODE_BACK");
                sendResponse(StkAppService.RES_ID_BACKWARD, null, false);
                finish();
                break;
        }
        return super.onKeyDown(keyCode, event);
    }

    private void sendResponse(int resId) {
        sendResponse(resId, null, false);
    }

    private void sendResponse(int resId, String input, boolean help) {
        if (StkAppService.getInstance().haveEndSession()) {
            // ignore current command
            CatLog.d(LOGTAG, "Ignore response, id is " + resId);
            return;
        }

        CatLog.d(LOGTAG, "sendResponse resID[" + resId + "] input[" + input + "] help[" + help
                + "]");
        mbSendResp = true;
        Bundle args = new Bundle();
        args.putInt(StkAppService.OPCODE, StkAppService.OP_RESPONSE);
        args.putInt(StkAppService.RES_ID, resId);
        if (input != null) {
            args.putString(StkAppService.INPUT, input);
        }
        args.putBoolean(StkAppService.HELP, help);
        mContext.startService(new Intent(mContext, StkAppService.class)
                .putExtras(args));
    }

    @Override
    public boolean onCreateOptionsMenu(android.view.Menu menu) {
        super.onCreateOptionsMenu(menu);
        menu.add(android.view.Menu.NONE, StkApp.MENU_ID_END_SESSION, 1,
                R.string.menu_end_session);
        menu.add(0, StkApp.MENU_ID_HELP, 2, R.string.help);

        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(android.view.Menu menu) {
        super.onPrepareOptionsMenu(menu);
        menu.findItem(StkApp.MENU_ID_END_SESSION).setVisible(true);
        menu.findItem(StkApp.MENU_ID_HELP).setVisible(mStkInput.helpAvailable);

        return true;
    }

    private void delayFinish() {
        mTimeoutHandler.sendMessageDelayed(mTimeoutHandler
                .obtainMessage(MSG_ID_FINISH), DELAY_TIME);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case StkApp.MENU_ID_END_SESSION:
                sendResponse(StkAppService.RES_ID_END_SESSION);
                finish();
                return true;
            case StkApp.MENU_ID_HELP:
                sendResponse(StkAppService.RES_ID_INPUT, "", true);
                delayFinish();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    public void beforeTextChanged(CharSequence s, int start, int count,
            int after) {
        // tempString = s;

    }

    public void onTextChanged(CharSequence s, int start, int before, int count) {
        // Reset timeout.
        startTimeOut();
    }

    public void afterTextChanged(Editable s) {

        int iStart = mTextIn.getSelectionStart();
        int iEnd = mTextIn.getSelectionEnd();
        if (mStkInput.ucs2 == true) {
            if (mStkInput.maxLen > 239 / 2)
                mStkInput.maxLen = 239 / 2;
        }
        if (s.length() > mStkInput.maxLen) {
            s.delete(mStkInput.maxLen, s.length());
            mTextIn.setText(s);
            int temp = 0;
            if (iStart > 0) {
                temp = iStart > (mStkInput.maxLen) ? mStkInput.maxLen : (iStart - 1);
            }
            mTextIn.setSelection(temp);
        }
    }

    private boolean verfiyTypedText() {
        // If not enough input was typed in stay on the edit screen.
        if (mTextIn.getText().length() < mStkInput.minLen) {
            return false;
        }

        return true;
    }

    private void cancelTimeOut() {
        mTimeoutHandler.removeMessages(MSG_ID_TIMEOUT);
        mTimeoutHandler.removeMessages(MSG_ID_FINISH);
    }

    private void startTimeOut() {
        cancelTimeOut();
        mTimeoutHandler.sendMessageDelayed(mTimeoutHandler
                .obtainMessage(MSG_ID_TIMEOUT), StkApp.UI_TIMEOUT);
    }

    private void configInputDisplay() {
        TextView numOfCharsView = (TextView) findViewById(R.id.num_of_chars);
        TextView inTypeView = (TextView) findViewById(R.id.input_type);

        int inTypeId = R.string.alphabet;
        String promptText = mStkInput.text;
        // set the prompt.
        if (mStkInput.iconSelfExplanatory == true) {
            promptText = "";
        }
        mPromptView.setText(promptText);

        if (mStkInput.icon != null) {
            setFeatureDrawable(Window.FEATURE_LEFT_ICON, new BitmapDrawable(
                    mStkInput.icon));
        }

        // Handle specific global and text attributes.
        switch (mState) {
            case STATE_TEXT:
                int maxLen = mStkInput.maxLen;
                int minLen = mStkInput.minLen;

                // Set number of chars info.
                String lengthLimit = String.valueOf(minLen);
                if (maxLen != minLen) {
                    lengthLimit = minLen + " - " + maxLen;
                }
                numOfCharsView.setText(lengthLimit);

                if (!mStkInput.echo) {
                    mTextIn.setInputType(InputType.TYPE_CLASS_TEXT
                            | InputType.TYPE_TEXT_VARIATION_PASSWORD);
                }
                // Set default text if present.
                if (mStkInput.defaultText != null) {
                    mTextIn.setText(mStkInput.defaultText);
                } else {
                    // make sure the text is cleared
                    mTextIn.setText("", BufferType.EDITABLE);
                }

                break;
            case STATE_YES_NO:
                // Set display mode - normal / yes-no layout
                mYesNoLayout.setVisibility(View.VISIBLE);
                mNormalLayout.setVisibility(View.GONE);
                break;
        }

        // Set input type (alphabet/digit) info close to the InText form.
        if (mStkInput.digitOnly) {
            mTextIn.setKeyListener(StkDigitsKeyListener.getInstance());
            inTypeId = R.string.digits;
        }
        inTypeView.setText(inTypeId);
    }

    private float getFontSizeFactor(FontSize size) {
        final float[] fontSizes =
                {
                NORMAL_FONT_FACTOR, LARGE_FONT_FACTOR, SMALL_FONT_FACTOR
        };

        return fontSizes[size.ordinal()];
    }
}
