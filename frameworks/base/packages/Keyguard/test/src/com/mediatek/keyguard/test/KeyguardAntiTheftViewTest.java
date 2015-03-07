/*
 * Copyright (C) 2011 The Android Open Source Project
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
package com.mediatek.keyguard.test;

import com.jayway.android.robotium.solo.Solo;
import com.mediatek.keyguard.util.KeyguardTestUtils;
import com.mediatek.xlog.Xlog;
import com.mediatek.common.featureoption.FeatureOption;
import android.util.Log;
import android.os.SystemClock;
import java.util.ArrayList;
import android.content.Intent;
import android.graphics.Bitmap;

import android.os.Bundle;
import android.test.ActivityInstrumentationTestCase2;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.TextView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.FrameLayout;

import com.android.keyguard.MediatekGlowPadView;
import com.android.keyguard.LockScreenNewEventView;
import com.android.keyguard.UnReadEventView;
import com.android.keyguard.DragView;
import com.android.keyguard.LockScreenLayout;
//import com.android.keyguard.KeyguardSecurityModel;
import com.android.keyguard.AntiTheftManager;



/*import com.android.keyguard.MediatekCarrierText;
*/
import android.provider.Settings;
import com.android.internal.R;

public class KeyguardAntiTheftViewTest extends ActivityInstrumentationTestCase2<KeyguardTestActivity2> {
    private static final String TAG = "KeyguardAntiTheftViewTest";

    private KeyguardTestActivity2 mActivity;
    private Solo mSolo;
    private int mTriggered = -1;
    private int mTriggerResId;    

    private Object mCurrentKeyguardSecurityView;
    private View mSecurityView;
	private View mOk ;
	private TextView mAntiTheftPinEntry;
    //private View mUnReadEventView;

    public KeyguardAntiTheftViewTest() {
        super(KeyguardTestActivity2.class);
        Log.v(TAG, "constructed this = "+this);
    }

    @Override
    protected void setUp() throws Exception {
        // TODO Auto-generated method stub
        super.setUp();
        mActivity = getActivity();
        Log.v(TAG, "get activity="+ mActivity);
        mSolo = new Solo(getInstrumentation(), mActivity);
        Log.v(TAG, "get mSolo="+ mSolo);

		mActivity.isAntiTheftAutoTest = true ;
    }

    @Override
    protected void tearDown() throws Exception {
        // TODO Auto-generated method stub
        super.tearDown();

    }

    private void initKeyguardHostView() {
        Log.v(TAG, "initKeyguardHostView");	
		
        this.getInstrumentation().runOnMainSync(new Runnable() {
            @Override
            public void run() {
                mActivity.setKeyguardHostView();
            }
        });
        getInstrumentation().waitForIdleSync();

        mCurrentKeyguardSecurityView  = (Object) KeyguardTestUtils.getProperty(mActivity.mKeyguardHostView, "mCurrentKeyguardSecurityView");
        Log.v(TAG, "getProperty mCurrentKeyguardSecurityView="+mCurrentKeyguardSecurityView);
    }

    private void resetAirplaneMode() {
        for (int i=1; i >=0; i--) {
            Settings.Global.putInt(
                    mActivity.getContentResolver(),
                    Settings.Global.AIRPLANE_MODE_ON,
                    i);
            Intent intent = new Intent(Intent.ACTION_AIRPLANE_MODE_CHANGED);
            intent.addFlags(Intent.FLAG_RECEIVER_REPLACE_PENDING);
            intent.putExtra("state", (i==1) ? true : false);
            mActivity.sendBroadcast(intent);
            try {
                Thread.sleep(5000);
            } catch (Exception e) {
            }
        }
        try {
            Thread.sleep(15000);
        } catch (Exception e) {
        }
    }


