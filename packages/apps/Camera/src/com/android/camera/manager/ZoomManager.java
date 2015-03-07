package com.android.camera.manager;

import android.view.View;

import com.android.camera.Camera;
import com.android.camera.Log;
import com.android.camera.R;
import com.android.camera.SettingChecker;
import com.android.gallery3d.ui.GestureRecognizer.Listener;
import com.android.camera.FeatureSwitcher;
import android.view.MotionEvent;

import java.util.List;
import java.util.Locale;

public class ZoomManager extends ViewManager implements Camera.OnFullScreenChangedListener,
        Camera.Resumable {
    private static final String TAG = "ZoomManager";
    private static final String PATTERN = "%.1f";
    
    private static final int UNKNOWN = -1;
    private static final int RATIO_FACTOR_RATE = 100;
    private static float mIgoreDistance;
    private Listener mGalleryGestureListener;
    private MyListener mGestureListener = new MyListener();
    private boolean mResumed;
    private boolean mDeviceSupport;
    private List<Integer> mZoomRatios;
    private int mLastZoomRatio = UNKNOWN;
    //for scale gesture
    private static final float ZERO = 1;
    private float mZoomIndexFactor = ZERO; //zoom rate from 1, mapping zoom gesture rate
    //for zooming behavior
    private boolean mIgnorGestureForZooming;
    private boolean isHDRecord = false;
    
    public ZoomManager(Camera context) {
        super(context);
        context.addResumable(this);
        context.addOnFullScreenChangedListener(this);
    }
    
    @Override
    protected View getView() {
        return null;
    }
    
    @Override
    public void begin() {
    }
    
    @Override
    public void resume() {
        if (!mResumed && getContext().isFullScreen()) {
            Listener last = getContext().setGestureListener(mGestureListener);
            if (last != mGestureListener) {
                mGalleryGestureListener = last;
            }
            mResumed = true;
        }
        Log.i(TAG, "resume() mGalleryGestureListener=" + mGalleryGestureListener);
    }
    @Override
    public void pause() {
        Log.i(TAG, "pause() mGalleryGestureListener=" + mGalleryGestureListener
                    + ", mResumed=" + mResumed);
        if (mResumed) {
            getContext().setGestureListener(mGalleryGestureListener);
            mGalleryGestureListener = null;
            mResumed = false;
        }
    }
    
    @Override
    public void finish() {
    }
    
    public void resetZoom() {
        Log.i(TAG, "resetZoom() mZoomRatios=" + mZoomRatios + ", mLastZoomRatio=" + mLastZoomRatio);
        mZoomIndexFactor = ZERO;
        if (isValidZoomIndex(0)) {
            mLastZoomRatio = mZoomRatios.get(0);
        }
    }
    
    private void performZoom(int zoomIndex, boolean userAction) {
        Log.i(TAG, "performZoom(" + zoomIndex + ", " + userAction + ") mResumed=" + mResumed
                + ", mDeviceSupport=" + mDeviceSupport);
        if (getContext().getCameraDevice() != null && mDeviceSupport && isValidZoomIndex(zoomIndex)) {
            startAsyncZoom(zoomIndex);
            int newRatio = mZoomRatios.get(zoomIndex);
            if (mLastZoomRatio != newRatio) {
                mLastZoomRatio = newRatio;//change last zoom value to new
            }
        }
        if (userAction) {
            float zoomRation = ((float)mLastZoomRatio) / RATIO_FACTOR_RATE;
            // here use English to format, since just display numbers. 
            getContext().showInfo("x" + String.format(Locale.ENGLISH, PATTERN, zoomRation));
        }
    }
    
    private void startAsyncZoom(final int zoomValue) {
        Log.i(TAG, "startAsyncZoom(" + zoomValue + ")");
        getContext().lockRun(new Runnable() {
            @Override
             public void run() {
                // Set zoom parameters asynchronously
                if (getContext().getParameters() != null && getContext().getCameraDevice() != null) {
                    Log.i(TAG, "startAsyncZoom() parameters.zoom=" + getContext().getParameters().getZoom());
                    if (getContext().getParameters().isZoomSupported()
                            && getContext().getParameters().getZoom() != zoomValue) {
                       //should set zoom value to mParameters
                       getContext().getParameters().setZoom(zoomValue);
                       getContext().getCameraDevice().setParametersAsync(getContext(), zoomValue);
                    }
                } 
            }
        });
    }
    
    private class MyListener implements Listener {
        @Override
        public void onDown(float x, float y) {
            Log.i(TAG, "onDown(" + x + ", " + y + ")");
            if (mGalleryGestureListener != null) {
                mGalleryGestureListener.onDown(x, y);
            }
            // for a complete new gesture, reset status
            mIgnorGestureForZooming = false;
        }
    
        public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
            Log.i(TAG, "onFling(" + velocityX + ", " + velocityY + ")");
            if (shouldIgnoreCurrentGesture()) {
                return false;
            }
            if (mGalleryGestureListener != null) {
                return mGalleryGestureListener.onFling(e1, e2, velocityX, velocityY);
            }
            return false;
        }
    
        @Override
        public boolean onScroll(float dx, float dy, float totalX, float totalY) {
            Log.i(TAG, "onScroll(" + dx + ", " + dy + ", " + totalX + ", " + totalY + ")");
            if (shouldIgnoreCurrentGesture() || shouldIgnoreScrollGesture(totalX, totalY)) {
                return false;
            }
            if (mGalleryGestureListener != null) {
                return mGalleryGestureListener.onScroll(dx, dy, totalX, totalY);
            }
            return false;
        }
    
        @Override
        public boolean onSingleTapUp(float x, float y) {
            if (FeatureSwitcher.isSupportDoubleTapUp()) return false;
            Log.i(TAG, "onSingleTapUp(" + x + ", " + y + ")");
            if (mGalleryGestureListener != null) {
                return mGalleryGestureListener.onSingleTapUp(x, y);
            }
            return false;
        }
    // @Override
        public boolean onSingleTapConfirmed(float x, float y) {
            if(FeatureSwitcher.isSupportDoubleTapUp()) {
                Log.i(TAG, "onSingleTapConfirmed(" + x + ", " + y + ")");
                if (mGalleryGestureListener != null) {
                    return mGalleryGestureListener.onSingleTapConfirmed(x, y);
                } else {
                    return false;
                }
            } else {
               return false;
            }
        }
    
        @Override
        public void onUp() {
            Log.i(TAG, "onUp");
            if (mGalleryGestureListener != null) {
                mGalleryGestureListener.onUp();
            }
        }
        
        @Override
        public boolean onDoubleTap(float x, float y) {
          if (!FeatureSwitcher.isSupportDoubleTapUp()) return false;
            Log.i(TAG, "onDoubleTap(" + x + ", " + y + ") mZoomIndexFactor=" + mZoomIndexFactor
                    + ", isAppSupported()=" + isAppSupported() + ", isEnabled()=" + isEnabled());
            if (!isAppSupported() || !isEnabled()) {
                return false;
            }
            int oldIndex = findZoomIndex(mLastZoomRatio);
            int zoomIndex = 0;
            if (oldIndex == 0) {
                zoomIndex = getMaxZoomIndex();
                mZoomIndexFactor = getMaxZoomIndexFactor();
            } else {
                mZoomIndexFactor = ZERO;
            }
            performZoom(zoomIndex, true);
            return true;
        }
    
        @Override
        public boolean onScale(float focusX, float focusY, float scale) {
            Log.i(TAG, "onScale(" + focusX + ", " + focusY + ", " + scale + ") mZoomIndexFactor=" + mZoomIndexFactor
                    + ", isAppSupported()=" + isAppSupported() + ", isEnabled()=" + isEnabled());
            if (!isAppSupported() || !isEnabled()) {
                return false;
            }
            if (Float.isNaN(scale) || Float.isInfinite(scale)) {
                return false;
            }
            mZoomIndexFactor *= scale;
            if (mZoomIndexFactor <= ZERO) {
                mZoomIndexFactor = ZERO;
            } else if (mZoomIndexFactor >= getMaxZoomIndexFactor()) {
                mZoomIndexFactor = getMaxZoomIndexFactor();
            }
            int zoomIndex = findZoomIndex(Math.round(mZoomIndexFactor * RATIO_FACTOR_RATE));
            performZoom(zoomIndex, true);
            Log.i(TAG, "onScale() mZoomIndexFactor=" + mZoomIndexFactor);
            return true;
        }
    
        @Override
        public boolean onScaleBegin(float focusX, float focusY) {
            Log.i(TAG, "onScaleBegin(" + focusX + ", " + focusY + ")");
            //remember that a zooming gesture has just ended
            mIgnorGestureForZooming = true;
            return true;
        }
    
        @Override
        public void onScaleEnd() {
            Log.i(TAG, "onScaleEnd()");
        }
        @Override
        public void onLongPress(float x, float y) {
            Log.i(TAG, "onLongPress(" + x + ", " + y + ")");
            if (mGalleryGestureListener != null) {
                mGalleryGestureListener.onLongPress(x, y);
            }
        }
    }

    public void setZoomParameter() {
        if (isAppSupported()) {
            mDeviceSupport = getContext().getParameters().isZoomSupported();
            mZoomRatios = getContext().getParameters().getZoomRatios();
            int index = getContext().getParameters().getZoom();
            int len = mZoomRatios.size();
            int curRatio = mZoomRatios.get(index);
            int maxRatio = mZoomRatios.get(len - 1);
            int minRatio = mZoomRatios.get(0);
            int finalIndex = index;
            if (mLastZoomRatio == UNKNOWN || mLastZoomRatio == curRatio) {
                mLastZoomRatio = curRatio;
                finalIndex = index;
            } else {
                finalIndex = findZoomIndex(mLastZoomRatio);
            }
            int newRatio = mZoomRatios.get(finalIndex);
            performZoom(finalIndex, newRatio != mLastZoomRatio);
            Log.i(TAG, "onCameraParameterReady() index=" + index + ", len=" + len + ", maxRatio=" + maxRatio
                    + ", minRatio=" + minRatio + ", curRatio=" + curRatio + ", finalIndex=" + finalIndex
                    + ", newRatio=" + newRatio + ", mSupportZoom=" + mDeviceSupport
                    + ", mLastZoomRatio=" + mLastZoomRatio);
        } else { //reset zoom if App limit zoom function.
            resetZoom();
            performZoom(0, false);
        }
    }
    public void checkQualityForZoom() {
        isHDRecord = true;
    }

    public void changeZoomForQuality() {
        isHDRecord = false;
    }
    
    private boolean isAppSupported() {
        boolean enable = SettingChecker.isZoomEnable(getContext().getCurrentMode());
        if(isHDRecord) {
            enable = false;
        }
        Log.i(TAG, "isAppSupported() return " + enable);
        return enable;
    }

    @Override
    public void onFullScreenChanged(boolean full) {
        if (full) {
            resume();
        } else {
            pause();
        }
    }
    
    private int findZoomIndex(int zoomRatio) {
        int find = 0; //if not find, return 0
        if (mZoomRatios != null) {
            int len = mZoomRatios.size();
            if (len == 1) {
                find = 0;
            } else {
                int max = mZoomRatios.get(len - 1);
                int min = mZoomRatios.get(0);
                if (zoomRatio <= min) {
                    find = 0;
                } else if (zoomRatio >= max) {
                    find = len - 1;
                } else {
                    for (int i = 0; i < len - 1; i++) {
                        int cur = mZoomRatios.get(i);
                        int next = mZoomRatios.get(i + 1);
                        if (zoomRatio >= cur && zoomRatio < next) {
                            find = i;
                            break;
                        }
                    }
                }
            }
        }
        return find;
    }
    
    private boolean isValidZoomIndex(int zoomIndex) {
        boolean valid = false;
        if (mZoomRatios != null && zoomIndex >= 0 && zoomIndex < mZoomRatios.size()) {
            valid = true;
        }
        Log.i(TAG, "isValidZoomIndex(" + zoomIndex + ") return " + valid);
        return valid;
    }
    
    private int getMaxZoomIndex() {
        int index = UNKNOWN;
        if (mZoomRatios != null) {
            index = mZoomRatios.size() - 1;
        }
        return index;
    }
    
    private float getMaxZoomIndexFactor() {
        return (float)getMaxZoomRatio() / RATIO_FACTOR_RATE;
    }
    
    private int getMaxZoomRatio() {
        int ratio = UNKNOWN;
        if (mZoomRatios != null) {
            ratio = mZoomRatios.get(mZoomRatios.size() - 1);
        }
        return ratio;
    }

    private boolean shouldIgnoreScrollGesture(float totalX, float totalY) {
        Log.i(TAG, "shouldIgnoreScrollGesture(" + totalX + " " + totalY + ") mIgoreDistance = " + mIgoreDistance);
        if (mIgoreDistance == 0) {
            mIgoreDistance = getContext().getResources().getDimensionPixelSize(R.dimen.ignore_distance);
        }
        return Math.abs(totalX) < mIgoreDistance && Math.abs(totalY) < mIgoreDistance;
    }

    private boolean shouldIgnoreCurrentGesture() {
        return (isAppSupported() && isEnabled() && mIgnorGestureForZooming);
    }
}
