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
import static android.os.UserManager.DISALLOW_CONFIG_WIFI;

import com.android.settings.R;
import com.android.settings.RestrictedSettingsFragment;
import com.android.settings.wifi.p2p.WifiP2pSettings;

import android.app.ActionBar;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.app.Dialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;/// M
import android.location.LocationManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.NetworkInfo.DetailedState;
import android.net.wifi.ScanResult;
import android.net.wifi.SupplicantState;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.net.wifi.WpsInfo;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceCategory;
import android.preference.PreferenceGroup;
import android.preference.PreferenceScreen;
import android.provider.Settings;
import android.util.AttributeSet;
import android.util.Log;
import android.util.TypedValue;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.PopupMenu;
import android.widget.PopupMenu.OnMenuItemClickListener;
import android.widget.RelativeLayout;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.android.settings.Utils;
import com.mediatek.common.featureoption.FeatureOption;
import com.mediatek.common.telephony.ITelephonyEx;
import com.mediatek.settings.ext.IWifiSettingsExt;
import com.mediatek.settings.OobeUtils;
import com.mediatek.telephony.TelephonyManagerEx;
import com.mediatek.wifi.PasspointSettings;
import com.mediatek.xlog.Xlog;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Two types of UI are provided here.
 *
 * The first is for "usual Settings", appearing as any other Setup fragment.
 *
 * The second is for Setup Wizard, with a simplified interface that hides the action bar
 * and menus.
 */
