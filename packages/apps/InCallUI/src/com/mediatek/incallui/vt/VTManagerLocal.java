package com.mediatek.incallui.vt;

import com.android.incallui.Log;
import com.android.services.telephony.common.VTManagerParams;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;

import java.util.ArrayList;
import java.util.List;

public class VTManagerLocal {

    // messages which will be used in UI, sync with VTManager's message.
    public static final int VT_MSG_CLOSE = 0x0001;
    public static final int VT_MSG_OPEN = 0x0002;
    public static final int VT_MSG_READY = 0x0003;
    public static final int VT_MSG_CONNECTED = 0x0004;
    public static final int VT_MSG_DISCONNECTED = 0x0005;
    public static final int VT_MSG_EM_INDICATION = 0x0006;
    public static final int VT_MSG_START_COUNTER = 0x0007;
    public static final int VT_MSG_RECEIVE_FIRSTFRAME = 0x0008;
    public static final int VT_MSG_PEER_CAMERA_OPEN = 0x0009;
    public static final int VT_MSG_PEER_CAMERA_CLOSE = 0x0010;
    public static final int VT_MSG_CAM_BEGIN = 0x1000;
    public static final int VT_ERROR_CALL_DISCONNECT = 0x8001;
    public static final int VT_ERROR_START_VTS_FAIL = 0x8002;
    public static final int VT_ERROR_CAMERA = 0x8003;
    public static final int VT_ERROR_MEDIA_SERVER_DIED = 0x8004;
    public static final int VT_ERROR_MEDIA_RECORDER_EVENT_INFO = 0x8005;
    public static final int VT_ERROR_MEDIA_RECORDER_EVENT_ERROR = 0x8006;
    public static final int VT_ERROR_MEDIA_RECORDER_COMPLETE = 0x8007;
    public static final int VT_NORMAL_END_SESSION_COMMAND = 0x8101;

    // settings which will be used in UI, sync with VTManager's defines.
    public static final int VT_VQ_NORMAL = 1;
    public static final int VT_VQ_SHARP = 0;

    // messages defined within UI and Telephony, not in VTManager.
    public static final int VT_RESULT_SWITCHCAMERA_OK = 128;
    public static final int VT_RESULT_SWITCHCAMERA_FAIL = 129;
    public static final int VT_RESULT_PEER_SNAPSHOT_OK = 126;
    public static final int VT_RESULT_PEER_SNAPSHOT_FAIL = 127;

    public enum State {
        CLOSE, OPEN, READY, CONNECTED
    }

    private static VTManagerLocal sVTManagerWrapper = new VTManagerLocal();
    private State mState = State.CLOSE;
    private List<String> mSupportedColorEffects = new ArrayList<String>();
    private String mColorEffect;
    private int mCameraSensorCount;
    private int mVideoQuality;
    private boolean mCanDecBrightness;
    private boolean mCanIncBrightness;
    private boolean mCanDecZoom;
    private boolean mCanIncZoom;
    private boolean mCanDecContrast;
    private boolean mCanIncContrast;
    private boolean mIsSupportNightMode;
    private boolean mIsNightModeOn;

    private VTManagerLocal() {
        mState = State.CLOSE;
    }

    public static VTManagerLocal getInstance() {
        return sVTManagerWrapper;
    }

    public void onVTStateChanged(int msgVT) {

        Log.d(this, "onVTStateChanged()... msgVT: " + msgVT);
        switch (msgVT) {
        case VT_MSG_CLOSE:
            setState(msgVT);
            VTInCallScreenFlags.getInstance().reset();
            break;

        case VT_MSG_OPEN:
            setState(msgVT);
            break;

        case VT_MSG_READY:
            setState(msgVT);
            break;

        case VT_MSG_CONNECTED:
            setState(msgVT);
            break;

        case VT_MSG_DISCONNECTED:
            VTInCallScreenFlags.getInstance().mVTHasReceiveFirstFrame = false;
            break;

        case VT_MSG_EM_INDICATION:
            break;

        case VT_MSG_START_COUNTER:
            break;

        case VT_MSG_RECEIVE_FIRSTFRAME:
            VTInCallScreenFlags.getInstance().mVTHasReceiveFirstFrame = true;
            updateVTCallButton();
            break;

        case VT_MSG_PEER_CAMERA_OPEN:
            break;

        case VT_MSG_PEER_CAMERA_CLOSE:
            break;

        case VT_MSG_CAM_BEGIN:
            break;

        case VT_ERROR_CALL_DISCONNECT:
            break;

        case VT_ERROR_START_VTS_FAIL:
            break;

        case VT_ERROR_CAMERA:
            break;

        case VT_ERROR_MEDIA_SERVER_DIED:
            break;

        case VT_ERROR_MEDIA_RECORDER_EVENT_INFO:
            break;

        case VT_ERROR_MEDIA_RECORDER_EVENT_ERROR:
            break;

        case VT_ERROR_MEDIA_RECORDER_COMPLETE:
            break;

        case VT_NORMAL_END_SESSION_COMMAND:
            break;

        case VT_RESULT_SWITCHCAMERA_OK:
        case VT_RESULT_SWITCHCAMERA_FAIL:
            VTInCallScreenFlags.getInstance().mVTInSwitchCamera = false;
            break;

        case VT_RESULT_PEER_SNAPSHOT_OK:
        case VT_RESULT_PEER_SNAPSHOT_FAIL:
            VTInCallScreenFlags.getInstance().mVTInSnapshot = false;
            break;

        default:
            break;
        }
        notifyVTStateChange(msgVT);
    }

