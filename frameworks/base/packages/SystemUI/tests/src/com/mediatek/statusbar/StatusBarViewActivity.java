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
package com.mediatek.systemui.statusbar;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager.NameNotFoundException;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.WindowManagerImpl;
import android.widget.FrameLayout;
import com.android.systemui.statusbar.phone.PhoneStatusBarView;
import com.android.internal.telephony.PhoneConstants;
import com.mediatek.common.featureoption.FeatureOption;
import com.mediatek.systemui.TestUtils;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;

public class StatusBarViewActivity extends Activity {

    public Context mSystemUIContext;
    public Object mPhoneStatusBar;
    public Object mStatusBarWindow;
    public Object mStatusBarView;
    public Object mToolBarView;

    private static final String TAG = "StatusBarTest_StatusBarViewActivity";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.v(TAG, "StatusBarViewActivity onCreate");
        requestWindowFeature(Window.FEATURE_NO_TITLE);

        Window win = getWindow();
        WindowManager.LayoutParams winParams = win.getAttributes();
        winParams.flags |= (WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
                | WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                | WindowManager.LayoutParams.FLAG_ALLOW_LOCK_WHILE_SCREEN_ON
                | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
         win.setAttributes(winParams);
    }

    @Override
    protected void onDestroy() {
        Log.d(TAG, "StatusBarViewActivity onDestroy");
        super.onDestroy();
    }

    public void setTestView() {
        try {
            mSystemUIContext = this.createPackageContext("com.android.systemui", Context.CONTEXT_INCLUDE_CODE
                    | Context.CONTEXT_IGNORE_SECURITY);

            /// Stop SystemUI service to avoid adding another statusbar window.
            //Intent intent = new Intent();
            //intent.setComponent(new ComponentName("com.android.systemui",
            //        "com.android.systemui.SystemUIService"));
            //mSystemUIContext.stopService(intent);

            Class cls = mSystemUIContext.getClassLoader().loadClass("com.android.systemui.statusbar.phone.PhoneStatusBar");
            mPhoneStatusBar = cls.newInstance();

            TestUtils.setProperty(mPhoneStatusBar, "mContext", mSystemUIContext);
            TestUtils.setSuperClassProperty(mPhoneStatusBar, "mContext", mSystemUIContext);

            TestUtils.invokeMethod(mPhoneStatusBar, "setRunningInTest", new Class[] { boolean.class },
                    new Object[] { true });

            TestUtils.invokeMethod(mPhoneStatusBar, "start", new Class[] {}, new Object[] {});

            TestUtils.setProperty(mPhoneStatusBar, "mNavigationBarView", null);
            mStatusBarWindow = TestUtils.getProperty(mPhoneStatusBar, "mStatusBarWindow");
            mStatusBarView   = TestUtils.getProperty(mPhoneStatusBar, "mStatusBarView");

            /// Deregister the broadcast event.
            Object receiver =  TestUtils.getProperty(mPhoneStatusBar, "mBroadcastReceiver");
            mSystemUIContext.unregisterReceiver((BroadcastReceiver)receiver);

            //((ViewGroup)mStatusBarWindow).removeView((View)mStatusBarView);
            ((ViewGroup)mStatusBarWindow).removeAllViews();

            getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
            setContentView((View)mStatusBarView);

            TestUtils.setProperty(mPhoneStatusBar, "mExpandedVisible", true);
            TestUtils.invokeMethod(mPhoneStatusBar, "updateExpandedViewPos", new Class[] { int.class },
                    new Object[] { -10001 });
            TestUtils.setProperty(mPhoneStatusBar, "mExpandedVisible", false);
        } catch (IllegalArgumentException e) {
            Log.d(TAG, "IllegalArgumentException");
            e.printStackTrace();
        } catch (ClassNotFoundException e) {
            Log.d(TAG, "ClassNotFoundException");
            e.printStackTrace();
        } catch (NameNotFoundException e) {
            Log.d(TAG, "NameNotFoundException");
            e.printStackTrace();
        } catch (InstantiationException e) {
            Log.d(TAG, "InstantiationException");
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            Log.d(TAG, "IllegalAccessException");
            e.printStackTrace();
        }
/*
            catch (IllegalAccessException e) {
            Log.d(TAG, "IllegalAccessException");
            e.printStackTrace();
        }  catch (InstantiationException e) {
            Log.d(TAG, "InstantiationException");
            e.printStackTrace();
        } catch (NoSuchMethodException e) {
            Log.d(TAG, "NoSuchMethodException");
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            Log.d(TAG, "InvocationTargetException");
            Log.d(TAG, "", e.getCause());
        }
*/
    }

    public void removeTestView() { 
        setContentView(new FrameLayout(this));
        Log.v(TAG, "remove KeyguardHostView from contentView");
    }
}
