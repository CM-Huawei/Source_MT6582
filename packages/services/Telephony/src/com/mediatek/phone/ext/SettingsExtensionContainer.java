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

import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

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

import com.android.internal.telephony.Phone;
import com.mediatek.phone.PhoneLog;
import com.mediatek.telephony.SimInfoManager.SimInfoRecord;

public class SettingsExtensionContainer extends SettingsExtension {

    private static final String TAG = "SettingsExtensionContainer";

    private LinkedList<SettingsExtension> mSubExtensionList;

    /**
     * @param extension
     */
    public void add(SettingsExtension extension) {
        if (null == mSubExtensionList) {
            PhoneLog.d(TAG, "create sub extension list");
            mSubExtensionList = new LinkedList<SettingsExtension>();
        }

        PhoneLog.d(TAG, "add extension, extension is " + extension);
        mSubExtensionList.add(extension);
    }

    /**
     * @param extension SettingsExtension
     */
    public void remove(SettingsExtension extension) {
        if (null == mSubExtensionList) {
            PhoneLog.d(TAG, "remove extension, sub extension list is null, just return");
            return;
        }

        PhoneLog.d(TAG, "remove extension, extension is " + extension);
        mSubExtensionList.remove(extension);
    }

    /**
     * @param prefSet
     */
    public void customizeFeatureForOperator(PreferenceScreen prefSet) {
        if (null == mSubExtensionList) {
            PhoneLog.d(TAG, "customizeFeatureForOperator(), sub extension list is null, just return");
            return;
        }

        PhoneLog.d(TAG, "customizeFeatureForOperator(), prefSet is " + prefSet);
        Iterator<SettingsExtension> iterator = mSubExtensionList.iterator();
        while (iterator.hasNext()) {
            iterator.next().customizeFeatureForOperator(prefSet);
        }
    }

    /**
     * @param prefSet
     * @param plmnPreference
     */
    public void customizePLMNFeature(PreferenceScreen prefSet, Preference plmnPreference) {
        if (null == mSubExtensionList) {
            PhoneLog.d(TAG, "customizePLMNFeature(), sub extension list is null, just return");
            return;
        }

        PhoneLog.d(TAG, "customizePLMNFeature(), prefSet is " + prefSet + ", plmnPreference is " + plmnPreference);
        Iterator<SettingsExtension> iterator = mSubExtensionList.iterator();
        while (iterator.hasNext()) {
            iterator.next().customizePLMNFeature(prefSet, plmnPreference);
        }
    };

    /**
     * @param prefsc
     * @param buttonPreferredNetworkMode
     * @param buttonPreferredGSMOnly
     * @param buttonPreferredNetworkModeEx
     */
    public void removeNMMode(PreferenceScreen prefsc, Preference buttonPreferredNetworkMode,
            Preference buttonPreferredGSMOnly, Preference buttonPreferredNetworkModeEx) {
        if (null == mSubExtensionList) {
            PhoneLog.d(TAG, "removeNMMode(), sub extension list is null, just return");
            return;
        }

        PhoneLog.d(TAG, "removeNMMode(), prefsc is " + prefsc
                + ", buttonPreferredNetworkMode is " + buttonPreferredNetworkMode
                + ", buttonPreferredGSMOnly is" + buttonPreferredGSMOnly
                + ", buttonPreferredNetworkModeEx is " + buttonPreferredNetworkModeEx);
        Iterator<SettingsExtension> iterator = mSubExtensionList.iterator();
        while (iterator.hasNext()) {
            iterator.next().removeNMMode(prefsc, buttonPreferredNetworkMode,
                    buttonPreferredGSMOnly, buttonPreferredNetworkModeEx);
        }
    }

    /**
     * @param prefsc
     * @param isShowPlmn
     */
    public void removeNMOp(PreferenceScreen prefsc, boolean isShowPlmn) {
        if (null == mSubExtensionList) {
            PhoneLog.d(TAG, "removeNMOp(), sub extension list is null, just return");
            return;
        }

        PhoneLog.d(TAG, "removeNMOp(), prefsc is " + prefsc + ", isShowPlmn is " + isShowPlmn);
        Iterator<SettingsExtension> iterator = mSubExtensionList.iterator();
        while (iterator.hasNext()) {
            iterator.next().removeNMOp(prefsc, isShowPlmn);
        }
    }

