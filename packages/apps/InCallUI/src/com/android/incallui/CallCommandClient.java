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

import java.util.List;

import android.os.RemoteException;
import android.view.Surface;


import com.android.services.telephony.common.AudioMode;
import com.android.services.telephony.common.ICallCommandService;
import com.android.services.telephony.common.Call;
import com.mediatek.incallui.InCallUtils;

/**
 * Main interface for phone related commands.
 */
public class CallCommandClient {

    private static CallCommandClient sInstance;

    public static synchronized CallCommandClient getInstance() {
        if (sInstance == null) {
            sInstance = new CallCommandClient();
        }
        return sInstance;
    }

    private ICallCommandService mCommandService;

    private CallCommandClient() {
    }

    public void setService(ICallCommandService service) {
        mCommandService = service;
    }

    public void answerCall(int callId) {
        Log.i(this, "answerCall: " + callId);
        if (mCommandService == null) {
            Log.e(this, "Cannot answer call; CallCommandService == null");
            return;
        }
        try {
            mCommandService.answerCall(callId);
        } catch (RemoteException e) {
            Log.e(this, "Error answering call.", e);
        }
    }

    public void rejectCall(Call call, boolean rejectWithMessage, String message) {
        Log.i(this, "rejectCall: " + call.getCallId() +
                ", with rejectMessage? " + rejectWithMessage);
        if (mCommandService == null) {
            Log.e(this, "Cannot reject call; CallCommandService == null");
            return;
        }
        try {
            mCommandService.rejectCall(call, rejectWithMessage, message);
        } catch (RemoteException e) {
            Log.e(this, "Error rejecting call.", e);
        }
    }

    public void disconnectCall(int callId) {
        Log.i(this, "disconnect Call: " + callId);
        if (mCommandService == null) {
            Log.e(this, "Cannot disconnect call; CallCommandService == null");
            return;
        }
        try {
            mCommandService.disconnectCall(callId);
        } catch (RemoteException e) {
            Log.e(this, "Error disconnecting call.", e);
        }
    }

    public void separateCall(int callId) {
        Log.i(this, "separate Call: " + callId);
        if (mCommandService == null) {
            Log.e(this, "Cannot separate call; CallCommandService == null");
            return;
        }
        try {
            mCommandService.separateCall(callId);
        } catch (RemoteException e) {
            Log.e(this, "Error separating call.", e);
        }
    }

    public void mute(boolean onOff) {
        Log.i(this, "mute: " + onOff);
        if (mCommandService == null) {
            Log.e(this, "Cannot mute call; CallCommandService == null");
            return;
        }
        try {
            mCommandService.mute(onOff);
        } catch (RemoteException e) {
            Log.e(this, "Error muting phone.", e);
        }
    }

    public void hold(int callId, boolean onOff) {
        Log.i(this, "hold call(" + onOff + "): " + callId);
        if (mCommandService == null) {
            Log.e(this, "Cannot hold call; CallCommandService == null");
            return;
        }
        try {
            mCommandService.hold(callId, onOff);
        } catch (RemoteException e) {
            Log.e(this, "Error holding call.", e);
        }
    }

    public void merge() {
        Log.i(this, "merge calls");
        if (mCommandService == null) {
            Log.e(this, "Cannot merge call; CallCommandService == null");
            return;
        }
        try {
            mCommandService.merge();
        } catch (RemoteException e) {
            Log.e(this, "Error merging calls.", e);
        }
    }

    public void swap() {
        Log.i(this, "swap active/hold calls");
        if (mCommandService == null) {
            Log.e(this, "Cannot swap call; CallCommandService == null");
            return;
        }
        try {
            mCommandService.swap();
        } catch (RemoteException e) {
            Log.e(this, "Error merging calls.", e);
        }
    }

    public void addCall() {
        Log.i(this, "add a new call");
        if (mCommandService == null) {
            Log.e(this, "Cannot add call; CallCommandService == null");
            return;
        }
        try {
            mCommandService.addCall();
        } catch (RemoteException e) {
            Log.e(this, "Error merging calls.", e);
        }
    }

