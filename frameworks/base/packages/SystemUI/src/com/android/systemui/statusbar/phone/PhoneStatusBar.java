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

package com.android.systemui.statusbar.phone;

import static android.app.StatusBarManager.NAVIGATION_HINT_BACK_ALT;
import static android.app.StatusBarManager.WINDOW_STATE_HIDDEN;
import static android.app.StatusBarManager.WINDOW_STATE_SHOWING;
import static android.app.StatusBarManager.windowStateToString;
import static com.android.systemui.statusbar.phone.BarTransitions.MODE_OPAQUE;
import static com.android.systemui.statusbar.phone.BarTransitions.MODE_SEMI_TRANSPARENT;
import static com.android.systemui.statusbar.phone.BarTransitions.MODE_TRANSLUCENT;
import static com.android.systemui.statusbar.phone.BarTransitions.MODE_LIGHTS_OUT;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.animation.TimeInterpolator;
import android.app.ActivityManager;
import android.app.ActivityManagerNative;
import android.app.Dialog;
import android.app.Notification;
import android.app.PendingIntent;
import android.app.StatusBarManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.database.ContentObserver;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.inputmethodservice.InputMethodService;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.PowerManager;
import android.os.Process;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.storage.StorageVolume;
import android.os.SystemClock;
import android.os.UserHandle;
import android.provider.Settings;
import android.provider.Telephony;
import android.service.notification.StatusBarNotification;
import android.telephony.ServiceState;
import android.util.DisplayMetrics;
import android.util.EventLog;
import android.util.Log;
import android.view.Display;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.view.ViewPropertyAnimator;
import android.view.ViewStub;
import android.view.WindowManager;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.view.animation.DecelerateInterpolator;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.android.internal.statusbar.StatusBarIcon;
import com.android.systemui.DemoMode;
import com.android.systemui.EventLogTags;
import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.TelephonyIntents;

import com.android.systemui.R;
import com.android.systemui.statusbar.BaseStatusBar;
import com.android.systemui.statusbar.CommandQueue;
import com.android.systemui.statusbar.GestureRecorder;
import com.android.systemui.statusbar.NotificationData;
import com.android.systemui.statusbar.NotificationData.Entry;
import com.android.systemui.statusbar.SignalClusterView;
import com.android.systemui.statusbar.StatusBarIconView;
import com.android.systemui.statusbar.policy.BatteryController;
import com.android.systemui.statusbar.policy.BluetoothController;
import com.android.systemui.statusbar.policy.DateView;
import com.android.systemui.statusbar.policy.HeadsUpNotificationView;
import com.android.systemui.statusbar.policy.LocationController;
import com.android.systemui.statusbar.policy.NetworkController;
import com.android.systemui.statusbar.policy.NotificationRowLayout;
import com.android.systemui.statusbar.policy.OnSizeChangedListener;
import com.android.systemui.statusbar.policy.RotationLockController;
import com.android.systemui.statusbar.policy.TelephonyIcons;

