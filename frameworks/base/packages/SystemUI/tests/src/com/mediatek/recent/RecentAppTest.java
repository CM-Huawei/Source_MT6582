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
package com.mediatek.systemui.recent;

import android.app.Instrumentation;
import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Environment;
import android.os.FileObserver;
import android.os.UserHandle;
import android.test.ActivityInstrumentationTestCase2;
import android.test.suitebuilder.annotation.LargeTest;
import android.test.TouchUtils;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;

///import com.mediatek.systemui.statusbar.StatusBarViewActivity;
import com.android.systemui.recent.RecentsActivity;
import com.mediatek.systemui.TestUtils;
import com.mediatek.xlog.Xlog;

import java.io.File;

/**
 * Functional tests for the recent application feature.
 */
@LargeTest
public class RecentAppTest extends ActivityInstrumentationTestCase2<RecentsActivity> {

    private static final String TAG = "RecentAppTest";
    private static final int SCREEN_WAIT_TIME_SEC = 5;

    private Object mPhoneStatusBar;
    private Context mContext;
    private RecentsActivity mRecentsActivity;
    private Instrumentation mInstrumentation;
    private Object mRecentsPanelView;  
    private ContentResolver mConResolver;   
    

    public RecentAppTest() {
        super(RecentsActivity.class);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
		setActivityInitialTouchMode(false);
        mRecentsActivity = getActivity();
        mInstrumentation = getInstrumentation();
        mContext = mInstrumentation.getTargetContext();
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
    }

    private void toggleRecentApp() {
        Intent intent = new Intent(RecentsActivity.TOGGLE_RECENTS_INTENT);
            TestUtils.invokeMethod(mRecentsActivity, "onNewIntent",  
                new Class[] {Intent.class }, new Object[] { intent });
    }

    public void testShowAndCloseRecentApps() {
        Log.v(TAG, "++++++++++ Start testShowAndCloseRecentApps ++++++++++");
        toggleRecentApp();
        mRecentsPanelView = TestUtils.getProperty(mRecentsActivity, "mRecentsPanel");
        TestUtils.sleepBy(1000);
        Boolean showingStart = (Boolean) TestUtils.getProperty(mRecentsPanelView, "mShowing");
        toggleRecentApp();
        TestUtils.sleepBy(1000);
        Boolean showingEnd = (Boolean) TestUtils.getProperty(mRecentsPanelView, "mShowing");
        if (showingStart) {
            assertFalse(showingEnd);
        } else {
            assertTrue(showingEnd);
        }
        Log.v(TAG, "---------- end testShowAndCloseRecentApps ----------");
    }

    public void testConstants() {
        Log.v(TAG, "++++++++++ Start testConstants ++++++++++");
        Class cls = null;
        try {
            cls = mContext.getClassLoader().loadClass("com.android.systemui.recent.Constants");
        } catch (ClassNotFoundException e) {
            Log.d(TAG, "testConstants ClassNotFoundException");
        }
        Integer a = (Integer) TestUtils.getStaticProperty(cls, "ESCAPE_VELOCITY");
        assertEquals(100, (int) a);
        Log.v(TAG, "---------- end testConstants ----------");
    }

