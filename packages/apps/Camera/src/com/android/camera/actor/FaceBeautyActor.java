package com.android.camera.actor;

import java.util.List;
import android.content.pm.FeatureInfo;
import android.hardware.Camera.FBOriginalCallback;
import android.graphics.drawable.Drawable;
import android.hardware.Camera.CameraInfo;
import android.hardware.Camera.Parameters;
import android.hardware.Camera.GestureCallback;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Message;
import android.os.Vibrator;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.ImageSpan;

import com.android.camera.Camera;
import com.android.camera.CameraHolder;
import com.android.camera.CameraSettings;
import com.android.camera.FeatureSwitcher;
import com.android.camera.ListPreference;
import com.android.camera.Log;
import com.android.camera.R;
import com.android.camera.SaveRequest;
import com.android.camera.SettingChecker;
import com.android.camera.Storage;
import com.android.camera.manager.ModePicker;
import com.android.camera.manager.GestureShotViewManager;
import com.android.camera.ui.ShutterButton;
import com.android.camera.ui.ShutterButton.OnShutterButtonListener;


public class FaceBeautyActor extends PhotoActor {
    private static final String TAG = "FaceBeautyActor";
    private FBOriginalCallback mFBOriginalCallback = new FBCallback();

    private SaveRequest mOriginalSaveRequest;
    private boolean mGestureShotOpened;
    private Drawable mGestureDrawable;

    protected final InnerGestureCallback mGestureCallback = new InnerGestureCallback();
    private GestureShotViewManager mGestureShotViewManager;
    private static final int FLASH_COUNT = 5;
    private int mFlashCount = 0;
    private String mOriginalFlashMode;
    private boolean mNeedToFlash = false;
    private ToneGenerator mGestureTone;
    private static final int GESTURESHOT_STANDBY = 0;
    private static final int GESTURESHOT_INTERVAL = 1;
    private static final int GESTURESHOT_IN_PROGRESS = 2;
    private int mStatus = GESTURESHOT_STANDBY;
    private static final int GESTRUE_VOLUME = 200;
    private static final int GESTURE_SHOT_INTERAL = 1500;
    private boolean mActorIsLeaved = false;

    private Runnable mDoGestureSnapRunnable = new Runnable() {
        @Override
        public void run() {
            Log.i(TAG, "onShutterButtonClick(null), CameraState = " + mCamera.getCameraState() + "," +
            		"mStatus:" + mStatus + ",mActorIsLeaved:" + mActorIsLeaved);
            if (mStatus != GESTURESHOT_IN_PROGRESS
                    && mCamera.getCameraState() != Camera.STATE_SNAPSHOT_IN_PROGRESS) {
                mStatus = GESTURESHOT_STANDBY;
                if (!mActorIsLeaved) {
                    readyToCapture();
                }
            }
        }
    };

    public FaceBeautyActor(Camera context) {
        super(context);
        Log.i(TAG, "FaceBeautyActor initialize");
        mCameraCategory = new FaceBeautyCameraCategory();
        
        mGestureDrawable = context.getResources().getDrawable(R.drawable.ic_gesture_on);
        ListPreference preference = getContext().getListPreference(
                SettingChecker.ROW_SETTING_GESTURE_SHOT);
        if (preference != null) {
            String value = preference.getValue();
            mGestureShotOpened = value.equalsIgnoreCase("on") && FeatureSwitcher.isGestureShotSupport();
        }
    }

    @Override
    public int getMode() {
        return ModePicker.MODE_FACE_BEAUTY;
    }

    @Override
    public void onCameraParameterReady(boolean startPreview) {
        try {
            mGestureTone = new ToneGenerator(AudioManager.STREAM_SYSTEM, GESTRUE_VOLUME);
        } catch (Exception e) {
            // TODO: handle exception
            Log.e(TAG, "construct ToneGenerator failed");
            mGestureTone = null;
        }

        super.onCameraParameterReady(startPreview);
        mActorIsLeaved = false;
        if (!mGestureShotOpened) {
            mHandler.removeCallbacks(mDoGestureSnapRunnable);
        } else {
            postGestureSnapRunnable();
        }
        Log.i(TAG, "FaceBeautyActor onCameraParameterReady");
    }

