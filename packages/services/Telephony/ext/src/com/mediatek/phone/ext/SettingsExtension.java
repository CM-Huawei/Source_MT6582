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

package com.mediatek.phone.ext;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.os.Handler;
import android.preference.CheckBoxPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceScreen;
import android.util.Log;

import com.android.internal.telephony.Phone;
import com.mediatek.telephony.SimInfoManager.SimInfoRecord;

import java.util.List;

public class SettingsExtension {

    private static final String LOG_TAG = "NetworkSettings";
    public static final String BUTTON_NETWORK_MODE_KEY = "gsm_umts_preferred_network_mode_key";
    public static final String BUTTON_PLMN_LIST = "button_plmn_key";
    public static final String BUTTON_NETWORK_MODE_EX_KEY = "button_network_mode_ex_key";
    public static final String BUTTON_PREFERED_NETWORK_MODE = "preferred_network_mode_key";
    public static final String BUTTON_2G_ONLY = "button_prefer_2g_key";

    /**
     *
     * @param prefSet
     */
    public void customizeFeatureForOperator(PreferenceScreen prefSet) {
    }

    /**
     *
     * @param prefSet
     * @param mPLMNPreference
     */
    public void customizePLMNFeature(PreferenceScreen prefSet, Preference plmnPreference) {
    };

    /**
     *
     * @param prefsc
     * @param buttonPreferredNetworkMode 
     * @param buttonPreferredGSMOnly 
     * @param buttonPreferredNetworkModeEx 
     */
    public void removeNMMode(PreferenceScreen prefsc, Preference buttonPreferredNetworkMode,
            Preference buttonPreferredGSMOnly, Preference buttonPreferredNetworkModeEx) {
    }

    /**
     *
     * @param prefsc
     * @param isShowPlmn
     */
    public void removeNMOp(PreferenceScreen prefsc, boolean isShowPlmn) {
    }

    /**
     *
     * For change feature ALPS00791254
     * @param prefsc
     * @param networkMode
     */
    public void removeNMOpFor3GSwitch(PreferenceScreen prefsc, Preference networkMode) {
    }

    /**
     *
     * @param phone
     * @param simList
     * @param targetClass
     */
    public void removeNMOpForMultiSim(Phone phone, List<SimInfoRecord> simList, String targetClass) {
    }

    /**
     *
     * for change feature ALPS00791254
     * add "remove 3g switch off radio" funtion
     */
    public boolean isRemoveRadioOffFor3GSwitchFlag() {
        return false;
    }

    /**
     *
     * @param dataEnable
     * @param activity
     * @return
     */
    public void dataEnableReminder(boolean isDataEnabled, boolean isRoamingEnabled,
            PreferenceActivity activity) {
        ConnectivityManager connService = (ConnectivityManager) activity.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (connService == null) {
            Log.d(LOG_TAG,"mConnService is null");
            return;
        }
        connService.setMobileDataEnabled(isDataEnabled);
    }

    public void log(String msg) {
        Log.d(LOG_TAG, msg);
    }

    /**
     *
     * @param buttonDataRoam data roaming checkbox pref
     * @param isEnabled true for enable
     * Default not doing anything
     */
    public void disableDataRoaming(CheckBoxPreference buttonDataRoam,boolean isEnabled) {
    }

    /**
     *
     * @param context
     *            Context
     * @param res
     *            string id
     * @return the summary
     */
    public String getRoamingMessage(Context context, int res) {
        String message = context.getString(res);
        log("Default getRoamingMessage with message = " + message);
        return message;
    }

    public void setRoamingSummary(CheckBoxPreference f, int resIdOn,
            int resIdOff) {
        f.setSummaryOn(f.getContext().getString(resIdOn));
        f.setSummaryOff(f.getContext().getString(resIdOff));
    }

    /**
     *
     * @return
     */
    public boolean needCustomizeNetworkSelection() {
        Log.d(LOG_TAG, "isNeedCustomize() default");
        return false;
    }
    
    /**
     *
     * @return
     */
    public void loadManualNetworkSelectionPreference(PreferenceActivity prefActivity,
        PreferenceScreen preferenceScreen) {
    }

    public String replaceSimToSimUim(String simString) {
        return simString;
    }

    public String replaceSimToUim(String simString) {
        return simString;
    }

    public String replaceSim1ToUim(String simString) {
        return simString;
    }

    public String replaceSimToCard(String simString) {
        return simString;
    }

    public String replaceSimBySlot(String simString, int slotId) {
        return simString;
    }

    public void switchPref(Preference manuSelect,Preference autoSelect) {
    }

    public String getManualSelectDialogMsg(String defaultMsg) {
        return defaultMsg;
    }

    /**
     * CT customized NetworkSettings Activity is localed in OP09Plugin.apk, need remote binder
     * for IPC.
     * Used in NetworkQueryService.java
     *
     * @return True if need to return remote binder, false will return local binder only for
     * inner process
     */
    public boolean shouldPublicRemoteBinder() {
        return false;
    }

    /**
     * CT spc, add APN and ManualNetwork in MobileNetworkSetting
     * CT spc, delete buttonPreferredNetworkModeEx, preference3GSwitch, plmnPreference 
     * @param prefActivity
     * @param preferenceScreen
     * @param buttonPreferredNetworkModeEx: witch can delete in CT feture
     * @param mPreference3GSwitch: witch can delete in CT feture
     * @param plmnPreference: witch can delete in CT feture
     */
    public boolean reloadPreference(PreferenceActivity prefActivity,
            PreferenceScreen preferenceScreen,
            Preference buttonPreferredNetworkModeEx,
            Preference preference3GSwitch, Preference plmnPreference) {
        return false;
    }

    /**
     * for Operator plugin, add APN and ManualNetwork in MobileNetworkSetting,
     * click the APN or ManualNetwork
     * @param preferenceScreen
     * @param preference
     */
    public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen, Preference preference) {
        return false;
    }

    /**
     * @deprecated
     * CT spc, add APN and ManualNetwork in MobileNetworkSetting
     * CT spc, click the APN or ManualNetwork
     * @param preferenceScreen
     * @param preference
     * @param slotId
     */
    public Intent onPreferenceTreeClick(PreferenceScreen preferenceScreen, Preference preference, int slotId) {
        return null;
    }

    /**
     * CT spc, add APN and ManualNetwork in MobileNetworkSetting
     * CT spc, ManualNetwork enable if one sim can use
     * @param prefActivity
     * @param phone : can judge sim can use or not
     * @param airplaneModeEnabled: true is Airplane mode
     */
    public void disableNetworkSelectionPrefs(PreferenceActivity prefActivity, Phone phone, boolean airplaneModeEnabled){
    }

    public Dialog onCreateAlertDialog(int dialogId, final Activity activity, Handler timeoutHandler) {
        return null;
    }
}
