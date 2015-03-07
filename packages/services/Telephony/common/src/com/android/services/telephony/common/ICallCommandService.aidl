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
 * limitations under the License.
 */

package com.android.services.telephony.common;

import android.view.Surface;
import com.android.services.telephony.common.Call;

/**
 * Service implemented by TelephonyService and used by In-call UI to control
 * phone calls on the device.
 * TODO: Move this out of opt/telephony and into opt/call or similar. This interface
 *       makes sense even without the telephony layer (think VOIP).
 */
oneway interface ICallCommandService {

    /**
     * Answer a ringing call.
     */
    void answerCall(int callId);

    /**
     * Reject a ringing call.
     */
    void rejectCall(in Call call, boolean rejectWithMessage, String message);

    /**
     * Disconnect an active call.
     */
    void disconnectCall(int callId);

    /**
     * Separate a conference call.
     */
    void separateCall(int callId);

    /**
     * Place call on hold.
     */
    void hold(int callId, boolean hold);

    /**
     * Merge foreground and background calls.
     */
    void merge();

    /**
     * Swap foreground and background calls.
     */
    void swap();

    /**
     * Add another call.
     * TODO(klp): Should this go through the service at all?
     *            It could just as easily call dialer directly.
     */
    void addCall();

    /**
     * Mute the phone.
     */
    void mute(boolean onOff);

    /**
     * Turn on or off speaker.
     * TODO(klp): Remove in favor of setAudioMode
     */
    void speaker(boolean onOff);

    /**
     * Start playing DTMF tone for the specified digit.
     */
    void playDtmfTone(char digit, boolean timedShortTone);

    /**
     * Stop playing DTMF tone for the specified digit.
     */
    void stopDtmfTone();

    /**
     * Sets the audio mode for the active phone call.
     * {@see AudioMode}
     */
    void setAudioMode(int mode);

    void postDialCancel(int callId);

    void postDialWaitContinue(int callId);

    /**
     * Enables or disables navigation using the system bar, and also prevents the
     * notification shade from being dragged down.
     * Hides or shows the home, recent and back buttons in the navigation bar if the
     * device has soft navigation buttons.
     */
    void setSystemBarNavigationEnabled(boolean enable);


    // --------------------- MTK --------------------

    void setVTOpen(int slotId);

    void setVTReady();

    void setVTConnected();

    void setVTClose();

    void onDisconnected();

    void setDisplay(in Surface local,in Surface peer);

    void switchDisplaySurface();

    void enlargeDisplaySurface(boolean isEnlarge);

    void switchCamera();

    void savePeerPhoto();

    void hideLocal(boolean on);

    void setLocalView(int videoType, String path);

    void setColorEffect(String value);

    void setNightMode(boolean isOn);

    void setVideoQuality(int quality);

    void setVTVisible(boolean isVisible);

    void onUserInput(String input);

    void lockPeerVideo();

    void unlockPeerVideo();

    void startVtRecording(int type, long maxSize);

    void startVoiceRecording();

    void stopRecording();

    void incomingVTCall(int flag);

    void hangupAllCalls();

    void hangupHoldingCall();

    void hangupActiveAndAnswerWaiting();

    void explicitCallTransfer();

    void decZoom();

    void incZoom();

    void incBrightness();

    void decBrightness();

    void incContrast();

    void decContrast();

    void updatePicToReplaceLocalVideo();

    void setUpVoiceCommandService();

    void stopVoiceCommand();

    void clearVoiceCommandHandler();

    void acceptIncomingCallByVoiceCommand(int callId);

    void updateVoiceCommand();

    void silenceRinger();

    void restartRinger();

    void acceptVtCallWithVoiceOnly();

    void setVTVoiceAnswerRelated(boolean vtVoiceAnswer, String vtVoiceAnswerPhoneNumber);

    void secondaryPhotoClicked();

    void secondaryHoldPhotoClicked();

    void switchRingtoneForDualTalk();

    void switchCalls();

    /**
     * Indication when InCall ui shows (onResume) or disappear(onPause) to user
     */
    void onUiShowing(boolean show);

    /**
     * update the screen for smartbook.
     */
    void updatePowerForSmartBook(boolean onOff);
}
