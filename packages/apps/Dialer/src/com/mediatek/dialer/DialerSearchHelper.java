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

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.net.Uri.Builder;
import android.provider.ContactsContract;
import android.provider.ContactsContract.CommonDataKinds.Callable;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.DialerSearch;
import android.util.Log;

import com.android.contacts.common.preference.ContactsPreferences;
import com.google.common.base.Preconditions;
import com.mediatek.dialer.util.LogUtils;

public class DialerSearchHelper {
    private static final String TAG = "DialerSearchHelper";
    private static final String DATA_READY_FLAG = "isDataReady";
    private static DialerSearchHelper sSingleton = null;
    private final Context mContext;
    private ContactsPreferences mContactsPrefs;
    private boolean mDataIsReady = true;

    protected DialerSearchHelper(Context context, ContactsPreferences contactsPrefs) {
        mContext = Preconditions.checkNotNull(context, "Context must not be null");
        mContactsPrefs = contactsPrefs;
        mDataIsReady = true;
    }

    /**
     * Access function to get the singleton instance of DialerDatabaseHelper.
     */
    public static synchronized DialerSearchHelper getInstance(Context context, ContactsPreferences contactsPrefs) {
        LogUtils.d(TAG, "Getting Instance");

        if (sSingleton == null) {
            // Use application context instead of activity context because this
            // is a singleton,
            // and we don't want to leak the activity if the activity is not
            // running but the
            // dialer database helper is still doing work.
            sSingleton = new DialerSearchHelper(context.getApplicationContext(), contactsPrefs);
        }
        return sSingleton;
    }

    public void dialerSearchInit() {
        LogUtils.d(TAG, "MTK-DialerSearch, dialerSearchInit");

        final ContentResolver resolver = mContext.getContentResolver();
        Cursor cursor = null;
        try {
            int displayOrder = mContactsPrefs.getDisplayOrder();
            int sortOrder = mContactsPrefs.getSortOrder();
            Uri baseUri = Uri.withAppendedPath(ContactsContract.AUTHORITY_URI, "dialer_search_init");

            Uri dialerSearchUri = baseUri.buildUpon()
                    .appendQueryParameter(ContactsContract.Preferences.DISPLAY_ORDER, String.valueOf(displayOrder))
                    .appendQueryParameter(ContactsContract.Preferences.SORT_ORDER, String.valueOf(sortOrder))
                    .build();

            cursor = resolver.query(dialerSearchUri, null, null, null, null);
            if (cursor != null) {
                cursor.close();
                cursor = null;
            }

        } catch (Exception e) {
            if (cursor != null) {
                cursor.close();
                cursor = null;
            }
            Log.w(TAG, "Exception thrown during handling MTK-DialerSearch, getDialerSearchResults", e);
        }
    }

    /**
     * Query dialerSearch results from contactsProvider, use MTK algorithm.
     * @param query
     * @return DialerSearch result.
     */
    public Cursor getDialerSearchResults(String query) {
        LogUtils.d(TAG, "MTK-DialerSearch, getDialerSearchResults: queryFilter: " + query);

        final ContentResolver resolver = mContext.getContentResolver();
        Cursor cursor = null;
        try {
            int displayOrder = mContactsPrefs.getDisplayOrder();
            int sortOrder = mContactsPrefs.getSortOrder();
            Uri baseUri = Uri.withAppendedPath(ContactsContract.AUTHORITY_URI, "dialer_search");
            Uri dialerSearchUri = baseUri.buildUpon().appendPath(query).build();
            LogUtils.d(TAG, "MTK-DialerSearch, DataReady: " + mDataIsReady 
                             + " ,displayOrder: " + displayOrder + " ,sortOrder: " + sortOrder);

            Uri dialerSearchParamUri = dialerSearchUri.buildUpon()
                    .appendQueryParameter(DATA_READY_FLAG, String.valueOf(mDataIsReady))
                    .appendQueryParameter(ContactsContract.Preferences.DISPLAY_ORDER, String.valueOf(displayOrder))
                    .appendQueryParameter(ContactsContract.Preferences.SORT_ORDER, String.valueOf(sortOrder))
                    .build();

            cursor = resolver.query(dialerSearchParamUri, null, null, null, null);

            LogUtils.d(TAG, "MTK-DialerSearch, cursor.getCount: " + cursor.getCount());
            mDataIsReady = true;

            return cursor;
        } catch (Exception e) {
            Log.w(TAG, "Exception thrown during handling MTK-DialerSearch, getDialerSearchResults", e);

            if (cursor != null) {
                cursor.close();
                cursor = null;
            }
            return null;
        }
    }

