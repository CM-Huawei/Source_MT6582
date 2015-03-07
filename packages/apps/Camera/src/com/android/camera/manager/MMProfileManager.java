package com.android.camera.manager;

import android.os.Trace;
import com.mediatek.mmprofile.MMProfile;

// This class is actually a wrapper class for MMProfile.
// It providers additional function as feature options
public class MMProfileManager {
    private static final String TAG = "MMProfileManager";
    //events used by Camera
    private static final int EVENT_CAMERA_ROOT;

    private static final int EVENT_CAMERA_HARDWARE;
    private static final int EVENT_CAMERA_OPEN;
    private static final int EVENT_START_PREVIEW;
    private static final int EVENT_STOP_PREVIEW;
    private static final int EVENT_CAMERA_RELEASE;
    private static final int EVENT_GET_PARAMETERS;
    private static final int EVENT_SET_PARAMETERS;

    private static final int EVENT_PHOTO_ACTOR;
    private static final int EVENT_PHOTO_SHUTTER_FOCUS;
    private static final int EVENT_PHOTO_SHUTTER_CLICK;
    private static final int EVENT_PHOTO_TAKE_PICTURE;
    private static final int EVENT_PHOTO_DATA_GRIP;
    private static final int EVENT_PHOTO_STORE_PICTURE;
    private static final int EVENT_PHOTO_SHOT2SHOT_ANIMATE;
    private static final int EVENT_PHOTO_APPLY_PARAMETER_FOR_CAPTURE;

    private static final int EVENT_SWITCH_CAMERA;
    private static final int EVENT_FOCUS;
    private static final int EVENT_CONTINUE_AUTO_FOCUS;
    private static final int EVENT_AUTO_FOCUS;

    private static final int EVENT_VIDEO_ACTOR;
    private static final int EVENT_VIDEO_SHUTTER_CLICK;
    private static final int EVENT_VIDEO_SHUTTER_FOCUS;
    private static final int EVENT_VIDEO_START_RECORD;
    private static final int EVENT_VIDEO_STOP_RECORD;
    private static final int EVENT_VIDEO_STORE_VIDEO;
    private static final int EVENT_VIDEO_INIT_MEDIARECORDER;
    private static final int EVENT_VIDEO_MEDIARECORDER_START;
    private static final int EVENT_VIDEO_EFFECTRECORDER_START;
    private static final int EVENT_VIDEO_UPDATE_RECORDING_TIME;

    private static final int EVENT_CAMERA_SCREEN_NAIL;
    private static final int EVENT_FRAME_AVAILABLE;
    private static final int EVENT_FIRST_FRAME_AVAILABLE;
    private static final int EVENT_REQUEST_RENDER;
    private static final int EVENT_DRAW_SCREEN_NAIL;
    private static final int EVENT_NOTIFY_SERVER_SELF_CHANGE;
    private static final int EVENT_ANIMATE_CAPTURE;
    private static final int EVENT_ANIMATE_SWITCH_CAMERA;
    private static final int EVENT_SUPER_DRAW_NO_ANIMATE;
    private static final int EVENT_SUPER_DRAW_ORIGIN_FRAME;
    private static final int EVENT_SUPER_DRAW_SIZE_CHANGE;

    private static final int EVENT_CAMERA_ACTIVITY;
    private static final int EVENT_CAMERA_ON_CREATE;
    private static final int EVENT_CAMERA_ON_RESUME;
    private static final int EVENT_CAMERA_ON_PAUSE;
    private static final int EVENT_CAMERA_ON_DESTROY;
    private static final int EVENT_CAMERA_START_UP;
    private static final int EVENT_CAMERA_ON_CONFIG_CHANGE;
    private static final int EVENT_CAMERA_ON_ORIENT_CHANGE;
    private static final int EVENT_CAMERA_HANDLE_MSG;
    private static final int EVENT_CAMERA_SEND_MSG;
    private static final int EVENT_CAMERA_EXIT_BY_BACK_KEY;
    private static final int EVENT_CAMERA_GALLERY_BACK_KEY;

    private static final int EVENT_CAMERA_ACTIVITY_DETAIL;
    private static final int EVENT_CAMERA_VIEW_OPERATION;
    private static final int EVENT_CAMERA_PARAMETER_COPY;
    private static final int EVENT_CAMERA_PREVIEW_PRE_READY_BLOCK;
    private static final int EVENT_CAMERA_PREVIEW_PRE_READY_OPEN;
    private static final int EVENT_INIT_OPEN_PROCESS;
    private static final int EVENT_APPLY_PARAMETERS;
    private static final int EVENT_INIT_CAMERA_PREF;
    private static final int EVENT_SET_DISP_ORIENT;
    private static final int EVENT_SET_PREVIEW_ASPECT_RATIO;
    private static final int EVENT_NOTIFY_ORIENT_CHANGED;
    private static final int EVENT_SET_PREVIEW_TEXT;
    private static final int EVENT_RE_INFLATE_VIEW_MGR;
    private static final int EVENT_UPDATE_SURFACE_TEXTURE;
    private static final int EVENT_INIT_FOCUS_MGR;
    private static final int EVENT_LAYOUT_CHANGE;

    private static final int EVENT_SETTING_CHECKER;
    private static final int EVENT_APPLY_PARAM_UI_IMMEDIAT;

    //names of event shown to user
    private static final String NAME_CAMERA_ROOT = "CameraApp";

    private static final String NAME_CAMERA_HARDWARE = "CameraHardWare";
    private static final String NAME_CAMERA_OPEN = "Open";
    private static final String NAME_CAMERA_START_PREVIEW = "StartPreview";
    private static final String NAME_CAMERA_STOP_PREVIEW = "StopPreview";
    private static final String NAME_CAMERA_RELEASE = "Release";
    private static final String NAME_GET_PARAMETERS = "getParameters";
    private static final String NAME_SET_PARAMETERS = "setParameters";

    private static final String NAME_PHOTO_ACTOR = "PhotoActor";
    private static final String NAME_PHOTO_SHUTTER_FOCUS = "FocusPhotoShutter";
    private static final String NAME_PHOTO_SHUTTER_CLICK = "ClickPhotoShutter";
    private static final String NAME_PHOTO_TAKE_PICTURE = "TakePicture";
    private static final String NAME_PHOTO_DATA_GRIP = "PictureDataGrip";
    private static final String NAME_PHOTO_STORE_PICTURE = "StorePicture";
    private static final String NAME_PHOTO_SHOT2SHOT_ANIMATE = "Shot2ShotAnimate";
    private static final String NAME_PHOTO_APPLY_PARAMETER_FOR_CAPTURE = "applyParameterForCapture";

    private static final String NAME_SWITCH_CAMERA = "SwitchCamera";
    private static final String NAME_FOCUS = "AutoAndContinueFocus";
    private static final String NAME_AUTO_FOCUS = "AutoFocus";
    private static final String NAME_CONTINUE_AUTO_FOCUS = "ContinueAutoFocus";

