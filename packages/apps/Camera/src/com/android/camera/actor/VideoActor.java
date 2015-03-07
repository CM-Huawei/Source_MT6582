package com.android.camera.actor;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ContentResolver;
import android.content.Intent;
import android.filterpacks.videosink.MediaRecorderStopException;
import android.hardware.Camera.CameraInfo;
import android.hardware.Camera.ErrorCallback;
import android.hardware.Camera.Parameters;
import android.hardware.Camera.PictureCallback;
import android.location.Location;
import android.media.CamcorderProfile;
import android.media.MediaMetadataRetriever;
import android.media.MediaRecorder;
import android.media.MediaRecorder.HDRecordMode;
import android.net.Uri;
import com.android.camera.manager.ModePicker;
import android.os.Handler;
import android.os.Message;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;
import android.os.SystemProperties;
import android.provider.MediaStore.Video;
import android.view.KeyEvent;
import android.view.OrientationEventListener;
import android.view.View;
import android.view.View.OnClickListener;

import com.android.camera.Camera;
import com.android.camera.Camera.OnSingleTapUpListener;
import com.android.camera.CameraErrorCallback;
import com.android.camera.CameraHolder;
import com.android.camera.FeatureSwitcher;
import com.android.camera.FileSaver;
import com.android.camera.FocusManager;
import com.android.camera.FocusManager.Listener;
import com.android.camera.Log;
import com.android.camera.R;
import com.android.camera.SaveRequest;
import com.android.camera.Storage;
import com.android.camera.Util;
import com.android.camera.WfdManagerLocal;
import com.android.camera.manager.MMProfileManager;
import com.android.camera.manager.ModePicker;
import com.android.camera.manager.RecordingView;
import com.android.camera.manager.ShutterManager;
import com.android.camera.ui.ShutterButton;
import com.android.camera.ui.ShutterButton.OnShutterButtonListener;

import com.mediatek.camera.FrameworksClassFactory;
import com.mediatek.camera.ext.ExtensionHelper;
import com.mediatek.mock.media.MockMediaRecorder;
import com.mediatek.media.MediaRecorderEx;
import com.mediatek.common.featureoption.FeatureOption;

import java.io.File;
import java.io.FileDescriptor;
import java.io.IOException;
import java.util.List;

