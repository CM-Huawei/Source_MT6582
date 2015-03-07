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
package com.android.dialer.list;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Handler;
import android.os.Message;
import android.provider.ContactsContract;
import android.text.TextUtils;
import android.util.Log;
import android.widget.AbsListView;
import android.widget.AbsListView.OnScrollListener;
import android.widget.Toast;

import com.android.contacts.common.list.ContactEntryListAdapter;
import com.android.contacts.common.list.ContactListItemView;
import com.android.contacts.common.list.OnPhoneNumberPickerActionListener;
import com.android.contacts.common.list.PhoneNumberPickerFragment;
import com.android.contacts.common.preference.ContactsPreferences;
import com.android.dialer.DialtactsActivity;
import com.android.dialer.R;
import com.android.dialer.dialpad.DialpadFragment;
import com.android.dialer.dialpad.SmartDialNameMatcher;
import com.android.dialer.dialpad.SmartDialPrefix;
import com.android.dialer.list.OnListFragmentScrolledListener;
import com.mediatek.dialer.DialerSearchHelper;
import com.mediatek.dialer.DialerSearchHelperManager;
import com.mediatek.dialer.util.LogUtils;

public class SearchFragment extends PhoneNumberPickerFragment {

    private final String TAG = "SearchFragment";

    private OnListFragmentScrolledListener mActivityScrollListener;

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);

        setQuickContactEnabled(true);
        setDarkTheme(false);
        setPhotoPosition(ContactListItemView.getDefaultPhotoPosition(true /* opposite */));
        setUseCallableUri(true);

        try {
            mActivityScrollListener = (OnListFragmentScrolledListener) activity;
        } catch (ClassCastException e) {
            throw new ClassCastException(activity.toString()
                    + " must implement OnListFragmentScrolledListener");
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        if (isSearchMode()) {
            getAdapter().setHasHeader(0, false);
        }
        getListView().setOnScrollListener(new OnScrollListener() {
            @Override
            public void onScrollStateChanged(AbsListView view, int scrollState) {
                mActivityScrollListener.onListFragmentScrollStateChange(scrollState);
            }

            @Override
            public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount,
                    int totalItemCount) {
            }
        });

        /// M: Fix CR: ALPS01271382, Add monitor of PHB state @{
        mContactsPrefs = new ContactsPreferences(getActivity());
        getActivity().getContentResolver().registerContentObserver(
                ContactsContract.AUTHORITY_URI, true, mContactsObserver);
        /// M: @}
    }

    @Override
    protected void setSearchMode(boolean flag) {
        super.setSearchMode(flag);
        // This hides the "All contacts with phone numbers" header in the search fragment
        final ContactEntryListAdapter adapter = getAdapter();
        if (adapter != null) {
            adapter.setHasHeader(0, false);
        }
    }

    @Override
    protected ContactEntryListAdapter createListAdapter() {
        DialerPhoneNumberListAdapter adapter = new DialerPhoneNumberListAdapter(getActivity());
        adapter.setDisplayPhotos(true);
        adapter.setUseCallableUri(super.usesCallableUri());
        return adapter;
    }

    @Override
    protected void onItemClick(int position, long id) {
        final DialerPhoneNumberListAdapter adapter = (DialerPhoneNumberListAdapter) getAdapter();
        final int shortcutType = adapter.getShortcutTypeFromPosition(position);
        LogUtils.d(TAG, "SearchFragment: " + shortcutType);

        if (shortcutType == DialerPhoneNumberListAdapter.SHORTCUT_INVALID) {
            super.onItemClick(position, id);
        } else if (shortcutType == DialerPhoneNumberListAdapter.SHORTCUT_DIRECT_CALL) {
            final OnPhoneNumberPickerActionListener listener =
                    getOnPhoneNumberPickerListener();
            if (listener != null) {
                listener.onCallNumberDirectly(getQueryString());
            }
        } else if (shortcutType == DialerPhoneNumberListAdapter.SHORTCUT_ADD_NUMBER_TO_CONTACTS) {
            String number = adapter.getFormattedQueryString();
            /// M: Fix CR: ALPS01264992, Add protection of null number @{
            if (TextUtils.isEmpty(number)) {
                number = SmartDialNameMatcher.normalizeNumber(getQueryString(), SmartDialPrefix.getMap());
                LogUtils.d(TAG, "number: " + number);
            }
            /// M: @}
            final Intent intent = DialtactsActivity.getAddNumberToContactIntent(number);
            startActivityWithErrorToast(intent);
        }
    }

    private void startActivityWithErrorToast(Intent intent) {
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException e) {
            Toast toast = Toast.makeText(getActivity(), R.string.add_contact_not_available,
                    Toast.LENGTH_SHORT);
            toast.show();
        }
    }

    //------------------------------MTK--------------------------------
    /// M: Fix CR: ALPS01271382, Add monitor of PHB state @{
    private ContactsPreferences mContactsPrefs;
    private static final int MTK_MSG_DB_CHANGED = 1252;
    private static final long WAIT_CURSOR_DELAY_TIME = 500;
    private final ContentObserver mContactsObserver = new ContactsObserver();

    private Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
            case MTK_MSG_DB_CHANGED:
                forceReloadData();
                break;
            default:
                break;
            }
        }
    };

    @Override
    public void onStop() {
        super.onStop();
        getActivity().getContentResolver().unregisterContentObserver(mContactsObserver);
    }

    private class ContactsObserver extends ContentObserver {
        public ContactsObserver() {
            super(new Handler());
        }

        @Override
        public void onChange(boolean selfChange) {
            mHandler.sendMessageDelayed(mHandler.obtainMessage(MTK_MSG_DB_CHANGED), WAIT_CURSOR_DELAY_TIME);
            LogUtils.d(TAG, "Should update datas");
        }
    }

    private void forceReloadData() {
        final DialerSearchHelper dialerSearchHelper = DialerSearchHelperManager.getDialerSearchHelper(getActivity(), mContactsPrefs);
        dialerSearchHelper.setDatasforDialersearch();
        reloadData();
        mHandler.removeMessages(MTK_MSG_DB_CHANGED);
        LogUtils.d(TAG, "Update data");
    }
    /// M: @}
}
