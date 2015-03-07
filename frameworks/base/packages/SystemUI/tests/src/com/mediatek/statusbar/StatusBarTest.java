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
package com.mediatek.systemui.statusbar;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.app.Dialog;
import android.app.Instrumentation;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.StatusBarManager;
import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.database.ContentObserver;
import android.graphics.drawable.AnimationDrawable;
import android.location.LocationManager;
import android.media.AudioManager;
import android.net.ConnectivityManager;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.SystemClock;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.provider.Settings;
import android.service.notification.StatusBarNotification;
import android.telephony.PhoneStateListener;
import android.telephony.ServiceState;
import android.telephony.TelephonyManager;
import android.test.ActivityInstrumentationTestCase2;
import android.test.suitebuilder.annotation.LargeTest;
import android.test.TouchUtils;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.MotionEvent.PointerCoords;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.WindowManagerGlobal;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.LinearLayout.LayoutParams;
import android.widget.Toast;
import com.android.internal.view.menu.ActionMenuItemView;
import com.android.internal.telephony.ITelephony;
import com.android.internal.telephony.ITelephonyRegistry;
import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.TelephonyIntents;
import com.android.internal.telephony.TelephonyProperties;
import com.android.systemui.R;
import com.android.systemui.statusbar.phone.PhoneStatusBar;
import com.mediatek.audioprofile.AudioProfileManager;
import com.mediatek.audioprofile.AudioProfileManager.Scenario;
import com.mediatek.common.audioprofile.AudioProfileListener;
import com.mediatek.common.featureoption.FeatureOption;
import com.mediatek.common.telephony.ITelephonyEx;
import com.mediatek.CellConnService.CellConnMgr;
import com.mediatek.systemui.ext.PluginFactory;
import com.mediatek.systemui.statusbar.toolbar.SimIconsListView;
import com.mediatek.systemui.statusbar.util.SIMHelper;
import com.mediatek.systemui.statusbar.util.StateTracker;
import com.mediatek.xlog.Xlog;
import com.mediatek.systemui.TestUtils;
import com.mediatek.telephony.TelephonyManagerEx;
import com.mediatek.telephony.SimInfoManager;
import java.util.ArrayList;
import java.util.List;
import java.util.Comparator;
import java.util.Collections;

@LargeTest
public class StatusBarTest extends ActivityInstrumentationTestCase2<StatusBarViewActivity> {
    private final int SLEEP_TIME = 500;
    private final int WAITING_TIME = 35000;
    private static final int NO_SIM = 0;
    private static final int ONE_SIM = 1;
    private static final int TWO_SIM = 2;
    private static final int THREE_SIM = 3;
    private static final int FOUR_SIM = 4;

    private static final String TAG = "StatusBarTest";
    private Object mPhoneStatusBar;
    private Context mContext;
    private StatusBarViewActivity mStatusBarViewActivity;
    private Instrumentation mInstrumentation;
    private static int sId = 1;
    private static final int mSmallIcon = 0;
    private static final int mActionIcon = 0;
 
    public StatusBarTest() {
        super(StatusBarViewActivity.class);
    }

    private int getId() {
        return sId++;
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        setActivityInitialTouchMode(false);
        mStatusBarViewActivity = getActivity();
        mInstrumentation = getInstrumentation();
    }

    private void initTestView() {
        Log.v(TAG, "initTestView");
        mInstrumentation.runOnMainSync(new Runnable() {
            @Override
            public void run() {
                mStatusBarViewActivity.setTestView();
            }
        });
        getInstrumentation().waitForIdleSync();

        mContext = mStatusBarViewActivity.mSystemUIContext;
        assertNotNull(mContext);
        mConResolver=mContext.getContentResolver();
        assertNotNull(mConResolver);      
       
        mPhoneStatusBar = mStatusBarViewActivity.mPhoneStatusBar;
        assertNotNull(mPhoneStatusBar);
        mQS = TestUtils.getProperty(mPhoneStatusBar, "mQS");
        assertNotNull(mQS);
        mQuickSettingsConnectionModel = TestUtils.getProperty(mQS, "mQuickSettingsConnectionModel");
        assertNotNull(mQuickSettingsConnectionModel);

        mWifiTileView = TestUtils.getProperty(mQuickSettingsConnectionModel, "mWifiTileView");
        assertNotNull(mWifiTileView);
        mBluetoothTileView = TestUtils.getProperty(mQuickSettingsConnectionModel, "mBluetoothTileView");
        assertNotNull(mBluetoothTileView);
        mGpsTileView = TestUtils.getProperty(mQuickSettingsConnectionModel, "mLocationTileView");
        assertNotNull(mGpsTileView);
        mMobileTileView = TestUtils.getProperty(mQuickSettingsConnectionModel, "mMobileTileView");
        assertNotNull(mMobileTileView);
        mAirlineModeTileView = TestUtils.getProperty(mQuickSettingsConnectionModel, "mAirlineModeTileView");
        assertNotNull(mAirlineModeTileView);
        mAudioProfileTileView = TestUtils.getProperty(mQuickSettingsConnectionModel, "mAudioProfileTileView");
        assertNotNull(mAudioProfileTileView);
        mAutoRotateTileView = TestUtils.getProperty(mQuickSettingsConnectionModel, "mAutoRotateTileView");
        assertNotNull(mAutoRotateTileView);

        mMobileIcon = TestUtils.getProperty(mQuickSettingsConnectionModel, "mMobileIcon");
        assertNotNull(mMobileIcon);

        getSIMInfoList(mContext);

    }

    private ContentResolver mConResolver;
    private Object mQS;
    private Object mQuickSettingsConnectionModel;
    private Object mWifiTileView;
    private Object mBluetoothTileView;
    private Object mGpsTileView;
    private Object mMobileTileView;
    private Object mAirlineModeTileView;
    private Object mAudioProfileTileView;
    private Object mAutoRotateTileView;
    private Object mMobileIcon;

    private static final int SIM_STATUS_COUNT = 9;
    private static final int MOBILE_ICON_COUNT = 4;
    private static int[] sSimStatusViews;
    private static int[] sMobileIconResIds;
    private List<SimInfoManager.SimInfoRecord> sSimInfos;
    private int mSimNum = 0;

    public static final int MINIMUM_TIMEOUT = 15000;
    public static final int MEDIUM_TIMEOUT = 30000;
    public static final int MAXIMUM_TIMEOUT = 60000;
    public static final int MINIMUM_BACKLIGHT = 30;
    public static final int MAXIMUM_BACKLIGHT = 255;
    public static final int DEFAULT_BACKLIGHT = 102;
    public static final int STATE_DISABLED = 0;
    public static final int STATE_ENABLED = 1;
    public static final int STATE_TURNING_ON = 2;
    public static final int STATE_TURNING_OFF = 3;
 
    public void testDataForUnchangedBtn() {
        initTestView();
        Xlog.d(TAG,"***testDataForUnchangedBtn***");
        
        final int mResId = getMobileIconResource();
        checkMobileIconResource();

        TestUtils.invokeMethod(mWifiTileView, "callOnClick", new Class[] {}, new Object[] {});
        TestUtils.sleepBy(SLEEP_TIME);
        TestUtils.invokeMethod(mBluetoothTileView, "callOnClick", new Class[] {}, new Object[] {});
        TestUtils.sleepBy(SLEEP_TIME);
        TestUtils.invokeMethod(mGpsTileView, "callOnClick", new Class[] {}, new Object[] {});
        TestUtils.sleepBy(SLEEP_TIME);
        mInstrumentation.waitForIdleSync();
        checkMobileIconResource();
        assertEquals(mResId, getMobileIconResource()); /// Should be the same as the original state
    }

    public void testDataForAirplaneMode() {
        initTestView();
        Xlog.d(TAG,"***testDataForAirplaneMode***");

        final Object airlineModeStateTrackerObj = TestUtils.getProperty(mQuickSettingsConnectionModel, "mAirlineModeStateTracker");
        Integer actualStateStart = (Integer) TestUtils.invokeMethod(airlineModeStateTrackerObj, "getActualState",
                new Class[] { Context.class }, new Object[] { mContext });
        mStatusBarViewActivity.runOnUiThread(new Runnable() {
            public void run() {
                TestUtils.invokeMethod(airlineModeStateTrackerObj, "toggleState", new Class[] { Context.class },
                        new Object[] { mContext });
            }
        });
        TestUtils.sleepBy(10000);
        mInstrumentation.waitForIdleSync();
        checkMobileIconResource();
    }

