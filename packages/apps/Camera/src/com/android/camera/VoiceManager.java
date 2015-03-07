package com.android.camera;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.media.AudioManager;
import android.media.SoundPool;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.RemoteException;
import android.media.SoundPool.OnLoadCompleteListener;

import com.android.camera.Log;
import com.android.camera.R;
import com.android.camera.SettingChecker;
import com.android.camera.manager.SettingManager;
import com.android.camera.manager.ShutterManager;
import com.android.camera.VoiceManager;
import com.mediatek.common.voicecommand.IVoiceCommandListener;
import com.mediatek.common.voicecommand.IVoiceCommandManagerService;
import com.mediatek.common.voicecommand.VoiceCommandListener;

import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

public class VoiceManager {
    private static final String TAG = "VoiceManager";
    private static final int VOICE_COMMAND_CAPTURE = 3;
    private static final int VOICE_COMMAND_CHEESE = 4;
    
    private static final int USER_GUIDE_UPDATED = 100;
    private static final int VOICE_VALUE_UPDATED = 101;
    private static final int VOICE_COMMAND_RECEIVE = 102;
    private static final int PLAY_VOICE_COMMAND = 103;
    protected final Handler mHandler = new MainHandler();
    public interface Listener {
        void onVoiceValueUpdated(String value);
    }
    
    public static final String VOICE_ON = "on";
    public static final String VOICE_OFF = "off";
    private static final String VOICE_SERVICE = "voicecommand";
    private static final int UNKNOWN = -1;
    
    private Context mContext;
    private String mVoiceValue;
    private String[] mKeywords;
    // Voice command Path from framework
    private String mCommandPathKeywords;
    // Key value
    private String[] mCommandPath = new String[]{"voice0", "voice1"};
    // Cache voice
    private HashMap<String, Integer> mSoundMap = new HashMap<String, Integer>();
    private List<Listener> mListeners = new CopyOnWriteArrayList<Listener>();
    private boolean mStartUpdate;
    private boolean mRegistered;
    private int mCommandId;
    // Voice command Id for recording voice
    private int mVoiceCommandId;
    private boolean mSwitchSublistShow;
    private SettingManager mSettingManager;
    
    private SoundPool mVoiceCaptureSound;
    private int mVoiceCaptureSoundId;
    private int mVoiceCaptureStreamId;
    private String mVoiceCommandPath;
    private String mPackageName;
    public VoiceManager(Context context) {
        mContext = context;
        mSettingManager = ((Camera) mContext).getSettingManager();
        mPackageName = ((Camera) mContext).getPackageName();
    }
    

    public boolean addListener(Listener l) {
        Log.d(TAG, "addListener(" + l + ")");
        boolean added = false;
        if (!mListeners.contains(l)) {
            added = mListeners.add(l);
        }
        mHandler.sendEmptyMessage(USER_GUIDE_UPDATED);
        return added;
    }
    
