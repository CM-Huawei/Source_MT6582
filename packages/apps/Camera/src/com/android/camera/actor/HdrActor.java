package com.android.camera.actor;

import android.hardware.Camera.HDROriginalCallback;
import com.android.camera.Camera;
import com.android.camera.FeatureSwitcher;
import com.android.camera.Log;
import com.android.camera.R;
import com.android.camera.SaveRequest;
import com.android.camera.Storage;
import com.android.camera.manager.ModePicker;
import com.android.camera.ui.ShutterButton;
import com.android.camera.ui.ShutterButton.OnShutterButtonListener;

public class HdrActor extends PhotoActor {
    private static final String TAG = "HdrActor";
    private HDROriginalCallback mHDROriginalCallback = new HDRCallback();
    private SaveRequest mOriginalSaveRequest;
    private byte[] mRawImageBuffer;

    public HdrActor(Camera context) {
        super(context);
        Log.i(TAG, "HdrActor initialize");
        mCameraCategory = new HdrCameraCategory();
    }

    @Override
    public int getMode() {
        return ModePicker.MODE_HDR;
    }

    @Override
    public void onCameraOpenDone() {
        super.onCameraOpenDone();
    }

    @Override
    public void onCameraParameterReady(boolean startPreview) {
        super.onCameraParameterReady(startPreview);
        Log.i(TAG, "HdrActor onCameraParameterReady");
    }

    @Override
    public OnShutterButtonListener getPhotoShutterButtonListener() {
        return this;
    }

    @Override
    public void onShutterButtonLongPressed(ShutterButton button) {
        Log.i(TAG, "Hdr.onShutterButtonLongPressed(" + button + ")");
        mCamera.showInfo(mCamera.getString(R.string.pref_camera_hdr_title) +
                mCamera.getString(R.string.camera_continuous_not_supported));
    }
    @Override
    public boolean capture() {
    	Log.i(TAG, "capture()");
    	mCamera.getCameraDevice().setHDROriginalCallback(mHDROriginalCallback);
    	super.capture();
    	return true;
    }
    
    public class HDRCallback implements HDROriginalCallback {
    	public void onCapture(byte[] originalJpegData) {
    		Log.i(TAG, "onCapture originalJpegData:" + originalJpegData.length);
    		if (mOriginalSaveRequest == null || originalJpegData == null) {
                return;
            }
    		
    		mOriginalSaveRequest.setData(originalJpegData);
            mOriginalSaveRequest.addRequest();
    	}
    }
    
    class HdrCameraCategory extends CameraCategory {
        public void initializeFirstTime() {
            mCamera.showInfo(mCamera.getString(R.string.hdr_guide_capture), Camera.SHOW_INFO_LENGTH_LONG);
        }

        @Override
        public boolean supportContinuousShot() {
            return false;
        }

        public void ensureCaptureTempPath() {
            if (FeatureSwitcher.isHdrOriginalPictureSaved()) {
                mOriginalSaveRequest = mContext.preparePhotoRequest(Storage.FILE_TYPE_PANO, Storage.PICTURE_TYPE_JPG);
            } else {
                mOriginalSaveRequest = null;
            }
        }

        public boolean applySpecialCapture() {
            return false;
        }

        public void doOnPictureTaken() {
            Log.i(TAG, "Hdr.doOnPictureTaken");
            // add animation
            super.animateCapture(mCamera);
            
        }

        @Override
        public void animateCapture(Camera camera) { }

        @Override
        public void onLeaveActor() {
            Log.i(TAG, "HDR.onLeaveActor");
            mCamera.restoreViewState();
        }
    }
}