    public void setAudioMode(int mode) {
        Log.i(this, "Set Audio Mode: " + AudioMode.toString(mode));
        if (mCommandService == null) {
            Log.e(this, "Cannot set audio mode; CallCommandService == null");
            return;
        }
        try {
            mCommandService.setAudioMode(mode);
        } catch (RemoteException e) {
            Log.e(this, "Error setting speaker.", e);
        }
    }

    public void playDtmfTone(char digit, boolean timedShortTone) {
        if (mCommandService == null) {
            Log.e(this, "Cannot start dtmf tone; CallCommandService == null");
            return;
        }
        try {
            Log.v(this, "Sending dtmf tone " + digit);
            mCommandService.playDtmfTone(digit, timedShortTone);
        } catch (RemoteException e) {
            Log.e(this, "Error setting speaker.", e);
        }

    }

    public void stopDtmfTone() {
        if (mCommandService == null) {
            Log.e(this, "Cannot stop dtmf tone; CallCommandService == null");
            return;
        }
        try {
            Log.v(this, "Stop dtmf tone ");
            mCommandService.stopDtmfTone();
        } catch (RemoteException e) {
            Log.e(this, "Error setting speaker.", e);
        }
    }

    public void postDialWaitContinue(int callId) {
        if (mCommandService == null) {
            Log.e(this, "Cannot postDialWaitContinue(); CallCommandService == null");
            return;
        }
        try {
            Log.v(this, "postDialWaitContinue()");
            mCommandService.postDialWaitContinue(callId);
        } catch (RemoteException e) {
            Log.e(this, "Error on postDialWaitContinue().", e);
        }
    }

    public void postDialCancel(int callId) {
        if (mCommandService == null) {
            Log.e(this, "Cannot postDialCancel(); CallCommandService == null");
            return;
        }
        try {
            Log.v(this, "postDialCancel()");
            mCommandService.postDialCancel(callId);
        } catch (RemoteException e) {
            Log.e(this, "Error on postDialCancel().", e);
        }
    }

    public void setSystemBarNavigationEnabled(boolean enable) {
        if (mCommandService == null) {
            Log.e(this, "Cannot setSystemBarNavigationEnabled(); CallCommandService == null");
            return;
        }
        try {
            Log.v(this, "setSystemBarNavigationEnabled() enabled = " + enable);
            mCommandService.setSystemBarNavigationEnabled(enable);
        } catch (RemoteException e) {
            Log.d(this, "Error on setSystemBarNavigationEnabled().");
        }
    }

    // ------------------------ MTK ---------------------------
    public void setVTOpen(int slotId) {
        if (mCommandService == null) {
            Log.e(this, "Cannot setVTOpen(); CallCommandService == null");
            return;
        }
        try {
            Log.v(this, "setVTOpen() slotId = " + slotId);
            mCommandService.setVTOpen(slotId);
        } catch (RemoteException e) {
            Log.d(this, "Error on setVTOpen().");
        }
    }

    public void setVTReady() {
        if (mCommandService == null) {
            Log.e(this, "Cannot setVTReady(); CallCommandService == null");
            return;
        }
        try {
            Log.v(this, "setVTReady()");
            mCommandService.setVTReady();
        } catch (RemoteException e) {
            Log.d(this, "Error on setVTReady().");
        }
    }

    public void setVTConnected() {
        if (mCommandService == null) {
            Log.e(this, "Cannot setVTConnected(); CallCommandService == null");
            return;
        }
        try {
            Log.v(this, "setVTConnected()");
            mCommandService.setVTConnected();
        } catch (RemoteException e) {
            Log.d(this, "Error on setVTConnected().");
        }
    }

    public void setVTClose() {
        if (mCommandService == null) {
            Log.e(this, "Cannot setVTClose(); CallCommandService == null");
            return;
        }
        try {
            Log.v(this, "setVTClose()");
            mCommandService.setVTClose();
        } catch (RemoteException e) {
            Log.d(this, "Error on setVTClose().");
        }
    }

    public void onDisconnected() {

    }

    public void setVTVisible(boolean isVisible) {
        if (mCommandService == null) {
            Log.e(this, "Cannot setVTVisible(); CallCommandService == null");
            return;
        }
        try {
            Log.v(this, "setVTVisible() isVisible = " + isVisible);
            mCommandService.setVTVisible(isVisible);
        } catch (RemoteException e) {
            Log.d(this, "Error on setVTVisible().");
        }
    }

