/*
 * Copyright (C) 2011 The Android Open Source Project
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

package android.net.wifi;

import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.ContentObserver;
import android.net.ConnectivityManager;
import android.net.LinkProperties;
import android.net.NetworkInfo;
import android.net.wifi.RssiPacketCountInfo;
import android.os.Message;
import android.os.SystemClock;
import android.provider.Settings;
import android.provider.Settings.Secure;
import android.util.Log;
import android.util.LruCache;

import com.android.internal.R;
import com.android.internal.util.AsyncChannel;
import com.android.internal.util.Protocol;
import com.android.internal.util.State;
import com.android.internal.util.StateMachine;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.text.DecimalFormat;
///M:
import android.net.NetworkInfo.DetailedState;
import android.widget.Toast;
import android.view.View;
import android.view.WindowManager;
import android.view.WindowManagerPolicy;
import android.widget.TextView;
import android.view.LayoutInflater;
import android.view.Gravity;
import android.os.Handler;
import android.net.ConnectivityManager;
import android.os.SystemProperties;


/**
 * WifiWatchdogStateMachine monitors the connection to a WiFi network. When WiFi
 * connects at L2 layer, the beacons from access point reach the device and it
 * can maintain a connection, but the application connectivity can be flaky (due
 * to bigger packet size exchange).
 * <p>
 * We now monitor the quality of the last hop on WiFi using packet loss ratio as
 * an indicator to decide if the link is good enough to switch to Wi-Fi as the
 * uplink.
 * <p>
 * When WiFi is connected, the WiFi watchdog keeps sampling the RSSI and the
 * instant packet loss, and record it as per-AP loss-to-rssi statistics. When
 * the instant packet loss is higher than a threshold, the WiFi watchdog sends a
 * poor link notification to avoid WiFi connection temporarily.
 * <p>
 * While WiFi is being avoided, the WiFi watchdog keep watching the RSSI to
 * bring the WiFi connection back. Once the RSSI is high enough to achieve a
 * lower packet loss, a good link detection is sent such that the WiFi
 * connection become available again.
 * <p>
 * BSSID roaming has been taken into account. When user is moving across
 * multiple APs, the WiFi watchdog will detect that and keep watching the
 * currently connected AP.
 * <p>
 * Power impact should be minimal since much of the measurement relies on
 * passive statistics already being tracked at the driver and the polling is
 * done when screen is turned on and the RSSI is in a certain range.
 *
 * @hide
 */
public class WifiWatchdogStateMachine extends StateMachine {

    private static boolean DBG = true;
    private static final String TAG = "WifiWatchdogStateMachine";

    private static final int BASE = Protocol.BASE_WIFI_WATCHDOG;

    /* Internal events */
    private static final int EVENT_WATCHDOG_TOGGLED                 = BASE + 1;
    private static final int EVENT_NETWORK_STATE_CHANGE             = BASE + 2;
    private static final int EVENT_RSSI_CHANGE                      = BASE + 3;
    private static final int EVENT_SUPPLICANT_STATE_CHANGE          = BASE + 4;
    private static final int EVENT_WIFI_RADIO_STATE_CHANGE          = BASE + 5;
    private static final int EVENT_WATCHDOG_SETTINGS_CHANGE         = BASE + 6;
    private static final int EVENT_BSSID_CHANGE                     = BASE + 7;
    private static final int EVENT_SCREEN_ON                        = BASE + 8;
    private static final int EVENT_SCREEN_OFF                       = BASE + 9;

    /* Internal messages */
    private static final int CMD_RSSI_FETCH                         = BASE + 11;

    /* Notifications from/to WifiStateMachine */
    static final int POOR_LINK_DETECTED                             = BASE + 21;
    static final int GOOD_LINK_DETECTED                             = BASE + 22;

///M:
    private static final int EVENT_ROAMING_DETECT                       = BASE + 40;    
    private static final int EVENT_P2P_STATE_CHANGE                       = BASE + 43;    
    private static final int EVENT_POOR_LINK_PROFILING_SETTINGS_CHANGE         = BASE + 44;


    public static final boolean DEFAULT_POOR_NETWORK_AVOIDANCE_ENABLED = false;

    /*
     * RSSI levels as used by notification icon
     * Level 4  -55 <= RSSI
     * Level 3  -66 <= RSSI < -55
     * Level 2  -77 <= RSSI < -67
     * Level 1  -88 <= RSSI < -78
     * Level 0         RSSI < -88
     */

    /**
     * WiFi link statistics is monitored and recorded actively below this threshold.
     * <p>
     * Larger threshold is more adaptive but increases sampling cost.
     */
    private static final int LINK_MONITOR_LEVEL_THRESHOLD = WifiManager.RSSI_LEVELS - 1;

    /**
     * Remember packet loss statistics of how many BSSIDs.
     * <p>
     * Larger size is usually better but requires more space.
     */
    private static final int BSSID_STAT_CACHE_SIZE = 20;

    /**
     * RSSI range of a BSSID statistics.
     * Within the range, (RSSI -> packet loss %) mappings are stored.
     * <p>
     * Larger range is usually better but requires more space.
     */
    private static final int BSSID_STAT_RANGE_LOW_DBM  = -105;

    /**
     * See {@link #BSSID_STAT_RANGE_LOW_DBM}.
     */
    private static final int BSSID_STAT_RANGE_HIGH_DBM = -45;

    /**
     * How many consecutive empty data point to trigger a empty-cache detection.
     * In this case, a preset/default loss value (function on RSSI) is used.
     * <p>
     * In normal uses, some RSSI values may never be seen due to channel randomness.
     * However, the size of such empty RSSI chunk in normal use is generally 1~2.
     */
    private static final int BSSID_STAT_EMPTY_COUNT = 3;

    /**
     * Sample interval for packet loss statistics, in msec.
     * <p>
     * Smaller interval is more accurate but increases sampling cost (battery consumption).
     */
    private static final long LINK_SAMPLING_INTERVAL_MS = 1 * 1000;

    /**
     * Coefficients (alpha) for moving average for packet loss tracking.
     * Must be within (0.0, 1.0).
     * <p>
     * Equivalent number of samples: N = 2 / alpha - 1 .
     * We want the historic loss to base on more data points to be statistically reliable.
     * We want the current instant loss to base on less data points to be responsive.
     */
    private static final double EXP_COEFFICIENT_RECORD  = 0.1;

    /**
     * See {@link #EXP_COEFFICIENT_RECORD}.
     */
    private static final double EXP_COEFFICIENT_MONITOR = 0.5;

    /**
     * Thresholds for sending good/poor link notifications, in packet loss %.
     * Good threshold must be smaller than poor threshold.
     * Use smaller poor threshold to avoid WiFi more aggressively.
     * Use smaller good threshold to bring back WiFi more conservatively.
     * <p>
     * When approaching the boundary, loss ratio jumps significantly within a few dBs.
     * 50% loss threshold is a good balance between accuracy and reponsiveness.
     * <=10% good threshold is a safe value to avoid jumping back to WiFi too easily.
     */
    private static final double POOR_LINK_LOSS_THRESHOLD = 0.5;

    /**
     * See {@link #POOR_LINK_LOSS_THRESHOLD}.
     */
   ///M: modify default =0.1
    private static final double GOOD_LINK_LOSS_THRESHOLD = 0.2;

    /**
     * Number of samples to confirm before sending a poor link notification.
     * Response time = confirm_count * sample_interval .
     * <p>
     * A smaller threshold improves response speed but may suffer from randomness.
     * According to experiments, 3~5 are good values to achieve a balance.
     * These parameters should be tuned along with {@link #LINK_SAMPLING_INTERVAL_MS}.
     */
     ///M: modify default =3
    private static final int POOR_LINK_SAMPLE_COUNT = 5;

