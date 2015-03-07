package com.android.camera.actor;

import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.view.OrientationEventListener;
import android.view.View;
import android.view.WindowManager;
import android.view.View.OnClickListener;
import android.widget.ImageView;

import android.graphics.drawable.AnimationDrawable;
import android.graphics.drawable.Drawable;
import android.hardware.Camera.AUTORAMACallback;
import android.hardware.Camera.AUTORAMAMVCallback;
import android.hardware.Camera.Size;

import com.android.camera.Camera;
import com.android.camera.CameraHolder;
import com.android.camera.SaveRequest;
import com.android.camera.Storage;
import com.android.camera.Util;
import com.android.camera.Log;
import com.android.gallery3d.R;
import com.android.camera.FileSaver.FileSaverListener;
import com.android.camera.actor.PanoramaActor.PanoramaCategory;
import com.android.camera.actor.PhotoActor.CameraCategory;
import com.android.camera.manager.ModePicker;
import com.android.camera.manager.PanoramaViewManager;
import com.android.camera.manager.ShutterManager;
import com.android.camera.manager.SingleStereoPhotoViewManager;
import com.android.camera.ui.ShutterButton;

public class SingleStereoPhotoActor extends PhotoActor {
    
    private SingleStereoPhotoViewManager mSingleStereoView;
    private SaveRequest mSaveRequest;
    private static final String TAG = "SingleStereoPhotoActor";
    private static final boolean LOG = Log.LOGV;
	
    protected final Handler mSingleStereoHandler = new SingleStereoHandler(mCamera.getMainLooper());
    private AUTORAMACallback mSinglePanoramaCallback = new SinglePanoramaCallback();
    private AUTORAMAMVCallback mSinglePanoramaMVCallback = new SinglePanoramaMVCallback();

    private static final int IDLE = 0;
    private static final int STARTED = 1;
    private static final int MERGING = 2;
    
    private static final int GUIDE_SHUTTER = 0;
    private static final int GUIDE_MOVE = 1;
    private static final int GUIDE_CAPTURE = 2;
    private static final int DIRECTION_UNKNOWN = 4;
    
    private static final int MSG_FINAL_IMAGE_READY = 1;
    private static final int MSG_CLEAR_SCREEN_DELAY = 2;
    private static final int MSG_LOCK_ORIENTATION = 3;

    private int mCaptureState;

    private static final int NUM_AUTORAMA_CAPTURE = 2;
    private int mCurrentNum = 0;

    private boolean mStopping;  
    private Size mPreviewSize;

    private boolean mPaused;

    private Object mLocker = new Object();
    private boolean mStopProcess = false;
    
    private Runnable mOnHardwareStop;
    private Runnable mRestartCaptureView;
    private boolean mShowingCollimatedDrawable = false;
    
    private Runnable mFalseShutterCallback = new Runnable() {
        @Override
        public void run() {
            // simulate an onShutter event since it is not supported in this mode.
            mCamera.getFocusManager().resetTouchFocus();
            mCamera.getFocusManager().updateFocusUI();
        }
    };
    
    public SingleStereoPhotoActor(Camera context) {
        super(context);
        mCameraCategory = new SingleStereoCategory();
    }
    
    private class SingleStereoHandler extends Handler {
        public SingleStereoHandler(Looper looper) {
            super(looper);
        }
        @Override
        public void handleMessage(Message msg) {
            if (LOG) {
                Log.v(TAG, "handleMessage what= " + msg.what);
            }

            switch (msg.what) {
            case MSG_FINAL_IMAGE_READY:
                updateSavingHint(false, false);
                resetCapture();
                if (!mCameraClosed) {
                    mCameraCategory.animateCapture(mCamera);
                }
                break;
            case MSG_CLEAR_SCREEN_DELAY:
                mCamera.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                break;
            case MSG_LOCK_ORIENTATION:
                lockOrientation();
                break;
            default:
                break;
            }
        }
    }
    