    public void testRecentItem01() {
        Log.v(TAG, "++++++++++ Start testRecentItem01 ++++++++++");
        toggleRecentApp();
        mRecentsPanelView = TestUtils.getProperty(mRecentsActivity, "mRecentsPanel");
        TestUtils.sleepBy(1000);
        ViewGroup container = (ViewGroup) TestUtils.getProperty(mRecentsPanelView, "mRecentsContainer");
        Boolean showing = (Boolean) TestUtils.getProperty(mRecentsPanelView, "mShowing");
        if (showing) {
            int count = ((ViewGroup) container.getChildAt(0)).getChildCount();
            if (count != 0) {
                View view = (View) ((ViewGroup) container.getChildAt(0)).getChildAt(count - 1);
                TouchUtils.longClickView(this, view);
                TestUtils.sleepBy(500);
                mInstrumentation.sendKeyDownUpSync(KeyEvent.ACTION_UP);
                mInstrumentation.waitForIdleSync();
                TestUtils.sleepBy(500);
                mInstrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN);
                mInstrumentation.waitForIdleSync();
                TestUtils.sleepBy(500);
                mInstrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN);
                mInstrumentation.waitForIdleSync();
                TestUtils.sleepBy(500);
                mInstrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_ENTER);
                mInstrumentation.waitForIdleSync();
                TestUtils.sleepBy(500);
                mInstrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_HOME);
                mInstrumentation.waitForIdleSync();
                TestUtils.sleepBy(500);
                showing = (Boolean) TestUtils.getProperty(mRecentsPanelView, "mShowing");
                assertFalse(showing);
            }
        }
        Log.v(TAG, "---------- end testRecentItem01 ----------");
    }

    public void testRecentItem02() {
        Log.v(TAG, "++++++++++ Start testRecentItem02 ++++++++++");
        toggleRecentApp();
        mRecentsPanelView = TestUtils.getProperty(mRecentsActivity, "mRecentsPanel");
        TestUtils.sleepBy(1000);
        ViewGroup container = (ViewGroup) TestUtils.getProperty(mRecentsPanelView, "mRecentsContainer");
        Boolean showing = (Boolean) TestUtils.getProperty(mRecentsPanelView, "mShowing");
        if (showing) {
            int countStart = ((ViewGroup) container.getChildAt(0)).getChildCount();
            if (countStart != 0) {
                View view = (View) ((ViewGroup) container.getChildAt(0)).getChildAt(countStart - 1);
                TouchUtils.longClickView(this, view);
                TestUtils.sleepBy(500);
                mInstrumentation.sendKeyDownUpSync(KeyEvent.ACTION_UP);
                mInstrumentation.waitForIdleSync();
                TestUtils.sleepBy(500);
                mInstrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN);
                mInstrumentation.waitForIdleSync();
                TestUtils.sleepBy(500);
                mInstrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_ENTER);
                mInstrumentation.waitForIdleSync();
                TestUtils.sleepBy(500);
                int countEnd = ((ViewGroup) container.getChildAt(0)).getChildCount();
                Log.d(TAG, "countStart = " + countStart + " countEnd = " + countEnd);
                assertEquals(countStart - 1, countEnd);
                mInstrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_HOME);
                mInstrumentation.waitForIdleSync();
                TestUtils.sleepBy(500);
            }
        }
        Log.v(TAG, "---------- end testRecentItem02 ----------");
    }

    public void testRecentItem03() {
        Log.v(TAG, "++++++++++ Start testRecentItem03 ++++++++++");
        toggleRecentApp();
        mRecentsPanelView = TestUtils.getProperty(mRecentsActivity, "mRecentsPanel");
        TestUtils.sleepBy(1000);        
        ViewGroup container = (ViewGroup) TestUtils.getProperty(mRecentsPanelView, "mRecentsContainer");
        Boolean showing = (Boolean) TestUtils.getProperty(mRecentsPanelView, "mShowing");
        if (showing) {
            int countStart = ((ViewGroup) container.getChildAt(0)).getChildCount();
            if (countStart != 0) {
                View view = (View) ((ViewGroup) container.getChildAt(0)).getChildAt(countStart - 1);
                TouchUtils.clickView(this, view);
                TestUtils.sleepBy(1000);
                showing = (Boolean) TestUtils.getProperty(mRecentsPanelView, "mShowing");
                assertFalse(showing);
                mInstrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_HOME);
                mInstrumentation.waitForIdleSync();
                TestUtils.sleepBy(500);
            }
        }
        Log.v(TAG, "---------- end testRecentItem03 ----------");
    }

    public void testRecentItem04() {
        Log.v(TAG, "++++++++++ Start testRecentItem04 ++++++++++");
        toggleRecentApp();
        mRecentsPanelView = TestUtils.getProperty(mRecentsActivity, "mRecentsPanel");
        TestUtils.sleepBy(1000);
        ViewGroup container = (ViewGroup) TestUtils.getProperty(mRecentsPanelView, "mRecentsContainer");
        Boolean showing = (Boolean) TestUtils.getProperty(mRecentsPanelView, "mShowing");
        if (showing) {
            int countStart = ((ViewGroup) container.getChildAt(0)).getChildCount();
            if (countStart != 0) {
                TouchUtils.drag(this, 300, 500, 700, 700, 3);
                TestUtils.sleepBy(1000);
                int countEnd = ((ViewGroup) container.getChildAt(0)).getChildCount();
                Log.d(TAG, "countStart = " + countStart + " countEnd = " + countEnd);
                assertEquals(countStart - 1, countEnd);
                mInstrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_HOME);
                mInstrumentation.waitForIdleSync();
                TestUtils.sleepBy(500);
            }
        }
        Log.v(TAG, "---------- end testRecentItem04 ----------");
    }

    public void testLandScapeShowAndCloseRecentApps() {
        mRecentsActivity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        Log.v(TAG, "++++++++++ Start testLandScapeShowAndCloseRecentApps ++++++++++");
        toggleRecentApp();
        mRecentsPanelView = TestUtils.getProperty(mRecentsActivity, "mRecentsPanel");
        TestUtils.sleepBy(1000);
        Boolean showingStart = (Boolean) TestUtils.getProperty(mRecentsPanelView, "mShowing");
        toggleRecentApp();
        TestUtils.sleepBy(1000);
        Boolean showingEnd = (Boolean) TestUtils.getProperty(mRecentsPanelView, "mShowing");
        if (showingStart) {
            assertFalse(showingEnd);
        } else {
            assertTrue(showingEnd);
        }
        Log.v(TAG, "---------- end testLandScapeShowAndCloseRecentApps ----------");
    }
}
