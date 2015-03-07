package com.mediatek.settings.inputmethod;

import android.content.Context;
import android.os.Handler;
import android.os.Message;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.widget.TextView;

import com.mediatek.xlog.Xlog;

public class DoubleClickTestArea extends TextView {

    private static final int TAP = 1;
    private final Handler mHandler;
    private MotionEvent mPreviousDownEvent;
    private MotionEvent mPreviousUpEvent;
    private boolean mIsDoubleTapping;
    private int mDoubleClickDuration = 300;
    private boolean bLargeText = false;
//    private float mTextSize;

    // private Context mContext;

    public DoubleClickTestArea(Context context, AttributeSet attr) {
        super(context, attr);
        // mContext = context;
        mHandler = new DoubleClickHandler();
        setTextSize(18);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        boolean handled = false;
        int action = event.getActionMasked();
        switch (action) {
            case MotionEvent.ACTION_DOWN:
                boolean hadTapMessage = mHandler.hasMessages(TAP);
                if (hadTapMessage)
                    mHandler.removeMessages(TAP);
                if ((mPreviousDownEvent != null) && (mPreviousUpEvent != null) && hadTapMessage
                        && isConsideredDoubleTap(mPreviousDownEvent, mPreviousUpEvent, event)) {
                    // This is a second tap
                    mIsDoubleTapping = true;
                    // Give a callback with the first tap of the double-tap
                    handled = true;
                } else {
                    // This is a first tap
                    mHandler.sendEmptyMessageDelayed(TAP, mDoubleClickDuration);
                    handled = true;
                }
                if (mPreviousDownEvent != null) {
                    mPreviousDownEvent.recycle();
                }
                mPreviousDownEvent = MotionEvent.obtain(event);
                break;
            case MotionEvent.ACTION_UP:
                if (mIsDoubleTapping) {
                    // notify listener
                    if (bLargeText) {
                        setTextSize(18);
                    } else {
                        setTextSize(30);
                    }
                    bLargeText = !bLargeText;
                    handled = true;
                }
                if (mPreviousUpEvent != null) {
                    mPreviousUpEvent.recycle();
                }
                mPreviousUpEvent = MotionEvent.obtain(event);
                mIsDoubleTapping = false;
                handled = true;
                break;
            case MotionEvent.ACTION_CANCEL:
                cancel();
                break;

        }
        return handled;
    }

    public void setDurationTime(int time) {
        mDoubleClickDuration = time;
    }

    private void cancel() {
        mHandler.removeMessages(TAP);
        mIsDoubleTapping = false;
    }

    private boolean isConsideredDoubleTap(MotionEvent firstDown, MotionEvent firstUp, MotionEvent secondDown) {

        if (secondDown.getEventTime() - firstUp.getEventTime() > mDoubleClickDuration) {
            return false;
        }

        int deltaX = (int) firstDown.getX() - (int) secondDown.getX();
        int deltaY = (int) firstDown.getY() - (int) secondDown.getY();
        return (deltaX * deltaX + deltaY * deltaY < 10000);
    }

    private class DoubleClickHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case TAP:
                    // If the user's finger is still down, do not count it as a
                    // tap
                    // Timeout
                    mIsDoubleTapping = false;
                    break;

                default:
                    throw new RuntimeException("Unknown message " + msg); // never
            }
        }
    }
}