package com.android.camera.manager;

import android.content.pm.ActivityInfo;
import android.graphics.drawable.AnimationDrawable;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ImageView;

import com.android.camera.Camera;
import com.android.camera.Camera.OnFullScreenChangedListener;
import com.android.camera.Log;
import com.android.camera.ModeChecker;
import com.android.camera.R;
import com.android.camera.Util;
import com.android.camera.ui.ProgressIndicator;
import com.android.camera.ui.ShutterButton;
import com.android.camera.ui.ShutterButton.OnShutterButtonListener;

public class SingleStereoPhotoViewManager extends ViewManager {
    private static final String TAG = "SingleStereoPhotoViewManager";
    private static final boolean LOG = Log.LOGV;
    
    private View mRootView;
    private View mPanoView;
    private ImageView mCenterWindow;
    private View mNaviWindow;
    private Drawable mNormalWindowDrawable;
    private ProgressIndicator mProgressIndicator;
    private boolean mNeedInitialize = true;
    
    private static final int DIRECTION_RIGHT = 0;
    private static final int DIRECTION_LEFT = 1;
    private static final int DIRECTION_UP = 2;
    private static final int DIRECTION_DOWN = 3;
    private static final int DIRECTION_UNKNOWN = 4;
    private int mDirection = DIRECTION_UNKNOWN;
    
    private static final int TARGET_DISTANCE_HORIZONTAL = 32;
    private static final int TARGET_DISTANCE_VERTICAL = 24;
    private static final int NONE_ORIENTATION = -1;
    
    private int mDisplayRotation;
    private int mHoldOrientation = NONE_ORIENTATION;
    
    protected final Handler mHandler;
    
    public SingleStereoPhotoViewManager(Camera context) {
        super(context);
        setFileter(false);
        mHandler = new Handler(getContext().getMainLooper());
    }
    
    @Override
    protected View getView() {
        View view = inflate(R.layout.mtk_single3d_pano_preview);
        mRootView = view.findViewById(R.id.single3d_pano_frame_layout);
        return view;
    }
    
    public void show() {
        super.show();
        mDisplayRotation = getContext().getDisplayRotation();
        if (mNeedInitialize) {
            initializeViewManager();
            mNeedInitialize = false;
        }
        showCaptureView();
    }
    
    protected void onRelease() {
        mNeedInitialize = true;
    }
    
    private void initializeViewManager() {
        mPanoView = mRootView.findViewById(R.id.pano_view);
        mCenterWindow = (ImageView) mRootView.findViewById(R.id.center_window);
        mNaviWindow = mRootView.findViewById(R.id.navi_window);
        
        android.content.res.Resources res = getContext().getResources();
        mNormalWindowDrawable = res
                .getDrawable(R.drawable.ic_pano_normal_window);
        
        mProgressIndicator = new ProgressIndicator(getContext(),
                ProgressIndicator.TYPE_SINGLE3D);
        //mProgressIndicator.setVisibility(View.GONE);
        mProgressIndicator.setOrientation(getOrientation());
        // showCaptureView();
    }
    
    @Override
    public void onOrientationChanged(int orientation) {
        if (LOG) {
            Log.v(TAG, "onOrientationChanged mContext.getCameraState()=" + getContext().getCameraState()
                    + " orientation=" + orientation);
        }
        if (getContext().getCameraState() != Camera.STATE_SNAPSHOT_IN_PROGRESS) {
            super.onOrientationChanged(orientation);
            if (mProgressIndicator != null) {
                mProgressIndicator.setOrientation(orientation);
            }
            mHoldOrientation = NONE_ORIENTATION;
        } else {
            mHoldOrientation = orientation;
        }
    }
    
    public void showCaptureView() {
        if (LOG) {
            Log.d(TAG, "showCaptureView");
        }
        // reset orientation,since camera state is snapinprogress at last time. 
        if (mHoldOrientation != NONE_ORIENTATION) {
            onOrientationChanged(mHoldOrientation);
        }
        mPanoView.setVisibility(View.VISIBLE);
        mCenterWindow.setVisibility(View.VISIBLE);
        mProgressIndicator.setProgress(0);
        mProgressIndicator.setVisibility(View.VISIBLE);
        // mCamera.showPostSingle3DControlAlert();
        // mCamera.showSingle3DGuide(R.string.single3d_guide_move);
        // mCamera.mFocusManager.clearFocusOnContinuous();
    }
    
    public void setProgress(int num) {
        if (mProgressIndicator != null) {
            mProgressIndicator.setProgress(num);
        }
    }
    
