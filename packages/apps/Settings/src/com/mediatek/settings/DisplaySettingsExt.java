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
 */

package com.mediatek.settings;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.hardware.display.DisplayManager;
import android.hardware.display.WifiDisplayStatus;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.SystemProperties;
import android.preference.CheckBoxPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceClickListener;
import android.preference.PreferenceCategory;
import android.preference.PreferenceGroup;
import android.preference.PreferenceScreen;
import android.provider.Settings;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.RelativeSizeSpan;
import android.text.style.SuperscriptSpan;

import com.android.settings.R;
import com.android.settings.Utils;
import com.mediatek.common.featureoption.FeatureOption;
import com.mediatek.common.hdmi.IMtkHdmiManager;
import com.mediatek.keyguard.ext.IKeyguardLayer;
import com.mediatek.keyguard.ext.KeyguardLayerInfo;
import com.mediatek.pluginmanager.Plugin;
import com.mediatek.pluginmanager.PluginManager;
import com.mediatek.settings.ext.ISettingsMiscExt;
import com.mediatek.settings.wfd.WfdSettingsExt;
import com.mediatek.settings.ext.IStatusBarPlmnDisplayExt;
import com.mediatek.settings.ext.DefaultStatusBarPlmnDisplayExt;
import com.mediatek.thememanager.ThemeManager;
import com.mediatek.xlog.Xlog;

import java.util.List;

public class DisplaySettingsExt implements OnPreferenceClickListener {
    private static final String TAG = "mediatek.DisplaySettings";
    private static final String KEY_HDMI_SETTINGS = "hdmi_settings";
    private static final String KEY_COLOR = "color";
    private static final String KEY_FONT_SIZE = "font_size";
    private static final String KEY_SCREEN_TIMEOUT = "screen_timeout";
    private static final String KEY_ACCELEROMETER = "accelerometer";
    private static final String KEY_WIFI_DISPLAY = "wifi_display";
    
    private static final String DATA_STORE_NONE = "none";
    
    private Preference mHDMISettings;
    private ISettingsMiscExt mExt;

    private PreferenceCategory mDisplayPerCategory;
    private PreferenceCategory mDisplayDefCategory;
    private static final String DISPLAY_PERSONALIZE = "display_personalize";
    private static final String DISPLAY_DEFAULT = "display_default";
    private static final String KEY_WALLPAPER = "wallpaper";
    private static final String KEY_MTK_WALLPAPER = "mtk_wallpaper";
    private static final String CONTACT_STRING = "&";
    private static final int PARSER_STRING_LENGTH_ZERO = 0;
    private static final int PARSER_STRING_LENGTH_ONE = 1;
    private static final int PARSER_STRING_LENGTH_TWO = 2;
    Preference mColorPref;
    Preference mWallpaperPref;

    private static final String KEY_LOCK_SCREEN_NOTIFICATIONS = "lock_screen_notifications";
    private static final String INCOMING_INDICATOR_ON_LOCKSCREEN = "incoming_indicator_on_lockscreen";
    private static final String LOCK_SCREEN_STYLE_INTENT_PACKAGE = "com.android.settings";
    private static final String LOCK_SCREEN_STYLE_INTENT_NAME = "com.mediatek.lockscreensettings.LockScreenStyleSettings";
    private static final String  KEY_LOCK_SCREEN_STYLE = "lock_screen_style";
    public static final String CURRENT_KEYGURAD_LAYER_KEY = "mtk_current_keyguard_layer";
    private static final int DEFAULT_LOCK_SCREEN_NOTIFICATIONS = 1;
    private CheckBoxPreference mLockScreenNotifications;
    private Preference mLockScreenStylePref;
    private IStatusBarPlmnDisplayExt mPlmnName;
    private boolean mIsUpdateFont;
    private Context mContext;
    
    private Preference mScreenTimeoutPreference;
    private ListPreference mFontSizePref;
    
    private static final int TYPE_CATEGORY = 0;
    private static final int TYPE_PREFERENCE = 1;
    private static final int TYPE_CHECKBOX = 2;
    private static final int TYPE_LIST = 3;
    private static final int SUM_CATEGORY = 2;
    
    // add WfdSettingsExt instance
    private WfdSettingsExt mWfdExt = null;
    