    private static final String NAME_VIDEO_ACTOR = "VideoActor";
    private static final String NAME_VIDEO_SHUTTER_CLICK = "ClickVideoShutter";
    private static final String NAME_VIDEO_SHUTTER_FOCUS = "VideoShutterFocus";
    private static final String NAME_VIDEO_START_RECORD = "VideoStartRecord";
    private static final String NAME_VIDEO_STOP_RECORD = "VideoStopRecord";
    private static final String NAME_VIDEO_STORE_VIDEO = "StoreVideo";
    private static final String NAME_VIDEO_INIT_MEDIARECORDER = "InitMediarecorder";
    private static final String NAME_VIDEO_MEDIARECORDER_START = "MediarecorderStart";
    private static final String NAME_VIDEO_EFFECTRECORDER_START = "EffectrecorderStart";
    private static final String NAME_VIDEO_UPDATE_RECORDING_TIME = "UpdateRecordingTime";

    private static final String NAME_CAMERA_SCREEN_NAIL = "CameraScreenNail";
    private static final String NAME_FRAME_AVAILABLE = "FrameAvailable";
    private static final String NAME_FIRST_FRAME_AVAILABLE = "FirstFrameAvailable";
    private static final String NAME_REQUEST_RENDER = "RequestRender";
    private static final String NAME_DRAW_SCREEN_NAIL = "DrawScreenNail";
    private static final String NAME_NOTIFY_SERVER_SELF_CHANGE = "NotifyServerSelfChange";
    private static final String NAME_ANIMATE_CAPTURE = "AnimateCapture";
    private static final String NAME_ANIMATE_SWITCH_CAMERA = "AnimateSwitchCamera";
    private static final String NAME_SUPER_DRAW_NO_ANIMATE = "SuperDrawNoAnimate";
    private static final String NAME_SUPER_DRAW_ORIGIN_FRAME = "SuperDrawOriginFrame";
    private static final String NAME_SUPER_DRAW_SIZE_CHANGE = "SuperDrawSizeChange";

    private static final String NAME_CAMERA_ACTIVITY = "CameraActivity";
    private static final String NAME_CAMERA_ON_CREATE = "CameraOnCreate";
    private static final String NAME_CAMERA_ON_RESUME = "CameraOnResume";
    private static final String NAME_CAMERA_ON_PAUSE = "CameraOnPause";
    private static final String NAME_CAMERA_ON_DESTROY = "CameraOnDestroy";
    private static final String NAME_CAMERA_START_UP = "CameraStartUp";
    private static final String NAME_CAMERA_ON_CONFIG_CHANGE = "OnConfigChange";
    private static final String NAME_CAMERA_ON_ORIENT_CHANGE = "OnOrientChange";
    private static final String NAME_CAMERA_HANDLE_MSG = "handleMessage";
    private static final String NAME_CAMERA_SEND_MSG = "sendMessage";
    private static final String NAME_CAMERA_EXIT_BY_BACK_KEY = "CameraExitByBackKey";
    private static final String NAME_CAMERA_GALLERY_BACK_KEY = "CameraGalleryBackKey";

    private static final String NAME_CAMERA_ACTIVITY_DETAIL = "CameraActivityDetail";
    private static final String NAME_CAMERA_VIEW_OPERATION = "CameraViewOperation";
    private static final String NAME_CAMERA_PARAMETER_COPY = "CameraParameterCopy";
    private static final String NAME_CAMERA_PREVIEW_PRE_READY_BLOCK = "CameraPreviewPreReadyBlock";
    private static final String NAME_CAMERA_PREVIEW_PRE_READY_OPEN = "CameraPreviewPreReadyOpen";
    private static final String NAME_INIT_OPEN_PROCESS = "InitForOpeningProcess";
    private static final String NAME_APPLY_PARAMETERS = "ApplyParameters";
    private static final String NAME_INIT_CAMERA_PREF = "InitCameraPref";
    private static final String NAME_SET_DISP_ORIENT = "SetDispOrient";
    private static final String NAME_SET_PREVIEW_ASPECT_RATIO = "SetPreviewAspectRatio";
    private static final String NAME_NOTIFY_ORIENT_CHANGED = "NotifyOrientChanged";
    private static final String NAME_SET_PREVIEW_TEXT = "SetPreviewTexture";
    private static final String NAME_RE_INFLATE_VIEW_MGR = "ReInflateViewManager";
    private static final String NAME_UPDATE_SURFACE_TEXTURE = "UpdateSurfaceTexture";
    private static final String NAME_INIT_FOCUS_MGR = "InitFocusManager";
    private static final String NAME_LAYOUT_CHANGE = "onLayoutChange";

    private static final String NAME_SETTING_CHECKER = "SettingChecker";
    private static final String NAME_APPLY_PARAM_UI_IMMEDIAT = "ApplyParametersToUiImmediately";

