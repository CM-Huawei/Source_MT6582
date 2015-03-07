package com.android.camera.actor;

import android.hardware.Camera.CameraInfo;
import android.hardware.Camera.SmileCallback;

import com.android.camera.Camera;
import com.android.camera.CameraHolder;
import com.android.camera.Log;
import com.android.camera.R;
import com.android.camera.SaveRequest;
import com.android.camera.manager.ModePicker;
import com.android.camera.ui.ShutterButton;
import com.android.camera.ui.ShutterButton.OnShutterButtonListener;

public class SymbolActor extends PhotoActor {
    private static final String TAG = "SymbolActor";

    private static final boolean SAVE_ORIGINAL_PICTURE = true;
    private static final int SYMBOLSHOT_STANDBY = 0;
    private static final int SYMBOLSHOT_INTERVAL = 1;
    private static final int SYMBOLSHOT_IN_PROGRESS = 2;
    private SaveRequest mOriginalSaveRequest;
    private int mStatus = SYMBOLSHOT_STANDBY;
    private static final int SMILE_SHOT_INTERVAL = 1500;
    private boolean mActorIsLeaved = false;

    private Runnable mDoSymbolSnapRunnable = new Runnable() {
        @Override
        public void run() {
            Log.i(TAG, "onShutterButtonClick(null), CameraState = " + mCamera.getCameraState() + "," +
            		"mStatus:" + mStatus + ",mActorIsLeaved:" + mActorIsLeaved);
            if (mStatus != SYMBOLSHOT_IN_PROGRESS
                    && mCamera.getCameraState() != Camera.STATE_SNAPSHOT_IN_PROGRESS) {
                mStatus = SYMBOLSHOT_STANDBY;
                if (!mActorIsLeaved) {
                    readyToCapture();
                }
               
            }
        }
    };

    public SymbolActor(Camera context) {
        super(context);
        Log.i(TAG, "SymbolActor initialize");
    }

    @Override
    public void onCameraParameterReady(boolean startPreview) {
        super.onCameraParameterReady(startPreview);
        Log.i(TAG, "SymbolActor onCameraParameterReady, mStatus:" + mStatus);
        mActorIsLeaved = false;
        ensureFDState(true);
        postSymbolSnapRunnable();
    }

    protected void postSymbolSnapRunnable() {
    	if (mStatus != SYMBOLSHOT_IN_PROGRESS
                && !mHandler.hasCallbacks(mDoSymbolSnapRunnable)) {
            mHandler.post(mDoSymbolSnapRunnable);
        }
    }
    
    protected void removeSymbolSnapRunnable() {
    	mHandler.removeCallbacks(mDoSymbolSnapRunnable);
    }
    
    @Override
    public void release() {
        super.release();
        mCamera.removeOnFullScreenChangedListener(mFullScreenChangedListener);
        mCameraCategory.doCancelCapture();
        ensureFDState(false);
    }

    @Override
    public OnShutterButtonListener getPhotoShutterButtonListener() {
        return this;
    }

    @Override
    public boolean readyToCapture() {
        Log.i(TAG, " readyToCapture? mStatus = " + String.valueOf(mStatus));
        if (mStatus == SYMBOLSHOT_STANDBY) {
            openSymbolShutterMode();
            return false;
        }
        return true;
    }

    public void openSymbolShutterMode() {
        Log.i(TAG, "openSymbolShutterMode ");
        if (mCamera.getCameraDevice() == null) {
            Log.e(TAG, "CameraDevice is null, ignore");
            return;
        }
        mStatus = SYMBOLSHOT_IN_PROGRESS;
        Log.i(TAG, "set mStatues as SMILESHOT_IN_PROGRESS");
        startSymbolDetection();
    }

    @Override
    public boolean doSymbolShutter() {
        Log.i(TAG, "doSmileShutter mStatus = " + String.valueOf(mStatus));
        if (mStatus == SYMBOLSHOT_IN_PROGRESS) {
            // already in smile shutter mode, capture directly.
            stopSymbolDetection();
            capture();
            return true;
        }
        return false;
    }

