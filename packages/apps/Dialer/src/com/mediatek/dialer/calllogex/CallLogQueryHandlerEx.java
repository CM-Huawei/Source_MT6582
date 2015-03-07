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

package com.mediatek.dialer.calllogex;

import android.content.AsyncQueryHandler;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.database.MergeCursor;
import android.database.sqlite.SQLiteDatabaseCorruptException;
import android.database.sqlite.SQLiteDiskIOException;
import android.database.sqlite.SQLiteException;
import android.database.sqlite.SQLiteFullException;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.provider.CallLog;
import android.provider.CallLog.Calls;
import android.provider.VoicemailContract.Status;
import android.provider.VoicemailContract.Voicemails;
import android.text.TextUtils;
import android.util.Log;

import com.android.common.io.MoreCloseables;
import com.android.contacts.common.database.NoNullCursorAsyncQueryHandler;
import com.android.dialer.voicemail.VoicemailStatusHelperImpl;
import com.google.common.collect.Lists;

/// The following lines are provided and maintained by Mediatek Inc.
import com.android.contacts.common.util.Constants;
import com.mediatek.contacts.ExtensionManager;
import com.mediatek.contacts.ext.ContactPluginDefault;
import com.mediatek.dialer.calllog.CallLogDateFormatHelper;
import com.mediatek.dialer.util.DialerUtils;
import com.mediatek.dialer.util.VvmUtils;

import java.lang.ref.WeakReference;
import java.util.List;
import java.util.concurrent.TimeUnit;

import javax.annotation.concurrent.GuardedBy;
/// The previous lines are provided and maintained by Mediatek Inc.

/** Handles asynchronous queries to the call log. */
/** M: modify @ { */
/*package*/ //class CallLogQueryHandler extends AsyncQueryHandler {
public class CallLogQueryHandlerEx extends NoNullCursorAsyncQueryHandler {
    /** @ } */
    private static final String[] EMPTY_STRING_ARRAY = new String[0];

    private static final String TAG = "CallLogQueryHandler";
    private static final int NUM_LOGS_TO_DISPLAY = 1000;

    /** The token for the query to fetch the new entries from the call log. */
    private static final int QUERY_NEW_CALLS_TOKEN = 53;
    /** The token for the query to fetch the old entries from the call log. */
    private static final int QUERY_OLD_CALLS_TOKEN = 54;
    /** The token for the query to mark all missed calls as old after seeing the call log. */
    private static final int UPDATE_MARK_AS_OLD_TOKEN = 55;
    /** The token for the query to mark all new voicemails as old. */
    private static final int UPDATE_MARK_VOICEMAILS_AS_OLD_TOKEN = 56;
    /** The token for the query to mark all missed calls as read after seeing the call log. */
    private static final int UPDATE_MARK_MISSED_CALL_AS_READ_TOKEN = 57;
    /** The token for the query to fetch voicemail status messages. */
    private static final int QUERY_VOICEMAIL_STATUS_TOKEN = 58;

    /**
     * Call type similar to Calls.INCOMING_TYPE used to specify all types instead of one particular
     * type.
     */
    public static final int CALL_TYPE_ALL = -1;

    /**
     * The time window from the current time within which an unread entry will be added to the new
     * section.
     */
    private static final long NEW_SECTION_TIME_WINDOW = TimeUnit.DAYS.toMillis(7);

