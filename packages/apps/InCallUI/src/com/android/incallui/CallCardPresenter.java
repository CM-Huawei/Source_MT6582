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
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.graphics.Bitmap;
import android.os.SystemClock;
import android.telephony.PhoneNumberUtils;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.view.View;
import android.view.ViewGroup;

import com.android.incallui.CallList;
import com.android.incallui.AudioModeProvider.AudioModeListener;
import com.android.incallui.CallButtonFragment.InCallMenuState;
import com.android.incallui.ContactInfoCache.ContactCacheEntry;
import com.android.incallui.ContactInfoCache.ContactInfoCacheCallback;
import com.android.incallui.InCallPresenter.InCallState;
import com.android.incallui.InCallPresenter.InCallStateListener;
import com.android.incallui.InCallPresenter.IncomingCallListener;
import com.android.incallui.InCallPresenter.PhoneRecorderListener;
import com.android.internal.telephony.PhoneConstants;
import com.android.services.telephony.common.AudioMode;
import com.android.services.telephony.common.Call;
import com.android.services.telephony.common.Call.Capabilities;
import com.android.services.telephony.common.CallIdentification;
import com.android.services.telephony.common.DualtalkCallInfo;
import com.google.common.base.Preconditions;
import com.mediatek.incallui.InCallUtils;
import com.mediatek.incallui.ext.ExtensionManager;
import com.mediatek.incallui.recorder.PhoneRecorderUtils;
import com.mediatek.incallui.recorder.PhoneRecorderUtils.RecorderState;
import com.mediatek.incallui.wrapper.FeatureOptionWrapper;
import com.mediatek.phone.SIMInfoWrapper;
import com.mediatek.telephony.SimInfoManager.SimInfoRecord;
import com.mediatek.incallui.vt.VTInCallScreenFlags;


import java.util.ArrayList;
import java.util.Arrays;

/**
 * Presenter for the Call Card Fragment.
 * <p>
 * This class listens for changes to InCallState and passes it along to the fragment.
 */