public class VideoActor extends CameraActor implements MediaRecorder.OnErrorListener,
        MediaRecorder.OnInfoListener, FocusManager.Listener {
    private static final String TAG = "VideoActor";
    protected int mSaveTempVideo = SystemProperties.getInt("camera.save.temp.video", 0);
    private static final String CONTENT_URI_PREFIX = "content://mms_temp_file";
    protected Camera mVideoContext;
    protected MediaRecorder mMediaRecorder;
    
    public CamcorderProfile mProfile;
    private ContentResolver mContentResolver;
    private Location mStartLocation;
    public ParcelFileDescriptor mVideoFileDescriptor;
    protected RecordingView mRecordingView;
    private SaveRequest mVideoSaveRequest;
    public String mVideoFilename = null;
    protected String mCurrentVideoFilename;
    private String mVideoTempPath;
    public Thread mVideoSavingTask;
    private Uri mCurrentVideoUri;
    private CameraErrorCallback mErrorCallback = new CameraErrorCallback();

    public boolean mMediaRecorderRecording = false;
    protected boolean mMediaRecoderRecordingPaused = false;
    public boolean mRecorderCameraReleased = true;
    public boolean mVideoCameraClosed = false;
    public boolean mCallFromOnPause = false;
    public boolean mFilterStartEncode = false;
    // Time Lapse parameters.
    public boolean mCaptureTimeLapse = false;
    public boolean mRecordAudio = false;
    public boolean mStartRecordingFailed = false;
    private long mVideoRecordedDuration = 0;
    private long mRecordingStartTime;
    private long mFocusStartTime;
    private int mCurrentShowIndicator = 0;
    public int mStoppingAction = STOP_NORMAL;
    // The video duration limit. 0 menas no limit.
    public int mMaxVideoDurationInMs;
    // Default 0. If it is larger than 0, the camcorder is in time lapse mode.
    public int mTimeBetweenTimeLapseFrameCaptureMs = 0;
    public int mOrientation = OrientationEventListener.ORIENTATION_UNKNOWN;
    private final AutoFocusCallback mAutoFocusCallback = new AutoFocusCallback();
    public final Handler mHandler = new MainHandler();

    protected static final int STOP_NORMAL = 1;
    protected static final int STOP_RETURN = 2;
    protected static final int STOP_RETURN_UNVALID = 3;
    protected static final int STOP_SHOW_ALERT = 4;
    protected static final int STOP_FAIL = 5;
    private static final int UPDATE_RECORD_TIME = 5;
    protected static final long INVALID_DURATION = -1l;
    protected static final long FILE_ERROR = -12;
    public static final int PARAMETER_CHANGE_DONE = 103;
    private static final int MEDIA_RECORDER_INFO_RECORDING_SIZE = 895;
    private static final String[] PREF_CAMERA_VIDEO_HD_RECORDING_ENTRYVALUES = { "normal", "indoor" };

    // touch AE/AF
    private boolean mIsAutoFocusCallback = false;
    private boolean mSingleStartRecording = false;
    protected boolean mSingleAutoModeSupported;
    private boolean mIsContinousFocusMode;
    private int mFocusState = 0;
    private final AutoFocusMoveCallback mAutoFocusMoveCallback = new AutoFocusMoveCallback();
    private static final int FOCUSING = 1;
    private static final int FOCUSED = 2;
    private static final int FOCUS_IDLE = 3;
    private static final int START_FOCUSING = -1;
    private static final int INIT_SHUTTER_STATUS = 6;
    // Snapshot feature
    private Uri mSnapUri;
    private SaveRequest mPhotoSaveRequest;
    private boolean mStopVideoRecording = false;
    private static final int UPDATE_SNAP_UI = 15;

    //MMS progress bar
    public long mTotalSize = 0;
    protected long mRequestedSizeLimit = 0;

    //WFD
    protected WfdManagerLocal mWfdManager;
    private boolean mWfdListenerEnabled = false;
	private static final int SLOW_MOTION_VIDEO_FILE_DEFAULT_FPS = 30;
    //use to tag whether need to ignore backtolastMode or not
    private boolean mNeedBackToLastMode = true;
    
    private OnShutterButtonListener mVideoShutterListener = new OnShutterButtonListener() {
        @Override
        public void onShutterButtonLongPressed(ShutterButton button) {
            Log.i(TAG, "Video.onShutterButtonLongPressed(" + button + ")");
        }
        @Override
        public void onShutterButtonFocus(ShutterButton button, boolean pressed) {
            Log.i(TAG, "Video.onShutterButtonFocus(" + button + ", " + pressed + ")");
            if (FeatureSwitcher.isContinuousFocusEnabledWhenTouch()) {
                if (!pressed) {
                    mVideoContext.getFocusManager().onShutterUp();
                }
            }
        }

        @Override
        public void onShutterButtonClick(ShutterButton button) {
            Log.i(TAG, "Video.onShutterButtonClick(" + button + ") mMediaRecorderRecording=" + mMediaRecorderRecording);
            //when recording,should initialize the parameter:mIgnoreBackToLastMode to true
            mNeedBackToLastMode = true;
            // Do not recording if there is not enough storage.
            if (Storage.getLeftSpace() <= 0) {
                backToLastModeIfNeed();
                return;
            }
            if (mVideoCameraClosed) {
                return;
            }
            if (mMediaRecorderRecording) {
                MMProfileManager.startProfileStopVideoRecording();
                onStopVideoRecordingAsync();
                MMProfileManager.stopProfileStopVideoRecording();
            } else {
                MMProfileManager.startProfileStartVideoRecording();
                mVideoContext.setSwipingEnabled(false);
                startVideoRecording();
                MMProfileManager.stopProfileStartVideoRecording();
                // we should enable swiping when cannot record
                if (!mMediaRecorderRecording) {
                    mVideoContext.setSwipingEnabled(true);
                }
            }
        }
    };

    private OnShutterButtonListener mPhotoShutterListener = new OnShutterButtonListener() {
        @Override
        public void onShutterButtonLongPressed(ShutterButton button) {
            Log.i(TAG, "Photo.onShutterButtonLongPressed(" + button + ")");
        }

        @Override
        public void onShutterButtonFocus(ShutterButton button, boolean pressed) {
            Log.i(TAG, "Photo.onShutterButtonFocus(" + button + ", " + pressed + ")");
        }

        @Override
        public void onShutterButtonClick(ShutterButton button) {
            Log.i(TAG, "Photo.onShutterButtonClick(" + button + ")");
            if (mStopVideoRecording) {
                return;
            }
            Log.i(TAG, "Video snapshot start");
            mPhotoSaveRequest = mVideoContext.preparePhotoRequest();
            mVideoContext.getCameraDevice().takePicture(null, null, null,
                    new JpegPictureCallback(mPhotoSaveRequest.getLocation()));
            showVideoSnapshotUI(true);
        }
    };

    public VideoActor(Camera context) {
        super(context);
        //first time need set mIgnoreBackToLastMode to true
        mNeedBackToLastMode = true;
        mVideoContext = getContext();
        initializeShutterType();
        mRecordingView = new RecordingView(mVideoContext);
        mRecordingView.setListener(mVideoPauseResumeListner);
        mWfdManager = mVideoContext.getWfdManagerLocal();
        if (mWfdManager != null) {
            mWfdManager.addListener(mWfdListener);
        }
        Log.i(TAG, "VideoActor Contructor end");
    }
    /**
     * decide shutter button's type(video,photo_video,cancel_video,etc)
     */
    public void initializeShutterType() {
        if (!mVideoContext.isNonePickIntent()) {
            mVideoContext.switchShutter(ShutterManager.SHUTTER_TYPE_VIDEO);
        } else {
        	if (mVideoContext.getSettingChecker().isSlowMotion()) {
                mVideoContext.switchShutter(ShutterManager.SHUTTER_TYPE_SLOW_VIDEO);
            } else {
            mVideoContext.switchShutter(ShutterManager.SHUTTER_TYPE_PHOTO_VIDEO);
        }
    }
    }
    
    /**
     * decide shutter button's status(disable or enable)
     */
    public void initializeShutterStatus() {
        int mFrontCameraId = CameraHolder.instance().getFrontCameraId();
        if (mVideoContext.getCameraId() == mFrontCameraId || mVideoContext.getSettingChecker().isSlowMotion()) {
            //front camera did not support snapshot
            mVideoContext.getShutterManager().setPhotoShutterEnabled(false);
        } else {
            //should get supported status from parameter
            //that's why we should do initializeShutterStatus in onCameraParametersReady
            mVideoContext.getShutterManager().setPhotoShutterEnabled(FeatureSwitcher.isVssEnable() || mVideoContext.getParameters().isVideoSnapshotSupported());
        }
    }

    protected WfdManagerLocal.Listener mWfdListener = new WfdManagerLocal.Listener() {
        @Override
        public void onStateChanged(boolean enabled) {

            Log.i(TAG, "onStateChanged(" + enabled + ")");
            mWfdListenerEnabled = enabled;
            if (enabled && mMediaRecorderRecording) {
                onStopVideoRecordingAsync();
            } else {
                Log.i(TAG, "mWfdListener, enabled = " + enabled + ",mMediaRecorderRecording = "
                        + mMediaRecorderRecording);
            }
        }
    };
    
    protected boolean isWfdEnable() {
        return mWfdListenerEnabled || mVideoContext.getWfdManagerLocal().isWfdEnabled();
    }

    @Override
    public int getMode() {
        return ModePicker.MODE_VIDEO;
    }

    private OnSingleTapUpListener mTapupListener = new OnSingleTapUpListener() {
        public void onSingleTapUp(View view, int x, int y) {
            String focusMode = null;
            if (mVideoContext.getFocusManager() != null) {
                focusMode = mVideoContext.getFocusManager().getCurrentFocusMode(mVideoContext);
            }
            if (focusMode == null || (Parameters.FOCUS_MODE_INFINITY.equals(focusMode))
                    || (Parameters.FOCUS_MODE_CONTINUOUS_PICTURE.equals(focusMode))) {
                return;
            }
            if (mVideoCameraClosed || mVideoContext.getCameraDevice() == null
                    || (mVideoContext.getCameraState() == Camera.STATE_PREVIEW_STOPPED)) {
                return;
            }
            // Check if focus area is supported.
            if (!(mVideoContext.getFocusManager().getFocusAreaSupported())) {
                return;
            }
            Log.i(TAG, "onSingleTapUp(" + x + ", " + y + ")" + ",focusMode = " + focusMode
                    + ",mVideoContext.getCameraState() = " + mVideoContext.getCameraState()
                    + "mVideoContext.getViewState() = " + mVideoContext.getViewState()
                    + ",mMediaRecorderRecording = " + mMediaRecorderRecording);
            if (mMediaRecorderRecording) {
                setFocusState(START_FOCUSING);
            }
            mVideoContext.getFocusManager().onSingleTapUp(x, y);
        }
    };

    public OnSingleTapUpListener getonSingleTapUpListener() {
        return mTapupListener;
    }

    @Override
    public OnShutterButtonListener getVideoShutterButtonListener() {
        Log.i(TAG, "getVideoShutterButtonListener" + mVideoShutterListener);
        return mVideoShutterListener;
    }

    @Override
    public OnShutterButtonListener getPhotoShutterButtonListener() {
        Log.i(TAG, "getPhotoShutterButtonListener" + mPhotoShutterListener);
        return mPhotoShutterListener;
    }
    
    @Override
    public void onCameraParameterReady(boolean startPreview) {
        Log.i(TAG, "onCameraParameterReady(" + startPreview + ") getCameraState()=" + mVideoContext.getCameraState());
        mHandler.sendEmptyMessage(INIT_SHUTTER_STATUS);
        mVideoCameraClosed = false;
        mProfile = mVideoContext.getProfile();
        Util.checkNotNull(mProfile);
        initVideoRecordingFirst();
        if (startPreview) {
            startPreview();
        }
        restoreReviewIfNeed();
    }
    
    private void restoreReviewIfNeed() {
        if (mVideoContext.getReviewManager().isShowing()) {
            //reopen file descriptor and show it(don't change view state in this case).
            if (!mVideoContext.isNonePickIntent() && mVideoFileDescriptor == null) {
                Uri saveUri = mVideoContext.getSaveUri();
                if (saveUri != null) {
                    try {
                        if(saveUri.toString().startsWith(CONTENT_URI_PREFIX)) {
                            mVideoFileDescriptor = mContentResolver.openFileDescriptor(saveUri, "r");
                        } else {
                            mVideoFileDescriptor = mContentResolver.openFileDescriptor(saveUri, "rw");
                        }
                    } catch (java.io.FileNotFoundException ex) {
                        Log.e(TAG, "initializeNormalRecorder()", ex);
                    }
                }
            }
            mVideoContext.runOnUiThread(new Runnable() {
               @Override
                public void run() {
                   if (mVideoFileDescriptor != null) {
                       mVideoContext.getReviewManager().show(mVideoFileDescriptor.getFileDescriptor());
                   } else if (mCurrentVideoFilename != null) {
                       mVideoContext.getReviewManager().show(mCurrentVideoFilename);
                   }
                } 
            });
        }
        Log.d(TAG, "restoreReviewIfNeed() review show=" + mVideoContext.getReviewManager().isShowing() +
                " mVideoFileDescriptor=" + mVideoFileDescriptor + ", mCurrentVideoFilename=" + mCurrentVideoFilename);
    }

    public void startPreview() {
        Log.i(TAG, "startPreview");
        mVideoContext.runOnUiThread(new Runnable() {
            public void run() {
                mVideoContext.getFocusManager().resetTouchFocus();
            }
        });
        stopPreview();
        if (mVideoContext.isNonePickIntent()) {
            mVideoContext.applyContinousCallback();
        }
        doStartPreview();
    }
    
    public void doStartPreview() {
        Log.i(TAG, "doStartPreview()");
        try {
            mVideoContext.getCameraDevice().startPreviewAsync();
            mVideoContext.setCameraState(Camera.STATE_IDLE);
            mVideoContext.getFocusManager().onPreviewStarted();
        } catch (Throwable ex) {
            releaseActor();
            Log.e(TAG, "doStartPreview() exception", ex);
            return;
        }
    }

    @Override
    public void stopPreview() {
        Log.i(TAG, "stopPreview() mVideoContext.getCameraState()=" + mVideoContext.getCameraState());
        //since start preview is async, so during start preview, stop preview may be called!
        //change photo mode to video mode, preview must stopped. 
        //so we should not stop this case and native handle multiple call stop preview.
        if (mVideoContext.getCameraDevice() != null) {
            mVideoContext.getCameraDevice().stopPreview();
            mVideoContext.setCameraState(Camera.STATE_PREVIEW_STOPPED);
        }
    }

    protected void initVideoRecordingFirst() {
        mContentResolver = mVideoContext.getContentResolver();
        mIsContinousFocusMode = mVideoContext.getFocusManager().getContinousFocusSupported();
        mTimeBetweenTimeLapseFrameCaptureMs = mVideoContext.getTimelapseMs();
        mCaptureTimeLapse = (mTimeBetweenTimeLapseFrameCaptureMs != 0);
        mRecordAudio = mVideoContext.getMicrophone();
        mSingleAutoModeSupported = FeatureSwitcher.isContinuousFocusEnabledWhenTouch();
        int seconds = mVideoContext.getLimitedDuration();
        mMaxVideoDurationInMs = 1000 * seconds;
        if(mProfile.quality == CamcorderProfile.QUALITY_MTK_1080P
              || mProfile.quality == CamcorderProfile.QUALITY_MTK_1080P + 1000) {
              //HD Recording is not support zoom
              mVideoContext.getZoomManager().checkQualityForZoom();
        }
        mVideoContext.runOnUiThread(new Runnable() {
            // to keep screen on in main thread
            @Override
            public void run() {
                mVideoContext.keepScreenOnAwhile();
            }
        });
        Log.i(TAG, "initVideoRecordingFirst,mIsContinousFocusMode =" + mIsContinousFocusMode
                + ",mTimeBetweenTimeLapseFrameCaptureMs =" + mTimeBetweenTimeLapseFrameCaptureMs + ",mRecordAudio = "
                + mRecordAudio + ",mSingleAutoModeSupported = " + mSingleAutoModeSupported + ",mMaxVideoDurationInMs ="
                + mMaxVideoDurationInMs);
    }

    private void initializeNormalRecorder() {
        Log.i(TAG, "initializeNormalRecorder()");
        MMProfileManager.startProfileInitMediarecorder();
        getRequestedSizeLimit();
        mMediaRecorder = FrameworksClassFactory.getMediaRecorder();

        mVideoContext.getCameraDevice().unlock();
        if (!FrameworksClassFactory.isMockCamera()) {
            mMediaRecorder.setCamera(mVideoContext.getCameraDevice().getCamera().getInstance());
        } else {
            ((MockMediaRecorder) mMediaRecorder).setContext(mVideoContext);
        }
        if (!mCaptureTimeLapse && mRecordAudio) {
            mMediaRecorder.setAudioSource(MediaRecorder.AudioSource.CAMCORDER);
        }
        mMediaRecorder.setVideoSource(MediaRecorder.VideoSource.CAMERA);
        mMediaRecorder.setOutputFormat(mProfile.fileFormat);
        mMediaRecorder.setVideoFrameRate(mProfile.videoFrameRate);
        mMediaRecorder.setVideoSize(mProfile.videoFrameWidth, mProfile.videoFrameHeight);
        mMediaRecorder.setVideoEncodingBitRate(mProfile.videoBitRate);
        mMediaRecorder.setVideoEncoder(mProfile.videoCodec);

        if(ExtensionHelper.getFeatureExtension().isVideoBitOffSet()) {
            Log.v(TAG, "mMediaRecorder.setVideoBitOffSet(true)");
            MediaRecorderEx.setVideoBitOffSet(mMediaRecorder,1,true);
        }
        if (!mCaptureTimeLapse && mRecordAudio) {
            mMediaRecorder.setAudioEncodingBitRate(mProfile.audioBitRate);
            mMediaRecorder.setAudioChannels(mProfile.audioChannels);
            mMediaRecorder.setAudioSamplingRate(mProfile.audioSampleRate);
            mMediaRecorder.setAudioEncoder(mProfile.audioCodec);
            if (FeatureSwitcher.isHdRecordingEnabled()) {
                MediaRecorderEx.setHDRecordMode(mMediaRecorder, getRecordMode(mVideoContext.getAudioMode()), true);
            }
        }
        mMediaRecorder.setMaxDuration(mMaxVideoDurationInMs);
        if (mCaptureTimeLapse) {
            mMediaRecorder.setCaptureRate((1000 / (double) mTimeBetweenTimeLapseFrameCaptureMs));
        }
        // Get location from save request firstly, if it is null,then get it from location manager.
        Location loc = null;
        if (mVideoSaveRequest != null) {
            loc = mVideoSaveRequest.getLocation();
        } else {
            loc = mVideoContext.getLocationManager().getCurrentLocation();
        }
        if (loc != null) {
            mMediaRecorder.setLocation((float) loc.getLatitude(), (float) loc.getLongitude());
        }

        mStartLocation = mVideoContext.getLocationManager().getCurrentLocation();
        if (mStartLocation != null) {
            float latitue = (float) mStartLocation.getLatitude();
            float longitude = (float) mStartLocation.getLongitude();
            mMediaRecorder.setLocation(latitue, longitude);
        }
        // Set maximum file size.
        long maxFileSize = Storage.getAvailableSpace() - Storage.RECORD_LOW_STORAGE_THRESHOLD;
        if (mRequestedSizeLimit > 0 && mRequestedSizeLimit < maxFileSize) {
            maxFileSize = mRequestedSizeLimit;
        }
        try {
            mMediaRecorder.setMaxFileSize(maxFileSize);
        } catch (RuntimeException exception) { // need,RuntimeException
            // We are going to ignore failure of setMaxFileSize here, as
            // a) The composer selected may simply not support it, or
            // b) The underlying media framework may not handle 64-bit range
            // on the size restriction.
            Log.w(TAG, "initializeNormalRecorder()", exception);
        }
        // Set output file.Try Uri in the intent first. If it doesn't exist, use our own instead.
        if (mVideoFileDescriptor != null) {
            mMediaRecorder.setOutputFile(mVideoFileDescriptor.getFileDescriptor());
        } else {
            generateVideoFilename(mProfile.fileFormat, null);
            mMediaRecorder.setOutputFile(mVideoFilename);
        }
        if(mVideoContext.getSettingChecker().isSlowMotion() && ModePicker.MODE_VIDEO == mVideoContext.getCameraActor().getMode()) {
            setSlowMotionVideoFileSpeed(mMediaRecorder, mProfile.videoFrameRate / SLOW_MOTION_VIDEO_FILE_DEFAULT_FPS);
        }
        // See android.hardware.Camera.Parameters.setRotation for documentation.
        // Note that mOrientation here is the device orientation, which is the opposite of what
        // activity.getWindowManager().getDefaultDisplay().getRotation() would return,which is the orientation the graphics
        // need to rotate in order to render correctly.
        mOrientation = mVideoContext.getOrietation();
        setOrientationHint(getRecordingRotation(mOrientation));
        mVideoContext.setReviewOrientationCompensation(mVideoContext.getOrientationCompensation());
        try {
            mMediaRecorder.prepare();
        } catch (IOException e) {
            Log.e(TAG, "prepare failed", e);
            releaseMediaRecorder();
            throw new RuntimeException(e);
        }
        mMediaRecorder.setOnErrorListener(this);
        mMediaRecorder.setOnInfoListener(this);
        mMediaRecorder.setOnCameraReleasedListener(this);
        //if main thread is waited(case: live photo onfullScreenChange)
        //mReleaseOnInfoListener will run after initializeNormalRecorder,
        //we should not allow this
        mHandler.removeCallbacks(mReleaseOnInfoListener);
        MMProfileManager.stopProfileInitMediarecorder();
    }
    
    public int getRecordingRotation(int orientation) {
        int rotation;
        if (orientation != OrientationEventListener.ORIENTATION_UNKNOWN) {
            CameraInfo info = CameraHolder.instance().getCameraInfo()[mVideoContext.getCameraId()];
            if (info.facing == CameraInfo.CAMERA_FACING_FRONT) {
                rotation = (info.orientation - mOrientation + 360) % 360;
            } else { // back-facing camera
                rotation = (info.orientation + orientation) % 360;
            }
        } else {
            // Get the right original orientation
            CameraInfo info = CameraHolder.instance().getCameraInfo()[mVideoContext.getCameraId()];
            rotation = info.orientation;
        }
        return rotation;
    }
    
    protected void setOrientationHint(int orientation) {
        if (mMediaRecorder != null) {
            mMediaRecorder.setOrientationHint(orientation);
        }
    }

    public String generateVideoFilename(int outputFileFormat, String suffix) {
        String mDisplayName = "videorecorder";
        // Used when emailing.
        String filename = mDisplayName + convertOutputFormatToFileExt(outputFileFormat);
        if (suffix == null) {
            mVideoTempPath = Storage.getFileDirectory() + '/' + filename  +  ".tmp";
        } else {
            mVideoTempPath = Storage.getFileDirectory() + '/' + filename + "_" + suffix +  ".tmp";
        }
        mVideoFilename = mVideoTempPath;
        Log.i(TAG, "generateVideoFilename mVideoFilename = " + mVideoFilename);
        return mVideoFilename;
    }

    private String convertOutputFormatToFileExt(int outputFileFormat) {
        if (outputFileFormat == MediaRecorder.OutputFormat.MPEG_4) {
            return ".mp4";
        }
        return ".3gp";
    }

    public void startVideoRecording() {
        Log.i(TAG, "startVideoRecording() mVideoCameraClosed = " + mVideoCameraClosed);
        //live photo,do start video recording after do start preview
        //when doStartPreview(),power off, may close camera
        if (mVideoCameraClosed) {
            return;
        }
        if (mSingleAutoModeSupported && mIsContinousFocusMode) {
            mSingleStartRecording = true;
            setAutoFocusMode();
        }
        mCallFromOnPause = false;
        hideOtherSettings(true);
        initializeRecordingView();
        mCurrentVideoUri = null;
        /// between with initializeRecorder unlock cameraclient and srartRecording lock cameraclient is not allow to setParameter so lock run it
        mVideoContext.lockRun(new Runnable() {
            @Override
            public void run() {
                initializeRecorder();
                pauseAudioPlayback();
                startRecording();
            }
        });
        if (mStartRecordingFailed) {
            Log.i(TAG, "mStartRecordingFailed.");
            mStartRecordingFailed = false;
            mVideoContext.showToast(R.string.video_recording_error);
            backToLastTheseCase();
        }
        // Parameters may have been changed by media recorder when recording starts.
        // To reduce latency, we do not update mParameters during zoom.
        // Keep this up-to-date now. Otherwise, we may revert the video size unexpectedly.
        mVideoContext.lockRun(new Runnable() {
            @Override
            public void run() {
                mVideoContext.fetchParametersFromServer();
            }
        });
        // add for camera pause feature
        mMediaRecoderRecordingPaused = false;
        mVideoRecordedDuration = 0;
        mRecorderCameraReleased = false;
        mStoppingAction = STOP_NORMAL;
        if (mWfdListenerEnabled) {
            releaseRecorder();
            backToLastTheseCase();
        }
        mMediaRecorderRecording = true;
        mRecordingStartTime = SystemClock.uptimeMillis();
        updateRecordingTime();
        if (!mCaptureTimeLapse) {
            // we just update recording time once,because it will be called in onInfo(xxx).update time to 00:00 or 00:30 .
            mHandler.removeMessages(UPDATE_RECORD_TIME);
        }
        mVideoContext.keepScreenOn();
        Log.i(TAG, "startVideoRecording() end");
    }
    
    public void initializeRecordingView() {
        if (mVideoContext.getLimitedSize() > 0) {
            mTotalSize = mVideoContext.getLimitedSize();
            mRecordingView.setTotalSize(mTotalSize);
            mRecordingView.setCurrentSize(0L);
            mRecordingView.setRecordingSizeVisible(true);
        }
        mRecordingView.setRecordingIndicator(true);
        mRecordingView.setPauseResumeVisible(true);
        mRecordingView.show();
    }
    
    public void initializeRecorder() {
        initializeNormalRecorder();
        if (mMediaRecorder == null) {
            Log.e(TAG, "Fail to initialize media recorder.", new Throwable());
            return;
        }
    }
    
    public void startRecording() {
        startNormalRecording();
        mVideoContext.getShutterManager().setVideoShutterMask(true);
        mVideoContext.setCameraState(Camera.STATE_RECORDING_IN_PROGRESS);
    }
    
    public void stopRecording() {
        Log.i(TAG, "stopRecording begin");
        mMediaRecorder.stop();
        mMediaRecorder.setOnCameraReleasedListener(null);
        Log.i(TAG, "stopRecording end");
    }
    
    public void doAfterStopRecording(boolean fail) {
        if (!mVideoContext.isNonePickIntent()) {
            if (!fail && mStoppingAction != STOP_RETURN_UNVALID) {
                if (mVideoContext.isQuickCapture()) {
                    mStoppingAction = STOP_RETURN;
                } else {
                    mStoppingAction = STOP_SHOW_ALERT;
                }
            }
        } else if (fail) {
            mStoppingAction = STOP_FAIL;
        }
        // always release media recorder
        releaseMediaRecorder();
        addVideoToMediaStore(false);
        synchronized (mVideoSavingTask) {
            mVideoSavingTask.notifyAll();
            mHandler.removeCallbacks(mVideoSavedRunnable);
            mHandler.post(mVideoSavedRunnable);
        }
    }

    protected void startNormalRecording() {
        Log.i(TAG, "startNormalRecording()");
        try {
            MMProfileManager.startProfileMediarecorderStart();
            mMediaRecorder.start(); // Recording is now started
            MMProfileManager.stopProfileMediarecorderStart();
        } catch (RuntimeException e) { // need Runtime
            Log.e(TAG, "Could not start media recorder. ", e);
            mStartRecordingFailed = true;
            releaseMediaRecorder();
            // If start fails, frameworks will not lock the camera for us.
            mVideoContext.getCameraDevice().lock();
        }
    }

    protected void onStopVideoRecordingAsync() {
        Log.i(TAG, "onStopVideoRecordingAsync");
        stopVideoRecordingAsync();
    }

    protected void pauseAudioPlayback() {
        // Shamelessly copied from MediaPlaybackService.java, which should be public, but isn't.
        Log.i(TAG, "pauseAudioPlayback()");
        Intent i = new Intent("com.android.music.musicservicecommand");
        i.putExtra("command", "pause");
        mVideoContext.sendBroadcast(i);
    }

    // from MediaRecorder.OnErrorListener
    @Override
    public void onError(MediaRecorder mr, int what, int extra) {
        Log.e(TAG, "MediaRecorder error. what=" + what + ". extra=" + extra);
        if (what == MediaRecorder.MEDIA_RECORDER_ERROR_UNKNOWN) {
            // We may have run out of space on the sdcard.
            stopVideoRecordingAsync();
        } else if (extra == MediaRecorder.MEDIA_RECORDER_ENCODER_ERROR) {
            onStopVideoRecordingAsync();
            mVideoContext.showAlertDialog(mVideoContext.getString(R.string.camera_error_title),
                    mVideoContext.getString(R.string.video_encoder_error), mVideoContext.getString(R.string.dialog_ok),
                    null, null, null);
        }
    }

    // from MediaRecorder.OnInfoListener
    @Override
    public void onInfo(MediaRecorder mr, int what, int extra) {
        if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED) {
            if (mMediaRecorderRecording) {
                onStopVideoRecordingAsync();
            }
        } else if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_FILESIZE_REACHED) {
            if (mMediaRecorderRecording) {
                onStopVideoRecordingAsync();
                mVideoContext.showToastForShort(R.string.video_reach_size_limit);
            }
        } else if (what == MediaRecorder.MEDIA_RECORDER_INFO_CAMERA_RELEASE) {
            if(extra == -1) { //info from mediaencoderfilter indicate it has started recorder.
                mFilterStartEncode = true;
                Log.i(TAG, "Filter start encode!");
            } else {
                if (mVideoSavingTask != null) {
                    synchronized (mVideoSavingTask) {
                        Log.i(TAG, "MediaRecorder camera released, notify job wait for camera release");
                        mVideoSavingTask.notifyAll();
                    }
                }
            }
        } else if (what == MediaRecorder.MEDIA_RECORDER_INFO_START_TIMER) {
            if (!mCaptureTimeLapse) {
                mRecordingStartTime = SystemClock.uptimeMillis();
                // mVideoContext.getShutterManager().setVideoShutterEnabled(true);
                updateRecordingTime();
            }
        } else if (what == MediaRecorder.MEDIA_RECORDER_INFO_FPS_ADJUSTED
                || what == MediaRecorder.MEDIA_RECORDER_INFO_BITRATE_ADJUSTED) {
            mVideoContext.showToast(R.string.video_bad_performance_drop_quality);
        } else if (what == MediaRecorder.MEDIA_RECORDER_INFO_WRITE_SLOW) {
            mVideoContext.showToast(R.string.video_bad_performance_auto_stop);
            stopVideoRecordingAsync();
        } else if (what == MEDIA_RECORDER_INFO_RECORDING_SIZE) {
            if (mTotalSize > 0) {
                int progress = (int) (extra * 100 / mTotalSize);
                if (progress <= 100) {
                    Log.i(TAG, "MEDIA_RECORDER_INFO_RECORDING_SIZE,extra= " + extra + " progress= " + progress);
                    mRecordingView.setCurrentSize(extra);
                    mRecordingView.setSizeProgress(progress);
                }
            }
        }
    }

    public void hideOtherSettings(boolean hide) {
        if (hide) {
            mVideoContext.setViewState(Camera.VIEW_STATE_RECORDING);
        } else {
            mVideoContext.restoreViewState();
        }
    }

    protected void updateRecordingTime() {
        if (!mMediaRecorderRecording) {
            return;
        }
        MMProfileManager.triggerUpdateRecordingTime();
        long now = SystemClock.uptimeMillis();
        long delta = now - mRecordingStartTime;
        if (mMediaRecoderRecordingPaused) {
            delta = mVideoRecordedDuration;
        }
        // Starting a minute before reaching the max duration
        // limit, we'll countdown the remaining time instead.
        long deltaAdjusted = delta;
        long targetNextUpdateDelay;
        if (!mCaptureTimeLapse) {
            if(mVideoContext.getSettingChecker().isSlowMotion()) {
                mRecordingView.showTime(deltaAdjusted * mProfile.videoFrameRate / SLOW_MOTION_VIDEO_FILE_DEFAULT_FPS, false);
            } else {
                mRecordingView.showTime(deltaAdjusted, false);
            }
            targetNextUpdateDelay = 1000;
        } else {
            // The length of time lapse video is different from the length
            // of the actual wall clock time elapsed. Display the video length
            // only in format hh:mm:ss.dd, where dd are the centi seconds.
            mRecordingView.showTime(getTimeLapseVideoLength(delta), true);
            targetNextUpdateDelay = mTimeBetweenTimeLapseFrameCaptureMs;
        }
        mCurrentShowIndicator = 1 - mCurrentShowIndicator;
        if (mMediaRecoderRecordingPaused && 1 == mCurrentShowIndicator) {
            mRecordingView.setTimeVisible(false);
        } else {
            mRecordingView.setTimeVisible(true);
        }
        long actualNextUpdateDelay = 500;
        if (!mMediaRecoderRecordingPaused) {
            actualNextUpdateDelay = targetNextUpdateDelay - (delta % targetNextUpdateDelay);
        }
        Log.d(TAG, "updateRecordingTime(),actualNextUpdateDelay==" + actualNextUpdateDelay);
        mHandler.sendEmptyMessageDelayed(UPDATE_RECORD_TIME, actualNextUpdateDelay);
    }

    public void doReturnToCaller(boolean valid) {
        Log.d(TAG, "doReturnToCaller(" + valid + ")");
        /*
         * when the SMB is connected, the fileSaver thread maybe first run the SaveRequest
         * but this time the videosaverunable may not wait 
         * so this time the uri maybe null,here get again
         */
        if (mCurrentVideoUri == null && mVideoSaveRequest != null) {
            mCurrentVideoUri = mVideoSaveRequest.getUri();
            Log.i(TAG,  "next time get the mCurrentVideoUri = " +mCurrentVideoUri);
        }
        Intent resultIntent = new Intent();
        int resultCode;
        if (valid) {
            resultCode = Activity.RESULT_OK;
            resultIntent.setData(mCurrentVideoUri);
            if (mVideoContext.isVideoWallPaperIntent()) {
                Util.setLastUri(mCurrentVideoUri);
            }
        } else {
            resultCode = Activity.RESULT_CANCELED;
        }
        mVideoContext.setResultExAndFinish(resultCode, resultIntent);
    }

    private long getTimeLapseVideoLength(long deltaMs) {
        // For better approximation calculate fractional number of frames captured.
        // This will update the video time at a higher resolution.
        double numberOfFrames = (double) deltaMs / mTimeBetweenTimeLapseFrameCaptureMs;
        return (long) (numberOfFrames / mProfile.videoFrameRate * 1000);
    }

    public void showAlert() {
        Log.d(TAG, "showAlert()");
        if (Storage.isStorageReady()) {
            FileDescriptor mFileDescriptor = null;
            if (mVideoFileDescriptor != null) {
                mFileDescriptor = mVideoFileDescriptor.getFileDescriptor();
                mVideoContext.showReview(mVideoFileDescriptor.getFileDescriptor());
            } else if (mCurrentVideoFilename != null) {
                mVideoContext.showReview(mCurrentVideoFilename);
            }
            mVideoContext.switchShutter(ShutterManager.SHUTTER_TYPE_OK_CANCEL);
        }
    }

    // This Handler is used to post message back onto the main thread of the application
    private class MainHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            Log.i(TAG, "MainHandler.handleMessage(" + msg + ")");
            switch (msg.what) {
            case UPDATE_RECORD_TIME:
                updateRecordingTime();
                break;
            case PARAMETER_CHANGE_DONE:
            	initializeShutterType();
            	break;
            case UPDATE_SNAP_UI:
                Util.broadcastNewPicture(mVideoContext, mSnapUri);
                showVideoSnapshotUI(false);
                break;
            case INIT_SHUTTER_STATUS:
            	initializeShutterStatus();
            	break;
            default:
                break;
            }
        }
    }

    @Override
    public void onCameraClose() { // before camera close and mCameraDevice = null
        Log.i(TAG, "onCameraClose()");
        mHandler.removeMessages(UPDATE_RECORD_TIME);
        mHandler.removeMessages(UPDATE_SNAP_UI);
        // avoid performance is bad,the UPDATE_SNAP_UI msg had not been handleMessage,
        // it was removed,the red frame will show always.
        showVideoSnapshotUI(false); 
        mVideoCameraClosed = true;
        mSingleStartRecording = false;
        mIsAutoFocusCallback = false;
        if (mVideoContext.getCameraDevice() != null) {
            mVideoContext.getCameraDevice().cancelAutoFocus();
        }
        if (mVideoContext.getFocusManager() != null) {
            mVideoContext.getFocusManager().onPreviewStopped();
        }
        stopVideoOnPause();
        if (mVideoContext.getCameraDevice() == null) {
            return;
        }
        mVideoContext.resetScreenOn();
    }

    @Override
    public boolean onUserInteraction() {
        if (!mMediaRecorderRecording) {
            mVideoContext.keepScreenOnAwhile();
            return true;
        }
        return false;
    }

    @Override
    public boolean onBackPressed() {
        Log.d(TAG, "onBackPressed() isFinishing()=" + mVideoContext.isFinishing() + ", mVideoCameraClosed="
                + mVideoCameraClosed + ", isVideoProcessing()=" + isVideoProcessing()
                + ",mVideoContext.isShowingProgress() = " + mVideoContext.isShowingProgress());
        if (mVideoCameraClosed || (mVideoContext.isShowingProgress()) || isVideoProcessing()) {
            return true;
        }
        if (mMediaRecorderRecording) {
            onStopVideoRecordingAsync();
            return true;
        }
        return super.onBackPressed();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        // Do not handle any key if the activity is paused.
        if (mVideoCameraClosed) {
            return true;
        }
        switch (keyCode) {
        case KeyEvent.KEYCODE_CAMERA:
            if (event.getRepeatCount() == 0) {
                if (!(mVideoContext.getReviewManager().isShowing())) {
                    // || (mLearningView != null && mLearningView.isShowing()))) {
                    mVideoShutterListener.onShutterButtonClick(null);
                }
                return true;
            }
            break;
        case KeyEvent.KEYCODE_DPAD_CENTER:
            if (event.getRepeatCount() == 0) {
                if (!(mVideoContext.getReviewManager().isShowing())) {
                    // || (mLearningView != null && mLearningView.isShowing()))) {
                    mVideoShutterListener.onShutterButtonClick(null);
                }
                return true;
            }
            break;
        case KeyEvent.KEYCODE_MENU:
            if (mMediaRecorderRecording) {
                return true;
            }
            break;

        default:
            break;
        }
        return super.onKeyDown(keyCode, event);
    }

