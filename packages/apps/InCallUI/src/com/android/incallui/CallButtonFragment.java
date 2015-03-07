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

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.Context;
import android.graphics.drawable.LayerDrawable;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.SystemProperties;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup.MarginLayoutParams;
import android.view.WindowManager;
import android.view.View.OnClickListener;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.RelativeLayout;
import android.widget.Toast;
import android.widget.PopupMenu.OnDismissListener;
import android.widget.PopupMenu.OnMenuItemClickListener;
import android.widget.ToggleButton;

import com.android.services.telephony.common.AudioMode;
import com.mediatek.common.dm.DmAgent;
import com.mediatek.incallui.InCallUtils;
import com.mediatek.incallui.ext.ExtensionManager;
import com.mediatek.incallui.recorder.PhoneRecorderUtils;
import com.mediatek.incallui.recorder.PhoneRecorderUtils.RecorderState;
import com.mediatek.incallui.vt.VTBackgroundBitmapHandler;
import com.mediatek.incallui.vt.VTInCallScreenFlags;
import com.mediatek.incallui.vt.VTManagerLocal;
import com.mediatek.incallui.vt.VTUtils;


/**
 * Fragment for call control buttons
 */
public class CallButtonFragment
        extends BaseFragment<CallButtonPresenter, CallButtonPresenter.CallButtonUi>
        implements CallButtonPresenter.CallButtonUi, OnMenuItemClickListener, OnDismissListener,
        View.OnClickListener, CompoundButton.OnCheckedChangeListener {

    private ImageButton mMuteButton;
    private ImageButton mAudioButton;
    private ImageButton mHoldButton;
    private ToggleButton mShowDialpadButton;
    private ImageButton mMergeButton;
    private ImageButton mAddCallButton;
    private ImageButton mSwapButton;

    private PopupMenu mAudioModePopup;
    private boolean mAudioModePopupVisible;
    private View mEndCallButton;
    private View mExtraRowButton;
    private View mManageConferenceButton;
    private View mGenericMergeButton;

    @Override
    public CallButtonPresenter createPresenter() {
        // TODO: find a cleaner way to include audio mode provider than
        // having a singleton instance.
        return new CallButtonPresenter();
    }

    @Override
    public CallButtonPresenter.CallButtonUi getUi() {
        return this;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        final View parent = inflater.inflate(R.layout.call_button_fragment, container, false);

        mExtraRowButton = parent.findViewById(R.id.extraButtonRow);

        mManageConferenceButton = parent.findViewById(R.id.manageConferenceButton);
        mManageConferenceButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                getPresenter().manageConferenceButtonClicked();
            }
        });
        mGenericMergeButton = parent.findViewById(R.id.cdmaMergeButton);
        mGenericMergeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                getPresenter().mergeClicked();
            }
        });

        mEndCallButton = parent.findViewById(R.id.endButton);
        mEndCallButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                getPresenter().endCallClicked();
            }
        });

        // make the hit target smaller for the end button so that is creates a deadzone
        // along the inside perimeter of the button.
        mEndCallButton.setOnTouchListener(new SmallerHitTargetTouchListener());

        mMuteButton = (ImageButton) parent.findViewById(R.id.muteButton);
        mMuteButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                final ImageButton button = (ImageButton) v;
                getPresenter().muteClicked(!button.isSelected());
            }
        });

        mAudioButton = (ImageButton) parent.findViewById(R.id.audioButton);
        mAudioButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                onAudioButtonClicked();
            }
        });

        mHoldButton = (ImageButton) parent.findViewById(R.id.holdButton);
        mHoldButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                final ImageButton button = (ImageButton) v;
                getPresenter().holdClicked(!button.isSelected());
            }
        });

        mShowDialpadButton = (ToggleButton) parent.findViewById(R.id.dialpadButton);
        mShowDialpadButton.setOnClickListener(this);
        /// M: unuse google code. @{
        /*
        mAddCallButton = (ImageButton) parent.findViewById(R.id.addButton);
        mAddCallButton.setOnClickListener(this);
        mMergeButton = (ImageButton) parent.findViewById(R.id.mergeButton);
        mMergeButton.setOnClickListener(this);
        */
        /// @}
        mSwapButton = (ImageButton) parent.findViewById(R.id.swapButton);
        mSwapButton.setOnClickListener(this);

        /// M: do inflate for MTK features. @{
        onCreateViewMtk(parent);
        /// @}

        /// M: For VT @{
        initVTCallButton(parent);
        /// @}

        return parent;
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        // set the buttons
        updateAudioButtons(getPresenter().getSupportedAudio());

        /// M: Add. @{
        mContext = getActivity();
        /// @}
    }

    @Override
    public void onResume() {
        /// M: design change for ALPS01262892 && ALPS01258249 && ALPS01236444 @{
        // the mute function when add call is not well designed on KK
        // the mute && unmute action should be done only in Telephony 
        // to prevent state confusion, the mute action will move to addCall()

        // MTK delete
        /*
        if (getPresenter() != null) {
            getPresenter().refreshMuteState();
        }
        */
        /// @}
        super.onResume();
    }

    @Override
    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
    }

    @Override
    public void onClick(View view) {
        int id = view.getId();
        Log.d(this, "onClick(View " + view + ", id " + id + ")...");

        /// M: For VT @{
        if (onClickMTK(view)) {
            return;
        }
        /// @}

        switch(id) {
            case R.id.addButton:
                getPresenter().addCallClicked();
                break;
            case R.id.mergeButton:
                getPresenter().mergeClicked();
                break;
            case R.id.swapButton:
                getPresenter().swapClicked();
                break;
            case R.id.dialpadButton:
                getPresenter().showDialpadClicked(mShowDialpadButton.isChecked());
                break;
            default:
                Log.wtf(this, "onClick: unexpected");
                break;
        }
    }

    @Override
    public void setEnabled(boolean isEnabled) {
        /// M: For VT @{
        if (!getPresenter().isNoCallExist()) {
            updateCallButton(getPresenter().isVTCall());
        }
        /// @}
        View view = getView();
        if (view.getVisibility() != View.VISIBLE) {
            view.setVisibility(View.VISIBLE);
        }

        // The main end-call button spanning across the screen.
        mEndCallButton.setEnabled(isEnabled);

        // The smaller buttons laid out horizontally just below the end-call button.
        mMuteButton.setEnabled(isEnabled);
        mAudioButton.setEnabled(isEnabled);
        mHoldButton.setEnabled(isEnabled);
        mShowDialpadButton.setEnabled(isEnabled);
        /// M: unuse google code. @{
        /*
        mMergeButton.setEnabled(isEnabled);
        mAddCallButton.setEnabled(isEnabled);
        */
        /// @}
        mSwapButton.setEnabled(isEnabled);
        /// M: For VT @{
        setEnableForVT(isEnabled);
        /// @}
    }

    @Override
    public void setMute(boolean value) {
        Log.d(this, "setMute:" + value);
        mMuteButton.setSelected(value);
        /// M: for VT call @{
        mVTMute.setChecked(value);
        /// @}
    }

    @Override
    public void enableMute(boolean enabled) {
        mMuteButton.setEnabled(enabled);
        mVTMute.setEnabled(enabled);
    }

    @Override
    public void setHold(boolean value) {
        mHoldButton.setSelected(value);
    }

    @Override
    public void showHold(boolean show) {
        mHoldButton.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    @Override
    public void enableHold(boolean enabled) {
        mHoldButton.setEnabled(enabled);
    }

    @Override
    public void showMerge(boolean show) {
        mMergeButton.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    @Override
    public void showSwap(boolean show) {
        mSwapButton.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    @Override
    public void showAddCall(boolean show) {
        mAddCallButton.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    @Override
    public void enableAddCall(boolean enabled) {
        mAddCallButton.setEnabled(enabled);
    }

    @Override
    public void setAudio(int mode) {
        updateAudioButtons(getPresenter().getSupportedAudio());
        refreshAudioModePopup();
    }

    @Override
    public void setSupportedAudio(int modeMask) {
        updateAudioButtons(modeMask);
        refreshAudioModePopup();
    }

    @Override
    public boolean onMenuItemClick(MenuItem item) {
        Log.d(this, "- onMenuItemClick: " + item);
        Log.d(this, "  id: " + item.getItemId());
        Log.d(this, "  title: '" + item.getTitle() + "'");

        /// M: For MTK feature. @{
        if (ExtensionManager.getInstance().getInCallUIExtension().handleMenuItemClick(item)) {
            return true;
        } else if (handleMenuItemClickMTK(item)) {
            return true;
        }
        /// @}

        int mode = AudioMode.WIRED_OR_EARPIECE;

        switch (item.getItemId()) {
            case R.id.audio_mode_speaker:
                mode = AudioMode.SPEAKER;
                break;
            case R.id.audio_mode_earpiece:
            case R.id.audio_mode_wired_headset:
                // InCallAudioMode.EARPIECE means either the handset earpiece,
                // or the wired headset (if connected.)
                mode = AudioMode.WIRED_OR_EARPIECE;
                break;
            case R.id.audio_mode_bluetooth:
                mode = AudioMode.BLUETOOTH;
                break;
            default:
                Log.e(this, "onMenuItemClick:  unexpected View ID " + item.getItemId()
                        + " (MenuItem = '" + item + "')");
                break;
        }

        getPresenter().setAudioMode(mode);

        return true;
    }

    // PopupMenu.OnDismissListener implementation; see showAudioModePopup().
    // This gets called when the PopupMenu gets dismissed for *any* reason, like
    // the user tapping outside its bounds, or pressing Back, or selecting one
    // of the menu items.
    @Override
    public void onDismiss(PopupMenu menu) {
        Log.d(this, "- onDismiss: " + menu);
        mAudioModePopupVisible = false;
    }

    /**
     * Checks for supporting modes.  If bluetooth is supported, it uses the audio
     * pop up menu.  Otherwise, it toggles the speakerphone.
     */
    private void onAudioButtonClicked() {
        Log.d(this, "onAudioButtonClicked: " +
                AudioMode.toString(getPresenter().getSupportedAudio()));

        if (isSupported(AudioMode.BLUETOOTH)) {
            showAudioModePopup();
        } else {
            getPresenter().toggleSpeakerphone();
        }
    }

    /**
     * Refreshes the "Audio mode" popup if it's visible.  This is useful
     * (for example) when a wired headset is plugged or unplugged,
     * since we need to switch back and forth between the "earpiece"
     * and "wired headset" items.
     *
     * This is safe to call even if the popup is already dismissed, or even if
     * you never called showAudioModePopup() in the first place.
     */
    public void refreshAudioModePopup() {
        if (mAudioModePopup != null && mAudioModePopupVisible) {
            // Dismiss the previous one
            mAudioModePopup.dismiss();  // safe even if already dismissed
            // And bring up a fresh PopupMenu
            /// M: For ALPS01274235. @{
            // Only show audio PopupMenu when support Bluetooth audio mode.
            if (isSupported(AudioMode.BLUETOOTH)) {
                showAudioModePopup();
            }
            /// @}
        }
    }

    /**
     * Updates the audio button so that the appriopriate visual layers
     * are visible based on the supported audio formats.
     */
    private void updateAudioButtons(int supportedModes) {
        final boolean bluetoothSupported = isSupported(AudioMode.BLUETOOTH);
        final boolean speakerSupported = isSupported(AudioMode.SPEAKER);

        boolean audioButtonEnabled = false;
        boolean audioButtonChecked = false;
        boolean showMoreIndicator = false;

        boolean showBluetoothIcon = false;
        boolean showSpeakerphoneOnIcon = false;
        boolean showSpeakerphoneOffIcon = false;
        boolean showHandsetIcon = false;

        boolean showToggleIndicator = false;

        if (bluetoothSupported) {
            Log.d(this, "updateAudioButtons - popup menu mode");

            audioButtonEnabled = true;
            showMoreIndicator = true;
            // The audio button is NOT a toggle in this state.  (And its
            // setChecked() state is irrelevant since we completely hide the
            // btn_compound_background layer anyway.)

            // Update desired layers:
            if (isAudio(AudioMode.BLUETOOTH)) {
                showBluetoothIcon = true;
            } else if (isAudio(AudioMode.SPEAKER)) {
                showSpeakerphoneOnIcon = true;
            } else {
                showHandsetIcon = true;
                // TODO: if a wired headset is plugged in, that takes precedence
                // over the handset earpiece.  If so, maybe we should show some
                // sort of "wired headset" icon here instead of the "handset
                // earpiece" icon.  (Still need an asset for that, though.)
            }
        } else if (speakerSupported) {
            Log.d(this, "updateAudioButtons - speaker toggle mode");

            audioButtonEnabled = true;

            // The audio button *is* a toggle in this state, and indicated the
            // current state of the speakerphone.
            audioButtonChecked = isAudio(AudioMode.SPEAKER);

            // update desired layers:
            showToggleIndicator = true;

            showSpeakerphoneOnIcon = isAudio(AudioMode.SPEAKER);
            showSpeakerphoneOffIcon = !showSpeakerphoneOnIcon;
        } else {
            Log.d(this, "updateAudioButtons - disabled...");

            // The audio button is a toggle in this state, but that's mostly
            // irrelevant since it's always disabled and unchecked.
            audioButtonEnabled = false;
            audioButtonChecked = false;

            // update desired layers:
            showToggleIndicator = true;
            showSpeakerphoneOffIcon = true;
        }

        // Finally, update it all!

        Log.v(this, "audioButtonEnabled: " + audioButtonEnabled);
        Log.v(this, "audioButtonChecked: " + audioButtonChecked);
        Log.v(this, "showMoreIndicator: " + showMoreIndicator);
        Log.v(this, "showBluetoothIcon: " + showBluetoothIcon);
        Log.v(this, "showSpeakerphoneOnIcon: " + showSpeakerphoneOnIcon);
        Log.v(this, "showSpeakerphoneOffIcon: " + showSpeakerphoneOffIcon);
        Log.v(this, "showHandsetIcon: " + showHandsetIcon);

        // Constants for Drawable.setAlpha()
        final int HIDDEN = 0;
        final int VISIBLE = 255;

        mAudioButton.setEnabled(audioButtonEnabled);
        mAudioButton.setSelected(audioButtonChecked);

        final LayerDrawable layers = (LayerDrawable) mAudioButton.getBackground();
        Log.d(this, "'layers' drawable: " + layers);

        layers.findDrawableByLayerId(R.id.compoundBackgroundItem)
                .setAlpha(showToggleIndicator ? VISIBLE : HIDDEN);

        layers.findDrawableByLayerId(R.id.moreIndicatorItem)
                .setAlpha(showMoreIndicator ? VISIBLE : HIDDEN);

        layers.findDrawableByLayerId(R.id.bluetoothItem)
                .setAlpha(showBluetoothIcon ? VISIBLE : HIDDEN);

        layers.findDrawableByLayerId(R.id.handsetItem)
                .setAlpha(showHandsetIcon ? VISIBLE : HIDDEN);

        layers.findDrawableByLayerId(R.id.speakerphoneOnItem)
                .setAlpha(showSpeakerphoneOnIcon ? VISIBLE : HIDDEN);

        layers.findDrawableByLayerId(R.id.speakerphoneOffItem)
                .setAlpha(showSpeakerphoneOffIcon ? VISIBLE : HIDDEN);
    }

    private void showAudioModePopup() {
        Log.d(this, "showAudioPopup()...");

        mAudioModePopup = new PopupMenu(getView().getContext(), mAudioButton /* anchorView */);
        mAudioModePopup.getMenuInflater().inflate(R.menu.incall_audio_mode_menu,
                mAudioModePopup.getMenu());
        mAudioModePopup.setOnMenuItemClickListener(this);
        mAudioModePopup.setOnDismissListener(this);

        final Menu menu = mAudioModePopup.getMenu();

        // TODO: Still need to have the "currently active" audio mode come
        // up pre-selected (or focused?) with a blue highlight.  Still
        // need exact visual design, and possibly framework support for this.
        // See comments below for the exact logic.

        final MenuItem speakerItem = menu.findItem(R.id.audio_mode_speaker);
        speakerItem.setEnabled(isSupported(AudioMode.SPEAKER));
        // TODO: Show speakerItem as initially "selected" if
        // speaker is on.

        // We display *either* "earpiece" or "wired headset", never both,
        // depending on whether a wired headset is physically plugged in.
        final MenuItem earpieceItem = menu.findItem(R.id.audio_mode_earpiece);
        final MenuItem wiredHeadsetItem = menu.findItem(R.id.audio_mode_wired_headset);

        final boolean usingHeadset = isSupported(AudioMode.WIRED_HEADSET);
        earpieceItem.setVisible(!usingHeadset);
        earpieceItem.setEnabled(!usingHeadset);
        wiredHeadsetItem.setVisible(usingHeadset);
        wiredHeadsetItem.setEnabled(usingHeadset);
        // TODO: Show the above item (either earpieceItem or wiredHeadsetItem)
        // as initially "selected" if speakerOn and
        // bluetoothIndicatorOn are both false.

        final MenuItem bluetoothItem = menu.findItem(R.id.audio_mode_bluetooth);
        bluetoothItem.setEnabled(isSupported(AudioMode.BLUETOOTH));
        // TODO: Show bluetoothItem as initially "selected" if
        // bluetoothIndicatorOn is true.

        mAudioModePopup.show();

        // Unfortunately we need to manually keep track of the popup menu's
        // visiblity, since PopupMenu doesn't have an isShowing() method like
        // Dialogs do.
        mAudioModePopupVisible = true;
    }

    private boolean isSupported(int mode) {
        return (mode == (getPresenter().getSupportedAudio() & mode));
    }

    private boolean isAudio(int mode) {
        return (mode == getPresenter().getAudioMode());
    }

    @Override
    public void displayDialpad(boolean value) {
        mShowDialpadButton.setChecked(value);
        mVTDialpad.setChecked(value);
        if (getActivity() != null && getActivity() instanceof InCallActivity) {
            ((InCallActivity) getActivity()).displayDialpad(value);
        }
    }

    @Override
    public boolean isDialpadVisible() {
        if (getActivity() != null && getActivity() instanceof InCallActivity) {
            return ((InCallActivity) getActivity()).isDialpadVisible();
        }
        return false;
    }

    /**
     * M: add for get for showing dialpad or not.
     *
     * @return
     */
    public ToggleButton getShowDialpadButton() {
        return mShowDialpadButton;
    }

    @Override
    public void displayManageConferencePanel(boolean value) {
        if (getActivity() != null && getActivity() instanceof InCallActivity) {
            ((InCallActivity) getActivity()).displayManageConferencePanel(value);
        }
    }


    @Override
    public void showManageConferenceCallButton() {
        mExtraRowButton.setVisibility(View.VISIBLE);
        mManageConferenceButton.setVisibility(View.VISIBLE);
        mGenericMergeButton.setVisibility(View.GONE);
    }

    @Override
    public void showGenericMergeButton() {
        mExtraRowButton.setVisibility(View.VISIBLE);
        mManageConferenceButton.setVisibility(View.GONE);
        mGenericMergeButton.setVisibility(View.VISIBLE);
    }

    @Override
    public void hideExtraRow() {
       mExtraRowButton.setVisibility(View.GONE);
    }

    //------------------------------Mediatek--------------------------------

    private ImageButton mMenuButton;
    private PopupMenu mPopupMenu;
    private Context mContext;

    /**
     * description :
     * (1) the 'add' and 'merge' button are moved
     * (2) add menu button
     * to the option menus when there is no permanent menu key.
     */
    private void onCreateViewMtk(View parent) {
        mMenuButton = (ImageButton) parent.findViewById(R.id.overflowMenu);
        if (InCallUtils.hasPermanentMenuKey(getActivity())) {
            mAddCallButton = (ImageButton) parent.findViewById(R.id.addButton);
            if (mAddCallButton != null) {
                mAddCallButton.setOnClickListener(this);
            }
            mMergeButton = (ImageButton) parent.findViewById(R.id.mergeButton);
            if (mMergeButton != null) {
                mMergeButton.setOnClickListener(this);
            }
            if (mMenuButton != null) {
                mMenuButton.setVisibility(View.GONE);
            }
        } else {
            String productCharacteristic = SystemProperties.get("ro.build.characteristics");
            // Add onClickListener for addButton.
            // Since even on a device without permanent menu key
            // the addButton will be shown if the screen is wide enough
            // If the addButton is shown, it should react
            mAddCallButton = (ImageButton) parent.findViewById(R.id.addButton);
            if (mAddCallButton != null) {
                if ((productCharacteristic != null) && (productCharacteristic.equals("tablet"))) {
                    mAddCallButton.setOnClickListener(this);
                } else {
                    mAddCallButton.setVisibility(View.GONE);
                }
            }
            // Add onClickListener for mergeButton.
            // Since even on a device without permanent menu key
            // the mergeButton will be shown if the screen is wide enough
            // If the mergeButton is shown, it should react
            mMergeButton = (ImageButton) parent.findViewById(R.id.mergeButton);
            if (mMergeButton != null) {
                if ((productCharacteristic != null) && (productCharacteristic.equals("tablet"))) {
                  mMergeButton.setOnClickListener(this);
                } else {
                  mMergeButton.setVisibility(View.GONE);
                }
            }

            if (mMenuButton != null) {
                mMenuButton.setOnClickListener(this);
                mMenuButton.setVisibility(View.VISIBLE);
            }
        }
    }

    private void initializeOverflowMenu(int id){
        if (mPopupMenu != null) {
            mPopupMenu.dismiss();
        }
        mPopupMenu = constructPopupMenu(getActivity().findViewById(id));
        if (mPopupMenu != null && mPopupMenu.getMenu().hasVisibleItems()) {
            mPopupMenu.show();
        }
    }

    private PopupMenu constructPopupMenu(View anchorView) {
        final PopupMenu popupMenu = new PopupMenu(mContext, anchorView);
        final Menu menu = popupMenu.getMenu();
        popupMenu.inflate(R.menu.mtk_voice_incall_menu);
        popupMenu.setOnMenuItemClickListener(this);
        InCallUtils.setAllMenuVisible(menu, false);
        setupVoiceMenuItems(menu);
        return popupMenu;
    }

    private void setupVoiceMenuItems(Menu menu) {
        Log.d(this, "setupVoiceMenuItems()...");
        final MenuItem addMenu = menu.findItem(R.id.menu_add_call);
        final MenuItem mergeMenu = menu.findItem(R.id.menu_merge_call);
        final MenuItem recordMenu = menu.findItem(R.id.menu_voice_record);
        final MenuItem voiceAnswerMenu = menu.findItem(R.id.menu_vt_voice_answer);

        final MenuItem hangupAllMenu = menu.findItem(R.id.menu_hangup_all);
        final MenuItem hangupHoldingMenu = menu.findItem(R.id.menu_hangup_holding);
        final MenuItem hangupActiveAndAnswerWaitingMenu = menu.findItem(R.id.menu_hangup_active_and_answer_waiting);
        final MenuItem ectMenu = menu.findItem(R.id.menu_ect);
        
        final MenuItem holdMenu = menu.findItem(R.id.menu_hold_voice);
        //TODO: need add logic for Dualtalk.
        holdMenu.setVisible(false);

        recordMenu.setVisible(InCallMenuState.sCanRecording);
        if (InCallMenuState.sCanRecording) {
            if (PhoneRecorderUtils.getRecorderState() == RecorderState.IDLE_STATE) {
                recordMenu.setTitle(R.string.start_record);
            } else {
                recordMenu.setTitle(R.string.stop_record);
            }
        }
        addMenu.setVisible(InCallMenuState.sCanAddCall);
        mergeMenu.setVisible(InCallMenuState.sCanMerge);
        voiceAnswerMenu.setVisible(InCallMenuState.sCanVTVoiceAnswer);
        hangupAllMenu.setVisible(InCallMenuState.sCanHangupAll);
        hangupHoldingMenu.setVisible(InCallMenuState.sCanHangupHolding);
        hangupActiveAndAnswerWaitingMenu.setVisible(InCallMenuState.sCanHangupActiveAndAnswerWaiting);
        ectMenu.setVisible(InCallMenuState.sCanECT);
        /// M: DM lock Feature @{
        if (InCallUtils.isDMLocked()) {
            return;
        }
        /// @}
        recordMenu.setEnabled(InCallMenuState.sCanRecording);
        addMenu.setEnabled(true);
        if (InCallMenuState.sDisableAdd) {
            addMenu.setEnabled(false);
        }
    }

    private void onVoiceRecordClick(MenuItem menuItem) {
        Log.d(this, "onVoiceRecordClick");
        String title = menuItem.getTitle().toString();
        if (title == null) {
            return;
        }
        if (!PhoneRecorderUtils.isExternalStorageMounted(mContext)) {
            Toast.makeText(mContext,
                    mContext.getResources().getString(R.string.error_sdcard_access),
                    Toast.LENGTH_LONG).show();
            return;
        }
        if (!PhoneRecorderUtils
                .diskSpaceAvailable(PhoneRecorderUtils.PHONE_RECORD_LOW_STORAGE_THRESHOLD)) {
            InCallPresenter.getInstance().handleStorageFull(true); // true for checking case
            return;
        }

        if (title.equals(getString(R.string.start_record))) {
            Log.d(this, "want to startRecord");
            getPresenter().voiceRecordClicked();
        } else if (title.equals(getString(R.string.stop_record))) {
            getPresenter().stopRecordClicked();
        }
    }

    public static class InCallMenuState {
        public static boolean sCanMerge;
        public static boolean sCanAddCall;
        public static boolean sCanHangupAll;
        public static boolean sCanHangupHolding;
        public static boolean sCanHangupActiveAndAnswerWaiting;
        public static boolean sCanECT;
        public static boolean sCanVTVoiceAnswer;
        public static boolean sCanRecording;
        public static boolean sDisableAdd;

        public static void reset(){
            sCanMerge = false;
            sCanAddCall = false;
            sCanHangupAll = false;
            sCanHangupHolding = false;
            sCanHangupActiveAndAnswerWaiting = false;
            sCanECT = false;
            sCanVTVoiceAnswer = false;
            sCanRecording = false;
            sDisableAdd = false;
        }
    }


    // --------------------------------VT-------------------------------------------
    public boolean onOptionsItemSelected(MenuItem item) {
        return handleMenuItemClickMTK(item);
    }

    private View mParentView;
    private View mVoiceButtonContainer;
    private View mVTButtonContainer;

    // mVTDialpad / mVTAudio / mVTMute => Reuse voice call's logic, will be updated by voice call's logic.
    // mVTHangUp / mVTOverflowMenu / mVTSwapVideo  => will be updated in setEnableForVT().
    private ToggleButton mVTDialpad;
    private ImageButton mVTAudio;
    private ImageButton mVTHangUp;
    private CompoundButton mVTMute;
    private CompoundButton mVTSwapVideo;
    private ImageButton mVTOverflowMenu;
    private PopupMenu mVTPopupMenu;

    private void initVTCallButton(View parent) {
        mVoiceButtonContainer = parent.findViewById(R.id.callButtonContainer);
        mVTButtonContainer = parent.findViewById(R.id.vtCallButtonContainer);

        mVTDialpad = (ToggleButton) parent.findViewById(R.id.VTDialpad);
        mVTAudio = (ImageButton) parent.findViewById(R.id.VTSpeaker);
        mVTHangUp = (ImageButton) parent.findViewById(R.id.VTHangUp);
        mVTMute = (CompoundButton) parent.findViewById(R.id.VTMute);
        mVTSwapVideo = (CompoundButton) parent.findViewById(R.id.VTSwapVideo);
        mVTOverflowMenu = (ImageButton) parent.findViewById(R.id.VTOverflowMenu);

        mVTDialpad.setOnClickListener(this);
        mVTAudio.setOnClickListener(this);
        mVTHangUp.setOnClickListener(this);
        mVTMute.setOnClickListener(this);
        mVTSwapVideo.setOnClickListener(this);
        mVTOverflowMenu.setOnClickListener(this);

        if (ViewConfiguration.get(getActivity()).hasPermanentMenuKey()) {
            mVTSwapVideo.setVisibility(View.VISIBLE);
            mVTOverflowMenu.setVisibility(View.GONE);
        } else {
            mVTSwapVideo.setVisibility(View.GONE);
            mVTOverflowMenu.setVisibility(View.VISIBLE);
        }
        mParentView = parent;

        /// M: for ALPS01271268 @{
        // if button size already initialized just use it.
        if (sVtButtonHeight != 0 && sVtButtonMarginLeft != 0 && sVtButtonMarginBottom != 0) {
            setVtButtonSize(sVtButtonMarginLeft, sVtButtonMarginBottom, sVtButtonHeight);
        }
        /// @}
    }

    private void setEnableForVT(boolean isEnabled) {
        mVTDialpad.setEnabled(isEnabled);
        mVTAudio.setEnabled(isEnabled);
        mVTMute.setEnabled(isEnabled);
        mVTOverflowMenu.setEnabled(isEnabled);
        mVTSwapVideo.setEnabled(isEnabled);
        if (isEnabled) {
            mVTSwapVideo.setEnabled(VTInCallScreenFlags.getInstance().mVTHasReceiveFirstFrame);
        }
        mVTSwapVideo.setChecked(!VTInCallScreenFlags.getInstance().mVTPeerBigger);
    }

    public boolean mIsVTButtonVisible = false;

    public void updateCallButton(boolean isVT) {
        Log.d(this, "setVTButtonVisible()... isVT: " + isVT);
        if (isVT) {
            mEndCallButton.setVisibility(View.GONE);
            mVoiceButtonContainer.setVisibility(View.GONE);
            mVTButtonContainer.setVisibility(View.VISIBLE);
            // use google default audio mAudioButton to do audio related operation.
            if (mParentView != null) {
                mAudioButton = (ImageButton) mParentView.findViewById(R.id.VTSpeaker);
            }
            updateAudioButtons(0);
            mIsVTButtonVisible = true;
        } else {
            mEndCallButton.setVisibility(View.VISIBLE);
            mVoiceButtonContainer.setVisibility(View.VISIBLE);
            mVTButtonContainer.setVisibility(View.GONE);
            if (mParentView != null) {
                mAudioButton = (ImageButton) mParentView.findViewById(R.id.audioButton);
            }
            /// M: for ALPS01272972 @{
            /// In this case, after video call drop back, during the voice call setup,
            /// the force speaker on will turn on the speaker, but then the mAudioButton
            /// changed...
            updateAudioButtons(getPresenter().getAudioMode());
            /// @}
            mIsVTButtonVisible = false;
        }
    }
    
    public boolean isVTButtonVisible() {
        return mIsVTButtonVisible;
    }

    private boolean onClickMTK(View view) {
        int id = view.getId();
        Log.d(this, "onClickMTK(View " + view + ", id " + id + ")...");

        switch(id) {
            /// M: Change feature. @{
            case R.id.overflowMenu:
                initializeOverflowMenu(id);
                break;
                /// @}
            case R.id.VTDialpad:
                getPresenter().showDialpadClicked(mVTDialpad.isChecked());
                break;
            case R.id.VTSpeaker:
                onAudioButtonClicked();
                break;
            case R.id.VTHangUp:
                getPresenter().onVTEndCallClick();
                break;
            case R.id.VTMute:
//                final ToggleButton button = (ToggleButton) view;
                getPresenter().muteClicked(!AudioModeProvider.getInstance().getMute());
                break;
            case R.id.VTSwapVideo:
                onVTSwapVideoClick();
                break;
            case R.id.VTOverflowMenu:
                if (null != mVTPopupMenu) {
                    mVTPopupMenu.dismiss();
                }
                PopupMenu popup = constructVTPopupMenu(mVTOverflowMenu);
                if (popup != null) {
                    popup.show();
                }
                break;
            default:
                return false;
        }
        return true;
    }

    private PopupMenu constructVTPopupMenu(View anchorView) {
        if (null == mVTPopupMenu) {
            mVTPopupMenu = new PopupMenu(getActivity(), anchorView);
            mVTPopupMenu.inflate(R.menu.mtk_vt_incall_menu);
            mVTPopupMenu.setOnMenuItemClickListener(this);
        }
        InCallUtils.setAllMenuVisible(mVTPopupMenu.getMenu(), false);
        setupVTMenuItems(mVTPopupMenu.getMenu());
        return mVTPopupMenu;
    }

    public void setupVTMenuItems(Menu menu) {
//        /// DM Lock feature @{
//        if (InCallUtils.isDMLocked()) {
//            Log.d(this, "setupVTMenuItems()... DM lock, just return");
//            return;
//        }
//        ///@}
        Log.d(this, "setupVTMenuItems()...");
        final MenuItem switchCameraMenu = menu.findItem(R.id.menu_switch_camera);
        final MenuItem takePeerPhotoMenu = menu.findItem(R.id.menu_take_peer_photo);
        final MenuItem hideLocalVideoMenu = menu.findItem(R.id.menu_hide_local_video);
        final MenuItem swapVideosMenu = menu.findItem(R.id.menu_swap_videos);
        final MenuItem videoRecordMenu = menu.findItem(R.id.menu_vt_record);
        final MenuItem videoSettingMenu = menu.findItem(R.id.menu_video_setting);

        swapVideosMenu.setVisible(!ViewConfiguration.get(getActivity()).hasPermanentMenuKey());
        takePeerPhotoMenu.setVisible(true);
        hideLocalVideoMenu.setVisible(true);
        if (VTInCallScreenFlags.getInstance().mVTHideMeNow) {
            hideLocalVideoMenu.setTitle(getResources().getString(R.string.vt_menu_show_me));
        } else {
            hideLocalVideoMenu.setTitle(getResources().getString(R.string.vt_menu_hide_me));
        }
        switchCameraMenu.setVisible(getPresenter().shouldSwitchCameraVisible());
        videoRecordMenu.setVisible(true);
        if (PhoneRecorderUtils.getRecorderState() == RecorderState.IDLE_STATE) {
            videoRecordMenu.setTitle(R.string.start_record_vt);
        } else {
            videoRecordMenu.setTitle(R.string.stop_record_vt);
        }
        /// DM Lock feature @{
        if (InCallUtils.isDMLocked()) {
            Log.d(this, "setupVTMenuItems()... DM lock, just return");
            return;
        }
        ///@}
        videoSettingMenu.setVisible(true);
        swapVideosMenu.setEnabled(VTInCallScreenFlags.getInstance().mVTHasReceiveFirstFrame);

        takePeerPhotoMenu.setEnabled(VTInCallScreenFlags.getInstance().mVTHasReceiveFirstFrame
                && !getUi().isDialpadVisible());

        hideLocalVideoMenu.setEnabled(true);

        switchCameraMenu.setEnabled(getPresenter().shouldSwitchCameraEnable());

        videoRecordMenu.setEnabled(getPresenter().shouldVTRecordEnable());
        videoSettingMenu.setEnabled(getPresenter().shouldVideoSettingEnable());

        ExtensionManager.getInstance().getVTCallExtension().onPrepareOptionMenu(menu);
    }

    public boolean handleMenuItemClickMTK(MenuItem menuItem) {
        Log.d(this, "- onMenuItemClick: " + menuItem);
        Log.d(this, "  id: " + menuItem.getItemId());
        Log.d(this, "  title: '" + menuItem.getTitle() + "'");
        switch (menuItem.getItemId()) {
            case R.id.menu_hold_voice:
                getPresenter().holdClicked(true);
                break;
            case R.id.menu_add_call:
                getPresenter().addCallClicked();
                break;
            case R.id.menu_merge_call:
                getPresenter().mergeClicked();
                break;
            case R.id.menu_voice_record:
                onVoiceRecordClick(menuItem);
                break;
            case R.id.menu_vt_voice_answer:
                // onVTVoiceAnswer();
                break;
            case R.id.menu_hangup_all:
                getPresenter().hangupAllCalls();
                break;
            case R.id.menu_hangup_holding:
                getPresenter().hangupHoldingCall();
                break;
            case R.id.menu_hangup_active_and_answer_waiting:
                getPresenter().hangupActiveAndAnswerWaiting();
                break;
            case R.id.menu_ect:
                getPresenter().explicitCallTransfer();
                break;
            case R.id.menu_swap_videos:
            case R.id.menu_switch_camera:
            case R.id.menu_take_peer_photo:
            case R.id.menu_hide_local_video:
            case R.id.menu_vt_record:
            case R.id.menu_video_setting:
                handleVTMenuClick(menuItem);
                break;
            default:
                Log.d(this, "This is not MTK menu item.");
                return false;
        }
        return true;
    }

    private void onVTSwapVideoClick() {
        if (getActivity() != null && getActivity() instanceof InCallActivity) {
            ((InCallActivity) getActivity()).onVTSwapVideoClick();
        }
    }
    private void handleVTMenuClick(MenuItem menuItem) {
        if (getActivity() != null && getActivity() instanceof InCallActivity) {
            ((InCallActivity) getActivity()).handleVTMenuClick(menuItem);
        }
    }

    public void setupMenuItems(Menu menu) {
        Log.d(this, "setupMenuItems()...");
        if (getPresenter().isNoCallExist()) {
            Log.d(this, "There is no call exist any more, just return");
            return;
        }
        if (getPresenter().isVTCall()) {
            setupVTMenuItems(menu);
        } else {
            setupVoiceMenuItems(menu);
            ExtensionManager.getInstance().getInCallUIExtension().setupMenuItems(menu, getPresenter().getCall());
        }
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        // Add for plugin.
        ExtensionManager.getInstance().getCallButtonExtension().onViewCreated(getActivity(), view);
    }

    @Override
    public Context getContext(){
        return mContext;
    }

    /*
     * dismiss all pop up menu
     */
    @Override
    public void dismissPopupMenu() {
        Log.d(this, "dismissPopUpMenu~~");

        if(mPopupMenu != null) {
            mPopupMenu.dismiss();
        }

        if(mVTPopupMenu != null) {
            mVTPopupMenu.dismiss();
        }

        if(mAudioModePopup != null) {
            mAudioModePopup.dismiss();
        }
    }

    private static int sVtButtonMarginLeft;
    private static int sVtButtonMarginBottom;
    private static int sVtButtonHeight;

    /// M: amend call button when vt screen ready.
    public void amendCallButtonLayout(int marginLeft, int marginBottom, int height) {
        if (mVTButtonContainer != null
                && (sVtButtonHeight != height || sVtButtonMarginLeft != marginLeft || sVtButtonMarginBottom != marginBottom)) {
            setVtButtonSize(marginLeft, marginBottom, height);
            sVtButtonHeight = height;
            sVtButtonMarginLeft = marginLeft;
            sVtButtonMarginBottom = marginBottom;
        }
    }

    private void setVtButtonSize(int marginLeft, int marginBottom, int height) {
        MarginLayoutParams params = (MarginLayoutParams) mVTButtonContainer.getLayoutParams();
        params.leftMargin = marginLeft;
        params.bottomMargin = marginBottom;
        params.height = height;
    }

    // for VTSwapVideo button, we only enable it after receiving VTManager.VT_MSG_RECEIVE_FIRSTFRAME,
    // so we disable it in setEnableForVT() if has not receive this msg, and update it when receive this msg.
    public void updateVTCallButton() {
        // for other buttons are sync with voice call, so no need update here specially.
        Log.d(this, "updateVTCallButton()...");
        if (mVTSwapVideo.getVisibility() == View.VISIBLE && !VTUtils.isVTIdle()) {
            mVTSwapVideo.setEnabled(VTInCallScreenFlags.getInstance().mVTHasReceiveFirstFrame);
        }
        mVTSwapVideo.setChecked(!VTInCallScreenFlags.getInstance().mVTPeerBigger);
    }

    public void enableEnd(boolean enabled) {
        mEndCallButton.setEnabled(enabled);
    }
    public void enableSwap(boolean enabled) {
        mSwapButton.setEnabled(enabled);
    }
}