    private void postGestureSnapRunnable() {
        if (GESTURESHOT_IN_PROGRESS != mStatus 
                && !mHandler.hasCallbacks(mDoGestureSnapRunnable)) {
            mHandler.post(mDoGestureSnapRunnable);
        }
    }
    
    public void enterGestureShotMode() {
        Log.i(TAG, "enterGestureShotMode");
        mGestureShotOpened = true;
        ((FaceBeautyCameraCategory) mCameraCategory).showCaptureGuide();
        startGestureShot();
    }

    public void exitGestureShotMode() {
        Log.i(TAG, "exitGestureShotMode");
        mGestureShotOpened = false;
        stopGestureShot();
    }
    @Override
    public boolean readyToCapture() {
        Log.i(TAG, " readyToCapture? mStatus = " + String.valueOf(mStatus));
        if (mGestureShotOpened && mStatus == GESTURESHOT_STANDBY) {
            startGestureShot();
            return false;
        }
        return true;
    }
    
    @Override
    public boolean doSymbolShutter() {
        Log.i(TAG, "doSymbolShutter mStatus = " + String.valueOf(mStatus));
        if (mStatus == GESTURESHOT_IN_PROGRESS) {
            // already in gesture shutter mode, capture directly.
            stopGestureShot();
            capture();
            return true;
        }
        return false;
    }

    private void startGestureShot() {
        Log.i(TAG, "startGestureShot()");
        if (mCamera.getCameraDevice() != null) {
            mCamera.getCameraDevice().setGestureCallback(mGestureCallback);
            mCamera.getCameraDevice().startGDPreview();
            mStatus = GESTURESHOT_IN_PROGRESS;
        }
    }
    
    private void stopGestureShot() {
        Log.i(TAG, "stopGestureShot()");
        mCountDowning = false;
        if (mCamera.getCameraDevice() != null) {
            mCamera.getCameraDevice().setGestureCallback(null);
            mCamera.getCameraDevice().cancelGDPreview();
            mStatus = GESTURESHOT_STANDBY;
        }
    }

    private final class InnerGestureCallback implements GestureCallback {
        public void onGesture() {
            Log.i(TAG, "onGesture(), mCountDowning:" + mCountDowning);
            if (GESTURESHOT_IN_PROGRESS != mStatus) {
                Log.e(TAG, "gesture callback in error state, please check");
                return;
            }

            if (mCountDowning || mSelfTimerManager.isSelfTimerCounting()) {
                return;
            } 
            // when settings show, can not do capture(for User Experience)
            if (!mCameraClosed
                    && (mCamera.getViewState() != Camera.VIEW_STATE_SETTING)) {
                Vibrator vibrator = mCamera.getVibrator();
                vibrator.vibrate(new long[] { 0, 50, 50, 100, 50 }, -1);
                if (mGestureTone != null) {
                    mGestureTone.startTone(ToneGenerator.TONE_DTMF_9,
                            GESTRUE_VOLUME);
                }
                
                if (mSelfTimerManager.isSelfTimerEnabled()) {
                    onShutterButtonClick(null);
                } else {
                    Parameters parameters = mCamera.getParameters();
                    mOriginalFlashMode = parameters.getFlashMode();
                    List<String> supported = parameters.getSupportedFlashModes();
                    Log.i(TAG, "supported:" + supported + ", mOriginalFlashMode:"
                            + mOriginalFlashMode);
                    if (supported != null
                            && supported.contains(Parameters.FLASH_MODE_TORCH)) {
                        parameters.setFlashMode(Parameters.FLASH_MODE_TORCH);
                        mCamera.applyParametersToServer();
                        mNeedToFlash = true;
                    }
                    
                    mGestureShotViewManager = new GestureShotViewManager(mCamera);
                    mGestureShotViewManager.show();
                    mCamera.setViewState(Camera.VIEW_STATE_CAPTURE);
                    mCamera.hideAllViews();
                    mCamera.setSwipingEnabled(false);
                    countDownCapture(2);
                }
            }
        }
    }

