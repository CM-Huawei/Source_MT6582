package com.android.camera.manager;

import com.android.camera.R;

import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.view.View;
import android.widget.ImageView;

import com.android.camera.Camera;
import com.android.camera.FeatureSwitcher;
import com.android.camera.Log;
import com.android.camera.ui.ProgressIndicator;
import com.android.camera.ui.RotateLayout;
import com.android.camera.ui.RotateLayout.OnSizeChangedListener;

public class MotionTrackViewManager extends ViewManager {
    private static final String TAG = "MotionTrackView";
    private static final boolean LOG = Log.LOGV;
    private View mRootView;
    private View mPanoView;
    private ImageView mCenterWindow;
    private View mNaviWindow;
    private Drawable mNormalWindowDrawable;
    private ProgressIndicator mProgressIndicator;
    private RotateLayout mScreenProgressLayout;
    private Camera mCamera;
    private int mDisplayRotation;
    private int mDisplayOrientaion;
    private int mPreviewWidth;
    private int mPreviewHeight;

    private boolean mNeedInitialize = true;
    private int mHoldOrientation = NONE_ORIENTATION;
    private static final int NONE_ORIENTATION = -1;
    private static final int PROGRESS_ZERO = 0;

    private static final int DIRECTION_RIGHT = 0;
    private static final int DIRECTION_LEFT = 1;
    private static final int DIRECTION_UP = 2;
    private static final int DIRECTION_DOWN = 3;
    private static final int DIRECTION_UNKNOWN = 4;

    // what's this use?
    private static final int TARGET_DISTANCE_HORIZONTAL = 32;
    private static final int TARGET_DISTANCE_VERTICAL = 24;

    protected Handler mHandler;

    public MotionTrackViewManager(Camera context) {
        super(context);
        mCamera = context;
        setFileter(false);
        mHandler = new Handler(getContext().getMainLooper());
    }

    @Override
    protected View getView() {
        Log.d(TAG, "getView");
        View view = inflate(R.layout.motion_track_pano_review);
        mRootView = view.findViewById(R.id.motion_track_frame_layout);
        return view;
    }

    public void show() {
        super.show();
        Log.d(TAG, "show,mNeedInitialize = " + mNeedInitialize);
        mDisplayOrientaion = getContext().getDisplayOrientation();
        mDisplayRotation = getContext().getDisplayRotation();
        if (mNeedInitialize) {
            initializeViewManager();
            mNeedInitialize = false;
        }
        showCaptureView();
    }

    // this method is used to modify for:
    // suspend ->onResume found the white
    // frame is not show
    @Override
    public void checkConfiguration() {
        super.checkConfiguration();
        Log.d(TAG, "checkConfiguration,mcamera = " + mCamera);
        // switch motion track mode to normal mode,onPause->onResume
        // will found in normal mode will show the white frame
        if (mCamera != null && mCamera.getCurrentMode() == ModePicker.MODE_MOTION_TRACK) {
            reInflate();
        }
    }

    protected void onRelease() {
        super.onRelease();
        mNeedInitialize = true;
    }

    protected void initializeViewManager() {
        Log.d(TAG, "initializeViewManager");
        mPanoView = mRootView.findViewById(R.id.pano_view);
        mScreenProgressLayout = (RotateLayout) mRootView.findViewById(R.id.on_screen_progress);

        mCenterWindow = (ImageView) mRootView.findViewById(R.id.mt_center_window);
        mNaviWindow = mRootView.findViewById(R.id.mt_navi_window);

        android.content.res.Resources res = getContext().getResources();
        mNormalWindowDrawable = res.getDrawable(R.drawable.ic_motion_track_normal_window);

        mProgressIndicator = new ProgressIndicator(getContext(), ProgressIndicator.TYPE_MOTION_TRACK);
        mProgressIndicator.setVisibility(View.GONE);
        mProgressIndicator.setOrientation(getOrientation());

    }

    public void setProgress(int num) {
        if (mProgressIndicator != null) {
            mProgressIndicator.setProgress(num);
        }
    }

