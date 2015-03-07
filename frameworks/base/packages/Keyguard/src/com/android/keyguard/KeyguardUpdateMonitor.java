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

package com.android.keyguard;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.ActivityManager.RunningAppProcessInfo;
import android.app.ActivityManagerNative;
import android.app.AlertDialog;
import android.app.IUserSwitchObserver;
import android.app.PendingIntent;
import android.app.admin.DevicePolicyManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.ComponentName;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.database.ContentObserver;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.net.sip.SipManager;
import android.os.BatteryManager;
import static android.os.BatteryManager.BATTERY_STATUS_FULL;
import static android.os.BatteryManager.BATTERY_STATUS_UNKNOWN;
import static android.os.BatteryManager.BATTERY_HEALTH_UNKNOWN;
import static android.os.BatteryManager.EXTRA_STATUS;
import static android.os.BatteryManager.EXTRA_PLUGGED;
import static android.os.BatteryManager.EXTRA_LEVEL;
import static android.os.BatteryManager.EXTRA_HEALTH;
import android.media.AudioManager;
import android.media.IRemoteControlDisplay;
import android.os.BatteryManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.IRemoteCallback;
import android.os.Message;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.provider.Settings;
import android.provider.Settings.System;

import android.telephony.PhoneStateListener;
import android.telephony.ServiceState;
import android.telephony.TelephonyManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;

import com.android.internal.telephony.ITelephony;
import com.android.internal.telephony.ITelephonyRegistry;
import com.android.internal.telephony.IccCardConstants;
import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.TelephonyIntents;

import static com.android.internal.telephony.TelephonyIntents.EXTRA_CALIBRATION_DATA;

import android.util.Log;
import com.google.android.collect.Lists;

import com.mediatek.common.dm.DmAgent;
import com.mediatek.CellConnService.CellConnMgr;
import com.mediatek.common.telephony.ITelephonyEx;

import com.mediatek.telephony.SimInfoManager;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

/**
 * Watches for updates that may be interesting to the keyguard, and provides
 * the up to date information as well as a registration for callbacks that care
 * to be updated.
 *
 * Note: under time crunch, this has been extended to include some stuff that
 * doesn't really belong here.  see {@link #handleBatteryUpdate} where it shutdowns
 * the device, and {@link #getFailedUnlockAttempts()}, {@link #reportFailedAttempt()}
 * and {@link #clearFailedUnlockAttempts()}.  Maybe we should rename this 'KeyguardContext'...
 */
public class KeyguardUpdateMonitor {

    private static final String TAG = "KeyguardUpdateMonitor";
    private static final boolean DEBUG = true;
    private static final boolean DEBUG_SIM_STATES = DEBUG || false;
    private static final int FAILED_BIOMETRIC_UNLOCK_ATTEMPTS_BEFORE_BACKUP = 3;
    /// M: support multiple battery number
    private static final int KEYGUARD_BATTERY_NUMBER = 2;
    /// M: Change the threshold to 16 for mediatek device
    private static final int LOW_BATTERY_THRESHOLD = 16;

    // Callback messages
    private static final int MSG_TIME_UPDATE = 301;
    private static final int MSG_BATTERY_UPDATE = 302;
    private static final int MSG_CARRIER_INFO_UPDATE = 303;
    private static final int MSG_SIM_STATE_CHANGE = 304;
    private static final int MSG_RINGER_MODE_CHANGED = 305;
    private static final int MSG_PHONE_STATE_CHANGED = 306;
    private static final int MSG_CLOCK_VISIBILITY_CHANGED = 307;
    private static final int MSG_DEVICE_PROVISIONED = 308;
    private static final int MSG_DPM_STATE_CHANGED = 309;
    private static final int MSG_USER_SWITCHING = 310;
    private static final int MSG_USER_REMOVED = 311;
    private static final int MSG_KEYGUARD_VISIBILITY_CHANGED = 312;
    protected static final int MSG_BOOT_COMPLETED = 313;
    private static final int MSG_USER_SWITCH_COMPLETE = 314;
    private static final int MSG_SET_CURRENT_CLIENT_ID = 315;
    protected static final int MSG_SET_PLAYBACK_STATE = 316;
    protected static final int MSG_USER_INFO_CHANGED = 317;
    protected static final int MSG_REPORT_EMERGENCY_CALL_ACTION = 318;
    private static final int MSG_SCREEN_TURNED_ON = 319;
    private static final int MSG_SCREEN_TURNED_OFF = 320;

    private static KeyguardUpdateMonitor sInstance;

    private final Context mContext;

    // Telephony state
    /// M: Set default sim state to UNKNOWN inseatd of READY
    private IccCardConstants.State mSimState[]; /// M: Support GeminiPlus
    private CharSequence mTelephonyPlmn[]; /// M: Support GeminiPlus
    private CharSequence mTelephonySpn[]; /// M: Support GeminiPlus
    private CharSequence mTelephonyHnbName[]; /// M: Support GeminiPlus
    private CharSequence mTelephonyCsgId[]; /// M: Support GeminiPlus
    private int mRingMode;
    private int mPhoneState;
    private boolean mKeyguardIsVisible;
    private boolean mBootCompleted;

    // Device provisioning state
    private boolean mDeviceProvisioned;

    // M: support multiple batteries
    private BatteryStatus mBatteryStatus[];
    private boolean mDocktoDesk = false;

    // Password attempts
    private int mFailedAttempts = 0;
    private int mFailedBiometricUnlockAttempts = 0;

    private boolean mAlternateUnlockEnabled;

    private boolean mClockVisible;

    private final ArrayList<WeakReference<KeyguardUpdateMonitorCallback>>
            mCallbacks = Lists.newArrayList();
    private ContentObserver mDeviceProvisionedObserver;

    private boolean mSwitchingUser;

    private boolean mScreenOn;