    private Camera.OnFullScreenChangedListener mFullScreenChangedListener = new Camera.OnFullScreenChangedListener() {

        @Override
        public void onFullScreenChanged(boolean full) {
            if (!full) {
                mHandler.removeCallbacks(mDoSymbolSnapRunnable);
                stopSymbolDetection();
            } else if (mStatus != SYMBOLSHOT_IN_PROGRESS
                    && !mHandler.hasCallbacks(mDoSymbolSnapRunnable)) {
                mHandler.post(mDoSymbolSnapRunnable);
            }
        }
    };

    /**
     * The function is to ensure FD is stopped in front sensor except Smile
     * shot.
     */
    public void ensureFDState(boolean enable) {
        Log.i(TAG, "ensureFDState enable=" + enable + "CameraState=" + mCamera.getCameraState());
        if (mCamera.getCameraState() != Camera.STATE_IDLE) {
            return;
        }
        if (enable) {
            startFaceDetection();
        } else {
            CameraInfo info = CameraHolder.instance().getCameraInfo()[mCamera.getCameraId()];
            if (info.facing == CameraInfo.CAMERA_FACING_FRONT) {
                stopFaceDetection();
            }
        }
    }
    
    public void handleSDcardUnmount() {
        if (mCamera.getCameraDevice() == null) {
            return;
        }
        if (mStatus == SYMBOLSHOT_IN_PROGRESS) {
            stopSymbolDetection();
        }
        //Camera.getCameraDevice().setSmileCallback(null);
    }

    public boolean isInShutterProgress() {
        return mStatus == SYMBOLSHOT_IN_PROGRESS;
    }
    
    public boolean isInStandby() {
    	return mStatus == SYMBOLSHOT_STANDBY;
    }
    
    protected void startSymbolDetection() {
    }
    
    protected void stopSymbolDetection() {
    	mStatus = SYMBOLSHOT_STANDBY;
    }
     
    @Override
    public void onShutterButtonLongPressed(ShutterButton button) {
        Log.i(TAG, "Symbol.onShutterButtonLongPressed(" + button + ")");
    }
    
    private boolean isSymbolActor() {
    	return mCamera.getCurrentMode() == ModePicker.MODE_SMILE_SHOT
    			|| mCamera.getCurrentMode() == ModePicker.MODE_GESTURE_SHOT
    			|| mCamera.getCurrentMode() == ModePicker.MODE_FACE_BEAUTY;
    			
    }

    public class SymbolCameraCategory extends CameraCategory {
        @Override
        public void initializeFirstTime() {
            mCamera.addOnFullScreenChangedListener(mFullScreenChangedListener);
        }

        @Override
        public boolean supportContinuousShot() {
            return false;
        }

        @Override
        public boolean applySpecialCapture() {
            return false;
        }

        @Override
        public void doOnPictureTaken() {
            Log.i(TAG, "doOnPictureTaken() mCamera.isFullScreen() = " + mCamera.isFullScreen()
                    + " mCamera.getCurrentMode() = " + mCamera.getCurrentMode());
            if (mCamera.isFullScreen() && isSymbolActor()) {
                if (mHandler.hasCallbacks(mDoSymbolSnapRunnable)) {
                    mHandler.removeCallbacks(mDoSymbolSnapRunnable);
                }
                mHandler.postDelayed(mDoSymbolSnapRunnable, SMILE_SHOT_INTERVAL);
                mStatus = SYMBOLSHOT_INTERVAL;
            }
        }

        @Override
        public boolean doCancelCapture() {
            Log.i(TAG, "mCamera.getCameraDevice()=" + mCamera.getCameraDevice()
                    + " mStatus=" + mStatus);
            mCamera.setSwipingEnabled(true);
            if (mCamera.getCameraDevice() == null) {
                return false;
            }
            if (mStatus == SYMBOLSHOT_IN_PROGRESS) {
                stopSymbolDetection();
            } else {
                mStatus = SYMBOLSHOT_STANDBY;
            }
            return false;
        }

        @Override
        public void onLeaveActor() {
        	mActorIsLeaved = true;
            mCamera.restoreViewState();

            mHandler.removeCallbacks(mDoSymbolSnapRunnable);
            if (mStatus == SYMBOLSHOT_IN_PROGRESS) {
                stopSymbolDetection();
            }
        }
    }
}
