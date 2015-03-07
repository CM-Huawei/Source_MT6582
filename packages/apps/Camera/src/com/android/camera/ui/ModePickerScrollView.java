package com.android.camera.ui;


import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ScrollView;

import com.android.camera.Log;
import com.android.camera.Util;

public class ModePickerScrollView extends ScrollView implements ModePickerScrollable {
    private static final String TAG = "ModePickerScrollView";
    private static final int DELAY_HIDE_MS = 3000; //3s
    private View mBackground;
    
    public ModePickerScrollView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }
    
    private Runnable mHideRunnable = new Runnable() {
        
        @Override
        public void run() {
            Log.d(TAG, "mHideRunnable.run()");
            if (mBackground != null) {
                Util.fadeOut(mBackground);
            }
        }
    };
    
    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        Log.d(TAG, "onInterceptTouchEvent(" + ev + ")");
        showBackground();
        return super.onInterceptTouchEvent(ev);
    }
    
    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        Log.d(TAG, "onTouchEvent(" + ev + ")");
        showBackground();
        return super.onTouchEvent(ev);
    }
    
    private void showBackground() {
        if (isEnabled() && mBackground != null) {
            Util.fadeIn(mBackground);
            removeCallbacks(mHideRunnable);
            postDelayed(mHideRunnable, DELAY_HIDE_MS);
        }
    }
    
    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        if (!enabled) {
            mHideRunnable.run();
        }
        Log.d(TAG, "setEnabled(" + enabled + ")");
    }
    
    @Override
    public void setBackgroundView(View view) {
        Log.d(TAG, "setBackgroundView(" + view + ")");
        mBackground = view;
    }
}