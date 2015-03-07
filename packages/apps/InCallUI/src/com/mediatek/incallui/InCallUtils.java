package com.mediatek.incallui;

import android.net.Uri;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.telephony.PhoneNumberUtils;
import android.view.Menu;
import android.view.MenuItem;
import android.view.ViewConfiguration;
import android.view.WindowManagerGlobal;

import com.android.incallui.R;
import com.android.incallui.AnswerFragment.IncomingCallMenuState;
import com.android.incallui.CallList;
import com.android.incallui.InCallPresenter;
import com.android.incallui.Log;
import com.android.incallui.CallButtonFragment.InCallMenuState;
import com.android.internal.util.Preconditions;
import com.mediatek.common.dm.DmAgent;
import com.mediatek.incallui.vt.Constants;
import com.mediatek.incallui.vt.VTUtils;
import com.mediatek.incallui.wrapper.FeatureOptionWrapper;
import com.android.services.telephony.common.Call;
import com.android.services.telephony.common.Call.Capabilities;

public final class InCallUtils {

    private static final String TAG = InCallUtils.class.getSimpleName();

    public static boolean isDMLocked() {
        boolean locked = false;
        try {
            IBinder binder = ServiceManager.getService("DmAgent");
            DmAgent agent = null;
            if (binder != null) {
                agent = DmAgent.Stub.asInterface(binder);
            }
            if (agent != null) {
                locked = agent.isLockFlagSet();
            }
        } catch (RemoteException e) {
            e.printStackTrace();
        }

        Log.d(TAG, "isDMLocked(): locked = " + locked);
        return locked;
    }


    public static boolean isIncomingCall(Call call) {
        boolean isIncoming = false;
        if (call != null) {
            isIncoming = call.isIncoming();
        }
        return isIncoming; 
    }

    public static void updateScreenPopupMenuState(Call call, Context context) {
        InCallMenuState.reset();
        if (call == null) {
            Log.d(TAG, "[updateScreenPopupMenuState], the call is null!");
            return;
        }
        boolean canMerge = call.can(Capabilities.MERGE_CALLS);
        boolean canAdd = call.can(Capabilities.ADD_CALL);
        boolean isGenericConference = call.can(Capabilities.GENERIC_CONFERENCE);
        canMerge = !isGenericConference && canMerge;

        Call ringingCall = CallList.getInstance().getIncomingCall();
        Call bgCall = CallList.getInstance().getBackgroundCall();
        if ((ringingCall == null)
                || (ringingCall != null && ringingCall.getState() == Call.State.DISCONNECTED)
                && (bgCall != null && bgCall.getState() == Call.State.IDLE)) {
            if (!hasPermanentMenuKey(context)) {
                if (canAdd) {
                    InCallMenuState.sCanAddCall = canAdd;
                } else if (canMerge) {
                    InCallMenuState.sCanMerge = canMerge;
                } else {
                    InCallMenuState.sCanAddCall = true;
                    InCallMenuState.sDisableAdd = true;
                }
            }
            InCallMenuState.sCanHangupAll = call.can(Capabilities.HANGUP_ALL);
            InCallMenuState.sCanHangupHolding = call.can(Capabilities.HANGUP_HOLDING);
        } else {
            // This is for VT voice answer feature
            InCallMenuState.sCanVTVoiceAnswer = call.can(Capabilities.VT_VOICE_ANSWER);
        }
        InCallMenuState.sCanHangupActiveAndAnswerWaiting = call
                .can(Capabilities.HANGUP_ACTIVE_AND_ANSWER_WAITING);
        InCallMenuState.sCanECT = call.can(Capabilities.ECT);
        InCallMenuState.sCanRecording = call.can(Capabilities.RECORD);
    }

