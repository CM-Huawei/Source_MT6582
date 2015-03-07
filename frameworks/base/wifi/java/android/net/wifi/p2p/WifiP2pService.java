/*
 * Copyright (C) 2011 The Android Open Source Project
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

package android.net.wifi.p2p;

import android.app.AlertDialog;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.net.IConnectivityManager;
import android.net.ConnectivityManager;
import android.net.DhcpResults;
import android.net.DhcpStateMachine;
import android.net.InterfaceConfiguration;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.net.NetworkInfo;
import android.net.NetworkUtils;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiMonitor;
import android.net.wifi.WifiNative;
import android.net.wifi.WifiStateMachine;
import android.net.wifi.WpsInfo;
import android.net.wifi.p2p.WifiP2pGroupList.GroupDeleteListener;
import android.net.wifi.p2p.nsd.WifiP2pServiceInfo;
import android.net.wifi.p2p.nsd.WifiP2pServiceRequest;
import android.net.wifi.p2p.nsd.WifiP2pServiceResponse;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.os.INetworkManagementService;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Slog;
import android.util.SparseArray;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.TextView;

import com.android.internal.R;
import com.android.internal.telephony.TelephonyIntents;
import com.android.internal.util.AsyncChannel;
import com.android.internal.util.Protocol;
import com.android.internal.util.State;
import com.android.internal.util.StateMachine;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

///M:@{
import android.net.wifi.p2p.fastconnect.WifiP2pFastConnectInfo;
import android.net.wifi.p2p.link.WifiP2pLinkInfo;
import android.net.RouteInfo;
import android.os.Environment;
import android.widget.Toast;

import java.net.InetAddress;
import java.util.Collections;
import java.util.Iterator;
import java.util.TreeMap;
import android.util.Log;
///@}

/**
 * WifiP2pService includes a state machine to perform Wi-Fi p2p operations. Applications
 * communicate with this service to issue device discovery and connectivity requests
 * through the WifiP2pManager interface. The state machine communicates with the wifi
 * driver through wpa_supplicant and handles the event responses through WifiMonitor.
 *
 * Note that the term Wifi when used without a p2p suffix refers to the client mode
 * of Wifi operation
 * @hide
 */
public class WifiP2pService extends IWifiP2pManager.Stub {
    private static final String TAG = "WifiP2pService";
    private static final boolean DBG = true;  ///Modify by MTK
    private static final String NETWORKTYPE = "WIFI_P2P";

    private Context mContext;
    private String mInterface;
    private Notification mNotification;

    INetworkManagementService mNwService;
    private DhcpStateMachine mDhcpStateMachine;

    private P2pStateMachine mP2pStateMachine;
    private AsyncChannel mReplyChannel = new AsyncChannel();
    private AsyncChannel mWifiChannel;

    private static final Boolean JOIN_GROUP = true;
    private static final Boolean FORM_GROUP = false;

    private static final Boolean RELOAD = true;
    private static final Boolean NO_RELOAD = false;

    /* Two minutes comes from the wpa_supplicant setting */
    private static final int GROUP_CREATING_WAIT_TIME_MS = 120 * 1000;
    private static int mGroupCreatingTimeoutIndex = 0;

    private static final int DISABLE_P2P_WAIT_TIME_MS = 5 * 1000;
    private static int mDisableP2pTimeoutIndex = 0;

    /* Set a two minute discover timeout to avoid STA scans from being blocked */
    private static final int DISCOVER_TIMEOUT_S = 120;

    /* Idle time after a peer is gone when the group is torn down */
    private static final int GROUP_IDLE_TIME_S = 10;

    private static final int BASE = Protocol.BASE_WIFI_P2P_SERVICE;

    /* Delayed message to timeout group creation */
    public static final int GROUP_CREATING_TIMED_OUT        =   BASE + 1;

    /* User accepted a peer request */
    private static final int PEER_CONNECTION_USER_ACCEPT    =   BASE + 2;
    /* User rejected a peer request */
    private static final int PEER_CONNECTION_USER_REJECT    =   BASE + 3;
    /* User wants to disconnect wifi in favour of p2p */
    private static final int DROP_WIFI_USER_ACCEPT          =   BASE + 4;
    /* User wants to keep his wifi connection and drop p2p */
    private static final int DROP_WIFI_USER_REJECT          =   BASE + 5;
    /* Delayed message to timeout p2p disable */
    public static final int DISABLE_P2P_TIMED_OUT           =   BASE + 6;
    /*M: wifi p2p fast connect*/
    public static final int FAST_CONNECT_FIND_GO_TIMED_OUT  =   BASE + 9;


    /* Commands to the WifiStateMachine */
    public static final int P2P_CONNECTION_CHANGED          =   BASE + 11;

    /* These commands are used to temporarily disconnect wifi when we detect
     * a frequency conflict which would make it impossible to have with p2p
     * and wifi active at the same time.
     *
     * If the user chooses to disable wifi temporarily, we keep wifi disconnected
     * until the p2p connection is done and terminated at which point we will
     * bring back wifi up
     *
     * DISCONNECT_WIFI_REQUEST
     *      msg.arg1 = 1 enables temporary disconnect and 0 disables it.
     */
    public static final int DISCONNECT_WIFI_REQUEST         =   BASE + 12;
    public static final int DISCONNECT_WIFI_RESPONSE        =   BASE + 13;

    public static final int SET_MIRACAST_MODE               =   BASE + 14;

    // During dhcp (and perhaps other times) we can't afford to drop packets
    // but Discovery will switch our channel enough we will.
    //   msg.arg1 = ENABLED for blocking, DISABLED for resumed.
    //   msg.arg2 = msg to send when blocked
    //   msg.obj  = StateMachine to send to when blocked
    public static final int BLOCK_DISCOVERY                 =   BASE + 15;

    // set country code
    public static final int SET_COUNTRY_CODE                =   BASE + 16;

    public static final int ENABLED                         = 1;
    public static final int DISABLED                        = 0;

    private final boolean mP2pSupported;

    private WifiP2pDevice mThisDevice = new WifiP2pDevice();

    /* When a group has been explicitly created by an app, we persist the group
     * even after all clients have been disconnected until an explicit remove
     * is invoked */
    private boolean mAutonomousGroup;

    /* Invitation to join an existing p2p group */
    private boolean mJoinExistingGroup;

    /* Track whether we are in p2p discovery. This is used to avoid sending duplicate
     * broadcasts
     */
    private boolean mDiscoveryStarted;
    /* Track whether servcice/peer discovery is blocked in favor of other wifi actions
     * (notably dhcp)
     */
    private boolean mDiscoveryBlocked;

    // Supplicant doesn't like setting the same country code multiple times (it may drop
    // current connected network), so we save the country code here to avoid redundency
    private String mLastSetCountryCode;

    /*
     * remember if we were in a scan when it had to be stopped
     */
    private boolean mDiscoveryPostponed = false;

    private NetworkInfo mNetworkInfo;

    private boolean mTempoarilyDisconnectedWifi = false;

    /* The transaction Id of service discovery request */
    private byte mServiceTransactionId = 0;

    /* Service discovery request ID of wpa_supplicant.
     * null means it's not set yet. */
    private String mServiceDiscReqId;

    /* clients(application) information list. */
    private HashMap<Messenger, ClientInfo> mClientInfoList = new HashMap<Messenger, ClientInfo>();

    ///M: variables @{
    /* Is chosen as a unique range to avoid conflict with
     * the range defined in Tethering.java
     * M: Changed due to fast connect's static IP, GC's static IP is 192.168.49.2*/
    private static final String[] DHCP_RANGE = {"192.168.49.3", "192.168.49.254"};
    public static final String SERVER_ADDRESS = "192.168.49.1";

    /*M: wifi direct fast connect, default time out value is 30s*/
    private static final int FAST_CONNECT_FIND_GO_WAIT_TIME_MS = 30 * 1000;
    private static int mFastConnectFindGoTimeOutIndex = 0;

    /*M: fast connect scan time interval is 3s*/
    private static final int FAST_SCAN_INTERVAL_TIME_MS = 3 * 1000;

    /*M: for fast connect scan mechanism */
    private static final int FAST_DISCOVER_TIMEOUT_S = 123;

    /*M: Set 25s for ALPS00450978, because scan block to
     * feel some peers has diappeared */
    private static final int CONNECTED_DISCOVER_TIMEOUT_S = 25;

    /*M: Power Saving Command*/
    public static final int P2P_ACTIVE  = 0;
    //When traffic is large will not ajust active/PS
    public static final int P2P_MAX_PS  = 1;
    //When traffic is large ajust active/PS automatically
    public static final int P2P_FAST_PS = 2;

    /*M: add to Enable wifi/wifi p2p */
    private WifiManager mWifiManager;

    /*M: NFC hand over wifi direct start*/
    private WifiP2pFastConnectInfo mFastConnectInfo =  new WifiP2pFastConnectInfo();
    private String mThisDeviceAddress;
    private boolean mGcFastConnectEnaled = false;
    private boolean mGoFastConnectEnaled = false;
    private boolean mFoundGo = false;
    private boolean mFoundGoTimeOut = false;
    private boolean mRestartFastConnectAsGo = false;
    private boolean mRestartFastConnectAsGc = false;
    /*NFC hand over wifi direct end*/

    /*M: add for ALPS00489161.The reason is when group created successfully,
    and UI do not know in time still send cancel connect command. 
    This case GroupcreatedState should remove the group. But when Inviting 3-device
    to join a group and 3-device do not response.At this time GO or GC click cancel means 
    the Group has created entirely, so use the default solution to process the cancel command*/
    private boolean mGroupCreatedEntirely = false;

    /*M: ALPS00677009: broadcast the group removed reason*/
    private P2pStatus mGroupRemoveReason;
    
    /*M: ALPS01000113: sync. Beam+ state*/
    private boolean mBeamPlusStart = false;
    ///@}

    /**
     * Error code definition.
     * see the Table.8 in the WiFi Direct specification for the detail.
     */
    public static enum P2pStatus {
        /* Success. */
        SUCCESS,

        /* The target device is currently unavailable. */
        INFORMATION_IS_CURRENTLY_UNAVAILABLE,

        /* Protocol error. */
        INCOMPATIBLE_PARAMETERS,

        /* The target device reached the limit of the number of the connectable device.
         * For example, device limit or group limit is set. */
        LIMIT_REACHED,

        /* Protocol error. */
        INVALID_PARAMETER,

        /* Unable to accommodate request. */
        UNABLE_TO_ACCOMMODATE_REQUEST,

        /* Previous protocol error, or disruptive behavior. */
        PREVIOUS_PROTOCOL_ERROR,

        /* There is no common channels the both devices can use. */
        NO_COMMON_CHANNEL,

        /* Unknown p2p group. For example, Device A tries to invoke the previous persistent group,
         *  but device B has removed the specified credential already. */
        UNKNOWN_P2P_GROUP,

        /* Both p2p devices indicated an intent of 15 in group owner negotiation. */
        BOTH_GO_INTENT_15,

        /* Incompatible provisioning method. */
        INCOMPATIBLE_PROVISIONING_METHOD,

        /* Rejected by user */
        REJECTED_BY_USER,

        /* Unknown error */
        UNKNOWN;

        public static P2pStatus valueOf(int error) {
            switch(error) {
            case 0 :
                return SUCCESS;
            case 1:
                return INFORMATION_IS_CURRENTLY_UNAVAILABLE;
            case 2:
                return INCOMPATIBLE_PARAMETERS;
            case 3:
                return LIMIT_REACHED;
            case 4:
                return INVALID_PARAMETER;
            case 5:
                return UNABLE_TO_ACCOMMODATE_REQUEST;
            case 6:
                return PREVIOUS_PROTOCOL_ERROR;
            case 7:
                return NO_COMMON_CHANNEL;
            case 8:
                return UNKNOWN_P2P_GROUP;
            case 9:
                return BOTH_GO_INTENT_15;
            case 10:
                return INCOMPATIBLE_PROVISIONING_METHOD;
            case 11:
                return REJECTED_BY_USER;
            default:
                return UNKNOWN;
            }
        }
    }

    public WifiP2pService(Context context) {
        mContext = context;

        //STOPSHIP: get this from native side
        mInterface = "p2p0";
        mNetworkInfo = new NetworkInfo(ConnectivityManager.TYPE_WIFI_P2P, 0, NETWORKTYPE, "");

        mP2pSupported = mContext.getPackageManager().hasSystemFeature(
                PackageManager.FEATURE_WIFI_DIRECT);

        mThisDevice.primaryDeviceType = mContext.getResources().getString(
                com.android.internal.R.string.config_wifi_p2p_device_type);

        mP2pStateMachine = new P2pStateMachine(TAG, mP2pSupported);
        mP2pStateMachine.start();
    }

    public void connectivityServiceReady() {
        IBinder b = ServiceManager.getService(Context.NETWORKMANAGEMENT_SERVICE);
        mNwService = INetworkManagementService.Stub.asInterface(b);
    }

    private void enforceAccessPermission() {
        mContext.enforceCallingOrSelfPermission(android.Manifest.permission.ACCESS_WIFI_STATE,
                "WifiP2pService");
    }

    private void enforceChangePermission() {
        mContext.enforceCallingOrSelfPermission(android.Manifest.permission.CHANGE_WIFI_STATE,
                "WifiP2pService");
    }

    private void enforceConnectivityInternalPermission() {
        mContext.enforceCallingOrSelfPermission(
                android.Manifest.permission.CONNECTIVITY_INTERNAL,
                "WifiP2pService");
    }

    /**
     * Get a reference to handler. This is used by a client to establish
     * an AsyncChannel communication with WifiP2pService
     */
    public Messenger getMessenger() {
        enforceAccessPermission();
        enforceChangePermission();
        return new Messenger(mP2pStateMachine.getHandler());
    }

    /** This is used to provide information to drivers to optimize performance depending
     * on the current mode of operation.
     * 0 - disabled
     * 1 - source operation
     * 2 - sink operation
     *
     * As an example, the driver could reduce the channel dwell time during scanning
     * when acting as a source or sink to minimize impact on miracast.
     */
    public void setMiracastMode(int mode) {
        enforceConnectivityInternalPermission();
        mP2pStateMachine.sendMessage(SET_MIRACAST_MODE, mode);
    }

