/*
 * Copyright (C) 2010 The Android Open Source Project
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

package android.bluetooth;

import android.bluetooth.BluetoothPan;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Environment;
import android.util.Log;

import junit.framework.Assert;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class BluetoothLeTestUtils extends Assert {
	private static final String TAG = "BluetoothLeTestUtils";
	
    /** Timeout for enable/disable in ms. */
    private static final int ENABLE_DISABLE_TIMEOUT = 20000;
    /** Timeout for connecting/disconnecting a profile in ms. */
    private static final int CONNECT_DISCONNECT_PROFILE_TIMEOUT = 20000;
    /** Timeout to connect a profile proxy in ms. */
    private static final int CONNECT_PROXY_TIMEOUT = 5000;
    /** Time between polls in ms. */
    private static final int POLL_TIME = 100;

    static final int GATT_CLIENT_CONNECT_TIMEOUT      = 90000;
    static final int GATT_CLIENT_DISCONNECT_TIMEOUT   = 5000;
    static final int GATT_CLIENT_DISCOVERY_TIMEOUT    = 10000;
    static final int GATT_CLIENT_ATTRIBUTE_OP_TIMEOUT = 10000;
    
    private abstract class FlagReceiver extends BroadcastReceiver {
        private int mExpectedFlags = 0;
        private int mFiredFlags = 0;
        private long mCompletedTime = -1;

        public FlagReceiver(int expectedFlags) {
            mExpectedFlags = expectedFlags;
        }

        public int getFiredFlags() {
            synchronized (this) {
                return mFiredFlags;
            }
        }

        public long getCompletedTime() {
            synchronized (this) {
                return mCompletedTime;
            }
        }

        protected void setFiredFlag(int flag) {
            synchronized (this) {
                mFiredFlags |= flag;
                if ((mFiredFlags & mExpectedFlags) == mExpectedFlags) {
                    mCompletedTime = System.currentTimeMillis();
                }
            }
        }
    }

    private class BluetoothReceiver extends FlagReceiver {
        private static final int DISCOVERY_STARTED_FLAG = 1;
        private static final int DISCOVERY_FINISHED_FLAG = 1 << 1;
        private static final int SCAN_MODE_NONE_FLAG = 1 << 2;
        private static final int SCAN_MODE_CONNECTABLE_FLAG = 1 << 3;
        private static final int SCAN_MODE_CONNECTABLE_DISCOVERABLE_FLAG = 1 << 4;
        private static final int STATE_OFF_FLAG = 1 << 5;
        private static final int STATE_TURNING_ON_FLAG = 1 << 6;
        private static final int STATE_ON_FLAG = 1 << 7;
        private static final int STATE_TURNING_OFF_FLAG = 1 << 8;

        public BluetoothReceiver(int expectedFlags) {
            super(expectedFlags);
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            if (BluetoothAdapter.ACTION_DISCOVERY_STARTED.equals(intent.getAction())) {
                setFiredFlag(DISCOVERY_STARTED_FLAG);
            } else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(intent.getAction())) {
                setFiredFlag(DISCOVERY_FINISHED_FLAG);
            } else if (BluetoothAdapter.ACTION_SCAN_MODE_CHANGED.equals(intent.getAction())) {
                int mode = intent.getIntExtra(BluetoothAdapter.EXTRA_SCAN_MODE, -1);
                assertNotSame(-1, mode);
                switch (mode) {
                    case BluetoothAdapter.SCAN_MODE_NONE:
                        setFiredFlag(SCAN_MODE_NONE_FLAG);
                        break;
                    case BluetoothAdapter.SCAN_MODE_CONNECTABLE:
                        setFiredFlag(SCAN_MODE_CONNECTABLE_FLAG);
                        break;
                    case BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE:
                        setFiredFlag(SCAN_MODE_CONNECTABLE_DISCOVERABLE_FLAG);
                        break;
                }
            } else if (BluetoothAdapter.ACTION_STATE_CHANGED.equals(intent.getAction())) {
                int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, -1);
                assertNotSame(-1, state);
                switch (state) {
                    case BluetoothAdapter.STATE_OFF:
                        setFiredFlag(STATE_OFF_FLAG);
                        break;
                    case BluetoothAdapter.STATE_TURNING_ON:
                        setFiredFlag(STATE_TURNING_ON_FLAG);
                        break;
                    case BluetoothAdapter.STATE_ON:
                        setFiredFlag(STATE_ON_FLAG);
                        break;
                    case BluetoothAdapter.STATE_TURNING_OFF:
                        setFiredFlag(STATE_TURNING_OFF_FLAG);
                        break;
                }
            }
        }
    }

    /// intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE) -> connect to which device
    /// intent.getIntExtra(BluetoothProfile.EXTRA_STATE, -1)    -> current state
    private class GattClientProfileConnectReceiver extends ConnectProfileReceiver{
        public GattClientProfileConnectReceiver(BluetoothDevice device, int expectedFlags) {
	        super(device, BluetoothProfile.GATT, expectedFlags);
	        mConnectionAction = BluetoothLeStressTest.ACTION_CONNECTION_STATE_CHANGED;
        }        
    }

    private class GattClientDiscoveryReceiver extends FlagReceiver{
		private static final int STATE_DISCOVERED_FLAG = 1;

        public GattClientDiscoveryReceiver(int expectedFlags) {
            super(expectedFlags);
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            if(BluetoothLeStressTest.ACTION_DISCOVERY_END.equals(intent.getAction())){
				 setFiredFlag(STATE_DISCOVERED_FLAG);
			}else{
				writeOutput("GattClientDiscoveryReceiver - onReceive() - unexpected action:" + intent.getAction());
            }
        }
    }
    
    private class GattClientAttributeOpReceiver extends FlagReceiver{
		private static final int STATE_READ_FLAG        = 1;
        private static final int STATE_WRITE_FLAG       = 1 << 1;
        private static final int STATE_RELIABLE_EXECUTE = 1 << 2;
        private static final int STATE_COUNT_MATCH      = 1 << 3;
        
        private int mnExpectedCount = 0;
        private int mnRealCount = 0;
        
        public GattClientAttributeOpReceiver(int expectedFlags, int nExpectedCount) {
			super(expectedFlags);
			mnExpectedCount = nExpectedCount;
        }

		@Override
		public void onReceive(Context context, Intent intent) {
			if(BluetoothLeStressTest.ACTION_READ_ATTRIBUTE.equals(intent.getAction())){
				mnRealCount++;
				 setFiredFlag(STATE_READ_FLAG);
			}else if(BluetoothLeStressTest.ACTION_WRITE_ATTRIBUTE.equals(intent.getAction())){
				mnRealCount++;
			    setFiredFlag(STATE_WRITE_FLAG);
			}else if(BluetoothLeStressTest.ACTION_RELIABLE_EXECUTE.equals(intent.getAction())){
			    setFiredFlag(STATE_RELIABLE_EXECUTE);
			}else{
				writeOutput("GattClientAttributeOpReceiver - onReceive() - error: unexpected action " + intent.getAction());				
			}
			
			if(0 != mnExpectedCount){
				if(mnRealCount == mnExpectedCount){
				    setFiredFlag(STATE_COUNT_MATCH);					
				}
			
			    if(mnRealCount > mnExpectedCount)
				    writeOutput("GattClientAttributeOpReceiver - onReceive() - error: mnRealCount > mnExpectedCount");				
			}
		}
    }

    
    private class ConnectProfileReceiver extends FlagReceiver {
        private static final int STATE_DISCONNECTED_FLAG = 1;
        private static final int STATE_CONNECTING_FLAG = 1 << 1;
        private static final int STATE_CONNECTED_FLAG = 1 << 2;
        private static final int STATE_DISCONNECTING_FLAG = 1 << 3;

        private BluetoothDevice mDevice;
        private int mProfile;
        String mConnectionAction;

        public ConnectProfileReceiver(BluetoothDevice device, int profile, int expectedFlags) {
            super(expectedFlags);

            mDevice = device;
            mProfile = profile;

            switch (mProfile) {
                case BluetoothProfile.A2DP:
                    mConnectionAction = BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED;
                    break;
                case BluetoothProfile.HEADSET:
                    mConnectionAction = BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED;
                    break;
                case BluetoothProfile.INPUT_DEVICE:
                    mConnectionAction = BluetoothInputDevice.ACTION_CONNECTION_STATE_CHANGED;
                    break;
                case BluetoothProfile.PAN:
                    mConnectionAction = BluetoothPan.ACTION_CONNECTION_STATE_CHANGED;
                    break;
                default:
                    mConnectionAction = null;
            }
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            if (mConnectionAction != null && mConnectionAction.equals(intent.getAction())) {
                if (!mDevice.equals(intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE))) {
                    return;
                }

                int state = intent.getIntExtra(BluetoothProfile.EXTRA_STATE, -1);
                assertNotSame(-1, state);
                switch (state) {
                    case BluetoothProfile.STATE_DISCONNECTED:
                        setFiredFlag(STATE_DISCONNECTED_FLAG);
                        break;
                    case BluetoothProfile.STATE_CONNECTING:
                        setFiredFlag(STATE_CONNECTING_FLAG);
                        break;
                    case BluetoothProfile.STATE_CONNECTED:
                        setFiredFlag(STATE_CONNECTED_FLAG);
                        break;
                    case BluetoothProfile.STATE_DISCONNECTING:
                        setFiredFlag(STATE_DISCONNECTING_FLAG);
                        break;
                }
            }
        }
    }
    

    private BluetoothProfile.ServiceListener mServiceListener =
            new BluetoothProfile.ServiceListener() {
        @Override
        public void onServiceConnected(int profile, BluetoothProfile proxy) {
            synchronized (this) {
                switch (profile) {
                    case BluetoothProfile.A2DP:
                        mA2dp = (BluetoothA2dp) proxy;
                        break;
                    case BluetoothProfile.HEADSET:
                        mHeadset = (BluetoothHeadset) proxy;
                        break;
                    case BluetoothProfile.INPUT_DEVICE:
                        mInput = (BluetoothInputDevice) proxy;
                        break;
                    case BluetoothProfile.PAN:
                        mPan = (BluetoothPan) proxy;
                        break;
                }
            }
        }

        @Override
        public void onServiceDisconnected(int profile) {
            synchronized (this) {
                switch (profile) {
                    case BluetoothProfile.A2DP:
                        mA2dp = null;
                        break;
                    case BluetoothProfile.HEADSET:
                        mHeadset = null;
                        break;
                    case BluetoothProfile.INPUT_DEVICE:
                        mInput = null;
                        break;
                    case BluetoothProfile.PAN:
                        mPan = null;
                        break;
                }
            }
        }
    };

    private List<BroadcastReceiver> mReceivers = new ArrayList<BroadcastReceiver>();

    private BufferedWriter mOutputWriter;
    private String mTag;
    private String mOutputFile;

    private Context mContext;
    private BluetoothA2dp mA2dp = null;
    private BluetoothHeadset mHeadset = null;
    private BluetoothInputDevice mInput = null;
    private BluetoothPan mPan = null;

    /**
     * Creates a utility instance for testing Bluetooth.
     *
     * @param context The context of the application using the utility.
     * @param tag The log tag of the application using the utility.
     */
    public BluetoothLeTestUtils(Context context, String tag) {
        this(context, tag, null);
    }

    /**
     * Creates a utility instance for testing Bluetooth.
     *
     * @param context The context of the application using the utility.
     * @param tag The log tag of the application using the utility.
     * @param outputFile The path to an output file if the utility is to write results to a
     *        separate file.
     */
    public BluetoothLeTestUtils(Context context, String tag, String outputFile) {
        mContext = context;
        mTag = tag;
        mOutputFile = outputFile;

        if (mOutputFile == null) {
            mOutputWriter = null;
        } else {
            try {
                mOutputWriter = new BufferedWriter(new FileWriter(new File(
                        Environment.getExternalStorageDirectory(), mOutputFile), true));
            } catch (IOException e) {
                Log.w(mTag, "Test output file could not be opened", e);
                mOutputWriter = null;
            }
        }
    }

    /**
     * Closes the utility instance and unregisters any BroadcastReceivers.
     */
    public void close() {
        while (!mReceivers.isEmpty()) {
            mContext.unregisterReceiver(mReceivers.remove(0));
        }

        if (mOutputWriter != null) {
            try {
                mOutputWriter.close();
            } catch (IOException e) {
                Log.w(mTag, "Test output file could not be closed", e);
            }
        }
    }

    /**
     * Enables Bluetooth and checks to make sure that Bluetooth was turned on and that the correct
     * actions were broadcast.
     *
     * @param adapter The BT adapter.
     */
    public void enable(BluetoothAdapter adapter) {
        int mask = (BluetoothReceiver.STATE_TURNING_ON_FLAG | BluetoothReceiver.STATE_ON_FLAG);
        long start = -1;
        BluetoothReceiver receiver = getBluetoothReceiver(mask);

        int state = adapter.getState();
        switch (state) {
            case BluetoothAdapter.STATE_ON:
                assertTrue(adapter.isEnabled());
                removeReceiver(receiver);
                return;
            case BluetoothAdapter.STATE_TURNING_ON:
                assertFalse(adapter.isEnabled());
                mask = 0; // Don't check for received intents since we might have missed them.
                break;
            case BluetoothAdapter.STATE_OFF:
                assertFalse(adapter.isEnabled());
                start = System.currentTimeMillis();
                assertTrue(adapter.enable());
                break;
            case BluetoothAdapter.STATE_TURNING_OFF:
                start = System.currentTimeMillis();
                assertTrue(adapter.enable());
                break;
            default:
                removeReceiver(receiver);
                reportFail(String.format("enable invalid state: state=%d", state));
        }

        long s = System.currentTimeMillis();
        while (System.currentTimeMillis() - s < ENABLE_DISABLE_TIMEOUT) {
            state = adapter.getState();
            if (state == BluetoothAdapter.STATE_ON
                    && (receiver.getFiredFlags() & mask) == mask) {
                assertTrue(adapter.isEnabled());
                long finish = receiver.getCompletedTime();
                if (start != -1 && finish != -1) {
                    writeOutput(String.format("enable completed in %d ms", (finish - start)));
                } else {
                    writeOutput("enable completed");
                }
                removeReceiver(receiver);
                return;
            }
            sleep(POLL_TIME);
        }

        int firedFlags = receiver.getFiredFlags();
        removeReceiver(receiver);
        reportFail(String.format("enable timeout: state=%d (expected %d), flags=0x%x (expected 0x%x)",
                state, BluetoothAdapter.STATE_ON, firedFlags, mask));
    }

    /**
     * Disables Bluetooth and checks to make sure that Bluetooth was turned off and that the correct
     * actions were broadcast.
     *
     * @param adapter The BT adapter.
     */
    public void disable(BluetoothAdapter adapter) {
        int mask = (BluetoothReceiver.STATE_TURNING_OFF_FLAG | BluetoothReceiver.STATE_OFF_FLAG
                | BluetoothReceiver.SCAN_MODE_NONE_FLAG);
        long start = -1;
        BluetoothReceiver receiver = getBluetoothReceiver(mask);

        int state = adapter.getState();
        switch (state) {
            case BluetoothAdapter.STATE_OFF:
                assertFalse(adapter.isEnabled());
                removeReceiver(receiver);
                return;
            case BluetoothAdapter.STATE_TURNING_ON:
                assertFalse(adapter.isEnabled());
                start = System.currentTimeMillis();
                break;
            case BluetoothAdapter.STATE_ON:
                assertTrue(adapter.isEnabled());
                start = System.currentTimeMillis();
                assertTrue(adapter.disable());
                break;
            case BluetoothAdapter.STATE_TURNING_OFF:
                assertFalse(adapter.isEnabled());
                mask = 0; // Don't check for received intents since we might have missed them.
                break;
            default:
                removeReceiver(receiver);
                reportFail(String.format("disable invalid state: state=%d", state));
        }

        long s = System.currentTimeMillis();
        while (System.currentTimeMillis() - s < ENABLE_DISABLE_TIMEOUT) {
            state = adapter.getState();
            if (state == BluetoothAdapter.STATE_OFF
                    && (receiver.getFiredFlags() & mask) == mask) {
                assertFalse(adapter.isEnabled());
                long finish = receiver.getCompletedTime();
                if (start != -1 && finish != -1) {
                    writeOutput(String.format("disable completed in %d ms", (finish - start)));
                } else {
                    writeOutput("disable completed");
                }
                removeReceiver(receiver);
                return;
            }
            sleep(POLL_TIME);
        }

        int firedFlags = receiver.getFiredFlags();
        removeReceiver(receiver);
        reportFail(String.format("disable timeout: state=%d (expected %d), flags=0x%x (expected 0x%x)",
                state, BluetoothAdapter.STATE_OFF, firedFlags, mask));
    }

    public boolean isGattConnect(Context ctxt){
        BluetoothManager btManager = (BluetoothManager) ctxt.getSystemService(Context.BLUETOOTH_SERVICE);
        List<BluetoothDevice> listBtDevice = btManager.getConnectedDevices(BluetoothProfile.GATT);

        return null != listBtDevice && !listBtDevice.isEmpty();
    }
    
    public void gattClientConnect(Context ctxt, BluetoothDevice device, BluetoothGatt gattClient){
    	int mask = ConnectProfileReceiver.STATE_CONNECTED_FLAG;

    	/// check pre-condition
        assertNotNull(ctxt);
        assertNotNull(device);
        assertNotNull(gattClient);
    	
    	/// check BT is enabled or not
    	BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (!adapter.isEnabled()) {
        	reportFail("gattClientConnect() bluetooth not enabled");
        }
        
        /// check gatt is not connected
        if(isGattConnect(ctxt)){
        	return;
        }
        
        /// try to connect and wait response
        GattClientProfileConnectReceiver receiver = getGattClientConnectProfileReceiver(device, mask);

        /// start to do connect
        long start = System.currentTimeMillis();
        assertTrue(gattClient.connect());
        
        /// check the result
        while (System.currentTimeMillis() - start < GATT_CLIENT_CONNECT_TIMEOUT) {
            if ((receiver.getFiredFlags() & mask) == mask) {
            	long finish = receiver.getCompletedTime();
                if (start != -1 && finish != -1) {
                    writeOutput(String.format("gattClientConnect completed in %d ms", (finish - start)));
                } else {
                    writeOutput("gattClientConnect completed");
                }            	
                removeReceiver(receiver);
                return;
            }
            sleep(POLL_TIME);
        }
        
        /// throw exception for timeout
        int firedFlags = receiver.getFiredFlags();
        removeReceiver(receiver);
        reportFail(String.format("gattClientConnect() timeout: (expected %d), flags=0x%x " + 
                           "(expected 0x%x)", ConnectProfileReceiver.STATE_CONNECTED_FLAG, firedFlags, mask));
    }
    
    public void gattClientDisconnect(Context ctxt, BluetoothDevice device, BluetoothGatt gattClient){
    	int mask = ConnectProfileReceiver.STATE_DISCONNECTED_FLAG;

    	/// check pre-condition
        assertNotNull(ctxt);
        assertNotNull(device);
        assertNotNull(gattClient);

    	/// check BT is enabled or not
    	BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (!adapter.isEnabled()) {
        	reportFail("gattClientDisconnect() bluetooth not enabled");
        }
        
        /// check gatt is connected
        if(!isGattConnect(ctxt)){
        	return;
        }

        /// try to connect and wait response
        GattClientProfileConnectReceiver receiver = getGattClientConnectProfileReceiver(device, mask);

        /// start to do disconnect
        long start = System.currentTimeMillis();
        gattClient.disconnect();

        /// check the result
        while (System.currentTimeMillis() - start < GATT_CLIENT_DISCONNECT_TIMEOUT) {
            if ((receiver.getFiredFlags() & mask) == mask) {
            	long finish = receiver.getCompletedTime();
                if (start != -1 && finish != -1) {
                    writeOutput(String.format("gattClientDisconnect completed in %d ms", (finish - start)));
                } else {
                    writeOutput("gattClientDisconnect completed");
                }            	
                removeReceiver(receiver);
                return;
            }
            sleep(POLL_TIME);
        }

        /// throw exception for timeout
        int firedFlags = receiver.getFiredFlags();
        removeReceiver(receiver);
        reportFail(String.format("gattClientDisconnect() timeout: (expected %d), flags=0x%x " + 
                           "(expected 0x%x)", mask, firedFlags, mask));
    }

    public void gattClientDoServiceDiscovery(Context ctxt, BluetoothGatt gattClient){
    	int mask = GattClientDiscoveryReceiver.STATE_DISCOVERED_FLAG;

    	/// check pre-condition
        assertNotNull(ctxt);
        assertNotNull(gattClient);

    	/// check BT is enabled or not
    	BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (!adapter.isEnabled()) {
        	reportFail("gattClientDisconnect() bluetooth not enabled");
        }

        /// check gatt is connected
        if(!isGattConnect(ctxt)){
            return;
        }

        /// try to do discovery
        GattClientDiscoveryReceiver receiver = getGattClientDiscoveryReceiver(mask);

        long start = System.currentTimeMillis();
        assertTrue(gattClient.discoverServices());

        /// check the result
        while (System.currentTimeMillis() - start < GATT_CLIENT_DISCOVERY_TIMEOUT) {
            if ((receiver.getFiredFlags() & mask) == mask) {
            	long finish = receiver.getCompletedTime();
                if (start != -1 && finish != -1) {
                    writeOutput(String.format("gattClientDoServiceDiscovery completed in %d ms", (finish - start)));
                } else {
                    writeOutput("gattClientDoServiceDiscovery completed");
                }            	
                removeReceiver(receiver);
                return;
            }
            sleep(POLL_TIME);
        }

        /// throw exception for timeout
        int firedFlags = receiver.getFiredFlags();
        removeReceiver(receiver);
        reportFail(String.format("gattClientDoServiceDiscovery() timeout: (expected %d), flags=0x%x " + 
                           "(expected 0x%x)", mask, firedFlags, mask));        
    }

    public void gattClientReadRssi(Context ctxt, BluetoothGatt gattClient){
    	int mask = GattClientAttributeOpReceiver.STATE_READ_FLAG;
    	
    	/// check pre-condition
        assertNotNull(ctxt);
        assertNotNull(gattClient);

    	/// check BT is enabled or not
    	BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (!adapter.isEnabled()) {
        	reportFail("gattClientAttributeOp() bluetooth not enabled");
        }

        /// check gatt is connected
        if(!isGattConnect(ctxt)){
            return;
        }
        
        /// try to do read rssi
        GattClientAttributeOpReceiver receiver = getGattClientAttributeOpReceiver(mask);        

        long start = System.currentTimeMillis();
        writeOutput(String.format("gattClientReadRssi readRemoteRssi"));
        assertTrue(gattClient.readRemoteRssi());

        /// check the result
        while (System.currentTimeMillis() - start < GATT_CLIENT_ATTRIBUTE_OP_TIMEOUT) {
            if ((receiver.getFiredFlags() & mask) == mask) {
            	long finish = receiver.getCompletedTime();
                if (start != -1 && finish != -1) {
                    writeOutput(String.format("gattClientReadRssi completed in %d ms", (finish - start)));
                } else {
                    writeOutput("gattClientReadRssi completed");
                }            	

                removeReceiver(receiver);
                return;
            }
            sleep(POLL_TIME);
        }

        /// throw exception for timeout
        int firedFlags = receiver.getFiredFlags();
        removeReceiver(receiver);
        reportFail(String.format("gattClientReadRssi() timeout: (expected %d), flags=0x%x " + 
                           "(expected 0x%x)", mask, firedFlags, mask));    	
    }
    
    private void gattClientAttributeOp(Context ctxt, BluetoothGatt gattClient, Object objAttr, int mask, boolean bWrite){
    	/// check pre-condition
        assertNotNull(ctxt);
        assertNotNull(gattClient);
        assertTrue((objAttr instanceof BluetoothGattCharacteristic || objAttr instanceof BluetoothGattDescriptor));

    	/// check BT is enabled or not
    	BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (!adapter.isEnabled()) {
        	reportFail("gattClientAttributeOp() bluetooth not enabled");
        }

        /// check gatt is connected
        if(!isGattConnect(ctxt)){
            return;
        }
        
        /// try to do read
        GattClientAttributeOpReceiver receiver = getGattClientAttributeOpReceiver(mask);        

        long start = System.currentTimeMillis();
        if(objAttr instanceof BluetoothGattCharacteristic){
        	if(bWrite)
        		assertTrue(gattClient.writeCharacteristic((BluetoothGattCharacteristic)objAttr));
        	else
        		assertTrue(gattClient.readCharacteristic((BluetoothGattCharacteristic)objAttr));
        }else{
        	if(bWrite)
    			assertTrue(gattClient.writeDescriptor((BluetoothGattDescriptor)objAttr));
        	else
        		assertTrue(gattClient.readDescriptor((BluetoothGattDescriptor)objAttr));
        }

        /// check the result
        while (System.currentTimeMillis() - start < GATT_CLIENT_ATTRIBUTE_OP_TIMEOUT) {
            if ((receiver.getFiredFlags() & mask) == mask) {
            	long finish = receiver.getCompletedTime();
                if (start != -1 && finish != -1) {
                    writeOutput(String.format("gattClientAttributeOp completed in %d ms", (finish - start)));
                } else {
                    writeOutput("gattClientAttributeOp completed");
                }            	

                removeReceiver(receiver);
                return;
            }
            sleep(POLL_TIME);
        }

        /// throw exception for timeout
        int firedFlags = receiver.getFiredFlags();
        removeReceiver(receiver);
        reportFail(String.format("gattClientAttributeOp() timeout: (expected %d), flags=0x%x " + 
                           "(expected 0x%x)", mask, firedFlags, mask));
    }
    
    public void gattClientReadCharacteristic(Context ctxt, BluetoothGatt gattClient, BluetoothGattCharacteristic chara){
    	gattClientAttributeOp(ctxt, gattClient, chara, GattClientAttributeOpReceiver.STATE_READ_FLAG,false);
    }

    public void gattClientWriteCharacteristic(Context ctxt, BluetoothGatt gattClient, BluetoothGattCharacteristic chara){
    	gattClientAttributeOp(ctxt, gattClient, chara, GattClientAttributeOpReceiver.STATE_WRITE_FLAG, true);
    }

    public void gattClientReadDescriptor(Context ctxt, BluetoothGatt gattClient, BluetoothGattDescriptor desc){
    	gattClientAttributeOp(ctxt, gattClient, desc, GattClientAttributeOpReceiver.STATE_READ_FLAG, false);
    }

    public void gattClientWriteDescriptor(Context ctxt, BluetoothGatt gattClient, BluetoothGattDescriptor desc){
    	gattClientAttributeOp(ctxt, gattClient, desc, GattClientAttributeOpReceiver.STATE_WRITE_FLAG, true);
    }   
    
    private void gattClientRealiableWriteOp(Context ctxt, BluetoothGatt gattClient, BluetoothDevice device, List<Object> listObj, boolean bExecute, boolean bSupportDescriptorWrite){
        int mask = GattClientAttributeOpReceiver.STATE_WRITE_FLAG|GattClientAttributeOpReceiver.STATE_RELIABLE_EXECUTE|GattClientAttributeOpReceiver.STATE_COUNT_MATCH;
    	int expectCount = 0;
    	
    	/// check pre-condition
        assertNotNull(ctxt);
        assertNotNull(gattClient);
        assertNotNull(device);
        for(Object objAttr : listObj){
        	if(objAttr instanceof BluetoothGattCharacteristic){
        		expectCount++;
        	}else if(objAttr instanceof BluetoothGattDescriptor && bSupportDescriptorWrite){
        		expectCount++;
        	}else{
                assertTrue(false);        		
        	}
        }
        writeOutput("gattClientRealiableWriteOp() expectCount = " + expectCount);
        assertTrue(0 != expectCount);

    	/// check BT is enabled or not
    	BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (!adapter.isEnabled()) {
        	reportFail("gattClientRealiableWriteOp() bluetooth not enabled");
        }

        /// check gatt is connected
        if(!isGattConnect(ctxt)){
            return;
        }
        
        /// try to do read
        GattClientAttributeOpReceiver receiver = getGattClientAttributeOpReceiver(mask, expectCount);        

        long start = System.currentTimeMillis();
        assertTrue(gattClient.beginReliableWrite());
        for(Object objAttr : listObj){
            if(objAttr instanceof BluetoothGattCharacteristic){
                assertTrue(gattClient.writeCharacteristic((BluetoothGattCharacteristic)objAttr));
            }else{
            	if(bSupportDescriptorWrite)
            		assertTrue(gattClient.writeDescriptor((BluetoothGattDescriptor)objAttr));
            }
        }

        if(bExecute)
        	assertTrue(gattClient.executeReliableWrite());
        else
        	gattClient.abortReliableWrite(device);
        
        /// check the result
        while (System.currentTimeMillis() - start < GATT_CLIENT_ATTRIBUTE_OP_TIMEOUT * expectCount) {
            if ((receiver.getFiredFlags() & mask) == mask) {
            	long finish = receiver.getCompletedTime();
                if (start != -1 && finish != -1) {
                    writeOutput(String.format("gattClientRealiableWriteOp completed in %d ms", (finish - start)));
                } else {
                    writeOutput("gattClientRealiableWriteOp completed");
                }            	

                removeReceiver(receiver);
                return;
            }
            sleep(POLL_TIME);
        }

        /// throw exception for timeout
        int firedFlags = receiver.getFiredFlags();
        removeReceiver(receiver);
        reportFail(String.format("gattClientRealiableWriteOp() timeout: (expected %d), flags=0x%x " + 
                           "(expected 0x%x)", mask, firedFlags, mask));    	
    }
    
    public void gattClientDoReliableWrite(Context ctxt, BluetoothGatt gattClient, BluetoothDevice device, List<Object> listAttr, boolean bSupportDescriptorWrite){
    	gattClientRealiableWriteOp(ctxt, gattClient, device, listAttr, true, bSupportDescriptorWrite);
    }

    public void gattClientAbortReliableWrite(Context ctxt, BluetoothGatt gattClient, BluetoothDevice device, List<Object> listAttr, boolean bSupportDescriptorWrite){
    	gattClientRealiableWriteOp(ctxt, gattClient, device, listAttr, false, bSupportDescriptorWrite);
    }

    /**
     * Connects a profile from the local device to a remote device and checks to make sure that the
     * profile is connected and that the correct actions were broadcast.
     *
     * @param adapter The BT adapter.
     * @param device The remote device.
     * @param profile The profile to connect. One of {@link BluetoothProfile#A2DP},
     * {@link BluetoothProfile#HEADSET}, or {@link BluetoothProfile#INPUT_DEVICE}.
     * @param methodName The method name to printed in the logs.  If null, will be
     * "connectProfile(profile=&lt;profile&gt;, device=&lt;device&gt;)"
     */
    public void connectProfile(BluetoothAdapter adapter, BluetoothDevice device, int profile,
            String methodName) {
        if (methodName == null) {
            methodName = String.format("connectProfile(profile=%d, device=%s)", profile, device);
        }
        int mask = (ConnectProfileReceiver.STATE_CONNECTING_FLAG
                | ConnectProfileReceiver.STATE_CONNECTED_FLAG);
        long start = -1;

        if (!adapter.isEnabled()) {
        	reportFail(String.format("%s bluetooth not enabled", methodName));
        }

        if (!adapter.getBondedDevices().contains(device)) {
        	reportFail(String.format("%s device not paired", methodName));
        }

        BluetoothProfile proxy = connectProxy(adapter, profile);
        assertNotNull(proxy);

        ConnectProfileReceiver receiver = getConnectProfileReceiver(device, profile, mask);

        int state = proxy.getConnectionState(device);
        switch (state) {
            case BluetoothProfile.STATE_CONNECTED:
                removeReceiver(receiver);
                return;
            case BluetoothProfile.STATE_CONNECTING:
                mask = 0; // Don't check for received intents since we might have missed them.
                break;
            case BluetoothProfile.STATE_DISCONNECTED:
            case BluetoothProfile.STATE_DISCONNECTING:
                start = System.currentTimeMillis();
                if (profile == BluetoothProfile.A2DP) {
                    boolean ret = false;
                    while (System.currentTimeMillis() - start < CONNECT_DISCONNECT_PROFILE_TIMEOUT) {
                        ret = ((BluetoothA2dp)proxy).connect(device);
                        if (ret) break;
                    }
                    assertTrue(ret);
                } else if (profile == BluetoothProfile.HEADSET) {
                	  sleep(25000);
                    assertTrue(((BluetoothHeadset)proxy).connect(device));
                } else if (profile == BluetoothProfile.INPUT_DEVICE) {
                    assertTrue(((BluetoothInputDevice)proxy).connect(device));
                }
                break;
            default:
                removeReceiver(receiver);
                reportFail(String.format("%s invalid state: state=%d", methodName, state));
        }

        long s = System.currentTimeMillis();
        while (System.currentTimeMillis() - s < CONNECT_DISCONNECT_PROFILE_TIMEOUT) {
            state = proxy.getConnectionState(device);
            if (state == BluetoothProfile.STATE_CONNECTED
                    && (receiver.getFiredFlags() & mask) == mask) {
                long finish = receiver.getCompletedTime();
                if (start != -1 && finish != -1) {
                    writeOutput(String.format("%s completed in %d ms", methodName,
                            (finish - start)));
                } else {
                    writeOutput(String.format("%s completed", methodName));
                }
                removeReceiver(receiver);
                return;
            }
            sleep(POLL_TIME);
        }

        int firedFlags = receiver.getFiredFlags();
        removeReceiver(receiver);
        reportFail(String.format("%s timeout: state=%d (expected %d), flags=0x%x (expected 0x%x)",
                methodName, state, BluetoothProfile.STATE_CONNECTED, firedFlags, mask));
    }

    /**
     * Disconnects a profile between the local device and a remote device and checks to make sure
     * that the profile is disconnected and that the correct actions were broadcast.
     *
     * @param adapter The BT adapter.
     * @param device The remote device.
     * @param profile The profile to disconnect. One of {@link BluetoothProfile#A2DP},
     * {@link BluetoothProfile#HEADSET}, or {@link BluetoothProfile#INPUT_DEVICE}.
     * @param methodName The method name to printed in the logs.  If null, will be
     * "connectProfile(profile=&lt;profile&gt;, device=&lt;device&gt;)"
     */
    public void disconnectProfile(BluetoothAdapter adapter, BluetoothDevice device, int profile,
            String methodName) {
        if (methodName == null) {
            methodName = String.format("disconnectProfile(profile=%d, device=%s)", profile, device);
        }
        int mask = (ConnectProfileReceiver.STATE_DISCONNECTING_FLAG
                | ConnectProfileReceiver.STATE_DISCONNECTED_FLAG);
        int maskWithoutDisconnecting = ConnectProfileReceiver.STATE_DISCONNECTED_FLAG;
        
        long start = -1;

        if (!adapter.isEnabled()) {
        	reportFail(String.format("%s bluetooth not enabled", methodName));
        }

        if (!adapter.getBondedDevices().contains(device)) {
        	reportFail(String.format("%s device not paired", methodName));
        }

        BluetoothProfile proxy = connectProxy(adapter, profile);
        assertNotNull(proxy);

        ConnectProfileReceiver receiver = getConnectProfileReceiver(device, profile, mask);

        int state = proxy.getConnectionState(device);
        switch (state) {
            case BluetoothProfile.STATE_CONNECTED:
            case BluetoothProfile.STATE_CONNECTING:
                start = System.currentTimeMillis();
                if (profile == BluetoothProfile.A2DP) {
                    assertTrue(((BluetoothA2dp)proxy).disconnect(device));
                } else if (profile == BluetoothProfile.HEADSET) {
                    assertTrue(((BluetoothHeadset)proxy).disconnect(device));
                } else if (profile == BluetoothProfile.INPUT_DEVICE) {
                    assertTrue(((BluetoothInputDevice)proxy).disconnect(device));
                }
                break;
            case BluetoothProfile.STATE_DISCONNECTED:
                removeReceiver(receiver);
                return;
            case BluetoothProfile.STATE_DISCONNECTING:
                mask = 0; // Don't check for received intents since we might have missed them.
                break;
            default:
                removeReceiver(receiver);
                reportFail(String.format("%s invalid state: state=%d", methodName, state));
        }

        long s = System.currentTimeMillis();
        while (System.currentTimeMillis() - s < CONNECT_DISCONNECT_PROFILE_TIMEOUT) {
            state = proxy.getConnectionState(device);
            if (state == BluetoothProfile.STATE_DISCONNECTED
                    && ((receiver.getFiredFlags() & mask) == mask) 
                    || ((receiver.getFiredFlags() & maskWithoutDisconnecting) == maskWithoutDisconnecting)) {
                long finish = receiver.getCompletedTime();
                if (start != -1 && finish != -1) {
                    writeOutput(String.format("%s completed in %d ms", methodName,
                            (finish - start)));
                } else {
                    writeOutput(String.format("%s completed", methodName));
                }
                removeReceiver(receiver);
                return;
            }
            sleep(POLL_TIME);
        }

        int firedFlags = receiver.getFiredFlags();
        removeReceiver(receiver);
        reportFail(String.format("%s timeout: state=%d (expected %d), flags=0x%x (expected 0x%x)",
                methodName, state, BluetoothProfile.STATE_DISCONNECTED, firedFlags, mask));
    }

    private void reportFail(String s){
    	writeOutput(s);
    	fail(s);
    }
    
    /**
     * Writes a string to the logcat and a file if a file has been specified in the constructor.
     *
     * @param s The string to be written.
     */
    public void writeOutput(String s) {
        Log.v(mTag, s);
        if (mOutputWriter == null) {
            return;
        }
        try {
            mOutputWriter.write(s + "\n");
            mOutputWriter.flush();
        } catch (IOException e) {
            Log.w(mTag, "Could not write to output file", e);
        }
    }

    private void addReceiver(BroadcastReceiver receiver, String[] actions) {
        IntentFilter filter = new IntentFilter();
        for (String action: actions) {
            filter.addAction(action);
        }
        mContext.registerReceiver(receiver, filter);
        mReceivers.add(receiver);
    }

    private BluetoothReceiver getBluetoothReceiver(int expectedFlags) {
        String[] actions = {
                BluetoothAdapter.ACTION_DISCOVERY_FINISHED,
                BluetoothAdapter.ACTION_DISCOVERY_STARTED,
                BluetoothAdapter.ACTION_SCAN_MODE_CHANGED,
                BluetoothAdapter.ACTION_STATE_CHANGED};
        BluetoothReceiver receiver = new BluetoothReceiver(expectedFlags);
        addReceiver(receiver, actions);
        return receiver;
    }

    private ConnectProfileReceiver getConnectProfileReceiver(BluetoothDevice device, int profile,
            int expectedFlags) {
        String[] actions = {
                BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED,
                BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED,
                BluetoothInputDevice.ACTION_CONNECTION_STATE_CHANGED};
        ConnectProfileReceiver receiver = new ConnectProfileReceiver(device, profile,
                expectedFlags);
        addReceiver(receiver, actions);
        return receiver;
    }

    private GattClientProfileConnectReceiver getGattClientConnectProfileReceiver(BluetoothDevice device, int exptectedFlags){
    	String[] actions = {BluetoothLeStressTest.ACTION_CONNECTION_STATE_CHANGED,};   	
    	GattClientProfileConnectReceiver gattClientProfileConnectReceiver = 
    			new GattClientProfileConnectReceiver(device, exptectedFlags);
        addReceiver(gattClientProfileConnectReceiver, actions);
    	return gattClientProfileConnectReceiver;
    }

    private GattClientDiscoveryReceiver getGattClientDiscoveryReceiver(int exptectedFlags){
    	String[] actions = {BluetoothLeStressTest.ACTION_DISCOVERY_END};   	
    	GattClientDiscoveryReceiver receiver = 
    			                    new GattClientDiscoveryReceiver(exptectedFlags);
        addReceiver(receiver, actions);
        return receiver;
    }

    private GattClientAttributeOpReceiver getGattClientAttributeOpReceiver(int exptectedFlags){
    	return getGattClientAttributeOpReceiver(exptectedFlags, 0);
    }
    
    private GattClientAttributeOpReceiver getGattClientAttributeOpReceiver(int exptectedFlags, int expectedCount){
    	String[] actions = {BluetoothLeStressTest.ACTION_READ_ATTRIBUTE,BluetoothLeStressTest.ACTION_WRITE_ATTRIBUTE,
    			            BluetoothLeStressTest.ACTION_RELIABLE_EXECUTE, BluetoothLeStressTest.ACTION_RELIABLE_ABORT};
    	GattClientAttributeOpReceiver receiver = 
    			                    new GattClientAttributeOpReceiver(exptectedFlags, expectedCount);
        addReceiver(receiver, actions);
        return receiver;    	
    }

    private void removeReceiver(BroadcastReceiver receiver) {
        mContext.unregisterReceiver(receiver);
        mReceivers.remove(receiver);
    }

    private BluetoothProfile connectProxy(BluetoothAdapter adapter, int profile) {
        switch (profile) {
            case BluetoothProfile.A2DP:
                if (mA2dp != null) {
                    return mA2dp;
                }
                break;
            case BluetoothProfile.HEADSET:
                if (mHeadset != null) {
                    return mHeadset;
                }
                break;
            case BluetoothProfile.INPUT_DEVICE:
                if (mInput != null) {
                    return mInput;
                }
                break;
            case BluetoothProfile.PAN:
                if (mPan != null) {
                    return mPan;
                }
                break;
            default:
                return null;
        }
        adapter.getProfileProxy(mContext, mServiceListener, profile);
        long s = System.currentTimeMillis();
        switch (profile) {
            case BluetoothProfile.A2DP:
                while (mA2dp == null && System.currentTimeMillis() - s < CONNECT_PROXY_TIMEOUT) {
                    sleep(POLL_TIME);
                }
                return mA2dp;
            case BluetoothProfile.HEADSET:
                while (mHeadset == null && System.currentTimeMillis() - s < CONNECT_PROXY_TIMEOUT) {
                    sleep(POLL_TIME);
                }
                return mHeadset;
            case BluetoothProfile.INPUT_DEVICE:
                while (mInput == null && System.currentTimeMillis() - s < CONNECT_PROXY_TIMEOUT) {
                    sleep(POLL_TIME);
                }
                return mInput;
            case BluetoothProfile.PAN:
                while (mPan == null && System.currentTimeMillis() - s < CONNECT_PROXY_TIMEOUT) {
                    sleep(POLL_TIME);
                }
                return mPan;
            default:
                return null;
        }
    }

    private void sleep(long time) {
        try {
            Thread.sleep(time);
        } catch (InterruptedException e) {
        }
    }
}


