/*
 * Copyright (C) 2007 The Android Open Source Project
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

import android.app.PendingIntent;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import com.android.internal.policy.IKeyguardShowCallback;
import com.android.internal.widget.LockPatternUtils;

import android.app.Activity;
import android.app.ActivityManager;
import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Parcelable;
import android.os.RemoteException;
import android.os.SystemProperties;
import android.util.Log;
import android.util.Slog;
import android.util.SparseArray;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewManager;
import android.view.WindowManager;
import android.widget.FrameLayout;

/**
 * Manages creating, showing, hiding and resetting the keyguard.  Calls back
 * via {@link KeyguardViewMediator.ViewMediatorCallback} to poke
 * the wake lock and report that the keyguard is done, which is in turn,
 * reported to this class by the current {@link KeyguardViewBase}.
 */
public class KeyguardViewManager {
    private final static boolean DEBUG = KeyguardViewMediator.DEBUG;
    private static String TAG = "KeyguardViewManager";
    public final static String IS_SWITCHING_USER = "is_switching_user";

    // Delay dismissing keyguard to allow animations to complete.
    private static final int HIDE_KEYGUARD_DELAY = 500;

    // Timeout used for keypresses
    static final int DIGIT_PRESS_WAKE_MILLIS = 5000;

    private final Context mContext;
    private final ViewManager mViewManager;
    private final KeyguardViewMediator.ViewMediatorCallback mViewMediatorCallback;

    private WindowManager.LayoutParams mWindowLayoutParams;
    private boolean mNeedsInput = false;

    private ViewManagerHost mKeyguardHost;
    private KeyguardHostView mKeyguardView;

    private boolean mScreenOn = false;
    private LockPatternUtils mLockPatternUtils;

    private KeyguardUpdateMonitorCallback mBackgroundChanger = new KeyguardUpdateMonitorCallback() {
        @Override
        public void onSetBackground(Bitmap bmp) {
            mKeyguardHost.setCustomBackground(bmp != null ?
                    new BitmapDrawable(mContext.getResources(), bmp) : null);
            updateShowWallpaper(bmp == null);
        }
    };

    public interface ShowListener {
        void onShown(IBinder windowToken);
    };

    /**
     * @param context Used to create views.
     * @param viewManager Keyguard will be attached to this.
     * @param callback Used to notify of changes.
     * @param lockPatternUtils
     */
    public KeyguardViewManager(Context context, ViewManager viewManager,
            KeyguardViewMediator.ViewMediatorCallback callback,
            LockPatternUtils lockPatternUtils) {
        mContext = context;
        mViewManager = viewManager;
        mViewMediatorCallback = callback;
        mLockPatternUtils = lockPatternUtils;
    }

    /**
     * Show the keyguard.  Will handle creating and attaching to the view manager
     * lazily.
     */
    public synchronized void show(Bundle options) {
        if (DEBUG) Log.d(TAG, "show(); mKeyguardView=" + mKeyguardView);

        boolean enableScreenRotation = KeyguardUtils.shouldEnableScreenRotation(mContext);
        if (DEBUG) Log.d(TAG, "show() query screen rotation after");

        /// M: Incoming Indicator for Keyguard Rotation @{
        KeyguardUpdateMonitor.getInstance(mContext).setQueryBaseTime();
        /// @}
        maybeCreateKeyguardLocked(enableScreenRotation, false, options);
        
        if (DEBUG) Log.d(TAG, "show() maybeCreateKeyguardLocked finish");
        
        maybeEnableScreenRotation(enableScreenRotation);

        // Disable common aspects of the system/status/navigation bars that are not appropriate or
        // useful on any keyguard screen but can be re-shown by dialogs or SHOW_WHEN_LOCKED
        // activities. Other disabled bits are handled by the KeyguardViewMediator talking
        // directly to the status bar service.
        int visFlags = View.STATUS_BAR_DISABLE_HOME;
        if (shouldEnableTranslucentDecor()) {
            mWindowLayoutParams.flags |= WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS
                                       | WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION;
        }
        if (DEBUG) Log.d(TAG, "show:setSystemUiVisibility(" + Integer.toHexString(visFlags)+")");
        mKeyguardHost.setSystemUiVisibility(visFlags);

        mViewManager.updateViewLayout(mKeyguardHost, mWindowLayoutParams);
        mKeyguardHost.setVisibility(View.VISIBLE);
        mKeyguardView.show();
        mKeyguardView.requestFocus();
        if (DEBUG) Log.d(TAG, "show() exit; mKeyguardView=" + mKeyguardView);
    }

