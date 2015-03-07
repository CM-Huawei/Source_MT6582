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

package com.android.dialer;

import android.animation.Animator;
import android.animation.Animator.AnimatorListener;
import android.animation.AnimatorListenerAdapter;
import android.app.Activity;
import android.app.Fragment;
import android.app.FragmentManager;
import android.app.FragmentManager.BackStackEntry;
import android.app.FragmentTransaction;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Drawable;
//The following lines are provided and maintained by Mediatek Inc.
import android.content.IntentFilter;
//The previous lines are provided and maintained by Mediatek Inc.
import android.content.SharedPreferences;
/** M: New Feature Phone Landscape UI @{ */
import android.content.res.Configuration;
/** @ }*/
import android.graphics.Rect;
import android.net.Uri;
import android.os.Bundle;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.provider.CallLog.Calls;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.Intents;
import android.provider.ContactsContract.Intents.UI;
import android.speech.RecognizerIntent;
import android.telephony.TelephonyManager;
import android.text.Editable;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.style.ImageSpan;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.ViewConfiguration;
import android.view.MenuItem.OnMenuItemClickListener;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnFocusChangeListener;
import android.view.inputmethod.InputMethodManager;
import android.widget.AbsListView.OnScrollListener;
import android.widget.EditText;
import android.widget.PopupMenu;
import android.widget.Toast;

import com.android.contacts.common.CallUtil;
import com.android.contacts.common.activity.TransactionSafeActivity;
import com.android.contacts.common.dialog.ClearFrequentsDialog;
import com.android.contacts.common.interactions.ImportExportDialogFragment;
import com.android.contacts.common.list.OnPhoneNumberPickerActionListener;
import com.android.contacts.common.preference.ContactsPreferences;
import com.android.dialer.calllog.CallLogActivity;
import com.android.dialer.calllog.ClearCallLogDialog;
import com.android.dialer.database.DialerDatabaseHelper;
import com.android.dialer.dialpad.DialpadFragment;
import com.android.dialer.dialpad.SmartDialNameMatcher;
import com.android.dialer.dialpad.SmartDialPrefix;
import com.android.dialer.interactions.PhoneNumberInteraction;
import com.android.dialer.list.AllContactsActivity;
import com.android.dialer.list.OnListFragmentScrolledListener;
import com.android.dialer.list.PhoneFavoriteFragment;
import com.android.dialer.list.RegularSearchFragment;
import com.android.dialer.list.SearchFragment;
import com.android.dialer.list.SmartDialSearchFragment;
import com.android.dialerbind.DatabaseHelperManager;
import com.android.internal.telephony.ITelephony;

// The following lines are provided and maintained by Mediatek Inc.
import com.android.dialer.util.OrientationUtil;
import com.mediatek.dialer.calllog.CallLogSearchFragment;
import com.mediatek.dialer.calllogex.CallLogActivityEx;
import com.mediatek.dialer.dialpad.NoSearchResultFragment;
import com.mediatek.contacts.ContactsFeatureConstants.FeatureOption;
import com.mediatek.contacts.ExtensionManager;
import com.mediatek.contacts.Profiler;
import com.mediatek.contacts.ext.ContactPluginDefault;
import com.mediatek.contacts.simservice.SIMServiceUtils;
import com.mediatek.contacts.simcontact.SlotUtils;
import com.mediatek.contacts.util.SetIndicatorUtils;
import com.mediatek.dialer.DialerSearchHelper;
import com.mediatek.dialer.DialerSearchHelperManager;
import com.mediatek.dialer.SpecialCharSequenceMgrProxy;
import com.mediatek.dialer.list.ProviderStatusWatcher;
import com.mediatek.dialer.list.ProviderStatusWatcher.ProviderStatusListener;
import com.mediatek.dialer.util.LogUtils;
import com.mediatek.dialer.util.PhoneCapabilityTester;
import com.mediatek.dialer.util.SmartBookUtils;
import com.mediatek.phone.HyphonManager;
import com.mediatek.xlog.Xlog;

import java.util.List;
import java.util.Locale;
// The previous lines are provided and maintained by Mediatek Inc.

import java.util.ArrayList;

/**
 * The dialer tab's title is 'phone', a more common name (see strings.xml).
 */
