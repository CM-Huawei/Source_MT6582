/*
 * Copyright (C) 2006 The Android Open Source Project
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

package com.android.phone;

import android.app.Activity;
import android.app.KeyguardManager;
import android.app.PendingIntent;
import android.app.ProgressDialog;
import android.app.TaskStackBuilder;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.IBluetoothHeadsetPhone;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.res.Configuration;
import android.media.AudioManager;
import android.net.Uri;
import android.os.AsyncResult;
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.IPowerManager;
import android.os.Message;
import android.os.PowerManager;
import android.os.RemoteException;
import android.os.SystemService;
import android.os.ServiceManager;
import android.os.SystemClock;
import android.os.SystemProperties;
import android.os.UpdateLock;
import android.os.UserHandle;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.provider.CallLog.Calls;
import android.provider.Settings.System;
import android.provider.Settings.SettingNotFoundException;
import android.telephony.ServiceState;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.Slog;
import android.view.KeyEvent;
import android.view.WindowManager;
import android.app.ActivityManagerNative;
import android.app.IActivityManager;

import com.android.i18n.phonenumbers.PhoneNumberUtil;
import com.android.internal.telephony.Call;
import com.android.internal.telephony.CallManager;
import com.android.internal.telephony.Connection;
import com.android.internal.telephony.CallerInfo;
import com.android.internal.telephony.CallStateException;
import com.android.internal.telephony.IccCard;
import com.android.internal.telephony.IccCardConstants;
import com.android.internal.telephony.MmiCode;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.PhoneFactory;
import com.android.internal.telephony.TelephonyCapabilities;
import com.android.internal.telephony.TelephonyIntents;
import com.android.internal.telephony.cdma.TtyIntent;
import com.android.internal.telephony.gsm.GSMPhone;
import com.android.internal.telephony.gemini.*;
import com.android.phone.common.CallLogAsync;
import com.android.phone.CdmaPhoneCallState.PhoneCallState;
import com.android.phone.OtaUtils.CdmaOtaScreenState;
import com.android.phone.WiredHeadsetManager.WiredHeadsetListener;
import com.android.server.sip.SipService;
import com.android.services.telephony.common.AudioMode;
import com.mediatek.CellConnService.CellConnMgr;
import com.mediatek.calloption.SimAssociateHandler;
import com.mediatek.phone.DualTalkUtils;
import com.mediatek.phone.GeminiConstants;
import com.mediatek.phone.HyphonManager;
import com.mediatek.phone.PhoneLog;
import com.mediatek.phone.PhoneFeatureConstants.FeatureOption;
import com.mediatek.phone.SIMInfoWrapper;
import com.mediatek.phone.ext.ExtensionManager;
import com.mediatek.phone.gemini.GeminiUtils;
import com.mediatek.phone.PhoneInterfaceManagerEx;
import com.mediatek.phone.provider.CallHistoryDatabaseHelper;
import com.mediatek.phone.vt.VTCallUtils;
import com.mediatek.phone.vt.VTInCallScreenFlags;
import com.mediatek.phone.vt.VTManagerWrapper;
import com.mediatek.phone.wrapper.CallManagerWrapper;
import com.mediatek.phone.wrapper.PhoneWrapper;
import com.mediatek.telephony.SimInfoManager;
import com.mediatek.telephony.SimInfoManager.SimInfoRecord;
import com.mediatek.vt.VTManager;

import java.util.List;

/**
 * Global state for the telephony subsystem when running in the primary
 * phone process.
 */
public class PhoneGlobals extends ContextWrapper implements WiredHeadsetListener {
    /* package */ static final String LOG_TAG = "PhoneGlobals";

    /**
     * Phone app-wide debug level:
     *   0 - no debug logging
     *   1 - normal debug logging if ro.debuggable is set (which is true in
     *       "eng" and "userdebug" builds but not "user" builds)
     *   2 - ultra-verbose debug logging
     *
     * Most individual classes in the phone app have a local DBG constant,
     * typically set to
     *   (PhoneGlobals.DBG_LEVEL >= 1) && (SystemProperties.getInt("ro.debuggable", 0) == 1)
     * or else
     *   (PhoneGlobals.DBG_LEVEL >= 2)
     * depending on the desired verbosity.
     *
     * ***** DO NOT SUBMIT WITH DBG_LEVEL > 0 *************
     */
    /* package */ static final int DBG_LEVEL = 1; /// M: update level to 1, original 0

    /// M: for Debug @{
    /// original code:
    /**
    private static final boolean DBG =
        (PhoneGlobals.DBG_LEVEL >= 1) && (SystemProperties.getInt("ro.debuggable", 0) == 1);
    private static final boolean VDBG = (PhoneGlobals.DBG_LEVEL >= 2);
    */
    private static final boolean DBG = true ;
    private static final boolean VDBG = true;
    /// @}

    // Message codes; see mHandler below.
    // M: original code (no used, remove it):
    // private static final int EVENT_SIM_NETWORK_LOCKED = 3;
    private static final int EVENT_SIM_STATE_CHANGED = 8;
    private static final int EVENT_DATA_ROAMING_DISCONNECTED = 10;
    private static final int EVENT_DATA_ROAMING_OK = 11;
    private static final int EVENT_UNSOL_CDMA_INFO_RECORD = 12;
    private static final int EVENT_DOCK_STATE_CHANGED = 13;
    private static final int EVENT_TTY_PREFERRED_MODE_CHANGED = 14;
    private static final int EVENT_TTY_MODE_GET = 15;
    private static final int EVENT_TTY_MODE_SET = 16;
    private static final int EVENT_START_SIP_SERVICE = 17;

    // The MMI codes are also used by the InCallScreen.
    public static final int MMI_INITIATE = 51;
    public static final int MMI_COMPLETE = 52;
    public static final int MMI_CANCEL = 53;
    // Don't use message codes larger than 99 here; those are reserved for
    // the individual Activities of the Phone UI.

    /**
     * Allowable values for the wake lock code.
     *   SLEEP means the device can be put to sleep.
     *   PARTIAL means wake the processor, but we display can be kept off.
     *   FULL means wake both the processor and the display.
     */
    public enum WakeState {
        SLEEP,
        PARTIAL,
        FULL
    }

    /**
     * Intent Action used for hanging up the current call from Notification bar. This will
     * choose first ringing call, first active call, or first background call (typically in
     * HOLDING state).
     */
    public static final String ACTION_HANG_UP_ONGOING_CALL =
            "com.android.phone.ACTION_HANG_UP_ONGOING_CALL";

    /**
     * Intent Action used for making a phone call from Notification bar.
     * This is for missed call notifications.
     */
    public static final String ACTION_CALL_BACK_FROM_NOTIFICATION =
            "com.android.phone.ACTION_CALL_BACK_FROM_NOTIFICATION";

    /**
     * Intent Action used for sending a SMS from notification bar.
     * This is for missed call notifications.
     */
    public static final String ACTION_SEND_SMS_FROM_NOTIFICATION =
            "com.android.phone.ACTION_SEND_SMS_FROM_NOTIFICATION";

    private static PhoneGlobals sMe;

    //MTK-START  MTK added for Gemini Plus
    static final String[] PROPERTY_RIL_UICC_TYPE = {
        "gsm.ril.uicctype",
        "gsm.ril.uicctype.2",
        "gsm.ril.uicctype.3",
        "gsm.ril.uicctype.4",
    };

    private String[] PROPERTY_ICCID_SIM = {
        "ril.iccid.sim1",
        "ril.iccid.sim2",
        "ril.iccid.sim3",
        "ril.iccid.sim4",
    };
    //MTK-END  MTK added for Gemini Plus

    // A few important fields we expose to the rest of the package
    // directly (rather than thru set/get methods) for efficiency.
    public CallController callController;
    public CallManager mCM;
    public CallNotifier notifier;
    public CallerInfoCache callerInfoCache;
    public NotificationMgr notificationMgr;
    public Phone phone;
    public PhoneInterfaceManager phoneMgr;
    /// M: Gemini+.@{
    public PhoneInterfaceManagerEx phoneMgrEx;
    /// @}

    private AudioRouter audioRouter;
    private BluetoothManager bluetoothManager;
    private CallCommandService callCommandService;
    private CallGatewayManager callGatewayManager;
    private CallHandlerServiceProxy callHandlerServiceProxy;
    private CallModeler callModeler;
    private CallStateMonitor callStateMonitor;
    private DTMFTonePlayer dtmfTonePlayer;
    private IBluetoothHeadsetPhone mBluetoothPhone;
    public Ringer ringer;
    private WiredHeadsetManager wiredHeadsetManager;

    static int mDockState = Intent.EXTRA_DOCK_STATE_UNDOCKED;
    static boolean sVoiceCapable = true;

    // Internal PhoneGlobals Call state tracker
    public CdmaPhoneCallState cdmaPhoneCallState;

    // The currently-active PUK entry activity and progress dialog.
    // Normally, these are the Emergency Dialer and the subsequent
    // progress dialog.  null if there is are no such objects in
    // the foreground.
    private Activity mPUKEntryActivity;
    private ProgressDialog mPUKEntryProgressDialog;

    private boolean mIsSimPinEnabled;
    private String mCachedSimPin;

    // True if we are beginning a call, but the phone state has not changed yet
    private boolean mBeginningCall;

    // Last phone state seen by updatePhoneState()
    private PhoneConstants.State mLastPhoneState = PhoneConstants.State.IDLE;

    private WakeState mWakeState = WakeState.SLEEP;

    private PowerManager mPowerManager;
    private IPowerManager mPowerManagerService;
    private PowerManager.WakeLock mWakeLock;
    private PowerManager.WakeLock mPartialWakeLock;
    private KeyguardManager mKeyguardManager;

    private UpdateLock mUpdateLock;

    // Broadcast receiver for various intent broadcasts (see onCreate())
    private final BroadcastReceiver mReceiver = new PhoneGlobalsBroadcastReceiver();

    // Broadcast receiver purely for ACTION_MEDIA_BUTTON broadcasts
    private final BroadcastReceiver mMediaButtonReceiver = new MediaButtonBroadcastReceiver();

    /** boolean indicating restoring mute state on InCallScreen.onResume() */
    private boolean mShouldRestoreMuteOnInCallResume;

    /**
     * The singleton OtaUtils instance used for OTASP calls.
     *
     * The OtaUtils instance is created lazily the first time we need to
     * make an OTASP call, regardless of whether it's an interactive or
     * non-interactive OTASP call.
     */
    public OtaUtils otaUtils;

    // Following are the CDMA OTA information Objects used during OTA Call.
    // cdmaOtaProvisionData object store static OTA information that needs
    // to be maintained even during Slider open/close scenarios.
    // cdmaOtaConfigData object stores configuration info to control visiblity
    // of each OTA Screens.
    // cdmaOtaScreenState object store OTA Screen State information.
    public OtaUtils.CdmaOtaProvisionData cdmaOtaProvisionData;
    public OtaUtils.CdmaOtaConfigData cdmaOtaConfigData;
    public OtaUtils.CdmaOtaScreenState cdmaOtaScreenState;
    public OtaUtils.CdmaOtaInCallScreenUiState cdmaOtaInCallScreenUiState;

    // TTY feature enabled on this platform
    private boolean mTtyEnabled;
    // Current TTY operating mode selected by user
    private int mPreferredTtyMode = Phone.TTY_MODE_OFF;

    /**
     * Set the restore mute state flag. Used when we are setting the mute state
     * OUTSIDE of user interaction {@link PhoneUtils#startNewCall(Phone)}
     */
    /*package*/void setRestoreMuteOnInCallResume (boolean mode) {
        PhoneLog.d(LOG_TAG, "setRestoreMuteOnInCallResume, mode = " + mode);
        mShouldRestoreMuteOnInCallResume = mode;
    }

    Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            if (handleMessageMtk(msg)) {
                return;
            }

