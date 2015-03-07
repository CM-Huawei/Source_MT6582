/*
 * Copyright (C) 2013 The Android Open Source Project
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
 * limitations under the License
 */

package com.android.incallui;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;

import com.android.services.telephony.common.AudioMode;
import com.android.services.telephony.common.Call;
import com.android.services.telephony.common.DualtalkCallInfo;
import com.android.services.telephony.common.ICallCommandService;
import com.android.services.telephony.common.ICallHandlerService;
import com.android.services.telephony.common.VTManagerParams;
import com.android.services.telephony.common.VTSettingParams;
import com.mediatek.incallui.VoiceCommandUIUtils;
import com.mediatek.incallui.ext.ExtensionManager;
import com.mediatek.incallui.vt.VTInCallScreenFlags;
import com.mediatek.incallui.vt.VTManagerLocal;

import java.util.AbstractMap;
import java.util.List;
import java.util.Map;

/**
 * Service used to listen for call state changes.
 */
public class CallHandlerService extends Service {

    private final static String TAG = CallHandlerService.class.getSimpleName();

    private static final int ON_UPDATE_CALL = 1;
    private static final int ON_UPDATE_MULTI_CALL = 2;
    private static final int ON_UPDATE_CALL_WITH_TEXT_RESPONSES = 3;
    private static final int ON_AUDIO_MODE = 4;
    private static final int ON_SUPPORTED_AUDIO_MODE = 5;
    private static final int ON_DISCONNECT_CALL = 6;
    private static final int ON_BRING_TO_FOREGROUND = 7;
    private static final int ON_POST_CHAR_WAIT = 8;
    private static final int ON_START = 9;
    private static final int ON_DESTROY = 10;

    /// M: unuse Google code:@{
    /*
    private static final int LARGEST_MSG_ID = ON_DESTROY;
    */
    ///@}


    private CallList mCallList;
    private Handler mMainHandler;
    private Object mHandlerInitLock = new Object();
    private InCallPresenter mInCallPresenter;
    private AudioModeProvider mAudioModeProvider;
    private boolean mServiceStarted = false;

    @Override
    public void onCreate() {
        Log.i(TAG, "onCreate");
        super.onCreate();

        synchronized(mHandlerInitLock) {
            if (mMainHandler == null) {
                mMainHandler = new MainHandler();
            }
        }
        /// M: Plug-in @{
        ExtensionManager.getInstance().initPlugin(getApplicationContext());
        /// @}
    }

    @Override
    public void onDestroy() {
        Log.i(TAG, "onDestroy");

        // onDestroy will get called when:
        // 1) there are no more calls
        // 2) the client (TeleService) crashes.
        //
        // Because onDestroy is not sequenced with calls to CallHandlerService binder,
        // we cannot know which is happening.
        // Thats okay since in both cases we want to end all calls and let the UI know it can tear
        // itself down when it's ready. Start the destruction sequence.

        /// M: For ALPS01271355 @{
        // CallHandlerServiceProxy will unbindService() just after call onDisconnect(),
        // but onDisconnect() is a binder call(may take some time).
        // If onDisconnect() comes after unbind, mServiceStarted will be set as false,
        // which causes ignoring message of ON_DISCONNECT_CALL in executeMessage().
        // So here we give 200ms make sure the message will not be ignored.
        // Origin Code:
        // mMainHandler.sendMessage(mMainHandler.obtainMessage(ON_DESTROY));
        mMainHandler.sendMessageDelayed(mMainHandler.obtainMessage(ON_DESTROY), 200);
        /// @}
    }


    @Override
    public IBinder onBind(Intent intent) {
        Log.i(TAG, "onBind");
        return mBinder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        Log.i(TAG, "onUnbind");

        // Returning true here means we get called on rebind, which is a feature we do not need.
        // Return false so that all reconnections happen with a call to onBind().
        return false;
    }

