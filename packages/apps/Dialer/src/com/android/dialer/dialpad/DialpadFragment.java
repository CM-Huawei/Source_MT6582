/*
 * Copyright (C) 2011 The Android Open Source Project
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

package com.android.dialer.dialpad;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.Fragment;
import android.content.ContentResolver;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
/** M: New Feature Phone Landscape UI @{ */
import android.content.res.Configuration;
/** @ }*/
import android.content.res.Resources;
import android.database.ContentObserver;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Drawable;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.SystemProperties;
import android.provider.Contacts.Intents.Insert;
import android.provider.ContactsContract.Contacts;
import android.provider.Contacts.People;
import android.provider.Contacts.Phones;
import android.provider.Contacts.PhonesColumns;
import android.provider.ContactsContract.ProviderStatus;
import android.provider.ContactsContract.Intents;
import android.provider.Settings;
import android.telephony.PhoneNumberUtils;
import android.telephony.PhoneStateListener;
import android.telephony.ServiceState;
import android.telephony.TelephonyManager;
import android.text.Editable;
import android.text.InputFilter;
import android.text.SpannableString;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.style.RelativeSizeSpan;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.TypedValue;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.AbsListView;
import android.widget.AbsListView.OnScrollListener;
import android.view.ViewTreeObserver;
import android.view.ViewTreeObserver.OnPreDrawListener;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.PopupMenu;
import android.widget.TableRow;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.android.contacts.common.CallUtil;
import com.android.contacts.common.GeoUtil;
import com.android.contacts.common.activity.TransactionSafeActivity;
import com.android.contacts.common.preference.ContactsPreferences;
import com.android.contacts.common.util.Constants;
import com.android.contacts.common.util.PhoneNumberFormatter;
import com.android.contacts.common.util.StopWatch;
import com.android.dialer.NeededForReflection;
import com.android.dialer.DialerApplication;
import com.android.dialer.DialtactsActivity;
import com.android.dialer.R;
import com.android.dialer.SpecialCharSequenceMgr;
import com.android.dialer.database.DialerDatabaseHelper;
import com.android.dialer.interactions.PhoneNumberInteraction;
import com.android.dialer.util.OrientationUtil;
import com.android.internal.telephony.CallerInfo;
import com.android.internal.telephony.ITelephony;
import com.android.phone.common.CallLogAsync;
import com.android.phone.common.HapticFeedback;
import com.google.common.annotations.VisibleForTesting;

import java.util.HashSet;
import java.util.List;

/// The following lines are provided and maintained by Mediatek Inc.
import com.mediatek.calloption.SimAssociateHandler;
import com.mediatek.contacts.ContactsFeatureConstants.FeatureOption;
import com.mediatek.contacts.ExtensionManager;
import com.mediatek.contacts.Profiler;
import com.mediatek.contacts.ext.ContactPluginDefault;
import com.mediatek.contacts.ext.IDialpadFragment;
import com.mediatek.contacts.simcontact.SlotUtils;
import com.mediatek.dialer.SpecialCharSequenceMgrProxy;
import com.mediatek.dialer.calloption.ContactsCallOptionHandlerFactory;
import com.mediatek.dialer.activities.SpeedDialManageActivity;
import com.mediatek.dialer.dialpad.AutoScaleTextSizeWatcher;
import com.mediatek.dialer.dialpad.DialerSearchAdapter;
import com.mediatek.dialer.dialpad.SpeedDial;
import com.mediatek.dialer.list.ProviderStatusWatcher.Status;
import com.mediatek.dialer.util.DialerUtils;
import com.mediatek.dialer.util.LogUtils;
import com.mediatek.dialer.util.PhoneCapabilityTester;
import com.mediatek.dialer.calloption.ContactsCallOptionHandler;
import com.mediatek.phone.SIMInfoWrapper;
import com.mediatek.telephony.TelephonyManagerEx;
import com.mediatek.dialer.calllogex.PhoneNumberHelperEx;
import android.provider.ContactsContract;
/// The previous lines are provided and maintained by Mediatek Inc.

/**
 * Fragment that displays a twelve-key phone dialpad.
 */
