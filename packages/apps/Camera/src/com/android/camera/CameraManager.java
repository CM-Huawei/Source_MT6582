/*
 * Copyright (C) 2012 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.camera;

import android.graphics.SurfaceTexture;
import android.hardware.Camera.ASDCallback;
import android.hardware.Camera.AUTORAMACallback;
import android.hardware.Camera.AUTORAMAMVCallback;
import android.hardware.Camera.AutoFocusCallback;
import android.hardware.Camera.AutoFocusMoveCallback;
import android.hardware.Camera.ContinuousShotDone;
import android.hardware.Camera.ErrorCallback;
import android.hardware.Camera.FaceDetectionListener;
import android.hardware.Camera.FBOriginalCallback;
import android.hardware.Camera.HDROriginalCallback;
import android.hardware.Camera.GestureCallback;
import android.hardware.Camera.MAVCallback;
import android.hardware.Camera.MotionTrackCallback;
import android.hardware.Camera.OnZoomChangeListener;
import android.hardware.Camera.ObjectTrackingListener;
import android.hardware.Camera.Parameters;
import android.hardware.Camera.PictureCallback;
import android.hardware.Camera.PreviewCallback;
import android.hardware.Camera.ShutterCallback;
import android.hardware.Camera.SmileCallback;
import android.hardware.Camera.ZSDPreviewDone;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;

import static com.android.camera.Util.assertError;
import com.android.camera.manager.MMProfileManager;
import com.mediatek.camera.FrameworksClassFactory;
import com.mediatek.camera.ICamera;

import java.io.IOException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

public class CameraManager {
    private static final String TAG = "CameraManager";
    private static CameraManager sCameraManager = new CameraManager();

    private Parameters mParameters;
    private boolean mParametersIsDirty;
    private IOException mReconnectException;

    private static final int RELEASE = 1;
    private static final int RECONNECT = 2;
    private static final int UNLOCK = 3;
    private static final int LOCK = 4;
    private static final int SET_PREVIEW_TEXTURE_ASYNC = 5;
    private static final int START_PREVIEW_ASYNC = 6;
    private static final int STOP_PREVIEW = 7;
    private static final int SET_PREVIEW_CALLBACK_WITH_BUFFER = 8;
    private static final int ADD_CALLBACK_BUFFER = 9;
    private static final int AUTO_FOCUS = 10;
    private static final int CANCEL_AUTO_FOCUS = 11;
    private static final int SET_AUTO_FOCUS_MOVE_CALLBACK = 12;
    private static final int SET_DISPLAY_ORIENTATION = 13;
    private static final int SET_ZOOM_CHANGE_LISTENER = 14;
    private static final int SET_FACE_DETECTION_LISTENER = 15;
    private static final int START_FACE_DETECTION = 16;
    private static final int STOP_FACE_DETECTION = 17;
    private static final int SET_ERROR_CALLBACK = 18;
    private static final int SET_PARAMETERS = 19;
    private static final int GET_PARAMETERS = 20;
    private static final int SET_PARAMETERS_ASYNC = 21;
    private static final int SET_HDR_ORIGINAL_CALLBACK = 23;
    private static final int SET_FB_ORIGINAL_CALLBACK = 24;
    private static final int START_OBJECT_TRACKING = 25;
    private static final int STOP_OBJECT_TRACKING = 26;
    private static final int SET_OBJECT_TRACKING_LISTENER = 27;
    private static final int SET_GESTURE_CALLBACK = 28;
    private static final int START_GD_PREVIEW = 29;
    private static final int CANCEL_GD_PREVIEW = 30;
    ///M: JB migration start @{
    private static final int START_SMOOTH_ZOOM = 100;
    private static final int SET_AUTORAMA_CALLBACK = 101;
    private static final int SET_AUTORAMA_MV_CALLBACK = 102;
    private static final int START_AUTORAMA = 103;
    private static final int STOP_AUTORAMA = 104;
    private static final int SET_MAV_CALLBACK = 105;
    private static final int START_MAV = 106;
    private static final int STOP_MAV = 107;
    private static final int SET_ASD_CALLBACK = 108;
    private static final int SET_SMILE_CALLBACK = 109;
    private static final int START_SD_PREVIEW = 110;
    private static final int CANCEL_SD_PREVIEW = 111;
    private static final int CANCEL_CONTINUOUS_SHOT = 112;
    private static final int SET_CONTINUOUS_SHOT_SPEED = 113;
    private static final int SET_PREVIEW_DONE_CALLBACK = 114;
    private static final int SET_CSHOT_DONE_CALLBACK = 115;
    private static final int ADD_RAW_IMAGE_CALLBACK_BUFFER = 116;
    /// @}
    private static final int SET_STEREO3D_MODE = 117;
    private static final int START_3D_SHOT = 118;
    private static final int STOP_3D_SHOT = 119;
    private static final int START_MOTION_TRACK = 120;
    private static final int STOP_MOTION_TRACK = 121;
    //
    private static final int SET_CONTINUOUS_SHOT_STATE = 122;
    private static final int SET_MOTIONTRACK_CALLBACK = 123;
    /// @}

    private Handler mCameraHandler;
    private CameraProxy mCameraProxy;
    private ICamera mCamera;
    
    // Used to retain a copy of Parameters for setting parameters.
    private Parameters mParamsToSet;

    public static CameraManager instance() {
        return sCameraManager;
    }

    private CameraManager() {
        HandlerThread ht = new HandlerThread("Camera Handler Thread");
        ht.start();
        mCameraHandler = new CameraHandler(ht.getLooper());
    }

    private class CameraHandler extends Handler {
        CameraHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(final Message msg) {
            Log.d(TAG, "handleMessage(" + msg + ")");
            try {
                switch (msg.what) {
                    case RELEASE:
                        MMProfileManager.startProfileCameraRelease();
                        mCamera.release();
                        MMProfileManager.stopProfileCameraRelease();
                        mCamera = null;
                        mCameraProxy = null;
                        return;

                    case RECONNECT:
                        mReconnectException = null;
                        try {
                            mCamera.reconnect();
                        } catch (IOException ex) {
                            mReconnectException = ex;
                        }
                        return;

                    case UNLOCK:
                        mCamera.unlock();
                        return;

                    case LOCK:
                        mCamera.lock();
                        return;

                    case SET_PREVIEW_TEXTURE_ASYNC:
                        try {
                            mCamera.setPreviewTexture((SurfaceTexture) msg.obj);
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                        return;  // no need to call mSig.open()

                    case START_PREVIEW_ASYNC:
                        MMProfileManager.startProfileStartPreview();
                        mCamera.startPreview();
                        MMProfileManager.stopProfileStartPreview();
                        return;  // no need to call mSig.open()

                    case STOP_PREVIEW:
                        MMProfileManager.startProfileStopPreview();
                        mCamera.stopPreview();
                        MMProfileManager.stopProfileStopPreview();
                        return;

                    case SET_PREVIEW_CALLBACK_WITH_BUFFER:
                        mCamera.setPreviewCallbackWithBuffer(
                            (PreviewCallback) msg.obj);
                        return;

                    case ADD_CALLBACK_BUFFER:
                        mCamera.addCallbackBuffer((byte[]) msg.obj);
                        return;

                    case AUTO_FOCUS:
                        mCamera.autoFocus((AutoFocusCallback) msg.obj);
                        return;

                    case CANCEL_AUTO_FOCUS:
                        mCamera.cancelAutoFocus();
                        return;

                    case SET_AUTO_FOCUS_MOVE_CALLBACK:
                        mCamera.setAutoFocusMoveCallback(
                            (AutoFocusMoveCallback) msg.obj);
                        return;

                    case SET_DISPLAY_ORIENTATION:
                        mCamera.setDisplayOrientation(msg.arg1);
                        return;

                    case SET_ZOOM_CHANGE_LISTENER:
                        mCamera.setZoomChangeListener(
                            (OnZoomChangeListener) msg.obj);
                        return;

                    case SET_FACE_DETECTION_LISTENER:
                        mCamera.setFaceDetectionListener(
                            (FaceDetectionListener) msg.obj);
                        return;

                    case START_FACE_DETECTION:
                        mCamera.startFaceDetection();
                        return;

                    case STOP_FACE_DETECTION:
                        mCamera.stopFaceDetection();
                        return;

                    case SET_ERROR_CALLBACK:
                        mCamera.setErrorCallback((ErrorCallback) msg.obj);
                        return;

                    case SET_PARAMETERS:
                        MMProfileManager.startProfileSetParameters();
                        mParametersIsDirty = true;
                        mParamsToSet.unflatten((String) msg.obj);
                        mCamera.setParameters(mParamsToSet);
                        MMProfileManager.stopProfileSetParameters();
                        return;

                    case GET_PARAMETERS:
                        MMProfileManager.startProfileGetParameters();
                        if (mParametersIsDirty) {
                            mParameters = mCamera.getParameters();
                            mParametersIsDirty = false;
                        }
                        MMProfileManager.stopProfileGetParameters();
                        return;

                    case SET_PARAMETERS_ASYNC:
                        MMProfileManager.startProfileSetParameters();
                        mParametersIsDirty = true;
                        mParamsToSet.unflatten((String) msg.obj);
                        mCamera.setParameters(mParamsToSet);
                        MMProfileManager.stopProfileSetParameters();
                        return;  // no need to call mSig.open()

                    case START_SMOOTH_ZOOM:
                        mCamera.startSmoothZoom(msg.arg1);
                        return;
                    case SET_AUTORAMA_CALLBACK:
                        mCamera.setAUTORAMACallback((AUTORAMACallback)msg.obj);
                        return;
                    case SET_AUTORAMA_MV_CALLBACK:
                        mCamera.setAUTORAMAMVCallback((AUTORAMAMVCallback)msg.obj);
                        return;
                    case START_AUTORAMA:
                        mCamera.startAUTORAMA(msg.arg1);
                        return;
                    case STOP_AUTORAMA:
                        mCamera.stopAUTORAMA(msg.arg1);
                        return;
                    case SET_MAV_CALLBACK:
                        mCamera.setMAVCallback((MAVCallback)msg.obj);
                        return;
                    case START_MAV:
                        mCamera.startMAV(msg.arg1);
                        return;
                    case STOP_MAV:
                        mCamera.stopMAV(msg.arg1);
                        return;
                    case SET_ASD_CALLBACK:
                        mCamera.setASDCallback((ASDCallback)msg.obj);
                        return;
                    case SET_SMILE_CALLBACK:
                        mCamera.setSmileCallback((SmileCallback)msg.obj);
                        return;
                    case START_SD_PREVIEW:
                        mCamera.startSDPreview();
                        return;
                    case CANCEL_SD_PREVIEW:
                        mCamera.cancelSDPreview();
                        return;
                    case CANCEL_CONTINUOUS_SHOT:
                        mCamera.cancelContinuousShot();
                        return;
                    case SET_CONTINUOUS_SHOT_SPEED:
                        mCamera.setContinuousShotSpeed(msg.arg1);
                        return;
                    case SET_PREVIEW_DONE_CALLBACK:
                        mCamera.setPreviewDoneCallback((ZSDPreviewDone)msg.obj);
                        return;
                    case SET_CSHOT_DONE_CALLBACK:
                        mCamera.setCSDoneCallback((ContinuousShotDone)msg.obj);
                        break;
                    case ADD_RAW_IMAGE_CALLBACK_BUFFER:
                        mCamera.addRawImageCallbackBuffer((byte[]) msg.obj);
                        return;
                    case START_OBJECT_TRACKING:
                        mCamera.startOT(msg.arg1, msg.arg2);
                        return;
                    case STOP_OBJECT_TRACKING:
                        mCamera.stopOT();
                        return;
                    case SET_OBJECT_TRACKING_LISTENER:
                        mCamera.setObjectTrackingListener((ObjectTrackingListener)msg.obj);
                        return;

                    case SET_STEREO3D_MODE:
                        mCamera.setPreview3DModeForCamera(((Boolean)msg.obj).booleanValue());
                       return;
                    case START_3D_SHOT:
                        mCamera.start3DSHOT(msg.arg1);
                        return;
                    case STOP_3D_SHOT:
                        mCamera.stop3DSHOT(msg.arg1);
                       return;
                    case SET_HDR_ORIGINAL_CALLBACK:
                    	mCamera.setHDROriginalCallback((HDROriginalCallback)msg.obj);
                    	return;
                    case SET_FB_ORIGINAL_CALLBACK:
                    	mCamera.setFBOriginalCallback((FBOriginalCallback)msg.obj);
                    	return;
                    case START_MOTION_TRACK:
                        mCamera.startMotionTrack(msg.arg1);
                    	return;
                    case STOP_MOTION_TRACK:
                        mCamera.stopMotionTrack();
                    	return;
                    case SET_MOTIONTRACK_CALLBACK:
                    	mCamera.setMotionTrackCallback((MotionTrackCallback)msg.obj);
                    	return;
                    case SET_GESTURE_CALLBACK:
                    	mCamera.setGestureCallback((GestureCallback)msg.obj);
                    	return;
                    case START_GD_PREVIEW:
                    	mCamera.startGDPreview();
                    	return;
                    case CANCEL_GD_PREVIEW:
                    	mCamera.cancelGDPreview();
                    	return;
                    default:
                        throw new RuntimeException("Invalid CameraProxy message=" + msg.what);
                }
            } catch (RuntimeException e) {
                if (msg.what != RELEASE && mCamera != null) {
                    try {
                        mCamera.release();
                    } catch (Exception ex) {
                        Log.e(TAG, "Fail to release the camera.");
                    }
                    mCamera = null;
                    mCameraProxy = null;
                }
                throw e;
            }
        }
    }

    // Open camera synchronously. This method is invoked in the context of a
    // background thread.
    CameraProxy cameraOpen(int cameraId) {
        // Cannot open camera in mCameraHandler, otherwise all camera events
        // will be routed to mCameraHandler looper, which in turn will call
        // event handler like Camera.onFaceDetection, which in turn will modify
        // UI and cause exception like this:
        // CalledFromWrongThreadException: Only the original thread that created
        // a view hierarchy can touch its views.
        MMProfileManager.startProfileCameraOpen();
        mCamera = FrameworksClassFactory.openCamera(cameraId);
        MMProfileManager.stopProfileCameraOpen();
        if (mCamera != null) {
            mParametersIsDirty = true;
            if (mParamsToSet == null) {
                mParamsToSet = mCamera.getParameters();
            }
            mCameraProxy = new CameraProxy();
            return mCameraProxy;
        } else {
            return null;
        }
    }

    public class CameraProxy {
        private CameraProxy() {
            assertError(mCamera != null);
        }

        public ICamera getCamera() {
            return mCamera;
        }

        public void release() {
            mCameraHandler.sendEmptyMessage(RELEASE);
            waitDone();
        }

        public void reconnect() throws IOException {
            mCameraHandler.sendEmptyMessage(RECONNECT);
            waitDone();
            if (mReconnectException != null) {
                throw mReconnectException;
            }
        }

        public void unlock() {
            mCameraHandler.sendEmptyMessage(UNLOCK);
            waitDone();
        }

        public void lock() {
            mCameraHandler.sendEmptyMessage(LOCK);
            waitDone();
        }

        public void setPreviewTextureAsync(final SurfaceTexture surfaceTexture) {
            mCameraHandler.obtainMessage(SET_PREVIEW_TEXTURE_ASYNC, surfaceTexture).sendToTarget();
        }

        public void startPreviewAsync() {
            mCameraHandler.sendEmptyMessage(START_PREVIEW_ASYNC);
        }

        public void stopPreview() {
            mCameraHandler.sendEmptyMessage(STOP_PREVIEW);
            waitDone();
        }

        public void setPreviewCallbackWithBuffer(final PreviewCallback cb) {
            mCameraHandler.obtainMessage(SET_PREVIEW_CALLBACK_WITH_BUFFER, cb).sendToTarget();
        }

        public void addCallbackBuffer(byte[] callbackBuffer) {
            mCameraHandler.obtainMessage(ADD_CALLBACK_BUFFER, callbackBuffer).sendToTarget();
        }

        public void addRawImageCallbackBuffer(byte[] callbackBuffer) {
            mCameraHandler.obtainMessage(ADD_RAW_IMAGE_CALLBACK_BUFFER, callbackBuffer).sendToTarget();
        }

        public void autoFocus(AutoFocusCallback cb) {
            mCameraHandler.obtainMessage(AUTO_FOCUS, cb).sendToTarget();
        }

        public void cancelAutoFocus() {
        	mCameraHandler.removeMessages(AUTO_FOCUS);
            mCameraHandler.sendEmptyMessage(CANCEL_AUTO_FOCUS);
        }

        public void setAutoFocusMoveCallback(AutoFocusMoveCallback cb) {
            mCameraHandler.obtainMessage(SET_AUTO_FOCUS_MOVE_CALLBACK, cb).sendToTarget();
        }

        public void takePicture(final ShutterCallback shutter, final PictureCallback raw,
                final PictureCallback postview, final PictureCallback jpeg) {
            // Too many parameters, so use post for simplicity
            mCameraHandler.post(new Runnable() {
                @Override
                public void run() {
                    mCamera.takePicture(shutter, raw, postview, jpeg);
                }
            });
            waitDone();
        }

        public void setDisplayOrientation(int degrees) {
            mCameraHandler.obtainMessage(SET_DISPLAY_ORIENTATION, degrees, 0)
                    .sendToTarget();
        }

        public void setZoomChangeListener(OnZoomChangeListener listener) {
            mCameraHandler.obtainMessage(SET_ZOOM_CHANGE_LISTENER, listener).sendToTarget();
        }

        public void setFaceDetectionListener(FaceDetectionListener listener) {
            mCameraHandler.obtainMessage(SET_FACE_DETECTION_LISTENER, listener).sendToTarget();
        }

        public void startFaceDetection() {
            mCameraHandler.sendEmptyMessage(START_FACE_DETECTION);
        }

        public void stopFaceDetection() {
            mCameraHandler.sendEmptyMessage(STOP_FACE_DETECTION);
        }
        public void setObjectTrackingListener(ObjectTrackingListener listener) {
            mCameraHandler.obtainMessage(SET_OBJECT_TRACKING_LISTENER, listener).sendToTarget();
        }
        public void startOT(int x, int y) {
            mCameraHandler.obtainMessage(START_OBJECT_TRACKING, x, y).sendToTarget();
        }
        
        public void stopOT() {
            mCameraHandler.sendEmptyMessage(STOP_OBJECT_TRACKING);
        }

        public void setErrorCallback(ErrorCallback cb) {
            mCameraHandler.obtainMessage(SET_ERROR_CALLBACK, cb).sendToTarget();
        }

        public void setParameters(Parameters params) {
            if (params == null) {
                Log.v(TAG, "null parameters in setParameters()");
                return;
            }
            mCameraHandler.obtainMessage(SET_PARAMETERS, params.flatten())
                    .sendToTarget();
        }
        
        public void setParametersAsync(Parameters params) {
            mCameraHandler.removeMessages(SET_PARAMETERS_ASYNC);
            if (params == null) {
                Log.v(TAG, "null parameters in setParameters()");
                return;
            }
            mCameraHandler.obtainMessage(SET_PARAMETERS_ASYNC, params.flatten())
                    .sendToTarget();
        }
        
        public void setParametersAsync(final Camera context, final int zoomValue) {
            // Too many parameters, so use post for simplicity
            synchronized (CameraProxy.this) {
                if (mAsyncRunnable != null) {
                    mCameraHandler.removeCallbacks(mAsyncRunnable);
                }
                mAsyncRunnable = new Runnable() {
                    @Override
                    public void run() {
                        Log.d(TAG, "mAsyncRunnable.run(" + zoomValue + ") this="
                                + mAsyncRunnable + ", mCamera=" + mCamera);
                        if (mCamera != null && mCameraProxy != null) {
                            if (!mCameraProxy.tryLockParametersRun(new Runnable() {
                                @Override
                                public void run() {
                                    MMProfileManager.startProfileSetParameters();
                                    //Here we use zoom value instead of parameters for that:
                                    //parameters may be different from current parameters.
                                    Parameters params = context.getParameters();
                                    if (mCamera != null && params != null) {
                                        params.setZoom(zoomValue);
                                        mCamera.setParameters(params);
                                    }
                                    MMProfileManager.stopProfileSetParameters();
                                 }
                             })) {
                                //Second async may changed the runnable,
                                //here we sync the new runnable and post it again.
                                synchronized (CameraProxy.this) {
                                    if (mAsyncRunnable != null) {
                                        mCameraHandler.removeCallbacks(mAsyncRunnable);
                                    }
                                    mCameraHandler.post(mAsyncRunnable);
                                    Log.d(TAG, "mAsyncRunnable.post " + mAsyncRunnable);
                                }
                            }
                        }
                    }
                };
                mCameraHandler.post(mAsyncRunnable);
                Log.d(TAG, "setParametersAsync(" + zoomValue + ") mAsyncRunnable=" + mAsyncRunnable);
            }
        }

        public Parameters getParameters() {
            mCameraHandler.sendEmptyMessage(GET_PARAMETERS);
            waitDone();
            return mParameters;
        }

        ///M: JB migration start @{
        public void startSmoothZoom(int zoomValue) {
            mCameraHandler.obtainMessage(START_SMOOTH_ZOOM, zoomValue, 0).sendToTarget();
            waitDone();
        }

        public void setAUTORAMACallback(AUTORAMACallback autoramaCallback) {
            mCameraHandler.obtainMessage(SET_AUTORAMA_CALLBACK, autoramaCallback).sendToTarget();
            waitDone();
        }

        public void setAUTORAMAMVCallback(AUTORAMAMVCallback autoramamvCallback) {
            mCameraHandler.obtainMessage(SET_AUTORAMA_MV_CALLBACK, autoramamvCallback).sendToTarget();
            waitDone();
        }
        
        public void setHDROriginalCallback(HDROriginalCallback hdrOriginalCallback) {
            mCameraHandler.obtainMessage(SET_HDR_ORIGINAL_CALLBACK, hdrOriginalCallback).sendToTarget();
            waitDone();
        }
        
        public void setFBOriginalCallback(FBOriginalCallback fbOriginalCallback) {
            mCameraHandler.obtainMessage(SET_FB_ORIGINAL_CALLBACK, fbOriginalCallback).sendToTarget();
            waitDone();
        }
        

        public void startAUTORAMA(int num) {
            mCameraHandler.obtainMessage(START_AUTORAMA, num, 0).sendToTarget();
            waitDone();
        }

        public void stopAUTORAMA(int isMerge) {
            mCameraHandler.obtainMessage(STOP_AUTORAMA, isMerge, 0).sendToTarget();
            waitDone();
        }

        public void setMAVCallback(MAVCallback mavCallback) {
            mCameraHandler.obtainMessage(SET_MAV_CALLBACK, mavCallback).sendToTarget();
            waitDone();
        }

        public void setMotionTrackCallback(MotionTrackCallback motionTrackCallback) {
        	mCameraHandler.obtainMessage(SET_MOTIONTRACK_CALLBACK, motionTrackCallback).sendToTarget();
            waitDone();
        }
        
        public void startMAV(int num) {
            mCameraHandler.obtainMessage(START_MAV, num, 0).sendToTarget();
            waitDone();
        }

        public void stopMAV(int isMerge) {
            mCameraHandler.obtainMessage(STOP_MAV, isMerge, 0).sendToTarget();
            waitDone();
        }

        public void setASDCallback(ASDCallback asdCallback) {
            mCameraHandler.obtainMessage(SET_ASD_CALLBACK, asdCallback).sendToTarget();
            waitDone();
        }

        public void setSmileCallback(SmileCallback smileCallback) {
            mCameraHandler.obtainMessage(SET_SMILE_CALLBACK, smileCallback).sendToTarget();
            waitDone();
        }

        public void startSDPreview() {
            mCameraHandler.sendEmptyMessage(START_SD_PREVIEW);
            waitDone();
        }

        public void cancelSDPreview() {
            mCameraHandler.sendEmptyMessage(CANCEL_SD_PREVIEW);
            waitDone();
        }
        
        public void setGestureCallback(GestureCallback smileCallback) {
            mCameraHandler.obtainMessage(SET_GESTURE_CALLBACK, smileCallback).sendToTarget();
            waitDone();
        }
        
        public void startGDPreview() {
            mCameraHandler.sendEmptyMessage(START_GD_PREVIEW);
            waitDone();
        }

        public void cancelGDPreview() {
            mCameraHandler.sendEmptyMessage(CANCEL_GD_PREVIEW);
            waitDone();
        }


        public void cancelContinuousShot() {
            mCameraHandler.sendEmptyMessage(CANCEL_CONTINUOUS_SHOT);
            waitDone();
        }

        public void setContinuousShotSpeed(int speed) {
            mCameraHandler.obtainMessage(SET_CONTINUOUS_SHOT_SPEED, speed, 0).sendToTarget();
            waitDone();
        }

        public void setPreviewDoneCallback(ZSDPreviewDone callback) {
            mCameraHandler.obtainMessage(SET_PREVIEW_DONE_CALLBACK, callback).sendToTarget();
            waitDone();
        }

        public void setCShotDoneCallback(ContinuousShotDone callback) {
            mCameraHandler.obtainMessage(SET_CSHOT_DONE_CALLBACK, callback).sendToTarget();
            waitDone();
        }
        public void setPreview3DModeForCamera(boolean enable) {
            mCameraHandler.obtainMessage(SET_STEREO3D_MODE, enable).sendToTarget();
            waitDone();
        }
        
        public void start3DSHOT(int num) {
            mCameraHandler.obtainMessage(START_3D_SHOT, num, 0).sendToTarget();
            waitDone();
        }
        
        public void stop3DSHOT(int isMerge) {
            mCameraHandler.obtainMessage(STOP_3D_SHOT, isMerge, 0).sendToTarget();
            waitDone();
        }
        
        public void startMotionTrack(int num) {

            mCameraHandler.obtainMessage(START_MOTION_TRACK, num, 0).sendToTarget();
            waitDone();
        }
        
        public void stopMotionTrack() {
            mCameraHandler.sendEmptyMessage(STOP_MOTION_TRACK);
            waitDone(); 
        }
    
    
        ///M: lock parameter for ConcurrentModificationException. @{ 
        private Runnable mAsyncRunnable;
        private static final int ENGINE_ACCESS_MAX_TIMEOUT_MS = 500;
        private ReentrantLock mLock = new ReentrantLock();
        private void lockParameters() throws InterruptedException {
            Log.d(TAG, "lockParameters: grabbing lock", new Throwable());
            mLock.lock();
            Log.d(TAG, "lockParameters: grabbed lock");
        }

        private void unlockParameters() {
            Log.d(TAG, "lockParameters: releasing lock");
            mLock.unlock();
        }
        
        private boolean tryLockParameters(long timeoutMs) throws InterruptedException {
            Log.d(TAG, "try lock: grabbing lock with timeout " + timeoutMs, new Throwable());
            boolean acquireSem = mLock.tryLock(timeoutMs, TimeUnit.MILLISECONDS);
            Log.d(TAG, "try lock: grabbed lock status " + acquireSem);

            return acquireSem;
        }
        
        public void lockParametersRun(Runnable runnable) {
            boolean lockedParameters = false;
            try {
                lockParameters();
                lockedParameters = true;
                runnable.run();
            } catch (InterruptedException  ex) {
                Log.e(TAG, "lockParametersRun() not successfull.", ex);
            } finally {
                if (lockedParameters) {
                    unlockParameters();
                }
            }
        }
        
        public boolean tryLockParametersRun(Runnable runnable) {
            boolean lockedParameters = false;
            try {
                lockedParameters = tryLockParameters(ENGINE_ACCESS_MAX_TIMEOUT_MS);
                if (lockedParameters) {
                    runnable.run();
                }
            } catch (InterruptedException  ex) {
                Log.e(TAG, "tryLockParametersRun() not successfull.", ex);
            } finally {
                if (lockedParameters) {
                    unlockParameters();
                }
            }
            Log.d(TAG, "tryLockParametersRun(" + runnable + ") return " + lockedParameters);
            return lockedParameters;
        }
        /// @}
    }
    
 // return false if cancelled.
    public boolean waitDone() {
        final Object waitDoneLock = new Object();
        final Runnable unlockRunnable = new Runnable() {
            @Override
            public void run() {
                synchronized (waitDoneLock) {
                    waitDoneLock.notifyAll();
                }
            }
        };

        synchronized (waitDoneLock) {
            mCameraHandler.post(unlockRunnable);
            try {
                waitDoneLock.wait();
            } catch (InterruptedException ex) {
                Log.v(TAG, "waitDone interrupted");
                return false;
            }
        }
        return true;
    }
    
}
