package com.mediatek.phone;


import com.mediatek.common.voicecommand.IVoiceCommandListener;
import com.mediatek.common.voicecommand.IVoiceCommandManagerService;
import com.mediatek.common.voicecommand.VoiceCommandListener;
import com.mediatek.phone.PhoneFeatureConstants.FeatureOption;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.RemoteException;

import com.android.internal.telephony.Call;
import com.android.phone.PhoneGlobals;
import com.android.phone.R;

/**
 * The Voice UI is used for accepting or rejecting the first incoming call(No
 * other active call exist).
 */
public class VoiceCommandHandler {

    public interface Listener {
        void acceptIncomingCallByVoiceCommand();
        void rejectIncomingCallByVoiceCommand();
        void receiveVoiceCommandNotificationMessage(String message);
    }

    private static final String TAG = "VoiceCommandHandler";

    private static final int VOICE_COMMAND_RESULT_INCOMING_CALL_ACCEPT = 1;
    private static final int VOICE_COMMAND_RESULT_INCOMING_CALL_REJECT = 2;

    private static VoiceCommandHandler sVoiceCommandHandler;

    private Context mContext;
    private Listener mListener;
    private String mNotificationMessage;

    private boolean mIsRegistered = false;
    private IVoiceCommandListener mCallback;
    private IVoiceCommandManagerService mVCmdMgrService;
    private ServiceConnection mVoiceCommandServiceConn = new ServiceConnection(){
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            mVCmdMgrService = IVoiceCommandManagerService.Stub.asInterface(service);
            if (isValidCondition()) {
                startVoiceCommand();
            }
            log("onServiceConnected, init mVCmdMgrService");
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            mVCmdMgrService = null;
            log("onServiceDisconnected, destroy mVCmdMgrService");
        }
    };

    private Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            log("handleMessage(): msg.what = " + msg.what);
            switch (msg.what) {
            case VoiceCommandListener.ACTION_MAIN_VOICE_COMMON:
                handleCommonVoiceCommand(msg.arg1, (Bundle) msg.obj);
                break;
            case VoiceCommandListener.ACTION_MAIN_VOICE_UI:
                handleUIVoiceCommand(msg.arg1, (Bundle) msg.obj);
                break;
            default:
                PhoneLog.w(TAG, "handleMessage(), running in default");
                break;
            }
        }
    };

    private VoiceCommandHandler() {
    }

    public void setListener(Listener listener) {
        mListener = listener;
    }

    public void setUpVoiceCommandService() {
        mContext = PhoneGlobals.getInstance().getApplicationContext();

        mCallback = new IVoiceCommandListener.Stub() {
            @Override
            public void onVoiceCommandNotified(int mainAction, int subAction, Bundle extraData)
                    throws RemoteException {
                log("handleMessage(): mainAction = " + mainAction + ", subAction=" + subAction);
                Message msg = new Message();
                msg.what = mainAction;
                msg.arg1 = subAction;
                msg.obj = extraData;
                // send the voice notified to UI thread.
                mHandler.sendMessage(msg);
            }
        };

        bindVoiceCommandService(mContext);
    }

    public static synchronized VoiceCommandHandler getInstance() {
        if (sVoiceCommandHandler == null) {
            sVoiceCommandHandler = new VoiceCommandHandler();
        }
        return sVoiceCommandHandler;
    }

    private void bindVoiceCommandService(Context context) {
        Intent voiceCmdIntent = new Intent();
        voiceCmdIntent.setAction(VoiceCommandListener.VOICE_SERVICE_ACTION);
        voiceCmdIntent.addCategory(VoiceCommandListener.VOICE_SERVICE_CATEGORY);
        context.bindService(voiceCmdIntent, mVoiceCommandServiceConn, Context.BIND_AUTO_CREATE);
    }

    private void handleCommonVoiceCommand(int subAction, Bundle extraData) {
        log("handleCommonVoiceCommand(): subAction = " + subAction + ", extraData = " + extraData);

        final Context context = mContext;
        if (context == null) {
            log("mContext is null, so return directly.");
            return;
        }

        switch (subAction) {

            case VoiceCommandListener.ACTION_VOICE_COMMON_KEYWORD:
                if (VoiceCommandListener.ACTION_EXTRA_RESULT_SUCCESS
                        == extraData.getInt(VoiceCommandListener.ACTION_EXTRA_RESULT)) {
                    log("handleCommonVoiceCommand(): extraData = ACTION_EXTRA_RESULT_SUCCESS");
                    String[] comments = extraData.getStringArray(VoiceCommandListener.ACTION_EXTRA_RESULT_INFO);
                    if (null != comments && comments.length > 1) {
                        mNotificationMessage =
                            context.getString(R.string.voice_command_notification_ticker, comments[0], comments[1]);
                        PhoneGlobals.getInstance().notificationMgr.showVoiceCommandNotification();
                        log("mNotificationMessage= " + mNotificationMessage +
                                "condition= " + isValidCondition() + ", register = " + isRegistered());
                        if (null != mListener && isValidCondition() && isRegistered()) {
                            mListener.receiveVoiceCommandNotificationMessage(mNotificationMessage);
                        }
                    }
                }
                break;

            default:
                break;
        }
    }

    private void handleUIVoiceCommand(int subAction, Bundle extraData) {
        log("handleUIVoiceCommand(): subAction = " + subAction + ", extraData = " + extraData);

        switch (subAction) {

            case VoiceCommandListener.ACTION_VOICE_UI_START:
                log("handleUIVoiceCommand(), VoiceCommandListener.ACTION_VOICE_UI_START");
                if (VoiceCommandListener.ACTION_EXTRA_RESULT_SUCCESS
                        != extraData.getInt(VoiceCommandListener.ACTION_EXTRA_RESULT)) {
                    log("handleUIVoiceCommand(), ACTION_VOICE_UI_START message's extra data is not SUCCESS");
                    break;
                }
                if (null != mContext) {
                    final String pkgName = mContext.getPackageName();
                    sendVoiceCommand(pkgName,
                            VoiceCommandListener.ACTION_MAIN_VOICE_COMMON,
                            VoiceCommandListener.ACTION_VOICE_COMMON_KEYWORD, null);
                }
                break;

            case VoiceCommandListener.ACTION_VOICE_UI_STOP:
                log("handleUIVoiceCommand(), VoiceCommandListener.ACTION_VOICE_UI_STOP");
                break;

            case VoiceCommandListener.ACTION_VOICE_UI_NOTIFY:
                log("handleUIVoiceCommand(), VoiceCommandListener.ACTION_VOICE_UI_NOTIFY");
                if (VoiceCommandListener.ACTION_EXTRA_RESULT_SUCCESS
                        != extraData.getInt(VoiceCommandListener.ACTION_EXTRA_RESULT)) {
                    log("handleUIVoiceCommand(), ACTION_VOICE_UI_NOTIFY message's extra data is not SUCCESS");
                    break;
                }
                int commandId = extraData.getInt(VoiceCommandListener.ACTION_EXTRA_RESULT_INFO);
                if (VOICE_COMMAND_RESULT_INCOMING_CALL_ACCEPT == commandId) {
                    log("handleUIVoiceCommand(), accept");
                    if (null != mListener) {
                        /// for ALPS01375553, noise voice maybe get ACTION_EXTRA_RESULT_SUCCESS, but the commandID is 0, so
                        // when commandId = 1 , accept successful.
                        stopVoiceCommand();
                        mListener.acceptIncomingCallByVoiceCommand();
                    }
                } else if (VOICE_COMMAND_RESULT_INCOMING_CALL_REJECT == commandId) {
                    log("handleUIVoiceCommand(), reject");
                    if (null != mListener) {
                        /// for ALPS01375553, noise voice maybe get ACTION_EXTRA_RESULT_SUCCESS, but the commandID is 0, so
                        // when commandId = 2 , reject successful.
                        stopVoiceCommand();
                        mListener.rejectIncomingCallByVoiceCommand();
                    }
                } else {
                    log("invalid command id commandId: " + commandId);
                }
                break;

            default:
                break;
        }
    }

    public void startVoiceCommand() {
        log("startVoiceCommand().... mIsRegistered: " + mIsRegistered);
        // if mIsRegistered is true, the voice service had started. do not start voice command.
        if (null == mContext || mVCmdMgrService == null || mIsRegistered) {
            log("mContext is null or voice had started, just return");
            return;
        }

        final String pkgName = mContext.getPackageName();
        registerVoiceCommand(pkgName);
        sendVoiceCommand(pkgName, VoiceCommandListener.ACTION_MAIN_VOICE_UI,
                VoiceCommandListener.ACTION_VOICE_UI_START, null);
    }

    public void stopVoiceCommand() {
        log("stopVoiceCommand()");
        if (null == mContext || mVCmdMgrService == null) {
            log("mContext is null, just return");
            return;
        }
        /// ALPS01337394 set the tip message is null.
        if (null != mListener) {
            mListener.receiveVoiceCommandNotificationMessage(null);
        }
        PhoneGlobals.getInstance().notificationMgr.cancelVoiceCommandNotification();
        final String pkgName = mContext.getPackageName();
        sendVoiceCommand(pkgName, VoiceCommandListener.ACTION_MAIN_VOICE_UI,
                VoiceCommandListener.ACTION_VOICE_UI_STOP, null);
        unRegisterVoiceCommand (pkgName);
        mNotificationMessage = null;
    }

    public void clear() {
        /// M: For ALPS01014340 @{
        // when inCallScreen destroy, the instance will not be GC immediately, so also can receive messages.
        // in handleCommonVoiceCommand(...) will use mContext, JE happened. So need unRegister it here.
        if (mContext != null) {
            final String pkgName = mContext.getPackageName();
            unRegisterVoiceCommand (pkgName);
            mContext.unbindService(mVoiceCommandServiceConn);
        }
        /// @}
        mContext = null;
        //mListener = null;
    }

    public boolean isRegistered() {
        return mIsRegistered;
    }

    public static boolean isValidCondition() {
        Call firstRingCall = null;
        if (DualTalkUtils.isSupportDualTalk()) {
            if (DualTalkUtils.getInstance().hasMultipleRingingCall()) {
                return false;
            } else {
                firstRingCall = DualTalkUtils.getInstance().getFirstActiveRingingCall();
            }
        } else {
            firstRingCall = PhoneGlobals.getInstance().mCM.getFirstActiveRingingCall();
        }
        if (null == firstRingCall) {
            return false;
        }
        return Call.State.INCOMING == firstRingCall.getState()
                && !PhoneGlobals.getInstance().mCM.hasActiveFgCall()
                && !PhoneGlobals.getInstance().mCM.hasActiveBgCall();
    }

    private void sendVoiceCommand(String pkgName, int mainAction, int subAction, Bundle extraData) {
        if (mIsRegistered && mVCmdMgrService != null) {
            try {
                log("sendVoiceCommand: pkgName=" + pkgName + "mainAction=" + mainAction
                        + " subAction=" + subAction + " extraData=" + extraData);
                int errorid = mVCmdMgrService
                        .sendCommand(pkgName, mainAction, subAction, extraData);
                if (errorid != VoiceCommandListener.VOICE_NO_ERROR) {
                    log("Send Command failure");
                } else {
                    log("Send Command success");
                }
            } catch (RemoteException e) {
                mIsRegistered = false;
                mVCmdMgrService = null;
                log("sendCommand RemoteException");
            }
        } else {
            log("App has not register listener can not send command");
        }
    }

    private void registerVoiceCommand(String pkgName) {
        log("mIsRegistered: " + mIsRegistered + ", mVCmdMgrService: " + mVCmdMgrService);
        if (!mIsRegistered && mVCmdMgrService != null) {
            try {
                int errorid = mVCmdMgrService.registerListener(pkgName, mCallback);
                log("registerVoiceCommand: pkgName = " + pkgName + ",errorid = " + errorid);
                if (errorid == VoiceCommandListener.VOICE_NO_ERROR) {
                    mIsRegistered = true;
                } else {
                    log("Register voice Listener failure ");
                }
            } catch (RemoteException e) {
                mVCmdMgrService = null;
                log("Register voice Listener RemoteException = " + e.getMessage());
            }
            log("mIsRegistered: " + mIsRegistered);
            if (!mIsRegistered) {
                PhoneRaiseDetector.getInstance().release();
            }
        } else {
            log("App has register voice listener success");
        }
    }

    private void unRegisterVoiceCommand(String pkgName) {
        if (mVCmdMgrService != null) {
            try {
                int errorid = mVCmdMgrService.unregisterListener(pkgName, mCallback);
                log("Unregister voice listener, errorid = " + errorid);
                if (errorid == VoiceCommandListener.VOICE_NO_ERROR) {
                    mIsRegistered = false;
                    /// M: For ALPS01025518 @{
                    // There may exist unexecuted messages when unRegister, so
                    // should remove those messages after unRegister successfully.
                    mHandler.removeMessages(VoiceCommandListener.ACTION_MAIN_VOICE_COMMON);
                    mHandler.removeMessages(VoiceCommandListener.ACTION_MAIN_VOICE_UI);
                    /// @}
                }
            } catch (RemoteException e) {
                log("Unregister error in handler RemoteException = " + e.getMessage());
                mIsRegistered = false;
                mVCmdMgrService = null;
            }
        }
        log("UnRegister voice listener end!");
    }

    private static void log(String msg) {
        PhoneLog.d(TAG, msg);
    }
}
