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
import android.media.AudioSystem;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.text.method.DigitsKeyListener;
import android.util.AttributeSet;
import android.view.View;
import android.widget.TextView.OnEditorActionListener;

/**
 * Displays a PIN pad for unlocking.
 */
public class KeyguardPINView extends KeyguardAbsKeyInputView
        implements KeyguardSecurityView, OnEditorActionListener, TextWatcher {

    public KeyguardPINView(Context context) {
        this(context, null);
    }

    public KeyguardPINView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    protected void resetState() {
        if (KeyguardUpdateMonitor.getInstance(mContext).getMaxBiometricUnlockAttemptsReached()) {
            ///M: use different prompt message in face unlock or voice unlock
            if (mLockPatternUtils.usingBiometricWeak()) {
                mSecurityMessageDisplay.setMessage(R.string.faceunlock_multiple_failures, true);
            } else if (mLockPatternUtils.usingVoiceWeak()) {
                mSecurityMessageDisplay.setMessage(R.string.voiceunlock_multiple_failures, true);
            }
        } else {
            /// M: [ALPS00581890] Indicate the user to input pin.
            mSecurityMessageDisplay.setMessage(R.string.kg_pin_instructions, true);
        }
        mPasswordEntry.setEnabled(true);
    }

    @Override
    protected int getPasswordTextViewId() {
        return R.id.pinEntry;
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
                    if (mPasswordEntry.isEnabled()) {
                        verifyPasswordAndUnlock();
                    }
                }
            });
            ok.setOnHoverListener(new LiftToActivateListener(getContext()));
        }

        // The delete button is of the PIN keyboard itself in some (e.g. tablet) layouts,
        // not a separate view
        View pinDelete = findViewById(R.id.delete_button);
        if (pinDelete != null) {
            pinDelete.setVisibility(View.VISIBLE);
            pinDelete.setOnClickListener(new OnClickListener() {
                public void onClick(View v) {
                    // check for time-based lockouts
                    if (mPasswordEntry.isEnabled()) {
                        CharSequence str = mPasswordEntry.getText();
                        if (str.length() > 0) {
                            mPasswordEntry.setText(str.subSequence(0, str.length()-1));
                        }
                    }
                    doHapticKeyClick();
                }
            });
            pinDelete.setOnLongClickListener(new View.OnLongClickListener() {
                public boolean onLongClick(View v) {
                    // check for time-based lockouts
                    if (mPasswordEntry.isEnabled()) {
                        mPasswordEntry.setText("");
                    }
                    doHapticKeyClick();
                    return true;
                }
            });
        }

        mPasswordEntry.setKeyListener(DigitsKeyListener.getInstance());
        mPasswordEntry.setInputType(InputType.TYPE_CLASS_NUMBER
                | InputType.TYPE_NUMBER_VARIATION_PASSWORD);

        mPasswordEntry.requestFocus();
    }

    @Override
    public void showUsabilityHint() {
    }

    @Override
    public int getWrongPasswordStringId() {
        return R.string.kg_wrong_pin;
    }

    /**
     * M: add for voice unlock
     *    display prompt message when voice unlock is disabled because of
     *    media is playing in background.
    **/
    @Override
    public void onResume(int reason) {
        super.onResume(reason);
        final boolean mediaPlaying = AudioSystem.isStreamActive(AudioSystem.STREAM_MUSIC, 0) 
                || AudioSystem.isStreamActive(AudioSystem.STREAM_FM, 0);
        if (mLockPatternUtils.usingVoiceWeak() && mediaPlaying) {
            mSecurityMessageDisplay.setMessage(R.string.voice_unlock_media_playing, true);
        }
    }
}