   /* public void test01GetContext() {
        View view = new View(mActivity);
        assertSame(mActivity, view.getContext());
    }



    public void test02FindSecurityView()  {
        Log.d(TAG, "test02ShowPinView");		
		
        initKeyguardHostView();

		mSecurityView= (View)KeyguardTestUtils.getProperty(mCurrentKeyguardSecurityView, "mBouncerFrameView");
        Log.v(TAG, "getProperty mSecurityView= "+ mSecurityView);
        assertNotNull(mSecurityView);
		
        getInstrumentation().waitForIdleSync();
    }

	public void test03OnEnterCorrectPassword()  {
        Log.d(TAG, "test03OnEnterCorrectPassword");		
		
        initKeyguardHostView();
		
		//mSecurityView= (View)KeyguardTestUtils.getProperty(mCurrentKeyguardSecurityView, "mBouncerFrameView");
		mAntiTheftPinEntry = (TextView)KeyguardTestUtils.getProperty(mCurrentKeyguardSecurityView, "mAntiTheftPinEntry");
		assertNotNull(mAntiTheftPinEntry) ;
		
		mOk = (View)KeyguardTestUtils.getProperty(mCurrentKeyguardSecurityView, "ok");
		mOk.setOnClickListener(new View.OnClickListener() {
        	@Override
           	public void onClick(View v) {           	  	
   	        	String entry = mAntiTheftPinEntry.getText().toString();
				assertTrue(entry.equals(CORRECT_PW)) ;
            }
	    });
		
		clickOnKey(KeyPad.KEY0) ;
		clickOnKey(KeyPad.KEY1) ;
		clickOnKey(KeyPad.KEY2) ;
		clickOnKey(KeyPad.KEY3) ;
		clickOnKey(KeyPad.OK) ;
		
        getInstrumentation().waitForIdleSync();
    }

	
	public void test04OnEnterWrongPassword()  {
        Log.d(TAG, "test04OnEnterWrongPassword");		
		
        initKeyguardHostView();
		
		//mSecurityView= (View)KeyguardTestUtils.getProperty(mCurrentKeyguardSecurityView, "mBouncerFrameView");
		mAntiTheftPinEntry = (TextView)KeyguardTestUtils.getProperty(mCurrentKeyguardSecurityView, "mAntiTheftPinEntry");
		assertNotNull(mAntiTheftPinEntry) ;
		
		mOk = (View)KeyguardTestUtils.getProperty(mCurrentKeyguardSecurityView, "ok");
		mOk.setOnClickListener(new View.OnClickListener() {
        	@Override
           	public void onClick(View v) {           	  	
   	        	String entry = mAntiTheftPinEntry.getText().toString();
				assertTrue(!entry.equals(CORRECT_PW)) ;
            }
	    });
		
		clickOnKey(KeyPad.KEY5) ;
		clickOnKey(KeyPad.KEY6) ;
		clickOnKey(KeyPad.KEY7) ;
		clickOnKey(KeyPad.KEY8) ;
		clickOnKey(KeyPad.OK) ;
		
        getInstrumentation().waitForIdleSync();
    }*/

    
    public void test01OnGetPplLockIntent()  {
            Log.d(TAG, "test05OnGetPplLockIntent");       
            
            mActivity.isAntiTheftAutoTest = false ;
            initKeyguardHostView();

            try {
                Thread.sleep(2000);
            } catch (Exception e) {
            }
            
            Intent intent = new Intent(getInstrumentation().getTargetContext(),KeyguardTestActivity2.class);
            intent.setAction("com.mediatek.ppl.NOTIFY_LOCK") ;
            //startActivity(intent, null, null);            
            mActivity.sendBroadcast(intent);
            getInstrumentation().waitForIdleSync();
        }

	//mPasswordEntry
	private final static String CORRECT_PW = "0123" ;
	public static class KeyPad {
		public static String KEY1 = "mKey1" ;
		public static String KEY2 = "mKey2" ;
		public static String KEY3 = "mKey3" ;
		public static String KEY4 = "mKey4" ;
		public static String KEY5 = "mKey5" ;
		public static String KEY6 = "mKey6" ;
		public static String KEY7 = "mKey7" ;
		public static String KEY8 = "mKey8" ;
		public static String KEY9 = "mKey9" ;
		public static String KEY0 = "mKey0" ;
		public static String OK = "ok" ;
		public static String DEL = "pinDelete" ;
	}
	
	public void clickOnKey(String id) {		
		View key = (View)KeyguardTestUtils.getProperty(mCurrentKeyguardSecurityView, id);	
		int[] coord = new int[2] ;
		key.getLocationOnScreen(coord) ;
		mSolo.clickOnScreen(coord[0], coord[1]);
	}		
}