    public void setDisplay(Surface local, Surface peer) {
        if (mCommandService == null) {
            Log.e(this, "Cannot setDisplay(); CallCommandService == null");
            return;
        }
        try {
            Log.v(this, "setDisplay() local = " + local + ", peer = " + peer);
            mCommandService.setDisplay(local, peer);
        } catch (RemoteException e) {
            Log.d(this, "Error on setDisplay().");
        }
    }

    public void switchDisplaySurface() {

    }

    public void setNightMode(boolean isOnNight) {
        if (mCommandService == null) {
            Log.e(this, "Cannot setNightMode(); CallCommandService == null");
            return;
        }
        try {
            Log.v(this, "setNightMode() isOnNight = " + isOnNight);
            mCommandService.setNightMode(isOnNight);
        } catch (RemoteException e) {
            Log.d(this, "Error on setNightMode().");
        }
    }

    public void setVideoQuality(int quality) {
        if (mCommandService == null) {
            Log.e(this, "Cannot setVideoQuality(); CallCommandService == null");
            return;
        }
        try {
            Log.v(this, "setVideoQuality() quality = " + quality);
            mCommandService.setVideoQuality(quality);
        } catch (RemoteException e) {
            Log.d(this, "Error on setVideoQuality().");
        }
    }

    public void lockPeerVideo() {
        if (mCommandService == null) {
            Log.e(this, "Cannot lockPeerVideo(); CallCommandService == null");
            return;
        }

        try {
            Log.d(this, "lockPeerVideo()... ");
            mCommandService.lockPeerVideo();
        } catch (RemoteException e) {
            Log.d(this, "Error on lockPeerVideo().");
        }
    }

    public void unlockPeerVideo() {
        if (mCommandService == null) {
            Log.e(this, "Cannot unlockPeerVideo(); CallCommandService == null");
            return;
        }

        try {
            Log.d(this, "unlockPeerVideo()... ");
            mCommandService.unlockPeerVideo();
        } catch (RemoteException e) {
            Log.d(this, "Error on unlockPeerVideo().");
        }
    }

    public boolean canIncZoom() {
        return false;
    }

    public boolean canDecZoom() {
        return false;
    }
    
    public boolean canIncBrightness() {
        return false;
    }

    public boolean canDecBrightness() {
        return false;
    }
    
    public boolean canIncContrast() {
        return false;
    }

    public boolean canDecContrast() {
        return false;
    }
    
    public void decZoom() {
        if (mCommandService == null) {
            Log.e(this, "Cannot decZoom(); CallCommandService == null");
            return;
        }
        try {
            Log.v(this, "decZoom()");
            mCommandService.decZoom();
        } catch (RemoteException e) {
            Log.d(this, "Error on decZoom().");
        }
    }

    public void incZoom() {
        if (mCommandService == null) {
            Log.e(this, "Cannot incZoom(); CallCommandService == null");
            return;
        }
        try {
            Log.v(this, "incZoom()");
            mCommandService.incZoom();
        } catch (RemoteException e) {
            Log.d(this, "Error on incZoom().");
        }
    }
    
    public void incBrightness() {
        if (mCommandService == null) {
            Log.e(this, "Cannot incBrightness(); CallCommandService == null");
            return;
        }
        try {
            Log.v(this, "incBrightness()");
            mCommandService.incBrightness();
        } catch (RemoteException e) {
            Log.d(this, "Error on incBrightness().");
        }
    }

    public void decBrightness() {
        if (mCommandService == null) {
            Log.e(this, "Cannot decBrightness(); CallCommandService == null");
            return;
        }
        try {
            Log.v(this, "decBrightness()");
            mCommandService.decBrightness();
        } catch (RemoteException e) {
            Log.d(this, "Error on decBrightness().");
        }
    }

    public void incContrast() {
        if (mCommandService == null) {
            Log.e(this, "Cannot incContrast(); CallCommandService == null");
            return;
        }
        try {
            Log.v(this, "incContrast()");
            mCommandService.incContrast();
        } catch (RemoteException e) {
            Log.d(this, "Error on incContrast().");
        }
    }