    protected void countDownCapture(int countDown) {
        Log.i(TAG, "countDownCapture(), countDown:" + countDown);
        if (GESTURESHOT_STANDBY == mStatus) {
            mGestureShotViewManager.hide();
            mCamera.showAllViews();
            mCamera.restoreViewState();
            return;
        }

        if (countDown == 1 && mNeedToFlash) {
            turnOnOffFlash();
        }
        
        if (countDown > 0) {
            mCountDowning = true;
            mGestureShotViewManager.setCountSeconds(String.valueOf(countDown));
            mGestureShotViewManager.startAnimation();
            countDown--;
            Message msg = mHandler.obtainMessage(COUNT_DOWN_CAPTURE, countDown);
            mHandler.sendMessageDelayed(msg, 1000);
        } else if (countDown == 0) {
            mGestureShotViewManager.setCountSeconds(String.valueOf(countDown));
            mGestureShotViewManager.endAnimation();
            mGestureShotViewManager.hide();
            mCamera.showAllViews();
            onShutterButtonClick(null);
            stopGestureShot();
        }
    }

    protected void turnOnOffFlash() {
        Parameters parameters = mCamera.getParameters();
        Log.i(TAG, "turnOnOffFlash(), flash:" + parameters.getFlashMode()
                + ", mFlashCount:" + mFlashCount);
        if (parameters.getFlashMode().equals(Parameters.FLASH_MODE_TORCH)) {
            parameters.setFlashMode(Parameters.FLASH_MODE_OFF);
        } else {
            parameters.setFlashMode(Parameters.FLASH_MODE_TORCH);
        }
        mFlashCount++;
        if (mFlashCount < FLASH_COUNT) {
            Message msg = mHandler.obtainMessage(TURN_ON_OFF_FLASH);
            mHandler.sendMessageDelayed(msg, 100);
        } else {
            parameters.setFlashMode(mOriginalFlashMode);
            mFlashCount = 0;
            mNeedToFlash = false;
        }
        mCamera.applyParametersToServer();
    }

    @Override
    public OnShutterButtonListener getPhotoShutterButtonListener() {
        return this;
    }

    public void initializeView(boolean isOtStarted) {
        super.initializeView(isOtStarted);
        mCamera.getFrameManager().enableFaceBeauty(true);
    }

    @Override
    public void onShutterButtonLongPressed(ShutterButton button) {
        Log.i(TAG, "FaceBeauty.onShutterButtonLongPressed(" + button + ")");
        mCamera.showInfo(mCamera.getString(R.string.pref_camera_capturemode_enrty_fb) +
                mCamera.getString(R.string.camera_continuous_not_supported));
    }
    @Override
    public boolean capture() {
    	mCamera.getCameraDevice().setFBOriginalCallback(mFBOriginalCallback);
    	super.capture();
    	return true;
    }

    public class FBCallback implements FBOriginalCallback {
    	
    	public void onCapture(byte[] originalJpegData) {
    		
    		if (mOriginalSaveRequest == null || originalJpegData == null) {
    			return;
    		}
    		
    		mOriginalSaveRequest.setData(originalJpegData);
    		mOriginalSaveRequest.addRequest();
    	}
    }

    @Override
    public void release() {
        super.release();

        // font sensor will not support fd in other mode
        CameraInfo info = CameraHolder.instance().getCameraInfo()[mCamera
                .getCameraId()];
        if (info.facing == CameraInfo.CAMERA_FACING_FRONT) {
            stopFaceDetection();
        }
        
        mCamera.removeOnFullScreenChangedListener(mFullScreenChangedListener);
        mCameraCategory.doCancelCapture();
    }
     
