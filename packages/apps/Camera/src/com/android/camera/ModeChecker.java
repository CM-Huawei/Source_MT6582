package com.android.camera;

import java.util.List;

import android.hardware.Camera.CameraInfo;
import android.hardware.Camera.Parameters;

import com.android.camera.manager.ModePicker;

public class ModeChecker {
    private static final String TAG = "ModeChecker";

    private static final String[] MODE_STRING_NORMAL = new String[ModePicker.MODE_NUM_ALL - 1];
    private static final boolean[][] MATRIX_NORMAL_ENABLE = new boolean[ModePicker.MODE_NUM_ALL][];
    private static final boolean[][] MATRIX_PREVIEW3D_ENABLE = new boolean[ModePicker.MODE_NUM_ALL][];
    private static final boolean[][] MATRIX_SINGLE3D_ENABLE = new boolean[ModePicker.MODE_NUM_ALL][];

    static {
        MODE_STRING_NORMAL[ModePicker.MODE_PHOTO]         = Parameters.CAPTURE_MODE_NORMAL;
        MODE_STRING_NORMAL[ModePicker.MODE_HDR]           = Parameters.SCENE_MODE_HDR;
        MODE_STRING_NORMAL[ModePicker.MODE_FACE_BEAUTY]   = Parameters.CAPTURE_MODE_FB;
        MODE_STRING_NORMAL[ModePicker.MODE_PANORAMA]      = "autorama";//Parameters.CAPTURE_MODE_PANORAMA_SHOT;
        MODE_STRING_NORMAL[ModePicker.MODE_MAV]           = "mav";
        MODE_STRING_NORMAL[ModePicker.MODE_ASD]           = Parameters.CAPTURE_MODE_ASD;
        MODE_STRING_NORMAL[ModePicker.MODE_SMILE_SHOT]    = Parameters.CAPTURE_MODE_SMILE_SHOT;
        MODE_STRING_NORMAL[ModePicker.MODE_MOTION_TRACK]  = "motiontrack";
        MODE_STRING_NORMAL[ModePicker.MODE_GESTURE_SHOT]  = "gestureshot";
        
                                                                          //back  front
        MATRIX_NORMAL_ENABLE[ModePicker.MODE_PHOTO]         = new boolean[]{true, true};
        MATRIX_NORMAL_ENABLE[ModePicker.MODE_HDR]           = new boolean[]{true, false};
        MATRIX_NORMAL_ENABLE[ModePicker.MODE_FACE_BEAUTY]   = new boolean[]{true, true};
        MATRIX_NORMAL_ENABLE[ModePicker.MODE_PANORAMA]      = new boolean[]{true, false};
        MATRIX_NORMAL_ENABLE[ModePicker.MODE_MAV]           = new boolean[]{true, false};
        MATRIX_NORMAL_ENABLE[ModePicker.MODE_ASD]           = new boolean[]{true, false};
        MATRIX_NORMAL_ENABLE[ModePicker.MODE_SMILE_SHOT]    = new boolean[]{true, false};
        MATRIX_NORMAL_ENABLE[ModePicker.MODE_MOTION_TRACK]  = new boolean[]{true, false};
        MATRIX_NORMAL_ENABLE[ModePicker.MODE_GESTURE_SHOT]  = new boolean[]{true, true};
        MATRIX_NORMAL_ENABLE[ModePicker.MODE_VIDEO]         = new boolean[]{true, true};
        MATRIX_NORMAL_ENABLE[ModePicker.MODE_LIVE_PHOTO]         = new boolean[]{true, false};
        
        MATRIX_PREVIEW3D_ENABLE[ModePicker.MODE_PHOTO]        = new boolean[]{true, false};
        MATRIX_PREVIEW3D_ENABLE[ModePicker.MODE_HDR]          = new boolean[]{false, false};
        MATRIX_PREVIEW3D_ENABLE[ModePicker.MODE_FACE_BEAUTY]  = new boolean[]{false, false};
        MATRIX_PREVIEW3D_ENABLE[ModePicker.MODE_PANORAMA]     = new boolean[]{false, false};
        MATRIX_PREVIEW3D_ENABLE[ModePicker.MODE_MAV]          = new boolean[]{false, false};
        MATRIX_PREVIEW3D_ENABLE[ModePicker.MODE_ASD]          = new boolean[]{false, false};
        MATRIX_PREVIEW3D_ENABLE[ModePicker.MODE_SMILE_SHOT]   = new boolean[]{false, false};
        MATRIX_PREVIEW3D_ENABLE[ModePicker.MODE_MOTION_TRACK] = new boolean[]{false, false};
        MATRIX_PREVIEW3D_ENABLE[ModePicker.MODE_GESTURE_SHOT] = new boolean[]{false, false};
        MATRIX_PREVIEW3D_ENABLE[ModePicker.MODE_VIDEO]        = new boolean[]{true, false};
        MATRIX_PREVIEW3D_ENABLE[ModePicker.MODE_LIVE_PHOTO]   = new boolean[]{false, false};
        
        MATRIX_SINGLE3D_ENABLE[ModePicker.MODE_PHOTO]         = new boolean[]{true, false};
        MATRIX_SINGLE3D_ENABLE[ModePicker.MODE_HDR]           = new boolean[]{false, false};
        MATRIX_SINGLE3D_ENABLE[ModePicker.MODE_FACE_BEAUTY]   = new boolean[]{false, false};
        MATRIX_SINGLE3D_ENABLE[ModePicker.MODE_PANORAMA]      = new boolean[]{true, false};
        MATRIX_SINGLE3D_ENABLE[ModePicker.MODE_MAV]           = new boolean[]{false, false};
        MATRIX_SINGLE3D_ENABLE[ModePicker.MODE_ASD]           = new boolean[]{false, false};
        MATRIX_SINGLE3D_ENABLE[ModePicker.MODE_SMILE_SHOT]    = new boolean[]{false, false};
        MATRIX_SINGLE3D_ENABLE[ModePicker.MODE_GESTURE_SHOT]  = new boolean[]{false, false};
        MATRIX_SINGLE3D_ENABLE[ModePicker.MODE_MOTION_TRACK]  = new boolean[]{false, false};
        MATRIX_SINGLE3D_ENABLE[ModePicker.MODE_VIDEO]         = new boolean[]{false, false};
        MATRIX_SINGLE3D_ENABLE[ModePicker.MODE_LIVE_PHOTO]    = new boolean[]{false, false};
    }

