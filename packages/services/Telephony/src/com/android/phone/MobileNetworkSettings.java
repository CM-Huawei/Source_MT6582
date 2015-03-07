/* Copyright Statement:
 *
 * This software/firmware and related documentation ("MediaTek Software") are
 * protected under relevant copyright laws. The information contained herein
 * is confidential and proprietary to MediaTek Inc. and/or its licensors.
 * Without the prior written permission of MediaTek inc. and/or its licensors,
 * any reproduction, modification, use or disclosure of MediaTek Software,
 * and information contained herein, in whole or in part, shall be strictly prohibited.
 */
/* MediaTek Inc. (C) 2010. All rights reserved.
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

/*
 * Copyright (C) 2006 The Android Open Source Project
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

package com.android.phone;

import android.app.ActionBar;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.IntentFilter;
import android.database.ContentObserver;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.AsyncResult;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.SystemProperties;
import android.preference.CheckBoxPreference;
import android.preference.DialogPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceFragment;
import android.preference.PreferenceScreen;
import android.provider.Settings;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Log;
import android.view.MenuItem;

import com.android.internal.telephony.ITelephony;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.TelephonyIntents;
import com.android.internal.telephony.TelephonyProperties;

import com.mediatek.common.featureoption.FeatureOption;
import com.mediatek.gemini.simui.SimSelectDialogPreference;
import com.mediatek.phone.GeminiConstants;
import com.mediatek.phone.PhoneFeatureConstants;
import com.mediatek.phone.PhoneLog;
import com.mediatek.phone.ext.ExtensionManager;
import com.mediatek.phone.ext.IDataConnection;
import com.mediatek.phone.ext.SettingsExtension;
import com.mediatek.phone.gemini.GeminiUtils;
import com.mediatek.phone.wrapper.ITelephonyWrapper;
import com.mediatek.phone.wrapper.PhoneWrapper;
import com.mediatek.phone.wrapper.TelephonyManagerWrapper;
import com.mediatek.settings.MultipleSimActivity;
import com.mediatek.settings.NetWorkHandler;
import com.mediatek.settings.PreCheckForRunning;
import com.mediatek.telephony.SimInfoManager;
import com.mediatek.telephony.SimInfoManager.SimInfoRecord;
import com.mediatek.telephony.TelephonyManagerEx;


import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
/**
 * "Mobile network settings" screen.  This preference screen lets you
 * enable/disable mobile data, and control data roaming and other
 * network-specific mobile data features.  It's used on non-voice-capable
 * tablets as well as regular phone devices.
 *
 * Note that this PreferenceActivity is part of the phone app, even though
 * you reach it from the "Wireless & Networks" section of the main
 * Settings app.  It's not part of the "Call settings" hierarchy that's
 * available from the Phone app (see CallFeaturesSetting for that.)
 */