    private Camera.OnFullScreenChangedListener mFullScreenChangedListener = new Camera.OnFullScreenChangedListener() {

        @Override
        public void onFullScreenChanged(boolean full) {
            if (!mGestureShotOpened) {
                return;
            }
            if (!full) {
                mHandler.removeCallbacks(mDoGestureSnapRunnable);
                stopGestureShot();
            } else if (mStatus != GESTURESHOT_IN_PROGRESS
                    && !mHandler.hasCallbacks(mDoGestureSnapRunnable)) {
                mHandler.post(mDoGestureSnapRunnable);
            }
        }
    };
    
    class FaceBeautyCameraCategory extends CameraCategory {
        @Override
        public void initializeFirstTime() {
            // make sure fs is started
            if (!sFaceDetectionStarted) {
                startFaceDetection();
            }
            if (mGestureShotOpened) {
                showCaptureGuide();
            }
            mCamera.addOnFullScreenChangedListener(mFullScreenChangedListener);
        }

        public void showCaptureGuide() {
            String guideString = mCamera
                    .getString(R.string.gestureshot_guide_capture);
            mGestureDrawable.setBounds(0, 0,
                    mGestureDrawable.getIntrinsicWidth(),
                    mGestureDrawable.getIntrinsicHeight());
            ImageSpan span = new ImageSpan(mGestureDrawable,
                    ImageSpan.ALIGN_BASELINE);
            SpannableString spanStr = new SpannableString(guideString + "1");
            spanStr.setSpan(span, spanStr.length() - 1, spanStr.length(),
                    Spannable.SPAN_INCLUSIVE_EXCLUSIVE);
            mCamera.showInfo(spanStr, Camera.SHOW_INFO_LENGTH_LONG);
        }

        @Override
        public boolean supportContinuousShot() {
            return false;
        }

        @Override
        public void ensureCaptureTempPath() {
            if (FeatureSwitcher.isFaceBeautyOriginalPictureSaved()) {
                mOriginalSaveRequest = mContext.preparePhotoRequest(Storage.FILE_TYPE_PANO, Storage.PICTURE_TYPE_JPG);
            } else {
                mOriginalSaveRequest = null;
            }
        }

        @Override
        public boolean applySpecialCapture() {
            return false;
        }

        @Override
        public void doOnPictureTaken() {
            Log.i(TAG, "FaceBeauty.doOnPictureTaken");
            if (mGestureShotOpened && mCamera.isFullScreen()) {
                if (mHandler.hasCallbacks(mDoGestureSnapRunnable)) {
                    mHandler.removeCallbacks(mDoGestureSnapRunnable);
                }
                
                mHandler.postDelayed(mDoGestureSnapRunnable, GESTURE_SHOT_INTERAL);
                mStatus = GESTURESHOT_INTERVAL;
            }
            // add animation
            super.animateCapture(mCamera);
        }
        
        @Override
        public boolean doCancelCapture() {
            // TODO Auto-generated method stub
            if (mCamera.getCameraDevice() == null) {
                return false;
            }
            
            if (GESTURESHOT_IN_PROGRESS == mStatus) {
                stopGestureShot();
            } else {
                mStatus = GESTURESHOT_STANDBY;
            }
            return false;
        }
        
        @Override
        public void animateCapture(Camera camera) { }

        @Override
        public boolean enableFD(Camera camera) {
            return true;
        }

        @Override
        public void onLeaveActor() {
            super.onLeaveActor();
            mActorIsLeaved = true;
            mCamera.getFrameManager().enableFaceBeauty(false);
            updateSavingHint(false, false);
            
            if (mGestureShotOpened) {
                mHandler.removeCallbacks(mDoGestureSnapRunnable);
                if (GESTURESHOT_IN_PROGRESS == mStatus) {
                    stopGestureShot();
                }
            }
	    
            if (mGestureTone != null) {
        	    mGestureTone.release();
        	    mGestureTone = null;
        	}
        }
    }
}