    public void testDataForDataSIMChanged() {
        initTestView();
        Xlog.d(TAG,"***testDataForDataSIMChanged***");

        if (FeatureOption.MTK_GEMINI_SUPPORT) {
            Intent intent = new Intent();
            intent.putExtra(PhoneConstants.MULTI_SIM_ID_KEY, Settings.System.DEFAULT_SIM_NOT_SET);
            intent.setAction(Intent.ACTION_DATA_DEFAULT_SIM_CHANGED);
            mContext.sendBroadcast(intent);
        } else {
            ConnectivityManager cm = (ConnectivityManager) mContext.getSystemService(Context.CONNECTIVITY_SERVICE);
            boolean enabled = cm.getMobileDataEnabled();
            cm.setMobileDataEnabled(!enabled);
        }

        TestUtils.sleepBy(SLEEP_TIME);
        mInstrumentation.waitForIdleSync();
        checkMobileIconResource();
    }

    public void testDataForSwitchDataConnection() {
        initTestView();
        Xlog.d(TAG,"***testDataForSwitchDataConnection***");

        checkMobileIconResource();

        if (FeatureOption.MTK_GEMINI_SUPPORT) {
            switch (sSimInfos.size()) {
                case ONE_SIM:
                    TestUtils.invokeMethod(mMobileTileView, "callOnClick", new Class[] {}, new Object[] {});
                    mInstrumentation.waitForIdleSync();
                    break;

                case TWO_SIM:
                case THREE_SIM:
                case FOUR_SIM:
                    {
                        TestUtils.invokeMethod(mMobileTileView, "callOnClick", new Class[] {}, new Object[] {});                                                
                        /// Pop-up a dialog
                        mInstrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN);
                        mInstrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_ENTER);
                        mInstrumentation.waitForIdleSync();
                    }                
                    break;
                case NO_SIM:
                default:
                    ///do nothing
                    return;
            }
        } else {
            TestUtils.invokeMethod(mMobileTileView, "callOnClick", new Class[] {}, new Object[] {});
        }

        TestUtils.sleepBy(SLEEP_TIME);

        checkMobileIconResource();        
    }

    public void testLocationController() {
        initTestView();
        Log.v(TAG, "++++++++++ Start testLocationController ++++++++++");
        Object obj = TestUtils.getProperty(mPhoneStatusBar, "mLocationController");
        
        Intent intent = new Intent(LocationManager.GPS_FIX_CHANGE_ACTION);
        intent.putExtra(LocationManager.EXTRA_GPS_ENABLED, true);
        TestUtils.invokeMethod(obj, "onReceive", new Class[] { Context.class, Intent.class }, new Object[] { mContext,
                intent });
        
        intent = new Intent(LocationManager.GPS_ENABLED_CHANGE_ACTION);
        intent.putExtra(LocationManager.EXTRA_GPS_ENABLED, false);
        TestUtils.invokeMethod(obj, "onReceive", new Class[] { Context.class, Intent.class }, new Object[] { mContext,
                intent });
        
        intent = new Intent("android.location.PRIVACY_OVERWRITE");
        TestUtils.invokeMethod(obj, "onReceive", new Class[] { Context.class, Intent.class }, new Object[] { mContext,
                intent });
        Log.v(TAG, "++++++++++ end testLocationController ++++++++++");
    }

    public void testBrightness() {
        initTestView();
        Log.v(TAG, "++++++++++ Start testBrightness ++++++++++");
        mStatusBarViewActivity.runOnUiThread(new Runnable() {
            public void run() {
                TestUtils.invokeMethod(mQS, "showBrightnessDialog", new Class[] {}, new Object[] {});            }
        });

        TestUtils.sleepBy(1000);
/*
        Object obj = mQuickSettingsConnectionModel;
        Integer brightnessStart = (Integer) TestUtils.invokeMethod(obj, "getBrightness",
                new Class[] { Context.class }, new Object[] { mContext });
        Boolean isAutoBrightnessStart = (Boolean) TestUtils.invokeMethod(obj, "getBrightnessMode",
                new Class[] { Context.class }, new Object[] { mContext });
        TestUtils.invokeMethod(obj, "toggleBrightness", new Class[] { Context.class }, new Object[] { mContext });
        Integer brightnessEnd = (Integer) TestUtils.invokeMethod(obj, "getBrightness", new Class[] { Context.class },
                new Object[] { mContext });
        Boolean isAutoBrightnessEnd = (Boolean) TestUtils.invokeMethod(obj, "getBrightnessMode",
                new Class[] { Context.class }, new Object[] { mContext });
        Log.d(TAG, "brightnessStart = " + brightnessStart + ", isAutoBrightnessStart = " + isAutoBrightnessStart
                + ", brightnessEnd = " + brightnessEnd + ", isAutoBrightnessEnd = " + isAutoBrightnessStart);
        if (brightnessStart == MINIMUM_BACKLIGHT && !isAutoBrightnessStart) {
            assertFalse(isAutoBrightnessStart);
            assertEquals(DEFAULT_BACKLIGHT, (int) brightnessEnd);
        } else if (brightnessStart == DEFAULT_BACKLIGHT && !isAutoBrightnessStart) {
            assertFalse(isAutoBrightnessStart);
            assertEquals(MAXIMUM_BACKLIGHT, (int) brightnessEnd);
        } else if (brightnessStart == MAXIMUM_BACKLIGHT && !isAutoBrightnessStart) {
            assertFalse(isAutoBrightnessStart);
            assertEquals(MAXIMUM_BACKLIGHT, (int) brightnessEnd);
        } else if (brightnessStart == MAXIMUM_BACKLIGHT && isAutoBrightnessStart) {
            assertTrue(isAutoBrightnessStart);
            assertEquals(MINIMUM_BACKLIGHT, (int) brightnessEnd);
        }
*/

        Log.v(TAG, "---------- end testBrightness ----------");
    }

    public void testTimeout() {
        initTestView();
        Log.v(TAG, "++++++++++ Start testTimeout ++++++++++");
        Object obj = mQuickSettingsConnectionModel;
        Integer timeoutStart = (Integer) TestUtils.invokeMethod(obj, "getTimeout", new Class[] { Context.class },
                new Object[] { mContext });
        TestUtils.invokeMethod(obj, "toggleTimeout", new Class[] { Context.class }, new Object[] { mContext });
        Integer timeoutEnd = (Integer) TestUtils.invokeMethod(obj, "getTimeout", new Class[] { Context.class },
                new Object[] { mContext });
        mInstrumentation.waitForIdleSync();
        if (timeoutStart == MAXIMUM_TIMEOUT) {
            assertEquals(MINIMUM_TIMEOUT, (int) timeoutEnd);
        } else if (timeoutStart == MINIMUM_TIMEOUT) {
            assertEquals(MEDIUM_TIMEOUT, (int) timeoutEnd);
        } else if (timeoutStart == MEDIUM_TIMEOUT) {
            assertEquals(MAXIMUM_TIMEOUT, (int) timeoutEnd);
        }
        Log.v(TAG, "---------- end testTimeout ----------");
    }

    public void testAutoRotation() {
        initTestView();
        Log.v(TAG, "++++++++++ Start testAutoRotation ++++++++++");
        final Object autoRotationObj = TestUtils.getProperty(mQuickSettingsConnectionModel, "mAutoRotationStateTracker");
        Integer actualState = (Integer) TestUtils.invokeMethod(autoRotationObj, "getActualState",
                new Class[] { Context.class }, new Object[] { mContext });        
        int state = 
            Settings.System.getInt(mConResolver, Settings.System.ACCELEROMETER_ROTATION, -1);
        mInstrumentation.waitForIdleSync();
        assertEquals(actualState, (Integer)state);
        Log.v(TAG, "---------- end testAutoRotation ----------");
    }

    public void testWifi() {
        initTestView();
        Log.v(TAG, "++++++++++ Start testWifi ++++++++++");
        Object wifiStateTrackerObj = TestUtils.getProperty(mQuickSettingsConnectionModel, "mWifiStateTracker");
        Integer actualStateStart = (Integer) TestUtils.invokeMethod(wifiStateTrackerObj, "getActualState",
                new Class[] { Context.class }, new Object[] { mContext });
        if (actualStateStart == STATE_DISABLED) {
            TestUtils.invokeMethod(wifiStateTrackerObj, "requestStateChange", new Class[] { Context.class, boolean.class },
                    new Object[] { mContext, true });
        } else if (actualStateStart == STATE_ENABLED) {
            TestUtils.invokeMethod(wifiStateTrackerObj, "requestStateChange", new Class[] { Context.class, boolean.class },
                    new Object[] { mContext, false });
        }
        TestUtils.sleepBy(6000);
        Integer actualStateEnd = (Integer) TestUtils.invokeMethod(wifiStateTrackerObj, "getActualState",
                new Class[] { Context.class }, new Object[] { mContext });
        mInstrumentation.waitForIdleSync();
        if (actualStateStart == STATE_DISABLED) {
            assertTrue(actualStateEnd == STATE_ENABLED || actualStateEnd == STATE_TURNING_ON);
        } else if (actualStateStart == STATE_ENABLED) {
            assertTrue(actualStateEnd == STATE_DISABLED || actualStateEnd == STATE_TURNING_OFF);
        }
        actualStateStart = (Integer) TestUtils.invokeMethod(wifiStateTrackerObj, "getActualState",
                new Class[] { Context.class }, new Object[] { mContext });
        if (actualStateStart == STATE_DISABLED) {
            TestUtils.invokeMethod(wifiStateTrackerObj, "requestStateChange", new Class[] { Context.class, boolean.class },
                    new Object[] { mContext, true });
        } else if (actualStateStart == STATE_ENABLED) {
            TestUtils.invokeMethod(wifiStateTrackerObj, "requestStateChange", new Class[] { Context.class, boolean.class },
                    new Object[] { mContext, false });
        }
        Log.v(TAG, "---------- end testWifi ----------");
    }

    public void testBluetooth() {
        initTestView();
        Log.v(TAG, "++++++++++ Start testBluetooth ++++++++++");
        Object bluetoothStateTrackerObj = TestUtils.getProperty(mQuickSettingsConnectionModel, "mBluetoothStateTracker");
        Integer actualStateStart = (Integer) TestUtils.invokeMethod(bluetoothStateTrackerObj, "getActualState",
                new Class[] { Context.class }, new Object[] { mContext });
        if (actualStateStart == STATE_DISABLED) {
            TestUtils.invokeMethod(bluetoothStateTrackerObj, "requestStateChange", new Class[] { Context.class,
                    boolean.class }, new Object[] { mContext, true });
        } else if (actualStateStart == STATE_ENABLED) {
            TestUtils.invokeMethod(bluetoothStateTrackerObj, "requestStateChange", new Class[] { Context.class,
                    boolean.class }, new Object[] { mContext, false });
        }
        TestUtils.sleepBy(1000);
        Integer actualStateEnd = (Integer) TestUtils.invokeMethod(bluetoothStateTrackerObj, "getActualState",
                new Class[] { Context.class }, new Object[] { mContext });
        mInstrumentation.waitForIdleSync();
        if (actualStateStart == STATE_DISABLED) {
            assertTrue(actualStateEnd == STATE_ENABLED || actualStateEnd == STATE_TURNING_ON);
        } else if (actualStateStart == STATE_ENABLED) {
            assertTrue(actualStateEnd == STATE_DISABLED || actualStateEnd == STATE_TURNING_OFF);
        }
        Log.v(TAG, "---------- end testBluetooth ----------");
    }

    public void testMobileConnect() {
        initTestView();
        Log.v(TAG, "++++++++++ Start testMobileConnect ++++++++++");
        Object mobileStateTrackerObj = TestUtils.getProperty(mQuickSettingsConnectionModel, "mMobileStateTracker");
        Boolean isClickable = (Boolean) TestUtils.invokeMethod(mobileStateTrackerObj, "isClickable", null, null);
        if (isClickable) {
            Integer actualStateStart = (Integer) TestUtils.invokeMethod(mobileStateTrackerObj, "getActualState",
                    new Class[] { Context.class }, new Object[] { mContext });
            TestUtils.invokeMethod(mobileStateTrackerObj, "toggleState", new Class[] { Context.class },
                    new Object[] { mContext });
            TestUtils.sleepBy(1000);
        } else {
            assertFalse(isClickable);
        }
        mInstrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN);
        mInstrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_ENTER);
        mInstrumentation.waitForIdleSync();
        TestUtils.sleepBy(1000);
        mInstrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_RIGHT);
        mInstrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_ENTER);
        mInstrumentation.waitForIdleSync();
        TestUtils.sleepBy(1000);
        Log.v(TAG, "---------- end testMobileConnect ----------");
    }

    public void testAirplaneMode() {
        initTestView();
        TestUtils.sleepBy(2000);
        Log.v(TAG, "++++++++++ Start testAirplaneMode ++++++++++");
        final Object airlineModeStateTrackerObj = TestUtils.getProperty(mQuickSettingsConnectionModel, "mAirlineModeStateTracker");
        Integer actualStateStart = (Integer) TestUtils.invokeMethod(airlineModeStateTrackerObj, "getActualState",
                new Class[] { Context.class }, new Object[] { mContext });
        mStatusBarViewActivity.runOnUiThread(new Runnable() {
            public void run() {
                TestUtils.invokeMethod(airlineModeStateTrackerObj, "toggleState", new Class[] { Context.class },
                        new Object[] { mContext });
            }
        });
        TestUtils.sleepBy(10000);
        Integer actualStateEnd = (Integer) TestUtils.invokeMethod(airlineModeStateTrackerObj, "getActualState",
                new Class[] { Context.class }, new Object[] { mContext });
        mInstrumentation.waitForIdleSync();
        if (actualStateStart == STATE_DISABLED) {
            assertTrue(actualStateEnd == STATE_ENABLED);
        } else if (actualStateStart == STATE_ENABLED) {
            assertTrue(actualStateEnd == STATE_DISABLED);
        }
        mStatusBarViewActivity.runOnUiThread(new Runnable() {
            public void run() {
                TestUtils.invokeMethod(airlineModeStateTrackerObj, "toggleState", new Class[] { Context.class },
                        new Object[] { mContext });
            }
        });
        TestUtils.sleepBy(1000);
        mInstrumentation.waitForIdleSync();
        Log.v(TAG, "---------- end testAirplaneMode ----------");
    }

    public void testGPS() {
        initTestView();
        Log.v(TAG, "++++++++++ Start testGPS ++++++++++");

        Object ctrler = TestUtils.getProperty(mPhoneStatusBar, "mLocationController");
        Boolean actualStateStart = 
            (Boolean) TestUtils.invokeMethod(ctrler, "isLocationEnabled", new Class[] {}, new Object[] {});

        mStatusBarViewActivity.runOnUiThread(new Runnable() {
            public void run() {
                TestUtils.invokeMethod(mGpsTileView, "callOnClick", new Class[] {}, new Object[] {});
            }
        });
        TestUtils.sleepBy(1000);
        Boolean actualStateEnd = 
            (Boolean) TestUtils.invokeMethod(ctrler, "isLocationEnabled", new Class[] {}, new Object[] {});
        mInstrumentation.waitForIdleSync();
        assertEquals(actualStateStart, actualStateEnd);
 
        Log.v(TAG, "---------- end testGPS ----------");
    }

    public void testProfileSwitchPanelGeneral() {
        initTestView();
        Log.v(TAG, "++++++++++ Start testProfileSwitchPanelGeneral ++++++++++");
        final Object obj = mQuickSettingsConnectionModel;
        TestUtils.invokeMethod(obj, "updateAudioProfile", new Class[] { String.class },
                new Object[] { "mtk_audioprofile_general" });
        final Scenario senario = AudioProfileManager.getScenario("mtk_audioprofile_general");
        mStatusBarViewActivity.runOnUiThread(new Runnable() {
            public void run() {
                TestUtils.invokeMethod(obj, "updateProfileView", new Class[] { Scenario.class }, new Object[] { senario });
            }
        });
        assertEquals(Scenario.GENERAL, senario);
        Log.v(TAG, "---------- end testProfileSwitchPanelGeneral ----------");
    }

    public void testProfileSwitchPanelSilent() {
        initTestView();    
        Log.v(TAG, "++++++++++ Start testProfileSwitchPanelSilent ++++++++++");
        final Object obj = mQuickSettingsConnectionModel;
        TestUtils.invokeMethod(obj, "updateAudioProfile", new Class[] { String.class },
                new Object[] { "mtk_audioprofile_silent" });
        final Scenario senario = AudioProfileManager.getScenario("mtk_audioprofile_silent");
        mStatusBarViewActivity.runOnUiThread(new Runnable() {
            public void run() {
                TestUtils.invokeMethod(obj, "updateProfileView", new Class[] { Scenario.class }, new Object[] { senario });
            }
        });
        assertEquals(Scenario.SILENT, senario);
        Log.v(TAG, "---------- end testProfileSwitchPanelSilent ----------");
    }

    public void testProfileSwitchPanelMeeting() {
        initTestView();
        Log.v(TAG, "++++++++++ Start testProfileSwitchPanelMeeting ++++++++++");
        final Object obj = mQuickSettingsConnectionModel;
        TestUtils.invokeMethod(obj, "updateAudioProfile", new Class[] { String.class },
                new Object[] { "mtk_audioprofile_meeting" });
        final Scenario senario = AudioProfileManager.getScenario("mtk_audioprofile_meeting");
        mStatusBarViewActivity.runOnUiThread(new Runnable() {
            public void run() {
                TestUtils.invokeMethod(obj, "updateProfileView", new Class[] { Scenario.class }, new Object[] { senario });
            }
        });
        assertEquals(Scenario.MEETING, senario);
        Log.v(TAG, "---------- end testProfileSwitchPanelMeeting ----------");
    }

    public void testProfileSwitchPanelOutdoor() {
        initTestView();
        Log.v(TAG, "++++++++++ Start testProfileSwitchPanelOutdoor ++++++++++");
        final Object obj = mQuickSettingsConnectionModel;
        TestUtils.invokeMethod(obj, "updateAudioProfile", new Class[] { String.class },
                new Object[] { "mtk_audioprofile_outdoor" });
        final Scenario senario = AudioProfileManager.getScenario("mtk_audioprofile_outdoor");
        mStatusBarViewActivity.runOnUiThread(new Runnable() {
            public void run() {
                TestUtils.invokeMethod(obj, "updateProfileView", new Class[] { Scenario.class }, new Object[] { senario });
            }
        });
        assertEquals(Scenario.OUTDOOR, senario);
        Log.v(TAG, "---------- end testProfileSwitchPanelOutdoor ----------");
    }

    public void testCurrentScreen() {
        initTestView();
        Log.v(TAG, "++++++++++ Start testCurrentScreen ++++++++++");
        final Object mToolBarView = TestUtils.getProperty(mPhoneStatusBar, "mToolBarView");
        Boolean isDefaultScreen = (Boolean) TestUtils.invokeMethod(mToolBarView, "isDefaultScreenShowing", null, null);
        assertTrue(isDefaultScreen);

        Integer currentScreenStart = (Integer) TestUtils.invokeMethod(mToolBarView, "getCurrentScreen", null, null);
        mStatusBarViewActivity.runOnUiThread(new Runnable() {
            public void run() {
                TestUtils.invokeMethod(mToolBarView, "setCurrentScreen", new Class[] { int.class, int.class }, new Object[] {
                        0, 0 });
            }
        });
        TestUtils.sleepBy(1000);
        Integer currentScreenEnd = (Integer) TestUtils.invokeMethod(mToolBarView, "getCurrentScreen", null, null);
        if (currentScreenStart > 0) {
            assertEquals((int) currentScreenStart - 1, (int) currentScreenEnd);
        } else {
            assertEquals((int) currentScreenStart, (int) currentScreenEnd);
        }

        mStatusBarViewActivity.runOnUiThread(new Runnable() {
            public void run() {
                TestUtils.invokeMethod(mToolBarView, "scrollRight", null, null);
            }
        });
        TestUtils.sleepBy(1000);
        currentScreenEnd = (Integer) TestUtils.invokeMethod(mToolBarView, "getCurrentScreen", null, null);
        assertEquals(0, (int) currentScreenEnd);

        mStatusBarViewActivity  .runOnUiThread(new Runnable() {
            public void run() {
                TestUtils.invokeMethod(mToolBarView, "scrollLeft", null, null);
            }
        });
        TestUtils.sleepBy(1000);
        currentScreenEnd = (Integer) TestUtils.invokeMethod(mToolBarView, "getCurrentScreen", null, null);
        assertEquals(0, (int) currentScreenEnd);
        Log.v(TAG, "---------- end testCurrentScreen ----------");
    }

    private void checkMobileIconResource() {
        assertEquals(getMobileIconResource(), getSpecMobileResource());
    }

    private int getMobileIconResource() {
        return (Integer) TestUtils.getProperty(mMobileIcon, "mResource");
    }

    private int getSpecMobileResource() {
        if (FeatureOption.MTK_GEMINI_SUPPORT) {
            long simId = Settings.System.getLong(mConResolver, Settings.System.GPRS_CONNECTION_SIM_SETTING,Settings.System.DEFAULT_SIM_NOT_SET);
            if (simId > 0) {
                SimInfoManager.SimInfoRecord simInfo = this.getSIMInfo(mContext, simId);
                if (simInfo != null) {
                    int slotId = simInfo.mSimSlotId;
                    if (isRadioOff(slotId)) {
                        return R.drawable.ic_qs_mobile_disable;
                    } else {
                        return this.getDataConnectionIconIdBySlotId(mContext, slotId);
                    }
                }
            }            
        } else {
            ConnectivityManager cm = (ConnectivityManager) mContext.getSystemService(Context.CONNECTIVITY_SERVICE);
            boolean enabled = cm.getMobileDataEnabled();
            if (enabled) {
                if (isRadioOff(0)) {
                    return R.drawable.ic_qs_mobile_disable;
                } else {
                    return R.drawable.ic_qs_mobile_enable;
                }
            }
        }
        return R.drawable.ic_qs_mobile_off;
    }

    private boolean isRadioOff(int SlotId) {
        boolean isRadioOff = true;
        
        try {
            if (FeatureOption.MTK_GEMINI_SUPPORT) {
                ITelephonyEx mTelephony = ITelephonyEx.Stub.asInterface(ServiceManager.getService("phoneEx"));
                if (mTelephony != null) {
                    isRadioOff = !mTelephony.isRadioOn(SlotId);
                }
            } else {
                ITelephony mTelephony = ITelephony.Stub.asInterface(ServiceManager.getService(Context.TELEPHONY_SERVICE));
                if (mTelephony != null) {
                    isRadioOff = !mTelephony.isRadioOn();
                }
            }
        } catch (RemoteException e) {
            Xlog.e(TAG, "MobileStateTracker: isRadioOff() mTelephony exception");
        }

        Xlog.d(TAG, "MobileStateTracker: isRadioOff() is " + isRadioOff + ", slotId=" + SlotId);

        return isRadioOff;
    }

    private boolean hasSimCard(){
        boolean isSimCardInserted = false;
        isSimCardInserted=(com.mediatek.telephony.SimInfoManager.getInsertedSimCount(mContext) != 0);
        Xlog.d(TAG,"hasSimCard = "+isSimCardInserted);
        return isSimCardInserted;
    }

    private List<SimInfoManager.SimInfoRecord> getSortedSIMInfoList(Context context) {
        List<SimInfoManager.SimInfoRecord> simInfoList = SimInfoManager.getInsertedSimInfoList(context);
        Collections.sort(simInfoList, new Comparator<SimInfoManager.SimInfoRecord>() {
            @Override
            public int compare(SimInfoManager.SimInfoRecord a, SimInfoManager.SimInfoRecord b) {
                if(a.mSimSlotId < b.mSimSlotId) {
                    return -1;
                } else if (a.mSimSlotId > b.mSimSlotId) {
                    return 1;
                } else {
                    return 0;
                }
            }
        });
        return simInfoList;
    }

    public List<SimInfoManager.SimInfoRecord> getSIMInfoList(Context context) {
        if (sSimInfos == null || sSimInfos.size() == 0) {
            sSimInfos = getSortedSIMInfoList(context);
        }
        return sSimInfos;
    }

    private SimInfoManager.SimInfoRecord getSIMInfo(Context context, long simId) {
        if (sSimInfos == null || sSimInfos.size() == 0) {
            getSIMInfoList(context);
        }
        for (SimInfoManager.SimInfoRecord info : sSimInfos) {
            if (info.mSimInfoId == simId) {
                return info;
            }
        }
        return null;
    }

    private int getDataConnectionIconIdBySlotId(Context context, int slotId) {
        SimInfoManager.SimInfoRecord simInfo = getSIMInfoBySlot(context, slotId);
        if (simInfo == null) {
            return -1;
        }
        if (sMobileIconResIds == null) {
            initMobileIcons();
        }
        if (simInfo.mColor == -1) {
            return -1;
        } else {
            return sMobileIconResIds[simInfo.mColor];
        }
    }

    public SimInfoManager.SimInfoRecord getSIMInfoBySlot(Context context, int slotId) {
            if (sSimInfos == null || sSimInfos.size() == 0) {
                getSIMInfoList(context);
            }
            if (sSimInfos == null) {
                return null;
            }
            if (slotId == -1 && sSimInfos.size() > 0) {
                return sSimInfos.get(0);
            }
            for (SimInfoManager.SimInfoRecord info : sSimInfos) {
                if (info.mSimSlotId == slotId) {
                    return info;
                }
            }
            return null;
        }

        public void initStatusIcons() {
            if (sSimStatusViews == null) {
                sSimStatusViews = new int[SIM_STATUS_COUNT];
                sSimStatusViews[PhoneConstants.SIM_INDICATOR_RADIOOFF] = com.mediatek.internal.R.drawable.sim_radio_off;
                sSimStatusViews[PhoneConstants.SIM_INDICATOR_LOCKED] = com.mediatek.internal.R.drawable.sim_locked;
                sSimStatusViews[PhoneConstants.SIM_INDICATOR_INVALID] = com.mediatek.internal.R.drawable.sim_invalid;
                sSimStatusViews[PhoneConstants.SIM_INDICATOR_SEARCHING] = com.mediatek.internal.R.drawable.sim_searching;
                sSimStatusViews[PhoneConstants.SIM_INDICATOR_ROAMING] = com.mediatek.internal.R.drawable.sim_roaming;
                sSimStatusViews[PhoneConstants.SIM_INDICATOR_CONNECTED] = com.mediatek.internal.R.drawable.sim_connected;
                sSimStatusViews[PhoneConstants.SIM_INDICATOR_ROAMINGCONNECTED] = com.mediatek.internal.R.drawable.sim_roaming_connected;
            }
        }

        public void initMobileIcons() {
            if (sMobileIconResIds == null) {
                sMobileIconResIds = new int[MOBILE_ICON_COUNT];
                sMobileIconResIds[0] = R.drawable.ic_qs_mobile_blue;
                sMobileIconResIds[1] = R.drawable.ic_qs_mobile_orange;
                sMobileIconResIds[2] = R.drawable.ic_qs_mobile_green;
                sMobileIconResIds[3] = R.drawable.ic_qs_mobile_purple;
            }
        }

        public long getSIMIdBySlot(Context context, int slotId) {
            SimInfoManager.SimInfoRecord simInfo = getSIMInfoBySlot(context, slotId);
            if (simInfo == null) {
                return 0;
            }
            return simInfo.mSimInfoId;
        }

        public int getSIMColorIdBySlot(Context context, int slotId) {
            SimInfoManager.SimInfoRecord simInfo = getSIMInfoBySlot(context, slotId);
            if (simInfo == null) {
                return -1;
            }
            return simInfo.mColor;
        }


    public void test007SystemUIVisibility() {
        initTestView();
        Log.v(TAG, "++++++++++ Start test007SystemUIVisibility ++++++++++");
        TestUtils.invokeMethod(mPhoneStatusBar, "setLightsOn", new Class[] { boolean.class }, new Object[] { true });
        TestUtils.sleepBy(1000);
        Boolean obj = (Boolean) TestUtils.invokeMethod(mPhoneStatusBar, "areLightsOn", null, null);
        assertTrue(obj);
        TestUtils.invokeMethod(mPhoneStatusBar, "setLightsOn", new Class[] { boolean.class }, new Object[] { false });
        TestUtils.sleepBy(1000);
        obj = (Boolean) TestUtils.invokeMethod(mPhoneStatusBar, "areLightsOn", null, null);
        assertFalse(obj);
        Log.v(TAG, "---------- end test007SystemUIVisibility ----------");
    }

    public void test006RefreshExpandedView() {
        initTestView();
        Log.v(TAG, "++++++++++ Start test006RefreshExpandedView ++++++++++");
        TestUtils.invokeMethod(mPhoneStatusBar, "refreshExpandedView", new Class[] { Context.class },
                new Object[] { mContext });
        TestUtils.sleepBy(1000);
        Log.v(TAG, "---------- end test006RefreshExpandedView ----------");
    }


    public void test005OnReceive() {
        initTestView();
        Log.v(TAG, "++++++++++ Start test005OnReceive ++++++++++");
        Object obj = TestUtils.getProperty(mPhoneStatusBar, "mBroadcastReceiver");
        Intent intent = new Intent(Intent.ACTION_CONFIGURATION_CHANGED);
        TestUtils.invokeMethod(obj, "onReceive", new Class[] { Context.class, Intent.class }, new Object[] { mContext,
                intent });
        TestUtils.sleepBy(1000);
        Log.v(TAG, "---------- end test005OnReceive ----------");
    }

    // / M: Support the scale notification item.
    public void test004ExpandItem() {
        Log.v(TAG, "++++++++++ Start test004ExpandItem ++++++++++");
        registerProvisioningObserver();
        int id = getId();
        Intent intent = new Intent();
        intent.setAction(Intent.ACTION_WEB_SEARCH);
        PendingIntent pendingIntent = PendingIntent.getActivity(mStatusBarViewActivity, 0, intent, 0);
        Notification.Builder builder = new Notification.Builder(mStatusBarViewActivity).setTicker("ticker").setContentTitle(
                "content title").setContentText("context text").setSmallIcon(mSmallIcon).addAction(mActionIcon, "action",
                pendingIntent);
        NotificationManager nm = (NotificationManager) mContext.getSystemService(Activity.NOTIFICATION_SERVICE);
        Notification.BigTextStyle expandedBuilder = new Notification.BigTextStyle(builder);
        expandedBuilder.bigText("Big text");
        nm.notify(id, expandedBuilder.build());

        TestUtils.sleepBy(1000);
        ViewGroup viewGroup = (ViewGroup) TestUtils.getSuperClassProperty(mPhoneStatusBar, "mPile");
        View view = viewGroup.getChildAt(0);
        if (view != null) {
            int height = view.getHeight();

            PointerCoords[] coords = new PointerCoords[2];
            PointerCoords pointerCoords = new PointerCoords();
            pointerCoords.size = 0.2f;
            pointerCoords.pressure = 1.0f;

            pointerCoords.x = 150;
            pointerCoords.y = 300;
            coords[0] = pointerCoords;
            MotionEvent event = MotionEvent.obtain(SystemClock.uptimeMillis(), SystemClock.uptimeMillis(),
                    MotionEvent.ACTION_DOWN, 1, new int[] { 0 }, coords, 0, 0, 0, 0, 0, 0, 0);
            mInstrumentation.sendPointerSync(event);

            pointerCoords.x = 450;
            pointerCoords.y = 400;
            coords[1] = pointerCoords;
            event = MotionEvent.obtain(SystemClock.uptimeMillis(), SystemClock.uptimeMillis(), 261, 2, new int[] { 0, 1 },
                    coords, 0, 0, 0, 0, 0, 0, 0);
            mInstrumentation.sendPointerSync(event);
            TestUtils.sleepBy(500);

            pointerCoords.x = 200;
            pointerCoords.y = 310;
            coords[1] = pointerCoords;
            event = MotionEvent.obtain(SystemClock.uptimeMillis(), SystemClock.uptimeMillis(), MotionEvent.ACTION_MOVE, 2,
                    new int[] { 0, 1 }, coords, 0, 0, 0, 0, 0, 0, 0);
            mInstrumentation.sendPointerSync(event);
            TestUtils.sleepBy(500);

            event = MotionEvent.obtain(SystemClock.uptimeMillis(), SystemClock.uptimeMillis(), 262, 2, new int[] { 0, 1 },
                    coords, 0, 0, 0, 0, 0, 0, 0);
            mInstrumentation.sendPointerSync(event);
            event = MotionEvent.obtain(SystemClock.uptimeMillis(), SystemClock.uptimeMillis(), MotionEvent.ACTION_UP, 1,
                    new int[] { 0 }, coords, 0, 0, 0, 0, 0, 0, 0);
            mInstrumentation.sendPointerSync(event);
            TestUtils.sleepBy(500);
            assertTrue(view.getHeight() <= height);

            pointerCoords.x = 150;
            pointerCoords.y = 300;
            coords[0] = pointerCoords;
            event = MotionEvent.obtain(SystemClock.uptimeMillis(), SystemClock.uptimeMillis(), MotionEvent.ACTION_DOWN, 1,
                    new int[] { 0 }, coords, 0, 0, 0, 0, 0, 0, 0);
            mInstrumentation.sendPointerSync(event);

            pointerCoords.x = 200;
            pointerCoords.y = 310;
            coords[1] = pointerCoords;
            event = MotionEvent.obtain(SystemClock.uptimeMillis(), SystemClock.uptimeMillis(), 261, 2, new int[] { 0, 1 },
                    coords, 0, 0, 0, 0, 0, 0, 0);
            mInstrumentation.sendPointerSync(event);
            TestUtils.sleepBy(500);

            pointerCoords.x = 450;
            pointerCoords.y = 400;
            coords[1] = pointerCoords;
            event = MotionEvent.obtain(SystemClock.uptimeMillis(), SystemClock.uptimeMillis(), MotionEvent.ACTION_MOVE, 2,
                    new int[] { 0, 1 }, coords, 0, 0, 0, 0, 0, 0, 0);
            mInstrumentation.sendPointerSync(event);
            TestUtils.sleepBy(500);

            event = MotionEvent.obtain(SystemClock.uptimeMillis(), SystemClock.uptimeMillis(), 262, 2, new int[] { 0, 1 },
                    coords, 0, 0, 0, 0, 0, 0, 0);
            mInstrumentation.sendPointerSync(event);
            event = MotionEvent.obtain(SystemClock.uptimeMillis(), SystemClock.uptimeMillis(), MotionEvent.ACTION_UP, 1,
                    new int[] { 0 }, coords, 0, 0, 0, 0, 0, 0, 0);
            mInstrumentation.sendPointerSync(event);
            TestUtils.sleepBy(500);
            assertTrue(view.getHeight() == height);
        }
        nm.cancel(id);
        unregisterProvisioningObserver();
        Log.v(TAG, "---------- end test004ExpandItem ----------");
    }

    public void test005ClickItem() {
        Log.v(TAG, "++++++++++ Start test005ClickItem ++++++++++");
        registerProvisioningObserver();
        int id = getId();
        Intent intent = new Intent();
        intent.setAction(Intent.ACTION_WEB_SEARCH);
        PendingIntent pendingIntent = PendingIntent.getActivity(mStatusBarViewActivity, 0, intent, 0);
        Notification.Builder builder = new Notification.Builder(mStatusBarViewActivity).setTicker("ticker").setContentTitle(
                "content title").setContentText("context text").setSmallIcon(mSmallIcon).addAction(mActionIcon, "action",
                pendingIntent);
        NotificationManager nm = (NotificationManager) mContext.getSystemService(Activity.NOTIFICATION_SERVICE);
        Notification.BigTextStyle expandedBuilder = new Notification.BigTextStyle(builder);
        expandedBuilder.bigText("Big text");
        nm.notify(id, expandedBuilder.build());

        TestUtils.sleepBy(1000);
        ViewGroup viewGroup = (ViewGroup) TestUtils.getSuperClassProperty(mPhoneStatusBar, "mPile");
        View view = viewGroup.getChildAt(0);
        if (view != null) {
            TouchUtils.clickView(this, view);
            TestUtils.sleepBy(500);
        }
        unregisterProvisioningObserver();
        Log.v(TAG, "---------- end test005ClickItem ----------");
    }

    public void test003LongPressItem() {
        Log.v(TAG, "++++++++++ Start test003LongPressItem ++++++++++");
        registerProvisioningObserver();
        Object notificationData = TestUtils.getSuperClassProperty(mPhoneStatusBar, "mNotificationData");
        Integer sizeBefore = (Integer) TestUtils.invokeMethod(notificationData, "size", null, null);
        
        int id = getId();
        Intent intent = new Intent();
        intent.setAction(Intent.ACTION_WEB_SEARCH);
        PendingIntent pendingIntent = PendingIntent.getActivity(mStatusBarViewActivity, 0, intent, 0);
        Notification.Builder builder = new Notification.Builder(mStatusBarViewActivity).setTicker("ticker").setContentTitle(
                "content title").setContentText("context text").setSmallIcon(mSmallIcon).addAction(mActionIcon, "action",
                pendingIntent);
        NotificationManager nm = (NotificationManager) mContext.getSystemService(Activity.NOTIFICATION_SERVICE);
        Notification.BigTextStyle expandedBuilder = new Notification.BigTextStyle(builder);
        expandedBuilder.bigText("Big text");
        nm.notify(id, expandedBuilder.build());

        TestUtils.sleepBy(1000);
        ViewGroup viewGroup = (ViewGroup) TestUtils.getSuperClassProperty(mPhoneStatusBar, "mPile");
        View view = viewGroup.getChildAt(0);
        if (view != null) {
            TouchUtils.longClickView(this, view);
            TestUtils.sleepBy(500);
        }

        mInstrumentation.sendKeyDownUpSync(KeyEvent.ACTION_UP);
        mInstrumentation.waitForIdleSync();
        TestUtils.sleepBy(500);
        
        mInstrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN);
        mInstrumentation.waitForIdleSync();
        TestUtils.sleepBy(500);
        
        mInstrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_ENTER);
        mInstrumentation.waitForIdleSync();
        TestUtils.sleepBy(500);
        
        mInstrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_HOME);
        mInstrumentation.waitForIdleSync();
        TestUtils.sleepBy(500);

        nm.cancel(id);
        TestUtils.sleepBy(1000);
        Integer size = (Integer) TestUtils.invokeMethod(notificationData, "size", null, null);
        assertEquals((int)sizeBefore, (int) size);
        unregisterProvisioningObserver();
        Log.v(TAG, "---------- end test003LongPressItem ----------");
    }

    public void test002Item() {
        Log.v(TAG, "++++++++++ Start test002Item ++++++++++");
        registerProvisioningObserver();
        int id = getId();
        Intent intent = new Intent();
        intent.setAction(Intent.ACTION_WEB_SEARCH);
        PendingIntent pendingIntent = PendingIntent.getActivity(mStatusBarViewActivity, 0, intent, 0);
        Notification.Builder builder = new Notification.Builder(mStatusBarViewActivity).setTicker("ticker").setContentTitle(
                "content title").setContentText("context text").setSmallIcon(mSmallIcon).addAction(mActionIcon, "action",
                pendingIntent);
        NotificationManager nm = (NotificationManager) mContext.getSystemService(Activity.NOTIFICATION_SERVICE);
        Notification.BigTextStyle expandedBuilder = new Notification.BigTextStyle(builder);
        expandedBuilder.bigText("Big text");
        nm.notify(id, expandedBuilder.build());

        TestUtils.sleepBy(1000);
        Object notificationData = TestUtils.getSuperClassProperty(mPhoneStatusBar, "mNotificationData");
        Integer size = (Integer) TestUtils.invokeMethod(notificationData, "size", null, null);

        unregisterProvisioningObserver();
        Log.v(TAG, "---------- end test002Item ----------");
    }

    public void test001Notification() {
        Log.v(TAG, "++++++++++ Start test001Notification ++++++++++");
        registerProvisioningObserver();
        int id = getId();
        Intent intent = new Intent();
        intent.setAction(Intent.ACTION_WEB_SEARCH);
        PendingIntent pendingIntent = PendingIntent.getActivity(mStatusBarViewActivity, 0, intent, 0);
        Notification.Builder builder = new Notification.Builder(mStatusBarViewActivity).setTicker("ticker").setContentTitle(
                "content title").setContentText("context text").setSmallIcon(mSmallIcon).addAction(mActionIcon, "action",
                pendingIntent);
        NotificationManager nm = (NotificationManager) mContext.getSystemService(Activity.NOTIFICATION_SERVICE);
        Notification.BigTextStyle expandedBuilder = new Notification.BigTextStyle(builder);
        expandedBuilder.bigText("Big text");
        nm.notify(id, expandedBuilder.build());
        TestUtils.sleepBy(1000);
        Object notificationData = TestUtils.getSuperClassProperty(mPhoneStatusBar, "mNotificationData");
        Integer size = (Integer) TestUtils.invokeMethod(notificationData, "size", null, null);
        mInstrumentation.waitForIdleSync();

        builder = new Notification.Builder(mInstrumentation.getContext()).setTicker("updateNotification").setContentTitle(
                "content title").setContentText("context text").setSmallIcon(mSmallIcon).addAction(mActionIcon, "action",
                pendingIntent);
        expandedBuilder = new Notification.BigTextStyle(builder);
        expandedBuilder.bigText("Big Big text");
        nm.notify(id, expandedBuilder.build());
        TestUtils.sleepBy(1000);
        notificationData = TestUtils.getSuperClassProperty(mPhoneStatusBar, "mNotificationData");
        size = (Integer) TestUtils.invokeMethod(notificationData, "size", null, null);
        mInstrumentation.waitForIdleSync();

        nm.cancel(id);
        TestUtils.sleepBy(1000);
        notificationData = TestUtils.getSuperClassProperty(mPhoneStatusBar, "mNotificationData");
        size = (Integer) TestUtils.invokeMethod(notificationData, "size", null, null);
        mInstrumentation.waitForIdleSync();

        unregisterProvisioningObserver();
        Log.v(TAG, "---------- end test001Notification ----------");
    }

    public void testAddTick() {
        initTestView();
        Log.v(TAG, "++++++++++ Start testAddTick ++++++++++");
        final Object tick = TestUtils.getProperty(mPhoneStatusBar, "mTicker");
        ArrayList segments = (ArrayList) TestUtils.getSuperClassProperty(tick, "mSegments");
        mInstrumentation.waitForIdleSync();
        assertEquals(0, segments.size());
        final IBinder key = new Binder();
        Intent intent = new Intent();
        intent.setAction(Intent.ACTION_WEB_SEARCH);
        PendingIntent pendingIntent = PendingIntent.getActivity(mInstrumentation.getContext(), 0, intent, 0);
        Notification.Builder builder = new Notification.Builder(mInstrumentation.getContext()).setTicker("addNotification")
                .setContentTitle("content title").setContentText("context text").setSmallIcon(mSmallIcon).addAction(
                        mActionIcon, "action", pendingIntent);
        Notification.BigTextStyle expandedBuilder = new Notification.BigTextStyle(builder);
        final StatusBarNotification notification = new StatusBarNotification("com.android.systemui.tests", 1000, null, 0, 0,
                0, expandedBuilder.build(), UserHandle.CURRENT);
        mStatusBarViewActivity.runOnUiThread(new Runnable() {
            public void run() {
                TestUtils.invokeSuperClassMethod(tick, "addEntry", new Class[] { StatusBarNotification.class },
                        new Object[] { notification });
            }
        });
        TestUtils.sleepBy(1000);
        segments = (ArrayList) TestUtils.getSuperClassProperty(tick, "mSegments");
        mInstrumentation.waitForIdleSync();
        assertTrue(segments.size() <= 1);
        Log.v(TAG, "---------- end testAddTick ----------");
    }

    public void testCarrierLabel() {
        initTestView();
        
        Log.v(TAG, "++++++++++ Start testCarrierLabel ++++++++++");
        TextView carrierLabel = (FeatureOption.MTK_GEMINI_SUPPORT)
            ? (TextView) TestUtils.getProperty(mPhoneStatusBar, "mCarrier1")
            : (TextView) TestUtils.getProperty(mPhoneStatusBar, "mCarrierLabel");
        TestUtils.invokeMethod(carrierLabel, "updateNetworkName", new Class[] { boolean.class, String.class, boolean.class,
                String.class }, new Object[] { true, "SPN", true, "PLMN" });
        ///assertEquals("b|a", carrierLabel.getText());

        ///TestUtils.invokeMethod(carrierLabel, "onAttachedToWindow", null, null);
        ///Boolean attached = (Boolean) TestUtils.getProperty(carrierLabel, "mAttached");
        ///assertTrue(attached);

        ///TestUtils.invokeMethod(carrierLabel, "onDetachedFromWindow", null, null);
        ///attached = (Boolean) TestUtils.getProperty(carrierLabel, "mAttached");
        ///assertFalse(attached);

        Object receiver = TestUtils.getProperty(mPhoneStatusBar, "mBroadcastReceiver");
        Intent intent = new Intent(TelephonyIntents.SPN_STRINGS_UPDATED_ACTION);
        intent.putExtra(TelephonyIntents.EXTRA_SHOW_SPN, true);
        intent.putExtra(TelephonyIntents.EXTRA_SPN, "SPN2");
        intent.putExtra(TelephonyIntents.EXTRA_SHOW_PLMN, true);
        intent.putExtra(TelephonyIntents.EXTRA_PLMN, "PLMN2");
        TestUtils.invokeMethod(receiver, "onReceive", new Class[] { Context.class, Intent.class }, new Object[] { mContext,
                intent });
        TestUtils.sleepBy(1000);
        Log.v(TAG, "carrierLabel.getText() =" + carrierLabel.getText());
        Log.v(TAG, "---------- end testCarrierLabel ----------");
    }

    public void testSearchPanel() {
        initTestView();
        Log.v(TAG, "++++++++++ Start testSearchPanel ++++++++++");
        final Object searchPanel = TestUtils.getSuperClassProperty(mPhoneStatusBar, "mSearchPanelView");
        if (searchPanel != null) {
            mStatusBarViewActivity.runOnUiThread(new Runnable() {
                public void run() {
                    TestUtils.invokeMethod(searchPanel, "show", new Class[] { boolean.class, boolean.class }, new Object[] {
                            true, true });
                }
            });
            TestUtils.sleepBy(1000);
            Boolean isShowing = (Boolean) TestUtils.invokeMethod(searchPanel, "isShowing", null, null);
            assertTrue(isShowing);
            mStatusBarViewActivity.runOnUiThread(new Runnable() {
                public void run() {
                    TestUtils.invokeMethod(searchPanel, "show", new Class[] { boolean.class, boolean.class }, new Object[] {
                            false, true });
                }
            });
            TestUtils.sleepBy(1000);
            isShowing = (Boolean) TestUtils.invokeMethod(searchPanel, "isShowing", null, null);
            assertFalse(isShowing);
        }
        Log.v(TAG, "---------- end testSearchPanel ----------");
    }

    public void testCloseDragHandle() {
        initTestView();
        Log.v(TAG, "++++++++++ Start testCloseDragHandle ++++++++++");
        Object commandQueue = TestUtils.getSuperClassProperty(mPhoneStatusBar, "mCommandQueue");
        TestUtils.invokeMethod(commandQueue, "expandNotificationsPanel", null, null);
        TestUtils.sleepBy(1000);
        TestUtils.invokeMethod(commandQueue, "expandSettingsPanel", null, null);
        TestUtils.sleepBy(1000);
        TestUtils.invokeMethod(commandQueue, "collapsePanels", null, null);
        TestUtils.sleepBy(1000);
        Log.v(TAG, "---------- end testCloseDragHandle ----------");
    }

    public void testShowSimIndicator() {
        initTestView();
        Log.v(TAG, "++++++++++ Start testShowSimIndicator ++++++++++");
        Object commandQueue = TestUtils.getSuperClassProperty(mPhoneStatusBar, "mCommandQueue");

        TestUtils.invokeMethod(commandQueue, "showSIMIndicator", new Class[] { String.class },
                new Object[] { "voice_call_sim_setting" });        
        TestUtils.sleepBy(1000);
        TestUtils.invokeMethod(commandQueue, "hideSIMIndicator", null, null);
        Log.v(TAG, "---------- end testShowSimIndicator ----------");
    }

    public void testNavigationBar() {
        initTestView();

        Log.v(TAG, "++++++++++ Start testNavigationBar ++++++++++");
        Object navigationBarView = TestUtils.getProperty(mPhoneStatusBar, "mNavigationBarView");
        if (navigationBarView != null) {
            int flags = StatusBarManager.DISABLE_NONE | StatusBarManager.DISABLE_RECENT;
            TestUtils.invokeMethod(navigationBarView, "setDisabledFlags", new Class[] { int.class }, new Object[] { flags });
            View recentsButton = (View) TestUtils.invokeMethod(navigationBarView, "getRecentsButton", null, null);
            assertEquals(View.INVISIBLE, recentsButton.getVisibility());

            flags = StatusBarManager.DISABLE_NONE | StatusBarManager.NAVIGATION_HINT_BACK_ALT;
            TestUtils.invokeMethod(navigationBarView, "setNavigationIconHints", new Class[] { int.class },
                    new Object[] { flags });
            assertEquals(0.5f, recentsButton.getAlpha());

            TestUtils.invokeMethod(navigationBarView, "setHidden", new Class[] { boolean.class }, new Object[] { true });
            Boolean hidden = (Boolean) TestUtils.getProperty(navigationBarView, "mHidden");
            assertTrue(hidden);

            TestUtils.invokeMethod(navigationBarView, "setMenuVisibility", new Class[] { boolean.class }, new Object[] { true });
            View menuButton = (View) TestUtils.invokeMethod(navigationBarView, "getMenuButton", null, null);
            assertEquals(View.VISIBLE, menuButton.getVisibility());

            TestUtils.invokeMethod(navigationBarView, "setLowProfile",
                    new Class[] { boolean.class, boolean.class, boolean.class }, new Object[] { true, true, false });
            Boolean lowProfile = (Boolean) TestUtils.getProperty(navigationBarView, "mLowProfile");
            assertTrue(lowProfile);

            TestUtils.invokeMethod(navigationBarView, "setLowProfile",
                    new Class[] { boolean.class, boolean.class, boolean.class }, new Object[] { false, false, false });
            lowProfile = (Boolean) TestUtils.getProperty(navigationBarView, "mLowProfile");
            assertFalse(lowProfile);

            TestUtils.setProperty(navigationBarView, "mVertical", true);
            TestUtils.invokeMethod(navigationBarView, "onSizeChanged",
                    new Class[] { int.class, int.class, int.class, int.class }, new Object[] { 800, 480, 480, 800 });
        }
        Log.v(TAG, "---------- end testNavigationBar ----------");
    }

    public void testShowApplicationGuide() {
        initTestView();

        final String SHOW_APP_GUIDE_SETTING = "settings";
        final String MMS = "MMS";
        final String PHONE = "PHONE";
        final String CONTACTS = "CONTACTS";
        final String MMS_SHOW_GUIDE = "mms_show_guide";
        final String PHONE_SHOW_GUIDE = "phone_show_guide";
        final String CONTACTS_SHOW_GUIDE = "contacts_show_guide";
        /// Reset the shared preferences.
        SharedPreferences settings = mContext.getSharedPreferences(SHOW_APP_GUIDE_SETTING, 0);
        SharedPreferences.Editor editor = settings.edit();
        editor.putString(MMS_SHOW_GUIDE, "1");
        editor.commit();
        editor.putString(PHONE_SHOW_GUIDE, "1");
        editor.commit();
        editor.putString(CONTACTS_SHOW_GUIDE, "1");
        editor.commit();
        ///
        TestUtils.setProperty(mPhoneStatusBar, "mDisabled", StatusBarManager.DISABLE_EXPAND);

        Log.v(TAG, "++++++++++ Start testShowApplicationGuide ++++++++++");
        Object commandQueue = TestUtils.getSuperClassProperty(mPhoneStatusBar, "mCommandQueue");
        TestUtils.invokeMethod(commandQueue, "showApplicationGuide", new Class[] { String.class },
                new Object[] { MMS });
        mInstrumentation.waitForIdleSync();
        TestUtils.sleepBy(1000);
        Dialog dialog = (Dialog) TestUtils.getProperty(mPhoneStatusBar, "mAppGuideDialog");
        if (dialog != null && dialog.isShowing()) {
            mInstrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);
            mInstrumentation.waitForIdleSync();
            TestUtils.sleepBy(1000);
            TestUtils.invokeMethod(commandQueue, "showApplicationGuide", new Class[] { String.class },
                new Object[] { MMS });

            TestUtils.sleepBy(1000);
            assertTrue(dialog.isShowing());

            mInstrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_HOME);
            mInstrumentation.waitForIdleSync();
            TestUtils.sleepBy(1000);

            TestUtils.invokeMethod(commandQueue, "showApplicationGuide", new Class[] { String.class },
                new Object[] { MMS });
            TestUtils.sleepBy(1000);
            assertTrue(dialog.isShowing());

            View view = (View) TestUtils.getProperty(mPhoneStatusBar, "mAppGuideButton");
            TouchUtils.clickView(this, view);
            assertFalse(dialog.isShowing());

            TestUtils.invokeMethod(commandQueue, "showApplicationGuide", new Class[] { String.class },
                new Object[] { MMS });
            TestUtils.sleepBy(1000);
            assertFalse(dialog.isShowing());

            TestUtils.invokeMethod(commandQueue, "showApplicationGuide", new Class[] { String.class },
                new Object[] { PHONE });
            TestUtils.sleepBy(1000);
            assertTrue(dialog.isShowing());

            TouchUtils.clickView(this, view);
            assertFalse(dialog.isShowing());

            TestUtils.invokeMethod(commandQueue, "showApplicationGuide", new Class[] { String.class },
                new Object[] { PHONE });
            TestUtils.sleepBy(1000);
            assertFalse(dialog.isShowing());

            TestUtils.invokeMethod(commandQueue, "showApplicationGuide", new Class[] { String.class },
                new Object[] { CONTACTS });
            TestUtils.sleepBy(1000);
            assertTrue(dialog.isShowing());

            TouchUtils.clickView(this, view);
            assertFalse(dialog.isShowing());

            TestUtils.invokeMethod(commandQueue, "showApplicationGuide", new Class[] { String.class },
                new Object[] { CONTACTS });
            TestUtils.sleepBy(1000);
            assertFalse(dialog.isShowing());

            Log.v(TAG, "---------- end testShowApplicationGuide ----------");
        }
        TestUtils.setProperty(mPhoneStatusBar, "mDisabled", 0);
    }

    private void registerProvisioningObserver() {
        initTestView();
        ContentObserver provisioningObserver = (ContentObserver) TestUtils.getSuperClassProperty(mPhoneStatusBar,
                "mProvisioningObserver");
        TestUtils.invokeMethod(provisioningObserver, "onChange", new Class[] { boolean.class }, new Object[] { false });
        mContext.getContentResolver().registerContentObserver(Settings.Secure.getUriFor(Settings.Secure.DEVICE_PROVISIONED),
                true, provisioningObserver);
    }

    private void unregisterProvisioningObserver() {
        ContentObserver provisioningObserver = (ContentObserver) TestUtils.getSuperClassProperty(mPhoneStatusBar,
                "mProvisioningObserver");
        mContext.getContentResolver().unregisterContentObserver(provisioningObserver);
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
        if (mStatusBarViewActivity != null) {
            mStatusBarViewActivity.finish();
        }
    }

}
