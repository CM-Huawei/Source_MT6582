
package com.mediatek.incallui.vt;

import android.os.SystemClock;
import android.view.SurfaceHolder;

import com.android.incallui.AudioModeProvider;
import com.android.incallui.AudioModeProvider.AudioModeListener;
import com.android.incallui.CallCommandClient;
import com.android.incallui.CallList;
import com.android.incallui.CallCardPresenter;
import com.android.incallui.InCallPresenter;
import com.android.incallui.InCallPresenter.InCallState;
import com.android.incallui.InCallPresenter.InCallStateListener;
import com.android.incallui.InCallPresenter.IncomingCallListener;
import com.android.incallui.Log;
import com.android.incallui.Presenter;
import com.android.incallui.Ui;
import com.android.services.telephony.common.Call;
import com.mediatek.incallui.InCallUtils;
import com.mediatek.incallui.vt.VTManagerLocal.State;
import com.mediatek.incallui.vt.VTManagerLocal.VTListener;

public class VTCallPresenter extends Presenter<VTCallPresenter.VTCallUi>
        implements InCallStateListener, IncomingCallListener, VTListener {

    private Call mPrimaryCall;
    private boolean mIsUIReady = false;

    public interface VTCallUi extends Ui {
        void surfaceCreated(SurfaceHolder holder);

        void surfaceChanged(SurfaceHolder holder, int format, int width, int height);

        void surfaceDestroyed(SurfaceHolder holder);

        void updateVTScreen();

        void onVTReceiveFirstFrame();

        void onVTReady();

        void answerVTCallPre();

        void dialVTCallSuccess();
    }

    public void onUiReady(VTCallUi ui) {
        super.onUiReady(ui);

        // Register for call state changes last
        InCallPresenter.getInstance().addListener(this);
        InCallPresenter.getInstance().addIncomingCallListener(this);

        // For VT
        VTManagerLocal.getInstance().addVTListener(this);
        // TODO: here may need reset VTIncallScreenFlags' UI related variables except VTSettingUtils related variable
        mIsUIReady = true;
        onVTReady();
    }

    @Override
    public void onUiUnready(VTCallUi ui) {
        super.onUiUnready(ui);

        // stop getting call state changes
        InCallPresenter.getInstance().removeListener(this);
        InCallPresenter.getInstance().removeIncomingCallListener(this);

        // For VT
        VTManagerLocal.getInstance().removeVTListener(this);
        mIsUIReady = false;
    }

    public void init(Call call) {
        mPrimaryCall = call;
    }

    public void onStateChange(InCallState state, CallList callList) {
    }

    @Override
    public void onIncomingCall(InCallState state, Call call) {
        mPrimaryCall = call;
    }

    public void endCallClicked() {
        if (mPrimaryCall == null) {
            Log.e(this, "endCallClicked but mCall is null");
            return;
        }

        CallCommandClient.getInstance().disconnectCall(mPrimaryCall.getCallId());
    }

    void startVtRecording(int type, long maxSize) {
        CallCommandClient.getInstance().startVtRecording(type, maxSize);
    }

    void startVoiceRecording() {
        CallCommandClient.getInstance().startVoiceRecording();
    }

    void stopRecording() {
        CallCommandClient.getInstance().stopRecording();
    }

    public boolean isIncomingCall() {
        boolean isMT = false;
        if (mPrimaryCall != null) {
            isMT = InCallUtils.isIncomingCall(mPrimaryCall);
        } else {
            Log.i(this, "mPrimaryCall is null");
        }
        return isMT;
    }

    @Override
    public void onVTStateChanged(int msgVT) {

        Log.d(this, "onVTStateChanged()... msgVT: " + msgVT);
        // Here we only handle UI related message, other logic will be done in VTManagerLocal.java.
        final VTCallUi ui = getUi();
        if (ui == null) {
            Log.d(this, "UI is not ready when onVTStateChanged(), just return.");
            return;
        }

        switch (msgVT) {
        case VTManagerLocal.VT_MSG_READY:
            onVTReady();
            break;

        /// M: for ALPS01425992 @{
        // InCallUI start count to display call time of VT.
        case VTManagerLocal.VT_MSG_START_COUNTER:
            startVTManagerCounter(mPrimaryCall);
            break;
        /// @}

        case VTManagerLocal.VT_MSG_RECEIVE_FIRSTFRAME:
            getUi().onVTReceiveFirstFrame();
            break;

        default:
            break;
        }
    }

    @Override
    public void answerVTCallPre() {
        if (getUi() != null) {
            Log.d(this, "answerVTCallPre()...");
            getUi().answerVTCallPre();
        }
    }

    @Override
    public void dialVTCallSuccess() {
        if (getUi() != null) {
            Log.d(this, "dialVTCallSuccess()...");
            getUi().dialVTCallSuccess();
        }
    }

    @Override
    public void updateVTCallFragment() {
        if (getUi() != null) {
            Log.d(this, "updateVTCallFragment()...");
            getUi().updateVTScreen();
        }
    }

    @Override
    public void updateVTCallButton() {
        //
    }

    /*
     * The function is to show mVTMTAskDialog if needed.
     * Here we must make sure the UI is ready and VTManager is Ready.
     * If UI is created later than VTManager.READY message,
     * we also have a chance to show mVTMTAskDialog in onUiReady().
     */
    private void onVTReady() {
        Log.d(this, "onVTReady()...mIsUIReady / VTManagerLocal.getState(): " + mIsUIReady + " / "
                + VTManagerLocal.getInstance().getState());
        if (mIsUIReady && VTManagerLocal.getInstance().getState() == State.READY) {
            final VTCallUi ui = getUi();
            if (ui != null) {
                Log.d(this, "Call ui.onVTReady()...");
                ui.onVTReady();
            }
        }
    }

    /// M: for ALPS01425992 @{
    // InCallUI start count to display call time of VT.
    private void startVTManagerCounter(Call call) {
        Log.d(this, "startVTManagerCounter()...");
        CallCardPresenter.startVTCallTimer();
        if (VTInCallScreenFlags.getInstance().mVTConnectionStarttime.mStarttime < 0) {
            if (null != call) {
                VTInCallScreenFlags.getInstance().mVTConnectionStarttime.mStarttime
                        = SystemClock.elapsedRealtime();
            }
        }
    }
    /// @}
}