    private final WeakReference<Listener> mListener;
    private final int mLogLimit;
    /** The cursor containing the new calls, or null if they have not yet been fetched. */
    @GuardedBy("this") private Cursor mNewCallsCursor;
    /** The cursor containing the old calls, or null if they have not yet been fetched. */
    @GuardedBy("this") private Cursor mOldCallsCursor;
    /**
     * The identifier of the latest calls request.
     * <p>
     * A request for the list of calls requires two queries and hence the two cursor
     * {@link #mNewCallsCursor} and {@link #mOldCallsCursor} above, corresponding to
     * {@link #QUERY_NEW_CALLS_TOKEN} and {@link #QUERY_OLD_CALLS_TOKEN}.
     * <p>
     * When a new request is about to be started, existing cursors are closed. However, it is
     * possible that one of the queries completes after the new request has started. This means that
     * we might merge two cursors that do not correspond to the same request. Moreover, this may
     * lead to a resource leak if the same query completes and we override the cursor without
     * closing it first.
     * <p>
     * To make sure we only join two cursors from the same request, we use this variable to store
     * the request id of the latest request and make sure we only process cursors corresponding to
     * the this request.
     */
    @GuardedBy("this") private int mCallsRequestId;

    /**
     * Simple handler that wraps background calls to catch
     * {@link SQLiteException}, such as when the disk is full.
     */
    protected class CatchingWorkerHandler extends AsyncQueryHandler.WorkerHandler {
        public CatchingWorkerHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            try {
                // Perform same query while catching any exceptions
                super.handleMessage(msg);
            } catch (SQLiteDiskIOException e) {
                Log.w(TAG, "Exception on background worker thread", e);
            } catch (SQLiteFullException e) {
                Log.w(TAG, "Exception on background worker thread", e);
            } catch (SQLiteDatabaseCorruptException e) {
                Log.w(TAG, "Exception on background worker thread", e);
            }
        }
    }

    @Override
    protected Handler createHandler(Looper looper) {
        // Provide our special handler that catches exceptions
        return new CatchingWorkerHandler(looper);
    }

    public CallLogQueryHandlerEx(ContentResolver contentResolver, Listener listener) {
        //super(contentResolver);
        //mListener = new WeakReference<Listener>(listener);
        this(contentResolver, listener, -1);
    }

    public CallLogQueryHandlerEx(ContentResolver contentResolver, Listener listener, int limit) {
        super(contentResolver);
        mListener = new WeakReference<Listener>(listener);
        // add for phone favorite adapter query.
        mLogLimit = limit;
    }

    /** M: delete @ { */
    /** Creates a cursor that contains a single row and maps the section to the given value. */
    /**
     * 
    private Cursor createHeaderCursorFor(int section) {
        MatrixCursor matrixCursor =
                new MatrixCursor(CallLogQuery.EXTENDED_PROJECTION);
        // The values in this row correspond to default values for _PROJECTION from CallLogQuery
        // plus the section value.
        matrixCursor.addRow(new Object[]{
                0L, "", 0L, 0L, 0, "", "", "", null, 0, null, null, null, null, 0L, null, 0,
                section
        });
        return matrixCursor;
    }
     */

    /** Returns a cursor for the old calls header. */
    /**
     * 
    private Cursor createOldCallsHeaderCursor() {
        return createHeaderCursorFor(CallLogQuery.SECTION_OLD_HEADER);
    }
     */

    /** Returns a cursor for the new calls header. */
    /**
     * 
    private Cursor createNewCallsHeaderCursor() {
        return createHeaderCursorFor(CallLogQuery.SECTION_NEW_HEADER);
    }
     */

    /**
     * Fetches the list of calls from the call log for a given type.
     * <p>
     * It will asynchronously update the content of the list view when the fetch completes.
     */
    //public void fetchCalls(int callType) {
    //    cancelFetch();
    //    int requestId = newCallsRequest();
    //    fetchCalls(QUERY_NEW_CALLS_TOKEN, requestId, true /*isNew*/, callType);
    //    fetchCalls(QUERY_OLD_CALLS_TOKEN, requestId, false /*isNew*/, callType);
    //}

    /*
    public void fetchVoicemailStatus() {
        startQuery(QUERY_VOICEMAIL_STATUS_TOKEN, null, Status.CONTENT_URI,
                VoicemailStatusHelperImpl.PROJECTION, null, null, null);
    }
     */

    /** Fetches the list of calls in the call log, either the new one or the old ones. */
    /**
     * 
    private void fetchCalls(int token, int requestId, boolean isNew, int callType) {
        // We need to check for NULL explicitly otherwise entries with where READ is NULL
        // may not match either the query or its negation.
        // We consider the calls that are not yet consumed (i.e. IS_READ = 0) as "new".
        String selection = String.format("%s IS NOT NULL AND %s = 0 AND %s > ?",
                Calls.IS_READ, Calls.IS_READ, Calls.DATE);
        List<String> selectionArgs = Lists.newArrayList(
                Long.toString(System.currentTimeMillis() - NEW_SECTION_TIME_WINDOW));
        if (!isNew) {
            // Negate the query.
            selection = String.format("NOT (%s)", selection);
        }
        if (callType > CALL_TYPE_ALL) {
            // Add a clause to fetch only items of type voicemail.
            selection = String.format("(%s) AND (%s = ?)", selection, Calls.TYPE);
            selectionArgs.add(Integer.toString(callType));
        }
        Uri uri = Calls.CONTENT_URI_WITH_VOICEMAIL.buildUpon()
                .appendQueryParameter(Calls.LIMIT_PARAM_KEY, Integer.toString(NUM_LOGS_TO_DISPLAY))
                .build();
        startQuery(token, requestId, uri,
                CallLogQuery._PROJECTION, selection, selectionArgs.toArray(EMPTY_STRING_ARRAY),
                Calls.DEFAULT_SORT_ORDER);
    }
     */
    /** @ }*/

    /** Cancel any pending fetch request. */
    private void cancelFetch() {
        cancelOperation(QUERY_NEW_CALLS_TOKEN);
        cancelOperation(QUERY_OLD_CALLS_TOKEN);
    }

    /** Updates all new calls to mark them as old. */
    public void markNewCallsAsOld() {
        // Mark all "new" calls as not new anymore.
        StringBuilder where = new StringBuilder();
        where.append(Calls.NEW);
        where.append(" = 1");

        ContentValues values = new ContentValues(1);
        values.put(Calls.NEW, "0");

        startUpdate(UPDATE_MARK_AS_OLD_TOKEN, null, Calls.CONTENT_URI_WITH_VOICEMAIL,
                values, where.toString(), null);
    }

    /** Updates all new voicemails to mark them as old. */
    /** M: delete @ { */

    public void markNewVoicemailsAsOld() {
        // Mark all "new" voicemails as not new anymore.
        StringBuilder where = new StringBuilder();
        where.append(Calls.NEW);
        where.append(" = 1 AND ");
        where.append(Calls.TYPE);
        where.append(" = ?");

        ContentValues values = new ContentValues(1);
        values.put(Calls.NEW, "0");

        startUpdate(UPDATE_MARK_VOICEMAILS_AS_OLD_TOKEN, null, Calls.CONTENT_URI_WITH_VOICEMAIL,
                values, where.toString(), new String[]{ Integer.toString(Calls.VOICEMAIL_TYPE) });
    }

    /** @ }*/
    /** Updates all missed calls to mark them as read. */
    public void markMissedCallsAsRead() {
        // Mark all "new" calls as not new anymore.
        StringBuilder where = new StringBuilder();
        where.append(Calls.IS_READ).append(" = 0");
        where.append(" AND ");
        where.append(Calls.TYPE).append(" = ").append(Calls.MISSED_TYPE);

        ContentValues values = new ContentValues(1);
        values.put(Calls.IS_READ, "1");

        startUpdate(UPDATE_MARK_MISSED_CALL_AS_READ_TOKEN, null, Calls.CONTENT_URI, values,
                where.toString(), null);
    }

    /**
     * Start a new request and return its id. The request id will be used as the cookie for the
     * background request.
     * <p>
     * Closes any open cursor that has not yet been sent to the requester.
     */
    private synchronized int newCallsRequest() {
        MoreCloseables.closeQuietly(mNewCallsCursor);
        MoreCloseables.closeQuietly(mOldCallsCursor);
        mNewCallsCursor = null;
        mOldCallsCursor = null;
        return ++mCallsRequestId;
    }

    @Override
    protected  void onNotNullableQueryComplete(int token, Object cookie, Cursor cursor) {
        /** M: delete @ { */
        /**
         * 
        if (token == QUERY_NEW_CALLS_TOKEN) {
            int requestId = ((Integer) cookie).intValue();
            if (requestId != mCallsRequestId) {
                // Ignore this query since it does not correspond to the latest request.
                return;
            }

            // Store the returned cursor.
            MoreCloseables.closeQuietly(mNewCallsCursor);
            mNewCallsCursor = new ExtendedCursor(
                    cursor, CallLogQuery.SECTION_NAME, CallLogQuery.SECTION_NEW_ITEM);
        } else if (token == QUERY_OLD_CALLS_TOKEN) {
            int requestId = ((Integer) cookie).intValue();
            if (requestId != mCallsRequestId) {
                // Ignore this query since it does not correspond to the latest request.
                return;
            }

            // Store the returned cursor.
            MoreCloseables.closeQuietly(mOldCallsCursor);
            mOldCallsCursor = new ExtendedCursor(
                    cursor, CallLogQuery.SECTION_NAME, CallLogQuery.SECTION_OLD_ITEM);
        } else if (token == QUERY_VOICEMAIL_STATUS_TOKEN) {
            updateVoicemailStatus(cursor);
            return;
        }
         */
        /** @ }*/
            /** M: add @ { */
        if (token == QUERY_SEARCH_TOKEN) {
            updateAdapterData(cursor);
//        } else if (token == QUERY_ALL_CALLS_TOKEN) {
//            updateAdapterData(cursor);
        } else if (token == QUERY_ALL_CALLS_JOIN_DATA_VIEW_TOKEN) {
            updateAdapterData(cursor);
            /** @ }*/
        } else {
            Log.w(TAG, "Unknown query completed: ignoring: " + token);
            return;
        }
        /** M: delete @ { */
//        if (mNewCallsCursor != null && mOldCallsCursor != null) {
//            updateAdapterData(createMergedCursor());
//        }
        /** @ }*/
    }

    /** Creates the merged cursor representing the data to show in the call log. */
    /**
     * 
    @GuardedBy("this")
    private Cursor createMergedCursor() {
        try {
            final boolean hasNewCalls = mNewCallsCursor.getCount() != 0;
            final boolean hasOldCalls = mOldCallsCursor.getCount() != 0;

            if (!hasNewCalls) {
                // Return only the old calls, without the header.
                MoreCloseables.closeQuietly(mNewCallsCursor);
                return mOldCallsCursor;
            }

            if (!hasOldCalls) {
                // Return only the new calls.
                MoreCloseables.closeQuietly(mOldCallsCursor);
                return new MergeCursor(
                        new Cursor[]{ createNewCallsHeaderCursor(), mNewCallsCursor });
            }

            return new MergeCursor(new Cursor[]{
                    createNewCallsHeaderCursor(), mNewCallsCursor,
                    createOldCallsHeaderCursor(), mOldCallsCursor});
        } finally {
            // Any cursor still open is now owned, directly or indirectly, by the caller.
            mNewCallsCursor = null;
            mOldCallsCursor = null;
        }
    }
     */

    /**
     * Updates the adapter in the call log fragment to show the new cursor data.
     */
    private void updateAdapterData(Cursor combinedCursor) {
        final Listener listener = mListener.get();
        if (listener != null) {
            listener.onCallsFetched(combinedCursor);
        /// M: ALPS01260098 @{
        // Cursor Leak check.
        } else {
            MoreCloseables.closeQuietly(combinedCursor);
        /// @}
        }
    }

    /** M: delete @ { */
    /**
     * 
    private void updateVoicemailStatus(Cursor statusCursor) {
        final Listener listener = mListener.get();
        if (listener != null) {
            listener.onVoicemailStatusFetched(statusCursor);
        }
    }
     */
    /** @ }*/
    /** Listener to completion of various queries. */
    public interface Listener {
        /** Called when {@link CallLogQueryHandlerEx#fetchVoicemailStatus()} completes. */
        void onVoicemailStatusFetched(Cursor statusCursor);

        /**
         * Called when {@link CallLogQueryHandlerEx#fetchCalls(int)}complete.
         */
        void onCallsFetched(Cursor combinedCursor);
        /** M: add @ { */
        void onCallsDeleted();
    }
    
    /** M: add @ { */
    
    private static final int DELETE_CALLS_TOKEN = 59;
    private static final int QUERY_ALL_CALLS_TOKEN = 60;
    private static final int QUERY_SEARCH_TOKEN = 61;

    private static final int QUERY_ALL_CALLS_JOIN_DATA_VIEW_TOKEN = 62;

    /**
     * 
    public void fetchAllCalls(int simFilter, int typeFilter) {
        log("fetchAllCalls(), simFilter = " + simFilter + " typeFilter " + typeFilter);
        cancelFetch();
        int requestId = newCallsRequest();
        fetchCalls(QUERY_ALL_CALLS_TOKEN, requestId ,simFilter, typeFilter);
    }
     */

    // add for phone favorite adapter query.
    public void fetchCallsJionDataView(int simFilter, int typeFilter, long newerThan) {
        log("fetchCallsJionDataView() for phone favorite, simFilter = " + simFilter + " typeFilter " + typeFilter +"newerThan"+newerThan);
        cancelFetch();
        int requestId = newCallsRequest();
        fetchCalls(QUERY_ALL_CALLS_JOIN_DATA_VIEW_TOKEN, requestId ,simFilter, typeFilter , newerThan);
    }

    public void fetchCallsJionDataView(int simFilter, int typeFilter) {
        log("fetchCallsJionDataView(), simFilter = " + simFilter + " typeFilter " + typeFilter);
        cancelFetch();
        int requestId = newCallsRequest();
        fetchCalls(QUERY_ALL_CALLS_JOIN_DATA_VIEW_TOKEN, requestId ,simFilter, typeFilter , -1);
    }

    private void fetchCalls(int token, int requestId, int simFilter, int typeFilter ,long newerThan) {
        log("fetchCalls(), token = " + token + " simFilter = " + simFilter + " typeFilter "
                + typeFilter);
        String selection = getSelection(simFilter, typeFilter);

        Uri queryUri = Calls.CONTENT_URI;
        String[] queryProjection = CallLogQueryEx._PROJECTION;

        if (QUERY_ALL_CALLS_JOIN_DATA_VIEW_TOKEN == token) {
            queryUri = Uri.parse("content://call_log/callsjoindataview");
            queryProjection = CallLogQueryEx.PROJECTION_CALLS_JOIN_DATAVIEW;
        }

        /** M: [VVM] append vvm query parameter.*/
        if (VvmUtils.isVvmEnabled()) {
            queryUri = VvmUtils.buildVvmAllowedUri(queryUri);
        }

        // add for phone favorite adapter query.
        String[] selectionArgs = null;
        if (mLogLimit == 1) {
            queryUri = queryUri.buildUpon().appendQueryParameter(Calls.LIMIT_PARAM_KEY, Integer.toString(mLogLimit)).build();
            if (newerThan > 0) {
                StringBuilder builder = new StringBuilder();
                if (selection != null && selection.length() > 0) {
                    builder.append(selection);
                    builder.append(" AND ");
                }
                builder.append(String.format("(%s > ?)", Calls.DATE));
                selection = builder.toString();
                selectionArgs = new String[] { Long.toString(newerThan) };
            }
        }
        log("fetchCalls() queryUri="+queryUri.toString());
        log("fetchCalls(),selection =" + selection + "selectionArgs =" +selectionArgs);
        CallLogDateFormatHelper.refreshData();
        startQuery(token, requestId, queryUri, queryProjection, selection, selectionArgs,
                Calls.DEFAULT_SORT_ORDER);
    }

    public void fetchSearchCalls(Uri uri) {
        cancelFetch();
        log("fetchSearchCalls(uri )");
        CallLogDateFormatHelper.refreshData();
        startQuery(QUERY_SEARCH_TOKEN, null, uri,
                CallLogQueryEx.PROJECTION_CALLS_JOIN_DATAVIEW, null, null,
                Calls.DEFAULT_SORT_ORDER);
    }

    protected void onDeleteComplete(int token, Object cookie, int result) {
        final Listener listener = mListener.get();
        if (listener != null) {
            listener.onCallsDeleted();
        }
    }

    public void deleteSpecifiedCalls(String deleteFilter) {
        /** M: [VVM] If the selected Calls is a voicemail, should not call CallLogProvider to delete,
          * we have to call VoicemailContentProvider to remove voicemail file. @{ */
        if (VvmUtils.isVvmEnabled()) {
            startDelete(DELETE_CALLS_TOKEN, null, Voicemails.CONTENT_URI, deleteFilter, null);
        }
        /** @} */
        startDelete(DELETE_CALLS_TOKEN, null, Calls.CONTENT_URI, deleteFilter, null);
    }


    private String getSelection(int simFilter, int typeFilter) {
        StringBuilder builder = new StringBuilder();

        // FILTER_ALL_RESOURCES - null: no condition
        if (Constants.FILTER_SIP_CALL == simFilter) {
            builder.append(CallLog.Calls.SIM_ID + "=" + DialerUtils.CALL_TYPE_SIP);
        } else if (simFilter < Constants.FILTER_BASE) {
            builder.append(CallLog.Calls.SIM_ID + "=" + simFilter);
        }

        if (typeFilter != Constants.FILTER_TYPE_ALL) {
            int t;
            switch (typeFilter) {
                case Constants.FILTER_TYPE_INCOMING:
                    t = Calls.INCOMING_TYPE;
                    break;
                case Constants.FILTER_TYPE_MISSED:
                    t = Calls.MISSED_TYPE;
                    break;
                case Constants.FILTER_TYPE_OUTGOING:
                    t = Calls.OUTGOING_TYPE;
                    break;
                case Constants.FILTER_TYPE_AUTO_REJECT:
                    t = Calls.AUTOREJECTED_TYPE;
                    break;
                default:
                    t = Constants.FILTER_TYPE_INCOMING;
                    break;
            }

            if (builder.length() > 0) {
                builder.append(" and ");
            }

            /**M: [VVM] Treat VVM as missed type @{ */
            if (VvmUtils.isVvmEnabled() && t == Calls.MISSED_TYPE) {
                builder.append(Calls.TYPE + " in (" + Calls.VOICEMAIL_TYPE + ", " + t + ")");
            } else {
                builder.append(Calls.TYPE + "=" + t);
            }
            /** @} */

        } else {
            /** M: New Feature Easy Porting @{ */
            String str = ExtensionManager.getInstance()
                        .getCallDetailExtension()
                        .setChar((builder.length() > 0), " and ", null, 0, false, ContactPluginDefault.COMMD_FOR_OP01);
            if (!TextUtils.isEmpty(str)) {
                builder.append(str);
            }
            String callsType = ExtensionManager.getInstance()
                              .getCallDetailExtension()
                              .setChar(false, null, Calls.TYPE, Calls.AUTOREJECTED_TYPE, true,
                                      ContactPluginDefault.COMMD_FOR_OP01);
            if (!TextUtils.isEmpty(callsType)) {
                builder.append(callsType);
            }
            /** @} */
        }

        if (builder.length() > 0) {
            return builder.toString();
        } else {
            return null;
        }
    }
    private void log(final String log) {
        Log.i(TAG, log);
    }
    /** @ }*/
}