    /**
     * Minimum volume (converted from pkt/sec) to detect a poor link, to avoid randomness.
     * <p>
     * According to experiments, 1pkt/sec is too sensitive but 3pkt/sec is slightly unresponsive.
     */
    private static final double POOR_LINK_MIN_VOLUME = 2.0 * LINK_SAMPLING_INTERVAL_MS / 1000.0;

    /**
     * When a poor link is detected, we scan over this range (based on current
     * poor link RSSI) for a target RSSI that satisfies a target packet loss.
     * Refer to {@link #GOOD_LINK_TARGET}.
     * <p>
     * We want range_min not too small to avoid jumping back to WiFi too easily.
     */
    private static final int GOOD_LINK_RSSI_RANGE_MIN = 3;

    /**
     * See {@link #GOOD_LINK_RSSI_RANGE_MIN}.
     */
    private static final int GOOD_LINK_RSSI_RANGE_MAX = 20;

    /**
     * Adaptive good link target to avoid flapping.
     * When a poor link is detected, a good link target is calculated as follows:
     * <p>
     *      targetRSSI = min { rssi | loss(rssi) < GOOD_LINK_LOSS_THRESHOLD } + rssi_adj[i],
     *                   where rssi is within the above GOOD_LINK_RSSI_RANGE.
     *      targetCount = sample_count[i] .
     * <p>
     * While WiFi is being avoided, we keep monitoring its signal strength.
     * Good link notification is sent when we see current RSSI >= targetRSSI
     * for targetCount consecutive times.
     * <p>
     * Index i is incremented each time after a poor link detection.
     * Index i is decreased to at most k if the last poor link was at lease reduce_time[k] ago.
     * <p>
     * Intuitively, larger index i makes it more difficult to get back to WiFi, avoiding flapping.
     * In experiments, (+9 dB / 30 counts) makes it quite difficult to achieve.
     * Avoid using it unless flapping is really bad (say, last poor link is < 1 min ago).
     */
    private static final GoodLinkTarget[] GOOD_LINK_TARGET = {
        /*                  rssi_adj,       sample_count,   reduce_time */
        new GoodLinkTarget( 0,              3,              30 * 60000   ),
        new GoodLinkTarget( 3,              5,              5  * 60000   ),
        new GoodLinkTarget( 6,              10,             1  * 60000   ),
        new GoodLinkTarget( 9,              30,             0  * 60000   ),
    };

    /**
     * The max time to avoid a BSSID, to prevent avoiding forever.
     * If current RSSI is at least min_rssi[i], the max avoidance time is at most max_time[i]
     * <p>
     * It is unusual to experience high packet loss at high RSSI. Something unusual must be
     * happening (e.g. strong interference). For higher signal strengths, we set the avoidance
     * time to be low to allow for quick turn around from temporary interference.
     * <p>
     * See {@link BssidStatistics#poorLinkDetected}.
     */
    private static final MaxAvoidTime[] MAX_AVOID_TIME = {
        /*                  max_time,           min_rssi */
        new MaxAvoidTime(   30 * 60000,         -200      ),
        new MaxAvoidTime(   5  * 60000,         -70       ),
        new MaxAvoidTime(   0  * 60000,         -55       ),
    };

    /* Framework related */
    private Context mContext;
    private ContentResolver mContentResolver;
    private WifiManager mWifiManager;
    private IntentFilter mIntentFilter;
    private BroadcastReceiver mBroadcastReceiver;
    private AsyncChannel mWsmChannel = new AsyncChannel();
    private WifiInfo mWifiInfo;
    private LinkProperties mLinkProperties;

    /* System settingss related */
    private static boolean sWifiOnly = false;
    private boolean mPoorNetworkDetectionEnabled;

    /* Poor link detection related */
    private LruCache<String, BssidStatistics> mBssidCache =
            new LruCache<String, BssidStatistics>(BSSID_STAT_CACHE_SIZE);
    private int mRssiFetchToken = 0;
    private int mCurrentSignalLevel;
    private BssidStatistics mCurrentBssid;
    private VolumeWeightedEMA mCurrentLoss;
    private boolean mIsScreenOn = true;
    private static double sPresetLoss[];

    /* WiFi watchdog state machine related */
    private DefaultState mDefaultState = new DefaultState();
    private WatchdogDisabledState mWatchdogDisabledState = new WatchdogDisabledState();
    private WatchdogEnabledState mWatchdogEnabledState = new WatchdogEnabledState();
    private NotConnectedState mNotConnectedState = new NotConnectedState();
    private VerifyingLinkState mVerifyingLinkState = new VerifyingLinkState();
    private ConnectedState mConnectedState = new ConnectedState();
    private OnlineWatchState mOnlineWatchState = new OnlineWatchState();
    private LinkMonitoringState mLinkMonitoringState = new LinkMonitoringState();
    private OnlineState mOnlineState = new OnlineState();


    ///M: add 
    private int mLinkSpeed;
    private boolean isRoaming =false;
    private DetailedState currentDetailState ;
    Toast toast;
    boolean p2pStart =false;

    View mLatencyPanelView = null;
    TextView mTextView = null;
    boolean mPoorLinkProfilingEnabled = false;
    private final Handler mHandler;
    private final static int POOR_LINK_PROFILING_PERIOD_MILLIS = 1*1000;
    private int mSampleCount =0;
    private int mLatestRssi;

    private double mPoorLinkThreshold = POOR_LINK_LOSS_THRESHOLD;
    private double mGoodLinkThreshold = GOOD_LINK_LOSS_THRESHOLD;
    /**
     * STATE MAP
     *          Default
     *         /       \
     * Disabled      Enabled
     *             /     \     \
     * NotConnected  Verifying  Connected
     *                         /---------\
     *                       (all other states)
     */
    private WifiWatchdogStateMachine(Context context) {
        super("WifiWatchdogStateMachine");
        mContext = context;
        mContentResolver = context.getContentResolver();
        mWifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
        mWsmChannel.connectSync(mContext, getHandler(),
                mWifiManager.getWifiStateMachineMessenger());

        setupNetworkReceiver();
        ///M:@{
        mHandler = getHandler();
        ///@}

        // the content observer to listen needs a handler
        registerForSettingsChanges();
        registerForWatchdogToggle();
        addState(mDefaultState);
            addState(mWatchdogDisabledState, mDefaultState);
            addState(mWatchdogEnabledState, mDefaultState);
                addState(mNotConnectedState, mWatchdogEnabledState);
                addState(mVerifyingLinkState, mWatchdogEnabledState);
                addState(mConnectedState, mWatchdogEnabledState);
                    addState(mOnlineWatchState, mConnectedState);
                    addState(mLinkMonitoringState, mConnectedState);
                    addState(mOnlineState, mConnectedState);

        if (isWatchdogEnabled()) {
            setInitialState(mNotConnectedState);
        } else {
            setInitialState(mWatchdogDisabledState);
        }
        setLogRecSize(25);
        setLogOnlyTransitions(true);
        updateSettings();
    }

    public static WifiWatchdogStateMachine makeWifiWatchdogStateMachine(Context context) {
        ContentResolver contentResolver = context.getContentResolver();

        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(
                Context.CONNECTIVITY_SERVICE);
        sWifiOnly = (cm.isNetworkSupported(ConnectivityManager.TYPE_MOBILE) == false);

        // Watchdog is always enabled. Poor network detection can be seperately turned on/off
        // TODO: Remove this setting & clean up state machine since we always have
        // watchdog in an enabled state
        putSettingsGlobalBoolean(contentResolver, Settings.Global.WIFI_WATCHDOG_ON, true);

        WifiWatchdogStateMachine wwsm = new WifiWatchdogStateMachine(context);
        wwsm.start();
        return wwsm;
    }