    public boolean capture() {
        if (LOG) {
            Log.v(TAG,"capture begin");
        }
        // If we are already in the middle of taking a snapshot then ignore.
        if (mCamera.getCameraDevice() == null
                || mCamera.getCameraState() == Camera.STATE_SNAPSHOT_IN_PROGRESS) {
            return false;
        }
        // set path
//        mSaveRequest = mContext.preparePhotoRequest(Storage.FILE_TYPE_PANO, Storage.PICTURE_TYPE_JPG);
        mSaveRequest = mContext.preparePhotoRequest(Storage.FILE_TYPE_PANO, Storage.PICTURE_TYPE_MPO_3D);
        // lock awb
        mCamera.getFocusManager().setAwbLock(true);
        setFocusParameters();

        mCamera.switchShutter(ShutterManager.SHUTTER_TYPE_CANCEL);

        if (!startCapture()) { // it is still busy.
            return false;
        }
        mCamera.setCameraState(Camera.STATE_SNAPSHOT_IN_PROGRESS);
        mCamera.setSwipingEnabled(false);
        mCamera.setViewState(Camera.VIEW_STATE_PANORAMA_CAPTURE);
        stopFaceDetection();
        mCamera.getCameraDevice().setAutoFocusMoveCallback(null);
        mCamera.getFocusManager().clearFocusOnContinuous();

        showGuideString(GUIDE_MOVE);
        mCamera.keepScreenOnAwhile();
        mSingleStereoHandler.postDelayed(mFalseShutterCallback, 300);
        return true;
    }
    

    public View.OnClickListener mCancelOnClickListener = new View.OnClickListener() {
        public void onClick(View v) {
            onCanclKeyPressed();
        }
    };
    
    @Override
    public OnClickListener getCancelListener() {
        return mCancelOnClickListener;
    }
    
    public void onCanclKeyPressed() {
        if (LOG) {
            Log.v(TAG, "onCanclKeyPressed " + " state=" + mCamera.getCameraState());
        }
        if (mCamera.getCameraState() == Camera.STATE_SNAPSHOT_IN_PROGRESS) {
            stopCapture(false);
            mCamera.dismissInfo();
        }
    }
    
    @Override
    public int getMode() {
        return ModePicker.MODE_PHOTO_SGINLE_3D;
    }
    
    public boolean startCapture() {
        if (LOG) {
            Log.v(TAG, "startCapture");
        }
        
        if (mCamera.getCameraDevice() != null && mCaptureState == IDLE && !mStopping) {
            mCaptureState = STARTED;
            mCurrentNum = 0;
            mShowingCollimatedDrawable = false;
            
            doStart();
            mSingleStereoView.show();
            return true;
        } 
       if (LOG) {
            Log.v(TAG, "startCapture mCaptureState: " + mCaptureState);
       }
       return false;
    }
    
    private void doStart() {
        if (LOG) {
            Log.v(TAG, "doStart");
        }
        mCamera.getCameraDevice().setAUTORAMACallback(getSinglePanoramaCallback());
        mCamera.getCameraDevice().setAUTORAMAMVCallback(getSinglePanoramaMVCallback());
        mCamera.getCameraDevice().start3DSHOT(NUM_AUTORAMA_CAPTURE);
        //mCamera.getCameraDevice().startAUTORAMA(NUM_AUTORAMA_CAPTURE);
    }
    
    public boolean hasCaptured() {
        if (LOG) {
            Log.v(TAG, "hasCaptured mCaptureState =" + mCaptureState + " mCurrentNum: " + mCurrentNum);
        }
        return mCaptureState != IDLE && mCurrentNum > 0;
    }
    
    private void resetCapture() {
        if (LOG) {
            Log.v(TAG, "resetCapture mCamera.getCameraState()=" + mCamera.getCameraState());
        }
        //mShutterPressed = false;
        // if we need to wait for merge,unlockAeAwb must be called after we receive the last callback.
        // so if isMerge = true,we will do it later in onCaptureDone.
        if (mCamera.getCameraState() == Camera.STATE_SNAPSHOT_IN_PROGRESS) {
            //unlockAeAwb();
            mCamera.setCameraState(Camera.STATE_IDLE);
        }
        mCamera.restoreViewState();
        mCamera.switchShutter(ShutterManager.SHUTTER_TYPE_PHOTO_VIDEO);
        mCamera.setSwipingEnabled(true);
        mCamera.keepScreenOnAwhile();
        //showGuideString(GUIDE_SHUTTER);

        if (!mCameraClosed) {
            mCamera.getCameraDevice().setAutoFocusMoveCallback(getAutoFocusMoveCallback());
            startFaceDetection();
        }
    }
    
    public void stopCapture(boolean isMerge) {
        //only do merge when already have captured images.
        if (!hasCaptured()) {
            isMerge = false;
        }
        stop(isMerge);
        if (mCameraClosed && mCaptureState != IDLE) {
            mCaptureState = IDLE;
            mCamera.setSwipingEnabled(true);
            mSingleStereoView.resetController();
            mSingleStereoView.hide();
            updateSavingHint(false, false);
            mCamera.switchShutter(ShutterManager.SHUTTER_TYPE_PHOTO_VIDEO);
            mCamera.restoreViewState();
        }
    }
    