    private boolean shouldEnableTranslucentDecor() {
        Resources res = mContext.getResources();
        return res.getBoolean(R.bool.config_enableLockScreenTranslucentDecor);
    }

    class ViewManagerHost extends FrameLayout {
        private static final int BACKGROUND_COLOR = 0x70000000;

        private Drawable mCustomBackground;

        // This is a faster way to draw the background on devices without hardware acceleration
        private final Drawable mBackgroundDrawable = new Drawable() {
            @Override
            public void draw(Canvas canvas) {
                if (mCustomBackground != null) {
                    final Rect bounds = mCustomBackground.getBounds();
                    final int vWidth = getWidth();
                    final int vHeight = getHeight();

                    final int restore = canvas.save();
                    canvas.translate(-(bounds.width() - vWidth) / 2,
                            -(bounds.height() - vHeight) / 2);
                    mCustomBackground.draw(canvas);
                    canvas.restoreToCount(restore);
                } else {
                    canvas.drawColor(BACKGROUND_COLOR, PorterDuff.Mode.SRC);
                }
            }

            @Override
            public void setAlpha(int alpha) {
            }

            @Override
            public void setColorFilter(ColorFilter cf) {
            }

            @Override
            public int getOpacity() {
                return PixelFormat.TRANSLUCENT;
            }
        };

        public ViewManagerHost(Context context) {
            super(context);
            setBackground(mBackgroundDrawable);

            /// M: Save initial config when view created
            mCreateOrientation = context.getResources().getConfiguration().orientation;
            mCreateScreenWidthDp = context.getResources().getConfiguration().screenWidthDp;
            mCreateScreenHeightDp = context.getResources().getConfiguration().screenHeightDp;
        }

        public void setCustomBackground(Drawable d) {
            mCustomBackground = d;
            if (d != null) {
                d.setColorFilter(BACKGROUND_COLOR, PorterDuff.Mode.SRC_OVER);
            }
            computeCustomBackgroundBounds();
            invalidate();
        }

        private void computeCustomBackgroundBounds() {
            if (mCustomBackground == null) return; // Nothing to do
            if (!isLaidOut()) return; // We'll do this later

            final int bgWidth = mCustomBackground.getIntrinsicWidth();
            final int bgHeight = mCustomBackground.getIntrinsicHeight();
            final int vWidth = getWidth();
            final int vHeight = getHeight();

            final float bgAspect = (float) bgWidth / bgHeight;
            final float vAspect = (float) vWidth / vHeight;

            if (bgAspect > vAspect) {
                mCustomBackground.setBounds(0, 0, (int) (vHeight * bgAspect), vHeight);
            } else {
                mCustomBackground.setBounds(0, 0, vWidth, (int) (vWidth / bgAspect));
            }
        }

        @Override
        protected void onSizeChanged(int w, int h, int oldw, int oldh) {
            super.onSizeChanged(w, h, oldw, oldh);
            computeCustomBackgroundBounds();
        }

