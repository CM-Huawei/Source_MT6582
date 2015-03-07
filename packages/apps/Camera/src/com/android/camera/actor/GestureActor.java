package com.android.camera.actor;

import java.util.List;
import android.graphics.drawable.Drawable;
import android.hardware.Camera.CameraInfo;
import android.hardware.Camera.GestureCallback;
import android.hardware.Camera.Parameters;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Message;
import android.os.Vibrator;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.ImageSpan;

import com.android.camera.Camera;
import com.android.camera.CameraHolder;
import com.android.camera.Log;
import com.android.camera.R;
import com.android.camera.SaveRequest;
import com.android.camera.manager.GestureShotViewManager;
import com.android.camera.manager.ModePicker;
import com.android.camera.ui.ShutterButton;
import com.android.camera.ui.ShutterButton.OnShutterButtonListener;

public class GestureActor extends SymbolActor {
    private static final String TAG = "GestureActor";
    
    protected final ActorGestureCallback mGestureCallback = new ActorGestureCallback();
    private GestureShotViewManager mGestureShotViewManager;
    private Drawable mGestureDrawable;
    private static final int FLASH_COUNT = 5;
    private int mFlashCount = 0;
    private String mOriginalFlashMode;
    private boolean mNeedToFlash = false;
    private ToneGenerator mGestureTone;
    private static final int GESTRUE_VOLUME = 200;

    public GestureActor(Camera context) {
        super(context);
        Log.i(TAG, "GestureActor initialize");
        mCameraCategory = new GestureCameraCategory();
        mGestureDrawable = context.getResources().getDrawable(R.drawable.ic_gesture_on);
    }

    @Override
    public int getMode() {
        return ModePicker.MODE_GESTURE_SHOT;
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
    }

    private final class ActorGestureCallback implements GestureCallback {
        public void onGesture() {
        	Log.i(TAG, "onGesture(), mCountDowning:" + mCountDowning);
            if (!isInShutterProgress()) {
                Log.e(TAG, "gesture callback in error state, please check");
                return;
            }

            if (mCountDowning || mSelfTimerManager.isSelfTimerCounting()) {
            	return;
            }
            //when settings show, can not do capture(for User Experience)
            if (!mCameraClosed && (mCamera.getViewState() != Camera.VIEW_STATE_SETTING)) {
                Vibrator vibrator = mCamera.getVibrator();
                vibrator.vibrate(new long[]{0, 50, 50, 100, 50}, -1);
                if (mGestureTone != null) {
                    mGestureTone.startTone(ToneGenerator.TONE_DTMF_9, GESTRUE_VOLUME);
                }

            	if (mSelfTimerManager.isSelfTimerEnabled()) {
            	    onShutterButtonClick(null);
            	} else {
            	    Parameters parameters = mCamera.getParameters();
                    mOriginalFlashMode = parameters.getFlashMode();
                    List<String> supported = parameters.getSupportedFlashModes();
                    Log.i(TAG, "supported:" + supported + ", mOriginalFlashMode:" + mOriginalFlashMode);
                    if (supported != null && supported.contains(Parameters.FLASH_MODE_TORCH)) {
                        parameters.setFlashMode(Parameters.FLASH_MODE_TORCH);
                        mCamera.applyParametersToServer();
                        mNeedToFlash = true;
                    }
                    
            	    mGestureShotViewManager = new GestureShotViewManager(mCamera);
                    mGestureShotViewManager.show();
                    mCamera.hideAllViews();
                    mCamera.setViewState(Camera.VIEW_STATE_CAPTURE);
                    mCamera.setSwipingEnabled(false);
                    countDownCapture(2);
            	}
            	
            }
        }
    }
    
    protected void countDownCapture(int countDown) {
    	Log.i(TAG, "countDownCapture(), countDown:" + countDown);
    	if (isInStandby()) {
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
    	} else if (countDown == 0){
    		mGestureShotViewManager.setCountSeconds(String.valueOf(countDown));
    		mGestureShotViewManager.endAnimation();
    		mGestureShotViewManager.hide();
    		mCamera.showAllViews();
    		onShutterButtonClick(null);
    		stopSymbolDetection();
    	}
    }
    
    protected void turnOnOffFlash() {
    	Parameters parameters = mCamera.getParameters();
    	Log.i(TAG, "turnOnOffFlash(), flash:" + parameters.getFlashMode() + ", mFlashCount:" + mFlashCount);
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
    public void startSymbolDetection() {
    	Log.i(TAG, "start gesture detection");
    	mCamera.getCameraDevice().setGestureCallback(mGestureCallback);
    	mCamera.getCameraDevice().startGDPreview();
    }
    
    @Override
    public void stopSymbolDetection() {
    	Log.i(TAG, "stop gesture detection");
    	mCountDowning = false;
    	if (mCamera.getCameraDevice() != null) {
    		mCamera.getCameraDevice().setGestureCallback(null);
        	mCamera.getCameraDevice().cancelGDPreview();
    	}
    	super.stopSymbolDetection();
    }

    @Override
    public void onShutterButtonLongPressed(ShutterButton button) {
        Log.i(TAG, "gesture.onShutterButtonLongPressed(" + button + ")");
        mCamera.showInfo(mCamera.getString(R.string.pref_camera_capturemode_entry_gestureshot) +
                mCamera.getString(R.string.camera_continuous_not_supported));
    }

    class GestureCameraCategory extends SymbolActor.SymbolCameraCategory {
        @Override
        public void initializeFirstTime() {
        	showCaptureGuide();
            super.initializeFirstTime();
        }
        
        private void showCaptureGuide() {
        	String guideString = mCamera.getString(R.string.gestureshot_guide_capture);
        	mGestureDrawable.setBounds(0, 0, mGestureDrawable.getIntrinsicWidth(), mGestureDrawable.getIntrinsicHeight());
    		ImageSpan span = new ImageSpan(mGestureDrawable, ImageSpan.ALIGN_BASELINE);
    		SpannableString spanStr = new SpannableString(guideString + "1");
    		spanStr.setSpan(span, spanStr.length()-1, spanStr.length(), Spannable.SPAN_INCLUSIVE_EXCLUSIVE);
            mCamera.showInfo(spanStr, Camera.SHOW_INFO_LENGTH_LONG);
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
        	super.doOnPictureTaken();
        }
        
        @Override
        public void onLeaveActor() {
        	super.onLeaveActor();
        	if (mGestureShotViewManager != null) {
        		mGestureShotViewManager.hide();
        	}
        	
        	if (mGestureTone != null) {
        	    mGestureTone.release();
        	    mGestureTone = null;
        	}
        }
    }
}
