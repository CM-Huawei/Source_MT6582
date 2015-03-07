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

import com.android.internal.telephony.IccCardConstants;
import com.android.internal.telephony.PhoneConstants;
import com.mediatek.common.featureoption.FeatureOption;
import com.mediatek.xlog.Xlog;
import android.os.IBinder;
import android.os.SystemProperties;
import android.os.ServiceManager;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.media.RemoteControlClient;
import android.provider.Settings;
import com.mediatek.telephony.SimInfoManager;
import com.mediatek.common.dm.DmAgent;
import android.util.Log;


public class KeyguardUtils {
    private static final String TAG = "KeyguardUtils";

    ///M: The default value of the remaining puk count
    private static final int GET_SIM_RETRY_EMPTY = -1;

    public static final boolean isGemini() {
        return FeatureOption.MTK_GEMINI_SUPPORT;
    }
    
    public static final boolean isMediatekVT3G324MSupport() {
        return FeatureOption.MTK_VT3G324M_SUPPORT;
    }
    
    public static final boolean isMediatekGemini3GSwitchSupport() {
        return FeatureOption.MTK_GEMINI_3G_SWITCH;
    }

    public static final boolean isMediatekLCASupport() {
        return FeatureOption.MTK_LCA_RAM_OPTIMIZE;
    }

    public static final boolean isTransparentBarSupport() {
        return FeatureOption.MTK_TRANSPARENT_BAR_SUPPORT;   
    }

    public static final void xlogD(final String tag, final String logs) {
        Xlog.d(tag, logs);
    }
    
    public static final void xlogI(final String tag, final String logs) {
        Xlog.i(tag, logs);
    }
    
    public static final void xlogE(final String tag, final String logs) {
        Xlog.e(tag, logs);
    }
    
    public static final void xlogW(final String tag, final String logs) {
        Xlog.w(tag, logs);
    }
    
    public static final boolean isTablet() {
        return ("tablet".equals(SystemProperties.get("ro.build.characteristics")));
    }

    /// M: Support GeminiPlus
    public static int getMaxSimId() {
        if (isGemini()) {
            if(PhoneConstants.GEMINI_SIM_NUM == 3) {
                return PhoneConstants.GEMINI_SIM_3;
            } else if(PhoneConstants.GEMINI_SIM_NUM == 4) {
                return PhoneConstants.GEMINI_SIM_4;
            } else {
               return PhoneConstants.GEMINI_SIM_2;
            }
        } else {
            return PhoneConstants.GEMINI_SIM_1;
        }
    }

    public static int getNumOfSim() {
        if (isGemini()) {
            return PhoneConstants.GEMINI_SIM_NUM;
        } else {
            return 1;
        }
    } 

    public static boolean isValidSimId(int mSimId) {
        if((PhoneConstants.GEMINI_SIM_1 <= mSimId) && (mSimId <= getMaxSimId())) {
            return true;
        } else {
            return false;
        }
    }
    
    public static int getNavBarHeight(Context context) {
        final boolean hasNavBar = context.getResources().getBoolean(
                                      com.android.internal.R.bool.config_showNavigationBar);
        
        final int navBarHeight = hasNavBar ? context.getResources().getDimensionPixelSize(
                                      com.android.internal.R.dimen.navigation_bar_height) : 0;
                                      
        return navBarHeight;
    }
    
    public static int getStatusBarHeight(Context context) {
        return context.getResources().getDimensionPixelSize(com.android.internal.R.dimen.status_bar_height);
    }

    /// M: to check current resolution res support rotation or not    
    public static boolean shouldEnableScreenRotation(Context context) {
        ///M: when is alarm boot,disable rotation @{
        if (PowerOffAlarmManager.isAlarmBoot()) {
            return false;
        } 
        ///@}
        return SystemProperties.getBoolean("lockscreen.rot_override",false)
                || context.getResources().getBoolean(R.bool.config_enableLockScreenRotation);
    }

    public static final boolean isMusicPlaying(int playbackState) {
        // This should agree with the list in AudioService.isPlaystateActive()
        switch (playbackState) {
            case RemoteControlClient.PLAYSTATE_PLAYING:
            case RemoteControlClient.PLAYSTATE_BUFFERING:
            case RemoteControlClient.PLAYSTATE_FAST_FORWARDING:
            case RemoteControlClient.PLAYSTATE_REWINDING:
            case RemoteControlClient.PLAYSTATE_SKIPPING_BACKWARDS:
            case RemoteControlClient.PLAYSTATE_SKIPPING_FORWARDS:
                return true;
            default:
                return false;
        }
    }

    /**
     *  M:Get the remaining puk count of the sim card with the simId .@{ 
     * @param simId
     */
    public static int getRetryPukCount(final int simId) {
        /// M: Support GeminiPlus
        if (simId == PhoneConstants.GEMINI_SIM_4) {
            return SystemProperties.getInt("gsm.sim.retry.puk1.4",GET_SIM_RETRY_EMPTY);
        } else if (simId == PhoneConstants.GEMINI_SIM_3) {
            return SystemProperties.getInt("gsm.sim.retry.puk1.3",GET_SIM_RETRY_EMPTY);
        } else if (simId == PhoneConstants.GEMINI_SIM_2) {
            return SystemProperties.getInt("gsm.sim.retry.puk1.2",GET_SIM_RETRY_EMPTY);
        } else {
            return SystemProperties.getInt("gsm.sim.retry.puk1",GET_SIM_RETRY_EMPTY);
        }
    }

    public static String getOptrNameByIdx(Context context, long simIdx) {
        if (simIdx > 0) {
            KeyguardUtils.xlogD(TAG, "getOptrNameByIdx, xxsimId=" + simIdx);
            SimInfoManager.SimInfoRecord info = SimInfoManager.getSimInfoById(context, simIdx);
            if (null == info) {
                KeyguardUtils.xlogD(TAG, "getOptrNameByIdx, return null");
               return null;
            } else {
                KeyguardUtils.xlogD(TAG, "info=" + info.mDisplayName);
               return info.mDisplayName; 
            }
        } else if (Settings.System.DEFAULT_SIM_SETTING_ALWAYS_ASK == simIdx) {
            return context.getResources().getString(R.string.keyguard_alwaysask);
        } else if (Settings.System.VOICE_CALL_SIM_SETTING_INTERNET == simIdx) {
            return context.getResources().getString(R.string.keyguard_internal_call);
        } else if (Settings.System.GPRS_CONNECTION_SIM_SETTING_NEVER == simIdx) {
            return context.getResources().getString(R.string.keyguard_data_none);
        } else if (Settings.System.SMS_SIM_SETTING_AUTO == simIdx) {
            return context.getResources().getString(R.string.keyguard_sim_auto);
        } else {
            return context.getResources().getString(R.string.keyguard_not_set);
        }
    }

    public static String getOptrNameBySlot(Context context, int slot) {
        if (slot >= 0) {
            KeyguardUtils.xlogD(TAG, "getOptrNameBySlot, xxSlot=" + slot);
            SimInfoManager.SimInfoRecord info = SimInfoManager.getSimInfoBySlot(context, slot);
            if (null == info) {
                KeyguardUtils.xlogD(TAG, "getOptrNameBySlot, return null");
               return null;
            } else {
                KeyguardUtils.xlogD(TAG, "info=" + info.mDisplayName);
               return info.mDisplayName; 
            }
        } else {
            throw new IndexOutOfBoundsException();
        }
    }
}