    static {
        EVENT_CAMERA_ROOT = MMProfileWrapper.doMMProfileRegisterEvent(
            MMProfileWrapper.MMP_ROOT_EVENT, NAME_CAMERA_ROOT);

        // for camera hardware subtree
        EVENT_CAMERA_HARDWARE = MMProfileWrapper.doMMProfileRegisterEvent(
            EVENT_CAMERA_ROOT, NAME_CAMERA_HARDWARE);
        EVENT_CAMERA_OPEN = MMProfileWrapper.doMMProfileRegisterEvent(
            EVENT_CAMERA_HARDWARE, NAME_CAMERA_OPEN);
        EVENT_START_PREVIEW = MMProfileWrapper.doMMProfileRegisterEvent(
            EVENT_CAMERA_HARDWARE, NAME_CAMERA_START_PREVIEW);
        EVENT_STOP_PREVIEW = MMProfileWrapper.doMMProfileRegisterEvent(
            EVENT_CAMERA_HARDWARE, NAME_CAMERA_STOP_PREVIEW);
        EVENT_CAMERA_RELEASE = MMProfileWrapper.doMMProfileRegisterEvent(
            EVENT_CAMERA_HARDWARE, NAME_CAMERA_RELEASE);
        EVENT_GET_PARAMETERS = MMProfileWrapper.doMMProfileRegisterEvent(
            EVENT_CAMERA_HARDWARE, NAME_GET_PARAMETERS);
        EVENT_SET_PARAMETERS = MMProfileWrapper.doMMProfileRegisterEvent(
            EVENT_CAMERA_HARDWARE, NAME_SET_PARAMETERS);

        // for taking photo
        EVENT_PHOTO_ACTOR = MMProfileWrapper.doMMProfileRegisterEvent(
            EVENT_CAMERA_ROOT, NAME_PHOTO_ACTOR);
        EVENT_PHOTO_SHUTTER_FOCUS= MMProfileWrapper.doMMProfileRegisterEvent(
            EVENT_PHOTO_ACTOR, NAME_PHOTO_SHUTTER_FOCUS);
        EVENT_PHOTO_SHUTTER_CLICK = MMProfileWrapper.doMMProfileRegisterEvent(
            EVENT_PHOTO_ACTOR, NAME_PHOTO_SHUTTER_CLICK);
        EVENT_PHOTO_TAKE_PICTURE = MMProfileWrapper.doMMProfileRegisterEvent(
            EVENT_PHOTO_ACTOR, NAME_PHOTO_TAKE_PICTURE);
        EVENT_PHOTO_DATA_GRIP= MMProfileWrapper.doMMProfileRegisterEvent(
            EVENT_PHOTO_ACTOR, NAME_PHOTO_DATA_GRIP);
        EVENT_PHOTO_STORE_PICTURE = MMProfileWrapper.doMMProfileRegisterEvent(
            EVENT_PHOTO_ACTOR, NAME_PHOTO_STORE_PICTURE);
        EVENT_PHOTO_SHOT2SHOT_ANIMATE = MMProfileWrapper.doMMProfileRegisterEvent(
            EVENT_PHOTO_ACTOR, NAME_PHOTO_SHOT2SHOT_ANIMATE);
        EVENT_PHOTO_APPLY_PARAMETER_FOR_CAPTURE = MMProfileWrapper.doMMProfileRegisterEvent(
            EVENT_PHOTO_ACTOR, NAME_PHOTO_APPLY_PARAMETER_FOR_CAPTURE);
        
        // for Focus
        EVENT_FOCUS = MMProfileWrapper.doMMProfileRegisterEvent(
                EVENT_CAMERA_ROOT, NAME_FOCUS);
        EVENT_AUTO_FOCUS = MMProfileWrapper.doMMProfileRegisterEvent(
                EVENT_FOCUS, NAME_AUTO_FOCUS);
        EVENT_CONTINUE_AUTO_FOCUS = MMProfileWrapper.doMMProfileRegisterEvent(
                EVENT_FOCUS, NAME_CONTINUE_AUTO_FOCUS);

        // for camera hardware subtree
        EVENT_SWITCH_CAMERA = MMProfileWrapper.doMMProfileRegisterEvent(
            EVENT_CAMERA_ROOT, NAME_SWITCH_CAMERA);

        // for video recording
        EVENT_VIDEO_ACTOR = MMProfileWrapper.doMMProfileRegisterEvent(
            EVENT_CAMERA_ROOT, NAME_VIDEO_ACTOR);
        EVENT_VIDEO_SHUTTER_CLICK = MMProfileWrapper.doMMProfileRegisterEvent(
            EVENT_VIDEO_ACTOR, NAME_VIDEO_SHUTTER_CLICK);
        EVENT_VIDEO_SHUTTER_FOCUS = MMProfileWrapper.doMMProfileRegisterEvent(
                EVENT_VIDEO_ACTOR, NAME_VIDEO_SHUTTER_FOCUS);
        EVENT_VIDEO_START_RECORD = MMProfileWrapper.doMMProfileRegisterEvent(
            EVENT_VIDEO_ACTOR, NAME_VIDEO_START_RECORD);
        EVENT_VIDEO_STOP_RECORD = MMProfileWrapper.doMMProfileRegisterEvent(
            EVENT_VIDEO_ACTOR, NAME_VIDEO_STOP_RECORD);
        EVENT_VIDEO_STORE_VIDEO = MMProfileWrapper.doMMProfileRegisterEvent(
            EVENT_VIDEO_ACTOR, NAME_VIDEO_STORE_VIDEO);
        EVENT_VIDEO_INIT_MEDIARECORDER= MMProfileWrapper.doMMProfileRegisterEvent(
            EVENT_VIDEO_ACTOR, NAME_VIDEO_INIT_MEDIARECORDER);
        EVENT_VIDEO_MEDIARECORDER_START= MMProfileWrapper.doMMProfileRegisterEvent(
            EVENT_VIDEO_ACTOR, NAME_VIDEO_MEDIARECORDER_START);
        EVENT_VIDEO_EFFECTRECORDER_START= MMProfileWrapper.doMMProfileRegisterEvent(
            EVENT_VIDEO_ACTOR, NAME_VIDEO_EFFECTRECORDER_START);
        EVENT_VIDEO_UPDATE_RECORDING_TIME= MMProfileWrapper.doMMProfileRegisterEvent(
            EVENT_VIDEO_ACTOR, NAME_VIDEO_UPDATE_RECORDING_TIME);

        // for preview frame
        EVENT_CAMERA_SCREEN_NAIL = MMProfileWrapper.doMMProfileRegisterEvent(
            EVENT_CAMERA_ROOT, NAME_CAMERA_SCREEN_NAIL);
        EVENT_FRAME_AVAILABLE = MMProfileWrapper.doMMProfileRegisterEvent(
            EVENT_CAMERA_SCREEN_NAIL, NAME_FRAME_AVAILABLE);
        EVENT_FIRST_FRAME_AVAILABLE = MMProfileWrapper.doMMProfileRegisterEvent(
            EVENT_CAMERA_SCREEN_NAIL, NAME_FIRST_FRAME_AVAILABLE);
        EVENT_REQUEST_RENDER = MMProfileWrapper.doMMProfileRegisterEvent(
            EVENT_CAMERA_SCREEN_NAIL, NAME_REQUEST_RENDER);
        EVENT_DRAW_SCREEN_NAIL = MMProfileWrapper.doMMProfileRegisterEvent(
            EVENT_CAMERA_SCREEN_NAIL, NAME_DRAW_SCREEN_NAIL);
        EVENT_NOTIFY_SERVER_SELF_CHANGE = MMProfileWrapper.doMMProfileRegisterEvent(
            EVENT_CAMERA_SCREEN_NAIL, NAME_NOTIFY_SERVER_SELF_CHANGE);
        EVENT_ANIMATE_CAPTURE = MMProfileWrapper.doMMProfileRegisterEvent(
            EVENT_CAMERA_SCREEN_NAIL, NAME_ANIMATE_CAPTURE);
        EVENT_ANIMATE_SWITCH_CAMERA = MMProfileWrapper.doMMProfileRegisterEvent(
            EVENT_CAMERA_SCREEN_NAIL, NAME_ANIMATE_SWITCH_CAMERA);
        EVENT_SUPER_DRAW_NO_ANIMATE = MMProfileWrapper.doMMProfileRegisterEvent(
            EVENT_CAMERA_SCREEN_NAIL, NAME_SUPER_DRAW_NO_ANIMATE);
        EVENT_SUPER_DRAW_ORIGIN_FRAME = MMProfileWrapper.doMMProfileRegisterEvent(
            EVENT_CAMERA_SCREEN_NAIL, NAME_SUPER_DRAW_ORIGIN_FRAME);
        EVENT_SUPER_DRAW_SIZE_CHANGE = MMProfileWrapper.doMMProfileRegisterEvent(
            EVENT_CAMERA_SCREEN_NAIL, NAME_SUPER_DRAW_SIZE_CHANGE);

        // for camera activity
        EVENT_CAMERA_ACTIVITY = MMProfileWrapper.doMMProfileRegisterEvent(
            EVENT_CAMERA_ROOT, NAME_CAMERA_ACTIVITY);
        EVENT_CAMERA_ON_CREATE = MMProfileWrapper.doMMProfileRegisterEvent(
            EVENT_CAMERA_ACTIVITY, NAME_CAMERA_ON_CREATE);
        EVENT_CAMERA_ON_RESUME = MMProfileWrapper.doMMProfileRegisterEvent(
            EVENT_CAMERA_ACTIVITY, NAME_CAMERA_ON_RESUME);
        EVENT_CAMERA_ON_PAUSE = MMProfileWrapper.doMMProfileRegisterEvent(
            EVENT_CAMERA_ACTIVITY, NAME_CAMERA_ON_PAUSE);
        EVENT_CAMERA_ON_DESTROY = MMProfileWrapper.doMMProfileRegisterEvent(
            EVENT_CAMERA_ACTIVITY, NAME_CAMERA_ON_DESTROY);
        EVENT_CAMERA_START_UP = MMProfileWrapper.doMMProfileRegisterEvent(
            EVENT_CAMERA_ACTIVITY, NAME_CAMERA_START_UP);
        EVENT_CAMERA_ON_CONFIG_CHANGE = MMProfileWrapper.doMMProfileRegisterEvent(
            EVENT_CAMERA_ACTIVITY, NAME_CAMERA_ON_CONFIG_CHANGE);
        EVENT_CAMERA_ON_ORIENT_CHANGE = MMProfileWrapper.doMMProfileRegisterEvent(
            EVENT_CAMERA_ACTIVITY, NAME_CAMERA_ON_ORIENT_CHANGE);
        EVENT_CAMERA_HANDLE_MSG = MMProfileWrapper.doMMProfileRegisterEvent(
            EVENT_CAMERA_ACTIVITY, NAME_CAMERA_HANDLE_MSG);
        EVENT_CAMERA_SEND_MSG = MMProfileWrapper.doMMProfileRegisterEvent(
            EVENT_CAMERA_ACTIVITY, NAME_CAMERA_SEND_MSG);
        EVENT_CAMERA_EXIT_BY_BACK_KEY = MMProfileWrapper.doMMProfileRegisterEvent(
            EVENT_CAMERA_ACTIVITY, NAME_CAMERA_EXIT_BY_BACK_KEY);
        EVENT_CAMERA_GALLERY_BACK_KEY = MMProfileWrapper.doMMProfileRegisterEvent(
            EVENT_CAMERA_ACTIVITY, NAME_CAMERA_GALLERY_BACK_KEY);

        // for camera activity detailed operations
        EVENT_CAMERA_ACTIVITY_DETAIL = MMProfileWrapper.doMMProfileRegisterEvent(
            EVENT_CAMERA_ACTIVITY, NAME_CAMERA_ACTIVITY_DETAIL);
        EVENT_CAMERA_VIEW_OPERATION = MMProfileWrapper.doMMProfileRegisterEvent(
            EVENT_CAMERA_ACTIVITY_DETAIL, NAME_CAMERA_VIEW_OPERATION);
        EVENT_CAMERA_PARAMETER_COPY = MMProfileWrapper.doMMProfileRegisterEvent(
            EVENT_CAMERA_ACTIVITY_DETAIL, NAME_CAMERA_PARAMETER_COPY);
        EVENT_CAMERA_PREVIEW_PRE_READY_BLOCK = MMProfileWrapper.doMMProfileRegisterEvent(
            EVENT_CAMERA_ACTIVITY_DETAIL, NAME_CAMERA_PREVIEW_PRE_READY_BLOCK);
        EVENT_CAMERA_PREVIEW_PRE_READY_OPEN = MMProfileWrapper.doMMProfileRegisterEvent(
            EVENT_CAMERA_ACTIVITY_DETAIL, NAME_CAMERA_PREVIEW_PRE_READY_OPEN);
        EVENT_INIT_OPEN_PROCESS = MMProfileWrapper.doMMProfileRegisterEvent(
            EVENT_CAMERA_ACTIVITY_DETAIL, NAME_INIT_OPEN_PROCESS);
        EVENT_APPLY_PARAMETERS = MMProfileWrapper.doMMProfileRegisterEvent(
            EVENT_CAMERA_ACTIVITY_DETAIL, NAME_APPLY_PARAMETERS);
        EVENT_INIT_CAMERA_PREF = MMProfileWrapper.doMMProfileRegisterEvent(
            EVENT_CAMERA_ACTIVITY_DETAIL, NAME_INIT_CAMERA_PREF);
        EVENT_SET_DISP_ORIENT = MMProfileWrapper.doMMProfileRegisterEvent(
            EVENT_CAMERA_ACTIVITY_DETAIL, NAME_SET_DISP_ORIENT);
        EVENT_SET_PREVIEW_ASPECT_RATIO = MMProfileWrapper.doMMProfileRegisterEvent(
            EVENT_CAMERA_ACTIVITY_DETAIL, NAME_SET_PREVIEW_ASPECT_RATIO);
        EVENT_NOTIFY_ORIENT_CHANGED = MMProfileWrapper.doMMProfileRegisterEvent(
            EVENT_CAMERA_ACTIVITY_DETAIL, NAME_NOTIFY_ORIENT_CHANGED);
        EVENT_SET_PREVIEW_TEXT = MMProfileWrapper.doMMProfileRegisterEvent(
            EVENT_CAMERA_ACTIVITY_DETAIL, NAME_SET_PREVIEW_TEXT);
        EVENT_RE_INFLATE_VIEW_MGR = MMProfileWrapper.doMMProfileRegisterEvent(
            EVENT_CAMERA_ACTIVITY_DETAIL, NAME_RE_INFLATE_VIEW_MGR);
        EVENT_UPDATE_SURFACE_TEXTURE = MMProfileWrapper.doMMProfileRegisterEvent(
            EVENT_CAMERA_ACTIVITY_DETAIL, NAME_UPDATE_SURFACE_TEXTURE);
        EVENT_INIT_FOCUS_MGR = MMProfileWrapper.doMMProfileRegisterEvent(
            EVENT_CAMERA_ACTIVITY_DETAIL, NAME_INIT_FOCUS_MGR);
        EVENT_LAYOUT_CHANGE = MMProfileWrapper.doMMProfileRegisterEvent(
            EVENT_CAMERA_ACTIVITY_DETAIL, NAME_LAYOUT_CHANGE);

        EVENT_SETTING_CHECKER = MMProfileWrapper.doMMProfileRegisterEvent(
            EVENT_CAMERA_ROOT, NAME_SETTING_CHECKER);
        EVENT_APPLY_PARAM_UI_IMMEDIAT = MMProfileWrapper.doMMProfileRegisterEvent(
            EVENT_SETTING_CHECKER, NAME_APPLY_PARAM_UI_IMMEDIAT);

    }