    /**
     * For change feature ALPS00791254
     * @param prefsc
     * @param networkMode
     */
    public void removeNMOpFor3GSwitch(PreferenceScreen prefsc, Preference networkMode) {
        if (null == mSubExtensionList) {
            PhoneLog.d(TAG, "removeNMOpFor3GSwitch(), sub extension list is null, just return");
            return;
        }

        PhoneLog.d(TAG, "removeNMOpFor3GSwitch(), prefsc is " + prefsc + ", networkMode is " + networkMode);
        Iterator<SettingsExtension> iterator = mSubExtensionList.iterator();
        while (iterator.hasNext()) {
            iterator.next().removeNMOpFor3GSwitch(prefsc, networkMode);
        }
    }

    /**
     * @param phone
     * @param simList
     * @param targetClass
     */
    public void removeNMOpForMultiSim(Phone phone, List<SimInfoRecord> simList, String targetClass) {
        if (null == mSubExtensionList) {
            PhoneLog.d(TAG, "removeNMOpForMultiSim(), sub extension list is null, just return");
            return;
        }

        PhoneLog.d(TAG, "removeNMOpForMultiSim(), phone is " + phone + ", simList is "
                + simList + ", targetClass is " + targetClass);
        Iterator<SettingsExtension> iterator = mSubExtensionList.iterator();
        while (iterator.hasNext()) {
            iterator.next().removeNMOpForMultiSim(phone, simList, targetClass);
        }
    }

    /**
     * For change feature ALPS00791254
     * @return
     */
    public boolean isRemoveRadioOffFor3GSwitchFlag() {
        if (null == mSubExtensionList) {
            PhoneLog.d(TAG, "isRemoveRadioOffFor3GSwitchFlag(), sub extension list is null, just return");
            return false;
        }

        Iterator<SettingsExtension> iterator = mSubExtensionList.iterator();
        while (iterator.hasNext()) {
            if (iterator.next().isRemoveRadioOffFor3GSwitchFlag()) {
                PhoneLog.d(TAG, "isRemoveRadioOffFor3GSwitchFlag(), return true");
                return true;
            }
        }
        PhoneLog.d(TAG, "isRemoveRadioOffFor3GSwitchFlag(), return false");
        return false;
    }

    /**
     * @param dataEnable
     * @param activity
     * @return
     */
    public void dataEnableReminder(boolean isDataEnabled, boolean isRoamingEnabled,
            PreferenceActivity activity) {
        if (null == mSubExtensionList) {
            PhoneLog.d(TAG, "dataEnableReminder(), sub extension list is null, just return");
            return;
        }

        PhoneLog.d(TAG, "dataEnableReminder(), isDataEnabled is " + isDataEnabled
                + ", isRoamingEnabled is " + isRoamingEnabled + ", activity is " + activity);
        Iterator<SettingsExtension> iterator = mSubExtensionList.iterator();
        while (iterator.hasNext()) {
            iterator.next().dataEnableReminder(isDataEnabled, isRoamingEnabled, activity);
        }
    }

    /**
     * @param buttonDataRoam data roaming CheckBoxPreference
     * @param isEnabled true for enable Default not doing anything
     */
    public void disableDataRoaming(CheckBoxPreference buttonDataRoam, boolean isEnabled) {
        if (null == mSubExtensionList) {
            PhoneLog.d(TAG, "disableDataRoaming(), sub extension list is null, just return");
            return;
        }

        PhoneLog.d(TAG, "disableDataRoaming(), isEnabled is " + isEnabled);
        Iterator<SettingsExtension> iterator = mSubExtensionList.iterator();
        while (iterator.hasNext()) {
            iterator.next().disableDataRoaming(buttonDataRoam, isEnabled);
        }
    }

    /**
     * @param context Context
     * @param res string id
     * @return the summary
     */
    public String getRoamingMessage(Context context, int res) {
        String defMessage = context.getString(res);
        if (null == mSubExtensionList) {
            PhoneLog.d(TAG, "disableDataRoaming(), sub extension list is null, return default");
            return defMessage;
        }

        Iterator<SettingsExtension> iterator = mSubExtensionList.iterator();
        while (iterator.hasNext()) {
            String msg = iterator.next().getRoamingMessage(context, res);
            if (!defMessage.equals(msg)) {
                PhoneLog.d(TAG, "getRoamingMessage(), plugin return " + msg);
                return msg;
            }
        }
        PhoneLog.d(TAG, "getRoamingMessage(), return " + defMessage);
        return defMessage;
    }

