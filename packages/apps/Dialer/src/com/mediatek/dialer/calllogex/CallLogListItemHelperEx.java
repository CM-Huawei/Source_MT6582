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

import android.content.res.Resources;
//import android.text.TextUtils;
//import android.view.View;

import com.android.dialer.R;
import com.mediatek.dialer.PhoneCallDetailsHelperEx;

/**
 * Helper class to fill in the views of a call log entry.
 */
/*package*/ class CallLogListItemHelperEx {
    /** Helper for populating the details of a phone call. */
    private final PhoneCallDetailsHelperEx mPhoneCallDetailsHelper;
    /** Helper for handling phone numbers. */
    private final PhoneNumberHelperEx mPhoneNumberHelper;
    /** Resources to look up strings. */
    private final Resources mResources;

    /**
     * Creates a new helper instance.
     *
     * @param phoneCallDetailsHelper used to set the details of a phone call
     * @param phoneNumberHelper used to process phone number
     */
    public CallLogListItemHelperEx(PhoneCallDetailsHelperEx phoneCallDetailsHelper,
            PhoneNumberHelperEx phoneNumberHelper, Resources resources) {
        mPhoneCallDetailsHelper = phoneCallDetailsHelper;
        mPhoneNumberHelper = phoneNumberHelper;
        mResources = resources;
    }
    
    /** M:  delete @ { */
    /**
     * Sets the name, label, and number for a contact.
     *
     * @param views the views to populate
     * @param details the details of a phone call needed to fill in the data
     * @param isHighlighted whether to use the highlight text for the call
     */
    /**
     * 
    public void setPhoneCallDetails(CallLogListItemViews views, PhoneCallDetails details,
            boolean isHighlighted) {
        mPhoneCallDetailsHelper.setPhoneCallDetails(views.phoneCallDetailsViews, details,
                isHighlighted);
        boolean canCall = mPhoneNumberHelper.canPlaceCallsTo(details.number);
        boolean canPlay = details.callTypes[0] == Calls.VOICEMAIL_TYPE;

        if (canPlay) {
            // Playback action takes preference.
            configurePlaySecondaryAction(views, isHighlighted);
            views.dividerView.setVisibility(View.VISIBLE);
        } else if (canCall) {
            // Call is the secondary action.
            configureCallSecondaryAction(views, details);
            views.dividerView.setVisibility(View.VISIBLE);
        } else {
            // No action available.
            views.secondaryActionView.setVisibility(View.GONE);
            views.dividerView.setVisibility(View.GONE);
        }
    }
     */
   
    /** Sets the secondary action to correspond to the call button. */
    /**
     * 
    private void configureCallSecondaryAction(CallLogListItemViews views,
            PhoneCallDetails details) {
        views.secondaryActionView.setVisibility(View.VISIBLE);
        views.secondaryActionView.setImageResource(R.drawable.ic_ab_dialer_holo_dark);
        views.secondaryActionView.setContentDescription(getCallActionDescription(details));
    }
     */

    /** Returns the description used by the call action for this phone call. */
    /**
     * 
    private CharSequence getCallActionDescription(PhoneCallDetails details) {
        final CharSequence recipient;
        if (!TextUtils.isEmpty(details.name)) {
            recipient = details.name;
        } else {
            recipient = mPhoneNumberHelper.getDisplayNumber(
                    details.number, details.formattedNumber);
        }
        return mResources.getString(R.string.description_call, recipient);
    }
     */

    /** Sets the secondary action to correspond to the play button. */
    /**
     * 
    private void configurePlaySecondaryAction(CallLogListItemViews views, boolean isHighlighted) {
        views.secondaryActionView.setVisibility(View.VISIBLE);
        views.secondaryActionView.setImageResource(
                isHighlighted ? R.drawable.ic_play_active_holo_dark : R.drawable.ic_play_holo_dark);
        views.secondaryActionView.setContentDescription(
                mResources.getString(R.string.description_call_log_play_button));
    }
     */
    /** @ } */
}
