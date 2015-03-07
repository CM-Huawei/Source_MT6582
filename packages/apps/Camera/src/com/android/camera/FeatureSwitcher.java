package com.android.camera;

import com.mediatek.common.featureoption.FeatureOption;

public class FeatureSwitcher {
    private static final String TAG = "FeatureSwitcher";
    //used as flag to decide whether support double tap up to zoom/default enabled=false means don't support double tap up
    // and enabled = false can make touch focus faster 300ms than suppot double tap up 
    public static boolean isSupportDoubleTapUp(){
        boolean enabled = false;
          Log.d(TAG, "isSupportDoubleTapUp() return " + enabled);
        return enabled;
    }
    public static boolean isHdRecordingEnabled() {
        boolean enabled = FeatureOption.MTK_AUDIO_HD_REC_SUPPORT;
        Log.d(TAG, "isHdRecordingEnabled() return " + enabled);
        return enabled;
    }
    
    public static boolean isStereo3dEnable() {
        boolean enabled = false;
        enabled =  FeatureOption.MTK_S3D_SUPPORT;
        Log.d(TAG, "isStereo3dEnable = " + enabled);
        return enabled;
    }
    public static boolean isVssEnable() {
        boolean enabled = false;
        enabled = FeatureOption.MTK_VSS_SUPPORT;
        Log.d(TAG,"isVSSEnable = " + enabled);
        return enabled;
    }
    public static boolean isStereoSingle3d() {
        boolean enabled = false;
        //enabled = FeatureOption.MTK_SINGLE_3DSHOT_SUPPORT;
        Log.d(TAG, "isStereoSingle3d = " + enabled);
        return enabled;
    }
    public static boolean isSlowMotionSupport(){
        boolean enabled = FeatureOption.MTK_SLOW_MOTION_VIDEO_SUPPORT;
        Log.d(TAG,"isSlowMotionSupport() return " + enabled);
        return enabled ;
    }
    public static boolean isGestureShotSupport() {
        boolean enabled = FeatureOption.MTK_CAM_GESTURE_SUPPORT;
        Log.d(TAG,"isGestureShotSupport() return " + enabled);
        return enabled;
    }
    
    public static boolean isLcaROM() {
        boolean enabled = FeatureOption.MTK_LCA_ROM_OPTIMIZE;
        Log.d(TAG, "isLcaEffects() return " + enabled);
        return enabled ;
    }
    
    // M: used as a flag to decide can slide to gallery or not
    public static boolean isSlideEnabled() {
        boolean enabled = true;
        Log.d(TAG, "isSlideEnabled() return " + enabled);
        return enabled;
    }

    // M: used as a flag to save origin or not in HDR mode
    public static boolean isHdrOriginalPictureSaved() {
        boolean enabled = true;
        Log.d(TAG, "isHdrOriginalPictureSaved() return " + enabled);
        return enabled;
    }

    // M: used as a flag to save origin or not in FaceBeauty mode
    public static boolean isFaceBeautyOriginalPictureSaved() {
        boolean enabled = true;
        Log.d(TAG, "isFaceBeautyOriginalPictureSaved() return " + enabled);
        return enabled;
    }

    public static boolean isContinuousFocusEnabledWhenTouch() {
        boolean enabled = true;
        Log.d(TAG, "isContinuousFocusEnabledWhenTouch() return " + enabled);
        return enabled;
    }
    
    public static boolean isThemeEnabled() {
        boolean enabled = FeatureOption.MTK_THEMEMANAGER_APP;
        Log.d(TAG, "isThemeEnabled() return " + enabled);
        return enabled;
    }

    public static boolean isVoiceEnabled() {
        boolean enabled = FeatureOption.MTK_VOICE_UI_SUPPORT;
        Log.d(TAG, "isVoiceEnabled() return " + enabled);
        return enabled;
    }

    //M: is LCA Enable
    public static boolean isLcaRAM() {
        boolean enabled = FeatureOption.MTK_LCA_RAM_OPTIMIZE;
        Log.d(TAG, "isLcaEnabled() return " + enabled);
        return enabled;
    }
    public static boolean isOnlyCheckBackCamera() {
        //false will check all camera
        //true will only check back camera
        return false;
    }
    
    public static boolean isMtkFatOnNand() {
        boolean enabled = FeatureOption.MTK_FAT_ON_NAND;
        Log.d(TAG, "isMtkFatOnNand() return " + enabled);
        return enabled;
    }

    public static boolean isTablet() {
        boolean sIsScreenLarge = android.os.SystemProperties.get("ro.build.characteristics").equals("tablet");
        Log.d(TAG, "IsTablet = " + sIsScreenLarge);
        return sIsScreenLarge;
    }
    
    public static boolean isMtkCaptureAnimationEnable() {
        boolean enabled = true;
        Log.d(TAG, "isMR2CaptureAnimationEnable() return " + enabled);
        return enabled;
    }
    public static boolean isSmartBookEnabled() {
        boolean enabled = FeatureOption.MTK_SMARTBOOK_SUPPORT;
        Log.d(TAG, "isSmartBookEnabled() return " + enabled);
        return enabled;
    }
    
    public static boolean isLivePhotoEnabled() {
        boolean enabled = FeatureOption.MTK_LIVE_PHOTO_SUPPORT;
        Log.d(TAG, "isLivePhotoEnabled() return " + enabled);
        return enabled;
    }
    
}
