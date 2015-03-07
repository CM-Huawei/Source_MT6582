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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import android.content.Context;
import android.content.Intent;
import android.os.Parcelable;
import android.test.InstrumentationTestCase;
import android.test.suitebuilder.annotation.MediumTest;
import android.test.suitebuilder.annotation.SmallTest;
import android.util.Log;

public class BluetoothLeStressTest extends InstrumentationTestCase {
    
	private static final String TAG = "BluetoothLeStressTest";
    private static final String OUTPUT_FILE = "BluetoothLeStressTestOutput.txt";

    private BluetoothLeTestUtils mTestUtils = null;
    private BluetoothDevice mCurDevice = null;
    private BluetoothAdapter mAdapter = null;
    private Context mContext = null;
        
    public static final String ACTION_CONNECTION_STATE_CHANGED = "android.bluetooth.gattclient.profile.action.CONNECTION_STATE_CHANGED";
    public static final String ACTION_DISCOVERY_END            = "android.bluetooth.gattclient.profile.action.DISCOVERY_END";
    public static final String ACTION_READ_ATTRIBUTE           = "android.bluetooth.gattclient.profile.action.READ_ATTRIBUTE";
    public static final String ACTION_WRITE_ATTRIBUTE          = "android.bluetooth.gattclient.profile.action.WRITE_ATTRIBUTE";
    public static final String ACTION_RELIABLE_EXECUTE         = "android.bluetooth.gattclient.profile.action.RELIABLE_EXECUTE";    
    public static final String ACTION_RELIABLE_ABORT           = "android.bluetooth.gattclient.profile.action.ACTION_RELIABLE_ABORT";    
    
    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mContext = getInstrumentation().getTargetContext();
        mTestUtils = new BluetoothLeTestUtils(mContext, TAG, OUTPUT_FILE);
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
        mTestUtils.writeOutput("");
        mTestUtils.close();
        mTestUtils = null;
        mCurDevice = null;
        mAdapter = null;
        mContext = null;        
    }

    private BluetoothGattCallback mClientCallback = new BluetoothGattCallback(){
		@Override
        public void onCharacteristicRead(BluetoothGatt gatt,
                BluetoothGattCharacteristic characteristic, int status) {
			Intent intent = new Intent(ACTION_READ_ATTRIBUTE);
			BluetoothLeStressTest.this.getInstrumentation().getContext().sendBroadcast(intent);						
        }

		@Override
        public void onCharacteristicWrite(BluetoothGatt gatt,
                BluetoothGattCharacteristic characteristic, int status) {
			Intent intent = new Intent(ACTION_WRITE_ATTRIBUTE);
			BluetoothLeStressTest.this.getInstrumentation().getContext().sendBroadcast(intent);						
        }

		@Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status,
                int newState) {
			if(BluetoothGatt.GATT_SUCCESS == status){
				Intent intent = new Intent(ACTION_CONNECTION_STATE_CHANGED);
				intent.putExtra(BluetoothDevice.EXTRA_DEVICE, (Parcelable)mCurDevice);
				intent.putExtra(BluetoothProfile.EXTRA_STATE, newState);
				BluetoothLeStressTest.this.getInstrumentation().getContext().sendBroadcast(intent);
			}
        }

		@Override
        public void onDescriptorRead(BluetoothGatt gatt,
                BluetoothGattDescriptor descriptor, int status) {
			Intent intent = new Intent(ACTION_READ_ATTRIBUTE);
			BluetoothLeStressTest.this.getInstrumentation().getContext().sendBroadcast(intent);						
        }

		@Override
        public void onDescriptorWrite(BluetoothGatt gatt,
                BluetoothGattDescriptor descriptor, int status) {
			Intent intent = new Intent(ACTION_WRITE_ATTRIBUTE);
			BluetoothLeStressTest.this.getInstrumentation().getContext().sendBroadcast(intent);						
        }

		@Override
        public void onReadRemoteRssi(BluetoothGatt gatt, int rssi, int status) {
			Intent intent = new Intent(ACTION_READ_ATTRIBUTE);
			BluetoothLeStressTest.this.getInstrumentation().getContext().sendBroadcast(intent);						
        }

		@Override
        public void onReliableWriteCompleted(BluetoothGatt gatt, int status) {
			
			String strAction = null;
			if(BluetoothGatt.GATT_SUCCESS == status){
				strAction = ACTION_RELIABLE_EXECUTE;
			}
			
			Intent intent = new Intent(strAction);
			BluetoothLeStressTest.this.getInstrumentation().getContext().sendBroadcast(intent);			
        }

		@Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status){
			Intent intent = new Intent(ACTION_DISCOVERY_END);
			BluetoothLeStressTest.this.getInstrumentation().getContext().sendBroadcast(intent);			
        }
    	
    };
    
    @SmallTest
    @MediumTest
    public void testCase01Enable(){
        mTestUtils.writeOutput("testCase01Enable() - start");
        
        mAdapter = BluetoothAdapter.getDefaultAdapter();
        mTestUtils.writeOutput("testCase01Enable() - getDefaultAdapter");
        assertNotNull(mAdapter);
        mTestUtils.writeOutput("testCase01Enable() - disable");
        mTestUtils.disable(mAdapter);
        
        int iterations = BluetoothLeTestRunner.nDefaultIterations;
        for(int i=0; i< iterations; i++){
            mTestUtils.writeOutput("testCase01Enable() - enable iteration" + (i + 1) + " of " + iterations);
            mTestUtils.enable(mAdapter);
            mTestUtils.writeOutput("testCase01Enable() - disable iteration" + (i + 1) + " of " + iterations);
            mTestUtils.disable(mAdapter);
        }
        
        mTestUtils.writeOutput("testCase01Enable() - pass ");
    }
    
    @MediumTest
    public void testCase02GetGattClient(){
        mTestUtils.writeOutput("testCase02GetGattClient() - start");
        
        mAdapter = BluetoothAdapter.getDefaultAdapter();
        mTestUtils.writeOutput("testCase02GetGattClient() - getDefaultAdapter");
        assertNotNull(mAdapter);
        mTestUtils.writeOutput("testCase02GetGattClient() - disable");
        mTestUtils.disable(mAdapter);
        mTestUtils.writeOutput("testCase02GetGattClient() - enable");
        mTestUtils.enable(mAdapter);
        mTestUtils.writeOutput("testCase02GetGattClient() - adapter.getRemoteDevice");
        mCurDevice = mAdapter.getRemoteDevice(BluetoothLeTestRunner.sDeviceAddress);
        assert(null != mCurDevice);
        mTestUtils.writeOutput("testCase02GetGattClient() - check whether device is paired or not");
        assertTrue(BluetoothDevice.BOND_BONDED == mCurDevice.getBondState());
        mTestUtils.writeOutput("testCase02GetGattClient() - mCurDevice.connectGatt");
        BluetoothGatt gattClient = mCurDevice.connectGatt(getInstrumentation().getContext(), false, mClientCallback);
        assert(null != gattClient);
        wait1stConnected(System.currentTimeMillis());
        mTestUtils.writeOutput("testCase02GetGattClient() - gattClientDisconnect");
        mTestUtils.gattClientDisconnect(mContext, mCurDevice, gattClient);
        
        gattClient.close();
        mTestUtils.writeOutput("testCase02GetGattClient() - pass");
    }
    
    @MediumTest
    public void testCase03Connect(){
        mTestUtils.writeOutput("testCase03Connect() - start");
        
        mAdapter = BluetoothAdapter.getDefaultAdapter();
        mTestUtils.writeOutput("testCase03Connect() - getDefaultAdapter");
        assertNotNull(mAdapter);
        mTestUtils.writeOutput("testCase03Connect() - disable");
        mTestUtils.disable(mAdapter);
        mTestUtils.writeOutput("testCase03Connect() - enable");
        mTestUtils.enable(mAdapter);
        mTestUtils.writeOutput("testCase03Connect() - getRemoteDevice");
        mCurDevice = mAdapter.getRemoteDevice(BluetoothLeTestRunner.sDeviceAddress);
        assert(null != mCurDevice);
        mTestUtils.writeOutput("testCase03Connect() - check whether device is paired or not");
        assertTrue(BluetoothDevice.BOND_BONDED == mCurDevice.getBondState());
        mTestUtils.writeOutput("testCase03Connect() - connectGatt");
        BluetoothGatt gattClient = mCurDevice.connectGatt(getInstrumentation().getContext(), false, mClientCallback);        
        assert(null != gattClient);
        mTestUtils.writeOutput("testCase03Connect() - wait1stConnected");
        wait1stConnected(System.currentTimeMillis());
        mTestUtils.writeOutput("testCase03Connect() - gattClientDisconnect");
        mTestUtils.gattClientDisconnect(mContext, mCurDevice, gattClient);
        /// start to do stress test for connect
        int iterations = BluetoothLeTestRunner.nConnectIterations;
        for(int i=0; i< iterations; i++){
        	mTestUtils.writeOutput("testCase03Connect() - gattClient connect iteration " + (i + 1) + " of " + iterations);
            mTestUtils.gattClientConnect(mContext, mCurDevice, gattClient);
        	mTestUtils.writeOutput("testCase03Connect() - gattClient disconnect iteration " + (i + 1) + " of " + iterations);
            mTestUtils.gattClientDisconnect(mContext, mCurDevice, gattClient);
        }
        
        gattClient.close();
        mTestUtils.writeOutput("testCase03Connect() - pass");
    }

    @MediumTest
    public void testCase04ReadRemoteRssi(){
        mTestUtils.writeOutput("testCase04ReadRemoteRssi() - start");

        mTestUtils.writeOutput("testCase04ReadRemoteRssi() - getDefaultAdapter()");
        mAdapter = BluetoothAdapter.getDefaultAdapter();
        assertNotNull(mAdapter);
        mTestUtils.writeOutput("testCase04ReadRemoteRssi() - disable()");
        mTestUtils.disable(mAdapter);
        mTestUtils.writeOutput("testCase04ReadRemoteRssi() - enable()");
        mTestUtils.enable(mAdapter);
        mTestUtils.writeOutput("testCase04ReadRemoteRssi() - adapter.getRemoteDevice");
        mCurDevice = mAdapter.getRemoteDevice(BluetoothLeTestRunner.sDeviceAddress);
        assertNotNull(mCurDevice);
        mTestUtils.writeOutput("testCase04ReadRemoteRssi() - check whether device is paired or not");
        assertTrue(BluetoothDevice.BOND_BONDED == mCurDevice.getBondState());        
        mTestUtils.writeOutput("testCase04ReadRemoteRssi() - connectGatt");
        BluetoothGatt gattClient = mCurDevice.connectGatt(getInstrumentation().getContext(), false, mClientCallback);
        assertNotNull(gattClient);
        mTestUtils.writeOutput("testCase04ReadRemoteRssi() - wait1stConnected");
        wait1stConnected(System.currentTimeMillis());
        
        int iterations = BluetoothLeTestRunner.nReadRssiIteration;
        for(int i=0; i< iterations ; i++){
	        mTestUtils.writeOutput("testCase04ReadRemoteRssi() - gattClientReadRssi iteration " + (i + 1) + " of " + iterations);
	        mTestUtils.gattClientReadRssi(getInstrumentation().getContext(), gattClient);
        }
        
        gattClient.close();
        mTestUtils.writeOutput("testCase04ReadRemoteRssi() - pass");        
    }    
    
    @MediumTest
    public void testCase05DoSericesDiscovery(){
        mTestUtils.writeOutput("testCase05DoSericesDiscovery() - start");
        
        mAdapter = BluetoothAdapter.getDefaultAdapter();
        mTestUtils.writeOutput("testCase05DoSericesDiscovery() - getDefaultAdapter");
        assertNotNull(mAdapter);
        mTestUtils.writeOutput("testCase05DoSericesDiscovery() - disable");
        mTestUtils.disable(mAdapter);
        mTestUtils.writeOutput("testCase05DoSericesDiscovery() - enable");
        mTestUtils.enable(mAdapter);
        mTestUtils.writeOutput("testCase05DoSericesDiscovery() - adapter.getRemoteDevice");
        mCurDevice = mAdapter.getRemoteDevice(BluetoothLeTestRunner.sDeviceAddress);
        assert(null != mCurDevice);
        mTestUtils.writeOutput("testCase05DoSericesDiscovery() - check whether device is paired or not");
        assertTrue(BluetoothDevice.BOND_BONDED == mCurDevice.getBondState());                
        mTestUtils.writeOutput("testCase05DoSericesDiscovery() - connectGatt");
        BluetoothGatt gattClient = mCurDevice.connectGatt(getInstrumentation().getContext(), false, mClientCallback);
        assert(null != gattClient);
        mTestUtils.writeOutput("testCase05DoSericesDiscovery() - wait1stConnected");
        wait1stConnected(System.currentTimeMillis());        
        /// start to do stress test for service discovery
        int iterations = BluetoothLeTestRunner.nDiscoveringIterations;
        for(int i=0; i< iterations; i++){
        	mTestUtils.writeOutput("testCase05DoSericesDiscovery() - gattClient do refresh iteration " + (i + 1) + " of " + iterations);
        	assertTrue(gattClient.refresh());
        	mTestUtils.writeOutput("testCase05DoSericesDiscovery() - gattClient do service discovery iteration " + (i + 1) + " of " + iterations);
        	mTestUtils.gattClientDoServiceDiscovery(mContext, gattClient);
        	/// check the result of discovery
        	mTestUtils.writeOutput("testCase05DoSericesDiscovery() - check results of service discovery iteration " + (i + 1) + " of " + iterations);
        	assertTrue(CheckGattService(gattClient));
        }
        mTestUtils.gattClientDisconnect(mContext, mCurDevice, gattClient);
        
        gattClient.close();
        mTestUtils.writeOutput("testCase05DoSericesDiscovery() - pass");    	
    }
    
    @MediumTest
    public void testCase06ReadWriteCharacteristic(){
        mTestUtils.writeOutput("testCase06ReadWriteCharacteristic() - start");

        mAdapter = BluetoothAdapter.getDefaultAdapter();
        mTestUtils.writeOutput("testCase06ReadWriteCharacteristic() - getDefaultAdapter");
        assertNotNull(mAdapter);
        mTestUtils.writeOutput("testCase06ReadWriteCharacteristic() - disable");
        mTestUtils.disable(mAdapter);
        mTestUtils.writeOutput("testCase06ReadWriteCharacteristic() - enable");
        mTestUtils.enable(mAdapter);
        mTestUtils.writeOutput("testCase06ReadWriteCharacteristic() - adapter.getRemoteDevice");
        mCurDevice = mAdapter.getRemoteDevice(BluetoothLeTestRunner.sDeviceAddress);
        assert(null != mCurDevice);
        mTestUtils.writeOutput("testCase06ReadWriteCharacteristic() - check whether device is paired or not");
        assertTrue(BluetoothDevice.BOND_BONDED == mCurDevice.getBondState());                        
        mTestUtils.writeOutput("testCase06ReadWriteCharacteristic() - connectGatt");
        BluetoothGatt gattClient = mCurDevice.connectGatt(getInstrumentation().getContext(), false, mClientCallback);
        assert(null != gattClient);
        mTestUtils.writeOutput("testCase06ReadWriteCharacteristic() - wait1stConnected");
        wait1stConnected(System.currentTimeMillis());        
        mTestUtils.writeOutput("testCase06ReadWriteCharacteristic() - gattClientDoServiceDiscovery");
    	mTestUtils.gattClientDoServiceDiscovery(mContext, gattClient);
    	
    	String strValue = null, strNewValue = null;
        mTestUtils.writeOutput("testCase06ReadWriteCharacteristic() - getGattTestCharacteristic");
    	BluetoothGattCharacteristic chara = getGattTestCharacteristic(gattClient);
        assertNotNull(chara);
        mTestUtils.writeOutput("testCase06ReadWriteCharacteristic() - gattClientReadCharacteristic");
    	mTestUtils.gattClientReadCharacteristic(mContext, gattClient, chara);
    	if(null != chara.getValue())
    		strValue = chara.getStringValue(0);
    	else
    		strValue = "";
    	
    	/// start to do stress test for writing characteristic
        int iterations = BluetoothLeTestRunner.nWriteCharIterations;
        for(int i=0; i< iterations; i++){
        	strNewValue = strValue + i;
        	chara.setValue(strNewValue);
        	mTestUtils.writeOutput("testCase06ReadWriteCharacteristic() - gattClient write char iteration " + (i + 1) + " of " + iterations);
        	mTestUtils.gattClientWriteCharacteristic(mContext, gattClient, chara);
        	mTestUtils.writeOutput("testCase06ReadWriteCharacteristic() - gattClient read char iteration " + (i + 1) + " of " + iterations);
        	mTestUtils.gattClientReadCharacteristic(mContext, gattClient, chara);
        	mTestUtils.writeOutput("testCase06ReadWriteCharacteristic() - check result of write iteration " + (i + 1) + " of " + iterations);
        	assertTrue(strNewValue.equals(chara.getStringValue(0)));
        }
        
        chara.setValue(strValue);
    	mTestUtils.writeOutput("testCase06ReadWriteCharacteristic() - recover write char");
    	mTestUtils.gattClientWriteCharacteristic(mContext, gattClient, chara);
    	mTestUtils.writeOutput("testCase06ReadWriteCharacteristic() - recover read char");
    	mTestUtils.gattClientReadCharacteristic(mContext, gattClient, chara);
    	mTestUtils.writeOutput("testCase06ReadWriteCharacteristic() - check result of recovery");
    	assertTrue(strValue.equals(chara.getStringValue(0)));

        gattClient.close();
        mTestUtils.writeOutput("testCase06ReadWriteCharacteristic() - pass");    	    	
    }
    
    public void testCase07ReadWriteDescriptor(){
        mTestUtils.writeOutput("testCase07ReadWriteDescriptor() - start");
        
        mAdapter = BluetoothAdapter.getDefaultAdapter();
        mTestUtils.writeOutput("testCase07ReadWriteDescriptor() - getDefaultAdapter");
        assertNotNull(mAdapter);
        mTestUtils.writeOutput("testCase07ReadWriteDescriptor() - disable");
        mTestUtils.disable(mAdapter);
        mTestUtils.writeOutput("testCase07ReadWriteDescriptor() - enable");
        mTestUtils.enable(mAdapter);
        mTestUtils.writeOutput("testCase07ReadWriteDescriptor() - adapter.getRemoteDevice");
        mCurDevice = mAdapter.getRemoteDevice(BluetoothLeTestRunner.sDeviceAddress);
        assert(null != mCurDevice);
        mTestUtils.writeOutput("testCase07ReadWriteDescriptor() - check whether device is paired or not");
        assertTrue(BluetoothDevice.BOND_BONDED == mCurDevice.getBondState());                                
        mTestUtils.writeOutput("testCase07ReadWriteDescriptor() - connectGatt");
        BluetoothGatt gattClient = mCurDevice.connectGatt(getInstrumentation().getContext(), false, mClientCallback);
        assert(null != gattClient);
        mTestUtils.writeOutput("testCase07ReadWriteDescriptor() - wait1stConnected");
        wait1stConnected(System.currentTimeMillis());        
        mTestUtils.writeOutput("testCase07ReadWriteDescriptor() - gattClientDoServiceDiscovery");
    	mTestUtils.gattClientDoServiceDiscovery(mContext, gattClient);

    	/// start to do stress test for writing descriptor
    	byte[] arrByte = null, arrNewByte = null;
        mTestUtils.writeOutput("testCase07ReadWriteDescriptor() - getGattTestDescriptor");
    	BluetoothGattDescriptor desc = getGattTestDescriptor(gattClient);
        assertNotNull(desc);
    	mTestUtils.writeOutput("testCase07ReadWriteDescriptor() - gattClientReadDescriptor");
    	mTestUtils.gattClientReadDescriptor(mContext, gattClient, desc);
    	arrByte = desc.getValue();
    	arrNewByte = new byte[arrByte.length + 1];
    	System.arraycopy(arrByte, 0, arrNewByte, 0, arrByte.length);
    	
        int iterations = BluetoothLeTestRunner.nWriteDescIterations;
        for(int i=0; i< iterations; i++){
        	arrNewByte[arrNewByte.length-1] += i;
        	desc.setValue(arrNewByte);
        	mTestUtils.writeOutput("testCase07ReadWriteDescriptor() - gattClient write desc iteration " + (i + 1) + " of " + iterations);
        	mTestUtils.gattClientWriteDescriptor(mContext, gattClient, desc);
        	mTestUtils.writeOutput("testCase07ReadWriteDescriptor() - gattClient read desc iteration " + (i + 1) + " of " + iterations);
        	mTestUtils.gattClientReadDescriptor(mContext, gattClient, desc);
        	mTestUtils.writeOutput("testCase07ReadWriteDescriptor() - check result of desc iteration " + (i + 1) + " of " + iterations);
        	assertTrue(Arrays.equals(arrNewByte,desc.getValue()));
        }    	    	

        /// recover the data
        desc.setValue(arrByte);
    	mTestUtils.writeOutput("testCase07ReadWriteDescriptor() - recover write char");
    	mTestUtils.gattClientWriteDescriptor(mContext, gattClient, desc);
    	mTestUtils.writeOutput("testCase07ReadWriteDescriptor() - recover read char");
    	mTestUtils.gattClientWriteDescriptor(mContext, gattClient, desc);
    	mTestUtils.writeOutput("testCase07ReadWriteDescriptor() - check result of recovery");
    	assertTrue(Arrays.equals(arrByte,desc.getValue()));
        
        gattClient.close();
    	mTestUtils.writeOutput("testCase07ReadWriteDescriptor() - pass");    	    	
    }
    
    public void testCase08DoReliableWrite(){
        mTestUtils.writeOutput("testCase08DoReliableWrite() - start");

        mTestUtils.writeOutput("testCase08DoReliableWrite() - getDefaultAdapter()");
        mAdapter = BluetoothAdapter.getDefaultAdapter();
        assertNotNull(mAdapter);
        mTestUtils.writeOutput("testCase08DoReliableWrite() - disable()");
        mTestUtils.disable(mAdapter);
        mTestUtils.writeOutput("testCase08DoReliableWrite() - enable()");        
        mTestUtils.enable(mAdapter);
        mTestUtils.writeOutput("testCase08DoReliableWrite() - adapter.getRemoteDevice");
        mCurDevice = mAdapter.getRemoteDevice(BluetoothLeTestRunner.sDeviceAddress);
        assertNotNull(mCurDevice);
        mTestUtils.writeOutput("testCase08DoReliableWrite() - check whether device is paired or not");
        assertTrue(BluetoothDevice.BOND_BONDED == mCurDevice.getBondState());                                
        mTestUtils.writeOutput("testCase08DoReliableWrite() - connectGatt");
        BluetoothGatt gattClient = mCurDevice.connectGatt(getInstrumentation().getContext(), false, mClientCallback);
        assertNotNull(gattClient);
        mTestUtils.writeOutput("testCase08DoReliableWrite() - wait1stConnected");
        wait1stConnected(System.currentTimeMillis());        
        mTestUtils.writeOutput("testCase08DoReliableWrite() - gattClientDoServiceDiscovery");
    	mTestUtils.gattClientDoServiceDiscovery(mContext, gattClient);
    	
    	/// prepare data for reliable write
        mTestUtils.writeOutput("testCase08DoReliableWrite() - prepare data for abort reliable write");
        mTestUtils.writeOutput("testCase08DoReliableWrite() - getGattTestAttrs");
    	List<Object> listAttr = getGattTestAttrs(gattClient);
    	assertTrue(0 != listAttr.size());
    	
    	/// readAll data
        mTestUtils.writeOutput("testCase08DoReliableWrite() - readAllAttribute");
    	readAllAttribute(listAttr, gattClient);
    	
    	mTestUtils.writeOutput("testCase08DoReliableWrite() - backup recover data");
    	HashMap<Object, Object> dataBackMap = backupRecoverData(listAttr);
    	
    	/// start to do stress test for reliable write
    	int iterations = BluetoothLeTestRunner.nWriteDescIterations;
    	for(int i=0; i< iterations; i++){
        	mTestUtils.writeOutput("testCase08DoReliableWrite() - gattClient reliable write iteration " + (i + 1) + " of " + iterations);
        	setAllAttributeData(listAttr, i);
        	mTestUtils.gattClientDoReliableWrite(mContext, gattClient, mCurDevice, listAttr, BluetoothLeTestRunner.SUPPORT_FEATURE_RELIABLE_WRITE_DESCRIPTOR);
    	}
    	
    	/// recover data
    	restoreData(dataBackMap);
    	mTestUtils.writeOutput("testCase08DoReliableWrite() - gattClient reliable write recovery");
    	mTestUtils.gattClientDoReliableWrite(mContext, gattClient, mCurDevice, listAttr, BluetoothLeTestRunner.SUPPORT_FEATURE_RELIABLE_WRITE_DESCRIPTOR);

    	/// readAll data
        mTestUtils.writeOutput("testCase08DoReliableWrite() - readAllAttribute");
    	readAllAttribute(listAttr, gattClient);

    	/// readAllData to check whether it's not changed
        mTestUtils.writeOutput("testCase08DoReliableWrite() - isAttrDataNotChanged");
    	assertTrue(isAttrDataNotChanged(dataBackMap));
    	
        gattClient.close();
        mTestUtils.writeOutput("testCase08DoReliableWrite() - pass");
    }
    
    public void testCase09AbortReliableWrite(){
        mTestUtils.writeOutput("testCase09AbortReliableWrite() - start");

        mTestUtils.writeOutput("testCase09AbortReliableWrite() - getDefaultAdapter()");
        mAdapter = BluetoothAdapter.getDefaultAdapter();
        assertNotNull(mAdapter);
        mTestUtils.writeOutput("testCase09AbortReliableWrite() - disable()");
        mTestUtils.disable(mAdapter);
        mTestUtils.writeOutput("testCase09AbortReliableWrite() - enable()");
        mTestUtils.enable(mAdapter);
        mTestUtils.writeOutput("testCase09AbortReliableWrite() - adapter.getRemoteDevice");
        mCurDevice = mAdapter.getRemoteDevice(BluetoothLeTestRunner.sDeviceAddress);
        assertNotNull(mCurDevice);
        mTestUtils.writeOutput("testCase09AbortReliableWrite() - check whether device is paired or not");
        assertTrue(BluetoothDevice.BOND_BONDED == mCurDevice.getBondState());                                        
        mTestUtils.writeOutput("testCase09AbortReliableWrite() - connectGatt");
        BluetoothGatt gattClient = mCurDevice.connectGatt(getInstrumentation().getContext(), false, mClientCallback);
        assertNotNull(gattClient);
        mTestUtils.writeOutput("testCase09AbortReliableWrite() - wait1stConnected");
        wait1stConnected(System.currentTimeMillis());        
        mTestUtils.writeOutput("testCase09AbortReliableWrite() - gattClientDoServiceDiscovery");
    	mTestUtils.gattClientDoServiceDiscovery(mContext, gattClient);

    	/// prepare data for abort reliable write
        mTestUtils.writeOutput("testCase09AbortReliableWrite() - prepare data for abort reliable write");
        mTestUtils.writeOutput("testCase09AbortReliableWrite() - getGattTestAttrs");
    	List<Object> listAttr = getGattTestAttrs(gattClient);
        assertTrue(0 != listAttr.size());
    	
    	/// readAll data
        mTestUtils.writeOutput("testCase09AbortReliableWrite() - readAllAttribute");
    	readAllAttribute(listAttr, gattClient);
    	
    	///backup recover data
    	mTestUtils.writeOutput("testCase09AbortReliableWrite() - backup recover data");
    	HashMap<Object, Object> dataBackMap = backupRecoverData(listAttr);
    	
    	/// start to do stress test for abort reliable write
    	int iterations = BluetoothLeTestRunner.nWriteDescIterations;
    	for(int i=0; i< iterations; i++){
        	mTestUtils.writeOutput("testCase09AbortReliableWrite() - gattClient abort reliable write iteration " + (i + 1) + " of " + iterations);
        	setAllAttributeData(listAttr, i);
        	mTestUtils.gattClientAbortReliableWrite(mContext, gattClient, mCurDevice, listAttr, BluetoothLeTestRunner.SUPPORT_FEATURE_RELIABLE_WRITE_DESCRIPTOR);
    	}

    	/// readAllData to check whether it's not changed
        mTestUtils.writeOutput("testCase09AbortReliableWrite() - readAllAttribute");
    	readAllAttribute(listAttr, gattClient);
        mTestUtils.writeOutput("testCase09AbortReliableWrite() - isAttrDataNotChanged");
    	assertTrue(isAttrDataNotChanged(dataBackMap));

        gattClient.close();
        mTestUtils.writeOutput("testCase09AbortReliableWrite() - pass");    	
    }  
    
    @SmallTest
    public void testCase10DoLeScan(){
    	mTestUtils.writeOutput("testCase10DoLeScan() - start");
    	
        mTestUtils.writeOutput("testCase10DoLeScan() - getDefaultAdapter()");
        mAdapter = BluetoothAdapter.getDefaultAdapter();
        assertNotNull(mAdapter);
        
        mTestUtils.writeOutput("testCase10DoLeScan() - disable");
        mTestUtils.disable(mAdapter);
        mTestUtils.writeOutput("testCase10DoLeScan() - enable");
        mTestUtils.enable(mAdapter);
        
        BluetoothAdapter.LeScanCallback leCallback = new BluetoothAdapter.LeScanCallback(){
			@Override
            public void onLeScan(BluetoothDevice arg0, int arg1, byte[] arg2) {   
            }        	
        };
    	
        int iterations = BluetoothLeTestRunner.nLeScanIterations;
        for(int i=0 ; i<iterations ; i++){
        	mAdapter.startLeScan(leCallback);
        	try{
        		Thread.sleep(100);
        	}catch(Exception e){
        		mTestUtils.writeOutput("testCase10DoLeScan() - exception:" + e.toString());
        	}
        	mAdapter.stopLeScan(leCallback);
        }
    	
    	mTestUtils.writeOutput("testCase10DoLeScan() - pass");
    }
    
    @SmallTest
    public void testCase11GetGattServer(){
    	mTestUtils.writeOutput("testCase11GetGattServer() - start");
    	BluetoothManager bluetoothManager = (BluetoothManager) mContext.getSystemService(Context.BLUETOOTH_SERVICE);
    	mTestUtils.writeOutput("testCase11GetGattServer() - check bluetoothManager");
    	assertNotNull(bluetoothManager);
    	BluetoothGattServerCallback gattServerCb = new BluetoothGattServerCallback(){};
    	assertNotNull(bluetoothManager.openGattServer(mContext, gattServerCb));
    	mTestUtils.writeOutput("testCase11GetGattServer() - pass");
    }
    
    public void testCase999CallDeprecateMethod(){
    	mTestUtils.writeOutput("testCase999CallDeprecate() - start");

        mTestUtils.writeOutput("testCase999CallDeprecate() - getDefaultAdapter()");
        mAdapter = BluetoothAdapter.getDefaultAdapter();
        assertNotNull(mAdapter);
        mTestUtils.writeOutput("testCase999CallDeprecate() - disable()");
        mTestUtils.disable(mAdapter);
        mTestUtils.writeOutput("testCase999CallDeprecate() - enable()");
        mTestUtils.enable(mAdapter);
        mTestUtils.writeOutput("testCase999CallDeprecate() - adapter.getRemoteDevice");
        mCurDevice = mAdapter.getRemoteDevice(BluetoothLeTestRunner.sDeviceAddress);
        assertNotNull(mCurDevice);
        mTestUtils.writeOutput("testCase999CallDeprecate() - check whether device is paired or not");
        assertTrue(BluetoothDevice.BOND_BONDED == mCurDevice.getBondState());                                        
        mTestUtils.writeOutput("testCase999CallDeprecate() - connectGatt");
        BluetoothGatt gattClient = mCurDevice.connectGatt(getInstrumentation().getContext(), false, mClientCallback);
        assertNotNull(gattClient);
    	
        try{
        	gattClient.getConnectedDevices();
        }catch(Exception e){
        	/// ...
        }

        try{
        	gattClient.getConnectionState(mCurDevice);
        }catch(Exception e){
        	/// ...
        }

        try{
        	gattClient.getDevicesMatchingConnectionStates(new int[]{BluetoothGatt.STATE_CONNECTED});
        }catch(Exception e){
        	/// ...
        }
    
        mTestUtils.writeOutput("testCase999CallDeprecate() - pass");
    }
    
    private void wait1stConnected(long start){
    	while (System.currentTimeMillis() - start < BluetoothLeTestUtils.GATT_CLIENT_CONNECT_TIMEOUT && !mTestUtils.isGattConnect(mContext));

    	if(mTestUtils.isGattConnect(mContext)){
    		mTestUtils.writeOutput("wait1stConnected() - wait1stConnected completed in " + (System.currentTimeMillis() - start) + " ms");
    	}else{
    		mTestUtils.writeOutput("wait1stConnected() - wait1stConnected timeout");    		
    	}
    	assertTrue(mTestUtils.isGattConnect(mContext));
    }
    
    private boolean CheckGattService(BluetoothGatt gattClient){
    	return null != gattClient.getServices() && !gattClient.getServices().isEmpty();
    }
        
    private List<? extends Object> filterAttributeByUuids(BluetoothGatt gattClient, String[] strUuids){
    	ArrayList<Object> listAttr = new ArrayList<Object>();
    	List<BluetoothGattService> listService = gattClient.getServices();
    	if(null != listService){
    	    for(BluetoothGattService service : listService){
    	    	List<BluetoothGattService> listIncludedService = service.getIncludedServices();
    	    	List<BluetoothGattCharacteristic> listCharacteristic = service.getCharacteristics();
    	        for(BluetoothGattCharacteristic characteristic : listCharacteristic){ 	        	
    	        	for(String uuid : strUuids){
    	        		if(characteristic.getUuid().toString().equals(uuid)){
    	        			if(!listAttr.contains(characteristic)){   	        				
    	        				listAttr.add(characteristic);
    	        				Log.v(TAG, "filterAttributeByUuids() - find " + characteristic + " uuid=" + uuid);
    	        			}
    	        		}
    	        	}
        	    	List<BluetoothGattDescriptor> listDescriptor = characteristic.getDescriptors();    	        	
                    for(BluetoothGattDescriptor descriptor : listDescriptor){
        	        	for(String uuid : strUuids){
        	        		if(descriptor.getUuid().toString().equals(uuid)){
        	        			if(!listAttr.contains(descriptor)){
        	        				listAttr.add(descriptor);
    	        				    Log.v(TAG, "filterAttributeByUuids() - find " + descriptor + " uuid=" + uuid);
        	        			}
        	        	    }
        	        	}
                    }
    	        }
    	    }	
    	}
    	return listAttr;
    }

    private BluetoothGattCharacteristic getGattTestCharacteristic(BluetoothGatt gattClient){
        if(BluetoothLeTestRunner.USE_DEFAULT_SETTING)
    		return loadDefaultTestCharacteristic(gattClient);
        else{
        	List<? extends Object> listCharacteristic = filterAttributeByUuids(gattClient, BluetoothLeTestRunner.arrsCharacteristicUuid);
    		return (0 == listCharacteristic.size() ? null : (BluetoothGattCharacteristic)listCharacteristic.get(0));
        }
    }
    
    private BluetoothGattDescriptor getGattTestDescriptor(BluetoothGatt gattClient){
        if(BluetoothLeTestRunner.USE_DEFAULT_SETTING)
    		return loadDefaultGattTestDescriptor(gattClient);
        else{
           	List<? extends Object> listDescriptor = filterAttributeByUuids(gattClient, BluetoothLeTestRunner.arrsDescriptorUuid);
    		return (0 == listDescriptor.size() ? null :(BluetoothGattDescriptor)listDescriptor.get(0));                  	
        }    	
    }

    private BluetoothGattCharacteristic loadDefaultTestCharacteristic(BluetoothGatt gattClient){
    	return gattClient.getServices().get(1).getCharacteristics().get(0);
    }
    
    private BluetoothGattDescriptor loadDefaultGattTestDescriptor(BluetoothGatt gattClient){
    	return gattClient.getServices().get(1).getCharacteristics().get(0).getDescriptors().get(0);
    }
    
    private List<Object> getGattTestAttrs(BluetoothGatt gattClient){
    	ArrayList<Object> listAttr = new ArrayList<Object>();
    	
		listAttr.addAll(getGattTestCharacteristics(gattClient));
    	if(BluetoothLeTestRunner.SUPPORT_FEATURE_RELIABLE_WRITE_DESCRIPTOR)
    		listAttr.addAll(getGattTestDescriptors(gattClient));
    	
    	return listAttr;
    }
    
    @SuppressWarnings("unchecked")
    private List<BluetoothGattCharacteristic> getGattTestCharacteristics(BluetoothGatt gattClient){
        if(BluetoothLeTestRunner.USE_DEFAULT_SETTING)
    		return loadDefaultTestCharacteristics(gattClient);
        else{
        	List<? extends Object> listCharacteristic = filterAttributeByUuids(gattClient, BluetoothLeTestRunner.arrsCharacteristicUuid);
    		return (List<BluetoothGattCharacteristic>)listCharacteristic;
        }
    }
    
    @SuppressWarnings("unchecked")
    private List<BluetoothGattDescriptor> getGattTestDescriptors(BluetoothGatt gattClient){
        if(BluetoothLeTestRunner.USE_DEFAULT_SETTING)
    		return loadDefaultTestDescriptors(gattClient);
        else{
        	List<? extends Object> listDescriptor = filterAttributeByUuids(gattClient, BluetoothLeTestRunner.arrsDescriptorUuid);
    		return (List<BluetoothGattDescriptor>)listDescriptor;
        }
    }
    
    private List<BluetoothGattCharacteristic> loadDefaultTestCharacteristics(BluetoothGatt gattClient){
    	ArrayList<BluetoothGattCharacteristic> listChar = new ArrayList<BluetoothGattCharacteristic>();
    	
    	listChar.add(gattClient.getServices().get(1).getCharacteristics().get(0));
    	listChar.add(gattClient.getServices().get(1).getCharacteristics().get(1));
    	listChar.add(gattClient.getServices().get(2).getCharacteristics().get(0));
    	
    	return listChar;
    }
    
    private List<BluetoothGattDescriptor> loadDefaultTestDescriptors(BluetoothGatt gattClient){
    	ArrayList<BluetoothGattDescriptor> listDesc = new ArrayList<BluetoothGattDescriptor>();
    	
    	listDesc.add(gattClient.getServices().get(1).getCharacteristics().get(0).getDescriptors().get(0));
    	listDesc.add(gattClient.getServices().get(1).getCharacteristics().get(0).getDescriptors().get(1));
    	listDesc.add(gattClient.getServices().get(1).getCharacteristics().get(1).getDescriptors().get(0));
    	
    	return listDesc;
    }
    
    private HashMap<Object, Object> backupRecoverData(List<Object> listAttr){
    	HashMap<Object, Object> backupMap = new HashMap<Object, Object>();
    	for(Object objAttr : listAttr){
    		byte[] arrByte = null;
    		if(objAttr instanceof BluetoothGattCharacteristic){
    			arrByte = ((BluetoothGattCharacteristic)objAttr).getValue();
    		}else if(objAttr instanceof BluetoothGattDescriptor){
    			if(BluetoothLeTestRunner.SUPPORT_FEATURE_RELIABLE_WRITE_DESCRIPTOR)
    				arrByte = ((BluetoothGattDescriptor)objAttr).getValue();
    			else
    				continue;
    		}else
    			continue;
    		if(null != arrByte)
    			backupMap.put(objAttr, Arrays.copyOf(arrByte, arrByte.length));
    		else
    			backupMap.put(objAttr, null);
    	}
    	return backupMap;
    }
    
    private void restoreData(HashMap<Object, Object> backupMap){
    	for(Map.Entry<Object, Object> entry : backupMap.entrySet()){
    		byte[] arrByte = (byte[]) entry.getValue();
    		Object objAttr = entry.getKey();
    		if(objAttr instanceof BluetoothGattCharacteristic){
    			((BluetoothGattCharacteristic)objAttr).setValue(arrByte);
    		}else if(objAttr instanceof BluetoothGattDescriptor){
    			if(BluetoothLeTestRunner.SUPPORT_FEATURE_RELIABLE_WRITE_DESCRIPTOR)
    				((BluetoothGattDescriptor)objAttr).setValue(arrByte);
    			else
    				continue;
    		}
    	}    	
    }

    private void readAllAttribute(List<Object> listAttr, BluetoothGatt gattClient){
    	for(Object obj : listAttr){
    		int charCount = 1, descCount = 1;
    		if(obj instanceof BluetoothGattCharacteristic){
                mTestUtils.writeOutput("readAllAttribute Characteristic (" + charCount + ")" + (BluetoothGattCharacteristic)obj);
            	mTestUtils.gattClientReadCharacteristic(mContext, gattClient, (BluetoothGattCharacteristic)obj);
            	charCount++;
    		}else{
                mTestUtils.writeOutput("readAllAttribute Descriptor (" + descCount + ")" + (BluetoothGattDescriptor)obj);
            	mTestUtils.gattClientReadDescriptor(mContext, gattClient, (BluetoothGattDescriptor)obj);
            	descCount++;
    		}
    	}    	
    }
    
    private void setAllAttributeData(List<Object> listAttr,int iteration){
    	for(Object obj : listAttr){
    		if(obj instanceof BluetoothGattCharacteristic){
    			BluetoothGattCharacteristic chara = (BluetoothGattCharacteristic)obj;                
                String strValue = null, strNewValue = null;
            	if(null != chara.getValue())
            		strValue = chara.getStringValue(0);
            	else
            		strValue = "";
            	strNewValue = strValue + iteration;
            	chara.setValue(strNewValue);
    		}else{
    			BluetoothGattDescriptor desc = (BluetoothGattDescriptor)obj;                
            	byte[] arrByte = null, arrNewByte = null;
            	arrByte = desc.getValue();
            	arrNewByte = new byte[arrByte.length + 1];
            	System.arraycopy(arrByte, 0, arrNewByte, 0, arrByte.length);            	
            	arrNewByte[arrNewByte.length-1] += iteration;
            	desc.setValue(arrNewByte);
    		}
    	}      	
    }
    
    private boolean isAttrDataNotChanged(HashMap<Object, Object> backupMap){
    	boolean bChanged = false;
    	
    	for(Map.Entry<Object, Object> entry : backupMap.entrySet()){
    		byte[] arrByte = (byte[]) entry.getValue();
    		if(bChanged)
    		    break;	
    		Object objAttr = entry.getKey();
    		if(objAttr instanceof BluetoothGattCharacteristic){
    			bChanged = !Arrays.equals(((BluetoothGattCharacteristic)objAttr).getValue(), arrByte);
    		}else if(objAttr instanceof BluetoothGattDescriptor){
    			if(BluetoothLeTestRunner.SUPPORT_FEATURE_RELIABLE_WRITE_DESCRIPTOR)
    			    bChanged = !Arrays.equals(((BluetoothGattDescriptor)objAttr).getValue(), arrByte);
    			else
    				continue;
    		}
    	}
    	return !bChanged;
    }
}