    public void setRoamingSummary(CheckBoxPreference f, int resIdOn, int resIdOff) {
        if (null == mSubExtensionList) {
            PhoneLog.d(TAG, "setRoamingSummary(), sub extension list is null, just return");
            return;
        }

        PhoneLog.d(TAG, "setRoamingSummary(), resIdOn is " + resIdOn + ", resIdOff is " + resIdOff);
        Iterator<SettingsExtension> iterator = mSubExtensionList.iterator();
        while (iterator.hasNext()) {
            iterator.next().setRoamingSummary(f, resIdOn, resIdOff);
        }
    }

    public boolean needCustomizeNetworkSelection() {
        if (null == mSubExtensionList) {
            PhoneLog.d(TAG, "needCustomizeNetworkSelection(), sub extension list is null, return false");
            return false;
        }

        Iterator<SettingsExtension> iterator = mSubExtensionList.iterator();
        while (iterator.hasNext()) {
            if (iterator.next().needCustomizeNetworkSelection()) {
                PhoneLog.d(TAG, "needCustomizeNetworkSelection(), plug-in return true");
                return true;
            }
        }
        PhoneLog.d(TAG, "needCustomizeNetworkSelection() return false");
        return false;
    }

    public void loadManualNetworkSelectionPreference(PreferenceActivity prefActivity,
            PreferenceScreen preferenceScreen) {
        if (null == mSubExtensionList) {
            PhoneLog.d(TAG, "loadManualNetworkSelectionPreference(), sub extension list is null, just return");
            return;
        }

        PhoneLog.d(TAG, "loadManualNetworkSelectionPreference(), prefActivity is "
                + prefActivity + ", preferenceScreen is " + preferenceScreen);
        Iterator<SettingsExtension> iterator = mSubExtensionList.iterator();
        while (iterator.hasNext()) {
            iterator.next().loadManualNetworkSelectionPreference(prefActivity, preferenceScreen);
        }
    }

    public String replaceSimToSimUim(String simString) {
        if (null == mSubExtensionList) {
            PhoneLog.d(TAG, "replaceSimToSimUim(), sub extension list is null, return " + simString);
            return simString;
        }

        Iterator<SettingsExtension> iterator = mSubExtensionList.iterator();
        while (iterator.hasNext()) {
            String plugSimString = iterator.next().replaceSimToSimUim(simString);
            if (plugSimString != null && !plugSimString.equals(simString)) {
                PhoneLog.d(TAG, "replaceSimToSimUim(), plugSimString is " + plugSimString);
                return plugSimString;
            }
        }
        PhoneLog.d(TAG, "replaceSimToSimUim(), return " + simString);
        return simString;
    }

    public String replaceSimToUim(String simString) {
        if (null == mSubExtensionList) {
            PhoneLog.d(TAG, "replaceSimToUim(), sub extension list is null, return " + simString);
            return simString;
        }

        Iterator<SettingsExtension> iterator = mSubExtensionList.iterator();
        while (iterator.hasNext()) {
            String plugSimString = iterator.next().replaceSimToUim(simString);
            if (plugSimString != null && !plugSimString.equals(simString)) {
                PhoneLog.d(TAG, "replaceSimToUim(), plugSimString is " + plugSimString);
                return plugSimString;
            }
        }
        PhoneLog.d(TAG, "replaceSimToUim(), return " + simString);
        return simString;
    }

    public String replaceSim1ToUim(String simString) {
        if (null == mSubExtensionList) {
            PhoneLog.d(TAG, "replaceSim1ToUim(), sub extension list is null, return " + simString);
            return simString;
        }

        Iterator<SettingsExtension> iterator = mSubExtensionList.iterator();
        while (iterator.hasNext()) {
            String plugSimString = iterator.next().replaceSim1ToUim(simString);
            if (plugSimString != null && !plugSimString.equals(simString)) {
                PhoneLog.d(TAG, "replaceSim1ToUim(), plugSimString is " + plugSimString);
                return plugSimString;
            }
        }
        PhoneLog.d(TAG, "replaceSim1ToUim(), return " + simString);
        return simString;
    }

