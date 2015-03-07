package com.android.camera.actor;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

import android.graphics.Bitmap;
import android.media.CamcorderProfile;
import android.media.MediaActionSound;
import android.media.MediaMetadataRetriever;
import android.view.OrientationEventListener;

import com.android.camera.Camera;
import com.android.camera.FeatureSwitcher;
import com.android.camera.Log;
import com.android.camera.R;
import com.android.camera.Storage;
import com.android.camera.actor.VideoActor.SavingTask;
import com.android.camera.manager.MMProfileManager;
import com.android.camera.manager.ModePicker;
import com.android.camera.manager.ShutterManager;
import com.android.camera.ui.ShutterButton;
import com.android.camera.ui.ShutterButton.OnShutterButtonListener;

import com.mediatek.effect.effects.VideoScenarioEffect;
import com.mediatek.media.MediaRecorderEx;

public class VideoLivePhotoActor extends VideoActor {
    private static final String TAG = "VideoLivePhotoActor";
    private static final String BitmapFactory = null;
    private boolean mNeedBackGroundRecording = true;
    private boolean mFullScreen = true;
    private static final String TEMP = ".tmp";
    private MediaActionSound mCameraSound;
    private long mDuration = -1l;
    private boolean mCanStartPreviewNow = true;
    private boolean mIsReleased = false;
    private static Object sWaitForVideoProcessing = new Object(); 
    
    public VideoLivePhotoActor(Camera context) {
        super(context);
        Log.i(TAG, "VideoLivePhotoActor");
        mNeedBackGroundRecording = true;
        mIsReleased = false;
        mCameraSound = new MediaActionSound();
        // Not required, but reduces latency when playback is requested later.
        mCameraSound.load(MediaActionSound.SHUTTER_CLICK);
    }
    
    @Override
    public void playSound(int soundId) {
        mCameraSound.play(soundId);
    }
    
    @Override
    public void initializeShutterType() {
        Log.i(TAG, "initializeShutterType");
        if (mVideoContext.getSettingChecker().isSlowMotion()) {
            mVideoContext.switchShutter(ShutterManager.SHUTTER_TYPE_SLOW_VIDEO);
        } else {
            mVideoContext.switchShutter(ShutterManager.SHUTTER_TYPE_PHOTO_VIDEO);
        }
    }
    
    @Override
    public void initializeShutterStatus() {
    }
    
    private OnShutterButtonListener mPhotoShutterListener = new OnShutterButtonListener() {
        @Override
        public void onShutterButtonLongPressed(ShutterButton button) {
            mVideoContext.showInfo(mVideoContext.getString(R.string.livephoto_dialog_title) +
                    mVideoContext.getString(R.string.camera_continuous_not_supported));
        }
        @Override
        public void onShutterButtonFocus(ShutterButton button, boolean pressed) {
        
        }
        @Override
        public void onShutterButtonClick(ShutterButton button) {
            Log.i(TAG, "Photo.onShutterButtonClick mMediaRecorderRecording = " + mMediaRecorderRecording);
            Log.i(TAG, "Photo.onShutterButtonClick");
            if (isWfdEnable()) {
                mVideoContext.showInfo(mVideoContext.getString(R.string.wfd_live_photo_not_supported));
                return;
            }
            
            if (!mMediaRecorderRecording || !mFullScreen) {
                return;
            }
            MMProfileManager.startProfileStopVideoRecording();
            mVideoContext.setSwipingEnabled(false);
            onStopVideoRecordingAsync();
            MMProfileManager.stopProfileStopVideoRecording();
        }
    };
    
    @Override
    public OnShutterButtonListener getPhotoShutterButtonListener() {
        return mPhotoShutterListener;
    }
    
    @Override
    public OnShutterButtonListener getVideoShutterButtonListener() {
        return null;
    }
    
