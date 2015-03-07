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

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.provider.CallLog.Calls;
import android.util.AttributeSet;
import android.view.View;

import com.android.contacts.common.test.NeededForTesting;
import com.android.dialer.R;
import com.google.common.collect.Lists;

import java.util.List;

/**
 * View that draws one or more symbols for different types of calls (missed calls, outgoing etc).
 * The symbols are set up horizontally. As this view doesn't create subviews, it is better suited
 * for ListView-recycling that a regular LinearLayout using ImageViews.
 */
public class CallTypeIconsViewEx extends View {
    
    /** M:  modify:Goole default display three type icons in call log list,
                 MTK only display only one. @ { */
    /**
     * private List<Integer> mCallTypes = Lists.newArrayListWithCapacity(3);
     */
    
    private int mCallType;
    /** @ }*/
    private Resources mResources;
    private int mWidth;
    private int mHeight;

    public CallTypeIconsViewEx(Context context) {
        this(context, null);
    }

    public CallTypeIconsViewEx(Context context, AttributeSet attrs) {
        super(context, attrs);
        mResources = new Resources(context);
    }

    public void clear() {
        /** M:  modify @ { */
        /**
         * mCallTypes.clear();
         */
        mCallType = -1;
        /** @ }*/
        mWidth = 0;
        mHeight = 0;
        invalidate();
    }
    /** M:  delete @ { */
    /**
    public void add(int callType) {
        mCallTypes.add(callType);

        final Drawable drawable = getCallTypeDrawable(callType);
        mWidth += drawable.getIntrinsicWidth() + mResources.iconMargin;
        mHeight = Math.max(mHeight, drawable.getIntrinsicHeight());
        invalidate();
    }

    @NeededForTesting
    public int getCount() {
        return mCallTypes.size();
    }

    @NeededForTesting
    public int getCallType(int index) {
        return mCallTypes.get(index);
    }

    private Drawable getCallTypeDrawable(int callType) {
        switch (callType) {
            case Calls.INCOMING_TYPE:
                return mResources.incoming;
            case Calls.OUTGOING_TYPE:
                return mResources.outgoing;
            case Calls.MISSED_TYPE:
                return mResources.missed;
            case Calls.VOICEMAIL_TYPE:
                return mResources.voicemail;
            default:
                throw new IllegalArgumentException("invalid call type: " + callType);
        }
    }
   */
    /** @ }*/

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        setMeasuredDimension(mWidth, mHeight);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        /** M:  modify @ { */
        /**
         * int left = 0;
        for (Integer callType : mCallTypes) {
            final Drawable drawable = getCallTypeDrawable(callType);
            final int right = left + drawable.getIntrinsicWidth();
            drawable.setBounds(left, 0, right, drawable.getIntrinsicHeight());
            drawable.draw(canvas);
            left = right + mResources.iconMargin;
        }
        */
        final Drawable drawable = getCallTypeDrawable(mCallType, mVTCall);
        if (null == drawable) {
            return;
        }
        drawable.setBounds(0, 0, drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight());
        drawable.draw(canvas);
        /** @ } */
    }

    private static class Resources {
        public final Drawable incoming;
        public final Drawable outgoing;
        public final Drawable missed;
        public final Drawable voicemail;
        public final int iconMargin;
        /** M: add @ { */
        public final Drawable vtincoming;
        public final Drawable vtoutgoing;
        public final Drawable vtmissed;
        public final Drawable autorejected;
        public final Drawable vtautorejected;
        /** @ }*/

        public Resources(Context context) {
            final android.content.res.Resources r = context.getResources();
            incoming = r.getDrawable(R.drawable.ic_call_incoming_holo_dark);
            outgoing = r.getDrawable(R.drawable.ic_call_outgoing_holo_dark);
            missed = r.getDrawable(R.drawable.ic_call_missed_holo_dark);
            /**M: [VVM] set voice mail icon color red.*/
            voicemail = r.getDrawable(R.drawable.mtk_ic_call_voicemail_holo_dark_red);
            iconMargin = r.getDimensionPixelSize(R.dimen.call_log_icon_margin);
            /** M: add @ { */
            vtincoming = r.getDrawable(R.drawable.mtk_ic_video_call_incoming_holo_dark);
            vtoutgoing = r.getDrawable(R.drawable.mtk_ic_video_call_outgoing_holo_dark);
            vtmissed = r.getDrawable(R.drawable.mtk_ic_video_call_missed_holo_dark);
            autorejected = r.getDrawable(R.drawable.mtk_ic_call_autorejected_holo_dark);
            vtautorejected = r.getDrawable(R.drawable.mtk_ic_video_call_autorejected_holo_dark);
            /** @ }*/
        }
    }

    /** M: add @ { */
    private int mVTCall;
    
    public int getCallType() {
        return mCallType;
    }

    public void set(int callType, int isVTCall) {
        mCallType = callType;
        mVTCall = isVTCall;
        final Drawable drawable = getCallTypeDrawable(callType, isVTCall);
        if (null == drawable) {
            return;
        }
        mWidth = drawable.getIntrinsicWidth();
        mHeight = Math.max(mHeight, drawable.getIntrinsicHeight());
        /** M: set current dimension val when set calltype @{ */
        setMeasuredDimension(mWidth, mHeight);
        /**@}*/
        invalidate();
    }

    private Drawable getCallTypeDrawable(int callType, int isVTCall) {
        switch (callType) {
            case Calls.INCOMING_TYPE:
                if (isVTCall == 1) {
                    return mResources.vtincoming;
                } else {
                    return mResources.incoming;
                }
            case Calls.OUTGOING_TYPE:
                if (isVTCall == 1) {
                    return mResources.vtoutgoing;
                } else {
                    return mResources.outgoing;
                }
            case Calls.MISSED_TYPE:
                if (isVTCall == 1) {
                    return mResources.vtmissed;
                } else {
                    return mResources.missed;
                }
            case Calls.VOICEMAIL_TYPE:
                return mResources.voicemail;
            case Calls.AUTOREJECTED_TYPE:
                if (isVTCall == 1) {
                    return mResources.vtautorejected;
                } else {
                    return mResources.autorejected;
                }
            default:
                throw new IllegalArgumentException("invalid call type: " + callType);
        }
    }
    /** @ } */
}