    private final Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_TIME_UPDATE:
                    handleTimeUpdate();
                    break;
                case MSG_BATTERY_UPDATE:
                    handleBatteryUpdate((BatteryStatus) msg.obj);
                    break;
                case MSG_CARRIER_INFO_UPDATE:
                    handleCarrierInfoUpdate(msg.arg1);
                    break;
                case MSG_SIM_STATE_CHANGE:
                    handleSimStateChange((SimArgs) msg.obj);
                    break;
                case MSG_RINGER_MODE_CHANGED:
                    handleRingerModeChange(msg.arg1);
                    break;
                case MSG_PHONE_STATE_CHANGED:
                    handlePhoneStateChanged((String)msg.obj);
                    break;
                case MSG_CLOCK_VISIBILITY_CHANGED:
                    handleClockVisibilityChanged();
                    break;
                case MSG_DEVICE_PROVISIONED:
                    handleDeviceProvisioned();
                    break;
                case MSG_DPM_STATE_CHANGED:
                    handleDevicePolicyManagerStateChanged();
                    break;
                case MSG_USER_SWITCHING:
                    handleUserSwitching(msg.arg1, (IRemoteCallback)msg.obj);
                    break;
                case MSG_USER_SWITCH_COMPLETE:
                    handleUserSwitchComplete(msg.arg1);
                    break;
                case MSG_USER_REMOVED:
                    handleUserRemoved(msg.arg1);
                    break;
                case MSG_KEYGUARD_VISIBILITY_CHANGED:
                    handleKeyguardVisibilityChanged(msg.arg1);
                    break;
                case MSG_BOOT_COMPLETED:
                    handleBootCompleted();
                    break;
                case MSG_SET_CURRENT_CLIENT_ID:
                    handleSetGenerationId(msg.arg1, msg.arg2 != 0, (PendingIntent) msg.obj);
                    break;
                case MSG_SET_PLAYBACK_STATE:
                    handleSetPlaybackState(msg.arg1, msg.arg2, (Long) msg.obj);
                    break;
                case MSG_USER_INFO_CHANGED:
                    handleUserInfoChanged(msg.arg1);
                    break;
                case MSG_REPORT_EMERGENCY_CALL_ACTION:
                    handleReportEmergencyCallAction();
                    break;
                case MSG_SCREEN_TURNED_OFF:
                    handleScreenTurnedOff(msg.arg1);
                    break;
                case MSG_SCREEN_TURNED_ON:
                    handleScreenTurnedOn();
                    break;
                ///M: support SMB dock status change
                case MSG_DOCK_STATUS_UPDATE:
                    KeyguardUtils.xlogD(TAG, "MSG_DOCK_STATUS_UPDATE, msg.arg1=" + msg.arg1);
                    handleDockStatusUpdate(msg.arg1);
                    break;
            }
        }
    };

    private AudioManager mAudioManager;

    static class DisplayClientState {
        public int clientGeneration;
        public boolean clearing;
        public PendingIntent intent;
        public int playbackState;
        public long playbackEventTime;
    }

    private DisplayClientState mDisplayClientState = new DisplayClientState();

    /**
     * This currently implements the bare minimum required to enable showing and hiding
     * KeyguardTransportControl.  There's a lot of client state to maintain which is why
     * KeyguardTransportControl maintains an independent connection while it's showing.
     */
    private final IRemoteControlDisplay.Stub mRemoteControlDisplay =
                new IRemoteControlDisplay.Stub() {

        public void setPlaybackState(int generationId, int state, long stateChangeTimeMs,
                long currentPosMs, float speed) {
            Message msg = mHandler.obtainMessage(MSG_SET_PLAYBACK_STATE,
                    generationId, state, stateChangeTimeMs);
            mHandler.sendMessage(msg);
        }

        public void setMetadata(int generationId, Bundle metadata) {

        }

        public void setTransportControlInfo(int generationId, int flags, int posCapabilities) {

        }

        public void setArtwork(int generationId, Bitmap bitmap) {

        }

        public void setAllMetadata(int generationId, Bundle metadata, Bitmap bitmap) {

        }

        public void setEnabled(boolean enabled) {
            // no-op: this RemoteControlDisplay is not subject to being disabled.
        }

        public void setCurrentClientId(int clientGeneration, PendingIntent mediaIntent,
                boolean clearing) throws RemoteException {
            Message msg = mHandler.obtainMessage(MSG_SET_CURRENT_CLIENT_ID,
                        clientGeneration, (clearing ? 1 : 0), mediaIntent);
            mHandler.sendMessage(msg);
        }
    };

    private final BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {

        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (DEBUG) Log.d(TAG, "received broadcast " + action);

            if (Intent.ACTION_TIME_TICK.equals(action)
                    || Intent.ACTION_TIME_CHANGED.equals(action)
                    || Intent.ACTION_TIMEZONE_CHANGED.equals(action)) {
                mHandler.sendEmptyMessage(MSG_TIME_UPDATE);
            } else if (TelephonyIntents.SPN_STRINGS_UPDATED_ACTION.equals(action)) {
                /// M: suppor Gemini, LTE CSG
                int mSimId = PhoneConstants.GEMINI_SIM_1;
                if (KeyguardUtils.isGemini()) {
                    mSimId = intent.getIntExtra(PhoneConstants.GEMINI_SIM_ID_KEY, PhoneConstants.GEMINI_SIM_1);
                }
                mTelephonyPlmn[mSimId] = getTelephonyPlmnFrom(intent);
                mTelephonySpn[mSimId] = getTelephonySpnFrom(intent);
                mTelephonyHnbName[mSimId] = getTelephonyHnbNameFrom(intent);
                mTelephonyCsgId[mSimId] = getTelephonyCsgIdFrom(intent);
                KeyguardUtils.xlogD(TAG, "SPN_STRINGS_UPDATED_ACTION, update simId = " + mSimId +" , plmn=" + mTelephonyPlmn[mSimId]
                        + ", spn=" + mTelephonySpn[mSimId]+ ", hnb=" + mTelephonyHnbName[mSimId]+ ", csg=" + mTelephonyCsgId[mSimId]);

                mHandler.sendMessage(mHandler.obtainMessage(MSG_CARRIER_INFO_UPDATE, mSimId, 0));
            } else if (Intent.ACTION_BATTERY_CHANGED.equals(action)) {
                int status = intent.getIntExtra(EXTRA_STATUS, BATTERY_STATUS_UNKNOWN);
                int plugged = intent.getIntExtra(EXTRA_PLUGGED, 0);
                int level = intent.getIntExtra(EXTRA_LEVEL, 0);
                int health = intent.getIntExtra(EXTRA_HEALTH, BATTERY_HEALTH_UNKNOWN);
                Message msg = mHandler.obtainMessage(
                        MSG_BATTERY_UPDATE, new BatteryStatus(0, status, level, plugged, health));
                mHandler.sendMessage(msg);

                boolean b2ndBattPresent = intent.getBooleanExtra(BatteryManager.EXTRA_PRESENT_2ND, false);

                if (mDocktoDesk && b2ndBattPresent) {
                    status = intent.getIntExtra(BatteryManager.EXTRA_STATUS_2ND, BATTERY_STATUS_UNKNOWN);
                    plugged = BatteryManager.BATTERY_PLUGGED_AC;
                    level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL_2ND, 0);
                    health = BATTERY_HEALTH_UNKNOWN;
                    KeyguardUtils.xlogD(TAG, "batt2 is present status="+status+" level="+level);
                    msg = mHandler.obtainMessage(
                            MSG_BATTERY_UPDATE, new BatteryStatus(1, status, level, plugged, health));
                    mHandler.sendMessage(msg);
                }
            } else if (TelephonyIntents.ACTION_SIM_STATE_CHANGED.equals(action)) {
                String stateExtra = intent.getStringExtra(IccCardConstants.INTENT_KEY_ICC_STATE);
                SimArgs simArgs = SimArgs.fromIntent(intent);
                if (DEBUG_SIM_STATES) {
                    Log.v(TAG, "action=" + action + " state=" + stateExtra);
                }
                KeyguardUtils.xlogD(TAG, "ACTION_SIM_STATE_CHANGED, stateExtra="+stateExtra +",simId="+simArgs.simId );
                if (IccCardConstants.State.NETWORK_LOCKED == simArgs.simState) {
                    //to create new thread to query SIM ME lock status
                    // after finish query, send MSG_SIM_STATE_CHANGE message
                    new simMeStatusQueryThread(simArgs).start();
                } else {
                    mHandler.sendMessage(mHandler.obtainMessage(MSG_SIM_STATE_CHANGE, simArgs));
                }
            } else if (AudioManager.RINGER_MODE_CHANGED_ACTION.equals(action)) {
                 if (DEBUG) KeyguardUtils.xlogD(TAG, "RINGER_MODE_CHANGED_ACTION received");
                mHandler.sendMessage(mHandler.obtainMessage(MSG_RINGER_MODE_CHANGED,
                        intent.getIntExtra(AudioManager.EXTRA_RINGER_MODE, -1), 0));
            } else if (TelephonyManager.ACTION_PHONE_STATE_CHANGED.equals(action)) {
                String state = intent.getStringExtra(TelephonyManager.EXTRA_STATE);
                mHandler.sendMessage(mHandler.obtainMessage(MSG_PHONE_STATE_CHANGED, state));
            } else if (DevicePolicyManager.ACTION_DEVICE_POLICY_MANAGER_STATE_CHANGED
                    .equals(action)) {
                mHandler.sendEmptyMessage(MSG_DPM_STATE_CHANGED);
            } else if (Intent.ACTION_USER_REMOVED.equals(action)) {
                mHandler.sendMessage(mHandler.obtainMessage(MSG_USER_REMOVED,
                       intent.getIntExtra(Intent.EXTRA_USER_HANDLE, 0), 0));
            } else if (Intent.ACTION_BOOT_COMPLETED.equals(action)) {
                dispatchBootCompleted();

            /// M: Mediatek added intent filter begin @{
            /// M: handle PhoneStateMgr triggered unlock intents
            } else if (CellConnMgr.ACTION_UNLOCK_SIM_LOCK.equals(action)) {
                processUnlockSimIntent(intent);
            /// M: Incoming Indicator for Rotation @{
            }else if(CLEAR_NEW_EVENT_VIEW_INTENT.equals(action)) {
                setQueryBaseTime();
            }
            /// M: Docking to SmartBook state changed @{
            else if(ACTION_SMARTBOOK_PLUG.equals(action)){
                mDocktoDesk = intent.getBooleanExtra(EXTRA_SMARTBOOK_PLUG_STATE, false);
                int plugState = mDocktoDesk ? 1 : 0;
                KeyguardUtils.xlogD(TAG, "plugState = "+plugState);
                mHandler.sendMessage(mHandler.obtainMessage(MSG_DOCK_STATUS_UPDATE, plugState, 0));
             }
            /// @}
        }
    };

    private final BroadcastReceiver mBroadcastAllReceiver = new BroadcastReceiver() {

        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (Intent.ACTION_USER_INFO_CHANGED.equals(action)) {
                mHandler.sendMessage(mHandler.obtainMessage(MSG_USER_INFO_CHANGED,
                        intent.getIntExtra(Intent.EXTRA_USER_HANDLE, getSendingUserId()), 0));
            }
        }
    };

    /**
     * When we receive a
     * {@link com.android.internal.telephony.TelephonyIntents#ACTION_SIM_STATE_CHANGED} broadcast,
     * and then pass a result via our handler to {@link KeyguardUpdateMonitor#handleSimStateChange},
     * we need a single object to pass to the handler.  This class helps decode
     * the intent and provide a {@link SimCard.State} result.
     * M: Add gemini support
     */
    public static class SimArgs {
        public final IccCardConstants.State simState;
        int simId = PhoneConstants.GEMINI_SIM_1;
        int simMECategory = 0;

        SimArgs(IccCardConstants.State state) {
            simState = state;
        }

        SimArgs(IccCardConstants.State state, int id, int meCategory) {
           simState = state;
           simId = id;
           simMECategory = meCategory;
        }

        static SimArgs fromIntent(Intent intent) {
            IccCardConstants.State state;
            int id = PhoneConstants.GEMINI_SIM_1;
            int meCategory = 0;
            if (!TelephonyIntents.ACTION_SIM_STATE_CHANGED.equals(intent.getAction())) {
                throw new IllegalArgumentException("only handles intent ACTION_SIM_STATE_CHANGED");
            }
            String stateExtra = intent.getStringExtra(IccCardConstants.INTENT_KEY_ICC_STATE);
            if (KeyguardUtils.isGemini()) {
                id = intent.getIntExtra(PhoneConstants.GEMINI_SIM_ID_KEY, PhoneConstants.GEMINI_SIM_1);
            }
            if (IccCardConstants.INTENT_VALUE_ICC_ABSENT.equals(stateExtra)) {
                final String absentReason = intent
                    .getStringExtra(IccCardConstants.INTENT_KEY_LOCKED_REASON);

                if (IccCardConstants.INTENT_VALUE_ABSENT_ON_PERM_DISABLED.equals(
                        absentReason)) {
                    state = IccCardConstants.State.PERM_DISABLED;
                } else {
                    state = IccCardConstants.State.ABSENT;
                }
            } else if (IccCardConstants.INTENT_VALUE_ICC_READY.equals(stateExtra)) {
                state = IccCardConstants.State.READY;
            } else if (IccCardConstants.INTENT_VALUE_ICC_LOCKED.equals(stateExtra)) {
                final String lockedReason = intent
                        .getStringExtra(IccCardConstants.INTENT_KEY_LOCKED_REASON);
                KeyguardUtils.xlogD(TAG, "INTENT_VALUE_ICC_LOCKED, lockedReason="+lockedReason);
                if (IccCardConstants.INTENT_VALUE_LOCKED_ON_PIN.equals(lockedReason)) {
                    state = IccCardConstants.State.PIN_REQUIRED;
                } else if (IccCardConstants.INTENT_VALUE_LOCKED_ON_PUK.equals(lockedReason)) {
                    state = IccCardConstants.State.PUK_REQUIRED;
                } else if (IccCardConstants.INTENT_VALUE_LOCKED_NETWORK.equals(lockedReason)) {
                    meCategory = 0;
                    state = IccCardConstants.State.NETWORK_LOCKED;
                } else if (IccCardConstants.INTENT_VALUE_LOCKED_NETWORK_SUBSET.equals(lockedReason)) {
                    meCategory = 1;
                    state = IccCardConstants.State.NETWORK_LOCKED;
                } else if (IccCardConstants.INTENT_VALUE_LOCKED_SERVICE_PROVIDER.equals(lockedReason)) {
                    meCategory = 2;
                    state = IccCardConstants.State.NETWORK_LOCKED;
                } else if (IccCardConstants.INTENT_VALUE_LOCKED_CORPORATE.equals(lockedReason)) {
                    meCategory = 3;
                    state = IccCardConstants.State.NETWORK_LOCKED;
                } else if (IccCardConstants.INTENT_VALUE_LOCKED_SIM.equals(lockedReason)) {
                    meCategory = 4;
                    state = IccCardConstants.State.NETWORK_LOCKED;
                } else {
                    state = IccCardConstants.State.UNKNOWN;
                }
            } else if (IccCardConstants.INTENT_VALUE_ICC_LOADED.equals(stateExtra)
                        || IccCardConstants.INTENT_VALUE_ICC_IMSI.equals(stateExtra)) {
                // This is required because telephony doesn't return to "READY" after
                // these state transitions. See bug 7197471.
                state = IccCardConstants.State.READY;
            } else if (IccCardConstants.INTENT_VALUE_ICC_NOT_READY.equals(stateExtra)) {
                state = IccCardConstants.State.NOT_READY;
            } else {
                state = IccCardConstants.State.UNKNOWN;
            }
            return new SimArgs(state, id, meCategory);
        }

        public String toString() {
            return simState.toString();
        }
    }

    /* package */ static class BatteryStatus {
        public final int index;
        public final int status;
        public final int level;
        public final int plugged;
        public final int health;
        public BatteryStatus(int index, int status, int level, int plugged, int health) {
            this.index = index;
            this.status = status;
            this.level = level;
            this.plugged = plugged;
            this.health = health;
        }

        /**
         * Determine whether the device is plugged in (USB, power, or wireless).
         * @return true if the device is plugged in.
         */
        boolean isPluggedIn() {
            return plugged == BatteryManager.BATTERY_PLUGGED_AC
                    || plugged == BatteryManager.BATTERY_PLUGGED_USB
                    || plugged == BatteryManager.BATTERY_PLUGGED_WIRELESS;
        }

        /**
         * Whether or not the device is charged. Note that some devices never return 100% for
         * battery level, so this allows either battery level or status to determine if the
         * battery is charged.
         * @return true if the device is charged
         */
        public boolean isCharged() {
            return status == BATTERY_STATUS_FULL || level >= 100;
        }

        /**
         * Whether battery is low and needs to be charged.
         * @return true if battery is low
         */
        public boolean isBatteryLow() {
            return level < LOW_BATTERY_THRESHOLD;
        }

    }

    public static KeyguardUpdateMonitor getInstance(Context context) {
        if (sInstance == null) {
            sInstance = new KeyguardUpdateMonitor(context);
        }
        return sInstance;
    }

    protected void handleScreenTurnedOn() {
        final int count = mCallbacks.size();
        for (int i = 0; i < count; i++) {
            KeyguardUpdateMonitorCallback cb = mCallbacks.get(i).get();
            if (cb != null) {
                cb.onScreenTurnedOn();
            }
        }
    }

    protected void handleScreenTurnedOff(int arg1) {
        final int count = mCallbacks.size();
        for (int i = 0; i < count; i++) {
            KeyguardUpdateMonitorCallback cb = mCallbacks.get(i).get();
            if (cb != null) {
                cb.onScreenTurnedOff(arg1);
            }
        }
    }

    /**
     * IMPORTANT: Must be called from UI thread.
     */
    public void dispatchSetBackground(Bitmap bmp) {
        if (DEBUG) Log.d(TAG, "dispatchSetBackground");
        final int count = mCallbacks.size();
        for (int i = 0; i < count; i++) {
            KeyguardUpdateMonitorCallback cb = mCallbacks.get(i).get();
            if (cb != null) {
                cb.onSetBackground(bmp);
            }
        }
    }

    protected void handleSetGenerationId(int clientGeneration, boolean clearing, PendingIntent p) {
        mDisplayClientState.clientGeneration = clientGeneration;
        mDisplayClientState.clearing = clearing;
        mDisplayClientState.intent = p;
        if (DEBUG)
            Log.v(TAG, "handleSetGenerationId(g=" + clientGeneration + ", clear=" + clearing + ")");
        for (int i = 0; i < mCallbacks.size(); i++) {
            KeyguardUpdateMonitorCallback cb = mCallbacks.get(i).get();
            if (cb != null) {
                cb.onMusicClientIdChanged(clientGeneration, clearing, p);
            }
        }
    }

    protected void handleSetPlaybackState(int generationId, int playbackState, long eventTime) {
        if (DEBUG)
            Log.v(TAG, "handleSetPlaybackState(gen=" + generationId
                + ", state=" + playbackState + ", t=" + eventTime + ")");
        mDisplayClientState.playbackState = playbackState;
        mDisplayClientState.playbackEventTime = eventTime;
        if (generationId == mDisplayClientState.clientGeneration) {
            for (int i = 0; i < mCallbacks.size(); i++) {
                KeyguardUpdateMonitorCallback cb = mCallbacks.get(i).get();
                if (cb != null) {
                    cb.onMusicPlaybackStateChanged(playbackState, eventTime);
                }
            }
        } else {
            Log.w(TAG, "Ignoring generation id " + generationId + " because it's not current");
        }
    }

    private void handleUserInfoChanged(int userId) {
        for (int i = 0; i < mCallbacks.size(); i++) {
            KeyguardUpdateMonitorCallback cb = mCallbacks.get(i).get();
            if (cb != null) {
                cb.onUserInfoChanged(userId);
            }
        }
    }

    private KeyguardUpdateMonitor(Context context) {
        mContext = context;

        initMembers();

        mDeviceProvisioned = isDeviceProvisionedInSettingsDb();
        
        KeyguardUtils.xlogD(TAG, "mDeviceProvisioned is:" + mDeviceProvisioned);

        // Since device can't be un-provisioned, we only need to register a content observer
        // to update mDeviceProvisioned when we are...
        if (!mDeviceProvisioned) {
            watchForDeviceProvisioning();
        }

        /// M: support multiple batteries
        mBatteryStatus = new BatteryStatus[KEYGUARD_BATTERY_NUMBER];
        for (int i = 0; i < KEYGUARD_BATTERY_NUMBER; i++) {
            mBatteryStatus[i] = new BatteryStatus(i, BATTERY_STATUS_UNKNOWN, 100, 0, 0);
        }
        // Take a guess at initial SIM state, battery status and PLMN until we get an update
        /// M: We think the sim card's default state is unknown, so mark this line @{
        //mSimState = IccCardConstants.State.NOT_READY;
        /// @}
        /// M: Support GeminiPlus
        for (int i = PhoneConstants.GEMINI_SIM_1; i <= KeyguardUtils.getMaxSimId(); i++) {
            mTelephonyPlmn[i] = getDefaultPlmn();
        }

        // Watch for interesting updates
        final IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_TIME_TICK);
        filter.addAction(Intent.ACTION_TIME_CHANGED);
        filter.addAction(Intent.ACTION_BATTERY_CHANGED);
        filter.addAction(Intent.ACTION_TIMEZONE_CHANGED);
        filter.addAction(TelephonyIntents.ACTION_SIM_STATE_CHANGED);
        filter.addAction(TelephonyManager.ACTION_PHONE_STATE_CHANGED);
        filter.addAction(TelephonyIntents.SPN_STRINGS_UPDATED_ACTION);
        filter.addAction(AudioManager.RINGER_MODE_CHANGED_ACTION);
        filter.addAction(DevicePolicyManager.ACTION_DEVICE_POLICY_MANAGER_STATE_CHANGED);
        filter.addAction(Intent.ACTION_USER_REMOVED);

        /// M: app triggered unlock SIM lock
        filter.addAction(CellConnMgr.ACTION_UNLOCK_SIM_LOCK);

        /// M: Incoming Indicator for Rotation
        filter.addAction(CLEAR_NEW_EVENT_VIEW_INTENT);

        /// M: SMB dock state change
        filter.addAction(ACTION_SMARTBOOK_PLUG);

        context.registerReceiver(mBroadcastReceiver, filter);

        final IntentFilter bootCompleteFilter = new IntentFilter();
        bootCompleteFilter.setPriority(IntentFilter.SYSTEM_HIGH_PRIORITY);
        bootCompleteFilter.addAction(Intent.ACTION_BOOT_COMPLETED);
        context.registerReceiver(mBroadcastReceiver, bootCompleteFilter);

        final IntentFilter userInfoFilter = new IntentFilter(Intent.ACTION_USER_INFO_CHANGED);
        context.registerReceiverAsUser(mBroadcastAllReceiver, UserHandle.ALL, userInfoFilter,
                null, null);

        try {
            ActivityManagerNative.getDefault().registerUserSwitchObserver(
                    new IUserSwitchObserver.Stub() {
                        @Override
                        public void onUserSwitching(int newUserId, IRemoteCallback reply) {
                            mHandler.sendMessage(mHandler.obtainMessage(MSG_USER_SWITCHING,
                                    newUserId, 0, reply));
                            mSwitchingUser = true;
                        }
                        @Override
                        public void onUserSwitchComplete(int newUserId) throws RemoteException {
                            mHandler.sendMessage(mHandler.obtainMessage(MSG_USER_SWITCH_COMPLETE,
                                    newUserId));
                            mSwitchingUser = false;
                        }
                    });
        } catch (RemoteException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    private boolean isDeviceProvisionedInSettingsDb() {
        return Settings.Global.getInt(mContext.getContentResolver(),
                Settings.Global.DEVICE_PROVISIONED, 0) != 0;
    }

    private void watchForDeviceProvisioning() {
        mDeviceProvisionedObserver = new ContentObserver(mHandler) {
            @Override
            public void onChange(boolean selfChange) {
                super.onChange(selfChange);
                mDeviceProvisioned = isDeviceProvisionedInSettingsDb();
                if (mDeviceProvisioned) {
                    mHandler.sendEmptyMessage(MSG_DEVICE_PROVISIONED);
                }
                if (DEBUG) Log.d(TAG, "DEVICE_PROVISIONED state = " + mDeviceProvisioned);
            }
        };

        mContext.getContentResolver().registerContentObserver(
                Settings.Global.getUriFor(Settings.Global.DEVICE_PROVISIONED),
                false, mDeviceProvisionedObserver);

        // prevent a race condition between where we check the flag and where we register the
        // observer by grabbing the value once again...
        boolean provisioned = isDeviceProvisionedInSettingsDb();
        if (provisioned != mDeviceProvisioned) {
            mDeviceProvisioned = provisioned;
            if (mDeviceProvisioned) {
                mHandler.sendEmptyMessage(MSG_DEVICE_PROVISIONED);
            }
        }
    }

    /**
     * Handle {@link #MSG_DPM_STATE_CHANGED}
     */
    protected void handleDevicePolicyManagerStateChanged() {
        for (int i = mCallbacks.size() - 1; i >= 0; i--) {
            KeyguardUpdateMonitorCallback cb = mCallbacks.get(i).get();
            if (cb != null) {
                cb.onDevicePolicyManagerStateChanged();
            }
        }
    }

    /**
     * Handle {@link #MSG_USER_SWITCHING}
     */
    protected void handleUserSwitching(int userId, IRemoteCallback reply) {
        for (int i = 0; i < mCallbacks.size(); i++) {
            KeyguardUpdateMonitorCallback cb = mCallbacks.get(i).get();
            if (cb != null) {
                cb.onUserSwitching(userId);
            }
        }
        try {
            reply.sendResult(null);
        } catch (RemoteException e) {
        }
    }

    /**
     * Handle {@link #MSG_USER_SWITCH_COMPLETE}
     */
    protected void handleUserSwitchComplete(int userId) {
        for (int i = 0; i < mCallbacks.size(); i++) {
            KeyguardUpdateMonitorCallback cb = mCallbacks.get(i).get();
            if (cb != null) {
                cb.onUserSwitchComplete(userId);
            }
        }
    }

    /**
     * This is exposed since {@link Intent#ACTION_BOOT_COMPLETED} is not sticky. If
     * keyguard crashes sometime after boot, then it will never receive this
     * broadcast and hence not handle the event. This method is ultimately called by
     * PhoneWindowManager in this case.
     */
    protected void dispatchBootCompleted() {
        mHandler.sendEmptyMessage(MSG_BOOT_COMPLETED);
    }

    /**
     * Handle {@link #MSG_BOOT_COMPLETED}
     */
    protected void handleBootCompleted() {
        if (mBootCompleted) return;
        mBootCompleted = true;
        mAudioManager = new AudioManager(mContext);
        mAudioManager.registerRemoteControlDisplay(mRemoteControlDisplay);
        for (int i = 0; i < mCallbacks.size(); i++) {
            KeyguardUpdateMonitorCallback cb = mCallbacks.get(i).get();
            if (cb != null) {
                cb.onBootCompleted();
            }
        }
    }

    /**
     * We need to store this state in the KeyguardUpdateMonitor since this class will not be
     * destroyed.
     */
    public boolean hasBootCompleted() {
        return mBootCompleted;
    }

    /**
     * Handle {@link #MSG_USER_REMOVED}
     */
    protected void handleUserRemoved(int userId) {
        for (int i = 0; i < mCallbacks.size(); i++) {
            KeyguardUpdateMonitorCallback cb = mCallbacks.get(i).get();
            if (cb != null) {
                cb.onUserRemoved(userId);
            }
        }
    }

    /**
     * Handle {@link #MSG_DEVICE_PROVISIONED}
     */
    protected void handleDeviceProvisioned() {
        for (int i = 0; i < mCallbacks.size(); i++) {
            KeyguardUpdateMonitorCallback cb = mCallbacks.get(i).get();
            if (cb != null) {
                cb.onDeviceProvisioned();
            }
        }
        if (mDeviceProvisionedObserver != null) {
            // We don't need the observer anymore...
            mContext.getContentResolver().unregisterContentObserver(mDeviceProvisionedObserver);
            mDeviceProvisionedObserver = null;
        }
    }

    /**
     * Handle {@link #MSG_PHONE_STATE_CHANGED}
     */
    protected void handlePhoneStateChanged(String newState) {
        if (DEBUG) Log.d(TAG, "handlePhoneStateChanged(" + newState + ")");
        if (TelephonyManager.EXTRA_STATE_IDLE.equals(newState)) {
            mPhoneState = TelephonyManager.CALL_STATE_IDLE;
        } else if (TelephonyManager.EXTRA_STATE_OFFHOOK.equals(newState)) {
            mPhoneState = TelephonyManager.CALL_STATE_OFFHOOK;
        } else if (TelephonyManager.EXTRA_STATE_RINGING.equals(newState)) {
            mPhoneState = TelephonyManager.CALL_STATE_RINGING;
        }
        for (int i = 0; i < mCallbacks.size(); i++) {
            KeyguardUpdateMonitorCallback cb = mCallbacks.get(i).get();
            if (cb != null) {
                cb.onPhoneStateChanged(mPhoneState);
            }
        }
    }

    /**
     * Handle {@link #MSG_RINGER_MODE_CHANGED}
     */
    protected void handleRingerModeChange(int mode) {
        if (DEBUG) Log.d(TAG, "handleRingerModeChange(" + mode + ")");
        mRingMode = mode;
        for (int i = 0; i < mCallbacks.size(); i++) {
            KeyguardUpdateMonitorCallback cb = mCallbacks.get(i).get();
            if (cb != null) {
                cb.onRingerModeChanged(mode);
            }
        }
    }

    /**
     * Handle {@link #MSG_TIME_UPDATE}
     */
    private void handleTimeUpdate() {
        if (DEBUG) Log.d(TAG, "handleTimeUpdate");
        for (int i = 0; i < mCallbacks.size(); i++) {
            KeyguardUpdateMonitorCallback cb = mCallbacks.get(i).get();
            if (cb != null) {
                cb.onTimeChanged();
            }
        }
    }

    /**
     * Handle {@link #MSG_BATTERY_UPDATE}
     */
    private void handleBatteryUpdate(BatteryStatus status) {
        final int idx = status.index;
        final boolean batteryUpdateInteresting = isBatteryUpdateInteresting(mBatteryStatus[idx], status);
        if (DEBUG) Log.d(TAG, "handleBatteryUpdate index=" + idx + " updateInteresting="+batteryUpdateInteresting);
        mBatteryStatus[idx] = status;
        if (batteryUpdateInteresting) {
            for (int i = 0; i < mCallbacks.size(); i++) {
                KeyguardUpdateMonitorCallback cb = mCallbacks.get(i).get();
                if (cb != null) {
                    cb.onRefreshBatteryInfo(status);
                }
            }
        }
    }

    /**
     * Handle {@link #MSG_CARRIER_INFO_UPDATE}
     */
    private void handleCarrierInfoUpdate(int simId) {
        if (DEBUG) Log.d(TAG, "handleCarrierInfoUpdate: plmn = " + mTelephonyPlmn
            + ", spn = " + mTelephonySpn);

        for (int i = 0; i < mCallbacks.size(); i++) {
            KeyguardUpdateMonitorCallback cb = mCallbacks.get(i).get();
            if (cb != null) {
                if (KeyguardUtils.isValidSimId(simId)) {
                    cb.onRefreshCarrierInfo(mTelephonyPlmn[simId], mTelephonySpn[simId], simId);
                }
            }
        }
    }

    /**
     * Handle {@link #MSG_SIM_STATE_CHANGE}
     */
    private void handleSimStateChange(SimArgs simArgs) {
        final IccCardConstants.State state = simArgs.simState;

        if (DEBUG) {
            Log.d(TAG, "handleSimStateChange: intentValue = " + simArgs + " "
                    + "state resolved to " + state.toString());
        }

        /// M: Support GeminiPlus
        if (!KeyguardUtils.isValidSimId(simArgs.simId)) {
            Log.d(TAG, "handleSimStateChange: !isValidSimId");
            return;
        }

        if (DEBUG) {
            Log.d(TAG, "handleSimStateChange: intentValue = " + simArgs + " "
                    + "state resolved to " + state.toString()
                    + ", oldssimtate=" + mSimState[simArgs.simId]);
        }

        IccCardConstants.State tempState;
        tempState = mSimState[simArgs.simId];
        mSimLastState[simArgs.simId] = mSimState[simArgs.simId];

        if (state != IccCardConstants.State.UNKNOWN && 
            (state == IccCardConstants.State.NETWORK_LOCKED || state != tempState)) {
            if (DEBUG_SIM_STATES) Log.v(TAG, "dispatching state: " + state + " to sim " + simArgs.simId);
            mSimState[simArgs.simId] = state;
            KeyguardUtils.xlogD(TAG, "handleSimStateChange: mSimState = " + mSimState[simArgs.simId]);
            for (int i = 0; i < mCallbacks.size(); i++) {
                KeyguardUpdateMonitorCallback cb = mCallbacks.get(i).get();
                if (cb != null) {
                    cb.onSimStateChanged(state, simArgs.simId);
                }
            }
        }
    }

    /**
     * Handle {@link #MSG_CLOCK_VISIBILITY_CHANGED}
     */
    private void handleClockVisibilityChanged() {
        if (DEBUG) Log.d(TAG, "handleClockVisibilityChanged()");
        for (int i = 0; i < mCallbacks.size(); i++) {
            KeyguardUpdateMonitorCallback cb = mCallbacks.get(i).get();
            if (cb != null) {
                cb.onClockVisibilityChanged();
            }
        }
    }

    /**
     * Handle {@link #MSG_KEYGUARD_VISIBILITY_CHANGED}
     */
    private void handleKeyguardVisibilityChanged(int showing) {
        if (DEBUG) Log.d(TAG, "handleKeyguardVisibilityChanged(" + showing + ")");
        boolean isShowing = (showing == 1);
        mKeyguardIsVisible = isShowing;
        for (int i = 0; i < mCallbacks.size(); i++) {
            KeyguardUpdateMonitorCallback cb = mCallbacks.get(i).get();
            if (cb != null) {
                cb.onKeyguardVisibilityChangedRaw(isShowing);
            }
        }
    }

    /**
     * Handle {@link #MSG_REPORT_EMERGENCY_CALL_ACTION}
     */
    private void handleReportEmergencyCallAction() {
        for (int i = 0; i < mCallbacks.size(); i++) {
            KeyguardUpdateMonitorCallback cb = mCallbacks.get(i).get();
            if (cb != null) {
                cb.onEmergencyCallAction();
            }
        }
    }

    public boolean isKeyguardVisible() {
        return mKeyguardIsVisible;
    }

    public boolean isSwitchingUser() {
        return mSwitchingUser;
    }

    private static boolean isBatteryUpdateInteresting(BatteryStatus old, BatteryStatus current) {
        final boolean nowPluggedIn = current.isPluggedIn();
        final boolean wasPluggedIn = old.isPluggedIn();
        final boolean stateChangedWhilePluggedIn =
            wasPluggedIn == true && nowPluggedIn == true
            && (old.status != current.status);

        // change in plug state is always interesting
        if (wasPluggedIn != nowPluggedIn || stateChangedWhilePluggedIn) {
            return true;
        }

        // change in battery level while plugged in
        /// M: We remove "nowPluggedIn" condition here.
        /// To fix the issue that if HW give up a low battery level(below threshold)
        /// and then a high battery level(above threshold) while device is not pluggin,
        /// then Keyguard may never be able be show
        /// charging text on screen when pluggin
        if (old.level != current.level) {
            return true;
        }

        // change where battery needs charging
        if (!nowPluggedIn && current.isBatteryLow() && current.level != old.level) {
            return true;
        }
        return false;
    }

    /**
     * @param intent The intent with action {@link TelephonyIntents#SPN_STRINGS_UPDATED_ACTION}
     * @return The string to use for the plmn, or null if it should not be shown.
     */
    private CharSequence getTelephonyPlmnFrom(Intent intent) {
        if (intent.getBooleanExtra(TelephonyIntents.EXTRA_SHOW_PLMN, false)) {
            KeyguardUtils.xlogD(TAG,"EXTRA_SHOW_PLMN =  TRUE ");
            final String plmn = intent.getStringExtra(TelephonyIntents.EXTRA_PLMN);
            return (plmn != null) ? plmn : getDefaultPlmn();
        } else {
            KeyguardUtils.xlogD(TAG,"EXTRA_SHOW_PLMN = FALSE  ");
        }
        return null;
    }

    /**
     * @return The default plmn (no service)
     */
    private CharSequence getDefaultPlmn() {
        return mContext.getResources().getText(R.string.keyguard_carrier_default);
    }

    /**
     * @param intent The intent with action {@link Telephony.Intents#SPN_STRINGS_UPDATED_ACTION}
     * @return The string to use for the plmn, or null if it should not be shown.
     */
    private CharSequence getTelephonySpnFrom(Intent intent) {
        if (intent.getBooleanExtra(TelephonyIntents.EXTRA_SHOW_SPN, false)) {
            final String spn = intent.getStringExtra(TelephonyIntents.EXTRA_SPN);
            if (spn != null) {
                return spn;
            }
        }
        return null;
    }

    /**
     * Remove the given observer's callback.
     *
     * @param callback The callback to remove
     */
    public void removeCallback(KeyguardUpdateMonitorCallback callback) {
        if (DEBUG) Log.v(TAG, "*** unregister callback for " + callback);
        for (int i = mCallbacks.size() - 1; i >= 0; i--) {
            if (mCallbacks.get(i).get() == callback) {
                mCallbacks.remove(i);
            }
        }
    }

    /**
     * Register to receive notifications about general keyguard information
     * (see {@link KeyguardUpdateMonitorCallback}.
     * @param callback The callback to register
     */
    public void registerCallback(KeyguardUpdateMonitorCallback callback) {
        if (DEBUG) Log.v(TAG, "*** register callback for " + callback);
        // Prevent adding duplicate callbacks
        for (int i = 0; i < mCallbacks.size(); i++) {
            if (mCallbacks.get(i).get() == callback) {
                if (DEBUG) Log.e(TAG, "Object tried to add another callback",
                        new Exception("Called by"));
                return;
            }
        }
        mCallbacks.add(new WeakReference<KeyguardUpdateMonitorCallback>(callback));
        removeCallback(null); // remove unused references
        sendUpdates(callback);

        ///M: in order to improve performance, add a flag to fliter redundant visibility change callbacks
        mNewClientRegUpdateMonitor = true;
    }

    private void sendUpdates(KeyguardUpdateMonitorCallback callback) {
        // Notify listener of the current state
        for (int i = 0; i < KEYGUARD_BATTERY_NUMBER; i++) {
            callback.onRefreshBatteryInfo(mBatteryStatus[i]);
        }
        callback.onTimeChanged();
        callback.onRingerModeChanged(mRingMode);
        callback.onPhoneStateChanged(mPhoneState);
        /// M: Modify to refresh gemini carrier
        for (int i = PhoneConstants.GEMINI_SIM_1; i <= KeyguardUtils.getMaxSimId(); i++) {
            callback.onRefreshCarrierInfo(mTelephonyPlmn[i], mTelephonySpn[i], i);
        }
        callback.onClockVisibilityChanged();
        /// M: Modify phone state change callback to support gemini
        for (int i = PhoneConstants.GEMINI_SIM_1; i <= KeyguardUtils.getMaxSimId(); i++) {
            callback.onSimStateChanged(mSimState[i], i);
        }

        callback.onMusicClientIdChanged(
                mDisplayClientState.clientGeneration,
                mDisplayClientState.clearing,
                mDisplayClientState.intent);
        callback.onMusicPlaybackStateChanged(mDisplayClientState.playbackState,
                mDisplayClientState.playbackEventTime);

        /// M: Support GeminiPlus
        for (int i = PhoneConstants.GEMINI_SIM_1; i <= KeyguardUtils.getMaxSimId(); i++) {
            callback.onSearchNetworkUpdate(i, mNetSearching[i]);
        }
    }

    public void sendKeyguardVisibilityChanged(boolean showing) {
        ///M: in order to improve performance we skip callbacks if no new client registered
        if (mNewClientRegUpdateMonitor || showing != mShowing) {
            if (DEBUG) Log.d(TAG, "sendKeyguardVisibilityChanged(" + showing + ")");
            Message message = mHandler.obtainMessage(MSG_KEYGUARD_VISIBILITY_CHANGED);
            message.arg1 = showing ? 1 : 0;
            message.sendToTarget();
            mNewClientRegUpdateMonitor = false;
            mShowing = showing;
        }
    }

    public void reportClockVisible(boolean visible) {
        mClockVisible = visible;
        mHandler.obtainMessage(MSG_CLOCK_VISIBILITY_CHANGED).sendToTarget();
    }

    public IccCardConstants.State getSimState(int simId) {
        /// M: Support GeminiPlus
        if (KeyguardUtils.isValidSimId(simId)) {
            KeyguardUtils.xlogD(TAG, "mSimState = " + mSimState[simId] + " for simId = " + simId);
            return mSimState[simId];
        } else {
            KeyguardUtils.xlogD(TAG, "mSimState = " + mSimState[PhoneConstants.GEMINI_SIM_1] + " for default sim");
            return mSimState[PhoneConstants.GEMINI_SIM_1];
        }
    }

    /**
     * Report that the user successfully entered the SIM PIN or PUK/SIM PIN so we
     * have the information earlier than waiting for the intent
     * broadcast from the telephony code.
     *
     * NOTE: Because handleSimStateChange() invokes callbacks immediately without going
     * through mHandler, this *must* be called from the UI thread.
     *  
     * M: Remove following code because we need to wait for next SIM state event.
     *    Since we integrated SIM ME unlock, we need to wait for READY or NETWORK_LOCKED
     *    after we passed PIN or PUK.
     */
    public void reportSimUnlocked() {
        ///M: handleSimStateChange(new SimArgs(IccCardConstants.State.READY));
    }

    /**
     * Report that the emergency call button has been pressed and the emergency dialer is
     * about to be displayed.
     *
     * @param bypassHandler runs immediately.
     *
     * NOTE: Must be called from UI thread if bypassHandler == true.
     */
    public void reportEmergencyCallAction(boolean bypassHandler) {
        if (!bypassHandler) {
            mHandler.obtainMessage(MSG_REPORT_EMERGENCY_CALL_ACTION).sendToTarget();
        } else {
            handleReportEmergencyCallAction();
        }
    }

    /**
     * @return Whether the device is provisioned (whether they have gone through
     *   the setup wizard)
     */
    public boolean isDeviceProvisioned() {
        return mDeviceProvisioned;
    }

    public int getFailedUnlockAttempts() {
        return mFailedAttempts;
    }

    public void clearFailedUnlockAttempts() {
        mFailedAttempts = 0;
        mFailedBiometricUnlockAttempts = 0;
    }

    public void reportFailedUnlockAttempt() {
        mFailedAttempts++;
    }

    public boolean isClockVisible() {
        return mClockVisible;
    }

    public int getPhoneState() {
        return mPhoneState;
    }

    public void reportFailedBiometricUnlockAttempt() {
        mFailedBiometricUnlockAttempts++;
    }

    public boolean getMaxBiometricUnlockAttemptsReached() {
        return mFailedBiometricUnlockAttempts >= FAILED_BIOMETRIC_UNLOCK_ATTEMPTS_BEFORE_BACKUP;
    }

    public boolean isAlternateUnlockEnabled() {
        return mAlternateUnlockEnabled;
    }

    public void setAlternateUnlockEnabled(boolean enabled) {
        if(isDocktoDesk() && enabled) {
            ///M: Ignore alternate unlock enable request when in docked state
        } else {
            mAlternateUnlockEnabled = enabled;
        }
    }

    /// M: Checks if any sim PIN lock no matter it is dismissed or not
    public boolean isSimLocked() {
        boolean bHasSimLock = false;
        for (int i = PhoneConstants.GEMINI_SIM_1; i <= KeyguardUtils.getMaxSimId(); i++) {
            bHasSimLock = bHasSimLock || isSimLocked(i);
            if (bHasSimLock) {
                break;
            }
        }
        return bHasSimLock;
    }

    private boolean isSimLocked(int simId) {
        IccCardConstants.State simState = getSimState(simId);
        return simState == IccCardConstants.State.PIN_REQUIRED
        || simState == IccCardConstants.State.PUK_REQUIRED
        || simState == IccCardConstants.State.NETWORK_LOCKED
        || simState == IccCardConstants.State.PERM_DISABLED;
    }

    /// M: Check if any sim PIN lock not yet unlocked, dismissed or permanently locked.
    public boolean isSimPinSecure() {
        boolean bHasSimPinSecure = false;
        for (int i = PhoneConstants.GEMINI_SIM_1; i <= KeyguardUtils.getMaxSimId(); i++) {
            bHasSimPinSecure = bHasSimPinSecure || isSimPinSecure(i);
            if (bHasSimPinSecure) {
                break;
            }
        }
        return bHasSimPinSecure;
    }

    private boolean isSimPinSecure(int simId) {
        IccCardConstants.State simState = getSimState(simId);
        return ((simState == IccCardConstants.State.PIN_REQUIRED && !getPINDismissFlag(simId, SimLockType.SIM_LOCK_PIN))
            || (simState == IccCardConstants.State.PUK_REQUIRED && !getPINDismissFlag(simId, SimLockType.SIM_LOCK_PUK))
            || (simState == IccCardConstants.State.NETWORK_LOCKED && !getPINDismissFlag(simId, SimLockType.SIM_LOCK_ME))
            || simState == IccCardConstants.State.PERM_DISABLED);
    }

    public DisplayClientState getCachedDisplayClientState() {
        return mDisplayClientState;
    }

    // TODO: use these callbacks elsewhere in place of the existing notifyScreen*()
    // (KeyguardViewMediator, KeyguardHostView)
    public void dispatchScreenTurnedOn() {
        synchronized (this) {
            mScreenOn = true;
        }
        mHandler.sendEmptyMessage(MSG_SCREEN_TURNED_ON);
    }

    public void dispatchScreenTurndOff(int why) {
        synchronized(this) {
            mScreenOn = false;
        }
        mHandler.sendMessage(mHandler.obtainMessage(MSG_SCREEN_TURNED_OFF, why, 0));
    }

    public boolean isScreenOn() {
        return mScreenOn;
    }

    /********************************************************
     ** Mediatek add begin
     ********************************************************/

    /// M: Incoming Indicator for Keyguard Rotation
    private static final String CLEAR_NEW_EVENT_VIEW_INTENT = "android.intent.action.KEYGUARD_CLEAR_UREAD_TIPS";

    private static long mQueryBaseTime;
    
    /// M:[SmartBook]Add SmartBook intent @{
    //Sticky broadcast of the current SMARTBOOK plug state.
    public final static String ACTION_SMARTBOOK_PLUG = "android.intent.action.SMARTBOOK_PLUG";

    //Extra in {@link #ACTION_SMARTBOOK_PLUG} indicating the state: true if
    //plug in to SMARTBOOK, false if not.
    public final static String EXTRA_SMARTBOOK_PLUG_STATE = "state";

    /// M: Save the last sim state, which will be used in KeyguardViewMediator to reset keyguard @{
    private IccCardConstants.State mSimLastState[]; /// M: Support GeminiPlus
    /// @}
    
    /// M: PhoneStateListenr used to told client to update NetWorkSearching state @{
    private PhoneStateListener mPhoneStateListener; 
    /// @}

    /// M: Used in PhoneStateChange listener to notify client to update, these two flag indicate weather
    /// sim card is searching network @{
    boolean mNetSearching[]; /// M: Support GeminiPlus

    ///M: in order to improve performance, add a flag to fliter redundant visibility change callbacks
    private boolean mNewClientRegUpdateMonitor = false;
    private boolean mShowing = true;
    
    /// @}
    /// M: dock status update message
    private static final int MSG_DOCK_STATUS_UPDATE = 1014;
        

    private static final int PIN_PUK_ME_RESET = 0x0000;
    private static final int SIM_1_PIN_PUK_MASK = 0x0001 | 0x0001 << 2;
    private static final int SIM_1_PIN_DISMISSED = 0x0001;
    private static final int SIM_1_PUK_DISMISSED = 0x0001 << 2;
    private static final int SIM_1_ME_DISMISSED = 0x0001 << 8;
	
    private static final int SIM_2_PIN_PUK_MASK = 0x0001 << 1 | 0x0001 << 3;
    private static final int SIM_2_PIN_DISMISSED = 0x0001 << 1;
    private static final int SIM_2_PUK_DISMISSED = 0x0001 << 3;
    private static final int SIM_2_ME_DISMISSED = 0x0001 << 9;
    /// M: Support GeminiPlus
    private static final int SIM_3_PIN_PUK_MASK = 0x0001 << 4 | 0x0001 << 6;
    private static final int SIM_3_PIN_DISMISSED = 0x0001 << 4;
    private static final int SIM_3_PUK_DISMISSED = 0x0001 << 6;
    private static final int SIM_3_ME_DISMISSED = 0x0001 << 10;

    private static final int SIM_4_PIN_PUK_MASK = 0x0001 << 5 | 0x0001 << 7;
    private static final int SIM_4_PIN_DISMISSED = 0x0001 << 5;
    private static final int SIM_4_PUK_DISMISSED = 0x0001 << 7;
    private static final int SIM_4_ME_DISMISSED = 0x0001 << 11;

    public enum SimLockType{
        SIM_LOCK_PIN,
        SIM_LOCK_PUK,
        SIM_LOCK_ME
    }

    /// M: Flag used to indicate weather sim1 or sim2 card's pin/puk is dismissed by user.
    private int mPinPukMeDismissFlag = PIN_PUK_ME_RESET;


    /**
     ** M: Used to set specified sim card's pin or puk dismiss flag
     * 
     * @param simId the id of the sim card to set dismiss flag
     * @param lockType specify what kind of sim lock to be set
     * @param dismiss true to dismiss this flag, false to clear
     */
    public void setPINDismiss(int simId, SimLockType lockType, boolean dismiss) {
        Log.i(TAG, "setPINDismiss, simId=" + simId + ", lockType=" + lockType
                + ", dismiss=" + dismiss + ", mPinPukMeDismissFlag=" + mPinPukMeDismissFlag);
        int pinFlag;
        int pukFlag;
        int meFlag;
        int flag2Dismiss = PIN_PUK_ME_RESET;
        /// M: Support GeminiPlus
        if (simId == PhoneConstants.GEMINI_SIM_1) {
            pinFlag = SIM_1_PIN_DISMISSED;
            pukFlag = SIM_1_PUK_DISMISSED;
            meFlag =  SIM_1_ME_DISMISSED;
        } else if (simId == PhoneConstants.GEMINI_SIM_2) {
            pinFlag = SIM_2_PIN_DISMISSED;
            pukFlag = SIM_2_PUK_DISMISSED;
            meFlag =  SIM_2_ME_DISMISSED;
        } else if (simId == PhoneConstants.GEMINI_SIM_3) {
            pinFlag = SIM_3_PIN_DISMISSED;
            pukFlag = SIM_3_PUK_DISMISSED;
            meFlag =  SIM_3_ME_DISMISSED;
        } else {
            pinFlag = SIM_4_PIN_DISMISSED;
            pukFlag = SIM_4_PUK_DISMISSED;
            meFlag =  SIM_4_ME_DISMISSED;
        }
        switch(lockType){
            case SIM_LOCK_PIN:
                flag2Dismiss = pinFlag;
                break;
            case SIM_LOCK_PUK:
                flag2Dismiss = pukFlag;
                break;
            case SIM_LOCK_ME:
                flag2Dismiss = meFlag;
                break;
        }
        if (dismiss) {
            mPinPukMeDismissFlag |= flag2Dismiss;
        } else {
            mPinPukMeDismissFlag &= ~flag2Dismiss;
        }
    }

    /**
     ** M: Used to get specified sim card's pin or puk dismiss flag
     * 
     * @param simId the id of the sim card to set dismiss flag
     * @param lockType specify what kind of sim lock to be set
     * @return Returns false if dismiss flag is set.
     */
    public boolean getPINDismissFlag(int simId, SimLockType lockType) {
        Log.i(TAG, "getPINDismissFlag, simId=" + simId + ", lockType="
                + lockType + ", mPinPukMeDismissFlag=" + mPinPukMeDismissFlag);
        int pinFlag;
        int pukFlag;
        int meFlag;
        int flag2Check = PIN_PUK_ME_RESET;
        /// M: Support GeminiPlus
        if (simId == PhoneConstants.GEMINI_SIM_1) {
            pinFlag = SIM_1_PIN_DISMISSED;
            pukFlag = SIM_1_PUK_DISMISSED;
            meFlag =  SIM_1_ME_DISMISSED;
        } else if (simId == PhoneConstants.GEMINI_SIM_2) {
            pinFlag = SIM_2_PIN_DISMISSED;
            pukFlag = SIM_2_PUK_DISMISSED;
            meFlag =  SIM_2_ME_DISMISSED;
        } else if (simId == PhoneConstants.GEMINI_SIM_3) {
            pinFlag = SIM_3_PIN_DISMISSED;
            pukFlag = SIM_3_PUK_DISMISSED;
            meFlag =  SIM_3_ME_DISMISSED;
        } else {
            pinFlag = SIM_4_PIN_DISMISSED;
            pukFlag = SIM_4_PUK_DISMISSED;
            meFlag =  SIM_4_ME_DISMISSED;
        }
        boolean result = false;
        switch(lockType){
            case SIM_LOCK_PIN:
                flag2Check = pinFlag;
                break;
            case SIM_LOCK_PUK:
                flag2Check = pukFlag;
                break;
            case SIM_LOCK_ME:
                flag2Check = meFlag;
                break;
        }
        result = (mPinPukMeDismissFlag & flag2Check) == flag2Check ? true : false;
        return result;
    }

    public IccCardConstants.State getLastSimState(int simId) {
        /// M: Support GeminiPlus
        if (KeyguardUtils.isValidSimId(simId)) {
            KeyguardUtils.xlogD(TAG, "mSimLastState = " + mSimLastState[simId] + " for simId = " + simId);
            return mSimLastState[simId];
        } else {
            KeyguardUtils.xlogD(TAG, "mSimLastState = " + mSimLastState[PhoneConstants.GEMINI_SIM_1] + " for default sim");
            return mSimLastState[PhoneConstants.GEMINI_SIM_1];
        }
    }

    /// M: init members
    private void initMembers() {
        /// M: Support GeminiPlus
        mSimState = new IccCardConstants.State[KeyguardUtils.getNumOfSim()];
        mSimLastState = new IccCardConstants.State[KeyguardUtils.getNumOfSim()];
        mTelephonyPlmn = new CharSequence[KeyguardUtils.getNumOfSim()];
        mTelephonySpn = new CharSequence[KeyguardUtils.getNumOfSim()];
        mTelephonyHnbName = new CharSequence[KeyguardUtils.getNumOfSim()];
        mTelephonyCsgId = new CharSequence[KeyguardUtils.getNumOfSim()];
        mNetSearching = new boolean[KeyguardUtils.getNumOfSim()];
        mSimMeCategory = new int[KeyguardUtils.getNumOfSim()];
        mSimMeLeftRetryCount = new int[KeyguardUtils.getNumOfSim()];

        for (int i = PhoneConstants.GEMINI_SIM_1; i <= KeyguardUtils.getMaxSimId(); i++) {
            mSimState[i] = IccCardConstants.State.UNKNOWN;
            mSimLastState[i] = IccCardConstants.State.UNKNOWN;
            mNetSearching[i] = false;
            mSimMeCategory[i] = 0;
            mSimMeLeftRetryCount[i] = 5;
        }
        
        /// M: Init phone state listener, used to update sim state
        initPhoneStateListener();
    }


    /**
     * M: reportSimUnlocked For Gemini phone
     */
    public void reportSimUnlocked(int simId) {
        if (DEBUG) KeyguardUtils.xlogD(TAG, "reportSimUnlocked");
        handleSimStateChange(new SimArgs(IccCardConstants.State.READY, simId, 0));
    }

    /// M: Initialize phone state listener, used to update sim's network searching state
    private void initPhoneStateListener() {
        /// M: Support GeminiPlus
        mPhoneStateListener = new PhoneStateListener() {
           @Override
           public void onServiceStateChanged(ServiceState state) {
                if (state != null) {
                    final int simId = KeyguardUtils.isGemini() 
                        ? state.getMySimId() : PhoneConstants.GEMINI_SIM_1;
                    if (!KeyguardUtils.isValidSimId(simId)) {
                        return;
                    }
                    int regState = state.getRegState();
                    if (mNetSearching[simId] && (regState != ServiceState.REGISTRATION_STATE_NOT_REGISTERED_AND_SEARCHING)) {
                        KeyguardUtils.xlogD(TAG, "PhoneStateListener, sim1 searching finished");
                        mNetSearching[simId] = false;
                    }
                   
                    if (ServiceState.REGISTRATION_STATE_NOT_REGISTERED_AND_SEARCHING == regState) {
                        KeyguardUtils.xlogD(TAG, "PhoneStateListener, sim1 searching begin");
                        mNetSearching[simId] = true;
                    }
                    for (int i = 0; i < mCallbacks.size(); i++) {
                        KeyguardUpdateMonitorCallback cb = mCallbacks.get(i).get();
                        if (cb != null) {
                            cb.onSearchNetworkUpdate(simId, mNetSearching[simId]);
                        }
                    }
                }
            }
        };

        if (KeyguardUtils.isGemini()) {
            try {
                ITelephony t = ITelephony.Stub.asInterface(ServiceManager.getService(Context.TELEPHONY_SERVICE));
                Boolean notifyNow = (t != null);
                ITelephonyRegistry tr1 = ITelephonyRegistry.Stub.asInterface(ServiceManager.getService("telephony.registry"));
                tr1.listen(TAG, mPhoneStateListener.getCallback(), PhoneStateListener.LISTEN_SERVICE_STATE, notifyNow);
                ITelephonyRegistry tr2 = ITelephonyRegistry.Stub.asInterface(ServiceManager.getService("telephony.registry2"));
                tr2.listen(TAG, mPhoneStateListener.getCallback(), PhoneStateListener.LISTEN_SERVICE_STATE, notifyNow);
                /// M: Support GeminiPlus
                if (KeyguardUtils.getNumOfSim() >= 3) {
                    ITelephonyRegistry tr3 = ITelephonyRegistry.Stub.asInterface(ServiceManager.getService("telephony.registry3"));
                    tr3.listen(TAG, mPhoneStateListener.getCallback(), PhoneStateListener.LISTEN_SERVICE_STATE, notifyNow);
                }
                if (KeyguardUtils.getNumOfSim() >= 4) {
                    ITelephonyRegistry tr4 = ITelephonyRegistry.Stub.asInterface(ServiceManager.getService("telephony.registry4"));
                    tr4.listen(TAG, mPhoneStateListener.getCallback(), PhoneStateListener.LISTEN_SERVICE_STATE, notifyNow);
                }
            } catch (RemoteException e) {
                Log.e(TAG, "Fail to listen GEMINI state", e);
            } catch (NullPointerException e) {
                Log.e(TAG, "The registry is null", e);
            }
        } else {
            ((TelephonyManager)mContext.getSystemService(Context.TELEPHONY_SERVICE))
                .listen(mPhoneStateListener, PhoneStateListener.LISTEN_SERVICE_STATE);
        }
    }


    private int mSimMeCategory[];   //current unlocking category of each SIM card.
    private int mSimMeLeftRetryCount[];  // current left retry count of current ME lock category.
    private static final String QUERY_SIMME_LOCK_RESULT = "com.android.phone.QUERY_SIMME_LOCK_RESULT";
    private static final String SIMME_LOCK_LEFT_COUNT = "com.android.phone.SIMME_LOCK_LEFT_COUNT";

    private void processUnlockSimIntent(Intent intent) {
        IccCardConstants.State state;
        int simId = intent.getIntExtra(CellConnMgr.EXTRA_SIM_SLOT, PhoneConstants.GEMINI_SIM_1);
        int unlockType = intent.getIntExtra(CellConnMgr.EXTRA_UNLOCK_TYPE, CellConnMgr.VERIFY_TYPE_PIN);
        int meCategory = 0;
        KeyguardUtils.xlogD(TAG, "ACTION_UNLOCK_SIM_LOCK, unlockType="+unlockType +",simId="+simId );
        
        switch(unlockType) {
            case CellConnMgr.VERIFY_TYPE_PIN:
                state = IccCardConstants.State.PIN_REQUIRED;
                break;
            case CellConnMgr.VERIFY_TYPE_PUK:
                state = IccCardConstants.State.PUK_REQUIRED;
                break;
            case CellConnMgr.VERIFY_TYPE_SIMMELOCK:
                state = IccCardConstants.State.NETWORK_LOCKED;
                meCategory = intent.getIntExtra(CellConnMgr.EXTRA_SIMME_LOCK_TYPE, 0);
                KeyguardUtils.xlogD(TAG, "VERIFY_TYPE_SIMMELOCK, meCategory="+meCategory);
                break;
            default:
                state = IccCardConstants.State.UNKNOWN;
                break;
        }
        mSimState[simId] = IccCardConstants.State.UNKNOWN; // set sim state as stranslating state
        SimArgs simArgs = new SimArgs(state, simId, meCategory);
        if (CellConnMgr.VERIFY_TYPE_SIMMELOCK == unlockType) {
            //to create new thread to query SIM ME lock status
            // after finish query, send MSG_SIM_STATE_CHANGE message
            new simMeStatusQueryThread(simArgs).start();
        } else {
            mHandler.sendMessage(mHandler.obtainMessage(MSG_SIM_STATE_CHANGE, simArgs));
        }
    }
    
    private class simMeStatusQueryThread extends Thread {
        SimArgs simArgs;
        
        simMeStatusQueryThread(SimArgs simArgs) {
            this.simArgs = simArgs;
        }

        @Override
        public void run() {
            if (simArgs.simId >= KeyguardUtils.getNumOfSim()) {
                throw new IllegalArgumentException("simId larger than simCard arrary size");
            }
            try {
                mSimMeCategory[simArgs.simId] = simArgs.simMECategory;
                Log.d(TAG, "queryNetworkLock, " + "SimId =" + simArgs.simId + ", simMECategory ="+ simArgs.simMECategory);
                if (simArgs.simMECategory < 0 || simArgs.simMECategory > 5) {
                    return;
                }

                Bundle bundle = ITelephonyEx.Stub.asInterface(ServiceManager.checkService("phoneEx")).queryNetworkLock(simArgs.simMECategory, simArgs.simId);
                boolean query_result = bundle.getBoolean(QUERY_SIMME_LOCK_RESULT, false);
                Log.d(TAG, "queryNetworkLock, " + "query_result =" + query_result);
                if (query_result) {
                    mSimMeLeftRetryCount[simArgs.simId] = bundle.getInt(SIMME_LOCK_LEFT_COUNT, 5);
                } else {
                    Log.e(TAG, "queryIccNetworkLock result fail");
                }
                mHandler.sendMessage(mHandler.obtainMessage(MSG_SIM_STATE_CHANGE, simArgs));
            } catch (Exception e) {
                Log.e(TAG, "queryIccNetworkLock got exception: " + e.getMessage());
            }
        }
    }

    public int getSimMeCategory(int simId) {
        return mSimMeCategory[simId];
    }

    public int getSimMeLeftRetryCount(int simId) {
        return mSimMeLeftRetryCount[simId];
    }
    public void minusSimMeLeftRetryCount(int simId) {
        if (mSimMeLeftRetryCount[simId] > 0 ) {
            mSimMeLeftRetryCount[simId]--;
        }
    }

    /** M: LTE CSG feature
     * @param intent The intent with action {@link Telephony.Intents#SPN_STRINGS_UPDATED_ACTION}
     * @return The string to use for the HNB name, or null if it should not be shown.
     */
    private CharSequence getTelephonyHnbNameFrom(Intent intent) {
        final String hnbName = intent.getStringExtra(TelephonyIntents.EXTRA_HNB_NAME);
        return hnbName;
    }

    /** M: LTE CSG feature
     * @param intent The intent with action {@link Telephony.Intents#SPN_STRINGS_UPDATED_ACTION}
     * @return The string to use for the CSG id, or null if it should not be shown.
     */
    private CharSequence getTelephonyCsgIdFrom(Intent intent) {
        final String csgId = intent.getStringExtra(TelephonyIntents.EXTRA_CSG_ID);
        return csgId;
    }

    /// M: gemini support
    public CharSequence getTelephonyPlmn(int simId) {
        /// M: Support GeminiPlus
        if (KeyguardUtils.isValidSimId(simId)) {
            return mTelephonyPlmn[simId];
        } else {
            return mTelephonyPlmn[PhoneConstants.GEMINI_SIM_1];
        }
    }

    /// M: gemini support
    public CharSequence getTelephonySpn(int simId) {
        /// M: Support GeminiPlus
        if (KeyguardUtils.isValidSimId(simId)) {
            return mTelephonySpn[simId];
        } else {
            return mTelephonySpn[PhoneConstants.GEMINI_SIM_1];
        }
    }
    
    /// M: CSG support
    public CharSequence getTelephonyHnbName(int simId) {
        /// M: Support GeminiPlus
        if (KeyguardUtils.isValidSimId(simId)) {
            return mTelephonyHnbName[simId];
        } else {
            return mTelephonyHnbName[PhoneConstants.GEMINI_SIM_1];
        }
    }

    /// M: CSG support
    public CharSequence getTelephonyCsgId(int simId) {
        /// M: Support GeminiPlus
        if (KeyguardUtils.isValidSimId(simId)) {
            return mTelephonyCsgId[simId];
        } else {
            return mTelephonyCsgId[PhoneConstants.GEMINI_SIM_1];
        }
    }

    /// M: Incoming Indicator for Keyguard Rotation 
    public void setQueryBaseTime() {
        mQueryBaseTime = java.lang.System.currentTimeMillis();
    }

    /// M: Incoming Indicator for Keyguard Rotation
    public long getQueryBaseTime() {
        return mQueryBaseTime;
    }

    private void handleDockStatusUpdate(int dockState) {
        for (int i = 0; i < mCallbacks.size(); i++) {
           KeyguardUpdateMonitorCallback cb = mCallbacks.get(i).get();
           if (cb != null) {
               cb.onDockStatusUpdate(dockState);
           }
        }
    }

    ///M: get is dock status
    public boolean isDocktoDesk() {
        return mDocktoDesk;
    }
    
}