    public static void updateModeMatrix(Camera camera, int cameraId) {
        List<String> supported = camera.getParameters()
                .getSupportedCaptureMode();
        Log.d(TAG, "updateModeMatrix: " + supported);
        List<String> scenemode = camera.getParameters()
                .getSupportedSceneModes();
        Log.d(TAG, "updateModeMatrix: scenemode = " + scenemode);
        if (FeatureSwitcher.isStereo3dEnable() && camera.isStereoMode()) {
            return;
        }
        //video modes can not judge form feature table
        for (int i = 0; i < ModePicker.MODE_LIVE_PHOTO; i++) {
            if (MATRIX_NORMAL_ENABLE[i][cameraId]
                    && supported.indexOf(MODE_STRING_NORMAL[i]) < 0) {
                if (i != ModePicker.MODE_HDR) {
                    MATRIX_NORMAL_ENABLE[i][cameraId] = false;
                } else if (scenemode.indexOf(MODE_STRING_NORMAL[i]) < 0) {
                    MATRIX_NORMAL_ENABLE[i][cameraId] = false;
                }
            }
            Log.d(TAG, "Camera " + cameraId + "'s " + MODE_STRING_NORMAL[i]
                    + " = " + MATRIX_NORMAL_ENABLE[i][cameraId]);
        }
        
        MATRIX_NORMAL_ENABLE[ModePicker.MODE_GESTURE_SHOT][cameraId] = FeatureSwitcher.isGestureShotSupport();

        //video mode judge from feature table
        if (cameraId == CameraInfo.CAMERA_FACING_BACK) {
            MATRIX_NORMAL_ENABLE[ModePicker.MODE_LIVE_PHOTO][cameraId] = FeatureSwitcher.isLivePhotoEnabled()
                    && (Util.getDeviceRam() > 512*1024)
                    && ((Util.getDeviceCores() > 2));
        }
    }