    public void decContrast() {
        if (mCommandService == null) {
            Log.e(this, "Cannot decContrast(); CallCommandService == null");
            return;
        }
        try {
            Log.v(this, "decContrast()");
            mCommandService.decContrast();
        } catch (RemoteException e) {
            Log.d(this, "Error on decContrast().");
        }
    }

    public boolean isSupportNightMode() {
        return false;
    }

    public boolean isNightMode() {
        return false;
    }

    public void setLocalView(int videoType, String path) {

    }

    public int getVTState() {
        return 0;
    }

    public int getVideoQuality() {
        return 1;
    }

    public void setColorEffect(String colorEffect) {
        if (mCommandService == null) {
            Log.e(this, "Cannot incZoom(); CallCommandService == null");
            return;
        }
        try {
            Log.v(this, "setColorEffect()... colorEffect: " + colorEffect);
            mCommandService.setColorEffect(colorEffect);
        } catch (RemoteException e) {
            Log.d(this, "Error on setColorEffect().");
        }
    }

    public String getColorEffect() {
        return "";
    }
    

    public List<String> getSupportedColorEffects() {
         return null;
    }

    public void startVtRecording(int type, long maxSize) {
        Log.d(this, "startVtRecording");
        if (mCommandService == null) {
            Log.e(this, "Cannot start vt record; CallCommandService == null");
            return;
        }

        try {
            mCommandService.startVtRecording(type, maxSize);
        } catch (RemoteException e) {
            Log.e(this, "Error start voice record.", e);
        }
    }

    public void stopRecording() {
        Log.i(this, "stopRecording");
        if (mCommandService == null) {
            Log.e(this, "Cannot stop record; CallCommandService == null");
            return;
        }
        try {
            mCommandService.stopRecording();
        } catch (RemoteException e) {
            Log.e(this, "Error start voice record.", e);
        }
    }

    public void startVoiceRecording(){
        Log.i(this, "startVoiceRecording");
        if (mCommandService == null) {
            Log.e(this, "Cannot start voice record; CallCommandService == null");
            return;
        }
        try {
            mCommandService.startVoiceRecording();
        } catch (RemoteException e) {
            Log.e(this, "Error start voice record.", e);
        }
    }

    public void hangupAllCalls(){
        Log.i(this, "hangupAllCalls");
        if (mCommandService == null) {
            Log.e(this, "Cannot hangup all calls; CallCommandService == null");
            return;
        }
        try {
            mCommandService.hangupAllCalls();
        } catch (RemoteException e) {
            Log.e(this, "Error hangup all calls.", e);
        }
    }

    public void hangupHoldingCall(){
        Log.i(this, "hangupHoldingCall");
        if (mCommandService == null) {
            Log.e(this, "Cannot hangup holding calls; CallCommandService == null");
            return;
        }
        try {
            mCommandService.hangupHoldingCall();
        } catch (RemoteException e) {
            Log.e(this, "Error hangup holding calls.", e);
        }
    }

    public void acceptVtCallWithVoiceOnly(){
        Log.i(this, "acceptVtCallWithVoiceOnly()...");
        if (mCommandService == null) {
            Log.e(this, "Cannot call acceptVtCallWithVoiceOnly(); CallCommandService == null");
            return;
        }
        try {
            mCommandService.acceptVtCallWithVoiceOnly();
        } catch (RemoteException e) {
            Log.e(this, "Error when acceptVtCallWithVoiceOnly().", e);
        }
    }

    public void setVTVoiceAnswerRelated(boolean vtVoiceAnswer, String vtVoiceAnswerPhoneNumber){
        Log.i(this, "setVTVoiceAnswerRelated()... vtVoiceAnswer / vtVoiceAnswerPhoneNumber: " + vtVoiceAnswer + " / "
                + vtVoiceAnswerPhoneNumber);
        if (mCommandService == null) {
            Log.e(this, "Cannot call setVTVoiceAnswerRelated(); CallCommandService == null");
            return;
        }
        try {
            mCommandService.setVTVoiceAnswerRelated(vtVoiceAnswer, vtVoiceAnswerPhoneNumber);
        } catch (RemoteException e) {
            Log.e(this, "Error when setVTVoiceAnswerRelated().", e);
        }
    }