    public static void startProfileCameraOpen() {
        MMProfileWrapper.doMMProfileLog(
            EVENT_CAMERA_OPEN, 
            MMProfileWrapper.MMPROFILE_FLAG_START);
        Trace.traceBegin(Trace.TRACE_TAG_VIEW,"CameraOpen");
    }

    public static void stopProfileCameraOpen() {
        MMProfileWrapper.doMMProfileLog(
            EVENT_CAMERA_OPEN,
            MMProfileWrapper.MMPROFILE_FLAG_END);
        Trace.traceEnd(Trace.TRACE_TAG_VIEW);
    }

    public static void startProfileStartPreview() {
        MMProfileWrapper.doMMProfileLog(
            EVENT_START_PREVIEW,
            MMProfileWrapper.MMPROFILE_FLAG_START);
        Trace.traceBegin(Trace.TRACE_TAG_VIEW,"StartPreview");
    }

    public static void stopProfileStartPreview() {
        MMProfileWrapper.doMMProfileLog(
            EVENT_START_PREVIEW,
            MMProfileWrapper.MMPROFILE_FLAG_END);
        Trace.traceEnd(Trace.TRACE_TAG_VIEW);
    }

    public static void startProfileStopPreview() {
        MMProfileWrapper.doMMProfileLog(
            EVENT_STOP_PREVIEW,
            MMProfileWrapper.MMPROFILE_FLAG_START);
    }