public class DialtactsActivity extends TransactionSafeActivity implements View.OnClickListener,
        DialpadFragment.OnDialpadQueryChangedListener, PopupMenu.OnMenuItemClickListener,
        OnListFragmentScrolledListener,
        DialpadFragment.OnDialpadFragmentStartedListener,
        PhoneFavoriteFragment.OnShowAllContactsListener {
    private static final String TAG = "DialtactsActivity";

    public static final boolean DEBUG = false;

    public static final String SHARED_PREFS_NAME = "com.android.dialer_preferences";

    /** Used to open Call Setting */
    private static final String PHONE_PACKAGE = "com.android.phone";
    /* original code
    private static final String CALL_SETTINGS_CLASS_NAME =
            "com.android.phone.CallFeaturesSetting";
    */
    private static final String CALL_SETTINGS_CLASS_NAME = "com.mediatek.settings.CallSettings";
    /** @see #getCallOrigin() */
    private static final String CALL_ORIGIN_DIALTACTS =
            "com.android.dialer.DialtactsActivity";

    private static final String KEY_IN_REGULAR_SEARCH_UI = "in_regular_search_ui";
    private static final String KEY_IN_DIALPAD_SEARCH_UI = "in_dialpad_search_ui";
    private static final String KEY_SEARCH_QUERY = "search_query";
    private static final String KEY_FIRST_LAUNCH = "first_launch";

    private static final String TAG_DIALPAD_FRAGMENT = "dialpad";
    private static final String TAG_REGULAR_SEARCH_FRAGMENT = "search";
    private static final String TAG_SMARTDIAL_SEARCH_FRAGMENT = "smartdial";
    private static final String TAG_FAVORITES_FRAGMENT = "favorites";

    /**
     * Just for backward compatibility. Should behave as same as {@link Intent#ACTION_DIAL}.
     */
    private static final String ACTION_TOUCH_DIALER = "com.android.phone.action.TOUCH_DIALER";

    private static final int SUBACTIVITY_ACCOUNT_FILTER = 1;

    private static final int ACTIVITY_REQUEST_CODE_VOICE_SEARCH = 1;

    private String mFilterText;

    /**
     * The main fragment displaying the user's favorites and frequent contacts
     */
    private PhoneFavoriteFragment mPhoneFavoriteFragment;

    /**
     * Fragment containing the dialpad that slides into view
     */
    private DialpadFragment mDialpadFragment;

    /**
     * Fragment for searching phone numbers using the alphanumeric keyboard.
     */
    private RegularSearchFragment mRegularSearchFragment;

    /**
     * Fragment for searching phone numbers using the dialpad.
     */
    private SmartDialSearchFragment mSmartDialSearchFragment;

    private View mMenuButton;
    private View mCallHistoryButton;
    private View mDialpadButton;
    private PopupMenu mOverflowMenu;

    // Padding view used to shift the fragments up when the dialpad is shown.
    private View mBottomPaddingView;
    private View mFragmentsFrame;
    private View mActionBar;

    private boolean mInDialpadSearch;
    private boolean mInRegularSearch;
    private boolean mClearSearchOnPause;

    /**
     * True if the dialpad is only temporarily showing due to being in call
     */
    private boolean mInCallDialpadUp;

    /**
     * True when this activity has been launched for the first time.
     */
    private boolean mFirstLaunch;
    private View mSearchViewContainer;
    private View mSearchViewCloseButton;
    /**M:original code:
    private View mVoiceSearchButton;
    */
    private EditText mSearchView;

    private String mSearchQuery;

    private DialerDatabaseHelper mDialerDatabaseHelper;

    private class OverflowPopupMenu extends PopupMenu {
        public OverflowPopupMenu(Context context, View anchor) {
            super(context, anchor);
        }

        @Override
        public void show() {
            final Menu menu = getMenu();
            final MenuItem clearFrequents = menu.findItem(R.id.menu_clear_frequents);
            LogUtils.d(TAG, "clearFrequents: " + mPhoneFavoriteFragment.hasFrequents());

            clearFrequents.setVisible(mPhoneFavoriteFragment.hasFrequents());
            super.show();
        }
    }

    /**
     * Listener used when one of phone numbers in search UI is selected. This will initiate a
     * phone call using the phone number.
     */
    private final OnPhoneNumberPickerActionListener mPhoneNumberPickerActionListener =
            new OnPhoneNumberPickerActionListener() {
                @Override
                public void onPickPhoneNumberAction(Uri dataUri) {
                    // Specify call-origin so that users will see the previous tab instead of
                    // CallLog screen (search UI will be automatically exited).
                    PhoneNumberInteraction.startInteractionForPhoneCall(
                        DialtactsActivity.this, dataUri, getCallOrigin());
                    mClearSearchOnPause = true;
                }

                @Override
                public void onCallNumberDirectly(String phoneNumber) {
                    Intent intent = CallUtil.getCallIntent(phoneNumber, getCallOrigin());
                  /// M: change for  CR ALPS01260118,improve the Dialer call performance
                    //Google code: startActivity(intent);
                    mDialpadFragment.doCallOptionHandle(intent);
                    mClearSearchOnPause = true;
                }

                @Override
                public void onShortcutIntentCreated(Intent intent) {
                    Log.w(TAG, "Unsupported intent has come (" + intent + "). Ignoring.");
                }

                @Override
                public void onHomeInActionBarSelected() {
                    exitSearchUi();
                }
    };

    /**
     * Listener used to send search queries to the phone search fragment.
     */
    private final TextWatcher mPhoneSearchQueryTextListener = new TextWatcher() {
        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
            final String newText = s.toString();
            if (newText.equals(mSearchQuery)) {
                // If the query hasn't changed (perhaps due to activity being
                // destroyed
                // and restored, or user launching the same DIAL intent twice),
                // then there is
                // no need to do anything here.
                return;
            }
            mSearchQuery = newText;
            if (DEBUG) {
                Log.d(TAG, "onTextChange for mSearchView called with new query: " + s);
            }
            /// M: Fix CR: ALPS01260630, Time lag issues @{
            /**
             * Original Code: final boolean dialpadSearch = isDialpadShowing()
             */
            final boolean dialpadSearch = isDialpadShowing() || mFirstQuery;
            /// M: @}

            // Show search result with non-empty text. Show a bare list
            // otherwise.
            if (TextUtils.isEmpty(newText) && getInSearchUi()) {
                exitSearchUi();
                mSearchViewCloseButton.setVisibility(View.GONE);
                /**
                 * M:original code:
                 * mVoiceSearchButton.setVisibility(View.VISIBLE);
                 */
                return;
            } else if (!TextUtils.isEmpty(newText)) {
                final boolean sameSearchMode = (dialpadSearch && mInDialpadSearch) || (!dialpadSearch && mInRegularSearch);
                if (!sameSearchMode) {
                    // call enterSearchUi only if we are switching search modes,
                    // or entering
                    // search ui for the first time
                    enterSearchUi(dialpadSearch, newText);
                    /// M: Fix CR: ALPS01285522. @{
                    // it's unnecessary to setQueryString again return directly.
                    LogUtils.d(TAG, "onTextChanged enterSearchUi return! newText="+newText);
                    return;
                    /// @}
                }

                if (dialpadSearch && mSmartDialSearchFragment != null) {
                    LogUtils.d(TAG, "MTK-DialerSearch, SmartDialSearchFragment");
                    mSmartDialSearchFragment.setQueryString(newText, false);
                    /// M: Fix CR: ALPS01260630, Time lag issue @{
                    mFirstQuery = false;
                    /// M: @}

                } else if (mRegularSearchFragment != null) {
                    LogUtils.d(TAG, "MTK-DialerSearch, RegularSearchFragment");
                    mRegularSearchFragment.setQueryString(newText, false);

                }
                mSearchViewCloseButton.setVisibility(View.VISIBLE);
                /**
                 * M:original code: mVoiceSearchButton.setVisibility(View.GONE);
                 */
                return;
            }
        }

        @Override
        public void afterTextChanged(Editable s) {
        }
    };

    private boolean isDialpadShowing() {
        return mDialpadFragment != null && mDialpadFragment.isVisible();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        /// M: ALPS01463172, for smartBook case @{
        SmartBookUtils.setOrientationPortait(this);
        /// M: @}

        mFirstLaunch = true;
        mSavedInstance = false;

        final Intent intent = getIntent();
        fixIntent(intent);
        /// M: ALPS01233269 @{
        if (returnToInCallScreen(intent)) {
            LogUtils.d(TAG, "onCreate, returnToInCallScreen");
        }
        /// @}

        setContentView(R.layout.dialtacts_activity);

        getActionBar().hide();

        // Add the favorites fragment, and the dialpad fragment, but only if savedInstanceState
        // is null. Otherwise the fragment manager takes care of recreating these fragments.
        if (savedInstanceState == null) {
            final PhoneFavoriteFragment phoneFavoriteFragment = new PhoneFavoriteFragment();

            final FragmentTransaction ft = getFragmentManager().beginTransaction();
            /// M: ALPS01329477 @{
            // if fragment is exist, don't need to add a new Fragment. May cause OOM.
            if (getFragmentManager().findFragmentByTag(TAG_FAVORITES_FRAGMENT) == null) {
                ft.add(R.id.dialtacts_frame, phoneFavoriteFragment, TAG_FAVORITES_FRAGMENT);
            }
            if (getFragmentManager().findFragmentByTag(TAG_DIALPAD_FRAGMENT) == null) {
                ft.add(R.id.dialtacts_container, new DialpadFragment(), TAG_DIALPAD_FRAGMENT);
            }
            /// @}
            /** M: New Feature Landspace in dialer @{ */
            if (OrientationUtil.isLandscape(this)
                    && getFragmentManager().findFragmentByTag(TAG_SEARCH_FRAGMENT) == null) {
                Log.i(TAG, "onCreate Tablet landscape support");
                ft.add(R.id.search_frame, new NoSearchResultFragment(), TAG_SEARCH_FRAGMENT);
            }
            /** @} */
            ft.commit();
        } else {
            mSearchQuery = savedInstanceState.getString(KEY_SEARCH_QUERY);
            mInRegularSearch = savedInstanceState.getBoolean(KEY_IN_REGULAR_SEARCH_UI);
            mInDialpadSearch = savedInstanceState.getBoolean(KEY_IN_DIALPAD_SEARCH_UI);
            mFirstLaunch = savedInstanceState.getBoolean(KEY_FIRST_LAUNCH);
        }

        mBottomPaddingView = findViewById(R.id.dialtacts_bottom_padding);
        mFragmentsFrame = findViewById(R.id.dialtacts_frame);
        mActionBar = findViewById(R.id.fake_action_bar);
        prepareSearchView();

        if (UI.FILTER_CONTACTS_ACTION.equals(intent.getAction())
                && savedInstanceState == null) {
            setupFilterText(intent);
        }

        setupFakeActionBarItems();

        mDialerDatabaseHelper = DatabaseHelperManager.getDatabaseHelper(this);
        SmartDialPrefix.initializeNanpSettings(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        mSavedInstance = false;
        if (mFirstLaunch) {
            /// M: Fix CR: ALPS01260630, Time lag issues @{
           if(isDialIntent(getIntent())) {
                mFirstQuery = true;
            }
            ///M: @}
            displayFragment(getIntent());
        } else if (!phoneIsInUse() && mInCallDialpadUp) {
            hideDialpadFragment(false, true);
            mInCallDialpadUp = false;
        /// M: Fix CR ALPS01380190. @{
        // modify for sometimes UI can not return to PhoneFavoriteFragment correctly issue.
        } else if (TextUtils.isEmpty(mSearchView.getText()) && !getInSearchUi()) {
            ensureExitSearchUi();
        }
        /// @}

        mFirstLaunch = false;
        mDialerDatabaseHelper.startSmartDialUpdateThread();
        /**
         * M: [Sim Indicator]
         */
        SetIndicatorUtils.getInstance().showIndicator(true, this);
    }

    @Override
    protected void onPause() {
        ///M: change for  CR ALPS01260118,improve the Dialer call performance
        if(mDialpadFragment.isNeedVlearDialpad()) {
            mDialpadFragment.setClearDialpadState(false);
            hideDialpadFragment(false,true);
        }
        if (mClearSearchOnPause) {
            hideDialpadAndSearchUi();
            mClearSearchOnPause = false;
        }
        /**
         * M: [Sim Indicator]
         */
        SetIndicatorUtils.getInstance().showIndicator(false, this);
        super.onPause();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        mSavedInstance = true;
        super.onSaveInstanceState(outState);
        outState.putString(KEY_SEARCH_QUERY, mSearchQuery);
        outState.putBoolean(KEY_IN_REGULAR_SEARCH_UI, mInRegularSearch);
        outState.putBoolean(KEY_IN_DIALPAD_SEARCH_UI, mInDialpadSearch);
        outState.putBoolean(KEY_FIRST_LAUNCH, mFirstLaunch);
    }

    @Override
    public void onAttachFragment(Fragment fragment) {
        if (fragment instanceof DialpadFragment) {
            mDialpadFragment = (DialpadFragment) fragment;
            final FragmentTransaction transaction = getFragmentManager().beginTransaction();
            transaction.hide(mDialpadFragment);
            transaction.commit();
        } else if (fragment instanceof SmartDialSearchFragment) {
            /// M: Fix CR: ALPS01285522. @{
            // get fragment by tag, if return null, recreate it.
            Fragment getFragment = (SearchFragment) getFragmentManager().findFragmentByTag(TAG_SMARTDIAL_SEARCH_FRAGMENT);
            String query = ((SmartDialSearchFragment)fragment).getQueryString();
            if (getFragment == null && !TextUtils.isEmpty(query)) {
                enterSearchUi(true, query);
                LogUtils.d(TAG, "onAttachFragment reenter Search UI and return! newText=" + query);
                return;
            }
            /// @}
            mSmartDialSearchFragment = (SmartDialSearchFragment) fragment;
            mSmartDialSearchFragment.setOnPhoneNumberPickerActionListener(
                    mPhoneNumberPickerActionListener);
        } else if (fragment instanceof SearchFragment) {
            mRegularSearchFragment = (RegularSearchFragment) fragment;
            mRegularSearchFragment.setOnPhoneNumberPickerActionListener(
                    mPhoneNumberPickerActionListener);
        } else if (fragment instanceof PhoneFavoriteFragment) {
            mPhoneFavoriteFragment = (PhoneFavoriteFragment) fragment;
            mPhoneFavoriteFragment.setListener(mPhoneFavoriteListener);
        }
        /** M: New Feature Landspace in dialer @{ */
          else if (fragment instanceof NoSearchResultFragment) {
            mEmptyFragment = (NoSearchResultFragment) fragment;
        }
        /** @} */
    }

    @Override
    public boolean onMenuItemClick(MenuItem item) {
        switch (item.getItemId()) {
            /// M: ALPS01250893.@{
            // modify for delete the import export function in dialer.
            // original code:
            /**
            case R.id.menu_import_export:
                // We hard-code the "contactsAreAvailable" argument because doing it properly would
                // involve querying a {@link ProviderStatusLoader}, which we don't want to do right
                // now in Dialtacts for (potential) performance reasons. Compare with how it is
                // done in {@link PeopleActivity}.
                ImportExportDialogFragment.show(getFragmentManager(), true,
                        DialtactsActivity.class);
                return true;
               */
            /// @}
            case R.id.menu_clear_frequents:
                ClearFrequentsDialog.show(getFragmentManager());
                return true;
            case R.id.menu_add_contact:
                try {
                    startActivity(new Intent(Intent.ACTION_INSERT, Contacts.CONTENT_URI));
                } catch (ActivityNotFoundException e) {
                    Toast toast = Toast.makeText(this,
                            R.string.add_contact_not_available,
                            Toast.LENGTH_SHORT);
                    toast.show();
                }
                return true;
            case R.id.menu_call_settings:
                handleMenuSettings();
                return true;
            /** original code:
            case R.id.menu_all_contacts:
                onShowAllContacts();
                return true;
            */
            case R.id.menu_people:
                onPeopleMenuItemSelected();
                return true;
        }
        return false;
    }

    protected void handleMenuSettings() {
        openTelephonySetting(this);
    }

    public static void openTelephonySetting(Activity activity) {
        final Intent settingsIntent = getCallSettingsIntent();
        activity.startActivity(settingsIntent);
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.overflow_menu: {
                mOverflowMenu.show();
                break;
            }
            case R.id.dialpad_button:
                // Reset the boolean flag that tracks whether the dialpad was up because
                // we were in call. Regardless of whether it was true before, we want to
                // show the dialpad because the user has explicitly clicked the dialpad
                // button.
                mInCallDialpadUp = false;
                showDialpadFragment(true);
                break;
            case R.id.call_history_on_dialpad_button:
            case R.id.call_history_button:
                // Use explicit CallLogActivity intent instead of ACTION_VIEW +
                // CONTENT_TYPE, so that we always open our call log from our dialer
                final Intent intent = new Intent(this, CallLogActivityEx.class);
                startActivity(intent);
                break;
            case R.id.search_close_button:
                // Clear the search field
                if (!TextUtils.isEmpty(mSearchView.getText())) {
                    mDialpadFragment.clearDialpad();
                    mSearchView.setText("");
                }
                break;
            /** original code:
            case R.id.voice_search_button:
                final Intent voiceIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
                startActivityForResult(voiceIntent, ACTIVITY_REQUEST_CODE_VOICE_SEARCH);
                break;
                */
            default: {
                Log.wtf(TAG, "Unexpected onClick event from " + view);
                break;
            }
        }
    }

    /* Not support Voice Search
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == ACTIVITY_REQUEST_CODE_VOICE_SEARCH) {
            if (resultCode == RESULT_OK) {
                final ArrayList<String> matches = data.getStringArrayListExtra(
                        RecognizerIntent.EXTRA_RESULTS);
                if (matches.size() > 0) {
                    final String match = matches.get(0);
                    mSearchView.setText(match);
                } else {
                    Log.e(TAG, "Voice search - nothing heard");
                }
            } else {
                Log.e(TAG, "Voice search failed");
            }
        }
        super.onActivityResult(requestCode, resultCode, data);
    }
    */

    private void showDialpadFragment(boolean animate) {
        mDialpadFragment.setAdjustTranslationForAnimation(animate);
        final FragmentTransaction ft = getFragmentManager().beginTransaction();
        if (animate) {
            ft.setCustomAnimations(R.anim.slide_in, 0);
        } else {
            mDialpadFragment.setYFraction(0);
        }
        ft.show(mDialpadFragment);
        /// M: ALPS01260254 @{
        // Google issues caused by onSaveInstance().
        /* Original Code:
        ft.commit();
         */
        ft.commitAllowingStateLoss();
        /// @}
    }

    public void hideDialpadFragment(boolean animate, boolean clearDialpad) {
        if (mDialpadFragment == null) return;
        if (clearDialpad) {
            mDialpadFragment.clearDialpad();
        }
        if (!mDialpadFragment.isVisible()) return;
        mDialpadFragment.setAdjustTranslationForAnimation(animate);
        final FragmentTransaction ft = getFragmentManager().beginTransaction();
        if (animate) {
            ft.setCustomAnimations(0, R.anim.slide_out);
        }
        ft.hide(mDialpadFragment);
        ft.commit();
    }

    private void prepareSearchView() {
        mSearchViewContainer = findViewById(R.id.search_view_container);
        mSearchViewCloseButton = findViewById(R.id.search_close_button);
        mSearchViewCloseButton.setOnClickListener(this);
        /**M:original code:
        mVoiceSearchButton = findViewById(R.id.voice_search_button);
        mVoiceSearchButton.setOnClickListener(this);
         */
        mSearchView = (EditText) findViewById(R.id.search_view);
        mSearchView.addTextChangedListener(mPhoneSearchQueryTextListener);

        final String hintText = getString(R.string.dialer_hint_find_contact);

        // The following code is used to insert an icon into a CharSequence (copied from
        // SearchView)
        final SpannableStringBuilder ssb = new SpannableStringBuilder("   "); // for the icon
        ssb.append(hintText);
        final Drawable searchIcon = getResources().getDrawable(R.drawable.ic_ab_search);
        final int textSize = (int) (mSearchView.getTextSize() * 1.20);
        searchIcon.setBounds(0, 0, textSize, textSize);
        ssb.setSpan(new ImageSpan(searchIcon), 1, 2, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);

        mSearchView.setHint(ssb);
    }

    final AnimatorListener mHideListener = new AnimatorListenerAdapter() {
        @Override
        public void onAnimationEnd(Animator animation) {
            mSearchViewContainer.setVisibility(View.GONE);
        }
    };

    private boolean getInSearchUi() {
        return mInDialpadSearch || mInRegularSearch;
    }

    private void setNotInSearchUi() {
        mInDialpadSearch = false;
        mInRegularSearch = false;
    }

    private void hideDialpadAndSearchUi() {
        mSearchView.setText(null);
        hideDialpadFragment(false, true);
    }

    public void hideSearchBar() {
       hideSearchBar(true);
    }

    public void hideSearchBar(boolean shiftView) {
        if (shiftView) {
            mSearchViewContainer.animate().cancel();
            mSearchViewContainer.setAlpha(1);
            mSearchViewContainer.setTranslationY(0);
            mSearchViewContainer.animate().withLayer().alpha(0).translationY(-mSearchView.getHeight())
                    .setDuration(200).setListener(mHideListener);

            mFragmentsFrame.animate().withLayer()
                    .translationY(-mSearchViewContainer.getHeight()).setDuration(200).setListener(
                    new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            mBottomPaddingView.setVisibility(View.VISIBLE);
                            mFragmentsFrame.setTranslationY(0);
                            mActionBar.setVisibility(View.INVISIBLE);
                        }
                    });
        } else {
            mSearchViewContainer.setTranslationY(-mSearchView.getHeight());
            mActionBar.setVisibility(View.INVISIBLE);
        }
    }

    public void showSearchBar() {
        mSearchViewContainer.animate().cancel();
        mSearchViewContainer.setAlpha(0);
        mSearchViewContainer.setTranslationY(-mSearchViewContainer.getHeight());
        mSearchViewContainer.animate().withLayer().alpha(1).translationY(0).setDuration(200)
                .setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationStart(Animator animation) {
                        mSearchViewContainer.setVisibility(View.VISIBLE);
                        mActionBar.setVisibility(View.VISIBLE);
                    }
                });

        mFragmentsFrame.setTranslationY(-mSearchViewContainer.getHeight());
        mFragmentsFrame.animate().withLayer().translationY(0).setDuration(200)
                .setListener(
                        new AnimatorListenerAdapter() {
                            @Override
                            public void onAnimationStart(Animator animation) {
                                mBottomPaddingView.setVisibility(View.GONE);
                            }
                        });
    }


    public void setupFakeActionBarItems() {
        mMenuButton = findViewById(R.id.overflow_menu);
        if (mMenuButton != null) {
            mMenuButton.setOnClickListener(this);

            mOverflowMenu = new OverflowPopupMenu(DialtactsActivity.this, mMenuButton);
            final Menu menu = mOverflowMenu.getMenu();
            mOverflowMenu.inflate(R.menu.dialtacts_options);
            mOverflowMenu.setOnMenuItemClickListener(this);
            mMenuButton.setOnTouchListener(mOverflowMenu.getDragToOpenListener());
        }

        mCallHistoryButton = findViewById(R.id.call_history_button);
        // mCallHistoryButton.setMinimumWidth(fakeMenuItemWidth);
        mCallHistoryButton.setOnClickListener(this);

        mDialpadButton = findViewById(R.id.dialpad_button);
        // DialpadButton.setMinimumWidth(fakeMenuItemWidth);
        mDialpadButton.setOnClickListener(this);
    }

    public void setupFakeActionBarItemsForDialpadFragment() {
        final View callhistoryButton = findViewById(R.id.call_history_on_dialpad_button);
        callhistoryButton.setOnClickListener(this);
    }

    private void fixIntent(Intent intent) {
        // This should be cleaned up: the call key used to send an Intent
        // that just said to go to the recent calls list.  It now sends this
        // abstract action, but this class hasn't been rewritten to deal with it.
        if (Intent.ACTION_CALL_BUTTON.equals(intent.getAction())) {
            intent.setDataAndType(Calls.CONTENT_URI, Calls.CONTENT_TYPE);
            intent.putExtra("call_key", true);
            setIntent(intent);
        }
    }

    /**
     * Returns true if the intent is due to hitting the green send key (hardware call button:
     * KEYCODE_CALL) while in a call.
     *
     * @param intent the intent that launched this activity
     * @param recentCallsRequest true if the intent is requesting to view recent calls
     * @return true if the intent is due to hitting the green send key while in a call
     */
    private boolean isSendKeyWhileInCall(Intent intent, boolean recentCallsRequest) {
        // If there is a call in progress go to the call screen
        if (recentCallsRequest) {
            final boolean callKey = intent.getBooleanExtra("call_key", false);

            try {
                ITelephony phone = ITelephony.Stub.asInterface(ServiceManager.checkService("phone"));
                if (callKey && phone != null && phone.showCallScreen()) {
                    return true;
                }
            } catch (RemoteException e) {
                Log.e(TAG, "Failed to handle send while in call", e);
            }
        }

        return false;
    }

    /**
     * Sets the current tab based on the intent's request type
     *
     * @param intent Intent that contains information about which tab should be selected
     */
    private void displayFragment(Intent intent) {
        // If we got here by hitting send and we're in call forward along to the in-call activity
        boolean recentCallsRequest = Calls.CONTENT_TYPE.equals(intent.resolveType(
            getContentResolver()));
        if (isSendKeyWhileInCall(intent, recentCallsRequest)) {
            finish();
            return;
        }

        if (mDialpadFragment != null) {
            final boolean phoneIsInUse = phoneIsInUse();
            if (phoneIsInUse || isDialIntent(intent)) {
                mDialpadFragment.setStartedFromNewIntent(true);
                if (phoneIsInUse && !mDialpadFragment.isVisible()) {
                    mInCallDialpadUp = true;
                }
                showDialpadFragment(false);
            }
        }
    }

    @Override
    public void onNewIntent(Intent newIntent) {
        /// M: ALPS01233269 @{
        if (returnToInCallScreen(newIntent)){
            LogUtils.d(TAG, "onNewIntent, returnToInCallScreen");
            return;
        }
        /// @}
        setIntent(newIntent);
        fixIntent(newIntent);
        displayFragment(newIntent);
        final String action = newIntent.getAction();

        invalidateOptionsMenu();
    }

    /** Returns true if the given intent contains a phone number to populate the dialer with */
    private boolean isDialIntent(Intent intent) {
        final String action = intent.getAction();
        if (Intent.ACTION_DIAL.equals(action) || ACTION_TOUCH_DIALER.equals(action)) {
            return true;
        }
        if (Intent.ACTION_VIEW.equals(action)) {
            final Uri data = intent.getData();
            if (data != null && CallUtil.SCHEME_TEL.equals(data.getScheme())) {
                return true;
            }
        }
        /** M: New Feature Easy Porting @{ */
        return ExtensionManager.getInstance().getDialtactsExtension().checkComponentName(intent,
                ContactPluginDefault.COMMD_FOR_OP01);
        /** @} */
       
    }

    /**
     * Returns an appropriate call origin for this Activity. May return null when no call origin
     * should be used (e.g. when some 3rd party application launched the screen. Call origin is
     * for remembering the tab in which the user made a phone call, so the external app's DIAL
     * request should not be counted.)
     */
    public String getCallOrigin() {
        return !isDialIntent(getIntent()) ? CALL_ORIGIN_DIALTACTS : null;
    }

    /**
     * Retrieves the filter text stored in {@link #setupFilterText(Intent)}.
     * This text originally came from a FILTER_CONTACTS_ACTION intent received
     * by this activity. The stored text will then be cleared after after this
     * method returns.
     *
     * @return The stored filter text
     */
    public String getAndClearFilterText() {
        String filterText = mFilterText;
        mFilterText = null;
        return filterText;
    }

    /**
     * Stores the filter text associated with a FILTER_CONTACTS_ACTION intent.
     * This is so child activities can check if they are supposed to display a filter.
     *
     * @param intent The intent received in {@link #onNewIntent(Intent)}
     */
    private void setupFilterText(Intent intent) {
        // If the intent was relaunched from history, don't apply the filter text.
        if ((intent.getFlags() & Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY) != 0) {
            return;
        }
        String filter = intent.getStringExtra(UI.FILTER_TEXT_EXTRA_KEY);
        if (filter != null && filter.length() > 0) {
            mFilterText = filter;
        }
    }

    private final PhoneFavoriteFragment.Listener mPhoneFavoriteListener =
            new PhoneFavoriteFragment.Listener() {
        @Override
        public void onContactSelected(Uri contactUri) {
            PhoneNumberInteraction.startInteractionForPhoneCall(
                        DialtactsActivity.this, contactUri, getCallOrigin());
        }

        @Override
        public void onCallNumberDirectly(String phoneNumber) {
            Intent intent = CallUtil.getCallIntent(phoneNumber, getCallOrigin());
            startActivity(intent);
        }
    };

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.dialtacts_options, menu);
        // set up intents and onClick listeners
        final MenuItem callSettingsMenuItem = menu.findItem(R.id.menu_call_settings);
        callSettingsMenuItem.setIntent(DialtactsActivity.getCallSettingsIntent());

        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        final MenuItem callSettingsMenuItem = menu.findItem(R.id.menu_call_settings);
        callSettingsMenuItem.setVisible(ViewConfiguration.get(this).hasPermanentMenuKey());

        /// M: Fix CR: ALPS01266253, HardWare menu just show common menu items @{
        final MenuItem clearFrequents = menu.findItem(R.id.menu_clear_frequents);
        clearFrequents.setVisible(false);
        /// M: @}

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        onMenuItemClick(item);
        return super.onOptionsItemSelected(item);
    }