public class CallCardPresenter extends Presenter<CallCardPresenter.CallCardUi>
        implements InCallStateListener, AudioModeListener, IncomingCallListener, PhoneRecorderListener {

    private static final String TAG = CallCardPresenter.class.getSimpleName();
    private static final long CALL_TIME_UPDATE_INTERVAL = 1000; // in milliseconds

    private Call mPrimary;
    private Call mSecondary;
    private ContactCacheEntry mPrimaryContactInfo;
    private ContactCacheEntry mSecondaryContactInfo;
    private static CallTimer mCallTimer;
    private Context mContext;

    public CallCardPresenter() {
        // create the call timer
        mCallTimer = new CallTimer(new Runnable() {
            @Override
            public void run() {
                updateCallTime();
            }
        });
    }


    public void init(Context context, Call call) {
        mContext = Preconditions.checkNotNull(context);

        // Call may be null if disconnect happened already.
        if (call != null) {
            mPrimary = call;

            final CallIdentification identification = call.getIdentification();

            // start processing lookups right away.
            if (!call.isConferenceCall()) {
                /// M: Modified for DualTalk. @{
                // Google code:
                /*
                startContactInfoSearch(identification, true,
                        call.getState() == Call.State.INCOMING);
                */
                startContactInfoSearch(identification, PRIMARY,
                        call.getState() == Call.State.INCOMING);
                /// @}
            } else {
                /// M: Modified for DualTalk. @{
                // Google code:
                /*
                updateContactEntry(null, true, true);
                */
                updateContactEntry(null, PRIMARY, true);
                /// @}
            }
        }
    }

    @Override
    public void onUiReady(CallCardUi ui) {
        super.onUiReady(ui);

        AudioModeProvider.getInstance().addListener(this);

        // Contact search may have completed before ui is ready.
        if (mPrimaryContactInfo != null) {
            updatePrimaryDisplayInfo(mPrimaryContactInfo, isConference(mPrimary));
        }

        /// M: for Recording @{
        // the UI may be destoryed, so when recreate it, update recording state
        updateVoiceCallRecordState(PhoneRecorderUtils.getRecorderState());
        /// @}

        // Register for call state changes last
        InCallPresenter.getInstance().addListener(this);
        InCallPresenter.getInstance().addIncomingCallListener(this);
        /// M: Add for recording. @{
        InCallPresenter.getInstance().addPhoneRecorderListener(this);
        /// @}
    }

    @Override
    public void onUiUnready(CallCardUi ui) {
        super.onUiUnready(ui);

        // stop getting call state changes
        InCallPresenter.getInstance().removeListener(this);
        InCallPresenter.getInstance().removeIncomingCallListener(this);
        /// M: Add for recording. @{
        InCallPresenter.getInstance().removePhoneRecorderListener(this);
        /// @}

        AudioModeProvider.getInstance().removeListener(this);

        mPrimary = null;
        mPrimaryContactInfo = null;
        mSecondaryContactInfo = null;
    }

    @Override
    public void onIncomingCall(InCallState state, Call call) {
        // same logic should happen as with onStateChange()
        onStateChange(state, CallList.getInstance());
    }

    @Override
    public void onStateChange(InCallState state, CallList callList) {
        Log.d(this, "onStateChange() " + state);
        final CallCardUi ui = getUi();
        if (ui == null) {
            return;
        }

        /// M: Add for CT Plugin. @{
        if (!InCallUtils.isLandscape(mContext)) {
            getUi().updateCallInfoLayout(state);
        }
        /// @}

        Call primary = null;
        Call secondary = null;

        if (state == InCallState.INCOMING) {
            primary = callList.getIncomingCall();
        } else if (state == InCallState.OUTGOING) {
            primary = callList.getOutgoingCall();

            // getCallToDisplay doesn't go through outgoing or incoming calls. It will return the
            // highest priority call to display as the secondary call.
            secondary = getCallToDisplay(callList, null, true);
            /// M: For support Dualtalk feature. @{
            if (FeatureOptionWrapper.isSupportDualTalk() && callList.getBackgroundCall() != null) {
                secondary = callList.getBackgroundCall();
            }
            /// @}
        } else if (state == InCallState.INCALL) {
            primary = getCallToDisplay(callList, null, false);
            secondary = getCallToDisplay(callList, primary, true);
        }

        Log.d(this, "Primary call: " + primary);
        Log.d(this, "Secondary call: " + secondary);

        final boolean primaryChanged = !areCallsSame(mPrimary, primary);
        final boolean secondaryChanged = !areCallsSame(mSecondary, secondary);
        mSecondary = secondary;
        mPrimary = primary;

        /// M: for ALPS01278985 @{
        // update the caller info presentation.
        // If any server information coming we have to update here.
        updateCallerInfoPresentation();
        /// @}

        if (primaryChanged && mPrimary != null) {
            // primary call has changed
            mPrimaryContactInfo = ContactInfoCache.buildCacheEntryFromCall(mContext,
                    mPrimary.getIdentification(), mPrimary.getState() == Call.State.INCOMING);
            updatePrimaryDisplayInfo(mPrimaryContactInfo, isConference(mPrimary));
            // / M: Modified for Dualtalk. @{
            // Google code:
            /*
             * maybeStartSearch(mPrimary, true);
             */
            maybeStartSearch(mPrimary, PRIMARY);
            /// @}
        }

        /// M: For Dualtalk feature, we unuse these code, and use our code. @{
        /*
        if (mSecondary == null) {
            // Secondary call may have ended.  Update the ui.
            mSecondaryContactInfo = null;
            updateSecondaryDisplayInfo(false);
        } else if (secondaryChanged) {
            // secondary call has changed
            mSecondaryContactInfo = ContactInfoCache.buildCacheEntryFromCall(mContext,
                    mSecondary.getIdentification(), mSecondary.getState() == Call.State.INCOMING);
            updateSecondaryDisplayInfo(mSecondary.isConferenceCall());
            maybeStartSearch(mSecondary, false);
        }
        */
        /// @}

        // Start/Stop the call time update timer
        if (mPrimary != null && mPrimary.getState() == Call.State.ACTIVE) {
            Log.d(this, "Starting the calltime timer");
            /// M: for ALPS01425992 @{
            // VT call need count message to caculate call time.
            // Voice call need to start timer at once.
            if (!mPrimary.isVideoCall()) {
                mCallTimer.start(CALL_TIME_UPDATE_INTERVAL);
            // UI recreated, and start timer for VT Call, which has already receive VT_MSG_START_COUNTER.
            } else if (VTInCallScreenFlags.getInstance().mVTConnectionStarttime.mStarttime >= 0) {
                mCallTimer.start(CALL_TIME_UPDATE_INTERVAL);
            } else {
                Log.d(this, "VT Call, do not start the calltime timer");
            }
            /// @}
        } else {
            Log.d(this, "Canceling the calltime timer");
            mCallTimer.cancel();
            ui.setPrimaryCallElapsedTime(false, null);
        }

        // Set the call state
        if (mPrimary != null) {
            final boolean bluetoothOn =
                    (AudioModeProvider.getInstance().getAudioMode() == AudioMode.BLUETOOTH);
            ui.setCallState(mPrimary.getState(), mPrimary.getDisconnectCause(), bluetoothOn,
                    getGatewayLabel(), getGatewayNumber());
            /// M: Change featrue about show sim indicator at ui. @{
            ui.setSimIndicator(mPrimary.getSlotId(), mPrimary.getCallType());
            /// @}
            /// M: Plug-in 
            ExtensionManager.getInstance().getCallCardExtension().onStateChange(mPrimary);
        } else {
            ui.setCallState(Call.State.IDLE, Call.DisconnectCause.UNKNOWN, false, null, null);
        }
        /**
         * M: [ALPS01234285] update phone recorder icon when UI updated @{
         */
        updateVoiceCallRecordState(PhoneRecorderUtils.getRecorderState());
        /** @} */

        /// M: Add for Dualtalk. @{
        updateCallDisplayForMTK();
        /// @}
    }

    @Override
    public void onAudioMode(int mode) {
        if (mPrimary != null && getUi() != null) {
            final boolean bluetoothOn = (AudioMode.BLUETOOTH == mode);

            getUi().setCallState(mPrimary.getState(), mPrimary.getDisconnectCause(), bluetoothOn,
                    getGatewayLabel(), getGatewayNumber());
        }
    }

    @Override
    public void onSupportedAudioMode(int mask) {
    }

    @Override
    public void onMute(boolean muted) {
    }

    public void updateCallTime() {
        final CallCardUi ui = getUi();

        if (ui == null || mPrimary == null || mPrimary.getState() != Call.State.ACTIVE) {
            if (ui != null) {
                ui.setPrimaryCallElapsedTime(false, null);
            }
            mCallTimer.cancel();
        } else {
            /// M: for ALPS01425992 @{
            // To caculate call time correctly.
            final long duration = getCallDuration(mPrimary);
            /// @}
            ui.setPrimaryCallElapsedTime(true, DateUtils.formatElapsedTime(duration / 1000));
        }
    }

    private boolean areCallsSame(Call call1, Call call2) {
        if (call1 == null && call2 == null) {
            return true;
        } else if (call1 == null || call2 == null) {
            return false;
        }

        // otherwise compare call Ids
        return call1.getCallId() == call2.getCallId();
    }

    /// M: unuse Google code:
    /*
    private void maybeStartSearch(Call call, boolean isPrimary) {
        // no need to start search for conference calls which show generic info.
        if (call != null && !call.isConferenceCall()) {
            startContactInfoSearch(call.getIdentification(), isPrimary,
                    call.getState() == Call.State.INCOMING);
        }
    }
    */

    /**
     * Starts a query for more contact data for the save primary and secondary calls.
     */
    private void startContactInfoSearch(final CallIdentification identification,
            /// M: Modified for DualTalk. @{
            // Google code:
            /*
            final boolean isPrimary, boolean isIncoming) {
            */
            final int type, boolean isIncoming) {
            /// @}
        final ContactInfoCache cache = ContactInfoCache.getInstance(mContext);

        cache.findInfo(identification, isIncoming, new ContactInfoCacheCallback() {
                @Override
                public void onContactInfoComplete(int callId, ContactCacheEntry entry) {
                    /// M: Modified for DualTalk. @{
                    // Google code:
                    /*
                    updateContactEntry(entry, isPrimary, false);
                    */
                    updateContactEntry(entry, type, false);
                    /// @}
                    if (entry.name != null) {
                        Log.d(TAG, "Contact found: " + entry);
                    }
                    if (entry.personUri != null) {
                        CallerInfoUtils.sendViewNotification(mContext, entry.personUri);
                    }
                }

                @Override
                public void onImageLoadComplete(int callId, ContactCacheEntry entry) {
                    if (getUi() == null) {
                        return;
                    }
                    if (entry.photo != null) {
                        if (mPrimary != null && callId == mPrimary.getCallId()) {
                            getUi().setPrimaryImage(entry.photo);
                        } else if (mSecondary != null && callId == mSecondary.getCallId()) {
                            getUi().setSecondaryImage(entry.photo);
                        }
                    }
                }
            });
    }

    private static boolean isConference(Call call) {
        return call != null && call.isConferenceCall();
    }

    private static boolean isGenericConference(Call call) {
        return call != null && call.can(Capabilities.GENERIC_CONFERENCE);
    }

    private void updateContactEntry(ContactCacheEntry entry, boolean isPrimary,
            boolean isConference) {
        if (isPrimary) {
            mPrimaryContactInfo = entry;
            updatePrimaryDisplayInfo(entry, isConference);
        } else {
            mSecondaryContactInfo = entry;
            updateSecondaryDisplayInfo(isConference);
        }
    }

    /**
     * Get the highest priority call to display.
     * Goes through the calls and chooses which to return based on priority of which type of call
     * to display to the user. Callers can use the "ignore" feature to get the second best call
     * by passing a previously found primary call as ignore.
     *
     * @param ignore A call to ignore if found.
     */
    static Call getCallToDisplay(CallList callList, Call ignore, boolean skipDisconnected) {

        // Active calls come second.  An active call always gets precedent.
        Call retval = callList.getActiveCall();
        /// M: Modify for Dualtalk. @{
        if (FeatureOptionWrapper.isSupportDualTalk() && callList.isGsmPhoneFirst()) {
            retval = callList.getGsmActiveCall();
        }
        /// @}

        if (retval != null && retval != ignore) {
            return retval;
        }

        // Disconnected calls get primary position if there are no active calls
        // to let user know quickly what call has disconnected. Disconnected
        // calls are very short lived.
        if (!skipDisconnected) {
            retval = callList.getDisconnectingCall();
            if (retval != null && retval != ignore) {
                return retval;
            }
            retval = callList.getDisconnectedCall();
            if (retval != null && retval != ignore) {
                return retval;
            }
        }

        // Then we go to background call (calls on hold)
        /// M: Modify for Dualtalk. @{
        if (FeatureOptionWrapper.isSupportDualTalk()
                && InCallPresenter.getInstance().mDualtalkCallInfo != null
                && InCallPresenter.getInstance().mDualtalkCallInfo.getIsCdmaAndGsmActive()) {
            retval = callList.getBackgroundCall();
            if (retval == null) {
                retval = callList.getCdmaActiveCall();
            }
        } else {
            retval = callList.getBackgroundCall();
        }
        /// @}
        if (retval != null && retval != ignore) {
            return retval;
        }

        // Lastly, we go to a second background call.
        retval = callList.getSecondBackgroundCall();

        return retval;
    }

    private void updatePrimaryDisplayInfo(ContactCacheEntry entry, boolean isConference) {
        Log.d(TAG, "Update primary display " + entry);
        final CallCardUi ui = getUi();
        if (ui == null) {
            // TODO: May also occur if search result comes back after ui is destroyed. Look into
            // removing that case completely.
            Log.d(TAG, "updatePrimaryDisplayInfo called but ui is null!");
            return;
        }

        final boolean isGenericConf = isGenericConference(mPrimary);
        if (entry != null) {
            final String name = getNameForCall(entry);
            final String number = getNumberForCall(entry);
            final boolean nameIsNumber = name != null && name.equals(entry.number);
            ui.setPrimary(number, name, nameIsNumber, entry.label,
                    entry.photo, isConference, isGenericConf, entry.isSipCall, entry.location);
        } else {
            ui.setPrimary(null, null, false, null, null, isConference, isGenericConf, false, null);
        }

        /// M: Add for plugin. @{
        if (mPrimary != null) { /// M: [ALPS01268302]when Async call back, mPrimary might be null
            SimInfoRecord simInfo = SIMInfoWrapper.getDefault().getSimInfoBySlot(mPrimary.getSlotId());
            ExtensionManager.getInstance().getCallCardExtension()
                    .updatePrimaryDisplayInfo(mPrimary, simInfo);
        } else {
            Log.w(this, "[updatePrimaryDisplayInfo]mPrimary is null, abort calling Plugin");
        }
        /// @}
    }

    private void updateSecondaryDisplayInfo(boolean isConference) {

        final CallCardUi ui = getUi();
        if (ui == null) {
            return;
        }

        final boolean isGenericConf = isGenericConference(mSecondary);
        if (mSecondaryContactInfo != null) {
            Log.d(TAG, "updateSecondaryDisplayInfo() " + mSecondaryContactInfo);
            final String nameForCall = getNameForCall(mSecondaryContactInfo);

            final boolean nameIsNumber = nameForCall != null && nameForCall.equals(
                    mSecondaryContactInfo.number);
            ui.setSecondary(true, nameForCall, nameIsNumber, mSecondaryContactInfo.label,
                    mSecondaryContactInfo.photo, isConference, isGenericConf);
        } else {
            // reset to nothing so that it starts off blank next time we use it.
            ui.setSecondary(false, null, false, null, null, isConference, isGenericConf);
        }
    }

    /**
     * Returns the gateway number for any existing outgoing call.
     */
    private String getGatewayNumber() {
        if (hasOutgoingGatewayCall()) {
            return mPrimary.getGatewayNumber();
        }

        return null;
    }

    /**
     * Returns the label for the gateway app for any existing outgoing call.
     */
    private String getGatewayLabel() {
        if (hasOutgoingGatewayCall() && getUi() != null) {
            final PackageManager pm = mContext.getPackageManager();
            try {
                final ApplicationInfo info = pm.getApplicationInfo(mPrimary.getGatewayPackage(), 0);
                return mContext.getString(R.string.calling_via_template,
                        pm.getApplicationLabel(info).toString());
            } catch (PackageManager.NameNotFoundException e) {
            }
        }
        return null;
    }

    private boolean hasOutgoingGatewayCall() {
        // We only display the gateway information while DIALING so return false for any othe
        // call state.
        // TODO: mPrimary can be null because this is called from updatePrimaryDisplayInfo which
        // is also called after a contact search completes (call is not present yet).  Split the
        // UI update so it can receive independent updates.
        if (mPrimary == null) {
            return false;
        }
        return (Call.State.isDialing(mPrimary.getState()) &&
                !TextUtils.isEmpty(mPrimary.getGatewayNumber()) &&
                !TextUtils.isEmpty(mPrimary.getGatewayPackage()));
    }

    /**
     * Gets the name to display for the call.
     */
    private static String getNameForCall(ContactCacheEntry contactInfo) {
        if (TextUtils.isEmpty(contactInfo.name)) {
            return contactInfo.number;
        }
        return contactInfo.name;
    }

    /**
     * Gets the number to display for a call.
     */
    private static String getNumberForCall(ContactCacheEntry contactInfo) {
        // If the name is empty, we use the number for the name...so dont show a second
        // number in the number field
        if (TextUtils.isEmpty(contactInfo.name)) {
//            return contactInfo.location;
            return "";
        }
        return contactInfo.number;
    }

    public void secondaryPhotoClicked() {
        /// M: Modified for Dualtalk. @{
        // Google code:
        /*
        CallCommandClient.getInstance().swap();
        */
        CallCommandClient.getInstance().secondaryPhotoClicked();
        /// @}
    }

    public interface CallCardUi extends Ui {
        void setVisible(boolean on);
        void setPrimary(String number, String name, boolean nameIsNumber, String label,
                Drawable photo, boolean isConference, boolean isGeneric, boolean isSipCall,
                /// M: MTK add for geo description @{
                String location);
                /// @}
        void setSecondary(boolean show, String name, boolean nameIsNumber, String label,
                Drawable photo, boolean isConference, boolean isGeneric);
        void setSecondaryImage(Drawable image);
        void setCallState(int state, Call.DisconnectCause cause, boolean bluetoothOn,
                String gatewayLabel, String gatewayNumber);
        void setPrimaryCallElapsedTime(boolean show, String duration);
        void setPrimaryName(String name, boolean nameIsNumber);
        void setPrimaryImage(Drawable image);
        void setPrimaryPhoneNumber(String phoneNumber);
        void setPrimaryLabel(String label);
        /// M: For Change featrue about show sim indicator at ui. @{
        void setSimIndicator(int slotId, int callType);
        void updateSecondaryCallBannerBackground(int slotId, int callType);
        /// @}

        /// M: Add for geo description @{
        void setLocation(String location);
        /// @}

        /// M: Add for recording. @{
        void updateVoiceRecordIcon(boolean show);
        /// @}

        /// M: Add for CT plugin. @{
        void updateCallInfoLayout(InCallState state);
        /// @}

        /// M: Add for DT.@{
        void setSecondaryHold(boolean show, String name, boolean nameIsNumber, String label,
                Drawable photo, boolean isConference, boolean isGeneric);
        void setSecondaryIncoming(boolean show, String name, boolean nameIsNumber, String label,
                Drawable photo, boolean isConference, boolean isGeneric);
        void updateSecondaryHoldCallBannerBackground(int slotId, int callType);
        void updateSecondaryIncomingCallBannerBackground(int slotId, int callType);
        void disableSecondHoldCallView();
        void disableSecondIncomingCallView();
        /// @}
    }

    //-------------------------------------MTK-------------------------------
    @Override
    public void onUpdateRecordState(int state, int customValue) {
        if (FeatureOptionWrapper.isSupportPhoneVoiceRecording()) {
            updateVoiceCallRecordState(state);
        }
    }

    private void updateVoiceCallRecordState(int state) {
        Log.d(this, "updateVoiceCallRecordState... state = " + state);
        Call ringCall = null;
        int ringCallState = -1;
        ringCall = CallList.getInstance().getIncomingCall();
        if (null != ringCall) {
            ringCallState = ringCall.getState();
        }
        if ((RecorderState.RECORDING_STATE == state) && (ringCallState != Call.State.INCOMING)
                && (ringCallState != Call.State.CALL_WAITING)) {
            getUi().updateVoiceRecordIcon(true);
        } else if ((RecorderState.IDLE_STATE == state)
                || (ringCallState == Call.State.INCOMING)
                || (ringCallState == Call.State.CALL_WAITING)) {
            getUi().updateVoiceRecordIcon(false);
        }
    }

    public boolean isVTCall() {
        boolean isVT = false;
        Call ringCall = null;
        int ringCallState = -1;
        ringCall = CallList.getInstance().getIncomingCall();
        if (ringCall != null) {
            isVT = ringCall.isVideoCall();
        }
        Log.d(this, "isVTCall()... mCall: " + ringCall + " / " + isVT);
        return isVT;
    }

    /// Dualtalk related start. @{
    private static final int PRIMARY = 0;
    private static final int SECONDARY = 1;
    private static final int SECONDARY_ONHOLD = 2;
    private static final int SECONDARY_INCOMING = 3;

    private Call mSecondaryIncomingCall;
    private Call mSecondaryHoldCall;
    private ContactCacheEntry mSecondaryIncomingCallInfo;
    private ContactCacheEntry mSecondaryHoldCallInfo;

    private void updateContactEntry(ContactCacheEntry entry, int type, boolean isConference) {
        Log.d(this, "updateContactEntry, type =" + type + "; entry = " + entry);
        if (type == PRIMARY) {
            mPrimaryContactInfo = entry;
            updatePrimaryDisplayInfo(entry, isConference);
        } else if (type == SECONDARY) {
            mSecondaryContactInfo = entry;
            updateSecondaryDisplayInfo(isConference);
        } else if (type == SECONDARY_ONHOLD) {
            updateSecondaryHoldCallDisplayInfo(isConference);
        } else if (type == SECONDARY_INCOMING) {
            updateSecondaryIncomingCallDisplayInfo(isConference);
        }
    }

    private void maybeStartSearch(Call call, int type) {
        // no need to start search for conference calls which show generic info.
        if (call != null && !call.isConferenceCall()) {
            Log.d(this, "maybeStartSearch, call =  " + call + "; type = " + type);
            startContactInfoSearch(call.getIdentification(), type,
                    call.getState() == Call.State.INCOMING);
        }
    }

    private void updateSecondaryCall(Call secondary) {
        final boolean secondaryChanged = !areCallsSame(mSecondary, secondary);
        Log.d(this, "updateSecondaryCall, secondary =  " + secondary + "; secondaryChanged = "
                + secondaryChanged);
        mSecondary = secondary;

        if (mSecondary == null) {
            // Secondary call may have ended. Update the ui.
            mSecondaryContactInfo = null;
            updateSecondaryDisplayInfo(false);
        } else {
            // secondary call has changed
            mSecondaryContactInfo = ContactInfoCache.buildCacheEntryFromCall(mContext,
                    mSecondary.getIdentification(), mSecondary.getState() == Call.State.INCOMING);
            updateSecondaryDisplayInfo(mSecondary.isConferenceCall());
            getUi().updateSecondaryCallBannerBackground(mSecondary.getSlotId(), mSecondary.getCallType());
            maybeStartSearch(mSecondary, SECONDARY);
        }
    }

    public void updateSecondaryHoldCall(Call secondaryHoldCall){
        final boolean secondaryHoldChanged = !areCallsSame(mSecondaryHoldCall, secondaryHoldCall);
        Log.d(this, "updateSecondaryHoldCall, secondaryHoldCall =  " + secondaryHoldCall
                + "; secondaryHoldChanged = " + secondaryHoldChanged);
        mSecondaryHoldCall = secondaryHoldCall;
        if (mSecondaryHoldCall == null) {
            mSecondaryHoldCallInfo = null;
            updateSecondaryHoldCallDisplayInfo(false);
        } else {
            // secondary call has changed
            mSecondaryHoldCallInfo = ContactInfoCache.buildCacheEntryFromCall(mContext,
                    mSecondaryHoldCall.getIdentification(),
                    mSecondaryHoldCall.getState() == Call.State.INCOMING);
            updateSecondaryHoldCallDisplayInfo(mSecondaryHoldCall.isConferenceCall());
            // update secondary hold call banner color. @{
            getUi().updateSecondaryHoldCallBannerBackground(mSecondaryHoldCall.getSlotId(),
                    mSecondaryHoldCall.getCallType());

            maybeStartSearch(mSecondaryHoldCall, SECONDARY_ONHOLD);
        }
    }

    private void updateSecondaryHoldCallDisplayInfo(boolean isConference) {
        Log.d(this, "Update SecondaryHoldCall display ");
        final CallCardUi ui = getUi();
        if (ui == null) {
            return;
        }

        final boolean isGenericConf = isGenericConference(mSecondaryHoldCall);
        if (mSecondaryHoldCallInfo != null) {
            Log.d(TAG, "updateSecondaryHoldCallDisplayInfo() " + mSecondaryHoldCallInfo);
            final String nameForCall = getNameForCall(mSecondaryHoldCallInfo);

            final boolean nameIsNumber = nameForCall != null
                    && nameForCall.equals(mSecondaryHoldCallInfo.number);
            ui.setSecondaryHold(true, nameForCall, nameIsNumber, mSecondaryHoldCallInfo.label,
                    mSecondaryHoldCallInfo.photo, isConference, isGenericConf);
        } else {
            // reset to nothing so that it starts off blank next time we use it.
            ui.setSecondaryHold(false, null, false, null, null, isConference, isGenericConf);
        }
    }

    private void updateCallForDualtalk() {
        int phoneType = PhoneConstants.PHONE_TYPE_NONE;
        Call fgCall = mPrimary;
        Call bgCall = mSecondary;
        Call bgSecondCall = CallList.getInstance().getSecondBackgroundCall();
        Log.d(this, "[updateCallForDualtalk], fgCall = " + fgCall + "bgCall = " + bgCall
                + "bgSecondCall = " + bgSecondCall);
        if (areCallsSame(bgCall, bgSecondCall)) {
            bgSecondCall = null;
        }
        DualtalkCallInfo dualtalkCallInfo = InCallPresenter.getInstance().mDualtalkCallInfo;
        if (dualtalkCallInfo == null) {
            Log.d(this, "[updateCallForDualtalk], dualtalkCallInfo is null");
            return;
        }
        if (fgCall != null) {
            phoneType = fgCall.getPhoneType();
            Log.d(this, "[updateCallForDualtalk], phoneType = " + phoneType);
        }
        if (FeatureOptionWrapper.isSupportDualTalk() && dualtalkCallInfo.getIsCdmaAndGsmActive()) {
            if (phoneType != PhoneConstants.PHONE_TYPE_CDMA
                    && (bgCall != null && bgCall.getPhoneType() != PhoneConstants.PHONE_TYPE_CDMA)) {
                updateSecondaryCall(bgCall);
                Call cdmaCall = CallList.getInstance().getCdmaActiveCall();
                updateSecondaryHoldCall(cdmaCall);
            } else {
                updateSecondaryCall(null);
                updateSecondaryHoldCall(bgCall);
            }
        } else if (phoneType == PhoneConstants.PHONE_TYPE_CDMA) {
            if ((dualtalkCallInfo.getCdmaPhoneCallState() == 0)
                    && dualtalkCallInfo.getIsThreeWayCallOrigStateDialing()) {
                updateSecondaryCall(fgCall);
            } else {
                // This is required so that even if a background call is not
                // present we need to clean up the background call area.
                updateSecondaryCall(bgCall);
            }
            updateSecondaryHoldCall(null);
        } else if (((phoneType == PhoneConstants.PHONE_TYPE_GSM) || (phoneType == PhoneConstants.PHONE_TYPE_SIP))) {
            Call secondaryCall = getSecondaryCallToShow(fgCall, bgCall, bgSecondCall);
            Call secondaryHoldCall = getSecondaryHoldCallToShow(fgCall, bgCall, bgSecondCall);
            updateSecondaryCall(secondaryCall);
            updateSecondaryHoldCall(secondaryHoldCall);
        }
    }

    /**
     * Display second incoming call info for dual talk. And hide the third call(SecondHoldCall) area.
     */
    private void updateSecondRingCallForDualTalk() {
        Log.d(this, "updateSecondRingCallForDualTalk: ");
        if (FeatureOptionWrapper.isSupportDualTalk()) {
            // maybe the dualtalkCallInfo is null, so add this judge
            DualtalkCallInfo dualtalkCallInfo = InCallPresenter.getInstance().mDualtalkCallInfo;
            if (dualtalkCallInfo != null && dualtalkCallInfo.getHasMultipleRingingCall()) {
                updateSecondaryIncomingCall(CallList.getInstance().getSecondaryIncomingCall());
            } else {
                getUi().disableSecondIncomingCallView();
            }
        }
        getUi().disableSecondHoldCallView();
    }

    public void updateSecondaryIncomingCall(Call secondaryIncomingCall) {
        Log.d(this, "[updateSecondaryIncomingCall],  secondaryIncomingCall = "
                + secondaryIncomingCall);
        mSecondaryIncomingCall = secondaryIncomingCall;
        if (mSecondaryIncomingCall == null) {
            mSecondaryIncomingCallInfo = null;
            updateSecondaryHoldCallDisplayInfo(false);
        } else {
            // secondary call has changed
            mSecondaryIncomingCallInfo = ContactInfoCache.buildCacheEntryFromCall(mContext,
                    mSecondaryIncomingCall.getIdentification(),
                    mSecondaryIncomingCall.getState() == Call.State.INCOMING);
            updateSecondaryIncomingCallDisplayInfo(mSecondaryIncomingCall.isConferenceCall());
            // update secondary hold call banner color. @{
            getUi().updateSecondaryIncomingCallBannerBackground(mSecondaryIncomingCall.getSlotId(),
                    mSecondaryIncomingCall.getCallType());

            maybeStartSearch(mSecondaryIncomingCall, SECONDARY_INCOMING);
        }
    }

    private void updateSecondaryIncomingCallDisplayInfo(boolean isConference) {
        Log.d(this, "Update SecondaryIncomingCall display ");
        final CallCardUi ui = getUi();
        if (ui == null) {
            return;
        }

        final boolean isGenericConf = isGenericConference(mSecondaryIncomingCall);
        if (mSecondaryIncomingCallInfo != null) {
            Log.d(TAG, "updateSecondaryHoldCallDisplayInfo() " + mSecondaryIncomingCallInfo);
            final String nameForCall = getNameForCall(mSecondaryIncomingCallInfo);

            final boolean nameIsNumber = nameForCall != null
                    && nameForCall.equals(mSecondaryIncomingCallInfo.number);
            ui.setSecondaryIncoming(true, nameForCall, nameIsNumber, mSecondaryIncomingCallInfo.label,
                    mSecondaryIncomingCallInfo.photo, isConference, isGenericConf);
        } else {
            // reset to nothing so that it starts off blank next time we use it.
            ui.setSecondaryIncoming(false, null, false, null, null, isConference, isGenericConf);
        }
    }

    public void updateCallDisplayForMTK() {
        /// Change for ALPS01276488, CallList.getInstance().getForegroundCall() &
        //  getBackgroundCall() not contain all status of the calls should be updated @{
        Call fgCall = mPrimary;
        Call bgCall = mSecondary;
        /// @}
        Call ringingCall = CallList.getInstance().getIncomingCall();
        /// Change for ALPS01311162, only when there are MultipleRingingCall,
        //  shall we go to updateSecondRingCallForDualTalk, add condition.
        DualtalkCallInfo dualtalkCallInfo = InCallPresenter.getInstance().mDualtalkCallInfo;
        if ((ringingCall != null) && (dualtalkCallInfo != null && dualtalkCallInfo.getHasMultipleRingingCall())
                && ((fgCall != null && fgCall.getState() != Call.State.DIALING) ||
                        FeatureOptionWrapper.isSupportDualTalk() &&
                        (dualtalkCallInfo != null && dualtalkCallInfo.getIsRingingWhenOutgoing()))) {
            // if (skipUpdateRingingCall(cm, ringingCall, fgCall, bgCall)) {
            //     return;
            // }
            // A phone call is ringing, call waiting *or* being rejected
            // (ie. another call may also be active as well.)
            updateSecondRingCallForDualTalk();
        } else if ((fgCall != null) || (bgCall != null)) {
            // We are here because either:
            // (1) the phone is off hook. At least one call exists that is
            // dialing, active, or holding, and no calls are ringing or waiting,
            // or:
            // (2) the phone is IDLE but a call just ended and it's still in
            // the DISCONNECTING or DISCONNECTED state. In this case, we want
            // the main CallCard to display "Hanging up" or "Call ended".
            // The normal "foreground call" code path handles both cases.
            updateCallForDualtalk();
        }
    }

    public void onDualtalkSecondaryPhotoClicked(){
        CallCommandClient.getInstance().secondaryHoldPhotoClicked();
    }

    public void switchRingtoneForDualTalk(){
        CallCommandClient.getInstance().switchRingtoneForDualTalk();
    }

    public void switchCalls(){
        CallCommandClient.getInstance().switchCalls();
    }
    /// @}

    /// M: For ALPS01276403 @{
    private final static int NO_PHONE_ID = -1;
    private Call getSecondaryCallToShow(Call fgCall, Call bgCall, Call bgSecondCall) {
        Call secondaryCall = null;

        if ((fgCall == null) || (bgCall == null && bgSecondCall == null)) {
            secondaryCall = null;
        }
        if (isSamePhone(fgCall, bgCall)) {
            secondaryCall = bgCall;
        } else if (isSamePhone(fgCall, bgSecondCall)) {
            secondaryCall = bgSecondCall;
        }

        Log.d(this, "getSecondaryCallToShow()...secondaryCall: " + secondaryCall);
        return secondaryCall;
    }

    private Call getSecondaryHoldCallToShow(Call fgCall, Call bgCall, Call bgSecondCall) {
        Call secondaryHoldCall = null;

        if ((fgCall == null) || (bgCall == null && bgSecondCall == null)) {
            secondaryHoldCall = null;
        }
        if (bgCall != null && !isSamePhone(fgCall, bgCall)) {
            secondaryHoldCall = bgCall;
        } else if (bgSecondCall != null && !isSamePhone(fgCall, bgSecondCall)) {
            secondaryHoldCall = bgSecondCall;
        }
        Log.d(this, "getSecondaryHoldCallToShow()...secondaryHoldCall: " + secondaryHoldCall);
        return secondaryHoldCall;
    }

    private boolean isSamePhone(Call call1, Call call2) {
        boolean result = false;

        int phoneId1 = NO_PHONE_ID;
        int phoneId2 = NO_PHONE_ID;
        if (call1 != null) {
            phoneId1 = call1.getPhoneId();
        }
        if (call2 != null) {
            phoneId2 = call2.getPhoneId();
        }

        if (phoneId1 == NO_PHONE_ID || phoneId2 == NO_PHONE_ID) {
            result = false;
        } else {
            result = (phoneId1 == phoneId2);
        }

        return result;
    }
    /// @}

    /// M: for ALPS01278985 @{
    // this method just update the caller number presentation.
    // TODO: in the future, if any server information coming that we need to update,
    // we have to update them here.
    private void updateCallerInfoPresentation() {
        if (mPrimary != null) {
            final ContactCacheEntry entry = ContactInfoCache.updateCallerInformation(mContext, mPrimary);
            if (entry != null) {
                mPrimaryContactInfo = entry;
                updatePrimaryDisplayInfo(mPrimaryContactInfo, isConference(mPrimary));
            }
        }
    }
    /// @}
    
    
    /// M: for ALPS01425992 @{
    // InCallUI start count to display call time of VT.
    // And need to check VTTimingMode to caculate time of VT.
    // Also need to add startVTCallTimer to be called when received count message.
    
    public static void startVTCallTimer() {
        mCallTimer.start(CALL_TIME_UPDATE_INTERVAL);
    }
    
    private static String[] sNumbersNone = { "12531", "+8612531" };
    private static String[] sNumbersDefault = { "12535", "13800100011", "+8612535", "+8613800100011" };

    public static enum VTTimingMode {
        VT_TIMING_NONE, /* VT_TIMING_SPECIAL, */VT_TIMING_DEFAULT
    }

    /**
     * Check video call time mode according to phone number
     * @param number phone number
     * @return video call time mode
     */
    private static VTTimingMode checkVTTimingMode(String number) {
        Log.d(TAG,"checkVTTimingMode - number:" + number);

        ArrayList<String> arrayListNone = new ArrayList<String>(Arrays.asList(sNumbersNone));
        ArrayList<String> arrayListDefault = new ArrayList<String>(Arrays.asList(sNumbersDefault));

        if (arrayListNone.indexOf(number) >= 0) {
            Log.d(TAG,"checkVTTimingMode - return:" + VTTimingMode.VT_TIMING_NONE);
            return VTTimingMode.VT_TIMING_NONE;
        }

        if (arrayListDefault.indexOf(number) >= 0) {
            Log.d(TAG,"checkVTTimingMode - return:" + VTTimingMode.VT_TIMING_DEFAULT);
            return VTTimingMode.VT_TIMING_DEFAULT;
        }

        return VTTimingMode.VT_TIMING_DEFAULT;
    }

    private long getCallDuration(Call call) {
        if (FeatureOptionWrapper.isSupportVT() && call.isVideoCall()) {
                                                       
            if (VTTimingMode.VT_TIMING_NONE == checkVTTimingMode(call.getNumber())) {
                return 0;
            } else if (VTTimingMode.VT_TIMING_DEFAULT == checkVTTimingMode(call.getNumber())) {
                if (VTInCallScreenFlags.getInstance().mVTConnectionStarttime.mStarttime < 0) {
                    return 0;
                } else {
                    return SystemClock.elapsedRealtime()
                            - VTInCallScreenFlags.getInstance().mVTConnectionStarttime.mStarttime;
                }
            } else {
                // Never happen here, only 2 mode for VTTimingMode
                return 0;
            }
        } else {
            final long callStart = call.getConnectTime();
            final long duration = System.currentTimeMillis() - callStart;
            return duration;
        }
    }
    /// @}
}
