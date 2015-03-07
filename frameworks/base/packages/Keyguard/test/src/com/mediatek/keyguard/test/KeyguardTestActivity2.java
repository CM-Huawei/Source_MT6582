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

/*import com.android.keyguard.KeyguardHostView;
import com.android.keyguard.KeyguardViewManager;
import com.android.keyguard.KeyguardViewMediator;
import com.android.keyguard.KeyguardSelectorView;
import com.android.keyguard.KeyguardSecurityViewFlipper;
*/

import com.mediatek.keyguard.util.KeyguardTestUtils;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Configuration;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.os.Debug;
import android.graphics.PixelFormat;
import com.android.internal.widget.LockPatternUtils;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;

public class KeyguardTestActivity2 extends Activity {
    public Object mKeyguardMediator; /// Object
    
    public Context mContext;
    public Object mKeyguardViewManager;
    FrameLayout mKeyguardHost;
    FrameLayout mKeyguardHostView;
    private static final String TAG = "KeyguardTestActivity2";
    WindowManager.LayoutParams params;
    boolean mViewDeattached;

    public boolean isAntiTheftAutoTest = false ;

    public Context mKeyguardContext;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.v(TAG, "onCreate");

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
        Log.d(TAG, "onDestroy");
        super.onDestroy();

    }

    public void setKeyguardHostView() {
    try {
        mKeyguardContext = this.createPackageContext("com.android.keyguard", Context.CONTEXT_INCLUDE_CODE
                | Context.CONTEXT_IGNORE_SECURITY);
        
        Log.v(TAG, "mKeyguardContext = "+mKeyguardContext);
        
        Class cls = mKeyguardContext.getClassLoader().loadClass("com.android.keyguard.KeyguardViewMediator");
        //mKeyguardMediator = (KeyguardViewMediator) cls.newInstance();
        // specify param class
        Class[] params = new Class[2];
        params[0] = Context.class;
        params[1] = LockPatternUtils.class;
        Constructor constructor = cls.getConstructor(params);
        
        // specify param value
        Object[] paramObjs = new Object[2];
        paramObjs[0] = mKeyguardContext;
        paramObjs[1] = null;
        mKeyguardMediator =  constructor.newInstance(paramObjs);
        Log.v(TAG, "mKeyguardMediator = "+mKeyguardMediator);

        if (mKeyguardMediator != null) {
            /// KeyguardViewManager
            mKeyguardViewManager = KeyguardTestUtils.getProperty((Object)mKeyguardMediator, "mKeyguardViewManager");
            Log.v(TAG, "getProperty mKeyguardViewManager = "+mKeyguardViewManager);
            if (mKeyguardViewManager != null) {
                /// reCreate window
                KeyguardTestUtils.setProperty(mKeyguardMediator, "isKeyguardInActivity", true);
                Log.v(TAG, "setProperty isKeyguardInActivity done");

                if(isAntiTheftAutoTest) {
                    KeyguardTestUtils.setProperty(mKeyguardMediator, "mAntiTheftModeAutoTest", true);
                    Log.v(TAG, "setProperty mKeyguardMediator mAntiTheftModeAutoTest true");
                }
                else {
                    KeyguardTestUtils.setProperty(mKeyguardMediator, "mAntiTheftModeAutoTest", false);
                    Log.v(TAG, "setProperty mKeyguardMediator mAntiTheftModeAutoTest false");
                }
				
                KeyguardTestUtils.invokeMethod(mKeyguardMediator, "setKeyguardEnabled", new Class[] {boolean.class}, new Object[] {true});
                Log.v(TAG, "invokeMethod setKeyguardEnabled done");
                KeyguardTestUtils.invokeMethod(mKeyguardViewManager, "show", new Class[] {Bundle.class}, new Object[] {null});
                Log.v(TAG, "invokeMethod show done");

                Object mViewManager = KeyguardTestUtils.getProperty(mKeyguardViewManager, "mViewManager");
                Log.v(TAG, "getProperty mViewManager="+mViewManager);
                mKeyguardHost = (FrameLayout) KeyguardTestUtils.getProperty(mKeyguardViewManager, "mKeyguardHost");
                Log.v(TAG, "getProperty mKeyguardHost="+mKeyguardHost);

                mKeyguardHostView = (FrameLayout) KeyguardTestUtils.getProperty(mKeyguardViewManager, "mKeyguardView");
                Log.v(TAG, "getProperty mKeyguardHostView="+mKeyguardHostView);

                mKeyguardHost.removeView(mKeyguardHostView);
                Log.v(TAG, "removeView mKeyguardView done");
                setContentView(mKeyguardHostView);
                Log.v(TAG, "set Content done");

            } else {
                Log.v(TAG, "mKeyguardViewManager is null");
            }
        } else {
            Log.v(TAG, "mKeyguardMediator is null");
        }
       } catch (IllegalArgumentException e) {
           Log.d(TAG, "IllegalArgumentException");
           e.printStackTrace();
       } catch (ClassNotFoundException e) {
           Log.d(TAG, "ClassNotFoundException");
           e.printStackTrace();
       } catch (NoSuchMethodException e) {
           e.printStackTrace();
       } catch (InvocationTargetException e) {
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
    }


    @Override
    public void onConfigurationChanged (Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        Log.v(TAG, "onConfigurationChanged");
    }


}
