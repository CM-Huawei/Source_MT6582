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

package com.mediatek.wifi;

import android.content.Context;
import android.net.NetworkInfo.DetailedState;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.SystemProperties;
import android.preference.CheckBoxPreference;
import android.preference.Preference;
import android.preference.PreferenceScreen;
import android.provider.Settings;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.android.settings.R;
import com.android.settings.wifi.WifiConfigUiBase;
import com.mediatek.xlog.Xlog;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PasspointSettings implements Preference.OnPreferenceClickListener {
    private static final String TAG = "PasspointSettings";
    private static final String REG_HOTSPOT = "hs20=(\\d+)\\s";
    private static final String REG_CREDENTIAL_TYPE = "selectedMethod=(\\d+)\\s";
    private static final String REG_EAP_METHOD = "Phase2 method=(\\w+)\\s";
    
    private static final int CREDENTIAL_TYPE_TTLS = 21;
    private static final int CREDENTIAL_TYPE_SIM = 18;
    private static final int CREDENTIAL_TYPE_TLS = 13;
    
    private static final int TTLS_INDEX = 0;
    private static final int SIM_INDEX = 1;
    private static final int TLS_INDEX = 2;
    private static final int HOTSPOT_INDEX = 0;
    private static final int CREDENTIAL_TYPE_INDEX = 1;
    private static final int EAP_METHOD_INDEX = 2;
    private static final int PASSPOINT_INFO_ITEMS = 3;
    
    private CheckBoxPreference mPasspointCheckBox;
    private Context mContext;
    
    /*
     * PasspointSettings: constructor
     */
    public PasspointSettings(Context context) {
        Xlog.d(TAG, "PasspointSettings") ;
        mContext = context;
    }
    
    /*
     * onPreferenceClick: response for click mPasspointCheckBox
     * @see android.preference.Preference.OnPreferenceClickListener#onPreferenceClick(android.preference.Preference)
     */
    public boolean onPreferenceClick(Preference preference) {
        if (preference == mPasspointCheckBox) {
            Settings.Global.putInt(mContext.getContentResolver(), Settings.Global.WIFI_PASSPOINT_ON,
                    ((CheckBoxPreference) preference).isChecked() ? 1 : 0);
        }
        return true;
    }
    
    /*
     * add mPasspointCheckBox to AdvanceSettings
     */
    public void addPasspointPreference(PreferenceScreen screen) {
        mPasspointCheckBox = new CheckBoxPreference(mContext);
        mPasspointCheckBox.setTitle(R.string.passpoint_title);
        mPasspointCheckBox.setSummary(R.string.passpoint_summary);
        mPasspointCheckBox.setOnPreferenceClickListener(this);
        screen.addPreference(mPasspointCheckBox);
    }
    
    /*
     * refreshPasspointPreference: refresh mPasspointCheckBox's status of checked and enabled
     */
    public void refreshPasspointPreference(boolean wifiEnabled) {
        mPasspointCheckBox.setChecked(Settings.Global.getInt(mContext.getContentResolver(),
                Settings.Global.WIFI_PASSPOINT_ON, 0) == 1);
        mPasspointCheckBox.setEnabled(wifiEnabled);
    }
    
    /*
     * setSummary: set passpoint ap's summary, when this ap is connected or not active
     */
    public static void setSummary(Preference ap, Context context, boolean supportedPasspoint, DetailedState state) {
        if (supportedPasspoint) {
            Xlog.d(TAG, "setSummary, ap = " + ap + ", supportedPasspoint = " + supportedPasspoint + ", state = " + state);
            StringBuilder summary = new StringBuilder();
            summary.append(ap.getSummary());
            summary.append(" ");
            summary.append(context.getString(state == DetailedState.CONNECTED ?
                    R.string.passpoint_append_summary : R.string.passpoint_append_summary_not_connected));
            Xlog.d(TAG, "setSummary = " + summary.toString());
            ap.setSummary(summary.toString());
        }
        
    }
    
    /*
     * shouldUpdate: current connected passpoint Ap doesn't have WifiConfiguration, so we should update Accesspoint by
     * our method
     */
    public static boolean shouldUpdate(WifiInfo info, String bssid, boolean supportedPasspoint) {
        // just for debug
        if (supportedPasspoint) {
            Xlog.d(TAG, "shouldUpdate, info = " + info + ", bssid = " + bssid);
        }
        boolean sigmaTest = SystemProperties.getInt("mediatek.wlan.hs20.sigma", 0) == 1;
        Xlog.d(TAG, "shouldUpdate, sigmaTest = " + sigmaTest);
        // Passpoint ap or do sigmaTest, we should update ap's information
        return (supportedPasspoint || sigmaTest) && info != null && bssid != null && bssid.equals(info.getBSSID());
    }
    
    /*
     * addView: current connected passpoint ap has its own WifiDialog information
     */
    public static boolean addView(WifiConfigUiBase configUi, DetailedState state, View view, 
            boolean shouldSetDisconnectButton) {
        Xlog.d(TAG, "addView, shouldSetDisconnectButton = " + shouldSetDisconnectButton + 
                ", state = " + state);
        if (state != null) {
            ViewGroup group = (ViewGroup) view.findViewById(R.id.info);
            Context context = configUi.getContext();
            view.findViewById(R.id.priority_field).setVisibility(View.GONE);
            
            addRows(configUi, group, state);
            
            if (shouldSetDisconnectButton) {
                configUi.setSubmitButton(context.getString(R.string.wifi_disconnect));
            }
            configUi.setCancelButton(context.getString(R.string.wifi_cancel));
            return true; 
        }
        
        return false;
    }
    
    /*
     * addRows: WifiDialog add passpoint three items of information
     */
    private static void addRows(WifiConfigUiBase configUi, ViewGroup group, DetailedState state) {
        Xlog.d(TAG, "addRows, DetailedState = " + state);
        if (state == DetailedState.CONNECTED) {
            Context context = configUi.getContext();
            List<String> passpointInfo = new ArrayList<String>();
            getPasspointInfo(context, passpointInfo);
            if (passpointInfo.get(HOTSPOT_INDEX) != null) {
                addRow(configUi, group, R.string.passpoint_config_hotspot, context.getString(R.string.passpoint_supported));
            }
            
            
            String[] credentialType = context.getResources().getStringArray(R.array.passpoint_credential_type);
            String[] eapMethodPhase = context.getResources().getStringArray(R.array.passpoint_eap_method);
            
            String strCredentialType = null;
            String strEapMethodPhase1 = null;
            
            String type = passpointInfo.get(CREDENTIAL_TYPE_INDEX);
            if (type != null) {
                switch (Integer.parseInt(type)) {
                case CREDENTIAL_TYPE_TTLS:
                    strCredentialType = credentialType[TTLS_INDEX];
                    strEapMethodPhase1 = eapMethodPhase[TTLS_INDEX];
                    break;
                case CREDENTIAL_TYPE_SIM:
                    strCredentialType = credentialType[SIM_INDEX];
                    strEapMethodPhase1 = eapMethodPhase[SIM_INDEX];
                    break;
                case CREDENTIAL_TYPE_TLS:
                    strCredentialType = credentialType[TLS_INDEX];
                    strEapMethodPhase1 = eapMethodPhase[TLS_INDEX];
                    break;
                default:
                    Xlog.e(TAG, "addRows error");
                    break;
                }
                addRow(configUi, group, R.string.passpoint_config_credential_type, strCredentialType);
            }
            
            String strEapMethodPhase2 = passpointInfo.get(EAP_METHOD_INDEX);
            if (strEapMethodPhase1 != null && strEapMethodPhase2 != null) {
                addRow(configUi, group, R.string.passpoint_config_eap_method, strEapMethodPhase1 + strEapMethodPhase2);
            }
        }
        
    }
    
    private static void addRow(WifiConfigUiBase configUi, ViewGroup group, int name, String value) {
        View row = configUi.getLayoutInflater().inflate(R.layout.wifi_dialog_row, group, false);
        ((TextView) row.findViewById(R.id.name)).setText(name);
        ((TextView) row.findViewById(R.id.value)).setText(value);
        group.addView(row);
    }
    
    private static String regPasspointInfo(String reg, String info) {
        if (reg == null || info == null) {
            return null;    
        }
        Pattern pattern = Pattern.compile(reg);
        Matcher matcher = pattern.matcher(info);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }
    
    
    private static void getPasspointInfo(Context context, List<String> passpointInfo) {
        String info = getPasspointInfo(context);
        passpointInfo.add(regPasspointInfo(REG_HOTSPOT, info));
        passpointInfo.add(regPasspointInfo(REG_CREDENTIAL_TYPE, info));
        passpointInfo.add(regPasspointInfo(REG_EAP_METHOD, info));
    }
    
    private static String getPasspointInfo(Context context) {
        WifiManager wifiManager = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        String info = wifiManager.getHsStatus();
        Xlog.d(TAG, "getPasspointInfo = " + info);
        return info;
    }

}