public class WifiSettings extends RestrictedSettingsFragment
        implements DialogInterface.OnClickListener  {
    private static final String TAG = "WifiSettings";
    private static final int MENU_ID_WPS_PBC = Menu.FIRST;
    private static final int MENU_ID_WPS_PIN = Menu.FIRST + 1;
    private static final int MENU_ID_P2P = Menu.FIRST + 2;
    private static final int MENU_ID_ADD_NETWORK = Menu.FIRST + 3;
    private static final int MENU_ID_ADVANCED = Menu.FIRST + 4;
    private static final int MENU_ID_SCAN = Menu.FIRST + 5;
    private static final int MENU_ID_CONNECT = Menu.FIRST + 6;
    private static final int MENU_ID_FORGET = Menu.FIRST + 7;
    private static final int MENU_ID_MODIFY = Menu.FIRST + 8;
    private static final int MENU_ID_DISCONNECT = Menu.FIRST + 9;

    private static final int WIFI_DIALOG_ID = 1;
    private static final int WPS_PBC_DIALOG_ID = 2;
    private static final int WPS_PIN_DIALOG_ID = 3;
    private static final int WIFI_SKIPPED_DIALOG_ID = 4;
    private static final int WIFI_AND_MOBILE_SKIPPED_DIALOG_ID = 5;


    // Combo scans can take 5-6s to complete - set to 10s.
    /// M: change interval time to 6s
    private static final int WIFI_RESCAN_INTERVAL_MS = 6 * 1000;

    // Instance state keys
    private static final String SAVE_DIALOG_EDIT_MODE = "edit_mode";
    private static final String SAVE_DIALOG_ACCESS_POINT_STATE = "wifi_ap_state";

    // Activity result when pressing the Skip button
    private static final int RESULT_SKIP = Activity.RESULT_FIRST_USER;

    private final IntentFilter mFilter;
    private final BroadcastReceiver mReceiver;
    private final Scanner mScanner;

    /// M:  @{
    private static final String WLAN_AP_AND_GPRS = "access_points_and_gprs";
    private static final String TRUST_AP = "trust_access_points";
    private static final String CONFIGED_AP = "configed_access_points";
    private static final String NEW_AP = "new_access_points";
    private ITelephonyEx mTelephonyEx;
    private TelephonyManagerEx mTelephonyManagerEx;
    /// @}

    private WifiManager mWifiManager;
    private WifiManager.ActionListener mConnectListener;
    private WifiManager.ActionListener mSaveListener;
    private WifiManager.ActionListener mForgetListener;
    private boolean mP2pSupported;

    private WifiEnabler mWifiEnabler;
    // An access point being editted is stored here.
    private AccessPoint mSelectedAccessPoint;

    private DetailedState mLastState;
    private WifiInfo mLastInfo;

    private final AtomicBoolean mConnected = new AtomicBoolean(false);

    private WifiDialog mDialog;

    private TextView mEmptyView;

    /* Used in Wifi Setup context */

    // this boolean extra specifies whether to disable the Next button when not connected
    private static final String EXTRA_ENABLE_NEXT_ON_CONNECT = "wifi_enable_next_on_connect";

    // this boolean extra specifies whether to auto finish when connection is established
    private static final String EXTRA_AUTO_FINISH_ON_CONNECT = "wifi_auto_finish_on_connect";

    // this boolean extra shows a custom button that we can control
    protected static final String EXTRA_SHOW_CUSTOM_BUTTON = "wifi_show_custom_button";

    // show a text regarding data charges when wifi connection is required during setup wizard
    protected static final String EXTRA_SHOW_WIFI_REQUIRED_INFO = "wifi_show_wifi_required_info";

    // this boolean extra is set if we are being invoked by the Setup Wizard
    private static final String EXTRA_IS_FIRST_RUN = "firstRun";

    // should Next button only be enabled when we have a connection?
    private boolean mEnableNextOnConnection;

    // should activity finish once we have a connection?
    private boolean mAutoFinishOnConnection;

    // Save the dialog details
    private boolean mDlgEdit;
    private AccessPoint mDlgAccessPoint;
    private Bundle mAccessPointSavedState;

    // the action bar uses a different set of controls for Setup Wizard
    private boolean mSetupWizardMode;

    /* End of "used in Wifi Setup context" */

    /// M: add for plug in @{
    IWifiSettingsExt mExt;
    /// @}
    private boolean mManuallyConnect;


    ///M: oobe 
    private View mAddApView;

    /// M: print performance log
    private boolean mScanResultsAvailable = false;


    public WifiSettings() {
        super(DISALLOW_CONFIG_WIFI);
        mFilter = new IntentFilter();
        mFilter.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION);
        mFilter.addAction(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION);
        mFilter.addAction(WifiManager.NETWORK_IDS_CHANGED_ACTION);
        mFilter.addAction(WifiManager.SUPPLICANT_STATE_CHANGED_ACTION);
        mFilter.addAction(WifiManager.CONFIGURED_NETWORKS_CHANGED_ACTION);
        mFilter.addAction(WifiManager.LINK_CONFIGURATION_CHANGED_ACTION);
        mFilter.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION);
        mFilter.addAction(WifiManager.RSSI_CHANGED_ACTION);
        /// M: add no WAPI certification action
        mFilter.addAction(WifiManager.NO_CERTIFICATION_ACTION);

        mReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                handleEvent(context, intent);
            }
        };

        mScanner = new Scanner();
        mManuallyConnect = false;
    }

    @Override
    public void onCreate(Bundle icicle) {
        // Set this flag early, as it's needed by getHelpResource(), which is called by super
        mSetupWizardMode = getActivity().getIntent().getBooleanExtra(EXTRA_IS_FIRST_RUN, false);

        super.onCreate(icicle);

        /// M: get plug in
        mExt = Utils.getWifiSettingsPlugin(getActivity());
        
        /// M: add for oobe 
        mSetupWizardMode = OobeUtils.isOobeMode(this) ? true : mSetupWizardMode;
        
        ///M: add for fix google issue, not pop input method
        if (!mSetupWizardMode) {
            getActivity().setTheme(R.style.Theme_Settings_WifiSettings);           
        }
    }

    @Override
    public View onCreateView(final LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        if (mSetupWizardMode) {
            View view = inflater.inflate(R.layout.setup_preference, container, false);
            mAddApView = view.findViewById(R.id.other_network);
            mAddApView.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (mWifiManager.isWifiEnabled()) {
                        onAddNetworkPressed();
                    }
                }
            });
            final ImageButton b = (ImageButton) view.findViewById(R.id.more);
            if (b != null) {
                b.setOnClickListener(new OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if (mWifiManager.isWifiEnabled()) {
                            PopupMenu pm = new PopupMenu(inflater.getContext(), b);
                            pm.inflate(R.menu.wifi_setup);
                            pm.setOnMenuItemClickListener(new OnMenuItemClickListener() {
                                @Override
                                public boolean onMenuItemClick(MenuItem item) {
                                    if (R.id.wifi_wps == item.getItemId()) {
                                        showDialog(WPS_PBC_DIALOG_ID);
                                        return true;
                                    }
                                    return false;
                                }
                            });
                            pm.show();
                        }
                    }
                });
            }

            Intent intent = getActivity().getIntent();
            if (intent.getBooleanExtra(EXTRA_SHOW_CUSTOM_BUTTON, false)) {
                view.findViewById(R.id.button_bar).setVisibility(View.VISIBLE);
                view.findViewById(R.id.back_button).setVisibility(View.INVISIBLE);
                view.findViewById(R.id.skip_button).setVisibility(View.INVISIBLE);
                view.findViewById(R.id.next_button).setVisibility(View.INVISIBLE);

                Button customButton = (Button) view.findViewById(R.id.custom_button);
                customButton.setVisibility(View.VISIBLE);
                customButton.setOnClickListener(new OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        boolean isConnected = false;
                        Activity activity = getActivity();
                        final ConnectivityManager connectivity = (ConnectivityManager)
                                activity.getSystemService(Context.CONNECTIVITY_SERVICE);
                        if (connectivity != null) {
                            final NetworkInfo info = connectivity.getActiveNetworkInfo();
                            isConnected = (info != null) && info.isConnected();
                        }
                        if (isConnected) {
                            // Warn of possible data charges
                            showDialog(WIFI_SKIPPED_DIALOG_ID);
                        } else {
                            // Warn of lack of updates
                            showDialog(WIFI_AND_MOBILE_SKIPPED_DIALOG_ID);
                        }
                    }
                });
            }

            if (intent.getBooleanExtra(EXTRA_SHOW_WIFI_REQUIRED_INFO, false)) {
                view.findViewById(R.id.wifi_required_info).setVisibility(View.VISIBLE);
            }
            /// M: oobe @{
            mWifiEnabler = OobeUtils.addWifiSwitch(this, view, mWifiEnabler);
            ///@}
            return view;
        } else {
            return super.onCreateView(inflater, container, savedInstanceState);
        }
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);



        /// M: get telephony manager @{
        if (FeatureOption.MTK_EAP_SIM_AKA) {
            mTelephonyEx = ITelephonyEx.Stub.asInterface(ServiceManager.getService(Context.TELEPHONY_SERVICEEX));
            mTelephonyManagerEx = TelephonyManagerEx.getDefault();
        }
        /// @}

        mP2pSupported = getPackageManager().hasSystemFeature(PackageManager.FEATURE_WIFI_DIRECT);
        /// M: WifiManager memory leak @{
        //mWifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);
        mWifiManager = (WifiManager) getActivity().getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        /// @}

        mConnectListener = new WifiManager.ActionListener() {
                                   @Override
                                   public void onSuccess() {
                                   }
                                   @Override
                                   public void onFailure(int reason) {
                                       Activity activity = getActivity();
                                       if (activity != null) {
                                           Toast.makeText(activity,
                                            R.string.wifi_failed_connect_message,
                                            Toast.LENGTH_SHORT).show();
                                   }
                                   }
                               };

        mSaveListener = new WifiManager.ActionListener() {
                                @Override
                                public void onSuccess() {
                                }
                                @Override
                                public void onFailure(int reason) {
                                    Activity activity = getActivity();
                                    if (activity != null) {
                                        Toast.makeText(activity,
                                        R.string.wifi_failed_save_message,
                                        Toast.LENGTH_SHORT).show();
                                }
                                }
                            };

        mForgetListener = new WifiManager.ActionListener() {
                                   @Override
                                   public void onSuccess() {
                                   }
                                   @Override
                                   public void onFailure(int reason) {
                                       Activity activity = getActivity();
                                       if (activity != null) {
                                           Toast.makeText(activity,
                                            R.string.wifi_failed_forget_message,
                                            Toast.LENGTH_SHORT).show();
                                   }
                                   }
                               };

        if (savedInstanceState != null
                && savedInstanceState.containsKey(SAVE_DIALOG_ACCESS_POINT_STATE)) {
            mDlgEdit = savedInstanceState.getBoolean(SAVE_DIALOG_EDIT_MODE);
            mAccessPointSavedState = savedInstanceState.getBundle(SAVE_DIALOG_ACCESS_POINT_STATE);
        }

        final Activity activity = getActivity();
        final Intent intent = activity.getIntent();

        // first if we're supposed to finish once we have a connection
        mAutoFinishOnConnection = intent.getBooleanExtra(EXTRA_AUTO_FINISH_ON_CONNECT, false);

        if (mAutoFinishOnConnection) {
            // Hide the next button
            if (hasNextButton()) {
                getNextButton().setVisibility(View.GONE);
            }

            final ConnectivityManager connectivity = (ConnectivityManager)
                    activity.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (connectivity != null
                    && connectivity.getNetworkInfo(ConnectivityManager.TYPE_WIFI).isConnected()) {
                activity.setResult(Activity.RESULT_OK);
                activity.finish();
                return;
            }
        }

        // if we're supposed to enable/disable the Next button based on our current connection
        // state, start it off in the right state
        mEnableNextOnConnection = intent.getBooleanExtra(EXTRA_ENABLE_NEXT_ON_CONNECT, false);

        if (mEnableNextOnConnection) {
            if (hasNextButton()) {
                final ConnectivityManager connectivity = (ConnectivityManager)
                        activity.getSystemService(Context.CONNECTIVITY_SERVICE);
                if (connectivity != null) {
                    NetworkInfo info = connectivity.getNetworkInfo(
                            ConnectivityManager.TYPE_WIFI);
                    if (info != null) {
                        changeNextButtonState(info.isConnected());
                    }
                }
            }
        }

        /// M: oobe 
        if (mSetupWizardMode && !OobeUtils.isOobeMode(this)) {
            getView().setSystemUiVisibility(
//                    View.STATUS_BAR_DISABLE_BACK |
                    View.STATUS_BAR_DISABLE_HOME |
                    View.STATUS_BAR_DISABLE_RECENT |
                    View.STATUS_BAR_DISABLE_NOTIFICATION_ALERTS |
                    View.STATUS_BAR_DISABLE_CLOCK);
        }

        /// M:  @{
        if (activity.getIntent().getBooleanExtra(WLAN_AP_AND_GPRS, false)) {
            addPreferencesFromResource(R.xml.wifi_access_points_and_gprs);
        } else {
            addPreferencesFromResource(R.xml.wifi_settings);
        }
        
        mExt.addCategories(getPreferenceScreen());
        /// @}

        // On/off switch is hidden for Setup Wizard
        if (!mSetupWizardMode) {
            /// M: for using MTK style Switch , text is I/O , not On/Off {@
            LayoutInflater inflater = (LayoutInflater)
                  activity.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            Switch actionBarSwitch = (Switch)inflater.inflate(com.mediatek.internal.R.layout.imageswitch_layout,null);
            /// @}
            if (activity instanceof PreferenceActivity) {
                PreferenceActivity preferenceActivity = (PreferenceActivity) activity;
                if (preferenceActivity.onIsHidingHeaders() || !preferenceActivity.onIsMultiPane()) {
                    final int padding = activity.getResources().getDimensionPixelSize(
                            R.dimen.action_bar_switch_padding);
                    actionBarSwitch.setPaddingRelative(0, 0, padding, 0);
                    activity.getActionBar().setDisplayOptions(ActionBar.DISPLAY_SHOW_CUSTOM,
                            ActionBar.DISPLAY_SHOW_CUSTOM);
                    activity.getActionBar().setCustomView(actionBarSwitch, new ActionBar.LayoutParams(
                            ActionBar.LayoutParams.WRAP_CONTENT,
                            ActionBar.LayoutParams.WRAP_CONTENT,
                            Gravity.CENTER_VERTICAL | Gravity.END));
                }
            }

            mWifiEnabler = new WifiEnabler(activity, actionBarSwitch);
        }

        mEmptyView = (TextView) getView().findViewById(android.R.id.empty);
        getListView().setEmptyView(mEmptyView);

        if (!mSetupWizardMode) {
            registerForContextMenu(getListView());
        }

        /// M: oobe @{
        if (!OobeUtils.isOobeMode(this)) {
            setHasOptionsMenu(true);
        }
        /// @}

        /// M: register priority observer
        mExt.registerPriorityObserver(getContentResolver());

    }

    @Override
    public void onResume() {
        super.onResume();
        if (mWifiEnabler != null) {
            mWifiEnabler.resume();
        }

        getActivity().registerReceiver(mReceiver, mFilter);
        updateAccessPoints();
    }

    @Override
    public void onPause() {
        super.onPause();
        if (mWifiEnabler != null) {
            mWifiEnabler.pause();
        }
        getActivity().unregisterReceiver(mReceiver);
        mScanner.pause();
    }
    @Override
    public void onDestroy() {
        /// M: unregister priority observer
        mExt.unregisterPriorityObserver(getContentResolver()); 
        super.onDestroy();
    }
    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        // If the user is not allowed to configure wifi, do not show the menu.
        if (isRestrictedAndNotPinProtected()) return;

        final boolean wifiIsEnabled = mWifiManager.isWifiEnabled();
        TypedArray ta = getActivity().getTheme().obtainStyledAttributes(
                new int[] {R.attr.ic_menu_add, R.attr.ic_wps});
        if (mSetupWizardMode) {
            menu.add(Menu.NONE, MENU_ID_WPS_PBC, 0, R.string.wifi_menu_wps_pbc)
                    .setIcon(ta.getDrawable(1))
                    .setEnabled(wifiIsEnabled)
                    .setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
            menu.add(Menu.NONE, MENU_ID_ADD_NETWORK, 0, R.string.wifi_add_network)
                    .setEnabled(wifiIsEnabled)
                    .setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
        } else {
            /// M @{
            /*
            menu.add(Menu.NONE, MENU_ID_WPS_PBC, 0, R.string.wifi_menu_wps_pbc)
                    .setIcon(ta.getDrawable(1))
                    .setEnabled(wifiIsEnabled)
                    .setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
            menu.add(Menu.NONE, MENU_ID_ADD_NETWORK, 0, R.string.wifi_add_network)
                    .setIcon(ta.getDrawable(0))
                    .setEnabled(wifiIsEnabled)
                    .setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
            */
            /// @}
            /// M @{
            MenuItem wpsMenuItem = menu.add(Menu.NONE, MENU_ID_WPS_PBC, 0, R.string.wifi_menu_wps_pbc);
            wpsMenuItem.setIcon(ta.getDrawable(1))
                    .setEnabled(wifiIsEnabled)
                    .setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
            MenuItem addMenuItem = menu.add(Menu.NONE, MENU_ID_ADD_NETWORK, 0, R.string.wifi_add_network);
            addMenuItem.setIcon(ta.getDrawable(0))
                    .setEnabled(wifiIsEnabled)
                    .setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);        
            Drawable wpsIcon = wpsMenuItem.getIcon();
            Drawable addIcon = addMenuItem.getIcon();
            if (wpsIcon != null && addIcon != null && !wifiIsEnabled) {
                wpsIcon.mutate().setColorFilter(Color.GRAY, PorterDuff.Mode.SRC_IN);
                addIcon.mutate().setColorFilter(Color.GRAY, PorterDuff.Mode.SRC_IN);
            }
            /// @} 
            menu.add(Menu.NONE, MENU_ID_SCAN, 0, R.string.wifi_menu_scan)
                    //.setIcon(R.drawable.ic_menu_scan_network)
                    .setEnabled(wifiIsEnabled)
                    .setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
            menu.add(Menu.NONE, MENU_ID_WPS_PIN, 0, R.string.wifi_menu_wps_pin)
                    .setEnabled(wifiIsEnabled)
                    .setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
            if (mP2pSupported) {
                menu.add(Menu.NONE, MENU_ID_P2P, 0, R.string.wifi_menu_p2p)
                        .setEnabled(wifiIsEnabled)
                        .setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
            }
            menu.add(Menu.NONE, MENU_ID_ADVANCED, 0, R.string.wifi_menu_advanced)
                    //.setIcon(android.R.drawable.ic_menu_manage)
                    .setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
        }
        ta.recycle();
        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        // If the dialog is showing, save its state.
        if (mDialog != null && mDialog.isShowing()) {
            outState.putBoolean(SAVE_DIALOG_EDIT_MODE, mDlgEdit);
            if (mDlgAccessPoint != null) {
                mAccessPointSavedState = new Bundle();
                mDlgAccessPoint.saveWifiState(mAccessPointSavedState);
                outState.putBundle(SAVE_DIALOG_ACCESS_POINT_STATE, mAccessPointSavedState);
            }
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // If the user is not allowed to configure wifi, do not handle menu selections.
        if (isRestrictedAndNotPinProtected()) return false;

        switch (item.getItemId()) {
            case MENU_ID_WPS_PBC:
                showDialog(WPS_PBC_DIALOG_ID);
                return true;
            case MENU_ID_P2P:
                if (getActivity() instanceof PreferenceActivity) {
                    ((PreferenceActivity) getActivity()).startPreferencePanel(
                            WifiP2pSettings.class.getCanonicalName(),
                            null,
                            R.string.wifi_p2p_settings_title, null,
                            this, 0);
                } else {
                    startFragment(this, WifiP2pSettings.class.getCanonicalName(), -1, null);
                }
                return true;
            case MENU_ID_WPS_PIN:
                showDialog(WPS_PIN_DIALOG_ID);
                return true;
            case MENU_ID_SCAN:
                if (mWifiManager.isWifiEnabled()) {
                    mScanner.forceScan();
                }
                return true;
            case MENU_ID_ADD_NETWORK:
                if (mWifiManager.isWifiEnabled()) {
                    onAddNetworkPressed();
                }
                return true;
            case MENU_ID_ADVANCED:
                if (getActivity() instanceof PreferenceActivity) {
                    ((PreferenceActivity) getActivity()).startPreferencePanel(
                            AdvancedWifiSettings.class.getCanonicalName(),
                            null,
                            R.string.wifi_advanced_titlebar, null,
                            this, 0);
                } else {
                    startFragment(this, AdvancedWifiSettings.class.getCanonicalName(), -1, null);
                }
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View view, ContextMenuInfo info) {
        if (info instanceof AdapterContextMenuInfo) {
            Preference preference = (Preference) getListView().getItemAtPosition(
                    ((AdapterContextMenuInfo) info).position);

            if (preference instanceof AccessPoint) {
                mSelectedAccessPoint = (AccessPoint) preference;
                menu.setHeaderTitle(mSelectedAccessPoint.ssid);
                if (mSelectedAccessPoint.getLevel() != -1
                        && mSelectedAccessPoint.getState() == null) {
                    menu.add(Menu.NONE, MENU_ID_CONNECT, 0, R.string.wifi_menu_connect);
                }
                /// M: current connected AP, add a disconnect option to it @{
                if (mSelectedAccessPoint.getState() != null && mExt.shouldAddDisconnectMenu()) {
                    menu.add(Menu.NONE, MENU_ID_DISCONNECT, 0, R.string.wifi_menu_disconnect);
                }
                /// @}

                if (mSelectedAccessPoint.networkId != INVALID_NETWORK_ID) {
                    /// M: should add forget menu
                    if (mExt.shouldAddForgetMenu(mSelectedAccessPoint.ssid,mSelectedAccessPoint.security)) {
                        menu.add(Menu.NONE, MENU_ID_FORGET, 0, R.string.wifi_menu_forget);
                    }
                    menu.add(Menu.NONE, MENU_ID_MODIFY, 0, R.string.wifi_menu_modify);
                }
            }
        }
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        if (mSelectedAccessPoint == null) {
            return super.onContextItemSelected(item);
        }
        switch (item.getItemId()) {
            case MENU_ID_CONNECT:
                if (mSelectedAccessPoint.networkId != INVALID_NETWORK_ID) {
                    mWifiManager.connect(mSelectedAccessPoint.networkId,
                            mConnectListener);                        
                    mManuallyConnect = true;
                } else if (mSelectedAccessPoint.security == AccessPoint.SECURITY_NONE) {
                    /** Bypass dialog for unsecured networks */
                    mSelectedAccessPoint.generateOpenNetworkConfig();
                    mWifiManager.connect(mSelectedAccessPoint.getConfig(),
                            mConnectListener);
                    mManuallyConnect = true;
                } else {
                    showDialog(mSelectedAccessPoint, false);
                }
                return true;
            
            case MENU_ID_FORGET:
                mWifiManager.forget(mSelectedAccessPoint.networkId, mForgetListener);
                return true;
            
            case MENU_ID_MODIFY:
                showDialog(mSelectedAccessPoint, true);
                return true;
            
            case MENU_ID_DISCONNECT:
                mExt.disconnect(mSelectedAccessPoint.networkId);
                return true;

        }
        return super.onContextItemSelected(item);
    }

    @Override
    public boolean onPreferenceTreeClick(PreferenceScreen screen, Preference preference) {
        if (preference instanceof AccessPoint) {
            mSelectedAccessPoint = (AccessPoint) preference;
            /** Bypass dialog for unsecured, unsaved networks */
            if (mSelectedAccessPoint.security == AccessPoint.SECURITY_NONE &&
                    mSelectedAccessPoint.networkId == INVALID_NETWORK_ID) {
                /// M: support open ap wps test @{
                if (mSelectedAccessPoint.isOpenApWPSSupported() ||
                          PasspointSettings.shouldUpdate(mLastInfo, mSelectedAccessPoint.bssid, 
                                  mSelectedAccessPoint.mSupportedPasspoint)) {
                    showDialog(mSelectedAccessPoint, false);
                } else {
                /// @}
                    mSelectedAccessPoint.generateOpenNetworkConfig();
                    mWifiManager.connect(mSelectedAccessPoint.getConfig(), mConnectListener);
                    mManuallyConnect = true;
                }
            } else {
                showDialog(mSelectedAccessPoint, false);
            }
        } else {
            return super.onPreferenceTreeClick(screen, preference);
        }
        return true;
    }

    private void showDialog(AccessPoint accessPoint, boolean edit) {
        if (mDialog != null) {
            removeDialog(WIFI_DIALOG_ID);
            mDialog = null;
            mAccessPointSavedState = null;
        }

        // Save the access point and edit mode
        mDlgAccessPoint = accessPoint;
        mDlgEdit = edit;

        showDialog(WIFI_DIALOG_ID);
    }

    @Override
    public Dialog onCreateDialog(int dialogId) {
        switch (dialogId) {
            case WIFI_DIALOG_ID:
                AccessPoint ap = mDlgAccessPoint; // For manual launch
                if (ap == null) { // For re-launch from saved state
                    if (mAccessPointSavedState != null) {
                        ap = new AccessPoint(getActivity(), mAccessPointSavedState);
                        // For repeated orientation changes
                        mDlgAccessPoint = ap;
                        // Reset the saved access point data
                        mAccessPointSavedState = null;
                    }
                }
                // If it's still null, fine, it's for Add Network
                mSelectedAccessPoint = ap;

                /// M: record priority of selected ap  @{
                if (mSelectedAccessPoint != null && mSelectedAccessPoint.getConfig() != null) {
                    //store the former priority value before user modification
                    mExt.recordPriority(mSelectedAccessPoint.getConfig().priority);
                } else {
                    //the last added AP will have highest priority, mean all other AP's priority will be adjusted,
                    //the same as adjust this new added one's priority from lowest to highest
                    mExt.recordPriority(-1);
                }
                /// @}

                /// M: add telephony manager params
                mDialog = new WifiDialog(getActivity(), this, ap, mDlgEdit);
                return mDialog;
            case WPS_PBC_DIALOG_ID:
                return new WpsDialog(getActivity(), WpsInfo.PBC);
            case WPS_PIN_DIALOG_ID:
                return new WpsDialog(getActivity(), WpsInfo.DISPLAY);
            case WIFI_SKIPPED_DIALOG_ID:
                return new AlertDialog.Builder(getActivity())
                            .setMessage(R.string.wifi_skipped_message)
                            .setCancelable(false)
                            .setNegativeButton(R.string.wifi_skip_anyway,
                                    new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int id) {
                                    getActivity().setResult(RESULT_SKIP);
                                    getActivity().finish();
                                }
                            })
                            .setPositiveButton(R.string.wifi_dont_skip,
                                    new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int id) {
                                }
                            })
                            .create();
            case WIFI_AND_MOBILE_SKIPPED_DIALOG_ID:
                return new AlertDialog.Builder(getActivity())
                            .setMessage(R.string.wifi_and_mobile_skipped_message)
                            .setCancelable(false)
                            .setNegativeButton(R.string.wifi_skip_anyway,
                                    new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int id) {
                                    getActivity().setResult(RESULT_SKIP);
                                    getActivity().finish();
                                }
                            })
                            .setPositiveButton(R.string.wifi_dont_skip,
                                    new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int id) {
                                }
                            })
                            .create();

        }
        return super.onCreateDialog(dialogId);
    }

    /**
     * Shows the latest access points available with supplimental information like
     * the strength of network and the security for it.
     */
    private void updateAccessPoints() {
        // Safeguard from some delayed event handling
        if (getActivity() == null) return;

        if (isRestrictedAndNotPinProtected()) {
            addMessagePreference(R.string.wifi_empty_list_user_restricted);
            return;
        }
        final int wifiState = mWifiManager.getWifiState();

        switch (wifiState) {
            case WifiManager.WIFI_STATE_ENABLED:
                // AccessPoints are automatically sorted with TreeSet.
                final Collection<AccessPoint> accessPoints = constructAccessPoints();

                if (accessPoints.size() == 0) {
                    addMessagePreference(R.string.wifi_empty_list_wifi_on);
                }
                /// M: add ap to screen @{
                    for (AccessPoint accessPoint : accessPoints) {
                    mExt.addPreference(getPreferenceScreen(), accessPoint, mExt.COMMON_AP, 
                            accessPoint.ssid, accessPoint.security);
                }
                /// @}
                /// M: print performance log @{
                if (mScanResultsAvailable) {
                    long endTime = System.currentTimeMillis();
                    Xlog.i(TAG, "[Performance test][Settings][wifi] wifi search end [" + endTime + "]");
                    mScanResultsAvailable = false;                    
                }
                /// @}
                break;

            case WifiManager.WIFI_STATE_ENABLING:
                /// M: empty ap list // oobe
                mExt.emptyScreen(getPreferenceScreen());
                break;

            case WifiManager.WIFI_STATE_DISABLING:
                addMessagePreference(R.string.wifi_stopping);
                break;

            case WifiManager.WIFI_STATE_DISABLED:
                setOffMessage();
                break;
        }
    }

    private void setOffMessage() {
        if (mEmptyView != null) {
            mEmptyView.setText(R.string.wifi_empty_list_wifi_off);
            if (Settings.Global.getInt(getActivity().getContentResolver(),
                    Settings.Global.WIFI_SCAN_ALWAYS_AVAILABLE, 0) == 1) {
                mEmptyView.append("\n\n");
                int resId;
                if (Settings.Secure.isLocationProviderEnabled(getActivity().getContentResolver(),
                        LocationManager.NETWORK_PROVIDER)) {
                    resId = R.string.wifi_scan_notify_text_location_on;
                } else {
                    resId = R.string.wifi_scan_notify_text_location_off;
                }
                CharSequence charSeq = getText(resId);
                mEmptyView.append(charSeq);
            }
        }
        mExt.emptyScreen(getPreferenceScreen());
    }

    private void addMessagePreference(int messageId) {
        if (mEmptyView != null) mEmptyView.setText(messageId);
        /// M: empty ap list
        mExt.emptyScreen(getPreferenceScreen());
    }

    /** Returns sorted list of access points */
    private List<AccessPoint> constructAccessPoints() {
        ArrayList<AccessPoint> accessPoints = new ArrayList<AccessPoint>();
        /** Lookup table to more quickly update AccessPoints by only considering objects with the
         * correct SSID.  Maps SSID -> List of AccessPoints with the given SSID.  */
        Multimap<String, AccessPoint> apMap = new Multimap<String, AccessPoint>();

        /// M: empty ap list
        mExt.emptyCategory(getPreferenceScreen());

        final List<WifiConfiguration> configs = mWifiManager.getConfiguredNetworks();
        if (configs != null) {
            for (WifiConfiguration config : configs) {
                /// M: Add for EAP-SIM begin @{
                if (hasChangedSimCard(config)) {
                                continue;
                            }
                /// @}
                /// M: get last priority
                mExt.setLastPriority(config.priority);

                AccessPoint accessPoint = new AccessPoint(getActivity(), config);
                accessPoint.update(mLastInfo, mLastState);
                accessPoints.add(accessPoint);
                apMap.put(accessPoint.ssid, accessPoint);

                /// M: add ap to category @{
                mExt.addPreference(null, accessPoint, mExt.CONFIGED_AP, accessPoint.ssid, accessPoint.security);
                /// @}
            }
        }

        final List<ScanResult> results = mWifiManager.getScanResults();
        if (results != null) {
            for (ScanResult result : results) {
                // Ignore hidden and ad-hoc networks.
                if (result.SSID == null || result.SSID.length() == 0 ||
                        result.capabilities.contains("[IBSS]")) {
                    continue;
                }

                boolean found = false;
                for (AccessPoint accessPoint : apMap.getAll(result.SSID)) {
                    if (accessPoint.update(result))
                        found = true;
                }
                if (!found) {
                    AccessPoint accessPoint = new AccessPoint(getActivity(), result);
                    /// M: add Passpoint, update at this point, because atuo connected passpoint not profile@{
                    if (PasspointSettings.shouldUpdate(mLastInfo, accessPoint.bssid, accessPoint.mSupportedPasspoint)) {
                        accessPoint.update(mLastInfo, mLastState);
                    }
                    /// @}
                    accessPoints.add(accessPoint);
                    apMap.put(accessPoint.ssid, accessPoint);

                    /// M: add ap to category @{
                    mExt.addPreference(null, accessPoint, mExt.NEW_AP, accessPoint.ssid, accessPoint.security);
                    /// @}
                }
            }
        }

        /// M: refresh category
        mExt.refreshCategory(getPreferenceScreen());

        // Pre-sort accessPoints to speed preference insertion
        ArrayList<AccessPoint> origAccessPoints = new ArrayList<AccessPoint>(accessPoints.size());
        origAccessPoints.addAll(accessPoints);
        try {
            Collections.sort(accessPoints);
        } catch (ClassCastException e) {
            Xlog.d(TAG,"collection.sort exception;origAccessPoints=" + origAccessPoints);
            return origAccessPoints;
        } catch (UnsupportedOperationException e) {
            Xlog.d(TAG,"collection.sort exception;origAccessPoints=" + origAccessPoints);
            return origAccessPoints;          
        }
        return accessPoints;
    }

    /** A restricted multimap for use in constructAccessPoints */
    private class Multimap<K,V> {
        private final HashMap<K,List<V>> store = new HashMap<K,List<V>>();
        /** retrieve a non-null list of values with key K */
        List<V> getAll(K key) {
            List<V> values = store.get(key);
            return values != null ? values : Collections.<V>emptyList();
        }

        void put(K key, V val) {
            List<V> curVals = store.get(key);
            if (curVals == null) {
                curVals = new ArrayList<V>(3);
                store.put(key, curVals);
            }
            curVals.add(val);
        }
    }

    private void handleEvent(Context context, Intent intent) {
        String action = intent.getAction();
        Xlog.i(TAG, "handleEvent(), action = " + action);
        if (WifiManager.WIFI_STATE_CHANGED_ACTION.equals(action)) {
            updateWifiState(intent.getIntExtra(WifiManager.EXTRA_WIFI_STATE,
                    WifiManager.WIFI_STATE_UNKNOWN));
        } else if (WifiManager.SCAN_RESULTS_AVAILABLE_ACTION.equals(action) ||
                WifiManager.CONFIGURED_NETWORKS_CHANGED_ACTION.equals(action) ||
                WifiManager.LINK_CONFIGURATION_CHANGED_ACTION.equals(action)) {
                /// print performance log @{
                if (WifiManager.SCAN_RESULTS_AVAILABLE_ACTION.equals(action)) {
                    mScanResultsAvailable = true;              
                }
                ///@}
                updateAccessPoints();
        } else if (WifiManager.SUPPLICANT_STATE_CHANGED_ACTION.equals(action)) {
            //Ignore supplicant state changes when network is connected
            //TODO: we should deprecate SUPPLICANT_STATE_CHANGED_ACTION and
            //introduce a broadcast that combines the supplicant and network
            //network state change events so the apps dont have to worry about
            //ignoring supplicant state change when network is connected
            //to get more fine grained information.
            SupplicantState state = (SupplicantState) intent.getParcelableExtra(
                    WifiManager.EXTRA_NEW_STATE);
            if (!mConnected.get() && SupplicantState.isHandshakeState(state)) {
                updateConnectionState(WifiInfo.getDetailedStateOf(state));
             } else {
                 // During a connect, we may have the supplicant
                 // state change affect the detailed network state.
                 // Make sure a lost connection is updated as well.
                 updateConnectionState(null);
             }
        } else if (WifiManager.NETWORK_STATE_CHANGED_ACTION.equals(action)) {
            NetworkInfo info = (NetworkInfo) intent.getParcelableExtra(
                    WifiManager.EXTRA_NETWORK_INFO);
            mConnected.set(info.isConnected());
            changeNextButtonState(info.isConnected());
            updateAccessPoints();
            updateConnectionState(info.getDetailedState());
            if (mAutoFinishOnConnection && info.isConnected()) {
                Activity activity = getActivity();
                if (activity != null) {
                    activity.setResult(Activity.RESULT_OK);
                    activity.finish();
                }
                return;
            }
        } else if (WifiManager.RSSI_CHANGED_ACTION.equals(action)) {
            updateConnectionState(null);
        } else if (WifiManager.NO_CERTIFICATION_ACTION.equals(action)) { 
            /// M: show error message @{
            String apSSID = "";
            if (mSelectedAccessPoint != null) {
                apSSID = "["+mSelectedAccessPoint.ssid + "] ";
            }
            Xlog.i(TAG, "Receive  no certification broadcast for AP " + apSSID);
            String message = getResources().getString(R.string.wifi_no_cert_for_wapi) + apSSID;
            Toast.makeText(getActivity(), message, Toast.LENGTH_LONG).show();
            /// @}
        }
    }

    private void updateConnectionState(DetailedState state) {
        /* sticky broadcasts can call this when wifi is disabled */
        if (!mWifiManager.isWifiEnabled()) {
            mScanner.pause();
            return;
        }
        /// M: print logs for auto test @{
        if (state == DetailedState.CONNECTED) {
        } else if (state == DetailedState.DISCONNECTED) {
            mManuallyConnect = false;
        }
        /// @}

        if (state == DetailedState.OBTAINING_IPADDR) {
            mScanner.pause();
        } else {
            mScanner.resume();
        }

        mLastInfo = mWifiManager.getConnectionInfo();
        if (state != null) {
            mLastState = state;
        }

        
        /// M: updateAP @{
        List<PreferenceGroup> preferenceCategoryList = mExt.getPreferenceCategory(getPreferenceScreen());
        updateAP(preferenceCategoryList);
            /// @}
        
        /// M: update priority @{
        if (state == DetailedState.CONNECTED) {
            if (mManuallyConnect && mLastInfo != null) {
            mExt.updatePriorityAfterConnect(mLastInfo.getNetworkId());
        }
            mManuallyConnect = false;
        }
        /// @}
    }

    private void updateWifiState(int state) {
        Activity activity = getActivity();
        if (activity != null) {
            activity.invalidateOptionsMenu();
        }

        switch (state) {
            case WifiManager.WIFI_STATE_ENABLED:
                mScanner.resume();
                /// M: update priority
                // if wifi has connectd, not need to updatePriority
                WifiInfo mCurrentConnecdInfo = mWifiManager.getConnectionInfo();
                if (mCurrentConnecdInfo != null && mCurrentConnecdInfo.getSSID() != null
                        && mCurrentConnecdInfo.getSSID().length() > 0) {
                    Xlog.d(TAG, "mCurrentConnectedInfo.getSSID() =  " + mCurrentConnecdInfo.getSSID());
                } else {
                    mExt.updatePriority();                
                }
                ///M: oobe
                OobeUtils.setEnabledStateOnViews(this, mAddApView, true);
                return; // not break, to avoid the call to pause() below

            case WifiManager.WIFI_STATE_ENABLING:
                addMessagePreference(R.string.wifi_starting);
                ///M: oobe
                OobeUtils.setEnabledStateOnViews(this, mAddApView, false);
                break;
            case WifiManager.WIFI_STATE_DISABLING:
                ///M: oobe
                OobeUtils.setEnabledStateOnViews(this, mAddApView, false);
                break;
            case WifiManager.WIFI_STATE_DISABLED:
                setOffMessage();
                ///M: oobe
                OobeUtils.setEnabledStateOnViews(this, mAddApView, false);
                break;
        }

        mLastInfo = null;
        mLastState = null;
        mScanner.pause();
    }

    private class Scanner extends Handler {
        private int mRetry = 0;

        void resume() {
            if (!hasMessages(0)) {
                sendEmptyMessage(0);
            }
        }

        void forceScan() {
            removeMessages(0);
            sendEmptyMessage(0);
        }

        void pause() {
            mRetry = 0;
            removeMessages(0);
        }

        @Override
        public void handleMessage(Message message) {
            if (mWifiManager.startScan()) {
                mRetry = 0;
            } else if (++mRetry >= 3) {
                mRetry = 0;
                Activity activity = getActivity();
                if (activity != null) {
                    Toast.makeText(activity, R.string.wifi_fail_to_scan,
                        Toast.LENGTH_LONG).show();
                }
                return;
            }
            sendEmptyMessageDelayed(0, WIFI_RESCAN_INTERVAL_MS);
        }
    }

    /**
     * Renames/replaces "Next" button when appropriate. "Next" button usually exists in
     * Wifi setup screens, not in usual wifi settings screen.
     *
     * @param connected true when the device is connected to a wifi network.
     */
    private void changeNextButtonState(boolean connected) {
        if (mEnableNextOnConnection && hasNextButton()) {
            getNextButton().setEnabled(connected);
        }
    }

    @Override
    public void onClick(DialogInterface dialogInterface, int button) {
        if (button == WifiDialog.BUTTON_FORGET && mSelectedAccessPoint != null) {
            forget();
        } else if (button == WifiDialog.BUTTON_SUBMIT) {
            if (mDialog != null) {
                submit(mDialog.getController());
            }
        }
    }

    /* package */ void submit(WifiConfigController configController) {

        final WifiConfiguration config = configController.getConfig();
        Xlog.d(TAG, "submit, config = " + config);

        /// M: add for EAP_SIM/AKA start,remind user when he use eap-sim/aka in a wrong way @{
        try {
            if (config != null && FeatureOption.MTK_EAP_SIM_AKA && config.imsi != null) {
                if (config.toString().contains("eap SIM") || config.toString().contains("eap AKA")) {
                    // cannot use eap-sim/aka under airplane mode
                    if (Settings.System.getInt(this.getContentResolver(),
                            Settings.System.AIRPLANE_MODE_ON, 0) == 1) {
                        Toast.makeText(getActivity(), R.string.eap_sim_aka_airplanemode, Toast.LENGTH_LONG).show();
                        return;
                    }
                    // cannot use eap-sim/aka without a sim/usimcard
                    if (config.imsi.equals("\"error\"")) {
                        Toast.makeText(getActivity(), R.string.eap_sim_aka_no_sim_error, Toast.LENGTH_LONG).show();
                        return;
                    }
                    // cannot use eap-sim/aka if user doesn't select a sim slot
                    if ((FeatureOption.MTK_GEMINI_SUPPORT) && (config.imsi.equals("\"none\""))) {
                        Toast.makeText(getActivity(), R.string.eap_sim_aka_no_sim_slot_selected,Toast.LENGTH_LONG).show();
                        return;
                    }
                }

            }
        } catch (Exception e) {
            Xlog.d(TAG,"submit exception() " + e.toString());
        }
        /// @}

        if (config == null) {

            /// M: add for EAP_SIM/AKA start, cannot use eap-sim/aka under airplane mode @{
            if (FeatureOption.MTK_EAP_SIM_AKA) {
                Xlog.d(TAG,"mSelectedAccessPoint " + mSelectedAccessPoint);
                List<WifiConfiguration> configs = mWifiManager.getConfiguredNetworks();
                if (configs != null) {
                    for (WifiConfiguration mConfig : configs) {
                        Xlog.d(TAG,"onClick() >>if ((mConfig.SSID).equals(mSelectedAccessPoint.ssid)) {");
                        Xlog.d(TAG,"onClick()" + mConfig.SSID);
                        Xlog.d(TAG,"onClick() " + mSelectedAccessPoint.ssid);
                        if (mConfig != null && mConfig.SSID != null && 
                            (mConfig.SSID).equals(WifiDialog.addQuote(mSelectedAccessPoint.ssid)) &&
                            (Settings.System.getInt(this.getContentResolver(),Settings.System.AIRPLANE_MODE_ON, 0) == 1) &&
                            (mConfig.toString().contains("eap SIM") || mConfig.toString().contains("eap AKA"))) {
                            Xlog.d(TAG, "remind user: cannot user eap-sim/aka under airplane mode");
                            Toast.makeText(getActivity(), R.string.eap_sim_aka_airplanemode, Toast.LENGTH_LONG).show();
                            return;
                        }
                    }
                }
            }
            /// @}

            if (mSelectedAccessPoint != null
                    && mSelectedAccessPoint.networkId != INVALID_NETWORK_ID) {
                /// M: whether mSelectedAccessPoint is null @{
                DetailedState state = mSelectedAccessPoint.getState();
                if (state == null) {
                /// @}
                    mWifiManager.connect(mSelectedAccessPoint.networkId,
                            mConnectListener);
                    mManuallyConnect = true;
                /// M: mSelectedAccessPoint is not null, disconnect it @{
                } else {
                   mExt.disconnect(mSelectedAccessPoint.networkId);
                }
                /// @}
            }
        } else if (config.networkId != INVALID_NETWORK_ID) {
            if (mSelectedAccessPoint != null) {
                /// M: save priority
                mExt.setNewPriority(config);
                mWifiManager.save(config, mSaveListener);
            }
        } else {
            /// M: update priority
            mExt.updatePriorityAfterSubmit(config);

            if (configController.isEdit()) {
                mWifiManager.save(config, mSaveListener);
            } else {
                mWifiManager.connect(config, mConnectListener);
                mManuallyConnect = true;
            }
        }
        /// M: set last connected config
        mExt.setLastConnectedConfig(config);

        if (mWifiManager.isWifiEnabled()) {
            mScanner.resume();
        }
        updateAccessPoints();
    }

    /* package */ void forget() {
        if (mSelectedAccessPoint.networkId == INVALID_NETWORK_ID) {
            // Should not happen, but a monkey seems to triger it
            Log.e(TAG, "Failed to forget invalid network " + mSelectedAccessPoint.getConfig());
            return;
        }

        mWifiManager.forget(mSelectedAccessPoint.networkId, mForgetListener);

        if (mWifiManager.isWifiEnabled()) {
            mScanner.resume();
        }
        updateAccessPoints();

        // We need to rename/replace "Next" button in wifi setup context.
        changeNextButtonState(false);

        /// M: since we lost a configured AP, left ones priority need to be refreshed
        mExt.updatePriority();
    }

    /**
     * Refreshes acccess points and ask Wifi module to scan networks again.
     */
    /* package */ void refreshAccessPoints() {
        if (mWifiManager.isWifiEnabled()) {
            mScanner.resume();
        }
        /// M: empty ap list
        mExt.emptyCategory(getPreferenceScreen());
    }

    /**
     * Called when "add network" button is pressed.
     */
    /* package */ void onAddNetworkPressed() {
        // No exact access point is selected.
        mSelectedAccessPoint = null;
        showDialog(null, true);
    }

    /**
     * Called when "add network" button in wifi gprs selected is pressed.
     */
    public void addNetworkForSelector() {
        if (mExt.isCatogoryExist()) {
            mSelectedAccessPoint = null;
            showDialog(null, true);
        }
    }

    /* package */ int getAccessPointsCount() {
        final boolean wifiIsEnabled = mWifiManager.isWifiEnabled();
        if (wifiIsEnabled) {
            /// M: return ap count
            return mExt.getAccessPointsCount(getPreferenceScreen());
        } else {
            return 0;
        }
    }

    /**
     * Requests wifi module to pause wifi scan. May be ignored when the module is disabled.
     */
    /* package */ void pauseWifiScan() {
        if (mWifiManager.isWifiEnabled()) {
            mScanner.pause();
        }
    }

    /**
     * Requests wifi module to resume wifi scan. May be ignored when the module is disabled.
     */
    /* package */ void resumeWifiScan() {
        if (mWifiManager.isWifiEnabled()) {
            mScanner.resume();
        }
    }

    /**
     * Used as the outer frame of all setup wizard pages that need to adjust their margins based
     * on the total size of the available display. (e.g. side margins set to 10% of total width.)
     */
    public static class ProportionalOuterFrame extends RelativeLayout {
        public ProportionalOuterFrame(Context context) {
            super(context);
        }
        public ProportionalOuterFrame(Context context, AttributeSet attrs) {
            super(context, attrs);
        }
        public ProportionalOuterFrame(Context context, AttributeSet attrs, int defStyle) {
            super(context, attrs, defStyle);
        }

        /**
         * Set our margins and title area height proportionally to the available display size
         */
        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int parentWidth = MeasureSpec.getSize(widthMeasureSpec);
            int parentHeight = MeasureSpec.getSize(heightMeasureSpec);
            final Resources resources = getContext().getResources();
            float titleHeight = resources.getFraction(R.dimen.setup_title_height, 1, 1);
            float sideMargin = resources.getFraction(R.dimen.setup_border_width, 1, 1);
            int bottom = resources.getDimensionPixelSize(R.dimen.setup_margin_bottom);
            setPaddingRelative(
                    (int) (parentWidth * sideMargin),
                    0,
                    (int) (parentWidth * sideMargin),
                    bottom);
            View title = findViewById(R.id.title_area);
            if (title != null) {
                title.setMinimumHeight((int) (parentHeight * titleHeight));
            }
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        }
    }

    /**
     * M: update ap information
     * @param screen
     * @return
     */
    public void updateAP(List<PreferenceGroup> list) {
        if (list == null) {
            return;
        }
        for(PreferenceGroup screen : list) {
        for (int i = screen.getPreferenceCount() - 1; i >= 0; --i) {
            // Maybe there's a WifiConfigPreference
            Preference preference = screen.getPreference(i);
            if (preference instanceof AccessPoint) {
                final AccessPoint accessPoint = (AccessPoint) preference;
                accessPoint.update(mLastInfo, mLastState);
            }
        }
    }

    }
    /**
     * M: handle configuration change event
     * @param configuration
     * @return
     */
    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (mDialog != null) {
            mDialog.closeSpinnerDialog();
        }
    }
    
    
    /**
     * M: Add for EAP-SIM
     * @param config The current AP's configuration
     * @return
     */
    private boolean hasChangedSimCard(WifiConfiguration config) {
        boolean result = false;
        if (FeatureOption.MTK_EAP_SIM_AKA && config.imsi != null && !config.imsi.equals("\"none\"")) {
            Xlog.d(TAG,"config = " + config.toString());
            int slot = 0;
            //Add for gemini+
            String[] simslots = config.simSlot.split("\"");
            if (simslots.length > 1) {
                slot = Integer.parseInt(simslots[1]);
            }
            //in simulator mode, skip
            if ((config.imsi).equals("\"1232010000000000@wlan.mnc001.mcc232.3gppnetwork.org\"")
                    || (config.imsi).equals("\"0232010000000000@wlan.mnc001.mcc232.3gppnetwork.org\"")) {
                Xlog.d(TAG,"in simulator mode, skip");
            } else {
                String imsiStr = null;
                try {
                    if (config.toString().contains("eap SIM")) {
                        imsiStr = WifiDialog.makeNAI(mTelephonyManagerEx.getSimOperator(slot), 
                                mTelephonyEx.getSubscriberId(slot), "SIM");
                    } else if (config.toString().contains("eap AKA")) {
                        imsiStr = WifiDialog.makeNAI(mTelephonyManagerEx.getSimOperator(slot),
                                mTelephonyEx.getSubscriberId(slot), "AKA");
                    }
                    Xlog.d(TAG,"mTelephonyEx.getSubscriberId() " + mTelephonyEx.getSubscriberId(slot));
                } catch (RemoteException ex) {
                    Xlog.d(TAG, "RemoteException when get subscriber id");
                    return true;
                }
                Xlog.d(TAG,"makeNAI() = " + imsiStr);
                
                if ((config.imsi).equals(imsiStr)) {
                    Xlog.d(TAG,"user doesn't change or remove sim card");
                } else {
                    if(!mExt.isTustAP(AccessPoint.removeDoubleQuotes(config.SSID), AccessPoint.getSecurity(config))){
                        Xlog.d(TAG,"user change or remove sim card");
                        boolean s = mWifiManager.removeNetwork(config.networkId);
                        Xlog.d(TAG,"removeNetwork: " + s);
                        s = mWifiManager.saveConfiguration();
                        Xlog.d(TAG,"saveNetworks(): " + s);
                        result = true;
                    }
                }
            }
          }
        return result;
   }
    
}