        @Override
        protected void onConfigurationChanged(Configuration newConfig) {
            super.onConfigurationChanged(newConfig);
            if (DEBUG) {
                Log.d(TAG, "onConfigurationChanged, old orientation=" + mCreateOrientation +
                        ", new orientation=" + newConfig.orientation);
            }
            /// M: Optimization, only create views when orientation changed
            if (mCreateOrientation != newConfig.orientation
                || mCreateScreenWidthDp != newConfig.screenWidthDp
                || mCreateScreenHeightDp != newConfig.screenHeightDp) {
                if (mKeyguardHost.getVisibility() == View.VISIBLE) {
                    // only propagate configuration messages if we're currently showing
                                maybeCreateKeyguardLocked(KeyguardUtils.shouldEnableScreenRotation(mContext), true, null);
                } else {
                    if (DEBUG) Log.d(TAG, "onConfigurationChanged: view not visible");
                }
            } else {
                if (DEBUG) Log.d(TAG, "onConfigurationChanged: orientation not changed and screen size not changed");
            }
        }

        @Override
        public boolean dispatchKeyEvent(KeyEvent event) {
            if (mKeyguardView != null) {
                // Always process back and menu keys, regardless of focus
                if (event.getAction() == KeyEvent.ACTION_DOWN) {
                    int keyCode = event.getKeyCode();
                    if (keyCode == KeyEvent.KEYCODE_BACK && mKeyguardView.handleBackKey()) {
                        return true;
                    } else if (keyCode == KeyEvent.KEYCODE_MENU && mKeyguardView.handleMenuKey()) {
                        return true;
                    }
                }
                // Always process media keys, regardless of focus
                /// M: [ALPS00601974] Avoid dispatch keyevent twice.
                return mKeyguardView.dispatchKeyEvent(event);
            }
            return super.dispatchKeyEvent(event);
        }
    }

    SparseArray<Parcelable> mStateContainer = new SparseArray<Parcelable>();

    private void maybeCreateKeyguardLocked(boolean enableScreenRotation, boolean force,
            Bundle options) {
        if (mKeyguardHost != null) {
            mKeyguardHost.saveHierarchyState(mStateContainer);
        }

        if (mKeyguardHost == null) {
            if (DEBUG) Log.d(TAG, "keyguard host is null, creating it...");

            mKeyguardHost = new ViewManagerHost(mContext);

            int flags = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                    | WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR
                    | WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN
                    | WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER;

            if (!mNeedsInput) {
                flags |= WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM;
            }

            final int stretch = ViewGroup.LayoutParams.MATCH_PARENT;
            final int type = WindowManager.LayoutParams.TYPE_KEYGUARD;
            WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                    stretch, stretch, type, flags, PixelFormat.TRANSLUCENT);
            lp.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE;
            lp.windowAnimations = R.style.Animation_LockScreen;
            lp.screenOrientation = enableScreenRotation ?
                    ActivityInfo.SCREEN_ORIENTATION_USER : ActivityInfo.SCREEN_ORIENTATION_NOSENSOR;

            if (ActivityManager.isHighEndGfx()) {
                lp.flags |= WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED;
                lp.privateFlags |=
                        WindowManager.LayoutParams.PRIVATE_FLAG_FORCE_HARDWARE_ACCELERATED;
            }
            lp.privateFlags |= WindowManager.LayoutParams.PRIVATE_FLAG_SET_NEEDS_MENU_KEY;
            /// M: Poke user activity when operating Keyguard
            //lp.inputFeatures |= WindowManager.LayoutParams.INPUT_FEATURE_DISABLE_USER_ACTIVITY;
            lp.setTitle("Keyguard");
            mWindowLayoutParams = lp;
            ///M: skip add KeyguardHost into viewManager in AT case
            if (!KeyguardViewMediator.isKeyguardInActivity) {
                mViewManager.addView(mKeyguardHost, lp);
            } else {
                if (DEBUG) Log.d(TAG, "skip add mKeyguardHost into mViewManager for testing");
            }
            KeyguardUpdateMonitor.getInstance(mContext).registerCallback(mBackgroundChanger);
        }

