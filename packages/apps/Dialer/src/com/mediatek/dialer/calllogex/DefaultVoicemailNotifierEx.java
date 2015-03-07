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
 * limitations under the License
 */

package com.mediatek.dialer.calllogex;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.database.Cursor;
import android.net.Uri;
import android.provider.CallLog.Calls;
import android.provider.ContactsContract.PhoneLookup;
import android.text.TextUtils;
import android.util.Log;

import com.android.common.io.MoreCloseables;
import com.android.dialer.CallDetailActivity;
import com.android.dialer.R;
import com.google.common.collect.Maps;

import java.util.Map;

/**
 * Implementation of {@link VoicemailNotifierEx} that shows a notification in the
 * status bar.
 */
public class DefaultVoicemailNotifierEx implements VoicemailNotifierEx {
    public static final String TAG = "DefaultVoicemailNotifier";

    /** The tag used to identify notifications from this class. */
    private static final String NOTIFICATION_TAG = "DefaultVoicemailNotifier";
    /** The identifier of the notification of new voicemails. */
    private static final int NOTIFICATION_ID = 1;

    /** The singleton instance of {@link DefaultVoicemailNotifierEx}. */
    private static DefaultVoicemailNotifierEx sInstance;

    private final Context mContext;
    private final NotificationManager mNotificationManager;
    private final NewCallsQuery mNewCallsQuery;
    private final NameLookupQuery mNameLookupQuery;
    private final PhoneNumberHelperEx mPhoneNumberHelper;