    private final ICallHandlerService.Stub mBinder = new ICallHandlerService.Stub() {

        @Override
        public void startCallService(ICallCommandService service) {
            try {
                Log.d(TAG, "startCallService: " + service.toString());

                mMainHandler.sendMessage(mMainHandler.obtainMessage(ON_START, service));
            } catch (Exception e) {
                Log.e(TAG, "Error processing setCallCommandservice() call", e);
            }
        }

        @Override
        public void onDisconnect(Call call) {
            try {
                Log.i(TAG, "onDisconnected: " + call);
                mMainHandler.sendMessage(mMainHandler.obtainMessage(ON_DISCONNECT_CALL, call));
            } catch (Exception e) {
                Log.e(TAG, "Error processing onDisconnect() call.", e);
            }
        }

        @Override
        public void onIncoming(Call call, List<String> textResponses) {
            try {
                Log.i(TAG, "onIncomingCall: " + call);
                Map.Entry<Call, List<String>> incomingCall
                        = new AbstractMap.SimpleEntry<Call, List<String>>(call, textResponses);
                mMainHandler.sendMessage(mMainHandler.obtainMessage(
                        ON_UPDATE_CALL_WITH_TEXT_RESPONSES, incomingCall));
            } catch (Exception e) {
                Log.e(TAG, "Error processing onIncoming() call.", e);
            }
        }

        @Override
        public void onUpdate(List<Call> calls) {
            try {
                Log.i(TAG, "onUpdate: " + calls);
                mMainHandler.sendMessage(mMainHandler.obtainMessage(ON_UPDATE_MULTI_CALL, calls));
            } catch (Exception e) {
                Log.e(TAG, "Error processing onUpdate() call.", e);
            }
        }

        @Override
        public void onAudioModeChange(int mode, boolean muted) {
            try {
                Log.i(TAG, "onAudioModeChange : " +
                        AudioMode.toString(mode));
                mMainHandler.sendMessage(mMainHandler.obtainMessage(ON_AUDIO_MODE, mode,
                            muted ? 1 : 0, null));
            } catch (Exception e) {
                Log.e(TAG, "Error processing onAudioModeChange() call.", e);
            }
        }

        @Override
        public void onSupportedAudioModeChange(int modeMask) {
            try {
                Log.i(TAG, "onSupportedAudioModeChange : " +
                        AudioMode.toString(modeMask));
                mMainHandler.sendMessage(mMainHandler.obtainMessage(ON_SUPPORTED_AUDIO_MODE,
                        modeMask, 0, null));
            } catch (Exception e) {
                Log.e(TAG, "Error processing onSupportedAudioModeChange() call.", e);
            }
        }

        @Override
        public void bringToForeground(boolean showDialpad) {
            mMainHandler.sendMessage(mMainHandler.obtainMessage(ON_BRING_TO_FOREGROUND,
                    showDialpad ? 1 : 0, 0));
        }

        @Override
        public void onPostDialWait(int callId, String chars) {
            mMainHandler.sendMessage(mMainHandler.obtainMessage(ON_POST_CHAR_WAIT, callId, 0,
                    chars));
        }

        // ---------------- MTK --------------------
        public void onVTOpen() {

        }

        public void onVTReady() {

        }

        public void onVTConnected() {

        }

        public void onUpdateRecordState(final int state, final int customValue) {
            try {
                Log.i(TAG, "state = " + state + "customValue = " + customValue);
                mMainHandler.sendMessage(mMainHandler.obtainMessage(ON_UPDATE_RECORD_STATE, state,
                        customValue));
            } catch (Exception e) {
                Log.e(TAG, "Error processing onUpdateRecordState().", e);
            }
        }

        public void onStorageFull() {
            try {
                Log.i(TAG, "onStorageFull");
                mMainHandler.sendMessage(mMainHandler.obtainMessage(ON_STORAGE_FULL));
            } catch (Exception e) {
                Log.e(TAG, "Error processing onStorageFull().", e);
            }
        }

        public void onSuppServiceFailed(String message) {
            try {
                Log.i(TAG, "onSuppServiceFailed");
                mMainHandler.sendMessage(mMainHandler
                        .obtainMessage(ON_SUPP_SERVICE_FAILED, message));
            } catch (Exception e) {
                Log.e(TAG, "Error processing onSuppServiceFailed().", e);
            }
        }

        public void onVTStateChanged(int msgVT) {
            try {
                Log.i(TAG, "onVTStateChanged()... state = " + msgVT);
                mMainHandler.sendMessage(mMainHandler.obtainMessage(ON_VT_STATE_CHANGE, msgVT));
            } catch (Exception e) {
                Log.e(TAG, "Error processing onVTStateChanged().", e);
            }
        }

        public void pushVTSettingParams(VTSettingParams params, Bitmap bitmap) {
            try {
                Log.i(TAG, "pushVTSettingParams()...");
                VTInCallScreenFlags.getInstance().getVTSettingParams((VTSettingParams)params);
                VTInCallScreenFlags.getInstance().mVTReplacePeerBitmap = bitmap;
            } catch (Exception e) {
                Log.e(TAG, "Error processing pushVTSettingParams().", e);
            }
        }

        /**
         * here we just do some UI-unrelated operation, so no need send message to mMainHandler.
         * Note: if we want to do some UI-related operation based on this "notification", we should do them in mMainHandler.
         */
        public void dialVTCallSuccess() {
            try {
                Log.i(TAG, "dialVTCallSuccess()...");
                VTInCallScreenFlags.getInstance().updateVTHideMeForMO();
                mMainHandler.sendMessage(mMainHandler.obtainMessage(VT_DIAL_CALL_SUCCESS));
            } catch (Exception e) {
                Log.e(TAG, "Error processing dialVTCallSuccess().", e);
            }
        }

        public void answerVTCallPre() {
            try {
                Log.i(TAG, "answerVTCallPre()...");
                VTInCallScreenFlags.getInstance().updateVTHideMeForMT();
                mMainHandler.sendMessage(mMainHandler.obtainMessage(VT_ANSWER_CALL_PRE));
            } catch (Exception e) {
                Log.e(TAG, "Error processing answerVTCallPre().", e);
            }
        }

        public void pushVTManagerParams(VTManagerParams params) {
            // because Binder will catch except from binder, so need do thing in our thread.
            try {
                Log.i(TAG, "pushVTManagerParams()...");
                mMainHandler.sendMessage(mMainHandler.obtainMessage(PUSH_VT_MANAGER_PARAMS, params));
            } catch (Exception e) {
                Log.e(TAG, "Error processing pushVTManagerParams().", e);
            }
        }

        @Override
        public void acceptIncomingCallByVoiceCommand() {
            try {
                Log.i(TAG, "acceptIncomingCallByVoiceCommand()...");
                mMainHandler.sendMessage(mMainHandler.obtainMessage(ON_VOICE_UI_COMMAND_ACCEPT_INCOMING_CALL));
            } catch (Exception e) {
                Log.e(TAG, "Error processing acceptIncomingCallByVoiceCommand()", e);
            }
        }

        @Override
        public void rejectIncomingCallByVoiceCommand() {
            try {
                Log.i(TAG, "rejectIncomingCallByVoiceCommand()...");
                mMainHandler.sendMessage(mMainHandler.obtainMessage(ON_VOICE_UI_COMMAND_REJECT_INCOMING_CALL));
            } catch (Exception e) {
                Log.e(TAG, "Error processing rejectIncomingCallByVoiceCommand()", e);
            }
        }

        @Override
        public void receiveVoiceCommandNotificationMessage(String message) {
            try {
                Log.i(TAG, "receiveVoiceCommandNotificationMessage()... message = " + message);
                mMainHandler.sendMessage(
                        mMainHandler.obtainMessage(ON_VOICE_UI_COMMAND_RECEIVE_NOTIFICATION_MESSAGE, message));
            } catch (Exception e) {
                Log.e(TAG, "Error processing receiveVoiceCommandNotificationMessage()", e);
            }
        }

        @Override
        public void onPhoneRaised() {
            try {
                Log.i(TAG, "onPhoneRaised()");
                mMainHandler.sendMessage(
                        mMainHandler.obtainMessage(ON_VOICE_UI_COMMAND_PHONE_DETECT_RAISED));
            } catch (Exception e) {
                Log.e(TAG, "Error processing onPhoneRaised()", e);
            }
        }

        @Override
        public void updateDualtalkCallStatus(DualtalkCallInfo info) {
            Log.e(TAG, "[updateDualtalkCallStatus], info:" + info);
            mMainHandler.sendMessage(mMainHandler.obtainMessage(ON_DUALTALK_INFO_UPDATE, info));
        }
    };

