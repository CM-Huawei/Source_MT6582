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

import android.content.ContentUris;
import android.content.Context;
import android.content.Loader;
import android.database.Cursor;
import android.net.Uri;
import android.provider.ContactsContract;
import android.provider.ContactsContract.CommonDataKinds.Callable;
import android.telephony.PhoneNumberUtils;
import android.util.Log;

import com.android.contacts.common.list.ContactListItemView;
import com.android.contacts.common.list.PhoneNumberListAdapter;
import com.android.contacts.common.list.PhoneNumberListAdapter.PhoneQuery;
import com.android.dialer.dialpad.SmartDialCursorLoader;
import com.android.dialer.dialpad.SmartDialNameMatcher;
import com.android.dialer.dialpad.SmartDialPrefix;
import com.android.dialer.dialpad.SmartDialMatchPosition;

import com.mediatek.dialer.DialerSearchCursorLoader;
import com.mediatek.dialer.util.DialerUtils;
import com.mediatek.dialer.util.LogUtils;

import java.util.ArrayList;

/**
 * List adapter to display the SmartDial search results.
 */
public class SmartDialNumberListAdapter extends DialerPhoneNumberListAdapter {

    private static final String TAG = SmartDialNumberListAdapter.class.getSimpleName();
    private static final boolean DEBUG = false;

    private SmartDialNameMatcher mNameMatcher;

    public SmartDialNumberListAdapter(Context context) {
        super(context);
        if (DEBUG) {
            Log.v(TAG, "Constructing List Adapter");
        }
    }

    /**
     * Sets query for the SmartDialCursorLoader.
     */
    /// M: Support MTK-DialerSearch @{
    /**
     * 
     * original code: public void configureLoader(SmartDialCursorLoader loader)
     * {
     */
    public void configureLoader(Loader<Cursor> loader) {

        LogUtils.d(TAG, "MTK-DialerSearch, configureLoader, getQueryString: " + getQueryString());

        if (DEBUG) {
            Log.v(TAG, "Configure Loader with query" + getQueryString());
        }

        if (DialerUtils.isDialerSearchEnabled()) {
            DialerSearchCursorLoader dialerSearchLoader = null;

            if (loader instanceof DialerSearchCursorLoader) {
                dialerSearchLoader = (DialerSearchCursorLoader) loader;
            } else {
                Log.w(TAG, "MTK-DiaerSearch, Not DialerSearchCursorLoader");
            }

            if (getQueryString() == null) {
                dialerSearchLoader.configureQuery("", true);
            } else {
                dialerSearchLoader.configureQuery(getQueryString(), true);
            }
        } else {
            SmartDialCursorLoader smartDialCursorLoader = null;

            if (loader instanceof DialerSearchCursorLoader) {
                smartDialCursorLoader = (SmartDialCursorLoader) loader;
            } else {
                Log.w(TAG, "MTK-DiaerSearch, Not SmartDialCursorLoader");
            }

            if (getQueryString() == null) {
                mNameMatcher = new SmartDialNameMatcher("", SmartDialPrefix.getMap());
                smartDialCursorLoader.configureQuery("");
            } else {
                smartDialCursorLoader.configureQuery(getQueryString());
                mNameMatcher = new SmartDialNameMatcher(PhoneNumberUtils.normalizeNumber(getQueryString()),
                        SmartDialPrefix.getMap());
            }
        }
    }

    /// M: @}

    /**
     * M: Support MTK-DialerSearch, hide google default behaviour.
     */
//    /**
//     * Sets highlight options for a List item in the SmartDial search results.
//     * @param view ContactListItemView where the result will be displayed.
//     * @param cursor Object containing information of the associated List item.
//     */
//    @Override
//    protected void setHighlight(ContactListItemView view, Cursor cursor) {
//        view.clearHighlightSequences();
//        /// M: Support MTK-DialerSearch @{
//        if (mNameMatcher == null) {
//            LogUtils.d(TAG, "MTK-DialerSearch, mNameMatcher is NULL." + getQueryString());
//
//            if (getQueryString() == null) {
//                mNameMatcher = new SmartDialNameMatcher("", SmartDialPrefix.getMap());
//            } else {
//                mNameMatcher = new SmartDialNameMatcher(PhoneNumberUtils.normalizeNumber(getQueryString()),
//                        SmartDialPrefix.getMap());
//            }
//        }
//        /// M: @}
//
//        if (mNameMatcher.matches(cursor.getString(PhoneQuery.DISPLAY_NAME))) {
//            final ArrayList<SmartDialMatchPosition> nameMatches = mNameMatcher.getMatchPositions();
//            for (SmartDialMatchPosition match : nameMatches) {
//                view.addNameHighlightSequence(match.start, match.end);
//                if (DEBUG) {
//                    Log.v(TAG,
//                            cursor.getString(PhoneQuery.DISPLAY_NAME) + " " + mNameMatcher.getQuery() + " "
//                                    + String.valueOf(match.start));
//                }
//            }
//        }
//
//        final SmartDialMatchPosition numberMatch = mNameMatcher.matchesNumber(cursor.getString(PhoneQuery.PHONE_NUMBER));
//        if (numberMatch != null) {
//            view.addNumberHighlightSequence(numberMatch.start, numberMatch.end);
//        }
//    }

    /**
     * Gets Uri for the list item at the given position.
     * @param position Location of the data of interest.
     * @return Data Uri of the entry.
     */

    @Override
    public Uri getDataUri(int position) {
        Cursor cursor = ((Cursor)getItem(position));
        if (cursor != null) {
            /// M: Add for support dialer list item @{
            /**Original code:
             *  long id = cursor.getLong(PhoneQuery.PHONE_ID);
                return ContentUris.withAppendedId(ContactsContract.Data.CONTENT_URI, id);
             */
            long id = cursor.getLong(PHONE_DATA_ID_INDEX);
            LogUtils.d(TAG, "SmartDialNumberListAdatper: DataId:" + id);

            if (id < 0) {
                return null;
            } else {
                return ContentUris.withAppendedId(ContactsContract.Data.CONTENT_URI, id);
            }
            /// M: @}
        } else {
            Log.w(TAG, "Cursor was null in getDataUri() call. Returning null instead.");
            return null;
        }
    }

    /// --------------------------------MTK----------------------------

    /// M: Add for support dialer list item @{
    private final int PHONE_NUMBER_INDEX = 14;
    private final int PHONE_DATA_ID_INDEX = 2;
    @Override
    public String getPhoneNumber(int position) {
        Cursor cursor = ((Cursor)getItem(position));
        if (cursor != null) {
            String phoneNumber = cursor.getString(PHONE_NUMBER_INDEX);
            LogUtils.d(TAG, "SmartDialNumberListAdatper: phoneNumber:" + phoneNumber);

            return phoneNumber;
        } else {
            Log.w(TAG, "Cursor was null in getPhoneNumber() call. Returning null instead.");
            return null;
        }
    }
    /// M: @}
}
