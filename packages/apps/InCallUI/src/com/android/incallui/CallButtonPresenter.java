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

import android.content.Context;

import com.android.incallui.AudioModeProvider.AudioModeListener;
import com.android.incallui.CallButtonFragment.InCallMenuState;
import com.android.incallui.InCallPresenter.InCallState;
import com.android.incallui.InCallPresenter.InCallStateListener;
import com.android.incallui.InCallPresenter.IncomingCallListener;
import com.android.incallui.InCallPresenter.PhoneRecorderListener;
import com.android.services.telephony.common.AudioMode;
import com.android.services.telephony.common.Call;
import com.android.services.telephony.common.Call.Capabilities;
import com.mediatek.incallui.ext.ExtensionManager;
import com.mediatek.incallui.recorder.PhoneRecorderUtils;
import com.mediatek.incallui.recorder.PhoneRecorderUtils.RecorderState;
import com.mediatek.incallui.vt.VTInCallScreenFlags;
import com.mediatek.incallui.vt.VTManagerLocal;
import com.mediatek.incallui.vt.VTManagerLocal.VTListener;
import com.mediatek.incallui.wrapper.FeatureOptionWrapper;
import com.mediatek.incallui.InCallUtils;
import com.mediatek.incallui.VoiceCommandUIUtils;
import android.telephony.PhoneNumberUtils;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewConfiguration;

/**
 * Logic for call buttons.
 */