    public static void stopProfileStopPreview() {
        MMProfileWrapper.doMMProfileLog(
            EVENT_STOP_PREVIEW,
            MMProfileWrapper.MMPROFILE_FLAG_END);
    }

    public static void startProfileCameraRelease() {
        MMProfileWrapper.doMMProfileLog(
            EVENT_CAMERA_RELEASE,
            MMProfileWrapper.MMPROFILE_FLAG_START);
    }

    public static void stopProfileCameraRelease() {
        MMProfileWrapper.doMMProfileLog(
            EVENT_CAMERA_RELEASE,
            MMProfileWrapper.MMPROFILE_FLAG_END);
    }

    public static void startProfileGetParameters() {
        MMProfileWrapper.doMMProfileLog(
            EVENT_GET_PARAMETERS,
            MMProfileWrapper.MMPROFILE_FLAG_START);
        Trace.traceBegin(Trace.TRACE_TAG_VIEW,"GetPara");
    }

    public static void stopProfileGetParameters() {
        MMProfileWrapper.doMMProfileLog(
            EVENT_GET_PARAMETERS,
            MMProfileWrapper.MMPROFILE_FLAG_END);
        Trace.traceEnd(Trace.TRACE_TAG_VIEW);
    }

    public static void startProfileSetParameters() {
        MMProfileWrapper.doMMProfileLog(
            EVENT_SET_PARAMETERS,
            MMProfileWrapper.MMPROFILE_FLAG_START);
    }

    public static void stopProfileSetParameters() {
        MMProfileWrapper.doMMProfileLog(
            EVENT_SET_PARAMETERS,
            MMProfileWrapper.MMPROFILE_FLAG_END);
    }

    public static void startProfileTakePicture() {
        MMProfileWrapper.doMMProfileLog(
            EVENT_PHOTO_TAKE_PICTURE,
            MMProfileWrapper.MMPROFILE_FLAG_START);
    }

    public static void stopProfileTakePicture() {
        MMProfileWrapper.doMMProfileLog(
            EVENT_PHOTO_TAKE_PICTURE,
            MMProfileWrapper.MMPROFILE_FLAG_END);
    }

    public static void triggerPhotoDataGrip() {
        MMProfileWrapper.doMMProfileLog(
            EVENT_PHOTO_DATA_GRIP,
            MMProfileWrapper.MMPROFILE_FLAG_PULSE);
    }

    public static void triggerPhotoShutterFocus() {
        MMProfileWrapper.doMMProfileLog(
            EVENT_PHOTO_SHUTTER_FOCUS,
            MMProfileWrapper.MMPROFILE_FLAG_PULSE);
    }

    public static void triggerPhotoShutterClick() {
        MMProfileWrapper.doMMProfileLog(
            EVENT_PHOTO_SHUTTER_CLICK,
            MMProfileWrapper.MMPROFILE_FLAG_PULSE);
    }

    public static void startProfileStorePicture() {
        MMProfileWrapper.doMMProfileLog(
            EVENT_PHOTO_STORE_PICTURE,
            MMProfileWrapper.MMPROFILE_FLAG_START);
    }

    public static void stopProfileStorePicture() {
        MMProfileWrapper.doMMProfileLog(
            EVENT_PHOTO_STORE_PICTURE,
            MMProfileWrapper.MMPROFILE_FLAG_END);
    }

    public static void triggerProfileShot2ShotAnimate() {
        MMProfileWrapper.doMMProfileLog(
            EVENT_PHOTO_SHOT2SHOT_ANIMATE,
            MMProfileWrapper.MMPROFILE_FLAG_PULSE);
    }

    public static void startProfileApplyParameterForCapture() {
        MMProfileWrapper.doMMProfileLog(
            EVENT_PHOTO_APPLY_PARAMETER_FOR_CAPTURE,
            MMProfileWrapper.MMPROFILE_FLAG_START);
    }

    public static void stopProfileApplyParameterForCapture() {
        MMProfileWrapper.doMMProfileLog(
            EVENT_PHOTO_APPLY_PARAMETER_FOR_CAPTURE,
            MMProfileWrapper.MMPROFILE_FLAG_END);
    }

    public static void startProfileSwitchCamera() {
        MMProfileWrapper.doMMProfileLog(
            EVENT_SWITCH_CAMERA,
            MMProfileWrapper.MMPROFILE_FLAG_START);
    }

    public static void stopProfileSwitchCamera() {
        MMProfileWrapper.doMMProfileLog(
            EVENT_SWITCH_CAMERA,
            MMProfileWrapper.MMPROFILE_FLAG_END);
    }
    public static void triggerTouchFocus() {
        MMProfileWrapper.doMMProfileLog(
            EVENT_AUTO_FOCUS,
            MMProfileWrapper.MMPROFILE_FLAG_PULSE);
    }
    
    public static void triggerContinueAutoFocus() {
        MMProfileWrapper.doMMProfileLog(
            EVENT_CONTINUE_AUTO_FOCUS,
            MMProfileWrapper.MMPROFILE_FLAG_PULSE);
    }
    public static void triggerVideoShutterClick() {
        MMProfileWrapper.doMMProfileLog(
            EVENT_VIDEO_SHUTTER_CLICK,
            MMProfileWrapper.MMPROFILE_FLAG_PULSE);
    }
    
    public static void triggerVideoShutterFocus() {
        MMProfileWrapper.doMMProfileLog(
            EVENT_VIDEO_SHUTTER_FOCUS,
            MMProfileWrapper.MMPROFILE_FLAG_PULSE);
    }

    public static void startProfileStartVideoRecording() {
        MMProfileWrapper.doMMProfileLog(
            EVENT_VIDEO_START_RECORD,
            MMProfileWrapper.MMPROFILE_FLAG_START);
    }

    public static void stopProfileStartVideoRecording() {
        MMProfileWrapper.doMMProfileLog(
            EVENT_VIDEO_START_RECORD,
            MMProfileWrapper.MMPROFILE_FLAG_END);
    }

    public static void startProfileStopVideoRecording() {
        MMProfileWrapper.doMMProfileLog(
            EVENT_VIDEO_STOP_RECORD,
            MMProfileWrapper.MMPROFILE_FLAG_START);
    }