            PhoneConstants.State phoneState;
            switch (msg.what) {
                // Starts the SIP service. It's a no-op if SIP API is not supported
                // on the deivce.
                // TODO: Having the phone process host the SIP service is only
                // temporary. Will move it to a persistent communication process
                // later.
                case EVENT_START_SIP_SERVICE:
                    SipService.start(getApplicationContext());
                    break;

                /// M: @{
                // original code:
                /*
                // TODO: This event should be handled by the lock screen, just
                // like the "SIM missing" and "Sim locked" cases (bug 1804111).
                case EVENT_SIM_NETWORK_LOCKED:
                    if (getResources().getBoolean(R.bool.ignore_sim_network_locked_events)) {
                        // Some products don't have the concept of a "SIM network lock"
                        Log.i(LOG_TAG, "Ignoring EVENT_SIM_NETWORK_LOCKED event; "
                              + "not showing 'SIM network unlock' PIN entry screen");
                    } else {
                        // Normal case: show the "SIM network unlock" PIN entry screen.
                        // The user won't be able to do anything else until
                        // they enter a valid SIM network PIN.
                        Log.i(LOG_TAG, "show sim depersonal panel");
                        IccNetworkDepersonalizationPanel ndpPanel =
                                new IccNetworkDepersonalizationPanel(PhoneGlobals.getInstance());
                        ndpPanel.show();
                    }
                    break;
                */
                /// @}

                case EVENT_DATA_ROAMING_DISCONNECTED:
                    /// M: @{
                    // original code:
                    // notificationMgr.showDataDisconnectedRoaming();
                    notificationMgr.showDataDisconnectedRoaming(msg.arg1);
                    /// @}
                    break;

                case EVENT_DATA_ROAMING_OK:
                    notificationMgr.hideDataDisconnectedRoaming();
                    break;

                /// M: For GEMINI+ move to handleMessageMtk()@{
                // add more message for GEMINI+.
                // original code:
                /*
                case MMI_COMPLETE:
                    onMMIComplete((AsyncResult) msg.obj);
                    break;

                case MMI_CANCEL:
                    PhoneUtils.cancelMmiCode(phone);
                    break;
                */
                /// @}

                case EVENT_SIM_STATE_CHANGED:
                    // Marks the event where the SIM goes into ready state.
                    // Right now, this is only used for the PUK-unlocking
                    // process.
                    if (msg.obj.equals(IccCardConstants.INTENT_VALUE_ICC_READY)) {
                        // when the right event is triggered and there
                        // are UI objects in the foreground, we close
                        // them to display the lock panel.
                        if (mPUKEntryActivity != null) {
                            mPUKEntryActivity.finish();
                            mPUKEntryActivity = null;
                        }
                        if (mPUKEntryProgressDialog != null) {
                            mPUKEntryProgressDialog.dismiss();
                            mPUKEntryProgressDialog = null;
                        }
                    }
                    break;

                case EVENT_UNSOL_CDMA_INFO_RECORD:
                    //TODO: handle message here;
                    break;

                case EVENT_DOCK_STATE_CHANGED:
                    // If the phone is docked/undocked during a call, and no wired or BT headset
                    // is connected: turn on/off the speaker accordingly.
                    boolean inDockMode = false;
                    if (mDockState != Intent.EXTRA_DOCK_STATE_UNDOCKED) {
                        inDockMode = true;
                    }
                    if (VDBG) Log.d(LOG_TAG, "received EVENT_DOCK_STATE_CHANGED. Phone inDock = "
                            + inDockMode);

                    phoneState = mCM.getState();
                    if (phoneState == PhoneConstants.State.OFFHOOK &&
                            !wiredHeadsetManager.isHeadsetPlugged() &&
                            !bluetoothManager.isBluetoothHeadsetAudioOn()) {
                        audioRouter.setSpeaker(inDockMode);

                        PhoneUtils.turnOnSpeaker(getApplicationContext(), inDockMode, true);
                    }
                    break;

                case EVENT_TTY_PREFERRED_MODE_CHANGED:
                    // TTY mode is only applied if a headset is connected
                    int ttyMode;
                    if (wiredHeadsetManager.isHeadsetPlugged()) {
                        ttyMode = mPreferredTtyMode;
                    } else {
                        ttyMode = Phone.TTY_MODE_OFF;
                    }
                    /// M: for GEMINI+ @{
                    // original code:
                    // phone.setTTYMode(ttyMode, mHandler.obtainMessage(EVENT_TTY_MODE_SET));
                    PhoneWrapper.setTTYMode(phone, convertTTYmodeToRadio(ttyMode), mHandler, EVENT_TTY_MODE_SET);
                    /// @}
                    break;

                case EVENT_TTY_MODE_GET:
                    handleQueryTTYModeResponse(msg);
                    break;

                case EVENT_TTY_MODE_SET:
                    handleSetTTYModeResponse(msg);
                    break;
            }
        }
    };

    public PhoneGlobals(Context context) {
        super(context);
        sMe = this;
    }

    public void onCreate() {
        if (VDBG) Log.v(LOG_TAG, "onCreate()...");

        /// M: gsm.phone.created & Settings.System.SIM_LOCK_STATE_SETTING
        updateSimLockState();

        ContentResolver resolver = getContentResolver();

        // Cache the "voice capable" flag.
        // This flag currently comes from a resource (which is
        // overrideable on a per-product basis):
        sVoiceCapable =
                getResources().getBoolean(com.android.internal.R.bool.config_voice_capable);
        // ...but this might eventually become a PackageManager "system
        // feature" instead, in which case we'd do something like:
        // sVoiceCapable =
        //   getPackageManager().hasSystemFeature(PackageManager.FEATURE_TELEPHONY_VOICE_CALLS);

        if (phone == null) {
            // Initialize the telephony framework
            PhoneFactory.makeDefaultPhones(this);
            PhoneLog.v(LOG_TAG, "onCreate(), make default phone complete");
            // Get the default phone
            phone = PhoneFactory.getDefaultPhone();

            // Start TelephonyDebugService After the default phone is created.
            Intent intent = new Intent(this, TelephonyDebugService.class);
            startService(intent);

            /// M: @{
            // Original code:
            // mCM = CallManager.getInstance();
            // mCM.registerPhone(phone);
            registerPhone();
            /// @}

            // Create the NotificationMgr singleton, which is used to display
            // status bar icons and control other status bar behavior.
            notificationMgr = NotificationMgr.init(this);

            mHandler.sendEmptyMessage(EVENT_START_SIP_SERVICE);

            int phoneType = phone.getPhoneType();

            if (phoneType == PhoneConstants.PHONE_TYPE_CDMA) {
                // Create an instance of CdmaPhoneCallState and initialize it to IDLE
                cdmaPhoneCallState = new CdmaPhoneCallState();
                cdmaPhoneCallState.CdmaPhoneCallStateInit();
            }

            PhoneLog.v(LOG_TAG, "onCreate(), start to get BT default adapter");
            if (BluetoothAdapter.getDefaultAdapter() != null) {
                // Start BluetoothPhoneService even if device is not voice capable.
                // The device can still support VOIP.
                startService(new Intent(this, BluetoothPhoneService.class));
                bindService(new Intent(this, BluetoothPhoneService.class),
                            mBluetoothPhoneConnection, 0);
            } else {
                // Device is not bluetooth capable
                mBluetoothPhone = null;
            }

            // before registering for phone state changes
            mPowerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
            /// M: ALPS00808867 @{
            // For fixing LockScreen flash issue
            // original code:
            // mWakeLock = mPowerManager.newWakeLock(PowerManager.FULL_WAKE_LOCK, LOG_TAG);
            mWakeLock = mPowerManager.newWakeLock(PowerManager.FULL_WAKE_LOCK
                    | PowerManager.ON_AFTER_RELEASE, LOG_TAG);
            mWakeLockForSecondRingCall = mPowerManager.newWakeLock(PowerManager.FULL_WAKE_LOCK
                    | PowerManager.ACQUIRE_CAUSES_WAKEUP | PowerManager.ON_AFTER_RELEASE, LOG_TAG);
            mWakeLockForDisconnect = mPowerManager.newWakeLock(PowerManager.FULL_WAKE_LOCK
                    | PowerManager.ON_AFTER_RELEASE, LOG_TAG);
            /// @}

            // lock used to keep the processor awake, when we don't care for the display.
            mPartialWakeLock = mPowerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK
                    | PowerManager.ON_AFTER_RELEASE, LOG_TAG);

            mKeyguardManager = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);

            // get a handle to the service so that we can use it later when we
            // want to set the poke lock.
            mPowerManagerService = IPowerManager.Stub.asInterface(
                    ServiceManager.getService("power"));

            // Get UpdateLock to suppress system-update related events (e.g. dialog show-up)
            // during phone calls.
            mUpdateLock = new UpdateLock("phone");

            if (DBG) Log.d(LOG_TAG, "onCreate: mUpdateLock: " + mUpdateLock);

            CallLogger callLogger = new CallLogger(this, new CallLogAsync());

            /// M: for ALPS01266495 @{
            // handle add call log case in PhoneGlobals
            mCallLogger = callLogger;
            /// @}

            callGatewayManager = new CallGatewayManager();

            // Create the CallController singleton, which is the interface
            // to the telephony layer for user-initiated telephony functionality
            // (like making outgoing calls.)
            callController = CallController.init(this, callLogger, callGatewayManager);

            // Create the CallerInfoCache singleton, which remembers custom ring tone and
            // send-to-voicemail settings.
            //
            // The asynchronous caching will start just after this call.
            callerInfoCache = CallerInfoCache.init(this);

            /// M: for ALPS01262892 @{
            // have to handle mute state in connectionHandler before
            // CallStateMonitor get phone state change

            // register connection tracking to PhoneUtils
            PhoneUtils.initializeConnectionHandler(mCM);
            /// @}

            // Monitors call activity from the telephony layer
            callStateMonitor = new CallStateMonitor(mCM);

            // Creates call models for use with CallHandlerService.
            callModeler = new CallModeler(callStateMonitor, mCM, callGatewayManager);

            // Plays DTMF Tones
            dtmfTonePlayer = new DTMFTonePlayer(mCM, callModeler);

            // Manages wired headset state
            wiredHeadsetManager = new WiredHeadsetManager(this);
            wiredHeadsetManager.addWiredHeadsetListener(this);

            // Bluetooth manager
            bluetoothManager = new BluetoothManager(this, mCM, callModeler);

            ringer = Ringer.init(this, bluetoothManager);

            // Audio router
            audioRouter = new AudioRouter(this, bluetoothManager, wiredHeadsetManager, mCM);

            // Service used by in-call UI to control calls
            callCommandService = new CallCommandService(this, mCM, callModeler, dtmfTonePlayer,
                    audioRouter);

            // Sends call state to the UI
            callHandlerServiceProxy = new CallHandlerServiceProxy(this, callModeler,
                    callCommandService, audioRouter);

            phoneMgr = PhoneInterfaceManager.init(this, phone, callHandlerServiceProxy);
            /// M: init PhoneInterfaceManagerEx
            phoneMgrEx = PhoneInterfaceManagerEx.init(this, phone);

            // Create the CallNotifer singleton, which handles
            // asynchronous events from the telephony layer (like
            // launching the incoming-call UI when an incoming call comes
            // in.)
            notifier = CallNotifier.init(this, phone, ringer, callLogger, callStateMonitor,
                    bluetoothManager, callModeler);

            /*
            // register for ICC status
            IccCard sim = phone.getIccCard();
            if (sim != null) {
                if (VDBG) Log.v(LOG_TAG, "register for ICC status");
                sim.registerForNetworkLocked(mHandler, EVENT_SIM_NETWORK_LOCKED, null);
            }
            */

            // register for MMI/USSD
            /// M: For GEMINI+ @{
            // TODO: Check CDMA?
            // Original code:
            // mCM.registerForMmiComplete(mHandler, MMI_COMPLETE, null);
            if (phoneType == PhoneConstants.PHONE_TYPE_GSM) {
                CallManagerWrapper.registerForMmiComplete(mHandler, MMI_COMPLETE_GEMINI);
                CallManagerWrapper.registerForMmiInitiate(mHandler, MMI_INITIATE_GEMINI);
            }
            /// @}

            /// M: For EVDO DualTalk @{
            if (FeatureOption.EVDO_DT_SUPPORT) {
                mCM.registerForMmiComplete(mHandler, MMI_INITIATE, null);
                mCM.registerForMmiComplete(mHandler, MMI_COMPLETE, null);
            }
            PhoneLog.v(LOG_TAG, "onCreate(), initialize connection handler");
            /// @}

            // Read platform settings for TTY feature
            /// M: For TTY Feature Option @{
            // original code:
            // mTtyEnabled = getResources().getBoolean(R.bool.tty_enabled);
            updateTTYEnabled();
            /// @}

            // Register for misc other intent broadcasts.
            IntentFilter intentFilter =
                    new IntentFilter(Intent.ACTION_AIRPLANE_MODE_CHANGED);
            intentFilter.addAction(TelephonyIntents.ACTION_ANY_DATA_CONNECTION_STATE_CHANGED);
            intentFilter.addAction(Intent.ACTION_DOCK_EVENT);
            intentFilter.addAction(TelephonyIntents.ACTION_SIM_STATE_CHANGED);
            intentFilter.addAction(TelephonyIntents.ACTION_RADIO_TECHNOLOGY_CHANGED);
            intentFilter.addAction(TelephonyIntents.ACTION_SERVICE_STATE_CHANGED);
            intentFilter.addAction(TelephonyIntents.ACTION_EMERGENCY_CALLBACK_MODE_CHANGED);
            if (mTtyEnabled) {
                intentFilter.addAction(TtyIntent.TTY_PREFERRED_MODE_CHANGE_ACTION);
            }
            intentFilter.addAction(AudioManager.RINGER_MODE_CHANGED_ACTION);
            /// M: add action for mtk feature
            addActionForGlobalReceiver(intentFilter);

            registerReceiver(mReceiver, intentFilter);

            // Use a separate receiver for ACTION_MEDIA_BUTTON broadcasts,
            // since we need to manually adjust its priority (to make sure
            // we get these intents *before* the media player.)
            IntentFilter mediaButtonIntentFilter =
                    new IntentFilter(Intent.ACTION_MEDIA_BUTTON);
            // TODO verify the independent priority doesn't need to be handled thanks to the
            //  private intent handler registration
            // Make sure we're higher priority than the media player's
            // MediaButtonIntentReceiver (which currently has the default
            // priority of zero; see apps/Music/AndroidManifest.xml.)
            mediaButtonIntentFilter.setPriority(1);
            //
            registerReceiver(mMediaButtonReceiver, mediaButtonIntentFilter);
            // register the component so it gets priority for calls
            AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
            am.registerMediaButtonEventReceiverForCalls(new ComponentName(this.getPackageName(),
                    MediaButtonBroadcastReceiver.class.getName()));

            //set the default values for the preferences in the phone.
            PreferenceManager.setDefaultValues(this, R.xml.network_setting, false);

            PreferenceManager.setDefaultValues(this, R.xml.call_feature_setting, false);

            // Make sure the audio mode (along with some
            // audio-mode-related state of our own) is initialized
            // correctly, given the current state of the phone.
            PhoneUtils.setAudioMode(mCM);
        }

        if (TelephonyCapabilities.supportsOtasp(phone)) {
            cdmaOtaProvisionData = new OtaUtils.CdmaOtaProvisionData();
            cdmaOtaConfigData = new OtaUtils.CdmaOtaConfigData();
            cdmaOtaScreenState = new OtaUtils.CdmaOtaScreenState();
            cdmaOtaInCallScreenUiState = new OtaUtils.CdmaOtaInCallScreenUiState();
        }

        // XXX pre-load the SimProvider so that it's ready
        resolver.getType(Uri.parse("content://icc/adn"));

        // start with the default value to set the mute state.
        mShouldRestoreMuteOnInCallResume = false;

        // TODO: Register for Cdma Information Records
        // phone.registerCdmaInformationRecord(mHandler, EVENT_UNSOL_CDMA_INFO_RECORD, null);

        // Read TTY settings and store it into BP NV.
        // AP owns (i.e. stores) the TTY setting in AP settings database and pushes the setting
        // to BP at power up (BP does not need to make the TTY setting persistent storage).
        // This way, there is a single owner (i.e AP) for the TTY setting in the phone.
        if (mTtyEnabled) {
            mPreferredTtyMode = android.provider.Settings.Secure.getInt(
                    phone.getContext().getContentResolver(),
                    android.provider.Settings.Secure.PREFERRED_TTY_MODE,
                    Phone.TTY_MODE_OFF);
            mHandler.sendMessage(mHandler.obtainMessage(EVENT_TTY_PREFERRED_MODE_CHANGED, 0));
        }
        // Read HAC settings and configure audio hardware
        if (getResources().getBoolean(R.bool.hac_enabled)) {
            int hac = android.provider.Settings.System.getInt(phone.getContext().getContentResolver(),
                                                              android.provider.Settings.System.HEARING_AID,
                                                              0);
            AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
            audioManager.setParameter(CallFeaturesSetting.HAC_KEY, hac != 0 ?
                                      CallFeaturesSetting.HAC_VAL_ON :
                                      CallFeaturesSetting.HAC_VAL_OFF);
        }

        /// M: initilizate vars for MTK features
        initForMtkFeatures();
        PhoneLog.d(LOG_TAG, "onCreate() end");
   }

    /**
     * Returns the singleton instance of the PhoneGlobals.
     */
    public static PhoneGlobals getInstance() {
        if (sMe == null) {
            throw new IllegalStateException("No PhoneGlobals here!");
        }
        return sMe;
    }

    /**
     * Returns the singleton instance of the PhoneGlobals if running as the
     * primary user, otherwise null.
     */
    static PhoneGlobals getInstanceIfPrimary() {
        return sMe;
    }

    /**
     * Returns the Phone associated with this instance
     */
    public static Phone getPhone() {
        return getInstance().phone;
    }

    Ringer getRinger() {
        return ringer;
    }

    IBluetoothHeadsetPhone getBluetoothPhoneService() {
        return mBluetoothPhone;
    }

    /* package */ BluetoothManager getBluetoothManager() {
        return bluetoothManager;
    }

    /* package */ WiredHeadsetManager getWiredHeadsetManager() {
        return wiredHeadsetManager;
    }

    /* package */ AudioRouter getAudioRouter() {
        return audioRouter;
    }

    public CallModeler getCallModeler() {
        return callModeler;
    }

    /// M: unused google code.@{
    /*
    CallManager getCallManager() {
        return mCM;
    }
    */
    /// @}

    /**
     * Returns an Intent that can be used to go to the "Call log"
     * UI (aka CallLogActivity) in the Contacts app.
     *
     * Watch out: there's no guarantee that the system has any activity to
     * handle this intent.  (In particular there may be no "Call log" at
     * all on on non-voice-capable devices.)
     */
    /* package */ static Intent createCallLogIntent() {
        Intent intent = new Intent(Intent.ACTION_VIEW, null);
        intent.setType("vnd.android.cursor.dir/calls");
        return intent;
    }

    /* package */static PendingIntent createPendingCallLogIntent(Context context) {
        final Intent callLogIntent = PhoneGlobals.createCallLogIntent();
        final TaskStackBuilder taskStackBuilder = TaskStackBuilder.create(context);
        taskStackBuilder.addNextIntent(callLogIntent);
        return taskStackBuilder.getPendingIntent(0, 0);
    }

    /**
     * Returns PendingIntent for hanging up ongoing phone call. This will typically be used from
     * Notification context.
     */
    /* package */ static PendingIntent createHangUpOngoingCallPendingIntent(Context context) {
        Intent intent = new Intent(PhoneGlobals.ACTION_HANG_UP_ONGOING_CALL, null,
                context, NotificationBroadcastReceiver.class);
        return PendingIntent.getBroadcast(context, 0, intent, 0);
    }

    /* package */ static PendingIntent getCallBackPendingIntent(Context context, String number) {
        Intent intent = new Intent(ACTION_CALL_BACK_FROM_NOTIFICATION,
                Uri.fromParts(Constants.SCHEME_TEL, number, null),
                context, NotificationBroadcastReceiver.class);
        return PendingIntent.getBroadcast(context, 0, intent, 0);
    }

    /* package */ static PendingIntent getSendSmsFromNotificationPendingIntent(
            Context context, String number) {
        Intent intent = new Intent(ACTION_SEND_SMS_FROM_NOTIFICATION,
                Uri.fromParts(Constants.SCHEME_SMSTO, number, null),
                context, NotificationBroadcastReceiver.class);
        return PendingIntent.getBroadcast(context, 0, intent, 0);
    }

    boolean isSimPinEnabled() {
        return mIsSimPinEnabled;
    }

    boolean authenticateAgainstCachedSimPin(String pin) {
        return (mCachedSimPin != null && mCachedSimPin.equals(pin));
    }

    void setCachedSimPin(String pin) {
        mCachedSimPin = pin;
    }

    /**
     * Handles OTASP-related events from the telephony layer.
     *
     * While an OTASP call is active, the CallNotifier forwards
     * OTASP-related telephony events to this method.
     */
    void handleOtaspEvent(Message msg) {
        if (DBG) Log.d(LOG_TAG, "handleOtaspEvent(message " + msg + ")...");

        if (otaUtils == null) {
            // We shouldn't be getting OTASP events without ever
            // having started the OTASP call in the first place!
            Log.w(LOG_TAG, "handleOtaEvents: got an event but otaUtils is null! "
                  + "message = " + msg);
            return;
        }

        otaUtils.onOtaProvisionStatusChanged((AsyncResult) msg.obj);
    }

    /**
     * Similarly, handle the disconnect event of an OTASP call
     * by forwarding it to the OtaUtils instance.
     */
    /* package */ void handleOtaspDisconnect() {
        if (DBG) Log.d(LOG_TAG, "handleOtaspDisconnect()...");

        if (otaUtils == null) {
            // We shouldn't be getting OTASP events without ever
            // having started the OTASP call in the first place!
            Log.w(LOG_TAG, "handleOtaspDisconnect: otaUtils is null!");
            return;
        }

        otaUtils.onOtaspDisconnect();
    }

    /**
     * Sets the activity responsible for un-PUK-blocking the device
     * so that we may close it when we receive a positive result.
     * mPUKEntryActivity is also used to indicate to the device that
     * we are trying to un-PUK-lock the phone. In other words, iff
     * it is NOT null, then we are trying to unlock and waiting for
     * the SIM to move to READY state.
     *
     * @param activity is the activity to close when PUK has
     * finished unlocking. Can be set to null to indicate the unlock
     * or SIM READYing process is over.
     */
    void setPukEntryActivity(Activity activity) {
        mPUKEntryActivity = activity;
    }

    Activity getPUKEntryActivity() {
        return mPUKEntryActivity;
    }

    /**
     * Sets the dialog responsible for notifying the user of un-PUK-
     * blocking - SIM READYing progress, so that we may dismiss it
     * when we receive a positive result.
     *
     * @param dialog indicates the progress dialog informing the user
     * of the state of the device.  Dismissed upon completion of
     * READYing process
     */
    void setPukEntryProgressDialog(ProgressDialog dialog) {
        mPUKEntryProgressDialog = dialog;
    }

    ProgressDialog getPUKEntryProgressDialog() {
        return mPUKEntryProgressDialog;
    }

    /**
     * Controls whether or not the screen is allowed to sleep.
     *
     * Once sleep is allowed (WakeState is SLEEP), it will rely on the
     * settings for the poke lock to determine when to timeout and let
     * the device sleep {@link PhoneGlobals#setScreenTimeout}.
     *
     * @param ws tells the device to how to wake.
     */
    public void requestWakeState(WakeState ws) {
        if (VDBG) Log.d(LOG_TAG, "requestWakeState(" + ws + ")...");
        synchronized (this) {
            if (mWakeState != ws) {
                switch (ws) {
                    case PARTIAL:
                        // acquire the processor wake lock, and release the FULL
                        // lock if it is being held.
                        mPartialWakeLock.acquire();
                        if (mWakeLock.isHeld()) {
                            mWakeLock.release();
                        }
                        /// M: ALPS00808867 @{
                        if (mWakeLockForSecondRingCall.isHeld()) {
                            mWakeLockForSecondRingCall.release();
                        }
                        /// @}
                        break;
                    case FULL:
                        // acquire the full wake lock, and release the PARTIAL
                        // lock if it is being held.
                        /// M: ALPS00808867 @{
                        // original code:
                        // mWakeLock.acquire();
                        requestWakeStateFull();
                        /// @}
                        break;
                    case SLEEP:
                    default:
                        // release both the PARTIAL and FULL locks.
                        if (mWakeLock.isHeld()) {
                            mWakeLock.release();
                        }
                        if (mPartialWakeLock.isHeld()) {
                            mPartialWakeLock.release();
                        }
                        /// M: ALPS00808867 @{
                        if (mWakeLockForSecondRingCall.isHeld()) {
                            mWakeLockForSecondRingCall.release();
                        }
                        /// @}
                        break;
                }
                mWakeState = ws;
            }
        }
    }

    /**
     * If we are not currently keeping the screen on, then poke the power
     * manager to wake up the screen for the user activity timeout duration.
     */
    /* package */ void wakeUpScreen() {
        synchronized (this) {
            if (mWakeState == WakeState.SLEEP) {
                if (DBG) Log.d(LOG_TAG, "pulse screen lock");
                mPowerManager.wakeUp(SystemClock.uptimeMillis());
            }
        }
    }

    /**
     * Sets the wake state and screen timeout based on the current state
     * of the phone, and the current state of the in-call UI.
     *
     * This method is a "UI Policy" wrapper around
     * {@link PhoneGlobals#requestWakeState} and {@link PhoneGlobals#setScreenTimeout}.
     *
     * It's safe to call this method regardless of the state of the Phone
     * (e.g. whether or not it's idle), and regardless of the state of the
     * Phone UI (e.g. whether or not the InCallScreen is active.)
     */
    /* package */ void updateWakeState() {
        PhoneConstants.State state = mCM.getState();

        // True if the speakerphone is in use.  (If so, we *always* use
        // the default timeout.  Since the user is obviously not holding
        // the phone up to his/her face, we don't need to worry about
        // false touches, and thus don't need to turn the screen off so
        // aggressively.)
        // Note that we need to make a fresh call to this method any
        // time the speaker state changes.  (That happens in
        // PhoneUtils.turnOnSpeaker().)
        boolean isSpeakerInUse = (state == PhoneConstants.State.OFFHOOK) && PhoneUtils.isSpeakerOn(this);

        // TODO (bug 1440854): The screen timeout *might* also need to
        // depend on the bluetooth state, but this isn't as clear-cut as
        // the speaker state (since while using BT it's common for the
        // user to put the phone straight into a pocket, in which case the
        // timeout should probably still be short.)

        // Decide whether to force the screen on or not.
        //
        // Force the screen to be on if the phone is ringing or dialing,
        // or if we're displaying the "Call ended" UI for a connection in
        // the "disconnected" state.
        // However, if the phone is disconnected while the user is in the
        // middle of selecting a quick response message, we should not force
        // the screen to be on.
        //
        boolean isRinging = (state == PhoneConstants.State.RINGING);
        boolean isDialing = GeminiUtils.isDialing(phone);
        boolean keepScreenOn = isRinging || isDialing;
        // keepScreenOn == true means we'll hold a full wake lock:
        requestWakeState(keepScreenOn ? WakeState.FULL : WakeState.SLEEP);
    }

    /**
     * Manually pokes the PowerManager's userActivity method.  Since we
     * set the {@link WindowManager.LayoutParams#INPUT_FEATURE_DISABLE_USER_ACTIVITY}
     * flag while the InCallScreen is active when there is no proximity sensor,
     * we need to do this for touch events that really do count as user activity
     * (like pressing any onscreen UI elements.)
     */
    /* package */ void pokeUserActivity() {
        if (VDBG) Log.d(LOG_TAG, "pokeUserActivity()...");
        mPowerManager.userActivity(SystemClock.uptimeMillis(), false);
    }

    /**
    /**
     * Notifies the phone app when the phone state changes.
     *
     * This method will updates various states inside Phone app (e.g. proximity sensor mode,
     * accelerometer listener state, update-lock state, etc.)
     */
    /* package */ void updatePhoneState(PhoneConstants.State state) {
        if (state != mLastPhoneState) {
            mLastPhoneState = state;
            /// M: @{
            PhoneLog.d(LOG_TAG, "updatePhoneState: state = " + state);
            if (state == PhoneConstants.State.IDLE) {
                PhoneGlobals.getInstance().pokeUserActivity();
            }
            /// @}

            // Try to acquire or release UpdateLock.
            //
            // Watch out: we don't release the lock here when the screen is still in foreground.
            // At that time InCallScreen will release it on onPause().
            if (state != PhoneConstants.State.IDLE) {
                // UpdateLock is a recursive lock, while we may get "acquire" request twice and
                // "release" request once for a single call (RINGING + OFFHOOK and IDLE).
                // We need to manually ensure the lock is just acquired once for each (and this
                // will prevent other possible buggy situations too).
                if (!mUpdateLock.isHeld()) {
                    mUpdateLock.acquire();
                }
            } else {
                if (mUpdateLock.isHeld()) {
                    mUpdateLock.release();
                }
            }
        }
    }

    /* package */ PhoneConstants.State getPhoneState() {
        return mLastPhoneState;
    }

    KeyguardManager getKeyguardManager() {
        return mKeyguardManager;
    }

    /// M: Add parameter slotId for GEMINI
    /*
     * original code:
    private void onMMIComplete(AsyncResult r) {
        if (VDBG) Log.d(LOG_TAG, "onMMIComplete()...");
        MmiCode mmiCode = (MmiCode) r.result;
        PhoneUtils.displayMMIComplete(phone, getInstance(), mmiCode, null, null);
    }
    */
    private void onMMIComplete(AsyncResult r, int slotId) {
        PhoneLog.d(LOG_TAG, "onMMIComplete(), slotId=" + slotId);
        MmiCode mmiCode = (MmiCode) r.result;
        if (null == mmiCode) {
            PhoneLog.w(LOG_TAG, "onMMIComplete(), mmiCode is null");
            return;
        }
        MmiCode.State state = mmiCode.getState();
        if (GeminiUtils.isGeminiSupport()) {
            if (state != MmiCode.State.PENDING) {
                Intent intent = new Intent();
                intent.setAction("com.android.phone.mmi");
                sendBroadcast(intent);
            }
        }
        Message message = Message.obtain(mHandler, PhoneGlobals.MMI_CANCEL);
        PhoneUtils.displayMMICompleteExt(phone, getApplicationContext(), mmiCode, message, null, slotId);
    }
    /// @}

    private void initForNewRadioTechnology() {
        if (DBG) Log.d(LOG_TAG, "initForNewRadioTechnology...");

         if (phone.getPhoneType() == PhoneConstants.PHONE_TYPE_CDMA) {
            // Create an instance of CdmaPhoneCallState and initialize it to IDLE
            cdmaPhoneCallState = new CdmaPhoneCallState();
            cdmaPhoneCallState.CdmaPhoneCallStateInit();
        }
        if (TelephonyCapabilities.supportsOtasp(phone)) {
            //create instances of CDMA OTA data classes
            if (cdmaOtaProvisionData == null) {
                cdmaOtaProvisionData = new OtaUtils.CdmaOtaProvisionData();
            }
            if (cdmaOtaConfigData == null) {
                cdmaOtaConfigData = new OtaUtils.CdmaOtaConfigData();
            }
            if (cdmaOtaScreenState == null) {
                cdmaOtaScreenState = new OtaUtils.CdmaOtaScreenState();
            }
            if (cdmaOtaInCallScreenUiState == null) {
                cdmaOtaInCallScreenUiState = new OtaUtils.CdmaOtaInCallScreenUiState();
            }
        } else {
            //Clean up OTA data in GSM/UMTS. It is valid only for CDMA
            clearOtaState();
        }

        ringer.updateRingerContextAfterRadioTechnologyChange(this.phone);
        notifier.updateCallNotifierRegistrationsAfterRadioTechnologyChange();
        callStateMonitor.updateAfterRadioTechnologyChange();

        if (mBluetoothPhone != null) {
            try {
                mBluetoothPhone.updateBtHandsfreeAfterRadioTechnologyChange();
            } catch (RemoteException e) {
                Log.e(LOG_TAG, Log.getStackTraceString(new Throwable()));
            }
        }

        /// M: @{
        // Delete original code:
        /*
        // Update registration for ICC status after radio technology change
        IccCard sim = phone.getIccCard();
        if (sim != null) {
            if (DBG) Log.d(LOG_TAG, "Update registration for ICC status...");

            //Register all events new to the new active phone
            sim.registerForNetworkLocked(mHandler, EVENT_SIM_NETWORK_LOCKED, null);
        }
        */
        /// @}
    }



    /**
     * This is called when the wired headset state changes.
     */
    @Override
    public void onWiredHeadsetConnection(boolean pluggedIn) {
        PhoneConstants.State phoneState = mCM.getState();

        // Force TTY state update according to new headset state
        if (mTtyEnabled) {
            mHandler.sendMessage(mHandler.obtainMessage(EVENT_TTY_PREFERRED_MODE_CHANGED, 0));
        }
    }

    /**
     * Receiver for misc intent broadcasts the Phone app cares about.
     */
    private class PhoneGlobalsBroadcastReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            /// M: @{
            if (ExtensionManager.getInstance().
                    getPhoneGlobalsBroadcastReceiverExtension().onReceive(context, intent)) {
                return;
            }
            if (onReceiveGlobalMtkActions(context, intent)) {
                return;
            }
            /// @}
            String action = intent.getAction();
            if (action.equals(Intent.ACTION_AIRPLANE_MODE_CHANGED)) {
                /// M: @{
                /* original code:
                boolean enabled = System.getInt(getContentResolver(),
                        System.AIRPLANE_MODE_ON, 0) == 0;
                phone.setRadioPower(enabled);
                */
                onReceiveAirplanModeChange(context, intent);
                /// @}
            } else if (action.equals(TelephonyIntents.ACTION_ANY_DATA_CONNECTION_STATE_CHANGED)) {
                if (VDBG) Log.d(LOG_TAG, "mReceiver: ACTION_ANY_DATA_CONNECTION_STATE_CHANGED");
                if (VDBG) Log.d(LOG_TAG, "- state: " + intent.getStringExtra(PhoneConstants.STATE_KEY));
                if (VDBG) Log.d(LOG_TAG, "- reason: "
                                + intent.getStringExtra(PhoneConstants.STATE_CHANGE_REASON_KEY));

                /// M: ALPS00555664 @{
                // Skip if the apn type is not APN_TYPE_DEFAULT
                final String apnType = intent.getStringExtra(PhoneConstants.DATA_APN_TYPE_KEY);
                if (!PhoneConstants.APN_TYPE_DEFAULT.equals(apnType)) {
                    return;
                }
                /// @}

                // The "data disconnected due to roaming" notification is shown
                // if (a) you have the "data roaming" feature turned off, and
                // (b) you just lost data connectivity because you're roaming.
                boolean disconnectedDueToRoaming =
                        !phone.getDataRoamingEnabled()
                        && "DISCONNECTED".equals(intent.getStringExtra(PhoneConstants.STATE_KEY))
                        && Phone.REASON_ROAMING_ON.equals(
                            intent.getStringExtra(PhoneConstants.STATE_CHANGE_REASON_KEY));
                mHandler.sendEmptyMessage(disconnectedDueToRoaming
                                          ? EVENT_DATA_ROAMING_DISCONNECTED
                                          : EVENT_DATA_ROAMING_OK);
            } else if (action.equals(TelephonyIntents.ACTION_SIM_STATE_CHANGED)) {
                // if an attempt to un-PUK-lock the device was made, while we're
                // receiving this state change notification, notify the handler.
                // NOTE: This is ONLY triggered if an attempt to un-PUK-lock has
                // been attempted.

                /// M: @{
                /* Original code:
                mHandler.sendMessage(mHandler.obtainMessage(EVENT_SIM_STATE_CHANGED,
                        intent.getStringExtra(IccCardConstants.INTENT_KEY_ICC_STATE)));
                */
                onReceiveSimStateChagne(context, intent);
                /// @}
            } else if (action.equals(TelephonyIntents.ACTION_RADIO_TECHNOLOGY_CHANGED)) {
                String newPhone = intent.getStringExtra(PhoneConstants.PHONE_NAME_KEY);
                Log.d(LOG_TAG, "Radio technology switched. Now " + newPhone + " is active.");
                initForNewRadioTechnology();
            } else if (action.equals(TelephonyIntents.ACTION_SERVICE_STATE_CHANGED)) {
                handleServiceStateChanged(intent);
            } else if (action.equals(TelephonyIntents.ACTION_EMERGENCY_CALLBACK_MODE_CHANGED)) {
                if (TelephonyCapabilities.supportsEcm(phone)) {
                    Log.d(LOG_TAG, "Emergency Callback Mode arrived in PhoneGlobals.");
                    // Start Emergency Callback Mode service
                    if (intent.getBooleanExtra("phoneinECMState", false)) {
                        context.startService(new Intent(context,
                                EmergencyCallbackModeService.class));
                    }
                } else {
                    // It doesn't make sense to get ACTION_EMERGENCY_CALLBACK_MODE_CHANGED
                    // on a device that doesn't support ECM in the first place.
                    Log.e(LOG_TAG, "Got ACTION_EMERGENCY_CALLBACK_MODE_CHANGED, "
                          + "but ECM isn't supported for phone: " + phone.getPhoneName());
                }
            } else if (action.equals(Intent.ACTION_DOCK_EVENT)) {
                mDockState = intent.getIntExtra(Intent.EXTRA_DOCK_STATE,
                        Intent.EXTRA_DOCK_STATE_UNDOCKED);
                if (VDBG) Log.d(LOG_TAG, "ACTION_DOCK_EVENT -> mDockState = " + mDockState);
                mHandler.sendMessage(mHandler.obtainMessage(EVENT_DOCK_STATE_CHANGED, 0));
            } else if (action.equals(TtyIntent.TTY_PREFERRED_MODE_CHANGE_ACTION)) {
                mPreferredTtyMode = intent.getIntExtra(TtyIntent.TTY_PREFFERED_MODE,
                                                       Phone.TTY_MODE_OFF);
                if (VDBG) Log.d(LOG_TAG, "mReceiver: TTY_PREFERRED_MODE_CHANGE_ACTION");
                if (VDBG) Log.d(LOG_TAG, "    mode: " + mPreferredTtyMode);
                mHandler.sendMessage(mHandler.obtainMessage(EVENT_TTY_PREFERRED_MODE_CHANGED, 0));
            } else if (action.equals(AudioManager.RINGER_MODE_CHANGED_ACTION)) {
                int ringerMode = intent.getIntExtra(AudioManager.EXTRA_RINGER_MODE,
                        AudioManager.RINGER_MODE_NORMAL);
                if (ringerMode == AudioManager.RINGER_MODE_SILENT) {
                    notifier.silenceRinger();
                }
            }
        }
    }

    /**
     * Broadcast receiver for the ACTION_MEDIA_BUTTON broadcast intent.
     *
     * This functionality isn't lumped in with the other intents in
     * PhoneGlobalsBroadcastReceiver because we instantiate this as a totally
     * separate BroadcastReceiver instance, since we need to manually
     * adjust its IntentFilter's priority (to make sure we get these
     * intents *before* the media player.)
     */
    private class MediaButtonBroadcastReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            KeyEvent event = (KeyEvent) intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT);
            if (VDBG) Log.d(LOG_TAG,
                           "MediaButtonBroadcastReceiver.onReceive()...  event = " + event);
            if ((event != null)
                && (event.getKeyCode() == KeyEvent.KEYCODE_HEADSETHOOK)
                && (event.getAction() == KeyEvent.ACTION_UP)) { /// M: Add ACTION_UP
                if (VDBG) Log.d(LOG_TAG, "MediaButtonBroadcastReceiver: HEADSETHOOK");
                /// M: Hold/Unhold call, Switch calls @{
                // Original Code:
                // boolean consumed = PhoneUtils.handleHeadsetHook(phone, event);
                if (event.getRepeatCount() == 0) { // Mute ONLY on the initial keypress.
                    boolean consumed = handleHeadsetHookKey();
                    /// @}
                    if (VDBG) Log.d(LOG_TAG, "==> handleHeadsetHook(): consumed = " + consumed);
                    if (consumed) {
                        abortBroadcast();
                    }
                } else {
                    if (mCM.getState() != PhoneConstants.State.IDLE) {
                        // If the phone is anything other than completely idle,
                        // then we consume and ignore any media key events,
                        // Otherwise it is too easy to accidentally start
                        // playing music while a phone call is in progress.
                        if (VDBG) {
                            Log.d(LOG_TAG, "MediaButtonBroadcastReceiver: consumed");
                        }
                        abortBroadcast();
                    }
                }
            }
        }
    }

    /**
     * Accepts broadcast Intents which will be prepared by {@link NotificationMgr} and thus
     * sent from framework's notification mechanism (which is outside Phone context).
     * This should be visible from outside, but shouldn't be in "exported" state.
     *
     * TODO: If possible merge this into PhoneGlobalsBroadcastReceiver.
     */
    public static class NotificationBroadcastReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            // TODO: use "if (VDBG)" here.
            Log.d(LOG_TAG, "Broadcast from Notification: " + action);

            if (action.equals(ACTION_HANG_UP_ONGOING_CALL)) {
                /// M: for ALPS00811599 @{
                // change feature, release pending call
                //
                //Origin code:
                //PhoneUtils.hangup(PhoneGlobals.getInstance().mCM);
                PhoneUtils.hangupEx(PhoneGlobals.getInstance().mCM);
                /// @}
            } else if (action.equals(ACTION_CALL_BACK_FROM_NOTIFICATION)) {
                // Collapse the expanded notification and the notification item itself.
                closeSystemDialogs(context);
                clearMissedCallNotification(context);

                /// M: for ALPS00771155 @{
                // Dismiss keyguard before start another activity, or user will be unable to act
                dismissKeyguard(context);
                /// @}

                Intent callIntent = new Intent(Intent.ACTION_CALL_PRIVILEGED, intent.getData());
                callIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
                context.startActivity(callIntent);
            } else if (action.equals(ACTION_SEND_SMS_FROM_NOTIFICATION)) {
                // Collapse the expanded notification and the notification item itself.
                closeSystemDialogs(context);
                clearMissedCallNotification(context);

                /// M: ALPS00771155 @{
                // Dismiss keyguard before start another activity, or user will be unable to act
                dismissKeyguard(context);
                /// @}

                Intent smsIntent = new Intent(Intent.ACTION_SENDTO, intent.getData());
                smsIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                /// M: ALPS00383231 @{
                // Add exception catch, for the case when SMS is disabled by user.
                // Original Code:
                // context.startActivity(smsIntent);
                try {
                    context.startActivity(smsIntent);
                } catch (ActivityNotFoundException e) {
                    PhoneLog.e(LOG_TAG, "start sms activity fail, sms is not available");
                }
                /// @}
            } else {
                Log.w(LOG_TAG, "Received hang-up request from notification,"
                        + " but there's no call the system can hang up.");
            }
        }

        private void closeSystemDialogs(Context context) {
            Intent intent = new Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS);
            context.sendBroadcastAsUser(intent, UserHandle.ALL);
        }

        private void clearMissedCallNotification(Context context) {
            Intent clearIntent = new Intent(context, ClearMissedCallsService.class);
            clearIntent.setAction(ClearMissedCallsService.ACTION_CLEAR_MISSED_CALLS);
            context.startService(clearIntent);
        }
    }

    private void handleServiceStateChanged(Intent intent) {
        /**
         * This used to handle updating EriTextWidgetProvider this routine
         * and and listening for ACTION_SERVICE_STATE_CHANGED intents could
         * be removed. But leaving just in case it might be needed in the near
         * future.
         */

        // If service just returned, start sending out the queued messages
        ServiceState ss = ServiceState.newFromBundle(intent.getExtras());

        if (ss != null) {
            int state = ss.getState();
            notificationMgr.updateNetworkSelection(state, ss.getMySimId()); /// M: Expand for GEMINI
        }
    }

    public boolean isOtaCallInActiveState() {
        boolean otaCallActive = false;
        if (VDBG) Log.d(LOG_TAG, "- isOtaCallInActiveState " + otaCallActive);
        return otaCallActive;
    }

    public boolean isOtaCallInEndState() {
        boolean otaCallEnded = false;
        if (VDBG) Log.d(LOG_TAG, "- isOtaCallInEndState " + otaCallEnded);
        return otaCallEnded;
    }

    // it is safe to call clearOtaState() even if the InCallScreen isn't active
    public void clearOtaState() {
        if (DBG) Log.d(LOG_TAG, "- clearOtaState ...");
        if (otaUtils != null) {
            otaUtils.cleanOtaScreen(true);
            if (DBG) Log.d(LOG_TAG, "  - clearOtaState clears OTA screen");
        }
    }

    // it is safe to call dismissOtaDialogs() even if the InCallScreen isn't active
    public void dismissOtaDialogs() {
        if (DBG) Log.d(LOG_TAG, "- dismissOtaDialogs ...");
        if (otaUtils != null) {
            otaUtils.dismissAllOtaDialogs();
            if (DBG) Log.d(LOG_TAG, "  - dismissOtaDialogs clears OTA dialogs");
        }
    }

    private void handleQueryTTYModeResponse(Message msg) {
        AsyncResult ar = (AsyncResult) msg.obj;
        if (ar.exception != null) {
            if (DBG) Log.d(LOG_TAG, "handleQueryTTYModeResponse: Error getting TTY state.");
        } else {
            if (DBG) Log.d(LOG_TAG,
                           "handleQueryTTYModeResponse: TTY enable state successfully queried.");
            /// M: TTY mode @{
            // Get the TTY mode from Settings directly
            // Original Code:
            // int ttymode = ((int[]) ar.result)[0];
            int ttymode = Phone.TTY_MODE_OFF;
            if (wiredHeadsetManager.isHeadsetPlugged()) {
                ttymode = mPreferredTtyMode;
            }
            /// @}
            if (DBG) Log.d(LOG_TAG, "handleQueryTTYModeResponse:ttymode=" + ttymode);

            Intent ttyModeChanged = new Intent(TtyIntent.TTY_ENABLED_CHANGE_ACTION);
            ttyModeChanged.putExtra("ttyEnabled", ttymode != Phone.TTY_MODE_OFF);
            sendBroadcastAsUser(ttyModeChanged, UserHandle.ALL);

            String audioTtyMode;
            switch (ttymode) {
            case Phone.TTY_MODE_FULL:
                audioTtyMode = "tty_full";
                break;
            case Phone.TTY_MODE_VCO:
                audioTtyMode = "tty_vco";
                break;
            case Phone.TTY_MODE_HCO:
                audioTtyMode = "tty_hco";
                break;
            case Phone.TTY_MODE_OFF:
            default:
                audioTtyMode = "tty_off";
                break;
            }
            AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
            audioManager.setParameters("tty_mode="+audioTtyMode);

            /// M: TTY mode
            PhoneUtils.setTtyMode(audioTtyMode);
        }
    }

    private void handleSetTTYModeResponse(Message msg) {
        AsyncResult ar = (AsyncResult) msg.obj;

        if (ar.exception != null) {
            if (DBG) Log.d (LOG_TAG,
                    "handleSetTTYModeResponse: Error setting TTY mode, ar.exception"
                    + ar.exception);
        }

        /// M: TTY mode @{
        // Now Phone doesn't support ttymode query, so we make a fake response
        // to trigger the set to audio.
        // Original Code:
        // phone.queryTTYMode(mHandler.obtainMessage(EVENT_TTY_MODE_GET));
        Message m = mHandler.obtainMessage(EVENT_TTY_MODE_GET);
        m.obj = new AsyncResult(null, null, null);
        m.sendToTarget();
        /// @}
    }

    /**
     * "Call origin" may be used by Contacts app to specify where the phone call comes from.
     * Currently, the only permitted value for this extra is {@link #ALLOWED_EXTRA_CALL_ORIGIN}.
     * Any other value will be ignored, to make sure that malicious apps can't trick the in-call
     * UI into launching some random other app after a call ends.
     *
     * TODO: make this more generic. Note that we should let the "origin" specify its package
     * while we are now assuming it is "com.android.contacts"
     */
    public static final String EXTRA_CALL_ORIGIN = "com.android.phone.CALL_ORIGIN";
    private static final String DEFAULT_CALL_ORIGIN_PACKAGE = "com.android.dialer";
    private static final String ALLOWED_EXTRA_CALL_ORIGIN =
            "com.android.dialer.DialtactsActivity";
    /**
     * Used to determine if the preserved call origin is fresh enough.
     */
    private static final long CALL_ORIGIN_EXPIRATION_MILLIS = 30 * 1000;


    /** Service connection */
    private final ServiceConnection mBluetoothPhoneConnection = new ServiceConnection() {

        /** Handle the task of binding the local object to the service */
        public void onServiceConnected(ComponentName className, IBinder service) {
            Log.i(LOG_TAG, "Headset phone created, binding local service.");
            mBluetoothPhone = IBluetoothHeadsetPhone.Stub.asInterface(service);
        }

        /** Handle the task of cleaning up the local binding */
        public void onServiceDisconnected(ComponentName className) {
            Log.i(LOG_TAG, "Headset phone disconnected, cleaning local binding.");
            mBluetoothPhone = null;
        }
    };

    /** --------------- MTK --------------------*/

    private static final int EVENT_TIMEOUT = 18;
    private static final int EVENT_TOUCH_ANSWER_VT = 30;

    public static final int EVENT_SHOW_INCALL_SCREEN_FOR_STK_SETUP_CALL = 57;
    public static final int DELAY_SHOW_INCALL_SCREEN_FOR_STK_SETUP_CALL = 160;

    private static final String PERMISSION = android.Manifest.permission.PROCESS_OUTGOING_CALLS;

    private static final String STKCALL_REGISTER_SPEECH_INFO = "com.android.stk.STKCALL_REGISTER_SPEECH_INFO";
    private static final String ACTION_SHUTDOWN_IPO = "android.intent.action.ACTION_SHUTDOWN_IPO";
    private static final String ACTION_PREBOOT_IPO = "android.intent.action.ACTION_PREBOOT_IPO";
    public static final String MISSEDCALL_DELETE_INTENT = "com.android.phone.MISSEDCALL_DELETE_INTENT";
    public static final String OLD_NETWORK_MODE = "com.android.phone.OLD_NETWORK_MODE";
    public static final String NETWORK_MODE_CHANGE = "com.android.phone.NETWORK_MODE_CHANGE";
    public static final String NETWORK_MODE_CHANGE_RESPONSE = "com.android.phone.NETWORK_MODE_CHANGE_RESPONSE";

    public static final int MESSAGE_SET_PREFERRED_NETWORK_TYPE = 10011;

    public static final boolean IS_VIDEO_CALL_SUPPORT = true;

    private static final String ACTION_MODEM_STATE = "com.mtk.ACTION_MODEM_STATE";
    private static final int CCCI_MD_BROADCAST_EXCEPTION = 1;
    private static final int CCCI_MD_BROADCAST_RESET = 2;
    private static final int CCCI_MD_BROADCAST_READY = 3;

    /**
     * ALPS00808867: If CallManager already has active call, the phone could not
     * light for incoming ring call. The WakeLock is used for light screen in
     * this case.
     */
    private PowerManager.WakeLock mWakeLockForSecondRingCall;
    private PowerManager.WakeLock mWakeLockForDisconnect;
    private int mWakelockSequence = 0;

    private void requestWakeStateFull() {
        if (mCM.hasActiveFgCall() || mCM.hasActiveBgCall()) {
            mWakeLockForSecondRingCall.acquire();
            if (mWakeLock.isHeld()) {
                mWakeLock.release();
            }
        } else {
            mWakeLock.acquire();
            if (mWakeLockForSecondRingCall.isHeld()) {
                mWakeLockForSecondRingCall.release();
            }
        }
    }

    // ECC button should be hidden when there is no service.
    private boolean mIsNoService[] = { true, true, true, true };

    AudioManager mAudioManager = null;

    public MTKCallManager mCMGemini = null;

    private static final int EVENT_SIM_NETWORK_LOCKED2 = 103;
    private static final int EVENT_SIM_NETWORK_LOCKED3 = 203;
    private static final int EVENT_SIM_NETWORK_LOCKED4 = 303;

    public static final int MMI_INITIATE2 = 151;
    public static final int MMI_COMPLETE2 = 152;
    public static final int MMI_CANCEL2 = 153;
    public static final int MMI_INITIATE3 = 251;
    public static final int MMI_COMPLETE3 = 252;
    public static final int MMI_CANCEL3 = 253;
    public static final int MMI_INITIATE4 = 351;
    public static final int MMI_COMPLETE4 = 352;
    public static final int MMI_CANCEL4 = 353;

    public static final int[] MMI_INITIATE_GEMINI = { MMI_INITIATE, MMI_INITIATE2, MMI_INITIATE3,
            MMI_INITIATE4 };
    public static final int[] MMI_COMPLETE_GEMINI = { MMI_COMPLETE, MMI_COMPLETE2, MMI_COMPLETE3,
            MMI_COMPLETE4 };
    public static final int[] MMI_CANCEL_GEMINI = { MMI_CANCEL, MMI_CANCEL2, MMI_CANCEL3,
            MMI_CANCEL4 };

    public CellConnMgr mCellConnMgr;

    private void registerPhone() {
        mCM = CallManager.getInstance();
        if (GeminiUtils.isGeminiSupport()) {
            mCMGemini = MTKCallManager.getInstance();
            mCMGemini.registerPhoneGemini(phone);
        } else {
            mCM.registerPhone(phone);
        }
    }

    /**
     * gsm.phone.created & Settings.System.SIM_LOCK_STATE_SETTING
     */
    private void updateSimLockState() {
        final String VOLD_DECRYPT = "vold.decrypt";
        final String GSM_PHONE_CREATED = "gsm.phone.created";
        final String TRIGGER_RESTART_FW = "trigger_restart_framework";

        final String state = SystemProperties.get(VOLD_DECRYPT);
        final boolean gsmPhoneCreated = SystemProperties.getBoolean(GSM_PHONE_CREATED, false);

        PhoneLog.d(LOG_TAG, "updateSimLockState: " + gsmPhoneCreated + ", " + state);
        if (!gsmPhoneCreated
                && (TextUtils.isEmpty(state) || TRIGGER_RESTART_FW.equals(state))) {
            PhoneLog.d(LOG_TAG, "updateSimLockState System Property: gsm.phone.created = true");
            SystemProperties.set(GSM_PHONE_CREATED, "true");
            Settings.System.putLong(getApplicationContext().getContentResolver(),
                    Settings.System.SIM_LOCK_STATE_SETTING, 0x0L);
        }
    }

    /**
     * This method is called at the end of onCreate()
     */
    private void initForMtkFeatures() {
        ExtensionManager.getInstance().getPhoneGlobalsExtension().onCreate(this, phone);

        VTManagerWrapper.getInstance().registerDefaultVTListener();
        // initilize SimAssociateHandler
        SimAssociateHandler.getInstance(this).prepair();
        SimAssociateHandler.getInstance(this).load();
        mCellConnMgr = new CellConnMgr();
        mCellConnMgr.register(getApplicationContext());

        // description : set the global flag that support dualtalk
        DualTalkUtils.init();

        // init SimInfoWrapper
        SIMInfoWrapper.getDefault().init(this);

        // init CallHistory
        CallHistoryDatabaseHelper.getInstance(this).initDatabase();

        // init PhoneNumberUtil
        PhoneNumberUtil.getInstance();

        // init HyphonManager for future use
        HyphonManager.getInstance().init(this);

        /// M: for ALPS01262692 @{
        // make default icon ready before answer the vt call.
        VTCallUtils.checkVTFileAsync();
        /// @}
    }

    /**
     * add action for MTK features
     *
     * @param intentFilter
     * @return
     */
    private IntentFilter addActionForGlobalReceiver(IntentFilter intentFilter) {
        intentFilter.addAction(Intent.ACTION_DUAL_SIM_MODE_CHANGED);
        intentFilter.addAction(Intent.ACTION_CONFIGURATION_CHANGED);
        intentFilter.addAction(Intent.ACTION_SHUTDOWN);
        intentFilter.addAction(STKCALL_REGISTER_SPEECH_INFO);
        intentFilter.addAction(MISSEDCALL_DELETE_INTENT);

        // Handle the network mode change for enhancement
        intentFilter.addAction(NETWORK_MODE_CHANGE);
        intentFilter.addAction(ACTION_SHUTDOWN_IPO);
        intentFilter.addAction(ACTION_PREBOOT_IPO);
        intentFilter.addAction(PhoneWrapper.EVENT_3G_SWITCH_START_MD_RESET);
        intentFilter.addAction(TelephonyIntents.ACTION_RADIO_OFF);
        intentFilter.addAction(ACTION_MODEM_STATE);

        return intentFilter;
    }

    private boolean onReceiveGlobalMtkActions(Context context, Intent intent) {
        final String action = intent.getAction();
        PhoneLog.d(LOG_TAG, "onReceiveMtkActions, action=" + action);
        if (action.equals(Intent.ACTION_DUAL_SIM_MODE_CHANGED)) {
            int mode = intent.getIntExtra(Intent.EXTRA_DUAL_SIM_MODE,
                    GeminiNetworkSubUtil.MODE_DUAL_SIM);
            PhoneWrapper.setRadioPower(phone, mode);
            return true;
        } else if (action.equals(Intent.ACTION_CONFIGURATION_CHANGED)) {
            phone.refreshSpnDisplay();
        } else if (action.equals(Intent.ACTION_SHUTDOWN)) {
            callStateMonitor.unregisterForNotifications();
            addCurrentCalltoCallLogs();
        } else if (action.equals(STKCALL_REGISTER_SPEECH_INFO)) {
            PhoneUtils.placeCallRegister(phone);
            mHandler.sendEmptyMessageDelayed(EVENT_SHOW_INCALL_SCREEN_FOR_STK_SETUP_CALL,
                    DELAY_SHOW_INCALL_SCREEN_FOR_STK_SETUP_CALL);
        } else if (action.equals(MISSEDCALL_DELETE_INTENT)) {
            notificationMgr.resetMissedCallNumber();
        } else if (action.equals(NETWORK_MODE_CHANGE)) {
            int modemNetworkMode = intent.getIntExtra(NETWORK_MODE_CHANGE, 0);
            int slotId = intent.getIntExtra(GeminiConstants.SLOT_ID_KEY, 0);
            int oldmode = intent.getIntExtra(OLD_NETWORK_MODE, -1);
            PhoneWrapper.setPreferredNetworkType(
                    phone, modemNetworkMode,
                    mHandler.obtainMessage(MESSAGE_SET_PREFERRED_NETWORK_TYPE, oldmode, modemNetworkMode), slotId);
        } else if (ACTION_SHUTDOWN_IPO.equals(action)) {
            //For Gemini Plus feature.
            for(int simIdx = PhoneConstants.GEMINI_SIM_1; simIdx < PhoneConstants.GEMINI_SIM_NUM; simIdx++){          
                SystemProperties.set(PROPERTY_RIL_UICC_TYPE[simIdx], "");
                SystemProperties.set(PROPERTY_ICCID_SIM[simIdx], null);
            }

            String bootReason = SystemProperties.get("sys.boot.reason");
            if ("1".equals(bootReason)) {
                PhoneLog.d(LOG_TAG, "Alarm boot shutdown and not turn off radio again");
            } else {
                phone.setRadioPower(false, true);
            }

            if (FeatureOption.MTK_VT3G324M_SUPPORT) {
                if (VTManager.State.CLOSE != VTManager.getInstance().getState()) {
                    PhoneLog.d(LOG_TAG, "call VTManager onDisconnected start");
                    VTManager.getInstance().onDisconnected();
                    PhoneLog.d(LOG_TAG, "call VTManager onDisconnected end");

                    PhoneLog.d(LOG_TAG, "call VTManager setVTClose start");
                    VTManager.getInstance().setVTClose();
                    PhoneLog.d(LOG_TAG, "call VTManager setVTClose end");

                    if (VTInCallScreenFlags.getInstance().mVTInControlRes) {
                        sendBroadcast(new Intent(VTCallUtils.VT_CALL_END));
                        VTInCallScreenFlags.getInstance().mVTInControlRes = false;
                    }
                }
            }

            /// M: @{
            // IPO shut down, cancel missed call notificaiton.
            if (notificationMgr != null) {
                PhoneLog.d(LOG_TAG, "IPO Shutdown: call cancelMissedCallNotification()");
                notificationMgr.cancelMissedCallNotification();
                notificationMgr.cancelCallInProgressNotifications();
            }
            /// @}

            /// M: for ALPS00935150 @{
            // IPO shut down, stop ringtone play.
            if (ringer != null && ringer.isRinging()) {
                PhoneLog.d(LOG_TAG, "IPO Shutdown: stop ringtone");
                ringer.stopRing();
            }
            /// @}
        } else if (ACTION_PREBOOT_IPO.equals(action)) {
            Settings.System.putLong(getApplicationContext().getContentResolver(),
                    Settings.System.SIM_LOCK_STATE_SETTING, 0x0L);
            /// M: Silent Reboot @{
            // Reset eboot flag because of no needs to do silent boot when IPO bootup
            SystemProperties.set("gsm.ril.eboot", "0");
            PhoneLog.d(LOG_TAG, "Set gsm.ril.eboot to 0");
            /// @}

            /// M: @{
            // Query missed call and show notification.
            // (LED will be shutdown when IPO shut down, re-send missed call
            // notification to notify LED useage.)
            if (notifier != null) {
                PhoneLog.d(LOG_TAG, "IPO Reboot: call cancelMissedCallNotification()");
                notifier.showMissedCallNotification(null, 0);
            }
            // ALPS00804975, IPO shut down when Call status is OFFHOOK,
            // phone may not receive disconnect message. In this case,
            // we need to call setAudioMode.
            PhoneUtils.setAudioMode(mCM);
            phone.setRadioPowerOn();
            /// @}
        } else if (action.equals(PhoneWrapper.EVENT_3G_SWITCH_START_MD_RESET)) {
            Settings.System.putLong(getApplicationContext().getContentResolver(),
                    Settings.System.SIM_LOCK_STATE_SETTING, 0x0L);
        } else if (action.equals(TelephonyIntents.ACTION_RADIO_OFF)) {
            int slot = intent.getIntExtra(TelephonyIntents.INTENT_KEY_ICC_SLOT, 0);
            PhoneLog.d(LOG_TAG, "ACTION_RADIO_OFF slot = " + slot);
            clearSimSettingFlag(slot);
        } else if (action.equals(ACTION_MODEM_STATE)) {
            SystemService.start("md_minilog_util");
        }

        PhoneLog.d(LOG_TAG, "onReceiveMtkActions, not mtk actions");
        return false;
    }

    private void onReceiveAirplanModeChange(Context context, Intent intent) {
        boolean enabled = intent.getBooleanExtra("state", false);
        PhoneLog.d(LOG_TAG, "onReceiveAirplanModeChange, AIRPLANEMODE enabled=" + enabled);
        if (enabled) {
            PhoneUtils.dismissMMIDialog();
        }

        /// M: ALPS00409547 @{
        // ECC call can not hang up after turn on airplan mode..
        try {
            if (enabled && (mCM.getState() != PhoneConstants.State.IDLE)) {
                PhoneLog.d(LOG_TAG, "Hangup all calls before turning on airplane mode");
                mCM.hangupAllEx();
            }
        } catch (CallStateException e) {
            PhoneLog.e(LOG_TAG, "onReceive mCM.hangupAllEx() exception.");
        }
        /// @}

        /// M: For VT @{
        // when turn on airplane while VT call is on.
        // if modem channel is closed before VTManager.setVTClose() is called
        // (channel is closed before we receive PHONE_VT_STATUS_INFOdisconnect
        // or PHONE_DISCONNECT msg.), muxd write vt data to closed channel, will raise NE.
        // This NE can't be catched for native can't judge whether the channel is closed normally or by other wrong case,
        // if by other wrong case, need raise NE to reset modem and other related.
        // so we do VTManager.setVTClose() here.
        // NOTE: this solution has a issue: between VTManager.setVTClose() and onDisconnect(),
        // there will be no vt data shown on screen while time is still move on.
        // NOTE2: we can't call VTManager.setVTClose() twice in a call, so need the flag here. just like VTCallUtils.answerCallPre().
        // see ALPS01257991.
        if (VTCallUtils.isVTActive()) {
            VTCallUtils.closeVTManager();
            VTInCallScreenFlags.getInstance().mVTShouldCloseVTManager = false;
        }
        /// @}

        if (FeatureOption.MTK_FLIGHT_MODE_POWER_OFF_MD) {
            final String GSM_RIL_EBOOT = "gsm.ril.eboot";
            if (enabled) {
                PhoneGlobals.getInstance().phoneMgr.setRadioOff();
            } else {
                if (enabled) {
                    phone.setRadioPower(false, true);
                } else {
                    //[ALPS00454637] - START
                    /* for consistent UI ,SIM Management for single sim project */
                    int SimModeSetting = System.getInt(getContentResolver(),
                            System.DUAL_SIM_MODE_SETTING, GeminiNetworkSubUtil.MODE_SIM1_ONLY);
                    if(SimModeSetting == GeminiNetworkSubUtil.MODE_FLIGHT_MODE){
                        if (phone instanceof GSMPhone){
                            /* Turn off airplane mode, but Radio still off due to sim mode setting is off */
                            /* We must force noitfy service state change */
                            if (VDBG) PhoneLog.d(LOG_TAG, "Force notify ServiceState");
                            ((GSMPhone)phone).forceNotifyServiceStateChange();
                        }                     
                    } 
                    //[ALPS00454637] - END
                    
                    /// M: Silent Reboot
                    SystemProperties.set(GSM_RIL_EBOOT, "1");
                    PhoneLog.d(LOG_TAG, "set gsm.ril.eboot to 1 for flight mode on");

                    phone.setRadioPowerOn();
                }
            }
        } else {
            PhoneWrapper.setRadioMode(phone, enabled, getContentResolver());
        }
    }

    private void onReceiveSimStateChagne(Context context, Intent intent) {
        final int unlockSlotId = intent.getIntExtra(PhoneConstants.GEMINI_SIM_ID_KEY, -1);
        final String unlockSIMStatus = intent.getStringExtra(IccCardConstants.INTENT_KEY_ICC_STATE);

        PhoneLog.d(LOG_TAG, "onReceiveSimStateChagne, slot+" + unlockSlotId + " status:"
                + unlockSIMStatus);

        if (unlockSIMStatus.equals(IccCardConstants.INTENT_VALUE_ICC_READY)) {
            final int delaySendMessage = 2000;
            mHandler.sendMessageDelayed(mHandler.obtainMessage(EVENT_SIM_STATE_CHANGED,
                    IccCardConstants.INTENT_VALUE_ICC_READY), delaySendMessage);
        } else {
            PhoneLog.w(LOG_TAG, "onReceiveSimStateChagne, SIM not ready");
        }
    }

    /**
     * dismiss keyguard, this is needed when user want to act on another activity
     */
    private static void dismissKeyguard(Context context) {
        KeyguardManager km = (KeyguardManager) context.getSystemService(Context.KEYGUARD_SERVICE);
        if (km != null) {
            if (km.isKeyguardLocked()) {
                PhoneLog.d(LOG_TAG, "dismissKeyguard: Disable keyguard!");
                try {
                    IActivityManager am = ActivityManagerNative.asInterface(ServiceManager
                            .getService("activity"));
                    am.dismissKeyguardOnNextActivity();
                } catch (RemoteException e) {
                    PhoneLog.e(LOG_TAG, "dismissKeyguard: fail...");
                }
            }
        }
    }
    /// @}

    /// M: MTK new Messages @{
    /**
     * 
     * @param msg
     * @return
     */
    private boolean handleMessageMtk(Message msg) {
        PhoneLog.d(LOG_TAG, "handleMessageMtk");
        switch (msg.what) {
            case MMI_INITIATE:
            case MMI_INITIATE2:
            case MMI_INITIATE3:
            case MMI_INITIATE4:
                int mmiInitiateSlot = GeminiUtils.getSlotIdByRegisterEvent(msg.what,
                        MMI_INITIATE_GEMINI);
                PhoneLog.d(LOG_TAG, "handleMessageMtk, MMI_INITIATE slot=" + mmiInitiateSlot);
                break;
            case MMI_COMPLETE:
            case MMI_COMPLETE2:
            case MMI_COMPLETE3:
            case MMI_COMPLETE4:
                int mmiCompleteSlot = GeminiUtils.getSlotIdByRegisterEvent(msg.what,
                        MMI_COMPLETE_GEMINI);
                PhoneLog.d(LOG_TAG, "handleMessageMtk, MMI_COMPLETE slot=" + mmiCompleteSlot);
                onMMIComplete((AsyncResult) msg.obj, mmiCompleteSlot);
                break;

            case MMI_CANCEL:
            case MMI_CANCEL2:
            case MMI_CANCEL3:
            case MMI_CANCEL4:
                int mmiCancelSlot = GeminiUtils.getSlotIdByRegisterEvent(msg.what,
                        MMI_CANCEL_GEMINI);
                PhoneLog.d(LOG_TAG, "handleMessageMtk, MMI_CANCEL slot=" + mmiCancelSlot);
                PhoneUtils.cancelMmiCodeExt(phone, mmiCancelSlot);
                PhoneUtils.dismissUssdDialog();
                PhoneUtils.dismissMMIDialog();
                break;

            case EVENT_TIMEOUT:
                handleTimeout(msg.arg1);
                break;

            case MESSAGE_SET_PREFERRED_NETWORK_TYPE:
                AsyncResult ar = (AsyncResult) msg.obj;
                Intent it = new Intent(NETWORK_MODE_CHANGE_RESPONSE);
                if (ar.exception == null) {
                    it.putExtra(NETWORK_MODE_CHANGE_RESPONSE, true);
                    it.putExtra("NEW_NETWORK_MODE", msg.arg2);
                } else {
                    it.putExtra(NETWORK_MODE_CHANGE_RESPONSE, false);
                    it.putExtra(OLD_NETWORK_MODE, msg.arg1);
                }
                sendBroadcast(it);
                break;

            case EVENT_TOUCH_ANSWER_VT:
                PhoneLog.d(LOG_TAG, "handleMessageMtk, EVENT_TOUCH_ANSWER_VT");
                try {
                    PhoneUtils.internalAnswerCall();
                } catch (Exception e) {
                    PhoneLog.e(LOG_TAG, "EVENT_TOUCH_ANSWER_VT exception:" + e.getMessage());
                }
                break;

            case EVENT_SHOW_INCALL_SCREEN_FOR_STK_SETUP_CALL:
                break;

            default:
                PhoneLog.d(LOG_TAG, "handleMessageMtk, false (not mtk message)");
                return false;
        }
        return true;
    }

    private void handleTimeout(int seq) {
        synchronized (this) {
            if (DBG) {
                Log.d(LOG_TAG, "handleTimeout");
            }
            if (seq == mWakelockSequence) {
                mWakeLockForDisconnect.release();
            }
        }
    }
    /// @}

    /// M: TTY Feature @{
    private void updateTTYEnabled() {
        // FeatureOption.MTK_TTY_SUPPORT
        if (PhoneUtils.isSupportFeature("TTY")) {
            mTtyEnabled = getResources().getBoolean(R.bool.tty_enabled);
        } else {
            mTtyEnabled = false;
        }
    }

    public boolean isEnableTTY() {
        return mTtyEnabled;
    }

    private int convertTTYmodeToRadio(int ttyMode) {
        int radioMode = 0;

        switch (ttyMode) {
        case Phone.TTY_MODE_FULL:
        case Phone.TTY_MODE_HCO:
        case Phone.TTY_MODE_VCO:
            radioMode = Phone.TTY_MODE_FULL;
            break;
        default:
            radioMode = Phone.TTY_MODE_OFF;
        }
        return radioMode;
    }
    /// @}

    /**
     * If GEMINI, get MTKCallManager, else CallManager
     *
     * @return
     */
    public Object getCallManager() {
        if (GeminiUtils.isGeminiSupport()) {
            return mCMGemini;
        }
        return mCM;
    }


    private CallLogger mCallLogger;

    /**
     * M: For ALPS00671278 Add Call Logs
     *
     * @param call  The call you want to add to CallLog
     * @param simId The SIM id this call used
     */
    private void addCallLogs(Call call) {
        Log.d(LOG_TAG, "call.getState() = " + call.getState());

        int slot = 0;
        Phone myPhone = call.getPhone();
        if (myPhone != null && myPhone.getPhoneType() != PhoneConstants.PHONE_TYPE_SIP) {
            // get slot from phone
            slot = myPhone.getMySimId();
        }
        List<Connection> connections = call.getConnections();

        if (call.getState() != Call.State.IDLE) {
            for (Connection c : connections) {
                if (c != null && c.isAlive()) {
                    mCallLogger.logCall(c, slot);
                    Log.d(LOG_TAG, "addCallLogs for shut down action");
                }
            }
        }
    }

    public Intent createPhoneEndIntent() {
        Intent intent = null;
        if (FeatureOption.MTK_BRAZIL_CUSTOMIZATION_VIVO) {
            intent = new Intent(Intent.ACTION_MAIN);
            intent.addCategory(Intent.CATEGORY_HOME);
            return intent;
        }

        return intent;
    }

    /// M: VT @{
    private boolean checkScreenOnForVT(boolean screenOn) {
        boolean screenOnImmediately = screenOn;
        if (FeatureOption.MTK_VT3G324M_SUPPORT) {
            screenOnImmediately = screenOnImmediately ||
                    ((!VTCallUtils.isVTIdle()) && (!VTCallUtils.isVTRinging()));
        }
        return screenOnImmediately;
    }
    /// @}

    private void clearSimSettingFlag(int slot) {
        Long bitSetMask = (0x3L << (2 * slot));

        Long simLockState = 0x0L;

        try {
            simLockState = Settings.System.getLong(getApplicationContext()
                    .getContentResolver(), Settings.System.SIM_LOCK_STATE_SETTING);

            simLockState = simLockState & (~bitSetMask);

            Settings.System.putLong(getApplicationContext().getContentResolver(),
                    Settings.System.SIM_LOCK_STATE_SETTING, simLockState);
        } catch (SettingNotFoundException e) {
            PhoneLog.e(LOG_TAG, "clearSimSettingFlag exception, slot=" + slot);
            e.printStackTrace();
        }
    }

    public void touchAnswerVTCall() {
        PhoneLog.d(LOG_TAG, "touchAnswerVTCall()");

        if (!VTCallUtils.isVTRinging()) {
            PhoneLog.w(LOG_TAG, "touchAnswerVTCall: no Ringing VT call, just return");
            return;
        }

        mHandler.sendMessage(Message.obtain(mHandler, EVENT_TOUCH_ANSWER_VT));
    }

    //To judge whether current sim card need to unlock sim lock:default false
    public static boolean bNeedUnlockSIMLock(int iSIMNum) {
        IccCardConstants.State state = PhoneWrapper.getIccCard(PhoneFactory.getDefaultPhone(), iSIMNum).getState();
        if ((state == IccCardConstants.State.PIN_REQUIRED)
                || (state == IccCardConstants.State.PUK_REQUIRED)
                || (state == IccCardConstants.State.NOT_READY)) {

            PhoneLog.d(LOG_TAG, "[bNeedUnlockSIMLock][NO Card/PIN/PUK]: " + iSIMNum);
            return false;
        }
        return true;
    }

    private void addCurrentCalltoCallLogs() {
        // TODO: need to check dualtalk case
        Call ringCall = mCM.getFirstActiveRingingCall();
        Call fgCall = mCM.getActiveFgCall();
        Call bgCall = mCM.getFirstActiveBgCall();

        if(ringCall != null && ringCall.getState() != Call.State.IDLE) {
            addCallLogs(ringCall);
        }

        if(fgCall != null && fgCall.getState() != Call.State.IDLE) {
            addCallLogs(fgCall);
        }

        if(bgCall != null && bgCall.getState() != Call.State.IDLE) {
            addCallLogs(bgCall);
        }
    }

    /**
     * M: For BT DualTalk
     */
    public boolean isBTConnected() {
        return bluetoothManager.isBluetoothAudioConnected();
    }

    /**
     * Handles the green CALL key while in-call.
     * @return true if we consumed the event.
     */
    public boolean handleHeadsetHookKey() {
        // The green CALL button means either "Answer", "Unhold", or
        // "Swap calls", or can be a no-op, depending on the current state
        // of the Phone.

        /// M: For ALPS01257153 @{
        // If the phone is totally idle, we ignore HEADSETHOOK events
        // (and instead let them fall through to the media player.)
        if (mCM.getState() == PhoneConstants.State.IDLE) {
            PhoneLog.d(LOG_TAG, "CallManager is idle, just ignore HEADSETHOOK key.");
            return false;
        }
        /// @}

        final boolean hasRingingCall = mCM.hasActiveRingingCall();
        final boolean hasActiveCall = mCM.hasActiveFgCall();
        final boolean hasHoldingCall = mCM.hasActiveBgCall();

        /// M: for DualTalk @{
        if (DualTalkUtils.isSupportDualTalk()) {
            if (DualTalkUtils.getInstance() != null && DualTalkUtils.getInstance().isCdmaAndGsmActive()) {
                return handleHeadsetHookKeyForDualtalk();
            }
        }
        /// @}

        /// M: for DualTalk @{
        int phoneType = PhoneUtils.getPhoneTypeMTK();
        /// @}
        
        if (phoneType == PhoneConstants.PHONE_TYPE_CDMA) {
            // The green CALL button means either "Answer", "Swap calls/On Hold", or
            // "Add to 3WC", depending on the current state of the Phone.

            CdmaPhoneCallState.PhoneCallState currCallState =
                cdmaPhoneCallState.getCurrentCallState();
            if (hasRingingCall) {
                //Scenario 1: Accepting the First Incoming and Call Waiting call
                if (DBG) {
                   PhoneLog.d(LOG_TAG, "answerCall: First Incoming and Call Waiting scenario");
                }
                PhoneUtils.internalAnswerCall();  // Automatically holds the current active call,
                                       // if there is one
            } else if ((currCallState == CdmaPhoneCallState.PhoneCallState.THRWAY_ACTIVE)
                    && (hasActiveCall)) {
                //Scenario 2: Merging 3Way calls
                if (DBG) {
                    PhoneLog.d(LOG_TAG, "answerCall: Merge 3-way call scenario");
                }
                // Merge calls
                PhoneUtils.mergeCalls(mCM);
            } else if (currCallState == CdmaPhoneCallState.PhoneCallState.CONF_CALL) {
                //Scenario 3: Switching between two Call waiting calls or drop the latest
                // connection if in a 3Way merge scenario
                if (DBG) {
                    PhoneLog.d(LOG_TAG, "answerCall: Switch btwn 2 calls scenario");
                }
                PhoneUtils.internalSwapCalls();
            /// M: for DualTalk && ALPS00300908 @{
            } else if (currCallState == CdmaPhoneCallState.PhoneCallState.SINGLE_ACTIVE
                    && hasActiveCall) {
                if (DBG) {
                    PhoneLog.d(LOG_TAG, "handleCallKey: hold/unhold cdma case.");
                }
                PhoneUtils.internalSwapCalls();
            /// @}
            }
        } else if ((phoneType == PhoneConstants.PHONE_TYPE_GSM)
                || (phoneType == PhoneConstants.PHONE_TYPE_SIP)) {
            if (hasRingingCall) {
                // If an incoming call is ringing, the CALL button is actually
                // handled by the PhoneWindowManager.  (We do this to make
                // sure that we'll respond to the key even if the InCallScreen
                // hasn't come to the foreground yet.)
                //
                // We'd only ever get here in the extremely rare case that the
                // incoming call started ringing *after*
                // PhoneWindowManager.interceptKeyTq() but before the event
                // got here, or else if the PhoneWindowManager had some
                // problem connecting to the ITelephony service.
                Log.w(LOG_TAG, "handleCallKey: incoming call is ringing!"
                      + " (PhoneWindowManager should have handled this key.)");
                // But go ahead and handle the key as normal, since the
                // PhoneWindowManager presumably did NOT handle it:

                // There's an incoming ringing call: CALL means "Answer".
                 PhoneUtils.internalAnswerCall();
            } else if (hasActiveCall && hasHoldingCall) {
                // Two lines are in use: CALL means "Swap calls".
                if (DBG) PhoneLog.d(LOG_TAG, "handleCallKey: both lines in use ");
                callCommandService.swap();
            } else if (hasHoldingCall) {
                // There's only one line in use, AND it's on hold.
                // In this case CALL is a shortcut for "unhold".
                if (DBG) PhoneLog.d(LOG_TAG, "handleCallKey: call on hold ==> unhold.");
                PhoneUtils.switchHoldingAndActive(mCM.getFirstActiveBgCall());  // Really means "unhold" in this state
            } else {
                // The most common case: there's only one line in use, and
                // it's an active call (i.e. it's not on hold.)
                // In this case CALL is a no-op.
                // (This used to be a shortcut for "add call", but that was a
                // bad idea because "Add call" is so infrequently-used, and
                // because the user experience is pretty confusing if you
                // inadvertently trigger it.)
                if (VDBG) PhoneLog.d(LOG_TAG, "handleCallKey: call in foregound ==> ignoring.");

                /// M: for Video Call @{
                // If the foreground call is video call, ignore this call key
                // And if in VT, it is impossible that hasHoldingCall is true
                PhoneUtils.checkCallKeyForVT(phoneType);
                /// @}
                // But note we still consume this key event; see below.
            }
        } else {
            throw new IllegalStateException("Unexpected phone type: " + phoneType);
        }

        // We *always* consume the CALL key, since the system-wide default
        // action ("go to the in-call screen") is useless here.
        return true;
    }

    /**
     * behavior of handling headsetHookKey for dual talk.
     * @return
     */
    private boolean handleHeadsetHookKeyForDualtalk() {
        if (mCM.getState() == PhoneConstants.State.RINGING) {
            //we assume that the callkey shouldn't be here when there is ringing call
            if (DBG) {
                PhoneLog.d(LOG_TAG, "handleCallKeyForDualTalk: rev call-key when ringing!");
            }
            /// For ALPS01196081 & ALPS01188044 @{
            if (DualTalkUtils.getInstance() != null && (DualTalkUtils.getInstance().hasMultipleRingingCall()
                    || DualTalkUtils.getInstance().isRingingWhenOutgoing())) {
                PhoneLog.d(LOG_TAG, "handleCallKeyForDualTalk: call internalAnswerCall directly");
                PhoneUtils.internalAnswerCall();
                return true;
            }
            /// @}
            return false;
        }
        return false;
    }

    public int updateCdmaPhoneCallState() {
        int currentCallState = -1;
        CdmaPhoneCallState.PhoneCallState newState = cdmaPhoneCallState.getCurrentCallState();
        switch (newState) {
            case IDLE:
                currentCallState = 0;
                break;
            case SINGLE_ACTIVE:
                currentCallState = 1;
                break;
            case THRWAY_ACTIVE:
                currentCallState = 2;
                break;
            case CONF_CALL:
                currentCallState = 3;
                break;
            default:
                break;
        }
        return currentCallState;
    }

    /**
     * For smartBook, because the powermanager's the signature is platform, so the app signature must be platform.
     * @param onOff
     */
    public void updatePowerForSmartBook(boolean onOff) {
        PhoneLog.d(LOG_TAG, "onOff: " + onOff);
        if (onOff) {
            mPowerManager.sbWakeUp(SystemClock.uptimeMillis());
        } else {
            mPowerManager.sbGoToSleep(SystemClock.uptimeMillis());
        }
    }
}