    private void doStart(ICallCommandService service) {
        Log.i(TAG, "doStart");

        // always setup the new callcommandservice
        CallCommandClient.getInstance().setService(service);

        // If we have a new service when one is already started, we can continue
        // using the service that we already have.
        if (mServiceStarted) {
            Log.i(TAG, "Starting a service before another one is completed");
            doStop();
        }

        mCallList = CallList.getInstance();
        mAudioModeProvider = AudioModeProvider.getInstance();
        mInCallPresenter = InCallPresenter.getInstance();

        mInCallPresenter.setUp(getApplicationContext(), mCallList, mAudioModeProvider);

        mServiceStarted = true;

        /// M: For ALPS01271355 @{
        if (mMainHandler.hasMessages(ON_DESTROY)){
            mMainHandler.removeMessages(ON_DESTROY);
        }
        /// @}
    }

    public void doStop() {
        Log.i(TAG, "doStop");

        if (!mServiceStarted) {
            return;
        }

        mServiceStarted = false;

        // We are disconnected, clear the call list so that UI can start
        // tearing itself down.
        mCallList.clearOnDisconnect();
        mCallList = null;

        mInCallPresenter.tearDown();
        mInCallPresenter = null;
        mAudioModeProvider = null;
    }

    /**
     * Handles messages from the service so that they get executed on the main thread, where they
     * can interact with UI.
     */
    private class MainHandler extends Handler {
        MainHandler() {
            super(getApplicationContext().getMainLooper(), null, true);
        }