    private void setupNetworkReceiver() {
        mBroadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                if (action.equals(WifiManager.RSSI_CHANGED_ACTION)) {
                    obtainMessage(EVENT_RSSI_CHANGE,
                            intent.getIntExtra(WifiManager.EXTRA_NEW_RSSI, -200), 0).sendToTarget();
                } else if (action.equals(WifiManager.SUPPLICANT_STATE_CHANGED_ACTION)) {
                    sendMessage(EVENT_SUPPLICANT_STATE_CHANGE, intent);
                } else if (action.equals(WifiManager.NETWORK_STATE_CHANGED_ACTION)) {
                    ///M: add@{
                    NetworkInfo networkInfo = (NetworkInfo) intent.getParcelableExtra(WifiManager.EXTRA_NETWORK_INFO);
                    if(networkInfo.getDetailedState() == DetailedState.CONNECTED || 
                        networkInfo.getDetailedState() == DetailedState.DISCONNECTED){

                        currentDetailState = networkInfo.getDetailedState();
                        logd("currentDetailState="+currentDetailState);
                    }          
                    ///@}
                    sendMessage(EVENT_NETWORK_STATE_CHANGE, intent);
                } else if (action.equals(Intent.ACTION_SCREEN_ON)) {
                    sendMessage(EVENT_SCREEN_ON);
                } else if (action.equals(Intent.ACTION_SCREEN_OFF)) {
                    sendMessage(EVENT_SCREEN_OFF);
                } else if (action.equals(WifiManager.WIFI_STATE_CHANGED_ACTION)) {
                    sendMessage(EVENT_WIFI_RADIO_STATE_CHANGE,intent.getIntExtra(
                            WifiManager.EXTRA_WIFI_STATE, WifiManager.WIFI_STATE_UNKNOWN));
                ///M:@{
                }else if (action.equals("wifi.wifi.roamingDetect")) {                    
                    String newBssid = intent.getStringExtra("newBssid");
                    String priBssid = intent.getStringExtra("priBssid");

                    isRoaming = true;
                    logd("roamingDetect priBssid= "+priBssid+" newBssid= "+newBssid+ " isRoaming= "+isRoaming);

                    if(mPoorLinkProfilingEnabled == true){
                        toast = Toast.makeText(context, "roamingDetect priBssid="+ priBssid+ " new="+ newBssid, Toast.LENGTH_SHORT);
                        toast.show();
                    }
                }else if(action.equals("com.mediatek.wifi.p2p.Tx")){
                    p2pStart = intent.getBooleanExtra("start",false);
                    logd("p2pStart= "+p2pStart);
                   

                }
                ///@}
            }
        };

        mIntentFilter = new IntentFilter();
        mIntentFilter.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION);
        mIntentFilter.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION);
        mIntentFilter.addAction(WifiManager.RSSI_CHANGED_ACTION);
        mIntentFilter.addAction(WifiManager.SUPPLICANT_STATE_CHANGED_ACTION);
        mIntentFilter.addAction(Intent.ACTION_SCREEN_ON);
        mIntentFilter.addAction(Intent.ACTION_SCREEN_OFF);
        ///M: add @{
        mIntentFilter.addAction("com.mediatek.wifi.p2p.Tx");        
        mIntentFilter.addAction("wifi.wifi.roamingDetect");
        ///}
        mContext.registerReceiver(mBroadcastReceiver, mIntentFilter);
    }

    /**
     * Observes the watchdog on/off setting, and takes action when changed.
     */
    private void registerForWatchdogToggle() {
        ContentObserver contentObserver = new ContentObserver(this.getHandler()) {
            @Override
            public void onChange(boolean selfChange) {
                sendMessage(EVENT_WATCHDOG_TOGGLED);
            }
        };

        mContext.getContentResolver().registerContentObserver(
                Settings.Global.getUriFor(Settings.Global.WIFI_WATCHDOG_ON),
                false, contentObserver);
    }

    /**
     * Observes watchdogs secure setting changes.
     */
    private void registerForSettingsChanges() {
        ContentObserver contentObserver = new ContentObserver(this.getHandler()) {
            @Override
            public void onChange(boolean selfChange) {
                sendMessage(EVENT_WATCHDOG_SETTINGS_CHANGE);
            }
        };

        mContext.getContentResolver().registerContentObserver(
                Settings.Global.getUriFor(Settings.Global.WIFI_WATCHDOG_POOR_NETWORK_TEST_ENABLED),
                false, contentObserver);
    }

    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        super.dump(fd, pw, args);
        pw.println("mWifiInfo: [" + mWifiInfo + "]");
        pw.println("mLinkProperties: [" + mLinkProperties + "]");
        pw.println("mCurrentSignalLevel: [" + mCurrentSignalLevel + "]");
        pw.println("mPoorNetworkDetectionEnabled: [" + mPoorNetworkDetectionEnabled + "]");
    }

    private boolean isWatchdogEnabled() {
        boolean ret = getSettingsGlobalBoolean(
                mContentResolver, Settings.Global.WIFI_WATCHDOG_ON, true);
        if (DBG) logd("Watchdog enabled " + ret);
        return ret;
    }

    private void updateSettings() {
        if (DBG) logd("Updating secure settings");

        // disable poor network avoidance
        if (sWifiOnly) {
            logd("Disabling poor network avoidance for wi-fi only device");
            mPoorNetworkDetectionEnabled = false;
        } else {
            mPoorNetworkDetectionEnabled = getSettingsGlobalBoolean(mContentResolver,
                    Settings.Global.WIFI_WATCHDOG_POOR_NETWORK_TEST_ENABLED,
                    DEFAULT_POOR_NETWORK_AVOIDANCE_ENABLED);
        }
        //M:
        mPoorLinkProfilingEnabled = SystemProperties.getBoolean("persist.sys.poorlinkProfile",false);

        mPoorLinkThreshold = Double.parseDouble(SystemProperties.get("persist.sys.poorlink",""+POOR_LINK_LOSS_THRESHOLD));
        mGoodLinkThreshold = Double.parseDouble(SystemProperties.get("persist.sys.goodlink",""+GOOD_LINK_LOSS_THRESHOLD));
        
    }

    /**
     * Default state, guard for unhandled messages.
     */
    class DefaultState extends State {
        @Override
        public void enter() {
            if (DBG) logd(getName());
        }

        @Override
        public boolean processMessage(Message msg) {
            switch (msg.what) {
                case EVENT_WATCHDOG_SETTINGS_CHANGE:
                    updateSettings();
                    if (DBG) logd("Updating wifi-watchdog secure settings");
                    break;
                case EVENT_RSSI_CHANGE:
                    mCurrentSignalLevel = calculateSignalLevel(msg.arg1);
                    break;
                case EVENT_WIFI_RADIO_STATE_CHANGE:
                case EVENT_NETWORK_STATE_CHANGE:
                case EVENT_SUPPLICANT_STATE_CHANGE:
                case EVENT_BSSID_CHANGE:
                case CMD_RSSI_FETCH:
                case WifiManager.RSSI_PKTCNT_FETCH_SUCCEEDED:
                case WifiManager.RSSI_PKTCNT_FETCH_FAILED:
                    // ignore
                    break;
                case EVENT_SCREEN_ON:
                    mIsScreenOn = true;
                    break;
                case EVENT_SCREEN_OFF:
                    mIsScreenOn = false;
                    break;
                ///M: @{
                case EVENT_ROAMING_DETECT:
                    break;
                case EVENT_POOR_LINK_PROFILING_SETTINGS_CHANGE:
                    handlePoorLinkProfilingChange();
                    break;
                 ///@}                 
                default:
                    loge("Unhandled message " + msg + " in state " + getCurrentState().getName());
                    break;
            }
            return HANDLED;
        }
    }

    /**
     * WiFi watchdog is disabled by the setting.
     */
    class WatchdogDisabledState extends State {
        @Override
        public void enter() {
            if (DBG) logd(getName());
        }

        @Override
        public boolean processMessage(Message msg) {
            switch (msg.what) {
                case EVENT_WATCHDOG_TOGGLED:
                    if (isWatchdogEnabled())
                        transitionTo(mNotConnectedState);
                    return HANDLED;
                case EVENT_NETWORK_STATE_CHANGE:
                    Intent intent = (Intent) msg.obj;
                    NetworkInfo networkInfo = (NetworkInfo)
                            intent.getParcelableExtra(WifiManager.EXTRA_NETWORK_INFO);

                    switch (networkInfo.getDetailedState()) {
                        case VERIFYING_POOR_LINK:
                            if (DBG) logd("Watchdog disabled, verify link");
                            sendLinkStatusNotification(true);
                            break;
                        default:
                            break;
                    }
                    break;
            }
            return NOT_HANDLED;
        }
    }

    /**
     * WiFi watchdog is enabled by the setting.
     */
    class WatchdogEnabledState extends State {
        @Override
        public void enter() {
            if (DBG) logd(getName());
        }

        @Override
        public boolean processMessage(Message msg) {
            Intent intent;
            switch (msg.what) {
                case EVENT_WATCHDOG_TOGGLED:
                    if (!isWatchdogEnabled())
                        transitionTo(mWatchdogDisabledState);
                    break;

                case EVENT_NETWORK_STATE_CHANGE:
                    intent = (Intent) msg.obj;
                    NetworkInfo networkInfo =
                            (NetworkInfo) intent.getParcelableExtra(WifiManager.EXTRA_NETWORK_INFO);
                    if (DBG) logd("Network state change " + networkInfo.getDetailedState());

                    mWifiInfo = (WifiInfo) intent.getParcelableExtra(WifiManager.EXTRA_WIFI_INFO);
                    updateCurrentBssid(mWifiInfo != null ? mWifiInfo.getBSSID() : null);

                    switch (networkInfo.getDetailedState()) {
                        case VERIFYING_POOR_LINK:
                            mLinkProperties = (LinkProperties) intent.getParcelableExtra(
                                    WifiManager.EXTRA_LINK_PROPERTIES);
                            if (mPoorNetworkDetectionEnabled) {
                                if (mWifiInfo == null || mCurrentBssid == null) {
                                    loge("Ignore, wifiinfo " + mWifiInfo +" bssid " + mCurrentBssid);
                                    sendLinkStatusNotification(true);
                                } else {
                                    transitionTo(mVerifyingLinkState);
                                }
                            } else {
                                sendLinkStatusNotification(true);
                            }
                            break;
                        case CONNECTED:
                            mLinkProperties = (LinkProperties) intent.getParcelableExtra(
                                    WifiManager.EXTRA_LINK_PROPERTIES);
                            transitionTo(mOnlineWatchState);
                            break;
                        default:
                            transitionTo(mNotConnectedState);
                            break;
                    }
                    break;

                case EVENT_SUPPLICANT_STATE_CHANGE:
                    intent = (Intent) msg.obj;
                    SupplicantState supplicantState = (SupplicantState) intent.getParcelableExtra(
                            WifiManager.EXTRA_NEW_STATE);
                    if (supplicantState == SupplicantState.COMPLETED) {
                        mWifiInfo = mWifiManager.getConnectionInfo();
                        updateCurrentBssid(mWifiInfo.getBSSID());
                    }
                    break;

                case EVENT_WIFI_RADIO_STATE_CHANGE:
                    if (msg.arg1 == WifiManager.WIFI_STATE_DISABLING) {
                        transitionTo(mNotConnectedState);
                    }
                    break;

                default:
                    return NOT_HANDLED;
            }

            return HANDLED;
        }
    }

    /**
     * WiFi is disconnected.
     */
    class NotConnectedState extends State {
        @Override
        public void enter() {
            if (DBG) logd(getName());
        }
    }

    /**
     * WiFi is connected, but waiting for good link detection message.
     */
    class VerifyingLinkState extends State {

       // private int mSampleCount;

        @Override
        public void enter() {
            if (DBG) logd(getName());
            mSampleCount = 0;
            mCurrentBssid.newLinkDetected();
            sendMessage(obtainMessage(CMD_RSSI_FETCH, ++mRssiFetchToken, 0));

            ///M:@{
            handlePoorLinkProfilingChange();
            ///@}
        }

        @Override
        public boolean processMessage(Message msg) {
            switch (msg.what) {
                case EVENT_WATCHDOG_SETTINGS_CHANGE:
                    updateSettings();
                    if (!mPoorNetworkDetectionEnabled) {
                        sendLinkStatusNotification(true);
                    }
                    break;

                case EVENT_BSSID_CHANGE:
                    transitionTo(mVerifyingLinkState);
                    break;

                case CMD_RSSI_FETCH:
                    if (msg.arg1 == mRssiFetchToken) {
                        mWsmChannel.sendMessage(WifiManager.RSSI_PKTCNT_FETCH);
                        sendMessageDelayed(obtainMessage(CMD_RSSI_FETCH, ++mRssiFetchToken, 0),
                                LINK_SAMPLING_INTERVAL_MS);
                    }
                    break;

                case WifiManager.RSSI_PKTCNT_FETCH_SUCCEEDED:
                    RssiPacketCountInfo info = (RssiPacketCountInfo) msg.obj;
                    int rssi = info.rssi;
                    int linkspeed = info.mLinkspeed;
                    mLinkSpeed =  info.mLinkspeed;
                    mLatestRssi = rssi;
                    
                    if (DBG) logd("Fetch RSSI succeed, rssi=" + rssi);

                    long time = mCurrentBssid.mBssidAvoidTimeMax - SystemClock.elapsedRealtime();
                    if (time <= 0) {
                        // max avoidance time is met
                        if (DBG) logd("Max avoid time elapsed");
                        sendLinkStatusNotification(true);
                    } else {
                        if(isRoaming == true && currentDetailState == DetailedState.CONNECTED){
                            if (DBG) logd("Roaming case with privious connected- always send true at first");
                            sendLinkStatusNotification(true);
                            isRoaming=false;
                            if (DBG) logd("isRoaming = false");                            
                            break;
                        }else if (isRoaming == true){
                            isRoaming=false;
                            if (DBG) logd("Roaming case with privious disconnected- should check isRoaming= "+
                                isRoaming+" currentDetailState "+currentDetailState);
                        }else{
                            if (DBG) logd("privious disconnected- should check isRoaming= "+
                                isRoaming+" currentDetailState "+currentDetailState);
                        }

                        if (rssi >= mCurrentBssid.mGoodLinkTargetRssi) {
                            if (++mSampleCount >= mCurrentBssid.mGoodLinkTargetCount) {
                                // link is good again
                                if (DBG) logd("Good link detected, rssi=" + rssi);
                                mCurrentBssid.mBssidAvoidTimeMax = 0;
                                sendLinkStatusNotification(true);
                            }
                        } else {
                            mSampleCount = 0;
                            if (DBG) logd("Link is still poor, time left=" + time);
                        }
                    }
                    break;

                case WifiManager.RSSI_PKTCNT_FETCH_FAILED:
                    if (DBG) logd("RSSI_FETCH_FAILED");
                    break;
                ///M:@{
                case EVENT_POOR_LINK_PROFILING_SETTINGS_CHANGE:
                    handlePoorLinkProfilingChange();
                    break;
                ///@}
                default:
                    return NOT_HANDLED;
            }
            return HANDLED;
        }

        ///M:@{
        @Override
        public void exit() {            
            mHandler.removeCallbacks(mPoorLinkProfilingInfo);
            hideLatencyPanel();
        }
        ///@}
    }

    /**
     * WiFi is connected and link is verified.
     */
    class ConnectedState extends State {
        @Override
        public void enter() {
            if (DBG) logd(getName());
        }

        @Override
        public boolean processMessage(Message msg) {
            switch (msg.what) {
                case EVENT_WATCHDOG_SETTINGS_CHANGE:
                    updateSettings();
                    if (mPoorNetworkDetectionEnabled) {
                        transitionTo(mOnlineWatchState);
                    } else {
                        transitionTo(mOnlineState);
                    }
                    return HANDLED;
            }
            return NOT_HANDLED;
        }
    }

    /**
     * RSSI is high enough and don't need link monitoring.
     */
    class OnlineWatchState extends State {
        @Override
        public void enter() {
            if (DBG) logd(getName());
            if (mPoorNetworkDetectionEnabled) {
                // treat entry as an rssi change
                handleRssiChange();
            } else {
                transitionTo(mOnlineState);
            }
        }

        private void handleRssiChange() {
            if (mCurrentSignalLevel <= LINK_MONITOR_LEVEL_THRESHOLD && mCurrentBssid != null) {
                transitionTo(mLinkMonitoringState);
            } else {
                // stay here
            }
        }

        @Override
        public boolean processMessage(Message msg) {
            switch (msg.what) {
                case EVENT_RSSI_CHANGE:
                    mCurrentSignalLevel = calculateSignalLevel(msg.arg1);
                    handleRssiChange();
                    break;
                default:
                    return NOT_HANDLED;
            }
            return HANDLED;
        }
    }

    /**
     * Keep sampling the link and monitor any poor link situation.
     */
    class LinkMonitoringState extends State {

       // private int mSampleCount;

        private int mLastRssi;
        private int mLastTxGood;
        private int mLastTxBad;


        private  long mLastFailedCount;
        private  long mLastRetryCount;
        private  long mLastMultipleRetryCount;
        private ConnectivityManager mCm;

        @Override
        public void enter() {
            if (DBG) logd(getName());
            mSampleCount = 0;
            mCurrentLoss = new VolumeWeightedEMA(EXP_COEFFICIENT_MONITOR);
            sendMessage(obtainMessage(CMD_RSSI_FETCH, ++mRssiFetchToken, 0));

            handlePoorLinkProfilingChange();
            mCm = (ConnectivityManager) mContext.getSystemService(Context.CONNECTIVITY_SERVICE);
        }

        @Override
        public boolean processMessage(Message msg) {
            switch (msg.what) {
                case EVENT_RSSI_CHANGE:
                    mCurrentSignalLevel = calculateSignalLevel(msg.arg1);
                    if (mCurrentSignalLevel <= LINK_MONITOR_LEVEL_THRESHOLD) {
                        // stay here;
                    } else {
                        // we don't need frequent RSSI monitoring any more
                        transitionTo(mOnlineWatchState);
                    }
                    break;

                case EVENT_BSSID_CHANGE:
                    transitionTo(mLinkMonitoringState);
                    break;

                case CMD_RSSI_FETCH:
                    if (!mIsScreenOn) {
                        transitionTo(mOnlineState);
                    } else if (msg.arg1 == mRssiFetchToken) {
                        mWsmChannel.sendMessage(WifiManager.RSSI_PKTCNT_FETCH);
                        sendMessageDelayed(obtainMessage(CMD_RSSI_FETCH, ++mRssiFetchToken, 0),
                                LINK_SAMPLING_INTERVAL_MS);
                    }
                    break;

                case WifiManager.RSSI_PKTCNT_FETCH_SUCCEEDED:
                    RssiPacketCountInfo info = (RssiPacketCountInfo) msg.obj;
                    int rssi = info.rssi;
                    int mrssi = (mLastRssi + rssi) / 2;
                    int txbad = info.txbad;
                    int txgood = info.txgood;

                    ///M:add@{
                    
                    long rFailedCount = info.rFailedCount;
                    long rRetryCount = info.rRetryCount;
                    long rMultipleRetryCount = info.rMultipleRetryCount;
                    
                    int linkspeed = info.mLinkspeed;
                    mLinkSpeed =  info.mLinkspeed;
                    mLatestRssi = rssi;

                    long    per = info.per;
                    double    rate = info.rate;
                    long    total_cnt = info.total_cnt;
                    long    fail_cnt = info.fail_cnt;
                    long    timeout_cnt = info.timeout_cnt;
                    long    apt = info.apt;
                    long    aat = info.aat;
                    ///@}
                    
                   /* if (DBG) logd("Fetch RSSI succeed, rssi=" + rssi + " mrssi=" + mrssi + " txbad="
                            + txbad + " txgood=" + txgood + " linkspeed="+ linkspeed +" rFailedCount="+rFailedCount
                            +" rRetryCount="+rRetryCount+" rMultipleRetryCount="+rMultipleRetryCount);
                    logd("per="+per+" rate="+rate+" total_cnt"+total_cnt+" fail_cnt="+fail_cnt+" timeout_cnt="+timeout_cnt+" apt="+apt+" aat="+aat);
*/
                    // skip the first data point as we want incremental values
                    long now = SystemClock.elapsedRealtime();
                    if (now - mCurrentBssid.mLastTimeSample < LINK_SAMPLING_INTERVAL_MS * 2) {

                        // update packet loss statistics
                        int dbad = txbad - mLastTxBad;
                        int dgood = txgood - mLastTxGood;
                        int dtotal = dbad + dgood;

                        ///M: add@{
                        long dFailCount = rFailedCount - mLastFailedCount;
                        long dRetryCount = rRetryCount - mLastRetryCount;
                        long dMultipleRetryCount = rMultipleRetryCount - mLastMultipleRetryCount;
                        ///@}

                        if (( dtotal > 0 && dRetryCount<dtotal) && p2pStart==false) {
                            // calculate packet loss in the last sampling interval
                            double loss = ((double) dbad) / ((double) dtotal);
                            DecimalFormat df = new DecimalFormat("#.##");


                            ///M: modify@{
                            //calculate packet loss counted retry
                            double loss_withRetry = ((double) (dbad+ dRetryCount +dMultipleRetryCount)) / 
                                ((double) (dtotal+ dRetryCount +dMultipleRetryCount));
                            int dtotalRetry = (dtotal+ (int)dRetryCount +(int)dMultipleRetryCount);


                            if(linkspeed >=5){  //when link speed is fast, it shoud have error torarrent
                                //recalculate  packet loss rate by using only 1-layer retry count
                                loss_withRetry = ((double) (dbad+ dRetryCount )) /((double) (dtotal+ dRetryCount));
                                dtotalRetry = (dtotal+ (int)dRetryCount);
                            }else if(linkspeed >=6.5){  //802.11n didn't support retry count
                                loss_withRetry = ((double) dbad) / ((double) dtotal);
                                dtotalRetry = dtotal;
                            }
                            //mCurrentLoss.update(loss, dtotal);
                            mCurrentLoss.update(loss_withRetry, dtotalRetry);

                            logd("conclusion, rate="+rate+" Current loss="+df.format(mCurrentLoss.mValue * 100) + "%" + " rssi=" + rssi + " txgood=" + dgood + " txbad="+dbad+" dtotal="+dtotal+
                                " dRetryCount="+dRetryCount+" dMultipleRetryCount="+dMultipleRetryCount);
                             logd("per="+per+" rate="+rate+" total_cnt"+total_cnt+" fail_cnt="+fail_cnt+" timeout_cnt="+timeout_cnt+" apt="+apt+" aat="+aat);

                             

                            if (DBG) {
                                //DecimalFormat df = new DecimalFormat("#.##");
    //                            logd("Incremental loss=" + dbad + "/" + dtotal + " Current loss="
  //                                      + df.format(mCurrentLoss.mValue * 100) + "% volume="
//                                        + df.format(mCurrentLoss.mVolume));
/*
                                logd("dFailCount= " + dFailCount + " dRetryCount= " + dRetryCount + " dMultipleRetryCount= "
                                        + dMultipleRetryCount  +"  Current loss with retry=" +df.format(loss_withRetry*100)+"%");
                                
                                logd("With Retry Incremental  Current loss="
                                        + df.format(mCurrentLoss.mValue * 100) + "% volume="
                                        + df.format(mCurrentLoss.mVolume));
                                */
                            }

//                            mCurrentBssid.updateLoss(mrssi, loss, dtotal);
                            mCurrentBssid.updateLoss(mrssi, loss_withRetry, dtotalRetry);
                            ///@}

                            if(mCm.getMobileDataEnabled()==true){
                                // check for high packet loss and send poor link notification
                                if (mCurrentLoss.mValue > mPoorLinkThreshold
                                        && mCurrentLoss.mVolume > POOR_LINK_MIN_VOLUME) {
                                    if (++mSampleCount >= POOR_LINK_SAMPLE_COUNT)
                                        if (mCurrentBssid.poorLinkDetected(rssi)) {
                                            sendLinkStatusNotification(false);
                                            ++mRssiFetchToken;
                                        }
                                } else {
                                    mSampleCount = 0;
                                }
                            }
                        }
                    }

                    mCurrentBssid.mLastTimeSample = now;
                    mLastTxBad = txbad;
                    mLastTxGood = txgood;
                    mLastRssi = rssi;

                    ///M: add@{
                    mLastFailedCount = rFailedCount;
                    mLastRetryCount = rRetryCount;
                    mLastMultipleRetryCount = rMultipleRetryCount;
                    ///@}

                    break;

                case WifiManager.RSSI_PKTCNT_FETCH_FAILED:
                    // can happen if we are waiting to get a disconnect notification
                    if (DBG) logd("RSSI_FETCH_FAILED");
                    break;
                ///M:@{
                case EVENT_POOR_LINK_PROFILING_SETTINGS_CHANGE:
                    handlePoorLinkProfilingChange();
                    break;
                ///@}
                default:
                    return NOT_HANDLED;
            }
            return HANDLED;
        }

        ///M:@{
        @Override
        public void exit() {            
            mHandler.removeCallbacks(mPoorLinkProfilingInfo);
            hideLatencyPanel();
        }
        ///@}
   }

    /**
     * Child state of ConnectedState indicating that we are online and there is nothing to do.
     */
    class OnlineState extends State {
        @Override
        public void enter() {
            if (DBG) logd(getName());
        }

        @Override
        public boolean processMessage(Message msg) {
            switch (msg.what) {
                case EVENT_SCREEN_ON:
                    mIsScreenOn = true;
                    if (mPoorNetworkDetectionEnabled)
                        transitionTo(mOnlineWatchState);
                    break;
                default:
                    return NOT_HANDLED;
            }
            return HANDLED;
        }
    }

    private void updateCurrentBssid(String bssid) {
        if (DBG) logd("Update current BSSID to " + (bssid != null ? bssid : "null"));

        // if currently not connected, then set current BSSID to null
        if (bssid == null) {
            if (mCurrentBssid == null) return;
            mCurrentBssid = null;
            if (DBG) logd("BSSID changed");
            sendMessage(EVENT_BSSID_CHANGE);
            return;
        }

        // if it is already the current BSSID, then done
        if (mCurrentBssid != null && bssid.equals(mCurrentBssid.mBssid)) return;

        // search for the new BSSID in the cache, add to cache if not found
        mCurrentBssid = mBssidCache.get(bssid);
        if (mCurrentBssid == null) {
            mCurrentBssid = new BssidStatistics(bssid);
            mBssidCache.put(bssid, mCurrentBssid);
        }

        // send BSSID change notification
        if (DBG) logd("BSSID changed");
        sendMessage(EVENT_BSSID_CHANGE);
    }

    private int calculateSignalLevel(int rssi) {
        int signalLevel = WifiManager.calculateSignalLevel(rssi, WifiManager.RSSI_LEVELS);
        if (DBG)
            logd("RSSI current: " + mCurrentSignalLevel + " new: " + rssi + ", " + signalLevel);
        return signalLevel;
    }

    private void sendLinkStatusNotification(boolean isGood) {
        if (DBG) logd("########################################");

        if(mPoorLinkProfilingEnabled == true){

            if(isGood==true){
                toast = Toast.makeText(mContext, "LinkStatusNotification  Good", Toast.LENGTH_SHORT);
                toast.show();
            }else{
                toast = Toast.makeText(mContext, "LinkStatusNotification Poor", Toast.LENGTH_SHORT);
                toast.show();
            }
        }
        if (isGood) {
            mWsmChannel.sendMessage(GOOD_LINK_DETECTED);
            if (mCurrentBssid != null) {
                mCurrentBssid.mLastTimeGood = SystemClock.elapsedRealtime();
            }
            if (DBG) logd("Good link notification is sent");
        } else {
            mWsmChannel.sendMessage(POOR_LINK_DETECTED);
            if (mCurrentBssid != null) {
                mCurrentBssid.mLastTimePoor = SystemClock.elapsedRealtime();
            }
            logd("Poor link notification is sent");
        }
    }

    /**
     * Convenience function for retrieving a single secure settings value as a
     * boolean. Note that internally setting values are always stored as
     * strings; this function converts the string to a boolean for you. The
     * default value will be returned if the setting is not defined or not a
     * valid boolean.
     *
     * @param cr The ContentResolver to access.
     * @param name The name of the setting to retrieve.
     * @param def Value to return if the setting is not defined.
     * @return The setting's current value, or 'def' if it is not defined or not
     *         a valid boolean.
     */
    private static boolean getSettingsGlobalBoolean(ContentResolver cr, String name, boolean def) {
        return Settings.Global.getInt(cr, name, def ? 1 : 0) == 1;
    }

    /**
     * Convenience function for updating a single settings value as an integer.
     * This will either create a new entry in the table if the given name does
     * not exist, or modify the value of the existing row with that name. Note
     * that internally setting values are always stored as strings, so this
     * function converts the given value to a string before storing it.
     *
     * @param cr The ContentResolver to access.
     * @param name The name of the setting to modify.
     * @param value The new value for the setting.
     * @return true if the value was set, false on database errors
     */
    private static boolean putSettingsGlobalBoolean(ContentResolver cr, String name, boolean value) {
        return Settings.Global.putInt(cr, name, value ? 1 : 0);
    }

    /**
     * Bundle of good link count parameters
     */
    private static class GoodLinkTarget {
        public final int RSSI_ADJ_DBM;
        public final int SAMPLE_COUNT;
        public final int REDUCE_TIME_MS;
        public GoodLinkTarget(int adj, int count, int time) {
            RSSI_ADJ_DBM = adj;
            SAMPLE_COUNT = count;
            REDUCE_TIME_MS = time;
        }
    }

    /**
     * Bundle of max avoidance time parameters
     */
    private static class MaxAvoidTime {
        public final int TIME_MS;
        public final int MIN_RSSI_DBM;
        public MaxAvoidTime(int time, int rssi) {
            TIME_MS = time;
            MIN_RSSI_DBM = rssi;
        }
    }

    /**
     * Volume-weighted Exponential Moving Average (V-EMA)
     *    - volume-weighted:  each update has its own weight (number of packets)
     *    - exponential:      O(1) time and O(1) space for both update and query
     *    - moving average:   reflect most recent results and expire old ones
     */
    private class VolumeWeightedEMA {
        private double mValue;
        private double mVolume;
        private double mProduct;
        private final double mAlpha;

        public VolumeWeightedEMA(double coefficient) {
            mValue   = 0.0;
            mVolume  = 0.0;
            mProduct = 0.0;
            mAlpha   = coefficient;
        }

        public void update(double newValue, int newVolume) {
            if (newVolume <= 0) return;
            // core update formulas
            double newProduct = newValue * newVolume;
            mProduct = mAlpha * newProduct + (1 - mAlpha) * mProduct;
            mVolume  = mAlpha * newVolume  + (1 - mAlpha) * mVolume;
            mValue   = mProduct / mVolume;
        }
    }

    /**
     * Record (RSSI -> pakce loss %) mappings of one BSSID
     */
    private class BssidStatistics {

        /* MAC address of this BSSID */
        private final String mBssid;

        /* RSSI -> packet loss % mappings */
        private VolumeWeightedEMA[] mEntries;
        private int mRssiBase;
        private int mEntriesSize;

        /* Target to send good link notification, set when poor link is detected */
        private int mGoodLinkTargetRssi;
        private int mGoodLinkTargetCount;

        /* Index of GOOD_LINK_TARGET array */
        private int mGoodLinkTargetIndex;

        /* Timestamps of some last events */
        private long mLastTimeSample;
        private long mLastTimeGood;
        private long mLastTimePoor;

        /* Max time to avoid this BSSID */
        private long mBssidAvoidTimeMax;

        /**
         * Constructor
         *
         * @param bssid is the address of this BSSID
         */
        public BssidStatistics(String bssid) {
            this.mBssid = bssid;
            mRssiBase = BSSID_STAT_RANGE_LOW_DBM;
            mEntriesSize = BSSID_STAT_RANGE_HIGH_DBM - BSSID_STAT_RANGE_LOW_DBM + 1;
            mEntries = new VolumeWeightedEMA[mEntriesSize];
            for (int i = 0; i < mEntriesSize; i++)
                mEntries[i] = new VolumeWeightedEMA(EXP_COEFFICIENT_RECORD);
        }

        /**
         * Update this BSSID cache
         *
         * @param rssi is the RSSI
         * @param value is the new instant loss value at this RSSI
         * @param volume is the volume for this single update
         */
        public void updateLoss(int rssi, double value, int volume) {
            if (volume <= 0) return;
            int index = rssi - mRssiBase;
            if (index < 0 || index >= mEntriesSize) return;
            mEntries[index].update(value, volume);
            if (DBG) {
                DecimalFormat df = new DecimalFormat("#.##");
                logd("Cache updated: loss[" + rssi + "]=" + df.format(mEntries[index].mValue * 100)
                        + "% volume=" + df.format(mEntries[index].mVolume));
            }
        }

        /**
         * Get preset loss if the cache has insufficient data, observed from experiments.
         *
         * @param rssi is the input RSSI
         * @return preset loss of the given RSSI
         */
        public double presetLoss(int rssi) {
            if (rssi <= -90) return 1.0;
            if (rssi > 0) return 0.0;

            if (sPresetLoss == null) {
                // pre-calculate all preset losses only once, then reuse them
                final int size = 90;
                sPresetLoss = new double[size];
                for (int i = 0; i < size; i++) sPresetLoss[i] = 1.0 / Math.pow(90 - i, 1.5);
            }
            return sPresetLoss[-rssi];
        }

        /**
         * A poor link is detected, calculate a target RSSI to bring WiFi back.
         *
         * @param rssi is the current RSSI
         * @return true iff the current BSSID should be avoided
         */
        public boolean poorLinkDetected(int rssi) {
            if (DBG) logd("Poor link detected, rssi=" + rssi);

            long now = SystemClock.elapsedRealtime();
            long lastGood = now - mLastTimeGood;
            long lastPoor = now - mLastTimePoor;

            // reduce the difficulty of good link target if last avoidance was long time ago
            while (mGoodLinkTargetIndex > 0
                    && lastPoor >= GOOD_LINK_TARGET[mGoodLinkTargetIndex - 1].REDUCE_TIME_MS)
                mGoodLinkTargetIndex--;
            mGoodLinkTargetCount = GOOD_LINK_TARGET[mGoodLinkTargetIndex].SAMPLE_COUNT;

            // scan for a target RSSI at which the link is good
            int from = rssi + GOOD_LINK_RSSI_RANGE_MIN;
            int to = rssi + GOOD_LINK_RSSI_RANGE_MAX;
            mGoodLinkTargetRssi = findRssiTarget(from, to, mGoodLinkThreshold);
            mGoodLinkTargetRssi += GOOD_LINK_TARGET[mGoodLinkTargetIndex].RSSI_ADJ_DBM;
            if (mGoodLinkTargetIndex < GOOD_LINK_TARGET.length - 1) mGoodLinkTargetIndex++;

            // calculate max avoidance time to prevent avoiding forever
            int p = 0, pmax = MAX_AVOID_TIME.length - 1;
            while (p < pmax && rssi >= MAX_AVOID_TIME[p + 1].MIN_RSSI_DBM) p++;
            long avoidMax = MAX_AVOID_TIME[p].TIME_MS;

            // don't avoid if max avoidance time is 0 (RSSI is super high)
            if (avoidMax <= 0) return false;

            // set max avoidance time, send poor link notification
            mBssidAvoidTimeMax = now + avoidMax;

            if (DBG) logd("goodRssi=" + mGoodLinkTargetRssi + " goodCount=" + mGoodLinkTargetCount
                    + " lastGood=" + lastGood + " lastPoor=" + lastPoor + " avoidMax=" + avoidMax);

           ///M:@{
            if(mPoorLinkProfilingEnabled == true){
                String ss = "poorLinkDetected link speed=" +mLinkSpeed+" target rssi="+mGoodLinkTargetRssi;
                toast = Toast.makeText(mContext, ss, Toast.LENGTH_SHORT);
                toast.show();
            }
                   ///@}   

            return true;
        }

        /**
         * A new BSSID is connected, recalculate target RSSI threshold
         */
        public void newLinkDetected() {
            // if this BSSID is currently being avoided, the reuse those values
            if (mBssidAvoidTimeMax > 0) {
                if (DBG) logd("Previous avoidance still in effect, rssi=" + mGoodLinkTargetRssi
                        + " count=" + mGoodLinkTargetCount);
                return;
            }

            // calculate a new RSSI threshold for new link verifying
            int from = BSSID_STAT_RANGE_LOW_DBM;
            int to = BSSID_STAT_RANGE_HIGH_DBM;
            mGoodLinkTargetRssi = findRssiTarget(from, to, mGoodLinkThreshold);
            mGoodLinkTargetCount = 1;
            mBssidAvoidTimeMax = SystemClock.elapsedRealtime() + MAX_AVOID_TIME[0].TIME_MS;
            if (DBG) logd("New link verifying target set, rssi=" + mGoodLinkTargetRssi + " count="
                + mGoodLinkTargetCount);

           if(mPoorLinkProfilingEnabled == true){

               String ss = "newLinkDetected link speed=" +mLinkSpeed+"target  rssi="+mGoodLinkTargetRssi;
               toast = Toast.makeText(mContext, ss, Toast.LENGTH_SHORT);
               toast.show();
            }
        }

        /**
         * Return the first RSSI within the range where loss[rssi] < threshold
         *
         * @param from start scanning from this RSSI
         * @param to stop scanning at this RSSI
         * @param threshold target threshold for scanning
         * @return target RSSI
         */
        public int findRssiTarget(int from, int to, double threshold) {
            from -= mRssiBase;
            to -= mRssiBase;
            int emptyCount = 0;
            int d = from < to ? 1 : -1;

            logd("findRssiTarget start");
                    
            for (int i = from; i != to; i += d)
                // don't use a data point if it volume is too small (statistically unreliable)
                if (i >= 0 && i < mEntriesSize && mEntries[i].mVolume > 1.0) {
                    emptyCount = 0;
                    int rssi = mRssiBase + i;
                    DecimalFormat df = new DecimalFormat("#.##");
                    logd("["+i+"] rssi=" + rssi + " threshold="
                                    + df.format(threshold * 100) + "% value="
                                    + df.format(mEntries[i].mValue * 100) + "% volume="
                                    + df.format(mEntries[i].mVolume));
                    
                    if (mEntries[i].mValue < threshold) {
                        // scan target found
                        //int rssi = mRssiBase + i;
                        if (DBG) {
                            df = new DecimalFormat("#.##");
                            logd("Scan target found: rssi=" + rssi + " threshold="
                                    + df.format(threshold * 100) + "% value="
                                    + df.format(mEntries[i].mValue * 100) + "% volume="
                                    + df.format(mEntries[i].mVolume));
                        }
                        logd("findRssiTarget end");
                        return rssi;
                    }
                } else if (++emptyCount >= BSSID_STAT_EMPTY_COUNT) {
                    // cache has insufficient data around this RSSI, use preset loss instead
                    int rssi = mRssiBase + i;
                    double lossPreset = presetLoss(rssi);
                    DecimalFormat df = new DecimalFormat("#.##");
                     logd("["+i+"] rssi=" + rssi + " threshold="
                                    + df.format(threshold * 100) + "% value="
                                    + df.format(lossPreset * 100) + "% volume=preset");
                     
                    if (lossPreset < threshold) {
                        if (DBG) {
                             df = new DecimalFormat("#.##");
                            logd("Scan target found: rssi=" + rssi + " threshold="
                                    + df.format(threshold * 100) + "% value="
                                    + df.format(lossPreset * 100) + "% volume=preset");
                        }
                        logd("findRssiTarget end");
                        return rssi;
                    }
                }
            logd("findRssiTarget end");

            return mRssiBase + to;
        }
    }


    private void showLatencyPanel(int type) {
        logd( Thread.currentThread().getStackTrace()[2].getMethodName());
        
        LayoutInflater adbInflater = LayoutInflater.from(mContext);

        if(mLatencyPanelView!=null){
            mTextView.setText(
              "mPoorLinkThreshold:\n"+
              "mGoodLinkThreshold:\n"+ 
              "RSSI:\n" +
              "mGoodLinkTargetRssi:\n" +
              "mGoodLinkTargetCount:\n" +
              "mSampleCount:\n" +
              "mLinkSpeed:\n" +
              "p2pStart:\n" +
              "mCurrentLossValue:\n" +
              "mCurrentLossVolume:\n"
              );   
            return;
        }
   
      mLatencyPanelView = adbInflater.inflate(com.mediatek.internal.R.layout.textpanel, null);
            // text view        
            mTextView = (TextView) mLatencyPanelView.findViewById(com.mediatek.internal.R.id.bodyText);

      mTextView.setText(
              "mPoorLinkThreshold:\n"+
              "mGoodLinkThreshold:\n"+ 
              "RSSI:\n" +
              "mGoodLinkTargetRssi:\n" +
              "mGoodLinkTargetCount:\n" +
              "mSampleCount:\n" +
              "mLinkSpeed:\n" +
              "p2pStart:\n" +
              "mCurrentLossValue:\n" +
              "mCurrentLossVolume:\n"
              );       

        // layout param
        WindowManager.LayoutParams layoutParams;
        layoutParams = new WindowManager.LayoutParams();
        layoutParams.type = WindowManager.LayoutParams.TYPE_TOP_MOST; //ok

        layoutParams.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;

        layoutParams.width = WindowManager.LayoutParams.WRAP_CONTENT;
        layoutParams.height = WindowManager.LayoutParams.WRAP_CONTENT;
        layoutParams.gravity = Gravity.LEFT | Gravity.TOP;
        layoutParams.alpha = 0.7f;

        // add view to window manager
        WindowManager windowManager = (WindowManager)
            mContext.getSystemService(Context.WINDOW_SERVICE);

        windowManager.addView(mLatencyPanelView, layoutParams);        
    }

    private void hideLatencyPanel(){
        logd(Thread.currentThread().getStackTrace()[2].getMethodName());
        
        if (mLatencyPanelView != null) {
            // remove view to window manager
            WindowManager windowManager = (WindowManager)
                mContext.getSystemService(Context.WINDOW_SERVICE);

            windowManager.removeView(mLatencyPanelView);
            mLatencyPanelView = null;
        }        

        mTextView = null;      
    }

     private final Runnable mPoorLinkProfilingInfo = new Runnable() {
        @Override
        public void run() {
            DecimalFormat df = new DecimalFormat("#.##");
            

             mTextView.setText(
                "mPoorLinkThreshold: "+mPoorLinkThreshold+"\n"+
                "mGoodLinkThreshold: "+mGoodLinkThreshold+"\n"+        
                "RSSI: " + mLatestRssi + "\n" +
                "mGoodLinkTargetRssi: " + mCurrentBssid.mGoodLinkTargetRssi + "\n" +
                "mGoodLinkTargetCount: " + mCurrentBssid.mGoodLinkTargetCount  + "\n" +
                "mSampleCount: " + mSampleCount   + "\n" +
                "mLinkSpeed: " + mLinkSpeed  + "\n" +
                "p2pStart:" + p2pStart + "\n" +
                "mCurrentLossValue:" +  df.format(mCurrentLoss.mValue * 100) + "%" + "\n" +
                "mCurrentLossVolume:" +  df.format(mCurrentLoss.mVolume )  + "\n"
                );   
            
            mHandler.postDelayed(mPoorLinkProfilingInfo, POOR_LINK_PROFILING_PERIOD_MILLIS);
    
        }
    };    

    private void handlePoorLinkProfilingChange(){

        logd( "handleLatencyProfilingChange(), mPoorLinkProfilingEnabled=" + mPoorLinkProfilingEnabled);
        
        if (mPoorLinkProfilingEnabled == false || sWifiOnly || !mPoorNetworkDetectionEnabled){
            mHandler.removeCallbacks(mPoorLinkProfilingInfo);
            hideLatencyPanel();
            return;
        }        

        if(getCurrentState() == mVerifyingLinkState || getCurrentState() == mLinkMonitoringState)
        {
            showLatencyPanel((getCurrentState()==mVerifyingLinkState)?1:2);
            // Remove callback first
            mHandler.removeCallbacks(mPoorLinkProfilingInfo);
            mHandler.postDelayed(mPoorLinkProfilingInfo, POOR_LINK_PROFILING_PERIOD_MILLIS); 

        }else
        {
            hideLatencyPanel();
            // Stop profiling
            mHandler.removeCallbacks(mPoorLinkProfilingInfo);
        }
    }


    ///M: add the following

    public void setPoorLinkProfilingOn(boolean enable) {

        logd( "setPoorLinkProfilingOn:" + enable);
        mPoorLinkProfilingEnabled = enable;
        sendMessage(EVENT_POOR_LINK_PROFILING_SETTINGS_CHANGE );

    }
    
    public double getPoorLinkThreshold(boolean isGood) {

         logd(  "getPoorLinkThreshold, isGood:" + isGood);

         if(isGood) return mGoodLinkThreshold;
         else  return mPoorLinkThreshold;
     }

     public boolean setPoorLinkThreshold(boolean isGood, double threshold) {

         logd( "setPoorLinkThreshold, isGood:" + isGood +" threshold= "+threshold);
         double tmpgood;
         double tmppoor;
         if(isGood){
            tmpgood = threshold;
            tmppoor = mPoorLinkThreshold;
         }else{
             tmpgood = mGoodLinkThreshold;
             tmppoor = threshold;
         }
         if(tmpgood>tmppoor || tmpgood>1.0 || tmpgood<0 ||tmppoor>1.0 || tmppoor<0 ){
            logd(  "setPoorLinkThreshold, fail good= "+tmpgood +" poor ="+ tmppoor);
            return false;
         }
         if(isGood){
            mGoodLinkThreshold = threshold;
         }
         else mPoorLinkThreshold= threshold;

         return true;
         
     }
 

}