    public String replaceSimToCard(String simString) {
        if (null == mSubExtensionList) {
            PhoneLog.d(TAG, "replaceSimToCard(), sub extension list is null, return " + simString);
            return simString;
        }

        Iterator<SettingsExtension> iterator = mSubExtensionList.iterator();
        while (iterator.hasNext()) {
            String plugSimString = iterator.next().replaceSimToCard(simString);
            if (plugSimString != null && !plugSimString.equals(simString)) {
                PhoneLog.d(TAG, "replaceSimToCard(), plugSimString is " + plugSimString);
                return plugSimString;
            }
        }
        PhoneLog.d(TAG, "replaceSimToCard(), return " + simString);
        return simString;
    }

    public String replaceSimBySlot(String simString, int slotId) {
        if (null == mSubExtensionList) {
            PhoneLog.d(TAG, "replaceSimBySlot(), sub extension list is null, return " + simString);
            return simString;
        }

        Iterator<SettingsExtension> iterator = mSubExtensionList.iterator();
        while (iterator.hasNext()) {
            String plugSimString = iterator.next().replaceSimBySlot(simString, slotId);
            if (plugSimString != null && !plugSimString.equals(simString)) {
                PhoneLog.d(TAG, "replaceSimBySlot(), plugSimString is " + plugSimString);
                return plugSimString;
            }
        }
        PhoneLog.d(TAG, "replaceSimBySlot(), return " + simString + ", slotId " + slotId);
        return simString;
    }

    public void switchPref(Preference manuSelect, Preference autoSelect) {
        if (null == mSubExtensionList) {
            PhoneLog.d(TAG, "switchPref(), sub extension list is null, just return");
            return;
        }
        PhoneLog.d(TAG, "switchPref(), manuSelect is " + manuSelect + ", autoSelect is " + autoSelect);
        Iterator<SettingsExtension> iterator = mSubExtensionList.iterator();
        while (iterator.hasNext()) {
            iterator.next().switchPref(manuSelect, autoSelect);
        }
    }

    public String getManualSelectDialogMsg(String defaultMsg) {
        if (null == mSubExtensionList) {
            PhoneLog.d(TAG, "getManualSelectDialogMsg(), sub extension list is null, return " + defaultMsg);
            return defaultMsg;
        }

        Iterator<SettingsExtension> iterator = mSubExtensionList.iterator();
        while (iterator.hasNext()) {
            String pluginMsg = iterator.next().getManualSelectDialogMsg(defaultMsg);
            if (pluginMsg != null && !pluginMsg.equals(defaultMsg)) {
                PhoneLog.d(TAG, "getManualSelectDialogMsg(), pluginMsg is " + pluginMsg);
                return pluginMsg;
            }
        }
        PhoneLog.d(TAG, "getManualSelectDialogMsg(), return default " + defaultMsg);
        return defaultMsg;
    }

    /**
     * CT customized NetworkSettings Activity, need remote binder for IPC. Used
     * in NetworkQueryService.java
     *
     * @return True if need to return remote binder, false will return local
     *         binder only for inner process
     */
    public boolean shouldPublicRemoteBinder() {
        if (null == mSubExtensionList) {
            PhoneLog.d(TAG, "shouldPublicRemoteBinder(), sub extension list is null, return false");
            return false;
        }

        Iterator<SettingsExtension> iterator = mSubExtensionList.iterator();
        while (iterator.hasNext()) {
            if (iterator.next().shouldPublicRemoteBinder()) {
                PhoneLog.d(TAG, "shouldPublicRemoteBinder(), plugin return true");
                return true;
            }
        }
        PhoneLog.d(TAG, "shouldPublicRemoteBinder(), return false");
        return false;
    }

