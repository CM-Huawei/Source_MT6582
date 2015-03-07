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
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.DialogInterface.OnDismissListener;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputFilter;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.PopupMenu;
import android.widget.TextView;

import com.android.incallui.CallButtonFragment.InCallMenuState;
import com.android.services.telephony.common.Call;
import com.google.common.base.Preconditions;
import com.mediatek.incallui.InCallUtils;
import com.mediatek.incallui.VoiceCommandUIUtils;
import com.mediatek.incallui.wrapper.FeatureOptionWrapper;

import java.util.ArrayList;

/**
 *
 */
public class AnswerFragment extends BaseFragment<AnswerPresenter, AnswerPresenter.AnswerUi>
        implements GlowPadWrapper.AnswerListener, AnswerPresenter.AnswerUi {

    /**
     * The popup showing the list of canned responses.
     *
     * This is an AlertDialog containing a ListView showing the possible choices.  This may be null
     * if the InCallScreen hasn't ever called showRespondViaSmsPopup() yet, or if the popup was
     * visible once but then got dismissed.
     */
    private Dialog mCannedResponsePopup = null;

    /**
     * The popup showing a text field for users to type in their custom message.
     */
    private AlertDialog mCustomMessagePopup = null;

    private ArrayAdapter<String> mTextResponsesAdapter = null;

    private GlowPadWrapper mGlowpad;

    public AnswerFragment() {
    }

    @Override
    public
    AnswerPresenter createPresenter() {
        return new AnswerPresenter();
    }

    @Override
    public
    AnswerPresenter.AnswerUi getUi() {
        return this;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        final View parent = inflater.inflate(R.layout.answer_fragment,
                container, false);
        mGlowpad = (GlowPadWrapper) parent.findViewById(R.id.glow_pad_view);
        mTextView = (TextView)parent.findViewById(R.id.rejectIncomingCallNoti);

        /// M: For incoming menu @{
        mIncomingMenuButton = (ImageButton)parent.findViewById(R.id.incomingOverflowMenu);
        mIncomingMenuButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onIncomingMenuButtonClick();
            }
        });
        /// @}

        Log.d(this, "Creating view for answer fragment ", this);
        Log.d(this, "Created from activity", getActivity());
        mGlowpad.setAnswerListener(this);
        return parent;
    }

    @Override
    public void onDestroyView() {
        Log.d(this, "onDestroyView");
        if (mGlowpad != null) {
            mGlowpad.stopPing();
            mGlowpad = null;
        }
        super.onDestroyView();
    }

    @Override
    public void showAnswerUi(boolean show) {
        //getView().setVisibility(show ? View.VISIBLE : View.GONE);
        mGlowpad.setVisibility(show ? View.VISIBLE : View.GONE);
        /// M: For VT @{
        if (getPresenter().isVTCall()) {
            mGlowpad.setHandleDrawableImage(R.drawable.mtk_ic_in_call_touch_handle_vt);
        } else {
            mGlowpad.setHandleDrawableImage(R.drawable.ic_in_call_touch_handle);
        }
        /// @}

        /// M: For incoming menu @{
        updateIncomingCallMenuButton();
        /// @}

        Log.d(this, "Show answer UI: " + show);
        if (show) {
            mGlowpad.startPing();
        } else {
            mGlowpad.stopPing();
        }

        /// M: Add to update reject call message. @{
        updatePromptsMessage(show);
        /// @}
    }

    @Override
    public void showTextButton(boolean show) {
        final int targetResourceId = show
                ? R.array.incoming_call_widget_3way_targets
                : R.array.incoming_call_widget_2way_targets;

        if (targetResourceId != mGlowpad.getTargetResourceId()) {
            if (show) {
                // Answer, Decline, and Respond via SMS.
                mGlowpad.setTargetResources(targetResourceId);
                mGlowpad.setTargetDescriptionsResourceId(
                        R.array.incoming_call_widget_3way_target_descriptions);
                mGlowpad.setDirectionDescriptionsResourceId(
                        R.array.incoming_call_widget_3way_direction_descriptions);
            } else {
                // Answer or Decline.
                mGlowpad.setTargetResources(targetResourceId);
                mGlowpad.setTargetDescriptionsResourceId(
                        R.array.incoming_call_widget_2way_target_descriptions);
                mGlowpad.setDirectionDescriptionsResourceId(
                        R.array.incoming_call_widget_2way_direction_descriptions);
            }

            mGlowpad.reset(false);
        }
    }

    @Override
    public void showMessageDialog() {
        Log.d(this, "showMessageDialog~~");
        final ListView lv = new ListView(getActivity());

        Preconditions.checkNotNull(mTextResponsesAdapter);
        lv.setAdapter(mTextResponsesAdapter);
        lv.setOnItemClickListener(new RespondViaSmsItemClickListener());

        final AlertDialog.Builder builder = new AlertDialog.Builder(getActivity()).setCancelable(
                true).setView(lv);
        builder.setOnCancelListener(new DialogInterface.OnCancelListener() {
            @Override
            public void onCancel(DialogInterface dialogInterface) {
                if (mGlowpad != null) {
                    mGlowpad.startPing();
                }
                dismissCannedResponsePopup();
                getPresenter().onDismissDialog();
            }
        });
        mCannedResponsePopup = builder.create();

        /// M: for ALPS01234374 @{
        // ensure set null when dismissed
        mCannedResponsePopup.setOnDismissListener(new OnDismissListener() {

            @Override
            public void onDismiss(DialogInterface dialog) {
                Log.d(this, "mCannedResponsePopup on dismiss");
                mCannedResponsePopup = null;
                getPresenter().onDismissDialog();
            }
        });
        /// @}

        mCannedResponsePopup.show();
    }

    private boolean isCannedResponsePopupShowing() {
        if (mCannedResponsePopup != null) {
            return mCannedResponsePopup.isShowing();
        }
        return false;
    }

    private boolean isCustomMessagePopupShowing() {
        if (mCustomMessagePopup != null) {
            return mCustomMessagePopup.isShowing();
        }
        return false;
    }

    /**
     * Dismiss the canned response list popup.
     *
     * This is safe to call even if the popup is already dismissed, and even if you never called
     * showRespondViaSmsPopup() in the first place.
     */
    private void dismissCannedResponsePopup() {
        if (mCannedResponsePopup != null) {
            mCannedResponsePopup.dismiss();  // safe even if already dismissed
            mCannedResponsePopup = null;
        }
    }

    /**
     * Dismiss the custom compose message popup.
     */
    private void dismissCustomMessagePopup() {
       if (mCustomMessagePopup != null) {
           mCustomMessagePopup.dismiss();
           mCustomMessagePopup = null;
       }
    }

    public void dismissPendingDialogues() {
        if (isCannedResponsePopupShowing()) {
            dismissCannedResponsePopup();
        }

        if (isCustomMessagePopupShowing()) {
            dismissCustomMessagePopup();
        }
    }

    public boolean hasPendingDialogs() {
        return !(mCannedResponsePopup == null && mCustomMessagePopup == null);
    }

    /**
     * Shows the custom message entry dialog.
     */
    public void showCustomMessageDialog() {
        // Create an alert dialog containing an EditText
        Log.d(this, "showCustomMessageDialog~~");
        final EditText et = new EditText(getActivity());

        /// M: Add for ALPS01260356
        //  Make sure we only allow input max (with blank) MAX_MESSAGE_LEN(140)
        //  characters, but we only send the characters without blank @{
        InputFilter[] smsLengthFilter = new InputFilter[1];
        smsLengthFilter[0] = new InputFilter.LengthFilter(MAX_MESSAGE_LEN);
        et.setFilters(smsLengthFilter);
        /// @}

        final AlertDialog.Builder builder = new AlertDialog.Builder(getActivity()).setCancelable(
                true).setView(et)
                .setPositiveButton(R.string.custom_message_send,
                        new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        // The order is arranged in a way that the popup will be destroyed when the
                        // InCallActivity is about to finish.
                        final String textMessage = et.getText().toString().trim();
                        dismissCustomMessagePopup();
                        getPresenter().rejectCallWithMessage(textMessage);
                    }
                })
                .setNegativeButton(R.string.custom_message_cancel,
                        new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dismissCustomMessagePopup();
                        getPresenter().onDismissDialog();
                        /// M: For ALPS01233682 @{
                        if (mGlowpad != null) {
                            mGlowpad.startPing();
                        }
                        /// @}
                    }
                })
                .setTitle(R.string.respond_via_sms_custom_message);
        mCustomMessagePopup = builder.create();

        // Enable/disable the send button based on whether there is a message in the EditText
        et.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                final Button sendButton = mCustomMessagePopup.getButton(
                        DialogInterface.BUTTON_POSITIVE);
                sendButton.setEnabled(s != null && s.toString().trim().length() != 0);
            }
        });

        // Keyboard up, show the dialog
        mCustomMessagePopup.getWindow().setSoftInputMode(
                WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);

        /// M: for ALPS01234374 @{
        // ensure set null when dismissed
        mCustomMessagePopup.setOnDismissListener(new OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface dialog) {
                // TODO Auto-generated method stub
                Log.d(this, "mCustomMessagePopup on dismiss");
                mCustomMessagePopup = null;
                getPresenter().onDismissDialog();
            }
        });
        /// @}

        mCustomMessagePopup.show();

        // Send button starts out disabled
        final Button sendButton = mCustomMessagePopup.getButton(DialogInterface.BUTTON_POSITIVE);
        sendButton.setEnabled(false);
    }

    @Override
    public void configureMessageDialog(ArrayList<String> textResponses) {
        final ArrayList<String> textResponsesForDisplay = new ArrayList<String>(textResponses);

        textResponsesForDisplay.add(getResources().getString(
                R.string.respond_via_sms_custom_message));
        mTextResponsesAdapter = new ArrayAdapter<String>(getActivity(),
                android.R.layout.simple_list_item_1, android.R.id.text1, textResponsesForDisplay);
    }

    @Override
    public void onAnswer() {
        getPresenter().onAnswer();
    }

    @Override
    public void onDecline() {
        getPresenter().onDecline();
    }

    @Override
    public void onText() {
        getPresenter().onText();
    }

    /**
     * OnItemClickListener for the "Respond via SMS" popup.
     */
    public class RespondViaSmsItemClickListener implements AdapterView.OnItemClickListener {

        /**
         * Handles the user selecting an item from the popup.
         */
        @Override
        public void onItemClick(AdapterView<?> parent,  // The ListView
                View view,  // The TextView that was clicked
                int position, long id) {
            Log.d(this, "RespondViaSmsItemClickListener.onItemClick(" + position + ")...");
            final String message = (String) parent.getItemAtPosition(position);
            Log.v(this, "- message: '" + message + "'");
            dismissCannedResponsePopup();

            // The "Custom" choice is a special case.
            // (For now, it's guaranteed to be the last item.)
            if (position == (parent.getCount() - 1)) {
                // Show the custom message dialog
                showCustomMessageDialog();
            } else {
                getPresenter().rejectCallWithMessage(message);
            }
        }
    }
    //------------------------------------------Mediatek----------------------------------
    private TextView mTextView;
    /// M: Voice UI
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(this, "onCreate()...");
        getPresenter().setVoiceUIListener();
        CallCommandClient.getInstance().setUpVoiceCommandService();
    }
    
    /// M: For incoming menu @{
    private ImageButton mIncomingMenuButton;
    private PopupMenu mIncomingPopupMenu;

    private void onIncomingMenuButtonClick() {
        if (mIncomingPopupMenu != null) {
            mIncomingPopupMenu.dismiss();
        }
        mIncomingPopupMenu = constructIncomingPopupMenu(mIncomingMenuButton);
        if (mIncomingPopupMenu != null && mIncomingPopupMenu.getMenu().hasVisibleItems()) {
            mIncomingPopupMenu.show();
        }
    }

    private PopupMenu constructIncomingPopupMenu(View anchorView) {
        final PopupMenu popupMenu = new PopupMenu(getActivity(), anchorView);
        final Menu menu = popupMenu.getMenu();
        popupMenu.inflate(R.menu.mtk_incoming_call_menu);
        popupMenu.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                return handleIncomingMenuItemClickMTK(item);
            }
        });
        InCallUtils.setAllMenuVisible(menu, false);
        setupIncomingMenuItems(menu);
        return popupMenu;
    }

    public void setupIncomingMenuItems(Menu menu) {
        Log.d(this, "setupIncomingMenuItems()...");
        final MenuItem voiceAnswerMenu = menu.findItem(R.id.menu_vt_voice_answer);
        final MenuItem hangupActiveAndAnswerWaitingMenu = menu.findItem(R.id.menu_hangup_active_and_answer_waiting);

        if (InCallUtils.isDMLocked()) {
            return;
        }
        InCallUtils.updateIncomingMenuState();
        if (voiceAnswerMenu != null) {
            voiceAnswerMenu.setVisible(IncomingCallMenuState.sCanVTVoiceAnswer);
        }

        if (hangupActiveAndAnswerWaitingMenu != null) {
            hangupActiveAndAnswerWaitingMenu.setVisible(IncomingCallMenuState.sCanHangupActiveAndAnswerWaiting);
        }
    }

    public boolean onOptionsItemSelected(MenuItem item) {
        return handleIncomingMenuItemClickMTK(item);
    }

    private boolean handleIncomingMenuItemClickMTK(MenuItem item) {
        Log.d(this, "- onMenuItemClick: " + item);
        Log.d(this, "  id: " + item.getItemId());
        Log.d(this, "  title: '" + item.getTitle() + "'");
        switch (item.getItemId()) {
        case R.id.menu_vt_voice_answer:
            InCallPresenter.getInstance().setInVoiceAnswerVideoCall(true);
            CallCommandClient.getInstance().acceptVtCallWithVoiceOnly();
            break;

        case R.id.menu_hangup_active_and_answer_waiting:
            CallCommandClient.getInstance().hangupActiveAndAnswerWaiting();
            break;

        default:
            Log.e(this, "unhandled menu!!");
            break;
        }
        return true;
    }

    public static class IncomingCallMenuState {
        public static boolean sCanHangupActiveAndAnswerWaiting;
        public static boolean sCanVTVoiceAnswer;

        public static void reset(){
            sCanHangupActiveAndAnswerWaiting = false;
            sCanVTVoiceAnswer = false;
        }
    }

    public void updateIncomingCallMenuButton() {
        Log.d(this, "updateIncomingCallMenuButton()...");

        if (mGlowpad != null && mGlowpad.getVisibility() != View.VISIBLE) {
            Log.d(this, "updateIncomingCallMenuButton()... mGlowpad is invisible, dismiss popup menu.");
            /// M: For ALPS01266964 @{
            // dismiss mIncomingPopupMenu when answerFragment becomes invisible.
            // we only handle the popup menu we construct by clicking overflow button.
            // the option menu from InCallActvity by type PermanentMenuKey will not handled just like JB2.
            if (mIncomingPopupMenu != null) {
                mIncomingPopupMenu.dismiss();
            }
            /// @}
            return;
        }

        InCallUtils.updateIncomingMenuState();
        if (!InCallUtils.hasPermanentMenuKey(getActivity())
                && (IncomingCallMenuState.sCanVTVoiceAnswer || IncomingCallMenuState.sCanHangupActiveAndAnswerWaiting)) {
            mIncomingMenuButton.setVisibility(View.VISIBLE);
        } else {
            mIncomingMenuButton.setVisibility(View.GONE);
        }
    }
    /// @}

    @Override
    public void updatePromptsMessage(boolean show) {
        if (getPresenter().getIncomingCall() == null || mTextView == null) {
            Log.e(this, "updatePromptsMessage, incoming call is null or TextView is null");
            return;
        }
        mTextView.setVisibility(show ? View.VISIBLE : View.GONE);
        if (show) {
            String message = getPresenter().getIncomingCall().getRejectCallNotifyMsg();

            /// ALPS01260258,only reject call message is null , get voice UI message. @{
            if (TextUtils.isEmpty(message)) {
                message = getMessageForVoiceUI();
            }
            /// @}

            // if message is same as original text, we don't need to update
            // the text.
            Log.d(this, "updatePromptsMessage, message = " + message);
            if (message != null && !message.equals(mTextView.getText().toString())) {
                mTextView.requestFocus();
                mTextView.setText(message);
            } else if (message == null) {
                mTextView.setVisibility(View.GONE);
            }
        }
    }

    /// M: Add for ALPS01260356, Define message max length
    private final int MAX_MESSAGE_LEN = 140;

    /**
     * show the voice UI tip.
     * 1.no background call
     * 2.no foreground call
     * 3.no outgoing call
     * 4.only one incoming call
     * @return
     */
    private String getMessageForVoiceUI() {
        String message = null;
        CallList callList = CallList.getInstance();
        if (FeatureOptionWrapper.isSupportVoiceUI()
                && (callList.getBackgroundCall() == null)
                && (callList.getActiveCall() == null)
                && (callList.getSecondaryIncomingCall() == null)
                && (callList.getOutgoingCall() == null)
                && (callList.getIncomingCall() != null)
                && ((callList.getIncomingCall().getState() == Call.State.INCOMING)
                        || callList.getIncomingCall().getState() == Call.State.CALL_WAITING)) {
            message = getPresenter().getVoiceUIShowMsg();
        }
        Log.d(this, "message = " + message);
        return message;
    }
}