    public void hangupActiveAndAnswerWaiting(){
        Log.i(this, "hangupActiveAndAnswerWaiting");
        if (mCommandService == null) {
            Log.e(this, "Cannot hangup active call; CallCommandService == null");
            return;
        }
        try {
            mCommandService.hangupActiveAndAnswerWaiting();
        } catch (RemoteException e) {
            Log.e(this, "Error hangup active call.", e);
        }
    }

    public void explicitCallTransfer(){
        Log.i(this, "explicitCallTransfer");
        if (mCommandService == null) {
            Log.e(this, "Cannot explicit call transfer; CallCommandService == null");
            return;
        }
        try {
            mCommandService.explicitCallTransfer();
        } catch (RemoteException e) {
            Log.e(this, "Error explicit call transfer.", e);
        }
    }
    
    public void switchCamera() {
        if (mCommandService == null) {
            Log.e(this, "Cannot switchCamera(); CallCommandService == null");
            return;
        }
        try {
            Log.v(this, "switchCamera()");
            mCommandService.switchCamera();
        } catch (RemoteException e) {
            Log.d(this, "Error on switchCamera().");
        }
    }
    
    public void savePeerPhoto() {
        if (mCommandService == null) {
            Log.e(this, "Cannot savePeerPhoto(); CallCommandService == null");
            return;
        }
        try {
            Log.v(this, "savePeerPhoto()");
            mCommandService.savePeerPhoto();
        } catch (RemoteException e) {
            Log.d(this, "Error on savePeerPhoto().");
        }
    }

    public void hideLocal(boolean on) {
        if (mCommandService == null) {
            Log.e(this, "Cannot hideLocal(); CallCommandService == null");
            return;
        }
        try {
            Log.v(this, "hideLocal()");
            mCommandService.hideLocal(on);
        } catch (RemoteException e) {
            Log.d(this, "Error on hideLocal().");
        }
    }

    public void updatePicToReplaceLocalVideo() {
        if (mCommandService == null) {
            Log.e(this, "Cannot updatePicToReplaceLocalVideo(); CallCommandService == null");
            return;
        }
        try {
            Log.v(this, "hideLocal()");
            mCommandService.updatePicToReplaceLocalVideo();
        } catch (RemoteException e) {
            Log.d(this, "Error on updatePicToReplaceLocalVideo().");
        }
    }

    public ICallCommandService getService() {
        return mCommandService;
    }

    public void setUpVoiceCommandService() {
        if (mCommandService == null) {
            Log.e(this, "setUpVoiceCommandService(); CallCommandService == null");
            return;
        }
        try {
            Log.v(this, "setUpVoiceCommandService()");
            mCommandService.setUpVoiceCommandService();
        } catch (RemoteException e) {
            Log.d(this, "Error on setUpVoiceCommandService().");
        }
    }

    public void stopVoiceCommand() {
        if (mCommandService == null) {
            Log.e(this, "stopVoiceCommand(); CallCommandService == null");
            return;
        }
        try {
            Log.v(this, "stopVoiceCommand()");
            mCommandService.stopVoiceCommand();
        } catch (RemoteException e) {
            Log.d(this, "Error on stopVoiceCommand()().");
        }
    }

    public void clearVoiceCommandHandler() {
        if (mCommandService == null) {
            Log.e(this, "clearVoiceCommandHandler(); CallCommandService == null");
            return;
        }
        try {
            Log.v(this, "clearVoiceCommandHandler()");
            mCommandService.clearVoiceCommandHandler();
        } catch (RemoteException e) {
            Log.d(this, "Error on clearVoiceCommandHandler().");
        }
    }

    public void acceptIncomingCallByVoiceCommand(int callId) {
        if (mCommandService == null) {
            Log.e(this, "acceptIncomingCallByVoiceCommand(); CallCommandService == null");
            return;
        }
        try {
            Log.v(this, "acceptIncomingCallByVoiceCommand()");
            mCommandService.acceptIncomingCallByVoiceCommand(callId);
        } catch (RemoteException e) {
            Log.d(this, "Error on acceptIncomingCallByVoiceCommand().");
        }
    }

