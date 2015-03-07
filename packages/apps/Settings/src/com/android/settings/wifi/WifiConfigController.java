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

package com.android.settings.wifi;

import static android.net.wifi.WifiConfiguration.INVALID_NETWORK_ID;

import android.content.Context;
import android.content.res.Resources;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.net.NetworkInfo.DetailedState;
import android.net.NetworkUtils;
import android.net.ProxyProperties;
import android.net.RouteInfo;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiConfiguration.AuthAlgorithm;
import android.net.wifi.WifiConfiguration.GroupCipher;
import android.net.wifi.WifiConfiguration.IpAssignment;
import android.net.wifi.WifiConfiguration.KeyMgmt;
import android.net.wifi.WifiConfiguration.PairwiseCipher;
import android.net.wifi.WifiConfiguration.ProxySettings;
import android.net.wifi.WifiEnterpriseConfig;
import android.net.wifi.WifiEnterpriseConfig.Eap;
import android.net.wifi.WifiEnterpriseConfig.Phase2;
import android.net.wifi.WifiConfiguration.Protocol;
import android.net.wifi.WifiInfo;
import android.os.Handler;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.SystemProperties;
import android.provider.Settings;
import android.security.Credentials;
import android.security.KeyStore;
import android.telephony.TelephonyManager;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextWatcher;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

import com.android.internal.telephony.PhoneConstants;
import com.android.settings.ProxySelector;
import com.android.settings.R;
import com.android.settings.Utils;
import com.mediatek.common.featureoption.FeatureOption;
import com.mediatek.common.telephony.ITelephonyEx;
import com.mediatek.settings.ext.IWifiExt;
import com.mediatek.telephony.TelephonyManagerEx;
import com.mediatek.wifi.PasspointSettings;
import com.mediatek.wifi.Utf8ByteLengthFilter;
import com.mediatek.xlog.Xlog;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.util.Iterator;
import java.util.ArrayList;
import java.util.List;

/**
 * The class for allowing UIs like {@link WifiDialog} and
 * {@link WifiConfigUiBase} to share the logic for controlling buttons, text
 * fields, etc.
 */
