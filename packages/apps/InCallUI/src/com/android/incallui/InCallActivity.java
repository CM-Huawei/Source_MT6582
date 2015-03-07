/*
 * Copyright (C) 2006 The Android Open Source Project
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

package com.android.incallui;

import com.android.incallui.InCallPresenter.InCallState;
import com.android.services.telephony.common.Call;
import com.android.services.telephony.common.Call.State;
import com.mediatek.incallui.InCallUtils;
import com.mediatek.incallui.ext.ExtensionManager;
import com.mediatek.incallui.vt.Constants;
import com.mediatek.incallui.vt.VTUtils;
import com.mediatek.incallui.vt.Constants.VTScreenMode;
import com.mediatek.incallui.vt.VTCallFragment;

import com.mediatek.incallui.vt.VTInCallScreenFlags;
import com.android.services.telephony.common.Call;
import com.android.services.telephony.common.Call.State;
import com.mediatek.incallui.ext.ExtensionManager;
import com.mediatek.incallui.vt.VTCallFragment;
import com.mediatek.incallui.wrapper.FeatureOptionWrapper;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.IntentFilter;
import android.content.DialogInterface.OnClickListener;
import android.content.DialogInterface.OnCancelListener;
import android.content.DialogInterface.OnDismissListener;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.graphics.Point;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.SystemProperties;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.Window;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.widget.Toast;

import com.android.services.telephony.common.Call;
import com.android.services.telephony.common.Call.State;
import com.mediatek.incallui.InCallUtils;
import com.mediatek.incallui.ext.ExtensionManager;
import com.mediatek.incallui.vt.VTCallFragment;
import com.mediatek.phone.SIMInfoWrapper;

import java.util.Timer;
import java.util.TimerTask;

/**
 * Phone app "in call" screen.
 */
public class InCallActivity extends Activity {

    public static final String SHOW_DIALPAD_EXTRA = "InCallActivity.show_dialpad";

    private static final int INVALID_RES_ID = -1;

    private CallButtonFragment mCallButtonFragment;
    private CallCardFragment mCallCardFragment;
    private AnswerFragment mAnswerFragment;
    private DialpadFragment mDialpadFragment;
    private ConferenceManagerFragment mConferenceManagerFragment;
    private boolean mIsForegroundActivity;
    private AlertDialog mDialog; // use for three types: google default error dialog / SuppMessage dialog / VT drop back dialog

    /** Use to pass 'showDialpad' from {@link #onNewIntent} to {@link #onResume} */
    private boolean mShowDialpadRequested;

    @Override
    protected void onCreate(Bundle icicle) {
        Log.d(this, "onCreate()...  this = " + this);

        super.onCreate(icicle);
        /// Add for get sim information. @{
        SIMInfoWrapper.getDefault().init(this);
        /// @}

        /// M: For SmartBook @{
        // change InCallScreen's orientation to be "portait".
        setOrientation();
        /// @}

        /// M: set the window flags @{
        /// Original code:
        /*
        // set this flag so this activity will stay in front of the keyguard
        // Have the WindowManager filter out touch events that are "too fat".
        int flags = WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                | WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
                | WindowManager.LayoutParams.FLAG_IGNORE_CHEEK_PRESSES);

        getWindow().addFlags(flags);
        */
        setWindowFlag();
        /// @}

        requestWindowFeature(Window.FEATURE_NO_TITLE);

        // TODO(klp): Do we need to add this back when prox sensor is not available?
        // lp.inputFeatures |= WindowManager.LayoutParams.INPUT_FEATURE_DISABLE_USER_ACTIVITY;

        /// M: Add for plugin.@{
        ExtensionManager.getInstance().initPlugin(getApplicationContext());
        ExtensionManager.getInstance().getInCallUIExtension().onCreate(icicle, this, CallList.getInstance());
        /// @}

        // Inflate everything in incall_screen.xml and add it to the screen.
        setContentView(R.layout.incall_screen);

        initializeInCall();

        /// M: For ALPS01262955 @{
        // "back" key will destroy InCallActivity,
        // so maybe onCreate() will be called instead of onNewIntent().
        // So we also need handle the intent here.
        internalResolveIntent(getIntent());
        /// @}

        Log.d(this, "onCreate(): exit");
    }

    @Override
    protected void onStart() {
        Log.d(this, "onStart()...");
        super.onStart();

        // setting activity should be last thing in setup process
        InCallPresenter.getInstance().setActivity(this);
    }

    @Override
    protected void onResume() {
        Log.i(this, "onResume()...");
        super.onResume();
        mIsForegroundActivity = true;
        InCallPresenter.getInstance().onUiShowing(true);
        ///M: we keep the showing status of dialpad synchronized with call button.
        mShowDialpadRequested = mShowDialpadRequested || mCallButtonFragment.getShowDialpadButton().isChecked();
        if (mShowDialpadRequested) {
            ///M: we should hide manage conference button if dialpad shown.
            if(mCallButtonFragment.getPresenter().getCall().isConferenceCall()){
                mCallButtonFragment.hideExtraRow();
            }
            mCallButtonFragment.displayDialpad(true);
            mShowDialpadRequested = false;
        }

        /// M: Add for ALPS01265934
        //  Update related UI when the InCallActivity onResume
        updateInCallUI();
    }