    public static void stopProfileStopVideoRecording() {
        MMProfileWrapper.doMMProfileLog(
            EVENT_VIDEO_STOP_RECORD,
            MMProfileWrapper.MMPROFILE_FLAG_END);
    }

    public static void startProfileStoreVideo() {
        MMProfileWrapper.doMMProfileLog(
            EVENT_VIDEO_STORE_VIDEO,
            MMProfileWrapper.MMPROFILE_FLAG_START);
    }

    public static void stopProfileStoreVideo() {
        MMProfileWrapper.doMMProfileLog(
            EVENT_VIDEO_STORE_VIDEO,
            MMProfileWrapper.MMPROFILE_FLAG_END);
    }

    public static void startProfileInitMediarecorder() {
        MMProfileWrapper.doMMProfileLog(
            EVENT_VIDEO_INIT_MEDIARECORDER,
            MMProfileWrapper.MMPROFILE_FLAG_START);
    }

    public static void stopProfileInitMediarecorder() {
        MMProfileWrapper.doMMProfileLog(
            EVENT_VIDEO_INIT_MEDIARECORDER,
            MMProfileWrapper.MMPROFILE_FLAG_END);
    }

    public static void startProfileMediarecorderStart() {
        MMProfileWrapper.doMMProfileLog(
            EVENT_VIDEO_MEDIARECORDER_START,
            MMProfileWrapper.MMPROFILE_FLAG_START);
    }

    public static void stopProfileMediarecorderStart() {
        MMProfileWrapper.doMMProfileLog(
            EVENT_VIDEO_MEDIARECORDER_START,
            MMProfileWrapper.MMPROFILE_FLAG_END);
    }

    public static void triggerUpdateRecordingTime() {
        MMProfileWrapper.doMMProfileLog(
            EVENT_VIDEO_UPDATE_RECORDING_TIME,
            MMProfileWrapper.MMPROFILE_FLAG_PULSE);
    }

    public static void startProfileEffectrecorderStart() {
        MMProfileWrapper.doMMProfileLog(
            EVENT_VIDEO_EFFECTRECORDER_START,
            MMProfileWrapper.MMPROFILE_FLAG_START);
    }

    public static void stopProfileEffectrecorderStart() {
        MMProfileWrapper.doMMProfileLog(
            EVENT_VIDEO_EFFECTRECORDER_START,
            MMProfileWrapper.MMPROFILE_FLAG_END);
    }

    public static void triggerFrameAvailable() {
        Trace.traceCounter(Trace.TRACE_TAG_VIEW,"triggerFrameAvailable", 1);
        MMProfileWrapper.doMMProfileLog(
            EVENT_FRAME_AVAILABLE,
            MMProfileWrapper.MMPROFILE_FLAG_PULSE);
        Trace.traceCounter(Trace.TRACE_TAG_VIEW,"triggerFrameAvailable", 0);
    }

    public static void triggerFirstFrameAvailable() {
        MMProfileWrapper.doMMProfileLog(
            EVENT_FIRST_FRAME_AVAILABLE,
            MMProfileWrapper.MMPROFILE_FLAG_PULSE);
        Trace.traceBegin(Trace.TRACE_TAG_VIEW,"FrameAvailable");
    }

    public static void startProfileFirstFrameAvailable() {
        MMProfileWrapper.doMMProfileLog(
            EVENT_FIRST_FRAME_AVAILABLE,
            MMProfileWrapper.MMPROFILE_FLAG_START);
        Trace.traceEnd(Trace.TRACE_TAG_VIEW);
    }

    public static void stopProfileFirstFrameAvailable() {
        MMProfileWrapper.doMMProfileLog(
            EVENT_FIRST_FRAME_AVAILABLE,
            MMProfileWrapper.MMPROFILE_FLAG_END);
    }

    public static void triggerRequestRender() {
        MMProfileWrapper.doMMProfileLog(
            EVENT_REQUEST_RENDER,
            MMProfileWrapper.MMPROFILE_FLAG_PULSE);
    }

    public static void startProfileDrawScreenNail() {
        MMProfileWrapper.doMMProfileLog(
            EVENT_DRAW_SCREEN_NAIL,
            MMProfileWrapper.MMPROFILE_FLAG_START);
        Trace.traceBegin(Trace.TRACE_TAG_VIEW,"DrawScreenNail");
    }

    public static void stopProfileDrawScreenNail() {
        MMProfileWrapper.doMMProfileLog(
            EVENT_DRAW_SCREEN_NAIL,
            MMProfileWrapper.MMPROFILE_FLAG_END);
        Trace.traceEnd(Trace.TRACE_TAG_VIEW);
    }

    
    public static void startProfileAnimateCapture() {
            MMProfileWrapper.doMMProfileLog(
                EVENT_ANIMATE_CAPTURE,
                MMProfileWrapper.MMPROFILE_FLAG_START);
    }
    
    public static void stopProfileAnimateCapture() {
            MMProfileWrapper.doMMProfileLog(
                EVENT_ANIMATE_CAPTURE,
                MMProfileWrapper.MMPROFILE_FLAG_END);
    }


    public static void startProfileAnimateSwitchCamera() {
            MMProfileWrapper.doMMProfileLog(
                EVENT_ANIMATE_SWITCH_CAMERA,
                MMProfileWrapper.MMPROFILE_FLAG_START);
    }
    
    public static void stopProfileAnimateSwitchCamera() {
            MMProfileWrapper.doMMProfileLog(
                EVENT_ANIMATE_SWITCH_CAMERA,
                MMProfileWrapper.MMPROFILE_FLAG_END);
    }
    
    public static void triggerSuperDrawOriginFrame() {
        MMProfileWrapper.doMMProfileLog(
            EVENT_SUPER_DRAW_ORIGIN_FRAME,
            MMProfileWrapper.MMPROFILE_FLAG_PULSE);
    }

    public static void triggerSuperDrawSizeChange() {
        MMProfileWrapper.doMMProfileLog(
            EVENT_SUPER_DRAW_SIZE_CHANGE,
            MMProfileWrapper.MMPROFILE_FLAG_PULSE);
    }

    public static void triggerSuperDrawNoAnimate() {
        MMProfileWrapper.doMMProfileLog(
            EVENT_SUPER_DRAW_NO_ANIMATE,
            MMProfileWrapper.MMPROFILE_FLAG_PULSE);
    }

    public static void triggerNotifyServerSelfChange() {
        MMProfileWrapper.doMMProfileLog(
            EVENT_NOTIFY_SERVER_SELF_CHANGE,
            MMProfileWrapper.MMPROFILE_FLAG_PULSE);
    }

    public static void startProfileCameraOnCreate() {
        MMProfileWrapper.doMMProfileLog(
            EVENT_CAMERA_ON_CREATE,
            MMProfileWrapper.MMPROFILE_FLAG_START);
        Trace.traceBegin(Trace.TRACE_TAG_VIEW,"CameraOncreate");
    }

    public static void stopProfileCameraOnCreate() {
        MMProfileWrapper.doMMProfileLog(
            EVENT_CAMERA_ON_CREATE,
            MMProfileWrapper.MMPROFILE_FLAG_END);
        Trace.traceEnd(Trace.TRACE_TAG_VIEW);
    }

