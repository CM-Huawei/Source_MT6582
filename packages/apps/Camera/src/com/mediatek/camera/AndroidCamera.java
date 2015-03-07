package com.mediatek.camera;

import android.content.Context;
import android.graphics.SurfaceTexture;

import android.hardware.Camera;
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

import com.android.camera.Log;
import com.android.camera.Util;

import java.io.IOException;

public class AndroidCamera implements ICamera {
    private static final String TAG = "AndroidCamera";

    protected Camera mCamera;

    public AndroidCamera(Camera camera) {
        Util.assertError(null != camera);
        mCamera = camera;
    }

    public Camera getInstance() {
        return mCamera;
    }

    public void addCallbackBuffer(byte[] callbackBuffer) {
        mCamera.addCallbackBuffer(callbackBuffer);
    }

    public void addRawImageCallbackBuffer(byte[] callbackBuffer) {
        mCamera.addRawImageCallbackBuffer(callbackBuffer);
    }

    public void autoFocus(AutoFocusCallback cb) {
        mCamera.autoFocus(cb);
    }

    public void cancelAutoFocus() {
        mCamera.cancelAutoFocus();
    }

    public void cancelContinuousShot() {
        mCamera.cancelContinuousShot();
    }

    public void cancelSDPreview() {
        mCamera.cancelSDPreview();
    }
    
    public void cancelGDPreview() {
        mCamera.cancelGDPreview();
    }

    public void lock() {
        mCamera.lock();
    }

    public Parameters getParameters() {
        return mCamera.getParameters();
    }

    public void release() {
        mCamera.release();
    }

    public void reconnect() throws IOException {
        mCamera.reconnect();
    }

    public void setASDCallback(ASDCallback cb) {
        mCamera.setASDCallback(cb);
    }

    public void setAutoFocusMoveCallback(AutoFocusMoveCallback cb) {
        mCamera.setAutoFocusMoveCallback(cb);
    }

    public void setAUTORAMACallback(AUTORAMACallback cb) {
        mCamera.setAUTORAMACallback(cb);
    }

    public void setAUTORAMAMVCallback(AUTORAMAMVCallback cb) {
        mCamera.setAUTORAMAMVCallback(cb);
    }
    
    public void setHDROriginalCallback(HDROriginalCallback cb) {
    	mCamera.setHDROriginalCallback(cb);
    }
    
    public void setFBOriginalCallback(FBOriginalCallback cb) {
    	mCamera.setFBOriginalCallback(cb);
    }
    
    public void setContext(Context context) {
    }

    public void setCSDoneCallback(ContinuousShotDone callback) {
        mCamera.setCSDoneCallback(callback);
    }

    public void setContinuousShotSpeed(int speed) {
        mCamera.setContinuousShotSpeed(speed);
    }

    public void setDisplayOrientation(int degrees) {
        mCamera.setDisplayOrientation(degrees);
    }

    public void setErrorCallback(ErrorCallback cb) {
        mCamera.setErrorCallback(cb);
    }

    public void setFaceDetectionListener(FaceDetectionListener listener) {
        mCamera.setFaceDetectionListener(listener);
    }

    public void setMAVCallback(MAVCallback cb) {
        mCamera.setMAVCallback(cb);
    }
    
    public void setMotionTrackCallback(MotionTrackCallback cb) {
        mCamera.setMotionTrackCallback(cb);
    }

    public void setParameters(Parameters params) {
        mCamera.setParameters(params);
    }

    public void setPreviewCallbackWithBuffer(PreviewCallback cb) {
        mCamera.setPreviewCallbackWithBuffer(cb);
    }

    public void setPreviewDoneCallback(ZSDPreviewDone callback) {
        mCamera.setPreviewDoneCallback(callback);
    }

    public void setPreviewTexture(SurfaceTexture surfaceTexture) throws IOException {
        mCamera.setPreviewTexture(surfaceTexture);
    }

    public void setSmileCallback(SmileCallback cb) {
        mCamera.setSmileCallback(cb);
    }
    
    public void setGestureCallback(GestureCallback cb) {
        mCamera.setGestureCallback(cb);
    }

    public void setZoomChangeListener(OnZoomChangeListener listener) {
        mCamera.setZoomChangeListener(listener);
    }

//    public void slowdownContinuousShot() {
//        mCamera.slowdownContinuousShot();
//    }

    public void startAUTORAMA(int num) {
        mCamera.startAUTORAMA(num);
    }

    public void startFaceDetection() {
        mCamera.startFaceDetection();
    }
    
    public void startOT(int x, int y) {
        mCamera.startOT(x, y);
    }
    
    public void stopOT() {
        mCamera.stopOT();
    }
    
    public void setObjectTrackingListener(ObjectTrackingListener listener) {
        mCamera.setObjectTrackingListener(listener);
    }
    public void startMAV(int num) {
        mCamera.startMAV(num);
    }

    public void startPreview() {
        Log.i(TAG, "startPreview()");
        mCamera.startPreview();
    }

    public void startSmoothZoom(int value) {
        mCamera.startSmoothZoom(value);
    }

    public void startSDPreview() {
        mCamera.startSDPreview();
    }
    
    public void startGDPreview() {
        mCamera.startGDPreview();
    }

    public void stopAUTORAMA(int isMerge) {
        mCamera.stopAUTORAMA(isMerge);
    }

    public void stopFaceDetection() {
        mCamera.stopFaceDetection();
    }

    public void stopMAV(int isMerge) {
        mCamera.stopMAV(isMerge);
    }
    
    public void startMotionTrack(int num) {
        mCamera.startMotionTrack(num);
    }
    
    public void stopMotionTrack() {
        mCamera.stopMotionTrack();
    }
    
    public void stopPreview() {
        mCamera.stopPreview();
    }

    public void takePicture(ShutterCallback shutter,
                            PictureCallback raw, PictureCallback jpeg) {
        mCamera.takePicture(shutter, raw, jpeg);
    }

    public void takePicture(ShutterCallback shutter, PictureCallback raw,
                            PictureCallback postview, PictureCallback jpeg) {
        mCamera.takePicture(shutter, raw, postview, jpeg);
    }

    public void unlock() {
        mCamera.unlock();
    }
    
    public void start3DSHOT(int num) {
        mCamera.start3DSHOT(num);
    }
    public void stop3DSHOT(int num) {
        mCamera.stop3DSHOT(num);
    }
    
    public void setPreview3DModeForCamera(boolean enable) {
        mCamera.setStereo3DModeForCamera(enable);
    }
}