    public boolean removeListener(Listener l) {
        Log.d(TAG, "removeListener(" + l + ")");
        return mListeners.remove(l);
    }
    /**
     * This Handler is used to post message back onto the main thread of the
     * application
     */
    private class MainHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            Log.i(TAG, "msg id=" + msg.what);
            switch (msg.what) {
            case USER_GUIDE_UPDATED:
                notifyUserGuideIfNeed();
                break;
            case VOICE_VALUE_UPDATED:
                notifyStateChangedIfNeed();
                break;
            case VOICE_COMMAND_RECEIVE:
                notifyCommandIfNeed(mCommandId);
                break;
            case PLAY_VOICE_COMMAND:
                playVoiceCommandSound(mVoiceCommandId);
                break;
            default:
                break;
            }
        }
    }

    public void playVoiceCommandSound(int voiceId) {
        Log.i(TAG, "playVoiceCommandSound() voiceId=" + voiceId);
        int voiceCommandId = mSoundMap.get(mCommandPath[voiceId]);
        mVoiceCaptureStreamId = mVoiceCaptureSound.play(voiceCommandId, 1, 1, 0, 0, 1);
    }

    private void notifyUserGuideIfNeed() {
        Log.d(TAG, "notifyUserGuideIfNeed() mKeywords=" + mKeywords);
        if (mKeywords != null) {
            String userGuide = getUserVoiceGuide(mKeywords);
            if (userGuide != null) {
                if (!((Camera) mContext).isVideoMode() && isVoiceEnabled()) {
                    ((Camera) mContext).showToast(userGuide);
                }
            }
        }
    }
    
    private void notifyStateChangedIfNeed() {
        Log.v(TAG, "notifyStateChangedIfNeed() mVoiceValue=" + mVoiceValue);
        for(Listener listener : mListeners) {
            listener.onVoiceValueUpdated(mVoiceValue);
        }
        if (VoiceManager.VOICE_ON.equals(mVoiceValue)) {
            enableVoice();
        }

        if (mSettingManager != null) {
            mSettingManager.refresh();
        }
    }

    private void notifyCommandIfNeed(int commandId) {
        Log.d(TAG, "notifyCommandIfNeed(" + commandId + ")");
        ShutterManager mShutterManager = ((Camera) mContext)
                .getShutterManager();
        if (VOICE_COMMAND_CAPTURE == commandId
                || VOICE_COMMAND_CHEESE == commandId) {
            if (mShutterManager != null && !((Camera) mContext).isVideoMode()
                    && isVoiceEnabled() && !mSwitchSublistShow
                    && ((Camera) mContext).getViewState() != Camera.VIEW_STATE_SETTING) {
            	// voice capture should do not work when setting pop up. 
                mShutterManager.performPhotoShutter();
            }
        }
    }
    
    private void notifyCachePathIfNeed() {
        Log.d(TAG, "notifyCachePathIfNeed mCommandPathKeywords = " + mCommandPathKeywords);
        mVoiceCaptureSound = null;
        mVoiceCaptureSound = new SoundPool(1, AudioManager.STREAM_MUSIC, 0);
        for (int i = 0; i< mCommandPath.length; i++) {
            String path = mCommandPathKeywords + i + ".ogg";
            mSoundMap.put(mCommandPath[i], mVoiceCaptureSound.load(path, 1));
        }
    }

    private String getUserVoiceGuide(String[] voice) {
        String userGuide = null;
        if (voice != null && voice.length >= 2) {
            userGuide = mContext.getString(R.string.voice_guide, voice[0], voice[1]);
        }
        Log.d(TAG, "getUserVoiceGuide(" + voice + ") return " + userGuide);
        return userGuide;
    }
    
    public void setVoiceValue(String value) {
        Log.d(TAG, "setVoiceValue(" + value + ") mVoiceValue=" + mVoiceValue);
        if (mVoiceValue == null || !mVoiceValue.equals(value)) {
            mVoiceValue = value;
            if (VOICE_ON.equals(mVoiceValue)) {
                enableVoice();
            } else {
                disableVoice();
            }
        }
    }
    
    public String getVoiceValue() {
        Log.d(TAG, "getVoiceValue() return " + mVoiceValue);
        return mVoiceValue;
    }
    
    public void startUpdateVoiceState() {
        Log.i(TAG, "startUpdateVoiceState() mStartUpdate=" + mStartUpdate);
        if (FeatureSwitcher.isVoiceEnabled()) {
            if (!mStartUpdate) {
                startGetVoiceState();
                mStartUpdate = true;
            }
        }
    }
    
    public void stopUpdateVoiceState() {
        Log.i(TAG, "stopUpdateVoiceState() mStartUpdate=" + mStartUpdate);
        if (FeatureSwitcher.isVoiceEnabled()) {
            if (mStartUpdate) {
                stopVoice();
                //set voice value off for don't update indicator before get voice state.
                mVoiceValue = VOICE_OFF;
                mStartUpdate = false;
            }
        }
    }
    
    public void enableVoice() {
        Log.d(TAG, "enableVoice()");
        if (mVoiceManagerService == null) {
            bindVoiceService();
        } else {
            registerVoiceCommand(mPackageName);
            startVoiceCommand(mPackageName,
                    VoiceCommandListener.ACTION_MAIN_VOICE_UI,
                    VoiceCommandListener.ACTION_VOICE_UI_ENABLE, null);
            startVoiceCommand(mPackageName,
                    VoiceCommandListener.ACTION_MAIN_VOICE_COMMON,
                    VoiceCommandListener.ACTION_VOICE_COMMON_KEYWORD, null);
            mHandler.sendEmptyMessage(USER_GUIDE_UPDATED);
        }
    }
    
    private void disableVoice() {
        Log.d(TAG, "disableVoice()");
        if (mVoiceManagerService != null) {
            startVoiceCommand(mPackageName,
                    VoiceCommandListener.ACTION_MAIN_VOICE_UI,
                    VoiceCommandListener.ACTION_VOICE_UI_DISALBE, null);
            unRegisterVoiceCommand(mPackageName);
        }
    }
    
    private void stopVoice() {
        Log.d(TAG, "stopVoice()");
        if (mVoiceManagerService != null) {
            startVoiceCommand(mPackageName,
                    VoiceCommandListener.ACTION_MAIN_VOICE_UI,
                    VoiceCommandListener.ACTION_VOICE_UI_STOP, null);
            unRegisterVoiceCommand(mPackageName);
            release();
        }
    }
    
    private void startGetVoiceState() {
        Log.i(TAG, "startGetVoiceState()");
        if (mVoiceManagerService == null) {
            bindVoiceService();
        } else {
            registerVoiceCommand(mPackageName);
            startVoiceCommand(mPackageName,
                    VoiceCommandListener.ACTION_MAIN_VOICE_COMMON,
                    VoiceCommandListener.ACTION_VOICE_COMMON_PROCSTATE, null);
            startVoiceCommand(mPackageName,
                    VoiceCommandListener.ACTION_MAIN_VOICE_COMMON,
                    VoiceCommandListener.ACTION_VOICE_COMMON_COMMANDPATH, null);
            startVoiceCommand(mPackageName,
                    VoiceCommandListener.ACTION_MAIN_VOICE_COMMON,
                    VoiceCommandListener.ACTION_VOICE_COMMON_KEYWORD, null);
        }
    }

    private void bindVoiceService() {
        Log.i(TAG, "Bind voice service.");
        Intent mVoiceServiceIntent = new Intent();
        mVoiceServiceIntent
                .setAction(VoiceCommandListener.VOICE_SERVICE_ACTION);
        mVoiceServiceIntent
                .addCategory(VoiceCommandListener.VOICE_SERVICE_CATEGORY);
        mContext.bindService(mVoiceServiceIntent, mVoiceSerConnection,
                Context.BIND_AUTO_CREATE);
    }

    private ServiceConnection mVoiceSerConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            mVoiceManagerService = IVoiceCommandManagerService.Stub
                    .asInterface(service);
            Log.i(TAG, "ServiceConnection onServiceConnected.");
            registerVoiceCommand(mPackageName);
            startVoiceCommand(mPackageName,
                    VoiceCommandListener.ACTION_MAIN_VOICE_COMMON,
                    VoiceCommandListener.ACTION_VOICE_COMMON_PROCSTATE, null);
            startVoiceCommand(mPackageName,
                    VoiceCommandListener.ACTION_MAIN_VOICE_COMMON,
                    VoiceCommandListener.ACTION_VOICE_COMMON_COMMANDPATH, null);
            startVoiceCommand(mPackageName,
                    VoiceCommandListener.ACTION_MAIN_VOICE_COMMON,
                    VoiceCommandListener.ACTION_VOICE_COMMON_KEYWORD, null);

        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            Log.v(TAG, "Service disconnected");
            mRegistered = false;
            mVoiceManagerService = null;
        }
    };
    
    private void registerVoiceCommand(String pkgName) {
        if (!mRegistered) {
            try {
                int errorid = mVoiceManagerService.registerListener(pkgName,
                        mCallback);
                Log.i(TAG, "Register voice Listener pkgName = " + pkgName
                        + ",errorid = " + errorid);
                if (errorid == VoiceCommandListener.VOICE_NO_ERROR) {
                    mRegistered = true;
                } else {
                    Log.v(TAG, "Register voice Listener failure ");
                }
            } catch (RemoteException e) {
                mRegistered = false;
                mVoiceManagerService = null;
                Log.v(TAG,
                        "Register voice Listener RemoteException = "
                                + e.getMessage());
            }
        } else {
            Log.v(TAG, "App has register voice listener success");
        }
        Log.v(TAG, "Register voice listener end! mRegistered = " + mRegistered);
    }
    
    private void unRegisterVoiceCommand(String pkgName) {
        try {
            int errorid = mVoiceManagerService.unregisterListener(pkgName,
                    mCallback);
            Log.v(TAG, "Unregister voice listener, errorid = " + errorid);
            if (errorid == VoiceCommandListener.VOICE_NO_ERROR) {
                mRegistered = false;
            }
        } catch (RemoteException e) {
            Log.v(TAG,
                    "Unregister error in handler RemoteException = "
                            + e.getMessage());
            mRegistered = false;
            mVoiceManagerService = null;
        }
        Log.v(TAG, "UnRegister voice listener end! mRegistered = "
                + mRegistered);
    }

    // Callback used to notify apps
    private IVoiceCommandListener mCallback = new IVoiceCommandListener.Stub() {

        @Override
        public void onVoiceCommandNotified(int mainAction, int subAction,
                Bundle extraData) {
            Log.i(TAG, "onVoiceCommandNotified(" + mainAction + ", "
                    + subAction + ", " + extraData + ")");
            int result = UNKNOWN;
            switch (mainAction) {
            case VoiceCommandListener.ACTION_MAIN_VOICE_UI:
                switch (subAction) {
                case VoiceCommandListener.ACTION_VOICE_UI_ENABLE:
                    break;
                case VoiceCommandListener.ACTION_VOICE_UI_DISALBE:
                    break;
                case VoiceCommandListener.ACTION_VOICE_UI_START:
                    break;
                case VoiceCommandListener.ACTION_VOICE_UI_STOP:
                    break;
                case VoiceCommandListener.ACTION_VOICE_UI_NOTIFY:
                    if (extraData != null) {
                        printExtraData(extraData);
                        result = extraData.getInt(
                                VoiceCommandListener.ACTION_EXTRA_RESULT,
                                UNKNOWN);
                        if (result == VoiceCommandListener.ACTION_EXTRA_RESULT_SUCCESS) {
                            mCommandId = extraData
                                    .getInt(VoiceCommandListener.ACTION_EXTRA_RESULT_INFO,
                                            UNKNOWN);
                            mHandler.sendEmptyMessage(VOICE_COMMAND_RECEIVE);
                        }
                    }
                    break;
                default:
                    break;
                }
                break;
            case VoiceCommandListener.ACTION_MAIN_VOICE_COMMON:
                switch (subAction) {
                case VoiceCommandListener.ACTION_VOICE_COMMON_KEYWORD:
                    if (extraData != null) {
                        printExtraData(extraData);
                        result = extraData.getInt(
                                VoiceCommandListener.ACTION_EXTRA_RESULT,
                                UNKNOWN);
                        if (result == VoiceCommandListener.ACTION_EXTRA_RESULT_SUCCESS) {
                            mKeywords = extraData
                                    .getStringArray(VoiceCommandListener.ACTION_EXTRA_RESULT_INFO);
                        }
                    }
                    break;
                case VoiceCommandListener.ACTION_VOICE_COMMON_PROCSTATE:
                    if (extraData != null) {
                        printExtraData(extraData);
                        result = extraData.getInt(
                                VoiceCommandListener.ACTION_EXTRA_RESULT,
                                UNKNOWN);
                        if (result == VoiceCommandListener.ACTION_EXTRA_RESULT_SUCCESS) {
                            boolean enabled = extraData
                                    .getBoolean(
                                            VoiceCommandListener.ACTION_EXTRA_RESULT_INFO,
                                            false);
                            mVoiceValue = (enabled ? VOICE_ON : VOICE_OFF);
                            mHandler.sendEmptyMessage(VOICE_VALUE_UPDATED);
                        }
                    }
                    break;
                case VoiceCommandListener.ACTION_VOICE_COMMON_COMMANDPATH:
                    if (extraData != null) {
                        printExtraData(extraData);
                        result = extraData.getInt(
                                VoiceCommandListener.ACTION_EXTRA_RESULT,
                                UNKNOWN);
                        if (result == VoiceCommandListener.ACTION_EXTRA_RESULT_SUCCESS) {
                            mCommandPathKeywords = extraData
                                    .getString(VoiceCommandListener.ACTION_EXTRA_RESULT_INFO);
                            notifyCachePathIfNeed();
                        }
                    }
                    break;
                default:
                    break;
                }
                break;
            default:
                break;
            }
        }
    };
    private IVoiceCommandManagerService mVoiceManagerService;

    private void startVoiceCommand(String pkgName, int mainAction,
            int subAction, Bundle extra) {
        Log.i(TAG, "startVoiceCommand(" + pkgName + ", " + mainAction + ", "
                + subAction + ", " + extra + ")");
        if (mVoiceManagerService != null) {
            try {
                mVoiceManagerService.sendCommand(pkgName, mainAction,
                        subAction, extra);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
        Log.d(TAG, "startVoiceCommand() mVoiceManagerService="
                + mVoiceManagerService);
    }

    private void printExtraData(Bundle extraData) {
        Set<String> keys = extraData.keySet();
        for (String key : keys) {
            Log.d(TAG,
                    "printExtraData() extraData[" + key + "]="
                            + extraData.get(key));
        }
    }

    public void setVoiceSublistShow(boolean show) {
        mSwitchSublistShow = show;
    }

    public void playVoiceCommandById(int commandId) {
        Log.d(TAG, "playVoiceCommandById commandId = " + commandId);
        mVoiceCommandId = commandId;
        mHandler.sendEmptyMessage(PLAY_VOICE_COMMAND);
    }

    private void release() {
        mHandler.removeMessages(USER_GUIDE_UPDATED);
        mHandler.removeMessages(VOICE_VALUE_UPDATED);
        mHandler.removeMessages(VOICE_COMMAND_RECEIVE);
        mHandler.removeMessages(PLAY_VOICE_COMMAND);
        if (mVoiceCaptureSound != null) {
            mVoiceCaptureSound.stop(mVoiceCaptureStreamId);
            mVoiceCaptureSound.unload(mVoiceCaptureSoundId);
        }

    }

    private boolean isVoiceEnabled() {
        // Here we check current value, not preference value.
        boolean enabled = false;
        SettingChecker mSettingChecker = ((Camera) mContext)
                .getSettingChecker();
        if (mSettingChecker != null
                && VoiceManager.VOICE_ON
                        .equals(mSettingChecker
                                .getSettingCurrentValue(SettingChecker.ROW_SETTING_VOICE))) {
            enabled = true;
        }
        return enabled;
    }
    
    public String[] getVoiceEntryValues() {
        return mKeywords;
    }
    
    public void releaseSoundPool() {
        if (mVoiceCaptureSound != null) {
            mVoiceCaptureSound.release();
            mVoiceCaptureSound = null;
        }
    }
    public void unBindVoiceService() {
        if (mVoiceManagerService != null) {
            mContext.unbindService(mVoiceSerConnection);
            mRegistered = false;
            mVoiceManagerService = null;
        }
    }
}