       /// M: If force and keyguardView is not null, we should relase memory hold by old keyguardview
        if (force && mKeyguardView != null) {
            mKeyguardView.cleanUp();
        }

        if (force || mKeyguardView == null) {
            mKeyguardHost.setCustomBackground(null);
            mKeyguardHost.removeAllViews();
            inflateKeyguardView(options);
            mKeyguardView.requestFocus();
        }
        updateUserActivityTimeoutInWindowLayoutParams();
        mViewManager.updateViewLayout(mKeyguardHost, mWindowLayoutParams);

        mKeyguardHost.restoreHierarchyState(mStateContainer);
    }

    private void inflateKeyguardView(Bundle options) {
        /// M: add for power-off alarm @{
        int resId = R.id.keyguard_host_view;
        int layoutId = R.layout.keyguard_host_view;
        if(PowerOffAlarmManager.isAlarmBoot()){
            resId = R.id.power_off_alarm_host_view;
            layoutId = R.layout.mtk_power_off_alarm_host_view;
        }
        /// @}
        View v = mKeyguardHost.findViewById(resId);
        if (v != null) {
            mKeyguardHost.removeView(v);
        }
        /// M: Save new orientation
        mCreateOrientation = mContext.getResources().getConfiguration().orientation;
        mCreateScreenWidthDp = mContext.getResources().getConfiguration().screenWidthDp;
        mCreateScreenHeightDp = mContext.getResources().getConfiguration().screenHeightDp;
        
        final LayoutInflater inflater = LayoutInflater.from(mContext);
        View view = inflater.inflate(layoutId, mKeyguardHost, true);
        mKeyguardView = (KeyguardHostView) view.findViewById(resId);
        mKeyguardView.setLockPatternUtils(mLockPatternUtils);
        mKeyguardView.setViewMediatorCallback(mViewMediatorCallback);
        mKeyguardView.initializeSwitchingUserState(options != null &&
                options.getBoolean(IS_SWITCHING_USER));

        // HACK
        // The keyguard view will have set up window flags in onFinishInflate before we set
        // the view mediator callback. Make sure it knows the correct IME state.
        if (mViewMediatorCallback != null) {
            KeyguardPasswordView kpv = (KeyguardPasswordView) mKeyguardView.findViewById(
                    R.id.keyguard_password_view);

            if (kpv != null) {
                mViewMediatorCallback.setNeedsInput(kpv.needsInput());
            }
        }

        if (options != null) {
            int widgetToShow = options.getInt(LockPatternUtils.KEYGUARD_SHOW_APPWIDGET,
                    AppWidgetManager.INVALID_APPWIDGET_ID);
            if (widgetToShow != AppWidgetManager.INVALID_APPWIDGET_ID) {
                mKeyguardView.goToWidget(widgetToShow);
            }
        }
    }

    public void updateUserActivityTimeout() {
        updateUserActivityTimeoutInWindowLayoutParams();
        mViewManager.updateViewLayout(mKeyguardHost, mWindowLayoutParams);
    }

    private void updateUserActivityTimeoutInWindowLayoutParams() {
        // Use the user activity timeout requested by the keyguard view, if any.
        if (mKeyguardView != null) {
            long timeout = mKeyguardView.getUserActivityTimeout();
            if (timeout >= 0) {
                mWindowLayoutParams.userActivityTimeout = timeout;
                return;
            }
        }

        // Otherwise, use the default timeout.
        mWindowLayoutParams.userActivityTimeout = KeyguardViewMediator.AWAKE_INTERVAL_DEFAULT_MS;
    }

    private void maybeEnableScreenRotation(boolean enableScreenRotation) {
        // TODO: move this outside
        if (enableScreenRotation) {
            if (DEBUG) Log.d(TAG, "Rotation sensor for lock screen On!");
            mWindowLayoutParams.screenOrientation = ActivityInfo.SCREEN_ORIENTATION_USER;
        } else {
            if (DEBUG) Log.d(TAG, "Rotation sensor for lock screen Off!");
            mWindowLayoutParams.screenOrientation = ActivityInfo.SCREEN_ORIENTATION_NOSENSOR;
        }
        mViewManager.updateViewLayout(mKeyguardHost, mWindowLayoutParams);
    }

    void updateShowWallpaper(boolean show) {
        if (show) {
            mWindowLayoutParams.flags |= WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER;
        } else {
            mWindowLayoutParams.flags &= ~WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER;
        }

        if (!KeyguardViewMediator.isKeyguardInActivity) {
            mViewManager.updateViewLayout(mKeyguardHost, mWindowLayoutParams);
        }
    }

    public void setNeedsInput(boolean needsInput) {
        mNeedsInput = needsInput;
        if (mWindowLayoutParams != null) {
            if (needsInput) {
                mWindowLayoutParams.flags &=
                    ~WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM;
            } else {
                mWindowLayoutParams.flags |=
                    WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM;
            }

            try {
                mViewManager.updateViewLayout(mKeyguardHost, mWindowLayoutParams);
            } catch (java.lang.IllegalArgumentException e) {
                // TODO: Ensure this method isn't called on views that are changing...
                Log.w(TAG,"Can't update input method on " + mKeyguardHost + " window not attached");
            }
        }
    }

    /**
     * Reset the state of the view.
     */
    public synchronized void reset(Bundle options) {
        if (DEBUG) Log.d(TAG, "reset()");
        // User might have switched, check if we need to go back to keyguard
        // TODO: It's preferable to stay and show the correct lockscreen or unlock if none

        boolean forceReCreate = true;
        if (mKeyguardView != null && mKeyguardView.isCurrentSimPinPukView() &&
            options.getBoolean(KeyguardViewMediator.RESET_FOR_SIM_STATE)) {
            forceReCreate = false;
        }

        /// M: set to show next security view after simpinpuk
        if (mKeyguardView != null && !mKeyguardView.isCurrentSimPinPukView()) {
            mShowNextViewAfterSimLock = true;
        }
        maybeCreateKeyguardLocked(KeyguardUtils.shouldEnableScreenRotation(mContext), forceReCreate, options);
        
        mKeyguardView.setShowNextViewFlag(mShowNextViewAfterSimLock);
    }

    public synchronized void onScreenTurnedOff() {
        if (DEBUG) Log.d(TAG, "onScreenTurnedOff()");
        mScreenOn = false;
        if (mKeyguardView != null) {
            mKeyguardView.onScreenTurnedOff();
        }
    }

    public synchronized void onScreenTurnedOn(final IKeyguardShowCallback callback) {
        if (DEBUG) Log.d(TAG, "onScreenTurnedOn()");
        mScreenOn = true;

        // If keyguard is not showing, we need to inform PhoneWindowManager with a null
        // token so it doesn't wait for us to draw...
        final IBinder token = isShowing() ? mKeyguardHost.getWindowToken() : null;

        if (DEBUG && token == null) Slog.v(TAG, "send wm null token: "
                + (mKeyguardHost == null ? "host was null" : "not showing"));

        if (mKeyguardView != null) {
            mKeyguardView.onScreenTurnedOn();

            if (mCreateOrientation != mContext.getResources().getConfiguration().orientation) {
                if (DEBUG) Log.d(TAG, "onScreenTurnedOn orientation is different, recreate it. mCreateOrientation="+mCreateOrientation
                    +", newConfig="+mContext.getResources().getConfiguration().orientation);
                maybeCreateKeyguardLocked(KeyguardUtils.shouldEnableScreenRotation(mContext), true, null);
            }
            // Caller should wait for this window to be shown before turning
            // on the screen.
            if (callback != null) {
                if (mKeyguardHost.getVisibility() == View.VISIBLE) {
                    // Keyguard may be in the process of being shown, but not yet
                    // updated with the window manager...  give it a chance to do so.
                    if (DEBUG) Log.d(TAG, "onScreenTurnedOn mKeyguardView visible, post runnable");
                    mKeyguardHost.post(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                if (DEBUG) Log.d(TAG, "onScreenTurnedOn before callback.onShown() mKeyguardHost is Visible");
                                callback.onShown(token);
                            } catch (RemoteException e) {
                                Slog.w(TAG, "Exception calling onShown():", e);
                            }
                        }
                    });
                } else {
                    try {
                        if (DEBUG) Log.d(TAG, "onScreenTurnedOn before callback.onShown() mKeyguardHost is NOT Visible");
                        callback.onShown(token);
                    } catch (RemoteException e) {
                        Slog.w(TAG, "Exception calling onShown():", e);
                    }
                }
            }
        } else if (callback != null) {
            try {
                if (DEBUG) Log.d(TAG, "onScreenTurnedOn before callback.onShown() mKeyguardView is null");
                callback.onShown(token);
            } catch (RemoteException e) {
                Slog.w(TAG, "Exception calling onShown():", e);
            }
        }
    }

    public synchronized void verifyUnlock() {
        if (DEBUG) Log.d(TAG, "verifyUnlock()");
        show(null);
        mKeyguardView.verifyUnlock();
    }

    /**
     * Hides the keyguard view
     */
    public synchronized void hide() {
        if (DEBUG) Log.d(TAG, "hide() mKeyguardView=" + mKeyguardView);

        if (mKeyguardHost != null) {
            mKeyguardHost.setVisibility(View.GONE);

            /// M: reset the simpin show next view flag
            mShowNextViewAfterSimLock = false;

            // We really only want to preserve keyguard state for configuration changes. Hence
            // we should clear state of widgets (e.g. Music) when we hide keyguard so it can
            // start with a fresh state when we return.
            mStateContainer.clear();

            // Don't do this right away, so we can let the view continue to animate
            // as it goes away.
            if (mKeyguardView != null) {
                final KeyguardViewBase lastView = mKeyguardView;
                mKeyguardView = null;
                mKeyguardHost.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        synchronized (KeyguardViewManager.this) {
                            if (DEBUG) Log.d(TAG, "hide() runnable lastView=" + lastView);
                            lastView.cleanUp();
                            // Let go of any large bitmaps.
                            mKeyguardHost.setCustomBackground(null);
                            updateShowWallpaper(true);
                            mKeyguardHost.removeView(lastView);
                            mViewMediatorCallback.keyguardGone();
                        }
                    }
                }, HIDE_KEYGUARD_DELAY);
            }
        }
    }

    /**
     * Dismisses the keyguard by going to the next screen or making it gone.
     */
    public synchronized void dismiss() {
        Log.d(TAG, "dismiss mScreenOn=" + mScreenOn);
        if (mScreenOn) {
            mKeyguardView.dismiss();
        }
    }

    /**
     * @return Whether the keyguard is showing
     */
    public synchronized boolean isShowing() {
        return (mKeyguardHost != null && mKeyguardHost.getVisibility() == View.VISIBLE);
    }

    public void showAssistant() {
        if (mKeyguardView != null) {
            mKeyguardView.showAssistant();
        }
    }

    public void dispatch(MotionEvent event) {
        if (mKeyguardView != null) {
            mKeyguardView.dispatch(event);
        }
    }

    public void launchCamera() {
        if (mKeyguardView != null) {
            mKeyguardView.launchCamera();
        }
    }

    /// M: add for ipo shut down update process
    public void ipoShutDownUpdate() {
        if (null != mKeyguardView) {
            mKeyguardView.ipoShutDownUpdate();
        }
    }

    // M: Save current orientation, so that we will only recreate views when orientation changed
    private int mCreateOrientation;
    private int mCreateScreenWidthDp;
    private int mCreateScreenHeightDp;
    /// M: Flag to show next view after SimPinPuk
    private boolean mShowNextViewAfterSimLock = false;
}
