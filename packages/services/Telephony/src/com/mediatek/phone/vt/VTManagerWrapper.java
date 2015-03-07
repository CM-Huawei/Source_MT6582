
package com.mediatek.phone.vt;

import android.content.Context;
import android.os.AsyncResult;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.Surface;
import android.widget.Toast;

import com.android.internal.telephony.PhoneConstants;
import com.android.phone.PhoneGlobals;
import com.android.phone.PhoneUtils;
import com.android.phone.R;
import com.android.services.telephony.common.VTManagerParams;
import com.mediatek.phone.recording.PhoneRecorderHandler;
import com.mediatek.vt.VTManager;

public class VTManagerWrapper {
    private final static String TAG = "VTManagerWrapper";
    private final static VTManagerWrapper sInstance = new VTManagerWrapper();

    public static final int VT_RESULT_SWITCHCAMERA_OK = 128;
    public static final int VT_RESULT_SWITCHCAMERA_FAIL = 129;
    public static final int VT_RESULT_PEER_SNAPSHOT_OK = 126;
    public static final int VT_RESULT_PEER_SNAPSHOT_FAIL = 127;
    private static final int VT_TAKE_PEER_PHOTO_DISK_MIN_SIZE = 1000000;
    private static final int VT_MEDIA_RECORDER_ERROR_UNKNOWN = 1;
    private static final int VT_MEDIA_RECORDER_NO_I_FRAME = 0x7FFF;
    private static final int VT_MEDIA_RECORDER_INFO_MAX_FILESIZE_REACHED = 801;
    private static final int VT_MEDIA_OCCUPIED = 1;
    private static final int VT_MEDIA_ERROR_VIDEO_FAIL = 1;

    private VTManager mVTManager = null;

    private boolean mSurfaceReady = false;
    protected boolean mHasMediaRecordError;

    private VTManagerWrapper() {
        mVTManager = VTManager.getInstance();
    }

    public static VTManagerWrapper getInstance() {
        return sInstance;
    }

    public void registerDefaultVTListener() {
        mVTManager.registerVTListener(mHandler);
    }

    public VTManager getVTManager() {
        return mVTManager;
    }

    public void setDisplay(Surface local, Surface peer) {
        mSurfaceReady = (local != null && peer != null);

        Log.i(TAG, "setDisplay " + local + ", " + peer);
        VTManager.getInstance().setDisplay(local, peer);
    }

    public void setVTVisible(boolean isVisible) {
        if (mVTManager.getState() == VTManager.State.CLOSE || VTCallUtils.isVTIdle()) {
            log("setVTVisible() error --> called when VTManager is CLOSE or VT Call is IDLE.");
            return;
        }
        Log.i(TAG, "setVTVisible : " + isVisible + ", mSurfaceReady=" + mSurfaceReady);
        if (isVisible) {
            if (mSurfaceReady) {
                log("- call VTManager.setVTVisible(true) begin ! ");
                mVTManager.setVTVisible(isVisible);
                log("- call VTManager.setVTVisible(true) end ! ");
            }
        } else {
            log("- call VTManager.setVTVisible(false) begin ! ");
            mVTManager.setVTVisible(isVisible);
            log("- call VTManager.setVTVisible(false) end ! ");
        }
    }

    public void setVTOpen(Context context, int slotId) {
        Log.i(TAG, "setVTOpen VT State: " + mVTManager.getState() + ", slotId=" + slotId);
        if (mVTManager.getState() == VTManager.State.CLOSE) {
            log("- call VTManager.setVTOpen() begin ! ");
            mVTManager.setVTOpen(context, slotId);
            log("- call VTManager.setVTOpen() end ! ");
        }
    }

    public void setVTReady() {
        Log.i(TAG, "setVTReady VT State: " + mVTManager.getState() + ", mSurfaceReady=" + mSurfaceReady);
        if (mVTManager.getState() == VTManager.State.OPEN && mSurfaceReady) {
            // For ALPS01234020 open speaker for VT;
            PhoneUtils.setSpeakerForVT(true);
            log("- call VTManager.setVTReady() begin ! ");
            mVTManager.setVTReady();
            log("- call VTManager.setVTReady() end ! ");
        }
    }

