/*
 * Copyright (C) 2008 The Android Open Source Project
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

package android.net;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.NetworkInfo.DetailedState;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.SystemProperties;
import android.telephony.PhoneNumberUtils;
import android.telephony.ServiceState;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Slog;

import com.android.internal.telephony.DctConstants;
import com.android.internal.telephony.ITelephony;
import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.TelephonyIntents;
import com.android.internal.telephony.TelephonyProperties;
import com.android.internal.util.AsyncChannel;
import com.mediatek.common.featureoption.FeatureOption;
import java.io.CharArrayWriter;
import java.io.PrintWriter;

import java.util.HashMap;
import java.util.Collection;
import java.util.Iterator;

/**
 * Track the state of mobile data connectivity. This is done by
 * receiving broadcast intents from the Phone process whenever
 * the state of data connectivity changes.
 *
 * {@hide}
 */
public class CsdStateTracker implements NetworkStateTracker {

    private static final String TAG = "CsdStateTracker";
    private static final boolean DBG = true;
    private static final boolean VDBG = true;

    private PhoneConstants.DataState mMobileDataState;
    private ITelephony mPhoneService;

    private NetworkInfo mNetworkInfo;
    private boolean mTeardownRequested = false;
    private Handler mTarget;
    private Context mContext;
    private LinkProperties mLinkProperties;
    private LinkCapabilities mLinkCapabilities;
    private boolean mPrivateDnsRouteSet = false;
    private boolean mDefaultRouteSet = false;

    // NOTE: these are only kept for debugging output; actual values are
    // maintained in DataConnectionTracker.
    protected boolean mUserDataEnabled = true;
    protected boolean mPolicyDataEnabled = true;

    private AsyncChannel mDataConnectionTrackerAc;
    private Messenger mMessenger;
    private String mCsdNumber;
    private int mSimId = 0;
    private BroadcastReceiver mCsdStateReceiver;

    /**
     * Create a new MobileDataStateTracker
     * @param netType the ConnectivityManager network type
     * @param tag the name of this network
     */
    public CsdStateTracker(int netType, String tag) {
        mNetworkInfo = new NetworkInfo(netType,
                TelephonyManager.getDefault().getNetworkType(), tag,
                TelephonyManager.getDefault().getNetworkTypeName());
        log("CsdStateTracker create");
    }

    /**
     * Begin monitoring data connectivity.
     *
     * @param context is the current Android context
     * @param target is the Hander to which to return the events.
     */
    public void startMonitoring(Context context, Handler target) {
        mTarget = target;
        mContext = context;

        IntentFilter filter = new IntentFilter();
        filter.addAction(TelephonyIntents.ACTION_ANY_DATA_CONNECTION_STATE_CHANGED_CSD);
        filter.addAction(TelephonyManager.ACTION_PHONE_STATE_CHANGED);
        filter.addAction("android.intent.action.CSD_DIAL");
        mCsdStateReceiver = new CsdDataStateReceiver();
        mContext.registerReceiver(mCsdStateReceiver, filter);
        mMobileDataState = PhoneConstants.DataState.DISCONNECTED;

        log("CsdStateTracker startMonitoring");
    }

    public boolean isPrivateDnsRouteSet() {
        return mPrivateDnsRouteSet;
    }

    public void privateDnsRouteSet(boolean enabled) {
        mPrivateDnsRouteSet = enabled;
    }

    public NetworkInfo getNetworkInfo() {
        return mNetworkInfo;
    }

    public boolean isDefaultRouteSet() {
        return mDefaultRouteSet;
    }

    public void defaultRouteSet(boolean enabled) {
        mDefaultRouteSet = enabled;
    }

    /**
     * This is not implemented.
     */
    public void releaseWakeLock() {
    }

    private class CsdDataStateReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {    
            log("CsdDataStateReceiver onReceive");
            
