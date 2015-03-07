package com.mediatek.incallui;

import com.android.incallui.Log;

public class VoiceCommandUIUtils {

    public interface Listener {
        void acceptIncomingCallByVoiceCommand();
        void rejectIncomingCallByVoiceCommand();
        void receiveVoiceCommandNotificationMessage(String message);
    }

    public interface PhoneDetectListener {
        void onPhoneRaised();
    }

    private static final String TAG = "VoiceCommandUIUtils";
    private static VoiceCommandUIUtils sVoiceCommandUIUtils;
    private Listener mListener;
    private PhoneDetectListener mDetectListener;

    public void setListener(Listener listener) {
        mListener = listener;
    }

    public void setPhoneDetectListener(PhoneDetectListener listener) {
        mDetectListener = listener;
    }

    public VoiceCommandUIUtils() {
        // TODO Auto-generated constructor stub
    }

    public static synchronized VoiceCommandUIUtils getInstance() {
        if (sVoiceCommandUIUtils == null) {
            sVoiceCommandUIUtils = new VoiceCommandUIUtils();
        }

        return sVoiceCommandUIUtils;
    }

    public void acceptIncomingCallByVoiceCommand() {
        log("acceptIncomingCallByVoiceCommand()..mListener: " + mListener);
        if (mListener != null) {
            mListener.acceptIncomingCallByVoiceCommand();
        }
    }
    public void rejectIncomingCallByVoiceCommand() {
        log("rejectIncomingCallByVoiceCommand()... mListener: " + mListener);
        if (mListener != null) {
            mListener.rejectIncomingCallByVoiceCommand();
        }
    }
    public void receiveVoiceCommandNotificationMessage(String message) {
        log("receiveVoiceCommandNotificationMessage()... message: " + message + ", mListener: " + mListener);
        if (mListener != null) {
            mListener.receiveVoiceCommandNotificationMessage(message);
        }
    }

    public void onPhoneRaised() {
        log("onPhoneRaised()... mDetectListener: " + mDetectListener);
        if (mDetectListener != null) {
            mDetectListener.onPhoneRaised();
        }
    }

    private void log(String msg) {
        Log.d(TAG, msg);
    }
}
