/*
 * Copyright (C) 2012 The Android Open Source Project
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

package com.android.keyguard;

import android.content.Context;
import android.content.res.Resources;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.util.AttributeSet;
import android.util.Log;
import android.util.Slog;
import android.view.View;
import android.widget.GridLayout;
import android.widget.TextClock;
import android.widget.TextView;

import com.android.internal.widget.LockPatternUtils;

import com.mediatek.keyguard.ext.IDualClock;
import com.mediatek.keyguard.ext.KeyguardPluginFactory;

import java.util.Locale;

public class KeyguardStatusView extends GridLayout {
    private static final boolean DEBUG = KeyguardViewMediator.DEBUG;
    private static final String TAG = "KeyguardStatusView";

    private LockPatternUtils mLockPatternUtils;

    private TextView mAlarmStatusView;
    private TextClock mDateView;
    //private TextClock mClockView;
	private ClockView mClockView ;

    /// M: For dual clock
    private IDualClock mDualClock;

    private KeyguardUpdateMonitorCallback mInfoCallback = new KeyguardUpdateMonitorCallback() {

        @Override
        public void onTimeChanged() {
            refresh();
        }

        @Override
        void onKeyguardVisibilityChanged(boolean showing) {
            if (showing) {
                if (DEBUG) Slog.v(TAG, "refresh statusview showing:" + showing);
                refresh();
            }
        };

        @Override
        public void onScreenTurnedOn() {
            setEnableMarquee(true);
        };

        @Override
        public void onScreenTurnedOff(int why) {
            setEnableMarquee(false);
        };
    };

    public KeyguardStatusView(Context context) {
        this(context, null, 0);
    }

    public KeyguardStatusView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public KeyguardStatusView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        /// M: Init dual clock plugin
        initDualClock();
    }

    /**
     * Init dual clock plugin
     */
    private void initDualClock() {
        mDualClock = KeyguardPluginFactory.getDualClock(mContext);
    }


    private void setEnableMarquee(boolean enabled) {
        if (DEBUG) Log.v(TAG, (enabled ? "Enable" : "Disable") + " transport text marquee");
        if (mAlarmStatusView != null) mAlarmStatusView.setSelected(enabled);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mDualClock.createClockView(getContext(), this);
        
        mAlarmStatusView = (TextView) findViewById(R.id.alarm_status);
        mDateView = (TextClock) findViewById(R.id.date_view);
        mClockView = (ClockView) findViewById(R.id.clock_view);
        mLockPatternUtils = new LockPatternUtils(getContext());
        final boolean screenOn = KeyguardUpdateMonitor.getInstance(mContext).isScreenOn();
        setEnableMarquee(screenOn);

        /// M: For loading the mediatek resource and avoid changing a lot. @{
        if (mDateView == null) {
            KeyguardUtils.xlogD(TAG, "onFinishInflate mDateView == null");
            mDateView = (TextClock) findViewById(R.id.date_view);
        }
        if (mAlarmStatusView == null) {
            KeyguardUtils.xlogD(TAG, "onFinishInflate mAlarmStatusView == null");
            mAlarmStatusView = (TextView) findViewById(R.id.alarm_status);
        }
        if (mClockView == null) {
            KeyguardUtils.xlogD(TAG, "onFinishInflate mClockView == null");
            mClockView = (ClockView) findViewById(R.id.clock_view);
        }
        /// @}
        
        KeyguardUtils.xlogD(TAG, "onFinishInflate --before-- new LockPatternUtils(getContext())");
        mLockPatternUtils = new LockPatternUtils(getContext());

        KeyguardUtils.xlogD(TAG, "onFinishInflate --before-- refresh()");
        refresh();
    }

    protected void refresh() {
        KeyguardUtils.xlogD(TAG, "refresh mClockView.updateTime()");
        Resources res = mContext.getResources();
        Locale locale = Locale.getDefault();
        final String dateFormat = DateFormat.getBestDateTimePattern(locale,
                res.getString(R.string.abbrev_wday_month_day_no_year));

        mDateView.setFormat24Hour(dateFormat);
        mDateView.setFormat12Hour(dateFormat);

        KeyguardUtils.xlogD(TAG, "refresh mClockView.updateTime()");
        mClockView.updateTime();
        KeyguardUtils.xlogD(TAG, "refresh refreshDate()");

        refreshAlarmStatus();
    }

    void refreshAlarmStatus() {
        // Update Alarm status
        String nextAlarm = mLockPatternUtils.getNextAlarm();
        if (!TextUtils.isEmpty(nextAlarm)) {
            mAlarmStatusView.setText(nextAlarm);
            mAlarmStatusView.setVisibility(View.VISIBLE);
        } else {
            mAlarmStatusView.setVisibility(View.GONE);
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        KeyguardUpdateMonitor.getInstance(mContext).registerCallback(mInfoCallback);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        KeyguardUpdateMonitor.getInstance(mContext).removeCallback(mInfoCallback);
        /// M: Unregister the phone listener for dual clock
        mDualClock.resetPhonelistener();
    }

    public int getAppWidgetId() {
        return LockPatternUtils.ID_DEFAULT_STATUS_WIDGET;
    }

    // DateFormat.getBestDateTimePattern is extremely expensive, and refresh is called often.
    // This is an optimization to ensure we only recompute the patterns when the inputs change.
    private static final class Patterns {
        static String dateView;
        static String clockView12;
        static String clockView24;
        static String cacheKey;

        static void update(Context context) {
            final Locale locale = Locale.getDefault();
            final Resources res = context.getResources();
            final String dateViewSkel = res.getString(R.string.abbrev_wday_month_day_no_year);
            final String clockView12Skel = res.getString(R.string.clock_12hr_format);
            final String clockView24Skel = res.getString(R.string.clock_24hr_format);
            final String key = locale.toString() + dateViewSkel + clockView12Skel + clockView24Skel;
            if (key.equals(cacheKey)) return;

            dateView = DateFormat.getBestDateTimePattern(locale, dateViewSkel);

            clockView12 = DateFormat.getBestDateTimePattern(locale, clockView12Skel);
            // CLDR insists on adding an AM/PM indicator even though it wasn't in the skeleton
            // format.  The following code removes the AM/PM indicator if we didn't want it.
            if (!clockView12Skel.contains("a")) {
                clockView12 = clockView12.replaceAll("a", "").trim();
            }

            clockView24 = DateFormat.getBestDateTimePattern(locale, clockView24Skel);

            cacheKey = key;
        }
    }
    
    /*
     * M: For CR ALPS00333114
     * 
     * We need update updateStatusLines when dialog dismiss 
     * which is in font of lock screen.
     * 
     * @see android.view.View#onWindowFocusChanged(boolean)
     */
    @Override
    public void onWindowFocusChanged(boolean hasWindowFocus) {
        super.onWindowFocusChanged(hasWindowFocus);
        if (hasWindowFocus) {
            refresh();
        }
    }
    
    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);
        mDualClock.updateClockLayout();
    }
}
