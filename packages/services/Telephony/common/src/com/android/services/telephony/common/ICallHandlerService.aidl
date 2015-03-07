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

import com.android.services.telephony.common.Call;
import com.android.services.telephony.common.DualtalkCallInfo;
import com.android.services.telephony.common.ICallCommandService;
import com.android.services.telephony.common.VTSettingParams;
import com.android.services.telephony.common.VTManagerParams;
import android.graphics.Bitmap;

/**
 * Service implemented by clients that would like to control and know the status
 * of phone calls on the device.
 * TODO: Rename interface.  This not only monitors but controls calls, too. Come
 *       up with a name that doesn't conflict with current CallManager and
 *       CallController classes.
 * TODO: Move this out of opt/telephony and into opt/call or similar. This interface
 *       makes sense even without the telephony layer (think VOIP).
 */
oneway interface ICallHandlerService {

    /**
     * First call made when we are ready to start sending events to the service.
     * Hands a command interface to the CallHandlerService through which
     * the call monitor can control the phone calls.
     */
    void startCallService(ICallCommandService callCommandService);

    /**
     * Called when there is an incoming call.
     */
    void onIncoming(in Call call, in List<String> textReponses);

    /**
     * Called when the state of a call changes.
     */
    void onUpdate(in List<Call> call);

    /**
     * Called when a call disconnects.
     */
    void onDisconnect(in Call call);

    /**
     * Called when the audio mode changes.
     * {@see AudioMode}
     */
    void onAudioModeChange(in int mode, in boolean muted);

    /**
     * Called when the supported audio modes change.
     * {@see AudioMode}
     */
    void onSupportedAudioModeChange(in int modeMask);

    /**
     * Called when the system wants to bring the in-call UI into the foreground.
     */
    void bringToForeground(boolean showDialpad);

    void onPostDialWait(int callId, String remainingChars);

    // -------------------- MTK ----------------------
    void onVTOpen();
    void onVTReady();
    void onVTConnected();
    void onStorageFull();
    void onUpdateRecordState(int state, int customValue);
    void onSuppServiceFailed(String message);
    void pushVTSettingParams(in VTSettingParams params, in Bitmap bitmap);
    void dialVTCallSuccess();
    void answerVTCallPre();
    void onVTStateChanged(int msgVT);
    void pushVTManagerParams(in VTManagerParams params);
    void acceptIncomingCallByVoiceCommand();
    void rejectIncomingCallByVoiceCommand();
    void receiveVoiceCommandNotificationMessage(String message);
    void onPhoneRaised();
    void updateDualtalkCallStatus(in DualtalkCallInfo info);
}
