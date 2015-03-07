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

package com.android.dialer;

import java.util.Date;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.provider.ContactsContract;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.telephony.PhoneNumberUtils;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.text.format.DateUtils;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import com.android.contacts.common.format.FormatUtils;
import com.android.contacts.common.test.NeededForTesting;
import com.android.dialer.calllog.CallTypeHelper;
import com.android.dialer.calllog.ContactInfo;
import com.android.dialer.calllog.PhoneNumberHelper;
import com.android.dialer.calllog.PhoneNumberUtilsWrapper;
import com.mediatek.contacts.ExtensionManager;
import com.mediatek.dialer.PhoneCallDetailsEx;
import com.mediatek.dialer.calllog.CallLogListItemView;
import com.mediatek.dialer.calllog.CallLogSimInfoHelper;
import com.mediatek.dialer.calllogex.CallTypeHelperEx;
import com.mediatek.dialer.calllogex.PhoneNumberHelperEx;
import com.mediatek.dialer.util.LogUtils;

/**
 * Helper class to fill in the views in {@link PhoneCallDetailsViews}.
 */
public class PhoneCallDetailsHelper {
    /** The maximum number of icons will be shown to represent the call types in a group. */
    private static final int MAX_CALL_TYPE_ICONS = 3;

    private final Resources mResources;
    /** The injected current time in milliseconds since the epoch. Used only by tests. */
    private Long mCurrentTimeMillisForTest;
    // Helper classes.
    private final CallTypeHelper mCallTypeHelper;
    private final PhoneNumberHelperEx mPhoneNumberHelper;
    private final PhoneNumberUtilsWrapper mPhoneNumberUtilsWrapper;

    /**
     * Creates a new instance of the helper.
     * <p>
     * Generally you should have a single instance of this helper in any context.
     *
     * @param resources used to look up strings
     */
    public PhoneCallDetailsHelper(Resources resources, CallTypeHelper callTypeHelper,
            PhoneNumberUtilsWrapper phoneUtils) {
        mResources = resources;
        mCallTypeHelper = callTypeHelper;
        mPhoneNumberHelper = new PhoneNumberHelperEx(resources);
        mPhoneNumberUtilsWrapper = phoneUtils;
    }

    /** Fills the call details views with content. */
    public void setPhoneCallDetails(PhoneCallDetailsViews views, PhoneCallDetails details,
            boolean isHighlighted) {
        // Display up to a given number of icons.
        views.callTypeIcons.clear();
        int count = details.callTypes.length;
        for (int index = 0; index < count && index < MAX_CALL_TYPE_ICONS; ++index) {
            /// M: phone favorite fragment refactoring.@{
            /** original code:
             views.callTypeIcons.add(details.callTypes[index]);
             */
            views.callTypeIcons.add(details.callTypes[index], details.isVtCall);
            /// @}
        }
        views.callTypeIcons.requestLayout();
        views.callTypeIcons.setVisibility(View.VISIBLE);

        // Show the total call count only if there are more than the maximum number of icons.
        final Integer callCount;
        if (count > MAX_CALL_TYPE_ICONS) {
            callCount = count;
        } else {
            callCount = null;
        }
        // The color to highlight the count and date in, if any. This is based on the first call.
        Integer highlightColor =
                isHighlighted ? mCallTypeHelper.getHighlightedColor(details.callTypes[0]) : null;

        // The date of this call, relative to the current time.
        CharSequence dateText =
            DateUtils.getRelativeTimeSpanString(details.date,
                    getCurrentTimeMillis(),
                    DateUtils.MINUTE_IN_MILLIS,
                    DateUtils.FORMAT_ABBREV_RELATIVE);
        /// M: ALPS01298236 @{
        // UI display call time.
        if (mContext != null) {
            dateText = DateFormat.getTimeFormat(mContext).format(new Date(details.date));
        }
        /// @}

        // Set the call count and date.
        setCallCountAndDate(views, callCount, dateText, highlightColor);

        /// M: ALPS01298236 @{
        // UI Display for VoiceMail and ECC number.
        if (updateNameAndLabelForEcc(views, details)
                || updateNameAndLabelForVoiceMail(views, details)) {
            LogUtils.d(TAG, "setPhoneCallDetails: updateNameAndLabelForEcc or updateNameAndLabelForVoiceMail");
            return;
        }
        /// @}

        CharSequence numberFormattedLabel = null;
        // Only show a label if the number is shown and it is not a SIP address.
        if (!TextUtils.isEmpty(details.number)
                && !PhoneNumberUtils.isUriNumber(details.number.toString())) {
            if (details.numberLabel == ContactInfo.GEOCODE_AS_LABEL) {
                numberFormattedLabel = details.geocode;
            } else {
                numberFormattedLabel = Phone.getTypeLabel(mResources, details.numberType,
                        details.numberLabel);
            }
        }

        final CharSequence nameText;
        final CharSequence numberText;
        final CharSequence labelText;
        final CharSequence displayNumber = mPhoneNumberHelper.getDisplayNumber(details.number, details.formattedNumber);
        if (TextUtils.isEmpty(details.name)) {
            /// M: fix CR ALPS01276025.
            // when the number and name are both empty, show it as unknown.
            /** original code:
             nameText = displayNumber;
             */
            if (TextUtils.isEmpty(displayNumber)) {
                nameText = mResources.getString(R.string.unknown);
            } else {
                nameText = displayNumber;
            }
            /// @}
            if (TextUtils.isEmpty(details.geocode)
                    || mPhoneNumberUtilsWrapper.isVoicemailNumber(details.number)) {
                numberText = mResources.getString(R.string.call_log_empty_gecode);
            } else {
                numberText = details.geocode;
            }
            labelText = numberText;
            // We have a real phone number as "nameView" so make it always LTR
            views.nameView.setTextDirection(View.TEXT_DIRECTION_LTR);
        } else {
            nameText = details.name;
            numberText = displayNumber;
            labelText = TextUtils.isEmpty(numberFormattedLabel) ? numberText :
                    numberFormattedLabel;
        }

        views.nameView.setText(nameText);

        views.labelView.setText(labelText);
        views.labelView.setVisibility(TextUtils.isEmpty(labelText) ? View.GONE : View.VISIBLE);
    }

