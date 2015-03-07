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

/*import com.android.keyguard.MediatekCarrierText;
*/
import android.provider.Settings;
import com.android.internal.R;

public class KeyguardSelectorViewTest extends ActivityInstrumentationTestCase2<KeyguardTestActivity2> {
    private static final String TAG = "KeyguardSelectorViewTest";

    private KeyguardTestActivity2 mActivity;
    private Solo mSolo;
    private int mTriggered = -1;
    private int mTriggerResId;    

    private Object mCurrentKeyguardSecurityView;
    private View mMediatekGlowPadView;
    private View mUnReadEventView;

    public KeyguardSelectorViewTest() {
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

		mActivity.isAntiTheftAutoTest = false ;
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


    public void test01GetContext() {
        View view = new View(mActivity);
        assertSame(mActivity, view.getContext());
    }



    public void test02FindMediatekGetGlowPadView()  {
        Log.d(TAG, "test02FindMediatekGetGlowPadView");
        initKeyguardHostView();

        mMediatekGlowPadView  = (View)KeyguardTestUtils.getProperty(mCurrentKeyguardSecurityView, "mGlowPadView");
        Log.v(TAG, "getProperty mGlowPadView="+mMediatekGlowPadView);
        assertNotNull(mMediatekGlowPadView);
        //assertTrue(mMediatekGlowPadView instanceof MediatekGlowPadView);
        getInstrumentation().waitForIdleSync();
    }

    public void test03OnTrigger() {
        Log.d(TAG, "test03OnTrigger");
        initKeyguardHostView();

        mMediatekGlowPadView  = (View) KeyguardTestUtils.getProperty(mCurrentKeyguardSecurityView, "mGlowPadView");
        Log.v(TAG, "getProperty mGlowPadView="+mMediatekGlowPadView);
           MediatekGlowPadView.OnTriggerListener triggerListener = new MediatekGlowPadView.OnTriggerListener() {
            @Override
            public void onTrigger(View v, int target) {
                mTriggered ++;
                Log.d(TAG, "test03OnTrigger onTrigger");
            }

            @Override
            public void onReleased(View v, int handle) {
            }

            @Override
            public void onGrabbedStateChange(View v, int handle) {
            }
            @Override
            public void onGrabbed(View v, int handle) {
            }
            @Override
            public void onFinishFinalAnimation() {
            }
        };
        
//        mMediatekGlowPadView.setOnTriggerListener(
        KeyguardTestUtils.invokeMethod(mMediatekGlowPadView, "setOnTriggerListener", 
            new Class[] {MediatekGlowPadView.OnTriggerListener.class}, new Object[] {triggerListener});
        Log.v(TAG, "setOnTriggerListener done");

        int[] rect = new int[2];
        mMediatekGlowPadView.getLocationOnScreen(rect);
        Float waveCenterX = (Float)KeyguardTestUtils.getProperty(mMediatekGlowPadView, "mWaveCenterX");
        Float waveCenterY = (Float)KeyguardTestUtils.getProperty(mMediatekGlowPadView, "mWaveCenterY");
        mSolo.drag(rect[0] + waveCenterX, rect[0] + mMediatekGlowPadView.getRight(),
                rect[1] + waveCenterY, rect[1] + waveCenterY, 20);
        getInstrumentation().waitForIdleSync();
        mTriggered = (Integer)KeyguardTestUtils.getProperty(mMediatekGlowPadView, "mActiveTarget");
        Log.d(TAG, "test03OnTrigger mTriggered="+ mTriggered);
        assertTrue(mTriggered != -1);
    }

    public void test04OnGrabbed()  {
        Log.d(TAG, "test04OnGrabbed");
        initKeyguardHostView();

                mTriggered = 0;
                mMediatekGlowPadView  = (View) KeyguardTestUtils.getProperty(mCurrentKeyguardSecurityView, "mGlowPadView");
                Log.v(TAG, "getProperty mGlowPadView="+mMediatekGlowPadView);
                   MediatekGlowPadView.OnTriggerListener triggerListener = new MediatekGlowPadView.OnTriggerListener() {
                    @Override
                    public void onTrigger(View v, int target) {
                    }
        
                    @Override
                    public void onReleased(View v, int handle) {
                    }
        
                    @Override
                    public void onGrabbedStateChange(View v, int handle) {
                    }
                    @Override
                    public void onGrabbed(View v, int handle) {
                        mTriggered ++;
                        Log.d(TAG, "test04OnGrabbed onGrabbed");
                    }
                    @Override
                    public void onFinishFinalAnimation() {
                    }
                };
                
        //        mMediatekGlowPadView.setOnTriggerListener(
                KeyguardTestUtils.invokeMethod(mMediatekGlowPadView, "setOnTriggerListener", 
                    new Class[] {MediatekGlowPadView.OnTriggerListener.class}, new Object[] {triggerListener});
                Log.v(TAG, "setOnTriggerListener done");

        int[] rect = new int[2];
        mMediatekGlowPadView.getLocationOnScreen(rect);
        Float waveCenterX = (Float)KeyguardTestUtils.getProperty(mMediatekGlowPadView, "mWaveCenterX");
        Float waveCenterY = (Float)KeyguardTestUtils.getProperty(mMediatekGlowPadView, "mWaveCenterY");
        mSolo.clickOnScreen(rect[0] + waveCenterX, rect[1] + waveCenterY);
        getInstrumentation().waitForIdleSync();
        Log.d(TAG, "test04OnGrabbed mTriggered="+ mTriggered);
        assertTrue( mTriggered != -1);
    }

   public void test05OnGrabbedStateChange()  {
       Log.d(TAG, "test05OnGrabbedStateChange");
       initKeyguardHostView();
   
           mTriggered = 0;
           mMediatekGlowPadView  = (View) KeyguardTestUtils.getProperty(mCurrentKeyguardSecurityView, "mGlowPadView");
           Log.v(TAG, "getProperty mGlowPadView="+mMediatekGlowPadView);
              MediatekGlowPadView.OnTriggerListener triggerListener = new MediatekGlowPadView.OnTriggerListener() {
               @Override
               public void onTrigger(View v, int target) {
               }
   
               @Override
               public void onReleased(View v, int handle) {
               }
   
               @Override
               public void onGrabbedStateChange(View v, int handle) {
                   mTriggered ++;
                   Log.d(TAG, "test05OnGrabbedStateChange  onGrabbedStateChange");
               }
               @Override
               public void onGrabbed(View v, int handle) {
               }
               @Override
               public void onFinishFinalAnimation() {
               }
           };
           
   //        mMediatekGlowPadView.setOnTriggerListener(
           KeyguardTestUtils.invokeMethod(mMediatekGlowPadView, "setOnTriggerListener", 
               new Class[] {MediatekGlowPadView.OnTriggerListener.class}, new Object[] {triggerListener});
           Log.v(TAG, "setOnTriggerListener done");

       int[] rect = new int[2];
       mMediatekGlowPadView.getLocationOnScreen(rect);
       Float waveCenterX = (Float)KeyguardTestUtils.getProperty(mMediatekGlowPadView, "mWaveCenterX");
       Float waveCenterY = (Float)KeyguardTestUtils.getProperty(mMediatekGlowPadView, "mWaveCenterY");
       mSolo.clickOnScreen(rect[0] + waveCenterX, rect[1] + waveCenterY);
       getInstrumentation().waitForIdleSync();
       Log.d(TAG, "test05OnGrabbedStateChange mTriggered="+ mTriggered);
       assertTrue( mTriggered != -1);
   }
   
   public void test06OnReleased()  {
       Log.d(TAG, "test06OnReleased");
       initKeyguardHostView();
        mTriggered = 0;
           mMediatekGlowPadView  = (View) KeyguardTestUtils.getProperty(mCurrentKeyguardSecurityView, "mGlowPadView");
           Log.v(TAG, "getProperty mGlowPadView="+mMediatekGlowPadView);
              MediatekGlowPadView.OnTriggerListener triggerListener = new MediatekGlowPadView.OnTriggerListener() {
               @Override
               public void onTrigger(View v, int target) {
               }
   
               @Override
               public void onReleased(View v, int handle) {
                   mTriggered ++;
                   Log.d(TAG, "test06OnReleased onReleased");
               }
   
               @Override
               public void onGrabbedStateChange(View v, int handle) {
               }
               @Override
               public void onGrabbed(View v, int handle) {
               }
               @Override
               public void onFinishFinalAnimation() {
               }
           };
           
   //        mMediatekGlowPadView.setOnTriggerListener(
           KeyguardTestUtils.invokeMethod(mMediatekGlowPadView, "setOnTriggerListener", 
               new Class[] {MediatekGlowPadView.OnTriggerListener.class}, new Object[] {triggerListener});
           Log.v(TAG, "setOnTriggerListener done");

       int[] rect = new int[2];
       mMediatekGlowPadView.getLocationOnScreen(rect);
       Float waveCenterX = (Float)KeyguardTestUtils.getProperty(mMediatekGlowPadView, "mWaveCenterX");
       Float waveCenterY = (Float)KeyguardTestUtils.getProperty(mMediatekGlowPadView, "mWaveCenterY");
       mSolo.clickOnScreen(rect[0] + waveCenterX, rect[1] + waveCenterY);
       getInstrumentation().waitForIdleSync();
       Log.d(TAG, "test06OnReleased mTriggered="+ mTriggered);
       assertTrue( mTriggered != -1);
   }
 
    public void test07testClickEmptyArea() {
        Log.d(TAG, "test07testClickEmptyArea");
        initKeyguardHostView();

        mMediatekGlowPadView  = (View) KeyguardTestUtils.getProperty(mCurrentKeyguardSecurityView, "mGlowPadView");
        Log.v(TAG, "getProperty mGlowPadView="+mMediatekGlowPadView);
        mTriggered = 0;
        mMediatekGlowPadView.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
            @Override
            public boolean onPreDraw() {
                mTriggered ++;
                return true;
            }
        });
        int[] rect = new int[2];
        mMediatekGlowPadView.getLocationOnScreen(rect);
        mSolo.clickOnScreen(rect[0]+30, rect[1]+30);
        Log.d(TAG, "test07EmptyAreaAnimation mTriggered="+ mTriggered);
        assertTrue(mTriggered > 0);
    }

   public void test08EmptyAreaAnimation() {
        Log.d(TAG, "test08EmptyAreaAnimation");
        initKeyguardHostView();

        mMediatekGlowPadView  = (View) KeyguardTestUtils.getProperty(mCurrentKeyguardSecurityView, "mGlowPadView");
        Log.v(TAG, "getProperty mGlowPadView="+mMediatekGlowPadView);
        mTriggered = 0;
        int[] rect = new int[2];
        mMediatekGlowPadView.getLocationOnScreen(rect);
        this.getInstrumentation().waitForIdleSync();

        mMediatekGlowPadView.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
            @Override
            public boolean onPreDraw() {
                mTriggered ++;
                return true;
            }
        });
        mSolo.clickOnScreen(rect[0]+30, rect[1]+30);
        Log.d(TAG, "test08EmptyAreaAnimation mTriggered="+ mTriggered);
        assertTrue(mTriggered > 0);
    }

    public void test09PingAnimation() {
        Log.d(TAG, "test09PingAnimation");
        initKeyguardHostView();

        mMediatekGlowPadView  = (View) KeyguardTestUtils.getProperty(mCurrentKeyguardSecurityView, "mGlowPadView");
        Log.v(TAG, "getProperty mGlowPadView="+mMediatekGlowPadView);
        mTriggered = 0;
        int[] rect = new int[2];
        mMediatekGlowPadView.getLocationOnScreen(rect);

        mMediatekGlowPadView.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
            @Override
            public boolean onPreDraw() {
                mTriggered ++;
                return true;
            }
        });
        this.getInstrumentation().runOnMainSync(new Runnable() {

            @Override
            public void run() {
                // TODO Auto-generated method stub
                //mMediatekGlowPadView.ping();
                KeyguardTestUtils.invokeMethod(mMediatekGlowPadView, "ping", 
                    new Class[] {}, new Object[] {});
            }

        });
        try {
            Thread.sleep(2000);
        } catch (Exception e) {
        }
        Log.d(TAG, "test09PingAnimation mTriggered="+ mTriggered);
        assertTrue(mTriggered > 0);
    }

    public void test10DragNewEventView() {
        Log.d(TAG, "test10DragNewEventView");
        initKeyguardHostView();

        mMediatekGlowPadView  = (View) KeyguardTestUtils.getProperty(mCurrentKeyguardSecurityView, "mGlowPadView");
        Log.v(TAG, "getProperty mGlowPadView="+mMediatekGlowPadView);
        mUnReadEventView  = (View) KeyguardTestUtils.getProperty(mMediatekGlowPadView, "mUnReadEventView");
        Log.v(TAG, "getProperty mUnReadEventView="+mUnReadEventView);
        mTriggered = 0;

//        final ArrayList<LockScreenNewEventView> newEventList = mUnReadEventView.getNewEventViewList();
       final ArrayList<LockScreenNewEventView> newEventList = 
            (ArrayList<LockScreenNewEventView>) KeyguardTestUtils.getProperty(mUnReadEventView, "mNewEventViews");
        assertTrue(newEventList.size() == 2);
        final View lockScreenNewEventView0 = (View) newEventList.get(0);
        final View lockScreenNewEventView1 = (View) newEventList.get(1);

        this.getInstrumentation().runOnMainSync(new Runnable() {
            @Override
            public void run() {
                for (int i = 0; i < newEventList.size(); i++) {
                    View lockScreenNewEventView = (View) newEventList.get(i);
                    KeyguardTestUtils.invokeMethod(lockScreenNewEventView, "setNumber", 
                    new Class[] {int.class}, new Object[] {10});
                }
            }
        });
        getInstrumentation().waitForIdleSync();
        try {
            Thread.sleep(1000);
        } catch (Exception e) {
        }

        int[] rect = new int[2];
        lockScreenNewEventView0.getLocationOnScreen(rect);

        long downTime = SystemClock.uptimeMillis();
        long eventTime = SystemClock.uptimeMillis();
        MotionEvent event = MotionEvent.obtain(downTime, eventTime,
                MotionEvent.ACTION_DOWN, rect[0], rect[1], 0);
        this.getInstrumentation().sendPointerSync(event);
        this.getInstrumentation().waitForIdleSync();

        eventTime = SystemClock.uptimeMillis();
        event = MotionEvent.obtain(downTime, eventTime,
                MotionEvent.ACTION_MOVE, rect[0], rect[1], 0);
        this.getInstrumentation().sendPointerSync(event);
        this.getInstrumentation().waitForIdleSync();

        eventTime = SystemClock.uptimeMillis();
        event = MotionEvent.obtain(downTime, eventTime,
                MotionEvent.ACTION_MOVE, rect[0] + 10, rect[1] + 10, 0);
        this.getInstrumentation().sendPointerSync(event);
        this.getInstrumentation().waitForIdleSync();

        // After touch and move the first NewEventView, the other two should be invisible(alpha is 0, scale is 0)
        try {
            Thread.sleep(1000);
        } catch (Exception e) {
        }
        assertTrue(lockScreenNewEventView1.getAlpha() <= 0.1f);
        assertTrue(lockScreenNewEventView1.getScaleX() <= 0.1f);
        assertTrue(lockScreenNewEventView1.getScaleY() <= 0.1f);
    }

    public void test11TriggerLaunchMms() {
        Log.d(TAG, "test11TriggerLaunchMms");
        launchTargetNewEventView(0);
    }

    public void test12TriggerLaunchCallLog() {
        Log.d(TAG, "test12TriggerLaunchCallLog");
        launchTargetNewEventView(1);
    }


    private void launchTargetNewEventView(final int index) {
        initKeyguardHostView();

        mMediatekGlowPadView  = (View) KeyguardTestUtils.getProperty(mCurrentKeyguardSecurityView, "mGlowPadView");
        Log.v(TAG, "getProperty mGlowPadView="+mMediatekGlowPadView);
        mUnReadEventView  = (View) KeyguardTestUtils.getProperty(mMediatekGlowPadView, "mUnReadEventView");
        Log.v(TAG, "getProperty mUnReadEventView="+mUnReadEventView);
        mTriggered = 0;

        final ArrayList<LockScreenNewEventView> newEventList = 
             (ArrayList<LockScreenNewEventView>) KeyguardTestUtils.getProperty(mUnReadEventView, "mNewEventViews");
         assertTrue(newEventList.size() == 2);
         View newEventView = (View) newEventList.get(index);
        
         this.getInstrumentation().runOnMainSync(new Runnable() {
             @Override
             public void run() {
                 for (int i = 0; i < newEventList.size(); i++) {
                     View lockScreenNewEventView = (View) newEventList.get(i);
                     KeyguardTestUtils.invokeMethod(lockScreenNewEventView, "setNumber", 
                     new Class[] {int.class}, new Object[] {10});
                 }
             }
         });
         getInstrumentation().waitForIdleSync();

        try {
            // find the offset between NewEventView and the MediatekPadView's center
            int[] newEventRect = new int[2];
            newEventView.getLocationOnScreen(newEventRect);
            int[] mediatekGlowPadViewRect = new int[2];
            mMediatekGlowPadView.getLocationOnScreen(mediatekGlowPadViewRect);

            int[] offsetXY = new int[2];
            Float waveCenterX = (Float)KeyguardTestUtils.getProperty(mMediatekGlowPadView, "mWaveCenterX");
            Float waveCenterY = (Float)KeyguardTestUtils.getProperty(mMediatekGlowPadView, "mWaveCenterY");
            
            offsetXY[0] = (int) (mediatekGlowPadViewRect[0] - newEventRect[0] + waveCenterX);
            offsetXY[1] = (int) (mediatekGlowPadViewRect[1] - newEventRect[1] + waveCenterY);
            mSolo.drag(newEventRect[0], newEventRect[0] + offsetXY[0], newEventRect[1], newEventRect[1] + offsetXY[1], 20);
            try {
                Thread.sleep(5000);
            } catch (Exception e) {
            }
            assertFalse(mActivity.hasWindowFocus());
        } catch (NullPointerException e) {
            fail("NewEventView should not be null");
        }
    }

    public void test13NewEventViewIntersectAnimation() {
        Log.d(TAG, "test13NewEventViewIntersectAnimation");
        initKeyguardHostView();

        mMediatekGlowPadView  = (View) KeyguardTestUtils.getProperty(mCurrentKeyguardSecurityView, "mGlowPadView");
        Log.v(TAG, "getProperty mGlowPadView="+mMediatekGlowPadView);
        mUnReadEventView  = (View) KeyguardTestUtils.getProperty(mMediatekGlowPadView, "mUnReadEventView");
        Log.v(TAG, "getProperty mUnReadEventView="+mUnReadEventView);
        
      
        final ArrayList<LockScreenNewEventView> newEventList = 
            (ArrayList<LockScreenNewEventView>) KeyguardTestUtils.getProperty(mUnReadEventView, "mNewEventViews");
        assertTrue(newEventList.size() == 2);
        View newEventView = (View) newEventList.get(0);
       
        this.getInstrumentation().runOnMainSync(new Runnable() {
            @Override
            public void run() {
                for (int i = 0; i < newEventList.size(); i++) {
                    View lockScreenNewEventView = (View) newEventList.get(i);
                    KeyguardTestUtils.invokeMethod(lockScreenNewEventView, "setNumber", 
                    new Class[] {int.class}, new Object[] {10});
                }
            }
        });
        getInstrumentation().waitForIdleSync();

        try {
            mTriggered = 0;
            mMediatekGlowPadView.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
                @Override
                public boolean onPreDraw() {
                    // The DragView will be moved to intersect with MediatekGlowPadView's handle drawable,
                    // so the instance field mDragView in MediatekGlowPadView must not be null
                    if (mMediatekGlowPadView != null) {
                        View dragView = (View)KeyguardTestUtils.getFiledObject(mMediatekGlowPadView, "mDragView");
                        Log.v(TAG, "getFiledObject dragView="+dragView);
                        if (dragView != null) {
                            mTriggered ++;
                        }
                    }
                    return true;
                }
            });
            // Click the first LockScreenNewEventView, it should do a intersect animation
            mSolo.clickOnView(newEventView);
            try {
                Thread.sleep(2000);
            } catch (Exception e) {
            }
            assertTrue(mTriggered>0);
        } catch (NullPointerException e) {
            fail("mms NewEventView should not be null");
        }
    }


    public void test14SetTouchRecepient() {
        Log.d(TAG, "test14SetTouchRecepient");
        initKeyguardHostView();

        mMediatekGlowPadView  = (View) KeyguardTestUtils.getProperty(mCurrentKeyguardSecurityView, "mGlowPadView");
        Log.v(TAG, "getProperty mGlowPadView="+mMediatekGlowPadView);

     
        mTriggered = 0;
        final View imageView = new ImageView(mActivity);
        imageView.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        imageView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                mTriggered ++;
                return true;
            }
        });
        
        this.getInstrumentation().runOnMainSync(new Runnable() {
            @Override
            public void run() {
                FrameLayout mLockScreenLayout;
                mLockScreenLayout  = (FrameLayout) KeyguardTestUtils.getProperty(mMediatekGlowPadView, "mLockScreenLayout");
                Log.v(TAG, "getProperty mLockScreenLayout="+mLockScreenLayout);

                mLockScreenLayout.addView(imageView);
                //mMediatekGlowPadView.setTouchRecepient(imageView);
                Log.v(TAG, "setTouchRecepient mMediatekGlowPadView="+mMediatekGlowPadView);
                Log.v(TAG, "setTouchRecepient imageView="+imageView);
                //KeyguardTestUtils.invokeMethod(mMediatekGlowPadView, "setTouchRecepient",
                  //  new Class[] {View.class}, new Object[] {imageView});
            }
        });
        getInstrumentation().waitForIdleSync();
        
        int[] rect = new int[2];
        mMediatekGlowPadView.getLocationOnScreen(rect);
        
        long downTime = SystemClock.uptimeMillis();
        long eventTime = SystemClock.uptimeMillis();

        Float waveCenterX = (Float)KeyguardTestUtils.getProperty(mMediatekGlowPadView, "mWaveCenterX");
        Float waveCenterY = (Float)KeyguardTestUtils.getProperty(mMediatekGlowPadView, "mWaveCenterY");
        
        float x = rect[0] + waveCenterX;
        float y = rect[1] + waveCenterY;

        MotionEvent event = MotionEvent.obtain(downTime, eventTime,
                MotionEvent.ACTION_DOWN, x, y, 0);
        getInstrumentation().sendPointerSync(event);
        
        eventTime = SystemClock.uptimeMillis();
        event = MotionEvent.obtain(downTime, eventTime,
                MotionEvent.ACTION_MOVE, x, y, 0);
        getInstrumentation().sendPointerSync(event);
        
        eventTime = SystemClock.uptimeMillis();
        event = MotionEvent.obtain(downTime, eventTime,
                MotionEvent.ACTION_UP, x, y, 0);
        getInstrumentation().sendPointerSync(event);
        
        assertTrue(mTriggered>0);
    }

    public void test15AddDragView() {
        Log.d(TAG, "test15AddDragView");
        initKeyguardHostView();
        
        mMediatekGlowPadView  = (View) KeyguardTestUtils.getProperty(mCurrentKeyguardSecurityView, "mGlowPadView");
        Log.v(TAG, "getProperty mGlowPadView="+mMediatekGlowPadView);
        
        FrameLayout mLockScreenLayout;
        mLockScreenLayout  = (FrameLayout) KeyguardTestUtils.getProperty(mMediatekGlowPadView, "mLockScreenLayout");
        Log.v(TAG, "getProperty mLockScreenLayout="+mLockScreenLayout);

        Bitmap bitmap = Bitmap.createBitmap(50, 50, Bitmap.Config.ARGB_8888);
        final DragView dragView = new DragView(mLockScreenLayout, bitmap, 10,
                10, 0, 0, bitmap.getWidth(), bitmap.getHeight(), 1.0f);
        dragView.setId(10000);
        this.getInstrumentation().runOnMainSync(new Runnable() {
            @Override
            public void run() {
                dragView.show(50, 50);
            }
        });
        getInstrumentation().waitForIdleSync();
        assertNotNull(mLockScreenLayout.findViewById(10000));
        assertTrue(dragView.getLayoutParams() instanceof LockScreenLayout.LayoutParams);
    }

    public void test16ShakeAnimation() {
        Log.d(TAG, "test16ShakeAnimation");
        initKeyguardHostView();
        
        mMediatekGlowPadView  = (View) KeyguardTestUtils.getProperty(mCurrentKeyguardSecurityView, "mGlowPadView");
        Log.v(TAG, "getProperty mGlowPadView="+mMediatekGlowPadView);

        mUnReadEventView  = (View) KeyguardTestUtils.getProperty(mMediatekGlowPadView, "mUnReadEventView");
        Log.v(TAG, "getProperty mUnReadEventView="+mUnReadEventView);
        
        final ArrayList<LockScreenNewEventView> newEventList = 
            (ArrayList<LockScreenNewEventView>) KeyguardTestUtils.getProperty(mUnReadEventView, "mNewEventViews");
        assertTrue(newEventList.size() == 2);
        View newEventView = (View) newEventList.get(0);
       
        this.getInstrumentation().runOnMainSync(new Runnable() {
            @Override
            public void run() {
                for (int i = 0; i < newEventList.size(); i++) {
                    View lockScreenNewEventView = (View) newEventList.get(i);
                    KeyguardTestUtils.invokeMethod(lockScreenNewEventView, "setNumber", 
                    new Class[] {int.class}, new Object[] {10});
                }
            }
        });
        getInstrumentation().waitForIdleSync();
        
        int[] rect = new int[2];
        mMediatekGlowPadView.getLocationOnScreen(rect);
        
        mTriggered = 0;
        mMediatekGlowPadView.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
            @Override
            public boolean onPreDraw() {
                mTriggered ++;
                return true;
            }
        });
        // Click empty area should trigger shake animation, which must lead to viewtree draw
        mSolo.clickOnScreen(rect[0]+2, rect[1]+2);
        getInstrumentation().waitForIdleSync();
        assertTrue(mTriggered >0);
        
        // By design the animation should stop after shake two times
        //mTriggered = 0;
        //getInstrumentation().waitForIdleSync();
        //assertFalse(mTriggered >0);
    }

}