    public void setState(int msgVT) {
        Log.d(this, "msgVT: " + msgVT);
        switch (msgVT) {
        case VT_MSG_OPEN:
            mState = State.OPEN;
            break;
        case VT_MSG_CLOSE:
            mState = State.CLOSE;
            break;
        case VT_MSG_READY:
            mState = State.READY;
            break;
        case VT_MSG_CONNECTED:
            mState = State.CONNECTED;
            break;
        default:
            Log.d(this, "msgVT is not a State related message.");
            break;
        }
        Log.d(this, "setState to be " + mState);
    }

    public State getState() {
        return mState;
    }

    // the only place to set those parameters; we must make sure this function will be called before below "get" functions.
    public void getVTManagerParams(VTManagerParams params) {
        Log.d(this, "getVTManagerParams: " + params.toString());
        mColorEffect = params.mColorEffect;
        mCameraSensorCount = params.mCameraSensorCount;
        mVideoQuality = params.mVideoQuality;
        mCanDecBrightness = params.mCanDecBrightness;
        mCanIncBrightness = params.mCanIncBrightness;
        mCanDecZoom = params.mCanDecZoom;
        mCanIncZoom = params.mCanIncZoom;
        mCanDecContrast = params.mCanDecContrast;
        mCanIncContrast = params.mCanIncContrast;
        mIsSupportNightMode = params.mIsSupportNightMode;
        mIsNightModeOn = params.mIsNightModeOn;
        mSupportedColorEffects = params.mSupportedColorEffects;
        updateVTCallFragment();
    }

    public int getCameraSensorCount() {
        return mCameraSensorCount;
    }

    public int getVideoQuality() {
        return mVideoQuality;
    }

    public boolean canDecBrightness() {
        return mCanDecBrightness;
    }

    public boolean canIncBrightness() {
        return mCanIncBrightness;
    }

    public boolean canDecZoom() {
        return mCanDecZoom;
    }

    public boolean canIncZoom() {
        return mCanIncZoom;
    }

    public boolean canDecContrast() {
        return mCanDecContrast;
    }

    public boolean canIncContrast() {
        return mCanIncContrast;
    }

    public boolean isSupportNightMode() {
        return mIsSupportNightMode;
    }

    public boolean getNightMode() {
        return mIsNightModeOn;
    }

    public String getColorEffect() {
        return mColorEffect;
    }

    public List<String> getSupportedColorEffects() {
        return mSupportedColorEffects;
    }

    private final ArrayList<VTListener> mVTListener = Lists.newArrayList();

    public interface VTListener{
        public void onVTStateChanged(int msgVT);
        public void answerVTCallPre();
        public void dialVTCallSuccess();
        public void updateVTCallFragment();
        public void updateVTCallButton();
    }

    public void addVTListener(VTListener listener) {
        Preconditions.checkNotNull(listener);
        mVTListener.add(listener);
    }

    public void removeVTListener(VTListener listener) {
        Preconditions.checkNotNull(listener);
        mVTListener.remove(listener);
    }

    public void notifyVTStateChange(int msgVT) {
        // here to pass the message msgVT to VTCallFragment
        Log.i(this, "notifyVTStateChange: state = " + msgVT);
        for (VTListener listener : mVTListener) {
            listener.onVTStateChanged(msgVT);
        }
    }

    public void answerVTCallPre() {
        Log.i(this, "answerVTCallPre()...");
        for (VTListener listener : mVTListener) {
            listener.answerVTCallPre();
        }
    }

    public void dialVTCallSuccess() {
        Log.i(this, "dialVTCallSuccess()...");
        for (VTListener listener : mVTListener) {
            listener.dialVTCallSuccess();
        }
    }

    public void updateVTCallFragment() {
        Log.i(this, "updateVTCallFragment()...");
        for (VTListener listener : mVTListener) {
            listener.updateVTCallFragment();
        }
    }

    public void updateVTCallButton() {
        Log.i(this, "updateVTCallButton()...");
        for (VTListener listener : mVTListener) {
            listener.updateVTCallButton();
        }
    }

}