    /** Sets the text of the header view for the details page of a phone call. */
    public void setCallDetailsHeader(TextView nameView, PhoneCallDetails details) {
        final CharSequence nameText;
        final CharSequence displayNumber = mPhoneNumberHelper.getDisplayNumber(details.number, mResources.getString(R.string.recentCalls_addToContact));
        if (TextUtils.isEmpty(details.name)) {
            nameText = displayNumber;
        } else {
            nameText = details.name;
        }

        nameView.setText(nameText);
    }

    @NeededForTesting
    public void setCurrentTimeForTest(long currentTimeMillis) {
        mCurrentTimeMillisForTest = currentTimeMillis;
    }

    /**
     * Returns the current time in milliseconds since the epoch.
     * <p>
     * It can be injected in tests using {@link #setCurrentTimeForTest(long)}.
     */
    private long getCurrentTimeMillis() {
        if (mCurrentTimeMillisForTest == null) {
            return System.currentTimeMillis();
        } else {
            return mCurrentTimeMillisForTest;
        }
    }

    /** Sets the call count and date. */
    private void setCallCountAndDate(PhoneCallDetailsViews views, Integer callCount,
            CharSequence dateText, Integer highlightColor) {
        // Combine the count (if present) and the date.
        final CharSequence text;
        if (callCount != null) {
            text = mResources.getString(
                    R.string.call_log_item_count_and_date, callCount.intValue(), dateText);
        } else {
            text = dateText;
        }

        // Apply the highlight color if present.
        final CharSequence formattedText;
        if (highlightColor != null) {
            formattedText = addBoldAndColor(text, highlightColor);
        } else {
            formattedText = text;
        }

        views.callTypeAndDate.setText(formattedText);
    }

    /** Creates a SpannableString for the given text which is bold and in the given color. */
    private CharSequence addBoldAndColor(CharSequence text, int color) {
        int flags = Spanned.SPAN_INCLUSIVE_INCLUSIVE;
        SpannableString result = new SpannableString(text);
        result.setSpan(new StyleSpan(Typeface.BOLD), 0, text.length(), flags);
        result.setSpan(new ForegroundColorSpan(color), 0, text.length(), flags);
        return result;
    }

    // ---------------------- MTK ---------------------------
    private static final String TAG = "PhoneCallDetailsHelper";
    private Context mContext;

    public PhoneCallDetailsHelper(Resources resources, CallTypeHelper callTypeHelper,
            PhoneNumberUtilsWrapper phoneUtils, Context context) {
        mResources = resources;
        mCallTypeHelper = callTypeHelper;
        mPhoneNumberHelper = new PhoneNumberHelperEx(resources);
        mPhoneNumberUtilsWrapper = phoneUtils;
        mContext = context;
    }

    public boolean updateNameAndLabelForEcc(PhoneCallDetailsViews views, PhoneCallDetails details){
        final boolean isEmergencyNumber = PhoneNumberHelperEx.isECCNumber(details.number, details.simId);

        if (isEmergencyNumber) {
            views.nameView.setText(mResources.getString(R.string.emergencycall));
            views.labelView.setText(details.number);
            views.labelView.setVisibility(TextUtils.isEmpty(details.number) ? View.GONE : View.VISIBLE);
        }
        return isEmergencyNumber;
    }

    public boolean updateNameAndLabelForVoiceMail(PhoneCallDetailsViews views, PhoneCallDetails details) {
        final boolean isVoiceMailNumber = PhoneNumberHelperEx.isSimVoiceMailNumber(details.number, details.simId);
        if (isVoiceMailNumber) {
            views.nameView.setText(mResources.getString(R.string.voicemail));
            views.labelView.setText(details.number);
            views.labelView.setVisibility(TextUtils.isEmpty(details.number) ? View.GONE
                    : View.VISIBLE);
        }
        return isVoiceMailNumber;
    }
}
