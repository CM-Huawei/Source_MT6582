/*
 * Copyright (C) 2010 The Android Open Source Project
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

package com.android.systemui.statusbar.policy;

import android.app.ActivityManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.os.SystemProperties;
import android.provider.Settings;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.android.systemui.R;

import com.mediatek.systemui.statusbar.util.BatteryHelper;
import com.mediatek.systemui.statusbar.util.LaptopBatteryView;
import com.mediatek.systemui.statusbar.util.SIMHelper;
import com.mediatek.xlog.Xlog;

import java.util.ArrayList;

public class BatteryController extends BroadcastReceiver {
    private static final String TAG = "StatusBar.BatteryController";


    private ArrayList<BatteryStateChangeCallback> mChangeCallbacks =
            new ArrayList<BatteryStateChangeCallback>();

    public interface BatteryStateChangeCallback {
        public void onBatteryLevelChanged(int level, boolean pluggedIn);
    }

    public BatteryController(Context context) {
        mContext = context;
        /// M: Support "battery percentage".
        mShouldShowBatteryPercentage = (Settings.Secure.getInt(context
                .getContentResolver(), Settings.Secure.BATTERY_PERCENTAGE, 0) != 0);
        /// M: Don't support battery percentage with smartbook plugged in.
        if (SIMHelper.isSmartBookPluggedIn(mContext)) {
            mShouldShowBatteryPercentage = false;
        }
        Xlog.d(TAG, "BatteryController mShouldShowBatteryPercentage is "
                + mShouldShowBatteryPercentage);
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_BATTERY_CHANGED);
        /// M: Support "battery percentage".
        filter.addAction(ACTION_BATTERY_PERCENTAGE_SWITCH);
        filter.addAction(Intent.ACTION_USER_SWITCHED);
        context.registerReceiver(this, filter);
    }

    public void addStateChangedCallback(BatteryStateChangeCallback cb) {
        mChangeCallbacks.add(cb);
    }

    public void onReceive(Context context, Intent intent) {
        final String action = intent.getAction();
        Xlog.d(TAG,"BatteryController onReceive action is " + action);
        if (action.equals(Intent.ACTION_BATTERY_CHANGED)) {
            final int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, 0);
            final int status = intent.getIntExtra(BatteryManager.EXTRA_STATUS,
                    BatteryManager.BATTERY_STATUS_UNKNOWN);

            boolean plugged = false;
            switch (status) {
                case BatteryManager.BATTERY_STATUS_CHARGING:
                case BatteryManager.BATTERY_STATUS_FULL:
                    plugged = true;
                    break;
            }

            int N = mLabelViews.size();
            for (int i=0; i<N; i++) {
                TextView v = mLabelViews.get(i);
                v.setText(mContext.getString(R.string.status_bar_settings_battery_meter_format,
                        level));
            }

            for (BatteryStateChangeCallback cb : mChangeCallbacks) {
                cb.onBatteryLevelChanged(level, plugged);
            }

            /// M: Support "battery percentage". @{
            mBatteryPercentage = getBatteryPercentage(intent);
            Xlog.d(TAG,"mBatteryPercentage is " + mBatteryPercentage + " mShouldShowBatteryPercentage is "
                    + mShouldShowBatteryPercentage + " mLabelViews.size() " + mLabelViews.size());
            refreshBatteryPercentage();
            /// M: Support "battery percentage". @}

            /// M: Support Laptop Battery Status.
            updateLaptopBatteryInfo(intent);
        }
        /// M: Support "battery percentage". @{
        else if (action.equals(ACTION_BATTERY_PERCENTAGE_SWITCH)) {
            mShouldShowBatteryPercentage = (intent.getIntExtra("state",0) == 1);
            Xlog.d(TAG, " OnReceive from mediatek.intent.ACTION_BATTERY_PERCENTAGE_SWITCH  mShouldShowBatteryPercentage" +
                    " is " + mShouldShowBatteryPercentage);
            refreshBatteryPercentage();
        } else if (Intent.ACTION_USER_SWITCHED.equals(action)) {
            mShouldShowBatteryPercentage = (Settings.Secure.getIntForUser(context
                    .getContentResolver(), Settings.Secure.BATTERY_PERCENTAGE, 0, ActivityManager.getCurrentUser()) != 0);
            Xlog.d(TAG, "ACTION_USER_SWITCHED mShouldShowBatteryPercentage is "
                    + mShouldShowBatteryPercentage + " ActivityManager.getCurrentUser() is " + ActivityManager.getCurrentUser());
            refreshBatteryPercentage();
        }
        /// @}
    }


    /// M: Support "Battery Percentage Switch"
    private static final String ACTION_BATTERY_PERCENTAGE_SWITCH = "mediatek.intent.action.BATTERY_PERCENTAGE_SWITCH";
    /// M: Support "battery percentage". @{
    private boolean mShouldShowBatteryPercentage = false;
    private String mBatteryPercentage = "100%";
    /// @}
    private Context mContext;
    private ArrayList<TextView> mLabelViews = new ArrayList<TextView>();

    public void addLabelView(TextView v) {
        mLabelViews.add(v);
    }

    /// M: Support "battery percentage". @{
    private  String getBatteryPercentage(Intent batteryChangedIntent) {
        int level = batteryChangedIntent.getIntExtra("level", 0);
        int scale = batteryChangedIntent.getIntExtra("scale", 100);
        return String.valueOf(level * 100 / scale) + "%";
    }
    /// @}

    /// M: Support "battery percentage". @{
    private void refreshBatteryPercentage() {
        if (mLabelViews.size() > 0) {
            TextView v = mLabelViews.get(0);
            if (v != null) {
                if (mShouldShowBatteryPercentage) {
                    v.setText(mBatteryPercentage);
                    v.setVisibility(View.VISIBLE);
                } else {
                    v.setVisibility(View.GONE);
                }
            }
        }
    }
    /// @}

    /// M: Support Laptop Battery Status.
    private LaptopBatteryView mIconView;
    public void addIconView(LaptopBatteryView v) {
        if (v != null) {
            mIconView = v;
        }
    }

    private ArrayList<LaptopBatteryStateChangeCallback> mLaptopBatteryCallbacks =
            new ArrayList<LaptopBatteryStateChangeCallback>();

    public interface LaptopBatteryStateChangeCallback {
        public void onLaptopBatteryLevelChanged(int level, boolean pluggedIn, boolean isPresent);
    }

    public void addLaptopStateChangedCallback(LaptopBatteryStateChangeCallback cb) {
        mLaptopBatteryCallbacks.add(cb);
    }

    private void updateLaptopBatteryInfo(Intent intent) {
        final boolean isPresent = intent.getBooleanExtra(BatteryManager.EXTRA_PRESENT_2ND, false);
        if (isPresent) {
            final int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL_2ND, 0);
            final int status = intent.getIntExtra(BatteryManager.EXTRA_STATUS_2ND,
                BatteryManager.BATTERY_STATUS_UNKNOWN);
            final boolean isPlugForProtection = 
                 BatteryHelper.isPlugForProtection(status, level);

            Xlog.d(TAG,"[Laptop] mBatteryProtection = "
                    + isPlugForProtection + ", status = " 
                    + status + ", level = " + level 
                    + ", isPlugForProtection= " + isPlugForProtection);

            for (LaptopBatteryStateChangeCallback cb : mLaptopBatteryCallbacks) {
                cb.onLaptopBatteryLevelChanged(level, isPlugForProtection, true);
            }

            if (mIconView != null) {
                mIconView.setVisibility(View.VISIBLE);
                mIconView.setBatteryLevel(level, isPlugForProtection);
            }
        } else {
            Xlog.d(TAG, "[Laptop] isPresent = false");
            for (LaptopBatteryStateChangeCallback cb : mLaptopBatteryCallbacks) {
                cb.onLaptopBatteryLevelChanged(0, false, false);
            }
            if (mIconView != null) {
                mIconView.setVisibility(View.GONE);
            }
        }
    }
}