    @Override
    public void onCameraParameterReady(boolean startPreview) {
        Log.i(TAG, "onCameraParameterReady begin startPreview = " + startPreview +
                " getSurfaceTextureReady = " + mVideoContext.getSurfaceTextureReady()
                + " mCameraState = " + mVideoContext.getCameraState());
        //in this case,we should do start preview in setSurfaceTextureReady()
        mCanStartPreviewNow = mVideoContext.getSurfaceTextureReady() 
                                  && !isVideoProcessing() 
                                  && (mVideoContext.getCameraState() != Camera.STATE_IDLE);
        //power key off will call release, power key on will resume
        //when power on we should set mNeedBackGroundRecording and mVideoCameraClosed to true for do start preview after saving
        mNeedBackGroundRecording = true;
        if (mCanStartPreviewNow) {
            super.onCameraParameterReady(startPreview);
            //slide to gallery, live photo's media recorder should stop and release
            //back from gallery,live photo's media recorder will restart again
            mVideoContext.addOnFullScreenChangedListener(mFullScreenChangedListener);
        } else {
        	mHandler.sendEmptyMessage(PARAMETER_CHANGE_DONE);
        }
        Log.i(TAG, "onCameraParameterReady end mCanStartPreviewNow = " + mCanStartPreviewNow);
    }
    
    @Override
    public int getMode() {
        return ModePicker.MODE_LIVE_PHOTO;
    }
    
    @Override
    public void initializeRecorder() {
        Log.i(TAG, "startVideoRecording initializeRecorder");
        super.initializeRecorder();
        MediaRecorderEx.setLivePhotoMode(mMediaRecorder);
    }
    
    @Override
    protected void setOrientationHint(int orientation) {
        Log.i(TAG, "setOrientationHint mMediaRecorder = " + mMediaRecorder);
        if (mMediaRecorder != null) {
            mMediaRecorder.setOrientationHint(0);
        }
    }
    
