/*
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.phone;

import android.Manifest;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.graphics.Bitmap;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.PowerManager;
import android.os.RemoteException;
import android.os.SystemClock;
import android.os.SystemProperties;
import android.text.TextUtils;
import android.util.Log;

import com.android.internal.telephony.Connection;
import com.android.internal.telephony.Connection.PostDialState;
import com.android.phone.AudioRouter.AudioModeListener;
import com.android.phone.NotificationMgr.StatusBarHelper;
import com.android.services.telephony.common.AudioMode;
import com.android.services.telephony.common.Call;
import com.android.services.telephony.common.DualtalkCallInfo;
import com.android.services.telephony.common.ICallHandlerService;
import com.android.services.telephony.common.VTSettingParams;
import com.android.services.telephony.common.VTManagerParams;
import com.google.common.collect.Lists;

import com.mediatek.phone.PhoneFeatureConstants.FeatureOption;
import com.mediatek.phone.recording.PhoneRecorderHandler;
import com.mediatek.phone.VoiceCommandHandler;
import com.mediatek.settings.VTSettingUtils;
import com.mediatek.vt.VTManager;
import com.mediatek.phone.vt.VTManagerWrapper;
import com.mediatek.phone.PhoneRaiseDetector;
import java.util.List;

/**
 * This class is responsible for passing through call state changes to the CallHandlerService.
 */