    private OnSizeChangedListener mOnSizeChangedListener = new OnSizeChangedListener() {
        @Override
        public void onSizeChanged(int width, int height) {
            Log.d(TAG, "onSizeChanged width=" + width + " height=" + height);
            mPreviewWidth = Math.max(width, height);
            mPreviewHeight = Math.min(width, height);
        }
    };

    public void resetController() {
        Log.d(TAG, "resetController");
        mCenterWindow.setVisibility(View.VISIBLE);
        mNaviWindow.setVisibility(View.GONE);
        hideProgressIndicaotr();
    }

    public void updateMovingUI(int xPos, int yPos, boolean shown) {
        Log.d(TAG, "onFrame,updateMovingUI ,x = " + xPos + ",y = " + yPos);
        if (0 == mNaviWindow.getHeight() || 0 == mNaviWindow.getWidth()) {
            mNaviWindow.setVisibility(View.INVISIBLE);
            return;
        }

        short x = (short) xPos;
        short y = (short) yPos;
        int cwx = mCenterWindow.getLeft() + mCenterWindow.getPaddingLeft();
        int cwy = mCenterWindow.getTop() + mCenterWindow.getPaddingTop();
        float x_ratio = (float) mPanoView.getWidth() / (float) getContext().getPreviewFrameWidth();
        float y_ratio = (float) mPanoView.getHeight() / (float) getContext().getPreviewFrameHeight();

        // assume that the activity's requested orientation is same as the lcm orientation.
        // if not,the following caculation would be wrong!!
        if (mDisplayOrientaion == 180) {
            x = (short) -x;
            y = (short) -y;
        } else if (mDisplayOrientaion == 90) {
            float temp = x_ratio;
            x_ratio = y_ratio;
            y_ratio = -temp;

            int temp2 = cwx;
            cwx = cwy;
            cwy = temp2;
        }

        x *= x_ratio;
        y *= y_ratio;

        int screenPosX = -x + cwx;
        int screenPosY = -y + cwy;

        int w = mNaviWindow.getWidth();
        int h = mNaviWindow.getHeight();
        if (mDisplayOrientaion == 90) {
            int temp = screenPosX;
            screenPosX = screenPosY;
            screenPosY = temp;

            temp = w;
            w = h;
            h = temp;
        }
        mNaviWindow.layout(screenPosX, screenPosY, screenPosX + w, screenPosY + h);
        mNaviWindow.setVisibility(View.VISIBLE);
    }

    public void showCaptureView() {
        if (mHoldOrientation != NONE_ORIENTATION) {
            onOrientationChanged(mHoldOrientation);
        }
        mPanoView.setVisibility(View.VISIBLE);
        mCenterWindow.setVisibility(View.VISIBLE);
    }

    public void showProgressIndicator() {
        mProgressIndicator.setProgress(PROGRESS_ZERO);
        mProgressIndicator.setVisibility(View.VISIBLE);
    }

    public void hideProgressIndicaotr() {
        Log.d(TAG, "hideProgressIndicaotr,mProgressIndicator = " + mProgressIndicator);
        if (mProgressIndicator != null) {
            mProgressIndicator.setProgress(0);
            mProgressIndicator.setVisibility(View.GONE);
        }
    }

    public void showNaviWindowView() {
        if (mHoldOrientation != NONE_ORIENTATION) {
            onOrientationChanged(mHoldOrientation);
        }
        mNaviWindow.setVisibility(View.VISIBLE);
    }

    public void hideNaviWindowView() {
        mNaviWindow.setVisibility(View.INVISIBLE);
    }

    public void hideCaptureView() {
        if (mCenterWindow != null) {
            mCenterWindow.setVisibility(View.INVISIBLE);
        }
    }
    
    @Override
    public void onOrientationChanged(int orientation) {
            Log.v(TAG, "onOrientationChanged mContext.getCameraState()=" + getContext().getCameraState()
                    + " orientation=" + orientation);
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
}