    public static void startProfileCameraOnResume() {
        MMProfileWrapper.doMMProfileLog(
            EVENT_CAMERA_ON_RESUME,
            MMProfileWrapper.MMPROFILE_FLAG_START);
        Trace.traceBegin(Trace.TRACE_TAG_VIEW,"CameraOnResume");
    }

    public static void stopProfileCameraOnResume() {
        MMProfileWrapper.doMMProfileLog(
            EVENT_CAMERA_ON_RESUME,
            MMProfileWrapper.MMPROFILE_FLAG_END);
        Trace.traceEnd(Trace.TRACE_TAG_VIEW);
    }

    public static void startProfileCameraOnPause() {
        MMProfileWrapper.doMMProfileLog(
            EVENT_CAMERA_ON_PAUSE,
            MMProfileWrapper.MMPROFILE_FLAG_START);
    }

    public static void stopProfileCameraOnPause() {
        MMProfileWrapper.doMMProfileLog(
            EVENT_CAMERA_ON_PAUSE,
            MMProfileWrapper.MMPROFILE_FLAG_END);
    }

    public static void startProfileCameraOnDestroy() {
        MMProfileWrapper.doMMProfileLog(
            EVENT_CAMERA_ON_DESTROY,
            MMProfileWrapper.MMPROFILE_FLAG_START);
    }

    public static void stopProfileCameraOnDestroy() {
        MMProfileWrapper.doMMProfileLog(
            EVENT_CAMERA_ON_DESTROY,
            MMProfileWrapper.MMPROFILE_FLAG_END);
    }
    
    public static void startProfileCameraExitByBackKey() {
        MMProfileWrapper.doMMProfileLog(
            EVENT_CAMERA_EXIT_BY_BACK_KEY,
            MMProfileWrapper.MMPROFILE_FLAG_START);
    }

    public static void stopProfileCameraExitByBackKey() {
        MMProfileWrapper.doMMProfileLog(
            EVENT_CAMERA_EXIT_BY_BACK_KEY,
            MMProfileWrapper.MMPROFILE_FLAG_END);
    }
    
    public static void startProfileCameraGalleryBackKey() {
        MMProfileWrapper.doMMProfileLog(
            EVENT_CAMERA_GALLERY_BACK_KEY,
            MMProfileWrapper.MMPROFILE_FLAG_START);
    }

    public static void stopProfileCameraGalleryBackKey() {
        MMProfileWrapper.doMMProfileLog(
            EVENT_CAMERA_GALLERY_BACK_KEY,
            MMProfileWrapper.MMPROFILE_FLAG_END);
    }

    public static void startProfileCameraStartUp() {
        MMProfileWrapper.doMMProfileLog(
            EVENT_CAMERA_START_UP,
            MMProfileWrapper.MMPROFILE_FLAG_START);
        Trace.traceBegin(Trace.TRACE_TAG_VIEW,"CameraStartUp");
    }

    public static void stopProfileCameraStartUp() {
        MMProfileWrapper.doMMProfileLog(
            EVENT_CAMERA_START_UP,
            MMProfileWrapper.MMPROFILE_FLAG_END);
        Trace.traceEnd(Trace.TRACE_TAG_VIEW);
    }

    public static void startProfileCameraOnConfigChange() {
        MMProfileWrapper.doMMProfileLog(
            EVENT_CAMERA_ON_CONFIG_CHANGE,
            MMProfileWrapper.MMPROFILE_FLAG_START);
    }

    public static void stopProfileCameraOnConfigChange() {
        MMProfileWrapper.doMMProfileLog(
            EVENT_CAMERA_ON_CONFIG_CHANGE,
            MMProfileWrapper.MMPROFILE_FLAG_END);
    }
    
    public static void startProfileCameraOnOrientChange() {
        MMProfileWrapper.doMMProfileLog(
            EVENT_CAMERA_ON_ORIENT_CHANGE,
            MMProfileWrapper.MMPROFILE_FLAG_START);
    }

    public static void stopProfileCameraOnOrientChange() {
        MMProfileWrapper.doMMProfileLog(
            EVENT_CAMERA_ON_ORIENT_CHANGE,
            MMProfileWrapper.MMPROFILE_FLAG_END);
    }
    
    public static void startProfileHandleMessage(String str) {
        MMProfileWrapper.doMMProfileLogMetaString(
            EVENT_CAMERA_HANDLE_MSG,
            MMProfileWrapper.MMPROFILE_FLAG_START, str);
    }

    public static void stopProfileHandleMessage() {
        MMProfileWrapper.doMMProfileLog(
            EVENT_CAMERA_HANDLE_MSG,
            MMProfileWrapper.MMPROFILE_FLAG_END);
    }

    public static void triggersSendMessage(String str) {
        MMProfileWrapper.doMMProfileLogMetaString(
            EVENT_CAMERA_SEND_MSG,
            MMProfileWrapper.MMPROFILE_FLAG_PULSE, str);
    }

    // for camera detail operations

    public static void startProfileCameraViewOperation() {
        MMProfileWrapper.doMMProfileLog(
            EVENT_CAMERA_VIEW_OPERATION,
            MMProfileWrapper.MMPROFILE_FLAG_START);
    }

    public static void stopProfileCameraViewOperation() {
        MMProfileWrapper.doMMProfileLog(
            EVENT_CAMERA_VIEW_OPERATION,
            MMProfileWrapper.MMPROFILE_FLAG_END);
    }

    public static void startProfileCameraParameterCopy() {
        MMProfileWrapper.doMMProfileLog(
            EVENT_CAMERA_PARAMETER_COPY,
            MMProfileWrapper.MMPROFILE_FLAG_START);
    }

    public static void stopProfileCameraParameterCopy() {
        MMProfileWrapper.doMMProfileLog(
            EVENT_CAMERA_PARAMETER_COPY,
            MMProfileWrapper.MMPROFILE_FLAG_END);
    }

    public static void startProfileCameraPreviewPreReadyBlock() {
        MMProfileWrapper.doMMProfileLog(
            EVENT_CAMERA_PREVIEW_PRE_READY_BLOCK,
            MMProfileWrapper.MMPROFILE_FLAG_START);
    }

    public static void stopProfileCameraPreviewPreReadyBlock() {
        MMProfileWrapper.doMMProfileLog(
            EVENT_CAMERA_PREVIEW_PRE_READY_BLOCK,
            MMProfileWrapper.MMPROFILE_FLAG_END);
    }

    public static void startProfileCameraPreviewPreReadyOpen() {
        MMProfileWrapper.doMMProfileLog(
            EVENT_CAMERA_PREVIEW_PRE_READY_OPEN,
            MMProfileWrapper.MMPROFILE_FLAG_START);
    }

    public static void stopProfileCameraPreviewPreReadyOpen() {
        MMProfileWrapper.doMMProfileLog(
            EVENT_CAMERA_PREVIEW_PRE_READY_OPEN,
            MMProfileWrapper.MMPROFILE_FLAG_END);
    }

