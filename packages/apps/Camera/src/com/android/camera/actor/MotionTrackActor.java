package com.android.camera.actor;


import android.R.xml;
import android.hardware.Camera.Parameters;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.view.OrientationEventListener;
import android.view.WindowManager;

import com.android.camera.Camera;
import com.android.camera.CameraHolder;
import com.android.camera.FileSaver;
import com.android.camera.Log;
import com.android.camera.SaveRequest;
import com.android.camera.Storage;
import com.android.camera.Util;
import com.android.camera.R;
import com.android.camera.FileSaver.FileSaverListener;
import com.android.camera.manager.MMProfileManager;
import com.android.camera.manager.ModePicker;
import com.android.camera.manager.MotionTrackViewManager;
import com.android.camera.manager.ShutterManager;
import com.android.camera.ui.ProgressIndicator;
import com.android.camera.ui.ShutterButton;


import android.hardware.Camera.MotionTrackCallback;


public class MotionTrackActor extends PhotoActor {

    private static final String TAG = "MotionTrack";
    public static final int GUIDE_SHUTTER = 0;
    public static final int GUIDE_MOTION_TRACK_FAILED = 1;
    public static final int GUIDE_MOTION_TRACK_MOVE = 2;
    private int mCurrentNum = 0;
    private MotionTrackViewManager mMotionTrackView;
    private boolean mShutterPressed;
    private SaveRequest mSaveRequest;
    private Camera mContext;

    private static final int BURST_SAVING_DONE = 101;
    private static final int MSG_LOCK_ORIENTATION = 3;
    private static final int MAX_MOTHION_TRACK_NUMBER = 20;

    private static final int GUIDE_MOVE = 1;
    private static final int GUIDE_CAPTURE = 2;

    private boolean mLongPressed = false;
    private boolean mBlendedFailed = false;
    private boolean mIgnoreClick = false;
    
    private boolean mSavingPictures = false;
    private boolean mMotionTrackStopeed = true;
    private boolean mIsShowAlterDilogInProcess = false;

    private Thread mWaitSavingDoneThread;

    private final Handler mMotionTrackHandler = new MotiontrackHandler(mCamera.getMainLooper());
    private MotionTrackCallback mMotionTrackCallback = new MTCallback();

    private Runnable mFalseShutterCallback = new Runnable() {
        @Override
        public void run() {
            mCamera.getFocusManager().resetTouchFocus();
            mCamera.getFocusManager().updateFocusUI();
        }
    };

    public MotionTrackActor(Camera context) {
        super(context);
        Log.d(TAG, "MotionTrack initialize");
        mContext = context;
        mCameraCategory = new MotionTrackCategory();
    }