public class WifiConfigController implements TextWatcher,
        AdapterView.OnItemSelectedListener, OnCheckedChangeListener {
      private final WifiConfigUiBase mConfigUi;
      private final View mView;
      private final AccessPoint mAccessPoint;

      private boolean mEdit;

      private TextView mSsidView;

      // e.g. AccessPoint.SECURITY_NONE
      private int mAccessPointSecurity;
      private TextView mPasswordView;

      private String unspecifiedCert = "unspecified";
      private static final int unspecifiedCertIndex = 0;

      /* Phase2 methods supported by PEAP are limited */
      private final ArrayAdapter<String> PHASE2_PEAP_ADAPTER;
      /* Full list of phase2 methods */
      private final ArrayAdapter<String> PHASE2_FULL_ADAPTER;

      private Spinner mSecuritySpinner;
      private Spinner mEapMethodSpinner;
      private Spinner mEapCaCertSpinner;
      private Spinner mPhase2Spinner;
      // Associated with mPhase2Spinner, one of PHASE2_FULL_ADAPTER or PHASE2_PEAP_ADAPTER
      private ArrayAdapter<String> mPhase2Adapter;
      private Spinner mEapUserCertSpinner;
      private TextView mEapIdentityView;
      private TextView mEapAnonymousView;

      /// M: add transmit key spinner @{
      private Spinner mWEPKeyIndex;
      private Spinner mWEPKeyType;
      ///@}

      /* This value comes from "wifi_ip_settings" resource array */
      private static final int DHCP = 0;
      private static final int STATIC_IP = 1;

      /* These values come from "wifi_proxy_settings" resource array */
      public static final int PROXY_NONE = 0;
      public static final int PROXY_STATIC = 1;

    /* These values come from "wifi_eap_method" resource array */
      public static final int WIFI_EAP_METHOD_PEAP = 0;
      public static final int WIFI_EAP_METHOD_TLS = 1;
      public static final int WIFI_EAP_METHOD_TTLS = 2;
      public static final int WIFI_EAP_METHOD_PWD = 3;
      /// M: sim/aka @{
      public static final int WIFI_EAP_METHOD_SIM = 4;
      public static final int WIFI_EAP_METHOD_AKA = 5;
      /// @}
      private static final int BUFFER_LENGTH = 40;
      private static final int MNC_SUB_BEG = 3;
      private static final int MNC_SUB_END = 5;
      private static final int MCC_SUB_BEG = 0;
      private static final int MCC_MNC_LENGTH = 5;

      /* These values come from "wifi_peap_phase2_entries" resource array */
      public static final int WIFI_PEAP_PHASE2_NONE 	    = 0;
      public static final int WIFI_PEAP_PHASE2_MSCHAPV2 	= 1;
      public static final int WIFI_PEAP_PHASE2_GTC        = 2; 
      
      private static final String TAG = "WifiConfigController";

      private Spinner mIpSettingsSpinner;
      private TextView mIpAddressView;
      private TextView mGatewayView;
      private TextView mNetworkPrefixLengthView;
      private TextView mDns1View;
      private TextView mDns2View;

      private Spinner mProxySettingsSpinner;
      private TextView mProxyHostView;
      private TextView mProxyPortView;
      private TextView mProxyExclusionListView;

      /// M: add for EAP_SIM/AKA @{
      private Spinner mSimSlot;
      private TelephonyManager mTm;
      private ITelephonyEx mTelephonyEx;
      private TelephonyManagerEx mTelephonyManagerEx;
      /// @}

      /// M: add for WAPI @{
      private Spinner mWapiAsCert;
      private Spinner mWapiClientCert;
      private boolean mHex;
      private static final String WLAN_PROP_KEY = "persist.sys.wlan";
      private static final String WIFI = "wifi";
      private static final String WAPI = "wapi";
      private static final String WIFI_WAPI = "wifi-wapi";
      private static final String DEFAULT_WLAN_PROP = WIFI_WAPI;
      private static final int SSID_MAX_LEN = 32;
      /// @}
      /// M: add for DHCPV6 @{
      private static final int IPV4_ADDRESS_LENGTH = 4;
      private static final int IPV6_ADDRESS_LENGTH = 16;
      /// @}
      /// M: add for plug in
      IWifiExt mExt;

      private IpAssignment mIpAssignment = IpAssignment.UNASSIGNED;
      private ProxySettings mProxySettings = ProxySettings.UNASSIGNED;
      private LinkProperties mLinkProperties = new LinkProperties();

      // True when this instance is used in SetupWizard XL context.
      private final boolean mInXlSetupWizard;

      private final Handler mTextViewChangedHandler;

      public WifiConfigController(
                  WifiConfigUiBase parent, View view, AccessPoint accessPoint, boolean edit) {
            mConfigUi = parent;
            mInXlSetupWizard = (parent instanceof WifiConfigUiForSetupWizardXL);

            mView = view;
            mAccessPoint = accessPoint;
            mAccessPointSecurity = (accessPoint == null) ? AccessPoint.SECURITY_NONE
                        : accessPoint.security;
            mEdit = edit;

            mTextViewChangedHandler = new Handler();
            final Context context = mConfigUi.getContext();
            final Resources resources = context.getResources();

            /// M: get telephonyManager and telephonyManagerEx @{
            if(FeatureOption.MTK_EAP_SIM_AKA){
                mTm = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
                mTelephonyEx = ITelephonyEx.Stub.asInterface(ServiceManager.getService(Context.TELEPHONY_SERVICEEX));
                mTelephonyManagerEx = TelephonyManagerEx.getDefault();
            }
            /// @}

            /// M: get plug in,whether to show access point priority select
            // spinner. @{
            mExt = Utils.getWifiPlugin(context);
            if (mAccessPoint != null) {
                  mExt.setAPNetworkId(mAccessPoint.networkId);
                  if (mAccessPoint.networkId != -1) {
                        Xlog.d(TAG, "priority=" + mAccessPoint.getConfig().priority);
                        mExt.setAPPriority(mAccessPoint.getConfig().priority);
                  }
            }
            LinearLayout priorityLayout = (LinearLayout) mView
                        .findViewById(R.id.priority_field);
            View priorityView = mExt.getPriorityView();
            if (priorityView != null) {
                  priorityLayout.addView(priorityView, new LinearLayout.LayoutParams(
                              LinearLayout.LayoutParams.MATCH_PARENT,
                              LinearLayout.LayoutParams.WRAP_CONTENT));
            }
            /// @}

        PHASE2_PEAP_ADAPTER = new ArrayAdapter<String>(
            context, android.R.layout.simple_spinner_item,
            context.getResources().getStringArray(R.array.wifi_peap_phase2_entries));
        PHASE2_PEAP_ADAPTER.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);

        PHASE2_FULL_ADAPTER = new ArrayAdapter<String>(
                context, android.R.layout.simple_spinner_item,
                context.getResources().getStringArray(R.array.wifi_phase2_entries));
        PHASE2_FULL_ADAPTER.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);

        unspecifiedCert = context.getString(R.string.wifi_unspecified);
            mIpSettingsSpinner = (Spinner) mView.findViewById(R.id.ip_settings);
            mIpSettingsSpinner.setOnItemSelectedListener(this);
            mProxySettingsSpinner = (Spinner) mView.findViewById(R.id.proxy_settings);
            mProxySettingsSpinner.setOnItemSelectedListener(this);

            if (mAccessPoint == null) { // new network
                  mConfigUi.setTitle(R.string.wifi_add_network);

                  mSsidView = (TextView) mView.findViewById(R.id.ssid);
                  mSsidView.addTextChangedListener(this);
                  /// M: set input filter
                  mSsidView.setFilters(new InputFilter[] { new Utf8ByteLengthFilter(
                              SSID_MAX_LEN) });

                  /// M: set security text @{
                  TextView securityText = (TextView) view
                              .findViewById(R.id.security_text);
                  mExt.setSecurityText(securityText);
                  /// @}
                  mSecuritySpinner = ((Spinner) mView.findViewById(R.id.security));
                  mSecuritySpinner.setOnItemSelectedListener(this);
                  if (mInXlSetupWizard) {
                        mView.findViewById(R.id.type_ssid).setVisibility(View.VISIBLE);
                        mView.findViewById(R.id.type_security).setVisibility(View.VISIBLE);
                        // We want custom layout. The content must be same as the other cases.

                        ArrayAdapter<String> adapter = new ArrayAdapter<String>(context,
                                   R.layout.wifi_setup_custom_list_item_1, android.R.id.text1,
                                   context.getResources().getStringArray(R.array.wifi_security_no_eap));
                        mSecuritySpinner.setAdapter(adapter);
                  } else {
                        mView.findViewById(R.id.type).setVisibility(View.VISIBLE);
                  }
                  /// M: set array for wifi security @{
                  int viewId = R.id.security;
                  if (FeatureOption.MTK_WAPI_SUPPORT) {
                        String type = SystemProperties.get(WLAN_PROP_KEY,
                                    DEFAULT_WLAN_PROP);
                        if (type.equals(WIFI_WAPI)) {
                              if (AccessPoint.isWFATestSupported()) {
                                    viewId = R.id.security_wfa; // WIFI + WAPI, support
                                    // separate WPA2 PSK
                                    // security
                              } else {
                                    viewId = R.id.security; // WIFI + WAPI
                              }
                        } else if (type.equals(WIFI)) {
                              if (AccessPoint.isWFATestSupported()) {
                                    viewId = R.id.wpa_security_wfa; // WIFI only, support
                                    // separate WPA2 PSK
                                    // security
                              } else {
                                    viewId = R.id.wpa_security; // WIFI only
                              }
                        } else if (type.equals(WAPI)) {
                              viewId = R.id.wapi_security; // WAPI only
                        }
                  } else {
                        if (AccessPoint.isWFATestSupported()) {
                              viewId = R.id.wpa_security_wfa; // WIFI only, support
                              // separate WPA and WPA2 PSK
                              // security
                        } else {
                              viewId = R.id.wpa_security; // WIFI only
                        }
                  }
                  switchWlanSecuritySpinner((Spinner) mView.findViewById(viewId));
                  /// @}

                  showIpConfigFields();
                  showProxyFields();
                  mView.findViewById(R.id.wifi_advanced_toggle).setVisibility(View.VISIBLE);
                  ((CheckBox)mView.findViewById(R.id.wifi_advanced_togglebox))
                          .setOnCheckedChangeListener(this);

                  mConfigUi.setSubmitButton(context.getString(R.string.wifi_save));
            } else {
                  mConfigUi.setTitle(mAccessPoint.ssid);

                  ViewGroup group = (ViewGroup) mView.findViewById(R.id.info);

                  DetailedState state = mAccessPoint.getState();
                  if (state != null) {
                    addRow(group, R.string.wifi_status, Summary.get(mConfigUi.getContext(), state));
                  }

                  int level = mAccessPoint.getLevel();
                  if (level != -1) {
                        String[] signal = resources.getStringArray(R.array.wifi_signal);
                        addRow(group, R.string.wifi_signal, signal[level]);
                  }

                  WifiInfo info = mAccessPoint.getInfo();
                  if (info != null && info.getLinkSpeed() != -1) {
                        addRow(group, R.string.wifi_speed, info.getLinkSpeed() + WifiInfo.LINK_SPEED_UNITS);
                  }

                  /// M: add security information @{
                  View row = mConfigUi.getLayoutInflater().inflate(
                              R.layout.wifi_dialog_row, group, false);
                  ((TextView) row.findViewById(R.id.name)).setText(mExt
                              .getSecurityText(mConfigUi.getContext().getString(R.string.wifi_security)));
                  ((TextView) row.findViewById(R.id.value)).setText(mAccessPoint
                              .getSecurityString(false));
                  group.addView(row);
                  /// @}

                  //M: add for Passpoint
                  if ((PasspointSettings.shouldUpdate(mAccessPoint.getInfo(), mAccessPoint.bssid, 
                          mAccessPoint.mSupportedPasspoint) ||
                          mAccessPoint.mSupportedPasspoint) && PasspointSettings.addView(mConfigUi, mAccessPoint.getState(),
                          mView, mExt.shouldSetDisconnectButton())) {
                      return;
                  }

                  boolean showAdvancedFields = false;
                  if (mAccessPoint.networkId != INVALID_NETWORK_ID) {
                        WifiConfiguration config = mAccessPoint.getConfig();
                        if (config.ipAssignment == IpAssignment.STATIC) {
                              mIpSettingsSpinner.setSelection(STATIC_IP);
                              showAdvancedFields = true;
                        } else {
                              mIpSettingsSpinner.setSelection(DHCP);
                        }
                        // Display IP addresses
                        /// M: show all Ipv6 address including stateful and stateless @{
                        addIpAddressRows(config, group);
                        /// @}

                        if (config.proxySettings == ProxySettings.STATIC) {
                              mProxySettingsSpinner.setSelection(PROXY_STATIC);
                              showAdvancedFields = true;
                        } else if (config.proxySettings == ProxySettings.PAC) {
                            mProxySettingsSpinner.setVisibility(View.GONE);
                            TextView textView = (TextView)mView.findViewById(R.id.proxy_pac_info);
                            textView.setVisibility(View.VISIBLE);
                            textView.setText(context.getString(R.string.proxy_url) +
                                    config.linkProperties.getHttpProxy().getPacFileUrl());
                            showAdvancedFields = true;                              
                        } else {
                              mProxySettingsSpinner.setSelection(PROXY_NONE);
                        }
                  }

                  if (mAccessPoint.networkId == INVALID_NETWORK_ID || mEdit) {
                        showSecurityFields();
                        showIpConfigFields();
                        showProxyFields();
                        mView.findViewById(R.id.wifi_advanced_toggle).setVisibility(View.VISIBLE);
                        ((CheckBox)mView.findViewById(R.id.wifi_advanced_togglebox))
                                .setOnCheckedChangeListener(this);
                        if (showAdvancedFields) {
                            ((CheckBox)mView.findViewById(R.id.wifi_advanced_togglebox)).setChecked(true);
                             mView.findViewById(R.id.wifi_advanced_fields).setVisibility(View.VISIBLE);
                        }
                  } else {
                        mView.findViewById(R.id.priority_field).setVisibility(View.GONE);
                  }

                  if (mEdit) {
                        mConfigUi.setSubmitButton(context.getString(R.string.wifi_save));
                  } else {
                        if (state == null && level != -1) {
                              mConfigUi.setSubmitButton(context.getString(R.string.wifi_connect));
                        } else if (state != null && mExt.shouldSetDisconnectButton()) {
                              /// M: set disconnect button
                              mConfigUi.setSubmitButton(context.getString(R.string.wifi_disconnect));
                        } else {
                              mView.findViewById(R.id.ip_fields).setVisibility(View.GONE);
                        }
                        if (mAccessPoint.networkId != INVALID_NETWORK_ID
                                    && mExt.shouldAddForgetButton(mAccessPoint.ssid,
                                                mAccessPoint.security)) {
                              mConfigUi.setForgetButton(context
                                          .getString(R.string.wifi_forget));
                        }
                  }
                  ///M: for CMCC-AUTO ignore some config information@{
                  if (mAccessPoint != null && mView != null) {
                      List<View> lists = new ArrayList<View>();
                      lists.add(mView.findViewById(R.id.info));
                      lists.add(mView.findViewById(R.id.wifi_advanced_togglebox));
                      lists.add(mView.findViewById(R.id.priority_field));

                      mExt.hideWifiConfigInfo(new IWifiExt.Builder()
                                      .setSsid(mAccessPoint.ssid)
                                      .setSecurity(mAccessPoint.security)
                                      .setNetworkId(mAccessPoint.networkId)
                                      .setEdit(mEdit)
                                      .setViews(lists));
                  }
                  ///@}
            }

            mConfigUi.setCancelButton(context.getString(R.string.wifi_cancel));
            if (mConfigUi.getSubmitButton() != null) {
                  enableSubmitIfAppropriate();
            }
      }

      /**
       * M: make NAI
       * @param simOperator mnc+mcc
       * @param imsi eapMethod
       * @return the string of NAI
       */
      public static String makeNAI(String simOperator, String imsi, String eapMethod) {

            // airplane mode & select wrong sim slot
            if (imsi == null) {
                  return addQuote("error");
            }

            StringBuffer NAI = new StringBuffer(BUFFER_LENGTH);
            // s = sb.append("a = ").append(a).append("!").toString();
            System.out.println("".length());

            if (eapMethod.equals("SIM")) {
                  NAI.append("1");
            } else if (eapMethod.equals("AKA")) {
                  NAI.append("0");
            }

            // add imsi
            NAI.append(imsi);
            NAI.append("@wlan.mnc");
            // add mnc
            // for some operator
            Log.i(TAG, "simOperator = " + simOperator);
            if (simOperator.length() == MCC_MNC_LENGTH) {
                NAI.append("0");
                NAI.append(imsi.substring(MNC_SUB_BEG, MNC_SUB_END));
            } else {
                NAI.append(imsi.substring(MNC_SUB_BEG, MNC_SUB_END + 1));
            }
            NAI.append(".mcc");
            // add mcc
            NAI.append(imsi.substring(MCC_SUB_BEG, MNC_SUB_BEG));

            // NAI.append(imsi.substring(5));
            NAI.append(".3gppnetwork.org");
            Xlog.d(TAG, NAI.toString());
            Xlog.d(TAG, "\"" + NAI.toString() + "\"");
            return addQuote(NAI.toString());
      }

      /**
       * M: add quote for strings
       * @param string
       * @return add quote to the string
       */
      public static String addQuote(String s) {
            return "\"" + s + "\"";
      }

      private void addRow(ViewGroup group, int name, String value) {
            View row = mConfigUi.getLayoutInflater().inflate(
                        R.layout.wifi_dialog_row, group, false);
            ((TextView) row.findViewById(R.id.name)).setText(name);
            ((TextView) row.findViewById(R.id.value)).setText(value);
            group.addView(row);
      }

      /* show submit button if password, ip and proxy settings are valid */
      void enableSubmitIfAppropriate() {
            Button submit = mConfigUi.getSubmitButton();
            if (submit == null)
                  return;

            boolean enabled = false;
            boolean passwordInvalid = false;

            if (mPasswordView != null
                 && ((mAccessPointSecurity == AccessPoint.SECURITY_WEP && !isWEPKeyValid(mPasswordView.getText().toString()))
                            || ((mAccessPointSecurity == AccessPoint.SECURITY_PSK
                                || mAccessPointSecurity == AccessPoint.SECURITY_WPA_PSK 
                                || mAccessPointSecurity == AccessPoint.SECURITY_WPA2_PSK) && mPasswordView.length() < 8)
                            || (mAccessPointSecurity == AccessPoint.SECURITY_WAPI_PSK && (mPasswordView.length() < 8
                            || 64 < mPasswordView.length() || (mHex && !mPasswordView
                                    .getText().toString().matches("[0-9A-Fa-f]*")))))) {
                  passwordInvalid = true;
            }
            /// M: verify WAPI information @{
            if (mAccessPointSecurity == AccessPoint.SECURITY_WAPI_CERT
                        && (mWapiAsCert != null
                                    && mWapiAsCert.getSelectedItemPosition() == 0 || mWapiClientCert != null
                                    && mWapiClientCert.getSelectedItemPosition() == 0)) {
                  passwordInvalid = true;
            }
            /// @}
            if ((mSsidView != null && mSsidView.length() == 0)
                        || ((mAccessPoint == null || mAccessPoint.networkId == INVALID_NETWORK_ID) && passwordInvalid)) {
                  enabled = false;
            } else {
                  if (ipAndProxyFieldsAreValid()) {
                        enabled = true;
                  } else {
                        enabled = false;
                  }
            }
            submit.setEnabled(enabled);
      }

      /**
       * M: verify password check whether we have got a valid WEP key
       *
       * @param password
       * @return
       */
      private boolean isWEPKeyValid(String password) {
            if (password == null || password.length() == 0) {
                  return false;
            }
            int keyType = 0;// password: auto, ASCII or Hex
            if (mWEPKeyType != null
                        && mWEPKeyType.getSelectedItemPosition() != AdapterView.INVALID_POSITION) {
                  keyType = mWEPKeyType.getSelectedItemPosition();
            }
            int keyLength = password.length();
            if ((keyLength == 10 || keyLength == 26 || keyLength == 32)
                        && password.matches("[0-9A-Fa-f]*")
                        && (keyType == 0 || keyType == 2)) {
                  return true;
            } else if ((keyLength == 5 || keyLength == 13 || keyLength == 16)
                        && (keyType == 0 || keyType == 1)) {
                  return true;
            }
            return false;
      }

      /* package */WifiConfiguration getConfig() {
            /// M: get context
            Context context = mConfigUi.getContext();
            if (mAccessPoint != null
                        && mAccessPoint.networkId != INVALID_NETWORK_ID && !mEdit) {
                  return null;
            }

            WifiConfiguration config = new WifiConfiguration();

            /// M: init eap information @{
            if (FeatureOption.MTK_EAP_SIM_AKA) {
                  config.imsi = addQuote("none");
                  config.simSlot = addQuote("-1");
                  config.pcsc = addQuote("none");
            }
            /// @}

            if (mAccessPoint == null) {
                  config.SSID = AccessPoint.convertToQuotedString(
                		  mSsidView.getText().toString());
                  // If the user adds a network manually, assume that it is hidden.
                  config.hiddenSSID = true;
            } else if (mAccessPoint.networkId == INVALID_NETWORK_ID) {
                  config.SSID = AccessPoint.convertToQuotedString(mAccessPoint.ssid);
                  /// M: save BSSID to configuration
                  config.BSSID = mAccessPoint.bssid;
            } else {
                  config.networkId = mAccessPoint.networkId;
            }
            /// M: get priority of configuration
            if (mExt.getPriority() >= 0) {
                  config.priority = mExt.getPriority();
            }

            switch (mAccessPointSecurity) {
            case AccessPoint.SECURITY_NONE:
                  config.allowedKeyManagement.set(KeyMgmt.NONE);
                  break;

            case AccessPoint.SECURITY_WEP:
                  config.allowedKeyManagement.set(KeyMgmt.NONE);
                  config.allowedAuthAlgorithms.set(AuthAlgorithm.OPEN);
                  config.allowedAuthAlgorithms.set(AuthAlgorithm.SHARED);
                  if (mPasswordView.length() != 0) {
                        int length = mPasswordView.length();
                        String password = mPasswordView.getText().toString();
                        /// M: get selected WEP key index @{
                        int keyIndex = 0;// selected password index, 0~3
                        if (mWEPKeyIndex != null
                                    && mWEPKeyIndex.getSelectedItemPosition() != AdapterView.INVALID_POSITION) {
                              keyIndex = mWEPKeyIndex.getSelectedItemPosition();
                        }
                        /// @}
                        // WEP-40, WEP-104, and 256-bit WEP (WEP-232?)
                        if ((length == 10 || length == 26 || length == 32)
                                    && password.matches("[0-9A-Fa-f]*")) {
                              /// M: hex password
                              config.wepKeys[keyIndex] = password;
                        } else {
                              /// M: ASCII password
                              config.wepKeys[keyIndex] = '"' + password + '"';
                        }
                        /// M: set wep index to configuration
                        config.wepTxKeyIndex = keyIndex;
                  }
                  break;
            case AccessPoint.SECURITY_WPA_PSK:
            case AccessPoint.SECURITY_WPA2_PSK:
            case AccessPoint.SECURITY_PSK:
                  config.allowedKeyManagement.set(KeyMgmt.WPA_PSK);
                  if (mPasswordView.length() != 0) {
                        String password = mPasswordView.getText().toString();
                        if (password.matches("[0-9A-Fa-f]{64}")) {
                              config.preSharedKey = password;
                        } else {
                              config.preSharedKey = '"' + password + '"';
                        }
                  }
                  break;

            case AccessPoint.SECURITY_EAP:
                config.allowedKeyManagement.set(KeyMgmt.WPA_EAP);
                config.allowedKeyManagement.set(KeyMgmt.IEEE8021X);
                config.enterpriseConfig = new WifiEnterpriseConfig();
                int eapMethod = mEapMethodSpinner.getSelectedItemPosition();
                int phase2Method = mPhase2Spinner.getSelectedItemPosition();
                config.enterpriseConfig.setEapMethod(eapMethod);
                //config.eap.setValue((String) mEapMethodSpinner.getSelectedItem());

                if (!"AKA".equals((String) mEapMethodSpinner.getSelectedItem())
                              && !"SIM".equals((String) mEapMethodSpinner.getSelectedItem())) {
                switch (eapMethod) {
                    case Eap.PEAP:
                        // PEAP supports limited phase2 values
                        // Map the index from the PHASE2_PEAP_ADAPTER to the one used
                        // by the API which has the full list of PEAP methods.
                        switch(phase2Method) {
                            case WIFI_PEAP_PHASE2_NONE:
                                config.enterpriseConfig.setPhase2Method(Phase2.NONE);
                                break;
                            case WIFI_PEAP_PHASE2_MSCHAPV2:
                                config.enterpriseConfig.setPhase2Method(Phase2.MSCHAPV2);
                                break;
                            case WIFI_PEAP_PHASE2_GTC:
                                config.enterpriseConfig.setPhase2Method(Phase2.GTC);
                                break;
                            default:
                                Log.e(TAG, "Unknown phase2 method" + phase2Method);
                                break;
                        }
                        break;
                    default:
                        // The default index from PHASE2_FULL_ADAPTER maps to the API
                        config.enterpriseConfig.setPhase2Method(phase2Method);
                        break;
                }
                  } else {
                      eapSimAkaConfig(config);
                      Xlog.d(TAG, "eap-sim/aka, config.toString(): " + config.toString());
                  }
                String caCert = (String) mEapCaCertSpinner.getSelectedItem();
                if (caCert.equals(unspecifiedCert)) caCert = "";
                config.enterpriseConfig.setCaCertificateAlias(caCert);
                String clientCert = (String) mEapUserCertSpinner.getSelectedItem();
                if (clientCert.equals(unspecifiedCert)) clientCert = "";
                config.enterpriseConfig.setClientCertificateAlias(clientCert);
                config.enterpriseConfig.setIdentity(mEapIdentityView.getText().toString());
                config.enterpriseConfig.setAnonymousIdentity(
                        mEapAnonymousView.getText().toString());

                if (mPasswordView.isShown()) {
                    // For security reasons, a previous password is not displayed to user.
                    // Update only if it has been changed.
                    if (mPasswordView.length() > 0) {
                        config.enterpriseConfig.setPassword(mPasswordView.getText().toString());
                    }
                } else {
                    // clear password
                    config.enterpriseConfig.setPassword(mPasswordView.getText().toString());
                }
                break;
            /// M: add WAPI_PSK & WAPI_CERT @{
            case AccessPoint.SECURITY_WAPI_PSK:
                  config.allowedKeyManagement.set(KeyMgmt.WAPI_PSK);
                  config.allowedProtocols.set(Protocol.WAPI);
                  config.allowedPairwiseCiphers.set(PairwiseCipher.SMS4);
                  config.allowedGroupCiphers.set(GroupCipher.SMS4);
                  if (mPasswordView.length() != 0) {
                        String password = mPasswordView.getText().toString();
                        Xlog.v(TAG, "getConfig(), mHex=" + mHex);
                        if (mHex) { /* Hexadecimal */
                              config.preSharedKey = password;
                        } else { /* ASCII */
                              config.preSharedKey = '"' + password + '"';
                        }
                  }
                  break;

            case AccessPoint.SECURITY_WAPI_CERT:
                  config.allowedKeyManagement.set(KeyMgmt.WAPI_CERT);
                  config.allowedProtocols.set(Protocol.WAPI);
                  config.allowedPairwiseCiphers.set(PairwiseCipher.SMS4);
                  config.allowedGroupCiphers.set(GroupCipher.SMS4);
                  config.enterpriseConfig.setCaCertificate2Alias((mWapiAsCert.getSelectedItemPosition() == 0) ? ""
                                          : (String) mWapiAsCert.getSelectedItem());
                  config.enterpriseConfig.setClientCertificate2Alias((mWapiClientCert.getSelectedItemPosition() == 0) ? ""
                                          : (String) mWapiClientCert.getSelectedItem());
                  break;
            /// @}
            default:
                  return null;
            }

            config.proxySettings = mProxySettings;
            config.ipAssignment = mIpAssignment;
            config.linkProperties = new LinkProperties(mLinkProperties);

            return config;
      }

      private boolean ipAndProxyFieldsAreValid() {
            mLinkProperties.clear();
            mIpAssignment = (mIpSettingsSpinner != null && mIpSettingsSpinner
                        .getSelectedItemPosition() == STATIC_IP) ? IpAssignment.STATIC
                        : IpAssignment.DHCP;

            if (mIpAssignment == IpAssignment.STATIC) {
                  int result = validateIpConfigFields(mLinkProperties);
                  if (result != 0) {
                        return false;
                  }
            }

            mProxySettings = (mProxySettingsSpinner != null && mProxySettingsSpinner
                        .getSelectedItemPosition() == PROXY_STATIC) ? ProxySettings.STATIC
                        : ProxySettings.NONE;

            if (mProxySettings == ProxySettings.STATIC && mProxyHostView != null) {
                  String host = mProxyHostView.getText().toString();
                  String portStr = mProxyPortView.getText().toString();
                  String exclusionList = mProxyExclusionListView.getText().toString();
                  int port = 0;
                  int result = 0;
                  try {
                        port = Integer.parseInt(portStr);
                        result = ProxySelector.validate(host, portStr, exclusionList);
                  } catch (NumberFormatException e) {
                        result = R.string.proxy_error_invalid_port;
                  }
                  if (result == 0) {
                        ProxyProperties proxyProperties = new ProxyProperties(host,
                                    port, exclusionList);
                        mLinkProperties.setHttpProxy(proxyProperties);
                  } else {
                        return false;
                  }
            }
            return true;
      }

      private int validateIpConfigFields(LinkProperties linkProperties) {
            if (mIpAddressView == null)
                  return 0;

            String ipAddr = mIpAddressView.getText().toString();
            if (TextUtils.isEmpty(ipAddr))
                  return R.string.wifi_ip_settings_invalid_ip_address;

            InetAddress inetAddr = null;
            try {
                  inetAddr = NetworkUtils.numericToInetAddress(ipAddr);
                  /// M: restrict static IP to IPv4@{
                  if (!(inetAddr instanceof Inet4Address)) {
                      return R.string.wifi_ip_settings_invalid_ip_address;
                  }
                  /// @}
            } catch (IllegalArgumentException e) {
                  return R.string.wifi_ip_settings_invalid_ip_address;
            }

            int networkPrefixLength = -1;
            try {
                  networkPrefixLength = Integer.parseInt(mNetworkPrefixLengthView
                              .getText().toString());
                  if (networkPrefixLength < 0 || networkPrefixLength > 32) {
                        return R.string.wifi_ip_settings_invalid_network_prefix_length;
                  }
                  linkProperties.addLinkAddress(new LinkAddress(inetAddr,
                              networkPrefixLength));
            } catch (NumberFormatException e) {
                  // Set the hint as default after user types in ip address
                  mNetworkPrefixLengthView.setText(mConfigUi.getContext().getString(
                              R.string.wifi_network_prefix_length_hint));
            }

            String gateway = mGatewayView.getText().toString();
            if (TextUtils.isEmpty(gateway)) {
                  try {
                        // Extract a default gateway from IP address
                        InetAddress netPart = NetworkUtils.getNetworkPart(inetAddr,
                                    networkPrefixLength);
                        byte[] addr = netPart.getAddress();
                        addr[addr.length - 1] = 1;
                        mGatewayView.setText(InetAddress.getByAddress(addr)
                                    .getHostAddress());
                  } catch (RuntimeException ee) {
                  } catch (java.net.UnknownHostException u) {
                  }
            } else {
                  InetAddress gatewayAddr = null;
                  try {
                        gatewayAddr = NetworkUtils.numericToInetAddress(gateway);
                  } catch (IllegalArgumentException e) {
                        return R.string.wifi_ip_settings_invalid_gateway;
                  }
                  linkProperties.addRoute(new RouteInfo(gatewayAddr));
            }

            String dns = mDns1View.getText().toString();
            InetAddress dnsAddr = null;

            if (TextUtils.isEmpty(dns)) {
                  // If everything else is valid, provide hint as a default option
                  mDns1View.setText(mConfigUi.getContext().getString(
                              R.string.wifi_dns1_hint));
            } else {
                  try {
                        dnsAddr = NetworkUtils.numericToInetAddress(dns);
                  } catch (IllegalArgumentException e) {
                        return R.string.wifi_ip_settings_invalid_dns;
                  }
                  linkProperties.addDns(dnsAddr);
            }

            if (mDns2View.length() > 0) {
                  dns = mDns2View.getText().toString();
                  try {
                        dnsAddr = NetworkUtils.numericToInetAddress(dns);
                  } catch (IllegalArgumentException e) {
                        return R.string.wifi_ip_settings_invalid_dns;
                  }
                  linkProperties.addDns(dnsAddr);
            }
            return 0;
      }

      private void showSecurityFields() {
            if (mInXlSetupWizard) {
                  // Note: XL SetupWizard won't hide "EAP" settings here.
                  if (!((WifiSettingsForSetupWizardXL) mConfigUi.getContext())
                              .initSecurityFields(mView, mAccessPointSecurity)) {
                        return;
                  }
            }
            /// M: hide advanced toggle & field @{
            if (mAccessPoint == null
                        && mAccessPointSecurity != AccessPoint.SECURITY_EAP) {
                  mView.findViewById(R.id.wifi_advanced_toggle).setVisibility(
                              View.GONE);
                  mView.findViewById(R.id.wifi_advanced_fields).setVisibility(
                              View.GONE);
                  Xlog
                              .d(TAG,
                                          "add network,mAccessPointSecurity != AccessPoint.SECURITY_EAP");
            }
            /// @}
            if (mAccessPointSecurity == AccessPoint.SECURITY_NONE) {
                  mView.findViewById(R.id.security_fields).setVisibility(View.GONE);
                  /// M: hide WAPI_CERT fileds
                  mView.findViewById(R.id.wapi_cert_fields).setVisibility(View.GONE);
                  return;
            }
            mView.findViewById(R.id.security_fields).setVisibility(View.VISIBLE);
            /// M: Hexadecimal checkbox only for WAPI_PSK @{
            mView.findViewById(R.id.hex_password).setVisibility(View.GONE);
            if (mAccessPointSecurity == AccessPoint.SECURITY_WAPI_PSK) {
                  mView.findViewById(R.id.hex_password).setVisibility(View.VISIBLE);
                  ((CheckBox) mView.findViewById(R.id.hex_password)).setChecked(mHex);
            }
            /// @}

            /// M: WEP transmit key & keytype @{
            if (mAccessPointSecurity == AccessPoint.SECURITY_WEP
                        && FeatureOption.WIFI_WEP_KEY_ID_SET) {
                  mView.findViewById(R.id.wep).setVisibility(View.VISIBLE);
                  mWEPKeyType = (Spinner) mView.findViewById(R.id.wep_key_type);
                  mWEPKeyIndex = (Spinner) mView.findViewById(R.id.wep_key_index);
                  if (mWEPKeyType != null) {
                        mWEPKeyType.setOnItemSelectedListener(this);
                  }
            }
            /// @}

            /// M: show WAPI CERT field @{
            if (mAccessPointSecurity == AccessPoint.SECURITY_WAPI_CERT) {
                  mView.findViewById(R.id.security_fields).setVisibility(View.GONE);
                  mView.findViewById(R.id.wapi_cert_fields).setVisibility(
                              View.VISIBLE);
                  mWapiAsCert = (Spinner) mView.findViewById(R.id.wapi_as_cert);
                  mWapiClientCert = (Spinner) mView.findViewById(R.id.wapi_user_cert);
                  mWapiAsCert.setOnItemSelectedListener(this);
                  mWapiClientCert.setOnItemSelectedListener(this);
                  loadCertificates(mWapiAsCert, Credentials.WAPI_AS_CERTIFICATE);
                  loadCertificates(mWapiClientCert, Credentials.WAPI_USER_CERTIFICATE);

                  if (mAccessPoint != null && mAccessPoint.networkId != -1) {
                        WifiConfiguration config = mAccessPoint.getConfig();
                        setCertificate(mWapiAsCert, Credentials.WAPI_AS_CERTIFICATE,
                                config.enterpriseConfig.getCaCertificate2Alias());
                        setCertificate(mWapiClientCert,
                                    Credentials.WAPI_USER_CERTIFICATE, config.enterpriseConfig.getClientCertificate2Alias());
                  }
                  return;
            } else {
                  mView.findViewById(R.id.wapi_cert_fields).setVisibility(View.GONE);
            }
            /// @}

            if (mPasswordView == null) {
                  mPasswordView = (TextView) mView.findViewById(R.id.password);
                  mPasswordView.addTextChangedListener(this);
                  ((CheckBox) mView.findViewById(R.id.show_password))
                          .setOnCheckedChangeListener(this);
                  /// M: set setOnClickListener for hex password
                  ((CheckBox) mView.findViewById(R.id.hex_password))
                              .setOnCheckedChangeListener(this);
                  if (mAccessPoint != null
                              && mAccessPoint.networkId != INVALID_NETWORK_ID) {
                        mPasswordView.setHint(R.string.wifi_unchanged);
                  }
            }

            if (mAccessPointSecurity != AccessPoint.SECURITY_EAP) {
                 /// M:  change eap to other security @{
                 ((TextView) mView.findViewById(R.id.identity)).setEnabled(true);
                 mPasswordView.setEnabled(true);
                 ((CheckBox) mView.findViewById(R.id.show_password)).setEnabled(true);
                 /// @}
                  mView.findViewById(R.id.eap).setVisibility(View.GONE);
                  /// M: hide eap identity if current security is not EAP
                  mView.findViewById(R.id.eap_identity).setVisibility(View.GONE);
                  return;
            }
            mView.findViewById(R.id.eap).setVisibility(View.VISIBLE);
            /// M: show eap identity field @{
            mView.findViewById(R.id.eap_identity).setVisibility(View.VISIBLE);
            View advancedView = mView.findViewById(R.id.wifi_advanced_toggle);
            if (mAccessPoint == null && !advancedView.isShown()) {
                  Xlog.d(TAG, "add network,Security is AccessPoint.SECURITY_EAP");
                  mView.findViewById(R.id.wifi_advanced_toggle).setVisibility(
                              View.VISIBLE);
                  ((CheckBox)mView.findViewById(R.id.wifi_advanced_togglebox))
                              .setOnCheckedChangeListener(this);
                  ((CheckBox) mView.findViewById(R.id.wifi_advanced_togglebox))
                              .setChecked(false);
                  mView.findViewById(R.id.wifi_advanced_fields).setVisibility(
                              View.GONE);
            }
            /// @}

            if (mEapMethodSpinner == null) {
                  mEapMethodSpinner = (Spinner) mView.findViewById(R.id.method);
                  /// M: set array for eap method spinner. show simslot in gemini
                  // load @{
                  if (FeatureOption.MTK_EAP_SIM_AKA) {
                        Context context = mConfigUi.getContext();
                        final ArrayAdapter<String> adapter = new ArrayAdapter<String>(
                                    context, android.R.layout.simple_spinner_item, context
                                                .getResources().getStringArray(
                                                            R.array.wifi_eap_method_sim_aka));
                        adapter
                                    .setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                        mEapMethodSpinner.setAdapter(adapter);
                  }
                  /// @}

                  mEapMethodSpinner.setOnItemSelectedListener(this);
                  mPhase2Spinner = (Spinner) mView.findViewById(R.id.phase2);
                  mEapCaCertSpinner = (Spinner) mView.findViewById(R.id.ca_cert);
                  mEapUserCertSpinner = (Spinner) mView.findViewById(R.id.user_cert);
                  mEapIdentityView = (TextView) mView.findViewById(R.id.identity);
                  mEapAnonymousView = (TextView) mView.findViewById(R.id.anonymous);

                  loadCertificates(mEapCaCertSpinner, Credentials.CA_CERTIFICATE);
                  loadCertificates(mEapUserCertSpinner, Credentials.USER_PRIVATE_KEY);

            // Modifying an existing network
            if (mAccessPoint != null && mAccessPoint.networkId != INVALID_NETWORK_ID) {
                WifiEnterpriseConfig enterpriseConfig = mAccessPoint.getConfig().enterpriseConfig;
                int eapMethod = enterpriseConfig.getEapMethod();
                int phase2Method = enterpriseConfig.getPhase2Method();
                mEapMethodSpinner.setSelection(eapMethod);
                showEapFieldsByMethod(eapMethod);
                switch (eapMethod) {
                    case Eap.PEAP:
                        switch (phase2Method) {
                            case Phase2.NONE:
                                mPhase2Spinner.setSelection(WIFI_PEAP_PHASE2_NONE);
                                break;
                            case Phase2.MSCHAPV2:
                                mPhase2Spinner.setSelection(WIFI_PEAP_PHASE2_MSCHAPV2);
                                break;
                            case Phase2.GTC:
                                mPhase2Spinner.setSelection(WIFI_PEAP_PHASE2_GTC);
                                break;
                            default:
                                Log.e(TAG, "Invalid phase 2 method " + phase2Method);
                                break;
                        }
                        break;
                    default:
                        mPhase2Spinner.setSelection(phase2Method);
                        break;
                }
                setSelection(mEapCaCertSpinner, enterpriseConfig.getCaCertificateAlias());
                setSelection(mEapUserCertSpinner, enterpriseConfig.getClientCertificateAlias());
                mEapIdentityView.setText(enterpriseConfig.getIdentity());
                mEapAnonymousView.setText(enterpriseConfig.getAnonymousIdentity());
            } else {
                // Choose a default for a new network and show only appropriate
                // fields
                mEapMethodSpinner.setSelection(Eap.PEAP);
                showEapFieldsByMethod(Eap.PEAP);
            }
        } else {
            showEapFieldsByMethod(mEapMethodSpinner.getSelectedItemPosition());
        }
            /// M: eap-sim/aka
            if (mEapMethodSpinner.getSelectedItemPosition() >= WIFI_EAP_METHOD_SIM) {
                  mView.findViewById(R.id.l_phase2).setVisibility(View.GONE);
                  mView.findViewById(R.id.l_ca_cert).setVisibility(View.GONE);
                  mView.findViewById(R.id.l_user_cert).setVisibility(View.GONE);
                  mView.findViewById(R.id.l_anonymous).setVisibility(View.GONE);
            }
            /// M: @{
            if (mEapMethodSpinner.getSelectedItemPosition() == WIFI_EAP_METHOD_SIM
                        || mEapMethodSpinner.getSelectedItemPosition() == WIFI_EAP_METHOD_AKA) {
                  mEapIdentityView.setEnabled(false);
                  mPasswordView.setEnabled(false);
                  ((CheckBox) mView.findViewById(R.id.show_password))
                              .setEnabled(false);
                  if (FeatureOption.MTK_GEMINI_SUPPORT) {
                        mView.findViewById(R.id.sim_slot_fields).setVisibility(
                                    View.VISIBLE);
                        mSimSlot = (Spinner) mView.findViewById(R.id.sim_slot);
                        /// M:Geminu plus @{
                        Context context = mConfigUi.getContext();
                        String[] tempSimAkaMethods = context.getResources().getStringArray(R.array.sim_slot);
                        int sum = PhoneConstants.GEMINI_SIM_NUM + 1;
                        Xlog.d(TAG, "the num of sim slot is :" + (sum - 1));
                        String[] simAkaMethods = new String[sum];
                        for (int i = 0; i < sum; i++) {
                            if (i < tempSimAkaMethods.length) {
                                simAkaMethods[i] = tempSimAkaMethods[i];
                            } else {
                                simAkaMethods[i] = tempSimAkaMethods[1].replaceAll("1", "" + i);
                            }
                         }
                        final ArrayAdapter<String> adapter = new ArrayAdapter<String>
                                          (context, android.R.layout.simple_spinner_item,simAkaMethods);
                        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                        mSimSlot.setAdapter(adapter);
                        ///@}

                        ///M: setting had selected simslot @{
                        if (mAccessPoint != null
                                && mAccessPoint.networkId != INVALID_NETWORK_ID) {
                            WifiConfiguration config = mAccessPoint.getConfig();
                            if (config != null && config.simSlot != null) {
                            String[] simslots = config.simSlot.split("\"");
                                if (simslots.length > 1) {
                                int slot = Integer.parseInt(simslots[1]) + 1;
                                mSimSlot.setSelection(slot);
                                }
                            }  
                        }
                        /// @}
                  }
            } else {
                  mEapIdentityView.setEnabled(true);
                  mPasswordView.setEnabled(true);
                  ((CheckBox) mView.findViewById(R.id.show_password))
                              .setEnabled(true);
                  if (FeatureOption.MTK_GEMINI_SUPPORT) {
                        mView.findViewById(R.id.sim_slot_fields).setVisibility(
                                    View.GONE);
                  }
            }
            /// @}
            
            /// M: eap method changed, and current eap method not equals config's eap method@{
            if (mAccessPoint != null && mAccessPoint.networkId != INVALID_NETWORK_ID
                    && mEapMethodSpinner != null && mEapAnonymousView != null) {
                ArrayAdapter<String> adapter = (ArrayAdapter<String>) mEapMethodSpinner.getAdapter();
                WifiConfiguration config = mAccessPoint.getConfig();
                int i = mEapMethodSpinner.getSelectedItemPosition();
                if (config.enterpriseConfig != null
                        && adapter != null && !(config.enterpriseConfig.getEapMethod() == i)) {
                    mEapAnonymousView.setText(null);
                }
            }
            /// @}
    }

     /**
     * EAP-PWD valid fields include
     *   identity
     *   password
     * EAP-PEAP valid fields include
     *   phase2: MSCHAPV2, GTC
     *   ca_cert
     *   identity
     *   anonymous_identity
     *   password
     * EAP-TLS valid fields include
     *   user_cert
     *   ca_cert
     *   identity
     * EAP-TTLS valid fields include
     *   phase2: PAP, MSCHAP, MSCHAPV2, GTC
     *   ca_cert
     *   identity
     *   anonymous_identity
     *   password
     */
    private void showEapFieldsByMethod(int eapMethod) {
        // Common defaults
        mView.findViewById(R.id.l_method).setVisibility(View.VISIBLE);
        mView.findViewById(R.id.l_identity).setVisibility(View.VISIBLE);

        // Defaults for most of the EAP methods and over-riden by
        // by certain EAP methods
        mView.findViewById(R.id.l_ca_cert).setVisibility(View.VISIBLE);
        mView.findViewById(R.id.password_layout).setVisibility(View.VISIBLE);
        mView.findViewById(R.id.show_password_layout).setVisibility(View.VISIBLE);

        Context context = mConfigUi.getContext();
        switch (eapMethod) {
            case WIFI_EAP_METHOD_PWD:
                setPhase2Invisible();
                setCaCertInvisible();
                setAnonymousIdentInvisible();
                setUserCertInvisible();
                break;
            case WIFI_EAP_METHOD_TLS:
                mView.findViewById(R.id.l_user_cert).setVisibility(View.VISIBLE);
                setPhase2Invisible();
                setAnonymousIdentInvisible();
                setPasswordInvisible();
                break;
            case WIFI_EAP_METHOD_PEAP:
                // Reset adapter if needed
                if (mPhase2Adapter != PHASE2_PEAP_ADAPTER) {
                    mPhase2Adapter = PHASE2_PEAP_ADAPTER;
                    mPhase2Spinner.setAdapter(mPhase2Adapter);
                }
                mView.findViewById(R.id.l_phase2).setVisibility(View.VISIBLE);
                mView.findViewById(R.id.l_anonymous).setVisibility(View.VISIBLE);
                setUserCertInvisible();
                break;
            case WIFI_EAP_METHOD_TTLS:
                // Reset adapter if needed
                if (mPhase2Adapter != PHASE2_FULL_ADAPTER) {
                    mPhase2Adapter = PHASE2_FULL_ADAPTER;
                    mPhase2Spinner.setAdapter(mPhase2Adapter);
                }
                mView.findViewById(R.id.l_phase2).setVisibility(View.VISIBLE);
                mView.findViewById(R.id.l_anonymous).setVisibility(View.VISIBLE);
                setUserCertInvisible();
                break;
        }
    }

    private void setPhase2Invisible() {
        mView.findViewById(R.id.l_phase2).setVisibility(View.GONE);
        mPhase2Spinner.setSelection(Phase2.NONE);
    }

    private void setCaCertInvisible() {
        mView.findViewById(R.id.l_ca_cert).setVisibility(View.GONE);
        mEapCaCertSpinner.setSelection(unspecifiedCertIndex);
    }

    private void setUserCertInvisible() {
        mView.findViewById(R.id.l_user_cert).setVisibility(View.GONE);
        mEapUserCertSpinner.setSelection(unspecifiedCertIndex);
    }

    private void setAnonymousIdentInvisible() {
        mView.findViewById(R.id.l_anonymous).setVisibility(View.GONE);
        mEapAnonymousView.setText("");
    }

    private void setPasswordInvisible() {
        mPasswordView.setText("");
        mView.findViewById(R.id.password_layout).setVisibility(View.GONE);
        mView.findViewById(R.id.show_password_layout).setVisibility(View.GONE);
    }

      private void showIpConfigFields() {
            WifiConfiguration config = null;

            mView.findViewById(R.id.ip_fields).setVisibility(View.VISIBLE);

            if (mAccessPoint != null && mAccessPoint.networkId != INVALID_NETWORK_ID) {
                  config = mAccessPoint.getConfig();
            }

            if (mIpSettingsSpinner.getSelectedItemPosition() == STATIC_IP) {
                  mView.findViewById(R.id.staticip).setVisibility(View.VISIBLE);
                  if (mIpAddressView == null) {
                        mIpAddressView = (TextView) mView.findViewById(R.id.ipaddress);
                        mIpAddressView.addTextChangedListener(this);
                        mGatewayView = (TextView) mView.findViewById(R.id.gateway);
                        mGatewayView.addTextChangedListener(this);
                        mNetworkPrefixLengthView = (TextView) mView.findViewById(
                             R.id.network_prefix_length);
                        mNetworkPrefixLengthView.addTextChangedListener(this);
                        mDns1View = (TextView) mView.findViewById(R.id.dns1);
                        mDns1View.addTextChangedListener(this);
                        mDns2View = (TextView) mView.findViewById(R.id.dns2);
                        mDns2View.addTextChangedListener(this);
                  }
                  if (config != null) {
                      /// M:restrict static IP to IPv4 @{
                        LinkProperties linkProperties = config.linkProperties;
                        Iterator<LinkAddress> iterator = linkProperties
                                    .getLinkAddresses().iterator();
                        while (iterator.hasNext()) {
                              LinkAddress linkAddress = iterator.next();
                              Xlog.d(TAG, "IpAddressView = " + linkAddress);
                              if (linkAddress.getAddress() instanceof Inet4Address) {
                              mIpAddressView.setText(linkAddress.getAddress()
                                          .getHostAddress());
                              mNetworkPrefixLengthView.setText(Integer
                                          .toString(linkAddress.getNetworkPrefixLength()));
                                  break;  
                              }
                        }

                        for (RouteInfo route : linkProperties.getRoutes()) {
                              Xlog.d(TAG, "RouteInfo = " + route);
                              if (route.isDefaultRoute() && (route.getGateway() instanceof Inet4Address)) {
                                    mGatewayView.setText(route.getGateway()
                                                .getHostAddress());
                                    break;
                              }
                        }

                        Iterator<InetAddress> dnsIterator = linkProperties.getDnses()
                                    .iterator();
                        while (dnsIterator.hasNext()) {
                              InetAddress dsn1 = dnsIterator.next();
                              Xlog.d(TAG, "dsn1 = " + dsn1);
                              if (dsn1 instanceof Inet4Address) {
                                  mDns1View.setText(dsn1.getHostAddress()); 
                                  break;
                              }
                        }
                        while (dnsIterator.hasNext()) {
                            InetAddress dsn2 = dnsIterator.next();
                            Xlog.d(TAG, "dsn2 = " + dsn2);
                          if (dsn2 instanceof Inet4Address) {
                              mDns2View.setText(dsn2.getHostAddress()); 
                              break;
                        }
                        }
                        /// @}
                  }
            } else {
                  mView.findViewById(R.id.staticip).setVisibility(View.GONE);
            }
      }

      private void showProxyFields() {
            WifiConfiguration config = null;

            mView.findViewById(R.id.proxy_settings_fields).setVisibility(
                        View.VISIBLE);

            if (mAccessPoint != null
                        && mAccessPoint.networkId != INVALID_NETWORK_ID) {
                  config = mAccessPoint.getConfig();
            }

            if (mProxySettingsSpinner.getSelectedItemPosition() == PROXY_STATIC) {
                  mView.findViewById(R.id.proxy_warning_limited_support)
                              .setVisibility(View.VISIBLE);
                  mView.findViewById(R.id.proxy_fields).setVisibility(View.VISIBLE);
                  if (mProxyHostView == null) {
                        mProxyHostView = (TextView) mView
                                    .findViewById(R.id.proxy_hostname);
                        mProxyHostView.addTextChangedListener(this);
                        mProxyPortView = (TextView) mView.findViewById(R.id.proxy_port);
                        mProxyPortView.addTextChangedListener(this);
                        mProxyExclusionListView = (TextView) mView
                                    .findViewById(R.id.proxy_exclusionlist);
                        mProxyExclusionListView.addTextChangedListener(this);

                        /// M: set text of proxy exclusion list @{
                        TextView proxyText = (TextView) mView
                                    .findViewById(R.id.proxy_exclusionlist_text);
                        mExt.setProxyText(proxyText);
                        /// @}
                  }
                  if (config != null) {
                        ProxyProperties proxyProperties = config.linkProperties
                                    .getHttpProxy();
                        if (proxyProperties != null) {
                              mProxyHostView.setText(proxyProperties.getHost());
                              mProxyPortView.setText(Integer.toString(proxyProperties
                                          .getPort()));
                              mProxyExclusionListView.setText(proxyProperties
                                          .getExclusionList());
                        }
                  }
            } else {
                  mView.findViewById(R.id.proxy_warning_limited_support)
                              .setVisibility(View.GONE);
                  mView.findViewById(R.id.proxy_fields).setVisibility(View.GONE);
            }
      }



    private void loadCertificates(Spinner spinner, String prefix) {
        final Context context = mConfigUi.getContext();

        String[] certs = KeyStore.getInstance().saw(prefix, android.os.Process.WIFI_UID);
        if (certs == null || certs.length == 0) {
            certs = new String[] {unspecifiedCert};
        } else {
            final String[] array = new String[certs.length + 1];
            array[0] = unspecifiedCert;
            System.arraycopy(certs, 0, array, 1, certs.length);
            certs = array;
        }

        final ArrayAdapter<String> adapter = new ArrayAdapter<String>(
                context, android.R.layout.simple_spinner_item, certs);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
    }

    private void setCertificate(Spinner spinner, String prefix, String cert) {
        if (cert != null && cert.startsWith(prefix)) {
            setSelection(spinner, cert.substring(prefix.length()));
        }
    }

      private void setSelection(Spinner spinner, String value) {
            if (value != null) {
                  @SuppressWarnings("unchecked")
                  ArrayAdapter<String> adapter = (ArrayAdapter<String>) spinner
                              .getAdapter();
                  for (int i = adapter.getCount() - 1; i >= 0; --i) {
                        if (value.equals(adapter.getItem(i))) {
                              spinner.setSelection(i);
                              break;
                        }
                  }
            }
      }

      public boolean isEdit() {
            return mEdit;
      }

      @Override
      public void afterTextChanged(Editable s) {
            mTextViewChangedHandler.post(new Runnable() {
                  public void run() {
                        enableSubmitIfAppropriate();
                  }
            });
      }

      @Override
      public void beforeTextChanged(CharSequence s, int start, int count,
                  int after) {
            // work done in afterTextChanged
      }

      @Override
      public void onTextChanged(CharSequence s, int start, int before, int count) {
            // work done in afterTextChanged
      }
      
      @Override
      public void onCheckedChanged(CompoundButton view, boolean isChecked) {
          if (view.getId() == R.id.show_password) {
              int pos = mPasswordView.getSelectionEnd();
              mPasswordView.setInputType(
                      InputType.TYPE_CLASS_TEXT | (isChecked ?
                              InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD :
                                  InputType.TYPE_TEXT_VARIATION_PASSWORD));
              if (pos >= 0) {
                  ((EditText)mPasswordView).setSelection(pos);
              }
          } else if (view.getId() == R.id.wifi_advanced_togglebox) {
              if (isChecked) {
                  mView.findViewById(R.id.wifi_advanced_fields).setVisibility(View.VISIBLE);
              } else {
                  mView.findViewById(R.id.wifi_advanced_fields).setVisibility(View.GONE);
              }
          } else if (view.getId() == R.id.hex_password) {
              /// M: verify password if hex check box is clicked @{
              mHex = ((CheckBox) view).isChecked();
              enableSubmitIfAppropriate();
              Xlog.d(TAG, "onClick mHex is=" + mHex
                          + ",enableSubmitIfAppropriate");
              /// @}
          } else if (view.getId() == R.id.wep_key_type) {
              /// M: verify password if wep key type is clicked
              enableSubmitIfAppropriate();
          }
      }

      @Override
      public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
            if (parent == mSecuritySpinner) {
                  mAccessPointSecurity = position;
                  /// M: get current access point security @{
                  if (FeatureOption.MTK_WAPI_SUPPORT
                              && (SystemProperties.get(WLAN_PROP_KEY, DEFAULT_WLAN_PROP)
                                          .equals(WAPI))) {// only WPAI supported
                        /*
                         * Need to shift only when persist.sys.wlan=="wapi". Only need
                         * to shift if WAPI_SUPPORT=yes && persist.sys.wlan=="wapi"
                         */
                        if (0 < mAccessPointSecurity) {
                              mAccessPointSecurity += AccessPoint.SECURITY_WAPI_PSK
                                          - AccessPoint.SECURITY_WEP;
                        }
                  } else if (!AccessPoint.isWFATestSupported()) {
                        if (mAccessPointSecurity > AccessPoint.SECURITY_PSK) {
                              mAccessPointSecurity += 2;
                        }
                  } else {
                        if (mAccessPointSecurity > AccessPoint.SECURITY_WEP) {
                              mAccessPointSecurity += 1;
                        }
                  }
                  /// @}
                  showSecurityFields();
            } else if (parent == mEapMethodSpinner) {
                  showSecurityFields();
            } else if (parent == mProxySettingsSpinner) {
                  showProxyFields();
            } else if (parent == mIpSettingsSpinner) {
                  showIpConfigFields();
            }
            enableSubmitIfAppropriate();
      }

      @Override
      public void onNothingSelected(AdapterView<?> parent) {
            //
      }

    /**
     * Make the characters of the password visible if show_password is checked.
     */
    private void updatePasswordVisibility(boolean checked) {
        int pos = mPasswordView.getSelectionEnd();
        mPasswordView.setInputType(
                InputType.TYPE_CLASS_TEXT | (checked ?
                        InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD :
                            InputType.TYPE_TEXT_VARIATION_PASSWORD));
        if (pos >= 0) {
            ((EditText)mPasswordView).setSelection(pos);
        }
    }

      /**
       * M: switch WLAN security spinner
       */
      private void switchWlanSecuritySpinner(Spinner securitySpinner) {
            mSecuritySpinner = securitySpinner;
            ((Spinner) mView.findViewById(R.id.security)).setVisibility(View.GONE);
            ((Spinner) mView.findViewById(R.id.wapi_security))
                        .setVisibility(View.GONE);
            ((Spinner) mView.findViewById(R.id.wpa_security))
                        .setVisibility(View.GONE);
            ((Spinner) mView.findViewById(R.id.security_wfa))
                        .setVisibility(View.GONE);
            ((Spinner) mView.findViewById(R.id.wpa_security_wfa))
                        .setVisibility(View.GONE);

            securitySpinner.setVisibility(View.VISIBLE);
            securitySpinner.setOnItemSelectedListener(this);
      }

      /**
       * M: close spinner when configuration change
       */
      public void closeSpinnerDialog() {
            if (mSecuritySpinner != null && mSecuritySpinner.isPopupShowing()) {
                  mSecuritySpinner.dismissPopup();
            } else if (mEapMethodSpinner != null
                        && mEapMethodSpinner.isPopupShowing()) {
                  mEapMethodSpinner.dismissPopup();
            } else if (mEapCaCertSpinner != null
                        && mEapCaCertSpinner.isPopupShowing()) {
                  mEapCaCertSpinner.dismissPopup();
            } else if (mPhase2Spinner != null && mPhase2Spinner.isPopupShowing()) {
                  mPhase2Spinner.dismissPopup();
            } else if (mEapUserCertSpinner != null
                        && mEapUserCertSpinner.isPopupShowing()) {
                  mEapUserCertSpinner.dismissPopup();
            } else if (mIpSettingsSpinner != null
                        && mIpSettingsSpinner.isPopupShowing()) {
                  mIpSettingsSpinner.dismissPopup();
            } else if (mProxySettingsSpinner != null
                        && mProxySettingsSpinner.isPopupShowing()) {
                  mProxySettingsSpinner.dismissPopup();
            } else if (mSimSlot != null && mSimSlot.isPopupShowing()) {
                  mSimSlot.dismissPopup();
            } else if (mWapiAsCert != null && mWapiAsCert.isPopupShowing()) {
                  mWapiAsCert.dismissPopup();
            } else if (mWapiClientCert != null && mWapiClientCert.isPopupShowing()) {
                  mWapiClientCert.dismissPopup();
            }
            mExt.closeSpinnerDialog();
      }
      /**
      * M: Geminu plus
      */
      private void eapSimAkaConfig(WifiConfiguration config) {
        if (mSimSlot == null) {
            Xlog.d(TAG, "mSimSlot is null");
            mSimSlot = (Spinner) mView.findViewById(R.id.sim_slot);
        }
        String strSimAka = (String) mEapMethodSpinner.getSelectedItem();
        if (FeatureOption.MTK_EAP_SIM_AKA) {
            if (FeatureOption.MTK_GEMINI_SUPPORT) {
                Xlog.d(TAG, "((String) mSimSlot.getSelectedItem()) " + ((String) mSimSlot.getSelectedItem()));
                //Xlog.d(TAG, "R.string.eap_sim_slot_0 " + context.getString(R.string.eap_sim_slot_0));
                simSlotConfig(config, strSimAka);
                Xlog.d(TAG, "eap-sim, choose sim_slot" + (String) mSimSlot.getSelectedItem());
            } else {
                config.imsi = makeNAI(mTm.getSimOperator(), mTm.getSubscriberId(), strSimAka);
                Xlog.d(TAG, "config.imsi: " + config.imsi);
                config.simSlot = addQuote("0");
                config.pcsc = addQuote("rild");
            }
            Xlog.d(TAG, "eap-sim, config.imsi: " + config.imsi);
            Xlog.d(TAG, "eap-sim, config.simSlot: " + config.simSlot);
        }
    }
    
    /**
     * M: Geminu plus
     */   
    private void simSlotConfig(WifiConfiguration config, String strSimAka) {
        int simSlot = mSimSlot.getSelectedItemPosition() - 1;
        if (simSlot > -1) {
            try {
                config.imsi = makeNAI(mTelephonyManagerEx.getSimOperator(simSlot),
                        mTelephonyEx.getSubscriberId(simSlot), strSimAka);
            } catch (RemoteException ex) {
                Xlog.d(TAG, "RemoteException when get subscriber id");
                return;
            }
            Xlog.d(TAG, "config.imsi: " + config.imsi);
            config.simSlot = addQuote("" + simSlot);
            Xlog.d(TAG, "config.simSlot " + addQuote("" + simSlot));
            config.pcsc = addQuote("rild");
            Xlog.d(TAG, "config.pcsc: " + addQuote("rild"));
        } 
    }
    
    /**
     * M: add ipv4 and ipv6 address
     * @param config The current AP's config
     * @param group The current view's group
     */
    private void addIpAddressRows(WifiConfiguration config, ViewGroup group) {
        Xlog.d(TAG, "WifiConfigController, config = " + config);
        Xlog.d(TAG, "config.linkProperties.getAddresses() is empty : " + config.linkProperties.getAddresses().isEmpty());
        if (!(config.linkProperties.getAddresses().isEmpty())) {
            final Context context = mConfigUi.getContext();
            WifiManager wifiManager = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            WifiInfo lastInfo = wifiManager.getConnectionInfo();
            int currentConnNetworkId = -1;
            if (lastInfo != null) {
                currentConnNetworkId = lastInfo.getNetworkId();
            }
            Xlog.d(TAG, "currentConnNetworkId = " + currentConnNetworkId);
            Xlog.d(TAG, "config.networkId = " + config.networkId);
            // this ap is the current connected ap
            if (currentConnNetworkId == config.networkId) {
                String ipAddress = Utils.getWifiIpAddresses();
                Xlog.d(TAG, "the ipAddress is : " + ipAddress);
                if (FeatureOption.MTK_DHCPV6C_WIFI && ipAddress != null) {
                    String[] ipAddresses = ipAddress.split(", ");
                    int ipAddressesLength = ipAddresses.length;
                    for (int i = 0; i < ipAddressesLength; i++) {
                        if (ipAddresses[i].indexOf(":") == AdvancedWifiSettings.NOT_FOUND_STRING) {
                            addRow(group, R.string.wifi_ipv4_address, ipAddresses[i]);
                        } else  {
                            addRow(group, R.string.wifi_ipv6_address, ipAddresses[i]);
                        }
                    }
                } else {
                    for (InetAddress a : config.linkProperties.getAddresses()) {
                        addRow(group, R.string.wifi_ip_address, a.getHostAddress());
                    }
                }
            } else {
                // this ap is not the current connected ap,but it has static ip address
                for (InetAddress a : config.linkProperties.getAddresses()) {
                    Xlog.d(TAG, "HostAddress : " + a.getHostAddress());
                    Xlog.d(TAG, "Address Length : " + a.getAddress().length);
                    if (FeatureOption.MTK_DHCPV6C_WIFI) {
                        if (a.getAddress().length == IPV4_ADDRESS_LENGTH) {
                            addRow(group, R.string.wifi_ipv4_address, a.getHostAddress());
                        } else if (a.getAddress().length == IPV6_ADDRESS_LENGTH) {
                            addRow(group, R.string.wifi_ipv6_address, a.getHostAddress());
                        }  
                    } else {
                        addRow(group, R.string.wifi_ip_address, a.getHostAddress());
                    }
                }
            }
        }
    }
}