    public void stop(boolean isMerge) {
        if (LOG) {
            Log.v(TAG, "stop mCaptureState: " + mCaptureState);
        }

        if (mCamera.getCameraDevice() != null && mCaptureState == STARTED) {
            mCaptureState = isMerge ? MERGING : IDLE;
            if (!isMerge) {
                mCamera.getCameraDevice().setAUTORAMACallback(null);
                mCamera.getCameraDevice().setAUTORAMAMVCallback(null);
            } else {
                onMergeStarted();
            }
            stopAsync(isMerge);
            mSingleStereoView.resetController();
            mSingleStereoView.hide();
        }
    }
    
    private void stopAsync(final boolean isMerge) {
        if (LOG) {
            Log.d(TAG, "stopAsync mStopping: " + mStopping);
        }
        
        if (mStopping) return;

        mStopping = true;
        Thread stopThread = new Thread(new Runnable() {
            public void run() {
                doStop(isMerge);
                mSingleStereoHandler.post(new Runnable() {
                     public void run() {
                         mStopping = false;
                         if (!isMerge) {//if isMerge is true, onHardwareStopped will be called in onCapture.
                            onHardwareStopped(false);
                            mSingleStereoView.resetController();
                         }
                     }
                });

                synchronized (mLocker) {
                    mStopProcess = false;
                    mLocker.notifyAll();
                }
            }
        });
        synchronized (mLocker) {
            mStopProcess = true;
        }
        stopThread.start();
    }
    
    private void doStop(boolean isMerge) {

        if (LOG) {
            Log.v(TAG, "doStop isMerge " + isMerge);
        }

        if (mCamera.getCameraDevice() != null) {
            CameraHolder holder = CameraHolder.instance();
            synchronized (holder) {
                if (holder.isSameCameraDevice(mCamera.getCameraDevice())) {
                // means that hw was shutdown and no need to call stop anymore.
                   mCamera.getCameraDevice().stop3DSHOT(isMerge ? 1 : 0);
                } else {
                    Log.w(TAG, "doStop device is release? ");
                }
            }
        }

    }
    
    public void onCameraClose() {
        mCameraClosed = true;
        safeStop();
        super.onCameraClose();
    }
    
    private void safeStop() {
        CameraHolder holder = CameraHolder.instance();
        if (LOG) {
            Log.v(TAG, "check stopAsync thread state, if running, we must wait");
        }
        checkStopProcess();
        synchronized(holder) {
            stopPreview();
        }
        stopCapture(false);
    }
    
    public void checkStopProcess() {
        while(mStopProcess) {
            waitLock();
        }
    }
    
    private void waitLock() {
        try {
            synchronized(mLocker) {
                mLocker.wait();
            }
        } catch (InterruptedException e) {
            Log.w(TAG, "interruptedException in waitLock()");
        }
    }
    private final class SinglePanoramaCallback implements AUTORAMACallback {
        public void onCapture(byte[] jpegData) {
            Log.i(TAG, "onCapture: " + mCurrentNum + ",mCaptureState: " + mCaptureState);
            if (mCaptureState == IDLE) return;

            if (mCurrentNum == NUM_AUTORAMA_CAPTURE || mCaptureState == MERGING) {
                Log.i(TAG, "onCapture done");
                mCaptureState = IDLE;
                mJpegImageData = jpegData;
                if (mCamera.getCameraDevice() != null) {
                    //if native merge done message delay, and camera release is called
                    //we should not save the file
                    onHardwareStopped(true);
                }
            } else if (mCurrentNum >= 0 && mCurrentNum < NUM_AUTORAMA_CAPTURE) {
                mSingleStereoView.setViewsForNext(mCurrentNum);
                if (mShowingCollimatedDrawable) {
                    mSingleStereoHandler.removeCallbacksAndMessages(null);
                }
                mShowingCollimatedDrawable = true;
                mSingleStereoHandler.postDelayed(new Runnable() {
                    public void run() {
                        mShowingCollimatedDrawable = false;
                        mSingleStereoView.stopCaptureAnimation();
                        if (mCaptureState != STARTED) {
                            mSingleStereoView.hideSingleStereoView();
                        }
                    }
                }, 500);
            } else {
                Log.w(TAG, "onCapture is called in abnormal state");
            }
            mCurrentNum++;
            if (mCurrentNum == NUM_AUTORAMA_CAPTURE) {
                stop(true);
            }
        }
    }

    public AUTORAMACallback getSinglePanoramaCallback() {
        return mSinglePanoramaCallback;
    }
    
    private final class SinglePanoramaMVCallback implements AUTORAMAMVCallback {
        public void onFrame(int xy, int direction) {
            
            boolean shown = mPaused || direction == DIRECTION_UNKNOWN ||  mShowingCollimatedDrawable || mCaptureState != STARTED || mCurrentNum < 1;
            if (shown) {
                return;
            } else {
                mSingleStereoView.updateMovingUI(xy, direction, shown);
            }
        }
    }

