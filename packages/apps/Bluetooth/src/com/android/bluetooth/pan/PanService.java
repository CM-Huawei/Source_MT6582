/*
 * Copyright (C) 2012 The Android Open Source Project
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

package com.android.bluetooth.pan;

import android.app.Service;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothPan;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.BluetoothTetheringDataTracker;
import android.bluetooth.IBluetooth;
import android.bluetooth.IBluetoothPan;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.res.Resources.NotFoundException;
import android.net.ConnectivityManager;
import android.net.InterfaceConfiguration;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.net.NetworkInfo.State;
import android.net.NetworkStateTracker;
import android.net.NetworkUtils;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.INetworkManagementService;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.provider.Settings;
import android.util.Log;
import android.widget.Toast;

import com.android.bluetooth.btservice.ProfileService;
import com.android.bluetooth.Utils;
import com.android.internal.util.AsyncChannel;
import com.android.bluetooth.R;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


/**
 * Provides Bluetooth Pan Device profile, as a service in
 * the Bluetooth application.
 * @hide
 */
public class PanService extends ProfileService {
    private static final String TAG = "[BT][PAN][PanService]";
    private static final boolean DBG = true;

    private static final String BLUETOOTH_IFACE_ADDR_START= "192.168.44.1";
    private static final int BLUETOOTH_MAX_PAN_CONNECTIONS = 5;
    private static final int BLUETOOTH_PREFIX_LENGTH        = 24;

    private static final String ACTION_NETWORK_CONNECTION_STATE_CHANGED = "android.net.conn.CONNECTIVITY_CHANGE";

    private HashMap<BluetoothDevice, BluetoothPanDevice> mPanDevices;
    private ArrayList<String> mBluetoothIfaceAddresses;
    private int mMaxPanDevices;
    private String mPanIfName;
    private boolean mNativeAvailable;

    private static final int MESSAGE_CONNECT = 1;
    private static final int MESSAGE_DISCONNECT = 2;
    private static final int MESSAGE_CONNECT_STATE_CHANGED = 11;
    private static final int MESSAGE_DISPLAY_TOAST = 100;

    private static final String MESSAGE_DISPLAY = "show toast";

    private boolean mTetherOn = false;

    private boolean mLocalPanuConnected = false;

    AsyncChannel mTetherAc;

    static {
        classInitNative();
    }

    protected String getName() {
        return TAG;
    }

    public IProfileServiceBinder initBinder() {
        return new BluetoothPanBinder(this);
    }

    protected boolean start() {
        Log.d(TAG, "[start] enter!!!");
        mPanDevices = new HashMap<BluetoothDevice, BluetoothPanDevice>();
        mBluetoothIfaceAddresses = new ArrayList<String>();
        try {
            mMaxPanDevices = getResources().getInteger(
                                 com.android.internal.R.integer.config_max_pan_devices);
        } catch (NotFoundException e) {
            mMaxPanDevices = BLUETOOTH_MAX_PAN_CONNECTIONS;
        }
        Log.d(TAG, "[start] mMaxPanDevices : " + mMaxPanDevices);
        initializeNative();
        mNativeAvailable=true;

        ConnectivityManager cm = (ConnectivityManager) getSystemService(
                Context.CONNECTIVITY_SERVICE);
        cm.supplyMessenger(ConnectivityManager.TYPE_BLUETOOTH, new Messenger(mHandler));

        IntentFilter filter = new IntentFilter(ACTION_NETWORK_CONNECTION_STATE_CHANGED);
        registerReceiver(mReceiver, filter);

        return true;
    }

    protected boolean stop() {
        Log.d(TAG, "[stop] enter!!!");
        mHandler.removeCallbacksAndMessages(null);
        if (mTetherAc != null) {
            mTetherAc.disconnect();
            mTetherAc = null;
        }
        unregisterReceiver(mReceiver);
        return true;
    }