    /**
     * The function to update incoming screen's menu state based on current CallList.
     * Note: we can't just use ringingCall.can(Capabilities.HANGUP_ACTIVE_AND_ANSWER_WAITING),
     * for the incoming call will be update only once (onIncoming(),
     * other call's state change will not update ringing call's Capabilities). please see ALPS01256843
     */
    public static void updateIncomingMenuState() {
        IncomingCallMenuState.reset();
        if (FeatureOptionWrapper.isSupportVT() && FeatureOptionWrapper.isSupportVTVoiceAnswer()) {
            if (VTUtils.isVTRinging()) {
                IncomingCallMenuState.sCanVTVoiceAnswer = true;
            }
        }

        Call activeCall = CallList.getInstance().getActiveCall();
        Call holdCall = CallList.getInstance().getBackgroundCall();
        Call ringingCall = CallList.getInstance().getIncomingCall();
        if (activeCall != null && holdCall == null && ringingCall != null
                && ringingCall.getState() == Call.State.CALL_WAITING && !VTUtils.isVTCall(ringingCall)) {
            IncomingCallMenuState.sCanHangupActiveAndAnswerWaiting = true;
        }
        Log.d(TAG, "[updateIncomingMenuState], sCanVTVoiceAnswer / sCanHangupActiveAndAnswerWaiting: "
                + IncomingCallMenuState.sCanVTVoiceAnswer + " / " + IncomingCallMenuState.sCanHangupActiveAndAnswerWaiting);
    }

    /**
     * @return if the context is in landscape orientation.
     */
    public static boolean isLandscape(Context context) {
        return context.getResources().getConfiguration().orientation
                == Configuration.ORIENTATION_LANDSCAPE;
    }

    public static boolean hasPermanentMenuKey(Context context) {
        if (context == null) {
            Log.e(TAG, "context is null when hasPermanentMenuKey() is called.");
            /// if context is null, means the activity is not ready, so just return false.
            return false;
        }

        return ViewConfiguration.get(context).hasPermanentMenuKey();
    }

    /**
     * check whether to disable P-sensor or not.
     * @return  if true, disable p-sensor.
     */
    public static boolean checkScreenOnForVT() {
        boolean shouldVTScreenOn = false;
        if (FeatureOptionWrapper.MTK_VT3G324M_SUPPORT) {
            Call call = CallList.getInstance().getFirstCall();
            Log.d(TAG, "call = " + call);
            if (call != null) {
                int state = call.getState();
                Log.d(TAG, "state = " + state);
                if (state == Call.State.ACTIVE
                        || state == Call.State.DIALING
                        || state == Call.State.ONHOLD
                        || state == Call.State.REDIALING) {
                    shouldVTScreenOn = call.isVideoCall();
                }
            }
        }
        Log.d(TAG, "isVTScreenOn = " + shouldVTScreenOn);
        return shouldVTScreenOn;
    }

    public static boolean hasNavigationBar() {
        try {
            return WindowManagerGlobal.getWindowManagerService().hasNavigationBar();
        } catch (RemoteException e) {
            return false;
        }
    }

    public static void setAllVoiceMenuVisible(Menu menu, boolean visible) {
        final MenuItem addMenu = menu.findItem(R.id.menu_add_call);
        final MenuItem mergeMenu = menu.findItem(R.id.menu_merge_call);
        final MenuItem recordMenu = menu.findItem(R.id.menu_voice_record);
        final MenuItem voiceAnswerMenu = menu.findItem(R.id.menu_vt_voice_answer);
        final MenuItem hangupAllMenu = menu.findItem(R.id.menu_hangup_all);
        final MenuItem hangupHoldingMenu = menu.findItem(R.id.menu_hangup_holding);
        final MenuItem hangupActiveAndAnswerWaitingMenu = menu.findItem(R.id.menu_hangup_active_and_answer_waiting);
        final MenuItem ectMenu = menu.findItem(R.id.menu_ect);
        final MenuItem holdMenu = menu.findItem(R.id.menu_hold_voice);
        if (addMenu != null) {
            addMenu.setVisible(visible);
        }
        if (mergeMenu != null) {
            mergeMenu.setVisible(visible);
        }
        if (recordMenu != null) {
            recordMenu.setVisible(visible);
        }
        if (voiceAnswerMenu != null) {
            voiceAnswerMenu.setVisible(visible);
        }
        if (hangupAllMenu != null) {
            hangupAllMenu.setVisible(visible);
        }
        if (hangupHoldingMenu != null) {
            hangupHoldingMenu.setVisible(visible);
        }
        if (hangupActiveAndAnswerWaitingMenu != null) {
            hangupActiveAndAnswerWaitingMenu.setVisible(visible);
        }
        if (ectMenu != null) {
            ectMenu.setVisible(visible);
        }
        if (holdMenu != null) {
            holdMenu.setVisible(visible);
        }
    }