    public AUTORAMAMVCallback getSinglePanoramaMVCallback() {
        return mSinglePanoramaMVCallback;
    }
    
    public void onMergeStarted() {
        if (!mCameraClosed) {
            updateSavingHint(true, false);
            mCamera.dismissInfo();
        }
    }
    
    private void onHardwareStopped(boolean isMerge) {
        Log.d(TAG, "onHardwareStopped isMerge: " + isMerge);

        if (isMerge) {
            mCamera.getCameraDevice().setAUTORAMACallback(null);
            mCamera.getCameraDevice().setAUTORAMAMVCallback(null);
        }   
        onCaptureDone(isMerge);
    }
    
    private void onCaptureDone(boolean isMerge) {
        if (LOG) {
            Log.v(TAG, "onCaptureDone isMerge " + isMerge + " mCameraState=" + mCamera.getCameraState());
        }

        if (isMerge && mJpegImageData != null) {
            mSaveRequest.setData(mJpegImageData);
            mSaveRequest.addRequest();
            mSaveRequest.setListener(mFileSaverListener);
        } else {
            resetCapture();
        }
    }
    
    private FileSaverListener mFileSaverListener = new FileSaverListener() {
        @Override
        public void onFileSaved(SaveRequest request) {
            mSingleStereoHandler.sendEmptyMessage(MSG_FINAL_IMAGE_READY);
        }
    };
    
    public void showGuideString(int step) {
        int guideId = 0;
        switch (step) {
        case GUIDE_SHUTTER:
           // guideId = R.string.panorama_guide_shutter;
            break;
        case GUIDE_MOVE:
            guideId = R.string.single3d_guide_move;
            break;
        case GUIDE_CAPTURE:
           // guideId = R.string.panorama_guide_capture;
            break;
        default:
            break;
        }

        // show current guide
        if (guideId != 0) {
            mCamera.showInfo(mCamera.getString(guideId), Camera.SHOW_INFO_LENGTH_LONG);
        }
    }
        
    @Override
    public void onCameraParameterReady(boolean startPreview) {
        super.onCameraParameterReady(startPreview);
        mSingleStereoHandler.sendEmptyMessage(MSG_LOCK_ORIENTATION);
    }
        
    public void release() {
        super.release();
        mCamera.removeOnFullScreenChangedListener(mFullScreenChangedListener);
        if (mSingleStereoView != null) {
            mSingleStereoView.release();
        }
    }
    
    ///add for single 3d lock orientation
    private void lockOrientation() {
        if (LOG) {
            Log.v(TAG, "lockOrientation mCamera.getCameraDisplayOrientation() =" + mCamera.getCameraDisplayOrientation()
                    + " mCamera.getOrietation() = " + mCamera.getOrietation());
        }
        //default landscape mode, or cameraInfo.orientation.
        if (mCamera.getCameraDisplayOrientation() == 0 || mCamera.getCameraDisplayOrientation() == 180) {
            mCamera.setOrientation(true, (mCamera.getOrietation() == 0 || mCamera.getOrietation() == 180)
                    ? OrientationEventListener.ORIENTATION_UNKNOWN : 0);
        } else {
            mCamera.setOrientation(true, (mCamera.getOrietation() == 90 || mCamera.getOrietation() == 270)
                    ? OrientationEventListener.ORIENTATION_UNKNOWN : 270);
        }
    }
    
    private Camera.OnFullScreenChangedListener mFullScreenChangedListener = new Camera.OnFullScreenChangedListener() {

        @Override
        public void onFullScreenChanged(boolean full) {
            if (LOG) {
                Log.v(TAG, "onFullScreenChanged full = " + full);
            }
            if (full) {
                // we should keep landscape orientation
                lockOrientation();
            }
        }
    };
    
 ///////////////////////////////////////////////////////////////////////////////////////
    

    class SingleStereoCategory extends CameraCategory {
        public void initializeFirstTime() {
            mCamera.addOnFullScreenChangedListener(mFullScreenChangedListener);
            mSingleStereoView = new SingleStereoPhotoViewManager(mCamera);
        }

        public void doShutter() {
            mCamera.setSwipingEnabled(false);
        }

        @Override
        public void ensureCaptureTempPath() {
            //mSaveRequest = mContext.preparePhotoRequest(Storage.FILE_TYPE_PANO, Storage.PICTURE_TYPE_MPO_3D);
        }
        
        @Override
        public void onLeaveActor() {
            mCamera.restoreViewState();
            mCamera.setOrientation(false, OrientationEventListener.ORIENTATION_UNKNOWN);
        }

    }
}