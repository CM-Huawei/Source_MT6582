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
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.TextView.OnEditorActionListener;
import com.android.internal.widget.LockPatternUtils;
import com.mediatek.common.featureoption.FeatureOption;

/**
 * Displays a PIN pad for unlocking.
 */
public class KeyguardAntiTheftLockView extends KeyguardAbsKeyInputView
        implements KeyguardSecurityView, OnEditorActionListener, TextWatcher {

	private static final String TAG = "KeyguardAntiTheftLockView" ;	
    private View mBouncerFrameView ;
    private View mKey0, mKey1, mKey2, mKey3, mKey4, mKey5, mKey6, mKey7, mKey8, mKey9, ok, pinDelete ;
    private TextView mAntiTheftPinEntry ;
    private SecurityMessageDisplay mSecurityMessageDisplay;
    private KeyguardWidgetPager mAppWidgetContainer;

    public KeyguardAntiTheftLockView(Context context) {
        this(context, null);
    }

    public KeyguardAntiTheftLockView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    protected void resetState() {        
		if(AntiTheftManager.isKeypadNeeded()) {
        	mPasswordEntry.setEnabled(true);
		}
    }

    @Override
    protected int getPasswordTextViewId() {
        return R.id.antiTheftPinEntry;
    }

    @Override
    protected void verifyPasswordAndUnlock() {
        String entry = mPasswordEntry.getText().toString();

        Log.d(TAG, "verifyPasswordAndUnlock is called.") ;
        
        if(AntiTheftManager.getInstance(null,null,null).checkPassword(entry)) {
            mCallback.reportSuccessfulUnlockAttempt();
            mCallback.dismiss(true);

            /// M: ALPS01370779 Because other security views except AntiTheft mode can show more info on status bar, 
            /// we need to call KeyguardViewMediator.adjustStatusBarLocked() to reset/adjust status bar info to reshow more info of other security modes.
            AntiTheftManager.getInstance(null, null, null).adjustStatusBarLocked() ;
       } else if (entry.length() > MINIMUM_PASSWORD_LENGTH_BEFORE_REPORT ) {
            Log.d(TAG, "verifyPasswordAndUnlock fail") ;
       
            // to avoid accidental lockout, only count attempts that are long enough to be a
            // real password. This may require some tweaking.
            mCallback.reportFailedUnlockAttempt();
            if (0 == (mCallback.getFailedAttempts()
                   % LockPatternUtils.FAILED_ATTEMPTS_BEFORE_TIMEOUT)) {
               long deadline = mLockPatternUtils.setLockoutAttemptDeadline();
               handleAttemptLockout(deadline);
           }
           mSecurityMessageDisplay.setMessage(getWrongPasswordStringId(), true);
       }
       mPasswordEntry.setText("");
    }
    

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

		Log.d(TAG, "onFinishInflate() is called") ;

		mBouncerFrameView = findViewById(R.id.keyguard_bouncer_frame);
		mKey0 = findViewById(R.id.key0);
		mKey1 = findViewById(R.id.key1);
		mKey2 = findViewById(R.id.key2);
		mKey3 = findViewById(R.id.key3);
		mKey4 = findViewById(R.id.key4);
		mKey5 = findViewById(R.id.key5);
		mKey6 = findViewById(R.id.key6);
		mKey7 = findViewById(R.id.key7);
		mKey8 = findViewById(R.id.key8);
		mKey9 = findViewById(R.id.key9);
		ok = findViewById(R.id.key_enter);
		pinDelete = findViewById(R.id.delete_button);
		mAntiTheftPinEntry = (TextView)findViewById(R.id.antiTheftPinEntry) ;
        mSecurityMessageDisplay = new KeyguardMessageArea.Helper(this);

		if(AntiTheftManager.isKeypadNeeded()) {
			Log.d(TAG, "onFinishInflate, need keypad") ;
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
		else {
			Log.d(TAG, "onFinishInflate, not need keypad") ;
			mBouncerFrameView.setVisibility(View.GONE);
		}

        // Suppose an AntiTheftManager object was already created now.
        // Some kinds of anti-theft locks need to use the functions of related services.
        // We should bind these services as early as possible.
        AntiTheftManager.getInstance(null,null,null).doBindAntiThftLockServices() ;
    }

    @Override
    public void showUsabilityHint() {
    }

    @Override
    public int getWrongPasswordStringId() {
        return R.string.kg_wrong_pin;
    }

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow() ;
        
        // Suppose an AntiTheftManager object was already created now.
        // Some kinds of anti-theft locks need to use the functions of related services.
        // We should bind these services as early as possible.
        AntiTheftManager.getInstance(null,null,null).doBindAntiThftLockServices() ;
    }

    @Override
    public void onPause() {
       Log.d(TAG, "onPause") ;

       AntiTheftManager.setKeyguardCurrentModeIsAntiTheftMode(false) ;
       //mSecurityMessageDisplay.setMessage("", true);
       mCallback.updateWidgetContainerInteractive(false) ;      
    }

    @Override
    public void onResume(int reason) {
        super.onResume(reason);
        final boolean mediaPlaying = AudioSystem.isStreamActive(AudioSystem.STREAM_MUSIC, 0) 
                || AudioSystem.isStreamActive(AudioSystem.STREAM_FM, 0);
        
        Log.d(TAG, "onResume") ;

        AntiTheftManager.setKeyguardCurrentModeIsAntiTheftMode(true) ;
        mSecurityMessageDisplay.setMessage(R.string.kg_pin_instructions, true);
        Log.d(TAG, "onResume callback updateWidgetContainerInteractive") ;
        mCallback.updateWidgetContainerInteractive(true) ;

        // Suppose an AntiTheftManager object was already created now.
        // Some kinds of anti-theft locks need to use the functions of related services.
        // We should bind these services as early as possible.
        AntiTheftManager.getInstance(null,null,null).doBindAntiThftLockServices() ;
    }  
}
