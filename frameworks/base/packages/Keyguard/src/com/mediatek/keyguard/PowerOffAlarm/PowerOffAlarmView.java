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
package com.android.keyguard;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.accounts.AccountManagerCallback;
import android.accounts.AccountManagerFuture;
import android.accounts.AuthenticatorException;
import android.accounts.OperationCanceledException;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.content.ServiceConnection;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.SystemClock;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.Log;
import android.view.IWindowManager;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;


import com.android.internal.widget.LockPatternUtils;
import com.android.internal.widget.LockPatternView;
import com.mediatek.common.voicecommand.IVoiceCommandListener;
import com.mediatek.common.voicecommand.IVoiceCommandManagerService;
import com.mediatek.common.voicecommand.VoiceCommandListener;
import java.io.IOException;
import java.util.Calendar;
import java.util.List;
import com.mediatek.common.featureoption.FeatureOption;

public class PowerOffAlarmView extends RelativeLayout implements
        KeyguardSecurityView, MediatekGlowPadView.OnTriggerListener {
    private static final String TAG = "PowerOffAlarm";
    private static final boolean DEBUG = false;
    private final int DELAY_TIME_SECONDS = 7;
    private int mFailedPatternAttemptsSinceLastTimeout = 0;
    private int mTotalFailedPatternAttempts = 0;
    private LockPatternUtils mLockPatternUtils;
    private Button mForgotPatternButton;
    private TextView mVcTips, titleView = null;
    private LinearLayout mVcTipsContainer;
    private KeyguardSecurityCallback mCallback;
    private IVoiceCommandManagerService mVCmdMgrService;
    private boolean isRegistered = false;
    private boolean mEnableFallback;
    private boolean SUPPORT_VOICE_UI = FeatureOption.MTK_VOICE_UI_SUPPORT;
    private Context mContext;

    // These defaults must match the values in res/xml/settings.xml
    private static final String DEFAULT_SNOOZE = "10";
    private static final String DEFAULT_VOLUME_BEHAVIOR = "2";
    protected static final String SCREEN_OFF = "screen_off";

    protected Alarm mAlarm;
    private int mVolumeBehavior;
    boolean mFullscreenStyle;
    private MediatekGlowPadView mGlowPadView;
    private boolean mIsDocked = false;
    private static final int UPDATE_LABEL = 99;
    // Parameters for the GlowPadView "ping" animation; see triggerPing().
    private static final int PING_MESSAGE_WHAT = 101;
    private static final boolean ENABLE_PING_AUTO_REPEAT = true;
    private static final long PING_AUTO_REPEAT_DELAY_MSEC = 1200;

    private boolean mPingEnabled = true;
    private IVoiceCommandListener mVoiceCallback = new IVoiceCommandListener.Stub(){
        public void onVoiceCommandNotified(int mainAction, int subAction, Bundle extraData)
                        throws RemoteException {
                        Message.obtain(mVoiceCommandHandler, mainAction, subAction, 0, extraData).sendToTarget();
         }
    };
    private Handler mVoiceCommandHandler = new Handler() {
        public void handleMessage(Message msg) {
            handleVoiceCommandNotified(msg.what, msg.arg1, (Bundle) msg.obj);
         }
    };
    public void handleVoiceCommandNotified(int mainAction, int subAction, Bundle extraData) {
        if (SUPPORT_VOICE_UI) {
            int actionExtraResult = extraData.getInt(VoiceCommandListener.ACTION_EXTRA_RESULT);
            if (actionExtraResult != VoiceCommandListener.ACTION_EXTRA_RESULT_SUCCESS) {
                Log.v(TAG, "handleVoiceCommandNotified with failed result , just return");
                return;
             }
            if (mainAction == VoiceCommandListener.ACTION_MAIN_VOICE_UI) {
                switch (subAction) {
                    case VoiceCommandListener.ACTION_VOICE_UI_START:
                        if (actionExtraResult == VoiceCommandListener.ACTION_EXTRA_RESULT_SUCCESS) {
                            if (mVCmdMgrService != null) {
                                sendVoiceCommand("powerOffAlarm", VoiceCommandListener.ACTION_MAIN_VOICE_COMMON,
                                            VoiceCommandListener.ACTION_VOICE_COMMON_KEYWORD, null);
                            }
                        }
                        break;
                    case VoiceCommandListener.ACTION_VOICE_UI_NOTIFY :
                        int notifyResult = extraData.getInt(VoiceCommandListener.ACTION_EXTRA_RESULT);
                        if (notifyResult == VoiceCommandListener.ACTION_EXTRA_RESULT_SUCCESS) {
                            int commandId = extraData.getInt(VoiceCommandListener.ACTION_EXTRA_RESULT_INFO);
                            Log.v(TAG, "voice command id = " + commandId);
                            if (commandId == 5) {
                                snooze();
                            } else if (commandId == 16) {
                                powerOn();
                            } else if (commandId == 17) {
                                powerOff();
                            }
                        }
                        break;
                    case VoiceCommandListener.ACTION_VOICE_UI_STOP :
                        break;
                    default :
                        break;
                }
            } else if (mainAction == VoiceCommandListener.ACTION_MAIN_VOICE_COMMON) {
                if (subAction == VoiceCommandListener.ACTION_VOICE_COMMON_KEYWORD) {
                    int indicatorResult = extraData.getInt(VoiceCommandListener.ACTION_EXTRA_RESULT);
                    if (indicatorResult == VoiceCommandListener.ACTION_EXTRA_RESULT_SUCCESS) {
                        String[] stringCommonInfo = extraData.getStringArray(VoiceCommandListener.ACTION_EXTRA_RESULT_INFO);
                        String quotaStart = mContext.getString(R.string.voiceui_quota_start);
                        String quotaEnd = mContext.getString(R.string.voiceui_quota_end);
                        if(TextUtils.isEmpty(quotaStart)){
                            quotaStart = "\"";
                            quotaEnd = "\"";
                        }
                        StringBuilder sb = new StringBuilder();
                        sb.append(mContext.getString(R.string.voiceui_notify_string));
                        sb.append(quotaStart).append(stringCommonInfo[0]).append(quotaEnd);
                        sb.append(mContext.getString(R.string.voiceui_comma));
                        sb.append(quotaStart).append(stringCommonInfo[1]).append(quotaEnd);
                        sb.append(mContext.getString(R.string.voiceui_or));
                        sb.append(quotaStart).append(stringCommonInfo[2]).append(quotaEnd);
                        sb.append(mContext.getString(R.string.voiceui_control_poweroff_alarm));
                        if (mVcTips != null) {
                            mVcTips.setText(sb);
                        }
                        if (mVcTipsContainer != null) {
                            mVcTipsContainer.setVisibility(View.VISIBLE);
                        }
                    }
                }
            }
        }
    }
    private final Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case PING_MESSAGE_WHAT:
                    triggerPing();
                    break;
                case UPDATE_LABEL:
                    if(titleView != null){
                        titleView.setText(msg.getData().getString("label"));
                    }
                    break;
            }
        }
    };

    public PowerOffAlarmView(Context context) {
        this(context, null);
    }

    public PowerOffAlarmView(Context context, AttributeSet attrs) {
        super(context, attrs);
        mContext = context;

    }

    public void setKeyguardCallback(KeyguardSecurityCallback callback) {
        mCallback = callback;
    }

    public void setLockPatternUtils(LockPatternUtils utils) {
        mLockPatternUtils = utils;
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        Log.w(TAG, "onFinishInflate ... ");
        setKeepScreenOn(true);
        titleView = (TextView) findViewById(R.id.alertTitle);
        if(SUPPORT_VOICE_UI) {
            mVcTips = (TextView) findViewById(R.id.tips);
            mVcTipsContainer = (LinearLayout) findViewById(R.id.tips_container);
        }
        mGlowPadView = (MediatekGlowPadView) findViewById(R.id.glow_pad_view);
        mGlowPadView.setOnTriggerListener(this);
        setFocusableInTouchMode(true);
        triggerPing();

        // Check the docking status , if the device is docked , do not limit rotation
        IntentFilter ifilter = new IntentFilter(Intent.ACTION_DOCK_EVENT);
        Intent dockStatus = mContext.registerReceiver(null, ifilter);
        if (dockStatus != null) {
            mIsDocked = dockStatus.getIntExtra(Intent.EXTRA_DOCK_STATE, -1)
                    != Intent.EXTRA_DOCK_STATE_UNDOCKED;
        }

        // Register to get the alarm killed/snooze/dismiss intent.
        IntentFilter filter = new IntentFilter(Alarms.ALARM_KILLED);
        filter.addAction(Alarms.ALARM_SNOOZE_ACTION);
        filter.addAction(Alarms.ALARM_DISMISS_ACTION);
        filter.addAction(UPDATE_LABEL_ACTION);
        mContext.registerReceiver(mReceiver, filter);

        mLockPatternUtils = mLockPatternUtils == null ? new LockPatternUtils(
                mContext) : mLockPatternUtils;
        enableEventDispatching(true);

        if (SUPPORT_VOICE_UI) {
            registerVoiceCmdService(mContext);
        }
    }

    @Override
    public void onTrigger(View v, int target) {
        final int resId = mGlowPadView.getResourceIdForTarget(target);
        switch (resId) {
            case R.drawable.mtk_ic_alarm_alert_snooze:
                snooze();
                break;

            case R.drawable.mtk_ic_alarm_alert_dismiss_pwroff:
                powerOff();
                break;

            case R.drawable.mtk_ic_alarm_alert_dismiss_pwron:
                powerOn();
                break;

            default:
                // Code should never reach here.
                Log.e(TAG, "Trigger detected on unhandled resource. Skipping.");
        }
    }

    private void triggerPing() {
        if (mPingEnabled) {
            mGlowPadView.ping();

            if (ENABLE_PING_AUTO_REPEAT) {
                mHandler.sendEmptyMessageDelayed(PING_MESSAGE_WHAT, PING_AUTO_REPEAT_DELAY_MSEC);
            }
        }
    }

    // Attempt to snooze this alert.
    private void snooze() {
        Log.d(TAG, "snooze selected");
	sendBR(SNOOZE);
        unregisteVoiceCmd();
    }

    // power on the device
    private void powerOn() {
        enableEventDispatching(false);
        Log.d(TAG, "powerOn selected");
        sendBR(DISMISS_AND_POWERON);
        sendBR(NORMAL_BOOT_ACTION);
        unregisteVoiceCmd();
    }

    // power off the device
    private void powerOff() {
        Log.d(TAG, "powerOff selected");
        sendBR(DISMISS_AND_POWEROFF);
        unregisteVoiceCmd();
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        boolean result = super.onTouchEvent(ev);
        //TODO: if we need to add some logic here ?
        return result;
    }

    @Override
    public void showUsabilityHint() {
    }

    /** TODO: hook this up */
    public void cleanUp() {
        if (DEBUG)
            Log.v(TAG, "Cleanup() called on " + this);
        mLockPatternUtils = null;
    }

    @Override
    public boolean needsInput() {
        return false;
    }

    @Override
    public void onPause() {
    }

    @Override
    public void onResume(int reason) {
        reset();
        Log.v(TAG, "onResume");
    }

    @Override
    public KeyguardSecurityCallback getCallback() {
        return mCallback;
    }

    @Override
    public void onDetachedFromWindow() {
        Log.v(TAG, "onDetachedFromWindow ....");
        mContext.unregisterReceiver(mReceiver);
    }

    @Override
    public void showBouncer(int duration) {
    }

    @Override
    public void hideBouncer(int duration) {
    }

    private void enableEventDispatching(boolean flag) {
        try {
            final IWindowManager wm = IWindowManager.Stub
                    .asInterface(ServiceManager
                            .getService(Context.WINDOW_SERVICE));
            if(wm != null){
                wm.setEventDispatching(flag);
            }
        } catch (RemoteException e) {
            Log.w(TAG, e.toString());
        }
    }

    private void sendBR(String action){
        Log.w(TAG, "send BR: " + action);
        mContext.sendBroadcast(new Intent(action));
    }

    // Receives the ALARM_KILLED action from the AlarmKlaxon,
    // and also ALARM_SNOOZE_ACTION / ALARM_DISMISS_ACTION from other
    // applications
    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
       @Override
       public void onReceive(Context context, Intent intent) {
          String action = intent.getAction();
          Log.v(TAG, "receive action : " + action);
          if(UPDATE_LABEL_ACTION.equals(action)){
              Message msg = new Message();
              msg.what = UPDATE_LABEL;
              Bundle data = new Bundle();
              data.putString("label", intent.getStringExtra("label"));
              msg.setData(data);
              mHandler.sendMessage(msg);
          }else if (PowerOffAlarmManager.isAlarmBoot()) {
              snooze();
          }
       }
    };

    private void unregisteVoiceCmd() {
        if (mVCmdMgrService != null && SUPPORT_VOICE_UI) {
            String pkgName = "powerOffAlarm";
            sendVoiceCommand(pkgName, VoiceCommandListener.ACTION_MAIN_VOICE_UI,
                        VoiceCommandListener.ACTION_VOICE_UI_STOP, null);
            try{
                int errorid = mVCmdMgrService.unregisterListener(pkgName, mVoiceCallback);
                if (errorid == VoiceCommandListener.VOICE_NO_ERROR) {
                    isRegistered = false;
                }
            } catch (RemoteException e) {
                Log.v(TAG, "unregisteVoiceCmd voiceCommand RemoteException = " + e.getMessage() );
                isRegistered = false;
                mVCmdMgrService = null;
            }
            Log.v(TAG, "unregisteVoiceCmd end " );
            mContext.unbindService(mVoiceSerConnection);
            mVCmdMgrService = null;
            isRegistered = false;
        }
    }


    private void registerVoiceCmdService(Context context){
        if (mVCmdMgrService == null) {
            bindVoiceService(context);
        } else {
            String pkgName = "powerOffAlarm";
            registerVoiceCommand(pkgName);
            sendVoiceCommand(pkgName, VoiceCommandListener.ACTION_MAIN_VOICE_UI,
                    VoiceCommandListener.ACTION_VOICE_UI_START, null);
        }
    }
    
    private void registerVoiceCommand(String pkgName) {
        if(!isRegistered) {
                                    try {
                int errorid = mVCmdMgrService.registerListener(pkgName, mVoiceCallback);
                if (errorid == VoiceCommandListener.VOICE_NO_ERROR) {
                    isRegistered = true;
                } else {
                    Log.v(TAG, "register voiceCommand fail " );
                }
            } catch (RemoteException e){
                isRegistered = false;
                mVCmdMgrService = null;                
                Log.v(TAG, "register voiceCommand RemoteException =  " + e.getMessage() );
            }
        } else {
            Log.v(TAG, "register voiceCommand success " );
                                    }
        Log.v(TAG, "register voiceCommand end " );
     }

    private void sendVoiceCommand(String pkgName, int mainAction, int subAction, Bundle extraData) {
        if(isRegistered) {
            try{
                int errorid = mVCmdMgrService.sendCommand(pkgName, mainAction, subAction, extraData);
                if (errorid != VoiceCommandListener.VOICE_NO_ERROR) {
                    Log.v(TAG, "send voice Command fail " );
                } else {
                    Log.v(TAG, "send voice Command success " );
                        }
                }catch (RemoteException e){
                    isRegistered = false;
                    mVCmdMgrService = null;                
                    Log.v(TAG, "send voice Command RemoteException =  " + e.getMessage() );
                }
        } else {
            Log.v(TAG, "didn't register , can not send voice Command  " );
        }
    }

    private void bindVoiceService(Context context){
        Log.v(TAG, "bindVoiceService begin  " );
	//mLockPatternUtils = new LockPatternUtils(context);
        Intent mVoiceServiceIntent = new Intent();
        mVoiceServiceIntent.setAction(VoiceCommandListener.VOICE_SERVICE_ACTION);
        mVoiceServiceIntent.addCategory(VoiceCommandListener.VOICE_SERVICE_CATEGORY);
        context.bindService(mVoiceServiceIntent, mVoiceSerConnection, Context.BIND_AUTO_CREATE);
    }

    private ServiceConnection mVoiceSerConnection = new ServiceConnection() {
         @Override
        public void onServiceConnected(ComponentName name, IBinder service){
            mVCmdMgrService = IVoiceCommandManagerService.Stub.asInterface(service);
            String pkgName = "powerOffAlarm";
            registerVoiceCommand(pkgName);
            sendVoiceCommand( pkgName, VoiceCommandListener.ACTION_MAIN_VOICE_UI,
                        VoiceCommandListener.ACTION_VOICE_UI_START, null);
            Log.v(TAG, "onServiceConnected   " );
        }
        public void onServiceDisconnected(ComponentName name) {            
            Log.v(TAG, "onServiceDisconnected   " );
            isRegistered = false;
            mVCmdMgrService = null;
        }

    };

    @Override
    public void onGrabbed(View v, int handle) {
        // TODO Auto-generated method stub
    }

    @Override
    public void onReleased(View v, int handle) {
        // TODO Auto-generated method stub
    }

    @Override
    public void onGrabbedStateChange(View v, int handle) {
        // TODO Auto-generated method stub
    }

    @Override
    public void onFinishFinalAnimation() {
        // TODO Auto-generated method stub
    }

    @Override
    public void reset() {
        // TODO Auto-generated method stub
    }

    private static final String SNOOZE = "com.android.deskclock.SNOOZE_ALARM";
    private static final String DISMISS_AND_POWEROFF = "com.android.deskclock.DISMISS_ALARM";
    private static final String DISMISS_AND_POWERON = "com.android.deskclock.POWER_ON_ALARM";
    private static final String UPDATE_LABEL_ACTION = "update.power.off.alarm.label";
    private static final String NORMAL_BOOT_ACTION = "android.intent.action.normal.boot";
    private static final String NORMAL_BOOT_DONE_ACTION = "android.intent.action.normal.boot.done";
    private static final String DISABLE_POWER_KEY_ACTION = "android.intent.action.DISABLE_POWER_KEY";

}