public class DialpadFragment extends Fragment
        implements View.OnClickListener,
        View.OnLongClickListener, View.OnKeyListener,
        AdapterView.OnItemClickListener, TextWatcher,
        PopupMenu.OnMenuItemClickListener,
        DialpadKeyButton.OnPressedListener,
        IDialpadFragment {
    private static final String TAG = DialpadFragment.class.getSimpleName();
    private DialogFragment mSpeedDialConfirmDialogFragment;

    public interface OnDialpadFragmentStartedListener {
        public void onDialpadFragmentStarted();
    }

    /**
     * LinearLayout with getter and setter methods for the translationY property using floats,
     * for animation purposes.
     */
    public static class DialpadSlidingLinearLayout extends LinearLayout {

        public DialpadSlidingLinearLayout(Context context) {
            super(context);
        }

        public DialpadSlidingLinearLayout(Context context, AttributeSet attrs) {
            super(context, attrs);
        }

        public DialpadSlidingLinearLayout(Context context, AttributeSet attrs, int defStyle) {
            super(context, attrs, defStyle);
        }

        @NeededForReflection
        public float getYFraction() {
            final int height = getHeight();
            if (height == 0) return 0;
            return getTranslationY() / height;
        }

        @NeededForReflection
        public void setYFraction(float yFraction) {
            setTranslationY(yFraction * getHeight());
        }
    }

    /**
     * LinearLayout that always returns true for onHoverEvent callbacks, to fix
     * problems with accessibility due to the dialpad overlaying other fragments.
     */
    public static class HoverIgnoringLinearLayout extends LinearLayout {

        public HoverIgnoringLinearLayout(Context context) {
            super(context);
        }

        public HoverIgnoringLinearLayout(Context context, AttributeSet attrs) {
            super(context, attrs);
        }

        public HoverIgnoringLinearLayout(Context context, AttributeSet attrs, int defStyle) {
            super(context, attrs, defStyle);
        }

        @Override
        public boolean onHoverEvent(MotionEvent event) {
            return true;
        }
    }

    public interface OnDialpadQueryChangedListener {
        void onDialpadQueryChanged(String query);
    }

    private static final boolean DEBUG = DialtactsActivity.DEBUG;

    // This is the amount of screen the dialpad fragment takes up when fully displayed
    private static final float DIALPAD_SLIDE_FRACTION = 0.67f;

    private static final String EMPTY_NUMBER = "";
    private static final char PAUSE = ',';
    private static final char WAIT = ';';

    /** The length of DTMF tones in milliseconds */
    private static final int TONE_LENGTH_MS = 100;
    private static final int TONE_LENGTH_INFINITE = -1;

    /** The DTMF tone volume relative to other sounds in the stream */
    private static final int TONE_RELATIVE_VOLUME = 80;

    /** Stream type used to play the DTMF tones off call, and mapped to the volume control keys */
    private static final int DIAL_TONE_STREAM_TYPE = AudioManager.STREAM_DTMF;

    private ContactsPreferences mContactsPrefs;
    private PopupMenu popup = null;

    private View fragmentView = null;

    private static final boolean DBG = true;
    private boolean mIsForeground = false;
    private OnDialpadQueryChangedListener mDialpadQueryListener;

    /**
     * View (usually FrameLayout) containing mDigits field. This can be null, in which mDigits
     * isn't enclosed by the container.
     */
    private View mDigitsContainer;
    private EditText mDigits;
 
    ///M: ALPS00936186 @{
    // Hilight string may got err when number length more than 255.
    //
    //Original Code:
    //private static final int MAX_DIGITS_NUMBER_LENGTH = 1024;  
    private static final int MAX_DIGITS_NUMBER_LENGTH = 255;
    /// @}
    /** Remembers if we need to clear digits field when the screen is completely gone. */
    private boolean mClearDigitsOnStop;

    private View mDelete;
    private ToneGenerator mToneGenerator;
    private final Object mToneGeneratorLock = new Object();
    private View mDialpad;
    private View mSpacer;

    private View mAdditionalButtonsRow;
    /** M: New Feature Phone Landscape UI @{ */
    //device is landscape or not
    public boolean ISTABLET_LAND = false;
/** @ }*/

    /**
     * Set of dialpad keys that are currently being pressed
     */
    private final HashSet<View> mPressedDialpadKeys = new HashSet<View>(12);
       
    /**
     * Remembers the number of dialpad buttons which are pressed at this moment.
     * If it becomes 0, meaning no buttons are pressed, we'll call
     * {@link ToneGenerator#stopTone()}; the method shouldn't be called unless the last key is
     * released.
     */
    private int mDialpadPressCount;

    private View mDialButtonContainer;
    private View mDialButton;
    private ListView mDialpadChooser;
    private DialpadChooserAdapter mDialpadChooserAdapter;

    /**
     * Regular expression prohibiting manual phone call. Can be empty, which means "no rule".
     */
    private String mProhibitedPhoneNumberRegexp;


    // Last number dialed, retrieved asynchronously from the call DB
    // in onCreate. This number is displayed when the user hits the
    // send key and cleared in onPause.
    private final CallLogAsync mCallLog = new CallLogAsync();
    private String mLastNumberDialed = EMPTY_NUMBER;

    // determines if we want to playback local DTMF tones.
    private boolean mDTMFToneEnabled;

    // Vibration (haptic feedback) for dialer key presses.
    private final HapticFeedback mHaptic = new HapticFeedback();

    /** Identifier for the "Add Call" intent extra. */
    private static final String ADD_CALL_MODE_KEY = "add_call_mode";

    /**
     * Identifier for intent extra for sending an empty Flash message for
     * CDMA networks. This message is used by the network to simulate a
     * press/depress of the "hookswitch" of a landline phone. Aka "empty flash".
     *
     * TODO: Using an intent extra to tell the phone to send this flash is a
     * temporary measure. To be replaced with an ITelephony call in the future.
     * TODO: Keep in sync with the string defined in OutgoingCallBroadcaster.java
     * in Phone app until this is replaced with the ITelephony API.
     */
    private static final String EXTRA_SEND_EMPTY_FLASH
            = "com.android.phone.extra.SEND_EMPTY_FLASH";

    private String mCurrentCountryIso;

    private PhoneNumberHelperEx mPhoneNumberHelper;

    private final PhoneStateListener mPhoneStateListener = new PhoneStateListener() {
        /**
         * Listen for phone state changes so that we can take down the
         * "dialpad chooser" if the phone becomes idle while the
         * chooser UI is visible.
         */
        @Override
        public void onCallStateChanged(int state, String incomingNumber) {
            // Log.i(TAG, "PhoneStateListener.onCallStateChanged: "
            //       + state + ", '" + incomingNumber + "'");
            if (state == TelephonyManager.CALL_STATE_IDLE) {
                // Log.i(TAG, "Call ended with dialpad chooser visible!  Taking it down...");
                // Note there's a race condition in the UI here: the
                // dialpad chooser could conceivably disappear (on its
                // own) at the exact moment the user was trying to select
                // one of the choices, which would be confusing.  (But at
                // least that's better than leaving the dialpad chooser
                // onscreen, but useless...)
                //On gemini platform, the phone state need to check SIM1 & SIM2
                //for current state, we only check if the phone is IDLE
                final boolean phoneIsInUse = phoneIsInUse();
                if (isDialpadChooserVisible()) {
                    if (!phoneIsInUse) {
                        showDialpadChooser(false);
                        adjustListViewLayoutParameters();
                    }
                }

                if (!phoneIsInUse) {
                    if (mDigits != null) {
                        mDigits.setHint(null);
                    }
                }
            }
            }

        @Override
        public void onServiceStateChanged(ServiceState serviceState) {
            log("onServiceStateChanged, serviceState = " + serviceState);
            if (getActivity() == null) {
                return;
            }

            /** M: Modify for text watcher when service state change */
            // if(serviceState.getState() == ServiceState.STATE_IN_SERVICE) {
            String newIso = DialerUtils.getCurrentCountryIso(getActivity());
            if (mCurrentCountryIso != null && !mCurrentCountryIso.equals(newIso)) {
                mCurrentCountryIso = newIso;
                if (mTextWatcher != null) {
                    mDigits.removeTextChangedListener(mTextWatcher);
                }

                log("re-set phone number formatting text watcher, mCurrentCountryIso = "
                        + mCurrentCountryIso + " newIso = " + newIso);
                mDigits.setTag(mHandler.obtainMessage(MSG_GET_TEXT_WATCHER));
                PhoneNumberFormatter.setPhoneNumberFormattingTextWatcher(getActivity(), mDigits,
                        mHandler);
            }
            // }
        }
    };

    private boolean mWasEmptyBeforeTextChange;

    /**
     * This field is set to true while processing an incoming DIAL intent, in order to make sure
     * that SpecialCharSequenceMgr actions can be triggered by user input but *not* by a
     * tel: URI passed by some other app.  It will be set to false when all digits are cleared.
     */
    private boolean mDigitsFilledByIntent;

    private boolean mStartedFromNewIntent = false;
    private boolean mFirstLaunch = false;
    private boolean mAdjustTranslationForAnimation = false;

    private static final String PREF_DIGITS_FILLED_BY_INTENT = "pref_digits_filled_by_intent";

    /**
     * Return an Intent for launching voicemail screen.
     */
    private static Intent getVoicemailIntent() {
        final Intent intent = new Intent(Intent.ACTION_CALL_PRIVILEGED,
                Uri.fromParts("voicemail", "", null));
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return intent;
    }

    private boolean mNeedCheckSetting = false;
    private TelephonyManager getTelephonyManager() {
        return (TelephonyManager) getActivity().getSystemService(Context.TELEPHONY_SERVICE);
    }

    @Override
    public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        mWasEmptyBeforeTextChange = TextUtils.isEmpty(s);
    }

    @Override
    public void onTextChanged(CharSequence input, int start, int before, int changeCount) {
        if (mWasEmptyBeforeTextChange != TextUtils.isEmpty(input)) {
            final Activity activity = getActivity();
            if (activity != null) {
                activity.invalidateOptionsMenu();
            }
        }

        // DTMF Tones do not need to be played here any longer -
        // the DTMF dialer handles that functionality now.
    }

    @Override
    public void afterTextChanged(Editable input) {
        // When DTMF dialpad buttons are being pressed, we delay SpecialCharSequencMgr sequence,
        // since some of SpecialCharSequenceMgr's behavior is too abrupt for the "touch-down"
        // behavior.
        if (!mDigitsFilledByIntent &&
                /// M: for Gemini && ALPS01241534 @{
                // SpecialCharSequenceMgr.handleChars(getActivity(), input.toString(), mDigits)) {
                SpecialCharSequenceMgrProxy.handleChars(getActivity(), input.toString(), mDigits)) {
                /// @}
            // A special sequence was entered, clear the digits
                /// M: Fix CR: ALPS01317195.
                mDigits.getText().clear();
        }

        int digitsVisibility = getDigitsVisibility();
        updateDialAndDeleteButtonEnabledState();

        final boolean isDigitsEmpty = isDigitsEmpty();
        if (isDigitsEmpty) {
            mDigitsFilledByIntent = false;
            mDigits.setCursorVisible(false);
        } else {
            if (!isDialpadChooserVisible()) {
                log("afterTextChanged, show digits");
                if (mDigitsContainer != null) {
                    mDigitsContainer.setVisibility(View.VISIBLE);
                    mDigits.setVisibility(View.VISIBLE);
                    mDelete.setVisibility(View.VISIBLE);
                } else if (mDelete != null && mDigits != null) {
                    mDigits.setVisibility(View.VISIBLE);
                    mDelete.setVisibility(View.VISIBLE);
                }
            }
        }
        /// M: fix CR:ALPS01285185
        if (mDialpadQueryListener != null) {
            mDialpadQueryListener.onDialpadQueryChanged(mDigits.getText().toString());
        }
        updateDialAndDeleteButtonEnabledState();
    }

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        mFirstLaunch = true;
        mContactsPrefs = new ContactsPreferences(getActivity());
        mCurrentCountryIso = GeoUtil.getCurrentCountryIso(getActivity());

        try {
            mHaptic.init(getActivity(),
                         getResources().getBoolean(R.bool.config_enable_dialer_key_vibration));
        } catch (Resources.NotFoundException nfe) {
             Log.e(TAG, "Vibrate control bool missing.", nfe);
        }

        mProhibitedPhoneNumberRegexp = getResources().getString(
                R.string.config_prohibited_phone_number_regexp);

        /**
         * Change Feature by mediatek .inc description : initialize speed dial
         */
        if (DialerApplication.sSpeedDial) {
            mSpeedDial = new SpeedDial(getActivity());
        }
        mCallOptionHandler = new ContactsCallOptionHandler(getActivity(),
                new ContactsCallOptionHandlerFactory());
        mFragmentState = FragmentState.CREATED;
        /**
         * Change Feature by mediatek end
         */

        if (state != null) {
            mDigitsFilledByIntent = state.getBoolean(PREF_DIGITS_FILLED_BY_INTENT);
        }
        ExtensionManager.getInstance().getDialPadExtension().onCreate(this, this);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        log("onDestroy");

        mFragmentState = FragmentState.DESTROYED;
        ExtensionManager.getInstance().getDialPadExtension().onDestroy();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedState) {
        final View fragmentView = inflater.inflate(R.layout.dialpad_fragment, container,
                false);
        fragmentView.buildLayer();
        ExtensionManager.getInstance().getDialPadExtension().onCreateView(inflater, container, savedState, fragmentView);
         /** M: New Feature Phone Landscape UI @{ */
        if (PhoneCapabilityTester.isUsingTwoPanes(getActivity()) 
			&& getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
            ISTABLET_LAND = true;
        } else {
            ISTABLET_LAND = false;
        }
        /** @ } */
        final ViewTreeObserver vto = fragmentView.getViewTreeObserver();
        // Adjust the translation of the DialpadFragment in a preDrawListener instead of in
        // DialtactsActivity, because at the point in time when the DialpadFragment is added,
        // its views have not been laid out yet.
        final OnPreDrawListener preDrawListener = new OnPreDrawListener() {

            @Override
            public boolean onPreDraw() {

                if (isHidden()) return true;
                if (mAdjustTranslationForAnimation && fragmentView.getTranslationY() == 0) {
                    ((DialpadSlidingLinearLayout) fragmentView).setYFraction(
                            DIALPAD_SLIDE_FRACTION);
                }
                final ViewTreeObserver vto = fragmentView.getViewTreeObserver();
                vto.removeOnPreDrawListener(this);
                return true;
            }

        };

        vto.addOnPreDrawListener(preDrawListener);

        // Load up the resources for the text field.
        Resources r = getResources();

        mDialButtonContainer = fragmentView.findViewById(R.id.dialButtonContainer);
        mDigitsContainer = fragmentView.findViewById(R.id.digits_container);
        mDigits = (EditText) fragmentView.findViewById(R.id.digits);
        mDigits.setKeyListener(UnicodeDialerKeyListener.INSTANCE);
        mDigits.setOnClickListener(this);
        mDigits.setOnKeyListener(this);
        mDigits.setOnLongClickListener(this);
        mDigits.addTextChangedListener(this);
        mDigits.setFilters(new InputFilter[]{new InputFilter.LengthFilter(MAX_DIGITS_NUMBER_LENGTH)});
        /**
         * Change Feature by mediatek .inc
         */
        mAutoScaleTextSizeWatcher = new AutoScaleTextSizeWatcher(mDigits);
        mAutoScaleTextSizeWatcher.setAutoScaleParameters(r
                .getDimensionPixelSize(R.dimen.dialpad_digits_text_size_min), r
                .getDimensionPixelSize(R.dimen.dialpad_digits_text_size), r
                .getDimensionPixelSize(R.dimen.dialpad_digits_text_size_delta), r
                .getDimensionPixelSize(R.dimen.dialpad_digits_width)); 
        mDigits.addTextChangedListener(mAutoScaleTextSizeWatcher);

        /**
         * Change Feature by mediatek .inc end
         */

        mDigits.setTag(mHandler.obtainMessage(MSG_GET_TEXT_WATCHER));
        String newIso = DialerUtils.getCurrentCountryIso(getActivity());
        if (mCurrentCountryIso != null && !mCurrentCountryIso.equals(newIso)) {
            mCurrentCountryIso = newIso;
        }
        log("onCreateView setPhoneNumberFormattingTextWatcher:" + mCurrentCountryIso);
        
        /** M:  modify @ { */
        /**
         * PhoneNumberFormatter.setPhoneNumberFormattingTextWatcher(getActivity(), mDigits);
         */
        PhoneNumberFormatter.setPhoneNumberFormattingTextWatcher(getActivity(), mDigits, mHandler);
        /** @ }*/
        
        // Check for the presence of the keypad
        View oneButton = fragmentView.findViewById(R.id.one);
        if (oneButton != null) {
            setupKeypad(fragmentView);
        }

        mDialButton = fragmentView.findViewById(R.id.dialButton);
        if (r.getBoolean(R.bool.config_show_onscreen_dial_button)) {
            mDialButton.setOnClickListener(this);
            mDialButton.setOnLongClickListener(this);
        } else {
            mDialButton.setVisibility(View.GONE); // It's VISIBLE by default
            mDialButton = null;
        }

        mDelete = fragmentView.findViewById(R.id.deleteButton);
        /**
         * Change Feature by mediatek.inc end
         */
        if (mDelete != null) {
            mDelete.setOnClickListener(this);
            mDelete.setOnLongClickListener(this);
            /** M: New Feature Phone Landscape UI @{ */
            if (!ISTABLET_LAND) {
                /** @ } */
                mDelete.setVisibility(View.GONE);
                /** M: New Feature Phone Landscape UI @{ */
            }
            /** @ } */
        }else{
            LogUtils.w(TAG,"#onCreateView(),mDelete is null.");
        }

        mSpacer = fragmentView.findViewById(R.id.spacer);
      ///M: change for  CR ALPS01260118,improve the Dialer call performance
        /*Google original code:
         * mSpacer.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (isDigitsEmpty()) {
                    hideAndClearDialpad();
                    return true;
                }
                return false;
            }
        });*/

        mDialpad = fragmentView.findViewById(R.id.dialpad);  // This is null in landscape mode.

        // In landscape we put the keyboard in phone mode.
        if (null == mDialpad) {
            mDigits.setInputType(android.text.InputType.TYPE_CLASS_PHONE);
        } else {
            mDigits.setCursorVisible(false);
        }

        // Set up the "dialpad chooser" UI; see showDialpadChooser().
        mDialpadChooser = (ListView) fragmentView.findViewById(R.id.dialpadChooser);
        mDialpadChooser.setOnItemClickListener(this);

        return fragmentView;
    }

    @Override
    public void onStart() {
        super.onStart();

        final Activity activity = getActivity();

        try {
            ((OnDialpadFragmentStartedListener) activity).onDialpadFragmentStarted();
        } catch (ClassCastException e) {
            throw new ClassCastException(activity.toString()
                    + " must implement OnDialpadFragmentStartedListener");
        }

        final View overflowButton = getView().findViewById(R.id.overflow_menu_on_dialpad);
        overflowButton.setOnClickListener(this);
    }

    private boolean isLayoutReady() {
        return mDigits != null;
    }

    public EditText getDigitsWidget() {
        return mDigits;
    }

    /**
     * @return true when {@link #mDigits} is actually filled by the Intent.
     */
    private boolean fillDigitsIfNecessary(Intent intent) {
        // Only fills digits from an intent if it is a new intent.
        // Otherwise falls back to the previously used number.
        if (!mFirstLaunch && !mStartedFromNewIntent) {
            return false;
        }

        final String action = intent.getAction();
        if (Intent.ACTION_DIAL.equals(action) || Intent.ACTION_VIEW.equals(action)) {
            Uri uri = intent.getData();
            if (uri != null) {
                if (CallUtil.SCHEME_TEL.equals(uri.getScheme())) {
                    // Put the requested number into the input area
                    String data = uri.getSchemeSpecificPart();
                    // Remember it is filled via Intent.
                    mDigitsFilledByIntent = true;
                    final String converted = PhoneNumberUtils.convertKeypadLettersToDigits(
                            PhoneNumberUtils.replaceUnicodeDigits(data));
                    setFormattedDigits(converted, null);
                    // clear the data
                    intent.setData(null);
                    return true;
                } else if ("voicemail".equals(uri.getScheme())) {
                    String data = uri.getSchemeSpecificPart();
                    setFormattedDigits(data, null);
                    // clear the data
                    intent.setData(null);
                    if (data != null && !data.isEmpty()) {
                        mDigits.setVisibility(View.VISIBLE);
                    }
                    return true;
                } else {
                    String type = intent.getType();
                    if (People.CONTENT_ITEM_TYPE.equals(type)
                            || Phones.CONTENT_ITEM_TYPE.equals(type)) {
                        // Query the phone number
                        Cursor c = getActivity().getContentResolver().query(intent.getData(),
                                new String[] {PhonesColumns.NUMBER, PhonesColumns.NUMBER_KEY},
                                null, null, null);
                        if (c != null) {
                            try {
                                if (c.moveToFirst()) {
                                    // Remember it is filled via Intent.
                                    mDigitsFilledByIntent = true;
                                    // Put the number into the input area
                                    setFormattedDigits(c.getString(0), c.getString(1));
                                    // clear the data
                                    intent.setData(null);
                                    return true;
                                }
                            } finally {
                                c.close();
                            }
                        }
                    }
                }
            }
        }

        return false;
    }

    /**
     * Determines whether an add call operation is requested.
     *
     * @param intent The intent.
     * @return {@literal true} if add call operation was requested.  {@literal false} otherwise.
     */
    private static boolean isAddCallMode(Intent intent) {
        final String action = intent.getAction();
        if (Intent.ACTION_DIAL.equals(action) || Intent.ACTION_VIEW.equals(action)) {
            // see if we are "adding a call" from the InCallScreen; false by default.
            return intent.getBooleanExtra(ADD_CALL_MODE_KEY, false);
        } else {
            return false;
        }
    }

    /**
     * Checks the given Intent and changes dialpad's UI state. For example, if the Intent requires
     * the screen to enter "Add Call" mode, this method will show correct UI for the mode.
     */
    private void configureScreenFromIntent(Activity parent) {
        // If we were not invoked with a DIAL intent,
        if (!(parent instanceof DialtactsActivity)) {
            setStartedFromNewIntent(false);
            return;
        }
        // See if we were invoked with a DIAL intent. If we were, fill in the appropriate
        // digits in the dialer field.
        Intent intent = parent.getIntent();

        if (!isLayoutReady()) {
            // This happens typically when parent's Activity#onNewIntent() is called while
            // Fragment#onCreateView() isn't called yet, and thus we cannot configure Views at
            // this point. onViewCreate() should call this method after preparing layouts, so
            // just ignore this call now.
            Log.i(TAG,
                    "Screen configuration is requested before onCreateView() is called. Ignored");
            return;
        }

        boolean needToShowDialpadChooser = false;

        // Be sure *not* to show the dialpad chooser if this is an
        // explicit "Add call" action, though.
        final boolean isAddCallMode = isAddCallMode(intent);
        if (!isAddCallMode) {

            // Don't show the chooser when called via onNewIntent() and phone number is present.
            // i.e. User clicks a telephone link from gmail for example.
            // In this case, we want to show the dialpad with the phone number.
            final boolean digitsFilled = fillDigitsIfNecessary(intent);
            if (!(mStartedFromNewIntent && digitsFilled)) {

                final String action = intent.getAction();
                if (Intent.ACTION_DIAL.equals(action) || Intent.ACTION_VIEW.equals(action)
                        || Intent.ACTION_MAIN.equals(action)) {
                    // If there's already an active call, bring up an intermediate UI to
                    // make the user confirm what they really want to do.
                    if (phoneIsInUse()) {
                        needToShowDialpadChooser = true;
                    }
                }

            }
        }
        showDialpadChooser(needToShowDialpadChooser);
        setStartedFromNewIntent(false);
    }

    public void setStartedFromNewIntent(boolean value) {
        mStartedFromNewIntent = value;
    }

    /**
     * Sets formatted digits to digits field.
     */
    private void setFormattedDigits(String data, String normalizedNumber) {
        // strip the non-dialable numbers out of the data string.
        String dialString = PhoneNumberUtils.extractNetworkPortion(data);
        dialString =
                PhoneNumberUtils.formatNumber(dialString, normalizedNumber, mCurrentCountryIso);
        if (!TextUtils.isEmpty(dialString)) {
            Editable digits = mDigits.getText();
            digits.replace(0, digits.length(), dialString);
            // for some reason this isn't getting called in the digits.replace call above..
            // but in any case, this will make sure the background drawable looks right
            afterTextChanged(digits);
            mAutoScaleTextSizeWatcher.trigger(true);
        }
    }

    private void setupKeypad(View fragmentView) {
        final int[] buttonIds = new int[] {R.id.zero, R.id.one, R.id.two, R.id.three, R.id.four,
                R.id.five, R.id.six, R.id.seven, R.id.eight, R.id.nine, R.id.star, R.id.pound};

        final int[] numberIds = new int[] {R.string.dialpad_0_number, R.string.dialpad_1_number,
                R.string.dialpad_2_number, R.string.dialpad_3_number, R.string.dialpad_4_number,
                R.string.dialpad_5_number, R.string.dialpad_6_number, R.string.dialpad_7_number,
                R.string.dialpad_8_number, R.string.dialpad_9_number, R.string.dialpad_star_number,
                R.string.dialpad_pound_number};

        final int[] letterIds = new int[] {R.string.dialpad_0_letters, R.string.dialpad_1_letters,
                R.string.dialpad_2_letters, R.string.dialpad_3_letters, R.string.dialpad_4_letters,
                R.string.dialpad_5_letters, R.string.dialpad_6_letters, R.string.dialpad_7_letters,
                R.string.dialpad_8_letters, R.string.dialpad_9_letters,
                R.string.dialpad_star_letters, R.string.dialpad_pound_letters};

        final Resources resources = getResources();

        DialpadKeyButton dialpadKey;
        TextView numberView;
        TextView lettersView;

        for (int i = 0; i < buttonIds.length; i++) {
            dialpadKey = (DialpadKeyButton) fragmentView.findViewById(buttonIds[i]);
            dialpadKey.setLayoutParams(new TableRow.LayoutParams(
                    TableRow.LayoutParams.MATCH_PARENT, TableRow.LayoutParams.MATCH_PARENT));
            dialpadKey.setOnPressedListener(this);
            numberView = (TextView) dialpadKey.findViewById(R.id.dialpad_key_number);
            lettersView = (TextView) dialpadKey.findViewById(R.id.dialpad_key_letters);
            final String numberString = resources.getString(numberIds[i]);
            numberView.setText(numberString);
            dialpadKey.setContentDescription(numberString);
            if (lettersView != null) {
                lettersView.setText(resources.getString(letterIds[i]));
                if (buttonIds[i] == R.id.zero) {
                    lettersView.setTextSize(TypedValue.COMPLEX_UNIT_PX, resources.getDimension(
                            R.dimen.dialpad_key_plus_size));
                }
            }
            fragmentView.findViewById(buttonIds[i]).setOnLongClickListener(this);
        }

        /** original code:
        // Long-pressing one button will initiate Voicemail.
        fragmentView.findViewById(R.id.one).setOnLongClickListener(this);

        // Long-pressing zero button will enter '+' instead.
        fragmentView.findViewById(R.id.zero).setOnLongClickListener(this);
        */

    }

    @Override
    public void onResume() {
        super.onResume();

        final DialtactsActivity activity = (DialtactsActivity) getActivity();
        mDialpadQueryListener = activity;

        final StopWatch stopWatch = StopWatch.start("Dialpad.onResume");
        if (mDelete == null) {
            mDelete = fragmentView.findViewById(R.id.deleteButton);
            if (mDelete != null) {
                mDelete.setOnClickListener(this);
                mDelete.setOnLongClickListener(this);
            }
        }

        /**
         * add by mediatek .inc
         * description : start query SIM association
         */
        SimAssociateHandler.getInstance(DialerApplication.getInstance()).load();
        /**
         * add by mediatek end
         */
        // Query the last dialed number. Do it first because hitting
        // the DB is 'slow'. This call is asynchronous.
        queryLastOutgoingCall();

        stopWatch.lap("qloc");

        final ContentResolver contentResolver = activity.getContentResolver();

        // retrieve the DTMF tone play back setting.
        mDTMFToneEnabled = Settings.System.getInt(contentResolver,
                Settings.System.DTMF_TONE_WHEN_DIALING, 1) == 1;

        stopWatch.lap("dtwd");

        // Retrieve the haptic feedback setting.
        mHaptic.checkSystemSetting();

        stopWatch.lap("hptc");

        // if the mToneGenerator creation fails, just continue without it.  It is
        // a local audio signal, and is not as important as the dtmf tone itself.
        synchronized (mToneGeneratorLock) {
            if (mToneGenerator == null) {
                try {
                    mToneGenerator = new ToneGenerator(DIAL_TONE_STREAM_TYPE, TONE_RELATIVE_VOLUME);
                } catch (RuntimeException e) {
                    Log.w(TAG, "Exception caught while creating local tone generator: " + e);
                    mToneGenerator = null;
                }
            }
        }
        stopWatch.lap("tg");

        mPressedDialpadKeys.clear();

        configureScreenFromIntent(getActivity());

        stopWatch.lap("fdin");

        // While we're in the foreground, listen for phone state changes,
        // purely so that we can take down the "dialpad chooser" if the
        // phone becomes idle while the chooser UI is visible.
        getTelephonyManager().listen(mPhoneStateListener, PhoneStateListener.LISTEN_CALL_STATE);

        stopWatch.lap("tm");

        // Potentially show hint text in the mDigits field when the user
        // hasn't typed any digits yet.  (If there's already an active call,
        // this hint text will remind the user that he's about to add a new
        // call.)
        //
        // TODO: consider adding better UI for the case where *both* lines
        // are currently in use.  (Right now we let the user try to add
        // another call, but that call is guaranteed to fail.  Perhaps the
        // entire dialer UI should be disabled instead.)
        if (phoneIsInUse()) {
            final SpannableString hint = new SpannableString(
                    getActivity().getString(R.string.dialerDialpadHintText));
            hint.setSpan(new RelativeSizeSpan(0.8f), 0, hint.length(), 0);
            mDigits.setHint(hint);
        } else {
            // Common case; no hint necessary.
            mDigits.setHint(null);

            // Also, a sanity-check: the "dialpad chooser" UI should NEVER
            // be visible if the phone is idle!
            showDialpadChooser(false);
        }

        mFirstLaunch = false;

        stopWatch.lap("hnt");

        updateDialAndDeleteButtonEnabledState();

        stopWatch.lap("bes");

        stopWatch.stopAndLog(TAG, 50);
    }

    @Override
    public void onPause() {
        super.onPause();

        // Stop listening for phone state changes.
        getTelephonyManager().listen(mPhoneStateListener, PhoneStateListener.LISTEN_NONE);

        // Make sure we don't leave this activity with a tone still playing.
        stopTone();
        mPressedDialpadKeys.clear();

        synchronized (mToneGeneratorLock) {
            if (mToneGenerator != null) {
                mToneGenerator.release();
                mToneGenerator = null;
            }
        }
        // TODO: I wonder if we should not check if the AsyncTask that
        // lookup the last dialed number has completed.
        mLastNumberDialed = EMPTY_NUMBER;  // Since we are going to query again, free stale number.

        SpecialCharSequenceMgr.cleanup();
    }

    @Override
    public void onStop() {
        super.onStop();

        if (mClearDigitsOnStop) {
            mClearDigitsOnStop = false;
            /// M: ALPS01234085.@{
            // modify for "can not perform this action after onSaveInstanceState" JE issue.
            // original code:
            /**
            clearDialpad();
            */
            /// @}
        }

        /// M: for ALPS01257332 @{
        // dismiss all dialogs from CallOptionHandler when activity onStop.
        if (mCallOptionHandler != null) {
            mCallOptionHandler.dismissDialogs();
        }
        /// @}
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(PREF_DIGITS_FILLED_BY_INTENT, mDigitsFilledByIntent);
    }

     @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);

        inflater.inflate(R.menu.dialpad_options, menu);
        LogUtils.d(TAG, "---onCreateOptionsMenu---");