    /**
     * Notify ContactsProvider to prepare the latest queried data.
     */
    public void setDatasforDialersearch() {
        mDataIsReady = false;
    }

    /**
     * Query dialerSearch results from contactsProvider, use google default algorithm.
     * @param query
     * @param useCallableUri, Similar to {@link Phone#CONTENT_URI}, but returns callable data instead of only
             * phone numbers.
     * @return DialerSearch results.
     */
    public Cursor getRegularDialerSearchResults(String query, boolean useCallableUri) {
        final ContentResolver resolver = mContext.getContentResolver();
        Cursor cursor = null;
        Cursor result = null;
        try {
            final Uri baseUri;
            if (useCallableUri) {
                baseUri = Callable.CONTENT_FILTER_URI;
            } else {
                baseUri = Phone.CONTENT_FILTER_URI;
            }

            final Builder builder = baseUri.buildUpon();
            builder.appendPath(query);
            builder.appendQueryParameter(ContactsContract.REMOVE_DUPLICATE_ENTRIES, "true");

            Uri regularDialerSearchUri = builder.build();

            final String[] projection;
            final String sortOrder;
            if (mContactsPrefs.getDisplayOrder() == ContactsContract.Preferences.DISPLAY_ORDER_PRIMARY) {
                projection = PhoneQuery.PROJECTION_PRIMARY;
            } else {
                projection = PhoneQuery.PROJECTION_ALTERNATIVE;
            }

            if (mContactsPrefs.getSortOrder() == ContactsContract.Preferences.SORT_ORDER_PRIMARY) {
                sortOrder = Phone.SORT_KEY_PRIMARY;
            } else {
                sortOrder = Phone.SORT_KEY_ALTERNATIVE;
            }
            cursor = resolver.query(regularDialerSearchUri, projection, null, null, sortOrder);

            int cursorPos = 0;
            int count = 0;
            if (cursor != null) {
                LogUtils.d(TAG, "MTK-DialerSearch, regularDialerSearch,cursor.getCount: " + cursor.getCount());

                count = cursor.getCount();
                Object[][] objectMap = new Object[count][];

                while (cursor.moveToNext()) {
                    long contactId = cursor.getLong(cursor.getColumnIndex(Phone.CONTACT_ID));
                    long dataId = cursor.getLong(cursor.getColumnIndex(Phone._ID));
                    long photoId = cursor.getLong(cursor.getColumnIndex(Phone.PHOTO_ID));
                    int type = cursor.getInt(cursor.getColumnIndex(Phone.TYPE));
                    int numberLabel = cursor.getInt(cursor.getColumnIndex(Phone.LABEL));
                    String displayName = cursor.getString(cursor.getColumnIndex(Phone.DISPLAY_NAME_PRIMARY));
                    String number = cursor.getString(cursor.getColumnIndex(Phone.NUMBER));
                    String lookup = cursor.getString(cursor.getColumnIndex(Phone.LOOKUP_KEY));
                    int simIndicate = cursor.getInt(cursor.getColumnIndex(Contacts.INDICATE_PHONE_SIM));

//                    LogUtils.d(TAG, "contactId: " + contactId + " ,dataId: " + dataId + " ,photoId: " + photoId
//                            + " ,numberLabel: " + numberLabel + " ,displayName: " + displayName + " ,number: " + number
//                            + " ,lookup: " + lookup + " ,type: " + type);

                    objectMap[cursorPos++] = buildCursorRecord(0, contactId, dataId, null, 0, 0, null, 0, 0, simIndicate, 0,
                            photoId, type, displayName, number, lookup, 0, "true", query);

                    result = buildCursor(objectMap);

                }
            }
            if (cursor != null) {
                cursor.close();
                cursor = null;
            }
            LogUtils.d(TAG, "result: " + result.getCount());

            return result;
        } catch (Exception e) {
            Log.w(TAG, "Exception thrown during handling MTK-DialerSearch, getDialerSearchResults", e);

            if (cursor != null) {
                cursor.close();
                cursor = null;
            }

            if (result != null) {
                result.close();
                result = null;
            }
            return null;
        }
    }

