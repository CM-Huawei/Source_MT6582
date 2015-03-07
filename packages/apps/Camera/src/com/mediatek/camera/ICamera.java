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

import java.io.IOException;

public interface ICamera {
    Camera getInstance();
    void addCallbackBuffer(byte[] callbackBuffer);
    void addRawImageCallbackBuffer(byte[] callbackBuffer);
    void autoFocus(AutoFocusCallback cb);
    void cancelAutoFocus();
    void cancelContinuousShot();
    void cancelSDPreview();
    void lock();
    Parameters getParameters();
    void release();
    void reconnect() throws IOException;
    void setASDCallback(ASDCallback cb);
    void setAutoFocusMoveCallback(AutoFocusMoveCallback cb);
    void setAUTORAMACallback(AUTORAMACallback cb);
    void setAUTORAMAMVCallback(AUTORAMAMVCallback cb);
    void setContext(Context context);
    void setCSDoneCallback(ContinuousShotDone callback);
    void setContinuousShotSpeed(int speed);
    void setDisplayOrientation(int degrees);
    void setErrorCallback(ErrorCallback cb);
    void setFaceDetectionListener(FaceDetectionListener listener);
    void setFBOriginalCallback(FBOriginalCallback cb);
    void setHDROriginalCallback(HDROriginalCallback cb);
    void setMAVCallback(MAVCallback cb);
    void setParameters(Parameters params);
    void setPreviewCallbackWithBuffer(PreviewCallback cb);
    void setPreviewDoneCallback(ZSDPreviewDone callback);
    void setPreviewTexture(SurfaceTexture surfaceTexture) throws IOException;
    void setSmileCallback(SmileCallback cb);
    void setZoomChangeListener(OnZoomChangeListener listener);
//    void slowdownContinuousShot();
    void startAUTORAMA(int num);
    void start3DSHOT(int num);
    void stop3DSHOT(int num);
    void setPreview3DModeForCamera(boolean enable);
    void startFaceDetection();
    void startOT(int x, int y);
    void stopOT();
    void setObjectTrackingListener(ObjectTrackingListener listener);
    void startMAV(int num);
    void startPreview();
    void startSmoothZoom(int value);
    void startSDPreview();
    void stopAUTORAMA(int isMerge);
    void stopFaceDetection();
    void stopMAV(int isMerge);
    void startMotionTrack(int num);
    void stopMotionTrack();
    void stopPreview();
    void setGestureCallback(GestureCallback cb);
    void startGDPreview();
    void cancelGDPreview();
    void takePicture(ShutterCallback shutter, PictureCallback raw,
                            PictureCallback jpeg);
    void takePicture(ShutterCallback shutter, PictureCallback raw,
                            PictureCallback postview, PictureCallback jpeg);
    void unlock();
    public void setMotionTrackCallback(MotionTrackCallback cb);
}