//        if (ISTABLET_LAND) {
//            inflater.inflate(R.menu.dialpad_options, menu);
//        } else {
//            // Landscape dialer uses the real actionbar menu, whereas portrait
//            // uses a fake one
//            // that is created using constructPopupMenu()
//            if (OrientationUtil.isLandscape(this.getActivity())
//                    || ViewConfiguration.get(getActivity()).hasPermanentMenuKey() && isLayoutReady()
//                    && mDialpadChooser != null) {
//                LogUtils.d("mtk54458", "---onCreateOptionsMenu---");
//
//                inflater.inflate(R.menu.dialpad_options, menu);
//            }
//        }
        ExtensionManager.getInstance().getDialPadExtension().onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public void onPrepareOptionsMenu(Menu menu) {
        // Hardware menu key should be available and Views should already be
        // ready.

        setupMenuItems(menu);
        setupPopupMenuItems(menu);
        LogUtils.d(TAG, "---onPrepareOptionsMenu---");

//        if (ISTABLET_LAND) {
//            setupMenuItems(menu);
//            setupPopupMenuItems(menu);
//        } else {
//            if (OrientationUtil.isLandscape(this.getActivity())
//                    || ViewConfiguration.get(getActivity()).hasPermanentMenuKey() && isLayoutReady()
//                    && mDialpadChooser != null) {
//                setupMenuItems(menu);
//            }
//        }
        ExtensionManager.getInstance().getDialPadExtension().onPrepareOptionsMenu(menu);
    }

    private void setupMenuItems(Menu menu) {
        final MenuItem callSettingsMenuItem = menu.findItem(R.id.menu_call_settings_dialpad);
        final MenuItem addToContactMenuItem = menu.findItem(R.id.menu_add_contacts);
        final MenuItem twoSecPauseMenuItem = menu.findItem(R.id.menu_2s_pause);
        final MenuItem waitMenuItem = menu.findItem(R.id.menu_add_wait);
        final MenuItem ipDialMenuItem = menu.findItem(R.id.menu_ip_dial);
        final MenuItem videoCallMenuItem = menu.findItem(R.id.menu_video_call);
        final MenuItem sendMessageMenuItem = menu.findItem(R.id.menu_send_message);

        // Check if all the menu items are inflated correctly. As a shortcut, we assume all menu
        // items are ready if the first item is non-null.
        if (callSettingsMenuItem == null) {
            return;
        }

        final Activity activity = getActivity();
        if (activity != null && ViewConfiguration.get(activity).hasPermanentMenuKey()) {
            // Call settings should be available via its parent Activity.
            callSettingsMenuItem.setVisible(false);
        } else {
            callSettingsMenuItem.setVisible(true);
            callSettingsMenuItem.setIntent(DialtactsActivity.getCallSettingsIntent());
        }

        // We show "add to contacts", "2sec pause", and "add wait" menus only when the user is
        // seeing usual dialpads and has typed at least one digit.
        // We never show a menu if the "choose dialpad" UI is up.
        videoCallMenuItem.setVisible(false);
        addToContactMenuItem.setVisible(false);
        if (isDialpadChooserVisible() || isDigitsEmpty()) {
            twoSecPauseMenuItem.setVisible(false);
            waitMenuItem.setVisible(false);
            ipDialMenuItem.setVisible(false);
            sendMessageMenuItem.setVisible(false);
        } else {
            final CharSequence digits = mDigits.getText();

            /// M: ALPS01255783.@{
            // modify for kk can not show vt-call button issue.
            if (FeatureOption.MTK_VT3G324M_SUPPORT) {
                videoCallMenuItem.setVisible(true);
            }
            // to follow new dialpad style,always show "add to contact" item.no need to releate with VT CALL.
            addToContactMenuItem.setIntent(getAddToContactIntent(digits, isSipNumber(digits)));
            addToContactMenuItem.setVisible(true);
            /// @}

            if (SIMInfoWrapper.getDefault().getInsertedSimCount() == 0) {
                ipDialMenuItem.setVisible(false);
            } else {
                ipDialMenuItem.setVisible(true);
            }
            sendMessageMenuItem.setVisible(true);
            // Check out whether to show Pause & Wait option menu items
            int selectionStart;
            int selectionEnd;
            String strDigits = digits.toString();

            selectionStart = mDigits.getSelectionStart();
            selectionEnd = mDigits.getSelectionEnd();

            if (selectionStart != -1) {
                if (selectionStart > selectionEnd) {
                    // swap it as we want start to be less then end
                    int tmp = selectionStart;
                    selectionStart = selectionEnd;
                    selectionEnd = tmp;
                }

                if (selectionStart != 0) {
                    // Pause can be visible if cursor is not in the begining
                    twoSecPauseMenuItem.setVisible(true);

                    // For Wait to be visible set of condition to meet
                    waitMenuItem.setVisible(showWait(selectionStart, selectionEnd, strDigits));
                } else {
                    // cursor in the beginning both pause and wait to be invisible
                    twoSecPauseMenuItem.setVisible(false);
                    waitMenuItem.setVisible(false);
                }
            } else {
                twoSecPauseMenuItem.setVisible(true);

                // cursor is not selected so assume new digit is added to the end
                int strLength = strDigits.length();
                waitMenuItem.setVisible(showWait(strLength, strLength, strDigits));
            }
        }
    }

    /// M: change for SIP number can be added to SIM card problem. CR ALPS01092714
    private static Intent getAddToContactIntent(CharSequence digits, boolean isSipNumber) {
        final Intent intent = new Intent(Intent.ACTION_INSERT_OR_EDIT);
        if (isSipNumber) {
            intent.putExtra(ContactsContract.Intents.Insert.SIP_ADDRESS, digits.toString());
        } else {
            intent.putExtra(Insert.PHONE, digits);
        }
        // add by mediatek 
        intent.putExtra("fromWhere", "CALL_LOG");
        intent.setType(People.CONTENT_ITEM_TYPE);
        return intent;
    }

    private void keyPressed(int keyCode) {
        if (getView().getTranslationY() != 0) {
            return;
        }
        switch (keyCode) {
            case KeyEvent.KEYCODE_1:
                playTone(ToneGenerator.TONE_DTMF_1, TONE_LENGTH_INFINITE);
                break;
            case KeyEvent.KEYCODE_2:
                playTone(ToneGenerator.TONE_DTMF_2, TONE_LENGTH_INFINITE);
                break;
            case KeyEvent.KEYCODE_3:
                playTone(ToneGenerator.TONE_DTMF_3, TONE_LENGTH_INFINITE);
                break;
            case KeyEvent.KEYCODE_4:
                playTone(ToneGenerator.TONE_DTMF_4, TONE_LENGTH_INFINITE);
                break;
            case KeyEvent.KEYCODE_5:
                playTone(ToneGenerator.TONE_DTMF_5, TONE_LENGTH_INFINITE);
                break;
            case KeyEvent.KEYCODE_6:
                playTone(ToneGenerator.TONE_DTMF_6, TONE_LENGTH_INFINITE);
                break;
            case KeyEvent.KEYCODE_7:
                playTone(ToneGenerator.TONE_DTMF_7, TONE_LENGTH_INFINITE);
                break;
            case KeyEvent.KEYCODE_8:
                playTone(ToneGenerator.TONE_DTMF_8, TONE_LENGTH_INFINITE);
                break;
            case KeyEvent.KEYCODE_9:
                playTone(ToneGenerator.TONE_DTMF_9, TONE_LENGTH_INFINITE);
                break;
            case KeyEvent.KEYCODE_0:
                playTone(ToneGenerator.TONE_DTMF_0, TONE_LENGTH_INFINITE);
                break;
            case KeyEvent.KEYCODE_POUND:
                playTone(ToneGenerator.TONE_DTMF_P, TONE_LENGTH_INFINITE);
                break;
            case KeyEvent.KEYCODE_STAR:
                playTone(ToneGenerator.TONE_DTMF_S, TONE_LENGTH_INFINITE);
                break;
            default:
                break;
        }

        mHaptic.vibrate();
        KeyEvent event = new KeyEvent(KeyEvent.ACTION_DOWN, keyCode);
        mDigits.onKeyDown(keyCode, event);

        // If the cursor is at the end of the text we hide it.
        final int length = mDigits.length();
        if (length == mDigits.getSelectionStart() && length == mDigits.getSelectionEnd()) {
            mDigits.setCursorVisible(false);
        }
    }

    @Override
    public boolean onKey(View view, int keyCode, KeyEvent event) {
        switch (view.getId()) {
            case R.id.digits:
                if (keyCode == KeyEvent.KEYCODE_ENTER) {
                    dialButtonPressed();
                    return true;
                }
                break;
        }
        return false;
    }

    /**
     * When a key is pressed, we start playing DTMF tone, do vibration, and enter the digit
     * immediately. When a key is released, we stop the tone. Note that the "key press" event will
     * be delivered by the system with certain amount of delay, it won't be synced with user's
     * actual "touch-down" behavior.
     */
    @Override
    public void onPressed(View view, boolean pressed) {
        if (DEBUG) Log.d(TAG, "onPressed(). view: " + view + ", pressed: " + pressed);
        if (pressed) {
            switch (view.getId()) {
                case R.id.one: {
                    keyPressed(KeyEvent.KEYCODE_1);
                    break;
                }
                case R.id.two: {
                    keyPressed(KeyEvent.KEYCODE_2);
                    break;
                }
                case R.id.three: {
                    keyPressed(KeyEvent.KEYCODE_3);
                    break;
                }
                case R.id.four: {
                    keyPressed(KeyEvent.KEYCODE_4);
                    break;
                }
                case R.id.five: {
                    keyPressed(KeyEvent.KEYCODE_5);
                    break;
                }
                case R.id.six: {
                    keyPressed(KeyEvent.KEYCODE_6);
                    break;
                }
                case R.id.seven: {
                    keyPressed(KeyEvent.KEYCODE_7);
                    break;
                }
                case R.id.eight: {
                    keyPressed(KeyEvent.KEYCODE_8);
                    break;
                }
                case R.id.nine: {
                    keyPressed(KeyEvent.KEYCODE_9);
                    break;
                }
                case R.id.zero: {
                    keyPressed(KeyEvent.KEYCODE_0);
                    break;
                }
                case R.id.pound: {
                    keyPressed(KeyEvent.KEYCODE_POUND);
                    break;
                }
                case R.id.star: {
                    keyPressed(KeyEvent.KEYCODE_STAR);
                    break;
                }
                default: {
                    Log.wtf(TAG, "Unexpected onTouch(ACTION_DOWN) event from: " + view);
                    break;
                }
            }
            mPressedDialpadKeys.add(view);
        } else {
            view.jumpDrawablesToCurrentState();
            mPressedDialpadKeys.remove(view);
            if (mPressedDialpadKeys.isEmpty()) {
                stopTone();
            }
        }
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.overflow_menu_on_dialpad: {
                if (null == popup) {
                    popup = constructPopupMenu(view);
                    if (null != popup) {
                        popup.show();
                    }
                } else {
                    Log.i(TAG, "dismiss mPopMenuView and reconstruct a new one!");
                    popup.dismiss();
                    popup = constructPopupMenu(view);
                    if (null != popup) {
                        popup.show();
                    }
                }
                break;
            }
            case R.id.deleteButton: {
                keyPressed(KeyEvent.KEYCODE_DEL);
                /// M: ALPS01285368 Size Of number not changed when click delete button.
                mAutoScaleTextSizeWatcher.trigger(true);
                return;
            }
            case R.id.dialButton: {
                mHaptic.vibrate();  // Vibrate here too, just like we do for the regular keys
                dialButtonPressed();
                return;
            }
            case R.id.digits: {
                if (!isDigitsEmpty()) {
                    mDigits.setCursorVisible(true);
                }
                return;
            }
            default: {
                Log.wtf(TAG, "Unexpected onClick() event from: " + view);
                return;
            }
        }
    }

    public PopupMenu constructPopupMenu(View anchorView) {
        final Context context = getActivity();
        if (context == null) {
            return null;
        }
        final PopupMenu popupMenu = new PopupMenu(context, anchorView);
        final Menu menu = popupMenu.getMenu();
        popupMenu.inflate(R.menu.dialpad_options);
        popupMenu.setOnMenuItemClickListener(this);
        setupMenuItems(menu);
        setupPopupMenuItems(menu);
        ExtensionManager.getInstance().getDialPadExtension().constructPopupMenu(popupMenu, anchorView, menu);
        return popupMenu;
    }

    @Override
    public boolean onLongClick(View view) {
        /**
         * Change Feature by mediatek .inc
         */
        boolean handled = onLongClickInternal(view);
        if (handled) {
            return handled;
        }
        /**
         * Change Feature by mediatek .inc end
         */
        final Editable digits = mDigits.getText();
        final int id = view.getId();
        switch (id) {
            case R.id.deleteButton: {
                digits.clear();
                // TODO: The framework forgets to clear the pressed
                // status of disabled button. Until this is fixed,
                // clear manually the pressed status. b/2133127
                mDelete.setPressed(false);
                return true;
            }
            case R.id.one: {
                /// M: For ALPS01266214 @{
                // for long press "1" button will popup set voicemail dialog when no sim card.
                if (SIMInfoWrapper.getDefault().getInsertedSimCount() == 0) {
                    LogUtils.d(TAG, "[onLongClick] insert sim count is 0.");
                    return true;
                }
                /// @}
                // '1' may be already entered since we rely on onTouch() event for numeric buttons.
                // Just for safety we also check if the digits field is empty or not.
                if (isDigitsEmpty() || TextUtils.equals(mDigits.getText(), "1")) {
                    /// M: For ALPS01237354.@{
                    // for long press "1" button,there are no "select sim" dialog popup issue.
                    if (isVoicemailAvailableProxy()) {
                        // We'll try to initiate voicemail and thus we want to remove irrelevant string.
                        removePreviousDigitIfPossible();
                        callVoicemail();
                    } else if (getActivity() != null) {
                        if (!SlotUtils.isGeminiEnabled()) {
                            // We'll try to initiate voicemail and thus we want to remove irrelevant string.
                            removePreviousDigitIfPossible();

                            // Voicemail is unavailable maybe because Airplane mode is turned on.
                            // Check the current status and show the most appropriate error message.
                            final boolean isAirplaneModeOn =
                                    Settings.Global.getInt(getActivity().getContentResolver(),
                                    Settings.Global.AIRPLANE_MODE_ON, 0) != 0;
                            if (isAirplaneModeOn) {
                                DialogFragment dialogFragment = ErrorDialogFragment.newInstance(
                                            R.string.dialog_voicemail_airplane_mode_message);
                                dialogFragment.show(getFragmentManager(),
                                            "voicemail_request_during_airplane_mode");
                            } else {
                                DialogFragment dialogFragment = ErrorDialogFragment.newInstance(
                                            R.string.dialog_voicemail_not_ready_message);
                                dialogFragment.show(getFragmentManager(), "voicemail_not_ready");
                            }
                        }
                    }
                    ///@}
                    return true;
                }
                return false;
            }
            case R.id.zero: {
                // Remove tentative input ('0') done by onTouch().
                removePreviousDigitIfPossible();
                keyPressed(KeyEvent.KEYCODE_PLUS);

                // Stop tone immediately
                stopTone();
                mPressedDialpadKeys.remove(view);

                return true;
            }
            case R.id.digits: {
                // Right now EditText does not show the "paste" option when cursor is not visible.
                // To show that, make the cursor visible, and return false, letting the EditText
                // show the option by itself.
                mDigits.setCursorVisible(true);
                return false;
            }
            case R.id.dialButton: {
                if (isDigitsEmpty()) {
                    handleDialButtonClickWithEmptyDigits();
                    // This event should be consumed so that onClick() won't do the exactly same
                    // thing.
                    return true;
                } else {
                    return false;
                }
            }
        }
        return false;
    }

    /**
     * Remove the digit just before the current position. This can be used if we want to replace
     * the previous digit or cancel previously entered character.
     */
    private void removePreviousDigitIfPossible() {
        final Editable editable = mDigits.getText();
        final int currentPosition = mDigits.getSelectionStart();
        if (currentPosition > 0) {
            mDigits.setSelection(currentPosition);
            mDigits.getText().delete(currentPosition - 1, currentPosition);
        }
    }

    public void callVoicemail() {
        ///M: modify for CR ALPS00995843 @{
        doCallOptionHandle(getVoicemailIntent());
        mClearDigitsOnStop = true;
        ///@}
    }

    private void hideAndClearDialpad() {
        ((DialtactsActivity) getActivity()).hideDialpadFragment(false, true);
    }

    public static class ErrorDialogFragment extends DialogFragment {
        private int mTitleResId;
        private int mMessageResId;

        private static final String ARG_TITLE_RES_ID = "argTitleResId";
        private static final String ARG_MESSAGE_RES_ID = "argMessageResId";

        public static ErrorDialogFragment newInstance(int messageResId) {
            return newInstance(0, messageResId);
        }

        public static ErrorDialogFragment newInstance(int titleResId, int messageResId) {
            final ErrorDialogFragment fragment = new ErrorDialogFragment();
            final Bundle args = new Bundle();
            args.putInt(ARG_TITLE_RES_ID, titleResId);
            args.putInt(ARG_MESSAGE_RES_ID, messageResId);
            fragment.setArguments(args);
            return fragment;
        }

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            mTitleResId = getArguments().getInt(ARG_TITLE_RES_ID);
            mMessageResId = getArguments().getInt(ARG_MESSAGE_RES_ID);
        }

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
            if (mTitleResId != 0) {
                builder.setTitle(mTitleResId);
            }
            if (mMessageResId != 0) {
                builder.setMessage(mMessageResId);
            }
            builder.setPositiveButton(android.R.string.ok,
                    new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                dismiss();
                            }
                    });
            return builder.create();
        }
    }

    /**
     * In most cases, when the dial button is pressed, there is a
     * number in digits area. Pack it in the intent, start the
     * outgoing call broadcast as a separate task and finish this
     * activity.
     *
     * When there is no digit and the phone is CDMA and off hook,
     * we're sending a blank flash for CDMA. CDMA networks use Flash
     * messages when special processing needs to be done, mainly for
     * 3-way or call waiting scenarios. Presumably, here we're in a
     * special 3-way scenario where the network needs a blank flash
     * before being able to add the new participant.  (This is not the
     * case with all 3-way calls, just certain CDMA infrastructures.)
     *
     * Otherwise, there is no digit, display the last dialed
     * number. Don't finish since the user may want to edit it. The
     * user needs to press the dial button again, to dial it (general
     * case described above).
     */
    public void dialButtonPressed() {
        if (isDigitsEmpty()) { // No number entered.
            handleDialButtonClickWithEmptyDigits();
        } else {
            final String number = mDigits.getText().toString();

            // "persist.radio.otaspdial" is a temporary hack needed for one carrier's automated
            // test equipment.
            // TODO: clean it up.
            if (number != null
                    && !TextUtils.isEmpty(mProhibitedPhoneNumberRegexp)
                    && number.matches(mProhibitedPhoneNumberRegexp)
                    && (SystemProperties.getInt("persist.radio.otaspdial", 0) != 1)) {
                Log.i(TAG, "The phone number is prohibited explicitly by a rule.");
                if (getActivity() != null) {
                    DialogFragment dialogFragment = ErrorDialogFragment.newInstance(
                            R.string.dialog_phone_call_prohibited_message);
                    dialogFragment.show(getFragmentManager(), "phone_prohibited_dialog");
                }

                // Clear the digits just in case.
                mDigits.getText().clear();
            } else {
                final Intent intent = CallUtil.getCallIntent(number,
                        (getActivity() instanceof DialtactsActivity ?
                                ((DialtactsActivity) getActivity()).getCallOrigin() : null));
              /// M: change for  CR ALPS01260118,improve the Dialer call performance
              /// Google original code: @{
                // startActivity(intent);
              hideAndClearDialpad();
              /// @}
                doCallOptionHandle(intent);
                mClearDigitsOnStop = true;
                mIsNeedClearDialpad = true;
            }
        }
    }

    public void clearDialpad() {
        mDigits.getText().clear();
    }

    private String getCallOrigin() {
        return (getActivity() instanceof DialtactsActivity) ?
                ((DialtactsActivity) getActivity()).getCallOrigin() : null;
    }

    public void handleDialButtonClickWithEmptyDigits() {
        if (phoneIsCdma() && phoneIsOffhook()) {
            // This is really CDMA specific. On GSM is it possible
            // to be off hook and wanted to add a 3rd party using
            // the redial feature.
            startActivity(newFlashIntent());
        } else {
            if (!TextUtils.isEmpty(mLastNumberDialed)) {
                // Recall the last number dialed.
                mDigits.setText(mLastNumberDialed);

                // ...and move the cursor to the end of the digits string,
                // so you'll be able to delete digits using the Delete
                // button (just as if you had typed the number manually.)
                //
                // Note we use mDigits.getText().length() here, not
                // mLastNumberDialed.length(), since the EditText widget now
                // contains a *formatted* version of mLastNumberDialed (due to
                // mTextWatcher) and its length may have changed.
                mDigits.setSelection(mDigits.getText().length());
            } else {
                // There's no "last number dialed" or the
                // background query is still running. There's
                // nothing useful for the Dial button to do in
                // this case.  Note: with a soft dial button, this
                // can never happens since the dial button is
                // disabled under these conditons.
                playTone(ToneGenerator.TONE_PROP_NACK);
            }
        }
    }

    /**
     * Plays the specified tone for TONE_LENGTH_MS milliseconds.
     */
    private void playTone(int tone) {
        playTone(tone, TONE_LENGTH_MS);
    }

    /**
     * Play the specified tone for the specified milliseconds
     *
     * The tone is played locally, using the audio stream for phone calls.
     * Tones are played only if the "Audible touch tones" user preference
     * is checked, and are NOT played if the device is in silent mode.
     *
     * The tone length can be -1, meaning "keep playing the tone." If the caller does so, it should
     * call stopTone() afterward.
     *
     * @param tone a tone code from {@link ToneGenerator}
     * @param durationMs tone length.
     */
    private void playTone(int tone, int durationMs) {
        // if local tone playback is disabled, just return.
        if (!mDTMFToneEnabled) {
            return;
        }

        // Also do nothing if the phone is in silent mode.
        // We need to re-check the ringer mode for *every* playTone()
        // call, rather than keeping a local flag that's updated in
        // onResume(), since it's possible to toggle silent mode without
        // leaving the current activity (via the ENDCALL-longpress menu.)
        AudioManager audioManager =
                (AudioManager) getActivity().getSystemService(Context.AUDIO_SERVICE);
        int ringerMode = audioManager.getRingerMode();
        if ((ringerMode == AudioManager.RINGER_MODE_SILENT)
            || (ringerMode == AudioManager.RINGER_MODE_VIBRATE)) {
            return;
        }

        synchronized (mToneGeneratorLock) {
            if (mToneGenerator == null) {
                Log.w(TAG, "playTone: mToneGenerator == null, tone: " + tone);
                return;
            }

            // Start the new tone (will stop any playing tone)
            mToneGenerator.startTone(tone, durationMs);
        }
    }

    /**
     * Stop the tone if it is played.
     */
    private void stopTone() {
        // if local tone playback is disabled, just return.
        if (!mDTMFToneEnabled) {
            return;
        }
        synchronized (mToneGeneratorLock) {
            if (mToneGenerator == null) {
                Log.w(TAG, "stopTone: mToneGenerator == null");
                return;
            }
            mToneGenerator.stopTone();
        }
    }

    /**
     * Brings up the "dialpad chooser" UI in place of the usual Dialer
     * elements (the textfield/button and the dialpad underneath).
     *
     * We show this UI if the user brings up the Dialer while a call is
     * already in progress, since there's a good chance we got here
     * accidentally (and the user really wanted the in-call dialpad instead).
     * So in this situation we display an intermediate UI that lets the user
     * explicitly choose between the in-call dialpad ("Use touch tone
     * keypad") and the regular Dialer ("Add call").  (Or, the option "Return
     * to call in progress" just goes back to the in-call UI with no dialpad
     * at all.)
     *
     * @param enabled If true, show the "dialpad chooser" instead
     *                of the regular Dialer UI
     */
    private void showDialpadChooser(boolean enabled) {
        // Check if onCreateView() is already called by checking one of View objects.
        if (!isLayoutReady()) {
            return;
        }

        if (enabled) {
            // Log.i(TAG, "Showing dialpad chooser!");
            if (mDigitsContainer != null) {
                mDigitsContainer.setVisibility(View.GONE);
            } else {
                // mDigits is not enclosed by the container. Make the digits field itself gone.
                mDigits.setVisibility(View.GONE);
            }
            if (mDialpad != null) mDialpad.setVisibility(View.GONE);
            if (mDialButtonContainer != null) mDialButtonContainer.setVisibility(View.GONE);

            mDialpadChooser.setVisibility(View.VISIBLE);

            // Instantiate the DialpadChooserAdapter and hook it up to the
            // ListView.  We do this only once.
            if (mDialpadChooserAdapter == null) {
                mDialpadChooserAdapter = new DialpadChooserAdapter(getActivity());
            }
            mDialpadChooser.setAdapter(mDialpadChooserAdapter);
        } else {
            // Log.i(TAG, "Displaying normal Dialer UI.");
            if (mDigitsContainer != null) {
                mDigitsContainer.setVisibility(View.VISIBLE);
            } else {
                mDigits.setVisibility(View.VISIBLE);
            }
            /// M:ALPS01236514.for sometimes can not show delete icon issue.@{
            if (mDelete != null) {
                mDelete.setVisibility(View.VISIBLE);
            }
            /// @}
            if (mDialpad != null) mDialpad.setVisibility(View.VISIBLE);
            if (mDialButtonContainer != null) mDialButtonContainer.setVisibility(View.VISIBLE);
            mDialpadChooser.setVisibility(View.GONE);
        }
    }

    /**
     * @return true if we're currently showing the "dialpad chooser" UI.
     */
    /** original code:
    private boolean isDialpadChooserVisible() {
        return mDialpadChooser.getVisibility() == View.VISIBLE;
    }
    */

    /**
     * Simple list adapter, binding to an icon + text label
     * for each item in the "dialpad chooser" list.
     */
    private static class DialpadChooserAdapter extends BaseAdapter {
        private LayoutInflater mInflater;

        // Simple struct for a single "choice" item.
        static class ChoiceItem {
            String text;
            Bitmap icon;
            int id;

            public ChoiceItem(String s, Bitmap b, int i) {
                text = s;
                icon = b;
                id = i;
            }
        }

        // IDs for the possible "choices":
        static final int DIALPAD_CHOICE_USE_DTMF_DIALPAD = 101;
        static final int DIALPAD_CHOICE_RETURN_TO_CALL = 102;
        static final int DIALPAD_CHOICE_ADD_NEW_CALL = 103;

        private static final int NUM_ITEMS = 3;
        private ChoiceItem mChoiceItems[] = new ChoiceItem[NUM_ITEMS];

        public DialpadChooserAdapter(Context context) {
            // Cache the LayoutInflate to avoid asking for a new one each time.
            mInflater = LayoutInflater.from(context);

            // Initialize the possible choices.
            // TODO: could this be specified entirely in XML?

            // - "Use touch tone keypad"
            mChoiceItems[0] = new ChoiceItem(
                    context.getString(R.string.dialer_useDtmfDialpad),
                    BitmapFactory.decodeResource(context.getResources(),
                                                 R.drawable.ic_dialer_fork_tt_keypad),
                    DIALPAD_CHOICE_USE_DTMF_DIALPAD);

            // - "Return to call in progress"
            mChoiceItems[1] = new ChoiceItem(
                    context.getString(R.string.dialer_returnToInCallScreen),
                    BitmapFactory.decodeResource(context.getResources(),
                                                 R.drawable.ic_dialer_fork_current_call),
                    DIALPAD_CHOICE_RETURN_TO_CALL);

            // - "Add call"
            mChoiceItems[2] = new ChoiceItem(
                    context.getString(R.string.dialer_addAnotherCall),
                    BitmapFactory.decodeResource(context.getResources(),
                                                 R.drawable.ic_dialer_fork_add_call),
                    DIALPAD_CHOICE_ADD_NEW_CALL);
        }

        @Override
        public int getCount() {
            return NUM_ITEMS;
        }

        /**
         * Return the ChoiceItem for a given position.
         */
        @Override
        public Object getItem(int position) {
            return mChoiceItems[position];
        }

        /**
         * Return a unique ID for each possible choice.
         */
        @Override
        public long getItemId(int position) {
            return position;
        }

        /**
         * Make a view for each row.
         */
        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            // When convertView is non-null, we can reuse it (there's no need
            // to reinflate it.)
            if (convertView == null) {
                convertView = mInflater.inflate(R.layout.dialpad_chooser_list_item, null);
            }

            TextView text = (TextView) convertView.findViewById(R.id.text);
            text.setText(mChoiceItems[position].text);

            ImageView icon = (ImageView) convertView.findViewById(R.id.icon);
            icon.setImageBitmap(mChoiceItems[position].icon);

            return convertView;
        }
    }

    /**
     * Handle clicks from the dialpad chooser.
     */
    @Override
    public void onItemClick(AdapterView<?> parent, View v, int position, long id) {
        DialpadChooserAdapter.ChoiceItem item =
                (DialpadChooserAdapter.ChoiceItem) parent.getItemAtPosition(position);
        int itemId = item.id;
        switch (itemId) {
            case DialpadChooserAdapter.DIALPAD_CHOICE_USE_DTMF_DIALPAD:
                // Log.i(TAG, "DIALPAD_CHOICE_USE_DTMF_DIALPAD");
                // Fire off an intent to go back to the in-call UI
                // with the dialpad visible.
                returnToInCallScreen(true);
                break;

            case DialpadChooserAdapter.DIALPAD_CHOICE_RETURN_TO_CALL:
                // Log.i(TAG, "DIALPAD_CHOICE_RETURN_TO_CALL");
                // Fire off an intent to go back to the in-call UI
                // (with the dialpad hidden).
                returnToInCallScreen(false);
                break;

            case DialpadChooserAdapter.DIALPAD_CHOICE_ADD_NEW_CALL:
                // Log.i(TAG, "DIALPAD_CHOICE_ADD_NEW_CALL");
                // Ok, guess the user really did want to be here (in the
                // regular Dialer) after all.  Bring back the normal Dialer UI.
                showDialpadChooser(false);
                break;

            default:
                Log.w(TAG, "onItemClick: unexpected itemId: " + itemId);
                break;
        }
    }

    /**
     * Returns to the in-call UI (where there's presumably a call in
     * progress) in response to the user selecting "use touch tone keypad"
     * or "return to call" from the dialpad chooser.
     */
    private void returnToInCallScreen(boolean showDialpad) {
        try {
            ITelephony phone = ITelephony.Stub.asInterface(ServiceManager.checkService("phone"));
            if (phone != null) phone.showCallScreenWithDialpad(showDialpad);
        } catch (RemoteException e) {
            Log.w(TAG, "phone.showCallScreenWithDialpad() failed", e);
        }

        // Finally, finish() ourselves so that we don't stay on the
        // activity stack.
        // Note that we do this whether or not the showCallScreenWithDialpad()
        // call above had any effect or not!  (That call is a no-op if the
        // phone is idle, which can happen if the current call ends while
        // the dialpad chooser is up.  In this case we can't show the
        // InCallScreen, and there's no point staying here in the Dialer,
        // so we just take the user back where he came from...)
        getActivity().finish();
    }

    /**
     * @return true if the phone is "in use", meaning that at least one line
     *              is active (ie. off hook or ringing or dialing).
     */
    public static boolean phoneIsInUse() {
        boolean phoneInUse = false;
        try {
            ITelephony phone = ITelephony.Stub.asInterface(ServiceManager.checkService("phone"));
            if (phone != null) {
                phoneInUse = !phone.isIdle();
            }
        } catch (RemoteException e) {
            Log.w(TAG, "phone.isIdle() failed", e);
        }
        return phoneInUse;
    }

    /**
     * @return true if the phone is a CDMA phone type
     */
    private boolean phoneIsCdma() {
        boolean isCdma = false;
        try {
            ITelephony phone = ITelephony.Stub.asInterface(ServiceManager.checkService("phone"));
            if (phone != null) {
                isCdma = (phone.getActivePhoneType() == TelephonyManager.PHONE_TYPE_CDMA);
            }
        } catch (RemoteException e) {
            Log.w(TAG, "phone.getActivePhoneType() failed", e);
        }
        return isCdma;
    }

    /**
     * @return true if the phone state is OFFHOOK
     */
    private boolean phoneIsOffhook() {
        boolean phoneOffhook = false;
        try {
            ITelephony phone = ITelephony.Stub.asInterface(ServiceManager.checkService("phone"));
            if (phone != null) {
                phoneOffhook = phone.isOffhook();
            }
        } catch (RemoteException e) {
            Log.w(TAG, "phone.isOffhook() failed", e);
        }
        return phoneOffhook;
    }

    /**
     * Returns true whenever any one of the options from the menu is selected.
     * Code changes to support dialpad options
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
       if (ExtensionManager.getInstance().getDialPadExtension().onOptionsItemSelected(item)) {
            return true;
        }
        switch (item.getItemId()) {
            case R.id.menu_2s_pause:
                /** M: New Feature xxx @{ */
                String p = ExtensionManager.getInstance().getDialPadExtension().changeChar("p",
                        ",", ContactPluginDefault.COMMD_FOR_OP01);
                updateDialString(p.charAt(0));
                return true;
            case R.id.menu_add_wait:
                String w = ExtensionManager.getInstance().getDialPadExtension().changeChar("w",
                        ";", ContactPluginDefault.COMMD_FOR_OP01);
                updateDialString(w.charAt(0));
                /** @} */
                return true;
            default:
                /*
                 * new feature by mediatek begin
                 * original android code :
                 * return false;
                 * description : handle 'ip dial' and 'speed dial'
                 */
                return onOptionsItemSelectedInternal(item);
                /*
                 * new feature by mediatek end
                 */
        }
    }

    @Override
    public boolean onMenuItemClick(MenuItem item) {
        if (ExtensionManager.getInstance().getDialPadExtension().onMenuItemClick(item)) {
            return true;
        }
        return onOptionsItemSelected(item);
    }

    /**
     * Updates the dial string (mDigits) after inserting a Pause character (,)
     * or Wait character (;).
     */
    private void updateDialString(char newDigit) {
        if(newDigit != WAIT && newDigit != PAUSE) {
            Log.wtf(TAG, "Not expected for anything other than PAUSE & WAIT");
            return;
        }

        int selectionStart;
        int selectionEnd;

        // SpannableStringBuilder editable_text = new SpannableStringBuilder(mDigits.getText());
        int anchor = mDigits.getSelectionStart();
        int point = mDigits.getSelectionEnd();

        selectionStart = Math.min(anchor, point);
        selectionEnd = Math.max(anchor, point);

        if (selectionStart == -1) {
            selectionStart = selectionEnd = mDigits.length();
        }

        Editable digits = mDigits.getText();

        if (canAddDigit(digits, selectionStart, selectionEnd, newDigit)) {
            digits.replace(selectionStart, selectionEnd, Character.toString(newDigit));

            if (selectionStart != selectionEnd) {
              // Unselect: back to a regular cursor, just pass the character inserted.
              mDigits.setSelection(selectionStart + 1);
            }
        }
    }

    /**
     * Update the enabledness of the "Dial" and "Backspace" buttons if applicable.
     */
    private void updateDialAndDeleteButtonEnabledState() {
        if (ExtensionManager.getInstance().getDialPadExtension()
                .updateDialAndDeleteButtonEnabledState(mLastNumberDialed)) {
            return;
        }
        final boolean digitsNotEmpty = !isDigitsEmpty();

        if (mDialButton != null) {
            // On CDMA phones, if we're already on a call, we *always*
            // enable the Dial button (since you can press it without
            // entering any digits to send an empty flash.)
            if (phoneIsCdma() && phoneIsOffhook()) {
                mDialButton.setEnabled(true);
            } else {
                // Common case: GSM, or CDMA but not on a call.
                // Enable the Dial button if some digits have
                // been entered, or if there is a last dialed number
                // that could be redialed.
                mDialButton.setEnabled(digitsNotEmpty ||
                        !TextUtils.isEmpty(mLastNumberDialed));
            }
        }

        if (mDelete != null) {
        mDelete.setEnabled(digitsNotEmpty);
        }
    }

    /**
     * Check if voicemail is enabled/accessible.
     *
     * @return true if voicemail is enabled and accessibly. Note that this can be false
     * "temporarily" after the app boot.
     * @see TelephonyManager#getVoiceMailNumber()
     */
    private boolean isVoicemailAvailable() {
        try {
            return getTelephonyManager().getVoiceMailNumber() != null;
        } catch (SecurityException se) {
            // Possibly no READ_PHONE_STATE privilege.
            Log.w(TAG, "SecurityException is thrown. Maybe privilege isn't sufficient.");
        }
        return false;
    }
    
     /**
     * This function return true if Wait menu item can be shown
     * otherwise returns false. Assumes the passed string is non-empty
     * and the 0th index check is not required.
     */
    private static boolean showWait(int start, int end, String digits) {
        if (start == end) {
            // visible false in this case
            if (start > digits.length()) {
                return false;
            }
            /** M: New Feature Easy Porting @{ */
            char[] cer = ExtensionManager.getInstance().getDialPadExtension().changeChar("w", ";",
                    ContactPluginDefault.COMMD_FOR_OP01)
                    .toCharArray();
            Log.i(TAG, "cer : " + cer + " | cer[0] : " + cer[0]
                    + " | digits.charAt(start - 1) == cer[0] : "
                    + (digits.charAt(start - 1) == cer[0])
                    + " | (digits.length() > start) && (digits.charAt(start) == cer[0]) : "
                    + ((digits.length() > start) && (digits.charAt(start) == cer[0])));
            if (digits.charAt(start - 1) == cer[0]) {
                return false;
            }
            if ((digits.length() > start) && (digits.charAt(start) == cer[0])) {
                return false;
            }
            /** @} */
        } else {
            // visible false in this case
            if (start > digits.length() || end > digits.length()) {
                return false;
            }

            /** M: New Feature Easy Porting @{ */
            char[] cer = ExtensionManager.getInstance().getDialPadExtension().changeChar("w", ";",
                    ContactPluginDefault.COMMD_FOR_OP01)
                    .toCharArray();
            Log.i(TAG, "cer : " + cer + " | cer[0] : " + cer[0]
                    + " | (digits.charAt(start - 1) == cer[0]) : "
                    + (digits.charAt(start - 1) == cer[0]));
            if (digits.charAt(start - 1) == cer[0]) {
                return false;
            }
            /** @} */
        }
        return true;
    }

    /**
     * Returns true of the newDigit parameter can be added at the current selection
     * point, otherwise returns false.
     * Only prevents input of WAIT and PAUSE digits at an unsupported position.
     * Fails early if start == -1 or start is larger than end.
     */
    @VisibleForTesting
    /* package */ static boolean canAddDigit(CharSequence digits, int start, int end,
                                             char newDigit) {
        if(newDigit != WAIT && newDigit != PAUSE) {
            Log.wtf(TAG, "Should not be called for anything other than PAUSE & WAIT");
            return false;
        }

        // False if no selection, or selection is reversed (end < start)
        if (start == -1 || end < start) {
            return false;
        }

        // unsupported selection-out-of-bounds state
        if (start > digits.length() || end > digits.length()) return false;

        // Special digit cannot be the first digit
        if (start == 0) return false;

        if (newDigit == WAIT) {
            // preceding char is ';' (WAIT)
            if (digits.charAt(start - 1) == WAIT) return false;

            // next char is ';' (WAIT)
            if ((digits.length() > end) && (digits.charAt(end) == WAIT)) return false;
        }

        return true;
    }

    /**
     * @return true if the widget with the phone number digits is empty.
     */
    private boolean isDigitsEmpty() {
        return mDigits.length() == 0;
    }

    /**
     * Starts the asyn query to get the last dialed/outgoing
     * number. When the background query finishes, mLastNumberDialed
     * is set to the last dialed number or an empty string if none
     * exists yet.
     */
    private void queryLastOutgoingCall() {
        mLastNumberDialed = EMPTY_NUMBER;
        CallLogAsync.GetLastOutgoingCallArgs lastCallArgs =
                new CallLogAsync.GetLastOutgoingCallArgs(
                    getActivity(),
                    new CallLogAsync.OnLastOutgoingCallComplete() {
                        @Override
                        public void lastOutgoingCall(String number) {
                            // TODO: Filter out emergency numbers if
                            // the carrier does not want redial for
                            // these.
                            // If the fragment has already been detached since the last time
                            // we called queryLastOutgoingCall in onResume there is no point
                            // doing anything here.
                            if (getActivity() == null) return;
                            mLastNumberDialed = number;
                            updateDialAndDeleteButtonEnabledState();
                        }
                    });
        mCallLog.getLastOutgoingCall(lastCallArgs);
    }
    
    private class CallLogContentObserver extends ContentObserver {
        public CallLogContentObserver() {
            super(new Handler());
        }

        @Override
        public void onChange(boolean selfChange) {
            log("call log observer onChange length: " + mLastNumberDialed.length());
            if (mIsForeground) {
                if (TextUtils.isEmpty(mLastNumberDialed)) {
                   queryLastOutgoingCall();
                }
            }
        }
    }

    private Intent newFlashIntent() {
        final Intent intent = CallUtil.getCallIntent(EMPTY_NUMBER);
        intent.putExtra(EXTRA_SEND_EMPTY_FLASH, true);
        return intent;
    }

    @Override
    public void onHiddenChanged(boolean hidden) {
        super.onHiddenChanged(hidden);
        final DialtactsActivity activity = (DialtactsActivity) getActivity();
        /// M: ALPS01233269 @{
        // Original code:
        // if (activity == null) return;
        if (activity == null || activity.isFinishing()) {
            LogUtils.d(TAG, "onHiddenChanged, activity is " + ((activity == null) ? "null" : "isFinishing"));
            return;
        }
        /// @}
        if (hidden) {
            activity.showSearchBar();
        } else {
            activity.hideSearchBar();
            mDigits.requestFocus();
        }
    }

    public void setAdjustTranslationForAnimation(boolean value) {
        mAdjustTranslationForAnimation = value;
    }

    public void setYFraction(float yFraction) {
        ((DialpadSlidingLinearLayout) getView()).setYFraction(yFraction);
    }

     /* below are added by mediatek .Inc */
    private static final int MSG_GET_TEXT_WATCHER = 1;

    enum FragmentState {
        UNKNOWN,
        CREATED,
        RESUMED,
        PAUSED,
        STOPPED,
        DESTROYED
    }

    private static final String ACTION = "com.mediatek.phone.OutgoingCallReceiver";

    private FragmentState mFragmentState = FragmentState.UNKNOWN;

    private ImageView mDialpadButton;
    private View mOverflowMenuButton;
    private View mVideoDialButton;
    //private View mAddToContactButton;
    private View mSendMessageButton;

    private ListView mListView;
    private View mDivider;
    private View mDialpadDivider;
    private AutoScaleTextSizeWatcher mAutoScaleTextSizeWatcher;
    private SpeedDial mSpeedDial;

    private Button mAddToContactListButton;

    private TextWatcher mTextWatcher;

    private ContactsCallOptionHandler mCallOptionHandler;

    /*private Intent newDialNumberIntent(String number, int type) {
        final Intent intent = new Intent(Intent.ACTION_CALL_PRIVILEGED,
                                         Uri.fromParts("tel", number, null));

        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        if((type & Constants.DIAL_NUMBER_INTENT_IP) != 0)
            intent.putExtra(Constants.EXTRA_IS_IP_DIAL, true);

        if((type & Constants.DIAL_NUMBER_INTENT_VIDEO) != 0)
            intent.putExtra(Constants.EXTRA_IS_VIDEO_CALL, true);

        return intent;
    }*/

    /*public void dialButtonPressed() {
        dialButtonPressedInner(mDigits.getText().toString(), Constants.DIAL_NUMBER_INTENT_NORMAL);
    }*/

    public void dialButtonPressed(int type) {
        dialButtonPressedInner(mDigits.getText().toString(), type);
    }

    public void dialButtonPressed(String number) {
        dialButtonPressedInner(number, Constants.DIAL_NUMBER_INTENT_NORMAL);
    }

    protected void dialButtonPressedInner(String number, int type) {
        log("dialButtonPressedInner number: " + number + "type:" + type);
        if (TextUtils.isEmpty(number)) { // No number entered.
            handleDialButtonClickWithEmptyDigits();
        } else {
            // "persist.radio.otaspdial" is a temporary hack needed for one
            // carrier's automated test equipment.
            // TODO: clean it up.
            if (number != null
                    && !TextUtils.isEmpty(mProhibitedPhoneNumberRegexp)
                    && number.matches(mProhibitedPhoneNumberRegexp)
                    && (SystemProperties.getInt("persist.radio.otaspdial", 0) != 1)) {
                Log.i(TAG, "The phone number is prohibited explicitly by a rule.");
                if (getActivity() != null) {
                    DialogFragment dialogFragment = ErrorDialogFragment.newInstance(
                            R.string.dialog_phone_call_prohibited_message);
                    dialogFragment.show(getFragmentManager(), "phone_prohibited_dialog");
                }

                // Clear the digits just in case.
                mDigits.getText().clear();
            } else {
                final Intent intent = CallUtil.getCallIntent(number,
                        (getActivity() instanceof DialtactsActivity ?
                                ((DialtactsActivity) getActivity()).getCallOrigin() : null), type);
                //mCallOptionHandler.doCallOptionHandle(intent);
                doCallOptionHandle(intent);
                hideAndClearDialpad();
                mClearDigitsOnStop = true;
                mIsNeedClearDialpad = true;
            }
        }
    }

   /* protected void showDialpad(boolean show) {
        int visibility = show ? View.VISIBLE : View.GONE;
        final Drawable drawable = show ? mHideDialpadDrawable : mShowDialpadDrawable;
        if (null != getActivity() && getActivity() instanceof DialtactsActivity) {
            ((DialtactsActivity)getActivity()).addDialpadScrollingThreshold(show);
        }
        log("showDialpad visibility: " + visibility + "drawable:" + drawable);
        mDialpad.setVisibility(visibility);
        mDialpadDivider.setVisibility(visibility);
        if (mDigitsContainer != null) {
            mDigitsContainer.setVisibility(visibility);
            mDelete.setVisibility(visibility);
        } else if (mDelete != null && mDigits != null) {
            mDigits.setVisibility(visibility);
            mDelete.setVisibility(visibility);
        }
        if (mDialpadButton != null) {
            mDialpadButton.setImageDrawable(drawable);
        }

        if (DialerApplication.sDialerSearchSupport) {
            log("showDialpad, adjust list view layout parameters");
            adjustListViewLayoutParameters();
        }
    }*/

    protected boolean onOptionsItemSelectedInternal(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_ip_dial:
                return onIPDialMenuItemSelected();
            case R.id.menu_speed_dial:
                return onSpeedDialMenuItemSelected();
            case R.id.menu_send_message:
                return onSendMessageMenuItemSelected();
            case R.id.menu_people:
                return ((DialtactsActivity) getActivity()).onPeopleMenuItemSelected();
            case R.id.menu_video_call:
                dialButtonPressed(Constants.DIAL_NUMBER_INTENT_VIDEO);
                return true;
        }
        return false;
    }

    public boolean onLongClickInternal(View view) {
        int key = -1;
        switch (view.getId()) {
            case R.id.deleteButton:
                // mDigits.getText().clear();
                mDigits.setText(EMPTY_NUMBER);
                // TODO: The framework forgets to clear the pressed
                // status of disabled button. Until this is fixed,
                // clear manually the pressed status. b/2133127
                mDelete.setPressed(false);
                mAutoScaleTextSizeWatcher.trigger(true);
                return true;
            case R.id.two:
                key = 2;
                break;
            case R.id.three:
                key = 3;
                break;
            case R.id.four:
                key = 4;
                break;
            case R.id.five:
                key = 5;
                break;
            case R.id.six:
                key = 6;
                break;
            case R.id.seven:
                key = 7;
                break;
            case R.id.eight:
                key = 8;
                break;
            case R.id.nine:
                key = 9;
                break;
            default:
                return false;
        }

        if (!(DialerApplication.sSpeedDial && (isDigitsEmpty() || 1 == mDigits.length() && -1 != key))) {
            return false;
        }

        boolean isDialed = mSpeedDial.getSpeedDialNumber(key).isEmpty();
        if (isDialed) {
            /// M: fixed CR ALPS01082215.use a function to show dialog
            showSpeedDialConfirmDialog();
        } else {
            mDigits.setText(EMPTY_NUMBER);
            /// M: fixed CR ALPS01082215.if speed dial contact is not exist,show Confirm Dialog also.
            if (!mSpeedDial.dial(key)) {
                showSpeedDialConfirmDialog();
                isDialed = true;
            }
        }
        if (-1 != key && 1 == mDigits.length() && isDialed) {
            removePreviousDigitIfPossible();
            stopTone();
            if (mDialpadPressCount > 0) {
                mDialpadPressCount--;
            }
        }

        log("onLongClickInternal key: " + key);
        return isDialed;
    }

    protected boolean onShowDialpadButtonClick() {
        if (mDialpad != null) {
            final boolean show = mDialpad.getVisibility() != View.VISIBLE;
         //   showDialpad(show);
            /**
             * M:ALPS00380682 Get mDigits have the focus when dialpad is on
             * 
             * @{
             */
            if (show && mDigits != null) {
                mDigits.requestFocus();
            }
            /**@}*/
            return true;
        }
        return false;
    }

    protected boolean onIPDialMenuItemSelected() {
        dialButtonPressed(Constants.DIAL_NUMBER_INTENT_IP);
        return true;
    }

    protected boolean onSendMessageMenuItemSelected() {
        String phoneNumber = mDigits.getText().toString();
        log("onSendMessageMenuItemSelected: number " + phoneNumber);

        Uri uri = Uri.fromParts("sms", phoneNumber, null);
        Intent intent = new Intent(Intent.ACTION_VIEW, uri);

        log("onSendMessageMenuItemSelected Launching SMS compose UI: " + intent);
        try {
            getActivity().startActivity(intent);
        } catch (ActivityNotFoundException e) {
            Log.e(TAG, "No activity found for intent: " + intent);
        }
        return true;
    }

    protected boolean onSpeedDialMenuItemSelected() {
        final Intent intent = new Intent();
        intent.setClass(getActivity(), SpeedDialManageActivity.class);
        getActivity().startActivity(intent);
        return true;
    }

    private boolean isVoicemailAvailableProxy() {
        if (SlotUtils.isGeminiEnabled()) {
            // OutgoingCallBroadcaster will do the stuffs
            // just return true

            final long defaultSim = Settings.System.getLong(getActivity().getContentResolver(),
                    Settings.System.VOICE_CALL_SIM_SETTING, Settings.System.DEFAULT_SIM_NOT_SET);

            if (defaultSim == Settings.System.VOICE_CALL_SIM_SETTING_INTERNET) {
                return false;
            }

        }
        /// M: Bug fix ALPS01272091 @{
        return true;
        /// @}
    }

    private void setupPopupMenuItems(Menu menu) {
        final MenuItem callSettingsMenuItem = menu.findItem(R.id.menu_call_settings_dialpad);
        /** M: change for cr ALPS00958600 ALPS00982820 @{ */
        final Activity activity = getActivity();
        if (activity != null && ViewConfiguration.get(activity).hasPermanentMenuKey()) {
            callSettingsMenuItem.setVisible(false);
        } else {
            callSettingsMenuItem.setVisible(true);
            callSettingsMenuItem.setIntent(DialtactsActivity.getCallSettingsIntent());
        }
        /** @} */
    }

    public boolean onKeyDown(int keyCode, KeyEvent event) {
        log("KeyEvent = " + event.getKeyCode() + "mDigits.hasFocus(): " + mDigits.hasFocus() + "keyCode " + keyCode);
        if (event.getKeyCode() == KeyEvent.KEYCODE_CALL) {
            dialButtonPressed();
            return true;
        }

        // focus the mDigits and let it handle the key events
        if (!isTrackBallEvent(event)) {
            if (!phoneIsOffhook() && mDigits.getVisibility() != View.VISIBLE) {
                mDigits.setVisibility(View.VISIBLE);
            }
            final InputMethodManager imm = ((InputMethodManager)getActivity()
                    .getSystemService(Context.INPUT_METHOD_SERVICE));
            if ((event.getKeyCode() == KeyEvent.KEYCODE_DEL) && !mDigits.hasFocus() && imm != null
                    && !imm.isActive(mDigits)) {
                return false;
            }

            if (!mDigits.hasFocus()) {
                mDigits.requestFocus();
                return mDigits.onKeyDown(keyCode, event);
            }
        }

        switch (keyCode) {
            case KeyEvent.KEYCODE_1: {
                keyPressed(KeyEvent.KEYCODE_1);
                return true;
            }
            case KeyEvent.KEYCODE_2: {
                keyPressed(KeyEvent.KEYCODE_2);
                return true;
            }
            case KeyEvent.KEYCODE_3: {
                keyPressed(KeyEvent.KEYCODE_3);
                return true;
            }
            case KeyEvent.KEYCODE_4: {
                keyPressed(KeyEvent.KEYCODE_4);
                return true;
            }
            case KeyEvent.KEYCODE_5: {
                keyPressed(KeyEvent.KEYCODE_5);
                return true;
            }
            case KeyEvent.KEYCODE_6: {
                keyPressed(KeyEvent.KEYCODE_6);
                return true;
            }
            case KeyEvent.KEYCODE_7: {
                keyPressed(KeyEvent.KEYCODE_7);
                return true;
            }
            case KeyEvent.KEYCODE_8: {
                keyPressed(KeyEvent.KEYCODE_8);
                return true;
            }
            case KeyEvent.KEYCODE_9: {
                keyPressed(KeyEvent.KEYCODE_9);
                return true;
            }
            case KeyEvent.KEYCODE_0: {
                keyPressed(KeyEvent.KEYCODE_0);
                return true;
            }
            case KeyEvent.KEYCODE_POUND: {
                keyPressed(KeyEvent.KEYCODE_POUND);
                return true;
            }
            case KeyEvent.KEYCODE_STAR: {
                keyPressed(KeyEvent.KEYCODE_STAR);
                return true;
            }
            case KeyEvent.KEYCODE_MENU: {
                if (null != getActivity()) {
                    getActivity().invalidateOptionsMenu();
                }
                return false;
            }
            case KeyEvent.KEYCODE_POWER:
            case KeyEvent.KEYCODE_VOLUME_UP:
            case KeyEvent.KEYCODE_VOLUME_DOWN: {
                mNeedCheckSetting = true;
                return false;
            }
        }
        return false;
    }

    void adjustListViewLayoutParameters() {
        final boolean dialpadVisible = mDialpad != null && mDialpad.getVisibility() == View.VISIBLE;
        final boolean digitsVisible = getDigitsVisibility() == View.VISIBLE;
        log("adjustListViewLayoutParameters, dialpadVisible = " + dialpadVisible + " digitsVisible = " + digitsVisible);

        if (mListView != null) {
            RelativeLayout.LayoutParams lParams = (RelativeLayout.LayoutParams) mListView
                    .getLayoutParams();

            int above;
            if (dialpadVisible) {
                if (digitsVisible) {
                    above = R.id.digits_container;
                } else {
                    if (mDivider != null) {
                        above = R.id.divider;
                    } else {
                        above = R.id.dialpad;
                    }
                }
            } else {
                if (digitsVisible) {
                    above = R.id.digits_container;
                } else {
                    //above = R.id.dialpadAdditionalButtons;
                }
            }

          //  lParams.addRule(RelativeLayout.ABOVE, above);
            mListView.setLayoutParams(lParams);
        }
    }

    void log(String msg) {
        if (DEBUG) {
            Log.d(TAG, msg);
        }
    }

    boolean isTrackBallEvent(KeyEvent event) {
        int keycode = event.getKeyCode();
        return keycode == KeyEvent.KEYCODE_DPAD_LEFT || keycode == KeyEvent.KEYCODE_DPAD_UP
                || keycode == KeyEvent.KEYCODE_DPAD_RIGHT || keycode == KeyEvent.KEYCODE_DPAD_DOWN;
    }

    int getDigitsVisibility() {
        if (mDigitsContainer != null) {
            return mDigitsContainer.getVisibility();
        } else {
            return mDigits.getVisibility();
        }
    }

    private boolean isDialpadChooserVisible() {
        return mDialpadChooser != null && mDialpadChooser.getVisibility() == View.VISIBLE;
    }

    private Handler mHandler = new Handler() {
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_GET_TEXT_WATCHER:
                    if (msg.obj instanceof TextWatcher) {
                        mTextWatcher = (TextWatcher) msg.obj;
                    }
                    break;
                default:
                    break;
            }
        }
    };

    public void doCallOptionHandle(Intent intent) {
        if (null != mCallOptionHandler) {
            mCallOptionHandler.doCallOptionHandle(intent);
        }
    }

    public class ConfirmDialogFragment extends DialogFragment {
        public static final String FRAGMENT_TAG = "speed_dial";

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
        }

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
            builder.setTitle(R.string.call_speed_dial_title);
            builder.setMessage(R.string.dialog_no_speed_dial_number_message);
            builder.setPositiveButton(android.R.string.ok,
                    new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                mSpeedDial.enterSpeedDial();
                                dismiss();
                            }
                    });
            builder.setNegativeButton(android.R.string.cancel,
                    new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                dismiss();
                            }
                    });
            return builder.create();
        }
    }

    /// M: change for SIP number can be added to SIM card problem. CR ALPS01092714
    private boolean isSipNumber(CharSequence number) {
        if (mPhoneNumberHelper == null) {
            mPhoneNumberHelper = new PhoneNumberHelperEx(getResources());
        }
        if (mPhoneNumberHelper.isSipNumber(number)) {
            return true;
        }
        return false;
    }

    /**
     * M: fixed CR ALPS01082215.use a function to show dialog @{
     */
    private void showSpeedDialConfirmDialog() {
        if (mSpeedDialConfirmDialogFragment == null) {
            mSpeedDialConfirmDialogFragment = new ConfirmDialogFragment();
        }
        mSpeedDialConfirmDialogFragment.show(getFragmentManager(), ConfirmDialogFragment.FRAGMENT_TAG);
    }
    
    ///M: change for  CR ALPS01260118,improve the Dialer call performance
    private boolean mIsNeedClearDialpad = false;
    
    public boolean isNeedVlearDialpad() {
        return mIsNeedClearDialpad;
    }
    
    public void setClearDialpadState(boolean state) {
        mIsNeedClearDialpad = state;
    }
    /** @} */
}