    protected boolean cleanup() {
        Log.d(TAG, "[cleanup] enter!!!");
        if (mNativeAvailable) {
            cleanupNative();
            mNativeAvailable=false;
        }
        if(mPanDevices != null) {
            List<BluetoothDevice> DevList = getConnectedDevices();
            for(BluetoothDevice dev : DevList) {
                handlePanDeviceStateChange(dev, mPanIfName, BluetoothProfile.STATE_DISCONNECTED,
                        BluetoothPan.LOCAL_PANU_ROLE, BluetoothPan.REMOTE_NAP_ROLE);
            }
            mPanDevices.clear();
        }
        if(mBluetoothIfaceAddresses != null) {
            mBluetoothIfaceAddresses.clear();
        }
        return true;
    }

    private BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            int size = getConnectedDevices().size();
			boolean isLocalPanu = isPanUOn();
            Log.d(TAG, "mReceiver mLocalPanuConnected : " + mLocalPanuConnected);
            Log.d(TAG, "mReceiver is local panu : " + isLocalPanu);
            if (mLocalPanuConnected && isLocalPanu) {
                ConnectivityManager cm =
                       (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
                State wifiState = cm.getNetworkInfo(ConnectivityManager.TYPE_WIFI).getState();
                Log.d(TAG, "[mReceiver] wifi state is : " + wifiState.toString());
                if (wifiState == State.CONNECTED) {
                    sendToastMessage(MESSAGE_DISPLAY_TOAST, PanService.this.getString(R.string.panu_disconnect_while_wifi_connected));
                    mLocalPanuConnected = false;
                    return;
                }
            }
        }
    };

    private final Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MESSAGE_CONNECT:
                {
                    Log.d(TAG, "[mHandler.handleMessage] message : MESSAGE_CONNECT!");
                    BluetoothDevice device = (BluetoothDevice) msg.obj;
                    handlePanDeviceStateChange(device, null, BluetoothProfile.STATE_CONNECTING,
                                                   BluetoothPan.LOCAL_PANU_ROLE, BluetoothPan.REMOTE_NAP_ROLE);
                    if (!connectPanNative(Utils.getByteAddress(device),
                            BluetoothPan.LOCAL_PANU_ROLE, BluetoothPan.REMOTE_NAP_ROLE)) {
                        Log.d(TAG, "[mHandler.handleMessage] connectPanNative return false!");
                        handlePanDeviceStateChange(device, null, BluetoothProfile.STATE_CONNECTING,
                                BluetoothPan.LOCAL_PANU_ROLE, BluetoothPan.REMOTE_NAP_ROLE);
                        handlePanDeviceStateChange(device, null,
                                BluetoothProfile.STATE_DISCONNECTED, BluetoothPan.LOCAL_PANU_ROLE,
                                BluetoothPan.REMOTE_NAP_ROLE);
                        break;
                    }
                }
                    break;
                case MESSAGE_DISCONNECT:
                {
                    Log.d(TAG, "[mHandler.handleMessage] message : MESSAGE_DISCONNECT!");
                    BluetoothDevice device = (BluetoothDevice) msg.obj;
                    if (!disconnectPanNative(Utils.getByteAddress(device))) {
                        Log.d(TAG, "[mHandler.handleMessage] disconnectPanNative return false!");
                        handlePanDeviceStateChange(device, mPanIfName,
                                BluetoothProfile.STATE_DISCONNECTING, BluetoothPan.LOCAL_PANU_ROLE,
                                BluetoothPan.REMOTE_NAP_ROLE);
                        handlePanDeviceStateChange(device, mPanIfName,
                                BluetoothProfile.STATE_DISCONNECTED, BluetoothPan.LOCAL_PANU_ROLE,
                                BluetoothPan.REMOTE_NAP_ROLE);
                        break;
                    }
                }
                    break;
                case MESSAGE_CONNECT_STATE_CHANGED:
                {
                    Log.d(TAG, "[mHandler.handleMessage] message : MESSAGE_CONNECT_STATE_CHANGED!");
                    ConnectState cs = (ConnectState)msg.obj;
                    BluetoothDevice device = getDevice(cs.addr);
                    // TBD get iface from the msg
                    if (DBG) {
                        Log.d(TAG, "MESSAGE_CONNECT_STATE_CHANGED : " + device + " state: " + cs.state);
                    }
                    handlePanDeviceStateChange(device, mPanIfName /* iface */,
                            convertHalState(cs.state), cs.local_role,  cs.remote_role);
                }
                break;
                case AsyncChannel.CMD_CHANNEL_FULL_CONNECTION:
                {
                    if (mTetherAc != null) {
                        mTetherAc.replyToMessage(msg,
                                AsyncChannel.CMD_CHANNEL_FULLY_CONNECTED,
                                AsyncChannel.STATUS_FULL_CONNECTION_REFUSED_ALREADY_CONNECTED);
                    } else {
                        mTetherAc = new AsyncChannel();
                        mTetherAc.connected(null, this, msg.replyTo);
                        mTetherAc.replyToMessage(msg, AsyncChannel.CMD_CHANNEL_FULLY_CONNECTED,
                                AsyncChannel.STATUS_SUCCESSFUL);
                    }
                }
                break;
                case AsyncChannel.CMD_CHANNEL_DISCONNECT:
                {
                    if (mTetherAc != null) {
                        mTetherAc.disconnect();
                        mTetherAc = null;
                    }
                }
                break;
            }
        }
    };

    /**
     * Handlers for incoming service calls
     */
    private static class BluetoothPanBinder extends IBluetoothPan.Stub
            implements IProfileServiceBinder {
        private PanService mService;
        public BluetoothPanBinder(PanService svc) {
            mService = svc;
        }
        public boolean cleanup() {
            mService = null;
            return true;
        }
        private PanService getService() {
            if (!Utils.checkCaller()) {
                Log.w(TAG,"Pan call not allowed for non-active user");
                return null;
            }

            if (mService  != null && mService.isAvailable()) {
                return mService;
            }
            return null;
        }
        public boolean connect(BluetoothDevice device) {
            PanService service = getService();
            if (service == null) return false;
            return service.connect(device);
        }
        public boolean disconnect(BluetoothDevice device) {
            PanService service = getService();
            if (service == null) return false;
            return service.disconnect(device);
        }
        public int getConnectionState(BluetoothDevice device) {
            PanService service = getService();
            if (service == null) return BluetoothPan.STATE_DISCONNECTED;
            return service.getConnectionState(device);
        }
        private boolean isPanNapOn() {
            PanService service = getService();
            if (service == null) return false;
            return service.isPanNapOn();
        }
        private boolean isPanUOn() {
            if(DBG) Log.d(TAG, "[BluetoothPanBinder][isPanUOn] isTetheringOn call getPanLocalRoleNative");
            PanService service = getService();
            return service.isPanUOn();
        }
        public boolean isTetheringOn() {
            // TODO(BT) have a variable marking the on/off state
            PanService service = getService();
            if (service == null) return false;
            return service.isTetheringOn();
        }
        public void setBluetoothTethering(boolean value) {
            PanService service = getService();
            if (service == null) return;
            Log.d(TAG, "[BluetoothPanBinder][setBluetoothTethering] value : " + value
                        +", mTetherOn: " + service.mTetherOn);
            service.setBluetoothTethering(value);
        }

        public List<BluetoothDevice> getConnectedDevices() {
            PanService service = getService();
            if (service == null) return new ArrayList<BluetoothDevice>(0);
            return service.getConnectedDevices();
        }

        public List<BluetoothDevice> getDevicesMatchingConnectionStates(int[] states) {
            PanService service = getService();
            if (service == null) return new ArrayList<BluetoothDevice>(0);
            return service.getDevicesMatchingConnectionStates(states);
        }
    };

    boolean connect(BluetoothDevice device) {
        ConnectivityManager cm =
            (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        State wifiState = cm.getNetworkInfo(ConnectivityManager.TYPE_WIFI).getState();
        Log.d(TAG, "[connect] wifi state is : " + wifiState.toString());
        if (wifiState == State.CONNECTED) {
            sendToastMessage(MESSAGE_DISPLAY_TOAST, getString(R.string.panu_no_need_connect));
            return false;
        }
        enforceCallingOrSelfPermission(BLUETOOTH_PERM, "Need BLUETOOTH permission");
        if (getConnectionState(device) != BluetoothProfile.STATE_DISCONNECTED) {
            Log.e(TAG, "Pan Device not disconnected : " + device);
            return false;
        }
        Message msg = mHandler.obtainMessage(MESSAGE_CONNECT,device);
        mHandler.sendMessage(msg);
        return true;
    }

    boolean disconnect(BluetoothDevice device) {
        enforceCallingOrSelfPermission(BLUETOOTH_PERM, "Need BLUETOOTH permission");
        Message msg = mHandler.obtainMessage(MESSAGE_DISCONNECT,device);
        mHandler.sendMessage(msg);
        return true;
    }

    int getConnectionState(BluetoothDevice device) {
        BluetoothPanDevice panDevice = mPanDevices.get(device);
        if (panDevice == null) {
            Log.d(TAG, "[getConnectionState] device is not in list, and return BluetoothPan.STATE_DISCONNECTED");
            return BluetoothPan.STATE_DISCONNECTED;
        }
        return panDevice.mState;
    }

    boolean isPanNapOn() {
        if(DBG) Log.d(TAG, "[isPanNapOn] isTetheringOn call getPanLocalRoleNative");
        return (getPanLocalRoleNative() & BluetoothPan.LOCAL_NAP_ROLE) != 0;
    }
     boolean isPanUOn() {
        if(DBG) Log.d(TAG, "[isPanUOn] isTetheringOn call getPanLocalRoleNative");
        return (getPanLocalRoleNative() & BluetoothPan.LOCAL_PANU_ROLE) != 0;
    }
     boolean isTetheringOn() {
        // TODO(BT) have a variable marking the on/off state
        return mTetherOn;
    }

    void setBluetoothTethering(boolean value) {
        if(DBG) Log.d(TAG, "[setBluetoothTethering] value : " + value +", origin value(mTetherOn) : " + mTetherOn);
        enforceCallingOrSelfPermission(BLUETOOTH_ADMIN_PERM, "Need BLUETOOTH_ADMIN permission");
        if(mTetherOn != value) {
            //drop any existing panu or pan-nap connection when changing the tethering state
            mTetherOn = value;
            List<BluetoothDevice> DevList = getConnectedDevices();
            for(BluetoothDevice dev : DevList)
                disconnect(dev);
        }
    }

    List<BluetoothDevice> getConnectedDevices() {
        enforceCallingOrSelfPermission(BLUETOOTH_PERM, "Need BLUETOOTH permission");
        List<BluetoothDevice> devices = getDevicesMatchingConnectionStates(
                new int[] {BluetoothProfile.STATE_CONNECTED});
        return devices;
    }

    List<BluetoothDevice> getDevicesMatchingConnectionStates(int[] states) {
         enforceCallingOrSelfPermission(BLUETOOTH_PERM, "Need BLUETOOTH permission");
        List<BluetoothDevice> panDevices = new ArrayList<BluetoothDevice>();

        for (BluetoothDevice device: mPanDevices.keySet()) {
            int panDeviceState = getConnectionState(device);
            for (int state : states) {
                if (state == panDeviceState) {
                    panDevices.add(device);
                    break;
                }
            }
        }
        return panDevices;
    }

    static protected class ConnectState {
        public ConnectState(byte[] address, int state, int error, int local_role, int remote_role) {
            this.addr = address;
            this.state = state;
            this.error = error;
            this.local_role = local_role;
            this.remote_role = remote_role;
        }
        byte[] addr;
        int state;
        int error;
        int local_role;
        int remote_role;
    };

    /**
    * JNI callback
    * Connection state changed callback
    */
    private void onConnectStateChanged(byte[] address, int state, int error, int local_role,
            int remote_role) {
        if (DBG) {
            Log.d(TAG, "[onConnectStateChanged] address : " + address
                + ", state : " + state
                + ", error : " + error
                + ", local role: " + local_role
                + ", remote_role: " + remote_role);
        }
        Message msg = mHandler.obtainMessage(MESSAGE_CONNECT_STATE_CHANGED);
        msg.obj = new ConnectState(address, state, error, local_role, remote_role);
        mHandler.sendMessage(msg);
    }
    private void onControlStateChanged(int local_role, int state, int error, String ifname) {
        if (DBG)
            Log.d(TAG, "[onControlStateChanged] local_role : " + local_role
                    + ", state : " + state + ", error: " + error + ", ifname: " + ifname);
        if(error == 0)
            mPanIfName = ifname;
    }

    private static int convertHalState(int halState) {
        switch (halState) {
            case CONN_STATE_CONNECTED:
                return BluetoothProfile.STATE_CONNECTED;
            case CONN_STATE_CONNECTING:
                return BluetoothProfile.STATE_CONNECTING;
            case CONN_STATE_DISCONNECTED:
                return BluetoothProfile.STATE_DISCONNECTED;
            case CONN_STATE_DISCONNECTING:
                return BluetoothProfile.STATE_DISCONNECTING;
            default:
                Log.e(TAG, "bad pan connection state: " + halState);
                return BluetoothProfile.STATE_DISCONNECTED;
        }
    }

    void handlePanDeviceStateChange(BluetoothDevice device,
                                    String iface, int state, int local_role, int remote_role) {
        if(DBG) {
            Log.d(TAG, "[handlePanDeviceStateChange] device : " + device + ", iface: " + iface +
                    ", state : " + state + ", local_role:" + local_role + ", remote_role :" +
                    remote_role);
        }
        int prevState;
        String ifaceAddr = null;
        BluetoothPanDevice panDevice = mPanDevices.get(device);
        if (panDevice == null) {
            prevState = BluetoothProfile.STATE_DISCONNECTED;
        } else {
            prevState = panDevice.mState;
            ifaceAddr = panDevice.mIfaceAddr;
        }
        Log.d(TAG, "[handlePanDeviceStateChange] preState: " + prevState + " state: " + state);
        if (prevState == state) return;
        if (remote_role == BluetoothPan.LOCAL_PANU_ROLE) {
            if (state == BluetoothProfile.STATE_CONNECTED) {
                if((!mTetherOn)||(local_role == BluetoothPan.LOCAL_PANU_ROLE)){
                    Log.d(TAG,"[handlePanDeviceStateChange] BT tethering is off/Local role is PANU "+
                              "drop the connection");
                    disconnectPanNative(Utils.getByteAddress(device));
                    return;
                }
                Log.d(TAG, "[handlePanDeviceStateChange] LOCAL_NAP_ROLE:REMOTE_PANU_ROLE");
                ifaceAddr = enableTethering(iface);
                if (ifaceAddr == null) Log.e(TAG, "[handlePanDeviceStateChange] Error seting up tether interface");

            } else if (state == BluetoothProfile.STATE_DISCONNECTED) {
                if (ifaceAddr != null) {
                    mBluetoothIfaceAddresses.remove(ifaceAddr);
                    ifaceAddr = null;
                }
            }
        } else if (mTetherAc != null) {
            // PANU Role = reverse Tether
            Log.d(TAG, "[handlePanDeviceStateChange] LOCAL_PANU_ROLE:REMOTE_NAP_ROLE state = " +
                    state + ", prevState = " + prevState);
            if (state == BluetoothProfile.STATE_CONNECTED) {
                LinkProperties lp = new LinkProperties();
                lp.setInterfaceName(iface);
                mTetherAc.sendMessage(NetworkStateTracker.EVENT_NETWORK_CONNECTED, lp);
                mLocalPanuConnected = true;
           } else if (state == BluetoothProfile.STATE_DISCONNECTED &&
                   (prevState == BluetoothProfile.STATE_CONNECTED ||
                   prevState == BluetoothProfile.STATE_DISCONNECTING)) {
                LinkProperties lp = new LinkProperties();
                lp.setInterfaceName(iface);
                mTetherAc.sendMessage(NetworkStateTracker.EVENT_NETWORK_DISCONNECTED, lp);
                mLocalPanuConnected = false;
            }
        }

        if (panDevice == null) {
            Log.d(TAG, "[handlePanDeviceStateChange] panDevice not exist, new one and put into list!");
            panDevice = new BluetoothPanDevice(state, ifaceAddr, iface, local_role);
            mPanDevices.put(device, panDevice);
        } else {
            panDevice.mState = state;
            panDevice.mIfaceAddr = ifaceAddr;
            panDevice.mLocalRole = local_role;
            panDevice.mIface = iface;
        }

        /* Notifying the connection state change of the profile before sending the intent for
           connection state change, as it was causing a race condition, with the UI not being
           updated with the correct connection state. */
        Log.d(TAG, "[handlePanDeviceStateChange] Pan Device state : device : " + device + ", State :" +
                       prevState + " -> " + state);
        notifyProfileConnectionStateChanged(device, BluetoothProfile.PAN, state, prevState);
        Intent intent = new Intent(BluetoothPan.ACTION_CONNECTION_STATE_CHANGED);
        intent.putExtra(BluetoothDevice.EXTRA_DEVICE, device);
        intent.putExtra(BluetoothPan.EXTRA_PREVIOUS_STATE, prevState);
        intent.putExtra(BluetoothPan.EXTRA_STATE, state);
        intent.putExtra(BluetoothPan.EXTRA_LOCAL_ROLE, local_role);
        sendBroadcast(intent, BLUETOOTH_PERM);
    }

    // configured when we start tethering
    private String enableTethering(String iface) {
        if (DBG) Log.d(TAG, "[enableTethering] updateTetherState:" + iface);

        IBinder b = ServiceManager.getService(Context.NETWORKMANAGEMENT_SERVICE);
        INetworkManagementService service = INetworkManagementService.Stub.asInterface(b);
        ConnectivityManager cm =
            (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        String[] bluetoothRegexs = cm.getTetherableBluetoothRegexs();

        // bring toggle the interfaces
        String[] currentIfaces = new String[0];
        try {
            currentIfaces = service.listInterfaces();
        } catch (Exception e) {
            Log.e(TAG, "[enableTethering] Error listing Interfaces :" + e);
            return null;
        }

        boolean found = false;
        for (String currIface: currentIfaces) {
            if (currIface.equals(iface)) {
                found = true;
                break;
            }
        }
        Log.d(TAG, "[enableTethering] found : " + found);
        if (!found) return null;

        String address = createNewTetheringAddressLocked();
        if (address == null) {
            Log.d(TAG, "[enableTethering] address is null, just return!!!");
            return null;
        }

        InterfaceConfiguration ifcg = null;
        try {
            ifcg = service.getInterfaceConfig(iface);
            if (ifcg != null) {
                InetAddress addr = null;
                LinkAddress linkAddr = ifcg.getLinkAddress();
                if (linkAddr == null || (addr = linkAddr.getAddress()) == null ||
                        addr.equals(NetworkUtils.numericToInetAddress("0.0.0.0")) ||
                        addr.equals(NetworkUtils.numericToInetAddress("::0"))) {
                    addr = NetworkUtils.numericToInetAddress(address);
                }
                ifcg.setInterfaceUp();
                ifcg.setLinkAddress(new LinkAddress(addr, BLUETOOTH_PREFIX_LENGTH));
                ifcg.clearFlag("running");
                // TODO(BT) ifcg.interfaceFlags = ifcg.interfaceFlags.replace("  "," ");
                service.setInterfaceConfig(iface, ifcg);
                if (cm.tether(iface) != ConnectivityManager.TETHER_ERROR_NO_ERROR) {
                    Log.e(TAG, "[enableTethering] Error tethering " + iface);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "[enableTethering] Error configuring interface " + iface + ", : " + e);
            return null;
        }
        return address;
    }

    private String createNewTetheringAddressLocked() {
        if (getConnectedPanDevices().size() == mMaxPanDevices) {
            if (DBG) Log.d(TAG, "[createNewTetheringAddressLocked] Max PAN device connections reached");
            return null;
        }
        String address = BLUETOOTH_IFACE_ADDR_START;
        while (true) {
            if (mBluetoothIfaceAddresses.contains(address)) {
                String[] addr = address.split("\\.");
                Integer newIp = Integer.parseInt(addr[2]) + 1;
                address = address.replace(addr[2], newIp.toString());
            } else {
                break;
            }
        }
        mBluetoothIfaceAddresses.add(address);
        return address;
    }

    private List<BluetoothDevice> getConnectedPanDevices() {
        List<BluetoothDevice> devices = new ArrayList<BluetoothDevice>();

        for (BluetoothDevice device: mPanDevices.keySet()) {
            if (getPanDeviceConnectionState(device) == BluetoothProfile.STATE_CONNECTED) {
                devices.add(device);
            }
        }
        Log.d(TAG, "[getConnectedPanDevices] devices list size : " + devices.size());
        return devices;
    }

    private int getPanDeviceConnectionState(BluetoothDevice device) {
        BluetoothPanDevice panDevice = mPanDevices.get(device);
        if (panDevice == null) {
            return BluetoothProfile.STATE_DISCONNECTED;
        }
        return panDevice.mState;
    }

    /**
    * Which used to show toast on the screen
    * @param what message what
    *        toastMsg the string which will show on the screen
    */
    private void sendToastMessage(int what, String toastMsg) {
        Message msg = Message.obtain();
        msg.what = what;
        Bundle data = new Bundle();
        data.putString(MESSAGE_DISPLAY, toastMsg);
        msg.setData(data);
        mToastHandler.sendMessage(msg);
    }

    /**
    * Handler which used to show toast
    */
	private final Handler mToastHandler = new Handler() {

        private String mOldToastMsg;
        private Toast mToast;

        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            switch (msg.what) {
                case MESSAGE_DISPLAY_TOAST:
                    Bundle data = msg.getData();
                    String toastMsg = (data != null) ? data.getString(MESSAGE_DISPLAY) : null;
                    if (mOldToastMsg != null) {
                        if (mOldToastMsg.equals(toastMsg)) {
                            if (mToast == null) {
                                mToast = Toast.makeText(PanService.this, toastMsg, Toast.LENGTH_SHORT);
                            } else {
                                mToast.setText(toastMsg);
                                mToast.setDuration(Toast.LENGTH_SHORT);
                            }
                            mToast.show();
                        } else {
                            Toast.makeText(PanService.this, toastMsg, Toast.LENGTH_SHORT).show();
                        }
                    } else {
                        Toast.makeText(PanService.this, toastMsg, Toast.LENGTH_SHORT).show();
                    }
                    mOldToastMsg = toastMsg;
					break;
                default:
                    break;
            }
        }
    };

    private class BluetoothPanDevice {
        private int mState;
        private String mIfaceAddr;
        private String mIface;
        private int mLocalRole; // Which local role is this PAN device bound to

        BluetoothPanDevice(int state, String ifaceAddr, String iface, int localRole) {
            mState = state;
            mIfaceAddr = ifaceAddr;
            mIface = iface;
            mLocalRole = localRole;
        }
    }

    // Constants matching Hal header file bt_hh.h
    // bthh_connection_state_t
    private final static int CONN_STATE_CONNECTED = 0;
    private final static int CONN_STATE_CONNECTING = 1;
    private final static int CONN_STATE_DISCONNECTED = 2;
    private final static int CONN_STATE_DISCONNECTING = 3;

    private native static void classInitNative();
    private native void initializeNative();
    private native void cleanupNative();
    private native boolean connectPanNative(byte[] btAddress, int local_role, int remote_role);
    private native boolean disconnectPanNative(byte[] btAddress);
    private native boolean enablePanNative(int local_role);
    private native int getPanLocalRoleNative();

}