public class CallButtonPresenter extends Presenter<CallButtonPresenter.CallButtonUi>
        implements InCallStateListener, AudioModeListener, IncomingCallListener,
        VoiceCommandUIUtils.PhoneDetectListener, VTListener {

    private Call mCall;

    /// M: design change for ALPS01262892 && ALPS01258249 && ALPS01236444 @{
    // the mute function when add call is not well designed on KK
    // the mute && unmute action should be done only in Telephony 
    // to prevent state confusion, the mute action will move to addCall()

    // MTK delete
    /*
    private boolean mAutomaticallyMuted = false;
    private boolean mPreviousMuteState = false;
     */
    /// @}

    private boolean mShowGenericMerge = false;
    private boolean mShowManageConference = false;

    private InCallState mPreviousState = null;

    public CallButtonPresenter() {
    }

    @Override
    public void onUiReady(CallButtonUi ui) {
        super.onUiReady(ui);

        AudioModeProvider.getInstance().addListener(this);

        // register for call state changes last
        InCallPresenter.getInstance().addListener(this);
        InCallPresenter.getInstance().addIncomingCallListener(this);
        VoiceCommandUIUtils.getInstance().setPhoneDetectListener(this);
        VTManagerLocal.getInstance().addVTListener(this);
    }

    @Override
    public void onUiUnready(CallButtonUi ui) {
        super.onUiUnready(ui);

        InCallPresenter.getInstance().removeListener(this);
        AudioModeProvider.getInstance().removeListener(this);
        InCallPresenter.getInstance().removeIncomingCallListener(this);
        VTManagerLocal.getInstance().removeVTListener(this);
    }

    @Override
    public void onStateChange(InCallState state, CallList callList) {

        Log.d(this, "onStateChange()... state: " + state);
        if (state == InCallState.OUTGOING) {
            mCall = callList.getOutgoingCall();
        } else if (state == InCallState.INCALL) {
            /// M: ALPS01291042, show button depend on primary call.
            // Google code
            /*
            mCall = callList.getActiveOrBackgroundCall();
             */
            mCall = CallCardPresenter.getCallToDisplay(callList, null, false);
            /// @}
            // When connected to voice mail, automatically shows the dialpad.
            // (On previous releases we showed it when in-call shows up, before waiting for
            // OUTGOING.  We may want to do that once we start showing "Voice mail" label on
            // the dialpad too.)
            if (mPreviousState == InCallState.OUTGOING
                    /**
                     * M: [ALPS01268554] PhoneNumberUtils.isVoiceMailNumber() do not support Gemini cases
                     * we should replace it with Gemini version wrapper
                    && mCall != null && PhoneNumberUtils.isVoiceMailNumber(mCall.getNumber())) {
                     */
                    && mCall != null && mCall.getState() == Call.State.ACTIVE && InCallUtils.isVoiceMailNumber(mCall.getNumber(),
                            mCall.getSlotId())) {
                getUi().displayDialpad(true);
            }
        } else if (state == InCallState.INCOMING) {
            getUi().displayDialpad(false);
            mCall = null;
        } else {
            mCall = null;
        }
        updateUi(state, mCall);

        /// M: Plug-in. @{
        ExtensionManager.getInstance().getCallButtonExtension().onStateChange(mCall, callList.getCallMap());
        /// @}

        mPreviousState = state;
    }

    @Override
    public void onIncomingCall(InCallState state, Call call) {
        /// M: for ALPS01255734 @{
        // dismiss all pop up menu when a new call incoming
        getUi().dismissPopupMenu();
        /// @}
        onStateChange(state, CallList.getInstance());
    }

    @Override
    public void onAudioMode(int mode) {
        if (getUi() != null) {
            getUi().setAudio(mode);
        }
    }

    @Override
    public void onSupportedAudioMode(int mask) {
        if (getUi() != null) {
            getUi().setSupportedAudio(mask);
        }
    }

    @Override
    public void onMute(boolean muted) {
        if (getUi() != null) {
            getUi().setMute(muted);
        }
    }

    public int getAudioMode() {
        return AudioModeProvider.getInstance().getAudioMode();
    }

    public int getSupportedAudio() {
        return AudioModeProvider.getInstance().getSupportedModes();
    }

    public void setAudioMode(int mode) {

        // TODO: Set a intermediate state in this presenter until we get
        // an update for onAudioMode().  This will make UI response immediate
        // if it turns out to be slow

        Log.d(this, "Sending new Audio Mode: " + AudioMode.toString(mode));
        CallCommandClient.getInstance().setAudioMode(mode);
    }

    /**
     * Function assumes that bluetooth is not supported.
     */
    public void toggleSpeakerphone() {
        // this function should not be called if bluetooth is available
        if (0 != (AudioMode.BLUETOOTH & getSupportedAudio())) {

            // It's clear the UI is wrong, so update the supported mode once again.
            Log.e(this, "toggling speakerphone not allowed when bluetooth supported.");
            getUi().setSupportedAudio(getSupportedAudio());
            return;
        }

        int newMode = AudioMode.SPEAKER;

        // if speakerphone is already on, change to wired/earpiece
        if (getAudioMode() == AudioMode.SPEAKER) {
            newMode = AudioMode.WIRED_OR_EARPIECE;
        }

        setAudioMode(newMode);
    }

    public void endCallClicked() {
        if (mCall == null) {
            return;
        }

        CallCommandClient.getInstance().disconnectCall(mCall.getCallId());
    }

    public void manageConferenceButtonClicked() {
        getUi().displayManageConferencePanel(true);
    }

    public void muteClicked(boolean checked) {
        Log.d(this, "turning on mute: " + checked);

        CallCommandClient.getInstance().mute(checked);
    }

    public void holdClicked(boolean checked) {
        if (mCall == null) {
            return;
        }

        Log.d(this, "holding: " + mCall.getCallId());

        CallCommandClient.getInstance().hold(mCall.getCallId(), checked);
    }

    public void mergeClicked() {
        CallCommandClient.getInstance().merge();
    }

    public void addCallClicked() {

        /// M: design change for ALPS01262892 && ALPS01258249 && ALPS01236444 @{
        // the mute function when add call is not well designed on KK
        // the mute && unmute action should be done only in Telephony 
        // to prevent state confusion, the mute action will move to addCall()

        // MTK delete
        /*
        // Automatically mute the current call
        mAutomaticallyMuted = true;
        mPreviousMuteState = AudioModeProvider.getInstance().getMute();
        /// M: for ALPS01236444 save current callId to restore.
        if (mCall != null) {
            mPreviousCallId = mCall.getCallId();
        }
        Log.d(this, "addCallClicked()... mPreviousMuteState = " + mPreviousMuteState
                + ", mPreviousCallId"+ mPreviousCallId);
        // Simulate a click on the mute button
        muteClicked(true);
        */
        /// @}

        CallCommandClient.getInstance().addCall();
    }

    public void swapClicked() {
        CallCommandClient.getInstance().swap();
    }

    public void showDialpadClicked(boolean checked) {
        Log.v(this, "Show dialpad " + String.valueOf(checked));
        getUi().displayDialpad(checked);
        updateExtraButtonRow();
    }

    private void updateUi(InCallState state, Call call) {
        /// M: DMLock @{
        if (InCallUtils.isDMLocked()) {
            updateInCallControlsDuringDMLocked(call);
            return;
        }
        /// @}
        final CallButtonUi ui = getUi();
        if (ui == null) {
            return;
        }

        final boolean isEnabled = state.isConnectingOrConnected() &&
                !state.isIncoming() && call != null;

        ui.setEnabled(isEnabled);

        /// M: @{
        InCallUtils.updateScreenPopupMenuState(call, ui.getContext());
        /// @}

        Log.d(this, "Updating call UI for call: ", call);

        if (isEnabled) {
            Log.v(this, "Show hold ", call.can(Capabilities.SUPPORT_HOLD));
            Log.v(this, "Enable hold", call.can(Capabilities.HOLD));
            Log.v(this, "Show merge ", call.can(Capabilities.MERGE_CALLS));
            Log.v(this, "Show swap ", call.can(Capabilities.SWAP_CALLS));
            Log.v(this, "Show add call ", call.can(Capabilities.ADD_CALL));
            Log.v(this, "Show mute ", call.can(Capabilities.MUTE));

            final boolean canMerge = call.can(Capabilities.MERGE_CALLS);
            final boolean canAdd = call.can(Capabilities.ADD_CALL);
            final boolean isGenericConference = call.can(Capabilities.GENERIC_CONFERENCE);


            final boolean showMerge = !isGenericConference && canMerge;
            /// M: when no permanent menu, we will show one of these two buttons at popup menu. @{
            if (InCallUtils.hasPermanentMenuKey(ui.getContext())) {
                if (showMerge) {
                    ui.showMerge(true);
                    ui.showAddCall(false);
                } else {
                    ui.showMerge(false);
                    ui.showAddCall(true);
                    ui.enableAddCall(canAdd);
                }
            } else {
                ui.showMerge(false);
                ui.showAddCall(false);
            }
            /// @}

            final boolean canHold = call.can(Capabilities.HOLD);
            final boolean canSwap = call.can(Capabilities.SWAP_CALLS);
            final boolean supportHold = call.can(Capabilities.SUPPORT_HOLD);

            if (canHold) {
                ui.showHold(true);
                ui.setHold(call.getState() == Call.State.ONHOLD);
                ui.enableHold(true);
                ui.showSwap(false);
            } else if (canSwap) {
                ui.showHold(false);
                ui.showSwap(true);
            } else {
                // Neither "Hold" nor "Swap" is available.  This can happen for two
                // reasons:
                //   (1) this is a transient state on a device that *can*
                //       normally hold or swap, or
                //   (2) this device just doesn't have the concept of hold/swap.
                //
                // In case (1), show the "Hold" button in a disabled state.  In case
                // (2), remove the button entirely.  (This means that the button row
                // will only have 4 buttons on some devices.)

                /// M: Add for support Dualtalk feature. @{
                if (FeatureOptionWrapper.isSupportDualTalk() && call.can(Capabilities.SHOW_SWAP)
                        && !canSwap) {
                    ui.showHold(false);
                    ui.showSwap(true);
                    // need disable swap button
                } else if (supportHold) {
                /// @}
                    ui.showHold(true);
                    ui.enableHold(false);
                    ui.setHold(call.getState() == Call.State.ONHOLD);
                    ui.showSwap(false);
                } else {
                    ui.showHold(false);
                    ui.showSwap(false);
                }
            }

            ui.enableMute(call.can(Capabilities.MUTE));

            // Finally, update the "extra button row": It's displayed above the
            // "End" button, but only if necessary.  Also, it's never displayed
            // while the dialpad is visible (since it would overlap.)
            //
            // The row contains two buttons:
            //
            // - "Manage conference" (used only on GSM devices)
            // - "Merge" button (used only on CDMA devices)

            mShowGenericMerge = isGenericConference && canMerge;
            mShowManageConference = (call.isConferenceCall() && !isGenericConference);
            /**
             * M: [ALPS01236512] when the call is hold, the ManageConference view
             * should not show @{
             */
            mShowManageConference = mShowManageConference && !(call.getState() == Call.State.ONHOLD);
            /** @} */

            updateExtraButtonRow();
        }
    }

    private void updateExtraButtonRow() {
        final boolean showExtraButtonRow = (mShowGenericMerge || mShowManageConference) &&
                !getUi().isDialpadVisible();

        Log.d(this, "isGeneric: " + mShowGenericMerge);
        Log.d(this, "mShowManageConference : " + mShowManageConference);
        Log.d(this, "mShowGenericMerge: " + mShowGenericMerge);
        if (showExtraButtonRow) {
            if (mShowGenericMerge) {
                getUi().showGenericMergeButton();
            } else if (mShowManageConference) {
                getUi().showManageConferenceCallButton();
            }
        } else {
            getUi().hideExtraRow();
        }
    }

    /// M: design change for ALPS01262892 && ALPS01258249 && ALPS01236444 @{
    // the mute function when add call is not well designed on KK
    // the mute && unmute action should be done only in Telephony 
    // to prevent state confusion, the mute action will move to addCall()

    // MTK delete
    /*
    public void refreshMuteState() {
        /// M: For alps01236444. when the currentCallId is the same with mPreviousCallId, update the mute @{
        boolean muteState = AudioModeProvider.getInstance().getMute();
        if(mCall == null) {
            return;
        }
        int currentCallId = mCall.getCallId();
        Log.d(this, "refreshMuteState()... + mAutomaticallyMuted = " + mAutomaticallyMuted +
                ", now mute = " + muteState + ", currentCallId = " + currentCallId);

        // Restore the previous mute state
        if (mAutomaticallyMuted
                && muteState != mPreviousMuteState
                && currentCallId == mPreviousCallId
                && mPreviousCallId != -1) {
            if (getUi() == null) {
                return;
            }
            muteClicked(mPreviousMuteState);
            mAutomaticallyMuted = false;
            mPreviousCallId = -1;
        }
        /// @}
    }
    */
    /// @}

    public interface CallButtonUi extends Ui {
        void setEnabled(boolean on);
        void setMute(boolean on);
        void enableMute(boolean enabled);
        void setHold(boolean on);
        void showHold(boolean show);
        void enableHold(boolean enabled);
        void showMerge(boolean show);
        void showSwap(boolean show);
        void showAddCall(boolean show);
        void enableAddCall(boolean enabled);
        void displayDialpad(boolean on);
        boolean isDialpadVisible();
        void setAudio(int mode);
        void setSupportedAudio(int mask);
        void showManageConferenceCallButton();
        void showGenericMergeButton();
        void hideExtraRow();
        void displayManageConferencePanel(boolean on);
        /// M: get a context from fragment.@{
        Context getContext();
        /// @}

        /// M: for ALPS01255734 @{
        // dismiss all pop up menu when a new call incoming
        void dismissPopupMenu();
        /// @}
        /// For VT @{
        void updateVTCallButton();
        /// @}

        /// M: DMLock feature @{
        void enableEnd(boolean enabled);
        void enableSwap(boolean enabled);
        /// @}
    }

    //---------------------------------------Mediatek-----------------------------------
    

    public void voiceRecordClicked(){
        CallCommandClient.getInstance().startVoiceRecording();
    }

    public void stopRecordClicked(){
        CallCommandClient.getInstance().stopRecording();
    }

    public void hangupAllCalls() {
        CallCommandClient.getInstance().hangupAllCalls();
    }

    public void hangupHoldingCall() {
        CallCommandClient.getInstance().hangupHoldingCall();
    }

    public void hangupActiveAndAnswerWaiting() {
        CallCommandClient.getInstance().hangupActiveAndAnswerWaiting();
    }

    public void explicitCallTransfer() {
        CallCommandClient.getInstance().explicitCallTransfer();
    }

    //------------------VT----------

    // int mPreviousCallId = -1;
    public boolean isVTCall() {
        boolean isVT = false;
        if (mCall != null) {
            isVT = mCall.isVideoCall();
        }
        Log.d(this, "isVTCall()... mCall: " + mCall + " / " + isVT);
        return isVT;
    }

    public boolean isNoCallExist() {
        return (mCall == null);
    }

    public boolean shouldSwitchCameraVisible() {
        boolean shouldVisible = VTManagerLocal.getInstance().getCameraSensorCount() == 2;
        return shouldVisible;
    }

    public boolean shouldSwitchCameraEnable() {
        boolean shouldEnable = VTInCallScreenFlags.getInstance().mVTEnableBackCamera
                && (!VTInCallScreenFlags.getInstance().mVTHideMeNow);
        return shouldEnable;
    }

    public boolean shouldVideoSettingEnable() {
        boolean shouldEnable = VTManagerLocal.getInstance().getState() == VTManagerLocal.State.CONNECTED;
        return shouldEnable;
    }

    public boolean shouldVTRecordEnable() {
        if (FeatureOptionWrapper.isSupportPhoneVoiceRecording()
                && (VTManagerLocal.getInstance().getState() == VTManagerLocal.State.CONNECTED)) {
             return true;
        }

        return false;
    }

    public void onVTEndCallClick() {
        endCallClicked();
    }

    // Below implement VTListener, we only handle updateVTCallButton().
    @Override
    public void onVTStateChanged(int msgVT) {
        //
    }

    @Override
    public void answerVTCallPre() {
        //
    }

    @Override
    public void dialVTCallSuccess() {
        //
    }

    @Override
    public void updateVTCallFragment() {
        //
    }

    @Override
    public void updateVTCallButton() {
        final CallButtonUi ui = getUi();
        if (ui != null) {
            ui.updateVTCallButton();
        }
    }

    /// @}

    @Override
    public void onPhoneRaised() {
        // TODO Auto-generated method stub
        
    }

    public Call getCall() {
        Log.d(this, "getCall()... mCall: " + mCall);
        return mCall;
    }

    void updateInCallControlsDuringDMLocked(Call call) {
        final CallButtonUi ui = getUi();
        if (ui == null) {
            Log.d(this, "just return ui:" + ui);
            return;
        }
        Context context = ui.getContext();
        if (context == null) {
            Log.d(this, "just return context:" + context);
            return;
        }
        if (call == null) {
            Log.d(this, "just return call:" + call);
            return;
        }
        ui.setEnabled(false);
        ui.showMerge(false);
        ui.showAddCall(true);
        ui.enableAddCall(false);
        final boolean canHold = call.can(Capabilities.HOLD);
        final boolean canSwap = call.can(Capabilities.SWAP_CALLS);
        ui.enableEnd(true);
        ui.displayDialpad(getUi().isDialpadVisible());
        ui.showHold(canHold);
        ui.showSwap(canSwap);
    }
}