    public void updateVoiceCommand() {
        if (mCommandService == null) {
            Log.e(this, "updateVoiceCommand(); CallCommandService == null");
            return;
        }
        try {
            Log.v(this, "updateVoiceCommand()");
            mCommandService.updateVoiceCommand();
        } catch (RemoteException e) {
            Log.d(this, "Error on updateVoiceCommand().");
        }
    }

    public void silenceRinger() {
        if (mCommandService == null) {
            Log.e(this, "Cannot silenceRinger(); CallCommandService == null");
            return;
        }

        try {
            Log.d(this, "silenceRinger()");
            mCommandService.silenceRinger();
        } catch (RemoteException e) {
            Log.d(this, "Error on silenceRinger().");
        }
    }

    public void restartRinger() {
        if (mCommandService == null) {
            Log.e(this, "Cannot restartRinger(); CallCommandService == null");
            return;
        }

        try {
            Log.d(this, "restartRinger()");
            mCommandService.restartRinger();
        } catch (RemoteException e) {
            Log.d(this, "Error on restartRinger().");
        }
    }

    public void onUserInput(String input) {
        if (mCommandService == null) {
            Log.e(this, "Cannot onUserInput(); CallCommandService == null");
            return;
        }

        try {
            Log.d(this, "onUserInput()... input: " + input);
            mCommandService.onUserInput(input);
        } catch (RemoteException e) {
            Log.d(this, "Error on onUserInput().");
        }
    }

    public void secondaryPhotoClicked() {
        if (mCommandService == null) {
            Log.e(this, "Cannot secondaryPhotoClicked(); CallCommandService == null");
            return;
        }

        try {
            Log.d(this, "secondaryPhotoClicked()");
            mCommandService.secondaryPhotoClicked();
        } catch (RemoteException e) {
            Log.d(this, "Error on secondaryPhotoClicked().");
        }
    }

    public void secondaryHoldPhotoClicked() {
        if (mCommandService == null) {
            Log.e(this, "Cannot secondaryHoldPhotoClicked(); CallCommandService == null");
            return;
        }

        try {
            Log.d(this, "secondaryHoldPhotoClicked()");
            mCommandService.secondaryHoldPhotoClicked();
        } catch (RemoteException e) {
            Log.d(this, "Error on secondaryHoldPhotoClicked().");
        }
    }

    public void switchRingtoneForDualTalk() {
        if (mCommandService == null) {
            Log.e(this, "Cannot switchRingtoneForDualTalk(); CallCommandService == null");
            return;
        }

        try {
            Log.d(this, "switchRingtoneForDualTalk()");
            mCommandService.switchRingtoneForDualTalk();
        } catch (RemoteException e) {
            Log.d(this, "Error on switchRingtoneForDualTalk().");
        }
    }

    public void switchCalls() {
        if (mCommandService == null) {
            Log.e(this, "Cannot switchCalls(); CallCommandService == null");
            return;
        }

        try {
            Log.d(this, "switchCalls()");
            mCommandService.switchCalls();
        } catch (RemoteException e) {
            Log.d(this, "Error on switchCalls().");
        }
    }

    /**
     * Indication when InCall ui shows (onResume) or disappear(onPause) to user
     */
    public void onUiShowing(boolean show) {
        /**
         * M: [ALPS01269100]record the UI show status
         * @{
         */
        InCallUtils.onUiShowing(show);
        /** @} */
        if (mCommandService == null) {
            Log.e(this, "Cannot onUiShowing(); CallCommandService == null");
            return;
        }

        try {
            Log.d(this, "onUIShowing, show = "+show);
            mCommandService.onUiShowing(show);
        } catch (RemoteException e) {
            Log.d(this, "Error on onUiShowing");
        }
    }

    /**
     * For smartBook, update screen.
     * @param onOff
     */
    public void updatePowerForSmartBook(boolean onOff) {
        if (mCommandService == null) {
            Log.e(this, "Cannot updatePowerForSmartBook(); CallCommandService == null");
            return;
        }

        try {
            Log.d(this, "updatePowerForSmartBook()... onOff: " + onOff);
            mCommandService.updatePowerForSmartBook(onOff);
        } catch (RemoteException e) {
            Log.d(this, "Error on updatePowerForSmartBook().");
        }
    }
}
