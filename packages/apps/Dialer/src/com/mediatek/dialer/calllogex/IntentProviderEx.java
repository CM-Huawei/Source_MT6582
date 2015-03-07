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

import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.provider.CallLog.Calls;

import com.android.contacts.common.CallUtil;
import com.android.contacts.common.util.Constants;
import com.android.dialer.CallDetailActivity;
import com.mediatek.dialer.util.DialerUtils;

/**
 * Used to create an intent to attach to an action in the call log.
 * <p>
 * The intent is constructed lazily with the given information.
 */
public abstract class IntentProviderEx {
    public abstract Intent getIntent(Context context);

    /** M:  delete @ { */
    /**
     * 
    public static IntentProvider getReturnCallIntentProvider(final String number) {
        return new IntentProvider() {
            @Override
            public Intent getIntent(Context context) {
                return CallUtil.getCallIntent(number);
            }
        };
    }
     */
    /** @ } */

    public static IntentProviderEx getPlayVoicemailIntentProvider(final long rowId,
            final String voicemailUri) {
        return new IntentProviderEx() {
            @Override
            public Intent getIntent(Context context) {
                Intent intent = new Intent(context, CallDetailActivity.class);
                intent.setData(ContentUris.withAppendedId(
                        Calls.CONTENT_URI_WITH_VOICEMAIL, rowId));
                if (voicemailUri != null) {
                    intent.putExtra(CallDetailActivity.EXTRA_VOICEMAIL_URI,
                            Uri.parse(voicemailUri));
                }
                intent.putExtra(CallDetailActivity.EXTRA_VOICEMAIL_START_PLAYBACK, true);
                return intent;
            }
        };
    }

    public static IntentProviderEx getCallDetailIntentProvider(
            final CallLogAdapterEx adapter, final int position, final long id, final int groupSize) {
        return new IntentProviderEx() {
            @Override
            public Intent getIntent(Context context) {
                Cursor cursor = adapter.getCursor();
                cursor.moveToPosition(position);
                /** M:  delete @ { */
                // if (CallLogQuery.isSectionHeader(cursor)) {
                // // Do nothing when a header is clicked.
                // return null;
                // }
                /** @ } */
                Intent intent = new Intent(context, CallDetailActivity.class);
                // Check if the first item is a voicemail.
                String voicemailUri = cursor.getString(cursor.getColumnIndex(Calls.VOICEMAIL_URI));
                if (voicemailUri != null) {
                    intent.putExtra(CallDetailActivity.EXTRA_VOICEMAIL_URI,
                            Uri.parse(voicemailUri));
                }
                intent.putExtra(CallDetailActivity.EXTRA_VOICEMAIL_START_PLAYBACK, false);
                
                /** M: New Feature Phone Landscape UI @{ */
                int tagId = cursor.getInt(CallLogQueryEx.ID);
                /** @ }*/

                if (groupSize > 1) {
                    // We want to restore the position in the cursor at the end.
                    long[] ids = new long[groupSize];
                    // Copy the ids of the rows in the group.
                    for (int index = 0; index < groupSize; ++index) {
                        ids[index] = cursor.getLong(CallLogQueryEx.ID);
                        cursor.moveToNext();
                    }
                    intent.putExtra(CallDetailActivity.EXTRA_CALL_LOG_IDS, ids);
                } else {
                    // If there is a single item, use the direct URI for it.
                    intent.setData(ContentUris.withAppendedId(
                            Calls.CONTENT_URI_WITH_VOICEMAIL, id));
                }
                /** M: New Feature Phone Landscape UI @{ */
                // set the tagin in intent to mark the item
                intent.putExtra("TAGID", tagId);
                /** @ }*/

                return intent;
            }
        };
    }

    /** M: add @ { */
    public static IntentProviderEx getReturnCallIntentProvider(final String number, final long simId) {
        return new IntentProviderEx() {
            @Override
            public Intent getIntent(Context context) {
                return DialerUtils.getCallIntent(number).putExtra(
                        Constants.EXTRA_ORIGINAL_SIM_ID, simId).setClassName(
                        Constants.PHONE_PACKAGE, Constants.OUTGOING_CALL_BROADCASTER);
            }
        };
    }
    /** @ }*/
}