public class CallHandlerServiceProxy extends Handler
 implements CallModeler.Listener, AudioModeListener,
        VTSettingUtils.Listener, PhoneRecorderHandler.Listener,
        VTManagerWrapper.Listener, VoiceCommandHandler.Listener,
        PhoneRaiseDetector.Listener {

    private static final String TAG = CallHandlerServiceProxy.class.getSimpleName();
    private static final boolean DBG = (PhoneGlobals.DBG_LEVEL >= 1) && (SystemProperties.getInt(
            "ro.debuggable", 0) == 1);

    public static final int RETRY_DELAY_MILLIS = 2000;
    public static final int RETRY_DELAY_LONG_MILLIS = 30 * 1000; // 30 seconds
    private static final int BIND_RETRY_MSG = 1;
    private static final int MAX_SHORT_DELAY_RETRY_COUNT = 5;

    private AudioRouter mAudioRouter;
    private CallCommandService mCallCommandService;
    private CallModeler mCallModeler;
    private Context mContext;
    private boolean mFullUpdateOnConnect;

    private ICallHandlerService mCallHandlerServiceGuarded;  // Guarded by mServiceAndQueueLock
    // Single queue to guarantee ordering
    private List<QueueParams> mQueue;                        // Guarded by mServiceAndQueueLock

    private final Object mServiceAndQueueLock = new Object();
    private int mBindRetryCount = 0;

    @Override
    public void handleMessage(Message msg) {
        super.handleMessage(msg);

        switch (msg.what) {
            case BIND_RETRY_MSG:
                handleConnectRetry();
                break;
        }
    }

    public CallHandlerServiceProxy(Context context, CallModeler callModeler,
            CallCommandService callCommandService, AudioRouter audioRouter) {
        if (DBG) {
            Log.d(TAG, "init CallHandlerServiceProxy");
        }
        mContext = context;
        mCallCommandService = callCommandService;
        mCallModeler = callModeler;
        mAudioRouter = audioRouter;

        mAudioRouter.addAudioModeListener(this);
        mCallModeler.addListener(this);
        VTSettingUtils.getInstance().setListener(this);
        VTManagerWrapper.getInstance().setListener(this);
        VoiceCommandHandler.getInstance().setListener(this);
        PhoneRaiseDetector.getInstance().setListener(this);
    }

    @Override
    public void onDisconnect(Call call) {
        // Wake up in case the screen was off.
        wakeUpScreen();
        synchronized (mServiceAndQueueLock) {
            if (mCallHandlerServiceGuarded == null) {
                if (DBG) {
                    Log.d(TAG, "CallHandlerService not connected.  Enqueue disconnect");
                }
                enqueueDisconnect(call);
                setupServiceConnection();
                return;
            }
        }
        processDisconnect(call);
    }

    private void wakeUpScreen() {
        Log.d(TAG, "wakeUpScreen()");
        final PowerManager pm = (PowerManager) mContext.getSystemService(Context.POWER_SERVICE);
        pm.wakeUp(SystemClock.uptimeMillis());
    }

    private void processDisconnect(Call call) {
        try {
            if (DBG) {
                Log.d(TAG, "onDisconnect: " + call);
            }
            synchronized (mServiceAndQueueLock) {
                if (mCallHandlerServiceGuarded != null) {
                    mCallHandlerServiceGuarded.onDisconnect(call);
                }
            }
            if (!mCallModeler.hasLiveCall()) {
                unbind();
            }
        } catch (Exception e) {
            Log.e(TAG, "Remote exception handling onDisconnect ", e);
        }
    }

    @Override
    public void onIncoming(Call call) {
        // for new incoming calls, reset the retry count.
        resetConnectRetryCount();

        synchronized (mServiceAndQueueLock) {
            if (mCallHandlerServiceGuarded == null) {
                if (DBG) {
                    Log.d(TAG, "CallHandlerService not connected.  Enqueue incoming.");
                }
                enqueueIncoming(call);
                setupServiceConnection();
                return;
            }
        }
        processIncoming(call);
    }

    private void processIncoming(Call call) {
        if (DBG) {
            Log.d(TAG, "onIncoming: " + call);
        }
        try {
            // TODO: check RespondViaSmsManager.allowRespondViaSmsForCall()
            // must refactor call method to accept proper call object.
            synchronized (mServiceAndQueueLock) {
                if (mCallHandlerServiceGuarded != null) {
                    mCallHandlerServiceGuarded.onIncoming(call,
                            RejectWithTextMessageManager.loadCannedResponses());
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Remote exception handling onUpdate", e);
        }
    }

    @Override
    public void onUpdate(List<Call> calls) {
        synchronized (mServiceAndQueueLock) {
            if (mCallHandlerServiceGuarded == null) {
                if (DBG) {
                    Log.d(TAG, "CallHandlerService not connected.  Enqueue update.");
                }
                enqueueUpdate(calls);
                setupServiceConnection();
                return;
            }
        }
        processUpdate(calls);
    }

    private void processUpdate(List<Call> calls) {
        if (DBG) {
            Log.d(TAG, "onUpdate: " + calls.toString());
        }
        try {
            synchronized (mServiceAndQueueLock) {
                if (mCallHandlerServiceGuarded != null) {
                    mCallHandlerServiceGuarded.onUpdate(calls);
                }
            }
            if (!mCallModeler.hasLiveCall()) {
                // TODO: unbinding happens in both onUpdate and onDisconnect because the ordering
                // is not deterministic.  Unbinding in both ensures that the service is unbound.
                // But it also makes this in-efficient because we are unbinding twice, which leads
                // to the CallHandlerService performing onCreate() and onDestroy() twice for each
                // disconnect.
                unbind();
            }
        } catch (Exception e) {
            Log.e(TAG, "Remote exception handling onUpdate", e);
        }
    }


    @Override
    public void onPostDialAction(Connection.PostDialState state, int callId, String remainingChars,
            char currentChar) {
        if (state != PostDialState.WAIT) return;
        try {
            synchronized (mServiceAndQueueLock) {
                if (mCallHandlerServiceGuarded == null) {
                    if (DBG) {
                        Log.d(TAG, "CallHandlerService not conneccted. Skipping "
                                + "onPostDialWait().");
                    }
                    return;
                }
            }

            mCallHandlerServiceGuarded.onPostDialWait(callId, remainingChars);
        } catch (Exception e) {
            Log.e(TAG, "Remote exception handling onUpdate", e);
        }
    }

    @Override
    public void onAudioModeChange(int newMode, boolean muted) {
        try {
            synchronized (mServiceAndQueueLock) {
                if (mCallHandlerServiceGuarded == null) {
                    if (DBG) {
                        Log.d(TAG, "CallHandlerService not conneccted. Skipping "
                                + "onAudioModeChange().");
                    }
                    return;
                }
            }

            // Just do a simple log for now.
            Log.i(TAG, "Updating with new audio mode: " + AudioMode.toString(newMode) +
                    " with mute " + muted);

            mCallHandlerServiceGuarded.onAudioModeChange(newMode, muted);
        } catch (Exception e) {
            Log.e(TAG, "Remote exception handling onAudioModeChange", e);
        }
    }

    @Override
    public void onSupportedAudioModeChange(int modeMask) {
        try {
            synchronized (mServiceAndQueueLock) {
                if (mCallHandlerServiceGuarded == null) {
                    if (DBG) {
                        Log.d(TAG, "CallHandlerService not conneccted. Skipping"
                                + "onSupportedAudioModeChange().");
                    }
                    return;
                }
            }

            if (DBG) {
                Log.d(TAG, "onSupportAudioModeChange: " + AudioMode.toString(modeMask));
            }

            mCallHandlerServiceGuarded.onSupportedAudioModeChange(modeMask);
        } catch (Exception e) {
            Log.e(TAG, "Remote exception handling onAudioModeChange", e);
        }

    }

    private ServiceConnection mConnection = null;

    private class InCallServiceConnection implements ServiceConnection {
        @Override public void onServiceConnected (ComponentName className, IBinder service){
            if (DBG) {
                Log.d(TAG, "Service Connected");
            }

            /// For ALPS01413389. @{
            // ALPS01413389, after modification, may bind several times.  onServiceConnected() maybe call
            // several times, we only need initialize  first time.
            if (mCallHandlerServiceGuarded != null) {
                Log.d(TAG, "Service Connected, service = " + service);
                Log.d(TAG, "Service Connected, mCallHandlerServiceGuarded = " + mCallHandlerServiceGuarded);
                return;
            }
            /// @}
            
            onCallHandlerServiceConnected(ICallHandlerService.Stub.asInterface(service));
            resetConnectRetryCount();
            /// M: Add for recording. @{
            PhoneRecorderHandler.getInstance().setListener(CallHandlerServiceProxy.this);
            /// @}
        }

        @Override public void onServiceDisconnected (ComponentName className){
            Log.i(TAG, "Disconnected from UI service.");
            synchronized (mServiceAndQueueLock) {
                // Technically, unbindService is un-necessary since the framework will schedule and
                // restart the crashed service.  But there is a exponential backoff for the restart.
                // Unbind explicitly and setup again to avoid the backoff since it's important to
                // always have an in call ui.
                unbind();

                reconnectOnRemainingCalls();
                /// M: Add for recording. @{
                PhoneRecorderHandler.getInstance().clearListener(CallHandlerServiceProxy.this);
                /// @}
            }
        }
    }

    public void bringToForeground(boolean showDialpad) {
        // only support this call if the service is already connected.
        synchronized (mServiceAndQueueLock) {
            if (mCallHandlerServiceGuarded != null && mCallModeler.hasLiveCall()) {
                try {
                    if (DBG) Log.d(TAG, "bringToForeground: " + showDialpad);
                    mCallHandlerServiceGuarded.bringToForeground(showDialpad);
                } catch (RemoteException e) {
                    Log.e(TAG, "Exception handling bringToForeground", e);
                }
            }
        }
    }

    private static Intent getInCallServiceIntent(Context context) {
        final Intent serviceIntent = new Intent(ICallHandlerService.class.getName());
        final ComponentName component = new ComponentName(context.getResources().getString(
                R.string.ui_default_package), context.getResources().getString(
                R.string.incall_default_class));
        serviceIntent.setComponent(component);
        return serviceIntent;
    }

    /**
     * Sets up the connection with ICallHandlerService
     */
    private void setupServiceConnection() {
        if (!PhoneGlobals.sVoiceCapable) {
            return;
        }

        final Intent serviceIntent = getInCallServiceIntent(mContext);
        if (DBG) {
            Log.d(TAG, "binding to service " + serviceIntent);
        }

        synchronized (mServiceAndQueueLock) {

            /// For ALPS01413389. @{
            // ALPS01413389, bind service failed, but mConnection is not null. 
            // Before bind service, incallui just be kill due to LMT, bindService return true.
            // So need check mCallHandlerServiceGuarded as well, or else will never bind again.
            if (mConnection == null || mCallHandlerServiceGuarded == null) {

                if (mConnection == null) {
                    mConnection = new InCallServiceConnection();
                }
            /// @}
            
                boolean failedConnection = false;
                final PackageManager packageManger = mContext.getPackageManager();
                final List<ResolveInfo> services = packageManger.queryIntentServices(serviceIntent,
                        0);

                ServiceInfo serviceInfo = null;

                for (int i = 0; i < services.size(); i++) {
                    final ResolveInfo info = services.get(i);
                    if (info.serviceInfo != null) {
                        if (Manifest.permission.BIND_CALL_SERVICE.equals(
                                info.serviceInfo.permission)) {
                            serviceInfo = info.serviceInfo;
                            break;
                        }
                    }
                }

                if (serviceInfo == null) {
                    // Service not found, retry again after some delay
                    // This can happen if the service is being installed by the package manager.
                    // Between deletes and installs, bindService could get a silent service not
                    // found error.
                    Log.w(TAG, "Default call handler service not found.");
                    failedConnection = true;
                } else {

                    serviceIntent.setComponent(new ComponentName(serviceInfo.packageName,
                            serviceInfo.name));
                    if (DBG) {
                        Log.d(TAG, "binding to service " + serviceIntent);
                    }
                    if (!mContext.bindService(serviceIntent, mConnection,
                            Context.BIND_AUTO_CREATE)) {
                        // This happens when the in-call package is in the middle of being installed
                        Log.w(TAG, "Could not bind to default call handler service: " +
                                serviceIntent.getComponent());
                        failedConnection = true;
                    }
                }

                if (failedConnection) {
                    mConnection = null;
                    enqueueConnectRetry();
                }
            } else {
                Log.d(TAG, "Service connection to in call service already started.");
            }
        }
    }

    private void resetConnectRetryCount() {
        mBindRetryCount = 0;
    }

    private void incrementRetryCount() {
        // Reset to the short delay retry count to avoid overflow
        if (Integer.MAX_VALUE == mBindRetryCount) {
            mBindRetryCount = MAX_SHORT_DELAY_RETRY_COUNT;
        }

        mBindRetryCount++;
    }

    private void handleConnectRetry() {
        // Remove any pending messages since we're already performing the action.
        // If the call to setupServiceConnection() fails, it will queue up another retry.
        removeMessages(BIND_RETRY_MSG);

        // Something else triggered the connection, cancel.
        if (mConnection != null) {
            Log.i(TAG, "Retry: already connected.");
            return;
        }

        if (mCallModeler.hasLiveCall()) {
            // Update the count when we are actually trying the retry instead of when the
            // retry is queued up.
            incrementRetryCount();

            Log.i(TAG, "Retrying connection: " + mBindRetryCount);
            setupServiceConnection();
        } else {
            Log.i(TAG, "Canceling connection retry since there are no calls.");
            // We are not currently connected and there is no call so lets not bother
            // with the retry. Also, empty the queue of pending messages to send
            // to the UI.
            synchronized (mServiceAndQueueLock) {
                if (mQueue != null) {
                    mQueue.clear();
                }
            }

            // Since we have no calls, reset retry count.
            resetConnectRetryCount();
        }
    }

    /**
     * Called after the connection failed and a retry is needed.
     * Queues up a retry to happen with a delay.
     */
    private void enqueueConnectRetry() {
        final boolean isLongDelay = (mBindRetryCount > MAX_SHORT_DELAY_RETRY_COUNT);
        final int delay = isLongDelay ? RETRY_DELAY_LONG_MILLIS : RETRY_DELAY_MILLIS;

        Log.w(TAG, "InCallUI Connection failed. Enqueuing delayed retry for " + delay + " ms." +
                " retries(" + mBindRetryCount + ")");

        sendEmptyMessageDelayed(BIND_RETRY_MSG, delay);
    }

    private void unbind() {
        synchronized (mServiceAndQueueLock) {
            // On unbind, reenable the notification shade and navigation bar just in case the
            // in-call UI crashed on an incoming call.
            final StatusBarHelper statusBarHelper = PhoneGlobals.getInstance().notificationMgr.
                    statusBarHelper;
            statusBarHelper.enableSystemBarNavigation(true);
            statusBarHelper.enableExpandedView(true);
            if (mCallHandlerServiceGuarded != null) {
                Log.d(TAG, "Unbinding service.");
                mCallHandlerServiceGuarded = null;
                mContext.unbindService(mConnection);
            }
            mConnection = null;
        }
    }

    /**
     * Called when the in-call UI service is connected.  Send command interface to in-call.
     */
    private void onCallHandlerServiceConnected(ICallHandlerService callHandlerService) {

        synchronized (mServiceAndQueueLock) {
            mCallHandlerServiceGuarded = callHandlerService;

            // Before we send any updates, we need to set up the initial service calls.
            makeInitialServiceCalls();

            processQueue();

            if (mFullUpdateOnConnect) {
                mFullUpdateOnConnect = false;
                onUpdate(mCallModeler.getFullList());
            }
        }
    }

    /**
     * Checks to see if there are any live calls left, and if so, try reconnecting the UI.
     */
    private void reconnectOnRemainingCalls() {
        if (mCallModeler.hasLiveCall()) {
            mFullUpdateOnConnect = true;
            setupServiceConnection();
        }
    }

    /**
     * Makes initial service calls to set up callcommandservice and audio modes.
     */
    private void makeInitialServiceCalls() {
        try {
            mCallHandlerServiceGuarded.startCallService(mCallCommandService);

            onSupportedAudioModeChange(mAudioRouter.getSupportedAudioModes());
            onAudioModeChange(mAudioRouter.getAudioMode(), mAudioRouter.getMute());
        } catch (RemoteException e) {
            Log.e(TAG, "Remote exception calling CallHandlerService::setCallCommandService", e);
        }
    }

    private List<QueueParams> getQueue() {
        if (mQueue == null) {
            mQueue = Lists.newArrayList();
        }
        return mQueue;
    }

    private void enqueueDisconnect(Call call) {
        getQueue().add(new QueueParams(QueueParams.METHOD_DISCONNECT, new Call(call)));
    }

    private void enqueueIncoming(Call call) {
        getQueue().add(new QueueParams(QueueParams.METHOD_INCOMING, new Call(call)));
    }

    private void enqueueUpdate(List<Call> calls) {
        final List<Call> copy = Lists.newArrayList();
        for (Call call : calls) {
            copy.add(new Call(call));
        }
        getQueue().add(new QueueParams(QueueParams.METHOD_UPDATE, copy));
    }

    private void processQueue() {
        synchronized (mServiceAndQueueLock) {
            if (mQueue != null) {
                for (QueueParams params : mQueue) {
                    switch (params.mMethod) {
                        case QueueParams.METHOD_INCOMING:
                            processIncoming((Call) params.mArg);
                            break;
                        case QueueParams.METHOD_UPDATE:
                            processUpdate((List<Call>) params.mArg);
                            break;
                        case QueueParams.METHOD_DISCONNECT:
                            processDisconnect((Call) params.mArg);
                            break;
                        /// M: For VT @{
                        case QueueParams.METHOD_VT_DIAL_SUCCESS:
                            processDialVTCallSuccess();
                            break;
                        case QueueParams.METHOD_VT_SETTING_PARAMS:
                            processPushVTSettingParams((VTSettingParams)params.mArg, (Bitmap)params.mArgExtra);
                            break;
                        case QueueParams.METHOD_VT_STATE_CHANGE:
                            processVTStateChanged((Integer)params.mArg);
                            break;
                        /// @}
                        /// M: for Record @{
                        case QueueParams.METHOD_UPDATE_RECORD_STATE:
                            processUpdateRecordState((Integer) params.mArg, (Integer) params.mArgExtra);
                            break;
                        /// @}
                        default:
                            throw new IllegalArgumentException("Method type " + params.mMethod +
                                    " not recognized.");
                    }
                }
                mQueue.clear();
                mQueue = null;
            }
        }
    }

    /**
     * Holds method parameters.
     */
    private static class QueueParams {
        private static final int METHOD_INCOMING = 1;
        private static final int METHOD_UPDATE = 2;
        private static final int METHOD_DISCONNECT = 3;

        private final int mMethod;
        private final Object mArg;

        private QueueParams(int method, Object arg) {
            mMethod = method;
            this.mArg = arg;
        }

        /// M: For VT @{
        private static final int METHOD_VT_DIAL_SUCCESS = 4;
        private static final int METHOD_VT_SETTING_PARAMS = 5;
        private static final int METHOD_VT_STATE_CHANGE = 6;
        private Object mArgExtra = null;
        private QueueParams (int method, Object arg, Object argExtra) {
            mMethod = method;
            this.mArg = arg;
            this.mArgExtra = argExtra;
        }
        /// @}

        /// M: for Record @{
        private static final int METHOD_UPDATE_RECORD_STATE = 7;
        /// @}
    }

//-------------------MTK----------------------------

    /*
     * push VT Setting related parameters to UI, because it will be called only
     * before MO or MT, so onDisconnect() will always be called after it, so the
     * service can be unBind(). So it's safe to call setupServiceConnection() here.
     * 
     * @see
     * com.mediatek.settings.VTSettingUtils.Listener#pushVTSettingParams(com
     * .android.services.telephony.common.VTSettingParams,
     * android.graphics.Bitmap)
     */
    public void pushVTSettingParams(VTSettingParams params, Bitmap bitmap) {
        synchronized (mServiceAndQueueLock) {
            if (mCallHandlerServiceGuarded == null) {
                if (DBG) {
                    Log.d(TAG, "CallHandlerService not connected.  Enqueue VTSetting.");
                }
                enqueueVTSettingParams(params, bitmap);
                setupServiceConnection();
                return;
            }
        }
        processPushVTSettingParams(params, bitmap);
    }

    private void processPushVTSettingParams(VTSettingParams params, Bitmap bitmap) {
        if (DBG) {
            Log.d(TAG, "processPushVTSettingParams: " + params.toString());
        }
        try {
            synchronized (mServiceAndQueueLock) {
                if (mCallHandlerServiceGuarded != null) {
                    mCallHandlerServiceGuarded.pushVTSettingParams(params, bitmap);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Remote exception handling pushVTSettingParams", e);
        }
    }

    private void enqueueVTSettingParams(VTSettingParams params, Bitmap bitmap) {
        final VTSettingParams paramsCopy = new VTSettingParams(params);
        getQueue().add(new QueueParams(QueueParams.METHOD_VT_SETTING_PARAMS, paramsCopy, bitmap));
    }

    /**
     * The information is must needed in InCallUI, so need setupConnection() if service is not up.
     */
    public void dialVTCallSuccess() {
        synchronized (mServiceAndQueueLock) {
            if (mCallHandlerServiceGuarded == null) {
                if (DBG) {
                    Log.d(TAG, "CallHandlerService not connected.  Enqueue dialVTCallSuccess.");
                }
                enqueueDialVTCallSuccess();
                setupServiceConnection();
                return;
            }
        }
        processDialVTCallSuccess();
    }

    private void enqueueDialVTCallSuccess() {
        getQueue().add(new QueueParams(QueueParams.METHOD_VT_DIAL_SUCCESS, null));
    }

    private void processDialVTCallSuccess() {
        if (DBG) {
            Log.d(TAG, "processDialVTCallSuccess()... ");
        }
        try {
            synchronized (mServiceAndQueueLock) {
                if (mCallHandlerServiceGuarded != null) {
                    mCallHandlerServiceGuarded.dialVTCallSuccess();
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Remote exception handling processDialVTCallSuccess", e);
        }
    }

    /**
     * This function is always called after Incoming call screen is shown, so we can sure the service is up here.
     */
    public void answerVTCallPre() {
        try {
            synchronized (mServiceAndQueueLock) {
                if (mCallHandlerServiceGuarded == null) {
                    if (DBG) {
                        Log.d(TAG, "CallHandlerService not conneccted. Skipping "
                                + "answerVTCallPre().");
                    }
                    return;
                }
            }
            Log.d(TAG, "CallHandlerService answerVTCallPre().");
            mCallHandlerServiceGuarded.answerVTCallPre();
        } catch (Exception e) {
            Log.e(TAG, "Remote exception handling onStorageFull", e);
        }
    }

    public void onStorageFull() {
        try {
            synchronized (mServiceAndQueueLock) {
                if (mCallHandlerServiceGuarded == null) {
                    if (DBG) {
                        Log.d(TAG, "CallHandlerService not conneccted. Skipping "
                                + "onStorageFull().");
                    }
                    return;
                }
            }
            Log.d(TAG, "CallHandlerService onStorageFull().");
            mCallHandlerServiceGuarded.onStorageFull();
        } catch (Exception e) {
            Log.e(TAG, "Remote exception handling onStorageFull", e);
        }
    }

    public void requestUpdateRecordState(final int state, final int customValue) {
        synchronized (mServiceAndQueueLock) {
            if (mCallHandlerServiceGuarded == null) {
                if (DBG) {
                    Log.d(TAG, "CallHandlerService not connected.  Enqueue requestUpdateRecordState...");
                }
                enqueueUpdateRecordState(state, customValue);
                setupServiceConnection();
                return;
            }
        }
        processUpdateRecordState(state, customValue);
    }

    private void enqueueUpdateRecordState(final int state, final int customValue) {
        getQueue().add(new QueueParams(QueueParams.METHOD_UPDATE_RECORD_STATE, state, customValue));
    }

    private void processUpdateRecordState(final int state, final int customValue) {
        if (DBG) {
            Log.d(TAG, "onUpdateRecordState: state = " + state + ", customValue = " + customValue);
        }
        try {
            synchronized (mServiceAndQueueLock) {
                if (mCallHandlerServiceGuarded != null) {
                    mCallHandlerServiceGuarded.onUpdateRecordState(state, customValue);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Remote exception handling onUpdateRecordState", e);
        }
    }

    /*
     * when VTManagerWrapper receives VTManager's message, tell UI. 
     * Note: We must notify UI some important messages, such as OPEN / READY / CONNECTED
     * / DISCONNECTED / CLOSE ... so we need call setupServiceConnection(), if
     * there is no service connected (eg, the service is unbind by onDisconnect().) 
     * We suppose VTManager.VT_MSG_CLOSE will be the last call related
     * message from VTManager, so we unbind service after push this message to UI.
     * 
     * @see
     * com.mediatek.phone.vt.VTManagerWrapper.Listener#onVTStateChanged(int)
     */
    @Override
    public void onVTStateChanged(int msg) {
        synchronized (mServiceAndQueueLock) {
            if (mCallHandlerServiceGuarded == null) {
                if (DBG) {
                    Log.d(TAG, "CallHandlerService not connected. Enqueue VTStateChange. msg: " + msg);
                }
                enqueueVTStateChange(msg);
                setupServiceConnection();
                return;
            }
        }
        processVTStateChanged(msg);
    }

    private void processVTStateChanged(int msg) {
        if (DBG) {
            Log.d(TAG, "processVTStateChanged: " + msg);
        }
        try {
            synchronized (mServiceAndQueueLock) {
                if (mCallHandlerServiceGuarded != null) {
                    mCallHandlerServiceGuarded.onVTStateChanged(msg);
                }
            }
            if (!mCallModeler.hasLiveCall() && msg == VTManager.VT_MSG_CLOSE) {
                unbind();
            }
        } catch (Exception e) {
            Log.e(TAG, "Remote exception handling onVTSettingUpdate", e);
        }
    }

    private void enqueueVTStateChange(int vtMessage) {
        getQueue().add(new QueueParams(QueueParams.METHOD_VT_STATE_CHANGE, vtMessage));
    }

    /*
     * if called before service connected, just drop it (do not try to setupServiceConnection()).
     * @see com.mediatek.phone.vt.VTManagerWrapper.Listener#pushVTManagerParams(com.android.services.telephony.common.VTManagerParams)
     */
    public void pushVTManagerParams(VTManagerParams params) {
        try {
            synchronized (mServiceAndQueueLock) {
                if (mCallHandlerServiceGuarded == null) {
                    if (DBG) {
                        Log.d(TAG, "CallHandlerService not conneccted. Skipping "
                                + "pushVTManagerParams(params).");
                    }
                    return;
                }
            }
            Log.d(TAG, "CallHandlerService.pushVTManagerParams(params)." + params.toString());
            mCallHandlerServiceGuarded.pushVTManagerParams(params);
        } catch (Exception e) {
            Log.e(TAG, "Remote exception handling pushVTManagerParams()", e);
        }
    }

    @Override
    public void onSuppServiceFailed(String message) {
        try {
            synchronized (mServiceAndQueueLock) {
                if (mCallHandlerServiceGuarded == null) {
                    if (DBG) {
                        Log.d(TAG, "CallHandlerService not conneccted. Skipping "
                                + "oonSuppServiceFailed(message).");
                    }
                    return;
                }
            }
            Log.d(TAG, "CallHandlerService onSuppServiceFailed(message).");
            mCallHandlerServiceGuarded.onSuppServiceFailed(message);
        } catch (Exception e) {
            Log.e(TAG, "Remote exception handling onSuppServiceFailed", e);
        }
    }

    @Override
    public void acceptIncomingCallByVoiceCommand() {
        try {
            synchronized (mServiceAndQueueLock) {
                if (mCallHandlerServiceGuarded == null) {
                    if (DBG) {
                        Log.d(TAG, "CallHandlerService not conneccted. Skipping "
                                + "acceptIncomingCallByVoiceCommand().");
                    }
                    return;
                }
            }
            Log.d(TAG, "VoiceCommandHandler.acceptIncomingCallByVoiceCommand().");
            mCallHandlerServiceGuarded.acceptIncomingCallByVoiceCommand();
        } catch (Exception e) {
            Log.e(TAG, "Remote exception handling acceptIncomingCallByVoiceCommand()", e);
        }
    }

    @Override
    public void rejectIncomingCallByVoiceCommand() {
        try {
            synchronized (mServiceAndQueueLock) {
                if (mCallHandlerServiceGuarded == null) {
                    if (DBG) {
                        Log.d(TAG, "CallHandlerService not conneccted. Skipping "
                                + "rejectIncomingCallByVoiceCommand().");
                    }
                    return;
                }
            }
            Log.d(TAG, "VoiceCommandHandler.rejectIncomingCallByVoiceCommand().");
            mCallHandlerServiceGuarded.rejectIncomingCallByVoiceCommand();
        } catch (Exception e) {
            Log.e(TAG, "Remote exception handling rejectIncomingCallByVoiceCommand()", e);
        }
    }

    @Override
    public void receiveVoiceCommandNotificationMessage(String message) {
        try {
            synchronized (mServiceAndQueueLock) {
                if (mCallHandlerServiceGuarded == null) {
                    if (DBG) {
                        Log.d(TAG, "CallHandlerService not conneccted. Skipping "
                                + "receiveVoiceCommandNotificationMessage().");
                    }
                    return;
                }
            }
            Log.d(TAG, "VoiceCommandHandler.receiveVoiceCommandNotificationMessage(message).");
            mCallHandlerServiceGuarded.receiveVoiceCommandNotificationMessage(message);
        } catch (Exception e) {
            Log.e(TAG, "Remote exception handling receiveVoiceCommandNotificationMessage()", e);
        }
    }

    @Override
    public void onPhoneRaised() {
        // TODO Auto-generated method stub
        try {
            synchronized (mServiceAndQueueLock) {
                if (mCallHandlerServiceGuarded == null) {
                    if (DBG) {
                        Log.d(TAG, "CallHandlerService not conneccted. Skipping "
                                + "onPhoneRaised().");
                    }
                    return;
                }
            }
            Log.d(TAG, "VoiceCommandHandler.onPhoneRaised().");
            mCallHandlerServiceGuarded.onPhoneRaised();
        } catch (Exception e) {
            Log.e(TAG, "Remote exception handling onPhoneRaised()", e);
        }
    }

    public void updateDualtalkCallStatus(DualtalkCallInfo info) {
        try {
            synchronized (mServiceAndQueueLock) {
                if (mCallHandlerServiceGuarded == null) {
                    if (DBG) {
                        Log.d(TAG, "CallHandlerService not conneccted. Skipping "
                                + "updateDualtalkCallStatus().");
                    }
                    return;
                }
            }
            Log.d(TAG, "updateDualtalkCallStatus.");
            mCallHandlerServiceGuarded.updateDualtalkCallStatus(info);
        } catch (Exception e) {
            Log.e(TAG, "Remote exception handling updateDualtalkCallStatus()", e);
        }
    }
}
