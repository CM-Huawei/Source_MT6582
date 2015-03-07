/*
 * Copyright (C) 2008 The Android Open Source Project
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

package com.android.settings;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Build;
import android.os.Bundle;
import android.os.SELinux;
import android.os.SystemClock;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.preference.Preference;
import android.preference.PreferenceGroup;
import android.preference.PreferenceScreen;
import android.util.Log;
import android.widget.Toast;

import com.android.internal.telephony.PhoneConstants;

import com.mediatek.common.featureoption.FeatureOption;
import com.mediatek.settings.ext.IDeviceInfoSettingsExt;
import com.mediatek.xlog.Xlog;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DeviceInfoSettings extends RestrictedSettingsFragment {

    private static final String LOG_TAG = "DeviceInfoSettings";
    private static final String MTK_SYSTEM_UPDATE_LOG_TAG = "SystemUpdate/Settings";

    private static final String FILENAME_PROC_VERSION = "/proc/version";
    private static final String FILENAME_MSV = "/sys/board_properties/soc/msv";

    private static final String KEY_CONTAINER = "container";
    private static final String KEY_TEAM = "team";
    private static final String KEY_CONTRIBUTORS = "contributors";
    private static final String KEY_REGULATORY_INFO = "regulatory_info";
    private static final String KEY_TERMS = "terms";
    private static final String KEY_LICENSE = "license";
    private static final String KEY_COPYRIGHT = "copyright";
    private static final String KEY_SYSTEM_UPDATE_SETTINGS = "system_update_settings";
    private static final String PROPERTY_URL_SAFETYLEGAL = "ro.url.safetylegal";
    private static final String PROPERTY_SELINUX_STATUS = "ro.build.selinux";
    private static final String KEY_KERNEL_VERSION = "kernel_version";
    private static final String KEY_BUILD_NUMBER = "build_number";
    private static final String KEY_DEVICE_MODEL = "device_model";
    private static final String KEY_SELINUX_STATUS = "selinux_status";
    private static final String KEY_BASEBAND_VERSION = "baseband_version";
    private static final String KEY_BASEBAND_VERSION_2 = "baseband_version_2";
    private static final String KEY_FIRMWARE_VERSION = "firmware_version";
    private static final String KEY_SCOMO = "scomo";
    private static final String KEY_MDM_SCOMO = "mdm_scomo";
    private static final String KEY_UPDATE_SETTING = "additional_system_update_settings";
    private static final String KEY_EQUIPMENT_ID = "fcc_equipment_id";
    private static final String PROPERTY_EQUIPMENT_ID = "ro.ril.fccid";
    private static final String KEY_DMSW_UPDATE = "software_update";
    private static final String KEY_MDM_FUMO = "mdm_fumo";
    private static final String KEY_SOFTWARE_UPDATE = "more_software_updates";
    //status info key
    private static final String KEY_STATUS_INFO = "status_info";
    private static final String KEY_STATUS_INFO_GEMINI = "status_info_gemini";
    //custom build version
    private static final String PROPERTY_BUILD_VERSION_CUSTOM = "ro.custom.build.version";
    //mtk system update info
    private static final String KEY_MTK_SYSTEM_UPDATE_SETTINGS = "mtk_system_update";

    IDeviceInfoSettingsExt mExt;
    /// M: CT E push feature
    private static final String KEY_CDMA_EPUSH = "cdma_epush";

    static final int TAPS_TO_BE_A_DEVELOPER = 7;

    long[] mHits = new long[3];
    int mDevHitCountdown;
    Toast mDevHitToast;

    public DeviceInfoSettings() {
        super(null /* Don't PIN protect the entire screen */);
    }

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        addPreferencesFromResource(R.xml.device_info_settings);

        // We only call ensurePinRestrictedPreference() when mDevHitCountdown == 0.
        // This will keep us from entering developer mode without a PIN.
        protectByRestrictions(KEY_BUILD_NUMBER);

      
         //M: initilise plug in
        mExt = Utils.getDeviceInfoSettingsPlugin(getActivity());

        setStringSummary(KEY_FIRMWARE_VERSION, Build.VERSION.RELEASE);
        findPreference(KEY_FIRMWARE_VERSION).setEnabled(true);
      
        /// M: update baseband version,baseversion shows GSM phone , version2 shows CDMA phone{@
        /// use flag hasExternalModem to judge if has external modem , not use "FeatureOption.MTK_DT_SUPPORT"
        String baseversion = "gsm.version.baseband"; 
        int modemSlot = getExternalModemSlot();
        boolean hasExternalModem = modemSlot != 0; 
        if (hasExternalModem && !FeatureOption.PURE_AP_USE_EXTERNAL_MODEM) {
           if (modemSlot == 1) {
               baseversion = "gsm.version.baseband.2";
           }
        }
        Log.d(LOG_TAG,"baseversion=" + baseversion);
        setValueSummary(KEY_BASEBAND_VERSION, baseversion);

        if (hasExternalModem && !FeatureOption.PURE_AP_USE_EXTERNAL_MODEM) {
            String version2 = "gsm.version.baseband.2";
            if (FeatureOption.EVDO_DT_SUPPORT) {
                version2 = "cdma.version.baseband";
            } else {
                if (modemSlot == 1) {
                    version2 = "gsm.version.baseband";
                }
            }
            Log.i(LOG_TAG, "version2=" + version2);
            setValueSummary(KEY_BASEBAND_VERSION_2, version2);
            updateBasebandTitle();
        } else {
            getPreferenceScreen().removePreference(
                    findPreference(KEY_BASEBAND_VERSION_2));
        }
        ///@}
        setStringSummary(KEY_DEVICE_MODEL, Build.MODEL + getMsvSuffix());
        setValueSummary(KEY_EQUIPMENT_ID, PROPERTY_EQUIPMENT_ID);
        setStringSummary(KEY_DEVICE_MODEL, Build.MODEL);
        setStringSummary(KEY_BUILD_NUMBER, Build.DISPLAY);
        findPreference(KEY_BUILD_NUMBER).setEnabled(true);
        findPreference(KEY_KERNEL_VERSION).setSummary(getFormattedKernelVersion());

        if (!SELinux.isSELinuxEnabled()) {
            String status = getResources().getString(R.string.selinux_status_disabled);
            setStringSummary(KEY_SELINUX_STATUS, status);
        } else if (!SELinux.isSELinuxEnforced()) {
            String status = getResources().getString(R.string.selinux_status_permissive);
            setStringSummary(KEY_SELINUX_STATUS, status);
        }

        // Remove selinux information if property is not present
        removePreferenceIfPropertyMissing(getPreferenceScreen(), KEY_SELINUX_STATUS,
                PROPERTY_SELINUX_STATUS);

        ///M: add to show customer build version
        setValueSummary("custom_build_version", PROPERTY_BUILD_VERSION_CUSTOM);
        
        // Remove Safety information preference if PROPERTY_URL_SAFETYLEGAL is not set
        removePreferenceIfPropertyMissing(getPreferenceScreen(), "safetylegal",
                PROPERTY_URL_SAFETYLEGAL);

        // Remove Equipment id preference if FCC ID is not set by RIL
        removePreferenceIfPropertyMissing(getPreferenceScreen(), KEY_EQUIPMENT_ID,
                PROPERTY_EQUIPMENT_ID);

        // Remove Baseband version if wifi-only device
        if (Utils.isWifiOnly(getActivity())) {
            getPreferenceScreen().removePreference(findPreference(KEY_BASEBAND_VERSION));
        }

        /*
         * Settings is a generic app and should not contain any device-specific
         * info.
         */
        final Activity act = getActivity();
        // These are contained in the "container" preference group
        PreferenceGroup parentPreference = (PreferenceGroup) findPreference(KEY_CONTAINER);
        Utils.updatePreferenceToSpecificActivityOrRemove(act, parentPreference, KEY_TERMS,
                Utils.UPDATE_PREFERENCE_FLAG_SET_TITLE_TO_MATCHING_ACTIVITY);
        Utils.updatePreferenceToSpecificActivityOrRemove(act, parentPreference, KEY_LICENSE,
                Utils.UPDATE_PREFERENCE_FLAG_SET_TITLE_TO_MATCHING_ACTIVITY);
        Utils.updatePreferenceToSpecificActivityOrRemove(act, parentPreference, KEY_COPYRIGHT,
                Utils.UPDATE_PREFERENCE_FLAG_SET_TITLE_TO_MATCHING_ACTIVITY);
        Utils.updatePreferenceToSpecificActivityOrRemove(act, parentPreference, KEY_TEAM,
                Utils.UPDATE_PREFERENCE_FLAG_SET_TITLE_TO_MATCHING_ACTIVITY);

        // These are contained by the root preference screen
        parentPreference = getPreferenceScreen();

        boolean hasSpecial = false;
        ///M: when user is not owner,can't show "system update" @{
        Xlog.d(LOG_TAG, "UserHandle.myUserId() = " + UserHandle.myUserId());
        if (FeatureOption.MTK_SYSTEM_UPDATE_SUPPORT && UserHandle.myUserId() == UserHandle.USER_OWNER) {
        /// @}
            hasSpecial = updatePreferenceToSpecificActivity(act,
                    parentPreference, KEY_MTK_SYSTEM_UPDATE_SETTINGS);

        } else {
            Preference preference = parentPreference.findPreference(KEY_MTK_SYSTEM_UPDATE_SETTINGS);
            if (preference != null) {
                parentPreference.removePreference(preference);
            }
        }
        Log.i(MTK_SYSTEM_UPDATE_LOG_TAG, "DeviceInfoSettings:Stop, hasSpecial = " + hasSpecial);

        if (UserHandle.myUserId() == UserHandle.USER_OWNER) {
            Utils.updatePreferenceToSpecificActivityOrRemove(act, parentPreference,
                    KEY_SYSTEM_UPDATE_SETTINGS,
                    Utils.UPDATE_PREFERENCE_FLAG_SET_TITLE_TO_MATCHING_ACTIVITY);
        } else {
            // Remove for secondary users
            removePreference(KEY_SYSTEM_UPDATE_SETTINGS);
        }
        Utils.updatePreferenceToSpecificActivityOrRemove(act, parentPreference, KEY_CONTRIBUTORS,
                Utils.UPDATE_PREFERENCE_FLAG_SET_TITLE_TO_MATCHING_ACTIVITY);

        // Read platform settings for additional system update setting
        removePreferenceIfBoolFalse(KEY_UPDATE_SETTING,
                R.bool.config_additional_system_update_setting_enable);

        // Remove regulatory information if not enabled.
        removePreferenceIfBoolFalse(KEY_REGULATORY_INFO,
                R.bool.config_show_regulatory_info);

        ///M: DM SCOMO @{
        if (!FeatureOption.MTK_SCOMO_ENTRY) {
            Preference scomoPreference = findPreference(KEY_SCOMO);
            if (scomoPreference != null) {
                getPreferenceScreen().removePreference(scomoPreference);
            }
        }
        if (!FeatureOption.MTK_MDM_SCOMO) {
            Preference scomoPreference = findPreference(KEY_MDM_SCOMO);
            if (scomoPreference != null) {
                getPreferenceScreen().removePreference(scomoPreference);
            }
        }
        if (!FeatureOption.MTK_GEMINI_SUPPORT) {
            //delete the Gemini preference if it is single sim
            parentPreference.removePreference(findPreference(KEY_STATUS_INFO_GEMINI));

            mExt.initSummary(findPreference(KEY_STATUS_INFO));
        } else {
            //if it is Gemini, then delete the single preference
            parentPreference.removePreference(findPreference(KEY_STATUS_INFO));

            mExt.initSummary(findPreference(KEY_STATUS_INFO_GEMINI));
        }
        ///@}
        if (!FeatureOption.MTK_FOTA_ENTRY) {
            parentPreference.removePreference(findPreference(KEY_DMSW_UPDATE));
        }
        if (!FeatureOption.MTK_MDM_FUMO) {
            parentPreference.removePreference(findPreference(KEY_MDM_FUMO));
        }
        /// M: CT E push feature refactory,add CT E push feature in CT project
        mExt.addEpushPreference(getPreferenceScreen());
        softwareUpdatePreference();
    }

    @Override
    public void onResume() {
        super.onResume();
        PreferenceGroup parentPreference = getPreferenceScreen();
        mDevHitCountdown = getActivity().getSharedPreferences(DevelopmentSettings.PREF_FILE,
                Context.MODE_PRIVATE).getBoolean(DevelopmentSettings.PREF_SHOW,
                        android.os.Build.TYPE.equals("eng")) ? -1 : TAPS_TO_BE_A_DEVELOPER;
        mDevHitToast = null;
    }
    @Override
    public void onDestroy() {
        super.onDestroy();

    }

    @Override
    public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen, Preference preference) {
        if (preference.getKey().equals(KEY_FIRMWARE_VERSION)) {
            System.arraycopy(mHits, 1, mHits, 0, mHits.length-1);
            mHits[mHits.length-1] = SystemClock.uptimeMillis();
            if (mHits[0] >= (SystemClock.uptimeMillis()-500)) {
                Intent intent = new Intent(Intent.ACTION_MAIN);
                intent.setClassName("android",
                        com.android.internal.app.PlatLogoActivity.class.getName());
                try {
                    startActivity(intent);
                } catch (Exception e) {
                    Log.e(LOG_TAG, "Unable to start activity " + intent.toString());
                }
            }
        } else if (preference.getKey().equals(KEY_BUILD_NUMBER)) {
            // Don't enable developer options for secondary users.
            if (UserHandle.myUserId() != UserHandle.USER_OWNER) return true;

            if (mDevHitCountdown > 0) {
                if (mDevHitCountdown == 1) {
                    if (super.ensurePinRestrictedPreference(preference)) {
                        return true;
                    }
                }
                mDevHitCountdown--;
                if (mDevHitCountdown == 0) {
                    getActivity().getSharedPreferences(DevelopmentSettings.PREF_FILE,
                            Context.MODE_PRIVATE).edit().putBoolean(
                                    DevelopmentSettings.PREF_SHOW, true).apply();
                    if (mDevHitToast != null) {
                        mDevHitToast.cancel();
                    }
                    mDevHitToast = Toast.makeText(getActivity(), R.string.show_dev_on,
                            Toast.LENGTH_LONG);
                    mDevHitToast.show();
                } else if (mDevHitCountdown > 0
                        && mDevHitCountdown < (TAPS_TO_BE_A_DEVELOPER-2)) {
                    if (mDevHitToast != null) {
                        mDevHitToast.cancel();
                    }
                    mDevHitToast = Toast.makeText(getActivity(), getResources().getQuantityString(
                            R.plurals.show_dev_countdown, mDevHitCountdown, mDevHitCountdown),
                            Toast.LENGTH_SHORT);
                    mDevHitToast.show();
                }
            } else if (mDevHitCountdown < 0) {
                if (mDevHitToast != null) {
                    mDevHitToast.cancel();
                }
                mDevHitToast = Toast.makeText(getActivity(), R.string.show_dev_already,
                        Toast.LENGTH_LONG);
                mDevHitToast.show();
            }
        } else if (preference.getKey().equals(KEY_DMSW_UPDATE)) {
            // /M: for DMSW to broadcast @{
            Intent i = new Intent();
            i.setAction("com.mediatek.DMSWUPDATE");
            getActivity().sendBroadcast(i);
            // /@}
        } else if (preference.getKey().equals(KEY_MDM_FUMO)) {
            // /M: for DMSW to broadcast @{
            Intent i = new Intent();
            i.setAction("com.mediatek.DMSWUPDATE");
            getActivity().sendBroadcast(i);
            // /@}
        } else if (preference.getKey().equals(KEY_CDMA_EPUSH)) {
            /// M: CT E push feature @{
            Intent intent = new Intent(Intent.ACTION_MAIN, null);
            ComponentName cn = new ComponentName("com.ctc.epush", "com.ctc.epush.IndexActivity");            
            intent.setComponent(cn);
            if (getPackageManager().resolveActivity(intent, 0) != null) {
                startActivity(intent);
            } else {
                Log.e(LOG_TAG, "Unable to start activity " + intent.toString());
            }
            /// @}
        }
        return super.onPreferenceTreeClick(preferenceScreen, preference);
    }

    private void removePreferenceIfPropertyMissing(PreferenceGroup preferenceGroup,
            String preference, String property ) {
        if (SystemProperties.get(property).equals("")) {
            // Property is missing so remove preference from group
            try {
                preferenceGroup.removePreference(findPreference(preference));
            } catch (RuntimeException e) {
                Log.d(LOG_TAG, "Property '" + property + "' missing and no '"
                        + preference + "' preference");
            }
        }
    }

    private void removePreferenceIfBoolFalse(String preference, int resId) {
        if (!getResources().getBoolean(resId)) {
            Preference pref = findPreference(preference);
            if (pref != null) {
                getPreferenceScreen().removePreference(pref);
            }
        }
    }

    private void setStringSummary(String preference, String value) {
        try {
            findPreference(preference).setSummary(value);
        } catch (RuntimeException e) {
            findPreference(preference).setSummary(
                getResources().getString(R.string.device_info_default));
        }
    }

    private void setValueSummary(String preference, String property) {
        try {
            findPreference(preference).setSummary(
                    SystemProperties.get(property,
                            getResources().getString(R.string.device_info_default)));
        } catch (RuntimeException e) {
            // No recovery
        }
    }

    /**
     * Reads a line from the specified file.
     * @param filename the file to read from
     * @return the first line, if any.
     * @throws IOException if the file couldn't be read
     */
    private static String readLine(String filename) throws IOException {
        BufferedReader reader = new BufferedReader(new FileReader(filename), 256);
        try {
            return reader.readLine();
        } finally {
            reader.close();
        }
    }

    public static String getFormattedKernelVersion() {
        try {
            return formatKernelVersion(readLine(FILENAME_PROC_VERSION));

        } catch (IOException e) {
            Log.e(LOG_TAG,
                "IO Exception when getting kernel version for Device Info screen",
                e);

            return "Unavailable";
        }
    }

    public static String formatKernelVersion(String rawKernelVersion) {
        // Example (see tests for more):
        // Linux version 3.0.31-g6fb96c9 (android-build@xxx.xxx.xxx.xxx.com) \
        //     (gcc version 4.6.x-xxx 20120106 (prerelease) (GCC) ) #1 SMP PREEMPT \
        //     Thu Jun 28 11:02:39 PDT 2012

        final String PROC_VERSION_REGEX =
            "Linux version (\\S+) " + /* group 1: "3.0.31-g6fb96c9" */
            "\\((\\S+?)\\) " +        /* group 2: "x@y.com" (kernel builder) */
            "(?:\\(gcc.+? \\)) " +    /* ignore: GCC version information */
            "(#\\d+) " +              /* group 3: "#1" */
            "(?:.*?)?" +              /* ignore: optional SMP, PREEMPT, and any CONFIG_FLAGS */
            "((Sun|Mon|Tue|Wed|Thu|Fri|Sat).+)"; /* group 4: "Thu Jun 28 11:02:39 PDT 2012" */

        Matcher m = Pattern.compile(PROC_VERSION_REGEX).matcher(rawKernelVersion);
        if (!m.matches()) {
            Log.e(LOG_TAG, "Regex did not match on /proc/version: " + rawKernelVersion);
            return "Unavailable";
        } else if (m.groupCount() < 4) {
            Log.e(LOG_TAG, "Regex match on /proc/version only returned " + m.groupCount()
                    + " groups");
            return "Unavailable";
        }
        return m.group(1) + "\n" +                 // 3.0.31-g6fb96c9
            m.group(2) + " " + m.group(3) + "\n" + // x@y.com #1
            m.group(4);                            // Thu Jun 28 11:02:39 PDT 2012
    }

    /**
     * Returns " (ENGINEERING)" if the msv file has a zero value, else returns "".
     * @return a string to append to the model number description.
     */
    private String getMsvSuffix() {
        // Production devices should have a non-zero value. If we can't read it, assume it's a
        // production device so that we don't accidentally show that it's an ENGINEERING device.
        try {
            String msv = readLine(FILENAME_MSV);
            // Parse as a hex number. If it evaluates to a zero, then it's an engineering build.
            if (Long.parseLong(msv, 16) == 0) {
                return " (ENGINEERING)";
            }
        } catch (IOException ioe) {
            // Fail quietly, as the file may not exist on some devices.
        } catch (NumberFormatException nfe) {
            // Fail quietly, returning empty string should be sufficient
        }
        return "";
    }

    /*
    *M: for support gemini feature and C+D two 
    *   moderm
    */
    private void updateBasebandTitle() {
        String basebandversion = getString(R.string.baseband_version);
        String slot1;
        String slot2;
        if (FeatureOption.EVDO_DT_SUPPORT) {
            Locale tr = Locale.getDefault();// For chinese there is no space
            slot1 = "GSM " + basebandversion;
            slot2 = "CDMA " + basebandversion;
            if (tr.getCountry().equals(Locale.CHINA.getCountry())
                    || tr.getCountry().equals(Locale.TAIWAN.getCountry())) {
                slot1 = slot1.replace("GSM ", "GSM");
                slot2 = slot2.replace("CDMA ", "CDMA");// delete the space
            }
        } else {
            slot1 = getString(R.string.status_imei_slot1);
            slot1 = basebandversion
                    + slot1.replace(getString(R.string.status_imei), " ");
            slot2 = getString(R.string.status_imei_slot2);
            slot2 = basebandversion
                    + slot2.replace(getString(R.string.status_imei), " ");
        }
        findPreference(KEY_BASEBAND_VERSION).setTitle(slot1);
        findPreference(KEY_BASEBAND_VERSION_2).setTitle(slot2);
    }

    private int getExternalModemSlot() {
        int modemSlot;
        String md = SystemProperties.get("ril.external.md",
                    getResources().getString(R.string.device_info_default));
        if (md.equals(getResources().getString(R.string.device_info_default))) {
            modemSlot = PhoneConstants.GEMINI_SIM_1;
        } else {
            modemSlot = Integer.valueOf(md).intValue();
        }
        Log.d(LOG_TAG,"modemSlot = " + modemSlot);
        return modemSlot;
    }

    private void softwareUpdatePreference() {
        Log.i(LOG_TAG, "softwareUpdatePreference"
                + "FeatureOption.MTK_SYSTEM_UPDATE_SUPPORT="
                + FeatureOption.MTK_SYSTEM_UPDATE_SUPPORT
                + " FeatureOption.MTK_FOTA_ENTRY="
                + FeatureOption.MTK_FOTA_ENTRY
                + " FeatureOption.MTK_SCOMO_ENTRY="
                + FeatureOption.MTK_SCOMO_ENTRY
                + " FeatureOption.MTK_MDM_FUMO="
                + FeatureOption.MTK_MDM_FUMO
                + " FeatureOption.MTK_MDM_SCOMO="
                + FeatureOption.MTK_MDM_SCOMO);
        PreferenceGroup parentPreference = getPreferenceScreen();
        if (FeatureOption.MTK_SYSTEM_UPDATE_SUPPORT
                && parentPreference.findPreference(KEY_MTK_SYSTEM_UPDATE_SETTINGS) != null) {

            if ((!FeatureOption.MTK_FOTA_ENTRY && !FeatureOption.MTK_MDM_FUMO)
                    && (!FeatureOption.MTK_SCOMO_ENTRY && !FeatureOption.MTK_MDM_SCOMO)
                    && parentPreference.findPreference(KEY_SYSTEM_UPDATE_SETTINGS) == null) {
                Log.i(LOG_TAG, "Remove software updates item as no item available");
                parentPreference.removePreference(findPreference(KEY_SOFTWARE_UPDATE));
                return;
            }
            if (parentPreference.findPreference(KEY_DMSW_UPDATE) != null) {
                Log.i(LOG_TAG, "Remove fota");
                parentPreference
                        .removePreference(findPreference(KEY_DMSW_UPDATE));
            }
            if (parentPreference.findPreference(KEY_MDM_FUMO) != null) {
                Log.i(LOG_TAG, "Remove fota");
                parentPreference
                        .removePreference(findPreference(KEY_MDM_FUMO));
            }
            if (parentPreference.findPreference(KEY_SCOMO) != null) {
                Log.i(LOG_TAG, "Remove scomo");
                parentPreference.removePreference(findPreference(KEY_SCOMO));
            }
            if (parentPreference.findPreference(KEY_MDM_SCOMO) != null) {
                Log.i(LOG_TAG, "Remove scomo");
                parentPreference.removePreference(findPreference(KEY_MDM_SCOMO));
            }
            if (parentPreference.findPreference(KEY_SYSTEM_UPDATE_SETTINGS) != null) {
                Log.i(LOG_TAG, "Remove GMS");
                parentPreference
                        .removePreference(findPreference(KEY_SYSTEM_UPDATE_SETTINGS));
            }
        } else {
            Log.i(LOG_TAG, "Remove software updates item");
            parentPreference
                    .removePreference(findPreference(KEY_SOFTWARE_UPDATE));
        }
    }

    private boolean updatePreferenceToSpecificActivity(Context context,
            PreferenceGroup parentPreferenceGroup, String preferenceKey) {

        Preference preference = parentPreferenceGroup.findPreference(preferenceKey);
        if (preference == null) {
            return false;
        }

        Intent intent = preference.getIntent();
        if (intent != null) {
            // Find the activity that is in the system image
            PackageManager pm = context.getPackageManager();
            Log.i(MTK_SYSTEM_UPDATE_LOG_TAG, "DeviceInfoSettings:intent.getAction() = " + intent.getAction());
            List<ResolveInfo> list = pm.queryIntentActivities(intent, 0);
            int listSize = list.size();
            Log.i(MTK_SYSTEM_UPDATE_LOG_TAG, "DeviceInfoSettings:listSize = " + listSize);
            for (int i = 0; i < listSize; i++) {
                ResolveInfo resolveInfo = list.get(i);
                Log.i(MTK_SYSTEM_UPDATE_LOG_TAG, "DeviceInfoSettings:resolveInfo.activityInfo.packageName = "
                        + resolveInfo.activityInfo.packageName);
                boolean is = resolveInfo.activityInfo.name.equals("com.mediatek.systemupdate.MainEntry");
                Log.i(MTK_SYSTEM_UPDATE_LOG_TAG, "DeviceInfoSettings:is = " + is);
                if (!is) continue;
                // Replace the intent with this specific activity
                preference.setIntent(new Intent().setClassName(
                        resolveInfo.activityInfo.packageName,
                        resolveInfo.activityInfo.name));

                // Set the preference title to the activity's label
                preference.setTitle(resolveInfo.loadLabel(pm));

                return true;
            }
        }
        parentPreferenceGroup.removePreference(preference);
        return false;
    }
}
