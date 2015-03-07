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

import java.util.ArrayList;

import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.ContactsContract;
import android.telephony.PhoneNumberUtils;
import android.text.TextUtils;
import android.util.Log;

import com.android.contacts.common.list.DirectoryPartition;
import com.android.contacts.common.list.PhoneNumberListAdapter;
import com.android.contacts.common.list.ContactListItemView;
import com.android.contacts.common.list.PhoneNumberListAdapter.PhoneQuery;
import com.android.dialer.calllog.ContactInfo;
import com.android.dialer.dialpad.SmartDialMatchPosition;
import com.android.dialer.dialpad.SmartDialNameMatcher;
import com.android.dialer.dialpad.SmartDialPrefix;
import com.android.dialer.service.CachedNumberLookupService;
import com.android.dialer.service.CachedNumberLookupService.CachedContactInfo;

import com.mediatek.dialer.DialerSearchCursorLoader;
import com.mediatek.dialer.util.DialerUtils;
import com.mediatek.dialer.util.LogUtils;
/**
 * List adapter to display regular search results.
 */
public class RegularSearchListAdapter extends DialerPhoneNumberListAdapter {

    public RegularSearchListAdapter(Context context) {
        super(context);
    }

    public CachedContactInfo getContactInfo(
            CachedNumberLookupService lookupService, int position) {
        ContactInfo info = new ContactInfo();
        CachedContactInfo cacheInfo = lookupService.buildCachedContactInfo(info);
        final Cursor item = (Cursor) getItem(position);
        if (item != null) {
            info.name = item.getString(PhoneQuery.DISPLAY_NAME);
            info.type = item.getInt(PhoneQuery.PHONE_TYPE);
            info.label = item.getString(PhoneQuery.PHONE_LABEL);
            info.number = item.getString(PhoneQuery.PHONE_NUMBER);
            final String photoUriStr = item.getString(PhoneQuery.PHOTO_URI);
            info.photoUri = photoUriStr == null ? null : Uri.parse(photoUriStr);

            cacheInfo.setLookupKey(item.getString(PhoneQuery.LOOKUP_KEY));

            final int partitionIndex = getPartitionForPosition(position);
            final DirectoryPartition partition =
                (DirectoryPartition) getPartition(partitionIndex);
            final long directoryId = partition.getDirectoryId();
            final String sourceName = partition.getLabel();
            if (isExtendedDirectory(directoryId)) {
                cacheInfo.setExtendedSource(sourceName, directoryId);
            } else {
                cacheInfo.setDirectorySource(sourceName, directoryId);
            }
        }
        return cacheInfo;
    }

    @Override
    public void setQueryString(String queryString) {
        final boolean showNumberShortcuts = !TextUtils.isEmpty(getFormattedQueryString());
        setShortcutEnabled(SHORTCUT_DIRECT_CALL, showNumberShortcuts);
        // Either one of the add contacts options should be enabled. If the user entered
        // a dialable number, then clicking add to contact should add it as a number.
        // Otherwise, it should add it to a new contact as a name.
        setShortcutEnabled(SHORTCUT_ADD_NUMBER_TO_CONTACTS, showNumberShortcuts);
        super.setQueryString(queryString);
    }

    //---------------------------MTK---------------------------------------

    /// M: Support MTK-DialerSearch @{
    private SmartDialNameMatcher mNameMatcher;
    private static final String TAG = "RegularSeachListAdapter";
    /**
     * Sets query for the DialerSearchCursorLoader.
     */
    public void configureLoader(DialerSearchCursorLoader loader) {
        LogUtils.d(TAG, "Configure Loader with query" + getQueryString());

        if (DialerUtils.isDialerSearchEnabled()) {
            DialerSearchCursorLoader dialerSearchLoader = null;

            if (loader instanceof DialerSearchCursorLoader) {
                dialerSearchLoader = (DialerSearchCursorLoader) loader;
            } else {
                Log.w(TAG, "MTK-DiaerSearch, Not DialerSearchCursorLoader");
            }

            if (getQueryString() == null) {
                loader.configureQuery("", false);
            } else {
                loader.configureQuery(getQueryString(), false);
            }
        } else {
            Log.w(TAG, "Not support MTK-DialerSearch");
        }
        /// M: ALPS01270474 Data Changed, do not forget to notifyDataSetChanged.
        notifyDataSetChanged();
    }
    /// M: @}

    /// M: Add for support Dialer list item @{
    private final int PHONE_NUMBER_INDEX = 14;
    private final int PHONE_DATA_ID_INDEX = 2;

    @Override
    public Uri getDataUri(int position) {
        Cursor cursor = ((Cursor)getItem(position));
        if (cursor != null) {
            long id = cursor.getLong(PHONE_DATA_ID_INDEX);
            LogUtils.d(TAG, "RegularSearchListAdatper: DataId:" + id);

            if (id < 0) {
                return null;
            } else {
                return ContentUris.withAppendedId(ContactsContract.Data.CONTENT_URI, id);
            }
        } else {
            Log.w(TAG, "Cursor was null in getDataUri() call. Returning null instead.");
            return null;
        }
    }

    @Override
    public String getPhoneNumber(int position) {
        Cursor cursor = ((Cursor)getItem(position));
        if (cursor != null) {
            String phoneNumber = cursor.getString(PHONE_NUMBER_INDEX);
            LogUtils.d(TAG, "RegularSearchListAdatper: phoneNumber:" + phoneNumber);

            return phoneNumber;
        } else {
            Log.w(TAG, "Cursor was null in getPhoneNumber() call. Returning null instead.");
            return null;
        }
    }
    /// M: @}
}