/*
    @Override
    public void startSearch(String initialQuery, boolean selectInitialQuery,
            Bundle appSearchData, boolean globalSearch) {
        if (mRegularSearchFragment != null && mRegularSearchFragment.isAdded() && !globalSearch) {
            if (mInSearchUi) {
                if (mSearchView.hasFocus()) {
                    showInputMethod(mSearchView.findFocus());
                } else {
                    mSearchView.requestFocus();
                }
            } else {
                enterSearchUi();
            }
        } else {
            super.startSearch(initialQuery, selectInitialQuery, appSearchData, globalSearch);
        }
    }*/

    private void showInputMethod(View view) {
        final InputMethodManager imm = (InputMethodManager) getSystemService(
                Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.showSoftInput(view, 0);
        }
    }

    private void hideInputMethod(View view) {
        final InputMethodManager imm = (InputMethodManager) getSystemService(
                Context.INPUT_METHOD_SERVICE);
        if (imm != null && view != null) {
            imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
        }
    }

    /**
     * Shows the search fragment
     */
    private void enterSearchUi(boolean smartDialSearch, String query) {
        if (getFragmentManager().isDestroyed()) {
            // Weird race condition where fragment is doing work after the activity is destroyed
            // due to talkback being on (b/10209937). Just return since we can't do any
            // constructive here.
            return;
        }

        if (DEBUG) {
            Log.d(TAG, "Entering search UI - smart dial " + smartDialSearch);
        }

        final FragmentTransaction transaction = getFragmentManager().beginTransaction();
        transaction.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE);

        SearchFragment fragment;
        if (mInDialpadSearch) {
            transaction.remove(mSmartDialSearchFragment);
        } else if (mInRegularSearch) {
            transaction.remove(mRegularSearchFragment);
        } else {
            /** M: New Feature Landspace in dialer @{ */
            // original code: transaction.remove(mPhoneFavoriteFragment);
            if (!OrientationUtil.isLandscape(this)) {
                transaction.remove(mPhoneFavoriteFragment);
            }
            /** @} */
            
        }

        final String tag;
        if (smartDialSearch) {
            tag = TAG_SMARTDIAL_SEARCH_FRAGMENT;
        } else {
            tag = TAG_REGULAR_SEARCH_FRAGMENT;
        }
        mInDialpadSearch = smartDialSearch;
        mInRegularSearch = !smartDialSearch;

        fragment = (SearchFragment) getFragmentManager().findFragmentByTag(tag);
        if (fragment == null) {
            if (smartDialSearch) {
                fragment = new SmartDialSearchFragment();
            } else {
                fragment = new RegularSearchFragment();
            }
        }
        
        /**
         * M: New Feature Landspace in dialer
         * 
         * Original Code:
         * transaction.replace(R.id.dialtacts_frame, fragment, tag);
         * @{
         */
        int anchorViewId = R.id.dialtacts_frame;
        if (OrientationUtil.isLandscape(this)) {
            Log.i(TAG, "Tablet landscape support");
            anchorViewId = R.id.search_frame;
        }
        transaction.replace(anchorViewId, fragment, tag);
        /** M: end @}*/
        transaction.addToBackStack(null);
        fragment.setQueryString(query, false);
        transaction.commit();
    }

    /**
     * Hides the search fragment
     */
    private void exitSearchUi() {
        LogUtils.i(TAG, "exitSearchUi mSavedInstance:" + mSavedInstance);
        // See related bug in enterSearchUI();
        if (getFragmentManager().isDestroyed() && mSavedInstance) {
            return;
        }
        // Go all the way back to the favorites fragment, regardless of how many times we
        // transitioned between search fragments
        getFragmentManager().popBackStack(0, FragmentManager.POP_BACK_STACK_INCLUSIVE);
        setNotInSearchUi();
    }

    /** Returns an Intent to launch Call Settings screen */
    public static Intent getCallSettingsIntent() {
        final Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.setClassName(PHONE_PACKAGE, CALL_SETTINGS_CLASS_NAME);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        return intent;
    }

    @Override
    public void onBackPressed() {
        if (mDialpadFragment != null && mDialpadFragment.isVisible()) {
            hideDialpadFragment(true, false);
        } else if (getInSearchUi()) {
            mSearchView.setText(null);
            mDialpadFragment.clearDialpad();
        } else if (isTaskRoot()) {
            // Instead of stopping, simply push this to the back of the stack.
            // This is only done when running at the top of the stack;
            // otherwise, we have been launched by someone else so need to
            // allow the user to go back to the caller.
            moveTaskToBack(false);
        } else {
            super.onBackPressed();
        }
    }

    @Override
    public void onDialpadQueryChanged(String query) {
        final String normalizedQuery = SmartDialNameMatcher.normalizeNumber(query,
                SmartDialNameMatcher.LATIN_SMART_DIAL_MAP);
        if (!TextUtils.equals(mSearchView.getText(), normalizedQuery)) {
            LogUtils.d(TAG, "onDialpadQueryChanged - new query: " + query);

            /// M: Fix CR: ALPS01260630, Time lag issues @{
            /**
             * Original Code:if (mDialpadFragment == null || (!mDialpadFragment.isVisible()) {
             */
            if (mDialpadFragment == null || (!mDialpadFragment.isVisible() && !mFirstQuery)) {
              /// M: @}
                // This callback can happen if the dialpad fragment is recreated because of
                // activity destruction. In that case, don't update the search view because
                // that would bring the user back to the search fragment regardless of the
                // previous state of the application. Instead, just return here and let the
                // fragment manager correctly figure out whatever fragment was last displayed.
                LogUtils.d(TAG, "onDialpadQueryChanged, NOT trigger dialer seach");
                return;
            }
            mSearchView.setText(normalizedQuery);
        }
    }

    @Override
    public void onListFragmentScrollStateChange(int scrollState) {
        if (scrollState == OnScrollListener.SCROLL_STATE_TOUCH_SCROLL) {
            hideDialpadFragment(true, false);
            hideInputMethod(getCurrentFocus());
        }
    }

    @Override
    public void onDialpadFragmentStarted() {
        setupFakeActionBarItemsForDialpadFragment();
    }

    private boolean phoneIsInUse() {
        final TelephonyManager tm = (TelephonyManager) getSystemService(
                Context.TELEPHONY_SERVICE);
        return tm.getCallState() != TelephonyManager.CALL_STATE_IDLE;
    }

    @Override
    public void onShowAllContacts() {
        final Intent intent = new Intent(this, AllContactsActivity.class);
        /// M: Make sure only launch one instance of AllContactsActivity, Fix CR: ALPS01364039 @{
        intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        /// M: @}
        startActivity(intent);
    }

    public static Intent getAddNumberToContactIntent(CharSequence text) {
        final Intent intent = new Intent(Intent.ACTION_INSERT_OR_EDIT);
        intent.putExtra(Intents.Insert.PHONE, text);
        intent.setType(Contacts.CONTENT_ITEM_TYPE);
        return intent;
    }

    public static Intent getInsertContactWithNameIntent(CharSequence text) {
        final Intent intent = new Intent(Intent.ACTION_INSERT, Contacts.CONTENT_URI);
        intent.putExtra(Intents.Insert.NAME, text);
        return intent;
    }

    public boolean onPeopleMenuItemSelected() {
        final Intent intent = new Intent("com.android.contacts.action.LIST_DEFAULT");
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        return true;
    }


