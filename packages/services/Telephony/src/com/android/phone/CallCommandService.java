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

package com.android.phone;

import java.util.List;

import android.bluetooth.IBluetoothHeadsetPhone;
import android.content.Context;
import android.os.RemoteException;
import android.os.SystemProperties;
import android.util.Log;
import android.view.Surface;

import com.android.internal.telephony.CallManager;
import com.android.internal.telephony.PhoneConstants;
import com.android.phone.CallModeler.CallResult;
import com.android.phone.NotificationMgr.StatusBarHelper;
import com.android.services.telephony.common.Call;
import com.android.services.telephony.common.DualtalkCallInfo;
import com.android.services.telephony.common.ICallCommandService;


import com.mediatek.phone.recording.PhoneRecorder;

import com.mediatek.phone.DualTalkUtils;
import com.mediatek.phone.PhoneRaiseDetector;
import com.mediatek.phone.VoiceCommandHandler;
import com.mediatek.phone.PhoneFeatureConstants.FeatureOption;
import com.mediatek.phone.recording.PhoneRecorderHandler;
import com.mediatek.phone.vt.VTInCallScreenFlags;
import com.mediatek.phone.vt.VTManagerWrapper;
import com.mediatek.phone.wrapper.PhoneWrapper;
import com.mediatek.vt.VTManager;

/**
 * Service interface used by in-call ui to control phone calls using commands exposed as methods.
 * Instances of this class are handed to in-call UI via CallMonitorService.
 */
class CallCommandService extends ICallCommandService.Stub {
    private static final String TAG = CallCommandService.class.getSimpleName();
    private static final boolean DBG =
            (PhoneGlobals.DBG_LEVEL >= 1) && (SystemProperties.getInt("ro.debuggable", 0) == 1);

    private final Context mContext;
    private final CallManager mCallManager;
    private final CallModeler mCallModeler;
    private final DTMFTonePlayer mDtmfTonePlayer;
    private final AudioRouter mAudioRouter;

    public CallCommandService(Context context, CallManager callManager, CallModeler callModeler,
            DTMFTonePlayer dtmfTonePlayer, AudioRouter audioRouter) {
        mContext = context;
        mCallManager = callManager;
        mCallModeler = callModeler;
        mDtmfTonePlayer = dtmfTonePlayer;
        mAudioRouter = audioRouter;
    }

    /**
     * TODO: Add a confirmation callback parameter.
     */
    @Override
    public void answerCall(int callId) {
        try {
            CallResult result = mCallModeler.getCallWithId(callId);
            if (result != null) {
                /// M: Modified for MTK feature. @{
                // Google code:
                /*
                PhoneUtils.answerCall(result.getConnection().getCall());
                */
                PhoneUtils.internalAnswerCall();
                /// @}
            }
        } catch (Exception e) {
            Log.e(TAG, "Error during answerCall().", e);
        }
    }

    /**
     * TODO: Add a confirmation callback parameter.
     */
    @Override
    public void rejectCall(Call call, boolean rejectWithMessage, String message) {
        try {
            int callId = Call.INVALID_CALL_ID;
            String phoneNumber = "";
            /// M: for Gemini && ALPS01239930 @{
            int slotId = 0;
            /// @}
            if (call != null) {
                callId = call.getCallId();
                phoneNumber = call.getNumber();
                /// M: for Gemini && ALPS01239930 @{
                slotId = call.getSlotId();
                /// @}
            }
            CallResult result = mCallModeler.getCallWithId(callId);

            if (result != null) {
                phoneNumber = result.getConnection().getAddress();

                Log.v(TAG, "Hanging up");
                PhoneUtils.hangupRingingCall(result.getConnection().getCall());
            }

            if (rejectWithMessage && !phoneNumber.isEmpty()) {
                /// M: for Gemini && ALPS01239930 @{
                // sometimes we call this when call is aready ended, so we get slot Id directly from here
                // RejectWithTextMessageManager.rejectCallWithMessage(phoneNumber, message);
                RejectWithTextMessageManager.rejectCallWithMessage(phoneNumber, message, slotId);
                /// @}
            }
        } catch (Exception e) {
            Log.e(TAG, "Error during rejectCall().", e);
        }
    }

