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

public class SmileActor extends SymbolActor {
    private static final String TAG = "SmileActor";
    
    private final ActorSmileCallback mSmileCallback = new ActorSmileCallback();
    public SmileActor(Camera context) {
        super(context);
        Log.i(TAG, "SmileActor initialize");
        mCameraCategory = new SmileCameraCategory();
    }

    @Override
    public int getMode() {
        return ModePicker.MODE_SMILE_SHOT;
    }

    private final class ActorSmileCallback implements SmileCallback {
        public void onSmile() {
            if (!isInShutterProgress()) {
            	Log.e(TAG, "Smile callback in error state, please check");
                return;
            }
            //when settings show, can not do capture(for User Experience)
            if (!mCameraClosed && (mCamera.getViewState() != Camera.VIEW_STATE_SETTING)) {
                mCamera.getShutterManager().performPhotoShutter();
                stopSymbolDetection();
            }
        }
    }

    @Override
    public void startSymbolDetection() {
    	Log.i(TAG, "start smile Detection");
    	mCamera.getCameraDevice().setSmileCallback(mSmileCallback);
        mCamera.getCameraDevice().startSDPreview();
    }
    
    @Override
    public void stopSymbolDetection() {
    	Log.i(TAG, "stop smile Detection");
    	mCamera.getCameraDevice().cancelSDPreview();
        mCamera.getCameraDevice().setSmileCallback(null);
        super.stopSymbolDetection();
    }

    @Override
    public void onShutterButtonLongPressed(ShutterButton button) {
        Log.i(TAG, "Smile.onShutterButtonLongPressed(" + button + ")");
        mCamera.showInfo(mCamera.getString(R.string.pref_camera_capturemode_entry_smileshot) +
                mCamera.getString(R.string.camera_continuous_not_supported));
    }

    class SmileCameraCategory extends SymbolActor.SymbolCameraCategory {
        @Override
        public void initializeFirstTime() {
            mCamera.showInfo(mCamera.getString(R.string.smileshot_guide_capture), Camera.SHOW_INFO_LENGTH_LONG);
            super.initializeFirstTime();
        }

        @Override
        public boolean supportContinuousShot() {
            return false;
        }

        @Override
        public boolean applySpecialCapture() {
            return false;
        }
    }
}
