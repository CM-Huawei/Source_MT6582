package com.android.keyguard;

import java.util.Calendar;
import java.util.Date;
import java.util.SimpleTimeZone;
import java.util.TimeZone;

import android.content.Context;
import android.graphics.Typeface;
import android.text.format.DateFormat;
import android.util.AttributeSet;
import android.widget.TextView;

import com.mediatek.xlog.Xlog;

public class MediatekDomesticClockView extends MediatekClock {
    private static final String TIMEZONE_BEIJING = "GMT+8:00";

    private static final String TAG = "MediatekDomesticClockView";

    public MediatekDomesticClockView(Context context) {
        super(context);
    }

    public MediatekDomesticClockView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mTimeView = (TextView) findViewById(R.id.clock_text_domestic);
        mTimeView.setTypeface(Typeface.createFromFile(ANDROID_CLOCK_FONT_FILE));
        
        mWeekDayView  = (TextView) findViewById(R.id.weekday_domestic);
        mTimeView.setTypeface(Typeface.createFromFile(ANDROID_CLOCK_FONT_FILE));
        
        mAmPm = new AmPm(this, null);
        mAmPmTextView = (TextView) findViewById(R.id.am_pm_domestic);
        setDateFormat();
        mCalendar = Calendar.getInstance();
    }
    
    public void updateTime() {
        int beijingoffset = TimeZone.getTimeZone(TIMEZONE_BEIJING).getRawOffset();
        int currentoffset = TimeZone.getTimeZone(TimeZone.getDefault().getID()).getOffset(System.currentTimeMillis());

        mCalendar.setTimeInMillis(System.currentTimeMillis() + (beijingoffset - currentoffset));

        CharSequence beijingTime = DateFormat.format(mFormat, mCalendar);
        Xlog.d(TAG, "keyguard updateTime beijingTime=" + beijingTime);
        mTimeView.setText(beijingTime);
        mAmPm.setIsMorning(mCalendar.get(Calendar.AM_PM) == 0);
        
        CharSequence beijingWeekDay = DateFormat.format("E", mCalendar);
        Xlog.d(TAG, "keyguard updateTime beijingWeekDay=" + beijingWeekDay);
        mWeekDayView.setText(beijingWeekDay);
    }
}
