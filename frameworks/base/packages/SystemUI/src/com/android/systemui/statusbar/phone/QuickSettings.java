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

package com.android.systemui.statusbar.phone;

import android.animation.ValueAnimator;
import android.app.ActivityManagerNative;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.PendingIntent;
import android.app.admin.DevicePolicyManager;
import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.UserInfo;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.hardware.display.DisplayManager;
import android.media.MediaRouter;
import android.net.wifi.WifiManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.BatteryManager;
import android.os.Handler;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.os.UserManager;
import android.provider.AlarmClock;
import android.provider.ContactsContract;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.Profile;
import android.provider.Settings;
import android.provider.Telephony;
import android.security.KeyChain;
import android.util.Log;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnLongClickListener;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.WindowManagerGlobal;
import android.widget.ImageView;
import android.widget.TextView;

import com.android.internal.app.MediaRouteDialogPresenter;
import android.net.ConnectivityManager;

import com.android.internal.telephony.ITelephony;
import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.TelephonyIntents;
import com.android.internal.telephony.TelephonyProperties;
import com.android.internal.view.RotationPolicy;
import com.android.systemui.R;
import com.android.systemui.statusbar.phone.QuickSettingsModel.ActivityState;
import com.android.systemui.statusbar.phone.QuickSettingsModel.BluetoothState;
import com.android.systemui.statusbar.phone.QuickSettingsModel.RSSIState;
import com.android.systemui.statusbar.phone.QuickSettingsModel.State;
import com.android.systemui.statusbar.phone.QuickSettingsModel.UserState;
import com.android.systemui.statusbar.phone.QuickSettingsModel.WifiState;
import com.android.systemui.statusbar.policy.BatteryController;
import com.android.systemui.statusbar.policy.BluetoothController;
import com.android.systemui.statusbar.policy.LocationController;
import com.android.systemui.statusbar.policy.NetworkController;
import com.android.systemui.statusbar.policy.RotationLockController;

import com.mediatek.common.featureoption.FeatureOption;
import com.mediatek.systemui.statusbar.toolbar.QuickSettingsConnectionModel;
import com.mediatek.systemui.statusbar.util.LaptopBatteryView;
import com.mediatek.systemui.statusbar.util.SIMHelper;
import com.mediatek.telephony.SimInfoManager;
import com.mediatek.telephony.TelephonyManagerEx;
import com.mediatek.xlog.Xlog;

import com.mediatek.systemui.ext.PluginFactory;
import com.mediatek.systemui.ext.QuickSettingsTileViewId;
import com.mediatek.systemui.ext.IQuickSettingsPlugin;

import java.util.ArrayList;

/**
 *
 */
class QuickSettings {
    //static final boolean DEBUG_GONE_TILES = false;
    private static final String TAG = "QuickSettings";
    public static final boolean SHOW_IME_TILE = false;

    /// M: [ALPS00563615] Use our long-press behavior for quick setting enhancement.
    public static final boolean LONG_PRESS_TOGGLES = false; 

    private Context mContext;
    private PanelBar mBar;
    private QuickSettingsModel mModel;
    private ViewGroup mContainerView;

    private DevicePolicyManager mDevicePolicyManager;
    private PhoneStatusBar mStatusBarService;
    private BluetoothState mBluetoothState;
    private BluetoothAdapter mBluetoothAdapter;
    private WifiManager mWifiManager;

    private BluetoothController mBluetoothController;
    private RotationLockController mRotationLockController;
    private LocationController mLocationController;

    private AsyncTask<Void, Void, Pair<String, Drawable>> mUserInfoTask;
    private AsyncTask<Void, Void, Pair<Boolean, Boolean>> mQueryCertTask;

    boolean mTilesSetUp = false;
    boolean mUseDefaultAvatar = false;

    private Handler mHandler;

    // The set of QuickSettingsTiles that have dynamic spans (and need to be updated on
    // configuration change)
    private final ArrayList<QuickSettingsTileView> mDynamicSpannedTiles =
            new ArrayList<QuickSettingsTileView>();

    public QuickSettings(Context context, QuickSettingsContainerView container) {
        mDevicePolicyManager
            = (DevicePolicyManager) context.getSystemService(Context.DEVICE_POLICY_SERVICE);
        mContext = context;
        mContainerView = container;
        mModel = new QuickSettingsModel(context);
        mBluetoothState = new QuickSettingsModel.BluetoothState();
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        mWifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
        /// M: [SystemUI] Add quick settings function(airplane, data conn, bluetooth, timeout, gps, etc.) @{.
        mQuickSettingsConnectionModel = new QuickSettingsConnectionModel(context);
        /// M: [SystemUI] Add quick settings function(airplane, data conn, bluetooth, timeout, gps, etc.) @{.

        mHandler = new Handler();

        IntentFilter filter = new IntentFilter();
        filter.addAction(DisplayManager.ACTION_WIFI_DISPLAY_STATUS_CHANGED);
        /// M: We control BT state in QuickSettingsConnectionModel.
        ///filter.addAction(BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED);
        ///filter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        filter.addAction(Intent.ACTION_USER_SWITCHED);
        filter.addAction(Intent.ACTION_CONFIGURATION_CHANGED);
        filter.addAction(KeyChain.ACTION_STORAGE_CHANGED);
        mContext.registerReceiver(mReceiver, filter);

        IntentFilter profileFilter = new IntentFilter();
        profileFilter.addAction(ContactsContract.Intents.ACTION_PROFILE_CHANGED);
        profileFilter.addAction(Intent.ACTION_USER_INFO_CHANGED);
        mContext.registerReceiverAsUser(mProfileReceiver, UserHandle.ALL, profileFilter,
                null, null);
    }