///////////////////////////////////////////////
// M: MTK code
///////////////////////////////////////////
    /// Add for ALPS01244227
    // Identifier for the "Add Call" intent extra.
    static final String ADD_CALL_MODE_KEY = "add_call_mode";
    /**
     * M: [KK] stub for migration, FIXME: remove it.
     * @return
     */
    public int getCurrentFragmentId() {
        return 1;  // 1 means currently in calllog tab
    }

    /// M: ALPS01233269 @{
    // Change Feature in KK. If there's already an active call, bring up InCallScreen and finish self.
    private boolean mReturnToInCallScreen = false;

    private boolean returnToInCallScreen(Intent intent) {
        mReturnToInCallScreen = false;
        if (intent != null) {
            final String action = intent.getAction();
            // ALPS01260196 dial with a number from "3rd APP", should not return to InCallScreen directly.
            if (Intent.ACTION_DIAL.equals(action) || Intent.ACTION_VIEW.equals(action)) {
                Uri uri = intent.getData();
                if (uri != null) {
                    if (CallUtil.SCHEME_TEL.equals(uri.getScheme())) {
                        LogUtils.d(TAG, "returnToInCallScreen intent: " + uri.getSchemeSpecificPart());
                        return false;
                    }
                }
            }

            final boolean isFromAddCall = intent.getBooleanExtra(ADD_CALL_MODE_KEY, false);
            if (!isFromAddCall
                    && (Intent.ACTION_DIAL.equals(action) || Intent.ACTION_VIEW.equals(action) || Intent.ACTION_MAIN.equals(action))) {
                if (phoneIsInUse()) {
                    try {
                        ITelephony phone = ITelephony.Stub.asInterface(ServiceManager.checkService("phone"));
                        if (phone != null) {
                            phone.showCallScreenWithDialpad(false);
                        }
                    } catch (RemoteException e) {
                        Log.w(TAG, "returnToInCallScreen failed", e);
                    }
                    mReturnToInCallScreen = true;
                }
            }
        }
        Log.d(TAG, "returnToInCallScreen() " + mReturnToInCallScreen);
        return mReturnToInCallScreen;
    }
    /// @}

    //---------------------------MTK-------------------------------------
    /// M: Support for MTK-DialerSearch @{
    private ContactsPreferences mContactsPrefs;
    /** M: ALPS01378594 Used for mark #onSaveInstanceState is called. */
    private boolean mSavedInstance = false;

    @Override
    protected void onStart() {
        super.onStart();
        mSavedInstance = false;
        LogUtils.d(TAG, "MTK-DialerSearch, onStart -- setDatasforDialersearch()");

        mContactsPrefs = new ContactsPreferences(this);
        final DialerSearchHelper dialerSearchHelper = DialerSearchHelperManager.getDialerSearchHelper(this, mContactsPrefs);
        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                dialerSearchHelper.dialerSearchInit();
            }
        };
        new Thread(runnable).start();
    }

    /// M: Fix CR: ALPS01260630, time lag issues @{
    private boolean mFirstQuery = false;
    /// M: @}

    /** M: New Feature Landspace in dialer @{ */
    private static final String TAG_SEARCH_FRAGMENT = "search_frame";
    private NoSearchResultFragment mEmptyFragment; 
    /** @} */

    /// M: add for CR ALPS01380190.@{
    /**
     * For sometimes UI can't return from SmartDialSearchFragment to PhoneFavoriteFragment correctly.
     */
    private void ensureExitSearchUi() {
        if (mSmartDialSearchFragment != null && mSmartDialSearchFragment.isVisible()) {
            final FragmentTransaction ft = getFragmentManager().beginTransaction();
            final PhoneFavoriteFragment phoneFavoriteFragment = (PhoneFavoriteFragment) getFragmentManager().findFragmentByTag(TAG_FAVORITES_FRAGMENT);
            ft.remove(mSmartDialSearchFragment);
            if (phoneFavoriteFragment != null) {
                ft.replace(R.id.dialtacts_frame, phoneFavoriteFragment, TAG_FAVORITES_FRAGMENT);
            }
            ft.commit();
            LogUtils.d(TAG, "ensureExitSearchUi() done!");
        }
    }
    /// @}

}