    /**
     * CT spc, add APN and ManualNetwork in MobileNetworkSetting CT spc, delete
     * buttonPreferredNetworkModeEx, preference3GSwitch, plmnPreference
     *
     * @param prefActivity
     * @param preferenceScreen
     * @param buttonPreferredNetworkModeEx: witch can delete in CT feture
     * @param mPreference3GSwitch: witch can delete in CT feture
     * @param plmnPreference: witch can delete in CT feture
     */
    public boolean reloadPreference(PreferenceActivity prefActivity,
            PreferenceScreen preferenceScreen,
            Preference buttonPreferredNetworkModeEx, Preference preference3GSwitch,
            Preference plmnPreference) {
        if (null == mSubExtensionList) {
            PhoneLog.d(TAG, "reloadPreference(), sub extension list is null, return false");
            return false;
        }

        Iterator<SettingsExtension> iterator = mSubExtensionList.iterator();
        while (iterator.hasNext()) {
            if (iterator.next().reloadPreference(prefActivity, preferenceScreen,
                    buttonPreferredNetworkModeEx, preference3GSwitch, plmnPreference)) {
                PhoneLog.d(TAG, "reloadPreference(), plugin return true");
                return true;
            }
        }
        PhoneLog.d(TAG, "reloadPreference() defalut false");
        return false;
    }

    /**
     * for Operator plugin, add APN and ManualNetwork in MobileNetworkSetting CT spc, click
     * the APN or ManualNetwork
     *
     * @param preferenceScreen
     * @param preference
     */
    public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen, Preference preference) {
        if (null == mSubExtensionList) {
            PhoneLog.d(TAG, "onPreferenceTreeClick(), sub extension list is null, return false");
            return false;
        }

        Iterator<SettingsExtension> iterator = mSubExtensionList.iterator();
        while (iterator.hasNext()) {
            if (iterator.next().onPreferenceTreeClick(preferenceScreen, preference)) {
                PhoneLog.d(TAG, "onPreferenceTreeClick(), plugin return true");
                return true;
            }
        }
        PhoneLog.d(TAG, "onPreferenceTreeClick() defalut null");
        return false;
    }

    /**
     * @deprecated
     * CT spc, add APN and ManualNetwork in MobileNetworkSetting CT spc, click
     * the APN or ManualNetwork
     *
     * @param preferenceScreen
     * @param preference
     * @param slotId
     */
    public Intent onPreferenceTreeClick(PreferenceScreen preferenceScreen, Preference preference, int slotId) {
        if (null == mSubExtensionList) {
            PhoneLog.d(TAG, "onPreferenceTreeClick(), sub extension list is null, return false");
            return null;
        }

        Iterator<SettingsExtension> iterator = mSubExtensionList.iterator();
        while (iterator.hasNext()) {
            Intent pluginIntent = iterator.next().onPreferenceTreeClick(preferenceScreen, preference, slotId);
            if (pluginIntent != null) {
                PhoneLog.d(TAG, "onPreferenceTreeClick(), plugin return " + pluginIntent);
                return pluginIntent;
            }
        }
        PhoneLog.d(TAG, "onPreferenceTreeClick() defalut null");
        return null;
    }

    /**
     * CT spc, add APN and ManualNetwork in MobileNetworkSetting CT spc,
     * ManualNetwork enable if one sim can use
     *
     * @param prefActivity
     * @param phone : can judge sim can use or not
     * @param airplaneModeEnabled: true is Airplane mode
     */
    public void disableNetworkSelectionPrefs(PreferenceActivity prefActivity, Phone phone,
            boolean airplaneModeEnabled) {
        if (null == mSubExtensionList) {
            PhoneLog.d(TAG, "disableNetworkSelectionPrefs(), sub extension list is null, just return");
            return;
        }

        PhoneLog.d(TAG, "disableNetworkSelectionPrefs()");
        Iterator<SettingsExtension> iterator = mSubExtensionList.iterator();
        while (iterator.hasNext()) {
            iterator.next().disableNetworkSelectionPrefs(prefActivity, phone, airplaneModeEnabled);
        }
    }

    public Dialog onCreateAlertDialog(int dialogId, final Activity activity, Handler timeoutHandler) {
        if (null == mSubExtensionList) {
            PhoneLog.d(TAG, "onCreateAlertDialog(), sub extension list is null, return null");
            return null;
        }

        Iterator<SettingsExtension> iterator = mSubExtensionList.iterator();
        while (iterator.hasNext()) {
            Dialog pluginDialog = iterator.next().onCreateAlertDialog(dialogId, activity, timeoutHandler);
            if (pluginDialog != null) {
                PhoneLog.d(TAG, "onCreateAlertDialog(), return plugin dialog " + pluginDialog);
                return pluginDialog;
            }
        }
        PhoneLog.d(TAG, "onCreateAlertDialog(), return null");
        return null;
    }
}