    void setBar(PanelBar bar) {
        mBar = bar;
    }

    public void setService(PhoneStatusBar phoneStatusBar) {
        mStatusBarService = phoneStatusBar;
        /// M: [SystemUI] Add quick settings function(airplane, data conn, bluetooth, timeout, gps, etc.) .
        mQuickSettingsConnectionModel.setStatusBarService(phoneStatusBar);
    }

    public PhoneStatusBar getService() {
        return mStatusBarService;
    }

    public void setImeWindowStatus(boolean visible) {
        mModel.onImeWindowStatusChanged(visible);
    }

    void setup(NetworkController networkController, BluetoothController bluetoothController,
            BatteryController batteryController, LocationController locationController,
            RotationLockController rotationLockController) {
        mBluetoothController = bluetoothController;
        mRotationLockController = rotationLockController;
        mLocationController = locationController;

        setupQuickSettings();
        updateResources();
        applyLocationEnabledStatus();

        /// M: We control BT state in QuickSettingsConnectionModel.
        ///networkController.addNetworkSignalChangedCallback(mModel);
        ///bluetoothController.addStateChangedCallback(mModel);
        batteryController.addStateChangedCallback(mModel);
        /// M: Support Laptop Battery on QS.
        batteryController.addLaptopStateChangedCallback(mModel);
        locationController.addSettingsChangedCallback(mModel);
        rotationLockController.addRotationLockControllerCallback(mModel);
    }

    private void queryForSslCaCerts() {
        mQueryCertTask = new AsyncTask<Void, Void, Pair<Boolean, Boolean>>() {
            @Override
            protected Pair<Boolean, Boolean> doInBackground(Void... params) {
                boolean hasCert = DevicePolicyManager.hasAnyCaCertsInstalled();
                boolean isManaged = mDevicePolicyManager.getDeviceOwner() != null;

                return Pair.create(hasCert, isManaged);
            }
            @Override
            protected void onPostExecute(Pair<Boolean, Boolean> result) {
                super.onPostExecute(result);
                boolean hasCert = result.first;
                boolean isManaged = result.second;
                mModel.setSslCaCertWarningTileInfo(hasCert, isManaged);
            }
        };
        mQueryCertTask.execute();
    }

    private void queryForUserInformation() {
        Context currentUserContext = null;
        UserInfo userInfo = null;
        try {
            userInfo = ActivityManagerNative.getDefault().getCurrentUser();
            currentUserContext = mContext.createPackageContextAsUser("android", 0,
                    new UserHandle(userInfo.id));
        } catch (NameNotFoundException e) {
            Log.e(TAG, "Couldn't create user context", e);
            throw new RuntimeException(e);
        } catch (RemoteException e) {
            Log.e(TAG, "Couldn't get user info", e);
        }
        final int userId = userInfo.id;
        final String userName = userInfo.name;

        final Context context = currentUserContext;
        mUserInfoTask = new AsyncTask<Void, Void, Pair<String, Drawable>>() {
            @Override
            protected Pair<String, Drawable> doInBackground(Void... params) {
                final UserManager um = UserManager.get(mContext);

                // Fall back to the UserManager nickname if we can't read the name from the local
                // profile below.
                String name = userName;
                Drawable avatar = null;
                Bitmap rawAvatar = um.getUserIcon(userId);
                if (rawAvatar != null) {
                    avatar = new BitmapDrawable(mContext.getResources(), rawAvatar);
                } else {
                    avatar = mContext.getResources().getDrawable(R.drawable.ic_qs_default_user);
                    mUseDefaultAvatar = true;
                }

                // If it's a single-user device, get the profile name, since the nickname is not
                // usually valid
                if (um.getUsers().size() <= 1) {
                    // Try and read the display name from the local profile
                    final Cursor cursor = context.getContentResolver().query(
                            Profile.CONTENT_URI, new String[] {Phone._ID, Phone.DISPLAY_NAME},
                            null, null, null);
                    if (cursor != null) {
                        try {
                            if (cursor.moveToFirst()) {
                                name = cursor.getString(cursor.getColumnIndex(Phone.DISPLAY_NAME));
                            } else if (!UserHandle.MU_ENABLED || !um.supportsMultipleUsers()) {
                                Xlog.d(TAG, "current user is owner.");
                                name = mContext.getResources().getString(R.string.user_owner);
                            }
                        } finally {
                            cursor.close();
                        }
                    }
                }
                return new Pair<String, Drawable>(name, avatar);
            }

            @Override
            protected void onPostExecute(Pair<String, Drawable> result) {
                super.onPostExecute(result);
                mModel.setUserTileInfo(result.first, result.second);
                mUserInfoTask = null;
            }
        };
        mUserInfoTask.execute();
    }