    private IMtkHdmiManager mHdmiManager;

    // add for clearMotion
    private static final String KEY_CLEAR_MOTION = "clearMotion";
    private static final String KEY_DISPLAY_CLEAR_MOTION = "persist.sys.display.clearMotion";
    private static final String KEY_DISPLAY_CLEAR_MOTION_DIMMED = "sys.display.clearMotion.dimmed";
    private static final String ACTION_CLEARMOTION_DIMMED = "com.mediatek.clearmotion.DIMMED_UPDATE";
    private CheckBoxPreference mClearMotion;
    
    public DisplaySettingsExt(Context context) {
        Xlog.d(TAG, "DisplaySettingsExt");
        mContext = context;
        
    }  
    
    private BroadcastReceiver mPackageReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context1, Intent intent) {
            Xlog.d(TAG,"package changed, update list");
            //add for lockScreen
            updateLockScreenStyle();
        }
    };
    
    /**
     *    
     * @param type: 0:PreferenceCategory; 1:Preference; 2:CheckBoxPreference; 3:ListPreference
     * @param titleRes
     * @param key
     * @param screen
     * @return
     */
    private Preference createPreference(int type, int titleRes, String key, PreferenceGroup screen) {
        Preference preference = null;
        switch (type) {
            case TYPE_CATEGORY:
                preference = new PreferenceCategory(mContext);
                break;
            case TYPE_PREFERENCE:
                preference = new Preference(mContext);
                break;
            case TYPE_CHECKBOX:
                preference = new CheckBoxPreference(mContext);
                preference.setOnPreferenceClickListener(this);
                break;
            case TYPE_LIST:
                preference = new ListPreference(mContext);
                preference.setOnPreferenceClickListener(this);
                break;
            default:
                break;
        }
        preference.setKey(key);
        preference.setTitle(titleRes);
        screen.addPreference(preference);
        return preference;
    }
 
    /*
     * initPreference: 1. new all mtk feature preference 2. add all google default preference to mDisplayDefCategory 
     *       3. add hdmi and landscapeLauncher behind font_size 4. remove google default wallpaper
     * @screen : UI Screen 
     */
    private void initPreference(PreferenceScreen screen) {
        mDisplayPerCategory = (PreferenceCategory)createPreference(TYPE_CATEGORY, R.string.display_personalize,
                                                  DISPLAY_PERSONALIZE, screen);

        mDisplayDefCategory = (PreferenceCategory)createPreference(TYPE_CATEGORY, R.string.display_default, 
                                                  DISPLAY_DEFAULT, screen);
        
        // add for clearMotion
        mClearMotion = (CheckBoxPreference)createPreference(TYPE_CHECKBOX, R.string.clear_motion_title, 
                KEY_CLEAR_MOTION, mDisplayPerCategory);
        mClearMotion.setSummary(R.string.clear_motion_summary);
        clearMotionStyle();
        if (mClearMotion != null && mDisplayPerCategory != null && !FeatureOption.MTK_CLEARMOTION_SUPPORT) {
            mDisplayPerCategory.removePreference(mClearMotion);
        }

        mLockScreenStylePref = createPreference(TYPE_PREFERENCE, R.string.lock_screen_style_title, KEY_LOCK_SCREEN_STYLE, 
                                                  mDisplayPerCategory);
        mLockScreenStylePref.setOnPreferenceClickListener(this);
        
        mLockScreenNotifications = (CheckBoxPreference)createPreference(TYPE_CHECKBOX, 
                R.string.lock_screen_notifications_title, KEY_LOCK_SCREEN_NOTIFICATIONS, mDisplayPerCategory);
        mLockScreenNotifications.setSummary(R.string.lock_screen_notifications_summary);
        
        
        if (FeatureOption.MTK_THEMEMANAGER_APP) {
            mColorPref = createPreference(TYPE_PREFERENCE, R.string.mtk_thememanager_title, KEY_COLOR, 
                                          mDisplayPerCategory);
            mColorPref.setFragment("com.mediatek.thememanager.ThemeManager");
        }
        
        mWallpaperPref = createPreference(TYPE_PREFERENCE, R.string.wallpaper_settings_title, KEY_MTK_WALLPAPER, 
                                          mDisplayPerCategory);
        mWallpaperPref.setFragment("com.android.settings.WallpaperTypeSettings");

        mHdmiManager = IMtkHdmiManager.Stub
                .asInterface(ServiceManager
                        .getService(Context.MTK_HDMI_SERVICE));
        if (mHdmiManager != null) {
            mHDMISettings = createPreference(TYPE_PREFERENCE, R.string.hdmi_settings, KEY_HDMI_SETTINGS, 
                                            mDisplayDefCategory);
            mHDMISettings.setSummary(R.string.hdmi_settings_summary);
            mHDMISettings.setFragment("com.mediatek.hdmi.HDMISettings");
            try {
                if (mHdmiManager.getDisplayType() == 2) {
                    String hdmi = mContext.getString(R.string.hdmi_replace_hdmi);
                    String mhl = mContext.getString(R.string.hdmi_replace_mhl);
                    mHDMISettings.setTitle(mHDMISettings.getTitle().toString().replaceAll(hdmi, mhl));
                    mHDMISettings.setSummary(mHDMISettings.getSummary().toString().replaceAll(hdmi, mhl));
                }
            } catch (RemoteException e) {
                Xlog.d(TAG, "getDisplayType RemoteException");
            }
        }

        // add all google default preference to mDisplayDefCategory not include wallpaper
        int j = 0;
        for (int i = 0 ; i < screen.getPreferenceCount() - SUM_CATEGORY; i++) {
            Preference preference = screen.getPreference(i);
            preference.setOrder(j++);
            mDisplayDefCategory.addPreference(preference);
            // add hdmi  behind font_size
            if (KEY_FONT_SIZE.equals(preference.getKey())) {
                if (mHDMISettings != null) {
                    mHDMISettings.setOrder(j++);
                }
            }
        }
        // use for plugin and EM
        mScreenTimeoutPreference = mDisplayDefCategory.findPreference(KEY_SCREEN_TIMEOUT);
        mFontSizePref = (ListPreference)mDisplayDefCategory.findPreference(KEY_FONT_SIZE);
        
        //remove google default wallpaper, because it move to mDisplayPerCategory
        if (mDisplayDefCategory.findPreference(KEY_WALLPAPER) != null) {
            mDisplayDefCategory.removePreference(mDisplayDefCategory.findPreference(KEY_WALLPAPER));
        }
        Xlog.d(TAG, "Plugin called for adding the prefernce");
        mPlmnName = Utils.getStatusBarPlmnPlugin(mContext);
        mPlmnName.createCheckBox(mDisplayDefCategory,j);

        screen.removeAll();
        screen.addPreference(mDisplayPerCategory);
        screen.addPreference(mDisplayDefCategory);
        
        /// M: if wfd feature is unavailable, remove cast screen preference @{
        DisplayManager displayManager = (DisplayManager)mContext.getSystemService(
                Context.DISPLAY_SERVICE);
        WifiDisplayStatus status = displayManager.getWifiDisplayStatus();
        if (status.getFeatureState() == WifiDisplayStatus.FEATURE_STATE_UNAVAILABLE) {
            Xlog.d(TAG,"Wifi display feature is unavailable, remove cast screen pref");
            PreferenceScreen wfdPreferenceScreen = (PreferenceScreen)screen.findPreference(KEY_WIFI_DISPLAY);
            if(wfdPreferenceScreen != null) {
                Xlog.d(TAG, "Find the wfd preference");
                mDisplayDefCategory.removePreference(wfdPreferenceScreen);
            }
        }
        /// @}
    }
    
    public void onCreate(PreferenceScreen screen) {
        Xlog.d(TAG,"onCreate");
        mExt = Utils.getMiscPlugin(mContext); 
        initPreference(screen);               
        updateLockScreenStyle(); 
        mExt.setTimeoutPrefTitle(mScreenTimeoutPreference);
        // for solve a bug
        updateFontSize(mFontSizePref);
        
        //new WfdSettingsExt for MTk WFD feature
        if (FeatureOption.MTK_WFD_SUPPORT) {
            mWfdExt = new WfdSettingsExt(mContext);
        }
    }
    
    public void onResume() {
        Xlog.d(TAG,"onResume of DisplaySettings");   
        
        IntentFilter filter = new IntentFilter(Intent.ACTION_PACKAGE_ADDED);
        filter.addAction(Intent.ACTION_PACKAGE_REMOVED);
        filter.addDataScheme("package");
        mContext.registerReceiver(mPackageReceiver, filter);
        
        
        //  add display new feature
        ContentResolver cr = mContext.getContentResolver();
        if (mColorPref != null) {
            mColorPref.setSummary(ThemeManager.getThemeSummary(mColorPref.getContext())); 
        }
        mWallpaperPref.setSummary(parseString(Settings.System.getString(cr, Settings.System.CURRENT_WALLPAPER_NAME)));

        // add for lockScreen
        mLockScreenNotifications.setChecked(Settings.System.getInt(mContext.getContentResolver(),
                INCOMING_INDICATOR_ON_LOCKSCREEN, DEFAULT_LOCK_SCREEN_NOTIFICATIONS) == 1);
        updateLockScreenStyleSummary();

        // Register the receiver: Smart book plug in/out intent
        mContext.registerReceiver(mSmartBookPlugReceiver,
                new IntentFilter(Intent.ACTION_SMARTBOOK_PLUG));

        // add for clearMotion
        mContext.registerReceiver(mUpdateClearMotionStatusReceiver, new IntentFilter(ACTION_CLEARMOTION_DIMMED));
        updateClearMotionStatus();
    }
    
    
    public void onPause() {     
        mContext.unregisterReceiver(mPackageReceiver);
        // Unregister the receiver: Smart book plug in/out intent
        mContext.unregisterReceiver(mSmartBookPlugReceiver);

        // add for clearMotion
        mContext.unregisterReceiver(mUpdateClearMotionStatusReceiver);
    }
    
    public void removePreference(Preference preference) {
        if (mDisplayDefCategory != null && preference != null) {
            mDisplayDefCategory.removePreference(preference);  
        }
    }
    
    @Override
    public boolean onPreferenceClick(Preference preference) {
        if (preference == mLockScreenStylePref) {
            Intent intent = new Intent();
            ComponentName comName = new ComponentName("com.android.settings",
                                                          "com.mediatek.lockscreensettings.LockScreenStyleSettings");
            intent.setComponent(comName);
            mContext.startActivity(intent);
            
        } else if (preference == mLockScreenNotifications) {
            boolean value = mLockScreenNotifications.isChecked();
            Settings.System.putInt(mContext.getContentResolver(),
                                  INCOMING_INDICATOR_ON_LOCKSCREEN, value ? 1 : 0);
        } else if (preference == mClearMotion) {
            // add for clearMotion
            boolean value = mClearMotion.isChecked();
            SystemProperties.set(KEY_DISPLAY_CLEAR_MOTION, value ? "1" : "0");
        }

        return true;
    }
    
    // add for lockScreen 
    private void updateLockScreenStyle() {
        Intent intent = new Intent();
        ComponentName comName = new ComponentName(LOCK_SCREEN_STYLE_INTENT_PACKAGE, LOCK_SCREEN_STYLE_INTENT_NAME);
        intent.setComponent(comName);
        List<ResolveInfo> lockScreenStyleApps = mContext.getPackageManager()
                .queryIntentActivities(intent, 0);
        boolean hasPlugin = queryPluginKeyguardLayers();

        if (lockScreenStyleApps != null
                && lockScreenStyleApps.size() != 0 && hasPlugin) {
            Xlog.d(TAG, "lockScreenStyleApps.size()="
                    + lockScreenStyleApps.size());
            if (mDisplayPerCategory != null && mLockScreenStylePref != null) {
                mDisplayPerCategory.addPreference(mLockScreenStylePref);
            }
        } else {
            Xlog.d(TAG, "lock screen style query return null or size 0 ");
            // There is no lock screen style installed , remove the preference.
             if (mDisplayPerCategory != null && mLockScreenStylePref != null) {
                 mDisplayPerCategory.removePreference(mLockScreenStylePref);
             }
            return;
        }

        updateLockScreenStyleSummary();
 
    }
    
    /**
     * Get key guard layers from system, a key guard layer should implement IKeyguardLayer interface. Plugin app should make
     * sure the data is valid.
     */
    private boolean queryPluginKeyguardLayers() {
        boolean pluginLayers = false;
        KeyguardLayerInfo info = null;
        try {
            final PluginManager plugManager = PluginManager.<IKeyguardLayer> create(mContext, 
                    IKeyguardLayer.class.getName());
            final int pluginCount = plugManager.getPluginCount();
            Xlog.d(TAG, "getKeyguardLayers: pluginCount = " + pluginCount);
            if (pluginCount != 0) {
                Plugin<IKeyguardLayer> plugin;
                IKeyguardLayer keyguardLayer;
                for (int i = 0; i < pluginCount; i++) {
                    plugin = plugManager.getPlugin(i);
                    keyguardLayer = (IKeyguardLayer) plugin.createObject();
                    info = keyguardLayer.getKeyguardLayerInfo();
                    Xlog.d(TAG, "getKeyguardLayers: i = " + i + ",keyguardLayer = " + keyguardLayer + ",info = " + info);
                    if (info != null) {
                        pluginLayers  = true;
                        return pluginLayers;
                    }
                }
            }
        } catch (Exception e) {
            Xlog.e(TAG, "getPluginKeyguardLayers exception happens: e = " + e.getMessage());
            return false;
        }

        return pluginLayers;
    }
    
    private void updateLockScreenStyleSummary() {
        String lockScreenStyleSummary = parseString(Settings.System.getString(
                mContext.getContentResolver(), CURRENT_KEYGURAD_LAYER_KEY));
        if (lockScreenStyleSummary.equals("")) {
            Xlog.d(TAG, "lockScreenStyleSummary = " + lockScreenStyleSummary);
            mLockScreenStylePref.setSummary(R.string.default_name);
        } else {
            mLockScreenStylePref.setSummary(lockScreenStyleSummary);
        } 
        
    }
    
    // add display new featue
    public String parseString(final String decodeStr) {
        if (decodeStr == null) {
            Xlog.w(TAG, "parseString error as decodeStr is null");
            return mContext.getString(R.string.default_name);
    }
        String ret = decodeStr;
        String[] tokens = decodeStr.split(CONTACT_STRING);
        int tokenSize = tokens.length;
        if (tokenSize > PARSER_STRING_LENGTH_ONE) {
            PackageManager pm = mContext.getPackageManager();
            Resources resources;
            try {
                resources = pm.getResourcesForApplication(tokens[PARSER_STRING_LENGTH_ZERO]);
            } catch (PackageManager.NameNotFoundException e) {
                Xlog.w(TAG, "parseString can not find pakcage: " + tokens[PARSER_STRING_LENGTH_ZERO]);
                return ret;
            }
            int resId;
            try {
                resId = Integer.parseInt(tokens[PARSER_STRING_LENGTH_ONE]);
            } catch (NumberFormatException e) {
                Xlog.w(TAG, "Invalid format of propery string: " + tokens[PARSER_STRING_LENGTH_ONE]);
                return ret;
            }
            if (tokenSize == PARSER_STRING_LENGTH_TWO) {
                ret = resources.getString(resId);
            } else {
                ret = resources.getString(resId, tokens[PARSER_STRING_LENGTH_TWO]);
            }
        }

        Xlog.d(TAG, "parseString return string: " + ret);
        return ret;
    }
    
    /**
     *  Update font size from EM
     *  Add by mtk54043
     */    
    private void updateFontSize(ListPreference fontSizePreference) {
        Xlog.d(TAG, "update font size ");

        final CharSequence[] values = fontSizePreference.getEntryValues();

        float small = Settings.System.getFloat(mContext.getContentResolver(),
                Settings.System.FONT_SCALE_SMALL, -1);
        float large = Settings.System.getFloat(mContext.getContentResolver(),
                Settings.System.FONT_SCALE_LARGE, -1);
        float extraLarge = Settings.System.getFloat(mContext.getContentResolver(),
                Settings.System.FONT_SCALE_EXTRALARGE, -1);
        Xlog.d(TAG, "update font size small = " + small);
        Xlog.d(TAG, "update font size large = " + large);
        Xlog.d(TAG, "update font size extraLarge = " + extraLarge);        
        if (small != -1 || large != -1 || extraLarge != -1) {

            if (null != values[0] && small != -1) {
                values[0] = small + "";
                Xlog.d(TAG, "update font size : " + values[0]);
            }
            if (null != values[2] && large != -1) {
                values[2] = large + "";
                Xlog.d(TAG, "update font size : " + values[2]);
            }
            if (null != values[3] && extraLarge != -1) {
                values[3] = extraLarge + "";
                Xlog.d(TAG, "update font size : " + values[3]);
            }

            if (null != values) {
                fontSizePreference.setEntryValues(values);
            }

            mIsUpdateFont = true;
        }
    }
    
    public int floatToIndex(ListPreference fontSizePreference, float val) {
        Xlog.d(TAG, "floatToIndex enter val = " + val);
        int res = -1;
        if (mIsUpdateFont) { 
            final CharSequence[] indicesEntry = fontSizePreference.getEntryValues();
            Xlog.d(TAG, "current font size : " + val);
            for (int i = 0; i < indicesEntry.length; i++) {
                float thisVal = Float.parseFloat(indicesEntry[i].toString());
                if (val == thisVal) {
                    Xlog.d(TAG, "Select : " + i);
                    res = i;
                }
            }
            if (res == -1) {
                res = 1;
            }
        }
        
        Xlog.d(TAG, "floatToIndex, res = " + res);
        return res;
    }
    
    /*
     * registerForWfdSwicth
     * @return true: WfdExt exist; false: WfdExt not exist
     */   
    public boolean registerForWfdSwicth(Preference wfdPreference) {
        if (mWfdExt != null) {
            mWfdExt.registerForWfdSwicth(wfdPreference);
            return true;
        }
        return false;
    }
 
    /*
     * unregisterForWfdSwicth
     * @return true: WfdExt exist; false: WfdExt not exist
     */
    public boolean unregisterForWfdSwicth() {
        if (mWfdExt != null) {
            mWfdExt.unregisterForWfdSwicth();
            return true;
        }
        return false;
        
    }
  
    /*
     * updateWfdPreferenceSummary
     * @return true: WfdExt exist; false: WfdExt not exist
     */
    public boolean updateWfdPreferenceSummary(Preference wfdPreference) {
        if (mWfdExt != null) {
            mWfdExt.updateWfdPreferenceSummary(wfdPreference);
            return true;
        }
        return false;
    }

    // Smart book plug in/out receiver {@
    private BroadcastReceiver mSmartBookPlugReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context1, Intent intent) {
            Boolean isSmartBookPluggedIn = intent.getBooleanExtra(Intent.EXTRA_SMARTBOOK_PLUG_STATE, false);
            Xlog.d(TAG, "smartbook plug:" + isSmartBookPluggedIn);
            // if has smart book plug in, HDMI item should gone
            if (isSmartBookPluggedIn || mHdmiManager == null) {
                mDisplayDefCategory.removePreference(mHDMISettings);
            } else {
                mDisplayDefCategory.addPreference(mHDMISettings);
            }
        }
    };
    // @}