    public static boolean getStereoPickerVisibile(Camera camera) {
        if (!FeatureSwitcher.isStereo3dEnable()) {
            return false;
        }
        //if the intent's actions is "IMAGE_CAPTURE", stereo3D icons is invisible. 
        if (camera.isImageCaptureIntent()) {
            return false;
        }
        boolean visible = false;
        int mode = camera.getCurrentMode();
        int cameraId = camera.getCameraId();
        boolean[][] matrix3d;
        if (FeatureSwitcher.isStereoSingle3d()) {
            matrix3d = MATRIX_SINGLE3D_ENABLE;
        } else {
            matrix3d = MATRIX_PREVIEW3D_ENABLE;
        }
        
        int index = mode % 100;
        visible = matrix3d[index][cameraId] && MATRIX_NORMAL_ENABLE[index][cameraId];
        Log.d(TAG, "getStereoPickerVisibile(" + mode + ", " + cameraId + ") return " + visible);
        return visible;
    }
    
    public static boolean getCameraPickerVisible(Camera camera) {
        int cameranum = camera.getCameraCount();
        if (cameranum < 2) {
            return false;
        }
        int mode = camera.getCurrentMode();
        boolean stereo = camera.isStereoMode();
        boolean[][] matrix;
        if (FeatureSwitcher.isStereoSingle3d() && stereo) {
            matrix = MATRIX_SINGLE3D_ENABLE;
        } else if (stereo) {
            matrix = MATRIX_PREVIEW3D_ENABLE;
        } else {
            matrix = MATRIX_NORMAL_ENABLE;
        }
        int index = mode % 100;
        boolean visible = matrix[index][0] && matrix[index][1];
        Log.d(TAG, "getCameraPickerVisible(" + mode + ", " + stereo + ") return " + visible);
        return visible;
    }
    
    public static boolean getModePickerVisible(Camera camera, int cameraId, int mode) {
        boolean visible = false;
        boolean stereo = camera.isStereoMode();
            boolean[][] matrix;
            if (FeatureSwitcher.isStereoSingle3d() && stereo) {
                matrix = MATRIX_SINGLE3D_ENABLE;
            } else if (stereo) {
                matrix = MATRIX_PREVIEW3D_ENABLE;
            } else {
                matrix = MATRIX_NORMAL_ENABLE;
            }
            int index = mode % 100;
            visible = matrix[index][cameraId];
        if (ModePicker.MODE_VIDEO == mode || ModePicker.MODE_VIDEO_3D == mode) {
            visible = true;
        }
        Log.d(TAG, "getModePickerVisible(" + cameraId + ", " + mode + ", " + stereo + ") return " + visible);
        return visible;
    }
    
    public static int modesSupportedByCamera(Camera camera, int cameraId) {
        int count = 0;
        boolean stereo = camera.isStereoMode();
        boolean[][] matrix;
        if (FeatureSwitcher.isStereoSingle3d() && stereo) {
            matrix = MATRIX_SINGLE3D_ENABLE;
        } else if (stereo) {
            matrix = MATRIX_PREVIEW3D_ENABLE;
        } else {
            matrix = MATRIX_NORMAL_ENABLE;
        }
        for (int i = 0; i < ModePicker.MODE_VIDEO; i++) {
        	// asd, smile shot, hdr, video is not show in ModePicker, so filter them
            if (matrix[i][cameraId] 
            		&& i != ModePicker.MODE_ASD
            		&& i != ModePicker.MODE_SMILE_SHOT
            		&& i != ModePicker.MODE_HDR
            		&& i != ModePicker.MODE_GESTURE_SHOT) {
                count++;
            }
        } 
        return count;
    }
}