    private void setupQuickSettings() {
        // Setup the tiles that we are going to be showing (including the temporary ones)
        LayoutInflater inflater = LayoutInflater.from(mContext);

        addUserTiles(mContainerView, inflater);
        addSystemTiles(mContainerView, inflater);
        addTemporaryTiles(mContainerView, inflater);
        IQuickSettingsPlugin quickSettingsPlugin = PluginFactory.getQuickSettingsPlugin(mContext);
        quickSettingsPlugin.customizeTileViews(mContainerView);

        queryForUserInformation();
        queryForSslCaCerts();
        mTilesSetUp = true;

        /// M: [SystemUI] Add quick settings function(airplane, data conn, bluetooth, timeout, gps, etc.) @{.
        mQuickSettingsConnectionModel.buildIconViews();
        new Handler().postDelayed(new Runnable() {
            public void run() {
                setUpdate();
                mQuickSettingsConnectionModel.setUpdates(true);
                mQuickSettingsConnectionModel.initConfigurationState();
            }
        }, 200);
        /// M: [SystemUI] Add quick settings function(airplane, data conn, bluetooth, timeout, gps, etc.) @}.
    }

    private void startSettingsActivity(String action) {
        Intent intent = new Intent(action);
        startSettingsActivity(intent);
    }

    private void startSettingsActivity(Intent intent) {
        startSettingsActivity(intent, true);
    }

    private void collapsePanels() {
        getService().animateCollapsePanels();
    }

    private void startSettingsActivity(Intent intent, boolean onlyProvisioned) {
        if (onlyProvisioned && !getService().isDeviceProvisioned()) return;
        try {
            // Dismiss the lock screen when Settings starts.
            ActivityManagerNative.getDefault().dismissKeyguardOnNextActivity();
        } catch (RemoteException e) {
        }
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        mContext.startActivityAsUser(intent, new UserHandle(UserHandle.USER_CURRENT));
        collapsePanels();
    }