    @Override
    protected void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        if (mContext.checkCallingOrSelfPermission(android.Manifest.permission.DUMP)
                != PackageManager.PERMISSION_GRANTED) {
            pw.println("Permission Denial: can't dump WifiP2pService from from pid="
                    + Binder.getCallingPid()
                    + ", uid=" + Binder.getCallingUid());
            return;
        }
        mP2pStateMachine.dump(fd, pw, args);
        pw.println("mAutonomousGroup " + mAutonomousGroup);
        pw.println("mJoinExistingGroup " + mJoinExistingGroup);
        pw.println("mDiscoveryStarted " + mDiscoveryStarted);
        pw.println("mNetworkInfo " + mNetworkInfo);
        pw.println("mTempoarilyDisconnectedWifi " + mTempoarilyDisconnectedWifi);
        pw.println("mServiceDiscReqId " + mServiceDiscReqId);
        pw.println();
    }

    ///M:@{
    /**
     * M: set in fast connect info's device address and venderId to framework
     * return the crendential and mac address of it self
     * @hide
     */
    public WifiP2pFastConnectInfo fastConnectAsGo(WifiP2pFastConnectInfo info) {
        Log.d(TAG, "fastConnectAsGo");
        try {
            Log.d(TAG, "Fast Connect As GO in fastConnectIno: " + info);

            //WifiP2pFastConnectInfo returnConnectInfo = new WifiP2pFastConnectInfo(WifiNative.getCredential());
            String strCred = "";
            strCred = WifiNative.getCredential();
            Log.d(TAG, "fastConnectAsGo, strCred = " + strCred);
            WifiP2pFastConnectInfo returnConnectInfo = new WifiP2pFastConnectInfo(strCred);
            
            //M: ALPS00949455: add protection if this device's p2p MAC had been changed
            if (null!=mThisDevice &&
                    !mThisDevice.deviceAddress.equals(mThisDeviceAddress)) {
                mThisDeviceAddress = WifiNative.getP2pDeviceAddress();
            }
            returnConnectInfo.deviceAddress = "";
            // get GC address, GO can avoid other device connect to it.
            mFastConnectInfo = new WifiP2pFastConnectInfo(returnConnectInfo);
            mFastConnectInfo.deviceAddress = info.deviceAddress;
            mFastConnectInfo.venderId = info.venderId;
            mP2pStateMachine.sendMessage(WifiP2pManager.START_FAST_CONNECT_AS_GO);

            Log.d(TAG, "Fast Connect As GO return fastConnectIno: " + returnConnectInfo);
            Log.d(TAG, "Fast Connect As GO Self fastConnectInfo: " + mFastConnectInfo);
            return returnConnectInfo;
        } catch (Exception e) {
            Log.e(TAG, "fastConnectAsGo() exception: " + e);
            return null;
        }        
    }

    /*M: get this device's Device Address*/
    public String getMacAddress() {
    	return WifiNative.getP2pDeviceAddress();
    }
    ///@}

    /**
     * Handles interaction with WifiStateMachine
     */
    private class P2pStateMachine extends StateMachine {

        private DefaultState mDefaultState = new DefaultState();
        private P2pNotSupportedState mP2pNotSupportedState = new P2pNotSupportedState();
        private P2pDisablingState mP2pDisablingState = new P2pDisablingState();
        private P2pDisabledState mP2pDisabledState = new P2pDisabledState();
        private P2pEnablingState mP2pEnablingState = new P2pEnablingState();
        private P2pEnabledState mP2pEnabledState = new P2pEnabledState();
        // Inactive is when p2p is enabled with no connectivity
        private InactiveState mInactiveState = new InactiveState();
        private GroupCreatingState mGroupCreatingState = new GroupCreatingState();
        private UserAuthorizingInviteRequestState mUserAuthorizingInviteRequestState
                = new UserAuthorizingInviteRequestState();
        private UserAuthorizingNegotiationRequestState mUserAuthorizingNegotiationRequestState
                = new UserAuthorizingNegotiationRequestState();
        private ProvisionDiscoveryState mProvisionDiscoveryState = new ProvisionDiscoveryState();
        private GroupNegotiationState mGroupNegotiationState = new GroupNegotiationState();
        private FrequencyConflictState mFrequencyConflictState =new FrequencyConflictState();

        private GroupCreatedState mGroupCreatedState = new GroupCreatedState();
        private UserAuthorizingJoinState mUserAuthorizingJoinState = new UserAuthorizingJoinState();
        private OngoingGroupRemovalState mOngoingGroupRemovalState = new OngoingGroupRemovalState();

        private WifiNative mWifiNative = new WifiNative(mInterface);
        private WifiMonitor mWifiMonitor = new WifiMonitor(this, mWifiNative);

        private final WifiP2pDeviceList mPeers = new WifiP2pDeviceList();
        /* During a connection, supplicant can tell us that a device was lost. From a supplicant's
         * perspective, the discovery stops during connection and it purges device since it does
         * not get latest updates about the device without being in discovery state.
         *
         * From the framework perspective, the device is still there since we are connecting or
         * connected to it. so we keep these devices in a separate list, so that they are removed
         * when connection is cancelled or lost
         */
        private final WifiP2pDeviceList mPeersLostDuringConnection = new WifiP2pDeviceList();
        private final WifiP2pGroupList mGroups = new WifiP2pGroupList(null,
                new GroupDeleteListener() {
            @Override
            public void onDeleteGroup(int netId) {
                if (DBG) logd("called onDeleteGroup() netId=" + netId);
                mWifiNative.removeNetwork(netId);
                mWifiNative.saveConfig();
                sendP2pPersistentGroupsChangedBroadcast();
            }
        });
        private final WifiP2pInfo mWifiP2pInfo = new WifiP2pInfo();
        private WifiP2pGroup mGroup;

        // Saved WifiP2pConfig for an ongoing peer connection. This will never be null.
        // The deviceAddress will be an empty string when the device is inactive
        // or if it is connected without any ongoing join request
        private WifiP2pConfig mSavedPeerConfig = new WifiP2pConfig();

        // Saved WifiP2pGroup from invitation request
        private WifiP2pGroup mSavedP2pGroup;

        P2pStateMachine(String name, boolean p2pSupported) {
            super(name);

            addState(mDefaultState);
                addState(mP2pNotSupportedState, mDefaultState);
                addState(mP2pDisablingState, mDefaultState);
                addState(mP2pDisabledState, mDefaultState);
                addState(mP2pEnablingState, mDefaultState);
                addState(mP2pEnabledState, mDefaultState);
                    addState(mInactiveState, mP2pEnabledState);
                    addState(mGroupCreatingState, mP2pEnabledState);
                        addState(mUserAuthorizingInviteRequestState, mGroupCreatingState);
                        addState(mUserAuthorizingNegotiationRequestState, mGroupCreatingState);
                        addState(mProvisionDiscoveryState, mGroupCreatingState);
                        addState(mGroupNegotiationState, mGroupCreatingState);
                        addState(mFrequencyConflictState, mGroupCreatingState);
                    addState(mGroupCreatedState, mP2pEnabledState);
                        addState(mUserAuthorizingJoinState, mGroupCreatedState);
                        addState(mOngoingGroupRemovalState, mGroupCreatedState);

            if (p2pSupported) {
                setInitialState(mP2pDisabledState);
            } else {
                setInitialState(mP2pNotSupportedState);
            }

            setLogRecSize(50);
            setLogOnlyTransitions(true);
        }

    class DefaultState extends State {
        @Override
        public boolean processMessage(Message message) {
            if (DBG) logd(getName() + message.toString());
            switch (message.what) {
                case AsyncChannel.CMD_CHANNEL_HALF_CONNECTED:
                    if (message.arg1 == AsyncChannel.STATUS_SUCCESSFUL) {
                        if (DBG) logd("Full connection with WifiStateMachine established");
                        mWifiChannel = (AsyncChannel) message.obj;
                    } else {
                        loge("Full connection failure, error = " + message.arg1);
                        mWifiChannel = null;
                    }
                    break;

                case AsyncChannel.CMD_CHANNEL_DISCONNECTED:
                    if (message.arg1 == AsyncChannel.STATUS_SEND_UNSUCCESSFUL) {
                        loge("Send failed, client connection lost");
                    } else {
                        loge("Client connection lost with reason: " + message.arg1);
                    }
                    mWifiChannel = null;
                    break;

                case AsyncChannel.CMD_CHANNEL_FULL_CONNECTION:
                    AsyncChannel ac = new AsyncChannel();
                    ac.connect(mContext, getHandler(), message.replyTo);
                    break;
                case BLOCK_DISCOVERY:
                    mDiscoveryBlocked = (message.arg1 == ENABLED ? true : false);
                    logd("DefaultState, case BLOCK_DISCOVERY, mDiscoveryBlocked = " + mDiscoveryBlocked);
                    // always reset this - we went to a state that doesn't support discovery so
                    // it would have stopped regardless
                    mDiscoveryPostponed = false;
                    if (mDiscoveryBlocked) {
                        try {
                            StateMachine m = (StateMachine)message.obj;
                            m.sendMessage(message.arg2);
                        } catch (Exception e) {
                            loge("unable to send BLOCK_DISCOVERY response: " + e);
                        }
                    }
                    break;
                case WifiP2pManager.DISCOVER_PEERS:
                    replyToMessage(message, WifiP2pManager.DISCOVER_PEERS_FAILED,
                            WifiP2pManager.BUSY);
                    break;
                case WifiP2pManager.STOP_DISCOVERY:
                    replyToMessage(message, WifiP2pManager.STOP_DISCOVERY_FAILED,
                            WifiP2pManager.BUSY);
                    break;
                case WifiP2pManager.DISCOVER_SERVICES:
                    replyToMessage(message, WifiP2pManager.DISCOVER_SERVICES_FAILED,
                            WifiP2pManager.BUSY);
                    break;
                case WifiP2pManager.CONNECT:
                    replyToMessage(message, WifiP2pManager.CONNECT_FAILED,
                            WifiP2pManager.BUSY);
                    break;
                case WifiP2pManager.CANCEL_CONNECT:
                    replyToMessage(message, WifiP2pManager.CANCEL_CONNECT_FAILED,
                            WifiP2pManager.BUSY);
                    break;
                case WifiP2pManager.CREATE_GROUP:
                    replyToMessage(message, WifiP2pManager.CREATE_GROUP_FAILED,
                            WifiP2pManager.BUSY);
                    break;
                case WifiP2pManager.REMOVE_GROUP:
                    replyToMessage(message, WifiP2pManager.REMOVE_GROUP_FAILED,
                            WifiP2pManager.BUSY);
                    break;
                case WifiP2pManager.ADD_LOCAL_SERVICE:
                    replyToMessage(message, WifiP2pManager.ADD_LOCAL_SERVICE_FAILED,
                            WifiP2pManager.BUSY);
                    break;
                case WifiP2pManager.REMOVE_LOCAL_SERVICE:
                    replyToMessage(message, WifiP2pManager.REMOVE_LOCAL_SERVICE_FAILED,
                            WifiP2pManager.BUSY);
                    break;
                case WifiP2pManager.CLEAR_LOCAL_SERVICES:
                    replyToMessage(message, WifiP2pManager.CLEAR_LOCAL_SERVICES_FAILED,
                            WifiP2pManager.BUSY);
                    break;
                case WifiP2pManager.ADD_SERVICE_REQUEST:
                    replyToMessage(message, WifiP2pManager.ADD_SERVICE_REQUEST_FAILED,
                            WifiP2pManager.BUSY);
                    break;
                case WifiP2pManager.REMOVE_SERVICE_REQUEST:
                    replyToMessage(message,
                            WifiP2pManager.REMOVE_SERVICE_REQUEST_FAILED,
                            WifiP2pManager.BUSY);
                    break;
                case WifiP2pManager.CLEAR_SERVICE_REQUESTS:
                    replyToMessage(message,
                            WifiP2pManager.CLEAR_SERVICE_REQUESTS_FAILED,
                            WifiP2pManager.BUSY);
                    break;
                case WifiP2pManager.SET_DEVICE_NAME:
                    replyToMessage(message, WifiP2pManager.SET_DEVICE_NAME_FAILED,
                            WifiP2pManager.BUSY);
                    break;
                case WifiP2pManager.DELETE_PERSISTENT_GROUP:
                    replyToMessage(message, WifiP2pManager.DELETE_PERSISTENT_GROUP,
                            WifiP2pManager.BUSY);
                    break;
                case WifiP2pManager.SET_WFD_INFO:
                    replyToMessage(message, WifiP2pManager.SET_WFD_INFO_FAILED,
                            WifiP2pManager.BUSY);
                    break;
                case WifiP2pManager.REQUEST_PEERS:
                    replyToMessage(message, WifiP2pManager.RESPONSE_PEERS,
                            new WifiP2pDeviceList(mPeers));
                    break;
                case WifiP2pManager.REQUEST_CONNECTION_INFO:
                    replyToMessage(message, WifiP2pManager.RESPONSE_CONNECTION_INFO,
                            new WifiP2pInfo(mWifiP2pInfo));
                    break;
                case WifiP2pManager.REQUEST_GROUP_INFO:
                    ///M: ALPS00787135: to avoid caller refer to null group reference @{
                    WifiP2pGroup returnGroup;
                    if (null != mGroup) {
                        returnGroup = new WifiP2pGroup(mGroup);
                    } else {
                        WifiP2pDevice deviceGO = new WifiP2pDevice();
                        returnGroup = new WifiP2pGroup();
                        returnGroup.setNetworkName("wifi p2p");
                        returnGroup.setIsGroupOwner(false);
                        returnGroup.setOwner(deviceGO);
                        returnGroup.setPassphrase("00000000");
                        returnGroup.setInterface("p2p0");
                        returnGroup.setNetworkId(-1);
                        loge("group removed but caller request group info: \n" + returnGroup);
                    }
                    replyToMessage(message, WifiP2pManager.RESPONSE_GROUP_INFO,
                            //mGroup != null ? new WifiP2pGroup(mGroup) : null);
                            returnGroup);
                    ///@}
                    break;
                case WifiP2pManager.REQUEST_PERSISTENT_GROUP_INFO:
                    replyToMessage(message, WifiP2pManager.RESPONSE_PERSISTENT_GROUP_INFO,
                            new WifiP2pGroupList(mGroups, null));
                    break;
                case WifiP2pManager.START_WPS:
                    replyToMessage(message, WifiP2pManager.START_WPS_FAILED,
                        WifiP2pManager.BUSY);
                    break;
                    // Ignore
                case WifiMonitor.P2P_INVITATION_RESULT_EVENT:
                case WifiMonitor.SCAN_RESULTS_EVENT:
                case WifiMonitor.SUP_CONNECTION_EVENT:
                case WifiMonitor.SUP_DISCONNECTION_EVENT:
                case WifiMonitor.NETWORK_CONNECTION_EVENT:
                case WifiMonitor.NETWORK_DISCONNECTION_EVENT:
                case WifiMonitor.SUPPLICANT_STATE_CHANGE_EVENT:
                case WifiMonitor.AUTHENTICATION_FAILURE_EVENT:
                case WifiMonitor.WPS_SUCCESS_EVENT:
                case WifiMonitor.WPS_FAIL_EVENT:
                case WifiMonitor.WPS_OVERLAP_EVENT:
                case WifiMonitor.WPS_TIMEOUT_EVENT:
                case WifiMonitor.P2P_GROUP_REMOVED_EVENT:
                case WifiMonitor.P2P_DEVICE_FOUND_EVENT:
                case WifiMonitor.P2P_DEVICE_LOST_EVENT:
                case WifiMonitor.P2P_FIND_STOPPED_EVENT:
                case WifiMonitor.P2P_SERV_DISC_RESP_EVENT:
                case PEER_CONNECTION_USER_ACCEPT:
                case PEER_CONNECTION_USER_REJECT:
                case DISCONNECT_WIFI_RESPONSE:
                case DROP_WIFI_USER_ACCEPT:
                case DROP_WIFI_USER_REJECT:
                case GROUP_CREATING_TIMED_OUT:
                case DISABLE_P2P_TIMED_OUT:
                case DhcpStateMachine.CMD_PRE_DHCP_ACTION:
                case DhcpStateMachine.CMD_POST_DHCP_ACTION:
                case DhcpStateMachine.CMD_ON_QUIT:
                case WifiMonitor.P2P_PROV_DISC_FAILURE_EVENT:
                case SET_MIRACAST_MODE:
                case WifiP2pManager.START_LISTEN:
                case WifiP2pManager.STOP_LISTEN:
                case WifiP2pManager.SET_CHANNEL:
                case SET_COUNTRY_CODE:
                    break;
                case WifiStateMachine.CMD_ENABLE_P2P:
                    // Enable is lazy and has no response
                    break;
                case WifiStateMachine.CMD_DISABLE_P2P_REQ:
                    // If we end up handling in default, p2p is not enabled
                    mWifiChannel.sendMessage(WifiStateMachine.CMD_DISABLE_P2P_RSP);
                    break;
                    /* unexpected group created, remove */
                case WifiMonitor.P2P_GROUP_STARTED_EVENT:
                    mGroup = (WifiP2pGroup) message.obj;
                    loge("Unexpected group creation, remove " + mGroup);
                    mWifiNative.p2pGroupRemove(mGroup.getInterface());
                    break;
                // A group formation failure is always followed by
                // a group removed event. Flushing things at group formation
                // failure causes supplicant issues. Ignore right now.
                case WifiMonitor.P2P_GROUP_FORMATION_FAILURE_EVENT:
                    break;
                default:
                    loge("Unhandled message " + message);
                    return NOT_HANDLED;
            }
            return HANDLED;
        }
    }

    class P2pNotSupportedState extends State {
        @Override
        public boolean processMessage(Message message) {
            switch (message.what) {
               case WifiP2pManager.DISCOVER_PEERS:
                    replyToMessage(message, WifiP2pManager.DISCOVER_PEERS_FAILED,
                            WifiP2pManager.P2P_UNSUPPORTED);
                    break;
                case WifiP2pManager.STOP_DISCOVERY:
                    replyToMessage(message, WifiP2pManager.STOP_DISCOVERY_FAILED,
                            WifiP2pManager.P2P_UNSUPPORTED);
                    break;
                case WifiP2pManager.DISCOVER_SERVICES:
                    replyToMessage(message, WifiP2pManager.DISCOVER_SERVICES_FAILED,
                            WifiP2pManager.P2P_UNSUPPORTED);
                    break;
                case WifiP2pManager.CONNECT:
                    replyToMessage(message, WifiP2pManager.CONNECT_FAILED,
                            WifiP2pManager.P2P_UNSUPPORTED);
                    break;
                case WifiP2pManager.CANCEL_CONNECT:
                    replyToMessage(message, WifiP2pManager.CANCEL_CONNECT_FAILED,
                            WifiP2pManager.P2P_UNSUPPORTED);
                    break;
               case WifiP2pManager.CREATE_GROUP:
                    replyToMessage(message, WifiP2pManager.CREATE_GROUP_FAILED,
                            WifiP2pManager.P2P_UNSUPPORTED);
                    break;
                case WifiP2pManager.REMOVE_GROUP:
                    replyToMessage(message, WifiP2pManager.REMOVE_GROUP_FAILED,
                            WifiP2pManager.P2P_UNSUPPORTED);
                    break;
                case WifiP2pManager.ADD_LOCAL_SERVICE:
                    replyToMessage(message, WifiP2pManager.ADD_LOCAL_SERVICE_FAILED,
                            WifiP2pManager.P2P_UNSUPPORTED);
                    break;
                case WifiP2pManager.REMOVE_LOCAL_SERVICE:
                    replyToMessage(message, WifiP2pManager.REMOVE_LOCAL_SERVICE_FAILED,
                            WifiP2pManager.P2P_UNSUPPORTED);
                    break;
                case WifiP2pManager.CLEAR_LOCAL_SERVICES:
                    replyToMessage(message, WifiP2pManager.CLEAR_LOCAL_SERVICES_FAILED,
                            WifiP2pManager.P2P_UNSUPPORTED);
                    break;
                case WifiP2pManager.ADD_SERVICE_REQUEST:
                    replyToMessage(message, WifiP2pManager.ADD_SERVICE_REQUEST_FAILED,
                            WifiP2pManager.P2P_UNSUPPORTED);
                    break;
                case WifiP2pManager.REMOVE_SERVICE_REQUEST:
                    replyToMessage(message,
                            WifiP2pManager.REMOVE_SERVICE_REQUEST_FAILED,
                            WifiP2pManager.P2P_UNSUPPORTED);
                    break;
                case WifiP2pManager.CLEAR_SERVICE_REQUESTS:
                    replyToMessage(message,
                            WifiP2pManager.CLEAR_SERVICE_REQUESTS_FAILED,
                            WifiP2pManager.P2P_UNSUPPORTED);
                    break;
                case WifiP2pManager.SET_DEVICE_NAME:
                    replyToMessage(message, WifiP2pManager.SET_DEVICE_NAME_FAILED,
                            WifiP2pManager.P2P_UNSUPPORTED);
                    break;
                case WifiP2pManager.DELETE_PERSISTENT_GROUP:
                    replyToMessage(message, WifiP2pManager.DELETE_PERSISTENT_GROUP,
                            WifiP2pManager.P2P_UNSUPPORTED);
                    break;
                case WifiP2pManager.SET_WFD_INFO:
                    replyToMessage(message, WifiP2pManager.SET_WFD_INFO_FAILED,
                            WifiP2pManager.P2P_UNSUPPORTED);
                    break;
                case WifiP2pManager.START_WPS:
                    replyToMessage(message, WifiP2pManager.START_WPS_FAILED,
                            WifiP2pManager.P2P_UNSUPPORTED);
                    break;
                case WifiP2pManager.START_LISTEN:
                    replyToMessage(message, WifiP2pManager.START_LISTEN_FAILED,
                            WifiP2pManager.P2P_UNSUPPORTED);
                    break;
                case WifiP2pManager.STOP_LISTEN:
                    replyToMessage(message, WifiP2pManager.STOP_LISTEN_FAILED,
                            WifiP2pManager.P2P_UNSUPPORTED);
                    break;

                default:
                    return NOT_HANDLED;
            }
            return HANDLED;
        }
    }

    class P2pDisablingState extends State {
        @Override
        public void enter() {
            if (DBG) logd(getName());
            sendMessageDelayed(obtainMessage(DISABLE_P2P_TIMED_OUT,
                    ++mDisableP2pTimeoutIndex, 0), DISABLE_P2P_WAIT_TIME_MS);
        }

        @Override
        public boolean processMessage(Message message) {
            if (DBG) logd(getName() + message.toString());
            switch (message.what) {
                case WifiMonitor.SUP_DISCONNECTION_EVENT:
                    if (DBG) logd("p2p socket connection lost");
                    transitionTo(mP2pDisabledState);
                    break;
                case WifiStateMachine.CMD_ENABLE_P2P:
                case WifiStateMachine.CMD_DISABLE_P2P_REQ:
                case WifiP2pManager.START_FAST_CONNECT_AS_GO:  ///Add by MTK
                case WifiP2pManager.START_FAST_CONNECT_AS_GC:  ///Add by MTK
                    logd("P2pDisablingState, case WifiP2pManager.START_FAST_CONNECT_AS_GO or WifiP2pManager.START_FAST_CONNECT_AS_GC");
                    deferMessage(message);
                    break;
                case DISABLE_P2P_TIMED_OUT:
                    if (mGroupCreatingTimeoutIndex == message.arg1) {
                        loge("P2p disable timed out");
                        transitionTo(mP2pDisabledState);
                    }
                    break;
                default:
                    return NOT_HANDLED;
            }
            return HANDLED;
        }

        @Override
        public void exit() {
            mWifiChannel.sendMessage(WifiStateMachine.CMD_DISABLE_P2P_RSP);
        }
    }

    class P2pDisabledState extends State {
       @Override
        public void enter() {
            if (DBG) logd(getName());
            ///M: ALPS01289841: device will do fast GC/GO immediately after wifi enabled
            ///    when wifi disable->enable without group form or form failed @{
            mGoFastConnectEnaled = false;
            mGcFastConnectEnaled = false;
            ///@}
        }

        @Override
        public boolean processMessage(Message message) {
            if (DBG) logd(getName() + message.toString());
            switch (message.what) {
                case WifiStateMachine.CMD_ENABLE_P2P:
                    try {
                        mNwService.setInterfaceUp(mInterface);
                    } catch (RemoteException re) {
                        loge("Unable to change interface settings: " + re);
                    } catch (IllegalStateException ie) {
                        loge("Unable to change interface settings: " + ie);
                    }
                    mWifiMonitor.startMonitoring();
                    transitionTo(mP2pEnablingState);
                    break;
                ///M:@{
                case WifiP2pManager.START_FAST_CONNECT_AS_GO:
                    logd("P2pDisabledState, case WifiP2pManager.START_FAST_CONNECT_AS_GO");
                    setWifiOn_WifiAPOff();
                    mGoFastConnectEnaled = true;
                    break;
                case WifiP2pManager.START_FAST_CONNECT_AS_GC:
                    logd("P2pDisabledState, case WifiP2pManager.START_FAST_CONNECT_AS_GC");
                    setWifiOn_WifiAPOff();
                    mFastConnectInfo = (WifiP2pFastConnectInfo)message.obj;
                    logd("Fast Connect As GC mFastConnectInfo = " + mFastConnectInfo);
                    mGcFastConnectEnaled = true;
                    break;
                ///@}
                default:
                    return NOT_HANDLED;
            }
            return HANDLED;
        }
    }

    class P2pEnablingState extends State {
        @Override
        public void enter() {
            if (DBG) logd(getName());
        }

        @Override
        public boolean processMessage(Message message) {
            if (DBG) logd(getName() + message.toString());
            switch (message.what) {
                case WifiMonitor.SUP_CONNECTION_EVENT:
                    if (DBG) logd("P2p socket connection successful");
                    logd("startDriver");
                    mWifiNative.startDriver();  ///Add by MTK
                    transitionTo(mInactiveState);
                    break;
                case WifiMonitor.SUP_DISCONNECTION_EVENT:
                    loge("P2p socket connection failed");
                    transitionTo(mP2pDisabledState);
                    break;
                case WifiStateMachine.CMD_ENABLE_P2P:
                case WifiStateMachine.CMD_DISABLE_P2P_REQ:
                case WifiP2pManager.START_FAST_CONNECT_AS_GO:  ///Add by MTK
                case WifiP2pManager.START_FAST_CONNECT_AS_GC:  ///Add by MTK
                    deferMessage(message);
                    break;
                default:
                    return NOT_HANDLED;
            }
            return HANDLED;
        }
    }

    class P2pEnabledState extends State {
        @Override
        public void enter() {
            if (DBG) logd(getName());
            sendP2pStateChangedBroadcast(true);
            mNetworkInfo.setIsAvailable(true);
            sendP2pConnectionChangedBroadcast();
            initializeP2pSettings();
            ///M: NFC hand over wifi direct @{
            if (mGoFastConnectEnaled) setFastConnectInfo(WifiP2pManager.FAST_CONNECT_AS_GO);
            if (mGcFastConnectEnaled) setFastConnectInfo(WifiP2pManager.FAST_CONNECT_AS_GC);
            ///@}
        }

        @Override
        public boolean processMessage(Message message) {
            if (DBG) logd(getName() + message.toString());
            switch (message.what) {
                case WifiMonitor.SUP_DISCONNECTION_EVENT:
                    loge("Unexpected loss of p2p socket connection");
                    transitionTo(mP2pDisabledState);
                    break;
                case WifiStateMachine.CMD_ENABLE_P2P:
                    //Nothing to do
                    break;
                case WifiStateMachine.CMD_DISABLE_P2P_REQ:
                    logd("P2pEnabledState, case WifiStateMachine.CMD_DISABLE_P2P_REQ, call mPeers.clear");
                    if (mPeers.clear()) {
                        sendPeersChangedBroadcast();
                    }
                    if (mGroups.clear()) sendP2pPersistentGroupsChangedBroadcast();
                    logd("stopMonitoring");
                    mWifiMonitor.stopMonitoring();
                    logd("stopDriver");
                    mWifiNative.stopDriver();  ///Add by MTK
                    transitionTo(mP2pDisablingState);
                    break;
                case WifiP2pManager.SET_DEVICE_NAME:
                {
                    WifiP2pDevice d = (WifiP2pDevice) message.obj;
                    if (d != null && setAndPersistDeviceName(d.deviceName)) {
                        if (DBG) logd("set device name " + d.deviceName);
                        replyToMessage(message, WifiP2pManager.SET_DEVICE_NAME_SUCCEEDED);
                    } else {
                        replyToMessage(message, WifiP2pManager.SET_DEVICE_NAME_FAILED,
                                WifiP2pManager.ERROR);
                    }
                    break;
                }
                case WifiP2pManager.SET_WFD_INFO:
                {
                    WifiP2pWfdInfo d = (WifiP2pWfdInfo) message.obj;
                    if (d != null && setWfdInfo(d)) {
                        replyToMessage(message, WifiP2pManager.SET_WFD_INFO_SUCCEEDED);
                    } else {
                        replyToMessage(message, WifiP2pManager.SET_WFD_INFO_FAILED,
                                WifiP2pManager.ERROR);
                    }
                    break;
                }
                case BLOCK_DISCOVERY:
                    boolean blocked = (message.arg1 == ENABLED ? true : false);
                    logd("P2pEnabledState, case BLOCK_DISCOVERY, blocked = " + blocked + " mDiscoveryBlocked = " + mDiscoveryBlocked);
                    if (mDiscoveryBlocked == blocked) break;
                    mDiscoveryBlocked = blocked;
                    logd("P2pEnabledState, case BLOCK_DISCOVERY, mDiscoveryBlocked = " + mDiscoveryBlocked);
                    if (blocked && mDiscoveryStarted) {
                        mWifiNative.p2pStopFind();
                        mDiscoveryPostponed = true;
                    }
                    logd("P2pEnabledState, case BLOCK_DISCOVERY, mDiscoveryPostponed = " + mDiscoveryPostponed);
                    if (!blocked && mDiscoveryPostponed) {
                        mDiscoveryPostponed = false;
                        mWifiNative.p2pFind(DISCOVER_TIMEOUT_S);
                    }
                    if (blocked) {
                        try {
                            StateMachine m = (StateMachine)message.obj;
                            m.sendMessage(message.arg2);
                        } catch (Exception e) {
                            loge("unable to send BLOCK_DISCOVERY response: " + e);
                        }
                    }
                    break;
                case WifiP2pManager.DISCOVER_PEERS:
                    if (mDiscoveryBlocked) {
                        replyToMessage(message, WifiP2pManager.DISCOVER_PEERS_FAILED,
                                WifiP2pManager.BUSY);
                        break;
                    }
                    // do not send service discovery request while normal find operation.
                    clearSupplicantServiceRequest();
                    if (mWifiNative.p2pFind(DISCOVER_TIMEOUT_S)) {
                        replyToMessage(message, WifiP2pManager.DISCOVER_PEERS_SUCCEEDED);
                        sendP2pDiscoveryChangedBroadcast(true);
                    } else {
                        replyToMessage(message, WifiP2pManager.DISCOVER_PEERS_FAILED,
                                WifiP2pManager.ERROR);
                    }
                    break;
                case WifiMonitor.P2P_FIND_STOPPED_EVENT:
                    sendP2pDiscoveryChangedBroadcast(false);
                    break;
                case WifiP2pManager.STOP_DISCOVERY:
                    if (mWifiNative.p2pStopFind()) {
                        replyToMessage(message, WifiP2pManager.STOP_DISCOVERY_SUCCEEDED);
                    } else {
                        replyToMessage(message, WifiP2pManager.STOP_DISCOVERY_FAILED,
                                WifiP2pManager.ERROR);
                    }
                    break;
                case WifiP2pManager.DISCOVER_SERVICES:
                    if (mDiscoveryBlocked) {
                        replyToMessage(message, WifiP2pManager.DISCOVER_SERVICES_FAILED,
                                WifiP2pManager.BUSY);
                        break;
                    }
                    if (DBG) logd(getName() + " discover services");
                    if (!updateSupplicantServiceRequest()) {
                        replyToMessage(message, WifiP2pManager.DISCOVER_SERVICES_FAILED,
                                WifiP2pManager.NO_SERVICE_REQUESTS);
                        break;
                    }
                    if (mWifiNative.p2pFind(DISCOVER_TIMEOUT_S)) {
                        replyToMessage(message, WifiP2pManager.DISCOVER_SERVICES_SUCCEEDED);
                    } else {
                        replyToMessage(message, WifiP2pManager.DISCOVER_SERVICES_FAILED,
                                WifiP2pManager.ERROR);
                    }
                    break;
                case WifiMonitor.P2P_DEVICE_FOUND_EVENT:
                    WifiP2pDevice device = (WifiP2pDevice) message.obj;
                    if (mThisDevice.deviceAddress.equals(device.deviceAddress)) break;
                    mPeers.updateSupplicantDetails(device);
                    sendPeersChangedBroadcast();
                    ///M: wifi direct fast connect, found peer before timing out @{
                    logd("P2P_DEVICE_FOUND_EVENT, Fast connect GC enableState:" + mGcFastConnectEnaled
                            + " findTimeout:" + mFoundGoTimeOut + " FoundGo:" + mFoundGo);

                    if(mGcFastConnectEnaled && !mFoundGoTimeOut && !mFoundGo 
                            &&  mFastConnectInfo.deviceAddress.equals(device.deviceAddress)) {
                        sendMessage(WifiP2pManager.FAST_CONNECT_AS_GC);
                        logd("Found Fast connect peer:" + mFastConnectInfo.deviceAddress);
                        mFoundGo = true;
                    }
                    ///@}
                    break;
                case WifiMonitor.P2P_DEVICE_LOST_EVENT:
                    device = (WifiP2pDevice) message.obj;
                    // Gets current details for the one removed
                    logd("P2pEnabledState, case WifiMonitor.P2P_DEVICE_LOST_EVENT, call mPeers.remove - device.deviceAddress = " + device.deviceAddress);
                    device = mPeers.remove(device.deviceAddress);
                    if (device != null) {
                        sendPeersChangedBroadcast();
                    }
                    break;
                case WifiP2pManager.ADD_LOCAL_SERVICE:
                    if (DBG) logd(getName() + " add service");
                    WifiP2pServiceInfo servInfo = (WifiP2pServiceInfo)message.obj;
                    if (addLocalService(message.replyTo, servInfo)) {
                        replyToMessage(message, WifiP2pManager.ADD_LOCAL_SERVICE_SUCCEEDED);
                    } else {
                        replyToMessage(message, WifiP2pManager.ADD_LOCAL_SERVICE_FAILED);
                    }
                    break;
                case WifiP2pManager.REMOVE_LOCAL_SERVICE:
                    if (DBG) logd(getName() + " remove service");
                    servInfo = (WifiP2pServiceInfo)message.obj;
                    removeLocalService(message.replyTo, servInfo);
                    replyToMessage(message, WifiP2pManager.REMOVE_LOCAL_SERVICE_SUCCEEDED);
                    break;
                case WifiP2pManager.CLEAR_LOCAL_SERVICES:
                    if (DBG) logd(getName() + " clear service");
                    clearLocalServices(message.replyTo);
                    replyToMessage(message, WifiP2pManager.CLEAR_LOCAL_SERVICES_SUCCEEDED);
                    break;
                case WifiP2pManager.ADD_SERVICE_REQUEST:
                    if (DBG) logd(getName() + " add service request");
                    if (!addServiceRequest(message.replyTo, (WifiP2pServiceRequest)message.obj)) {
                        replyToMessage(message, WifiP2pManager.ADD_SERVICE_REQUEST_FAILED);
                        break;
                    }
                    replyToMessage(message, WifiP2pManager.ADD_SERVICE_REQUEST_SUCCEEDED);
                    break;
                case WifiP2pManager.REMOVE_SERVICE_REQUEST:
                    if (DBG) logd(getName() + " remove service request");
                    removeServiceRequest(message.replyTo, (WifiP2pServiceRequest)message.obj);
                    replyToMessage(message, WifiP2pManager.REMOVE_SERVICE_REQUEST_SUCCEEDED);
                    break;
                case WifiP2pManager.CLEAR_SERVICE_REQUESTS:
                    if (DBG) logd(getName() + " clear service request");
                    clearServiceRequests(message.replyTo);
                    replyToMessage(message, WifiP2pManager.CLEAR_SERVICE_REQUESTS_SUCCEEDED);
                    break;
                case WifiMonitor.P2P_SERV_DISC_RESP_EVENT:
                    if (DBG) logd(getName() + " receive service response");
                    List<WifiP2pServiceResponse> sdRespList =
                        (List<WifiP2pServiceResponse>) message.obj;
                    for (WifiP2pServiceResponse resp : sdRespList) {
                        WifiP2pDevice dev =
                            mPeers.get(resp.getSrcDevice().deviceAddress);
                        resp.setSrcDevice(dev);
                        sendServiceResponse(resp);
                    }
                    break;
                case WifiP2pManager.DELETE_PERSISTENT_GROUP:
                   if (DBG) logd(getName() + " delete persistent group");
                   mGroups.remove(message.arg1);
                   replyToMessage(message, WifiP2pManager.DELETE_PERSISTENT_GROUP_SUCCEEDED);
                   break;
                case SET_MIRACAST_MODE:
                    mWifiNative.setMiracastMode(message.arg1);
                    break;
                case WifiP2pManager.START_LISTEN:
                    if (DBG) logd(getName() + " start listen mode");
                    mWifiNative.p2pFlush();
                    if (mWifiNative.p2pExtListen(true, 500, 500)) {
                        replyToMessage(message, WifiP2pManager.START_LISTEN_SUCCEEDED);
                    } else {
                        replyToMessage(message, WifiP2pManager.START_LISTEN_FAILED);
                    }
                    break;
                case WifiP2pManager.STOP_LISTEN:
                    if (DBG) logd(getName() + " stop listen mode");
                    if (mWifiNative.p2pExtListen(false, 0, 0)) {
                        replyToMessage(message, WifiP2pManager.STOP_LISTEN_SUCCEEDED);
                    } else {
                        replyToMessage(message, WifiP2pManager.STOP_LISTEN_FAILED);
                    }
                    mWifiNative.p2pFlush();
                    break;
                case WifiP2pManager.SET_CHANNEL:
                    Bundle p2pChannels = (Bundle) message.obj;
                    int lc = p2pChannels.getInt("lc", 0);
                    int oc = p2pChannels.getInt("oc", 0);
                    if (DBG) logd(getName() + " set listen and operating channel");
                    if (mWifiNative.p2pSetChannel(lc, oc)) {
                        replyToMessage(message, WifiP2pManager.SET_CHANNEL_SUCCEEDED);
                    } else {
                        replyToMessage(message, WifiP2pManager.SET_CHANNEL_FAILED);
                    }
                    break;
                case SET_COUNTRY_CODE:
                    String countryCode = (String) message.obj;
                    countryCode = countryCode.toUpperCase(Locale.ROOT);
                    if (mLastSetCountryCode == null ||
                            countryCode.equals(mLastSetCountryCode) == false) {
                        if (mWifiNative.setCountryCode(countryCode)) {
                            mLastSetCountryCode = countryCode;
                        }
                    }
                    break;
                ///M:@{
               case WifiP2pManager.FAST_DISCOVER_PEERS:
                    logd("FAST_DISCOVER_PEERS, Fast connect GC enableState:" + mGcFastConnectEnaled 
                           + " findTimeout:" + mFoundGoTimeOut + " FoundGo:" + mFoundGo);

                    if (mGcFastConnectEnaled && !mFoundGoTimeOut && !mFoundGo) {
                        sendMessageDelayed(WifiP2pManager.FAST_DISCOVER_PEERS, FAST_SCAN_INTERVAL_TIME_MS);
                        clearSupplicantServiceRequest();
                        if (mWifiNative.p2pFind(FAST_DISCOVER_TIMEOUT_S)) {
                            logd(getName() + " Fast connect scan OK");
                            sendP2pDiscoveryChangedBroadcast(true);
                        }
                    }
                    break;
               case WifiP2pManager.REQUEST_LINK_INFO:
                   WifiP2pLinkInfo info = (WifiP2pLinkInfo) message.obj;
                   info.linkInfo = mWifiNative.p2pLinkStatics(info.interfaceAddress);
                   logd("Wifi P2p link info is " + info.toString());
                   replyToMessage(message, WifiP2pManager.RESPONSE_LINK_INFO, new WifiP2pLinkInfo(info));
                   break;
               case WifiP2pManager.SET_WFD_SESSION_MODE:
                   int state = message.arg1;
                   mWifiNative.wfdSessionMode(state);
                   mWifiNative.wfdUpdate();
                   replyToMessage(message, WifiP2pManager.SET_WFD_SESSION_MODE_SUCCEEDED);
                   break;
               case WifiP2pManager.SET_AUTO_CHANNEL_SELECT:
                   int enable = message.arg1;
                   mWifiNative.p2pAutoChannel(enable);
                   replyToMessage(message, WifiP2pManager.SET_AUTO_CHANNEL_SELECT_SUCCEEDED);
                   break;                   
                ///@}
                default:
                   return NOT_HANDLED;
            }
            return HANDLED;
        }

        @Override
        public void exit() {
            mDiscoveryStarted = false;  ///Add by MTK
            sendP2pStateChangedBroadcast(false);
            mNetworkInfo.setIsAvailable(false);

            mLastSetCountryCode = null;
        }
    }

    class InactiveState extends State {
        @Override
        public void enter() {
            if (DBG) logd(getName());
            ///M: it will cause 2nd connect of channel conflict failed, Google issue @{
            //mSavedPeerConfig.invalidate();
            ///@}
        }

        @Override
        public boolean processMessage(Message message) {
            if (DBG) logd(getName() + message.toString());
            switch (message.what) {
                case WifiP2pManager.CONNECT:
                    if (DBG) logd(getName() + " sending connect");
                    WifiP2pConfig config = (WifiP2pConfig) message.obj;
                    if (isConfigInvalid(config)) {
                        loge("Dropping connect requeset " + config);
                        replyToMessage(message, WifiP2pManager.CONNECT_FAILED);
                        break;
                    }

                    mAutonomousGroup = false;
                    ///M: it will cause provision failed @{
                    //mWifiNative.p2pStopFind();
                    ///@}
                    logd("InactiveState, case WifiP2pManager.CONNECT, call reinvokePersistentGroup - config.deviceAddress = " + config.deviceAddress);
                    if (reinvokePersistentGroup(config)) {
                        ///M: to fix provision failed @{
                        //mWifiNative.p2pStopFind();
                        ///@}
                        transitionTo(mGroupNegotiationState);
                    } else {
                        ///M: to fix provision failed @{
                        //mWifiNative.p2pFind(DISCOVER_TIMEOUT_S);
                        ///@}
                        mWifiNative.p2pStopFind();
                        logd("InactiveState, case WifiP2pManager.CONNECT, call reinvokePersistentGroup failed, transfer to ProvisionDiscoveryState");
                        transitionTo(mProvisionDiscoveryState);
                    }
                    logd("InactiveState, case WifiP2pManager.CONNECT, set mSavedPeerConfig - " + config.deviceAddress);
                    mSavedPeerConfig = config;
                    mPeers.updateStatus(mSavedPeerConfig.deviceAddress, WifiP2pDevice.INVITED);
                    sendPeersChangedBroadcast();
                    replyToMessage(message, WifiP2pManager.CONNECT_SUCCEEDED);
                    break;
                case WifiP2pManager.STOP_DISCOVERY:
                    if (mWifiNative.p2pStopFind()) {
                        // When discovery stops in inactive state, flush to clear
                        // state peer data
                        mWifiNative.p2pFlush();
                        mServiceDiscReqId = null;
                        replyToMessage(message, WifiP2pManager.STOP_DISCOVERY_SUCCEEDED);
                    } else {
                        replyToMessage(message, WifiP2pManager.STOP_DISCOVERY_FAILED,
                                WifiP2pManager.ERROR);
                    }
                    break;
                case WifiMonitor.P2P_GO_NEGOTIATION_REQUEST_EVENT:
                    config = (WifiP2pConfig) message.obj;
                    if (isConfigInvalid(config)) {
                        loge("Dropping GO neg request " + config);
                        break;
                    }
                    logd("InactiveState, case WifiMonitor.P2P_GO_NEGOTIATION_REQUEST_EVENT, set mSavedPeerConfig - " + config.deviceAddress);
                    mSavedPeerConfig = config;
                    mAutonomousGroup = false;
                    mJoinExistingGroup = false;
                    transitionTo(mUserAuthorizingNegotiationRequestState);
                    break;
                case WifiMonitor.P2P_INVITATION_RECEIVED_EVENT:
                    WifiP2pGroup group = (WifiP2pGroup) message.obj;
                    WifiP2pDevice owner = group.getOwner();

                    if (owner == null) {
                        loge("Ignored invitation from null owner");
                        break;
                    }
                    
                    logd("InactiveState, case WifiMonitor.P2P_INVITATION_RECEIVED_EVENT, owner.deviceAddress = " + owner.deviceAddress);

                    config = new WifiP2pConfig();
                    config.deviceAddress = group.getOwner().deviceAddress;
                    
                    if (isConfigInvalid(config)) {
                        loge("Dropping invitation request " + config);
                        break;
                    }
                    
                    logd("InactiveState, case WifiMonitor.P2P_INVITATION_RECEIVED_EVENT, set mSavedPeerConfig - " + config.deviceAddress);
                    mSavedPeerConfig = config;

                    //Check if we have the owner in peer list and use appropriate
                    //wps method. Default is to use PBC.
                    if ((owner = mPeers.get(owner.deviceAddress)) != null) {
                        if (owner.wpsPbcSupported()) {
                            mSavedPeerConfig.wps.setup = WpsInfo.PBC;
                        } else if (owner.wpsKeypadSupported()) {
                            mSavedPeerConfig.wps.setup = WpsInfo.KEYPAD;
                        } else if (owner.wpsDisplaySupported()) {
                            mSavedPeerConfig.wps.setup = WpsInfo.DISPLAY;
                        }
                    }

                    mAutonomousGroup = false;
                    mJoinExistingGroup = true;
                    transitionTo(mUserAuthorizingInviteRequestState);
                    break;
                case WifiMonitor.P2P_PROV_DISC_PBC_REQ_EVENT:
                case WifiMonitor.P2P_PROV_DISC_ENTER_PIN_EVENT:
                case WifiMonitor.P2P_PROV_DISC_SHOW_PIN_EVENT:
                    //We let the supplicant handle the provision discovery response
                    //and wait instead for the GO_NEGOTIATION_REQUEST_EVENT.
                    //Handling provision discovery and issuing a p2p_connect before
                    //group negotiation comes through causes issues
                    break;
                case WifiP2pManager.CREATE_GROUP:
                    mAutonomousGroup = true;
                    int netId = message.arg1;
                    boolean ret = false;
                    if (netId == WifiP2pGroup.PERSISTENT_NET_ID) {
                        // check if the go persistent group is present.
                        netId = mGroups.getNetworkId(mThisDevice.deviceAddress);
                        if (netId != -1) {
                            ret = mWifiNative.p2pGroupAdd(netId);
                        } else {
                            ret = mWifiNative.p2pGroupAdd(true);
                        }
                    } else {
                        ret = mWifiNative.p2pGroupAdd(false);
                    }

                    if (ret) {
                        replyToMessage(message, WifiP2pManager.CREATE_GROUP_SUCCEEDED);
                        transitionTo(mGroupNegotiationState);
                    } else {
                        replyToMessage(message, WifiP2pManager.CREATE_GROUP_FAILED,
                                WifiP2pManager.ERROR);
                        // remain at this state.
                    }
                    break;
                case WifiMonitor.P2P_GROUP_STARTED_EVENT:
                    mGroup = (WifiP2pGroup) message.obj;
                    if (DBG) logd(getName() + " group started");

                    // We hit this scenario when a persistent group is reinvoked
                    if (mGroup.getNetworkId() == WifiP2pGroup.PERSISTENT_NET_ID) {
                        mAutonomousGroup = false;
                        deferMessage(message);
                        transitionTo(mGroupNegotiationState);
                    } else {
                        loge("Unexpected group creation, remove " + mGroup);
                        mWifiNative.p2pGroupRemove(mGroup.getInterface());
                    }
                    break;
                case WifiP2pManager.START_LISTEN:
                    if (DBG) logd(getName() + " start listen mode");
                    mWifiNative.p2pFlush();
                    if (mWifiNative.p2pExtListen(true, 500, 500)) {
                        replyToMessage(message, WifiP2pManager.START_LISTEN_SUCCEEDED);
                    } else {
                        replyToMessage(message, WifiP2pManager.START_LISTEN_FAILED);
                    }
                    break;
                case WifiP2pManager.STOP_LISTEN:
                    if (DBG) logd(getName() + " stop listen mode");
                    if (mWifiNative.p2pExtListen(false, 0, 0)) {
                        replyToMessage(message, WifiP2pManager.STOP_LISTEN_SUCCEEDED);
                    } else {
                        replyToMessage(message, WifiP2pManager.STOP_LISTEN_FAILED);
                    }
                    mWifiNative.p2pFlush();
                    break;
                case WifiP2pManager.SET_CHANNEL:
                    Bundle p2pChannels = (Bundle) message.obj;
                    int lc = p2pChannels.getInt("lc", 0);
                    int oc = p2pChannels.getInt("oc", 0);
                    if (DBG) logd(getName() + " set listen and operating channel");
                    if (mWifiNative.p2pSetChannel(lc, oc)) {
                        replyToMessage(message, WifiP2pManager.SET_CHANNEL_SUCCEEDED);
                    } else {
                        replyToMessage(message, WifiP2pManager.SET_CHANNEL_FAILED);
                    }
                    break;
                ///M:@{
                case WifiP2pManager.START_FAST_CONNECT_AS_GO:
                    logd("InactiveState, case WifiP2pManager.START_FAST_CONNECT_AS_GO");
                    setFastConnectInfo(WifiP2pManager.FAST_CONNECT_AS_GO);
                    break; 
                case WifiP2pManager.START_FAST_CONNECT_AS_GC:
                    logd("InactiveState, case WifiP2pManager.START_FAST_CONNECT_AS_GC");
                    mFastConnectInfo = (WifiP2pFastConnectInfo) message.obj;
                    mGcFastConnectEnaled = true;
                    setFastConnectInfo(WifiP2pManager.FAST_CONNECT_AS_GC);
                    /*M: ALPS01126713: to avoid doing FAST_CONNECT_FIND_GO_TIMED_OUT between START_FAST_CONNECT_AS_GO and FAST_CONNECT_AS_GC*/
                    mFastConnectFindGoTimeOutIndex = mFastConnectFindGoTimeOutIndex + 1;
                    break;
                case WifiP2pManager.FAST_CONNECT_AS_GO:
                    ///M: ALPS01000113: sync. Beam+ state @{
                    if (true == mBeamPlusStart) {
                        mWifiNative.p2pBeamPlusGO(1); //1: reserve start
                    }
                    ///@}
                    mAutonomousGroup = false;
                    mWifiNative.p2pGroupAdd(mFastConnectInfo.networkId);
                    transitionTo(mGroupNegotiationState);
                    break;
                case WifiP2pManager.FAST_CONNECT_AS_GC: 
                    //mFoundGo is true means FAST_CONNECT_AS_GC is from P2P_DEVICE_FOUND_EVENT
                    logd("FAST_CONNECT_AS_GC, mFoundGo=" + mFoundGo 
                            + " inMpeers=" + mPeers.containsPeer(mFastConnectInfo.deviceAddress));

                    /*M: ALPS00804938: mFoundGo is true but mPeers not contain fastConnect GO, then it needed scan again!*/
                    /*M: ALPS00918679: mFoundGo is false but mPeers contain fastConnect GO, then it still needed scan again!*/
                    if ( !mFoundGo || !(mPeers.containsPeer(mFastConnectInfo.deviceAddress)) ) {
                        mFoundGoTimeOut = false;
                        mFoundGo = false;    ///M: ALPS00931786: need re-trigger WifiP2pManager.FAST_CONNECT_AS_GC when scan is done 
                        sendMessageDelayed(WifiP2pManager.FAST_DISCOVER_PEERS, FAST_SCAN_INTERVAL_TIME_MS);
                        sendMessageDelayed(obtainMessage(FAST_CONNECT_FIND_GO_TIMED_OUT,
                                ++ mFastConnectFindGoTimeOutIndex, 0), FAST_CONNECT_FIND_GO_WAIT_TIME_MS);
                        logd("Send FAST_CONNECT_FIND_GO_TIMED_OUT message");
                        break;
                    }
                    int gc2 = mWifiNative.getGroupCapability(mFastConnectInfo.deviceAddress);
                    mPeers.updateGroupCapability(mFastConnectInfo.deviceAddress, gc2);
                    mSavedPeerConfig = new WifiP2pConfig();
                    logd("InactiveState, case WifiP2pManager.FAST_CONNECT_AS_GC, set mSavedPeerConfig - " + mFastConnectInfo.deviceAddress);
                    mSavedPeerConfig.deviceAddress = mFastConnectInfo.deviceAddress;
                    mSavedPeerConfig.netId = mFastConnectInfo.networkId;
                    mWifiNative.p2pStopFind();

                    /*M: ALPS00804938: no matter how the desired fastConnect GO is GO/not GO, INVITE can work!*/
                    if (DBG) logd("Fast connect join Group, is peer GO? " + mPeers.isGroupOwner(mFastConnectInfo.deviceAddress));
                    logd("InactiveState, case WifiP2pManager.FAST_CONNECT_AS_GC, call p2pReinvoke - networkId = " + mFastConnectInfo.networkId + " deviceAddress = " + mFastConnectInfo.deviceAddress);
                    mWifiNative.p2pReinvoke(mFastConnectInfo.networkId, mFastConnectInfo.deviceAddress);
                    transitionTo(mGroupNegotiationState);
                    break;
                case FAST_CONNECT_FIND_GO_TIMED_OUT:
                    /*M: this message means fast connect failed because of find GO too long,
                     *  but still not found*/ 
                    if (mFastConnectFindGoTimeOutIndex == message.arg1) {
                        if (DBG) logd("FAST CONNECT FIND GO timed out");
                        handleGroupCreationFailure();
                        mFoundGoTimeOut = true;
                        mFoundGo=false;
                    }
                    break;
                ///@}
                default:
                    return NOT_HANDLED;
            }
            return HANDLED;
        }
    }

    class GroupCreatingState extends State {
        @Override
        public void enter() {
            if (DBG) logd(getName());
            sendMessageDelayed(obtainMessage(GROUP_CREATING_TIMED_OUT,
                    ++mGroupCreatingTimeoutIndex, 0), GROUP_CREATING_WAIT_TIME_MS);
            ///M: ALPS01212893: for poor link, wifi p2p start Tx all traffic @{
            sendP2pTxBroadcast(true);
            ///@}
        }

        @Override
        public boolean processMessage(Message message) {
            if (DBG) logd(getName() + message.toString());
            boolean ret = HANDLED;
            switch (message.what) {
               case GROUP_CREATING_TIMED_OUT:
                    if (mGroupCreatingTimeoutIndex == message.arg1) {
                        if (DBG) logd("Group negotiation timed out");
                        handleGroupCreationFailure();
                        transitionTo(mInactiveState);
                    }
                    break;
                case WifiMonitor.P2P_DEVICE_LOST_EVENT:
                    WifiP2pDevice device = (WifiP2pDevice) message.obj;
                    if (!mSavedPeerConfig.deviceAddress.equals(device.deviceAddress)) {
                        if (DBG) {
                            logd("mSavedPeerConfig " + mSavedPeerConfig.deviceAddress +
                                "device " + device.deviceAddress);
                        }
                        // Do the regular device lost handling
                        ret = NOT_HANDLED;
                        break;
                    }
                    // Do nothing
                    if (DBG) logd("Add device to lost list " + device);
                    mPeersLostDuringConnection.updateSupplicantDetails(device);
                    break;
                case WifiP2pManager.DISCOVER_PEERS:
                    /* Discovery will break negotiation */
                    replyToMessage(message, WifiP2pManager.DISCOVER_PEERS_FAILED,
                            WifiP2pManager.BUSY);
                    break;
                ///M:@{
                case WifiP2pManager.CANCEL_CONNECT:
                    logd("GroupCreatingState, case WifiP2pManager.CANCEL_CONNECT");
                    //Do a supplicant p2p_cancel which only cancels an ongoing
                    //group negotiation. This will fail for a pending provision
                    //discovery or for a pending user action, but at the framework
                    //level, we always treat cancel as succeeded and enter
                    //an inactive state
                    boolean success = false;
                    if (mWifiNative.p2pCancelConnect()) {
                        success = true;
                    } else if (mWifiNative.p2pGroupRemove(mInterface)) {
                        success = true;
                    }
                    handleGroupCreationFailure();
                    transitionTo(mInactiveState);
                    if (success) {
                        replyToMessage(message, WifiP2pManager.CANCEL_CONNECT_SUCCEEDED);
                    } else {
                        replyToMessage(message, WifiP2pManager.CANCEL_CONNECT_FAILED);
                    }
                    break;
                case WifiP2pManager.START_FAST_CONNECT_AS_GO:
                    logd("GroupCreatingState, case WifiP2pManager.START_FAST_CONNECT_AS_GO");
                    sendMessage(WifiP2pManager.CANCEL_CONNECT);
                    //will turn to inactiveState, let inactiveState to process this message
                    mRestartFastConnectAsGo = true;
                    mGoFastConnectEnaled = true;
                    deferMessage(message);
                    break;
                case WifiP2pManager.START_FAST_CONNECT_AS_GC:
                    logd("GroupCreatingState, case WifiP2pManager.START_FAST_CONNECT_AS_GC");
                    sendMessage(WifiP2pManager.CANCEL_CONNECT);
                    //will turn to inactiveState, let inactiveState to process this message
                    mRestartFastConnectAsGc = true;
                    mGcFastConnectEnaled = true;
                    deferMessage(message);
                    break;
                case WifiMonitor.P2P_DEVICE_FOUND_EVENT:
                    WifiP2pDevice peerDevice = (WifiP2pDevice) message.obj;
                    if (mThisDevice.deviceAddress.equals(peerDevice.deviceAddress)) break;
                    if (mSavedPeerConfig != null &&
                            mSavedPeerConfig.deviceAddress.equals(peerDevice.deviceAddress)) {
                    	peerDevice.status = WifiP2pDevice.INVITED;
                    }
                    logd("GroupCreatingState, case WifiMonitor.P2P_DEVICE_FOUND_EVENT, call mPeers.update - peerDevice.deviceAddress = " + peerDevice.deviceAddress + " peerDevice.deviceName = " + peerDevice.deviceName);
                    mPeers.update(peerDevice);
                    sendPeersChangedBroadcast();
                    break;
                ///@}
                default:
                    ret = NOT_HANDLED;
            }
            return ret;
        }
    }

    class UserAuthorizingNegotiationRequestState extends State {
        @Override
        public void enter() {
            if (DBG) logd(getName());
            notifyInvitationReceived();
        }

        @Override
        public boolean processMessage(Message message) {
            if (DBG) logd(getName() + message.toString());
            boolean ret = HANDLED;
            switch (message.what) {
                case PEER_CONNECTION_USER_ACCEPT:
                    if (DBG) logd("User accept negotiation");
                    mWifiNative.p2pStopFind();
                    logd("UserAuthorizingNegotiationRequestState, case PEER_CONNECTION_USER_ACCEPT, call p2pConnectWithPinDisplay");
                    p2pConnectWithPinDisplay(mSavedPeerConfig);
                    mPeers.updateStatus(mSavedPeerConfig.deviceAddress, WifiP2pDevice.INVITED);
                    sendPeersChangedBroadcast();
                    transitionTo(mGroupNegotiationState);
                   break;
                case PEER_CONNECTION_USER_REJECT:
                    if (DBG) logd("User rejected negotiation " + mSavedPeerConfig);
                    transitionTo(mInactiveState);
                    break;
                default:
                    return NOT_HANDLED;
            }
            return ret;
        }

        @Override
        public void exit() {
            //TODO: dismiss dialog if not already done
        }
    }

    class UserAuthorizingInviteRequestState extends State {
        @Override
        public void enter() {
            if (DBG) logd(getName());
            notifyInvitationReceived();
        }

        @Override
        public boolean processMessage(Message message) {
            if (DBG) logd(getName() + message.toString());
            boolean ret = HANDLED;
            switch (message.what) {
                case PEER_CONNECTION_USER_ACCEPT:
                    if (DBG) logd("User accept invitation");
                    mWifiNative.p2pStopFind();
                    logd("UserAuthorizingInviteRequestState, case PEER_CONNECTION_USER_ACCEPT, call reinvokePersistentGroup - mSavedPeerConfig.deviceAddress = " + mSavedPeerConfig.deviceAddress);
                    if (!reinvokePersistentGroup(mSavedPeerConfig)) {
                        logd("UserAuthorizingInviteRequestState, case PEER_CONNECTION_USER_ACCEPT, call p2pConnectWithPinDisplay");
                        // Do negotiation when persistence fails
                        p2pConnectWithPinDisplay(mSavedPeerConfig);
                    }
                    mPeers.updateStatus(mSavedPeerConfig.deviceAddress, WifiP2pDevice.INVITED);
                    sendPeersChangedBroadcast();
                    transitionTo(mGroupNegotiationState);
                   break;
                case PEER_CONNECTION_USER_REJECT:
                    if (DBG) logd("User rejected invitation " + mSavedPeerConfig);
                    transitionTo(mInactiveState);
                    break;
                default:
                    return NOT_HANDLED;
            }
            return ret;
        }

        @Override
        public void exit() {
            //TODO: dismiss dialog if not already done
        }
    }



    class ProvisionDiscoveryState extends State {
        @Override
        public void enter() {
            if (DBG) logd(getName());
            mWifiNative.p2pProvisionDiscovery(mSavedPeerConfig);
        }

        @Override
        public boolean processMessage(Message message) {
            if (DBG) logd(getName() + message.toString());
            WifiP2pProvDiscEvent provDisc;
            WifiP2pDevice device;
            switch (message.what) {
                case WifiMonitor.P2P_PROV_DISC_PBC_RSP_EVENT:
                    logd("ProvisionDiscoveryState, case WifiMonitor.P2P_PROV_DISC_PBC_RSP_EVENT");
                    provDisc = (WifiP2pProvDiscEvent) message.obj;
                    device = provDisc.device;
                    logd("ProvisionDiscoveryState, case WifiMonitor.P2P_PROV_DISC_PBC_RSP_EVENT, device.deviceAddress = " + device.deviceAddress + " mSavedPeerConfig.deviceAddress = " + mSavedPeerConfig.deviceAddress);
                    if (!device.deviceAddress.equals(mSavedPeerConfig.deviceAddress)) break;

                    if (mSavedPeerConfig.wps.setup == WpsInfo.PBC) {
                        if (DBG) logd("Found a match " + mSavedPeerConfig);
                        ///M: to fix provision failed @{
                        mWifiNative.p2pStopFind();
                        ///@}
                        logd("ProvisionDiscoveryState, case WifiMonitor.P2P_PROV_DISC_PBC_RSP_EVENT, call p2pConnectWithPinDisplay");
                        p2pConnectWithPinDisplay(mSavedPeerConfig);
                        transitionTo(mGroupNegotiationState);
                    }
                    break;
                case WifiMonitor.P2P_PROV_DISC_ENTER_PIN_EVENT:
                    logd("ProvisionDiscoveryState, case WifiMonitor.P2P_PROV_DISC_ENTER_PIN_EVENT");
                    provDisc = (WifiP2pProvDiscEvent) message.obj;
                    device = provDisc.device;
                    logd("ProvisionDiscoveryState, case WifiMonitor.P2P_PROV_DISC_ENTER_PIN_EVENT, device.deviceAddress = " + device.deviceAddress + " mSavedPeerConfig.deviceAddress = " + mSavedPeerConfig.deviceAddress);
                    if (!device.deviceAddress.equals(mSavedPeerConfig.deviceAddress)) break;

                    if (mSavedPeerConfig.wps.setup == WpsInfo.KEYPAD) {
                        if (DBG) logd("Found a match " + mSavedPeerConfig);
                        /* we already have the pin */
                        if (!TextUtils.isEmpty(mSavedPeerConfig.wps.pin)) {
                            ///M: to fix provision failed @{
                            mWifiNative.p2pStopFind();
                            ///@}
                            logd("ProvisionDiscoveryState, case WifiMonitor.P2P_PROV_DISC_ENTER_PIN_EVENT, call p2pConnectWithPinDisplay");
                            p2pConnectWithPinDisplay(mSavedPeerConfig);
                            transitionTo(mGroupNegotiationState);
                        } else {
                            mJoinExistingGroup = false;
                            transitionTo(mUserAuthorizingNegotiationRequestState);
                        }
                    }
                    break;
                case WifiMonitor.P2P_PROV_DISC_SHOW_PIN_EVENT:
                    logd("ProvisionDiscoveryState, case WifiMonitor.P2P_PROV_DISC_SHOW_PIN_EVENT");
                    provDisc = (WifiP2pProvDiscEvent) message.obj;
                    device = provDisc.device;
                    logd("ProvisionDiscoveryState, case WifiMonitor.P2P_PROV_DISC_SHOW_PIN_EVENT, device.deviceAddress = " + device.deviceAddress + " mSavedPeerConfig.deviceAddress = " + mSavedPeerConfig.deviceAddress);
                    if (!device.deviceAddress.equals(mSavedPeerConfig.deviceAddress)) break;

                    if (mSavedPeerConfig.wps.setup == WpsInfo.DISPLAY) {
                        if (DBG) logd("Found a match " + mSavedPeerConfig);
                        logd("ProvisionDiscoveryState, case WifiMonitor.P2P_PROV_DISC_SHOW_PIN_EVENT, provDisc.pin = " + provDisc.pin);
                        mSavedPeerConfig.wps.pin = provDisc.pin;
                        ///M: to fix provision failed @{
                        mWifiNative.p2pStopFind();
                        ///@}                        
                        logd("ProvisionDiscoveryState, case WifiMonitor.P2P_PROV_DISC_SHOW_PIN_EVENT, call p2pConnectWithPinDisplay");
                        p2pConnectWithPinDisplay(mSavedPeerConfig);
                        notifyInvitationSent(provDisc.pin, device.deviceAddress);
                        transitionTo(mGroupNegotiationState);
                    }
                    break;
                case WifiMonitor.P2P_PROV_DISC_FAILURE_EVENT:
                    logd("ProvisionDiscoveryState, case WifiMonitor.P2P_PROV_DISC_FAILURE_EVENT");
                    loge("provision discovery failed");
                    handleGroupCreationFailure();
                    transitionTo(mInactiveState);
                    break;
                ///M:@{
                case WifiP2pManager.CANCEL_CONNECT:
                    logd("ProvisionDiscoveryState, case WifiP2pManager.CANCEL_CONNECT");
                    //Do a supplicant p2p_cancel which only cancels an ongoing
                    //group negotiation. This will fail for a pending provision
                    //discovery or for a pending user action, so try Remove group
                    boolean success = mWifiNative.p2pGroupRemove(mInterface);
                    handleGroupCreationFailure();
                    transitionTo(mInactiveState);
                    replyToMessage(message, success == true 
                        ? WifiP2pManager.CANCEL_CONNECT_SUCCEEDED : WifiP2pManager.CANCEL_CONNECT_FAILED);
                    break;
                ///@}
                default:
                    return NOT_HANDLED;
            }
            return HANDLED;
        }
    }

    class GroupNegotiationState extends State {
        @Override
        public void enter() {
            if (DBG) logd(getName());
        }

        @Override
        public boolean processMessage(Message message) {
            if (DBG) logd(getName() + message.toString());
            switch (message.what) {
                // We ignore these right now, since we get a GROUP_STARTED notification
                // afterwards
                case WifiMonitor.P2P_GO_NEGOTIATION_SUCCESS_EVENT:
                case WifiMonitor.P2P_GROUP_FORMATION_SUCCESS_EVENT:
                    if (DBG) logd(getName() + " go success");
                    break;
                case WifiMonitor.P2P_GROUP_STARTED_EVENT:
                    mGroup = (WifiP2pGroup) message.obj;
                    if (DBG) logd(getName() + " group started");

                    if (mGroup.getNetworkId() == WifiP2pGroup.PERSISTENT_NET_ID) {
                        /*
                         * update cache information and set network id to mGroup.
                         */
                        logd("GroupNegotiationState, case WifiMonitor.P2P_GROUP_STARTED_EVENT, call updatePersistentNetworks - NO_RELOAD");
                        updatePersistentNetworks(NO_RELOAD);
                        String devAddr = mGroup.getOwner().deviceAddress;
                        logd("GroupNegotiationState, case WifiMonitor.P2P_GROUP_STARTED_EVENT, GO devAddr = " + devAddr);
                        mGroup.setNetworkId(mGroups.getNetworkId(devAddr,
                                mGroup.getNetworkName()));
                    }

                    logd("GroupNegotiationState, case WifiMonitor.P2P_GROUP_STARTED_EVENT, isGroupOwner = " + mGroup.isGroupOwner());

                    if (mGroup.isGroupOwner()) {
                        /* Setting an idle time out on GO causes issues with certain scenarios
                         * on clients where it can be off-channel for longer and with the power
                         * save modes used.
                         *
                         * TODO: Verify multi-channel scenarios and supplicant behavior are
                         * better before adding a time out in future
                         */
                        //Set group idle timeout of 10 sec, to avoid GO beaconing incase of any
                        //failure during 4-way Handshake.
                        if (!mAutonomousGroup) {
                            mWifiNative.setP2pGroupIdle(mGroup.getInterface(), GROUP_IDLE_TIME_S);
                        }
                        startDhcpServer(mGroup.getInterface());
                    } else {
                        ///M: fast connect @{
                        if (mGcFastConnectEnaled) {
                            String gcIp = mFastConnectInfo.gcIpAddress;
                            String intf = mGroup.getInterface();
                            try {
                                InterfaceConfiguration ifcg = mNwService.getInterfaceConfig(intf);
                                ifcg.setLinkAddress(new LinkAddress(NetworkUtils.numericToInetAddress(gcIp), 24));
                                ifcg.setInterfaceUp();
                                mNwService.setInterfaceConfig(intf, ifcg);                         
                            } catch (Exception e) {
                                loge("Error configuring interface " + intf + ", :" + e);
                            }
                        } else {
                        mWifiNative.setP2pGroupIdle(mGroup.getInterface(), GROUP_IDLE_TIME_S);
                        mDhcpStateMachine = DhcpStateMachine.makeDhcpStateMachine(mContext,
                                P2pStateMachine.this, mGroup.getInterface());
                        // TODO: We should use DHCP state machine PRE message like WifiStateMachine
                        mDhcpStateMachine.sendMessage(DhcpStateMachine.CMD_START_DHCP);
                        }
                        ///@}
                        ///M: use MTK power saving command @{
                        //mWifiNative.setP2pPowerSave(mGroup.getInterface(), true);

                        mWifiNative.setP2pPowerSaveMtk(mGroup.getInterface(), P2P_FAST_PS);
                        
                        ///@}
                        WifiP2pDevice groupOwner = mGroup.getOwner();
                        WifiP2pDevice peer = mPeers.get(groupOwner.deviceAddress);
                        if (peer != null) {
                            // update group owner details with peer details found at discovery
                            groupOwner.updateSupplicantDetails(peer);
                            mPeers.updateStatus(groupOwner.deviceAddress, WifiP2pDevice.CONNECTED);
                            sendPeersChangedBroadcast();
                        } else {
                            // A supplicant bug can lead to reporting an invalid
                            // group owner address (all zeroes) at times. Avoid a
                            // crash, but continue group creation since it is not
                            // essential.
                            logw("Unknown group owner " + groupOwner);
                        }
                    }
                    transitionTo(mGroupCreatedState);
                    break;
                case WifiMonitor.P2P_GO_NEGOTIATION_FAILURE_EVENT:
                    P2pStatus status = (P2pStatus) message.obj;
                    loge("go negotiation failed, status = " + status);
                    if (status == P2pStatus.NO_COMMON_CHANNEL) {
                        transitionTo(mFrequencyConflictState);
                        break;
                    }
                    /* continue with group removal handling */
                case WifiMonitor.P2P_GROUP_REMOVED_EVENT:
                    if (DBG) logd(getName() + " go failure");
                    handleGroupCreationFailure();
                    transitionTo(mInactiveState);
                    break;
                // A group formation failure is always followed by
                // a group removed event. Flushing things at group formation
                // failure causes supplicant issues. Ignore right now.
                case WifiMonitor.P2P_GROUP_FORMATION_FAILURE_EVENT:
                    status = (P2pStatus) message.obj;
                    loge("group formation failed, status = " + status);
                    if (status == P2pStatus.NO_COMMON_CHANNEL) {
                        transitionTo(mFrequencyConflictState);
                        break;
                    }
                    break;
                case WifiMonitor.P2P_INVITATION_RESULT_EVENT:
                    status = (P2pStatus)message.obj;
                    logd("invitation result - status = " + status);
                    if (status == P2pStatus.SUCCESS) {
                        // invocation was succeeded.
                        // wait P2P_GROUP_STARTED_EVENT.
                        break;
                    }
                    loge("Invitation result " + status);
                    if (status == P2pStatus.UNKNOWN_P2P_GROUP) {
                        // target device has already removed the credential.
                        // So, remove this credential accordingly.
                        int netId = mSavedPeerConfig.netId;
                        logd("GroupNegotiationState, case WifiMonitor.P2P_INVITATION_RESULT_EVENT, netId = " + netId);
                        if (netId >= 0) {
                            if (DBG) logd("Remove unknown client from the list");
                            removeClientFromList(netId, mSavedPeerConfig.deviceAddress, true);
                        }

                        // Reinvocation has failed, try group negotiation
                        mSavedPeerConfig.netId = WifiP2pGroup.PERSISTENT_NET_ID;
                        
                        logd("GroupNegotiationState, case WifiMonitor.P2P_INVITATION_RESULT_EVENT, call p2pConnectWithPinDisplay");
                        p2pConnectWithPinDisplay(mSavedPeerConfig);
                    } else if (status == P2pStatus.INFORMATION_IS_CURRENTLY_UNAVAILABLE) {

                        // Devices setting persistent_reconnect to 0 in wpa_supplicant
                        // always defer the invocation request and return
                        // "information is currently unable" error.
                        // So, try another way to connect for interoperability.
                        mSavedPeerConfig.netId = WifiP2pGroup.PERSISTENT_NET_ID;
                        logd("GroupNegotiationState, case WifiMonitor.P2P_INVITATION_RESULT_EVENT, call p2pConnectWithPinDisplay");
                        p2pConnectWithPinDisplay(mSavedPeerConfig);
                    } else if (status == P2pStatus.NO_COMMON_CHANNEL) {
                        transitionTo(mFrequencyConflictState);
                    } else {
                        handleGroupCreationFailure();
                        transitionTo(mInactiveState);
                    }
                    break;
                ///M:@{
                case WifiMonitor.WPS_OVERLAP_EVENT:
                    Toast.makeText(mContext, com.mediatek.internal.R.string.wifi_wps_failed_overlap
                        , Toast.LENGTH_SHORT).show();
                    
                    break;
                ///@}
                default:
                    return NOT_HANDLED;
            }
            return HANDLED;
        }
    }

    class FrequencyConflictState extends State {
        private AlertDialog mFrequencyConflictDialog;
        @Override
        public void enter() {
            if (DBG) logd(getName());
            notifyFrequencyConflict();
        }

        private void notifyFrequencyConflict() {
            logd("Notify frequency conflict");
            Resources r = Resources.getSystem();

            AlertDialog dialog = new AlertDialog.Builder(mContext)
                .setMessage(r.getString(R.string.wifi_p2p_frequency_conflict_message,
                        getDeviceName(mSavedPeerConfig.deviceAddress)))
                .setPositiveButton(r.getString(R.string.dlg_ok), new OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            sendMessage(DROP_WIFI_USER_ACCEPT);
                        }
                    })
                .setNegativeButton(r.getString(R.string.decline), new OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            sendMessage(DROP_WIFI_USER_REJECT);
                        }
                    })
                .setOnCancelListener(new DialogInterface.OnCancelListener() {
                        @Override
                        public void onCancel(DialogInterface arg0) {
                            sendMessage(DROP_WIFI_USER_REJECT);
                        }
                    })
                .create();

            dialog.getWindow().setType(WindowManager.LayoutParams.TYPE_SYSTEM_ALERT);
            dialog.show();
            mFrequencyConflictDialog = dialog;
        }

        @Override
        public boolean processMessage(Message message) {
            if (DBG) logd(getName() + message.toString());
            switch (message.what) {
                case WifiMonitor.P2P_GO_NEGOTIATION_SUCCESS_EVENT:
                case WifiMonitor.P2P_GROUP_FORMATION_SUCCESS_EVENT:
                    loge(getName() + "group sucess during freq conflict!");
                    break;
                case WifiMonitor.P2P_GROUP_STARTED_EVENT:
                    loge(getName() + "group started after freq conflict, handle anyway");
                    deferMessage(message);
                    transitionTo(mGroupNegotiationState);
                    break;
                case WifiMonitor.P2P_GO_NEGOTIATION_FAILURE_EVENT:
                case WifiMonitor.P2P_GROUP_REMOVED_EVENT:
                case WifiMonitor.P2P_GROUP_FORMATION_FAILURE_EVENT:
                    // Ignore failures since we retry again
                    break;
                case DROP_WIFI_USER_REJECT:
                    if (DBG) logd(getName() + "User reject");
                    // User rejected dropping wifi in favour of p2p
                    handleGroupCreationFailure();
                    transitionTo(mInactiveState);
                    break;
                case DROP_WIFI_USER_ACCEPT:
                    if (DBG) logd(getName() + "User accept");
                    // User accepted dropping wifi in favour of p2p
                    mWifiChannel.sendMessage(WifiP2pService.DISCONNECT_WIFI_REQUEST, 1);
                    mTempoarilyDisconnectedWifi = true;
                    break;
                case DISCONNECT_WIFI_RESPONSE:
                    // Got a response from wifistatemachine, retry p2p
                    if (DBG) logd(getName() + "Wifi disconnected, retry p2p");
                    transitionTo(mInactiveState);
                    ///M:@{
                    if (mGcFastConnectEnaled) {
                        sendMessage(WifiP2pManager.FAST_CONNECT_AS_GC);
                    } else {
                    sendMessage(WifiP2pManager.CONNECT, mSavedPeerConfig);
                    }
                    ///@}
                    break;
                default:
                    return NOT_HANDLED;
            }
            return HANDLED;
        }

        public void exit() {
            if (mFrequencyConflictDialog != null) mFrequencyConflictDialog.dismiss();
        }
    }

    class GroupCreatedState extends State {
        @Override
        public void enter() {
            if (DBG) logd(getName());
            // Once connected, peer config details are invalid
            mSavedPeerConfig.invalidate();
            mNetworkInfo.setDetailedState(NetworkInfo.DetailedState.CONNECTED, null, null);

            updateThisDevice(WifiP2pDevice.CONNECTED);

            logd("GroupCreatedState, enter, isGroupOwner = " + mGroup.isGroupOwner());

            //DHCP server has already been started if I am a group owner
            if (mGroup.isGroupOwner()) {
                setWifiP2pInfoOnGroupFormation(NetworkUtils.numericToInetAddress(SERVER_ADDRESS));
            ///M:@{
            } else if (mGcFastConnectEnaled) {
                setWifiP2pInfoOnGroupFormation(NetworkUtils.numericToInetAddress(SERVER_ADDRESS));
                sendP2pConnectionChangedBroadcast();
            }
            ///@}

            // In case of a negotiation group, connection changed is sent
            // after a client joins. For autonomous, send now
            if (mAutonomousGroup) {
                sendP2pConnectionChangedBroadcast();
            }
            
            /* ///M: ALPS01078896: remove multicast routing on wifi p2p  @{
            //route add -net 224.0.0.0 netmask 240.0.0.0 dev p2p0 
            try {
                InetAddress address = InetAddress.parseNumericAddress("224.0.0.0");
                RouteInfo route = new RouteInfo(new LinkAddress(address, 4), null);
                mNwService.addRoute("p2p0", route);
            } catch (Exception e) {
                loge("Failed to add multicast route " + e);
            }
            ///@} */
        }

        @Override
        public boolean processMessage(Message message) {
            if (DBG) logd(getName() + message.toString());
            switch (message.what) {
                case WifiMonitor.AP_STA_CONNECTED_EVENT:
                    WifiP2pDevice device = (WifiP2pDevice) message.obj;
                    logd("GroupCreatedState, case WifiMonitor.AP_STA_CONNECTED_EVENT, device.deviceAddress = " + device.deviceAddress + " device.deviceName = " + device.deviceName);
                    String deviceAddress = device.deviceAddress;
                    // Clear timeout that was set when group was started.
                    mWifiNative.setP2pGroupIdle(mGroup.getInterface(), 0);
                    if (deviceAddress != null) {
                        if (mPeers.get(deviceAddress) != null) {
                            mGroup.addClient(mPeers.get(deviceAddress));
                        } else {
                            ///M: ALPS00712601 + ALPS00741190: GC connected but not in GO UI @{
                            //mGroup.addClient(deviceAddress);
                            device = p2pGoGetSta(device, deviceAddress); 
                            logd("GroupCreatedState,case WifiMonitor.AP_STA_CONNECTED_EVENT, device.deviceAddress = " + device.deviceAddress + " device.deviceName = " + device.deviceName);
                            mGroup.addClient(device);
                            logd("GroupCreatedState, case WifiMonitor.AP_STA_CONNECTED_EVENT, call mPeers.update - device.deviceAddress = " + device.deviceAddress + " device.deviceName = " + device.deviceName);
                            mPeers.update(device);
                            ///@}
                        }
                        mPeers.updateStatus(deviceAddress, WifiP2pDevice.CONNECTED);
                        if (DBG) logd(getName() + " ap sta connected");
                        sendPeersChangedBroadcast();
                        ///M: ALPS01000113: sync. Beam+ state @{
                        if (true == mBeamPlusStart) {
                            mWifiNative.p2pBeamPlusGO(0); //0: reserve end
                        }
                        ///@}
                    } else {
                        loge("Connect on null device address, ignore");
                    }
                    sendP2pConnectionChangedBroadcast();
                    break;
                case WifiMonitor.AP_STA_DISCONNECTED_EVENT:
                    logd("GroupCreatedState, case WifiMonitor.AP_STA_DISCONNECTED_EVENT");
                    device = (WifiP2pDevice) message.obj;
                    deviceAddress = device.deviceAddress;
                    logd("GroupCreatedState, case WifiMonitor.AP_STA_DISCONNECTED_EVENT, deviceAddress = " + deviceAddress);
                    if (deviceAddress != null) {
                        mPeers.updateStatus(deviceAddress, WifiP2pDevice.AVAILABLE);
                        if (mGroup.removeClient(deviceAddress)) {
                            if (DBG) logd("Removed client " + deviceAddress);
                            if (!mAutonomousGroup && mGroup.isClientListEmpty()) {
                                logd("Client list empty, remove non-persistent p2p group");
                                mWifiNative.p2pGroupRemove(mGroup.getInterface());
                                // We end up sending connection changed broadcast
                                // when this happens at exit()
                            } else {
                                // Notify when a client disconnects from group
                                sendP2pConnectionChangedBroadcast();
                            }
                        } else {
                            if (DBG) logd("Failed to remove client " + deviceAddress);
                            for (WifiP2pDevice c : mGroup.getClientList()) {
                                if (DBG) logd("client " + c.deviceAddress);
                            }
                        }
                        sendPeersChangedBroadcast();
                        if (DBG) logd(getName() + " ap sta disconnected");
                    } else {
                        loge("Disconnect on unknown device: " + device);
                    }
                    break;
                case DhcpStateMachine.CMD_POST_DHCP_ACTION:
                    DhcpResults dhcpResults = (DhcpResults) message.obj;
                    if (message.arg1 == DhcpStateMachine.DHCP_SUCCESS &&
                            dhcpResults != null) {
                        if (DBG) logd("DhcpResults: " + dhcpResults);
                        setWifiP2pInfoOnGroupFormation(dhcpResults.serverAddress);
                        sendP2pConnectionChangedBroadcast();
                        //Turn on power save on client
                        ///M: GC power saving had doing at GroupNegotiationState P2P_GROUP_STARTED_EVENT @{
                        //mWifiNative.setP2pPowerSave(mGroup.getInterface(), true);
                        ///@}
                    } else {
                        loge("DHCP failed");
                        mWifiNative.p2pGroupRemove(mGroup.getInterface());
                    }
                    break;
                case WifiP2pManager.REMOVE_GROUP:
                    if (DBG) logd(getName() + " remove group");
                    if (mWifiNative.p2pGroupRemove(mGroup.getInterface())) {
                        transitionTo(mOngoingGroupRemovalState);
                        replyToMessage(message, WifiP2pManager.REMOVE_GROUP_SUCCEEDED);
                    } else {
                        handleGroupRemoved();
                        transitionTo(mInactiveState);
                        replyToMessage(message, WifiP2pManager.REMOVE_GROUP_FAILED,
                                WifiP2pManager.ERROR);
                    }
                    break;
                /* We do not listen to NETWORK_DISCONNECTION_EVENT for group removal
                 * handling since supplicant actually tries to reconnect after a temporary
                 * disconnect until group idle time out. Eventually, a group removal event
                 * will come when group has been removed.
                 *
                 * When there are connectivity issues during temporary disconnect, the application
                 * will also just remove the group.
                 *
                 * Treating network disconnection as group removal causes race conditions since
                 * supplicant would still maintain the group at that stage.
                 */
                case WifiMonitor.P2P_GROUP_REMOVED_EVENT:
                    ///M:@{
                    mGroupRemoveReason = (P2pStatus) message.obj;
                    if (DBG) logd(getName() + " group removed, reason: " + mGroupRemoveReason);
                    ///@}
                    handleGroupRemoved();
                    transitionTo(mInactiveState);
                    break;
                case WifiMonitor.P2P_DEVICE_LOST_EVENT:
                    device = (WifiP2pDevice) message.obj;
                    //Device loss for a connected device indicates it is not in discovery any more
                    if (mGroup.contains(device)) {
                        if (DBG) logd("Add device to lost list " + device);
                        mPeersLostDuringConnection.updateSupplicantDetails(device);
                        return HANDLED;
                    }
                    // Do the regular device lost handling
                    return NOT_HANDLED;
                case WifiStateMachine.CMD_DISABLE_P2P_REQ:
                    sendMessage(WifiP2pManager.REMOVE_GROUP);
                    deferMessage(message);
                    break;
                    // This allows any client to join the GO during the
                    // WPS window
                case WifiP2pManager.START_WPS:
                    WpsInfo wps = (WpsInfo) message.obj;
                    if (wps == null) {
                        replyToMessage(message, WifiP2pManager.START_WPS_FAILED);
                        break;
                    }
                    boolean ret = true;
                    if (wps.setup == WpsInfo.PBC) {
                        ret = mWifiNative.startWpsPbc(mGroup.getInterface(), null);
                    } else {
                        if (wps.pin == null) {
                            String pin = mWifiNative.startWpsPinDisplay(mGroup.getInterface());
                            try {
                                Integer.parseInt(pin);
                                notifyInvitationSent(pin, "any");
                            } catch (NumberFormatException ignore) {
                                ret = false;
                            }
                        } else {
                            ret = mWifiNative.startWpsPinKeypad(mGroup.getInterface(),
                                    wps.pin);
                        }
                    }
                    replyToMessage(message, ret ? WifiP2pManager.START_WPS_SUCCEEDED :
                            WifiP2pManager.START_WPS_FAILED);
                    break;
                case WifiP2pManager.CONNECT:
                    //M: add for ALPS00489161
                    mGroupCreatedEntirely = true;
                    WifiP2pConfig config = (WifiP2pConfig) message.obj;
                    if (isConfigInvalid(config)) {
                        loge("Dropping connect requeset " + config);
                        replyToMessage(message, WifiP2pManager.CONNECT_FAILED);
                        break;
                    }
                    logd("Inviting device : " + config.deviceAddress);
                    logd("GroupCreatedState, case WifiP2pManager.CONNECT, set mSavedPeerConfig - " + config.deviceAddress);
                    mSavedPeerConfig = config;
                    if (mWifiNative.p2pInvite(mGroup, config.deviceAddress)) {
                        mPeers.updateStatus(config.deviceAddress, WifiP2pDevice.INVITED);
                        sendPeersChangedBroadcast();
                        replyToMessage(message, WifiP2pManager.CONNECT_SUCCEEDED);
                    } else {
                        replyToMessage(message, WifiP2pManager.CONNECT_FAILED,
                                WifiP2pManager.ERROR);
                    }
                    // TODO: figure out updating the status to declined when invitation is rejected
                    break;
                case WifiMonitor.P2P_INVITATION_RESULT_EVENT:
                    P2pStatus status = (P2pStatus)message.obj;
                    logd("invitation result - status = " + status);
                    logd("===> INVITATION RESULT EVENT : " + status +
                        ",\tis GO ? : " + mGroup.getOwner().deviceAddress.equals(mThisDevice.deviceAddress));
                    ///M: ALPS00609781: remove 3rd phone on gc UI when 3rd is invited @{
                    boolean inviteDone = false;
                    if (status == P2pStatus.SUCCESS) {
                        // invocation was succeeded.
                        //break;
                        inviteDone = true;
                    }
                    loge("Invitation result " + status +
                        ",\tis GO ? : " + mGroup.getOwner().deviceAddress.equals(mThisDevice.deviceAddress));
                    if (status == P2pStatus.UNKNOWN_P2P_GROUP) {
                        // target device has already removed the credential.
                        // So, remove this credential accordingly.
                        int netId = mGroup.getNetworkId();
                        if (netId >= 0) {
                            if (DBG) logd("Remove unknown client from the list");
                            if (!removeClientFromList(netId,
                                    mSavedPeerConfig.deviceAddress, false)) {
                                // not found the client on the list
                                loge("Already removed the client, ignore");
                                break;
                            }
                            // try invitation.
                            sendMessage(WifiP2pManager.CONNECT, mSavedPeerConfig);
                        }
                    } else {
                        inviteDone = true;
                    }

                    if (true == inviteDone && 
                        !mGroup.getOwner().deviceAddress.equals(mThisDevice.deviceAddress)) {
                        logd("GroupCreatedState, case WifiMonitor.P2P_INVITATION_RESULT_EVENT, call mPeers.remove - mSavedPeerConfig.deviceAddress = " + mSavedPeerConfig.deviceAddress);
                        if (mPeers.remove(mPeers.get(mSavedPeerConfig.deviceAddress))) sendPeersChangedBroadcast();
                    }
                    ///@}
                    break;
                case WifiMonitor.P2P_PROV_DISC_PBC_REQ_EVENT:
                case WifiMonitor.P2P_PROV_DISC_ENTER_PIN_EVENT:
                case WifiMonitor.P2P_PROV_DISC_SHOW_PIN_EVENT:
                    logd("GroupCreatedState, get P2P_PROV_DISC_XXX");
                    //M: add for ALPS00489161
                    mGroupCreatedEntirely = true;
                    WifiP2pProvDiscEvent provDisc = (WifiP2pProvDiscEvent) message.obj;
                    if (provDisc != null) {
                        logd("GroupCreatedState, get P2P_PROV_DISC_XXX, provDisc.device.deviceAddress = " + provDisc.device.deviceAddress + " provDisc.device.deviceName = " + provDisc.device.deviceName);
                    }
                    mSavedPeerConfig = new WifiP2pConfig();
                    logd("GroupCreatedState, GroupCreatedState, get P2P_PROV_DISC_XXX, set mSavedPeerConfig - " + provDisc.device.deviceAddress);
                    mSavedPeerConfig.deviceAddress = provDisc.device.deviceAddress;
                    if (message.what == WifiMonitor.P2P_PROV_DISC_ENTER_PIN_EVENT) {
                        mSavedPeerConfig.wps.setup = WpsInfo.KEYPAD;
                    } else if (message.what == WifiMonitor.P2P_PROV_DISC_SHOW_PIN_EVENT) {
                        mSavedPeerConfig.wps.setup = WpsInfo.DISPLAY;
                        mSavedPeerConfig.wps.pin = provDisc.pin;
                    } else {
                        mSavedPeerConfig.wps.setup = WpsInfo.PBC;
                    }
                    transitionTo(mUserAuthorizingJoinState);
                    break;
                case WifiMonitor.P2P_GROUP_STARTED_EVENT:
                    loge("Duplicate group creation event notice, ignore");
                    break;
                ///M:@{

                case WifiMonitor.P2P_PEER_DISCONNECT_EVENT:
                    logd("peer disconnect");
                    /*M: ALPS00790492: handle P2P_PEER_DISCONNECT_EVENT frequency conflict error, reason code is 99*/
                    int IEEE802Dot11_ReasonCode = -1;
                    if (null != message.obj) {
                        try {
                            IEEE802Dot11_ReasonCode = Integer.valueOf( (String)message.obj );
                            if (99 == IEEE802Dot11_ReasonCode) {
                                mGroupRemoveReason = P2pStatus.NO_COMMON_CHANNEL;
                            }
                        } catch (NumberFormatException e) {
                             logd("Error! Format unexpected");
                        }
                    }
                	/*only GC will received this event*/
                    if (DBG) loge(getName() + " I'm GC and has been disconnected by GO. IEEE 802.11 reason code: " + IEEE802Dot11_ReasonCode);
                    mWifiNative.p2pGroupRemove(mGroup.getInterface());
                    handleGroupRemoved();
                    transitionTo(mInactiveState);
                    break;
                    
                case WifiP2pManager.START_FAST_CONNECT_AS_GO:
                    logd("GroupCreatedState, case WifiP2pManager.START_FAST_CONNECT_AS_GO");
             	    sendMessage(WifiP2pManager.REMOVE_GROUP);
             	    //will turn to inactiveState, let inactiveState to process this message
             	    mGoFastConnectEnaled = true;
                    mRestartFastConnectAsGo = true;
             	    deferMessage(message);
             	    break;  
                case WifiP2pManager.START_FAST_CONNECT_AS_GC:
                    logd("GroupCreatedState, case WifiP2pManager.START_FAST_CONNECT_AS_GC");
             	    sendMessage(WifiP2pManager.REMOVE_GROUP);
             	    //will turn to inactiveState, let inactiveState to process this message
             	    mGcFastConnectEnaled = true;
                    mRestartFastConnectAsGc = true;
             	    deferMessage(message);
             	    break;
                case WifiMonitor.P2P_DEVICE_FOUND_EVENT:
                    WifiP2pDevice peerDevice = (WifiP2pDevice) message.obj;
                    if (mThisDevice.deviceAddress.equals(peerDevice.deviceAddress)) break;
                    if (mGroup.contains(peerDevice)) {
                    	peerDevice.status = WifiP2pDevice.CONNECTED;
                    }
                    logd("GroupCreatedState, case WifiMonitor.P2P_DEVICE_FOUND_EVENT, call mPeers.update - peerDevice.deviceAddress = " + peerDevice.deviceAddress + " peerDevice.deviceName = " + peerDevice.deviceName);
                    mPeers.update(peerDevice);
                    sendPeersChangedBroadcast();
             	    break;         
                case WifiMonitor.SUP_DISCONNECTION_EVENT:
                    if (DBG) loge("Supplicant close unexpected, send fake Group Remove event");
                    sendMessage(WifiMonitor.P2P_GROUP_REMOVED_EVENT);
                    deferMessage(message);
                    break;
                case WifiP2pManager.DISCOVER_PEERS:
                    // do not send service discovery request while normal find operation.
                    clearSupplicantServiceRequest();
                    if (mWifiNative.p2pFind(CONNECTED_DISCOVER_TIMEOUT_S)) {
                        replyToMessage(message, WifiP2pManager.DISCOVER_PEERS_SUCCEEDED);
                        sendP2pDiscoveryChangedBroadcast(true);
                    } else {
                        replyToMessage(message, WifiP2pManager.DISCOVER_PEERS_FAILED,
                                WifiP2pManager.ERROR);
                    }
                    break;
                case WifiP2pManager.CANCEL_CONNECT:
                    logd("GroupCreatedState, case WifiP2pManager.CANCEL_CONNECT");
                    /*M: when group created successfully,
                    and UI do not know in time still send cancel connect command. 
                    This case GroupcreatedState should remove the group. But when Inviting 3-device
                    to join a group and 3-device do not response.At this time GO or GC click cancel means 
                    the Group has created entirely, so use the default solution to process the cancel command*/
                    if (mGroupCreatedEntirely) {
                        return NOT_HANDLED;
                    }
                    
                    if (DBG) logd(getName() + " cancel connect, try to remove group");
                    boolean success = mWifiNative.p2pGroupRemove(mGroup.getInterface());
                    handleGroupRemoved();
                    transitionTo(mInactiveState);
                    replyToMessage(message, success == true
                        ? WifiP2pManager.CANCEL_CONNECT_SUCCEEDED : WifiP2pManager.CANCEL_CONNECT_FAILED);
                    break;
                ///@}
                default:
                    return NOT_HANDLED;
            }
            return HANDLED;
        }

        public void exit() {
            logd("GroupCreatedState, exit");
            updateThisDevice(WifiP2pDevice.AVAILABLE);
            resetWifiP2pInfo();
            mNetworkInfo.setDetailedState(NetworkInfo.DetailedState.DISCONNECTED, null, null);
            /*M: ALPS00677009: broadcast the group removed reason*/
            sendP2pConnectionChangedBroadcast(mGroupRemoveReason);
        }
    }

    class UserAuthorizingJoinState extends State {
        @Override
        public void enter() {
            if (DBG) logd(getName());
            notifyInvitationReceived();
        }

        @Override
        public boolean processMessage(Message message) {
            if (DBG) logd(getName() + message.toString());
            switch (message.what) {
                case WifiMonitor.P2P_PROV_DISC_PBC_REQ_EVENT:
                case WifiMonitor.P2P_PROV_DISC_ENTER_PIN_EVENT:
                case WifiMonitor.P2P_PROV_DISC_SHOW_PIN_EVENT:
                    //Ignore more client requests
                    break;
                case PEER_CONNECTION_USER_ACCEPT:
                    if (DBG) logd("User accept incoming request");
                    //Stop discovery to avoid failure due to channel switch
                    mWifiNative.p2pStopFind();
                    if (mSavedPeerConfig.wps.setup == WpsInfo.PBC) {
                        mWifiNative.startWpsPbc(mGroup.getInterface(), null);
                    } else {
                        mWifiNative.startWpsPinKeypad(mGroup.getInterface(),
                                mSavedPeerConfig.wps.pin);
                    }
                    transitionTo(mGroupCreatedState);
                    break;
                case PEER_CONNECTION_USER_REJECT:
                    if (DBG) logd("User rejected incoming request");
                    transitionTo(mGroupCreatedState);
                    break;
                default:
                    return NOT_HANDLED;
            }
            return HANDLED;
        }

        @Override
        public void exit() {
            //TODO: dismiss dialog if not already done
        }
    }

    class OngoingGroupRemovalState extends State {
        @Override
        public void enter() {
            if (DBG) logd(getName());
        }

        @Override
        public boolean processMessage(Message message) {
            if (DBG) logd(getName() + message.toString());
            switch (message.what) {
                // Group removal ongoing. Multiple calls
                // end up removing persisted network. Do nothing.
                case WifiP2pManager.REMOVE_GROUP:
                    replyToMessage(message, WifiP2pManager.REMOVE_GROUP_SUCCEEDED);
                    break;
                // Parent state will transition out of this state
                // when removal is complete
                default:
                    return NOT_HANDLED;
            }
            return HANDLED;
        }
    }

    @Override
    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        super.dump(fd, pw, args);
        pw.println("mWifiP2pInfo " + mWifiP2pInfo);
        pw.println("mGroup " + mGroup);
        pw.println("mSavedPeerConfig " + mSavedPeerConfig);
        pw.println("mSavedP2pGroup " + mSavedP2pGroup);
        pw.println();
    }

    private void sendP2pStateChangedBroadcast(boolean enabled) {
        logd("sendP2pStateChangedBroadcast, enabled = " + enabled);
        final Intent intent = new Intent(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION);
        intent.addFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY_BEFORE_BOOT);
        if (enabled) {
            intent.putExtra(WifiP2pManager.EXTRA_WIFI_STATE,
                    WifiP2pManager.WIFI_P2P_STATE_ENABLED);
        } else {
            intent.putExtra(WifiP2pManager.EXTRA_WIFI_STATE,
                    WifiP2pManager.WIFI_P2P_STATE_DISABLED);
        }
        mContext.sendStickyBroadcastAsUser(intent, UserHandle.ALL);
    }

    private void sendP2pDiscoveryChangedBroadcast(boolean started) {
        logd("sendP2pDiscoveryChangedBroadcast, started = " + started);
        if (mDiscoveryStarted == started) return;
        mDiscoveryStarted = started;

        if (DBG) logd("discovery change broadcast " + started);

        final Intent intent = new Intent(WifiP2pManager.WIFI_P2P_DISCOVERY_CHANGED_ACTION);
        intent.addFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY_BEFORE_BOOT);
        intent.putExtra(WifiP2pManager.EXTRA_DISCOVERY_STATE, started ?
                WifiP2pManager.WIFI_P2P_DISCOVERY_STARTED :
                WifiP2pManager.WIFI_P2P_DISCOVERY_STOPPED);
        mContext.sendStickyBroadcastAsUser(intent, UserHandle.ALL);
    }

    private void sendThisDeviceChangedBroadcast() {
        logd("sendThisDeviceChangedBroadcast");
        final Intent intent = new Intent(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION);
        intent.addFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY_BEFORE_BOOT);
        intent.putExtra(WifiP2pManager.EXTRA_WIFI_P2P_DEVICE, new WifiP2pDevice(mThisDevice));
        mContext.sendStickyBroadcastAsUser(intent, UserHandle.ALL);
    }

    private void sendPeersChangedBroadcast() {
        logd("sendPeersChangedBroadcast");
        final Intent intent = new Intent(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION);
        intent.putExtra(WifiP2pManager.EXTRA_P2P_DEVICE_LIST, new WifiP2pDeviceList(mPeers));
        intent.addFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY_BEFORE_BOOT);
        /*M: ALPS00541624, sticky broadcast to avoid apk miss peer information */
        mContext.sendStickyBroadcastAsUser(intent, UserHandle.ALL);
    }

    private void sendP2pConnectionChangedBroadcast() {
        logd("sendP2pConnectionChangedBroadcast");
        if (DBG) logd("sending p2p connection changed broadcast, mGroup: " + mGroup);
        Intent intent = new Intent(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION);
        intent.addFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY_BEFORE_BOOT
                | Intent.FLAG_RECEIVER_REPLACE_PENDING);
        intent.putExtra(WifiP2pManager.EXTRA_WIFI_P2P_INFO, new WifiP2pInfo(mWifiP2pInfo));
        intent.putExtra(WifiP2pManager.EXTRA_NETWORK_INFO, new NetworkInfo(mNetworkInfo));
        intent.putExtra(WifiP2pManager.EXTRA_WIFI_P2P_GROUP, new WifiP2pGroup(mGroup));
        mContext.sendStickyBroadcastAsUser(intent, UserHandle.ALL);
        mWifiChannel.sendMessage(WifiP2pService.P2P_CONNECTION_CHANGED,
                new NetworkInfo(mNetworkInfo));
    }

    private void sendP2pPersistentGroupsChangedBroadcast() {
        logd("sendP2pPersistentGroupsChangedBroadcast");
        if (DBG) logd("sending p2p persistent groups changed broadcast");
        Intent intent = new Intent(WifiP2pManager.WIFI_P2P_PERSISTENT_GROUPS_CHANGED_ACTION);
        intent.addFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY_BEFORE_BOOT);
        mContext.sendStickyBroadcastAsUser(intent, UserHandle.ALL);
    }

    private void startDhcpServer(String intf) {
        logd("startDhcpServer, intf = " + intf);
        InterfaceConfiguration ifcg = null;
        try {
            ifcg = mNwService.getInterfaceConfig(intf);
            ifcg.setLinkAddress(new LinkAddress(NetworkUtils.numericToInetAddress(
                        SERVER_ADDRESS), 24));
            ifcg.setInterfaceUp();
            mNwService.setInterfaceConfig(intf, ifcg);
            /* This starts the dnsmasq server */
            mNwService.startTethering(DHCP_RANGE);
        } catch (Exception e) {
            loge("Error configuring interface " + intf + ", :" + e);
            return;
        }

        logd("Started Dhcp server on " + intf);
   }

    private void stopDhcpServer(String intf) {
        logd("stopDhcpServer, intf = " + intf);
        try {
            mNwService.stopTethering();
        } catch (Exception e) {
            loge("Error stopping Dhcp server" + e);
            return;
        }

        logd("Stopped Dhcp server");
    }

    private void notifyP2pEnableFailure() {
        logd("notifyP2pEnableFailure");
        Resources r = Resources.getSystem();
        AlertDialog dialog = new AlertDialog.Builder(mContext)
            .setTitle(r.getString(R.string.wifi_p2p_dialog_title))
            .setMessage(r.getString(R.string.wifi_p2p_failed_message))
            .setPositiveButton(r.getString(R.string.ok), null)
            .create();
        dialog.getWindow().setType(WindowManager.LayoutParams.TYPE_SYSTEM_ALERT);
        dialog.show();
    }

    private void addRowToDialog(ViewGroup group, int stringId, String value) {
        logd("addRowToDialog, stringId = " + stringId + " value = " + value);
        Resources r = Resources.getSystem();
        View row = LayoutInflater.from(mContext).inflate(R.layout.wifi_p2p_dialog_row,
                group, false);
        ((TextView) row.findViewById(R.id.name)).setText(r.getString(stringId));
        ((TextView) row.findViewById(R.id.value)).setText(value);
        group.addView(row);
    }

    private void notifyInvitationSent(String pin, String peerAddress) {
        logd("notifyInvitationSent, pin = " + pin + " peerAddress = " + peerAddress);
        Resources r = Resources.getSystem();

        final View textEntryView = LayoutInflater.from(mContext)
                .inflate(R.layout.wifi_p2p_dialog, null);

        ViewGroup group = (ViewGroup) textEntryView.findViewById(R.id.info);
        addRowToDialog(group, R.string.wifi_p2p_to_message, getDeviceName(peerAddress));
        addRowToDialog(group, R.string.wifi_p2p_show_pin_message, pin);

        AlertDialog dialog = new AlertDialog.Builder(mContext)
            .setTitle(r.getString(R.string.wifi_p2p_invitation_sent_title))
            .setView(textEntryView)
            .setPositiveButton(r.getString(R.string.ok), null)
            .create();
        dialog.getWindow().setType(WindowManager.LayoutParams.TYPE_SYSTEM_ALERT);
        dialog.show();
    }

    private void notifyInvitationReceived() {
        logd("notifyInvitationReceived");
        Resources r = Resources.getSystem();
        final WpsInfo wps = mSavedPeerConfig.wps;
        final View textEntryView = LayoutInflater.from(mContext)
                .inflate(R.layout.wifi_p2p_dialog, null);

        ViewGroup group = (ViewGroup) textEntryView.findViewById(R.id.info);
        logd("notifyInvitationReceived, mSavedPeerConfig.deviceAddress = " + mSavedPeerConfig.deviceAddress);
        addRowToDialog(group, R.string.wifi_p2p_from_message, getDeviceName(
                mSavedPeerConfig.deviceAddress));

        final EditText pin = (EditText) textEntryView.findViewById(R.id.wifi_p2p_wps_pin);

        AlertDialog dialog = new AlertDialog.Builder(mContext)
            .setTitle(r.getString(R.string.wifi_p2p_invitation_to_connect_title))
            .setView(textEntryView)
            .setPositiveButton(r.getString(R.string.accept), new OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            if (wps.setup == WpsInfo.KEYPAD) {
                                mSavedPeerConfig.wps.pin = pin.getText().toString();
                            }
                            if (DBG) logd(getName() + " accept invitation " + mSavedPeerConfig);
                            sendMessage(PEER_CONNECTION_USER_ACCEPT);
                        }
                    })
            .setNegativeButton(r.getString(R.string.decline), new OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            if (DBG) logd(getName() + " ignore connect");
                            sendMessage(PEER_CONNECTION_USER_REJECT);
                        }
                    })
            .setOnCancelListener(new DialogInterface.OnCancelListener() {
                        @Override
                        public void onCancel(DialogInterface arg0) {
                            if (DBG) logd(getName() + " ignore connect");
                            sendMessage(PEER_CONNECTION_USER_REJECT);
                        }
                    })
            .create();

        //make the enter pin area or the display pin area visible
        switch (wps.setup) {
            case WpsInfo.KEYPAD:
                if (DBG) logd("Enter pin section visible");
                textEntryView.findViewById(R.id.enter_pin_section).setVisibility(View.VISIBLE);
                break;
            case WpsInfo.DISPLAY:
                if (DBG) logd("Shown pin section visible");
                addRowToDialog(group, R.string.wifi_p2p_show_pin_message, wps.pin);
                break;
            default:
                break;
        }

        if ((r.getConfiguration().uiMode & Configuration.UI_MODE_TYPE_APPLIANCE) ==
                Configuration.UI_MODE_TYPE_APPLIANCE) {
            // For appliance devices, add a key listener which accepts.
            dialog.setOnKeyListener(new DialogInterface.OnKeyListener() {

                @Override
                public boolean onKey(DialogInterface dialog, int keyCode, KeyEvent event) {
                    // TODO: make the actual key come from a config value.
                    if (keyCode == KeyEvent.KEYCODE_VOLUME_MUTE) {
                        sendMessage(PEER_CONNECTION_USER_ACCEPT);
                        dialog.dismiss();
                        return true;
                    }
                    return false;
                }
            });
            // TODO: add timeout for this dialog.
            // TODO: update UI in appliance mode to tell user what to do.
        }

        dialog.getWindow().setType(WindowManager.LayoutParams.TYPE_SYSTEM_ALERT);
        dialog.show();
    }

    /**
     * Synchronize the persistent group list between
     * wpa_supplicant and mGroups.
     */
    private void updatePersistentNetworks(boolean reload) {
        logd("updatePersistentNetworks, reload = " + reload);
        String listStr = mWifiNative.listNetworks();
        if (listStr == null) return;

        boolean isSaveRequired = false;
        String[] lines = listStr.split("\n");
        if (lines == null) return;

        if (reload) mGroups.clear();

        // Skip the first line, which is a header
        for (int i = 1; i < lines.length; i++) {
            String[] result = lines[i].split("\t");
            if (result == null || result.length < 4) {
                continue;
            }
            // network-id | ssid | bssid | flags
            int netId = -1;
            String ssid = result[1];
            String bssid = result[2];
            String flags = result[3];
            try {
                netId = Integer.parseInt(result[0]);
            } catch(NumberFormatException e) {
                e.printStackTrace();
                continue;
            }

            if (flags.indexOf("[CURRENT]") != -1) {
                continue;
            }
            if (flags.indexOf("[P2P-PERSISTENT]") == -1) {
                /*
                 * The unused profile is sometimes remained when the p2p group formation is failed.
                 * So, we clean up the p2p group here.
                 */
                if (DBG) logd("clean up the unused persistent group. netId=" + netId);
                mWifiNative.removeNetwork(netId);
                isSaveRequired = true;
                continue;
            }

            if (mGroups.contains(netId)) {
                continue;
            }

            WifiP2pGroup group = new WifiP2pGroup();
            group.setNetworkId(netId);
            group.setNetworkName(ssid);
            String mode = mWifiNative.getNetworkVariable(netId, "mode");
            if (mode != null && mode.equals("3")) {
                group.setIsGroupOwner(true);
            }
            if (bssid.equalsIgnoreCase(mThisDevice.deviceAddress)) {
                logd("updatePersistentNetworks, call group.setOwner - mThisDevice.deviceAddress = " + mThisDevice.deviceAddress);
                group.setOwner(mThisDevice);
            } else {
                WifiP2pDevice device = new WifiP2pDevice();
                device.deviceAddress = bssid;
                logd("updatePersistentNetworks, call group.setOwner - device.deviceAddress = " + mThisDevice.deviceAddress);
                group.setOwner(device);
            }
            mGroups.add(group);
            isSaveRequired = true;
        }

        if (reload || isSaveRequired) {
            mWifiNative.saveConfig();
            sendP2pPersistentGroupsChangedBroadcast();
        }
    }

    /**
     * A config is valid if it has a peer address that has already been
     * discovered
     * @return true if it is invalid, false otherwise
     */
    private boolean isConfigInvalid(WifiP2pConfig config) {
        if (config == null) return true;
        if (TextUtils.isEmpty(config.deviceAddress)) return true;
        if (mPeers.get(config.deviceAddress) == null) return true;
        return false;
    }

    /* TODO: The supplicant does not provide group capability changes as an event.
     * Having it pushed as an event would avoid polling for this information right
     * before a connection
     */
    private WifiP2pDevice fetchCurrentDeviceDetails(WifiP2pConfig config) {
        logd("fetchCurrentDeviceDetails");
        /* Fetch & update group capability from supplicant on the device */
        int gc = mWifiNative.getGroupCapability(config.deviceAddress);
        logd("fetchCurrentDeviceDetails, gc = " + gc + " config.deviceAddress = " + config.deviceAddress);
        mPeers.updateGroupCapability(config.deviceAddress, gc);
        return mPeers.get(config.deviceAddress);
    }

    /**
     * Start a p2p group negotiation and display pin if necessary
     * @param config for the peer
     */
    private void p2pConnectWithPinDisplay(WifiP2pConfig config) {
        logd("p2pConnectWithPinDisplay");
        WifiP2pDevice dev = fetchCurrentDeviceDetails(config);
        logd("p2pConnectWithPinDisplay, dev.deviceAddress = " + dev.deviceAddress + " dev.isGroupOwner() = " + dev.isGroupOwner());

        logd("p2pConnectWithPinDisplay, call p2pConnect");
        String pin = mWifiNative.p2pConnect(config, dev.isGroupOwner());
        logd("p2pConnectWithPinDisplay, pin = " + pin);
        try {
            Integer.parseInt(pin);
            notifyInvitationSent(pin, config.deviceAddress);
        } catch (NumberFormatException ignore) {
            // do nothing if p2pConnect did not return a pin
        }
    }

    /**
     * Reinvoke a persistent group.
     *
     * @param config for the peer
     * @return true on success, false on failure
     */
    private boolean reinvokePersistentGroup(WifiP2pConfig config) {
        logd("reinvokePersistentGroup, config.deviceAddress = " + config.deviceAddress);
        WifiP2pDevice dev = fetchCurrentDeviceDetails(config);

        boolean join = dev.isGroupOwner();
        String ssid = mWifiNative.p2pGetSsid(dev.deviceAddress);
        if (DBG) logd("target ssid is " + ssid + " join:" + join);

        if (join && dev.isGroupLimit()) {
            if (DBG) logd("target device reaches group limit.");

            // if the target group has reached the limit,
            // try group formation.
            join = false;
        } else if (join) {
            int netId = mGroups.getNetworkId(dev.deviceAddress, ssid);
            if (DBG) logd("reinvokePersistentGroup, netId = " + netId);
            if (netId >= 0) {
                ///M: ALPS00605482+ALPS00657537: can't use p2pGroupAdd when peer is GO and had ever formed @{
                // Skip WPS and start 4way handshake immediately.
                //if (!mWifiNative.p2pGroupAdd(netId)) {
                logd("reinvokePersistentGroup, call p2pReinvoke - netId = " + netId + " deviceAddress = " + dev.deviceAddress);
                if (!mWifiNative.p2pReinvoke(netId, dev.deviceAddress)) {
                    return false;
                }
                ///@}
                return true;
            }
        }

        if (!join && dev.isDeviceLimit()) {
            loge("target device reaches the device limit.");
            return false;
        }

        if (!join && dev.isInvitationCapable()) {
            int netId = WifiP2pGroup.PERSISTENT_NET_ID;
            if (DBG) logd("reinvokePersistentGroup, netId = " + netId);
            if (config.netId >= 0) {
                if (config.deviceAddress.equals(mGroups.getOwnerAddr(config.netId))) {
                    netId = config.netId;
                }
            } else {
                netId = mGroups.getNetworkId(dev.deviceAddress);
            }
            if (netId < 0) {
                netId = getNetworkIdFromClientList(dev.deviceAddress);
            }
            if (DBG) logd("netId related with " + dev.deviceAddress + " = " + netId);
            if (netId >= 0) {
                logd("reinvokePersistentGroup, call p2pReinvoke - netId = " + netId + " deviceAddress = " + dev.deviceAddress);
                // Invoke the persistent group.
                if (mWifiNative.p2pReinvoke(netId, dev.deviceAddress)) {
                    // Save network id. It'll be used when an invitation result event is received.
                    config.netId = netId;
                    return true;
                } else {
                    loge("p2pReinvoke() failed, update networks");
                    logd("reinvokePersistentGroup, call updatePersistentNetworks - RELOAD");
                    updatePersistentNetworks(RELOAD);
                    return false;
                }
            }
        }

        return false;
    }

    /**
     * Return the network id of the group owner profile which has the p2p client with
     * the specified device address in it's client list.
     * If more than one persistent group of the same address is present in its client
     * lists, return the first one.
     *
     * @param deviceAddress p2p device address.
     * @return the network id. if not found, return -1.
     */
    private int getNetworkIdFromClientList(String deviceAddress) {
        if (deviceAddress == null) return -1;

        Collection<WifiP2pGroup> groups = mGroups.getGroupList();
        for (WifiP2pGroup group : groups) {
            int netId = group.getNetworkId();
            String[] p2pClientList = getClientList(netId);
            if (p2pClientList == null) continue;
            for (String client : p2pClientList) {
                if (deviceAddress.equalsIgnoreCase(client)) {
                    return netId;
                }
            }
        }
        return -1;
    }

    /**
     * Return p2p client list associated with the specified network id.
     * @param netId network id.
     * @return p2p client list. if not found, return null.
     */
    private String[] getClientList(int netId) {
        String p2pClients = mWifiNative.getNetworkVariable(netId, "p2p_client_list");
        if (p2pClients == null) {
            return null;
        }
        return p2pClients.split(" ");
    }

    /**
     * Remove the specified p2p client from the specified profile.
     * @param netId network id of the profile.
     * @param addr p2p client address to be removed.
     * @param isRemovable if true, remove the specified profile if its client list becomes empty.
     * @return whether removing the specified p2p client is successful or not.
     */
    private boolean removeClientFromList(int netId, String addr, boolean isRemovable) {
        logd("removeClientFromList, netId = " + netId + " addr = " + addr + " isRemovable = " + isRemovable);
        StringBuilder modifiedClientList =  new StringBuilder();
        String[] currentClientList = getClientList(netId);
        boolean isClientRemoved = false;
        if (currentClientList != null) {
            for (String client : currentClientList) {
                if (!client.equalsIgnoreCase(addr)) {
                    modifiedClientList.append(" ");
                    modifiedClientList.append(client);
                } else {
                    isClientRemoved = true;
                }
            }
        }
        if (modifiedClientList.length() == 0 && isRemovable) {
            // the client list is empty. so remove it.
            if (DBG) logd("Remove unknown network");
            mGroups.remove(netId);
            return true;
        }

        if (!isClientRemoved) {
            // specified p2p client is not found. already removed.
            return false;
        }

        if (DBG) logd("Modified client list: " + modifiedClientList);
        if (modifiedClientList.length() == 0) {
            modifiedClientList.append("\"\"");
        }
        mWifiNative.setNetworkVariable(netId,
                "p2p_client_list", modifiedClientList.toString());
        mWifiNative.saveConfig();
        return true;
    }

    private void setWifiP2pInfoOnGroupFormation(InetAddress serverInetAddress) {
        logd("setWifiP2pInfoOnGroupFormation, serverInetAddress = " + serverInetAddress + ", isGroupOwner = " + mGroup.isGroupOwner());
        mWifiP2pInfo.groupFormed = true;
        mWifiP2pInfo.isGroupOwner = mGroup.isGroupOwner();
        mWifiP2pInfo.groupOwnerAddress = serverInetAddress;
    }

    // M: return if it is fast connect GC or not
    private boolean resetWifiP2pInfo() {
        logd("resetWifiP2pInfo");

        ///M: clear fast connect group 
        if( mFastConnectInfo!=null && mFastConnectInfo.networkId >=0){
            logd("clear netId="+mFastConnectInfo.networkId);
            mGroups.remove(mFastConnectInfo.networkId);
        }
        
        mWifiP2pInfo.groupFormed = false;
        mWifiP2pInfo.isGroupOwner = false;
        mWifiP2pInfo.groupOwnerAddress = null;
        ///M: ALPS01212893: for poor link, wifi p2p stop Tx all traffic @{
        sendP2pTxBroadcast(false);
        ///@}
        ///M: NFC fast connect @{
        return setFastConnectInfoOnGroupTermination();
        ///@}
    }

    private String getDeviceName(String deviceAddress) {
        logd("getDeviceName, deviceAddress = " + deviceAddress);
        WifiP2pDevice d = mPeers.get(deviceAddress);
        if (d != null) {
                logd("getDeviceName, d.deviceName = " + d.deviceName);
                return d.deviceName;
        }
        logd("getDeviceName, d is null");
        //Treat the address as name if there is no match
        return deviceAddress;
    }

    private String getPersistedDeviceName() {
        String deviceName = Settings.Global.getString(mContext.getContentResolver(),
                Settings.Global.WIFI_P2P_DEVICE_NAME);
        if (deviceName == null) {
            /* We use the 4 digits of the ANDROID_ID to have a friendly
             * default that has low likelihood of collision with a peer */
            String id = Settings.Secure.getString(mContext.getContentResolver(),
                    Settings.Secure.ANDROID_ID);
            return "Android_" + id.substring(0,4);
        }
        return deviceName;
    }

    private boolean setAndPersistDeviceName(String devName) {
        logd("setAndPersistDeviceName, devName = " + devName);
        if (devName == null) return false;

        if (!mWifiNative.setDeviceName(devName)) {
            loge("Failed to set device name " + devName);
            return false;
        }

        mThisDevice.deviceName = devName;
        mWifiNative.setP2pSsidPostfix("-" + mThisDevice.deviceName);

        Settings.Global.putString(mContext.getContentResolver(),
                Settings.Global.WIFI_P2P_DEVICE_NAME, devName);
        sendThisDeviceChangedBroadcast();
        return true;
    }

    private boolean setWfdInfo(WifiP2pWfdInfo wfdInfo) {
        logd("setWfdInfo");
        boolean success;

        if (!wfdInfo.isWfdEnabled()) {
            success = mWifiNative.setWfdEnable(false);
        } else {
            success =
                mWifiNative.setWfdEnable(true)
                && mWifiNative.setWfdDeviceInfo(wfdInfo.getDeviceInfoHex());
        }

        if (!success) {
            loge("Failed to set wfd properties, Device Info part");
            return false;
        }

        //M: ALPS01255052: UIBC in WFD IE
        if (0 != wfdInfo.getExtendedCapability()) {
            success = mWifiNative.setWfdExtCapability(wfdInfo.getExtCapaHex());
        }

        if (!success) {
            loge("Failed to set wfd properties, Extended Capability part");
            return false;
        }

        mThisDevice.wfdInfo = wfdInfo;
        sendThisDeviceChangedBroadcast();
        return true;
    }

    private void initializeP2pSettings() {
        logd("initializeP2pSettings");
        mWifiNative.setPersistentReconnect(true);
        mThisDevice.deviceName = getPersistedDeviceName();
        mWifiNative.setDeviceName(mThisDevice.deviceName);
        // DIRECT-XY-DEVICENAME (XY is randomly generated)
        mWifiNative.setP2pSsidPostfix("-" + mThisDevice.deviceName);
        mWifiNative.setDeviceType(mThisDevice.primaryDeviceType);
        // Supplicant defaults to using virtual display with display
        // which refers to a remote display. Use physical_display
        mWifiNative.setConfigMethods("virtual_push_button physical_display keypad");
        // STA has higher priority over P2P
        mWifiNative.setConcurrencyPriority("sta");

        mThisDevice.deviceAddress = mWifiNative.p2pGetDeviceAddress();
        updateThisDevice(WifiP2pDevice.AVAILABLE);
        if (DBG) logd("DeviceAddress: " + mThisDevice.deviceAddress);

        mClientInfoList.clear();
        mWifiNative.p2pFlush();
        mWifiNative.p2pServiceFlush();
        mServiceTransactionId = 0;
        mServiceDiscReqId = null;

        String countryCode = Settings.Global.getString(mContext.getContentResolver(),
                Settings.Global.WIFI_COUNTRY_CODE);
        if (countryCode != null && !countryCode.isEmpty()) {
            mP2pStateMachine.sendMessage(SET_COUNTRY_CODE, countryCode);
        }

        logd("initializeP2pSettings, call updatePersistentNetworks - RELOAD");
        updatePersistentNetworks(RELOAD);
    }

    private void updateThisDevice(int status) {
        logd("updateThisDevice, status = " + status);
        mThisDevice.status = status;
        sendThisDeviceChangedBroadcast();
    }

    private void handleGroupCreationFailure() {
        logd("handleGroupCreationFailure");
        /*M: ALPS00918679: fast connect GC mode won't trigger normal scan */
        boolean isFastConnGC = resetWifiP2pInfo();
        logd("handleGroupCreationFailure, isFastConnGC = " + isFastConnGC);
        mNetworkInfo.setDetailedState(NetworkInfo.DetailedState.FAILED, null, null);
        sendP2pConnectionChangedBroadcast();

        // Remove only the peer we failed to connect to so that other devices discovered
        // that have not timed out still remain in list for connection
        logd("handleGroupCreationFailure, call mPeers.remove(mPeersLostDuringConnection)");
        boolean peersChanged = mPeers.remove(mPeersLostDuringConnection);
        logd("handleGroupCreationFailure, peersChanged = " + peersChanged);

        ///M: fix peer hadn't been searched but group formed failure. MR2 will false alarm! @{
        if (mPeers.containsPeer(mSavedPeerConfig.deviceAddress)) {
            logd("handleGroupCreationFailure, call mPeers.remove - mSavedPeerConfig.deviceAddress = " + mSavedPeerConfig.deviceAddress);
            if (mPeers.remove(mSavedPeerConfig.deviceAddress) != null) {
                peersChanged = true;
            }
        }
        ///@}
        
        if (peersChanged) {
            sendPeersChangedBroadcast();
        }

        mPeersLostDuringConnection.clear();
        mServiceDiscReqId = null;
        /*M: ALPS00918679: fast connect GC mode won't trigger normal scan */
        if (false == isFastConnGC) {
            sendMessage(WifiP2pManager.DISCOVER_PEERS);
        }

        /*M: ALPS01000415: case #17-7,#18-1 -> Wifi AP not reconnect problem */
        if (mTempoarilyDisconnectedWifi) {
            mWifiChannel.sendMessage(WifiP2pService.DISCONNECT_WIFI_REQUEST, 0);
            mTempoarilyDisconnectedWifi = false;
        }        
    }

    private void handleGroupRemoved() {
        logd("handleGroupRemoved");

        Collection <WifiP2pDevice> devices = mGroup.getClientList();
        boolean changed = false;
        for (WifiP2pDevice d : mPeers.getDeviceList()) {
            if (devices.contains(d) || mGroup.getOwner().equals(d)) {
                d.status = WifiP2pDevice.AVAILABLE;
                changed = true;
            }
        }

        logd("handleGroupRemoved, changed = " + changed + " isGroupOwner = " + mGroup.isGroupOwner());

        if (mGroup.isGroupOwner()) {
            stopDhcpServer(mGroup.getInterface());
        } else {
            ///M: add judge for fast connect @{
            if (mDhcpStateMachine != null) {
                if (DBG) logd("stop DHCP client");
                mDhcpStateMachine.sendMessage(DhcpStateMachine.CMD_STOP_DHCP);
                mDhcpStateMachine.doQuit();
                if (!NetworkUtils.stopDhcp(mInterface)) {
                    loge("Failed to stop dhcp on " + mInterface);
                } else {
                    logd("Stop dhcp successfully!");
                }
                mDhcpStateMachine = null;
            }
            ///@}
        }

        try {
            mNwService.clearInterfaceAddresses(mGroup.getInterface());
        } catch (Exception e) {
            loge("Failed to clear addresses " + e);
        }
        NetworkUtils.resetConnections(mGroup.getInterface(), NetworkUtils.RESET_ALL_ADDRESSES);

        // Clear any timeout that was set. This is essential for devices
        // that reuse the main p2p interface for a created group.
        mWifiNative.setP2pGroupIdle(mGroup.getInterface(), 0);

        mGroup = null;
        mWifiNative.p2pFlush();
        logd("handleGroupRemoved, call mPeers.remove(mPeersLostDuringConnection)");
        if (mPeers.remove(mPeersLostDuringConnection)) sendPeersChangedBroadcast();
        mPeersLostDuringConnection.clear();
        mServiceDiscReqId = null;
        if (changed) sendPeersChangedBroadcast();

        if (mTempoarilyDisconnectedWifi) {
            mWifiChannel.sendMessage(WifiP2pService.DISCONNECT_WIFI_REQUEST, 0);
            mTempoarilyDisconnectedWifi = false;
        }

        //M: add for ALPS00489161
        mGroupCreatedEntirely = false;
   }

    //State machine initiated requests can have replyTo set to null indicating
    //there are no recipients, we ignore those reply actions
    private void replyToMessage(Message msg, int what) {
        if (msg.replyTo == null) return;
        Message dstMsg = obtainMessage(msg);
        dstMsg.what = what;
        mReplyChannel.replyToMessage(msg, dstMsg);
    }

    private void replyToMessage(Message msg, int what, int arg1) {
        if (msg.replyTo == null) return;
        Message dstMsg = obtainMessage(msg);
        dstMsg.what = what;
        dstMsg.arg1 = arg1;
        mReplyChannel.replyToMessage(msg, dstMsg);
    }

    private void replyToMessage(Message msg, int what, Object obj) {
        if (msg.replyTo == null) return;
        Message dstMsg = obtainMessage(msg);
        dstMsg.what = what;
        dstMsg.obj = obj;
        mReplyChannel.replyToMessage(msg, dstMsg);
    }

    /* arg2 on the source message has a hash code that needs to be retained in replies
     * see WifiP2pManager for details */
    private Message obtainMessage(Message srcMsg) {
        Message msg = Message.obtain();
        msg.arg2 = srcMsg.arg2;
        return msg;
    }

    @Override
    protected void logd(String s) {
        //Slog.d(TAG, s);
        Log.d(TAG, s);
    }

    @Override
    protected void loge(String s) {
        //Slog.e(TAG, s);
        Log.e(TAG, s);
    }

    /**
     * Update service discovery request to wpa_supplicant.
     */
    private boolean updateSupplicantServiceRequest() {
        clearSupplicantServiceRequest();

        StringBuffer sb = new StringBuffer();
        for (ClientInfo c: mClientInfoList.values()) {
            int key;
            WifiP2pServiceRequest req;
            for (int i=0; i < c.mReqList.size(); i++) {
                req = c.mReqList.valueAt(i);
                if (req != null) {
                    sb.append(req.getSupplicantQuery());
                }
            }
        }

        if (sb.length() == 0) {
            return false;
        }

        mServiceDiscReqId = mWifiNative.p2pServDiscReq("00:00:00:00:00:00", sb.toString());
        if (mServiceDiscReqId == null) {
            return false;
        }
        return true;
    }

    /**
     * Clear service discovery request in wpa_supplicant
     */
    private void clearSupplicantServiceRequest() {
        if (mServiceDiscReqId == null) return;

        mWifiNative.p2pServDiscCancelReq(mServiceDiscReqId);
        mServiceDiscReqId = null;
    }

    /* TODO: We could track individual service adds separately and avoid
     * having to do update all service requests on every new request
     */
    private boolean addServiceRequest(Messenger m, WifiP2pServiceRequest req) {
        clearClientDeadChannels();
        ClientInfo clientInfo = getClientInfo(m, true);
        if (clientInfo == null) {
            return false;
        }

        ++mServiceTransactionId;
        //The Wi-Fi p2p spec says transaction id should be non-zero
        if (mServiceTransactionId == 0) ++mServiceTransactionId;
        req.setTransactionId(mServiceTransactionId);
        clientInfo.mReqList.put(mServiceTransactionId, req);

        if (mServiceDiscReqId == null) {
            return true;
        }

        return updateSupplicantServiceRequest();
    }

    private void removeServiceRequest(Messenger m, WifiP2pServiceRequest req) {
        ClientInfo clientInfo = getClientInfo(m, false);
        if (clientInfo == null) {
            return;
        }

        //Application does not have transaction id information
        //go through stored requests to remove
        boolean removed = false;
        for (int i=0; i<clientInfo.mReqList.size(); i++) {
            if (req.equals(clientInfo.mReqList.valueAt(i))) {
                removed = true;
                clientInfo.mReqList.removeAt(i);
                break;
            }
        }

        if (!removed) return;

        if (clientInfo.mReqList.size() == 0 && clientInfo.mServList.size() == 0) {
            if (DBG) logd("remove client information from framework");
            mClientInfoList.remove(clientInfo.mMessenger);
        }

        if (mServiceDiscReqId == null) {
            return;
        }

        updateSupplicantServiceRequest();
    }

    private void clearServiceRequests(Messenger m) {

        ClientInfo clientInfo = getClientInfo(m, false);
        if (clientInfo == null) {
            return;
        }

        if (clientInfo.mReqList.size() == 0) {
            return;
        }

        clientInfo.mReqList.clear();

        if (clientInfo.mServList.size() == 0) {
            if (DBG) logd("remove channel information from framework");
            mClientInfoList.remove(clientInfo.mMessenger);
        }

        if (mServiceDiscReqId == null) {
            return;
        }

        updateSupplicantServiceRequest();
    }

    private boolean addLocalService(Messenger m, WifiP2pServiceInfo servInfo) {
        clearClientDeadChannels();
        ClientInfo clientInfo = getClientInfo(m, true);
        if (clientInfo == null) {
            return false;
        }

        if (!clientInfo.mServList.add(servInfo)) {
            return false;
        }

        if (!mWifiNative.p2pServiceAdd(servInfo)) {
            clientInfo.mServList.remove(servInfo);
            return false;
        }

        return true;
    }

    private void removeLocalService(Messenger m, WifiP2pServiceInfo servInfo) {
        ClientInfo clientInfo = getClientInfo(m, false);
        if (clientInfo == null) {
            return;
        }

        mWifiNative.p2pServiceDel(servInfo);

        clientInfo.mServList.remove(servInfo);
        if (clientInfo.mReqList.size() == 0 && clientInfo.mServList.size() == 0) {
            if (DBG) logd("remove client information from framework");
            mClientInfoList.remove(clientInfo.mMessenger);
        }
    }

    private void clearLocalServices(Messenger m) {
        ClientInfo clientInfo = getClientInfo(m, false);
        if (clientInfo == null) {
            return;
        }

        for (WifiP2pServiceInfo servInfo: clientInfo.mServList) {
            mWifiNative.p2pServiceDel(servInfo);
        }

        clientInfo.mServList.clear();
        if (clientInfo.mReqList.size() == 0) {
            if (DBG) logd("remove client information from framework");
            mClientInfoList.remove(clientInfo.mMessenger);
        }
    }

    private void clearClientInfo(Messenger m) {
        logd("clearClientInfo");
        clearLocalServices(m);
        clearServiceRequests(m);
    }

    /**
     * Send the service response to the WifiP2pManager.Channel.
     *
     * @param resp
     */
    private void sendServiceResponse(WifiP2pServiceResponse resp) {
        for (ClientInfo c : mClientInfoList.values()) {
            WifiP2pServiceRequest req = c.mReqList.get(resp.getTransactionId());
            if (req != null) {
                Message msg = Message.obtain();
                msg.what = WifiP2pManager.RESPONSE_SERVICE;
                msg.arg1 = 0;
                msg.arg2 = 0;
                msg.obj = resp;
                try {
                    c.mMessenger.send(msg);
                } catch (RemoteException e) {
                    if (DBG) logd("detect dead channel");
                    clearClientInfo(c.mMessenger);
                    return;
                }
            }
        }
    }

    /**
     * We dont get notifications of clients that have gone away.
     * We detect this actively when services are added and throw
     * them away.
     *
     * TODO: This can be done better with full async channels.
     */
    private void clearClientDeadChannels() {
        logd("clearClientDeadChannels");
        ArrayList<Messenger> deadClients = new ArrayList<Messenger>();

        for (ClientInfo c : mClientInfoList.values()) {
            Message msg = Message.obtain();
            msg.what = WifiP2pManager.PING;
            msg.arg1 = 0;
            msg.arg2 = 0;
            msg.obj = null;
            try {
                c.mMessenger.send(msg);
            } catch (RemoteException e) {
                if (DBG) logd("detect dead channel");
                deadClients.add(c.mMessenger);
            }
        }

        for (Messenger m : deadClients) {
            clearClientInfo(m);
        }
    }

    /**
     * Return the specified ClientInfo.
     * @param m Messenger
     * @param createIfNotExist if true and the specified channel info does not exist,
     * create new client info.
     * @return the specified ClientInfo.
     */
    private ClientInfo getClientInfo(Messenger m, boolean createIfNotExist) {
        ClientInfo clientInfo = mClientInfoList.get(m);

        if (clientInfo == null && createIfNotExist) {
            if (DBG) logd("add a new client");
            clientInfo = new ClientInfo(m);
            mClientInfoList.put(m, clientInfo);
        }

        return clientInfo;
    }

    ///M:@{
    /*M: ALPS00677009: broadcast the group removed reason*/
    private void sendP2pConnectionChangedBroadcast(P2pStatus reason) {
        logd("sendP2pConnectionChangedBroadcast, reason = " + reason);
        if (DBG) logd("sending p2p connection changed broadcast, reason = " + reason + ", mGroup: " + mGroup);
        Intent intent = new Intent(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION);
        intent.addFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY_BEFORE_BOOT
                | Intent.FLAG_RECEIVER_REPLACE_PENDING);
        intent.putExtra(WifiP2pManager.EXTRA_WIFI_P2P_INFO, new WifiP2pInfo(mWifiP2pInfo));
        intent.putExtra(WifiP2pManager.EXTRA_NETWORK_INFO, new NetworkInfo(mNetworkInfo));
        intent.putExtra(WifiP2pManager.EXTRA_WIFI_P2P_GROUP, new WifiP2pGroup(mGroup));
        if (P2pStatus.NO_COMMON_CHANNEL == reason) {
            intent.putExtra("reason=", 7);
        } else {
            intent.putExtra("reason=", -1);
        }
        mGroupRemoveReason = P2pStatus.UNKNOWN;
        mContext.sendStickyBroadcastAsUser(intent, UserHandle.ALL);
        mWifiChannel.sendMessage(WifiP2pService.P2P_CONNECTION_CHANGED,
                new NetworkInfo(mNetworkInfo));
    }

    //ALPS01212893: for poor link: wifi p2p Tx broadcast
    private void sendP2pTxBroadcast(boolean bStart) {
        logd("sendP2pTxBroadcast, bStart = " + bStart);
        if (DBG) logd("sending p2p Tx broadcast: " + bStart);
        Intent intent = new Intent("com.mediatek.wifi.p2p.Tx");
        intent.addFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY_BEFORE_BOOT
                | Intent.FLAG_RECEIVER_REPLACE_PENDING);
        intent.putExtra("start", bStart);
        mContext.sendStickyBroadcastAsUser(intent, UserHandle.ALL);
    }

    // return if it is fast connect GC or not
    private boolean setFastConnectInfoOnGroupTermination() {
        logd("setFastConnectInfoOnGroupTermination");
        ///M: ALPS01000113: to notofy native layer Beam+ is working  @{
        if (true == mBeamPlusStart) {
            mBeamPlusStart = false;
            mWifiNative.p2pBeamPlus(0); //0: stop
        }
        ///@}
        //wifi direct fast connect group terminate
        if (mRestartFastConnectAsGc) {
            mGoFastConnectEnaled = false;
            mRestartFastConnectAsGc = false;
            return true;

        } else if (mRestartFastConnectAsGo) {
            mGcFastConnectEnaled = false;
            mFoundGo = false;
            mFoundGoTimeOut = false;
            mRestartFastConnectAsGo = false;
            return false;

        } else {
            mGcFastConnectEnaled = false;
            mGoFastConnectEnaled = false;
            mFoundGo = false;
            mFoundGoTimeOut = false;
            mRestartFastConnectAsGc = false;
            mRestartFastConnectAsGo = false;
            return false;

        }
    }

    public void setFastConnectInfo(int role) {
        logd("setFastConnectInfo, role = " + role);
        ///M: ALPS01000113: to notofy native layer Beam+ is working  @{
        mBeamPlusStart = true;
        mWifiNative.p2pBeamPlus(1); //1: start        
    	///@}    
    	int id = mWifiNative.addNetwork();
    	mFastConnectInfo.networkId = id;
    	mWifiNative.setNetworkVariable(id, "ssid", "\"" + mFastConnectInfo.ssid + "\"");
    	mWifiNative.setNetworkVariable(id, "key_mgmt", "WPA-PSK");
    	mWifiNative.setNetworkVariable(id, "psk", "\"" + mFastConnectInfo.psk + "\"");
    	mWifiNative.setNetworkVariable(id, "proto", "RSN");
    	mWifiNative.setNetworkVariable(id, "pairwise", "CCMP");
    	mWifiNative.setNetworkVariable(id, "auth_alg", "OPEN");
    	mWifiNative.setNetworkVariable(id, "disabled", "2");

        logd("setFastConnectInfo, networkId = " + mFastConnectInfo.networkId + " ssid = " + mFastConnectInfo.ssid);

        if (WifiP2pManager.FAST_CONNECT_AS_GO == role) {
            logd("setFastConnectInfo, role is WifiP2pManager.FAST_CONNECT_AS_GO");
            mWifiNative.setNetworkVariable(id, "mode", "3");
            mWifiNative.p2pSetBssid(id, mThisDeviceAddress);
            sendMessage(WifiP2pManager.DISCOVER_PEERS);
        } else {
            logd("setFastConnectInfo, role is WifiP2pManager.FAST_CONNECT_AS_GC");
            mWifiNative.setNetworkVariable(id, "mode", "0");
            mWifiNative.p2pSetBssid(id, mFastConnectInfo.deviceAddress);
            
            //mWifiNative.setNetworkVariable(id, "bssid", mFastConnectInfo.deviceAddress);
            sendMessage(WifiP2pManager.FAST_DISCOVER_PEERS);
        }
        logd("setFastConnectInfo(): role = " + role + "\n FastConnectInfo = " + mFastConnectInfo);
        sendMessage(role);
    }

    private WifiP2pDevice p2pGoGetSta(WifiP2pDevice p2pDev, String p2pMAC) {
        if (null==p2pMAC || null==p2pDev) {
            loge("gc or gc mac is null");
            return null;
        }

        p2pDev.deviceAddress = p2pMAC;

        String p2pSta = mWifiNative.p2pGoGetSta(p2pMAC);
        
        if (null == p2pSta)    return p2pDev;

        String[] tokens= p2pSta.split("\n");
        for (String token : tokens) {
            if (token.startsWith("p2p_device_name=")) {
                String[] nameValue = token.split("=");
                p2pDev.deviceName = nameValueAssign(nameValue, p2pDev.deviceName);
            } else if (token.startsWith("p2p_primary_device_type=")) {
                String[] nameValue = token.split("=");
                p2pDev.primaryDeviceType = nameValueAssign(nameValue, p2pDev.primaryDeviceType);
            } else if (token.startsWith("p2p_group_capab=")) {
                String[] nameValue = token.split("=");
                p2pDev.groupCapability = nameValueAssign(nameValue, p2pDev.groupCapability);
            } else if (token.startsWith("p2p_dev_capab=")) {
                String[] nameValue = token.split("=");
                p2pDev.deviceCapability = nameValueAssign(nameValue, p2pDev.deviceCapability);
            }  else if (token.startsWith("p2p_config_methods=")) {
                String[] nameValue = token.split("=");
                p2pDev.wpsConfigMethodsSupported = nameValueAssign(nameValue, p2pDev.wpsConfigMethodsSupported);
            }
        }//for

        return p2pDev;
    }
    
    private String nameValueAssign(String[] nameValue, String string) {
        if (null==nameValue || 2!=nameValue.length) {
            return null;
        } else {
            return nameValue[1];
        }
    }

    private int nameValueAssign(String[] nameValue, int integer) {
        if (null==nameValue || 2!=nameValue.length) {
            return 0;
        } else {
            if (null != nameValue[1]) {
                return WifiP2pDevice.parseHex(nameValue[1]);
            } else {
                return 0;
            }
        }
    }

    private void setWifiOn_WifiAPOff() {
        logd("setWifiOn_WifiAPOff");
        if (null == mWifiManager) {
            mWifiManager = (WifiManager)mContext.getSystemService(Context.WIFI_SERVICE);
        }

        int wifiApState = mWifiManager.getWifiApState();
        if ((wifiApState == WifiManager.WIFI_AP_STATE_ENABLING) ||
                (wifiApState == WifiManager.WIFI_AP_STATE_ENABLED)) {
            mWifiManager.setWifiApEnabled(null, false);
        }
        mWifiManager.setWifiEnabled(true); 
    }
    
    } // end of class P2pStateMachine
    ///@}

    /**
     * Information about a particular client and we track the service discovery requests
     * and the local services registered by the client.
     */
    private class ClientInfo {

        /*
         * A reference to WifiP2pManager.Channel handler.
         * The response of this request is notified to WifiP2pManager.Channel handler
         */
        private Messenger mMessenger;

        /*
         * A service discovery request list.
         */
        private SparseArray<WifiP2pServiceRequest> mReqList;

        /*
         * A local service information list.
         */
        private List<WifiP2pServiceInfo> mServList;

        private ClientInfo(Messenger m) {
            mMessenger = m;
            mReqList = new SparseArray();
            mServList = new ArrayList<WifiP2pServiceInfo>();
        }
    }
}