        @Override
        public void handleMessage(Message msg) {
            executeMessage(msg);
        }
    }

    private void executeMessage(Message msg) {
        if (msg.what > LARGEST_MSG_ID) {
            // If you got here, you may have added a new message and forgotten to
            // update LARGEST_MSG_ID
            Log.wtf(TAG, "Cannot handle message larger than LARGEST_MSG_ID.");
        }

         if (!mServiceStarted && (msg.what != ON_START)) {
            Log.i(TAG, "System not initialized.  Ignoring message: " + msg.what);
            return;
        }

        Log.d(TAG, "executeMessage " + msg.what);
        /// M:@{
        handleMTKMessage(msg);
        /// @}
        switch (msg.what) {
            case ON_UPDATE_CALL:
                Log.i(TAG, "ON_UPDATE_CALL: " + msg.obj);
                mCallList.onUpdate((Call) msg.obj);
                break;
            case ON_UPDATE_MULTI_CALL:
                Log.i(TAG, "ON_UPDATE_MULTI_CALL: " + msg.obj);
                mCallList.onUpdate((List<Call>) msg.obj);
                break;
            case ON_UPDATE_CALL_WITH_TEXT_RESPONSES:
                AbstractMap.SimpleEntry<Call, List<String>> entry
                        = (AbstractMap.SimpleEntry<Call, List<String>>) msg.obj;
                Log.i(TAG, "ON_INCOMING_CALL: " + entry.getKey());
                mCallList.onIncoming(entry.getKey(), entry.getValue());
                break;
            case ON_DISCONNECT_CALL:
                Log.i(TAG, "ON_DISCONNECT_CALL: " + msg.obj);
                /// M: for VT auto Dropback@{
                // need to use the context
                // mCallList.onDisconnect((Call) msg.obj);
                mCallList.onDisconnect(getApplicationContext(), (Call) msg.obj);
                /// @}
                break;
            case ON_POST_CHAR_WAIT:
                mInCallPresenter.onPostDialCharWait(msg.arg1, (String) msg.obj);
                break;
            case ON_AUDIO_MODE:
                Log.i(TAG, "ON_AUDIO_MODE: " +
                        AudioMode.toString(msg.arg1) + ", muted (" + (msg.arg2 == 1) + ")");
                mAudioModeProvider.onAudioModeChange(msg.arg1, msg.arg2 == 1);
                break;
            case ON_SUPPORTED_AUDIO_MODE:
                Log.i(TAG, "ON_SUPPORTED_AUDIO_MODE: " + AudioMode.toString(
                        msg.arg1));

                mAudioModeProvider.onSupportedAudioModeChange(msg.arg1);
                break;
            case ON_BRING_TO_FOREGROUND:
                Log.i(TAG, "ON_BRING_TO_FOREGROUND" + msg.arg1);
                if (mInCallPresenter != null) {
                    mInCallPresenter.bringToForeground(msg.arg1 != 0);
                }
                break;
            case ON_START:
                doStart((ICallCommandService) msg.obj);
                break;
            case ON_DESTROY:
                doStop();
                break;
            default:
                break;
        }
    }

