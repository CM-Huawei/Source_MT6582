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

import android.animation.LayoutTransition;
import android.content.Context;
import android.content.Intent;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.ViewStub;
import android.view.accessibility.AccessibilityEvent;
import android.view.animation.Animation;
import android.view.animation.AnimationSet;
import android.view.animation.ScaleAnimation;
import android.view.animation.TranslateAnimation;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.LinearLayout.LayoutParams;

import com.android.incallui.InCallPresenter.InCallState;
import com.android.internal.telephony.PhoneConstants;
import com.android.phone.Constants;
import com.android.services.telephony.common.Call;
import com.mediatek.incallui.ext.ExtensionManager;
import com.mediatek.incallui.wrapper.FeatureOptionWrapper;
import com.mediatek.phone.SIMInfoWrapper;
import com.mediatek.telephony.SimInfoManager.SimInfoRecord;

import java.util.List;

/**
 * Fragment for call card.
 */
public class CallCardFragment extends BaseFragment<CallCardPresenter, CallCardPresenter.CallCardUi>
        implements CallCardPresenter.CallCardUi {

    // Primary caller info
    private TextView mPhoneNumber;
    private TextView mNumberLabel;
    private TextView mPrimaryName;
    private TextView mCallStateLabel;
    private TextView mCallTypeLabel;
    private ImageView mPhoto;
    ///M: Change featrue.@{
    // Gooogle code:
    /*
    private TextView mElapsedTime;
    */
    /// @}
    private View mProviderInfo;
    private TextView mProviderLabel;
    private TextView mProviderNumber;
    private ViewGroup mSupplementaryInfoContainer;

    // Secondary caller info
    private ViewStub mSecondaryCallInfo;
    private TextView mSecondaryCallName;
    private ImageView mSecondaryPhoto;
    private View mSecondaryPhotoOverlay;

    // Cached DisplayMetrics density.
    private float mDensity;

    @Override
    public
    CallCardPresenter.CallCardUi getUi() {
        return this;
    }

    @Override
    public
    CallCardPresenter createPresenter() {
        return new CallCardPresenter();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }


    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        final CallList calls = CallList.getInstance();
        final Call call = calls.getFirstCall();
        getPresenter().init(getActivity(), call);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        super.onCreateView(inflater, container, savedInstanceState);

        mDensity = getResources().getDisplayMetrics().density;

        final View view = inflater.inflate(R.layout.call_card, container, false);

        /// M: add listener to compute the vt dynamic layout. @{
        view.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
            @Override
            public void onLayoutChange(View v, int left, int top, int right, int bottom, int oldLeft,
                    int oldTop, int oldRight, int oldBottom) {
                if (v == view) {
                    final int[] location = new int[2];
                    view.findViewById(R.id.supplementary_info_container).getLocationInWindow(location);
                    final int callCardBottom = location[1];
                    if (callCardBottom > 0 && mCallCardHeight != callCardBottom) {
                        mCallCardHeight = callCardBottom;
                        Object activity = getActivity();
                        if (activity != null) {
                            ((InCallActivity) activity).onCallCardLayoutChange(callCardBottom);
                        }
                    }
                }
            }
        });
        /// @}

        return view;
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mPhoneNumber = (TextView) view.findViewById(R.id.phoneNumber);
        mPrimaryName = (TextView) view.findViewById(R.id.name);
        mNumberLabel = (TextView) view.findViewById(R.id.label);
        mSecondaryCallInfo = (ViewStub) view.findViewById(R.id.secondary_call_info);
        mPhoto = (ImageView) view.findViewById(R.id.photo);
        mCallStateLabel = (TextView) view.findViewById(R.id.callStateLabel);
        mCallTypeLabel = (TextView) view.findViewById(R.id.callTypeLabel);
        /// M: Change featrue. @{
        // Google code:
        /*
        mElapsedTime = (TextView) view.findViewById(R.id.elapsedTime);
        */
        /// @}
        mProviderInfo = view.findViewById(R.id.providerInfo);
        mProviderLabel = (TextView) view.findViewById(R.id.providerLabel);
        mProviderNumber = (TextView) view.findViewById(R.id.providerAddress);
        mSupplementaryInfoContainer =
            (ViewGroup) view.findViewById(R.id.supplementary_info_container);

        /// M: Add for MTK feature. @{
        onViewCreatedMTK(view);
        /// @}
    }

    @Override
    public void setVisible(boolean on) {
        if (on) {
            getView().setVisibility(View.VISIBLE);
        } else {
            getView().setVisibility(View.INVISIBLE);
        }
    }

    @Override
    public void setPrimaryName(String name, boolean nameIsNumber) {
        if (TextUtils.isEmpty(name)) {
            mPrimaryName.setText("");
        } else {
            mPrimaryName.setText(name);

            // Set direction of the name field
            int nameDirection = View.TEXT_DIRECTION_INHERIT;
            if (nameIsNumber) {
                nameDirection = View.TEXT_DIRECTION_LTR;
            }
            mPrimaryName.setTextDirection(nameDirection);
        }
    }

    @Override
    public void setPrimaryImage(Drawable image) {
        if (image != null) {
            setDrawableToImageView(mPhoto, image);
        }
    }

    @Override
    public void setPrimaryPhoneNumber(String number) {
        // Set the number
        if (TextUtils.isEmpty(number)) {
            mPhoneNumber.setText("");
            mPhoneNumber.setVisibility(View.GONE);
        } else {
            mPhoneNumber.setText(number);
            mPhoneNumber.setVisibility(View.VISIBLE);
            mPhoneNumber.setTextDirection(View.TEXT_DIRECTION_LTR);
        }
    }

    @Override
    public void setPrimaryLabel(String label) {
        if (!TextUtils.isEmpty(label)) {
            mNumberLabel.setText(label);
            mNumberLabel.setVisibility(View.VISIBLE);
        } else {
            mNumberLabel.setVisibility(View.GONE);
        }

    }

    @Override
    public void setPrimary(String number, String name, boolean nameIsNumber, String label,
            Drawable photo, boolean isConference, boolean isGeneric, boolean isSipCall, String location) {
        Log.d(this, "Setting primary call");

        if (isConference) {
            name = getConferenceString(isGeneric);
            photo = getConferencePhoto(isGeneric);
            nameIsNumber = false;
        }

        setPrimaryPhoneNumber(number);

        // set the name field.
        setPrimaryName(name, nameIsNumber);

        // Set the label (Mobile, Work, etc)
        setPrimaryLabel(label);

        showInternetCallLabel(isSipCall);

        setDrawableToImageView(mPhoto, photo);

        /// M: MTK add for geo description @{
        setLocation(location);
        /// @}
    }

    @Override
    public void setSecondary(boolean show, String name, boolean nameIsNumber, String label,
            Drawable photo, boolean isConference, boolean isGeneric) {

        if (show) {
            if (isConference) {
                name = getConferenceString(isGeneric);
                photo = getConferencePhoto(isGeneric);
                nameIsNumber = false;
            }

            showAndInitializeSecondaryCallInfo();
            mSecondaryCallName.setText(name);

            int nameDirection = View.TEXT_DIRECTION_INHERIT;
            if (nameIsNumber) {
                nameDirection = View.TEXT_DIRECTION_LTR;
            }
            mSecondaryCallName.setTextDirection(nameDirection);

            setDrawableToImageView(mSecondaryPhoto, photo);
        } else {
            mSecondaryCallInfo.setVisibility(View.GONE);
            /// M: Add for disable the secodary photo dim effect. @{
            if (mSecondaryPhotoOverlay != null
                    && mSecondaryPhotoOverlay.getVisibility() == View.VISIBLE) {
                AnimationUtils.Fade.hide(mSecondaryPhotoOverlay, View.GONE);
            }
            /// @}
        }
    }

    @Override
    public void setSecondaryImage(Drawable image) {
        if (image != null) {
            setDrawableToImageView(mSecondaryPhoto, image);
        }
    }

    @Override
    public void setCallState(int state, Call.DisconnectCause cause, boolean bluetoothOn,
            String gatewayLabel, String gatewayNumber) {
        String callStateLabel = null;

        // States other than disconnected not yet supported
        callStateLabel = getCallStateLabelFromState(state, cause);

        Log.v(this, "setCallState " + callStateLabel);
        Log.v(this, "DisconnectCause " + cause);
        Log.v(this, "bluetooth on " + bluetoothOn);
        Log.v(this, "gateway " + gatewayLabel + gatewayNumber);

        // There are cases where we totally skip the animation, in which case remove the transition
        // animation here and restore it afterwards.
        final boolean skipAnimation = (Call.State.isDialing(state)
                || state == Call.State.DISCONNECTED || state == Call.State.DISCONNECTING);
        LayoutTransition transition = null;
        if (skipAnimation) {
            transition = mSupplementaryInfoContainer.getLayoutTransition();
            mSupplementaryInfoContainer.setLayoutTransition(null);
        }

        // Update the call state label.
        /// M: For Change feature @{
        // Original Code:
        /*
        if (!TextUtils.isEmpty(callStateLabel)) {
        */
        /// @}
            mCallStateLabel.setVisibility(View.VISIBLE);
            /// M: if callStateLabel is null, not set label to avoid call time flicking @{
            // Original Code:
            /*
            mCallStateLabel.setText(callStateLabel);
            */
            if (null != callStateLabel) {
                mCallStateLabel.setText(callStateLabel);
            }
            /// @}

            /// M: for ALPS01275292 &&ALPS01275228 @{
            // update bluetooth icon also for waiting incoming call
            /*
            if (Call.State.INCOMING == state ) {
               setBluetoothOn(bluetoothOn);
            }
            */
            if (Call.State.INCOMING == state || Call.State.CALL_WAITING ==  state) {
                setBluetoothOn(bluetoothOn);
            } else {
                setBluetoothOn(false);
            }
            /// @}

        ///M: For Change feature remove code.
        // Original Code:
        /*
        } else {
            mCallStateLabel.setVisibility(View.GONE);
            // Gravity is aligned left when receiving an incoming call in landscape.
            // In that rare case, the gravity needs to be reset to the right.
            // Also, setText("") is used since there is a delay in making the view GONE,
            // so the user will otherwise see the text jump to the right side before disappearing.
            if(mCallStateLabel.getGravity() != Gravity.END) {
                mCallStateLabel.setText("");
                mCallStateLabel.setGravity(Gravity.END);
            }
        }
        */

        // Provider info: (e.g. "Calling via <gatewayLabel>")
        if (!TextUtils.isEmpty(gatewayLabel) && !TextUtils.isEmpty(gatewayNumber)) {
            mProviderLabel.setText(gatewayLabel);
            mProviderNumber.setText(gatewayNumber);
            mProviderInfo.setVisibility(View.VISIBLE);
        } else {
            mProviderInfo.setVisibility(View.GONE);
        }

        // Restore the animation.
        if (skipAnimation) {
            mSupplementaryInfoContainer.setLayoutTransition(transition);
        }
    }

    private void showInternetCallLabel(boolean show) {
        if (show) {
            final String label = getView().getContext().getString(
                    R.string.incall_call_type_label_sip);
            mCallTypeLabel.setVisibility(View.VISIBLE);
            mCallTypeLabel.setText(label);
        } else {
            mCallTypeLabel.setVisibility(View.GONE);
        }
    }

    @Override
    public void setPrimaryCallElapsedTime(boolean show, String callTimeElapsed) {
        if (show) {
            ///M: Change featrue on ui.@{
            // Google code:
            /*
            if (mElapsedTime.getVisibility() != View.VISIBLE) {
                AnimationUtils.Fade.show(mElapsedTime);
            }
            mElapsedTime.setText(callTimeElapsed);
            */
            /// @}
            mCallStateLabel.setVisibility(View.VISIBLE);
            mCallStateLabel.setText(callTimeElapsed);
        } else {
            // hide() animation has no effect if it is already hidden.
            ///M: Change featrue on ui.@{
            // Google code:
            /*
            AnimationUtils.Fade.hide(mElapsedTime, View.INVISIBLE);
            */
            /// @}
            mCallStateLabel.setVisibility(View.INVISIBLE);
        }
    }

    private void setDrawableToImageView(ImageView view, Drawable photo) {
        Log.d(this, "[setDrawableToImageView], photo = " + photo);
        if (!mIsPhotoVisible) {
            Log.d(this, "[setDrawableToImageView]not visible");
            return;
        }

        if (photo == null) {
            photo = view.getResources().getDrawable(R.drawable.picture_unknown);
            Log.d(this, "[setDrawableToImageView]set default photo");
        }

        final Drawable current = view.getDrawable();
        if (current == null) {
            Log.d(this, "[setDrawableToImageView]current is null");
            view.setImageDrawable(photo);
            AnimationUtils.Fade.show(view);
        } else {
            AnimationUtils.startCrossFade(view, current, photo);
            view.setVisibility(View.VISIBLE);
        }
    }

    private String getConferenceString(boolean isGeneric) {
        Log.v(this, "isGenericString: " + isGeneric);
        final int resId = isGeneric ? R.string.card_title_in_call : R.string.card_title_conf_call;
        return getView().getResources().getString(resId);
    }

    private Drawable getConferencePhoto(boolean isGeneric) {
        Log.v(this, "isGenericPhoto: " + isGeneric);
        final int resId = isGeneric ? R.drawable.picture_dialing : R.drawable.picture_conference;
        return getView().getResources().getDrawable(resId);
    }

    private void setBluetoothOn(boolean onOff) {
        // Also, display a special icon (alongside the "Incoming call"
        // label) if there's an incoming call and audio will be routed
        // to bluetooth when you answer it.
        final int bluetoothIconId = R.drawable.ic_in_call_bt_dk;

        if (onOff) {
            mCallStateLabel.setCompoundDrawablesWithIntrinsicBounds(bluetoothIconId, 0, 0, 0);
            mCallStateLabel.setCompoundDrawablePadding((int) (mDensity * 5));
        } else {
            // Clear out any icons
            mCallStateLabel.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0);
        }
    }

    /**
     * Gets the call state label based on the state of the call and
     * cause of disconnect
     */
    private String getCallStateLabelFromState(int state, Call.DisconnectCause cause) {
        final Context context = getView().getContext();
        String callStateLabel = null;  // Label to display as part of the call banner

        if (Call.State.IDLE == state) {
            // "Call state" is meaningless in this state.
            /// M: set callStateLabel as empty, not null @{
            // set callStateLabel as empty, not null
            callStateLabel = "";
            /// @}

        } else if (Call.State.ACTIVE == state) {
            // We normally don't show a "call state label" at all in
            // this state (but see below for some special cases).

        } else if (Call.State.ONHOLD == state) {
            callStateLabel = context.getString(R.string.card_title_on_hold);
        } else if (Call.State.DIALING == state) {
            callStateLabel = context.getString(R.string.card_title_dialing);
        } else if (Call.State.REDIALING == state) {
            callStateLabel = context.getString(R.string.card_title_redialing);
        } else if (Call.State.INCOMING == state || Call.State.CALL_WAITING == state) {
            if (getPresenter().isVTCall()) {
                callStateLabel = context.getString(R.string.card_title_incoming_vt_call);
            } else {
                callStateLabel = context.getString(R.string.card_title_incoming_call);
            }
        } else if (Call.State.DISCONNECTING == state) {
            // While in the DISCONNECTING state we display a "Hanging up"
            // message in order to make the UI feel more responsive.  (In
            // GSM it's normal to see a delay of a couple of seconds while
            // negotiating the disconnect with the network, so the "Hanging
            // up" state at least lets the user know that we're doing
            // something.  This state is currently not used with CDMA.)
            callStateLabel = context.getString(R.string.card_title_hanging_up);

        } else if (Call.State.DISCONNECTED == state) {
            callStateLabel = getCallFailedString(cause);

        } else {
            Log.wtf(this, "updateCallStateWidgets: unexpected call: " + state);
        }

        return callStateLabel;
    }

    /**
     * Maps the disconnect cause to a resource string.
     */
    private String getCallFailedString(Call.DisconnectCause cause) {
        int resID = R.string.card_title_call_ended;

        // TODO: The card *title* should probably be "Call ended" in all
        // cases, but if the DisconnectCause was an error condition we should
        // probably also display the specific failure reason somewhere...

        switch (cause) {
            case BUSY:
                resID = R.string.callFailed_userBusy;
                break;

            case CONGESTION:
                resID = R.string.callFailed_congestion;
                break;

            case TIMED_OUT:
                resID = R.string.callFailed_timedOut;
                break;

            case SERVER_UNREACHABLE:
                resID = R.string.callFailed_server_unreachable;
                break;

            case NUMBER_UNREACHABLE:
                resID = R.string.callFailed_number_unreachable;
                break;

            case INVALID_CREDENTIALS:
                resID = R.string.callFailed_invalid_credentials;
                break;

            case SERVER_ERROR:
                resID = R.string.callFailed_server_error;
                break;

            case OUT_OF_NETWORK:
                resID = R.string.callFailed_out_of_network;
                break;

            case LOST_SIGNAL:
            case CDMA_DROP:
                resID = R.string.callFailed_noSignal;
                break;

            case LIMIT_EXCEEDED:
                resID = R.string.callFailed_limitExceeded;
                break;

            case POWER_OFF:
                resID = R.string.callFailed_powerOff;
                break;

            case ICC_ERROR:
                resID = R.string.callFailed_simError;
                break;

            case OUT_OF_SERVICE:
                resID = R.string.callFailed_outOfService;
                break;

            case INVALID_NUMBER:
            case UNOBTAINABLE_NUMBER:
                resID = R.string.callFailed_unobtainable_number;
                break;

            default:
                resID = R.string.card_title_call_ended;
                break;
        }
        return this.getView().getContext().getString(resID);
    }

    private void showAndInitializeSecondaryCallInfo() {
        mSecondaryCallInfo.setVisibility(View.VISIBLE);

        // mSecondaryCallName is initialized here (vs. onViewCreated) because it is inaccesible
        // until mSecondaryCallInfo is inflated in the call above.
        if (mSecondaryCallName == null) {
            mSecondaryCallName = (TextView) getView().findViewById(R.id.secondaryCallName);
        }
        if (mSecondaryPhoto == null) {
            mSecondaryPhoto = (ImageView) getView().findViewById(R.id.secondaryCallPhoto);
        }

        mSecondaryPhotoOverlay = getView().findViewById(R.id.dim_effect_for_secondary_photo);
        mSecondaryPhotoOverlay.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                getPresenter().secondaryPhotoClicked();
            }
        });
        mSecondaryPhotoOverlay.setOnTouchListener(new SmallerHitTargetTouchListener());
        /// M: Add for enable the secodary photo dim effect. @{
        AnimationUtils.Fade.show(mSecondaryPhotoOverlay);
        setSecondaryCallClickable(true);
        /// @}

        /// M: @{
        if (null == mSecondaryCallBanner) {
            mSecondaryCallBanner = (ViewGroup) getView().findViewById(R.id.secondary_call_banner);
        }
        /// @}
    }

    public void dispatchPopulateAccessibilityEvent(AccessibilityEvent event) {
        if (event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            dispatchPopulateAccessibilityEvent(event, mPrimaryName);
            dispatchPopulateAccessibilityEvent(event, mPhoneNumber);
            return;
        }
        dispatchPopulateAccessibilityEvent(event, mCallStateLabel);
        dispatchPopulateAccessibilityEvent(event, mPrimaryName);
        dispatchPopulateAccessibilityEvent(event, mPhoneNumber);
        dispatchPopulateAccessibilityEvent(event, mCallTypeLabel);
        dispatchPopulateAccessibilityEvent(event, mSecondaryCallName);

        return;
    }

    private void dispatchPopulateAccessibilityEvent(AccessibilityEvent event, View view) {
        if (view == null) return;
        final List<CharSequence> eventText = event.getText();
        int size = eventText.size();
        view.dispatchPopulateAccessibilityEvent(event);
        // if no text added write null to keep relative position
        if (size == eventText.size()) {
            eventText.add(null);
        }
    }

    //--------------------------------MTK----------------------------------

    private TextView mSimIndicator;
    private int mSimIndicatorPaddingLeft;
    private int mSimIndicatorPaddingRight;
    private int mCallBannerSidePadding;
    private int mCallBannerTopBottomPadding;
    /** "Call banner" for the primary call */
    private ViewGroup mPrimaryCallBanner;
    /** Primary "call info" block (the foreground or ringing call) */
    private ViewGroup mPrimaryCallInfo;

    // secondary call UI related
    private ViewGroup mSecondaryCallBanner;
    private TextView mSecondaryCallStatus;

    // recording related
    private ImageView mVoiceRecorderIcon;

    private int[] mSimColorMap = {
            R.drawable.mtk_incall_status_color0,
            R.drawable.mtk_incall_status_color1,
            R.drawable.mtk_incall_status_color2,
            R.drawable.mtk_incall_status_color3,
        };

    private int[] mSimBorderMap = {
            R.drawable.mtk_sim_light_blue,
            R.drawable.mtk_sim_light_orange,
            R.drawable.mtk_sim_light_green,
            R.drawable.mtk_sim_light_purple,
        };

    /// Dualtalk variables start. @{
    // secondary incoming call UI related
    private ViewGroup mSecondIncomingCallBanner;
    private ImageView mPhotoIncomingPre;
    private ViewGroup mIncomingCall2Info;
    private View mIncomingCall2PhotoDimEffect;
    TextView m2ndIncomingName;
    TextView m2ndIncomingState;
    private boolean mNeedShowIncomingCall2Animator;
    private boolean mShowSwtchCall2Animator;
    private int mShowAnimator2End = 1;
    private int mIncomingCallInfoWidth;
    private int mIncomingCallInfoHeight;
    private int mIncomingCallInfoTopMargin;

    // secondary hold call UI related
    private ViewGroup mSecondHoldCallBanner;
    private ImageView mPhotoHoldPre;
    private ViewStub mDualTalkSecondaryCallInfo;
    private View mDualTalkSecondaryCallPhotoDimEffect;
    TextView m2ndHoldName;
    TextView m2ndHoldState;
    /// @}

    @Override
    public void setSimIndicator(int slotId, int callType) {
        mSlotId = slotId;
        mCallType = callType;
        SimInfoRecord simInfo = SIMInfoWrapper.getDefault().getSimInfoBySlot(slotId);
        if (simInfo != null) {
            Log.d("setSimIndicator", "iccid:" + simInfo.mIccId + " slot:" + simInfo.mSimSlotId
                    + " id:" + simInfo.mSimInfoId + " displayName:" + simInfo.mDisplayName
                    + " color:" + simInfo.mColor + " operator:" + simInfo.mOperator);
        }
        if (simInfo != null && !TextUtils.isEmpty(simInfo.mDisplayName)
                && (Call.CALL_TYPE_SIP != callType)) {
            mSimIndicator.setText(simInfo.mDisplayName);
            mSimIndicator.setVisibility(View.VISIBLE);
        } else if (Call.CALL_TYPE_SIP == callType) {
            mSimIndicator.setText(R.string.incall_call_type_label_sip);
            mSimIndicator.setVisibility(View.VISIBLE);
        } else {
            mSimIndicator.setVisibility(View.GONE);
        }
        // update call banner background according to mSimInfo.mColor
        updateCallBannerBackground(simInfo, mPrimaryCallBanner, callType);
    }

    /**
     * init Call Banner's padding dimensions. called in constructor CallCard().
     */
    private void initPaddingDimensions() {
        mCallBannerSidePadding = getActivity().getResources().getDimensionPixelSize(R.dimen.incoming_call_2_banner_side_padding);
        mCallBannerTopBottomPadding = getActivity().getResources().getDimensionPixelSize(R.dimen.call_banner_top_bottom_padding);
        mSimIndicatorPaddingLeft = getActivity().getResources().getDimensionPixelSize(R.dimen.call_banner_sim_indicator_padding_left);
        mSimIndicatorPaddingRight = getActivity().getResources().getDimensionPixelSize(R.dimen.call_banner_sim_indicator_padding_right);
    }

    /**
     * update secondary Call Banner's background.
     * @param simInfo
     * @param callBanner
     * @param callType
     */
    private void updateCallBannerBackground(SimInfoRecord simInfo, ViewGroup callBanner,
            int callType) {
        if (FeatureOptionWrapper.isSupportGemini()) {
            if (Call.CALL_TYPE_SIP == callType) {
                if (mPrimaryCallBanner == callBanner) {
                    if (null != mSimIndicator && mSimIndicator.getVisibility() == View.VISIBLE) {
                        mSimIndicator.setBackgroundResource(R.drawable.mtk_sim_light_internet_call);
                    }
                    if (null != mSupplementaryInfoContainer
                            && mSupplementaryInfoContainer.getVisibility() == View.VISIBLE) {
                        mSupplementaryInfoContainer
                                .setBackgroundResource(R.drawable.mtk_incall_status_color8);
                    }
                } else if (null != mSecondaryCallBanner
                        && mSecondaryCallBanner.getVisibility() == View.VISIBLE) {
                    mSecondaryCallBanner.setBackgroundResource(R.drawable.mtk_incall_status_color8);
                }
            } else {
                if (null == simInfo || null == mSimColorMap || simInfo.mColor < 0
                        || simInfo.mColor >= mSimColorMap.length) {
                    Log.d("updateCallBannerBackground",
                            "simInfo is null or simInfo.mColor invalid, do not update background");
                    return;
                }
                Log.i("updateCallBannerBackground", "GEMINI color=" + simInfo.mColor + ", slot="
                        + simInfo.mSimSlotId);
                if (mPrimaryCallBanner == callBanner) {
                    if (null != mSimIndicator && mSimIndicator.getVisibility() == View.VISIBLE) {
                        mSimIndicator.setBackgroundResource(mSimBorderMap[simInfo.mColor]);
                    }
                    if (null != mSupplementaryInfoContainer
                            && mSupplementaryInfoContainer.getVisibility() == View.VISIBLE) {
                        mSupplementaryInfoContainer
                                .setBackgroundResource(mSimColorMap[simInfo.mColor]);
                    }
                } else if (null != mSecondaryCallBanner
                        && mSecondaryCallBanner.getVisibility() == View.VISIBLE) {
                    mSecondaryCallBanner.setBackgroundResource(mSimColorMap[simInfo.mColor]);
                }
            }
        } else {
            if (null == simInfo || null == mSimColorMap || simInfo.mColor < 0
                    || simInfo.mColor >= mSimColorMap.length) {
                Log.d("updateCallBannerBackground",
                        "simInfo is null or simInfo.mColor invalid, set them to default value");
                if (Call.CALL_TYPE_SIP == callType) {
                    if (mPrimaryCallBanner == callBanner) {
                        if (null != mSimIndicator && mSimIndicator.getVisibility() == View.VISIBLE) {
                            mSimIndicator.setBackgroundResource(R.drawable.mtk_sim_light_internet_call);
                        }
                        if (null != mSupplementaryInfoContainer
                                && mSupplementaryInfoContainer.getVisibility() == View.VISIBLE) {
                            mSupplementaryInfoContainer
                                    .setBackgroundResource(R.drawable.mtk_incall_status_color8);
                        }
                    } else if (null != mSecondaryCallBanner
                            && mSecondaryCallBanner.getVisibility() == View.VISIBLE) {
                        mSecondaryCallBanner.setBackgroundResource(R.drawable.mtk_incall_status_color8);
                    }
                }
                return;
            }
            Log.i("updateCallBannerBackground", "color=" + simInfo.mColor + ", slot="
                    + simInfo.mSimSlotId);
            if (mPrimaryCallBanner == callBanner) {
                if (null != mSimIndicator && mSimIndicator.getVisibility() == View.VISIBLE) {
                    mSimIndicator.setBackgroundResource(mSimBorderMap[simInfo.mColor]);
                }
                if (null != mSupplementaryInfoContainer
                        && mSupplementaryInfoContainer.getVisibility() == View.VISIBLE) {
                    mSupplementaryInfoContainer.setBackgroundResource(mSimColorMap[simInfo.mColor]);
                }
            } else if (null != mSecondaryCallBanner
                    && mSecondaryCallBanner.getVisibility() == View.VISIBLE) {
                mSecondaryCallBanner.setBackgroundResource(mSimColorMap[simInfo.mColor]);
            }
        }
        if (null != mSecondaryCallBanner && mSecondaryCallBanner.getVisibility() == View.VISIBLE) {
             mSecondaryCallBanner.setPadding(mCallBannerSidePadding, 0,
             mCallBannerSidePadding, 0);
        }
        if (null != mSimIndicator && mSimIndicator.getVisibility() == View.VISIBLE) {
             mSimIndicator.setPadding(mSimIndicatorPaddingLeft, 0,
             mSimIndicatorPaddingRight, 0);
        }
    }

    /**
     * update secondary Call Banner's background.
     * @param slotId
     * @param callType
     */
    public void updateSecondaryCallBannerBackground(int slotId, int callType) {
        SimInfoRecord simInfo = SIMInfoWrapper.getDefault().getSimInfoBySlot(slotId);
        if (simInfo != null) {
            Log.d("setSimIndicator", "iccid:" + simInfo.mIccId + " slot:" + simInfo.mSimSlotId
                    + " id:" + simInfo.mSimInfoId + " displayName:" + simInfo.mDisplayName
                    + " color:" + simInfo.mColor + " operator:" + simInfo.mOperator);
        }
        updateCallBannerBackground(simInfo, mSecondaryCallBanner, callType);
    }

    public void setSecondaryCallClickable(boolean clickable) {
        if (mSecondaryPhotoOverlay != null) {
            Log.d("setSecondaryCallClickable", "clickable = " + clickable);
            mSecondaryPhotoOverlay.setEnabled(clickable);
        }
    }

    /// This is for geo description. @{
    private TextView mLocation;

    @Override
    public void setLocation(String location) {
        Log.d(this, "setLocation = " + location);

        if (TextUtils.isEmpty(location)) {
            mLocation.setText("");
        } else {
            mLocation.setText(location);
        }
    }
    /// @}

    private void initVoiceRecorderIcon(View view) {
        mVoiceRecorderIcon = (ImageView) view.findViewById(R.id.voiceRecorderIcon);
        mVoiceRecorderIcon.setBackgroundResource(R.drawable.mtk_voice_record);
        mVoiceRecorderIcon.setVisibility(View.INVISIBLE);
    }

    @Override
    public void updateVoiceRecordIcon(boolean show) {
        mVoiceRecorderIcon.setVisibility(show ? View.VISIBLE : View.INVISIBLE);
    }

    private boolean mIsPhotoVisible = true;

    void setPhotoVisible(boolean show) {
        mIsPhotoVisible = show;

        if (mPhoto != null) {
            mPhoto.setVisibility(show ? View.VISIBLE : View.INVISIBLE);
        }
    }

    public void onViewCreatedMTK(View view) {
        /// Change featrue for show sim information on ui. @{
        mPrimaryCallInfo = (ViewGroup) view.findViewById(R.id.primary_call_info);
        mSimIndicator = (TextView) view.findViewById(R.id.simIndicator);
        mPrimaryCallBanner = (ViewGroup) view.findViewById(R.id.primary_call_banner);
        initPaddingDimensions();
        /// @}

        // Add for geo description.
        mLocation = (TextView) view.findViewById(R.id.location);

        // Add for recording.
        initVoiceRecorderIcon(view);

        // Add for dualtalk.
        if (FeatureOptionWrapper.isSupportDualTalk()) {
            initResourcesForDualTalk(view);
        }

        // Add for plugin.
        ExtensionManager.getInstance().getCallCardExtension().onViewCreated(getActivity(), view);
    }

    public void updateCallInfoLayout(InCallState state) {
        PhoneConstants.State newState = PhoneConstants.State.IDLE;
        switch (state) {
            case INCALL:
            case OUTGOING:
                newState = PhoneConstants.State.OFFHOOK;
                break;
            case INCOMING:
                newState = PhoneConstants.State.RINGING;
                break;
            case NO_CALLS:
                newState = PhoneConstants.State.IDLE;
                break;
            default:
                break;
        }
        // / M: For CT Plugin. @{
        if (ExtensionManager.getInstance().getCallCardExtension().updateCallInfoLayout(newState)) {
            return;
        }
        // / @}

        // Based on the current state, update the overall
        // CallCard layout:

        // - Update the bottom margin of mCallInfoContainer to make sure
        // the call info area won't overlap with the touchable
        // controls on the bottom part of the screen.
        // Need todo somthing for MTK.
    }

    private int mCallCardHeight;

    /// For Dualtalk related start.  @{
    /**
     * init UI resources for dual talk; Called in onFinishInflate().
     */
    private void initResourcesForDualTalk(View view) {
        mIncomingCall2Info = (ViewGroup) view.findViewById(R.id.inset_incoming_call_2_info);
        mPhotoIncomingPre = (ImageView) view.findViewById(R.id.inset_Incoming_call_2_Photo);

        m2ndIncomingName = (TextView) view.findViewById(R.id.incoming_call_2_name);
        m2ndIncomingState = (TextView) view.findViewById(R.id.incoming_call_2_state);
        mSecondIncomingCallBanner = (ViewGroup) view.findViewById(R.id.incoming_call_2_banner);

        mIncomingCall2PhotoDimEffect = view.findViewById(R.id.dim_effect_for_incoming_call_2_photo);
        mIncomingCall2PhotoDimEffect.setEnabled(true);
        mIncomingCall2PhotoDimEffect.setClickable(true);
        mIncomingCall2PhotoDimEffect.setOnClickListener(callCardListener);
        // Add a custom OnTouchListener to manually shrink the
        // "hit target".
        mIncomingCall2PhotoDimEffect.setOnTouchListener(new SmallerHitTargetTouchListener());

        mIncomingCallInfoWidth = getActivity().getResources().getDimensionPixelSize(
                R.dimen.incoming_call_2_total_width);
        mIncomingCallInfoHeight = getActivity().getResources().getDimensionPixelSize(
                R.dimen.incoming_call_2_total_height);
        mIncomingCallInfoTopMargin = getActivity().getResources().getDimensionPixelSize(
                R.dimen.incoming_call_2_name_margintop);
    }

    @Override
    public void setSecondaryHold(boolean show, String name, boolean nameIsNumber, String label,
            Drawable photo, boolean isConference, boolean isGeneric) {

        showAndInitializeSecondaryHoldCallInfo();
        if (show) {
            if (isConference) {
                name = getConferenceString(isGeneric);
                photo = getConferencePhoto(isGeneric);
                nameIsNumber = false;
            }

            m2ndHoldName.setText(name);
            int nameDirection = View.TEXT_DIRECTION_INHERIT;
            if (nameIsNumber) {
                nameDirection = View.TEXT_DIRECTION_LTR;
            }
            m2ndHoldName.setTextDirection(nameDirection);

            m2ndHoldState.setText(getActivity().getString(R.string.card_title_on_hold));

            setDrawableToImageView(mPhotoHoldPre, photo);
        } else {
            mDualTalkSecondaryCallInfo.setVisibility(View.GONE);
            if (mDualTalkSecondaryCallPhotoDimEffect != null
                    && mDualTalkSecondaryCallPhotoDimEffect.getVisibility() == View.VISIBLE) {
                AnimationUtils.Fade.hide(mDualTalkSecondaryCallPhotoDimEffect, View.GONE);
            }
        }
    }

    @Override
    public void updateSecondaryHoldCallBannerBackground(int slotId, int callType) {
        SimInfoRecord simInfo = SIMInfoWrapper.getDefault().getSimInfoBySlot(slotId);
        if (simInfo != null) {
            Log.d("setSimIndicator", "iccid:" + simInfo.mIccId + " slot:" + simInfo.mSimSlotId
                    + " id:" + simInfo.mSimInfoId + " displayName:" + simInfo.mDisplayName
                    + " color:" + simInfo.mColor + " operator:" + simInfo.mOperator);
        }
        if (callType == Call.CALL_TYPE_SIP) {
            mSecondHoldCallBanner.setBackgroundResource(R.drawable.mtk_incall_status_color8);
        } else if (simInfo != null && (simInfo.mColor >= 0 && simInfo.mColor < mSimColorMap.length)) {
            mSecondHoldCallBanner.setBackgroundResource(mSimColorMap[simInfo.mColor]);
        }
        mSecondHoldCallBanner.setPadding(mCallBannerSidePadding, 0, mCallBannerSidePadding, 0);
    }

    void showAndInitializeSecondaryHoldCallInfo() {
        Log.d(this, "showAndInitializeSecondaryHoldCallInfo");

        if (null == mDualTalkSecondaryCallInfo) {
            mDualTalkSecondaryCallInfo = (ViewStub) getView().findViewById(
                    R.id.dual_talk_secondary_call_info);
            mDualTalkSecondaryCallInfo.setVisibility(View.VISIBLE);
            mPhotoHoldPre = (ImageView) getView().findViewById(R.id.inset_hold_call_2_Photo);
            m2ndHoldName = (TextView) getView().findViewById(R.id.hold_call_2_name);
            m2ndHoldState = (TextView) getView().findViewById(R.id.hold_call_2_state);
            mSecondHoldCallBanner = (ViewGroup) getView().findViewById(R.id.hold_call_2_banner);
            mDualTalkSecondaryCallPhotoDimEffect = getView().findViewById(
                    R.id.dim_effect_for_dual_talk_secondary_photo);
            mDualTalkSecondaryCallPhotoDimEffect.setEnabled(true);
            mDualTalkSecondaryCallPhotoDimEffect.setClickable(true);
            mDualTalkSecondaryCallPhotoDimEffect.setOnClickListener(callCardListener);
            // Add a custom OnTouchListener to manually shrink the
            // "hit target".
            mDualTalkSecondaryCallPhotoDimEffect
                    .setOnTouchListener(new SmallerHitTargetTouchListener());
        } else {
            mDualTalkSecondaryCallInfo.setVisibility(View.VISIBLE);
        }
        m2ndHoldName.setVisibility(View.VISIBLE);
        m2ndHoldState.setVisibility(View.VISIBLE);
        mPhotoHoldPre.setVisibility(View.VISIBLE);
        AnimationUtils.Fade.show(mDualTalkSecondaryCallPhotoDimEffect);
    }

    @Override
    public void setSecondaryIncoming(boolean show, String name, boolean nameIsNumber, String label,
            Drawable photo, boolean isConference, boolean isGeneric) {

        if (show) {
            if (isConference) {
                name = getConferenceString(isGeneric);
                photo = getConferencePhoto(isGeneric);
                nameIsNumber = false;
            }

            if (null != mIncomingCall2Info && View.GONE == mIncomingCall2Info.getVisibility()) {
                mNeedShowIncomingCall2Animator = true;
            }
            if (mNeedShowIncomingCall2Animator) {
                displaySecondaryIncomingAnimator();
            }

            if (null != mIncomingCall2Info && null != mIncomingCall2PhotoDimEffect) {
                mIncomingCall2Info.setVisibility(View.VISIBLE);
                AnimationUtils.Fade.show(mIncomingCall2PhotoDimEffect);
            }

            if (null != mPhotoIncomingPre) {
                mPhotoIncomingPre.setVisibility(View.VISIBLE);
                setDrawableToImageView(mPhotoIncomingPre, photo);
            }

            m2ndIncomingName.setText(name);
            int nameDirection = View.TEXT_DIRECTION_INHERIT;
            if (nameIsNumber) {
                nameDirection = View.TEXT_DIRECTION_LTR;
            }
            m2ndIncomingName.setTextDirection(nameDirection);
            m2ndIncomingName.setVisibility(View.VISIBLE);

            m2ndIncomingState.setVisibility(View.VISIBLE);
            String callState = "";
//            if (VTCallUtils.isVideoCall(call)) {
//                callState = getContext().getString(R.string.card_title_incoming_vt_call);
//            } else {
                callState = getActivity().getString(R.string.card_title_incoming_call);
//            }
            m2ndIncomingState.setText(callState);
        } else {
            mSecondIncomingCallBanner.setVisibility(View.GONE);
            if (mIncomingCall2PhotoDimEffect != null
                    && mIncomingCall2PhotoDimEffect.getVisibility() == View.VISIBLE) {
                AnimationUtils.Fade.hide(mIncomingCall2PhotoDimEffect, View.GONE);
            }
        }
    }

    @Override
    public void updateSecondaryIncomingCallBannerBackground(int slotId, int callType) {
        SimInfoRecord simInfo = SIMInfoWrapper.getDefault().getSimInfoBySlot(slotId);
        if (simInfo != null && (simInfo.mColor >= 0 && simInfo.mColor < mSimColorMap.length)) {
            if (null != mSecondIncomingCallBanner) {
                mSecondIncomingCallBanner.setBackgroundResource(mSimColorMap[simInfo.mColor]);
            }
        } else if (simInfo == null && (callType == Call.CALL_TYPE_SIP)) {
            if (null != mSecondIncomingCallBanner) {
                mSecondIncomingCallBanner
                        .setBackgroundResource(R.drawable.mtk_incall_status_color8);
            }
        }
        if (null != mSecondIncomingCallBanner) {
            mSecondIncomingCallBanner.setVisibility(View.VISIBLE);
        }
    }

    public void disableSecondHoldCallView() {
        if (null != m2ndHoldName) {
            m2ndHoldName.setVisibility(View.GONE);
        }
        if (null != m2ndHoldState) {
            m2ndHoldState.setVisibility(View.GONE);
        }
        if (null != mPhotoHoldPre) {
            mPhotoHoldPre.setVisibility(View.GONE);
        }
        if (null != mDualTalkSecondaryCallInfo) {
            mDualTalkSecondaryCallInfo.setVisibility(View.GONE);
        }
        if (null != mDualTalkSecondaryCallPhotoDimEffect) {
            AnimationUtils.Fade.hide(mDualTalkSecondaryCallPhotoDimEffect, View.GONE);
        }
    }

    public void disableSecondIncomingCallView(){
        if (null != m2ndIncomingName) {
            m2ndIncomingName.setVisibility(View.GONE);
        }
        if (null != m2ndIncomingState) {
            m2ndIncomingState.setVisibility(View.GONE);
        }
        if (null != mPhotoIncomingPre) {
            mPhotoIncomingPre.setVisibility(View.GONE);
        }
        if (null != mIncomingCall2PhotoDimEffect) {
            AnimationUtils.Fade.hide(mIncomingCall2PhotoDimEffect, View.GONE);
        }
        if (null != mSecondIncomingCallBanner) {
            mSecondIncomingCallBanner.setVisibility(View.GONE);
        }
        if (null != mIncomingCall2Info) {
            mIncomingCall2Info.setVisibility(View.GONE);
            mNeedShowIncomingCall2Animator = false;
        }
    }

    View.OnClickListener callCardListener = new View.OnClickListener() {

        public void onClick(View v) {
            int id = v.getId();
            switch (id) {
                case R.id.dim_effect_for_incoming_call_2_photo:
                    mShowSwtchCall2Animator = true;
                    displaySwitchIncomingAnimator();
                    break;

                case R.id.dim_effect_for_dual_talk_secondary_photo:
                    Log.d("callCardListener", "Which call to disconnected?");
                    getPresenter().onDualtalkSecondaryPhotoClicked();
                    break;

                default:
                    Log.d("callCardListener", "do nothing");
                    break;
            }
        }
    };

    Animation.AnimationListener mAnimationListener = new Animation.AnimationListener() {
        public void onAnimationEnd(Animation animation) {
            Log.d(this, "onAnimationEnd");
            boolean allAnimationEnd = false;
            int animationCount = ((AnimationSet)animation).getAnimations().size();
            mShowAnimator2End++;

            Log.d(this, " mShowAnimator2End " + mShowAnimator2End + " animationCount " + animationCount);

            if (mShowAnimator2End == animationCount) {
                allAnimationEnd = true;
            }

            if (allAnimationEnd && mShowSwtchCall2Animator) {
                //when animation end, switch 2 incomming call
                getPresenter().switchCalls();
                /// M: add for supporting BT HTP (I,I)
                broadRingCallToggled();

                CallList.getInstance().requestUpdateScreen();
                mShowSwtchCall2Animator = false;
                getPresenter().switchRingtoneForDualTalk();
            }
            if (allAnimationEnd) {
                RelativeLayout relativeLayout = (RelativeLayout) getView().findViewById(R.id.inset_incoming_call_2_info);
                ViewGroup.MarginLayoutParams source = new ViewGroup.MarginLayoutParams(
                        mIncomingCallInfoWidth, mIncomingCallInfoHeight);
                source.topMargin = mIncomingCallInfoTopMargin;
                RelativeLayout.LayoutParams param = new RelativeLayout.LayoutParams(source);
                param.addRule(RelativeLayout.ALIGN_PARENT_RIGHT);
                relativeLayout.setLayoutParams(param);
                mIncomingCall2Info.clearAnimation();
                mShowAnimator2End = 0;
            }
        }
        public void onAnimationRepeat(Animation animation) {
            Log.d(this, "onAnimationRepeat");
        }
        public void onAnimationStart(Animation animation) {
            Log.d(this, "onAnimationStart");
            RelativeLayout relativeLayout = (RelativeLayout) getView().findViewById(R.id.inset_incoming_call_2_info);
            ViewGroup.MarginLayoutParams source = new ViewGroup.MarginLayoutParams(
                    LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
            source.topMargin = 0;
            RelativeLayout.LayoutParams param = new RelativeLayout.LayoutParams(source);
            relativeLayout.setLayoutParams(param);
            if (mNeedShowIncomingCall2Animator) {
                mNeedShowIncomingCall2Animator = false;
            }
        }
    };

    private void displaySecondaryIncomingAnimator() {
        // Animate the photo view into its end location.
        AnimationSet animationSet = new AnimationSet(true);

        int dx = mPrimaryCallInfo.getRight() + mIncomingCallInfoWidth;
        int dy = mIncomingCallInfoTopMargin + mIncomingCallInfoHeight;
        TranslateAnimation translateAnimation = new TranslateAnimation(0, dx, 0, dy);
        translateAnimation.setDuration(300);
        animationSet.addAnimation(translateAnimation);

        ScaleAnimation myAnimationScale = new ScaleAnimation(0.0f, 0.4f, 0.0f, 0.4f,
                Animation.RELATIVE_TO_SELF, 0.0f, Animation.RELATIVE_TO_SELF, 0.0f);
        myAnimationScale.setDuration(0);
        animationSet.addAnimation(myAnimationScale);

        animationSet.setAnimationListener(mAnimationListener);
        mIncomingCall2Info.startAnimation(animationSet);
    }

    private void displaySwitchIncomingAnimator() {
        // Animate the photo view into its end location.
        int dx = mPrimaryCallInfo.getRight() - mIncomingCallInfoWidth;

        AnimationSet animationSet = new AnimationSet(true);
        TranslateAnimation translateAnimation = new TranslateAnimation(dx, 0, mIncomingCallInfoTopMargin, 0);
        translateAnimation.setDuration(500);
        animationSet.addAnimation(translateAnimation);

        ScaleAnimation myAnimationScale = new ScaleAnimation(0.4f, 0.0f, 0.4f, 0.0f,
            Animation.RELATIVE_TO_SELF, 0.0f, Animation.RELATIVE_TO_SELF, 0.0f);
        myAnimationScale.setDuration(500);
        animationSet.addAnimation(myAnimationScale);
        animationSet.setAnimationListener(mAnimationListener);

        mIncomingCall2Info.startAnimation(animationSet);
    }

    /**
     *  M: BT HFP Dual Talk
     *  Boradcast incoming call toggled when call switched.
     */
    private void broadRingCallToggled() {
        Log.d(this, "broadRingCallToggled() action:" + Constants.ACTION_RING_CALL_TOGGLED);
        Intent intent = new Intent(Constants.ACTION_RING_CALL_TOGGLED);
        getActivity().sendBroadcast(intent);
    }
    /// @}

    /// M: Add for ALPS01265934
    //  Keep a copy of callType & slotId
    //  when setSimIndicator @{
    private int mCallType = Call.CALL_TYPE_VOICE;
    private int mSlotId;

    /**
     * Update the CallCardFragment when the InCallActivity onResume.
     * TODO: We may need update many things.
     */
    public void updateCallCard() {
        Log.d(this, "[updateSimIndicator]mSlotId = " + mSlotId
                + "; mCallType =" + mCallType);
        setSimIndicator(mSlotId, mCallType);
    }
    /// @}
}