    public static void setAllVTMenuVisible(Menu menu, boolean visible) {
        final MenuItem switchCameraMenu = menu.findItem(R.id.menu_switch_camera);
        final MenuItem takePeerPhotoMenu = menu.findItem(R.id.menu_take_peer_photo);
        final MenuItem hideLocalVideoMenu = menu.findItem(R.id.menu_hide_local_video);
        final MenuItem swapVideosMenu = menu.findItem(R.id.menu_swap_videos);
        final MenuItem voiceRecordMenu = menu.findItem(R.id.menu_vt_record);
        final MenuItem videoSettingMenu = menu.findItem(R.id.menu_video_setting);
        if (switchCameraMenu != null) {
            switchCameraMenu.setVisible(visible);
        }
        if (takePeerPhotoMenu != null) {
            takePeerPhotoMenu.setVisible(visible);
        }
        if (hideLocalVideoMenu != null) {
            hideLocalVideoMenu.setVisible(visible);
        }
        if (swapVideosMenu != null) {
            swapVideosMenu.setVisible(visible);
        }
        if (voiceRecordMenu != null) {
            voiceRecordMenu.setVisible(visible);
        }
        if (videoSettingMenu != null) {
            videoSettingMenu.setVisible(visible);
        }
    }

    public static void setAllIncomingMenuVisible(Menu menu, boolean visible) {
        final MenuItem voiceAnswerMenu = menu.findItem(R.id.menu_vt_voice_answer);
        final MenuItem hangupActiveAndAnswerWaitingMenu = menu.findItem(R.id.menu_hangup_active_and_answer_waiting);
        if (voiceAnswerMenu != null) {
            voiceAnswerMenu.setVisible(visible);
        }
        if (hangupActiveAndAnswerWaitingMenu != null) {
            hangupActiveAndAnswerWaitingMenu.setVisible(visible);
        }
    }

    public static void setAllMenuVisible(Menu menu, boolean visible) {
        setAllVoiceMenuVisible(menu, visible);
        setAllVTMenuVisible(menu, visible);
        setAllIncomingMenuVisible(menu, visible);
    }

    public static boolean isVoiceMailNumber(String number, int slotId) {
        return PhoneNumberUtils.isVoiceMailNumberGemini(number, slotId);
    }

    private static boolean sIsUiShowing = false;
    public static void onUiShowing(boolean isShowing) {
        sIsUiShowing = isShowing;
    }
    public static boolean isUiShowing() {
        return sIsUiShowing;
    }

    private static boolean mPrivacyProtectOpen = false;
    /**
     * check whether PrivacyProtect open.
     * @return
     */
    public static boolean isprivacyProtectOpen() {
        Log.d(TAG, "mPrivacyProtectOpen: " + mPrivacyProtectOpen);
        return mPrivacyProtectOpen;
    }

    /**
     * set privacyProtectOpen value.
     * @param isPrivacyProtectOpen
     */
    public static void setprivacyProtectEnabled(boolean isPrivacyProtectOpen) {
        Log.d(TAG, "isPrivacyProtectOpen: " + isPrivacyProtectOpen);
        mPrivacyProtectOpen = isPrivacyProtectOpen;
    }
}