public class MobileNetworkSettings extends PreferenceActivity
        implements DialogInterface.OnClickListener, DialogInterface.OnDismissListener {

    // debug data
    private static final String LOG_TAG = "NetworkSettings";
    private static final boolean DBG = true;
    public static final int REQUEST_CODE_EXIT_ECM = 17;

    //String keys for preference lookup
    private static final String BUTTON_DATA_ENABLED_KEY = "button_data_enabled_key";
    private static final String BUTTON_PREFERED_NETWORK_MODE = "preferred_network_mode_key";
    private static final String BUTTON_ROAMING_KEY = "button_roaming_key";
    private static final String BUTTON_CDMA_LTE_DATA_SERVICE_KEY = "cdma_lte_data_service_key";
    private static final String BUTTON_ENABLED_NETWORKS_KEY = "enabled_networks_key";
    private static final String BUTTON_CARRIER_SETTINGS_KEY = "carrier_settings_key";

    static final int preferredNetworkMode = Phone.PREFERRED_NT_MODE;

    //Information about logical "up" Activity
    private static final String UP_ACTIVITY_PACKAGE = "com.android.settings";
    private static final String UP_ACTIVITY_CLASS =
            "com.android.settings.Settings$WirelessSettingsActivity";

    //UI objects
    private ListPreference mButtonPreferredNetworkMode;
    private ListPreference mButtonEnabledNetworks;
    private CheckBoxPreference mButtonDataRoam;
    private CheckBoxPreference mButtonDataEnabled;
    private Preference mLteDataServicePref;

    private static final String iface = "rmnet0"; //TODO: this will go away

    private Phone mPhone;
    private MyHandler mHandler;
    private NetWorkHandler mNetworkHandler;
    private boolean mOkClicked;

    //GsmUmts options and Cdma options
    GsmUmtsOptions mGsmUmtsOptions;
    CdmaOptions mCdmaOptions;

    private Preference mClickedPreference;
    private boolean mShow4GForLTE;
    private boolean mIsGlobalCdma;
    ///M: for adjust setting UI on VXGA device.
    public PreferenceFragment mFragment;

    //This is a method implemented for DialogInterface.OnClickListener.
    //  Used to dismiss the dialogs when they come up.
    public void onClick(DialogInterface dialog, int which) {
        if (which == DialogInterface.BUTTON_POSITIVE) {
            mPhone.setDataRoamingEnabled(true);
            mOkClicked = true;
        } else {
            // Reset the toggle
            mButtonDataRoam.setChecked(false);
        }
    }

    public void onDismiss(DialogInterface dialog) {
        // Assuming that onClick gets called first
        if (!mOkClicked) {
            mButtonDataRoam.setChecked(false);
        }
    }

    @Override
    protected void onCreate(Bundle icicle) {
        ///M: for adjust setting UI on VXGA device. @{
        mFragment = new MobileNetworkSettingFragment();
        super.onCreate(icicle);
        mPhone = PhoneGlobals.getPhone();
        mHandler = new MyHandler();
        mPreCheckForRunning = new PreCheckForRunning(this);
        getFragmentManager().beginTransaction()
                .replace(android.R.id.content, mFragment).commit();
        /// @}
    }

    ///M: for adjust setting UI on VXGA device.
    public static class MobileNetworkSettingFragment extends PreferenceFragment
                            implements Preference.OnPreferenceChangeListener {
        MobileNetworkSettings instance = null;

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            WeakReference<MobileNetworkSettings> activityRef =
                    new WeakReference<MobileNetworkSettings>((MobileNetworkSettings) getActivity());
            instance = activityRef.get();
            addPreferencesFromResource(R.xml.network_setting);

            try {
                Context con = getActivity().createPackageContext("com.android.systemui", 0);
                int id = con.getResources().getIdentifier("config_show4GForLTE",
                        "bool", "com.android.systemui");
                instance.mShow4GForLTE = con.getResources().getBoolean(id);
            } catch (NameNotFoundException e) {
                loge("NameNotFoundException for show4GFotLTE");
                instance.mShow4GForLTE = false;
            }

            /// M: add for custom mediatek's common UI @{
            instance.initCommonUI();
            /// @}
            instance.initPhoneAndTelephony();
            instance.initIntentFilter();
            List<SimInfoRecord> list = SimInfoManager.getInsertedSimInfoList(getActivity());

            //get UI object references
            PreferenceScreen prefSet = getPreferenceScreen();

            instance.mButtonDataEnabled = (CheckBoxPreference) prefSet.findPreference(BUTTON_DATA_ENABLED_KEY);
            instance.mButtonDataRoam = (CheckBoxPreference) prefSet.findPreference(BUTTON_ROAMING_KEY);
            instance.mExtension.setRoamingSummary(instance.mButtonDataRoam, R.string.roaming_enable, R.string.roaming_disable);
            if (GeminiUtils.isGeminiSupport()) {
                prefSet.removePreference(instance.mButtonDataRoam);
            }
            instance.mButtonPreferredNetworkMode = (ListPreference) prefSet.findPreference(
                    BUTTON_PREFERED_NETWORK_MODE);
            instance.mButtonEnabledNetworks = (ListPreference) prefSet.findPreference(
                    BUTTON_ENABLED_NETWORKS_KEY);
            instance.mNetworkHandler = new NetWorkHandler(getActivity(), instance.mButtonPreferredNetworkMode);
            /// M: support gemini phone, 3G switch, PLMN prefer @{
            instance.mPreference3GSwitch = prefSet.findPreference(BUTTON_3G_SERVICE);
            /// @}
            instance.mLteDataServicePref = prefSet.findPreference(BUTTON_CDMA_LTE_DATA_SERVICE_KEY);
            instance.mButtonPreferredNetworkModeEx = prefSet.findPreference(BUTTON_NETWORK_MODE_EX_KEY);
            boolean isLteOnCdma = instance.mPhone.getLteOnCdmaMode() == PhoneConstants.LTE_ON_CDMA_TRUE;
            instance.mIsGlobalCdma = isLteOnCdma && getResources().getBoolean(R.bool.config_show_cdma);
            if (getResources().getBoolean(R.bool.world_phone) == true) {
                prefSet.removePreference(instance.mButtonEnabledNetworks);
                // set the listener for the mButtonPreferredNetworkMode list preference so we can issue
                // change Preferred Network Mode.
                instance.mButtonPreferredNetworkMode.setOnPreferenceChangeListener(this);

                //Get the networkMode from Settings.System and displays it
                instance.setPreferredNetworkModeValueFromSettings();
                instance.mCdmaOptions = new CdmaOptions(this, prefSet, instance.mPhone);
                instance.mGsmUmtsOptions = new GsmUmtsOptions(this, prefSet);
            } else {
                prefSet.removePreference(instance.mButtonPreferredNetworkMode);
                int phoneType = instance.mPhone.getPhoneType();
                /// M: For CT feature:APN and ManualNetwork refactory @{
                if (instance.mExtension.reloadPreference((PreferenceActivity)getActivity(), prefSet, instance.mButtonPreferredNetworkModeEx, instance.mPreference3GSwitch, instance.mPLMNPreference)) {
                    prefSet.removePreference(instance.mButtonEnabledNetworks);
                /// @}
                } else if (phoneType == PhoneConstants.PHONE_TYPE_CDMA) {
                    instance.mCdmaOptions = new CdmaOptions(this, prefSet, instance.mPhone);
                    if (isLteOnCdma) {
                        instance.mButtonEnabledNetworks.setEntries(
                                R.array.enabled_networks_cdma_choices);
                        instance.mButtonEnabledNetworks.setEntryValues(
                                R.array.enabled_networks_cdma_values);
                        /// M: @{
                        instance.mButtonEnabledNetworks.setOnPreferenceChangeListener(this);
                        instance.setNetworkModeValueFromSettings();
                        /// @}
                    }
                    /// M: support for cdma @{
                    if (!PhoneUtils.isSupportFeature("3G_SWITCH")) {
                        if (instance.mPreference3GSwitch != null) {
                            prefSet.removePreference(instance.mPreference3GSwitch);
                            instance.mPreference3GSwitch = null;
                        }
                    }
                    prefSet.removePreference(instance.mButtonPreferredNetworkModeEx);
                    instance.mCarrierSelPref = (PreferenceScreen) prefSet.findPreference(BUTTON_CARRIER_SEL);

                    if(FeatureOption.MTK_3GDONGLE_SUPPORT){
                        PreferenceScreen mActivatedevicetemp = (PreferenceScreen) prefSet.findPreference(BUTTON_ACTIVATE_DEVICE);
                        if(mActivatedevicetemp != null){
                             prefSet.removePreference(mActivatedevicetemp);
                        }
                    }
                    /// @}
                } else if (phoneType == PhoneConstants.PHONE_TYPE_GSM) {
                    if (!getResources().getBoolean(R.bool.config_prefer_2g)
                            && !getResources().getBoolean(R.bool.config_enabled_lte)) {
                        instance.mButtonEnabledNetworks.setEntries(
                                R.array.enabled_networks_except_gsm_lte_choices);
                        instance.mButtonEnabledNetworks.setEntryValues(
                                R.array.enabled_networks_except_gsm_lte_values);
                    } else if (!getResources().getBoolean(R.bool.config_prefer_2g)) {
                        int select = (instance.mShow4GForLTE == true) ?
                            R.array.enabled_networks_except_gsm_4g_choices
                            : R.array.enabled_networks_except_gsm_choices;
                        instance.mButtonEnabledNetworks.setEntries(select);
                        instance.mButtonEnabledNetworks.setEntryValues(
                                R.array.enabled_networks_except_gsm_values);
                    } else if (!getResources().getBoolean(R.bool.config_enabled_lte)) {
                        instance.mButtonEnabledNetworks.setEntries(
                                R.array.enabled_networks_except_lte_choices);
                        instance.mButtonEnabledNetworks.setEntryValues(
                                R.array.enabled_networks_except_lte_values);
                    } else if (instance.mIsGlobalCdma) {
                        instance.mButtonEnabledNetworks.setEntries(
                                R.array.enabled_networks_cdma_choices);
                        instance.mButtonEnabledNetworks.setEntryValues(
                                R.array.enabled_networks_cdma_values);
                    }
                    /// M: @{
                    instance.mGsmUmtsOptions = new GsmUmtsOptions(this, prefSet);
                    instance.initPreferenceForMobileNetwork(prefSet);
                    if (instance.mButtonPreferredNetworkMode == null && instance.mButtonPreferredNetworkModeEx == null) {
                        if (!getResources().getBoolean(R.bool.config_prefer_2g)
                                && !getResources().getBoolean(R.bool.config_enabled_lte)) {
                            instance.mButtonEnabledNetworks.setEntries(
                                    R.array.enabled_networks_except_gsm_lte_choices);
                            instance.mButtonEnabledNetworks.setEntryValues(
                                    R.array.enabled_networks_except_gsm_lte_values);
                        } else if (!getResources().getBoolean(R.bool.config_prefer_2g)) {
                            int select = (instance.mShow4GForLTE == true) ?
                                R.array.enabled_networks_except_gsm_4g_choices
                                : R.array.enabled_networks_except_gsm_choices;
                            instance.mButtonEnabledNetworks.setEntries(select);
                            instance.mButtonEnabledNetworks.setEntryValues(
                                    R.array.enabled_networks_except_gsm_values);
                        } else if (!getResources().getBoolean(R.bool.config_enabled_lte)) {
                            instance.mButtonEnabledNetworks.setEntries(
                                    R.array.enabled_networks_except_lte_choices);
                            instance.mButtonEnabledNetworks.setEntryValues(
                                    R.array.enabled_networks_except_lte_values);
                        } else if (isLteOnCdma && getResources().getBoolean(R.bool.config_show_cdma)) {
                            instance.mButtonEnabledNetworks.setEntries(
                                    R.array.enabled_networks_cdma_choices);
                            instance.mButtonEnabledNetworks.setEntryValues(
                                    R.array.enabled_networks_cdma_values);
                        } else {
                            int select = (instance.mShow4GForLTE == true) ? R.array.enabled_networks_4g_choices
                                : R.array.enabled_networks_choices;
                            instance.mButtonEnabledNetworks.setEntries(select);
                            instance.mButtonEnabledNetworks.setEntryValues(
                                    R.array.enabled_networks_values);
                        }
                    } else {
                        // Remove google default item and use our.
                        prefSet.removePreference(instance.mButtonEnabledNetworks);
                    }
                    /// @}
                } else {
                    throw new IllegalStateException("Unexpected phone type: " + phoneType);
                }
                ///M: Google code.@{
                /*
                mButtonEnabledNetworks.setOnPreferenceChangeListener(this);
                int settingsNetworkMode = android.provider.Settings.Global.getInt(
                        mPhone.getContext().getContentResolver(),
                        android.provider.Settings.Global.PREFERRED_NETWORK_MODE,
                        preferredNetworkMode);
                if (DBG) log("settingsNetworkMode: " + settingsNetworkMode);
                mButtonEnabledNetworks.setValue(Integer.toString(settingsNetworkMode));
                */
                /// @}
            }

            if(FeatureOption.MTK_3GDONGLE_SUPPORT){
                    if(instance.mPLMNPreference != null){
                    prefSet.removePreference(instance.mPLMNPreference);
                }
            }

            final boolean missingDataServiceUrl = TextUtils.isEmpty(
                    android.provider.Settings.Global.getString(instance.getContentResolver(),
                            android.provider.Settings.Global.SETUP_PREPAID_DATA_SERVICE_URL));
            if (!isLteOnCdma || missingDataServiceUrl) {
                prefSet.removePreference(instance.mLteDataServicePref);
            } else {
                android.util.Log.d(LOG_TAG, "keep ltePref");
            }
            /// M: register receivers
            instance.registerReceiver(instance.mReceiver, instance.mIntentFilter);

            // Read platform settings for carrier settings
            final boolean isCarrierSettingsEnabled = getResources().getBoolean(
                    R.bool.config_carrier_settings_enable);
            if (!isCarrierSettingsEnabled) {
                Preference pref = prefSet.findPreference(BUTTON_CARRIER_SETTINGS_KEY);
                if (pref != null) {
                    prefSet.removePreference(pref);
                }
            }

            ActionBar actionBar = instance.getActionBar();
            if (actionBar != null) {
                // android.R.id.home will be triggered in onOptionsItemSelected()
                actionBar.setDisplayHomeAsUpEnabled(true);
            }
            instance.setNetworkOperator();
        }

        /**
         * Invoked on each preference click in this hierarchy, overrides
         * PreferenceActivity's implementation.  Used to make sure we track the
         * preference click events.
         */
        @Override
        public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen, Preference preference) {
            /** TODO: Refactor and get rid of the if's using subclasses */
            /// M: For Operation plugin new feature @{
            if (instance.mExtension.onPreferenceTreeClick(preferenceScreen, preference)) {
                return true;
            /// @}
            } else if (instance.mGsmUmtsOptions != null &&
                    instance.mGsmUmtsOptions.preferenceTreeClick(preference)) {
                return true;
            } else if (instance.mCdmaOptions != null &&
                    instance.mCdmaOptions.preferenceTreeClick(preference)) {
                if (Boolean.parseBoolean(
                        SystemProperties.get(TelephonyProperties.PROPERTY_INECM_MODE))) {

                    instance.mClickedPreference = preference;

                    // In ECM mode launch ECM app dialog
                    instance.startActivityForResult(
                        new Intent(TelephonyIntents.ACTION_SHOW_NOTICE_ECM_BLOCK_OTHERS, null),
                        REQUEST_CODE_EXIT_ECM);
                }
                return true;
            } else if (preference == instance.mButtonPreferredNetworkMode) {
                instance.setPreferredNetworkModeValueFromSettings();
                return true;
            } else if (preference == instance.mButtonDataRoam) {
                if (DBG) {
                    log("onPreferenceTreeClick: preference == mButtonDataRoam.");
                }

                //normally called on the toggle click
                if (instance.mButtonDataRoam.isChecked()) {
                    // First confirm with a warning dialog about charges
                    instance.mOkClicked = false;
                    getActivity().showDialog(ROAMING_DIALOG);
                } else {
                    instance.mPhone.setDataRoamingEnabled(false);
                }
                return true;
            } else if (preference == instance.mButtonDataEnabled) {
                if (DBG) {
                    log("onPreferenceTreeClick: preference == mButtonDataEnabled.");
                }
                ConnectivityManager cm =
                        (ConnectivityManager)instance.getSystemService(Context.CONNECTIVITY_SERVICE);

                cm.setMobileDataEnabled(instance.mButtonDataEnabled.isChecked());
                return true;
            } else if (preference == instance.mLteDataServicePref) {
                String tmpl = android.provider.Settings.Global.getString(instance.getContentResolver(),
                            android.provider.Settings.Global.SETUP_PREPAID_DATA_SERVICE_URL);
                if (!TextUtils.isEmpty(tmpl)) {
                    String imsi = TelephonyManagerWrapper.getSubscriberId(PhoneWrapper.UNSPECIFIED_SLOT_ID);
                    if (imsi == null) {
                        imsi = "";
                    }
                    final String url = TextUtils.isEmpty(tmpl) ? null
                            : TextUtils.expandTemplate(tmpl, imsi).toString();
                    Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                    instance.startActivity(intent);
                } else {
                    Log.e(LOG_TAG, "Missing SETUP_PREPAID_DATA_SERVICE_URL");
                }
                return true;
            } else if (preference == instance.mButtonEnabledNetworks) {
                int settingsNetworkMode = android.provider.Settings.Global.getInt(instance.mPhone.getContext().
                        getContentResolver(), android.provider.Settings.Global.PREFERRED_NETWORK_MODE,
                        preferredNetworkMode);
                instance.mButtonEnabledNetworks.setValue(Integer.toString(settingsNetworkMode));
                return true;
            } else if (preference == instance.mButtonPreferredNetworkModeEx) {
                Intent intent = new Intent(getActivity(), MultipleSimActivity.class);
                intent.putExtra(MultipleSimActivity.INTENT_KEY, "ListPreference");
                intent.putExtra(GeminiUtils.EXTRA_TITLE_NAME, R.string.preferred_network_mode_title);
                intent.putExtra(MultipleSimActivity.LIST_TITLE, R.string.gsm_umts_network_preferences_title);
                intent.putExtra(MultipleSimActivity.INIT_FEATURE_NAME, "NETWORK_MODE");
                intent.putExtra(MultipleSimActivity.INIT_BASE_KEY, "preferred_network_mode_key@");

                int slot3G = PhoneGlobals.getInstance().phoneMgrEx.get3GCapabilitySIM();
                if (GeminiUtils.isValidSlot(slot3G)) {
                    /// M: Add for LTE feature
                    instance.setPreferredNetworkModeExEntriesAndValues(intent, slot3G).putExtra(GeminiUtils.EXTRA_3G_CARD_ONLY, true);
                    instance.mPreCheckForRunning.checkToRun(intent, slot3G, PreCheckForRunning.PIN1_REQUEST_CODE);
                }
                return true;
            } else if (preference == instance.mCarrierSelPref || preference == instance.mPLMNPreference) {
                instance.mSlotId = GeminiUtils.getSlotId(getActivity(), preference.getTitle().toString(), android.R.style.Theme_Holo_DialogWhenLarge);
                instance.mTargetPreference = preference;
                if (GeminiUtils.isValidSlot(instance.mSlotId)) {
                    GeminiUtils.startActivity(instance.mSlotId, preference, instance.mPreCheckForRunning);
                }
                return true;
            } else if (preference == instance.mDataConnPref) {
                ///M: consistent_UI use the data connection ui style same as sim management for single 
                ///   and gemini
                Log.d(LOG_TAG,"mDataConnPref is clicked");
                return true;
            } else {
                // if the button is anything but the simple toggle preference,
                // we'll need to disable all preferences to reject all click
                // events until the sub-activity's UI comes up.
                preferenceScreen.setEnabled(false);
                // Let the intents be launched by the Preference manager
                return false;
            }
        }

        /**
         * Implemented to support onPreferenceChangeListener to look for preference
         * changes specifically on CLIR.
         *
         * @param preference is the preference to be changed, should be mButtonCLIR.
         * @param objValue should be the value of the selection, NOT its localized
         * display value.
         */
        public boolean onPreferenceChange(Preference preference, Object objValue) {
            if (preference == instance.mButtonPreferredNetworkMode) {
                //NOTE onPreferenceChange seems to be called even if there is no change
                //Check if the button value is changed from the System.Setting
                instance.mButtonPreferredNetworkMode.setValue((String) objValue);
                int buttonNetworkMode;
                buttonNetworkMode = Integer.valueOf((String) objValue).intValue();
                int settingsNetworkMode = android.provider.Settings.Global.getInt(
                        instance.mPhone.getContext().getContentResolver(),
                        android.provider.Settings.Global.PREFERRED_NETWORK_MODE, preferredNetworkMode);
                if (buttonNetworkMode != settingsNetworkMode) {
                    /// M: when wait for network switch done show dialog    
                    getActivity().showDialog(GeminiUtils.PROGRESS_DIALOG);
                    int modemNetworkMode;
                    // if new mode is invalid ignore it
                    switch (buttonNetworkMode) {
                        case Phone.NT_MODE_WCDMA_PREF:
                        case Phone.NT_MODE_GSM_ONLY:
                        case Phone.NT_MODE_WCDMA_ONLY:
                        case Phone.NT_MODE_GSM_UMTS:
                        case Phone.NT_MODE_CDMA:
                        case Phone.NT_MODE_CDMA_NO_EVDO:
                        case Phone.NT_MODE_EVDO_NO_CDMA:
                        case Phone.NT_MODE_GLOBAL:
                        case Phone.NT_MODE_LTE_CDMA_AND_EVDO:
                        case Phone.NT_MODE_LTE_GSM_WCDMA:
                        case Phone.NT_MODE_LTE_CMDA_EVDO_GSM_WCDMA:
                        case Phone.NT_MODE_LTE_ONLY:
                        case Phone.NT_MODE_LTE_WCDMA:
                            // This is one of the modes we recognize
                            modemNetworkMode = buttonNetworkMode;
                            break;
                        default:
                            loge("Invalid Network Mode (" + buttonNetworkMode + ") chosen. Ignore.");
                            return true;
                    }

                    instance.mButtonPreferredNetworkMode.setSummary(instance.mButtonPreferredNetworkMode.getEntry());

                    android.provider.Settings.Global.putInt(instance.mPhone.getContext().getContentResolver(),
                            android.provider.Settings.Global.PREFERRED_NETWORK_MODE,
                            buttonNetworkMode );
                    /// M: support gemini Set the modem network mode
                    PhoneWrapper.setPreferredNetworkType(instance.mPhone, modemNetworkMode, instance.mNetworkHandler
                            .obtainMessage(NetWorkHandler.MESSAGE_SET_PREFERRED_NETWORK_TYPE), instance.mSlotId);
                }
            } else if (preference == instance.mButtonEnabledNetworks) {
                instance.mButtonEnabledNetworks.setValue((String) objValue);
                int buttonNetworkMode;
                buttonNetworkMode = Integer.valueOf((String) objValue).intValue();
                if (DBG) log("buttonNetworkMode: " + buttonNetworkMode);
                int settingsNetworkMode = android.provider.Settings.Global.getInt(
                        instance.mPhone.getContext().getContentResolver(),
                        android.provider.Settings.Global.PREFERRED_NETWORK_MODE, preferredNetworkMode);
                if (buttonNetworkMode != settingsNetworkMode) {
                    int modemNetworkMode;
                    // if new mode is invalid ignore it
                    switch (buttonNetworkMode) {
                        case Phone.NT_MODE_WCDMA_PREF:
                        case Phone.NT_MODE_GSM_ONLY:
                        case Phone.NT_MODE_LTE_GSM_WCDMA:
                        case Phone.NT_MODE_LTE_CMDA_EVDO_GSM_WCDMA:
                        case Phone.NT_MODE_CDMA:
                        case Phone.NT_MODE_CDMA_NO_EVDO:
                        case Phone.NT_MODE_LTE_CDMA_AND_EVDO:
                            // This is one of the modes we recognize
                            modemNetworkMode = buttonNetworkMode;
                            break;
                        default:
                            loge("Invalid Network Mode (" + buttonNetworkMode + ") chosen. Ignore.");
                            return true;
                    }

                    instance.UpdateEnabledNetworksValueAndSummary(buttonNetworkMode);

                    android.provider.Settings.Global.putInt(instance.mPhone.getContext().getContentResolver(),
                            android.provider.Settings.Global.PREFERRED_NETWORK_MODE,
                            buttonNetworkMode );
                    //Set the modem network mode
                    instance.mPhone.setPreferredNetworkType(modemNetworkMode, instance.mHandler
                            .obtainMessage(MyHandler.MESSAGE_SET_PREFERRED_NETWORK_TYPE));
                }
            /// M: @{
            // TODO: Move this to a function
            } else if (preference == instance.mDataConnPref) {
                long simid = ((Long) objValue).longValue();
                Log.d(LOG_TAG, "value=" + simid);
                int slotId = GeminiUtils.getSimSlotIdBySimInfoId(simid, instance.mSimInfoList);
                if (slotId != GeminiUtils.UNDEFINED_SLOT_ID && instance.mPreCheckForRunning.isSimLocked(slotId)) {
                    instance.mPreCheckForRunning.unLock(null, slotId, PreCheckForRunning.PIN1_REQUEST_CODE);
                    Log.d(LOG_TAG, "need unlock before switch data connection");
                    /// M: ALPS01014569, if sim card is lock, return false, do not update listview.
                    return false;
                }
                ///M: only gemini need to show a dialog @{
                if (FeatureOption.MTK_GEMINI_SUPPORT) {
                    if (simid == 0) {
                        ///M: turn off data connection no need to get whether to show connection dlg
                        instance.mDataSwitchMsgIndex = -1;
                    } else {
                        instance.mDataSwitchMsgIndex = instance.dataSwitchConfirmDlgMsg(simid);
                    }
                }
                ///@}
                if (instance.mDataSwitchMsgIndex == -1 || !FeatureOption.MTK_GEMINI_SUPPORT) {
                    instance.switchGprsDefaultSIM(simid);
                } else {
                    instance.mSelectedGprsSimId = simid;
                    instance.showDialog(DIALOG_GPRS_SWITCH_CONFIRM);
                }
                /// @}
            }

            // always let the preference setting proceed.
            return true;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        /// M: MTK Delete @{
        // upon resumption from the sub-activity, make sure we re-enable the
        // preferences.
        /*getPreferenceScreen().setEnabled(true);

        ConnectivityManager cm =
                (ConnectivityManager)getSystemService(Context.CONNECTIVITY_SERVICE);
        mButtonDataEnabled.setChecked(cm.getMobileDataEnabled());*/
        /// @}

        // Set UI state in onResume because a user could go home, launch some
        // app to change this setting's backend, and re-launch this settings app
        // and the UI state would be inconsistent with actual state
        mButtonDataRoam.setChecked(mPhone.getDataRoamingEnabled());
        mConnService = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (mFragment.getPreferenceScreen().findPreference(BUTTON_PREFERED_NETWORK_MODE) != null)  {
            mPhone.getPreferredNetworkType(mHandler.obtainMessage(
                    MyHandler.MESSAGE_GET_PREFERRED_NETWORK_TYPE));
        }

        if (mFragment.getPreferenceScreen().findPreference(BUTTON_ENABLED_NETWORKS_KEY) != null)  {
            mPhone.getPreferredNetworkType(mHandler.obtainMessage(
                    MyHandler.MESSAGE_GET_PREFERRED_NETWORK_TYPE));
        }

        /// M: set RAT mode when on resume 
        if (mButtonPreferredNetworkMode != null) {
            mButtonPreferredNetworkMode.setSummary(mButtonPreferredNetworkMode.getEntry());
        }

        ///M: add for data connection gemini and op01 only
        initDataConnPref();
        updateDataConnPref(false);
        //Please make sure this is the last line!!
        updateScreenStatus();
        ///M: @{
        if (FeatureOption.MTK_GEMINI_SUPPORT) {
            getContentResolver().registerContentObserver(
                Settings.System.getUriFor(Settings.System.GPRS_CONNECTION_SIM_SETTING),
                false, mGprsDefaultSIMObserver);
        } else {
            getContentResolver().registerContentObserver(
                Settings.Secure.getUriFor(Settings.Global.MOBILE_DATA),
                false, mGprsDefaultSIMObserver);
        }
        ///@}
    }

    @Override
    protected void onPause() {
        super.onPause();
        /// M: unregister Default SIM Observer 
        getContentResolver().unregisterContentObserver(mGprsDefaultSIMObserver);
    }

    // please use NetworkHandler instead of MyHandler for CallSetting & NetworkSetting @ {
    private class MyHandler extends Handler {

        static final int MESSAGE_GET_PREFERRED_NETWORK_TYPE = 0;
        static final int MESSAGE_SET_PREFERRED_NETWORK_TYPE = 1;

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MESSAGE_GET_PREFERRED_NETWORK_TYPE:
                    handleGetPreferredNetworkTypeResponse(msg);
                    break;

                case MESSAGE_SET_PREFERRED_NETWORK_TYPE:
                    handleSetPreferredNetworkTypeResponse(msg);
                    break;
            }
        }

        private void handleGetPreferredNetworkTypeResponse(Message msg) {
            AsyncResult ar = (AsyncResult) msg.obj;

            if (ar.exception == null) {
                int modemNetworkMode = ((int[])ar.result)[0];

                if (DBG) {
                    log ("handleGetPreferredNetworkTypeResponse: modemNetworkMode = " +
                            modemNetworkMode);
                }

                int settingsNetworkMode = android.provider.Settings.Global.getInt(
                        mPhone.getContext().getContentResolver(),
                        android.provider.Settings.Global.PREFERRED_NETWORK_MODE,
                        preferredNetworkMode);

                if (DBG) {
                    log("handleGetPreferredNetworkTypeReponse: settingsNetworkMode = " +
                            settingsNetworkMode);
                }

                //check that modemNetworkMode is from an accepted value
                if (modemNetworkMode == Phone.NT_MODE_WCDMA_PREF ||
                        modemNetworkMode == Phone.NT_MODE_GSM_ONLY ||
                        modemNetworkMode == Phone.NT_MODE_WCDMA_ONLY ||
                        modemNetworkMode == Phone.NT_MODE_GSM_UMTS ||
                        modemNetworkMode == Phone.NT_MODE_CDMA ||
                        modemNetworkMode == Phone.NT_MODE_CDMA_NO_EVDO ||
                        modemNetworkMode == Phone.NT_MODE_EVDO_NO_CDMA ||
                        modemNetworkMode == Phone.NT_MODE_GLOBAL ||
                        modemNetworkMode == Phone.NT_MODE_LTE_CDMA_AND_EVDO ||
                        modemNetworkMode == Phone.NT_MODE_LTE_GSM_WCDMA ||
                        modemNetworkMode == Phone.NT_MODE_LTE_CMDA_EVDO_GSM_WCDMA ||
                        modemNetworkMode == Phone.NT_MODE_LTE_ONLY ||
                        modemNetworkMode == Phone.NT_MODE_LTE_WCDMA) {
                    if (DBG) {
                        log("handleGetPreferredNetworkTypeResponse: if 1: modemNetworkMode = " +
                                modemNetworkMode);
                    }

                    //check changes in modemNetworkMode and updates settingsNetworkMode
                    if (modemNetworkMode != settingsNetworkMode) {
                        if (DBG) {
                            log("handleGetPreferredNetworkTypeResponse: if 2: " +
                                    "modemNetworkMode != settingsNetworkMode");
                        }

                        settingsNetworkMode = modemNetworkMode;

                        if (DBG) { log("handleGetPreferredNetworkTypeResponse: if 2: " +
                                "settingsNetworkMode = " + settingsNetworkMode);
                        }

                        //changes the Settings.System accordingly to modemNetworkMode
                        android.provider.Settings.Global.putInt(
                                mPhone.getContext().getContentResolver(),
                                android.provider.Settings.Global.PREFERRED_NETWORK_MODE,
                                settingsNetworkMode );
                    }

                    //UpdatePreferredNetworkModeSummary(modemNetworkMode);
                    UpdateEnabledNetworksValueAndSummary(modemNetworkMode);
                    // changes the mButtonPreferredNetworkMode accordingly to modemNetworkMode
                    mButtonPreferredNetworkMode.setValue(Integer.toString(modemNetworkMode));
                } else {
                    if (DBG) log("handleGetPreferredNetworkTypeResponse: else: reset to default");
                    resetNetworkModeToDefault();
                }
            }
        }

        private void handleSetPreferredNetworkTypeResponse(Message msg) {
            AsyncResult ar = (AsyncResult) msg.obj;

            if (ar.exception == null) {
                int networkMode = Integer.valueOf(
                        mButtonPreferredNetworkMode.getValue()).intValue();
                android.provider.Settings.Global.putInt(mPhone.getContext().getContentResolver(),
                        android.provider.Settings.Global.PREFERRED_NETWORK_MODE,
                        networkMode );
                networkMode = Integer.valueOf(
                        mButtonEnabledNetworks.getValue()).intValue();
                android.provider.Settings.Global.putInt(mPhone.getContext().getContentResolver(),
                        android.provider.Settings.Global.PREFERRED_NETWORK_MODE,
                        networkMode );
            } else {
                /// M: Modified for Gemini. @{
                // Google code:
                /*
                mPhone.getPreferredNetworkType(obtainMessage(MESSAGE_GET_PREFERRED_NETWORK_TYPE));
                */
                PhoneWrapper.getPreferredNetworkType(mPhone,
                        obtainMessage(MESSAGE_GET_PREFERRED_NETWORK_TYPE), mSlotId);
            }
        }

        private void resetNetworkModeToDefault() {
            //set the mButtonPreferredNetworkMode
            mButtonPreferredNetworkMode.setValue(Integer.toString(preferredNetworkMode));
            mButtonEnabledNetworks.setValue(Integer.toString(preferredNetworkMode));
            //set the Settings.System
            android.provider.Settings.Global.putInt(mPhone.getContext().getContentResolver(),
                        android.provider.Settings.Global.PREFERRED_NETWORK_MODE,
                        preferredNetworkMode );
            //Set the Modem
            /// M: Modified for Gemini. @{
            // Google code:
            /*
            mPhone.setPreferredNetworkType(preferredNetworkMode,
                    this.obtainMessage(MyHandler.MESSAGE_SET_PREFERRED_NETWORK_TYPE));
            */
            PhoneWrapper.setPreferredNetworkType(mPhone, preferredNetworkMode,
                    this.obtainMessage(MyHandler.MESSAGE_SET_PREFERRED_NETWORK_TYPE), mSlotId);
        }
    }

    /// MTK Delete : We don't use this function now, just keep here for mirgation
    /* Google Code
    private void UpdatePreferredNetworkModeSummary(int NetworkMode) {
        switch(NetworkMode) {
            case Phone.NT_MODE_WCDMA_PREF:
                mButtonPreferredNetworkMode.setSummary(
                        R.string.preferred_network_mode_wcdma_perf_summary);
                break;
            case Phone.NT_MODE_GSM_ONLY:
                mButtonPreferredNetworkMode.setSummary(
                        R.string.preferred_network_mode_gsm_only_summary);
                break;
            case Phone.NT_MODE_WCDMA_ONLY:
                mButtonPreferredNetworkMode.setSummary(
                        R.string.preferred_network_mode_wcdma_only_summary);
                break;
            case Phone.NT_MODE_GSM_UMTS:
                mButtonPreferredNetworkMode.setSummary(
                        R.string.preferred_network_mode_gsm_wcdma_summary);
                break;
            case Phone.NT_MODE_CDMA:
                switch (mPhone.getLteOnCdmaMode()) {
                    case PhoneConstants.LTE_ON_CDMA_TRUE:
                        mButtonPreferredNetworkMode.setSummary(
                            R.string.preferred_network_mode_cdma_summary);
                    break;
                    case PhoneConstants.LTE_ON_CDMA_FALSE:
                    default:
                        mButtonPreferredNetworkMode.setSummary(
                            R.string.preferred_network_mode_cdma_evdo_summary);
                        break;
                }
                break;
            case Phone.NT_MODE_CDMA_NO_EVDO:
                mButtonPreferredNetworkMode.setSummary(
                        R.string.preferred_network_mode_cdma_only_summary);
                break;
            case Phone.NT_MODE_EVDO_NO_CDMA:
                mButtonPreferredNetworkMode.setSummary(
                        R.string.preferred_network_mode_evdo_only_summary);
                break;
            case Phone.NT_MODE_LTE_ONLY:
                mButtonPreferredNetworkMode.setSummary(
                        R.string.preferred_network_mode_lte_summary);
                break;
            case Phone.NT_MODE_LTE_GSM_WCDMA:
                mButtonPreferredNetworkMode.setSummary(
                        R.string.preferred_network_mode_lte_gsm_wcdma_summary);
                break;
            case Phone.NT_MODE_LTE_CDMA_AND_EVDO:
                mButtonPreferredNetworkMode.setSummary(
                        R.string.preferred_network_mode_lte_cdma_evdo_summary);
                break;
            case Phone.NT_MODE_LTE_CMDA_EVDO_GSM_WCDMA:
                mButtonPreferredNetworkMode.setSummary(
                        R.string.preferred_network_mode_global_summary);
                break;
            case Phone.NT_MODE_GLOBAL:
                mButtonPreferredNetworkMode.setSummary(
                        R.string.preferred_network_mode_cdma_evdo_gsm_wcdma_summary);
                break;
            case Phone.NT_MODE_LTE_WCDMA:
                mButtonPreferredNetworkMode.setSummary(
                        R.string.preferred_network_mode_lte_wcdma_summary);
                break;
            default:
                mButtonPreferredNetworkMode.setSummary(
                        R.string.preferred_network_mode_global_summary);
        }
    }
    */

    private void UpdateEnabledNetworksValueAndSummary(int NetworkMode) {
        switch (NetworkMode) {
            case Phone.NT_MODE_WCDMA_ONLY:
            case Phone.NT_MODE_GSM_UMTS:
            case Phone.NT_MODE_WCDMA_PREF:
                if (!mIsGlobalCdma) {
                    mButtonEnabledNetworks.setValue(
                            Integer.toString(Phone.NT_MODE_WCDMA_PREF));
                    mButtonEnabledNetworks.setSummary(R.string.network_3G);
                } else {
                    mButtonEnabledNetworks.setValue(
                            Integer.toString(Phone.NT_MODE_LTE_CMDA_EVDO_GSM_WCDMA));
                    mButtonEnabledNetworks.setSummary(R.string.network_global);
                }
                break;
            case Phone.NT_MODE_GSM_ONLY:
                if (!mIsGlobalCdma) {
                    mButtonEnabledNetworks.setValue(
                            Integer.toString(Phone.NT_MODE_GSM_ONLY));
                    mButtonEnabledNetworks.setSummary(R.string.network_2G);
                } else {
                    mButtonEnabledNetworks.setValue(
                            Integer.toString(Phone.NT_MODE_LTE_CMDA_EVDO_GSM_WCDMA));
                    mButtonEnabledNetworks.setSummary(R.string.network_global);
                }
                break;
            case Phone.NT_MODE_LTE_GSM_WCDMA:
            case Phone.NT_MODE_LTE_ONLY:
            case Phone.NT_MODE_LTE_WCDMA:
                if (!mIsGlobalCdma) {
                    mButtonEnabledNetworks.setValue(
                            Integer.toString(Phone.NT_MODE_LTE_GSM_WCDMA));
                    mButtonEnabledNetworks.setSummary((mShow4GForLTE == true)
                            ? R.string.network_4G : R.string.network_lte);
                } else {
                    mButtonEnabledNetworks.setValue(
                            Integer.toString(Phone.NT_MODE_LTE_CMDA_EVDO_GSM_WCDMA));
                    mButtonEnabledNetworks.setSummary(R.string.network_global);
                }
                break;
            case Phone.NT_MODE_LTE_CDMA_AND_EVDO:
                mButtonEnabledNetworks.setValue(
                        Integer.toString(Phone.NT_MODE_LTE_CDMA_AND_EVDO));
                mButtonEnabledNetworks.setSummary(R.string.network_lte);
                break;
            case Phone.NT_MODE_CDMA:
            case Phone.NT_MODE_EVDO_NO_CDMA:
            case Phone.NT_MODE_GLOBAL:
                mButtonEnabledNetworks.setValue(
                        Integer.toString(Phone.NT_MODE_CDMA));
                mButtonEnabledNetworks.setSummary(R.string.network_3G);
                break;
            case Phone.NT_MODE_CDMA_NO_EVDO:
                mButtonEnabledNetworks.setValue(
                        Integer.toString(Phone.NT_MODE_CDMA_NO_EVDO));
                mButtonEnabledNetworks.setSummary(R.string.network_1x);
                break;
            case Phone.NT_MODE_LTE_CMDA_EVDO_GSM_WCDMA:
                mButtonEnabledNetworks.setValue(
                        Integer.toString(Phone.NT_MODE_LTE_CMDA_EVDO_GSM_WCDMA));
                mButtonEnabledNetworks.setSummary(R.string.network_global);
                break;
            default:
                String errMsg = "Invalid Network Mode (" + NetworkMode + "). Ignore.";
                loge(errMsg);
                mButtonEnabledNetworks.setSummary(errMsg);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch(requestCode) {
        case REQUEST_CODE_EXIT_ECM:
            Boolean isChoiceYes =
                data.getBooleanExtra(EmergencyCallbackModeExitDialog.EXTRA_EXIT_ECM_RESULT, false);
            if (isChoiceYes) {
                // If the phone exits from ECM mode, show the CDMA Options
                mCdmaOptions.showDialog(mClickedPreference);
            } else {
                // do nothing
            }
            break;
        case GeminiUtils.REQUEST_SIM_SELECT:
            if (RESULT_OK == resultCode) {
                mSlotId = data.getIntExtra(GeminiConstants.SLOT_ID_KEY, GeminiUtils.UNDEFINED_SLOT_ID);
            }
            Log.d(LOG_TAG, "mSlotId=" + mSlotId);
            if (GeminiUtils.isValidSlot(mSlotId)) {
                GeminiUtils.startActivity(mSlotId, mTargetPreference, mPreCheckForRunning);
            }
            break;
        default:
            break;
        }
    }

    private static void log(String msg) {
        Log.d(LOG_TAG, msg);
    }

    private static void loge(String msg) {
        Log.e(LOG_TAG, msg);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        final int itemId = item.getItemId();
        if (itemId == android.R.id.home) {  // See ActionBar#setDisplayHomeAsUpEnabled()
            // Commenting out "logical up" capability. This is a workaround for issue 5278083.
            //
            // Settings app may not launch this activity via UP_ACTIVITY_CLASS but the other
            // Activity that looks exactly same as UP_ACTIVITY_CLASS ("SubSettings" Activity).
            // At that moment, this Activity launches UP_ACTIVITY_CLASS on top of the Activity.
            // which confuses users.
            // TODO: introduce better mechanism for "up" capability here.
            /*Intent intent = new Intent(Intent.ACTION_MAIN);
            intent.setClassName(UP_ACTIVITY_PACKAGE, UP_ACTIVITY_CLASS);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(intent);*/
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    // -------------------- Mediatek ---------------------
    ///M: add for data connection under gemini
    private static final String KEY_DATA_CONN = "data_connection_setting";
    private static final String ACTION_DATA_USAGE_DISABLED_DIALOG_OK =
            "com.mediatek.systemui.net.action.ACTION_DATA_USAGE_DISABLED_DIALOG_OK";
    ///M: add for data conn feature @{
    private SimSelectDialogPreference mDataConnPref = null;
    public static final int DATA_STATE_CHANGE_TIMEOUT = 2001;
    public static final int RESET_DATA_CONNECTION = 2002;
    private List<SimInfoRecord> mSimInfoList;
    private static final int VIDEO_CALL_OFF = -1;
    private int mIndicatorState;
    private int mIndicatorSlotId;
    ///@}

    private SettingsExtension mExtension;

    /// M: add for gemini support @{
    private static final String BUTTON_GSM_UMTS_OPTIONS = "gsm_umts_options_key";
    private static final String BUTTON_CDMA_OPTIONS = "cdma_options_key";
    private static final String BUTTON_APN = "button_apn_key";
    private static final String BUTTON_ACTIVATE_DEVICE = "cdma_activate_device_key";
    private static final String BUTTON_CARRIER_SEL = "button_carrier_sel_key";
    private static final String BUTTON_3G_SERVICE = "button_3g_service_key";
    private static final String BUTTON_PLMN_LIST = "button_plmn_key";
    private static final String BUTTON_NETWORK_MODE_EX_KEY = "button_network_mode_ex_key";
    private static final String BUTTON_NETWORK_MODE_KEY = "gsm_umts_preferred_network_mode_key";

    private static final int DATA_ENABLE_ALERT_DIALOG = 100;
    private static final int DATA_DISABLE_ALERT_DIALOG = 200;
    private static final int ROAMING_DIALOG = 300;

    private Preference mButtonPreferredNetworkModeEx;
    private Preference mPreference3GSwitch;
    private Preference mPLMNPreference;
    private PreferenceScreen mCarrierSelPref;
    private PreCheckForRunning mPreCheckForRunning;

    private Preference mTargetPreference;

    private TelephonyManagerEx mTelephonyManagerEx;
    private ConnectivityManager mConnService;
    private ITelephony mITelephony;

    private static final int DIALOG_GPRS_SWITCH_CONFIRM = 1;
    private int mDataSwitchMsgIndex = -1;
    private long mSelectedGprsSimId = -1;
    private int mSlotId;

    /// M: add for 3G switch 2.0 @{
    private boolean mIs3GSwitchManualEnabled = false;
    private boolean mManualAllowedSlot;
    /// @}

    private PhoneStateListener mPhoneStateListener = new PhoneStateListener() {
        @Override
        public void onCallStateChanged(int state, String incomingNumber) {
            super.onCallStateChanged(state, incomingNumber);
            log("onCallStateChanged ans state is " + state);
            switch(state) {
            case TelephonyManager.CALL_STATE_IDLE:
                updateScreenStatus();
                break;
            default:
                break;
            }
        }
    };

    private ContentObserver mGprsDefaultSIMObserver = new ContentObserver(
            new Handler()) {
        @Override
        public void onChange(boolean selfChange) {
            updateDataConnPref(false);
        }
    };

    ///@}
    private IntentFilter mIntentFilter;
    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            Log.d(LOG_TAG, "action: " + action);
            if (action.equals(Intent.ACTION_AIRPLANE_MODE_CHANGED)) {
                /// M: ALPS00694371 @{
                // when ariplane mode is on, dismiss dialog
                updateScreenStatus();
                /// @}
            } else if (action.equals(TelephonyIntents.ACTION_ANY_DATA_CONNECTION_STATE_CHANGED) && mIsChangeData) {
                PhoneConstants.DataState state = getMobileDataState(intent);
                String apnTypeList = intent.getStringExtra(PhoneConstants.DATA_APN_TYPE_KEY);
                Log.d(LOG_TAG, "apnTypeList=" + apnTypeList + "state=" + state);
                if (PhoneConstants.APN_TYPE_DEFAULT.equals(apnTypeList) && 
                    (state == PhoneConstants.DataState.CONNECTED || 
                     state == PhoneConstants.DataState.DISCONNECTED)) {
                    mTimeoutHandler.removeMessages(DATA_STATE_CHANGE_TIMEOUT);
                    updateDataConnPref(true);
                }
            } else if (action.equals(Intent.ACTION_DUAL_SIM_MODE_CHANGED)) {
                updateScreenStatus();
            } else if (TelephonyIntents.ACTION_EF_CSP_CONTENT_NOTIFY.equals(action)) {
                setNetworkOperator();
            } else if (action.equals(TelephonyIntents.ACTION_SIM_INDICATOR_STATE_CHANGED)) {
                mIndicatorState = intent.getIntExtra(TelephonyIntents.INTENT_KEY_ICC_STATE, -1);
                mIndicatorSlotId = intent.getIntExtra(TelephonyIntents.INTENT_KEY_ICC_SLOT, -1);
                Log.d(LOG_TAG,"state = " + mIndicatorState + " slotId = " + mIndicatorSlotId);
                updateDataConnPref(false);
                updateScreenStatus();
            } else if (TelephonyIntents.ACTION_SIM_INFO_UPDATE.equals(action)) {
                ///M: add for hot swap {
                mSimInfoList = SimInfoManager.getInsertedSimInfoList(MobileNetworkSettings.this);
                if (mSimInfoList != null) {
                    Log.d(LOG_TAG, "sim card number is: " + mSimInfoList.size());
                    if (mSimInfoList.size() > 0) {
                        initDataConnPref();
                        updateScreenStatus();
                    } else {
                        finish();
                    }
                }
                ///@}
            } else if (PhoneGlobals.NETWORK_MODE_CHANGE_RESPONSE.equals(action)) {
                if (!intent.getBooleanExtra(PhoneGlobals.NETWORK_MODE_CHANGE_RESPONSE, true)) {
                        int oldMode = intent.getIntExtra(PhoneGlobals.OLD_NETWORK_MODE, 0);
                        Log.d(LOG_TAG,"network mode change failed! restore the old value,oldMode = " + oldMode);
                        android.provider.Settings.Global.putInt(mPhone.getContext().getContentResolver(),
                                android.provider.Settings.Global.PREFERRED_NETWORK_MODE,
                                oldMode);
                }
            } else if (action.equals(GeminiUtils.TRANSACTION_START)) {
                updatePreference(mDataConnPref, false);
            } else if (action.equals(GeminiUtils.TRANSACTION_STOP)) {
                updatePreference(mDataConnPref, true);
            } else if (action.equals(ACTION_DATA_USAGE_DISABLED_DIALOG_OK)) {
                updateDataConnPref(true);
            }
        }
    };

    private boolean mIsChangeData = false;
    Handler mTimeoutHandler = new Handler() {
        public void handleMessage(Message msg) {
            if (msg.what == DATA_STATE_CHANGE_TIMEOUT) {
                updateDataConnPref(true);
            }
        }
    };
    /// @}

    /// M: add for support receiver & check sim lock
    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(mReceiver);
        if (mPreCheckForRunning != null) {
            mPreCheckForRunning.deRegister();
        }
        TelephonyManagerWrapper.listen(mPhoneStateListener, PhoneStateListener.LISTEN_NONE,
                PhoneWrapper.UNSPECIFIED_SLOT_ID);
    }

    /**
     * switch data connection default SIM
     *
     * @param value: sim id of the new default SIM
     */
    private void switchGprsDefaultSIM(long simid) {
        Log.d(LOG_TAG, "switchGprsDefaultSIM() with simid=" + simid);
        boolean isSimInsertedIn = GeminiUtils.UNDEFINED_SLOT_ID != GeminiUtils.getSimSlotIdBySimInfoId(simid, mSimInfoList);
        if (simid < 0 || simid > 0 && !isSimInsertedIn) {
            Log.d(LOG_TAG,"simid = " + simid + " not available anymore");
            return;
        }
        boolean isConnect = (simid > 0) ? true : false;
        if (FeatureOption.MTK_GEMINI_SUPPORT) {
            long curConSimId = Settings.System.getLong(getContentResolver(),
                Settings.System.GPRS_CONNECTION_SIM_SETTING,
                Settings.System.DEFAULT_SIM_NOT_SET);
            Log.d(LOG_TAG,"curConSimId=" + curConSimId);
            if (simid == curConSimId) {
                return;
            }
            Intent intent = new Intent(Intent.ACTION_DATA_DEFAULT_SIM_CHANGED);
            intent.putExtra("simid", simid);
            // simid>0 means one of sim card is selected
            // and <0 is close id which is -1 so mean disconnect
            sendBroadcast(intent);
            showDialog(GeminiUtils.PROGRESS_DIALOG);
            mTimeoutHandler.sendMessageDelayed(mTimeoutHandler.obtainMessage(DATA_STATE_CHANGE_TIMEOUT), 30000);
            mIsChangeData = true;
        } else {
            boolean isDataEnabled = false;
            boolean isRoamingEnabled = mPhone.getDataRoamingEnabled();
            // simid = 0 means off is clicked
            if (simid != 0) {
                isDataEnabled = true;
            }
            mExtension.dataEnableReminder(isDataEnabled, isRoamingEnabled, this);
        }
    }

    /// M: show dialogs
    @Override
    protected Dialog onCreateDialog(int id) {
        Dialog dialog = null;
        if (id == GeminiUtils.PROGRESS_DIALOG) {
            dialog = new ProgressDialog(this);
            ((ProgressDialog)dialog).setMessage(getResources().getString(R.string.updating_settings));
            dialog.setCancelable(false);
        } else if (id == DATA_ENABLE_ALERT_DIALOG
                || id == DATA_DISABLE_ALERT_DIALOG) {
            dialog = mExtension.onCreateAlertDialog(id,this,mTimeoutHandler);
            mIsChangeData = true;
        } else if (id == ROAMING_DIALOG) {
            dialog = new AlertDialog.Builder(this).setMessage(mExtension.getRoamingMessage(this, R.string.roaming_warning))
                    .setTitle(com.android.internal.R.string.dialog_alert_title)
                    .setIconAttribute(com.android.internal.R.attr.alertDialogIcon)
                    .setPositiveButton(com.android.internal.R.string.yes, this)
                    .setNegativeButton(com.android.internal.R.string.no, this)
                    .create();
            dialog.setOnDismissListener(this);
        } else if (id == DIALOG_GPRS_SWITCH_CONFIRM) {
            dialog = new AlertDialog.Builder(this)
            .setIcon(android.R.drawable.ic_dialog_alert)
            .setTitle(android.R.string.dialog_alert_title)
            .setMessage(getResources().getString(mDataSwitchMsgIndex))
            .setPositiveButton(com.android.internal.R.string.yes, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        if ((mDataSwitchMsgIndex == R.string.gemini_3g_disable_warning_case0) 
                                || (mDataSwitchMsgIndex == R.string.gemini_3g_disable_warning_case2)) {
                            enableDataRoaming(mSelectedGprsSimId);
                        }
                        switchGprsDefaultSIM(mSelectedGprsSimId);
                    }
                })
            .setNegativeButton(com.android.internal.R.string.no, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        updateDataConnPref(false);
                    }
                })
            .setOnDismissListener(new DialogInterface.OnDismissListener() {
                @Override
                public void onDismiss(DialogInterface dialog) {
                    /// M: for ALPS01035678, when phone state change from offhook to idle. update DataConnection.
                    updateDataConnPref(false);
                }
            })
            .create();
        }
        return dialog;
    }

    private void setNetworkOperator() {
        boolean isShowPlmn = false;
        isShowPlmn = PhoneWrapper.isCspPlmnEnabled(mPhone, this);
        mExtension.removeNMOp(mFragment.getPreferenceScreen(), isShowPlmn);
    }

    /// M: when receive data change broadcast get the extra    
    private static PhoneConstants.DataState getMobileDataState(Intent intent) {
        String str = intent.getStringExtra(PhoneConstants.STATE_KEY);
        if (str != null) {
            return Enum.valueOf(PhoneConstants.DataState.class, str);
        } else {
            return PhoneConstants.DataState.DISCONNECTED;
        }
    }

    /// M: add for updating screen status.
    private void updateScreenStatus() {
        boolean isIdle = (TelephonyManagerWrapper.getCallState(PhoneWrapper.UNSPECIFIED_SLOT_ID) == TelephonyManager.CALL_STATE_IDLE);
        boolean isShouldEnabled = isIdle && !GeminiUtils.isAllRadioOff(MobileNetworkSettings.this);

        mFragment.getPreferenceScreen().setEnabled(isShouldEnabled);

        if (!isShouldEnabled) {
            removeDialog(DIALOG_GPRS_SWITCH_CONFIRM);
            // When airplane mode, dismiss the mDataConnPref's select SIM Dialog.
            if (mDataConnPref.getDialog() != null) {
                mDataConnPref.getDialog().dismiss();
            }
            /// M: Delete for ALPS01266374
            //  We should update all screen.
            //return;
        }

        // update data connection preference state
        boolean isConnEnable = true;
        if (mConnService != null) {
            NetworkInfo networkInfo = mConnService.getNetworkInfo(ConnectivityManager.TYPE_MOBILE_MMS);
            if (networkInfo != null) {
                NetworkInfo.State state = networkInfo.getState();
                isConnEnable = isShouldEnabled 
                    && state != NetworkInfo.State.CONNECTING
                    && state != NetworkInfo.State.CONNECTED;
                log("mms state = " + state);
            }
        }
        updatePreference(mDataConnPref, isConnEnable);
        // add for ALPS00945889
        updateNetworkModePreference();
    }
    ///@}

    private void updatePreference(Preference preference, boolean isEnable) {
        if (preference != null) {
            preference.setEnabled(isEnable);
        }
        if (preference instanceof DialogPreference) {
            Dialog dialog = ((DialogPreference)preference).getDialog();
            if (dialog != null && dialog.isShowing()) {
                dialog.dismiss();
            }
        }
        /// M: support for CT @{
        mExtension.disableNetworkSelectionPrefs(this, mPhone, GeminiUtils.isAllRadioOff(MobileNetworkSettings.this));
        /// @}
    }

    private int dataSwitchConfirmDlgMsg(long simid) {
        SimInfoRecord siminfo = null;
        for (SimInfoRecord simInfoRecord : mSimInfoList) {
            if (simid == simInfoRecord.mSimInfoId) {
                siminfo = simInfoRecord;
            }
        }

        /// M: ALPS00676087 @{
        // When 3G service is showing and allow to manual choose, we show attention;
        boolean isManualAndSlodEnabled = mIs3GSwitchManualEnabled && mManualAllowedSlot;
        /// @}

        boolean isInRoaming = TelephonyManagerWrapper.isNetworkRoaming(siminfo.mSimSlotId);
        boolean isRoamingDataAllowed = (siminfo.mDataRoaming == SimInfoManager.DATA_ROAMING_ENABLE);
        Log.d(LOG_TAG, "isInRoaming=" + isInRoaming + " isRoamingDataAllowed="
                + isRoamingDataAllowed);

        if (isInRoaming) {
            if (!isRoamingDataAllowed) {
                if (FeatureOption.MTK_GEMINI_3G_SWITCH && isManualAndSlodEnabled) {
                    if (siminfo.mSimSlotId != GeminiUtils.get3GCapabilitySIM()) {
                        // under roaming but not abled and switch card is not 3G
                        // slot, \
                        // to pormpt user turn on roaming and how to modify to
                        // 3G service
                        return R.string.gemini_3g_disable_warning_case2;
                    } else {
                        // switch card is 3G slot but not able to roaming
                        // so only prompt to turn on roaming
                        return R.string.gemini_3g_disable_warning_case0;
                    }
                } else {
                    // no support 3G service so only prompt user to turn on
                    // roaming
                    return R.string.gemini_3g_disable_warning_case0;
                }
            } else {
                if (FeatureOption.MTK_GEMINI_3G_SWITCH && isManualAndSlodEnabled) {
                    if (siminfo.mSimSlotId != GeminiUtils.get3GCapabilitySIM()) {
                        // by support 3g switch and switched sim is not
                        // 3g slot to prompt user how to modify 3G service
                        return R.string.gemini_3g_disable_warning_case1;
                    }
                }
            }
        } else {
            if (FeatureOption.MTK_GEMINI_3G_SWITCH
                    && isManualAndSlodEnabled
                    && siminfo.mSimSlotId != GeminiUtils.get3GCapabilitySIM()) {
                // not in roaming but switched sim is not 3G
                // slot so prompt user to modify 3G service
                return R.string.gemini_3g_disable_warning_case1;
            }
        }
        return -1;
    }

    /**
     * M: enable data roaming by simId
     *
     * @param simId: simId
     */
    private boolean enableDataRoaming(long simId) {
        SimInfoRecord simInfoRecord = SimInfoManager.getSimInfoById(this, simId);
        if (simInfoRecord == null) {
            log("[enableDataRoaming] simInfoRecord ==null with SimId=" + simId);
            return false;
        }

        final int slotId = simInfoRecord.mSimSlotId;
        log("enableDataRoaming with SimId=" + simId + ", slotId=" + slotId);
        if (GeminiUtils.isValidSlot(slotId)) {
            try {
                mTelephonyManagerEx.setDataRoamingEnabled(true, slotId);
            } catch (RemoteException e) {
                log("iTelephony exception");
                return false;
            }
            SimInfoManager.setDataRoaming(this, SimInfoManager.DATA_ROAMING_ENABLE, simId);
            return true;
        }
        return false;
    }

    private void initIntentFilter() {
        /// M: for receivers sim lock gemini phone @{
        mIntentFilter = new IntentFilter(Intent.ACTION_AIRPLANE_MODE_CHANGED);
        mIntentFilter.addAction(TelephonyIntents.ACTION_ANY_DATA_CONNECTION_STATE_CHANGED);
        mIntentFilter.addAction(TelephonyIntents.ACTION_EF_CSP_CONTENT_NOTIFY);
        if (FeatureOption.MTK_GEMINI_SUPPORT) {
            mIntentFilter.addAction(Intent.ACTION_DUAL_SIM_MODE_CHANGED);
        }
        ///M: add to receiver indicator intents@{
        /**modify for consistent_UI*/
        mIntentFilter.addAction(TelephonyIntents.ACTION_SIM_INDICATOR_STATE_CHANGED);
        mIntentFilter.addAction(PhoneGlobals.NETWORK_MODE_CHANGE_RESPONSE);
        mIntentFilter.addAction(TelephonyIntents.ACTION_SIM_INFO_UPDATE);
        mIntentFilter.addAction(GeminiUtils.TRANSACTION_START);
        mIntentFilter.addAction(GeminiUtils.TRANSACTION_STOP);
        mIntentFilter.addAction(ACTION_DATA_USAGE_DISABLED_DIALOG_OK);
        ///@}
    }

    private void initPhoneAndTelephony() {
        mExtension = ExtensionManager.getInstance().getSettingsExtension();
        mPhone = PhoneGlobals.getPhone();

        /// M: init ITelephony
        mITelephony = PhoneGlobals.getInstance().phoneMgr;
        if (mITelephony == null) {
            Log.e(LOG_TAG, "ITelephony initilize failed!!!");
            // finish();
        }

        mTelephonyManagerEx = TelephonyManagerEx.getDefault();
        if (mTelephonyManagerEx == null) {
            Log.e(LOG_TAG, "get instance of TelephonyManagerEx failed!!");
        }
        /// M: add for 3G switch 2.0 @{
        mIs3GSwitchManualEnabled =  PhoneGlobals.getInstance().phoneMgrEx.is3GSwitchManualEnabled();
        mManualAllowedSlot = PhoneGlobals.getInstance().phoneMgrEx.is3GSwitchManualChange3GAllowed();
        Log.d(LOG_TAG, "mIs3GSwitchManualEnabled, mManualAllowedSlot" + mIs3GSwitchManualEnabled
            + " ," + mManualAllowedSlot);
        /// @}
        TelephonyManagerWrapper.listen(mPhoneStateListener, PhoneStateListener.LISTEN_CALL_STATE, PhoneWrapper.UNSPECIFIED_SLOT_ID);
    }

    private void initPreferenceForMobileNetwork(PreferenceScreen root) {
      /// M: support for operators @{
        mButtonPreferredNetworkMode = (ListPreference)root.findPreference(BUTTON_NETWORK_MODE_KEY);
        mCarrierSelPref = (PreferenceScreen) root.findPreference(BUTTON_CARRIER_SEL);

        //Get the networkMode from Settings.System and displays it
        if (setPreferredNetworkModeValueFromSettings() < 0) {
            android.provider.Settings.Global.putInt(mPhone.getContext().getContentResolver(),
                    android.provider.Settings.Global.PREFERRED_NETWORK_MODE,
                    preferredNetworkMode);
            if (mButtonPreferredNetworkMode != null) {
                mButtonPreferredNetworkMode.setValue(Integer.toString(preferredNetworkMode));
            }
        }

        if (!PhoneUtils.isSupportFeature("3G_SWITCH")) {
            root.removePreference(mPreference3GSwitch);
            // network mode only support 3G, when phone support gprs and edge,
            // the value of slot base band is 3, so should be MODEM_MASK_WCDMA
            if (GeminiUtils.getBaseband(0) > GeminiUtils.MODEM_3G) {
                if (GeminiUtils.isGeminiSupport()) {
                    if (mButtonPreferredNetworkMode != null) {
                        root.removePreference(mButtonPreferredNetworkMode);
                    }
                } else {
                    if (mButtonPreferredNetworkModeEx != null) {
                        root.removePreference(mButtonPreferredNetworkModeEx);
                    }
                }
            } else {
                /// M: ALPS00676897 @{
                // if phone only have 2G, remove network mode.
                root.removePreference(mButtonPreferredNetworkModeEx);
                root.removePreference(mButtonPreferredNetworkMode);
                /// @}
            }
        } else {
            root.removePreference(mButtonPreferredNetworkModeEx);
            root.removePreference(mButtonPreferredNetworkMode);
            /// M: add for 3G switch 2.0 @{
            if (!mIs3GSwitchManualEnabled) {
                root.removePreference(mPreference3GSwitch);
            }
            /// @}
        }
        if (mButtonPreferredNetworkMode != null) {
            mButtonPreferredNetworkMode.setOnPreferenceChangeListener((Preference.OnPreferenceChangeListener)mFragment);
            /// M: Add for LTE (FDD)
            setPreferredNetworkModeEntriesAndValues();
        }
        mExtension.customizeFeatureForOperator(root);
        /// @}
    }

    private void setPreferredNetworkModeEntriesAndValues() {
        /// M: Add for LTE (FDD) @{
        log("[setPreferredNetworkModeEntriesAndValues]...mSlotId " + mSlotId
                + "; is4GSimSlot(mSlotId) = " + GeminiUtils.is4GSimSlot(mSlotId));

        if (GeminiUtils.is4GSimSlot(mSlotId)) {
            mButtonPreferredNetworkMode.setEntries(
                    getResources().getStringArray(R.array.lte_prefer_network_mode_choices));
            mButtonPreferredNetworkMode.setEntryValues(
                    getResources().getStringArray(R.array.lte_prefer_network_mode_values));
        }
        /// @}
    }
    private void initCommonUI() {
        PreferenceScreen root = mFragment.getPreferenceScreen();
        mButtonDataEnabled = (CheckBoxPreference) root.findPreference(BUTTON_DATA_ENABLED_KEY);

        // add data connection for gemini sim project
        mDataConnPref = new SimSelectDialogPreference(this);
        mDataConnPref.setKey(KEY_DATA_CONN);
        mDataConnPref.setTitle(R.string.gemini_data_connection);
        mDataConnPref.setDialogTitle(R.string.gemini_data_connection);
        mDataConnPref.setOnPreferenceChangeListener((Preference.OnPreferenceChangeListener)mFragment);
        initDataConnPref();
        int dataEnabledOrder = mButtonDataEnabled.getOrder();
        mDataConnPref.setOrder(dataEnabledOrder + 1);
        root.addPreference(mDataConnPref);

        // add network mode for gemini
        Preference networkModeExPref = new Preference(this);
        networkModeExPref.setKey(BUTTON_NETWORK_MODE_EX_KEY);
        networkModeExPref.setTitle(R.string.preferred_network_mode_title);
        // Delete for Android 4.3
        //int dataUsageOrder = mButtonDataUsage.getOrder();
        //networkModeExPref.setOrder(dataUsageOrder + 1);
        root.addPreference(networkModeExPref);

        // add 3g switch
        Preference modemSwitchPref = new Preference(this);
        modemSwitchPref.setKey(BUTTON_3G_SERVICE);
        modemSwitchPref.setTitle(R.string.setting_for_3G_service);
        Intent intent3GSwitch = new Intent();
        intent3GSwitch.setClassName("com.android.phone", "com.mediatek.settings.Modem3GCapabilitySwitch");
        modemSwitchPref.setIntent(intent3GSwitch);
        root.addPreference(modemSwitchPref);

        // add PLMNList
        mPLMNPreference = new Preference(this);
        mPLMNPreference.setKey(BUTTON_PLMN_LIST);
        mPLMNPreference.setTitle(R.string.plmn_list_setting_title);
        Intent intentPlmn = new Intent();
        intentPlmn.setClassName("com.android.phone", "com.mediatek.settings.PLMNListPreference");
        mPLMNPreference.setIntent(intentPlmn);
        root.addPreference(mPLMNPreference);

        // remove the Google default data enable
        root.removePreference(mButtonDataEnabled);
    }

    /**
     * init data connection
     * @param dataConnPref
     */
    private void initDataConnPref() {
        // for data connection always add close item no matter how many sim card insert
        mSimInfoList = SimInfoManager.getInsertedSimInfoList(this);
        Collections.sort(mSimInfoList, new GeminiUtils.SIMInfoComparable());
        List<Integer> simIndicatorList = new ArrayList<Integer>();
        List<Long> entryValues = new ArrayList<Long>();
        List<Boolean> itemStatus = new ArrayList<Boolean>();
        for (SimInfoRecord siminfo : mSimInfoList) {
            simIndicatorList.add(GeminiUtils.getSimIndicator(this, siminfo.mSimSlotId));
            entryValues.add(siminfo.mSimInfoId);
            itemStatus.add(true);
        }
        List<String> normalListGprs = new ArrayList<String>();
        if (mSimInfoList.size() > 0) {
            normalListGprs.add(getString(R.string.service_3g_off));
            entryValues.add(Settings.System.GPRS_CONNECTION_SIM_SETTING_NEVER);
        }
        mDataConnPref.setEntriesData(mSimInfoList, simIndicatorList, normalListGprs, itemStatus);
        mDataConnPref.setEntryValues(entryValues);
    }

    /*
     * Update dataconnection prefe with new selected value and new sim name as
     * summary
     */
    private void updateDataConnPref(boolean isRemoveDialog) {
        if (isRemoveDialog) {
            removeDialog(GeminiUtils.PROGRESS_DIALOG);
            mIsChangeData = false;
        }
        /// M: for ALPS01040871 @{
        // when plug out sim card, maybe the siminfo is empty.
        mSimInfoList = SimInfoManager.getInsertedSimInfoList(MobileNetworkSettings.this);
        if (mSimInfoList.size() < 1) {
            PhoneLog.d(LOG_TAG, "updateDataConnPref, the siminfolist size is empty");
            return;
        }
        /// @}
        mDataConnPref.updateSimIndicator(mIndicatorSlotId, mIndicatorState);
        long simid = Settings.System.GPRS_CONNECTION_SIM_SETTING_NEVER;
        if (FeatureOption.MTK_GEMINI_SUPPORT) {
            simid = Settings.System.getLong(getContentResolver(),
                    Settings.System.GPRS_CONNECTION_SIM_SETTING,
                    Settings.System.DEFAULT_SIM_NOT_SET);
        } else {
            if (mConnService.getMobileDataEnabled()) {
                simid = mSimInfoList.get(0).mSimInfoId;
            }
        }
        Log.d(LOG_TAG, "Gprs connection SIM changed with simid is " + simid);
        mDataConnPref.setValue(simid);
    }

    /**
     * Add for update the display of network mode preference
     */
    public void updateNetworkModePreference() {
        // for ALPS01018956
        // if airplane mode is on or all SIMs closed, should also dismiss dialog
        boolean isIdle = (TelephonyManagerWrapper.getCallState(PhoneWrapper.UNSPECIFIED_SLOT_ID) == TelephonyManager.CALL_STATE_IDLE);
        boolean isNWModeEnabled = isIdle && !GeminiUtils.isAllRadioOff(MobileNetworkSettings.this)
                && isHas3GCapabilitySimCard();

        Log.d(LOG_TAG, "updateNetworkModePreference: isIdle: " + isIdle
                + "; isNWModeEnabled:" + isNWModeEnabled + "; isAllRadioOff: "
                + GeminiUtils.isAllRadioOff(MobileNetworkSettings.this));

        if (mButtonPreferredNetworkMode != null) {
            mButtonPreferredNetworkMode.setEnabled(isNWModeEnabled);
            if (!isNWModeEnabled) {
                Dialog dialog = mButtonPreferredNetworkMode.getDialog();
                if (dialog != null && dialog.isShowing()) {
                    dialog.dismiss();
                }
            }
        }

        if (mButtonPreferredNetworkModeEx != null) {
            mButtonPreferredNetworkModeEx.setEnabled(isNWModeEnabled);
        }
    }

    /**
     * Whether there has a Sim card with 3G capability
     *
     * @return result if there has no ready Sim card with 3G capability, the
     *         result will false or else will be true.
     */
    public boolean isHas3GCapabilitySimCard() {
        boolean result = false;
        if (mITelephony == null) {
            mITelephony = PhoneGlobals.getInstance().phoneMgr;
        }
        int slot = PhoneGlobals.getInstance().phoneMgrEx.get3GCapabilitySIM();
        result = ITelephonyWrapper.hasIccCard(slot) && !PhoneWrapper.isRadioOffBySlot(slot, this);
        Log.d(LOG_TAG, "isHas3GCapabilitySimCard slot:"+ slot +"result"+result);
        return result;
    }

    /**
     * Change the intent value for LTE case
     * @param intent the intent will send to MultipleSimActivity
     * @return the intent value with suitable INIT_ARRAY & INIT_ARRAY_VALUE
     */
    private Intent setPreferredNetworkModeExEntriesAndValues(Intent intent, int slotId) {
        log("[setPreferredNetworkModeExEntriesAndValues]...mSlotId " + slotId
                + "; is4GSimSlot(mSlotId) = " + GeminiUtils.is4GSimSlot(slotId));

        if (GeminiUtils.is4GSimSlot(slotId)) {
            intent.putExtra(MultipleSimActivity.INIT_ARRAY, R.array.lte_prefer_network_mode_choices);
            intent.putExtra(MultipleSimActivity.INIT_ARRAY_VALUE, R.array.lte_prefer_network_mode_values);
        } else {
            intent.putExtra(MultipleSimActivity.INIT_ARRAY, R.array.gsm_umts_network_preferences_choices);
            intent.putExtra(MultipleSimActivity.INIT_ARRAY_VALUE, R.array.gsm_umts_network_preferences_values);
        }
        return intent;
    }

    /**
     * Displays the value taken from the Settings.System
     * @return the network mode from settings system
     */
    private int setPreferredNetworkModeValueFromSettings() {
        //Get the networkMode from Settings.System and displays it
        if (mButtonPreferredNetworkMode != null) {
            int settingsNetworkMode = android.provider.Settings.Global.getInt(mPhone.getContext().
                    getContentResolver(),android.provider.Settings.Global.PREFERRED_NETWORK_MODE,
                    preferredNetworkMode);
            log("settingsNetworkMode = " + settingsNetworkMode);
            mButtonPreferredNetworkMode.setValue(Integer.toString(settingsNetworkMode));
            return settingsNetworkMode;
        } else {
            log("mButtonPreferredNetworkMode == null");
            return -1;
        }
    }

    /**
     * M: Displays the value taken from the Settings.System
     * @return the network mode from settings system
     */
    private int setNetworkModeValueFromSettings() {
        //Get the networkMode from Settings.System and displays it
        if (mButtonEnabledNetworks != null) {
            int settingsNetworkMode = android.provider.Settings.Global.getInt(
                    mPhone.getContext().getContentResolver(),
                    android.provider.Settings.Global.PREFERRED_NETWORK_MODE,
                    preferredNetworkMode);
            if (DBG) log("settingsNetworkMode: " + settingsNetworkMode);
            mButtonEnabledNetworks.setValue(Integer.toString(settingsNetworkMode));
            return settingsNetworkMode;
        } else {
            log("mButtonEnabledNetworks == null");
            return -1;
        }
    }
}
