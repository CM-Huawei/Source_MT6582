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

import junit.framework.TestSuite;

import android.os.Bundle;
import android.test.InstrumentationTestRunner;
import android.test.InstrumentationTestSuite;
import android.util.Log;

/**
 * Instrumentation test runner for Bluetooth tests.
 * <p>
 * To run:
 * <pre>
 * {@code
 * adb shell am instrument \
 *     [-e default_iterations <iterations>] \
 *     [-e connect_iterations <iterations>] \
 *     [-e read_remote_rssi <iterations>] \
 *     [-e discovering_iterations <iterations>] \
 *     [-e write_char_iterations <iterations>] \
 *     [-e write_desc_iterations <iterations>] \
 *     [-e reliable_write_iterations <iterations>] \
 *     [-e abort_reliable_write_iterations <iterations>] \
 *     [-e device_address <address>] \
 *     [-e characteristic_uuid <uuids>] \
 *     [-e descriptor_uuid <uuids>] \
 *     [-e use_default_settting <true/false>] \
 *     -w com.android.bluetoothle.tests/android.bluetooth.BluetoothLeTestRunner
 * }
 * </pre>
 */
public class BluetoothLeTestRunner extends InstrumentationTestRunner {
    private static final String TAG = "BluetoothLeTestRunner";

    public static int nDefaultIterations            = 1;
    public static int nConnectIterations            = 1;
    public static int nReadRssiIteration            = 1;
    public static int nDiscoveringIterations        = 1;
    public static int nWriteCharIterations          = 1;
    public static int nWriteDescIterations          = 1;
    public static int nReliableWriteIterations      = 1;
    public static int nAbortReliableWriteIterations = 1;
    public static int nLeScanIterations             = 1;

    
    public static String sDeviceAddress           = "DE:0E:46:65:92:C5";
    public static String[] arrsCharacteristicUuid = "10002a07-0000-1000-8000-00805f9b34fb,10002a08-0000-1000-8000-00805f9b34fb,10002a09-0000-1000-8000-00805f9b34fb".split(",");
    public static String[] arrsDescriptorUuid     = "10002907-0000-1000-8000-00805f9b34fb,10002908-0000-1000-8000-00805f9b34fb,10002909-0000-1000-8000-00805f9b34fb".split(",");    
    
    public static boolean SUPPORT_FEATURE_RELIABLE_WRITE_DESCRIPTOR = false;
    public static boolean USE_DEFAULT_SETTING = false;
    
    @Override
    public TestSuite getAllTests() {
        TestSuite suite = new InstrumentationTestSuite(this);
        suite.addTestSuite(BluetoothLeStressTest.class);
        return suite;
    }

    @Override
    public ClassLoader getLoader() {
        return BluetoothLeTestRunner.class.getClassLoader();
    }

    @Override
    public void onCreate(Bundle arguments) {
    	Log.v(TAG, "[onCreate]");
        String val = arguments.getString("default_iterations");
        if (val != null) {
            try {
            	nDefaultIterations = Integer.parseInt(val);
            } catch (NumberFormatException e) {
            	nDefaultIterations = 10;
            }
        }

        nConnectIterations = nDefaultIterations;
        val = arguments.getString("connect_iterations");
        if (val != null) {
            try {
            	nConnectIterations = Integer.parseInt(val);
            } catch (NumberFormatException e) {
            	nConnectIterations = nDefaultIterations;
            }
        }

        nReadRssiIteration = nDefaultIterations;
        val = arguments.getString("read_remote_rssi");
        if (val != null) {
            try {
            	nReadRssiIteration = Integer.parseInt(val);
            } catch (NumberFormatException e) {
            	nReadRssiIteration = nDefaultIterations;
            }
        }

        
    	nDiscoveringIterations = nDefaultIterations;
        val = arguments.getString("discovering_iterations");
        if (val != null) {
            try {
            	nDiscoveringIterations = Integer.parseInt(val);
            } catch (NumberFormatException e) {
            	nDiscoveringIterations = nDefaultIterations;
            }
        }

        nWriteCharIterations = nDefaultIterations;
        val = arguments.getString("write_char_iterations");
        if (val != null) {
            try {
            	nWriteCharIterations = Integer.parseInt(val);
            } catch (NumberFormatException e) {
            	nWriteCharIterations = nDefaultIterations;
            }
        }

        nWriteDescIterations = nDefaultIterations;
        val = arguments.getString("write_desc_iterations");
        if (val != null) {
            try {
            	nWriteDescIterations = Integer.parseInt(val);
            } catch (NumberFormatException e) {
                nWriteDescIterations = nDefaultIterations;
            }
        }

        nReliableWriteIterations = nDefaultIterations;
        val = arguments.getString("reliable_write_iterations");
        if (val != null) {
            try {
            	nReliableWriteIterations = Integer.parseInt(val);
            } catch (NumberFormatException e) {
                nReliableWriteIterations = nDefaultIterations;
            }
        }

        val = arguments.getString("abort_reliable_write_iterations");
        if (val != null) {
            try {
            	nAbortReliableWriteIterations = Integer.parseInt(val);
            } catch (NumberFormatException e) {
            	nAbortReliableWriteIterations = nDefaultIterations;
            }
        }
        
        val = arguments.getString("le_scan_iterations");
        if (val != null) {
            try {
            	nLeScanIterations = Integer.parseInt(val);
            } catch (NumberFormatException e) {
            	nLeScanIterations = nDefaultIterations;
            }
        }

        val = arguments.getString("device_address");
        if (val != null) {
            sDeviceAddress = val;
        }
        
        val = arguments.getString("characteristic_uuid");
        if(val != null){
        	arrsCharacteristicUuid = val.split(",");
        }
        
        val = arguments.getString("descriptor_uuid");
        if(val != null){
        	arrsDescriptorUuid = val.split(",");        	
        }

        val = arguments.getString("use_default_settting");
        if(val != null){
            try {
            	USE_DEFAULT_SETTING = Boolean.parseBoolean(val);
            } catch (NumberFormatException e) {
            	USE_DEFAULT_SETTING = false;
            }  	
        }
        
        Log.i(TAG, String.format("default_iterations=%d", nDefaultIterations));
        Log.i(TAG, String.format("connect_iterations=%d", nConnectIterations));
        Log.i(TAG, String.format("discovering_iterations=%d", nDiscoveringIterations));
        Log.i(TAG, String.format("write_char_iterations=%d", nWriteCharIterations));
        Log.i(TAG, String.format("write_desc_iterations=%d", nWriteDescIterations));
        Log.i(TAG, String.format("reliable_write_iterations=%d", nReliableWriteIterations));
        Log.i(TAG, String.format("abort_reliable_write_iterations=%d", nAbortReliableWriteIterations));
        Log.i(TAG, String.format("device_address=%s", sDeviceAddress));
        Log.i(TAG, String.format("use_default_settting=%s", USE_DEFAULT_SETTING));
   
        if(null != arrsCharacteristicUuid)
        	for(String strUuid : arrsCharacteristicUuid)
        		Log.i(TAG, String.format("characteristic_uuid=%s", strUuid));
        if(null != arrsDescriptorUuid)
        	for(String strUuid : arrsDescriptorUuid)
        		Log.i(TAG, String.format("descriptor_uuid=%s", strUuid));
        
        // Call onCreate last since we want to set the static variables first.
        super.onCreate(arguments);
    	Log.v(TAG, "[[onCreate]]");
    }
}