    @Override
    public void doStartPreview() {
        Log.i(TAG, "doStartPreview " + " mVideoCameraClosed = " + mVideoCameraClosed + " mFullScreen = " + mFullScreen
                + " isVideoProcessing = " + isVideoProcessing() + " mIsReleased = " + mIsReleased);
        super.doStartPreview();
        if(mVideoContext.getRemainingManager() != null) {
            mVideoContext.getRemainingManager().updateStorage();	
        }
        // in live photo mode,recording is begin after start preview
        if (Storage.getLeftSpace() <= 0 || mVideoCameraClosed || !mFullScreen
                || isVideoProcessing()
                || !mVideoContext.getSurfaceTextureReady() || mIsReleased || isWfdEnable()) {
            return;
        }
        if (mMediaRecorderRecording) {
            MMProfileManager.startProfileStopVideoRecording();
            //onStopVideoRecordingAsync and startVideoRecording
            //will operate shuttermanager,so should do in UI thread
            mVideoContext.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    onStopVideoRecordingAsync();
                }
            });
            MMProfileManager.stopProfileStopVideoRecording();
        } else {
            unlockOrientation();
            MMProfileManager.startProfileStartVideoRecording();
            mVideoContext.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    startVideoRecording();
                }
            });
            MMProfileManager.stopProfileStartVideoRecording();
            // we should enable swiping when cannot record
            mVideoContext.setSwipingEnabled(true);
        }
    }
    
    @Override
    public void startRecording() {
        startNormalRecording();
    }
    
    @Override
    protected void pauseAudioPlayback() {
        //live photo no need audio, so is not need to pause other audio
    }
    
    @Override
    protected void initVideoRecordingFirst() {
        super.initVideoRecordingFirst();
        //no need to recording sound and time lapse in live photo mode
        mRecordAudio = false;
        mCaptureTimeLapse = false;
    }
    
    @Override
    public void setSurfaceTextureReady(boolean ready) {
        Log.i(TAG, "setSurfaceTextureReady ready = " + ready + " mCanStartPreviewNow = " + mCanStartPreviewNow);
        super.setSurfaceTextureReady(ready);
        if (!mCanStartPreviewNow) {
            onCameraParameterReady(true);
        }
    }
    
   //live photo recording is no need recording view
    @Override
    public void initializeRecordingView() {
        return;
    }
    
    @Override
    protected void updateRecordingTime() {
        //override parent class
        Log.i(TAG, "updateRecordingTime mMediaRecorder = " + mMediaRecorder);
        return;
    }
    
    private Camera.OnFullScreenChangedListener mFullScreenChangedListener = new Camera.OnFullScreenChangedListener() {
        @Override
        public void onFullScreenChanged(boolean full) {
            Log.i(TAG, "onFullScreenChanged full = " + full + " isVideoProcessing = " + isVideoProcessing());
            mFullScreen = full;
            if (isVideoProcessing()) {
                Log.i(TAG, "video is processing");
                synchronized (sWaitForVideoProcessing) {
                    try {
                        Log.i(TAG, "Wait for video processing");
                        sWaitForVideoProcessing.wait();
                    } catch (InterruptedException e) {
                        Log.w(TAG, "Got notify from video processing", e);
                    }
                }
            }
            if (full && !mMediaRecorderRecording) {
                Log.i(TAG, "onFullScreenChanged start video recording");
                mNeedBackGroundRecording = true;
                mVideoContext.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        startVideoRecording();
                    }
                });
            } else if (mMediaRecorderRecording) {
                Log.i(TAG, "onFullScreenChanged stop video recording");
                mNeedBackGroundRecording = false;
                stopVideoRecordingAsync();
            }
        }
    };
    
    @Override
    public void addVideoToMediaStore(boolean islivephoto) {
        Log.i(TAG, "addVideoToMediaStore mCurrentVideoFilename = " + mCurrentVideoFilename);
        //live photo will post-processing video
        if (!mNeedBackGroundRecording) {
            releaseMediaRecorder();
            deleteCurrentVideo();
            Log.i(TAG, "addVideoToMediaStore deleteCurrentVideo !!!!!!!!!");
            return;
        }
        Log.i(TAG, "new VideoScenarioEffect begin");
        VideoScenarioEffect vv = new VideoScenarioEffect();
        Log.i(TAG, "new VideoScenarioEffect end");
        //mDuration will be changed here
        Bitmap lastFrame = createVideoLastFramePicture();
        int rotation = 0;
        boolean result = false;
        try {
            if (mProfile != null && lastFrame !=null) {
                mOrientation = mVideoContext.getOrietation();
                rotation = getRecordingRotation(mOrientation);
                Log.i(TAG, "MFF setScenario begin mRotation = " + rotation);
                if(vv.setScenario(mVideoContext,
                    getScenario(rotation, mProfile.videoFrameWidth, mProfile.videoFrameHeight, mDuration,
                            mCurrentVideoFilename, generateVideoFilename(mProfile.fileFormat, "livephoto")),
                    mProfile, lastFrame, lastFrame)) {
                    Log.i(TAG, "MFF setScenario end");
                    Log.i(TAG, "MFF Process begin");
                    result = vv.process();
                    Log.i(TAG, "MFF Process end result = " + result);
                }
            }
        } catch(Exception e) {
            e.printStackTrace();
        }
        //delete original video
        deleteCurrentVideo();
        //after delete original video,current video file is live photo
        mCurrentVideoFilename = mVideoFilename;
        super.addVideoToMediaStore(true);
    }
    
    private Bitmap createVideoLastFramePicture() {
        Log.i(TAG, "createVideoLastFramePicture begin");
        Bitmap bitmap = null;
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try {
            retriever.setDataSource(mCurrentVideoFilename);
            mDuration =  Long.valueOf(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION));
            bitmap = retriever.getFrameAtTime((mDuration - 200) * 1000, MediaMetadataRetriever.OPTION_NEXT_SYNC);
            Log.i(TAG, "createVideoLastFramePicture bitmap = " + bitmap + " duration = " + mDuration);
        } catch (IllegalArgumentException ex) {
            ex.printStackTrace();
        } catch (RuntimeException ex) {
            ex.printStackTrace();
        } finally {
            try {
                retriever.release();
            } catch (RuntimeException ex) {
                // Ignore failures while cleaning up.
                ex.printStackTrace();
            }
        }
        if (bitmap == null) { return null; }
       
        Log.i(TAG, "bitmap = " + bitmap.getWidth() + "x" + bitmap.getHeight());
        Log.i(TAG, "createVideoLastFramePicture end");
        return bitmap;
    }
    
    @Override
    protected boolean backToLastModeIfNeed() {
        Log.i(TAG, "backToLastModeIfNeed mNeedBackGroundRecording = " + mNeedBackGroundRecording 
                + " mVideoCameraClosed = " + mVideoCameraClosed);
        if (mNeedBackGroundRecording && !mVideoCameraClosed) {
            onCameraParameterReady(true);
        }
        return false;
    }
    
    @Override
    public void hideOtherSettings(boolean hide) {
        //when hide is true,live photo actor should not do
        //mVideoContext.setViewState(Camera.VIEW_STATE_RECORDING)
        if(!hide) {
            mVideoContext.restoreViewState();
        }
    }
    
    @Override
    public void release() {
        Log.i(TAG, "release");
        mRecorderBusy = false;
        mIsReleased = true;
        mCameraSound.release();
        mVideoContext.removeOnFullScreenChangedListener(mFullScreenChangedListener);
        //use stopVideoOnPause can wait for media recorder release
        stopVideoOnPause();
    }
    
    @Override
    public void onCameraOpenDone() {
        super.onCameraOpenDone();
        mVideoCameraClosed = false;
        mIsReleased = false;
    }
    
    @Override
    public void onCameraClose() {
        mIsReleased = true;
        mVideoContext.removeOnFullScreenChangedListener(mFullScreenChangedListener);
        super.onCameraClose();
    }
    
    @Override
    public void stopVideoRecordingAsync() {
        Log.i(TAG, "stopVideoRecordingAsync() mMediaRecorderRecording=" + mMediaRecorderRecording + ", mRecorderBusy="
                + mRecorderBusy);
        Log.i(TAG, "stopVideoRecordingAsync");
        mVideoContext.getZoomManager().changeZoomForQuality();
        mVideoContext.getShutterManager().setVideoShutterMask(false);
        if (isVideoProcessing()) {
            return;
        }
        if (mRecorderBusy) {
            return;
        }
        mRecorderBusy = true;
        if (mMediaRecorderRecording) {
            if (mStoppingAction != STOP_RETURN_UNVALID && mCallFromOnPause != true && mNeedBackGroundRecording) {
                mVideoContext.getShutterManager().setVideoShutterEnabled(false);
                mVideoContext.showProgress(mVideoContext.getResources().getString(R.string.saving_livephoto));
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
    
    public void stopVideoOnPause(){
        Log.i(TAG, "stopVideoOnPause mMediaRecorderRecording = " + mMediaRecorderRecording);
        mNeedBackGroundRecording = false;
        super.stopVideoOnPause();
    }

    @Override
   public void stopRecording() {
        Log.i(TAG, "stopRecording mNeedBackGroundRecording = " + mNeedBackGroundRecording);
        if (mNeedBackGroundRecording) {
            Log.i(TAG, "captureLivePhoto begin");
            MediaRecorderEx.captureLivePhoto(mMediaRecorder);
            super.stopPreview();
            lockOrientation();
            Log.i(TAG, "captureLivePhoto end");
            playSound(MediaActionSound.SHUTTER_CLICK);
        }
        super.stopRecording();
    }

    @Override
    public boolean onBackPressed() {
        Log.i(TAG, "onBackPressed() isFinishing()=" + mVideoContext.isFinishing() + ", mVideoCameraClosed="
                + mVideoCameraClosed + ", isVideoProcessing()=" + isVideoProcessing()
                + ",mVideoContext.isShowingProgress() = " + mVideoContext.isShowingProgress());
        if (mVideoCameraClosed || (mVideoContext.isShowingProgress()) || isVideoProcessing()) {
            return true;
        }
        return false;
    }

    @Override
    public void doAfterStopRecording(boolean fail) {
        Log.i(TAG,"doAfterStopRecording fail = " + fail + " mNeedBackGroundRecording = " + mNeedBackGroundRecording);
        if (mNeedBackGroundRecording) {
            super.doAfterStopRecording(fail);
        } else {
            Log.i(TAG, "doAfterStopRecording deleteCurrentVideo !!!!!!!!!");
            mCurrentVideoFilename = mVideoFilename;
            deleteCurrentVideo();
            releaseMediaRecorder();
            mRecorderBusy = false;
            //switch camera, stopVideoOnPause will wait to notifyall
            synchronized (mVideoSavingTask) {
                Log.i(TAG, "notify for releasing camera");
                mVideoSavingTask.notifyAll();
            }
            synchronized (sWaitForVideoProcessing) {
                Log.i(TAG, "notify for video processing");
                mMediaRecorderRecording = false;
                sWaitForVideoProcessing.notifyAll();
            }
            mVideoContext.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    mVideoContext.dismissProgress();
                }
            });
        }
    }
    
    @Override
    protected void releaseMediaRecorder() {
        Log.i(TAG, "releaseMediaRecorder begin");
        super.releaseMediaRecorder();
        Log.i(TAG, "releaseMediaRecorder end");
    }
    
    private void lockOrientation() {
        mVideoContext.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mVideoContext.setOrientation(true,
                    OrientationEventListener.ORIENTATION_UNKNOWN);
            }
        });
    }
    
    private void unlockOrientation() {
        mVideoContext.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mVideoContext.setOrientation(false, 
                        OrientationEventListener.ORIENTATION_UNKNOWN);
            }
        });
    }
    
    private String getScenario(int orientation, int width, int height, long duration ,String inputPath, String outputPath) {
        Log.i(TAG, "getScenario begin");
        String fixBitmap = "object1";
        String scenario =  
            "<?xml version=\"1.0\"?>" +
            "<scenario>" +
            "   <size orientation= \"" + orientation + "\" owidth=\"" + width + "\" oheight=\"" + height + "\"></size>" +
            "   <video>/system/media/video/gen30.mp4</video>" +   // we shall has this to make sure the timestamp and to trigger the scenario
            "   <video>" + inputPath + "</video>" +     // the second video to be transcoded
            "   <edge>/system/media/video/edge720p.png</edge>" +  // the edge frame
            "   <outputvideo livephoto=\"1\">" + outputPath + "</outputvideo>" +  // the output file

            "   <videoevent name=\"ve\" type=\"still\" start=\"0\" end=\"1500\">" +
            "   <background>" + fixBitmap + "</background>" +
            "   </videoevent>" +

            "   <videoevent name=\"ve\" type=\"overlay\" start=\"1500\" end=\"2000\">" +
            "   <showtime related_start=\"0\" length=\"500\"></showtime>" +
            "   <thumbnail move=\"1\">" + fixBitmap + "</thumbnail>" +
            "   <background still=\"1\" fade_in=\"1\">video2</background>" +
            "   </videoevent>" +

            "   <videoevent name=\"ve\" type=\"overlay\" start=\"1900\" end=\"" + (2000 + duration) + "\">" +
            "   <showtime related_start=\"100\" length=\"" + duration + "\"></showtime>" +
            "   <thumbnail>" + fixBitmap + "</thumbnail>" +
            "   <background>video2</background>" +
            "   </videoevent>" +
            
            "   <videoevent name=\"ve\" type=\"overlay\" start=\"" + (2000 + duration) + "\" end=\"" + (2300 + duration) + "\">" +
            "   <showtime related_start=\"0\" length=\"300\"></showtime>" +
            "   <thumbnail fade_out=\"1\">" + fixBitmap + "</thumbnail>" +
            "   <background still=\"1\">" + fixBitmap + "</background>" +
            "   </videoevent>" +

            "   <videoevent name=\"ve\" type=\"still\" start=\"" + (2300 + duration) + "\" end=\"" + (2301 + duration) + "\">" +
            "   <background>" + fixBitmap + "</background>" +
            "   </videoevent>" +
            "</scenario>";
        Log.i(TAG, "getScenario end");
        return scenario;
    }
}