    private class MotiontrackHandler extends Handler {
        public MotiontrackHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            Log.i(TAG, "handleMessage, msg.what = " + msg.what);
            switch (msg.what) {
            case MSG_LOCK_ORIENTATION:
                lockOrientation();
                break;
            case BURST_SAVING_DONE:
                updateSavingHint(false, false);
                resetCapture();
                break;
            default:
                break;
            }
        }
    }

    private class WaitMotionTrackSavingDoneThread extends Thread {
        @Override
        public void run() {
            Log.i(TAG, "WaitSavingDoneThread, will BURST_SAVING_DONE");
            mCamera.getFileSaver().waitDone();
            mMotionTrackHandler.sendEmptyMessage(BURST_SAVING_DONE);
        }
    }

    @Override
    public int getMode() {
        return ModePicker.MODE_MOTION_TRACK;
    }

    private void resetCapture() {
        Log.d(TAG, "resetCapture,cameraState = " + mCamera.getCameraState());
        if (mCamera.getCameraState() == Camera.STATE_SNAPSHOT_IN_PROGRESS) {
            unlockAeAwb();
            mCamera.setCameraState(Camera.STATE_IDLE);
        }
        mSavingPictures = false;
        mCamera.restoreViewState();
        mCamera.switchShutter(ShutterManager.SHUTTER_TYPE_PHOTO_VIDEO);
        mCamera.setSwipingEnabled(true);
        mCamera.keepScreenOnAwhile();
        mMotionTrackView.show();
        mMotionTrackView.hideNaviWindowView();

        if (!mCameraClosed) {
            mCamera.getCameraDevice().setAutoFocusMoveCallback(getAutoFocusMoveCallback());
        }
    }

    private void unlockAeAwb() {
        Log.d(TAG, "unlockAeAwb");
        if (mCamera.getCameraState() != Camera.STATE_PREVIEW_STOPPED) {
            mCamera.getFocusManager().setAeLock(false); // Unlock AE and AWB.
            mCamera.getFocusManager().setAwbLock(false);
            setFocusParameters();
            if (Parameters.FOCUS_MODE_CONTINUOUS_PICTURE.equals(mCamera.getFocusManager().getFocusMode())) {
                mCamera.getCameraDevice().cancelAutoFocus();
            }
        }
    }

    private Camera.OnFullScreenChangedListener mFullScreenChangedListener = new Camera.OnFullScreenChangedListener() {
        @Override
        public void onFullScreenChanged(boolean full) {
            Log.d(TAG, "onFullScreenChanged full = " + full);
            if (full) {
                // we should keep landscape orientation
                lockOrientation();
            }
        }
    };

    private void lockOrientation() {
//        Log.d(TAG, "lockOrientation mCamera.getCameraDisplayOrientation() =" + mCamera.getCameraDisplayOrientation()
//                + " mCamera.getOrietation() = " + mCamera.getOrietation());
        //default landscape mode, or cameraInfo.orientation.
        if (mCamera.getCameraDisplayOrientation() == 0 || mCamera.getCameraDisplayOrientation() == 180) {
            mCamera.setOrientation(true, (mCamera.getOrietation() == 0 || mCamera.getOrietation() == 180)
                    ? OrientationEventListener.ORIENTATION_UNKNOWN : 0);
        } else {
            mCamera.setOrientation(true, (mCamera.getOrietation() == 90 || mCamera.getOrietation() == 270)
                    ? OrientationEventListener.ORIENTATION_UNKNOWN : 270);
        }
    }

    @Override
    public void onDisplayRotate() {
//        Log.d(TAG, "onDisplayRotate mCamera.isFullScreen() = " + mCamera.isFullScreen());
        if (mCamera.isFullScreen()) {
            // need to relock orientation when display rotate
            // used case: orientation = 90 and slide from gallery2 to camera
            mCamera.setOrientation(false, OrientationEventListener.ORIENTATION_UNKNOWN);
            lockOrientation();
        }
    }

    public void showGuideString(int step) {
        Log.d(TAG, "showGuideString, step = " + step);
        int guideId = 0;
        switch (step) {
        case GUIDE_SHUTTER:
            guideId = R.string.motion_track_guide_shutter;
            break;
        case GUIDE_MOTION_TRACK_FAILED:
            guideId = R.string.motion_track_required_more;
            break;
        case GUIDE_MOTION_TRACK_MOVE:
            guideId = R.string.motion_track_move;
            break;
        }
        if (guideId != 0) {
            mCamera.showInfo(mCamera.getString(guideId), Camera.SHOW_INFO_LENGTH_LONG);
        }
    }

    private void savingDoneThread() {
        if (mWaitSavingDoneThread == null || !mWaitSavingDoneThread.isAlive()) {
            mWaitSavingDoneThread = new WaitMotionTrackSavingDoneThread();
            mWaitSavingDoneThread.start();
        }
    }

    private final class MTCallback implements MotionTrackCallback {
    	
        public void onFrame(int x, int y, int direction) {
          if (mCurrentNum < 1 || mCurrentNum >= MAX_MOTHION_TRACK_NUMBER || !mLongPressed) {
              Log.d(TAG, "will return ,not update the MovingUI");
              return;
          }

          mMotionTrackView.updateMovingUI(x, y, false);
      }

        public void onBlended(byte[] data, int imageIndex, int totalIndex) {
            Log.i(TAG, "onBlended, imageindex = " + imageIndex + ", totoalIndex = " + totalIndex + "data.length = " + data.length);
            // totalIndex = 0 means blended failed
            if (totalIndex == 0) { // no Image to merge, should dismiss dialog.
                mBlendedFailed = true;
                mContext.getFileSaver().onContinousShotDone();
                showMotionFailedAlterDialog();
                return;
            }
            imageIndex++;
            mSaveRequest.setContinuousRequest(true);
            mBlendedFailed = false;
            setSaveRequest(data, FileSaver.BLENDED_IMAGE, imageIndex, totalIndex);
            if (imageIndex == totalIndex) {
                updateSavingHint(true, false);
                savingDoneThread();
                //if not need this,will found the saving dialog will not dismiss,
                //because will always run in the doInback ground,so onPostExcute->notifyAll
                mContext.getFileSaver().onContinousShotDone();
            } else {
                mSaveRequest = mContext.getFileSaver().copyPhotoRequest(mSaveRequest);
            }
        }

        public void onIntermediate(byte[] data) {
            Log.i(TAG, "onIntermediate, data = " + data);
            if (data == null) {
                return;
            }
            mSaveRequest.setContinuousRequest(true);
            setSaveRequest(data, FileSaver.INTERMEDIA_IMAGE, 0, FileSaver.UNKONWEN_TOTATL);
            mSaveRequest = mContext.getFileSaver().copyPhotoRequest(mSaveRequest);
        }

        public void onCapture(byte[] data) {
            Log.i(TAG, "onCapture callback, data = " + data+ ",mCurrentNum = " + mCurrentNum);
            mIgnoreClick = true;
            if (data == null) {
                return;
            }
            mSaveRequest.setContinuousRequest(true);
            mCurrentNum++;
            if (mLongPressed && mStreamID == 0) {
                playContinuousSound();
            }
            if (mCurrentNum >= 0 && mCurrentNum <= MAX_MOTHION_TRACK_NUMBER) {
                if (mCurrentNum == 1 || mCurrentNum == 2) {
                    mMotionTrackView.setProgress(1); // if we capture 1 or 2 ,will found the progress is 0
                } else {
                    //how to do ?
                    mMotionTrackView.setProgress((mCurrentNum +1) * ProgressIndicator.BLOCK_NUMBERS / MAX_MOTHION_TRACK_NUMBER);
                }
            }
            // save the data
            setSaveRequest(data, FileSaver.ORIGINAL_IMAGE, mCurrentNum, FileSaver.UNKONWEN_TOTATL);
            mSaveRequest = mContext.getFileSaver().copyPhotoRequest(mSaveRequest);

            if (mCurrentNum == MAX_MOTHION_TRACK_NUMBER) {
                mIgnoreClick = true;
                onShutterButtonFocus(null, false);
            }
        }
    }

    public void setSaveRequest(byte[] jpegData, int tag, int index, int total) {
        Log.d(TAG, "setSaveRequest,tag =" + tag + ",index = " + index + ",total = " + total);
        mSaveRequest.setTag(tag);
        mSaveRequest.setIndex(index, total);
        mSaveRequest.setData(jpegData);
        mSaveRequest.addRequest();
        Log.i(TAG, "end of setSaveRequest");
    }

    // if not support single capture,will go the single route
    @Override
    public void onShutterButtonClick(ShutterButton button) {
        Log.d(TAG, "onShutterButtonClick,ignoreClick = " + mIgnoreClick);
        if (!mLongPressed && !mIgnoreClick) { // this is used to cancel the long pressed after capture more than one
            super.onShutterButtonClick(button);
        }
    }

    @Override
    public void onShutterButtonLongPressed(ShutterButton button) {
        Log.d(TAG, "onShutterButtonLongPressed");
        // not support WFD
        if (mWfdListenerEnabled || mCamera.getWfdManagerLocal().isWfdEnabled()) {
            mCamera.showInfo(mCamera.getString(R.string.wfd_motion_track_not_supported));
            return;
        }
        // check sdcard size
        if (!isCameraPrepareDone()) {
            return;
        }
        mLongPressed = true;
        mBlendedFailed = false;
        mCamera.getFocusManager().setNeedAutoFocus(false);
        //when in Motion Track process,need lock the 3A
        //lock begain
        overrideFocusMode(Parameters.FOCUS_MODE_AUTO);
        mCamera.getFocusManager().onShutterDown();
        mSaveRequest = mContext.preparePhotoRequest();
        mStreamID = 0;
         // lock AWB
        mCamera.getFocusManager().setAwbLock(true);
        //lock end
        setFocusParameters();
        mCurrentNum = 0;
        mContext.setViewState(Camera.VIEW_STATE_CONTINIUOUS);
        startCapture();
    }

    @Override
    public void onShutterButtonFocus(ShutterButton button, boolean pressed) {
        Log.i(TAG, "onShutterButtonFocus, press : " + pressed + "; mLongPressed = " + mLongPressed);
        
        if (pressed) {
            mIgnoreClick = false;
        }
        if (!mLongPressed) {
            super.onShutterButtonFocus(button, pressed);
            return;
        }

        if (!pressed && mLongPressed && !mCameraClosed) {
            Log.i(TAG, "onShutterButtonFocus,will stopCaptre,mCurrentNum = " + mCurrentNum + "; mBlendedFailed = "
                    + mBlendedFailed);
            mLongPressed = false;
            stopContinuousSound();

            boolean needMerge = mCurrentNum > 1 && !mBlendedFailed;
            Log.d(TAG, "needMerge = " + needMerge);
            if (!needMerge) {
                mMotionTrackView.resetController();
                mMotionTrackView.hide();
                resetCapture();
                showMotionFailedAlterDialog();
                return;
            }
            mSavingPictures = true;
            //move show toast to the onBlended callback
            //because when capture less than 2 pictures,will show a saving
            //toast,not very smart
//            updateSavingHint(true, false);
            stopCapture();
        }
    }

    public void showMotionFailedAlterDialog() {
        // if capture number is 1,will first show take more and also this time
        // the onBlended will must fail, so this time the alterDialog not need show
        if (mIsShowAlterDilogInProcess) {
            //ignore this time
            Log.i(TAG, "showMotionFailedAlterDialog,will ignor this time");
            return;
        }
        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                captureFailed();
            }
        };
        doStop();
        if (mCurrentNum < 2) {
            mCamera.showAlertDialog(null, mCamera.getString(R.string.motion_track_required_more), mCamera
                    .getString(android.R.string.ok), runnable, null, null);
        } else {
            mCamera.showAlertDialog(null, mCamera.getString(R.string.motion_track_blended_failed), mCamera
                    .getString(android.R.string.ok), runnable, null, null);
        }
        mIsShowAlterDilogInProcess = true;
    }

    private void captureFailed() {
        Log.d(TAG, "captureFailed");
        mMotionTrackView.resetController();
        mCamera.setCameraState(Camera.STATE_IDLE);
        unlockAeAwb();
        resetCapture();
        mCamera.getFocusManager().resetTouchFocus();
        mIsShowAlterDilogInProcess = false;
    }

    @Override
    public boolean capture() {
        return super.capture();
    }
    
    // isMerge :true means the picture will merge the success MT pic
    // false: means the MT capture failed
    public void stopCapture() {
        doStop();
        mMotionTrackView.resetController();
        mMotionTrackView.hide();// need check
        //when finished the motion track capture,need unlock the awb
        unlockAeAwb();
    }

    private void doStop() {
        Log.i(TAG, "doStop isMerge mMotionTrackStopeed ="+mMotionTrackStopeed);
        if (mCamera.getCameraDevice() != null && mMotionTrackStopeed == false) {
            CameraHolder holder = CameraHolder.instance();
            synchronized (holder) {
                if (holder.isSameCameraDevice(mCamera.getCameraDevice())) {
                    Log.i(TAG, "stopMotionTrack");
                    mCamera.getCameraDevice().stopMotionTrack();
                    mMotionTrackStopeed = true;
                } else {
                    Log.w(TAG, "doStop device is release? ");
                }
            }
        }
    }

    private void startCapture() {
        Log.d(TAG, "startCapture, mCameraDevice =" + mCamera.getCameraDevice());
        if (mCamera.getCameraDevice() != null) {
            mMotionTrackView.showProgressIndicator();
            doStart();
        }
    }

    private void doStart() {
        Log.i(TAG, "doStart, startMotionTrack");
        mCamera.getCameraDevice().setMotionTrackCallback(mMotionTrackCallback);
        mCamera.getCameraDevice().startMotionTrack(MAX_MOTHION_TRACK_NUMBER);
        mMotionTrackStopeed = false;
    }

    @Override
    public void onCameraParameterReady(boolean startPreview) {
        super.onCameraParameterReady(startPreview);
        mMotionTrackHandler.sendEmptyMessage(MSG_LOCK_ORIENTATION);
    }

    public void release() {
        super.release();
        mCamera.removeOnFullScreenChangedListener(mFullScreenChangedListener);
        if (mMotionTrackHandler != null) {
            mMotionTrackHandler.removeMessages(MSG_LOCK_ORIENTATION);
        }
        if (mMotionTrackView != null) {
            mMotionTrackView.hideCaptureView();
            mMotionTrackView.release();
        }
    }

    
    @Override
    public boolean handleFocus() {
        // TODO Auto-generated method stub
        if (mLongPressed) {
            return true;
        } else {
            return super.handleFocus();
        }
    }

    class MotionTrackCategory extends CameraCategory {
        public void initializeFirstTime() {
            Log.d(TAG, "MotionTrack/CameraCategory/initializeFirstTime");
            mCamera.addOnFullScreenChangedListener(mFullScreenChangedListener);
            showGuideString(GUIDE_SHUTTER);
            mMotionTrackView = new MotionTrackViewManager(mCamera);
            mMotionTrackView.show();
            mCamera.getPickerManager().hide();
        }

        public void shutterPressed() {
        }

        public void shutterUp() {
            Log.d(TAG, "MT.shutterUp");
            mCamera.getFocusManager().setNeedAutoFocus(true);
            mCamera.getFocusManager().onShutterUp();
        }

        public boolean supportContinuousShot() {
            return false;
        }

        public boolean skipFocus() {
            return false;
        }

        // FD not support
        public boolean enableFD(Camera camera) {
            return false;
        }

        public boolean applySpecialCapture() {
            return false;
        }

        public void doOnPictureTaken() {
        }

        @Override
        public void onLeaveActor() {
            Log.d(TAG, "onLeaveActor");
            //when the alter dialog is showing ->suspend [not click the "ok" button]
            //and then wake up->capture less two pictures,will found not show the alter dialog
            //so when onLeaveAcotr,reset the statues
            mIsShowAlterDilogInProcess = false;
            if (mLongPressed) {
                mLongPressed = false;
                mSavingPictures = true;
                stopContinuousSound();
            }
            if (mSavingPictures) {
                // avoid for received onFocus(press = false) and cannot receive onCsshutdone
                if (mWaitSavingDoneThread == null || !mWaitSavingDoneThread.isAlive()) {
                    savingDoneThread();
                    mContext.getFileSaver().onContinousShotDone();
                    updateSavingHint(true, false);
                }
            } else {
                mCamera.restoreViewState();
            }
            mCamera.getCameraDevice().setMotionTrackCallback(null);
            Log.i(TAG, "end of setMotionTrackCallback to null");
            mShutterPressed = false;
            mCamera.setOrientation(false, OrientationEventListener.ORIENTATION_UNKNOWN);
            mCamera.getFocusManager().setNeedAutoFocus(true);
            overrideFocusMode(null);

        }
    }
}
