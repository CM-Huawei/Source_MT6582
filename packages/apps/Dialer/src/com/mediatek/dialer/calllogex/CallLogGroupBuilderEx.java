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

import android.database.Cursor;
import android.provider.CallLog.Calls;
import android.telephony.PhoneNumberUtils;

import com.android.common.widget.GroupingListAdapter;
import com.google.common.annotations.VisibleForTesting;
import com.mediatek.contacts.util.LogUtils;
import com.mediatek.dialer.calllog.CallLogDateFormatHelper;

/**
 * Groups together calls in the call log.
 * <p>
 * This class is meant to be used in conjunction with {@link GroupingListAdapter}.
 */
public class CallLogGroupBuilderEx {
    public interface GroupCreator {
        public void addGroup(int cursorPosition, int size, boolean expanded);
        /** M: add @ { */
        public void setGroupHeaderPosition(int cursorPosition);
    }

    /** The object on which the groups are created. */
    private final GroupCreator mGroupCreator;

    public CallLogGroupBuilderEx(GroupCreator groupCreator) {
        mGroupCreator = groupCreator;
    }

    /**
     * Finds all groups of adjacent entries in the call log which should be grouped together and
     * calls {@link GroupCreator#addGroup(int, int, boolean)} on {@link #mGroupCreator} for each of
     * them.
     * <p>
     * For entries that are not grouped with others, we do not need to create a group of size one.
     * <p>
     * It assumes that the cursor will not change during its execution.
     *
     * @see GroupingListAdapter#addGroups(Cursor)
     */
    public void addGroups(Cursor cursor) {
        final int count = cursor.getCount();
        /// M: add @{
        LogUtils.d(TAG,"addGroups(), cursor count = " + count);
        /// @}
        if (count == 0) {
            return;
        }

        int currentGroupSize = 1;
        cursor.moveToFirst();
        /// M: modify @{
        /** original code:
        // The number of the first entry in the group.
        String firstNumber = cursor.getString(CallLogQuery.NUMBER);
        This is the type of the first call in the group.
        int firstCallType = cursor.getInt(CallLogQuery.CALL_TYPE);
        */
        // The number of the first entry in the group.
        String firstNumber = cursor.getString(CallLogQueryEx.CALLS_JOIN_DATA_VIEW_NUMBER);
        // This is the type of the first call in the group.
        int firstCallType = cursor.getInt(CallLogQueryEx.CALLS_JOIN_DATA_VIEW_CALL_TYPE);

        //The following lines are provided and maintained by Mediatek Inc.
        int firstSimId = cursor.getInt(CallLogQueryEx.CALLS_JOIN_DATA_VIEW_SIM_ID);
        int firstVtCall = cursor.getInt(CallLogQueryEx.CALLS_JOIN_DATA_VIEW_VTCALL);
        long firstDate = cursor.getLong(CallLogQueryEx.CALLS_JOIN_DATA_VIEW_DATE);
        if (0 != cursor.getCount()) {
            setGroupHeaderPosition(cursor.getPosition());
        }
        /// @}

        while (cursor.moveToNext()) {
            // The number of the current row in the cursor.
            /// M: modify @ {
            /** original code:
            final String currentNumber = cursor.getString(CallLogQuery.NUMBER);
            final int callType = cursor.getInt(CallLogQuery.CALL_TYPE);
             */
            final String currentNumber = cursor.getString(CallLogQueryEx.CALLS_JOIN_DATA_VIEW_NUMBER);
            final int callType = cursor.getInt(CallLogQueryEx.CALLS_JOIN_DATA_VIEW_CALL_TYPE);
            /// @}
            final boolean sameNumber = equalNumbers(firstNumber, currentNumber);
            final boolean shouldGroup;
            /// M: add @{
            final int simId = cursor.getInt(CallLogQueryEx.CALLS_JOIN_DATA_VIEW_SIM_ID);
            final int vtCall = cursor.getInt(CallLogQueryEx.CALLS_JOIN_DATA_VIEW_VTCALL);
            final long date = cursor.getLong(CallLogQueryEx.CALLS_JOIN_DATA_VIEW_DATE);
            final boolean isSameDay = CallLogDateFormatHelper.isSameDay(firstDate, date);
            /// @ }
          /// M: modify @ {
          /** original code:
           if (CallLogQuery.isSectionHeader(cursor)) {
                // Cannot group headers.
                shouldGroup = false;
            } else if (!sameNumber) {
                // Should only group with calls from the same number.
                shouldGroup = false;
            } else if (firstCallType == Calls.VOICEMAIL_TYPE) {
                // never group voicemail.
                shouldGroup = false;
            } else {
                // Incoming, outgoing, and missed calls group together.
                shouldGroup = (callType == Calls.INCOMING_TYPE || callType == Calls.OUTGOING_TYPE ||
                        callType == Calls.MISSED_TYPE);
            }
           */
            /// M: [VVM] voice mail should not be grouped.
            if (firstCallType == Calls.VOICEMAIL_TYPE || !sameNumber || firstCallType != callType
                    || firstSimId != simId || firstVtCall != vtCall || !isSameDay) {
                // Should only group with calls from the same number, the same
                // callType, the same simId and the same vtCall values.
                shouldGroup = false;
            } else {
                shouldGroup = true;
            }
            /// @}

            if (shouldGroup) {
                // Increment the size of the group to include the current call, but do not create
                // the group until we find a call that does not match.
                currentGroupSize++;
            } else {
                // Create a group for the previous set of calls, excluding the current one, but do
                // not create a group for a single call.
                /// M: modify @{
                /** original code:
                if (currentGroupSize > 1) {
                    addGroup(cursor.getPosition() - currentGroupSize, currentGroupSize);
                }
                */
                addGroup(cursor.getPosition() - currentGroupSize, currentGroupSize);
                if (!isSameDay) {
                    setGroupHeaderPosition(cursor.getPosition());
                }
                /// @}
                // Start a new group; it will include at least the current call.
                currentGroupSize = 1;
                // The current entry is now the first in the group.
                firstNumber = currentNumber;
                firstCallType = callType;
                /// M: add @{
                firstCallType = callType;
                firstSimId = simId;
                firstVtCall = vtCall;
                firstDate = date;
                /// @}
            }
        }
        /// M: modify @{
        /** original code:
        // If the last set of calls at the end of the call log was itself a group, create it now.
        // Actually when currentGroupSize is 1, not do group opration,
        // but just move such logic to GroupingListWithCustomViewAdapter
        if (currentGroupSize > 1) {
            addGroup(count - currentGroupSize, currentGroupSize);
        }
        */
        addGroup(count - currentGroupSize, currentGroupSize);
        /// @}
    }