    @Override
    public void disconnectCall(int callId) {
        try {
            CallResult result = mCallModeler.getCallWithId(callId);

            if (result != null) {
                if (DBG) Log.d(TAG, "disconnectCall " + result.getCall() +" isVT="+result.getConnection().isVideo());
                int state = result.getCall().getState();
                if (Call.State.ACTIVE == state ||
                        Call.State.ONHOLD == state ||
                        Call.State.DIALING == state) {
                    result.getConnection().getCall().hangup();
                } else if (Call.State.CONFERENCED == state) {
                    result.getConnection().hangup();
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error during disconnectCall().", e);
        }
    }

    @Override
    public void separateCall(int callId) {
        try {
            CallResult result = mCallModeler.getCallWithId(callId);
            if (DBG) Log.d(TAG, "disconnectCall " + result.getCall());

            if (result != null) {
                int state = result.getCall().getState();
                if (Call.State.CONFERENCED == state) {
                    result.getConnection().separate();
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error trying to separate call.", e);
        }
    }

    @Override
    public void hold(int callId, boolean hold) {
        /// M: Modified for Dualtalk. @{
        // Google code:
        /*
        try {
            CallResult result = mCallModeler.getCallWithId(callId);
            if (result != null) {
                int state = result.getCall().getState();
                if (hold && Call.State.ACTIVE == state) {
                    PhoneUtils.switchHoldingAndActive(mCallManager.getFirstActiveBgCall());
                } else if (!hold && Call.State.ONHOLD == state) {
                    PhoneUtils.switchHoldingAndActive(result.getConnection().getCall());
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error trying to place call on hold.", e);
        }
        */
        PhoneUtils.onHoldClick();
        /// @}
    }

    @Override
    public void merge() {
        if (PhoneUtils.okToMergeCalls(mCallManager)) {
            PhoneUtils.mergeCalls(mCallManager);
        }
    }

    @Override
    public void addCall() {
        // start new call checks okToAddCall() already
        PhoneUtils.startNewCall(mCallManager);
    }


    @Override
    public void swap() {
        if (!PhoneUtils.okToSwapCalls(mCallManager)) {
            // TODO: throw an error instead?
            return;
        }

        // Swap the fg and bg calls.
        // In the future we may provides some way for user to choose among
        // multiple background calls, for now, always act on the first background calll.
        /// M: for DualTalk @{
        // original code
        /*
        PhoneUtils.switchHoldingAndActive(mCallManager.getFirstActiveBgCall());

        final PhoneGlobals mApp = PhoneGlobals.getInstance();

        // If we have a valid BluetoothPhoneService then since CDMA network or
        // Telephony FW does not send us information on which caller got swapped
        // we need to update the second call active state in BluetoothPhoneService internally
        if (mCallManager.getBgPhone().getPhoneType() == PhoneConstants.PHONE_TYPE_CDMA) {
            final IBluetoothHeadsetPhone btPhone = mApp.getBluetoothPhoneService();
            if (btPhone != null) {
                try {
                    btPhone.cdmaSwapSecondCallState();
                } catch (RemoteException e) {
                    Log.e(TAG, Log.getStackTraceString(new Throwable()));
                }
            }
        }
        */
        PhoneUtils.onSwapClick();
        /// @}
    }

    @Override
    public void mute(boolean onOff) {
        try {
            PhoneUtils.setMute(onOff);
        } catch (Exception e) {
            Log.e(TAG, "Error during mute().", e);
        }
    }

    @Override
    public void speaker(boolean onOff) {
        try {
            PhoneUtils.turnOnSpeaker(mContext, onOff, true);
        } catch (Exception e) {
            Log.e(TAG, "Error during speaker().", e);
        }
    }

    @Override
    public void playDtmfTone(char digit, boolean timedShortTone) {
        try {
            mDtmfTonePlayer.playDtmfTone(digit, timedShortTone);
        } catch (Exception e) {
            Log.e(TAG, "Error playing DTMF tone.", e);
        }
    }

    @Override
    public void stopDtmfTone() {
        try {
            mDtmfTonePlayer.stopDtmfTone();
        } catch (Exception e) {
            Log.e(TAG, "Error stopping DTMF tone.", e);
        }
    }

    @Override
    public void setAudioMode(int mode) {
        try {
            mAudioRouter.setAudioMode(mode);
        } catch (Exception e) {
            Log.e(TAG, "Error setting the audio mode.", e);
        }

        /// M: ALPS01264365 stop voice commmand if user tap speaker @{
        if (FeatureOption.MTK_VOICE_UI_SUPPORT) {
            Log.e(TAG,"setAudioMode(), stopPhoneDetect()");
            PhoneRaiseDetector.getInstance().stopPhoneDetect();
        }
        /// @}
    }

    @Override
    public void postDialCancel(int callId) throws RemoteException {
        final CallResult result = mCallModeler.getCallWithId(callId);
        if (result != null) {
            result.getConnection().cancelPostDial();
        }
    }

    @Override
    public void postDialWaitContinue(int callId) throws RemoteException {
        final CallResult result = mCallModeler.getCallWithId(callId);
        if (result != null) {
            result.getConnection().proceedAfterWaitChar();
        }
    }

    @Override
    public void setSystemBarNavigationEnabled(boolean enable) {
        try {
            final StatusBarHelper statusBarHelper = PhoneGlobals.getInstance().notificationMgr.
                    statusBarHelper;
            statusBarHelper.enableSystemBarNavigation(enable);
            statusBarHelper.enableExpandedView(enable);
        } catch (Exception e) {
            Log.e(TAG, "Error enabling or disabling system bar navigation", e);
        }
    }

    // ---------------- MTK ------------------
    @Override
    public void setVTOpen(int slotId) {
        VTManagerWrapper.getInstance().setVTOpen(mContext, slotId);
    }

    public void setVTReady() {
        VTManagerWrapper.getInstance().setVTReady();
    }

    public void setVTConnected() {
        VTManagerWrapper.getInstance().setVTConnected();
    }

    public void setVTClose() {
        VTManagerWrapper.getInstance().setVTClose();
    }
    
    public void onDisconnected() {
        VTManagerWrapper.getInstance().onDisconnected();
    }

    public void setVTVisible(boolean isVisible) {
        VTManagerWrapper.getInstance().setVTVisible(isVisible);
    }

    public void setDisplay(Surface local, Surface peer) {
        log("setDisplay local=" + local + " peer=" + peer);
        VTManagerWrapper.getInstance().setDisplay(local, peer);
    }

    public void incomingVTCall(int flag) {

    }

    public void switchDisplaySurface() {

    }

    public void setLocalView(int videoType, String path) {

    }

    public void setNightMode(boolean isOnNight) {
        VTManagerWrapper.getInstance().setNightMode(isOnNight);
    }
    
    public void setVideoQuality(int quality){
        VTManagerWrapper.getInstance().setVideoQuality(quality);
    }

    /**
     * For DM lock feature
     */
    public void lockPeerVideo() {
        log("lockPeerVideo()...");
        VTManager.getInstance().lockPeerVideo();
    }

    /**
     * For DM lock feature
     */
    public void startVtRecording(int type, long maxSize) {
        log("start vt recording");
        PhoneRecorderHandler.getInstance().startVideoRecord(type, maxSize,
                Constants.PHONE_RECORDING_VIDEO_CALL_CUSTOM_VALUE);
    }

    public void stopRecording() {
        PhoneRecorderHandler.getInstance().stopRecording();
    }

    public void unlockPeerVideo() {
        log("unlockPeerVideo()...");
        VTManager.getInstance().unlockPeerVideo();
    }

    public void onUserInput(String input) {
        VTManagerWrapper.getInstance().onUserInput(input);
    }

    public void enlargeDisplaySurface(boolean isEnlarge) {

    }

    public void switchCamera(){
        VTManagerWrapper.getInstance().switchCamera();
    }

    public void setColorEffect(String colorEffect) {
        VTManagerWrapper.getInstance().setColorEffect(colorEffect);
    }

    public void decZoom() {
        VTManagerWrapper.getInstance().decZoom();
    }

    public void incZoom() {
        VTManagerWrapper.getInstance().incZoom();
    }

    public void incBrightness() {
        VTManagerWrapper.getInstance().incBrightness();
    }

    public void decBrightness() {
        VTManagerWrapper.getInstance().decBrightness();
    }

    public void incContrast() {
        VTManagerWrapper.getInstance().incContrast();
    }

    public void decContrast() {
        VTManagerWrapper.getInstance().decContrast();
    }

    public void updatePicToReplaceLocalVideo() {
        VTManagerWrapper.getInstance().updatePicToReplaceLocalVideo();
    }

    public boolean canIncZoom() {
        return VTManagerWrapper.getInstance().getVTManager().canIncZoom();
    }

    public boolean canDecZoom() {
        return VTManagerWrapper.getInstance().getVTManager().canDecZoom();
    }

    public boolean canIncBrightness() {
        return VTManagerWrapper.getInstance().getVTManager().canIncBrightness();
    }

    public boolean canDecBrightness() {
        return VTManagerWrapper.getInstance().getVTManager().canDecBrightness();
    }
    

    public boolean canIncContrast() {
        return VTManagerWrapper.getInstance().getVTManager().canIncContrast();
    }

    public boolean canDecContrast() {
        return VTManagerWrapper.getInstance().getVTManager().canDecContrast();
    }
    
    public void savePeerPhoto() {
        VTManagerWrapper.getInstance().savePeerPhoto();
    }

    public void hideLocal(boolean on) {
        VTManagerWrapper.getInstance().hideLocal(on);
    }
    
    public boolean isSupportNightMode() {
        return false;
    }

    public boolean isNightMode() {
        return false;
    }
    

    public int getVTState() {
        return 0;
    }
    
    public int getVideoQuality() {
        return 1;
    }

    public String getColorEffect() {
        return VTManagerWrapper.getInstance().getVTManager().getColorEffect();
    }

    public List<String> getSupportedColorEffects() {
        return VTManagerWrapper.getInstance().getVTManager().getSupportedColorEffects();
    }

    public void startVoiceRecording() {
        if (PhoneUtils.okToRecordVoice(mCallManager)) {
            PhoneRecorderHandler.getInstance().startVoiceRecord(
                    Constants.PHONE_RECORDING_VOICE_CALL_CUSTOM_VALUE);
        } else {
            log("Can not start voice recording!!");
        }
    }

    public void hangupAllCalls() {
        PhoneUtils.hangupAllCalls();
    }

    public void hangupHoldingCall() {
        PhoneUtils.hangupHoldingCall(mCallManager.getFirstActiveBgCall());
    }

    public void acceptVtCallWithVoiceOnly() {
        PhoneUtils.acceptVtCallWithVoiceOnly(mCallManager, mCallManager.getFirstActiveRingingCall());
    }

    public void setVTVoiceAnswerRelated(boolean vtVoiceAnswer, String vtVoiceAnswerPhoneNumber) {
        VTInCallScreenFlags.getInstance().mVTVoiceAnswer = vtVoiceAnswer;
        VTInCallScreenFlags.getInstance().mVTVoiceAnswerPhoneNumber = vtVoiceAnswerPhoneNumber;
    }

    public void hangupActiveAndAnswerWaiting() {
        PhoneUtils.hangup(mCallManager.getActiveFgCall());
    }

    public void explicitCallTransfer() {
        PhoneWrapper.explicitCallTransfer(PhoneGlobals.getInstance().phone);
    }

    public void setUpVoiceCommandService() {
        log("setUpVoiceCommandService()...");
        if (FeatureOption.MTK_VOICE_UI_SUPPORT) {
            VoiceCommandHandler.getInstance().setUpVoiceCommandService();
            PhoneRaiseDetector.getInstance().initDetector();
        }
    }

    public void startVoiceCommand() {
        log("startVoiceCommand()...");
        if (FeatureOption.MTK_VOICE_UI_SUPPORT) {
            if (VoiceCommandHandler.getInstance().isValidCondition()) {
                VoiceCommandHandler.getInstance().startVoiceCommand();
            }
        }
    }

    public void silenceRinger() {
        PhoneGlobals.getInstance().notifier.silenceRinger();
    }

    public void stopVoiceCommand() {
        log("stopVoiceCommand()");
        if (FeatureOption.MTK_VOICE_UI_SUPPORT) {
            VoiceCommandHandler.getInstance().stopVoiceCommand();
            PhoneRaiseDetector.getInstance().stopPhoneDetect();
        }
    }

    public void clearVoiceCommandHandler() {
        log("clearVoiceCommandHandler()");
        if (FeatureOption.MTK_VOICE_UI_SUPPORT) {
            VoiceCommandHandler.getInstance().clear();
            PhoneRaiseDetector.getInstance().release();
        }
    }

    /**
     * when only one incoming call, should startvoicecommand();
     */
    public void updateVoiceCommand() {
        log("updateVoiceCommand()");
        if (FeatureOption.MTK_VOICE_UI_SUPPORT) {
            if (VoiceCommandHandler.isValidCondition()) {
                VoiceCommandHandler.getInstance().startVoiceCommand();
            } else {
                VoiceCommandHandler.getInstance().stopVoiceCommand();
            }
            if (!PhoneRaiseDetector.isValidCondition()) {
                PhoneRaiseDetector.getInstance().stopPhoneDetect();
            }
        }
    }

    public void acceptIncomingCallByVoiceCommand(int callId) {
        log("acceptIncomingCallByVoiceCommand callId: " + callId);
        if (callId == Call.INVALID_CALL_ID) {
            return;
        }
        answerCall(callId);
        PhoneGlobals mApp = PhoneGlobals.getInstance();
        WiredHeadsetManager wiredHeadsetManager = mApp.getWiredHeadsetManager();
        BluetoothManager bluetoothManager = mApp.getBluetoothManager();
        boolean isHeadsetPlugged = false;
        boolean isBluetoothHeadsetAudioOn = false;
        boolean isSpeakerOn = PhoneUtils.isSpeakerOn(mApp.getApplicationContext());
        if (wiredHeadsetManager != null) {
            isHeadsetPlugged = wiredHeadsetManager.isHeadsetPlugged();
        }
        if (bluetoothManager != null) {
            isBluetoothHeadsetAudioOn = bluetoothManager.isBluetoothHeadsetAudioOn();
        }

        log("isHeadsetPlugged: " + isHeadsetPlugged + ", isSpeakerOn: " + isSpeakerOn
                + ", isBluetoothHeadsetAudioOn: " + isBluetoothHeadsetAudioOn);

        if (!(isHeadsetPlugged || isBluetoothHeadsetAudioOn || isSpeakerOn)) {
            log("acceptIncomingCallByVoiceCommand(), turn on speaker");
            PhoneGlobals.getInstance().getAudioRouter().setSpeaker(true);
            PhoneUtils.turnOnSpeaker(mApp, true, true, true);
            PhoneRaiseDetector.getInstance().startPhoneDetect();
        }
    }

    private void log(String msg) {
        Log.i(TAG, msg);
    }

    public void restartRinger() {
        PhoneGlobals.getInstance().notifier.restartRinger();
    }

    public void secondaryPhotoClicked() {
        PhoneUtils.swapCallsByCondition();
    }

    public void secondaryHoldPhotoClicked() {
        PhoneUtils.secondaryHoldPhotoClicked();
    }

    public void switchRingtoneForDualTalk(){
        PhoneGlobals.getInstance().notifier.switchRingtoneForDualTalk();
    }

    public void switchCalls(){
        DualTalkUtils.getInstance().switchCalls();
    }

    /**
     * Indication when InCall ui shows (onResume) or disappear(onPause) to user
     */
    public void onUiShowing(boolean show) {
        log("onUiShowing, show = "+ show);
        if (show) {
            PhoneUtils.restoreMuteState();
        }
    }

    /**
     * For smartBook, update screen.
     * @param onOff
     */
    public void updatePowerForSmartBook(boolean onOff) {
        log("updatePowerForSmartBook()... onOff: " + onOff);
        PhoneGlobals.getInstance().updatePowerForSmartBook(onOff);
    }
}
