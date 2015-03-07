package com.mediatek.phone;

import android.os.Handler;
import android.os.Message;
import android.util.Log;

import com.android.internal.telephony.PhoneConstants;
import com.android.phone.PhoneGlobals;
import com.android.phone.PhoneUtils;
import com.mediatek.common.voicecommand.IVoicePhoneDetection;
import com.mediatek.common.MediatekClassFactory;
import com.mediatek.phone.VoiceCommandHandler.Listener;
import com.mediatek.voicecommand.app.VoicePhoneDetection;

public class PhoneRaiseDetector {

    public interface Listener {
        void onPhoneRaised();
    }

    private static final String TAG = "PhoneRaiseDetector";

    private static final int MOTION_TYPE_RAISE_TO_HEAD = 0;
    private static final int MESSAGE_PHONE_ARAISE_IDENTIFY = 0;
    private static final int IDENTIFY_SUCCESS = 1;

    private static PhoneRaiseDetector sPhoneRaiseDetector;

    private IVoicePhoneDetection mVoicePhoneDetector;
    private Listener mListener;
    private boolean mIsDetecting;

    private Handler mHandler = new Handler() {

        @Override
        public void handleMessage(Message msg) {
            log("handleMessage(), what = " + msg.what + ", arg1 = " + msg.arg1 + ", arg2 = " + msg.arg2);

            switch (msg.what) {

                case MESSAGE_PHONE_ARAISE_IDENTIFY:
                    if (IDENTIFY_SUCCESS == msg.arg1) {
                        onPhoneRaised();
                    }
                    break;

                default:
                    break;
            }
        }
    };

    public PhoneRaiseDetector() {
    }

    public static synchronized PhoneRaiseDetector getInstance() {
        if (sPhoneRaiseDetector == null) {
            sPhoneRaiseDetector = new PhoneRaiseDetector();
        }

        return sPhoneRaiseDetector;
    }

    public void setListener(Listener listener) {
        mListener = listener;
    }

    public void initDetector() {
        log("initDetector()... mVoicePhoneDetector: " + mVoicePhoneDetector);

        if (mVoicePhoneDetector == null) {
            mVoicePhoneDetector = MediatekClassFactory.createInstance(IVoicePhoneDetection.class, mHandler,
                    MOTION_TYPE_RAISE_TO_HEAD);
            log("mVoicePhoneDetector: " + mVoicePhoneDetector);
        }
        if (null == mVoicePhoneDetector) {
            log("PhoneRaiseDetector(), mVoicePhoneDetector is null");
        }
    }

    private void onPhoneRaised() {
        log("onPhoneRaised(), mIsDetecting = " + mIsDetecting + ", listener = " + mListener);
        log("callback thread id = " + Thread.currentThread().getId());
        if (mIsDetecting) {
            /// ALPS01264365  turn off speaker.
            PhoneUtils.onPhoneRaised();
        }
        stopPhoneDetect();
    }

    public void startPhoneDetect() {
        log("startPhoneDetect(), mVoicePhoneDetector = " + mVoicePhoneDetector + ", mIsDetecting = " + mIsDetecting);
        if (null == mVoicePhoneDetector) {
            return;
        }
        if (mIsDetecting) {
            return;
        }
        try {
            mVoicePhoneDetector.startPhoneDetection();
        } catch (IllegalStateException ex) {
            log("startPhoneDetect exception");
        }
        mIsDetecting = true;
    }

    public void stopPhoneDetect() {
        log("stopPhoneDetect(), mVoicePhoneDetector = " + mVoicePhoneDetector + ", mIsDetecting = " + mIsDetecting);
        if (null == mVoicePhoneDetector) {
            return;
        }
        if (!mIsDetecting) {
            return;
        }
        try {
            mVoicePhoneDetector.stopPhoneDetection();
        } catch (IllegalStateException ex) {
            log("stopPhoneDetect exception");
        }
        mIsDetecting = false;
    }

    public void release() {
        log("release");
        mListener = null;
        if (null != mVoicePhoneDetector) {
            mVoicePhoneDetector.releaseSelf();
            mVoicePhoneDetector = null;
        }
    }

    public static boolean isValidCondition() {
        if (PhoneConstants.State.IDLE == PhoneGlobals.getInstance().mCM.getState()) {
            return false;
        }
        if (!PhoneGlobals.getInstance().mCM.hasActiveRingingCall()
                && !PhoneGlobals.getInstance().mCM.hasActiveFgCall()) {
            return false;
        }
        if (PhoneGlobals.getInstance().mCM.hasActiveBgCall()) {
            return false;
        }
        return true;
    }

    private static void log(String msg) {
        Log.d(TAG, msg);
    }
}