/**
     * add for clearMotion
     * clearMotionStyle clearMotion^TM
     */
    private void clearMotionStyle() {
        String title = mContext.getString(R.string.clear_motion_title);
        SpannableString spanText = new SpannableString(title);
        int strLen = spanText.length();
        spanText.setSpan(new SuperscriptSpan(), strLen - 2, strLen, Spannable.SPAN_INCLUSIVE_EXCLUSIVE);
        spanText.setSpan(new RelativeSizeSpan(0.6f), strLen - 2, strLen, Spannable.SPAN_INCLUSIVE_EXCLUSIVE);
        mClearMotion.setTitle(spanText);
    }
    
    /**
     *  add for clearMotion
     *  clearMotion and WFD/HDMI can't co-work
     */
    private void updateClearMotionStatus() {
        if (mClearMotion != null) {
            Xlog.d(TAG,"updateClearMotionStatus");
            mClearMotion.setChecked(SystemProperties.get(KEY_DISPLAY_CLEAR_MOTION, "0").equals("1"));
            mClearMotion.setEnabled(SystemProperties.get(KEY_DISPLAY_CLEAR_MOTION_DIMMED, "0").equals("0"));           
        }
    }

    /**
     * add for clearMotion
     */
    private BroadcastReceiver mUpdateClearMotionStatusReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context1, Intent intent) {
            Xlog.d(TAG,"mUpdateClearMotionStatusReceiver");
            updateClearMotionStatus();
        }
    };
}