import com.mediatek.common.featureoption.FeatureOption;
import com.mediatek.systemui.ext.PluginFactory;
import com.mediatek.systemui.statusbar.toolbar.ToolBarIndicator;
import com.mediatek.systemui.statusbar.toolbar.ToolBarView;
import com.mediatek.systemui.statusbar.util.AutoTestHelper;
import com.mediatek.systemui.statusbar.util.CarrierLabel;
import com.mediatek.systemui.statusbar.util.LaptopBatteryView;
import com.mediatek.systemui.statusbar.util.LteDcController;
import com.mediatek.systemui.statusbar.util.LtdDcStateChangeCallback;
import com.mediatek.systemui.statusbar.util.SIMHelper;
import com.mediatek.xlog.Xlog;
import com.mediatek.telephony.SimInfoManager;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class PhoneStatusBar extends BaseStatusBar implements DemoMode {
    static final String TAG = "PhoneStatusBar";
    public static final boolean DEBUG = BaseStatusBar.DEBUG;
    public static final boolean SPEW = false;
    public static final boolean DUMPTRUCK = true; // extra dumpsys info
    public static final boolean DEBUG_GESTURES = false;

    public static final boolean DEBUG_WINDOW_STATE = true;

    public static final boolean SETTINGS_DRAG_SHORTCUT = true;

    // additional instrumentation for testing purposes; intended to be left on during development
    public static final boolean CHATTY = DEBUG;

    public static final String ACTION_STATUSBAR_START
            = "com.android.internal.policy.statusbar.START";

    private static final int MSG_OPEN_NOTIFICATION_PANEL = 1000;
    private static final int MSG_CLOSE_PANELS = 1001;
    private static final int MSG_OPEN_SETTINGS_PANEL = 1002;
    private static final int MSG_OPEN_NOTIFICATION_PANEL_SLOW = 1005;

    // 1020-1030 reserved for BaseStatusBar

    private static final boolean CLOSE_PANEL_WHEN_EMPTIED = true;

    private static final int NOTIFICATION_PRIORITY_MULTIPLIER = 10; // see NotificationManagerService
    private static final int HIDE_ICONS_BELOW_SCORE = Notification.PRIORITY_LOW * NOTIFICATION_PRIORITY_MULTIPLIER;

    private static final int STATUS_OR_NAV_TRANSIENT =
            View.STATUS_BAR_TRANSIENT | View.NAVIGATION_BAR_TRANSIENT;
    private static final long AUTOHIDE_TIMEOUT_MS = 3000;

    // fling gesture tuning parameters, scaled to display density
    private float mSelfExpandVelocityPx; // classic value: 2000px/s
    private float mSelfCollapseVelocityPx; // classic value: 2000px/s (will be negated to collapse "up")
    private float mFlingExpandMinVelocityPx; // classic value: 200px/s
    private float mFlingCollapseMinVelocityPx; // classic value: 200px/s
    private float mCollapseMinDisplayFraction; // classic value: 0.08 (25px/min(320px,480px) on G1)
    private float mExpandMinDisplayFraction; // classic value: 0.5 (drag open halfway to expand)
    private float mFlingGestureMaxXVelocityPx; // classic value: 150px/s

    private float mExpandAccelPx; // classic value: 2000px/s/s
    private float mCollapseAccelPx; // classic value: 2000px/s/s (will be negated to collapse "up")

    private float mFlingGestureMaxOutputVelocityPx; // how fast can it really go? (should be a little
                                                    // faster than mSelfCollapseVelocityPx)

    PhoneStatusBarPolicy mIconPolicy;

    // These are no longer handled by the policy, because we need custom strategies for them
    BluetoothController mBluetoothController;
    BatteryController mBatteryController;
    LocationController mLocationController;
    NetworkController mNetworkController;
    RotationLockController mRotationLockController;

    int mNaturalBarHeight = -1;
    int mIconSize = -1;
    int mIconHPadding = -1;
    Display mDisplay;
    Point mCurrentDisplaySize = new Point();
    private float mHeadsUpVerticalOffset;
    private int[] mPilePosition = new int[2];

    StatusBarWindowView mStatusBarWindow;
    PhoneStatusBarView mStatusBarView;
    private int mStatusBarWindowState = WINDOW_STATE_SHOWING;

    int mPixelFormat;
    Object mQueueLock = new Object();

    // viewgroup containing the normal contents of the statusbar
    LinearLayout mStatusBarContents;

    // right-hand icons
    LinearLayout mSystemIconArea;

    // left-hand icons
    LinearLayout mStatusIcons;
    // the icons themselves
    IconMerger mNotificationIcons;
    // [+>
    View mMoreIcon;

    // expanded notifications
    NotificationPanelView mNotificationPanel; // the sliding/resizing panel within the notification window
    public ScrollView mScrollView; /// M: Public 
    View mExpandedContents;
    int mNotificationPanelGravity;
    int mNotificationPanelMarginBottomPx, mNotificationPanelMarginPx;
    float mNotificationPanelMinHeightFrac;
    boolean mNotificationPanelIsFullScreenWidth;
    TextView mNotificationPanelDebugText;

    // settings
    QuickSettings mQS;
    public boolean mHasSettingsPanel, mHasFlipSettings; /// M: Public 
    SettingsPanelView mSettingsPanel;
    public View mFlipSettingsView; /// M: Public 
    QuickSettingsContainerView mSettingsContainer;
    int mSettingsPanelGravity;

    // top bar
    View mNotificationPanelHeader;
    View mDateTimeView;
    View mClearButton;
    ImageView mSettingsButton, mNotificationButton;

    // carrier/wifi label
    private TextView mCarrierLabel;
    private boolean mCarrierLabelVisible = false;
    private int mCarrierLabelHeight;
    private TextView mEmergencyCallLabel;
    private int mNotificationHeaderHeight;

    private boolean mShowCarrierInPanel = false;

    //status bar plmn text
    private TextView mPlmnDisplay;  
    // position
    int[] mPositionTmp = new int[2];
    boolean mExpandedVisible;

    // the date view
    DateView mDateView;

    // for heads up notifications
    private HeadsUpNotificationView mHeadsUpNotificationView;
    private int mHeadsUpNotificationDecay;

    // on-screen navigation buttons
    private NavigationBarView mNavigationBarView = null;
    private int mNavigationBarWindowState = WINDOW_STATE_SHOWING;

    // the tracker view
    int mTrackingPosition; // the position of the top of the tracking view.

    // ticker
    private Ticker mTicker;
    private View mTickerView;
    private boolean mTicking;

    // Tracking finger for opening/closing.
    int mEdgeBorder; // corresponds to R.dimen.status_bar_edge_ignore
    boolean mTracking;
    VelocityTracker mVelocityTracker;

    int[] mAbsPos = new int[2];
    Runnable mPostCollapseCleanup = null;

    // for disabling the status bar
    int mDisabled = 0;

    // tracking calls to View.setSystemUiVisibility()
    int mSystemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE;

    DisplayMetrics mDisplayMetrics = new DisplayMetrics();

    // XXX: gesture research
    private final GestureRecorder mGestureRec = DEBUG_GESTURES
        ? new GestureRecorder("/sdcard/statusbar_gestures.dat")
        : null;

    private int mNavigationIconHints = 0;
    private final Animator.AnimatorListener mMakeIconsInvisible = new AnimatorListenerAdapter() {
        @Override
        public void onAnimationEnd(Animator animation) {
            // double-check to avoid races
            if (mStatusBarContents.getAlpha() == 0) {
                if (DEBUG) Log.d(TAG, "makeIconsInvisible");
                mStatusBarContents.setVisibility(View.INVISIBLE);
            }
        }
    };

    // ensure quick settings is disabled until the current user makes it through the setup wizard
    private boolean mUserSetup = false;
    private ContentObserver mUserSetupObserver = new ContentObserver(new Handler()) {
        @Override
        public void onChange(boolean selfChange) {
            final boolean userSetup = 0 != Settings.Secure.getIntForUser(
                    mContext.getContentResolver(),
                    Settings.Secure.USER_SETUP_COMPLETE,
                    0 /*default */,
                    mCurrentUserId);
            if (true) Log.d(TAG, String.format("User setup changed: " +
                    "selfChange=%s userSetup=%s mUserSetup=%s",
                    selfChange, userSetup, mUserSetup));
            if (mSettingsButton != null && mHasFlipSettings) {
                mSettingsButton.setVisibility(userSetup ? View.VISIBLE : View.INVISIBLE);
            }
            if (mSettingsPanel != null) {
                mSettingsPanel.setEnabled(userSetup);
            }
            if (userSetup != mUserSetup) {
                mUserSetup = userSetup;
                if (!mUserSetup && mStatusBarView != null)
                    animateCollapseQuickSettings();
            }
        }
    };

    final private ContentObserver mHeadsUpObserver = new ContentObserver(mHandler) {
        @Override
        public void onChange(boolean selfChange) {
            boolean wasUsing = mUseHeadsUp;
            mUseHeadsUp = ENABLE_HEADS_UP && 0 != Settings.Global.getInt(
                    mContext.getContentResolver(), SETTING_HEADS_UP, 0);
            Log.d(TAG, "heads up is " + (mUseHeadsUp ? "enabled" : "disabled"));
            if (wasUsing != mUseHeadsUp) {
                if (!mUseHeadsUp) {
                    Log.d(TAG, "dismissing any existing heads up notification on disable event");
                    mHandler.sendEmptyMessage(MSG_HIDE_HEADS_UP);
                    removeHeadsUpView();
                } else {
                    addHeadsUpView();
                }
            }
        }
    };

    private int mInteractingWindows;
    private boolean mAutohideSuspended;
    private int mStatusBarMode;
    private int mNavigationBarMode;
    private Boolean mScreenOn;

    private final Runnable mAutohide = new Runnable() {
        @Override
        public void run() {
            int requested = mSystemUiVisibility & ~STATUS_OR_NAV_TRANSIENT;
            if (mSystemUiVisibility != requested) {
                notifyUiVisibilityChanged(requested);
            }
        }};

    @Override
    public void start() {
        mDisplay = ((WindowManager)mContext.getSystemService(Context.WINDOW_SERVICE))
                .getDefaultDisplay();
        updateDisplaySize();

        /// M: Support Smartbook Feature.
        if (SIMHelper.isMediatekSmartBookSupport()) {
            /// M: [ALPS01097705] Query the plug-in state as soon as possible.
            mIsDisplayDevice = SIMHelper.isSmartBookPluggedIn(mContext);
            Log.v(TAG, "start, mIsDisplayDevice=" + mIsDisplayDevice);
        }

        super.start(); // calls createAndAddWindows()

        addNavigationBar();

        // Lastly, call to the icon policy to install/update all the icons.
        mIconPolicy = new PhoneStatusBarPolicy(mContext);

        mHeadsUpObserver.onChange(true); // set up
        if (ENABLE_HEADS_UP) {
            mContext.getContentResolver().registerContentObserver(
                    Settings.Global.getUriFor(SETTING_HEADS_UP), true,
                    mHeadsUpObserver);
        }
    }

    // ================================================================================
    // Constructing the view
    // ================================================================================
    protected PhoneStatusBarView makeStatusBarView() {
        final Context context = mContext;

        Resources res = context.getResources();

        updateDisplaySize(); // populates mDisplayMetrics
        loadDimens();

        mIconSize = res.getDimensionPixelSize(com.android.internal.R.dimen.status_bar_icon_size);

        /// M: Support "Change font size of phone".
        Configuration config = res.getConfiguration();
        mPreviousConfigFontScale = config.fontScale;
        mPrevioutConfigOrientation = config.orientation;

        /// M: Support Smartbook Feature.
        Log.v(TAG, "makeStatusBarView dump config information start ");
        Log.v(TAG, " widthPixels = " + mDisplayMetrics.widthPixels);
        Log.v(TAG, " heightPixels = " + mDisplayMetrics.heightPixels);
        Log.v(TAG, " orientation = " + config.orientation);
        Log.v(TAG, " config = " + config.toString());
        Log.v(TAG, "makeStatusBarView dump config information end ");

        /// M: Support AirplaneMode for Statusbar SimIndicator.
        updateAirplaneMode();
        /// M: [SystemUI] Support "Dual SIM". {
        if (FeatureOption.MTK_GEMINI_SUPPORT) {
            mStatusBarWindow = (StatusBarWindowView)View.inflate(context, R.layout.gemini_super_status_bar, null);
        } else {
            mStatusBarWindow = (StatusBarWindowView) View.inflate(context, R.layout.super_status_bar, null);
        }
        mStatusBarWindow.mService = this;
        mStatusBarWindow.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                checkUserAutohide(v, event);
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    if (mExpandedVisible) {
                        animateCollapsePanels();
                    }
                }
                return mStatusBarWindow.onTouchEvent(event);
            }});

        mStatusBarView = (PhoneStatusBarView) mStatusBarWindow.findViewById(R.id.status_bar);
        mStatusBarView.setBar(this);

        PanelHolder holder = (PanelHolder) mStatusBarWindow.findViewById(R.id.panel_holder);
        mStatusBarView.setPanelHolder(holder);

        mNotificationPanel = (NotificationPanelView) mStatusBarWindow.findViewById(R.id.notification_panel);
        mNotificationPanel.setStatusBar(this);
        mNotificationPanelIsFullScreenWidth =
            (mNotificationPanel.getLayoutParams().width == ViewGroup.LayoutParams.MATCH_PARENT);

        // make the header non-responsive to clicks
        mNotificationPanel.findViewById(R.id.header).setOnTouchListener(
                new View.OnTouchListener() {
                    @Override
                    public boolean onTouch(View v, MotionEvent event) {
                        return true; // e eats everything
                    }
                });
        /// M: [ALPS00352181] When ActivityManager.isHighEndGfx(mDisplay) return true, the dialog
        /// will show error, it will has StatusBar windowBackground.
        mStatusBarWindow.setBackground(null);
        if (!ActivityManager.isHighEndGfx()) {
            ///mStatusBarWindow.setBackground(null);
            mNotificationPanel.setBackground(new FastColorDrawable(context.getResources().getColor(
                    R.color.notification_panel_solid_background)));
        }
        if (ENABLE_HEADS_UP) {
            mHeadsUpNotificationView =
                    (HeadsUpNotificationView) View.inflate(context, R.layout.heads_up, null);
            mHeadsUpNotificationView.setVisibility(View.GONE);
            mHeadsUpNotificationView.setBar(this);
        }
        if (MULTIUSER_DEBUG) {
            mNotificationPanelDebugText = (TextView) mNotificationPanel.findViewById(R.id.header_debug_info);
            mNotificationPanelDebugText.setVisibility(View.VISIBLE);
        }

        updateShowSearchHoldoff();

        try {
            boolean showNav = mWindowManagerService.hasNavigationBar();
            /// M: Support Smartbook Feature.
            if (true) Log.v(TAG, "hasNavigationBar=" + showNav);
            if (showNav) {
                mNavigationBarView =
                    (NavigationBarView) View.inflate(context, R.layout.navigation_bar, null);

                mNavigationBarView.setDisabledFlags(mDisabled);
                mNavigationBarView.setBar(this);
                mNavigationBarView.setOnTouchListener(new View.OnTouchListener() {
                    @Override
                    public boolean onTouch(View v, MotionEvent event) {
                        checkUserAutohide(v, event);
                        return false;
                    }});
            }
        } catch (RemoteException ex) {
            // no window manager? good luck with that
        }

        // figure out which pixel-format to use for the status bar.
        mPixelFormat = PixelFormat.OPAQUE;

        mSystemIconArea = (LinearLayout) mStatusBarView.findViewById(R.id.system_icon_area);
        mStatusIcons = (LinearLayout)mStatusBarView.findViewById(R.id.statusIcons);
        mNotificationIcons = (IconMerger)mStatusBarView.findViewById(R.id.notificationIcons);
        mMoreIcon = mStatusBarView.findViewById(R.id.moreIcon);
        mNotificationIcons.setOverflowIndicator(mMoreIcon);
        mStatusBarContents = (LinearLayout)mStatusBarView.findViewById(R.id.status_bar_contents);
        mTickerView = mStatusBarView.findViewById(R.id.ticker);
        /// M: For AT&T
        if (!FeatureOption.MTK_GEMINI_SUPPORT &&
                !PluginFactory.getStatusBarPlugin(mContext)
                .isHspaDataDistinguishable() &&
                !PluginFactory.getStatusBarPlugin(context)
                .supportDataTypeAlwaysDisplayWhileOn()) {
            mPlmnLabel = (TextView) mStatusBarView.findViewById(R.id.att_plmn);
        }
        /*add calling code here [Akhil]*/
        /*calling the plugin for plmn display for orange*/
        Xlog.d(TAG, "over the call to plugin factory");
        mPlmnDisplay = PluginFactory.getStatusBarPlmnPlugin(mContext).getPlmnTextView(mContext);
        if(mPlmnDisplay != null){
            mPlmnLabel = mPlmnDisplay;
            mPlmnDisplay.setVisibility(View.VISIBLE);
            String str = (String)mPlmnLabel.getText();
            if(str == null)
                Xlog.d(TAG, "No text in framework");
            Xlog.d(TAG, "text of TextView = "+str);
            //mStatusBarView.addView(mPlmnDisplay,1);//Commented here to test
            mStatusBarContents.addView(mPlmnDisplay,0);
            mNotificationIcons.setCarrierText(mPlmnLabel);
        }
        /// M: [SystemUI] Support "Notification toolbar". {
        mToolBarSwitchPanel = mStatusBarWindow.findViewById(R.id.toolBarSwitchPanel);
        mToolBarView = (ToolBarView) mStatusBarWindow.findViewById(R.id.tool_bar_view);
        ToolBarIndicator indicator = (ToolBarIndicator) mStatusBarWindow.findViewById(R.id.indicator);
        mToolBarView.setStatusBarService(this);
        mToolBarView.setToolBarSwitchPanel(mToolBarSwitchPanel);
        mToolBarView.setScrollToScreenCallback(indicator);
        mToolBarView.setToolBarIndicator(indicator);
        mToolBarView.hideSimSwithPanel();
        mToolBarView.moveToDefaultScreen(false);
        /// M: [SystemUI] Support "Notification toolbar". }

        /// M: [SystemUI] Support "SIM indicator". {
        mSimIndicatorIcon = (ImageView) mStatusBarView.findViewById(R.id.sim_indicator_internet_or_alwaysask);
        /// M: [SystemUI] Support "SIM indicator". }

        mPile = (NotificationRowLayout)mStatusBarWindow.findViewById(R.id.latestItems);
        mPile.setLayoutTransitionsEnabled(false);
        mPile.setLongPressListener(getNotificationLongClicker());
        mExpandedContents = mPile; // was: expanded.findViewById(R.id.notificationLinearLayout);

        mNotificationPanelHeader = mStatusBarWindow.findViewById(R.id.header);

        mClearButton = mStatusBarWindow.findViewById(R.id.clear_all_button);
        mClearButton.setOnClickListener(mClearButtonListener);
        mClearButton.setAlpha(0f);
        mClearButton.setVisibility(View.INVISIBLE);
        mClearButton.setEnabled(false);
        mDateView = (DateView)mStatusBarWindow.findViewById(R.id.date);

        mHasSettingsPanel = res.getBoolean(R.bool.config_hasSettingsPanel);
        mHasFlipSettings = res.getBoolean(R.bool.config_hasFlipSettingsPanel);

        mDateTimeView = mNotificationPanelHeader.findViewById(R.id.datetime);
        if (mDateTimeView != null) {
            mDateTimeView.setOnClickListener(mClockClickListener);
            mDateTimeView.setEnabled(true);
        }

        mSettingsButton = (ImageView) mStatusBarWindow.findViewById(R.id.settings_button);
        if (mSettingsButton != null) {
            mSettingsButton.setOnClickListener(mSettingsButtonListener);
            if (mHasSettingsPanel) {
                if (mStatusBarView.hasFullWidthNotifications()) {
                    // the settings panel is hiding behind this button
                    mSettingsButton.setImageResource(R.drawable.ic_notify_quicksettings);
                    mSettingsButton.setVisibility(View.VISIBLE);
                } else {
                    // there is a settings panel, but it's on the other side of the (large) screen
                    final View buttonHolder = mStatusBarWindow.findViewById(
                            R.id.settings_button_holder);
                    if (buttonHolder != null) {
                        buttonHolder.setVisibility(View.GONE);
                    }
                }
            } else {
                // no settings panel, go straight to settings
                mSettingsButton.setVisibility(View.VISIBLE);
                mSettingsButton.setImageResource(R.drawable.ic_notify_settings);
            }
        }
        if (mHasFlipSettings) {
            mNotificationButton = (ImageView) mStatusBarWindow.findViewById(R.id.notification_button);
            if (mNotificationButton != null) {
                mNotificationButton.setOnClickListener(mNotificationButtonListener);
            }
        }

        mScrollView = (ScrollView)mStatusBarWindow.findViewById(R.id.scroll);
        mScrollView.setVerticalScrollBarEnabled(false); // less drawing during pulldowns
        if (!mNotificationPanelIsFullScreenWidth) {
            mScrollView.setSystemUiVisibility(
                    View.STATUS_BAR_DISABLE_NOTIFICATION_TICKER |
                    View.STATUS_BAR_DISABLE_NOTIFICATION_ICONS |
                    View.STATUS_BAR_DISABLE_CLOCK);
        }

        mTicker = new MyTicker(context, mStatusBarView);

        TickerView tickerView = (TickerView)mStatusBarView.findViewById(R.id.tickerText);
        tickerView.mTicker = mTicker;

        mEdgeBorder = res.getDimensionPixelSize(R.dimen.status_bar_edge_ignore);

        // set the inital view visibility
        setAreThereNotifications();

        // Other icons
        mLocationController = new LocationController(mContext); // will post a notification
        mBatteryController = new BatteryController(mContext);
        mNetworkController = new NetworkController(mContext);
        mBluetoothController = new BluetoothController(mContext);
        mRotationLockController = new RotationLockController(mContext);
        final SignalClusterView signalCluster =
                (SignalClusterView)mStatusBarView.findViewById(R.id.signal_cluster);


        mNetworkController.addSignalCluster(signalCluster);
        signalCluster.setNetworkController(mNetworkController);
        /// M: Support Battery Percentage
        mBatteryController.addLabelView((TextView) mStatusBarWindow.findViewById(R.id.percentage));
        /// M: Support Laptop Battery Status
        if (SIMHelper.isMediatekSmartBookSupport()) {
            mBatteryController.addIconView((LaptopBatteryView)mStatusBarView.findViewById(R.id.laptop_battery));
        }
        /// M: [SystemUI] Support "Dual SIM". {
        if (FeatureOption.MTK_GEMINI_SUPPORT) {
            /// M: Support GeminiPlus
            mCarrier1 = (CarrierLabel) mStatusBarWindow.findViewById(R.id.carrier1);
            mCarrier2 = (CarrierLabel) mStatusBarWindow.findViewById(R.id.carrier2);
            mCarrier3 = (CarrierLabel) mStatusBarWindow.findViewById(R.id.carrier3);
            mCarrier4 = (CarrierLabel) mStatusBarWindow.findViewById(R.id.carrier4);
            mCarrierDivider = mStatusBarWindow.findViewById(R.id.carrier_divider);
            mCarrierDivider2 = mStatusBarWindow.findViewById(R.id.carrier_divider2);
            mCarrierDivider3 = mStatusBarWindow.findViewById(R.id.carrier_divider3);
            mCarrierLabelGemini = (LinearLayout) mStatusBarWindow.findViewById(R.id.carrier_label_gemini);
            mShowCarrierInPanel = (mCarrierLabelGemini != null);
            if (mShowCarrierInPanel) {
                mCarrier1.setSlotId(PhoneConstants.GEMINI_SIM_1);
                mCarrier2.setSlotId(PhoneConstants.GEMINI_SIM_2);
                mCarrier3.setSlotId(PhoneConstants.GEMINI_SIM_3);
                mCarrier4.setSlotId(PhoneConstants.GEMINI_SIM_4);
            }
            if(PhoneConstants.GEMINI_SIM_NUM == 2) {
                mNetworkController.setCarrierGemini(mCarrier1, mCarrier2, mCarrierDivider);
            } else if(PhoneConstants.GEMINI_SIM_NUM == 3) {
                mNetworkController.setCarrierGemini(mCarrier1, mCarrier2, mCarrier3, mCarrierDivider, mCarrierDivider2);
            } else if(PhoneConstants.GEMINI_SIM_NUM == 4) {
                mNetworkController.setCarrierGemini(mCarrier1, mCarrier2, mCarrier3, mCarrier4, mCarrierDivider, mCarrierDivider2, mCarrierDivider3);
            }
            Log.v(TAG, "carrierlabel for Gemini=" + mCarrierLabelGemini + " show=" + mShowCarrierInPanel);
            if (mShowCarrierInPanel) {
                mCarrierLabelGemini.setVisibility(mCarrierLabelVisible ? View.VISIBLE : View.INVISIBLE);
                mCarrier2.setVisibility(View.GONE);
                mCarrierDivider.setVisibility(View.GONE);
            }
        } else {
            mCarrierLabel = (TextView)mStatusBarWindow.findViewById(R.id.carrier_label);
            mShowCarrierInPanel = (mCarrierLabel != null);
            if (DEBUG) Log.v(TAG, "carrierlabel=" + mCarrierLabel + " show=" + mShowCarrierInPanel);
            if (mShowCarrierInPanel) {
                mCarrierLabel.setVisibility(mCarrierLabelVisible ? View.VISIBLE : View.INVISIBLE);
            }
            final boolean isAPhone = mNetworkController.hasVoiceCallingFeature(PhoneConstants.GEMINI_SIM_1);
            if (isAPhone) {
                mEmergencyCallLabel =
                        (TextView) mStatusBarWindow.findViewById(R.id.emergency_calls_only);
                if (mEmergencyCallLabel != null) {
                    mNetworkController.addEmergencyLabelView(mEmergencyCallLabel);
                    mEmergencyCallLabel.setOnClickListener(new View.OnClickListener() {
                        public void onClick(View v) { }});
                    mEmergencyCallLabel.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
                        @Override
                        public void onLayoutChange(View v, int left, int top, int right, int bottom,
                                int oldLeft, int oldTop, int oldRight, int oldBottom) {
                            updateCarrierLabelVisibility(false);
                        }});
                }
            }
            // for mobile devices, we always show mobile connection info here (SPN/PLMN)
            // for other devices, we show whatever network is connected
            if (!mNetworkController.hasMobileDataFeature()) {
                mNetworkController.addCombinedLabelView(mCarrierLabel);
            }
        }

        if (mShowCarrierInPanel) {
            if (SIMHelper.isMediatekLteDcSupport()) {
                LteDcController.getInstance(context).registerCallback(
                    new LtdDcStateChangeCallback() {
                    @Override
                    public void onNetworkNameChanged(Intent intent) {
                        Log.d(TAG, "LteDcController onNetworkNameChanged");
                        refreshNetworkNameViews(intent);
                    }
                    @Override
                    public void onServiceStateChanged(ServiceState serviceState) {
                        boolean hasService = SIMHelper.hasService(serviceState);
                        int slotId = serviceState.getMySimId();
                        Log.d(TAG, "LteDcController onServiceStateChanged hasService = " + hasService);
                        if (!hasService) {
                            Intent intent = 
                                (Intent)LteDcController.getInstance(mContext).getNetworkNameTag(slotId);
                            if (intent != null) {
                                refreshNetworkNameViews(intent);
                            }
                        }
                    }
                    private void refreshNetworkNameViews (Intent intent) {
                        if (!mShowCarrierInPanel) return;
                        Log.d(TAG, "LteDcController refreshNetworkNameViews");
                        final int tempSlotId = 
                            intent.getIntExtra(PhoneConstants.GEMINI_SIM_ID_KEY, PhoneConstants.GEMINI_SIM_1);
                        if (FeatureOption.MTK_GEMINI_SUPPORT) {
                            for (int childIdx = 0; childIdx < mCarrierLabelGemini.getChildCount(); childIdx++) {
                                final View mChildView = mCarrierLabelGemini.getChildAt(childIdx);
                                if(mChildView instanceof CarrierLabel) {
                                    CarrierLabel mChildCarrier = (CarrierLabel) mChildView;
                                    if (tempSlotId == mChildCarrier.getSlotId()) {
                                        mChildCarrier.setText(SIMHelper.getNetworkName(context, intent));
                                    }
                                }
                            }
                        } else {
                            mCarrierLabel.setText(SIMHelper.getNetworkName(context, intent));
                        }
                    }
                });
            }

            // set up the dynamic hide/show of the label
            mPile.setOnSizeChangedListener(new OnSizeChangedListener() {
                @Override
                public void onSizeChanged(View view, int w, int h, int oldw, int oldh) {
                    updateCarrierLabelVisibility(false);
                }
            });
        }

        // Quick Settings (where available, some restrictions apply)
        if (mHasSettingsPanel) {
            // first, figure out where quick settings should be inflated
            final View settings_stub;
            if (mHasFlipSettings) {
                // a version of quick settings that flips around behind the notifications
                settings_stub = mStatusBarWindow.findViewById(R.id.flip_settings_stub);
                if (settings_stub != null) {
                    mFlipSettingsView = ((ViewStub)settings_stub).inflate();
                    mFlipSettingsView.setVisibility(View.GONE);
                    mFlipSettingsView.setVerticalScrollBarEnabled(false);
                }
            } else {
                // full quick settings panel
                settings_stub = mStatusBarWindow.findViewById(R.id.quick_settings_stub);
                if (settings_stub != null) {
                    mSettingsPanel = (SettingsPanelView) ((ViewStub)settings_stub).inflate();
                } else {
                    mSettingsPanel = (SettingsPanelView) mStatusBarWindow.findViewById(R.id.settings_panel);
                }

                if (mSettingsPanel != null) {
                    if (!ActivityManager.isHighEndGfx()) {
                        mSettingsPanel.setBackground(new FastColorDrawable(context.getResources().getColor(
                                R.color.notification_panel_solid_background)));
                    }
                }
            }

            // wherever you find it, Quick Settings needs a container to survive
            mSettingsContainer = (QuickSettingsContainerView)
                    mStatusBarWindow.findViewById(R.id.quick_settings_container);
            if (mSettingsContainer != null) {
                mQS = new QuickSettings(mContext, mSettingsContainer);
                if (!mNotificationPanelIsFullScreenWidth) {
                    mSettingsContainer.setSystemUiVisibility(
                            View.STATUS_BAR_DISABLE_NOTIFICATION_TICKER
                            | View.STATUS_BAR_DISABLE_SYSTEM_INFO);
                }
                if (mSettingsPanel != null) {
                    mSettingsPanel.setQuickSettings(mQS);
                }
                mQS.setService(this);
                mQS.setBar(mStatusBarView);
                mQS.setup(mNetworkController, mBluetoothController, mBatteryController,
                        mLocationController, mRotationLockController);
            } else {
                mQS = null; // fly away, be free
            }
        }

        PowerManager pm = (PowerManager) mContext.getSystemService(Context.POWER_SERVICE);
        mBroadcastReceiver.onReceive(mContext,
                new Intent(pm.isScreenOn() ? Intent.ACTION_SCREEN_ON : Intent.ACTION_SCREEN_OFF));

        // receive broadcasts
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_CLOSE_SYSTEM_DIALOGS);
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        filter.addAction(Intent.ACTION_SCREEN_ON);
        filter.addAction(ACTION_DEMO);
        context.registerReceiver(mBroadcastReceiver, filter);

        /// M: Support Smartbook Feature.
        if (SIMHelper.isMediatekSmartBookSupport()) {
            IntentFilter mFilter = new IntentFilter(Intent.ACTION_SMARTBOOK_PLUG);
            context.registerReceiver(mDisplayDevicePluginReceiver, mFilter);
            Log.v(TAG, "makeStatusBarView, isSmartBookPluggedIn=" 
                + SIMHelper.isSmartBookPluggedIn(context));
        }

        // listen for USER_SETUP_COMPLETE setting (per-user)
        resetUserSetupObserver();
        /// M: [SystemUI] Support "Dual SIM". {
        IntentFilter simInfoIntentFilter = new IntentFilter();
        simInfoIntentFilter.addAction(Intent.SIM_SETTINGS_INFO_CHANGED);
        simInfoIntentFilter.addAction(TelephonyIntents.ACTION_SIM_INSERTED_STATUS);
        simInfoIntentFilter.addAction(TelephonyIntents.ACTION_SIM_INFO_UPDATE);
        simInfoIntentFilter.addAction(Intent.ACTION_AIRPLANE_MODE_CHANGED);
        /// M: [ALPS01234409][KK] Update sim indicator if default sim chagned.
        simInfoIntentFilter.addAction(Intent.ACTION_VOICE_CALL_DEFAULT_SIM_CHANGED);
        simInfoIntentFilter.addAction(Intent.ACTION_SMS_DEFAULT_SIM_CHANGED);
        simInfoIntentFilter.addAction(ACTION_BOOT_IPO);
        /// M: ALPS00349274 to hide navigation bar when ipo shut down to avoid it flash when in boot ipo mode.{
        simInfoIntentFilter.addAction("android.intent.action.ACTION_SHUTDOWN_IPO");
        simInfoIntentFilter.addAction("android.intent.action.ACTION_BOOT_IPO");
        /// M: ALPS00349274 to hide navigation bar when ipo shut down to avoid it flash when in boot ipo mode.}
        /// M: Support "Dual SIM PLMN".
        simInfoIntentFilter.addAction(Telephony.Intents.SPN_STRINGS_UPDATED_ACTION);
        if (!FeatureOption.MTK_GEMINI_SUPPORT && /// M: for AT&T
                !PluginFactory.getStatusBarPlugin(mContext)
                .isHspaDataDistinguishable() &&
                !PluginFactory.getStatusBarPlugin(context)
                .supportDataTypeAlwaysDisplayWhileOn()) {
            simInfoIntentFilter.addAction(TelephonyIntents.ACTION_SIM_INDICATOR_STATE_CHANGED);
        }
        context.registerReceiver(mSIMInfoReceiver, simInfoIntentFilter);
        /// M: [SystemUI] Support "Dual SIM". }

        /// M: [ALPS00512845] Handle SD Swap Condition.
        mNeedRemoveKeys = new ArrayList<IBinder>();
        if (SUPPORT_SD_SWAP) {
            IntentFilter mediaEjectFilter = new IntentFilter();
            mediaEjectFilter.addAction(Intent.ACTION_MEDIA_EJECT);
            mediaEjectFilter.addAction(Intent.ACTION_MEDIA_MOUNTED);
            mediaEjectFilter.addDataScheme("file");
            context.registerReceiver(mMediaEjectBroadcastReceiver, mediaEjectFilter);
        }

        return mStatusBarView;
    }

    @Override
    protected void onShowSearchPanel() {
        if (mNavigationBarView != null) {
            mNavigationBarView.getBarTransitions().setContentVisible(false);
        }
    }

    @Override
    protected void onHideSearchPanel() {
        if (mNavigationBarView != null) {
            mNavigationBarView.getBarTransitions().setContentVisible(true);
        }
    }

    @Override
    protected View getStatusBarView() {
        return mStatusBarView;
    }

    @Override
    protected WindowManager.LayoutParams getSearchLayoutParams(LayoutParams layoutParams) {
        boolean opaque = false;
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_NAVIGATION_BAR_PANEL,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                | WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM
                | WindowManager.LayoutParams.FLAG_SPLIT_TOUCH,
                (opaque ? PixelFormat.OPAQUE : PixelFormat.TRANSLUCENT));
        if (ActivityManager.isHighEndGfx()) {
            lp.flags |= WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED;
        }
        lp.gravity = Gravity.BOTTOM | Gravity.START;
        lp.setTitle("SearchPanel");
        // TODO: Define custom animation for Search panel
        lp.windowAnimations = com.android.internal.R.style.Animation_RecentApplications;
        lp.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_STATE_UNCHANGED
        | WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING;
        return lp;
    }

    @Override
    protected void updateSearchPanel() {
        super.updateSearchPanel();
        if (mNavigationBarView != null) {
            mNavigationBarView.setDelegateView(mSearchPanelView);
        }
    }

    @Override
    public void showSearchPanel() {
        super.showSearchPanel();
        mHandler.removeCallbacks(mShowSearchPanel);

        // we want to freeze the sysui state wherever it is
        mSearchPanelView.setSystemUiVisibility(mSystemUiVisibility);

        if (mNavigationBarView != null) {
            WindowManager.LayoutParams lp =
                (android.view.WindowManager.LayoutParams) mNavigationBarView.getLayoutParams();
            lp.flags &= ~WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL;
            mWindowManager.updateViewLayout(mNavigationBarView, lp);
        }
    }

    @Override
    public void hideSearchPanel() {
        super.hideSearchPanel();
        if (mNavigationBarView != null) {
            WindowManager.LayoutParams lp =
                (android.view.WindowManager.LayoutParams) mNavigationBarView.getLayoutParams();
            lp.flags |= WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL;
            mWindowManager.updateViewLayout(mNavigationBarView, lp);
        }
    }

    protected int getStatusBarGravity() {
        return Gravity.TOP | Gravity.FILL_HORIZONTAL;
    }

    public int getStatusBarHeight() {
        if (mNaturalBarHeight < 0) {
            final Resources res = mContext.getResources();
            mNaturalBarHeight =
                    res.getDimensionPixelSize(com.android.internal.R.dimen.status_bar_height);
        }
        return mNaturalBarHeight;
    }

    private View.OnClickListener mRecentsClickListener = new View.OnClickListener() {
        public void onClick(View v) {
            awakenDreams();
            toggleRecentApps();
        }
    };

    private int mShowSearchHoldoff = 0;
    private Runnable mShowSearchPanel = new Runnable() {
        public void run() {
            showSearchPanel();
            awakenDreams();
        }
    };

    View.OnTouchListener mHomeSearchActionListener = new View.OnTouchListener() {
        public boolean onTouch(View v, MotionEvent event) {
            switch(event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                if (!shouldDisableNavbarGestures()) {
                    mHandler.removeCallbacks(mShowSearchPanel);
                    mHandler.postDelayed(mShowSearchPanel, mShowSearchHoldoff);
                }
            break;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                mHandler.removeCallbacks(mShowSearchPanel);
                awakenDreams();
            break;
        }
        return false;
        }
    };

    private void awakenDreams() {
        if (mDreamManager != null) {
            try {
                mDreamManager.awaken();
            } catch (RemoteException e) {
                // fine, stay asleep then
            }
        }
    }

    private void prepareNavigationBarView() {
        mNavigationBarView.reorient();

        mNavigationBarView.getRecentsButton().setOnClickListener(mRecentsClickListener);
        mNavigationBarView.getRecentsButton().setOnTouchListener(mRecentsPreloadOnTouchListener);
        mNavigationBarView.getHomeButton().setOnTouchListener(mHomeSearchActionListener);
        mNavigationBarView.getSearchLight().setOnTouchListener(mHomeSearchActionListener);
        updateSearchPanel();
    }

    // For small-screen devices (read: phones) that lack hardware navigation buttons
    private void addNavigationBar() {
        if (DEBUG) Log.v(TAG, "addNavigationBar: about to add " + mNavigationBarView);
        if (mNavigationBarView == null) return;

        prepareNavigationBarView();

        /// M: [SystemUI] For SystemUI AT.
        if (AutoTestHelper.isNotRunningInTest()) {
            mWindowManager.addView(mNavigationBarView, getNavigationBarLayoutParams());
        }
    }

    private void repositionNavigationBar() {
        if (mNavigationBarView == null || !mNavigationBarView.isAttachedToWindow()) return;

        prepareNavigationBarView();

        mWindowManager.updateViewLayout(mNavigationBarView, getNavigationBarLayoutParams());
    }

    private void notifyNavigationBarScreenOn(boolean screenOn) {
        if (mNavigationBarView == null) return;
        mNavigationBarView.notifyScreenOn(screenOn);
    }

    private WindowManager.LayoutParams getNavigationBarLayoutParams() {
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_NAVIGATION_BAR,
                    0
                    | WindowManager.LayoutParams.FLAG_TOUCHABLE_WHEN_WAKING
                    | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                    | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                    | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
                    | WindowManager.LayoutParams.FLAG_SPLIT_TOUCH,
                PixelFormat.TRANSLUCENT);
        // this will allow the navbar to run in an overlay on devices that support this
        if (ActivityManager.isHighEndGfx()) {
            lp.flags |= WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED;
        }

        lp.setTitle("NavigationBar");
        lp.windowAnimations = 0;
        return lp;
    }

    private void addHeadsUpView() {
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_STATUS_BAR_PANEL, // above the status bar!
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                    | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                    | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                    | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                    | WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM
                    | WindowManager.LayoutParams.FLAG_SPLIT_TOUCH,
                PixelFormat.TRANSLUCENT);
        lp.flags |= WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED;
        lp.gravity = Gravity.TOP;
        lp.y = getStatusBarHeight();
        lp.setTitle("Heads Up");
        lp.packageName = mContext.getPackageName();
        lp.windowAnimations = R.style.Animation_StatusBar_HeadsUp;

        mWindowManager.addView(mHeadsUpNotificationView, lp);
    }

    private void removeHeadsUpView() {
        mWindowManager.removeView(mHeadsUpNotificationView);
    }

    public void refreshAllStatusBarIcons() {
        refreshAllIconsForLayout(mStatusIcons);
        refreshAllIconsForLayout(mNotificationIcons);
    }

    private void refreshAllIconsForLayout(LinearLayout ll) {
        final int count = ll.getChildCount();
        for (int n = 0; n < count; n++) {
            View child = ll.getChildAt(n);
            if (child instanceof StatusBarIconView) {
                ((StatusBarIconView) child).updateDrawable();
            }
        }
    }

    public void addIcon(String slot, int index, int viewIndex, StatusBarIcon icon) {
        if (SPEW) Log.d(TAG, "addIcon slot=" + slot + " index=" + index + " viewIndex=" + viewIndex
                + " icon=" + icon);
        StatusBarIconView view = new StatusBarIconView(mContext, slot, null);
        view.set(icon);
        mStatusIcons.addView(view, viewIndex, new LinearLayout.LayoutParams(mIconSize, mIconSize));
    }

    public void updateIcon(String slot, int index, int viewIndex,
            StatusBarIcon old, StatusBarIcon icon) {
        if (SPEW) Log.d(TAG, "updateIcon slot=" + slot + " index=" + index + " viewIndex=" + viewIndex
                + " old=" + old + " icon=" + icon);
        StatusBarIconView view = (StatusBarIconView)mStatusIcons.getChildAt(viewIndex);
        view.set(icon);
    }

    public void removeIcon(String slot, int index, int viewIndex) {
        if (SPEW) Log.d(TAG, "removeIcon slot=" + slot + " index=" + index + " viewIndex=" + viewIndex);
        mStatusIcons.removeViewAt(viewIndex);
    }

    public void addNotification(IBinder key, StatusBarNotification notification) {
        if (DEBUG) Log.d(TAG, "addNotification score=" + notification.getScore());
        Entry shadeEntry = createNotificationViews(key, notification);
        if (shadeEntry == null) {
            return;
        }
        /// M: [ALPS00512845] Handle SD Swap Condition.
        if (SUPPORT_SD_SWAP) {
            try {
                ApplicationInfo applicationInfo = mContext.getPackageManager().getApplicationInfo(notification.getPackageName(), 0);
                if ((applicationInfo.flags & applicationInfo.FLAG_EXTERNAL_STORAGE) != 0) {
                    if (mAvoidSDAppAddNotification) {
                        return;
                    }
                    if (!mNeedRemoveKeys.contains(key)) {
                        mNeedRemoveKeys.add(key);
                    }
                    Log.d(TAG, "addNotification, applicationInfo pkg = " + notification.getPackageName() + " to remove notification key = " + key);
                }
            } catch (NameNotFoundException e1) {
                e1.printStackTrace();
            }
        }
        if (mUseHeadsUp && shouldInterrupt(notification)) {
            if (DEBUG) Log.d(TAG, "launching notification in heads up mode");
            Entry interruptionCandidate = new Entry(key, notification, null);
            if (inflateViews(interruptionCandidate, mHeadsUpNotificationView.getHolder())) {
                mInterruptingNotificationTime = System.currentTimeMillis();
                mInterruptingNotificationEntry = interruptionCandidate;
                shadeEntry.setInterruption();

                // 1. Populate mHeadsUpNotificationView
                mHeadsUpNotificationView.setNotification(mInterruptingNotificationEntry);

                // 2. Animate mHeadsUpNotificationView in
                mHandler.sendEmptyMessage(MSG_SHOW_HEADS_UP);

                // 3. Set alarm to age the notification off
                resetHeadsUpDecayTimer();
            }
        } else if (notification.getNotification().fullScreenIntent != null) {
            // Stop screensaver if the notification has a full-screen intent.
            // (like an incoming phone call)
            awakenDreams();

            // not immersive & a full-screen alert should be shown
            if (DEBUG) Log.d(TAG, "Notification has fullScreenIntent; sending fullScreenIntent");
            try {
                notification.getNotification().fullScreenIntent.send();
            } catch (PendingIntent.CanceledException e) {
            }
        } else {
            // usual case: status bar visible & not immersive

            // show the ticker if there isn't already a heads up
            if (mInterruptingNotificationEntry == null) {
                tick(null, notification, true);
            }
        }
        addNotificationViews(shadeEntry);

        // Recalculate the position of the sliding windows and the titles.
        setAreThereNotifications();
        updateExpandedViewPos(EXPANDED_LEAVE_ALONE);
    }

    @Override
    public void resetHeadsUpDecayTimer() {
        if (mUseHeadsUp && mHeadsUpNotificationDecay > 0
                && mHeadsUpNotificationView.isClearable()) {
            mHandler.removeMessages(MSG_HIDE_HEADS_UP);
            mHandler.sendEmptyMessageDelayed(MSG_HIDE_HEADS_UP, mHeadsUpNotificationDecay);
        }
    }

    public void removeNotification(IBinder key) {
        StatusBarNotification old = removeNotificationViews(key);
        if (SPEW) Log.d(TAG, "removeNotification key=" + key + " old=" + old);

        /// M: [ALPS00512845] Handle SD Swap Condition.
        if (SUPPORT_SD_SWAP) {
            if (mNeedRemoveKeys.contains(key)) {
                mNeedRemoveKeys.remove(key);
            }
        }

        if (old != null) {
            // Cancel the ticker if it's still running
            mTicker.removeEntry(old);

            // Recalculate the position of the sliding windows and the titles.
            updateExpandedViewPos(EXPANDED_LEAVE_ALONE);

            if (ENABLE_HEADS_UP && mInterruptingNotificationEntry != null
                    && old == mInterruptingNotificationEntry.notification) {
                mHandler.sendEmptyMessage(MSG_HIDE_HEADS_UP);
            }

            if (CLOSE_PANEL_WHEN_EMPTIED && mNotificationData.size() == 0
                    && !mNotificationPanel.isTracking()) {
                animateCollapsePanels();
            }
        }

        setAreThereNotifications();
    }

    @Override
    protected void refreshLayout(int layoutDirection) {
        if (mNavigationBarView != null) {
            mNavigationBarView.setLayoutDirection(layoutDirection);
        }

        if (mClearButton != null && mClearButton instanceof ImageView) {
            // Force asset reloading
            ((ImageView)mClearButton).setImageDrawable(null);
            ((ImageView)mClearButton).setImageResource(R.drawable.ic_notify_clear);
        }

        if (mSettingsButton != null) {
            // Force asset reloading
            mSettingsButton.setImageDrawable(null);
            mSettingsButton.setImageResource(R.drawable.ic_notify_quicksettings);
        }

        if (mNotificationButton != null) {
            // Force asset reloading
            mNotificationButton.setImageDrawable(null);
            mNotificationButton.setImageResource(R.drawable.ic_notifications);
        }

        refreshAllStatusBarIcons();

        /// M: Notification UI Support RTL.
        refreshExpandedView(mContext);
    }

    private void updateShowSearchHoldoff() {
        mShowSearchHoldoff = mContext.getResources().getInteger(
            R.integer.config_show_search_delay);
    }

    private void loadNotificationShade() {
        if (mPile == null) return;

        int N = mNotificationData.size();

        ArrayList<View> toShow = new ArrayList<View>();

        final boolean provisioned = isDeviceProvisioned();
        // If the device hasn't been through Setup, we only show system notifications
        for (int i=0; i<N; i++) {
            Entry ent = mNotificationData.get(N-i-1);
            if (!(provisioned || showNotificationEvenIfUnprovisioned(ent.notification))) continue;
            if (!notificationIsForCurrentUser(ent.notification)) continue;
            toShow.add(ent.row);
        }

        ArrayList<View> toRemove = new ArrayList<View>();
        for (int i=0; i<mPile.getChildCount(); i++) {
            View child = mPile.getChildAt(i);
            if (!toShow.contains(child)) {
                toRemove.add(child);
            }
        }

        for (View remove : toRemove) {
            mPile.removeView(remove);
        }

        for (int i=0; i<toShow.size(); i++) {
            View v = toShow.get(i);
            if (v.getParent() == null) {
                mPile.addView(v, i);
            }
        }

        if (mSettingsButton != null) {
            mSettingsButton.setEnabled(isDeviceProvisioned());
        }
    }

    @Override
    protected void updateNotificationIcons() {
        if (mNotificationIcons == null) return;

        loadNotificationShade();

        final LinearLayout.LayoutParams params
            = new LinearLayout.LayoutParams(mIconSize + 2*mIconHPadding, mNaturalBarHeight);

        int N = mNotificationData.size();

        if (DEBUG) {
            Log.d(TAG, "refreshing icons: " + N + " notifications, mNotificationIcons=" + mNotificationIcons);
        }

        ArrayList<View> toShow = new ArrayList<View>();
        // M: StatusBar IconMerger feature, hash{pkg+icon}=iconlevel
        HashMap<String, Integer> uniqueIcon = new HashMap<String, Integer>();

        final boolean provisioned = isDeviceProvisioned();
        // If the device hasn't been through Setup, we only show system notifications
        for (int i=0; i<N; i++) {
            Entry ent = mNotificationData.get(N-i-1);
            if (!((provisioned && ent.notification.getScore() >= HIDE_ICONS_BELOW_SCORE)
                    || showNotificationEvenIfUnprovisioned(ent.notification))) continue;
            if (!notificationIsForCurrentUser(ent.notification)) continue;

            // M: StatusBar IconMerger feature
            String key = ent.notification.getPackageName() + String.valueOf(ent.notification.getNotification().icon);
            if (uniqueIcon.containsKey(key) && uniqueIcon.get(key) == ent.notification.getNotification().iconLevel) {
                Xlog.d(TAG, "updateNotificationIcons(), IconMerger feature, skip pkg / icon / iconlevel ="
                    + ent.notification.getPackageName() + "/" + ent.notification.getNotification().icon + "/" + ent.notification.getNotification().iconLevel);
                continue;
            }

            toShow.add(ent.icon);
            uniqueIcon.put(key, ent.notification.getNotification().iconLevel);
        }
        uniqueIcon = null;

        ArrayList<View> toRemove = new ArrayList<View>();
        for (int i=0; i<mNotificationIcons.getChildCount(); i++) {
            View child = mNotificationIcons.getChildAt(i);
            if (!toShow.contains(child)) {
                toRemove.add(child);
            }
        }

        for (View remove : toRemove) {
            mNotificationIcons.removeView(remove);
        }

        for (int i=0; i<toShow.size(); i++) {
            View v = toShow.get(i);
            if (v.getParent() == null) {
                mNotificationIcons.addView(v, i, params);
            }
        }
    }

    protected void updateCarrierLabelVisibility(boolean force) {
        if (!mShowCarrierInPanel) return;
        // The idea here is to only show the carrier label when there is enough room to see it,
        // i.e. when there aren't enough notifications to fill the panel.
        if (SPEW) {
            Log.d(TAG, String.format("pileh=%d scrollh=%d carrierh=%d",
                    mPile.getHeight(), mScrollView.getHeight(), mCarrierLabelHeight));
        }

        final boolean emergencyCallsShownElsewhere = mEmergencyCallLabel != null;
        boolean makeVisible = false;
        /// M: Calculate ToolBar height when sim indicator is showing.
        /// M: Fix [ALPS00455548] Use getExpandedHeight instead of getHeight to avoid race condition.
        int height = mToolBarSwitchPanel.getVisibility() == View.VISIBLE ?
                ((int)mNotificationPanel.getExpandedHeight() - mCarrierLabelHeight - mNotificationHeaderHeight - mToolBarViewHeight)
                : ((int)mNotificationPanel.getExpandedHeight() - mCarrierLabelHeight - mNotificationHeaderHeight);
        /// M: Support "Dual Sim" @{
        if (FeatureOption.MTK_GEMINI_SUPPORT) {
            makeVisible =
                mPile.getHeight() < height && mScrollView.getVisibility() == View.VISIBLE;
        } else {
            makeVisible =
                !(emergencyCallsShownElsewhere && mNetworkController.isEmergencyOnly())
                && mPile.getHeight() < height && mScrollView.getVisibility() == View.VISIBLE;
        }

        if (force || mCarrierLabelVisible != makeVisible) {
            mCarrierLabelVisible = makeVisible;
            if (DEBUG) {
                Log.d(TAG, "making carrier label " + (makeVisible?"visible":"invisible"));
            }
            /// M: Support "Dual Sim" @{
            if (FeatureOption.MTK_GEMINI_SUPPORT) {
                mCarrierLabelGemini.animate().cancel();
                if (makeVisible) {
                    mCarrierLabelGemini.setVisibility(View.VISIBLE);
                }
                mCarrierLabelGemini.animate()
                    .alpha(makeVisible ? 1f : 0f)
                    .setDuration(150)
                    .setListener(makeVisible ? null : new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            if (!mCarrierLabelVisible) { // race
                                mCarrierLabelGemini.setVisibility(View.INVISIBLE);
                                mCarrierLabelGemini.setAlpha(0f);
                            }
                        }
                    })
                    .start();
            /// M: Support "Dual Sim" @{
            } else {
                mCarrierLabel.animate().cancel();
                if (makeVisible) {
                    mCarrierLabel.setVisibility(View.VISIBLE);
                }
                mCarrierLabel.animate()
                    .alpha(makeVisible ? 1f : 0f)
                    //.setStartDelay(makeVisible ? 500 : 0)
                    //.setDuration(makeVisible ? 750 : 100)
                    .setDuration(150)
                    .setListener(makeVisible ? null : new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            if (!mCarrierLabelVisible) { // race
                                mCarrierLabel.setVisibility(View.INVISIBLE);
                                mCarrierLabel.setAlpha(0f);
                            }
                        }
                    })
                    .start();
            }
        }
    }

    @Override
    protected void setAreThereNotifications() {
        /// M: Check mPile size for visible layout, ex: Unprovisioned mode
        final boolean any = (mNotificationData.size() > 0) 
                        && ((mPile != null) && (mPile.getChildCount() > 0));

        final boolean clearable = any && mNotificationData.hasClearableItems();

        if (SPEW) {
            Log.d(TAG, "setAreThereNotifications: N=" + mNotificationData.size()
                    + " any=" + any + " clearable=" + clearable);
        }

        if (mHasFlipSettings
                && mFlipSettingsView != null
                && mFlipSettingsView.getVisibility() == View.VISIBLE
                && mScrollView.getVisibility() != View.VISIBLE) {
            // the flip settings panel is unequivocally showing; we should not be shown
            mClearButton.setVisibility(View.INVISIBLE);
        } else if (mClearButton.isShown()) {
            if (clearable != (mClearButton.getAlpha() == 1.0f)) {
                ObjectAnimator clearAnimation = ObjectAnimator.ofFloat(
                        mClearButton, "alpha", clearable ? 1.0f : 0.0f).setDuration(250);
                clearAnimation.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        if (mClearButton.getAlpha() <= 0.0f) {
                            mClearButton.setVisibility(View.INVISIBLE);
                        }
                    }

                    @Override
                    public void onAnimationStart(Animator animation) {
                        if (mClearButton.getAlpha() <= 0.0f) {
                            mClearButton.setVisibility(View.VISIBLE);
                        }
                    }
                });
                clearAnimation.start();
            }
        } else {
            mClearButton.setAlpha(clearable ? 1.0f : 0.0f);
            mClearButton.setVisibility(clearable ? View.VISIBLE : View.INVISIBLE);
        }
        mClearButton.setEnabled(clearable);

        final View nlo = mStatusBarView.findViewById(R.id.notification_lights_out);
        final boolean showDot = (any&&!areLightsOn());
        if (showDot != (nlo.getAlpha() == 1.0f)) {
            if (showDot) {
                nlo.setAlpha(0f);
                nlo.setVisibility(View.VISIBLE);
            }
            nlo.animate()
                .alpha(showDot?1:0)
                .setDuration(showDot?750:250)
                .setInterpolator(new AccelerateInterpolator(2.0f))
                .setListener(showDot ? null : new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator _a) {
                        nlo.setVisibility(View.GONE);
                    }
                })
                .start();
        }

        updateCarrierLabelVisibility(false);
    }

    public void showClock(boolean show) {
        if (mStatusBarView == null) return;
        View clock = mStatusBarView.findViewById(R.id.clock);
        if (clock != null) {
            clock.setVisibility(show ? View.VISIBLE : View.GONE);
        }
    }

    /**
     * State is one or more of the DISABLE constants from StatusBarManager.
     */
    public void disable(int state) {
        final int old = mDisabled;
        final int diff = state ^ old;
        mDisabled = state;

        if (DEBUG) {
            Log.d(TAG, String.format("disable: 0x%08x -> 0x%08x (diff: 0x%08x)",
                old, state, diff));
        }

        StringBuilder flagdbg = new StringBuilder();
        flagdbg.append("disable: < ");
        flagdbg.append(((state & StatusBarManager.DISABLE_EXPAND) != 0) ? "EXPAND" : "expand");
        flagdbg.append(((diff  & StatusBarManager.DISABLE_EXPAND) != 0) ? "* " : " ");
        flagdbg.append(((state & StatusBarManager.DISABLE_NOTIFICATION_ICONS) != 0) ? "ICONS" : "icons");
        flagdbg.append(((diff  & StatusBarManager.DISABLE_NOTIFICATION_ICONS) != 0) ? "* " : " ");
        flagdbg.append(((state & StatusBarManager.DISABLE_NOTIFICATION_ALERTS) != 0) ? "ALERTS" : "alerts");
        flagdbg.append(((diff  & StatusBarManager.DISABLE_NOTIFICATION_ALERTS) != 0) ? "* " : " ");
        flagdbg.append(((state & StatusBarManager.DISABLE_NOTIFICATION_TICKER) != 0) ? "TICKER" : "ticker");
        flagdbg.append(((diff  & StatusBarManager.DISABLE_NOTIFICATION_TICKER) != 0) ? "* " : " ");
        flagdbg.append(((state & StatusBarManager.DISABLE_SYSTEM_INFO) != 0) ? "SYSTEM_INFO" : "system_info");
        flagdbg.append(((diff  & StatusBarManager.DISABLE_SYSTEM_INFO) != 0) ? "* " : " ");
        flagdbg.append(((state & StatusBarManager.DISABLE_BACK) != 0) ? "BACK" : "back");
        flagdbg.append(((diff  & StatusBarManager.DISABLE_BACK) != 0) ? "* " : " ");
        flagdbg.append(((state & StatusBarManager.DISABLE_HOME) != 0) ? "HOME" : "home");
        flagdbg.append(((diff  & StatusBarManager.DISABLE_HOME) != 0) ? "* " : " ");
        flagdbg.append(((state & StatusBarManager.DISABLE_RECENT) != 0) ? "RECENT" : "recent");
        flagdbg.append(((diff  & StatusBarManager.DISABLE_RECENT) != 0) ? "* " : " ");
        flagdbg.append(((state & StatusBarManager.DISABLE_CLOCK) != 0) ? "CLOCK" : "clock");
        flagdbg.append(((diff  & StatusBarManager.DISABLE_CLOCK) != 0) ? "* " : " ");
        flagdbg.append(((state & StatusBarManager.DISABLE_SEARCH) != 0) ? "SEARCH" : "search");
        flagdbg.append(((diff  & StatusBarManager.DISABLE_SEARCH) != 0) ? "* " : " ");
        flagdbg.append(">");
        Log.d(TAG, flagdbg.toString());

        if ((diff & StatusBarManager.DISABLE_SYSTEM_INFO) != 0) {
            mSystemIconArea.animate().cancel();
            if ((state & StatusBarManager.DISABLE_SYSTEM_INFO) != 0) {
                mSystemIconArea.animate()
                    .alpha(0f)
                    .translationY(mNaturalBarHeight*0.5f)
                    .setDuration(175)
                    .setInterpolator(new DecelerateInterpolator(1.5f))
                    .setListener(mMakeIconsInvisible)
                    .start();
            } else {
                mSystemIconArea.setVisibility(View.VISIBLE);
                mSystemIconArea.animate()
                    .alpha(1f)
                    .translationY(0)
                    .setStartDelay(0)
                    .setInterpolator(new DecelerateInterpolator(1.5f))
                    .setDuration(175)
                    .start();
            }
        }

        if ((diff & StatusBarManager.DISABLE_CLOCK) != 0) {
            boolean show = (state & StatusBarManager.DISABLE_CLOCK) == 0;
            showClock(show);
        }
        if ((diff & StatusBarManager.DISABLE_EXPAND) != 0) {
            if ((state & StatusBarManager.DISABLE_EXPAND) != 0) {
                animateCollapsePanels();
            }
        }

        if ((diff & (StatusBarManager.DISABLE_HOME
                        | StatusBarManager.DISABLE_RECENT
                        | StatusBarManager.DISABLE_BACK
                        | StatusBarManager.DISABLE_SEARCH)) != 0) {
            // the nav bar will take care of these
            if (mNavigationBarView != null) mNavigationBarView.setDisabledFlags(state);

            if ((state & StatusBarManager.DISABLE_RECENT) != 0) {
                // close recents if it's visible
                mHandler.removeMessages(MSG_CLOSE_RECENTS_PANEL);
                mHandler.sendEmptyMessage(MSG_CLOSE_RECENTS_PANEL);
            }
        }

        if ((diff & StatusBarManager.DISABLE_NOTIFICATION_ICONS) != 0) {
            if ((state & StatusBarManager.DISABLE_NOTIFICATION_ICONS) != 0) {
                if (mTicking) {
                    haltTicker();
                }

                mNotificationIcons.animate()
                    .alpha(0f)
                    .translationY(mNaturalBarHeight*0.5f)
                    .setDuration(175)
                    .setInterpolator(new DecelerateInterpolator(1.5f))
                    .setListener(mMakeIconsInvisible)
                    .start();
            } else {
                mNotificationIcons.setVisibility(View.VISIBLE);
                mNotificationIcons.animate()
                    .alpha(1f)
                    .translationY(0)
                    .setStartDelay(0)
                    .setInterpolator(new DecelerateInterpolator(1.5f))
                    .setDuration(175)
                    .start();
            }
        } else if ((diff & StatusBarManager.DISABLE_NOTIFICATION_TICKER) != 0) {
            if (mTicking && (state & StatusBarManager.DISABLE_NOTIFICATION_TICKER) != 0) {
                haltTicker();
            }
        }
    }

    @Override
    protected BaseStatusBar.H createHandler() {
        return new PhoneStatusBar.H();
    }

    /**
     * All changes to the status bar and notifications funnel through here and are batched.
     */
    private class H extends BaseStatusBar.H {
        public void handleMessage(Message m) {
            super.handleMessage(m);
            switch (m.what) {
                case MSG_OPEN_NOTIFICATION_PANEL:
                    animateExpandNotificationsPanel();
                    break;
                case MSG_OPEN_SETTINGS_PANEL:
                    animateExpandSettingsPanel();
                    break;
                case MSG_CLOSE_PANELS:
                    animateCollapsePanels();
                    break;
                case MSG_SHOW_HEADS_UP:
                    setHeadsUpVisibility(true);
                    break;
                case MSG_HIDE_HEADS_UP:
                    setHeadsUpVisibility(false);
                    break;
                case MSG_ESCALATE_HEADS_UP:
                    escalateHeadsUp();
                    setHeadsUpVisibility(false);
                    break;
                case MSG_OPEN_NOTIFICATION_PANEL_SLOW:
                    createAndShowAppGuideDialog();
                    break;
            }
        }
    }

    /**  if the interrupting notification had a fullscreen intent, fire it now.  */
    private void escalateHeadsUp() {
        if (mInterruptingNotificationEntry != null) {
            final StatusBarNotification sbn = mInterruptingNotificationEntry.notification;
            final Notification notification = sbn.getNotification();
            if (notification.fullScreenIntent != null) {
                if (DEBUG)
                    Log.d(TAG, "converting a heads up to fullScreen");
                try {
                    notification.fullScreenIntent.send();
                } catch (PendingIntent.CanceledException e) {
                }
            }
        }
    }

    public Handler getHandler() {
        return mHandler;
    }

    View.OnFocusChangeListener mFocusChangeListener = new View.OnFocusChangeListener() {
        public void onFocusChange(View v, boolean hasFocus) {
            // Because 'v' is a ViewGroup, all its children will be (un)selected
            // too, which allows marqueeing to work.
            v.setSelected(hasFocus);
        }
    };

    boolean panelsEnabled() {
        return (mDisabled & StatusBarManager.DISABLE_EXPAND) == 0;
    }

    void makeExpandedVisible() {
        if (true) Log.d(TAG, "Make expanded visible: expanded visible=" + mExpandedVisible);
        if (mExpandedVisible || !panelsEnabled()) {
            return;
        }

        mExpandedVisible = true;
        mPile.setLayoutTransitionsEnabled(true);
        if (mNavigationBarView != null)
            mNavigationBarView.setSlippery(true);

        updateCarrierLabelVisibility(true);

        updateExpandedViewPos(EXPANDED_LEAVE_ALONE);

        // Expand the window to encompass the full screen in anticipation of the drag.
        // This is only possible to do atomically because the status bar is at the top of the screen!
        if (mStatusBarWindow.isAttachedToWindow()) {
            WindowManager.LayoutParams lp = (WindowManager.LayoutParams) mStatusBarWindow.getLayoutParams();
            lp.flags &= ~WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
            lp.flags |= WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM;
            lp.height = ViewGroup.LayoutParams.MATCH_PARENT;
            mWindowManager.updateViewLayout(mStatusBarWindow, lp);
        }

        /// M: Show always update clock of DateView.
        if (mDateView != null) {
            mDateView.updateClock();
        }
        visibilityChanged(true);

        setInteracting(StatusBarManager.WINDOW_STATUS_BAR, true);
    }

    private void releaseFocus() {
        if (mStatusBarWindow.isAttachedToWindow()) {
            WindowManager.LayoutParams lp =
                    (WindowManager.LayoutParams) mStatusBarWindow.getLayoutParams();
            lp.flags |= WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
            lp.flags &= ~WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM;
                mWindowManager.updateViewLayout(mStatusBarWindow, lp);
        }
    }

    public void animateCollapsePanels() {
        animateCollapsePanels(CommandQueue.FLAG_EXCLUDE_NONE);
    }

    public void animateCollapsePanels(int flags) {
        if (SPEW) {
            Log.d(TAG, "animateCollapse():"
                    + " mExpandedVisible=" + mExpandedVisible
                    + " flags=" + flags);
        }

        // release focus immediately to kick off focus change transition
        releaseFocus();

        if ((flags & CommandQueue.FLAG_EXCLUDE_RECENTS_PANEL) == 0) {
            mHandler.removeMessages(MSG_CLOSE_RECENTS_PANEL);
            mHandler.sendEmptyMessage(MSG_CLOSE_RECENTS_PANEL);
        }

        if ((flags & CommandQueue.FLAG_EXCLUDE_SEARCH_PANEL) == 0) {
            mHandler.removeMessages(MSG_CLOSE_SEARCH_PANEL);
            mHandler.sendEmptyMessage(MSG_CLOSE_SEARCH_PANEL);
        }

        mStatusBarWindow.cancelExpandHelper();
        mStatusBarView.collapseAllPanels(true);

        /// M: [ALPS00802561] Dismiss app guide while we collapse the panel.
        dismissAppGuideInt();
    }

    public ViewPropertyAnimator setVisibilityWhenDone(
            final ViewPropertyAnimator a, final View v, final int vis) {
        a.setListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                v.setVisibility(vis);
                a.setListener(null); // oneshot
            }
        });
        return a;
    }

    public Animator setVisibilityWhenDone(
            final Animator a, final View v, final int vis) {
        a.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                v.setVisibility(vis);
            }
        });
        return a;
    }

    public Animator interpolator(TimeInterpolator ti, Animator a) {
        a.setInterpolator(ti);
        return a;
    }

    public Animator startDelay(int d, Animator a) {
        a.setStartDelay(d);
        return a;
    }

    public Animator start(Animator a) {
        a.start();
        return a;
    }

    final TimeInterpolator mAccelerateInterpolator = new AccelerateInterpolator();
    final TimeInterpolator mDecelerateInterpolator = new DecelerateInterpolator();
    final int FLIP_DURATION_OUT = 125;
    final int FLIP_DURATION_IN = 225;
    final int FLIP_DURATION = (FLIP_DURATION_IN + FLIP_DURATION_OUT);

    Animator mScrollViewAnim, mFlipSettingsViewAnim, mNotificationButtonAnim,
        mSettingsButtonAnim, mClearButtonAnim;

    @Override
    public void animateExpandNotificationsPanel() {
        if (SPEW) Log.d(TAG, "animateExpand: mExpandedVisible=" + mExpandedVisible);
        if (!panelsEnabled()) {
            return ;
        }

        mNotificationPanel.expand();
        if (mHasFlipSettings && mScrollView.getVisibility() != View.VISIBLE) {
            flipToNotifications();
        }

        if (false) postStartTracing();
    }

    public void flipToNotifications() {
        if (mFlipSettingsViewAnim != null) mFlipSettingsViewAnim.cancel();
        if (mScrollViewAnim != null) mScrollViewAnim.cancel();
        if (mSettingsButtonAnim != null) mSettingsButtonAnim.cancel();
        if (mNotificationButtonAnim != null) mNotificationButtonAnim.cancel();
        if (mClearButtonAnim != null) mClearButtonAnim.cancel();

        mScrollView.setVisibility(View.VISIBLE);
        mScrollViewAnim = start(
            startDelay(FLIP_DURATION_OUT,
                interpolator(mDecelerateInterpolator,
                    ObjectAnimator.ofFloat(mScrollView, View.SCALE_X, 0f, 1f)
                        .setDuration(FLIP_DURATION_IN)
                    )));
        mFlipSettingsViewAnim = start(
            setVisibilityWhenDone(
                interpolator(mAccelerateInterpolator,
                        ObjectAnimator.ofFloat(mFlipSettingsView, View.SCALE_X, 1f, 0f)
                        )
                    .setDuration(FLIP_DURATION_OUT),
                mFlipSettingsView, View.INVISIBLE));
        mNotificationButtonAnim = start(
            setVisibilityWhenDone(
                ObjectAnimator.ofFloat(mNotificationButton, View.ALPHA, 0f)
                    .setDuration(FLIP_DURATION),
                mNotificationButton, View.INVISIBLE));
        mSettingsButton.setVisibility(View.VISIBLE);
        mSettingsButtonAnim = start(
            ObjectAnimator.ofFloat(mSettingsButton, View.ALPHA, 1f)
                .setDuration(FLIP_DURATION));
        mClearButton.setVisibility(View.VISIBLE);
        mClearButton.setAlpha(0f);
        setAreThereNotifications(); // this will show/hide the button as necessary
        mNotificationPanel.postDelayed(new Runnable() {
            public void run() {
                updateCarrierLabelVisibility(false);
            }
        }, FLIP_DURATION - 150);
        /// M: [SystemUI] Support SimIndicator, show SimIndicator when notification panel is visible. @{
        if (mToolBarView.mSimSwitchPanelView.isPanelShowing()) {
            mToolBarSwitchPanel.setVisibility(View.VISIBLE);
        }
        /// M: [SystemUI] Support SimIndicator, show SimIndicator when notification panel is visible. @{
    }

    @Override
    public void animateExpandSettingsPanel() {
        if (SPEW) Log.d(TAG, "animateExpand: mExpandedVisible=" + mExpandedVisible);
        if (!panelsEnabled()) {
            return;
        }

        // Settings are not available in setup
        if (!mUserSetup) return;

        if (mHasFlipSettings) {
            mNotificationPanel.expand();
            if (mFlipSettingsView.getVisibility() != View.VISIBLE) {
                flipToSettings();
            }
        } else if (mSettingsPanel != null) {
            mSettingsPanel.expand();
        }

        if (false) postStartTracing();
    }

    public void switchToSettings() {
        // Settings are not available in setup
        if (!mUserSetup) return;

        mFlipSettingsView.setScaleX(1f);
        mFlipSettingsView.setVisibility(View.VISIBLE);
        mSettingsButton.setVisibility(View.GONE);
        mScrollView.setVisibility(View.GONE);
        mScrollView.setScaleX(0f);
        mNotificationButton.setVisibility(View.VISIBLE);
        mNotificationButton.setAlpha(1f);
        mClearButton.setVisibility(View.GONE);
        /// M: [SystemUI] Support SimIndicator, hide SimIndicator when settings panel is visible.
        mToolBarSwitchPanel.setVisibility(View.GONE);
    }

    public void flipToSettings() {
        // Settings are not available in setup
        if (!mUserSetup) return;

        if (mFlipSettingsViewAnim != null) mFlipSettingsViewAnim.cancel();
        if (mScrollViewAnim != null) mScrollViewAnim.cancel();
        if (mSettingsButtonAnim != null) mSettingsButtonAnim.cancel();
        if (mNotificationButtonAnim != null) mNotificationButtonAnim.cancel();
        if (mClearButtonAnim != null) mClearButtonAnim.cancel();

        mFlipSettingsView.setVisibility(View.VISIBLE);
        mFlipSettingsView.setScaleX(0f);
        mFlipSettingsViewAnim = start(
            startDelay(FLIP_DURATION_OUT,
                interpolator(mDecelerateInterpolator,
                    ObjectAnimator.ofFloat(mFlipSettingsView, View.SCALE_X, 0f, 1f)
                        .setDuration(FLIP_DURATION_IN)
                    )));
        mScrollViewAnim = start(
            setVisibilityWhenDone(
                interpolator(mAccelerateInterpolator,
                        ObjectAnimator.ofFloat(mScrollView, View.SCALE_X, 1f, 0f)
                        )
                    .setDuration(FLIP_DURATION_OUT),
                mScrollView, View.INVISIBLE));
        mSettingsButtonAnim = start(
            setVisibilityWhenDone(
                ObjectAnimator.ofFloat(mSettingsButton, View.ALPHA, 0f)
                    .setDuration(FLIP_DURATION),
                    mScrollView, View.INVISIBLE));
        mNotificationButton.setVisibility(View.VISIBLE);
        mNotificationButtonAnim = start(
            ObjectAnimator.ofFloat(mNotificationButton, View.ALPHA, 1f)
                .setDuration(FLIP_DURATION));
        mClearButtonAnim = start(
            setVisibilityWhenDone(
                ObjectAnimator.ofFloat(mClearButton, View.ALPHA, 0f)
                .setDuration(FLIP_DURATION),
                mClearButton, View.INVISIBLE));
        /// M: [SystemUI] Support SimIndicator, hide SimIndicator when settings panel is visible.
        mToolBarSwitchPanel.setVisibility(View.GONE);
        mNotificationPanel.postDelayed(new Runnable() {
            public void run() {
                updateCarrierLabelVisibility(false);
            }
        }, FLIP_DURATION - 150);
    }

    public void flipPanels() {
        if (mHasFlipSettings) {
            if (mFlipSettingsView.getVisibility() != View.VISIBLE) {
                flipToSettings();
            } else {
                flipToNotifications();
            }
        }
    }

    public void animateCollapseQuickSettings() {
        mStatusBarView.collapseAllPanels(true);
    }

    void makeExpandedInvisibleSoon() {
        mHandler.postDelayed(new Runnable() { public void run() { makeExpandedInvisible(); }}, 50);
    }

    void makeExpandedInvisible() {
        if (true) Log.d(TAG, "makeExpandedInvisible: mExpandedVisible=" + mExpandedVisible
                + " mExpandedVisible=" + mExpandedVisible);

        if (!mExpandedVisible) {
            return;
        }

        // Ensure the panel is fully collapsed (just in case; bug 6765842, 7260868)
        mStatusBarView.collapseAllPanels(/*animate=*/ false);

        if (mHasFlipSettings) {
            // reset things to their proper state
            if (mFlipSettingsViewAnim != null) mFlipSettingsViewAnim.cancel();
            if (mScrollViewAnim != null) mScrollViewAnim.cancel();
            if (mSettingsButtonAnim != null) mSettingsButtonAnim.cancel();
            if (mNotificationButtonAnim != null) mNotificationButtonAnim.cancel();
            if (mClearButtonAnim != null) mClearButtonAnim.cancel();

            mScrollView.setScaleX(1f);
            mScrollView.setVisibility(View.VISIBLE);
            mSettingsButton.setAlpha(1f);
            mSettingsButton.setVisibility(View.VISIBLE);
            mNotificationPanel.setVisibility(View.GONE);
            mFlipSettingsView.setVisibility(View.GONE);
            mNotificationButton.setVisibility(View.GONE);
            /// M: [SystemUI] Support SimIndicator, show SimIndicator when notification panel is visible. @{
            if (mToolBarView.mSimSwitchPanelView.isPanelShowing()) {
                mToolBarSwitchPanel.setVisibility(View.VISIBLE);
            }
            /// M: [SystemUI] Support SimIndicator, show SimIndicator when notification panel is visible. @}
            setAreThereNotifications(); // show the clear button
        }

        mExpandedVisible = false;
        mPile.setLayoutTransitionsEnabled(false);
        if (mNavigationBarView != null)
            mNavigationBarView.setSlippery(false);
        visibilityChanged(false);

        // Shrink the window to the size of the status bar only
        if (mStatusBarWindow.isAttachedToWindow()) {
            WindowManager.LayoutParams lp = (WindowManager.LayoutParams) mStatusBarWindow.getLayoutParams();
            lp.height = getStatusBarHeight();
            lp.flags |= WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
            lp.flags &= ~WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM;
            mWindowManager.updateViewLayout(mStatusBarWindow, lp);
        }

        if ((mDisabled & StatusBarManager.DISABLE_NOTIFICATION_ICONS) == 0) {
            setNotificationIconVisibility(true, com.android.internal.R.anim.fade_in);
        }

        /// M: [SystemUI] Support "Notification toolbar". {
        mToolBarView.dismissDialogs();
        if (mQS != null) {
            mQS.dismissDialogs();
        }
        /// M: [SystemUI] Support "Notification toolbar". }

        /// M: [SystemUI] Dismiss application guide dialog.@{
        if (mAppGuideDialog != null && mAppGuideDialog.isShowing()) {
            mAppGuideDialog.dismiss();
            Xlog.d(TAG, "performCollapse dismiss mAppGuideDialog");
        }
        /// M: [SystemUI] Dismiss application guide dialog.@}

        
        // Close any "App info" popups that might have snuck on-screen
        dismissPopups();

        if (mPostCollapseCleanup != null) {
            mPostCollapseCleanup.run();
            mPostCollapseCleanup = null;
        }

        setInteracting(StatusBarManager.WINDOW_STATUS_BAR, false);
    }

    /**
     * Enables or disables layers on the children of the notifications pile.
     *
     * When layers are enabled, this method attempts to enable layers for the minimal
     * number of children. Only children visible when the notification area is fully
     * expanded will receive a layer. The technique used in this method might cause
     * more children than necessary to get a layer (at most one extra child with the
     * current UI.)
     *
     * @param layerType {@link View#LAYER_TYPE_NONE} or {@link View#LAYER_TYPE_HARDWARE}
     */
    private void setPileLayers(int layerType) {
        final int count = mPile.getChildCount();

        switch (layerType) {
            case View.LAYER_TYPE_NONE:
                for (int i = 0; i < count; i++) {
                    mPile.getChildAt(i).setLayerType(layerType, null);
                }
                break;
            case View.LAYER_TYPE_HARDWARE:
                final int[] location = new int[2];
                mNotificationPanel.getLocationInWindow(location);

                final int left = location[0];
                final int top = location[1];
                final int right = left + mNotificationPanel.getWidth();
                final int bottom = top + getExpandedViewMaxHeight();

                final Rect childBounds = new Rect();

                for (int i = 0; i < count; i++) {
                    final View view = mPile.getChildAt(i);
                    view.getLocationInWindow(location);

                    childBounds.set(location[0], location[1],
                            location[0] + view.getWidth(), location[1] + view.getHeight());

                    if (childBounds.intersects(left, top, right, bottom)) {
                        view.setLayerType(layerType, null);
                    }
                }

                break;
        }
    }

    private void setQuickSettingLayers(int layerType) {
        if (mSettingsContainer != null) {
            final int count = mSettingsContainer.getChildCount();

            switch (layerType) {
                case View.LAYER_TYPE_NONE:
                case View.LAYER_TYPE_HARDWARE:
                    for (int i = 0; i < count; i++) {
                        mSettingsContainer.getChildAt(i).setLayerType(layerType, null);
                    }
                    break;
            }
        }
    }

    private void setNotificationIconsLayers(int layerType) {
        if (mNotificationIcons != null) {
            final int count = mNotificationIcons.getChildCount();

            switch (layerType) {
                case View.LAYER_TYPE_NONE:
                    for (int i = 0; i < count; i++) {
                        mNotificationIcons.getChildAt(i).setLayerType(layerType, null);
                    }
                    break;
                case View.LAYER_TYPE_HARDWARE:
                    for (int i = 0; i < count; i++) {
                        if (mNotificationIcons.getChildAt(i).getVisibility() != View.GONE) {
                            mNotificationIcons.getChildAt(i).setLayerType(layerType, null);
                        }
                    }
                    break;
            }
        }
    }

    private void setSignalClusterLayers(int layerType) {
        final SignalClusterView signalCluster =
                (SignalClusterView)mStatusBarView.findViewById(R.id.signal_cluster);

        if (signalCluster != null) {
            final int count = signalCluster.getChildCount();

            switch (layerType) {
                case View.LAYER_TYPE_NONE:
                case View.LAYER_TYPE_HARDWARE:
                    for (int i = 0; i < count; i++) {
                        signalCluster.getChildAt(i).setLayerType(layerType, null);
                    }
                    break;
            }
        }
    }

    public void setLayers(int layerType) {
        setPileLayers(layerType);
        setQuickSettingLayers(layerType);
        setNotificationIconsLayers(layerType);
        setSignalClusterLayers(layerType);
    }

    public boolean interceptTouchEvent(MotionEvent event) {
        if (DEBUG_GESTURES) {
            if (event.getActionMasked() != MotionEvent.ACTION_MOVE) {
                EventLog.writeEvent(EventLogTags.SYSUI_STATUSBAR_TOUCH,
                        event.getActionMasked(), (int) event.getX(), (int) event.getY(), mDisabled);
            }

        }

        if (SPEW) {
            Log.d(TAG, "Touch: rawY=" + event.getRawY() + " event=" + event + " mDisabled="
                + mDisabled + " mTracking=" + mTracking);
        } else if (CHATTY) {
            if (event.getAction() != MotionEvent.ACTION_MOVE) {
                Log.d(TAG, String.format(
                            "panel: %s at (%f, %f) mDisabled=0x%08x",
                            MotionEvent.actionToString(event.getAction()),
                            event.getRawX(), event.getRawY(), mDisabled));
            }
        }

        if (DEBUG_GESTURES) {
            mGestureRec.add(event);
        }

        if (mStatusBarWindowState == WINDOW_STATE_SHOWING) {
            final boolean upOrCancel =
                    event.getAction() == MotionEvent.ACTION_UP ||
                    event.getAction() == MotionEvent.ACTION_CANCEL;
            if (upOrCancel && !mExpandedVisible) {
                setInteracting(StatusBarManager.WINDOW_STATUS_BAR, false);
            } else {
                setInteracting(StatusBarManager.WINDOW_STATUS_BAR, true);
            }
        }
        return false;
    }

    public GestureRecorder getGestureRecorder() {
        return mGestureRec;
    }

    private void setNavigationIconHints(int hints) {
        if (hints == mNavigationIconHints) return;

        mNavigationIconHints = hints;

        if (mNavigationBarView != null) {
            mNavigationBarView.setNavigationIconHints(hints);
        }
        checkBarModes();
    }

    @Override // CommandQueue
    public void setWindowState(int window, int state) {
        boolean showing = state == WINDOW_STATE_SHOWING;
        if (mStatusBarWindow != null
                && window == StatusBarManager.WINDOW_STATUS_BAR
                && mStatusBarWindowState != state) {
            mStatusBarWindowState = state;
            if (DEBUG_WINDOW_STATE) Log.d(TAG, "Status bar " + windowStateToString(state));
            if (!showing) {
                mStatusBarView.collapseAllPanels(false);
            }
        }
        if (mNavigationBarView != null
                && window == StatusBarManager.WINDOW_NAVIGATION_BAR
                && mNavigationBarWindowState != state) {
            mNavigationBarWindowState = state;
            if (DEBUG_WINDOW_STATE) Log.d(TAG, "Navigation bar " + windowStateToString(state));
        }
    }

    @Override // CommandQueue
    public void setSystemUiVisibility(int vis, int mask) {
        final int oldVal = mSystemUiVisibility;
        final int newVal = (oldVal&~mask) | (vis&mask);
        final int diff = newVal ^ oldVal;
        if (true) Log.d(TAG, String.format(
                "setSystemUiVisibility vis=%s mask=%s oldVal=%s newVal=%s diff=%s",
                Integer.toHexString(vis), Integer.toHexString(mask),
                Integer.toHexString(oldVal), Integer.toHexString(newVal),
                Integer.toHexString(diff)));
        if (diff != 0) {
            mSystemUiVisibility = newVal;

            // update low profile
            if ((diff & View.SYSTEM_UI_FLAG_LOW_PROFILE) != 0) {
                final boolean lightsOut = (vis & View.SYSTEM_UI_FLAG_LOW_PROFILE) != 0;
                if (lightsOut) {
                    animateCollapsePanels();
                    if (mTicking) {
                        haltTicker();
                    }
                }

                setAreThereNotifications();
            }

            // update status bar mode
            final int sbMode = computeBarMode(oldVal, newVal, mStatusBarView.getBarTransitions(),
                    View.STATUS_BAR_TRANSIENT, View.STATUS_BAR_TRANSLUCENT);

            // update navigation bar mode
            final int nbMode = mNavigationBarView == null ? -1 : computeBarMode(
                    oldVal, newVal, mNavigationBarView.getBarTransitions(),
                    View.NAVIGATION_BAR_TRANSIENT, View.NAVIGATION_BAR_TRANSLUCENT);
            final boolean sbModeChanged = sbMode != -1;
            final boolean nbModeChanged = nbMode != -1;
            boolean checkBarModes = false;
            if (sbModeChanged && sbMode != mStatusBarMode) {
                mStatusBarMode = sbMode;
                checkBarModes = true;
            }
            if (nbModeChanged && nbMode != mNavigationBarMode) {
                mNavigationBarMode = nbMode;
                checkBarModes = true;
            }
            if (checkBarModes) {
                checkBarModes();
            }
            if (sbModeChanged || nbModeChanged) {
                // update transient bar autohide
                if (sbMode == MODE_SEMI_TRANSPARENT || nbMode == MODE_SEMI_TRANSPARENT) {
                    scheduleAutohide();
                } else {
                    cancelAutohide();
                }
            }

            // ready to unhide
            if ((vis & View.STATUS_BAR_UNHIDE) != 0) {
                mSystemUiVisibility &= ~View.STATUS_BAR_UNHIDE;
            }
            if ((vis & View.NAVIGATION_BAR_UNHIDE) != 0) {
                mSystemUiVisibility &= ~View.NAVIGATION_BAR_UNHIDE;
            }

            // send updated sysui visibility to window manager
            notifyUiVisibilityChanged(mSystemUiVisibility);
        }
    }

    private int computeBarMode(int oldVis, int newVis, BarTransitions transitions,
            int transientFlag, int translucentFlag) {
        final int oldMode = barMode(oldVis, transientFlag, translucentFlag);
        final int newMode = barMode(newVis, transientFlag, translucentFlag);
        if (oldMode == newMode) {
            return -1; // no mode change
        }
        return newMode;
    }

    private int barMode(int vis, int transientFlag, int translucentFlag) {
        return (vis & transientFlag) != 0 ? MODE_SEMI_TRANSPARENT
                : (vis & translucentFlag) != 0 ? MODE_TRANSLUCENT
                : (vis & View.SYSTEM_UI_FLAG_LOW_PROFILE) != 0 ? MODE_LIGHTS_OUT
                : MODE_OPAQUE;
    }

    private void checkBarModes() {
        if (mDemoMode) return;
        int sbMode = mStatusBarMode;
        if (panelsEnabled() && (mInteractingWindows & StatusBarManager.WINDOW_STATUS_BAR) != 0) {
            // if panels are expandable, force the status bar opaque on any interaction
            sbMode = MODE_OPAQUE;
        }
        checkBarMode(sbMode, mStatusBarWindowState, mStatusBarView.getBarTransitions());
        if (mNavigationBarView != null) {
            checkBarMode(mNavigationBarMode,
                    mNavigationBarWindowState, mNavigationBarView.getBarTransitions());
        }
    }

    private void checkBarMode(int mode, int windowState, BarTransitions transitions) {
        final boolean anim = (mScreenOn == null || mScreenOn) && windowState != WINDOW_STATE_HIDDEN && mode != MODE_OPAQUE;
        transitions.transitionTo(mode, anim);
    }

    private void finishBarAnimations() {
        mStatusBarView.getBarTransitions().finishAnimations();
        if (mNavigationBarView != null) {
            mNavigationBarView.getBarTransitions().finishAnimations();
        }
    }

    private final Runnable mCheckBarModes = new Runnable() {
        @Override
        public void run() {
            checkBarModes();
        }};

    @Override
    public void setInteracting(int barWindow, boolean interacting) {
        mInteractingWindows = interacting
                ? (mInteractingWindows | barWindow)
                : (mInteractingWindows & ~barWindow);
        if (mInteractingWindows != 0) {
            suspendAutohide();
        } else {
            resumeSuspendedAutohide();
        }
        checkBarModes();
    }

    private void resumeSuspendedAutohide() {
        if (mAutohideSuspended) {
            scheduleAutohide();
            mHandler.postDelayed(mCheckBarModes, 500); // longer than home -> launcher
        }
    }

    private void suspendAutohide() {
        mHandler.removeCallbacks(mAutohide);
        mHandler.removeCallbacks(mCheckBarModes);
        mAutohideSuspended = (mSystemUiVisibility & STATUS_OR_NAV_TRANSIENT) != 0;
    }

    private void cancelAutohide() {
        mAutohideSuspended = false;
        mHandler.removeCallbacks(mAutohide);
    }

    private void scheduleAutohide() {
        cancelAutohide();
        mHandler.postDelayed(mAutohide, AUTOHIDE_TIMEOUT_MS);
    }

    private void checkUserAutohide(View v, MotionEvent event) {
        if ((mSystemUiVisibility & STATUS_OR_NAV_TRANSIENT) != 0  // a transient bar is revealed
                && event.getAction() == MotionEvent.ACTION_OUTSIDE // touch outside the source bar
                && event.getX() == 0 && event.getY() == 0  // a touch outside both bars
                ) {
            userAutohide();
        }
    }

    private void userAutohide() {
        cancelAutohide();
        mHandler.postDelayed(mAutohide, 350); // longer than app gesture -> flag clear
    }

    private boolean areLightsOn() {
        return 0 == (mSystemUiVisibility & View.SYSTEM_UI_FLAG_LOW_PROFILE);
    }

    public void setLightsOn(boolean on) {
        Log.v(TAG, "setLightsOn(" + on + ")");
        if (on) {
            setSystemUiVisibility(0, View.SYSTEM_UI_FLAG_LOW_PROFILE);
        } else {
            setSystemUiVisibility(View.SYSTEM_UI_FLAG_LOW_PROFILE, View.SYSTEM_UI_FLAG_LOW_PROFILE);
        }
    }

    private void notifyUiVisibilityChanged(int vis) {
        try {
            mWindowManagerService.statusBarVisibilityChanged(vis);
        } catch (RemoteException ex) {
        }
    }

    public void topAppWindowChanged(boolean showMenu) {
        if (DEBUG) {
            Log.d(TAG, (showMenu?"showing":"hiding") + " the MENU button");
        }
        if (mNavigationBarView != null) {
            mNavigationBarView.setMenuVisibility(showMenu);
        }

        // See above re: lights-out policy for legacy apps.
        if (showMenu) setLightsOn(true);
    }

    @Override
    public void setImeWindowStatus(IBinder token, int vis, int backDisposition) {
        boolean altBack = (backDisposition == InputMethodService.BACK_DISPOSITION_WILL_DISMISS)
            || ((vis & InputMethodService.IME_VISIBLE) != 0);

        setNavigationIconHints(
                altBack ? (mNavigationIconHints | NAVIGATION_HINT_BACK_ALT)
                        : (mNavigationIconHints & ~NAVIGATION_HINT_BACK_ALT));
        if (mQS != null) mQS.setImeWindowStatus(vis > 0);
    }

    @Override
    public void setHardKeyboardStatus(boolean available, boolean enabled) {}

    @Override
    protected void tick(IBinder key, StatusBarNotification n, boolean firstTime) {
        // no ticking in lights-out mode
        if (!areLightsOn()) {
            if (ENABLE_REQUEST_TRANSIENT_STATUSBAR) {
                Xlog.d(TAG, "ticker !areLightsOn, mStatusBarWindowState = " + mStatusBarWindowState
                    + ", mStatusbarMode = " + mStatusBarMode);
                if (mStatusBarWindowState != WINDOW_STATE_SHOWING) {
                    /// Go Ticking for Full Screen.
                } else {
                    return;
                }
            } else {
                return;
            }
        }

        // no ticking in Setup
        if (!isDeviceProvisioned()) return;

        // not for you
        if (!notificationIsForCurrentUser(n)) return;

        // Show the ticker if one is requested. Also don't do this
        // until status bar window is attached to the window manager,
        // because...  well, what's the point otherwise?  And trying to
        // run a ticker without being attached will crash!
        if (n.getNotification().tickerText != null && mStatusBarWindow.getWindowToken() != null) {
            if (0 == (mDisabled & (StatusBarManager.DISABLE_NOTIFICATION_ICONS
                            | StatusBarManager.DISABLE_NOTIFICATION_TICKER))) {
                mTicker.addEntry(n);
            }
        }
    }

    private class MyTicker extends Ticker {
        MyTicker(Context context, View sb) {
            super(context, sb);
        }

        @Override
        public void tickerStarting() {
            mTicking = true;
            mStatusBarContents.setVisibility(View.GONE);
            mTickerView.setVisibility(View.VISIBLE);
            mTickerView.startAnimation(loadAnim(com.android.internal.R.anim.push_up_in, null));
            mStatusBarContents.startAnimation(loadAnim(com.android.internal.R.anim.push_up_out, null));
            if (ENABLE_REQUEST_TRANSIENT_STATUSBAR ) {
                Xlog.d(TAG, "tickerStarting, mStatusBarWindowState = " + mStatusBarWindowState
                    + ", mStatusbarMode = " + mStatusBarMode);
                if (mStatusBarWindowState != WINDOW_STATE_SHOWING) {
                    /// Show ticking when the status bar is not showing.
                    requestTransientStatusbar();
                    mHandler.postDelayed(new Runnable() { 
                        public void run() { cancelAutohide(); }}, 500);
                } else if (mStatusBarMode == MODE_SEMI_TRANSPARENT) {
                    /// Cancel auto hide to play ticking.
                    cancelAutohide();
                }
            }
        }

        @Override
        public void tickerDone() {
            if (!mTicking) return; ///M: ALPS00829072
            mStatusBarContents.setVisibility(View.VISIBLE);
            mTickerView.setVisibility(View.GONE);
            mStatusBarContents.startAnimation(loadAnim(com.android.internal.R.anim.push_down_in, null));
            mTickerView.startAnimation(loadAnim(com.android.internal.R.anim.push_down_out,
                        mTickingDoneListener));
            Xlog.d(TAG, "tickerDone, mStatusbarMode = " + mStatusBarMode);
            if (ENABLE_REQUEST_TRANSIENT_STATUSBAR && mStatusBarMode == MODE_SEMI_TRANSPARENT) {
                /// Restart.
                scheduleAutohide();
            }
        }

        public void tickerHalting() {
            if (mStatusBarContents.getVisibility() != View.VISIBLE) {
                mStatusBarContents.setVisibility(View.VISIBLE);
                mStatusBarContents
                        .startAnimation(loadAnim(com.android.internal.R.anim.fade_in, null));
            }
            mTickerView.setVisibility(View.GONE);
            // we do not animate the ticker away at this point, just get rid of it (b/6992707)
            mTicking = false; ///M: ALPS00829072
            Xlog.d(TAG, "tickerHalting, mStatusbarMode = " + mStatusBarMode);
            if (ENABLE_REQUEST_TRANSIENT_STATUSBAR && mStatusBarMode == MODE_SEMI_TRANSPARENT) {
                /// Restart.
                scheduleAutohide();
            }
        }
    }

    Animation.AnimationListener mTickingDoneListener = new Animation.AnimationListener() {;
        public void onAnimationEnd(Animation animation) {
            mTicking = false;
        }
        public void onAnimationRepeat(Animation animation) {
        }
        public void onAnimationStart(Animation animation) {
        }
    };

    private Animation loadAnim(int id, Animation.AnimationListener listener) {
        Animation anim = AnimationUtils.loadAnimation(mContext, id);
        if (listener != null) {
            anim.setAnimationListener(listener);
        }
        return anim;
    }

    public static String viewInfo(View v) {
        return "[(" + v.getLeft() + "," + v.getTop() + ")(" + v.getRight() + "," + v.getBottom()
                + ") " + v.getWidth() + "x" + v.getHeight() + "]";
    }

    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        synchronized (mQueueLock) {
            pw.println("Current Status Bar state:");
            pw.println("  mExpandedVisible=" + mExpandedVisible
                    + ", mTrackingPosition=" + mTrackingPosition);
            pw.println("  mTicking=" + mTicking);
            pw.println("  mTracking=" + mTracking);
            pw.println("  mDisplayMetrics=" + mDisplayMetrics);
            pw.println("  mPile: " + viewInfo(mPile));
            pw.println("  mTickerView: " + viewInfo(mTickerView));
            pw.println("  mScrollView: " + viewInfo(mScrollView)
                    + " scroll " + mScrollView.getScrollX() + "," + mScrollView.getScrollY());
        }

        pw.print("  mInteractingWindows="); pw.println(mInteractingWindows);
        pw.print("  mStatusBarWindowState=");
        pw.println(windowStateToString(mStatusBarWindowState));
        pw.print("  mStatusBarMode=");
        pw.println(BarTransitions.modeToString(mStatusBarMode));
        dumpBarTransitions(pw, "mStatusBarView", mStatusBarView.getBarTransitions());
        if (mNavigationBarView != null) {
            pw.print("  mNavigationBarWindowState=");
            pw.println(windowStateToString(mNavigationBarWindowState));
            pw.print("  mNavigationBarMode=");
            pw.println(BarTransitions.modeToString(mNavigationBarMode));
            dumpBarTransitions(pw, "mNavigationBarView", mNavigationBarView.getBarTransitions());
        }

        pw.print("  mNavigationBarView=");
        if (mNavigationBarView == null) {
            pw.println("null");
        } else {
            mNavigationBarView.dump(fd, pw, args);
        }

        pw.println("  Panels: ");
        if (mNotificationPanel != null) {
            pw.println("    mNotificationPanel=" +
                mNotificationPanel + " params=" + mNotificationPanel.getLayoutParams().debug(""));
            pw.print  ("      ");
            mNotificationPanel.dump(fd, pw, args);
        }
        if (mSettingsPanel != null) {
            pw.println("    mSettingsPanel=" +
                mSettingsPanel + " params=" + mSettingsPanel.getLayoutParams().debug(""));
            pw.print  ("      ");
            mSettingsPanel.dump(fd, pw, args);
        }

        if (DUMPTRUCK) {
            synchronized (mNotificationData) {
                int N = mNotificationData.size();
                pw.println("  notification icons: " + N);
                for (int i=0; i<N; i++) {
                    NotificationData.Entry e = mNotificationData.get(i);
                    pw.println("    [" + i + "] key=" + e.key + " icon=" + e.icon);
                    StatusBarNotification n = e.notification;
                    pw.println("         pkg=" + n.getPackageName() + " id=" + n.getId() + " score=" + n.getScore());
                    pw.println("         notification=" + n.getNotification());
                    pw.println("         tickerText=\"" + n.getNotification().tickerText + "\"");
                }
            }

            int N = mStatusIcons.getChildCount();
            pw.println("  system icons: " + N);
            for (int i=0; i<N; i++) {
                StatusBarIconView ic = (StatusBarIconView) mStatusIcons.getChildAt(i);
                pw.println("    [" + i + "] icon=" + ic);
            }

            if (false) {
                pw.println("see the logcat for a dump of the views we have created.");
                // must happen on ui thread
                mHandler.post(new Runnable() {
                        public void run() {
                            mStatusBarView.getLocationOnScreen(mAbsPos);
                            Log.d(TAG, "mStatusBarView: ----- (" + mAbsPos[0] + "," + mAbsPos[1]
                                    + ") " + mStatusBarView.getWidth() + "x"
                                    + getStatusBarHeight());
                            mStatusBarView.debug();
                        }
                    });
            }
        }

        if (DEBUG_GESTURES) {
            pw.print("  status bar gestures: ");
            mGestureRec.dump(fd, pw, args);
        }

        mNetworkController.dump(fd, pw, args);
    }

    private static void dumpBarTransitions(PrintWriter pw, String var, BarTransitions transitions) {
        pw.print("  "); pw.print(var); pw.print(".BarTransitions.mMode=");
        pw.println(BarTransitions.modeToString(transitions.getMode()));
    }

    @Override
    public void createAndAddWindows() {
        addStatusBarWindow();
    }

    private void addStatusBarWindow() {
        // Put up the view
        final int height = getStatusBarHeight();

        // Now that the status bar window encompasses the sliding panel and its
        // translucent backdrop, the entire thing is made TRANSLUCENT and is
        // hardware-accelerated.
        final WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                height,
                WindowManager.LayoutParams.TYPE_STATUS_BAR,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                    | WindowManager.LayoutParams.FLAG_TOUCHABLE_WHEN_WAKING
                    | WindowManager.LayoutParams.FLAG_SPLIT_TOUCH
                    | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                PixelFormat.TRANSLUCENT);

        lp.flags |= WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED;

        lp.gravity = getStatusBarGravity();
        lp.setTitle("StatusBar");
        lp.packageName = mContext.getPackageName();

        makeStatusBarView();

        /// M: [SystemUI] For SystemUI AT.
        if (AutoTestHelper.isNotRunningInTest()) {
            mWindowManager.addView(mStatusBarWindow, lp);
        }
    }

    void setNotificationIconVisibility(boolean visible, int anim) {
        int old = mNotificationIcons.getVisibility();
        int v = visible ? View.VISIBLE : View.INVISIBLE;
        if (old != v) {
            mNotificationIcons.setVisibility(v);
            mNotificationIcons.startAnimation(loadAnim(anim, null));
        }
    }

    void updateExpandedInvisiblePosition() {
        mTrackingPosition = -mDisplayMetrics.heightPixels;
    }

    static final float saturate(float a) {
        return a < 0f ? 0f : (a > 1f ? 1f : a);
    }

    @Override
    protected int getExpandedViewMaxHeight() {
        return mDisplayMetrics.heightPixels - mNotificationPanelMarginBottomPx;
    }

    @Override
    public void updateExpandedViewPos(int thingy) {
        if (SPEW) Log.v(TAG, "updateExpandedViewPos");

        // on larger devices, the notification panel is propped open a bit
        mNotificationPanel.setMinimumHeight(
                (int)(mNotificationPanelMinHeightFrac * mCurrentDisplaySize.y));

        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) mNotificationPanel.getLayoutParams();
        lp.gravity = mNotificationPanelGravity;
        lp.setMarginStart(mNotificationPanelMarginPx);
        mNotificationPanel.setLayoutParams(lp);

        if (mSettingsPanel != null) {
            lp = (FrameLayout.LayoutParams) mSettingsPanel.getLayoutParams();
            lp.gravity = mSettingsPanelGravity;
            lp.setMarginEnd(mNotificationPanelMarginPx);
            mSettingsPanel.setLayoutParams(lp);
        }

        if (ENABLE_HEADS_UP && mHeadsUpNotificationView != null) {
            mHeadsUpNotificationView.setMargin(mNotificationPanelMarginPx);
            mPile.getLocationOnScreen(mPilePosition);
            mHeadsUpVerticalOffset = mPilePosition[1] - mNaturalBarHeight;
        }

        updateCarrierLabelVisibility(false);
    }

    // called by makeStatusbar and also by PhoneStatusBarView
    void updateDisplaySize() {
        mDisplay.getMetrics(mDisplayMetrics);
        mDisplay.getSize(mCurrentDisplaySize);
        if (DEBUG_GESTURES) {
            mGestureRec.tag("display",
                    String.format("%dx%d", mDisplayMetrics.widthPixels, mDisplayMetrics.heightPixels));
        }
    }

    private View.OnClickListener mClearButtonListener = new View.OnClickListener() {
        public void onClick(View v) {
            synchronized (mNotificationData) {
                // animate-swipe all dismissable notifications, then animate the shade closed
                int numChildren = mPile.getChildCount();

                int scrollTop = mScrollView.getScrollY();
                int scrollBottom = scrollTop + mScrollView.getHeight();
                final ArrayList<View> snapshot = new ArrayList<View>(numChildren);
                for (int i=0; i<numChildren; i++) {
                    final View child = mPile.getChildAt(i);
                    if (mPile.canChildBeDismissed(child) && child.getBottom() > scrollTop &&
                            child.getTop() < scrollBottom) {
                        snapshot.add(child);
                    }
                }
                if (snapshot.isEmpty()) {
                    animateCollapsePanels(CommandQueue.FLAG_EXCLUDE_NONE);
                    return;
                }
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        // Decrease the delay for every row we animate to give the sense of
                        // accelerating the swipes
                        final int ROW_DELAY_DECREMENT = 10;
                        int currentDelay = 140;
                        int totalDelay = 0;

                        // Set the shade-animating state to avoid doing other work during
                        // all of these animations. In particular, avoid layout and
                        // redrawing when collapsing the shade.
                        mPile.setViewRemoval(false);

                        mPostCollapseCleanup = new Runnable() {
                            @Override
                            public void run() {
                                if (DEBUG) {
                                    Log.v(TAG, "running post-collapse cleanup");
                                }
                                try {
                                    mPile.setViewRemoval(true);
                                    mBarService.onClearAllNotifications();
                                } catch (Exception ex) { }
                            }
                        };

                        View sampleView = snapshot.get(0);
                        int width = sampleView.getWidth();
                        final int dir = sampleView.isLayoutRtl() ? -1 : +1;
                        final int velocity = dir * width * 8; // 1000/8 = 125 ms duration
                        for (final View _v : snapshot) {
                            mHandler.postDelayed(new Runnable() {
                                @Override
                                public void run() {
                                    mPile.dismissRowAnimated(_v, velocity);
                                }
                            }, totalDelay);
                            currentDelay = Math.max(50, currentDelay - ROW_DELAY_DECREMENT);
                            totalDelay += currentDelay;
                        }
                        // Delay the collapse animation until after all swipe animations have
                        // finished. Provide some buffer because there may be some extra delay
                        // before actually starting each swipe animation. Ideally, we'd
                        // synchronize the end of those animations with the start of the collaps
                        // exactly.
                        mHandler.postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                animateCollapsePanels(CommandQueue.FLAG_EXCLUDE_NONE);
                            }
                        }, totalDelay + 225);
                    }
                }).start();
                /// M: [SystemUI] Dismiss new event icon when click clear button for keyguard.@{
                Intent intent = new Intent(CLEAR_NEW_EVENT_VIEW_INTENT);
                mContext.sendBroadcast(intent);
                /// M: [SystemUI] Dismiss new event icon when click clear button for keyguard.@}
            }
        }
    };

    public void startActivityDismissingKeyguard(Intent intent, boolean onlyProvisioned) {
        if (onlyProvisioned && !isDeviceProvisioned()) return;
        try {
            // Dismiss the lock screen when Settings starts.
            ActivityManagerNative.getDefault().dismissKeyguardOnNextActivity();
        } catch (RemoteException e) {
        }
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        mContext.startActivityAsUser(intent, new UserHandle(UserHandle.USER_CURRENT));
        animateCollapsePanels();
    }

    private View.OnClickListener mSettingsButtonListener = new View.OnClickListener() {
        public void onClick(View v) {
            if (mHasSettingsPanel) {
                animateExpandSettingsPanel();
            } else {
                startActivityDismissingKeyguard(
                        new Intent(android.provider.Settings.ACTION_SETTINGS), true);
            }
        }
    };

    private View.OnClickListener mClockClickListener = new View.OnClickListener() {
        public void onClick(View v) {
            startActivityDismissingKeyguard(
                    new Intent(Intent.ACTION_QUICK_CLOCK), true); // have fun, everyone
        }
    };

    private View.OnClickListener mNotificationButtonListener = new View.OnClickListener() {
        public void onClick(View v) {
            animateExpandNotificationsPanel();
        }
    };

    private BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            if (DEBUG) Log.v(TAG, "onReceive: " + intent);
            String action = intent.getAction();
            Xlog.d(TAG, "onReceive, action=" + action);
            if (Intent.ACTION_CLOSE_SYSTEM_DIALOGS.equals(action)) {
                int flags = CommandQueue.FLAG_EXCLUDE_NONE;
                if (Intent.ACTION_CLOSE_SYSTEM_DIALOGS.equals(action)) {
                    String reason = intent.getStringExtra("reason");
                    if (reason != null && reason.equals(SYSTEM_DIALOG_REASON_RECENT_APPS)) {
                        flags |= CommandQueue.FLAG_EXCLUDE_RECENTS_PANEL;
                    }
                }
                animateCollapsePanels(flags);
            }
            else if (Intent.ACTION_SCREEN_OFF.equals(action)) {
                mScreenOn = false;
                // no waiting!
                /// M: [SystemUI]Show application guide for App.
                dismissAppGuideInt();
                /// M: [SystemUI]Show application guide for App. @}
                makeExpandedInvisible();
                notifyNavigationBarScreenOn(false);
                notifyHeadsUpScreenOn(false);
                finishBarAnimations();
            }
            else if (Intent.ACTION_SCREEN_ON.equals(action)) {
                mScreenOn = true;
                // work around problem where mDisplay.getRotation() is not stable while screen is off (bug 7086018)
                repositionNavigationBar();
                notifyNavigationBarScreenOn(true);
            }
            else if (ACTION_DEMO.equals(action)) {
                Bundle bundle = intent.getExtras();
                if (bundle != null) {
                    String command = bundle.getString("command", "").trim().toLowerCase();
                    if (command.length() > 0) {
                        try {
                            dispatchDemoCommand(command, bundle);
                        } catch (Throwable t) {
                            Log.w(TAG, "Error running demo command, intent=" + intent, t);
                        }
                    }
                }
            }
        }
    };

    // SystemUIService notifies SystemBars of configuration changes, which then calls down here
    @Override
    protected void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig); // calls refreshLayout

        if (DEBUG) {
            Log.v(TAG, "configuration changed: " + mContext.getResources().getConfiguration());
        }

        /// M: [SystemUI]Show application guide for App.
        refreshApplicationGuide();
        /// M: [ALPS00336833] When orientation changed, request layout to avoid status bar layout error. @{
        if (newConfig.orientation != mPrevioutConfigOrientation) {
            mNeedRelayout = true;
            mPrevioutConfigOrientation = newConfig.orientation;
        }
        /// M: [ALPS00336833] When orientation changed, request layout to avoid status bar layout error. @}

        updateDisplaySize(); // populates mDisplayMetrics

        Log.v(TAG, "onConfigurationChanged dump config information start ");
        Log.v(TAG, " widthPixels = " + mDisplayMetrics.widthPixels);
        Log.v(TAG, " heightPixels = " + mDisplayMetrics.heightPixels);
        Log.v(TAG, " orientation = " + newConfig.orientation);
        Log.v(TAG, " config = " + newConfig.toString());
        Log.v(TAG, "onConfigurationChanged dump config information end ");

        updateResources();
        repositionNavigationBar();
        updateExpandedViewPos(EXPANDED_LEAVE_ALONE);
        updateShowSearchHoldoff();
    }

    @Override
    public void userSwitched(int newUserId) {
        if (MULTIUSER_DEBUG) mNotificationPanelDebugText.setText("USER " + newUserId);
        animateCollapsePanels();
        updateNotificationIcons();
        resetUserSetupObserver();
    }

    private void resetUserSetupObserver() {
        mContext.getContentResolver().unregisterContentObserver(mUserSetupObserver);
        mUserSetupObserver.onChange(false);
        mContext.getContentResolver().registerContentObserver(
                Settings.Secure.getUriFor(Settings.Secure.USER_SETUP_COMPLETE), true,
                mUserSetupObserver,
                mCurrentUserId);
    }

    private void setHeadsUpVisibility(boolean vis) {
        if (!ENABLE_HEADS_UP) return;
        if (DEBUG) Log.v(TAG, (vis ? "showing" : "hiding") + " heads up window");
        mHeadsUpNotificationView.setVisibility(vis ? View.VISIBLE : View.GONE);
        if (!vis) {
            if (DEBUG) Log.d(TAG, "setting heads up entry to null");
            mInterruptingNotificationEntry = null;
        }
    }

    public void animateHeadsUp(boolean animateInto, float frac) {
        if (!ENABLE_HEADS_UP || mHeadsUpNotificationView == null) return;
        frac = frac / 0.4f;
        frac = frac < 1.0f ? frac : 1.0f;
        float alpha = 1.0f - frac;
        float offset = mHeadsUpVerticalOffset * frac;
        offset = animateInto ? offset : 0f;
        mHeadsUpNotificationView.setAlpha(alpha);
        mHeadsUpNotificationView.setY(offset);
    }

    public void onHeadsUpDismissed() {
        if (mInterruptingNotificationEntry == null) return;
        mHandler.sendEmptyMessage(MSG_HIDE_HEADS_UP);
        if (mHeadsUpNotificationView.isClearable()) {
            try {
                mBarService.onNotificationClear(
                        mInterruptingNotificationEntry.notification.getPackageName(),
                        mInterruptingNotificationEntry.notification.getTag(),
                        mInterruptingNotificationEntry.notification.getId());
            } catch (android.os.RemoteException ex) {
                // oh well
            }
        }
    }

    /**
     * Reload some of our resources when the configuration changes.
     *
     * We don't reload everything when the configuration changes -- we probably
     * should, but getting that smooth is tough.  Someday we'll fix that.  In the
     * meantime, just update the things that we know change.
     */
    void updateResources() {
        final Context context = mContext;
        final Resources res = context.getResources();

        if (mClearButton instanceof TextView) {
            ((TextView)mClearButton).setText(context.getText(R.string.status_bar_clear_all_button));
        }
        /// M: [SystemUI] Support "Notification toolbar". {
        Xlog.d(TAG, "updateResources");
        mToolBarView.updateResources();
        /// M: [SystemUI] Support "Notification toolbar". }

        // Update the QuickSettings container
        if (mQS != null) mQS.updateResources();

        loadDimens();
    }

    protected void loadDimens() {
        final Resources res = mContext.getResources();

        mNaturalBarHeight = res.getDimensionPixelSize(
                com.android.internal.R.dimen.status_bar_height);

        int newIconSize = res.getDimensionPixelSize(
            com.android.internal.R.dimen.status_bar_icon_size);
        int newIconHPadding = res.getDimensionPixelSize(
            R.dimen.status_bar_icon_padding);

        if (newIconHPadding != mIconHPadding || newIconSize != mIconSize) {
//            Log.d(TAG, "size=" + newIconSize + " padding=" + newIconHPadding);
            mIconHPadding = newIconHPadding;
            mIconSize = newIconSize;
            //reloadAllNotificationIcons(); // reload the tray
        }

        mEdgeBorder = res.getDimensionPixelSize(R.dimen.status_bar_edge_ignore);

        mSelfExpandVelocityPx = res.getDimension(R.dimen.self_expand_velocity);
        mSelfCollapseVelocityPx = res.getDimension(R.dimen.self_collapse_velocity);
        mFlingExpandMinVelocityPx = res.getDimension(R.dimen.fling_expand_min_velocity);
        mFlingCollapseMinVelocityPx = res.getDimension(R.dimen.fling_collapse_min_velocity);

        mCollapseMinDisplayFraction = res.getFraction(R.dimen.collapse_min_display_fraction, 1, 1);
        mExpandMinDisplayFraction = res.getFraction(R.dimen.expand_min_display_fraction, 1, 1);

        mExpandAccelPx = res.getDimension(R.dimen.expand_accel);
        mCollapseAccelPx = res.getDimension(R.dimen.collapse_accel);

        mFlingGestureMaxXVelocityPx = res.getDimension(R.dimen.fling_gesture_max_x_velocity);

        mFlingGestureMaxOutputVelocityPx = res.getDimension(R.dimen.fling_gesture_max_output_velocity);

        mNotificationPanelMarginBottomPx
            = (int) res.getDimension(R.dimen.notification_panel_margin_bottom);
        mNotificationPanelMarginPx
            = (int) res.getDimension(R.dimen.notification_panel_margin_left);
        mNotificationPanelGravity = res.getInteger(R.integer.notification_panel_layout_gravity);
        if (mNotificationPanelGravity <= 0) {
            mNotificationPanelGravity = Gravity.START | Gravity.TOP;
        }
        mSettingsPanelGravity = res.getInteger(R.integer.settings_panel_layout_gravity);
        Log.d(TAG, "mSettingsPanelGravity = " + mSettingsPanelGravity);
        if (mSettingsPanelGravity <= 0) {
            mSettingsPanelGravity = Gravity.END | Gravity.TOP;
        }

        mCarrierLabelHeight = res.getDimensionPixelSize(R.dimen.carrier_label_height);
        mNotificationHeaderHeight = res.getDimensionPixelSize(R.dimen.notification_panel_header_height);
        /// M: Calculate ToolBar height when sim indicator is showing.
        mToolBarViewHeight = res.getDimensionPixelSize(R.dimen.toolbar_height);

        mNotificationPanelMinHeightFrac = res.getFraction(R.dimen.notification_panel_min_height_frac, 1, 1);
        if (mNotificationPanelMinHeightFrac < 0f || mNotificationPanelMinHeightFrac > 1f) {
            mNotificationPanelMinHeightFrac = 0f;
        }

        mHeadsUpNotificationDecay = res.getInteger(R.integer.heads_up_notification_decay);
        mRowHeight =  res.getDimensionPixelSize(R.dimen.notification_row_min_height);

        if (false) Log.v(TAG, "updateResources");
    }

    //
    // tracing
    //

    void postStartTracing() {
        mHandler.postDelayed(mStartTracing, 3000);
    }

    void vibrate() {
        android.os.Vibrator vib = (android.os.Vibrator)mContext.getSystemService(
                Context.VIBRATOR_SERVICE);
        vib.vibrate(250);
    }

    Runnable mStartTracing = new Runnable() {
        public void run() {
            vibrate();
            SystemClock.sleep(250);
            Log.d(TAG, "startTracing");
            android.os.Debug.startMethodTracing("/data/statusbar-traces/trace");
            mHandler.postDelayed(mStopTracing, 10000);
        }
    };

    Runnable mStopTracing = new Runnable() {
        public void run() {
            android.os.Debug.stopMethodTracing();
            Log.d(TAG, "stopTracing");
            vibrate();
        }
    };

    @Override
    protected void haltTicker() {
        mTicker.halt();
    }

    @Override
    protected boolean shouldDisableNavbarGestures() {
        return !isDeviceProvisioned()
                || mExpandedVisible
                || (mDisabled & StatusBarManager.DISABLE_SEARCH) != 0;
    }

    private static class FastColorDrawable extends Drawable {
        private final int mColor;

        public FastColorDrawable(int color) {
            mColor = 0xff000000 | color;
        }

        @Override
        public void draw(Canvas canvas) {
            canvas.drawColor(mColor, PorterDuff.Mode.SRC);
        }

        @Override
        public void setAlpha(int alpha) {
        }

        @Override
        public void setColorFilter(ColorFilter cf) {
        }

        @Override
        public int getOpacity() {
            return PixelFormat.OPAQUE;
        }

        @Override
        public void setBounds(int left, int top, int right, int bottom) {
        }

        @Override
        public void setBounds(Rect bounds) {
        }
    }

    @Override
    public void destroy() {
        super.destroy();
        if (mStatusBarWindow != null) {
            mWindowManager.removeViewImmediate(mStatusBarWindow);
        }
        if (mNavigationBarView != null) {
            mWindowManager.removeViewImmediate(mNavigationBarView);
        }
        mContext.unregisterReceiver(mBroadcastReceiver);
    }

    private boolean mDemoModeAllowed;
    private boolean mDemoMode;
    private DemoStatusIcons mDemoStatusIcons;

    @Override
    public void dispatchDemoCommand(String command, Bundle args) {
        if (!mDemoModeAllowed) {
            mDemoModeAllowed = Settings.Global.getInt(mContext.getContentResolver(),
                    "sysui_demo_allowed", 0) != 0;
        }
        if (!mDemoModeAllowed) return;
        if (command.equals(COMMAND_ENTER)) {
            mDemoMode = true;
        } else if (command.equals(COMMAND_EXIT)) {
            mDemoMode = false;
            checkBarModes();
        } else if (!mDemoMode) {
            // automatically enter demo mode on first demo command
            dispatchDemoCommand(COMMAND_ENTER, new Bundle());
        }
        boolean modeChange = command.equals(COMMAND_ENTER) || command.equals(COMMAND_EXIT);
        if (modeChange || command.equals(COMMAND_CLOCK)) {
            dispatchDemoCommandToView(command, args, R.id.clock);
        }
        if (modeChange || command.equals(COMMAND_BATTERY)) {
            dispatchDemoCommandToView(command, args, R.id.battery);
        }
        if (modeChange || command.equals(COMMAND_STATUS)) {
            if (mDemoStatusIcons == null) {
                mDemoStatusIcons = new DemoStatusIcons(mStatusIcons, mIconSize);
            }
            mDemoStatusIcons.dispatchDemoCommand(command, args);
        }
        if (mNetworkController != null && (modeChange || command.equals(COMMAND_NETWORK))) {
            mNetworkController.dispatchDemoCommand(command, args);
        }
        if (command.equals(COMMAND_BARS)) {
            String mode = args.getString("mode");
            int barMode = "opaque".equals(mode) ? MODE_OPAQUE :
                    "translucent".equals(mode) ? MODE_TRANSLUCENT :
                    "semi-transparent".equals(mode) ? MODE_SEMI_TRANSPARENT :
                    -1;
            if (barMode != -1) {
                boolean animate = true;
                if (mStatusBarView != null) {
                    mStatusBarView.getBarTransitions().transitionTo(barMode, animate);
                }
                if (mNavigationBarView != null) {
                    mNavigationBarView.getBarTransitions().transitionTo(barMode, animate);
                }
            }
        }
    }

    private void dispatchDemoCommandToView(String command, Bundle args, int id) {
        if (mStatusBarView == null) return;
        View v = mStatusBarView.findViewById(id);
        if (v instanceof DemoMode) {
            ((DemoMode)v).dispatchDemoCommand(command, args);
        }
    }


    /// M: Support "SIM Indicator".
    private ImageView mSimIndicatorIcon;
    /// M: For AT&T
    private TextView mPlmnLabel;
    /// M: Calculate ToolBar height when sim indicator is showing.
    private int mToolBarViewHeight;
    /// M: Support "Change font size of phone".
    private float mPreviousConfigFontScale;
    /// M: [ALPS00336833] When orientation changed, request layout to avoid status bar layout error. @{
    boolean mNeedRelayout = false;
    private int mPrevioutConfigOrientation;
    /// M: [ALPS00336833] When orientation changed, request layout to avoid status bar layout error. @}
    /// M: Support AirplaneMode for Statusbar SimIndicator.
    private static final String ACTION_BOOT_IPO
            = "android.intent.action.ACTION_PREBOOT_IPO";
    /// M: [SystemUI] Dismiss new event icon when click clear button for keyguard.
    private static final String CLEAR_NEW_EVENT_VIEW_INTENT = "android.intent.action.KEYGUARD_CLEAR_UREAD_TIPS";

    /// M: [SystemUI] Support "Dual SIM". @{

    /// M: Support GeminiPlus
    private CarrierLabel mCarrier1 = null;
    private CarrierLabel mCarrier2 = null;
    private CarrierLabel mCarrier3 = null;
    private CarrierLabel mCarrier4 = null;
    private View mCarrierDivider = null;
    private View mCarrierDivider2 = null;
    private View mCarrierDivider3 = null;

    private LinearLayout mCarrierLabelGemini = null;

    private BroadcastReceiver mSIMInfoReceiver = new BroadcastReceiver() {
        public void onReceive(final Context context, final Intent intent) {
            String action = intent.getAction();
            Xlog.d(TAG, "onReceive, intent action is " + action + ".");
            if (action.equals(Intent.SIM_SETTINGS_INFO_CHANGED)) {
                mHandler.post(new Runnable() {
                    public void run() {
                        SIMHelper.updateSIMInfos(context);
                        int type = intent.getIntExtra("type", -1);
                        long simId = intent.getLongExtra("simid", -1);
                        if (type == 0 || type == 1) {
                            // name and color changed
                            updateNotificationsSimInfo(simId);
                        }
                        // update ToolBarView's panel views
                        mToolBarView.updateSimInfos(intent);
                        if (mQS != null) {
                            mQS.updateSimInfo(intent);
                        }
                    }
                });
            } else if (action.equals(TelephonyIntents.ACTION_SIM_INSERTED_STATUS)
                    || action.equals(TelephonyIntents.ACTION_SIM_INFO_UPDATE)) {
                mHandler.post(new Runnable() {
                    public void run() {
                        SIMHelper.updateSIMInfos(context);
                    }
                });
                updateSimIndicator();
            } else if (action.equals(Intent.ACTION_AIRPLANE_MODE_CHANGED)) {
                updateAirplaneMode();
            } else if (action.equals(ACTION_BOOT_IPO)) {
                if (mSimIndicatorIcon != null) {
                    mSimIndicatorIcon.setVisibility(View.GONE);
                }
            } else if (action.equals(Intent.ACTION_VOICE_CALL_DEFAULT_SIM_CHANGED)
                    || action.equals(Intent.ACTION_SMS_DEFAULT_SIM_CHANGED)) {
                /// M: [ALPS01234409][KK] Update sim indicator if default sim chagned.
                updateSimIndicator();
            } else if (action.equals(TelephonyIntents.ACTION_SIM_INDICATOR_STATE_CHANGED)) { ///for AT&T
                int simStatus = intent.getIntExtra(TelephonyIntents.INTENT_KEY_ICC_STATE, -1);
                if (simStatus == PhoneConstants.SIM_INDICATOR_SEARCHING) {
                    Xlog.d(TAG, "updateSIMState. simStatus is " + simStatus);
                    updatePLMNSearchingStateView(true);
                } else {
                    updatePLMNSearchingStateView(false);
                }
            } else if ("android.intent.action.ACTION_BOOT_IPO".equals(action)) {
                /// M: ALPS00349274 to hide navigation bar when ipo shut down to avoid it flash when in boot ipo mode.{
                if (mNavigationBarView != null) {
                    View view = mNavigationBarView.findViewById(R.id.rot0);
                    if (view != null && view.getVisibility() != View.GONE) {
                        Xlog.d(TAG, "receive android.intent.action.ACTION_BOOT_IPO to set mNavigationBarView visible");
                        view.setVisibility(View.VISIBLE);
                    }
                }
            } else if ("android.intent.action.ACTION_SHUTDOWN_IPO".equals(action)) {
                if (mNavigationBarView != null) {
                    Xlog.d(TAG, "receive android.intent.action.ACTION_SHUTDOWN_IPO to set mNavigationBarView invisible");
                    mNavigationBarView.hideForIPOShutdown();
                }
                /// M: ALPS00349274 to hide navigation bar when ipo shut down to avoid it flash when in boot ipo mode.}
            } else if (Telephony.Intents.SPN_STRINGS_UPDATED_ACTION.equals(action)) {
                /// M: [SystemUI] Support "Dual SIM PLMN Change". @{
                if (mShowCarrierInPanel) {
                    if (!FeatureOption.MTK_GEMINI_SUPPORT) {
                        /// M: For AT&T
                        if (!PluginFactory.getStatusBarPlugin(context)
                                .isHspaDataDistinguishable() &&
                                !PluginFactory.getStatusBarPlugin(context)
                                .supportDataTypeAlwaysDisplayWhileOn()) {
                            updateNetworkName(
                                    intent.getBooleanExtra(Telephony.Intents.EXTRA_SHOW_SPN, false),
                                    intent.getStringExtra(Telephony.Intents.EXTRA_SPN),
                                    intent.getBooleanExtra(Telephony.Intents.EXTRA_SHOW_PLMN, false),
                                    intent.getStringExtra(Telephony.Intents.EXTRA_PLMN));
                        }
                    }

                    if (SIMHelper.isMediatekLteDcSupport()) {
                        final int slotId = LteDcController.getInstance(mContext).getLteDcEnabledSlotId();
                        final int tempSlotId = 
                            intent.getIntExtra(PhoneConstants.GEMINI_SIM_ID_KEY, PhoneConstants.GEMINI_SIM_1);
                        Log.d(TAG, "LteDcController nn enable slotId = " + slotId + 
                            ", SPN slotId =" + tempSlotId);
                        if (slotId == tempSlotId
                            && slotId != LteDcController.INVALID_SLOT_ID) {
                            Log.d(TAG, "LteDcController backup NetworkName");
                            LteDcController.getInstance(mContext).setNetworkNameTag(intent, slotId);
                            return;
                        }
                    }

                    if (FeatureOption.MTK_GEMINI_SUPPORT) {
                        final int tempSimId = intent.getIntExtra(PhoneConstants.GEMINI_SIM_ID_KEY, PhoneConstants.GEMINI_SIM_1);
                        /// M: Support GeminiPlus
                        for (int childIdx = 0; childIdx < mCarrierLabelGemini.getChildCount(); childIdx++) {
                            final View mChildView = mCarrierLabelGemini.getChildAt(childIdx);
                            if(mChildView instanceof CarrierLabel) {
                                CarrierLabel mChildCarrier = (CarrierLabel) mChildView;
                                if (tempSimId == mChildCarrier.getSlotId()) {
                                    mChildCarrier.setText(SIMHelper.getNetworkName(context, intent));
                                }
                            }
                        }
                    } else {
                        mCarrierLabel.setText(SIMHelper.getNetworkName(context, intent));
                    }
                }
            }
            /// M: [SystemUI] Support "Dual SIM PLMN Change". }@
        }
    };

    private void updateNotificationsSimInfo(long simId) {
        Xlog.d(TAG, "updateNotificationsSimInfo, the simId is " + simId + ".");
        if (simId == -1) {
            return;
        }
        SimInfoManager.SimInfoRecord simInfo = SIMHelper.getSIMInfo(mContext, simId);
        if (simInfo == null) {
            Xlog.d(TAG, "updateNotificationsSimInfo, the simInfo is null.");
            return;
        }
        for (int i = 0, n = this.mNotificationData.size(); i < n; i++) {
            Entry entry = this.mNotificationData.get(i);
            updateNotificationSimInfo(simInfo, entry.notification.getNotification(), entry.icon, entry.expanded);
        }
    }

    private void updateNotificationSimInfo(SimInfoManager.SimInfoRecord simInfo, Notification n, StatusBarIconView iconView, View itemView) {
        if (n.simId != simInfo.mSimInfoId) {
            return;
        }
        int simInfoType = n.simInfoType;
        if (iconView == null) { //for update SimIndicatorView
            for (int i=0; i<mNotificationIcons.getChildCount(); i++) {
                View child = mNotificationIcons.getChildAt(i);
                if (child instanceof StatusBarIconView) {
                    StatusBarIconView iconViewtemp = (StatusBarIconView) child;
                    if(iconViewtemp.getNotificationSimId() == n.simId){
                        iconView = iconViewtemp;
                        break;
                    }
                }
            }
        }
        // icon part.
//        if ((simInfoType == 2 || simInfoType == 3) && simInfo != null && iconView != null) {
//            Xlog.d(TAG, "updateNotificationSimInfo, add sim info to status bar.");
//            Drawable drawable = iconView.getResources().getDrawable(simInfo.mSimBackgroundRes);
//           if (drawable != null) {
//                iconView.setSimInfoBackground(drawable);
//                iconView.invalidate();
//            }
//        }
        // item part.
        if ((simInfoType == 1 || simInfoType == 3) && simInfo != null && (simInfo.mColor >= 0 && simInfo.mColor < SimInfoManager.SimBackgroundRes.length)) {
            Xlog.d(TAG, "updateNotificationSimInfo, update sim info to notification item. simInfo.mColor = " + simInfo.mColor);
            View simIndicatorLayout = itemView.findViewById(com.android.internal.R.id.notification_sim_indicator);
            simIndicatorLayout.setVisibility(View.VISIBLE);
            ImageView bgView = (ImageView) itemView.findViewById(com.android.internal.R.id.notification_sim_indicator_bg);
            /// M: For SIM Backgronud. @{
            if (!PluginFactory.getStatusBarPlugin(mContext).supportDataConnectInTheFront()) {
                bgView.setBackground(mContext.getResources().getDrawable(
                                TelephonyIcons.SIM_INDICATOR_BACKGROUND_NOTIFICATION[simInfo.mColor]));
            } else {
                int drawableId = TelephonyIcons.SIM_INDICATOR_BACKGROUND_NOTIFICATION_CT[simInfo.mColor];
                if (drawableId > 0) {
                    bgView.setBackground(mContext.getResources().getDrawable(drawableId));
                } else {
                    Xlog.d(TAG,"updateNotificationSimInfo, Wrong SIM Color!");
                }
            }
            /// @}
            bgView.setVisibility(View.VISIBLE);
        } else {
            View simIndicatorLayout = itemView.findViewById(com.android.internal.R.id.notification_sim_indicator);
            simIndicatorLayout.setVisibility(View.VISIBLE);
            View bgView = itemView.findViewById(com.android.internal.R.id.notification_sim_indicator_bg);
            bgView.setVisibility(View.GONE);
        }
    }

    public void updateNotificationSimInfo(NotificationData.Entry entry) {
        if (FeatureOption.MTK_GEMINI_SUPPORT) {
            /// M: Support SIM Info Notification.
            // process SIM info of notification.
            StatusBarNotification sbn = entry.notification;
            int simInfoType = sbn.getNotification().simInfoType;
            long simId = sbn.getNotification().simId;          
            if ((simInfoType >= 1 || simInfoType <= 3) && simId > 0) {
                SimInfoManager.SimInfoRecord simInfo = SIMHelper.getSIMInfo(mContext, simId);
                if (simInfo != null) {
                    updateNotificationSimInfo(simInfo, sbn.getNotification(), entry.icon, entry.expanded);
                }
            }            
        }
    }

    /// M: [SystemUI] Support "Dual SIM". @}

    /// M: [SystemUI] Support "Notification toolbar". @{
    private ToolBarView mToolBarView;
    private View mToolBarSwitchPanel;
    /// M: [SystemUI] Support "Notification toolbar". @}

    /// M: [SystemUI] Support "SIM indicator". @{

    private boolean mIsSimIndicatorShowing = false;
    private String mBusinessType = null;
    public void showSimIndicator(String businessType) {
        if (mIsSimIndicatorShowing) {
            hideSimIndicator();
        }
        mBusinessType = businessType;
        long simId = SIMHelper.getDefaultSIM(mContext, businessType);
        Xlog.d(TAG, "showSimIndicator, show SIM indicator which business is " + businessType + "  simId = "+simId+".");
        if (simId == android.provider.Settings.System.DEFAULT_SIM_SETTING_ALWAYS_ASK) {
            List<SimInfoManager.SimInfoRecord> simInfos = SIMHelper.getSIMInfoList(mContext);
            if (simInfos != null && simInfos.size() > 0) {
                showAlwaysAskOrInternetCall(simId);
                mToolBarView.showSimSwithPanel(businessType);
            }
        } else if (businessType.equals(android.provider.Settings.System.VOICE_CALL_SIM_SETTING)
                && simId == android.provider.Settings.System.VOICE_CALL_SIM_SETTING_INTERNET) {
            showAlwaysAskOrInternetCall(simId);
            mToolBarView.showSimSwithPanel(businessType);
        } else if (simId == android.provider.Settings.System.SMS_SIM_SETTING_AUTO) {
            List<SimInfoManager.SimInfoRecord> simInfos = SIMHelper.getSIMInfoList(mContext);
            if (simInfos != null && simInfos.size() > 0) {
                showAlwaysAskOrInternetCall(simId);
                mToolBarView.showSimSwithPanel(businessType);
            }
        } else {
            mSimIndicatorIconShow = false;
            List<SimInfoManager.SimInfoRecord> simInfos = SIMHelper.getSIMInfoList(mContext);
            if (simInfos == null) {
                return;
            }
            int slot = 0;
            for (int i = 0; i < simInfos.size(); i++) {
                if (simInfos.get(i).mSimInfoId == simId) {
                    slot = simInfos.get(i).mSimSlotId;
                    break;
                }
            }
            if (simInfos.size() == 1) {
                if (businessType.equals(android.provider.Settings.System.VOICE_CALL_SIM_SETTING)
                        && isInternetCallEnabled(mContext)) {
                    mNetworkController.showSimIndicator(slot);
                    mToolBarView.showSimSwithPanel(businessType);
                }
            } else if (simInfos.size() > 1) {
                mNetworkController.showSimIndicator(slot);
                mToolBarView.showSimSwithPanel(businessType);
            }
        }
        mIsSimIndicatorShowing = true;
    }

    public void hideSimIndicator() {
        Xlog.d(TAG, "hideSimIndicator SIM indicator.mBusinessType = " + mBusinessType);
        if (mBusinessType == null) return;
        dismissAppGuideInt();
        long simId = SIMHelper.getDefaultSIM(mContext, mBusinessType);
        Xlog.d(TAG, "hideSimIndicator, hide SIM indicator simId = "+simId+".");
        mSimIndicatorIcon.setVisibility(View.GONE);

        for (int i=PhoneConstants.GEMINI_SIM_1; i < SIMHelper.getNumOfSim(); i++) {
            mNetworkController.hideSimIndicator(i);
        }

        mToolBarView.hideSimSwithPanel();
        mIsSimIndicatorShowing = false;
        mSimIndicatorIconShow = false;
    }

    private boolean mAirplaneMode = false;
    private boolean mSimIndicatorIconShow = false;

    private void updateAirplaneMode() {
        mAirplaneMode = (Settings.System.getInt(mContext.getContentResolver(),
            Settings.Global.AIRPLANE_MODE_ON, 0) == 1);
        if (mSimIndicatorIcon != null) {
            mSimIndicatorIcon.setVisibility(mSimIndicatorIconShow && !mAirplaneMode ? View.VISIBLE : View.GONE);
        }
    }

    private void updateSimIndicator() {
        Xlog.d(TAG, "updateSimIndicator mIsSimIndicatorShowing = " + mIsSimIndicatorShowing + " mBusinessType is "
                + mBusinessType);
        if (mIsSimIndicatorShowing && mBusinessType != null) {
            showSimIndicator(mBusinessType);
        }
        if (mSimIndicatorIconShow && mBusinessType != null) {
            long simId = SIMHelper.getDefaultSIM(mContext, mBusinessType);
            if (mSimIndicatorIcon != null && simId != android.provider.Settings.System.DEFAULT_SIM_SETTING_ALWAYS_ASK
                    && simId != android.provider.Settings.System.VOICE_CALL_SIM_SETTING_INTERNET
                    && simId != android.provider.Settings.System.SMS_SIM_SETTING_AUTO) {
                mSimIndicatorIcon.setVisibility(View.GONE);
            }
        }
    }

    private void showAlwaysAskOrInternetCall(long simId) {
        mSimIndicatorIconShow = true;
        if (simId == android.provider.Settings.System.VOICE_CALL_SIM_SETTING_INTERNET) {
            mSimIndicatorIcon.setBackgroundResource(R.drawable.sim_indicator_internet_call);
        } else if (simId == android.provider.Settings.System.SMS_SIM_SETTING_AUTO) {
            mSimIndicatorIcon.setBackgroundResource(R.drawable.sim_indicator_auto);
        } else {
            mSimIndicatorIcon.setBackgroundResource(R.drawable.sim_indicator_always_ask);
        }
        if (!mAirplaneMode) {
            mSimIndicatorIcon.setVisibility(View.VISIBLE);
        } else {
            mSimIndicatorIcon.setVisibility(View.GONE);
            mSimIndicatorIconShow = false;
        }
    }

    private static boolean isInternetCallEnabled(Context context) {
        return Settings.System.getInt(context.getContentResolver(), Settings.System.ENABLE_INTERNET_CALL, 0) == 1;
    }

    /// M: [SystemUI] Support "SIM Indicator". }@

    /// M: [SystemUI]Show application guide for App. @{
    private Dialog mAppGuideDialog;
    private Button mAppGuideButton;
    private String mAppName;
    private View mAppGuideView;
    private static final String SHOW_APP_GUIDE_SETTING = "settings";
    private static final String MMS = "MMS";
    private static final String PHONE = "PHONE";
    private static final String CONTACTS = "CONTACTS";
    private static final String MMS_SHOW_GUIDE = "mms_show_guide";
    private static final String PHONE_SHOW_GUIDE = "phone_show_guide";
    private static final String CONTACTS_SHOW_GUIDE = "contacts_show_guide";

   // M: To expand slowly than usual.
    private void animateExpandNotificationsPanelSlow() {
        Log.d(TAG, "animateExpandSlow: mExpandedVisible=" + mExpandedVisible);
        if ((mDisabled & StatusBarManager.DISABLE_EXPAND) != 0) {
            return ;
        }

        mNotificationPanel.expandSlow();
        if (mHasFlipSettings && mScrollView.getVisibility() != View.VISIBLE) {
            flipToNotifications();
        }

        if (false) postStartTracing();
    }

    public void showApplicationGuide(String appName) {
        SharedPreferences settings = mContext.getSharedPreferences(SHOW_APP_GUIDE_SETTING, 0);
        mAppName = appName;
        Xlog.d(TAG, "showApplicationGuide appName = " + appName);
        if ((MMS.equals(appName) && "1".equals(settings.getString(MMS_SHOW_GUIDE, "1"))) 
          || (PHONE.equals(appName) && "1".equals(settings.getString(PHONE_SHOW_GUIDE, "1"))) 
          || (CONTACTS.equals(appName) && "1".equals(settings.getString(CONTACTS_SHOW_GUIDE, "1")))) {
            mHandler.sendEmptyMessageDelayed(MSG_OPEN_NOTIFICATION_PANEL_SLOW, 100);
        }
    }

    private void dismissAppGuideInt() {
        Xlog.d(TAG, "dismissAppGuideInt");
        mHandler.removeMessages(MSG_OPEN_NOTIFICATION_PANEL_SLOW);
        if (mAppGuideDialog != null) {
            mAppGuideDialog.dismiss();
            Xlog.d(TAG, "dismissAppGuideInt.dismiss()");
        }
    }

    public void createAndShowAppGuideDialog() {
        Xlog.d(TAG, "createAndShowAppGuideDialog");
        if ((mDisabled & StatusBarManager.DISABLE_EXPAND) != 0) {
            Xlog.d(TAG, "StatusBar can not expand, so return.");
            return;
        }
        mAppGuideDialog = new ApplicationGuideDialog(mContext, R.style.ApplicationGuideDialog);
        mAppGuideDialog.getWindow().setType(WindowManager.LayoutParams.TYPE_STATUS_BAR_PANEL);
        /// M: [ALPS01234354][KK] Avoid IME to get the focus.
        mAppGuideDialog.getWindow().setFlags(WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM,
                WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM);
        animateExpandNotificationsPanelSlow();
        mAppGuideDialog.show();
        ObjectAnimator oa = ObjectAnimator.ofFloat(mAppGuideView, "alpha", 0.0f, 1.0f);
        oa.setDuration(1500);
        oa.start();
    }

    private class ApplicationGuideDialog extends Dialog {

        public ApplicationGuideDialog(Context context, int theme) {
            super(context, theme);
        }

        @Override
        protected void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            mAppGuideView = View.inflate(mContext, R.layout.application_guide, null);
            setContentView(mAppGuideView);
            mAppGuideButton = (Button) mAppGuideView.findViewById(R.id.appGuideBtn);
            mAppGuideButton.setOnClickListener(mAppGuideBtnListener);
        }

        @Override
        public boolean onTouchEvent(MotionEvent e){
            if (mToolBarView.mSimSwitchPanelView.isInsideSimIconView(e.getX(), e.getY())) {
                mStatusBarWindow.dispatchTouchEvent(e);
                if (e.getAction() == MotionEvent.ACTION_UP) {
                    mAppGuideDialog.dismiss();
                    appGuideDismissCommit();
                }
            }
            return true; //consume all touch evt
        }

        @Override
        public void onBackPressed() {
            mAppGuideDialog.dismiss();
            animateCollapsePanels();
            super.onBackPressed();
        }

    }

    private View.OnClickListener mAppGuideBtnListener = new View.OnClickListener() {
        public void onClick(View v) {
            Xlog.d(TAG, "onClick! dimiss application guide dialog.");
            mAppGuideDialog.dismiss();
            animateCollapsePanels();
            appGuideDismissCommit();
        }
    };

    private void appGuideDismissCommit(){
        SharedPreferences settings = mContext.getSharedPreferences(SHOW_APP_GUIDE_SETTING, 0);
        SharedPreferences.Editor editor = settings.edit();
        if (MMS.equals(mAppName)) {
            editor.putString(MMS_SHOW_GUIDE, "0");
            editor.commit();
        } else if (PHONE.equals(mAppName)) {
            editor.putString(PHONE_SHOW_GUIDE, "0");
            editor.commit();
        } else if (CONTACTS.equals(mAppName)) {
            editor.putString(CONTACTS_SHOW_GUIDE, "0");
            editor.commit();
        }
    }

    public void dismissAppGuide() {
        if (mAppGuideDialog != null && mAppGuideDialog.isShowing()) {
            Xlog.d(TAG, "dismiss app guide dialog");
            mAppGuideDialog.dismiss();
            mNotificationPanel.cancelTimeAnimator();
            makeExpandedInvisible();
        }
    }

    private void refreshApplicationGuide() {
        if (mAppGuideDialog != null) {
            mAppGuideView = View.inflate(mContext, R.layout.application_guide, null);
            mAppGuideDialog.setContentView(mAppGuideView);
            mAppGuideButton = (Button) mAppGuideView.findViewById(R.id.appGuideBtn);
            mAppGuideButton.setOnClickListener(mAppGuideBtnListener);
        }
    }
    /// M: [SystemUI]Show application guide for App. @}

    /// M: [SystemUI]Support ThemeManager. @{
    private void refreshExpandedView(Context context) {
        for (int i = 0, n = this.mNotificationData.size(); i < n; i++) {
            Entry entry = this.mNotificationData.get(i);
            inflateViews(entry, mPile);
        }
        loadNotificationShade();
        updateExpansionStates();
        setAreThereNotifications();
        mNotificationPanel.onFinishInflate();
        if (mSettingsPanel != null) {
            mSettingsPanel.onFinishInflate();
        }
        mToolBarView.mSimSwitchPanelView.updateSimInfo();
        if (mHasFlipSettings) {
            ImageView notificationButton = (ImageView) mStatusBarWindow.findViewById(R.id.notification_button);
            if (notificationButton != null) {
                notificationButton.setImageDrawable(context.getResources().getDrawable(R.drawable.ic_notifications));
            }
        }
        if (mHasSettingsPanel) {
            if (mStatusBarView.hasFullWidthNotifications()) {
                ImageView settingsButton = (ImageView) mStatusBarWindow.findViewById(R.id.settings_button);
                settingsButton.setImageDrawable(context.getResources()
                        .getDrawable(R.drawable.ic_notify_quicksettings));
            }
        } else {
            ImageView settingsButton = (ImageView) mStatusBarWindow.findViewById(R.id.settings_button);
            settingsButton.setImageDrawable(context.getResources().getDrawable(R.drawable.ic_notify_settings));
        }
        ImageView clearButton = (ImageView) mStatusBarWindow.findViewById(R.id.clear_all_button);
        clearButton.setImageDrawable(context.getResources().getDrawable(R.drawable.ic_notify_clear));
    }
    /// M: [SystemUI]Support ThemeManager. @}

    /// M: Request Transient Statusbar.
    private boolean ENABLE_REQUEST_TRANSIENT_STATUSBAR = true;
    private void requestTransientStatusbar() {
        Intent intent = new Intent(Intent.MTK_ACTION_REQUEST_TRANSIENT_STATUSBAR);
        mContext.sendBroadcast(intent);
    }

    /// M: For AT&T @{
    private String mOldPlmn = null;
    private void updateNetworkName(boolean showSpn, String spn, boolean showPlmn, String plmn) {
        Xlog.d(TAG, "For AT&T updateNetworkName, showSpn=" + showSpn + " spn=" + spn + " showPlmn=" + showPlmn + " plmn=" + plmn);
        StringBuilder str = new StringBuilder();
        boolean something = false;
        if (showPlmn && plmn != null) {
            str.append(plmn);
            something = true;
        }
        if (showSpn && spn != null) {
            if (something) {
                str.append(mContext.getString(R.string.status_bar_network_name_separator));
            }
            str.append(spn);
            something = true;
        }
        if (something) {
            mOldPlmn = str.toString();
        } else {
            mOldPlmn = mContext.getResources().getString(com.android.internal.R.string.lockscreen_carrier_default);
        }
    }

    private void updatePLMNSearchingStateView(boolean searching) {
        if(searching) {
            mPlmnLabel.setText(R.string.plmn_searching);
        } else {
            mPlmnLabel.setText(mOldPlmn);
        }
        mPlmnLabel.setVisibility(View.VISIBLE);
    }
    /// M: For AT&T.}@

    private void setRunningInTest(boolean bEnable) {
        AutoTestHelper.setRunningInTest(bEnable);
    }

    /// M: [ALPS00512845] Handle SD Swap Condition
    private static final boolean SUPPORT_SD_SWAP = true;
    private ArrayList<IBinder> mNeedRemoveKeys;
    private boolean mAvoidSDAppAddNotification;
    private static final String EXTERNAL_SD0 = (FeatureOption.MTK_SHARED_SDCARD && !FeatureOption.MTK_2SDCARD_SWAP) ? "/storage/emulated/0" : "/storage/sdcard0";
    private static final String EXTERNAL_SD1 = "/storage/sdcard1";

    private BroadcastReceiver mMediaEjectBroadcastReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            StorageVolume storageVolume = (StorageVolume) intent.getParcelableExtra(StorageVolume.EXTRA_STORAGE_VOLUME);
            if (storageVolume == null) {
                return;
            }
            String path = storageVolume.getPath();
            if (!EXTERNAL_SD0.equals(path) && !EXTERNAL_SD1.equals(path)) {
                return;
            }
            String action = intent.getAction();
            if (action.equals(Intent.ACTION_MEDIA_EJECT)) {
                Xlog.d(TAG, "receive Intent.ACTION_MEDIA_EJECT to remove notification & path = " + path);
                mAvoidSDAppAddNotification = true;
                if (mNeedRemoveKeys.isEmpty()) {
                    Xlog.d(TAG, "receive Intent.ACTION_MEDIA_EJECT to remove notificaiton done, array is empty");
                    return;
                }
                ArrayList<IBinder> copy = (ArrayList) mNeedRemoveKeys.clone();
                for (IBinder key : copy) {
                    removeNotification(key);
                }
                copy.clear();
                System.gc();
                Xlog.d(TAG, "receive Intent.ACTION_MEDIA_EJECT to remove notificaiton done, array size is " + mNeedRemoveKeys.size());
            } else if(action.equals(Intent.ACTION_MEDIA_MOUNTED)) {
                Xlog.d(TAG, "receive Intent.ACTION_MEDIA_MOUNTED, path =" + path);
                mAvoidSDAppAddNotification = false;
            }
        }
    };

    /// M: [Smartbook] Dynamic Resolution Change
    private boolean mIsDisplayDevice = false;
    private BroadcastReceiver mDisplayDevicePluginReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            if (DEBUG) Log.v(TAG, "onReceive: " + intent);
            String action = intent.getAction();
            Log.v(TAG, "onReceive, action=" + action);
            if (Intent.ACTION_SMARTBOOK_PLUG.equals(action)) {
                boolean mPlugin = intent.getBooleanExtra(Intent.EXTRA_SMARTBOOK_PLUG_STATE, false);
                Log.v(TAG, "recreateStatusBar, mPlugin = " + mPlugin);
                if (mPlugin != mIsDisplayDevice) {
                    Log.v(TAG, "recreateStatusBar, mIsDisplayDevice = " + mIsDisplayDevice);
                    mIsDisplayDevice = mPlugin;
                    Process.killProcess(Process.myPid());
                }
            }
        }
    };

    /// M: [Smartbook] Process Notification Key
    public void dispatchStatusBarKeyEvent(KeyEvent event) {
        final int keyCode = event.getKeyCode();
        if (keyCode == KeyEvent.KEYCODE_NOTIFICATION) {
            processNotificationKeyEvent(event);
        }
    }

    public void processNotificationKeyEvent(KeyEvent event) {
        final int repeatCount = event.getRepeatCount();
        final int metaState = event.getMetaState();
        final int flags = event.getFlags();
        final boolean down = event.getAction() == KeyEvent.ACTION_DOWN;
        Log.v(TAG, "processOnKeyEvent, KEYCODE_NOTIFICATION");
        
        if ((mDisabled & StatusBarManager.DISABLE_EXPAND) != 0) {
            Log.v(TAG, "processOnKeyEvent, DISABLE_EXPAND");
        }
        if (down && repeatCount == 0) {
            boolean isNotificationPanelShow = false;
            boolean isSettingsPanelShow = false;
            if (mNotificationPanel.isFullyExpanded()) {
                if (mHasFlipSettings) {
                    if (mFlipSettingsView.getVisibility() != View.VISIBLE) {
                        isNotificationPanelShow = true;
                    } else {
                        isSettingsPanelShow = true;
                    }
                } else if (mSettingsPanel != null) {
                    isNotificationPanelShow = true;
                }
            } else if (mSettingsPanel != null && mSettingsPanel.isFullyExpanded()) {
                isSettingsPanelShow = true;
            }
            Log.v(TAG, "processOnKeyEvent, KEYCODE_NOTIFICATION, isNPanel = " + isNotificationPanelShow);
            Log.v(TAG, "processOnKeyEvent, KEYCODE_NOTIFICATION, isSPanel = " + isSettingsPanelShow);
            if ((metaState & KeyEvent.META_SHIFT_LEFT_ON) != 0) {
                if (isNotificationPanelShow) {
                    mCommandQueue.animateCollapsePanels();
                } else if (!mExpandedVisible) {
                    mCommandQueue.animateExpandNotificationsPanel();
                } else if (isSettingsPanelShow) { // Setting
                    mCommandQueue.animateExpandNotificationsPanel();
                }
            } else if ((metaState & KeyEvent.META_SHIFT_RIGHT_ON) != 0) {
                if (isNotificationPanelShow) {
                    mCommandQueue.animateExpandSettingsPanel();
                } else if (!mExpandedVisible) {
                    mCommandQueue.animateExpandSettingsPanel();
                } else if (isSettingsPanelShow) { // Setting
                    mCommandQueue.animateCollapsePanels();
                }
            } else {
                if (isNotificationPanelShow) {
                    mCommandQueue.animateExpandSettingsPanel();
                } else if (!mExpandedVisible) {
                    mCommandQueue.animateExpandNotificationsPanel();
                } else if (isSettingsPanelShow) { // Setting
                    mCommandQueue.animateCollapsePanels();
                }
            }
        }
    }
}