    private static class PhoneQuery {
        public static final String[] PROJECTION_PRIMARY = new String[] {
            Phone._ID,                          // 0
            Phone.TYPE,                         // 1
            Phone.LABEL,                        // 2
            Phone.NUMBER,                       // 3
            Phone.CONTACT_ID,                   // 4
            Phone.LOOKUP_KEY,                   // 5
            Phone.PHOTO_ID,                     // 6
            Phone.DISPLAY_NAME_PRIMARY,         // 7
            Phone.PHOTO_THUMBNAIL_URI,          // 8
            Contacts.INDICATE_PHONE_SIM,        // 9
        };

        public static final String[] PROJECTION_ALTERNATIVE = new String[] {
            Phone._ID,                          // 0
            Phone.TYPE,                         // 1
            Phone.LABEL,                        // 2
            Phone.NUMBER,                       // 3
            Phone.CONTACT_ID,                   // 4
            Phone.LOOKUP_KEY,                   // 5
            Phone.PHOTO_ID,                     // 6
            Phone.DISPLAY_NAME_ALTERNATIVE,     // 7
            Phone.PHOTO_THUMBNAIL_URI,          // 8
            Contacts.INDICATE_PHONE_SIM,        // 9
        };
    }

    private Object[] buildCursorRecord(long id, long contactId, long dataId, String callData, long callLogId,
            int callType, String geo, int callSimId, int isVtCall, int simIndicator, int starred,
            long photoId, int phoneType, String name, String number, String lookup, int isSdn,
            String isRegularSearch, String nameOffset) {
        Object[] record = new Object[] {
                id, contactId, dataId, callData, callLogId,
                callType, geo, callSimId, isVtCall, simIndicator, starred,
                photoId, phoneType, name, number, lookup, isSdn,
                isRegularSearch, nameOffset
        };
        return record;
    }

    private Cursor buildCursor(Object[][] cursorValues) {
        MatrixCursor c = new MatrixCursor(DialerSearchQuery.COLUMNS);
        if (cursorValues != null) {
            for (Object[] record : cursorValues) {
                if (record == null) {
                    break;
                }
                c.addRow(record);
            }
        }
        return c;
    }

    public interface DialerSearchQuery {
        String[] COLUMNS = new String[] {
                DialerSearch.NAME_LOOKUP_ID,
                DialerSearch.CONTACT_ID,
                "data_id",
                DialerSearch.CALL_DATE,
                DialerSearch.CALL_LOG_ID,
                DialerSearch.CALL_TYPE,
                DialerSearch.CALL_GEOCODED_LOCATION,
                DialerSearch.SIM_ID,
                DialerSearch.VTCALL,
                DialerSearch.INDICATE_PHONE_SIM,
                DialerSearch.CONTACT_STARRED,
                DialerSearch.PHOTO_ID,
                DialerSearch.SEARCH_PHONE_TYPE,
                DialerSearch.NAME,
                DialerSearch.SEARCH_PHONE_NUMBER,
                DialerSearch.CONTACT_NAME_LOOKUP,
                DialerSearch.IS_SDN_CONTACT,
                DialerSearch.MATCHED_DATA_OFFSETS,
                DialerSearch.MATCHED_NAME_OFFSETS
        };
    }
}
