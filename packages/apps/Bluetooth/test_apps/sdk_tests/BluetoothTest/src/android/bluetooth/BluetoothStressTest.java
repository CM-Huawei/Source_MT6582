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

import java.io.IOException;
import java.util.UUID;

import android.content.Context;
import android.test.InstrumentationTestCase;
import android.test.suitebuilder.annotation.SmallTest;

/**
 * Stress test suite for Bluetooth related functions.
 *
 * This file contains cases that can be run without a remote BT device
 *
 * Includes tests for enabling/disabling bluetooth, enabling/disabling discoverable mode,
 * starting/stopping scans, connecting/disconnecting to HFP, A2DP, HID, PAN profiles, and verifying
 * that remote connections/disconnections occur for the PAN profile.
 * <p>
 * This test suite uses {@link android.bluetooth.BluetoothTestRunner} to for parameters such as the
 * number of iterations and the addresses of remote Bluetooth devices.
 */
public class BluetoothStressTest extends InstrumentationTestCase {
    private static final String TAG = "BluetoothStressTest";
    private static final String OUTPUT_FILE = "BluetoothStressTestOutput.txt";

    private BluetoothTestUtils mTestUtils;
    
    private static final String NAME_SECURE = "CtsBluetoothChatSecure";
    
    static final UUID SECURE_UUID = 
            UUID.fromString("8591d757-18ee-45e1-9b12-92875d06ba23");
    
    @Override
    protected void setUp() throws Exception {
        super.setUp();

        Context context = getInstrumentation().getTargetContext();
        mTestUtils = new BluetoothTestUtils(context, TAG, OUTPUT_FILE);
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();

        mTestUtils.close();
    }

    /**
     * Stress test for enabling and disabling Bluetooth.
     */
    @SmallTest
    public void test1_Enable() {
        int iterations = BluetoothTestRunner.sEnableIterations;
        if (iterations == 0) {
            return;
        }

        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        mTestUtils.disable(adapter);

        for (int i = 0; i < iterations; i++) {
            mTestUtils.writeOutput("enable iteration " + (i + 1) + " of " + iterations);
            mTestUtils.enable(adapter);
            mTestUtils.disable(adapter);
        }
    }

    /**
     * Stress test for putting the device in and taking the device out of discoverable mode.
     */
    public void test2_Discoverable() {
        int iterations = BluetoothTestRunner.sDiscoverableIterations;
        if (iterations == 0) {
            return;
        }

        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        mTestUtils.disable(adapter);
        mTestUtils.enable(adapter);
        mTestUtils.undiscoverable(adapter);

        for (int i = 0; i < iterations; i++) {
            mTestUtils.writeOutput("discoverable iteration " + (i + 1) + " of " + iterations);
            mTestUtils.discoverable(adapter);
            mTestUtils.undiscoverable(adapter);
        }

        mTestUtils.disable(adapter);
    }

    /**
     * Stress test for starting and stopping Bluetooth scans.
     */
    public void test3_Scan() {
        int iterations = BluetoothTestRunner.sScanIterations;
        if (iterations == 0) {
            return;
        }

        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        mTestUtils.disable(adapter);
        mTestUtils.enable(adapter);
        mTestUtils.stopScan(adapter);

        for (int i = 0; i < iterations; i++) {
            mTestUtils.writeOutput("scan iteration " + (i + 1) + " of " + iterations);
            mTestUtils.startScan(adapter);
            mTestUtils.stopScan(adapter);
        }

        mTestUtils.disable(adapter);
    }
    

    /**
     * Stress test for bt socket.
     */
    public void test4_Socket() {

        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        mTestUtils.disable(adapter);
        mTestUtils.enable(adapter);
        
        //BluetoothDevice localDevice = adapter.getRemoteDevice(adapter.getAddress());

        try {
            BluetoothServerSocket bss = 
                    adapter.listenUsingRfcommWithServiceRecord(NAME_SECURE, SECURE_UUID);
            //BluetoothServerSocket bss = 
            //        adapter.listenUsingRfcommOn(12);
            bss.close();
            mTestUtils.writeOutput("testSocket ok");
        } catch (IOException e) {
            // TODO Auto-generated catch block
            mTestUtils.writeOutput("testSocket fail:" + e.toString());
            throw new RuntimeException(e);
        }

        mTestUtils.disable(adapter);
    }
    
    /**
     * Stress test for bt get address & name.
     */
    public void test5_AddressAndName() {
        String localName = null;
        String localAddr = null;
        final String newLocalName = "BT Test Device999";
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        mTestUtils.disable(adapter);
        mTestUtils.enable(adapter);

        localAddr = adapter.getAddress();
        mTestUtils.writeOutput("test5_AddressAndName: localAddr= " + localAddr);
        assertTrue(BluetoothAdapter.checkBluetoothAddress(localAddr));
        
        ///Get default name
        localName = adapter.getName();
        mTestUtils.writeOutput("test5_AddressAndName: localName= " + localName);
        assertNotNull(localName);
        
        ///Set New Name & Verify
        mTestUtils.writeOutput("test5_AddressAndName: set newLocalName= " + newLocalName);
        adapter.setName(newLocalName);
        //assertEquals(newLocalName, adapter.getName());
        
        ///Revert to original name
        adapter.setName(localName);
        mTestUtils.disable(adapter);
    }
    
    private void sleep(long time) {
        try {
            Thread.sleep(time);
        } catch (InterruptedException e) {
        }
    }
    
    private class ConnectThread extends Thread {
        private final BluetoothSocket mmSocket;
        
        public ConnectThread(BluetoothDevice device) {
            BluetoothSocket btSock = null;
            
            try {
                btSock = device.createRfcommSocketToServiceRecord(SECURE_UUID);
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
            mmSocket = btSock;
        }
        
        public void run(){
            if(null != mmSocket)
            {
                BluetoothStressTest.this.sleep(1000);
                
                try {
                    mTestUtils.writeOutput("Client Connecting...");
                    mmSocket.connect();
                } catch (IOException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
                finally{
                    try {
                        mmSocket.close();
                    } catch (IOException e) {
                        // TODO Auto-generated catch block
                        e.printStackTrace();
                    }
                }
            }
        }
    }
}
