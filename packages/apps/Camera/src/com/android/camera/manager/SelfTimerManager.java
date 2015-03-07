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

package com.android.camera.manager;

import java.util.Locale;

import android.content.Context;
import android.media.AudioManager;
import android.media.SoundPool;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.view.animation.LayoutAnimationController;
import android.view.animation.RotateAnimation;
import android.widget.FrameLayout;
import android.widget.TextView;

import com.android.camera.Camera;
import com.android.camera.Util;
import com.android.camera.R;

import com.mediatek.xlog.Xlog;

public class SelfTimerManager extends ViewManager{
    private static final String TAG = "SelfTimerManager";

    private static final int SELF_TIMER_VOLUME = 100;
    private static final int SELF_TIMER_INTERVAL = 250;
    private static final int SELF_TIMER_SHORT_BOUND = 2000;
    private static final int MAX_DELEY_TIME = 10000; // sec

    // self timer related fields
    private static final int STATE_SELF_TIMER_IDLE = 0;
    private static final int STATE_SELF_TIMER_COUNTING = 1;
    private static final int STATE_SELF_TIMER_SNAP = 2;
    private static final int MSG_SELFTIMER_TIMEOUT = 9;

    private final Handler mHandler;
    private Looper mLooper;
    private int mSelfTimerDuration;
    private int mSelfTimerState;
    private long mTimeSelfTimerStart;
    private boolean mLowStorageTag = false;
    private int mOrientation;
    private TextView mRemainingSecondsView;
    private int mRemainingSecs = 0;
    private Animation mCountDownAnim;
    private boolean mPlaySound;
    private Camera mContext;
    private SoundPool mSoundPool;
    private int mBeepTwice;
    private int mBeepOnce;

    //private static SelfTimerManager sSelfTimerManager;
    private SelfTimerListener mSelfTimerListener;

    public interface SelfTimerListener {
        void onTimerStart();

        void onTimerTimeout();

        void onTimerStop();
    }

    public SelfTimerManager(Looper looper, final Camera context) {
        super(context);
        mLooper = looper;
        mContext = context;
        mSoundPool = new SoundPool(1, AudioManager.STREAM_NOTIFICATION, 0);
        mBeepOnce = mSoundPool.load(context, R.raw.beep_once, 1);
        mBeepTwice = mSoundPool.load(context, R.raw.beep_twice, 1);
        mHandler = new Handler(looper) {
            @Override
            public void handleMessage(Message msg) {
                switch (msg.what) {
                case MSG_SELFTIMER_TIMEOUT:
                    selfTimerTimeout(mRemainingSecs -1);
                    break;
                default:
                    break;
                }
            }
        };
    }

    /*public static synchronized SelfTimerManager getInstance(Looper looper, Camera context) {
        if (sSelfTimerManager == null || sLooper != looper) {
            sSelfTimerManager = new SelfTimerManager(looper, context);
        }
        return sSelfTimerManager;
    }*/

/*    public static void releaseSelfTimerManager() {
        sLooper = null;
        if (sSelfTimerManager != null) {
            sSelfTimerManager.mSoundPool.release();
            sSelfTimerManager.mSoundPool = null;
            sSelfTimerManager.mHandler.removeCallbacksAndMessages(null);
            sSelfTimerManager = null;
        }
    }*/
    
    public void releaseSelfTimer() {
        mLooper = null;
        mSoundPool.release();
        mSoundPool = null;
        mHandler.removeCallbacksAndMessages(null);
    }

    public void setTimerListener(SelfTimerListener listener) {
        mSelfTimerListener = listener;
    }

    public boolean clerSelfTimerState() {
        if (mSelfTimerDuration != 0) {
            if (mSelfTimerState == STATE_SELF_TIMER_COUNTING) {
                selfTimerStop();
            }
            return true;
        }
        return false;
    }

    private synchronized void selfTimerStop() {
        if (mSelfTimerState == STATE_SELF_TIMER_IDLE) {
            return;
        }
        Xlog.i(TAG, "selfTimerStop");
        mSelfTimerState = STATE_SELF_TIMER_IDLE;
        hideTimerView();
        if (mSelfTimerListener != null) {
            mSelfTimerListener.onTimerStop();
            // cancelAutoFocus(); // move
        }
        mHandler.removeMessages(MSG_SELFTIMER_TIMEOUT);
    }

