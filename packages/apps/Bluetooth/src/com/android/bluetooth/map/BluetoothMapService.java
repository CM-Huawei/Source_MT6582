/*
* Copyright (C) 2013 Samsung System LSI
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

package com.android.bluetooth.map;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import javax.obex.ServerSession;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.IBluetooth;
import android.bluetooth.IBluetoothMap;
import android.bluetooth.BluetoothUuid;
import android.bluetooth.BluetoothMap;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.PowerManager;
import android.os.ParcelUuid;
import android.text.TextUtils;
import android.util.Log;
import android.provider.Settings;
import android.content.IntentFilter;
import android.content.BroadcastReceiver;

import com.android.bluetooth.R;
import com.android.bluetooth.Utils;
import com.android.bluetooth.btservice.AdapterService;
import com.android.bluetooth.btservice.ProfileService;
import com.android.bluetooth.btservice.ProfileService.IProfileServiceBinder;


public class BluetoothMapService extends ProfileService {
    private static final String TAG = "[MAP]BluetoothMapService";

    /**
     * To enable MAP DEBUG/VERBOSE logging - run below cmd in adb shell, and
     * restart com.android.bluetooth process. only enable DEBUG log:
     * "setprop log.tag.BluetoothMapService DEBUG"; enable both VERBOSE and
     * DEBUG log: "setprop log.tag.BluetoothMapService VERBOSE"
     */

    public static final boolean DEBUG = true;

    public static final boolean VERBOSE = true;

    /**
     * Intent indicating incoming obex authentication request which is from
     * PCE(Carkit)
     */
    public static final String AUTH_CHALL_ACTION = "com.android.bluetooth.map.authchall";

    /**
     * Intent indicating timeout for user confirmation, which is sent to
     * BluetoothMapActivity
     */
    public static final String USER_CONFIRM_TIMEOUT_ACTION =
            "com.android.bluetooth.map.userconfirmtimeout";

    /**
     * Intent Extra name indicating session key which is sent from
     * BluetoothMapActivity
     */
    public static final String EXTRA_SESSION_KEY = "com.android.bluetooth.map.sessionkey";

    public static final String THIS_PACKAGE_NAME = "com.android.bluetooth";

    public static final int MSG_SERVERSESSION_CLOSE = 5000;

    public static final int MSG_SESSION_ESTABLISHED = 5001;

    public static final int MSG_SESSION_DISCONNECTED = 5002;

    public static final int MSG_OBEX_AUTH_CHALL = 5003;

    private static final String BLUETOOTH_PERM = android.Manifest.permission.BLUETOOTH;

    private static final String BLUETOOTH_ADMIN_PERM = android.Manifest.permission.BLUETOOTH_ADMIN;

    private static final int START_LISTENER = 1;

    private static final int USER_TIMEOUT = 2;

    private static final int DISCONNECT_MAP = 3;

    private PowerManager.WakeLock mWakeLock = null;

    private BluetoothAdapter mAdapter;

    private SocketAcceptThread mAcceptThread = null;

    private BluetoothMapAuthenticator mAuth = null;

    private BluetoothMapObexServer mMapServer;

    private ServerSession mServerSession = null;

    private BluetoothMnsObexClient mBluetoothMnsObexClient = null;

    private BluetoothServerSocket mServerSocket = null;

    private BluetoothSocket mConnSocket = null;

    private BluetoothDevice mRemoteDevice = null;

    private static String sRemoteDeviceName = null;

    private volatile boolean mInterrupted;

    private int mState;

    private boolean isWaitingAuthorization = false;

    // package and class name to which we send intent to check message access access permission
    private static final String ACCESS_AUTHORITY_PACKAGE = "com.android.settings";
    private static final String ACCESS_AUTHORITY_CLASS =
        "com.android.settings.bluetooth.BluetoothPermissionRequest";

    private static final ParcelUuid[] MAP_UUIDS = {
        BluetoothUuid.MAP,
        BluetoothUuid.MNS,
    };

    public BluetoothMapService() {
        mState = BluetoothMap.STATE_DISCONNECTED;
    }

    private void startRfcommSocketListener() {
        if (DEBUG) Log.d(TAG, "[startRfcommSocketListener] Map Service startRfcommSocketListener");

        if (mAcceptThread == null) {
            mAcceptThread = new SocketAcceptThread();
            mAcceptThread.setName("BluetoothMapAcceptThread");
            mAcceptThread.start();
        }
    }

    private final boolean initSocket() {
        if (DEBUG) Log.d(TAG, "[initSocket] Map Service initSocket");

        boolean initSocketOK = false;
        final int CREATE_RETRY_TIME = 10;

        // It's possible that create will fail in some cases. retry for 10 times
        for (int i = 0; (i < CREATE_RETRY_TIME) && !mInterrupted; i++) {
            initSocketOK = true;
            try {
                // It is mandatory for MSE to support initiation of bonding and
                // encryption.
                mServerSocket = mAdapter.listenUsingEncryptedRfcommWithServiceRecord
                    ("MAP SMS/MMS", BluetoothUuid.MAS.getUuid());

            } catch (IOException e) {
                Log.e(TAG, "[initSocket] Error create RfcommServerSocket " + e.toString());
                initSocketOK = false;
            }
            if (!initSocketOK) {
                // Need to break out of this loop if BT is being turned off.
                if (mAdapter == null) break;
                int state = mAdapter.getState();
                if ((state != BluetoothAdapter.STATE_TURNING_ON) &&
                    (state != BluetoothAdapter.STATE_ON)) {
                    Log.w(TAG, "[initSocket] initServerSocket failed as BT is (being) turned off");
                    break;
                }
                try {
                    if (VERBOSE) Log.v(TAG, "[initSocket] wait 300 ms");
                    Thread.sleep(300);
                } catch (InterruptedException e) {
                    Log.e(TAG, "[initSocket] socketAcceptThread thread was interrupted (3)");
                }
            } else {
                break;
            }
        }
        if (mInterrupted) {
            initSocketOK = false;
            // close server socket to avoid resource leakage
            closeServerSocket();
        }

        if (initSocketOK) {
            if (VERBOSE) Log.v(TAG, "[initSocket] Succeed to create listening socket ");

        } else {
            Log.e(TAG, "[initSocket] Error to create listening socket after " + CREATE_RETRY_TIME + " try");
        }
        return initSocketOK;
    }

    private final synchronized void closeServerSocket() {
        // exit SocketAcceptThread early
        if (mServerSocket != null) {
            try {
                // this will cause mServerSocket.accept() return early with IOException
                mServerSocket.close();
                mServerSocket = null;
            } catch (IOException ex) {
                Log.e(TAG, "[closeServerSocket] Close Server Socket error: " + ex);
            }
        }
    }
    private final synchronized void closeConnectionSocket() {
        if (mConnSocket != null) {
            try {
                mConnSocket.close();
                mConnSocket = null;
            } catch (IOException e) {
                Log.e(TAG, "[closeConnectionSocket] Close Connection Socket error: " + e.toString());
            }
        }
    }

    private final void closeService() {
        if (DEBUG) Log.d(TAG, "[closeService] MAP Service closeService in");

        // exit initSocket early
        mInterrupted = true;
        closeServerSocket();

        if (mAcceptThread != null) {
            try {
                mAcceptThread.shutdown();
                mAcceptThread.join();
                mAcceptThread = null;
            } catch (InterruptedException ex) {
                Log.w(TAG, "[closeService] mAcceptThread close error" + ex);
            }
        }

        if (mWakeLock != null) {
            mWakeLock.release();
            mWakeLock = null;
        }

        if (mServerSession != null) {
            mServerSession.close();
            mServerSession = null;
        }

        if (mBluetoothMnsObexClient != null) {
            mBluetoothMnsObexClient.shutdown();
            mBluetoothMnsObexClient = null;
        }

        closeConnectionSocket();

        if (mSessionStatusHandler != null) {
            mSessionStatusHandler.removeCallbacksAndMessages(null);
        }
        isWaitingAuthorization = false;

        if (VERBOSE) Log.v(TAG, "[closeService] MAP Service closeService out");
    }

    private final void startObexServerSession() throws IOException {
        if (DEBUG) Log.d(TAG, "[startObexServerSession] Map Service startObexServerSession");

        // acquire the wakeLock before start Obex transaction thread
        if (mWakeLock == null) {
            PowerManager pm = (PowerManager)getSystemService(Context.POWER_SERVICE);
            mWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
                    "StartingObexMapTransaction");
            mWakeLock.setReferenceCounted(false);
            mWakeLock.acquire();
        }

        mBluetoothMnsObexClient = new BluetoothMnsObexClient(this, mRemoteDevice);
        mMapServer = new BluetoothMapObexServer(mSessionStatusHandler, this,
                                                mBluetoothMnsObexClient);
        synchronized (this) {
            // We need to get authentication now that obex server is up
            mAuth = new BluetoothMapAuthenticator(mSessionStatusHandler);
            mAuth.setChallenged(false);
            mAuth.setCancelled(false);
        }
        // setup RFCOMM transport
        BluetoothMapRfcommTransport transport = new BluetoothMapRfcommTransport(mConnSocket);
        mServerSession = new ServerSession(transport, mMapServer, mAuth);
        setState(BluetoothMap.STATE_CONNECTED);
        if (VERBOSE) {
            Log.v(TAG, "[startObexServerSession] success!");
        }
    }

    private void stopObexServerSession() {
        if (DEBUG) Log.d(TAG, "[stopObexServerSession] MAP Service stopObexServerSession");

        // Release the wake lock if obex transaction is over
        if (mWakeLock != null) {
            mWakeLock.release();
            mWakeLock = null;
        }

        if (mServerSession != null) {
            mServerSession.close();
            mServerSession = null;
        }

        mAcceptThread = null;

        if(mBluetoothMnsObexClient != null) {
            mBluetoothMnsObexClient.shutdown();
            mBluetoothMnsObexClient = null;
        }
        closeConnectionSocket();

        // Last obex transaction is finished, we start to listen for incoming
        // connection again
        if (mAdapter.isEnabled()) {
            startRfcommSocketListener();
        }
        setState(BluetoothMap.STATE_DISCONNECTED);
    }



    /**
     * A thread that runs in the background waiting for remote rfcomm
     * connect.Once a remote socket connected, this thread shall be
     * shutdown.When the remote disconnect,this thread shall run again waiting
     * for next request.
     */
    private class SocketAcceptThread extends Thread {

        private boolean stopped = false;

        @Override
        public void run() {
            BluetoothServerSocket serverSocket;
            if (mServerSocket == null) {
                if (!initSocket()) {
                    return;
                }
            }

            while (!stopped) {
                try {
                    if (DEBUG) Log.d(TAG, "[SocketAcceptThread] Accepting socket connection...");
                    serverSocket = mServerSocket;
                    if(serverSocket == null) {
                        Log.w(TAG, "[SocketAcceptThread] mServerSocket is null");
                        break;
                    }
                    mConnSocket = serverSocket.accept();
                    if (DEBUG) Log.d(TAG, "[SocketAcceptThread] Accepted socket connection...");
                    synchronized (BluetoothMapService.this) {
                        if (mConnSocket == null) {
                            Log.w(TAG, "[SocketAcceptThread] mConnSocket is null");
                            break;
                        }
                        mRemoteDevice = mConnSocket.getRemoteDevice();
                    }
                    if (mRemoteDevice == null) {
                        Log.i(TAG, "[SocketAcceptThread] getRemoteDevice() = null");
                        break;
                    }

                    sRemoteDeviceName = mRemoteDevice.getName();
                    // In case getRemoteName failed and return null
                    if (TextUtils.isEmpty(sRemoteDeviceName)) {
                        sRemoteDeviceName = getString(R.string.defaultname);
                    }
                    boolean trust = mRemoteDevice.getTrustState();
                    if (DEBUG) Log.d(TAG, "[SocketAcceptThread] GetTrustState() = " + trust);


                    if (trust) {
                        try {
                            if (DEBUG) Log.d(TAG, "[SocketAcceptThread] incoming connection accepted from: "
                                + sRemoteDeviceName + " automatically as trusted device");
                            startObexServerSession();
                        } catch (IOException ex) {
                            Log.e(TAG, "[SocketAcceptThread] catch exception starting obex server session"
                                    + ex.toString());
                        }
                    } else {
                        Intent intent = new
                            Intent(BluetoothDevice.ACTION_CONNECTION_ACCESS_REQUEST);
                        intent.setClassName(ACCESS_AUTHORITY_PACKAGE, ACCESS_AUTHORITY_CLASS);
                        intent.putExtra(BluetoothDevice.EXTRA_ACCESS_REQUEST_TYPE,
                                        BluetoothDevice.REQUEST_TYPE_MESSAGE_ACCESS);
                        intent.putExtra(BluetoothDevice.EXTRA_DEVICE, mRemoteDevice);

                        isWaitingAuthorization = true;
                        sendBroadcast(intent, BLUETOOTH_ADMIN_PERM);

                        if (DEBUG) Log.d(TAG, "[SocketAcceptThread] waiting for authorization for connection from: "
                                + sRemoteDeviceName);

                    }
                    stopped = true; // job done ,close this thread;
                } catch (IOException ex) {
                    stopped=true;
                    if (VERBOSE) Log.v(TAG, "[SocketAcceptThread] Accept exception: " + ex.toString());
                }
            }
        }

        void shutdown() {
            stopped = true;
            interrupt();
        }
    }

    private final Handler mSessionStatusHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            if (VERBOSE) Log.v(TAG, "[mSessionStatusHandler]: got msg=" + msg.what);

            switch (msg.what) {
                case START_LISTENER:
                    if (mAdapter.isEnabled()) {
                        startRfcommSocketListener();
                    }
                    break;
                case USER_TIMEOUT:
                    Intent intent = new Intent(BluetoothDevice.ACTION_CONNECTION_ACCESS_CANCEL);
                    intent.setClassName(ACCESS_AUTHORITY_PACKAGE, ACCESS_AUTHORITY_CLASS);
                    intent.putExtra(BluetoothDevice.EXTRA_ACCESS_REQUEST_TYPE,
                                    BluetoothDevice.REQUEST_TYPE_MESSAGE_ACCESS);
                    sendBroadcast(intent);
                    isWaitingAuthorization = false;
                    stopObexServerSession();
                    break;
                case MSG_SERVERSESSION_CLOSE:
                    stopObexServerSession();
                    break;
                case MSG_SESSION_ESTABLISHED:
                    break;
                case MSG_SESSION_DISCONNECTED:
                    // handled elsewhere
                    break;
                case DISCONNECT_MAP:
                    disconnectMap((BluetoothDevice)msg.obj);
                    break;
                default:
                    break;
            }
        }
    };


   public int getState() {
        return mState;
    }

    public BluetoothDevice getRemoteDevice() {
        return mRemoteDevice;
    }
    private void setState(int state) {
        setState(state, BluetoothMap.RESULT_SUCCESS);
    }

    private synchronized void setState(int state, int result) {
        if (state != mState) {
            if (DEBUG) Log.d(TAG, "[setState] Map state " + mState + " -> " + state + ", result = "
                    + result);
            int prevState = mState;
            mState = state;
            Intent intent = new Intent(BluetoothMap.ACTION_CONNECTION_STATE_CHANGED);
            intent.putExtra(BluetoothProfile.EXTRA_PREVIOUS_STATE, prevState);
            intent.putExtra(BluetoothProfile.EXTRA_STATE, mState);
            intent.putExtra(BluetoothDevice.EXTRA_DEVICE, mRemoteDevice);
            sendBroadcast(intent, BLUETOOTH_PERM);
            AdapterService s = AdapterService.getAdapterService();
            if (s != null) {
                s.onProfileConnectionStateChanged(mRemoteDevice, BluetoothProfile.MAP,
                        mState, prevState);
            }
        }
    }

    public static String getRemoteDeviceName() {
        return sRemoteDeviceName;
    }

    public boolean disconnect(BluetoothDevice device) {
        mSessionStatusHandler.sendMessage(mSessionStatusHandler.obtainMessage(DISCONNECT_MAP, 0, 0, device));
        return true;
    }

    public boolean disconnectMap(BluetoothDevice device) {
        boolean result = false;
        if (DEBUG) Log.d(TAG, "[disconnectMap] enter");
        if (getRemoteDevice().equals(device)) {
            switch (mState) {
                case BluetoothMap.STATE_CONNECTED:
                    if (mServerSession != null) {
                        mServerSession.close();
                        mServerSession = null;
                    }
                    if(mBluetoothMnsObexClient != null) {
                        mBluetoothMnsObexClient.shutdown();
                        mBluetoothMnsObexClient = null;
                    }
                    closeConnectionSocket();

                    setState(BluetoothMap.STATE_DISCONNECTED, BluetoothMap.RESULT_CANCELED);
                    result = true;
                    break;
                default:
                    break;
                }
        }
        return result;
    }

    public List<BluetoothDevice> getConnectedDevices() {
        List<BluetoothDevice> devices = new ArrayList<BluetoothDevice>();
        synchronized(this) {
            if (mState == BluetoothMap.STATE_CONNECTED && mRemoteDevice != null) {
                devices.add(mRemoteDevice);
            }
        }
        return devices;
    }

    public List<BluetoothDevice> getDevicesMatchingConnectionStates(int[] states) {
        List<BluetoothDevice> deviceList = new ArrayList<BluetoothDevice>();
        Set<BluetoothDevice> bondedDevices = mAdapter.getBondedDevices();
        int connectionState;
        synchronized (this) {
            for (BluetoothDevice device : bondedDevices) {
                ParcelUuid[] featureUuids = device.getUuids();
                if (!BluetoothUuid.containsAnyUuid(featureUuids, MAP_UUIDS)) {
                    continue;
                }
                connectionState = getConnectionState(device);
                for(int i = 0; i < states.length; i++) {
                    if (connectionState == states[i]) {
                        deviceList.add(device);
                    }
                }
            }
        }
        return deviceList;
    }

    public int getConnectionState(BluetoothDevice device) {
        synchronized(this) {
            if (getState() == BluetoothMap.STATE_CONNECTED && getRemoteDevice().equals(device)) {
                return BluetoothProfile.STATE_CONNECTED;
            } else {
                return BluetoothProfile.STATE_DISCONNECTED;
            }
        }
    }

    public boolean setPriority(BluetoothDevice device, int priority) {
        Settings.Global.putInt(getContentResolver(),
            Settings.Global.getBluetoothMapPriorityKey(device.getAddress()),
            priority);
        if (DEBUG) Log.d(TAG, "[setPriority] Saved priority " + device + " = " + priority);
        return true;
    }

    public int getPriority(BluetoothDevice device) {
        int priority = Settings.Global.getInt(getContentResolver(),
            Settings.Global.getBluetoothMapPriorityKey(device.getAddress()),
            BluetoothProfile.PRIORITY_UNDEFINED);
        return priority;
    }

    @Override
    protected IProfileServiceBinder initBinder() {
        return new BluetoothMapBinder(this);
    }

    @Override
    protected boolean start() {
        if (DEBUG) Log.d(TAG, "start()");

        IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothDevice.ACTION_CONNECTION_ACCESS_REPLY);
        filter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        try {
            registerReceiver(mMapReceiver, filter);
        } catch (Exception e) {
            Log.w(TAG,"[start] Unable to register map receiver",e);
        }
        mInterrupted = false;
        mAdapter = BluetoothAdapter.getDefaultAdapter();
        // start RFCOMM listener
        mSessionStatusHandler.sendMessage(mSessionStatusHandler
                .obtainMessage(START_LISTENER));
        return true;
    }

    @Override
    protected boolean stop() {
        if (DEBUG) Log.d(TAG, "stop()");
        try {
            unregisterReceiver(mMapReceiver);
        } catch (Exception e) {
            Log.w(TAG,"[stop] Unable to unregister map receiver",e);
        }

        setState(BluetoothMap.STATE_DISCONNECTED, BluetoothMap.RESULT_CANCELED);
        closeService();
        return true;
    }

    public boolean cleanup()  {
        if (DEBUG) Log.d(TAG, "cleanup()");
        setState(BluetoothMap.STATE_DISCONNECTED, BluetoothMap.RESULT_CANCELED);
        closeService();
        return true;
    }

    private MapBroadcastReceiver mMapReceiver = new MapBroadcastReceiver();

    private class MapBroadcastReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (DEBUG) Log.d(TAG, "[MapBroadcastReceiver] onReceive");
            String action = intent.getAction();
            if (action.equals(BluetoothAdapter.ACTION_STATE_CHANGED)) {
                int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE,
                                               BluetoothAdapter.ERROR);
                if (state == BluetoothAdapter.STATE_TURNING_OFF) {
                    if (DEBUG) Log.d(TAG, "[MapBroadcastReceiver] STATE_TURNING_OFF");
                    // Release all resources
                    closeService();
                } else if (state == BluetoothAdapter.STATE_ON) {
                    if (DEBUG) Log.d(TAG, "[MapBroadcastReceiver] STATE_ON");
                    mInterrupted = false;
                    // start RFCOMM listener
                    mSessionStatusHandler.sendMessage(mSessionStatusHandler
                                  .obtainMessage(START_LISTENER));
                }
            } else if (action.equals(BluetoothDevice.ACTION_CONNECTION_ACCESS_REPLY)) {
                int requestType = intent.getIntExtra(BluetoothDevice.EXTRA_ACCESS_REQUEST_TYPE,
                                               BluetoothDevice.REQUEST_TYPE_PHONEBOOK_ACCESS);
                if (DEBUG) Log.d(TAG, "[MapBroadcastReceiver] Received ACTION_CONNECTION_ACCESS_REPLY:" +
                           requestType + ":" + isWaitingAuthorization);
                if ((!isWaitingAuthorization) ||
                    (requestType != BluetoothDevice.REQUEST_TYPE_MESSAGE_ACCESS)) {
                    // this reply is not for us
                    return;
                }

                isWaitingAuthorization = false;

                if (intent.getIntExtra(BluetoothDevice.EXTRA_CONNECTION_ACCESS_RESULT,
                                       BluetoothDevice.CONNECTION_ACCESS_NO) ==
                    BluetoothDevice.CONNECTION_ACCESS_YES) {
                    //bluetooth connection accepted by user
                    if (intent.getBooleanExtra(BluetoothDevice.EXTRA_ALWAYS_ALLOWED, false)) {
                        boolean result = mRemoteDevice.setTrust(true);
                        if (DEBUG) Log.d(TAG, "[MapBroadcastReceiver] setTrust() result=" + result);
                    }
                    try {
                        if (mConnSocket != null) {
                            // start obex server and rfcomm connection
                            startObexServerSession();
                        } else {
                            stopObexServerSession();
                        }
                    } catch (IOException ex) {
                        Log.e(TAG, "[MapBroadcastReceiver] Caught the error: " + ex.toString());
                    }
                } else {
                    stopObexServerSession();
                }
            }
        }
    };

    //Binder object: Must be static class or memory leak may occur
    /**
     * This class implements the IBluetoothMap interface - or actually it validates the
     * preconditions for calling the actual functionality in the MapService, and calls it.
     */
    private static class BluetoothMapBinder extends IBluetoothMap.Stub
        implements IProfileServiceBinder {
        private BluetoothMapService mService;

        private BluetoothMapService getService() {
            if (!Utils.checkCaller()) {
                Log.w(TAG,"[BluetoothMapBinder] MAP call not allowed for non-active user");
                return null;
            }

            if (mService != null && mService.isAvailable()) {
                mService.enforceCallingOrSelfPermission(BLUETOOTH_PERM, "Need BLUETOOTH permission");
                return mService;
            }
            return null;
        }

        BluetoothMapBinder(BluetoothMapService service) {
            if (VERBOSE) Log.v(TAG, "BluetoothMapBinder()");
            mService = service;
        }

        public boolean cleanup()  {
            mService = null;
            return true;
        }

        public int getState() {
            if (VERBOSE) Log.v(TAG, "[BluetoothMapBinder] getState()");
            BluetoothMapService service = getService();
            if (service == null) return BluetoothMap.STATE_DISCONNECTED;
            return getService().getState();
        }

        public BluetoothDevice getClient() {
            if (VERBOSE) Log.v(TAG, "[BluetoothMapBinder] getClient()");
            BluetoothMapService service = getService();
            if (service == null) return null;
            Log.v(TAG, "[BluetoothMapBinder] getClient() - returning " + service.getRemoteDevice());
            return service.getRemoteDevice();
        }

        public boolean isConnected(BluetoothDevice device) {
            if (VERBOSE) Log.v(TAG, "[BluetoothMapBinder] isConnected()");
            BluetoothMapService service = getService();
            if (service == null) return false;
            return service.getState() == BluetoothMap.STATE_CONNECTED && service.getRemoteDevice().equals(device);
        }

        public boolean connect(BluetoothDevice device) {
            if (VERBOSE) Log.v(TAG, "[BluetoothMapBinder] connect()");
            BluetoothMapService service = getService();
            if (service == null) return false;
            return false;
        }

        public boolean disconnect(BluetoothDevice device) {
            if (VERBOSE) Log.v(TAG, "[BluetoothMapBinder] disconnect()");
            BluetoothMapService service = getService();
            if (service == null) return false;
            return service.disconnect(device);
        }

        public List<BluetoothDevice> getConnectedDevices() {
            if (VERBOSE) Log.v(TAG, "[BluetoothMapBinder] getConnectedDevices()");
            BluetoothMapService service = getService();
            if (service == null) return new ArrayList<BluetoothDevice>(0);
            return service.getConnectedDevices();
        }

        public List<BluetoothDevice> getDevicesMatchingConnectionStates(int[] states) {
            if (VERBOSE) Log.v(TAG, "[BluetoothMapBinder] getDevicesMatchingConnectionStates()");
            BluetoothMapService service = getService();
            if (service == null) return new ArrayList<BluetoothDevice>(0);
            return service.getDevicesMatchingConnectionStates(states);
        }

        public int getConnectionState(BluetoothDevice device) {
            if (VERBOSE) Log.v(TAG, "[BluetoothMapBinder] getConnectionState()");
            BluetoothMapService service = getService();
            if (service == null) return BluetoothProfile.STATE_DISCONNECTED;
            return service.getConnectionState(device);
        }

        public boolean setPriority(BluetoothDevice device, int priority) {
            BluetoothMapService service = getService();
            if (service == null) return false;
            return service.setPriority(device, priority);
        }

        public int getPriority(BluetoothDevice device) {
            BluetoothMapService service = getService();
            if (service == null) return BluetoothProfile.PRIORITY_UNDEFINED;
            return service.getPriority(device);
        }
    };
}
