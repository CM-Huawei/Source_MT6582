/* Copyright Statement:
 *
 * This software/firmware and related documentation ("MediaTek Software") are
 * protected under relevant copyright laws. The information contained herein
 * is confidential and proprietary to MediaTek Inc. and/or its licensors.
 * Without the prior written permission of MediaTek inc. and/or its licensors,
 * any reproduction, modification, use or disclosure of MediaTek Software,
 * and information contained herein, in whole or in part, shall be strictly prohibited.
 *
 * MediaTek Inc. (C) 2010. All rights reserved.
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

package com.mediatek.settings.wfd;

import android.app.FragmentManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.ContentObserver;
import android.hardware.display.DisplayManager;
import android.hardware.display.WifiDisplayStatus;
import android.net.Uri;
import android.os.Handler;
import android.preference.Preference;
import android.preference.PreferenceCategory;
import android.preference.PreferenceScreen;
import android.provider.Settings;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Switch;
import android.widget.TextView;

import com.android.settings.R;
import com.mediatek.xlog.Xlog;

import java.util.ArrayList;
import java.util.Arrays;

public class WfdSettingsExt {
    private static final String TAG = "WfdSettingsExt";

    private Context mContext;
    private Preference mWfdPreference;
    private DisplayManager mDisplayManager;

    //change resolution menu id
    private static final int MENU_ID_CHANGE_RESOLUTION = Menu.FIRST + 1;

    //720p device resolution 0: 720p 30fps off; 1:720p 60fps off; 3:720p 60fps on; 4:720p 30fps on
    public static final int DEVICE_720P_60FPS_RESOLUTION = 3;
    public static final int DEVICE_720P_30FPS_RESOLUTION = 4;
    public static final ArrayList<Integer> DEVICE_720P_RESOLUTION_LIST = new ArrayList(
            Arrays.asList(DEVICE_720P_60FPS_RESOLUTION, DEVICE_720P_30FPS_RESOLUTION));
    
    //1080p device resolution 2: 1080p off; 5:1080p on; 6:720p 60 fps on; 7:720p 30 fps on
    public static final int DEVICE_1080P_ON_RESOLUTION = 5;
    public static final int DEVICE_1080P_60FPS_RESOLUTION = 6;
    public static final int DEVICE_1080P_30FPS_RESOLUTION = 7;
    public static final ArrayList<Integer> DEVICE_1080P_RESOLUTION_LIST = new ArrayList(
            Arrays.asList(DEVICE_1080P_ON_RESOLUTION, DEVICE_1080P_60FPS_RESOLUTION, 
            DEVICE_1080P_30FPS_RESOLUTION));

    public WfdSettingsExt(Context context) {
        mContext = context;
        mDisplayManager = (DisplayManager)mContext.getSystemService(
                Context.DISPLAY_SERVICE);
    }    

    /**
     * Update the wfd preference summary in DisplaySettings
     * @param wfdPreference the preference in displaySetting
     */
    public void updateWfdPreferenceSummary(Preference wfdPreference) {
        WifiDisplayStatus wifiDisplayStatus = mDisplayManager.getWifiDisplayStatus();
        if(wifiDisplayStatus.getFeatureState() == WifiDisplayStatus.FEATURE_STATE_DISABLED) {
            Xlog.d(TAG, "updateWfdPreferenceSummary: set preference summary as disabled");
            wfdPreference.setSummary(R.string.wifi_display_summary_disabled);
        } else {
            boolean wifiDisplayOnSetting = Settings.Global.getInt(mContext.getContentResolver(),
                    Settings.Global.WIFI_DISPLAY_ON, 0) != 0;
            Xlog.d(TAG, "updateWfdPreferenceSummary: " + "wifiDisplayOnSetting is " + wifiDisplayOnSetting);
            wfdPreference.setSummary(wifiDisplayOnSetting ? R.string.wifi_display_summary_on 
                    : R.string.wifi_display_summary_off);
        }
    }

    /**
     * register wfd content observer and broadcast receiver when called onResume()
     * @param wfdPreference the preference that will be updated when the provider value change
     */
    public void registerForWfdSwicth(Preference wfdPreference) {
        mWfdPreference = wfdPreference;
        mContext.getContentResolver().registerContentObserver(Settings.Global.getUriFor(
                Settings.Global.WIFI_DISPLAY_ON), false, mSettingsObserver);
        mContext.registerReceiver(mReceiver, new IntentFilter(
                DisplayManager.ACTION_WIFI_DISPLAY_STATUS_CHANGED));
    }

    private final ContentObserver mSettingsObserver = new ContentObserver(new Handler()) {
        @Override
        public void onChange(boolean selfChange, Uri uri) {
            Xlog.d(TAG, "ContentObserver, onChange()");
            updateWfdPreferenceSummary(mWfdPreference);
        }
    };

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(DisplayManager.ACTION_WIFI_DISPLAY_STATUS_CHANGED)) {
                Xlog.d(TAG, "receive WIFI_DISPLAY_STATUS_CHANGED broadcast");
                updateWfdPreferenceSummary(mWfdPreference);
            }
        }
    };

    /**
     * unregister wfd content observer and broadcast receiver when called onPause()
     */
    public void unregisterForWfdSwicth() {
        mContext.getContentResolver().unregisterContentObserver(mSettingsObserver);
        mContext.unregisterReceiver(mReceiver);
    }

    /**
     * refresh the empty view when the feature state is off or disabled
     * @param featureState current WFD FWK feature state
     * @param emptyView the empty view
     * @param screen the preference screen
     */
    public void refreshEmptyView(int featureState, TextView emptyView, PreferenceScreen screen) {
        if(featureState == WifiDisplayStatus.FEATURE_STATE_DISABLED) {
        	emptyView.setText(R.string.wifi_display_settings_empty_list_wifi_display_disabled);
        } else {
            boolean wfdOnSettings = Settings.Global.getInt(mContext.getContentResolver(),
                    Settings.Global.WIFI_DISPLAY_ON, 0) != 0;
            Xlog.d(TAG, "refresh UI, wfd status is off, user select is " + wfdOnSettings);
            //wfd feature state is off, but wfd user select is on
            if(wfdOnSettings) {
            	PreferenceCategory category = new PreferenceCategory(mContext);
                category.setTitle(R.string.wifi_display_available_devices);
                category.setEnabled(false);
                screen.addPreference(category);
            } else {
                emptyView.setText(R.string.wifi_display_settings_empty_list_wifi_display_off);
            }
        }
    }

    /**
     * add change resolution option menu
     * @param menu the menu that change resolution menuitem will be added to
     * @param status current wfd status
     */
    public void onCreateOptionMenu(Menu menu, WifiDisplayStatus status) {
        int currentResolution = Settings.Global.getInt(mContext.getContentResolver(), 
                Settings.Global.WIFI_DISPLAY_RESOLUTION, 0);
        Xlog.d(TAG, "current resolution is " + currentResolution);
        if(DEVICE_720P_RESOLUTION_LIST.contains(currentResolution) || 
                DEVICE_1080P_RESOLUTION_LIST.contains(currentResolution)) {
            menu.add(Menu.NONE, MENU_ID_CHANGE_RESOLUTION, 0 ,R.string.wfd_change_resolution_menu_title)
            .setEnabled(status.getFeatureState() == WifiDisplayStatus.FEATURE_STATE_ON)
            .setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
        }
    }
    
    /**
     * called when the option menu is selected
     * @param item the selected menu item
     * @return true, change resolution item is selected, otherwise false
     */
    public boolean onOptionMenuSelected(MenuItem item, FragmentManager fragmentManager) {
        if(item.getItemId() == MENU_ID_CHANGE_RESOLUTION) {
            new WfdChangeResolutionFragment().show(
                    fragmentManager, "change resolution");
            return true;
        }
        return false;
    }
}