    public void updateMovingUI(int xy, int direction, boolean shown) {
        short x = (short) ((xy & 0xFFFF0000) >> 16);
        short y = (short) (xy & 0x0000FFFF);
        
        int cwx = mCenterWindow.getLeft() + mCenterWindow.getPaddingLeft();
        int cwy = mCenterWindow.getTop() + mCenterWindow.getPaddingTop();
        int w = mNaviWindow.getWidth();
        int h = mNaviWindow.getHeight();
        float x_ratio = (float) mPanoView.getWidth()
                / (float) getContext().getPreviewFrameWidth();
        float y_ratio = (float) mPanoView.getHeight()
                / (float) getContext().getPreviewFrameHeight();
        
        if (mDisplayRotation == 270) {
            x = (short) -x;
            y = (short) -y;
            direction = 1 - direction;
        }
        if (mDisplayRotation == 0 || mDisplayRotation == 180) {
            float temp = x_ratio;
            x_ratio = y_ratio;
            y_ratio = -temp;
            
            int temp2 = cwx;
            cwx = cwy;
            cwy = temp2;
           
            temp2 = w;
            w = h;
            h = temp2;
        }
        x *= x_ratio;
        y *= y_ratio;
        
        int screenPosX = 0;
        int screenPosY = 0;
        
        switch (direction) {
        case DIRECTION_RIGHT:
            screenPosX = -x + cwx
                    + (int) (TARGET_DISTANCE_HORIZONTAL * x_ratio);
            screenPosY = -y + cwy;
            break;
        case DIRECTION_LEFT:
            screenPosX = -x + cwx
                    + (int) (-TARGET_DISTANCE_HORIZONTAL * x_ratio);
            screenPosY = -y + cwy;
            break;
        case DIRECTION_UP:
            screenPosX = -x + cwx;
            screenPosY = -y + cwy + (int) (-TARGET_DISTANCE_VERTICAL * y_ratio);
            break;
        case DIRECTION_DOWN:
            screenPosX = -x + cwx;
            screenPosY = -y + cwy + (int) (TARGET_DISTANCE_VERTICAL * y_ratio);
            break;
        }
        Log.i(TAG, "onFrame x = " + x / x_ratio + " y = " + y / y_ratio
                + " cwx = " + cwx + " cwy = " + cwy + " screenPosX = "
                + screenPosX + " screenPosY = " + screenPosY);
        
        if (mDisplayRotation == 0 || mDisplayRotation == 180) {
            int temp = screenPosX;
            screenPosX = screenPosY;
            screenPosY = temp;
        }
        if (screenPosX < 0) {
            screenPosX = 0;
        }
        if (screenPosY < 0) {
            screenPosY = 0;
        }
        if (mPanoView.getWidth() < screenPosX + w) {
            screenPosX = mPanoView.getWidth() - w;
        }
        if (mPanoView.getHeight() < screenPosY + h) {
            screenPosY = mPanoView.getHeight() - h;
        }
        mNaviWindow.setVisibility(View.VISIBLE);
        mNaviWindow.layout(screenPosX, screenPosY, screenPosX + w, screenPosY
                + h);
    }
    
    public void drawNaviWindow() {
        int cwx = mCenterWindow.getLeft() + mCenterWindow.getPaddingLeft();
        int cwy = mCenterWindow.getTop() + mCenterWindow.getPaddingTop();
        Log.d(TAG, "drawNaviWindow cwx = " + cwx + " cwy = " + cwy);
        if (ActivityInfo.SCREEN_ORIENTATION_PORTRAIT == getContext()
                .getRequestedOrientation()
                || ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT == getContext()
                    .getRequestedOrientation()) {
            mNaviWindow.layout(cwx, cwy + TARGET_DISTANCE_VERTICAL, cwx
                    + mNaviWindow.getWidth(), cwy + TARGET_DISTANCE_VERTICAL
                    + mNaviWindow.getHeight());
        } else {
            if (mDisplayRotation == 90) {
                mNaviWindow.layout(cwx + TARGET_DISTANCE_HORIZONTAL, cwy, cwx
                        + TARGET_DISTANCE_HORIZONTAL + mNaviWindow.getHeight(), cwy
                        + mNaviWindow.getWidth());
            } else if (mDisplayRotation == 270) {
                mNaviWindow.layout(cwx - TARGET_DISTANCE_HORIZONTAL, cwy, cwx
                        - TARGET_DISTANCE_HORIZONTAL + mNaviWindow.getHeight(), cwy
                        + mNaviWindow.getWidth());
            }
        }
        mNaviWindow.setVisibility(View.VISIBLE);
    }
    
    public void setViewsForNext(int imageNum) {
        mProgressIndicator.setProgress(imageNum + 1);
        mNaviWindow.setVisibility(View.INVISIBLE);
        mCenterWindow.setImageResource(R.anim.window_collimate);
        AnimationDrawable animation = (AnimationDrawable)mCenterWindow.getDrawable();
        animation.start();
    }
        
    public void hideSingleStereoView() {
        mPanoView.setVisibility(View.INVISIBLE);
        mNaviWindow.setVisibility(View.INVISIBLE);
    }
    
    public void stopCaptureAnimation() {
        Log.d(TAG, "stopCaptureAnimation");
        if (mCenterWindow.getDrawable() instanceof AnimationDrawable) {
            ((AnimationDrawable) mCenterWindow.getDrawable()).stop();
            mCenterWindow.setImageDrawable(mNormalWindowDrawable);
        }
    }
    
    public void resetController() {
        // original hide 3d ui
        mPanoView.setVisibility(View.INVISIBLE);
        mNaviWindow.setVisibility(View.INVISIBLE);
        mHandler.postDelayed(new Runnable() {
            public void run() {
                mProgressIndicator.setVisibility(View.INVISIBLE);
            }
        }, 50);
    }
    
}