            if (intent.getAction().equals(TelephonyIntents.ACTION_ANY_DATA_CONNECTION_STATE_CHANGED_CSD)) {
                boolean state = intent.getBooleanExtra("state",false);
                log("ACTION_ANY_DATA_CONNECTION_STATE_CHANGED_CSD connect:" + state);
                mLinkProperties = intent.getParcelableExtra(PhoneConstants.DATA_LINK_PROPERTIES_KEY);
                if (mLinkProperties == null){
		    mLinkProperties = new LinkProperties();
		}
                mLinkCapabilities = intent.getParcelableExtra(PhoneConstants.DATA_LINK_CAPABILITIES_KEY);
                if (mLinkCapabilities == null){
		    mLinkCapabilities = new LinkCapabilities();
		}

                mNetworkInfo.setDetailedState(state ? NetworkInfo.DetailedState.CONNECTED : NetworkInfo.DetailedState.DISCONNECTED , "", "");
                Message msg = mTarget.obtainMessage(EVENT_STATE_CHANGED, mNetworkInfo);
                msg.sendToTarget();
            } else if (intent.getAction().equals(TelephonyManager.ACTION_PHONE_STATE_CHANGED)) {
                String state = intent.getStringExtra(TelephonyManager.EXTRA_STATE);
                if (state.equals(TelephonyManager.EXTRA_STATE_IDLE) && mNetworkInfo.isConnected()) {
                    setTeardownRequested(false);
                    mNetworkInfo.setDetailedState(NetworkInfo.DetailedState.DISCONNECTED, "", "");
                    Message msg = mTarget.obtainMessage(EVENT_STATE_CHANGED, mNetworkInfo);
                    msg.sendToTarget();
                }
            } else if (intent.getAction().equals("android.intent.action.CSD_DIAL")) {
            //android.intent.action.CSD_DIAL
                log("CsdDataStateReceiver get intent:" + intent.getAction());
                mCsdNumber = PhoneNumberUtils.getNumberFromIntent(intent, context);
                // com.android.phone.extra.slot is defined in Constants.java of Phone App
                mSimId = intent.getIntExtra("com.android.phone.extra.slot", 0);
                if (DBG) Slog.d(TAG, "android.intent.action.CSD_DIAL: slotid=" + mSimId + ", csdnum=" + mCsdNumber);
                reconnect();
            } else {
                if (DBG) Slog.d(TAG, "MobileDataStateReceiver received: ignore " + intent.getAction());
            }
        }
    }

    private void getPhoneService() {
        mPhoneService = ITelephony.Stub.asInterface(ServiceManager.getService("phone"));
    }

    /**
     * Report whether data connectivity is possible.
     */
    public boolean isAvailable() {
        return mNetworkInfo.isAvailable();
    }

    /**
     * Return the system properties name associated with the tcp buffer sizes
     * for this network.
     */
    public String getTcpBufferSizesPropName() {
        return "net.tcp.buffersize.csd";
    }

    /**
     * Tear down mobile data connectivity, i.e., disable the ability to create
     * mobile data connections.
     * TODO - make async and return nothing?
     */
    public boolean teardown() {
        setTeardownRequested(true);
        getPhoneService();

        for (int retry = 0; retry < 2; retry++) {
            if (mPhoneService == null) break;

            try {
                if (FeatureOption.MTK_GEMINI_SUPPORT) {
                    return mPhoneService.endCallGemini(mSimId);
                } else {
                    return mPhoneService.endCall();
                }
            } catch (RemoteException e) {
                // First-time failed, get the phone service again
                if (retry == 0) getPhoneService();
            }
        }
        return false;
    }

    @Override
    public void captivePortalCheckComplete() {
        // not implemented
    }

    @Override
    public void captivePortalCheckCompleted(boolean isCaptive){
    }

    public void setTeardownRequested(boolean isRequested) {
        mTeardownRequested = isRequested;
    }

    public boolean isTeardownRequested() {
        return mTeardownRequested;
    }

    public void setDialUpParam(int simId, String dialUpNumber) {
        mSimId = simId;
        mCsdNumber = dialUpNumber;
    }

    /**
     * Re-enable mobile data connectivity after a {@link #teardown()}.
     * TODO - make async and always get a notification?
     */
    public boolean reconnect() {
        boolean retValue = false; //connected or expect to be?
        
        setTeardownRequested(false);
        getPhoneService();

        for (int retry = 0; retry < 2; retry++) {
            if (mPhoneService == null) break;

            try {
                return mPhoneService.dialUpCsd(mSimId, mCsdNumber) > 0;
            } catch (RemoteException e) {
                // First-time failed, get the phone service again
                if (retry == 0) getPhoneService();
            }
        }
        return false;

        /* switch (setEnableApn(mApnType, true)) {
            case PhoneConstants.APN_ALREADY_ACTIVE:
                // need to set self to CONNECTING so the below message is handled.
                //TODO: need to notify the app
                retValue = true;
                break;
            case PhoneConstants.APN_REQUEST_STARTED:
                // set IDLE here , avoid the following second FAILED not sent out
                if (!mNetworkInfo.isConnectedOrConnecting()) {
                    mNetworkInfo.setDetailedState(DetailedState.IDLE, null, null);
                }
                retValue = true;
                break;
            case PhoneConstants.APN_REQUEST_FAILED:
            case PhoneConstants.APN_TYPE_NOT_AVAILABLE:
                break;
            default:
                loge("Error in reconnect - unexpected response.");
                break;
        }
        return retValue; */
    }

    @Override
    public String toString() {
        final CharArrayWriter writer = new CharArrayWriter();
        final PrintWriter pw = new PrintWriter(writer);
        pw.print("CSD data state: "); pw.println(mMobileDataState);
        return writer.toString();
    }

    /**
     * Turn on or off the mobile radio. No connectivity will be possible while the
     * radio is off. The operation is a no-op if the radio is already in the desired state.
     * @param turnOn {@code true} if the radio should be turned on, {@code false} if
     */
    public boolean setRadio(boolean turnOn) {
        getPhoneService();
        /*
         * If the phone process has crashed in the past, we'll get a
         * RemoteException and need to re-reference the service.
         */
        for (int retry = 0; retry < 2; retry++) {
            if (mPhoneService == null) {
                loge("Ignoring mobile radio request because could not acquire PhoneService");
                break;
            }

            try {
                return mPhoneService.setRadio(turnOn);
            } catch (RemoteException e) {
                if (retry == 0) getPhoneService();
            }
        }

        loge("Could not set radio power to " + (turnOn ? "on" : "off"));
        return false;
    }

    /**
     * @see android.net.NetworkStateTracker#getLinkProperties()
     */
    public LinkProperties getLinkProperties() {
        return new LinkProperties(mLinkProperties);
    }

    /**
     * @see android.net.NetworkStateTracker#getLinkCapabilities()
     */
    public LinkCapabilities getLinkCapabilities() {
        return new LinkCapabilities(mLinkCapabilities);
    }
    
    @Override
    public LinkQualityInfo getLinkQualityInfo() {
        return null;
    }

    @Override
    public void setDependencyMet(boolean met) {
        // not supported on this network
    }

    @Override
    public void setUserDataEnable(boolean enabled) {
        // ignored
    }

    @Override
    public void setPolicyDataEnable(boolean enabled) {
        // ignored
    }


    @Override
    public void addStackedLink(LinkProperties link){
    }
    

    @Override
    public void removeStackedLink(LinkProperties link){
    }

    @Override
    public void startSampling(SamplingDataTracker.SamplingSnapshot s){
    }

    @Override
    public void stopSampling(SamplingDataTracker.SamplingSnapshot s){    
    }

    @Override
    public String getNetworkInterfaceName() {
        return null;
    }

    @Override
    public void supplyMessenger(Messenger messenger) {
        //ignored
    }
    
    private void log(String s) {
        Slog.d(TAG, ": " + s);
    }

    private void loge(String s) {
        Slog.e(TAG, ": " + s);
    }

    static private void sloge(String s) {
        Slog.e(TAG, s);
    }
   
}
/** @} */