    public static void startProfileInitOpeningProcess() {
        MMProfileWrapper.doMMProfileLog(
            EVENT_INIT_OPEN_PROCESS,
            MMProfileWrapper.MMPROFILE_FLAG_START);
    }

    public static void stopProfileInitOpeningProcess() {
        MMProfileWrapper.doMMProfileLog(
            EVENT_INIT_OPEN_PROCESS,
            MMProfileWrapper.MMPROFILE_FLAG_END);
    }

    public static void startProfileApplyParameters() {
        MMProfileWrapper.doMMProfileLog(
            EVENT_APPLY_PARAMETERS,
            MMProfileWrapper.MMPROFILE_FLAG_START);
        Trace.traceBegin(Trace.TRACE_TAG_VIEW,"ApplyParameters");
    }

    public static void stopProfileApplyParameters() {
        MMProfileWrapper.doMMProfileLog(
            EVENT_APPLY_PARAMETERS,
            MMProfileWrapper.MMPROFILE_FLAG_END);
        Trace.traceEnd(Trace.TRACE_TAG_VIEW);
    }

    public static void startProfileInitPref() {
        MMProfileWrapper.doMMProfileLog(
            EVENT_INIT_CAMERA_PREF,
            MMProfileWrapper.MMPROFILE_FLAG_START);
    }

    public static void stopProfileInitPref() {
        MMProfileWrapper.doMMProfileLog(
            EVENT_INIT_CAMERA_PREF,
            MMProfileWrapper.MMPROFILE_FLAG_END);
    }

    public static void startProfileSetDisplayOrientation() {
        MMProfileWrapper.doMMProfileLog(
            EVENT_SET_DISP_ORIENT,
            MMProfileWrapper.MMPROFILE_FLAG_START);
    }

    public static void stopProfileSetDisplayOrientation() {
        MMProfileWrapper.doMMProfileLog(
            EVENT_SET_DISP_ORIENT,
            MMProfileWrapper.MMPROFILE_FLAG_END);
    }

    public static void triggerSetPreviewAspectRatio() {
        MMProfileWrapper.doMMProfileLog(
            EVENT_SET_PREVIEW_ASPECT_RATIO,
            MMProfileWrapper.MMPROFILE_FLAG_PULSE);
    }

    public static void startProfileNotifyOrientChanged() {
        MMProfileWrapper.doMMProfileLog(
            EVENT_NOTIFY_ORIENT_CHANGED,
            MMProfileWrapper.MMPROFILE_FLAG_START);
    }

    public static void stopProfileNotifyOrientChanged() {
        MMProfileWrapper.doMMProfileLog(
            EVENT_NOTIFY_ORIENT_CHANGED,
            MMProfileWrapper.MMPROFILE_FLAG_END);
    }

    public static void triggerSetPreviewTexture() {
        MMProfileWrapper.doMMProfileLog(
            EVENT_SET_PREVIEW_TEXT,
            MMProfileWrapper.MMPROFILE_FLAG_PULSE);
    }

    public static void startProfileReInflateViewManager() {
        MMProfileWrapper.doMMProfileLog(
            EVENT_RE_INFLATE_VIEW_MGR,
            MMProfileWrapper.MMPROFILE_FLAG_START);
    }

    public static void stopProfileReInflateViewManager() {
        MMProfileWrapper.doMMProfileLog(
            EVENT_RE_INFLATE_VIEW_MGR,
            MMProfileWrapper.MMPROFILE_FLAG_END);
    }

    public static void startProfileUpdateSurfaceTexture() {
        MMProfileWrapper.doMMProfileLog(
            EVENT_UPDATE_SURFACE_TEXTURE,
            MMProfileWrapper.MMPROFILE_FLAG_START);
    }

    public static void stopProfileUpdateSurfaceTexture() {
        MMProfileWrapper.doMMProfileLog(
            EVENT_UPDATE_SURFACE_TEXTURE,
            MMProfileWrapper.MMPROFILE_FLAG_END);
    }

    public static void startProfileInitFocusManager() {
        MMProfileWrapper.doMMProfileLog(
            EVENT_INIT_FOCUS_MGR,
            MMProfileWrapper.MMPROFILE_FLAG_START);
    }

    public static void stopProfileInitFocusManager() {
        MMProfileWrapper.doMMProfileLog(
            EVENT_INIT_FOCUS_MGR,
            MMProfileWrapper.MMPROFILE_FLAG_END);
    }

    public static void triggerProfileLayoutChange(String str) {
        MMProfileWrapper.doMMProfileLogMetaString(
            EVENT_LAYOUT_CHANGE,
            MMProfileWrapper.MMPROFILE_FLAG_PULSE, str);
    }



    // for Setting Checker

    public static void startProfileApplyParamsToUiImmediately() {
        MMProfileWrapper.doMMProfileLog(
            EVENT_APPLY_PARAM_UI_IMMEDIAT,
            MMProfileWrapper.MMPROFILE_FLAG_START);
    }

    public static void stopProfileApplyParamsToUiImmediately() {
        MMProfileWrapper.doMMProfileLog(
            EVENT_APPLY_PARAM_UI_IMMEDIAT,
            MMProfileWrapper.MMPROFILE_FLAG_END);
    }


    // wrapper class for MMProfile.
    // this class will be most useful during migration
    private static class MMProfileWrapper {
        private static final int MMP_ROOT_EVENT = MMProfile.MMP_RootEvent;
        private static final int MMPROFILE_FLAG_START = MMProfile.MMProfileFlagStart;
        private static final int MMPROFILE_FLAG_END = MMProfile.MMProfileFlagEnd;
        private static final int MMPROFILE_FLAG_PULSE = MMProfile.MMProfileFlagPulse;
        private static final int MMPROFILE_FLAG_EVENT_SEPARATOR = MMProfile.MMProfileFlagEventSeparator;

        public static int doMMProfileRegisterEvent(int parent, String name) {
            return MMProfile.MMProfileRegisterEvent(parent, name);
        }
        public static int doMMProfileFindEvent(int parent, String name) {
            return MMProfile.MMProfileFindEvent(parent, name);
        }
        public static void doMMProfileEnableEvent(int event, int enable) {
            MMProfile.MMProfileEnableEvent(event, enable);
        }
        public static void doMMProfileEnableEventRecursive(int event, int enable) {
            MMProfile.MMProfileEnableEventRecursive(event, enable);
        }
        public static int doMMProfileQueryEnable(int event) {
            return MMProfile.MMProfileQueryEnable(event);
        }
        public static void doMMProfileLog(int event, int type) {
            MMProfile.MMProfileLog(event, type);
        }
        public static void doMMProfileLogEx(int event, int type, int data1, int data2) {
            MMProfile.MMProfileLogEx(event, type, data1, data2);
        }
        public static int doMMProfileLogMetaString(int event, int type, String str) {
            return MMProfile.MMProfileLogMetaString(event, type, str);
        }
        public static int doMMProfileLogMetaStringEx(int event, int type, int data1, int data2, String str) {
            return MMProfile.MMProfileLogMetaStringEx(event, type, data1, data2, str);
        }
    }
}