    // onPause is guaranteed to be called when the InCallActivity goes
    // in the background.
    @Override
    protected void onPause() {
        Log.d(this, "onPause()...");
        super.onPause();

        mIsForegroundActivity = false;

        mDialpadFragment.onDialerKeyUp(null);

        InCallPresenter.getInstance().onUiShowing(false);

        /// M: ALPS01237618 @{
        // Dismiss any dialogs we may have brought up, just to be 100%
        // sure they won't still be around when we get back here.
        dismissPendingDialogs();
        /// @}
        /**
         * M: [SmartBook][ALPS01052168]: turn off screen when the call screen
         * is paused when Smartbook connected. @{
         */
        InCallPresenter.getInstance().getProximitySensor().screenOffForSmartBook();
        /** @} */
    }

    @Override
    protected void onStop() {
        Log.d(this, "onStop()...");
        super.onStop();
        /** M: For VT @{ */
        if (VTUtils.isVTIdle()) {
            setVTScreenMode(VTScreenMode.VT_SCREEN_CLOSE);
        }
        /** @} */
    }

    @Override
    protected void onDestroy() {
        Log.d(this, "onDestroy()...  this = " + this);

        InCallPresenter.getInstance().setActivity(null);

        super.onDestroy();

        /// M: Add for sim information. @{
        SIMInfoWrapper.getDefault().release();
        /// @}
        /// M: privacy protect feature @{
        // reset privacyProtectEnable
        if (FeatureOptionWrapper.isSupportPrivacyProtect()) {
            InCallUtils.setprivacyProtectEnabled(false);
        }
        /// @}
        /// M: DM lock Feature.
        unregisterReceiver(mDMLockReceiver);
        /// M: ALPS01264365 release voice commmand.
        CallCommandClient.getInstance().stopVoiceCommand();
        CallCommandClient.getInstance().clearVoiceCommandHandler();
        /// M: Add for Extension.@{
        ExtensionManager.getInstance().getInCallUIExtension().onDestroy(this);
        /// @}
    }

    /**
     * Returns true when theActivity is in foreground (between onResume and onPause).
     */
    /* package */ boolean isForegroundActivity() {
        return mIsForegroundActivity;
    }