    /** Returns the singleton instance of the {@link DefaultVoicemailNotifierEx}. */
    public static synchronized DefaultVoicemailNotifierEx getInstance(Context context) {
        if (sInstance == null) {
            NotificationManager notificationManager =
                    (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            ContentResolver contentResolver = context.getContentResolver();
            sInstance = new DefaultVoicemailNotifierEx(context, notificationManager,
                    createNewCallsQuery(contentResolver),
                    createNameLookupQuery(contentResolver),
                    createPhoneNumberHelper(context));
        }
        return sInstance;
    }

    private DefaultVoicemailNotifierEx(Context context,
            NotificationManager notificationManager, NewCallsQuery newCallsQuery,
            NameLookupQuery nameLookupQuery, PhoneNumberHelperEx phoneNumberHelper) {
        mContext = context;
        mNotificationManager = notificationManager;
        mNewCallsQuery = newCallsQuery;
        mNameLookupQuery = nameLookupQuery;
        mPhoneNumberHelper = phoneNumberHelper;
    }

    /** Updates the notification and notifies of the call with the given URI. */
    @Override
    public void updateNotification(Uri newCallUri) {
        // Lookup the list of new voicemails to include in the notification.
        // TODO: Move this into a service, to avoid holding the receiver up.
        final NewCall[] newCalls = mNewCallsQuery.query();

        if (newCalls == null) {
            // Query failed, just return.
            return;
        }

        /**M: update the new voicemails count.*/
        mNewVioceMailCount = newCalls.length;
        if (newCalls.length == 0) {
            // No voicemails to notify about: clear the notification.
            clearNotification();
            return;
        }

        Resources resources = mContext.getResources();

        // This represents a list of names to include in the notification.
        String callers = null;

        // Maps each number into a name: if a number is in the map, it has already left a more
        // recent voicemail.
        final Map<String, String> names = Maps.newHashMap();

        // Determine the call corresponding to the new voicemail we have to notify about.
        NewCall callToNotify = null;

        // Iterate over the new voicemails to determine all the information above.
        for (NewCall newCall : newCalls) {
            // Check if we already know the name associated with this number.
            String name = names.get(newCall.number);
            if (name == null) {
                // Look it up in the database.
                name = mNameLookupQuery.query(newCall.number);
                // If we cannot lookup the contact, use the number instead.
                if (name == null) {
                    name = mPhoneNumberHelper.getDisplayNumber(newCall.number, "").toString();
                    if (TextUtils.isEmpty(name)) {
                        name = newCall.number;
                    }
                }
                names.put(newCall.number, name);
                // This is a new caller. Add it to the back of the list of callers.
                if (TextUtils.isEmpty(callers)) {
                    callers = name;
                } else {
                    callers = resources.getString(
                            R.string.notification_voicemail_callers_list, callers, name);
                }
            }
            // Check if this is the new call we need to notify about.
            if (newCallUri != null && newCallUri.equals(newCall.voicemailUri)) {
                callToNotify = newCall;
            }
        }

        if (newCallUri != null && callToNotify == null) {
            Log.e(TAG, "The new call could not be found in the call log: " + newCallUri);
        }

        // Determine the title of the notification and the icon for it.
        final String title = resources.getQuantityString(
                R.plurals.notification_voicemail_title, newCalls.length, newCalls.length);
        // TODO: Use the photo of contact if all calls are from the same person.
        final int icon = android.R.drawable.stat_notify_voicemail;

        Notification.Builder notificationBuilder = new Notification.Builder(mContext)
                .setSmallIcon(icon)
                .setContentTitle(title)
                .setContentText(callers)
                .setDefaults(callToNotify != null ? Notification.DEFAULT_ALL : 0)
                .setDeleteIntent(createMarkNewVoicemailsAsOldIntent())
                .setAutoCancel(true);

        // Determine the intent to fire when the notification is clicked on.
        final Intent contentIntent;
        if (newCalls.length == 1) {
            // Open the voicemail directly.
            contentIntent = new Intent(mContext, CallDetailActivity.class);
            contentIntent.setData(newCalls[0].callsUri);
            contentIntent.putExtra(CallDetailActivity.EXTRA_VOICEMAIL_URI,
                    newCalls[0].voicemailUri);
            Intent playIntent = new Intent(mContext, CallDetailActivity.class);
            playIntent.setData(newCalls[0].callsUri);
            playIntent.putExtra(CallDetailActivity.EXTRA_VOICEMAIL_URI,
                    newCalls[0].voicemailUri);
            playIntent.putExtra(CallDetailActivity.EXTRA_VOICEMAIL_START_PLAYBACK, true);
            playIntent.putExtra(CallDetailActivity.EXTRA_FROM_NOTIFICATION, true);
            /// M: add VVM flag {@
            playIntent.putExtra(CallDetailActivity.EXTRA_FLAG_VVM, true);
            /// M: @}
           // playIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK);
            playIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
            notificationBuilder.addAction(R.drawable.ic_play_holo_dark,
                    resources.getString(R.string.notification_action_voicemail_play),
                    PendingIntent.getActivity(mContext, 0, playIntent, 0));
        } else {
            // Open the call log.
            contentIntent = new Intent(Intent.ACTION_VIEW, Calls.CONTENT_URI);
            /**M: open CallLog fragment.*/
            contentIntent.setType(Calls.CONTENT_TYPE);
        }
        contentIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        notificationBuilder.setContentIntent(
                PendingIntent.getActivity(mContext, 0, contentIntent, 0));
        /// M: add VVM flag {@
        contentIntent.putExtra(CallDetailActivity.EXTRA_FLAG_VVM, true);
        /// M: @}
        // The text to show in the ticker, describing the new event.
        if (callToNotify != null) {
            notificationBuilder.setTicker(resources.getString(
                    R.string.notification_new_voicemail_ticker, names.get(callToNotify.number)));
        }

        mNotificationManager.notify(NOTIFICATION_TAG, NOTIFICATION_ID, notificationBuilder.build());
    }

    /** Creates a pending intent that marks all new voicemails as old. */
    private PendingIntent createMarkNewVoicemailsAsOldIntent() {
        Intent intent = new Intent(mContext, CallLogNotificationsServiceEx.class);
        intent.setAction(CallLogNotificationsServiceEx.ACTION_MARK_NEW_VOICEMAILS_AS_OLD);
        return PendingIntent.getService(mContext, 0, intent, 0);
    }

    @Override
    public void clearNotification() {
        mNotificationManager.cancel(NOTIFICATION_TAG, NOTIFICATION_ID);
    }

    /** Information about a new voicemail. */
    private static final class NewCall {
        public final Uri callsUri;
        public final Uri voicemailUri;
        public final String number;

        public NewCall(Uri callsUri, Uri voicemailUri, String number) {
            this.callsUri = callsUri;
            this.voicemailUri = voicemailUri;
            this.number = number;
        }
    }

    /** Allows determining the new calls for which a notification should be generated. */
    public interface NewCallsQuery {
        /**
         * Returns the new calls for which a notification should be generated.
         */
        public NewCall[] query();
    }

    /** Create a new instance of {@link NewCallsQuery}. */
    public static NewCallsQuery createNewCallsQuery(ContentResolver contentResolver) {
        return new DefaultNewCallsQuery(contentResolver);
    }

    /**
     * Default implementation of {@link NewCallsQuery} that looks up the list of new calls to
     * notify about in the call log.
     */
    private static final class DefaultNewCallsQuery implements NewCallsQuery {
        private static final String[] PROJECTION = {
            Calls._ID, Calls.NUMBER, Calls.VOICEMAIL_URI
        };
        private static final int ID_COLUMN_INDEX = 0;
        private static final int NUMBER_COLUMN_INDEX = 1;
        private static final int VOICEMAIL_URI_COLUMN_INDEX = 2;

        private final ContentResolver mContentResolver;

        private DefaultNewCallsQuery(ContentResolver contentResolver) {
            mContentResolver = contentResolver;
        }

        @Override
        public NewCall[] query() {
            final String selection = String.format("%s = 1 AND %s = ?", Calls.NEW, Calls.TYPE);
            final String[] selectionArgs = new String[]{ Integer.toString(Calls.VOICEMAIL_TYPE) };
            Cursor cursor = null;
            try {
                cursor = mContentResolver.query(Calls.CONTENT_URI_WITH_VOICEMAIL, PROJECTION,
                        selection, selectionArgs, Calls.DEFAULT_SORT_ORDER);
                if (cursor == null) {
                    return null;
                }
                NewCall[] newCalls = new NewCall[cursor.getCount()];
                while (cursor.moveToNext()) {
                    newCalls[cursor.getPosition()] = createNewCallsFromCursor(cursor);
                }
                return newCalls;
            } finally {
                MoreCloseables.closeQuietly(cursor);
            }
        }

        /** Returns an instance of {@link NewCall} created by using the values of the cursor. */
        private NewCall createNewCallsFromCursor(Cursor cursor) {
            String voicemailUriString = cursor.getString(VOICEMAIL_URI_COLUMN_INDEX);
            Uri callsUri = ContentUris.withAppendedId(
                    Calls.CONTENT_URI_WITH_VOICEMAIL, cursor.getLong(ID_COLUMN_INDEX));
            Uri voicemailUri = voicemailUriString == null ? null : Uri.parse(voicemailUriString);
            return new NewCall(callsUri, voicemailUri, cursor.getString(NUMBER_COLUMN_INDEX));
        }
    }

    /** Allows determining the name associated with a given phone number. */
    public interface NameLookupQuery {
        /**
         * Returns the name associated with the given number in the contacts database, or null if
         * the number does not correspond to any of the contacts.
         * <p>
         * If there are multiple contacts with the same phone number, it will return the name of one
         * of the matching contacts.
         */
        public String query(String number);
    }

    /** Create a new instance of {@link NameLookupQuery}. */
    public static NameLookupQuery createNameLookupQuery(ContentResolver contentResolver) {
        return new DefaultNameLookupQuery(contentResolver);
    }

    /**
     * Default implementation of {@link NameLookupQuery} that looks up the name of a contact in the
     * contacts database.
     */
    private static final class DefaultNameLookupQuery implements NameLookupQuery {
        private static final String[] PROJECTION = { PhoneLookup.DISPLAY_NAME };
        private static final int DISPLAY_NAME_COLUMN_INDEX = 0;

        private final ContentResolver mContentResolver;

        private DefaultNameLookupQuery(ContentResolver contentResolver) {
            mContentResolver = contentResolver;
        }

        @Override
        public String query(String number) {
            Cursor cursor = null;
            try {
                cursor = mContentResolver.query(
                        Uri.withAppendedPath(PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number)),
                        PROJECTION, null, null, null);
                if (cursor == null || !cursor.moveToFirst()) return null;
                return cursor.getString(DISPLAY_NAME_COLUMN_INDEX);
            } finally {
                if (cursor != null) {
                    cursor.close();
                }
            }
        }
    }

    /**
     * Create a new PhoneNumberHelper.
     * <p>
     * This will cause some Disk I/O, at least the first time it is created, so it should not be
     * called from the main thread.
     */
    public static PhoneNumberHelperEx createPhoneNumberHelper(Context context) {
        return new PhoneNumberHelperEx(context.getResources());
    }

    /**M: mark the new voice mail count @{*/
    private int mNewVioceMailCount;
    public boolean hasNewVoicemails() {
        return mNewVioceMailCount > 0;
    }

    /**
     * This method is only used in CallDetailActivity at present.
     * @param newVMCnt new voice mail count
     */
    public void setNewVoicemailCount(int newVMCnt) {
        Log.d(TAG, "mNewVioceMailCount = " + mNewVioceMailCount + "; newVMCnt=" + newVMCnt);
        mNewVioceMailCount = newVMCnt;
    }
    /** @}*/
}