    /**
     * Check VT Call status and call setVTConnected()
     *
     * @see PHONE_VT_STATUS_INFO
     * @param asyncResult VT call status: 0 is active, 1 is disconnected
     */
    public void handleVTStatusInfo(AsyncResult asyncResult, PhoneConstants.State state) {
        boolean isDisconent = false;
        if (null != asyncResult) {
            final int result = ((int[]) asyncResult.result)[0];
            isDisconent = result != 0;
            log("handleVTStatusInfo, result=" + result);
        }

        VTInCallScreenFlags.getInstance().mVTStatusActive = !isDisconent;

        if (state == PhoneConstants.State.IDLE) {
            log("handleVTStatusInfo, IDLE, just return! ");
            return;
        }

        VTManagerWrapper.getInstance().setVTConnected();
    }

    /**
     * Check CallManager PHONE_VT_STATUS_INFO Message
     * (VTInCallScreenFlags.getInstance().mVTStatusActive) and VT Status
     * (VTManager.State.READY), if all ready, call VTManager.setVTConnected()
     */
    public void setVTConnected() {
        Log.i(TAG, "setVTConnected VT State: " + mVTManager.getState());
        if (mVTManager.getState() == VTManager.State.READY
                && VTInCallScreenFlags.getInstance().mVTStatusActive) {
            log("- call VTManager.setVTConnected() begin ! ");
            mVTManager.setVTConnected();
            log("- call VTManager.setVTConnected() end ! ");
        }
    }

    public void setVTClose() {
        if (mVTManager.getState() != VTManager.State.CLOSE) {
            log("- call VTManager.setVTClose() begin ! ");
            mVTManager.setVTClose();
            log("- call VTManager.setVTClose() end ! ");
        }
    }

    public void onDisconnected() {
        log("- call VTManager.onDisconnected() begin ! ");
        mVTManager.onDisconnected();
        log("- call VTManager.onDisconnected() end ! ");
    }

    private Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            log("VTManagerWrapper handleMessage message:" + msg);