    /**
     * Creates a group of items in the cursor.
     * <p>
     * The group is always unexpanded.
     *
     * @see CallLogAdapterEx#addGroup(int, int, boolean)
     */
    private void addGroup(int cursorPosition, int size) {
        mGroupCreator.addGroup(cursorPosition, size, false);
    }

    @VisibleForTesting
    public boolean equalNumbers(String number1, String number2) {
        if (PhoneNumberUtils.isUriNumber(number1) || PhoneNumberUtils.isUriNumber(number2)) {
            return compareSipAddresses(number1, number2);
        } else {
            return PhoneNumberUtils.compare(number1, number2);
        }
    }

    @VisibleForTesting
    public boolean compareSipAddresses(String number1, String number2) {
        if (number1 == null || number2 == null) return number1 == number2;

        int index1 = number1.indexOf('@');
        final String userinfo1;
        final String rest1;
        if (index1 != -1) {
            userinfo1 = number1.substring(0, index1);
            rest1 = number1.substring(index1);
        } else {
            userinfo1 = number1;
            rest1 = "";
        }

        int index2 = number2.indexOf('@');
        final String userinfo2;
        final String rest2;
        if (index2 != -1) {
            userinfo2 = number2.substring(0, index2);
            rest2 = number2.substring(index2);
        } else {
            userinfo2 = number2;
            rest2 = "";
        }

        return userinfo1.equals(userinfo2) && rest1.equalsIgnoreCase(rest2);
    }

    /**--------------------------------------------- MTK ---------------------------------------*/
    private static final String TAG = "CallLogGroupBuilder";

    public void setGroupHeaderPosition(int cursorPosition) {
        mGroupCreator.setGroupHeaderPosition(cursorPosition);
    }

}
