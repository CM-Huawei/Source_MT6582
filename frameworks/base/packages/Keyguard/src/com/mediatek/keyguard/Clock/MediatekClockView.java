package com.android.keyguard;


import java.util.Calendar;
import java.util.TimeZone;



import android.content.Context;
import android.graphics.Typeface;
import android.text.format.DateFormat;
import android.util.AttributeSet;
import android.widget.TextView;

//import com.android.internal.R;
import com.android.keyguard.MediatekClock.AmPm;
import com.mediatek.xlog.Xlog;


public class MediatekClockView extends MediatekClock {

    private static final String TAG = "MediatekClockView";
    
	public MediatekClockView(Context context) {
		super(context);
		// TODO Auto-generated constructor stub
	}
	
    public MediatekClockView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }
    
    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mTimeView = (TextView) findViewById(R.id.clock_text);
        mTimeView.setTextColor(0xFF000000);
        mTimeView.setTypeface(Typeface.createFromFile(ANDROID_CLOCK_FONT_FILE));
        mAmPm = new AmPm(this, null);
        mAmPmTextView = (TextView) findViewById(R.id.am_pm);
        mCalendar = Calendar.getInstance();
        setDateFormat();
    }
    
    public void updateTime() {
        mCalendar.setTimeInMillis(System.currentTimeMillis());

        CharSequence systemTime = DateFormat.format(mFormat, mCalendar);
        Xlog.d(TAG, "keyguard updateTime systemTime=" + systemTime);
        mTimeView.setText(systemTime);
        mAmPm.setIsMorning(mCalendar.get(Calendar.AM_PM) == 0);
    }
}
