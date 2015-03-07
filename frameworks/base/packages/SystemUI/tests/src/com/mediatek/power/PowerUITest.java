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
package com.mediatek.systemui.power;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.os.BatteryManager;
import android.os.Message;
import android.test.AndroidTestCase;
import android.util.Log;
import com.mediatek.systemui.TestUtils;

public class PowerUITest extends AndroidTestCase {

    private static final String TAG = "StatusBarTest";
    private Object mPowerUI;
    private Context mContext;
    private AlertDialog mLowBatteryDialog;
    private AlertDialog mInvalidChargerDialog;
    private static final int EVENT_LOW_BATTERY_WARN_SOUND = 10;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mContext = getContext().createPackageContext("com.android.systemui",
                Context.CONTEXT_INCLUDE_CODE | Context.CONTEXT_IGNORE_SECURITY);
        Class cls = mContext.getClassLoader().loadClass("com.android.systemui.power.PowerUI");
        mPowerUI = cls.newInstance();
    }

    public void test001LowBattery() {
        Log.v(TAG, "++++++++++ Start test001LowBattery ++++++++++");
        TestUtils.setSuperClassProperty(mPowerUI, "mContext", mContext);
        Object obj = TestUtils.getProperty(mPowerUI, "mIntentReceiver");
        TestUtils.setProperty(mPowerUI, "mLowBatteryAlertCloseLevel", 20);
        TestUtils.setProperty(mPowerUI, "mLowBatteryReminderLevels", new int[] { 15, 4 });
        Intent intent = new Intent(Intent.ACTION_BATTERY_CHANGED);
        intent.putExtra(BatteryManager.EXTRA_LEVEL, 10);
        intent.putExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_HEALTH_GOOD);
        intent.putExtra(BatteryManager.EXTRA_PLUGGED, 0);
        TestUtils.invokeMethod(obj, "onReceive", new Class[] { Context.class, Intent.class }, new Object[] { mContext,
                intent });
        mLowBatteryDialog = (AlertDialog) TestUtils.getProperty(mPowerUI, "mLowBatteryDialog");
        if (mLowBatteryDialog != null) {
            assertTrue(mLowBatteryDialog.isShowing());
        }

        intent.putExtra(BatteryManager.EXTRA_LEVEL, 100);
        intent.putExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_HEALTH_GOOD);
        intent.putExtra(BatteryManager.EXTRA_PLUGGED, 1);
        TestUtils.invokeMethod(obj, "onReceive", new Class[] { Context.class, Intent.class }, new Object[] { mContext,
                intent });
        mLowBatteryDialog = (AlertDialog) TestUtils.getProperty(mPowerUI, "mLowBatteryDialog");
        if (mLowBatteryDialog != null) {
            assertFalse(mLowBatteryDialog.isShowing());
        }
        Log.v(TAG, "---------- end test001LowBattery ----------");
    }

    public void test002InvalidChargerDialog() {
        Log.v(TAG, "++++++++++ Start test002InvalidChargerDialog ++++++++++");
        TestUtils.setSuperClassProperty(mPowerUI, "mContext", mContext);
        Object obj = TestUtils.getProperty(mPowerUI, "mIntentReceiver");

        Intent intent = new Intent(Intent.ACTION_BATTERY_CHANGED);
        intent.putExtra(BatteryManager.EXTRA_INVALID_CHARGER, 1);
        TestUtils.invokeMethod(obj, "onReceive", new Class[] { Context.class, Intent.class }, new Object[] { mContext,
                intent });
        mInvalidChargerDialog = (AlertDialog) TestUtils.getProperty(mPowerUI, "mInvalidChargerDialog");
        if (mInvalidChargerDialog != null) {
            assertTrue(mInvalidChargerDialog.isShowing());
        }

        intent.putExtra(BatteryManager.EXTRA_INVALID_CHARGER, 0);
        TestUtils.invokeMethod(obj, "onReceive", new Class[] { Context.class, Intent.class }, new Object[] { mContext,
                intent });
        if (mInvalidChargerDialog != null) {
            assertFalse(mInvalidChargerDialog.isShowing());
        }
        Log.v(TAG, "---------- end test002InvalidChargerDialog ----------");
    }

    public void test003BroadCast() {
        Log.v(TAG, "++++++++++ Start test003BroadCast ++++++++++");
        TestUtils.setSuperClassProperty(mPowerUI, "mContext", mContext);
        Object obj = TestUtils.getProperty(mPowerUI, "mIntentReceiver");

        Intent intent = new Intent(Intent.ACTION_BATTERY_LOW);
        TestUtils.invokeMethod(obj, "onReceive", new Class[] { Context.class, Intent.class }, new Object[] { mContext,
                intent });
        assertTrue((Boolean) TestUtils.getProperty(mPowerUI, "mInBatteryLow"));

        intent = new Intent(Intent.ACTION_BATTERY_OKAY);
        TestUtils.invokeMethod(obj, "onReceive", new Class[] { Context.class, Intent.class }, new Object[] { mContext,
                intent });
        assertFalse((Boolean) TestUtils.getProperty(mPowerUI, "mInBatteryLow"));

        intent = new Intent("android.intent.action.normal.boot");
        TestUtils.invokeMethod(obj, "onReceive", new Class[] { Context.class, Intent.class }, new Object[] { mContext,
                intent });
        assertFalse((Boolean) TestUtils.getProperty(mPowerUI, "mHideLowBDialog"));
        
        intent = new Intent(Intent.ACTION_CONFIGURATION_CHANGED);
        TestUtils.invokeMethod(obj, "onReceive", new Class[] { Context.class, Intent.class }, new Object[] { mContext,
                intent });
        
        intent = new Intent("android.intent.action.ACTION_SHUTDOWN_IPO");
        TestUtils.invokeMethod(obj, "onReceive", new Class[] { Context.class, Intent.class }, new Object[] { mContext,
                intent });
        assertTrue((Boolean) TestUtils.getProperty(mPowerUI, "mHideLowBDialog"));
        
        intent = new Intent("unknowintent");
        TestUtils.invokeMethod(obj, "onReceive", new Class[] { Context.class, Intent.class }, new Object[] { mContext,
                intent });
        Log.v(TAG, "---------- end test003BroadCast ----------");
    }

    public void test004BatteryHandler() {
        Log.v(TAG, "++++++++++ Start test004BatteryHandler ++++++++++");
        TestUtils.setSuperClassProperty(mPowerUI, "mContext", mContext);
        Object obj = TestUtils.getProperty(mPowerUI, "mHandler");
        Message msg = Message.obtain();
        msg.what = EVENT_LOW_BATTERY_WARN_SOUND;
        TestUtils.invokeMethod(obj, "handleMessage", new Class[] { Message.class }, new Object[] { msg });
        obj = TestUtils.getProperty(mPowerUI, "mNP");
        assertNotNull(obj);
        Log.v(TAG, "---------- end test004BatteryHandler ----------");
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
    }

}