            switch (msg.what) {

            case VTManager.VT_MSG_OPEN:
                log("- handler : VT_MSG_OPEN ! ");
                setVTReady();
                VTCallUtils.updateLocalViewToVTManager();
                mListener.onVTStateChanged(msg.what);
                pushVTManagerParams();
                break;

            case VTManager.VT_MSG_READY:
                log("- handler : VT_MSG_READY ! ");
                setVTConnected();
                mListener.onVTStateChanged(msg.what);
                pushVTManagerParams();
                break;

            case VTManager.VT_MSG_CONNECTED:
                log("- handler : VT_MSG_CONNECTED ! ");
                PhoneUtils.setAudioMode();
                pushVTManagerParams();
                mListener.onVTStateChanged(msg.what);
                break;

            case VTManager.VT_MSG_DISCONNECTED:
                log("- handler : VT_MSG_DISCONNECTED ! ");
                mListener.onVTStateChanged(msg.what);
                break;

            case VTManager.VT_MSG_CLOSE:
                log("- handler : VT_MSG_CLOSE ! ");
                mListener.onVTStateChanged(msg.what);
                pushVTManagerParams();
                break;

            case VTManager.VT_MSG_RECEIVE_FIRSTFRAME:
                log("- handler : VT_MSG_RECEIVE_FIRSTFRAME ! ");
                mListener.onVTStateChanged(msg.what);
                break;

            case VTManager.VT_MSG_START_COUNTER:
                log("- handler : VT_MSG_START_COUNTER ! ");
                PhoneGlobals.getInstance().notifier.onReceiveVTManagerStartCounter();
                
                /// M: for ALPS01425992 @{
                // To call onVTStateChanged to notify InCallUI start count.
                mListener.onVTStateChanged(msg.what);
                /// @}
                break;

            case VTManager.VT_MSG_EM_INDICATION:
                log("- handler : VT_MSG_EM_INDICATION ! ");
                showToast((String) msg.obj);
                break;

            case VTManager.VT_ERROR_CALL_DISCONNECT:
                log("- handler : VT_ERROR_CALL_DISCONNECT ! ");
                PhoneUtils.hangupAllCalls();
                showToast(PhoneGlobals.getInstance().getResources().getString(R.string.vt_error_network));
                break;

            case VTManager.VT_NORMAL_END_SESSION_COMMAND:
                log("- handler : VT_NORMAL_END_SESSION_COMMAND ! ");
                PhoneUtils.hangupAllCalls();
                break;

            case VTManager.VT_ERROR_START_VTS_FAIL:
                log("- handler : VT_ERROR_START_VTS_FAIL ! ");
                PhoneUtils.hangupAllCalls();
                if (VT_MEDIA_ERROR_VIDEO_FAIL == msg.arg2) {
                    showToast(PhoneGlobals.getInstance().getResources().getString(R.string.vt_media_video_fail));
                } else {
                    showToast(PhoneGlobals.getInstance().getResources().getString(R.string.vt_error_media));
                }
                break;

            case VTManager.VT_ERROR_CAMERA:
                log("- handler : VT_ERROR_CAMERA ! ");
                PhoneUtils.hangupAllCalls(true, null);
                if (VT_MEDIA_OCCUPIED == msg.arg2) {
                    showToast(PhoneGlobals.getInstance().getResources().getString(R.string.vt_media_occupied));
                } else {
                    showToast(PhoneGlobals.getInstance().getResources().getString(R.string.vt_error_media));
                }
                break;

            case VTManager.VT_ERROR_MEDIA_SERVER_DIED:
                log("- handler : VT_ERROR_MEDIA_SERVER_DIED ! ");
                PhoneUtils.hangupAllCalls();
                showToast(PhoneGlobals.getInstance().getResources().getString(R.string.vt_error_media));
                break;

            case VTManager.VT_ERROR_MEDIA_RECORDER_EVENT_INFO:
                log("- handler : VT_ERROR_MEDIA_RECORDER_EVENT_INFO ! ");
                if (VT_MEDIA_RECORDER_NO_I_FRAME == msg.arg1) {
                    showToast(PhoneGlobals.getInstance().getResources().getString(R.string.vt_recorder_only_voice));
                } else if (VT_MEDIA_RECORDER_INFO_MAX_FILESIZE_REACHED == msg.arg1) {
                    PhoneRecorderHandler.getInstance().stopRecording();
                    if (PhoneRecorderHandler.getInstance().getListener() != null) {
                        PhoneRecorderHandler.getInstance().getListener().onStorageFull(); // false for recording case
                    }
                }
                break;

            case VTManager.VT_ERROR_MEDIA_RECORDER_EVENT_ERROR:
                log("- handler : VT_ERROR_MEDIA_RECORDER_EVENT_ERROR ! ");
                if (VT_MEDIA_RECORDER_ERROR_UNKNOWN == msg.arg1 && !mHasMediaRecordError) {
                    showToast(PhoneGlobals.getInstance().getResources().getString(R.string.vt_recording_error));
                    /// M: For ALPS00568488 @{
                    // We need a flag to indicate if any error happen when recording.
                    // If recording error happen mark it.
                    mHasMediaRecordError = true;
                    /// @}
                    PhoneRecorderHandler.getInstance().stopRecording();
                }
                break;

            case VTManager.VT_ERROR_MEDIA_RECORDER_COMPLETE:
                log("- handler : VT_ERROR_MEDIA_RECORDER_COMPLETE ! ");
                int ok = 0;
                if (ok == msg.arg1 && !mHasMediaRecordError) {
                    log("- handler : VT_ERROR_MEDIA_RECORDER_COMPLETE, arg is OK ");
                    showToast(PhoneGlobals.getInstance().getResources().getString(R.string.vt_recording_saved));
                }

                /// M: For ALPS00568488 @{
                // We need a flag to indicate if any error happen when recording.
                // When recording complete, reset it.
                mHasMediaRecordError = false;
                /// @}
                break;

            case VTManager.VT_MSG_PEER_CAMERA_OPEN:
                log("- handler : VT_MSG_PEER_CAMERA_OPEN ! ");
                showToast(PhoneGlobals.getInstance().getResources().getString(R.string.vt_peer_camera_open));
                break;

            case VTManager.VT_MSG_PEER_CAMERA_CLOSE:
                log("- handler : VT_MSG_PEER_CAMERA_CLOSE ! ");
                showToast(PhoneGlobals.getInstance().getResources().getString(R.string.vt_peer_camera_close));
                break;

            case VT_RESULT_PEER_SNAPSHOT_OK:
                log("- handler : VT_RESULT_PEER_SNAPSHOT_OK ! ");
                showToast(PhoneGlobals.getInstance().getResources().getString(R.string.vt_pic_saved_to_sd));
                break;

            case VT_RESULT_PEER_SNAPSHOT_FAIL:
                log("- handler : VT_RESULT_PEER_SNAPSHOT_FAIL ! ");
                showToast(PhoneGlobals.getInstance().getResources().getString(R.string.vt_pic_saved_to_sd_fail));
                break;

            default:
                Log.wtf(TAG, "mHandler: unexpected message: " + msg);
                break;
            }
        }
    };

    private Listener mListener;
    
    public void setListener(Listener listener) {
        mListener = listener;
    }

    public interface Listener {
        void onVTStateChanged(int msgVT);
        void pushVTManagerParams(VTManagerParams params);
    }

    private VTManagerParams mVTManagerParams = new VTManagerParams();
    private void updateVTManagerParams() {
        mVTManagerParams.mCameraSensorCount = mVTManager.getCameraSensorCount();
        mVTManagerParams.mVideoQuality = mVTManager.getVideoQuality();
        mVTManagerParams.mCanDecBrightness = mVTManager.canDecBrightness();
        mVTManagerParams.mCanIncBrightness = mVTManager.canIncBrightness();
        mVTManagerParams.mCanDecZoom = mVTManager.canDecZoom();
        mVTManagerParams.mCanIncZoom = mVTManager.canIncZoom();
        mVTManagerParams.mCanDecContrast = mVTManager.canDecContrast();
        mVTManagerParams.mCanIncContrast = mVTManager.canIncContrast();
        mVTManagerParams.mIsSupportNightMode = mVTManager.isSupportNightMode();
        mVTManagerParams.mIsNightModeOn = mVTManager.getNightMode();
        mVTManagerParams.mColorEffect = mVTManager.getColorEffect();
        mVTManagerParams.mSupportedColorEffects = mVTManager.getSupportedColorEffects();
        if (mVTManager.getColorEffect() != null) {
            log("updateVTManagerParams / mVTManager.getColorEffect(): " + mVTManager.getColorEffect());
        }
        if (mVTManager.getSupportedColorEffects() != null) {
            log("updateVTManagerParams / mVTManager.getSupportedColorEffects(): " + mVTManager.getSupportedColorEffects().size());
        }
    }

    private void pushVTManagerParams() {
        // For ALPS01383116 & ALPS01435927 & ALPS01439919,
        // we can't call VTManager's methods when VTService is down;
        // But here we can't not use !VTCallUtils.isVTCallActive(),
        // for VT-Dialing, should show menu "switch camera", which is triggered by this function
        if (mVTManager.getState() == VTManager.State.CLOSE || VTCallUtils.isVTIdle()) {
            log("pushVTManagerParams() error --> called when VTManager is CLOSE or VT Call is IDLE.");
            return;
        }
        updateVTManagerParams();
        mListener.pushVTManagerParams(mVTManagerParams);
    }

    private void showToast(String string) {
        Toast.makeText(PhoneGlobals.getInstance(), string, Toast.LENGTH_LONG).show();
    }

    private void log(String msg) {
        Log.d(TAG, msg);
    }

    public void switchCamera() {
        if (mVTManager.getState() == VTManager.State.CLOSE) {
            log("switchCamera() error --> called when VTManager is CLOSE.");
            mListener.onVTStateChanged(VT_RESULT_SWITCHCAMERA_FAIL);
            return;
        }
        // because switch camera may spend 2-4 second
        // new a thread to finish it so that it cannot block UI update
        (new Thread() {
            public void run() {
                boolean result = VTManager.getInstance().switchCamera();
                log("VTManager.switchCamera() result=" + result);
                if (result) {
                    // VTManager will show toast to notify user if switch fail,
                    // so here we just notify UI the operation is done.
                     mListener.onVTStateChanged(VT_RESULT_SWITCHCAMERA_OK);
                } else {
                     mListener.onVTStateChanged(VT_RESULT_SWITCHCAMERA_FAIL);
                }
                pushVTManagerParams();
            }
        }).start();
    }

    public void savePeerPhoto() {
        if (!PhoneUtils.isExternalStorageMounted()) {
            log("onVTTakePeerPhotoClick: failed, SD card is full.");
            Toast.makeText(PhoneGlobals.getInstance(),
                    PhoneGlobals.getInstance().getResources().getString(R.string.vt_sd_null), Toast.LENGTH_SHORT).show();
            // here we need notify UI the operation is done, can do this operation again.
            mListener.onVTStateChanged(VT_RESULT_PEER_SNAPSHOT_FAIL);
            return;
        }

        if (!PhoneUtils.diskSpaceAvailable(VT_TAKE_PEER_PHOTO_DISK_MIN_SIZE)) {
            log("onVTTakePeerPhotoClick: failed, SD card space is not enough.");
            Toast.makeText(PhoneGlobals.getInstance(),
                    PhoneGlobals.getInstance().getResources().getString(R.string.vt_sd_not_enough), Toast.LENGTH_SHORT).show();
            // here we need notify UI the operation is done, can do this operation again.
            mListener.onVTStateChanged(VT_RESULT_PEER_SNAPSHOT_FAIL);
            return;
        }

        if (mVTManager.getState() == VTManager.State.CLOSE || !VTCallUtils.isVTCallActive()) {
            log("savePeerPhoto() error --> called when VTManager is CLOSE or no active VT call exist.");
            mListener.onVTStateChanged(VT_RESULT_PEER_SNAPSHOT_FAIL);
            return;
        }

        (new Thread() {
            public void run() {
                boolean ret = VTManager.getInstance().savePeerPhoto();
                log("onVTTakePeerPhotoClick(): VTManager.savePeerPhoto(), return " + ret);
                if (ret) {
                    mHandler.sendMessage(Message.obtain(mHandler, VT_RESULT_PEER_SNAPSHOT_OK));
                    // here we need notify UI the operation is done, can do this operation again.
                    mListener.onVTStateChanged(VT_RESULT_PEER_SNAPSHOT_OK);
                } else {
                    mHandler.sendMessage(Message.obtain(mHandler, VT_RESULT_PEER_SNAPSHOT_FAIL));
                    mListener.onVTStateChanged(VT_RESULT_PEER_SNAPSHOT_FAIL);
                }
            }
        }).start();
    }

    public void hideLocal(boolean on) {
        if (mVTManager.getState() == VTManager.State.CLOSE) {
            log("hideLocal() error --> called when VTManager is CLOSE.");
            return;
        }
        log("onVTHideMeClick()...on: " + on);
        if (on) {
            VTCallUtils.updatePicToReplaceLocalVideo();
        } else {
            VTManager.getInstance().setLocalView(0, "");
        }
    }

    public void setNightMode(boolean isOnNight) {
        if (mVTManager.getState() == VTManager.State.CLOSE || !VTCallUtils.isVTCallActive()) {
            log("setNightMode() error --> called when VTManager is CLOSE or no active VT call exist.");
            return;
        }
        log("setNightMode()...isOnNight: " + isOnNight);
        VTManager.getInstance().setNightMode(isOnNight);
        pushVTManagerParams();
    }

    public void setVideoQuality(int quality) {
        if (mVTManager.getState() == VTManager.State.CLOSE || !VTCallUtils.isVTCallActive()) {
            log("setVideoQuality() error --> called when VTManager is CLOSE or no active VT call exist.");
            return;
        }
        log("setVideoQuality()...quality: " + quality);
        VTManager.getInstance().setVideoQuality(quality);
        pushVTManagerParams();
    }

    public void setColorEffect(String colorEffect) {
        if (mVTManager.getState() == VTManager.State.CLOSE || !VTCallUtils.isVTCallActive()) {
            log("setColorEffect() error --> called when VTManager is CLOSE or no active VT call exist.");
            return;
        }
        log("setColorEffect()...colorEffect: " + colorEffect);
        VTManager.getInstance().setColorEffect(colorEffect);
        pushVTManagerParams();
    }

    public void decZoom() {
        if (mVTManager.getState() == VTManager.State.CLOSE || !VTCallUtils.isVTCallActive()) {
            log("decZoom() error --> called when VTManager is CLOSE or no active VT call exist.");
            return;
        }
        log("decZoom()...");
        VTManager.getInstance().decZoom();
        pushVTManagerParams();
    }

    public void incZoom() {
        if (mVTManager.getState() == VTManager.State.CLOSE || !VTCallUtils.isVTCallActive()) {
            log("incZoom() error --> called when VTManager is CLOSE or no active VT call exist.");
            return;
        }
        log("incZoom()...");
        VTManager.getInstance().incZoom();
        pushVTManagerParams();
    }

    public void incBrightness() {
        if (mVTManager.getState() == VTManager.State.CLOSE || !VTCallUtils.isVTCallActive()) {
            log("incBrightness() error --> called when VTManager is CLOSE or no active VT call exist.");
            return;
        }
        log("incBrightness()...");
        VTManager.getInstance().incBrightness();
        pushVTManagerParams();
    }

    public void decBrightness() {
        if (mVTManager.getState() == VTManager.State.CLOSE || !VTCallUtils.isVTCallActive()) {
            log("decBrightness() error --> called when VTManager is CLOSE or no active VT call exist.");
            return;
        }
        log("decBrightness()...");
        VTManager.getInstance().decBrightness();
        pushVTManagerParams();
    }

    public void incContrast() {
        if (mVTManager.getState() == VTManager.State.CLOSE || !VTCallUtils.isVTCallActive()) {
            log("incContrast() error --> called when VTManager is CLOSE or no active VT call exist.");
            return;
        }
        log("incContrast()...");
        VTManager.getInstance().incContrast();
        pushVTManagerParams();
    }

    public void decContrast() {
        if (mVTManager.getState() == VTManager.State.CLOSE || !VTCallUtils.isVTCallActive()) {
            log("decContrast() error --> called when VTManager is CLOSE or no active VT call exist.");
            return;
        }
        log("decContrast()...");
        VTManager.getInstance().decContrast();
        pushVTManagerParams();
    }

    public void updatePicToReplaceLocalVideo() {
        log("updatePicToReplaceLocalVideo()...");
        VTCallUtils.updatePicToReplaceLocalVideo();
    }

    public void onUserInput(String input) {
        if (mVTManager.getState() == VTManager.State.CLOSE) {
            log("onUserInput() error --> called when VTManager is CLOSE.");
            return;
        }
        log("onUserInput()... input: " + input);
        mVTManager.onUserInput(input);
    }

    public void incomingVTCall(int flag) {
        log("incomingVTCall()... flag: " + flag);
        mVTManager.incomingVTCall(flag);
    }

    public void startRecording(int type, long maxSize) {
        if (mVTManager.getState() == VTManager.State.CLOSE || !VTCallUtils.isVTCallActive()) {
            log("startRecording() error --> called when VTManager is CLOSE or no active VT call exist.");
            return;
        }
        log("startRecording()... type / maxSize: " + type + " / " + maxSize);
        mVTManager.startRecording(type, maxSize);
    }

}
