/*
 * Copyright (C) 2009 The Android Open Source Project
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
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.admin.DevicePolicyManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.hardware.usb.UsbManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.nfc.NfcAdapter;
import android.os.Bundle;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.preference.CheckBoxPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.PreferenceScreen;
//M: ADD @{
import android.preference.PreferenceActivity;
//M: }
import android.provider.Settings;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager; 
import android.text.TextUtils;
import android.util.Log;

import com.android.internal.telephony.ITelephony;
import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.SmsApplication;
import com.android.internal.telephony.SmsApplication.SmsApplicationData;
import com.android.internal.telephony.TelephonyIntents;
import com.android.internal.telephony.TelephonyProperties;
import com.android.settings.nfc.NfcEnabler;
//M: ADD @{
import com.android.settings.nfc.AndroidBeam;
import com.mediatek.nfc.MtkNfcEnabler;
import com.mediatek.nfc.NfcPreference;
import com.mediatek.nfc.NfcSettings;
//M: }
import com.mediatek.common.featureoption.FeatureOption;
import com.mediatek.telephony.SimInfoManager;
import com.mediatek.telephony.SimInfoManager.SimInfoRecord;
import com.mediatek.wireless.UsbSharingChoose;
import com.mediatek.xlog.Xlog;

import java.util.Collection;
import java.util.List;

public class WirelessSettings extends RestrictedSettingsFragment 
        implements Preference.OnPreferenceChangeListener {

    private static final String TAG = "WirelessSettings";
    private static final String KEY_TOGGLE_AIRPLANE = "toggle_airplane";
    private static final String KEY_TOGGLE_NFC = "toggle_nfc";
    //M: ADD @{
    private static final String KEY_MTK_TOGGLE_NFC = "toggle_mtk_nfc";
    //M: }
    private static final String KEY_WIMAX_SETTINGS = "wimax_settings";
    private static final String KEY_ANDROID_BEAM_SETTINGS = "android_beam_settings";
    private static final String KEY_VPN_SETTINGS = "vpn_settings";
    private static final String KEY_TETHER_SETTINGS = "tether_settings";
    private static final String KEY_PROXY_SETTINGS = "proxy_settings";
    private static final String KEY_MOBILE_NETWORK_SETTINGS = "mobile_network_settings";
    private static final String KEY_MANAGE_MOBILE_PLAN = "manage_mobile_plan";
    private static final String KEY_SMS_APPLICATION = "sms_application";
    private static final String KEY_TOGGLE_NSD = "toggle_nsd"; //network service discovery
    private static final String KEY_CELL_BROADCAST_SETTINGS = "cell_broadcast_settings";

    /// M: @{
    private static final String RCSE_SETTINGS_INTENT = "com.mediatek.rcse.RCSE_SETTINGS";
    private static final String KEY_RCSE_SETTINGS = "rcse_settings";

    private static final String KEY_USB_SHARING = "usb_sharing";
    private static final String USB_DATA_STATE = "mediatek.intent.action.USB_DATA_STATE";
    /// @}

    public static final String EXIT_ECM_RESULT = "exit_ecm_result";
    public static final int REQUEST_CODE_EXIT_ECM = 1;

    private AirplaneModeEnabler mAirplaneModeEnabler;
    private CheckBoxPreference mAirplaneModePreference;
    private NfcEnabler mNfcEnabler;
    //M: ADD @{
    private MtkNfcEnabler mMTKNfcEnabler;
    private NfcPreference mNfcPreference;
    //M: }
    private NfcAdapter mNfcAdapter;
    private NsdEnabler mNsdEnabler;

    /// M: @{
    private PreferenceScreen mNetworkSettingsPreference;
    //Gemini phone instance
    //In order to do not run with phone process
    private ITelephony mTelephony;

    private CheckBoxPreference mUsbSharing;
    private ConnectivityManager mConnectivityManager;
    private IntentFilter mIntentFilter;
    private Preference mTetherSettings;
    /// @}
    
    private ConnectivityManager mCm;
    private TelephonyManager mTm;

    private static final int MANAGE_MOBILE_PLAN_DIALOG_ID = 1;
    private static final String SAVED_MANAGE_MOBILE_PLAN_MSG = "mManageMobilePlanMessage";
   
    private SmsListPreference mSmsApplicationPreference;
     
    public WirelessSettings() {
        super(null);
    }

    /// M: the feature of manage mobile plan removed temporarily @{
    private boolean mDisableMobilePlan = true;
    /// @}
    
    /**
     * M: USB internet sharing connect state receiver
     */
    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            String dataApnKey = intent.getStringExtra(PhoneConstants.DATA_APN_KEY);
            if (USB_DATA_STATE.equals(action) 
                    && "internet".equals(dataApnKey)) {
                PhoneConstants.DataState state = Enum.valueOf(PhoneConstants.DataState.class,
                        intent.getStringExtra(PhoneConstants.STATE_KEY));
                Xlog.d(TAG, "receive USB_DATA_STATE");
                Xlog.d(TAG, "dataApnKey = " + dataApnKey + ", state = " + state);
                switch (state) {
                    case CONNECTING:
                        mUsbSharing.setEnabled(false);
                        mUsbSharing.setChecked(false);
                        mUsbSharing.setSummary(R.string.radioInfo_data_connecting);
                        break;
                    case CONNECTED:
                        mUsbSharing.setEnabled(true);
                        mUsbSharing.setChecked(true);
                        mUsbSharing.setSummary(R.string.radioInfo_data_connected);
                        break;
                    case SUSPENDED:
                        break;
                    case DISCONNECTED:
                        mUsbSharing.setEnabled(true);
                        mUsbSharing.setChecked(false);
                        mUsbSharing.setSummary(R.string.usb_sharing_summary);
                        break;
                    default:
                        break;
                }
            } else if (action.equals(UsbManager.ACTION_USB_STATE)) {
                boolean usbConnected = intent.getBooleanExtra(UsbManager.USB_CONNECTED, false);
                boolean usbTethered = false;
                String[] tethered = mConnectivityManager.getTetheredIfaces();
                String[] usbRegexs = mConnectivityManager.getTetherableUsbRegexs();
                for (String s : tethered) {
                    for (String regex : usbRegexs) {
                        if (s.matches(regex)) { 
                            usbTethered = true;
                        }
                    }
                }
                Xlog.d(TAG, "onReceive: ACTION_USB_STATE usbConnected:" +
                    usbConnected + " usbTethered:" + usbTethered);
                if (!mUsbSharing.isChecked()) {
                    mUsbSharing.setEnabled(usbConnected && !usbTethered);
                }
            }  else if (action.equals(TelephonyIntents.ACTION_SIM_INFO_UPDATE)) {
                ///M: add for hot swap {
                Xlog.d(TAG, "ACTION_SIM_INFO_UPDATE received");
                List<SimInfoRecord> simList = SimInfoManager.getInsertedSimInfoList(getActivity());
                if (simList != null) {
                    Xlog.d(TAG, "sim card number is: " + simList.size());
                    mNetworkSettingsPreference.setEnabled(simList.size() > 0);
                }
                ///@}
            }
        }
    };

    /**
     * Invoked on each preference click in this hierarchy, overrides
     * PreferenceActivity's implementation.  Used to make sure we track the
     * preference click events.
     */
    @Override
    public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen, Preference preference) {
        if (ensurePinRestrictedPreference(preference)) {
            return true;
        }
        log("onPreferenceTreeClick: preference=" + preference);
        if (preference == mAirplaneModePreference && Boolean.parseBoolean(
                SystemProperties.get(TelephonyProperties.PROPERTY_INECM_MODE))) {
            // In ECM mode launch ECM app dialog
            startActivityForResult(
                new Intent(TelephonyIntents.ACTION_SHOW_NOTICE_ECM_BLOCK_OTHERS, null),
                REQUEST_CODE_EXIT_ECM);
            return true;
        } else if (preference == findPreference(KEY_MANAGE_MOBILE_PLAN)) {
            onManageMobilePlanClick();
        //M: add @{
        } else if (preference == mNfcPreference) {
            ((PreferenceActivity)getActivity()).startPreferencePanel(
                    NfcSettings.class.getName(), null, 0, null, null, 0);
        }
        //M: @}
        // Let the intents be launched by the Preference manager
        return super.onPreferenceTreeClick(preferenceScreen, preference);
    }
    
    private String mManageMobilePlanMessage;
    private static final String CONNECTED_TO_PROVISIONING_NETWORK_ACTION
            = "com.android.server.connectivityservice.CONNECTED_TO_PROVISIONING_NETWORK_ACTION";
    public void onManageMobilePlanClick() {
        log("onManageMobilePlanClick:");
        mManageMobilePlanMessage = null;
        Resources resources = getActivity().getResources();

        NetworkInfo ni = mCm.getProvisioningOrActiveNetworkInfo();
        if (mTm.hasIccCard() && (ni != null)) {
            // Get provisioning URL
            String url = mCm.getMobileProvisioningUrl();
            if (!TextUtils.isEmpty(url)) {
                Intent intent = new Intent(CONNECTED_TO_PROVISIONING_NETWORK_ACTION);
                intent.putExtra("EXTRA_URL", url);
                Context context = getActivity().getBaseContext();
                context.sendBroadcast(intent);
                mManageMobilePlanMessage = null;
            } else {
                // No provisioning URL
                String operatorName = mTm.getSimOperatorName();
                if (TextUtils.isEmpty(operatorName)) {
                    // Use NetworkOperatorName as second choice in case there is no
                    // SPN (Service Provider Name on the SIM). Such as with T-mobile.
                    operatorName = mTm.getNetworkOperatorName();
                    if (TextUtils.isEmpty(operatorName)) {
                        mManageMobilePlanMessage = resources.getString(
                                R.string.mobile_unknown_sim_operator);
                    } else {
                        mManageMobilePlanMessage = resources.getString(
                                R.string.mobile_no_provisioning_url, operatorName);
                    }
                } else {
                    mManageMobilePlanMessage = resources.getString(
                            R.string.mobile_no_provisioning_url, operatorName);
                }
            }
        } else if (mTm.hasIccCard() == false) {
            // No sim card
            mManageMobilePlanMessage = resources.getString(R.string.mobile_insert_sim_card);
        } else {
            // NetworkInfo is null, there is no connection
            mManageMobilePlanMessage = resources.getString(R.string.mobile_connect_to_internet);
        }
        if (!TextUtils.isEmpty(mManageMobilePlanMessage)) {
            log("onManageMobilePlanClick: message=" + mManageMobilePlanMessage);
            showDialog(MANAGE_MOBILE_PLAN_DIALOG_ID);
        }
    }

    private void updateSmsApplicationSetting() {
        log("updateSmsApplicationSetting:");
        ComponentName appName = SmsApplication.getDefaultSmsApplication(getActivity(), true);
        if (appName != null) {
            String packageName = appName.getPackageName();

            CharSequence[] values = mSmsApplicationPreference.getEntryValues();
            for (int i = 0; i < values.length; i++) {
                if (packageName.contentEquals(values[i])) {
                    mSmsApplicationPreference.setValueIndex(i);
                    mSmsApplicationPreference.setSummary(mSmsApplicationPreference.getEntries()[i]);
                    break;
                }
            }
        }
    }

    private void initSmsApplicationSetting() {
        log("initSmsApplicationSetting:");
        Collection<SmsApplicationData> smsApplications =
                SmsApplication.getApplicationCollection(getActivity());

        // If the list is empty the dialog will be empty, but we will not crash.
        int count = smsApplications.size();
        CharSequence[] entries = new CharSequence[count];
        CharSequence[] entryValues = new CharSequence[count];
        Drawable[] entryImages = new Drawable[count];

        PackageManager packageManager = getPackageManager();
        int i = 0;
        for (SmsApplicationData smsApplicationData : smsApplications) {
            entries[i] = smsApplicationData.mApplicationName;
            entryValues[i] = smsApplicationData.mPackageName;
            try {
                entryImages[i] = packageManager.getApplicationIcon(smsApplicationData.mPackageName);
            } catch (NameNotFoundException e) {
                entryImages[i] = packageManager.getDefaultActivityIcon();
            }
            i++;
        }
        mSmsApplicationPreference.setEntries(entries);
        mSmsApplicationPreference.setEntryValues(entryValues);
        mSmsApplicationPreference.setEntryDrawables(entryImages);
        updateSmsApplicationSetting();
    }

    @Override
    public Dialog onCreateDialog(int dialogId) {
        log("onCreateDialog: dialogId=" + dialogId);
        switch (dialogId) {
            case MANAGE_MOBILE_PLAN_DIALOG_ID:
                return new AlertDialog.Builder(getActivity())
                            .setMessage(mManageMobilePlanMessage)
                            .setCancelable(false)
                            .setPositiveButton(com.android.internal.R.string.ok,
                                    new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int id) {
                                    log("MANAGE_MOBILE_PLAN_DIALOG.onClickListener id=" + id);
                                    mManageMobilePlanMessage = null;
                                }
                            })
                            .create();
        }
        return super.onCreateDialog(dialogId);
    }

    private void log(String s) {
        Log.d(TAG, s);
    }

    public boolean onPreferenceChange(Preference preference, Object newValue) {
        if (preference == mSmsApplicationPreference && newValue != null) {
            SmsApplication.setDefaultApplication(newValue.toString(), getActivity());
            updateSmsApplicationSetting();
            return true;
        } else {
            String key = preference.getKey();
            if (KEY_USB_SHARING.equals(key)) {
                if (mUsbSharing.isChecked()) {
                    mConnectivityManager.setUsbInternet(false);
                } else {
                    startFragment(this, UsbSharingChoose.class.getName(), 0, null, R.string.usb_sharing_title);
                    getActivity().overridePendingTransition(R.anim.slide_right_in, R.anim.slide_left_out);
                }
                return false;
            }
            return true;
        }
    }

    public static boolean isRadioAllowed(Context context, String type) {
        if (!AirplaneModeEnabler.isAirplaneModeOn(context)) {
            return true;
        }
        // Here we use the same logic in onCreate().
        String toggleable = Settings.Global.getString(context.getContentResolver(),
                Settings.Global.AIRPLANE_MODE_TOGGLEABLE_RADIOS);
        return toggleable != null && toggleable.contains(type);
    }

    private boolean isSmsSupported() {
        // Some tablet has sim card but could not do telephony operations. Skip those.
        return (mTm.getPhoneType() != TelephonyManager.PHONE_TYPE_NONE);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState != null) {
            mManageMobilePlanMessage = savedInstanceState.getString(SAVED_MANAGE_MOBILE_PLAN_MSG);
        }
        log("onCreate: mManageMobilePlanMessage=" + mManageMobilePlanMessage);

        mCm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        mTm = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);

        addPreferencesFromResource(R.xml.wireless_settings);

        final boolean isSecondaryUser = UserHandle.myUserId() != UserHandle.USER_OWNER;

        final Activity activity = getActivity();
        mAirplaneModePreference = (CheckBoxPreference) findPreference(KEY_TOGGLE_AIRPLANE);
        CheckBoxPreference nfc = (CheckBoxPreference) findPreference(KEY_TOGGLE_NFC);
        PreferenceScreen androidBeam = (PreferenceScreen) findPreference(KEY_ANDROID_BEAM_SETTINGS);
        //M: add @{
        mNfcPreference = (NfcPreference) findPreference(KEY_MTK_TOGGLE_NFC);

        mNfcAdapter = NfcAdapter.getDefaultAdapter(activity);

        if (FeatureOption.MTK_NFC_ADDON_SUPPORT) {
            PreferenceScreen screen = getPreferenceScreen();
            screen.removePreference(nfc);
            screen.removePreference(androidBeam);
            mMTKNfcEnabler = new MtkNfcEnabler(activity, mNfcPreference, null, mNfcAdapter);
        } else {
            getPreferenceScreen().removePreference(mNfcPreference);
            mNfcEnabler = new NfcEnabler(activity, nfc, androidBeam);
        }
        //M: @}
        
        /// M: @{
        mNetworkSettingsPreference = (PreferenceScreen) findPreference(KEY_MOBILE_NETWORK_SETTINGS);
        mUsbSharing = (CheckBoxPreference) findPreference(KEY_USB_SHARING);
        mUsbSharing.setOnPreferenceChangeListener(this);
        /// @}
        CheckBoxPreference nsd = (CheckBoxPreference) findPreference(KEY_TOGGLE_NSD);

        mAirplaneModeEnabler = new AirplaneModeEnabler(activity, mAirplaneModePreference);

        mSmsApplicationPreference = (SmsListPreference) findPreference(KEY_SMS_APPLICATION);
        mSmsApplicationPreference.setOnPreferenceChangeListener(this);
        initSmsApplicationSetting();

        // Remove NSD checkbox by default
        getPreferenceScreen().removePreference(nsd);
        //mNsdEnabler = new NsdEnabler(activity, nsd);

        String toggleable = Settings.Global.getString(activity.getContentResolver(),
                Settings.Global.AIRPLANE_MODE_TOGGLEABLE_RADIOS);

        //enable/disable wimax depending on the value in config.xml
        boolean isWimaxEnabled = !isSecondaryUser && this.getResources().getBoolean(
                com.android.internal.R.bool.config_wimaxEnabled);
        if (!isWimaxEnabled) {
            PreferenceScreen root = getPreferenceScreen();
            Preference ps = (Preference) findPreference(KEY_WIMAX_SETTINGS);
            if (ps != null) root.removePreference(ps);
        } else {
            if (toggleable == null || !toggleable.contains(Settings.Global.RADIO_WIMAX )
                    && isWimaxEnabled) {
                Preference ps = (Preference) findPreference(KEY_WIMAX_SETTINGS);
                ps.setDependency(KEY_TOGGLE_AIRPLANE);
            }
        }
        protectByRestrictions(KEY_WIMAX_SETTINGS);

        // Manually set dependencies for Wifi when not toggleable.
        if (toggleable == null || !toggleable.contains(Settings.Global.RADIO_WIFI)) {
            findPreference(KEY_VPN_SETTINGS).setDependency(KEY_TOGGLE_AIRPLANE);
        }
        if (isSecondaryUser) { // Disable VPN
            removePreference(KEY_VPN_SETTINGS);
        }
        protectByRestrictions(KEY_VPN_SETTINGS);
        // Manually set dependencies for Bluetooth when not toggleable.
        //if (toggleable == null || !toggleable.contains(Settings.Global.RADIO_BLUETOOTH)) {
            // No bluetooth-dependent items in the list. Code kept in case one is added later.
        //}

        // Manually set dependencies for NFC when not toggleable.
        if (toggleable == null || !toggleable.contains(Settings.Global.RADIO_NFC)) {
            //M: add @{
            if (FeatureOption.MTK_NFC_ADDON_SUPPORT) {
                mNfcPreference.setDependency(KEY_TOGGLE_AIRPLANE);
            } else {
                nfc.setDependency(KEY_TOGGLE_AIRPLANE);
            }
            //M: @}
            findPreference(KEY_ANDROID_BEAM_SETTINGS).setDependency(KEY_TOGGLE_AIRPLANE);
        }

        //M: add @{
        // Remove NFC if its not available
        if (mNfcAdapter == null) {
            if (FeatureOption.MTK_NFC_ADDON_SUPPORT) {
                getPreferenceScreen().removePreference(mNfcPreference);
                mMTKNfcEnabler = null;
            } else {
                getPreferenceScreen().removePreference(nfc);
                mNfcEnabler = null;
            }
            getPreferenceScreen().removePreference(androidBeam);
        }
        //M: @}
        
        /// M: the feature of manage mobile plan removed temporarily @{
        if (mDisableMobilePlan) {
            removePreference(KEY_MANAGE_MOBILE_PLAN);
        }
        /// @}
        
        // Remove Mobile Network Settings and Manage Mobile Plan if it's a wifi-only device.
        if (isSecondaryUser || Utils.isWifiOnly(getActivity())) {
            /// M: remove preference
            getPreferenceScreen().removePreference(mNetworkSettingsPreference);
            removePreference(KEY_MANAGE_MOBILE_PLAN);
        }
        // Remove Mobile Network Settings and Manage Mobile Plan
        // if config_show_mobile_plan sets false.
        boolean isMobilePlanEnabled = this.getResources().getBoolean(
                R.bool.config_show_mobile_plan);
        if (!isMobilePlanEnabled) {
            Preference pref = findPreference(KEY_MANAGE_MOBILE_PLAN);
            if (pref != null) {
                removePreference(KEY_MANAGE_MOBILE_PLAN);
            }
        }
        protectByRestrictions(KEY_MOBILE_NETWORK_SETTINGS);
        protectByRestrictions(KEY_MANAGE_MOBILE_PLAN);

        // Remove SMS Application if the device does not support SMS
        if (!isSmsSupported()) {
            removePreference(KEY_SMS_APPLICATION);
        }

        // Remove Airplane Mode settings if it's a stationary device such as a TV.
        if (getActivity().getPackageManager().hasSystemFeature(PackageManager.FEATURE_TELEVISION)) {
            removePreference(KEY_TOGGLE_AIRPLANE);
        }

        // Enable Proxy selector settings if allowed.
        Preference mGlobalProxy = findPreference(KEY_PROXY_SETTINGS);
        DevicePolicyManager mDPM = (DevicePolicyManager)
                activity.getSystemService(Context.DEVICE_POLICY_SERVICE);
        // proxy UI disabled until we have better app support
        getPreferenceScreen().removePreference(mGlobalProxy);
        mGlobalProxy.setEnabled(mDPM.getGlobalProxyAdmin() == null);

        /// M: @{
        // Disable Tethering if it's not allowed or if it's a wifi-only device
        mConnectivityManager = (ConnectivityManager) activity.getSystemService(Context.CONNECTIVITY_SERVICE);
        mTetherSettings = findPreference(KEY_TETHER_SETTINGS);
        if (mConnectivityManager != null) {
            if (isSecondaryUser || !mConnectivityManager.isTetheringSupported() || Utils.isWifiOnly(getActivity())) {
                getPreferenceScreen().removePreference(mTetherSettings);
            } else {
                mTetherSettings.setTitle(Utils.getTetheringLabel(mConnectivityManager));
            }
        }
        /// @}
        protectByRestrictions(KEY_TETHER_SETTINGS);
        // Enable link to CMAS app settings depending on the value in config.xml.
        boolean isCellBroadcastAppLinkEnabled = this.getResources().getBoolean(
                com.android.internal.R.bool.config_cellBroadcastAppLinks);
        try {
            if (isCellBroadcastAppLinkEnabled) {
                PackageManager pm = getPackageManager();
                if (pm.getApplicationEnabledSetting("com.android.cellbroadcastreceiver")
                        == PackageManager.COMPONENT_ENABLED_STATE_DISABLED) {
                    isCellBroadcastAppLinkEnabled = false;  // CMAS app disabled
                }
            }
        } catch (IllegalArgumentException ignored) {
            isCellBroadcastAppLinkEnabled = false;  // CMAS app not installed
        }
        if (isSecondaryUser || !isCellBroadcastAppLinkEnabled) {
            PreferenceScreen root = getPreferenceScreen();
            Preference ps = findPreference(KEY_CELL_BROADCAST_SETTINGS);
            if (ps != null) {
                root.removePreference(ps);
            }
        }
        protectByRestrictions(KEY_CELL_BROADCAST_SETTINGS);
        /// M: @{
        //If rcse apk was not installed, then should hide rcse settings ui
        Intent intent = new Intent(RCSE_SETTINGS_INTENT);
        List<ResolveInfo> rcseApps = getPackageManager().queryIntentActivities(intent, 0);
        if (rcseApps == null || rcseApps.size() == 0) {
            Xlog.w(TAG, RCSE_SETTINGS_INTENT + " is not installed");
            getPreferenceScreen().removePreference(findPreference(KEY_RCSE_SETTINGS));
        } else {
            Xlog.w(TAG, RCSE_SETTINGS_INTENT + " is installed");
            findPreference(KEY_RCSE_SETTINGS).setIntent(intent);
        }

        mIntentFilter = new IntentFilter(USB_DATA_STATE);
        mIntentFilter.addAction(UsbManager.ACTION_USB_STATE);
        mIntentFilter.addAction(TelephonyIntents.ACTION_SIM_INFO_UPDATE);
        /// @}
    }
    /**
     * M: update mobile network enabled
     */
    private void updateMobileNetworkEnabled() {
        ///M: GEMINI+ 
        ///   modify in a simple way to get whether there is sim card inserted
        int simNum = SimInfoManager.getInsertedSimCount(getActivity());
        Xlog.d(TAG,"simNum=" + simNum);
        if (simNum > 0) {
            mNetworkSettingsPreference.setEnabled(true);   
        } else {
            mNetworkSettingsPreference.setEnabled(false);   
        }    
        /// M:  @{ 
    }
    /// M:  @{
    private PhoneStateListener mPhoneStateListener = new PhoneStateListener() {
        @Override
        public void onCallStateChanged(int state, String incomingNumber) {
            super.onCallStateChanged(state, incomingNumber);
            Xlog.d(TAG, "PhoneStateListener, new state=" + state);
            if (state == TelephonyManager.CALL_STATE_IDLE && getActivity() != null) {
                TelephonyManager telephonyManager = 
                    (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
                int currPhoneCallState = telephonyManager.getCallState();
                
                Xlog.d(TAG, "Total PhoneState =" + currPhoneCallState);
                if (currPhoneCallState == TelephonyManager.CALL_STATE_IDLE) {
                    //only if both SIM are in call state, we will enable mobile network settings
                    mTelephony = ITelephony.Stub.asInterface(ServiceManager.getService("phone"));
                    updateMobileNetworkEnabled();
                }
            } 
        }
    };
    /// @}

    @Override
    public void onStart() {
        super.onStart();

        initSmsApplicationSetting();
    }

    @Override
    public void onResume() {
        super.onResume();

        mAirplaneModeEnabler.resume();
        if (FeatureOption.MTK_NFC_ADDON_SUPPORT) {
            if (mMTKNfcEnabler != null) {
                mMTKNfcEnabler.resume();
            }
        } else {
            if (mNfcEnabler != null) {
                mNfcEnabler.resume();
            }
        }
        if (mNsdEnabler != null) {
            mNsdEnabler.resume();
        }

        /// M:  @{
        TelephonyManager telephonyManager = 
            (TelephonyManager)getSystemService(Context.TELEPHONY_SERVICE);
        telephonyManager.listen(mPhoneStateListener, PhoneStateListener.LISTEN_CALL_STATE);

        Xlog.d(TAG, "onResume(), call state=" + telephonyManager.getCallState());
        if (telephonyManager.getCallState() != TelephonyManager.CALL_STATE_IDLE) {
            mNetworkSettingsPreference.setEnabled(false);
        } else {
            mNetworkSettingsPreference.setEnabled(true);
        }

        getActivity().registerReceiver(mReceiver, mIntentFilter);
        /// @}
    }
    
    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        if (!TextUtils.isEmpty(mManageMobilePlanMessage)) {
            outState.putString(SAVED_MANAGE_MOBILE_PLAN_MSG, mManageMobilePlanMessage);
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        /// M:  @{
        TelephonyManager telephonyManager = 
            (TelephonyManager)getSystemService(Context.TELEPHONY_SERVICE);
        telephonyManager.listen(mPhoneStateListener, PhoneStateListener.LISTEN_NONE);

        getActivity().unregisterReceiver(mReceiver);
        /// @}

        mAirplaneModeEnabler.pause();
        if (FeatureOption.MTK_NFC_ADDON_SUPPORT) {
            if (mMTKNfcEnabler != null) {
                mMTKNfcEnabler.pause();
            }
        } else {
            if (mNfcEnabler != null) {
                mNfcEnabler.pause();
            }
        }
        if (mNsdEnabler != null) {
            mNsdEnabler.pause();
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_CODE_EXIT_ECM) {
            Boolean isChoiceYes = data.getBooleanExtra(EXIT_ECM_RESULT, false);
            // Set Airplane mode based on the return value and checkbox state
            mAirplaneModeEnabler.setAirplaneModeInECM(isChoiceYes,
                    mAirplaneModePreference.isChecked());
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    protected int getHelpResource() {
        return R.string.help_url_more_networks;
    }
}