    //-------------------------------------MTK-----------------------------
    private static final int ON_UPDATE_RECORD_STATE = 11;
    private static final int ON_STORAGE_FULL = 12;
    private static final int ON_SUPP_SERVICE_FAILED = 13;
    private static final int ON_VT_STATE_CHANGE = 14;
    private static final int PUSH_VT_MANAGER_PARAMS = 15;
    private static final int ON_VOICE_UI_COMMAND_ACCEPT_INCOMING_CALL = 16;
    private static final int ON_VOICE_UI_COMMAND_REJECT_INCOMING_CALL = 17;
    private static final int ON_VOICE_UI_COMMAND_RECEIVE_NOTIFICATION_MESSAGE = 18;
    private static final int ON_VOICE_UI_COMMAND_PHONE_DETECT_RAISED = 19;
    private static final int ON_DUALTALK_INFO_UPDATE = 20;
    private static final int VT_ANSWER_CALL_PRE = 21;
    private static final int VT_DIAL_CALL_SUCCESS = 22;
    private static final int LARGEST_MSG_ID = VT_DIAL_CALL_SUCCESS;
    private boolean handleMTKMessage(Message msg) {
        switch (msg.what) {
            case ON_STORAGE_FULL:
                mCallList.onStorageFull();
                return true;
            case ON_UPDATE_RECORD_STATE:
                Log.i(TAG, "msg.arg1 = " + msg.arg1 + "msg.arg2 = " + msg.arg2);
                mCallList.onUpdateRecordState(msg.arg1, msg.arg2);
                return true;
            case ON_SUPP_SERVICE_FAILED:
                Log.i(TAG, "msg.obj = " + (String)msg.obj);
                mCallList.onSuppServiceFailed((String)msg.obj);
                return true;
            case ON_VT_STATE_CHANGE:
                Log.i(TAG, "msg.obj = " + msg.obj);
                VTManagerLocal.getInstance().onVTStateChanged((Integer)msg.obj);
                return true;
            case PUSH_VT_MANAGER_PARAMS:
                Log.i(TAG, "ON_VT_DATA_UPDATE");
                VTManagerLocal.getInstance().getVTManagerParams((VTManagerParams)msg.obj);
                return true;
            case VT_ANSWER_CALL_PRE:
                Log.i(TAG, "- handle - VT_ANSWER_CALL_PRE.");
                VTManagerLocal.getInstance().answerVTCallPre();
                return true;
            case VT_DIAL_CALL_SUCCESS:
                Log.i(TAG, "- handle - VT_DIAL_CALL_SUCCESS.");
                VTManagerLocal.getInstance().dialVTCallSuccess();
                return true;
            case ON_VOICE_UI_COMMAND_ACCEPT_INCOMING_CALL:
                Log.i(TAG,"ON_VOICE_UI_COMMAND_ACCEPT_INCOMING_CALL");
                VoiceCommandUIUtils.getInstance().acceptIncomingCallByVoiceCommand();
                return true;
            case ON_VOICE_UI_COMMAND_REJECT_INCOMING_CALL:
                Log.i(TAG, "ON_VOICE_UI_COMMAND_REJECT_INCOMING_CALL");
                VoiceCommandUIUtils.getInstance().rejectIncomingCallByVoiceCommand();
                return true;
            case ON_VOICE_UI_COMMAND_RECEIVE_NOTIFICATION_MESSAGE:
                Log.i(TAG, "ON_VOICE_UI_COMMAND_RECEIVE_NOTIFICATION_MESSAGE");
                VoiceCommandUIUtils.getInstance().receiveVoiceCommandNotificationMessage((String)msg.obj);
                return true;
            case ON_VOICE_UI_COMMAND_PHONE_DETECT_RAISED:
                Log.i(TAG, "ON_VOICE_UI_COMMAND_PHONE_DETECT_RAISED");
                VoiceCommandUIUtils.getInstance().onPhoneRaised();
                return true;
            case ON_DUALTALK_INFO_UPDATE:
                Log.i(TAG, "msg.obj = " + msg.obj);
                InCallPresenter.getInstance().updateDualtalkCallInfo((DualtalkCallInfo)msg.obj);
                return true;
            default:
                break;
        }
        return false;
    }
}
