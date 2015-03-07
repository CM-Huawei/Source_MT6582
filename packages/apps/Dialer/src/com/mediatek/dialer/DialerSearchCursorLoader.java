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

package com.mediatek.dialer;

import java.util.ArrayList;

import android.content.AsyncTaskLoader;
import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.util.Log;

import com.android.contacts.common.list.PhoneNumberListAdapter.PhoneQuery;
import com.android.contacts.common.preference.ContactsPreferences;
import com.android.dialer.dialpad.SmartDialNameMatcher;
import com.android.dialer.dialpad.SmartDialPrefix;

import com.mediatek.dialer.dialpad.DialerSearchUtils;
import com.mediatek.dialer.util.LogUtils;

/**
 * Implements a Loader<Cursor> class to asynchronously load SmartDial search results.
 */
public class DialerSearchCursorLoader extends AsyncTaskLoader<Cursor> {

    private final String TAG = "DialerSearchCursorLoader";
    private final boolean DEBUG = true;
    private final Context mContext;
    private Cursor mCursor;
    private String mQuery;
    private boolean mUseCallableUri = false;
    private boolean mRegularSearch = false;

    private ContactsPreferences mContactsPrefs;
    private final int CONTACT_ID = 1;
    private final int CONTACT_DATA_ID = 2;
    private final int INDICATE_PHONE_OR_SIM = 9;
    private final int CONTACT_PHOTO_ID = 11;
    private final int CONTACT_DISPLAY_NAME = 13;
    private final int CONTACT_NUMBER = 14;
    private final int CONTACT_LOOKUP = 15;

    public DialerSearchCursorLoader(Context context, boolean useCallable) {
        super(context);
        mContext = context;
        mUseCallableUri = useCallable;
    }

    /**
     * Configures the query string to be used to find SmartDial matches.
     * @param query The query string user typed.
     */
    public void configureQuery(String query, boolean isSmartQuery) {

        LogUtils.d(TAG, "MTK-DialerSearch, Configure new query to be " + query);

        if (isSmartQuery) {
            mQuery = SmartDialNameMatcher.normalizeNumber(query, SmartDialPrefix.getMap());
        } else {
            mQuery = query;
            if (DialerSearchUtils.isInValidDialpadString(mQuery)) {
                mRegularSearch = true;
            }
        }
        mContactsPrefs = new ContactsPreferences(mContext);
    }

    /**
     * Queries the Contacts database and loads results in background.
     * @return Cursor of contacts that matches the SmartDial query.
     */
    @Override
    public Cursor loadInBackground() {

        LogUtils.d(TAG, "MTK-DialerSearch, Load in background. mQuery: " + mQuery);

        final DialerSearchHelper dialerSearchHelper = DialerSearchHelperManager.getDialerSearchHelper(mContext,
                mContactsPrefs);
        Cursor cursor = null;
        if (mRegularSearch) {
            cursor = dialerSearchHelper.getRegularDialerSearchResults(mQuery, mUseCallableUri);
        } else {
            cursor = dialerSearchHelper.getDialerSearchResults(mQuery);
        }
        if (cursor != null) {
            LogUtils.d(TAG, "MTK-DialerSearch, loadInBackground, result.getCount: " + cursor.getCount());

            return cursor;
        } else {
            Log.w(TAG, "MTK-DialerSearch, ----cursor is null----");
            return null;
        }

        // Following code maybe useful in the future
//        final MatrixCursor result = new MatrixCursor(PhoneQuery.PROJECTION_PRIMARY);
//        Object[] row = new Object[PhoneQuery.PROJECTION_PRIMARY.length];
//        if (cursor != null) {
//            cursor.moveToPosition(-1);
//            while (cursor.moveToNext()) {
//                row[PhoneQuery.PHONE_ID] = cursor.getLong(CONTACT_DATA_ID);
//                row[PhoneQuery.PHONE_NUMBER] = cursor.getString(CONTACT_NUMBER);
//                row[PhoneQuery.PHONE_INDICATE_PHONE_SIM_COLUMN_INDEX] = cursor.getInt(INDICATE_PHONE_OR_SIM);
//                row[PhoneQuery.CONTACT_ID] = cursor.getLong(CONTACT_ID);
//                row[PhoneQuery.LOOKUP_KEY] = cursor.getString(CONTACT_LOOKUP);
//                row[PhoneQuery.PHOTO_ID] = cursor.getLong(CONTACT_PHOTO_ID);
//                if (cursor.getString(CONTACT_DISPLAY_NAME) != null) {
//                    row[PhoneQuery.DISPLAY_NAME] = cursor.getString(CONTACT_DISPLAY_NAME);
//                } else {
//                    row[PhoneQuery.DISPLAY_NAME] = cursor.getString(CONTACT_NUMBER);
//                }
//                result.addRow(row);
//            }
//        } else {
//            Log.w(TAG, "MTK-DialerSearch, ----cursor is null----");
//        }
//        if (cursor != null) {
//            cursor.close();
//            cursor = null;
//        }
    }

    @Override
    public void deliverResult(Cursor cursor) {
        if (isReset()) {
            /** The Loader has been reset; ignore the result and invalidate the data. */
            releaseResources(cursor);
            return;
        }

        /** Hold a reference to the old data so it doesn't get garbage collected. */
        Cursor oldCursor = mCursor;
        mCursor = cursor;

        if (isStarted()) {
            /** If the Loader is in a started state, deliver the results to the client. */
            super.deliverResult(cursor);
        }

        /** Invalidate the old data as we don't need it any more. */
        if (oldCursor != null && oldCursor != cursor) {
            releaseResources(oldCursor);
        }
    }

    @Override
    protected void onStartLoading() {
        if (mCursor != null) {
            /** Deliver any previously loaded data immediately. */
            deliverResult(mCursor);
        }
        if (mCursor == null) {
            /** Force loads every time as our results change with queries. */
            forceLoad();
        }
    }

    @Override
    protected void onStopLoading() {
        /** The Loader is in a stopped state, so we should attempt to cancel the current load. */
        cancelLoad();
    }

    @Override
    protected void onReset() {
        /** Ensure the loader has been stopped. */
        onStopLoading();

        /** Release all previously saved query results. */
        if (mCursor != null) {
            releaseResources(mCursor);
            mCursor = null;
        }
    }

    @Override
    public void onCanceled(Cursor cursor) {
        // modify for sometimes loader cannot return correct cursor issue.
        if (mCursor != cursor) {
            deliverResult(cursor);
            LogUtils.d(TAG, "MTK-DialerSearch, onCanceled(). force deliverResult cursor!");
            return;
        }
        super.onCanceled(cursor);

        /** The load has been canceled, so we should release the resources associated with 'data'.*/
        releaseResources(cursor);
    }

    private void releaseResources(Cursor cursor) {
        if (cursor != null) {
            cursor.close();
            cursor = null;
        }
    }
}