    private void addUserTiles(ViewGroup parent, LayoutInflater inflater) {
        QuickSettingsTileView userTile = (QuickSettingsTileView)
                inflater.inflate(R.layout.quick_settings_tile, parent, false);
        userTile.setContent(R.layout.quick_settings_tile_user, inflater);
        userTile.setTileViewId(QuickSettingsTileViewId.ID_User);

        userTile.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                collapsePanels();
                final UserManager um = UserManager.get(mContext);
                if (um.getUsers(true).size() > 1) {
                    // Since keyguard and systemui were merged into the same process to save
                    // memory, they share the same Looper and graphics context.  As a result,
                    // there's no way to allow concurrent animation while keyguard inflates.
                    // The workaround is to add a slight delay to allow the animation to finish.
                    mHandler.postDelayed(new Runnable() {
                        public void run() {
                            try {
                                WindowManagerGlobal.getWindowManagerService().lockNow(null);
                            } catch (RemoteException e) {
                                Log.e(TAG, "Couldn't show user switcher", e);
                            }
                        }
                    }, 400); // TODO: ideally this would be tied to the collapse of the panel
                } else {
                    Intent intent = ContactsContract.QuickContact.composeQuickContactsIntent(
                            mContext, v, ContactsContract.Profile.CONTENT_URI,
                            ContactsContract.QuickContact.MODE_LARGE, null);
                    mContext.startActivityAsUser(intent, new UserHandle(UserHandle.USER_CURRENT));
                }
            }
        });
        mModel.addUserTile(userTile, new QuickSettingsModel.RefreshCallback() {
            @Override
            public void refreshView(QuickSettingsTileView view, State state) {
                UserState us = (UserState) state;
                ImageView iv = (ImageView) view.findViewById(R.id.user_imageview);
                TextView tv = (TextView) view.findViewById(R.id.user_textview);
                tv.setText(state.label);
                iv.setImageDrawable(us.avatar);
                view.setContentDescription(mContext.getString(
                        R.string.accessibility_quick_settings_user, state.label));
            }
        });
        parent.addView(userTile);
        mDynamicSpannedTiles.add(userTile);
    }

    private void addSystemTiles(ViewGroup parent, LayoutInflater inflater) {
        /// M: MTK Quick Setting.
        addLaptopBatteryTile(parent, inflater);
        addBatteryTile(parent, inflater);
        addSettingTile(parent, inflater);

        addWifiTile(parent, inflater);
        addBluetoothTile(parent, inflater);
        addLocationTile(parent, inflater);

        addAirplaneTile(parent, inflater);
        addDataConnectionTile(parent, inflater);
        addDataUsageTile(parent, inflater);

        addAudioProfileTile(parent, inflater);
        addBrightnessTile(parent, inflater);
        addRotationTile(parent, inflater);

        addTimeoutTile(parent, inflater);
    }

    private void addTemporaryTiles(final ViewGroup parent, final LayoutInflater inflater) {
        // Alarm tile
        final QuickSettingsBasicTile alarmTile
                = new QuickSettingsBasicTile(mContext);
        alarmTile.setImageResource(R.drawable.ic_qs_alarm_on);
        alarmTile.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startSettingsActivity(AlarmClock.ACTION_SHOW_ALARMS);
            }
        });
        mModel.addAlarmTile(alarmTile, new QuickSettingsModel.RefreshCallback() {
            @Override
            public void refreshView(QuickSettingsTileView unused, State alarmState) {
                alarmTile.setText(alarmState.label);
                alarmTile.setVisibility(alarmState.enabled ? View.VISIBLE : View.GONE);
                alarmTile.setContentDescription(mContext.getString(
                        R.string.accessibility_quick_settings_alarm, alarmState.label));
            }
        });
        parent.addView(alarmTile);

        // Remote Display
        QuickSettingsBasicTile remoteDisplayTile
                = new QuickSettingsBasicTile(mContext);
        remoteDisplayTile.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                /// M: Support Remote Display Feature.
                startSettingsActivity(android.provider.Settings.ACTION_WIFI_DISPLAY_SETTINGS);                    
                /*
                collapsePanels();

                final Dialog[] dialog = new Dialog[1];
                dialog[0] = MediaRouteDialogPresenter.createDialog(mContext,
                        MediaRouter.ROUTE_TYPE_REMOTE_DISPLAY,
                        new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        dialog[0].dismiss();
                        startSettingsActivity(
                                android.provider.Settings.ACTION_WIFI_DISPLAY_SETTINGS);
                    }
                });
                dialog[0].getWindow().setType(WindowManager.LayoutParams.TYPE_VOLUME_OVERLAY);
                dialog[0].show();
                        */
            }
        });
        mModel.addRemoteDisplayTile(remoteDisplayTile,
                new QuickSettingsModel.BasicRefreshCallback(remoteDisplayTile)
                        .setShowWhenEnabled(false)); /// M: RemoteDisplay is always on.
        if (FeatureOption.MTK_WFD_SUPPORT) {
            parent.addView(remoteDisplayTile);
        }

        if (SHOW_IME_TILE || QuickSettingsTileView.DEBUG_GONE_TILES) {
            // IME
            final QuickSettingsBasicTile imeTile
                    = new QuickSettingsBasicTile(mContext);
            imeTile.setImageResource(R.drawable.ic_qs_ime);
            imeTile.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    try {
                        collapsePanels();
                        Intent intent = new Intent(Settings.ACTION_SHOW_INPUT_METHOD_PICKER);
                        PendingIntent pendingIntent = PendingIntent.getBroadcast(mContext, 0, intent, 0);
                        pendingIntent.send();
                    } catch (Exception e) {}
                }
            });
            mModel.addImeTile(imeTile,
                    new QuickSettingsModel.BasicRefreshCallback(imeTile)
                            .setShowWhenEnabled(true));
            parent.addView(imeTile);
        }

        // Bug reports
        final QuickSettingsBasicTile bugreportTile
                = new QuickSettingsBasicTile(mContext);
        bugreportTile.setImageResource(com.android.internal.R.drawable.stat_sys_adb);
        bugreportTile.setTextResource(com.android.internal.R.string.bugreport_title);
        bugreportTile.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                collapsePanels();
                showBugreportDialog();
            }
        });
        mModel.addBugreportTile(bugreportTile, new QuickSettingsModel.RefreshCallback() {
            @Override
            public void refreshView(QuickSettingsTileView view, State state) {
                view.setVisibility(state.enabled ? View.VISIBLE : View.GONE);
            }
        });
        parent.addView(bugreportTile);
        /*
        QuickSettingsTileView mediaTile = (QuickSettingsTileView)
                inflater.inflate(R.layout.quick_settings_tile, parent, false);
        mediaTile.setContent(R.layout.quick_settings_tile_media, inflater);
        parent.addView(mediaTile);
        QuickSettingsTileView imeTile = (QuickSettingsTileView)
                inflater.inflate(R.layout.quick_settings_tile, parent, false);
        imeTile.setContent(R.layout.quick_settings_tile_ime, inflater);
        imeTile.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                parent.removeViewAt(0);
            }
        });
        parent.addView(imeTile);
        */

        // SSL CA Cert Warning.
        final QuickSettingsBasicTile sslCaCertWarningTile =
                new QuickSettingsBasicTile(mContext, null, R.layout.quick_settings_tile_monitoring);
        sslCaCertWarningTile.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                collapsePanels();
                startSettingsActivity(Settings.ACTION_MONITORING_CERT_INFO);
            }
        });

        sslCaCertWarningTile.setImageResource(
                com.android.internal.R.drawable.indicator_input_error);
        sslCaCertWarningTile.setTextResource(R.string.ssl_ca_cert_warning);

        mModel.addSslCaCertWarningTile(sslCaCertWarningTile,
                new QuickSettingsModel.BasicRefreshCallback(sslCaCertWarningTile)
                        .setShowWhenEnabled(true));
        parent.addView(sslCaCertWarningTile);
    }

    void updateResources() {
        Resources r = mContext.getResources();

        // Update the model
        mModel.updateResources();
        IQuickSettingsPlugin quickSettingsPlugin = PluginFactory.getQuickSettingsPlugin(mContext);
        quickSettingsPlugin.updateResources();

        /// M: Reload User Info.
        reloadUserInfo();

        // Update the User, Time, and Settings tiles spans, and reset everything else
        int span = r.getInteger(R.integer.quick_settings_user_time_settings_tile_span);
        for (QuickSettingsTileView v : mDynamicSpannedTiles) {
            v.setColumnSpan(span);
        }
        ((QuickSettingsContainerView)mContainerView).updateResources();
        mContainerView.requestLayout();

        /// M: [SystemUI] Add quick settings function(airplane, data conn, bluetooth, timeout, gps, etc.) @{.
        mQuickSettingsConnectionModel.updateResources();
        /// M: [SystemUI] Add quick settings function(airplane, data conn, bluetooth, timeout, gps, etc.) @}.

        /// M: [ALPS00772744] Update Wifi Display Resource.
        //applyWifiDisplayStatus();
    }


    private void showBrightnessDialog() {
        Intent intent = new Intent(Intent.ACTION_SHOW_BRIGHTNESS_DIALOG);
        mContext.sendBroadcast(intent);
    }

    private void showBugreportDialog() {
        final AlertDialog.Builder builder = new AlertDialog.Builder(mContext);
        builder.setPositiveButton(com.android.internal.R.string.report, new OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                if (which == DialogInterface.BUTTON_POSITIVE) {
                    // Add a little delay before executing, to give the
                    // dialog a chance to go away before it takes a
                    // screenshot.
                    mHandler.postDelayed(new Runnable() {
                        @Override public void run() {
                            try {
                                ActivityManagerNative.getDefault()
                                        .requestBugReport();
                            } catch (RemoteException e) {
                            }
                        }
                    }, 500);
                }
            }
        });
        builder.setMessage(com.android.internal.R.string.bugreport_message);
        builder.setTitle(com.android.internal.R.string.bugreport_title);
        builder.setCancelable(true);
        final Dialog dialog = builder.create();
        dialog.getWindow().setType(WindowManager.LayoutParams.TYPE_SYSTEM_ALERT);
        try {
            WindowManagerGlobal.getWindowManagerService().dismissKeyguard();
        } catch (RemoteException e) {
        }
        dialog.show();
    }

    private void applyBluetoothStatus() {
        mModel.onBluetoothStateChange(mBluetoothState);
    }

    private void applyLocationEnabledStatus() {
        mModel.onLocationSettingsChanged(mLocationController.isLocationEnabled());
    }

    void reloadUserInfo() {
        if (mUserInfoTask != null) {
            mUserInfoTask.cancel(false);
            mUserInfoTask = null;
        }
        if (mTilesSetUp) {
            queryForUserInformation();
            queryForSslCaCerts();
        }
    }

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (BluetoothAdapter.ACTION_STATE_CHANGED.equals(action)) {
                int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE,
                        BluetoothAdapter.ERROR);
                mBluetoothState.enabled = (state == BluetoothAdapter.STATE_ON);
                applyBluetoothStatus();
            } else if (BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED.equals(action)) {
                int status = intent.getIntExtra(BluetoothAdapter.EXTRA_CONNECTION_STATE,
                        BluetoothAdapter.STATE_DISCONNECTED);
                mBluetoothState.connected = (status == BluetoothAdapter.STATE_CONNECTED);
                applyBluetoothStatus();
            } else if (Intent.ACTION_USER_SWITCHED.equals(action)) {
                reloadUserInfo();
            } else if (Intent.ACTION_CONFIGURATION_CHANGED.equals(action)) {
                if (mUseDefaultAvatar) {
                    queryForUserInformation();
                }
            } else if (KeyChain.ACTION_STORAGE_CHANGED.equals(action)) {
                queryForSslCaCerts();
            }
        }
    };

    private final BroadcastReceiver mProfileReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (ContactsContract.Intents.ACTION_PROFILE_CHANGED.equals(action) ||
                    Intent.ACTION_USER_INFO_CHANGED.equals(action)) {
                try {
                    final int currentUser = ActivityManagerNative.getDefault().getCurrentUser().id;
                    final int changedUser =
                            intent.getIntExtra(Intent.EXTRA_USER_HANDLE, getSendingUserId());
                    if (changedUser == currentUser) {
                        reloadUserInfo();
                    }
                } catch (RemoteException e) {
                    Log.e(TAG, "Couldn't get current user id for profile change", e);
                }
            }

        }
    };

    private abstract static class NetworkActivityCallback
            implements QuickSettingsModel.RefreshCallback {
        private final long mDefaultDuration = new ValueAnimator().getDuration();
        private final long mShortDuration = mDefaultDuration / 3;

        public void setActivity(View view, ActivityState state) {
            setVisibility(view.findViewById(R.id.activity_in), state.activityIn);
            setVisibility(view.findViewById(R.id.activity_out), state.activityOut);
        }

        private void setVisibility(View view, boolean visible) {
            final float newAlpha = visible ? 1 : 0;
            if (view.getAlpha() != newAlpha) {
                view.animate()
                    .setDuration(visible ? mShortDuration : mDefaultDuration)
                    .alpha(newAlpha)
                    .start();
            }
        }
    }

    /// M: [SystemUI] Add quick settings function(airplane, data conn, bluetooth, timeout, gps, etc.) .
    private QuickSettingsConnectionModel mQuickSettingsConnectionModel;
    // M : tablet has different icon flow
    private static boolean IS_Tablet = ("tablet".equals(SystemProperties.get("ro.build.characteristics")));

    /// M: [SystemUI] Add quick settings function(airplane, data conn, bluetooth, timeout, gps, etc.) @{.
    private void setUpdate() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(TelephonyIntents.ACTION_SIM_INFO_UPDATE);
        filter.addAction("android.intent.action.SIM_NAME_UPDATE");
        mContext.registerReceiver(mIntentReceiver, filter, null, null);
    }
    
    private BroadcastReceiver mIntentReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action.equals(TelephonyIntents.ACTION_SIM_INFO_UPDATE)) {
                Xlog.d(TAG, "sim ready");
                updateForSimReady();
            } else if (action.equals("android.intent.action.SIM_NAME_UPDATE")) {
                updateForSimNameUpdate();
            }
        }
    };
    
    private void updateForSimReady() {
        SIMHelper.updateSIMInfos(mContext);
        mQuickSettingsConnectionModel.updateForSimReady();
    }
    
    /**
     * M: When sim is ready, we get SIMInfoList, but at that time, SIMInfo's name may be null, 
     * so need to get again when sim name updated. 
     */
    private void updateForSimNameUpdate() {
        updateForSimReady();
    }
    /// M: [SystemUI] Add quick settings function(airplane, data conn, bluetooth, timeout, gps, etc.) @}.

    public void updateSimInfo(Intent intent) {
        if (mQuickSettingsConnectionModel != null) {
            mQuickSettingsConnectionModel.updateSimInfo(intent);
        }
    }

    public void dismissDialogs() {
        if (mQuickSettingsConnectionModel != null) {
            mQuickSettingsConnectionModel.dismissDialogs();
        }
    }    

    /**
     * M: Used to check weather this device is wifi only.
     */
    private boolean isWifiOnlyDevice() {
      ConnectivityManager cm = (ConnectivityManager)mContext.getSystemService(mContext.CONNECTIVITY_SERVICE);
      return  !(cm.isNetworkSupported(ConnectivityManager.TYPE_MOBILE));
    }

    private void addBrightnessTile(ViewGroup parent, LayoutInflater inflater) {
        // Brightness
        final QuickSettingsBasicTile brightnessTile
                = new QuickSettingsBasicTile(mContext);
        brightnessTile.setImageResource(R.drawable.ic_qs_brightness_auto_off);
        brightnessTile.setTileViewId(QuickSettingsTileViewId.ID_Brightness);
        brightnessTile.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                collapsePanels();
                showBrightnessDialog();
            }
        });
        mModel.addBrightnessTile(brightnessTile,
                new QuickSettingsModel.BasicRefreshCallback(brightnessTile));
        parent.addView(brightnessTile);
    }

    private void addSettingTile(ViewGroup parent, LayoutInflater inflater) {
        // Settings tile
        final QuickSettingsBasicTile settingsTile = new QuickSettingsBasicTile(mContext);
        settingsTile.setImageResource(R.drawable.ic_qs_settings);
        settingsTile.setTileViewId(QuickSettingsTileViewId.ID_Settings);
        settingsTile.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startSettingsActivity(android.provider.Settings.ACTION_SETTINGS);
            }
        });
        mModel.addSettingsTile(settingsTile,
                new QuickSettingsModel.BasicRefreshCallback(settingsTile));
        parent.addView(settingsTile);
    }

    private void addWifiTile(ViewGroup parent, LayoutInflater inflater) {
        /// M: Wifi Tile
        final QuickSettingsBasicTile wifiTile = 
            new QuickSettingsBasicTile(mContext, null, R.layout.mtk_quick_settings_tile_basic);
        wifiTile.setTileViewId(QuickSettingsTileViewId.ID_Wifi);
        wifiTile.setOnLongClickListener(new OnLongClickListener() {
            public boolean onLongClick(View v) {
                startSettingsActivity(android.provider.Settings.ACTION_WIFI_SETTINGS);
                return true;
            }
        });
        parent.addView(wifiTile);
        if (!FeatureOption.MTK_WLAN_SUPPORT) {
            wifiTile.setVisibility(View.GONE);
        }
        mQuickSettingsConnectionModel.addWifiTile(wifiTile);
    }

    private void addBluetoothTile(ViewGroup parent, LayoutInflater inflater) {
        /// M: BT Tile
        final QuickSettingsBasicTile bluetoothTile = 
            new QuickSettingsBasicTile(mContext, null, R.layout.mtk_quick_settings_tile_basic);
        bluetoothTile.setTileViewId(QuickSettingsTileViewId.ID_BlueTooth);
        bluetoothTile.setOnLongClickListener(new OnLongClickListener() {
            public boolean onLongClick(View v) {
                startSettingsActivity(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS);
                return true;
             }
        });
        if (mModel.deviceSupportsBluetooth()
                && FeatureOption.MTK_BT_SUPPORT) {
            parent.addView(bluetoothTile);
        }
        mQuickSettingsConnectionModel.addBluetoothTile(bluetoothTile);
    }

    private void addDataConnectionTile(ViewGroup parent, LayoutInflater inflater) {
        /// M: Data Connection.
        QuickSettingsBasicTile dataConnectionTile = 
            new QuickSettingsBasicTile(mContext, null, R.layout.mtk_quick_settings_tile_basic);
        dataConnectionTile.setTileViewId(QuickSettingsTileViewId.ID_DataConnection);
        if (!isWifiOnlyDevice()) {
            parent.addView(dataConnectionTile);
        }
        mQuickSettingsConnectionModel.addMobileTile(dataConnectionTile);
    }

    private void addAirplaneTile(ViewGroup parent, LayoutInflater inflater) {
        /// M: Airplane Mode
        QuickSettingsBasicTile airplaneTile = new QuickSettingsBasicTile(mContext);
        airplaneTile.setTileViewId(QuickSettingsTileViewId.ID_Airplane);
        parent.addView(airplaneTile);
        mQuickSettingsConnectionModel.addAirlineTile(airplaneTile);
    }

    private void addRotationTile(ViewGroup parent, LayoutInflater inflater) {
        /// M: Rotation Lock
        final QuickSettingsBasicTile rotationLockTile
                    = new QuickSettingsBasicTile(mContext);
        rotationLockTile.setTileViewId(QuickSettingsTileViewId.ID_RotationLock);
        rotationLockTile.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                final boolean locked = mRotationLockController.isRotationLocked();
                mRotationLockController.setRotationLocked(!locked);
            }
        });
        mModel.addRotationLockTile(rotationLockTile, mRotationLockController,
            new QuickSettingsModel.RefreshCallback() {
            @Override
            public void refreshView(QuickSettingsTileView view, State state) {
                QuickSettingsModel.RotationLockState rotationLockState =
                        (QuickSettingsModel.RotationLockState) state;
                view.setVisibility(rotationLockState.visible
                        ? View.VISIBLE : View.GONE);
                if (state.iconId != 0) {
                    // needed to flush any cached IDs
                    rotationLockTile.setImageDrawable(null);
                    rotationLockTile.setImageResource(state.iconId);
                }
                if (state.label != null) {
                    rotationLockTile.setText(state.label);
                }
            }
        });

        QuickSettingsBasicTile autoRotateTile = new QuickSettingsBasicTile(mContext);
        autoRotateTile.setTileViewId(QuickSettingsTileViewId.ID_AutoRotate);

        if (mContext.getResources().getBoolean(R.bool.quick_settings_show_rotation_lock)) {
            parent.addView(rotationLockTile);
        } else {
            parent.addView(autoRotateTile);
        }
        mQuickSettingsConnectionModel.addAutoRotateTile(autoRotateTile);
    }

    private void addDataUsageTile(ViewGroup parent, LayoutInflater inflater) {
        /// M: Data Usage.
        QuickSettingsBasicTile datausageTile = new QuickSettingsBasicTile(mContext);
        datausageTile.setImageResource(R.drawable.ic_qs_data_usage);
        datausageTile.setTileViewId(QuickSettingsTileViewId.ID_Datausage);
        datausageTile.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent();
                intent.setComponent(new ComponentName(
                                "com.android.settings",
                                "com.android.settings.Settings$DataUsageSummaryActivity"));
                    startSettingsActivity(intent);
                }
            });
        parent.addView(datausageTile);
        mQuickSettingsConnectionModel.addDataUsageTile(datausageTile);
    }

    private void addAudioProfileTile(ViewGroup parent, LayoutInflater inflater) {
        /// M: Audio profile
        QuickSettingsBasicTile audioProfileTile = new QuickSettingsBasicTile(mContext);
        audioProfileTile.setTileViewId(QuickSettingsTileViewId.ID_AudioProfile);
        audioProfileTile.setOnLongClickListener(new OnLongClickListener() {
            public boolean onLongClick(View v) {
                Intent intent = new Intent();
                intent.setComponent(new ComponentName(
                                "com.android.settings",
                                "com.android.settings.Settings$AudioProfileSettingsActivity"));
                startSettingsActivity(intent);
                return true;
            }
        });
        if (FeatureOption.MTK_AUDIO_PROFILES) {
            parent.addView(audioProfileTile);
        }
        mQuickSettingsConnectionModel.addAudioProfileTile(audioProfileTile);
    }

    private void addBatteryTile(ViewGroup parent, LayoutInflater inflater) {
        // Battery
        final QuickSettingsTileView batteryTile = (QuickSettingsTileView)
                inflater.inflate(R.layout.quick_settings_tile, parent, false);
        batteryTile.setContent(R.layout.quick_settings_tile_battery, inflater);
        batteryTile.setTileViewId(QuickSettingsTileViewId.ID_Battery);
        batteryTile.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startSettingsActivity(Intent.ACTION_POWER_USAGE_SUMMARY);
            }
        });
        mModel.addBatteryTile(batteryTile, new QuickSettingsModel.RefreshCallback() {
            @Override
            public void refreshView(QuickSettingsTileView unused, State state) {
                QuickSettingsModel.BatteryState batteryState =
                        (QuickSettingsModel.BatteryState) state;
                String t;
                if (batteryState.batteryLevel == 100) {
                    t = mContext.getString(R.string.quick_settings_battery_charged_label);
                } else {
                    t = batteryState.pluggedIn
                        ? mContext.getString(R.string.quick_settings_battery_charging_label,
                                batteryState.batteryLevel)
                        : mContext.getString(R.string.status_bar_settings_battery_meter_format,
                                batteryState.batteryLevel);
                }
                ((TextView)batteryTile.findViewById(R.id.text)).setText(t);
                batteryTile.setContentDescription(
                        mContext.getString(R.string.accessibility_quick_settings_battery, t));
            }
        });
        parent.addView(batteryTile);
    }

    private void addRSSITile(ViewGroup parent, LayoutInflater inflater) {
        if (mModel.deviceHasMobileData()) {
            // RSSI
            QuickSettingsTileView rssiTile = (QuickSettingsTileView)
                    inflater.inflate(R.layout.quick_settings_tile, parent, false);
            rssiTile.setContent(R.layout.quick_settings_tile_rssi, inflater);
            rssiTile.setTileViewId(QuickSettingsTileViewId.ID_Rssi);
            rssiTile.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Intent intent = new Intent();
                    intent.setComponent(new ComponentName(
                            "com.android.settings",
                            "com.android.settings.Settings$DataUsageSummaryActivity"));
                    startSettingsActivity(intent);
                }
            });
            mModel.addRSSITile(rssiTile, new NetworkActivityCallback() {
                @Override
                public void refreshView(QuickSettingsTileView view, State state) {
                    RSSIState rssiState = (RSSIState) state;
                    ImageView iv = (ImageView) view.findViewById(R.id.rssi_image);
                    ImageView iov = (ImageView) view.findViewById(R.id.rssi_overlay_image);
                    TextView tv = (TextView) view.findViewById(R.id.rssi_textview);
                    // Force refresh
                    iv.setImageDrawable(null);
                    iv.setImageResource(rssiState.signalIconId);

                    if (rssiState.dataTypeIconId > 0) {
                        iov.setImageResource(rssiState.dataTypeIconId);
                    } else {
                        iov.setImageDrawable(null);
                    }
                    setActivity(view, rssiState);

                    tv.setText(state.label);
                    view.setContentDescription(mContext.getResources().getString(
                            R.string.accessibility_quick_settings_mobile,
                            rssiState.signalContentDescription, rssiState.dataContentDescription,
                            state.label));
                }
            });
            parent.addView(rssiTile);
        }
    }

    private void addLocationTile(ViewGroup parent, LayoutInflater inflater) {
        // M: Location
        final QuickSettingsBasicTile locationTile
                = new QuickSettingsBasicTile(mContext);
        locationTile.setImageResource(R.drawable.ic_qs_location_on);
        locationTile.setTextResource(R.string.quick_settings_location_label);
        locationTile.setTileViewId(QuickSettingsTileViewId.ID_Location);
        locationTile.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                locationTile.setEnabled(false);
                /// M: [ALPS01255941] Use AsyncTask and Update UI Directly.
                new AsyncTask<Void, Void, Integer>() {
                Integer LOCATION_OP_FAIL = 0;
                Integer LOCATION_OP_SUCCESS_ON = 1;
                Integer LOCATION_OP_SUCCESS_OFF = 2;
                @Override
                protected Integer doInBackground(Void... args) {
                    boolean newLocationEnabledState = !mLocationController.isLocationEnabled();
                    Xlog.d(TAG, "setLocationEnabled = " + newLocationEnabledState);
                    if (mLocationController.setLocationEnabled(newLocationEnabledState)) {
                        if (newLocationEnabledState) {
                            return LOCATION_OP_SUCCESS_ON;
                        } else {
                            return LOCATION_OP_SUCCESS_OFF;
                        }
                    } else {
                        Xlog.d(TAG, "setLocationEnabled not allow!");
                        return LOCATION_OP_FAIL;
                    }
                }
                @Override
                protected void onPostExecute(Integer result) {
                    if (result == LOCATION_OP_FAIL) {
                        /// do nothing.
                    } else if (result == LOCATION_OP_SUCCESS_ON) {
                        mModel.onLocationSettingsChanged(true);
                    } else if (result == LOCATION_OP_SUCCESS_OFF) {
                        mModel.onLocationSettingsChanged(false);
                    }
                    locationTile.setEnabled(true);
                }
                }.execute();
            }
        });
        locationTile.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                startSettingsActivity(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS);
                return true; // Consume click
            }} );
        mModel.addLocationTile(locationTile,
                new QuickSettingsModel.BasicRefreshCallback(locationTile));
        /// M: Add location tile for refreshview.
        mQuickSettingsConnectionModel.addLocationTile(locationTile);
        if (FeatureOption.MTK_GPS_SUPPORT) {
            parent.addView(locationTile);
        }
    }

    private void addTimeoutTile(ViewGroup parent, LayoutInflater inflater) {
        /// M: Time out
        QuickSettingsTileView timeoutTile = (QuickSettingsTileView) inflater.inflate(
                R.layout.quick_settings_tile, parent, false);
        timeoutTile.setContent(R.layout.mtk_quick_settings_tile_timeout,inflater);
        timeoutTile.setTileViewId(QuickSettingsTileViewId.ID_Timeout);
        timeoutTile.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                startSettingsActivity(android.provider.Settings.ACTION_DISPLAY_SETTINGS);
                return true; // Consume click
            }} );
        /// parent.addView(timeoutTile);
        mQuickSettingsConnectionModel.addTimeoutTile(timeoutTile);
    }

    private void addLaptopBatteryTile(ViewGroup parent, LayoutInflater inflater) {
        // Laptop Battery
        final QuickSettingsTileView batteryTile = (QuickSettingsTileView)
                inflater.inflate(R.layout.quick_settings_tile, parent, false);
        batteryTile.setContent(R.layout.mtk_quick_settings_tile_laptopbattery, inflater);
        batteryTile.setTileViewId(QuickSettingsTileViewId.ID_LaptopBattery);
        batteryTile.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                /// Do nothing.
            }
        });
        mModel.addLaptopBatteryTile(batteryTile, new QuickSettingsModel.RefreshCallback() {
            @Override
            public void refreshView(QuickSettingsTileView view, State state) {
                QuickSettingsModel.LaptopBatteryState batteryState =
                        (QuickSettingsModel.LaptopBatteryState) state;
                if (batteryState.isPresent) {
                    LaptopBatteryView v = 
                        (LaptopBatteryView) view.findViewById(R.id.image);
                    String t;
                    if (batteryState.batteryLevel == 100) {
                        t = mContext.getString(R.string.quick_settings_battery_charged_label);
                    } else {
                        t = batteryState.pluggedIn
                            ? mContext.getString(R.string.quick_settings_battery_charging_label,
                                    batteryState.batteryLevel)
                            : mContext.getString(R.string.status_bar_settings_battery_meter_format,
                                    batteryState.batteryLevel);
                    }
                    ((TextView)batteryTile.findViewById(R.id.text)).setText(t);
                    batteryTile.setContentDescription(
                            mContext.getString(R.string.accessibility_quick_settings_battery, t));
                    view.setVisibility(View.VISIBLE);
                    /// Update.
                    v.setBatteryLevel(batteryState.batteryLevel, batteryState.pluggedIn);
                } else {
                    view.setVisibility(View.GONE);
                }
            }
        });
        parent.addView(batteryTile);
    }

}