    private synchronized void selfTimerStart() {
        if (mSelfTimerState != STATE_SELF_TIMER_IDLE || mHandler.hasMessages(MSG_SELFTIMER_TIMEOUT) || mLowStorageTag) {
            return;
        }
        mTimeSelfTimerStart = System.currentTimeMillis();
        mSelfTimerState = STATE_SELF_TIMER_COUNTING;
        showTimerView();
        Xlog.i(TAG, "SelfTimer start");
        selfTimerTimeout(mSelfTimerDuration / 1000);
    }
    
    private void showTimerView() {
        show();
        mContext.hideAllViews();
        mRemainingSecondsView.setVisibility(View.VISIBLE);
        mContext.showInfo(mContext.getString(R.string.count_down_title_text), mSelfTimerDuration);
    }

    private synchronized void selfTimerTimeout(int newVal) {
        Xlog.i(TAG, "selfTimerTimeout: newVal = " + newVal);
        mRemainingSecs = newVal;
        if (newVal <= 0) {
            // Countdown has finished
            hideTimerView();
            mSelfTimerState = STATE_SELF_TIMER_SNAP;
            if (mSelfTimerListener != null) {
                Xlog.i(TAG, "onTimerTimeout");
                mSelfTimerListener.onTimerTimeout();
            }
            mSelfTimerState = STATE_SELF_TIMER_IDLE;
        } else {
            Locale locale = mContext.getResources().getConfiguration().locale;
            String localizedValue = String.format(locale, "%d", newVal);
            mRemainingSecondsView.setText(localizedValue);
            // Fade-out animation
            mCountDownAnim.reset();
            mRemainingSecondsView.clearAnimation();
            mRemainingSecondsView.startAnimation(mCountDownAnim);

            // Play sound effect for the last 3 seconds of the countdown
            if (newVal == 1) {
                mSoundPool.play(mBeepTwice, 1.0f, 1.0f, 0, 0, 1.0f);
            } else if (newVal <= 3) {
                mSoundPool.play(mBeepOnce, 1.0f, 1.0f, 0, 0, 1.0f);
            }
            // Schedule the next remainingSecondsChanged() call in 1 second
            mHandler.sendEmptyMessageDelayed(MSG_SELFTIMER_TIMEOUT, 1000);
        }
    }

    public synchronized void breakTimer() {
        if (mSelfTimerState != STATE_SELF_TIMER_IDLE) {
            mHandler.removeMessages(MSG_SELFTIMER_TIMEOUT);
            mSelfTimerState = STATE_SELF_TIMER_IDLE;
            hideTimerView();
            if (mSelfTimerListener != null) {
                mSelfTimerListener.onTimerStop();
            }
        }
    }
    
    private void hideTimerView() {
        mRemainingSecondsView.setVisibility(View.INVISIBLE);
        hide();
        mContext.showAllViews();
        mContext.dismissInfo();
    }

    public void setLowStorage(boolean storage) {
        mLowStorageTag = storage;
        breakTimer();
    }

    public void setSelfTimerDuration(String timeDelay) {
        int delay = Integer.valueOf(timeDelay);
        if (delay < 0 || delay > MAX_DELEY_TIME) {
            throw new RuntimeException("invalid self timer delay");
        }
        mSelfTimerDuration = delay;
    }

    public boolean checkSelfTimerMode() {
        if (mSelfTimerDuration > 0 && mSelfTimerState == STATE_SELF_TIMER_IDLE) {
            selfTimerStart();
            return true;
        } else if (mSelfTimerState == STATE_SELF_TIMER_COUNTING) {
            return true;
        }
        return false;
    }

    public boolean isSelfTimerEnabled() {
        return mSelfTimerDuration > 0;
    }

    public boolean isSelfTimerCounting() {
        return mSelfTimerState == STATE_SELF_TIMER_COUNTING;
    }

    @Override
    protected View getView() {
        mCountDownAnim = AnimationUtils.loadAnimation(mContext, R.anim.count_down_exit);
        View view = inflate(R.layout.count_down_to_capture);
        mRemainingSecondsView = (TextView) view.findViewById(R.id.remaining_seconds);
        return view;
    }
}