    private boolean hasPendingErrorDialog() {
        return (mDialog != null || mVTVoiceAnswerProgressDialog != null);
    }
    /**
     * Dismisses the in-call screen.
     *
     * We never *really* finish() the InCallActivity, since we don't want to get destroyed and then
     * have to be re-created from scratch for the next call.  Instead, we just move ourselves to the
     * back of the activity stack.
     *
     * This also means that we'll no longer be reachable via the BACK button (since moveTaskToBack()
     * puts us behind the Home app, but the home app doesn't allow the BACK key to move you any
     * farther down in the history stack.)
     *
     * (Since the Phone app itself is never killed, this basically means that we'll keep a single
     * InCallActivity instance around for the entire uptime of the device.  This noticeably improves
     * the UI responsiveness for incoming calls.)
     */
    @Override
    public void finish() {
        Log.i(this, "finish().  Dialog showing: " + (mDialog != null));

        /// M: For ALPS01265452. @{
        // when there is no any call, dismiss all popup menu.
        if (mCallButtonFragment != null) {
            mCallButtonFragment.dismissPopupMenu();
        }
        /// @}

        // skip finish if we are still showing a dialog.
        if (!hasPendingErrorDialog() && !mAnswerFragment.hasPendingDialogs()) {
            Log.d(this, "truly finish~~");
            super.finish();
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        Log.d(this, "onNewIntent: intent = " + intent);
        /// Add for get sim information. @{
        SIMInfoWrapper.getDefault().init(this);
        /// @}

        /// M: For SmartBook @{
        // light screen on if InCallScreen is paused by new Intent.
        // onPause() will light screen off, see ALPS01052168.
        InCallPresenter.getInstance().getProximitySensor().lightOnScreenForSmartBook();
        /// @}

        // We're being re-launched with a new Intent.  Since it's possible for a
        // single InCallActivity instance to persist indefinitely (even if we
        // finish() ourselves), this sequence can potentially happen any time
        // the InCallActivity needs to be displayed.

        // Stash away the new intent so that we can get it in the future
        // by calling getIntent().  (Otherwise getIntent() will return the
        // original Intent from when we first got created!)
        setIntent(intent);

        // Activities are always paused before receiving a new intent, so
        // we can count on our onResume() method being called next.

        // Just like in onCreate(), handle the intent.
        internalResolveIntent(intent);
    }

    @Override
    public void onBackPressed() {
        Log.d(this, "onBackPressed()...");

        /// M: for ALPS01251903 @{
        // when incoming, disable back key and home key
        if(InCallPresenter.getInstance().getInCallState().isIncoming()) {
            Log.d(this,"BACK key while incoming: ignored~~");
            return;
        }
        /// @}

        // BACK is also used to exit out of any "special modes" of the
        // in-call UI:

        if (mDialpadFragment.isVisible()) {
            mCallButtonFragment.displayDialpad(false);  // do the "closing" animation
            ///M: if the conference call is visible, 
            // when the dialpad canceled we need to show manage conference button @{
            if (mCallButtonFragment.getPresenter().getCall().isConferenceCall()) {
                mCallButtonFragment.showManageConferenceCallButton();
            }
            /// @}
            return;
        } else if (mConferenceManagerFragment.isVisible()) {
            mConferenceManagerFragment.setVisible(false);
            return;
        }

        // Always disable the Back key while an incoming call is ringing
        final Call call = CallList.getInstance().getIncomingCall();
        if (call != null) {
            Log.d(this, "Consume Back press for an inconing call");
            return;
        }

        // Nothing special to do.  Fall back to the default behavior.
        super.onBackPressed();
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        // push input to the dialer.
        if ((mDialpadFragment.isVisible()) && (mDialpadFragment.onDialerKeyUp(event))){
            return true;
        } else if (keyCode == KeyEvent.KEYCODE_CALL) {
            // Always consume CALL to be sure the PhoneWindow won't do anything with it
            return true;
        }
        return super.onKeyUp(keyCode, event);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        switch (keyCode) {

            /// M: for ALPS01251903 @{
            // when incoming, disable back key and home key
            case KeyEvent.KEYCODE_HOME:
                Log.d(this,"ignore KEYCODE_HOME ~~");
                return true;
            /// @}

            case KeyEvent.KEYCODE_CALL:
                boolean handled = InCallPresenter.getInstance().handleCallKey();
                if (!handled) {
                    Log.w(this, "InCallActivity should always handle KEYCODE_CALL in onKeyDown");
                }
                // Always consume CALL to be sure the PhoneWindow won't do anything with it
                return true;

            // Note there's no KeyEvent.KEYCODE_ENDCALL case here.
            // The standard system-wide handling of the ENDCALL key
            // (see PhoneWindowManager's handling of KEYCODE_ENDCALL)
            // already implements exactly what the UI spec wants,
            // namely (1) "hang up" if there's a current active call,
            // or (2) "don't answer" if there's a current ringing call.

            case KeyEvent.KEYCODE_CAMERA:
                // Disable the CAMERA button while in-call since it's too
                // easy to press accidentally.
                return true;

            case KeyEvent.KEYCODE_VOLUME_UP:
            case KeyEvent.KEYCODE_VOLUME_DOWN:
            case KeyEvent.KEYCODE_VOLUME_MUTE:
                // Ringer silencing handled by PhoneWindowManager.
                break;

            case KeyEvent.KEYCODE_MUTE:
                // toggle mute
                CallCommandClient.getInstance().mute(!AudioModeProvider.getInstance().getMute());
                return true;

            // Various testing/debugging features, enabled ONLY when VERBOSE == true.
            case KeyEvent.KEYCODE_SLASH:
                if (Log.VERBOSE) {
                    Log.v(this, "----------- InCallActivity View dump --------------");
                    // Dump starting from the top-level view of the entire activity:
                    Window w = this.getWindow();
                    View decorView = w.getDecorView();
                    decorView.debug();
                    return true;
                }
                break;
            case KeyEvent.KEYCODE_EQUALS:
                // TODO: Dump phone state?
                break;
        }

        if (event.getRepeatCount() == 0 && handleDialerKeyDown(keyCode, event)) {
            return true;
        }

        return super.onKeyDown(keyCode, event);
    }

    private boolean handleDialerKeyDown(int keyCode, KeyEvent event) {
        Log.v(this, "handleDialerKeyDown: keyCode " + keyCode + ", event " + event + "...");

        // As soon as the user starts typing valid dialable keys on the
        // keyboard (presumably to type DTMF tones) we start passing the
        // key events to the DTMFDialer's onDialerKeyDown.
        if (mDialpadFragment.isVisible()) {
            return mDialpadFragment.onDialerKeyDown(event);

            // TODO: If the dialpad isn't currently visible, maybe
            // consider automatically bringing it up right now?
            // (Just to make sure the user sees the digits widget...)
            // But this probably isn't too critical since it's awkward to
            // use the hard keyboard while in-call in the first place,
            // especially now that the in-call UI is portrait-only...
        }

        return false;
    }

    @Override
    public void onConfigurationChanged(Configuration config) {
        InCallPresenter.getInstance().getProximitySensor().onConfigurationChanged(config);
    }

    private void internalResolveIntent(Intent intent) {
        final String action = intent.getAction();

        if (action.equals(intent.ACTION_MAIN)) {
            // This action is the normal way to bring up the in-call UI.
            //
            // But we do check here for one extra that can come along with the
            // ACTION_MAIN intent:

            if (intent.hasExtra(SHOW_DIALPAD_EXTRA)) {
                // SHOW_DIALPAD_EXTRA can be used here to specify whether the DTMF
                // dialpad should be initially visible.  If the extra isn't
                // present at all, we just leave the dialpad in its previous state.

                final boolean showDialpad = intent.getBooleanExtra(SHOW_DIALPAD_EXTRA, false);
                Log.d(this, "- internalResolveIntent: SHOW_DIALPAD_EXTRA: " + showDialpad);

                relaunchedFromDialer(showDialpad);
            }

            return;
        }
    }

    private void relaunchedFromDialer(boolean showDialpad) {
        mShowDialpadRequested = showDialpad;

        if (mShowDialpadRequested) {
            // If there's only one line in use, AND it's on hold, then we're sure the user
            // wants to use the dialpad toward the exact line, so un-hold the holding line.
            final Call call = CallList.getInstance().getActiveOrBackgroundCall();
            if (call != null && call.getState() == State.ONHOLD) {
                CallCommandClient.getInstance().hold(call.getCallId(), false);
            }
        }
    }

    private void initializeInCall() {
        if (mCallButtonFragment == null) {
            mCallButtonFragment = (CallButtonFragment) getFragmentManager()
                    .findFragmentById(R.id.callButtonFragment);
            mCallButtonFragment.getView().setVisibility(View.INVISIBLE);
        }

        if (mCallCardFragment == null) {
            mCallCardFragment = (CallCardFragment) getFragmentManager()
                    .findFragmentById(R.id.callCardFragment);
        }

        if (mAnswerFragment == null) {
            mAnswerFragment = (AnswerFragment) getFragmentManager()
                    .findFragmentById(R.id.answerFragment);
        }

        if (mDialpadFragment == null) {
            mDialpadFragment = (DialpadFragment) getFragmentManager()
                    .findFragmentById(R.id.dialpadFragment);
            mDialpadFragment.getView().setVisibility(View.INVISIBLE);
        }

        if (mConferenceManagerFragment == null) {
            mConferenceManagerFragment = (ConferenceManagerFragment) getFragmentManager()
                    .findFragmentById(R.id.conferenceManagerFragment);
            mConferenceManagerFragment.getView().setVisibility(View.INVISIBLE);
        }

        /// M: For MTK features, such as VT
        initializeInCallMTK();
    }

    private void toast(String text) {
        final Toast toast = Toast.makeText(this, text, Toast.LENGTH_SHORT);

        toast.show();
    }

    /**
     * Simulates a user click to hide the dialpad. This will update the UI to show the call card,
     * update the checked state of the dialpad button, and update the proximity sensor state.
     */
    public void hideDialpadForDisconnect() {
        mCallButtonFragment.displayDialpad(false);
    }

    public void dismissKeyguard(boolean dismiss) {
        if (dismiss) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD);
        } else {
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD);
        }
    }

    public void displayDialpad(boolean showDialpad) {
        if (showDialpad) {
            mDialpadFragment.setVisible(true);
            if (InCallUtils.isLandscape(this)) {
                mCallCardFragment.setVisible(true);
            } else {
                mCallCardFragment.setVisible(false);
            }
        } else {
            mDialpadFragment.setVisible(false);
            mCallCardFragment.setVisible(true);
        }

        InCallPresenter.getInstance().getProximitySensor().onDialpadVisible(showDialpad);
    }

    public boolean isDialpadVisible() {
        return mDialpadFragment.isVisible();
    }

    public void displayManageConferencePanel(boolean showPanel) {
        if (showPanel) {
            mConferenceManagerFragment.setVisible(true);
        }
    }

    public void showPostCharWaitDialog(int callId, String chars) {
        final PostCharDialogFragment fragment = new PostCharDialogFragment(callId,  chars);
        fragment.show(getFragmentManager(), "postCharWait");
    }

    @Override
    public boolean dispatchPopulateAccessibilityEvent(AccessibilityEvent event) {
        if (mCallCardFragment != null) {
            mCallCardFragment.dispatchPopulateAccessibilityEvent(event);
        }
        return super.dispatchPopulateAccessibilityEvent(event);
    }

    public void maybeShowErrorDialogOnDisconnect(Call.DisconnectCause cause) {
        Log.d(this, "maybeShowErrorDialogOnDisconnect");

        if (!isFinishing()) {
            final int resId = getResIdForDisconnectCause(cause);
            if (resId != INVALID_RES_ID) {
                showErrorDialog(resId);
            }
        }
    }

    public void dismissPendingDialogs() {
        if (mDialog != null) {
            mDialog.dismiss();
            mDialog = null;
        }
        /// M: For voice answer VT call @{
        if (mVTVoiceAnswerProgressDialog != null) {
            mVTVoiceAnswerProgressDialog .dismiss();
            mVTVoiceAnswerProgressDialog = null;
        }
        /// @}
        mAnswerFragment.dismissPendingDialogues();

        /// M: For ALPS01274235. @{
        // dismiss all popup menu when activity onPause.
        if (mCallButtonFragment != null) {
            mCallButtonFragment.dismissPopupMenu();
        }
        /// @}
    }

    public void dismissPendingDialogsAndHideConferenceFragment() {
        /// M: hide the conference management ui if current incoming a call and right now 
        /// showing the conference management screen @{
        if (mConferenceManagerFragment.isFragmentVisible()) {
            mConferenceManagerFragment.setVisible(false);
        }
        dismissPendingDialogs();
        /// @}
    }

    /**
     * Utility function to bring up a generic "error" dialog.
     */
    private void showErrorDialog(int resId) {
        final CharSequence msg = getResources().getText(resId);
        Log.i(this, "Show Dialog: " + msg);

        dismissPendingDialogs();

        mDialog = new AlertDialog.Builder(this)
            .setMessage(msg)
            .setPositiveButton(R.string.ok, new OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    onDialogDismissed();
                }})
            .setOnCancelListener(new OnCancelListener() {
                @Override
                public void onCancel(DialogInterface dialog) {
                    onDialogDismissed();
                }})
            /// M: for ALPS01260145 && ALPS01259344 @{
            // critical Google bug: if the dialog is not dismissed by user click,
            // the call end screen cannot exit because mDialog is not null
            .setOnDismissListener(new OnDismissListener() {
                @Override
                public void onDismiss(DialogInterface dialog) {
                    onDialogDismissed();
                }
            })
            /// @}
            .create();

        mDialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        mDialog.show();
    }

    private int getResIdForDisconnectCause(Call.DisconnectCause cause) {
        int resId = INVALID_RES_ID;

        if (cause == Call.DisconnectCause.CALL_BARRED) {
            resId = R.string.callFailed_cb_enabled;
        } else if (cause == Call.DisconnectCause.FDN_BLOCKED) {
            resId = R.string.callFailed_fdn_only;
        } else if (cause == Call.DisconnectCause.CS_RESTRICTED) {
            resId = R.string.callFailed_dsac_restricted;
        } else if (cause == Call.DisconnectCause.CS_RESTRICTED_EMERGENCY) {
            resId = R.string.callFailed_dsac_restricted_emergency;
        } else if (cause == Call.DisconnectCause.CS_RESTRICTED_NORMAL) {
            resId = R.string.callFailed_dsac_restricted_normal;
        }

        return resId;
    }

    private void onDialogDismissed() {
        /// M: For voice answer VT call @{
        mVTVoiceAnswerProgressDialog = null;
        /// @}
        mDialog = null;
        InCallPresenter.getInstance().onDismissDialog();
    }

    // ---------------- MTK ---------------------------------
    public static final String VT_CALL_EXTRA = "com.android.phone.extra.video";

    private VTCallFragment mVTCallFragment = null;
    private int mNavigationBarHeight;
    private int mStatusBarHeight;

    private void initializeInCallMTK() {
        if (FeatureOptionWrapper.isSupportVT() && mVTCallFragment == null) {
            Log.i(this, "[initializeInCallMTK]on VT call, init the VTCallFragment");
            mVTCallFragment = (VTCallFragment) getFragmentManager()
                    .findFragmentById(R.id.vtCallFragment);
        }
        /// DM Lock Feature @ {
        IntentFilter lockFilter = new IntentFilter(ACTION_LOCKED);
        lockFilter.addAction(ACTION_UNLOCK);

        if (FeatureOptionWrapper.isSupportPrivacyProtect()) {
            Log.d(this, "register ppl lock message");
            lockFilter.addAction(NOTIFY_LOCKED);
            lockFilter.addAction(NOTIFY_UNLOCK);
        }
        registerReceiver(mDMLockReceiver, lockFilter);
        /// @]
    }

    public void updateInCallActivity() {
        Log.d(this, "updateInCallActivity()... call: " + CallList.getInstance().getFirstCall());

        // if VT is active and no ringing call exist, set VT_SCREEN_OPEN.
        // else if has ringing call exist, set VT_SCREEN_CLOSE
        // else just keep previous setting.
        // When call is totally idle here, InCallActivity is just begin to finish,
        // before InCallActivity is totally finished, we still do not set VT_SCREEN_CLOSE; see ALPS01269794
        if (InCallPresenter.getInstance().getInCallState() != InCallState.INCOMING
                && (VTUtils.isVTActive() || VTUtils.isVTOutgoing())) {
            setVTScreenMode(VTScreenMode.VT_SCREEN_OPEN);
        } else if (!VTUtils.isVTActive() && VTUtils.existNonVTCall()) {
            Log.d(this, "[updateInCallActivity]Existing non-VT Call while VT not active, should close VT_SCREEN");
            setVTScreenMode(VTScreenMode.VT_SCREEN_CLOSE);
        } else if (InCallPresenter.getInstance().getInCallState() == InCallState.INCOMING) {
            Log.d(this, "[updateInCallActivity]InCallState.INCOMING, should close VT_SCREEN");
            setVTScreenMode(VTScreenMode.VT_SCREEN_CLOSE);
        // maybe the VTCallFragment is null
        } else if (mVTCallFragment != null) {
            Log.d(this, "keep current VT Screen Mode.");
            setVTScreenMode(mVTCallFragment.getVTScreenMode());
        }

        if (mAnswerFragment != null) {
            // in below function will judge whether mGlowpad is visible, so it's safe to call it. 
            mAnswerFragment.updateIncomingCallMenuButton();
        }

    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        Log.d(this, "onCreateOptionsMenu()...");
        MenuInflater inflate = new MenuInflater(this);
        // because onCreateOptionsMenu() will only be called once, so we must inflate a menu with all menu items.
        inflate.inflate(R.menu.mtk_incall_menu, menu);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        Log.d(this, "onPrepareOptionsMenu()...");
        InCallUtils.setAllMenuVisible(menu, false);
        if (InCallPresenter.getInstance().getInCallState() == InCallState.INCOMING) {
            mAnswerFragment.setupIncomingMenuItems(menu);
        } else {
            mCallButtonFragment.setupMenuItems(menu);
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (InCallPresenter.getInstance().getInCallState() == InCallState.INCOMING) {
            return mAnswerFragment.onOptionsItemSelected(item);
        } else {
            return mCallButtonFragment.onOptionsItemSelected(item);
        }
    }

    public void handleVTMenuClick(MenuItem menuItem) {
        if (mVTCallFragment != null) {
            mVTCallFragment.handleVTMenuClick(menuItem);
        }
    }

    public void onVTSwapVideoClick() {
        if (mVTCallFragment != null) {
            mVTCallFragment.onVTSwapVideoClick();
        }
    }

    public void showSuppMessageDialog(String message) {
        if (mDialog != null) {
            Log.i(this, "- DISMISSING mSuppServiceFailureDialog.");
            // It's safe to dismiss() a dialog at's already dismissed.
            mDialog.dismiss();
            mDialog = null;
        }

        mDialog = new AlertDialog.Builder(this).setMessage(message)
                .setPositiveButton(R.string.ok, null).create();
        mDialog.setOnDismissListener(new OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface dialog) {
                onDialogDismissed();
            }
        });
        mDialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND);
        mDialog.show();
    }

    /// M: For SmartBook @{
    // change InCallScreen's orientation to be "portait".
    private void setOrientation() {
        if(FeatureOptionWrapper.MTK_SMARTBOOK_SUPPORT || FeatureOptionWrapper.MTK_HDMI_SUPPORT) {
            Log.d(this, "MTK_SMARTBOOK_SUPPORT / MTK_HDMI_SUPPORT: " + FeatureOptionWrapper.MTK_SMARTBOOK_SUPPORT + " / "
                    + FeatureOptionWrapper.MTK_HDMI_SUPPORT);
            String ProductCharacteristic = SystemProperties.get("ro.build.characteristics");
            if (!"tablet".equals(ProductCharacteristic)) {
                Log.d(this, "set InCallScreen's orientation to be ActivityInfo.SCREEN_ORIENTATION_PORTRAIT");
                this.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
            }
        }
    }
    /// @}

    private void setWindowFlag() {
        // set this flag so this activity will stay in front of the keyguard
        int flags = WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                        | WindowManager.LayoutParams.FLAG_IGNORE_CHEEK_PRESSES
                        | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON;

        Call call = CallList.getInstance().getActiveOrBackgroundCall();
        if (call != null && Call.State.isConnected(call.getState())) {
            // While we are in call, the in-call screen should dismiss the keyguard.
            // This allows the user to press Home to go directly home without going through
            // an insecure lock screen.
            // But we do not want to do this if there is no active call so we do not
            // bypass the keyguard if the call is not answered or declined.

            /// M: DM lock@{
            if (!InCallUtils.isDMLocked()) {
                flags |= WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD;
                Log.d(this, "set window FLAG_DISMISS_KEYGUARD flag ");
            }
            /// @}
        }

        final WindowManager.LayoutParams lp = getWindow().getAttributes();
        lp.flags |= flags;
        getWindow().setAttributes(lp);
    }

    /// For: VT drop back to voice call. @{
    public boolean maybeShowErrorDialogOnDisconnectForVT(Call.DisconnectCause cause, boolean isIncoming, String number, int slot) {
        Log.d(this, "maybeShowErrorDialogOnDisconnectForVT()... cause / isIncoming / number / slot: " + cause + " / "
                + isIncoming + " / " + number + " / " + slot);

        if (!isFinishing()) {
            if (isForegroundActivity()) {
                int resId = VTUtils.getResIdForVTErrorDialog(cause);
                if (resId != INVALID_RES_ID) {
                    showErrorDialog(resId);
                    return true;
                }
            }

            if (!isIncoming) {
                int resId = VTUtils.getResIdForVTReCallDialog(cause);
                if (resId != INVALID_RES_ID) {
                    // We only handle disconnect cause, which we are interested, or just let it go.
                    if (isForegroundActivity()
                            && !VTInCallScreenFlags.getInstance().mVTAutoDropBack) {
                        showReCallDialog(resId, number, slot);
                    }
                    return true;
                }
            }
        }
        return false;
    }

    private void showReCallDialog(final int resid, final String number, final int slot) {
        Log.d(this, "showReCallDialog... ");

        if (null != mDialog) {
            mDialog.dismiss();
            mDialog = null;
        }
        CharSequence msg = getResources().getText(resid);

        DialogInterface.OnClickListener clickListener1
                = new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                Log.d(this, "showReCallDialog... , on click, which=" + which);
                if (null != mDialog) {
                    mDialog.dismiss();
                    mDialog = null;
                }
                // we already do turn off speaker in callNotifier.onDisconnect(), so no need turn off speaker any more.
                VTUtils.makeVoiceReCall(getApplicationContext(), number, slot);
            }
        };

        DialogInterface.OnClickListener clickListener2
                = new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                Log.d(this, "showReCallDialog... , on click, which=" + which);
                if (null != mDialog) {
                    mDialog.dismiss();
                    mDialog = null;
                }
                finish();
            }
        };

        OnCancelListener cancelListener = new OnCancelListener() {
            @Override
            public void onCancel(DialogInterface dialog) {
                onDialogDismissed();
            }
        };

        mDialog = new AlertDialog.Builder(this).setMessage(msg)
                .setNegativeButton(getResources().getString(android.R.string.cancel), clickListener2)
                .setPositiveButton(getResources().getString(android.R.string.ok), clickListener1)
                .setOnCancelListener(cancelListener).create();
        mDialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        mDialog.setOnDismissListener(new OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface dialog) {
                onDialogDismissed();
            }
        });

        mDialog.show();
    }
    /// @}

    /// M: For voice answer VT call @{
    private static final int VT_VOICE_ANSWER_OVER = 161;
    private boolean mInVoiceAnswerVideoCall;
    private ProgressDialog mVTVoiceAnswerProgressDialog;
    private Timer mVTVoiceAnswerTimer = null;

    public boolean getInVoiceAnswerVideoCall() {
        return mInVoiceAnswerVideoCall;
    }
    
    public void setInVoiceAnswerVideoCall(boolean value) {
        Log.d(this, "setInVoiceAnswerVideoCall() : " + value);
        if (value) {
            mInVoiceAnswerVideoCall = true;
            mVTVoiceAnswerProgressDialog = new ProgressDialog(this);
            mVTVoiceAnswerProgressDialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
            mVTVoiceAnswerProgressDialog.setMessage(getResources().getString(R.string.vt_wait_voice));
            mVTVoiceAnswerProgressDialog.setOnCancelListener(new OnCancelListener() {
                public void onCancel(DialogInterface dialog) {
                    onDialogDismissed();
                    mInVoiceAnswerVideoCall = false;
                }
            });
            mVTVoiceAnswerProgressDialog.setOnDismissListener(new OnDismissListener() {
                @Override
                public void onDismiss(DialogInterface dialog) {
                    onDialogDismissed();
                }
            });

            mVTVoiceAnswerProgressDialog.show();

            mVTVoiceAnswerTimer = new Timer();
            mVTVoiceAnswerTimer.schedule(new TimerTask() {
                public void run() {
                    mHandler.sendMessage(Message.obtain(mHandler, VT_VOICE_ANSWER_OVER));
                }
            }, 15 * 1000);
        } else {
            mInVoiceAnswerVideoCall = false;
            CallCommandClient.getInstance().setVTVoiceAnswerRelated(false, null);
            if (mVTVoiceAnswerProgressDialog != null) {
                mVTVoiceAnswerProgressDialog.dismiss();
                mVTVoiceAnswerProgressDialog = null;
            }
            if (mVTVoiceAnswerTimer != null) {
                mVTVoiceAnswerTimer.cancel();
                mVTVoiceAnswerTimer = null;
            }
        }
    }
    
    private Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
            case VT_VOICE_ANSWER_OVER:
                Log.d(this, "VT_VOICE_ANSWER_OVER ! ");
                if (getInVoiceAnswerVideoCall()) {
                    setInVoiceAnswerVideoCall(false);
                    finish();
                }
                break;

            default:
                Log.e(this, "unhandled msg!!");
            }
        }
    };
    /// @}

    /*
     * To enable or disable home key
     * when incoming screen in showing, home key should be disabled
     */
    void enableHomeKeyDispatched(boolean enable) {
        Log.d(this,"enableHomeKeyDispatched, enable = " + enable);
        final Window window = getWindow();
        final WindowManager.LayoutParams lp = window.getAttributes();
        if (enable) {
            lp.flags |= WindowManager.LayoutParams.FLAG_HOMEKEY_DISPATCHED;
        } else {
            lp.flags &= ~WindowManager.LayoutParams.FLAG_HOMEKEY_DISPATCHED;
        }
        window.setAttributes(lp);
    }

    /// M: dispatch when call card layout changed, then begin to layout vt screen.
    public void onCallCardLayoutChange(int callCardBottom) {
        if (mVTCallFragment != null) {
            mVTCallFragment.amendVtLayout(callCardBottom);
        }
    }

    /// M: dispatch the vt layout finish event, then begin to layout the vt button.
    public void onFinishVtVideoLayout(int marginLeft, int marginBottom, int height) {
        if (mCallButtonFragment != null) {
            mCallButtonFragment.amendCallButtonLayout(marginLeft, marginBottom, height);
        }

        mVTCallFragment.onFinishLayoutAmend();
    }

    public void setVTScreenMode(VTScreenMode mode) {
        if (!FeatureOptionWrapper.isSupportVT() || mVTCallFragment == null) {
            Log.d(this, "setVTScreenMode()... VT is not support, or not in VTCall, just return.");
            return;
        }
        Log.d(this, "setVTScreenMode()... mode: " + mode);
        if (VTScreenMode.VT_SCREEN_CLOSE == mode) {
            mCallCardFragment.setPhotoVisible(true);
        } else if (VTScreenMode.VT_SCREEN_OPEN == mode) {
            mCallCardFragment.setPhotoVisible(false);
        }
        mVTCallFragment.setVTScreenMode(mode);
    }

    /**
     * M: Add for ALPS01265934
     * Update related UI at this point.
     * TODO: Many UI part many need update here.
     */
    private void updateInCallUI() {
        /// update mCallCardFragment.
        if (mCallCardFragment != null) {
            Log.d(this, "[onResume] call updateSimIndicator");
            mCallCardFragment.updateCallCard();
        }
        /// For VT
        if (mCallButtonFragment != null) {
            mCallButtonFragment.updateVTCallButton();
        }
        updateInCallActivity();
    }

    /// DM lock @{
    private static String ACTION_LOCKED = "com.mediatek.dm.LAWMO_LOCK";
    private static String ACTION_UNLOCK = "com.mediatek.dm.LAWMO_UNLOCK";
    /// @}

    /// privacy protect @{
    private static String NOTIFY_LOCKED = "com.mediatek.ppl.NOTIFY_LOCK";
    private static String NOTIFY_UNLOCK = "com.mediatek.ppl.NOTIFY_UNLOCK";
    /// @}

    private final BroadcastReceiver mDMLockReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            Log.d(InCallActivity.this, "action: " + action);
            /// if privacy protect open,should disable NavigationBar. For ALPS01414144 @
            if (action.equals(NOTIFY_LOCKED)) {
                InCallUtils.setprivacyProtectEnabled(true);
                CallCommandClient.getInstance().setSystemBarNavigationEnabled(false);
            } else if (action.equals(NOTIFY_UNLOCK)) {
                InCallUtils.setprivacyProtectEnabled(false);
                Call call = CallList.getInstance().getIncomingCall();
                // if exist incoming call, the system bar should disable.
                if (call == null) {
                    CallCommandClient.getInstance().setSystemBarNavigationEnabled(true);
                }
            }
            /// @}
            Call call = CallList.getInstance().getActiveOrBackgroundCall();
            if (call == null || !Call.State.isConnected(call.getState())) {
                Log.d(this, "mDMLockReceiver , return");
                return;
            }
            if (action.equals(ACTION_LOCKED) || action.equals(NOTIFY_LOCKED)) {
                int msg = R.string.dm_lock;
                if (call.getState() == Call.State.IDLE) {
                    return;
                } else {
                    Toast.makeText(InCallActivity.this, msg, Toast.LENGTH_LONG).show();
                }
            } else if (action.equals(ACTION_UNLOCK) || action.equals(NOTIFY_UNLOCK)) {
                int msg = R.string.dm_unlock;
                if (call.getState() == Call.State.IDLE) {
                    return;
                } else {
                    Toast.makeText(InCallActivity.this, msg, Toast.LENGTH_LONG).show();
                }
            }

            InCallPresenter.getInstance().onCallListChange(CallList.getInstance());
        }
    };
}