/*    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        switch (keyCode) {
        case KeyEvent.KEYCODE_CAMERA:
            mVideoContext.getShutterManager().setVideoShutterEnabled(false);
            return true;
        default:
            break;
        }
        return super.onKeyUp(keyCode, event);
    }  */

    public void stopVideoOnPause() {
        Log.d(TAG, "stopVideoOnPause() mMediaRecorderRecording =  " + mMediaRecorderRecording);
        boolean videoSaving = false;
        if (mMediaRecorderRecording) {
            mCallFromOnPause = true; 
            if (!mVideoContext.isNonePickIntent()) {
                mStoppingAction = STOP_SHOW_ALERT;
            }
            stopVideoRecordingAsync();
            videoSaving = isVideoProcessing();
        } else {
            // always release media recorder. if video saving task is ongoing, let SavingTask do this job.
            releaseMediaRecorder();
            // Case: onPause quickly when not recording, we should back to photo mode.
            if (mVideoContext.isNonePickIntent()) {
                //if the CameraVideo is launched by 3rd app, need not back to last case .
                backToLastTheseCase();
            }
        }
        if (videoSaving) {
            waitForRecorder();
        } else {
            // here if media recorder is stopping in videoSavingTask, do the job later.
            closeVideoFileDescriptor();
        }
        Log.i(TAG, "stopVideoOnPause() " + " videoSaving=" + videoSaving
                + ", mVideoSavingTask=" + mVideoSavingTask + ", mMediaRecorderRecording=" + mMediaRecorderRecording);
    }

    private void pauseVideoRecording() {
        Log.d(TAG, "pauseVideoRecording() mRecorderBusy=" + mRecorderBusy);
        mRecordingView.setRecordingIndicator(false);
        if (mMediaRecorderRecording && !mMediaRecoderRecordingPaused) {
            try {
                MediaRecorderEx.pause(mMediaRecorder);
            } catch (IllegalStateException e) {
                Log.e("Camera", "Could not pause media recorder. ");
            }
            mVideoRecordedDuration = SystemClock.uptimeMillis() - mRecordingStartTime;
            mMediaRecoderRecordingPaused = true;
        }
    }

    public volatile boolean mRecorderBusy = false;

    public void stopVideoRecordingAsync() {
        // for snapshot
        mStopVideoRecording = true;
        Log.d(TAG, "stopVideoRecordingAsync() mMediaRecorderRecording=" + mMediaRecorderRecording + ", mRecorderBusy="
                + mRecorderBusy + "mStopVideoRecording =" + mStopVideoRecording);
        mVideoContext.getZoomManager().changeZoomForQuality();
        mVideoContext.setSwipingEnabled(true);
        mHandler.removeMessages(UPDATE_RECORD_TIME);
        mVideoContext.getShutterManager().setVideoShutterMask(false);
        if (isVideoProcessing()) {
            return;
        }
        if (mRecorderBusy) { // return for recorder is busy.
            return;
        }
        mRecorderBusy = true;
        mRecordingView.hide();
        if (mMediaRecorderRecording) {
            mVideoContext.getShutterManager().setVideoShutterEnabled(false);
            if (mStoppingAction != STOP_RETURN_UNVALID && mCallFromOnPause != true) {
                mVideoContext.showProgress(mVideoContext.getResources().getString(R.string.saving));
            }
            mVideoSavingTask = new SavingTask();
            mVideoSavingTask.start();
        } else {
            mRecorderBusy = false;
            releaseRecorder();
            if (mStoppingAction == STOP_RETURN_UNVALID) {
                doReturnToCaller(false);
            }
        }
    }
    protected final Runnable mReleaseOnInfoListener = new Runnable() {
        @Override
        public void run() {
                if(mMediaRecorder != null){
                    mMediaRecorder.setOnInfoListener(null);
                    mMediaRecorder.setOnErrorListener(null);
                    mMediaRecorder = null ;
                }
    	}
    };
    protected class SavingTask extends Thread {
        public void run() {
            Log.i(TAG, "SavingTask.run() begin " + this
                    + ", mMediaRecorderRecording=" + mMediaRecorderRecording + ", mRecorderBusy=" + mRecorderBusy);
            MMProfileManager.startProfileStoreVideo();
            boolean fail = false;
            // add saving thread to avoid blocking main thread
            if (mMediaRecorderRecording) {
                try {
                    stopRecording();
                    if(mCallFromOnPause) {
                    }
                    mCurrentVideoFilename = mVideoFilename;
                    Log.d(TAG, "Setting current video filename: " + mCurrentVideoFilename);
                } catch (RuntimeException e) { // need Runtime
                    Log.e(TAG, "stop fail", e);
                    fail = true;
                    if (mVideoFilename != null) {
                        deleteVideoFile(mVideoFilename);
                    }
                }
            }
            //live photo,current camera state is preview stopped, 
            //here sets camera sate to idle only for stop preview is not done when stopping recording
            if (!mVideoCameraClosed && (mVideoContext.getCameraState() != Camera.STATE_PREVIEW_STOPPED)) {
                mVideoContext.setCameraState(Camera.STATE_IDLE);
            }
            doAfterStopRecording(fail);
            mMediaRecorderRecording = false;
            Log.i(TAG, "SavingTask.run() end " + this + ", mCurrentVideoUri=" + mCurrentVideoUri + ", mRecorderBusy="
                    + mRecorderBusy);
            MMProfileManager.stopProfileStoreVideo();
        }
    }

    private Runnable mVideoSavedRunnable = new Runnable() {
        public void run() {
            Log.i(TAG, "mVideoSavedRunnable.run() begin mVideoCameraClosed=" + mVideoCameraClosed + ", mStoppingAction="
                    + mStoppingAction + ", mFocusState="
                    + mFocusState + ", mSingleAutoModeSupported=" + mSingleAutoModeSupported + ", mRecorderBusy="
                    + mRecorderBusy);
            hideOtherSettings(false);
            mVideoContext.dismissProgress();
            if (!mVideoCameraClosed) {
                // The orientation was fixed during video recording. Now make it
                // reflect the device orientation as video recording is stopped.
                mVideoContext.keepScreenOnAwhile();
            }
            mVideoContext.getShutterManager().setVideoShutterEnabled(true);
            int action = (mVideoCameraClosed && mStoppingAction != STOP_NORMAL && mStoppingAction != STOP_FAIL)
                    ? STOP_SHOW_ALERT
                    : mStoppingAction;
            switch (action) {
            case STOP_NORMAL:
                if (!mVideoCameraClosed) {
                    mVideoContext.animateCapture();
                }
                break;
            case STOP_SHOW_ALERT:
                showAlert();
                break;
            case STOP_RETURN_UNVALID:
                doReturnToCaller(false);
                break;
            case STOP_RETURN:
                doReturnToCaller(true);
                break;
            default:
                break;
            }
            if (mVideoCameraClosed) {
                closeVideoFileDescriptor();
            }
            // if the onAutoFocus had not back yet,changeFocusState()
            if (!mVideoCameraClosed
                    && ((mFocusState == START_FOCUSING) || (mFocusState == FOCUSING) || mSingleAutoModeSupported)) {
                changeFocusState();
            }
            backToLastModeIfNeed();
            mRecorderBusy = false;
            Log.i(TAG, "mVideoSavedRunnable.run() end mRecorderBusy=" + mRecorderBusy);
        };
    };

    protected boolean isVideoProcessing() {
        return mVideoSavingTask != null && mVideoSavingTask.isAlive();
    }

    protected void waitForRecorder() {
        synchronized (mVideoSavingTask) {
            if (!mRecorderCameraReleased) {
                try {
                    Log.i(TAG, "Wait for releasing camera done in MediaRecorder");
                    mVideoSavingTask.wait();
                } catch (InterruptedException e) {
                    Log.w(TAG, "Got notify from Media recorder()", e);
                }
            }
        }
    }

    // for pause/resume
    public OnClickListener mVideoPauseResumeListner = new OnClickListener() {
        public void onClick(View v) {
            Log.i(TAG, "mVideoPauseResumeListner.onClick() mMediaRecoderRecordingPaused=" + mMediaRecoderRecordingPaused
                    + ",mRecorderBusy = " + mRecorderBusy + ",mMediaRecorderRecording = " + mMediaRecorderRecording);
            // return for recorder is busy.or if the recording has already stopped because of some info,it will not response
            // the restart.
            if (mRecorderBusy || (!mMediaRecorderRecording)) {
                return;
            }
            mRecorderBusy = true;
            if (mMediaRecoderRecordingPaused) {
                mRecordingView.setRecordingIndicator(true);
                try {
                    mMediaRecorder.start();
                    mRecordingStartTime = SystemClock.uptimeMillis() - mVideoRecordedDuration;
                    mVideoRecordedDuration = 0;
                    mMediaRecoderRecordingPaused = false;
                } catch (IllegalStateException e) {
                    Log.e(TAG, "Could not start media recorder. ", e);
                    mVideoContext.showToast(R.string.toast_video_recording_not_available);
                    releaseMediaRecorder();
                }
            } else {
                pauseVideoRecording();
            }
            mRecorderBusy = false;
            Log.i(TAG, "mVideoPauseResumeListner.onClick() end. mRecorderBusy=" + mRecorderBusy);
        }
    };

    protected boolean backToLastModeIfNeed() {
        Log.d(TAG, "backToLastModeIfNeed(),mIgnoreBackToLastMode = " + mNeedBackToLastMode);
        boolean back = false;
        if (mVideoContext.isNonePickIntent()) {
            releaseActor();
            mVideoContext.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    mVideoContext.getShutterManager().setPhotoShutterEnabled(true);
                    if (mNeedBackToLastMode) {
                        mVideoContext.backToLastMode();
                        mNeedBackToLastMode = false;
                    }
                }
            });
            back = true;
        } else if (mVideoContext.isVideoCaptureIntent() || mVideoContext.isVideoWallPaperIntent()) {
            mVideoContext.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (!mVideoContext.getReviewManager().isShowing()) {
                        mVideoContext.switchShutter(ShutterManager.SHUTTER_TYPE_VIDEO);
                    }
                }
            });
        }
        Log.d(TAG, "backToLastModeIfNeed() return " + back);
        return back;
    }

    public void releaseActor() {
        Log.i(TAG, "releaseVideoActor");
        mVideoShutterListener = null;
        if (mVideoContext.getFocusManager() != null) {
            mVideoContext.getFocusManager().removeMessages();
        }
        if (mRecordingView != null) {
            mRecordingView.release();
        }
        if (mWfdManager != null) {
            mWfdManager.removeListener(mWfdListener);
        }
        mSingleStartRecording = false;
        mIsAutoFocusCallback = false;
        mFocusManager = null;
        mMediaRecoderRecordingPaused = false;
    }

    private long computeDuration() {
        long duration = getDuration();
        Log.i(TAG, "computeDuration() return " + duration);
        return duration;
    }

    private long getDuration() {
        Log.i(TAG, "getDuration mCurrentVideoFilename = " + mCurrentVideoFilename);
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try {
            retriever.setDataSource(mCurrentVideoFilename);
            return Long.valueOf(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION));
        } catch (IllegalArgumentException e) {
            return INVALID_DURATION;
        } catch (RuntimeException e) {
            return FILE_ERROR;
        } finally {
            retriever.release();
        }
    }

    protected void releaseMediaRecorder() {
        Log.i(TAG, "releaseMediaRecorder() mMediaRecorder=" 
                    + mMediaRecorder + " mRecorderCameraReleased = " + mRecorderCameraReleased);
        if (mMediaRecorder != null && !mRecorderCameraReleased) {
            cleanupEmptyFile();
            mMediaRecorder.reset();
            mMediaRecorder.release();
            mRecorderCameraReleased = true;
            mHandler.post(mReleaseOnInfoListener);
        }
        mVideoFilename = null;
    }
    
    public void releaseRecorder() {
        releaseMediaRecorder();
    }

    private String convertOutputFormatToMimeType(int outputFileFormat) {
        if (outputFileFormat == MediaRecorder.OutputFormat.MPEG_4) {
            return "video/mp4";
        }
        return "video/3gpp";
    }

    public void closeVideoFileDescriptor() {
        if (mVideoFileDescriptor != null) {
            try {
                mVideoFileDescriptor.close();
            } catch (IOException e) {
                Log.e(TAG, "Fail to close fd", e);
            }
            mVideoFileDescriptor = null;
        }
    }

    public void cleanupEmptyFile() {
        if (mVideoFilename != null) {
            File f = new File(mVideoFilename);
            if (f.length() == 0 && f.delete()) {
                Log.d(TAG, "Empty video file deleted: " + mVideoFilename);
                mVideoFilename = null;
            }
        }
    }

    protected void deleteCurrentVideo() {
        // Remove the video and the uri if the uri is not passed in by intent.
        Log.d(TAG, "deleteCurrentVideo() mCurrentVideoFilename=" + mCurrentVideoFilename);
        if (mCurrentVideoFilename != null) {
            if (mSaveTempVideo > 0) {
                renameVideoFile(mCurrentVideoFilename);
            } else {
                deleteVideoFile(mCurrentVideoFilename);
            }
            mCurrentVideoFilename = null;
            if (mCurrentVideoUri != null) {
                mContentResolver.delete(mCurrentVideoUri, null, null);
                mCurrentVideoUri = null;
            }
        }
    }

    public void deleteVideoFile(String fileName) {
        File f = new File(fileName);
        if (!f.delete()) {
            Log.i(TAG, "Could not delete " + fileName);
        }
    }
    
    public void renameVideoFile(String fileName) {
        File f = new File(fileName);
        File newFile = new File(fileName + "_" + (SystemClock.currentThreadTimeMillis()));
        if (!f.renameTo(newFile)) {
            Log.i(TAG, "Rename to new file " + newFile.getName());
        }
    }

    private final class AutoFocusCallback implements android.hardware.Camera.AutoFocusCallback {
        public void onAutoFocus(boolean focused, android.hardware.Camera camera) {
            if (mVideoCameraClosed) {
                return;
            }
            Log.i(TAG, "mAutoFocusTime = " + (System.currentTimeMillis() - mFocusStartTime) + "ms"
                    + ",mFocusManager.onAutoFocus(focused)");
            setFocusState(FOCUSED);
            mVideoContext.getFocusManager().onAutoFocus(focused);
            mIsAutoFocusCallback = true;
        }
    }

    public void autoFocus() {
        mFocusStartTime = System.currentTimeMillis();
        Log.i(TAG, "autoFocus");
        mVideoContext.getCameraDevice().autoFocus(mAutoFocusCallback);
        setFocusState(FOCUSING);
    }

    public void cancelAutoFocus() {
        Log.i(TAG, "cancelAutoFocus");
        if (mVideoContext.getCameraDevice() != null) {
            mVideoContext.getCameraDevice().cancelAutoFocus();
        }
        setFocusState(FOCUS_IDLE);
        if (!(mSingleStartRecording && mSingleAutoModeSupported && mIsAutoFocusCallback)) {
            setFocusParameters();
        }
        mIsAutoFocusCallback = false;
    }

    public Listener getFocusManagerListener() {
        return this;
    }

    public boolean capture() {
        return false;
    }

    public void setFocusParameters() {
        mVideoContext.applyParameterForFocus(!mIsAutoFocusCallback);
    }

    public void startFaceDetection() {
    }

    public void stopFaceDetection() {
    }
    
    public void startObjectTracking(int x, int y) {
    }

    public void stopObjectTracking() {
    }

    public void playSound(int soundId) {
    }

    public boolean readyToCapture() {
        return false;
    }

    public boolean doSymbolShutter() {
        return false;
    }

    protected void setAutoFocusMode() { // should be checked
        if (isSupported(Parameters.FOCUS_MODE_AUTO, mVideoContext.getParameters().getSupportedFocusModes())) {
            final String focusMode = Parameters.FOCUS_MODE_AUTO;
            mVideoContext.lockRun(new Runnable() {
                @Override
                public void run() {
                    mVideoContext.getParameters().setFocusMode(focusMode);
                    mVideoContext.applyParametersToServer();
                }
            });
        }
        Log.i(TAG, "set focus mode is auto");
    }

    private void changeFocusState() {
        Log.i(TAG, "changeFocusState()");
        if (mVideoContext.getCameraDevice() != null) {
            mVideoContext.getCameraDevice().cancelAutoFocus();
        }
        mSingleStartRecording = false;
        mIsAutoFocusCallback = false;
        mVideoContext.getFocusManager().resetTouchFocus();
        setFocusParameters();
        mVideoContext.getFocusManager().updateFocusUI();
    }

    private void setFocusState(int state) {
        Log.i(TAG, "setFocusState(" + state + ") mMediaRecorderRecording=" + mMediaRecorderRecording
                + ", mVideoCameraClosed=" + mVideoCameraClosed
                + ",mVideoContext.getViewState() = " + mVideoContext.getViewState());
        mFocusState = state;
        if (mMediaRecorderRecording || mVideoCameraClosed || (mVideoContext.getViewState() == Camera.VIEW_STATE_REVIEW)) {
            return;
        }
        switch (state) {
        case FOCUSING:
            mVideoContext.setViewState(Camera.VIEW_STATE_FOCUSING);
            break;
        case FOCUS_IDLE:
        case FOCUSED:
            hideOtherSettings(false);
            break;
        default:
            break;
        }
    }

    private final class AutoFocusMoveCallback implements android.hardware.Camera.AutoFocusMoveCallback {
        @Override
        public void onAutoFocusMoving(boolean moving, android.hardware.Camera camera) {
            Log.i(TAG, "VideoActor onAutoFocusMoving moving = " + moving);
            mVideoContext.getFocusManager().onAutoFocusMoving(moving);
        }
    }

    public AutoFocusMoveCallback getAutoFocusMoveCallback() {
        return mAutoFocusMoveCallback;
    }

    public OnClickListener mReviewPlay = new OnClickListener() {
        public void onClick(View v) {
            startPlayVideoActivity();
        }
    };
    private OnClickListener mRetakeListener = new OnClickListener() {
        public void onClick(View v) {
            deleteCurrentVideo();
            mVideoContext.hideReview();
            mVideoContext.getShutterManager().setVideoShutterEnabled(true);
            mVideoContext.switchShutter(ShutterManager.SHUTTER_TYPE_VIDEO);
        }
    };
    private OnClickListener mOkListener = new OnClickListener() {
        public void onClick(View v) {
            doReturnToCaller(true);
        }
    };
    private OnClickListener mCancelListener = new OnClickListener() {
        public void onClick(View v) {
            Log.i(TAG, "mCancelListener");
            if (mVideoContext.getReviewManager().isShowing()) {
                mStoppingAction = STOP_RETURN_UNVALID;
                stopVideoRecordingAsync();
            } else {
                hideOtherSettings(false);
                if (!mVideoContext.isNonePickIntent()) {
                    // if click cancel when not none to none in MMS(pick),update the preference of videoactor.
                    mVideoContext.notifyPreferenceChanged(null);
                }
                backToLastModeIfNeed();
            }
        }
    };

    public OnClickListener getPlayListener() {
        return mReviewPlay;
    }

    public OnClickListener getRetakeListener() {
        return mRetakeListener;
    }

    public OnClickListener getOkListener() {
        return mOkListener;
    }

    public OnClickListener getCancelListener() {
        return mCancelListener;
    }

    private void startPlayVideoActivity() {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        //ALPS01043585
        Log.d(TAG, "getContext().mCanShowVideoShare = " +getContext().mCanShowVideoShare);
        intent.putExtra(Camera.CAN_SHARE, getContext().mCanShowVideoShare);
        intent.setDataAndType(mCurrentVideoUri, convertOutputFormatToMimeType(mProfile.fileFormat));
        try {
            mVideoContext.startActivity(intent);
        } catch (ActivityNotFoundException ex) {
            Log.e(TAG, "Couldn't view video " + mCurrentVideoUri, ex);
        }
    }

    private int getRecordMode(String mode) {
        int audioMode = 0;
        if (mRecordAudio) {
            if (mode.equals(PREF_CAMERA_VIDEO_HD_RECORDING_ENTRYVALUES[0])) {
                audioMode = HDRecordMode.NORMAL;
            } else if (mode.equals(PREF_CAMERA_VIDEO_HD_RECORDING_ENTRYVALUES[1])) {
                audioMode = HDRecordMode.INDOOR;
            } else {
                audioMode = HDRecordMode.OUTDOOR;
            }
        } else {
            audioMode = HDRecordMode.NORMAL;
        }
        Log.d(TAG, "getRecordMode(" + mode + ") return " + audioMode);
        return audioMode;
    }

    public ErrorCallback getErrorCallback() {
        return mErrorCallback;
    }

    private static boolean isSupported(Object value, List<?> supported) {
        return supported == null ? false : supported.indexOf(value) >= 0;
    }

    public void getRequestedSizeLimit() {
        closeVideoFileDescriptor();
        if (!mVideoContext.isNonePickIntent()) {
            Uri saveUri = mVideoContext.getSaveUri();
            if (saveUri != null) {
                try {
                    mVideoFileDescriptor = mContentResolver.openFileDescriptor(saveUri, "rw");
                    mCurrentVideoUri = saveUri;
                } catch (java.io.FileNotFoundException ex) {
                    Log.e(TAG, "initializeNormalRecorder()", ex);
                }
            }
            mRequestedSizeLimit = mVideoContext.getLimitedSize();
            //M: enlage recording video size nearby to limit size in MMS
            //in case of low quality and files size below 2M.
            if (mVideoContext.isLowVideoQuality() && (mRequestedSizeLimit < 2*1024*1024)) {
                mRequestedSizeLimit = (long)((double)mRequestedSizeLimit / 0.95 - 1024 *2);
            }
        }
    }

    private void showVideoSnapshotUI(boolean enabled) {
        if (!mVideoContext.isVideoCaptureIntent()) {
            mVideoContext.showBorder(enabled);
            mVideoContext.getZoomManager().setEnabled(!enabled);
            mVideoContext.getShutterManager().setPhotoShutterEnabled(!enabled);
            Log.d(TAG, "showVideoSnapshotUI,enable shutter,enabled is " + enabled);
        }
    }

    private final class JpegPictureCallback implements PictureCallback {
        public JpegPictureCallback(Location loc) {
        }
        @Override
        public void onPictureTaken(byte[] jpegData, android.hardware.Camera camera) {
            Log.i(TAG, "onPictureTaken,storeImage");
            mPhotoSaveRequest.setData(jpegData);
            mPhotoSaveRequest.addRequest();
            mHandler.sendEmptyMessage(UPDATE_SNAP_UI);
        }
    }

    public void addVideoToMediaStore(boolean islivephoto) {
        if (mVideoFileDescriptor == null) {
            FileSaver filesaver = mVideoContext.getFileSaver();
            mVideoSaveRequest = filesaver.prepareVideoRequest(islivephoto? Storage.FILE_TYPE_LIV : Storage.FILE_TYPE_VIDEO, 
                    mProfile.fileFormat, Integer.toString(mProfile.videoFrameWidth) +
                        "x" + Integer.toString(mProfile.videoFrameHeight));
            mVideoSaveRequest.setLocation(mStartLocation);
            mVideoSaveRequest.setTempPath(mVideoTempPath);
            if (Storage.isStorageReady()) {
                mVideoSaveRequest.setDuration(computeDuration());
            }
            mVideoSaveRequest.setlivePhoto(islivephoto? 1 : 0); 
            if(mVideoContext.getSettingChecker().isSlowMotion() && ModePicker.MODE_VIDEO == mVideoContext.getCameraActor().getMode()) {
                mVideoSaveRequest.setSlowMotionSpeed(2 * mProfile.videoFrameRate / SLOW_MOTION_VIDEO_FILE_DEFAULT_FPS);
            }
            mVideoSaveRequest.setListener(new FileSaver.FileSaverListener() {
                public void onFileSaved(SaveRequest request) {
                    Log.i(TAG, "onFileSaved,notify,isOnsaveInstance = " + mVideoContext.isOnsaveInstance);
                    if (FeatureSwitcher.isSmartBookEnabled() && mVideoContext.isOnsaveInstance) {
                        // will check the thumbnail of video
                        mVideoContext.getThumbnailManager().forceUpdate();
                        mVideoContext.resetOnsaveInstanceState(false);
                    }
                    synchronized (mVideoSaveRequest) {
                        mVideoSaveRequest.notifyAll();
                    }
                }
            });
            
            // make sure the add save request run on UI thread.
            mHandler.post(new Runnable() {
               public void run() {
                   mVideoSaveRequest.addRequest();
               } 
            });
           
            /////////////////////////////////
            // changed for the smart book case
            // just the FeatureSwitcher is true & mIsSmartBookPlugged is true means is connected on smb,
            // we not go follow logic.
            // [because the SMB FeatureOption is open in all 92 project]
            // storage.isStorageReady() retrun false then don't wait or maybe notifyAll go before wait 
            Log.i(TAG,"getSamrtBookPluggedState = " +getContext().getSmartBookPluggedState());
            if ((!(FeatureSwitcher.isSmartBookEnabled()&& getContext().getSmartBookPluggedState())) && Storage.isStorageReady()) {
                try {
                    Log.i(TAG, "Wait for URI when saving video done");
                    synchronized (mVideoSaveRequest) {
                        mVideoSaveRequest.wait();
                    }
                } catch (InterruptedException e) {
                    Log.i(TAG, "Got notify from onFileSaved", e);
                }
            }
            mCurrentVideoUri = mVideoSaveRequest.getUri();
            mCurrentVideoFilename = mVideoSaveRequest.getFilePath();
            Log.i(TAG, "Saving video,mCurrentVideoUri==" + mVideoSaveRequest.getUri() + ",mCurrentVideoFilename="
                    + mVideoSaveRequest.getFilePath());
        }
    }

    @Override
    public void onMediaEject() {
        stopVideoRecordingAsync();
    }

    private void backToLastTheseCase() {
        mRecordingView.hide();
        mVideoContext.restoreViewState();
        backToLastModeIfNeed();
        return;
    }

    @Override
    public void onRestoreSettings() {
        if (!mVideoContext.isNonePickIntent()) {
            hideOtherSettings(false);
        } else {
            mVideoContext.getShutterManager().setPhotoShutterEnabled(true);
        }
    }
    public static void setSlowMotionVideoFileSpeed(MediaRecorder recorder, int value) {
        recorder.setParametersExtra("media-param-slowmotion=" + value);
    }

    public void onOrientationChanged(int orientation) {
    }
}
